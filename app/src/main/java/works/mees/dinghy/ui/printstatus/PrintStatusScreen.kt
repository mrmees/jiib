package works.mees.dinghy.ui.printstatus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.PrintStartArgs
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.HeaterState
import works.mees.dinghy.state.PrintMetadata
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.spool.SpoolmanSpool
import works.mees.dinghy.spool.parseSpoolmanSpools
import works.mees.dinghy.ui.spool.ActiveSpoolCardState
import works.mees.dinghy.ui.spool.deriveActiveSpoolCardState
import works.mees.dinghy.ui.spool.parseNormalizedHex
import androidx.compose.foundation.layout.BoxWithConstraints
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.ui.route.HomeAction
import works.mees.dinghy.ui.route.NavDest
import works.mees.dinghy.ui.route.buildIdleActions

/**
 * The Print Status home (SHELL-04) — the primary monitor surface (≈90% of interaction). Built on
 * [ScreenScaffold]; all color via [LocalTokens] (THEME-01); live numbers in GeistMono tabular numerals.
 *
 * Data is read STRICTLY from fields confirmed present in docs/moonraker-capabilities.md (real Ender 5 +
 * Ender 3) — no assumed fields. Layer info is nullable (slicer/state-dependent) → "—" fallback.
 *
 * ## Four-state surface (Phase 16, foot bars per R1 2026-06-12) — routed off [classifyPrintStatus]
 * The screen renders FROM the pure [uiModel] ([PrintStatusUiModel]) per the classified
 * [PrintStatusMode] (Standby / Printing / Paused / Terminal). Each mode renders its own
 * Focus / Field; mode actions live in a [PrintStatusFootBar] at the foot of the field (the
 * ScreenScaffold gutter slot is retired). E-Stop on every mode = the AppShell-level FloatingEStop:
 *  - **Standby:** app-icon Focus + minimal glance overlay (Nozzle/Bed + [selectGlanceSensor] glance
 *    temp + active-spool remaining) · the idle action list · the hand-built Preheat (spool-aware via
 *    [selectPreheatPath]) + System foot bar — no E-Stop.
 *  - **Printing:** the 03-print-status composition ([PrintStatusFocus]) · ONE framed [StatGrid] (incl.
 *    the Applied-Z-offset row) + the shortcut row OR the babystep 3-cell row (early-layer window) ·
 *    foot: Pause · Cancel.
 *  - **Paused:** the Printing focus DIMMED + a pause overlay · same Field/toolset · foot: Resume · Cancel.
 *  - **Terminal:** a clean hero ([TerminalFocus], no ring/dim) · the stats summary (live-only fields →
 *    "—") · foot: Dismiss · Reprint; Terminal(Error) appends the AppShell-projected ≤3 [errorLines].
 *
 * @param container the service-locator (live `printerState` + the session dispatcher).
 * @param onNavigate launcher/forward-nav seam — every Standby launcher tile dispatches a real [NavDest];
 *   the System foot button routes to [NavDest.System] (D-04/28-05).
 * @param onScanSpool opens the QR scan surface (the active-spool card Scan action).
 * @param errorLines the bounded ≤3 ERROR-line projection AppShell passes for Terminal(Error).
 */
