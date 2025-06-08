package cash.atto.notification

import cash.atto.account.AccountService
import cash.atto.account.toAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoHeight
import cash.atto.commons.node.AttoNodeOperations
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import kotlin.time.Duration.Companion.seconds

@Service
class Notifier(
    private val notificationStateRepository: NotificationStateRepository,
    private val accountService: AccountService,
    private val nodeOperations: AttoNodeOperations,
    private val notificationClient: NotificationClient,
) {
    private val logger = KotlinLogging.logger {}

    private val stateMap = HashMap<AttoAddress, NotificationState>()
    private val mutex = Mutex()

    @PostConstruct
    fun init() {
        runBlocking {
            stateMap.putAll(notificationStateRepository.findAll().toList().associateBy { AttoAddress.parsePath(it.address) })

            accountService.getAccountMap(true).forEach { (address, _) ->
                if (stateMap.containsKey(address)) {
                    return@forEach
                }

                stateMap[address] = NotificationState.emptyState(address)
            }
        }
    }

    @Scheduled(fixedRate = 1_000)
    suspend fun notify() {
        if (mutex.isLocked) {
            return
        }
        mutex.withLock {
            val accountHeights = accountService.getAccountMap(true).mapValues { it.value?.height ?: AttoHeight(0U) }

            accountHeights.forEach { (address, height) ->
                val state =
                    stateMap[address] ?: run {
                        val state = NotificationState.emptyState(address)
                        notificationStateRepository.save(state)
                        notificationStateRepository.findById(address.path)!!
                    }

                stateMap[address] = state

                val updartedState = notify(state, height)

                if (state != updartedState) {
                    stateMap[address] = notificationStateRepository.save(updartedState)
                }
            }
        }
    }

    private suspend fun notify(
        state: NotificationState,
        latestHeight: AttoHeight,
    ): NotificationState {
        val stateHeight = AttoHeight(state.height.toULong())
        if (stateHeight >= latestHeight) {
            return state
        }

        val newHeight = stateHeight + 1UL

        val entry =
            withTimeoutOrNull(60.seconds) {
                nodeOperations
                    .accountEntryStream(AttoAddress.parsePath(state.address).publicKey, newHeight, newHeight)
                    .first()
            }

        if (entry == null) {
            return state
        }

        return try {
            notificationClient.publish(entry.toAccountEntry())
            state.copy(height = newHeight.value.toLong())
        } catch (e: Exception) {
            logger.error(e) { "Failed to callback $entry" }
            state
        }
    }
}
