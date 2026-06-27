package works.mees.jiib.command

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.net.JsonRpcMethods

@OptIn(ExperimentalCoroutinesApi::class)
class CommandDispatcherGatingTest {

    /** Minimal controllable RPC: each call parks on a CompletableDeferred we resolve by index. */
    private class FakeRpc {
        val calls = mutableListOf<kotlinx.coroutines.CompletableDeferred<JsonElement>>()
        suspend fun request(method: String, params: JsonElement?, timeoutMs: Long): JsonElement {
            val d = kotlinx.coroutines.CompletableDeferred<JsonElement>()
            calls += d
            return d.await()
        }
        fun complete(i: Int) { calls[i].complete(kotlinx.serialization.json.JsonNull) }
    }

    @Test
    fun softBusy_reTap_isQueuedNotDropped_andKeyStaysOutOfInFlight() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val dispatcher = CommandDispatcher(
            request = rpc::request, scope = this, debounceMs = 0L,
            timeSource = { testScheduler.currentTime },
        )

        dispatcher.dispatch("jog_X", JsonRpcMethods.GCODE_SCRIPT, null, GatingMode.SoftBusy)
        runCurrent()
        dispatcher.dispatch("jog_X", JsonRpcMethods.GCODE_SCRIPT, null, GatingMode.SoftBusy)
        runCurrent()

        assertEquals("both SoftBusy taps dispatched (queued, not dropped)", 2, rpc.calls.size)
        assertFalse("SoftBusy key must NOT disable its control", "jog_X" in dispatcher.inFlight.value)
        assertEquals("two active SoftBusy commands tracked", 2, dispatcher.activeGating.value.size)
    }

    @Test
    fun hardLock_reTap_isDropped_andKeyIsInFlight() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val dispatcher = CommandDispatcher(
            request = rpc::request, scope = this, debounceMs = 0L,
            timeSource = { testScheduler.currentTime },
        )
        dispatcher.dispatch("home_all", JsonRpcMethods.GCODE_SCRIPT, null, GatingMode.HardLock)
        runCurrent()
        dispatcher.dispatch("home_all", JsonRpcMethods.GCODE_SCRIPT, null, GatingMode.HardLock)
        runCurrent()

        assertEquals("HardLock re-tap dropped while in flight", 1, rpc.calls.size)
        assertTrue("HardLock key disables its control", "home_all" in dispatcher.inFlight.value)
        assertEquals(GatingMode.HardLock, dispatcher.activeGating.value.single().gating)
    }

    @Test
    fun softBusy_secondTapAfterDebounceWindow_queues_butWithinWindowIsDropped() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val dispatcher = CommandDispatcher(
            request = rpc::request, scope = this, debounceMs = 400L,
            timeSource = { testScheduler.currentTime },
        )
        dispatcher.dispatch("jog_X", JsonRpcMethods.GCODE_SCRIPT, null, GatingMode.SoftBusy)
        runCurrent()
        // Within the 400ms window → debounce drops it (accidental double-fire guard stays).
        advanceTimeBy(100L); runCurrent()
        dispatcher.dispatch("jog_X", JsonRpcMethods.GCODE_SCRIPT, null, GatingMode.SoftBusy)
        runCurrent()
        assertEquals("within-debounce re-tap dropped", 1, rpc.calls.size)
        // Past the window, still in flight → queues (NOT dropped by the in-flight guard).
        advanceTimeBy(401L); runCurrent()
        dispatcher.dispatch("jog_X", JsonRpcMethods.GCODE_SCRIPT, null, GatingMode.SoftBusy)
        runCurrent()
        assertEquals("post-debounce re-tap queued while still in flight", 2, rpc.calls.size)
    }

    @Test
    fun perCommandTimeout_overridesDefault() = runTest(UnconfinedTestDispatcher()) {
        val rpc = FakeRpc()
        val events = mutableListOf<DispatchEvent>()
        val dispatcher = CommandDispatcher(
            request = rpc::request, scope = this, debounceMs = 0L,
            timeSource = { testScheduler.currentTime },
        )
        val job = launch { dispatcher.events.collect { events += it } }

        // 600s override on a never-completing call: at 200s (past the 120s gcode default) it is STILL running.
        dispatcher.dispatch("bed_mesh_calibrate", JsonRpcMethods.GCODE_SCRIPT, null, GatingMode.HardLock, timeoutMs = 600_000L)
        runCurrent()
        advanceTimeBy(200_000L); runCurrent()
        assertTrue("still running at 200s under a 600s override", "bed_mesh_calibrate" in dispatcher.inFlight.value)

        advanceTimeBy(401_000L); runCurrent()
        assertFalse("times out after the 600s override", "bed_mesh_calibrate" in dispatcher.inFlight.value)
        job.cancel()
    }
}
