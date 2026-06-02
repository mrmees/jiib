package works.mees.dinghy.ui.macros

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * DataStore(Preferences) persistence of macro visibility prefs (MACRO-03): the user's
 * [bookmarks] (`Set<String>` of macro NAMEs pinned to the Bookmarked launcher) and the
 * [revealHidden] toggle (whether underscore-prefixed System macros are shown — default FALSE, the
 * MACRO-03 underscore-default-hide rule).
 *
 * Copies the [works.mees.dinghy.config.ConnectionStore] / `ThemePrefs` shape EXACTLY: the [DataStore]
 * is INJECTED (no `preferencesDataStore` delegate), so it is host-testable. The PRODUCTION instance
 * (its OWN `macros.preferences_pb`, NOT shared with connection/theme) is created by DinghyApp and
 * exposed via AppContainer in 08-07.
 *
 * FAIL-SAFE READ CONTRACT (mirrors ConnectionStore, D-02): a read [IOException] (corrupt/partial blob)
 * recovers by emitting empty prefs → defaults (empty bookmark set, revealHidden=false), NEVER a crash.
 *
 * HOST-TEST NOTE (per STATE.md 04-01): back-to-back DataStore writes / a second instance to one
 * `.preferences_pb` are not reliably host-testable on the Windows build host (atomic rename races).
 * MacroPrefsTest proves the single-write + read round-trip + the fail-safe default read, exactly as
 * ConnectionStoreTest does; multi-write atomicity is deferred to on-device.
 */
class MacroPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    /** Pinned macro NAMEs. Fail-safe: a read error yields the empty set, never throws. */
    val bookmarks: Flow<Set<String>> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[KEY_BOOKMARKS] ?: emptySet() }

    /** Whether underscore-prefixed System macros are revealed. Default FALSE (MACRO-03). Fail-safe. */
    val revealHidden: Flow<Boolean> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[KEY_REVEAL_HIDDEN] ?: false }

    /** Pin [name] to the Bookmarked launcher (idempotent — adding an existing name is a no-op). */
    suspend fun addBookmark(name: String) {
        dataStore.edit { prefs ->
            prefs[KEY_BOOKMARKS] = (prefs[KEY_BOOKMARKS] ?: emptySet()) + name
        }
    }

    /** Unpin [name] from the Bookmarked launcher (idempotent). */
    suspend fun removeBookmark(name: String) {
        dataStore.edit { prefs ->
            prefs[KEY_BOOKMARKS] = (prefs[KEY_BOOKMARKS] ?: emptySet()) - name
        }
    }

    /** Toggle [name]: add if absent, remove if present. */
    suspend fun toggleBookmark(name: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_BOOKMARKS] ?: emptySet()
            prefs[KEY_BOOKMARKS] = if (name in current) current - name else current + name
        }
    }

    /** Persist whether underscore-prefixed System macros are revealed. */
    suspend fun setRevealHidden(reveal: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_REVEAL_HIDDEN] = reveal }
    }

    companion object {
        private val KEY_BOOKMARKS = stringSetPreferencesKey("bookmarks")
        private val KEY_REVEAL_HIDDEN = booleanPreferencesKey("reveal_hidden")
    }
}
