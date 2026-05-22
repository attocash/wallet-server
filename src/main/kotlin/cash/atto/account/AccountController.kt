package cash.atto.account

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAddressAsPathStringSerializer
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoInstantAsLongSerializer
import cash.atto.commons.AttoKeyIndex
import cash.atto.commons.AttoVersion
import cash.atto.commons.node.AccountHeightSearch
import cash.atto.commons.node.AttoNodeOperations
import cash.atto.commons.node.HeightSearch
import cash.atto.commons.toAttoHeight
import cash.atto.commons.toAttoIndex
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.timeout
import kotlinx.serialization.Serializable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import kotlin.time.Duration.Companion.seconds
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody

@RestController
@RequestMapping
@Tag(
    name = "Accounts",
    description =
        "Create and inspect wallet accounts, recover deterministic accounts, toggle account availability, send funds, " +
            "change representatives, and stream account entries. Account rows are local wallet identities; account details " +
            "are live chain state once an account has opened on the Atto network.",
)
class AccountController(
    private val accountService: AccountService,
    private val accountRecoverer: AccountRecoverer,
    private val accountRepository: AccountRepository,
    private val nodeOperations: AttoNodeOperations,
) {
    @PostMapping("/wallets/{walletName}/accounts")
    @Operation(
        summary = "Create next account",
        description = "Creates the next deterministic account for the named unlocked wallet and opens it in the local runtime.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Account created",
                content = [
                    Content(
                        schema = Schema(implementation = AccountCreationResponse::class),
                    ),
                ],
            ),
            ApiResponse(responseCode = "400", description = "Wallet is locked"),
            ApiResponse(responseCode = "404", description = "Wallet does not exist"),
        ],
    )
    suspend fun create(
        @PathVariable walletName: String,
    ): AccountCreationResponse = accountService.create(walletName).toAccountCreationResponse()

    @PostMapping("/wallets/{walletName}/accounts/ranges/{toIndex}")
    @Operation(
        summary = "Create accounts through index",
        description = "Creates and opens every deterministic account from index 0 through the requested index.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Accounts created or already present",
                content = [
                    Content(
                        array = ArraySchema(schema = Schema(implementation = AccountCreationResponse::class)),
                    ),
                ],
            ),
            ApiResponse(responseCode = "400", description = "Wallet is locked or toIndex is invalid"),
            ApiResponse(responseCode = "404", description = "Wallet does not exist"),
            ApiResponse(responseCode = "409", description = "Persisted account index does not match the wallet mnemonic"),
        ],
    )
    suspend fun createMultiple(
        @PathVariable walletName: String,
        @PathVariable toIndex: Long,
    ): List<AccountCreationResponse> =
        accountService
            .createMultiple(walletName, toIndex.toAttoKeyIndex("toIndex"))
            .map { it.toAccountCreationResponse() }

    @PostMapping("/wallets/{walletName}/recoveries")
    @Operation(
        summary = "Recover wallet accounts",
        description =
            "Rebuilds account rows for an already imported and unlocked wallet by deriving deterministic account indexes, " +
                "querying the node for current account state, and persisting any missing addresses. " +
                "Recovery starts at the wallet's highest persisted account index and stops after consecutive unopened accounts.",
        requestBody =
            OpenApiRequestBody(
                required = true,
                description = "Gap-based recovery settings.",
                content = [
                    Content(
                        schema = Schema(implementation = AccountRecoveryRequest::class),
                    ),
                ],
            ),
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Recovery result",
                content = [
                    Content(
                        schema = Schema(implementation = AccountRecoveryResponse::class),
                    ),
                ],
            ),
            ApiResponse(responseCode = "400", description = "Wallet is locked or gapLimit is invalid"),
            ApiResponse(responseCode = "404", description = "Wallet does not exist"),
            ApiResponse(responseCode = "409", description = "Persisted account index does not match the wallet mnemonic"),
        ],
    )
    suspend fun recover(
        @PathVariable walletName: String,
        @RequestBody request: AccountRecoveryRequest,
    ): AccountRecoveryResponse {
        val recovery =
            accountRecoverer.recover(
                walletName = walletName,
                gapLimit = request.gapLimit.toPositiveUInt("gapLimit"),
            )

        return AccountRecoveryResponse(
            fromIndex = recovery.fromIndex,
            toIndex = recovery.toIndex,
            scannedCount = recovery.scannedCount.toLong(),
            gapCount = recovery.gapCount.toLong(),
            recoveredCount = recovery.recoveredCount.toLong(),
            existingCount = recovery.existingCount.toLong(),
            accounts =
                recovery.accounts.map {
                    RecoveredAccountResponse(
                        address = AttoAddress.parse(it.account.address),
                        displayAddress = AttoAddress.parse(it.account.address).toString(),
                        index =
                            it.account.accountIndex
                                .toUInt()
                                .toAttoIndex(),
                        opened = it.opened,
                        recovered = it.recovered,
                    )
                },
        )
    }

    @GetMapping("/wallets/{walletName}/accounts")
    @Operation(
        summary = "List wallet accounts",
        description = "Lists persisted local account rows for the named wallet.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Persisted account rows",
                content = [
                    Content(
                        array = ArraySchema(schema = Schema(implementation = Account::class)),
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
        summary = "Get account row",
        description = "Retrieves the persisted local account row for an address.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Persisted account row",
                content = [
                    Content(
                        schema = Schema(implementation = Account::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Account row was not found",
            ),
        ],
    )
    suspend fun get(
        @PathVariable address: String,
    ): ResponseEntity<Account> = ResponseEntity.ofNullable(accountRepository.findById(address))

    @GetMapping("/wallets/accounts/{address}/details")
    @Operation(
        summary = "Get live account details",
        description = "Returns live chain state known by the wallet runtime. Unopened accounts return 404.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Live account state",
                content = [
                    Content(
                        schema = Schema(implementation = AccountDetails::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Account is unknown or has not opened on-chain",
            ),
        ],
    )
    suspend fun getDetails(
        @PathVariable address: String,
    ): ResponseEntity<AccountDetails> =
        ResponseEntity.ofNullable(accountService.getAccountDetails(AttoAddress.parse(address))?.toAccountDetails())

    @PostMapping("/wallets/accounts/{address}/states/DISABLED")
    @Operation(
        summary = "Disable an account",
        description = "Marks a persisted account as disabled so it is excluded from automatic receive processing.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Account disabled",
            ),
            ApiResponse(responseCode = "404", description = "Account row was not found"),
        ],
    )
    suspend fun disable(
        @PathVariable address: String,
    ) {
        accountService.disable(AttoAddress.parse(address))
    }

    @PostMapping("/wallets/accounts/{address}/states/ENABLED")
    @Operation(
        summary = "Enable an account",
        description = "Clears the disabled marker so the account can participate in automatic receive processing again.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Account enabled",
            ),
            ApiResponse(responseCode = "404", description = "Account row was not found"),
        ],
    )
    suspend fun enable(
        @PathVariable address: String,
    ) {
        accountService.enable(AttoAddress.parse(address))
    }

    @PostMapping("/wallets/accounts/{address}/transactions/CHANGE")
    @Operation(
        summary = "Change representative",
        description = "Publishes a representative change block from an opened account in an unlocked wallet.",
        requestBody =
            OpenApiRequestBody(
                required = true,
                description = "Representative address to assign to the account.",
                content = [
                    Content(
                        schema = Schema(implementation = ChangeRequest::class),
                    ),
                ],
            ),
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Representative changed",
                content = [
                    Content(
                        schema = Schema(implementation = AccountEntry::class),
                    ),
                ],
            ),
            ApiResponse(responseCode = "400", description = "Wallet is locked"),
            ApiResponse(responseCode = "404", description = "Account is unknown or has not opened on-chain"),
        ],
    )
    suspend fun change(
        @PathVariable address: String,
        @RequestBody request: ChangeRequest,
    ): AccountEntry = accountService.change(AttoAddress.parse(address), request.representativeAddress).toAccountEntry()

    @PostMapping("/wallets/accounts/{address}/transactions/SEND")
    @Operation(
        summary = "Send funds",
        description = "Publishes a send block from an opened account in an unlocked wallet.",
        requestBody =
            OpenApiRequestBody(
                required = true,
                description =
                    "Receiver address, amount, and optional last known height. Supplying lastHeight lets clients reject stale " +
                        "send attempts explicitly.",
                content = [
                    Content(
                        schema = Schema(implementation = SendRequest::class),
                    ),
                ],
            ),
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Send block published",
                content = [
                    Content(
                        schema = Schema(implementation = AccountEntry::class),
                    ),
                ],
            ),
            ApiResponse(responseCode = "400", description = "Wallet is locked or request values are invalid"),
            ApiResponse(responseCode = "404", description = "Account is unknown or has not opened on-chain"),
            ApiResponse(responseCode = "409", description = "Provided lastHeight does not match current account height"),
        ],
    )
    suspend fun send(
        @PathVariable address: String,
        @RequestBody request: SendRequest,
    ): AccountEntry {
        val lastHeight =
            request.lastHeight ?: accountService.getAccountDetails(AttoAddress.parse(address))?.height ?: 1UL.toAttoHeight()

        return accountService
            .send(
                AttoAddress.parse(address),
                request.receiverAddress,
                request.amount,
                lastHeight,
            ).toAccountEntry()
    }

    @OptIn(FlowPreview::class)
    @PostMapping("/wallets/accounts/entries")
    @Operation(
        summary = "Stream account entries",
        description =
            "Streams account entries for known accounts. If no body is provided, all locally opened accounts are queried " +
                "from height 1 through their current height.",
        requestBody =
            OpenApiRequestBody(
                required = false,
                description =
                    "Optional height search bounds. Each fromHeight must be greater than 0; searches beyond the current " +
                        "account height are ignored.",
                content = [
                    Content(
                        schema = Schema(implementation = HeightSearch::class),
                    ),
                ],
            ),
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Matching account entries",
                content = [
                    Content(
                        array = ArraySchema(schema = Schema(implementation = AccountEntry::class)),
                    ),
                ],
            ),
            ApiResponse(responseCode = "400", description = "A provided fromHeight is zero"),
        ],
    )
    suspend fun search(
        @RequestBody(required = false) request: HeightSearch?,
    ): Flow<AccountEntry> {
        val request = request?.toBoundedNodeSearch() ?: createNodeSearch()

        if (request.search.isEmpty()) {
            return emptyFlow()
        }

        return nodeOperations
            .accountEntryStream(request)
            .timeout(60.seconds)
            .map { it.toAccountEntry() }
    }

    private fun HeightSearch.toBoundedNodeSearch(): HeightSearch {
        val nodeSearch =
            search.mapNotNull {
                if (it.fromHeight.value == 0UL) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "fromHeight must be greater than 0")
                }

                val currentHeight = accountService.getAccountDetails(it.address)?.height?.value ?: return@mapNotNull null

                if (currentHeight < it.fromHeight.value) {
                    return@mapNotNull null
                }

                return@mapNotNull AccountHeightSearch(
                    it.address,
                    it.fromHeight,
                    currentHeight.toAttoHeight(),
                )
            }
        return HeightSearch(nodeSearch)
    }

    private fun createNodeSearch(): HeightSearch {
        val nodeSearch =
            accountService.getAccountMap().mapNotNull {
                val address = it.key
                val fromHeight = 1UL
                val toHeight = it.value?.height?.value ?: return@mapNotNull null

                if (toHeight < fromHeight) {
                    return@mapNotNull null
                }

                return@mapNotNull AccountHeightSearch(
                    address,
                    fromHeight.toAttoHeight(),
                    toHeight.toAttoHeight(),
                )
            }
        return HeightSearch(nodeSearch)
    }

    private fun Long.toAttoKeyIndex(fieldName: String) =
        when {
            this < 0 -> {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$fieldName must be greater than or equal to 0")
            }

            this > UInt.MAX_VALUE.toLong() -> {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$fieldName must be less than or equal to ${UInt.MAX_VALUE}")
            }

            else -> {
                toUInt().toAttoIndex()
            }
        }

    private fun Long.toPositiveUInt(fieldName: String) =
        when {
            this < 1 -> {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$fieldName must be greater than 0")
            }

            this > UInt.MAX_VALUE.toLong() -> {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$fieldName must be less than or equal to ${UInt.MAX_VALUE}")
            }

            else -> {
                toUInt()
            }
        }

    private fun Account.toAccountCreationResponse(): AccountCreationResponse =
        AccountCreationResponse(
            address = AttoAddress.parse(address),
            displayAddress = AttoAddress.parse(address).toString(),
            index = accountIndex.toUInt().toAttoIndex(),
        )

    @Serializable
    @Schema(name = "AccountCreationResponse", description = "Created deterministic account address and index")
    data class AccountCreationResponse(
        @field:Schema(
            description = "Bare account address path used by API routes",
            example = "aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2",
            type = "String",
        )
        @Serializable(with = AttoAddressAsPathStringSerializer::class)
        val address: AttoAddress,
        @field:Schema(
            description = "Display form of the account address",
            example = "atto://aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2",
            type = "String",
        )
        val displayAddress: String,
        @field:Schema(description = "Deterministic wallet account index", example = "0", type = "Long")
        val index: AttoKeyIndex,
    )

    @Serializable
    @Schema(name = "AccountRecoveryRequest", description = "Gap-based deterministic account recovery request")
    data class AccountRecoveryRequest(
        @field:Schema(description = "Stop after this many consecutive unopened accounts", example = "20", type = "Long")
        val gapLimit: Long,
    )

    @Serializable
    @Schema(name = "AccountRecoveryResponse", description = "Result of a gap-based account recovery scan")
    data class AccountRecoveryResponse(
        @field:Schema(description = "First recovered account index", example = "0", type = "Long")
        val fromIndex: AttoKeyIndex,
        @field:Schema(description = "Last recovered account index", example = "10", type = "Long")
        val toIndex: AttoKeyIndex,
        @field:Schema(description = "Number of indexes scanned by this recovery", example = "11", type = "Long")
        val scannedCount: Long,
        @field:Schema(description = "Final number of consecutive unopened accounts", example = "3", type = "Long")
        val gapCount: Long,
        @field:Schema(description = "Number of account rows created by this recovery", example = "8", type = "Long")
        val recoveredCount: Long,
        @field:Schema(description = "Number of requested indexes that were already persisted", example = "2", type = "Long")
        val existingCount: Long,
        @field:Schema(description = "Recovered account rows")
        val accounts: List<RecoveredAccountResponse>,
    )

    @Serializable
    @Schema(name = "RecoveredAccountResponse", description = "Recovered account row and scan state")
    data class RecoveredAccountResponse(
        @field:Schema(
            description = "Bare recovered account address path used by API routes",
            example = "aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2",
            type = "String",
        )
        @Serializable(with = AttoAddressAsPathStringSerializer::class)
        val address: AttoAddress,
        @field:Schema(
            description = "Display form of the recovered account address",
            example = "atto://aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2",
            type = "String",
        )
        val displayAddress: String,
        @field:Schema(description = "Deterministic wallet account index", example = "0", type = "Long")
        val index: AttoKeyIndex,
        @field:Schema(description = "Whether the account exists on-chain", example = "true")
        val opened: Boolean,
        @field:Schema(description = "Whether this request created the local account row", example = "false")
        val recovered: Boolean,
    )

    @Serializable
    @Schema(name = "ChangeRequest", description = "Representative change request")
    data class ChangeRequest(
        @field:Schema(
            description = "Address of the new representative account",
            example = "aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2",
            type = "String",
        )
        @Serializable(with = AttoAddressAsPathStringSerializer::class)
        val representativeAddress: AttoAddress,
    )

    @Serializable
    @Schema(name = "SendRequest", description = "Send transaction request")
    data class SendRequest(
        @field:Schema(
            description = "Address of the receiving account",
            example = "aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2",
            type = "String",
        )
        @Serializable(with = AttoAddressAsPathStringSerializer::class)
        val receiverAddress: AttoAddress,
        @field:Schema(description = "Amount to send in raw attos", example = "10000", type = "Long")
        val amount: AttoAmount,
        @field:Schema(
            description = "Optional last known account height used to reject stale or duplicate sends",
            example = "1",
            type = "Long",
        )
        val lastHeight: AttoHeight? = null,
    )

    @Serializable
    @Schema(name = "AccountDetails", description = "Live chain state for an opened account")
    data class AccountDetails(
        @field:Schema(
            description = "Address of the account",
            example = "aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2",
            type = "String",
        )
        @Serializable(with = AttoAddressAsPathStringSerializer::class)
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
        @Serializable(with = AttoInstantAsLongSerializer::class)
        val lastTransactionTimestamp: AttoInstant,
        @field:Schema(
            description = "Current representative address",
            example = "aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2",
            type = "String",
        )
        @Serializable(with = AttoAddressAsPathStringSerializer::class)
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
}
