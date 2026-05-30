package works.mees.dinghy

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.net.FakeWebSocket
import works.mees.dinghy.net.GoldenFixtures

/**
 * Wave-0 harness/fixture sanity (no live hardware): proves every committed fixture loads + parses,
 * the autonomous fallback floor holds, the redaction holds, and [FakeWebSocket] replays golden +
 * injected adversarial frames through the real `onMessage` inbound path. Passing on the fallback
 * corpus ALONE is what makes Wave 1 genuinely autonomous.
 */
class FixtureSanityTest {

    private val allFixtures = listOf(
        "objects_list.json",
        "objects_query_snapshot.json",
        "notify_status_update_stream.json",
        "fallback_objects_list.json",
        "fallback_objects_query_snapshot.json",
        "fallback_notify_status_update_stream.json",
        "adversarial_interleaved.json",
        "adversarial_klippy_shutdown.json",
        "adversarial_auth_identify_error.json",
        "adversarial_rest_401.json",
    )

    @Test
    fun allFixturesLoadAndParse() {
        allFixtures.forEach { name ->
            val obj = GoldenFixtures.loadObject(name)   // throws if missing / not a JSON object
            assertTrue("Fixture $name parsed empty", obj.isNotEmpty())
        }
    }

    @Test
    fun fallbackObjectsListHasNonEmptyObjects() {
        val objects = GoldenFixtures.objectsList("fallback_objects_list.json")
        assertTrue("fallback_objects_list result.objects must be non-empty", objects.isNotEmpty())
        // The live name resolves (to live capture or the fallback) and is also non-empty.
        val resolved = GoldenFixtures.resolve("objects_list.json")
            .jsonObject["result"]!!.jsonObject["objects"]!!
        assertTrue("resolved objects_list must be non-empty", resolved.toString().length > 2)
    }

    @Test
    fun authIdentifyErrorCarriesUnauthorizedAnd32602() {
        val raw = GoldenFixtures.raw("adversarial_auth_identify_error.json")
        assertTrue("must contain -32602", raw.contains("-32602"))
        assertTrue("must contain \"Unauthorized\"", raw.contains("Unauthorized"))
    }

    @Test
    fun klippyShutdownHasNoParams() {
        val frame = GoldenFixtures.frames("adversarial_klippy_shutdown.json").first()
        val obj: JsonObject = works.mees.dinghy.net.MoonrakerJson.parseToJsonElement(frame).jsonObject
        assertEquals(
            "notify_klippy_shutdown",
            obj["method"]!!.jsonPrimitive.content,
        )
        assertFalse("klippy shutdown carries NO params (verified)", obj.containsKey("params"))
    }

    @Test
    fun redactionHolds_noSecretsInAnyGoldenJson() {
        val forbidden = listOf(
            Regex("""192\.168"""),
            Regex("""\.local\b"""),
            Regex("""/home/"""),
            Regex("""/mnt/"""),
            Regex("""C:\\"""),
            Regex("""data:image"""),
        )
        allFixtures.forEach { name ->
            val raw = GoldenFixtures.raw(name)
            forbidden.forEach { rx ->
                assertFalse(
                    "Redaction FAILED: $name matches forbidden pattern ${rx.pattern}",
                    rx.containsMatchIn(raw),
                )
            }
        }
    }

    @Test
    fun fakeWebSocketReplaysGoldenDiffAndInjectsAdversarialFrame() {
        val received = mutableListOf<String>()
        var failed = false
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {}
            override fun onMessage(webSocket: WebSocket, text: String) { received += text }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { failed = true }
        }
        val socket = FakeWebSocket(listener)
        socket.open()

        // The code-under-test "sends" a subscribe; the fake captures it.
        socket.send("""{"jsonrpc":"2.0","method":"printer.objects.subscribe","id":1}""")
        assertEquals(1, socket.sentFrames.size)

        // Replay one golden/fallback status diff through the real onMessage path.
        val statusFrames = GoldenFixtures.frames("fallback_notify_status_update_stream.json")
        socket.replay(listOf(statusFrames.first()))

        // Inject an adversarial frame mid-sequence (klippy shutdown, no params).
        val shutdown = GoldenFixtures.frames("adversarial_klippy_shutdown.json").first()
        socket.inject(shutdown)

        assertEquals("expected golden diff + injected adversarial frame", 2, received.size)
        assertTrue(
            "injected frame must be the klippy shutdown notification",
            received[1].contains("notify_klippy_shutdown"),
        )

        // Drive a failure to prove the close-cleanup hook reaches the listener.
        socket.simulateFailure(RuntimeException("socket died"))
        assertTrue("onFailure must reach the listener", failed)

        // And a clean close.
        val socket2Received = mutableListOf<Pair<Int, String>>()
        val l2 = object : WebSocketListener() {
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket2Received += code to reason
            }
        }
        val s2 = FakeWebSocket(l2)
        s2.close(1000, "done")
        assertEquals(listOf(1000 to "done"), socket2Received)
    }
}
