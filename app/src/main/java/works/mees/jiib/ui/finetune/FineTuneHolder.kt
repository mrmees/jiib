package works.mees.jiib.ui.finetune

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import works.mees.jiib.state.Capabilities
import works.mees.jiib.state.PrinterState
import works.mees.jiib.state.PrinterStateStore

/**
 * Identifies WHICH tuner a [FineTuneHolder.pendingStateFlip] is waiting on, so the holder can tell when
 * the reduced printer-object value has flipped to the dispatched target (D-15 / REVIEW #2). Each maps to
 * exactly one display value on [FineTuneVm].
 */
enum class FineTuneTuner {
    SPEED, MAX_VELOCITY, MAX_ACCEL, MIN_CRUISE, SCV,
    FLOW, PRESSURE_ADVANCE, SMOOTH_TIME, PART_FAN,
    RETRACT_LENGTH, UNRETRACT_EXTRA_LENGTH, RETRACT_SPEED, UNRETRACT_SPEED,
}

/**
 * One outstanding dispatch the whole-group busy lock waits on (D-15 / REVIEW #2): the [tuner] that was
 * nudged and the [target] value (in the SAME display unit / scale the vm exposes for that tuner) the
 * printer object must reach before the group is considered idle again.
 *
 * [seq] is a holder-private MONOTONIC arm id (17-07): it makes every arm a DISTINCT value even when
 * `tuner` + `target` are structurally equal (so a `MutableStateFlow`, which conflates equal values,
 * ALWAYS emits a new pending flip), and gives the seq-guarded timeout backstop a stable token to match
 * on. ⚠ [seq] does NOT participate in flip-DETECTION: [FineTuneHolder.reached] / `currentFor` key on
 * `tuner` + `target` ONLY (they read those fields, never compare whole instances), so including `seq` in
 * the data class never changes value-flip detection. Do NOT introduce a `==` of whole instances anywhere.
 */
data class PendingStateFlip(val tuner: FineTuneTuner, val target: Double, val seq: Long = 0L)

/**
 * Toolkit-agnostic holder for the Fine-Tune live-adjust panel (TUNE-02..06). It transforms the store's
 * ALREADY-throttled [PrinterStateStore.printerState] — COMBINED with the capability flow and the ten
 * one-shot config-baseline StateFlows (17-03) — into a host-testable [FineTuneVm].
 *
 * ## Display scaling lives here (RESEARCH Pitfall 1)
 * The reducer stores RAW ratios/units; [buildVm] is the ONLY place ratio→% (speed/flow) and 0..1→%
 * (part-fan, minimum-cruise display) conversions happen. Nothing reads a raw ratio into a tile.
 *
 * ## Capabilities folded as a FLOW (REVIEW #10)
 * [PrinterStateStore.capabilities] is a [StateFlow] re-derived on every reconnect, so it is folded into
 * the `combine` (NOT snapshotted once) — a reconnect that changes the gating re-emits the vm.
 *
 * ## State-flip whole-group busy lock (D-15 / REVIEW #2)
 * The screen marks a [pendingStateFlip] (tuner + target) on each ±dispatch via [markPending]. The
 * holder derives `groupBusy = inFlight.isNotEmpty() || pendingStateFlip != null` and CLEARS the pending
 * flip the instant the reduced vm value for that tuner reaches the target (state flip observed) — NOT on
 * the bare RPC ack. A dispatch failure/timeout clears it via [clearPending] (the screen calls it off the
 * dispatcher Failure event). The screen feeds the dispatcher's `inFlight` set in via [setInFlight].
 *
 * Plain Kotlin (no Compose annotations) so it is host-unit-testable.
 *
 * @param scope the lifecycle scope the combine collect runs on (the UI host supplies it).
 * @param store the already-assembled Phase-2/17 spine; the holder CONSUMES it, never opens a session.
 */
