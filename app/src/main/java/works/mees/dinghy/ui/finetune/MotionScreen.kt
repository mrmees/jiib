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
import kotlinx.coroutines.delay
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.CommandSpec
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.SpeedFactorArgs
import works.mees.dinghy.command.VelocityLimitArgs
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.di.AppContainer

/**
 * Fine-Tune **Motion** group (D-01: what moves the print head). Five capability-gated value tiles
 * (D-03..D-07): Speed %, Max velocity, Max accel, Minimum cruise ratio, Square-corner velocity.
 *
 * ## State-flip whole-group busy lock (D-15 / REVIEW #2)
 * `groupBusy = inFlight.isNotEmpty() || holder.pendingStateFlip != null`. DIVERGES from ExtrudeScreen's
 * per-key check AND from a bare `inFlight.isNotEmpty()`: the group stays busy until the printer-object
 * value actually flips, not on the bare RPC ack. Every tile `enabled = !groupBusy`. Each nudge computes
 * the TARGET from the live `vm` value + the per-control step, marks the holder's pendingStateFlip, then
 * dispatches ONE command (no optimistic local state — the readout flips only when the reducer reports).
 *
 * Absent tunables are HIDDEN, not disabled (SC-2): each tile renders only when its capability gate is on.
 * A dispatcher [DispatchEvent.Failure] surfaces a non-fatal error [SeverityToast] (out-of-range
 * rejections land here, G1 lesson) and clears the pending flip. Gutter Back = [Intent.Neutral].
 *
 * This live `container` overload resolves the dispatcher/holder flows + builds the dispatch side-effects,
 * then delegates rendering to the container-free [MotionContent] — the same surface the stateless preview
 * overload calls (18-06 state-hoist, mirroring the PrintStatus anchor). Behaviour is unchanged.
 */
