package net.badgersmc.nexus

import net.badgersmc.nexus.annotations.*
import net.badgersmc.nexus.core.NexusContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NexusTest {

    @Test
    fun `should create context and resolve singleton beans`() {
        val context = NexusContext.create("net.badgersmc.nexus", listOf(TestService::class, TestRepository::class))

        val service = context.getBean<TestService>()
        assertNotNull(service)

        // Singleton should return same instance
        val service2 = context.getBean<TestService>()
        assertTrue(service === service2)

        context.close()
    }

    @Test
    fun `should inject dependencies via constructor`() {
        val context = NexusContext.create("net.badgersmc.nexus", listOf(TestService::class, TestRepository::class))

        val service = context.getBean<TestService>()
        assertNotNull(service.repository)

        context.close()
    }

    @Test
    fun `should invoke post construct`() {
        val context = NexusContext.create("net.badgersmc.nexus", listOf(TestService::class, TestRepository::class))

        val service = context.getBean<TestService>()
        assertTrue(service.initialized)

        context.close()
    }

    @Test
    fun `should support manual bean registration`() {
        val context = NexusContext.create()

        val repo = TestRepository()
        context.registerBean("testRepository", TestRepository::class, repo)

        val retrieved = context.getBean<TestRepository>()
        assertEquals(repo, retrieved)

        context.close()
    }

    @Test
    fun `should throw when bean not found`() {
        val context = NexusContext.create()

        assertThrows<Exception> {
            context.getBean<TestService>()
        }

        context.close()
    }
}

@Service
class TestService(val repository: TestRepository) {
    var initialized = false

    @PostConstruct
    fun init() {
        initialized = true
    }

    @PreDestroy
    fun cleanup() {
        // Cleanup logic
    }
}

@Repository
class TestRepository {
    fun getData(): String = "test data"
}
