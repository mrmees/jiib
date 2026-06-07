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
import androidx.compose.ui.res.stringResource
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
import works.mees.dinghy.designsystem.icons.DinghyIcons
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
 *
 * This live `container` overload resolves the dispatcher/holder flows + builds the dispatch side-effects,
 * then delegates rendering to the container-free [ExtrusionContent] — the same surface the stateless
 * preview overload calls (18-06 state-hoist, mirroring the PrintStatus anchor). Behaviour is unchanged.
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

    ExtrusionContent(
        vm = vm,
        enabled = !groupBusy,
        failureText = failureText,
        onBack = onBack,
        onFwRetraction = onFwRetraction,
        markPending = { tuner, target -> holder.markPending(tuner, target) },
        dispatchFlow = { args -> dispatchCommand(CommandRegistry.flowFactor, args) },
        dispatchPressureAdvance = { args -> dispatchCommand(CommandRegistry.setPressureAdvance, args) },
        dispatchFan = { args -> dispatchCommand(CommandRegistry.setFan, args) },
        modifier = modifier,
    )
}

/**
 * Stateless preview/host overload of [ExtrusionScreen]: renders the same [ExtrusionContent] from a pure
 * [FineTuneVm] fixture with no [AppContainer], dispatcher, or holder (18-06 SC-1 — no live Moonraker). The
 * `fineTuneNoFwRetraction` fixture proves the absent→HIDDEN FW-retraction path. Side-effects default to
 * no-ops; a preview never dispatches.
 */
@Composable
fun ExtrusionScreen(
    vm: FineTuneVm,
    onBack: () -> Unit = {},
    onFwRetraction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ExtrusionContent(
        vm = vm,
        enabled = !vm.groupBusy,
        failureText = null,
        onBack = onBack,
        onFwRetraction = onFwRetraction,
        markPending = { _, _ -> },
        dispatchFlow = {},
        dispatchPressureAdvance = {},
        dispatchFan = {},
        modifier = modifier,
    )
}

/**
 * The pure, container-free Extrusion rendering surface shared by BOTH [ExtrusionScreen] overloads — the
 * live `container` entry (real dispatch side-effects) and the stateless preview entry (no-op side-effects).
 * Holds the capability-gated tile column + the FW-Retraction entry + the Back gutter; carries NO
 * `remember`/flow/dispatcher state, so it renders byte-identically under `@Preview` and at runtime. The
 * `vm.hasFwRetraction` HIDDEN path is the absent-capability archetype 18-06 proves (de-risks Phase 19).
 */
