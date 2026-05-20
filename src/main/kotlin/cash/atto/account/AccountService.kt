package cash.atto.account

import cash.atto.CacheSupport
import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoBlockType
import cash.atto.commons.AttoChangeBlock
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoKeyIndex
import cash.atto.commons.AttoOpenBlock
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoReceiveBlock
import cash.atto.commons.AttoSeed
import cash.atto.commons.AttoSendBlock
import cash.atto.commons.compareTo
import cash.atto.commons.node.AttoNodeClient
import cash.atto.commons.node.monitor.createAccountMonitor
import cash.atto.commons.toAttoAmount
import cash.atto.commons.toAttoIndex
import cash.atto.commons.toSeed
import cash.atto.commons.toSigner
import cash.atto.commons.wallet.AttoWallet
import cash.atto.commons.wallet.AttoWalletAccount
import cash.atto.commons.wallet.create
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.retry
import cash.atto.wallet.WalletEvent
import cash.atto.wallet.WalletService
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
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
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

@Service
@DependsOn("flywayInitializer")
class AccountService(
    private val accountRepository: AccountRepository,
    private val walletService: WalletService,
    private val nodeClient: AttoNodeClient,
    private val worker: AttoWorker,
) : CacheSupport {
    private val logger = KotlinLogging.logger {}

    private val mutex = Mutex()
    private val wallets = ConcurrentHashMap<String, WalletRuntime>()
    private val accountsByAddress = ConcurrentHashMap<AttoAddress, AccountRuntime>()

    private val scope = CoroutineScope(Dispatchers.Default)
    private val accountMonitor = nodeClient.createAccountMonitor()
    private val receiveAddresses = MutableStateFlow<Set<AttoAddress>>(emptySet())
    private val walletWorker = worker.retry(10.seconds)

    override fun clear() {
        wallets.clear()
        accountsByAddress.clear()
        receiveAddresses.value = emptySet()
    }

    fun getAccountMap(includeDisabled: Boolean = false): Map<AttoAddress, AttoAccount?> =
        accountsByAddress.values
            .asSequence()
            .filter { includeDisabled || it.enabled }
            .associate { it.address to it.account }

    fun getAccountDetails(address: AttoAddress): AttoAccount? = accountsByAddress[address]?.account

    @PostConstruct
    fun init() {
        runBlocking {
            walletService.getMnemonicMap().forEach { (walletName, mnemonic) ->
                val seed = mnemonic?.toSeed()
                wallets[walletName] = WalletRuntime(walletName, seed, seed?.toAttoWallet())
            }

            logger.info("Loaded ${wallets.size} wallets")

            val accounts = accountRepository.findAll().toList()
            val addresses = accounts.map { AttoAddress.parse(it.address) }
            val attoAccountMap: Map<AttoAddress, AttoAccount> =
                nodeClient
                    .account(addresses)
                    .associateBy { it.address }

            logger.info("Found ${accounts.size} addresses")

            accounts.forEach { account ->
                val runtime = AccountRuntime(account, attoAccountMap[AttoAddress.parse(account.address)])
                val walletRuntime = wallets.computeIfAbsent(account.walletName) { WalletRuntime(it) }

                walletRuntime.accounts[runtime.address] = runtime
                accountsByAddress[runtime.address] = runtime
            }

            wallets.values.forEach { it.openPersistedAccounts() }

            logger.info("Loaded ${accounts.size} addresses")

            refreshReceiveAddresses()
            startReceiver()
        }
    }

    private suspend fun refreshReceiveAddresses() {
        val newReceiveAddresses =
            accountsByAddress.values
                .asSequence()
                .filter { it.enabled }
                .filter { wallets[it.walletName]?.wallet != null }
                .map { it.address }
                .toSet()

        receiveAddresses.emit(newReceiveAddresses)
        logger.info { "Refreshed ${newReceiveAddresses.size} active addresses" }
    }

    private fun startReceiver() {
        startMonitorBinding()
        startAutoReceiver()
    }

    private fun startMonitorBinding() {
        scope.launch {
            var previousAddresses = emptySet<AttoAddress>()

            receiveAddresses.collect { nextAddresses ->
                val toMonitor = nextAddresses - previousAddresses
                val toStop = previousAddresses - nextAddresses

                toMonitor.forEach { address ->
                    if (!accountMonitor.isMonitored(address)) {
                        accountMonitor.monitor(address)
                    }
                }

                toStop.forEach { address ->
                    if (accountMonitor.isMonitored(address)) {
                        accountMonitor.stopMonitoring(address)
                    }
                }

                previousAddresses = nextAddresses
            }
        }
    }

    private fun startAutoReceiver() {
        scope.launch {
            while (isActive) {
                try {
                    accountMonitor
                        .receivableStream(1UL.toAttoAmount())
                        .onStart { logger.info { "Started listening receivables" } }
                        .onCompletion { logger.info { "Stopped listening receivables" } }
                        .collect { receivable ->
                            receive(receivable)
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error(e) { "Error while listening receivables. Retrying in 10 seconds..." }
                    delay(10.seconds)
                }
            }
        }
    }

    private suspend fun receive(receivable: AttoReceivable) {
        logger.debug { "Receiving $receivable" }

        val accountRuntime = accountsByAddress[receivable.receiverAddress] ?: return
        if (!accountRuntime.enabled) {
            return
        }

        accountRuntime.mutex.withLock {
            if (!accountRuntime.enabled) {
                return
            }

            val wallet = wallets[accountRuntime.walletName]?.wallet ?: return
            wallet.receive(receivable, receivable.receiverAddress)
            accountRuntime.account = wallet.getAccount(receivable.receiverAddress)
        }

        logger.info { "Received $receivable" }
    }

    @PreDestroy
    fun close() {
        scope.cancel()
    }

    @EventListener
    suspend fun onWallet(walletEvent: WalletEvent) {
        mutex.withLock {
            val walletRuntime = wallets.computeIfAbsent(walletEvent.name) { WalletRuntime(it) }
            val seed = walletEvent.mnemonic?.toSeed()

            walletRuntime.seed = seed
            walletRuntime.wallet = seed?.toAttoWallet()
            walletRuntime.openPersistedAccounts()

            refreshReceiveAddresses()
        }
    }

    @Transactional
    suspend fun create(walletName: String): Account {
        mutex.withLock {
            val walletRuntime = getUnlockedWalletRuntime(walletName)
            val seed = walletRuntime.requiredSeed()
            val index = walletRuntime.nextIndex()
            val address = seed.toSigner(index).address
            val account =
                Account(
                    address = address.path,
                    accountIndex = index.value.toLong(),
                    walletName = walletName,
                )

            val savedAccount = accountRepository.save(account)
            val walletAccount = walletRuntime.requiredWallet().openAccount(index)
            val accountRuntime = AccountRuntime(savedAccount, walletAccount.account)

            walletRuntime.accounts[accountRuntime.address] = accountRuntime
            accountsByAddress[accountRuntime.address] = accountRuntime

            logger.info { "Created new account: ${accountRuntime.address}" }

            refreshReceiveAddresses()

            return savedAccount
        }
    }

    @Transactional
    suspend fun createMultiple(
        walletName: String,
        toIndex: AttoKeyIndex,
    ): List<Account> =
        mutex.withLock {
            val walletRuntime = getUnlockedWalletRuntime(walletName)
            val seed = walletRuntime.requiredSeed()
            val persistedAccounts =
                accountRepository
                    .findAllByWalletName(walletName)
                    .toList()
                    .associateBy { it.index }
            val indexes = 0U.toAttoIndex().toIndexRange(toIndex)
            val savedAccounts =
                indexes.map { index ->
                    val address = seed.toSigner(index).address
                    val persistedAccount = persistedAccounts[index]

                    if (persistedAccount == null) {
                        accountRepository.save(
                            Account(
                                address = address.path,
                                accountIndex = index.value.toLong(),
                                walletName = walletName,
                            ),
                        )
                    } else {
                        if (persistedAccount.address != address.path) {
                            throw ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Persisted account index $index does not match wallet mnemonic",
                            )
                        }
                        persistedAccount
                    }
                }
            val walletAccounts =
                walletRuntime
                    .requiredWallet()
                    .openAccounts(indexes, seed)

            savedAccounts.forEach { account ->
                val walletAccount = walletAccounts.getValue(account.index)
                val accountRuntime = AccountRuntime(account, walletAccount.account)
                walletRuntime.accounts[accountRuntime.address] = accountRuntime
                accountsByAddress[accountRuntime.address] = accountRuntime
            }

            refreshReceiveAddresses()

            logger.info { "Created wallet $walletName accounts through index $toIndex" }

            savedAccounts
        }

    @Transactional
    suspend fun disable(address: AttoAddress): Account {
        mutex.withLock {
            val accountRuntime = getAccountRuntime(address)
            val account = accountRepository.findById(address.path)!!

            account.disabledAt = Instant.now()
            val savedAccount = accountRepository.save(account)

            accountRuntime.row = savedAccount

            refreshReceiveAddresses()

            logger.info { "Disabled account: $address" }

            return savedAccount
        }
    }

    @Transactional
    suspend fun enable(address: AttoAddress): Account {
        mutex.withLock {
            val accountRuntime = getAccountRuntime(address)
            val account = accountRepository.findById(address.path)!!

            account.disabledAt = null
            val savedAccount = accountRepository.save(account)

            accountRuntime.row = savedAccount

            refreshReceiveAddresses()

            logger.info { "Enabled account: $address" }

            return savedAccount
        }
    }

    suspend fun send(
        address: AttoAddress,
        receiverAddress: AttoAddress,
        amount: AttoAmount,
        lastHeight: AttoHeight,
    ): AttoAccountEntry {
        logger.info { "Sending from $address to $receiverAddress $amount" }

        val accountRuntime = getAccountRuntime(address)
        accountRuntime.mutex.withLock {
            val account = accountRuntime.requiredAccount()
            val balance = account.balance

            if (account.height != lastHeight) {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Given height for $address does not match last known height ${account.height}. List all the newest transactions and try again!",
                )
            }

            if (amount > account.balance) {
                throw IllegalArgumentException("Not enough balance.")
            }

            val wallet = getUnlockedWalletRuntime(accountRuntime.walletName).requiredWallet()

            try {
                val transaction = wallet.send(address, receiverAddress, amount)
                accountRuntime.account = wallet.getAccount(address)

                return AttoAccountEntry(
                    hash = transaction.hash,
                    algorithm = transaction.block.algorithm,
                    publicKey = transaction.block.publicKey,
                    height = transaction.height,
                    blockType = transaction.block.toBlockType(),
                    subjectAlgorithm = receiverAddress.algorithm,
                    subjectPublicKey = receiverAddress.publicKey,
                    previousBalance = balance,
                    balance = transaction.block.balance,
                    timestamp = transaction.block.timestamp,
                )
            } catch (e: Exception) {
                refreshReceiveAddresses()
                throw e
            }
        }
    }

    suspend fun change(
        address: AttoAddress,
        representativeAddress: AttoAddress,
    ): AttoAccountEntry {
        logger.info { "Changing $address representative to $representativeAddress" }

        val accountRuntime = getAccountRuntime(address)
        accountRuntime.mutex.withLock {
            accountRuntime.requiredAccount()
            val wallet = getUnlockedWalletRuntime(accountRuntime.walletName).requiredWallet()

            try {
                val transaction = wallet.change(accountRuntime.index, representativeAddress)
                accountRuntime.account = wallet.getAccount(address)

                return AttoAccountEntry(
                    hash = transaction.hash,
                    algorithm = transaction.block.algorithm,
                    publicKey = transaction.block.publicKey,
                    height = transaction.height,
                    blockType = transaction.block.toBlockType(),
                    subjectAlgorithm = representativeAddress.algorithm,
                    subjectPublicKey = representativeAddress.publicKey,
                    previousBalance = transaction.block.balance,
                    balance = transaction.block.balance,
                    timestamp = transaction.block.timestamp,
                )
            } catch (e: Exception) {
                refreshReceiveAddresses()
                throw e
            }
        }
    }

    private fun getAccountRuntime(address: AttoAddress): AccountRuntime =
        accountsByAddress[address] ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Address  $address wasn't found",
        )

    private fun getWalletRuntime(walletName: String): WalletRuntime =
        wallets[walletName] ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Wallet $walletName does not exist",
        )

    private fun getUnlockedWalletRuntime(walletName: String): WalletRuntime {
        val walletRuntime = getWalletRuntime(walletName)

        if (walletRuntime.wallet == null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Wallet $walletName is locked",
            )
        }

        return walletRuntime
    }

    private fun AttoSeed.toAttoWallet(): AttoWallet = AttoWallet.create(nodeClient, walletWorker, this)

    private val Account.index: AttoKeyIndex get() = accountIndex.toUInt().toAttoIndex()

    private suspend fun AttoWallet.openAccounts(
        indexes: List<AttoKeyIndex>,
        seed: AttoSeed,
    ): Map<AttoKeyIndex, AttoWalletAccount> {
        val missingIndexes = indexes.filterNot { isOpen(it) }
        val openedAccounts =
            if (missingIndexes.isEmpty()) {
                emptyMap()
            } else {
                openAccount(missingIndexes).associateBy { it.index }
            }

        return indexes.associateWith { index ->
            openedAccounts[index] ?: AttoWalletAccount(
                index = index,
                address = seed.toSigner(index).address,
                account = getAccount(index),
            )
        }
    }

    private fun AttoKeyIndex.toIndexRange(toIndex: AttoKeyIndex): List<AttoKeyIndex> =
        buildList {
            var value = this@toIndexRange.value
            while (value <= toIndex.value) {
                add(value.toAttoIndex())
                if (value == UInt.MAX_VALUE) {
                    break
                }
                value++
            }
        }

    private fun WalletRuntime.nextIndex(): AttoKeyIndex {
        val maxIndex = accounts.values.maxOfOrNull { it.index.value }
        if (maxIndex == null) {
            return 0U.toAttoIndex()
        }

        if (maxIndex == UInt.MAX_VALUE) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Wallet $name has no available account indexes")
        }

        return (maxIndex + 1U).toAttoIndex()
    }

    private suspend fun WalletRuntime.openPersistedAccounts() {
        val wallet = wallet ?: return
        val persistedAccounts = accounts.values.toList()

        if (persistedAccounts.isEmpty()) {
            return
        }

        val walletAccounts =
            wallet
                .openAccount(persistedAccounts.map { it.index })
                .associateBy { it.address }

        persistedAccounts.forEach { accountRuntime ->
            accountRuntime.mutex.withLock {
                accountRuntime.account = walletAccounts[accountRuntime.address]?.account
            }
        }
    }

    private fun WalletRuntime.requiredWallet(): AttoWallet =
        wallet ?: throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Wallet $name is locked",
        )

    private fun WalletRuntime.requiredSeed(): AttoSeed =
        seed ?: throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Wallet $name is locked",
        )

    private class WalletRuntime(
        val name: String,
        @Volatile var seed: AttoSeed? = null,
        @Volatile var wallet: AttoWallet? = null,
    ) {
        val accounts = ConcurrentHashMap<AttoAddress, AccountRuntime>()
    }

    private class AccountRuntime(
        @Volatile var row: Account,
        @Volatile var account: AttoAccount?,
    ) {
        val mutex = Mutex()
        val address: AttoAddress = AttoAddress.parse(row.address)
        val index: AttoKeyIndex get() = row.accountIndex.toUInt().toAttoIndex()
        val walletName: String get() = row.walletName
        val enabled: Boolean get() = row.disabledAt == null
    }

    private fun AccountRuntime.requiredAccount(): AttoAccount =
        account ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Account $address is not open yet. Please receive some attos before try again",
        )

    private fun AttoBlock.toBlockType(): AttoBlockType =
        when (this) {
            is AttoOpenBlock -> AttoBlockType.OPEN
            is AttoChangeBlock -> AttoBlockType.CHANGE
            is AttoReceiveBlock -> AttoBlockType.RECEIVE
            is AttoSendBlock -> AttoBlockType.SEND
        }
}
