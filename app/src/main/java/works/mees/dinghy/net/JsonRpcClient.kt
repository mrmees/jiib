package works.mees.dinghy.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicLong

/**
 * The JSON-RPC correlation + dispatch layer over a [MoonrakerSocket] frame stream (02-RESEARCH
 * § Pattern 2). This is the STATE-05 correctness seam plus two review HIGH fixes.
 *
 * Responsibilities:
 * - **id-correlation (STATE-05):** [request] registers a [CompletableDeferred] under a fresh
 *   [AtomicLong] id and awaits the reply whose `id` echoes it — NEVER the next arriving frame.
 *   Interleaved `notify_*` frames between a request and its reply route to notification flows and
 *   can never satisfy a pending request.
 * - **per-request timeout (review HIGH #1 / T-02-10):** every [request] is wrapped in
 *   [withTimeout]; on timeout it removes its own pending entry and throws a typed
 *   [ConnectionError.NetworkUnavailable]-bearing failure — a missing reply can never deadlock.
 * - **close-fails-all-pending (review HIGH #1 / T-02-10):** [close] atomically snapshots+clears the
 *   pending map and completes EVERY deferred exceptionally with a typed cause, so a socket death /
 *   reconnect / send failure / parse failure can never leave a suspended request hanging.
 * - **notification routing:** frames with a `method` and no `id` route to [statusUpdates],
 *   [klippyEvents], or [gcodeResponses] by method name. The gcode line stream is a SEPARATE,
 *   bounded, un-throttled flow kept distinct from status (STATE-03 discretion / T-02-05).
 * - **hostile-JSON defense (T-02-04):** every parse is `runCatching`-guarded; a malformed frame is
 *   dropped (never fatal), and a malformed *response* fails only its own deferred without wedging
 *   the map.
 *
 * Sends go ONLY through the active [RpcConnection] (Task 1), set on [SocketEvent.Open] via
 * [bind] and cleared on [SocketEvent.Closed] via [close] — there is no path to send outside a live
 * socket lifecycle.
 */
