package works.mees.dinghy.ui.outputs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.designsystem.fractionFromX
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.CommandSpec
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.command.SetGenericFanArgs
import works.mees.dinghy.command.SetHeaterArgs
import works.mees.dinghy.command.SetLedArgs
import works.mees.dinghy.command.SetOutputPinArgs
import works.mees.dinghy.command.SetServoArgs
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.ColorWheel
import works.mees.dinghy.designsystem.ScrubberActions
import works.mees.dinghy.designsystem.ScrubberControl
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.hsvToRgb
import works.mees.dinghy.designsystem.rgbToHsv
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.outputs.OutputRowVm
import works.mees.dinghy.outputs.OutputsHolder
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The per-output-type inline Focus control surface (D-18/D-19), hosted inside a `DetailCard` in the
 * Outputs screen's Focus region. Switches on [output.descriptor.family] and renders the correct
 * in-place control — no [works.mees.dinghy.designsystem.layout.ScreenScaffold] wrapping (the DetailCard
 * IS the surface). Dispatch logic migrated VERBATIM from the three deleted detail pages.
 *
 * ## Output type routing
 *  - **fan_generic / pwm_tool** — `ScrubberControl` (0..100 %) via [ScrubberActions.OnSettle]; dispatch
 *    routes through `PrinterCommands.outputPctToWire` + `OutputsHolder.markPending` (P17 clamp authority).
 *  - **servo** — `ScrubberControl` (0..servoAngleMax°) via OnSettle; `clampServoAngle` before `markPending`.
 *  - **heater_generic** — `ScrubberControl` (0..MAX_TEMP_C °C) via OnSettle; `clampHeaterTarget`.
 *  - **output_pin (digital)** — On/Off toggle via [OutputToggleControl] dispatch logic.
 *  - **output_pin (PWM)** — `ScrubberControl` 0..100% via OnSettle.
 *  - **led/neopixel/dotstar/pca9533/pca9632** — brightness `ScrubberControl` + P19 GAP-B capability-gated
 *    hue `ColorWheel` + Off. Channel gating (hide-not-grey) preserved verbatim from OutputLedDetail.
 *
 * ## Build-once scrubber rule (SC-3 / P19)
 * `ScrubberControl` is hosted WITHOUT a `key(output.currentPct)` wrapper — the P19 build-once rule.
 * `ScrubberControl`'s internal `working` state is seeded via `remember(value, range)` and updates in
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
 * @param onBack    exit — called when the Back foot button is tapped; the caller sets selectedKey = null.
 */
