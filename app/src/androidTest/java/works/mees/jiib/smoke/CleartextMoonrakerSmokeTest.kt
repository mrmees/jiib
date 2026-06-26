package works.mees.jiib.smoke

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * THROWAWAY Phase-1 cleartext smoke probe (CONN-05 / D-10).
 *
 * This is a DISPOSABLE go/no-go probe — NOT the start of the connection layer. It deliberately
 * hand-rolls a minimal JSON-RPC exchange inline: there is NO PrinterState, NO reconnect/backoff,
 * NO capability gating, and NO shared connection/socket class here. The real resilient socket +
 * state machine begins in Phase 2 and must NOT reuse this file. Delete this once CONN-05 is proven.
 *
 * It proves D-10's three steps against the live Ender 5 Plus Moonraker on the LAN, from the real
 * Nexus 7 2013 (API 23), over CLEARTEXT:
 *
 *   1. A `ws://<host>:<port>/websocket` OkHttp websocket reaches `onOpen`.
 *   2. A `http://<host>:<port>/` REST GET to `server/info` AND `printer/info` each returns 2xx
 *      with the expected JSON-RPC `result` keys.
 *   3. An `objects/subscribe` over the socket is acked (correlated by JSON-RPC request `id`), then
 *      a DETERMINISTIC `notify_status_update` carrying the SPECIFIC subscribed object arrives within
 *      a timeout. It FAILS on timeout — it does NOT pass on a stray unsolicited `notify_*`.
 *
 * Host/port come from instrumentation args so no printer IP is baked into the committed throwaway:
 *   connectedAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.moonrakerHost=<live-ip> \
 *     -Pandroid.testInstrumentationRunnerArguments.moonrakerPort=7125
 * The defaults below are PLACEHOLDERS overridden at run time by the orchestrator.
 */
@RunWith(AndroidJUnit4::class)
class CleartextMoonrakerSmokeTest {