class JsonRpcClient(
    private val defaultTimeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS,
) {
    private val idCounter = AtomicLong(1)
    private val pendingMutex = Mutex()
    private val pending = mutableMapOf<Long, CompletableDeferred<JsonElement>>()

    @Volatile
    private var connection: RpcConnection? = null

    private val _statusUpdates = MutableSharedFlow<JsonObject>(extraBufferCapacity = STATUS_BUFFER)
    /** `notify_status_update` diffs — element [0] of the params array (the changed-objects object). */
    val statusUpdates: SharedFlow<JsonObject> = _statusUpdates.asSharedFlow()

    private val _klippyEvents = MutableSharedFlow<String>(extraBufferCapacity = KLIPPY_BUFFER)
    /** `notify_klippy_ready/shutdown/disconnected` — the bare method name (these carry no params). */
    val klippyEvents: SharedFlow<String> = _klippyEvents.asSharedFlow()

    private val _gcodeResponses = MutableSharedFlow<String>(extraBufferCapacity = GCODE_BUFFER)
    /**
     * `notify_gcode_response` lines — element [0] of the params array (the gcode line string). A
     * SEPARATE, bounded, un-throttled flow, intentionally distinct from [statusUpdates] (no
     * conflation at this layer; T-02-05 bounds the buffer for the 2GB target).
     */
    val gcodeResponses: SharedFlow<String> = _gcodeResponses.asSharedFlow()

    /** Bind the active socket connection (call on [SocketEvent.Open]). */
    fun bind(connection: RpcConnection) {
        this.connection = connection
    }

    /**
     * Issue a JSON-RPC request and suspend until the reply whose `id` matches resolves (STATE-05),
     * or fail with a typed cause on timeout / socket death / error response.
     *
     * @throws RpcError if the server returns an `{error,id}` envelope (carries code/message).
     * @throws java.io.IOException-bearing failure surfaced via [ConnectionError] on no active
     *   connection, send failure, or per-request timeout.
     */
    suspend fun request(
        method: String,
        params: JsonElement? = null,
        timeoutMs: Long = defaultTimeoutMs,
    ): JsonElement {
        val id = idCounter.getAndIncrement()
        val deferred = CompletableDeferred<JsonElement>()
        pendingMutex.withLock { pending[id] = deferred }

        val conn = connection
        if (conn == null) {
            pendingMutex.withLock { pending.remove(id) }
            throw RpcConnectionException(ConnectionError.NetworkUnavailable, "no active connection")
        }

        val frame = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            if (params != null) put("params", params)
            put("id", id)
        }.toString()

        try {
            conn.send(frame)
        } catch (e: IllegalStateException) {
            // Send failed (socket closing/closed) — fail fast, never silently drop (review HIGH #1).
            pendingMutex.withLock { pending.remove(id) }
            throw RpcConnectionException(ConnectionError.NetworkUnavailable, "send failed: ${e.message}")
        }

        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            // Remove our own entry so a never-answered request can't wedge the map (T-02-10).
            pendingMutex.withLock { pending.remove(id) }
            // A request-await timeout is NOT a transport failure: the frame was sent and accepted,
            // we simply have no reply yet. Type it as ConnectionError.Timeout (distinct from the
            // genuine no-connection / send-failure paths above, which stay NetworkUnavailable) so the
            // dispatcher can tell a slow-but-valid gcode apart from a real send failure (G4).
            throw RpcConnectionException(
                ConnectionError.Timeout,
                "request '$method' (id=$id) timed out after ${timeoutMs}ms",
            )
        }
    }

    /**
     * Feed one raw inbound frame (from the [MoonrakerSocket] frame flow). Splits per Pattern 2:
     * - `id != null` + `result` → resolve `pending[id]`,
     * - `id != null` + `error`  → fail `pending[id]` with a typed [RpcError],
     * - `method` present, no `id` → route to the notification flow by method name,
     * - unparseable / unmatched → dropped (never fatal; T-02-04).
     *
     * Suspends only on the pending mutex; safe to call from the socket-collecting coroutine.
     */
    suspend fun dispatch(text: String) {
        val obj = runCatching { MoonrakerJson.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return // malformed frame: drop (T-02-04), never fatal.

        val id = obj["id"]?.jsonPrimitive?.longOrNull
        if (id != null) {
            // It is a response (success or error) correlated by id.
            val deferred = pendingMutex.withLock { pending.remove(id) } ?: return
            val error = obj["error"]
            if (error != null) {
                val errObj = error.jsonObject
                // intOrNull (NOT content.toInt() coerced to 0): garbage/absent code → null, which
                // classifyIdentifyError maps to the safe AuthRequired fallback, not ServerError(0) (WR-05).
                val code = errObj["code"]?.jsonPrimitive?.intOrNull
                val message = errObj["message"]?.jsonPrimitive?.contentSafe() ?: "JSON-RPC error"
                deferred.completeExceptionally(RpcError(code, message))
            } else {
                val result = obj["result"]
                if (result != null) {
                    deferred.complete(result)
                } else {
                    // id present but neither result nor error parseable — fail just this deferred,
                    // do NOT wedge the map (T-02-04).
                    deferred.completeExceptionally(
                        RpcConnectionException(
                            ConnectionError.ParseError("response id=$id had no result/error"),
                            "malformed response",
                        ),
                    )
                }
            }
            return
        }

        // No id → a notification (or noise). Route by method; never resolves a pending request.
        val method = obj["method"]?.jsonPrimitive?.contentSafe() ?: return
        when (method) {
            JsonRpcMethods.NOTIFY_STATUS_UPDATE -> {
                statusDiff(obj)?.let { _statusUpdates.tryEmit(it) }
            }
            JsonRpcMethods.NOTIFY_GCODE_RESPONSE -> {
                gcodeLine(obj)?.let { _gcodeResponses.tryEmit(it) }
            }
            JsonRpcMethods.NOTIFY_KLIPPY_READY,
            JsonRpcMethods.NOTIFY_KLIPPY_SHUTDOWN,
            JsonRpcMethods.NOTIFY_KLIPPY_DISCONNECTED -> {
                _klippyEvents.tryEmit(method)
            }
            // Any other notify_* is unmodeled here; drop it (a later wave may add routing).
            else -> Unit
        }
    }

    /**
     * Atomically fail+clear ALL pending deferreds with [cause] and clear the active connection
     * (review HIGH #1 / T-02-10). Call on [SocketEvent.Closed], on cancel, or on a fatal parse.
     * Idempotent — the snapshot-and-clear leaves the map empty so the client is reusable after a
     * fresh [bind].
     */
    suspend fun close(cause: ConnectionError) {
        connection = null
        val snapshot = pendingMutex.withLock {
            val copy = pending.values.toList()
            pending.clear()
            copy
        }
        val failure = RpcConnectionException(cause, "connection closed")
        snapshot.forEach { it.completeExceptionally(failure) }
    }

    // ---- private helpers ------------------------------------------------------------------------

    /** Extract the status-diff object — element [0] of the 2-element params array. Null-safe. */
    private fun statusDiff(obj: JsonObject): JsonObject? = runCatching {
        obj["params"]?.jsonArray?.firstOrNull()?.jsonObject
    }.getOrNull()

    /** Extract the gcode line — element [0] of the 1-element params array. Null-safe. */
    private fun gcodeLine(obj: JsonObject): String? = runCatching {
        obj["params"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentSafe()
    }.getOrNull()

    private fun JsonPrimitive.contentSafe(): String? = runCatching { content }.getOrNull()

    companion object {
        /** Default per-request deadline; a reply must arrive within this window (review HIGH #1). */
        const val DEFAULT_REQUEST_TIMEOUT_MS = 10_000L

        // Bounded notification buffers (T-02-05): never let an unbounded gcode log eat 2GB RAM.
        private const val STATUS_BUFFER = 64
        private const val KLIPPY_BUFFER = 16
        private const val GCODE_BUFFER = 256
    }
}

/**
 * A typed transport-layer failure carrying a [ConnectionError] reason. Distinct from [RpcError]
 * (which models a JSON-RPC `{error}` envelope from the server); this is raised for client-side /
 * transport conditions (no connection, send failure, timeout, socket close, malformed response).
 *
 * Deliberately a plain [Exception], NOT a [kotlin.coroutines.cancellation.CancellationException]:
 * `CompletableDeferred.completeExceptionally(CancellationException)` would CANCEL the deferred
 * rather than fail it, and `await()` would surface a generic cancellation instead of this typed
 * reason — defeating the close-fails-all-pending contract (review HIGH #1).
 */
class RpcConnectionException(
    val reason: ConnectionError,
    override val message: String,
) : Exception(message)
