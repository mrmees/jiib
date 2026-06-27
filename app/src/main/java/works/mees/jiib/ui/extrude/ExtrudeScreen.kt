package works.mees.jiib.ui.extrude

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import works.mees.jiib.R
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.DispatchEvent
import works.mees.jiib.command.ExtrudeArgs
import works.mees.jiib.command.GatingMode
import works.mees.jiib.command.GatingState
import works.mees.jiib.command.MacroInvocation
import works.mees.jiib.command.PrinterCommands
import works.mees.jiib.command.SelectToolArgs
import works.mees.jiib.command.SetFilamentSensorArgs
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.Severity
import works.mees.jiib.state.Capabilities
import works.mees.jiib.ui.heaters.HeatDispatch
import works.mees.jiib.ui.heaters.HeatersRow
import works.mees.jiib.ui.heaters.HeatersList
import works.mees.jiib.ui.heaters.HeatScope
import works.mees.jiib.ui.heaters.buildHeatersRows
import works.mees.jiib.ui.heaters.dispatchHeat
import works.mees.jiib.ui.heaters.loadedSpoolTemps
import works.mees.jiib.designsystem.SeverityToast
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.ListRowIcon
import works.mees.jiib.designsystem.components.ListRowLabel
import works.mees.jiib.designsystem.components.Scrubber
import works.mees.jiib.designsystem.components.ToggleRow
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.control.OutlinedControl
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.FocusInset
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.LocalUnitDp
import works.mees.jiib.designsystem.layout.controlHeight
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.di.AppContainer
import works.mees.jiib.net.JsonRpcMethods
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.spool.SpoolmanSpool
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.ui.spool.SpoolStatusRow

/** Default highlighted length (mm). */
private const val DEFAULT_DISTANCE = 5.0

/** Default feedrate (mm/s). */
private const val DEFAULT_SPEED_MM_S = 5

/** Length-slider floor / ceiling (mm). The ceiling is the owner-set max, capped further at runtime by
 *  the printer's reported `max_extrude_only_distance`. */
private const val MIN_DISTANCE_MM = 1f
private const val MAX_DISTANCE_MM = 100f

/** Feedrate floor (mm/s) for the speed slider. */
private const val MIN_SPEED_MM_S = 1

/** Material-Symbol shown on Extrude/Retract while the cold-extrude gate is closed (needs heat). */
private const val COLD_SYMBOL = "thermostat_arrow_down"

/**
 * The in-screen Field mode: the main action list (default) or the macro-settings takeover where the
 * user curates this screen's pinned filament macros ([ExtrudeMacroPrefs]).
 */
sealed class ExtrudeFieldMode {
    /** Main Extrude action list — runout toggles + macros + spool link. */
    data object Main : ExtrudeFieldMode()

    /** Macro-settings takeover — toggle which discovered macros are pinned to the Field. */
    data object MacroSettings : ExtrudeFieldMode()

    /** Heaters takeover — extruder-only preset list (OFF row + spool + presets). */
    data object Heaters : ExtrudeFieldMode()
}

