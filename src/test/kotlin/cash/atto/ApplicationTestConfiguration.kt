package cash.atto

import io.netty.handler.logging.LogLevel
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ClientHttpConnector
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import org.testcontainers.containers.MySQLContainer
import reactor.netty.http.client.HttpClient
import reactor.netty.transport.logging.AdvancedByteBufFormat

@Configuration
class ApplicationTestConfiguration {
    @Bean
    @ServiceConnection
    fun mysqlContainer(): MySQLContainer<*> {
        val container = MySQLContainer("mysql:8.2")
        container.withUsername("root")
        return container
    }

    @Bean
    fun webClient(
        webClientBuilder: WebClient.Builder,
        exchangeStrategies: ExchangeStrategies,
    ): WebClient {
        val httpClient: HttpClient =
            HttpClient
                .create()
                .wiretap(
                    this.javaClass.canonicalName,
                    LogLevel.DEBUG,
                    AdvancedByteBufFormat.TEXTUAL,
                )
        val connector: ClientHttpConnector = ReactorClientHttpConnector(httpClient)
        return webClientBuilder
            .exchangeStrategies(exchangeStrategies)
            .clientConnector(connector)
            .build()
    }
}
