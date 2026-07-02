package works.mees.jiib.ui.calibration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.jiib.R
import works.mees.jiib.calibration.CalibrationRoutine
import works.mees.jiib.calibration.TiltHolder
import works.mees.jiib.calibration.TiltState
import works.mees.jiib.calibration.TiltVm
import works.mees.jiib.calibration.ZAdjustment
import works.mees.jiib.calibration.tiltState
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.GatingState
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.components.ConfirmOnBack
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.HardLockStatusCard
import works.mees.jiib.designsystem.components.UnknownStatusCard
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.footAction
import works.mees.jiib.designsystem.focus.FocusStage
import works.mees.jiib.control.ControlSpecs
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.di.AppContainer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.FocusText
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

/** The two routines this ONE screen serves (D-10 shared automatic-flow code path). */
enum class TiltVariant { ZTilt, Qgl }

/**
 * The Z-tilt / QGL shared automatic-flow screen (CALIB-03 / D-10 jiib restyle). ONE code path
 * serves both routines — [variant] selects the title + which [CommandRegistry] command Run dispatches.
 *
 * Thin VM-collecting wrapper that forwards a plain snapshot to the STATELESS [TiltContent]
 * composable (WARNING-5 seam). [TiltContent] is the @Preview target.
 *
 * @param container the service-locator (provides the session dispatcher).
 * @param holder    the headless [TiltHolder] (load-scoped run facts + homed gate + adjustments + error).
 * @param variant   Z-tilt or QGL — selects the title + dispatched command.
 * @param onBack    leave the page (neutral Back, D-10).
 */
@Composable
fun TiltScreen(
    container: AppContainer,
    holder: TiltHolder,
    variant: TiltVariant,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    val gating by container.gatingState.collectAsStateWithLifecycle(initialValue = GatingState.Idle)
    val vm by holder.vm.collectAsStateWithLifecycle()

    val runCommand = when (variant) {
        TiltVariant.ZTilt -> CommandRegistry.zTiltAdjust
        TiltVariant.Qgl -> CommandRegistry.quadGantryLevel
    }

    // Every entry resets to the Idle landing state.
    LaunchedEffect(Unit) { holder.reset() }

    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val running = runCommand.dispatchKey(Unit) in inFlight
    val state = tiltState(ran = vm.ran, running = running, failed = vm.failed)

    // Confirm-on-back: armed ONLY when this screen owns the HardLock. Tilt owns both
    // "quad_gantry_level" (QGL variant) and "z_tilt_adjust" (ZTilt variant). Filter to these
    // keys so a different screen's HardLock never triggers the guard here.
    val tiltLocked = (gating as? GatingState.Locked)?.key?.let { key ->
        key == "quad_gantry_level" || key == "z_tilt_adjust" || key.startsWith("home")
    } == true

    ConfirmOnBack(enabled = tiltLocked, onBack = onBack) { requestBack ->
        TiltContent(
            vm = vm,
            variant = variant,
            state = state,
            running = running,
            isPrinting = isPrinting,
            gating = gating,
            onAcknowledgeUnknown = { dispatcher?.acknowledgeUnresolved() },
            onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
            onRun = {
                val d = dispatcher ?: return@TiltContent
                d.dispatch(runCommand, Unit)
                holder.markDispatched()
            },
            onHome = { dispatcher?.dispatch(CommandRegistry.homeAll, Unit) },
            onBack = requestBack,
            modifier = modifier,
        )
    }
}

/**
 * Stateless layout for the Z-tilt / QGL page — @Preview target (WARNING-5 seam).
 *
 * Both [TiltVariant.ZTilt] and [TiltVariant.Qgl] render from this ONE parameterized composable.
 *
 * Layout:
 *  - Focus = bed-tilt icon + state headline (token-restyled, unchanged information).
 *  - Field = status body text + adjustment recommendations (result state). No scrollable list.
 *  - Foot = state-adaptive [FootButtonBar] (D-10).
 */
