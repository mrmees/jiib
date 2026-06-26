package works.mees.jiib.webcam

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.net.WebcamProbe
import works.mees.jiib.net.redactWebcamUrl
import works.mees.jiib.net.surfaceWebcamUrl
import works.mees.jiib.state.Rung

/**
 * Typed assertions for the D-02 Content-Type probe/rung-selector (`net/WebcamProbe.kt`, plan 10-04).
 *
 * Proves (CAM-01 / D-02 / A4 / Pitfall 3 / Security V7) against the hardened [FakeWebcamHttp] double:
 *  - A `multipart/x-mixed-replace` stream → [WebcamProbe.ProbeResult.Mjpeg], body kept OPEN, boundary parsed.
 *  - The mediamtx WebRTC reality (`:8889/N/` → 404 text/plain) with a snapshot present → Snapshot.
 *  - A 401/403 (E5 ravens-perch Basic Auth) → a TERMINAL Unsupported — no spin-retry signal.
 *  - The probe issues a GET (not HEAD) and never throws a raw exception.
 *  - A resolved tokened URL redacts cleanly (the surface the probe must use before any log).
 */
class WebcamProbeTest {

    @Test
    fun multipartStream_yieldsMjpeg_bodyKeptOpen_boundaryParsed() {
        val http = FakeWebcamHttp()
        val probe = WebcamProbe(http)

        val result = probe.probe(
            resolvedStreamUrl = "http://192.168.1.120/webcam/?action=stream",
            snapshotUrlPresent = true,
        )

        assertTrue("multipart → Mjpeg rung", result is WebcamProbe.ProbeResult.Mjpeg)
        result as WebcamProbe.ProbeResult.Mjpeg
        assertEquals(Rung.Mjpeg, result.rung)
        assertEquals("boundary parsed off the Content-Type", FakeWebcamHttp.BOUNDARY, result.boundary)
        assertFalse("an MJPEG outcome is not terminal", result.terminal)
        // The body is kept OPEN for the decoder — it must be readable here, then closed by the caller.
        val bytes = result.body.bytes()
        assertTrue("the open MJPEG body carries the multipart payload", bytes.isNotEmpty())
        result.body.close()
    }

    @Test
    fun getNotHead_issuedToTheStreamUrl() {
        val http = FakeWebcamHttp()
        val probe = WebcamProbe(http)

        probe.probe("http://192.168.1.120/webcam/?action=stream", snapshotUrlPresent = false)

        assertEquals("the probe must GET, not HEAD (some MJPEG servers ignore HEAD)", "GET", http.lastRequest?.method)
    }

    @Test
    fun webrtcStream404_withSnapshotPresent_fallsToSnapshot() {
        val http = FakeWebcamHttp()
        val probe = WebcamProbe(http)

        // :8889/N/ → 404 text/plain (mediamtx); a snapshot exists → rung 2.
        val result = probe.probe("http://192.168.1.120:8889/3/", snapshotUrlPresent = true)

        assertEquals(Rung.Snapshot, result.rung)
        assertFalse(result.terminal)
    }

    @Test
    fun webrtcStream404_noSnapshot_isTerminalUnsupported() {
        val http = FakeWebcamHttp()
        val probe = WebcamProbe(http)

        val result = probe.probe("http://192.168.1.120:8889/3/", snapshotUrlPresent = false)

        assertEquals(Rung.Unsupported, result.rung)
        assertTrue("a 404 with no snapshot is terminal — no spin-retry (A4)", result.terminal)
    }