/**
 * The Extrude panel — rebuilt on the Focus/Field grammar (2026-06-16) as the filament-handling hub.
 *
 * ## Focus (fixed feed surface) — a single vertical stack, top→bottom (no keyboard, slider-only — UI law):
 *  - An optional [ToolSelector] (multi-extruder only).
 *  - An info line (values only, units disambiguate): current temperature · Length · Speed.
 *  - Two bare slider rows (title start-aligned, no ± stepper): Length (1–100 mm, printer-capped) and
 *    Speed (capped to the reported `max_extrude_only_velocity`).
 *  - A bottom **Extrude** / **Retract** big-command row (the hero, fills remaining height). When the
 *    LIVE per-tool `can_extrude` gate ([ExtrudeVm.canExtrude]) is closed they swap to [COLD_SYMBOL] and
 *    render DISABLED — the icon IS the cold signal (EXTR-04 / D-07). Extrude → `extrude(+dist, speed)`,
 *    Retract → `extrude(-dist, speed)`.
 *
 * ## Field (scrolling action list)
 *  - Live runout-sensor toggles (hidden when none discovered).
 *  - Load / Unload macro rows (presence-gated) + the user's screen-scoped pinned macros (run bare).
 *  - A spool link row → the Spoolman library.
 *
 * ## FootButtonBar — Back / Heaters / Macros
 * Back (accent/nav), Heaters (accent — opens the extruder-only heaters takeover), Macros (opens the macro-settings takeover).
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
    val gating by container.gatingState.collectAsStateWithLifecycle(initialValue = GatingState.Idle)
    val vm by holder.vm.collectAsStateWithLifecycle()
    val heatPresets by container.activeHeatPresets.collectAsStateWithLifecycle(emptyList())
    val caps by container.capabilities.collectAsStateWithLifecycle(initialValue = Capabilities())
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

    val loadedFilamentLabel = stringResource(R.string.extrude_loaded_filament)
    val heatersRows = buildHeatersRows(
        presets = heatPresets,
        loadedSpool = loadedSpoolTemps(activeSpoolDetail, loadedFilamentLabel),
        scope = HeatScope.ExtruderOnly,
        extruderObject = vm.activeHeater,
        capabilities = caps,
    )

    ExtrudeContent(
        vm = vm,
        activeSpoolDetail = activeSpoolDetail,
        inFlight = inFlight,
        failureText = failureText,
        isPrinting = isPrinting,
        gating = gating,
        heatersRows = heatersRows,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        // SoftBusy — dispatcher queues re-taps; no screen-side inFlight guard needed.
        onExtrude = { dist, speed ->
            dispatcher?.dispatch(CommandRegistry.extrude, ExtrudeArgs(dist, speed * 60))
        },
        onRetract = { dist, speed ->
            dispatcher?.dispatch(CommandRegistry.extrude, ExtrudeArgs(-dist, speed * 60))
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
        onHeatApply = { dispatchHeat(dispatcher, it) },
        onToggleSensor = { _, sensorName, enable ->
            val key = "set_filament_sensor_$sensorName"
            if (key !in inFlight) {
                dispatcher?.dispatch(CommandRegistry.setFilamentSensor, SetFilamentSensorArgs(sensorName, enable))
            }
        },
        onRunMacro = { name ->
            // Pinned macros are SoftBusy (no fence — arbitrary macros are not fenced). Pass
            // GatingMode.SoftBusy so the global gatingState tracks them and the busy indicator fires.
            dispatcher?.dispatch(
                key = "macro_$name",
                method = JsonRpcMethods.GCODE_SCRIPT,
                params = PrinterCommands.scriptParams(MacroInvocation.buildRaw(name, "")),
                gating = GatingMode.SoftBusy,
            )
        },
        onToggleMacroPin = { name -> container.toggleExtrudeMacroPin(name) },
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
        onToggleSensor = { _, _, _ -> },
        onRunMacro = {},
        onToggleMacroPin = {},
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
    gating: GatingState = GatingState.Idle,
    heatersRows: List<HeatersRow> = emptyList(),
    onEmergencyStop: (() -> Unit)? = null,
    onExtrude: (distance: Double, speed: Int) -> Unit,
    onRetract: (distance: Double, speed: Int) -> Unit,
    onSelectTool: (Int) -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onHeatApply: (HeatDispatch) -> Unit = {},
    onToggleSensor: (objectKey: String, sensorName: String, enable: Boolean) -> Unit,
    onRunMacro: (name: String) -> Unit,
    onToggleMacroPin: (name: String) -> Unit,
    onBack: () -> Unit,
    onOpenSpool: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var distance by remember { mutableStateOf(DEFAULT_DISTANCE) }
    var speed by remember { mutableStateOf(DEFAULT_SPEED_MM_S) }
    var fieldMode by remember { mutableStateOf<ExtrudeFieldMode>(ExtrudeFieldMode.Main) }

    // Keep the length within the slider ceiling (owner max 100 mm, capped further by the printer's
    // reported max_extrude_only_distance) when that live ceiling drops below the current value.
    LaunchedEffect(vm.maxExtrudeDistance) {
        val ceiling = (vm.maxExtrudeDistance?.coerceAtMost(MAX_DISTANCE_MM) ?: MAX_DISTANCE_MM).toDouble()
        if (distance > ceiling) distance = ceiling
    }
    // If the printer's reported max velocity is below the current speed, pull speed down so Extrude
    // never dispatches over cap (the scrubber range also caps, but the seeded default could exceed a
    // low reported max before the user touches it).
    LaunchedEffect(vm.maxExtrudeVelocity) {
        if (speed > vm.maxExtrudeVelocity) speed = vm.maxExtrudeVelocity
    }

    // Show "Extruding…" only when an Extrude-owned SoftBusy key is active. gatingState is global so
    // filter to the keys this screen owns — Extrude owns extrude, retract, load, unload, macro_*.
    // TODO(owner ICON LAW): confirm final extrude-busy glyph — MoveTouch is the placeholder.
    val extrudeBusy = (gating as? GatingState.Busy)?.key?.let {
        it == "extrude" || it == "retract" || it == "load" || it == "unload" || it.startsWith("macro_")
    } == true

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = stringResource(R.string.cd_launcher_extrude),
                    icon = JiibIcons.LauncherExtrude,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxSize(),
                    isPrinting = isPrinting,
                    safetyActive = gating !is GatingState.Idle,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                    contentInset = FocusInset / 2, // shared adjustment-focus rhythm (matches Fine-Tune)
                    trailingStatusIcon = if (extrudeBusy) JiibIcons.MoveTouch else null,
                    trailingStatusContentDescription = if (extrudeBusy) stringResource(R.string.gating_extruding) else null,
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
                        // Canonical scrolling list (ListBlock = LazyColumn + gradient fade-edges +
                        // owned 8dp spacing); rows are the canonical ListRow class (translucent, 1U,
                        // 0.6U leading icons).
                        val loadLabel = stringResource(R.string.extrude_load)
                        val unloadLabel = stringResource(R.string.extrude_unload)
                        ListBlock(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            // 1. Runout sensors (the whole section is hidden when none discovered).
                            items(vm.sensors, key = { it.objectKey }) { s ->
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
                                item { MacroListRow(loadLabel, JiibIcons.ExpandCircleUp, onLoad, grid.uDp) }
                            }
                            if (vm.hasUnloadMacro) {
                                item { MacroListRow(unloadLabel, JiibIcons.ExpandCircleDown, onUnload, grid.uDp) }
                            }
                            items(vm.pinnedMacros, key = { "pin_${it.name}" }) { m ->
                                MacroListRow(
                                    label = m.name,
                                    icon = JiibIcons.LauncherMacros,
                                    onClick = { onRunMacro(m.name) },
                                    uDp = grid.uDp,
                                    busy = "macro_${m.name}" in inFlight,
                                )
                            }
                            // 3. Spool link.
                            item { SpoolStatusRow(activeSpoolDetail, grid.uDp, onOpenSpool) }
                        }
                        failureText?.let { msg ->
                            SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth())
                        }
                        FootButtonBar(
                            uDp = grid.uDp,
                            actions = listOf(
                                FootAction(
                                    label = stringResource(R.string.common_back),
                                    onClick = onBack,
                                    intent = Intent.Accent, // R5: Back/nav = accent
                                    icon = JiibIcons.Back,
                                ),
                                FootAction(
                                    label = stringResource(R.string.home_foot_heaters),
                                    onClick = { fieldMode = ExtrudeFieldMode.Heaters },
                                    intent = Intent.Accent,
                                    icon = JiibIcons.OutputHeater,
                                    contentDescription = stringResource(R.string.home_foot_heaters),
                                ),
                                FootAction(
                                    label = stringResource(R.string.extrude_macro_settings),
                                    onClick = { fieldMode = ExtrudeFieldMode.MacroSettings },
                                    intent = Intent.Accent,
                                    icon = JiibIcons.ManageMacros,
                                    contentDescription = stringResource(R.string.extrude_macro_settings),
                                ),
                            ),
                        )
                    }

                    is ExtrudeFieldMode.MacroSettings -> {
                        val t = LocalTokens.current
                        if (vm.allMacros.isEmpty()) {
                            Box(
                                Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.extrude_no_macros),
                                    color = t.text2,
                                    style = JiibType.caption.toTextStyle(t),
                                )
                            }
                        } else {
                            ListBlock(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                items(vm.allMacros, key = { it.name }) { m ->
                                    ToggleRow(
                                        label = m.name,
                                        checked = m.name in vm.pinnedNames,
                                        onToggle = { onToggleMacroPin(m.name) },
                                        uDp = grid.uDp,
                                    )
                                }
                            }
                        }
                        FootButtonBar(
                            uDp = grid.uDp,
                            actions = listOf(
                                FootAction(
                                    label = stringResource(R.string.common_back),
                                    onClick = { fieldMode = ExtrudeFieldMode.Main },
                                    intent = Intent.Accent,
                                    icon = JiibIcons.Back,
                                ),
                            ),
                        )
                    }

                    is ExtrudeFieldMode.Heaters -> {
                        HeatersList(
                            rows = heatersRows,
                            onApply = onHeatApply,
                            onApplied = { fieldMode = ExtrudeFieldMode.Main },
                            onBack = { fieldMode = ExtrudeFieldMode.Main },
                            uDp = grid.uDp,
                        )
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
 * The Focus feed surface, top→bottom: (optional tool selector) · an info line (temp · length · speed) ·
 * Length slider · Speed slider · an Extrude/Retract big-command row (the hero, fills the remaining
 * height; cold → [COLD_SYMBOL] + disabled).
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
    // Single vertical stack (2026-06-17 reflow): top→bottom = [tool selector] · nozzle readout ·
    // distance selector · speed slider · Extrude/Retract in one bottom row (the hero, fills remaining).
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (vm.showToolSelector) {
            ToolSelector(
                tools = vm.tools,
                inFlight = inFlight,
                onSelect = onSelectTool,
                modifier = Modifier.fillMaxWidth().controlHeight(uDp),
            )
        }
        // Info line + sliders, centered vertically in the leftover space above the command row.
        val lengthCeiling = vm.maxExtrudeDistance?.coerceAtMost(MAX_DISTANCE_MM) ?: MAX_DISTANCE_MM
        Column(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            // Info line — current temperature · length · speed (values only; the unit disambiguates).
            InfoLine(temp = vm.nozzleTemp, distance = distance, speed = speed, modifier = Modifier.fillMaxWidth())
            // Length slider — title start-aligned, bare track; max 100 mm (capped by printer ceiling).
            LabeledSlider(
                title = stringResource(R.string.extrude_length),
                value = distance.toFloat(),
                range = MIN_DISTANCE_MM..lengthCeiling,
                onChange = { onSelectDistance(it.toDouble()) },
                uDp = uDp,
            )
            // Speed slider — capped to the printer's reported max_extrude_only_velocity.
            LabeledSlider(
                title = stringResource(R.string.extrude_speed_readout),
                value = speed.toFloat(),
                range = MIN_SPEED_MM_S.toFloat()..vm.maxExtrudeVelocity.toFloat(),
                onChange = { onSpeedSettle(it.roundToInt()) },
                uDp = uDp,
            )
        }
        // Command row pinned at the bottom — canonical [OutlinedControl] (filled + intent-colored
        // border), 1U tall, glyph at the 0.6U tier (LocalUnitDp provided like every other control
        // row). Warm = Accent; cold lockout = Danger/stop border + cold glyph, not tappable; an
        // in-flight dispatch dims the affected button.
        CompositionLocalProvider(LocalUnitDp provides uDp) {
            Row(
                Modifier.fillMaxWidth().controlHeight(uDp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val extrudeBusy = "extrude" in inFlight
                OutlinedControl(
                    label = "",
                    onClick = onExtrude,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .alpha(if (vm.canExtrude && extrudeBusy) 0.38f else 1f),
                    intent = if (vm.canExtrude) Intent.Accent else Intent.Danger,
                    symbol = if (vm.canExtrude) "output_circle" else COLD_SYMBOL,
                    enabled = vm.canExtrude && !extrudeBusy,
                    contentDescription = stringResource(R.string.extrude_cmd_extrude),
                )
                val retractBusy = "retract" in inFlight
                OutlinedControl(
                    label = "",
                    onClick = onRetract,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .alpha(if (vm.canExtrude && retractBusy) 0.38f else 1f),
                    intent = if (vm.canExtrude) Intent.Accent else Intent.Danger,
                    symbol = if (vm.canExtrude) "input_circle" else COLD_SYMBOL,
                    enabled = vm.canExtrude && !retractBusy,
                    contentDescription = stringResource(R.string.extrude_cmd_retract),
                )
            }
        }
    }
}

/**
 * A labelled bare slider row: the [title] start-aligned on the same line as a track-only [Scrubber]
 * (no ± stepper, no inline value — the value is shown in the [SliderValuesLine] above). [onChange]
 * fires live per drag frame AND once on settle; it only mutates caller state, never dispatches.
 * Title/track widths are weighted (1:3) so both sliders' tracks align.
 */
