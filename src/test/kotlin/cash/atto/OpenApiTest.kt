package cash.atto

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiTest {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun `generates api docs`() {
        webTestClient
            .mutate()
            .responseTimeout(Duration.ofSeconds(60))
            .build()
            .get()
            .uri("/v3/api-docs")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.openapi")
            .exists()
            .jsonPath("$.components.schemas.Account")
            .exists()
            .jsonPath("$.components.schemas.AccountCreationResponse")
            .exists()
            .jsonPath("$.components.schemas.RecoveredAccountResponse")
            .exists()
            .jsonPath("$.components.schemas.AccountEntry")
            .exists()
            .jsonPath("$.components.schemas.WalletState")
            .exists()
    }
}
