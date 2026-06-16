package works.mees.dinghy.ui.printstatus

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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.HeaterState
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
 * ## Single collapsed home path (2026-06-15)
 * The screen renders ONE path for EVERY printer state — the universal [HomeFocus] (in
 * [PrintStatusFocus]) + the universal [HomeField] (in [PrintStatusField]). The former four-mode
 * (Standby / Printing / Paused / Terminal) classifier, per-state UI model, and per-state foot/control
 * sets are GONE; the home is the root for idle/printing/paused/terminal alike. The Focus owns the home
 * title (print-state OR Klipper host-fault via [homeStateLabelRes]) and the docked e-stop; the Field
 * carries the idle action list + the spool-aware [runPreheat] (gated by [selectPreheatPath]). E-Stop
 * elsewhere = the AppShell-level FloatingEStop while printing (D-14).
 *
 * @param container the service-locator (live `printerState` + the session dispatcher).
 * @param onNavigate launcher/forward-nav seam — every idle action routes to a real [NavDest];
 *   the System action routes to [NavDest.System] (D-04/28-05).
 * @param onScanSpool opens the QR scan surface (the active-spool card Scan action).
 * @param errorLines retained for the AppShell call site (the former Terminal(Error) projection);
 *   the collapsed home no longer consumes it — see the single-path note above.
 */
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
    // The active print's gcode metadata — fuels the D-07 fallback swatch (gcode filament_colors) on the
    // active-spool glyph when Spoolman is present but its record has no usable color.
    val metadata by container.printMetadata.collectAsStateWithLifecycle(initialValue = null)
    // Per-heater capability gate (16-06): the Preheat per-temp dispatch is gated on each heater being
    // present (a bed temp is NEVER routed to an absent heater_bed). Read the live capabilities.
    val capabilities by container.capabilities.collectAsStateWithLifecycle(initialValue = works.mees.dinghy.state.Capabilities())
    // Idle-list D-08 capability gates (24-04): Outputs and Webcam rows are hidden when absent.
    // outputsPresent = the printer exposes ≥1 controllable output (AppContainer.outputsPresent spine-scoped).
    // webcamEnabled  = ≥1 webcam configured AND not toggled off by the app-global setting (webcamTileEnabled).
    val outputsPresent by container.outputsPresent.collectAsStateWithLifecycle(initialValue = false)
    val webcamEnabled by container.webcamTileEnabled.collectAsStateWithLifecycle(initialValue = false)

    // Title data (Part B): the active printer's display name + whether >1 profile is saved.
    val printerName by container.activeName.collectAsStateWithLifecycle(initialValue = null)
    val profileCount by container.profileStore.profiles
        .map { it.size }
        .collectAsStateWithLifecycle(initialValue = 0)

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

    var showPresetSelector by remember { mutableStateOf(false) }
    var failureText by remember { mutableStateOf<String?>(null) }

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

    Box(modifier.fillMaxSize()) {
        // The pure, container-free rendering surface (hoisted for the 18-05 preview anchor): the live
        // composable resolves all flow values + action lambdas above and passes them in; the
        // `PrintStatusScreen(state = …)` preview overload calls the SAME body with fixture state and
        // no-op callbacks (no Moonraker). Keeps this entry as the single layout source the previews and
        // the running app share — drift is impossible. The dispatcher-driven guards/PresetSelector stay
        // OUTSIDE the content (they're live-only modals, not part of the previewable scaffold).
        PrintStatusContent(
            state = state,
            printerName = printerName,
            isMultiPrinter = profileCount > 1,
            idleActions = idleActions,
            spoolmanPresent = spoolmanPresent,
            activeSpoolCardState = activeSpoolCardState,
            failureText = failureText,
            onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
            onNavigate = onNavigate,
            onPreheat = ::runPreheat,
        )

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
 * The STATELESS Print-Status entry (18-05 preview anchor / D-01). Renders the collapsed home from a
 * plain [PrinterState] fixture with NO [AppContainer], NO dispatcher, NO Moonraker — the seam every
 * `@Preview` in [works.mees.dinghy.preview.PrintStatusPreviews] composes inside a `PreviewBox`. The home
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
            failureText = null,
            onEmergencyStop = null,
            onNavigate = onNavigate,
            onPreheat = {},
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
    failureText: String?,
    onEmergencyStop: (() -> Unit)?,
    onNavigate: (NavDest) -> Unit,
    onPreheat: () -> Unit,
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
                onEmergencyStop = onEmergencyStop,
                uDp = grid.uDp,
            )
        },
        field = {
            HomeField(
                idleActions = idleActions,
                failureText = failureText,
                onNavigate = onNavigate,
                onPreheat = onPreheat,
                uDp = grid.uDp,
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
