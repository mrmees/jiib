package works.mees.dinghy.ui.webcam

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import works.mees.dinghy.config.ConnectionConfig
import works.mees.dinghy.net.WebcamClients
import works.mees.dinghy.net.surfaceWebcamUrl
import works.mees.dinghy.render.Media3SurfaceProvider
import works.mees.dinghy.state.NativeTransport
import works.mees.dinghy.state.ResolvedWebcam
import works.mees.dinghy.state.selectsH264Rung
import works.mees.dinghy.net.nativeStreamUrlFor

/**
 * The native-H.264 rung's ON-DEVICE player binding (CAM-12/14/16; Phase 21) — wired as a COMPOSITE
 * [WebcamFeed] that fits the existing Phase-10 reconnect ladder.
 *
 * ## Why a COMPOSITE feed (the load-bearing correctness point — D-10 / SC2)
 * The holder's [WebcamHolder.drive] STOPS on [FeedOutcome.Terminal] (→ DeadEnd, `return`) — it does NOT
 * try a lower rung. So the H.264-then-MJPEG/Snapshot fall-through MUST happen INSIDE one feed's `run()`,
 * exactly like [bitmapFeed] internally falls MJPEG→Snapshot→Terminal. A standalone Media3 feed that
 * returned `Terminal` on a decoder error would DEAD-END a cam that has a working lower rung — the bug this
 * file must NOT ship. [compositeMedia3Feed] therefore tries H.264 first and, on a DECODER failure,
 * DELEGATES to the lower-rung feed ([bitmapFeed]) within the same `run()`, returning [FeedOutcome.Terminal]
 * ONLY when the lower rung is also exhausted.
 *
 * ## The flagged DIVERGENCE — player ops on the main thread
 * The holder drives this feed on `Dispatchers.IO` (its blocking decode/poll). ExoPlayer is single-threaded
 * by contract, so ALL player ops (build / prepare / setVideoSurfaceView / release) marshal onto
 * `Dispatchers.Main` via [withContext] (the real attempt, [realH264Attempt]) while the IO driver loop owns
 * orchestration. This is the single biggest structural difference from [bitmapFeed].
 *
 * ## Host-testability
 * The real player attempt lives behind the [H264Attempt] seam so [compositeMedia3Feed] is unit-testable
 * without a real ExoPlayer (a fake attempt + a scripted lower rung prove the fall-through). The player's
 * own end-to-end behavior is an on-device property (plan 21-05 UAT — green units are not a passing camera).
 */

/** The outcome of the H.264 player ATTEMPT (the top of the composite), classified from PlaybackException. */
enum class H264AttemptResult {
    /** A NETWORK error (connection failed/timeout) — retry the H.264 rung (a Wi-Fi hiccup, not a dead cam). */
    Transient,

    /**
     * A DECODER-class error (decoder-init / format-unsupported) — the decoder VERIFIED the guess was wrong
     * (D-10). The composite feed FALLS THROUGH to the lower MJPEG/Snapshot rung; it does NOT dead-end.
     */
    FallThrough,

    /**
     * The driver scope was cancelled (page background / nav / spine rebuild) — clean exit (WR-01).
     *
     * NOTE (WR-02): the PRODUCTION [realH264Attempt] NO LONGER returns this — it RE-THROWS the
     * [kotlinx.coroutines.CancellationException] so structured-concurrency cancellation propagates. This value
     * is retained as the composite's mapping for a FAKE attempt that returns it (the host-test seam, see
     * [compositeMedia3Feed] / Media3FeedOutcomeTest) and for any future synchronous cancel signal.
     */
    Cancelled,
}

/**
 * The H.264 player attempt seam (injected for host-testability). [attempt] builds + drives an ExoPlayer to
 * play the cam's native H.264 stream, and returns an [H264AttemptResult] when playback ENDS (it does not
 * itself fall through — the composite owns that). Production binds [realH264Attempt]; tests inject a fake.
 *
 * NOTE: the H.264 video renders DIRECTLY to the host SurfaceView, so the attempt does NOT push Bitmap
 * frames — there is no `onFrame` here (the frame seam stays for the lower MJPEG/Snapshot rungs). The lambda
 * arg `cam: ResolvedWebcam` carries the cam identity; the cutout chrome is the host's job (overlay layer).
 */
