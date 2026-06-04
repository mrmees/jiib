package works.mees.dinghy.ui.webcam

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import works.mees.dinghy.config.ConnectionConfig
import works.mees.dinghy.net.MjpegDecodePolicy
import works.mees.dinghy.net.SnapshotPoller
import works.mees.dinghy.net.WebcamClients
import works.mees.dinghy.net.WebcamProbe
import works.mees.dinghy.net.backoffDelay
import works.mees.dinghy.net.bitmaps
import works.mees.dinghy.net.resolveWebcamUrl
import works.mees.dinghy.net.snapshotBitmaps
import works.mees.dinghy.render.WebcamView
import works.mees.dinghy.state.ResolvedWebcam
import works.mees.dinghy.state.Rung
import works.mees.dinghy.state.Webcam
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The webcam orchestration holder (CAM-01, D-01/D-09/D-10/D-11/D-12/D-13; WR-01) — the toolkit-agnostic
 * [MutableStateFlow] → [StateFlow] holder (the [works.mees.dinghy.ui.move.MoveHolder] skeleton; NO Compose
 * annotations, host-unit-testable) that turns the enumeration + decode services + render surface into a
 * working screen. It owns, for the SELECTED cam:
 *
 *  1. SELECTED-CAM resolution (D-10) — on a cam-list change, pick [WebcamPrefs.preferredCam] if it is
 *     present-and-still-listed, else the FIRST cam in list order. A user [cycleCam]/[selectCam] updates
 *     the selection AND writes [WebcamPrefs.setPreferredCam] (last-viewed).
 *  2. URL resolution (D-09) — `resolveWebcamUrl(stream/snapshot, cfg)` (relative-join + loopback rewrite,
 *     token-preserving).
 *  3. Rung select + the active loop (D-01) — the [feed] runs the cam (probe → MJPEG decode / snapshot
 *     poll / dead-end); its outcome drives the mode.
 *  4. The D-11 RECONNECT STATE MACHINE — on a transient stall/drop the holder enters [WebcamView.Mode.Reconnecting]
 *     (KEEPS the last good frame; the View dims it) and retries via [backoffDelay] (1s→~10s cap)
 *     FOREGROUND-ONLY; a 401/403/WebRTC dead-end is TERMINAL → [WebcamView.Mode.DeadEnd] (no spin, A4).
 *  5. Frame hand-off — the feed pushes drop-behind latest-wins frames into the VM.
 *  6. The [start]/[stop] page-visible lifecycle surface (the shell binds it, plan 10-07) + the WR-01
 *     [cancel] — both fully and IDEMPOTENTLY tear down ALL owned decode/poll/retry coroutines (this
 *     project's frozen-feed-after-restart history demands a complete, leak-free teardown).
 *
 * ## Generic over the frame type [T] (host-testability — the 10-04 decoder discipline)
 * The project has NO Robolectric, so the reconnect/backoff/terminal STATE MACHINE must be provable on the
 * JVM without `Bitmap`. The holder is therefore generic over [T] and drives an injected [WebcamFeed] — the
 * test scripts a feed of transient/terminal outcomes and asserts the resulting [WebcamVm.mode] +
 * last-frame retention + cancellation; production binds `T = Bitmap` via [bitmaps]/[snapshotBitmaps]
 * (see [webcamBitmapHolder]).
 *
 * ## WR-01 leak-cancel (the load-bearing teardown)
 * The holder owns ONE supervised driver [Job] (started by [start]); [stop]/[cancel] cancel it, which
 * structurally cancels the active decode/poll loop and the retry/backoff `delay` inside it (cooperative
 * cancellation — the decoder/poller loops already check `isActive`). [cancel] is idempotent: a second
 * call is a no-op. The shell's `DisposableEffect(holder){ onDispose{ holder.cancel() } }` (plan 10-07)
 * calls it on spine rebuild so a reconnect never leaks a wedged feed that keeps the FGS awake (T-10-09).
 *
 * @param scope the lifecycle scope the driver runs on (the shell supplies a session/page scope).
 * @param webcams the live enumeration ([works.mees.dinghy.di.AppContainer.webcams]); selection re-derives on change.
 * @param webcamPrefs the per-printer preferred-cam store (D-10).
 * @param host the active printer host (the per-printer preferred-cam key + the loopback-rewrite host).
 * @param feed the rung-select + active-loop driver (injected for host-testability; production = [bitmapFeed]).
 * @param backoffRng deterministic RNG for the retry backoff in tests (default real RNG).
 */
class WebcamHolder<T>(
    private val scope: CoroutineScope,
    private val webcams: StateFlow<List<Webcam>>,
    private val webcamPrefs: WebcamPrefs,
    private val host: String,
    private val feed: WebcamFeed<T>,
    private val backoffRng: Random = Random.Default,
) {
    private val _vm = MutableStateFlow(WebcamVm<T>())

    /** The resolved webcam view-model (selected cam, latest frame, mode, multi-cam, cam list). */
    val vm: StateFlow<WebcamVm<T>> = _vm.asStateFlow()

    /** The user-selected cam identity (uid/name), or null = "follow the D-10 default-pick". */
    private val selectedId = MutableStateFlow<String?>(null)

    /** The ONE owned driver job ([start] launches it; [stop]/[cancel] cancel it). WR-01. */
    private var driver: Job? = null

    @Volatile
    private var cancelled = false

    init {
        // Keep the cam LIST + multi-cam flag live in the VM even before [start] (so the Field/cycle
        // overlay render correctly the moment the screen composes). The selected cam resolves in the
        // driver loop (it needs the suspend preferred-cam read).
        scope.launch {
            webcams.collect { cams ->
                _vm.value = _vm.value.copy(cams = cams, multiCam = cams.size > 1)
            }
        }
    }

    /**
     * Start (or restart) the page-visible decode/poll/retry driver (D-13). Idempotent-ish: a second
     * [start] cancels the previous driver first (so a re-entry never doubles loops). The shell calls this
     * when the Webcam page becomes visible+foreground; [stop] when it leaves.
     */
    fun start() {
        if (cancelled) return
        driver?.cancel()
        driver = scope.launch { drive() }
    }

    /**
     * Stop the active loop + retry (page background/nav, D-13). The decode/poll work and any pending
     * backoff `delay` are cancelled with the driver — NO wasted decode/bandwidth while not visible.
     */
    fun stop() {
        driver?.cancel()
        driver = null
    }

    /**
     * The WR-01 leak-cancel: fully + idempotently tear down EVERY owned loop. The shell's
     * `DisposableEffect(holder){ onDispose{ cancel() } }` (plan 10-07) calls this on spine rebuild. After
     * [cancel] the holder is inert ([start] is a no-op) — a reconnect builds a fresh holder.
     */
    fun cancel() {
        cancelled = true
        driver?.cancel()
        driver = null
    }

    /** Select a specific cam by identity (uid/name) and persist it as the last-viewed (D-10). */
    fun selectCam(camId: String) {
        selectedId.value = camId
        scope.launch { webcamPrefs.setPreferredCam(host, camId) }
        start() // re-drive the new selection
    }

    /**
     * Cycle to the NEXT cam in list order (the full-focus in-feed overlay tap, camera_feed note) and
     * persist it (D-10). A no-op when there are <2 cams.
     */
    fun cycleCam() {
        val cams = webcams.value
        if (cams.size < 2) return
        val currentId = _vm.value.selected?.let { camIdOf(it) }
        val idx = cams.indexOfFirst { camIdOf(it) == currentId }
        val next = cams[(idx + 1).mod(cams.size)]
        selectCam(camIdOf(next))
    }

    /**
     * The driver: resolve the selected cam (D-10), then run the feed with the D-11 reconnect/backoff
     * state machine until cancelled or terminal. Re-runs from the top whenever [selectCam] restarts it.
     */
    private suspend fun drive() {
        val cam = resolveSelectedCam() ?: run {
            // No cams at all → nothing to show (the screen renders the empty cam list; mode stays Live
            // with a null frame, which the View letterboxes). Idle here; a cam list change restarts.
            return
        }
        _vm.value = _vm.value.copy(selected = cam.webcam, mode = WebcamView.Mode.Live, frame = null)

        var attempt = 0
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val outcome = feed.run(cam) { frame ->
                // A live frame arrived → publish it, clear any Reconnecting/backoff, reset the cap.
                attempt = 0
                _vm.value = _vm.value.copy(
                    frame = frame,
                    mode = if (cam.rung == Rung.Snapshot) WebcamView.Mode.SnapshotFallback else WebcamView.Mode.Live,
                )
            }
            when (outcome) {
                // The active loop dropped transiently (Wi-Fi hiccup / mid-read stall). D-11: keep the last
                // good frame visible (the VM keeps `frame`) and flip to Reconnecting; back off + retry.
                FeedOutcome.Transient -> {
                    _vm.value = _vm.value.copy(mode = WebcamView.Mode.Reconnecting) // KEEP last frame
                    val wait = backoffDelay(attempt, base = BACKOFF_BASE, rng = backoffRng)
                        .coerceAtMost(MAX_BACKOFF)
                    attempt++
                    delay(wait) // foreground-only: cancelled with the driver on stop()/cancel() (D-12)
                }
                // 401/403/WebRTC/no-snapshot → terminal-for-this-cam. Dead-end card (D-04); STOP, no spin.
                FeedOutcome.Terminal -> {
                    _vm.value = _vm.value.copy(mode = WebcamView.Mode.DeadEnd, frame = null)
                    return
                }
                // The feed ended because the scope was cancelled (stop()/cancel()) — exit cleanly.
                FeedOutcome.Cancelled -> return
            }
        }
    }

    /** Resolve the D-10 selected cam: explicit selection → preferred (if still listed) → first-in-list. */
    private suspend fun resolveSelectedCam(): ResolvedWebcam? {
        val cams = webcams.value
        if (cams.isEmpty()) return null

        val explicit = selectedId.value?.let { id -> cams.firstOrNull { camIdOf(it) == id } }
        val preferredId = webcamPrefs.preferredCam(host).first()
        val preferred = preferredId?.let { id -> cams.firstOrNull { camIdOf(it) == id } }
        val cam = explicit ?: preferred ?: cams.first()

        // The feed resolves the rung itself (probe); the holder hands it the cam + resolved URLs. The
        // rung here is a provisional Snapshot-vs-Live hint (refined by the feed's probe); URLs are the
        // load-bearing part (D-09). Resolution needs the ConnectionConfig — supplied to the feed.
        return ResolvedWebcam(webcam = cam, rung = Rung.Mjpeg)
    }

    private companion object {
        /** Backoff base (D-12 wants ~1s→~10s) — mirrors [SnapshotPoller.BACKOFF_BASE]. */
        val BACKOFF_BASE: Duration = 1.seconds

        /** Backoff CAP (D-12 / T-10-09) — never wait longer than ~10s between retries. */
        val MAX_BACKOFF: Duration = 10.seconds
    }
}

