package works.mees.dinghy.ui.extrude

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.CommandSpec
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.ExtrudeArgs
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.command.SelectToolArgs
import works.mees.dinghy.command.SetHeaterArgs
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.NumpadPage
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/** Default highlighted length (mm) — must be a member of [DISTANCE_PRESETS]. */
private const val DEFAULT_DISTANCE = 5.0

/** Default feedrate (mm/s) — must be a member of [SPEED_PRESETS]. */
private const val DEFAULT_SPEED_MM_S = 5

/** The fixed 4-up filament-length presets (mm). Selecting one fills the focus Distance readout. */
private val DISTANCE_PRESETS = listOf(1.0, 5.0, 25.0, 50.0)

/** The fixed 4-up extrusion-feedrate presets (mm/s). Selecting one fills the focus Speed readout. */
private val SPEED_PRESETS = listOf(1, 2, 5, 10)

/** Numpad lower bound for a manual filament length (mm). */
private const val MIN_DISTANCE = 0.1

/** Numpad bounds for a manual feedrate (mm/s) — ceiling mirrors PrinterCommands' mm/min cap (÷60). */
private const val MIN_SPEED_MM_S = 1
private val MAX_SPEED_MM_S = PrinterCommands.MAX_EXTRUDE_FEED_MM_MIN / 60

/** Material-Symbol shown on Extrude/Retract while the cold-extrude gate is closed (needs heat). */
private const val COLD_SYMBOL = "thermostat_arrow_down"

/** Which setpoint a [NumpadPage] is currently editing (null = no numpad shown). */
private enum class NumpadTarget { Distance, Speed, Temp }

/**
 * The Extrude panel (EXTR-01..04) — Move-style (NO mockup, D-08). Tuning settings (flow / pressure
 * advance / smooth time) deliberately live elsewhere (during-print surface), NOT here.
 *
 * ## Focus — two columns (2026-06-01 redesign)
 *  - LEFT: big **Extrude** / **Retract** accent commands. When extrudable they show Material-Symbol
 *    `output_circle` / `input_circle`; when the LIVE per-tool `can_extrude` gate ([ExtrudeVm.canExtrude])
 *    is closed they swap to [COLD_SYMBOL] (a thermostat-down cue) and render DISABLED (dim, no-op) —
 *    the icon IS the cold signal (EXTR-04 / D-07), no separate hint text. Extrude → `extrude(+dist,
 *    speed)`, Retract → `extrude(-dist, speed)` (SAVE/M83/G1 E±/RESTORE, mode-safe — [PrinterCommands.extrude]).
 *  - RIGHT: the live **Distance** and **Speed** setting readouts. Tapping either opens a full-screen
 *    [NumpadPage] for exact manual entry (clamped to the safe range).
 *
 * ## Field — three equal-height rows that fill the region
 *  - A 4-up distance preset row (1/5/25/50 mm); selecting fills the Distance readout. Any preset above
 *    the live [ExtrudeVm.maxExtrudeDistance] (`max_extrude_only_distance`) is DISABLED.
 *  - A 4-up feedrate preset row (1/2/5/10 mm/s); selecting fills the Speed readout.
 *  - A two-button row: a nozzle-temp button (live current temp; tap opens the temp [NumpadPage] →
 *    `SET_HEATER_TEMPERATURE`) and a Spoolman placeholder (future spool-manager integration).
 *  - A T0/T1… tool row is inserted ONLY when [ExtrudeVm.showToolSelector] (EXTR-03 / D-09).
 *
 * ## Gutter — Load / Unload / Back (EXTR-02 / D-10)
 * Load and Unload are ALWAYS shown. Present-macro tap dispatches `loadFilament()` / `unloadFilament()`;
 * ABSENT shows an informational [SeverityToast] (never a failed dispatch). Back ([Intent.Neutral]) returns.
 *
 * Every action dispatches a registry gcode entry via the per-session
 * [works.mees.dinghy.command.CommandDispatcher]. A control whose dispatch key is in-flight is disabled
 * (T-05-07-T). A dispatcher [DispatchEvent.Failure] surfaces an error [SeverityToast].
 *
 * @param container the service-locator (provides the session dispatcher).
 * @param holder    the toolkit-agnostic [ExtrudeHolder] (live gate + tools + temp + macro presence).
 * @param onBack    invoked by the neutral Back gutter tile (D-10).
 */
