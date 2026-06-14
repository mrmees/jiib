package works.mees.dinghy.ui.temperature

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.dinghy.R
import works.mees.dinghy.command.ApplyPresetArgs
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.CommandSpec
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.command.SetHeaterArgs
import works.mees.dinghy.command.TrailingCommitBatcher
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.Scrubber
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.components.ListRowLabel
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.layout.FocusInset
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.LocalUnitDp
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.render.GraphViewHost
import works.mees.dinghy.spool.SpoolmanSpool
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.Palette
import works.mees.dinghy.theme.ThemePrefs
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.theme.seriesColor
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.HeaterLimits
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState

/** The heater target step (°C): coarse set via the scrubber, fine-tune via ±1 (owner 2026-06-14). */
private const val TEMP_FINE_STEP = 1

/** Field mode for the Temperature panel — SensorList (default) or PresetPicker (D-12). */
private sealed class TempFieldMode {
    data object SensorList : TempFieldMode()
    data object PresetPicker : TempFieldMode()
}

/** Top-level screen mode — Monitoring (read-only list) or Adjust (adjustable heaters only). */
private enum class TempMode { Monitoring, Adjust }

/**
 * ONE aligned visible-trace model: series, setpoints, names, and colors filtered together by
 * visibility so that every index-aligned list stays aligned (GraphView draws `setpoints[t]` at
 * the SAME index as `series[t]` — finding 4, D-14).
 */
private data class VisibleTraces(
    val series: List<FloatArray>,
    val setpoints: List<Float?>,
    val names: List<String>,
    val colors: List<Int?>,   // ARGB override per visible trace; null → seriesColor(i)
)

/**
 * The Temperature panel — rebuilt for the jiib redesign grammar (Phase 26, D-10..D-14).
 *
 * Focus MORPHS between:
 *  - **Default:** the live multi-trace [GraphViewHost] (the Klipper temperature history graph).
 *  - **Adjuster:** the selected sensor's graph controls + color picker + optional heater stepper.
 *    Morph is a plain `if (selectedSensor == null)` branch — NOT AnimatedContent — so the
 *    [GraphViewHost] AndroidView factory runs exactly once (Phase-22 D-12 guard, no thrash).
 *
 * Field:
 *  - **SensorList (default):** [ListRow] per sensor (icon tinted to the trace color, trailing
 *    current/target readout in GeistMono), foot = Back · Presets · Cooldown.
 *  - **PresetPicker (D-12):** Field-takeover list of [PrinterCommands.MATERIAL_PRESETS]; the old
 *    full-screen scrim is retired; foot = Back.
 *
 * Graph controls (visible in adjuster morph for every sensor):
 *  - Show/hide trace toggle (calls [TemperatureHolder.setTraceVisibility] + `container.setTraceVisibility`).
 *  - 8-swatch Colorful-pool color row — ALWAYS a dedicated Colorful pool of 8 regardless of the
 *    active palette mode (D-14 / finding 5); calls [TemperatureHolder.setTraceColor] + `container.setTraceColor`.
 *
 * Heater controls (visible only when `sensor.isAdjustable`):
 *  - [AdjusterPanel] with [TEMP_STEPS] increment picker; nudge routes through
 *    [PrinterCommands.clampHeaterTarget] (17-07 invariant / D-22).
 *  - Per-heater Off button ([Intent.Warn], target=0 — P19 GAP-A precedent).
 *
 * [FootButtonBar] inside the field lambda (redesigned-screen law).
 * [FloatingEStop] + [ConfirmGuard] as Box siblings (printing-list-valid destination).
 *
 * This LIVE overload resolves flows from [AppContainer] + [TemperatureHolder] and delegates
 * rendering to the stateless [TemperatureContent].
 *
 * @param container the service-locator (live printer state, dispatcher, theme tuple).
 * @param holder    the toolkit-agnostic [TemperatureHolder] (series/setpoints/legend/traceColors/visibility).
 * @param onBack    neutral nav (D-10).
 */
