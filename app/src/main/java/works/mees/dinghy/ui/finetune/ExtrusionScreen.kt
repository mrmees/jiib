package works.mees.dinghy.ui.finetune

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.coroutines.delay
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.CommandSpec
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.FanArgs
import works.mees.dinghy.command.FlowFactorArgs
import works.mees.dinghy.command.PressureAdvanceArgs
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.di.AppContainer

/**
 * Fine-Tune **Extrusion** group (D-01: what affects the filament). Capability-gated tiles (D-08..D-11):
 * Flow %, Pressure advance, Smooth time, Part-cooling fan — plus a FW-Retraction ENTRY tile shown ONLY
 * when `vm.hasFwRetraction`, navigated via the explicit typed [onFwRetraction] callback (REVIEW #4 —
 * build-blind so UAT can't catch a dead wire; 17-06 threads it from the AppShell sub-nav).
 *
 * ## NO cold/temperature guard on flow or PA (REVIEW #5 / D-02)
 * Flow factor (M221) and pressure advance are non-extruding LIVE overrides — always available when
 * connected. Flow gates ONLY on `gcode_move`; PA/smooth gate ONLY on the `extruder` OBJECT presence.
 * There is deliberately NO `can_extrude`/cold-extrude guard here.
 *
 * State-flip whole-group busy lock (D-15), state-flip-confirmed nudges, failure→SeverityToast as in
 * [MotionScreen]. Gutter Back = [Intent.Neutral].
 */
