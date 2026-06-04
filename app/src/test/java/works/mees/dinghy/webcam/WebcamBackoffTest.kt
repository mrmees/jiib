package works.mees.dinghy.webcam

import org.junit.Assert.fail
import org.junit.Test

/**
 * COMPILING runtime-RED scaffold — fail()s until plan 10-04/10-06 REPLACES this body with typed
 * assertions against the foreground-only retry backoff (`net/SnapshotPoller.kt` / `WebcamHolder`
 * retry, reusing `net/Backoff.kt`). See WebcamListParseTest's KDoc for why the body is a fail() stub
 * (the cross-wave compile rule).
 *
 * What the owning plan must prove here (D-12, T-DoS): retry backs off 1s→~10s cap while the page is
 * foreground; a 401/403 is TERMINAL-for-this-cam (no spin-retry — it won't fix itself, and spinning a
 * 401 self-DoSes the weak SBC host). The E5 snapshot is a real Basic-Auth 401 ([FakeWebcamHttp] models
 * it) → it must drive the rung-3 card, not an infinite retry.
 */
class WebcamBackoffTest {

    @Test
    fun foregroundBackoff1sTo10sCap_authFailureTerminalNoSpin() {
        fail("RED until plan 10-04/10-06 builds the foreground-only retry backoff and replaces this scaffold body")
    }
}
