package works.mees.dinghy.ui.printstatus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.PrintStartArgs
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.preview.PreviewPlaceholderBox
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.render.ProgressRing
import works.mees.dinghy.state.HeaterState
import works.mees.dinghy.state.PrintMetadata
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.thumbnailUrl
import works.mees.dinghy.spool.SpoolmanSpool
import works.mees.dinghy.spool.parseSpoolmanSpools
import works.mees.dinghy.ui.spool.ActiveSpoolCard
import works.mees.dinghy.ui.spool.ActiveSpoolCardState
import works.mees.dinghy.ui.spool.deriveActiveSpoolCardState
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.theme.seriesColor
import works.mees.dinghy.ui.route.Dest

/**
 * The Print Status home (SHELL-04) — the primary monitor surface (≈90% of interaction). Built on
 * [ScreenScaffold]; all color via [LocalTokens] (THEME-01); live numbers in GeistMono tabular numerals.
 *
 * Data is read STRICTLY from fields confirmed present in docs/moonraker-capabilities.md (real Ender 5 +
 * Ender 3) — no assumed fields. Layer info is nullable (slicer/state-dependent) → "—" fallback.
 *
 * ## Four-state surface (Phase 16) — routed off [classifyPrintStatus]
 * The screen renders FROM the pure [uiModel] ([PrintStatusUiModel]) per the classified
 * [PrintStatusMode] (Standby / Printing / Paused / Terminal). Each mode renders its own
 * Focus / Field / Gutter by RECOMPOSING the harvested primitives (ProgressRing, StatGrid, StopButton,
 * PrintStatusControlTile, ConfirmGuard, PresetSelector):
 *  - **Standby:** app-icon Focus + minimal glance overlay (Nozzle/Bed + [selectGlanceSensor] glance
 *    temp + active-spool remaining) · the adaptive launcher grid (Drawer = flexible/growing tile, every
 *    other tile dispatches a real [Dest] via [onNavigate]) · Preheat (spool-aware via [selectPreheatPath])
 *    + inert Power gutter — no E-Stop.
 *  - **Printing:** the 03-print-status composition ([PrintStatusFocus]) · ONE framed [StatGrid] (incl.
 *    the Applied-Z-offset row) + the shortcut row OR the babystep 3-cell row (early-layer window) ·
 *    Pause / Cancel / E-Stop.
 *  - **Paused:** the Printing focus DIMMED + a pause overlay · same Field/toolset · Resume / Cancel.
 *  - **Terminal:** a clean hero ([TerminalFocus], no ring/dim) · the stats frame (live-only fields →
 *    "—") · Dismiss / Reprint; Terminal(Error) appends the AppShell-projected ≤3 [errorLines].
 *
 * @param container the service-locator (live `printerState` + the session dispatcher).
 * @param onNavigate launcher/forward-nav seam — every Standby launcher tile dispatches a real [Dest].
 * @param onOpenDrawer opens the swipe-up App Drawer (the flexible Drawer launcher tile).
 * @param onScanSpool opens the QR scan surface (the active-spool card Scan action).
 * @param errorLines the bounded ≤3 ERROR-line projection AppShell passes for Terminal(Error).
 */