@Composable
fun OutputFocusControl(
    output: OutputRowVm,
    holder: OutputsHolder,
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
                prettyName = descriptor.prettyName,
                value = currentPct,
                range = 0f..100f,
                step = 1f,
                unit = "%",
                busy = busy,
                failureText = failureText,
                onSettle = { v -> if (!busy) dispatchFan(v.roundToInt()) },
                onOff = { dispatchFan(0) },
                onBack = onBack,
                modifier = modifier,
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
                prettyName = descriptor.prettyName,
                value = 0f,  // servo angle can't be read back (PWM value != angle — SC-3)
                range = 0f..descriptor.servoAngleMax,
                step = 5f,
                unit = "°",
                busy = busy,
                failureText = failureText,
                onSettle = { v -> if (!busy) dispatchServo(v.roundToInt()) },
                onOff = { dispatchServoOff() },
                onBack = onBack,
                modifier = modifier,
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
                prettyName = descriptor.prettyName,
                value = currentTemp,
                range = 0f..PrinterCommands.MAX_TEMP_C.toFloat(),
                step = 5f,
                unit = "°C",
                busy = busy,
                failureText = failureText,
                onSettle = { v -> if (!busy) dispatchHeater(v.roundToInt()) },
                onOff = { dispatchHeater(0) },
                onBack = onBack,
                modifier = modifier,
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
                prettyName = descriptor.prettyName,
                value = currentPct,
                range = 0f..100f,
                step = 1f,
                unit = "%",
                busy = busy,
                failureText = failureText,
                onSettle = { v -> if (!busy) dispatchPwmTool(v.roundToInt()) },
                onOff = { dispatchPwmTool(0) },
                onBack = onBack,
                modifier = modifier,
            )
        }

        // ---- led / neopixel / dotstar / pca9533 / pca9632 (D-19) --------------------------------
        family in OutputsHolder.LED_FAMILIES -> {
            val key = "set_output_led_${descriptor.commandName}"
            val busy = key in inFlight

            fun dispatchColor(hue: Float, brightnessPct: Float) {
                val (r, g, b) = hsvToRgb(hue, 1f, (brightnessPct / 100f).coerceIn(0f, 1f))
                holder.markPending(descriptor.objectKey, maxOf(r, g, b).toDouble())
                // WHITE-CHANNEL POLICY: every color dispatch sends WHITE=0 (T-19-06-05).
                dispatchCommand(
                    CommandRegistry.setLed,
                    SetLedArgs(descriptor.commandName, r, g, b, w = 0f),
                )
            }

            // GAP-B: a white-only LED dispatches the WHITE channel (RGB=0).
            fun dispatchWhite(brightnessPct: Float) {
                val white = (brightnessPct / 100f).coerceIn(0f, 1f)
                holder.markPending(descriptor.objectKey, white.toDouble())
                dispatchCommand(
                    CommandRegistry.setLed,
                    SetLedArgs(descriptor.commandName, 0f, 0f, 0f, w = white),
                )
            }

            fun dispatchOff() {
                holder.markPending(descriptor.objectKey, 0.0)
                dispatchCommand(
                    CommandRegistry.setLed,
                    SetLedArgs(descriptor.commandName, 0f, 0f, 0f, w = 0f),
                )
            }

            FocusLedSurface(
                prettyName = descriptor.prettyName,
                ledHasRgb = descriptor.ledHasRgb,
                ledHasWhite = descriptor.ledHasWhite,
                swatchArgb = output.swatchColor,
                busy = busy,
                failureText = failureText,
                onColorSettle = { hue, brightness -> if (!busy) dispatchColor(hue, brightness) },
                onWhiteSettle = { brightness -> if (!busy) dispatchWhite(brightness) },
                onOff = { if (!busy) dispatchOff() },
                onBack = onBack,
                modifier = modifier,
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
                    prettyName = descriptor.prettyName,
                    value = currentPct,
                    range = 0f..100f,
                    step = 1f,
                    unit = "%",
                    busy = busy,
                    failureText = failureText,
                    onSettle = { v -> if (!busy) dispatchPwmPin(v.roundToInt()) },
                    onOff = { dispatchPwmPin(0) },
                    onBack = onBack,
                    modifier = modifier,
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
        }
    }
}

/**
 * Shared scrubber surface for fan / servo / heater / PWM outputs, hosted inline in the Focus.
 * Uses [ScrubberControl] (the embeddable gesture+state core — NO ScreenScaffold, NO background).
 * The Off/Back row is rendered directly below the control (not in a separate gutter).
 *
 * ## Build-once rule (P19 SC-3)
 * [ScrubberControl] is placed WITHOUT a `key(value)` wrapper so its internal `working` state
 * survives across live value updates and recomposes. The `remember(value, range)` inside
 * [ScrubberControl] seeds the state once on entry and when the range changes — NEVER on every frame.
 */
