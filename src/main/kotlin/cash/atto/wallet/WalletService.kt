package cash.atto.wallet

import cash.atto.ChaCha20
import cash.atto.commons.AttoMnemonic
import cash.atto.commons.fromHexToByteArray
import cash.atto.commons.toHex
import cash.atto.publishAfterCommit
import kotlinx.coroutines.flow.toList
import mu.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class WalletService(
    private val repository: WalletRepository,
    private val properties: WalletProperties,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional
    suspend fun import(
        name: String,
        mnemonic: AttoMnemonic,
        encryptionKey: String,
    ) {
        val encryptedEntropy = ChaCha20.encrypt(mnemonic.toEntropy(), encryptionKey)
        val encryptedEncryptionKey = ChaCha20.encrypt(encryptionKey.fromHexToByteArray(), properties.chaCha20EncryptionKey!!)

        val wallet =
            Wallet(
                name = name,
                encryptedEntropy = encryptedEntropy,
                encryptedEncryptionKey = encryptedEncryptionKey,
            )

        repository.save(wallet)

        eventPublisher.publishAfterCommit(WalletCreated(name, mnemonic))

        logger.info { "Wallet $name created" }
    }

    @Transactional
    suspend fun lock(name: String) {
        val wallet = repository.getById(name)

        wallet.encryptedEncryptionKey = null

        eventPublisher.publishAfterCommit(WalletLocked(name))

        repository.save(wallet)

        logger.info { "Wallet $name locked" }
    }

    @Transactional
    suspend fun unlock(
        name: String,
        encryptionKey: String,
    ) {
        val wallet = repository.getById(name)

        val mnemonic =
            try {
                val entropy = ChaCha20.decrypt(wallet.encryptedEntropy, encryptionKey)
                AttoMnemonic(entropy)
            } catch (e: Exception) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid encryption key for $name", e)
            }

        wallet.encryptedEncryptionKey = ChaCha20.encrypt(encryptionKey.fromHexToByteArray(), properties.chaCha20EncryptionKey!!)

        eventPublisher.publishAfterCommit(WalletUnlocked(name, mnemonic))

        repository.save(wallet)

        logger.info { "Wallet $name unlocked" }
    }

    private fun Wallet.toMnemonic(): AttoMnemonic? {
        val encryptedEncryptionKey = encryptedEncryptionKey

        if (encryptedEncryptionKey == null) {
            return null
        }

        val encryptionKey = ChaCha20.decrypt(encryptedEncryptionKey, properties.chaCha20EncryptionKey!!)

        val entropy = ChaCha20.decrypt(encryptedEntropy, encryptionKey.toHex())

        return AttoMnemonic(entropy)
    }

    suspend fun getMnemonicMap(): Map<String, AttoMnemonic?> {
        val wallets = repository.findAll().toList()
        return wallets.associate { it.name to it.toMnemonic() }
    }
}