    private companion object {
        // PLACEHOLDER default — overridden at run time via the `moonrakerHost` instrumentation arg.
        const val DEFAULT_HOST = "192.168.1.50"

        // PLACEHOLDER default — overridden at run time via the `moonrakerPort` instrumentation arg.
        const val DEFAULT_PORT = "7125"

        // The subscribed object whose deterministic update step 3 awaits. `heater_bed` always reports
        // a `temperature` on any Klipper config with a bed; objects/subscribe forces an immediate
        // status snapshot, so we get a deterministic update without depending on the printer doing
        // anything (an idle printer may never emit an UNSOLICITED notify_*).
        const val SUBSCRIBED_OBJECT = "heater_bed"

        const val OPEN_TIMEOUT_MS = 10_000L
        const val SUBSCRIBE_AWAIT_MS = 15_000L

        // JSON-RPC request ids (correlate responses to our calls).
        const val ID_SUBSCRIBE = 9001
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun arg(key: String, default: String): String =
        InstrumentationRegistry.getArguments().getString(key) ?: default

    private val host: String get() = arg("moonrakerHost", DEFAULT_HOST)
    private val port: String get() = arg("moonrakerPort", DEFAULT_PORT)
    private val httpBase: String get() = "http://$host:$port"
    private val wsUrl: String get() = "ws://$host:$port/websocket"

    @Test
    fun cleartextMoonraker_threeStepSmoke() {
        val client = OkHttpClient.Builder()
            .connectTimeout(OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // websocket: no read timeout
            .build()

        // ---- Step 1: cleartext ws:// opens (onOpen) -------------------------------------------
        val opened = CountDownLatch(1)
        val socketFailure = AtomicReference<Throwable?>(null)

        // Step 3 plumbing — declared here because the same listener observes the subscribe ack
        // (by id) and the deterministic notify_status_update for the subscribed object.
        val subscribeAcked = CountDownLatch(1)
        val deterministicUpdate = CountDownLatch(1)

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.countDown()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return

                // (a) Response correlated by JSON-RPC id → our objects/subscribe ack.
                val id = root["id"]?.jsonPrimitive?.intOrNull
                if (id == ID_SUBSCRIBE && root.containsKey("result")) {
                    subscribeAcked.countDown()
                }

                // (b) Deterministic push: notify_status_update whose params carry OUR subscribed
                //     object. We do NOT accept any stray notify_* — it must name SUBSCRIBED_OBJECT.
                val method = root["method"]?.jsonPrimitive?.content
                if (method == "notify_status_update") {
                    val params = root["params"]
                    if (params != null && paramsContainSubscribedObject(params)) {
                        deterministicUpdate.countDown()
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socketFailure.set(t)
                opened.countDown()
                subscribeAcked.countDown()
                deterministicUpdate.countDown()
            }
        }

        val webSocket = client.newWebSocket(Request.Builder().url(wsUrl).build(), listener)
        try {
            val didOpen = opened.await(OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            socketFailure.get()?.let { fail("Step 1 FAILED: cleartext ws:// to $wsUrl errored: $it") }
            assertTrue("Step 1 FAILED: ws:// did not reach onOpen within ${OPEN_TIMEOUT_MS}ms ($wsUrl)", didOpen)

            // ---- Step 2: cleartext http:// REST GET server.info + printer.info -----------------
            assertRestInfo(client, "$httpBase/server/info", "klippy_state")
            assertRestInfo(client, "$httpBase/printer/info", "state")

            // ---- Step 3: objects/subscribe, ack by id, AWAIT deterministic update --------------
            val subscribeRpc = buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", "printer.objects.subscribe")
                putJsonObject("params") {
                    putJsonObject("objects") {
                        // null value = subscribe to ALL fields of this object.
                        put(SUBSCRIBED_OBJECT, JsonNull)
                    }
                }
                put("id", ID_SUBSCRIBE)
            }
            webSocket.send(subscribeRpc.toString())

            val acked = subscribeAcked.await(SUBSCRIBE_AWAIT_MS, TimeUnit.MILLISECONDS)
            socketFailure.get()?.let { fail("Step 3 FAILED: socket errored during subscribe: $it") }
            assertTrue(
                "Step 3 FAILED: objects/subscribe (id=$ID_SUBSCRIBE) was not acked within ${SUBSCRIBE_AWAIT_MS}ms — " +
                    "auth/trusted-client may be misconfigured (D-01b)",
                acked,
            )

            val gotUpdate = deterministicUpdate.await(SUBSCRIBE_AWAIT_MS, TimeUnit.MILLISECONDS)
            socketFailure.get()?.let { fail("Step 3 FAILED: socket errored awaiting update: $it") }
            assertTrue(
                "Step 3 FAILED: no deterministic notify_status_update naming '$SUBSCRIBED_OBJECT' arrived " +
                    "within ${SUBSCRIBE_AWAIT_MS}ms (a stray notify_* does NOT count)",
                gotUpdate,
            )
        } finally {
            webSocket.close(1000, "smoke test done")
            client.dispatcher.executorService.shutdown()
        }
    }

    /** Step 2 helper: GET a Moonraker REST info endpoint, assert 2xx + the expected `result` key. */
    private fun assertRestInfo(client: OkHttpClient, url: String, expectedResultKey: String) {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            assertTrue(
                "Step 2 FAILED: GET $url returned HTTP ${resp.code} (expected 2xx)",
                resp.isSuccessful,
            )
            val body = resp.body?.string()
            assertNotNull("Step 2 FAILED: GET $url returned an empty body", body)

            // Moonraker REST wraps payloads as { "result": { ... } }.
            val root = json.parseToJsonElement(body!!).jsonObject
            val result = root["result"]
            assertNotNull("Step 2 FAILED: GET $url response had no `result` object", result)
            val resultObj = result as? JsonObject
            assertNotNull("Step 2 FAILED: GET $url `result` was not a JSON object", resultObj)
            resultObj!!
            assertTrue(
                "Step 2 FAILED: GET $url `result` is missing expected key '$expectedResultKey' " +
                    "(keys: ${resultObj.keys})",
                resultObj.containsKey(expectedResultKey),
            )
        }
    }

    /**
     * Deterministic-update guard: the notify_status_update params is a JSON array whose first
     * element is the status object `{ "<obj>": { ... } }`. We require OUR subscribed object name
     * to be present so a stray notification for some other object can never satisfy step 3.
     */
    private fun paramsContainSubscribedObject(params: kotlinx.serialization.json.JsonElement): Boolean {
        val arr = params as? JsonArray ?: return false
        val first = arr.firstOrNull() as? JsonObject ?: return false
        return first.containsKey(SUBSCRIBED_OBJECT)
    }
}
