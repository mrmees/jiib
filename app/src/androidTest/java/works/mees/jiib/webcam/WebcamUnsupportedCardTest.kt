package works.mees.jiib.webcam

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import works.mees.jiib.render.WebcamView
import works.mees.jiib.state.ResolvedWebcam
import works.mees.jiib.state.Webcam
import works.mees.jiib.theme.ThemeResolver
import works.mees.jiib.theme.compose.JiibTheme
import works.mees.jiib.ui.webcam.FeedOutcome
import works.mees.jiib.ui.webcam.WebcamFeed
import works.mees.jiib.ui.webcam.WebcamHolder
import works.mees.jiib.ui.webcam.WebcamScreen

/**
 * Instrumented proof of SC-4 / D-04 (plan 10-07): a `webrtc-mediamtx` cam (the E5/E3 reality) drives the
 * holder to the TERMINAL dead-end, the screen renders the rung-3 dead-end card, and there is **NO
 * open-in-browser button** anywhere (the design law: the card is informational only — D-04, T-10-12).
 *
 * The dead-end card itself is painted on the classic-View [WebcamView] `Canvas` (`drawDeadEndCard`), so
 * its TITLE/BODY text is intentionally NOT in the Compose semantics tree — the service-name-in-card
 * formatting is a unit property of `WebcamView`. What the instrumented layer proves is the SCREEN-level
 * contract: the holder reaches [WebcamView.Mode.DeadEnd] for a WebRTC cam (host-observable via the VM)
 * AND the rendered Webcam screen exposes no clickable browser/open affordance — only the red Back gutter.
 * A scripted [WebcamFeed] returns [FeedOutcome.Terminal] so no live network is touched.
 */
@RunWith(AndroidJUnit4::class)
class WebcamUnsupportedCardTest {

    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private val testScope = CoroutineScope(SupervisorJob())

    /** Minimal in-memory DataStore — the holder only reads/writes the per-printer preferred-cam key. */
    private class FakeDataStore : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
        override val data = kotlinx.coroutines.flow.flowOf(
            androidx.datastore.preferences.core.emptyPreferences(),
        )
        override suspend fun updateData(
            transform: suspend (t: androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences,
        ) = androidx.datastore.preferences.core.emptyPreferences()
    }

    /** A feed that immediately returns Terminal — the 401/403/WebRTC dead-end (no spin, A4). */
    private val terminalFeed = WebcamFeed<Bitmap> { _, _ -> FeedOutcome.Terminal }

    private val webrtcCam = Webcam(
        name = "ravens-perch",
        service = "webrtc-mediamtx",
        streamUrl = "/webrtc",
        uid = "ravens-perch",
    )

    private fun newHolder(): WebcamHolder<Bitmap> =
        WebcamHolder(
            scope = testScope,
            webcams = MutableStateFlow(listOf(webrtcCam)),
            webcamPrefs = works.mees.jiib.ui.webcam.WebcamPrefs(FakeDataStore()),
            profileId = "10.255.255.1",
            feed = terminalFeed,
        )

    @After
    fun tearDown() {
        testScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    // A WebRTC cam → DeadEnd mode (no spin) AND the rendered screen has no browser button — only Back.
    @Test
    fun webrtcCam_goesDeadEnd_screenHasNoBrowserButton() {
        val holder = newHolder()
        val resolver = ThemeResolver()
        composeRule.setContent {
            JiibTheme(resolver) {
                WebcamScreen(holder = holder, surfaceProvider = works.mees.jiib.render.Media3SurfaceProvider(), onBack = {})
            }
        }

        // The page's DisposableEffect started the driver; the terminal feed flips the VM to DeadEnd
        // EXACTLY once and stops (no spin-retry, A4 / D-04) — host-observable on the holder's VM.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            holder.vm.value.mode == WebcamView.Mode.DeadEnd
        }
        assertEquals(WebcamView.Mode.DeadEnd, holder.vm.value.mode)
        composeRule.waitForIdle()

        // The dead-end card is informational only (D-04): the only clickable affordance on the whole
        // Webcam surface is the red Back gutter tile — there is NO open-in-browser button. Assert no
        // node carries browser/open text, and that Back IS present (the rung-3 card has no escape hatch).
        composeRule.onNodeWithText("Back").assertIsDisplayed()
        assertEquals(
            "no open-in-browser affordance exists on the dead-end Webcam screen (D-04)",
            0,
            composeRule.onAllNodes(hasText("Open in browser", substring = true)).fetchSemanticsNodes().size,
        )
        assertEquals(
            "no 'Open' clickable affordance exists on the dead-end Webcam screen (D-04)",
            0,
            composeRule.onAllNodes(hasText("Open", substring = true).and(hasClickAction()))
                .fetchSemanticsNodes().size,
        )
    }
}
