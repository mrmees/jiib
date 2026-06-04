package works.mees.dinghy.webcam

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import works.mees.dinghy.DinghyApp
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.config.ConnectionConfig
import works.mees.dinghy.di.SpineHandle
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.KlippyState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore
import works.mees.dinghy.state.Webcam
import works.mees.dinghy.theme.compose.DinghyTheme
import works.mees.dinghy.ui.files.FileBrowserClient
import works.mees.dinghy.ui.shell.RootController

/**
 * Instrumented proof of D-08 (plan 10-07): the App Drawer's **Webcam** tile is RUNTIME-gated on the live
 * session's webcam COUNT — GREYED (present but inert, no click action) when 0 cams are enumerated, LIVE
 * (present + click-actionable, navigates) at ≥1. This is the DELIBERATE departure from Phase-9's
 * hide-the-tile (Calibration) to greyed-visible (D-08).
 *
 * It mirrors [works.mees.dinghy.ui.ShellPresenceTest] exactly: it hosts the production [RootController]
 * inside the real [DinghyTheme] under [createComposeRule], resolving the PROCESS [DinghyApp] container,
 * and drives routing by seeding a saved [ConnectionConfig] + a published [SpineHandle] whose
 * `webcams` StateFlow carries the cam list under test — NO live socket is opened. The greyed-vs-live
 * assertion is the `assertHasClickAction()` / `assertHasNoClickAction()` discipline `ShellPresenceTest`
 * already uses for the inert Devices/Power tiles.
 */
@RunWith(AndroidJUnit4::class)
class DrawerWebcamGatingTest {

    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private val app: DinghyApp = ApplicationProvider.getApplicationContext()
    private val container get() = app.container

    private val testScope = CoroutineScope(SupervisorJob())

    /** A representative WebRTC-mediamtx cam (the E5/E3 reality) — enough to make the count ≥1. */
    private fun aCam(name: String = "cam1") = Webcam(
        name = name,
        service = "webrtc-mediamtx",
        streamUrl = "/webrtc",
        uid = name,
    )

    /** Seed the container so `derive(hasConfig, Ready)` yields Shell, with [cams] enumerated. */
    private fun seedShell(cams: List<Webcam>) {
        runBlocking { container.connectionStore.save(ConnectionConfig(host = "10.255.255.1", port = 7125)) }
        val store = PrinterStateStore(scope = testScope)
        val state = MutableStateFlow(
            PrinterState(
                klippyState = KlippyState.Ready,
                connection = ConnectionState.Connected,
            ),
        )
        val handle = SpineHandle(
            printerState = state,
            connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected),
            capabilities = MutableStateFlow(Capabilities()),
            dispatcher = CommandDispatcher(request = { _, _, _ -> JsonNull }, scope = testScope),
            store = store,
            minExtrudeTemp = store.minExtrudeTemp,
            maxExtrudeDistance = store.maxExtrudeDistance,
            temperatureBackfill = store.temperatureBackfill,
            httpBase = "http://test:7125",
            metadata = MutableStateFlow(null),
            lastJob = MutableStateFlow(null),
            webcams = MutableStateFlow(cams),
            activeSpool = MutableStateFlow(null),
            fileBrowser = object : FileBrowserClient {},
            sessionInstanceId = 1L,
        )
        container.publishSpine(handle)
    }

    private fun setContentRoot() {
        composeRule.setContent {
            DinghyTheme(container.themeResolver) {
                RootController(container)
            }
        }
    }

    @After
    fun tearDown() {
        container.publishSpine(null)
        runBlocking { container.connectionStore.clear() }
        testScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    // 0 cams → the Webcam tile is present but GREYED (inert, no click action).
    @Test
    fun zeroCams_webcamTileIsGreyedAndInert() {
        seedShell(cams = emptyList())
        setContentRoot()
        composeRule.waitForIdle()

        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Webcam"))
        composeRule.waitForIdle()
        // Present (greyed-visible, D-08) but NOT click-actionable → it cannot navigate.
        composeRule.onNodeWithText("Webcam").assertIsDisplayed().assertHasNoClickAction()
    }

    // ≥1 cam → the Webcam tile is LIVE (present + click-actionable) and navigates to the Webcam screen.
    @Test
    fun withCams_webcamTileIsLiveAndNavigates() {
        seedShell(cams = listOf(aCam()))
        setContentRoot()
        composeRule.waitForIdle()

        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Webcam"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Webcam").assertIsDisplayed().assertHasClickAction()

        // Tapping the LIVE tile navigates to the Webcam destination + collapses the drawer: the Webcam
        // screen's red Back gutter tile appears and the drawer's "Status" tile is gone.
        composeRule.onNodeWithText("Webcam").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Back").assertIsDisplayed()
        composeRule.onNodeWithText("Status").assertDoesNotExist()
    }
}
