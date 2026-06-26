package works.mees.jiib.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * THEME-02 / D-04 / Pattern 5: `--fs` is the sole text-size authority. The S/M/L choice maps to
 * 1.0/1.15/1.32, `fsSp(base, fs)` is exactly `base*fs`, and that multiplier lands in [ThemeTokens.fs].
 */
class FontScaleTest {

    @Test
    fun fontScaleChoices_mapToCanonicalMultipliers() {
        assertEquals(1.0f, FontScale.S.multiplier)
        assertEquals(1.15f, FontScale.M.multiplier)
        assertEquals(1.32f, FontScale.L.multiplier)
    }

    @Test
    fun fsSp_isBaseTimesMultiplier() {
        // Pattern 5 worked example: fsSp(16f, 1.32f) == 21.12f.
        assertEquals(21.12f, fsSp(16f, FontScale.L.multiplier), 1e-4f)
        assertEquals(16f, fsSp(16f, FontScale.S.multiplier), 1e-4f)
        assertEquals(18.4f, fsSp(16f, FontScale.M.multiplier), 1e-4f)
    }

    @Test
    fun fs_landsInResolvedThemeTokens() {
        // The fs multiplier flows through the generate-and-cache resolver into the emitted tokens (D-04).
        val small = ThemeResolver(fs = FontScale.S.multiplier)
        assertEquals(1.0f, small.tokens.value.fs)

        val large = ThemeResolver(dark = false, fs = FontScale.L.multiplier)
        assertEquals(1.32f, large.tokens.value.fs)

        // setFs re-emits with the new multiplier.
        small.setFs(FontScale.L.multiplier)
        assertEquals(1.32f, small.tokens.value.fs)
    }
}
