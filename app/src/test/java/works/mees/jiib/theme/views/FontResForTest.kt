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
        // NON-default faces in BOTH slots — proves fontResFor reads the token-selected face, not the
        // catalog default (Geist / Geist Mono). A vacuous version using the defaults would pass even
        // if the token path were ignored.
        val t = TokensDark.copy(uiFont = FontCatalog.NOTO_SANS, dataFont = FontCatalog.SPACE_MONO)
        // Data role → chosen Space Mono; consoleLine is Medium (Space Mono ships Medium → exact)
        assertEquals(R.font.space_mono_medium, fontResFor(JiibType.consoleLine, t))
        // UI role → chosen Noto Sans; listLabel is SemiBold (Noto Sans ships SemiBold → exact)
        assertEquals(R.font.noto_sans_semibold, fontResFor(JiibType.listLabel, t))
    }
}
