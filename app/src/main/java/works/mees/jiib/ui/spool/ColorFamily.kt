package works.mees.jiib.ui.spool

import works.mees.jiib.spool.normalizeColorHex
import kotlin.math.abs

/**
 * Classify a filament color hex into one of the 12 fixed palette FAMILIES (the swatch names), or null
 * when the hex is absent/unparseable. PURE + host-testable: integer RGB → HSL/chroma, NO
 * android.graphics.Color (that lives only in display code, e.g. SpoolScreen.parseNormalizedHex).
 *
 * Replaces Spoolman's server-side CIE76 color_similarity matching: perceptual nearness to a SATURATED
 * swatch does not model "color family" — a muted olive is perceptually near gray/brown yet a human calls
 * it green (its nearest saturated swatch, Green, is ΔE 72 away). Hue-family bucketing matches how people
 * categorize color. The Natural family covers pale warm near-whites (cream/ivory/"natural" filament).
 * See docs/superpowers/specs/2026-06-18-spool-color-family-filter-design.md.
 */
internal fun colorFamily(hex: String?): String? {
    val rgb = parseRgbChannels(normalizeColorHex(hex) ?: return null) ?: return null
    val (r, g, b) = rgb
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val chroma = max - min                                   // 0..255
    val l = (max + min) / 2.0 / 255.0                        // HSL lightness 0..1
    val sHsl = if (chroma == 0) 0.0 else (chroma / 255.0) / (1.0 - abs(2 * l - 1))
    val hue = hueDegrees(r, g, b, max, chroma)               // 0..360

    // 1. Natural — pale, lightly-tinted warm near-white (cream/ivory/beige). Uses CHROMA (not HSL
    //    saturation, which is unstable near white: cream and pastel pink both report S≈1.0). Tested
    //    first so cream resolves to Natural, not White or Yellow.
    if (l > 0.80 && chroma in 12..70 && hue in 20.0..95.0) return "Natural"
    // 2. Neutral.
    if (sHsl < 0.15) return when {
        l < 0.22 -> "Black"
        l > 0.85 -> "White"
        else -> "Gray"
    }
    // 3. Brown — a dark/muted warm color, not a hue band; tested before the hue families.
    if (hue >= 20.0 && hue < 50.0 && (l < 0.45 || sHsl < 0.45)) return "Brown"
    // 4. Hue families.
    return when {
        hue < 15.0 || hue >= 345.0 -> "Red"
        hue < 45.0 -> "Orange"
        hue < 70.0 -> "Yellow"
        hue < 165.0 -> "Green"
        hue < 255.0 -> "Blue"
        hue < 290.0 -> "Purple"
        else -> "Pink"
    }
}

/** Hue in degrees [0,360) from integer channels; 0 for an achromatic (chroma 0) color. */
private fun hueDegrees(r: Int, g: Int, b: Int, max: Int, chroma: Int): Double {
    if (chroma == 0) return 0.0
    val hp = when (max) {
        r -> (g - b).toDouble() / chroma          // may be negative; wrapped by mod below
        g -> (b - r).toDouble() / chroma + 2.0
        else -> (r - g).toDouble() / chroma + 4.0
    }
    return (hp * 60.0).mod(360.0)
}

/** Parse the RGB triple from a normalized `#RRGGBB`/`#RRGGBBAA` hex; null if unparseable. Alpha ignored. */
private fun parseRgbChannels(normalizedHex: String): Triple<Int, Int, Int>? {
    val h = normalizedHex.removePrefix("#")
    if (h.length != 6 && h.length != 8) return null
    return runCatching {
        Triple(h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
    }.getOrNull()
}
