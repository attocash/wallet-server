package cash.atto

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Duration

private val openApiBodyChecks =
    listOf(
        "$.paths['/wallets/accounts/{address}/transactions/SEND'].post.requestBody",
        "$.paths['/wallets/accounts/{address}/transactions/SEND'].post.responses['200'].content",
        "$.paths['/wallets/accounts/{address}/transactions/CHANGE'].post.requestBody",
        "$.paths['/wallets/accounts/{address}/transactions/CHANGE'].post.responses['200'].content",
        "$.paths['/wallets/{name}'].put.requestBody",
        "$.paths['/wallets/{name}'].put.responses['200'].content",
        "$.paths['/wallets/{name}/locks/UNLOCKED'].put.requestBody",
        "$.paths['/wallets/{walletName}/recoveries'].post.requestBody",
        "$.paths['/wallets/{walletName}/recoveries'].post.responses['200'].content",
    )

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiTest {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun `generates api docs`() {
        val openApi =
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

        openApi.jsonPath("$.openapi").isEqualTo("3.1.0")
        openApiBodyChecks.forEach { openApi.jsonPath(it).exists() }
    }
}