/** The stable per-cam identity (uid/name) used for selection + the preferred-cam key (D-10). */
fun camIdOf(cam: Webcam): String = cam.uid?.takeIf { it.isNotBlank() } ?: cam.name

/**
 * The resolved webcam view-model the screen consumes (mirrors `MoveVm`'s "resolved VM for the screen"
 * role). Generic over the frame type [T] (= `Bitmap` in production; a sentinel in host tests).
 *
 * @param selected the currently-shown cam (null until resolved / when no cams).
 * @param frame the latest decoded frame, or null (no frame yet / hard dead-end). In Reconnecting the
 *   LAST GOOD frame is KEPT here (D-11) — the View dims it rather than blanking.
 * @param mode the chrome mode driving [WebcamView] (Live / SnapshotFallback / Reconnecting / DeadEnd).
 * @param cams the full enumerated cam list (the Field picker + cycle overlay).
 * @param multiCam whether >1 cam exists (the full-focus cycle overlay shows; camera_feed note).
 */
data class WebcamVm<T>(
    val selected: Webcam? = null,
    val frame: T? = null,
    val mode: WebcamView.Mode = WebcamView.Mode.Live,
    val cams: List<Webcam> = emptyList(),
    val multiCam: Boolean = false,
) {
    /** The selected cam's Moonraker name (the cycle-overlay label); "" when none. */
    val camName: String get() = selected?.name ?: ""

    /** The detected service string for the dead-end card body (D-04); "" when none. */
    val serviceName: String get() = selected?.service ?: ""
}

