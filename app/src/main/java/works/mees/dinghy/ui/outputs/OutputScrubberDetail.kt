package works.mees.dinghy.ui.outputs

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import androidx.compose.material3.Text
import kotlin.math.roundToInt
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.CommandSpec
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.command.SetGenericFanArgs
import works.mees.dinghy.command.SetHeaterArgs
import works.mees.dinghy.command.SetOutputPinArgs
import works.mees.dinghy.command.SetServoArgs
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.ScrubberActions
import works.mees.dinghy.designsystem.ScrubberPage
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.outputs.OutputDescriptor
import works.mees.dinghy.outputs.OutputsHolder
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * Which single-numeric output family this scrubber detail page serves (the dispatch/range/unit selector).
 * ONE shared scrubber page (SC-2) serves all five — the per-type difference is only the range/unit and which
 * [CommandRegistry] spec the value dispatches through.
 *
 *  - [FAN] — `fan_generic`, 0..100 % (SET_FAN_SPEED), Off = SPEED 0.
 *  - [HEATER] — `heater_generic`, 0..[PrinterCommands.MAX_TEMP_C] °C (SET_HEATER_TEMPERATURE), Off = TARGET 0.
 *  - [SERVO] — `servo`, 0..servoAngleMax ° (SET_SERVO ANGLE), Off = WIDTH 0 (the live-verifiable disable form).
 *  - [PWM_PIN] — PWM `output_pin`, 0..100 % (SET_PIN VALUE), Off = VALUE 0.
 *  - [PWM_TOOL] — `pwm_tool`, 0..100 % (SET_PIN VALUE), Off = VALUE 0.
 */
enum class OutputScrubberType { FAN, HEATER, SERVO, PWM_PIN, PWM_TOOL }

/**
 * The shared immediate-dispatch scrubber detail (SC-2/SC-3) for every single-numeric output type (fan %,
 * heater °C, servo °, pwm output_pin %, pwm_tool %). Reuses [ScrubberPage] in its HIGH-3 [ScrubberActions
 * .OnSettle] mode: there is NO Apply flow and NO confirm-guard step — the value dispatches THROUGH the catalog
 * ([CommandRegistry], HIGH-2) built from the descriptor's BARE [OutputDescriptor.commandName] (HIGH-1) EXACTLY
 * ONCE on settle (gesture-end / stepper tap). An explicit Off action dispatches the zero/disable form
 * immediately; for a servo Off routes through `WIDTH=0` ([SetServoArgs.disable]) so "every page has an Off"
 * holds for servo too. markPending is fed the CLAMPED wire value (17-07) so the optimistic flip target equals
 * the dispatched value. A dispatch [DispatchEvent.Failure] → [SeverityToast] + clearPending and the user STAYS
 * on the page (T-19-06-03). A read-only descriptor renders value-only with the control disabled (SC-3).
 *
 * This live `container` overload resolves the dispatcher + builds the dispatch side-effects, then delegates
 * to the container-free [OutputScrubberContent] (the same surface the preview seam drives — 18-06 hoist).
 *
 * @param container the service-locator (live dispatcher + inFlight).
 * @param holder    the [OutputsHolder] whose per-output [OutputsHolder.markPending]/[OutputsHolder.clearPending]
 *                  busy lock this page arms (keyed by [OutputDescriptor.objectKey]).
 * @param descriptor the output being commanded.
 * @param type      which family (selects range/unit + the dispatch spec).
 * @param currentValue the live display value to seed the scrubber from (caller reads it off the row VM).
 * @param onBack    the neutral Back exit.
 */
