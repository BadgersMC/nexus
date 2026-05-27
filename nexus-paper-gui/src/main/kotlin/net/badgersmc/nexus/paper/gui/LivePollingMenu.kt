package net.badgersmc.nexus.paper.gui

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.badgersmc.nexus.scheduler.NexusScheduler
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * Abstract base for ChestGuis that need to re-render their contents on a
 * fixed cadence (live auctions, queue browsers, leaderboards…). The base
 * handles:
 *
 * - Constructing the IFramework [ChestGui] with an Adventure title.
 * - Calling [render] once before showing the player.
 * - Starting a [NexusScheduler] repeat task that calls [render] every
 *   [refreshTicks], guarded so it stops when the player closes the menu.
 * - Cancelling the task automatically via `setOnClose`.
 *
 * Subclasses implement [render] to populate / repaint the GUI panes.
 */
abstract class LivePollingMenu(
    protected val scheduler: NexusScheduler,
    private val rows: Int = 6,
    private val refreshTicks: Long = 20L
) : MenuBase {

    /**
     * Build the GUI title. Called once at open time. Override to return a
     * MiniMessage-rendered Component sourced from your [net.badgersmc.nexus.i18n.LangService].
     */
    protected abstract fun title(): Component

    /**
     * Populate [gui] with its current state. Called both at open time and on
     * every refresh tick. Implementations typically `gui.panes.clear()` before
     * adding their pages so a redraw is clean.
     */
    protected abstract fun render(gui: ChestGui)

    override fun open(player: Player) {
        val gui = ChestGui(rows, ComponentHolder.of(title()))
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { it.isCancelled = true }

        render(gui)
        gui.show(player)

        val handle = scheduler.runRepeating(refreshTicks, refreshTicks) {
            // Stop refreshing if the player closed the inventory (or disconnected).
            if (player.openInventory.topInventory != gui.inventory) return@runRepeating
            render(gui)
            gui.update()
        }

        gui.setOnClose { handle.close() }
    }
}
