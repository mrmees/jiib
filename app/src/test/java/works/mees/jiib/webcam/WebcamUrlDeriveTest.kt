package works.mees.jiib.webcam

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.config.ConnectionConfig
import works.mees.jiib.net.deriveNativeStreamUrl
import works.mees.jiib.net.nativeStreamUrlFor
import works.mees.jiib.net.resolveWebcamUrl
import works.mees.jiib.state.NativeTransport
import works.mees.jiib.state.Webcam

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

    /**
     * Build the REAL ravens-perch nested `extra_data` schema (the contract shipped by
     * `build_stream_extra_data()`): `extra_data.ravens_perch.streams.<proto>.url`. This is the schema the
     * code actually reads — the flat `ravens_perch_native_*` key the prior build looked for was NEVER emitted.
     */
    private fun ravensPerchExtra(rtspUrl: String? = null, hlsUrl: String? = null): JsonObject {
        val streams = buildMap<String, JsonObject> {
            if (rtspUrl != null) put("rtsp", JsonObject(mapOf("url" to JsonPrimitive(rtspUrl), "protocol" to JsonPrimitive("rtsp"))))
            if (hlsUrl != null) put("hls", JsonObject(mapOf("url" to JsonPrimitive(hlsUrl), "protocol" to JsonPrimitive("hls"))))
        }
        return JsonObject(
            mapOf(
                "ravens_perch" to JsonObject(
                    mapOf(
                        "schema_version" to JsonPrimitive(1),
                        "camera_id" to JsonPrimitive("3"),
                        "path" to JsonPrimitive("3"),
                        "streams" to JsonObject(streams),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `explicit ravens-perch nested stream url is read verbatim, not derived (the shipped bug)`() {
        // REGRESSION (21-05 on-device): the code reads extra_data.ravens_perch.streams.<proto>.url — the
        // REAL nested schema. The prior build read FLAT keys ravens-perch never emitted → null → fell through
        // to MJPEG → "Feed unavailable". Verified live on flox: playstation_eye + nozzle_tracker now play H.264.
        val cam = webcam(
            // stream_url is now the WebRTC :8889 URL; if we DERIVED from it we'd get a different (and, with an
            // empty-host cfg, null) result — the explicit nested URL must win and be returned verbatim.
            streamUrl = "http://192.168.1.120:8889/3/",
            extraData = ravensPerchExtra(
                rtspUrl = "rtsp://192.168.1.120:8554/3",
                hlsUrl = "http://192.168.1.120:8888/3/",
            ),
        )
        // The EXPLICIT nested rtsp url, NOT a derived one.
        assertEquals("rtsp://192.168.1.120:8554/3", nativeStreamUrlFor(cam, NativeTransport.Rtsp, cfg))
        // And the explicit nested hls url.
        assertEquals("http://192.168.1.120:8888/3/", nativeStreamUrlFor(cam, NativeTransport.Hls, cfg))
    }

    @Test
    fun `explicit nested url is returned even with an empty-host cfg (cfg-independence — the exact ship failure)`() {
        // The shipped failure mode: the webcam holder's cfg host is EMPTY (httpBase = "http://:7125",
        // unparseable) even though the main connection works → the derive returned null → "Feed unavailable".
        // The nested URL is ABSOLUTE + cfg-free, so it must resolve regardless of a broken cfg.
        val emptyHostCfg = ConnectionConfig(host = "", port = 7125)
        val cam = webcam(
            streamUrl = "http://192.168.1.120:8889/3/",
            extraData = ravensPerchExtra(
                rtspUrl = "rtsp://192.168.1.120:8554/3",
                hlsUrl = "http://192.168.1.120:8888/3/",
            ),
        )
        assertEquals("rtsp://192.168.1.120:8554/3", nativeStreamUrlFor(cam, NativeTransport.Rtsp, emptyHostCfg))
        assertEquals("http://192.168.1.120:8888/3/", nativeStreamUrlFor(cam, NativeTransport.Hls, emptyHostCfg))
    }

    @Test
    fun `a cam with no ravens-perch extra_data still derives via the fallback`() {
        // Non-ravens-perch cam (no nested schema) → the explicit read yields null → fall to the port-swap derive.
        val cam = webcam(streamUrl = "http://192.168.1.120:8889/0/")
        assertEquals("rtsp://192.168.1.120:8554/0", nativeStreamUrlFor(cam, NativeTransport.Rtsp, cfg))
        assertEquals("http://192.168.1.120:8888/0/", nativeStreamUrlFor(cam, NativeTransport.Hls, cfg))
    }

    @Test
    fun `a missing transport in the nested schema falls through to the derive (tolerant)`() {
        // Only an rtsp stream present → the HLS transport finds no nested url → falls to the derive.
        val cam = webcam(
            streamUrl = "http://192.168.1.120:8889/0/",
            extraData = ravensPerchExtra(rtspUrl = "rtsp://192.168.1.120:8554/3"),
        )
        assertEquals("rtsp://192.168.1.120:8554/3", nativeStreamUrlFor(cam, NativeTransport.Rtsp, cfg))
        // HLS has no nested url → derive from stream_url.
        assertEquals("http://192.168.1.120:8888/0/", nativeStreamUrlFor(cam, NativeTransport.Hls, cfg))
    }

    @Test
    fun `a blank or non-string nested url falls through to the derive (tolerant, never throws)`() {
        // Blank nested url → ignored, fall to derive.
        val blank = webcam(
            streamUrl = "http://192.168.1.120:8889/0/",
            extraData = ravensPerchExtra(rtspUrl = ""),
        )
        assertEquals("rtsp://192.168.1.120:8554/0", nativeStreamUrlFor(blank, NativeTransport.Rtsp, cfg))
        // A garbled nested node (url is an object, not a string) → ignored, fall to derive (never throws).
        val garbled = webcam(
            streamUrl = "http://192.168.1.120:8889/0/",
            extraData = JsonObject(
                mapOf(
                    "ravens_perch" to JsonObject(
                        mapOf(
                            "streams" to JsonObject(
                                mapOf(
                                    "rtsp" to JsonObject(
                                        mapOf("url" to JsonObject(mapOf("nested" to JsonPrimitive("x")))),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertEquals("rtsp://192.168.1.120:8554/0", nativeStreamUrlFor(garbled, NativeTransport.Rtsp, cfg))
    }
}
