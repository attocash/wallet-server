package cash.atto.notification

import cash.atto.commons.AttoAddress
import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.core.annotation.Order
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.domain.Persistable
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.time.Instant

@Order(0)
interface NotificationStateRepository : CoroutineCrudRepository<NotificationState, String>

data class NotificationState(
    @Id
    val address: String,
    @Version
    val version: Long? = null,
    val height: Long,
    @JsonIgnore
    val persistedAt: Instant? = null,
    @JsonIgnore
    val updatedAt: Instant? = null,
) : Persistable<String> {
    companion object {}

    @JsonIgnore
    override fun getId(): String = address

    @JsonIgnore
    override fun isNew(): Boolean = persistedAt == null
}

fun NotificationState.Companion.emptyState(address: AttoAddress) =
    NotificationState(
        address = address.path,
        height = 0,
    )