@Composable
fun TiltContent(
    vm: TiltVm,
    variant: TiltVariant = TiltVariant.ZTilt,
    state: TiltState = TiltState.Idle,
    running: Boolean = false,
    isPrinting: Boolean = false,
    gating: GatingState = GatingState.Idle,
    onAcknowledgeUnknown: () -> Unit = {},
    onEmergencyStop: () -> Unit = {},
    onRun: () -> Unit = {},
    onHome: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // WR-03 (27-review): all user-facing copy resolves through stringResource (PREVIEW_AND_TOKENS law).
    val title = when (variant) {
        TiltVariant.ZTilt -> stringResource(R.string.tilt_title_ztilt)
        TiltVariant.Qgl -> stringResource(R.string.tilt_title_qgl)
    }
    val runLabel = when (variant) {
        TiltVariant.ZTilt -> stringResource(R.string.tilt_run_label_ztilt)
        TiltVariant.Qgl -> stringResource(R.string.tilt_run_label_qgl)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        Box(Modifier.fillMaxSize()) {
            val routine = when (variant) {
                TiltVariant.ZTilt -> CalibrationRoutine.Z_TILT
                TiltVariant.Qgl   -> CalibrationRoutine.QUAD_GANTRY_LEVEL
            }

            // HardLock morph: Tilt owns "quad_gantry_level" (QGL) and "z_tilt_adjust" (ZTilt).
            // Resolve the per-variant label; null means this screen doesn't own the active lock
            // and the Focus renders normally. `running` (from inFlight) still drives the foot.
            val tiltLockedLabel = (gating as? GatingState.Locked)?.key?.let { key ->
                when {
                    key == "quad_gantry_level" -> stringResource(R.string.gating_leveling_gantry)
                    key == "z_tilt_adjust"     -> stringResource(R.string.gating_z_tilt)
                    key.startsWith("home")     -> stringResource(R.string.gating_homing)
                    else                       -> null
                }
            }
            val tiltLocked = tiltLockedLabel != null
            // Unknown (abnormal HardLock exit) — scope to Tilt-owned keys so an unrelated screen's
            // unresolved op doesn't surface "still running" here.
            val tiltUnknownOwned = (gating as? GatingState.Unknown)?.key?.let {
                it == "quad_gantry_level" || it == "z_tilt_adjust" || it.startsWith("home")
            } == true

            ScreenScaffold(
                focus = {
                    FocusFrame(
                        title = title,
                        icon = routineIconToken(routine),
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
                        if (tiltUnknownOwned) {
                            UnknownStatusCard(grid.uDp, onDismiss = onAcknowledgeUnknown, Modifier.fillMaxSize())
                            return@FocusFrame
                        }
                        // HardLock Focus morph: while running, replace the entire Focus body with
                        // a centered status card. E-stop stays live in the FocusFrame header.
                        if (tiltLockedLabel != null) {
                            HardLockStatusCard(tiltLockedLabel, grid.uDp, Modifier.fillMaxSize())
                            return@FocusFrame
                        }
                        TiltFocus(
                            title = title,
                            state = state,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                },
                field = {
                    TiltFieldBody(
                        state = state,
                        homedGate = vm.homedGate,
                        runLabel = runLabel,
                        adjustments = vm.adjustments,
                        errorText = vm.errorText,
                        modifier = Modifier.weight(1f),
                    )
                    // State-adaptive FootButtonBar (D-10).
                    // While locked (tilt/QGL running), non-Back buttons are disabled — Back
                    // (guarded by ConfirmOnBack) and e-stop stay live. The `running` branch
                    // already shows a disabled "Running" button so the lock and running states
                    // are consistent; the guard on the other branches is belt-and-suspenders.
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
                                    // Unhomed: offer Home All.
                                    add(footAction(ControlSpecs.calibrationHomeAll, onClick = onHome, enabled = !tiltLocked))
                                }
                                running -> {
                                    // Running: non-interactive.
                                    add(FootAction(
                                        label = stringResource(R.string.calibration_running),
                                        icon = JiibIcons.CalibrationWait,
                                        onClick = {},
                                        intent = Intent.Neutral,
                                        enabled = false,
                                    ))
                                }
                                state == TiltState.Done || state == TiltState.Failed -> {
                                    // Result shown: Run Again.
                                    add(FootAction(
                                        label = stringResource(R.string.calibration_run_again),
                                        icon = JiibIcons.Revert,
                                        onClick = onRun,
                                        intent = Intent.Go, // R5: expected re-run action
                                        enabled = !tiltLocked,
                                    ))
                                }
                                else -> {
                                    // Homed idle: Run (variant-selected, gated on not Running).
                                    add(FootAction(
                                        label = stringResource(R.string.calibration_run),
                                        icon = JiibIcons.CalibrationRun,
                                        onClick = onRun,
                                        intent = Intent.Go, // R5: the screen's expected action
                                        enabled = !running && !tiltLocked,
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

@Composable
private fun TiltFocus(
    title: String,
    state: TiltState,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val headlineColor = when (state) {
        TiltState.Done -> t.go
        TiltState.Failed -> t.stop
        TiltState.Running -> t.text
        TiltState.Idle -> t.text
    }
    FocusStage(modifier = modifier) {
        // Title lives in the FocusFrame header (the mandatory 1U header) — not repeated here.
        Box(
            Modifier.fillMaxWidth(0.9f).aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            // bed_tilt → token-tinted (THEME-01). ONE Focus asset for both variants — no overlay.
            Icon(
                painter = painterResource(R.drawable.bed_tilt),
                contentDescription = title,
                tint = headlineColor,
                modifier = Modifier.fillMaxSize(0.9f),
            )
        }
    }
}

@Composable
private fun TiltFieldBody(
    state: TiltState,
    homedGate: Boolean,
    runLabel: String,
    adjustments: List<ZAdjustment>,
    errorText: String?,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(modifier, verticalArrangement = Arrangement.Center) {
        when (state) {
            TiltState.Idle ->
                if (homedGate) {
                    TiltHeadlineText(stringResource(R.string.tilt_ready_headline, runLabel), t.text)
                    TiltBodyText(stringResource(R.string.tilt_ready_body))
                } else {
                    TiltHeadlineText(stringResource(R.string.tilt_home_first_headline), t.heat)
                    TiltBodyText(stringResource(R.string.tilt_home_first_body, runLabel))
                }
            TiltState.Running -> {
                TiltHeadlineText(stringResource(R.string.calibration_running), t.text)
                TiltBodyText(stringResource(R.string.tilt_running_body))
            }
            TiltState.Failed -> {
                TiltHeadlineText(stringResource(R.string.tilt_failed_headline), t.stop)
                TiltBodyText(errorText ?: stringResource(R.string.tilt_failed_body))
            }
            TiltState.Done -> {
                TiltHeadlineText(stringResource(R.string.tilt_done_headline), t.go)
                if (adjustments.isEmpty()) {
                    TiltBodyText(stringResource(R.string.tilt_done_body))
                } else {
                    TiltBodyText(stringResource(R.string.tilt_adjustments_applied))
                    adjustments.forEach { adj ->
                        Row(
                            Modifier.padding(top = 8.dp), // gapS (R13)
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = adj.stepper,
                                color = t.text2,
                                style = JiibType.dataInline.toTextStyle(t),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = String.format(Locale.US, "%+.4f mm", adj.mm),
                                color = t.text,
                                style = JiibType.dataInline.toTextStyle(t),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TiltHeadlineText(text: String, color: Color) {
    val t = LocalTokens.current
    // State copy ("Ready to Run", "Leveled", …) is UI text → Geist. Only the printer-pulled
    // stepper names + adjustment values below stay Mono.
    Text(
        text = text,
        color = color,
        style = JiibType.screenTitle.toTextStyle(t),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TiltBodyText(text: String) {
    val t = LocalTokens.current
    FocusText(
        text = text,
        role = JiibType.body,
        t = t,
        color = t.text2,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        textAlign = TextAlign.Start,
        maxHeightU = 2f,
    )
}
