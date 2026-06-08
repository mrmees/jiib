package works.mees.dinghy.webcam

import org.junit.Assert.fail
import org.junit.Test
import works.mees.dinghy.config.ConnectionConfig
import works.mees.dinghy.net.resolveWebcamUrl
import works.mees.dinghy.state.Webcam

/**
 * WAVE-0 RED SCAFFOLD (Phase 21, CAM-13) — fails until plan 21-03 implements the native-URL derive helper.
 *
 * Behavior this file pins (converted to live assertions by 21-03):
 *  - Derive the native H.264 transport URL from a cam's WHEP/HTTP `stream_url` by swapping the port:
 *      :8889 (camera-streamer/MediaMTX WHEP)  →  :8554 with an `rtsp://` scheme (RTSP transport), and
 *      :8889  →  :8888 with the `http(s)` scheme + trailing slash (HLS transport) — keeping the PATH
 *      segment parsed from `stream_url` intact.
 *  - The name≠path landmine: a friendly Moonraker `name` ("Print Bed Cam") must NEVER appear in the
 *    derived URL — only the path parsed from `stream_url` is used.
 *  - A blank / garbage / un-parseable `stream_url` → null (caller treats null as "no native URL").
 *  - The D-09 loopback host rewrite is preserved through the derive (127.0.0.1/localhost → configured host).
 *  - An explicit `extra_data` transport tag, WHEN PRESENT, is PREFERRED over the heuristic derive.
 *
 * COMPILE DISCIPLINE (project wave-0 rule): references ONLY symbols that exist today — [Webcam],
 * [ConnectionConfig], [resolveWebcamUrl]. Does NOT import/call the unbuilt native-URL derive helper or
 * the unbuilt native-transport enum; those land in 21-03, which replaces these `fail(...)` bodies with
 * typed assertions.
 */
class WebcamUrlDeriveTest {

    private val cfg = ConnectionConfig(host = "192.168.1.120", port = 7125, apiKey = null)

    private fun webcam(name: String = "", streamUrl: String? = null, snapshotUrl: String? = null) =
        Webcam(name = name, streamUrl = streamUrl, snapshotUrl = snapshotUrl)

    @Test
    fun `derive swaps 8889 WHEP to 8554 rtsp keeping the path`() {
        @Suppress("UNUSED_VARIABLE")
        val cam = webcam(name = "Print Bed Cam", streamUrl = "http://192.168.1.120:8889/cam1/whep")
        fail("not yet implemented — 21-03: assert the RTSP derive yields rtsp://192.168.1.120:8554/cam1")
    }

    @Test
    fun `derive swaps 8889 WHEP to 8888 http hls with trailing slash`() {
        @Suppress("UNUSED_VARIABLE")
        val cam = webcam(name = "Print Bed Cam", streamUrl = "http://192.168.1.120:8889/cam1/whep")
        fail("not yet implemented — 21-03: assert the HLS derive yields http://192.168.1.120:8888/cam1/")
    }

    @Test
    fun `friendly name never leaks into the derived url (name != path landmine)`() {
        @Suppress("UNUSED_VARIABLE")
        val cam = webcam(name = "Print Bed Cam", streamUrl = "http://192.168.1.120:8889/cam1/whep")
        // The derived URL must carry the PATH segment "cam1", never the spaced friendly name.
        fail("not yet implemented — 21-03: assert the derived URL contains 'cam1' and NOT 'Print Bed Cam'")
    }

    @Test
    fun `blank or garbage stream_url derives to null`() {
        @Suppress("UNUSED_VARIABLE")
        val blank = webcam(streamUrl = "")
        @Suppress("UNUSED_VARIABLE")
        val garbage = webcam(streamUrl = "::::not a url::::")
        fail("not yet implemented — 21-03: assert the native-URL derive returns null for blank/garbage stream_url")
    }

    @Test
    fun `loopback host rewrite is preserved through the derive (D-09)`() {
        // resolveWebcamUrl already performs the loopback→configured-host rewrite; the native derive must
        // keep that behavior. Sanity-check the existing resolver does the rewrite the derive will reuse.
        val resolved = resolveWebcamUrl("http://127.0.0.1:8889/cam1/whep", cfg)
        check(resolved != null && resolved.contains("192.168.1.120")) {
            "precondition: resolveWebcamUrl must rewrite loopback to the configured host"
        }
        fail("not yet implemented — 21-03: assert the native-URL derive rewrites a loopback host to 192.168.1.120")
    }

    @Test
    fun `explicit extra_data transport tag is preferred over the heuristic derive`() {
        // Webcam.extraData exists today (JsonObject); 21-03 reads an explicit transport URL out of it.
        @Suppress("UNUSED_VARIABLE")
        val cam = webcam(streamUrl = "http://192.168.1.120:8889/cam1/whep")
        fail("not yet implemented — 21-03: assert an explicit extra_data native URL wins over the port-swap heuristic")
    }
}
