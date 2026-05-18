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
                        "The Atto Wallet Server provides a simple, self-hostable interface to manage accounts, " +
                            "send and receive funds, and track balances within the Atto network. " +
                            "It is designed for use by applications, abstracting away the complexity of directly " +
                            "interacting with the node.",
                    ).version(version),
            ).externalDocs(
                ExternalDocumentation()
                    .description("Integration Docs")
                    .url("https://atto.cash/docs/integration"),
            )
    }
}
