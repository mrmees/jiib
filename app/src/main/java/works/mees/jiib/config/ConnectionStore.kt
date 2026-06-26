package works.mees.jiib.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * DataStore(Preferences) persistence of the user-entered Moonraker connection (CONN-01, D-04) — the
 * runtime config SOURCE that replaces the static [DevConfig] once a user saves a connection in Settings.
 * Copies the [works.mees.jiib.theme.ThemePrefs] shape exactly: injected [DataStore], a fail-safe
 * read [config] flow, suspend writers, and a PURE host-testable [sanitize] companion.
 *
 * FAIL-SAFE READ CONTRACT (mirrors ThemePrefs, D-02): a read [IOException] (corrupt/partial blob)
 * recovers by emitting empty prefs — which [sanitize] turns into `null` (the first-run Connect prompt,
 * D-11), NEVER a crash. An empty store and [clear] both resolve to `null`:
 *   - empty store → null  → first-run Connect prompt (D-11)
 *   - [clear]     → null  → a config-cleared-while-running takes the 04-03 service to a clean idle
 *                           with no leaked connection, routing back to Connect (review #12)
 *
 * V5 input validation lives in the PURE [sanitize] (host/port range, host trim, blank-key → null) so a
 * malformed host or out-of-range port is rejected BEFORE it can drive the spine.
 *
 * The [DataStore] is INJECTED (no `preferencesDataStore` delegate here) — the JiibApp (04-03) owns
 * the actual `connection.preferences_pb` file, kept SEPARATE from `theme.preferences_pb` for a cleaner
 * API-key redaction boundary (T-04-01-I; PATTERNS DECIDE note).
 */
class ConnectionStore(
    private val dataStore: DataStore<Preferences>,
) {
    /** Sanitized connection read — never throws; `null` signals the Connect prompt (D-11 / review #12). */
    val config: Flow<ConnectionConfig?> =
        dataStore.data
            .catch { e ->
                // A corrupt store / read error is a fail-safe case, not a crash (D-02).
                if (e is IOException) emit(emptyPreferences()) else throw e
            }
            .map { prefs -> sanitize(prefs[KEY_HOST], prefs[KEY_PORT], prefs[KEY_API_KEY], prefs[KEY_USE_SECURE]) }

    /** Persist a connection. Writes host/port and either writes or clears the optional API key. */
    suspend fun save(config: ConnectionConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_HOST] = config.host
            prefs[KEY_PORT] = config.port
            val key = config.apiKey
            if (key.isNullOrBlank()) prefs.remove(KEY_API_KEY) else prefs[KEY_API_KEY] = key
            prefs[KEY_USE_SECURE] = config.useSecure
        }
    }

    /**
     * Clear the persisted connection. MUST drive [config] to emit `null` (review #12: a config-clear
     * while the service runs takes it to a clean idle, no leaked connection, routing back to Connect).
     */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_HOST = stringPreferencesKey("host")
        private val KEY_PORT = intPreferencesKey("port")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_USE_SECURE = booleanPreferencesKey("use_secure")

        /** Inclusive valid TCP port range. */
        private val PORT_RANGE = 1..65535

        /**
         * PURE input-validation sanitizer (V5) — host-pure-testable with no DataStore/IO. Returns a
         * usable [ConnectionConfig] or `null`:
         *   - null/blank host                 → null  (no connection → Connect prompt, D-11)
         *   - null or out-of-range port       → null  (reject before persist)
         *   - otherwise                       → trimmed host, valid port, blank apiKey normalized to null
         *   - useSecure null (missing key — every pre-R7 store) → false (plain ws/http, migration-safe)
         */
        fun sanitize(host: String?, port: Int?, apiKey: String?, useSecure: Boolean? = null): ConnectionConfig? {
            val trimmedHost = host?.trim()
            if (trimmedHost.isNullOrBlank()) return null
            if (port == null || port !in PORT_RANGE) return null
            return ConnectionConfig(
                host = trimmedHost,
                port = port,
                apiKey = apiKey?.takeIf { it.isNotBlank() },
                useSecure = useSecure ?: false,
            )
        }
    }
}
