package works.mees.jiib.webcam

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.net.GoldenFixtures
import works.mees.jiib.net.MoonrakerJson
import works.mees.jiib.state.parseWebcamsList

/**
 * Typed assertions for the tolerant webcam model parser (`state/WebcamModels.kt`, plan 10-02).
 *
 * Proves (CAM-01 / enumerate, T-10-04 / Security V5): BOTH goldens parse into tolerant cam models with
 * all 16 fields read; blank `service`, missing snapshot (`""`), the `?token=` snapshot, and the
 * odd-unicode E3 name all decode without throwing; a malformed payload fails safe to an empty list.
 *
 * Goldens are loaded by classpath resource via [GoldenFixtures] (never an absolute path).
 */
class WebcamListParseTest {

    private fun resultOf(goldenName: String) =
        GoldenFixtures.loadObject(goldenName)["result"]!!

    @Test
    fun parsesE5AndE3Goldens_allFields_blankServiceTolerated_missingAndTokenedSnapshot() {
        // --- E5 golden: 2 cams, webrtc-mediamtx, no-token snapshot ---
        val e5 = parseWebcamsList(resultOf("webcams_list_e5.json"))
        assertEquals("E5 golden has 2 cams", 2, e5.size)

        val ps = e5[0]
        // Every documented field is read from the wire (all 16).
        assertEquals("playstation_eye", ps.name)
        assertEquals("printer", ps.location)
        assertEquals("webrtc-mediamtx", ps.service)
        assertTrue(ps.enabled)
        assertEquals("mdiWebcam", ps.icon)
        assertEquals(30, ps.targetFps)
        assertEquals(5, ps.targetFpsIdle)
        assertEquals("http://192.168.1.120:8889/3/", ps.streamUrl)
        assertEquals("http://192.168.1.120/cameras/snapshot/3.jpg", ps.snapshotUrl)
        assertFalse(ps.flipHorizontal)
        assertFalse(ps.flipVertical)
        assertEquals(0, ps.rotation)
        assertEquals("16:9", ps.aspectRatio)
        assertEquals(JsonObject(emptyMap()), ps.extraData)
        assertEquals("database", ps.source)
        assertEquals("5bfa41e7-0000-0000-0000-000000000003", ps.uid)
        // E5 snapshot present (rung-2 eligible) even though the stream is webrtc.
        assertTrue(ps.hasSnapshot)

        // --- E3 golden: 3 cams, incl. odd-unicode name, blank-service relative cam, localhost cam ---
        val e3 = parseWebcamsList(resultOf("webcams_list_e3.json"))
        assertEquals("E3 golden has 3 cams", 3, e3.size)

        // Cam 1: odd-unicode name + ?token= snapshot — both survive verbatim.
        val lifecam = e3[0]
        assertEquals("microsoft®_lifecam_hd-3000:_mi", lifecam.name)
        assertTrue("tokened snapshot survives", lifecam.snapshotUrl!!.contains("?token="))
        assertTrue(lifecam.hasSnapshot)

        // Cam 2: blank name + blank service + relative stream + EMPTY snapshot → tolerated, no snapshot.
        val relative = e3[1]
        assertEquals("", relative.name)
        assertEquals("", relative.service)
        assertEquals("/webcam2/?action=stream", relative.streamUrl)
        assertEquals("", relative.snapshotUrl)
        assertFalse("empty snapshot_url is NOT a usable snapshot", relative.hasSnapshot)
        // Non-default field values still read (flip/rotation/aspect).
        assertTrue(relative.flipVertical)
        assertEquals(90, relative.rotation)
        assertEquals("4:3", relative.aspectRatio)

        // Cam 3: localhost/127.0.0.1 cam — read verbatim (the resolver test rewrites these later).
        val loopback = e3[2]
        assertEquals("local_loopback_cam", loopback.name)
        assertEquals("mjpegstreamer", loopback.service)
        assertEquals("http://127.0.0.1:8080/?action=stream", loopback.streamUrl)
        assertEquals("http://localhost:8080/?action=snapshot", loopback.snapshotUrl)
        assertEquals(180, loopback.rotation)
        assertTrue(loopback.flipHorizontal)
    }

    @Test
    fun garbagePayload_failsSafeToEmptyList_neverThrows() {
        // Truncated/garbage shapes must never throw — fail safe to empty (greyed tile, D-08).
        assertTrue(parseWebcamsList(null).isEmpty())
        // result present but webcams missing.
        assertTrue(parseWebcamsList(MoonrakerJson.parseToJsonElement("""{"foo":"bar"}""")).isEmpty())
        // webcams is the wrong type (not an array).
        assertTrue(parseWebcamsList(MoonrakerJson.parseToJsonElement("""{"webcams":"nope"}""")).isEmpty())
        // result is a primitive, not an object.
        assertTrue(parseWebcamsList(MoonrakerJson.parseToJsonElement("""42""")).isEmpty())
    }

    @Test
    fun unknownKeysIgnored_andOddRotationFoldsToZeroViaSafeRotation() {
        // An entry with an unknown extra key + an out-of-range rotation still decodes; safeRotation folds it.
        val json = """
            {"webcams":[
              {"name":"x","rotation":45,"mystery_field":"ignored","stream_url":"/s"}
            ]}
        """.trimIndent()
        val cams = parseWebcamsList(MoonrakerJson.parseToJsonElement(json))
        assertEquals(1, cams.size)
        assertEquals(45, cams[0].rotation)          // raw value preserved
        assertEquals(0, cams[0].safeRotation)        // illegal angle folds to 0 for the draw Matrix
        assertNull(cams[0].location)                 // absent field → null default, never fabricated
    }
}