@Composable
fun MotionScreen(
    container: AppContainer,
    holder: FineTuneHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val vm by holder.vm.collectAsStateWithLifecycle()
    var failureText by remember { mutableStateOf<String?>(null) }

    // Feed the dispatcher's live in-flight set into the holder so the busy lock's first arm works (D-15).
    LaunchedEffect(inFlight) { holder.setInFlight(inFlight) }

    // STATE-FLIP whole-group busy (D-15): the vm already folds inFlight + pendingStateFlip into groupBusy.
    val groupBusy = inFlight.isNotEmpty() || holder.pendingStateFlip != null || vm.groupBusy

    LaunchedEffect(dispatcher) {
        failureText = null
        val d = dispatcher ?: return@LaunchedEffect
        d.events.collect { event ->
            when (event) {
                is DispatchEvent.Failure -> {
                    failureText = event.message
                    holder.clearPending() // a failure clears the state-flip wait (T-17-05-02).
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

    MotionContent(
        vm = vm,
        enabled = !groupBusy,
        failureText = failureText,
        onBack = onBack,
        markPending = { tuner, target -> holder.markPending(tuner, target) },
        dispatchSpeed = { args -> dispatchCommand(CommandRegistry.speedFactor, args) },
        dispatchVelocityLimit = { args -> dispatchCommand(CommandRegistry.setVelocityLimit, args) },
        modifier = modifier,
    )
}

/**
 * Stateless preview/host overload of [MotionScreen]: renders the same [MotionContent] from a pure
 * [FineTuneVm] fixture with no [AppContainer], dispatcher, or holder (18-06 SC-1 — no live Moonraker).
 * All side-effects default to no-ops; a preview never dispatches.
 */
@Composable
fun MotionScreen(
    vm: FineTuneVm,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    MotionContent(
        vm = vm,
        enabled = !vm.groupBusy,
        failureText = null,
        onBack = onBack,
        markPending = { _, _ -> },
        dispatchSpeed = {},
        dispatchVelocityLimit = {},
        modifier = modifier,
    )
}

/**
 * The pure, container-free Motion rendering surface shared by BOTH [MotionScreen] overloads — the live
 * `container` entry (real dispatch side-effects) and the stateless preview entry (no-op side-effects).
 * Holds the capability-gated tile column + the Back gutter; carries NO `remember`/flow/dispatcher state,
 * so it renders byte-identically under `@Preview` and at runtime. The capability gates (`vm.has*`) and the
 * absent-tunable HIDDEN path are exactly the D-01 archetype 18-06 proves.
 */
@Composable
private fun MotionContent(
    vm: FineTuneVm,
    enabled: Boolean,
    failureText: String?,
    onBack: () -> Unit,
    markPending: (FineTuneTuner, Double) -> Unit,
    dispatchSpeed: (SpeedFactorArgs) -> Unit,
    dispatchVelocityLimit: (VelocityLimitArgs) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            field = {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // D-03 Speed % (step 5, M220, gate gcode_move). Reset = M220 S100 (protocol 100%,
                    // always available — no baseline needed, D-16).
                    if (vm.hasGcodeMove) {
                        FineTuneTile(
                            icon = DinghyIcons.Speed,
                            name = stringResource(R.string.cd_finetune_speed),
                            valueText = vm.speedPct?.let { "$it%" } ?: DASH,
                            onDecrement = {
                                val cur = vm.speedPct ?: return@FineTuneTile
                                val target = cur - SPEED_STEP
                                markPending(FineTuneTuner.SPEED, target.toDouble())
                                dispatchSpeed(SpeedFactorArgs(target))
                            },
                            onIncrement = {
                                val cur = vm.speedPct ?: return@FineTuneTile
                                val target = cur + SPEED_STEP
                                markPending(FineTuneTuner.SPEED, target.toDouble())
                                dispatchSpeed(SpeedFactorArgs(target))
                            },
                            onReset = {
                                markPending(FineTuneTuner.SPEED, 100.0)
                                dispatchSpeed(SpeedFactorArgs(100))
                            },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                    // D-04 Max velocity (step 10, SET_VELOCITY_LIMIT VELOCITY, gate toolhead).
                    if (vm.hasToolhead) {
                        VelocityLimitTile(
                            icon = DinghyIcons.MaxVelocity,
                            name = stringResource(R.string.cd_finetune_max_velocity),
                            value = vm.maxVelocity,
                            unit = " mm/s",
                            step = VEL_STEP,
                            field = VelocityLimitArgs.VELOCITY,
                            tuner = FineTuneTuner.MAX_VELOCITY,
                            baseline = vm.baselines.maxVelocity,
                            markPending = markPending,
                            dispatch = dispatchVelocityLimit,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                        // D-05 Max accel (step 100, SET_VELOCITY_LIMIT ACCEL).
                        VelocityLimitTile(
                            icon = DinghyIcons.MaxAccel,
                            name = stringResource(R.string.cd_finetune_max_accel),
                            value = vm.maxAccel,
                            unit = "",
                            step = ACCEL_STEP,
                            field = VelocityLimitArgs.ACCEL,
                            tuner = FineTuneTuner.MAX_ACCEL,
                            baseline = vm.baselines.maxAccel,
                            markPending = markPending,
                            dispatch = dispatchVelocityLimit,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                        // D-06 Minimum cruise ratio — DISPLAY percent, send ratio (REVIEW #9).
                        // step 5 percentage points = 0.05 ratio.
                        FineTuneTile(
                            icon = DinghyIcons.MinCruise,
                            name = stringResource(R.string.cd_finetune_min_cruise),
                            valueText = vm.minCruisePct?.let { "$it%" } ?: DASH,
                            onDecrement = {
                                val cur = vm.minCruisePct ?: return@FineTuneTile
                                val targetPct = (cur - MIN_CRUISE_STEP_PCT).coerceAtLeast(0)
                                markPending(FineTuneTuner.MIN_CRUISE, targetPct.toDouble())
                                dispatchVelocityLimit(
                                    VelocityLimitArgs(VelocityLimitArgs.MIN_CRUISE_RATIO, targetPct / 100.0),
                                )
                            },
                            onIncrement = {
                                val cur = vm.minCruisePct ?: return@FineTuneTile
                                val targetPct = (cur + MIN_CRUISE_STEP_PCT).coerceAtMost(100)
                                markPending(FineTuneTuner.MIN_CRUISE, targetPct.toDouble())
                                dispatchVelocityLimit(
                                    VelocityLimitArgs(VelocityLimitArgs.MIN_CRUISE_RATIO, targetPct / 100.0),
                                )
                            },
                            onReset = vm.baselines.minCruise?.let { base ->
                                {
                                    markPending(FineTuneTuner.MIN_CRUISE, base * 100)
                                    dispatchVelocityLimit(
                                        VelocityLimitArgs(VelocityLimitArgs.MIN_CRUISE_RATIO, base),
                                    )
                                }
                            },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                        // D-07 Square-corner velocity (step 0.1, SET_VELOCITY_LIMIT SQUARE_CORNER_VELOCITY).
                        VelocityLimitTile(
                            icon = DinghyIcons.SquareCornerVelocity,
                            name = stringResource(R.string.cd_finetune_scv),
                            value = vm.scv,
                            unit = " mm/s",
                            step = SCV_STEP,
                            field = VelocityLimitArgs.SCV,
                            tuner = FineTuneTuner.SCV,
                            baseline = vm.baselines.scv,
                            markPending = markPending,
                            dispatch = dispatchVelocityLimit,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth().weight(1f),
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
                        intent = Intent.Neutral, // 15.2 C7: plain nav spends no safety color.
                    )
                }
            },
        )
    }
}
