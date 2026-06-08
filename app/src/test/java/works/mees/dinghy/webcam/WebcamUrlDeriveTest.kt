package works.mees.dinghy.webcam

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.config.ConnectionConfig
import works.mees.dinghy.net.deriveNativeStreamUrl
import works.mees.dinghy.net.nativeStreamUrlFor
import works.mees.dinghy.net.resolveWebcamUrl
import works.mees.dinghy.state.NativeTransport
import works.mees.dinghy.state.Webcam

/**
 * CAM-13 (Phase 21, plan 21-03) — the native-transport URL derive + the explicit-tag-else-derive policy.
 *
 * Behavior pinned here (D-09/D-11/D-12, RESEARCH § URL Resolution):
 *  - [deriveNativeStreamUrl] swaps the port (and, for RTSP, the scheme) of a WebRTC/HTTP `stream_url`:
 *      :8889 → :8554 + `rtsp://` (RTSP, no trailing slash), :8889 → :8888 (HLS, trailing slash kept),
 *      KEEPING the PATH segment parsed from `stream_url`.
 *  - The name≠path landmine: a friendly Moonraker `name` ("Print Bed Cam") must NEVER appear in the
 *    derived URL — only the path parsed from `stream_url` is used.
 *  - A blank / garbage / un-parseable `stream_url` → null (caller treats null as "no native URL").
 *  - The D-09 loopback rewrite is preserved through [nativeStreamUrlFor] (127.0.0.1/localhost → host).
 *  - An explicit `extra_data` transport tag, WHEN PRESENT, is PREFERRED over the heuristic derive (D-12).
 */
class WebcamUrlDeriveTest {

    private val cfg = ConnectionConfig(host = "192.168.1.120", port = 7125, apiKey = null)

    private fun webcam(
        name: String = "",
        streamUrl: String? = null,
        snapshotUrl: String? = null,
        extraData: JsonObject = JsonObject(emptyMap()),
    ) = Webcam(name = name, streamUrl = streamUrl, snapshotUrl = snapshotUrl, extraData = extraData)

    @Test
    fun `derive swaps 8889 WHEP to 8554 rtsp keeping the path and dropping the trailing slash`() {
        // The plan's canonical case: trailing-slash path "/0/" → rtsp "/0" (no trailing slash).
        assertEquals(
            "rtsp://192.168.1.120:8554/0",
            deriveNativeStreamUrl("http://192.168.1.120:8889/0/", NativeTransport.Rtsp),
        )
        // Multi-token WHEP path → keep only the resolved encodedPath, swap port + scheme.
        assertEquals(
            "rtsp://192.168.1.120:8554/cam1/whep",
            deriveNativeStreamUrl("http://192.168.1.120:8889/cam1/whep", NativeTransport.Rtsp),
        )
    }

    @Test
    fun `derive swaps 8889 WHEP to 8888 http hls keeping http and the trailing slash`() {
        assertEquals(
            "http://192.168.1.120:8888/0/",
            deriveNativeStreamUrl("http://192.168.1.120:8889/0/", NativeTransport.Hls),
        )
        assertEquals(
            "http://192.168.1.120:8888/cam1/whep/",
            deriveNativeStreamUrl("http://192.168.1.120:8889/cam1/whep", NativeTransport.Hls),
        )
    }

    @Test
    fun `friendly name never leaks into the derived url (name != path landmine)`() {
        // name="Print Bed Cam" but the stream_url path is "0" — the derive MUST use the path, not the name.
        val resolved = resolveWebcamUrl("http://192.168.1.120:8889/0/", cfg)
        val rtsp = deriveNativeStreamUrl(resolved, NativeTransport.Rtsp)!!
        assertTrue("derived URL must carry the stream_url path segment '0'", rtsp.contains("/0"))
        assertFalse(
            "the friendly name must NEVER appear in the derived URL",
            rtsp.contains("Print Bed Cam") || rtsp.contains("print_bed_cam"),
        )
        // End-to-end through nativeStreamUrlFor with the friendly name set.
        val cam = webcam(name = "Print Bed Cam", streamUrl = "http://192.168.1.120:8889/0/")
        val e2e = nativeStreamUrlFor(cam, NativeTransport.Rtsp, cfg)!!
        assertEquals("rtsp://192.168.1.120:8554/0", e2e)
        assertFalse(e2e.contains("Print Bed Cam"))
    }