@Composable
fun PrintStatusScreen(
    container: AppContainer,
    onNavigate: (Dest) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onScanSpool: () -> Unit = {},
    errorLines: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    // Launcher/forward-nav seams (16-06): the Standby launcher tiles dispatch onNavigate(Dest.*) /
    // onOpenDrawer(); the active-spool card Change/Open routes to the Spool screen. Local aliases keep
    // the existing card-wiring below readable while Tasks 2-4 fill the Standby/Terminal bodies.
    val onOpenFiles: () -> Unit = { onNavigate(Dest.Files) }
    val onOpenSpool: () -> Unit = { onNavigate(Dest.Spool) }
    val state by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val metadata by container.printMetadata.collectAsStateWithLifecycle(initialValue = null)
    val lastJob by container.lastJob.collectAsStateWithLifecycle(initialValue = null)
    val httpBase by container.httpBase.collectAsStateWithLifecycle(initialValue = "")
    // Per-heater capability gate (16-06): the Preheat per-temp dispatch is gated on each heater being
    // present (a bed temp is NEVER routed to an absent heater_bed). Read the live capabilities.
    val capabilities by container.capabilities.collectAsStateWithLifecycle(initialValue = works.mees.dinghy.state.Capabilities())
    // Babystep app setting (16-05): the early-layer babystep row shows only while enabled AND the print
    // is within the configured first-N-layer window. Process-scoped (survives reconnects).
    val babystepEnabled by container.babystepEnabled.collectAsStateWithLifecycle(initialValue = true)
    val babystepLayers by container.babystepLayers.collectAsStateWithLifecycle(initialValue = 5)
    // Bookmarked-macros gate (16-06): the Macros launcher tile appears on Standby ONLY if the user has
    // bookmarked at least one macro (UI-SPEC launcher order). Process-scoped pref.
    val bookmarkedMacros by container.macroPrefs.bookmarks.collectAsStateWithLifecycle(initialValue = emptySet())

    // ---- Active-spool card (SPOOL-02, 11-06) -------------------------------------------------------
    // The D-03 card reads the capability gate + the D-10-reconciled active status; the spool DETAIL is
    // resolved once-per-id via the session's lean SpoolmanClient (a best-effort getSpool — a rejected/
    // absent read leaves the card in its Loading variant, never crashes). The whole card / its Change
    // action route to the Spool screen; Clear dispatches post_spool_id {} (D-13).
    val spoolmanPresent by container.spoolmanPresent.collectAsStateWithLifecycle(initialValue = false)
    val activeSpool by container.activeSpool.collectAsStateWithLifecycle(initialValue = null)
    var spoolDetail by remember { mutableStateOf<SpoolmanSpool?>(null) }
    val activeSpoolId = activeSpool?.activeSpoolId
    LaunchedEffect(activeSpoolId) {
        val id = activeSpoolId
        if (id == null) {
            spoolDetail = null
        } else {
            val envelope = container.currentSpoolmanClient?.let { runCatching { it.getSpool(id) }.getOrNull() }
            // The detail endpoint returns a SINGLE spool object inside the proxy-v2 envelope; reuse the
            // list parser (it tolerates an object response → empty) by wrapping the lone row, or fall back
            // to a one-row parse. parseSpoolmanSpools handles the array case; a bare object stays null
            // (the card keeps Loading) rather than crashing.
            spoolDetail = parseSpoolmanSpools(envelope).rows.firstOrNull { it.id == id }
                ?: parseSpoolDetail(envelope, id)
        }
    }
    val activeSpoolCardState = deriveActiveSpoolCardState(
        spoolmanPresent = spoolmanPresent,
        status = activeSpool,
        detail = spoolDetail,
    )

    var showEstopGuard by remember { mutableStateOf(false) }
    var showCancelGuard by remember { mutableStateOf(false) }
    var showPresetSelector by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PrintStatusPendingAction?>(null) }
    var failureText by remember { mutableStateOf<String?>(null) }
    // Babystep step size (16-06): the center cell shows it; tapping cycles via nextBabystepStep. Session
    // state — defaults to the first canonical step. Compress fires -step, Expand +step.
    var babystepStep by remember { mutableStateOf(works.mees.dinghy.command.PrinterCommands.BABYSTEP_STEPS.first()) }

    // The classified four-state mode (the phase's central routing axis) + the pure UI model the screen
    // renders FROM (launcher order, gutter set, active Field row, terminal-error flag).
    val mode = classifyPrintStatus(state)
    val babystepShown = babystepVisible(babystepEnabled, state.currentLayer, babystepLayers)
    val ui = uiModel(
        mode = mode,
        state = state,
        lastJob = lastJob,
        pendingAction = pendingAction,
        spoolmanPresent = spoolmanPresent,
        hasBookmarkedMacros = bookmarkedMacros.isNotEmpty(),
        babystepVisible = babystepShown,
    )
    // The restart filename (Terminal Reprint / restart-guard) still resolves through the 16-02 builder.
    val restartFilename = derivePrintStatusControls(state = state, lastJob = lastJob).restartFilename

    // Spool-aware Preheat (D-01): fire whichever heaters the active spool provides, EACH gated on its
    // capability, else fall through to the PresetSelector. Decision owned by the pure selectPreheatPath.
    fun runPreheat() {
        val path = selectPreheatPath(
            spoolmanPresent = spoolmanPresent,
            nozzleTemp = spoolDetail?.filament?.settingsExtruderTemp,
            bedTemp = spoolDetail?.filament?.settingsBedTemp,
        )
        when (path) {
            is PreheatPath.DirectTemps -> {
                // Per-temp setHeater for each NON-NULL temp the result carries, EACH capability-gated:
                // nozzle on `extruder`, bed on `heater_bed`. A null temp fires nothing (never 0).
                path.nozzle?.let { noz ->
                    if (capabilities.hasObject("extruder")) {
                        dispatcher?.dispatch(
                            CommandRegistry.setHeater,
                            works.mees.dinghy.command.SetHeaterArgs(heater = "extruder", target = noz),
                        )
                    }
                }
                path.bed?.let { bed ->
                    if (capabilities.hasObject("heater_bed")) {
                        dispatcher?.dispatch(
                            CommandRegistry.setHeater,
                            works.mees.dinghy.command.SetHeaterArgs(heater = "heater_bed", target = bed),
                        )
                    }
                }
            }
            PreheatPath.OpenSelector -> showPresetSelector = true
        }
    }

    LaunchedEffect(dispatcher) {
        failureText = null
        val d = dispatcher ?: return@LaunchedEffect
        d.events.collect { event ->
            when (event) {
                is DispatchEvent.Failure -> failureText = event.message
            }
        }
    }
    LaunchedEffect(failureText) {
        if (failureText != null) {
            delay(4_000)
            failureText = null
        }
    }
    LaunchedEffect(pendingAction, state.printState, state.printFilename) {
        val next = clearPrintStatusPendingAction(pendingAction, state)
        if (next != pendingAction) pendingAction = next
    }

    fun runAction(action: PrintStatusControlAction) {
        when (action) {
            PrintStatusControlAction.OpenFiles -> onOpenFiles()
            PrintStatusControlAction.RestartPrint -> {
                // Terminal Reprint (D-05): direct print-start of Moonraker's current/last file path, NO
                // ConfirmGuard, does NOT SDCARD_RESET_FILE first. Available when a usable path is exposed.
                restartFilename?.let { dispatcher?.dispatch(CommandRegistry.printStart, PrintStartArgs(it)) }
            }
            PrintStatusControlAction.Tune -> onNavigate(Dest.FineTune) // TUNE-01 / D-21 — opens the Fine-Tune Hub.
            PrintStatusControlAction.PausePrint -> {
                if (pendingAction == null) {
                    dispatcher?.dispatch(CommandRegistry.printPause, Unit)
                    pendingAction = PrintStatusPendingAction.Pause
                }
            }
            PrintStatusControlAction.ResumePrint -> {
                if (pendingAction == null) {
                    dispatcher?.dispatch(CommandRegistry.printResume, Unit)
                    pendingAction = PrintStatusPendingAction.Resume
                }
            }
            PrintStatusControlAction.GracefulCancel -> {
                if (pendingAction == null) showCancelGuard = true
            }
            PrintStatusControlAction.EmergencyStop -> {
                showEstopGuard = true
            }
            // Spool-aware Preheat (D-01) — selectPreheatPath owns the direct-vs-selector branch.
            PrintStatusControlAction.Preheat -> runPreheat()
            // Terminal Dismiss (D-05): SDCARD_RESET_FILE; a failure surfaces via the dispatcher toast
            // (the LaunchedEffect below). Neutral — clears, does not discard input.
            PrintStatusControlAction.Dismiss -> dispatcher?.dispatch(CommandRegistry.dismissPrint, Unit)
            // Power is INERT in P16 (D-04) — rendered as the red Power tile, no-op.
            PrintStatusControlAction.Power -> Unit
        }
    }

    Box(modifier.fillMaxSize()) {
        // The pure, container-free rendering surface (hoisted for the 18-05 preview anchor): the live
        // composable resolves all flow values + action lambdas above and passes them in; the
        // `PrintStatusScreen(state = …)` preview overload calls the SAME body with fixture state and
        // no-op callbacks (no Moonraker). Keeps this entry as the single layout source the previews and
        // the running app share — drift is impossible. The dispatcher-driven guards/PresetSelector stay
        // OUTSIDE the content (they're live-only modals, not part of the previewable scaffold).
        PrintStatusContent(
            mode = mode,
            state = state,
            metadata = metadata,
            httpBase = httpBase,
            ui = ui,
            errorLines = errorLines,
            babystepShown = babystepShown,
            spoolmanPresent = spoolmanPresent,
            activeSpoolCardState = activeSpoolCardState,
            babystepStep = babystepStep,
            failureText = failureText,
            pendingActionIsNull = pendingAction == null,
            hasBookmarkedMacros = bookmarkedMacros.isNotEmpty(),
            onRunAction = ::runAction,
            onEmergencyStopHold = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
            onBabystepCompress = { dispatcher?.dispatch(CommandRegistry.babystepZ, works.mees.dinghy.command.BabystepArgs(-babystepStep)) },
            onBabystepExpand = { dispatcher?.dispatch(CommandRegistry.babystepZ, works.mees.dinghy.command.BabystepArgs(babystepStep)) },
            onCycleBabystepStep = { babystepStep = nextBabystepStep(babystepStep) },
            onNavigate = onNavigate,
            onOpenDrawer = onOpenDrawer,
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
        if (showCancelGuard) {
            ConfirmGuard(
                title = stringResource(R.string.printstatus_cancel_guard_title),
                message = stringResource(R.string.printstatus_cancel_guard_message),
                confirmLabel = stringResource(R.string.printstatus_cancel_guard_confirm),
                cancelLabel = stringResource(R.string.printstatus_cancel_guard_keep),
                onConfirm = {
                    dispatcher?.dispatch(CommandRegistry.printCancel, Unit)
                    pendingAction = PrintStatusPendingAction.Cancel
                    showCancelGuard = false
                },
                onCancel = { showCancelGuard = false },
                destructive = true,
            )
        }
        // (No restart ConfirmGuard: Terminal Reprint dispatches printStart DIRECTLY with no guard, D-05.)
        // Spool-aware Preheat fallback (D-01): the now-internal Phase-5 PresetSelector (fixed
        // PLA/PETG/ABS/TPU, keyboard-free) — opened when selectPreheatPath returns OpenSelector.
        if (showPresetSelector) {
            val inFlight by (dispatcher?.inFlight ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>()) })
                .collectAsStateWithLifecycle(initialValue = emptySet())
            works.mees.dinghy.ui.temperature.PresetSelector(
                inFlight = inFlight,
                onPreset = { p ->
                    dispatcher?.dispatch(
                        CommandRegistry.applyPreset,
                        works.mees.dinghy.command.ApplyPresetArgs(nozzle = p.nozzle, bed = p.bed, key = "preset_${p.name}"),
                    )
                    showPresetSelector = false
                },
                onDismiss = { showPresetSelector = false },
            )
        }
    }
}

