package works.mees.jiib.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import works.mees.jiib.R
import works.mees.jiib.calibration.BedMeshHolder
import works.mees.jiib.calibration.CalibrationHubHolder
import works.mees.jiib.calibration.CalibrationRoutine
import works.mees.jiib.calibration.ProbeCalibrateHolder
import works.mees.jiib.calibration.ScrewsTiltHolder
import works.mees.jiib.calibration.TiltHolder
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.SetSpoolArgs
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.ConfirmGuard
import works.mees.jiib.designsystem.components.FloatingEStop
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.net.JsonRpcMethods
import works.mees.jiib.outputs.OutputsHolder
import works.mees.jiib.prompt.PromptEngine
import works.mees.jiib.ui.prompt.PromptDialog
import works.mees.jiib.ui.prompt.flattenContentButtons
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.Capabilities
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.state.PrinterStateStore
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.ui.console.ConsoleHolder
import works.mees.jiib.ui.console.ConsoleScreen
import works.mees.jiib.ui.extrude.ExtrudeHolder
import works.mees.jiib.ui.extrude.ExtrudeScreen
import works.mees.jiib.ui.finetune.FineTuneHolder
import works.mees.jiib.ui.finetune.FineTuneScreen
import works.mees.jiib.ui.heatpresets.HeatPresetsScreen
import works.mees.jiib.ui.increments.IncrementValuesScreen
import works.mees.jiib.ui.files.FileBrowserClient
import works.mees.jiib.ui.files.FileBrowserHolder
import works.mees.jiib.ui.files.FilesScreen
import works.mees.jiib.ui.macros.BookmarkedMacrosScreen
import works.mees.jiib.ui.macros.MacroHolder
import works.mees.jiib.ui.calibration.BedMeshScreen
import works.mees.jiib.ui.calibration.CalibrationHubScreen
import works.mees.jiib.ui.calibration.ProbeCalibrateScreen
import works.mees.jiib.ui.calibration.ScrewsTiltScreen
import works.mees.jiib.ui.calibration.TiltScreen
import works.mees.jiib.ui.calibration.TiltVariant
import works.mees.jiib.ui.move.MoveHolder
import works.mees.jiib.ui.move.MoveScreen
import works.mees.jiib.ui.printstatus.PrintStatusScreen
import works.mees.jiib.ui.route.NavDest
import works.mees.jiib.ui.route.FOOT_GUN_DESTS
import works.mees.jiib.ui.route.shouldPopToRoot
import works.mees.jiib.ui.route.toNavDest
import works.mees.jiib.ui.spool.SpoolHolder
import works.mees.jiib.ui.spool.SpoolPrefilterSeed
import works.mees.jiib.ui.spool.SpoolScreen
import works.mees.jiib.ui.spool.parseNormalizedHex
import works.mees.jiib.ui.spool.scan.ScanSurface
import works.mees.jiib.ui.systeminfo.SystemInformationScreen
import works.mees.jiib.ui.outputs.OutputsScreen
import works.mees.jiib.ui.screen.PrintersScreen
import works.mees.jiib.ui.screen.AppSettingsScreen
import works.mees.jiib.ui.screen.PowerResetScreen
import works.mees.jiib.ui.screen.PrinterSettingsScreen
import works.mees.jiib.ui.screen.SystemPageScreen
import works.mees.jiib.ui.screen.ThemeScreen
import works.mees.jiib.ui.temperature.TemperatureHolder
import works.mees.jiib.ui.temperature.TemperatureScreen
import works.mees.jiib.ui.webcam.WebcamHolder
import works.mees.jiib.ui.webcam.WebcamScreen
import works.mees.jiib.ui.webcam.webcamMedia3Holder
import works.mees.jiib.render.Media3SurfaceProvider
import works.mees.jiib.config.ConnectionConfig
import android.graphics.Bitmap

/**
 * The running shell host (SHELL-01) — it renders the active [NavDest] FULL-BLEED with NO persistent
 * title/status bar (status is color on existing elements, never global chrome) and exposes the ONE
 * navigation surface: the swipe-up full-screen [AppDrawer] (D-14).
 *
 * ## Navigation-Compose NavHost (Phase 24 — plan 24-03)
 * The active destination is driven by a [NavHost] (replaces the old hand-rolled `when(dest)` hub-and-spoke
 * holder). [rememberNavController] owns the drill-down back-stack; destinations are type-safe
 * `@Serializable` [NavDest] objects. The start destination is derived from the dev-gated
 * [ShellNavState.startDest] seed (null in release → [NavDest.WaterfallHome]).
 *
 * ## Accepted regression: land-on-root after recovery Splash (FIX-3, owner-locked 2026-06-09)
 * After a reconnect/recovery Splash the user LANDS ON [NavDest.WaterfallHome] AND each in-screen
 * sub-nav RESETS to its hub. The [NavHost] is composition-local inside [AppShell] and decomposes
 * during the Splash (gate-above in [RootController]), so the drill-down back-stack is NOT preserved
 * across the recovery. [ShellNavState.applyEntryReset] clears the sub-nav on the next entry into
 * Macros/Calibration/FineTune. This is the deliberate, simpler path the owner accepted — it is NOT
 * a bug; executors and verifiers must EXPECT both behaviors.
 *
 * ## Session holder hoist
 * All ~20 session holders live ABOVE the [NavHost]. Each holder is `remember(store)`-keyed so a spine
 * rebuild (reconnect) re-keys it onto the new session. The four leak-cancel [DisposableEffect] blocks
 * (webcam/spool/console/macro) are preserved verbatim above the [NavHost].
 *
 * ## Overlays
 * All overlays (ScanSurface, PromptDialog, DevThemeCycler, and the printing-only fallback
 * [FloatingEStop] + Stop Confirm guard) float as Box siblings AFTER the [NavHost]. The fallback
 * e-stop is visible only on destinations that do not own a FocusFrame header dock.
 *
 * ## Settings are IN-SHELL destinations
 * App settings ([NavDest.AppSettings]) and printer settings ([NavDest.PrinterSettings]) are reached
 * via the System page ([NavDest.System]) and rendered here like any other destination.
 *
 * @param container the process-scoped service-locator (provides the live spine + theme + dispatcher).
 */
