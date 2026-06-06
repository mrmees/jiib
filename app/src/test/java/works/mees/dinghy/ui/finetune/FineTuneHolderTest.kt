package works.mees.dinghy.ui.finetune

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test

/**
 * Host-side proof for the (NOT-YET-BUILT) `FineTuneHolder` / `FineTuneVm` (land in 17-05). Mirrors
 * [works.mees.dinghy.ui.extrude.ExtrudeHolderTest]: the holder COMBINEs the throttled `printerState`
 * with the one-shot config-baseline StateFlows and derives a host-testable `FineTuneVm` — display
 * scaling (ratio→%, 0..1→%), capability gates, baseline folding, the D-15 whole-group state-flip
 * busy-lock, and nullable-baseline reset.
 *
 * [[dinghy-wave0-red-scaffold-compile]]: `FineTuneHolder`/`FineTuneVm` do NOT exist yet — Gradle compiles
 * the WHOLE test sourceset before the `--tests` filter, so these stubs MUST reference only symbols that
 * exist today. Each is a `fail("RED — 17-05: ...")` carrying the exact field/value/gate target. 17-05
 * converts each to a real `runTest(UnconfinedTestDispatcher())` + real `PrinterStateStore` + assertion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FineTuneHolderTest {

    @Test
    fun vm_scales_ratio_to_percent() = runTest(UnconfinedTestDispatcher()) {
        // Target (17-05): speedFactor 1.05 -> vm.speedPct == 105; extrudeFactor 1.0 -> vm.flowPct == 100.
        //   Scaling lives in the HOLDER (display boundary), never the reducer.
        fail("RED — 17-05: speedFactor 1.05 -> speedPct 105; extrudeFactor 1.0 -> flowPct 100")
    }

    @Test
    fun vm_scales_fan_0to1_to_percent() = runTest(UnconfinedTestDispatcher()) {
        // Target (17-05): partFanSpeed 0.6 (raw 0..1) -> vm.partFanPct == 60.
        fail("RED — 17-05: partFanSpeed 0.6 -> partFanPct 60 (0..1 -> %)")
    }

    @Test
    fun vm_scales_minCruiseRatio_to_percent() = runTest(UnconfinedTestDispatcher()) {
        // REVIEW #9: minimumCruiseRatio 0.5 -> vm shows 50% (display %); the +tap path produces the 0.55
        //   WIRE target proven in PrinterCommandsTest.setVelocityLimit_minCruiseRatio_percentDisplayRatioWire.
        fail("RED — 17-05: minimumCruiseRatio 0.5 -> vm shows 50% (display percent / wire ratio)")
    }

    @Test
    fun vm_gates_on_capabilities() = runTest(UnconfinedTestDispatcher()) {
        // Target (17-05): vm.hasFan == caps.hasObject("fan"); vm.hasFwRetraction == caps.hasObject("firmware_retraction").
        //   Absent object -> gate false (the FW-retraction screen is build-blind / off on both dev printers).
        fail("RED — 17-05: hasFan=hasObject(\"fan\"); hasFwRetraction=hasObject(\"firmware_retraction\")")
    }

    @Test
    fun vm_folds_baselines() = runTest(UnconfinedTestDispatcher()) {
        // Target (17-05): the config-baseline one-shot StateFlows (max_velocity/accel/min_cruise/scv/PA/
        //   smooth_time/retraction) fold into the vm deterministically via combine (NOT dependent on a later
        //   status diff) — exactly like ExtrudeHolder folds minExtrudeTemp/maxExtrudeDistance.
        fail("RED — 17-05: config-baseline StateFlows fold into vm deterministically (combine, not snapshot)")
    }

    @Test
    fun groupBusy_persists_until_state_flip() = runTest(UnconfinedTestDispatcher()) {
        // REVIEW #2 / D-15: vm.groupBusy == (inFlight.isNotEmpty() || pendingStateFlip != null). After the RPC
        //   ack CLEARS inFlight the group STAYS busy because pendingStateFlip is still set to the dispatched
        //   target; it CLEARS only once the reduced vm value reaches that target (or on dispatch failure/timeout).
        //   This proves busy persists past the bare ack until the printer-object value actually flips
        //   ("until the app sees the ready/confirmed state again").
        fail("RED — 17-05: groupBusy = inFlight.isNotEmpty() || pendingStateFlip != null; persists past bare ack until value flips to target (D-15)")
    }

    @Test
    fun reset_isNoOp_whenBaselineNull() = runTest(UnconfinedTestDispatcher()) {
        // REVIEW #3: when a tuner's baseline (or its current value) is null, the reset action dispatches
        //   NOTHING — no bare/invalid command is ever emitted from a missing baseline.
        fail("RED — 17-05: reset dispatches NOTHING when baseline/current is null (no bare/invalid command)")
    }

    @Test
    fun nullValue_shows_dash_not_zero() = runTest(UnconfinedTestDispatcher()) {
        // Target (17-05): an unreported field surfaces as null/"—" on the vm — NEVER a fabricated 0.
        fail("RED — 17-05: unreported field -> null/\"—\", never fabricated 0")
    }
}
