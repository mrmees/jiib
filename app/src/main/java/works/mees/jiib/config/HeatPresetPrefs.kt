package works.mees.jiib.config

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
 * Per-printer Heat Presets, persisted as a JSON list under a profile-scoped key (`presets_<profileId>`).
 * Mirrors [works.mees.jiib.ui.move.SavedLocationPrefs] serialization + [TraceStylePrefs] per-printer
 * key scoping. All writes are read-modify-write inside ONE [DataStore.edit]. Fail-safe: a read error
 * yields an empty list. The 12th, independent DataStore file (heat_presets.preferences_pb).
 */
class HeatPresetPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    /** Presets for [profileId], pre-sorted for display. Empty when none / on read error. */
    fun presets(profileId: String): Flow<List<HeatPreset>> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> decode(prefs[key(profileId)]).sortedForDisplay() }

    /** Insert or replace [preset] (matched by id) under [profileId]. */
    suspend fun addOrUpdate(profileId: String, preset: HeatPreset) {
        dataStore.edit { prefs ->
            val current = decode(prefs[key(profileId)]).filterNot { it.id == preset.id }
            prefs[key(profileId)] = JSON.encodeToString(LIST_SERIALIZER, current + preset)
        }
    }

    /** Remove the preset with [id] under [profileId] (idempotent). */
    suspend fun delete(profileId: String, id: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[key(profileId)]).filterNot { it.id == id }
            prefs[key(profileId)] = JSON.encodeToString(LIST_SERIALIZER, current)
        }
    }

    /** Seed [presets] under [profileId] ONLY if it currently has none (new-printer seeding). */
    suspend fun seedIfEmpty(profileId: String, presets: List<HeatPreset>) {
        dataStore.edit { prefs ->
            if (decode(prefs[key(profileId)]).isEmpty()) {
                prefs[key(profileId)] = JSON.encodeToString(LIST_SERIALIZER, presets)
            }
        }
    }

    private fun decode(raw: String?): List<HeatPreset> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { JSON.decodeFromString(LIST_SERIALIZER, raw) }.getOrDefault(emptyList())

    companion object {
        private fun key(profileId: String) = stringPreferencesKey("presets_$profileId")
        private val JSON = Json { ignoreUnknownKeys = true }
        private val LIST_SERIALIZER = ListSerializer(HeatPreset.serializer())
    }
}
