package works.mees.jiib.calibration

/**
 * The Z_TILT_ADJUST / QUAD_GANTRY_LEVEL run state (CALIB-03 / D-02 — ONE code path, shared), scoped to
 * THIS page load. We deliberately do NOT read the persisted `z_tilt.applied` /
 * `quad_gantry_level.applied` flag: it stays true for the rest of a Klipper session after a single run,
 * so it cannot answer "did the user run it THIS time" — the page would land on "Done" before anything
 * ran (on-device UAT finding). State is derived from what happened on this load instead.
 *
 * - [Idle]    — not run this load (the Ready / "Home first" landing state).
 * - [Running] — Run dispatched this load and the command is still in flight.
 * - [Done]    — the dispatched run finished with no dispatcher Failure (show the parsed per-stepper
 *               adjustments, if any).
 * - [Failed]  — the dispatcher surfaced an RpcError for the run (G1).
 */
enum class TiltState { Idle, Running, Done, Failed }

/**
 * Pure tilt/QGL run-state machine (CALIB-03), scoped to this load. NO I/O, NO Compose — host-tested by
 * [TiltResultTest]. Failed is ONLY a real dispatcher Failure; a run that left the in-flight set without
 * a failure is Done.
 *
 * @param ran     Run was dispatched on this page load (reset on every entry — the printer has no
 *                reliable persistent "applied" state, so each load starts Idle).
 * @param running the run command is still in flight (its dispatch key is in the dispatcher's in-flight set).
 * @param failed  the dispatcher caught an RpcError for the run.
 */
fun tiltState(ran: Boolean, running: Boolean, failed: Boolean): TiltState = when {
    failed -> TiltState.Failed
    running -> TiltState.Running
    ran -> TiltState.Done
    else -> TiltState.Idle
}

/** A single per-stepper Z adjustment parsed from the Z_TILT_ADJUST / QGL console output. */
data class ZAdjustment(val stepper: String, val mm: Double)

private val Z_ADJUST_LINE = Regex("""^(stepper_z\d*)\s*=\s*(-?\d+(?:\.\d+)?)$""")

/**
 * True if [line] is the "Making the following Z adjustments:" header that precedes the per-stepper
 * adjustment lines in the Z_TILT_ADJUST / QGL console output. Tolerant of the leading `// ` comment
 * prefix Klipper puts on `gcode_response` lines.
 */
fun isZAdjustHeader(line: String): Boolean =
    line.removePrefix("//").trim().startsWith("Making the following Z adjustments", ignoreCase = true)

/**
 * Parse one per-stepper adjustment line — e.g. `// stepper_z = 0.052788` or `stepper_z1 = -0.0035` —
 * into a [ZAdjustment], or null if the line is not one. Tolerant of the `// ` comment prefix.
 */
fun parseZAdjustment(line: String): ZAdjustment? {
    val body = line.removePrefix("//").trim()
    val m = Z_ADJUST_LINE.find(body) ?: return null
    val mm = m.groupValues[2].toDoubleOrNull() ?: return null
    return ZAdjustment(m.groupValues[1], mm)
}