@Composable
fun TemperatureScreen(
    container: AppContainer,
    holder: TemperatureHolder,
    activeSpoolDetail: SpoolmanSpool? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val series by holder.series.collectAsStateWithLifecycle()
    val setpoints by holder.setpoints.collectAsStateWithLifecycle()
    val legend by holder.legend.collectAsStateWithLifecycle()
    val graphRange by holder.yRange.collectAsStateWithLifecycle()
    val traceColors by holder.traceColors.collectAsStateWithLifecycle()
    val traceVisibility by holder.traceVisibility.collectAsStateWithLifecycle()
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    // Collect the active theme tuple to generate the Colorful-8 swatch pool (D-14 / finding 5).
    val themeTuple by container.activeThemeTuple.collectAsStateWithLifecycle(
        initialValue = ThemePrefs.TUPLE_DEFAULT,
    )
    val caps by container.capabilities.collectAsStateWithLifecycle(initialValue = Capabilities())
    val heaterLimits by holder.heaterLimits.collectAsStateWithLifecycle()
    val selectedSensors by holder.selectedSensors.collectAsStateWithLifecycle()
    val availableSensors = remember(caps) {
        caps.objects.filter { it.startsWith("temperature_sensor ") }.sorted()
    }
    var failureText by remember { mutableStateOf<String?>(null) }

    // quick-rmr: lifecycle-aware in-flight collection — DISPLAY ONLY (the heater AdjusterPanel
    // busy dim). This does NOT reintroduce the 26.5-03-removed dedup pre-check; the dispatcher
    // still owns dedup exclusively.
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())

    // quick-rmr: trailing-commit batcher keyed by SENSOR NAME — heater stepper taps accumulate a
    // clamped working target locally; ONE setHeater dispatch per ~500ms quiet window.
    // COMPOSITION-STABLE (post-review fix 3): `remember {}` with NO dispatcher key — re-keying on
    // reconnect disposed the old batcher mid-burst (early flush, working targets lost to the empty
    // replacement). Both lambdas read `dispatcher` through the local `by` STATE DELEGATE, so every
    // invocation resolves the CURRENT dispatcher at fire time — no key needed for freshness.
    val batcher = remember {
        TrailingCommitBatcher(
            canCommit = { name ->
                // Fire-time read (never a composition snapshot): reschedule while this heater's
                // dispatch key is in flight instead of dispatching into a guaranteed rejection.
                heaterDispatchKey(name) !in (dispatcher?.inFlight?.value ?: emptySet())
            },
            onCommit = { name, value ->
                // Dispatcher resolved at fire time (19-09 lesson); clamp re-applied at commit.
                dispatcher?.dispatch(
                    CommandRegistry.setHeater,
                    SetHeaterArgs(
                        name,
                        PrinterCommands.clampHeaterTarget(value.roundToInt()),
                        heaterDispatchKey(name),
                    ),
                )
            },
        )
    }
    // Commit-on-dispose: a nav-out mid-burst flushes the pending target (the dispatch rides
    // CommandDispatcher's app-lifetime scope). The batcher is composition-stable, so this fires
    // exactly once — when the screen leaves composition.
    DisposableEffect(batcher) { onDispose { batcher.dispose() } }
    val workingTargets by batcher.working.collectAsStateWithLifecycle()

    LaunchedEffect(dispatcher) {
        failureText = null
        val d = dispatcher ?: return@LaunchedEffect
        d.events.collect { event ->
            when (event) {
                is DispatchEvent.Failure -> {
                    failureText = event.message
                    // Revert working targets to live on failure (the toast explains why).
                    batcher.cancelAll()
                }
            }
        }
    }
    LaunchedEffect(failureText) {
        if (failureText != null) { delay(4_000); failureText = null }
    }

    // R10 (26.5-03): per-dispatch-key rejection tick counters — each [CommandDispatcher.rejectedKey]
    // emission (busy/debounce drop) bumps that key's counter; the heater AdjusterPanel showing the
    // matching key renders a one-shot flash. PersistentMap = @Stable param (Phase-22 discipline);
    // changes only on a rejection, never per sample tick.
    var rejectTicks by remember { mutableStateOf(persistentMapOf<String, Long>()) }
    // Lifecycle-aware collection (codex review; Part-1 #3 lifecycle-hygiene invariant): no
    // collection while backgrounded; resumes on STARTED. House pattern = AppShell webcam binding.
    val rejectLifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(dispatcher, rejectLifecycleOwner) {
        rejectTicks = persistentMapOf()
        val d = dispatcher ?: return@LaunchedEffect
        rejectLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            d.rejectedKey.collect { key ->
                rejectTicks = rejectTicks.put(key, (rejectTicks[key] ?: 0L) + 1L)
            }
        }
    }

    // Loaded-spool preheat preset, built from the active Spoolman spool detail resolved UPSTREAM by
    // AppShell's SpoolHolder — the SAME source the Extrude preset list uses (the in-screen
    // currentSpoolmanClient fetch hit the no-op client and never resolved). Shown only when a spool is
    // loaded and its filament reports a nozzle temp; bed falls back to 0 when the spool omits it.
    val spoolPreset: PrinterCommands.Preset? = remember(activeSpoolDetail) {
        val f = activeSpoolDetail?.filament
        val nozzle = f?.settingsExtruderTemp
        if (f != null && nozzle != null) {
            PrinterCommands.Preset(
                name = f.name ?: f.material ?: "LOADED SPOOL",
                nozzle = nozzle,
                bed = f.settingsBedTemp ?: 0,
            )
        } else {
            null
        }
    }

    Box(modifier.fillMaxSize()) {
        TemperatureContent(
            series = series,
            setpoints = setpoints,
            legend = legend,
            graphRange = graphRange,
            traceColors = traceColors,
            traceVisibility = traceVisibility,
            isPrinting = isPrinting,
            failureText = failureText,
            rejectTicks = rejectTicks,
            workingTargets = workingTargets,
            inFlight = inFlight,
            seedHex = themeTuple.seedHex,
            dark = themeTuple.dark,
            availableSensors = availableSensors,
            selectedSensors = selectedSensors,
            heaterLimits = heaterLimits,
            spoolPreset = spoolPreset,
            onBack = onBack,
            onSetTraceColor = { sensorName, color ->
                holder.setTraceColor(sensorName, color)
                container.setTraceColor(sensorName, color.toArgb())
            },
            onSetTraceVisibility = { sensorName, visible ->
                holder.setTraceVisibility(sensorName, visible)
                container.setTraceVisibility(sensorName, visible)
            },
            onToggleSensor = { name, sel ->
                holder.setSensorSelected(name, sel)
                container.setSensorSelected(name, sel)
            },
            onNudgeHeater = { sensorName, rawTarget ->
                // quick-rmr TAP handler: clamp per-tap (the display can never exceed bounds) and
                // accumulate locally — NO dispatch on tap. One setHeater command fires per quiet
                // window via the batcher's onCommit (rejections become structurally rare; the
                // rejectedKey flash stays wired as the fallback signal).
                batcher.tap(sensorName, PrinterCommands.clampHeaterTarget(rawTarget).toDouble())
            },
            onHeaterOff = { sensorName ->
                // Off must NEVER be droppable (post-review fix 1, HIGH): the old cancel()+bare
                // immediate dispatch RACED the dispatcher's guards — if the scheduled nonzero
                // commit had just fired, the same-key S0 was rejected by the in-flight guard (or,
                // after a fast clear, by the 400ms debounce keyed on that accepted dispatch) with
                // NO retry → heater stayed hot. Routing Off through tap(0.0) rides the batcher's
                // guaranteed-delivery machinery instead: the display shows 0 instantly (the
                // working target wins), the canCommit loop re-waits while the key is in flight,
                // and the 500ms quiet window strictly exceeds the 400ms debounce — so the S0
                // always lands, exactly once, after the key clears.
                batcher.tap(sensorName, 0.0)
            },
            onApplyPreset = { preset ->
                dispatcher?.dispatch(
                    CommandRegistry.applyPreset,
                    ApplyPresetArgs(nozzle = preset.nozzle, bed = preset.bed, key = "preset_${preset.name}"),
                )
            },
            onCooldown = {
                dispatcher?.dispatch(CommandRegistry.cooldown, Unit)
            },
            onEmergencyStop = {
                dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit)
            },
        )
    }
}