@Composable
private fun LabeledSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = t.text,
            style = JiibType.listLabel.toTextStyle(t),
            modifier = Modifier.weight(1f),
        )
        Scrubber(
            name = "",
            value = value,
            range = range,
            step = 1f,
            uDp = uDp,
            bare = true,
            onSettle = onChange,
            onValueChange = onChange,
            modifier = Modifier.weight(3f),
        )
    }
}

/**
 * The Focus info line, start→finish: current temperature · length · speed. Values only — no titles
 * (the unit on each disambiguates; the sliders below carry their own start titles). Temperature is the
 * live current reading only (no setpoint).
 */
@Composable
private fun InfoLine(temp: Double, distance: Double, speed: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
        ValueReadout(temp.roundToInt().toString(), "°C")
        ValueReadout(fmtDist(distance), "mm")
        ValueReadout(speed.toString(), "mm/s")
    }
}

/**
 * One "value unit" readout — the canonical Focus value/unit pairing (AdjusterPanel): a [focusHero]
 * value with a readable [statValue] unit, baseline-aligned so the unit rides the value's baseline
 * (not a subscript). Unit is [text2] (brighter than the old dim [text3]) for legibility.
 */
@Composable
private fun ValueReadout(value: String, unit: String) {
    val t = LocalTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = value,
            color = t.text,
            style = JiibType.focusHero.toTextStyle(t),
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = unit,
            color = t.text2,
            style = JiibType.statValue.toTextStyle(t),
            modifier = Modifier.alignByBaseline(),
        )
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
                    style = JiibType.dataInline.toTextStyle(t),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Field sub-composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A macro action list row — canonical [ListRow] with an accent 0.6U leading icon; runs bare on tap.
 * Dims when [busy] (an in-flight dispatch of the same macro).
 */
@Composable
private fun MacroListRow(
    label: String,
    icon: JiibIcon,
    onClick: () -> Unit,
    uDp: Dp,
    busy: Boolean = false,
) {
    val t = LocalTokens.current
    ListRow(
        selected = false,
        onClick = onClick,
        uDp = uDp,
        modifier = Modifier.alpha(if (busy) 0.4f else 1f),
        leadingContent = { ListRowIcon(icon = icon, uDp = uDp, tint = t.accent) },
    ) {
        ListRowLabel(label)
    }
}

/** Distance label: drop the trailing ".0" on whole-mm values. */
private fun fmtDist(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
