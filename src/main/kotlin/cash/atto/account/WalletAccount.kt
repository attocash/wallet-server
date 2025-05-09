package cash.atto.account

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.atomic.AtomicBoolean

class WalletAccount(
    val network: AttoNetwork,
    val address: AttoAddress,
    val walletName: String,
    private val transactionPublisher: suspend (AttoBlock) -> AttoTransaction,
) {
    private val disabled = AtomicBoolean(false)

    private val mutex = Mutex()

    @Volatile
    var account: AttoAccount? = null
        private set

    val enabled: Boolean
        get() = !disabled.get()

    fun disable() {
        disabled.set(true)
    }

    fun enable() {
        disabled.set(false)
    }

    suspend fun update(account: AttoAccount) {
        mutex.withLock {
            this.account = account
        }
    }

    suspend fun receive(receivable: AttoReceivable): AttoTransaction {
        mutex.withLock {
            val (block, updatedAccount) =
                if (account == null) {
                    AttoAccount.open(AttoAlgorithm.V1, address.publicKey, receivable, network)
                } else {
                    account!!.receive(receivable)
                }
            return publish(block, updatedAccount)
        }
    }

    suspend fun change(representativeAddress: AttoAddress): AttoTransaction {
        mutex.withLock {
            val (block, updatedAccount) = account!!.change(representativeAddress.algorithm, representativeAddress.publicKey)
            return publish(block, updatedAccount)
        }
    }

    suspend fun send(
        receiverAddress: AttoAddress,
        amount: AttoAmount,
        lastHeight: AttoHeight,
    ): AttoTransaction {
        mutex.withLock {
            val account = account!!

            if (account.height != lastHeight) {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Given height for $address does not match last known height ${account.height}. List all the newest transactions and try again!",
                )
            }

            if (amount > account.balance) {
                throw IllegalArgumentException("Not enough balance.")
            }
            val (block, updatedAccount) = account.send(receiverAddress.algorithm, receiverAddress.publicKey, amount)
            return publish(block, updatedAccount)
        }
    }

    suspend fun publish(
        block: AttoBlock,
        updatedAccount: AttoAccount,
    ): AttoTransaction {
        val transaction = transactionPublisher.invoke(block)
        account = updatedAccount
        return transaction
    }
}
