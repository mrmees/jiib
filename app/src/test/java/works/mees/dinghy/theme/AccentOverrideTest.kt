package works.mees.dinghy.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccentOverrideTest {
    @Test fun `sanitize keeps a valid accent override`() {
        val t = ThemePrefs.sanitizeTuple(
            rawSeed = "#3f78ff", rawDark = true, rawMode = "Colorful",
            rawShift = 0, rawOverrides = emptyMap(), rawAccent = 0xFF112233L,
        )
        assertEquals(0xFF112233L, t.accentOverride)
    }

    @Test fun `sanitize drops a garbage accent override`() {
        val t = ThemePrefs.sanitizeTuple(
            rawSeed = "#3f78ff", rawDark = true, rawMode = "Colorful",
            rawShift = 0, rawOverrides = emptyMap(), rawAccent = -5L,
        )
        assertNull(t.accentOverride)
    }

    @Test fun `default tuple has no accent override`() {
        assertNull(ThemePrefs.TUPLE_DEFAULT.accentOverride)
    }

    @Test fun `profile threads accent override through tuple and persistence`() {
        val p = works.mees.dinghy.config.Profile(id = "x", host = "h", accentOverrideArgb = 0xFF445566L)
        assertEquals(0xFF445566L, p.toThemeTuple().accentOverride)
        val round = works.mees.dinghy.config.Profile.fromPersisted(p.toPersisted())
        assertEquals(0xFF445566L, round.accentOverrideArgb)
    }

    @Test fun `token bridge applies accent override to accent token`() {
        val gen = Palette.generate(seedHex = "#3f78ff", dark = true, maxItems = 4)
        val overridden = TokenBridge.build(
            gen = gen, overrides = emptyMap(), fs = 1f,
            statusOverrides = emptyMap(),
            accentOverride = androidx.compose.ui.graphics.Color(0xFFFF0000),
        )
        assertEquals(0xFF, (overridden.accent.toArgb() ushr 24) and 0xFF) // opaque
        org.junit.Assert.assertTrue(
            "accent red channel should dominate after red override",
            (overridden.accent.toArgb() ushr 16 and 0xFF) > (overridden.accent.toArgb() and 0xFF),
        )
    }

    @Test fun `resolver bake equals apply for an accent override`() {
        val tuple = ThemePrefs.TUPLE_DEFAULT.copy(accentOverride = 0xFFFF0000L)
        val r = ThemeResolver()
        r.apply(
            seedHex = tuple.seedHex, dark = tuple.dark, paletteMode = tuple.paletteMode,
            poolShift = tuple.poolShift, overrides = emptyMap(), fs = tuple.fs,
            statusOverrides = emptyMap(),
            accentOverride = androidx.compose.ui.graphics.Color(0xFFFF0000),
        )
        val applied = r.tokens.value.accent
        val baked = r.bake(tuple).accent
        assertEquals(baked.value, applied.value)
    }
}
