package works.mees.dinghy.ui.extrude

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.ExtrudeArgs
import works.mees.dinghy.command.MacroInvocation
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.command.SelectToolArgs
import works.mees.dinghy.command.SetFilamentSensorArgs
import works.mees.dinghy.command.SetHeaterArgs
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.Scrubber
import works.mees.dinghy.designsystem.components.StepperRow
import works.mees.dinghy.designsystem.components.ToggleRow
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.SpoolGlyph
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.net.JsonRpcMethods
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.spool.SpoolmanSpool
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.ui.spool.parseNormalizedHex

/** Default highlighted length (mm) — must be a member of [DISTANCE_PRESETS]. */
private const val DEFAULT_DISTANCE = 5.0

/** Default feedrate (mm/s). */
private const val DEFAULT_SPEED_MM_S = 5

/** The fixed 4-up filament-length presets (mm). The distance ±stepper indexes through these. */
private val DISTANCE_PRESETS = listOf(1.0, 5.0, 25.0, 50.0)

/** Feedrate floor (mm/s) for the speed scrubber. */
private const val MIN_SPEED_MM_S = 1

/** Material-Symbol shown on Extrude/Retract while the cold-extrude gate is closed (needs heat). */
private const val COLD_SYMBOL = "thermostat_arrow_down"

/**
 * The in-screen Field mode: the main action list (default) or the macro-settings takeover where the
 * user curates this screen's pinned filament macros ([ExtrudeMacroPrefs]).
 */
sealed class ExtrudeFieldMode {
    /** Main Extrude action list — runout toggles + macros + thermal chips + spool link. */
    data object Main : ExtrudeFieldMode()

    /** Macro-settings takeover — toggle which discovered macros are pinned to the Field. */
    data object MacroSettings : ExtrudeFieldMode()
}

/**
 * The Extrude panel — rebuilt on the Focus/Field grammar (2026-06-16) as the filament-handling hub.
 *
 * ## Focus (fixed feed surface)
 *  - LEFT column: big **Extrude** / **Retract** accent commands. When the LIVE per-tool `can_extrude`
 *    gate ([ExtrudeVm.canExtrude]) is closed they swap to [COLD_SYMBOL] and render DISABLED — the icon
 *    IS the cold signal (EXTR-04 / D-07). Extrude → `extrude(+dist, speed)`, Retract → `extrude(-dist)`.
 *  - RIGHT column (no keyboard — UI law): an optional [ToolSelector] (multi-extruder only), a distance
 *    ±stepper through 1/5/25/50 mm (clamped, ceiling-skipped), a speed scrubber capped to the printer's
 *    reported `max_extrude_only_velocity`, and a live nozzle current/target readout.
 *
 * ## Field (scrolling action list)
 *  - Live runout-sensor toggles (hidden when none discovered).
 *  - Load / Unload macro rows (presence-gated) + the user's screen-scoped pinned macros (run bare).
 *  - Inline nozzle-only thermal preset chips (loaded-spool + PLA/PETG/ABS/TPU).
 *  - A spool link row → the Spoolman library.
 *
 * ## FootButtonBar — Back / Cooldown / Macros
 * Back (accent/nav), Cooldown (warn — heat-off, no confirm), Macros (opens the macro-settings takeover).
 * E-stop is NOT in the foot bar — [FocusFrame] docks it in the header when a print is active.
 */
