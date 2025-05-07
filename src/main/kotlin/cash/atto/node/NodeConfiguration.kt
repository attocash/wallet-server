package cash.atto.node

import cash.atto.ApplicationProperties
import cash.atto.commons.node.AttoNodeOperations
import cash.atto.commons.node.custom
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
    fun nodeClient(
        applicationProperties: ApplicationProperties,
        properties: NodeProperties,
    ): AttoNodeOperations =
        AttoNodeOperations.custom(applicationProperties.network, properties.baseUrl) {
            emptyMap()
        }
}
