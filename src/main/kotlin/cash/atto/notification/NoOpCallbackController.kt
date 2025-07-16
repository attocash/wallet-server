package cash.atto.notification

import io.swagger.v3.oas.annotations.Hidden
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Hidden
class NoOpCallbackController {
    @PostMapping("/noop")
    fun noOpCallback() {
    }
}
