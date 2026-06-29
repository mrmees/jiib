package works.mees.jiib.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.jiib.theme.FontCatalog
import java.io.File

class FontPrefsTest {
    private val tmp = mutableListOf<File>()
    private val scopes = mutableListOf<CoroutineScope>()
    @After fun tearDown() { scopes.forEach { it.cancel() }; tmp.forEach { it.delete() } }

    private fun newStore(): DataStore<Preferences> {
        val file = File.createTempFile("font_test_", ".preferences_pb").also { it.delete(); tmp += it }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        return PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    private fun memStore(): DataStore<Preferences> = object : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    @Test fun defaultsAreCatalogDefaultIds() = runBlocking {
        val p = FontPrefs(newStore())
        assertEquals(FontCatalog.DEFAULT_UI.id, p.uiFontId.first())
        assertEquals(FontCatalog.DEFAULT_DATA.id, p.dataFontId.first())
    }

    @Test fun writeRoundTrips() = runBlocking {
        val p = FontPrefs(memStore())
        p.setUiFontId("inter")
        p.setDataFontId("jetbrains_mono")
        assertEquals("inter", p.uiFontId.first())
        assertEquals("jetbrains_mono", p.dataFontId.first())
    }
}
