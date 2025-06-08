package cash.atto.notification

import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoHash
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.ConcurrentHashMap

@RestController
class MockCallbackController {
    private val callbackMap = ConcurrentHashMap<AttoHash, AttoAccountEntry>()

    @PostMapping("/callback")
    fun callback(
        @RequestBody entry: AttoAccountEntry,
    ) {
        callbackMap[entry.hash] = entry
    }
}
