package works.mees.dinghy.theme

import org.junit.Test

/**
 * RED scaffold for the D-11 bed-mesh OKLCH sequential ramp (the perceptually-uniform blue→teal→
 * yellow ramp, off pure red/green, that replaces BedMeshHeatmapView's accent→surface2→stop sRGB
 * lerp). The Kotlin ramp baker (to land in a later wave, e.g. `Palette.bakeRamp` / `OklchRamp`)
 * must reproduce `app/src/test/resources/oklch-ramp-golden.json`'s `ramp[]` BIT-FOR-BIT — the
 * committed fixture is the canonical spec the Kotlin implements TO (never the reverse), generated
 * by the independent oracle `tools/oklch-ramp-oracle.mjs`.
 *
 * Fixture parameterization (LOCKED): 32 stops, endpoints inclusive, three OKLCH control points
 * low(0.45 0.12 255) → mid(0.65 0.13 150) → high(0.85 0.15 95), piecewise-linear across two
 * segments split at the mid control point anchored at stop index 15, hue along the shorter arc.
 *
 * CRITICAL ([[dinghy-wave0-red-scaffold-compile]]): this body MUST NOT reference the unbuilt ramp
 * baker. The future behavior is named in PROSE only, with a `fail(...)` body — mirrors how
 * PaletteGoldenTest asserts the generator against color-golden.json.
 */
class OklchRampTest {

    @Test
    fun bakedRamp_matchesGoldenFixtureBitForBit() {
        // TODO(15.1-0N): load /oklch-ramp-golden.json, run the Kotlin OKLCH ramp baker with the
        // _meta control points + interpolation rule, and assert each of the 32 baked #rrggbb stops
        // equals ramp[i] exactly (the device-side ramp == the oracle == this fixture).
        org.junit.Assert.fail("not yet implemented — wave N (OKLCH ramp golden equality)")
    }

    @Test
    fun rampEndpoints_areControlPointsInclusive() {
        // TODO(15.1-0N): assert stop 0 == the low control point's hex and stop 31 == the high
        // control point's hex (endpointsInclusive = true).
        org.junit.Assert.fail("not yet implemented — wave N (OKLCH ramp inclusive endpoints)")
    }
}
