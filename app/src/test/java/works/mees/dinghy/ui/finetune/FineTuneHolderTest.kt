package works.mees.dinghy.ui.finetune

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.command.PrinterCommands
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

    // --- 17-07 GAP-1 regression: the busy-lock wedge at the clamp ceiling ---------------------------

    @Test
    fun smallStep_inRange_nudge_arms_and_holds_until_value_equals_target() =
        runTest(UnconfinedTestDispatcher()) {
            val store = PrinterStateStore(backgroundScope)
            val holder = FineTuneHolder(backgroundScope, store)
            store.setCapabilities(caps("extruder"))
            store.seed(PrinterState(pressureAdvance = 0.040))
            runCurrent()

            // A LEGITIMATE +0.001 in-range nudge on a small-step tuner (PA step 0.001 ≫ epsilon 0.0001).
            // The CLAMPED target 0.041 is well above the per-tuner epsilon from the reported 0.040 → ARMS.
            holder.markPending(FineTuneTuner.PRESSURE_ADVANCE, PrinterCommands.clampPressureAdvance(0.041))
            runCurrent()
            assertNotNull("small-step in-range nudge ARMS the flip (flat 0.5 would wrongly skip)", holder.pendingStateFlip)
            assertTrue("groupBusy true while the real flip is pending", holder.vm.value.groupBusy)

            // A MIDPOINT reading (half a step toward target) must NOT release — strict-< / epsilon regression.
            store.seed(PrinterState(pressureAdvance = 0.0405))
            runCurrent()
            assertNotNull("a midpoint reading does NOT release the lock", holder.pendingStateFlip)
            assertTrue("STILL busy at the midpoint reading", holder.vm.value.groupBusy)

            // The reported value finally EQUALS the target: NOW the flip clears.
            store.seed(PrinterState(pressureAdvance = 0.041))
            runCurrent()
            assertNull("released ONLY when the value reaches the target", holder.pendingStateFlip)
            assertFalse("groupBusy clears on the real flip", holder.vm.value.groupBusy)
        }

    @Test
    fun groupBusy_releases_when_target_at_cap_already_reported() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("gcode_move"))
        store.seed(PrinterState(extrudeFactor = 1.5)) // flowPct 150 = the FLOW_PCT_MAX cap
        runCurrent()

        // At-cap '+' tap: the wire clamps 151 → 150, so the CLAMPED markPending target == reported 150.
        holder.markPending(FineTuneTuner.FLOW, PrinterCommands.clampFlowPct(151).toDouble())
        runCurrent()
        assertNull("skip-arm: at-cap no-op does NOT arm a flip (|150-150| < FLOW epsilon 0.1)", holder.pendingStateFlip)
        assertFalse("no false busy lock at the cap", holder.vm.value.groupBusy)
    }

    @Test
    fun smallStep_atCap_noop_releases() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("extruder"))
        store.seed(PrinterState(pressureAdvance = 1.0)) // PA at the PA_MAX cap
        runCurrent()

        // At-cap '+' on a SMALL-STEP tuner: wire clamps 1.001 → 1.0, target == reported → skip-arm.
        holder.markPending(FineTuneTuner.PRESSURE_ADVANCE, PrinterCommands.clampPressureAdvance(1.001))
        runCurrent()
        assertNull("small-step at-cap no-op skips arming (per-tuner epsilon still releases the no-op)", holder.pendingStateFlip)
        assertFalse("no false busy lock for the small-step cap no-op", holder.vm.value.groupBusy)
    }

    @Test
    fun groupBusy_releases_via_timeout_on_unreachable_target() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("gcode_move"))
        store.seed(PrinterState(extrudeFactor = 1.0)) // flowPct 100
        runCurrent()

        // An unreachable target (never reported) arms — then the seq-guarded backstop self-clears it.
        holder.markPending(FineTuneTuner.FLOW, 9999.0)
        runCurrent()
        assertNotNull("unreachable target arms a flip", holder.pendingStateFlip)
        assertTrue("busy while the unreachable flip is armed", holder.vm.value.groupBusy)

        advanceTimeBy(FineTuneHolder.PENDING_FLIP_TIMEOUT_MS + 100)
        runCurrent()
        assertNull("the bounded timeout self-clears the unreachable flip", holder.pendingStateFlip)
        assertFalse("groupBusy can never wedge forever (backstop)", holder.vm.value.groupBusy)
    }

    @Test
    fun rapidDoubleTap_staleTimer_doesNotClearNewerFlip() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("gcode_move"))
        store.seed(PrinterState(extrudeFactor = 1.0))
        runCurrent()

        // First unreachable arm; advance PARTWAY so its timer is pending-but-not-fired.
        holder.markPending(FineTuneTuner.FLOW, 9999.0)
        runCurrent()
        val firstSeq = holder.pendingStateFlip!!.seq
        advanceTimeBy(FineTuneHolder.PENDING_FLIP_TIMEOUT_MS / 2)
        runCurrent()

        // Second STRUCTURALLY-EQUAL arm (same tuner+target, fresh seq) → distinct StateFlow value.
        holder.markPending(FineTuneTuner.FLOW, 9999.0)
        runCurrent()
        val secondSeq = holder.pendingStateFlip!!.seq
        assertTrue("re-arm gets a NEWER monotonic seq despite equal contents", secondSeq > firstSeq)

        // Advance so the FIRST timer's full delay elapses (from ITS own schedule) — its seq is now stale.
        advanceTimeBy(FineTuneHolder.PENDING_FLIP_TIMEOUT_MS / 2 + 100)
        runCurrent()
        assertNotNull("stale (older-seq) timer must NOT cross-clear the newer flip", holder.pendingStateFlip)
        assertEquals("the live flip is still the second arm", secondSeq, holder.pendingStateFlip!!.seq)
        assertTrue("still busy — second flip outstanding", holder.vm.value.groupBusy)

        // Advance past the SECOND timer's full delay → it clears its OWN flip.
        advanceTimeBy(FineTuneHolder.PENDING_FLIP_TIMEOUT_MS)
        runCurrent()
        assertNull("the second flip clears on its own timer", holder.pendingStateFlip)
        assertFalse("group released once the newer flip times out", holder.vm.value.groupBusy)
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
