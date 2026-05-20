package cash.atto.work

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Configuration
@ConfigurationProperties(prefix = "atto.work")
class WorkProperties {
    lateinit var baseUrl: String
    var timeoutInSeconds: Long = 5 * 60

    fun timeoutDuration(): Duration = timeoutInSeconds.seconds
}
