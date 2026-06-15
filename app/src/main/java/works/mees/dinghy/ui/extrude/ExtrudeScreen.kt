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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandMap
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.CommandSpec
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.ExtrudeArgs
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.command.SelectToolArgs
import works.mees.dinghy.command.SetHeaterArgs
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.icons.SpoolGlyph
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
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

/** Default feedrate (mm/s) — must be a member of [SPEED_PRESETS]. */
private const val DEFAULT_SPEED_MM_S = 5

/** The fixed 4-up filament-length presets (mm). Selecting one fills the focus Distance readout. */
private val DISTANCE_PRESETS = listOf(1.0, 5.0, 25.0, 50.0)

/** The fixed 4-up extrusion-feedrate presets (mm/s). Selecting one fills the focus Speed readout. */
private val SPEED_PRESETS = listOf(1, 2, 5, 10)

/** IME bounds for a manual filament length (mm). */
private const val MIN_DISTANCE = 0.1

/** IME bounds for a manual feedrate (mm/s) — ceiling mirrors PrinterCommands' mm/min cap (÷60). */
private const val MIN_SPEED_MM_S = 1
private val MAX_SPEED_MM_S = PrinterCommands.MAX_EXTRUDE_FEED_MM_MIN / 60

/** Material-Symbol shown on Extrude/Retract while the cold-extrude gate is closed (needs heat). */
private const val COLD_SYMBOL = "thermostat_arrow_down"

/**
 * The in-screen Field mode: either the main Extrude controls (default) or the filament-preset
 * Field-takeover for setting the nozzle temperature (D-17).
 */
sealed class ExtrudeFieldMode {
    /** Main Extrude panel — distance/speed presets + nozzle-temp button + tool selector. */
    data object Main : ExtrudeFieldMode()

