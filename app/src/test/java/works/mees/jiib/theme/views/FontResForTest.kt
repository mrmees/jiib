package works.mees.jiib.theme.views

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.jiib.R
import works.mees.jiib.theme.FontCatalog
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.TokensDark

class FontResForTest {
    @Test fun nullTokensUsesCatalogDefaults() {
        // Data role → Geist Mono; statValue is SemiBold
        assertEquals(R.font.geist_mono_semibold, fontResFor(JiibType.statValue, null))
        // UI role → Geist; listLabel is SemiBold
        assertEquals(R.font.geist_semibold, fontResFor(JiibType.listLabel, null))
    }

    @Test fun tokensSelectTheChosenFace() {
        val t = TokensDark.copy(dataFont = FontCatalog.GEIST_MONO) // default, but proves the path
        assertEquals(R.font.geist_mono_medium, fontResFor(JiibType.consoleLine, t)) // Data + Medium
    }
}