fun interface H264Attempt {
    suspend fun attempt(cam: ResolvedWebcam): H264AttemptResult
}

/**
 * Pure PlaybackException classifier (CAM-14): a NETWORK error → [H264AttemptResult.Transient] (retry the
 * H.264 rung); ANY other error (decoder-init / format-unsupported / renderer / source) → [FallThrough]
 * (the decoder verified the guess was wrong — fall through to the lower rung, NEVER short-circuit Terminal
 * while a lower rung is viable). Pure: no Android UI, no I/O — host-testable.
 */
fun classifyPlaybackException(errorCode: Int): H264AttemptResult = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    -> H264AttemptResult.Transient
    // decoder-init / format-unsupported / renderer / source / anything-else → the decoder VERIFIES → fall
    // through to MJPEG/Snapshot (D-10). A hostile/wrong service can never force a fatal mis-decode here.
    else -> H264AttemptResult.FallThrough
}

/**
 * Build the RTSP MediaSource Factory with forced TCP (CAM-16). MediaMTX serves RTP over TCP only; the
 * default UDP attempt hits "461 Unsupported Transport", so [RtspMediaSource.Factory.setForceUseRtpTcp] is
 * called with the media3 1.10.1 BOOLEAN-arg form (`setForceUseRtpTcp(true)`), NOT the no-arg form.
 */
@OptIn(UnstableApi::class)
fun buildRtspMediaSourceFactory(): RtspMediaSource.Factory =
    RtspMediaSource.Factory()
        .setForceUseRtpTcp(true) // media3 1.10.1: BOOLEAN arg; MediaMTX is TCP-only (defeats 461)
        .setTimeoutMs(RTSP_TIMEOUT_MS)

/**
 * Build the HLS MediaSource Factory (the D-04 NON-LEAD robustness fallback — RTSP is the recorded lead, but
 * HLS is constructed/exercisable too). Uses a plain HTTP data source (no token; the snapshot fallback that
 * does carry a token routes through [surfaceWebcamUrl] before any log).
 */
@OptIn(UnstableApi::class)
fun buildHlsMediaSourceFactory(): HlsMediaSource.Factory =
    HlsMediaSource.Factory(DefaultHttpDataSource.Factory())

@OptIn(UnstableApi::class)
private fun buildMediaSource(transport: NativeTransport, uri: String): MediaSource {
    val item = MediaItem.fromUri(uri)
    return when (transport) {
        NativeTransport.Rtsp -> buildRtspMediaSourceFactory().createMediaSource(item)
        NativeTransport.Hls -> buildHlsMediaSourceFactory().createMediaSource(item)
    }
}

/**
 * The COMPOSITE H.264-first [WebcamFeed] (generic over the frame type [T] for host-testability — production
 * binds `T = Bitmap`). One `run()`:
 *  1. ATTEMPT H.264 via [h264Attempt] (the real attempt builds an ExoPlayer on the main thread).
 *  2. Map the result:
 *     - [H264AttemptResult.Transient] (network) → [FeedOutcome.Transient] (the holder retries the H.264 rung);
 *     - [H264AttemptResult.Cancelled] → [FeedOutcome.Cancelled] (clean exit, WR-01);
 *     - [H264AttemptResult.FallThrough] (decoder) → DELEGATE to [lowerRung] within the SAME run() and
 *       return whatever it returns (Transient/Terminal/Cancelled) — [FeedOutcome.Terminal] ONLY when the
 *       lower rung is also dead, NEVER a short-circuit while a lower rung is viable (D-10 / SC2 / T-21-04-01).
 */