    /** Filament-preset takeover — ListBlock of material presets + loaded-spool row; applies extruder temp only. */
    data object FilamentPresets : ExtrudeFieldMode()
}

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
 *  - RIGHT: the live **Distance** and **Speed** setting readouts with inline numeric IME entry
 *    (clamped to the safe range before dispatch — T-26-06-01).
 *
 * ## Field — three equal-height rows that fill the region
 *  - A 4-up distance preset row (1/5/25/50 mm); selecting fills the Distance readout. Any preset above
 *    the live [ExtrudeVm.maxExtrudeDistance] (`max_extrude_only_distance`) is DISABLED.
 *  - A 4-up feedrate preset row (1/2/5/10 mm/s); selecting fills the Speed readout.
 *  - A two-button row: a nozzle-temp button (live current temp; tap opens the filament-preset
 *    Field-takeover [ExtrudeFieldMode.FilamentPresets]) and a Spool button (reactive [SpoolGlyph],
 *    no label; navigates to the Spoolman screen via `onOpenSpool`).
 *  - A T0/T1… tool row is inserted ONLY when [ExtrudeVm.showToolSelector] (EXTR-03 / D-09).
 *
 * ## FootButtonBar — Load / Unload / Back (EXTR-02 / D-10)
 * Load and Unload are ALWAYS shown. Present-macro tap dispatches `loadFilament()` / `unloadFilament()`;
 * ABSENT shows an informational [SeverityToast] (never a failed dispatch). Load/Unload are [Intent.Warn]
 * (caution — they heat + drive filament); Back ([Intent.Accent]) returns (R5: nav = accent).
 *
 * Every action dispatches a registry gcode entry via the per-session
 * [works.mees.dinghy.command.CommandDispatcher]. A control whose dispatch key is in-flight is disabled
 * (T-05-07-T). A dispatcher [DispatchEvent.Failure] surfaces an error [SeverityToast].
 *
 * @param container          the service-locator (provides the session dispatcher).
 * @param holder             the toolkit-agnostic [ExtrudeHolder] (live gate + tools + temp + macro presence).
 * @param activeSpoolDetail  the currently loaded spool (for the filament-preset takeover's extra row).
 * @param onBack             invoked by the neutral Back foot button.
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

    // WR-02 (26-rev): collect dispatcher failure events so a failed extrude/retract/heater dispatch
    // actually surfaces the promised error SeverityToast — the old onDispatchFailure parameter
    // referenced a collector that never existed, silently dropping every failure. Mirrors the
    // FineTuneScreen/TemperatureScreen collector pattern.
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
            else null // caller shows info toast
        },
        onUnload = {
            if (vm.hasUnloadMacro) dispatcher?.dispatch(CommandRegistry.unloadFilament, Unit)
            else null
        },
        onSetExtruderTemp = { temp ->
            val clamped = PrinterCommands.clampHeaterTarget(temp)
            dispatcher?.dispatch(CommandRegistry.setHeater, SetHeaterArgs(vm.activeHeater, clamped, key = "set_temp"))
        },
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
    onBack: () -> Unit,
    onOpenSpool: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var distance by remember { mutableStateOf(DEFAULT_DISTANCE) }
    var speed by remember { mutableStateOf(DEFAULT_SPEED_MM_S) }
    var fieldMode by remember { mutableStateOf<ExtrudeFieldMode>(ExtrudeFieldMode.Main) }
    var infoText by remember { mutableStateOf<String?>(null) }

    // If the live max-extrude ceiling makes the selected length illegal, fall back to the largest enabled.
    LaunchedEffect(vm.maxExtrudeDistance) {
        val max = vm.maxExtrudeDistance
        if (max != null && distance > max) {
            distance = DISTANCE_PRESETS.filter { it <= max }.maxOrNull() ?: DISTANCE_PRESETS.first()
        }
    }
    LaunchedEffect(infoText) {
        if (infoText != null) {
            delay(4_000)
            infoText = null
        }
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
                        canExtrude = vm.canExtrude,
                        inFlight = inFlight,
                        distance = distance,
                        speed = speed,
                        onDistanceChange = { newDist ->
                            // Always clamp before storing (T-26-06-01: IME input is an untrusted surface).
                            val ceiling = vm.maxExtrudeDistance?.toDouble()?.coerceAtMost(PrinterCommands.MAX_EXTRUDE_MM)
                                ?: PrinterCommands.MAX_EXTRUDE_MM
                            distance = newDist.coerceIn(MIN_DISTANCE, ceiling)
                        },
                        onSpeedChange = { newSpeed ->
                            speed = newSpeed.coerceIn(MIN_SPEED_MM_S, MAX_SPEED_MM_S.toInt())
                        },
                        onExtrude = { onExtrude(distance, speed) },
                        onRetract = { onRetract(distance, speed) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
            field = {
                val t = LocalTokens.current
                // R4: macro names flow from CommandMap so a fork's rename shows in the copy.
                val noLoadMacro =
                    stringResource(R.string.extrude_no_load_macro, CommandMap.loadFilament.macro)
                val noUnloadMacro =
                    stringResource(R.string.extrude_no_unload_macro, CommandMap.unloadFilament.macro)
                when (fieldMode) {
                    is ExtrudeFieldMode.Main -> {
                        Column(
                            Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SelectorLabel(stringResource(R.string.extrude_distance_label))
                            DistanceSelector(
                                selected = distance,
                                maxDistance = vm.maxExtrudeDistance,
                                onSelect = { distance = it },
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            )
                            SelectorLabel(stringResource(R.string.extrude_speed_label))
                            SpeedSelector(
                                selected = speed,
                                onSelect = { speed = it },
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            )
                            if (vm.showToolSelector) {
                                SelectorLabel(stringResource(R.string.extrude_tool_label))
                                ToolSelector(
                                    tools = vm.tools,
                                    inFlight = inFlight,
                                    onSelect = onSelectTool,
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                )
                            }
                            // Nozzle-temp button (opens filament-preset Field-takeover, D-17) + Spool button (→ Spoolman).
                            val tempColor = if (vm.canExtrude) t.go else t.stop
                            // Reactive spool swatches (D-07/D-08): same derivation as the home launcher
                            // tile — normalized Spoolman colors → empty list renders the honest empty spool.
                            val spoolSwatches = remember(activeSpoolDetail) {
                                activeSpoolDetail?.filament?.colorSwatches.orEmpty()
                                    .mapNotNull(::parseNormalizedHex)
                            }
                            Row(
                                Modifier.fillMaxWidth().weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FieldButton(
                                    text = "${vm.nozzleTemp.roundToInt()}°",
                                    onClick = { fieldMode = ExtrudeFieldMode.FilamentPresets },
                                    borderColor = tempColor,
                                    contentColor = tempColor,
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
                                    text = "",
                                    onClick = onOpenSpool,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    icon = {
                                        SpoolGlyph(
                                            swatches = spoolSwatches,
                                            bodyTint = t.text2,
                                            keyline = t.hair,
                                            sizeDp = fsSp(40f, t.fs).dp,
                                            contentDescription = stringResource(R.string.cd_launcher_spool),
                                        )
                                    },
                                )
                            }
                            failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
                            infoText?.let { msg -> SeverityToast(Severity.Info, msg, Modifier.fillMaxWidth()) }
                        }
                        FootButtonBar(
                            uDp = grid.uDp,
                        ) {
                            OutlinedControl(
                                label = "",
                                onClick = onBack,
                                modifier = Modifier.weight(1f),
                                intent = Intent.Accent, // R5: Back/nav = accent
                                icon = DinghyIcons.Back,
                            )
                            OutlinedControl(
                                label = stringResource(R.string.extrude_load),
                                onClick = {
                                    if (vm.hasLoadMacro) onLoad()
                                    else infoText = noLoadMacro
                                },
                                modifier = Modifier.weight(1f),
                                intent = Intent.Warn, // amber — heats + drives filament in (caution)
                                icon = DinghyIcons.ExpandCircleUp,
                            )
                            OutlinedControl(
                                label = stringResource(R.string.extrude_unload),
                                onClick = {
                                    if (vm.hasUnloadMacro) onUnload()
                                    else infoText = noUnloadMacro
                                },
                                modifier = Modifier.weight(1f),
                                intent = Intent.Warn, // amber — heats + drives filament out
                                icon = DinghyIcons.ExpandCircleDown,
                            )
                        }
                    }

                    is ExtrudeFieldMode.FilamentPresets -> {
                        Column(
                            Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.extrude_preset_title),
                                color = t.text2,
                                style = DinghyType.caption.toTextStyle(t),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            // Loaded-spool row — shown when Spoolman provides an active spool with a temp.
                            val loadedSpool = activeSpoolDetail
                            val loadedTemp = loadedSpool?.filament?.settingsExtruderTemp
                            if (loadedSpool != null && loadedTemp != null) {
                                val spoolName = loadedSpool.filament?.name
                                    ?: loadedSpool.filament?.material
                                    ?: stringResource(R.string.extrude_loaded_filament)
                                PresetRow(
                                    name = stringResource(R.string.extrude_loaded_prefix, spoolName),
                                    temp = loadedTemp,
                                    onClick = {
                                        onSetExtruderTemp(loadedTemp)
                                        fieldMode = ExtrudeFieldMode.Main
                                    },
                                )
                            }
                            // Standard material presets.
                            PrinterCommands.MATERIAL_PRESETS.forEach { preset ->
                                PresetRow(
                                    name = preset.name,
                                    temp = preset.nozzle,
                                    onClick = {
                                        onSetExtruderTemp(preset.nozzle)
                                        fieldMode = ExtrudeFieldMode.Main
                                    },
                                )
                            }
                        }
                        FootButtonBar(
                            uDp = grid.uDp,
                        ) {
                            OutlinedControl(
                                label = "",
                                onClick = { fieldMode = ExtrudeFieldMode.Main },
                                modifier = Modifier.weight(1f),
                                intent = Intent.Neutral,
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
 * The two-column Focus: LEFT = Extrude/Retract big commands (cold → [COLD_SYMBOL] + disabled),
 * RIGHT = Distance/Speed setting readouts with inline numeric IME (D-16).
 */
@Composable
private fun FocusGrid(
    canExtrude: Boolean,
    inFlight: Set<String>,
    distance: Double,
    speed: Int,
    onDistanceChange: (Double) -> Unit,
    onSpeedChange: (Int) -> Unit,
    onExtrude: () -> Unit,
    onRetract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // LEFT column — the physical commands.
        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BigCommand(
                label = stringResource(R.string.extrude_cmd_extrude),
                symbol = if (!canExtrude) COLD_SYMBOL else "output_circle",
                disabled = !canExtrude || "extrude" in inFlight,
                onClick = onExtrude,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            BigCommand(
                label = stringResource(R.string.extrude_cmd_retract),
                symbol = if (!canExtrude) COLD_SYMBOL else "input_circle",
                disabled = !canExtrude || "retract" in inFlight,
                onClick = onRetract,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        // RIGHT column — numeric IME entry for Distance and Speed (D-16).
        val t = LocalTokens.current
        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val keyboardController = LocalSoftwareKeyboardController.current

            // Distance field — parsed to Double + clamped before updating state (T-26-06-01).
            var distanceText by rememberSaveable { mutableStateOf(fmtDist(distance)) }
            // Keep the display text in sync when the preset selector or LaunchedEffect updates distance.
            LaunchedEffect(distance) {
                val synced = fmtDist(distance)
                if (distanceText != synced) distanceText = synced
            }
            NumericSettingReadout(
                label = stringResource(R.string.extrude_distance_readout),
                text = distanceText,
                unit = "mm",
                onTextChange = { raw ->
                    distanceText = raw
                    // Parse and clamp — NEVER concatenate raw IME text into gcode (T-26-06-01).
                    raw.toDoubleOrNull()?.let { parsed ->
                        if (parsed > 0.0) onDistanceChange(parsed)
                    }
                },
                onDone = {
                    // WR-03 (26-rev): ALWAYS resync the display text from the clamped state — not
                    // only when parsing fails. The LaunchedEffect(distance) resync fires only when
                    // distance CHANGES, so typing "500" against a 50 mm ceiling left the field
                    // showing 500 while the clamped state (and every dispatch) stayed at 50.
                    distanceText = fmtDist(distance)
                    keyboardController?.hide()
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            // Speed field — parsed to Int + clamped (T-26-06-01).
            var speedText by rememberSaveable { mutableStateOf(speed.toString()) }
            LaunchedEffect(speed) {
                val synced = speed.toString()
                if (speedText != synced) speedText = synced
            }
            NumericSettingReadout(
                label = stringResource(R.string.extrude_speed_readout),
                text = speedText,
                unit = "mm/s",
                onTextChange = { raw ->
                    speedText = raw
                    raw.toDoubleOrNull()?.let { parsed ->
                        if (parsed > 0.0) onSpeedChange(parsed.roundToInt())
                    }
                },
                onDone = {
                    // WR-03: unconditional resync from clamped state (see distance field above).
                    speedText = speed.toString()
                    keyboardController?.hide()
                },
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
            // UAT-1: BigCommand is a Focus hero tile — prominent icon tier (~70-80% of U).
            // Bumped from 40f → 56f to be "big and vibrant" without overflowing the cell.
            MaterialSymbol(name = symbol, tint = tint, sizeSp = fsSp(56f, t.fs))
            Text(
                text = label,
                color = tint,
                style = DinghyType.buttonLabel.toTextStyle(t),
            )
        }
    }
}

/**
 * A live setting readout with inline numeric IME (D-16): neutral-outlined, the current value
 * as the hero in GeistMono, a small label above. The [BasicTextField] uses [KeyboardType.Decimal];
 * values are parsed and clamped before dispatch (T-26-06-01 — never pass raw IME text to gcode).
 */
@Composable
private fun NumericSettingReadout(
    label: String,
    text: String,
    unit: String,
    onTextChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .clip(shape)
            .border(BorderStroke(2.dp, t.outline), shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                color = t.text2,
                style = DinghyType.caption.toTextStyle(t),
            )
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                textStyle = DinghyType.focusHero.toTextStyle(t).copy(
                    color = t.text,
                    textAlign = TextAlign.Center,
                ),
                singleLine = true,
            )
            Text(
                text = unit,
                color = t.text3,
                style = DinghyType.dataMeta.toTextStyle(t),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Field sub-composables
// ─────────────────────────────────────────────────────────────────────────────

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
        if (text.isBlank()) {
            // Icon-only field button (e.g. the Spool button — the reactive spool glyph IS the label).
            icon()
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                icon()
                Text(
                    text = text,
                    color = contentColor,
                    style = DinghyType.statValue.toTextStyle(t),
                )
            }
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
        style = DinghyType.caption.toTextStyle(t),
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
                    style = DinghyType.statValue.toTextStyle(t),
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
                    style = DinghyType.statValue.toTextStyle(t),
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
                    style = DinghyType.dataInline.toTextStyle(t),
                )
            }
        }
    }
}

/**
 * A single row in the filament-preset takeover (D-17): material name on the left, nozzle temp on the
 * right. Selecting dispatches the extruder temperature ONLY (never the bed — D-17).
 */
@Composable
private fun PresetRow(
    name: String,
    temp: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .border(BorderStroke(1.dp, t.outline), shape)
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
