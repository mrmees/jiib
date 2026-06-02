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

    /** If non-null, the identify reply is this raw error frame (drives the auth/protocol-error path). */
    @Volatile
    var identifyErrorFrame: String? = null

    /** If true, a freshly opened socket immediately fails (network-down simulation) before any reply. */
    @Volatile
    var failOnOpen: Boolean = false

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
        return when (method) {
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
                    """{"jsonrpc":"2.0","result":${MoonrakerJson.parseToJsonElement(configfileResultJson)},"id":$id}"""
                }
                else invalidObjectsSubset(obj)?.let {
                    """{"jsonrpc":"2.0","error":{"code":400,"message":"$it"},"id":$id}"""
                } ?: reIdResult(snapshotJson, id)
            // One-shot temperature_store backfill (05-03) — NOT subscribed; faithful heater-keyed reply.
            JsonRpcMethods.TEMPERATURE_STORE ->
                """{"jsonrpc":"2.0","result":${MoonrakerJson.parseToJsonElement(temperatureStoreResultJson)},"id":$id}"""
            // One-shot gcode_store console backfill (08-04) — NOT subscribed; faithful entry-list reply.
            JsonRpcMethods.GCODE_STORE ->
                """{"jsonrpc":"2.0","result":${MoonrakerJson.parseToJsonElement(gcodeStoreResultJson)},"id":$id}"""
            JsonRpcMethods.OBJECTS_SUBSCRIBE ->
                invalidObjectsSubset(obj)?.let {
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
            harness.replyFor(text)?.let { inject(it) }
        }
        return accepted
    }
}
