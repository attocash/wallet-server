package cash.atto.notification

import io.netty.handler.logging.LogLevel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.support.WebClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import reactor.netty.http.client.HttpClient
import reactor.netty.transport.logging.AdvancedByteBufFormat

@Configuration
@EnableScheduling
class NotificationConfiguration : WebFluxConfigurer {
    @Bean
    fun githubClient(
        properties: NotificationProperties,
        webClientBuilder: WebClient.Builder,
        environment: Environment,
    ): NotificationClient {
        val httpClient =
            HttpClient
                .create()
                .wiretap(
                    this.javaClass.canonicalName,
                    LogLevel.TRACE,
                    AdvancedByteBufFormat.TEXTUAL,
                )

        val port = environment.getProperty("server.port", "8080")

        val callbackUrl =
            if (properties.callbackUrl.isNotEmpty()) {
                properties.callbackUrl
            } else {
                "http://localhost:$port/noop"
            }
        val restClient =
            webClientBuilder
                .baseUrl(callbackUrl)
                .defaultHeaders { defaultHeaders ->
                    val header = properties.header
                    if (header != null && header.isNotEmpty()) {
                        defaultHeaders.set(header.key, header.value)
                    }
                }.clientConnector(ReactorClientHttpConnector(httpClient))
                .build()
        val adapter = WebClientAdapter.create(restClient)
        val factory = HttpServiceProxyFactory.builderFor(adapter).build()

        return factory.createClient(NotificationClient::class.java)
    }
}