@Composable
fun ExtrudeScreen(
    container: AppContainer,
    holder: ExtrudeHolder,
    activeSpoolDetail: SpoolmanSpool?,
    onBack: () -> Unit,
    onOpenSpool: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val vm by holder.vm.collectAsStateWithLifecycle()
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    var failureText by remember { mutableStateOf<String?>(null) }

    // WR-02 (26-rev): collect dispatcher failure events so a failed dispatch surfaces the error toast.
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

    ExtrudeContent(
        vm = vm,
        activeSpoolDetail = activeSpoolDetail,
        inFlight = inFlight,
        failureText = failureText,
        isPrinting = isPrinting,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onExtrude = { dist, speed ->
            val key = CommandRegistry.extrude.dispatchKey(ExtrudeArgs(dist, speed * 60))
            if (key !in inFlight) dispatcher?.dispatch(CommandRegistry.extrude, ExtrudeArgs(dist, speed * 60))
        },
        onRetract = { dist, speed ->
            val key = CommandRegistry.extrude.dispatchKey(ExtrudeArgs(-dist, speed * 60))
            if (key !in inFlight) dispatcher?.dispatch(CommandRegistry.extrude, ExtrudeArgs(-dist, speed * 60))
        },
        onSelectTool = { i ->
            if (CommandRegistry.selectTool.dispatchKey(SelectToolArgs(i)) !in inFlight) {
                dispatcher?.dispatch(CommandRegistry.selectTool, SelectToolArgs(i))
                holder.setActiveTool(if (i == 0) "extruder" else "extruder$i")
            }
        },
        onLoad = {
            if (vm.hasLoadMacro) dispatcher?.dispatch(CommandRegistry.loadFilament, Unit)
            else null
        },
        onUnload = {
            if (vm.hasUnloadMacro) dispatcher?.dispatch(CommandRegistry.unloadFilament, Unit)
            else null
        },
        onSetExtruderTemp = { temp ->
            val clamped = PrinterCommands.clampHeaterTarget(temp)
            dispatcher?.dispatch(CommandRegistry.setHeater, SetHeaterArgs(vm.activeHeater, clamped, key = "set_temp"))
        },
        onToggleSensor = { _, sensorName, enable ->
            val key = "set_filament_sensor_$sensorName"
            if (key !in inFlight) {
                dispatcher?.dispatch(CommandRegistry.setFilamentSensor, SetFilamentSensorArgs(sensorName, enable))
            }
        },
        onRunMacro = { name ->
            val key = "macro_$name"
            if (key !in inFlight) {
                dispatcher?.dispatch(
                    key = key,
                    method = JsonRpcMethods.GCODE_SCRIPT,
                    params = PrinterCommands.scriptParams(MacroInvocation.buildRaw(name, "")),
                )
            }
        },
        onToggleMacroPin = { name -> container.toggleExtrudeMacroPin(name) },
        onCooldown = { dispatcher?.dispatch(CommandRegistry.cooldown, Unit) },
        onBack = onBack,
        onOpenSpool = onOpenSpool,
        modifier = modifier,
    )
}

/**
 * Stateless preview seam for [ExtrudeScreen]. All callbacks default to no-ops; all data is injected.
 * Used by `ExtrudePreviews.kt`.
 */
@Composable
fun ExtrudeScreen(
    vm: ExtrudeVm = ExtrudeVm(),
    activeSpoolDetail: SpoolmanSpool? = null,
    onBack: () -> Unit = {},
    onOpenSpool: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ExtrudeContent(
        vm = vm,
        activeSpoolDetail = activeSpoolDetail,
        inFlight = emptySet(),
        failureText = null,
        onExtrude = { _, _ -> },
        onRetract = { _, _ -> },
        onSelectTool = {},
        onLoad = {},
        onUnload = {},
        onSetExtruderTemp = {},
        onToggleSensor = { _, _, _ -> },
        onRunMacro = {},
        onToggleMacroPin = {},
        onCooldown = {},
        onBack = onBack,
        onOpenSpool = onOpenSpool,
        modifier = modifier,
    )
}

