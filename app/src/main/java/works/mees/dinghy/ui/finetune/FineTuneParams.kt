package works.mees.dinghy.ui.finetune

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.FanArgs
import works.mees.dinghy.command.FlowFactorArgs
import works.mees.dinghy.command.PressureAdvanceArgs
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.command.RetractionArgs
import works.mees.dinghy.command.SpeedFactorArgs
import works.mees.dinghy.command.VelocityLimitArgs
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.command.CommandDispatcher

/**
 * Display group for the flat Fine-Tune param list (D-02). Each group maps to a distinct hue from
 * [works.mees.dinghy.theme.ThemeTokens.pool] — no text labels; the pool color on the leading icon
 * is the sole group identity signal.
 *
 * ## Pool index assignments (D-02)
 * - EXTRUSION → pool index 0 (first pool hue — typically blue/violet in Colorful)
 * - MOTION    → pool index 1 (second pool hue — typically teal/green in Colorful)
 * - FW_RETRACTION → pool index 2 (third pool hue — typically amber/orange in Colorful)
 *
 * Three clearly distinct indices across the pool; take()-guarded against pools < 3.
 */
enum class FineTuneParamGroup {
    EXTRUSION,
    MOTION,
    FW_RETRACTION,
}

/** The pool indices chosen for each group (D-02). Never use bare `pool[i]` — always take-guarded. */
private val GROUP_POOL_INDEX = mapOf(
    FineTuneParamGroup.EXTRUSION    to 0,
    FineTuneParamGroup.MOTION       to 1,
    FineTuneParamGroup.FW_RETRACTION to 2,
)

/**
 * Resolve the pool-color hue for [group] from the theme's [pool].
 *
 * Guarded against pools smaller than the chosen index: if the pool has fewer colours than expected,
 * falls back gracefully:
 *  - pool.size == 0 → [Color.White]
 *  - pool.size == 1 → pool[0] for all groups
 *  - pool.size == 2 → pool[0]/pool[1] for EXTRUSION/MOTION; pool[0] for FW_RETRACTION
 *  - pool.size >= 3 → the three distinct indices above
 *
 * Takes the list via `pool.take(n+1)` before indexing (never a bare `pool[n]`).
 */
fun groupColorFor(group: FineTuneParamGroup, pool: List<Color>): Color {
    if (pool.isEmpty()) return Color.White
    val idx = GROUP_POOL_INDEX[group] ?: 0
    return pool.take(idx + 1).lastOrNull() ?: pool[0]
}

/**
 * Describes a single Fine-Tune adjustable parameter — everything the flat-list row and AdjusterPanel
 * Focus need to render and dispatch a nudge.
 *
 * @param tuner              the [FineTuneTuner] enum constant this descriptor binds to.
 * @param name               human-readable display name.
 * @param icon               [DinghyIcon] registry token (D-24: never auto-picked).
 * @param steps              ordered increment step set (ImmutableList for Compose stability).
 * @param defaultStepIndex   index into [steps] for the session-entry default; 0 = smallest step.
 * @param unit               unit suffix appended to the displayed value (e.g. "%", "mm/s", " s").
 * @param decimals           decimal places for [fmtValue] and shouldShowBaseline rounding.
 * @param group              which display group this param belongs to (controls the pool-color tint).
 * @param requiresFwRetraction   if true, this row is HIDDEN (not greyed) when [FineTuneVm.hasFwRetraction]
 *                               is false (D-08 hide-not-grey capability gating).
 */
data class FineTuneParam(
    val tuner: FineTuneTuner,
    val name: String,
    val icon: DinghyIcon,
    val steps: ImmutableList<Double>,
    val defaultStepIndex: Int = 0,
    val unit: String,
    val decimals: Int,
    val group: FineTuneParamGroup,
    val requiresFwRetraction: Boolean = false,
)

/** The default active step for [param] (index-guarded; falls back to [steps][0] if index out of range). */
fun defaultStepFor(param: FineTuneParam): Double =
    param.steps.getOrElse(param.defaultStepIndex) { param.steps.first() }

