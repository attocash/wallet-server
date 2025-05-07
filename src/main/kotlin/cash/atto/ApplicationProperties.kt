package cash.atto

import cash.atto.commons.AttoNetwork
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "atto")
class ApplicationProperties {
    lateinit var network: AttoNetwork
}
