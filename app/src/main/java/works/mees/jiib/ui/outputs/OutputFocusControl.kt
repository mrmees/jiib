package works.mees.jiib.ui.outputs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import works.mees.jiib.R
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.CommandSpec
import works.mees.jiib.command.DispatchEvent
import works.mees.jiib.command.PrinterCommands
import works.mees.jiib.command.SetGenericFanArgs
import works.mees.jiib.command.SetHeaterArgs
import works.mees.jiib.command.SetLedArgs
import works.mees.jiib.command.SetOutputPinArgs
import works.mees.jiib.command.SetServoArgs
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.HsvSliders
import works.mees.jiib.designsystem.Severity
import works.mees.jiib.designsystem.SeverityToast
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.Scrubber
import works.mees.jiib.designsystem.components.footAction
import works.mees.jiib.control.ControlSpecs
import works.mees.jiib.designsystem.hsvToRgb
import works.mees.jiib.designsystem.rgbToHsv
import works.mees.jiib.di.AppContainer
import works.mees.jiib.outputs.OutputRowVm
import works.mees.jiib.outputs.OutputsHolder

/**
 * The per-output-type inline Focus control surface (D-18/D-19), hosted inside a `FocusFrame` in the
 * Outputs screen's Focus region. Switches on [output.descriptor.family] and renders the correct
 * in-place control — no [works.mees.jiib.designsystem.layout.ScreenScaffold] wrapping (the FocusFrame
 * IS the surface). Dispatch logic migrated VERBATIM from the three deleted detail pages.
 *
 * ## Output type routing
 *  - **fan_generic / pwm_tool** — the 004 [Scrubber] (0..100 %), settle-on-gesture-end; dispatch
 *    routes through `PrinterCommands.outputPctToWire` + `OutputsHolder.markPending` (P17 clamp authority).
 *  - **servo** — the 004 [Scrubber] (0..servoAngleMax°), settle-on-gesture-end; `clampServoAngle` before `markPending`.
 *  - **heater_generic** — the 004 [Scrubber] (0..MAX_TEMP_C °C), settle-on-gesture-end; `clampHeaterTarget`.
 *  - **output_pin (digital)** — On/Off toggle via [OutputToggleControl] dispatch logic.
 *  - **output_pin (PWM)** — the 004 [Scrubber] 0..100%, settle-on-gesture-end.
 *  - **led/neopixel/dotstar/pca9533/pca9632** — theme-style H/S/V sliders (+ an independent White
 *    track for RGBW) via [HsvSliders], or a single brightness [Scrubber] for a white-only LED; foot
 *    `[Off]`. Channel gating (hide-not-grey) by `ledHasRgb`/`ledHasWhite`. Full-state SET_LED dispatch.
 *
 * ## Build-once scrubber rule (SC-3 / P19)
 * `Scrubber` is hosted WITHOUT a `key(output.currentPct)` wrapper — the P19 build-once rule.
 * `Scrubber`'s internal `working` state is seeded via `remember(value, range)` and updates in
 * place on drag; wrapping in `key(…)` would force a rebuild mid-drag (the fa97efb fill-from-middle /
 * value-not-sticking regression).
 *
 * ## P17 clamp authority (D-22)
 * Every `onSettle` calls `PrinterCommands.clamp*`/`outputPctToWire` BEFORE `markPending` so the
 * optimistic flip target equals the wire value. `OutputsHolder.markPending` is never called with an
 * unclamped value.
 *
 * @param output    the live row VM (descriptor + display values for seeding).
 * @param holder    the [OutputsHolder] owning per-output busy lock + `markPending`/`clearPending`.
 * @param container the service-locator (live dispatcher + in-flight set for busy lock).
 */
@Composable
fun OutputFocusControl(
    output: OutputRowVm,
    holder: OutputsHolder,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    // Derive unit U here so FocusScrubberSurface / FocusLedSurface can cap controls at ≤1U (UAT-5).
    // BoxWithConstraints is the standard call-site pattern for rememberUnitGrid; it resolves ONCE
    // and the uDp is passed explicitly (no CompositionLocal per Phase-23 Open Q §1 decision).
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        OutputFocusControlInner(
            output = output,
            holder = holder,
            container = container,
            uDp = grid.uDp,
        )
    }
}

