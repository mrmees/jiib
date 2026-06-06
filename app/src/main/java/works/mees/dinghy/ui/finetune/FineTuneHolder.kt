package works.mees.dinghy.ui.finetune

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

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
 */
data class PendingStateFlip(val tuner: FineTuneTuner, val target: Double)

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
    scope: CoroutineScope,
    private val store: PrinterStateStore,
) {
    /** The dispatcher's live in-flight key set, fed in by the screen (empty until wired). */
    private val _inFlight = MutableStateFlow<Set<String>>(emptySet())

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
                // Clear the pending flip the instant the printer-object value reaches the target (D-15).
                val stillPending = pending?.takeUnless { reached(state, it) }
                if (stillPending !== pending) _pendingStateFlip.value = stillPending
                buildVm(state, caps, baselines, inFlight, stillPending)
            }.collect { _vm.value = it }
        }
    }

    /** The screen feeds the dispatcher's live in-flight key set in (drives the busy lock's first arm). */
    fun setInFlight(keys: Set<String>) { _inFlight.value = keys }

    /** Mark an outstanding state-flip (tuner + display-unit target) on each ±dispatch (D-15). */
    fun markPending(tuner: FineTuneTuner, target: Double) {
        _pendingStateFlip.value = PendingStateFlip(tuner, target)
    }

    /** Clear the pending flip on a dispatch failure/timeout (the screen calls this off Failure). */
    fun clearPending() { _pendingStateFlip.value = null }

    /** True once the reduced [state] value for the pending [flip].tuner reaches its target. */
    private fun reached(state: PrinterState, flip: PendingStateFlip): Boolean {
        val current: Double? = when (flip.tuner) {
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
        // Not-yet-reported value can't be a flip; tolerance covers float/percent rounding on the wire.
        return current != null && abs(current - flip.target) <= FLIP_TOLERANCE
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

    private companion object {
        /** Display-unit tolerance for "value flipped to target" — covers wire rounding (e.g. %, 1dp). */
        const val FLIP_TOLERANCE = 0.5
    }
}
