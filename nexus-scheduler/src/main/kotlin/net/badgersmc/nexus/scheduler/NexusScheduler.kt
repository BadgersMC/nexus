package net.badgersmc.nexus.scheduler

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Backend abstraction over Bukkit's scheduler. Production wires this against
 * `Bukkit.getScheduler()`; tests substitute a deterministic implementation.
 *
 * All methods accept Bukkit-style tick units (20 ticks per second).
 */
interface SchedulerBackend {
    fun runOnMain(action: Runnable): TaskHandle
    fun runDelayedOnMain(delayTicks: Long, action: Runnable): TaskHandle
    fun runRepeatingOnMain(delayTicks: Long, periodTicks: Long, action: Runnable): TaskHandle
    fun runAsync(action: Runnable): TaskHandle
    fun runDelayedAsync(delayTicks: Long, action: Runnable): TaskHandle
    fun runRepeatingAsync(delayTicks: Long, periodTicks: Long, action: Runnable): TaskHandle

    fun isPrimaryThread(): Boolean
}

/**
 * Handle to a scheduled task. Cancelling is always safe — multiple
 * cancellations are no-ops.
 */
interface TaskHandle {
    fun cancel()
    val isCancelled: Boolean
}

/**
 * Default backend used in production. Delegates to `Bukkit.getScheduler()`.
 */
class BukkitSchedulerBackend(private val plugin: JavaPlugin) : SchedulerBackend {
    override fun runOnMain(action: Runnable): TaskHandle =
        Bukkit.getScheduler().runTask(plugin, action).toHandle()

    override fun runDelayedOnMain(delayTicks: Long, action: Runnable): TaskHandle =
        Bukkit.getScheduler().runTaskLater(plugin, action, delayTicks).toHandle()

    override fun runRepeatingOnMain(delayTicks: Long, periodTicks: Long, action: Runnable): TaskHandle =
        Bukkit.getScheduler().runTaskTimer(plugin, action, delayTicks, periodTicks).toHandle()

    override fun runAsync(action: Runnable): TaskHandle =
        Bukkit.getScheduler().runTaskAsynchronously(plugin, action).toHandle()

    override fun runDelayedAsync(delayTicks: Long, action: Runnable): TaskHandle =
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, action, delayTicks).toHandle()

    override fun runRepeatingAsync(delayTicks: Long, periodTicks: Long, action: Runnable): TaskHandle =
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, action, delayTicks, periodTicks).toHandle()

    override fun isPrimaryThread(): Boolean = Bukkit.isPrimaryThread()

    private fun BukkitTask.toHandle(): TaskHandle = object : TaskHandle {
        override fun cancel() {
            if (!isCancelled) this@toHandle.cancel()
        }
        override val isCancelled: Boolean get() = this@toHandle.isCancelled
    }
}

/**
 * Coroutine-aware Bukkit scheduler facade. Every method returns an
 * [AutoCloseable] whose [close] cancels the underlying task. All outstanding
 * tasks are tracked in [active]; [cancelAll] is called from `onDisable` so the
 * plugin shuts down cleanly.
 *
 * Construct one per plugin and register it as a Nexus bean.
 */
class NexusScheduler(private val backend: SchedulerBackend) {

    constructor(plugin: JavaPlugin) : this(BukkitSchedulerBackend(plugin))

    private val active: MutableSet<TrackedTask> = Collections.newSetFromMap(ConcurrentHashMap())

    /** Run [action] on the main server thread on the next tick. */
    fun runOnMain(action: () -> Unit): AutoCloseable =
        track(backend.runOnMain(wrap(action)))

    /** Run [action] on the main server thread after [delayTicks] ticks. */
    fun runDelayed(delayTicks: Long, action: () -> Unit): AutoCloseable =
        track(backend.runDelayedOnMain(delayTicks, wrap(action)))

    /**
     * Run [action] on the main server thread every [periodTicks] starting
     * after [initialDelayTicks]. The returned handle's [AutoCloseable.close]
     * cancels the repeat.
     */
    fun runRepeating(initialDelayTicks: Long, periodTicks: Long, action: () -> Unit): AutoCloseable =
        track(backend.runRepeatingOnMain(initialDelayTicks, periodTicks, wrap(action)))

    /** Run [action] off-thread. */
    fun runAsync(action: () -> Unit): AutoCloseable =
        track(backend.runAsync(wrap(action)))

    /** Run [action] off-thread after [delayTicks]. */
    fun runDelayedAsync(delayTicks: Long, action: () -> Unit): AutoCloseable =
        track(backend.runDelayedAsync(delayTicks, wrap(action)))

    /** Run [action] off-thread every [periodTicks] starting after [initialDelayTicks]. */
    fun runRepeatingAsync(initialDelayTicks: Long, periodTicks: Long, action: () -> Unit): AutoCloseable =
        track(backend.runRepeatingAsync(initialDelayTicks, periodTicks, wrap(action)))

    /**
     * Throw [IllegalStateException] if not currently on the main server thread.
     * Cheap call — leave it on the hot path of any Bukkit-mutating code.
     */
    fun requireMainThread() {
        check(backend.isPrimaryThread()) {
            "Operation requires main server thread; called from ${Thread.currentThread().name}"
        }
    }

    /**
     * Throw [IllegalStateException] if currently on the main server thread.
     * Use to flag blocking I/O that must not run on the tick loop.
     */
    fun requireAsyncThread() {
        check(!backend.isPrimaryThread()) {
            "Operation must run async; called from main thread"
        }
    }

    /**
     * Cancel every outstanding task. Call from `onDisable`.
     */
    fun cancelAll() {
        val snapshot = active.toList()
        for (task in snapshot) {
            task.close()
        }
    }

    /** Number of tasks currently registered as active. Mostly for tests. */
    val activeCount: Int get() = active.size

    private fun wrap(action: () -> Unit): Runnable = Runnable {
        try {
            action()
        } catch (e: Exception) {
            // Don't let recoverable exceptions kill the scheduler — log and
            // continue. JVM Errors (OutOfMemoryError, LinkageError, …) are
            // deliberately not caught: those signal conditions the scheduler
            // cannot meaningfully recover from.
            java.util.logging.Logger.getLogger(NexusScheduler::class.java.name)
                .log(java.util.logging.Level.WARNING, "Scheduled task threw", e)
        }
    }

    private fun track(handle: TaskHandle): AutoCloseable {
        val tracked = TrackedTask(handle, this)
        active.add(tracked)
        return tracked
    }

    internal fun forget(task: TrackedTask) {
        active.remove(task)
    }
}

internal class TrackedTask(
    private val handle: TaskHandle,
    private val scheduler: NexusScheduler
) : AutoCloseable {
    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        handle.cancel()
        scheduler.forget(this)
    }
}
