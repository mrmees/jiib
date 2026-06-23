package works.mees.dinghy.theme

import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The bed-mesh OKLCH sequential ramp baker (D-11) — a perceptually-uniform sequential ramp for the
 * overhead heatmap. The ramp is now THEME-DERIVED ([themedRampStops]): it interpolates in OKLCH from
 * a LOW endpoint = the active theme's first DATA-POOL color to a HIGH endpoint = the theme ACCENT, so
 * the heatmap re-tints with the theme (dark/light/custom × Colorful/Simple/High-Contrast) instead of
 * baking a fixed viridis sequence. The pool→accent direction keeps the height scale OFF the status
 * red/amber/green language (height is its own sub-system, distinct from the categorical pool and the
 * stoplight status colors), while still being a token-only, theme-following gradient (THEME-01).
 *
 * The original fixed viridis/cividis sequence is retained as [bedMeshRampStops] for the golden test +
 * any consumer that still wants the locked sequence, but [works.mees.dinghy.render.BedMeshHeatmapView]
 * now bakes [themedRampStops] from the CURRENT tokens in `applyTokens`.
 *
 * Host-pure (ZERO Android / Compose imports), mirroring [Palette]'s discipline — the output is a
 * baked `List<Int>` of opaque sRGB ARGB ints, ready for [works.mees.dinghy.render.BedMeshHeatmapView]
 * to bake ONCE in `applyTokens` and index/lerp cheaply per cell per frame (Adreno-320 floor: NO
 * OKLCH math in `onDraw`).
 *
 * ## The OKLCH math is NOT re-ported here
 * Every control point is converted to sRGB through [Palette.oklchToHex] (which already clamps
 * out-of-gamut by reducing chroma via its 20-iter binary search) — mirroring the
 * [TokenBridge.lShift] delegate-to-Palette pattern. Re-deriving the OKLab 3×3 matrices here would
 * risk golden drift on the Nexus-7 floor; the matrices are golden-tested ONCE in [Palette].
 *
 * ## The ramp is LOCKED (plan 15.1-01's `oklch-ramp-golden.json` `_meta` block)
 * 32 stops, endpoints INCLUSIVE (stop 0 == low control point, stop 31 == high control point),
 * THREE OKLCH control points
 *   low `oklch(0.45 0.12 255)` → mid `oklch(0.65 0.13 150)` → high `oklch(0.85 0.15 95)`
 * (viridis/cividis-style blue → teal → yellow, OFF pure red and pure green), interpolated
 * PIECEWISE-LINEARLY across TWO segments split at the mid control point anchored at stop index 15
 * (segment A = stops 0..15 low→mid; segment B = stops 15..31 mid→high). Hue lerps along the SHORTER
 * arc per segment; L and C lerp linearly. The Kotlin output here matches `ramp[]` bit-for-bit; if the
 * locked endpoints must ever change, edit + re-run the independent oracle `tools/oklch-ramp-oracle.mjs`
 * and re-bake the fixture from THAT — never from this Kotlin (that would collapse the golden test
 * into a tautology).
 */
object OklchRamp {

    /**
     * The bed-mesh ramp's THREE LOCKED OKLCH control points (off pure red/green). Named + valued to
     * match the `oklch-ramp-golden.json` `_meta.controlPoints` block exactly.
     */
    private val LOW = Palette.OklchValue(L = 0.45, C = 0.12, H = 255.0)
    private val MID = Palette.OklchValue(L = 0.65, C = 0.13, H = 150.0)
    private val HIGH = Palette.OklchValue(L = 0.85, C = 0.15, H = 95.0)

    /** 32 stops, mid anchored at stop index 15 — the two-segment split point (`_meta.midIndex`). */
    private const val STOPS = 32
    private const val MID_INDEX = 15

    /** Linear interpolation of two scalars. */
    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    /**
     * Shorter-arc hue interpolation (degrees) — mirrors the oracle's `lerpHueShortArc`. The signed
     * delta is wrapped into (-180, 180] so the hue always travels the short way around the wheel.
     */
    private fun lerpHueShortArc(h0: Double, h1: Double, t: Double): Double {
        val d = ((h1 - h0) % 360.0 + 540.0) % 360.0 - 180.0
        val h = h0 + d * t
        return ((h % 360.0) + 360.0) % 360.0
    }

