package works.mees.dinghy.ui.finetune

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import works.mees.dinghy.command.VelocityLimitArgs

/** Unreported value placeholder (D-20: "—", never a fabricated 0). */
internal const val DASH = "—"

// --- Fixed, app-defined per-control increments (D-03..D-12 / D-18: no customization UI this phase) ----
internal const val SPEED_STEP = 5 // % (D-03)
internal const val VEL_STEP = 10.0 // mm/s (D-04)
internal const val ACCEL_STEP = 100.0 // mm/s² (D-05)
internal const val MIN_CRUISE_STEP_PCT = 5 // percentage points = 0.05 ratio (D-06, REVIEW #9)
internal const val SCV_STEP = 0.1 // mm/s (D-07)
internal const val FLOW_STEP = 1 // % (D-08)
internal const val PA_STEP = 0.001 // s (D-09)
internal const val SMOOTH_STEP = 0.01 // s (D-10)
internal const val FAN_STEP_PCT = 5 // % (D-11)
internal const val RETRACT_LEN_STEP = 0.1 // mm (D-12)
internal const val RETRACT_SPEED_STEP = 1 // mm/s (D-12)

/**
 * Format a raw Double tuner value for display: drop a trailing ".0" on whole numbers, otherwise show the
 * value at a sensible precision (no locale grouping). Null → [DASH].
 */
internal fun fmtValue(v: Double?, decimals: Int): String {
    if (v == null) return DASH
    val rounded = if (v == v.toLong().toDouble()) {
        v.toLong().toString()
    } else {
        "%.${decimals}f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')
    }
    return rounded
}

/**
 * A motion-limit value tile bound to ONE [VelocityLimitArgs] field (Max velocity / Max accel / SCV —
 * Min-cruise has its own percent-display path). Computes the target from the live [value] + [step],
 * marks the pendingStateFlip via [markPending] (D-15), then dispatches via [dispatch]. [baseline]-driven
 * reset is wired ONLY when [baseline] is non-null (REVIEW #3 — else no long-press affordance).
 *
 * Takes the [markPending]/[dispatch] side-effects directly (not the holder) so it renders identically in
 * a stateless preview, where both are no-ops (18-06 state-hoist).
 */
@Composable
internal fun VelocityLimitTile(
    @DrawableRes iconRes: Int,
    name: String,
    value: Double?,
    unit: String,
    step: Double,
    field: String,
    tuner: FineTuneTuner,
    baseline: Double?,
    markPending: (FineTuneTuner, Double) -> Unit,
    dispatch: (VelocityLimitArgs) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    fun nudge(target: Double) {
        markPending(tuner, target)
        dispatch(VelocityLimitArgs(field, target))
    }
    FineTuneTile(
        iconRes = iconRes,
        name = name,
        valueText = if (value == null) DASH else fmtValue(value, decimals = 2) + unit,
        onDecrement = { value?.let { nudge((it - step).coerceAtLeast(0.0)) } },
        onIncrement = { value?.let { nudge(it + step) } },
        onReset = baseline?.let { base -> { nudge(base) } },
        enabled = enabled,
        modifier = modifier,
    )
}
