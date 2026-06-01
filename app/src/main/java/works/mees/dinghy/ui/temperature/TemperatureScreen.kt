package works.mees.dinghy.ui.temperature

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.designsystem.ScrubberPage
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.net.JsonRpcMethods
import works.mees.dinghy.render.GraphViewHost
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/** The fixed shared graph Y-range (°C) — 0..350 covers the [PrinterCommands] setHeater clamp ceiling. */
private val Y_RANGE = 0f..350f

/** Scrubber step for heater targets (°C) — coarse enough for fat-finger taps, fine enough for control. */
private const val TEMP_STEP = 5f

/**
 * The Temperature panel (TEMP-01..04) — the heater monitor + control surface, LAW per
 * docs/ui_design/images/09-temperature-graph.png + README §9 (captured verbatim). Mirrors the Move
 * panel's holder + [ScreenScaffold] + dispatch + GraphViewHost shape. Built on Focus/Field/Gutter;
 * all color routes through [LocalTokens] role tokens (THEME-01); all live numbers use GeistMono.
 *
 * ## Focus — the legend (TEMP-01)
 * Each drawn sensor's current/target temperature, colored per trace (nozzle=heat / bed=accent /
 * chamber=violet, the same palette the graph uses). A single sensor renders large value-on-glyph; two
 * or three render a compact legend list. Tapping a sensor's value opens the [ScrubberPage] (TEMP-02).
 *
 * ## Field — the multi-trace history graph (TEMP-04, G-1 fix)
 * The 05-04 N-trace [GraphViewHost] hosting [TemperatureHolder.series] (seeded deterministically from
 * the temperature_store backfill on connect, appended live) with a FIXED [Y_RANGE] and the per-sensor
 * dashed [TemperatureHolder.setpoints] line.
 *
 * ## Gutter — Back · Presets · Cooldown
 *  - Back ([Intent.Danger], red) → [onBack].
 *  - Presets ([Intent.Neutral]) → reveals the fixed PLA/PETG/ABS/TPU selector ([PrinterCommands.MATERIAL_PRESETS]);
 *    each dispatches `applyPreset(nozzle,bed)`. Keyboard-free (D-01).
 *  - Cooldown ([Intent.Warn], amber) → dispatches [PrinterCommands.COOLDOWN] (TURN_OFF_HEATERS) (TEMP-03).
 *
 * Every action dispatches via the per-session [works.mees.dinghy.command.CommandDispatcher] as a
 * `printer.gcode.script` ([JsonRpcMethods.GCODE_SCRIPT]) carrying `scriptParams(PrinterCommands.*)` —
 * never a raw rpc request. A control whose dispatch key is in-flight is disabled (PRIM-05 / T-05-05-T).
 * A dispatcher [DispatchEvent.Failure] surfaces a [SeverityToast].
 *
 * @param container the service-locator (provides the live `printerState` + the session dispatcher).
 * @param holder    the toolkit-agnostic [TemperatureHolder] (legend + per-sensor series/setpoints).
 * @param onBack    invoked by the red Back gutter tile.
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
        dispatcher?.inFlight ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val series by holder.series.collectAsStateWithLifecycle()
    val setpoints by holder.setpoints.collectAsStateWithLifecycle()
    val legend by holder.legend.collectAsStateWithLifecycle()
    val t = LocalTokens.current

    // The sensor whose ScrubberPage is open (null = none); and whether the Presets selector is showing.
    var scrubberTarget by remember { mutableStateOf<SensorReadout?>(null) }
    var showPresets by remember { mutableStateOf(false) }
    var failureText by remember { mutableStateOf<String?>(null) }

    // Surface a dispatch failure (redacted message) as an error toast (PRIM-04). Reset on session swap.
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

    // One dispatch helper — every action funnels through GCODE_SCRIPT + scriptParams (no raw rpc).
    fun script(key: String, gcode: String) {
        if (key in inFlight) return
        dispatcher?.dispatch(key, JsonRpcMethods.GCODE_SCRIPT, PrinterCommands.scriptParams(gcode))
    }

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            focus = {
                TemperatureLegend(
                    legend = legend,
                    onTapSensor = { scrubberTarget = it },
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            },
            field = {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GraphViewHost(
                        tokens = t,
                        series = series,
                        setpoints = setpoints,
                        yRange = Y_RANGE,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    failureText?.let { msg ->
                        SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth())
                    }
                }
            },
            gutter = {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedControl(
                        label = "Back",
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Danger,
                    )
                    OutlinedControl(
                        label = "Presets",
                        onClick = { showPresets = true },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Neutral,
                    )
                    OutlinedControl(
                        label = "Cooldown",
                        onClick = { script("cooldown", PrinterCommands.COOLDOWN) },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Warn,
                    )
                }
            },
        )

        // TEMP-02: tapping a sensor value opens the keyboard-free ScrubberPage (NO keypad, D-01).
        scrubberTarget?.let { sensor ->
            val seedTarget = (sensor.target ?: sensor.current).toFloat().coerceIn(Y_RANGE)
            ScrubberPage(
                label = sensor.label,
                value = seedTarget,
                range = 0f..maxTempFor(sensor.name),
                step = TEMP_STEP,
                unit = "°C",
                onValueChange = { /* live preview only; the printer is set on Apply */ },
                onCancel = { scrubberTarget = null },
                onApply = { v ->
                    script("set_${sensor.name}", PrinterCommands.setHeater(sensor.name, v.roundToInt()))
                    scrubberTarget = null
                },
            )
        }

        // TEMP-03: the fixed material-preset selector (keyboard-free tiles), dismissable.
        if (showPresets) {
            PresetSelector(
                inFlight = inFlight,
                onPreset = { p ->
                    script("preset_${p.name}", PrinterCommands.applyPreset(p.nozzle, p.bed))
                    showPresets = false
                },
                onDismiss = { showPresets = false },
            )
        }
    }
}