/**
 * The canonical flat-list param order (D-02 flattened display order):
 *  Extrusion → Motion → FW-retraction
 *
 * PART_FAN is in the Extrusion group (D-03: it stays a Fine-Tune param AND remains in Outputs).
 *
 * 13 entries — one per [FineTuneTuner] enum constant:
 *  SPEED, FLOW, PRESSURE_ADVANCE, SMOOTH_TIME, PART_FAN  (Extrusion)
 *  MAX_VELOCITY, MAX_ACCEL, MIN_CRUISE, SCV              (Motion)
 *  RETRACT_LENGTH, RETRACT_SPEED, UNRETRACT_EXTRA_LENGTH, UNRETRACT_SPEED (FW-retraction)
 */
val ALL_FINE_TUNE_PARAMS: List<FineTuneParam> = listOf(
    // ─── Extrusion group (pool index 0) ────────────────────────────────────────
    FineTuneParam(
        tuner = FineTuneTuner.SPEED,
        name = "Print Speed",
        icon = DinghyIcons.Speed,
        steps = persistentListOf(1.0, 5.0, 10.0),
        defaultStepIndex = 1,   // 5% default step
        unit = "%",
        decimals = 0,
        group = FineTuneParamGroup.EXTRUSION,
    ),
    FineTuneParam(
        tuner = FineTuneTuner.FLOW,
        name = "Flow Rate",
        icon = DinghyIcons.OutputCircle,
        steps = persistentListOf(1.0, 5.0, 10.0),
        defaultStepIndex = 0,   // 1% default step
        unit = "%",
        decimals = 0,
        group = FineTuneParamGroup.EXTRUSION,
    ),
    FineTuneParam(
        tuner = FineTuneTuner.PRESSURE_ADVANCE,
        name = "Pressure Advance",
        icon = DinghyIcons.PressureAdvance,
        steps = persistentListOf(0.001, 0.005, 0.01),
        defaultStepIndex = 0,
        unit = "",
        decimals = 3,
        group = FineTuneParamGroup.EXTRUSION,
    ),
    FineTuneParam(
        tuner = FineTuneTuner.SMOOTH_TIME,
        name = "Smooth Time",
        icon = DinghyIcons.SmoothTime,
        steps = persistentListOf(0.01, 0.02, 0.05),
        defaultStepIndex = 0,
        unit = " s",
        decimals = 2,
        group = FineTuneParamGroup.EXTRUSION,
    ),
    FineTuneParam(
        tuner = FineTuneTuner.PART_FAN,
        name = "Part Fan",
        icon = DinghyIcons.FanMode,
        steps = persistentListOf(1.0, 5.0, 10.0),
        defaultStepIndex = 1,   // 5% default step
        unit = "%",
        decimals = 0,
        group = FineTuneParamGroup.EXTRUSION,
    ),
    // ─── Motion group (pool index 1) ────────────────────────────────────────────
    FineTuneParam(
        tuner = FineTuneTuner.MAX_VELOCITY,
        name = "Max Velocity",
        icon = DinghyIcons.MaxVelocity,
        steps = persistentListOf(10.0, 50.0, 100.0),
        defaultStepIndex = 0,
        unit = " mm/s",
        decimals = 0,
        group = FineTuneParamGroup.MOTION,
    ),
    FineTuneParam(
        tuner = FineTuneTuner.MAX_ACCEL,
        name = "Max Accel",
        icon = DinghyIcons.MaxAccel,
        steps = persistentListOf(100.0, 500.0, 1000.0),
        defaultStepIndex = 0,
        unit = " mm/s²",
        decimals = 0,
        group = FineTuneParamGroup.MOTION,
    ),
    FineTuneParam(
        tuner = FineTuneTuner.MIN_CRUISE,
        name = "Min Cruise",
        icon = DinghyIcons.MinCruise,
        steps = persistentListOf(1.0, 5.0, 10.0),
        defaultStepIndex = 0,
        unit = "%",
        decimals = 0,
        group = FineTuneParamGroup.MOTION,
    ),
    FineTuneParam(
        tuner = FineTuneTuner.SCV,
        name = "Square Corner Vel",
        icon = DinghyIcons.SquareCornerVelocity,
        steps = persistentListOf(0.1, 0.5, 1.0),
        defaultStepIndex = 0,
        unit = " mm/s",
        decimals = 1,
        group = FineTuneParamGroup.MOTION,
    ),
    // ─── FW-retraction group (pool index 2) — hidden when !hasFwRetraction (D-08) ───
    FineTuneParam(
        tuner = FineTuneTuner.RETRACT_LENGTH,
        name = "Retract Length",
        icon = DinghyIcons.OutputCircle,
        steps = persistentListOf(0.1, 0.5, 1.0),
        defaultStepIndex = 0,
        unit = " mm",
        decimals = 2,
        group = FineTuneParamGroup.FW_RETRACTION,
        requiresFwRetraction = true,
    ),
    FineTuneParam(
        tuner = FineTuneTuner.RETRACT_SPEED,
        name = "Retract Speed",
        icon = DinghyIcons.MaxAccel,
        steps = persistentListOf(1.0, 5.0, 10.0),
        defaultStepIndex = 0,
        unit = " mm/s",
        decimals = 0,
        group = FineTuneParamGroup.FW_RETRACTION,
        requiresFwRetraction = true,
    ),
    FineTuneParam(
        tuner = FineTuneTuner.UNRETRACT_EXTRA_LENGTH,
        name = "Unretract Extra",
        icon = DinghyIcons.InputCircle,
        steps = persistentListOf(0.1, 0.5, 1.0),
        defaultStepIndex = 0,
        unit = " mm",
        decimals = 2,
        group = FineTuneParamGroup.FW_RETRACTION,
        requiresFwRetraction = true,
    ),
    FineTuneParam(
        tuner = FineTuneTuner.UNRETRACT_SPEED,
        name = "Unretract Speed",
        icon = DinghyIcons.MaxVelocity,
        steps = persistentListOf(1.0, 5.0, 10.0),
        defaultStepIndex = 0,
        unit = " mm/s",
        decimals = 0,
        group = FineTuneParamGroup.FW_RETRACTION,
        requiresFwRetraction = true,
    ),
)

