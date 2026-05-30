package works.mees.dinghy.net

import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * A test fake implementing OkHttp's [WebSocket] interface so golden + adversarial frames replay
 * through the EXACT inbound path the real socket uses (`WebSocketListener.onMessage`), with no
 * network. The real spine (Wave 2) must inject its socket-opening factory so this can be
 * substituted (02-PATTERNS § "Testable seam").
 *
 * Capabilities:
 * - [sentFrames] captures every [send] call (tests assert what was REQUESTED, incl. outbound RPC).
 * - [open] drives `onOpen`; [replay] feeds frames through `onMessage` in order; [inject] feeds a
 *   single frame mid-sequence (for the STATE-05 interleaving fixture).
 * - [simulateClosing]/[simulateFailure] drive `onClosing`/`onFailure` so Wave-2/3 close-cleanup and
 *   reconnect tests can drive a socket death.
 *
 * Driving is synchronous (the caller decides ordering) — combine with `runTest` virtual time in
 * Wave 2/3 reducer/correlation tests.
 */
class FakeWebSocket(
    private val listener: WebSocketListener,
    private val request: Request = Request.Builder().url("http://localhost/websocket").build(),
) : WebSocket {

    /** Every text frame the code-under-test sent (e.g. the identify / subscribe RPC, `?token=` aside). */
    val sentFrames: MutableList<String> = mutableListOf()

    /** Byte frames sent (Moonraker is text-only, but the interface requires it). */
    val sentBytes: MutableList<okio.ByteString> = mutableListOf()

    @Volatile var closed: Boolean = false
        private set
    @Volatile var closeCode: Int? = null
        private set
    @Volatile var closeReason: String? = null
        private set

    // ---- Test driving API ----------------------------------------------------------------------

    /** Drive `onOpen` (socket reached the open state). */
    fun open(response: Response? = null) {
        listener.onOpen(this, response ?: stubResponse())
    }

    /** Replay raw text frames through the real `onMessage` inbound path, in order. */
    fun replay(frames: List<String>) {
        frames.forEach { listener.onMessage(this, it) }
    }

    /** Inject a single frame at a chosen point (mid-flight, for STATE-05 interleaving). */
    fun inject(frame: String) {
        listener.onMessage(this, frame)
    }

    /** Drive `onClosing` (server initiated, or our close echoed). */
    fun simulateClosing(code: Int = 1000, reason: String = "closing") {
        listener.onClosing(this, code, reason)
    }

    /** Drive `onFailure` (socket died — transport error). */
    fun simulateFailure(t: Throwable, response: Response? = null) {
        listener.onFailure(this, t, response)
    }

    // ---- okhttp3.WebSocket interface ------------------------------------------------------------

    override fun request(): Request = request

    override fun queueSize(): Long = 0L

    override fun send(text: String): Boolean {
        if (closed) return false
        sentFrames += text
        return true
    }

    override fun send(bytes: okio.ByteString): Boolean {
        if (closed) return false
        sentBytes += bytes
        return true
    }

    override fun close(code: Int, reason: String?): Boolean {
        if (closed) return false
        closed = true
        closeCode = code
        closeReason = reason
        listener.onClosed(this, code, reason ?: "")
        return true
    }

    override fun cancel() {
        closed = true
    }

    private fun stubResponse(): Response =
        Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(101)
            .message("Switching Protocols")
            .build()
}
