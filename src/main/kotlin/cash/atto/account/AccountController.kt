package cash.atto.account

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlockType
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoVersion
import cash.atto.commons.node.AttoNodeOperations
import cash.atto.commons.serialiazer.AttoAddressAsStringSerializer
import cash.atto.commons.serialiazer.InstantMillisSerializer
import cash.atto.commons.toAttoHeight
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.timeout
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.time.Duration.Companion.seconds

@RestController
@RequestMapping
@Tag(
    name = "Wallet Accounts",
    description =
        "Manage accounts within a wallet. This controller allows clients to create new accounts, enable or disable them, " +
            "send transactions, and change a representative. Accounts are scoped to wallets and represent individual identities " +
            "on the Atto network. This interface is designed for applications interacting with locally managed accounts.",
)
class AccountController(
    private val accountService: AccountService,
    private val accountRepository: AccountRepository,
    private val nodeOperations: AttoNodeOperations,
) {
    @PostMapping("/wallets/{walletName}/accounts")
    @Operation(
        summary = "Create an account",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
        ],
    )
    suspend fun create(
        @PathVariable walletName: String,
    ): AccountCreationResponse {
        val account = accountService.create(walletName)
        return AccountCreationResponse(
            address = account.address,
            displayAddress = AttoAddress.parsePath(account.address).toString(),
            index = account.accountIndex.toUInt(),
        )
    }

    @GetMapping("/wallets/{walletName}/accounts")
    @Operation(
        summary = "Get accounts for given wallet",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
        ],
    )
    fun findAll(
        @PathVariable walletName: String,
    ): Flow<Account> = accountRepository.findAllByWalletName(walletName)

    @GetMapping("/wallets/accounts/{address}")
    @Operation(
        summary = "Disable an account",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
            ApiResponse(
                responseCode = "404",
            ),
        ],
    )
    suspend fun get(
        @PathVariable address: String,
    ): ResponseEntity<Account> = ResponseEntity.ofNullable(accountRepository.findById(address))

    @GetMapping("/wallets/accounts/{address}/details")
    @Operation(
        summary = "Disable an account",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
            ApiResponse(
                responseCode = "404",
            ),
        ],
    )
    suspend fun getDetails(
        @PathVariable address: String,
    ): ResponseEntity<AccountDetails> =
        ResponseEntity.ofNullable(accountService.getAccountDetails(AttoAddress.parsePath(address))?.toAccountDetails())

    @PostMapping("/wallets/accounts/{address}/states/DISABLED")
    @Operation(
        summary = "Disable an account",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
        ],
    )
    suspend fun disable(
        @PathVariable address: String,
    ) {
        accountService.disable(AttoAddress.parsePath(address))
    }

    @PostMapping("/wallets/accounts/{address}/states/ENABLED")
    @Operation(
        summary = "Enable an account",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
        ],
    )
    suspend fun enable(
        @PathVariable address: String,
    ) {
        accountService.enable(AttoAddress.parsePath(address))
    }

    @PostMapping("/wallets/accounts/{address}/transactions/CHANGE")
    @Operation(
        summary = "Change representative",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
        ],
    )
    suspend fun change(
        @PathVariable address: String,
        @RequestBody request: ChangeRequest,
    ): AttoAccountEntry = accountService.change(AttoAddress.parsePath(address), AttoAddress.parsePath(request.representativeAddress))

    @PostMapping("/wallets/accounts/{address}/transactions/SEND")
    @Operation(
        summary = "Send to target address",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
        ],
    )
    suspend fun send(
        @PathVariable address: String,
        @RequestBody request: SendRequest,
    ): AttoAccountEntry {
        val lastHeight =
            request.lastHeight ?:
            accountService.getAccountDetails(AttoAddress.parsePath(address))?.height ?:
            1UL.toAttoHeight()

        return accountService.send(
            AttoAddress.parsePath(address),
            AttoAddress.parsePath(request.receiverAddress),
            request.amount,
            lastHeight,
        )
    }

    @OptIn(FlowPreview::class)
    @PostMapping("/wallets/accounts/entries")
    @Operation(
        summary = "Get account entries",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
        ],
    )
    suspend fun search(
        @RequestBody(required = false) request: HeightSearch?,
    ): Flow<AccountEntry> {
        val request = request?.toNodeSearch() ?: createNodeSearch()
        return nodeOperations
            .accountEntryStream(request)
            .timeout(60.seconds)
            .map { it.toAccountEntry() }
    }

    private fun HeightSearch.toNodeSearch(): AttoNodeOperations.HeightSearch {
        val nodeSearch =
            search.mapNotNull {
                val address = AttoAddress.parsePath(it.address)
                val toHeight = accountService.getAccountDetails(address)?.height?.value ?: 1UL

                if (toHeight <= it.fromHeight) {
                    return@mapNotNull null
                }

                return@mapNotNull AttoNodeOperations.AccountHeightSearch(
                    it.address,
                    it.fromHeight,
                    toHeight,
                )
            }
        return AttoNodeOperations.HeightSearch(nodeSearch)
    }

    private fun createNodeSearch(): AttoNodeOperations.HeightSearch {
        val nodeSearch =
            accountService.getAccountMap().mapNotNull {
                val address = it.key
                val fromHeight = 1UL
                val toHeight = it.value?.height?.value ?: 1UL

                if (fromHeight > toHeight) {
                    return@mapNotNull null
                }

                return@mapNotNull AttoNodeOperations.AccountHeightSearch(
                    address.path,
                    fromHeight,
                    toHeight,
                )
            }
        return AttoNodeOperations.HeightSearch(nodeSearch)
    }

    @Serializable
    data class AccountCreationResponse(
        val address: String,
        val displayAddress: String,
        val index: UInt,
    )

    @Serializable
    data class ChangeRequest(
        val representativeAddress: String,
    )

    @Serializable
    data class SendRequest(
        val receiverAddress: String,
        val amount: AttoAmount,
        val lastHeight: AttoHeight? = null,
    )

    @Serializable
    data class AccountHeightSearch(
        val address: String,
        val fromHeight: ULong,
    )

    @Serializable
    data class HeightSearch(
        val search: List<AccountHeightSearch>,
    )

    @Serializable
    data class AccountDetails(
        @Serializable(with = AttoAddressAsStringSerializer::class)
        val address: AttoAddress,
        val version: AttoVersion,
        val algorithm: AttoAlgorithm,
        val height: AttoHeight,
        val balance: AttoAmount,
        val lastTransactionHash: AttoHash,
        @Serializable(with = InstantMillisSerializer::class)
        val lastTransactionTimestamp: Instant,
        @Serializable(with = AttoAddressAsStringSerializer::class)
        val representativeAddress: AttoAddress,
    )

    private fun AttoAccount.toAccountDetails(): AccountDetails =
        AccountDetails(
            address = this.address,
            version = this.version,
            algorithm = this.algorithm,
            height = this.height,
            balance = this.balance,
            lastTransactionHash = this.lastTransactionHash,
            lastTransactionTimestamp = this.lastTransactionTimestamp,
            representativeAddress = this.representativeAddress,
        )

    @Serializable
    data class AccountEntry(
        val hash: AttoHash,
        @Serializable(with = AttoAddressAsStringSerializer::class)
        val address: AttoAddress,
        val height: AttoHeight,
        val blockType: AttoBlockType,
        @Serializable(with = AttoAddressAsStringSerializer::class)
        val subjectAddress: AttoAddress,
        val previousBalance: AttoAmount,
        val balance: AttoAmount,
        @Serializable(with = InstantMillisSerializer::class)
        val timestamp: Instant,
    )

    private fun AttoAccountEntry.toAccountEntry(): AccountEntry =
        AccountEntry(
            hash = this.hash,
            address = this.address,
            height = this.height,
            blockType = this.blockType,
            subjectAddress = this.subjectAddress,
            previousBalance = this.previousBalance,
            balance = this.balance,
            timestamp = this.timestamp,
        )
}
