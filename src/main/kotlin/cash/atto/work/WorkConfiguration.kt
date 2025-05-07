package cash.atto.work

import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.remote
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.reactive.config.WebFluxConfigurer

@Configuration
@EnableScheduling
class WorkConfiguration : WebFluxConfigurer {
    @Bean
    @ConditionalOnMissingBean
    fun worker(properties: WorkProperties): AttoWorker = AttoWorker.remote(properties.baseUrl)
}
