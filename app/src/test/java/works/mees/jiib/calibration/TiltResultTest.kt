package works.mees.jiib.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * REQ-CALIB-03. The Z_TILT_ADJUST / QUAD_GANTRY_LEVEL pure run-state machine + the per-stepper
 * adjustment parser.
 *
 * The state machine is LOAD-scoped (no persisted `applied` — on-device UAT showed `z_tilt.applied`
 * persists for the whole session, so it cannot say "did the user run it this load"):
 *   - `failed` (a dispatcher RpcError surfaced) → Failed.
 *   - `running` (the run command still in flight) → Running.
 *   - `ran` and finished, no failure → Done.
 *   - never run this load → Idle.
 *
 * The adjustment parser walks the real Z_TILT_ADJUST console block:
 *   `// Making the following Z adjustments:` then `// stepper_z = 0.052788` / `// stepper_z1 = ...`.
 */
class TiltResultTest {

    @Test
    fun ranFinishedNoFailure_isDone() {
        assertEquals(TiltState.Done, tiltState(ran = true, running = false, failed = false))
    }

    @Test
    fun ranStillInFlight_isRunning() {
        assertEquals(TiltState.Running, tiltState(ran = true, running = true, failed = false))
    }

    @Test
    fun failed_isFailed_evenWhileRunning() {
        // Failed comes ONLY from a dispatcher Failure; it wins over a still-in-flight flag.
        assertEquals(TiltState.Failed, tiltState(ran = true, running = true, failed = true))
    }

    @Test
    fun notRunThisLoad_isIdle() {
        // The landing state on every page load — NOT inferred from a persisted applied flag.
        assertEquals(TiltState.Idle, tiltState(ran = false, running = false, failed = false))
    }

    @Test
    fun adjustHeader_isRecognised_withAndWithoutCommentPrefix() {
        assertEquals(true, isZAdjustHeader("// Making the following Z adjustments:"))
        assertEquals(true, isZAdjustHeader("Making the following Z adjustments:"))
        assertEquals(false, isZAdjustHeader("// Probe at 150,150 is z=0.1"))
    }

    @Test
    fun parseAdjustment_realLines() {
        // The real Z_TILT_ADJUST output shape (captured from the live E5 console).
        assertEquals(ZAdjustment("stepper_z", 0.052788), parseZAdjustment("// stepper_z = 0.052788"))
        assertEquals(ZAdjustment("stepper_z1", 0.035756), parseZAdjustment("// stepper_z1 = 0.035756"))
        // A negative adjustment + no comment prefix still parses.
        assertEquals(ZAdjustment("stepper_z2", -0.0035), parseZAdjustment("stepper_z2 = -0.0035"))
    }

    @Test
    fun parseAdjustment_nonAdjustmentLine_isNull() {
        assertNull(parseZAdjustment("// Making the following Z adjustments:"))
        assertNull(parseZAdjustment("// Probe at 150,150 is z=0.1"))
        assertNull(parseZAdjustment("stepper_x = 0.5")) // not a Z stepper
    }
}
