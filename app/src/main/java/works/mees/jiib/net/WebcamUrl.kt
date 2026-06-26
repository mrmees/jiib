package works.mees.jiib.net

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import works.mees.jiib.config.ConnectionConfig
import works.mees.jiib.state.NativeTransport
import works.mees.jiib.state.Webcam

/**
 * The D-09 webcam URL resolver/rewriter — the ONE genuinely hand-rolled networking helper of Phase 10
 * (CAM-01, Pitfall 1; Security V7).
 *
 * Moonraker returns `stream_url`/`snapshot_url` exactly as the printer host configured them, written
 * from the HOST's frame of reference. The two real-world breakages this kills (the #1 "works in
 * Mainsail, blank here" gotcha):
 *  - **Relative** URLs (`/webcam2/?action=stream`) — no host; must resolve against the configured
 *    Moonraker host.
 *  - **Host-local** URLs (`http://127.0.0.1:8080/…`, `http://localhost/…`) — `127.0.0.1`/`localhost`
 *    is the TABLET, not the printer; the host must be rewritten to the configured Moonraker host,
 *    preserving scheme/port/path AND the `?token=…` query (the E3 snapshot needs its token — verified live).
 *
 * `okhttp3.HttpUrl.resolve()` does the relative join correctly (do NOT string-concat); the localhost
 * rewrite is the small bit we hand-roll. [redactWebcamUrl] mirrors
 * [works.mees.jiib.auth.MoonrakerAuth.redactWsUrl] so a resolved URL is NEVER surfaced unredacted —
 * the only form any log/crash surface may show (Security V7 / T-10-05).
 *
 * Pure — no Android, no logging side effects inside the resolver.
 */

/** Hosts that, from the tablet's frame of reference, mean "this device" and must be rewritten (D-09). */
private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1")

/**
 * Resolve a raw `stream_url`/`snapshot_url` to an absolute URL on the configured Moonraker host (D-09):
 *  (a) join a relative [raw] against [ConnectionConfig.httpBase] via `HttpUrl.resolve` (correct relative join);
 *  (b) if the resolved host is a loopback ([LOOPBACK_HOSTS]) rewrite the host to [ConnectionConfig.host],
 *      PRESERVING scheme/port/path/query (so `?token=…` survives);
 *  (c) return the absolute URL string, or `null` on an un-parseable/blank input (the caller treats
 *      `null` as no usable URL → rung-3).
 */
fun resolveWebcamUrl(raw: String?, cfg: ConnectionConfig): String? {
    if (raw.isNullOrBlank()) return null

    // (a) Join: HttpUrl.resolve handles BOTH an already-absolute raw and a relative path against the base.
    //     An UNPARSEABLE base (e.g. an empty configured host → httpBase = "http://:7125") yields no usable
    //     URL → return null per this function's contract (c). Do NOT fall back to a throwing toHttpUrl() here:
    //     that crashed the webcam screen with IllegalArgumentException "Invalid URL host: \"\"" on Dispatchers.IO.
    val base = cfg.httpBase.toHttpUrlOrNull() ?: return null
    val resolved = base.resolve(raw.trim()) ?: return null

    // (b) Loopback rewrite — swap only the host; newBuilder() preserves scheme/port/path/query (?token=).
    if (resolved.host in LOOPBACK_HOSTS) {
        return resolved.newBuilder()
            .host(cfg.host)
            .build()
            .toString()
    }

    // (c) Already on a real host — pass through unchanged (token preserved).
    return resolved.toString()
}

/**
 * Strip a `?token=…`/`&token=…` value to `<redacted>` so a resolved webcam URL can be safely
 * surfaced/logged (Security V7 / T-10-05). Mirrors [works.mees.jiib.auth.MoonrakerAuth.redactWsUrl]
 * exactly — the E3 snapshot URL embeds a token; the resolved URL must NEVER reach a log line in the clear.
 */
fun redactWebcamUrl(url: String): String =
    url.replace(Regex("([?&]token=)[^&]*"), "$1<redacted>")

/**
 * The ONE sanctioned way for ANY webcam URL to reach a surfaced/diagnostic string (Security V7 / T-10-05,
 * CR-02). EVERY diagnostic that wants to mention a resolved URL — a probe/poll failure reason, a future
 * `Log.*`, a thrown message, a crash breadcrumb — MUST route the URL through here so the `?token=` is
 * ALWAYS stripped. This makes [redactWebcamUrl] a LIVE, enforced control rather than dead code that the
 * next added log line would silently bypass: there is no second path from a raw URL to a surfaced string.
 *
 * Pass `null`/blank (no URL) → "" (nothing to surface). Otherwise → the redacted URL.
 */
fun surfaceWebcamUrl(url: String?): String =
    if (url.isNullOrBlank()) "" else redactWebcamUrl(url)

/** MediaMTX native-transport ports, VERIFIED against ravens-perch `stream_manager.py` (CAM-13). */
private const val PORT_RTSP = 8554
private const val PORT_HLS = 8888

