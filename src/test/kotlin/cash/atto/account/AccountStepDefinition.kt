package cash.atto.account

import cash.atto.CacheSupport
import cash.atto.account.AccountController.AccountCreationResponse
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoUnit
import cash.atto.commons.node.AccountHeightSearch
import cash.atto.commons.node.HeightSearch
import cash.atto.commons.toAttoHeight
import cash.atto.getForObject
import cash.atto.node.TestNodeSupport
import cash.atto.postForJsonArray
import cash.atto.postForLocation
import cash.atto.postForObject
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountStepDefinition(
    private val webTestClient: WebTestClient,
    private val testNodeSupport: TestNodeSupport,
) : CacheSupport {
    var address: String? = null
    private var lastResponse: Response? = null
    private var lastStatuses: List<Int> = emptyList()

    @When("a new address is created in {word} wallet")
    fun create(walletName: String) {
        val response = webTestClient.postForObject<AccountCreationResponse>("/wallets/$walletName/accounts", null)
        address = response.address.path
    }

    @When("address is disabled")
    fun disable() {
        webTestClient.postForLocation("/wallets/accounts/$address/states/DISABLED", null)
    }

    @When("address is enabled")
    fun enable() {
        webTestClient.postForLocation("/wallets/accounts/$address/states/ENABLED", null)
    }

    @When("account receives {word} attos")
    fun receive(amount: String) =
        runBlocking {
            testNodeSupport.sendTo(
                receiverAddress = AttoAddress.parse(address!!),
                amount = AttoAmount.from(AttoUnit.ATTO, amount),
            )
        }

    @When("account sends {word} attos")
    fun send(amount: String) {
        val request = sendRequest(amount)
        webTestClient.postForObject<AccountEntry>("/wallets/accounts/$address/transactions/SEND", request)
    }

    @When("account tries to send {word} attos")
    fun trySend(amount: String) {
        lastResponse = sendForResult(sendRequest(amount)).toResponse()
    }

    @When("two concurrent sends of {word} attos use the same last height")
    fun concurrentSend(amount: String) =
        runBlocking {
            val lastHeight = getAccountDetails()?.height ?: error("Account details not available for $address")
            val request = sendRequest(amount, lastHeight)

            lastStatuses =
                coroutineScope {
                    listOf(
                        async(Dispatchers.Default) { sendForResult(request).status.value() },
                        async(Dispatchers.Default) { sendForResult(request).status.value() },
                    ).awaitAll()
                }
        }

    @When("account representative changes to {word}")
    fun change(representativeName: String) {
        val publicKey = representativeName.toRepresentativePublicKey()

        val representativeAddress = AttoAddress(AttoAlgorithm.V1, publicKey)
        val request = AccountController.ChangeRequest(representativeAddress)
        webTestClient.postForObject<AccountEntry>(
            "/wallets/accounts/$address/transactions/CHANGE",
            request,
        )
    }

    private fun getAccount(): Account = webTestClient.getForObject("/wallets/accounts/$address")

    private fun getAccountDetails(): AccountController.AccountDetails? {
        val result =
            webTestClient
                .get()
                .uri("/wallets/accounts/$address/details")
                .exchange()
                .returnResult(AccountController.AccountDetails::class.java)

        if (result.status.value() == 404) {
            return null
        }

        check(result.status.is2xxSuccessful) { "Unexpected account details status: ${result.status}" }
        return result.responseBody.blockFirst()
    }

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
            val matched =
                withTimeoutOrNull(60_000) {
                    do {
                        val accountDetails = getAccountDetails()
                        if (accountDetails?.balance == amount) {
                            return@withTimeoutOrNull true
                        }
                        delay(100)
                    } while (isActive)
                    false
                } ?: false

            assertTrue(matched, "Account balance did not become $amount")
        }

    @Then("account representative is {word}")
    fun checkRepresentative(representativeName: String) =
        runBlocking {
            val representativePublicKey = representativeName.toRepresentativePublicKey()

            val representativeAddress = AttoAddress(AttoAlgorithm.V1, representativePublicKey)
            val matched =
                withTimeoutOrNull(60_000) {
                    do {
                        val accountDetails = getAccountDetails()
                        if (accountDetails?.representativeAddress == representativeAddress) {
                            return@withTimeoutOrNull true
                        }
                        delay(100)
                    } while (isActive)
                    false
                } ?: false

            assertTrue(matched, "Account representative did not become $representativeAddress")
        }

    @Then("one send should succeed and one should conflict")
    fun checkConcurrentSendResult() {
        assertEquals(listOf(200, 409), lastStatuses.sorted())
    }

    @Then("request should fail because account is not open")
    fun checkAccountNotOpenFailure() {
        val response = lastResponse ?: error("No response captured")

        assertEquals(404, response.status)
        assertTrue(
            response.body?.contains("is not open yet. Please receive some attos before try again") == true,
            "Unexpected response body: ${response.body}",
        )
    }

    @Then("request should fail because the {word} wallet is locked")
    fun checkWalletLockedFailure(walletName: String) {
        val response = lastResponse ?: error("No response captured")

        assertEquals(400, response.status)
        assertTrue(
            response.body?.contains("Wallet $walletName is locked") == true,
            "Unexpected response body: ${response.body}",
        )
    }

    @When("entry is added")
    fun addEntry() {
        send("1")
    }

    @Then("entries are streamable")
    fun stream() {
        val accountHeight =
            AccountHeightSearch(
                address = AttoAddress.parse(address!!),
                fromHeight = 1UL.toAttoHeight(),
            )
        val entries1 =
            webTestClient.postForJsonArray(
                "/wallets/accounts/entries",
                HeightSearch(listOf(accountHeight)),
            )

        val entries2 = webTestClient.postForJsonArray("/wallets/accounts/entries", null)

        assertFalse(entries1.isEmpty())
        assertEquals(entries1.size, entries2.size)
    }

    private fun String.toRepresentativePublicKey(): AttoPublicKey = AttoPublicKey(AttoHash.hash(32, this.toByteArray()).value)

    private fun sendRequest(
        amount: String,
        lastHeight: AttoHeight? = null,
    ): AccountController.SendRequest {
        val receiverAddress = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32)))
        return AccountController.SendRequest(receiverAddress, AttoAmount.from(AttoUnit.ATTO, amount), lastHeight)
    }

    private fun sendForResult(request: AccountController.SendRequest): EntityExchangeResult<String> =
        webTestClient
            .post()
            .uri("/wallets/accounts/$address/transactions/SEND")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectBody(String::class.java)
            .returnResult()

    private fun EntityExchangeResult<String>.toResponse(): Response = Response(status.value(), responseBody)

    override fun clear() {
        address = null
        lastResponse = null
        lastStatuses = emptyList()
    }

    private data class Response(
        val status: Int,
        val body: String?,
    )
}