/** Retrieve the current display value from [vm] for the given [tuner]. */
fun FineTuneVm.valueForTuner(tuner: FineTuneTuner): Double? = when (tuner) {
    FineTuneTuner.SPEED              -> speedPct?.toDouble()
    FineTuneTuner.FLOW               -> flowPct?.toDouble()
    FineTuneTuner.PRESSURE_ADVANCE   -> pressureAdvance
    FineTuneTuner.SMOOTH_TIME        -> smoothTime
    FineTuneTuner.PART_FAN           -> partFanPct?.toDouble()
    FineTuneTuner.MAX_VELOCITY       -> maxVelocity
    FineTuneTuner.MAX_ACCEL          -> maxAccel
    FineTuneTuner.MIN_CRUISE         -> minCruisePct?.toDouble()
    FineTuneTuner.SCV                -> scv
    FineTuneTuner.RETRACT_LENGTH     -> retractLength
    FineTuneTuner.RETRACT_SPEED      -> retractSpeed
    FineTuneTuner.UNRETRACT_EXTRA_LENGTH -> unretractExtraLength
    FineTuneTuner.UNRETRACT_SPEED    -> unretractSpeed
}

/**
 * Retrieve the config-reset baseline from [vm] for the given [tuner]. Null = no reset affordance.
 *
 * Note: SPEED and FLOW always use protocol defaults (M220/M221 S100) and PART_FAN has no persistent
 * baseline in Klipper — these three return null (REVIEW #3). MIN_CRUISE baseline is in DISPLAY
 * percent (ratio ×100) to match the live value scale.
 */