/**
 * ravens-perch advertises explicit per-transport stream URLs under a NESTED `extra_data` object (the
 * schema shipped by ravens-perch's `build_stream_extra_data()` — feat: send/advertise stream extra data):
 *
 *     "extra_data": { "ravens_perch": { "schema_version": 1, "camera_id": "3", "path": "3",
 *         "streams": { "rtsp": { "url": "rtsp://host:8554/3", "protocol": "rtsp" },
 *                      "hls":  { "url": "http://host:8888/3/", "protocol": "hls" }, … } } }
 *
 * These URLs are ABSOLUTE and authoritative — reading them needs NO [ConnectionConfig] and bypasses the
 * derive (which fails when `stream_url` moved to the WebRTC `:8889` port AND when `cfg.httpBase` is blank).
 * The transport→proto key map and the nested path `ravens_perch.streams.<proto>.url`.
 *
 * (Historical note: a prior build looked for FLAT keys `ravens_perch_native_rtsp` that ravens-perch never
 * emitted — the explicit read was effectively dead, so every cam fell to the cfg-dependent derive. This
 * reads the real nested contract; the derive remains the fallback for non-ravens-perch cams.)
 */
private fun NativeTransport.protoKey(): String = when (this) {
    NativeTransport.Rtsp -> "rtsp"
    NativeTransport.Hls -> "hls"
}

/** Read `extra_data.ravens_perch.streams.<proto>.url` (absolute, cfg-free). Tolerant: any missing/garbled node → null. */
private fun explicitNativeStreamUrl(cam: Webcam, transport: NativeTransport): String? =
    runCatching {
        cam.extraData["ravens_perch"]?.jsonObject
            ?.get("streams")?.jsonObject
            ?.get(transport.protoKey())?.jsonObject
            ?.get("url")?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }

/**
 * Derive a Media3-playable native-transport URL from a cam's WebRTC/HTTP `stream_url` by swapping ONLY
 * the port (and, for RTSP, the scheme) while KEEPING the path segment parsed OUT of [webrtcStreamUrl]
 * (CAM-13, D-11/D-12 derive-from-convention).
 *
 * ⚠ THE LANDMINE (RESEARCH-flagged, highest-leverage correctness item): the path comes from
 * `stream_url`, NEVER from the webcam `name`. Moonraker's `name` = `friendly_name.replace(' ','_').lower()`
 * but the MediaMTX `path` (the last segment of `stream_url`) = `camera_id.replace(' ','_').lower()` — they
 * are DIFFERENT identifiers; reconstructing `:8554/<name>` would 404/timeout. We parse the existing
 * `stream_url` with [okhttp3.HttpUrl] (NEVER string-concat the host/port) and reuse its `encodedPath`.
 *
 * Transform (ports VERIFIED against ravens-perch `stream_manager.py`):
 *  - [NativeTransport.Rtsp] → `rtsp://<host>:8554/<path>` (scheme→rtsp, port :8889→:8554, trailing slash dropped)
 *  - [NativeTransport.Hls]  → `http://<host>:8888/<path>/` (http kept, port :8889→:8888, trailing slash kept)
 *
 * Fail-safe (Security V5): a null/blank/un-parseable [webrtcStreamUrl], or one whose path is blank,
 * returns `null` → the caller treats `null` as "no native URL" → the rung falls through. NEVER throws.
 *
 * Pure — no Android, no I/O, no logging side effects. [webrtcStreamUrl] is expected to ALREADY be the
 * absolute, loopback-rewritten URL (route it through [resolveWebcamUrl] first — see [nativeStreamUrlFor]).
 */
fun deriveNativeStreamUrl(webrtcStreamUrl: String?, transport: NativeTransport): String? {
    val url = webrtcStreamUrl?.trim()?.toHttpUrlOrNull() ?: return null
    val path = url.encodedPath.trim('/') // the single MediaMTX path segment from stream_url, never `name`
    if (path.isBlank()) return null
    return when (transport) {
        NativeTransport.Rtsp -> "rtsp://${url.host}:$PORT_RTSP/$path"
        // R7 (26.5-07 codex review): preserve the SOURCE url's scheme instead of hardcoding
        // http — MediaMTX serves cleartext HLS on the LAN today (so behavior is unchanged for
        // every current stream_url), but a future https stream_url derives https HLS instead of
        // being silently downgraded. Camera hosts are NOT governed by the Moonraker useSecure
        // toggle; the stream_url's own scheme is the authority here.
        NativeTransport.Hls -> "${url.scheme}://${url.host}:$PORT_HLS/$path/"
    }
}

/**
 * Resolve the native-transport ([transport]) stream URL for [cam] — the D-12 "explicit-if-present, else
 * derive-from-convention" policy:
 *  1. EXPLICIT (prefer-if-present, D-12/D-14): read an explicit native URL out of `cam.extra_data`
 *     ([EXTRA_NATIVE_RTSP] / [EXTRA_NATIVE_HLS]). Tolerant — a missing key, a non-string value, or a
 *     blank value falls through to the derive (never throws, never fabricates). DORMANT today (ravens-perch
 *     ships no tag yet), so this is the layered enhancement that activates once the tag exists.
 *  2. DERIVE (fallback, the ONLY path that works today): resolve `cam.stream_url` to its absolute,
 *     loopback-rewritten form via [resolveWebcamUrl] (preserving the D-09 host rewrite), then
 *     [deriveNativeStreamUrl] the port/scheme swap from that resolved URL.
 *
 * Returns `null` when neither path yields a URL (rung falls through). Pure (the resolve/derive are pure).
 */
fun nativeStreamUrlFor(cam: Webcam, transport: NativeTransport, cfg: ConnectionConfig): String? {
    // (1) Explicit ravens-perch nested URL — absolute + cfg-free; prefer if present.
    explicitNativeStreamUrl(cam, transport)?.let { return it }

    // (2) Derive from the resolved (absolute, loopback-rewritten) stream_url (legacy / non-ravens-perch cams).
    val resolved = resolveWebcamUrl(cam.streamUrl, cfg) ?: return null
    return deriveNativeStreamUrl(resolved, transport)
}
