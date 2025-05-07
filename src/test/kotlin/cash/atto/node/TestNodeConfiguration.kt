package cash.atto.node

import cash.atto.commons.AttoNetwork
import cash.atto.commons.node.AttoMockNode
import cash.atto.commons.node.AttoNodeOperations
import cash.atto.commons.node.custom
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TestNodeConfiguration {
    @Bean(initMethod = "start", destroyMethod = "stop")
    fun mockNode(): AttoMockNode = AttoMockNode()

    @Bean
    fun mockNodeClient(mockNode: AttoMockNode): AttoNodeOperations =
        AttoNodeOperations.custom(AttoNetwork.LOCAL, "http://localhost:${mockNode.port}") {
            emptyMap()
        }
}