/**
 * STATELESS preview/host seam — drives [TemperatureContent] from pure fixture data with no
 * [AppContainer], dispatcher, or holder. All callbacks default to no-ops.
 *
 * @param series        per-trace ring-buffer snapshots (oldest→newest).
 * @param setpoints     per-trace current targets (null = off).
 * @param legend        drawn sensors with current/target readouts.
 * @param graphRange    the shared Y-range for the graph.
 * @param traceColors   per-sensor chosen colors (keyed by sensor name).
 * @param traceVisibility per-sensor visibility (absent = visible).
 * @param isPrinting    drives [FloatingEStop] visibility.
 * @param seedHex       active theme seed for the 8-swatch Colorful pool (D-14).
 * @param dark          active theme dark flag for the 8-swatch Colorful pool.
 */
@Composable
fun TemperatureScreen(
    series: List<FloatArray> = emptyList(),
    setpoints: List<Float?> = emptyList(),
    legend: List<SensorReadout> = emptyList(),
    graphRange: ClosedFloatingPointRange<Float> = DEFAULT_GRAPH_RANGE,
    traceColors: Map<String, Color> = emptyMap(),
    traceVisibility: Map<String, Boolean> = emptyMap(),
    isPrinting: Boolean = false,
    failureText: String? = null,
    seedHex: String = ThemePrefs.DEFAULT_SEED,
    dark: Boolean = true,
    availableSensors: List<String> = emptyList(),
    selectedSensors: Set<String> = emptySet(),
    heaterLimits: Map<String, HeaterLimits> = emptyMap(),
    spoolPreset: PrinterCommands.Preset? = null,
    onBack: () -> Unit = {},
    onSetTraceColor: (String, Color) -> Unit = { _, _ -> },
    onSetTraceVisibility: (String, Boolean) -> Unit = { _, _ -> },
    onToggleSensor: (String, Boolean) -> Unit = { _, _ -> },
    onNudgeHeater: (String, Int) -> Unit = { _, _ -> },
    onHeaterOff: (String) -> Unit = {},
    onApplyPreset: (PrinterCommands.Preset) -> Unit = {},
    onCooldown: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        TemperatureContent(
            series = series,
            setpoints = setpoints,
            legend = legend,
            graphRange = graphRange,
            traceColors = traceColors,
            traceVisibility = traceVisibility,
            isPrinting = isPrinting,
            failureText = failureText,
            seedHex = seedHex,
            dark = dark,
            availableSensors = availableSensors,
            selectedSensors = selectedSensors,
            heaterLimits = heaterLimits,
            spoolPreset = spoolPreset,
            onBack = onBack,
            onSetTraceColor = onSetTraceColor,
            onSetTraceVisibility = onSetTraceVisibility,
            onToggleSensor = onToggleSensor,
            onNudgeHeater = onNudgeHeater,
            onHeaterOff = onHeaterOff,
            onApplyPreset = onApplyPreset,
            onCooldown = onCooldown,
        )
    }
}

