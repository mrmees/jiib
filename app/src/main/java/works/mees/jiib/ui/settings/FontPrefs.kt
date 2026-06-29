package works.mees.jiib.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import works.mees.jiib.theme.FontCatalog
import java.io.IOException

/**
 * App-global font selection (sibling of [FontScalePrefs]/[DisplayPrefs]) — the SOLE source of the
 * Interface and Data face ids. Stores catalog ids (never indices). Unknown/missing → catalog default
 * id. INJECTED DataStore (host-testable); the production instance (its OWN `font.preferences_pb`) is
 * built by [works.mees.jiib.JiibApp] and exposed via [works.mees.jiib.di.AppContainer]. Fail-safe:
 * a read IOException recovers to the default id, never crashes. Writes go through
 * `AppContainer.writeScope` ([[dinghy-compose-write-scope-cancellation]]).
 */
class FontPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    val uiFontId: Flow<String> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[KEY_UI] ?: FontCatalog.DEFAULT_UI.id }

    val dataFontId: Flow<String> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[KEY_DATA] ?: FontCatalog.DEFAULT_DATA.id }

    suspend fun setUiFontId(id: String) { dataStore.edit { it[KEY_UI] = id } }
    suspend fun setDataFontId(id: String) { dataStore.edit { it[KEY_DATA] = id } }

    private companion object {
        val KEY_UI = stringPreferencesKey("ui_font_id")
        val KEY_DATA = stringPreferencesKey("data_font_id")
    }
}
