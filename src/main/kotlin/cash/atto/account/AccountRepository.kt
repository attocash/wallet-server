package cash.atto.account

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.coroutines.flow.Flow
import org.springframework.core.annotation.Order
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.domain.Persistable
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.time.Instant

@Order(1)
interface AccountRepository : CoroutineCrudRepository<Account, String> {
    fun findAllByWalletName(walletName: String): Flow<Account>
}

class Account(
    @field:Schema(description = "The address of the account", example = "aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2")
    @Id
    val address: String,
    @field:Schema(
        description = "The database version of the row used for optimistic locking",
        example = "1",
    )
    @Version
    val version: Long? = null,
    @field:Schema(
        description = "The index of the account in the mnemonic",
        example = "1",
    )
    val accountIndex: Long,
    @field:Schema(
        description = "Wallet name",
        example = "treasury",
    )
    val walletName: String,
    @JsonIgnore
    val persistedAt: Instant? = null,
    @JsonIgnore
    val updatedAt: Instant? = null,
    @field:Schema(
        description = "Timestamp when the account was disabled",
        example = "treasury",
    )
    var disabledAt: Instant? = null,
) : Persistable<String> {
    @JsonIgnore
    override fun getId(): String = address

    @JsonIgnore
    override fun isNew(): Boolean = persistedAt == null
}
