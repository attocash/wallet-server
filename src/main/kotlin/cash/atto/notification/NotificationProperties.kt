package cash.atto.notification

import cash.atto.notification.NotificationProperties.Header
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "atto.notification")
class NotificationProperties {
    lateinit var callbackUrl: String
    var header: Header? = null

    data class Header(
        val key: String,
        val value: String,
    )
}

fun Header.isNotEmpty(): Boolean = key.isNotEmpty() && value.isNotEmpty()
