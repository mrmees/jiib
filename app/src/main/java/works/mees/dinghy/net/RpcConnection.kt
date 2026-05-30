package works.mees.dinghy.net

import okhttp3.WebSocket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A concrete "active socket" handle bound to ONE live [okhttp3.WebSocket] (review HIGH #4).
 *
 * This is the only path through which outbound frames may be sent. An instance is constructed
 * exclusively by [MoonrakerSocket] when the underlying socket reaches `onOpen`, and is emitted
 * inside [SocketEvent.Open]. There is no public constructor path that yields a connection without
 * a live socket handle, so **send-before-open is unrepresentable** by construction. When the
 * socket closes / fails (or the collecting scope is cancelled), the connection is [close]d and
 * any further [send] fails cleanly with an [IllegalStateException] — never a silent no-op, never
 * an NPE, never a frame sent into a dead socket.
 *
 * Lifecycle (bound to the live socket):
 * - created after `onOpen` over the live [WebSocket],
 * - [send] forwards to [WebSocket.send] while valid,
 * - [close] cancels the socket and flips the valid flag so subsequent sends fail.
 *
 * Construction is `internal` on purpose: only the `net` transport layer ([MoonrakerSocket]) may
 * mint one, enforcing the "send only within a live socket lifecycle" invariant.
 */
class RpcConnection internal constructor(
    private val webSocket: WebSocket,
) {
    private val open = AtomicBoolean(true)

    /** True while the underlying socket is live and sends are permitted. */
    val isOpen: Boolean get() = open.get()

    /**
     * Forward a text frame to the live socket.
     *
     * @throws IllegalStateException if the connection has been [close]d / invalidated (the socket
     *   is no longer live) — callers get a clean typed failure instead of a silent drop or an NPE.
     */
    fun send(text: String) {
        check(open.get()) { "RpcConnection.send after close: socket is no longer live" }
        // OkHttp's WebSocket.send returns false if the message could not be enqueued (e.g. the
        // socket is shutting down). Surface that as a clean failure too, rather than dropping.
        val enqueued = webSocket.send(text)
        check(enqueued) { "RpcConnection.send failed: frame could not be enqueued (socket closing)" }
    }

    /**
     * Invalidate this connection and cancel the underlying socket. Idempotent. After this returns,
     * [isOpen] is false and any [send] throws. [cause] is advisory (the caller — typically the
     * socket bridge or the JSON-RPC client — uses it to fail pending work with a typed reason).
     */
    fun close(cause: ConnectionError? = null) {
        if (open.compareAndSet(true, false)) {
            // 1000 = normal closure; OkHttp's cancel() is the hard teardown for failure paths.
            webSocket.cancel()
        }
    }
}