/** Why the active feed loop ended — the input to the D-11 reconnect state machine. */
enum class FeedOutcome {
    /** A transient stall/drop (Wi-Fi hiccup / mid-read EOF) — keep last frame, back off, retry (D-11). */
    Transient,

    /** A terminal failure (401/403 / WebRTC / no usable stream+snapshot) — dead-end card, no spin (A4). */
    Terminal,

    /** The scope was cancelled (page background / nav / spine rebuild) — exit cleanly (D-13/WR-01). */
    Cancelled,
}

/**
 * The rung-select + active-loop driver the holder runs per cam (injected so the holder's reconnect/backoff
 * state machine is host-provable without Android). [run] resolves the rung (probe), drives the active
 * decode/poll loop, pushes each decoded frame to [onFrame], and RETURNS a [FeedOutcome] when the loop ends
 * (a transient drop → retry; a terminal failure → dead-end; cancellation → clean exit). It must honor
 * coroutine cancellation (the holder cancels its driver to stop the feed — WR-01).
 */
fun interface WebcamFeed<T> {
    suspend fun run(cam: ResolvedWebcam, onFrame: (T) -> Unit): FeedOutcome
}

/**
 * The PRODUCTION feed binding `T = Bitmap` (D-01/D-02). Resolves the cam's stream/snapshot URLs (D-09),
 * probes the Content-Type (D-02 via [WebcamProbe]), then runs [bitmaps] (MJPEG) or [snapshotBitmaps]
 * (snapshot poll), handing each decoded [Bitmap] to `onFrame`. A probe/decoder transient maps to
 * [FeedOutcome.Transient]; a 401/403/WebRTC dead-end to [FeedOutcome.Terminal]; cancellation to
 * [FeedOutcome.Cancelled]. The shell threads the shared OkHttp client + [ConnectionConfig] in (plan 10-07).
 *
 * NOTE: this binding wires the already-unit-proven 10-04 services; its own end-to-end behavior is an
 * on-device property (plan 10-08). The holder's STATE MACHINE (the new logic) is proven host-side via an
 * injected scripted [WebcamFeed] in `WebcamReconnectStateTest`.
 */
