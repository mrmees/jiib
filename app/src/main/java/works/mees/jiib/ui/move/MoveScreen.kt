package works.mees.jiib.ui.move

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.floor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.jiib.R
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.CommandSpec
import works.mees.jiib.command.GatingMode
import works.mees.jiib.command.GatingState
import works.mees.jiib.command.HomeAxisArgs
import works.mees.jiib.command.JogArgs
import works.mees.jiib.command.MoveToArgs
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.ConfirmGuard
import works.mees.jiib.designsystem.components.AxisOption
import works.mees.jiib.designsystem.components.ConfirmOnBack
import works.mees.jiib.designsystem.components.AxisSelectorRow
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.HardLockStatusCard
import works.mees.jiib.designsystem.components.UnknownStatusCard
import works.mees.jiib.designsystem.layout.FocusInset
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.ListRowIcon
import works.mees.jiib.designsystem.components.ListRowLabel
import works.mees.jiib.designsystem.components.Scrubber
import works.mees.jiib.designsystem.components.ScrubberOrientation
import works.mees.jiib.designsystem.components.StepperRow
import works.mees.jiib.designsystem.components.ToggleRow
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.control.OutlinedControl
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.icons.JiibIconView
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.LocalUnitDp
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.controlHeight
import works.mees.jiib.designsystem.layout.gapS
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.theme.fsSp
import works.mees.jiib.ui.increments.IncrementControls
import works.mees.jiib.ui.screen.TokenTextField

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

    /** Read-only live view of each configured endstop's trigger state. */
    data object Endstops : MoveMode
}

