package works.mees.jiib.ui.printstatus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import works.mees.jiib.R
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.DispatchEvent
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.HeaterState
import works.mees.jiib.state.KlippyState
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.spool.SpoolmanSpool
import works.mees.jiib.spool.parseSpoolmanSpools
import works.mees.jiib.ui.spool.ActiveSpoolCardState
import works.mees.jiib.ui.spool.deriveActiveSpoolCardState
import androidx.compose.foundation.layout.BoxWithConstraints
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.ui.route.HomeAction
import works.mees.jiib.ui.route.NavDest
import works.mees.jiib.ui.route.buildIdleActions

/**
 * The Print Status home (SHELL-04) — the primary monitor surface (≈90% of interaction). Built on
 * [ScreenScaffold]; all color via [LocalTokens] (THEME-01); live numbers in GeistMono tabular numerals.
 *
 * Data is read STRICTLY from fields confirmed present in docs/moonraker-capabilities.md (real Ender 5 +
 * Ender 3) — no assumed fields. Layer info is nullable (slicer/state-dependent) → "—" fallback.
 *
 * ## Single collapsed home path (2026-06-15)
 * The screen renders ONE path for EVERY printer state — the universal [HomeFocus] (in
 * [PrintStatusFocus]) + the universal [HomeField] (in [PrintStatusField]). The former four-mode
 * (Standby / Printing / Paused / Terminal) classifier, per-state UI model, and per-state foot/control
 * sets are GONE; the home is the root for idle/printing/paused/terminal alike. The Focus owns the home
 * title (print-state OR Klipper host-fault via [homeStateLabelRes]) and the docked e-stop; the Field
 * carries the idle action list + the standby Heaters foot button (opens the unified HeatersList). E-stop:
 * WaterfallHome is in AppShell `screenOwnsEstop`, so the FocusFrame header (via [HomeFocus] passing
 * `isPrinting`/`onEmergencyStop`) owns it — NOT the shell FloatingEStop, which is gated off here.
 *
 * @param container the service-locator (live `printerState` + the session dispatcher).
 * @param onNavigate launcher/forward-nav seam — every idle action routes to a real [NavDest];
 *   the System action routes to [NavDest.System] (D-04/28-05).
 * @param onScanSpool opens the QR scan surface (the active-spool card Scan action).
 * @param errorLines retained for the AppShell call site (the former Terminal(Error) projection);
 *   the collapsed home no longer consumes it — see the single-path note above.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun PrintStatusScreen(
    container: AppContainer,
    onNavigate: (NavDest) -> Unit = {},
    onScanSpool: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") errorLines: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val state by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    // Per-heater capability gate (16-06): the Preheat per-temp dispatch is gated on each heater being
    // present (a bed temp is NEVER routed to an absent heater_bed). Read the live capabilities.
    val capabilities by container.capabilities.collectAsStateWithLifecycle(initialValue = works.mees.jiib.state.Capabilities())
    // Idle-list D-08 capability gates (24-04): Outputs and Webcam rows are hidden when absent.
    // outputsPresent = the printer exposes ≥1 controllable output (AppContainer.outputsPresent spine-scoped).
    // webcamEnabled  = ≥1 webcam configured AND not toggled off by the app-global setting (webcamTileEnabled).
    val outputsPresent by container.outputsPresent.collectAsStateWithLifecycle(initialValue = false)
    val webcamEnabled by container.webcamTileEnabled.collectAsStateWithLifecycle(initialValue = false)

    // Per-printer trace-color overrides (R-CDX-3) — same source as the Temperature graph. Used to color
    // heater values in the home digest; the digest falls back to seriesColor when a heater has no override.
    val heaterColors by remember(container) {
        container.activeProfileId.flatMapLatest { pid ->
            if (pid == null) flowOf(persistentMapOf<String, Color>())
            else container.traceStylePrefs.traceColors(pid).map { argbByName ->
                argbByName.entries.fold(persistentMapOf<String, Color>()) { acc, (name, argb) ->
                    acc.put(name, Color(argb))
                }
            }
        }
    }.collectAsStateWithLifecycle(initialValue = persistentMapOf())

    // Title data (Part B): the active printer's display name + whether >1 profile is saved.
    val printerName by container.activeName.collectAsStateWithLifecycle(initialValue = null)
    val profileCount by container.profileStore.profiles
        .map { it.size }
        .collectAsStateWithLifecycle(initialValue = 0)

    // Active-print Focus inputs: the one-shot-per-filename gcode metadata (thumbnail + object height +
    // layer count) and the HTTP base for the thumbnail URL. Both already exposed on AppContainer.
    val printMetadata by container.printMetadata.collectAsStateWithLifecycle(initialValue = null)
    val httpBase by container.httpBase.collectAsStateWithLifecycle(initialValue = "")

    // ---- Active-spool card (SPOOL-02, 11-06) -------------------------------------------------------
    // The D-03 card reads the capability gate + the D-10-reconciled active status; the spool DETAIL is
    // resolved once-per-id via the session's lean SpoolmanClient (a best-effort getSpool — a rejected/
    // absent read leaves the card in its Loading variant, never crashes). The whole card / its Change
    // action route to the Spool screen; Clear dispatches post_spool_id {} (D-13).
    val spoolmanPresent by container.spoolmanPresent.collectAsStateWithLifecycle(initialValue = false)
    val heatPresets by container.activeHeatPresets.collectAsStateWithLifecycle(emptyList())
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

    // E-stop for the home: HomeFocus passes isPrinting + onEmergencyStop (below) into the FocusFrame
    // header, which renders the docked e-stop while Printing/Paused. WaterfallHome is in AppShell's
    // screenOwnsEstop set, so the shell-level FloatingEStop is gated OFF here (no double-render).

    // Idle action list (24-04, D-05/D-06/D-08): built once per capability-flag change.
    // All four capability flags are live StateFlows so the list is rebuilt whenever the printer
    // connects/disconnects, Spoolman changes, or the user toggles webcam in Settings.
    // Active-print state axis: while Printing/Paused the System action moves into the idle list
    // (it leaves the foot bar, replaced by Pause/Cancel). Keyed into the remember so the row appears
    // the moment a print starts.
    val isPrinting = state.printState == PrintState.Printing || state.printState == PrintState.Paused
    val isPaused = state.printState == PrintState.Paused
    val idleActions: List<HomeAction> = remember(
        spoolmanPresent, outputsPresent, webcamEnabled, isPrinting,
    ) {
        buildIdleActions(
            spoolmanPresent = spoolmanPresent,
            outputsPresent  = outputsPresent,
            webcamEnabled   = webcamEnabled,
            isPrinting      = isPrinting,
        )
    }

    // Pause / Resume / Cancel dispatch (existing CommandSpecs, availability-gated on pause_resume).
    val onPause: () -> Unit = { dispatcher?.dispatch(CommandRegistry.printPause, Unit); Unit }
    val onResume: () -> Unit = { dispatcher?.dispatch(CommandRegistry.printResume, Unit); Unit }
    val onCancel: () -> Unit = { dispatcher?.dispatch(CommandRegistry.printCancel, Unit); Unit }
    // Complete (Klippy-fault gated to match HomeFocus.showComplete) → Dismiss clears the job to standby.
    val isComplete = state.printState == PrintState.Complete &&
        state.klippyState != KlippyState.Shutdown && state.klippyState != KlippyState.Error
    val onDismiss: () -> Unit = { dispatcher?.dispatch(CommandRegistry.dismissPrint, Unit); Unit }

    var failureText by remember { mutableStateOf<String?>(null) }

    // Build the unified Heaters list for the standby foot-bar takeover.
    val loadedFilamentLabel = androidx.compose.ui.res.stringResource(R.string.extrude_loaded_filament)
    val heatersRows = works.mees.jiib.ui.heaters.buildHeatersRows(
        presets = heatPresets,
        loadedSpool = works.mees.jiib.ui.heaters.loadedSpoolTemps(spoolDetail, loadedFilamentLabel),
        scope = works.mees.jiib.ui.heaters.HeatScope.Full,
        extruderObject = "extruder",
        capabilities = capabilities,
    )

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

    Box(modifier.fillMaxSize()) {
        // The pure, container-free rendering surface (hoisted for the 18-05 preview anchor): the live
        // composable resolves all flow values + action lambdas above and passes them in; the
        // `PrintStatusScreen(state = …)` preview overload calls the SAME body with fixture state and
        // no-op callbacks (no Moonraker). Keeps this entry as the single layout source the previews and
        // the running app share — drift is impossible.
        PrintStatusContent(
            state = state,
            printerName = printerName,
            isMultiPrinter = profileCount > 1,
            idleActions = idleActions,
            spoolmanPresent = spoolmanPresent,
            activeSpoolCardState = activeSpoolCardState,
            heaterColors = heaterColors,
            printMetadata = printMetadata,
            httpBase = httpBase,
            isPrinting = isPrinting,
            isPaused = isPaused,
            isComplete = isComplete,
            onDismiss = onDismiss,
            failureText = failureText,
            onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
            onNavigate = onNavigate,
            heatersRows = heatersRows,
            onHeatApply = { works.mees.jiib.ui.heaters.dispatchHeat(dispatcher, it) },
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancel,
        )
    }
}

/**
 * The STATELESS Print-Status entry (18-05 preview anchor / D-01). Renders the collapsed home from a
 * plain [PrinterState] fixture with NO [AppContainer], NO dispatcher, NO Moonraker — the seam every
 * `@Preview` in [works.mees.jiib.preview.PrintStatusPreviews] composes inside a `PreviewBox`. The home
 * content keys off the fixture's `printState` (and `klippyState` for the title), so
 * `SampleFixtures.forState(s)` reproduces every home state.
 *
 * This is the hoisted-state half of the live [PrintStatusScreen] `container` overload: both delegate to
 * the shared [PrintStatusContent], so a preview renders byte-identical layout to the running app. All
 * action callbacks default to no-ops (a preview never dispatches).
 */