fun bitmapFeed(
    cfg: ConnectionConfig,
    sharedClient: okhttp3.OkHttpClient,
    viewWidthPx: Int,
    viewHeightPx: Int,
): WebcamFeed<Bitmap> = WebcamFeed { cam, onFrame ->
    val streamClient = WebcamClients.streamClient(sharedClient)
    val snapshotClient = WebcamClients.snapshotClient(sharedClient)
    val resolvedStream = resolveWebcamUrl(cam.webcam.streamUrl, cfg)
    val resolvedSnapshot = resolveWebcamUrl(cam.webcam.snapshotUrl, cfg)

    val probe = WebcamProbe(streamClient).probe(resolvedStream, cam.webcam.hasSnapshot)
    when (probe) {
        is WebcamProbe.ProbeResult.Mjpeg -> {
            val sample = MjpegDecodePolicy.computeInSampleSize(
                // Source size is unknown until the first frame; start at 1 (the decoder re-establishes
                // inBitmap as frames arrive). 10-08 pins the on-device sample step.
                srcWidth = viewWidthPx, srcHeight = viewHeightPx,
                reqWidth = viewWidthPx, reqHeight = viewHeightPx,
            )
            val decoder = bitmaps(probe.boundary, sample)
            // Structured concurrency: the frame collector is a CHILD of decodeStream's scope, so the
            // decoder finishing (or the holder cancelling the driver) tears the collector down too — no
            // orphaned collector (WR-01). The body is closed on exit regardless.
            probe.body.use { body ->
                kotlinx.coroutines.coroutineScope {
                    val collector = launch { decoder.frames.collect { onFrame(it) } }
                    decoder.decodeStream(body.source())
                    collector.cancel()
                }
            }
            // The stream ended (EOF / mid-read stall) WITHOUT cancellation → transient (D-11): the holder
            // keeps the last frame + reconnects; a genuinely dead cam re-probes to Terminal on retry. (A
            // cancelled scope throws CancellationException out of this lambda — never reaches here.)
            FeedOutcome.Transient
        }
        WebcamProbe.ProbeResult.Snapshot -> {
            val url = resolvedSnapshot ?: return@WebcamFeed FeedOutcome.Terminal
            val poller = snapshotBitmaps(snapshotClient, inSampleSize = 1)
            val outcome = kotlinx.coroutines.coroutineScope {
                val collector = launch { poller.frames.collect { onFrame(it) } }
                val r = poller.poll(url)
                collector.cancel()
                r
            }
            when (outcome) {
                SnapshotPoller.PollOutcome.Unsupported -> FeedOutcome.Terminal // terminal 401/403 (A4)
                SnapshotPoller.PollOutcome.Cancelled -> FeedOutcome.Cancelled
            }
        }
        is WebcamProbe.ProbeResult.Unsupported ->
            if (probe.terminal) FeedOutcome.Terminal else FeedOutcome.Transient
    }
}

/**
 * Build the PRODUCTION holder (`T = Bitmap`). A thin convenience for the shell (plan 10-07): it threads
 * the shared OkHttp client + [ConnectionConfig] into [bitmapFeed]. Kept separate from the generic holder
 * so the holder itself stays Android-free + host-testable.
 */
fun webcamBitmapHolder(
    scope: CoroutineScope,
    webcams: StateFlow<List<Webcam>>,
    webcamPrefs: WebcamPrefs,
    cfg: ConnectionConfig,
    sharedClient: okhttp3.OkHttpClient,
    viewWidthPx: Int,
    viewHeightPx: Int,
): WebcamHolder<Bitmap> = WebcamHolder(
    scope = scope,
    webcams = webcams,
    webcamPrefs = webcamPrefs,
    host = cfg.host,
    feed = bitmapFeed(cfg, sharedClient, viewWidthPx, viewHeightPx),
)