/**
 * The pure, container-free Temperature rendering surface shared by BOTH [TemperatureScreen]
 * overloads. Holds no flow/dispatcher state; renders identically under `@Preview` and at runtime.
 */
@Composable
private fun TemperatureContent(
    series: List<FloatArray>,
    setpoints: List<Float?>,
    legend: List<SensorReadout>,
    graphRange: ClosedFloatingPointRange<Float>,
    traceColors: Map<String, Color>,
    traceVisibility: Map<String, Boolean>,
    isPrinting: Boolean,
    failureText: String?,
    seedHex: String,
    dark: Boolean,
    availableSensors: List<String> = emptyList(),
    selectedSensors: Set<String> = emptySet(),
    heaterLimits: Map<String, HeaterLimits> = emptyMap(),
    // Loaded-spool preheat preset (nozzle/bed from the active Spoolman filament) — null when no spool
    // is loaded or it carries no temp settings. Prepended to the PresetPicker list when present.
    spoolPreset: PrinterCommands.Preset? = null,
    onToggleSensor: (String, Boolean) -> Unit = { _, _ -> },
    rejectTicks: ImmutableMap<String, Long> = persistentMapOf(),
    // quick-rmr: pending (batched) heater working targets keyed by sensor name — wins over the
    // live target for display; the stateless/preview seam defaults to empty.
    workingTargets: ImmutableMap<String, Double> = persistentMapOf(),
    // quick-rmr: dispatcher in-flight keys, DISPLAY ONLY (heater AdjusterPanel busy dim).
    inFlight: Set<String> = emptySet(),
    onBack: () -> Unit,
    onSetTraceColor: (String, Color) -> Unit,
    onSetTraceVisibility: (String, Boolean) -> Unit,
    onNudgeHeater: (String, Int) -> Unit,
    onHeaterOff: (String) -> Unit = {},
    onApplyPreset: (PrinterCommands.Preset) -> Unit,
    onCooldown: () -> Unit,
    onEmergencyStop: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    // D-10: selected sensor triggers adjuster morph; null = graph fills Focus.
    // CR-01 (26-rev): store the selected sensor NAME and resolve the LIVE readout from [legend] on
    // every composition — a remembered SensorReadout snapshot freezes the adjuster's target (each
    // ± tap re-dispatches the same base) and never reflects live updates. If the sensor vanishes
    // (e.g. on reconnect), the lookup returns null and the Focus falls back to the graph automatically.
    var selectedName by remember { mutableStateOf<String?>(null) }
    val selectedSensor: SensorReadout? = selectedName?.let { n -> legend.firstOrNull { it.name == n } }
    // D-12: Field-mode (SensorList | PresetPicker).
    var fieldMode by remember { mutableStateOf<TempFieldMode>(TempFieldMode.SensorList) }
    // Top-level screen mode: Monitoring (read list) | Adjust (adjustable heaters, footers differ).
    var mode by remember { mutableStateOf(TempMode.Monitoring) }
    // Settings panel open flag — wired to Task 10/11; declared here so the footer button compiles.
    var settingsOpen by remember { mutableStateOf(false) }
    // D-14 / finding 5: dedicated Colorful-8 swatch pool, independent of the active palette mode.
    // DEFAULT_MAX_ITEMS=4 (ThemePrefs.kt:185) and Simple/HighContrast collapse the active pool,
    // so we NEVER source from t.pool.take(8). Force Colorful, maxItems=8.
    val colorfulSwatches: List<Color> = remember(seedHex, dark) {
        Palette.generate(
            seedHex = seedHex,
            dark = dark,
            maxItems = 8,
            simple = false,
            highContrast = false,
        ).pool.take(8).map { hex ->
            // Parse the "#rrggbb" hex from the generator to a fully-opaque sRGB Compose Color.
            // Mirrors TokenBridge.bake() — sRGB-only, never Oklab.
            val s = hex.removePrefix("#")
            val r = s.substring(0, 2).toInt(16)
            val g = s.substring(2, 4).toInt(16)
            val b = s.substring(4, 6).toInt(16)
            Color(red = r, green = g, blue = b, alpha = 0xFF)
        }
    }

    // ONE aligned visible-trace model (finding 4, D-14): filter series + setpoints + names + colors
    // TOGETHER by visibility so that every index-aligned list stays aligned.
    val visible: VisibleTraces = remember(series, setpoints, legend, traceVisibility, traceColors) {
        val keep = legend.indices.filter { i -> traceVisibility[legend[i].name] ?: true }
        VisibleTraces(
            series = keep.map { series.getOrNull(it) ?: FloatArray(0) },
            setpoints = keep.map { setpoints.getOrNull(it) },
            names = keep.map { legend[it].name },
            colors = keep.map { traceColors[legend[it].name]?.toArgb() },
        )
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        ScreenScaffold(
            focus = {
                // D-10: plain if/else morph — NOT AnimatedContent (which would remove + re-add the
                // GraphViewHost, thrashing the AndroidView factory and resetting trace data).
                // The GraphViewHost is ALWAYS in the tree unless a sensor is selected.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    when {
                        settingsOpen -> SensorPickerFocus(
                            available = availableSensors,
                            selected = selectedSensors,
                            onToggle = onToggleSensor,
                            onDone = { settingsOpen = false; selectedName = null },
                            isPrinting = isPrinting,
                            onEmergencyStop = onEmergencyStop,
                            uDp = grid.uDp,
                        )
                        selectedSensor == null -> {
                            // DEFAULT: graph fills the Focus shell — multi-trace, visibility-filtered.
                            FocusFrame(
                                title = stringResource(R.string.cd_launcher_temperature),
                                icon = DinghyIcons.LauncherTemperature,
                                uDp = grid.uDp,
                                modifier = Modifier.fillMaxSize(),
                                isPrinting = isPrinting,
                                onEmergencyStop = onEmergencyStop,
                                onPanic = onEmergencyStop,
                                contentInset = FocusInset / 2, // shared calibration-focus rhythm
                            ) {
                                GraphViewHost(
                                    tokens = t,
                                    series = visible.series,
                                    setpoints = visible.setpoints,
                                    traceColors = visible.colors,
                                    yRange = graphRange,
                                    showAxisLabels = true,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        mode == TempMode.Monitoring -> {
                            // MONITORING APPEARANCE POPUP: sensor selected in Monitoring mode —
                            // color grid + visibility toggle only (no heater controls).
                            val sensor = selectedSensor
                            FocusFrame(
                                title = sensor.label,
                                icon = iconForSensor(sensor.name),
                                uDp = grid.uDp,
                                modifier = Modifier.fillMaxSize(),
                                isPrinting = isPrinting,
                                onEmergencyStop = onEmergencyStop,
                                onPanic = onEmergencyStop,
                                contentInset = FocusInset / 2,
                            ) {
                                SensorAppearanceFocus(
                                    traceColor = traceColors[sensor.name],
                                    traceVisible = traceVisibility[sensor.name] ?: true,
                                    colorfulSwatches = colorfulSwatches,
                                    onColorSelect = { onSetTraceColor(sensor.name, it) },
                                    onVisibilityToggle = {
                                        onSetTraceVisibility(sensor.name, !(traceVisibility[sensor.name] ?: true))
                                    },
                                    onDone = { selectedName = null },
                                    uDp = grid.uDp,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        else -> { // mode == Adjust, a heater row selected
                            val sensor = selectedSensor
                            FocusFrame(
                                title = sensor.label,
                                icon = iconForSensor(sensor.name),
                                uDp = grid.uDp,
                                modifier = Modifier.fillMaxSize(),
                                isPrinting = isPrinting,
                                onEmergencyStop = onEmergencyStop,
                                onPanic = onEmergencyStop,
                                contentInset = FocusInset / 2, // shared calibration-focus rhythm
                            ) {
                                HeaterControlFocus(
                                    sensor = sensor,
                                    currentTarget = workingTargets[sensor.name] ?: sensor.target,
                                    scrubRange = heaterScrubberRange(heaterLimits[sensor.name]),
                                    busy = heaterDispatchKey(sensor.name) in inFlight,
                                    onNudge = { raw -> onNudgeHeater(sensor.name, raw) },
                                    onOff = { onHeaterOff(sensor.name) },
                                    onDone = { selectedName = null },
                                    uDp = grid.uDp,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }

                }

                // Error toast inside the Focus area.
                failureText?.let { msg ->
                    SeverityToast(
                        severity = Severity.Error,
                        text = msg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    )
                }
            },
            field = {
                // D-12 Field-takeover: SensorList (default) or PresetPicker.
                when (fieldMode) {
                    TempFieldMode.SensorList -> {
                        // In Adjust mode only show adjustable sensors; in Monitoring show all.
                        // idx is computed against the FULL legend so trace-color indexing is stable.
                        val rows = if (mode == TempMode.Adjust) legend.filter { it.isAdjustable } else legend
                        ListBlock(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                            items(rows, key = { it.name }) { sensor ->
                                val idx = legend.indexOf(sensor)
                                // D-14 same-hue invariant: row icon tinted to the chosen trace color.
                                val rowTint = traceColors[sensor.name] ?: t.seriesColor(idx)
                                val isSelected = selectedName == sensor.name
                                ListRow(
                                    selected = isSelected,
                                    onClick = {
                                        selectedName = if (isSelected) null else sensor.name
                                    },
                                    uDp = grid.uDp,
                                    leadingContent = {
                                        // R23: canonical 0.6U list-row icon.
                                        ListRowIcon(
                                            icon = iconForSensor(sensor.name),
                                            uDp = grid.uDp,
                                            tint = rowTint,
                                        )
                                    },
                                    trailingContent = {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "${fmt(sensor.current)}°",
                                                fontFamily = GeistMono,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = fsSp(17f, t.fs).sp,
                                                color = t.text,
                                            )
                                            sensor.target?.let { tgt ->
                                                Text(
                                                    text = "→ ${fmt(tgt)}°",
                                                    fontFamily = GeistMono,
                                                    fontSize = fsSp(15f, t.fs).sp,
                                                    color = rowTint,
                                                )
                                            }
                                        }
                                    },
                                ) {
                                    // Canonical list label (R22/R11).
                                    ListRowLabel(sensor.label)
                                }
                            }
                        }
                        // Mode-specific footers: Monitoring = Back · Settings · Adjust-enter;
                        // Adjust = Presets · Cooldown · Monitor-return (no Back).
                        if (mode == TempMode.Monitoring) {
                            FootButtonBar(uDp = grid.uDp) {
                                OutlinedControl(
                                    label = "",
                                    onClick = onBack,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Accent,
                                    icon = DinghyIcons.Back,
                                )
                                OutlinedControl(
                                    label = "",
                                    onClick = { settingsOpen = true; selectedName = null },
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Accent,
                                    icon = DinghyIcons.TempSettings,
                                )
                                OutlinedControl(
                                    label = "",
                                    onClick = { mode = TempMode.Adjust; selectedName = null; settingsOpen = false },
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Accent,
                                    icon = DinghyIcons.OutputHeater,
                                )
                            }
                        } else {
                            FootButtonBar(uDp = grid.uDp) {
                                OutlinedControl(
                                    label = stringResource(R.string.temp_presets),
                                    onClick = { fieldMode = TempFieldMode.PresetPicker },
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Accent,
                                )
                                OutlinedControl(
                                    label = stringResource(R.string.temp_cooldown),
                                    onClick = onCooldown,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Warn,
                                )
                                OutlinedControl(
                                    label = "",
                                    onClick = { mode = TempMode.Monitoring; selectedName = null },
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Accent,
                                    icon = DinghyIcons.MonitorMode,
                                )
                            }
                        }
                    }

                    TempFieldMode.PresetPicker -> {
                        // D-12: Field-takeover preset picker (the old full-screen scrim is retired).
                        ListBlock(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                            // Loaded-spool preset FIRST when available (heat to the active filament's temps).
                            spoolPreset?.let { sp ->
                                item(key = "__spool_preset__") {
                                    PresetListRow(sp, grid.uDp) {
                                        onApplyPreset(sp)
                                        fieldMode = TempFieldMode.SensorList
                                    }
                                }
                            }
                            items(PrinterCommands.MATERIAL_PRESETS, key = { it.name }) { preset ->
                                PresetListRow(preset, grid.uDp) {
                                    onApplyPreset(preset)
                                    fieldMode = TempFieldMode.SensorList
                                }
                            }
                        }
                        FootButtonBar(
                            uDp = grid.uDp,
                        ) {
                            OutlinedControl(
                                label = "",
                                onClick = { fieldMode = TempFieldMode.SensorList },
                                modifier = Modifier.weight(1f),
                                intent = Intent.Accent, // R5: Back = accent
                                icon = DinghyIcons.Back,
                            )
                        }
                    }
                }
            },
        )

    }
}

/**
 * Settings Focus morph: lists all `temperature_sensor *` capability objects so the user can
 * toggle which ones are actively monitored. Heaters (extruder*, heater_bed, etc.) are NOT listed —
 * they are always monitored; only passive temperature_sensor objects appear here.
 */
@Composable
private fun SensorPickerFocus(
    available: List<String>,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDone: () -> Unit,
    isPrinting: Boolean,
    onEmergencyStop: () -> Unit,
    uDp: androidx.compose.ui.unit.Dp,
) {
    val t = LocalTokens.current
    FocusFrame(
        title = stringResource(R.string.temp_settings_title),
        icon = DinghyIcons.TempSettings,
        uDp = uDp,
        modifier = Modifier.fillMaxSize(),
        isPrinting = isPrinting,
        onEmergencyStop = onEmergencyStop,
        onPanic = onEmergencyStop,
        contentInset = FocusInset / 2,
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListBlock(modifier = Modifier.weight(1f)) {
                items(available, key = { it }) { name ->
                    val isOn = name in selected
                    ListRow(
                        selected = isOn,
                        onClick = { onToggle(name, !isOn) },
                        uDp = uDp,
                        leadingContent = {
                            ListRowIcon(icon = iconForSensor(name), uDp = uDp, tint = t.text2)
                        },
                        trailingContent = {
                            DinghyIconView(
                                icon = if (isOn) DinghyIcons.Visibility else DinghyIcons.VisibilityOff,
                                tint = if (isOn) t.accent else t.text2,
                            )
                        },
                    ) { ListRowLabel(sensorPickerLabel(name)) }
                }
            }
            OutlinedControl(
                label = stringResource(R.string.common_done),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                intent = Intent.Accent,
            )
        }
    }
}

private fun sensorPickerLabel(objectName: String): String =
    objectName.removePrefix("temperature_sensor ").uppercase()

/**
 * Monitoring-mode appearance popup (Task-10): 4×2 color grid + visibility toggle + Done.
 *
 * Shown in the Focus when a sensor is tapped in Monitoring mode. No temperature controls —
 * purely graph appearance: pick a trace color from the Colorful-8 pool and show/hide the trace.
 */
@Composable
private fun SensorAppearanceFocus(
    traceColor: Color?,
    traceVisible: Boolean,
    colorfulSwatches: List<Color>,
    onColorSelect: (Color) -> Unit,
    onVisibilityToggle: () -> Unit,
    onDone: () -> Unit,
    uDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 4×2 color grid fills the space between the FocusFrame header and the bottom buttons.
        val swatchRows = colorfulSwatches.chunked(4) // two rows of four
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            // Center the two rows in the leftover space; rows wrap to the (width-driven) dot height.
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            swatchRows.forEach { rowColors ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowColors.forEach { poolColor ->
                        val isSelected = traceColor != null && poolColor.toArgb() == traceColor.toArgb()
                        // Each dot is a centered, WIDTH-DRIVEN square (¼ of the row) capped at ~1.4U so
                        // it stays large but never balloons to the row height (the old overlap/overflow bug).
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .sizeIn(maxWidth = uDp * 1.4f, maxHeight = uDp * 1.4f)
                                    .clip(CircleShape)
                                    .background(poolColor) // data color — THEME-01 carve-out
                                    .border(
                                        BorderStroke(if (isSelected) 4.dp else 1.dp, t.accentLine),
                                        CircleShape,
                                    )
                                    .clickable { onColorSelect(poolColor) },
                            )
                        }
                    }
                }
            }
        }
        // Bottom buttons: Visibility · Done.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedControl(
                label = "",
                onClick = onVisibilityToggle,
                modifier = Modifier.weight(1f),
                intent = if (traceVisible) Intent.Accent else Intent.Neutral,
                icon = if (traceVisible) DinghyIcons.Visibility else DinghyIcons.VisibilityOff,
            )
            OutlinedControl(
                label = stringResource(R.string.common_done),
                onClick = onDone,
                modifier = Modifier.weight(1f),
                intent = Intent.Accent,
            )
        }
    }
}

