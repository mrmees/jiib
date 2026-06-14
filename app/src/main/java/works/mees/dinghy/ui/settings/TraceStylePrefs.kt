package works.mees.dinghy.ui.settings

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
 * DataStore(Preferences) persistence of per-sensor trace color and show/hide settings (D-14,
 * Phase 26). Stores a flat Map<String, Int (ARGB)> for trace colors and Map<String, Boolean>
 * for trace visibility, keyed by Moonraker sensor object name (e.g. "extruder", "heater_bed").
 *
 * Copies the [BabystepPrefs] / [works.mees.dinghy.ui.macros.MacroPrefs] shape EXACTLY: the
 * [DataStore] is INJECTED (no `preferencesDataStore` delegate), so it is host-testable. The
 * PRODUCTION instance (its OWN `tracestyle.preferences_pb`, NOT shared with any other store)
 * is created by [works.mees.dinghy.DinghyApp] and exposed via [works.mees.dinghy.di.AppContainer].
 *
 * ## Encoding (flat key-stable preference entries)
 * Each sensor's color is stored as a single [intPreferencesKey] named `trace_color_<sensorName>` —
 * an ARGB Int chosen from a fixed 8-color Colorful pool (no free-text injection surface).
 * Each sensor's visibility is stored as a single [booleanPreferencesKey] named
 * `trace_visible_<sensorName>`. The flat-key scheme is stable across reconnects and sensor-count
 * changes — keys for sensors not currently present are simply unused.
 *
 * ## Fail-safe read contract (mirrors BabystepPrefs/MacroPrefs)
 * A read [IOException] (corrupt/partial blob) recovers by emitting empty prefs → defaults
 * (no colors = all inherit seriesColor(i); absent-means-visible for visibility), NEVER a crash.
 *
 * ## Write-scope contract ([[dinghy-compose-write-scope-cancellation]])
 * The suspend writers perform the read-modify-write inside ONE [DataStore.edit]. The UI NEVER
 * calls them from a composition scope — it routes through
 * [works.mees.dinghy.di.AppContainer.setTraceColor] / [works.mees.dinghy.di.AppContainer.setTraceVisibility],
 * which launch on the process-lifetime `writeScope`.
 *
 * ## T-26-03-02 mitigation
 * All DataStore writes run on the process-lifetime `writeScope` (supplied by AppContainer), never
 * a composition scope — guards the P14 switch-revert bug class (the write-scope-cancellation trap).
 */
class TraceStylePrefs(
    private val dataStore: DataStore<Preferences>,
) {
    /**
     * Per-sensor trace colors as a map from sensor object name to ARGB Int. An absent entry
     * means the sensor uses its default [works.mees.dinghy.theme.SeriesColor.seriesColor] token.
     * Fail-safe: a read error yields an empty map → all sensors use token defaults.
     */
    val traceColors: Flow<Map<String, Int>> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                buildMap {
                    prefs.asMap().forEach { (key, value) ->
                        if (key.name.startsWith(PREFIX_COLOR) && value is Int) {
                            put(key.name.removePrefix(PREFIX_COLOR), value)
                        }
                    }
                }
            }

    /**
     * Per-sensor trace visibility as a map from sensor object name to Boolean. An absent entry
     * means the sensor is VISIBLE (absent-means-visible convention — default is on). Fail-safe:
     * a read error yields an empty map → all sensors visible.
     */
    val traceVisibility: Flow<Map<String, Boolean>> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                buildMap {
                    prefs.asMap().forEach { (key, value) ->
                        if (key.name.startsWith(PREFIX_VISIBLE) && value is Boolean) {
                            put(key.name.removePrefix(PREFIX_VISIBLE), value)
                        }
                    }
                }
            }

    /**
     * Persist the trace color for a sensor, as an ARGB Int (T-26-03-01: color is from a fixed
     * 8-color Colorful pool, not arbitrary user text — no injection surface). Read-modify-write
     * happens inside ONE [DataStore.edit] (WR-01: no lost-update race).
     */
    suspend fun setTraceColor(sensorName: String, argb: Int) {
        dataStore.edit { prefs ->
            prefs[intPreferencesKey(PREFIX_COLOR + sensorName)] = argb
        }
    }

    /**
     * Persist the trace visibility for a sensor. [visible]=false hides the trace from the graph;
     * true restores it. Read-modify-write inside ONE [DataStore.edit].
     */
    suspend fun setTraceVisibility(sensorName: String, visible: Boolean) {
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(PREFIX_VISIBLE + sensorName)] = visible
        }
    }

    /**
     * User-selected `temperature_sensor` objects to display in the temperature monitor. Selection
     * is opt-in: an absent key means NOT selected; deselecting removes the key (sparse store).
     * Only entries with [Boolean] value `true` are included in the emitted set. Fail-safe: a read
     * error yields an empty set → nothing selected.
     */
    val selectedSensors: Flow<Set<String>> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                buildSet {
                    prefs.asMap().forEach { (key, value) ->
                        if (key.name.startsWith(PREFIX_SELECTED) && value is Boolean && value) {
                            add(key.name.removePrefix(PREFIX_SELECTED))
                        }
                    }
                }
            }

    /**
     * Persist the selection state for a sensor. [selected]=true adds the sensor; false removes
     * its key entirely (sparse: absent == not selected). Read-modify-write inside ONE [DataStore.edit].
     */
    suspend fun setSensorSelected(sensorName: String, selected: Boolean) {
        dataStore.edit { prefs ->
            val key = booleanPreferencesKey(PREFIX_SELECTED + sensorName)
            if (selected) prefs[key] = true else prefs.remove(key)
        }
    }

    companion object {
        private const val PREFIX_COLOR = "trace_color_"
        private const val PREFIX_VISIBLE = "trace_visible_"
        private const val PREFIX_SELECTED = "trace_selected_"
    }
}