/**
 * The STATELESS Print-Status entry (18-05 preview anchor / D-01). Renders the four-state scaffold from a
 * plain [PrinterState] fixture with NO [AppContainer], NO dispatcher, NO Moonraker — the seam every
 * `@Preview` in [works.mees.dinghy.preview.PrintStatusPreviews] composes inside a `PreviewBox`. The mode
 * is derived from the fixture's `printState` via [classifyPrintStatus] (the same print-state-only
 * discipline the live screen uses), so `SampleFixtures.forMode(mode)` reproduces every screen state.
 *
 * This is the hoisted-state half of the live [PrintStatusScreen] `container` overload: both delegate to
 * the shared [PrintStatusContent], so a preview renders byte-identical layout to the running app. All
 * action callbacks default to no-ops (a preview never dispatches); pass values only where a preview wants
 * to exercise a branch (e.g. [errorLines] for Terminal(Error)).
 */
@Composable
fun PrintStatusScreen(
    state: PrinterState,
    metadata: PrintMetadata? = null,
    httpBase: String = "",
    errorLines: List<String> = emptyList(),
    spoolmanPresent: Boolean = false,
    activeSpoolCardState: ActiveSpoolCardState = ActiveSpoolCardState.Unavailable,
    hasBookmarkedMacros: Boolean = false,
    babystepStep: Double = works.mees.dinghy.command.PrinterCommands.BABYSTEP_STEPS.first(),
    onNavigate: (Dest) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val mode = classifyPrintStatus(state)
    val babystepShown = false
    val ui = uiModel(
        mode = mode,
        state = state,
        lastJob = null,
        pendingAction = null,
        spoolmanPresent = spoolmanPresent,
        hasBookmarkedMacros = hasBookmarkedMacros,
        babystepVisible = babystepShown,
    )
    Box(modifier.fillMaxSize()) {
        PrintStatusContent(
            mode = mode,
            state = state,
            metadata = metadata,
            httpBase = httpBase,
            ui = ui,
            errorLines = errorLines,
            babystepShown = babystepShown,
            spoolmanPresent = spoolmanPresent,
            activeSpoolCardState = activeSpoolCardState,
            babystepStep = babystepStep,
            failureText = null,
            pendingActionIsNull = true,
            hasBookmarkedMacros = hasBookmarkedMacros,
            onRunAction = {},
            onEmergencyStopHold = {},
            onBabystepCompress = {},
            onBabystepExpand = {},
            onCycleBabystepStep = {},
            onNavigate = onNavigate,
            onOpenDrawer = onOpenDrawer,
        )
    }
}

/**
 * The pure, container-free four-state rendering surface shared by BOTH [PrintStatusScreen] overloads —
 * the live `container` entry (passing resolved flow values + real dispatch lambdas) and the stateless
 * preview entry (passing fixture state + no-op lambdas). Holds the gutter renderer, the active-print
 * Field, and the per-mode [ScreenScaffold] `when(mode)`. Carries NO `remember`/flow/dispatcher state —
 * every input arrives as a parameter so it renders identically under `@Preview` and at runtime.
 */