@Composable
private fun OutputFocusControlInner(
    output: OutputRowVm,
    holder: OutputsHolder,
    container: AppContainer,
    uDp: Dp,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    var failureText by remember { mutableStateOf<String?>(null) }

    // Watch for dispatch failures from the live dispatcher — clear the busy lock and toast.
    LaunchedEffect(dispatcher) {
        failureText = null
        val d = dispatcher ?: return@LaunchedEffect
        d.events.collect { event ->
            when (event) {
                is DispatchEvent.Failure -> {
                    failureText = event.message
                    holder.clearPending(output.descriptor.objectKey)
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

    val descriptor = output.descriptor
    val family = descriptor.family

    when {
        // ---- fan_generic (0..100%) ---------------------------------------------------------------
        family == OutputsHolder.FAMILY_FAN -> {
            val key = "set_output_fan_${descriptor.commandName}"
            val busy = key in inFlight
            val currentPct = (output.displayValue?.removeSuffix("%")?.toFloatOrNull() ?: 0f)
                .coerceIn(0f, 100f)

            fun dispatchFan(pct: Int) {
                holder.markPending(descriptor.objectKey, PrinterCommands.outputPctToWire(pct))
                dispatchCommand(
                    CommandRegistry.setGenericFan,
                    SetGenericFanArgs(descriptor.commandName, pct),
                )
            }

            FocusScrubberSurface(
                value = currentPct,
                range = 0f..100f,
                step = 1f,
                unit = "%",
                busy = busy,
                failureText = failureText,
                onSettle = { v -> if (!busy) dispatchFan(v.roundToInt()) },
                onOff = { dispatchFan(0) },
                uDp = uDp,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ---- servo (0..servoAngleMax°) -----------------------------------------------------------
        family == OutputsHolder.FAMILY_SERVO -> {
            val key = "set_output_servo_${descriptor.commandName}"
            val busy = key in inFlight

            fun dispatchServo(deg: Int) {
                holder.markPending(
                    descriptor.objectKey,
                    PrinterCommands.clampServoAngle(
                        deg, descriptor.servoAngleMax.roundToInt()
                    ).toDouble(),
                )
                dispatchCommand(
                    CommandRegistry.setServo,
                    SetServoArgs(
                        descriptor.commandName,
                        deg = deg,
                        maxDeg = descriptor.servoAngleMax.roundToInt(),
                    ),
                )
            }

            fun dispatchServoOff() {
                holder.markPending(descriptor.objectKey, 0.0)
                dispatchCommand(
                    CommandRegistry.setServo,
                    SetServoArgs(descriptor.commandName, disable = true),
                )
            }

            FocusScrubberSurface(
                value = 0f,  // servo angle can't be read back (PWM value != angle — SC-3)
                range = 0f..descriptor.servoAngleMax,
                step = 5f,
                unit = "°",
                busy = busy,
                failureText = failureText,
                onSettle = { v -> if (!busy) dispatchServo(v.roundToInt()) },
                onOff = { dispatchServoOff() },
                uDp = uDp,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ---- heater_generic (0..MAX_TEMP_C °C) --------------------------------------------------
        family == OutputsHolder.FAMILY_HEATER -> {
            val key = "set_${descriptor.commandName}"
            val busy = key in inFlight
            val currentTemp = (output.displayValue?.removeSuffix("°C")?.toFloatOrNull() ?: 0f)
                .coerceIn(0f, PrinterCommands.MAX_TEMP_C.toFloat())

            fun dispatchHeater(degC: Int) {
                holder.markPending(
                    descriptor.objectKey,
                    PrinterCommands.clampHeaterTarget(degC).toDouble(),
                )
                dispatchCommand(
                    CommandRegistry.setHeater,
                    SetHeaterArgs(descriptor.commandName, degC),
                )
            }

            FocusScrubberSurface(
                value = currentTemp,
                range = 0f..PrinterCommands.MAX_TEMP_C.toFloat(),
                step = 5f,
                unit = "°C",
                busy = busy,
                failureText = failureText,
                onSettle = { v -> if (!busy) dispatchHeater(v.roundToInt()) },
                onOff = { dispatchHeater(0) },
                uDp = uDp,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ---- pwm_tool (0..100%) -----------------------------------------------------------------
        family == OutputsHolder.FAMILY_PWM_TOOL -> {
            val key = "set_output_pin_${descriptor.commandName}"
            val busy = key in inFlight
            val currentPct = (output.displayValue?.removeSuffix("%")?.toFloatOrNull() ?: 0f)
                .coerceIn(0f, 100f)

            fun dispatchPwmTool(pct: Int) {
                holder.markPending(descriptor.objectKey, PrinterCommands.outputPctToWire(pct))
                dispatchCommand(
                    CommandRegistry.setOutputPin,
                    SetOutputPinArgs(descriptor.commandName, pwm = true, pct = pct),
                )
            }

            FocusScrubberSurface(
                value = currentPct,
                range = 0f..100f,
                step = 1f,
                unit = "%",
                busy = busy,
                failureText = failureText,
                onSettle = { v -> if (!busy) dispatchPwmTool(v.roundToInt()) },
                onOff = { dispatchPwmTool(0) },
                uDp = uDp,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ---- led / neopixel / dotstar / pca9533 / pca9632 (D-19) --------------------------------
        family in OutputsHolder.LED_FAMILIES -> {
            val key = "set_output_led_${descriptor.commandName}"
            val busy = key in inFlight

            // Full-state dispatch: SET_LED zeroes any channel not sent (klippy/extras/led.py), so we
            // always send the complete r/g/b/w. RGB/RGBW → from H/S/V (+ white when present); a
            // WHITE-ONLY strip must send RGB=0 (NOT hsvToRgb(0,0,v), which is grey) + the white channel.
            fun dispatchLed(h: Float, s: Float, v: Float, whitePct: Float) {
                val white = if (descriptor.ledHasWhite) (whitePct / 100f).coerceIn(0f, 1f) else null
                val args = if (descriptor.ledHasRgb) {
                    ledChannelsFromHsv(descriptor.commandName, h, s, v, white)
                } else {
                    SetLedArgs(descriptor.commandName, 0f, 0f, 0f, w = white)
                }
                val targetChannels = ledTargetChannels(args)
                holder.markPending(
                    descriptor.objectKey,
                    clampedWireTarget = targetChannels.maxOrNull() ?: 0.0,
                    targetChannels = targetChannels,
                )
                dispatchCommand(CommandRegistry.setLed, args)
            }

            fun dispatchOff() {
                holder.markPending(
                    descriptor.objectKey, 0.0,
                    targetChannels = listOf(0.0, 0.0, 0.0, 0.0),
                )
                dispatchCommand(
                    CommandRegistry.setLed,
                    SetLedArgs(descriptor.commandName, 0f, 0f, 0f, w = if (descriptor.ledHasWhite) 0f else null),
                )
            }

            FocusLedSurface(
                ledHasRgb = descriptor.ledHasRgb,
                ledHasWhite = descriptor.ledHasWhite,
                channels = output.ledChannels,
                busy = busy,
                failureText = failureText,
                onSettle = { h, s, v, whitePct -> if (!busy) dispatchLed(h, s, v, whitePct) },
                onOff = { if (!busy) dispatchOff() },
                uDp = uDp,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ---- output_pin (digital or PWM) --------------------------------------------------------
        else -> {
            if (descriptor.pwm) {
                // PWM output_pin (0..100%)
                val key = "set_output_pin_${descriptor.commandName}"
                val busy = key in inFlight
                val currentPct = (output.displayValue?.removeSuffix("%")?.toFloatOrNull() ?: 0f)
                    .coerceIn(0f, 100f)

                fun dispatchPwmPin(pct: Int) {
                    holder.markPending(descriptor.objectKey, PrinterCommands.outputPctToWire(pct))
                    dispatchCommand(
                        CommandRegistry.setOutputPin,
                        SetOutputPinArgs(descriptor.commandName, pwm = true, pct = pct),
                    )
                }

                FocusScrubberSurface(
                        value = currentPct,
                    range = 0f..100f,
                    step = 1f,
                    unit = "%",
                    busy = busy,
                    failureText = failureText,
                    onSettle = { v -> if (!busy) dispatchPwmPin(v.roundToInt()) },
                    onOff = { dispatchPwmPin(0) },
                        uDp = uDp,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Digital output_pin (On/Off toggle)
                val key = "set_output_pin_${descriptor.commandName}"
                val busy = key in inFlight
                val isOn: Boolean? = when (output.displayValue) {
                    "On" -> true
                    "Off" -> false
                    else -> null
                }

                fun setDigital(on: Boolean) {
                    holder.markPending(descriptor.objectKey, if (on) 1.0 else 0.0)
                    dispatchCommand(
                        CommandRegistry.setOutputPin,
                        SetOutputPinArgs(descriptor.commandName, pwm = false, on = on),
                    )
                }

                OutputToggleControl(
                        isOn = isOn,
                    readOnly = descriptor.readOnly,
                    enabled = !busy,
                    failureText = failureText,
                    onOn = { setDigital(true) },
                    onOff = { setDigital(false) },
                    uDp = uDp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Convert a full HSV color (+ optional independent white channel) into a [SetLedArgs] for [name].
 * White is passed only when the LED has a white channel ([white] non-null) so `SET_LED` omits `WHITE=`
 * on plain RGB strips ([PrinterCommands.setLed] appends WHITE only for a non-null w). Pure + host-testable.
 */
internal fun ledChannelsFromHsv(name: String, h: Float, s: Float, v: Float, white: Float?): SetLedArgs {
    val (r, g, b) = hsvToRgb(h, s, v)
    return SetLedArgs(name, r, g, b, w = white)
}

/**
 * The r/g/b/w target the optimistic pending flip waits on — rounded to the SAME 2dp the wire carries
 * ([PrinterCommands.setLed] formats `%.2f`). The printer echoes the rounded values in `color_data`, so
 * comparing against the raw HSV floats could miss by ~epsilon at half-step values and wedge the busy
 * lock until the timeout backstop. Pure + host-testable.
 */
internal fun ledTargetChannels(args: SetLedArgs): List<Double> =
    // Math.round = HALF_UP (floor(x+0.5)) to match String.format("%.2f"); kotlin.math.round is
    // half-to-even and would disagree at exact half-steps (0.125 → 0.12 vs the wire's 0.13).
    listOf(args.r, args.g, args.b, args.w ?: 0f).map { Math.round(it.toDouble() * 100.0) / 100.0 }

/**
 * Shared scrubber surface for fan / servo / heater / PWM outputs, hosted inline in the Focus.
 * Uses the 004 ringed-thumb [Scrubber] (R9 — NO ScreenScaffold, NO background). The scrubber fills
 * the body (centered in the slack) and the `[Off]` foot bar is pinned to the bottom.
 *
 * ## Build-once rule (P19 SC-3)
 * [Scrubber] is placed WITHOUT a `key(value)` wrapper so its internal `working` state
 * survives across live value updates and recomposes. The `remember(value, range)` inside
 * [Scrubber] seeds the state once on entry and when the range changes — NEVER on every frame.
 */
@Composable
private fun FocusScrubberSurface(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    unit: String,
    busy: Boolean,
    failureText: String?,
    onSettle: (Float) -> Unit,
    onOff: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // CRITICAL build-once rule (P19 SC-3): Scrubber is NOT wrapped in key(value).
        // Its internal working state is seeded via remember(value, range) — never rebuilt mid-drag.
        // 004 ringed-thumb style (R9). Name-less: the FocusFrame header carries the output's identity,
        // so the Scrubber header shows the live value only (centered, Task 2) — no duplicated name.
        // Weighted body so the foot bar pins to the bottom (matches the LED/switch surfaces).
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Scrubber(
                name = "",
                value = value.coerceIn(range.start, range.endInclusive),
                range = range,
                step = step,
                unit = unit,
                uDp = uDp,
                // Settle: dispatch ONCE on gesture-end / stepper tap; busy guard inside the lambda
                // (the drag stays live while a dispatch is in flight — pre-004 semantics).
                onSettle = { v -> if (!busy) onSettle(v) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
        // Foot = FootButtonBar [Off] (power_off, Warn). No in-Focus Back — the Field list + its Back
        // own navigation; you switch outputs by tapping list rows.
        FootButtonBar(
            uDp = uDp,
            actions = listOf(footAction(ControlSpecs.outputOff, onClick = onOff, enabled = !busy)),
        )
    }
}

/**
 * Inline LED Focus surface — theme-style H/S/V sliders (+ an independent White slider for RGBW) via
 * [HsvSliders]; a white-only LED shows a single brightness [Scrubber]. Foot = [FootButtonBar] `[Off]`.
 * No in-Focus Back (the Field list + its Back own navigation). Hosted directly in the FocusFrame body
 * (no [works.mees.jiib.designsystem.layout.ScreenScaffold]).
 *
 * ## Channel gating (P19 GAP-B — hide-not-grey)
 * RGB sliders render only when [ledHasRgb]; the White slider only when [ledHasWhite] too (RGBW). A
 * white-only LED ([ledHasRgb] false) shows just the brightness scrubber, dispatched as the WHITE channel.
 * Seeded from the strip's live [channels] (r,g,b,w 0..1) so White survives the round-trip.
 */
@Composable
private fun FocusLedSurface(
    ledHasRgb: Boolean,
    ledHasWhite: Boolean,
    channels: List<Float>?,
    busy: Boolean,
    failureText: String?,
    onSettle: (h: Float, s: Float, v: Float, whitePct: Float) -> Unit,
    onOff: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    // Seed H/S/V + White from the live channels [r,g,b,w]. White is its own axis (NOT derived from HSV).
    val seed = remember(channels) {
        val r = channels?.getOrNull(0) ?: 0f
        val g = channels?.getOrNull(1) ?: 0f
        val b = channels?.getOrNull(2) ?: 0f
        val w = channels?.getOrNull(3) ?: 0f
        val (h, s, v) = rgbToHsv(r, g, b)
        floatArrayOf(h, s, v, w)
    }
    var h by remember(seed) { mutableFloatStateOf(seed[0]) }
    var s by remember(seed) { mutableFloatStateOf(seed[1]) }
    var v by remember(seed) { mutableFloatStateOf(seed[2]) }
    var w by remember(seed) { mutableFloatStateOf(seed[3]) }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (ledHasRgb) {
                // Full HSV (+ White for RGBW). Each settle sends the COMPLETE r/g/b/w state.
                HsvSliders(
                    hue = h, sat = s, value = v,
                    onMove = { nh, ns, nv -> h = nh; s = ns; v = nv },
                    onSettle = { nh, ns, nv -> h = nh; s = ns; v = nv; if (!busy) onSettle(nh, ns, nv, w * 100f) },
                    white = if (ledHasWhite) w else null,
                    onWhiteMove = { w = it },
                    onWhiteSettle = { nw -> w = nw; if (!busy) onSettle(h, s, v, nw * 100f) },
                    enabled = !busy,
                )
            } else {
                // White-only LED: a single brightness scrubber (name-less → centered value, Task 2).
                Scrubber(
                    name = "",
                    value = w * 100f,
                    range = 0f..100f,
                    step = 1f,
                    unit = "%",
                    uDp = uDp,
                    enabled = !busy,
                    onValueChange = { w = it / 100f },
                    onSettle = { settled -> w = settled / 100f; if (!busy) onSettle(0f, 0f, settled / 100f, settled) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
        FootButtonBar(
            uDp = uDp,
            actions = listOf(footAction(ControlSpecs.outputOff, onClick = onOff, enabled = !busy)),
        )
    }
}

