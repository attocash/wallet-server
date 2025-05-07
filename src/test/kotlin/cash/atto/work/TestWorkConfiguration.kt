package cash.atto.work

import cash.atto.commons.node.AttoMockWorker
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.remote
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TestWorkConfiguration {
    @Bean(initMethod = "start", destroyMethod = "stop")
    fun mockWorker(): AttoMockWorker = AttoMockWorker()

    @Bean
    fun mockWorkerClient(mocWorker: AttoMockWorker): AttoWorker = AttoWorker.remote("http://localhost:${mocWorker.port}")
}
