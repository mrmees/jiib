package works.mees.jiib.webcam

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Hardened HTTP test double for the webcam probe/decoder/poller (Phase 10, plan 10-01, Wave 0).
 *
 * This is an [okhttp3.Call.Factory] — the SAME injectable seam `auth/MoonrakerAuth.kt` uses
 * ([works.mees.jiib.auth.MoonrakerAuth] takes a `Call.Factory`) — so the later `WebcamProbe` /
 * `SnapshotPoller` (built in 10-02..10-06) can be driven entirely off canned [Response]s with NO
 * network. It references ONLY okhttp + this test package, so it COMPILES day-one even though no
 * production webcam symbol exists yet (the cross-wave compile rule — see 10-01-PLAN.md).
 *
 * Faithful-mock discipline (10-RESEARCH.md § Pitfall 6 — the project's recurring mock-vs-reality
 * bug class, bitten twice; see STATE.md): this double is DELIBERATELY NOT lenient. It models the
 * exact REAL contract captured live 2026-06-03, because the 401 / 404-text-plain / no-Content-Length
 * / split-JPEG / relative / 127.0.0.1 cases are the whole point of Wave 0 — a too-clean fake would
 * hide on-device breakage behind a green suite.
 *
 * Behaviors it can produce, keyed by request URL (see [responseFor]):
 *  - MJPEG stream: `multipart/x-mixed-replace; boundary=dinghyboundary` 200, body from a `.bin`
 *    fixture (rung-1 path).
 *  - Snapshot 200: `image/jpeg` for the E3 `?token=` snapshot URL (rung-2 path).
 *  - Snapshot 401: `WWW-Authenticate: Basic realm="Ravens Perch"` for the E5 (no-token) snapshot —
 *    the real ravens-perch nginx Basic-Auth reality; MUST be terminal-for-cam, never spin-retried.
 *  - Stream 404 `text/plain`: the mediamtx WebRTC `stream_url` plain-GET reality (rung-3 → fall
 *    through; NEVER `multipart`).
 *  - Relative + `127.0.0.1` request URLs: served so the URL resolver/rewriter test can exercise
 *    rewrite-to-host.
 */
class FakeWebcamHttp(
    /**
     * Optional override responder. When set it takes precedence over the built-in [responseFor]
     * keying, so a single test can inject a bespoke response. Receives the issued [Request].
     */
    private val responder: ((Request) -> Response)? = null,
) : Call.Factory {

    /** The last request actually issued, so a test can assert headers / the resolved URL. */
    @Volatile
    var lastRequest: Request? = null
        private set

    /** Every URL requested, in order — lets a test assert localhost/relative rewrite happened. */
    val requestedUrls: MutableList<String> = mutableListOf()

    override fun newCall(request: Request): Call = object : Call {
        override fun request(): Request = request
        override fun execute(): Response {
            lastRequest = request
            requestedUrls += request.url.toString()
            return (responder ?: ::responseFor)(request)
        }

        override fun enqueue(responseCallback: Callback) = throw UnsupportedOperationException()
        override fun cancel() = Unit
        override fun isExecuted(): Boolean = false
        override fun isCanceled(): Boolean = false
        override fun timeout(): okio.Timeout = okio.Timeout.NONE
        override fun clone(): Call = this
    }

    /** Built-in URL-keyed responder modeling the real per-printer contract (verified live 2026-06-03). */
    private fun responseFor(request: Request): Response {
        val url = request.url.toString()
        return when {
            // mediamtx WebRTC stream_url — 404 text/plain on a plain GET (NOT multipart). Rung-3 fall.
            url.contains(":8889/") ->
                build(request, code = 404, contentType = TEXT_PLAIN, body = "404 page not found")

            // An MJPEG stream endpoint (crowsnest/ustreamer `?action=stream`) → multipart 200.
            url.contains("action=stream") ->
                build(
                    request,
                    code = 200,
                    contentType = "$MULTIPART; boundary=$BOUNDARY",
                    bytes = mjpegFixture("mjpeg_with_content_length.bin"),
                )

            // E5 snapshot (NO token) → 401 Basic Auth (real ravens-perch nginx reality).
            url.endsWith("/cameras/snapshot/3.jpg") || url.endsWith("/cameras/snapshot/2.jpg") ->
                build(
                    request,
                    code = 401,
                    contentType = TEXT_PLAIN,
                    body = "401 Authorization Required",
                    headers = listOf(WWW_AUTHENTICATE to BASIC_REALM),
                )

            // E3 snapshot (WITH ?token=) → 200 image/jpeg.
            url.contains("/cameras/snapshot/") && url.contains("token=") ->
                build(request, code = 200, contentType = JPEG, bytes = TINY_JPEG)

            // A snapshot `?action=snapshot` (crowsnest) → 200 image/jpeg.
            url.contains("action=snapshot") ->
                build(request, code = 200, contentType = JPEG, bytes = TINY_JPEG)

            // Anything else → a generic 200 image/jpeg (a reachable snapshot fallback).
            else -> build(request, code = 200, contentType = JPEG, bytes = TINY_JPEG)
        }
    }

    private fun build(
        request: Request,
        code: Int,
        contentType: String,
        body: String? = null,
        bytes: ByteArray? = null,
        headers: List<Pair<String, String>> = emptyList(),
    ): Response {
        val mediaType = contentType.toMediaType()
        val responseBody: ResponseBody =
            bytes?.toResponseBody(mediaType) ?: (body ?: "").toResponseBody(mediaType)
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(messageFor(code))
            .body(responseBody)
        for ((name, value) in headers) builder.header(name, value)
        return builder.build()
    }

    private fun messageFor(code: Int): String = when (code) {
        200 -> "OK"
        401 -> "Unauthorized"
        404 -> "Not Found"
        else -> "Status $code"
    }

    companion object {
        const val BOUNDARY = "dinghyboundary"
        const val MULTIPART = "multipart/x-mixed-replace"
        const val JPEG = "image/jpeg"
        const val TEXT_PLAIN = "text/plain"
        const val WWW_AUTHENTICATE = "WWW-Authenticate"
        const val BASIC_REALM = "Basic realm=\"Ravens Perch\""

        /** A minimal structurally-valid JPEG (SOI ... EOI) for snapshot 200 bodies. */
        val TINY_JPEG: ByteArray = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), // SOI
            0xFF.toByte(), 0xFE.toByte(), 0x00, 0x04, 0x00, 0x00, // COM segment, len 4, 2 payload bytes
            0xFF.toByte(), 0xD9.toByte(), // EOI
        )

        /** Load one of the synthetic `.bin` MJPEG fixtures from the test classpath. */
        fun mjpegFixture(name: String): ByteArray =
            requireNotNull(FakeWebcamHttp::class.java.getResourceAsStream("/fixtures/$name")) {
                "MJPEG fixture /fixtures/$name not found on the test classpath"
            }.use { it.readBytes() }
    }
}
