package works.mees.dinghy.state

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.net.ConnectionError
import works.mees.dinghy.net.MoonrakerJson

/**
 * STATE-03 + control-plane-immediate (review MEDIUM). A high-rate temp/position burst is conflated to
 * ~2-4 Hz (latest-wins; intermediate samples dropped), while a klippy-shutdown / stale transition AND
 * every gcode line are delivered WITHOUT sampling delay.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConflationTest {

    private fun statusDiff(json: String): JsonObject =
        MoonrakerJson.parseToJsonElement(json).jsonObject

    @Test
    fun highRateTempBurst_isConflated_latestWins() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
        store.seed(reduceSnapshot(statusDiff("""{"heater_bed":{"temperature":20.0,"target":0.0}}""")))

        val seen = mutableListOf<Double>()
        val job = launch(dispatcher) {
            store.printerState.collect { seen += it.heaters["heater_bed"]?.temperature ?: -1.0 }
        }

        // Burst many high-rate temperature updates within a single sample window.
        for (t in 21..40) {
            store.onStatusDiff(statusDiff("""{"heater_bed":{"temperature":$t.0}}"""))
        }
        runCurrent()
        advanceTimeBy(300L)
        runCurrent()

        // The burst must NOT have produced 20 distinct emissions; the latest (40.0) must be present.
        assertTrue("temp burst should be conflated (far fewer emissions than updates)", seen.size < 20)
        assertEquals(40.0, store.printerState.value.heaters["heater_bed"]?.temperature)
        job.cancel()
    }

    @Test
    fun controlPlaneTransition_isImmediate_notSampled() = runTest {
        val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
        store.seed(PrinterState())

        // A klippy shutdown is a control-plane transition — must propagate immediately, no sample wait.
        store.onKlippyMethod("notify_klippy_shutdown")
        runCurrent() // NO advanceTimeBy — immediacy is the assertion.
        assertEquals(KlippyState.Shutdown, store.printerState.value.klippyState)

        // A print_stats.state transition is also control-plane and immediate.
        store.onStatusDiff(statusDiff("""{"print_stats":{"state":"printing"}}"""))
        runCurrent()
        assertEquals(PrintState.Printing, store.printerState.value.printState)
    }

    /**
     * WR-04 regression guard: a single notify_status_update carrying a control-plane field
     * (print_stats.state) TOGETHER WITH high-rate fields — exactly how Moonraker batches a
     * print-finish frame — must publish immediately with the state transition visible, NOT delayed
     * behind the 250 ms sampler. touchesControlPlane inspects the whole diff, so the mixed frame is
     * immediate; this test locks that in so a refactor splitting the planes can't silently delay a
     * print-complete behind a sample tick.
     */
    @Test
    fun controlPlaneMixedWithHighRate_isImmediate() = runTest {
        val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
        store.seed(PrinterState())
        store.onStatusDiff(statusDiff(
            """{"print_stats":{"state":"complete"},"heater_bed":{"temperature":58.3},"virtual_sdcard":{"progress":0.99}}"""))
        runCurrent() // NO advanceTimeBy — immediacy is the assertion.
        assertEquals(PrintState.Complete, store.printerState.value.printState)
        // And the high-rate values that rode along in the same diff are visible immediately too.
        assertEquals(58.3, store.printerState.value.heaters["heater_bed"]?.temperature)
    }

    @Test
    fun connectionAndStaleMarkers_areImmediate() = runTest {
        val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
        store.seed(PrinterState())

        store.setConnectionState(ConnectionState.Connected)
        runCurrent()
        assertEquals(ConnectionState.Connected, store.printerState.value.connection)

        // Drop: stale marker flips immediately and last-known values are RETAINED (D-03).
        store.onStatusDiff(statusDiff("""{"heater_bed":{"temperature":55.0}}"""))
        runCurrent()
        advanceTimeBy(300L); runCurrent()
        store.markStale(ConnectionState.Disconnected)
        runCurrent()
        assertTrue("stale marker must be set immediately on drop", store.printerState.value.stale)
        assertEquals(
            "last-known temp must be retained (not blanked) on drop",
            55.0,
            store.printerState.value.heaters["heater_bed"]?.temperature,
        )
        assertEquals(ConnectionState.Disconnected, store.printerState.value.connection)
    }

    @Test
    fun gcodeLines_areNeverConflated() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)

        val lines = mutableListOf<String>()
        val job = launch(dispatcher) { store.gcodeResponses.collect { lines += it } }

        // Many gcode lines in a tight burst — every one must be delivered (Console needs them all).
        for (i in 1..10) store.onGcodeLine("line-$i")
        runCurrent()

        assertEquals(10, lines.size)
        assertEquals("line-1", lines.first())
        assertEquals("line-10", lines.last())
        job.cancel()
    }

    @Test
    fun seedOverwritesRetainedState_clearingStale() = runTest {
        val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
        store.seed(reduceSnapshot(statusDiff("""{"heater_bed":{"temperature":55.0,"target":60.0}}""")))
        store.markStale(ConnectionState.Disconnected)
        runCurrent()
        assertTrue(store.printerState.value.stale)

        // Post-reconnect query snapshot overwrites and clears stale (D-04).
        store.seed(reduceSnapshot(statusDiff("""{"heater_bed":{"temperature":23.0,"target":0.0}}""")))
        runCurrent()
        assertEquals(23.0, store.printerState.value.heaters["heater_bed"]?.temperature)
        assertTrue("seed must clear the stale marker (D-04)", !store.printerState.value.stale)
    }
}
