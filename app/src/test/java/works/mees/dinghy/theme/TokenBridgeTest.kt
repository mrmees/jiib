package works.mees.dinghy.theme

import org.junit.Assert.fail
import org.junit.Test

/**
 * RED token-bridge derivation scaffold (plan 15-01, Wave 0).
 *
 * GREEN in plan 15-03 when `tokensFromPalette` (the port of `dinghy.js`'s bridge) lands.
 * These pin the dinghy-specific derivation that turns the slim generator output into the
 * full `ThemeTokens` tier set:
 *  - `derivesInBetweenTiers_matchesDinghyJs`: the `lShift`/`rgbaOf`-derived in-between
 *    tiers (bg2, surface2/3, text3, hair, outline2, accent2/soft/line/glow, edgeGlow)
 *    match the `dinghy.js` reference for the default-seed palette.
 *  - `appliesPoolOverrides_atIndex`: a sparse `poolOverrides` map replaces exactly the
 *    overridden `pool[i]` and leaves the rest seed-derived.
 *
 * `[[dinghy-wave0-red-scaffold-compile]]`: compiles day-one against the CURRENT
 * sourceset — bodies are `fail(...)`, no reference to the unbuilt `TokenBridge`/`Palette`.
 */
class TokenBridgeTest {

    @Test
    fun derivesInBetweenTiers_matchesDinghyJs() {
        fail("RED — TokenBridge not yet ported, GREEN in plan 15-03")
    }

    @Test
    fun appliesPoolOverrides_atIndex() {
        fail("RED — TokenBridge not yet ported, GREEN in plan 15-03")
    }
}
