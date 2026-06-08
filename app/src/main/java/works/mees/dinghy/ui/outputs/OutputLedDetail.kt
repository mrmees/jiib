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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.CommandSpec
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.SetLedArgs
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.ColorWheel
import works.mees.dinghy.designsystem.fractionFromX
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.hsvToRgb
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.rgbToHsv
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.outputs.OutputDescriptor
import works.mees.dinghy.outputs.OutputsHolder
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The LED detail page (SC-2, D-12). Pairs the hue-only [ColorWheel] with a brightness [ScrubberPage]
 * (OnSettle, 0..100 %) — v1 fixes saturation = 1.0 ([hsvToRgb]'s default, RESEARCH Open Q1 / A4 — reuses the
 * hue wheel verbatim). The page SEEDs its hue + brightness from the live `color_data[0]` (the whole-strip
 * `[r,g,b,w]`) via the [rgbToHsv] inverse so it reflects the printer's current color, and the swatch chip
 * shows that same color.
 *
 * On settle (the wheel's `onSettle` OR the brightness scrubber's onSettle) it computes RGB via
 * `hsvToRgb(hue, 1f, brightness/100f)` and dispatches THROUGH the catalog (HIGH-2) [CommandRegistry.setLed]
 * with `w = 0f` — the WHITE-CHANNEL POLICY (review MEDIUM / T-19-06-05): EVERY color dispatch sends WHITE=0 so
 * a stale white channel never lingers behind a chosen hue. The built command uses the BARE [OutputDescriptor
 * .commandName] (HIGH-1). An explicit Off (D-12) dispatches all-zero `setLed(name, 0, 0, 0, w = 0)`
 * immediately. Settle-dispatch only (no per-frame wire spam, Adreno-320 budget); failure → [SeverityToast] +
 * clearPending and the user STAYS on the page; busy per-objectKey. NO confirm-guard step, NO Apply flow.
 *
 * The hue ring + handle + the current-color swatch render their LITERAL color (THEME-01 data-color carve-out,
 * same as the spool filament color); ALL other chrome routes through [LocalTokens].
 */