/**
 * The Move Hub — the keystone of the Move redesign (Task D3). Mirrors the [FineTuneScreen] Hub
 * archetype: a [ScreenScaffold] whose Field is a translucent [ListRow] action-list and whose Focus
 * is a [FocusFrame] that swaps its content (and header identity) by the selected [MoveMode].
 *
 * This LIVE entry collects the standard Move inputs (dispatcher / vm / printerState / inFlight /
 * isPrinting) plus [AppContainer.savedLocations], then delegates rendering to the stateless
 * [MoveHubContent]. (It replaced the retired command-centric jog-pad screen, deleted in the
 * control-baseline audit Phase 9 — the AppShell route now points here.)
 *
 * @param container service-locator (live `printerState`, session dispatcher, saved locations).
 * @param holder    toolkit-agnostic [MoveHolder] (live X/Y/Z + per-axis homed gating + bounds/feed).
 * @param onBack    invoked by the Back foot button — leaves the hub from ANY sub-mode.
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
    val gating by container.gatingState.collectAsStateWithLifecycle(initialValue = GatingState.Idle)
    val vm by holder.vm.collectAsStateWithLifecycle()
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    val savedLocations by container.savedLocations.collectAsStateWithLifecycle()
    val incrementLists by container.activeIncrementLists.collectAsStateWithLifecycle(emptyMap())
    val microstepSteps: ImmutableList<Double> = remember(incrementLists) {
        (incrementLists["move_microstep"]
            ?: IncrementControls.defaultValueMap().getValue("move_microstep")).toImmutableList()
    }

    // Confirm-on-back: armed ONLY when Move owns the HardLock (homing keys start with "home").
    // Global GatingState.Locked may come from other screens; filter to Move-owned keys so a
    // non-Move lock doesn't trigger the guard here.
    val lockedKey = (gating as? GatingState.Locked)?.key
    val locked = lockedKey?.startsWith("home") == true

    // One dispatch helper — every action funnels through the registry (no raw rpc).
    // SoftBusy commands are queueable — the dispatcher handles debounce/queuing; skip the inFlight
    // block for them so rapid jog taps accumulate rather than being swallowed here.
    fun <P> dispatchCommand(command: CommandSpec<P>, args: P) {
        if (command.gating != GatingMode.SoftBusy && command.dispatchKey(args) in inFlight) return
        dispatcher?.dispatch(command, args)
    }

    ConfirmOnBack(enabled = locked, onBack = onBack) { requestBack ->
        MoveHubContent(
            vm = vm,
            savedLocations = savedLocations,
            isPrinting = isPrinting,
            gating = gating,
            onAcknowledgeUnknown = { dispatcher?.acknowledgeUnresolved() },
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
            queryEndstops = {
                val d = dispatcher ?: error("no active session")
                parseEndstops(d.query(CommandRegistry.queryEndstops, Unit))
            },
            onBack = requestBack,
            microstepSteps = microstepSteps,
            modifier = modifier,
        )
    }
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
    gating: GatingState = GatingState.Idle,
    onAcknowledgeUnknown: () -> Unit = {},
    onMoveTo: (Double?, Double?, Double?) -> Unit,
    onJog: (String, Double) -> Unit,
    onHomeAll: () -> Unit,
    onDisableSteppers: () -> Unit,
    onHomeXY: () -> Unit,
    onHomeAxis: (String) -> Unit,
    onEmergencyStop: () -> Unit,
    onSaveLocation: (SavedLocation) -> Unit,
    onDeleteLocation: (String) -> Unit,
    queryEndstops: suspend () -> List<EndstopStatus>,
    onBack: () -> Unit,
    microstepSteps: ImmutableList<Double> =
        IncrementControls.defaultValueMap().getValue("move_microstep")
            .toImmutableList(),
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

    // When the printer un-homes, drop out of any homed-only transient Focus (Bookmark/SaveDialog)
    // so we don't strand the user on a Focus whose menu row just disappeared (see Task 1 helper).
    LaunchedEffect(vm.allHomed) {
        mode = moveModeAfterHomedChange(mode, vm.allHomed)
    }

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

        // Show "Moving…" only when a Move-owned SoftBusy key is active. gatingState is global so we
        // must filter to the keys this screen owns — Move owns move_to, jog_*, override_jog_*.
        // TODO(owner ICON LAW): confirm final motion-busy glyph — MoveTouch is the placeholder.
        val moveBusy = (gating as? GatingState.Busy)?.key?.let {
            it == "move_to" || it.startsWith("jog_") || it.startsWith("override_jog_")
        } == true

        // HardLock morph: Move owns all home_* keys. While locked, the Focus short-circuits to a
        // centered HardLockStatusCard and the Field nav rows are dimmed/disabled. gatingState is
        // global so filter to Move-owned HardLock keys (all start with "home").
        val lockedKey = (gating as? GatingState.Locked)?.key
        val homingLabelRes = lockedKey?.let { if (it.startsWith("home")) R.string.gating_homing else null }
        val isHoming = homingLabelRes != null

        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = headerTitle,
                    icon = headerIcon,
                    uDp = grid.uDp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    isPrinting = isPrinting,
                    safetyActive = gating !is GatingState.Idle,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                    contentInset = FocusInset / 2, // match FineTuneScreen's focus rhythm (8dp, not 16dp)
                    trailingStatusIcon = if (moveBusy) JiibIcons.MoveTouch else null,
                    trailingStatusContentDescription = if (moveBusy) stringResource(R.string.gating_moving) else null,
                ) {
                    // Unknown Focus morph (precedence: Unknown > Locked > normal content): when the
                    // link or firmware can't confirm the HardLock completed, replace the Focus body
                    // with a "Still running" card that requires explicit dismissal.
                    if (gating is GatingState.Unknown) {
                        UnknownStatusCard(grid.uDp, onDismiss = onAcknowledgeUnknown, Modifier.fillMaxSize())
                        return@FocusFrame
                    }
                    // HardLock Focus morph: while homing, replace the entire Focus body with a
                    // centered status card. E-stop stays live in the FocusFrame header (above).
                    if (homingLabelRes != null) {
                        HardLockStatusCard(stringResource(homingLabelRes), grid.uDp, Modifier.fillMaxSize())
                        return@FocusFrame
                    }

                    when (mode) {
                        MoveMode.TouchMove -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // X / Y coordinate readout — staged while gesturing, else current.
                                // Shown at the TOP so the position is always visible above the bed map.
                                val shownX = staged?.first ?: vm.x
                                val shownY = staged?.second ?: vm.y
                                Text(
                                    text = "X ${fmt1(shownX)}   Y ${fmt1(shownY)}",
                                    style = JiibType.statValue.toTextStyle(t),
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
                                    // travelPending = bed-map travel VISUALIZATION only. Busy authority is gatingState (B1).
                                    // TODO(gating): consider removing travelPending after on-device confirms fenced inFlight
                                    // matches real toolhead arrivals (Codex Finding 8 — keep until that evidence exists).
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
                                    style = JiibType.caption.toTextStyle(t),
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
                                        style = JiibType.statValue.toTextStyle(t),
                                        color = t.text,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                    )
                                    // BELOW: bed map + sliders (both bare — track only, no steppers).
                                    // The BED SQUARE itself is horizontally CENTERED in the focus; the
                                    // Y scrubber lives in the right padding (it no longer hugs the plate),
                                    // but keeps the plate's height so its thumb tracks the marker vertically.
                                    val bedAspect = (bed.width / bed.height).toFloat()
                                    val control = minOf(grid.uDp, 74.dp) // scrubber track thickness
                                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                                        // Reserve 2*control of width (symmetric padding so the Y scrubber
                                        // fits in the right padding without overlapping the centered plate)
                                        // and control of height (for the X scrubber below).
                                        val availW = (maxWidth - control * 2).coerceAtLeast(1.dp)
                                        val availH = (maxHeight - control).coerceAtLeast(1.dp)
                                        val plate = if (availW / availH > bedAspect) {
                                            val h = availH
                                            DpSize(h * bedAspect, h)
                                        } else {
                                            val w = availW
                                            DpSize(w, w / bedAspect)
                                        }
                                        Column(
                                            Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Box(Modifier.fillMaxWidth().height(plate.height)) {
                                                // Bed square CENTERED in the full width.
                                                BedMapView(
                                                    bed = bed,
                                                    current = currentPair,
                                                    target = workingX.toDouble() to workingY.toDouble(),
                                                    travel = false,
                                                    modifier = Modifier.size(plate).align(Alignment.Center),
                                                )
                                                // Y scrubber: same height as the plate, parked at the right
                                                // edge (in the right padding — no longer hugs the plate).
                                                Box(
                                                    Modifier.width(control).height(plate.height)
                                                        .align(Alignment.CenterEnd),
                                                ) {
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
                                            // X scrubber: same width as the plate, centered under it
                                            // (the Column's CenterHorizontally keeps it aligned with the plate).
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
                        MoveMode.Z -> {
                            val zMax = vm.axisMax?.getOrNull(2)?.toFloat()
                            if (zMax == null) {
                                FocusHint("Waiting for printer bounds…")
                            } else {
                                var workingZ by remember(mode) {
                                    mutableFloatStateOf((vm.z?.toFloat() ?: 0f).coerceIn(0f, zMax))
                                }
                                // Five columns: [fine labels] [fine slider] [Z value] [full slider]
                                // [full labels]. Range labels flank each slider as their own columns
                                // (owner 2026-06-17). Center Z value matches the X/Y coordinate text.
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                ) {
                                    // Col 1 — Fine range labels: 50 (top) / 0 (bottom).
                                    ZRangeLabels(top = "50", bottom = "0")
                                    // Col 2 — Fine slider: 0–50 mm @ 0.1 mm.
                                    ZScrubberColumn(
                                        name = "Fine",
                                        value = workingZ,
                                        range = 0f..50f,
                                        step = 0.1f,
                                        uDp = grid.uDp,
                                        modifier = Modifier.weight(1f),
                                        onValueChange = { workingZ = it },
                                        onSettle = { v ->
                                            workingZ = v
                                            onMoveTo(null, null, workingZ.toDouble())
                                        },
                                    )
                                    // Col 3 — Z value, centered; matches the X/Y coordinate readout
                                    // (statValue, 26sp) on the Touch Move / XY focuses (owner 2026-06-17).
                                    Box(
                                        Modifier.fillMaxHeight().padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.2f", workingZ) + "mm",
                                            style = JiibType.statValue.toTextStyle(t),
                                            color = t.text,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                    // Col 4 — Full slider: 0–Zmax @ 1 mm.
                                    ZScrubberColumn(
                                        name = "Full",
                                        value = workingZ,
                                        range = 0f..zMax,
                                        step = 1f,
                                        uDp = grid.uDp,
                                        modifier = Modifier.weight(1f),
                                        onValueChange = { workingZ = it },
                                        onSettle = { v ->
                                            workingZ = v
                                            onMoveTo(null, null, workingZ.toDouble())
                                        },
                                    )
                                    // Col 5 — Full range labels: floor(Zmax) (top) / 0 (bottom).
                                    ZRangeLabels(top = floor(zMax).toInt().toString(), bottom = "0")
                                }
                            }
                        }
                        MoveMode.Microstep -> {
                            val anyHomed = vm.xHomed || vm.yHomed || vm.zHomed
                            if (!anyHomed) {
                                FocusHint("Home an axis to micro-step")
                            } else {
                                val steps: ImmutableList<Double> = microstepSteps
                                var stepIndex by remember(mode) {
                                    mutableStateOf(steps.indexOf(0.1).coerceAtLeast(0))
                                }
                                val activeStep = steps[stepIndex.coerceIn(0, steps.lastIndex)]

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

                                // BOTTOM-DOCK (master-list §f#7): the XYZ coordinate readout is the
                                // weighted body (absorbs the slack above via weight(1f)); the three
                                // control rows (step-size / jog / axis-select) are pinned to the
                                // BOTTOM of the Focus content. Was top-aligned spacedBy(8.dp).
                                Column(
                                    Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    // XYZ coordinate readout — weighted body, centered, shrinks to fit
                                    // one line via TextAutoSize.
                                    Box(
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        BasicText(
                                            text = "X ${fmt1(vm.x)}   Y ${fmt1(vm.y)}   Z ${fmt1(vm.z)}",
                                            style = JiibType.focusHero.toTextStyle(t).copy(
                                                color = t.text,
                                                textAlign = TextAlign.Center,
                                            ),
                                            maxLines = 1,
                                            softWrap = false,
                                            autoSize = TextAutoSize.StepBased(
                                                minFontSize = fsSp(15f, t.fs).sp,
                                                maxFontSize = fsSp(40f, t.fs).sp,
                                                stepSize = 1.sp,
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    // Step-size cycler — [−][value][+] via StepperRow (kills the literal
                                    // −/+; Decrease/Increase icon tokens). 7 magnitudes don't fit as
                                    // tiles, so it stays a cycler with the step value in the center slot.
                                    StepperRow(
                                        onDecrement = { stepIndex = (stepIndex - 1 + steps.size) % steps.size },
                                        onIncrement = { stepIndex = (stepIndex + 1) % steps.size },
                                        uDp = grid.uDp,
                                        intent = Intent.Accent,
                                        decrementIcon = JiibIcons.StatMinus1,
                                        incrementIcon = JiibIcons.StatPlus1,
                                        center = {
                                            Text(
                                                text = fmtStep(activeStep),
                                                style = JiibType.dataInline.toTextStyle(t),
                                                color = t.text,
                                                textAlign = TextAlign.Center,
                                            )
                                        },
                                    )
                                    // Jog ±-pair: drives the SELECTED axis by ±activeStep. Intent.Go
                                    // (R19 — motion is Move's purpose); bare ±-pair (no center).
                                    StepperRow(
                                        onDecrement = { onJog(selectedAxis, -activeStep) },
                                        onIncrement = { onJog(selectedAxis, activeStep) },
                                        uDp = grid.uDp,
                                        intent = Intent.Go,
                                        enabled = selectedHomed,
                                    )
                                    // Axis selector: picks which axis the ±-pair drives — SelectorRow
                                    // (X/Y/Z text-label tiles; selected = Accent + accentSoft fill).
                                    AxisSelectorRow(
                                        options = listOf(
                                            AxisOption("X", isSelected = selectedAxis == "X", enabled = vm.xHomed),
                                            AxisOption("Y", isSelected = selectedAxis == "Y", enabled = vm.yHomed),
                                            AxisOption("Z", isSelected = selectedAxis == "Z", enabled = vm.zHomed),
                                        ),
                                        onSelect = { selectedAxis = it },
                                        uDp = grid.uDp,
                                    )
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
                                    // TOP: centered destination coordinate readout — shrink-to-fit
                                    // via TextAutoSize so the full X/Y/Z line fits on narrow screens.
                                    BasicText(
                                        text = "X ${fmt1(loc.x)}   Y ${fmt1(loc.y)}" +
                                            if (loc.z != null) "   Z ${fmt1(loc.z)}" else "",
                                        style = JiibType.focusHero.toTextStyle(t).copy(
                                            color = t.text,
                                            textAlign = TextAlign.Center,
                                        ),
                                        maxLines = 1,
                                        softWrap = false,
                                        autoSize = TextAutoSize.StepBased(
                                            minFontSize = fsSp(15f, t.fs).sp,
                                            maxFontSize = fsSp(40f, t.fs).sp,
                                            stepSize = 1.sp,
                                        ),
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
                                    // Breathing room so the bed map doesn't crowd the action row.
                                    Spacer(Modifier.height(12.dp))
                                    // Move / Delete action row — canonical 1U button pattern.
                                    CompositionLocalProvider(LocalUnitDp provides grid.uDp) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .controlHeight(grid.uDp),
                                            horizontalArrangement = Arrangement.spacedBy(gapS(grid.uDp)),
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
                                // Include-Z toggle: the canonical full-width 1U switch ToggleRow
                                // (control baseline audit, Phase 5 — kills the Material MUI tick-box
                                // rogue). includeZ stays INTENTIONALLY ephemeral (reset per dialog
                                // open) — render-only swap, no persistence.
                                ToggleRow(
                                    label = stringResource(R.string.move_include_z, fmt1(vm.z)),
                                    checked = includeZ,
                                    onToggle = { includeZ = it },
                                    uDp = grid.uDp,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                // BOTTOM-DOCK (master-list §f#7): weighted spacer pushes the Cancel/Save
                                // button group to the BOTTOM of the Focus; the name field + include-Z
                                // toggle form the body above. (Intents/labels unchanged — that's a later
                                // ActionButton sweep; this is ONLY the vertical docking.)
                                Spacer(Modifier.weight(1f))
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
                        MoveMode.Endstops -> {
                            // null = no result yet (Querying). errored = last poll threw before any data.
                            var endstops by remember { mutableStateOf<List<EndstopStatus>?>(null) }
                            var errored by remember { mutableStateOf(false) }
                            // rememberUpdatedState so the long-lived poll loop always calls the LATEST
                            // lambda (which reads the live dispatcher) — guards against a stale capture
                            // never recovering after reconnect.
                            val query by rememberUpdatedState(queryEndstops)
                            // Lifecycle-scoped poll (mirrors TemperatureScreen.kt): the LaunchedEffect
                            // cancels when this branch leaves composition (mode change), and
                            // repeatOnLifecycle(STARTED) suspends the loop while backgrounded.
                            val lifecycleOwner = LocalLifecycleOwner.current
                            LaunchedEffect(lifecycleOwner) {
                                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                    while (true) {
                                        try {
                                            endstops = query()
                                            errored = false
                                        } catch (e: CancellationException) {
                                            throw e // never swallow structured cancellation
                                        } catch (e: Throwable) {
                                            if (endstops == null) errored = true
                                        }
                                        delay(500)
                                    }
                                }
                            }

                            val current = endstops
                            when {
                                current != null -> {
                                    // Note + rows as ONE vertically-centered block: the cadence note is a
                                    // line directly above the switch statuses (not pinned to the top).
                                    Column(
                                        Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                                    ) {
                                        // Polling cadence note — the poll loop above is literally delay(500).
                                        Text(
                                            text = "Polls Every 500ms",
                                            style = JiibType.caption.toTextStyle(t),
                                            color = t.text3,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        current.forEach { es -> EndstopRow(es, grid.uDp) }
                                    }
                                }
                                errored -> FocusCenteredHint("Endstops unavailable")
                                else -> FocusCenteredHint("Querying…")
                            }
                        }
                        // All seven MoveMode cases (TouchMove/XY/Z/Microstep/Bookmark/SaveDialog/Endstops) handled — no else needed.
                    }
                }
            },
            field = {
                ListBlock(
                    modifier = Modifier
                        .weight(1f),
                ) {
                    // Homing rows (never "selected") — gated by availability. Home All moved to the foot bar.
                    if (avail.homeXY) {
                        item("home_xy") {
                            MoveRow("Home XY", JiibIcons.HomeStateUnhomed, false, grid.uDp, t.accent, enabled = !isHoming) { onHomeXY() }
                        }
                    }
                    if (avail.homeZ) {
                        item("home_z") {
                            MoveRow("Home Z", JiibIcons.HomeStateUnhomed, false, grid.uDp, t.accent, enabled = !isHoming) { onHomeAxis("Z") }
                        }
                    }
                    // Mode-selecting nav rows (selected = this row's mode == current mode).
                    if (avail.touchMove) {
                        item("touch_move") {
                            MoveRow("Touch Move", JiibIcons.MoveTouch, mode == MoveMode.TouchMove, grid.uDp, t.accent, enabled = !isHoming) {
                                mode = MoveMode.TouchMove
                            }
                        }
                    }
                    if (avail.xy) {
                        item("xy") {
                            MoveRow("XY Position", JiibIcons.MoveXY, mode == MoveMode.XY, grid.uDp, t.accent, enabled = !isHoming) {
                                mode = MoveMode.XY
                            }
                        }
                    }
                    if (avail.z) {
                        item("z") {
                            MoveRow("Z Position", JiibIcons.MoveZ, mode == MoveMode.Z, grid.uDp, t.accent, enabled = !isHoming) {
                                mode = MoveMode.Z
                            }
                        }
                    }
                    if (avail.microstep) {
                        item("microstep") {
                            MoveRow("Microstep", JiibIcons.FineTune, mode == MoveMode.Microstep, grid.uDp, t.accent, enabled = !isHoming) {
                                mode = MoveMode.Microstep
                            }
                        }
                    }
                    // Bookmark group: only meaningful once all axes are homed (a bookmark Move needs a
                    // known coordinate frame). Hidden entirely otherwise. Add Bookmark leads the group.
                    if (vm.allHomed) {
                        item("add_bookmark") {
                            MoveRow("Add Bookmark", JiibIcons.SaveLocation, mode == MoveMode.SaveDialog, grid.uDp, t.accent, enabled = !isHoming) {
                                mode = MoveMode.SaveDialog
                            }
                        }
                        items(savedLocations, key = { "bookmark_${it.name}" }) { loc ->
                            MoveRow(
                                loc.name,
                                JiibIcons.SavedLocation,
                                mode == MoveMode.Bookmark(loc.name),
                                grid.uDp,
                                t.accent,
                                enabled = !isHoming,
                            ) { mode = MoveMode.Bookmark(loc.name) }
                        }
                    }
                    // Endstops view — always available; last row in the list (owner: after bookmarks).
                    item("endstops") {
                        MoveRow(
                            "Endstops",
                            JiibIcons.CenterFocusStrong,
                            mode == MoveMode.Endstops,
                            grid.uDp,
                            t.accent,
                            enabled = !isHoming,
                        ) { mode = MoveMode.Endstops }
                    }
                    // Disable Motors — destructive utility, pinned at the bottom. Fires immediately on
                    // tap (owner: one tap, no confirm); does NOT swap the Focus. Red icon (t.stop) reads
                    // destructive on a translucent row. Un-homes the printer → the LaunchedEffect above
                    // drops any transient Focus and the bookmark group + motion rows collapse.
                    // Disabled while homing (same as all nav rows) to prevent mid-homing interruption.
                    item("disable_motors") {
                        MoveRow("Disable Motors", JiibIcons.MoveDisableMotors, false, grid.uDp, t.stop, enabled = !isHoming) {
                            onDisableSteppers()
                        }
                    }
                }

                FootButtonBar(
                    uDp = grid.uDp,
                    actions = listOf(
                        FootAction(
                            label = stringResource(R.string.common_back),
                            onClick = onBack,
                            intent = Intent.Accent,
                            icon = JiibIcons.Back,
                            contentDescription = "Back",
                        ),
                        // Home All (go) — the expected homing action.
                        FootAction(
                            label = stringResource(R.string.move_home_all),
                            onClick = { onHomeAll() },
                            intent = Intent.Go,
                            icon = JiibIcons.MoveHomeAll,
                            contentDescription = "Home all",
                        ),
                    ),
                )
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
private fun moveModeHeader(mode: MoveMode): Pair<String, JiibIcon> = when (mode) {
    MoveMode.TouchMove -> "Touch Move" to JiibIcons.MoveTouch
    MoveMode.XY -> "XY Position" to JiibIcons.MoveXY
    MoveMode.Z -> "Z Position" to JiibIcons.MoveZ
    MoveMode.Microstep -> "Microstep" to JiibIcons.FineTune
    is MoveMode.Bookmark -> mode.name to JiibIcons.SavedLocation
    MoveMode.SaveDialog -> "Save Location" to JiibIcons.SaveLocation
    MoveMode.Endstops -> "Endstops" to JiibIcons.CenterFocusStrong
}

/**
 * One vertical Z scrubber column (Fine 0–50 / Full 0–Zmax). Bare — the endpoint range labels now
 * live in their own flanking [ZRangeLabels] columns (owner 2026-06-17, five-column Z layout). Both
 * sliders are `weight(1f)`-equal via [modifier].
 */
