package works.mees.dinghy.theme

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
}