    @Test
    fun `blank or garbage or pathless stream_url derives to null`() {
        assertNull(deriveNativeStreamUrl(null, NativeTransport.Rtsp))
        assertNull(deriveNativeStreamUrl("", NativeTransport.Rtsp))
        assertNull(deriveNativeStreamUrl("   ", NativeTransport.Rtsp))
        assertNull(deriveNativeStreamUrl("::::not a url::::", NativeTransport.Rtsp))
        // Parseable host but no path segment → null (nothing to point the native transport at).
        assertNull(deriveNativeStreamUrl("http://192.168.1.120:8889/", NativeTransport.Rtsp))
        assertNull(deriveNativeStreamUrl("http://192.168.1.120:8889", NativeTransport.Hls))
        // And nativeStreamUrlFor fails safe to null when stream_url is blank/null (resolveWebcamUrl → null).
        assertNull(nativeStreamUrlFor(webcam(streamUrl = ""), NativeTransport.Rtsp, cfg))
        assertNull(nativeStreamUrlFor(webcam(streamUrl = null), NativeTransport.Hls, cfg))
    }

    @Test
    fun `loopback host rewrite is preserved through the derive (D-09)`() {
        // resolveWebcamUrl already performs the loopback→configured-host rewrite; nativeStreamUrlFor must
        // keep it (it derives from the RESOLVED url). Sanity-check the precondition first.
        val resolved = resolveWebcamUrl("http://127.0.0.1:8889/0/", cfg)
        check(resolved != null && resolved.contains("192.168.1.120")) {
            "precondition: resolveWebcamUrl must rewrite loopback to the configured host"
        }
        val cam = webcam(streamUrl = "http://127.0.0.1:8889/0/")
        val rtsp = nativeStreamUrlFor(cam, NativeTransport.Rtsp, cfg)
        assertEquals("rtsp://192.168.1.120:8554/0", rtsp)
        // localhost variant too.
        val cam2 = webcam(streamUrl = "http://localhost:8889/0/")
        assertEquals(
            "http://192.168.1.120:8888/0/",
            nativeStreamUrlFor(cam2, NativeTransport.Hls, cfg),
        )
    }

    @Test
    fun `explicit extra_data transport tag is preferred over the heuristic derive`() {
        // An explicit ravens-perch native-RTSP tag wins over the port-swap derive (D-12 prefer-if-present).
        val explicitRtsp = "rtsp://192.168.1.120:8554/explicit-from-tag"
        val cam = webcam(
            streamUrl = "http://192.168.1.120:8889/0/",
            extraData = JsonObject(mapOf("ravens_perch_native_rtsp" to JsonPrimitive(explicitRtsp))),
        )
        assertEquals(explicitRtsp, nativeStreamUrlFor(cam, NativeTransport.Rtsp, cfg))
        // The HLS transport (no explicit HLS tag here) still falls through to the derive.
        assertEquals(
            "http://192.168.1.120:8888/0/",
            nativeStreamUrlFor(cam, NativeTransport.Hls, cfg),
        )
    }

    @Test
    fun `a blank or non-string explicit tag falls through to the derive (tolerant)`() {
        // Blank value → ignored, fall to derive.
        val blankTag = webcam(
            streamUrl = "http://192.168.1.120:8889/0/",
            extraData = JsonObject(mapOf("ravens_perch_native_rtsp" to JsonPrimitive(""))),
        )
        assertEquals(
            "rtsp://192.168.1.120:8554/0",
            nativeStreamUrlFor(blankTag, NativeTransport.Rtsp, cfg),
        )
        // Non-string (object) value → ignored, fall to derive (never throws).
        val garbageTag = webcam(
            streamUrl = "http://192.168.1.120:8889/0/",
            extraData = JsonObject(
                mapOf("ravens_perch_native_rtsp" to JsonObject(mapOf("nested" to JsonPrimitive("x")))),
            ),
        )
        assertEquals(
            "rtsp://192.168.1.120:8554/0",
            nativeStreamUrlFor(garbageTag, NativeTransport.Rtsp, cfg),
        )
    }
}