    /** The single stop [i] (0..31): pick the segment, lerp L/C/H, convert via [Palette.oklchToHex]. */
    private fun rampStopHex(i: Int): String {
        val lo: Palette.OklchValue
        val hi: Palette.OklchValue
        val t: Double
        if (i <= MID_INDEX) {
            lo = LOW
            hi = MID
            t = i.toDouble() / MID_INDEX            // 0 at stop 0, 1 at stop 15
        } else {
            lo = MID
            hi = HIGH
            t = (i - MID_INDEX).toDouble() / (STOPS - 1 - MID_INDEX)  // 0 at stop 15, 1 at stop 31
        }
        val L = lerp(lo.L, hi.L, t)
        val C = lerp(lo.C, hi.C, t)
        val H = lerpHueShortArc(lo.H, hi.H, t)
        return Palette.oklchToHex(L, C, H)
    }

    /** Pack a `#rrggbb` hex (no alpha) into an opaque (alpha 0xFF) ARGB int. */
    private fun hexToArgb(hex: String): Int {
        val s = hex.removePrefix("#")
        val r = s.substring(0, 2).toInt(16)
        val g = s.substring(2, 4).toInt(16)
        val b = s.substring(4, 6).toInt(16)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Unpack the RGB channels of an opaque ARGB int into a `#rrggbb` hex (alpha discarded). */
    private fun argbToHex(argb: Int): String {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return String.format(Locale.US, "#%02x%02x%02x", r, g, b)
    }

    /**
     * Bake the LOCKED 32-stop viridis bed-mesh ramp as opaque ARGB ints (stop 0 = low/blue …
     * stop 31 = high/yellow). The locked sequence is retained for the golden test + any consumer that
     * needs the fixed viridis ramp; the live heatmap now bakes [themedRampStops] instead so the ramp
     * follows the theme. Call ONCE; index + lerp cheaply per cell.
     */
    fun bedMeshRampStops(): List<Int> = (0 until STOPS).map { hexToArgb(rampStopHex(it)) }

    /**
     * Bake a THEME-DERIVED 32-stop sequential ramp as opaque ARGB ints, interpolated in OKLCH from a
     * LOW endpoint [lowArgb] to a HIGH endpoint [highArgb]. When [midArgb] is non-null the ramp is
     * split at stop MID (=STOPS/2=16): stops 0..MID interpolate low→mid, stops MID..31 interpolate
     * mid→high, so stop ~16 == mid (the neutral) and the gradient reads low → neutral → high. Callers
     * pass a theme-INDEPENDENT neutral here (see BedMeshScreen) so the midpoint reads the same in dark
     * and light mode. When [midArgb] is null the exact 2-endpoint behavior is preserved (backward
     * compatible).
     * Stop 0 == [lowArgb] and stop 31 == [highArgb] EXACTLY (endpoints inclusive); L and C lerp
     * linearly, hue lerps along the SHORTER arc. All endpoints are token-derived (THEME-01): no raw
     * hex, no status color. Call ONCE per `applyTokens` (32 OKLCH conversions); the View then indexes
     * + lerps cheaply per cell per frame (Adreno-320 floor: NO OKLCH math in `onDraw`).
     */
    fun themedRampStops(lowArgb: Int, highArgb: Int, midArgb: Int? = null): IntArray {
        val lo = Palette.hexToOklch(argbToHex(lowArgb))
        val hi = Palette.hexToOklch(argbToHex(highArgb))
        if (midArgb == null) {
            return IntArray(STOPS) { i ->
                val t = if (STOPS == 1) 0.0 else i.toDouble() / (STOPS - 1)
                val L = lerp(lo.L, hi.L, t)
                val C = lerp(lo.C, hi.C, t)
                val H = lerpHueShortArc(lo.H, hi.H, t)
                hexToArgb(Palette.oklchToHex(L, C, H))
            }
        }
        val mid = Palette.hexToOklch(argbToHex(midArgb))
        val MID = STOPS / 2  // = 16
        return IntArray(STOPS) { i ->
            val loOklch: Palette.OklchValue
            val hiOklch: Palette.OklchValue
            val t: Double
            if (i <= MID) {
                loOklch = lo
                hiOklch = mid
                t = i.toDouble() / MID
            } else {
                loOklch = mid
                hiOklch = hi
                t = (i - MID).toDouble() / (STOPS - 1 - MID)
            }
            val L = lerp(loOklch.L, hiOklch.L, t)
            val C = lerp(loOklch.C, hiOklch.C, t)
            val H = lerpHueShortArc(loOklch.H, hiOklch.H, t)
            hexToArgb(Palette.oklchToHex(L, C, H))
        }
    }

    /** Stop count of [bedMeshRampStops]/[themedRampStops] (32) — so consumers needn't hardcode it. */
    val stopCount: Int get() = STOPS
}
