package works.mees.dinghy.theme

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.dinghy.di.resolveThemeTuple

class ThemeDraftTest {
    private val base = ThemePrefs.TUPLE_DEFAULT
    private val draft = base.copy(seedHex = "#ff0000")

    @Test fun `draft wins over base and override regardless of devOn`() {
        assertEquals(draft, resolveThemeTuple(base = base, draft = draft, override = null, devOn = false))
        assertEquals(draft, resolveThemeTuple(base = base, draft = draft, override = null, devOn = true))
    }

    @Test fun `no draft falls back to base when devOn is false`() {
        assertEquals(base, resolveThemeTuple(base = base, draft = null, override = null, devOn = false))
    }
}
