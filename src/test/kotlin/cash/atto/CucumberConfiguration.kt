package cash.atto

import io.cucumber.java.Before
import io.cucumber.spring.CucumberContextConfiguration
import kotlinx.coroutines.runBlocking
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@CucumberContextConfiguration
class CucumberConfiguration(
    val caches: List<CacheSupport>,
    val repositories: List<CoroutineCrudRepository<*, *>>,
) {
    @Before
    fun before() =
        runBlocking {
            caches.forEach { it.clear() }
            repositories.forEach { it.deleteAll() }
        }
}
