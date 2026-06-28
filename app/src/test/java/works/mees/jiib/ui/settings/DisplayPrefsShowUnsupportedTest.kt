package works.mees.jiib.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.datastore.preferences.core.emptyPreferences

/**
 * Host-side proof that [DisplayPrefs.showUnsupportedTools] defaults false and persists correctly.
 * Mirrors the [DisplayPrefsTest] in-memory DataStore shape to avoid the Windows .tmp→rename race
 * on double-write tests while also exercising the real default-false read path.
 */
class DisplayPrefsShowUnsupportedTest {

    /** In-memory DataStore — avoids Windows temp-file rename race on back-to-back writes. */
    private fun memDataStore(): DataStore<Preferences> = object : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    @Test
    fun showUnsupportedTools_defaultsFalse_andPersists() = runBlocking {
        val prefs = DisplayPrefs(memDataStore())
        assertFalse("default showUnsupportedTools is false", prefs.showUnsupportedTools.first())
        prefs.setShowUnsupportedTools(true)
        assertTrue("setShowUnsupportedTools(true) round-trips", prefs.showUnsupportedTools.first())
    }

    @Test
    fun showUnsupportedTools_setFalseAfterTrue_roundTrips() = runBlocking {
        val prefs = DisplayPrefs(memDataStore())
        prefs.setShowUnsupportedTools(true)
        assertTrue("intermediate true state persisted", prefs.showUnsupportedTools.first())
        prefs.setShowUnsupportedTools(false)
        assertFalse("setShowUnsupportedTools(false) after true round-trips", prefs.showUnsupportedTools.first())
    }
}
