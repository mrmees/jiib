package works.mees.dinghy.net

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Request
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicReference

/**
 * Event-driven test harness for [MoonrakerSession] over a [FakeWebSocket]. NO polling/spin loops (those
 * starve the virtual-time dispatcher): each socket auto-replies to a handshake RPC the instant the
 * session sends it, by overriding [FakeWebSocket.send] to synchronously feed the canned reply back
 * through `onMessage`. Replies are correlated by the request's JSON-RPC id — exercising the real
 * correlation path. Each connect attempt builds a FRESH responding fake (a reconnect opens a new socket).
 */
class SessionTestHarness {

    companion object {
        /**
         * The `server.webcams.list` JSON-RPC method (Phase 10 CAM-01). Held as a harness-local literal
         * (NOT `JsonRpcMethods.WEBCAMS_LIST`) on purpose: plan 10-01 (Wave 0) adds NO production code,
         * and the harness extension must compile before the production const exists. Plan 10-03 may
         * point this at `JsonRpcMethods.WEBCAMS_LIST` once that const is built — the string value is
         * identical to the real method name, so the canned reply still matches the live contract.
         */
        const val WEBCAMS_LIST = "server.webcams.list"
    }

    /** The most recently opened fake (the live socket for the current attempt). */
    val current = AtomicReference<RespondingFakeWebSocket?>(null)

    /** Number of sockets opened (= connect attempts that reached factory.open). */
    @Volatile
    var opens: Int = 0
        private set

    var objectsListJson: String = GoldenFixtures.raw("objects_list.json")
    var snapshotJson: String = GoldenFixtures.raw("objects_query_snapshot.json")

    /** The `server.info` reply RESULT object. Components feed Capabilities.components (06-03). */
    @Volatile
    var serverInfoResultJson: String = """
        {"klippy_state":"ready","components":["history","file_manager","spoolman","webcam"]}
    """.trimIndent()

    /**
     * The snapshot returned by the `objects.subscribe` reply. Defaults to [snapshotJson] (same shape,
     * as real Moonraker), but is overridable so a test can prove the spine seeds from the AUTHORITATIVE
     * subscribe reply — not just the earlier query — closing the query→subscribe gap (CR-02).
     */
    @Volatile
    var subscribeSnapshotJson: String? = null

    /**
     * The `printer.info` one-shot RESULT object (2026-06-15 hostname seed). Defaults to a realistic
     * `{hostname,state}` shape faithful to the live Moonraker contract so the one-shot read exercises
     * [works.mees.dinghy.state.parsePrinterInfoHostname] the way flox will.
     */
    @Volatile
    var printerInfoResultJson: String = """{"hostname":"ender5plus","state":"ready"}"""

    /**
     * The `server.temperature_store` reply RESULT object (05-03 backfill). Defaults to a realistic
     * heater-keyed shape (index 0 = oldest) — faithful to the live contract so the one-shot read exercises
     * [works.mees.dinghy.state.parseTemperatureStore] the way flox will. The store also returns pure
     * `temperature_sensor X` entries the graph must ignore, so include one.
     */
    @Volatile
    var temperatureStoreResultJson: String = """
        {"extruder":{"temperatures":[21.0,22.0,23.0],"targets":[0,0,0],"powers":[0,0,0]},
         "heater_bed":{"temperatures":[60.0,61.0,62.0],"targets":[0,0,0],"powers":[0,0,0]},
         "temperature_sensor mcu":{"temperatures":[40.0,40.1,40.2]}}
    """.trimIndent()

    /**
     * The `configfile` one-shot query RESULT object (05-03 EXTR-04 + 08-04 MACRO-02). Defaults to the
     * real shape: `status.configfile.settings.extruder.{min_extrude_temp,max_extrude_only_distance}`
     * plus two lowercased `gcode_macro <name>` sections carrying a `gcode` body string (Moonraker
     * lowercases settings keys) — faithful to the live shape the macro-body extract walks. configfile is
     * a real always-defined Moonraker object, so the subset validator accepts it even though it is not in
     * the objects.list fixture.
     */
    @Volatile
    var configfileResultJson: String = """
        {"eventtime":100002.0,"status":{"configfile":{"settings":{
          "extruder":{"min_extrude_temp":170.0,"max_extrude_only_distance":50.0},
          "gcode_macro start_print":{"gcode":"M104 S{params.EXTRUDER|default(200)}\nG28"},
          "gcode_macro load_filament":{"gcode":"M83\nG1 E50 F300"}}}}}
    """.trimIndent()

