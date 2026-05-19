package cash.atto.work

import cash.atto.commons.node.AttoWorkerMock
import cash.atto.commons.node.create
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.remote
import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TestWorkConfiguration {
    @Bean(destroyMethod = "close")
    fun attoWorkerMock(): AttoWorkerMock =
        runBlocking {
            AttoWorkerMock
                .create()
                .also { it.start() }
        }

    @Bean
    fun mockWorker(mock: AttoWorkerMock): AttoWorker = AttoWorker.remote(mock.baseUrl)
}
