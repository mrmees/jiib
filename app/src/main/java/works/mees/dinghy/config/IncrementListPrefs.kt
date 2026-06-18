package works.mees.dinghy.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Per-printer increment step lists, persisted as a JSON `Map<controlKey, canonicalString>` under a
 * profile-scoped key (`increments_<profileId>`). Mirrors [HeatPresetPrefs]. The 13th, independent
 * DataStore file (increments.preferences_pb). All writes are read-modify-write inside ONE edit; a
 * read error yields an empty map.
 */
class IncrementListPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    /** The stored control→string map for [profileId] (absent keys mean "use the default"). */
    fun lists(profileId: String): Flow<Map<String, String>> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> decode(prefs[key(profileId)]) }

    /** Set one control's canonical string under [profileId] (read-modify-write). */
    suspend fun setList(profileId: String, controlKey: String, canonical: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[key(profileId)]).toMutableMap()
            current[controlKey] = canonical
            prefs[key(profileId)] = JSON.encodeToString(SERIALIZER, current)
        }
    }

    /** Seed [defaults] under [profileId] ONLY if it currently has none (new-printer seeding). */
    suspend fun seedIfEmpty(profileId: String, defaults: Map<String, String>) {
        dataStore.edit { prefs ->
            if (decode(prefs[key(profileId)]).isEmpty()) {
                prefs[key(profileId)] = JSON.encodeToString(SERIALIZER, defaults)
            }
        }
    }

    private fun decode(raw: String?): Map<String, String> =
        if (raw.isNullOrBlank()) emptyMap()
        else runCatching { JSON.decodeFromString(SERIALIZER, raw) }.getOrDefault(emptyMap())

    companion object {
        private fun key(profileId: String) = stringPreferencesKey("increments_$profileId")
        private val JSON = Json { ignoreUnknownKeys = true }
        private val SERIALIZER = MapSerializer(String.serializer(), String.serializer())
    }
}
