package works.mees.dinghy.ui.calibration

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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.dinghy.R
import works.mees.dinghy.calibration.CalibrationRoutine
import works.mees.dinghy.calibration.TiltHolder
import works.mees.dinghy.calibration.TiltState
import works.mees.dinghy.calibration.TiltVm
import works.mees.dinghy.calibration.ZAdjustment
import works.mees.dinghy.calibration.tiltState
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.control.ControlSpecs
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle

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

    TiltContent(
        vm = vm,
        variant = variant,
        state = state,
        running = running,
        isPrinting = isPrinting,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onRun = {
            val d = dispatcher ?: return@TiltContent
            d.dispatch(runCommand, Unit)
            holder.markDispatched()
        },
        onHome = { dispatcher?.dispatch(CommandRegistry.homeAll, Unit) },
        onBack = onBack,
        modifier = modifier,
    )
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
            ScreenScaffold(
                focus = {
                    FocusFrame(
                        title = title,
                        icon = routineIconToken(routine),
                        uDp = grid.uDp,
                        modifier = Modifier.fillMaxSize(),
                        isPrinting = isPrinting,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                    ) {
                        TiltFocus(
                            title = title,
                            state = state,
                            modifier = Modifier.fillMaxSize().padding(8.dp),
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
                                // Unhomed: offer Home All.
                                OutlinedControl(
                                    spec = ControlSpecs.calibrationHomeAll,
                                    onClick = onHome,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            running -> {
                                // Running: non-interactive.
                                OutlinedControl(
                                    label = stringResource(R.string.calibration_running),
                                    onClick = {},
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Neutral,
                                    enabled = false,
                                )
                            }
                            state == TiltState.Done || state == TiltState.Failed -> {
                                // Result shown: Run Again.
                                OutlinedControl(
                                    label = stringResource(R.string.calibration_run_again),
                                    onClick = onRun,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Go, // R5: expected re-run action
                                )
                            }
                            else -> {
                                // Homed idle: Run (variant-selected, gated on not Running).
                                OutlinedControl(
                                    label = stringResource(R.string.calibration_run),
                                    onClick = onRun,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Go, // R5: the screen's expected action
                                    enabled = !running,
                                )
                            }
                        }
                    }
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
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
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
                                style = DinghyType.dataInline.toTextStyle(t),
                            )
                            Text(
                                text = String.format(Locale.US, "%+.4f mm", adj.mm),
                                color = t.text,
                                style = DinghyType.dataInline.toTextStyle(t),
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
        style = DinghyType.screenTitle.toTextStyle(t),
    )
}

@Composable
private fun TiltBodyText(text: String) {
    val t = LocalTokens.current
    Text(
        text = text,
        color = t.text2,
        style = DinghyType.body.toTextStyle(t),
        modifier = Modifier.padding(top = 8.dp),
    )
}