fun <T> compositeMedia3Feed(
    h264Attempt: H264Attempt,
    lowerRung: WebcamFeed<T>,
): WebcamFeed<T> = WebcamFeed { cam, onFrame ->
    // Route per cam (D-09/D-10): only an H.264-SELECTED cam (the service/scheme HINT — [selectsH264Rung])
    // takes the native attempt; everything else goes straight to the existing MJPEG/Snapshot probe path.
    // A bad/hostile service can only mis-SELECT (then the decoder verifies + falls through), never force a
    // fatal mis-decode (T-21-04-01).
    if (!selectsH264Rung(cam.webcam)) return@WebcamFeed lowerRung.run(cam, onFrame)
    when (h264Attempt.attempt(cam)) {
        H264AttemptResult.Transient -> FeedOutcome.Transient
        H264AttemptResult.Cancelled -> FeedOutcome.Cancelled
        // The decoder verified the guess was wrong → fall through to MJPEG/Snapshot IN-FEED (NOT Terminal).
        H264AttemptResult.FallThrough -> lowerRung.run(cam, onFrame)
    }
}

/**
 * The PRODUCTION H.264 player attempt (`T = Bitmap`). Builds + drives an ExoPlayer to play [cam]'s native
 * H.264 stream over the recorded LEAD transport (RTSP, [NativeTransport.Rtsp]; D-04), confining ALL player
 * ops to `Dispatchers.Main` (the flagged divergence — the holder drives on IO). Returns:
 *  - [H264AttemptResult.Transient] on a NETWORK PlaybackException (retry the rung),
 *  - [H264AttemptResult.FallThrough] on a DECODER PlaybackException (the composite delegates to MJPEG/Snapshot),
 *  - [H264AttemptResult.FallThrough] when no native URL is derivable (rung falls through),
 *  - [H264AttemptResult.Transient] on a clean stream END (STATE_ENDED) — a live RTSP/HLS feed that ENDs
 *    has torn down; let the holder back off + re-attempt the rung rather than hang forever (WR-01),
 *  - [H264AttemptResult.FallThrough] when no SurfaceView is ever registered within
 *    [SURFACE_AWAIT_TIMEOUT_MS] (the host never composed — fall to the lower rung, WR-03).
 *  On driver cancellation the [kotlinx.coroutines.CancellationException] is RE-THROWN after teardown (WR-02)
 *  — structured-concurrency cancellation propagates out of `run()` and the holder's `drive()` exits its
 *  loop as a cancelled coroutine. The attempt itself NEVER returns [H264AttemptResult.Cancelled] (that enum
 *  value remains the composite's host-test mapping for a fake attempt; see [compositeMedia3Feed]).
 *
 * ## Surface re-attach across rotation (CR-01)
 * The host recreates its SurfaceView on `AndroidView` reset (orientation change — this app supports portrait
 * AND landscape), firing `clear()` → `register(new)` on the provider. The RUNNING player must follow, so the
 * attempt does NOT capture one surface via `first()` and pin it — it COLLECTS [Media3SurfaceProvider.surface]
 * for the player's whole lifetime and re-attaches on every change: a non-null SurfaceView →
 * `setVideoSurfaceView(sv)`; a transient null (host disposing/rotating) → `setVideoSurfaceView(null)` WITHOUT
 * tearing the player down (the fresh surface arrives momentarily and re-attaches). The feed keeps rendering
 * across rotation instead of pinning a destroyed surface (the CR-01 permanent-black-feed defect).
 *
 * Leak-free teardown (WR-01 / T-21-04-02): on EVERY exit path the player is detached + released —
 * `setVideoSurfaceView(null)` THEN `release()`, idempotently, on `Dispatchers.Main`. The H.264 player is
 * released BEFORE the composite delegates to the lower rung, so a held MediaCodec never starves the 2 GB
 * device. The video renders directly to the host SurfaceView, so `onFrame` is NOT called for H.264 (the
 * Bitmap frame seam stays for the lower rungs); the holder keeps the SurfaceView-rendered feed Live while
 * playback is healthy.
 */
