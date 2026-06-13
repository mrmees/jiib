package works.mees.dinghy.ui.move

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.CommandSpec
import works.mees.dinghy.command.HomeAxisArgs
import works.mees.dinghy.command.JogArgs
import works.mees.dinghy.command.MoveToArgs
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.components.ListRowLabel
import works.mees.dinghy.designsystem.components.Scrubber
import works.mees.dinghy.designsystem.components.ScrubberOrientation
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.ui.screen.TokenTextField

/**
 * The current Move-Hub sub-mode. The Field action-list selects a mode; the Focus swaps content
 * (and its header title/icon) accordingly. Each Field row selects a Focus sub-mode: Touch Move
 * (tap-to-move bed map), XY/Z scrubbers, Microstep jogger, per-bookmark Move/Delete, Save dialog.
 * [Bookmark] carries the tapped saved-location name; [SaveDialog] is the save-name flow.
 */
sealed interface MoveMode {
    data object TouchMove : MoveMode
    data object XY : MoveMode
    data object Z : MoveMode
    data object Microstep : MoveMode
    data class Bookmark(val name: String) : MoveMode
    data object SaveDialog : MoveMode
}

/**
 * The Move Hub — the keystone of the Move redesign (Task D3). Mirrors the [FineTuneScreen] Hub
 * archetype: a [ScreenScaffold] whose Field is a translucent [ListRow] action-list and whose Focus
 * is a [FocusFrame] that swaps its content (and header identity) by the selected [MoveMode].
 *
 * This LIVE entry mirrors [OldMoveScreen]'s collection (dispatcher / vm / printerState / inFlight /
 * isPrinting) and additionally collects [AppContainer.savedLocations], then delegates rendering to
 * the stateless [MoveHubContent]. Signature is IDENTICAL to [OldMoveScreen] so the AppShell route
 * swap is one line.
 *
 * @param container service-locator (live `printerState`, session dispatcher, saved locations).
 * @param holder    toolkit-agnostic [MoveHolder] (live X/Y/Z + per-axis homed gating + bounds/feed).
 * @param onBack    invoked by the Back foot button from Touch Move.
 */
