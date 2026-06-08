package works.mees.dinghy.ui.outputs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import works.mees.dinghy.designsystem.ScrubberActions
import works.mees.dinghy.designsystem.ScrubberPage
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

    fun dispatchColor(hue: Float, brightnessPct: Float) {
        val (r, g, b) = hsvToRgb(hue, 1f, (brightnessPct / 100f).coerceIn(0f, 1f))
        // markPending the LED's reached() target = the brightest channel (matches OutputsHolder.reached()).
        holder.markPending(descriptor.objectKey, maxOf(r, g, b).toDouble())
        // WHITE-CHANNEL POLICY: every color dispatch sends WHITE=0 (T-19-06-05).
        dispatchCommand(CommandRegistry.setLed, SetLedArgs(descriptor.commandName, r, g, b, w = 0f))
    }

    fun dispatchOff() {
        holder.markPending(descriptor.objectKey, 0.0)
        dispatchCommand(CommandRegistry.setLed, SetLedArgs(descriptor.commandName, 0f, 0f, 0f, w = 0f))
    }

    OutputLedContent(
        prettyName = descriptor.prettyName,
        initialHue = seedHue,
        initialBrightness = seedBrightness,
        enabled = !busy,
        failureText = failureText,
        onColorSettle = { hue, brightness -> if (busy.not()) dispatchColor(hue, brightness) },
        onOff = { if (busy.not()) dispatchOff() },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * The pure, container-free LED rendering surface shared by the live entry and the @Preview seam. Owns the
 * hue wheel + brightness scrubber + the live swatch + Off + Back; the hue/brightness working state lives here
 * so a settle (wheel or brightness) dispatches the CURRENT hue+brightness pair.
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
) {
    val t = LocalTokens.current
    var hue by remember(initialHue) { mutableFloatStateOf(initialHue) }
    var brightness by remember(initialBrightness) { mutableFloatStateOf(initialBrightness) }

    // The live preview swatch — the LITERAL chosen color (THEME-01 data-color carve-out).
    val (pr, pg, pb) = hsvToRgb(hue, 1f, (brightness / 100f).coerceIn(0f, 1f))
    val swatch = Color(pr, pg, pb)

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
                    ColorWheel(
                        hue = hue,
                        onHandleMove = { hue = it }, // cheap repaint only (no dispatch).
                        onSettle = { settled ->
                            hue = settled
                            if (enabled) onColorSettle(settled, brightness)
                        },
                    )
                    Text(
                        text = stringResource(R.string.output_led_brightness),
                        color = t.text2,
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = fsSp(16f, t.fs).sp,
                    )
                    // Brightness scrubber (OnSettle 0..100 %). Sized so it shares the field but leaves room for
                    // the wheel; dispatches the CURRENT hue at the settled brightness.
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        ScrubberPage(
                            label = stringResource(R.string.output_led_brightness),
                            value = brightness,
                            range = 0f..100f,
                            step = 1f,
                            unit = "%",
                            onValueChange = { brightness = it },
                            actions = ScrubberActions.OnSettle(
                                onSettle = { settled ->
                                    brightness = settled
                                    if (enabled) onColorSettle(hue, settled)
                                },
                                onBack = onBack,
                            ),
                        )
                    }
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
