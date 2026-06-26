package works.mees.jiib.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import works.mees.jiib.theme.FontScale
import java.io.IOException

/**
 * App-global font scale (S/M/L) — process-scoped, connection-INDEPENDENT (sibling of
 * [DisplayPrefs]/[BabystepPrefs]). Replaces the retired per-printer `Profile.fsChoice` as the SOLE
 * source of `--fs`. Stores the [FontScale] as its `.name`; unknown/missing decodes to [FontScale.M].
 */
class FontScalePrefs(
    private val dataStore: DataStore<Preferences>,
) {
    val fontScale: Flow<FontScale> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                prefs[KEY_FS]?.let { runCatching { FontScale.valueOf(it) }.getOrNull() } ?: DEFAULT
            }

    suspend fun setFontScale(choice: FontScale) {
        dataStore.edit { it[KEY_FS] = choice.name }
    }

    /**
     * One-time migration seed (must NOT block on an active profile). Seeds [seed] ONLY when the value
     * has never been written and the sentinel is unset; idempotent thereafter.
     */
    suspend fun migrateSeed(seed: FontScale) {
        dataStore.edit { prefs ->
            if (prefs[KEY_MIGRATED] == true) return@edit
            if (prefs[KEY_FS] == null) prefs[KEY_FS] = seed.name
            prefs[KEY_MIGRATED] = true
        }
    }

    companion object {
        val DEFAULT = FontScale.M
        private val KEY_FS = stringPreferencesKey("font_scale")
        private val KEY_MIGRATED = booleanPreferencesKey("font_scale_migrated_v1")
    }
}
