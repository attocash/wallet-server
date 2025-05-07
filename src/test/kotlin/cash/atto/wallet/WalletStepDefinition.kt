package cash.atto.wallet

import cash.atto.CacheSupport
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.springframework.boot.test.web.client.TestRestTemplate
import kotlin.test.assertEquals

class WalletStepDefinition(
    private val testRestTemplate: TestRestTemplate,
) : CacheSupport {
    var encryptionKey: String? = null

    @Given("the {word} wallet has been imported")
    fun create(walletName: String) {
        val response = testRestTemplate.postForObject("/wallets/$walletName", null, WalletController.WalletCreationResponse::class.java)
        encryptionKey = response.encryptionKey
    }

    @When("the {word} wallet is locked")
    fun lock(walletName: String) {
        testRestTemplate.put("/wallets/$walletName/locks/LOCKED", null)
    }

    @When("the {word} wallet is unlocked")
    fun unlock(walletName: String) {
        testRestTemplate.put("/wallets/$walletName/locks/UNLOCKED", WalletController.WalletUnlockRequest(encryptionKey!!))
    }

    @Then("the {word} wallet status should be {word}")
    fun checkStatus(
        walletName: String,
        lockState: String,
    ) {
        val state = testRestTemplate.getForObject("/wallets/$walletName", WalletController.WalletState::class.java)
        assertEquals(state.lockState.toString(), lockState)
    }

    override fun clear() {
        encryptionKey = null
    }
}
