package cash.atto

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.reactive.TransactionSynchronization
import org.springframework.transaction.reactive.TransactionSynchronizationManager
import reactor.core.publisher.Mono

suspend fun ApplicationEventPublisher.publishAfterCommit(event: Any) {
    val manager = TransactionSynchronizationManager.forCurrentTransaction().awaitSingle()
    manager.registerSynchronization(
        object : TransactionSynchronization {
            override fun afterCommit(): Mono<Void> =
                Mono.fromRunnable {
                    publishEvent(event)
                }
        },
    )
}
