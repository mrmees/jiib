package works.mees.dinghy.calibration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.ManualProbeObject
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

/**
 * Host-side proof for [ProbeCalibrateHolder]: the toolkit-agnostic transform that drives the
 * interactive manual-probe Z-calibrate page (CALIB-05 / D-01) off `manual_probe.is_active`.
 *
 *  - is_active = false (never opened) → [ProbePageState.Idle].
 *  - is_active = true → [ProbePageState.Active] with the live `z_position` hero.
 *  - a real `// Z position: a --> b <-- c` frame emitted on the un-throttled
 *    [PrinterStateStore.gcodeResponses] SharedFlow surfaces the PARSED bracket in the vm — the
 *    bracket is NOT a printerState field (it arrives only on the raw gcode stream, Phase-8 D-04).
 *  - after Accept (is_active flips back to false with a captured offset) → [ProbePageState.Accepted].
 *  - the Start command resolves via [probeCalibrateGate] (A3): probe-less → Z_ENDSTOP_CALIBRATE.
 *
 * Mirrors [TiltHolderTest] / [works.mees.dinghy.ui.extrude.ExtrudeHolderTest]: a real
 * [PrinterStateStore] seeded synchronously under `runTest` with an [UnconfinedTestDispatcher].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProbeCalibrateHolderTest {

    @Test
    fun inactiveSessionIsIdle() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ProbeCalibrateHolder(backgroundScope, store)

        store.seed(PrinterState(manualProbe = ManualProbeObject(isActive = false)))
        runCurrent()

        assertEquals(ProbePageState.Idle, holder.vm.value.state)
    }

    @Test
    fun activeSessionExposesLiveZPosition() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ProbeCalibrateHolder(backgroundScope, store)

        store.seed(
            PrinterState(
                manualProbe = ManualProbeObject(isActive = true, zPosition = 7.624),
            ),
        )
        runCurrent()

        val vm = holder.vm.value
        assertEquals(ProbePageState.Active, vm.state)
        assertEquals(7.624, vm.zPosition!!, 0.0001)
    }

    @Test
    fun gcodeFrameSurfacesParsedBracketWhileActive() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ProbeCalibrateHolder(backgroundScope, store)

        store.seed(PrinterState(manualProbe = ManualProbeObject(isActive = true, zPosition = 0.25)))
        runCurrent()

        // A real manual-probe console frame lands on the un-throttled gcode stream.
        store.onGcodeLine("// Z position: 0.30 --> 0.25 <-- 0.20")
        runCurrent()

        val bracket = holder.vm.value.bracket
        assertEquals(0.30, bracket!!.lower!!, 0.0001)
        assertEquals(0.25, bracket.current, 0.0001)
        assertEquals(0.20, bracket.upper!!, 0.0001)
    }

    @Test
    fun unknownBoundsBracketDoesNotCrash() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ProbeCalibrateHolder(backgroundScope, store)

        store.seed(PrinterState(manualProbe = ManualProbeObject(isActive = true, zPosition = 7.624)))
        runCurrent()

        // The real captured shape: bounds are the literal ?????? (unknown). Must tolerate → null bounds.
        store.onGcodeLine("// Z position: ?????? --> 7.624 <-- ??????")
        runCurrent()

        val bracket = holder.vm.value.bracket
        assertEquals(7.624, bracket!!.current, 0.0001)
        assertNull("?????? lower → null bound, no crash", bracket.lower)
        assertNull("?????? upper → null bound, no crash", bracket.upper)
    }

    @Test
    fun bracketClearsWhenSessionEnds() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ProbeCalibrateHolder(backgroundScope, store)

        store.seed(PrinterState(manualProbe = ManualProbeObject(isActive = true, zPosition = 0.25)))
        runCurrent()
        store.onGcodeLine("// Z position: 0.30 --> 0.25 <-- 0.20")
        runCurrent()
        assertTrue(holder.vm.value.bracket != null)

        // Session ends — the stale bracket must clear so it doesn't linger on the Idle/Accepted page.
        store.seed(PrinterState(manualProbe = ManualProbeObject(isActive = false)))
        runCurrent()
        assertNull("bracket cleared when is_active flips false", holder.vm.value.bracket)
    }

    @Test
    fun acceptCapturesOffsetThenAccepted() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ProbeCalibrateHolder(backgroundScope, store)

        store.seed(PrinterState(manualProbe = ManualProbeObject(isActive = true, zPosition = 0.25)))
        runCurrent()
        assertEquals(ProbePageState.Active, holder.vm.value.state)

        // The user accepts: Klipper flips is_active false. The page captured the last live Z as the offset.
        store.seed(PrinterState(manualProbe = ManualProbeObject(isActive = false)))
        runCurrent()

        val vm = holder.vm.value
        assertEquals(ProbePageState.Accepted, vm.state)
        assertEquals("captured offset = the last live probed Z", 0.25, vm.capturedOffset!!, 0.0001)
    }

    @Test
    fun startCommandResolvesViaProbePresentGate() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ProbeCalibrateHolder(backgroundScope, store)

        // Probe present → PROBE_CALIBRATE.
        store.setCapabilities(Capabilities(objects = setOf("probe", "manual_probe")))
        store.seed(PrinterState(manualProbe = ManualProbeObject(isActive = false)))
        runCurrent()
        assertEquals("PROBE_CALIBRATE", holder.vm.value.startCommand)

        // Probe-LESS → Z_ENDSTOP_CALIBRATE (A3).
        store.setCapabilities(Capabilities(objects = setOf("manual_probe")))
        runCurrent()
        assertEquals("Z_ENDSTOP_CALIBRATE", holder.vm.value.startCommand)
    }

    @Test
    fun dispatcherFailureSurfacesRedactedError() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val events = MutableSharedFlow<DispatchEvent>(extraBufferCapacity = 8)
        val holder = ProbeCalibrateHolder(backgroundScope, store, events = events)

        store.seed(PrinterState(manualProbe = ManualProbeObject(isActive = true, zPosition = 0.25)))
        runCurrent()

        events.emit(DispatchEvent.Failure(key = "testz", message = "Move out of range"))
        runCurrent()

        assertEquals("Move out of range", holder.vm.value.errorText)
    }
}
