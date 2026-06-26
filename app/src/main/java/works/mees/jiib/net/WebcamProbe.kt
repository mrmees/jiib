package works.mees.jiib.net

import okhttp3.Call
import okhttp3.Request
import okhttp3.ResponseBody
import works.mees.jiib.state.Rung
import works.mees.jiib.state.rungFor
import java.io.IOException
// surfaceWebcamUrl: the ONE sanctioned redacted-URL surface (CR-02 / V7) — same package, no explicit import needed.

/**
 * The D-02 Content-Type probe + rung selector (CAM-01, Pitfall 1/3/4; Security V7).
 *
 * Issues a **GET** (NOT HEAD — some MJPEG servers ignore HEAD, 10-RESEARCH § Pattern 1) of the resolved
 * `stream_url` through an INJECTABLE [okhttp3.Call.Factory] — the SAME seam
 * [works.mees.jiib.auth.MoonrakerAuth] uses (production passes the shared OkHttp client derived with a
 * `readTimeout(0)` stream posture; tests pass a fake `Call.Factory`). It reads the response `Content-Type`
 * and folds it through the pure [rungFor] selector — **the HTTP Content-Type is the sole decode authority
 * (D-02); the `/server/webcams/list` `service` field is never consulted here.**
 *
 * The result is a [ProbeResult]:
 *  - [ProbeResult.Mjpeg] — a usable `multipart/x-mixed-replace` response; the body is **kept OPEN** (the
 *    caller hands it straight to [MjpegStreamDecoder]) and the `boundary=` token is parsed out here.
 *  - [ProbeResult.Snapshot] — not MJPEG-decodable, but a snapshot is the cam's rung-2 fallback; the probe
 *    body is closed.
 *  - [ProbeResult.Unsupported] — neither a decodable stream nor a usable snapshot. A **401/403 is
 *    terminal-for-this-cam (A4 / Pitfall 3)** — the probe returns Unsupported, it does NOT throw and does
 *    NOT signal "retry"; spinning a 401 self-DoSes the weak SBC host. The body is closed.
 *
 * NEVER throws a raw exception: an [IOException] (host down / refused / mid-read failure) folds to a
 * transient-but-non-decodable [ProbeResult.Unsupported] (the holder treats it via foreground backoff, not
 * a crash). SECURITY V7 / T-10-05: a resolved URL embeds a `?token=` (the E3 snapshot) — any URL that
 * reaches a surfaced message MUST pass through [redactWebcamUrl]; this class logs nothing in the clear.
 */
