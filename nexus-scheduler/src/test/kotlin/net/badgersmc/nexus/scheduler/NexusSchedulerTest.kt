package net.badgersmc.nexus.scheduler

import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NexusSchedulerTest {

    /**
     * Deterministic test backend. Each call records the action; tests drive
     * execution manually so we don't need a real server.
     */
    private class FakeBackend(var mainThread: Boolean = true) : SchedulerBackend {
        val tasks = mutableListOf<FakeTask>()
        var nextId = 0

        override fun runOnMain(action: Runnable) = record(action, repeating = false)
        override fun runDelayedOnMain(delayTicks: Long, action: Runnable) = record(action, delayTicks, false)
        override fun runRepeatingOnMain(delayTicks: Long, periodTicks: Long, action: Runnable) =
            record(action, delayTicks, true, periodTicks)
        override fun runAsync(action: Runnable) = record(action, repeating = false, async = true)
        override fun runDelayedAsync(delayTicks: Long, action: Runnable) =
            record(action, delayTicks, false, async = true)
        override fun runRepeatingAsync(delayTicks: Long, periodTicks: Long, action: Runnable) =
            record(action, delayTicks, true, periodTicks, async = true)

        override fun isPrimaryThread(): Boolean = mainThread

        private fun record(
            action: Runnable, delay: Long = 0L, repeating: Boolean = false,
            period: Long = 0L, async: Boolean = false
        ): TaskHandle {
            val task = FakeTask(nextId++, action, delay, repeating, period, async)
            tasks.add(task)
            return task
        }

        fun fire(taskId: Int) {
            val task = tasks.first { it.id == taskId }
            if (!task.cancelled) task.action.run()
        }
    }

    private class FakeTask(
        val id: Int,
        val action: Runnable,
        val delay: Long,
        val repeating: Boolean,
        val period: Long,
        val async: Boolean
    ) : TaskHandle {
        var cancelled: Boolean = false
            private set
        override fun cancel() { cancelled = true }
        override val isCancelled: Boolean get() = cancelled
    }

    @Test
    fun `runRepeating schedules a repeating task on the main thread`() {
        val backend = FakeBackend()
        val scheduler = NexusScheduler(backend)
        val counter = AtomicInteger(0)

        val handle = scheduler.runRepeating(20L, 20L) { counter.incrementAndGet() }

        assertEquals(1, scheduler.activeCount)
        val task = backend.tasks.single()
        assertEquals(20L, task.delay)
        assertEquals(20L, task.period)
        assertTrue(task.repeating)
        assertFalse(task.async)
        assertNotNull(handle)
    }

    @Test
    fun `close on returned handle cancels the underlying task`() {
        val backend = FakeBackend()
        val scheduler = NexusScheduler(backend)

        val handle = scheduler.runRepeating(0L, 10L) { }
        handle.close()

        assertTrue(backend.tasks.single().cancelled)
        assertEquals(0, scheduler.activeCount)
    }

    @Test
    fun `cancelAll closes every outstanding task`() {
        val backend = FakeBackend()
        val scheduler = NexusScheduler(backend)
        scheduler.runRepeating(0L, 10L) {}
        scheduler.runAsync {}
        scheduler.runDelayed(5L) {}
        assertEquals(3, scheduler.activeCount)

        scheduler.cancelAll()

        assertEquals(0, scheduler.activeCount)
        assertTrue(backend.tasks.all { it.cancelled })
    }

    @Test
    fun `requireMainThread throws when backend reports async`() {
        val backend = FakeBackend(mainThread = false)
        val scheduler = NexusScheduler(backend)
        assertFailsWith<IllegalStateException> { scheduler.requireMainThread() }
    }

    @Test
    fun `requireAsyncThread throws when backend reports main`() {
        val backend = FakeBackend(mainThread = true)
        val scheduler = NexusScheduler(backend)
        assertFailsWith<IllegalStateException> { scheduler.requireAsyncThread() }
    }

    @Test
    fun `task exceptions are swallowed so the scheduler keeps running`() {
        val backend = FakeBackend()
        val scheduler = NexusScheduler(backend)
        scheduler.runOnMain { error("boom") }

        // Should not throw — the wrapping Runnable absorbs it.
        backend.fire(backend.tasks.single().id)
    }

    @Test
    fun `closing a handle twice is a no-op`() {
        val backend = FakeBackend()
        val scheduler = NexusScheduler(backend)
        val handle = scheduler.runOnMain {}

        handle.close()
        handle.close()

        assertEquals(0, scheduler.activeCount)
    }
}