@Composable
private fun FocusScrubberSurface(
    prettyName: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    unit: String,
    busy: Boolean,
    failureText: String?,
    onSettle: (Float) -> Unit,
    onOff: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = prettyName,
            color = t.text2,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Medium,
            fontSize = fsSp(17f, t.fs).sp,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        // CRITICAL build-once rule (P19 SC-3): ScrubberControl is NOT wrapped in key(value).
        // Its internal working state is seeded via remember(value, range) — never rebuilt mid-drag.
        ScrubberControl(
            label = prettyName,
            value = value.coerceIn(range.start, range.endInclusive),
            range = range,
            step = step,
            unit = unit,
            // OnSettle: dispatch ONCE on gesture-end / stepper tap; enabled guard inside lambda.
            actions = ScrubberActions.OnSettle(
                onSettle = { v -> if (!busy) onSettle(v) },
                onBack = onBack,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
        // Foot row: Off (Danger/red) + Back (Neutral). Mirrors the ScrubberPage OnSettle gutter.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedControl(
                label = stringResource(R.string.output_off),
                onClick = { if (!busy) onOff() },
                modifier = Modifier.weight(1f),
                intent = Intent.Danger,
            )
            OutlinedControl(
                label = stringResource(R.string.common_back),
                onClick = onBack,
                modifier = Modifier.weight(1f),
                intent = Intent.Neutral,
            )
        }
    }
}

/**
 * Inline LED Focus surface (D-19) — brightness [ScrubberControl] + P19 GAP-B capability-gated hue
 * [ColorWheel] + Off/Back row. All hosted without a [works.mees.dinghy.designsystem.layout.ScreenScaffold].
 *
 * ## Channel gating (P19 GAP-B — hide-not-grey)
 * The hue wheel renders ONLY when [ledHasRgb] is true. A white/brightness-only LED shows the
 * brightness control alone — no hue wheel — and dispatches the WHITE channel so the value actually
 * reaches the hardware. This matches OutputLedContent's capability check verbatim.
 */
