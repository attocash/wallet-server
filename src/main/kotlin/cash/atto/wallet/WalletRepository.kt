package cash.atto.wallet

import cash.atto.commons.AttoMnemonic
import org.springframework.core.annotation.Order
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.domain.Persistable
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Order(1)
interface WalletRepository : CoroutineCrudRepository<Wallet, String>

suspend fun WalletRepository.getById(name: String): Wallet =
    findById(name) ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Wallet with $name not found")

class Wallet(
    @Id
    val name: String,
    @Version
    val version: Long? = null,
    val encryptedEntropy: ByteArray,
    var encryptedEncryptionKey: ByteArray?,
    val persistedAt: Instant? = null,
    val updatedAt: Instant? = null,
) : Persistable<String> {
    override fun getId(): String = name

    override fun isNew(): Boolean = persistedAt == null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Wallet

        if (version != other.version) return false
        if (name != other.name) return false
        if (!encryptedEntropy.contentEquals(other.encryptedEntropy)) return false
        if (!encryptedEncryptionKey.contentEquals(other.encryptedEncryptionKey)) return false
        if (persistedAt != other.persistedAt) return false
        if (updatedAt != other.updatedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version?.hashCode() ?: 0
        result = 31 * result + name.hashCode()
        result = 31 * result + encryptedEntropy.contentHashCode()
        result = 31 * result + (encryptedEncryptionKey?.contentHashCode() ?: 0)
        result = 31 * result + (persistedAt?.hashCode() ?: 0)
        result = 31 * result + (updatedAt?.hashCode() ?: 0)
        return result
    }
}

interface WalletEvent {
    val name: String
    val mnemonic: AttoMnemonic?
}

data class WalletCreated(
    override val name: String,
    override val mnemonic: AttoMnemonic,
) : WalletEvent

data class WalletUnlocked(
    override val name: String,
    override val mnemonic: AttoMnemonic,
) : WalletEvent

data class WalletLocked(
    override val name: String,
) : WalletEvent {
    override val mnemonic = null
}
