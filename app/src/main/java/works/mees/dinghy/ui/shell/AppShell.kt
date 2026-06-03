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
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import works.mees.dinghy.calibration.BedMeshHolder
import works.mees.dinghy.calibration.CalibrationHubHolder
import works.mees.dinghy.calibration.CalibrationRoutine
import works.mees.dinghy.calibration.ProbeCalibrateHolder
import works.mees.dinghy.calibration.ScrewsTiltHolder
import works.mees.dinghy.calibration.TiltHolder
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.ui.console.ConsoleHolder
import works.mees.dinghy.ui.console.ConsoleScreen
import works.mees.dinghy.ui.extrude.ExtrudeHolder
import works.mees.dinghy.ui.extrude.ExtrudeScreen
import works.mees.dinghy.ui.files.FileBrowserClient
import works.mees.dinghy.ui.files.FileBrowserHolder
import works.mees.dinghy.ui.files.FilesScreen
import works.mees.dinghy.ui.macros.BookmarkedMacrosScreen
import works.mees.dinghy.ui.macros.MacroExecutionPopup
import works.mees.dinghy.ui.macros.MacroHolder
import works.mees.dinghy.ui.macros.MacroVm
import works.mees.dinghy.ui.macros.SystemMacrosScreen
import works.mees.dinghy.ui.calibration.BedMeshScreen
import works.mees.dinghy.ui.calibration.CalibrationHubScreen
import works.mees.dinghy.ui.calibration.ProbeCalibrateScreen
import works.mees.dinghy.ui.calibration.ScrewsTiltScreen
import works.mees.dinghy.ui.calibration.TiltScreen
import works.mees.dinghy.ui.calibration.TiltVariant
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

    // Macro sub-navigation (within Dest.Macros — NOT separate top-level Dests, mirroring how the popup
    // lives inside the macro surface). The drawer "Macros" tile opens the Bookmarked launcher;
    // `Manage macros` reveals the System list; tapping a macro opens its Execution popup as an overlay.
    var macroShowSystem by remember { mutableStateOf(false) }
    var macroPopupFor by remember { mutableStateOf<MacroVm?>(null) }

    // Calibration sub-navigation (a lean LOCAL back-stack WITHIN Dest.Calibration — NOT five new
    // top-level Dests, mirroring how Dest.Macros hosts its Bookmarked-vs-System sub-screens). null =
    // the hub; a non-null routine = that routine's page. The hub's onNavigate pushes; a BackHandler
    // (and each page's green Back) pops back to the hub.
    var calibrationRoutine by remember { mutableStateOf<CalibrationRoutine?>(null) }

    fun navigateTo(target: Dest) {
        if (target == dest) return
        if (target == Dest.PrintStatus) backStack.clear() else backStack.add(dest)
        // Entering the Macros surface always starts on the Bookmarked launcher with no popup open.
        if (target == Dest.Macros) {
            macroShowSystem = false
            macroPopupFor = null
        }
        // Entering the Calibration surface always starts on the hub (no routine selected).
        if (target == Dest.Calibration) {
            calibrationRoutine = null
        }
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

    // ---- Calibration holders (09-07) ---------------------------------------------------------------
    // The five headless calibration holders, each built off the SAME live per-session store and re-keyed
    // when the spine rebuilds (reconnect), mirroring the Phase-5 control holders above. The dispatcher
    // Failure stream comes from the per-session dispatcher (`spine?.dispatcher?.events`) — null while
    // idle so the holders simply carry no error until a live session attaches; re-captured when `store`
    // swaps (a new session brings a new dispatcher). The two TiltHolders share ONE TiltScreen via an
    // applied-selector lambda (D-02) and each fold ONLY their own routine's failures via its dispatchKey.
    val calibrationHubHolder = remember(store) { CalibrationHubHolder(scope = scope, store = store) }
    val calibEvents = spine?.dispatcher?.events
    val screwsTiltHolder = remember(store) {
        ScrewsTiltHolder(scope = scope, store = store, events = calibEvents)
    }
    val zTiltHolder = remember(store) {
        TiltHolder(
            scope = scope,
            store = store,
            events = calibEvents,
            dispatchKey = CommandRegistry.zTiltAdjust.dispatchKey(Unit),
        )
    }
    val qglHolder = remember(store) {
        TiltHolder(
            scope = scope,
            store = store,
            events = calibEvents,
            dispatchKey = CommandRegistry.quadGantryLevel.dispatchKey(Unit),
        )
    }
    val bedMeshHolder = remember(store) { BedMeshHolder(scope = scope, store = store, events = calibEvents) }
    val probeCalibrateHolder = remember(store) {
        ProbeCalibrateHolder(scope = scope, store = store, events = calibEvents)
    }
    val zTiltVm by zTiltHolder.vm.collectAsStateWithLifecycle()
    val qglVm by qglHolder.vm.collectAsStateWithLifecycle()
    val bedMeshVm by bedMeshHolder.vm.collectAsStateWithLifecycle()
    val probeCalibrateVm by probeCalibrateHolder.vm.collectAsStateWithLifecycle()

    // ---- Console + Macro holders (08-07) -----------------------------------------------------------
    // Both are SESSION-owned: built off the same per-session store and re-keyed when the spine rebuilds
    // (reconnect), exactly like the Phase-5 control holders above. While idle the empty fallback store
    // backs them so the screens still compose (an idle Console is "quiet", an idle Macros surface is
    // capability-unavailable).
    val consoleHolder = remember(store) {
        ConsoleHolder(
            scope = scope,
            gcodeResponses = store.gcodeResponses,
            consoleBackfill = store.consoleBackfill,
        )
    }
    // Cancel this holder's detached collectors when `remember(store)` swaps it on a spine rebuild
    // (reconnect) — otherwise the discarded holder leaks its collector pair until the shell leaves
    // composition, compounding per reconnect (WR-01). onDispose fires when [consoleHolder] re-keys.
    DisposableEffect(consoleHolder) { onDispose { consoleHolder.cancel() } }

    // Macro bookmarks/revealHidden are PROCESS-scoped (container.macroPrefs from Task 1 B1) — they
    // survive reconnects, so they are stateIn'd ONCE on the shell scope (not re-keyed on the store).
    val bookmarksFlow = remember {
        container.macroPrefs.bookmarks.stateIn(scope, SharingStarted.Eagerly, emptySet())
    }
    val revealHiddenFlow = remember {
        container.macroPrefs.revealHidden.stateIn(scope, SharingStarted.Eagerly, false)
    }
    // Capabilities StateFlow for the holder: the live session's (carries macro NAMEs) or an empty
    // fallback while idle. Re-keyed when the spine rebuilds so a reconnect re-points the macro universe.
    val idleCapabilities = remember { MutableStateFlow(Capabilities()) }
    val capabilitiesFlow = spine?.capabilities ?: idleCapabilities
    val macroHolder = remember(store, capabilitiesFlow) {
        MacroHolder(
            scope = scope,
            capabilities = capabilitiesFlow,
            bookmarks = bookmarksFlow,
            revealHidden = revealHiddenFlow,
        )
    }
    // Cancel this holder's detached combine collector when `remember(store, capabilitiesFlow)` swaps it
    // on a spine rebuild (reconnect) — otherwise the discarded holder leaks its collector until the
    // shell leaves composition, compounding per reconnect (WR-01). onDispose fires when [macroHolder]
    // re-keys.
    DisposableEffect(macroHolder) { onDispose { macroHolder.cancel() } }
    // Feed the parsed macro bodies seam (handshake/reconnect) into the holder so each macro's params
    // populate. Re-collected when the store rebuilds (a new session's macroBodies).
    androidx.compose.runtime.LaunchedEffect(macroHolder, store) {
        store.macroBodies.collect { macroHolder.setMacroBodies(it) }
    }
    // The current session dispatcher (the macro Execution popup routes through it); null while idle.
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    // Console backfill-failed flag: the server.gcode_store read failed on (re)connect. Best-effort —
    // surfaced as the non-blanking "History unavailable" notice (WR-04). Re-keyed when the spine
    // rebuilds so a reconnect re-points at the new session's store flag.
    val consoleBackfillFailed by store.consoleBackfillFailed.collectAsStateWithLifecycle()

    // System Back: collapse the drawer if open; otherwise pop the back stack to the calling screen.
    // When the drawer is closed AND we're at the home root (empty stack), this is DISABLED so the OS
    // handles Back and closes the app (the desired "only Status closes the app" behavior).
    BackHandler(enabled = drawerOpen) { drawerOpen = false }
    BackHandler(enabled = !drawerOpen && backStack.isNotEmpty()) { goBack() }
    // Macro sub-state intercepts system Back BEFORE the generic back-stack pop (registered later =
    // higher priority): an open popup closes first, then the System list returns to the launcher.
    BackHandler(enabled = !drawerOpen && dest == Dest.Macros && macroPopupFor != null) {
        macroPopupFor = null
    }
    BackHandler(enabled = !drawerOpen && dest == Dest.Macros && macroPopupFor == null && macroShowSystem) {
        macroShowSystem = false
    }
    // Calibration sub-state intercepts system Back BEFORE the generic back-stack pop (registered later =
    // higher priority): an open routine page returns to the hub; from the hub, Back falls through to the
    // generic back-stack pop (leaving the Calibration surface).
    BackHandler(enabled = !drawerOpen && dest == Dest.Calibration && calibrationRoutine != null) {
        calibrationRoutine = null
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(t.bg)
            // Swipe UP from anywhere on the canvas reveals the drawer (the one nav affordance).
            // EXCEPT on the finger-scrollable picker/scrollback screens — Files (RecyclerView picker),
            // Console (RecyclerView scrollback) and Macros (the System manage-visibility LazyColumn):
            // a full-canvas vertical-drag detector fights the list scroll ("the stroke gets confusing").
            // Each of those screens keeps an explicit green Back in its gutter as the exit (D-05).
            .pointerInput(dest) {
                // Calibration is suppressed too: BedMeshScreen's Load selector is a scrollable Field
                // (the Files Views-in-Compose scroll lesson) — the hub + each page keeps an explicit
                // green Back as the exit (D-05).
                if (dest !in setOf(Dest.Files, Dest.Console, Dest.Macros, Dest.Calibration)) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -SWIPE_UP_THRESHOLD_PX) drawerOpen = true
                    }
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
            Dest.Macros -> {
                // The macro surface: Bookmarked launcher OR the System manage-visibility list. Tapping a
                // macro opens its Execution popup as a full-screen overlay (rendered below, outside the
                // when so it floats over either sub-screen). Back from the launcher leaves the surface;
                // Back from the System list returns to the launcher.
                if (macroShowSystem) {
                    SystemMacrosScreen(
                        holder = macroHolder,
                        onToggleBookmark = { name -> scope.launch { container.macroPrefs.toggleBookmark(name) } },
                        onSetRevealHidden = { reveal -> scope.launch { container.macroPrefs.setRevealHidden(reveal) } },
                        onBack = { macroShowSystem = false },
                    )
                } else {
                    BookmarkedMacrosScreen(
                        holder = macroHolder,
                        onRunMacro = { macro -> macroPopupFor = macro },
                        onManage = { macroShowSystem = true },
                        onBack = { goBack() },
                    )
                }
            }
            Dest.Console -> ConsoleScreen(
                holder = consoleHolder,
                onBack = { goBack() },
                backfillFailed = consoleBackfillFailed,
            )
            Dest.Calibration -> {
                // The calibration surface: the hub (a routine grid) OR the selected routine page. The
                // hub's onNavigate pushes the LOCAL sub-dest; each page's green Back (and system Back)
                // pops back to the hub by clearing [calibrationRoutine] — a lean local back-stack within
                // Dest.Calibration (NOT five top-level Dests, mirroring Dest.Macros). The two tilt
                // variants (Z-Tilt / QGL) share ONE TiltScreen via [TiltVariant] + a per-variant holder.
                when (val routine = calibrationRoutine) {
                    null -> CalibrationHubScreen(
                        holder = calibrationHubHolder,
                        onNavigate = { calibrationRoutine = it },
                        onBack = { goBack() },
                    )
                    CalibrationRoutine.SCREWS_TILT -> ScrewsTiltScreen(
                        container = container,
                        holder = screwsTiltHolder,
                        onBack = { calibrationRoutine = null },
                    )
                    CalibrationRoutine.Z_TILT -> TiltScreen(
                        vm = zTiltVm,
                        variant = TiltVariant.ZTilt,
                        tokens = t,
                        dispatcher = dispatcher,
                        onRunDispatched = { zTiltHolder.markDispatched() },
                        onHome = { dispatcher?.dispatch(CommandRegistry.homeAll, Unit) },
                        onEnter = { zTiltHolder.reset() },
                        onBack = { calibrationRoutine = null },
                    )
                    CalibrationRoutine.QUAD_GANTRY_LEVEL -> TiltScreen(
                        vm = qglVm,
                        variant = TiltVariant.Qgl,
                        tokens = t,
                        dispatcher = dispatcher,
                        onRunDispatched = { qglHolder.markDispatched() },
                        onHome = { dispatcher?.dispatch(CommandRegistry.homeAll, Unit) },
                        onEnter = { qglHolder.reset() },
                        onBack = { calibrationRoutine = null },
                    )
                    CalibrationRoutine.BED_MESH -> BedMeshScreen(
                        vm = bedMeshVm,
                        tokens = t,
                        dispatcher = dispatcher,
                        onCycleScaleMode = { bedMeshHolder.cycleScaleMode() },
                        onBack = { calibrationRoutine = null },
                    )
                    CalibrationRoutine.PROBE_CALIBRATE -> ProbeCalibrateScreen(
                        vm = probeCalibrateVm,
                        tokens = t,
                        dispatcher = dispatcher,
                        onStartDispatched = { },
                        onEnter = {
                            probeCalibrateHolder.reset()
                            // Re-read the saved z_offset on entry so a just-applied SAVE_CONFIG shows
                            // immediately (it's otherwise only read at handshake) — no app restart needed.
                            spine?.refreshProbeZOffset?.invoke()
                        },
                        onHome = { dispatcher?.dispatch(CommandRegistry.homeAll, Unit) },
                        onAbort = { probeCalibrateHolder.markAborted() },
                        onBack = { calibrationRoutine = null },
                    )
                }
            }
            Dest.Settings -> SettingsScreen(
                container = container,
                onConnectionSaved = { navigateTo(Dest.PrintStatus) },
            )
        }

        // Macro Execution popup overlay (D-08) — a full-screen action gate floating over the macro
        // surface. Shown only on Dest.Macros with a tapped macro AND a live session dispatcher (a macro
        // can only be dispatched while connected). Dismiss (Cancel or successful dispatch) clears it.
        val popupMacro = macroPopupFor
        val liveDispatcher = dispatcher
        if (dest == Dest.Macros && popupMacro != null && liveDispatcher != null) {
            MacroExecutionPopup(
                holder = macroHolder,
                macro = popupMacro,
                dispatcher = liveDispatcher,
                onDismiss = { macroPopupFor = null },
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
