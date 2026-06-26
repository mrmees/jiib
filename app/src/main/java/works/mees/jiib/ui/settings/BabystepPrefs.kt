package works.mees.jiib.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * DataStore(Preferences) persistence of the babystep app setting (D-06, Phase 16): the [enabled] toggle
 * (whether the Print-Status Z-babystep row is offered at all — default TRUE) and the [layerCount] (the
 * first-layer window, in layers, during which the row shows — default 5).
 *
 * Copies the [works.mees.jiib.ui.macros.MacroPrefs] / [works.mees.jiib.config.ConnectionStore] shape
 * EXACTLY: the [DataStore] is INJECTED (no `preferencesDataStore` delegate), so it is host-testable. The
 * PRODUCTION instance (its OWN `babystep.preferences_pb`, NOT shared with connection/theme/macros/webcam)
 * is created by [works.mees.jiib.DinghyApp] and exposed via [works.mees.jiib.di.AppContainer].
 *
 * FAIL-SAFE READ CONTRACT (mirrors MacroPrefs/ConnectionStore, D-02): a read [IOException] (corrupt/partial
 * blob) recovers by emitting empty prefs → defaults (enabled=true / layerCount=5), NEVER a crash.
 *
 * WRITE-SCOPE CONTRACT ([[dinghy-compose-write-scope-cancellation]]): the suspend writers do the
 * read-modify-write inside ONE [DataStore.edit]; the UI NEVER calls them from a composition scope — it
 * goes through [works.mees.jiib.di.AppContainer.setBabystepEnabled] / `setBabystepLayers`, which launch
 * on the process-lifetime `writeScope` so a same-frame nav cannot cancel the write on slow flash.
 */
class BabystepPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    /** Whether the Z-babystep row is offered. Default TRUE (D-06). Fail-safe: a read error yields the default. */
    val enabled: Flow<Boolean> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[KEY_ENABLED] ?: DEFAULT_ENABLED }

    /** First-layer window in layers. Default 5 (D-06). Fail-safe: a read error yields the default. */
    val layerCount: Flow<Int> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[KEY_LAYERS] ?: DEFAULT_LAYERS }

    /** Persist the enable toggle. */
    suspend fun setEnabled(on: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ENABLED] = on }
    }

    /**
     * Persist the layer-count, COERCED to a positive integer (`>= 1` — never a zero-layer window). The
     * field is numeric-keyboard-only (Settings is keyboard-permitted), so no free text reaches here; this
     * coerce is the V5 input-validation guard (T-16-05-01). The value gates UI VISIBILITY only — it is
     * never concatenated into a gcode string.
     */
    suspend fun setLayerCount(n: Int) {
        dataStore.edit { prefs -> prefs[KEY_LAYERS] = n.coerceAtLeast(1) }
    }

    companion object {
        const val DEFAULT_ENABLED = true
        const val DEFAULT_LAYERS = 5
        private val KEY_ENABLED = booleanPreferencesKey("babystep_enabled")
        private val KEY_LAYERS = intPreferencesKey("babystep_layers")
    }
}