@Composable
private fun ExtrudeContent(
    vm: ExtrudeVm,
    activeSpoolDetail: SpoolmanSpool?,
    inFlight: Set<String>,
    failureText: String?,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
    onExtrude: (distance: Double, speed: Int) -> Unit,
    onRetract: (distance: Double, speed: Int) -> Unit,
    onSelectTool: (Int) -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onSetExtruderTemp: (Int) -> Unit,
    onToggleSensor: (objectKey: String, sensorName: String, enable: Boolean) -> Unit,
    onRunMacro: (name: String) -> Unit,
    onToggleMacroPin: (name: String) -> Unit,
    onCooldown: () -> Unit,
    onBack: () -> Unit,
    onOpenSpool: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var distance by remember { mutableStateOf(DEFAULT_DISTANCE) }
    var speed by remember { mutableStateOf(DEFAULT_SPEED_MM_S) }
    var fieldMode by remember { mutableStateOf<ExtrudeFieldMode>(ExtrudeFieldMode.Main) }

    // If the live max-extrude ceiling makes the selected length illegal, fall back to the largest enabled.
    LaunchedEffect(vm.maxExtrudeDistance) {
        val max = vm.maxExtrudeDistance
        if (max != null && distance > max) {
            distance = DISTANCE_PRESETS.filter { it <= max }.maxOrNull() ?: DISTANCE_PRESETS.first()
        }
    }
    // If the printer's reported max velocity is below the current speed, pull speed down so Extrude
    // never dispatches over cap (the scrubber range also caps, but the seeded default could exceed a
    // low reported max before the user touches it).
    LaunchedEffect(vm.maxExtrudeVelocity) {
        if (speed > vm.maxExtrudeVelocity) speed = vm.maxExtrudeVelocity
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = stringResource(R.string.cd_launcher_extrude),
                    icon = DinghyIcons.LauncherExtrude,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxSize(),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    FocusGrid(
                        vm = vm,
                        inFlight = inFlight,
                        distance = distance,
                        speed = speed,
                        uDp = grid.uDp,
                        onSelectDistance = { distance = it },
                        onSpeedSettle = { speed = it.coerceIn(MIN_SPEED_MM_S, vm.maxExtrudeVelocity) },
                        onSelectTool = onSelectTool,
                        onExtrude = { onExtrude(distance, speed) },
                        onRetract = { onRetract(distance, speed) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
            field = {
                when (fieldMode) {
                    is ExtrudeFieldMode.Main -> {
                        Column(
                            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // 1. Runout sensors (the whole section is hidden when none discovered).
                            vm.sensors.forEach { s ->
                                ToggleRow(
                                    label = s.prettyName,
                                    subLabel = s.filamentDetected?.let {
                                        stringResource(
                                            if (it) R.string.extrude_filament_present
                                            else R.string.extrude_filament_absent,
                                        )
                                    },
                                    checked = s.enabled,
                                    onToggle = { enable -> onToggleSensor(s.objectKey, s.sensorName, enable) },
                                    uDp = grid.uDp,
                                    enabled = "set_filament_sensor_${s.sensorName}" !in inFlight,
                                )
                            }
                            // 2. Macros — Load/Unload presence-gated, then screen-scoped pins (run bare).
                            if (vm.hasLoadMacro) {
                                MacroActionRow(
                                    label = stringResource(R.string.extrude_load),
                                    icon = DinghyIcons.ExpandCircleUp,
                                    onClick = onLoad,
                                    uDp = grid.uDp,
                                )
                            }
                            if (vm.hasUnloadMacro) {
                                MacroActionRow(
                                    label = stringResource(R.string.extrude_unload),
                                    icon = DinghyIcons.ExpandCircleDown,
                                    onClick = onUnload,
                                    uDp = grid.uDp,
                                )
                            }
                            vm.pinnedMacros.forEach { m ->
                                MacroActionRow(
                                    label = m.name,
                                    icon = DinghyIcons.LauncherMacros,
                                    onClick = { onRunMacro(m.name) },
                                    uDp = grid.uDp,
                                    busy = "macro_${m.name}" in inFlight,
                                )
                            }
                            // 3. Inline nozzle-only thermal preset chips.
                            ThermalChips(
                                activeSpoolDetail = activeSpoolDetail,
                                onSetExtruderTemp = onSetExtruderTemp,
                                uDp = grid.uDp,
                            )
                            // 4. Spool link.
                            SpoolLinkRow(
                                activeSpoolDetail = activeSpoolDetail,
                                onOpenSpool = onOpenSpool,
                                uDp = grid.uDp,
                            )
                            failureText?.let { msg ->
                                SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth())
                            }
                        }
                        FootButtonBar(uDp = grid.uDp) {
                            OutlinedControl(
                                label = "",
                                onClick = onBack,
                                modifier = Modifier.weight(1f),
                                intent = Intent.Accent, // R5: Back/nav = accent
                                icon = DinghyIcons.Back,
                            )
                            OutlinedControl(
                                label = stringResource(R.string.extrude_cooldown),
                                onClick = onCooldown,
                                modifier = Modifier.weight(1f),
                                intent = Intent.Warn, // hazardous-but-deliberate (heat off)
                                icon = DinghyIcons.HideTemps,
                            )
                            OutlinedControl(
                                label = stringResource(R.string.extrude_macro_settings),
                                onClick = { fieldMode = ExtrudeFieldMode.MacroSettings },
                                modifier = Modifier.weight(1f),
                                intent = Intent.Accent,
                                icon = DinghyIcons.ManageMacros,
                            )
                        }
                    }

                    is ExtrudeFieldMode.MacroSettings -> {
                        val t = LocalTokens.current
                        Column(
                            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (vm.allMacros.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.extrude_no_macros),
                                    color = t.text2,
                                    style = DinghyType.caption.toTextStyle(t),
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            } else {
                                vm.allMacros.forEach { m ->
                                    ToggleRow(
                                        label = m.name,
                                        checked = m.name in vm.pinnedNames,
                                        onToggle = { onToggleMacroPin(m.name) },
                                        uDp = grid.uDp,
                                    )
                                }
                            }
                        }
                        FootButtonBar(uDp = grid.uDp) {
                            OutlinedControl(
                                label = "",
                                onClick = { fieldMode = ExtrudeFieldMode.Main },
                                modifier = Modifier.weight(1f),
                                intent = Intent.Accent,
                                icon = DinghyIcons.Back,
                            )
                        }
                    }
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Focus sub-composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The Focus feed surface: LEFT = Extrude/Retract big commands (cold → [COLD_SYMBOL] + disabled),
 * RIGHT = (optional tool selector) + distance ±stepper + capped speed scrubber + nozzle readout.
 */
@Composable
private fun FocusGrid(
    vm: ExtrudeVm,
    inFlight: Set<String>,
    distance: Double,
    speed: Int,
    uDp: Dp,
    onSelectDistance: (Double) -> Unit,
    onSpeedSettle: (Int) -> Unit,
    onSelectTool: (Int) -> Unit,
    onExtrude: () -> Unit,
    onRetract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // LEFT column — the physical commands.
        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BigCommand(
                label = stringResource(R.string.extrude_cmd_extrude),
                symbol = if (!vm.canExtrude) COLD_SYMBOL else "output_circle",
                disabled = !vm.canExtrude || "extrude" in inFlight,
                onClick = onExtrude,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            BigCommand(
                label = stringResource(R.string.extrude_cmd_retract),
                symbol = if (!vm.canExtrude) COLD_SYMBOL else "input_circle",
                disabled = !vm.canExtrude || "retract" in inFlight,
                onClick = onRetract,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        // RIGHT column — distance stepper + speed scrubber + nozzle readout (no keyboard — UI law).
        Column(
            Modifier.weight(1f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (vm.showToolSelector) {
                ToolSelector(
                    tools = vm.tools,
                    inFlight = inFlight,
                    onSelect = onSelectTool,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
            DistanceStepper(
                selected = distance,
                maxDistance = vm.maxExtrudeDistance,
                onSelect = onSelectDistance,
                uDp = uDp,
            )
            SpeedScrubberRow(
                speed = speed,
                maxVelocity = vm.maxExtrudeVelocity,
                onSettle = onSpeedSettle,
                uDp = uDp,
            )
            NozzleReadout(temp = vm.nozzleTemp, target = vm.nozzleTarget)
        }
    }
}

/**
 * The distance ±stepper: indexes through [DISTANCE_PRESETS], clamps at the ends, and skips presets
 * over the printer's live `max_extrude_only_distance` ceiling.
 */
@Composable
private fun DistanceStepper(
    selected: Double,
    maxDistance: Float?,
    onSelect: (Double) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val enabledPresets = DISTANCE_PRESETS.filter { maxDistance == null || it <= maxDistance }
        .ifEmpty { listOf(DISTANCE_PRESETS.first()) }
    val idx = enabledPresets.indexOf(selected).let {
        if (it < 0) enabledPresets.indexOfLast { p -> p <= selected }.coerceAtLeast(0) else it
    }
    StepperRow(
        onDecrement = { onSelect(enabledPresets[(idx - 1).coerceAtLeast(0)]) },
        onIncrement = { onSelect(enabledPresets[(idx + 1).coerceAtMost(enabledPresets.lastIndex)]) },
        uDp = uDp,
        modifier = modifier,
        center = {
            Text(
                text = "${fmtDist(selected)} mm",
                color = t.text,
                style = DinghyType.statValue.toTextStyle(t),
            )
        },
        decreaseContentDescription = stringResource(R.string.extrude_distance_dec),
        increaseContentDescription = stringResource(R.string.extrude_distance_inc),
    )
}

/** The speed scrubber, range capped to the printer's reported `max_extrude_only_velocity`. */
@Composable
private fun SpeedScrubberRow(
    speed: Int,
    maxVelocity: Int,
    onSettle: (Int) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    Scrubber(
        name = stringResource(R.string.extrude_speed_readout),
        value = speed.toFloat(),
        range = MIN_SPEED_MM_S.toFloat()..maxVelocity.toFloat(),
        step = 1f,
        uDp = uDp,
        onSettle = { onSettle(it.roundToInt().coerceIn(MIN_SPEED_MM_S, maxVelocity)) },
        unit = "mm/s",
        modifier = modifier,
    )
}

/** A small live nozzle current/target readout (display only — arbitrary entry lives on Temperature). */
@Composable
private fun NozzleReadout(temp: Double, target: Double, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.nozzle),
            contentDescription = null,
            tint = t.text2,
            modifier = Modifier.size(fsSp(28f, t.fs).dp),
        )
        Text(
            text = "${temp.roundToInt()} / ${target.roundToInt()}°C",
            color = t.text,
            style = DinghyType.dataInline.toTextStyle(t),
        )
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
            // UAT-1: BigCommand is a Focus hero tile — prominent icon tier (~70-80% of U).
            MaterialSymbol(name = symbol, tint = tint, sizeSp = fsSp(56f, t.fs))
            Text(
                text = label,
                color = tint,
                style = DinghyType.buttonLabel.toTextStyle(t),
            )
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
                    style = DinghyType.dataInline.toTextStyle(t),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Field sub-composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * An outlined clickable macro action row: leading [icon] + label, runs bare on tap. Dims when [busy]
 * (an in-flight dispatch of the same macro). Mirrors the outline+click style of [PresetRow].
 */
@Composable
private fun MacroActionRow(
    label: String,
    icon: DinghyIcon,
    onClick: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = uDp)
            .alpha(if (busy) 0.4f else 1f)
            .clip(shape)
            .border(BorderStroke(2.dp, t.outline), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DinghyIconView(icon = icon, tint = t.text2, sizeDp = fsSp(24f, t.fs).dp)
        Text(
            text = label,
            color = t.text,
            style = DinghyType.listLabel.toTextStyle(t),
        )
    }
}

/**
 * Inline nozzle-only thermal preset chips: the loaded-spool temp (when a spool with a temp is active)
 * + PLA/PETG/ABS/TPU. One tap applies the nozzle setpoint ONLY (never the bed — D-17).
 */
@Composable
private fun ThermalChips(
    activeSpoolDetail: SpoolmanSpool?,
    onSetExtruderTemp: (Int) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val loadedTemp = activeSpoolDetail?.filament?.settingsExtruderTemp
    if (loadedTemp != null) {
        val spoolName = activeSpoolDetail.filament?.name
            ?: activeSpoolDetail.filament?.material
            ?: stringResource(R.string.extrude_loaded_filament)
        PresetRow(
            name = stringResource(R.string.extrude_loaded_prefix, spoolName),
            temp = loadedTemp,
            onClick = { onSetExtruderTemp(loadedTemp) },
            modifier = modifier,
            uDp = uDp,
        )
    }
    PrinterCommands.MATERIAL_PRESETS.forEach { preset ->
        PresetRow(
            name = preset.name,
            temp = preset.nozzle,
            onClick = { onSetExtruderTemp(preset.nozzle) },
            modifier = modifier,
            uDp = uDp,
        )
    }
}

/** The spool link row — reactive [SpoolGlyph] + label; navigates to the Spoolman library. */
@Composable
private fun SpoolLinkRow(
    activeSpoolDetail: SpoolmanSpool?,
    onOpenSpool: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val spoolSwatches = remember(activeSpoolDetail) {
        activeSpoolDetail?.filament?.colorSwatches.orEmpty().mapNotNull(::parseNormalizedHex)
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = uDp)
            .clip(shape)
            .border(BorderStroke(2.dp, t.outline), shape)
            .clickable(onClick = onOpenSpool)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpoolGlyph(
            swatches = spoolSwatches,
            bodyTint = t.text2,
            keyline = t.hair,
            sizeDp = fsSp(28f, t.fs).dp,
            contentDescription = null,
        )
        Text(
            text = stringResource(R.string.cd_launcher_spool),
            color = t.text,
            style = DinghyType.listLabel.toTextStyle(t),
        )
    }
}

/**
 * A single thermal-preset row: material name on the left, nozzle temp on the right. Selecting
 * dispatches the extruder temperature ONLY (never the bed — D-17).
 */
@Composable
private fun PresetRow(
    name: String,
    temp: Int,
    onClick: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = uDp)
            .clip(shape)
            .border(BorderStroke(2.dp, t.outline), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = name,
            color = t.text,
            style = DinghyType.listLabel.toTextStyle(t),
        )
        Text(
            text = "${temp}°C",
            color = t.text2,
            style = DinghyType.dataInline.toTextStyle(t),
        )
    }
}

/** Distance label: drop the trailing ".0" on whole-mm values. */
private fun fmtDist(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
