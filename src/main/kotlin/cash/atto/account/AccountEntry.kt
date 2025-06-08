package cash.atto.account

import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlockType
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.serialiazer.AttoAddressAsStringSerializer
import cash.atto.commons.serialiazer.InstantMillisSerializer
import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Schema(name = "AccountEntry", description = "An user-friendly view of account activity")
@Serializable
data class AccountEntry(
    @field:Schema(
        description = "Unique hash of the block",
        example = "68BA42CDD87328380BE32D5AA6DBB86E905B50273D37AF1DE12F47B83A001154",
        type = "String",
    )
    val hash: AttoHash,
    @field:Schema(
        description = "Address of the account",
        example = "aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2",
        type = "String",
    )
    @Serializable(with = AttoAddressAsStringSerializer::class)
    val address: AttoAddress,
    @field:Schema(description = "Block height", example = "0", type = "Long")
    val height: AttoHeight,
    @field:Schema(description = "Type of block in the account chain", example = "RECEIVE")
    val blockType: AttoBlockType,
    @field:Schema(
        description = "Address of the subject involved in the transaction",
        example = "aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2",
    )
    @Serializable(with = AttoAddressAsStringSerializer::class)
    val subjectAddress: AttoAddress,
    @field:Schema(
        description = "Public key of the subject involved in the transaction",
        example = "2EB21717813E7A0E0A7E308B8E2FD8A051F8724F5C5F0047E92E19310C582E3A",
    )
    val previousBalance: AttoAmount,
    @field:Schema(description = "Balance before this block", example = "0")
    val balance: AttoAmount,
    @field:Schema(description = "Balance after this block", example = "100")
    @Serializable(with = InstantMillisSerializer::class)
    val timestamp: Instant,
)

fun AttoAccountEntry.toAccountEntry(): AccountEntry =
    AccountEntry(
        hash = this.hash,
        address = this.address,
        height = this.height,
        blockType = this.blockType,
        subjectAddress = this.subjectAddress,
        previousBalance = this.previousBalance,
        balance = this.balance,
        timestamp = this.timestamp,
    )
