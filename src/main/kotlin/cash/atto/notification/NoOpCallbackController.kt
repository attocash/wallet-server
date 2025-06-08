package cash.atto.notification

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class NoOpCallbackController {
    @PostMapping("/noop")
    fun noOpCallback() {
    }
}
