package cash.atto.account

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoKeyIndex
import cash.atto.commons.AttoSeed
import cash.atto.commons.node.AttoNodeClient
import cash.atto.commons.toAttoIndex
import cash.atto.commons.toSeed
import cash.atto.commons.toSigner
import cash.atto.wallet.WalletService
import kotlinx.coroutines.flow.toList
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class AccountRecoverer(
    private val accountRepository: AccountRepository,
    private val accountService: AccountService,
    private val walletService: WalletService,
    private val nodeClient: AttoNodeClient,
) {
    private val logger = KotlinLogging.logger {}

    @Transactional
    suspend fun recover(
        walletName: String,
        gapLimit: UInt,
    ): Recovery {
        if (gapLimit == 0U) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "gapLimit must be greater than 0")
        }

        val seed = getUnlockedSeed(walletName)
        val persistedAccounts =
            accountRepository
                .findAllByWalletName(walletName)
                .toList()
                .associateBy { it.index }
        val fromIndex = persistedAccounts.keys.maxByOrNull { it.value } ?: 0U.toAttoIndex()

        val scannedAccounts = seed.scanAccountUntilGap(fromIndex, gapLimit)
        val scannedIndexes = scannedAccounts.map { it.index }
        val finalGapCount = scannedAccounts.finalGapCount()
        val toIndex = scannedAccounts.lastOpenedIndex() ?: scannedIndexes.last()

        val recoveredAccounts =
            accountService
                .createMultiple(walletName, toIndex)
                .associateBy { it.index }
        val recoveredIndexes = fromIndex.toIndexRange(toIndex)
        val recoveredCount = recoveredIndexes.count { it !in persistedAccounts }.toUInt()
        val existingCount = recoveredIndexes.count { it in persistedAccounts }.toUInt()
        val scannedAccountMap = scannedAccounts.associateBy { it.index }

        logger.info {
            "Recovered wallet $walletName accounts from $fromIndex to $toIndex " +
                "($recoveredCount recovered, $existingCount existing, $finalGapCount gaps)"
        }

        return Recovery(
            fromIndex = fromIndex,
            toIndex = toIndex,
            scannedCount = scannedAccounts.size.toUInt(),
            gapCount = finalGapCount,
            recoveredCount = recoveredCount,
            existingCount = existingCount,
            accounts =
                recoveredIndexes.map { index ->
                    val account = recoveredAccounts.getValue(index)
                    RecoveredAccount(
                        account = account,
                        opened = scannedAccountMap[index]?.account != null,
                        recovered = index !in persistedAccounts,
                    )
                },
        )
    }

    private suspend fun getUnlockedSeed(walletName: String): AttoSeed {
        val mnemonic = walletService.getMnemonic(walletName)
        if (mnemonic == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Wallet $walletName is locked")
        }

        return mnemonic.toSeed()
    }

    private val Account.index: AttoKeyIndex get() = accountIndex.toUInt().toAttoIndex()

    private fun AttoKeyIndex.toIndexRange(toIndex: AttoKeyIndex): List<AttoKeyIndex> =
        buildList {
            var value = this@toIndexRange.value
            while (value <= toIndex.value) {
                add(value.toAttoIndex())
                if (value == UInt.MAX_VALUE) {
                    break
                }
                value++
            }
        }

    private suspend fun AttoSeed.scanAccountUntilGap(
        fromIndex: AttoKeyIndex,
        gapLimit: UInt,
    ): List<ScannedAccount> {
        val scannedAccounts = mutableListOf<ScannedAccount>()
        var index = fromIndex
        var gaps = 0U

        while (gaps < gapLimit) {
            val scannedAccount = scanAccount(index)
            scannedAccounts += scannedAccount

            gaps =
                if (scannedAccount.account == null) {
                    gaps + 1U
                } else {
                    0U
                }

            if (gaps >= gapLimit || index.value == UInt.MAX_VALUE) {
                break
            }

            index = (index.value + 1U).toAttoIndex()
        }

        return scannedAccounts
    }

    private suspend fun AttoSeed.scanAccount(index: AttoKeyIndex): ScannedAccount {
        val signer = toSigner(index)
        return ScannedAccount(
            index = index,
            address = signer.address,
            account = nodeClient.account(signer.publicKey),
        )
    }

    private fun List<ScannedAccount>.finalGapCount(): UInt {
        var gaps = 0U
        for (scannedAccount in asReversed()) {
            if (scannedAccount.account != null) {
                return gaps
            }
            gaps++
        }
        return gaps
    }

    private fun List<ScannedAccount>.lastOpenedIndex(): AttoKeyIndex? =
        asReversed()
            .firstOrNull { it.account != null }
            ?.index

    private data class ScannedAccount(
        val index: AttoKeyIndex,
        val address: AttoAddress,
        val account: AttoAccount? = null,
    )

    data class Recovery(
        val fromIndex: AttoKeyIndex,
        val toIndex: AttoKeyIndex,
        val scannedCount: UInt,
        val gapCount: UInt,
        val recoveredCount: UInt,
        val existingCount: UInt,
        val accounts: List<RecoveredAccount>,
    )

    data class RecoveredAccount(
        val account: Account,
        val opened: Boolean,
        val recovered: Boolean,
    )
}
