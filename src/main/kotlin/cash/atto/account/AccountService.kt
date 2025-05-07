package cash.atto.account

import cash.atto.ApplicationProperties
import cash.atto.CacheSupport
import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoMnemonic
import cash.atto.commons.AttoSigner
import cash.atto.commons.AttoTransaction
import cash.atto.commons.node.AttoNodeOperations
import cash.atto.commons.toPrivateKey
import cash.atto.commons.toSeed
import cash.atto.commons.toSigner
import cash.atto.commons.worker.AttoWorker
import cash.atto.wallet.WalletEvent
import cash.atto.wallet.WalletService
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import org.springframework.context.annotation.DependsOn
import org.springframework.context.event.EventListener
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

@Service
@DependsOn("flywayInitializer")
class AccountService(
    private val applicationProperties: ApplicationProperties,
    private val accountRepository: AccountRepository,
    private val walletService: WalletService,
    private val nodeClient: AttoNodeOperations,
    private val worker: AttoWorker,
) : CacheSupport {
    private val logger = KotlinLogging.logger {}

    private val mutex = Mutex()
    private val mnemonicMap = ConcurrentHashMap<String, AttoMnemonic?>()
    private val walletAccountMap = ConcurrentHashMap<AttoAddress, WalletAccount>()

    private val scope = CoroutineScope(Dispatchers.Default)
    private val activeAddresses = MutableStateFlow<List<AttoAddress>>(emptyList())

    override fun clear() {
        mnemonicMap.clear()
        walletAccountMap.clear()
        activeAddresses.value = emptyList()
    }

    private fun getMnemonic(walletName: String): AttoMnemonic {
        if (!mnemonicMap.containsKey(walletName)) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Wallet $walletName does not exist",
            )
        }

        return mnemonicMap[walletName] ?: throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Wallet $walletName is locked",
        )
    }

    private fun getWalletAccount(address: AttoAddress): WalletAccount =
        walletAccountMap[address] ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Address  $address wasn't found",
        )

    fun getAccountMap(): Map<AttoAddress, AttoAccount?> =
        walletAccountMap
            .filter { it.value.enabled }
            .map { it.key to it.value.account }
            .toMap()

    fun getAccount(address: AttoAddress): AttoAccount? = walletAccountMap[address]?.account

    @PostConstruct
    fun init() {
        runBlocking {
            mnemonicMap.putAll(walletService.getMnemonicMap())

            val accounts = accountRepository.findAll().toList()

            val attoAccountMap: Map<AttoAddress, AttoAccount> =
                nodeClient
                    .account(accounts.map { AttoAddress.parsePath(it.address) })
                    .associateBy { it.address }

            accountRepository.findAll().collect { account ->
                val walletAccount = account.toWalletAccount()
                if (account.disabledAt == null) {
                    walletAccount.disable()
                }
                attoAccountMap[walletAccount.address]?.let {
                    walletAccount.update(it)
                }
                walletAccountMap[walletAccount.address] = walletAccount
            }

            refreshActiveAddresses()
            startReceiver()
        }
    }

    private fun refreshActiveAddresses() {
        activeAddresses.value =
            walletAccountMap.values
                .filter { it.enabled }
                .filter { mnemonicMap[it.walletName] != null }
                .map { it.address }
        logger.info { "Refreshed active addresses" }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startReceiver() {
        scope.launch {
            activeAddresses
                .flatMapLatest { addresses ->
                    if (addresses.isEmpty()) {
                        emptyFlow()
                    } else {
                        nodeClient
                            .receivableStream(addresses)
                            .onStart { logger.info { "Started listening for ${addresses.size} addresses receivables" } }
                            .onStart { logger.info { "Stopped listening for ${addresses.size} addresses receivables" } }
                            .retryWhen { e, _ ->
                                logger.warn(e) { "Receiver failed. Retrying in 10s..." }
                                delay(10.seconds)
                                true
                            }
                    }
                }.collect { receivable ->
                    walletAccountMap[receivable.receiverAddress]
                        ?.receive(receivable)
                }
        }
    }

    @PreDestroy
    fun close() {
        scope.cancel()
    }

    @EventListener
    suspend fun onWallet(walletEvent: WalletEvent) {
        mnemonicMap[walletEvent.name] = walletEvent.mnemonic
        refreshActiveAddresses()
    }

    @Transactional
    suspend fun create(walletName: String): Account {
        mutex.withLock {
            val mnemonic = getMnemonic(walletName)

            val index =
                walletAccountMap.values
                    .asSequence()
                    .filter { it.walletName == walletName }
                    .count()
                    .toLong()

            val signer = mnemonic.toSigner(index)
            val address = AttoAddress(AttoAlgorithm.V1, signer.publicKey)

            val account =
                Account(
                    address = address.path,
                    accountIndex = index,
                    walletName = walletName,
                )

            accountRepository.save(account)

            walletAccountMap[address] =
                account.toWalletAccount().apply {
                    nodeClient.account(address.publicKey)?.let { update(it) }
                }

            logger.info { "Created new account: $address" }

            refreshActiveAddresses()

            return account
        }
    }

    @Transactional
    suspend fun disable(address: AttoAddress): Account {
        mutex.withLock {
            val walletAccount = getWalletAccount(address)
            val account = accountRepository.findById(address.path)!!

            account.disabledAt = java.time.Instant.now()
            accountRepository.save(account)

            walletAccount.disable()

            refreshActiveAddresses()

            logger.info { "Disabled account: $address" }

            return account
        }
    }

    @Transactional
    suspend fun enable(address: AttoAddress): Account {
        mutex.withLock {
            val walletAccount = getWalletAccount(address)
            val account = accountRepository.findById(address.path)!!

            account.disabledAt = null
            accountRepository.save(account)

            walletAccount.enable()

            refreshActiveAddresses()

            logger.info { "Enabled account: $address" }

            return account
        }
    }

    suspend fun send(
        address: AttoAddress,
        receiverAddress: AttoAddress,
        amount: AttoAmount,
    ): AttoTransaction {
        logger.debug { "Sending from $address to $receiverAddress $amount" }
        val walletAccount = getWalletAccount(address)
        return walletAccount.send(receiverAddress, amount)
    }

    suspend fun change(
        address: AttoAddress,
        representativeAddress: AttoAddress,
    ): AttoTransaction {
        logger.debug { "Changing $address representative to $representativeAddress" }
        val walletAccount = getWalletAccount(address)
        return walletAccount.change(representativeAddress)
    }

    private suspend fun Account.toWalletAccount(): WalletAccount {
        val address = AttoAddress.parsePath(address)

        return WalletAccount(
            network = applicationProperties.network,
            address = address,
            walletName = walletName,
        ) { block ->
            val mnemonic = getMnemonic(walletName)

            val signer = mnemonic.toSigner(accountIndex)
            val transaction =
                AttoTransaction(
                    block = block,
                    signature = signer.sign(block),
                    work = worker.work(block),
                )

            nodeClient.publish(transaction)

            return@WalletAccount transaction
        }
    }

    private suspend fun AttoMnemonic.toSigner(index: Long): AttoSigner = toSeed().toPrivateKey(index.toUInt()).toSigner()
}