@Composable
fun MoveScreen(
    container: AppContainer,
    holder: MoveHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val vm by holder.vm.collectAsStateWithLifecycle()
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    val savedLocations by container.savedLocations.collectAsStateWithLifecycle()

    // One in-flight-guarded dispatch helper — every action funnels through the registry (no raw rpc).
    fun <P> dispatchCommand(command: CommandSpec<P>, args: P) {
        if (command.dispatchKey(args) in inFlight) return
        dispatcher?.dispatch(command, args)
    }

    MoveHubContent(
        vm = vm,
        savedLocations = savedLocations,
        isPrinting = isPrinting,
        onMoveTo = { x, y, z ->
            dispatchCommand(
                CommandRegistry.moveTo,
                MoveToArgs(x, y, z, vm.travelFeedMmMin, vm.axisMin, vm.axisMax),
            )
        },
        onJog = { axis, mm -> dispatchCommand(CommandRegistry.jog, JogArgs(axis, mm, vm.travelFeedMmMin)) },
        onHomeAll = { dispatchCommand(CommandRegistry.homeAll, Unit) },
        onDisableSteppers = { dispatchCommand(CommandRegistry.disableSteppers, Unit) },
        onHomeXY = { dispatchCommand(CommandRegistry.homeXY, Unit) },
        onHomeAxis = { axis -> dispatchCommand(CommandRegistry.homeAxis, HomeAxisArgs(axis)) },
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onSaveLocation = { container.saveLocation(it) },
        onDeleteLocation = { container.deleteLocation(it) },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * The stateless Move Hub surface — no live Moonraker, no AppContainer, no dispatcher. Renders the
 * Field action-list + the mode-swapping Focus, holding only the screen-local selected [MoveMode].
 *
 * ## Sub-modes
 * All six sub-modes are fully implemented: [MoveMode.TouchMove] shows the tap-to-move [BedMapView]
 * (or a homing hint when bounds are unknown); [MoveMode.XY] and [MoveMode.Z] use [Scrubber]
 * controls; [MoveMode.Microstep] shows a wrapping step selector and per-axis jog buttons;
 * [MoveMode.Bookmark] shows Move/Delete for a saved location; [MoveMode.SaveDialog] is the
 * save-name form.
 *
 * ## Header law
 * The [FocusFrame] header title + icon track the CURRENT mode; the docked e-stop morphs in while
 * [isPrinting] (e-stop params copied verbatim from FineTuneScreen's FocusFrame call).
 */
@Composable
internal fun MoveHubContent(
    vm: MoveVm,
    savedLocations: List<SavedLocation>,
    isPrinting: Boolean,
    onMoveTo: (Double?, Double?, Double?) -> Unit,
    onJog: (String, Double) -> Unit,
    onHomeAll: () -> Unit,
    onDisableSteppers: () -> Unit,
    onHomeXY: () -> Unit,
    onHomeAxis: (String) -> Unit,
    onEmergencyStop: () -> Unit,
    onSaveLocation: (SavedLocation) -> Unit,
    onDeleteLocation: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    var mode by remember { mutableStateOf<MoveMode>(MoveMode.TouchMove) }

    // Delete-confirm overlay: non-null = name of the location pending deletion.
    var deleteConfirm by remember { mutableStateOf<String?>(null) }

    // Staged target (finger-live preview) and committed target for travel-line tracking.
    // Both keyed on mode so entering a new sub-mode always starts clean.
    var staged by remember(mode) { mutableStateOf<Pair<Double, Double>?>(null) }
    var committedTarget by remember(mode) { mutableStateOf<Pair<Double, Double>?>(null) }

    val avail = moveRowAvailability(vm.xHomed, vm.yHomed, vm.zHomed)

    // Bed extent from toolhead.axis_minimum/axis_maximum X/Y (indices 0,1). Null until first
    // snapshot (or if either bounds list is too short) — TouchMove shows a homing hint then.
    val bed: BedExtent? = run {
        val mn = vm.axisMin
        val mx = vm.axisMax
        if (mn != null && mx != null && mn.size >= 2 && mx.size >= 2) {
            BedExtent(xMin = mn[0], xMax = mx[0], yMin = mn[1], yMax = mx[1])
        } else {
            null
        }
    }

    val (headerTitle, headerIcon) = moveModeHeader(mode)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = headerTitle,
                    icon = headerIcon,
                    uDp = grid.uDp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    when (mode) {
                        MoveMode.TouchMove -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // X / Y coordinate readout — staged while gesturing, else current.
                                // Shown at the TOP so the position is always visible above the bed map.
                                val shownX = staged?.first ?: vm.x
                                val shownY = staged?.second ?: vm.y
                                Text(
                                    text = "X ${fmt1(shownX)}   Y ${fmt1(shownY)}",
                                    fontFamily = GeistMono,
                                    fontSize = fsSp(22f, t.fs).sp,
                                    color = t.text,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                )
                                // Bed map or waiting-for-bounds hint.
                                if (bed != null) {
                                    val cx = vm.x
                                    val cy = vm.y
                                    val ct = committedTarget
                                    val travelling = ct != null && cx != null && cy != null &&
                                        travelPending(cx, cy, ct.first, ct.second)
                                    BedMapView(
                                        bed = bed,
                                        current = if (cx != null && cy != null) cx to cy else null,
                                        target = staged ?: committedTarget,
                                        travel = travelling,
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        onTapBed = { x, y -> staged = x to y },
                                        onDragBed = { x, y -> staged = x to y },
                                        onDragEnd = {
                                            staged?.let { (x, y) ->
                                                onMoveTo(x, y, null)
                                                committedTarget = x to y
                                            }
                                        },
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                    ) {
                                        FocusHint("Waiting for printer bounds…")
                                    }
                                }
                                // Instruction line — below the bed map, centered.
                                Text(
                                    text = "Tap to move, hold to refine",
                                    color = t.text2,
                                    fontFamily = GeistMono,
                                    fontSize = fsSp(15f, t.fs).sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                )
                            }
                        }
                        MoveMode.XY -> {
                            if (bed == null) {
                                FocusHint("Waiting for printer bounds…")
                            } else {
                                val xMinF = bed.xMin.toFloat()
                                val xMaxF = bed.xMax.toFloat()
                                val yMinF = bed.yMin.toFloat()
                                val yMaxF = bed.yMax.toFloat()
                                var workingX by remember(mode) {
                                    mutableFloatStateOf(
                                        (vm.x?.toFloat() ?: ((xMinF + xMaxF) / 2f)).coerceIn(xMinF, xMaxF),
                                    )
                                }
                                var workingY by remember(mode) {
                                    mutableFloatStateOf(
                                        (vm.y?.toFloat() ?: ((yMinF + yMaxF) / 2f)).coerceIn(yMinF, yMaxF),
                                    )
                                }
                                val cx = vm.x
                                val cy = vm.y
                                val currentPair = if (cx != null && cy != null) cx to cy else null

                                Column(modifier = Modifier.fillMaxSize()) {
                                    // TOP: centered X/Y coordinate readout showing the working target.
                                    Text(
                                        text = "X ${fmt1(workingX.toDouble())}   Y ${fmt1(workingY.toDouble())}",
                                        fontFamily = GeistMono,
                                        fontSize = fsSp(22f, t.fs).sp,
                                        color = t.text,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                    )
                                    // BELOW: bed map + sliders (both bare — track only, no steppers).
                                    // Size the plate to EXACTLY bedAspect (so BedMapView fills it
                                    // edge-to-edge, NO letterbox) within the area left after reserving
                                    // the scrubber gutters, then match X/Y scrubbers to plate.width/height.
                                    val bedAspect = (bed.width / bed.height).toFloat()
                                    val control = minOf(grid.uDp, 74.dp) // scrubber track thickness
                                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                                        // Area available for the plate = full area minus the gutters
                                        // (Y scrubber on the right, X scrubber below). Clamp to a small
                                        // positive floor so a tiny screen can't yield a zero/negative size.
                                        val availW = (maxWidth - control).coerceAtLeast(1.dp)
                                        val availH = (maxHeight - control).coerceAtLeast(1.dp)
                                        // Aspect-lock the plate into the available area (no stretch).
                                        val plate = if (availW / availH > bedAspect) {
                                            // height-bound
                                            val h = availH
                                            DpSize(h * bedAspect, h)
                                        } else {
                                            // width-bound
                                            val w = availW
                                            DpSize(w, w / bedAspect)
                                        }
                                        // Center the whole bed+scrubber assembly horizontally,
                                        // top-anchored. The Column wraps its content width
                                        // (plate.width + control — the Row is the widest child),
                                        // so TopCenter centers it while the start-aligned X scrubber
                                        // stays directly under the plate's LEFT edge.
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.TopCenter,
                                        ) {
                                            Column(horizontalAlignment = Alignment.Start) {
                                                Row(verticalAlignment = Alignment.Top) {
                                                    // Plate fills this box exactly (aspect matches) -> no letterbox.
                                                    BedMapView(
                                                        bed = bed,
                                                        current = currentPair,
                                                        target = workingX.toDouble() to workingY.toDouble(),
                                                        travel = false,
                                                        modifier = Modifier.size(plate),
                                                    )
                                                    // Y scrubber: same height as the plate, to its right.
                                                    Box(Modifier.width(control).height(plate.height)) {
                                                        Scrubber(
                                                            name = "Y",
                                                            value = workingY,
                                                            range = yMinF..yMaxF,
                                                            step = 1f,
                                                            uDp = grid.uDp,
                                                            unit = "mm",
                                                            orientation = ScrubberOrientation.Vertical,
                                                            onValueChange = { workingY = it },
                                                            onSettle = { v ->
                                                                workingY = v
                                                                onMoveTo(workingX.toDouble(), workingY.toDouble(), null)
                                                            },
                                                        )
                                                    }
                                                }
                                                // X scrubber: same width as the plate, below it (left-aligned).
                                                Box(Modifier.width(plate.width).height(control)) {
                                                    Scrubber(
                                                        name = "X",
                                                        value = workingX,
                                                        range = xMinF..xMaxF,
                                                        step = 1f,
                                                        uDp = grid.uDp,
                                                        unit = "mm",
                                                        bare = true,
                                                        onValueChange = { workingX = it },
                                                        onSettle = { v ->
                                                            workingX = v
                                                            onMoveTo(workingX.toDouble(), workingY.toDouble(), null)
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        MoveMode.Z -> {
                            val zMax = vm.axisMax?.getOrNull(2)?.toFloat()
                            if (zMax == null) {
                                FocusHint("Waiting for printer bounds…")
                            } else {
                                var workingZ by remember(mode) {
                                    mutableFloatStateOf((vm.z?.toFloat() ?: 0f).coerceIn(0f, zMax))
                                }
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Z readout — centered at the top.
                                    Text(
                                        text = "Z  ${fmt1(workingZ.toDouble())} mm",
                                        fontFamily = GeistMono,
                                        fontSize = fsSp(22f, t.fs).sp,
                                        color = t.text,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                    )
                                    // Two equal-weight scrubber columns with numeric endpoint labels.
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                    ) {
                                        // Fine: 0–50 mm at 0.1 mm resolution.
                                        Column(
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                text = "50",
                                                fontFamily = GeistMono,
                                                fontSize = fsSp(15f, t.fs).sp,
                                                color = t.text2,
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .weight(1f),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Scrubber(
                                                    name = "Fine",
                                                    value = workingZ,
                                                    range = 0f..50f,
                                                    step = 0.1f,
                                                    uDp = grid.uDp,
                                                    unit = "mm",
                                                    orientation = ScrubberOrientation.Vertical,
                                                    onValueChange = { workingZ = it },
                                                    onSettle = { v ->
                                                        workingZ = v
                                                        onMoveTo(null, null, workingZ.toDouble())
                                                    },
                                                )
                                            }
                                            Text(
                                                text = "0",
                                                fontFamily = GeistMono,
                                                fontSize = fsSp(15f, t.fs).sp,
                                                color = t.text2,
                                            )
                                        }
                                        // Full: 0–Zmax at 1 mm resolution.
                                        Column(
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                text = fmt1(zMax.toDouble()),
                                                fontFamily = GeistMono,
                                                fontSize = fsSp(15f, t.fs).sp,
                                                color = t.text2,
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .weight(1f),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Scrubber(
                                                    name = "Full",
                                                    value = workingZ,
                                                    range = 0f..zMax,
                                                    step = 1f,
                                                    uDp = grid.uDp,
                                                    unit = "mm",
                                                    orientation = ScrubberOrientation.Vertical,
                                                    onValueChange = { workingZ = it },
                                                    onSettle = { v ->
                                                        workingZ = v
                                                        onMoveTo(null, null, workingZ.toDouble())
                                                    },
                                                )
                                            }
                                            Text(
                                                text = "0",
                                                fontFamily = GeistMono,
                                                fontSize = fsSp(15f, t.fs).sp,
                                                color = t.text2,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        MoveMode.Microstep -> {
                            val anyHomed = vm.xHomed || vm.yHomed || vm.zHomed
                            if (!anyHomed) {
                                FocusHint("Home an axis to micro-step")
                            } else {
                                val steps: ImmutableList<Double> = remember {
                                    persistentListOf(0.01, 0.025, 0.1, 0.25, 1.0, 2.5, 10.0)
                                }
                                var stepIndex by remember(mode) {
                                    mutableStateOf(steps.indexOf(0.1).coerceAtLeast(0))
                                }
                                val activeStep = steps[stepIndex]

                                // Selected axis — reset on mode entry; defaults to the first homed axis.
                                var selectedAxis by remember(mode) {
                                    mutableStateOf(
                                        when {
                                            vm.xHomed -> "X"
                                            vm.yHomed -> "Y"
                                            vm.zHomed -> "Z"
                                            else -> "X"
                                        },
                                    )
                                }
                                val selectedHomed = when (selectedAxis) {
                                    "X" -> vm.xHomed
                                    "Y" -> vm.yHomed
                                    else -> vm.zHomed
                                }

                                Column(
                                    Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    // XYZ coordinate readout — shrinks to fit one line via TextAutoSize.
                                    BasicText(
                                        text = "X ${fmt1(vm.x)}   Y ${fmt1(vm.y)}   Z ${fmt1(vm.z)}",
                                        style = TextStyle(
                                            fontFamily = GeistMono,
                                            color = t.text,
                                            textAlign = TextAlign.Center,
                                        ),
                                        maxLines = 1,
                                        softWrap = false,
                                        autoSize = TextAutoSize.StepBased(
                                            minFontSize = fsSp(11f, t.fs).sp,
                                            maxFontSize = fsSp(22f, t.fs).sp,
                                            stepSize = 1.sp,
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                    )
                                    // Wrapping − / value / + increment selector (inline; does NOT touch IncrementPicker).
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedControl(
                                            label = "−",
                                            onClick = { stepIndex = (stepIndex - 1 + steps.size) % steps.size },
                                            modifier = Modifier.weight(1f),
                                            intent = Intent.Accent,
                                        )
                                        Text(
                                            text = fmtStep(activeStep),
                                            fontFamily = GeistMono,
                                            fontSize = fsSp(22f, t.fs).sp,
                                            color = t.text,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.weight(1f),
                                        )
                                        OutlinedControl(
                                            label = "+",
                                            onClick = { stepIndex = (stepIndex + 1) % steps.size },
                                            modifier = Modifier.weight(1f),
                                            intent = Intent.Accent,
                                        )
                                    }
                                    // Row A — jog ±-pair: drives the SELECTED axis by ±activeStep.
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedControl(
                                            label = "−",
                                            onClick = { onJog(selectedAxis, -activeStep) },
                                            modifier = Modifier.weight(1f),
                                            intent = Intent.Accent,
                                            enabled = selectedHomed,
                                        )
                                        OutlinedControl(
                                            label = "+",
                                            onClick = { onJog(selectedAxis, activeStep) },
                                            modifier = Modifier.weight(1f),
                                            intent = Intent.Accent,
                                            enabled = selectedHomed,
                                        )
                                    }
                                    // Row B — axis selector: picks which axis the ±-pair drives.
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        listOf(
                                            "X" to vm.xHomed,
                                            "Y" to vm.yHomed,
                                            "Z" to vm.zHomed,
                                        ).forEach { (axis, homed) ->
                                            AxisSelectChip(
                                                axis = axis,
                                                selected = axis == selectedAxis,
                                                homed = homed,
                                                uDp = grid.uDp,
                                                modifier = Modifier.weight(1f),
                                                onClick = { selectedAxis = axis },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        is MoveMode.Bookmark -> {
                            val bookmarkMode = mode as MoveMode.Bookmark
                            val loc = savedLocations.firstOrNull { it.name == bookmarkMode.name }
                            if (loc == null) {
                                FocusHint("Bookmark not found")
                            } else {
                                Column(Modifier.fillMaxSize()) {
                                    // TOP: centered destination coordinate readout — matches the
                                    // other focus modes' top readout (GeistMono ~22sp, centered).
                                    Text(
                                        text = "X ${fmt1(loc.x)}   Y ${fmt1(loc.y)}" +
                                            if (loc.z != null) "   Z ${fmt1(loc.z)}" else "",
                                        fontFamily = GeistMono,
                                        fontSize = fsSp(22f, t.fs).sp,
                                        color = t.text,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                    )
                                    // Bed map: current toolhead + bookmark destination + travel line.
                                    if (bed != null) {
                                        val cx = vm.x
                                        val cy = vm.y
                                        BedMapView(
                                            bed = bed,
                                            current = if (cx != null && cy != null) cx to cy else null,
                                            target = loc.x to loc.y,
                                            travel = (vm.x != null && vm.y != null),
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                        ) {
                                            FocusHint("Waiting for printer bounds…")
                                        }
                                    }
                                    // Move / Delete action row.
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedControl(
                                            label = "Move",
                                            onClick = { onMoveTo(loc.x, loc.y, loc.z) },
                                            modifier = Modifier.weight(1f),
                                            intent = Intent.Go,
                                        )
                                        OutlinedControl(
                                            label = "Delete",
                                            onClick = { deleteConfirm = loc.name },
                                            modifier = Modifier.weight(1f),
                                            intent = Intent.Danger,
                                        )
                                    }
                                }
                            }
                        }
                        MoveMode.SaveDialog -> {
                            // E6: Save Location form — the sanctioned save-name keyboard exception.
                            // State resets every time we enter this mode (keyed on mode).
                            var name by remember(mode) { mutableStateOf("") }
                            var includeZ by remember(mode) { mutableStateOf(true) }

                            Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // Name field: alphanumeric keyboard — sanctioned save-name exception.
                                TokenTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = "Name",
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardType = KeyboardType.Text,
                                )
                                // Include-Z checkbox row: tapping the row (label or box) toggles state.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { includeZ = !includeZ },
                                ) {
                                    Checkbox(
                                        checked = includeZ,
                                        onCheckedChange = { includeZ = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = t.accent,
                                            uncheckedColor = t.outline,
                                            checkmarkColor = t.surface,
                                        ),
                                    )
                                    Text(
                                        text = "Include Z height (Z = ${fmt1(vm.z)})",
                                        fontFamily = GeistMono,
                                        fontSize = fsSp(16f, t.fs).sp,
                                        color = t.text,
                                    )
                                }
                                // Save / Cancel button row.
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedControl(
                                        label = "Cancel",
                                        onClick = { mode = MoveMode.TouchMove },
                                        modifier = Modifier.weight(1f),
                                        intent = Intent.Accent,
                                    )
                                    OutlinedControl(
                                        label = "Save",
                                        onClick = {
                                            val sx = vm.x
                                            val sy = vm.y
                                            if (sx != null && sy != null && name.isNotBlank()) {
                                                onSaveLocation(
                                                    SavedLocation(
                                                        name = name.trim(),
                                                        x = sx,
                                                        y = sy,
                                                        z = if (includeZ) vm.z else null,
                                                    ),
                                                )
                                                mode = MoveMode.TouchMove
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        intent = Intent.Go,
                                        enabled = name.isNotBlank() && vm.x != null && vm.y != null,
                                    )
                                }
                            }
                        }
                        // All six MoveMode cases (TouchMove/XY/Z/Microstep/Bookmark/SaveDialog) handled — no else needed.
                    }
                }
            },
            field = {
                ListBlock(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp),
                ) {
                    // Homing rows (never "selected") — gated by availability. Home All moved to the foot bar.
                    if (avail.homeXY) {
                        item("home_xy") {
                            MoveRow("Home XY", DinghyIcons.HomeStateUnhomed, false, grid.uDp, t.accent) { onHomeXY() }
                        }
                    }
                    if (avail.homeZ) {
                        item("home_z") {
                            MoveRow("Home Z", DinghyIcons.HomeStateUnhomed, false, grid.uDp, t.accent) { onHomeAxis("Z") }
                        }
                    }
                    // Mode-selecting nav rows (selected = this row's mode == current mode).
                    if (avail.touchMove) {
                        item("touch_move") {
                            MoveRow("Touch Move", DinghyIcons.MoveTouch, mode == MoveMode.TouchMove, grid.uDp, t.accent) {
                                mode = MoveMode.TouchMove
                            }
                        }
                    }
                    if (avail.xy) {
                        item("xy") {
                            MoveRow("XY Position", DinghyIcons.MoveXY, mode == MoveMode.XY, grid.uDp, t.accent) {
                                mode = MoveMode.XY
                            }
                        }
                    }
                    if (avail.z) {
                        item("z") {
                            MoveRow("Z Position", DinghyIcons.MoveZ, mode == MoveMode.Z, grid.uDp, t.accent) {
                                mode = MoveMode.Z
                            }
                        }
                    }
                    if (avail.microstep) {
                        item("microstep") {
                            MoveRow("Microstep", DinghyIcons.FineTune, mode == MoveMode.Microstep, grid.uDp, t.accent) {
                                mode = MoveMode.Microstep
                            }
                        }
                    }
                    // Saved-location rows.
                    items(savedLocations, key = { "bookmark_${it.name}" }) { loc ->
                        MoveRow(
                            loc.name,
                            DinghyIcons.SavedLocation,
                            mode == MoveMode.Bookmark(loc.name),
                            grid.uDp,
                            t.accent,
                        ) { mode = MoveMode.Bookmark(loc.name) }
                    }
                }

                FootButtonBar(uDp = grid.uDp) {
                    // Back: sub-mode → Touch Move; Touch Move → onBack (accent, FIRST — R5/R8).
                    OutlinedControl(
                        label = "",
                        onClick = { if (mode == MoveMode.TouchMove) onBack() else mode = MoveMode.TouchMove },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent,
                        icon = DinghyIcons.Back,
                        contentDescription = "Back",
                    )
                    // Disable Motors (danger) — drops stepper hold.
                    OutlinedControl(
                        label = "",
                        onClick = { onDisableSteppers() },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Danger,
                        icon = DinghyIcons.MoveDisableMotors,
                        contentDescription = "Disable motors",
                    )
                    // Home All (go) — the expected homing action.
                    OutlinedControl(
                        label = "",
                        onClick = { onHomeAll() },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Go,
                        icon = DinghyIcons.MoveHomeAll,
                        contentDescription = "Home all",
                    )
                    // Save Location (accent) — only when all XYZ known.
                    OutlinedControl(
                        label = "",
                        onClick = { mode = MoveMode.SaveDialog },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent,
                        icon = DinghyIcons.SaveLocation,
                        enabled = avail.saveLocation,
                        contentDescription = "Save location",
                    )
                }
            },
        )

        // Delete-confirm overlays ON TOP of the scaffold (keeps the docked e-stop composed underneath).
        val pendingDelete = deleteConfirm
        if (pendingDelete != null) {
            ConfirmGuard(
                title = "Delete \"$pendingDelete\"?",
                message = "Remove this saved location.",
                confirmLabel = "Delete",
                destructive = true,
                onConfirm = {
                    onDeleteLocation(pendingDelete)
                    deleteConfirm = null
                    mode = MoveMode.TouchMove
                },
                onCancel = { deleteConfirm = null },
            )
        }
    }
}

/** Header title + icon per the current [MoveMode] (header law). */
private fun moveModeHeader(mode: MoveMode): Pair<String, DinghyIcon> = when (mode) {
    MoveMode.TouchMove -> "Touch Move" to DinghyIcons.MoveTouch
    MoveMode.XY -> "XY Position" to DinghyIcons.MoveXY
    MoveMode.Z -> "Z Position" to DinghyIcons.MoveZ
    MoveMode.Microstep -> "Microstep" to DinghyIcons.FineTune
    is MoveMode.Bookmark -> mode.name to DinghyIcons.SavedLocation
    MoveMode.SaveDialog -> "Save Location" to DinghyIcons.SaveLocation
}

/** A single Field action row — canonical [ListRow] with a leading icon and a label. */
@Composable
private fun MoveRow(
    label: String,
    icon: DinghyIcon,
    selected: Boolean,
    uDp: androidx.compose.ui.unit.Dp,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    ListRow(
        selected = selected,
        onClick = onClick,
        uDp = uDp,
        leadingContent = { ListRowIcon(icon = icon, uDp = uDp, tint = tint) },
    ) {
        ListRowLabel(label)
    }
}

/**
 * A small selectable axis chip for the Microstep axis selector (Row B). The SELECTED axis reads as
 * a *selection state* — a focus FILL mirroring the [ListRow] convention (`accentSoft` background +
 * 2dp `accentLine` border) rather than an action button. Unselected axes are outlined/transparent
 * (`outline`, 1.5dp). Unhomed axes are dimmed (`text2`) and not selectable ([homed] gates the tap).
 */
@Composable
private fun AxisSelectChip(
    axis: String,
    selected: Boolean,
    homed: Boolean,
    uDp: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val bg = if (selected) t.accentSoft else Color.Transparent
    val borderColor = if (selected) t.accentLine else t.outline
    val borderWidth = if (selected) 2.dp else 1.5.dp
    Box(
        modifier = modifier
            .heightIn(min = uDp)
            .clip(shape)
            .background(bg)
            .border(BorderStroke(borderWidth, borderColor), shape)
            .clickable(enabled = homed, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = axis,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(20f, t.fs).sp,
            color = if (homed) t.text else t.text2,
        )
    }
}

/** One-decimal mm formatting for the TouchMove X/Y readout. Null values render as "—". */
private fun fmt1(v: Double?): String =
    if (v == null) "—" else String.format(java.util.Locale.US, "%.1f", v)

/** Formats a microstep increment value as a signed label (e.g. 0.1 → "±0.1", 10.0 → "±10"). */
private fun fmtStep(v: Double): String =
    "±" + v.toBigDecimal().stripTrailingZeros().toPlainString()

/** A centered Focus-body hint/placeholder string (homing hint + sub-mode hints). */
@Composable
private fun FocusHint(text: String, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = t.text2,
            fontFamily = Geist,
            fontSize = fsSp(18f, t.fs).sp,
            textAlign = TextAlign.Center,
        )
    }
}