@Composable
fun PrintStatusScreen(
    container: AppContainer,
    onNavigate: (NavDest) -> Unit = {},
    onScanSpool: () -> Unit = {},
    errorLines: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
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
    // Bookmarked-macros set: the Standby Macros launcher tile is now ALWAYS shown (no gate), but this
    // still feeds the Printing/Paused mid-print ShortcutRow combination matrix. Process-scoped pref.
    val bookmarkedMacros by container.macroPrefs.bookmarks.collectAsStateWithLifecycle(initialValue = emptySet())
    // Idle-list D-08 capability gates (24-04): Outputs and Webcam rows are hidden when absent.
    // outputsPresent = the printer exposes ≥1 controllable output (AppContainer.outputsPresent spine-scoped).
    // webcamEnabled  = ≥1 webcam configured AND not toggled off by the app-global setting (webcamTileEnabled).
    val outputsPresent by container.outputsPresent.collectAsStateWithLifecycle(initialValue = false)
    val webcamEnabled by container.webcamTileEnabled.collectAsStateWithLifecycle(initialValue = false)

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

    // D-07 color precedence for the reactive spool glyph on the launcher tile + mid-print shortcut slot.
    // These surfaces are ALREADY Spoolman-gated (standbyLauncherDests/spoolmanPresent), so this resolution
    // only changes the icon the gated slot draws — never un-gates it. From state already collected (no new
    // fetch): (1) the active Spoolman record's color FIRST, then (2) the active print's gcode
    // filament_colors[0] FALLBACK (fires only when Spoolman IS present but the active record has no usable
    // color — exactly D-07's middle tier), else (3) persistentListOf() → the empty spool (D-03). Index [0] /
    // first only (D-09). The parse helpers null-guard malformed hex → that swatch drops → empty spool, never a
    // throw. ImmutableList: stable Compose param so SpoolGlyph/LauncherTile recomposition can be skipped when
    // the swatch list hasn't changed (D-02/D-03 P1 allocation fix).
    val spoolSwatches: ImmutableList<Color> = remember(spoolDetail, metadata) {
        val spoolmanColors = spoolDetail?.filament?.colorSwatches.orEmpty().mapNotNull(::parseNormalizedHex)
        if (spoolmanColors.isNotEmpty()) {
            spoolmanColors.toImmutableList()
        } else {
            val gcodeColor = metadata?.filamentColors?.firstOrNull()?.let(::parseNormalizedHex)
            if (gcodeColor != null) persistentListOf(gcodeColor) else persistentListOf()
        }
    }

    // The FloatingEStop and its Stop Confirm guard are owned by the AppShell overlay layer (24-03),
    // where they appear on EVERY destination while Printing/Paused (D-14). No e-stop path exists in
    // this screen — the foot bars carry only the mode actions (R1 gutter→foot migration).

    // Idle action list (24-04, D-05/D-06/D-08): built once per capability-flag change.
    // All four capability flags are live StateFlows so the list is rebuilt whenever the printer
    // connects/disconnects, Spoolman changes, or the user toggles webcam in Settings.
    val idleActions: List<HomeAction> = remember(
        spoolmanPresent, outputsPresent, webcamEnabled,
    ) {
        buildIdleActions(
            spoolmanPresent = spoolmanPresent,
            outputsPresent  = outputsPresent,
            webcamEnabled   = webcamEnabled,
        )
    }

    var showCancelGuard by remember { mutableStateOf(false) }
    var showPresetSelector by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PrintStatusPendingAction?>(null) }
    var failureText by remember { mutableStateOf<String?>(null) }
    // Babystep step size (16-06): the center cell shows it; tapping cycles via nextBabystepStep. Session
    // state — defaults to the first canonical step. Compress fires -step, Expand +step.
    var babystepStep by remember { mutableStateOf(works.mees.dinghy.command.PrinterCommands.BABYSTEP_STEPS.first()) }

    // The classified four-state mode (the phase's central routing axis) + the pure UI model the screen
    // renders FROM (launcher order, foot set, active Field row, terminal-error flag).
    val mode = classifyPrintStatus(state)
    val babystepShown = babystepVisible(babystepEnabled, state.currentLayer, babystepLayers)
    val ui = uiModel(
        mode = mode,
        state = state,
        lastJob = lastJob,
        pendingAction = pendingAction,
        spoolmanPresent = spoolmanPresent,
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
                is DispatchEvent.Failure -> {
                    failureText = event.message
                    // Un-wedge the debounce (Codex W-03): a failed pause/resume/cancel/reprint never
                    // flips printState, so the state-watching clear would leave the foot bar dimmed
                    // forever. Key-matched — unrelated failures don't clear it.
                    pendingAction = clearPendingOnDispatchFailure(pendingAction, event.key)
                }
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
            PrintStatusControlAction.RestartPrint -> {
                // Terminal Reprint (D-05): direct print-start of Moonraker's current/last file path, NO
                // ConfirmGuard, does NOT SDCARD_RESET_FILE first. Available when a usable path is exposed.
                // Pending debounce (Codex W-03): arms the "Reprinting" label + dims the foot bar so a
                // double-tap can't dispatch printStart twice; cleared when the file goes active OR the
                // dispatch fails (clearPendingOnDispatchFailure).
                if (pendingAction == null) {
                    restartFilename?.let {
                        dispatcher?.dispatch(CommandRegistry.printStart, PrintStartArgs(it))
                        pendingAction = PrintStatusPendingAction.Restart(it)
                    }
                }
            }
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
            // Terminal Dismiss (D-05): SDCARD_RESET_FILE; a failure surfaces via the dispatcher toast
            // (the LaunchedEffect above). Clears, does not discard input.
            PrintStatusControlAction.Dismiss -> dispatcher?.dispatch(CommandRegistry.dismissPrint, Unit)
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
            idleActions = idleActions,
            babystepShown = babystepShown,
            spoolmanPresent = spoolmanPresent,
            spoolSwatches = spoolSwatches,
            activeSpoolCardState = activeSpoolCardState,
            babystepStep = babystepStep,
            failureText = failureText,
            pendingActionIsNull = pendingAction == null,
            hasBookmarkedMacros = bookmarkedMacros.isNotEmpty(),
            onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
            onRunAction = ::runAction,
            onBabystepCompress = { dispatcher?.dispatch(CommandRegistry.babystepZ, works.mees.dinghy.command.BabystepArgs(-babystepStep)) },
            onBabystepExpand = { dispatcher?.dispatch(CommandRegistry.babystepZ, works.mees.dinghy.command.BabystepArgs(babystepStep)) },
            onCycleBabystepStep = { babystepStep = nextBabystepStep(babystepStep) },
            onNavigate = onNavigate,
            onPreheat = ::runPreheat,
        )

        // FIX-1 (24-04): the in-screen FloatingEStop + its Stop Confirm guard have been REMOVED from
        // PrintStatusScreen. They now live at the AppShell overlay layer (24-03), where they appear
        // on EVERY destination while printing (D-14). The showEstopGuard state + ConfirmGuard block
        // are gone. The cancel guard below is RETAINED — it is the GracefulCancel guard, NOT estop.
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
    spoolSwatches: ImmutableList<Color> = persistentListOf(),
    activeSpoolCardState: ActiveSpoolCardState = ActiveSpoolCardState.Unavailable,
    hasBookmarkedMacros: Boolean = false,
    idleActions: List<HomeAction> = buildIdleActions(
        spoolmanPresent = spoolmanPresent,
        outputsPresent  = false,
        webcamEnabled   = false,
    ),
    babystepStep: Double = works.mees.dinghy.command.PrinterCommands.BABYSTEP_STEPS.first(),
    onNavigate: (NavDest) -> Unit = {},
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
            idleActions = idleActions,
            babystepShown = babystepShown,
            spoolmanPresent = spoolmanPresent,
            spoolSwatches = spoolSwatches,
            activeSpoolCardState = activeSpoolCardState,
            babystepStep = babystepStep,
            failureText = null,
            pendingActionIsNull = true,
            hasBookmarkedMacros = hasBookmarkedMacros,
            onEmergencyStop = null,
            onRunAction = {},
            onBabystepCompress = {},
            onBabystepExpand = {},
            onCycleBabystepStep = {},
            onNavigate = onNavigate,
            onPreheat = {},
        )
    }
}