@Composable
private fun ZScrubberColumn(
    name: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    uDp: androidx.compose.ui.unit.Dp,
    onValueChange: (Float) -> Unit,
    onSettle: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Scrubber(
            name = name,
            value = value,
            range = range,
            step = step,
            uDp = uDp,
            unit = "mm",
            orientation = ScrubberOrientation.Vertical,
            onValueChange = onValueChange,
            onSettle = onSettle,
        )
    }
}

/**
 * A range-label column flanking a Z scrubber: [top] pushed to the top of the height, [bottom] to the
 * bottom. Sized at the Z value's CURRENT size (dataInline, 20sp — owner ruling 2026-06-17; NOT the
 * 26sp the center readout grows to). Muted via `t.text2`.
 */
@Composable
private fun ZRangeLabels(top: String, bottom: String) {
    val t = LocalTokens.current
    Column(
        modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = top, style = JiibType.dataInline.toTextStyle(t), color = t.text2)
        Spacer(Modifier.weight(1f))
        Text(text = bottom, style = JiibType.dataInline.toTextStyle(t), color = t.text2)
    }
}

/**
 * A single Field action row — canonical [ListRow] with a leading icon and a label.
 *
 * @param enabled when false the row is visually dimmed (alpha 0.38) and its click is a no-op.
 *                Used to disable nav rows while a HardLock op (homing) is running without
 *                touching the foot Back button or the FocusFrame e-stop.
 */