    /**
     * The `server.gcode_store` one-shot backfill RESULT object (08-04 CONS-02). Defaults to a faithful
     * `{gcode_store:[{message,time,type}]}` shape carrying both a `// ` response (→ WARNING) and a plain
     * command (→ NORMAL) so the backfill-REPLACE wiring exercises [parseGcodeStore] end-to-end.
     */
    @Volatile
    var gcodeStoreResultJson: String = """
        {"gcode_store":[
          {"message":"// External Power OFF","time":1780184150.5,"type":"response"},
          {"message":"TURN_OFF_HEATERS","time":1780336705.0,"type":"command"}]}
    """.trimIndent()

    /**
     * The `server.webcams.list` one-shot enumeration RESULT object (Phase 10 CAM-01 / plan 10-01).
     * Defaults to a `{webcams:[...]}` shape FAITHFUL to the E5/E3 goldens (two webrtc-mediamtx cams,
     * one with a `?token=` snapshot, one with a blank `service`) so the spine's edge-driven one-shot
     * enumeration read receives a REAL reply instead of the `else -> {"result":{}}` empty stub.
     *
     * Faithful-mock discipline (the harness's own rule): do NOT make this more lenient than the real
     * server — the shape mirrors the captured goldens (`golden/webcams_list_e5.json` /
     * `golden/webcams_list_e3.json`). The cadence test ([webcamsListRequests]) asserts the enumeration
     * fires EXACTLY ONCE per handshake edge via the hit-counter below, never the private
     * `V1_SUBSCRIBE_CORE` constant — so the once-per-edge contract is observable through PUBLIC
     * behavior (request count + the subscribe frame carrying no webcam objects).
     */
    @Volatile
    var webcamsListResultJson: String = """
        {"webcams":[
          {"name":"playstation_eye","location":"printer","service":"webrtc-mediamtx","enabled":true,
           "icon":"mdiWebcam","target_fps":30,"target_fps_idle":5,
           "stream_url":"http://192.168.1.120:8889/3/",
           "snapshot_url":"http://192.168.1.120/cameras/snapshot/3.jpg",
           "flip_horizontal":false,"flip_vertical":false,"rotation":0,"aspect_ratio":"16:9",
           "extra_data":{},"source":"database","uid":"5bfa41e7-0000-0000-0000-000000000003"},
          {"name":"","location":"printer","service":"","enabled":true,
           "icon":"mdiWebcam","target_fps":30,"target_fps_idle":5,
           "stream_url":"/webcam2/?action=stream","snapshot_url":"",
           "flip_horizontal":false,"flip_vertical":true,"rotation":90,"aspect_ratio":"4:3",
           "extra_data":{},"source":"config","uid":"3ba24469-0000-0000-0000-000000000002"}]}
    """.trimIndent()

    /**
     * Per-method outbound-request hit-counter (Phase 10 CAM-01 cadence guard). Incremented in
     * [replyFor] for every correlatable request frame the session sends, keyed by JSON-RPC `method`.
     * Exposed read-only via [requestCount] / [webcamsListRequests] so a test can assert
     * "`server.webcams.list` was requested EXACTLY once per handshake edge, zero on subsequent ticks"
     * WITHOUT reaching into any private spine constant. This is the observable seam the
     * `WebcamEnumerationCadenceTest` (built GREEN in 10-03) asserts against.
     */
    private val requestCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** Read-only count of outbound requests seen for [method] (0 if none). */
    fun requestCount(method: String): Int = requestCounts[method] ?: 0

    /** Convenience: how many `server.webcams.list` requests the session has sent so far. */
    val webcamsListRequests: Int
        get() = requestCount(WEBCAMS_LIST)

    /** Reset the per-method hit-counter (e.g. between handshake edges in a cadence test). */
    fun resetRequestCounts() = requestCounts.clear()

    /** If non-null, the identify reply is this raw error frame (drives the auth/protocol-error path). */
    @Volatile
    var identifyErrorFrame: String? = null