@Composable
fun ExtrudeScreen(
    container: AppContainer,
    holder: ExtrudeHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val vm by holder.vm.collectAsStateWithLifecycle()

    var distance by remember { mutableStateOf(DEFAULT_DISTANCE) }
    var speed by remember { mutableStateOf(DEFAULT_SPEED_MM_S) }
    var numpad by remember { mutableStateOf<NumpadTarget?>(null) }
    var failureText by remember { mutableStateOf<String?>(null) }
    var infoText by remember { mutableStateOf<String?>(null) }

    // If the live max-extrude ceiling makes the selected length illegal, fall back to the largest enabled.
    LaunchedEffect(vm.maxExtrudeDistance) {
        val max = vm.maxExtrudeDistance
        if (max != null && distance > max) {
            distance = DISTANCE_PRESETS.filter { it <= max }.maxOrNull() ?: DISTANCE_PRESETS.first()
        }
    }

    // Surface a dispatch failure (redacted message) as an error toast. Reset on session swap.
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
    // The missing-macro / placeholder informational popup auto-dismisses like the failure toast (D-10).
    LaunchedEffect(infoText) {
        if (infoText != null) {
            delay(4_000)
            infoText = null
        }
    }

    // One dispatch helper — every action funnels through the registry (no raw rpc).
    fun <P> dispatchCommand(command: CommandSpec<P>, args: P) {
        if (command.dispatchKey(args) in inFlight) return
        dispatcher?.dispatch(command, args)
    }

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            focus = {
                FocusGrid(
                    canExtrude = vm.canExtrude,
                    inFlight = inFlight,
                    distance = distance,
                    speed = speed,
                    onExtrude = { dispatchCommand(CommandRegistry.extrude, ExtrudeArgs(distance, speed * 60)) },
                    onRetract = { dispatchCommand(CommandRegistry.extrude, ExtrudeArgs(-distance, speed * 60)) },
                    onEditDistance = { numpad = NumpadTarget.Distance },
                    onEditSpeed = { numpad = NumpadTarget.Speed },
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            },
            field = {
                val t = LocalTokens.current
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SelectorLabel("Distance (mm)")
                    DistanceSelector(
                        selected = distance,
                        maxDistance = vm.maxExtrudeDistance,
                        onSelect = { distance = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    SelectorLabel("Feedrate (mm/s)")
                    SpeedSelector(
                        selected = speed,
                        onSelect = { speed = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    if (vm.showToolSelector) {
                        SelectorLabel("Tool")
                        ToolSelector(
                            tools = vm.tools,
                            inFlight = inFlight,
                            onSelect = { i ->
                                dispatchCommand(CommandRegistry.selectTool, SelectToolArgs(i))
                                holder.setActiveTool(if (i == 0) "extruder" else "extruder$i")
                            },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                    // Third row: nozzle-temp setter + Spoolman placeholder. The temp button is RED below
                    // the min-extrude temp (can't extrude) and GREEN above it — the same live gate the
                    // big commands use (EXTR-04).
                    val tempColor = if (vm.canExtrude) t.go else t.stop
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FieldButton(
                            text = "${vm.nozzleTemp.roundToInt()}°",
                            onClick = { numpad = NumpadTarget.Temp },
                            borderColor = tempColor,
                            contentColor = tempColor,
                            textSizeSp = 34f,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.nozzle),
                                    contentDescription = null,
                                    tint = tempColor,
                                    modifier = Modifier.size(fsSp(48f, t.fs).dp),
                                )
                            },
                        )
                        FieldButton(
                            text = "Spool",
                            onClick = { infoText = "Spoolman integration coming soon" },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            icon = {
                                MaterialSymbol(
                                    name = "inventory_2",
                                    tint = t.text2,
                                    sizeSp = fsSp(34f, t.fs),
                                )
                            },
                        )
                    }
                    failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
                    infoText?.let { msg -> SeverityToast(Severity.Info, msg, Modifier.fillMaxWidth()) }
                }
            },
            gutter = {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedControl(
                        label = "Load",
                        onClick = {
                            if (vm.hasLoadMacro) dispatchCommand(CommandRegistry.loadFilament, Unit)
                            else infoText = "No LOAD_FILAMENT macro configured"
                        },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Warn, // amber — heats + drives filament.
                    )
                    OutlinedControl(
                        label = "Unload",
                        onClick = {
                            if (vm.hasUnloadMacro) dispatchCommand(CommandRegistry.unloadFilament, Unit)
                            else infoText = "No UNLOAD_FILAMENT macro configured"
                        },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent, // blue — physical command.
                    )
                    OutlinedControl(
                        label = "Back",
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Neutral, // D-10: plain nav spends no safety color (matches Move).
                    )
                }
            },
        )

        // Full-screen numeric keypad for exact Distance / Speed / Temp entry (opaque — paints over).
        when (numpad) {
            NumpadTarget.Distance -> {
                val ceiling = vm.maxExtrudeDistance?.toDouble()?.coerceAtMost(PrinterCommands.MAX_EXTRUDE_MM)
                    ?: PrinterCommands.MAX_EXTRUDE_MM
                NumpadPage(
                    label = "Distance",
                    initial = distance,
                    range = MIN_DISTANCE..ceiling,
                    unit = "mm",
                    allowDecimal = true,
                    onCancel = { numpad = null },
                    onSet = { distance = it; numpad = null },
                )
            }
            NumpadTarget.Speed -> NumpadPage(
                label = "Speed",
                initial = speed.toDouble(),
                range = MIN_SPEED_MM_S.toDouble()..MAX_SPEED_MM_S.toDouble(),
                unit = "mm/s",
                allowDecimal = false,
                onCancel = { numpad = null },
                onSet = { speed = it.roundToInt(); numpad = null },
            )
            NumpadTarget.Temp -> NumpadPage(
                label = "Nozzle",
                initial = vm.nozzleTarget,
                range = PrinterCommands.MIN_TEMP_C.toDouble()..PrinterCommands.MAX_TEMP_C.toDouble(),
                unit = "°C",
                allowDecimal = false,
                onCancel = { numpad = null },
                onSet = {
                    dispatchCommand(
                        CommandRegistry.setHeater,
                        SetHeaterArgs(vm.activeHeater, it.roundToInt(), key = "set_temp"),
                    )
                    numpad = null
                },
            )
            null -> Unit
        }
    }
}

