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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentListOf
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
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.components.AdjusterPanel
import works.mees.dinghy.designsystem.components.DetailCard
import works.mees.dinghy.designsystem.components.FloatingEStop
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.IncrementPicker
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.render.GraphViewHost
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.Palette
import works.mees.dinghy.theme.ThemePrefs
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.theme.seriesColor
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState

/** The heater target step (°C) — matching the old TEMP_STEP for fine enough control. */
private val TEMP_STEPS = persistentListOf(1.0, 5.0, 10.0)
private const val TEMP_DEFAULT_STEP = 5.0

/** Field mode for the Temperature panel — SensorList (default) or PresetPicker (D-12). */
private sealed class TempFieldMode {
    data object SensorList : TempFieldMode()
    data object PresetPicker : TempFieldMode()
}

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
 * `gutter = null`; [FootButtonBar] inside the field lambda (redesigned-screen law).
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
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
    var failureText by remember { mutableStateOf<String?>(null) }

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
        if (failureText != null) { delay(4_000); failureText = null }
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
            seedHex = themeTuple.seedHex,
            dark = themeTuple.dark,
            onBack = onBack,
            onSetTraceColor = { sensorName, color ->
                holder.setTraceColor(sensorName, color)
                container.setTraceColor(sensorName, color.toArgb())
            },
            onSetTraceVisibility = { sensorName, visible ->
                holder.setTraceVisibility(sensorName, visible)
                container.setTraceVisibility(sensorName, visible)
            },
            onNudgeHeater = { sensorName, rawTarget ->
                val clamped = PrinterCommands.clampHeaterTarget(rawTarget)
                if (clamped.toString() !in inFlight) {
                    dispatcher?.dispatch(
                        CommandRegistry.setHeater,
                        SetHeaterArgs(sensorName, clamped, "set_heater_$sensorName"),
                    )
                }
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
    onBack: () -> Unit = {},
    onSetTraceColor: (String, Color) -> Unit = { _, _ -> },
    onSetTraceVisibility: (String, Boolean) -> Unit = { _, _ -> },
    onNudgeHeater: (String, Int) -> Unit = { _, _ -> },
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
            onBack = onBack,
            onSetTraceColor = onSetTraceColor,
            onSetTraceVisibility = onSetTraceVisibility,
            onNudgeHeater = onNudgeHeater,
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
    onBack: () -> Unit,
    onSetTraceColor: (String, Color) -> Unit,
    onSetTraceVisibility: (String, Boolean) -> Unit,
    onNudgeHeater: (String, Int) -> Unit,
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
    // Session step memory for the heater adjuster.
    var activeStep by remember { mutableStateOf(TEMP_DEFAULT_STEP) }
    // D-12: Field-mode (SensorList | PresetPicker).
    var fieldMode by remember { mutableStateOf<TempFieldMode>(TempFieldMode.SensorList) }
    // E-stop confirm guard.
    var showEstopGuard by remember { mutableStateOf(false) }

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
                        .weight(1f)
                        .padding(8.dp),
                ) {
                    if (selectedSensor == null) {
                        // DEFAULT: graph fills Focus — multi-trace with visibility-filtered model.
                        GraphViewHost(
                            tokens = t,
                            series = visible.series,
                            setpoints = visible.setpoints,
                            traceColors = visible.colors,
                            yRange = graphRange,
                            showAxisLabels = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // ADJUSTER: sensor selected — graph controls + optional heater adjuster.
                        // selectedSensor is a non-null LIVE readout here (resolved from legend above).
                        val sensor = selectedSensor
                        DetailCard(modifier = Modifier.fillMaxSize()) {
                            TemperatureAdjusterFocus(
                                sensor = sensor,
                                traceColor = traceColors[sensor.name],
                                traceVisible = traceVisibility[sensor.name] ?: true,
                                colorfulSwatches = colorfulSwatches,
                                activeStep = activeStep,
                                currentTarget = sensor.target,
                                onColorSelect = { color -> onSetTraceColor(sensor.name, color) },
                                onVisibilityToggle = {
                                    onSetTraceVisibility(sensor.name, !(traceVisibility[sensor.name] ?: true))
                                },
                                onDecrement = { current ->
                                    val rawTarget = (current - activeStep).roundToInt()
                                    onNudgeHeater(sensor.name, rawTarget)
                                },
                                onIncrement = { current ->
                                    val rawTarget = (current + activeStep).roundToInt()
                                    onNudgeHeater(sensor.name, rawTarget)
                                },
                                onOff = { onNudgeHeater(sensor.name, 0) },
                                onDone = { selectedName = null },
                                onStepSelect = { activeStep = it },
                                uDp = grid.uDp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                            )
                        }
                    }

                    // FloatingEStop: Box sibling, printing-only (P24 D-04 / PATTERNS §4).
                    FloatingEStop(
                        visible = isPrinting,
                        onClick = { showEstopGuard = true },
                        uDp = grid.uDp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp),
                    )
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
                        ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            items(legend, key = { it.name }) { sensor ->
                                val idx = legend.indexOf(sensor)
                                // D-14 same-hue invariant: row icon tinted to the chosen trace color.
                                val rowTint = traceColors[sensor.name] ?: t.seriesColor(idx)
                                val isSelected = selectedName == sensor.name
                                ListRow(
                                    selected = isSelected,
                                    onClick = {
                                        selectedName = if (isSelected) null else sensor.name
                                        // Reset step to default on new selection.
                                        if (!isSelected) activeStep = TEMP_DEFAULT_STEP
                                    },
                                    uDp = grid.uDp,
                                    leadingContent = {
                                        DinghyIconView(
                                            icon = iconForSensor(sensor.name),
                                            tint = rowTint,
                                            sizeDp = 22.dp,
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
                                    Text(
                                        text = sensor.label,
                                        fontFamily = Geist,
                                        fontSize = fsSp(18f, t.fs).sp,
                                        color = t.text,
                                    )
                                }
                            }
                        }
                        val heating = legend.any { it.target != null }
                        FootButtonBar(
                            uDp = grid.uDp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            OutlinedControl(
                                label = "",
                                onClick = onBack,
                                modifier = Modifier.weight(1f),
                                intent = Intent.Neutral,
                                icon = DinghyIcons.Back,
                            )
                            OutlinedControl(
                                label = "Presets",
                                onClick = { fieldMode = TempFieldMode.PresetPicker },
                                modifier = Modifier.weight(1f),
                                intent = if (heating) Intent.Neutral else Intent.Accent,
                            )
                            OutlinedControl(
                                label = "Cooldown",
                                onClick = onCooldown,
                                modifier = Modifier.weight(1f),
                                intent = Intent.Warn,
                            )
                        }
                    }

                    TempFieldMode.PresetPicker -> {
                        // D-12: Field-takeover preset picker (the old full-screen scrim is retired).
                        ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            items(PrinterCommands.MATERIAL_PRESETS, key = { it.name }) { preset ->
                                ListRow(
                                    selected = false,
                                    onClick = {
                                        onApplyPreset(preset)
                                        fieldMode = TempFieldMode.SensorList
                                    },
                                    uDp = grid.uDp,
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
                                        fontSize = fsSp(18f, t.fs).sp,
                                        color = t.text,
                                    )
                                }
                            }
                        }
                        FootButtonBar(
                            uDp = grid.uDp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            OutlinedControl(
                                label = "",
                                onClick = { fieldMode = TempFieldMode.SensorList },
                                modifier = Modifier.weight(1f),
                                intent = Intent.Neutral,
                                icon = DinghyIcons.Back,
                            )
                        }
                    }
                }
            },
            gutter = null, // MANDATORY: redesigned screens null the gutter (FootButtonBar in field)
        )

        // ConfirmGuard for the E-stop (Box sibling — SpoolScreen.kt pattern §4).
        if (showEstopGuard) {
            ConfirmGuard(
                title = stringResource(R.string.printstatus_estop_guard_title),
                message = stringResource(R.string.printstatus_estop_guard_message),
                confirmLabel = stringResource(R.string.printstatus_estop_guard_confirm),
                cancelLabel = stringResource(R.string.common_cancel),
                onConfirm = {
                    onEmergencyStop()
                    showEstopGuard = false
                },
                onCancel = { showEstopGuard = false },
                destructive = true,
            )
        }
    }
}

