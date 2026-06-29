package works.mees.jiib.theme

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTokensFontEqualityTest {
    @Test fun defaultsAreCatalogDefaults() {
        assertEquals(FontCatalog.DEFAULT_UI, TokensDark.uiFont)
        assertEquals(FontCatalog.DEFAULT_DATA, TokensDark.dataFont)
    }

    @Test fun changingUiFontMakesTokensUnequal() {
        val a = TokensDark
        val b = TokensDark.copy(uiFont = FontCatalog.GEIST_MONO) // any different AppFont
        assertNotEquals("font change must break == so Views repaint", a, b)
    }

    @Test fun sameFontsStayEqual() {
        assertEquals(TokensDark, TokensDark.copy())
    }
}
