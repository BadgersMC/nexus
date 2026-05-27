package net.badgersmc.nexus.paper.bedrock

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformDetectionServiceTest {

    @Test
    fun `isBedrockPlayer returns false when Floodgate is absent or not initialised`() {
        // The test classpath has cumulus + floodgate-api but no running
        // FloodgateApi singleton — getInstance() returns null / throws, so the
        // service must report `false` rather than blowing up.
        val service = PlatformDetectionService()
        assertFalse(service.isBedrockPlayer(UUID.randomUUID()))
    }

    @Test
    fun `isCumulusAvailable reflects classpath presence`() {
        val service = PlatformDetectionService()
        assertTrue(service.isCumulusAvailable())
    }

    @Test
    fun `isFloodgateAvailable reflects classpath presence`() {
        val service = PlatformDetectionService()
        assertTrue(service.isFloodgateAvailable())
    }
}
