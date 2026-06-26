package works.mees.jiib.ui.move

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Persists the user's named toolhead positions as one JSON array string in DataStore. Order is
 * insertion order; name is the identity (adding an existing name replaces it). Fail-safe: a read
 * error or corrupt blob yields the empty list, never throws.
 */
class SavedLocationPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    val locations: Flow<List<SavedLocation>> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> decode(prefs[KEY_LOCATIONS]) }

    /** Add [loc], replacing any existing entry with the same name. */
    suspend fun add(loc: SavedLocation) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY_LOCATIONS]).filterNot { it.name == loc.name }
            prefs[KEY_LOCATIONS] = JSON.encodeToString(LOCATION_LIST_SERIALIZER, current + loc)
        }
    }

    /** Remove the location named [name] (idempotent). */
    suspend fun remove(name: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY_LOCATIONS]).filterNot { it.name == name }
            prefs[KEY_LOCATIONS] = JSON.encodeToString(LOCATION_LIST_SERIALIZER, current)
        }
    }

    private fun decode(raw: String?): List<SavedLocation> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { JSON.decodeFromString(LOCATION_LIST_SERIALIZER, raw) }.getOrDefault(emptyList())

    companion object {
        private val KEY_LOCATIONS = stringPreferencesKey("locations")
        private val JSON = Json { ignoreUnknownKeys = true }

        /** Explicit list serializer — the reified encodeToString<List<…>> is ambiguous on this Kotlin. */
        private val LOCATION_LIST_SERIALIZER = ListSerializer(SavedLocation.serializer())
    }
}