/**
 * The adjuster surface that appears in the Focus when a sensor is selected (D-10 morph).
 *
 * For EVERY selected sensor: show/hide trace toggle + 8-swatch Colorful-pool color row (D-14).
 * For ADJUSTABLE heaters: [AdjusterPanel] with target temp stepper + per-heater Off (D-13).
 */
@Composable
private fun TemperatureAdjusterFocus(
    sensor: SensorReadout,
    traceColor: Color?,
    traceVisible: Boolean,
    colorfulSwatches: List<Color>,
    activeStep: Double,
    currentTarget: Double?,
    onColorSelect: (Color) -> Unit,
    onVisibilityToggle: () -> Unit,
    onDecrement: (Double) -> Unit,
    onIncrement: (Double) -> Unit,
    onOff: () -> Unit,
    onDone: () -> Unit,
    onStepSelect: (Double) -> Unit,
    uDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Graph controls: show/hide toggle + 8-swatch color row ────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Show/hide trace toggle (D-14 — Visibility / VisibilityOff from registry).
            OutlinedControl(
                label = "",
                onClick = onVisibilityToggle,
                modifier = Modifier.weight(1f),
                intent = if (traceVisible) Intent.Accent else Intent.Neutral,
                icon = if (traceVisible) DinghyIcons.Visibility else DinghyIcons.VisibilityOff,
            )
            // D-14: Done button to return to the graph view.
            OutlinedControl(
                label = "Done",
                onClick = onDone,
                modifier = Modifier.weight(1f),
                intent = Intent.Neutral,
            )
        }

        // 8-swatch Colorful-pool color row (D-14 / THEME-01 data carve-out).
        // ALWAYS exactly 8 swatches from the dedicated Colorful pool (finding 5 — NOT t.pool.take(8)).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            colorfulSwatches.forEach { poolColor ->
                val isSelected = traceColor != null && poolColor.toArgb() == traceColor.toArgb()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(poolColor) // data color — THEME-01 carve-out
                        .border(
                            BorderStroke(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = t.accentLine,
                            ),
                            CircleShape,
                        )
                        .clickable { onColorSelect(poolColor) },
                )
            }
        }

        // ── Heater controls (D-13 / D-22): only for adjustable sensors ──────────────────
        if (sensor.isAdjustable) {
            // CR-02 (26-rev): when the heater is OFF (target == null), seed the adjuster from the
            // live temperature so the stepper can heat an idle heater — AdjusterPanel renders DASH
            // and disables both tiles on a null value, which would otherwise make the primary
            // heater-setpoint control inert until a Preset was applied. clampHeaterTarget keeps the
            // nudged result legal.
            val currentValue: Double? = currentTarget ?: sensor.current.let { if (it > 0.0) it else null }

            Spacer(modifier = Modifier.weight(1f))

            AdjusterPanel(
                icon = iconForSensor(sensor.name),
                name = sensor.label,
                value = currentValue,
                unit = "°C",
                baseline = null, // Temperature target has no persistent "entry baseline" — no Reset
                decimals = 0,
                onDecrement = {
                    val base = currentValue ?: 0.0
                    onDecrement(base)
                },
                onIncrement = {
                    val base = currentValue ?: 0.0
                    onIncrement(base)
                },
                onReset = null, // No baseline / Reset for temperature (target can be 0 via Off)
                enabled = true,
                incrementPicker = {
                    IncrementPicker(
                        steps = TEMP_STEPS,
                        activeStep = activeStep,
                        onSelect = onStepSelect,
                        uDp = uDp,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // D-13: Per-heater Off (P19 GAP-A precedent — Intent.Warn, target=0).
            OutlinedControl(
                label = "Off",
                onClick = onOff,
                modifier = Modifier.fillMaxWidth(),
                intent = Intent.Warn,
            )
        }
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
                text = "Preheat preset",
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
                label = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                intent = Intent.Neutral,
            )
        }
    }
}