/**
 * The two-column Focus: LEFT = Extrude/Retract big commands (cold → [COLD_SYMBOL] + disabled),
 * RIGHT = Distance/Speed setting readouts (tap to open the numpad).
 */
@Composable
private fun FocusGrid(
    canExtrude: Boolean,
    inFlight: Set<String>,
    distance: Double,
    speed: Int,
    onExtrude: () -> Unit,
    onRetract: () -> Unit,
    onEditDistance: () -> Unit,
    onEditSpeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // LEFT column — the physical commands.
        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BigCommand(
                label = "Extrude",
                symbol = if (!canExtrude) COLD_SYMBOL else "output_circle",
                disabled = !canExtrude || "extrude" in inFlight,
                onClick = onExtrude,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            BigCommand(
                label = "Retract",
                symbol = if (!canExtrude) COLD_SYMBOL else "input_circle",
                disabled = !canExtrude || "retract" in inFlight,
                onClick = onRetract,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        // RIGHT column — the live setting readouts (tap to enter an exact value).
        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingReadout(
                label = "Distance",
                value = fmtDist(distance),
                unit = "mm",
                onClick = onEditDistance,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            SettingReadout(
                label = "Speed",
                value = speed.toString(),
                unit = "mm/s",
                onClick = onEditSpeed,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

/** A large accent (physical-command) button: Material-Symbol glyph over the label; disabled cells dim. */
@Composable
private fun BigCommand(
    label: String,
    symbol: String,
    disabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val tint = if (disabled) t.text3 else t.accent2
    var box = modifier
        .alpha(if (disabled) 0.4f else 1f)
        .clip(shape)
        .border(BorderStroke(2.dp, if (disabled) t.hair else t.accentLine), shape)
    if (!disabled) box = box.clickable(onClick = onClick)
    Box(box, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MaterialSymbol(name = symbol, tint = tint, sizeSp = fsSp(40f, t.fs))
            Text(
                text = label,
                color = tint,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(22f, t.fs).sp,
            )
        }
    }
}

/**
 * A live setting readout (a "white = setting" affordance, D-13): neutral-outlined, the current value
 * as the hero in GeistMono with a small label above. Tapping opens the [NumpadPage] for exact entry.
 */
@Composable
private fun SettingReadout(
    label: String,
    value: String,
    unit: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .clip(shape)
            .border(BorderStroke(2.dp, t.outline), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                color = t.text2,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Medium,
                fontSize = fsSp(18f, t.fs).sp,
            )
            Text(
                text = value,
                color = t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(48f, t.fs).sp,
            )
            Text(
                text = unit,
                color = t.text3,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Medium,
                fontSize = fsSp(16f, t.fs).sp,
            )
        }
    }
}

/**
 * An outlined field button: a leading [icon] slot + a GeistMono label, centered. [borderColor] /
 * [contentColor] default to the neutral outline/text roles; callers override them to color the button
 * by state (e.g. the nozzle-temp button red/green by the cold-extrude gate).
 */
@Composable
private fun FieldButton(
    text: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = LocalTokens.current.outline,
    contentColor: Color = LocalTokens.current.text,
    textSizeSp: Float = 24f,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .clip(shape)
            .border(BorderStroke(2.dp, borderColor), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            icon()
            Text(
                text = text,
                color = contentColor,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(textSizeSp, t.fs).sp,
            )
        }
    }
}

/** A small section label above a selector row. */
@Composable
private fun SelectorLabel(text: String) {
    val t = LocalTokens.current
    Text(
        text = text,
        color = t.text2,
        fontFamily = GeistMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = fsSp(13f, t.fs).sp,
    )
}

/**
 * The fixed 4-up filament-length presets; the active preset is accent-outlined. Presets above the live
 * [maxDistance] (Klipper's `max_extrude_only_distance`) are DISABLED when it is known. Cells fill the
 * row height (the row is a weighted, equal-height field row).
 */
@Composable
private fun DistanceSelector(
    selected: Double,
    maxDistance: Float?,
    onSelect: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (d in DISTANCE_PRESETS) {
            val overCeiling = maxDistance != null && d > maxDistance
            val active = d == selected && !overCeiling
            val shape = RoundedCornerShape(t.rCtrl)
            var cell = Modifier
                .weight(1f)
                .fillMaxHeight()
                .alpha(if (overCeiling) 0.4f else 1f)
                .clip(shape)
                .border(BorderStroke(2.dp, if (active) t.accentLine else t.outline), shape)
            if (!overCeiling) cell = cell.clickable { onSelect(d) }
            Box(cell, contentAlignment = Alignment.Center) {
                Text(
                    text = fmtDist(d),
                    color = when {
                        overCeiling -> t.text3
                        active -> t.accent2
                        else -> t.text2
                    },
                    fontFamily = GeistMono,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = fsSp(24f, t.fs).sp,
                )
            }
        }
    }
}

