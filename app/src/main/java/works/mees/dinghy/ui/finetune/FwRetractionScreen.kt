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
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.CommandSpec
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.command.RetractionArgs
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.di.AppContainer

/**
 * Fine-Tune **Firmware-Retraction** mini-screen (D-12). Four `SET_RETRACTION`-driven tiles — Retract
 * length, Unretract extra length, Retract speed, Unretract speed — gated on `firmware_retraction`.
 *
 * ## BUILD-BLIND (RESEARCH Pitfall 3)
 * Neither dev printer exposes `firmware_retraction`, so this screen is code-reasoned + fixture-tested
 * only; there is NO on-device UAT gate for it. On the dev printers the reset baselines are null, so the
 * resets are no-ops (REVIEW #3).
 *
 * Each nudge re-sends all four fields ([RetractionArgs] carries the full set); the changed field is
 * computed from its live vm value + step, the other three are held at their live values. State-flip
 * busy lock + failure→SeverityToast as in the group screens. Gutter Back = [Intent.Neutral].
 */
@Composable
fun FwRetractionScreen(
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

    // 17-07 (WARN 4, BUILD-BLIND): clamp the markPending target per tuner against the EXISTING
    // PrinterCommands.*_MIN/*_MAX consts (the setRetraction builder re-clamps the full set identically),
    // so an at-cap '+' (e.g. retract length at RETRACT_LEN_MAX, tap '+') feeds markPending the same value
    // the wire sends — the skip-arm guard then treats it as the no-op it is, instead of wedging the lock.
    fun clampRetractionTarget(tuner: FineTuneTuner, value: Double): Double = when (tuner) {
        FineTuneTuner.RETRACT_LENGTH ->
            value.coerceIn(PrinterCommands.RETRACT_LEN_MIN, PrinterCommands.RETRACT_LEN_MAX)
        FineTuneTuner.UNRETRACT_EXTRA_LENGTH ->
            value.coerceIn(PrinterCommands.UNRETRACT_EXTRA_MIN, PrinterCommands.UNRETRACT_EXTRA_MAX)
        FineTuneTuner.RETRACT_SPEED, FineTuneTuner.UNRETRACT_SPEED ->
            value.coerceIn(PrinterCommands.RETRACT_SPEED_MIN.toDouble(), PrinterCommands.RETRACT_SPEED_MAX.toDouble())
        else -> value
    }

    // Re-send all four fields, overriding ONE; the others hold their live values (0 fallback if unread).
    fun send(
        tuner: FineTuneTuner,
        target: Double,
        retractLength: Double = vm.retractLength ?: 0.0,
        unretractExtraLength: Double = vm.unretractExtraLength ?: 0.0,
        retractSpeed: Double = vm.retractSpeed ?: 0.0,
        unretractSpeed: Double = vm.unretractSpeed ?: 0.0,
    ) {
        holder.markPending(tuner, clampRetractionTarget(tuner, target))
        dispatchCommand(
            CommandRegistry.setRetraction,
            RetractionArgs(
                retractLength = retractLength,
                unretractExtraLength = unretractExtraLength,
                retractSpeed = retractSpeed.roundToInt(),
                unretractSpeed = unretractSpeed.roundToInt(),
            ),
        )
    }

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            field = {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (vm.hasFwRetraction) {
                        // Retract length (step 0.1).
                        FineTuneTile(
                            icon = DinghyIcons.OutputCircle,
                            name = "Retract Len",
                            valueText = fmtValue(vm.retractLength, decimals = 2),
                            onDecrement = {
                                val cur = vm.retractLength ?: return@FineTuneTile
                                send(FineTuneTuner.RETRACT_LENGTH, (cur - RETRACT_LEN_STEP).coerceAtLeast(0.0), retractLength = (cur - RETRACT_LEN_STEP).coerceAtLeast(0.0))
                            },
                            onIncrement = {
                                val cur = vm.retractLength ?: return@FineTuneTile
                                send(FineTuneTuner.RETRACT_LENGTH, cur + RETRACT_LEN_STEP, retractLength = cur + RETRACT_LEN_STEP)
                            },
                            onReset = vm.baselines.retractLength?.let { base ->
                                { send(FineTuneTuner.RETRACT_LENGTH, base, retractLength = base) }
                            },
                            enabled = !groupBusy,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                        // Unretract extra length (step 0.1).
                        FineTuneTile(
                            icon = DinghyIcons.InputCircle,
                            name = "Unretract Extra",
                            valueText = fmtValue(vm.unretractExtraLength, decimals = 2),
                            onDecrement = {
                                val cur = vm.unretractExtraLength ?: return@FineTuneTile
                                send(FineTuneTuner.UNRETRACT_EXTRA_LENGTH, (cur - RETRACT_LEN_STEP).coerceAtLeast(0.0), unretractExtraLength = (cur - RETRACT_LEN_STEP).coerceAtLeast(0.0))
                            },
                            onIncrement = {
                                val cur = vm.unretractExtraLength ?: return@FineTuneTile
                                send(FineTuneTuner.UNRETRACT_EXTRA_LENGTH, cur + RETRACT_LEN_STEP, unretractExtraLength = cur + RETRACT_LEN_STEP)
                            },
                            onReset = vm.baselines.unretractExtraLength?.let { base ->
                                { send(FineTuneTuner.UNRETRACT_EXTRA_LENGTH, base, unretractExtraLength = base) }
                            },
                            enabled = !groupBusy,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                        // Retract speed (step 1).
                        FineTuneTile(
                            icon = DinghyIcons.MaxAccel,
                            name = "Retract Spd",
                            valueText = fmtValue(vm.retractSpeed, decimals = 0),
                            onDecrement = {
                                val cur = vm.retractSpeed ?: return@FineTuneTile
                                send(FineTuneTuner.RETRACT_SPEED, (cur - RETRACT_SPEED_STEP).coerceAtLeast(0.0), retractSpeed = (cur - RETRACT_SPEED_STEP).coerceAtLeast(0.0))
                            },
                            onIncrement = {
                                val cur = vm.retractSpeed ?: return@FineTuneTile
                                send(FineTuneTuner.RETRACT_SPEED, cur + RETRACT_SPEED_STEP, retractSpeed = cur + RETRACT_SPEED_STEP)
                            },
                            onReset = vm.baselines.retractSpeed?.let { base ->
                                { send(FineTuneTuner.RETRACT_SPEED, base, retractSpeed = base) }
                            },
                            enabled = !groupBusy,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                        // Unretract speed (step 1).
                        FineTuneTile(
                            icon = DinghyIcons.MaxAccel,
                            name = "Unretract Spd",
                            valueText = fmtValue(vm.unretractSpeed, decimals = 0),
                            onDecrement = {
                                val cur = vm.unretractSpeed ?: return@FineTuneTile
                                send(FineTuneTuner.UNRETRACT_SPEED, (cur - RETRACT_SPEED_STEP).coerceAtLeast(0.0), unretractSpeed = (cur - RETRACT_SPEED_STEP).coerceAtLeast(0.0))
                            },
                            onIncrement = {
                                val cur = vm.unretractSpeed ?: return@FineTuneTile
                                send(FineTuneTuner.UNRETRACT_SPEED, cur + RETRACT_SPEED_STEP, unretractSpeed = cur + RETRACT_SPEED_STEP)
                            },
                            onReset = vm.baselines.unretractSpeed?.let { base ->
                                { send(FineTuneTuner.UNRETRACT_SPEED, base, unretractSpeed = base) }
                            },
                            enabled = !groupBusy,
                            modifier = Modifier.fillMaxWidth().weight(1f),
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
