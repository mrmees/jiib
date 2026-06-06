package works.mees.dinghy.ui.finetune

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
import works.mees.dinghy.theme.compose.DinghyTheme
import works.mees.dinghy.ui.files.FileBrowserClient
import works.mees.dinghy.ui.shell.RootController

/**
 * Instrumented proof of the Fine-Tune entry path (TUNE-01 / D-21, 17-06 — closes the 17-01 RED scaffold).
 *
 * Mirrors the [works.mees.dinghy.ui.ShellPresenceTest] harness: it hosts the production [RootController]
 * (the single routing authority) inside the real [DinghyTheme] under [createComposeRule], resolving the
 * PROCESS [DinghyApp] container, and seeds a saved config + a Ready spine so `derive()` yields the Shell.
 * NO live socket is opened.
 *
 * It proves two contracts:
 *  - **Tune → Dest.FineTune**: invoking the Fine-Tune entry (the always-reachable drawer tile — the SAME
 *    `navigateTo(Dest.FineTune)` the Print-Status Tune shortcut uses) lands on the Fine-Tune Hub (its
 *    "Fine-Tune" title + the Motion/Extrusion entries render).
 *  - **Reset-to-Hub on entry (REVIEW #6)**: navigating INTO a group page (Motion), then leaving and
 *    RE-entering Fine-Tune via the drawer, opens the HUB again — never the stale Motion group page.
 */
@RunWith(AndroidJUnit4::class)
class FineTuneNavTest {

    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private val app: DinghyApp = ApplicationProvider.getApplicationContext()
    private val container get() = app.container

    private val testScope = CoroutineScope(SupervisorJob())

    /** Seed a saved config + a Ready spine so `derive(hasConfig, state)` yields the running Shell. */
    private fun seedShell() {
        runBlocking { container.connectionStore.save(ConnectionConfig(host = "10.255.255.1", port = 7125)) }
        val store = PrinterStateStore(scope = testScope)
        val state = MutableStateFlow(
            PrinterState(
                klippyState = KlippyState.Ready,
                printState = works.mees.dinghy.state.PrintState.Standby,
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
            webcams = MutableStateFlow(emptyList()),
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

    /** Open the drawer and tap the Fine-Tune tile (the always-reachable entry). */
    private fun openFineTuneViaDrawer() {
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        // Fine-Tune is a lower tile in the lazy grid — scroll to it, then tap.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Fine-Tune"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Fine-Tune").performClick()
        composeRule.waitForIdle()
    }

    @After
    fun tearDown() {
        container.publishSpine(null)
        runBlocking { container.connectionStore.clear() }
        testScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    // (a) The Fine-Tune entry lands on Dest.FineTune — the Hub renders (title + the two group entries).
    @Test
    fun tuneAction_navigatesToFineTuneHub() {
        seedShell()
        setContentRoot()
        composeRule.waitForIdle()

        openFineTuneViaDrawer()

        // The Fine-Tune Hub is shown: its "Fine-Tune" title + the Motion + Extrusion entries.
        composeRule.onNodeWithText("Fine-Tune").assertIsDisplayed()
        composeRule.onNodeWithText("Motion").assertIsDisplayed()
        composeRule.onNodeWithText("Extrusion").assertIsDisplayed()
    }

    // (b) Reset-to-Hub on entry (REVIEW #6): into Motion, leave, re-enter → the HUB, not the Motion page.
    @Test
    fun reEnteringFineTune_opensHub_notStaleGroupPage() {
        seedShell()
        setContentRoot()
        composeRule.waitForIdle()

        // Enter Fine-Tune → tap Motion → the Motion group page is shown.
        openFineTuneViaDrawer()
        composeRule.onNodeWithText("Motion").performClick()
        composeRule.waitForIdle()
        // On the Motion page the Hub's Extrusion entry is gone (we are inside a group, not the Hub).
        composeRule.onNodeWithText("Extrusion").assertDoesNotExist()

        // Re-enter Fine-Tune via the drawer (navigateTo resets fineTuneGroup = null, REVIEW #6).
        openFineTuneViaDrawer()
        // It opens the HUB again — both group entries render, NOT a stale Motion group page.
        composeRule.onNodeWithText("Motion").assertIsDisplayed()
        composeRule.onNodeWithText("Extrusion").assertIsDisplayed()
    }
}