@Composable
private fun ExtrusionContent(
    vm: FineTuneVm,
    enabled: Boolean,
    failureText: String?,
    onBack: () -> Unit,
    onFwRetraction: () -> Unit,
    markPending: (FineTuneTuner, Double) -> Unit,
    dispatchFlow: (FlowFactorArgs) -> Unit,
    dispatchPressureAdvance: (PressureAdvanceArgs) -> Unit,
    dispatchFan: (FanArgs) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                            icon = DinghyIcons.OutputCircle,
                            name = stringResource(R.string.cd_finetune_flow),
                            valueText = vm.flowPct?.let { "$it%" } ?: DASH,
                            onDecrement = {
                                val cur = vm.flowPct ?: return@FineTuneTile
                                val target = cur - FLOW_STEP
                                markPending(FineTuneTuner.FLOW, target.toDouble())
                                dispatchFlow(FlowFactorArgs(target))
                            },
                            onIncrement = {
                                val cur = vm.flowPct ?: return@FineTuneTile
                                val target = cur + FLOW_STEP
                                markPending(FineTuneTuner.FLOW, target.toDouble())
                                dispatchFlow(FlowFactorArgs(target))
                            },
                            onReset = {
                                markPending(FineTuneTuner.FLOW, 100.0)
                                dispatchFlow(FlowFactorArgs(100))
                            },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                    // D-09 Pressure advance (step 0.001, gate extruder OBJECT ONLY — NO cold guard).
                    // Reset → baseline if non-null else null (REVIEW #3).
                    if (vm.hasExtruder) {
                        FineTuneTile(
                            icon = DinghyIcons.PressureAdvance,
                            name = stringResource(R.string.cd_finetune_pressure_advance),
                            valueText = if (vm.pressureAdvance == null) {
                                DASH
                            } else {
                                String.format(Locale.US, "%.3f", vm.pressureAdvance)
                            },
                            onDecrement = {
                                val cur = vm.pressureAdvance ?: return@FineTuneTile
                                val target = (cur - PA_STEP).coerceAtLeast(0.0)
                                markPending(FineTuneTuner.PRESSURE_ADVANCE, target)
                                dispatchPressureAdvance(
                                    PressureAdvanceArgs(PressureAdvanceArgs.ADVANCE, target),
                                )
                            },
                            onIncrement = {
                                val cur = vm.pressureAdvance ?: return@FineTuneTile
                                val target = cur + PA_STEP
                                markPending(FineTuneTuner.PRESSURE_ADVANCE, target)
                                dispatchPressureAdvance(
                                    PressureAdvanceArgs(PressureAdvanceArgs.ADVANCE, target),
                                )
                            },
                            onReset = vm.baselines.pressureAdvance?.let { base ->
                                {
                                    markPending(FineTuneTuner.PRESSURE_ADVANCE, base)
                                    dispatchPressureAdvance(
                                        PressureAdvanceArgs(PressureAdvanceArgs.ADVANCE, base),
                                    )
                                }
                            },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                        // D-10 Smooth time (step 0.01, SET_PRESSURE_ADVANCE SMOOTH_TIME).
                        // Reset → baseline (from CONFIG pressure_advance_smooth_time) if non-null else null.
                        FineTuneTile(
                            icon = DinghyIcons.SmoothTime,
                            name = stringResource(R.string.cd_finetune_smooth_time),
                            valueText = if (vm.smoothTime == null) {
                                DASH
                            } else {
                                String.format(Locale.US, "%.2f", vm.smoothTime)
                            },
                            onDecrement = {
                                val cur = vm.smoothTime ?: return@FineTuneTile
                                val target = (cur - SMOOTH_STEP).coerceAtLeast(0.0)
                                markPending(FineTuneTuner.SMOOTH_TIME, target)
                                dispatchPressureAdvance(
                                    PressureAdvanceArgs(PressureAdvanceArgs.SMOOTH_TIME, target),
                                )
                            },
                            onIncrement = {
                                val cur = vm.smoothTime ?: return@FineTuneTile
                                val target = cur + SMOOTH_STEP
                                markPending(FineTuneTuner.SMOOTH_TIME, target)
                                dispatchPressureAdvance(
                                    PressureAdvanceArgs(PressureAdvanceArgs.SMOOTH_TIME, target),
                                )
                            },
                            onReset = vm.baselines.smoothTime?.let { base ->
                                {
                                    markPending(FineTuneTuner.SMOOTH_TIME, base)
                                    dispatchPressureAdvance(
                                        PressureAdvanceArgs(PressureAdvanceArgs.SMOOTH_TIME, base),
                                    )
                                }
                            },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                    // D-11 Part-cooling fan (step 5%, M106, gate fan). onReset = null — no config baseline
                    // exists for a Klipper [fan] (A2 / Open-Q1 / REVIEW #3); owner confirms at UAT.
                    if (vm.hasFan) {
                        FineTuneTile(
                            icon = DinghyIcons.FanMode,
                            name = stringResource(R.string.cd_finetune_part_fan),
                            valueText = vm.partFanPct?.let { "$it%" } ?: DASH,
                            onDecrement = {
                                val cur = vm.partFanPct ?: return@FineTuneTile
                                val target = (cur - FAN_STEP_PCT).coerceAtLeast(0)
                                markPending(FineTuneTuner.PART_FAN, target.toDouble())
                                dispatchFan(FanArgs(target))
                            },
                            onIncrement = {
                                val cur = vm.partFanPct ?: return@FineTuneTile
                                val target = (cur + FAN_STEP_PCT).coerceAtMost(100)
                                markPending(FineTuneTuner.PART_FAN, target.toDouble())
                                dispatchFan(FanArgs(target))
                            },
                            onReset = null, // A2 / Open-Q1: [fan] has no persistent configured speed.
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                    // FW-Retraction ENTRY — shown ONLY when the object is present; tap → onFwRetraction (D-12).
                    if (vm.hasFwRetraction) {
                        OutlinedControl(
                            label = stringResource(R.string.finetune_fw_retraction_label),
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
                        label = stringResource(R.string.common_back),
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                        intent = Intent.Neutral,
                    )
                }
            },
        )
    }
}
