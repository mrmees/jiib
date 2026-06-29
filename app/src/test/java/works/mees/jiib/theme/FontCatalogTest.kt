package works.mees.jiib.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FontCatalogTest {
    @Test fun idsAreUniqueAndNonBlank() {
        val ids = FontCatalog.all.map { it.id }
        assertTrue("blank id", ids.none { it.isBlank() })
        assertEquals("duplicate ids: $ids", ids.size, ids.toSet().size)
    }

    @Test fun listsAreKindFiltered() {
        assertTrue(FontCatalog.ui.all { it.kind == FontKind.Ui })
        assertTrue(FontCatalog.data.all { it.kind == FontKind.Data })
    }

    @Test fun defaultsAreGeistAndAreInTheirLists() {
        assertSame(FontCatalog.GEIST, FontCatalog.DEFAULT_UI)
        assertSame(FontCatalog.GEIST_MONO, FontCatalog.DEFAULT_DATA)
        assertTrue(FontCatalog.DEFAULT_UI in FontCatalog.ui)
        assertTrue(FontCatalog.DEFAULT_DATA in FontCatalog.data)
    }

    @Test fun unknownIdFallsBackToDefault() {
        assertSame(FontCatalog.DEFAULT_UI, FontCatalog.uiOrDefault("nope"))
        assertSame(FontCatalog.DEFAULT_UI, FontCatalog.uiOrDefault(null))
        assertSame(FontCatalog.DEFAULT_DATA, FontCatalog.dataOrDefault("nope"))
        // a data id must not resolve through the UI list and vice-versa
        assertSame(FontCatalog.DEFAULT_UI, FontCatalog.uiOrDefault(FontCatalog.GEIST_MONO.id))
    }

    @Test fun byIdRoundTrips() {
        assertSame(FontCatalog.GEIST, FontCatalog.byId("geist"))
        assertEquals(null, FontCatalog.byId("nope"))
    }
}