/**
 * The Focus legend (TEMP-01): one row per drawn sensor (current/target), colored per trace. A single
 * sensor renders large value-on-glyph; multiple render a compact list. Tapping a row's value opens the
 * scrubber (TEMP-02). Trace color: nozzle=heat / bed=accent / chamber=violet (mockup §9).
 */
@Composable
private fun TemperatureLegend(
    legend: List<SensorReadout>,
    onTapSensor: (SensorReadout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    if (legend.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "No heaters",
                color = t.text3,
                fontFamily = GeistMono,
                fontSize = fsSp(20f, t.fs).sp,
            )
        }
        return
    }
    if (legend.size == 1) {
        val s = legend[0]
        // Single-sensor value-on-glyph: big current temp + dashed-style setpoint line below.
        Box(
            modifier.clickable { onTapSensor(s) },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = s.label,
                    color = t.text3,
                    fontFamily = GeistMono,
                    fontSize = fsSp(16f, t.fs).sp,
                )
                Text(
                    text = "${fmt(s.current)}°",
                    color = traceColor(0, t),
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(56f, t.fs).sp,
                )
                Text(
                    text = s.target?.let { "→ ${fmt(it)}°" } ?: "off",
                    color = t.text2,
                    fontFamily = GeistMono,
                    fontSize = fsSp(20f, t.fs).sp,
                )
            }
        }
        return
    }
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        legend.forEachIndexed { i, s ->
            LegendRow(
                readout = s,
                color = traceColor(i, t),
                onClick = { onTapSensor(s) },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

/** One compact legend row: a trace-colored label + tappable current/target value. */
@Composable
private fun LegendRow(
    readout: SensorReadout,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Row(
        modifier
            .clip(shape)
            .border(BorderStroke(2.dp, color), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = readout.label,
            color = color,
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(16f, t.fs).sp,
        )
        Text(
            text = readout.target?.let { "${fmt(readout.current)} / ${fmt(it)}°" } ?: "${fmt(readout.current)}°",
            color = t.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Bold,
            fontSize = fsSp(22f, t.fs).sp,
        )
    }
}

/**
 * The fixed material-preset selector (TEMP-03) — a full-screen scrim of keyboard-free preset tiles
 * ([PrinterCommands.MATERIAL_PRESETS]) plus a Cancel. Each tile dispatches `applyPreset(nozzle,bed)`.
 */
@Composable
private fun PresetSelector(
    inFlight: Set<String>,
    onPreset: (PrinterCommands.Preset) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalTokens.current
    // Opaque full-screen scrim (mirrors ConfirmGuard's 03-08 opaque-scrim fix) so the graph behind it
    // doesn't bleed through the keyboard-free preset tiles.
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

/** Trace palette (mockup §9): index 0 = nozzle (heat), 1 = bed (accent), 2 = chamber (violet). */
private fun traceColor(index: Int, t: ThemeTokens): Color = when (index) {
    0 -> t.heat
    1 -> t.accent
    else -> t.violet
}

/**
 * Per-sensor scrubber ceiling (°C). Bed-class heaters top out far lower than a nozzle, so the scrubber
 * doesn't waste its travel on unreachable targets. `setHeater` clamps to 0..350 regardless (defense in
 * depth); this is just the UI affordance range.
 */
private fun maxTempFor(name: String): Float = when {
    name == "heater_bed" -> 120f
    name.startsWith("heater_generic") -> 120f // chamber-class heaters are low-temp.
    else -> 300f // nozzle.
}

/** Tabular-friendly one-decimal temperature formatting (rounded, not truncated). */
private fun fmt(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()
