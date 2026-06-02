package works.mees.dinghy.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.ui.extrude.ExtrudeHolder
import works.mees.dinghy.ui.extrude.ExtrudeScreen
import works.mees.dinghy.ui.files.FileBrowserClient
import works.mees.dinghy.ui.files.FileBrowserHolder
import works.mees.dinghy.ui.files.FilesScreen
import works.mees.dinghy.ui.move.MoveHolder
import works.mees.dinghy.ui.move.MoveScreen
import works.mees.dinghy.ui.printstatus.PrintStatusScreen
import works.mees.dinghy.ui.route.Dest
import works.mees.dinghy.ui.screen.SettingsScreen
import works.mees.dinghy.ui.temperature.TemperatureHolder
import works.mees.dinghy.ui.temperature.TemperatureScreen

/**
 * The running shell host (SHELL-01) — it renders the active [Dest] FULL-BLEED with NO persistent
 * title/status bar (status is color on existing elements, never global chrome) and exposes the ONE
 * navigation surface: the swipe-up full-screen [AppDrawer] (D-14).
 *
 * ## Lean route holder + back stack — NOT Navigation-Compose (D-05)
 * The active destination is a single `var dest` rendered by a lean `when(dest)` (lighter than a nav
 * graph; each panel is a one-line addition). Navigation keeps a small [backStack] of CALLER dests:
 * opening a panel pushes the current screen, and Back (system OR gutter) pops to the caller. PrintStatus
 * is the home root — navigating home clears the stack, and Back at home falls through to the OS so it
 * closes the app. There is NO `androidx.navigation` dependency here.
 *
 * ## Settings is an IN-SHELL destination (review #2/#11)
 * Settings is reached via the drawer's "Settings" tile (`Dest.Settings`) and rendered here like any
 * other destination. There is deliberately NO `onOpenSettings` callback on this shell: the only
 * open-Settings-OUTSIDE-the-shell path (first-run / splash "Edit connection") is owned by the
 * [RootController] (Task 2). Saving the connection from in-shell Settings returns the shell to Print
 * Status.
 *
 * ## Drawer gesture + collapse
 * A swipe UP anywhere on the canvas opens the drawer (`drawerOpen = true`); the drawer floats over the
 * destination as a full-screen overlay. `BackHandler(enabled = drawerOpen)` collapses it on system
 * Back. Tapping a live drawer tile sets [Dest] and collapses.
 *
 * ## Per-session Print Status holder
 * The Print Status home needs a [PrintStatusHolder] built from the LIVE per-session [PrinterStateStore]
 * (it owns the primary-heater RingBuffer + the 2×3 grid model). The shell reads the current
 * [works.mees.dinghy.di.SpineHandle.store] off [AppContainer.spine] and `remember`s a holder keyed on
 * that store, so a spine rebuild (reconnect) re-keys the holder onto the new session's store. While
 * idle (no spine) an empty fallback store backs the holder so the surface still composes (it shows the
 * idle "Ready"/— readout). All color routes through [LocalTokens] (THEME-01).
 *
 * @param container the process-scoped service-locator (provides the live spine + theme + dispatcher).
 */
@Composable
fun AppShell(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val scope = rememberCoroutineScope()

    // The lean route holder (D-05) — NOT Navigation-Compose. [dest] is the visible screen; [backStack]
    // holds the CALLERS (most-recent last). Navigating to a panel pushes the current screen; Back pops
    // to the caller. PrintStatus is the home/root — navigating home CLEARS the stack, and Back at home
    // (empty stack) is left to the OS so it closes the app (per the system-Back contract).
    var dest by remember { mutableStateOf(Dest.PrintStatus) }
    val backStack = remember { mutableStateListOf<Dest>() }
    var drawerOpen by remember { mutableStateOf(false) }

    fun navigateTo(target: Dest) {
        if (target == dest) return
        if (target == Dest.PrintStatus) backStack.clear() else backStack.add(dest)
        dest = target
    }
    fun goBack() {
        if (backStack.isNotEmpty()) dest = backStack.removeAt(backStack.lastIndex)
    }

    // Build the Print Status holder from the LIVE per-session store; re-key it when the spine rebuilds.
    val spine by container.spine.collectAsStateWithLifecycle()
    val capabilities by container.capabilities.collectAsStateWithLifecycle(initialValue = Capabilities())
    val httpBase by container.httpBase.collectAsStateWithLifecycle(initialValue = "")
    // An empty fallback store keeps the home surface composable while idle (no live session yet).
    val idleStore = remember { PrinterStateStore(scope = scope) }
    val idlePrinterState = remember { MutableStateFlow(PrinterState()) }
    val idleFileBrowser = remember { object : FileBrowserClient {} }
    val store = spine?.store ?: idleStore
    val printerStateFlow = spine?.printerState ?: idlePrinterState
    val fileBrowser = spine?.fileBrowser ?: idleFileBrowser
    // The three Phase-5 control panels — each holder built off the SAME live per-session store and
    // re-keyed when the spine rebuilds (reconnect), mirroring the Print Status holder above.
    val temperatureHolder = remember(store) { TemperatureHolder(scope = scope, store = store) }
    val moveHolder = remember(store) { MoveHolder(scope = scope, store = store) }
    val extrudeHolder = remember(store) { ExtrudeHolder(scope = scope, store = store) }
    val filesHolder = remember(fileBrowser, printerStateFlow) {
        FileBrowserHolder(scope = scope, client = fileBrowser, printerState = printerStateFlow)
    }
    val printerState by printerStateFlow.collectAsStateWithLifecycle()

    // System Back: collapse the drawer if open; otherwise pop the back stack to the calling screen.
    // When the drawer is closed AND we're at the home root (empty stack), this is DISABLED so the OS
    // handles Back and closes the app (the desired "only Status closes the app" behavior).
    BackHandler(enabled = drawerOpen) { drawerOpen = false }
    BackHandler(enabled = !drawerOpen && backStack.isNotEmpty()) { goBack() }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(t.bg)
            // Swipe UP from anywhere on the canvas reveals the drawer (the one nav affordance).
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -SWIPE_UP_THRESHOLD_PX) drawerOpen = true
                }
            },
    ) {
        // The active destination, full-bleed (no persistent chrome).
        when (dest) {
            Dest.PrintStatus -> PrintStatusScreen(
                container = container,
                onOpenFiles = { navigateTo(Dest.Files) },
            )
            Dest.Temperature -> TemperatureScreen(
                container = container,
                holder = temperatureHolder,
                onBack = { goBack() },
            )
            Dest.Move -> MoveScreen(
                container = container,
                holder = moveHolder,
                onBack = { goBack() },
            )
            Dest.Extrude -> ExtrudeScreen(
                container = container,
                holder = extrudeHolder,
                onBack = { goBack() },
            )
            Dest.Files -> FilesScreen(
                holder = filesHolder,
                printerState = printerState,
                httpBase = httpBase,
                canStartPrint = capabilities.hasObject("virtual_sdcard"),
                onBack = { goBack() },
            )
            Dest.Settings -> SettingsScreen(
                container = container,
                onConnectionSaved = { navigateTo(Dest.PrintStatus) },
            )
        }

        // A thin bottom-edge affordance: a deliberate, discoverable swipe-up handle at the bottom.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(8.dp)
                .background(t.hair),
        )

        if (drawerOpen) {
            AppDrawer(
                onDestination = { navigateTo(it) },
                onDismiss = { drawerOpen = false },
            )
        }
    }
}

/** Drag distance (px) past which an upward drag opens the drawer — a deliberate, non-accidental pull. */
private const val SWIPE_UP_THRESHOLD_PX = 80f
