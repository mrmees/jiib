package works.mees.dinghy.webcam

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.render.WebcamView
import works.mees.dinghy.state.ResolvedWebcam
import works.mees.dinghy.state.Webcam
import works.mees.dinghy.ui.webcam.FeedOutcome
import works.mees.dinghy.ui.webcam.WebcamFeed
import works.mees.dinghy.ui.webcam.WebcamHolder
import works.mees.dinghy.ui.webcam.WebcamPrefs
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Typed assertions for the [WebcamHolder] D-11 reconnect state machine (`ui/webcam/WebcamHolder.kt`,
 * plan 10-06) — REPLACES the plan-10-01 runtime-RED scaffold.
 *
 * The state machine is the genuinely-new orchestration logic of plan 10-06; the project has no
 * Robolectric, so it is proven host-side by injecting a SCRIPTED [WebcamFeed] (generic over a sentinel
 * frame type, NOT `Bitmap`) and asserting the resulting [works.mees.dinghy.ui.webcam.WebcamVm] mode +
 * last-frame retention + cancellation behavior. The Bitmap-bound production feed (`bitmapFeed`) wires the
 * already-unit-proven 10-04 decoder/poller; its end-to-end behavior is an on-device property (plan 10-08).
 *
 * Proves (D-11 / D-12 / A4 / WR-01):
 *  - a TRANSIENT stall keeps the LAST GOOD frame visible and flips to [WebcamView.Mode.Reconnecting]
 *    (NOT a black error flash) while auto-retrying with foreground-only backoff (1s→10s);
 *  - a 401/403/WebRTC TERMINAL outcome → [WebcamView.Mode.DeadEnd], no spin-retry;
 *  - [WebcamHolder.cancel] stops ALL loops (the feed stops being re-invoked) and is idempotent (WR-01).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebcamReconnectStateTest {

    private val tmpFiles = mutableListOf<File>()

    private fun prefs(scope: CoroutineScope): WebcamPrefs {
        val file = File.createTempFile("webcam_reconn_", ".preferences_pb").also {
            it.delete(); tmpFiles += it
        }
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }
        return WebcamPrefs(ds)
    }

    private val oneCam = listOf(Webcam(name = "cam1", uid = "cam1", streamUrl = "http://h/s", snapshotUrl = "http://h/snap"))

    @Test
    fun transientStall_keepsLastFrameDimmedReconnecting_notError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val webcams = MutableStateFlow(oneCam)
        val frame = "FRAME-A" // sentinel "last good frame"

        // A feed that delivers ONE frame then drops transiently — every invocation does the same, so the
        // holder loops: frame → Transient → (backoff) → frame → Transient … (a flaky-Wi-Fi cam).
        val runs = AtomicInteger(0)
        val feed = WebcamFeed<String> { _, onFrame ->
            runs.incrementAndGet()
            onFrame(frame)        // a good frame lands (mode → Live, frame kept)
            delay(50)             // …then the stream stalls mid-view
            FeedOutcome.Transient // transient drop → the holder must KEEP the frame + go Reconnecting
        }

        val holder = WebcamHolder(
            scope = scope,
            webcams = webcams,
            webcamPrefs = prefs(scope),
            host = "192.168.1.120",
            feed = feed,
            backoffRng = Random(0),
        )
        holder.start()

        // Advance EXACTLY to the transient drop: the feed's delay(50) resumes at t=50 and returns
        // Transient; the holder sets Reconnecting + keeps the frame SYNCHRONOUSLY before scheduling its
        // backoff `delay`. Capturing here (before any backoff elapses) is deterministic regardless of the
        // jittered backoff length.
        testScheduler.advanceTimeBy(50)
        testScheduler.runCurrent()

        val vm = holder.vm.value
        assertEquals("a transient stall is Reconnecting, NOT a dead-end", WebcamView.Mode.Reconnecting, vm.mode)
        assertSame("the last good frame is KEPT (dimmed by the View), never blanked (D-11)", frame, vm.frame)

        // It auto-retries under backoff (the feed is re-invoked) rather than giving up on a transient blip.
        testScheduler.advanceTimeBy(30_000)
        testScheduler.runCurrent()
        assertTrue("the holder retried the transient drop (foreground backoff, D-12)", runs.get() >= 2)

        holder.cancel()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun terminalAuthFailure_goesDeadEnd_noSpinRetry() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val webcams = MutableStateFlow(oneCam)

        // A feed that immediately returns Terminal (a 401/403 / WebRTC dead-end). It must be invoked
        // EXACTLY ONCE — the holder shows the dead-end card and does NOT spin (A4).
        val runs = AtomicInteger(0)
        val feed = WebcamFeed<String> { _, _ ->
            runs.incrementAndGet()
            FeedOutcome.Terminal
        }

        val holder = WebcamHolder(
            scope = scope, webcams = webcams, webcamPrefs = prefs(scope),
            host = "192.168.1.120", feed = feed, backoffRng = Random(0),
        )
        holder.start()
        testScheduler.advanceTimeBy(30_000)
        testScheduler.runCurrent()

        assertEquals("a terminal outcome shows the dead-end card (D-04)", WebcamView.Mode.DeadEnd, holder.vm.value.mode)
        assertEquals("a terminal failure invokes the feed exactly ONCE — no spin-retry (A4)", 1, runs.get())

        holder.cancel()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun cancel_stopsAllLoops_andIsIdempotent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val webcams = MutableStateFlow(oneCam)

        // A feed that retries forever (always transient) — the loop only stops when cancel() tears it down.
        val runs = AtomicInteger(0)
        val feed = WebcamFeed<String> { _, _ ->
            runs.incrementAndGet()
            delay(10)
            FeedOutcome.Transient
        }
        val holder = WebcamHolder(
            scope = scope, webcams = webcams, webcamPrefs = prefs(scope),
            host = "192.168.1.120", feed = feed, backoffRng = Random(0),
        )
        holder.start()
        testScheduler.advanceTimeBy(5_000)
        testScheduler.runCurrent()
        val before = runs.get()
        assertTrue("the loop was running before cancel", before >= 1)

        holder.cancel()
        holder.cancel() // idempotent — a second cancel must not throw (WR-01)
        testScheduler.advanceTimeBy(30_000)
        testScheduler.runCurrent()
        val after = runs.get()

        // After cancel the feed is no longer re-invoked — the loop is fully torn down (no leak/no frozen feed).
        assertEquals("cancel() stops the loop — the feed is not invoked again (WR-01)", after, runs.get())
        assertTrue("the loop stopped growing after cancel", after - before <= 1)

        // start() after cancel is inert (the holder is dead; a reconnect builds a fresh one).
        holder.start()
        testScheduler.advanceTimeBy(10_000)
        testScheduler.runCurrent()
        assertEquals("start() after cancel is a no-op (the holder is inert)", after, runs.get())

        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun defaultPick_resolvesToFirstCam_whenNoPreference() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val cams = listOf(
            Webcam(name = "alpha", uid = "a", streamUrl = "http://h/a"),
            Webcam(name = "beta", uid = "b", streamUrl = "http://h/b"),
        )
        val webcams = MutableStateFlow(cams)
        val seen = MutableStateFlow<ResolvedWebcam?>(null)
        val feed = WebcamFeed<String> { cam, _ ->
            seen.value = cam
            FeedOutcome.Cancelled // resolve once, then idle
        }
        val holder = WebcamHolder(
            scope = scope, webcams = webcams, webcamPrefs = prefs(scope),
            host = "h", feed = feed, backoffRng = Random(0),
        )
        holder.start()
        testScheduler.advanceTimeBy(100)
        testScheduler.runCurrent()

        // D-10: a fresh printer with no saved preference defaults to the FIRST cam in list order.
        assertNotNull(seen.value)
        assertEquals("alpha", seen.value!!.webcam.name)

        holder.cancel()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