@Composable
fun PrintStatusScreen(
    state: PrinterState,
    printerName: String? = null,
    isMultiPrinter: Boolean = false,
    spoolmanPresent: Boolean = false,
    activeSpoolCardState: ActiveSpoolCardState = ActiveSpoolCardState.Unavailable,
    idleActions: List<HomeAction> = buildIdleActions(
        spoolmanPresent = spoolmanPresent,
        outputsPresent  = false,
        webcamEnabled   = false,
        isPrinting      = state.printState == PrintState.Printing || state.printState == PrintState.Paused,
    ),
    onNavigate: (NavDest) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        PrintStatusContent(
            state = state,
            printerName = printerName,
            isMultiPrinter = isMultiPrinter,
            idleActions = idleActions,
            spoolmanPresent = spoolmanPresent,
            activeSpoolCardState = activeSpoolCardState,
            heaterColors = persistentMapOf(),
            printMetadata = null,
            httpBase = "",
            isPrinting = state.printState == PrintState.Printing || state.printState == PrintState.Paused,
            isPaused = state.printState == PrintState.Paused,
            isComplete = state.printState == PrintState.Complete &&
                state.klippyState != KlippyState.Shutdown && state.klippyState != KlippyState.Error,
            onDismiss = {},
            failureText = null,
            onEmergencyStop = null,
            onNavigate = onNavigate,
            onPause = {},
            onResume = {},
            onCancel = {},
        )
    }
}

