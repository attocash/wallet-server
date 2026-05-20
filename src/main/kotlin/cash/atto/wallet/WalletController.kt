package cash.atto.wallet

import cash.atto.ChaCha20
import cash.atto.commons.AttoMnemonic
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody

@RestController
@RequestMapping("/wallets")
@Tag(
    name = "Wallets",
    description =
        "Create, import, lock, unlock, list, and inspect wallets in the self-hosted Atto Wallet Server. " +
            "Wallets store encrypted mnemonic entropy at rest and load their decryption key only while unlocked.",
)
class WalletController(
    private val service: WalletService,
    private val repository: WalletRepository,
) {
    private val logger = KotlinLogging.logger {}

    @GetMapping
    @Operation(
        summary = "List all wallets",
        description = "Lists every wallet stored on the server with its current lock state.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Stored wallets",
                content = [
                    Content(
                        array = ArraySchema(schema = Schema(implementation = WalletState::class)),
                    ),
                ],
            ),
        ],
    )
    suspend fun list(): Flow<WalletState> = repository.findAll().map { it.toState() }

    @GetMapping("/{name}")
    @Operation(
        summary = "Get wallet metadata",
        description = "Retrieves metadata and lock state for one wallet.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Wallet metadata",
                content = [
                    Content(
                        schema = Schema(implementation = WalletState::class),
                    ),
                ],
            ),
            ApiResponse(responseCode = "404", description = "Wallet does not exist"),
        ],
    )
    suspend fun get(
        @PathVariable name: String,
    ): WalletState = repository.getById(name).toState()

    private suspend fun import(
        name: String,
        mnemonic: AttoMnemonic,
        encryptionKey: String?,
    ): ResponseEntity<WalletCreationResponse> {
        val password = encryptionKey ?: ChaCha20.generateKey()

        service.import(name, mnemonic, password)

        val response =
            WalletCreationResponse(
                mnemonic = mnemonic.words.joinToString(" "),
                encryptionKey = password,
            )

        return ResponseEntity.ok(response)
    }

    @PutMapping("/{name}")
    @Operation(
        summary = "Import existing wallet",
        description =
            "Imports a wallet from its 24-word mnemonic and stores the encrypted mnemonic entropy. " +
                "If encryptionKey is omitted, the server generates one and returns it in the response.",
        requestBody =
            OpenApiRequestBody(
                required = true,
                description = "Mnemonic to import and optional encryption key to use for this wallet.",
                content = [
                    Content(
                        schema = Schema(implementation = WalletImportRequest::class),
                    ),
                ],
            ),
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Wallet imported",
                content = [
                    Content(
                        schema = Schema(implementation = WalletCreationResponse::class),
                    ),
                ],
            ),
            ApiResponse(responseCode = "400", description = "Mnemonic or encryption key is invalid"),
            ApiResponse(responseCode = "409", description = "Wallet name already exists"),
        ],
    )
    suspend fun import(
        @PathVariable name: String,
        @RequestBody request: WalletImportRequest,
    ): ResponseEntity<WalletCreationResponse> {
        val mnemonic =
            try {
                AttoMnemonic(request.mnemonic)
            } catch (e: Exception) {
                logger.warn(e) { "Invalid mnemonic" }
                return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .build<WalletCreationResponse>()
            }

        return import(name, mnemonic, request.encryptionKey)
    }

    @PostMapping("/{name}")
    @Operation(
        summary = "Create a wallet",
        description =
            "Generates a new wallet with a fresh mnemonic and encryption key. The response is the only time " +
                "the generated mnemonic and key are returned together.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Wallet created",
                content = [
                    Content(
                        schema = Schema(implementation = WalletCreationResponse::class),
                    ),
                ],
            ),
            ApiResponse(responseCode = "409", description = "Wallet name already exists"),
        ],
    )
    suspend fun createWallet(
        @PathVariable name: String,
    ): ResponseEntity<WalletCreationResponse> = import(name = name, mnemonic = AttoMnemonic.generate(), encryptionKey = null)

    @PutMapping("/{name}/locks/LOCKED")
    @Operation(
        summary = "Lock wallet",
        description = "Removes the wallet encryption key from server storage, preventing signing until the wallet is unlocked again.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Wallet locked",
            ),
            ApiResponse(responseCode = "404", description = "Wallet does not exist"),
        ],
    )
    suspend fun lock(
        @PathVariable name: String,
    ) {
        service.lock(name)
    }

    @PutMapping("/{name}/locks/UNLOCKED")
    @Operation(
        summary = "Unlock wallet",
        description =
            "Validates the provided wallet encryption key and stores it encrypted with `CHA_CHA20_KEY_ENCRYPTION_KEY`.",
        requestBody =
            OpenApiRequestBody(
                required = true,
                description = "Encryption key originally returned when the wallet was created or imported.",
                content = [
                    Content(
                        schema = Schema(implementation = WalletUnlockRequest::class),
                    ),
                ],
            ),
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Wallet unlocked",
            ),
            ApiResponse(responseCode = "400", description = "Encryption key is invalid"),
            ApiResponse(responseCode = "404", description = "Wallet does not exist"),
        ],
    )
    suspend fun unlock(
        @PathVariable name: String,
        @RequestBody request: WalletUnlockRequest,
    ) {
        service.unlock(name, request.encryptionKey)
    }

    enum class LockState {
        LOCKED,
        UNLOCKED,
    }

    @Schema(name = "WalletState", description = "View of the current wallet state")
    @Serializable
    data class WalletState(
        @field:Schema(description = "Wallet name", example = "treasury")
        val name: String,
        @field:Schema(description = "Whether signing material is currently available", example = "UNLOCKED")
        val lockState: LockState,
    )

    private fun Wallet.toState(): WalletState {
        val lockState =
            if (encryptedEncryptionKey != null) {
                LockState.UNLOCKED
            } else {
                LockState.LOCKED
            }
        return WalletState(name, lockState)
    }

    @Schema(name = "WalletImportRequest", description = "Request to import a wallet from a mnemonic")
    @Serializable
    data class WalletImportRequest(
        @field:Schema(
            description = "24-word wallet mnemonic",
            example =
                "florbit nuster glenth ravax drindle sporkel quenth brimzo kraddle yempth plarnix chuzzle grintop vornish daprex " +
                    "slindle frumple zorgat mekton yindle cravix blanter swooshle prindle",
        )
        val mnemonic: String,
        @field:Schema(
            description =
                "Optional 32-byte hex encoded Cha Cha 20 key used to encrypt the mnemonic at rest. " +
                    "If omitted, the server generates one.",
            example = "0000000000000000000000000000000000000000000000000000000000000000",
        )
        val encryptionKey: String? = null,
    )

    @Schema(name = "WalletCreationResponse", description = "Wallet mnemonic and encryption key returned after create or import")
    @Serializable
    data class WalletCreationResponse(
        @field:Schema(
            description = "24-word wallet mnemonic",
            example =
                "florbit nuster glenth ravax drindle sporkel quenth brimzo kraddle yempth plarnix chuzzle grintop vornish daprex " +
                    "slindle frumple zorgat mekton yindle cravix blanter swooshle prindle",
        )
        val mnemonic: String,
        @field:Schema(
            description = "32-byte hex encoded Cha Cha 20 key used to encrypt the mnemonic at rest",
            example = "0000000000000000000000000000000000000000000000000000000000000000",
        )
        val encryptionKey: String,
    )

    @Schema(name = "WalletUnlockRequest", description = "Represents a request to unlock the wallet")
    @Serializable
    data class WalletUnlockRequest(
        @field:Schema(
            description = "32-byte hex encoded Cha Cha 20 key used to decrypt the wallet mnemonic",
            example = "0000000000000000000000000000000000000000000000000000000000000000",
        )
        val encryptionKey: String,
    )
}