    /** If true, a freshly opened socket immediately fails (network-down simulation) before any reply. */
    @Volatile
    var failOnOpen: Boolean = false

    /**
     * D-10 klippy-down window. While true, the printer is mid-restart: `objects.subscribe` and
     * `objects.query` do NOT receive an auto-injected SUCCESS reply — they get the real mid-restart
     * error frame Moonraker returns (code 503 "Klippy Not Connected"), exactly as the live server does
     * across a FIRMWARE_RESTART / SAVE_CONFIG window. This is the toggle the keystone + self-heal tests
     * drive so a re-handshake's subscribe is rejected/withheld until the test declares klippy ready
     * (set this false, then inject [klippyReadyFrame]). Modeled on [failOnOpen] / [identifyErrorFrame].
     *
     * Faithful-mock discipline: do NOT make the fake more lenient than the real server — while down, a
     * subscribe genuinely fails, so [RespondingFakeWebSocket] never flips subscriptionActive back true,
     * and a status diff injected through the gate cannot reach the store.
     */
    @Volatile
    var klippyDown: Boolean = false

    // ---- D-10 captured klippy-restart signal frames (verbatim from the live E3 capture) ---------
    // The repro printer (E3) emits, on the SAME socket: notify_klippy_disconnected … [gap] …
    // notify_klippy_ready (no notify_klippy_shutdown, no webhooks.state transition). These are non-status
    // frames, so they pass the FakeWebSocket subscription gate; the harness uses them to drive the
    // down-window verbatim rather than a lone synthetic ready.

    /** The captured klippy-DROP signal (clears the live subscription). E3/E5 both emit this verbatim. */
    val klippyDisconnectedFrame: String = """{"jsonrpc":"2.0","method":"notify_klippy_disconnected"}"""

    /** The captured klippy-READY signal (the restart-complete notification on the same socket). */
    val klippyReadyFrame: String = """{"jsonrpc":"2.0","method":"notify_klippy_ready"}"""

    /**
     * Inject the captured klippy-DROP signal on the live socket and clear [RespondingFakeWebSocket]'s
     * subscriptionActive flag — modelling the instant the printer's subscription is lost (FIRMWARE_RESTART).
     * After this, no `notify_status_update` can reach the store until a genuine re-subscribe succeeds.
     */
    fun injectKlippyDrop() {
        val fake = current.get() ?: return
        fake.subscriptionActive = false
        fake.inject(klippyDisconnectedFrame)
    }

    /** Inject the captured klippy-READY signal on the live socket (drives the re-handshake path). */
    fun injectKlippyReady() {
        current.get()?.inject(klippyReadyFrame)
    }

    /** The real mid-restart error Moonraker returns for subscribe/query while klippy is disconnected. */
    private fun klippyNotConnectedError(id: Long): String =
        """{"jsonrpc":"2.0","error":{"code":503,"message":"Klippy Not Connected"},"id":$id}"""

    /** The socket-events factory to inject into the session. */
    fun socketEvents(): (String?) -> Flow<SocketEvent> = { _ ->
        val factory = WebSocketFactory { request: Request, listener: WebSocketListener ->
            val fake = RespondingFakeWebSocket(listener, request, this)
            current.set(fake)
            opens += 1
            // Drive onOpen now — the callbackFlow producer is already registered at this point.
            fake.driveOpen()
            if (failOnOpen) fake.driveFailure(java.io.IOException("network down"))
            fake
        }
        val socket = MoonrakerSocket(factory, Request.Builder().url("http://localhost/websocket").build())
        socket.events()
    }

