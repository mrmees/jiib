package works.mees.dinghy.calibration

/**
 * The Z_TILT_ADJUST / QUAD_GANTRY_LEVEL convergence state (CALIB-03 / D-02 — ONE code path, shared).
 *
 * - [Idle]    — never dispatched.
 * - [Running] — dispatched, not yet `applied`, no failure. The captured `z_tilt_e5.json` post-run
 *               shape is `{applied:false}`, which is BOTH "running" AND "post-run-not-converged";
 *               we treat it as Running until either `applied==true` (Done) or an RpcError (Failed).
 * - [Done]    — `applied == true` (the explicit convergence signal).
 * - [Failed]  — the dispatcher surfaced an RpcError (G1) — NEVER inferred from `applied==false` alone.
 */
enum class TiltState { Idle, Running, Done, Failed }

/**
 * Pure tilt/QGL state machine (CALIB-03 / Pitfall 2). Mirrors the `when`-based pure derive idiom in
 * `ui/route/TopRoute.kt`. NO I/O, NO Compose — host-tested by [TiltResultTest].
 *
 * @param dispatched whether the routine was started this session.
 * @param applied the live `z_tilt.applied` / `quad_gantry_level.applied` flag.
 * @param failed whether the dispatcher caught an RpcError for the run.
 *
 * LOAD-BEARING (Pitfall 2): Failed comes ONLY from [failed]; `applied == false` never implies Failed.
 */
fun tiltState(dispatched: Boolean, applied: Boolean, failed: Boolean): TiltState = when {
    failed -> TiltState.Failed
    applied -> TiltState.Done
    dispatched -> TiltState.Running
    else -> TiltState.Idle
}
