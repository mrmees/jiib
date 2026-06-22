package works.mees.dinghy.designsystem

/**
 * Pure HSV→RGB helper for the LED control page (Phase 19, NO ANALOG in the repo).
 *
 * Dependency-free by design: it takes/returns plain floats (no `androidx.compose.ui.graphics.Color`)
 * so it is host-unit-testable WITHOUT Robolectric, and its three outputs feed
 * [works.mees.dinghy.command.PrinterCommands.setLed] RED/GREEN/BLUE directly.
 *
 * ## Saturation is user-controlled (2026-06-21 Outputs slider rework)
 * The LED Focus surface now drives FULL H/S/V via `HsvSliders` (the prior hue-only color ring +
 * fixed-saturation-1.0 model is retired), so callers pass a real [saturation]. It still defaults to
 * `1f` for any caller that wants a pure hue.
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

/**
 * The inverse of [hsvToRgb] (Phase 19 LED page initial state). Maps a live `color_data[0]` RGB triple back
 * to (hue, saturation, value) so the LED page can SEED the hue wheel + brightness scrubber from the
 * printer's current color (review MEDIUM — the page reflects the live strip, not a blank default).
 *
 * Dependency-free (plain floats, no `androidx…Color`) so it is host-unit-testable. For a primary-hue input
 * the round-trip `rgbToHsv(hsvToRgb(h, 1, v))` recovers `(h, ~1, v)`. A pure grey/black input has an
 * undefined hue; this returns hue 0 (the wheel handle parks at 3-o'clock) — value/brightness is still exact.
 *
 * @param r 0f..1f red.
 * @param g 0f..1f green.
 * @param b 0f..1f blue.
 * @return [Triple] of (hue 0..360, saturation 0f..1f, value 0f..1f).
 */
fun rgbToHsv(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
    val rr = r.coerceIn(0f, 1f)
    val gg = g.coerceIn(0f, 1f)
    val bb = b.coerceIn(0f, 1f)
    val max = maxOf(rr, gg, bb)
    val min = minOf(rr, gg, bb)
    val delta = max - min

    val hue: Float = when {
        delta == 0f -> 0f // achromatic — hue undefined, park at 0.
        max == rr -> 60f * (((gg - bb) / delta) % 6f)
        max == gg -> 60f * (((bb - rr) / delta) + 2f)
        else -> 60f * (((rr - gg) / delta) + 4f)
    }
    val h = if (hue < 0f) hue + 360f else hue
    val saturation = if (max == 0f) 0f else delta / max
    return Triple(h % 360f, saturation, max)
}
