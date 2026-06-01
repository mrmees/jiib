package works.mees.dinghy.ui.extrude

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.net.JsonRpcMethods
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/** Extrude/retract feedrate (mm/min) — default 300 mm/min ≈ 5 mm/s (RESEARCH §6; clamped in PrinterCommands). */
private const val EXTRUDE_FEED = 300

/** The fixed 6-up extrude-distance selector (mm), RESEARCH §6. The 100 mm step is the default ceiling. */
private val DISTANCES = listOf(1.0, 5.0, 10.0, 25.0, 50.0, 100.0)

/**
 * The Extrude panel (EXTR-01..04) — Move-style (NO mockup, D-08): the Move template within
 * Focus/Field/Gutter. Built on [ScreenScaffold]; all color routes through [LocalTokens] role tokens
 * (THEME-01); all live numbers use GeistMono tabular numerals.
 *
 * ## Focus — Extrude / Retract (the primary physical commands, EXTR-01)
 * Two big accent buttons. Extrude → `extrude(+dist, speed)`, Retract → `extrude(-dist, speed)`
 * (SAVE/M83/G1 E±/RESTORE, mode-safe — [PrinterCommands.extrude]). When the LIVE per-tool
 * `can_extrude` gate ([ExtrudeVm.canExtrude]) is false they render DISABLED (reduced opacity, no-op
 * tap) with an inline reason "Heat nozzle to <min>°C to extrude" — the real
 * [ExtrudeVm.minExtrudeTemp] when known, else the generic hint (EXTR-04 / D-07). No failed taps.
 *
 * ## Field — distance + speed selectors (+ tool selector on multi-extruder)
 *  - A 6-up distance selector (1/5/10/25/50/100 mm). When [ExtrudeVm.maxExtrudeDistance] is known, any
 *    step above it is DISABLED (an over-extrude is firmware-rejected); when null, all six up to 100 mm
 *    stand. If the selected step becomes disabled it falls back to the largest still-enabled value.
 *  - A speed selector (2/5/10 mm/s → mm/min).
 *  - A T0/T1… tool row ONLY when [ExtrudeVm.showToolSelector] (EXTR-03 / D-09); selecting dispatches
 *    `selectTool(i)` and re-points the holder's per-tool gate.
 *
 * ## Gutter — Load / Unload / Back (EXTR-02 / D-10)
 * Load and Unload are ALWAYS shown. Tapping when the macro is present dispatches `loadFilament()` /
 * `unloadFilament()`; when ABSENT it shows an informational [SeverityToast] (Severity.Info) — never a
 * failed dispatch. Back ([Intent.Danger]) returns.
 *
 * Every action dispatches via the per-session [works.mees.dinghy.command.CommandDispatcher] as a
 * `printer.gcode.script` ([JsonRpcMethods.GCODE_SCRIPT]) carrying `scriptParams(PrinterCommands.*)`. A
 * control whose dispatch key is in-flight is disabled (T-05-07-T). A dispatcher [DispatchEvent.Failure]
 * surfaces an error [SeverityToast].
 *
 * @param container the service-locator (provides the session dispatcher).
 * @param holder    the toolkit-agnostic [ExtrudeHolder] (live gate + tools + macro presence + hints).
 * @param onBack    invoked by the red Back gutter tile.
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
    val t = LocalTokens.current

    var distance by remember { mutableStateOf(10.0) } // default highlighted step.
    var speed by remember { mutableStateOf(5) }       // default 5 mm/s (→ 300 mm/min).
    var failureText by remember { mutableStateOf<String?>(null) }
    var infoText by remember { mutableStateOf<String?>(null) }

    // If the live max-extrude ceiling makes the selected step illegal, fall back to the largest enabled.
    LaunchedEffect(vm.maxExtrudeDistance) {
        val max = vm.maxExtrudeDistance
        if (max != null && distance > max) {
            distance = DISTANCES.filter { it <= max }.maxOrNull() ?: DISTANCES.first()
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
    // The missing-macro informational popup auto-dismisses like the failure toast (D-10).
    LaunchedEffect(infoText) {
        if (infoText != null) {
            delay(4_000)
            infoText = null
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
                ExtrudeActions(
                    canExtrude = vm.canExtrude,
                    minExtrudeTemp = vm.minExtrudeTemp,
                    inFlight = inFlight,
                    onExtrude = { script("extrude", PrinterCommands.extrude(distance, speed.toMmMin())) },
                    onRetract = { script("retract", PrinterCommands.extrude(-distance, speed.toMmMin())) },
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            },
            field = {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SelectorLabel("Distance (mm)")
                    DistanceSelector(
                        selected = distance,
                        maxDistance = vm.maxExtrudeDistance,
                        onSelect = { distance = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SelectorLabel("Speed (mm/s)")
                    SpeedSelector(
                        selected = speed,
                        onSelect = { speed = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (vm.showToolSelector) {
                        SelectorLabel("Tool")
                        ToolSelector(
                            tools = vm.tools,
                            inFlight = inFlight,
                            onSelect = { i ->
                                script("tool_$i", PrinterCommands.selectTool(i))
                                // Re-point the holder's per-tool can_extrude gate (extruder, extruder1, …).
                                holder.setActiveTool(if (i == 0) "extruder" else "extruder$i")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    failureText?.let { msg ->
                        SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth())
                    }
                    infoText?.let { msg ->
                        SeverityToast(Severity.Info, msg, Modifier.fillMaxWidth())
                    }
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
                            if (vm.hasLoadMacro) {
                                script("load", PrinterCommands.loadFilament())
                            } else {
                                infoText = "No LOAD_FILAMENT macro configured"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Neutral,
                    )
                    OutlinedControl(
                        label = "Unload",
                        onClick = {
                            if (vm.hasUnloadMacro) {
                                script("unload", PrinterCommands.unloadFilament())
                            } else {
                                infoText = "No UNLOAD_FILAMENT macro configured"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Neutral,
                    )
                    OutlinedControl(
                        label = "Back",
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Danger,
                    )
                }
            },
        )
    }
}

/** Convert a mm/s speed step into the mm/min feedrate the gcode carries. */
private fun Int.toMmMin(): Int = this * 60

