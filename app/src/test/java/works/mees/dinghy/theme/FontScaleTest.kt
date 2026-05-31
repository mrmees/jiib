package works.mees.dinghy.theme

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
        assertEquals(1.0f, resolve(ThemeBase.Dark, TokenDelta.EMPTY, FontScale.S.multiplier).fs)
        assertEquals(1.32f, resolve(ThemeBase.Light, TokenDelta.EMPTY, FontScale.L.multiplier).fs)
    }
}
