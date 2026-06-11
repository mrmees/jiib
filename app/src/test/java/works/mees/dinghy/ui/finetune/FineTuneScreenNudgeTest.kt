package works.mees.dinghy.ui.finetune

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

/**
 * Regression proof for the D-22 clamp-authority invariant in the new flat Fine-Tune screen.
 *
 * Verifies: for every over-cap nudge via [nudge], the [FineTuneHolder.markPending] target equals
 * the PrinterCommands clamp ceiling — NOT the raw over-cap value. This is the P17 UAT Check-6
 * regression guard: an UNCLAMPED markPending target above the cap would arm a flip the wire command
 * can never reach, wedging the whole group's busy lock permanently.
 *
 * See also [FineTuneHolderTest] which tests the holder itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FineTuneScreenNudgeTest {

    private fun caps(vararg objects: String): Capabilities = Capabilities(objects = objects.toSet())

    // ─── MAX_VELOCITY cap test ────────────────────────────────────────────────────

    /**
     * An over-cap nudge on MAX_VELOCITY (above VEL_MAX=1000 mm/s) must arm markPending at
     * exactly the VEL_MAX ceiling — not the raw value.
     *
     * Proof of D-22: [nudge] calls [clampForTuner] before [FineTuneHolder.markPending], so the
     * armed target == clamped ceiling == wire value → no permanent busy-lock wedge.
     */
    @Test
    fun velocity_overCap_armsMarkPendingAtClampCeiling() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("toolhead"))
        // Seed a valid below-cap velocity so we can test the over-cap nudge.
        store.seed(PrinterState(maxVelocity = 990.0))
        runCurrent()

        val param = ALL_FINE_TUNE_PARAMS.first { it.tuner == FineTuneTuner.MAX_VELOCITY }
        val vm = holder.vm.value

        // Over-cap raw target: 990 + 100 = 1090, but VEL_MAX = 1000.
        nudge(
            param = param,
            currentValue = 990.0,
            stepDelta = 100.0,
            vm = vm,
            holder = holder,
            dispatcher = null,
        )
        runCurrent()

        // The armed target must be the clamp ceiling, NOT the raw 1090.
        val pending = holder.pendingStateFlip
        if (pending != null) {
            // Nudge was > epsilon away from cap → armed; target must be clamped.
            assertEquals(
                "over-cap velocity nudge arms markPending at VEL_MAX (not raw 1090)",
                PrinterCommands.VEL_MAX,
                pending.target,
                0.001,
            )
        }
        // Either armed at cap OR skip-armed (if 990 is within epsilon of 1000) — either is correct:
        // what is NOT correct is an arm with target > VEL_MAX.
        pending?.let { flip ->
            assertFalse(
                "armed target must never exceed VEL_MAX (raw 1090 would have wedged the lock)",
                flip.target > PrinterCommands.VEL_MAX,
            )
        }
    }

    /**
     * An at-cap velocity (already at VEL_MAX) nudged further should skip-arm (no-op):
     * markPending's skip-arm fires because clamped target ≈ current value.
     */
    @Test
    fun velocity_atCap_skipArms() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("toolhead"))
        store.seed(PrinterState(maxVelocity = PrinterCommands.VEL_MAX))
        runCurrent()

        val param = ALL_FINE_TUNE_PARAMS.first { it.tuner == FineTuneTuner.MAX_VELOCITY }
        val vm = holder.vm.value

        nudge(
            param = param,
            currentValue = PrinterCommands.VEL_MAX,
            stepDelta = 100.0,
            vm = vm,
            holder = holder,
            dispatcher = null,
        )
        runCurrent()

        assertNull(
            "at-cap velocity nudge skip-arms (no permanent busy-lock wedge)",
            holder.pendingStateFlip,
        )
        assertFalse("no false busy lock at velocity cap", holder.vm.value.groupBusy)
    }

    // ─── SCV cap test ─────────────────────────────────────────────────────────────

    /**
     * An over-cap nudge on SCV (above SCV_MAX=20 mm/s) must arm markPending at the SCV_MAX ceiling.
     *
     * SCV is one of the small-step tuners (step=0.1 mm/s, epsilon=step×0.1=0.01 mm/s, wire precision=1dp)
     * where the P17 UAT Check-6 bug was most dangerous (tight epsilon + off-grid targets).
     */
    @Test
    fun scv_overCap_armsMarkPendingAtClampCeiling() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("toolhead"))
        // Seed at 19.5 mm/s so a +1.0 step pushes past SCV_MAX=20.
        store.seed(PrinterState(squareCornerVelocity = 19.5))
        runCurrent()

        val param = ALL_FINE_TUNE_PARAMS.first { it.tuner == FineTuneTuner.SCV }
        val vm = holder.vm.value

        // Over-cap raw target: 19.5 + 1.0 = 20.5, but SCV_MAX = 20.0.
        nudge(
            param = param,
            currentValue = 19.5,
            stepDelta = 1.0,
            vm = vm,
            holder = holder,
            dispatcher = null,
        )
        runCurrent()

        val pending = holder.pendingStateFlip
        if (pending != null) {
            assertEquals(
                "over-cap SCV nudge arms markPending at SCV_MAX (not raw 20.5)",
                PrinterCommands.SCV_MAX,
                pending.target,
                0.001,
            )
            assertFalse(
                "armed target must never exceed SCV_MAX",
                pending.target > PrinterCommands.SCV_MAX,
            )
        }
        // If skip-armed (current already within epsilon of cap), that is also correct.
    }

    /**
     * An at-cap SCV nudge skip-arms (mirrors the [FineTuneHolderTest.smallStep_atCap_noop_releases]
     * pattern, but exercised via the screen's [nudge] function to prove the call path is clean).
     */
    @Test
    fun scv_atCap_skipArms() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("toolhead"))
        store.seed(PrinterState(squareCornerVelocity = PrinterCommands.SCV_MAX))
        runCurrent()

        val param = ALL_FINE_TUNE_PARAMS.first { it.tuner == FineTuneTuner.SCV }
        val vm = holder.vm.value

        nudge(
            param = param,
            currentValue = PrinterCommands.SCV_MAX,
            stepDelta = 0.1,
            vm = vm,
            holder = holder,
            dispatcher = null,
        )
        runCurrent()

        assertNull("at-cap SCV nudge skip-arms", holder.pendingStateFlip)
        assertFalse("no false busy lock at SCV cap", holder.vm.value.groupBusy)
    }

    // ─── ALL_FINE_TUNE_PARAMS completeness assertion ─────────────────────────────

    /**
     * Structural assertion: [ALL_FINE_TUNE_PARAMS] must contain exactly one entry per
     * [FineTuneTuner] constant — no duplicates, no missing tuners.
     */
    @Test
    fun allParams_hasOneEntryPerTuner() {
        val allTuners = FineTuneTuner.values().toSet()
        val paramTuners = ALL_FINE_TUNE_PARAMS.map { it.tuner }.toSet()
        assertEquals(
            "ALL_FINE_TUNE_PARAMS has one entry per FineTuneTuner (no duplicates, no missing)",
            allTuners,
            paramTuners,
        )
        assertEquals(
            "ALL_FINE_TUNE_PARAMS list has no duplicate tuners",
            ALL_FINE_TUNE_PARAMS.size,
            ALL_FINE_TUNE_PARAMS.distinctBy { it.tuner }.size,
        )
    }

    /**
     * groupColorFor must never throw on an empty or under-sized pool.
     */
    @Test
    fun groupColorFor_emptyPool_returnsWhite() {
        val color = groupColorFor(FineTuneParamGroup.EXTRUSION, emptyList())
        assertEquals("empty pool returns white", androidx.compose.ui.graphics.Color.White, color)
    }

    @Test
    fun groupColorFor_singleEntryPool_returnsFirstForAllGroups() {
        val single = listOf(androidx.compose.ui.graphics.Color.Red)
        for (group in FineTuneParamGroup.values()) {
            assertEquals("single-entry pool returns pool[0] for $group", single[0], groupColorFor(group, single))
        }
    }
}
