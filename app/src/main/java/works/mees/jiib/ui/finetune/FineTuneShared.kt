package works.mees.jiib.ui.finetune

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
    if (v == v.toLong().toDouble()) return v.toLong().toString()
    // CR-03 (26-rev): only strip trailing zeros when the formatted string actually CONTAINS a
    // decimal point. With decimals = 0 the string has no point, and an unconditional trimEnd('0')
    // corrupts integral digits — fmtValue(1499.5, 0) rendered "15" instead of "1500".
    val s = "%.${decimals}f".format(java.util.Locale.US, v)
    return if ('.' in s) s.trimEnd('0').trimEnd('.') else s
}