fun FineTuneVm.baselineForTuner(tuner: FineTuneTuner): Double? = when (tuner) {
    FineTuneTuner.SPEED              -> null  // protocol reset M220 S100 (no config baseline)
    FineTuneTuner.FLOW               -> null  // protocol reset M221 S100 (no config baseline)
    FineTuneTuner.PRESSURE_ADVANCE   -> baselines.pressureAdvance
    FineTuneTuner.SMOOTH_TIME        -> baselines.smoothTime
    FineTuneTuner.PART_FAN           -> null  // no persistent [fan] baseline in Klipper
    FineTuneTuner.MAX_VELOCITY       -> baselines.maxVelocity
    FineTuneTuner.MAX_ACCEL          -> baselines.maxAccel
    FineTuneTuner.MIN_CRUISE         -> baselines.minCruise?.let { it * 100.0 } // ratio→% for display match
    FineTuneTuner.SCV                -> baselines.scv
    FineTuneTuner.RETRACT_LENGTH     -> baselines.retractLength
    FineTuneTuner.RETRACT_SPEED      -> baselines.retractSpeed
    FineTuneTuner.UNRETRACT_EXTRA_LENGTH -> baselines.unretractExtraLength
    FineTuneTuner.UNRETRACT_SPEED    -> baselines.unretractSpeed
}

/**
 * Clamp [rawTarget] for [tuner] using the matching [PrinterCommands] clamp authority (D-22 / 17-07).
 *
 * EVERY nudge must pass its target through this before [FineTuneHolder.markPending], so the armed
 * pending-flip target equals the value the wire will actually send — preventing the P17 UAT Check-6
 * permanent busy-lock wedge (unclamped optimistic target at the cap). MIN_CRUISE and SPEED/FLOW receive
 * Int-typed clamps; the result is widened to Double.
 *
 * Note: RETRACT_SPEED and UNRETRACT_SPEED are displayed as mm/s (Double) but the wire uses Int;
 * the retraction command builder rounds to Int — we clamp the Double here (same bound, wider type).
 */
fun clampForTuner(tuner: FineTuneTuner, rawTarget: Double): Double = when (tuner) {
    FineTuneTuner.SPEED              -> PrinterCommands.clampSpeedPct(rawTarget.toInt()).toDouble()
    FineTuneTuner.FLOW               -> PrinterCommands.clampFlowPct(rawTarget.toInt()).toDouble()
    FineTuneTuner.PRESSURE_ADVANCE   -> PrinterCommands.clampPressureAdvance(rawTarget)
    FineTuneTuner.SMOOTH_TIME        -> PrinterCommands.clampSmoothTime(rawTarget)
    FineTuneTuner.PART_FAN           -> PrinterCommands.clampOutputPct(rawTarget.toInt()).toDouble()
    FineTuneTuner.MAX_VELOCITY       -> PrinterCommands.clampVelocity(rawTarget)
    FineTuneTuner.MAX_ACCEL          -> PrinterCommands.clampAccel(rawTarget)
    FineTuneTuner.MIN_CRUISE         -> {
        // MIN_CRUISE is displayed as percent (0..100) but clampMinCruiseRatio takes ratio (0.0..1.0).
        // Convert to ratio → clamp → convert back to percent.
        PrinterCommands.clampMinCruiseRatio(rawTarget / 100.0) * 100.0
    }
    FineTuneTuner.SCV                -> PrinterCommands.clampScv(rawTarget)
    FineTuneTuner.RETRACT_LENGTH     -> rawTarget.coerceIn(
        PrinterCommands.RETRACT_LEN_MIN,
        PrinterCommands.RETRACT_LEN_MAX,
    )
    FineTuneTuner.RETRACT_SPEED      -> rawTarget.coerceIn(
        PrinterCommands.RETRACT_SPEED_MIN.toDouble(),
        PrinterCommands.RETRACT_SPEED_MAX.toDouble(),
    )
    FineTuneTuner.UNRETRACT_EXTRA_LENGTH -> rawTarget.coerceIn(
        PrinterCommands.UNRETRACT_EXTRA_MIN,
        PrinterCommands.UNRETRACT_EXTRA_MAX,
    )
    FineTuneTuner.UNRETRACT_SPEED    -> rawTarget.coerceIn(
        PrinterCommands.RETRACT_SPEED_MIN.toDouble(),
        PrinterCommands.RETRACT_SPEED_MAX.toDouble(),
    )
}

