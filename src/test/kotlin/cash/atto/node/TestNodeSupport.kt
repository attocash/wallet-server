package cash.atto.node

import cash.atto.CacheSupport
import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoSigner
import cash.atto.commons.AttoTransaction
import cash.atto.commons.node.AttoNodeOperations
import cash.atto.commons.worker.AttoWorker
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicReference

class TestNodeSupport(
    private val nodeOperations: AttoNodeOperations,
    private val worker: AttoWorker,
    private val genesisSigner: AttoSigner,
) : CacheSupport {
    private val fundingAccount = AtomicReference<AttoAccount?>()

    suspend fun sendTo(
        receiverAddress: AttoAddress,
        amount: AttoAmount,
    ) {
        val account = fundingAccount.get() ?: loadFundingAccount()
        val (block, updatedAccount) = account.send(receiverAddress.algorithm, receiverAddress.publicKey, amount)
        val transaction =
            AttoTransaction(
                block = block,
                signature = genesisSigner.sign(block),
                work = worker.work(block),
            )

        nodeOperations.publish(transaction)
        fundingAccount.set(updatedAccount)
    }

    override fun clear() {
        fundingAccount.set(null)
    }

    private suspend fun loadFundingAccount(): AttoAccount {
        repeat(100) {
            nodeOperations.account(genesisSigner.publicKey)?.let { return it }
            delay(100)
        }

        error("Genesis account was not available from AttoNodeMock")
    }
}