@Composable
fun ExtrusionScreen(
    container: AppContainer,
    holder: FineTuneHolder,
    onBack: () -> Unit,
    onFwRetraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val vm by holder.vm.collectAsStateWithLifecycle()
    var failureText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(inFlight) { holder.setInFlight(inFlight) }
    val groupBusy = inFlight.isNotEmpty() || holder.pendingStateFlip != null || vm.groupBusy

    LaunchedEffect(dispatcher) {
        failureText = null
        val d = dispatcher ?: return@LaunchedEffect
        d.events.collect { event ->
            when (event) {
                is DispatchEvent.Failure -> {
                    failureText = event.message
                    holder.clearPending()
                }
            }
        }
    }
    LaunchedEffect(failureText) {
        if (failureText != null) { delay(4_000); failureText = null }
    }

    fun <P> dispatchCommand(command: CommandSpec<P>, args: P) {
        if (command.dispatchKey(args) in inFlight) return
        dispatcher?.dispatch(command, args)
    }

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            field = {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // D-08 Flow % (step 1, M221, gate gcode_move ONLY — NO cold guard, REVIEW #5).
                    // Reset = M221 S100 (protocol 100%, always available — no baseline, D-16).
                    if (vm.hasGcodeMove) {
                        FineTuneTile(
                            iconRes = R.drawable.output_circle,
                            name = "Flow",
                            valueText = vm.flowPct?.let { "$it%" } ?: DASH,
                            onDecrement = {
                                val cur = vm.flowPct ?: return@FineTuneTile
                                val target = cur - FLOW_STEP
                                holder.markPending(FineTuneTuner.FLOW, target.toDouble())
                                dispatchCommand(CommandRegistry.flowFactor, FlowFactorArgs(target))
                            },
                            onIncrement = {
                                val cur = vm.flowPct ?: return@FineTuneTile
                                val target = cur + FLOW_STEP
                                holder.markPending(FineTuneTuner.FLOW, target.toDouble())
                                dispatchCommand(CommandRegistry.flowFactor, FlowFactorArgs(target))
                            },
                            onReset = {
                                holder.markPending(FineTuneTuner.FLOW, 100.0)
                                dispatchCommand(CommandRegistry.flowFactor, FlowFactorArgs(100))
                            },
                            enabled = !groupBusy,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                    // D-09 Pressure advance (step 0.001, gate extruder OBJECT ONLY — NO cold guard).
                    // Reset → baseline if non-null else null (REVIEW #3).
                    if (vm.hasExtruder) {
                        FineTuneTile(
                            iconRes = R.drawable.text_select_move_forward,
                            name = "Press Adv",
                            valueText = if (vm.pressureAdvance == null) {
                                DASH
                            } else {
                                String.format(Locale.US, "%.3f", vm.pressureAdvance)
                            },
                            onDecrement = {
                                val cur = vm.pressureAdvance ?: return@FineTuneTile
                                val target = (cur - PA_STEP).coerceAtLeast(0.0)
                                holder.markPending(FineTuneTuner.PRESSURE_ADVANCE, target)
                                dispatchCommand(
                                    CommandRegistry.setPressureAdvance,
                                    PressureAdvanceArgs(PressureAdvanceArgs.ADVANCE, target),
                                )
                            },
                            onIncrement = {
                                val cur = vm.pressureAdvance ?: return@FineTuneTile
                                val target = cur + PA_STEP
                                holder.markPending(FineTuneTuner.PRESSURE_ADVANCE, target)
                                dispatchCommand(
                                    CommandRegistry.setPressureAdvance,
                                    PressureAdvanceArgs(PressureAdvanceArgs.ADVANCE, target),
                                )
                            },
                            onReset = vm.baselines.pressureAdvance?.let { base ->
                                {
                                    holder.markPending(FineTuneTuner.PRESSURE_ADVANCE, base)
                                    dispatchCommand(
                                        CommandRegistry.setPressureAdvance,
                                        PressureAdvanceArgs(PressureAdvanceArgs.ADVANCE, base),
                                    )
                                }
                            },
                            enabled = !groupBusy,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                        // D-10 Smooth time (step 0.01, SET_PRESSURE_ADVANCE SMOOTH_TIME).
                        // Reset → baseline (from CONFIG pressure_advance_smooth_time) if non-null else null.
                        FineTuneTile(
                            iconRes = R.drawable.avg_time,
                            name = "Smooth",
                            valueText = if (vm.smoothTime == null) {
                                DASH
                            } else {
                                String.format(Locale.US, "%.2f", vm.smoothTime)
                            },
                            onDecrement = {
                                val cur = vm.smoothTime ?: return@FineTuneTile
                                val target = (cur - SMOOTH_STEP).coerceAtLeast(0.0)
                                holder.markPending(FineTuneTuner.SMOOTH_TIME, target)
                                dispatchCommand(
                                    CommandRegistry.setPressureAdvance,
                                    PressureAdvanceArgs(PressureAdvanceArgs.SMOOTH_TIME, target),
                                )
                            },
                            onIncrement = {
                                val cur = vm.smoothTime ?: return@FineTuneTile
                                val target = cur + SMOOTH_STEP
                                holder.markPending(FineTuneTuner.SMOOTH_TIME, target)
                                dispatchCommand(
                                    CommandRegistry.setPressureAdvance,
                                    PressureAdvanceArgs(PressureAdvanceArgs.SMOOTH_TIME, target),
                                )
                            },
                            onReset = vm.baselines.smoothTime?.let { base ->
                                {
                                    holder.markPending(FineTuneTuner.SMOOTH_TIME, base)
                                    dispatchCommand(
                                        CommandRegistry.setPressureAdvance,
                                        PressureAdvanceArgs(PressureAdvanceArgs.SMOOTH_TIME, base),
                                    )
                                }
                            },
                            enabled = !groupBusy,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                    // D-11 Part-cooling fan (step 5%, M106, gate fan). onReset = null — no config baseline
                    // exists for a Klipper [fan] (A2 / Open-Q1 / REVIEW #3); owner confirms at UAT.
                    if (vm.hasFan) {
                        FineTuneTile(
                            iconRes = R.drawable.mode_fan,
                            name = "Part Fan",
                            valueText = vm.partFanPct?.let { "$it%" } ?: DASH,
                            onDecrement = {
                                val cur = vm.partFanPct ?: return@FineTuneTile
                                val target = (cur - FAN_STEP_PCT).coerceAtLeast(0)
                                holder.markPending(FineTuneTuner.PART_FAN, target.toDouble())
                                dispatchCommand(CommandRegistry.setFan, FanArgs(target))
                            },
                            onIncrement = {
                                val cur = vm.partFanPct ?: return@FineTuneTile
                                val target = (cur + FAN_STEP_PCT).coerceAtMost(100)
                                holder.markPending(FineTuneTuner.PART_FAN, target.toDouble())
                                dispatchCommand(CommandRegistry.setFan, FanArgs(target))
                            },
                            onReset = null, // A2 / Open-Q1: [fan] has no persistent configured speed.
                            enabled = !groupBusy,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                    // FW-Retraction ENTRY — shown ONLY when the object is present; tap → onFwRetraction (D-12).
                    if (vm.hasFwRetraction) {
                        OutlinedControl(
                            label = "Firmware Retraction",
                            onClick = onFwRetraction,
                            modifier = Modifier.fillMaxWidth(),
                            intent = Intent.Accent,
                        )
                    }
                    failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
                }
            },
            gutter = {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    OutlinedControl(
                        label = "Back",
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                        intent = Intent.Neutral,
                    )
                }
            },
        )
    }
}