/**
 * The WIRE decimal precision for [tuner]'s display value — which decimal grid the command builders
 * format to and [FineTuneHolder.markPending] arms at. This table MIRRORS the holder's private
 * `wirePrecisionFor` (the holder is frozen by the quick-rmr rails; duplicating the table here is
 * the accepted cost). Drift guard: FineTuneScreenNudgeTest asserts [canonicalTunerValue] equals
 * the armed pending target EXACTLY — a divergence between the two tables fails that test.
 *
 * Precisions trace [PrinterCommands] `fmt(..., N)` calls: SCV `fmt(...,1)`→1dp; PA `fmt(...,3)`→3dp;
 * smooth `fmt(...,2)`→2dp; velocity/accel `fmt(...,0)`→0dp; retraction lengths `fmt(...,1)`→1dp;
 * speeds/percents are ints→0dp. MIN_CRUISE is a 2dp RATIO on the wire == 0dp in display percent.
 */
private fun wireDecimalsForTuner(tuner: FineTuneTuner): Int = when (tuner) {
    FineTuneTuner.SPEED -> 0
    FineTuneTuner.MAX_VELOCITY -> 0
    FineTuneTuner.MAX_ACCEL -> 0
    FineTuneTuner.MIN_CRUISE -> 0 // display percent; wire ratio is 2dp == integer percent.
    FineTuneTuner.SCV -> 1
    FineTuneTuner.FLOW -> 0
    FineTuneTuner.PRESSURE_ADVANCE -> 3
    FineTuneTuner.SMOOTH_TIME -> 2
    FineTuneTuner.RETRACT_LENGTH -> 1
    FineTuneTuner.UNRETRACT_EXTRA_LENGTH -> 1
    FineTuneTuner.RETRACT_SPEED -> 0
    FineTuneTuner.UNRETRACT_SPEED -> 0
    FineTuneTuner.PART_FAN -> 0
}

/** 10^n for n in 0..3 — the precisions [wireDecimalsForTuner] yields (avoids `Math.pow` churn). */
private val WIRE_PRECISION_POW: DoubleArray = doubleArrayOf(1.0, 10.0, 100.0, 1000.0)

/**
 * Canonicalize a tuner value to its CLAMPED + WIRE-PRECISION-ROUNDED form (quick-rmr post-review
 * WR-01/02 closure): [clampForTuner] through the PrinterCommands authority, then round half-up to
 * [wireDecimalsForTuner] — the same grid [FineTuneHolder.markPending] arms and the command
 * builders format. The SAME helper applies at batcher TAP time (the displayed working value) and
 * at COMMIT time, so display == armed target == wire ALWAYS — an off-grid live baseline can never
 * show a working value that differs from what actually wires.
 */
fun canonicalTunerValue(tuner: FineTuneTuner, rawTarget: Double): Double {
    val clamped = clampForTuner(tuner, rawTarget)
    val factor = WIRE_PRECISION_POW[wireDecimalsForTuner(tuner)]
    return (clamped * factor).roundToInt() / factor
}

/**
 * COMMIT-time write path for a tuner value (quick-rmr trailing-commit batching): canonicalize
 * (re-clamp + wire-precision round) → [FineTuneHolder.markPending] → dispatch the same value.
 *
 * This is the ONLY call site that writes [FineTuneHolder.markPending] (source law) — the screen's
 * per-tap path only accumulates a canonicalized working value in
 * [works.mees.dinghy.command.TrailingCommitBatcher]; markPending fires once per commit.
 *
 * NULL-DISPATCHER GUARD (post-review): a null [dispatcher] means NOTHING will reach the wire —
 * arming markPending anyway would create a pending flip no echo can ever release (the offline
 * 8s-backstop dim, the exact 17-07 wedge class). Return BEFORE any state write.
 *
 * @param param      the descriptor for the tuner being committed.
 * @param target     the absolute target value (the batcher's final working value, or a baseline).
 *                   Idempotently re-canonicalized here — the hard invariant "clamp BEFORE
 *                   markPending" holds even if a caller forgot (17-07 Check-6).
 * @param vm         the live [FineTuneVm] (FW-retraction sibling values in the command).
 * @param holder     the [FineTuneHolder] whose [FineTuneHolder.markPending] receives the clamped target.
 * @param dispatcher the live [CommandDispatcher] (null = NO-OP, e.g. offline or in preview).
 */
