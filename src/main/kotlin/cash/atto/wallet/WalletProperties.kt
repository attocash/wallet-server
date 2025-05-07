package cash.atto.wallet

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "atto.wallet")
class WalletProperties {
    var chaCha20EncryptionKey: String? = null
}