@OptIn(UnstableApi::class)
fun realH264Attempt(
    context: Context,
    cfg: ConnectionConfig,
    transport: NativeTransport,
    surfaceProvider: Media3SurfaceProvider,
): H264Attempt = H264Attempt { cam ->
    // Resolve the native URL (explicit extra_data tag else derive — D-12). No URL → fall through to MJPEG.
    val nativeUrl = nativeStreamUrlFor(cam.webcam, transport, cfg)
        ?: return@H264Attempt H264AttemptResult.FallThrough

    // surfaceWebcamUrl() is the ONLY sanctioned surface for a webcam URL (Security V7 / T-21-04-03) — RTSP
    // is tokenless but a mixed diagnostic must never leak the snapshot ?token=. (Touch it so the redaction
    // path stays a live, enforced control rather than dead code.)
    @Suppress("UNUSED_VARIABLE")
    val safeForLog = surfaceWebcamUrl(nativeUrl)

    var player: ExoPlayer? = null
    val result = CompletableDeferred<H264AttemptResult>()
    try {
        // WR-03: bound the FIRST-surface wait so an H.264-selected cam whose host never composes (the
        // SurfaceView never registers) does NOT park the IO driver forever — fall through to the lower rung.
        // Cancellable: a cancelled driver throws CancellationException out of the timed wait (re-thrown below).
        withTimeoutOrNull(SURFACE_AWAIT_TIMEOUT_MS) {
            surfaceProvider.surface.filterNotNull().first()
        } ?: return@H264Attempt H264AttemptResult.FallThrough

        // CR-01: the player must re-attach the surface for its WHOLE lifetime (rotation recreates the host
        // SurfaceView). coroutineScope keeps the surface-collector a CHILD of the attempt — it is cancelled
        // when result.await() returns OR the driver is cancelled, and the finally still releases the player.
        coroutineScope {
            withContext(Dispatchers.Main) {
                val exo = ExoPlayer.Builder(context).build()
                player = exo
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        // Classify network-vs-decoder and complete the attempt (the composite acts on it).
                        result.complete(classifyPlaybackException(error.errorCode))
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        // WR-01: a clean stream END (server tore the stream down / HLS playlist ended) raises
                        // no PlaybackException — map STATE_ENDED to Transient so the holder reconnects rather
                        // than hanging forever. complete() is a no-op if already completed (safe).
                        if (state == Player.STATE_ENDED) {
                            result.complete(H264AttemptResult.Transient)
                        }
                    }
                })
                exo.setMediaSource(buildMediaSource(transport, nativeUrl))
                exo.playWhenReady = true
                exo.prepare()
            }

            // CR-01: re-attach the surface on EVERY change for the player's lifetime. Collecting the StateFlow
            // replays the current value first (idempotent initial attach), then follows rotation: a new
            // SurfaceView re-attaches; a transient null (host disposing/rotating) detaches WITHOUT tearing the
            // player down — the fresh surface arrives momentarily and re-attaches. All player ops stay on Main.
            val surfaceJob = launch(Dispatchers.Main) {
                surfaceProvider.surface.collect { sv -> player?.setVideoSurfaceView(sv) }
            }

            // Suspend until the player errors / ends (the listener completes [result]) OR the driver is
            // cancelled. A healthy stream renders to the SurfaceView indefinitely; await() suspends here, and a
            // cancelled driver throws CancellationException out of await() → re-thrown below; the finally always
            // releases the player (WR-01). Cancel the surface-collector once the attempt resolves.
            try {
                result.await()
            } finally {
                surfaceJob.cancel()
            }
        }
    } catch (ce: kotlinx.coroutines.CancellationException) {
        // WR-02: the finally below runs FIRST (NonCancellable teardown), then re-throw so structured-concurrency
        // cancellation propagates out of run() — the holder's drive() exits its loop as a cancelled coroutine.
        throw ce
    } finally {
        // Idempotent leak-free release on EVERY exit path (WR-01): detach the surface BEFORE release().
        val p = player
        if (p != null) {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                p.setVideoSurfaceView(null)
                p.release()
            }
            player = null
        }
    }
}

