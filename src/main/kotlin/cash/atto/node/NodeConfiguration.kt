package cash.atto.node

import cash.atto.commons.node.AttoNodeClient
import cash.atto.commons.node.remote
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.reactive.config.WebFluxConfigurer

@Configuration
@EnableScheduling
class NodeConfiguration : WebFluxConfigurer {
    @Bean
    @ConditionalOnMissingBean
    fun nodeClient(properties: NodeProperties): AttoNodeClient =
        AttoNodeClient.remote(properties.baseUrl) {
            emptyMap()
        }
}
