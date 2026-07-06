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
 * DataStore(Preferences) persistence of per-sensor trace color and show/hide settings (D-14,
 * Phase 26). Stores a flat Map<String, Int (ARGB)> for trace colors and Map<String, Boolean>
 * for trace visibility, keyed by Moonraker sensor object name (e.g. "extruder", "heater_bed").
 *
 * Copies the [BabystepPrefs] / [works.mees.jiib.ui.macros.MacroPrefs] shape EXACTLY: the
 * [DataStore] is INJECTED (no `preferencesDataStore` delegate), so it is host-testable. The
 * PRODUCTION instance (its OWN `tracestyle.preferences_pb`, NOT shared with any other store)
 * is created by [works.mees.jiib.JiibApp] and exposed via [works.mees.jiib.di.AppContainer].
 *
 * ## PER-PRINTER scoping (pre-merge review fix, 2026-06-14)
 * Trace color / visibility / selection are scoped by the ACTIVE PROFILE'S stable UUID
 * ([works.mees.jiib.config.Profile.id]) — the [WebcamPrefs] precedent ("re-key on the profile id,
 * not host"). Without scoping, two printers that share sensor names (`extruder`, `heater_bed`) would
 * BLEED trace config into each other, and a `temperature_sensor` selected on printer A would render
 * as a ghost 0° row on printer B. Every read flow + writer therefore takes a `profileId: String`.
 *
 * ## Encoding (flat, profile-scoped key-stable preference entries)
 * Each sensor's color is `trace_color_<profileId>_<sensorName>` (an [intPreferencesKey], ARGB Int from
 * a fixed 8-color Colorful pool — no free-text injection surface). Visibility is
 * `trace_visible_<profileId>_<sensorName>` ([booleanPreferencesKey]); selection is
 * `trace_selected_<profileId>_<sensorName>`. A read for a profile builds the exact
 * `<prefix><profileId>_` scope string and strips it to rebuild the per-sensor map — keys for OTHER
 * profiles (different id) never match. The sensor name segment may itself contain `_`/spaces (e.g.
 * `temperature_sensor chamber`); since the scope string is the exact known prefix, `removePrefix`
 * recovers the full sensor name unambiguously.
 *
 * ## One-time migration ([migrateUnscopedTo])
 * Legacy UNSCOPED keys (`trace_color_<sensor>` etc.) from before scoping are copied to the active
 * profile on first launch, then removed; a `trace_migrated_v1` sentinel makes it idempotent (runs
 * exactly once, before any scoped keys exist). The owner's pre-scoping dev prefs carry forward to
 * whatever profile is active first.
 *
 * ## Fail-safe read contract (mirrors BabystepPrefs/MacroPrefs)
 * A read [IOException] (corrupt/partial blob) recovers by emitting empty prefs → defaults
 * (no colors = all inherit seriesColor(i); absent-means-visible for visibility), NEVER a crash.
 *
 * ## Write-scope contract ([[dinghy-compose-write-scope-cancellation]])
 * The suspend writers perform the read-modify-write inside ONE [DataStore.edit]. The UI NEVER
 * calls them from a composition scope — it routes through [works.mees.jiib.di.AppContainer], which
 * supplies the active profile id and launches on the process-lifetime `writeScope`.
 */