@Composable
fun AppShell(
    container: AppContainer,
    nav: ShellNavState,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val scope = rememberCoroutineScope()

    // ---- Navigation-Compose back-stack (Phase 24-03) -----------------------------------------------
    // The NavHost start destination is derived from the dev-gated nav.startDest seed (null in release →
    // WaterfallHome). Read ONCE into the startDestination param — NOT via a LaunchedEffect (RESEARCH Pitfall 2).
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    // ---- Keep-screen-on (§R2 step 1, 26.5-05) --------------------------------------------------------
    // Applied at the SHELL root via View.keepScreenOn — the Compose-idiomatic equivalent of
    // FLAG_KEEP_SCREEN_ON on the hosting window — because MainActivity is owned by sibling plan 26.5-04
    // this wave (file-ownership disjointness). Semantics: the flag holds while this view is attached and
    // the preference is ON; it clears LIVE on toggle-off (the keyed DisposableEffect re-runs) and on
    // dispose (shell decomposed → background/Splash), so the screen is never pinned awake outside the
    // running shell. Default TRUE — the dedicated-display use case.
    val keepScreenOn by container.keepScreenOn.collectAsStateWithLifecycle(initialValue = true)
    val rootView = LocalView.current
    DisposableEffect(rootView, keepScreenOn) {
        rootView.keepScreenOn = keepScreenOn
        onDispose { rootView.keepScreenOn = false }
    }

    // In-screen sub-nav aliases (D-01 holdouts — NOT promoted to NavHost routes).
    // macroShowSystem + macroPopupFor removed: Macros merged to a single FieldMode screen (25-05).
    // fineTuneGroup removed: Fine-Tune is now a flat single-screen (26-02), no sub-nav.
    // calibrationRoutine removed: D-07 (Phase 27) — NavHost back-stack is the single source of truth.

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
    val temperatureHolder = remember(store) {
        TemperatureHolder(
            scope = scope,
            store = store,
            traceStylePrefs = container.traceStylePrefs,
            activeProfileId = container.activeProfileId,
        )
    }
    val moveHolder = remember(store) { MoveHolder(scope = scope, store = store) }
    val extrudeHolder = remember(store) {
        ExtrudeHolder(scope = scope, store = store, pinnedExtrudeMacros = container.extrudeMacroPins)
    }
    val filesHolder = remember(fileBrowser, printerStateFlow) {
        FileBrowserHolder(scope = scope, client = fileBrowser, printerState = printerStateFlow)
    }
    val printerState by printerStateFlow.collectAsStateWithLifecycle()

    // ---- Webcam holder (10-07) ---------------------------------------------------------------------
    // The webcam-tile gate (D-08 + MEDIUM-4, 15.2-03/04): the drawer Webcam tile is LIVE only when the
    // CURRENT session enumerated ≥1 cam (D-08 capability) AND the app-global webcam toggle is on
    // (flipped on the App Settings screen; moved per-profile→app-global 2026-06-15). This reads
    // `container.webcamTileEnabled` (capability × app-global toggle) so the App Settings toggle
    // actually greys/lights this tile. webcamCount is still collected below for the holder's
    // decode-budget / default-cam pick.
    val webcamCount by container.webcamCount.collectAsStateWithLifecycle(initialValue = 0)
    val webcamEnabled by container.webcamTileEnabled.collectAsStateWithLifecycle(initialValue = false)
    // The live per-session cam enumeration + the persisted connection config (host/port → the D-09
    // URL-resolution base + the per-printer preferred-cam key). An idle fallback keeps the holder
    // constructible while no session/config exists (it simply enumerates no cams → never drives a feed).
    // WR-02 fix (25-06): read from container.activeConfig (the live Phase-14 source — combines the active
    // profile's host/port/apiKey) instead of the write-dead connectionStore.config. This was the root cause
    // of the Phase-15 webcam-screen crash (empty host → invalid URL → IllegalArgumentException); the crash
    // was gracefully fixed in 49f3fe2 but the stale-source read remained. activeCfg is now nullable
    // (ConnectionConfig?) — downstream consumers use activeCfg?.host ?: "" for null safety.
    val webcams = spine?.webcams ?: remember { MutableStateFlow(emptyList<works.mees.jiib.state.Webcam>()) }
    val activeCfg by container.activeConfig.collectAsStateWithLifecycle(initialValue = null)
    // D-06: the per-printer preferred-cam pref keys on the ACTIVE PROFILE ID (not the host) so two
    // same-host profiles keep distinct preferred cams. The feed URLs still resolve off `activeCfg`;
    // only the pref KEY moves to the profile id. Empty string when no active profile (the webcam
    // surface is only reachable with an active printer, so the empty-suffixed key is rarely hit).
    // D-01 hoist (22-07): these were inline `.map{}` expressions that created new un-memoized Flow objects
    // on every shell recomposition; now collected from process-scoped AppContainer StateFlows (stable singletons).
    val activeProfileId by container.activeProfileId.collectAsStateWithLifecycle()
    // D-03 active-printer indicator: the active profile's display name → the Devices drawer-tile subtitle.
    val activeName by container.activeName.collectAsStateWithLifecycle()
    // A downscale hint for the MJPEG decode (MjpegDecodePolicy) — the full-screen px (the feed fills the
    // Focus). 10-08 pins the on-device sample step; this only sizes the decode budget, not correctness.
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val viewWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }.toInt().coerceAtLeast(1)
    val viewHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }.toInt().coerceAtLeast(1)
    // Build the Bitmap-bound webcam holder re-keyed on the live per-session store (the MoveHolder
    // precedent) AND the connection host (a printer swap re-resolves URLs + the preferred-cam key). The
    // holder owns the long-lived decode/poll/retry loops; the shell binds page-visibility (below) and
    // cancels on spine rebuild (the WR-01 leak-cancel — a reconnect MUST tear down the old loops or they
    // leak + keep streaming after the session swapped). Process-scoped client/prefs survive reconnects.
    // Phase 21: the H.264 rung's SurfaceView bridge — re-keyed WITH the holder so a printer/config swap
    // gets a fresh provider (the old one is abandoned with the old holder; the host re-registers on
    // recompose). The Media3SurfaceHost (rendered for H.264 cams in WebcamScreen) registers its SurfaceView
    // here; the composite feed (built into the holder below) awaits it before attaching the player.
    // CR-01: view-px (screenWidthDp/HeightDp) are DELIBERATELY EXCLUDED from these keys — they swap on
    // orientation change, and re-keying here would tear down + rebuild the ExoPlayer on every rotation
    // (the player-thrash / black-feed bug). The player must SURVIVE rotation; the Media3SurfaceHost's
    // SurfaceView swap is handled by the feed's lifetime surface-collect (re-attach). The view-px below
    // are captured once (first orientation) and feed only the MJPEG-fallback downscale, which tolerates
    // stale px safely (it can only over-downscale, never an OOM risk on the 2 GB floor).
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val webcamSurfaceProvider = remember(store, activeCfg?.host ?: "", activeProfileId) {
        Media3SurfaceProvider()
    }
    val webcamHolder: WebcamHolder<Bitmap> = remember(store, activeCfg?.host ?: "", activeProfileId) {
        webcamMedia3Holder(
            scope = scope,
            webcams = webcams,
            webcamPrefs = container.webcamPrefs,
            cfg = activeCfg ?: ConnectionConfig(host = ""),
            profileId = activeProfileId ?: "",
            sharedClient = container.webcamHttpClient,
            viewWidthPx = viewWidthPx,
            viewHeightPx = viewHeightPx,
            context = appContext,
            surfaceProvider = webcamSurfaceProvider,
        )
    }
    // WR-01 leak-cancel: when `remember(...)` swaps the holder on a spine rebuild (reconnect) or a config
    // change, fully tear down the old holder's decode/poll/retry loops — otherwise they leak + keep
    // streaming after the session swapped (this project's frozen-feed-after-restart history makes this
    // load-bearing). onDispose fires when [webcamHolder] re-keys. (start/stop is page-level, below; this
    // cancel is holder-death-level — the screen's own DisposableEffect handles the page-visible surface.)
    DisposableEffect(webcamHolder) { onDispose { webcamHolder.cancel() } }
    // Page-visible lifecycle (SC-3/D-13): the decode/poll/retry loops run ONLY while the Webcam page is
    // the active dest AND the process is foreground (STARTED). repeatOnLifecycle(STARTED) covers the
    // screen-off/home-button case (auto-cancel on STOPPED); keying the effect on [navBackStackEntry] means
    // nav-AWAY (back-stack entry changes away from NavDest.Webcam) cancels the effect → stop(). No background
    // decode, no leaked stream. (The screen's own DisposableEffect also starts/stops; this shell binding is
    // the authoritative foreground gate — both compose cleanly: a stop() is idempotent.)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(webcamHolder, navBackStackEntry, lifecycleOwner) {
        val isWebcam = navBackStackEntry?.destination?.isRoute<NavDest.Webcam>() == true
        if (isWebcam) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                webcamHolder.start()
                try {
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    webcamHolder.stop()
                }
            }
        }
    }

    // ---- Spool holder + capability gate (11-06) ----------------------------------------------------
    // The D-02 capability greyed-gating signal: the drawer Spool tile is LIVE only when the CURRENT
    // session's printer has the Moonraker `spoolman` component (false while idle). Collected here and
    // threaded into AppDrawer below — the SAME shape webcamEnabled plays for the Webcam tile.
    val spoolEnabled by container.spoolmanPresent.collectAsStateWithLifecycle(initialValue = false)
    // The per-session Spool picker holder, re-keyed on the live store (the MoveHolder/webcamHolder
    // precedent) so a spine rebuild (reconnect) re-points it at the new session's inventory client +
    // active-spool flow. The inventory reader is the session's lean SpoolmanClient (an idle fallback
    // no-op keeps it constructible while no session exists — every read → null → empty); the active-spool
    // truth is the session's StateFlow (an idle MutableStateFlow(null) while no session).
    val spoolmanClient = spine?.spoolmanClient ?: remember { object : works.mees.jiib.spool.SpoolmanClient {} }
    val activeSpoolFlow = spine?.activeSpool
        ?: remember { MutableStateFlow<works.mees.jiib.spool.SpoolmanStatus?>(null) }
    val spoolHolder = remember(store) { SpoolHolder(scope = scope, client = spoolmanClient, activeSpool = activeSpoolFlow) }
    // Cancel this holder's detached collector when `remember(store)` swaps it on a spine rebuild
    // (reconnect) — otherwise the discarded holder leaks its collector until the shell leaves
    // composition, compounding per reconnect (CR-01). Mirrors ConsoleHolder / MacroHolder pattern.
    DisposableEffect(spoolHolder) { onDispose { spoolHolder.cancel() } }
    // The live active-spool status the Files print-start gate reads (D-01) — the D-10-reconciled truth.
    val activeSpoolStatus by activeSpoolFlow.collectAsStateWithLifecycle()
    // 18.3-04 (D-06.2): the live active-spool DETAIL (color-bearing) for the drawer Spool tile's reactive
    // glyph. Resolve to swatches via the shared parser — Spoolman-active-color → empty spool ONLY (no gcode
    // middle tier on the drawer; printMetadata is deliberately NOT threaded here — owner's simpler-diff
    // narrowing of D-07). A null detail / malformed hex → empty list → the honest empty spool (D-03).
    val activeSpoolDetail by spoolHolder.activeSpoolDetail.collectAsStateWithLifecycle()
    // D-01 stabilize (22-07): was an inline List<Color> allocation on every recomposition, which is
    // unstable (Compose sees a different reference each frame and skips no re-draw). Wrapped in
    // remember(activeSpoolDetail) + toImmutableList() so Compose can structurally skip AppDrawer when
    // the spool color hasn't changed. ImmutableList is stable per kotlinx-collections-immutable contract.
    val drawerSpoolSwatches: ImmutableList<Color> = remember(activeSpoolDetail) {
        activeSpoolDetail?.filament?.colorSwatches.orEmpty()
            .mapNotNull(::parseNormalizedHex)
            .toImmutableList()
    }

    // ---- Outputs holder + capability gate (19-07) --------------------------------------------------
    // The D-10 capability HIDE signal: the drawer Output tile is SHOWN only when the CURRENT session's
    // printer reports ≥1 controllable output (false while idle). Spine-scoped (AppContainer.outputsPresent
    // derives off the live store's outputDescriptors via flatMapLatest), so it idles to false on disconnect/
    // printer-switch — never stale process state. Collected here and threaded into AppDrawer below; UNLIKE
    // webcamEnabled/spoolEnabled (which GREY their tiles) this drives the HIDE-not-grey filter (D-10).
    val outputsEnabled by container.outputsPresent.collectAsStateWithLifecycle(initialValue = false)
    // The per-session Outputs holder, re-keyed on the live store (the MoveHolder/calibration precedent) so a
    // spine rebuild (reconnect) re-points it at the new session's descriptors + live values. While idle the
    // empty fallback store backs it (no descriptors → an empty list). The holder is dispatch-free; the detail
    // pages source the dispatcher from `container.dispatcher` (== spine?.dispatcher) themselves.
    val outputsHolder = remember(store) { OutputsHolder(scope = scope, store = store) }

    // ---- System Information holder (20-04) ----------------------------------------------------------
    // The dedicated per-session read-only host-telemetry holder (off the printer hot path — host CPU
    // telemetry is NOT Klipper state, 20-03 Q2). Published on its OWN AppContainer slot (NOT carried on
    // the SpineHandle — its procStatUpdates dependency lives on the JsonRpcClient), so it is collected
    // directly here rather than re-keyed off `store`. Null while idle → the screen renders the degraded
    // all-"—" state. The screen dispatches nothing back (read-only).
    val systemInfoHolder by container.systemInfoHolder.collectAsStateWithLifecycle()

    // ---- Calibration holders (09-07) ---------------------------------------------------------------
    // The five headless calibration holders, each built off the SAME live per-session store and re-keyed
    // when the spine rebuilds (reconnect), mirroring the Phase-5 control holders above. The dispatcher
    // Failure stream comes from the per-session dispatcher (`spine?.dispatcher?.events`) — null while
    // idle so the holders simply carry no error until a live session attaches; re-captured when `store`
    // swaps (a new session brings a new dispatcher). The two TiltHolders share ONE TiltScreen via an
    // applied-selector lambda (D-02) and each fold ONLY their own routine's failures via its dispatchKey.
    val calibrationHubHolder = remember(store) { CalibrationHubHolder(scope = scope, store = store, showUnsupportedTools = container.showUnsupportedTools) }
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
    val bedMeshHolder = remember(store) {
        BedMeshHolder(
            scope = scope,
            store = store,
            events = calibEvents,
            renderPrefs = container.bedMeshRenderPrefs,
            activeProfileId = container.activeProfileId,
        )
    }
    val probeCalibrateHolder = remember(store) {
        ProbeCalibrateHolder(scope = scope, store = store, events = calibEvents)
    }
    // D-01 move #2 (22-07): the four calibration *Vm collections are removed; TiltScreen/BedMeshScreen/
    // ProbeCalibrateScreen now take their holder directly and collect holder.vm internally (mirroring the
    // existing ScrewsTiltScreen pattern). The holder builds above (lines 366-385) stay in AppShell.

    // ---- Fine-Tune holder (17-06) ------------------------------------------------------------------
    // ONE FineTuneHolder per spine (re-keyed when the spine rebuilds (reconnect), mirroring the Phase-5
    // control + calibration holders above) feeds ALL FOUR Fine-Tune screens (Hub/Motion/Extrusion/
    // FwRetraction) so the D-15 whole-group state-flip busy lock is SHARED across the group sub-nav.
    // The holder CONSUMES the live per-session store; while idle the empty fallback store backs it (no
    // readbacks → dashes, no baselines → resets are no-ops). The screens source the dispatcher's live
    // in-flight set + Failure stream from `container.dispatcher` themselves (the holder is dispatch-free).
    val fineTuneHolder = remember(store) { FineTuneHolder(scope = scope, store = store) }

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

    // FIX 6 (16-06): AppShell projects a BOUNDED ≤3 error-line list into the Print-Status home — the
    // Terminal(Error) data path. The screen does NOT reach into the store itself; it renders these
    // pre-projected strings as plain text (T-16-06-04 injection mitigation). Filter the raw console
    // scrollback to ERROR severity, take the last 3, map to the raw message (the prefix-preserved line).
    val errorLines by consoleHolder.state
        .map { lines ->
            lines.asSequence()
                .filter { it.severity == works.mees.jiib.ui.console.ConsoleSeverity.ERROR }
                .map { it.rawMessage }
                .toList()
                .takeLast(3)
        }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Macro bookmarks/revealHidden are PROCESS-scoped and live on AppContainer.stateScope (WR-02 fix):
    // the prior stateIn(rememberCoroutineScope()) was cancelled when AppShell left composition (recovery
    // Splash), causing the cached StateFlows to go stale post-Splash. Reading the process-scoped
    // StateFlows directly from the container avoids any composition-scope lifecycle dependency.
    val bookmarksFlow = container.macroBookmarks
    val revealHiddenFlow = container.macroRevealHidden
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
    LaunchedEffect(macroHolder, store) {
        store.macroBodies.collect { macroHolder.setMacroBodies(it) }
    }
    LaunchedEffect(macroHolder, store) {
        store.macroDescriptions.collect { macroHolder.setMacroDescriptions(it) }
    }
    // The current session dispatcher (the macro Execution popup routes through it); null while idle.
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)

    // ---- Macro Prompt Protocol engine (12-05) ------------------------------------------------------
    // The spine-level PromptEngine: an INDEPENDENT collector on the live per-session gcode stream that
    // folds each parsed prompt action through the proven reducer into a StateFlow<PromptView>. Built off
    // the SAME live per-session store and re-keyed when the spine rebuilds (reconnect), exactly like the
    // calibration/control holders above; while idle the empty fallback store backs it (no prompts). The
    // dispatcher Failure stream is the per-session one (`spine?.dispatcher?.events` == [calibEvents]) — a
    // `prompt:`-keyed Failure folds into the overlay toast (latestPromptError). On a Connected→down edge
    // the engine closes the prompt LOCALLY and dispatches NOTHING (D-10 cross-client safety, on-device
    // gated in 12-05 Task 2). The engine owns the stable dispatch keys/params; the overlay below wires
    // the actual CommandDispatcher.dispatch.
    val promptEngine = remember(store) {
        PromptEngine(scope = scope, store = store, events = calibEvents)
    }
    val promptView by promptEngine.view.collectAsStateWithLifecycle()
    // Console backfill-failed flag: the server.gcode_store read failed on (re)connect. Best-effort —
    // surfaced as the non-blanking "History unavailable" notice (WR-04). Re-keyed when the spine
    // rebuilds so a reconnect re-points at the new session's store flag.
    val consoleBackfillFailed by store.consoleBackfillFailed.collectAsStateWithLifecycle()

    // ---- App-level fallback e-stop guard state ------------------------------------------------------
    // The FloatingEStop + Stop Confirm guard are AppShell-level Box siblings above the NavHost, but the
    // visible affordance is gated to destinations without a FocusFrame header dock.
    var showEstopGuard by remember { mutableStateOf(false) }

    // ---- BackHandler priority (FIX-2, corrected by 27-review CR-01) --------------------------------
    // OnBackPressedDispatcher fires the MOST RECENTLY REGISTERED enabled callback. Compose BackHandlers
    // register in composition order, and enabled-ness does NOT reorder priority — registration order
    // does. NavHost (in the BoxWithConstraints below) registers its own internal back handler that pops
    // drill-down whenever the back stack is deeper than root, so any overlay handler composed BEFORE
    // the NavHost would LOSE to that internal pop (the CR-01 priority inversion: Back with a scan/prompt
    // overlay open popped the route UNDERNEATH the overlay, re-opening the D-09 abandon-live-probe hole).
    //
    // Therefore the scan/prompt overlay Back interception lives INSIDE the overlay `if` blocks — Box
    // siblings composed AFTER the NavHost — so a visible overlay consumes Back before NavHost can pop.
    // Net priority, lowest → highest:
    //   1. NavHost internal pop (drill-down Back when no overlay is visible).
    //   2. In-screen BackHandlers inside composable<> lambdas (e.g. the D-09 probe gate) — they
    //      register AFTER NavHost's internal handler during the destination's composition.
    //   3. The scan/prompt overlay handlers (composed after the whole NavHost — see the overlay blocks).
    //
    // The old generic "pop backStack" BackHandler is REMOVED — NavHost now owns drill-down Back.
    // Macro BackHandlers for popup/system-list REMOVED: Macros merged to a single FieldMode screen (25-05);
    // in-screen Back is handled by the screen's own FootButtonBar (MacroFieldMode state machine).
    // Calibration sub-state BackHandler REMOVED (D-07, Phase 27): NavHost back-stack owns Calibration
    // sub-routes. The D-09 probe-session BackHandler is now inside composable<NavDest.CalibrationProbe>.
    // The scan/prompt overlay BackHandlers MOVED into their overlay `if` blocks after the NavHost (CR-01).

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(t.bg),
    ) {
        // ---- NavHost (replaces the old when(dest) hub-and-spoke) -----------------------------------
        // FIX-7: start destination derived from the dev-gated nav.startDest seed (null in release →
        // WaterfallHome). Read ONCE as the startDestination param — NOT via LaunchedEffect (Pitfall 2).
        // Each composable<NavDest.*> receives its already-hoisted holder as a parameter; navigation seams
        // call navController.navigate(NavDest.X); onBack calls navController.popBackStack().
        // In-screen sub-nav (Macros/Calibration/FineTune/Outputs) stays INSIDE its destination
        // composable (D-01 — NOT promoted to NavHost routes this phase).
        NavHost(
            navController = navController,
            startDestination = nav.startDest ?: NavDest.WaterfallHome,
        ) {
            composable<NavDest.WaterfallHome> {
                PrintStatusScreen(
                    container = container,
                    // ONE clean launcher shape (16-06): every Standby launcher tile dispatches a real NavDest
                    // via onNavigate; the System foot button navigates to NavDest.System (D-04/28-05).
                    onNavigate = { navController.navigate(it) },
                    // The active-spool card Scan action opens the 11-07 QR scan surface directly (D-12).
                    onScanSpool = { nav.scanActive = true },
                    // FIX 6: the bounded ≤3 ERROR-line projection (above) — the Terminal(Error) data path.
                    errorLines = errorLines,
                )
            }
            composable<NavDest.Temperature> {
                TemperatureScreen(
                    container = container,
                    holder = temperatureHolder,
                    activeSpoolDetail = activeSpoolDetail,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.Move> {
                MoveScreen(
                    container = container,
                    holder = moveHolder,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.Extrude> {
                ExtrudeScreen(
                    container = container,
                    holder = extrudeHolder,
                    activeSpoolDetail = activeSpoolDetail,
                    onBack = { navController.popBackStack() },
                    onOpenSpool = { navController.navigate(NavDest.Spool) },
                )
            }
            composable<NavDest.Files> {
                FilesScreen(
                    holder = filesHolder,
                    printerState = printerState,
                    httpBase = httpBase,
                    canStartPrint = capabilities.hasObject("virtual_sdcard"),
                    onBack = { navController.popBackStack() },
                    // D-01 warn-only print-start gate inputs (SPOOL-07): the capability gate, the
                    // D-10-reconciled active status, and the inventory reader the gate resolves the
                    // active-spool detail through. The gate is skipped entirely when spoolman is absent.
                    spoolmanPresent = spoolEnabled,
                    activeSpoolStatus = activeSpoolStatus,
                    spoolmanClient = spoolmanClient,
                    // D-04 gcode-aware prefilter: the gate's "Pick spool" seeds the picker from the file's
                    // filament_type[] (material) + filament_colors[] (color hint) and opens the Spool screen.
                    onPickSpoolForFile = { filamentType, filamentColors ->
                        nav.spoolPrefilter = SpoolPrefilterSeed(filamentType, filamentColors)
                        navController.navigate(NavDest.Spool)
                    },
                    // The gate's "Scan" opens the QR scan sub-surface (D-12), same as the Status card Scan.
                    onScanSpool = { nav.scanActive = true },
                    onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
                )
            }
            composable<NavDest.Macros> {
                // Entry reset: entering the Macros surface always starts on the Bookmarked launcher (FIX-3).
                // applyEntryReset clears macroShowSystem + macroPopupFor (ShellNavState legacy fields, still
                // zeroed for Splash-recovery safety even though they are no longer read from AppShell).
                LaunchedEffect(Unit) { nav.applyEntryReset(NavDest.Macros) }

                // Merged Macros screen (25-05 / D-09): ONE screen with two field modes (Launcher /
                // ManageMode) replacing BookmarkedMacrosScreen + SystemMacrosScreen +
                // MacroExecutionPopup. The selected macro fills the Focus and Execute is a foot button.
                // MacroPrefs writes route through AppContainer.writeScope intent methods (WR-08 —
                // [[dinghy-compose-write-scope-cancellation]]: a composition-scoped launch is cancelled
                // by same-frame decomposition, silently dropping the write); session dispatcher passed
                // for the Execute/dispatch path; null-safe (Execute disabled while idle, WR-03).
                BookmarkedMacrosScreen(
                    holder = macroHolder,
                    dispatcher = dispatcher,
                    onToggleBookmark = container::toggleMacroBookmark,
                    onSetRevealHidden = container::setMacroRevealHidden,
                    onBack = { navController.popBackStack() },
                    isPrinting = printerState.printState == PrintState.Printing ||
                        printerState.printState == PrintState.Paused,
                    onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
                )
            }
            composable<NavDest.Console> {
                ConsoleScreen(
                    holder = consoleHolder,
                    onBack = { navController.popBackStack() },
                    backfillFailed = consoleBackfillFailed,
                    isPrinting = printerState.printState == PrintState.Printing ||
                        printerState.printState == PrintState.Paused,
                    onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
                )
            }
            // D-07 (Phase 27): Calibration sub-nav converted to real NavHost routes.
            // SIX composable<> blocks replace the old when(calibrationRoutine) dispatch.

            composable<NavDest.CalibrationHub> {
                // Entry reset: entering the hub always resets sub-nav for FIX-3 symmetry (no-op body
                // since calibrationRoutine was removed, but kept for future-proofing and documentation).
                LaunchedEffect(Unit) { nav.applyEntryReset(NavDest.CalibrationHub) }
                CalibrationHubScreen(
                    holder = calibrationHubHolder,
                    container = container,
                    onOpen = { routine -> navController.navigate(routine.toNavDest()) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.CalibrationProbe> {
                // Collect the probe VM and inFlight set for the D-09 BackHandler gating.
                val vm by probeCalibrateHolder.vm.collectAsStateWithLifecycle()
                val inFlight by remember(dispatcher) {
                    dispatcher?.inFlight ?: MutableStateFlow(emptySet())
                }.collectAsStateWithLifecycle(initialValue = emptySet())
                val starting = vm.state == works.mees.jiib.calibration.ProbePageState.Idle &&
                    ("probe_calibrate" in inFlight || "z_endstop_calibrate" in inFlight)
                // D-09 BackHandler: swallow system Back while a probe session is Active OR starting.
                // GATED to require no overlay visible (D-09, Codex WARNING-6). After CR-01 (27-review)
                // the scan/prompt overlay handlers are composed AFTER the whole NavHost and therefore
                // out-prioritize this one whenever an overlay is visible — the overlay conditions here
                // are belt-and-braces so this gate's enabled-ness MATCHES the actual dispatch priority.
                // nav.scanActive and promptView.visible are captured from the outer AppShell.
                BackHandler(
                    enabled = (vm.state == works.mees.jiib.calibration.ProbePageState.Active || starting) &&
                        !nav.scanActive && !promptView.visible
                ) {
                    // Intentionally swallow — abandoning a live probe nozzle descent is unsafe.
                }
                ProbeCalibrateScreen(
                    container = container,
                    holder = probeCalibrateHolder,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.CalibrationBedMesh> {
                BedMeshScreen(
                    container = container,
                    holder = bedMeshHolder,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.CalibrationScrewsTilt> {
                ScrewsTiltScreen(
                    container = container,
                    holder = screwsTiltHolder,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.CalibrationZTilt> {
                TiltScreen(
                    container = container,
                    holder = zTiltHolder,
                    variant = TiltVariant.ZTilt,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.CalibrationQgl> {
                TiltScreen(
                    container = container,
                    holder = qglHolder,
                    variant = TiltVariant.Qgl,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.FineTune> {
                // 26-02: flat single-screen Fine-Tune (replaces Hub + 3 group sub-pages).
                // No entry reset needed — there is no sub-nav state left to clear.
                FineTuneScreen(
                    holder = fineTuneHolder,
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.Webcam> {
                WebcamScreen(
                    holder = webcamHolder,
                    surfaceProvider = webcamSurfaceProvider,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.Spool> {
                SpoolScreen(
                    holder = spoolHolder,
                    dispatcher = dispatcher,
                    client = spoolmanClient,
                    container = container,
                    // Home foot-button pops to the existing WaterfallHome root (never pushes a duplicate).
                    onHome = { navController.popBackStack<NavDest.WaterfallHome>(inclusive = false) },
                    // Open the 11-07 QR scan sub-surface as a full-screen overlay (rendered below, outside
                    // the NavHost — mirrors the macro Execution popup). The camera binds/releases there.
                    onScan = { nav.scanActive = true },
                    // D-04 gcode-aware prefilter seed carried over from a Files spool-warning "Pick spool"
                    // (null on a plain drawer open). SpoolScreen seeds the picker filters ONCE then clears it.
                    prefilter = nav.spoolPrefilter,
                    onPrefilterConsumed = { nav.spoolPrefilter = null },
                )
            }
            composable<NavDest.Outputs> {
                // Outputs screen (26-05 D-18/D-19): Detail-in-Focus layout — list + per-output inline
                // control in the Focus region. No back-stack entries; selection state lives in OutputsScreen.
                OutputsScreen(
                    holder = outputsHolder,
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.SystemInfo> {
                // NavDest.SystemInfo (Phase 20): the read-only printer-host health page. Back-only gutter;
                // the drawer is suppressed on-screen (swipe-suppress set above).
                SystemInformationScreen(
                    holder = systemInfoHolder,
                    isPrinting = printerState.printState == PrintState.Printing ||
                        printerState.printState == PrintState.Paused,
                    onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.Power> {
                // NavDest.Power (Task A): Power / Reset page reached from Printer Settings.
                // Mirrors SystemInfo wiring — reuses the systemInfoHolder's identity flow for
                // host-action availability. FocusFrame docks the e-stop (page reachable mid-print).
                PowerResetScreen(
                    holder = systemInfoHolder,
                    dispatcher = dispatcher,
                    isPrinting = printerState.printState == PrintState.Printing ||
                        printerState.printState == PrintState.Paused,
                    onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.PrinterSettings> {
                PrinterSettingsScreen(
                    container = container,
                    onNavigate = { navController.navigate(it) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.HeatPresets> {
                HeatPresetsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.IncrementValues> {
                IncrementValuesScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.ManagePrinters> {
                PrintersScreen(
                    container = container,
                    onSwitched = { navController.popBackStack<NavDest.PrinterSettings>(inclusive = false) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.Theme> {
                // NavDest.Theme (15.2-04 D-03): per-printer look editor. Done/Back pops to the caller.
                ThemeScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.AppSettings> {
                AppSettingsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<NavDest.System> {
                // NavDest.System (28-02/28-05, D-04): the replacement System cluster hub — brand Focus +
                // D-03 direct-tap dense rows (App Settings / Printer Settings / Manage Printers).
                // E-stop: System now docks it in its FocusFrame header (Focus-header law 2026-06-13) —
                // the shell float no longer fires here. Still absent from FOOT_GUN_DESTS:
                // the else -> null path in shouldPopToRoot covers System (pop-to-root never fires from here).
                SystemPageScreen(
                    container = container,
                    onNavigate = { navController.navigate(it) },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        // ---- D-04: Pop-to-root on print state change (shouldPopToRoot predicate from 24-01) -----------
        // When a print starts/ends, pop foot-gun destinations (Move/Extrude/Calibration) back to
        // WaterfallHome. Uses the pure shouldPopToRoot predicate from NavDest.kt (24-01 / PopToRootTest) —
        // single source of truth, host-tested. Temperature/Macros/FineTune/Console/Webcam are NOT in
        // FOOT_GUN_DESTS and are left undisturbed. inclusive=false (Pitfall 3); popBackStack is
        // idempotent so it no-ops at root (the double-nav guard).
        LaunchedEffect(printerState.printState) {
            val d = navBackStackEntry?.destination
            val currentNavDest: NavDest? = when {
                d == null -> null
                d.isRoute<NavDest.Move>() -> NavDest.Move
                d.isRoute<NavDest.Extrude>() -> NavDest.Extrude
                // D-07 (Phase 27): ALL SIX calibration routes mapped so pop-to-root fires from ANY
                // routine, not just the hub (Codex SHOW-STOPPER-2 / D-17).
                d.isRoute<NavDest.CalibrationHub>()         -> NavDest.CalibrationHub
                d.isRoute<NavDest.CalibrationProbe>()       -> NavDest.CalibrationProbe
                d.isRoute<NavDest.CalibrationBedMesh>()     -> NavDest.CalibrationBedMesh
                d.isRoute<NavDest.CalibrationScrewsTilt>()  -> NavDest.CalibrationScrewsTilt
                d.isRoute<NavDest.CalibrationZTilt>()       -> NavDest.CalibrationZTilt
                d.isRoute<NavDest.CalibrationQgl>()         -> NavDest.CalibrationQgl
                // Non-foot-gun destinations — shouldPopToRoot returns false for these.
                else -> null
            }
            // shouldPopToRoot is the 24-01 pure predicate (FIX-8 — no NavController in the test).
            // It returns true iff currentNavDest is in FOOT_GUN_DESTS (non-null check already in predicate).
            if (shouldPopToRoot(currentNavDest, printActive = printerState.printState == PrintState.Printing)) {
                navController.popBackStack<NavDest.WaterfallHome>(inclusive = false)
            }
        }

        // ---- Overlays: Box siblings AFTER NavHost (render above every destination) -----------------

        // MacroExecutionPopup REMOVED (25-05): there is no longer a param-entry popup. The selected
        // macro's params are entered in the Focus inside BookmarkedMacrosScreen, and Execute is a foot
        // button. The PROMPT-protocol overlay (D-13) below remains — it is independent of the macro
        // execution path and untouched here.

        // QR scan sub-surface overlay (11-07) — a full-screen camera scan floating over the Spool screen.
        if (nav.scanActive) {
            // CR-01 (27-review): composed AFTER the NavHost so an open scan overlay out-prioritizes
            // NavHost's internal pop — Back closes the scan (releasing the camera via ScanSurface's
            // onDispose) and returns to the underlying screen, instead of popping the route beneath it.
            BackHandler { nav.scanActive = false }
            ScanSurface(
                client = spoolmanClient,
                onConfirm = { id ->
                    dispatcher?.dispatch(CommandRegistry.spoolmanPostSpoolId, SetSpoolArgs(spoolId = id))
                    nav.scanActive = false
                },
                onUsePicker = {
                    nav.scanActive = false
                    navController.navigate(NavDest.Spool)
                },
                onBack = { nav.scanActive = false },
            )
        }

        // Macro Prompt overlay (12-05) — the full-screen PromptDialog hoisted OUTSIDE the NavHost so it
        // floats over ANY destination. Content/footer buttons + the always-present close fire gcode through
        // the SHARED CommandDispatcher under the engine's stable keys.
        if (promptView.visible) {
            // CR-01 (27-review): composed AFTER the NavHost (and after the scan handler above, so a
            // prompt over a scan still wins) — a visible Macro Prompt owns Back as an EXPLICIT user
            // dismissal. Dispatch `action:prompt_end` so the prompt closes via the echoed prompt_end
            // round-trip — NOT a local teardown. (Disconnect closes the prompt LOCALLY with NO dispatch.)
            BackHandler {
                dispatcher?.dispatch(
                    promptEngine.closeKey,
                    JsonRpcMethods.GCODE_SCRIPT,
                    promptEngine.scriptParamsFor(promptEngine.closeGcode),
                )
            }
            val inFlightKeys by (dispatcher?.inFlight
                ?: remember { MutableStateFlow(emptySet<String>()) })
                .collectAsStateWithLifecycle(initialValue = emptySet())
            val promptInFlight = inFlightKeys.any { it.startsWith("prompt:") }
            PromptDialog(
                view = promptView,
                httpBase = httpBase,
                onButton = { buttonIndex ->
                    val buttons = promptView.flattenContentButtons()
                    buttons.getOrNull(buttonIndex)?.let { btn ->
                        dispatcher?.dispatch(
                            promptEngine.buttonKey(buttonIndex),
                            JsonRpcMethods.GCODE_SCRIPT,
                            promptEngine.scriptParamsFor(btn.gcode),
                        )
                    }
                },
                onFooterButton = { footerIndex ->
                    promptView.footerButtons.getOrNull(footerIndex)?.let { fb ->
                        dispatcher?.dispatch(
                            promptEngine.footerKey(footerIndex),
                            JsonRpcMethods.GCODE_SCRIPT,
                            promptEngine.scriptParamsFor(fb.gcode),
                        )
                    }
                },
                onClose = {
                    dispatcher?.dispatch(
                        promptEngine.closeKey,
                        JsonRpcMethods.GCODE_SCRIPT,
                        promptEngine.scriptParamsFor(promptEngine.closeGcode),
                    )
                },
                errorText = promptEngine.latestPromptError,
                inFlight = promptInFlight,
            )
        }

        // Dev theme cycler overlay (15.2-02, D-08) — floats over EVERY destination.
        val devCyclerEnabled by container.devCyclerEnabled.collectAsStateWithLifecycle(initialValue = false)
        if (devCyclerEnabled) {
            val currentOverride by container.themeOverride.collectAsStateWithLifecycle(initialValue = null)
            val profiles by container.profileStore.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
            val profileIds = profiles.map { it.id }
            DevThemeCyclerOverlay(
                currentOverride = currentOverride,
                onCycleStyle = { container.updateThemeOverride { nextStyleOverride(it) } },
                onCycleSize = { container.updateThemeOverride { nextSizeOverride(it) } },
                onDismiss = { container.setThemeOverride(null) },
                modifier = Modifier.fillMaxSize(),
                printerLabel = activeName,
                printerSwitchable = profileIds.size >= 2,
                onCyclePrinter = {
                    nextProfileId(profileIds, activeProfileId)?.let { container.setActiveProfile(it) }
                },
            )
        }

        // FIX-1 (D-14): the printing-only FloatingEStop + Stop Confirm guard are AppShell-level Box
        // siblings AFTER the NavHost so the e-stop is reachable from any destination while printing.
        // It is visible when print state is Printing or Paused. Tap raises the shared full-screen
        // ConfirmGuard before issuing EMERGENCY_STOP (T-24-03-02 mitigation).
        // UnitGrid for sizing: derive U from the minimum dimension (portrait- and landscape-safe).
        // Focus-header law (2026-06-13): the canonical e-stop is now the FocusFrame HEADER DOCK — every
        // FocusFrame's start-icon slot morphs into the e-stop while printing. So nearly every destination
        // owns its e-stop (screenOwnsEstop below) and this shell-level float is the fallback for the ONE
        // non-FocusFrame destination: Webcam (full-bleed, the header exemption). Theme now owns its
        // e-stop via FocusFrame (D-14 rework). ONE owner per destination.
        // The shell float is also fail-CLOSED: even if route matching ever misses a header-owning screen,
        // it still only appears on destinations explicitly known to lack a FocusFrame header.
        val estopDest = navBackStackEntry?.destination
        val screenOwnsEstop = estopDest != null && (
            estopDest.isRoute<NavDest.WaterfallHome>() ||
            estopDest.isRoute<NavDest.FineTune>() ||
            estopDest.isRoute<NavDest.Temperature>() ||
            estopDest.isRoute<NavDest.Spool>() ||
            estopDest.isRoute<NavDest.Files>() ||
            estopDest.isRoute<NavDest.Outputs>() ||
            estopDest.isRoute<NavDest.Console>() ||
            estopDest.isRoute<NavDest.Extrude>() ||
            estopDest.isRoute<NavDest.Move>() ||
            estopDest.isRoute<NavDest.Macros>() ||
            estopDest.isRoute<NavDest.CalibrationHub>() ||
            estopDest.isRoute<NavDest.CalibrationBedMesh>() ||
            estopDest.isRoute<NavDest.CalibrationProbe>() ||
            estopDest.isRoute<NavDest.CalibrationScrewsTilt>() ||
            estopDest.isRoute<NavDest.CalibrationZTilt>() ||
            estopDest.isRoute<NavDest.CalibrationQgl>() ||
            estopDest.isRoute<NavDest.System>() ||
            estopDest.isRoute<NavDest.SystemInfo>() ||
            estopDest.isRoute<NavDest.AppSettings>() ||
            estopDest.isRoute<NavDest.PrinterSettings>() ||
            estopDest.isRoute<NavDest.Power>() ||
            estopDest.isRoute<NavDest.ManagePrinters>() ||
            estopDest.isRoute<NavDest.Theme>()
        )
        val screenUsesShellEstop = estopDest != null && !screenOwnsEstop && (
            estopDest.isRoute<NavDest.Webcam>()
        )
        val estopGrid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        FloatingEStop(
            visible = screenUsesShellEstop &&
                (printerState.printState == PrintState.Printing || printerState.printState == PrintState.Paused),
            onClick = { showEstopGuard = true },
            // R1 hold-parity: the retired gutter StopButton carried HOLD = fire IMMEDIATELY (the
            // panic path, no guard — Matthew). The shell e-stop keeps that semantic app-wide.
            onHold = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
            uDp = estopGrid.uDp,
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
        )
        if (showEstopGuard) {
            ConfirmGuard(
                title = stringResource(R.string.printstatus_estop_guard_title),
                message = stringResource(R.string.printstatus_estop_guard_message),
                confirmLabel = stringResource(R.string.printstatus_estop_guard_confirm),
                cancelLabel = stringResource(R.string.common_cancel),
                onConfirm = {
                    dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit)
                    showEstopGuard = false
                },
                onCancel = { showEstopGuard = false },
                destructive = true,
            )
        }
    }
}

/**
 * Type-safe route check for navigation-compose 2.8.x. Use Navigation's serializer/id based matcher
 * instead of parsing [NavDestination.route] text; typed route strings are an implementation detail and
 * can drift across Navigation/Kotlin serialization/R8 changes.
 */
private inline fun <reified T : NavDest> androidx.navigation.NavDestination.isRoute(): Boolean =
    with(NavDestination.Companion) { this@isRoute.hasRoute<T>() }
