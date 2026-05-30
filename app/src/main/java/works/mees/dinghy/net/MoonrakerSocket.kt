package works.mees.dinghy.net

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import works.mees.dinghy.config.DevConfig
import java.util.concurrent.TimeUnit

/**
 * One event in the raw socket lifecycle, bridged from OkHttp's [WebSocketListener] callbacks into
 * a cold [Flow] (02-RESEARCH § Pattern 1). The [Open] event carries a live [RpcConnection] — the
 * only handle through which outbound sends are possible — so a send can never precede `onOpen`
 * nor outlive a close (review HIGH #4).
 */
sealed interface SocketEvent {
    /** The socket reached `onOpen`; [connection] is bound to the live websocket for the duration. */
    data class Open(val connection: RpcConnection) : SocketEvent

    /** A raw inbound text frame (`onMessage`). Parsing/correlation happens downstream in JsonRpcClient. */
    data class Frame(val text: String) : SocketEvent

    /** The socket is closing / failed (`onClosing`/`onFailure`); [cause] is typed when known. */
    data class Closed(val cause: ConnectionError?) : SocketEvent
}

/**
 * The function that actually opens a websocket given a [Request] and a [WebSocketListener].
 *
 * This is the **deliberate testability seam** (02-PATTERNS § "Testable seam"): the real path binds
 * it to `OkHttpClient.newWebSocket`, and tests bind it to a lambda that returns a `FakeWebSocket`,
 * so golden/adversarial frames replay through the SAME `onMessage` path the real socket uses with
 * no network. The transport class never hard-codes `newWebSocket`.
 */
fun interface WebSocketFactory {
    fun open(request: Request, listener: WebSocketListener): WebSocket
}

/**
 * Bridges an OkHttp [WebSocket] into a cold [Flow] of [SocketEvent]s via [callbackFlow]
 * (02-RESEARCH § Pattern 1, § "Don't Hand-Roll" push→Flow).
 *
 * - `onOpen`  → construct an [RpcConnection] over the live socket, emit [SocketEvent.Open].
 * - `onMessage` → emit [SocketEvent.Frame] (raw text; no parsing at this layer).
 * - `onClosing` → invalidate the connection, emit [SocketEvent.Closed] (normal close), complete.
 * - `onFailure` → invalidate the connection, emit [SocketEvent.Closed] with a typed
 *   [ConnectionError.NetworkUnavailable], complete.
 * - `awaitClose` → on scope cancellation, [RpcConnection.close] cancels the underlying socket so
 *   nothing leaks (structured-concurrency cancel propagates to the socket).
 *
 * The OkHttp client carries forward the Phase-1 smoke-test websocket posture: one client for ws+REST,
 * a finite `connectTimeout`, and `readTimeout(0)` (a websocket must NOT be killed by a read timeout).
 */
class MoonrakerSocket(
    private val factory: WebSocketFactory,
    private val request: Request,
) {
    /**
     * Open the socket and stream its lifecycle. Cold: each collection opens a fresh socket; cancelling
     * the collecting scope cancels the socket (via `awaitClose`). The emitted [SocketEvent.Open]
     * connection is invalidated when the flow completes for any reason.
     */
    fun events(): Flow<SocketEvent> = callbackFlow {
        // Connection is created in onOpen and shared with awaitClose for cancel-time invalidation.
        var connection: RpcConnection? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val conn = RpcConnection(webSocket)
                connection = conn
                trySend(SocketEvent.Open(conn))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                trySend(SocketEvent.Frame(text))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                connection?.close()
                trySend(SocketEvent.Closed(cause = null))
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connection?.close(ConnectionError.NetworkUnavailable)
                trySend(SocketEvent.Closed(ConnectionError.NetworkUnavailable))
                // Complete the flow NORMALLY — the transport failure is carried as a typed cause in
                // the SocketEvent.Closed value, NOT as a flow exception. Consumers (the reconnect
                // supervisor in 02-04) observe Closed(cause) and decide; they should not have to
                // wrap collection in try/catch to learn the socket died.
                close()
            }
        }

        factory.open(request, listener)

        awaitClose {
            // Scope cancelled (or flow completed): invalidate the connection, which cancels the
            // underlying socket. No leak.
            connection?.close()
        }
    }

    companion object {
        /** websocket connect timeout (ms) — finite; matches the Phase-1 smoke posture. */
        const val OPEN_TIMEOUT_MS = 10_000L

        /**
         * Build a [MoonrakerSocket] over a REAL OkHttp client (the production path). Carries the
         * smoke-test posture: finite `connectTimeout`, `readTimeout(0)` (no read timeout on a ws).
         * The [client] is reused for REST too (one TLS/pool/timeout config — CLAUDE.md networking).
         *
         * @param wsUrl the websocket URL; defaults to [DevConfig.wsUrl]. A token-bearing URL
         *   (`?token=...`) can be supplied here for the Plan-04 auth variant.
         */
        fun real(
            client: OkHttpClient = defaultClient(),
            wsUrl: String = DevConfig.wsUrl,
        ): MoonrakerSocket =
            MoonrakerSocket(
                factory = { request, listener -> client.newWebSocket(request, listener) },
                request = Request.Builder().url(wsUrl).build(),
            )

        /** The shared OkHttp client posture for ws (and REST): finite connect, no read timeout. */
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // websocket: no read timeout
                .build()
    }
}