@Composable
fun OutputLedDetail(
    container: AppContainer,
    holder: OutputsHolder,
    descriptor: OutputDescriptor,
    colorData: List<Double>?,
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

    val key = "set_output_led_${descriptor.commandName}"
    val busy = key in inFlight

    fun <P> dispatchCommand(command: CommandSpec<P>, args: P) {
        if (command.dispatchKey(args) in inFlight) return
        dispatcher?.dispatch(command, args)
    }

    // Seed hue + brightness from the live color_data[0] (review MEDIUM — reflect the live strip).
    val (seedHue, seedBrightness) = remember(colorData) {
        val r = colorData?.getOrNull(0)?.toFloat() ?: 0f
        val g = colorData?.getOrNull(1)?.toFloat() ?: 0f
        val b = colorData?.getOrNull(2)?.toFloat() ?: 0f
        val (h, _, v) = rgbToHsv(r, g, b)
        h to (v * 100f)
    }

    // GAP-B: a white-only LED seeds its brightness from the live WHITE component (color_data[0][3]),
    // NOT the brightest RGB channel (which is 0 on a white-only light).
    val seedWhiteBrightness = remember(colorData) {
        (colorData?.getOrNull(3)?.toFloat() ?: 0f) * 100f
    }

    fun dispatchColor(hue: Float, brightnessPct: Float) {
        val (r, g, b) = hsvToRgb(hue, 1f, (brightnessPct / 100f).coerceIn(0f, 1f))
        // markPending the LED's reached() target = the brightest channel (matches OutputsHolder.reached()).
        holder.markPending(descriptor.objectKey, maxOf(r, g, b).toDouble())
        // WHITE-CHANNEL POLICY: every color dispatch sends WHITE=0 (T-19-06-05).
        dispatchCommand(CommandRegistry.setLed, SetLedArgs(descriptor.commandName, r, g, b, w = 0f))
    }

    // GAP-B: a white-only LED dispatches the WHITE channel (RGB=0). The white fraction is the max channel
    // when r=g=b=0, so markPending it to match OutputsHolder.reached() (color_data[0].max()).
    fun dispatchWhite(brightnessPct: Float) {
        val white = (brightnessPct / 100f).coerceIn(0f, 1f)
        holder.markPending(descriptor.objectKey, white.toDouble())
        dispatchCommand(CommandRegistry.setLed, SetLedArgs(descriptor.commandName, 0f, 0f, 0f, w = white))
    }

    fun dispatchOff() {
        holder.markPending(descriptor.objectKey, 0.0)
        dispatchCommand(CommandRegistry.setLed, SetLedArgs(descriptor.commandName, 0f, 0f, 0f, w = 0f))
    }

    OutputLedContent(
        prettyName = descriptor.prettyName,
        ledHasRgb = descriptor.ledHasRgb,
        ledHasWhite = descriptor.ledHasWhite,
        initialHue = seedHue,
        initialBrightness = if (descriptor.ledHasRgb) seedBrightness else seedWhiteBrightness,
        enabled = !busy,
        failureText = failureText,
        onColorSettle = { hue, brightness -> if (busy.not()) dispatchColor(hue, brightness) },
        onWhiteSettle = { brightness -> if (busy.not()) dispatchWhite(brightness) },
        onOff = { if (busy.not()) dispatchOff() },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * The pure, container-free LED rendering surface shared by the live entry and the @Preview seam. It branches
 * on channel capability (GAP-B):
 *  - RGB-capable ([ledHasRgb] = true): hue wheel + brightness + swatch; a settle dispatches the CURRENT
 *    hue+brightness pair via [onColorSettle] (WHITE=0, unchanged from 19-09).
 *  - white/brightness-only ([ledHasRgb] = false): NO hue wheel — a brightness control alone + a grey/white
 *    swatch; a settle dispatches the WHITE channel via [onWhiteSettle].
 * The hue/brightness working state lives here. [ledHasWhite] is informational for now (RGBW lights still use
 * the RGB wheel in v1 per the UAT fix direction).
 */
@Composable
fun OutputLedContent(
    prettyName: String,
    initialHue: Float,
    initialBrightness: Float,
    enabled: Boolean,
    failureText: String?,
    onColorSettle: (hue: Float, brightnessPct: Float) -> Unit,
    onOff: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    ledHasRgb: Boolean = true,
    ledHasWhite: Boolean = false,
    onWhiteSettle: (brightnessPct: Float) -> Unit = {},
) {
    val t = LocalTokens.current
    var hue by remember(initialHue) { mutableFloatStateOf(initialHue) }
    var brightness by remember(initialBrightness) { mutableFloatStateOf(initialBrightness) }

    // The live preview swatch — the LITERAL chosen color (THEME-01 data-color carve-out). For an RGB LED it
    // is the chosen hue; for a white-only LED it is a grey/white scaled by the brightness (GAP-B — not a hue).
    val swatch = if (ledHasRgb) {
        val (pr, pg, pb) = hsvToRgb(hue, 1f, (brightness / 100f).coerceIn(0f, 1f))
        Color(pr, pg, pb)
    } else {
        val w = (brightness / 100f).coerceIn(0f, 1f)
        Color(w, w, w)
    }

    Box(modifier.fillMaxSize().background(t.bg)) {
        ScreenScaffold(
            field = {
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = prettyName,
                            color = t.text2,
                            fontFamily = GeistMono,
                            fontWeight = FontWeight.Medium,
                            fontSize = fsSp(20f, t.fs).sp,
                            modifier = Modifier.weight(1f),
                        )
                        // Current-color swatch (literal data-color carve-out).
                        Box(
                            Modifier
                                .size(fsSp(36f, t.fs).dp)
                                .clip(RoundedCornerShape(t.rCtrl))
                                .background(swatch)
                                .border(BorderStroke(2.dp, t.outline), RoundedCornerShape(t.rCtrl)),
                        )
                    }
                    // GAP-B: the hue wheel renders ONLY for an RGB-capable LED. A white/brightness-only LED
                    // (the owner's E5 chamber light) shows the brightness control alone — no hue wheel, and it
                    // drives the WHITE channel so value changes actually reach the hardware.
                    if (ledHasRgb) {
                        ColorWheel(
                            hue = hue,
                            onHandleMove = { hue = it }, // cheap repaint only (no dispatch).
                            onSettle = { settled ->
                                hue = settled
                                if (enabled) onColorSettle(settled, brightness)
                            },
                        )
                    }
                    Text(
                        text = stringResource(R.string.output_led_brightness),
                        color = t.text2,
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = fsSp(16f, t.fs).sp,
                    )
                    // GAP-A (19-09): the brightness control is an INLINE control inside the LED field — it does
                    // not nest a whole [ScrubberPage] (the double-scaffold bug). [LedBrightnessControl] is a
                    // self-contained fill-bar + [− +] stepper that dispatches ONCE per settle (gesture-end /
                    // stepper tap). GAP-B (19-10) reuses the SAME control for the white-only page — the only
                    // difference is the settle goes to the WHITE channel instead of the RGB hue.
                    LedBrightnessControl(
                        value = brightness,
                        enabled = enabled,
                        onValueChange = { brightness = it },
                        onSettle = { settled ->
                            brightness = settled
                            if (!enabled) return@LedBrightnessControl
                            if (ledHasRgb) onColorSettle(hue, settled) else onWhiteSettle(settled)
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
                }
            },
            gutter = {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Explicit Off (D-12) — all-zero dispatch, immediate.
                    OutlinedControl(
                        label = stringResource(R.string.output_off),
                        onClick = { if (enabled) onOff() },
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
            },
        )
    }
}

/**
 * The inline brightness control (19-09 GAP-A) — a self-contained 0..100 % fill-bar + `[− +]` stepper row that
 * lives INSIDE the LED field's Column. It is NOT a [ScrubberPage] (which carries its own [ScreenScaffold] +
 * gutter — nesting one inside the LED page's scaffold was the GAP-A double-scaffold bug); it is a single inline
 * widget so the LED page has exactly ONE scaffold.
 *
 * Settle semantics mirror [ScrubberActions.OnSettle]: [onValueChange] fires continuously as the user drags/steps
 * (cheap live preview, no dispatch) and [onSettle] fires EXACTLY ONCE per gesture-end (pointer-up) or stepper
 * tap — never per scrub frame (Adreno-320 budget). The fill-bar gesture reuses the same WR-01 single
 * `awaitEachGesture` + [fractionFromX] pure mapping as [ScrubberPage], so a tap at x and a drag to x agree.
 *
 * 19-10 (GAP-B white-only LED page) REUSES this composable verbatim for the brightness-only light, dispatching
 * the WHITE channel instead of an RGB hue. Keep it reusable: value in, settled value out, no LED-specific logic.
 *
 * @param value         current brightness 0..100 (caller-owned).
 * @param onValueChange live preview as the user scrubs/steps (no dispatch).
 * @param onSettle      dispatched ONCE per gesture-end / stepper tap with the settled value.
 * @param enabled       when false the control is inert (busy lock).
 */
@Composable
internal fun LedBrightnessControl(
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

    fun set(next: Float) {
        if (!enabled) return
        onValueChange(next.coerceIn(range.start, range.endInclusive))
    }

    fun setFromX(x: Float) {
        if (barWidthPx <= 0f) return
        set(range.start + fractionFromX(x, barWidthPx) * (range.endInclusive - range.start))
    }

    fun settle(v: Float) {
        if (enabled) onSettle(v.coerceIn(range.start, range.endInclusive))
    }

    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val display = value.roundToInt().toString()

    Column(modifier) {
        // Horizontal fill-bar scrubber (mirrors ScrubberPage's fill-bar; horizontal here to share the LED field
        // with the wheel above). Drag/tap anywhere to set; settle dispatches once on pointer-up.
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
                        // Gesture END — settle once (HIGH-3), never per move frame.
                        settle(value)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Accent-tinted fill tracks the value (left-anchored).
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(t.accentSoft),
            )
            Text(
                text = "$display%",
                color = t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(40f, t.fs).sp,
            )
        }
        // ± stepper row (keyboard-free): each tap is itself a settle (ends a discrete adjustment).
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedControl(
                label = "−",
                onClick = { val n = (value - step).coerceIn(range.start, range.endInclusive); set(n); settle(n) },
                modifier = Modifier.weight(1f),
                intent = Intent.Neutral,
            )
            OutlinedControl(
                label = "+",
                onClick = { val n = (value + step).coerceIn(range.start, range.endInclusive); set(n); settle(n) },
                modifier = Modifier.weight(1f),
                intent = Intent.Neutral,
            )
        }
    }
}