    @Test
    fun basicAuth401_isTerminalUnsupported_noSpinRetry() {
        // Drive the probe at the stream-side 401 directly (the E5 reality is a 401 on the snapshot, but
        // the probe's contract is: ANY 401/403 with no snapshot fallback is terminal-for-this-cam).
        val http = FakeWebcamHttp(responder = { req ->
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .header("WWW-Authenticate", FakeWebcamHttp.BASIC_REALM)
                .body("401 Authorization Required".toResponseBody("text/plain".toMediaType()))
                .build()
        })
        val probe = WebcamProbe(http)

        val result = probe.probe("http://192.168.1.120/stream", snapshotUrlPresent = false)

        assertEquals(Rung.Unsupported, result.rung)
        assertTrue("401 is terminal-for-this-cam (Pitfall 3) — never spin a weak SBC host", result.terminal)
    }

    @Test
    fun noStreamUrl_routesPurelyOnSnapshotPresence() {
        val http = FakeWebcamHttp()
        val probe = WebcamProbe(http)

        assertEquals(Rung.Snapshot, probe.probe(resolvedStreamUrl = null, snapshotUrlPresent = true).rung)
        val none = probe.probe(resolvedStreamUrl = "", snapshotUrlPresent = false)
        assertEquals(Rung.Unsupported, none.rung)
        assertTrue(none.terminal)
    }

    @Test
    fun transportFailure_neverThrows_foldsToNonTerminalOrSnapshot() {
        // A Call.Factory whose execute() throws an IOException (host down / refused) must NOT escape.
        val http = FakeWebcamHttp(responder = { throw java.io.IOException("connection refused") })
        val probe = WebcamProbe(http)

        // With a snapshot fallback present → rung 2 (the cam still has a path).
        assertEquals(Rung.Snapshot, probe.probe("http://10.0.0.9/stream", snapshotUrlPresent = true).rung)

        // With no fallback → a NON-terminal Unsupported (the holder may retry under foreground backoff,
        // unlike a 401 which is terminal).
        val res = probe.probe("http://10.0.0.9/stream", snapshotUrlPresent = false)
        assertEquals(Rung.Unsupported, res.rung)
        assertFalse("a transient transport failure is NOT terminal (unlike a 401)", res.terminal)
    }

    @Test
    fun resolvedTokenedUrl_redactsBeforeAnySurface() {
        // The probe must redact a tokened URL before it could ever reach a log/message (Security V7).
        val tokened = "http://192.168.1.121/cameras/snapshot/1.jpg?token=SECRETTOKEN12345"
        val redacted = redactWebcamUrl(tokened)
        assertFalse("the raw token must not survive redaction", redacted.contains("SECRETTOKEN12345"))
        assertTrue(redacted.contains("token=<redacted>"))
    }

    @Test
    fun transportFailure_diagnosticReason_isProducedThroughSurfaceRedactor_neverRawToken() {
        // CR-02: [surfaceWebcamUrl]/[redactWebcamUrl] must be a LIVE control, not dead code. The probe's
        // transient-transport-failure path is the one place a URL surfaces as a diagnostic today, and it
        // builds that breadcrumb EXCLUSIVELY through [surfaceWebcamUrl]. Prove the resulting reason is the
        // redacted URL — the raw ?token= NEVER reaches the surfaced string — so a future log of this reason
        // cannot leak the token.
        val tokenedStream = "http://192.168.1.121/webcam/?action=stream&token=SECRETTOKEN12345"
        val http = FakeWebcamHttp(responder = { throw java.io.IOException("connection refused") })
        val probe = WebcamProbe(http)

        // No snapshot fallback → a NON-terminal Unsupported carrying the redacted diagnostic reason.
        val res = probe.probe(tokenedStream, snapshotUrlPresent = false)
        assertTrue(res is WebcamProbe.ProbeResult.Unsupported)
        res as WebcamProbe.ProbeResult.Unsupported

        assertEquals(
            "the diagnostic reason is produced through the sanctioned redactor (CR-02)",
            surfaceWebcamUrl(tokenedStream),
            res.reason,
        )
        assertFalse("the raw token must NEVER reach a surfaced diagnostic", res.reason.contains("SECRETTOKEN12345"))
        assertTrue("the surfaced reason is redacted", res.reason.contains("token=<redacted>"))
    }
}
