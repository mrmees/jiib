package works.mees.dinghy.ui.outputs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.CommandSpec
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.SetOutputPinArgs
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.outputs.OutputDescriptor
import works.mees.dinghy.outputs.OutputsHolder

/**
 * The `output_pin` detail page (SC-2/SC-3). Branches on [OutputDescriptor.pwm]:
 *  - **PWM** (`pwm = true`) → the shared [OutputScrubberDetail] % path dispatching [CommandRegistry
 *    .setOutputPin] with `pwm = true` (a duty-cycle scrubber).
 *  - **digital** (`pwm = false`) → the new [OutputToggleControl] On/Off page dispatching `pwm = false`.
 *
 * Both dispatch THROUGH the catalog (HIGH-2) built from the BARE [OutputDescriptor.commandName] (HIGH-1),
 * immediate-dispatch with NO Apply flow and NO confirm-guard step, toast-on-failure-stay (T-19-06-03), and a
 * per-objectKey busy lock. A read-only static pin renders value-only with the control disabled (SC-3).
 *
 * @param container the service-locator (live dispatcher + inFlight).
 * @param holder    the [OutputsHolder] whose per-output busy lock this page arms.
 * @param descriptor the output_pin being commanded.
 * @param currentPct  the live PWM % (PWM branch only — seeds the scrubber).
 * @param isOn        the live On/Off state (digital branch only — null = absent, SC-3).
 * @param onBack    the neutral Back exit.
 */
@Composable
fun OutputPinDetail(
    container: AppContainer,
    holder: OutputsHolder,
    descriptor: OutputDescriptor,
    currentPct: Float,
    isOn: Boolean?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (descriptor.pwm) {
        // PWM output_pin → reuse the shared % scrubber (dispatches setOutputPin pwm=true).
        OutputScrubberDetail(
            container = container,
            holder = holder,
            descriptor = descriptor,
            type = OutputScrubberType.PWM_PIN,
            currentValue = currentPct,
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    // Digital output_pin → the On/Off toggle page.
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    var failureText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dispatcher) {
        failureText = null
        val d = dispatcher ?: return@LaunchedEffect
        d.events.collect { event ->
            when (event) {
                is DispatchEvent.Failure -> {
                    failureText = event.message
                    holder.clearPending(descriptor.objectKey)
                }
            }
        }
    }
    LaunchedEffect(failureText) {
        if (failureText != null) { delay(4_000); failureText = null }
    }

    val key = "set_output_pin_${descriptor.commandName}"
    val busy = key in inFlight

    fun <P> dispatchCommand(command: CommandSpec<P>, args: P) {
        if (command.dispatchKey(args) in inFlight) return
        dispatcher?.dispatch(command, args)
    }

    fun setDigital(on: Boolean) {
        // Digital pin reaches 1.0 / 0.0 on the wire; markPending the clamped wire value (17-07).
        holder.markPending(descriptor.objectKey, if (on) 1.0 else 0.0)
        dispatchCommand(CommandRegistry.setOutputPin, SetOutputPinArgs(descriptor.commandName, pwm = false, on = on))
    }

    OutputToggleControl(
        prettyName = descriptor.prettyName,
        isOn = isOn,
        readOnly = descriptor.readOnly,
        enabled = !busy,
        failureText = failureText,
        onOn = { setDigital(true) },
        onOff = { setDigital(false) },
        onBack = onBack,
        modifier = modifier,
    )
}
