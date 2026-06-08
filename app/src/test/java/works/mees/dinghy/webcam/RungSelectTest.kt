package works.mees.dinghy.webcam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.state.Rung
import works.mees.dinghy.state.Webcam
import works.mees.dinghy.state.rungFor
import works.mees.dinghy.state.selectsH264Rung

/**
 * CAM-11/CAM-12 (Phase 21, plan 21-03) — the H.264-rung SELECTOR + the service-blind probe invariant.
 *
 * Behavior pinned here (D-09/D-10, T-10-06):
 *  - [selectsH264Rung] returns TRUE when the cam's `service` startsWith "webrtc" (case-insensitive,
 *    the MediaMTX / camera-streamer signal) OR its `stream_url` is an `rtsp://` URL.
 *  - It returns FALSE for a plain MJPEG / snapshot cam (no webrtc service, no rtsp stream_url).
 *  - [Rung.H264] is the new tier-0 (top/preferred) rung, strictly below [Rung.Mjpeg].
 *  - The existing Content-Type probe [rungFor] stays service-BLIND and NEVER returns [Rung.H264] for
 *    any Content-Type / code / snapshot combination (D-10): the H.264 rung is selected SOLELY from the
 *    `service`/`stream_url` hints up front; the byte-probe ladder is unchanged. This test holds that
 *    invariant so the heuristic cannot leak `service` into the probe.
 */
class RungSelectTest {

    private fun webcam(service: String = "", streamUrl: String? = null, snapshotUrl: String? = null) =
        Webcam(service = service, streamUrl = streamUrl, snapshotUrl = snapshotUrl)

    @Test
    fun `H264 is the tier-0 top rung strictly below Mjpeg`() {
        assertEquals("H264 must be the tier-0 (best/top) rung", 0, Rung.H264.tier)
        assertTrue(
            "H264 must rank strictly above (lower tier than) Mjpeg",
            Rung.H264.tier < Rung.Mjpeg.tier,
        )
    }

    @Test
    fun `webrtc service cam selects the H264 rung`() {
        val cam = webcam(service = "webrtc", streamUrl = "http://printer.local:8889/cam1/whep")
        assertTrue(selectsH264Rung(cam))
    }

    @Test
    fun `webrtc-mediamtx service (de-facto) selects the H264 rung case-insensitively`() {
        val cam = webcam(service = "WebRTC-MediaMTX", streamUrl = "http://printer.local:8889/cam1/whep")
        assertTrue(selectsH264Rung(cam))
    }

    @Test
    fun `rtsp stream_url cam selects the H264 rung even without a webrtc service`() {
        val cam = webcam(service = "", streamUrl = "rtsp://printer.local:8554/cam1")
        assertTrue(selectsH264Rung(cam))
    }

    @Test
    fun `rtsp stream_url is matched case-insensitively`() {
        val cam = webcam(service = "", streamUrl = "RTSP://printer.local:8554/cam1")
        assertTrue(selectsH264Rung(cam))
    }

    @Test
    fun `plain mjpeg cam does NOT select the H264 rung`() {
        val cam = webcam(service = "mjpegstreamer", streamUrl = "http://printer.local/webcam/?action=stream")
        assertFalse(selectsH264Rung(cam))
    }

    @Test
    fun `snapshot-only cam with no stream_url does NOT select the H264 rung`() {
        val cam = webcam(service = "", streamUrl = null, snapshotUrl = "http://printer.local/snap.jpg")
        assertFalse(selectsH264Rung(cam))
    }

    @Test
    fun `content-type probe stays service-blind and never returns an H264 rung (D-10)`() {
        // Walk a representative grid of Content-Type / code / snapshot combinations and assert the byte
        // probe never yields Rung.H264 — it can only classify Mjpeg / Snapshot / Unsupported.
        val contentTypes = listOf(
            null,
            "",
            "multipart/x-mixed-replace;boundary=x",
            "MULTIPART/X-MIXED-REPLACE; boundary=dinghy",
            "image/jpeg",
            "text/html",
            "application/sdp",
            "video/avc",
        )
        val codes = listOf(200, 204, 301, 401, 403, 404, 500)
        for (ct in contentTypes) {
            for (code in codes) {
                for (snap in listOf(false, true)) {
                    val rung = rungFor(ct, httpCode = code, snapshotUrlPresent = snap)
                    assertTrue(
                        "rungFor must stay service-blind and NEVER emit H264 (ct=$ct code=$code snap=$snap)",
                        rung != Rung.H264,
                    )
                }
            }
        }
        // Sanity: the probe still classifies the canonical MJPEG / snapshot cases as before.
        assertEquals(
            Rung.Mjpeg,
            rungFor("multipart/x-mixed-replace;boundary=x", httpCode = 200, snapshotUrlPresent = false),
        )
        assertEquals(
            Rung.Snapshot,
            rungFor("image/jpeg", httpCode = 200, snapshotUrlPresent = true),
        )
    }
}
