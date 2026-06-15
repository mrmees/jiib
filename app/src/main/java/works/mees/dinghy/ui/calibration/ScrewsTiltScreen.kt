package works.mees.dinghy.ui.calibration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.dinghy.R
import works.mees.dinghy.calibration.ScrewPoint
import works.mees.dinghy.calibration.ScrewsTiltHolder
import works.mees.dinghy.calibration.ScrewsTiltVm
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.control.ControlSpecs
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.calibration.CalibrationRoutine
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.theme.fsSp
import kotlin.math.max

/**
 * The Screws-Tilt page (CALIB-02 / D-10 jiib restyle). Thin VM-collecting wrapper that forwards a
 * plain snapshot to the STATELESS [ScrewsTiltContent] composable (WARNING-5 seam).
 *
 * [ScrewsTiltContent] is the @Preview target — no VM, no AppContainer required for design-time.
 *
 * @param container the service-locator (provides the session dispatcher + printer state).
 * @param holder    the headless [ScrewsTiltHolder] (guided loop + coords + homed gate + error).
 * @param onBack    the neutral Back action.
 */
@Composable
fun ScrewsTiltScreen(
    container: AppContainer,
    holder: ScrewsTiltHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val vm by holder.vm.collectAsStateWithLifecycle()

    var failureText by remember { mutableStateOf<String?>(null) }

    // Surface a dispatch failure as an error toast (T-09-04-02).
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

    val runKey = CommandRegistry.screwsTiltCalculate.dispatchKey(Unit)
    val running = runKey in inFlight

    // Armed: Run tapped this visit. showResults = armed && !running so stale turns never show.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed, running) { holder.setShowResults(armed && !running) }

    val errorText = failureText ?: vm.errorText

    ScrewsTiltContent(
        vm = vm,
        running = running,
        errorText = errorText,
        isPrinting = isPrinting,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onRun = {
            if (!running) {
                dispatcher?.dispatch(CommandRegistry.screwsTiltCalculate, Unit)
                armed = true
            }
        },
        onHome = {
            val key = CommandRegistry.homeAll.dispatchKey(Unit)
            if (key !in inFlight) dispatcher?.dispatch(CommandRegistry.homeAll, Unit)
        },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless layout for the Screws-Tilt page — @Preview target (WARNING-5 seam).
 *
 * Layout:
 *  - Focus = the to-scale bed spatial visualization ([ScrewsTiltFocus]) — spatial carve-out, unchanged shape.
 *  - Field = [ListBlock] of [ListRow]s (screw name primary, turn instruction trailing in Geist Mono).
 *  - Foot = state-adaptive [FootButtonBar] (foot-of-list pattern).
 *
 * D-10: this is a PURE RESTYLE — the spatial visualization in Focus is NOT converted to a list.
 */
@Composable
fun ScrewsTiltContent(
    vm: ScrewsTiltVm,
    running: Boolean = false,
    errorText: String? = null,
    isPrinting: Boolean = false,
    onEmergencyStop: () -> Unit = {},
    onRun: () -> Unit = {},
    onHome: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        Box(Modifier.fillMaxSize()) {
            ScreenScaffold(
                focus = {
                    FocusFrame(
                        title = stringResource(routineTitleRes(CalibrationRoutine.SCREWS_TILT)),
                        icon = routineIconToken(CalibrationRoutine.SCREWS_TILT),
                        uDp = grid.uDp,
                        modifier = Modifier.fillMaxSize(),
                        isPrinting = isPrinting,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                    ) {
                        ScrewsTiltFocus(vm = vm, modifier = Modifier.fillMaxSize().padding(8.dp))
                    }
                },
                field = {
                    // ListBlock suppresses swipe-up drawer automatically (scrollable Field — Pitfall 1).
                    ListBlock(
                        modifier = Modifier
                            .weight(1f),
                    ) {
                        items(vm.points, key = { it.key }) { point ->
                            ScrewListRow(point = point, uDp = grid.uDp)
                        }
                    }
                    errorText?.let {
                        SeverityToast(Severity.Error, it, Modifier.fillMaxWidth())
                    }
                    // State-adaptive FootButtonBar (D-10).
                    FootButtonBar(
                        uDp = grid.uDp,
                    ) {
                        // Back FIRST (accent — R5/R8), before the state-adaptive primary.
                        OutlinedControl(
                            label = "",
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Accent,
                            icon = DinghyIcons.Back,
                            contentDescription = stringResource(R.string.common_back),
                        )
                        when {
                            !vm.homedGate -> {
                                // Unhomed: offer Home All instead of Run.
                                OutlinedControl(
                                    spec = ControlSpecs.calibrationHomeAll,
                                    onClick = onHome,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            running -> {
                                // Running: non-interactive; no abort in current impl.
                                OutlinedControl(
                                    label = stringResource(R.string.calibration_running),
                                    onClick = {},
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Neutral,
                                    enabled = false,
                                )
                            }
                            vm.loop.totalScrews > 0 -> {
                                // Result shown: Run Again.
                                OutlinedControl(
                                    label = stringResource(R.string.calibration_run_again),
                                    onClick = onRun,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Go, // R5: expected re-run action
                                )
                            }
                            else -> {
                                // Homed idle: Run.
                                OutlinedControl(
                                    label = stringResource(R.string.calibration_run),
                                    onClick = onRun,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Go, // R5: the screen's expected action
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}

/**
 * One screw as a [ListRow] — primary label = screw name, trailing = turn instruction (Geist Mono).
 * Informational only (onClick = no-op, selected = false).
 * No leading icon per D-16 (no owner-confirmed per-screw glyph for this screen).
 */
@Composable
private fun ScrewListRow(point: ScrewPoint, uDp: androidx.compose.ui.unit.Dp) {
    val t = LocalTokens.current
    val turn = point.turn
    val turnText = when {
        turn == null -> "—"
        turn.isBase -> stringResource(R.string.screws_turn_base)
        else -> "${turn.adjust} ${turn.sign ?: ""}".trim()
    }
    ListRow(
        selected = false,
        onClick = {},
        uDp = uDp,
        trailingContent = {
            Text(
                text = turnText,
                style = DinghyType.dataMeta.toTextStyle(t),
                color = t.text2,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        },
    ) {
        Text(
            text = point.name ?: point.key,
            color = t.text,
            style = DinghyType.listLabel.toTextStyle(t),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Focus = the to-scale bed, centered and filling the pane. Each point is a state glyph at its
 * real bed location + the screw name (see [BedScale]). D-06 fallback: when no coords are
 * available, shows a short prompt.
 *
 * SPATIAL CARVE-OUT (D-10): this custom drawn surface is NOT converted to a list.
 */
@Composable
private fun ScrewsTiltFocus(vm: ScrewsTiltVm, modifier: Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (vm.hasCoords) {
            BedScale(
                vm = vm,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = 0.95f)
                    .clip(shape)
                    .border(BorderStroke(2.dp, t.outline), shape)
                    .background(t.surface),
            )
        } else {
            Text(
                text = if (vm.loop.totalScrews > 0) {
                    stringResource(R.string.screws_map_unavailable)
                } else {
                    stringResource(R.string.screws_run_prompt)
                },
                color = t.text2,
                style = DinghyType.body.toTextStyle(t),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A to-scale square bed with location-accurate point indicators. Bed extents come from the
 * screw-coord bounding box (with a margin so edge screws aren't clipped); the printer Y axis
 * (up) is flipped to screen Y (down). Each point is its state glyph.
 */
@Composable
private fun BedScale(vm: ScrewsTiltVm, modifier: Modifier) {
    val coordPoints = vm.points.filter { it.x != null && it.y != null }
    if (coordPoints.isEmpty()) {
        Box(modifier)
        return
    }

    val minX = coordPoints.minOf { it.x!! }
    val maxX = coordPoints.maxOf { it.x!! }
    val minY = coordPoints.minOf { it.y!! }
    val maxY = coordPoints.maxOf { it.y!! }
    val spanX = max(maxX - minX, 1.0)
    val spanY = max(maxY - minY, 1.0)
    val padX = spanX * 0.18
    val padY = spanY * 0.18
    val loX = minX - padX
    val hiX = maxX + padX
    val loY = minY - padY
    val hiY = maxY + padY
    Box(modifier) {
        BoxWithPoints(coordPoints, loX, hiX, loY, hiY)
    }
}

/** Place a state glyph at each point's fractional bed position (printer-Y flipped). */
@Composable
private fun BoxWithPoints(
    points: List<ScrewPoint>,
    loX: Double,
    hiX: Double,
    loY: Double,
    hiY: Double,
) {
    val t = LocalTokens.current
    Box(Modifier.fillMaxSize()) {
        points.forEach { p ->
            val fx = ((p.x!! - loX) / (hiX - loX)).toFloat().coerceIn(0f, 1f)
            val fy = (1f - ((p.y!! - loY) / (hiY - loY)).toFloat()).coerceIn(0f, 1f)
            val turn = p.turn
            // 27-review WR-04: the bed-map state glyphs route through the DinghyIcons registry
            // (promoted verbatim — same glyphs as before, now subset/gate-covered).
            val glyph = when {
                turn == null -> DinghyIcons.ScrewPending
                turn.isBase -> DinghyIcons.ScrewBase
                turn.isInTol -> DinghyIcons.ScrewInTolerance
                turn.sign == "CCW" -> DinghyIcons.ScrewTurnCcw
                turn.sign == "CW" -> DinghyIcons.ScrewTurnCw
                else -> DinghyIcons.ScrewPending
            }
            val tint = when {
                turn == null -> t.text2
                turn.isBase -> t.text2
                turn.isInTol -> t.accent
                turn.sign == "CW" -> t.go
                turn.sign == "CCW" -> t.stop
                else -> t.accent
            }
            Box(
                Modifier.fillMaxSize().padding(8.dp),
                contentAlignment = BiasAlignment(fx, fy),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DinghyIconView(icon = glyph, tint = tint, sizeDp = fsSp(56f, t.fs).dp)
                    p.name?.let { name ->
                        Text(
                            text = shortScrewName(name),
                            color = t.text2,
                            style = DinghyType.body.toTextStyle(t),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (turn != null) {
                        Text(
                            text = "${"%.3f".format(turn.z)} mm",
                            color = t.text3,
                            style = DinghyType.dataMeta.toTextStyle(t),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Trim a trailing " screw" so corner labels stay short on the bed. */
private fun shortScrewName(name: String): String =
    name.trim().removeSuffix("screw").trim().ifEmpty { name.trim() }

/** Map a 0..1 fraction to Compose's -1..1 bias for [androidx.compose.ui.BiasAlignment]. */
private fun BiasAlignment(fx: Float, fy: Float): Alignment =
    androidx.compose.ui.BiasAlignment(horizontalBias = fx * 2f - 1f, verticalBias = fy * 2f - 1f)
