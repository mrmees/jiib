package works.mees.dinghy.net

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import works.mees.dinghy.config.ConnectionConfig

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
 * [works.mees.dinghy.auth.MoonrakerAuth.redactWsUrl] so a resolved URL is NEVER surfaced unredacted —
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
    val base = cfg.httpBase.toHttpUrlOrNull() ?: cfg.httpBase.toHttpUrl()
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
 * surfaced/logged (Security V7 / T-10-05). Mirrors [works.mees.dinghy.auth.MoonrakerAuth.redactWsUrl]
 * exactly — the E3 snapshot URL embeds a token; the resolved URL must NEVER reach a log line in the clear.
 */
fun redactWebcamUrl(url: String): String =
    url.replace(Regex("([?&]token=)[^&]*"), "$1<redacted>")