@Composable
private fun PrintStatusContent(
    mode: PrintStatusMode,
    state: PrinterState,
    metadata: PrintMetadata?,
    httpBase: String,
    ui: PrintStatusUiModel,
    errorLines: List<String>,
    babystepShown: Boolean,
    spoolmanPresent: Boolean,
    activeSpoolCardState: ActiveSpoolCardState,
    babystepStep: Double,
    failureText: String?,
    pendingActionIsNull: Boolean,
    hasBookmarkedMacros: Boolean,
    onRunAction: (PrintStatusControlAction) -> Unit,
    onEmergencyStopHold: () -> Unit,
    onBabystepCompress: () -> Unit,
    onBabystepExpand: () -> Unit,
    onCycleBabystepStep: () -> Unit,
    onNavigate: (Dest) -> Unit,
    onOpenDrawer: () -> Unit,
) {
    // The shared gutter renderer — drives all four modes from [ui.gutter] (the 16-02 per-mode set).
    val gutterContent: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ui.gutter.forEach { control ->
                val renderControl = control.copy(
                    enabled = control.enabled &&
                        (pendingActionIsNull ||
                            control.tapAction == PrintStatusControlAction.OpenFiles ||
                            control.tapAction == PrintStatusControlAction.EmergencyStop),
                )
                if (control.tapAction == PrintStatusControlAction.EmergencyStop) {
                    StopButton(
                        onTap = { onRunAction(PrintStatusControlAction.EmergencyStop) },
                        onHold = onEmergencyStopHold,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    PrintStatusControlTile(
                        control = renderControl,
                        onTap = { control.tapAction?.let(onRunAction) },
                        onHold = { control.holdAction?.let(onRunAction) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // The active-print Field: ONE framed StatGrid + the shortcut OR babystep row + optional Spoolman
    // line + the estop-failure toast. Shared by Printing AND Paused (Paused reuses the same toolset).
    val activeFieldContent: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatGrid(
                state = state,
                metadata = metadata,
                babystepWindow = babystepShown,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            // Optional Spoolman print line (informational; accent when available < required, D-1c).
            if (spoolmanPresent) {
                SpoolmanPrintLine(
                    cardState = activeSpoolCardState,
                    metadata = metadata,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // The shortcut row, OR the babystep 3-cell row inside the early-layer window.
            if (ui.activeRow == PrintStatusFieldRow.Babystep) {
                BabystepRow(
                    step = babystepStep,
                    onCompress = onBabystepCompress,
                    onExpand = onBabystepExpand,
                    onCycleStep = onCycleBabystepStep,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ShortcutRow(
                    spoolmanPresent = spoolmanPresent,
                    hasBookmarkedMacros = hasBookmarkedMacros,
                    onNavigate = onNavigate,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
        }
    }

    when (mode) {
        is PrintStatusMode.Standby -> ScreenScaffold(
            focus = {
                StandbyFocus(
                    state = state,
                    spoolmanPresent = spoolmanPresent,
                    activeSpoolCardState = activeSpoolCardState,
                )
            },
            field = {
                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LauncherGrid(
                        dests = ui.launcherDests,
                        onNavigate = onNavigate,
                        onOpenDrawer = onOpenDrawer,
                        modifier = Modifier.fillMaxSize().weight(1f),
                    )
                    failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
                }
            },
            gutter = gutterContent,
        )

        is PrintStatusMode.Printing -> ScreenScaffold(
            focus = { PrintStatusFocus(state = state, metadata = metadata, httpBase = httpBase) },
            field = { activeFieldContent() },
            gutter = gutterContent,
        )

        is PrintStatusMode.Paused -> ScreenScaffold(
            focus = { PrintStatusFocus(state = state, metadata = metadata, httpBase = httpBase, paused = true) },
            field = { activeFieldContent() },
            gutter = gutterContent,
        )

        is PrintStatusMode.Terminal -> ScreenScaffold(
            focus = { TerminalFocus(state = state, metadata = metadata, httpBase = httpBase) },
            field = {
                // Roomier padding than the cockpit grid — the Terminal summary breathes, and the
                // right-aligned values don't hug the screen edge (2026-06-06 UAT).
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Terminal field = a FINISHED-print summary LIST (not the live cockpit grid): the
                    // file, how long it ran, filament used, and how far it got (2026-06-06 UAT).
                    TerminalStatsList(
                        state = state,
                        metadata = metadata,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    // Terminal(Error) ONLY: the AppShell-projected ≤3 error lines (hidden if empty).
                    if (ui.showErrorLines && errorLines.isNotEmpty()) {
                        TerminalErrorLines(lines = errorLines, modifier = Modifier.fillMaxWidth())
                    }
                    failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
                }
            },
            gutter = gutterContent,
        )
    }
}

/**
 * Focus: the [ProgressRing] is ALWAYS drawn (gray track when idle — progress 0 shows only the
 * surface2 well; accent arc fills while printing). The ring CENTER is the "preview" slot — the live
 * % while printing, the Benchy no-job image when idle. Beneath: filename + Z/layer while printing,
 * else "Ready". Temps are NOT repeated here — they live in the field grid (Matthew, 2026-06-01).
 */
@Composable
private fun PrintStatusFocus(
    state: PrinterState,
    metadata: PrintMetadata? = null,
    httpBase: String = "",
    paused: Boolean = false,
) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val printing = state.printState == PrintState.Printing || state.printState == PrintState.Paused
    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        // The ring is ~90% of the focus's SMALLER dimension (largest circle that fits, both orientations).
        val ringSize = minOf(maxWidth, maxHeight) * 0.9f
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.size(ringSize)) {
                // Paused (16-06): the Printing composition DIMMED (alpha) with a static pause overlay.
                val dim = if (paused) Modifier.alpha(0.4f) else Modifier
                Box(dim.fillMaxSize()) {
                ProgressRing(
                    progress = if (printing) state.progress.toFloat() else 0f,
                    modifier = Modifier.fillMaxSize(),
                )
                // Preview slot: ~90% of the ring, circle-clipped (corners drop — preview isn't edge-to-edge).
                // Idle → the Benchy no-job image (theme-accent tinted). Printing → the gcode thumbnail
                // (Coil 3) when a metadata thumbnail URL is available, else the center stays EMPTY (the
                // ring + % still read — never show Benchy while printing).
                val thumbRel = metadata?.largestThumbRelPath
                val filename = state.printFilename
                Box(
                    Modifier.fillMaxSize(0.9f).align(Alignment.Center).clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!printing) {
                        Icon(
                            painter = painterResource(R.drawable.benchy),
                            contentDescription = null,
                            tint = t.accent2,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1600f / 900f),
                        )
                    } else if (thumbRel != null && httpBase.isNotBlank() && filename.isNotBlank()) {
                        // D-05/D-02 preview branch: a Coil AsyncImage never loads under @Preview
                        // (LocalInspectionMode) — render the labeled placeholder so the previewed ring
                        // center is not blank, else the live cleartext-LAN thumbnail load (coil-network-
                        // okhttp on the classpath, same NSC posture as the websocket/REST).
                        if (LocalInspectionMode.current) {
                            PreviewPlaceholderBox(label = "Thumbnail", modifier = Modifier.fillMaxSize())
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(thumbnailUrl(httpBase, filename, thumbRel))
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else if (filename.isNotBlank()) {
                        // No preview thumbnail available → the filename itself lives in the ring center,
                        // marquee-scrolling if it's too long to fit on one line (Matthew, 2026-06-01).
                        Text(
                            text = filename,
                            color = t.text,
                            fontFamily = GeistMono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = fsSp(18f, t.fs).sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp).basicMarquee(),
                        )
                    }
                }
                } // end dim wrapper
                // Status label rendered OUTSIDE the dim/alpha layer: in Paused that alpha graphicsLayer
                // CLIPS to its bounds, cutting off this label's 6-o'clock overhang (2026-06-06 UAT). The
                // outer ring Box doesn't clip, so here it stays fully visible AND full-opacity (readable in
                // Paused). Centered on the ring's 6-o'clock point (box-center + R): "Ready"/"NN%"/"PAUSED".
                Text(
                    text = if (state.printState == PrintState.Printing)
                        "${(state.progress * 100).roundToInt()}%"
                    else stringResource(statusLabelRes(state.printState)),
                    color = t.text,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(30f, t.fs).sp,
                    modifier = Modifier.align(Alignment.Center).offset(y = ringSize / 2),
                )
                // Static pause overlay (NOT dimmed) centered on the ring — the Focus carries the paused
                // state (UI-SPEC Accessibility: contentDescription "Print paused"). RING-RELATIVE (sized
                // from ringSize, not a fixed sp) so it scales with the focus and can NEVER crop, in any
                // orientation (2026-06-06 UAT: fixed glyph grew/cropped strangely). The glyph fills a
                // bounded box at 40% of the ring; the font size tracks that box's dp.
                if (paused) {
                    // Render the glyph WITHOUT a fixed-size Box: a font glyph's line box is ~1.17× its
                    // fontSize, so wrapping it in a `size(fontSize)` Box capped the Text height and shaved
                    // the circle's bottom (2026-06-06 UAT). Let the glyph size itself (no height cap)
                    // and just center it on the ring — sizeDp tracks the ring so it still scales/can't crop.
                    // DinghyIconView owns the a11y semantics (the cd is the sole spoken label).
                    DinghyIconView(
                        DinghyIcons.PauseCircle,
                        tint = t.text,
                        sizeDp = (ringSize.value * 0.4f).dp,
                        contentDescription = stringResource(R.string.cd_print_paused),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            // Nothing below the ring — the ONLY focus readout is the %/READY on the ring itself.
            // Z height + layer live in the field grid (Matthew, 2026-06-01: extra lines pushed the
            // ring off the top edge; filename-when-no-thumbnail lives in the ring center).
        }
    }
}

/**
 * The 3×2 icon-led stat grid (mockup 03-print-status.png) — glanceable from across the room. Reads only
 * catalog-confirmed fields; a missing source shows "—" (never fabricated). Two cell shapes:
 *  - [IconTwoRowCell] (icon | active-over-inactive): Z height (altitude), Layer (layers), Nozzle/Bed temp.
 *  - [IconValueCell] (icon | single value): Elapsed (timer_arrow_up), Remaining (timer_arrow_down).
 * The "final height" (Z) and Remaining (ETA) need file metadata → "—" until Inc 2.
 */
@Composable
private fun StatGrid(
    state: PrinterState,
    metadata: PrintMetadata? = null,
    babystepWindow: Boolean = false,
    terminal: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val nozzle = primaryHeater(state)
    val bed = state.heaters["heater_bed"]
    // Applied Z offset (SC-5): shown when non-zero OR inside the babystep window. The row stays stable
    // (em-dash placeholder) when shown-but-zero in-window; hidden entirely otherwise.
    val zOffset = state.gcodeZOffset ?: 0.0
    val showZOffset = !terminal && (babystepWindow || zOffset != 0.0)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTwoRowCell(
                icon = { sp -> DinghyIconView(DinghyIcons.Altitude, tint = t.text2, sizeDp = sp.dp) },
                // Active = live Z; inactive = metadata object_height (final print height context), "—" when absent.
                active = if (terminal) "—" else fmtZ(state), inactive = metadata?.objectHeight?.let { fmt(it) } ?: "—", activeColor = t.text,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            IconTwoRowCell(
                icon = { sp -> DinghyIconView(DinghyIcons.Layers, tint = t.text2, sizeDp = sp.dp) },
                active = if (terminal) "—" else (state.currentLayer?.toString() ?: "—"),
                // Total = live slicer value preferred, metadata layer_count as the reliable fallback.
                inactive = totalLayers(state, metadata),
                activeColor = t.text,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Heater READOUTS read the accent-led N-series rule at the SAME canonical index as the
            // GraphView trace / Temperature legend (D-05/D-06): nozzle = seriesColor(0) = ACCENT (in all
            // modes), bed = seriesColor(1) = pool[0]. Cross-screen identity — same sensor = same color
            // everywhere; the nozzle readout shares one hue with the GraphView trace 0 and the Temperature
            // legend trace-0. D-06 supersession of the Phase-15-07 `nozzle = pool[0]` binding — the nozzle
            // is now accent, not pool[0]. NOT `t.heat` (now caution-only); `seriesColor` guards empty pool.
            val nozzleColor = t.seriesColor(0)
            val bedColor = t.seriesColor(1)
            IconTwoRowCell(
                icon = { sp -> DinghyIconView(DinghyIcons.Nozzle, tint = nozzleColor, sizeDp = sp.dp) },
                active = tempActive(nozzle), inactive = tempInactive(nozzle), activeColor = nozzleColor,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            IconTwoRowCell(
                icon = { sp -> DinghyIconView(DinghyIcons.HeatBed, tint = bedColor, sizeDp = sp.dp) },
                active = tempActive(bed), inactive = tempInactive(bed), activeColor = bedColor,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconValueCell(
                icon = { sp -> DinghyIconView(DinghyIcons.TimerUp, tint = t.text2, sizeDp = sp.dp) },
                value = fmtDuration(state.printDuration), valueColor = t.text,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            // Remaining = slicer-file estimate × (1 − live progress) → H:MM; "—" when estimate unknown.
            val remainingSeconds = metadata?.estimatedTime?.let { it * (1.0 - state.progress.coerceIn(0.0, 1.0)) }
            val remaining = remainingSeconds?.takeIf { it > 0.0 }?.let { fmtDuration(it) } ?: "—"
            IconValueCell(
                icon = { sp -> DinghyIconView(DinghyIcons.TimerDown, tint = t.text2, sizeDp = sp.dp) },
                value = remaining, valueColor = if (remaining != "—") t.text else t.text3,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        // Applied Z offset (SC-5): the running gcode_move.homing_origin[2] readback (16-04), shown only
        // when non-zero OR inside the babystep window (the row that pairs with the babystep field row).
        // The applied offset lives HERE in the stat frame, never in the babystep row itself.
        if (showZOffset) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconValueCell(
                    icon = { sp -> DinghyIconView(DinghyIcons.Height, tint = t.text2, sizeDp = sp.dp) },
                    value = stringResource(R.string.printstatus_z_offset, fmtSignedZ(zOffset)),
                    valueColor = if (zOffset != 0.0) t.text else t.text3,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

/** Icon font size as a fraction of the cell height — kept SMALL so the icon is a quiet indicator and
 * the reading is the hero (Matthew: big icons distract from the values). */
private const val CELL_ICON_FRACTION = 0.45f

/** Icon span = 25% of the cell width; the reading gets the remaining 75% (Matthew, 2026-06-01). */
private const val CELL_ICON_WEIGHT = 0.25f

/**
 * Icon (LEFT, scaled to the cell height) | active-over-inactive value CENTERED in the cell. Active =
 * bold/bright, inactive = dim/smaller. The icon is pinned to the start edge while the reading sits in
 * the cell's center (Matthew, 2026-06-01 — icons left-justified, measurements centered).
 */
@Composable
private fun IconTwoRowCell(
    icon: @Composable (sizeSp: Float) -> Unit,
    active: String,
    inactive: String,
    activeColor: Color,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    BoxWithConstraints(modifier) {
        val iconSp = maxHeight.value * CELL_ICON_FRACTION
        Row(
            Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon span = 25% of the cell width, glyph centered within it.
            Box(Modifier.weight(CELL_ICON_WEIGHT).fillMaxHeight(), contentAlignment = Alignment.Center) {
                icon(iconSp)
            }
            // Value span = the remaining 75%, reading centered within it.
            Column(
                Modifier.weight(1f - CELL_ICON_WEIGHT),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(active, color = activeColor, fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = fsSp(26f, t.fs).sp)
                Text(inactive, color = t.text3, fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = fsSp(17f, t.fs).sp)
            }
        }
    }
}

/** Icon (LEFT, pinned to the start edge) | single value CENTERED in the cell — the time cells. */
@Composable
private fun IconValueCell(
    icon: @Composable (sizeSp: Float) -> Unit,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    BoxWithConstraints(modifier) {
        val iconSp = maxHeight.value * CELL_ICON_FRACTION
        Row(
            Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(CELL_ICON_WEIGHT).fillMaxHeight(), contentAlignment = Alignment.Center) {
                icon(iconSp)
            }
            Box(Modifier.weight(1f - CELL_ICON_WEIGHT).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(value, color = valueColor, fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = fsSp(26f, t.fs).sp)
            }
        }
    }
}

/**
 * The gutter Stop: a red `crisis_alert` glyph (no label). TAP opens the e-stop [ConfirmGuard]; HOLD
 * (>~½ s, the system long-press) fires the e-stop IMMEDIATELY (the panic path, with haptic) — Matthew.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PrintStatusControlTile(
    control: PrintStatusControl,
    onTap: () -> Unit,
    onHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val outline = if (control.enabled) controlColor(control, t) else t.hair
    val cancelCd = stringResource(R.string.cd_cancel_print)
    val base = modifier
        .heightIn(min = 64.dp)
        .clip(shape)
        .border(BorderStroke(2.dp, outline), shape)
        .then(
            if (control.accessibilityAction == PrintStatusControlAction.GracefulCancel && control.enabled) {
                Modifier.semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(cancelCd) {
                            onHold()
                            true
                        },
                    )
                }
            } else {
                Modifier
            },
        )
    val actionModifier = if (control.enabled) {
        base.combinedClickable(
            onClick = onTap,
            onLongClick = if (control.holdAction != null) onHold else null,
        )
    } else {
        base.semantics { disabled() }
    }

    Box(actionModifier, contentAlignment = Alignment.Center) {
        Text(
            text = control.label,
            color = if (control.enabled) t.text else t.text3,
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun controlColor(control: PrintStatusControl, t: works.mees.dinghy.theme.ThemeTokens): Color =
    when (control.tapAction) {
        PrintStatusControlAction.OpenFiles,
        PrintStatusControlAction.PausePrint,
        PrintStatusControlAction.Preheat,
        -> t.accentLine
        PrintStatusControlAction.ResumePrint,
        PrintStatusControlAction.RestartPrint,
        -> t.go
        PrintStatusControlAction.EmergencyStop,
        PrintStatusControlAction.GracefulCancel,
        PrintStatusControlAction.Power,
        -> t.stop
        PrintStatusControlAction.Tune,
        PrintStatusControlAction.Dismiss,
        null,
        -> t.hair
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StopButton(onTap: () -> Unit, onHold: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, t.stop), shape)
            .combinedClickable(onClick = onTap, onLongClick = onHold),
        contentAlignment = Alignment.Center,
    ) {
        // D-01/D-02/D-06: the stop-status silhouette (a redundant non-color cue for the stop state). The
        // `disabled_by_default` glyph is a square+✕, not a literal octagon — safety is carried by the
        // distinct shape (D-06). Explicit fsSp-scaled size — do NOT rely on the 96dp intrinsic.
        // [[dinghy-font-sizes-too-small]]: the glyph tracks adjacent control text via fsSp(baseSp, t.fs).
        DinghyIconView(
            DinghyIcons.StatusStop,
            tint = t.stop,
            sizeDp = fsSp(32f, t.fs).dp,
            contentDescription = stringResource(R.string.cd_emergency_stop),
        )
    }
}

// --- Phase-16 four-state surfaces (Standby launcher / babystep / Terminal / Spoolman line) -----------

/**
 * Standby Focus: the app-icon base + a minimal centered glance overlay (UI-SPEC Standby). The glance
 * list is intentionally short/glanceable: Nozzle · Bed · the MCU/host glance sensor (only when a real
 * `selectGlanceSensor` reading exists, else omitted — NO host-load fallback in P16) · Active spool
 * remaining (only when Spoolman is available). NO connection-state line.
 */
@Composable
private fun StandbyFocus(
    state: PrinterState,
    spoolmanPresent: Boolean,
    activeSpoolCardState: ActiveSpoolCardState,
) {
    val t = LocalTokens.current
    val nozzle = primaryHeater(state)
    val bed = state.heaters["heater_bed"]
    val glance = selectGlanceSensor(state.temperatureSensors)
    val spoolRemaining = (activeSpoolCardState as? ActiveSpoolCardState.Loaded)?.spool?.remainingWeight
    Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        // App-icon base (faint backdrop): the Benchy brand image (default; per-printer user-brandable per
        // the staging note). ContentScale.Crop FILLS the focus region in BOTH orientations — Icon's hard
        // Fit left big margins on the tall landscape half-focus (2026-06-06 UAT); Crop scales the 16:9 art
        // to cover and trims the decorative margins. Themed via accent2 tint, faint under the glance list.
        Image(
            painter = painterResource(R.drawable.benchy),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(t.accent2),
            alpha = 0.45f,
            modifier = Modifier.fillMaxSize(),
        )
        // The centered glance list overlaid on the backdrop.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GlanceRow("nozzle-temp", stringResource(R.string.printstatus_nozzle_label), tempActive(nozzle), t.seriesColor(0))
            GlanceRow("heat-bed", stringResource(R.string.printstatus_bed_label), tempActive(bed), t.seriesColor(1))
            glance?.let { GlanceRow("glance", glanceLabel(it.name), "${fmt(it.temperature)}", t.text) }
            if (spoolmanPresent && spoolRemaining != null) {
                GlanceRow("spool", stringResource(R.string.printstatus_spool_label), "${spoolRemaining.roundToInt()} g", t.text)
            }
        }
    }
}

/** One glance line: dim caption + GeistMono value (focus-tier — "large and in charge", 2026-06-06 UAT). */
@Composable
private fun GlanceRow(key: String, label: String, value: String, valueColor: Color) {
    val t = LocalTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = t.text2, fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = fsSp(23f, t.fs).sp)
        Text(value, color = valueColor, fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = fsSp(34f, t.fs).sp)
    }
}

/** Friendly glance-sensor label: strip the `temperature_sensor ` prefix, fall back to the raw key. */
private fun glanceLabel(name: String): String =
    name.removePrefix("temperature_sensor ").ifBlank { name }

/**
 * The Standby adaptive launcher grid (UI-SPEC). Every tile dispatches a real Dest via [onNavigate], or
 * the flexible/growing Drawer tile via [onOpenDrawer] — NO tile is bound to a no-op. The Drawer tile is
 * the explicitly-chosen flexible tile (interactive-grid flexible-tile rule): it spans the remaining
 * column(s) on the last row so the rest of the grid stays regular.
 */
@Composable
private fun LauncherGrid(
    dests: List<LauncherDest>,
    onNavigate: (Dest) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 2 columns (portrait-stable, touch-friendly ≥64px). The Drawer tile (always last) participates in
    // the row flow and ABSORBS any leftover cell: an ODD nonDrawer count → Drawer fills the single
    // leftover slot next to the last item (grid stays tight, no gap); an EVEN count → Drawer lands alone
    // on a fresh final row and GROWS to full width (the flexible tile, interactive-grid rule).
    val columns = 2
    val nonDrawer = dests.filter { it != LauncherDest.Drawer }
    val ordered = nonDrawer + LauncherDest.Drawer
    val rows = ordered.chunked(columns)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowItems ->
            // The Drawer alone on the final row (even nonDrawer count) → grow to full width.
            val drawerLone = rowItems.size == 1 && rowItems.first() == LauncherDest.Drawer
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { d ->
                    val cellWeight = if (drawerLone) columns.toFloat() else 1f
                    if (d == LauncherDest.Drawer) {
                        LauncherTile(
                            dest = LauncherDest.Drawer,
                            onClick = onOpenDrawer,
                            modifier = Modifier.weight(cellWeight).fillMaxHeight(),
                        )
                    } else {
                        LauncherTile(
                            dest = d,
                            onClick = { launcherDestTarget(d)?.let(onNavigate) },
                            modifier = Modifier.weight(cellWeight).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

/** Map a [LauncherDest] to its route [Dest] (Drawer → null, it opens the drawer not a Dest). */
private fun launcherDestTarget(d: LauncherDest): Dest? = when (d) {
    LauncherDest.Files -> Dest.Files
    LauncherDest.Temperature -> Dest.Temperature
    LauncherDest.Move -> Dest.Move
    LauncherDest.Extrude -> Dest.Extrude
    LauncherDest.Calibration -> Dest.Calibration
    LauncherDest.Spool -> Dest.Spool
    LauncherDest.Macros -> Dest.Macros
    LauncherDest.Console -> Dest.Console
    LauncherDest.Drawer -> null
}

/** One neutral-outline launcher tile (navigation intent = neutral, UI-SPEC). ICON-ONLY (the text label
 *  was dropped on-device, 2026-06-06 UAT): the glyph fills the tile; [launcherLabel] now feeds a11y only. */
@Composable
private fun LauncherTile(dest: LauncherDest, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val label = stringResource(launcherLabelRes(dest))
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, t.hair), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Icon-only: the glyph owns the whole tile, enlarged to read across the room. DinghyIconView
        // owns the a11y (the tile is visually icon-only) — the launcher label is the spoken cd.
        DinghyIconView(
            launcherIcon(dest),
            tint = t.text2,
            sizeDp = fsSp(40f, t.fs).dp,
            contentDescription = label,
        )
    }
}

/**
 * The LIVE Print-Status shortcut Tune tile (TUNE-01 / D-21) — taps open the Fine-Tune Hub. Icon-only,
 * mirroring [LauncherTile] (hair outline, ≥64dp, `instant_mix` sliders glyph — DISTINCT from `tune`
 * which is Calibration's, icon-no-repeat). The flexible/growing first cell of the shortcut row.
 */
@Composable
private fun TuneShortcutTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, t.hair), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Icon-only: DinghyIconView owns the a11y (the spoken "Tune" cd).
        DinghyIconView(
            DinghyIcons.FineTune,
            tint = t.text2,
            sizeDp = fsSp(40f, t.fs).dp,
            contentDescription = stringResource(R.string.cd_tune),
        )
    }
}

/** Distinct semantic icon token per launcher tile (icon-never-twice), routed through [DinghyIcons]. */
private fun launcherIcon(d: LauncherDest): works.mees.dinghy.designsystem.icons.DinghyIcon = when (d) {
    LauncherDest.Files -> DinghyIcons.LauncherFiles
    LauncherDest.Temperature -> DinghyIcons.LauncherTemperature
    LauncherDest.Move -> DinghyIcons.LauncherMove
    LauncherDest.Extrude -> DinghyIcons.LauncherExtrude
    LauncherDest.Calibration -> DinghyIcons.LauncherCalibration
    LauncherDest.Spool -> DinghyIcons.LauncherSpool
    LauncherDest.Macros -> DinghyIcons.LauncherMacros
    LauncherDest.Console -> DinghyIcons.LauncherConsole
    LauncherDest.Drawer -> DinghyIcons.LauncherDrawer
}

/** The tile's a11y label string-resource id (icon-only tiles; the label feeds TalkBack only). */
private fun launcherLabelRes(d: LauncherDest): Int = when (d) {
    LauncherDest.Files -> R.string.cd_launcher_files
    LauncherDest.Temperature -> R.string.cd_launcher_temperature
    LauncherDest.Move -> R.string.cd_launcher_move
    LauncherDest.Extrude -> R.string.cd_launcher_extrude
    LauncherDest.Calibration -> R.string.cd_launcher_calibration
    LauncherDest.Spool -> R.string.cd_launcher_spool
    LauncherDest.Macros -> R.string.cd_launcher_macros
    LauncherDest.Console -> R.string.cd_launcher_console
    LauncherDest.Drawer -> R.string.cd_launcher_drawer
}

/**
 * The Printing/Paused shortcut row (UI-SPEC combination matrix). Tune is the flexible/growing tile (the
 * P17 stub, no-op); the other three slots are navigation tiles per the Spoolman × bookmarked-macros
 * combination. NO Drawer tile mid-print (drawer stays swipe-only).
 */
@Composable
private fun ShortcutRow(
    spoolmanPresent: Boolean,
    hasBookmarkedMacros: Boolean,
    onNavigate: (Dest) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The trailing nav slots per the matrix (Tune is always first + flexible).
    val tail: List<LauncherDest> = when {
        spoolmanPresent && hasBookmarkedMacros -> listOf(LauncherDest.Temperature, LauncherDest.Macros, LauncherDest.Spool)
        !spoolmanPresent && hasBookmarkedMacros -> listOf(LauncherDest.Temperature, LauncherDest.Macros, LauncherDest.Console)
        spoolmanPresent && !hasBookmarkedMacros -> listOf(LauncherDest.Temperature, LauncherDest.Spool, LauncherDest.Console)
        else -> listOf(LauncherDest.Temperature, LauncherDest.Console)
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Tune = the flexible/growing tile (weight grows when the row is short — the "Tune grows" case).
        // TUNE-01 / D-21: now LIVE — taps open the Fine-Tune Hub (Dest.FineTune). This is the mid-print
        // Print-Status entry into the live-adjust panel (the gutter Tune control stays a disabled stub,
        // PrintStatusControlModel). Distinct `instant_mix` glyph (icon-no-repeat; `tune` is Calibration's).
        val tuneWeight = if (tail.size < 3) 2f else 1f
        Box(Modifier.weight(tuneWeight)) {
            TuneShortcutTile(
                onClick = { onNavigate(Dest.FineTune) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        tail.forEach { d ->
            LauncherTile(dest = d, onClick = { launcherDestTarget(d)?.let(onNavigate) }, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * The babystep 3-cell row (SC-5) — `[ Compress ] [ step value ] [ Expand ]` — replaces the shortcut row
 * inside the early-layer window. Compress fires `babystepZ(-step)` (nozzle CLOSER), Expand `+step`
 * (FARTHER); the center cell shows the step value and tapping it cycles via `nextBabystepStep`. Both
 * controls are accent-outline, icon-only (distinct silhouettes), carrying their verbatim UI-SPEC
 * contentDescription. The applied offset lives in the StatGrid, not here (exempt from the flexible rule).
 */
@Composable
private fun BabystepRow(
    step: Double,
    onCompress: () -> Unit,
    onExpand: () -> Unit,
    onCycleStep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BabystepIconCell(
            icon = DinghyIcons.BabystepCompress,
            description = stringResource(R.string.cd_babystep_compress),
            onClick = onCompress,
            modifier = Modifier.weight(1f),
        )
        // Center: step value only; tap cycles the size.
        val stepCd = stringResource(R.string.cd_babystep_step_size, fmtStep(step))
        Box(
            Modifier.weight(1f).heightIn(min = 64.dp)
                .clip(RoundedCornerShape(t.rCtrl))
                .border(BorderStroke(2.dp, t.accentLine), RoundedCornerShape(t.rCtrl))
                .clickable(onClick = onCycleStep)
                .semantics { contentDescription = stepCd },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                fmtStep(step),
                color = t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(22f, t.fs).sp,
            )
        }
        BabystepIconCell(
            icon = DinghyIcons.BabystepExpand,
            description = stringResource(R.string.cd_babystep_expand),
            onClick = onExpand,
            modifier = Modifier.weight(1f),
        )
    }
}

/** An accent-outline icon-only babystep cell (Compress/Expand). The glyph carries the action direction;
 *  [description] is the TalkBack contract (the cell is visually icon-only). */
@Composable
private fun BabystepIconCell(
    icon: works.mees.dinghy.designsystem.icons.DinghyIcon,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, t.accentLine), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // DinghyIconView owns the a11y (the cell is visually icon-only) — [description] is the spoken cd.
        DinghyIconView(
            icon,
            tint = t.accentLine,
            sizeDp = fsSp(32f, t.fs).dp,
            contentDescription = description,
        )
    }
}

/**
 * Terminal Focus: a clean result hero — the gcode thumbnail (Coil) when available, else the app-icon
 * fallback. NO ring, NO dim, NO result-icon overlay (UI-SPEC Terminal). Complete/Cancelled/Error share
 * this treatment.
 */
@Composable
private fun TerminalFocus(state: PrinterState, metadata: PrintMetadata?, httpBase: String) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val thumbRel = metadata?.largestThumbRelPath
    val filename = state.printFilename
    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        val size = minOf(maxWidth, maxHeight) * 0.9f
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            if (thumbRel != null && httpBase.isNotBlank() && filename.isNotBlank()) {
                // D-05/D-02 preview branch: Coil doesn't load under @Preview → labeled placeholder.
                if (LocalInspectionMode.current) {
                    PreviewPlaceholderBox(
                        label = "Thumbnail",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(t.rCard)),
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(thumbnailUrl(httpBase, filename, thumbRel))
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(t.rCard)),
                    )
                }
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = t.accent2,
                    modifier = Modifier.fillMaxSize(0.7f).alpha(0.6f),
                )
            }
            // The result label centered at the ring-bottom analog — the terminal outcome.
            Text(
                stringResource(statusLabelRes(state.printState)),
                color = t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(22f, t.fs).sp,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * Terminal Field: a FINISHED-print summary LIST (2026-06-06 UAT) — replaces the live cockpit grid for
 * Complete/Cancelled/Error. Reads only retained, catalog-confirmed state (print_stats + the held
 * metadata); a missing value shows "—", never fabricated. Vertically centered, roomy rows.
 */
@Composable
private fun TerminalStatsList(state: PrinterState, metadata: PrintMetadata?, modifier: Modifier = Modifier) {
    val file = state.printFilename.substringAfterLast('/').ifBlank { "—" }
    val time = fmtDuration(state.printDuration.takeIf { it > 0.0 } ?: state.totalDuration)
    val filament = state.filamentUsed.takeIf { it > 0.0 }?.let { "${fmt(it / 1000.0)} m" } ?: "—"
    val totalLayers = state.totalLayer ?: metadata?.layerCount
    val layers = when {
        totalLayers != null -> "${state.currentLayer ?: 0} / $totalLayers"
        state.currentLayer != null -> "${state.currentLayer}"
        else -> "—"
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
        TerminalStatRow(stringResource(R.string.printstatus_terminal_file_label), file, marquee = true)
        TerminalStatRow(stringResource(R.string.printstatus_print_time_label), time)
        TerminalStatRow(stringResource(R.string.printstatus_terminal_filament_label), filament)
        TerminalStatRow(stringResource(R.string.printstatus_terminal_layers_label), layers)
    }
}

/** One Terminal summary row: dim caption (left) + GeistMono value filling the rest, right-aligned.
 *  [marquee] = true scrolls an over-long value (the filename) instead of ellipsizing it. */
@Composable
private fun TerminalStatRow(label: String, value: String, marquee: Boolean = false) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = t.text2,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Medium,
            fontSize = fsSp(20f, t.fs).sp,
        )
        Text(
            value,
            color = t.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(22f, t.fs).sp,
            maxLines = 1,
            softWrap = false,
            overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).then(if (marquee) Modifier.basicMarquee() else Modifier),
        )
    }
}

/** Terminal(Error) error lines (≤3, AppShell-projected). Plain text — NO markup execution (T-16-06-04).
 *  Hidden by the caller when the list is empty. */
@Composable
private fun TerminalErrorLines(lines: List<String>, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    Column(
        modifier
            .clip(shape)
            .border(BorderStroke(2.dp, t.hair), shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { line ->
            Text(
                line,
                color = t.text2,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Medium,
                fontSize = fsSp(15f, t.fs).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The optional Spoolman print line (Printing/Paused Field) — informational active-spool remaining.
 * Hidden entirely when Spoolman is unavailable (the caller gates that) or the remaining is unknown.
 * (The live `PrintMetadata` carries no per-job filament weight in P16, so the required-vs-available
 * comparison + the accent-when-short attention cue is deferred — there is no required-weight source
 * on this surface yet; the line stays informational/neutral. UI-SPEC D-1c.)
 */
@Composable
private fun SpoolmanPrintLine(
    cardState: ActiveSpoolCardState,
    metadata: PrintMetadata?,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val availableG = (cardState as? ActiveSpoolCardState.Loaded)?.spool?.remainingWeight ?: return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        DinghyIconView(DinghyIcons.Progress, tint = t.text2, sizeDp = fsSp(18f, t.fs).dp)
        Text(
            stringResource(R.string.printstatus_spool_remaining, availableG.roundToInt()),
            color = t.text2,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Medium,
            fontSize = fsSp(16f, t.fs).sp,
        )
    }
}

// --- formatters / resolution (catalog-aligned) -----------------------------------------------------

/** Primary nozzle heater: `extruder`, else the first `extruder`-prefixed heater (multi-tool naming). */
private fun primaryHeater(state: PrinterState): HeaterState? =
    state.heaters["extruder"] ?: state.heaters.entries.firstOrNull { it.key.startsWith("extruder") }?.value

/** Active (current) temp; "—" when the heater is absent. No degree symbol (saves space — Matthew). */
private fun tempActive(h: HeaterState?): String = h?.let { fmt(it.temperature) } ?: "—"

/** Inactive (target) temp; "—" when off (target 0) or absent. */
private fun tempInactive(h: HeaterState?): String =
    h?.takeIf { it.target > 0.0 }?.let { fmt(it.target) } ?: "—"

/** Live Z height (mm, 1 decimal) from gcode_position[2]; "—" until a position is known. */
private fun fmtZ(state: PrinterState): String =
    state.gcodePosition?.getOrNull(2)?.let { fmt(it) } ?: "—"

/**
 * Total layers: live slicer value (`print_stats.info.total_layer`) preferred, metadata `layer_count`
 * as the reliable fallback (catalog), else "—". Never fabricated (docs/moonraker-capabilities.md).
 */
private fun totalLayers(state: PrinterState, metadata: PrintMetadata?): String =
    (state.totalLayer ?: metadata?.layerCount)?.toString() ?: "—"

/** Duration as H:MM (≥1h) or M:SS (<1h); "—" when zero/none. */
private fun fmtDuration(seconds: Double): String {
    if (seconds <= 0.0) return "—"
    val total = seconds.roundToInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h >= 1) "$h:${m.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

/** Tabular-friendly one-decimal formatting, rounded (not truncated). */
private fun fmt(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()

/** A signed, 3-decimal Z-offset readout (`+0.050` / `-0.025` / `0.000`) — tabular, sign always shown. */
private fun fmtSignedZ(v: Double): String {
    val rounded = (v * 1000).roundToInt() / 1000.0
    val sign = if (rounded > 0) "+" else ""
    return "$sign${String.format(java.util.Locale.US, "%.3f", rounded)}"
}

/** A babystep step size as a 2-decimal value (`0.05`) — matches the canonical BABYSTEP_STEPS members. */
private fun fmtStep(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)

/**
 * Decode the single-spool DETAIL from a `/v1/spool/{id}` proxy-v2 envelope (its `response` is a lone
 * object, not an array — so [parseSpoolmanSpools] sees no array and returns empty). Best-effort: walk the
 * envelope's `response` object and decode it via the shared [works.mees.dinghy.net.MoonrakerJson]; a
 * malformed/absent envelope or an id mismatch yields null (the card stays in its Loading variant), never
 * throws (T-11-06-01).
 */
private fun parseSpoolDetail(envelope: kotlinx.serialization.json.JsonElement?, expectedId: Int): SpoolmanSpool? {
    val obj = envelope as? kotlinx.serialization.json.JsonObject ?: return null
    val response = obj["response"] as? kotlinx.serialization.json.JsonObject ?: return null
    val spool = runCatching {
        works.mees.dinghy.net.MoonrakerJson.decodeFromJsonElement(SpoolmanSpool.serializer(), response)
    }.getOrNull() ?: return null
    return spool.takeIf { it.id == expectedId }
}

/** The printer's current print state as a short uppercase label for the ring center (idle/finished
 *  states); the Printing case is rendered as the live % instead. */
private fun statusLabelRes(s: PrintState): Int = when (s) {
    PrintState.Standby -> R.string.printstatus_status_standby
    PrintState.Printing -> R.string.printstatus_status_printing
    PrintState.Paused -> R.string.printstatus_status_paused
    PrintState.Complete -> R.string.printstatus_status_complete
    PrintState.Cancelled -> R.string.printstatus_status_cancelled
    PrintState.Error -> R.string.printstatus_status_error
}
