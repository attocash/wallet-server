package cash.atto.node

import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoSigner
import cash.atto.commons.node.AttoNodeClient
import cash.atto.commons.node.AttoNodeMock
import cash.atto.commons.node.AttoNodeOperations
import cash.atto.commons.node.create
import cash.atto.commons.node.remote
import cash.atto.commons.toSigner
import cash.atto.commons.worker.AttoWorker
import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TestNodeConfiguration {
    @Bean
    fun genesisPrivateKey(): AttoPrivateKey = AttoPrivateKey.generate()

    @Bean
    fun genesisSigner(privateKey: AttoPrivateKey): AttoSigner = privateKey.toSigner()

    @Bean(destroyMethod = "close")
    fun attoNodeMock(privateKey: AttoPrivateKey): AttoNodeMock =
        runBlocking {
            AttoNodeMock
                .create(privateKey)
                .also { it.start() }
        }

    @Bean
    fun mockNode(mock: AttoNodeMock): AttoNodeClient =
        AttoNodeClient.remote(mock.baseUrl) {
            emptyMap()
        }

    @Bean
    fun testNodeSupport(
        nodeOperations: AttoNodeOperations,
        worker: AttoWorker,
        genesisSigner: AttoSigner,
    ): TestNodeSupport =
        TestNodeSupport(
            nodeOperations = nodeOperations,
            worker = worker,
            genesisSigner = genesisSigner,
        )
}