/**
 * The pure, container-free four-state rendering surface shared by BOTH [PrintStatusScreen] overloads —
 * the live `container` entry (passing resolved flow values + real dispatch lambdas) and the stateless
 * preview entry (passing fixture state + no-op lambdas). Coordinates the four ScreenScaffold slot
 * lambdas, each of which contains ONLY a call to a named top-level composable — creating independently-
 * restartable recomposition scopes that ScreenScaffold can skip (D-01/D-02 P0 fix).
 *
 * Carries NO `remember`/flow/dispatcher state — every input arrives as a parameter so it renders
 * identically under `@Preview` and at runtime.
 */
@Composable
private fun PrintStatusContent(
    mode: PrintStatusMode,
    state: PrinterState,
    metadata: PrintMetadata?,
    httpBase: String,
    ui: PrintStatusUiModel,
    errorLines: List<String>,
    idleActions: List<HomeAction>,
    babystepShown: Boolean,
    spoolmanPresent: Boolean,
    spoolSwatches: ImmutableList<Color>,
    activeSpoolCardState: ActiveSpoolCardState,
    babystepStep: Double,
    failureText: String?,
    pendingActionIsNull: Boolean,
    hasBookmarkedMacros: Boolean,
    onEmergencyStop: (() -> Unit)?,
    onRunAction: (PrintStatusControlAction) -> Unit,
    onBabystepCompress: () -> Unit,
    onBabystepExpand: () -> Unit,
    onCycleBabystepStep: () -> Unit,
    onNavigate: (NavDest) -> Unit,
    onPreheat: () -> Unit,
) {
    // Pilot fix 2026-06-12: ONE screen-root unit grid (LAYOUT.md §"The unit U" — derived from the
    // SCREEN short edge, constant through rotation), passed down to U-consumers. The standby field
    // previously self-derived from its Field-slot box → smaller rows than every other screen.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
    // D-13: one-shot ~150ms Crossfade for idle ↔ printing ↔ terminal transitions.
    // No continuous/looping animation (Adreno-320 fill-rate budget; LAYOUT.md motion rule).
    androidx.compose.animation.Crossfade(
        targetState = mode,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 150),
        label = "PrintStatusMorph",
    ) { crossfadeMode ->
    when (crossfadeMode) {
        is PrintStatusMode.Standby -> ScreenScaffold(
            fieldFramed = false,
            focus = {
                StandbyFocus(
                    state = state,
                    spoolmanPresent = spoolmanPresent,
                    activeSpoolCardState = activeSpoolCardState,
                    uDp = grid.uDp,
                )
            },
            field = {
                PrintStatusStandbyField(
                    idleActions = idleActions,
                    failureText = failureText,
                    onNavigate = onNavigate,
                    onPreheat = onPreheat,
                    uDp = grid.uDp,
                )
            },
        )

        is PrintStatusMode.Printing -> ScreenScaffold(
            fieldFramed = false,
            focus = { PrintStatusFocus(state = state, uDp = grid.uDp, onEmergencyStop = onEmergencyStop, metadata = metadata, httpBase = httpBase) },
            field = {
                PrintStatusActiveField(
                    state = state,
                    metadata = metadata,
                    babystepShown = babystepShown,
                    spoolmanPresent = spoolmanPresent,
                    spoolSwatches = spoolSwatches,
                    activeSpoolCardState = activeSpoolCardState,
                    babystepStep = babystepStep,
                    failureText = failureText,
                    hasBookmarkedMacros = hasBookmarkedMacros,
                    ui = ui,
                    pendingActionIsNull = pendingActionIsNull,
                    onRunAction = onRunAction,
                    onBabystepCompress = onBabystepCompress,
                    onBabystepExpand = onBabystepExpand,
                    onCycleBabystepStep = onCycleBabystepStep,
                    onNavigate = onNavigate,
                    uDp = grid.uDp,
                )
            },
        )

        is PrintStatusMode.Paused -> ScreenScaffold(
            fieldFramed = false,
            focus = { PrintStatusFocus(state = state, uDp = grid.uDp, onEmergencyStop = onEmergencyStop, metadata = metadata, httpBase = httpBase, paused = true) },
            field = {
                PrintStatusActiveField(
                    state = state,
                    metadata = metadata,
                    babystepShown = babystepShown,
                    spoolmanPresent = spoolmanPresent,
                    spoolSwatches = spoolSwatches,
                    activeSpoolCardState = activeSpoolCardState,
                    babystepStep = babystepStep,
                    failureText = failureText,
                    hasBookmarkedMacros = hasBookmarkedMacros,
                    ui = ui,
                    pendingActionIsNull = pendingActionIsNull,
                    onRunAction = onRunAction,
                    onBabystepCompress = onBabystepCompress,
                    onBabystepExpand = onBabystepExpand,
                    onCycleBabystepStep = onCycleBabystepStep,
                    onNavigate = onNavigate,
                    uDp = grid.uDp,
                )
            },
        )

        is PrintStatusMode.Terminal -> ScreenScaffold(
            fieldFramed = false,
            focus = { TerminalFocus(state = state, metadata = metadata, httpBase = httpBase, uDp = grid.uDp) },
            field = {
                PrintStatusTerminalField(
                    state = state,
                    metadata = metadata,
                    ui = ui,
                    errorLines = errorLines,
                    failureText = failureText,
                    pendingActionIsNull = pendingActionIsNull,
                    onRunAction = onRunAction,
                    uDp = grid.uDp,
                )
            },
        )
    }
    } // end Crossfade
    } // end screen-root BoxWithConstraints (unit grid)
}