/** The fixed 4-up feedrate presets (mm/s); the active preset is accent-outlined. Cells fill row height. */
@Composable
private fun SpeedSelector(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in SPEED_PRESETS) {
            val active = s == selected
            val shape = RoundedCornerShape(t.rCtrl)
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .clip(shape)
                    .border(BorderStroke(2.dp, if (active) t.accentLine else t.outline), shape)
                    .clickable { onSelect(s) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = s.toString(),
                    color = if (active) t.accent2 else t.text2,
                    fontFamily = GeistMono,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = fsSp(24f, t.fs).sp,
                )
            }
        }
    }
}

/** The multi-extruder T0/T1… tool selector — shown only when the printer has >1 extruder (D-09). */
@Composable
private fun ToolSelector(
    tools: List<String>,
    inFlight: Set<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tools.forEachIndexed { i, label ->
            val shape = RoundedCornerShape(t.rCtrl)
            val busy = "tool_$i" in inFlight
            var cell = Modifier.weight(1f).fillMaxHeight()
                .alpha(if (busy) 0.4f else 1f)
                .clip(shape)
                .border(BorderStroke(2.dp, t.accentLine), shape)
            if (!busy) cell = cell.clickable { onSelect(i) }
            Box(cell, contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    color = t.accent2,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(15f, t.fs).sp,
                )
            }
        }
    }
}

/** Distance label: drop the trailing ".0" on whole-mm values. */
private fun fmtDist(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
