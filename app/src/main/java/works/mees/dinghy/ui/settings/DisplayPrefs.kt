package works.mees.dinghy.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.Flow

/**
 * DataStore(Preferences) persistence of the display app settings (§R2, 26.5-05): the [keepScreenOn]
 * toggle (whether the shell holds `FLAG_KEEP_SCREEN_ON` on its window while foregrounded — default
 * TRUE for the dedicated-display use case).
 *
 * Copies the [BabystepPrefs] shape EXACTLY: the [DataStore] is INJECTED (no `preferencesDataStore`
 * delegate), so it is host-testable. The PRODUCTION instance (its OWN `display.preferences_pb`, NOT
 * shared with connection/theme/macros/webcam/profiles/babystep/tracestyle) is created by
 * [works.mees.dinghy.DinghyApp] and exposed via [works.mees.dinghy.di.AppContainer].
 *
 * FAIL-SAFE READ CONTRACT (mirrors BabystepPrefs/MacroPrefs, D-02): a read [java.io.IOException]
 * (corrupt/partial blob) recovers by emitting empty prefs → the default (keepScreenOn=true), NEVER
 * a crash.
 *
 * WRITE-SCOPE CONTRACT ([[dinghy-compose-write-scope-cancellation]]): the UI NEVER calls the suspend
 * [setKeepScreenOn] from a composition scope — it goes through
 * [works.mees.dinghy.di.AppContainer.setKeepScreenOn], which launches on the process-lifetime
 * `writeScope` so a same-frame nav cannot cancel the write on slow flash.
 */
class DisplayPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    /** Whether the screen is held awake while the shell is foregrounded. Default TRUE (§R2). */
    val keepScreenOn: Flow<Boolean>
        get() = TODO("26.5-05 GREEN — implemented after the RED gate")

    /** Persist the keep-screen-on toggle. */
    suspend fun setKeepScreenOn(on: Boolean) {
        TODO("26.5-05 GREEN — implemented after the RED gate")
    }

    companion object {
        const val DEFAULT_KEEP_SCREEN_ON = true
        @Suppress("unused") // consumed by the GREEN implementation
        private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }
}
