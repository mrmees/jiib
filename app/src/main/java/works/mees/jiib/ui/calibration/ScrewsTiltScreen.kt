package works.mees.jiib.ui.calibration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.jiib.R
import works.mees.jiib.calibration.ScrewPoint
import works.mees.jiib.calibration.ScrewsTiltHolder
import works.mees.jiib.calibration.ScrewsTiltVm
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.DispatchEvent
import works.mees.jiib.command.GatingState
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.components.ConfirmOnBack
import works.mees.jiib.designsystem.components.HardLockStatusCard
import works.mees.jiib.designsystem.components.UnknownStatusCard
import works.mees.jiib.designsystem.Severity
import works.mees.jiib.designsystem.SeverityToast
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.focusGlyphSideDp
import works.mees.jiib.designsystem.components.footAction
import works.mees.jiib.designsystem.focus.FocusStage
import works.mees.jiib.control.ControlSpecs
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.icons.JiibIconView
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.calibration.CalibrationRoutine
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.FocusText
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.ui.temperature.titleCase
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
    val gating by container.gatingState.collectAsStateWithLifecycle(initialValue = GatingState.Idle)
    val locked = (gating as? GatingState.Locked)?.key?.let {
        it == "screws_tilt" || it.startsWith("home")
    } == true
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
    // `armed` is retained for result display; the Focus morph is driven by gatingState (locked).
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed, running) { holder.setShowResults(armed && !running) }

    val errorText = failureText ?: vm.errorText

    ConfirmOnBack(enabled = locked, onBack = onBack) { requestBack ->
        ScrewsTiltContent(
            vm = vm,
            running = running,
            errorText = errorText,
            isPrinting = isPrinting,
            gating = gating,
            onAcknowledgeUnknown = { dispatcher?.acknowledgeUnresolved() },
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
            onBack = requestBack,
            modifier = modifier,
        )
    }
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
    gating: GatingState = GatingState.Idle,
    onAcknowledgeUnknown: () -> Unit = {},
    onEmergencyStop: () -> Unit = {},
    onRun: () -> Unit = {},
    onHome: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        // HardLock morph: ScrewsTilt owns "screws_tilt" and "home_*" (Home All on this screen).
        // Drive the Focus morph off gatingState (the authoritative HardLock signal), not the local
        // `running` flag. `running` (from inFlight) still drives the foot-button state; `armed`
        // still gates result display in the wrapper.
        val screwsLockedLabel = (gating as? GatingState.Locked)?.key?.let { key ->
            when {
                key == "screws_tilt"   -> stringResource(R.string.gating_screws_tilt)
                key.startsWith("home") -> stringResource(R.string.gating_homing)
                else                   -> null
            }
        }
        val isLocked = screwsLockedLabel != null
        // Unknown (abnormal HardLock exit) — scope to ScrewsTilt-owned keys so an unrelated screen's
        // unresolved op doesn't surface "still running" here.
        val unknownOwned = (gating as? GatingState.Unknown)?.key?.let {
            it == "screws_tilt" || it.startsWith("home")
        } == true

        Box(Modifier.fillMaxSize()) {
            ScreenScaffold(
                focus = {
                    FocusFrame(
                        title = stringResource(routineTitleRes(CalibrationRoutine.SCREWS_TILT)),
                        icon = routineIconToken(CalibrationRoutine.SCREWS_TILT),
                        uDp = grid.uDp,
                        modifier = Modifier.fillMaxSize(),
                        isPrinting = isPrinting,
                        safetyActive = gating !is GatingState.Idle,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                        contentInset = 0.dp,
                    ) {
                        // Unknown Focus morph (precedence: Unknown > Locked > normal content): when
                        // the link or firmware can't confirm the HardLock completed, show "Still
                        // running" and require explicit dismissal. E-stop stays live above.
                        if (unknownOwned) {
                            UnknownStatusCard(grid.uDp, onDismiss = onAcknowledgeUnknown, Modifier.fillMaxSize())
                            return@FocusFrame
                        }
                        // HardLock Focus morph: while measuring or homing, replace the entire Focus
                        // body with a centered status card. E-stop stays live in the FocusFrame header.
                        if (screwsLockedLabel != null) {
                            HardLockStatusCard(screwsLockedLabel, grid.uDp, Modifier.fillMaxSize())
                            return@FocusFrame
                        }
                        ScrewsTiltFocus(vm = vm, modifier = Modifier.fillMaxSize())
                    }
                },
                field = {
                    // ListBlock suppresses swipe-up drawer automatically (scrollable Field — Pitfall 1).
                    // Dim while locked — screw rows are informational (onClick = no-op) so alpha-only
                    // is sufficient; no click-blocking required.
                    ListBlock(
                        modifier = Modifier
                            .weight(1f)
                            .alpha(if (isLocked) 0.38f else 1f),
                    ) {
                        items(vm.points, key = { it.key }) { point ->
                            ScrewListRow(point = point, uDp = grid.uDp)
                        }
                    }
                    errorText?.let {
                        SeverityToast(Severity.Error, it, Modifier.fillMaxWidth())
                    }
                    // State-adaptive FootButtonBar (D-10).
                    // While locked, non-Back buttons are disabled — Back (guarded by ConfirmOnBack)
                    // and e-stop stay live.
                    FootButtonBar(
                        uDp = grid.uDp,
                        actions = buildList {
                            // Back FIRST (accent — R5/R8), before the state-adaptive primary.
                            add(FootAction(
                                label = stringResource(R.string.common_back),
                                icon = JiibIcons.Back,
                                onClick = onBack,
                                intent = Intent.Accent,
                                contentDescription = stringResource(R.string.common_back),
                            ))
                            when {
                                !vm.homedGate -> {
                                    // Unhomed: offer Home All instead of Run.
                                    add(footAction(ControlSpecs.calibrationHomeAll, onClick = onHome, enabled = !isLocked))
                                }
                                running -> {
                                    // Running: non-interactive; no abort in current impl.
                                    add(FootAction(
                                        label = stringResource(R.string.calibration_running),
                                        icon = JiibIcons.CalibrationWait,
                                        onClick = {},
                                        intent = Intent.Neutral,
                                        enabled = false,
                                    ))
                                }
                                vm.loop.totalScrews > 0 -> {
                                    // Result shown: Run Again.
                                    add(FootAction(
                                        label = stringResource(R.string.calibration_run_again),
                                        icon = JiibIcons.Revert,
                                        onClick = onRun,
                                        intent = Intent.Go, // R5: expected re-run action
                                        enabled = !isLocked,
                                    ))
                                }
                                else -> {
                                    // Homed idle: Run.
                                    add(FootAction(
                                        label = stringResource(R.string.calibration_run),
                                        icon = JiibIcons.CalibrationRun,
                                        onClick = onRun,
                                        intent = Intent.Go, // R5: the screen's expected action
                                        enabled = !isLocked,
                                    ))
                                }
                            }
                        },
                    )
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
                style = JiibType.dataMeta.toTextStyle(t),
                color = t.text2,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        },
    ) {
        Text(
            text = point.name?.let { titleCase(it) } ?: point.key,
            color = t.text,
            style = JiibType.listLabel.toTextStyle(t),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Focus = the to-scale screw map, centered in the pane (see [BedScale]). D-06 fallback:
 * when no coords are available, shows a short prompt.
 *
 * SPATIAL CARVE-OUT (D-10): this custom drawn surface is NOT converted to a list.
 */
@Composable
private fun ScrewsTiltFocus(vm: ScrewsTiltVm, modifier: Modifier) {
    val t = LocalTokens.current
    FocusStage(modifier = modifier) {
        if (vm.hasCoords) {
            BedScale(vm = vm)
        } else {
            FocusText(
                text = if (vm.loop.totalScrews > 0) {
                    stringResource(R.string.screws_map_unavailable)
                } else {
                    stringResource(R.string.screws_run_prompt)
                },
                role = JiibType.body,
                t = t,
                color = t.text2,
                modifier = Modifier.fillMaxWidth(),
                maxHeightU = 2f,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A to-scale, aspect-correct screw map. The screw bounding box (with margin) is drawn
 * letterboxed into the pane — its real X:Y aspect ratio is preserved, never stretched —
 * so a 3-screw triangle stays a triangle and a wide bed looks wide. Printer Y (up) is
 * flipped to screen Y (down). The framed surface is sized to fit via [BoxWithConstraints].
 */
@Composable
private fun BedScale(vm: ScrewsTiltVm) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val coordPoints = vm.points.filter { it.x != null && it.y != null }
    if (coordPoints.isEmpty()) return

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
    val aspect = screwBoxAspect(hiX - loX, hiY - loY) // width / height, clamped

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val availW = maxWidth
        val availH = maxHeight
        val markerDp = focusGlyphSideDp(maxWidth, maxHeight, 0.18f)
        // Largest w x h matching `aspect` (= w/h) that still fits the pane -> letterbox.
        val frameW: androidx.compose.ui.unit.Dp
        val frameH: androidx.compose.ui.unit.Dp
        if (availW / (availH * aspect) > 1f) {
            // pane wider than the ratio -> height-bound, margin on the sides
            frameH = availH
            frameW = availH * aspect
        } else {
            // pane taller/narrower than the ratio -> width-bound, margin top/bottom
            frameW = availW
            frameH = availW / aspect
        }
        Box(
            Modifier
                .size(width = frameW, height = frameH)
                .clip(shape)
                .border(BorderStroke(2.dp, t.outline), shape)
                .background(t.surface),
        ) {
            BoxWithPoints(coordPoints, loX, hiX, loY, hiY, markerDp)
        }
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
    sizeDp: androidx.compose.ui.unit.Dp,
) {
    val t = LocalTokens.current
    Box(Modifier.fillMaxSize()) {
        points.forEach { p ->
            val fx = ((p.x!! - loX) / (hiX - loX)).toFloat().coerceIn(0f, 1f)
            val fy = (1f - ((p.y!! - loY) / (hiY - loY)).toFloat()).coerceIn(0f, 1f)
            val turn = p.turn
            // 27-review WR-04: the bed-map state glyphs route through the JiibIcons registry
            // (promoted verbatim — same glyphs as before, now subset/gate-covered).
            val glyph = when {
                turn == null -> JiibIcons.ScrewPending
                turn.isBase -> JiibIcons.ScrewBase
                turn.isInTol -> JiibIcons.ScrewInTolerance
                turn.sign == "CCW" -> JiibIcons.ScrewTurnCcw
                turn.sign == "CW" -> JiibIcons.ScrewTurnCw
                else -> JiibIcons.ScrewPending
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
                    JiibIconView(icon = glyph, tint = tint, sizeDp = sizeDp)
                    if (turn != null) {
                        Text(
                            text = "${"%.3f".format(turn.z)} mm",
                            color = t.text3,
                            style = JiibType.dataMeta.toTextStyle(t),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Aspect ratio (width / height) of the padded screw bounding box, clamped so a
 * near-collinear screw layout can't collapse the drawn frame to a sliver. Callers pass
 * already-positive spans (bounds use `max(..., 1.0)` + padding), so no divide-by-zero.
 */
internal fun screwBoxAspect(spanX: Double, spanY: Double): Float =
    (spanX / spanY).toFloat().coerceIn(0.25f, 4f)

/** Map a 0..1 fraction to Compose's -1..1 bias for [androidx.compose.ui.BiasAlignment]. */
private fun BiasAlignment(fx: Float, fy: Float): Alignment =
    androidx.compose.ui.BiasAlignment(horizontalBias = fx * 2f - 1f, verticalBias = fy * 2f - 1f)
