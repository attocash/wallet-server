package cash.atto.work

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "atto.work")
class WorkProperties {
    lateinit var baseUrl: String
}