// --- formatters / resolution (catalog-aligned) — internal so all PrintStatus*.kt files can read them

/** Primary nozzle heater: `extruder`, else the first `extruder`-prefixed heater (multi-tool naming). */
internal fun primaryHeater(state: PrinterState): HeaterState? =
    state.heaters["extruder"] ?: state.heaters.entries.firstOrNull { it.key.startsWith("extruder") }?.value

/** Active (current) temp; "—" when the heater is absent. No degree symbol (saves space — Matthew). */
internal fun tempActive(h: HeaterState?): String = h?.let { fmt(it.temperature) } ?: "—"

/** Inactive (target) temp; "—" when off (target 0) or absent. */
internal fun tempInactive(h: HeaterState?): String =
    h?.takeIf { it.target > 0.0 }?.let { fmt(it.target) } ?: "—"

/** Live Z height (mm, 1 decimal) from gcode_position[2]; "—" until a position is known. */
internal fun fmtZ(state: PrinterState): String =
    state.gcodePosition?.getOrNull(2)?.let { fmt(it) } ?: "—"

/**
 * Total layers: live slicer value (`print_stats.info.total_layer`) preferred, metadata `layer_count`
 * as the reliable fallback (catalog), else "—". Never fabricated (docs/moonraker-capabilities.md).
 */
