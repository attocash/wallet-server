package cash.atto.account

import org.springframework.core.annotation.Order
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.domain.Persistable
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.time.Instant

@Order(0)
interface AccountRepository : CoroutineCrudRepository<Account, String>

class Account(
    @Id
    val address: String,
    @Version
    val version: Long? = null,
    val accountIndex: Long,
    val walletName: String,
    val persistedAt: Instant? = null,
    val updatedAt: Instant? = null,
    var disabledAt: Instant? = null,
) : Persistable<String> {
    override fun getId(): String = address

    override fun isNew(): Boolean = persistedAt == null
}