fun commitTunerValue(
    param: FineTuneParam,
    target: Double,
    vm: FineTuneVm,
    holder: FineTuneHolder,
    dispatcher: CommandDispatcher?,
) {
    // Post-review fix 2: no dispatcher → no commit AT ALL (never markPending-without-command).
    if (dispatcher == null) return
    // D-22 + WR-01/02 invariant: markPending receives the CLAMPED, wire-grid value — identical to
    // what the batcher displayed at tap time (display == wire).
    val clamped = canonicalTunerValue(param.tuner, target)
    holder.markPending(param.tuner, clamped)
    // The canonical value is also the wire arg — the builders re-clamp/format identically (same
    // result); commit-time has no meaningful "raw" anymore.
    dispatchForTuner(param.tuner, clamped, clamped, vm, dispatcher)
}

/**
 * Route a reset — sets the tuner to [baseline] exactly via [commitTunerValue] (identical
 * clamp → markPending → dispatch semantics; kept so the reset call sites stay readable).
 * Resets commit IMMEDIATELY (no batching) — the screen cancels any pending working value first.
 */
fun nudgeToBaseline(
    param: FineTuneParam,
    baseline: Double,
    vm: FineTuneVm,
    holder: FineTuneHolder,
    dispatcher: CommandDispatcher?,
) {
    commitTunerValue(param, baseline, vm, holder, dispatcher)
}

/**
 * R10 (26.5-03): the dispatch key [dispatchForTuner] will use for [tuner] — the rejection-feedback
 * filter key. Derived from the SAME [CommandRegistry] specs via [works.mees.dinghy.command.CommandSpec.dispatchKey]
 * (dummy args — every key lambda depends only on the args' field constant, never the value), so a
 * registry key rename can never silently desync the [works.mees.dinghy.command.CommandDispatcher.rejectedKey]
 * filter from the actual dispatch.
 */
fun dispatchKeyForTuner(tuner: FineTuneTuner): String = when (tuner) {
    FineTuneTuner.SPEED ->
        CommandRegistry.speedFactor.dispatchKey(SpeedFactorArgs(0))
    FineTuneTuner.FLOW ->
        CommandRegistry.flowFactor.dispatchKey(FlowFactorArgs(0))
    FineTuneTuner.PRESSURE_ADVANCE ->
        CommandRegistry.setPressureAdvance.dispatchKey(PressureAdvanceArgs(PressureAdvanceArgs.ADVANCE, 0.0))
    FineTuneTuner.SMOOTH_TIME ->
        CommandRegistry.setPressureAdvance.dispatchKey(PressureAdvanceArgs(PressureAdvanceArgs.SMOOTH_TIME, 0.0))
    FineTuneTuner.PART_FAN ->
        CommandRegistry.setFan.dispatchKey(FanArgs(0))
    FineTuneTuner.MAX_VELOCITY ->
        CommandRegistry.setVelocityLimit.dispatchKey(VelocityLimitArgs(VelocityLimitArgs.VELOCITY, 0.0))
    FineTuneTuner.MAX_ACCEL ->
        CommandRegistry.setVelocityLimit.dispatchKey(VelocityLimitArgs(VelocityLimitArgs.ACCEL, 0.0))
    FineTuneTuner.MIN_CRUISE ->
        CommandRegistry.setVelocityLimit.dispatchKey(VelocityLimitArgs(VelocityLimitArgs.MIN_CRUISE_RATIO, 0.0))
    FineTuneTuner.SCV ->
        CommandRegistry.setVelocityLimit.dispatchKey(VelocityLimitArgs(VelocityLimitArgs.SCV, 0.0))
    FineTuneTuner.RETRACT_LENGTH,
    FineTuneTuner.RETRACT_SPEED,
    FineTuneTuner.UNRETRACT_EXTRA_LENGTH,
    FineTuneTuner.UNRETRACT_SPEED ->
        CommandRegistry.setRetraction.dispatchKey(RetractionArgs(0.0, 0.0, 0, 0))
}