@Composable
fun OutputScrubberDetail(
    container: AppContainer,
    holder: OutputsHolder,
    descriptor: OutputDescriptor,
    type: OutputScrubberType,
    currentValue: Float,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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

    // The per-page busy lock: this output's dispatch key is in-flight, scoped to THIS objectKey (T-19-06-03).
    val key = type.dispatchKey(descriptor.commandName)
    val busy = key in inFlight

    fun <P> dispatchCommand(command: CommandSpec<P>, args: P) {
        if (command.dispatchKey(args) in inFlight) return
        dispatcher?.dispatch(command, args)
    }

    // Dispatch a target value THROUGH the catalog (HIGH-2), built from the BARE commandName (HIGH-1), arming
    // the holder's optimistic flip with the SAME clamped wire value the command sends (17-07).
    fun dispatchValue(v: Int) {
        when (type) {
            OutputScrubberType.FAN -> {
                holder.markPending(descriptor.objectKey, PrinterCommands.outputPctToWire(v))
                dispatchCommand(CommandRegistry.setGenericFan, SetGenericFanArgs(descriptor.commandName, v))
            }
            OutputScrubberType.HEATER -> {
                holder.markPending(descriptor.objectKey, PrinterCommands.clampHeaterTarget(v).toDouble())
                dispatchCommand(CommandRegistry.setHeater, SetHeaterArgs(descriptor.commandName, v))
            }
            OutputScrubberType.SERVO -> {
                // Servo can't confirm from the live PWM value (reached() is timeout-only); markPending the
                // clamped angle so the backstop has a stable target.
                holder.markPending(
                    descriptor.objectKey,
                    PrinterCommands.clampServoAngle(v, descriptor.servoAngleMax.roundToInt()).toDouble(),
                )
                dispatchCommand(
                    CommandRegistry.setServo,
                    SetServoArgs(descriptor.commandName, deg = v, maxDeg = descriptor.servoAngleMax.roundToInt()),
                )
            }
            OutputScrubberType.PWM_PIN, OutputScrubberType.PWM_TOOL -> {
                holder.markPending(descriptor.objectKey, PrinterCommands.outputPctToWire(v))
                dispatchCommand(CommandRegistry.setOutputPin, SetOutputPinArgs(descriptor.commandName, pwm = true, pct = v))
            }
        }
    }

    // The explicit Off/zero/disable action (every page has one): fan SPEED=0, heater TARGET=0, pwm VALUE=0,
    // servo WIDTH=0 (the live-verifiable disable form — servo Off is NOT omitted).
    fun dispatchOff() {
        when (type) {
            OutputScrubberType.SERVO -> {
                holder.markPending(descriptor.objectKey, 0.0)
                dispatchCommand(CommandRegistry.setServo, SetServoArgs(descriptor.commandName, disable = true))
            }
            else -> dispatchValue(0)
        }
    }

    OutputScrubberContent(
        prettyName = descriptor.prettyName,
        type = type,
        currentValue = currentValue,
        servoAngleMax = descriptor.servoAngleMax,
        readOnly = descriptor.readOnly,
        enabled = !busy,
        failureText = failureText,
        heaterTemp = if (type == OutputScrubberType.HEATER) currentValue else null,
        onSettle = { dispatchValue(it.roundToInt()) },
        onOff = { dispatchOff() },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * The pure, container-free scrubber-detail surface shared by the live entry and the @Preview seam (no
 * `remember`/dispatcher state — renders byte-identically under `@Preview` and at runtime). Holds the
 * [ScrubberPage] (OnSettle mode), the Off action, and the failure toast.
 */
@Composable
fun OutputScrubberContent(
    prettyName: String,
    type: OutputScrubberType,
    currentValue: Float,
    servoAngleMax: Float,
    readOnly: Boolean,
    enabled: Boolean,
    failureText: String?,
    heaterTemp: Float?,
    onSettle: (Float) -> Unit,
    onOff: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val range = type.range(servoAngleMax)
    val step = type.step

    Box(modifier.fillMaxSize()) {
        if (readOnly) {
            // SC-3: a read-only (static_value) output is value-only — no control, no dispatch.
            ScreenScaffold(
                field = {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = prettyName,
                            color = t.text2,
                            fontFamily = GeistMono,
                            fontWeight = FontWeight.Medium,
                            fontSize = fsSp(20f, t.fs).sp,
                        )
                        Text(
                            text = "${currentValue.roundToInt()}${type.unit}",
                            color = t.text3,
                            fontFamily = GeistMono,
                            fontWeight = FontWeight.Bold,
                            fontSize = fsSp(48f, t.fs).sp,
                        )
                        Text(
                            text = stringResource(R.string.output_read_only),
                            color = t.text3,
                            fontFamily = GeistMono,
                            fontSize = fsSp(15f, t.fs).sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
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
            return@Box
        }

        // The scrubber owns the full screen (its own ScreenScaffold + opaque scrim). Off + failure toast
        // float over the field via the overlay column below.
        ScrubberPage(
            label = prettyName,
            value = currentValue.coerceIn(range.start, range.endInclusive),
            range = range,
            step = step,
            unit = type.unit,
            actions = ScrubberActions.OnSettle(
                onSettle = { if (enabled) onSettle(it) },
                onBack = onBack,
            ),
        )

        // Off action + heater current-temp + failure toast overlaid at the top of the field.
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (type == OutputScrubberType.HEATER && heaterTemp != null) {
                Text(
                    text = stringResource(R.string.output_heater_current, heaterTemp.roundToInt()),
                    color = t.text2,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = fsSp(16f, t.fs).sp,
                )
            }
            OutlinedControl(
                label = stringResource(R.string.output_off),
                onClick = { if (enabled) onOff() },
                modifier = Modifier.fillMaxWidth(),
                intent = Intent.Danger,
            )
            failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
        }
    }
}

// --- Per-type range/step/unit + dispatch-key derivation -----------------------------------------------

private val OutputScrubberType.unit: String
    get() = when (this) {
        OutputScrubberType.HEATER -> "°C"
        OutputScrubberType.SERVO -> "°"
        else -> "%"
    }

private val OutputScrubberType.step: Float
    get() = when (this) {
        OutputScrubberType.HEATER -> 5f
        OutputScrubberType.SERVO -> 5f
        else -> 1f
    }

private fun OutputScrubberType.range(servoAngleMax: Float): ClosedFloatingPointRange<Float> = when (this) {
    OutputScrubberType.HEATER -> 0f..PrinterCommands.MAX_TEMP_C.toFloat()
    OutputScrubberType.SERVO -> 0f..servoAngleMax
    else -> 0f..100f
}

/** The per-output dispatch key for the busy lock — mirrors the [CommandRegistry] spec keys (HIGH-1 BARE name). */
private fun OutputScrubberType.dispatchKey(commandName: String): String = when (this) {
    OutputScrubberType.FAN -> "set_output_fan_$commandName"
    OutputScrubberType.HEATER -> "set_$commandName"
    OutputScrubberType.SERVO -> "set_output_servo_$commandName"
    OutputScrubberType.PWM_PIN, OutputScrubberType.PWM_TOOL -> "set_output_pin_$commandName"
}