class FineTuneHolder(
    private val scope: CoroutineScope,
    private val store: PrinterStateStore,
) {
    /** The dispatcher's live in-flight key set, fed in by the screen (empty until wired). */
    private val _inFlight = MutableStateFlow<Set<String>>(emptySet())

    /** Monotonic arm id — pre-incremented on each [markPending] arm so equal-content flips are distinct. */
    private var flipSeq: Long = 0L

    /** The bounded self-clear timer for the CURRENT armed flip (cancelled/replaced on each arm). */
    private var timeoutJob: Job? = null

    /**
     * The last printer state the combine collector actually observed (17 code-review fix, WR-04). The
     * `reached`/clear logic runs inside the combine over the THROTTLED `store.printerState`, which can lag
     * the raw `StateFlow.value`. [markPending]'s skip-arm must compare against THIS same throttled
     * snapshot — not the raw value — so a `+` tap can't read an already-moved raw state, compute
     * `current ≈ target`, and drop the arm while a dispatch the combine hasn't seen yet is still on the
     * wire. Written from the combine collector (main-confined, same single thread markPending runs on).
     */
    private var lastObservedState: PrinterState? = null

    /** The outstanding state-flip the group busy lock waits on (null = none). */
    private val _pendingStateFlip = MutableStateFlow<PendingStateFlip?>(null)
    /** Public read for the screen / tests: the tuner+target the group is still waiting to flip. */
    val pendingStateFlip: PendingStateFlip? get() = _pendingStateFlip.value

    private val _vm = MutableStateFlow(FineTuneVm())
    /** The resolved Fine-Tune view-model (display-scaled values, gates, baselines, state-flip busy). */
    val vm: StateFlow<FineTuneVm> = _vm.asStateFlow()

    init {
        // Wrap the ten baseline StateFlows into ONE FineTuneBaselines flow (combine arity cap, PATTERNS):
        // list-form combine over the homogeneous Double? sources.
        val baselinesFlow = combine(
            store.baselineMaxVelocity,
            store.baselineMaxAccel,
            store.baselineMinCruise,
            store.baselineScv,
            store.baselinePressureAdvance,
            store.baselineSmoothTime,
            store.baselineRetractLength,
            store.baselineRetractSpeed,
            store.baselineUnretractExtraLength,
            store.baselineUnretractSpeed,
        ) { values ->
            FineTuneBaselines(
                maxVelocity = values[0],
                maxAccel = values[1],
                minCruise = values[2],
                scv = values[3],
                pressureAdvance = values[4],
                smoothTime = values[5],
                retractLength = values[6],
                retractSpeed = values[7],
                unretractExtraLength = values[8],
                unretractSpeed = values[9],
            )
        }

        scope.launch {
            combine(
                store.printerState,
                store.capabilities, // REVIEW #10 — fold caps as a FLOW so a reconnect re-emits the vm.
                baselinesFlow,
                _inFlight,
                _pendingStateFlip,
            ) { state, caps, baselines, inFlight, pending ->
                // WR-04: record the throttled state the combine actually sees so markPending's skip-arm
                // compares against the SAME snapshot reached() does (not the racing raw StateFlow.value).
                lastObservedState = state
                // Clear the pending flip the instant the printer-object value reaches the target (D-15).
                val stillPending = pending?.takeUnless { reached(state, it) }
                if (stillPending !== pending) {
                    // Real flip observed before the timeout — cancel the now-moot backstop timer.
                    timeoutJob?.cancel()
                    _pendingStateFlip.value = stillPending
                }
                buildVm(state, caps, baselines, inFlight, stillPending)
            }.collect { _vm.value = it }
        }
    }

    /** The screen feeds the dispatcher's live in-flight key set in (drives the busy lock's first arm). */
    fun setInFlight(keys: Set<String>) { _inFlight.value = keys }

    /**
     * Mark an outstanding state-flip (tuner + display-unit target) on each ±dispatch (D-15).
     *
     * 17-07 (UAT Check 6 fix): [target] MUST be the CLAMPED value (the screens feed it the same
     * [works.mees.jiib.command.PrinterCommands] clamp the wire uses) so target and wire never disagree.
     *
     * SKIP-ARM: if the clamped [target] is already within the tuner's own float epsilon of the value the
     * printer currently reports, this is a true no-op (e.g. a '+' tap at the cap) — do NOT arm a flip, or
     * the busy lock would wedge forever on a value the no-op wire command can never move. Because the
     * epsilon is step×0.1 (an order of magnitude below the step), a legitimate one-step in-range nudge
     * still arms (Check 5 preserved for every tuner).
     *
     * TIMEOUT BACKSTOP: on a real arm, schedule a seq-guarded bounded self-clear so no future unreachable
     * flip can wedge the group permanently.
     */
    fun markPending(tuner: FineTuneTuner, target: Double) {
        // 17 code-review fix (WR-01/WR-02): arm the value the WIRE will actually report, not the raw
        // double. The wire builder formats each field to a fixed display precision (SCV→1dp, PA→3dp,
        // smooth→2dp, etc.); an OFF-GRID live value (a config baseline reached via Reset, e.g. SCV 4.05)
        // would otherwise arm a target the rounded wire value can never land within the tight step×0.1
        // epsilon of — wedging the group for the full PENDING_FLIP_TIMEOUT_MS backstop. Rounding the
        // armed target to the SAME precision Klipper will echo back makes reported == target exactly, so
        // an on-grid Reset reliably flips. On-grid inputs round to themselves (no behaviour change).
        val rounded = roundToWirePrecision(tuner, target)
        // WR-04: read the throttled state the combine last observed (falling back to the raw value before
        // the first emission) so the skip-arm and reached() agree — a pending flip is never dropped while
        // an in-flight dispatch the combine hasn't re-emitted for is still on the wire.
        val current = currentFor(lastObservedState ?: store.printerState.value, tuner)
        if (current != null && abs(current - rounded) < toleranceFor(tuner)) {
            // True no-op (clamped at-cap target ≈ already-reported value): do not arm a flip.
            timeoutJob?.cancel()
            _pendingStateFlip.value = null
            return
        }
        timeoutJob?.cancel()
        val armed = PendingStateFlip(tuner, rounded, seq = ++flipSeq)
        _pendingStateFlip.value = armed
        timeoutJob = scope.launch {
            delay(PENDING_FLIP_TIMEOUT_MS)
            // Clear ONLY if the live pending flip is still the EXACT one this timer armed (seq token
            // match) — a stale timer from an earlier (lower-seq) arm must never clear a newer flip.
            if (_pendingStateFlip.value?.seq == armed.seq) _pendingStateFlip.value = null
        }
    }

    /** Clear the pending flip on a dispatch failure/timeout (the screen calls this off Failure). */
    fun clearPending() {
        timeoutJob?.cancel()
        _pendingStateFlip.value = null
    }

    /** The value the printer currently reports for [tuner] in DISPLAY units (null = not yet reported). */
    private fun currentFor(state: PrinterState, tuner: FineTuneTuner): Double? = when (tuner) {
        FineTuneTuner.SPEED -> state.speedFactor * 100
        FineTuneTuner.MAX_VELOCITY -> state.maxVelocity
        FineTuneTuner.MAX_ACCEL -> state.maxAccel
        FineTuneTuner.MIN_CRUISE -> state.minimumCruiseRatio?.let { it * 100 }
        FineTuneTuner.SCV -> state.squareCornerVelocity
        FineTuneTuner.FLOW -> state.extrudeFactor * 100
        FineTuneTuner.PRESSURE_ADVANCE -> state.pressureAdvance
        FineTuneTuner.SMOOTH_TIME -> state.smoothTime
        FineTuneTuner.PART_FAN -> state.partFanSpeed?.let { it * 100 }
        FineTuneTuner.RETRACT_LENGTH -> state.firmwareRetraction?.retractLength
        FineTuneTuner.UNRETRACT_EXTRA_LENGTH -> state.firmwareRetraction?.unretractExtraLength
        FineTuneTuner.RETRACT_SPEED -> state.firmwareRetraction?.retractSpeed
        FineTuneTuner.UNRETRACT_SPEED -> state.firmwareRetraction?.unretractSpeed
    }

    /**
     * Per-tuner float EPSILON for "value flipped to target" (17-07 BLOCK-1, STRICT `<`). This is NOT half
     * a step — it is one-TENTH of that tuner's display-unit step, so it is (i) comfortably larger than
     * float round-trip jitter yet (ii) an order of magnitude SMALLER than one step. With a strict-`<`
     * comparison a one-step nudge is "reached" ONLY when the reported value actually EQUALS the commanded
     * (clamped) target — a midpoint reading (half a step away) is never inside epsilon, so the lock never
     * releases early (the flat 0.5 / a half-step `<=` would have broken every sub-0.5-step tuner).
     */
    private fun toleranceFor(tuner: FineTuneTuner): Double = when (tuner) {
        FineTuneTuner.SPEED -> SPEED_STEP.toDouble() * 0.1
        FineTuneTuner.MAX_VELOCITY -> VEL_STEP * 0.1
        FineTuneTuner.MAX_ACCEL -> ACCEL_STEP * 0.1
        FineTuneTuner.MIN_CRUISE -> MIN_CRUISE_STEP_PCT.toDouble() * 0.1
        FineTuneTuner.SCV -> SCV_STEP * 0.1
        FineTuneTuner.FLOW -> FLOW_STEP.toDouble() * 0.1
        FineTuneTuner.PRESSURE_ADVANCE -> PA_STEP * 0.1
        FineTuneTuner.SMOOTH_TIME -> SMOOTH_STEP * 0.1
        FineTuneTuner.PART_FAN -> FAN_STEP_PCT.toDouble() * 0.1
        FineTuneTuner.RETRACT_LENGTH -> RETRACT_LEN_STEP * 0.1
        FineTuneTuner.UNRETRACT_EXTRA_LENGTH -> RETRACT_LEN_STEP * 0.1
        FineTuneTuner.RETRACT_SPEED -> RETRACT_SPEED_STEP.toDouble() * 0.1
        FineTuneTuner.UNRETRACT_SPEED -> RETRACT_SPEED_STEP.toDouble() * 0.1
    }

    /**
     * The number of decimal places the WIRE builder formats this tuner's value to, expressed in the
     * holder's DISPLAY unit (17 code-review fix, WR-01/WR-02). [markPending] rounds the armed target to
     * this precision so the optimistic flip target equals the value Klipper will echo back — an off-grid
     * Reset can no longer arm a target the rounded wire value can never reach within [toleranceFor].
     *
     * Precisions trace [works.mees.jiib.command.PrinterCommands] `fmt(..., N)` calls:
     * SCV `fmt(...,1)`→1dp; PA `fmt(...,3)`→3dp; smooth `fmt(...,2)`→2dp; velocity/accel `fmt(...,0)`→0dp;
     * retraction lengths `fmt(...,1)`→1dp; speeds/percents are ints→0dp. MIN_CRUISE is a 2dp RATIO on the
     * wire, but the holder's display unit is the percent (ratio×100), so 2dp-ratio == 0dp-percent.
     */
    private fun wirePrecisionFor(tuner: FineTuneTuner): Int = when (tuner) {
        FineTuneTuner.SPEED -> 0
        FineTuneTuner.MAX_VELOCITY -> 0
        FineTuneTuner.MAX_ACCEL -> 0
        FineTuneTuner.MIN_CRUISE -> 0 // display percent; wire ratio is 2dp == integer percent.
        FineTuneTuner.SCV -> 1
        FineTuneTuner.FLOW -> 0
        FineTuneTuner.PRESSURE_ADVANCE -> 3
        FineTuneTuner.SMOOTH_TIME -> 2
        FineTuneTuner.PART_FAN -> 0
        FineTuneTuner.RETRACT_LENGTH -> 1
        FineTuneTuner.UNRETRACT_EXTRA_LENGTH -> 1
        FineTuneTuner.RETRACT_SPEED -> 0
        FineTuneTuner.UNRETRACT_SPEED -> 0
    }

    /** Round [value] to [wirePrecisionFor] decimal places (banker-free half-up), matching the wire grid. */
    private fun roundToWirePrecision(tuner: FineTuneTuner, value: Double): Double {
        val factor = TEN_POW[wirePrecisionFor(tuner)]
        return (value * factor).roundToInt() / factor
    }

    /** True once the reduced [state] value for the pending [flip].tuner reaches its target (STRICT-< eps). */
    private fun reached(state: PrinterState, flip: PendingStateFlip): Boolean {
        val current = currentFor(state, flip.tuner)
        // Not-yet-reported value can't be a flip; a per-tuner float epsilon absorbs wire round-trip error.
        return current != null && abs(current - flip.target) < toleranceFor(flip.tuner)
    }

    private fun buildVm(
        state: PrinterState,
        caps: Capabilities,
        baselines: FineTuneBaselines,
        inFlight: Set<String>,
        pending: PendingStateFlip?,
    ): FineTuneVm {
        // Scaling lives HERE (display boundary), never the reducer (Pitfall 1).
        // ratio → percent (D-03 / D-08): speed_factor / extrude_factor are always reported (default 1.0).
        val speedPct = (state.speedFactor * 100).roundToInt()
        val flowPct = (state.extrudeFactor * 100).roundToInt()
        // 0..1 → percent, nullable (null → "—", never a fabricated 0, D-20).
        val partFanPct = state.partFanSpeed?.let { (it * 100).roundToInt() }
        // ratio → percent for DISPLAY (the +tap converts back to a ratio on the wire, REVIEW #9).
        val minCruisePct = state.minimumCruiseRatio?.let { (it * 100).roundToInt() }

        return FineTuneVm(
            speedPct = speedPct,
            maxVelocity = state.maxVelocity,
            maxAccel = state.maxAccel,
            minCruisePct = minCruisePct,
            scv = state.squareCornerVelocity,
            flowPct = flowPct,
            pressureAdvance = state.pressureAdvance,
            smoothTime = state.smoothTime,
            partFanPct = partFanPct,
            retractLength = state.firmwareRetraction?.retractLength,
            unretractExtraLength = state.firmwareRetraction?.unretractExtraLength,
            retractSpeed = state.firmwareRetraction?.retractSpeed,
            unretractSpeed = state.firmwareRetraction?.unretractSpeed,
            hasGcodeMove = caps.hasObject("gcode_move"),
            hasToolhead = caps.hasObject("toolhead"),
            hasExtruder = caps.hasObject("extruder"),
            hasFan = caps.hasObject("fan"),
            hasFwRetraction = caps.hasObject("firmware_retraction"),
            baselines = baselines,
            // D-15: stay busy while a key is in-flight OR a dispatched value has not flipped yet.
            groupBusy = inFlight.isNotEmpty() || pending != null,
        )
    }

    companion object {
        /**
         * Bounded self-clear backstop for an UNREACHABLE armed flip (17-07). Generous — a real mid-print
         * flip lands in well under a second; this only catches the pathological never-reached case so the
         * whole-group busy lock can never wedge forever. `internal` so the test can advance past it.
         */
        internal const val PENDING_FLIP_TIMEOUT_MS = 8_000L

        /** 10^n for n in 0..3 — the wire precisions [wirePrecisionFor] yields (avoids `Math.pow` churn). */
        private val TEN_POW: DoubleArray = doubleArrayOf(1.0, 10.0, 100.0, 1000.0)
    }
}
