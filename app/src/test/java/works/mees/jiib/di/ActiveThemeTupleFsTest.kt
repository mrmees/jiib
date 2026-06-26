package works.mees.jiib.di

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.jiib.theme.FontScale
import works.mees.jiib.theme.ThemePrefs

/**
 * Documents the override invariant used in activeThemeTuple: whatever per-profile/idle tuple is
 * produced, its `fs` is replaced by the app-global font scale (`tuple.copy(fs = appFs.multiplier)`).
 */
class ActiveThemeTupleFsTest {
    private fun base(fs: Float) = ThemePrefs.ThemeTuple(
        seedHex = "#3f78ff", dark = true, paletteMode = "Colorful",
        poolShift = 0, poolOverrides = emptyMap(), fs = fs,
    )

    @Test fun appFontScaleOverridesTupleFs() = runTest {
        val overridden = base(FontScale.S.multiplier).copy(fs = FontScale.L.multiplier)
        assertEquals(FontScale.L.multiplier, overridden.fs, 0.0001f)
    }
}