    /** Compute the canned reply for one outbound request frame, or null if it is not a request. */
    internal fun replyFor(raw: String): String? {
        val obj: JsonObject =
            runCatching { MoonrakerJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val id = obj["id"]?.jsonPrimitive?.longOrNull ?: return null
        val method = obj["method"]?.jsonPrimitive?.content ?: return null
        // Phase 10 cadence guard: count every correlatable outbound request by method, so a test can
        // assert server.webcams.list fires exactly once per handshake edge via PUBLIC behavior.
        requestCounts.merge(method, 1) { a, b -> a + b }
        return when (method) {
            // Phase 10 CAM-01 one-shot enumeration — faithful {webcams:[...]} reply (NOT subscribed),
            // so the spine's edge-driven read gets a real reply instead of the empty `else` stub.
            WEBCAMS_LIST ->
                """{"jsonrpc":"2.0","result":${MoonrakerJson.parseToJsonElement(webcamsListResultJson)},"id":$id}"""
            JsonRpcMethods.IDENTIFY ->
                identifyErrorFrame?.let { errorFrameWithId(it, id) }
                    // Mirror Moonraker's REAL contract: client_name/version/type/url are all required.
                    // A missing one returns `{code:400,"No data for argument: <name>"}` (verified live).
                    // This is what catches a url-less identify — the fake must not be more lenient than
                    // the server, or unit tests pass while the live handshake loops forever.
                    ?: missingIdentifyArg(obj)?.let {
                        """{"jsonrpc":"2.0","error":{"code":400,"message":"No data for argument: $it"},"id":$id}"""
                    }
                    ?: """{"jsonrpc":"2.0","result":{"connection_id":1730367696},"id":$id}"""
            "server.info" ->
                """{"jsonrpc":"2.0","result":${MoonrakerJson.parseToJsonElement(serverInfoResultJson)},"id":$id}"""
            JsonRpcMethods.OBJECTS_LIST -> reIdResult(objectsListJson, id)
            // The one-shot configfile query (05-03) is an OBJECTS_QUERY of just {configfile:null};
            // answer it with the static parsed-config shape (real Moonraker always defines configfile).
            JsonRpcMethods.OBJECTS_QUERY ->
                if (queriesOnlyConfigfile(obj)) {
                    // The one-shot configfile read is only reached AFTER objects.subscribe succeeds in
                    // runHandshake; if klippy is down the subscribe below fails first, so this branch is
                    // never hit mid-restart. Always answer it with the (possibly mutated) config fixture.
                    """{"jsonrpc":"2.0","result":${MoonrakerJson.parseToJsonElement(configfileResultJson)},"id":$id}"""
                }
                // D-10: while klippy is down the FULL-subset query fails with the real 503, exactly as the
                // live server does mid-restart (the fake must not be more lenient than reality).
                else if (klippyDown) klippyNotConnectedError(id)
                else invalidObjectsSubset(obj)?.let {
                    """{"jsonrpc":"2.0","error":{"code":400,"message":"$it"},"id":$id}"""
                } ?: reIdResult(snapshotJson, id)
            // One-shot temperature_store backfill (05-03) — NOT subscribed; faithful heater-keyed reply.
            JsonRpcMethods.TEMPERATURE_STORE ->
                """{"jsonrpc":"2.0","result":${MoonrakerJson.parseToJsonElement(temperatureStoreResultJson)},"id":$id}"""
            // One-shot printer.info hostname seed (2026-06-15) — NOT subscribed; faithful {hostname,state} reply.
            "printer.info" ->
                """{"jsonrpc":"2.0","result":${MoonrakerJson.parseToJsonElement(printerInfoResultJson)},"id":$id}"""
            // One-shot gcode_store console backfill (08-04) — NOT subscribed; faithful entry-list reply.
            JsonRpcMethods.GCODE_STORE ->
                """{"jsonrpc":"2.0","result":${MoonrakerJson.parseToJsonElement(gcodeStoreResultJson)},"id":$id}"""
            JsonRpcMethods.OBJECTS_SUBSCRIBE ->
                // D-10: a re-handshake's subscribe is REJECTED while klippy is down (real 503) — the
                // subscription does NOT come back, so RespondingFakeWebSocket does NOT re-arm
                // subscriptionActive and resumed diffs stay gated. Only a subscribe that produces a
                // SUCCESS reply (klippy up) flips subscriptionActive back true (see RespondingFakeWebSocket.send).
                if (klippyDown) klippyNotConnectedError(id)
                else invalidObjectsSubset(obj)?.let {
                    """{"jsonrpc":"2.0","error":{"code":400,"message":"$it"},"id":$id}"""
                } ?: reIdResult(subscribeSnapshotJson ?: snapshotJson, id)
            else -> """{"jsonrpc":"2.0","result":{},"id":$id}"""
        }
    }

    /**
     * The first required `server.connection.identify` argument that is absent or blank, or null when
     * all four are present. Mirrors Moonraker's required-field validation so the fake can't accept an
     * identify the real server would reject (the bug that let a url-less identify pass unit tests).
     */
    private fun missingIdentifyArg(requestObj: JsonObject): String? {
        val params = requestObj["params"]?.jsonObject
        for (arg in listOf("client_name", "version", "type", "url")) {
            val v = params?.get(arg)?.jsonPrimitive?.content
            if (v.isNullOrBlank()) return arg
        }
        return null
    }

    /**
     * Validate the `params.objects` subset for objects.query / objects.subscribe against the objects the
     * printer actually DEFINES (`objects.list` `result.objects`) — the A3 correctness seam: "never query
     * or subscribe to an object the printer doesn't define". Returns a Moonraker-style error message when
     * the subset is absent, empty, or names an object not present in the objects-list fixture; null when
     * it is a valid non-empty subset. Mirrors [missingIdentifyArg] so the fake is no more lenient than the
     * real server — a regression that subscribed to nothing, or to a wrong/absent object, now fails a
     * test instead of silently passing (WR-02).
     */
    /** True when the query's `params.objects` is exactly `{configfile}` — the 05-03 one-shot config read. */
    private fun queriesOnlyConfigfile(requestObj: JsonObject): Boolean {
        val keys = requestObj["params"]?.jsonObject
            ?.get("objects")?.jsonObject
            ?.keys
            ?: return false
        return keys == setOf("configfile")
    }

    private fun invalidObjectsSubset(requestObj: JsonObject): String? {
        val requested = requestObj["params"]?.jsonObject
            ?.get("objects")?.jsonObject
            ?.keys
            ?: return "Invalid argument: objects"
        if (requested.isEmpty()) return "Invalid argument: objects (empty subset)"

        val defined = MoonrakerJson.parseToJsonElement(objectsListJson)
            .jsonObject["result"]?.jsonObject
            ?.get("objects")?.jsonArray
            ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
            .orEmpty()
            .toSet()
        val absent = requested.firstOrNull { it !in defined }
        return absent?.let { "Invalid object: $it" }
    }

    private fun reIdResult(fixtureJson: String, id: Long): String {
        val resultObj = MoonrakerJson.parseToJsonElement(fixtureJson).jsonObject["result"]
        return """{"jsonrpc":"2.0","result":$resultObj,"id":$id}"""
    }

    private fun errorFrameWithId(errorFixtureFrame: String, id: Long): String {
        val errObj = MoonrakerJson.parseToJsonElement(errorFixtureFrame).jsonObject["error"]
        return """{"jsonrpc":"2.0","error":$errObj,"id":$id}"""
    }
}

/**
 * A [FakeWebSocket] that auto-responds: every outbound [send] synchronously feeds the harness's canned
 * reply back through `onMessage`. Event-driven — no polling.
 */
class RespondingFakeWebSocket(
    private val listener: WebSocketListener,
    request: Request,
    private val harness: SessionTestHarness,
) : FakeWebSocket(listener, request) {

    fun driveOpen() = open()
    fun driveFailure(t: Throwable) = simulateFailure(t)
    fun driveClosing() = simulateClosing()

    override fun send(text: String): Boolean {
        val accepted = super.send(text)
        if (accepted) {
            harness.replyFor(text)?.let { reply ->
                // D-10: a LIVE subscription exists only once a SUCCESSFUL objects.subscribe reply is
                // produced. Flip subscriptionActive TRUE *before* injecting it, so any status diff that
                // follows passes the gate. A down-window subscribe yields an error reply (no `result`) →
                // subscriptionActive stays false and resumed diffs remain dropped (the inject() gate).
                if (isSuccessfulSubscribeReply(text, reply)) subscriptionActive = true
                inject(reply)
            }
        }
        return accepted
    }

    /** True iff [outbound] was an objects.subscribe request AND [reply] is a success (has `result`). */
    private fun isSuccessfulSubscribeReply(outbound: String, reply: String): Boolean =
        runCatching {
            val method = MoonrakerJson.parseToJsonElement(outbound)
                .jsonObject["method"]?.jsonPrimitive?.content
            if (method != JsonRpcMethods.OBJECTS_SUBSCRIBE) return@runCatching false
            MoonrakerJson.parseToJsonElement(reply).jsonObject.containsKey("result")
        }.getOrDefault(false)
}
