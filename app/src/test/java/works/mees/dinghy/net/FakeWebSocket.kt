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
open class FakeWebSocket(
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

    /**
     * Whether a LIVE `objects.subscribe` registration currently exists on this socket (D-10). The real
     * Moonraker server only PUSHES `notify_status_update` diffs while a subscription is active: after a
     * klippy drop (FIRMWARE_RESTART / SAVE_CONFIG) the subscription is gone, and diffs do NOT resume
     * until the client re-runs `objects.subscribe` and the server accepts it.
     *
     * Starts FALSE (a fresh socket has no subscription). The harness owns the transitions: it sets this
     * TRUE only after it PRODUCES a successful `objects.subscribe` reply, and clears it FALSE the instant
     * the captured klippy-drop signal is injected. The gate lives on [inject] (the single delivery path
     * every frame passes through) so a test cannot smuggle a resumed status diff into the store via a raw
     * `inject(diff)` without a genuine re-subscribe having happened — closing the inject() bypass that let
     * the keystone test pass even when the fix was absent.
     */
    @Volatile var subscriptionActive: Boolean = false

    /** Has [cancel]'s onFailure already fired? Guards the double-fire when cancel() races a close(). */
    @Volatile private var cancelFired: Boolean = false

    // ---- Test driving API ----------------------------------------------------------------------

    /** Drive `onOpen` (socket reached the open state). */
    fun open(response: Response? = null) {
        listener.onOpen(this, response ?: stubResponse())
    }

    /** Replay raw text frames through the real `onMessage` inbound path, in order. */
    fun replay(frames: List<String>) {
        frames.forEach { listener.onMessage(this, it) }
    }

    /**
     * Inject a single frame at a chosen point (mid-flight, for STATE-05 interleaving).
     *
     * D-10 subscription gate: while [subscriptionActive] is false, a `notify_status_update` (status-diff)
     * frame is REFUSED/DROPPED — it never reaches `listener.onMessage`, mirroring the real server, which
     * pushes diffs only while a subscription is registered. Non-status frames (RPC replies, `notify_klippy_*`,
     * klippy-ready, gcode responses) still pass — the gate is specifically on resumed STATUS diffs. This
     * closes the bypass where a raw `inject(diff)` reached the store with no re-subscribe.
     */
    fun inject(frame: String) {
        if (!subscriptionActive && isStatusUpdate(frame)) return
        listener.onMessage(this, frame)
    }

    /** True iff [frame] is a `notify_status_update` push (the diff the subscription gate guards). */
    private fun isStatusUpdate(frame: String): Boolean =
        runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(frame)
                .let { it as? kotlinx.serialization.json.JsonObject }
                ?.get("method")
                ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                ?.content == "notify_status_update"
        }.getOrDefault(false)

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

    /** Subclasses may observe/respond to sends; this base just records the frame. */

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
        // Real OkHttp cancel() surfaces as WebSocketListener.onFailure (hard teardown, no close frame).
        // RpcConnection.close(cause != null) → webSocket.cancel(); without firing onFailure here the
        // self-heal escalation (close(cause) → cancel()) could never produce a SocketEvent.Closed in
        // tests, so the supervisor's reconnect was unobservable (D-10). Fire onFailure exactly once.
        closed = true
        if (!cancelFired) {
            cancelFired = true
            listener.onFailure(this, java.io.IOException("socket cancelled"), null)
        }
    }

    private fun stubResponse(): Response =
        Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(101)
            .message("Switching Protocols")
            .build()
}
