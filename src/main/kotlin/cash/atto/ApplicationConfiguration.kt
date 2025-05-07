package cash.atto

import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.function.client.ExchangeStrategies

@Configuration
@EnableScheduling
class ApplicationConfiguration : WebFluxConfigurer {
    @Bean
    fun walletServerOpenAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Atto Wallet Server API")
                    .description(
                        "The Atto Wallet Server provides a simple, self-hostable interface to manage accounts, " +
                            "send and receive funds, and track balances within the Atto network. " +
                            "It is designed for use by applications, abstracting away the complexity of directly " +
                            "interacting with the node.",
                    ).version("v1.0.0"),
            ).externalDocs(
                ExternalDocumentation()
                    .description("Docs")
                    .url("https://atto.cash/docs"),
            )

    override fun configureHttpMessageCodecs(configurer: ServerCodecConfigurer) {
        configurer
            .defaultCodecs()
        configurer
            .defaultCodecs()
    }

    @Bean
    fun exchangeStrategies(): ExchangeStrategies =
        ExchangeStrategies
            .builder()
            .codecs { configurer: ClientCodecConfigurer ->
                configurer
                    .defaultCodecs()
                configurer
                    .defaultCodecs()
            }.build()
}
