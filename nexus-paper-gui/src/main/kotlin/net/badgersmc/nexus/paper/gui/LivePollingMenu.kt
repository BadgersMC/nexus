package net.badgersmc.nexus.paper.gui

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.badgersmc.nexus.scheduler.NexusScheduler
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * Abstract base for ChestGuis that re-render their contents on a fixed
 * cadence (live auctions, queue browsers, leaderboards, …).
 *
 * The base handles:
 *
 * - Constructing the IFramework [ChestGui] with an Adventure title.
 * - Calling [render] once before showing the player.
 * - Optionally running a [prefetch] step **off the main thread** every
 *   [refreshTicks] so JDBC queries / OfflinePlayer name resolution / other
 *   blocking work never stalls the tick loop. Subclasses that don't need
 *   prefetching leave the default no-op in place.
 * - Starting a [NexusScheduler] repeat task on the **main thread** that
 *   calls [render], guarded so it stops when the player closes the menu.
 * - Cancelling both tasks automatically via `setOnClose`.
 *
 * Recommended pattern: do all blocking work inside [prefetch] (publishing
 * its results into a `@Volatile` / `AtomicReference` snapshot), then read
 * the snapshot inside [render] without touching I/O.
 */
abstract class LivePollingMenu(
    protected val scheduler: NexusScheduler,
    private val rows: Int = 6,
    private val refreshTicks: Long = 20L
) : MenuBase {

    /**
     * Build the GUI title. Called once at open time. Override to return a
     * MiniMessage-rendered Component sourced from your
     * [net.badgersmc.nexus.i18n.LangService].
     */
    protected abstract fun title(): Component

    /**
     * Populate [gui] with its current state. Called both at open time
     * (main thread) and on every refresh tick (main thread).
     * Implementations typically `gui.panes.clear()` before adding their
     * panes so a redraw is clean.
     *
     * **Must not block.** If you need to read from a database or resolve
     * offline-player names, do it in [prefetch] and cache the result.
     */
    protected abstract fun render(gui: ChestGui)

    /**
     * Off-main-thread pre-render hook. Called once before the menu is
     * shown (synchronously, on the calling thread — typically a command
     * handler) and then every [refreshTicks] ticks on an async scheduler
     * task. Override to fetch fresh data and publish it into whatever
     * holder [render] reads from.
     *
     * Default: no-op. Subclasses without blocking work can ignore this.
     *
     * Exceptions thrown here are swallowed so a transient repository
     * failure doesn't kill the menu — the next tick retries and [render]
     * continues to show the last good snapshot.
     */
    protected open fun prefetch() { /* no-op */ }

    override fun open(player: Player) {
        val gui = ChestGui(rows, ComponentHolder.of(title()))
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { it.isCancelled = true }

        // First snapshot is fetched on the calling thread so the initial
        // frame isn't empty. The cost is one-time per menu open.
        runCatching { prefetch() }
        render(gui)
        gui.show(player)

        // Async tick: refresh the snapshot off-thread.
        val prefetchHandle = scheduler.runRepeatingAsync(refreshTicks, refreshTicks) {
            runCatching { prefetch() }
        }

        // Main-thread tick: repaint from the (now refreshed) snapshot.
        val renderHandle = scheduler.runRepeating(refreshTicks, refreshTicks) {
            // Stop refreshing if the player closed the inventory (or disconnected).
            if (player.openInventory.topInventory != gui.inventory) return@runRepeating
            render(gui)
            gui.update()
        }

        gui.setOnClose {
            prefetchHandle.close()
            renderHandle.close()
        }
    }
}