/**
 * The pure, container-free collapsed-home rendering surface shared by BOTH [PrintStatusScreen] overloads —
 * the live `container` entry (passing resolved flow values + real dispatch lambdas) and the stateless
 * preview entry (passing fixture state + no-op lambdas). ONE rendering path for every printer state: the
 * universal [HomeFocus] in the Focus slot + the universal [HomeField] in the Field slot. Each slot lambda
 * contains ONLY a call to a named top-level composable — independently-restartable recomposition scopes
 * that ScreenScaffold can skip (D-01/D-02 P0 fix).
 *
 * Carries NO `remember`/flow/dispatcher state — every input arrives as a parameter so it renders
 * identically under `@Preview` and at runtime.
 */
@Composable
private fun PrintStatusContent(
    state: PrinterState,
    printerName: String?,
    isMultiPrinter: Boolean,
    idleActions: List<HomeAction>,
    spoolmanPresent: Boolean,
    activeSpoolCardState: ActiveSpoolCardState,
    heaterColors: ImmutableMap<String, Color>,
    printMetadata: works.mees.jiib.state.PrintMetadata?,
    httpBase: String,
    isPrinting: Boolean,
    isPaused: Boolean,
    isComplete: Boolean,
    onDismiss: () -> Unit,
    failureText: String?,
    onEmergencyStop: (() -> Unit)?,
    onNavigate: (NavDest) -> Unit,
    heatersRows: List<works.mees.jiib.ui.heaters.HeatersRow> = emptyList(),
    onHeatApply: (works.mees.jiib.ui.heaters.HeatDispatch) -> Unit = {},
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    // Pilot fix 2026-06-12: ONE screen-root unit grid (LAYOUT.md §"The unit U" — derived from the
    // SCREEN short edge, constant through rotation), passed down to U-consumers. The standby field
    // previously self-derived from its Field-slot box → smaller rows than every other screen.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
    // Collapsed skeleton (2026-06-15): ONE rendering path for every printer state — the universal
    // HomeFocus + the universal HomeField. No more four-mode Crossfade/when. The per-state morph
    // is gone; the home is the root for idle/printing/paused/terminal alike.
    ScreenScaffold(
        fieldFramed = false,
        focus = {
            HomeFocus(
                state = state,
                printerName = printerName,
                isMultiPrinter = isMultiPrinter,
                spoolmanPresent = spoolmanPresent,
                activeSpoolCardState = activeSpoolCardState,
                heaterColors = heaterColors,
                onEmergencyStop = onEmergencyStop,
                uDp = grid.uDp,
                printMetadata = printMetadata,
                httpBase = httpBase,
            )
        },
        field = {
            HomeField(
                idleActions = idleActions,
                activeSpoolCardState = activeSpoolCardState,
                failureText = failureText,
                onNavigate = onNavigate,
                heatersRows = heatersRows,
                onHeatApply = onHeatApply,
                uDp = grid.uDp,
                isPrinting = isPrinting,
                isPaused = isPaused,
                onPause = onPause,
                onResume = onResume,
                onCancel = onCancel,
                isComplete = isComplete,
                onDismiss = onDismiss,
            )
        },
    )
    } // end screen-root BoxWithConstraints (unit grid)
}

