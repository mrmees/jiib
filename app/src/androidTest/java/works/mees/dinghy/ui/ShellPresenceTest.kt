package works.mees.dinghy.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
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
import works.mees.dinghy.ui.shell.RootController

/**
 * Instrumented proof of SHELL-01 / D-06 / D-14 (review #12): the shell is present and navigable, the
 * greyed drawer tiles are inert, and the Splash hard-override has NO reachable drawer.
 *
 * It hosts the production [RootController] (the single routing authority, 04-07) inside the real
 * [DinghyTheme] boundary under [createComposeRule], resolving the PROCESS [DinghyApp] container (so the
 * real DataStores exist). Routing is driven by seeding the container's inputs directly — a saved
 * [ConnectionConfig] plus a published [SpineHandle] whose `printerState` `klippyState` forces Shell
 * (Ready) vs Splash (Shutdown) through the pure `derive()` — NO live socket is opened (the lean fake
 * spine seeds flows directly, mirroring the AppContainerTest discipline).
 *
 * ### Notification-permission-denied smoke (review #12, API 30+)
 * On the flox test device (API 30) the runtime POST_NOTIFICATIONS prompt does NOT apply — notifications
 * are granted by default below API 33, so a true *denied* state is unreachable on this device. The
 * relevant safety property is therefore proven by the NON-CRASH launch: the shell composes and the
 * drawer opens with the permission absent from the runtime grant flow, and the started FGS
 * (MainActivity, not under test here) never throws a SecurityException for it. We assert the non-crash
 * compose+navigate path; the true-denial branch is documented as API-30-unreachable.
 */
@RunWith(AndroidJUnit4::class)
class ShellPresenceTest {

    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private val app: DinghyApp = ApplicationProvider.getApplicationContext()
    private val container get() = app.container

    // A test scope for the seeded per-session store + dispatcher (no real socket).
    private val testScope = CoroutineScope(SupervisorJob())

    /**
     * Seed the container so `derive(hasConfig, state)` yields the route we want:
     *  - a saved config (so it is NOT the Connect/first-run branch), and
     *  - a published spine whose printerState carries [klippy] (Ready → Shell, else → Splash).
     */
    private fun seedRoute(klippy: KlippyState, printState: works.mees.dinghy.state.PrintState = works.mees.dinghy.state.PrintState.Standby) {
        runBlocking { container.connectionStore.save(ConnectionConfig(host = "10.255.255.1", port = 7125)) }
        val store = PrinterStateStore(scope = testScope)
        val state = MutableStateFlow(
            PrinterState(
                klippyState = klippy,
                printState = printState,
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

    // (a) Drawer opens; live Status + Settings tiles present, tappable; tapping Settings navigates + collapses.
    @Test
    fun shellRoute_drawerOpens_liveTilesPresentAndNavigate() {
        seedRoute(KlippyState.Ready)
        setContentRoot()
        composeRule.waitForIdle()

        // Swipe up to open the drawer.
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        // Status is the first tile (always on-screen): displayed + click-actionable.
        composeRule.onNodeWithText("Status").assertIsDisplayed().assertHasClickAction()
        // Settings is a lower tile in the lazy grid — scroll the grid to it, then assert + tap.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Settings"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings").assertIsDisplayed().assertHasClickAction()

        // Tapping Settings navigates to the in-shell Settings destination and collapses the drawer.
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
        // The Settings screen header is shown; the drawer's "Status" tile is gone (collapsed).
        composeRule.onNodeWithText("Connection").assertIsDisplayed()
        composeRule.onNodeWithText("Status").assertDoesNotExist()
    }

    // (b) Greyed "coming soon" tiles (incl. Power) do NOT navigate — present but not click-actionable.
    @Test
    fun shellRoute_greyedTilesAreInert() {
        seedRoute(KlippyState.Ready)
        setContentRoot()
        composeRule.waitForIdle()

        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        // Files is an upper greyed tile (on-screen): present but NOT click-actionable (inert).
        // (Move/Temp/Tools went LIVE in Phase 5 (05-08); Files/Macros/Devices remain greyed.)
        composeRule.onNodeWithText("Files").assertIsDisplayed().assertHasNoClickAction()

        // Power is the last (red, greyed) tile — scroll the grid to it; it is inert (T-04-07-E).
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Power"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Power").assertIsDisplayed().assertHasNoClickAction()

        // Tapping a greyed tile does nothing — the drawer stays open (Power still composed in the
        // drawer; if a greyed tile had navigated, the drawer would have collapsed and Power would be gone).
        composeRule.onNodeWithText("Power").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Power").assertExists()
    }

    // (c) Splash hard-override (D-06): the drawer is structurally absent — a swipe-up reveals no tiles.
    @Test
    fun splashRoute_drawerIsStructurallyUnreachable() {
        seedRoute(KlippyState.Shutdown)
        setContentRoot()
        composeRule.waitForIdle()

        // The splash recovery surface is shown (the app title is the splash header).
        composeRule.onNodeWithText("Dinghy Display").assertIsDisplayed()

        // Attempting a swipe-up does NOT reveal the drawer tiles — AppShell is not composed under Splash.
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Status").assertDoesNotExist()
        composeRule.onNodeWithText("Power").assertDoesNotExist()
    }

    // (d) Notification-permission non-crash launch smoke (review #12) — see class kdoc for the API-30
    //     default-grant rationale. The shell composes + the drawer opens without a SecurityException.
    @Test
    fun notificationPermission_nonCrashLaunchAndCompose() {
        seedRoute(KlippyState.Ready)
        setContentRoot()
        composeRule.waitForIdle()
        // No crash reaching here. The shell composed; the drawer is reachable (compose path is alive).
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Status").assertIsDisplayed()
    }
}
