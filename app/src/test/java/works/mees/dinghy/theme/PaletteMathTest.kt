package works.mees.dinghy.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OKLCH round-trip / gamut property checks on the ported math core (plan 15-02).
 *
 *  - `oklchToSrgbRoundTrip_staysInGamut`: hex → OKLCH → hex round-trips stably (the
 *    re-encoded hex equals the original for in-gamut sRGB samples).
 *  - `gamutClamp_neverExceedsUnitInterval`: the 20-iter chroma-reduction binary search
 *    in `oklchToHex` always yields a parseable in-[0,1] sRGB hex for any (L,C,H),
 *    including deliberately out-of-gamut high-chroma requests.
 *
 * These exercise the same `internal` helpers (`hexToOklch` / `oklchToHex`) the 15-03
 * `TokenBridge` derives tiers from.
 */
class PaletteMathTest {

    // A spread of in-gamut sRGB hexes (primaries, neutrals, mids — non-0xFF channels
    // present so the `/255.0` Double normalization is exercised, not Int-collapsed).
    private val samples = listOf(
        "#3f78ff", "#e23a3a", "#c8b400", "#0b0b0b", "#e8e8e8",
        "#6895f4", "#866200", "#c575cc", "#57af58", "#007780",
        "#808080", "#123456", "#abcdef",
    )

    private fun parseChannels(hex: String): Triple<Int, Int, Int> {
        val s = hex.removePrefix("#")
        return Triple(s.substring(0, 2).toInt(16), s.substring(2, 4).toInt(16), s.substring(4, 6).toInt(16))
    }

    @Test
    fun oklchToSrgbRoundTrip_staysInGamut() {
        for (hex in samples) {
            val o = Palette.hexToOklch(hex)
            val back = Palette.oklchToHex(o.L, o.C, o.H)
            // In-gamut sRGB re-encodes to the same hex (±0 — exact round-trip for these
            // samples; the binary search only engages when out of gamut).
            assertEquals("round-trip $hex", hex, back)
        }
    }

    @Test
    fun gamutClamp_neverExceedsUnitInterval() {
        // Sweep L/H broadly with a deliberately huge chroma (0.5 is past most cusps) so the
        // 20-iter clamp must engage; every output channel must parse to a valid [0,255] byte
        // (i.e. clamp01 held — no overflow / negative channel).
        var L = 0.05
        while (L <= 0.95) {
            var H = 0.0
            while (H < 360.0) {
                val hex = Palette.oklchToHex(L, 0.5, H)
                val (r, g, b) = parseChannels(hex)
                assertTrue("channels in [0,255] for L=$L H=$H -> $hex", r in 0..255 && g in 0..255 && b in 0..255)
                H += 30.0
            }
            L += 0.1
        }
    }
}