/**
 * The Focus pair — Extrude / Retract big accent commands. When the live cold-extrude gate is closed
 * ([canExtrude] false) both render dim + no-op, with the inline reason hint below (EXTR-04 / D-07).
 */
@Composable
private fun ExtrudeActions(
    canExtrude: Boolean,
    minExtrudeTemp: Float?,
    inFlight: Set<String>,
    onExtrude: () -> Unit,
    onRetract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BigCommand(
            label = "Extrude",
            disabled = !canExtrude || "extrude" in inFlight,
            onClick = onExtrude,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        BigCommand(
            label = "Retract",
            disabled = !canExtrude || "retract" in inFlight,
            onClick = onRetract,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        if (!canExtrude) {
            // The real min-extrude-temp when known, else the generic hint (single-extruder-accurate).
            val tempPart = minExtrudeTemp?.let { "${it.toInt()}°C " }.orEmpty()
            Text(
                text = "Heat nozzle to ${tempPart}extrude",
                color = t.heat,
                fontFamily = GeistMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(15f, t.fs).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
    }
}

/** A large accent (physical-command) button; disabled cells dim and ignore taps. */
@Composable
private fun BigCommand(label: String, disabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    var box = modifier
        .alpha(if (disabled) 0.4f else 1f)
        .clip(shape)
        .border(BorderStroke(2.dp, if (disabled) t.hair else t.accentLine), shape)
    if (!disabled) box = box.clickable(onClick = onClick)
    Box(box, contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = if (disabled) t.text3 else t.accent2,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Bold,
            fontSize = fsSp(26f, t.fs).sp,
        )
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
 * The fixed 6-up distance selector; the active step is accent-outlined. Steps above the live
 * [maxDistance] (Klipper's `max_extrude_only_distance`) are DISABLED when it is known; when null all
 * six stand up to the 100 mm default ceiling.
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
        for (d in DISTANCES) {
            val overCeiling = maxDistance != null && d > maxDistance
            val active = d == selected && !overCeiling
            val shape = RoundedCornerShape(t.rCtrl)
            var cell = Modifier
                .weight(1f)
                .aspectRatio(1f)
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
                    fontSize = fsSp(15f, t.fs).sp,
                )
            }
        }
    }
}

/** The speed selector (mm/s steps); the active step is accent-outlined. */
@Composable
private fun SpeedSelector(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val speeds = listOf(2, 5, 10)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in speeds) {
            val active = s == selected
            val shape = RoundedCornerShape(t.rCtrl)
            Box(
                Modifier.weight(1f).aspectRatio(2f)
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
                    fontSize = fsSp(15f, t.fs).sp,
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
            var cell = Modifier.weight(1f).aspectRatio(2f)
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

/** Distance label: drop the trailing ".0" on whole-mm steps. */
private fun fmtDist(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