// --- formatters / resolution (catalog-aligned) — internal so all PrintStatus*.kt files can read them

/** Primary nozzle heater: `extruder`, else the first `extruder`-prefixed heater (multi-tool naming). */
internal fun primaryHeater(state: PrinterState): HeaterState? =
    state.heaters["extruder"] ?: state.heaters.entries.firstOrNull { it.key.startsWith("extruder") }?.value

/** Active (current) temp; "—" when the heater is absent. No degree symbol (saves space — Matthew). */
internal fun tempActive(h: HeaterState?): String = h?.let { fmt(it.temperature) } ?: "—"

/** Tabular-friendly one-decimal formatting, rounded (not truncated). */
internal fun fmt(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()

/**
 * Decode the single-spool DETAIL from a `/v1/spool/{id}` proxy-v2 envelope (its `response` is a lone
 * object, not an array — so [parseSpoolmanSpools] sees no array and returns empty). Best-effort: walk the
 * envelope's `response` object and decode it via the shared [works.mees.jiib.net.MoonrakerJson]; a
 * malformed/absent envelope or an id mismatch yields null (the card stays in its Loading variant), never
 * throws (T-11-06-01).
 */
private fun parseSpoolDetail(envelope: kotlinx.serialization.json.JsonElement?, expectedId: Int): SpoolmanSpool? {
    val obj = envelope as? kotlinx.serialization.json.JsonObject ?: return null
    val response = obj["response"] as? kotlinx.serialization.json.JsonObject ?: return null
    val spool = runCatching {
        works.mees.jiib.net.MoonrakerJson.decodeFromJsonElement(SpoolmanSpool.serializer(), response)
    }.getOrNull() ?: return null
    return spool.takeIf { it.id == expectedId }
}

/** The printer's current print state as a short uppercase label for the ring center (idle/finished
 *  states); the Printing case is rendered as the live % instead. */
internal fun statusLabelRes(s: works.mees.jiib.state.PrintState): Int = when (s) {
    works.mees.jiib.state.PrintState.Standby -> R.string.printstatus_status_standby
    works.mees.jiib.state.PrintState.Printing -> R.string.printstatus_status_printing
    works.mees.jiib.state.PrintState.Paused -> R.string.printstatus_status_paused
    works.mees.jiib.state.PrintState.Complete -> R.string.printstatus_status_complete
    works.mees.jiib.state.PrintState.Cancelled -> R.string.printstatus_status_cancelled
    works.mees.jiib.state.PrintState.Error -> R.string.printstatus_status_error
}

/** Two-axis home title label: a Klipper host fault (Shutdown/Error) takes precedence over the
 *  print-job state; otherwise the print-state label. Klipper Error reuses the ERROR string. */
internal fun homeStateLabelRes(
    printState: works.mees.jiib.state.PrintState,
    klippyState: works.mees.jiib.state.KlippyState,
): Int = when (klippyState) {
    works.mees.jiib.state.KlippyState.Shutdown -> R.string.printstatus_status_shutdown
    works.mees.jiib.state.KlippyState.Error -> R.string.printstatus_status_error
    else -> statusLabelRes(printState)
}
