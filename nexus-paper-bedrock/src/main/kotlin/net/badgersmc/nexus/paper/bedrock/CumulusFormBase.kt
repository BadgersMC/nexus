package net.badgersmc.nexus.paper.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.gui.MenuBase
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.Form
import org.geysermc.floodgate.api.FloodgateApi
import java.util.logging.Logger

/**
 * Base class for Bedrock Cumulus form menus. Mirrors the
 * `BedrockMenuBase` pattern from EnthusiaMarket but with [LangService]
 * folded in so error messages come from the lang file rather than being
 * hardcoded.
 *
 * Subclasses implement [buildForm] and the base handles dispatching via
 * [FloodgateApi] plus catching dispatch errors gracefully.
 *
 * **Note on optional dependencies:** Cumulus and Floodgate are declared
 * `compileOnly` in `nexus-paper-bedrock`'s build script. Consumer plugins
 * that link this module are expected to declare them as `softdepend` in
 * their `paper-plugin.yml` AND guard usage behind
 * [PlatformDetectionService.isCumulusAvailable] / `isFloodgateAvailable`.
 * Loading this class on a server without Cumulus/Floodgate will throw
 * `NoClassDefFoundError`, by design — the platform-detection probes let
 * you avoid that path entirely.
 */
abstract class CumulusFormBase(
    protected val logger: Logger,
    protected val lang: LangService
) : MenuBase {

    abstract fun buildForm(): Form

    override fun open(player: Player) {
        try {
            val form = buildForm()
            sendForm(player, form)
            logger.fine("Opened ${this::class.simpleName} for ${player.name}")
        } catch (e: Exception) {
            logger.log(java.util.logging.Level.WARNING, "Failed to open Bedrock menu for ${player.name}", e)
            player.sendMessage(lang.msg(MENU_ERROR_KEY))
        }
    }

    /** Open for testing — subclasses can override to assert dispatch. */
    protected open fun sendForm(player: Player, form: Form) {
        FloodgateApi.getInstance().sendForm(player.uniqueId, form)
    }

    companion object {
        /**
         * Lang key consulted when form dispatch fails. Consumers add this to
         * their lang file (or override [open] entirely).
         */
        const val MENU_ERROR_KEY: String = "bedrock.menu_error"
    }
}
