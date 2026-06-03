package works.mees.dinghy.calibration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

/**
 * Host-side proof for [TiltHolder]: the LOAD-scoped transform that exposes whether the routine ran this
 * load ([TiltVm.ran]), the per-stepper adjustments parsed off the gcode stream, the homed gate, and a
 * dispatcher Failure. It deliberately does NOT read the persisted `z_tilt.applied` flag (on-device UAT:
 * that flag persists across the whole session, so it cannot say "ran this load"). Running vs Done is the
 * screen's job (it owns the dispatcher in-flight set); the holder just supplies these facts.
 *
 * Mirrors [ScrewsTiltHolderTest] / [ProbeCalibrateHolderTest]: a real [PrinterStateStore] seeded
 * synchronously under `runTest` with an [UnconfinedTestDispatcher]; gcode frames pushed via onGcodeLine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TiltHolderTest {

    @Test
    fun freshLoad_isNotRan_regardlessOfPersistedApplied() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = TiltHolder(backgroundScope, store)

        // Even with the persisted applied flag TRUE, a freshly-loaded page has NOT run this load.
        store.seed(PrinterState(homedAxes = "xyz", zTiltApplied = true))
        runCurrent()

        assertFalse("a fresh load never reports ran (no persisted-applied 'Done')", holder.vm.value.ran)
        assertTrue(holder.vm.value.adjustments.isEmpty())
    }

    @Test
    fun markDispatchedSetsRan_resetClearsIt() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = TiltHolder(backgroundScope, store)
        store.seed(PrinterState(homedAxes = "xyz"))
        runCurrent()

        holder.markDispatched()
        runCurrent()
        assertTrue(holder.vm.value.ran)

        // Re-entering the page resets to the Idle landing state (no run persists across loads).
        holder.reset()
        runCurrent()
        assertFalse(holder.vm.value.ran)
        assertTrue(holder.vm.value.adjustments.isEmpty())
    }

    @Test
    fun gcodeBlockSurfacesParsedAdjustments_onlyAfterRun() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = TiltHolder(backgroundScope, store)
        store.seed(PrinterState(homedAxes = "xyz"))
        runCurrent()

        // Before a run, stray adjustment lines on the console are ignored.
        store.onGcodeLine("// Making the following Z adjustments:")
        store.onGcodeLine("// stepper_z = 0.052788")
        runCurrent()
        assertTrue("adjustments ignored until the page runs", holder.vm.value.adjustments.isEmpty())

        // Run, then the real Z_TILT_ADJUST block lands on the un-throttled gcode stream.
        holder.markDispatched()
        store.onGcodeLine("// Making the following Z adjustments:")
        store.onGcodeLine("// stepper_z = 0.052788")
        store.onGcodeLine("// stepper_z1 = 0.035756")
        runCurrent()

        assertEquals(
            listOf(ZAdjustment("stepper_z", 0.052788), ZAdjustment("stepper_z1", 0.035756)),
            holder.vm.value.adjustments,
        )
    }

    @Test
    fun adjustmentsArriveAsOneNewlineDelimitedMessage() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = TiltHolder(backgroundScope, store)
        store.seed(PrinterState(homedAxes = "xyz"))
        holder.markDispatched()
        runCurrent()

        // The REAL E5 shape: the entire block is a SINGLE gcode_response with embedded newlines.
        store.onGcodeLine("// Making the following Z adjustments:\n// stepper_z = 0.036042\n// stepper_z1 = 0.036042")
        runCurrent()

        assertEquals(
            listOf(ZAdjustment("stepper_z", 0.036042), ZAdjustment("stepper_z1", 0.036042)),
            holder.vm.value.adjustments,
        )
    }

    @Test
    fun freshHeaderResetsToLastIterationAdjustments() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = TiltHolder(backgroundScope, store)
        store.seed(PrinterState(homedAxes = "xyz"))
        holder.markDispatched()
        runCurrent()

        // Iteration 1 then iteration 2 — the latest block replaces the prior (a non-stepper line ends one).
        store.onGcodeLine("// Making the following Z adjustments:")
        store.onGcodeLine("// stepper_z = 0.10")
        store.onGcodeLine("// Probe samples...")
        store.onGcodeLine("// Making the following Z adjustments:")
        store.onGcodeLine("// stepper_z = 0.001")
        store.onGcodeLine("// stepper_z1 = 0.002")
        runCurrent()

        assertEquals(
            listOf(ZAdjustment("stepper_z", 0.001), ZAdjustment("stepper_z1", 0.002)),
            holder.vm.value.adjustments,
        )
    }

    @Test
    fun dispatcherFailureForThisRoutineSurfaces() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val events = MutableSharedFlow<DispatchEvent>(extraBufferCapacity = 8)
        val holder = TiltHolder(backgroundScope, store, events = events, dispatchKey = "z_tilt_adjust")
        store.seed(PrinterState(homedAxes = "xyz"))
        holder.markDispatched()
        runCurrent()

        events.emit(DispatchEvent.Failure(key = "z_tilt_adjust", message = "Too many retries"))
        runCurrent()
        assertTrue(holder.vm.value.failed)
        assertEquals("Too many retries", holder.vm.value.errorText)
    }

    @Test
    fun unrelatedFailureKeyIsIgnored() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val events = MutableSharedFlow<DispatchEvent>(extraBufferCapacity = 8)
        val holder = TiltHolder(backgroundScope, store, events = events, dispatchKey = "z_tilt_adjust")
        store.seed(PrinterState(homedAxes = "xyz"))
        holder.markDispatched()
        runCurrent()

        events.emit(DispatchEvent.Failure(key = "screws_tilt", message = "bed level exceeds limits"))
        runCurrent()
        assertFalse(holder.vm.value.failed)
        assertNull(holder.vm.value.errorText)
    }

    @Test
    fun homedGateReflectsHomedAxes() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = TiltHolder(backgroundScope, store)

        store.seed(PrinterState(homedAxes = "xy"))
        runCurrent()
        assertFalse(holder.vm.value.homedGate)

        store.seed(PrinterState(homedAxes = "xyz"))
        runCurrent()
        assertTrue(holder.vm.value.homedGate)
    }
}
