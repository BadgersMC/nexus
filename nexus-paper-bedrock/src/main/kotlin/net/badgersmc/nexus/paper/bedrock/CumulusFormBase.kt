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
 */
abstract class CumulusFormBase(
    protected val player: Player,
    protected val logger: Logger,
    protected val lang: LangService
) : MenuBase {

    abstract fun buildForm(): Form

    override fun open(player: Player) {
        try {
            val form = buildForm()
            sendForm(form)
            logger.fine("Opened ${this::class.simpleName} for ${player.name}")
        } catch (e: Exception) {
            logger.warning("Failed to open Bedrock menu for ${player.name}: ${e.message}")
            player.sendMessage(lang.msg(MENU_ERROR_KEY))
        }
    }

    /** Open for testing — subclasses can override to assert dispatch. */
    protected open fun sendForm(form: Form) {
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