@Composable
private fun MoveRow(
    label: String,
    icon: JiibIcon,
    selected: Boolean,
    uDp: androidx.compose.ui.unit.Dp,
    tint: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ListRow(
        selected = selected,
        onClick = if (enabled) onClick else ({}),
        uDp = uDp,
        modifier = if (!enabled) Modifier.alpha(0.38f) else Modifier,
        leadingContent = { ListRowIcon(icon = icon, uDp = uDp, tint = tint) },
    ) {
        ListRowLabel(label)
    }
}

/** One endstop status row: state glyph + axis label + OPEN/TRIGGERED readout.
 *  Color + shape both encode state (crop_free/center_focus_strong) for high-contrast/colorblind palettes. */
@Composable
private fun EndstopRow(status: EndstopStatus, uDp: Dp) {
    val t = LocalTokens.current
    val color = if (status.triggered) t.accent else t.text3
    val glyph = if (status.triggered) JiibIcons.CenterFocusStrong else JiibIcons.CropFree
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = uDp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JiibIconView(
            icon = glyph,
            tint = color,
            sizeDp = uDp * 0.6f,
            contentDescription = null,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = endstopLabel(status.name),
            style = JiibType.listLabel.toTextStyle(t),
            color = t.text,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (status.triggered) "TRIGGERED" else "OPEN",
            style = JiibType.statValue.toTextStyle(t),
            color = color,
        )
    }
}

@Composable
private fun FocusCenteredHint(text: String) {
    val t = LocalTokens.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = JiibType.body.toTextStyle(t), color = t.text3)
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
            style = JiibType.body.toTextStyle(t),
            textAlign = TextAlign.Center,
        )
    }
}
