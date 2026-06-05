package works.mees.dinghy.theme

import org.junit.Assert.fail
import org.junit.Test

/**
 * RED OKLCH round-trip / gamut property scaffold (plan 15-01, Wave 0).
 *
 * GREEN in plan 15-02 when the Kotlin OKLCH↔sRGB math core lands. These are property
 * checks on the ported conversions:
 *  - `oklchToSrgbRoundTrip_staysInGamut`: hex → OKLCH → hex round-trips stably and the
 *    intermediate channels stay within the sRGB gamut (±0.0002 epsilon, matching
 *    `color.js inGamut`).
 *  - `gamutClamp_neverExceedsUnitInterval`: the 20-iter chroma-reduction binary search
 *    always yields sRGB channels in [0,1] for any (L,C,H).
 *
 * `[[dinghy-wave0-red-scaffold-compile]]`: compiles day-one against the CURRENT
 * sourceset — bodies are `fail(...)`, no reference to the unbuilt `Palette` math core.
 */
class PaletteMathTest {

    @Test
    fun oklchToSrgbRoundTrip_staysInGamut() {
        fail("RED — Palette math not yet ported, GREEN in plan 15-02")
    }

    @Test
    fun gamutClamp_neverExceedsUnitInterval() {
        fail("RED — Palette math not yet ported, GREEN in plan 15-02")
    }
}
