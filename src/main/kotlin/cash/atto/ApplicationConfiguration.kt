package cash.atto

import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
class ApplicationConfiguration {
    @Bean
    fun openApi(environment: Environment): OpenAPI {
        val version = environment.getProperty("spring.application.version", "").ifEmpty { "dev" }
        return OpenAPI()
            .info(
                Info()
                    .title("Atto Wallet Server API")
                    .description(
                        "Reference Atto Wallet Server API endpoints for wallet creation, import and locking, " +
                            "account management, sends, balances and account history.\n\n" +
                            "This is a self-hosted service for applications, not the " +
                            "[browser wallet](https://atto.cash/wallet) or a CLI/MCP wallet profile. " +
                            "Match this reference to the Wallet Server release you deploy. " +
                            "Operators are responsible for API access controls, wallet secrets and payment authorization. " +
                            "Review the database, node and work-service requirements in the " +
                            "[Wallet Server setup guide](https://atto.cash/docs/integration/wallet-server).",
                    ).version(version),
            ).externalDocs(
                ExternalDocumentation()
                    .description("Atto Wallet Server setup and deployment")
                    .url("https://atto.cash/docs/integration/wallet-server"),
            )
    }
}