internal fun totalLayers(state: PrinterState, metadata: PrintMetadata?): String =
    (state.totalLayer ?: metadata?.layerCount)?.toString() ?: "—"

/** Duration as H:MM (≥1h) or M:SS (<1h); "—" when zero/none. */
internal fun fmtDuration(seconds: Double): String {
    if (seconds <= 0.0) return "—"
    val total = seconds.roundToInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h >= 1) "$h:${m.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

/** Tabular-friendly one-decimal formatting, rounded (not truncated). */
internal fun fmt(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()

/** A signed, 3-decimal Z-offset readout (`+0.050` / `-0.025` / `0.000`) — tabular, sign always shown. */
internal fun fmtSignedZ(v: Double): String {
    val rounded = (v * 1000).roundToInt() / 1000.0
    val sign = if (rounded > 0) "+" else ""
    return "$sign${String.format(java.util.Locale.US, "%.3f", rounded)}"
}

/** A babystep step size as a 2-decimal value (`0.05`) — matches the canonical BABYSTEP_STEPS members. */
internal fun fmtStep(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)

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
internal fun statusLabelRes(s: works.mees.dinghy.state.PrintState): Int = when (s) {
    works.mees.dinghy.state.PrintState.Standby -> R.string.printstatus_status_standby
    works.mees.dinghy.state.PrintState.Printing -> R.string.printstatus_status_printing
    works.mees.dinghy.state.PrintState.Paused -> R.string.printstatus_status_paused
    works.mees.dinghy.state.PrintState.Complete -> R.string.printstatus_status_complete
    works.mees.dinghy.state.PrintState.Cancelled -> R.string.printstatus_status_cancelled
    works.mees.dinghy.state.PrintState.Error -> R.string.printstatus_status_error
}

/** Two-axis home title label: a Klipper host fault (Shutdown/Error) takes precedence over the
 *  print-job state; otherwise the print-state label. Klipper Error reuses the ERROR string. */
internal fun homeStateLabelRes(
    printState: works.mees.dinghy.state.PrintState,
    klippyState: works.mees.dinghy.state.KlippyState,
): Int = when (klippyState) {
    works.mees.dinghy.state.KlippyState.Shutdown -> R.string.printstatus_status_shutdown
    works.mees.dinghy.state.KlippyState.Error -> R.string.printstatus_status_error
    else -> statusLabelRes(printState)
}
