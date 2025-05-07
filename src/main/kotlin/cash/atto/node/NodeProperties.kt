package cash.atto.node

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "atto.node")
class NodeProperties {
    lateinit var baseUrl: String
}
