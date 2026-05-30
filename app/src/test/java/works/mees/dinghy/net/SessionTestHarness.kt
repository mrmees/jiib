package works.mees.dinghy.net

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
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

    /**
     * The snapshot returned by the `objects.subscribe` reply. Defaults to [snapshotJson] (same shape,
     * as real Moonraker), but is overridable so a test can prove the spine seeds from the AUTHORITATIVE
     * subscribe reply — not just the earlier query — closing the query→subscribe gap (CR-02).
     */
    @Volatile
    var subscribeSnapshotJson: String? = null

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
            JsonRpcMethods.OBJECTS_LIST -> reIdResult(objectsListJson, id)
            JsonRpcMethods.OBJECTS_QUERY -> reIdResult(snapshotJson, id)
            JsonRpcMethods.OBJECTS_SUBSCRIBE -> reIdResult(subscribeSnapshotJson ?: snapshotJson, id)
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
