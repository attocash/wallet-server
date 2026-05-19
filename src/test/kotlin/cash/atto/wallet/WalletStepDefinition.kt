package cash.atto.wallet

import cash.atto.CacheSupport
import cash.atto.getForObject
import cash.atto.postForObject
import cash.atto.put
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals

class WalletStepDefinition(
    private val webTestClient: WebTestClient,
) : CacheSupport {
    var encryptionKey: String? = null

    @Given("the {word} wallet has been imported")
    fun create(walletName: String) {
        val response = webTestClient.postForObject<WalletController.WalletCreationResponse>("/wallets/$walletName", null)
        encryptionKey = response.encryptionKey
    }

    @When("the {word} wallet is locked")
    fun lock(walletName: String) {
        webTestClient.put("/wallets/$walletName/locks/LOCKED", null)
    }

    @When("the {word} wallet is unlocked")
    fun unlock(walletName: String) {
        webTestClient.put("/wallets/$walletName/locks/UNLOCKED", WalletController.WalletUnlockRequest(encryptionKey!!))
    }

    @Then("the {word} wallet status should be {word}")
    fun checkStatus(
        walletName: String,
        lockState: String,
    ) {
        val state = webTestClient.getForObject<WalletController.WalletState>("/wallets/$walletName")
        assertEquals(state.lockState.toString(), lockState)
    }

    override fun clear() {
        encryptionKey = null
    }
}
