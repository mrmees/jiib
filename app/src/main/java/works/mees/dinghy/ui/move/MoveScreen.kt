package works.mees.dinghy.ui.move

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.CommandSpec
import works.mees.dinghy.command.HomeAxisArgs
import works.mees.dinghy.command.JogArgs
import works.mees.dinghy.command.MoveToArgs
import works.mees.dinghy.command.dispatch
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

/**
 * The current Move-Hub sub-mode (Task D3). The Field action-list selects a mode; the Focus swaps
 * content (and its header title/icon) accordingly. The six sub-mode BODIES are placeholders in this
 * task — tasks E1–E6 fill them. [Bookmark] carries the tapped saved-location name; [SaveDialog] is
 * the save-name flow.
 */
sealed interface MoveMode {
    data object Overview : MoveMode
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
 * @param onBack    invoked by the Back foot button from the Overview mode.
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
 * ## Sub-mode bodies are PLACEHOLDERS (Task D3)
 * Only [MoveMode.Overview] renders a real Focus body (the read-only [BedMapView], or a homing hint
 * when bounds are unknown). Every other mode renders "<name> — coming soon" — tasks E1–E6 replace
 * these with the real interactions. Do NOT build sub-mode behavior here.
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
    onHomeXY: () -> Unit,
    onHomeAxis: (String) -> Unit,
    onEmergencyStop: () -> Unit,
    onSaveLocation: (SavedLocation) -> Unit,
    onDeleteLocation: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    var mode by remember { mutableStateOf<MoveMode>(MoveMode.Overview) }

    // Staged target (finger-live preview) and in-flight travel flag.
    // Keyed on mode so entering a new sub-mode always starts clean.
    var staged by remember(mode) { mutableStateOf<Pair<Double, Double>?>(null) }
    var pending by remember(mode) { mutableStateOf(false) }

    val avail = moveRowAvailability(vm.xHomed, vm.yHomed, vm.zHomed)

    // Bed extent from toolhead.axis_minimum/axis_maximum X/Y (indices 0,1). Null until first
    // snapshot (or if either bounds list is too short) — the Overview Focus shows a homing hint then.
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
                        MoveMode.Overview -> {
                            if (bed != null) {
                                // Read-only overhead bed map (no gesture callbacks — E1+ add taps).
                                val cx = vm.x
                                val cy = vm.y
                                BedMapView(
                                    bed = bed,
                                    current = if (cx != null && cy != null) cx to cy else null,
                                    target = null,
                                    travel = false,
                                    modifier = Modifier.fillMaxSize().padding(12.dp),
                                )
                            } else {
                                FocusHint("Home the printer to begin")
                            }
                        }
                        MoveMode.TouchMove -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Instruction line.
                                Text(
                                    text = "Tap to move, hold to refine",
                                    color = t.text2,
                                    fontFamily = GeistMono,
                                    fontSize = fsSp(15f, t.fs).sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                )
                                // Bed map or waiting-for-bounds hint.
                                if (bed != null) {
                                    val cx = vm.x
                                    val cy = vm.y
                                    BedMapView(
                                        bed = bed,
                                        current = if (cx != null && cy != null) cx to cy else null,
                                        target = staged,
                                        travel = pending,
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        onTapBed = { x, y -> staged = x to y },
                                        onDragBed = { x, y -> staged = x to y },
                                        onDragEnd = {
                                            staged?.let { (x, y) ->
                                                onMoveTo(x, y, null)
                                                pending = true
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
                                // X / Y coordinate readout — staged while gesturing, else current.
                                val shownX = staged?.first ?: vm.x
                                val shownY = staged?.second ?: vm.y
                                Text(
                                    text = "X ${fmt1(shownX)}   Y ${fmt1(shownY)}",
                                    fontFamily = GeistMono,
                                    fontSize = fsSp(18f, t.fs).sp,
                                    color = t.text,
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

                                Row(modifier = Modifier.fillMaxSize()) {
                                    // LEFT: bed map (read-only) stacked above the X scrubber.
                                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        BedMapView(
                                            bed = bed,
                                            current = currentPair,
                                            target = workingX.toDouble() to workingY.toDouble(),
                                            travel = false,
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                        )
                                        Scrubber(
                                            name = "X",
                                            value = workingX,
                                            range = xMinF..xMaxF,
                                            step = 1f,
                                            uDp = grid.uDp,
                                            unit = "mm",
                                            onValueChange = { workingX = it },
                                            onSettle = { v ->
                                                workingX = v
                                                onMoveTo(workingX.toDouble(), workingY.toDouble(), null)
                                            },
                                        )
                                    }
                                    // RIGHT: vertical Y scrubber in a fixed-width full-height slot.
                                    Box(
                                        modifier = Modifier.fillMaxHeight().width(96.dp),
                                        contentAlignment = Alignment.Center,
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
                            }
                        }
                        // Sub-mode bodies are PLACEHOLDERS (Task D3) — E3–E6 replace these.
                        else -> FocusHint("$headerTitle — coming soon")
                    }
                }
            },
            field = {
                ListBlock(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp),
                ) {
                    // Homing rows (never "selected") — gated by availability.
                    if (avail.homeAll) {
                        item("home_all") {
                            MoveRow("Home All", DinghyIcons.HomeStateUnhomed, false, grid.uDp, t.accent) { onHomeAll() }
                        }
                    }
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
                    if (avail.saveLocation) {
                        item("save_location") {
                            MoveRow("Save Location", DinghyIcons.SaveLocation, mode == MoveMode.SaveDialog, grid.uDp, t.accent) {
                                mode = MoveMode.SaveDialog
                            }
                        }
                    }
                }

                FootButtonBar(uDp = grid.uDp) {
                    // Back: sub-mode → Overview; Overview → onBack (accent, FIRST — R5/R8).
                    OutlinedControl(
                        label = "",
                        onClick = { if (mode == MoveMode.Overview) onBack() else mode = MoveMode.Overview },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent,
                        icon = DinghyIcons.Back,
                    )
                }
            },
        )
    }
}

/** Header title + icon per the current [MoveMode] (header law). */
private fun moveModeHeader(mode: MoveMode): Pair<String, DinghyIcon> = when (mode) {
    MoveMode.Overview -> "Move" to DinghyIcons.LauncherMove
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

/** One-decimal mm formatting for the TouchMove X/Y readout. Null values render as "—". */
private fun fmt1(v: Double?): String =
    if (v == null) "—" else String.format(java.util.Locale.US, "%.1f", v)

/** A centered Focus-body hint/placeholder string (Overview homing hint + sub-mode stubs). */
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
