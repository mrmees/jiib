package works.mees.jiib.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * GREEN token-bridge derivation test (was the plan 15-01 Wave-0 RED scaffold; turned GREEN in
 * 15-03 when [TokenBridge] landed).
 *
 * Pins the dinghy-specific derivation that turns the slim [Palette.generate] output into the full
 * [ThemeTokens] tier set:
 *  - [derivesInBetweenTiers_matchesDinghyJs]: the `lShift`/`rgbaOf`-derived in-between tiers
 *    (bg2, surface2/3, text3, hair, outline2, accent2, edgeGlow) match an INDEPENDENT
 *    `dinghy.js`-equivalent derivation (Palette's `internal` helpers + the same dL/alpha
 *    constants) — proving the bridge wires the right source + shift, with no re-ported OKLCH math.
 *  - [appliesPoolOverrides_atIndex]: a sparse `poolOverrides` map replaces exactly the overridden
 *    `pool[i]` and leaves the rest seed-derived; an out-of-range index is ignored (no crash).
 *  - [surfacesArePureNeutral_bgVerbatim]: `bg` is the generator's neutral bg verbatim — no tint
 *    reintroduced (D-16).
 */
class TokenBridgeTest {

    private fun gen() = Palette.generate(
        seedHex = "#3f78ff",
        dark = true,
        maxItems = 3,
        poolShift = 0,
        statusFromPool = true,
    )

    // Independent dinghy.js-equivalent helpers (call Palette's internal OKLCH helpers, NOT TokenBridge).
    private fun bake(hex: String): Color {
        val s = hex.removePrefix("#")
        return Color(
            red = s.substring(0, 2).toInt(16),
            green = s.substring(2, 4).toInt(16),
            blue = s.substring(4, 6).toInt(16),
            alpha = 0xFF,
        )
    }

    private fun lShift(hex: String, dL: Double): Color {
        val o = Palette.hexToOklch(hex)
        return bake(Palette.oklchToHex((o.L + dL).coerceIn(0.0, 1.0), o.C, o.H))
    }

    private fun rgbaOf(hex: String, a: Double): Color = bake(hex).copy(alpha = a.toFloat())

    @Test
    fun derivesInBetweenTiers_matchesDinghyJs() {
        val g = gen()
        val tok = TokenBridge.build(g, emptyMap(), fs = 1.0f)
        val s = g.surfaces
        val t = g.theme

        // Derived surface/outline tiers — dark dL constants from dinghy.js tokensFromPalette.
        assertEquals("bg2", lShift(s.bg, 0.03), tok.bg2)
        assertEquals("surface2", lShift(s.surface, 0.04), tok.surface2)
        assertEquals("surface3", lShift(s.surface, 0.085), tok.surface3)
        assertEquals("text3", lShift(s.muted, -0.12), tok.text3)
        assertEquals("outline2", lShift(s.divider, 0.11), tok.outline2)
        assertEquals("accent2", lShift(t.primary, 0.08), tok.accent2)

        // Alpha-bearing variants — rgbaOf parity.
        assertEquals("hair", rgbaOf(s.text, 0.08), tok.hair)
        assertEquals("edgeGlow", rgbaOf(s.muted, 0.24), tok.edgeGlow)
        assertEquals("accentGlow", rgbaOf(t.primary, 0.35), tok.accentGlow)

        // 1:1 maps — including heat = caution (D-13).
        assertEquals("surface", bake(s.surface), tok.surface)
        assertEquals("text2 = muted", bake(s.muted), tok.text2)
        assertEquals("heat = caution", bake(g.status.caution), tok.heat)
        assertEquals("go", bake(g.status.go), tok.go)
        assertEquals("stop", bake(g.status.stop), tok.stop)
        // D-07: directional is RE-DERIVED accent-led — NOT bake(gen.directional.*) anymore.
        assertEquals("directional.temperature == accent", tok.accent, tok.directional.temperature)
        assertEquals("directional.xy == pool[0]", tok.pool[0], tok.directional.xy)
        assertEquals("directional.z == pool[1]", tok.pool[1], tok.directional.z)
    }

    @Test
    fun directional_isAccentLed_perD07() {
        val tok = TokenBridge.build(gen(), emptyMap(), fs = 1.0f)
        assertEquals("temperature = accent", tok.accent, tok.directional.temperature)
        assertEquals("xy = pool[0]", tok.pool[0], tok.directional.xy)
        assertEquals("z = pool[1]", tok.pool[1], tok.directional.z)
    }

    @Test
    fun directionalTemperature_staysAccent_inSimpleAndHighContrast() {
        val simple = Palette.generate(
            seedHex = "#3f78ff", dark = true, maxItems = 3, poolShift = 0,
            statusFromPool = true, simple = true,
        )
        val hc = Palette.generate(
            seedHex = "#3f78ff", dark = true, maxItems = 3, poolShift = 0,
            statusFromPool = true, highContrast = true,
        )
        val simpleTok = TokenBridge.build(simple, emptyMap(), fs = 1.0f)
        val hcTok = TokenBridge.build(hc, emptyMap(), fs = 1.0f)
        // accent survives all modes — temperature must NOT collapse to text.
        assertEquals("Simple temperature = accent", simpleTok.accent, simpleTok.directional.temperature)
        assertNotEquals("Simple temperature != text", simpleTok.text, simpleTok.directional.temperature)
        assertEquals("HighContrast temperature = accent", hcTok.accent, hcTok.directional.temperature)
        assertNotEquals("HighContrast temperature != text", hcTok.text, hcTok.directional.temperature)
    }

    @Test
    fun directionalOverridesFollowPool() {
        // A pool override at index 0/1 shifts directional.xy/.z with it (same-identity re-derivation).
        val xy = Color(0xFF010203)
        val z = Color(0xFF040506)
        val tok = TokenBridge.build(gen(), overrides = mapOf(0 to xy, 1 to z), fs = 1.0f)
        assertEquals("xy follows pool[0] override", xy, tok.directional.xy)
        assertEquals("z follows pool[1] override", z, tok.directional.z)
        // temperature is accent, untouched by pool overrides.
        assertEquals("temperature unaffected", tok.accent, tok.directional.temperature)
    }

    @Test
    fun appliesPoolOverrides_atIndex() {
        val g = gen()
        val seedPool = g.pool.map { bake(it) }
        check(seedPool.size >= 3) { "test palette must have >=3 pool slots" }

        val override = Color(0xFF123456)
        val outOfRange = Color(0xFFABCDEF)
        val tok = TokenBridge.build(
            g,
            overrides = mapOf(1 to override, 999 to outOfRange),
            fs = 1.0f,
        )

        // Index 1 replaced; every other slot stays seed-derived.
        assertEquals("pool[1] overridden", override, tok.pool[1])
        for (i in seedPool.indices) {
            if (i == 1) continue
            assertEquals("pool[$i] untouched", seedPool[i], tok.pool[i])
        }
        // The out-of-range override is dropped, never appended (no crash, size unchanged).
        assertEquals("pool size unchanged", seedPool.size, tok.pool.size)
        // Sanity: the override actually differs from the original slot.
        assertNotEquals("override differs from seed slot", seedPool[1], tok.pool[1])
    }

    @Test
    fun surfacesArePureNeutral_bgVerbatim() {
        val g = gen()
        val tok = TokenBridge.build(g, emptyMap(), fs = 1.0f)
        // bg is the generator's neutral bg verbatim — no tint added (D-16).
        assertEquals(bake(g.surfaces.bg), tok.bg)
    }
}
