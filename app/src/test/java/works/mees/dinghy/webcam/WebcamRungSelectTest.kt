package works.mees.dinghy.webcam

import org.junit.Assert.fail
import org.junit.Test

/**
 * COMPILING runtime-RED scaffold — fail()s until plan 10-02 REPLACES this body with typed assertions
 * against the rung selector (`net/WebcamProbe.kt`). See WebcamListParseTest's KDoc for why the body is
 * a fail() stub (the cross-wave compile rule).
 *
 * What 10-02 must prove here (CAM-01 / D-02 sniff): the HTTP `Content-Type` is the source of truth
 * (`service` is only a HINT). `multipart/x-mixed-replace` → rung 1; blank/`image/jpeg` (snapshot
 * reachable) → rung 2; `webrtc-mediamtx` service whose stream 404s text/plain (or no usable
 * stream+snapshot) → rung 3. The replacement drives [FakeWebcamHttp] (already hardened with the
 * 404-text-plain / 401 / multipart / jpeg cases) — no network.
 */
class WebcamRungSelectTest {

    @Test
    fun contentTypeIsSourceOfTruth_routesToRung1Rung2Rung3() {
        fail("RED until plan 10-02 builds WebcamProbe rung selector and replaces this scaffold body")
    }
}