class WebcamProbe(
    private val callFactory: Call.Factory,
) {

    /**
     * The outcome of probing a cam's stream URL. [Mjpeg] carries the still-OPEN body + parsed boundary;
     * [Snapshot]/[Unsupported] carry no body (the probe closed it). [terminal] marks an outcome that the
     * holder must NOT spin-retry (a 401/403 won't fix itself — A4).
     */
    sealed interface ProbeResult {
        /** The selected rung for this probe outcome (mirrors the D-01 rung the holder routes on). */
        val rung: Rung

        /** True when this outcome is terminal-for-this-cam (no foreground spin-retry — A4/Pitfall 3). */
        val terminal: Boolean

        /**
         * Rung 1: a usable `multipart/x-mixed-replace` body kept OPEN for [MjpegStreamDecoder], plus the
         * parsed [boundary] token (no surrounding `--`, no quotes). The CALLER owns closing [body].
         */
        data class Mjpeg(val body: ResponseBody, val boundary: String) : ProbeResult {
            override val rung: Rung get() = Rung.Mjpeg
            override val terminal: Boolean get() = false
        }

        /** Rung 2: not MJPEG, fall to the snapshot poller (the body was already closed by the probe). */
        data object Snapshot : ProbeResult {
            override val rung: Rung get() = Rung.Snapshot
            override val terminal: Boolean get() = false
        }

        /**
         * Rung 3: no usable stream AND no usable snapshot (WebRTC-only / 401/403/404-no-snapshot /
         * unreachable). [terminal] is true for a definitive auth/HTTP rejection (A4) — the holder shows
         * the dead-end card and does not spin; it is false for a transient transport failure the holder
         * may retry under foreground backoff.
         *
         * [reason] is a PRE-REDACTED diagnostic breadcrumb (CR-02 / Security V7 / T-10-05): it is the
         * ONLY field on a [ProbeResult] that may carry any trace of the probed URL, and it is built
         * EXCLUSIVELY through [surfaceWebcamUrl], so the `?token=` is already stripped before it can reach
         * any log/crash/error surface. Empty when there is nothing safe to surface. This keeps the V7
         * redactor a LIVE, enforced control instead of dead code a future log line would bypass.
         */
        data class Unsupported(
            override val terminal: Boolean,
            val reason: String = "",
        ) : ProbeResult {
            override val rung: Rung get() = Rung.Unsupported
        }
    }

    /**
     * GET the [resolvedStreamUrl], read the Content-Type, and select the rung (D-02).
     *
     * @param resolvedStreamUrl the already-D-09-resolved absolute stream URL (host-rewritten,
     *   token-preserving). May be null/blank when the cam has no stream_url at all.
     * @param snapshotUrlPresent whether the cam carries a non-blank `snapshot_url` (its rung-2 fallback).
     * @return a [ProbeResult]; [ProbeResult.Mjpeg] carries the still-open body the caller must consume+close.
     */
    fun probe(resolvedStreamUrl: String?, snapshotUrlPresent: Boolean): ProbeResult {
        // No stream URL to probe at all → rung selection is purely "do we have a snapshot?".
        if (resolvedStreamUrl.isNullOrBlank()) {
            return if (snapshotUrlPresent) ProbeResult.Snapshot else ProbeResult.Unsupported(terminal = true)
        }

        val request = Request.Builder().url(resolvedStreamUrl).get().build()

        val response = try {
            callFactory.newCall(request).execute()
        } catch (e: IOException) {
            // Transport failure (DNS/host down/refused/mid-read) — typed, NEVER an escaping IOException.
            // Transient: if a snapshot exists the cam still has a rung-2 fallback; otherwise a
            // NON-terminal Unsupported the holder may retry under foreground backoff (not a 401 spin).
            // The diagnostic breadcrumb routes the URL through [surfaceWebcamUrl] (CR-02 / V7 / T-10-05),
            // so the `?token=` is stripped before this reason can ever reach a log/crash surface.
            return if (snapshotUrlPresent) {
                ProbeResult.Snapshot
            } else {
                ProbeResult.Unsupported(terminal = false, reason = surfaceWebcamUrl(resolvedStreamUrl))
            }
        }

        // Content-Type authority (D-02): prefer the response header; fall back to the body's media type
        // when the header is absent (some servers/stacks surface it only on the body). Same string on
        // the wire — both forms carry `multipart/x-mixed-replace; boundary=…`.
        val contentType = response.header("Content-Type") ?: response.body?.contentType()?.toString()
        val code = response.code

        return when (rungFor(contentType, code, snapshotUrlPresent)) {
            Rung.Mjpeg -> {
                val body = response.body
                val boundary = parseBoundary(contentType)
                if (body != null && boundary.isNotBlank()) {
                    // KEEP the body open — the caller streams it through MjpegStreamDecoder.
                    ProbeResult.Mjpeg(body, boundary)
                } else {
                    // multipart Content-Type but no body / no boundary token → not decodable. Close + fall.
                    response.close()
                    if (snapshotUrlPresent) ProbeResult.Snapshot else ProbeResult.Unsupported(terminal = true)
                }
            }

            Rung.Snapshot -> {
                response.close()
                ProbeResult.Snapshot
            }

            Rung.Unsupported -> {
                // A 401/403/404 (or any non-decodable, no-snapshot response) is terminal-for-this-cam —
                // do NOT spin-retry (A4 / Pitfall 3). Close the probe body; show the dead-end card.
                response.close()
                ProbeResult.Unsupported(terminal = true)
            }

            // UNREACHABLE by construction: the Content-Type byte probe (`rungFor`) is service-BLIND and
            // NEVER emits Rung.H264 (D-10 / T-10-06 — H.264 is selected up front by `selectsH264Rung`,
            // not by this GET probe). The branch exists only to satisfy `when` exhaustiveness now that
            // Rung.H264 is a member; if it were ever hit it falls through defensively like Unsupported.
            Rung.H264 -> {
                response.close()
                ProbeResult.Unsupported(terminal = true)
            }
        }
    }

    private companion object {
        /**
         * Pull the `boundary=` token out of a `multipart/x-mixed-replace; boundary=dinghyboundary`
         * Content-Type, stripping surrounding quotes and any trailing params. Returns "" when absent.
         */
        fun parseBoundary(contentType: String?): String =
            contentType.orEmpty()
                .substringAfter("boundary=", "")
                .substringBefore(';')
                .trim()
                .trim('"')
    }
}