@Composable
private fun FocusLedSurface(
    prettyName: String,
    ledHasRgb: Boolean,
    ledHasWhite: Boolean,
    swatchArgb: Long?,
    busy: Boolean,
    failureText: String?,
    onColorSettle: (hue: Float, brightnessPct: Float) -> Unit,
    onWhiteSettle: (brightnessPct: Float) -> Unit,
    onOff: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    // Seed hue + brightness from the live color data (swatchArgb encodes the strip's packed RGB).
    // Unpack from ARGB long: R=(argb >> 16) & 0xFF, G=(argb >> 8) & 0xFF, B=argb & 0xFF.
    val (seedHue, seedBrightness) = remember(swatchArgb, ledHasRgb) {
        if (swatchArgb == null) {
            0f to 0f
        } else {
            val r = ((swatchArgb shr 16) and 0xFF).toFloat() / 255f
            val g = ((swatchArgb shr 8) and 0xFF).toFloat() / 255f
            val b = (swatchArgb and 0xFF).toFloat() / 255f
            val (h, _, v) = rgbToHsv(r, g, b)
            if (ledHasRgb) h to (v * 100f) else 0f to (v * 100f)
        }
    }

    var hue by remember(seedHue) { mutableFloatStateOf(seedHue) }
    var brightness by remember(seedBrightness) { mutableFloatStateOf(seedBrightness) }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = prettyName,
            color = t.text2,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Medium,
            fontSize = fsSp(17f, t.fs).sp,
        )
        // GAP-B: the hue wheel renders ONLY for an RGB-capable LED.
        if (ledHasRgb) {
            ColorWheel(
                hue = hue,
                onHandleMove = { hue = it },
                onSettle = { settled ->
                    hue = settled
                    if (!busy) onColorSettle(settled, brightness)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Brightness fill-bar (inline, mirrors LedBrightnessControl from OutputLedDetail).
        // CRITICAL: build-once — NOT wrapped in key(brightness); LedBrightnessControl uses
        // remember(value) internally so it survives live value updates without re-creation.
        LedBrightnessControl(
            value = brightness,
            enabled = !busy,
            onValueChange = { brightness = it },
            onSettle = { settled ->
                brightness = settled
                if (!busy) {
                    if (ledHasRgb) onColorSettle(hue, settled) else onWhiteSettle(settled)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
        // Foot row: Off (Danger/red) + Back (Neutral) — mirrors OutputLedContent's gutter verbatim.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedControl(
                label = stringResource(R.string.output_off),
                onClick = { if (!busy) onOff() },
                modifier = Modifier.weight(1f),
                intent = Intent.Danger,
            )
            OutlinedControl(
                label = stringResource(R.string.common_back),
                onClick = onBack,
                modifier = Modifier.weight(1f),
                intent = Intent.Neutral,
            )
        }
    }
}

/**
 * LED brightness fill-bar + ± stepper — migrated from the deleted OutputLedDetail.kt. Renders a
 * horizontal accent-filled bar (LEFT-anchored fill, no center-expand) and a ± stepper row below.
 * Dispatch-once-per-settle (gesture-end / stepper tap). Internal `working` state seeded via
 * `remember(value)` — NOT wrapped in `key(value)` (P19 build-once rule).
 *
 * @param value         seed brightness (0..100 %).
 * @param onValueChange notified on drag/stepper; does NOT dispatch — caller holds dispatch.
 * @param onSettle      called once on gesture-end / stepper tap with the settled value.
 * @param enabled       false while a dispatch is in-flight (busy lock).
 */
@Composable
private fun LedBrightnessControl(
    value: Float,
    onValueChange: (Float) -> Unit,
    onSettle: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val t = LocalTokens.current
    val range = 0f..100f
    val step = 1f
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    // Internal working value — seeded from [value]; re-seeds on a live state flip.
    // Reads `working` at gesture-settle so the dragged-to value is dispatched, not the stale seed.
    //
    // CR-04 (26-rev): same stale-closure hardening as ScrubberControl — ONE stable working state
    // object (re-seeded IN PLACE when [value] changes, P19 build-once) plus rememberUpdatedState
    // routing, so the long-lived awaitEachGesture handler never writes a dead state object after a
    // re-seed and never dispatches through the lambdas captured when the handler started.
    val workingState = remember { mutableFloatStateOf(value.coerceIn(range.start, range.endInclusive)) }
    remember(value) { workingState.floatValue = value.coerceIn(range.start, range.endInclusive) }
    var working by workingState
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnSettle by rememberUpdatedState(onSettle)

    fun set(next: Float) {
        if (!enabled) return
        val clamped = next.coerceIn(range.start, range.endInclusive)
        working = clamped
        currentOnValueChange(clamped)
    }

    fun setFromX(x: Float) {
        if (barWidthPx <= 0f) return
        set(range.start + fractionFromX(x, barWidthPx) * (range.endInclusive - range.start))
    }

    fun settle() {
        if (enabled) currentOnSettle(working)
    }

    val fraction = ((working - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val display = working.roundToInt().toString()

    Column(modifier) {
        // Horizontal fill-bar scrubber — LEFT-anchored fill (no center-expand regression).
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(t.rCard))
                .background(t.surface2)
                .border(BorderStroke(2.dp, t.outline), RoundedCornerShape(t.rCard))
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        setFromX(down.position.x)
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    setFromX(change.position.x)
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        // Gesture END — settle once; never per move frame (P19 rule).
                        settle()
                    }
                },
            // NO contentAlignment: fill MUST anchor to start edge and grow rightward.
        ) {
            // Accent fill, LEFT-anchored (TopStart default).
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(t.accentSoft),
            )
            // Centered value overlay.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "$display%",
                    color = t.text,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(40f, t.fs).sp,
                )
            }
        }
        // ± stepper row — each tap is a discrete adjust + settle.
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedControl(
                label = "−",
                onClick = { set(working - step); settle() },
                modifier = Modifier.weight(1f),
                intent = Intent.Neutral,
            )
            OutlinedControl(
                label = "+",
                onClick = { set(working + step); settle() },
                modifier = Modifier.weight(1f),
                intent = Intent.Neutral,
            )
        }
    }
}