/**
 * The heater-control surface shown in the Focus when an adjustable sensor is selected in Adjust mode
 * (D-10 morph). Pure heater target control — NO color/visibility (appearance is Monitoring-only,
 * Task 10's [SensorAppearanceFocus]).
 *
 * Layout top→bottom (owner 2026-06-14): value display → scrubber → ±1 buttons → Off/Done.
 *  - **Value:** big GeistMono readout (hero style, matching [AdjusterPanel]) showing the live scrubber
 *    position while dragging (`scrubLive`) else the committed working target. Absorbs the slack between
 *    the header and the scrubber.
 *  - **Scrubber (coarse):** bare 0..max_temp track — [Scrubber.onValueChange] updates the LOCAL preview
 *    only (no dispatch); [Scrubber.onSettle] commits the absolute target once on pointer-up.
 *  - **±1 (fine):** two [OutlinedControl] steppers, each nudging the target by ±[TEMP_FINE_STEP] (no
 *    1/5/10 picker — coarse is the scrubber's job).
 * Both committers route through the SAME trailing-commit batcher via [onNudge] (absolute, batcher-clamped).
 *
 * @param currentTarget the pending working-or-live SETPOINT (null = heater off → shows 0, never live temp).
 * @param scrubRange    0..max_temp from [heaterScrubberRange] (config limits or global clamp).
 */
