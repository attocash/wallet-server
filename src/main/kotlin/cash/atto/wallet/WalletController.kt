package cash.atto.wallet

import cash.atto.ChaCha20
import cash.atto.commons.AttoMnemonic
import io.swagger.v3.oas.annotations.Operation
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

@RestController
@RequestMapping("/wallets")
@Tag(
    name = "Wallets",
    description =
        "Manage wallets in the self-hosted Atto Wallet Server. " +
            "This controller allows you to create new wallets, import existing ones using mnemonics, " +
            "lock and unlock wallets by managing their in-memory decryption keys, and list or retrieve wallet information. " +
            "Designed for applications that require secure and programmatic control over local wallets.",
)
class WalletController(
    private val service: WalletService,
    private val repository: WalletRepository,
) {
    private val logger = KotlinLogging.logger {}

    @GetMapping
    @Operation(
        summary = "List all wallets",
        description = "Lists every wallet stored on the server together with its current lock state.",
        responses = [
            ApiResponse(
                responseCode = "200",
                content = [
                    Content(
                        schema = Schema(implementation = WalletState::class),
                    ),
                ],
            ),
        ],
    )
    suspend fun list(): Flow<WalletState> = repository.findAll().map { it.toState() }

    @GetMapping("/{name}")
    @Operation(
        summary = "Get wallet metadata",
        description = "Retrieves metadata for the wallet identified by *name*.",
        responses = [
            ApiResponse(
                responseCode = "200",
                content = [
                    Content(
                        schema = Schema(implementation = WalletState::class),
                    ),
                ],
            ),
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
            "Imports a wallet from its 24‑word mnemonic. You may supply your own Cha Cha 20 encryption key; if omitted, " +
                "the server generates one. Losing either the mnemonic or key means permanent loss of access.",
        requestBody =
            io.swagger.v3.oas.annotations.parameters.RequestBody(
                required = true,
                content = [
                    Content(
                        schema = Schema(implementation = WalletImportRequest::class),
                    ),
                ],
            ),
        responses = [
            ApiResponse(
                responseCode = "200",
                content = [
                    Content(
                        schema = Schema(implementation = WalletCreationResponse::class),
                    ),
                ],
            ),
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
            "Generates a brand‑new wallet with a fresh mnemonic and encryption key, both encrypted at rest. " +
                "Losing either will lock the wallet forever.",
        responses = [
            ApiResponse(
                responseCode = "200",
                content = [
                    Content(
                        schema = Schema(implementation = WalletCreationResponse::class),
                    ),
                ],
            ),
        ],
    )
    suspend fun createWallet(
        @PathVariable name: String,
    ): ResponseEntity<WalletCreationResponse> = import(name = name, mnemonic = AttoMnemonic.generate(), encryptionKey = null)

    @PutMapping("/{name}/locks/LOCKED")
    @Operation(
        summary = "Lock wallet",
        description = "Purges the decryption key for the wallet, preventing signing operations until it is unlocked again.",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
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
            "Loads the provided encryptionKey into memory and stores it securely by encrypting it " +
                "at rest using the `CHA_CHA20_KEY_ENCRYPTION_KEY`.",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
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
        val name: String,
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

    @Schema(name = "WalletImportRequest", description = "Represents a request to import an wallet")
    @Serializable
    data class WalletImportRequest(
        @field:Schema(
            description = "24 words mnemonic",
            example =
                "florbit nuster glenth ravax drindle sporkel quenth brimzo kraddle yempth plarnix chuzzle grintop vornish daprex " +
                    "slindle frumple zorgat mekton yindle cravix blanter swooshle prindle",
        )
        val mnemonic: String,
        @field:Schema(
            description =
                "Optional 32 bytes hex encoded Cha Cha 20 encryption key used to encrypt mnemonic at rest. " +
                    "If not provided the wallet will generate one.",
            example = "0000000000000000000000000000000000000000000000000000000000000000",
        )
        val encryptionKey: String? = null,
    )

    @Schema(name = "WalletCreationResponse", description = "Represents a request to create an wallet")
    @Serializable
    data class WalletCreationResponse(
        @field:Schema(
            description = "24 words mnemonic",
            example =
                "florbit nuster glenth ravax drindle sporkel quenth brimzo kraddle yempth plarnix chuzzle grintop vornish daprex " +
                    "slindle frumple zorgat mekton yindle cravix blanter swooshle prindle",
        )
        val mnemonic: String,
        @field:Schema(
            description = "32 bytes hex encoded Cha Cha 20 encryption key used to encrypt mnemonic at rest",
            example = "0000000000000000000000000000000000000000000000000000000000000000",
        )
        val encryptionKey: String,
    )

    @Schema(name = "WalletUnlockRequest", description = "Represents a request to unlock the wallet")
    @Serializable
    data class WalletUnlockRequest(
        @field:Schema(
            description = "32 bytes hex encoded Cha Cha 20 encryption key used to encrypt mnemonic at rest",
            example = "0000000000000000000000000000000000000000000000000000000000000000",
        )
        val encryptionKey: String,
    )
}
