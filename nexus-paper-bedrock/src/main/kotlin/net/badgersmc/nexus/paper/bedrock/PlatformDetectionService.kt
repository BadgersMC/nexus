package net.badgersmc.nexus.paper.bedrock

import org.bukkit.entity.Player
import java.util.UUID

/**
 * Reflective probe for Bedrock-related runtime APIs. Floodgate and Cumulus
 * are optional at runtime — the consumer plugin's `paper-plugin.yml` lists
 * them as `required: false`. This service answers two questions:
 *
 * - Is the given player a Bedrock client (i.e. routed via Floodgate)?
 * - Is the Cumulus form library on the classpath at all?
 *
 * Both methods are safe to call regardless of whether the underlying plugins
 * are installed. They return `false` rather than throwing.
 */
open class PlatformDetectionService {

    /** Returns true if [player] is a Bedrock player connected via Floodgate. */
    open fun isBedrockPlayer(player: Player): Boolean = isBedrockPlayer(player.uniqueId)

    open fun isBedrockPlayer(uuid: UUID): Boolean {
        return try {
            val cls = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
            val instance = cls.getMethod("getInstance").invoke(null)
            val getPlayer = cls.getMethod("getPlayer", UUID::class.java)
            getPlayer.invoke(instance, uuid) != null
        } catch (_: Throwable) {
            false
        }
    }

    /** True if Cumulus is on the classpath. Use before opening a Bedrock form. */
    open fun isCumulusAvailable(): Boolean {
        return try {
            Class.forName("org.geysermc.cumulus.form.Form")
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** True if the Floodgate API is on the classpath. */
    open fun isFloodgateAvailable(): Boolean {
        return try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi")
            true
        } catch (_: Throwable) {
            false
        }
    }
}