@Composable
private fun HeaterControlFocus(
    sensor: SensorReadout,
    currentTarget: Double?,
    scrubRange: ClosedFloatingPointRange<Float>,
    busy: Boolean,
    onNudge: (Int) -> Unit,        // absolute new target (clamped by the batcher)
    onOff: () -> Unit,
    onDone: () -> Unit,
    uDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    // SETPOINT ONLY (owner 2026-06-14): the display/scrubber show the target, never the live temp.
    // An off heater (no target) reads 0 — adjusting up sets a setpoint from 0.
    val seed: Double = currentTarget ?: 0.0
    var scrubLive by remember(sensor.name) { mutableStateOf<Float?>(null) }
    val shown: Int = (scrubLive?.toDouble() ?: seed).roundToInt()
    val dim = if (busy) Modifier.alpha(0.38f) else Modifier

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ── Value display (live scrubber position while dragging, else the working target) ──
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Row {
                Text(
                    text = shown.toString(),
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(48f, t.fs).sp,
                    color = t.text,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = "°C",
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = fsSp(28f, t.fs).sp,
                    color = t.text2,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        // ── Scrubber (coarse) — bare track; preview on drag, commit on release ──
        Scrubber(
            name = "",
            value = seed.toFloat(),
            range = scrubRange,
            step = 1f,
            uDp = uDp,
            bare = true,
            onValueChange = { scrubLive = it },
            onSettle = { v -> scrubLive = null; onNudge(v.roundToInt()) },
            modifier = Modifier.fillMaxWidth(),
        )
        // ── ±1 fine adjust (no 1/5/10 picker — the scrubber is the coarse control) ──
        CompositionLocalProvider(LocalUnitDp provides uDp) {
            Row(
                modifier = Modifier.fillMaxWidth().height(uDp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedControl(
                    label = "",
                    onClick = { scrubLive = null; onNudge(shown - TEMP_FINE_STEP) },
                    modifier = Modifier.weight(1f).then(dim),
                    intent = Intent.Accent,
                    icon = DinghyIcons.Decrease,
                    contentDescription = stringResource(R.string.cd_decrement),
                )
                OutlinedControl(
                    label = "",
                    onClick = { scrubLive = null; onNudge(shown + TEMP_FINE_STEP) },
                    modifier = Modifier.weight(1f).then(dim),
                    intent = Intent.Accent,
                    icon = DinghyIcons.Increase,
                    contentDescription = stringResource(R.string.cd_increment),
                )
            }
        }
        // ── Off / Done — 1U row (LocalUnitDp + height(uDp)) so they match the ± buttons; without this
        // they sized to text height and rendered shorter than the steppers on flox (owner 2026-06-14). ──
        CompositionLocalProvider(LocalUnitDp provides uDp) {
            Row(
                modifier = Modifier.fillMaxWidth().height(uDp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedControl(label = stringResource(R.string.output_off), onClick = onOff, modifier = Modifier.weight(1f), intent = Intent.Warn)
                OutlinedControl(label = stringResource(R.string.common_done), onClick = onDone, modifier = Modifier.weight(1f), intent = Intent.Accent)
            }
        }
    }
}

/** One preset row in the PresetPicker field-takeover (shared by the loaded-spool + material rows). */
@Composable
private fun PresetListRow(
    preset: PrinterCommands.Preset,
    uDp: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val t = LocalTokens.current
    ListRow(
        selected = false,
        onClick = onClick,
        uDp = uDp,
        trailingContent = {
            Text(
                text = "${preset.nozzle}° / ${preset.bed}°",
                fontFamily = GeistMono,
                fontSize = fsSp(17f, t.fs).sp,
                color = t.text2,
            )
        },
    ) {
        Text(
            text = preset.name,
            fontFamily = Geist,
            fontSize = fsSp(20f, t.fs).sp, // R11 list-label default
            color = t.text,
        )
    }
}

// ── Icon lookup ────────────────────────────────────────────────────────────────────────────────

/**
 * Registry icon for a heater sensor name, falling back to [DinghyIcons.LauncherTemperature]
 * when no dedicated glyph exists (D-24 registry-only law — NEVER auto-pick).
 */
private fun iconForSensor(name: String) = when {
    name.startsWith("extruder") -> DinghyIcons.Nozzle
    name == "heater_bed" -> DinghyIcons.HeatBed
    else -> DinghyIcons.LauncherTemperature
}

// ── Helpers ────────────────────────────────────────────────────────────────────────────────────

/**
 * The per-heater dispatch key (the same string passed into [SetHeaterArgs]). R10 (26.5-03): single
 * construction point so the [works.mees.dinghy.command.CommandDispatcher.rejectedKey] filter can
 * never drift from the key the dispatch actually uses.
 */
private fun heaterDispatchKey(sensorName: String): String = "set_heater_$sensorName"

/** Tabular-friendly one-decimal temperature formatting (rounded, not truncated). */
private fun fmt(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()

// ── PresetSelector (retained internal for PrintStatusScreen reuse — 16-06) ────────────────────

/**
 * The fixed material-preset selector (TEMP-03) — a full-screen scrim of keyboard-free preset tiles
 * ([PrinterCommands.MATERIAL_PRESETS]) plus a Cancel. Each tile dispatches `applyPreset(nozzle,bed)`.
 *
 * `internal` (not `private`) so the Print-Status Preheat OpenSelector fallback (16-06) reuses the SAME
 * keyboard-free preset chooser. On the Temperature screen itself this is superseded by the in-Field
 * PresetPicker takeover (D-12), but the internal scrim stays for Print-Status backward compatibility.
 * Signature/behavior unchanged from the pre-Phase-26 version.
 */
@Composable
internal fun PresetSelector(
    inFlight: Set<String>,
    onPreset: (PrinterCommands.Preset) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalTokens.current
    // Opaque full-screen scrim (mirrors ConfirmGuard's 03-08 opaque-scrim fix) so the graph behind
    // it doesn't bleed through the keyboard-free preset tiles.
    Box(
        Modifier
            .fillMaxSize()
            .background(t.bg)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.temp_preheat_preset),
                color = t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(22f, t.fs).sp,
            )
            for (p in PrinterCommands.MATERIAL_PRESETS) {
                val key = "preset_${p.name}"
                OutlinedControl(
                    label = "${p.name}   ${p.nozzle}° / ${p.bed}°",
                    onClick = { if (key !in inFlight) onPreset(p) },
                    modifier = Modifier.fillMaxWidth(),
                    intent = Intent.Accent,
                )
            }
            OutlinedControl(
                label = stringResource(R.string.common_cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                intent = Intent.Accent, // R5: dismiss-without-loss = plain nav
            )
        }
    }
}
