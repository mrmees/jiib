package works.mees.jiib.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The TWO derived read-timeout postures the webcam layer needs, both derived off the ONE shared OkHttp
 * client / connection pool / TLS config (CLAUDE.md networking law; 10-RESEARCH Pitfall 4). NEVER mint a
 * second client — `newBuilder()` reuses the parent's pool/dispatcher/TLS, only overriding the timeout.
 *
 * The two postures are OPPOSITE on purpose (Pitfall 4):
 *  - [streamClient] — `readTimeout(0)`: an MJPEG `multipart/x-mixed-replace` body legitimately holds the
 *    connection open between frames; a read timeout would kill the stream mid-feed. (Same posture as the
 *    websocket — see [MoonrakerSocket.defaultClient].)
 *  - [snapshotClient] — a FINITE read timeout: a wedged snapshot GET MUST time out (→ transient → backoff)
 *    rather than hang the poller forever (the rung-2 self-DoS guard, T-10-09).
 *
 * The webcam services ([WebcamProbe], [MjpegStreamDecoder], [SnapshotPoller]) take an `okhttp3.Call.Factory`
 * (an [OkHttpClient] IS one), so the holder (plan 10-06) threads the right posture in; this keeps the
 * posture choice auditable in ONE place.
 */
object WebcamClients {

    /**
     * The stream/probe posture: `readTimeout(0)` derived off [shared]. Used for the [WebcamProbe] GET and
     * the [MjpegStreamDecoder] body (a stream holds the connection open — no read timeout).
     */
    fun streamClient(shared: OkHttpClient): OkHttpClient =
        shared.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // stream: hold the connection open, no read timeout
            .build()

    /**
     * The snapshot posture: a FINITE read timeout ([SnapshotPoller.SNAPSHOT_READ_TIMEOUT], ~5s) derived
     * off [shared]. A wedged snapshot GET times out → the poller backs off, never hangs (Pitfall 4).
     */
    fun snapshotClient(shared: OkHttpClient): OkHttpClient =
        shared.newBuilder()
            .readTimeout(SnapshotPoller.SNAPSHOT_READ_TIMEOUT.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .build()
}
