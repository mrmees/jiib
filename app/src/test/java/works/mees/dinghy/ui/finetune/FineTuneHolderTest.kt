package works.mees.dinghy.ui.finetune

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.FirmwareRetractionObject
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

/**
 * Host-side proof for [FineTuneHolder] / [FineTuneVm] (17-05). Mirrors
 * [works.mees.dinghy.ui.extrude.ExtrudeHolderTest]: the holder COMBINEs the throttled `printerState`
 * with the one-shot config-baseline StateFlows + the capability flow and derives a host-testable
 * [FineTuneVm] — display scaling (ratio→%, 0..1→%), capability gates, baseline folding, the D-15
 * whole-group state-flip busy-lock, and the nullable-baseline reset no-op.
 *
 * Scaling lives in the HOLDER (display boundary), never the reducer (RESEARCH Pitfall 1).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FineTuneHolderTest {

    private fun caps(vararg objects: String): Capabilities = Capabilities(objects = objects.toSet())

    @Test
    fun vm_scales_ratio_to_percent() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("gcode_move"))
        store.seed(PrinterState(speedFactor = 1.05, extrudeFactor = 1.0))
        runCurrent()

        val vm = holder.vm.value
        assertEquals("speedFactor 1.05 -> speedPct 105 (ratio->%)", 105, vm.speedPct)
        assertEquals("extrudeFactor 1.0 -> flowPct 100 (ratio->%)", 100, vm.flowPct)
    }

    @Test
    fun vm_scales_fan_0to1_to_percent() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("fan"))
        store.seed(PrinterState(partFanSpeed = 0.6))
        runCurrent()

        assertEquals("partFanSpeed 0.6 -> partFanPct 60 (0..1 -> %)", 60, holder.vm.value.partFanPct)
    }

    @Test
    fun vm_scales_minCruiseRatio_to_percent() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("toolhead"))
        store.seed(PrinterState(minimumCruiseRatio = 0.5))
        runCurrent()

        // REVIEW #9: minimumCruiseRatio 0.5 -> vm shows 50% (display percent; the +tap sends 0.55 on the wire).
        assertEquals("minimumCruiseRatio 0.5 -> 50% display", 50, holder.vm.value.minCruisePct)
    }

    @Test
    fun vm_gates_on_capabilities() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("fan")) // fan present, firmware_retraction absent.
        store.seed(PrinterState())
        runCurrent()

        val vm = holder.vm.value
        assertTrue("hasFan == hasObject(\"fan\")", vm.hasFan)
        assertFalse("hasFwRetraction false when object absent (build-blind on dev printers)", vm.hasFwRetraction)
    }

    @Test
    fun vm_folds_baselines() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("toolhead", "extruder"))
        store.seed(PrinterState())
        // The one-shot handshake baseline reads land on their StateFlows; the holder COMBINES them
        // deterministically (NOT contingent on a later status diff) — like ExtrudeHolder folds minExtrudeTemp.
        store.setBaselineMaxVelocity(300.0)
        store.setBaselineMaxAccel(3000.0)
        store.setBaselineMinCruise(0.5)
        store.setBaselinePressureAdvance(0.04)
        runCurrent()

        val b = holder.vm.value.baselines
        assertEquals(300.0, b.maxVelocity!!, 0.001)
        assertEquals(3000.0, b.maxAccel!!, 0.001)
        assertEquals(0.5, b.minCruise!!, 0.001)
        assertEquals(0.04, b.pressureAdvance!!, 0.001)
    }

    @Test
    fun groupBusy_persists_until_state_flip() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("gcode_move"))
        store.seed(PrinterState(speedFactor = 1.0)) // speedPct 100
        runCurrent()
        assertFalse("idle to start", holder.vm.value.groupBusy)

        // ±tap: mark the dispatched target (speed 105%) AND arm the dispatcher in-flight key.
        holder.markPending(FineTuneTuner.SPEED, target = 105.0)
        holder.setInFlight(setOf("set_speed_factor"))
        runCurrent()
        assertTrue("busy while in-flight", holder.vm.value.groupBusy)

        // The RPC ack CLEARS inFlight — but the printer-object value has NOT flipped yet (still 100%).
        holder.setInFlight(emptySet())
        runCurrent()
        assertTrue("STAYS busy past the bare ack — pendingStateFlip still set (D-15)", holder.vm.value.groupBusy)

        // The reduced state finally reaches the target (speed_factor 1.05 -> 105%): the flip clears busy.
        store.seed(PrinterState(speedFactor = 1.05))
        runCurrent()
        assertFalse("clears once the value flips to target", holder.vm.value.groupBusy)
        assertNull("pendingStateFlip cleared on the flip", holder.pendingStateFlip)
    }

    @Test
    fun reset_isNoOp_whenBaselineNull() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("firmware_retraction"))
        store.seed(PrinterState(firmwareRetraction = FirmwareRetractionObject(retractLength = 0.8)))
        // No baseline set → the retraction reset baselines stay null (build-blind on dev printers).
        runCurrent()

        val b = holder.vm.value.baselines
        // REVIEW #3: a null baseline → the screen wires onReset = null (no command emitted). The holder's
        // contract is that the baseline IS null so the screen can make the reset a no-op.
        assertNull("retractLength baseline null -> reset is a no-op", b.retractLength)
        assertNull("retractSpeed baseline null -> reset is a no-op", b.retractSpeed)
    }

    @Test
    fun nullValue_shows_dash_not_zero() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("toolhead", "extruder"))
        // maxAccel / pressureAdvance never reported -> null on the vm, NEVER a fabricated 0 (D-20).
        store.seed(PrinterState())
        runCurrent()

        val vm = holder.vm.value
        assertNull("unreported maxAccel -> null/\"—\"", vm.maxAccel)
        assertNull("unreported pressureAdvance -> null/\"—\"", vm.pressureAdvance)
    }
}
