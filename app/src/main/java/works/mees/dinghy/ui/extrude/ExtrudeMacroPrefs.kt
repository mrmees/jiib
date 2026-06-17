package works.mees.dinghy.ui.extrude

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Extrude-screen-scoped pinned filament macro NAMEs — independent of the global Macros bookmarks. */
class ExtrudeMacroPrefs(private val dataStore: DataStore<Preferences>) {
    val pins: Flow<Set<String>> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[KEY_PINS] ?: emptySet() }

    suspend fun togglePin(name: String) {
        dataStore.edit { prefs ->
            val cur = prefs[KEY_PINS] ?: emptySet()
            prefs[KEY_PINS] = if (name in cur) cur - name else cur + name
        }
    }

    companion object {
        private val KEY_PINS = stringSetPreferencesKey("extrude_macro_pins")
    }
}
