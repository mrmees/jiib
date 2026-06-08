package works.mees.dinghy.webcam

import org.junit.Assert.fail
import org.junit.Test
import works.mees.dinghy.state.Rung
import works.mees.dinghy.state.Webcam
import works.mees.dinghy.state.rungFor

/**
 * WAVE-0 RED SCAFFOLD (Phase 21, CAM-11/12) — fails until plan 21-03 implements the H.264 rung selector.
 *
 * Behavior this file pins (converted to live assertions by 21-03):
 *  - The H.264-rung selector returns TRUE when the cam's `service` startsWith "webrtc"
 *    (case-insensitive, the MediaMTX / camera-streamer signal) OR its `stream_url` is an `rtsp://` URL.
 *  - It returns FALSE for a plain MJPEG / snapshot cam (no webrtc service, no rtsp stream_url).
 *  - The EXISTING Content-Type probe [rungFor] must stay service-BLIND and must NEVER return an H.264
 *    rung value (D-10): the H.264 rung is selected SOLELY from the `service`/`stream_url` hints up front,
 *    the byte-probe ladder is unchanged. This test holds that invariant so 21-03 cannot leak `service`
 *    into the probe.
 *
 * COMPILE DISCIPLINE (project wave-0 rule): references ONLY symbols that exist today — [Webcam], [Rung],
 * [rungFor]. Does NOT import/call the unbuilt H.264-rung selector, the unbuilt H.264 rung enum value, or
 * the unbuilt native-transport enum; those land in 21-03, which replaces these `fail(...)` bodies with
 * typed assertions.
 */
class RungSelectTest {

    private fun webcam(service: String = "", streamUrl: String? = null, snapshotUrl: String? = null) =
        Webcam(service = service, streamUrl = streamUrl, snapshotUrl = snapshotUrl)

    @Test
    fun `webrtc service cam selects the H264 rung`() {
        // Fixture exists today; the selector under test does not.
        @Suppress("UNUSED_VARIABLE")
        val cam = webcam(service = "webrtc", streamUrl = "http://printer.local:8889/cam1/whep")
        fail("not yet implemented — 21-03: assert the H.264-rung selector returns true for a webrtc service")
    }

    @Test
    fun `rtsp stream_url cam selects the H264 rung even without a webrtc service`() {
        @Suppress("UNUSED_VARIABLE")
        val cam = webcam(service = "", streamUrl = "rtsp://printer.local:8554/cam1")
        fail("not yet implemented — 21-03: assert the H.264-rung selector returns true for an rtsp:// stream_url")
    }

    @Test
    fun `plain mjpeg cam does NOT select the H264 rung`() {
        @Suppress("UNUSED_VARIABLE")
        val cam = webcam(service = "mjpegstreamer", streamUrl = "http://printer.local/webcam/?action=stream")
        fail("not yet implemented — 21-03: assert the H.264-rung selector returns false for a plain MJPEG cam")
    }

    @Test
    fun `content-type probe stays service-blind and never returns an H264 rung (D-10)`() {
        // The existing byte-probe ladder is already callable; this guards that 21-03 leaves it H.264-free.
        val mjpeg = rungFor("multipart/x-mixed-replace;boundary=x", httpCode = 200, snapshotUrlPresent = false)
        val snap = rungFor("image/jpeg", httpCode = 200, snapshotUrlPresent = true)
        // rungFor today can only yield Mjpeg / Snapshot / Unsupported — there is no H.264 rung in the probe.
        check(mjpeg == Rung.Mjpeg && snap == Rung.Snapshot)
        fail("not yet implemented — 21-03: assert no H.264 enum value is ever returned by the byte probe (service-blind, D-10)")
    }
}
