package works.mees.jiib.webcam

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.jiib.state.Rung
import works.mees.jiib.state.rungFor

/**
 * Typed assertions for the D-02 rung selector (`state/WebcamModels.kt:rungFor`, plan 10-02).
 *
 * Proves (CAM-01 / D-02 sniff, T-10-06): the HTTP `Content-Type` is the SOLE decode authority and the
 * `service` field is never consulted (it is not even a parameter of [rungFor]).
 *  - `multipart/x-mixed-replace` (any case / with boundary params) → [Rung.Mjpeg].
 *  - 2xx `image/jpeg` OR a non-multipart stream when a snapshot is present → [Rung.Snapshot].
 *  - 401/403/404 with no snapshot, or neither usable → [Rung.Unsupported].
 */
class WebcamRungSelectTest {

    @Test
    fun multipartContentType_routesToRung1_Mjpeg() {
        assertEquals(Rung.Mjpeg, rungFor("multipart/x-mixed-replace; boundary=dinghyboundary", 200, true))
        // Case-insensitive + no-boundary-param variants still route to rung 1.
        assertEquals(Rung.Mjpeg, rungFor("Multipart/X-Mixed-Replace", 200, false))
        assertEquals(Rung.Mjpeg, rungFor("multipart/x-mixed-replace", 200, false))
    }

    @Test
    fun imageJpegOrSnapshotPresent_routesToRung2_Snapshot() {
        // A 200 image/jpeg stream → snapshot rung.
        assertEquals(Rung.Snapshot, rungFor("image/jpeg", 200, true))
        // A non-multipart usable stream but a snapshot_url exists → fall to snapshot.
        assertEquals(Rung.Snapshot, rungFor("text/plain", 200, true))
        // The mediamtx WebRTC reality: stream 404s text/plain, BUT a snapshot is present → rung 2.
        assertEquals(Rung.Snapshot, rungFor("text/plain", 404, true))
    }

    @Test
    fun authFailureOr404_withNoSnapshot_routesToRung3_Unsupported() {
        // 401/403/404 are terminal-for-cam; with no snapshot fallback → unsupported card (D-04).
        assertEquals(Rung.Unsupported, rungFor("text/plain", 401, false))
        assertEquals(Rung.Unsupported, rungFor("text/plain", 403, false))
        assertEquals(Rung.Unsupported, rungFor("text/plain", 404, false))
        // Pure WebRTC: stream not multipart, no snapshot → unsupported.
        assertEquals(Rung.Unsupported, rungFor("application/sdp", 200, false))
        // Unknown/blank content type, no snapshot → unsupported.
        assertEquals(Rung.Unsupported, rungFor(null, 200, false))
        assertEquals(Rung.Unsupported, rungFor("", 500, false))
    }

    @Test
    fun serviceFieldIsNotConsulted_contentTypeIsSourceOfTruth() {
        // The selector takes NO `service` argument — a blank or hostile `service: "webrtc-mediamtx"`
        // cannot influence the decision. Identical content-type → identical rung regardless of any
        // service the caller might have. This is the D-02 guarantee, enforced by the signature itself:
        // multipart content type ALWAYS yields Mjpeg even when the live service is "webrtc-mediamtx".
        assertEquals(Rung.Mjpeg, rungFor("multipart/x-mixed-replace; boundary=b", 200, true))
        // And a webrtc stream that 404s falls to snapshot purely on the HTTP outcome, not the service.
        assertEquals(Rung.Snapshot, rungFor("text/plain", 404, true))
    }
}
