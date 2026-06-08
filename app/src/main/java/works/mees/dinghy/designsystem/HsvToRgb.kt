package works.mees.dinghy.designsystem

/**
 * Pure HSV→RGB helper for the LED control page (Phase 19, NO ANALOG in the repo).
 *
 * Dependency-free by design: it takes/returns plain floats (no `androidx.compose.ui.graphics.Color`)
 * so it is host-unit-testable WITHOUT Robolectric, and its three outputs feed
 * [works.mees.dinghy.command.PrinterCommands.setLed] RED/GREEN/BLUE directly.
 *
 * ## v1 fixed-saturation decision (RESEARCH Open Q1 / A4)
 * v1 LED control is **hue + brightness** with saturation FIXED at 1.0 — it reuses the existing
 * hue-only [ColorWheel] verbatim and matches the staging "simple RGB color picker" intent. The
 * [saturation] parameter defaults to `1f` so a future saturation control can be added without a
 * signature break; the Wave-2 LED page passes saturation = 1f.
 *
 * All inputs are clamped to their valid ranges (hue wraps 0..360, saturation/value to 0f..1f) and all
 * three returned channels are guaranteed within 0f..1f so they feed `setLed` safely.
 *
 * @param hue 0..360 (wraps; 360 == 0).
 * @param saturation 0f..1f (default 1f — the v1 fixed-saturation decision).
 * @param value 0f..1f brightness (scales the output).
 * @return [Triple] of (red, green, blue), each 0f..1f.
 */
fun hsvToRgb(hue: Float, saturation: Float = 1f, value: Float): Triple<Float, Float, Float> {
    // Wrap hue into [0, 360) then clamp sat/value into [0, 1].
    var h = hue % 360f
    if (h < 0f) h += 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)

    val c = v * s
    val hPrime = h / 60f
    val x = c * (1f - kotlin.math.abs(hPrime % 2f - 1f))
    val (r1, g1, b1) = when {
        hPrime < 1f -> Triple(c, x, 0f)
        hPrime < 2f -> Triple(x, c, 0f)
        hPrime < 3f -> Triple(0f, c, x)
        hPrime < 4f -> Triple(0f, x, c)
        hPrime < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = v - c
    return Triple(
        (r1 + m).coerceIn(0f, 1f),
        (g1 + m).coerceIn(0f, 1f),
        (b1 + m).coerceIn(0f, 1f),
    )
}