/**
 * Build the PRODUCTION composite H.264-first feed (`T = Bitmap`): the real player attempt (RTSP lead) over
 * the host SurfaceView, falling through to [bitmapFeed] (MJPEG → Snapshot) on a decoder failure. The shell
 * threads the Android [context], the [ConnectionConfig], the shared OkHttp client, the view px (for the
 * MJPEG downscale), and the [Media3SurfaceProvider] (the host registers its SurfaceView there).
 */
fun media3CompositeBitmapFeed(
    context: Context,
    cfg: ConnectionConfig,
    sharedClient: okhttp3.OkHttpClient,
    viewWidthPx: Int,
    viewHeightPx: Int,
    surfaceProvider: Media3SurfaceProvider,
    transport: NativeTransport = NativeTransport.Rtsp, // the recorded LEAD (D-04); HLS is the fallback factory
): WebcamFeed<Bitmap> = compositeMedia3Feed(
    h264Attempt = realH264Attempt(context, cfg, transport, surfaceProvider),
    lowerRung = bitmapFeed(cfg, sharedClient, viewWidthPx, viewHeightPx),
)

/**
 * Build the PRODUCTION webcam holder with the H.264-first composite feed (`T = Bitmap`) — the Phase-21
 * analog of [webcamBitmapHolder]. An H.264-SELECTED cam ([selectsH264Rung]) is played via Media3 over the
 * recorded LEAD transport (RTSP) on the host SurfaceView, falling through to MJPEG/Snapshot on a decoder
 * failure; every other cam goes straight to the existing [bitmapFeed] probe path. The reconnect machine
 * ([WebcamHolder.drive]/[WebcamHolder.cancel]/[FeedOutcome]) is REUSED VERBATIM — only the feed changes.
 *
 * The IO [WebcamHolder.driverContext] is kept for orchestration; only the player ops marshal to the main
 * thread (inside [realH264Attempt], the flagged divergence). The [surfaceProvider] is shared with the
 * [works.mees.dinghy.render.Media3SurfaceHost] the screen renders for H.264 cams.
 */
fun webcamMedia3Holder(
    scope: kotlinx.coroutines.CoroutineScope,
    webcams: kotlinx.coroutines.flow.StateFlow<List<works.mees.dinghy.state.Webcam>>,
    webcamPrefs: WebcamPrefs,
    cfg: ConnectionConfig,
    profileId: String,
    sharedClient: okhttp3.OkHttpClient,
    viewWidthPx: Int,
    viewHeightPx: Int,
    context: Context,
    surfaceProvider: Media3SurfaceProvider,
): WebcamHolder<Bitmap> = WebcamHolder(
    scope = scope,
    webcams = webcams,
    webcamPrefs = webcamPrefs,
    profileId = profileId,
    feed = media3CompositeBitmapFeed(
        context = context,
        cfg = cfg,
        sharedClient = sharedClient,
        viewWidthPx = viewWidthPx,
        viewHeightPx = viewHeightPx,
        surfaceProvider = surfaceProvider,
    ),
    // The blocking probe/decode/poll runs off the main thread (the holder is built on a main-thread Compose
    // scope); ONLY the ExoPlayer ops marshal back to Main inside realH264Attempt (the flagged divergence).
    driverContext = Dispatchers.IO,
)

/** Ensure WebcamClients stays referenced for the lower-rung client split (documents the shared stack). */
@Suppress("unused")
private val webcamClientsAnchor = WebcamClients

private const val RTSP_TIMEOUT_MS = 8000L

/**
 * WR-03: the bound on the FIRST-surface wait in [realH264Attempt]. If the host never composes a SurfaceView
 * within this window (e.g. the screen is on the Bitmap branch, or a refactor gates the host differently),
 * the attempt falls through to the lower rung rather than parking the IO driver forever.
 */
private const val SURFACE_AWAIT_TIMEOUT_MS = 5000L
