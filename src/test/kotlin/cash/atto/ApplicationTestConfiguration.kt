package cash.atto

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.MySQLContainer

@Configuration
class ApplicationTestConfiguration {
    @Bean
    @ServiceConnection
    fun mysqlContainer(): MySQLContainer<*> {
        val container = MySQLContainer("mysql:8.2")
        container.withUsername("root")
        return container
    }
}
