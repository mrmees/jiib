package works.mees.jiib.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * DataStore(Preferences) persistence of the display app settings (§R2, 26.5-05): the [keepScreenOn]
 * toggle (whether the shell holds `FLAG_KEEP_SCREEN_ON` on its window while foregrounded — default
 * TRUE for the dedicated-display use case) and the [webcamEnabled] app-global toggle (whether the
 * webcam tile is offered app-wide — moved here from per-profile 2026-06-15, default TRUE).
 *
 * Copies the [BabystepPrefs] shape EXACTLY: the [DataStore] is INJECTED (no `preferencesDataStore`
 * delegate), so it is host-testable. The PRODUCTION instance (its OWN `display.preferences_pb`, NOT
 * shared with connection/theme/macros/webcam/profiles/babystep/tracestyle) is created by
 * [works.mees.jiib.JiibApp] and exposed via [works.mees.jiib.di.AppContainer].
 *
 * FAIL-SAFE READ CONTRACT (mirrors BabystepPrefs/MacroPrefs, D-02): a read [java.io.IOException]
 * (corrupt/partial blob) recovers by emitting empty prefs → the default (keepScreenOn=true), NEVER
 * a crash.
 *
 * WRITE-SCOPE CONTRACT ([[dinghy-compose-write-scope-cancellation]]): the UI NEVER calls the suspend
 * [setKeepScreenOn] from a composition scope — it goes through
 * [works.mees.jiib.di.AppContainer.setKeepScreenOn], which launches on the process-lifetime
 * `writeScope` so a same-frame nav cannot cancel the write on slow flash.
 */
class DisplayPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    /**
     * Whether the screen is held awake while the shell is foregrounded. Default TRUE (§R2).
     * Fail-safe: a read error yields the default.
     */
    val keepScreenOn: Flow<Boolean> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[KEY_KEEP_SCREEN_ON] ?: DEFAULT_KEEP_SCREEN_ON }

    /** Persist the keep-screen-on toggle. */
    suspend fun setKeepScreenOn(on: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_KEEP_SCREEN_ON] = on }
    }

    /**
     * Whether the webcam tile/surface is offered app-wide (moved from per-profile `Profile.webcamEnabled`
     * to app-global, 2026-06-15). Default TRUE — preserves today's always-available webcam behavior; a
     * printer with no cams still hides the tile via [AppContainer.webcamTileGate] (`count > 0`).
     * Fail-safe: a read error yields the default.
     */
    val webcamEnabled: Flow<Boolean> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[KEY_WEBCAM_ENABLED] ?: DEFAULT_WEBCAM_ENABLED }

    /** Persist the app-global webcam-enabled toggle. */
    suspend fun setWebcamEnabled(on: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_WEBCAM_ENABLED] = on }
    }

    companion object {
        const val DEFAULT_KEEP_SCREEN_ON = true
        private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")

        const val DEFAULT_WEBCAM_ENABLED = true
        private val KEY_WEBCAM_ENABLED = booleanPreferencesKey("webcam_enabled")
    }
}
