package works.mees.jiib.webcam

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import works.mees.jiib.state.ResolvedWebcam
import works.mees.jiib.state.Webcam
import works.mees.jiib.theme.ThemeResolver
import works.mees.jiib.theme.compose.DinghyTheme
import works.mees.jiib.ui.webcam.FeedOutcome
import works.mees.jiib.ui.webcam.WebcamFeed
import works.mees.jiib.ui.webcam.WebcamHolder
import works.mees.jiib.ui.webcam.WebcamScreen
import java.util.concurrent.atomic.AtomicInteger

/**
 * Instrumented proof of SC-3 / D-13 (plan 10-07): the holder's decode/poll/retry driver runs ONLY while
 * the Webcam page is composed (visible) and STOPS on nav-away (decompose) — no work off-page.
 *
 * The seam is the page-level [WebcamScreen] `DisposableEffect(holder){ start(); onDispose{ stop() } }`
 * (the shell layers the spine-rebuild `cancel()` on top — proven separately by the holder's host unit
 * `WebcamReconnectStateTest`). A scripted [WebcamFeed] makes the loop's active/idle state observable: it
 * increments a counter when the loop ENTERS and suspends on [awaitCancellation], so a started driver
 * leaves the feed "active" and a stopped driver tears it down (the loop's `finally` decrements). Driving
 * the screen in and out of composition asserts the start-on-visible / stop-on-nav-away contract WITHOUT a
 * live network (no Bitmap is ever produced; the feed only proves the loop lifecycle).
 */
@RunWith(AndroidJUnit4::class)
class WebcamLifecycleTest {

    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private val testScope = CoroutineScope(SupervisorJob())

    /** A feed that records loop entry/exit so the page-visible lifecycle is host-observable. */
    private class LoopProbeFeed : WebcamFeed<Bitmap> {
        val starts = AtomicInteger(0)
        val active = AtomicInteger(0)
        override suspend fun run(cam: ResolvedWebcam, onFrame: (Bitmap) -> Unit): FeedOutcome {
            starts.incrementAndGet()
            active.incrementAndGet()
            try {
                awaitCancellation() // hold the loop "running" until the driver is cancelled (stop/cancel)
            } finally {
                active.decrementAndGet()
            }
            return FeedOutcome.Cancelled
        }
    }

    /** Minimal in-memory DataStore — the holder only reads/writes the per-printer preferred-cam key. */
    private class FakeDataStore : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
        override val data = kotlinx.coroutines.flow.flowOf(
            androidx.datastore.preferences.core.emptyPreferences(),
        )
        override suspend fun updateData(
            transform: suspend (t: androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences,
        ) = androidx.datastore.preferences.core.emptyPreferences()
    }

    private fun newHolder(feed: WebcamFeed<Bitmap>): WebcamHolder<Bitmap> =
        WebcamHolder(
            scope = testScope,
            webcams = MutableStateFlow(listOf(Webcam(name = "cam1", uid = "cam1"))),
            webcamPrefs = works.mees.jiib.ui.webcam.WebcamPrefs(FakeDataStore()),
            profileId = "10.255.255.1",
            feed = feed,
        )

    @After
    fun tearDown() {
        testScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    // The driver starts when the page is composed (visible) and stops when it leaves composition (nav-away).
    @Test
    fun driver_startsOnVisible_stopsOnNavAway() {
        val feed = LoopProbeFeed()
        val holder = newHolder(feed)
        val resolver = ThemeResolver()

        var pageVisible by mutableStateOf(true)
        composeRule.setContent {
            DinghyTheme(resolver) {
                if (pageVisible) {
                    WebcamScreen(holder = holder, surfaceProvider = works.mees.jiib.render.Media3SurfaceProvider(), onBack = {})
                }
            }
        }

        // Visible → the page's DisposableEffect started the driver; the feed loop is active.
        composeRule.waitUntil(timeoutMillis = 5_000) { feed.active.get() == 1 }
        assertEquals("loop active while page is visible", 1, feed.active.get())

        // Nav away (decompose the page) → onDispose stops the driver → the feed loop tears down.
        pageVisible = false
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) { feed.active.get() == 0 }
        assertEquals("loop stopped after nav-away (no off-page work)", 0, feed.active.get())
        assertTrue("the driver did start at least once while visible", feed.starts.get() >= 1)
    }
}
