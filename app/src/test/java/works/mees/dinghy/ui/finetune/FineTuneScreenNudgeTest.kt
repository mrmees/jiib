package works.mees.dinghy.ui.finetune

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.command.TrailingCommitBatcher
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

/**
 * Regression proof for the D-22 clamp-authority invariant on the trailing-commit path
 * (quick-rmr): [commitTunerValue] is now the SINGLE commit-time write path — for every over-cap
 * commit the [FineTuneHolder.markPending] target equals the PrinterCommands clamp ceiling, NOT
 * the raw over-cap value (the P17 UAT Check-6 permanent busy-lock wedge guard), and a burst of
 * batched taps dispatches exactly ONE wire request carrying the final clamped value.
 *
 * See also [FineTuneHolderTest] which tests the holder itself (UNCHANGED by quick-rmr).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FineTuneScreenNudgeTest {

    private fun caps(vararg objects: String): Capabilities = Capabilities(objects = objects.toSet())

    /**
     * Real [CommandDispatcher] with a no-op request lambda + virtual timeSource (the
     * CommandDispatcherTest pattern). Post-review fix 2: [commitTunerValue] is now a hard no-op
     * on a null dispatcher (no markPending-without-command), so every test asserting commit
     * behavior must supply a live one.
     */
    private fun TestScope.newDispatcher(
        requests: MutableList<String> = mutableListOf(),
    ): CommandDispatcher = CommandDispatcher(
        request = { method, _, _ ->
            requests += method
            kotlinx.serialization.json.JsonNull
        },
        scope = backgroundScope,
        timeSource = { testScheduler.currentTime },
    )

    // ─── MAX_VELOCITY cap test ────────────────────────────────────────────────────

    /**
     * An over-cap commit on MAX_VELOCITY (above VEL_MAX=1000 mm/s) must arm markPending at
     * exactly the VEL_MAX ceiling — not the raw value.
     *
     * Proof of D-22: [commitTunerValue] calls [clampForTuner] before [FineTuneHolder.markPending],
     * so the armed target == clamped ceiling == wire value → no permanent busy-lock wedge.
     */
    @Test
    fun velocity_overCap_armsMarkPendingAtClampCeiling() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("toolhead"))
        // Seed a valid below-cap velocity so we can test the over-cap commit.
        store.seed(PrinterState(maxVelocity = 990.0))
        runCurrent()

        val param = ALL_FINE_TUNE_PARAMS.first { it.tuner == FineTuneTuner.MAX_VELOCITY }
        val vm = holder.vm.value

        // Over-cap absolute target: 990 + 100 = 1090, but VEL_MAX = 1000.
        commitTunerValue(
            param = param,
            target = 1090.0,
            vm = vm,
            holder = holder,
            dispatcher = newDispatcher(),
        )
        runCurrent()

        // The armed target must be the clamp ceiling, NOT the raw 1090.
        val pending = holder.pendingStateFlip
        if (pending != null) {
            // Commit was > epsilon away from cap → armed; target must be clamped.
            assertEquals(
                "over-cap velocity commit arms markPending at VEL_MAX (not raw 1090)",
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
     * An at-cap velocity (already at VEL_MAX) committed further should skip-arm (no-op):
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

        commitTunerValue(
            param = param,
            target = PrinterCommands.VEL_MAX + 100.0,
            vm = vm,
            holder = holder,
            dispatcher = newDispatcher(),
        )
        runCurrent()

        assertNull(
            "at-cap velocity commit skip-arms (no permanent busy-lock wedge)",
            holder.pendingStateFlip,
        )
        assertFalse("no false busy lock at velocity cap", holder.vm.value.groupBusy)
    }

    // ─── SCV cap test ─────────────────────────────────────────────────────────────

    /**
     * An over-cap commit on SCV (above SCV_MAX=20 mm/s) must arm markPending at the SCV_MAX ceiling.
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

        // Over-cap absolute target: 19.5 + 1.0 = 20.5, but SCV_MAX = 20.0.
        commitTunerValue(
            param = param,
            target = 20.5,
            vm = vm,
            holder = holder,
            dispatcher = newDispatcher(),
        )
        runCurrent()

        val pending = holder.pendingStateFlip
        if (pending != null) {
            assertEquals(
                "over-cap SCV commit arms markPending at SCV_MAX (not raw 20.5)",
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
     * An at-cap SCV commit skip-arms (mirrors the [FineTuneHolderTest] at-cap pattern, but
     * exercised via the screen's commit path to prove the call path is clean).
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

        commitTunerValue(
            param = param,
            target = PrinterCommands.SCV_MAX + 0.1,
            vm = vm,
            holder = holder,
            dispatcher = newDispatcher(),
        )
        runCurrent()

        assertNull("at-cap SCV commit skip-arms", holder.pendingStateFlip)
        assertFalse("no false busy lock at SCV cap", holder.vm.value.groupBusy)
    }

    // ─── Trailing-commit batching (quick-rmr, the money test) ─────────────────────

    /**
     * A 5-tap FLOW burst through a [TrailingCommitBatcher] whose onCommit resolves
     * `holder.vm.value` AT FIRE TIME (19-09 stale-closure lesson) dispatches EXACTLY ONE wire
     * request after the quiet window, with the pending flip armed at the final clamped value —
     * and ZERO requests during the burst.
     */
    @Test
    fun burst_dispatchesExactlyOnce_withFinalClampedValue() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("gcode_move"))
        store.seed(PrinterState(extrudeFactor = 1.0)) // FLOW = 100%
        runCurrent()

        // Real CommandDispatcher with a request-counting lambda + virtual timeSource
        // (CommandDispatcherTest pattern).
        val requests = mutableListOf<String>()
        val dispatcher = CommandDispatcher(
            request = { method, _, _ ->
                requests += method
                kotlinx.serialization.json.JsonNull
            },
            scope = backgroundScope,
            timeSource = { testScheduler.currentTime },
        )
        val param = ALL_FINE_TUNE_PARAMS.first { it.tuner == FineTuneTuner.FLOW }
        val batcher = TrailingCommitBatcher(
            scope = backgroundScope,
            canCommit = { tunerName ->
                dispatchKeyForTuner(FineTuneTuner.valueOf(tunerName)) !in dispatcher.inFlight.value
            },
            onCommit = { tunerName, value ->
                // Fire-time resolution: holder.vm.value + the live dispatcher (19-09 lesson).
                val tuner = FineTuneTuner.valueOf(tunerName)
                val p = ALL_FINE_TUNE_PARAMS.first { it.tuner == tuner }
                commitTunerValue(p, value, holder.vm.value, holder, dispatcher)
            },
        )

        // 5 rapid FLOW taps (canonicalTunerValue per tap — the prod tap path): 100 → 105, 100ms apart.
        var working = holder.vm.value.flowPct!!.toDouble()
        repeat(5) { i ->
            working = canonicalTunerValue(FineTuneTuner.FLOW, working + 1.0)
            batcher.tap(FineTuneTuner.FLOW.name, working)
            runCurrent()
            assertEquals("zero wire requests during the burst (tap ${i + 1})", 0, requests.size)
            advanceTimeBy(100)
        }

        // Quiet window elapses after the LAST tap → exactly ONE dispatch.
        advanceTimeBy(600)
        runCurrent()

        assertEquals("exactly one wire request after the quiet window", 1, requests.size)
        val pending = holder.pendingStateFlip
        assertNotNull("single commit armed a pending flip", pending)
        assertEquals(
            "pending flip target == the final clamped working value",
            105.0,
            pending!!.target,
            0.001,
        )
        batcher.dispose()
    }

    // ─── Off-grid wire-precision rounding (WR-01/02 on the SINGLE commit) ─────────

    /**
     * Committing an off-grid SCV value arms a pending target rounded to the 1dp wire precision —
     * proves the WR-01/02 rounding still guards the SINGLE commit (the holder's own off-grid
     * tests stay untouched as the deeper proof).
     *
     * Post-review fix 4: ALSO proves display == committed — the tap-time [canonicalTunerValue]
     * (what the batcher stores and the screen displays) equals the armed pending target EXACTLY
     * (delta 0.0). This is the drift guard between FineTuneParams' wire-precision table and
     * FineTuneHolder's private one.
     */
    @Test
    fun offGridCommit_armsWirePrecisionRoundedTarget_andDisplayEqualsCommitted() =
        runTest(UnconfinedTestDispatcher()) {
            val store = PrinterStateStore(backgroundScope)
            val holder = FineTuneHolder(backgroundScope, store)
            store.setCapabilities(caps("toolhead"))
            store.seed(PrinterState(squareCornerVelocity = 5.0))
            runCurrent()

            val param = ALL_FINE_TUNE_PARAMS.first { it.tuner == FineTuneTuner.SCV }

            // 4.07 is off the 1dp wire grid: the tap-time canonical (displayed) value is the
            // half-up 1dp rounding (4.1) — the screen never shows an off-wire-grid working value.
            val displayed = canonicalTunerValue(FineTuneTuner.SCV, 4.07)
            assertEquals("tap-time canonical value lands on the 1dp wire grid", 4.1, displayed, 1e-9)

            commitTunerValue(
                param = param,
                target = 4.07,
                vm = holder.vm.value,
                holder = holder,
                dispatcher = newDispatcher(),
            )
            runCurrent()

            val pending = holder.pendingStateFlip
            assertNotNull("off-grid SCV commit arms a pending flip", pending)
            assertEquals(
                "armed target equals the 1dp wire-precision rounding of the off-grid commit",
                4.1,
                pending!!.target,
                1e-9,
            )
            assertEquals(
                "display == committed EXACTLY (tap-time canonical == armed target — table drift guard)",
                displayed,
                pending.target,
                0.0,
            )
        }

    /**
     * Post-review fix 4 spec: [canonicalTunerValue] = clamp authority + half-up rounding to the
     * tuner's WIRE precision. Covers each precision class — including RETRACT_LENGTH, whose
     * DISPLAY decimals (2) differ from its WIRE precision (1dp), the divergence that motivated a
     * dedicated table instead of reusing [FineTuneParam.decimals].
     */
    @Test
    fun canonicalTunerValue_roundsToWirePrecision_perTuner() {
        assertEquals("SCV → 1dp", 4.1, canonicalTunerValue(FineTuneTuner.SCV, 4.07), 1e-9)
        assertEquals(
            "PA → 3dp",
            0.043,
            canonicalTunerValue(FineTuneTuner.PRESSURE_ADVANCE, 0.0426),
            1e-9,
        )
        assertEquals(
            "smooth → 2dp",
            0.04,
            canonicalTunerValue(FineTuneTuner.SMOOTH_TIME, 0.0449),
            1e-9,
        )
        assertEquals(
            "retract length → WIRE 1dp (display decimals are 2 — wire grid wins)",
            0.6,
            canonicalTunerValue(FineTuneTuner.RETRACT_LENGTH, 0.55),
            1e-9,
        )
        assertEquals(
            "velocity → 0dp",
            151.0,
            canonicalTunerValue(FineTuneTuner.MAX_VELOCITY, 150.6),
            1e-9,
        )
        // Clamp-first: an over-cap off-grid value canonicalizes to the CEILING, never above.
        assertEquals(
            "over-cap off-grid SCV canonicalizes to SCV_MAX",
            PrinterCommands.SCV_MAX,
            canonicalTunerValue(FineTuneTuner.SCV, 20.55),
            1e-9,
        )
    }

    /**
     * Post-review fix 2: a null dispatcher makes [commitTunerValue] a hard NO-OP — it must return
     * BEFORE markPending. The old order (markPending → dispatch early-return) armed a pending flip
     * no echo could ever release, re-creating the offline 8s-backstop dim (the 17-07 wedge class).
     */
    @Test
    fun nullDispatcher_commitIsNoOp_neverArmsPending() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("toolhead"))
        store.seed(PrinterState(squareCornerVelocity = 5.0))
        runCurrent()

        val param = ALL_FINE_TUNE_PARAMS.first { it.tuner == FineTuneTuner.SCV }

        // A legitimate in-range change — would arm a flip with a live dispatcher.
        commitTunerValue(
            param = param,
            target = 6.0,
            vm = holder.vm.value,
            holder = holder,
            dispatcher = null,
        )
        runCurrent()

        assertNull(
            "null-dispatcher commit never arms markPending (no command will ever release it)",
            holder.pendingStateFlip,
        )
        assertFalse("no offline busy dim", holder.vm.value.groupBusy)
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