class TraceStylePrefs(
    private val dataStore: DataStore<Preferences>,
) {
    /**
     * Per-sensor trace colors for [profileId] as a map from sensor object name to ARGB Int. An absent
     * entry means the sensor uses its default [works.mees.jiib.theme.SeriesColor.seriesColor] token.
     * Fail-safe: a read error yields an empty map → all sensors use token defaults.
     */
    fun traceColors(profileId: String): Flow<Map<String, Int>> {
        val scope = PREFIX_COLOR + profileId + SEP
        return dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                buildMap {
                    prefs.asMap().forEach { (key, value) ->
                        if (key.name.startsWith(scope) && value is Int) {
                            put(key.name.removePrefix(scope), value)
                        }
                    }
                }
            }
    }

    /**
     * Per-sensor trace visibility for [profileId]. An absent entry means the sensor is VISIBLE
     * (absent-means-visible convention). Fail-safe: a read error yields an empty map → all visible.
     */
    fun traceVisibility(profileId: String): Flow<Map<String, Boolean>> {
        val scope = PREFIX_VISIBLE + profileId + SEP
        return dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                buildMap {
                    prefs.asMap().forEach { (key, value) ->
                        if (key.name.startsWith(scope) && value is Boolean) {
                            put(key.name.removePrefix(scope), value)
                        }
                    }
                }
            }
    }

    /**
     * User-selected `temperature_sensor` objects to display for [profileId]. Selection is opt-in: an
     * absent key means NOT selected; deselecting removes the key (sparse store). Only entries with
     * [Boolean] value `true` are included. Fail-safe: a read error yields an empty set.
     */
    fun selectedSensors(profileId: String): Flow<Set<String>> {
        val scope = PREFIX_SELECTED + profileId + SEP
        return dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                buildSet {
                    prefs.asMap().forEach { (key, value) ->
                        if (key.name.startsWith(scope) && value is Boolean && value) {
                            add(key.name.removePrefix(scope))
                        }
                    }
                }
            }
    }

    /**
     * Persist the trace color for a sensor under [profileId], as an ARGB Int (color is from a fixed
     * 8-color Colorful pool — no injection surface). Read-modify-write inside ONE [DataStore.edit].
     */
    suspend fun setTraceColor(profileId: String, sensorName: String, argb: Int) {
        dataStore.edit { prefs -> prefs[intPreferencesKey(colorKey(profileId, sensorName))] = argb }
    }

    /**
     * Persist the trace visibility for a sensor under [profileId]. [visible]=false hides the trace from
     * the graph; true restores it. Read-modify-write inside ONE [DataStore.edit].
     */
    suspend fun setTraceVisibility(profileId: String, sensorName: String, visible: Boolean) {
        dataStore.edit { prefs -> prefs[booleanPreferencesKey(visibleKey(profileId, sensorName))] = visible }
    }

    /**
     * Persist the selection state for a sensor under [profileId]. [selected]=true adds the sensor;
     * false removes its key entirely (sparse: absent == not selected). One [DataStore.edit].
     */
    suspend fun setSensorSelected(profileId: String, sensorName: String, selected: Boolean) {
        dataStore.edit { prefs ->
            val key = booleanPreferencesKey(selectedKey(profileId, sensorName))
            if (selected) prefs[key] = true else prefs.remove(key)
        }
    }

    /**
     * One-time per-[profileId] DEFAULT selection (Design A, 2026-07-05): on a printer's first visit
     * select every currently-[available] `temperature_sensor` object so the monitoring page shows all
     * thermometers out of the box, then the user configures DOWN. Idempotent via a per-profile sentinel
     * ([sensorsDefaultedKey]) so it runs exactly once per printer:
     *  - sentinel already set → no-op (respect every later edit, including deselect-to-none);
     *  - sentinel unset + the profile ALREADY has selection keys → preserve them (an install configured
     *    under the old opt-in default), only stamp the sentinel;
     *  - sentinel unset + NO selection keys → brand-new: write every [available] sensor as selected.
     * All in ONE [DataStore.edit] (mirrors the other writers' RMW discipline).
     */
    suspend fun applyDefaultSensorSelection(profileId: String, available: List<String>) {
        dataStore.edit { prefs ->
            if (prefs[sensorsDefaultedKey(profileId)] == true) return@edit
            val selectedScope = PREFIX_SELECTED + profileId + SEP
            val hasExistingSelection = prefs.asMap().keys.any { it.name.startsWith(selectedScope) }
            if (!hasExistingSelection) {
                for (name in available) prefs[booleanPreferencesKey(selectedKey(profileId, name))] = true
            }
            prefs[sensorsDefaultedKey(profileId)] = true
        }
    }

    /**
     * One-time migration of legacy UNSCOPED `trace_*_<sensor>` entries into [profileId]-scoped keys.
     * Idempotent via the [MIGRATED_KEY] sentinel — runs exactly once (before any scoped keys exist, so
     * there is no legacy-vs-scoped ambiguity); subsequent calls (e.g. on every profile change) no-op.
     * The legacy entries are removed after copying so they never linger or re-migrate.
     */
    suspend fun migrateUnscopedTo(profileId: String) {
        dataStore.edit { prefs ->
            if (prefs[MIGRATED_KEY] == true) return@edit
            // Already-scoped prefixes for THIS profile. DataStore serializes writes, so the only scoped
            // keys that can exist before the sentinel is set are racing scoped writes for this same
            // profileId (the intents use activeProfileId.value). SKIP those — re-scoping them would
            // double-scope (`trace_color_<pid>_<pid>_x`) and drop the correct key (Codex re-review fix).
            val colorScope = PREFIX_COLOR + profileId + SEP
            val visibleScope = PREFIX_VISIBLE + profileId + SEP
            val selectedScope = PREFIX_SELECTED + profileId + SEP
            // Snapshot first — we mutate `prefs` while iterating the legacy entries.
            val legacy = prefs.asMap().toMap()
            for ((key, value) in legacy) {
                val n = key.name
                when {
                    // Leave already-scoped (racing) writes for this profile untouched.
                    n.startsWith(colorScope) || n.startsWith(visibleScope) || n.startsWith(selectedScope) -> {}
                    n.startsWith(PREFIX_COLOR) && value is Int -> {
                        prefs[intPreferencesKey(colorKey(profileId, n.removePrefix(PREFIX_COLOR)))] = value
                        prefs.remove(key)
                    }
                    n.startsWith(PREFIX_VISIBLE) && value is Boolean -> {
                        prefs[booleanPreferencesKey(visibleKey(profileId, n.removePrefix(PREFIX_VISIBLE)))] = value
                        prefs.remove(key)
                    }
                    n.startsWith(PREFIX_SELECTED) && value is Boolean -> {
                        prefs[booleanPreferencesKey(selectedKey(profileId, n.removePrefix(PREFIX_SELECTED)))] = value
                        prefs.remove(key)
                    }
                }
            }
            prefs[MIGRATED_KEY] = true
        }
    }

    companion object {
        private const val PREFIX_COLOR = "trace_color_"
        private const val PREFIX_VISIBLE = "trace_visible_"
        private const val PREFIX_SELECTED = "trace_selected_"

        /** Per-profile idempotence sentinel for [applyDefaultSensorSelection] (Design A, 2026-07-05). */
        private const val PREFIX_SENSORS_DEFAULTED = "trace_sensors_defaulted_"

        /** Separator between the profile id and the sensor name in a scoped key. */
        private const val SEP = "_"

        /** Idempotence sentinel for [migrateUnscopedTo] (a Boolean key in the same store). */
        private val MIGRATED_KEY = booleanPreferencesKey("trace_migrated_v1")

        fun colorKey(profileId: String, sensorName: String) = PREFIX_COLOR + profileId + SEP + sensorName
        fun visibleKey(profileId: String, sensorName: String) = PREFIX_VISIBLE + profileId + SEP + sensorName
        fun selectedKey(profileId: String, sensorName: String) = PREFIX_SELECTED + profileId + SEP + sensorName

        /** The per-profile sentinel key marking that [applyDefaultSensorSelection] has run for a printer. */
        fun sensorsDefaultedKey(profileId: String) =
            booleanPreferencesKey(PREFIX_SENSORS_DEFAULTED + profileId)
    }
}
