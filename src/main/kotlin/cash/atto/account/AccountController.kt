package cash.atto.account

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
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
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
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
    name = "Accounts",
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
                content = [
                    Content(
                        schema = Schema(implementation = AccountCreationResponse::class),
                    ),
                ],
            ),
        ],
    )
    suspend fun create(
        @PathVariable walletName: String,
    ): AccountCreationResponse {
        val account = accountService.create(walletName)
        return AccountCreationResponse(
            address = AttoAddress.parsePath(account.address),
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
                content = [
                    Content(
                        schema = Schema(implementation = Account::class),
                    ),
                ],
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
                content = [
                    Content(
                        schema = Schema(implementation = AccountEntry::class),
                    ),
                ],
            ),
        ],
    )
    suspend fun change(
        @PathVariable address: String,
        @RequestBody request: ChangeRequest,
    ): AccountEntry = accountService.change(AttoAddress.parsePath(address), request.representativeAddress).toAccountEntry()

    @PostMapping("/wallets/accounts/{address}/transactions/SEND")
    @Operation(
        summary = "Send to target address",
        requestBody =
            io.swagger.v3.oas.annotations.parameters.RequestBody(
                required = true,
                content = [
                    Content(
                        schema = Schema(implementation = SendRequest::class),
                    ),
                ],
            ),
        responses = [
            ApiResponse(
                responseCode = "200",
                content = [
                    Content(
                        schema = Schema(implementation = AccountEntry::class),
                    ),
                ],
            ),
        ],
    )
    suspend fun send(
        @PathVariable address: String,
        @RequestBody request: SendRequest,
    ): AccountEntry {
        val lastHeight =
            request.lastHeight ?: accountService.getAccountDetails(AttoAddress.parsePath(address))?.height ?: 1UL.toAttoHeight()

        return accountService
            .send(
                AttoAddress.parsePath(address),
                request.receiverAddress,
                request.amount,
                lastHeight,
            ).toAccountEntry()
    }

    @OptIn(FlowPreview::class)
    @PostMapping("/wallets/accounts/entries")
    @Operation(
        summary = "Get account entries",
        responses = [
            ApiResponse(
                responseCode = "200",
                content = [
                    Content(
                        schema = Schema(implementation = AccountEntry::class),
                    ),
                ],
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
                val toHeight = accountService.getAccountDetails(it.address)?.height?.value ?: 1UL

                if (toHeight <= it.fromHeight) {
                    return@mapNotNull null
                }

                return@mapNotNull AttoNodeOperations.AccountHeightSearch(
                    it.address.path,
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
        @Serializable(with = AttoAddressAsStringSerializer::class)
        val address: AttoAddress,
        val displayAddress: String,
        val index: UInt,
    )

    @Serializable
    data class ChangeRequest(
        @field:Schema(
            description = "The address of the new representative account",
            example = "aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2",
            type = "String",
        )
        @Serializable(with = AttoAddressAsStringSerializer::class)
        val representativeAddress: AttoAddress,
    )

    @Serializable
    data class SendRequest(
        @field:Schema(
            description = "The address of the receiving account",
            example = "aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2",
            type = "String",
        )
        @Serializable(with = AttoAddressAsStringSerializer::class)
        val receiverAddress: AttoAddress,
        @field:Schema(description = "Amount", example = "10000", type = "Long")
        val amount: AttoAmount,
        @field:Schema(
            description = "Optional last known height. Used to avoid double sent",
            example = "1",
            type = "Long",
        )
        val lastHeight: AttoHeight? = null,
    )

    @Serializable
    data class AccountHeightSearch(
        @field:Schema(
            description = "The address of the account being searched",
            example = "aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2",
            type = "String",
        )
        @Serializable(with = AttoAddressAsStringSerializer::class)
        val address: AttoAddress,
        val fromHeight: ULong,
    )

    @Serializable
    data class HeightSearch(
        val search: List<AccountHeightSearch>,
    )

    @Serializable
    data class AccountDetails(
        @field:Schema(
            description = "The address of the account",
            example = "aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2",
            type = "String",
        )
        @Serializable(with = AttoAddressAsStringSerializer::class)
        val address: AttoAddress,
        @field:Schema(description = "Version", example = "0", type = "Short")
        val version: AttoVersion,
        @field:Schema(description = "Height", example = "1", type = "Long")
        val height: AttoHeight,
        @field:Schema(description = "Balance", example = "180000000000", type = "Long")
        val balance: AttoAmount,
        @field:Schema(
            description = "Last transaction hash",
            example = "70F9406609BCB2E3E18F22BD0839C95E5540E95489DC6F24DBF6A1F7CFD83A92",
            type = "String",
        )
        val lastTransactionHash: AttoHash,
        @field:Schema(description = "Timestamp of the last transaction", example = "1705517157478", type = "Long")
        @Serializable(with = InstantMillisSerializer::class)
        val lastTransactionTimestamp: Instant,
        @field:Schema(
            description = "Representative algorithm",
            example = "aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2",
            type = "String",
        )
        @Serializable(with = AttoAddressAsStringSerializer::class)
        val representativeAddress: AttoAddress,
    )

    private fun AttoAccount.toAccountDetails(): AccountDetails =
        AccountDetails(
            address = this.address,
            version = this.version,
            height = this.height,
            balance = this.balance,
            lastTransactionHash = this.lastTransactionHash,
            lastTransactionTimestamp = this.lastTransactionTimestamp,
            representativeAddress = this.representativeAddress,
        )

    @Schema(name = "AccountEntry", description = "An user-friendly view of account activity")
    @Serializable
    data class AccountEntry(
        @field:Schema(
            description = "Unique hash of the block",
            example = "68BA42CDD87328380BE32D5AA6DBB86E905B50273D37AF1DE12F47B83A001154",
            type = "String",
        )
        val hash: AttoHash,
        @field:Schema(
            description = "Address of the account",
            example = "aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2",
            type = "String",
        )
        @Serializable(with = AttoAddressAsStringSerializer::class)
        val address: AttoAddress,
        @field:Schema(description = "Block height", example = "0", type = "Long")
        val height: AttoHeight,
        @field:Schema(description = "Type of block in the account chain", example = "RECEIVE")
        val blockType: AttoBlockType,
        @field:Schema(
            description = "Address of the subject involved in the transaction",
            example = "aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2",
        )
        @Serializable(with = AttoAddressAsStringSerializer::class)
        val subjectAddress: AttoAddress,
        @field:Schema(
            description = "Public key of the subject involved in the transaction",
            example = "2EB21717813E7A0E0A7E308B8E2FD8A051F8724F5C5F0047E92E19310C582E3A",
        )
        val previousBalance: AttoAmount,
        @field:Schema(description = "Balance before this block", example = "0")
        val balance: AttoAmount,
        @field:Schema(description = "Balance after this block", example = "100")
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
