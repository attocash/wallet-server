package cash.atto

import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.scheduling.annotation.EnableScheduling

@ImportRuntimeHints(SpringDocWorkaround::class)
@Configuration
@EnableScheduling
class ApplicationConfiguration {
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
                    .description("Integration Docs")
                    .url("https://atto.cash/docs/integration"),
            )
}

class SpringDocWorkaround : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        hints.reflection().registerType(
            TypeReference.of("org.springframework.core.convert.support.GenericConversionService\$Converters"),
            *MemberCategory.entries.toTypedArray()
        )
    }
}