/**
 * Internal: map [tuner] to its command and dispatch. [rawTarget] is used for the wire args (the
 * command builders re-clamp it identically). [clampedTarget] is NOT sent directly to the wire — the
 * command builders own the final format. Passing [rawTarget] to the builder is consistent with the
 * existing FineTune tile pattern (the builder re-clamps identically, so the wire value matches).
 */
private fun dispatchForTuner(
    tuner: FineTuneTuner,
    rawTarget: Double,
    @Suppress("UNUSED_PARAMETER") clampedTarget: Double,
    vm: FineTuneVm,
    dispatcher: CommandDispatcher?,
) {
    val d = dispatcher ?: return
    when (tuner) {
        FineTuneTuner.SPEED ->
            d.dispatch(CommandRegistry.speedFactor, SpeedFactorArgs(rawTarget.toInt()))
        FineTuneTuner.FLOW ->
            d.dispatch(CommandRegistry.flowFactor, FlowFactorArgs(rawTarget.toInt()))
        FineTuneTuner.PRESSURE_ADVANCE ->
            d.dispatch(CommandRegistry.setPressureAdvance, PressureAdvanceArgs(PressureAdvanceArgs.ADVANCE, rawTarget))
        FineTuneTuner.SMOOTH_TIME ->
            d.dispatch(CommandRegistry.setPressureAdvance, PressureAdvanceArgs(PressureAdvanceArgs.SMOOTH_TIME, rawTarget))
        FineTuneTuner.PART_FAN ->
            d.dispatch(CommandRegistry.setFan, FanArgs(rawTarget.toInt()))
        FineTuneTuner.MAX_VELOCITY ->
            d.dispatch(CommandRegistry.setVelocityLimit, VelocityLimitArgs(VelocityLimitArgs.VELOCITY, rawTarget))
        FineTuneTuner.MAX_ACCEL ->
            d.dispatch(CommandRegistry.setVelocityLimit, VelocityLimitArgs(VelocityLimitArgs.ACCEL, rawTarget))
        FineTuneTuner.MIN_CRUISE -> {
            // MIN_CRUISE display is percent (0..100); wire takes ratio (0.0..1.0).
            d.dispatch(CommandRegistry.setVelocityLimit, VelocityLimitArgs(VelocityLimitArgs.MIN_CRUISE_RATIO, rawTarget / 100.0))
        }
        FineTuneTuner.SCV ->
            d.dispatch(CommandRegistry.setVelocityLimit, VelocityLimitArgs(VelocityLimitArgs.SCV, rawTarget))
        FineTuneTuner.RETRACT_LENGTH,
        FineTuneTuner.RETRACT_SPEED,
        FineTuneTuner.UNRETRACT_EXTRA_LENGTH,
        FineTuneTuner.UNRETRACT_SPEED -> {
            // All four FW-retraction fields are sent together in one command (existing FwRetractionScreen pattern).
            // WR-04 (26-rev): the speed Double→Int conversion must ROUND HALF-UP to match
            // FineTuneHolder.markPending's roundToWirePrecision (0dp, half-up). Truncation (.toInt())
            // armed e.g. 24 (round 23.5 up) while the wire sent 23 — the echo could never land within
            // the flip epsilon and the group dimmed for the full pending-flip timeout (the P17
            // WR-01/02 transient-dim class re-opened for fractional retraction speeds).
            d.dispatch(
                CommandRegistry.setRetraction,
                RetractionArgs(
                    retractLength = if (tuner == FineTuneTuner.RETRACT_LENGTH) rawTarget
                                    else vm.retractLength ?: 0.0,
                    unretractExtraLength = if (tuner == FineTuneTuner.UNRETRACT_EXTRA_LENGTH) rawTarget
                                           else vm.unretractExtraLength ?: 0.0,
                    retractSpeed = (if (tuner == FineTuneTuner.RETRACT_SPEED) rawTarget
                                    else vm.retractSpeed ?: 0.0).roundToInt(),
                    unretractSpeed = (if (tuner == FineTuneTuner.UNRETRACT_SPEED) rawTarget
                                      else vm.unretractSpeed ?: 0.0).roundToInt(),
                )
            )
        }
    }
}
