package cash.atto.notification

import cash.atto.account.AccountEntry
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.PostExchange

interface NotificationClient {
    @PostExchange
    suspend fun publish(
        @RequestBody request: AccountEntry,
    )
}
