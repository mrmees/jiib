package works.mees.dinghy.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import works.mees.dinghy.theme.FontScale
import java.io.File

class FontScalePrefsTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { File(tmp.newFolder(), "$name.preferences_pb") })

    /** In-memory DataStore for tests that issue multiple writes on the same store — avoids the
     * Windows host .tmp→rename race documented in [DisplayPrefsTest]. */
    private fun memStore(): DataStore<Preferences> = object : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    @Test fun defaultsToM() = runTest {
        val prefs = FontScalePrefs(store("fs1.pb"))
        assertEquals(FontScale.M, prefs.fontScale.first())
    }

    @Test fun persistsSetValue() = runTest {
        val prefs = FontScalePrefs(store("fs2.pb"))
        prefs.setFontScale(FontScale.L)
        assertEquals(FontScale.L, prefs.fontScale.first())
    }

    @Test fun migrateSeedsOnceThenIsIdempotent() = runTest {
        // Two writes on same store — use in-memory to avoid Windows .tmp→rename race (see memStore()).
        val prefs = FontScalePrefs(memStore())
        prefs.migrateSeed(FontScale.S)
        assertEquals(FontScale.S, prefs.fontScale.first())
        prefs.migrateSeed(FontScale.L)
        assertEquals(FontScale.S, prefs.fontScale.first())
    }

    @Test fun migrateDoesNotClobberAnExplicitValue() = runTest {
        // Two writes on same store — use in-memory to avoid Windows .tmp→rename race (see memStore()).
        val prefs = FontScalePrefs(memStore())
        prefs.setFontScale(FontScale.L)
        prefs.migrateSeed(FontScale.S)
        assertEquals(FontScale.L, prefs.fontScale.first())
    }
}
