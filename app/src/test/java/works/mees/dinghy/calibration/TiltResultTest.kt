// RED scaffold (Wave 0) — turns GREEN in 09-04 (tiltState pure state machine).
package works.mees.dinghy.calibration

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Wave-0 RED scaffold (09-01) — turned GREEN by 09-04 (`tiltState`).
 *
 * REQ-CALIB-03. The Z_TILT_ADJUST / QUAD_GANTRY_LEVEL pure state machine over
 * {dispatched, applied, failed}. The captured z_tilt fixture (`/fixtures/z_tilt_e5.json`)
 * is `{z_tilt:{applied:false}}` — applied:false POST-RUN — which is exactly the trap:
 *
 * REAL-SHAPE CONTRACT (RESEARCH Pitfall 2):
 *   - `applied == true`  → Done.
 *   - `dispatched && !applied && !failed` → Running (NOT Failed).
 *   - `failed` (a dispatcher RpcError surfaced) → Failed.
 *   - **NEVER infer Failed from `applied == false` alone** — applied:false is BOTH
 *     "running" and "post-run-not-converged"; only an RpcError marks Failed.
 *
 * Production symbol referenced (NOT YET BUILT → RED): `tiltState(dispatched, applied, failed)`
 * in `works.mees.dinghy.calibration`, returning a `TiltState` enum {Idle, Running, Done, Failed}.
 */
class TiltResultTest {

    @Test
    fun appliedTrue_isDone() {
        assertEquals(TiltState.Done, tiltState(dispatched = true, applied = true, failed = false))
    }

    @Test
    fun dispatchedNotAppliedNotFailed_isRunning() {
        // The captured z_tilt_e5.json shape: applied:false while dispatched, no error.
        assertEquals(TiltState.Running, tiltState(dispatched = true, applied = false, failed = false))
    }

    @Test
    fun failed_isFailed_evenIfNotApplied() {
        assertEquals(TiltState.Failed, tiltState(dispatched = true, applied = false, failed = true))
    }

    @Test
    fun appliedFalseAloneNeverImpliesFailed() {
        // The load-bearing Pitfall-2 assertion: applied:false + no error ≠ Failed.
        assertEquals(TiltState.Running, tiltState(dispatched = true, applied = false, failed = false))
    }

    @Test
    fun notDispatched_isIdle() {
        assertEquals(TiltState.Idle, tiltState(dispatched = false, applied = false, failed = false))
    }
}
