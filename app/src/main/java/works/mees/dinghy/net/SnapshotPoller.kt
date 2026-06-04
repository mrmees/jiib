package works.mees.dinghy.net

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import okhttp3.Call
import okhttp3.Request
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The ~2fps snapshot-poll fallback (CAM-01 rung 2; D-07; Pitfall 3/4; Security V7) — REPLACES the
 * MJPEG stream when the cam isn't `multipart/x-mixed-replace` (the PRIMARY on-device path for this
 * project's ravens-perch/mediamtx printers; see 10-RESEARCH Pitfall 2).
 *
 * A loop of finite-timeout GETs of the resolved `snapshot_url` at ~[POLL_INTERVAL] (~2fps, D-07),
 * decoding each `200 image/jpeg` body into a frame handed off DROP-BEHIND (same conflated latest-wins
 * seam as [MjpegStreamDecoder]). Decode is parameterised over an injectable [decode] so the cadence /
 * backoff / terminal-401 logic is host-testable WITHOUT Android `BitmapFactory` (tests inject a recorder;
 * production passes [snapshotBitmaps]).
 *
 * READ-TIMEOUT POSTURE (Pitfall 4): the production client MUST use a FINITE read timeout
 * ([SNAPSHOT_READ_TIMEOUT], ~5s) derived off the ONE shared pool — NEVER the `readTimeout(0)` stream
 * posture. A wedged snapshot GET must TIME OUT (→ transient → backoff), not hang the poller forever.
 *
 * TERMINAL 401/403 (A4 / Pitfall 3 / T-10-10): an auth rejection is terminal-for-this-cam — the poller
 * STOPS and surfaces [PollOutcome.Unsupported]; it does NOT spin-retry (a 401 won't fix itself, and
 * spinning it self-DoSes the weak SBC host — the E5 ravens-perch Basic-Auth reality drives the rung-3 card).
 *
 * BACKOFF (D-12 / T-10-09): a transient IOException/timeout/5xx retries via [backoffDelay] capped to
 * [MAX_BACKOFF] (~10s), foreground-only (the caller cancels the scope on background/nav — plan 10-06/07).
 *
 * SECURITY V7 / T-10-05: the E3 snapshot URL embeds a `?token=` — this class logs nothing in the clear;
 * any URL that reaches a surface MUST pass through [redactWebcamUrl].
 */
class SnapshotPoller<T>(
    private val callFactory: Call.Factory,
    private val decode: (jpeg: ByteArray) -> T?,
    private val pollInterval: Duration = POLL_INTERVAL,
    private val rng: Random = Random.Default,
) {

    private val channel = Channel<T>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Drop-behind frame stream (conflated latest-wins) — a slow consumer never backs up the poller. */
    val frames: Flow<T> = channel.receiveAsFlow()

    /** Why the poll loop ended. [Cancelled] = scope cancelled (background/nav); [Unsupported] = terminal 401/403. */
    enum class PollOutcome { Cancelled, Unsupported }

    /**
     * Poll [resolvedSnapshotUrl] at ~2fps until the scope is cancelled (→ [PollOutcome.Cancelled]) or a
     * 401/403 makes the cam terminal (→ [PollOutcome.Unsupported]). Closes the frame channel on exit.
     * Transient failures back off ([backoffDelay], capped) and retry; a successful GET resets the backoff.
     */
    suspend fun poll(resolvedSnapshotUrl: String): PollOutcome {
        var attempt = 0
        try {
            while (currentCoroutineContext().isActive) {
                when (val r = fetchOne(resolvedSnapshotUrl)) {
                    is Fetch.Frame -> {
                        channel.trySend(r.frame) // drop-behind: never suspends, newest wins
                        attempt = 0              // success resets backoff
                        delay(pollInterval)      // ~500ms → ~2fps (D-07)
                    }
                    Fetch.Terminal -> return PollOutcome.Unsupported // 401/403 — STOP, no spin (A4)
                    Fetch.Transient -> {
                        // Wedged/slow/5xx → backoff (1s→~10s cap, foreground-only), then retry.
                        val wait = backoffDelay(attempt, base = BACKOFF_BASE, rng = rng)
                            .coerceAtMost(MAX_BACKOFF)
                        attempt++
                        delay(wait)
                    }
                }
            }
        } finally {
            channel.close()
        }
        return PollOutcome.Cancelled
    }

    private sealed interface Fetch<out T> {
        data class Frame<out T>(val frame: T) : Fetch<T>
        data object Terminal : Fetch<Nothing>   // 401/403 — terminal-for-cam
        data object Transient : Fetch<Nothing>  // timeout / IOException / 5xx — retry with backoff
    }

    private fun fetchOne(url: String): Fetch<T> {
        val request = Request.Builder().url(url).get().build()
        return try {
            callFactory.newCall(request).execute().use { resp ->
                when {
                    resp.code == 401 || resp.code == 403 -> Fetch.Terminal // A4 — never spin
                    !resp.isSuccessful -> Fetch.Transient                  // 5xx/404 → retry
                    else -> {
                        val bytes = resp.body?.bytes()
                        val frame = if (bytes != null && bytes.isNotEmpty()) decode(bytes) else null
                        if (frame != null) Fetch.Frame(frame) else Fetch.Transient
                    }
                }
            }
        } catch (e: java.io.IOException) {
            // Timeout (the FINITE read timeout fired) or transport failure → transient, retry with backoff.
            Fetch.Transient
        }
    }

    companion object {
        /** ~2fps poll cadence (D-07) — marginally snappier than ravens-perch's own ~1/sec refresh. */
        val POLL_INTERVAL: Duration = 500.milliseconds

        /** FINITE snapshot read timeout (Pitfall 4) — a wedged GET must time out, not hang the poller. */
        val SNAPSHOT_READ_TIMEOUT: Duration = 5.seconds

        /** Backoff base for the transient-retry side (D-12 wants ~1s→~10s). */
        val BACKOFF_BASE: Duration = 1.seconds

        /** Backoff CAP (D-12 / T-10-09) — never wait longer than ~10s between retries. */
        val MAX_BACKOFF: Duration = 10.seconds
    }
}

/**
 * Build a production [SnapshotPoller] that decodes each snapshot JPEG into a downsampled [Bitmap] (D-06).
 * Uses a fresh [BitmapFactory.Options] per snapshot (the snapshot path is ~2fps and request-per-image —
 * far below the MJPEG hot loop — so the one-reused-bitmap discipline isn't load-bearing here; correctness
 * over micro-optimisation). [inSampleSize] downsamples hard to the view px (no native-res OOM, T-10-02).
 */
fun snapshotBitmaps(
    callFactory: Call.Factory,
    inSampleSize: Int,
): SnapshotPoller<Bitmap> = SnapshotPoller(
    callFactory = callFactory,
    decode = { jpeg ->
        val options = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize.coerceAtLeast(1)
            inPreferredConfig =
                if (MjpegDecodePolicy.PREFER_RGB_565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        runCatching { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options) }.getOrNull()
    },
)
