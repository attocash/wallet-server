package cash.atto.account

import cash.atto.CacheSupport
import cash.atto.account.AccountController.AccountCreationResponse
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoUnit
import cash.atto.commons.node.AttoMockNode
import cash.atto.commons.toAttoVersion
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.springframework.boot.test.web.client.TestRestTemplate
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AccountStepDefinition(
    private val testRestTemplate: TestRestTemplate,
    private val mockNode: AttoMockNode,
) : CacheSupport {
    var address: String? = null

    @When("a new address is created in {word} wallet")
    fun create(walletName: String) {
        val response = testRestTemplate.postForObject("/wallets/$walletName/accounts", null, AccountCreationResponse::class.java)
        address = response.address.path
    }

    @When("address is disabled")
    fun disable() {
        testRestTemplate.postForLocation("/wallets/accounts/$address/states/DISABLED", null)
    }

    @When("address is enabled")
    fun enable() {
        testRestTemplate.postForLocation("/wallets/accounts/$address/states/ENABLED", null)
    }

    @When("account receives {word} attos")
    fun createReceivable(amount: String) {
        val receivable =
            AttoReceivable(
                version = 0U.toAttoVersion(),
                algorithm = AttoAlgorithm.V1,
                publicKey = AttoPublicKey(Random.Default.nextBytes(32)),
                receiverAlgorithm = AttoAlgorithm.V1,
                receiverPublicKey = AttoAddress.parsePath(address!!).publicKey,
                amount = AttoAmount.from(AttoUnit.ATTO, amount),
                timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds() - 10000L),
                hash = AttoHash(Random.Default.nextBytes(32)),
            )
        runBlocking {
            mockNode.receivableFlow.emit(receivable)
        }
    }

    @When("account sends {word} attos")
    fun send(amount: String) {
        val receiverAddress = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32)))
        val request = AccountController.SendRequest(receiverAddress, AttoAmount.from(AttoUnit.ATTO, amount))
        testRestTemplate.postForObject("/wallets/accounts/$address/transactions/SEND", request, AccountController.AccountEntry::class.java)
    }

    @When("account representative changes to {word}")
    fun change(representativeName: String) {
        val publicKey = representativeName.toRepresentativePublicKey()

        val representativeAddress = AttoAddress(AttoAlgorithm.V1, publicKey)
        val request = AccountController.ChangeRequest(representativeAddress)
        testRestTemplate.postForObject(
            "/wallets/accounts/$address/transactions/CHANGE",
            request,
            AccountController.AccountEntry::class.java,
        )
    }

    private fun getAccount(): Account = testRestTemplate.getForObject("/wallets/accounts/$address", Account::class.java)

    private fun getAccountDetails(): AccountController.AccountDetails? =
        testRestTemplate.getForObject("/wallets/accounts/$address/details", AccountController.AccountDetails::class.java)

    @Then("account should be created")
    fun checkCreated() {
        val response = getAccount()
        assertEquals(address, response.address)
    }

    @Then("account should be disabled")
    fun checkDisabled() {
        val response = getAccount()
        assertNotNull(response.disabledAt)
    }

    @Then("account should be enabled")
    fun checkEnabled() {
        val response = getAccount()
        assertNull(response.disabledAt)
    }

    @Then("account balance is {word} attos")
    fun checkEnabled(amount: String) =
        runBlocking {
            val amount = AttoAmount.from(AttoUnit.ATTO, amount)
            withTimeoutOrNull(60_000) {
                do {
                    val accountDetails = getAccountDetails()
                    if (accountDetails?.balance == amount) {
                        return@withTimeoutOrNull
                    }
                    delay(100)
                } while (isActive)
            }
        }

    @Then("account representative is {word}")
    fun checkRepresentative(representativeName: String) =
        runBlocking {
            val representativePublicKey = representativeName.toRepresentativePublicKey()

            withTimeoutOrNull(60_000) {
                do {
                    val accountDetails = getAccountDetails()
                    if (accountDetails?.representativeAddress == AttoAddress(AttoAlgorithm.V1, representativePublicKey)) {
                        return@withTimeoutOrNull
                    }
                    delay(100)
                } while (isActive)
            }
        }

    private fun String.toRepresentativePublicKey(): AttoPublicKey = AttoPublicKey(AttoHash.hash(32, this.toByteArray()).value)

    override fun clear() {
        address = null
    }
}
