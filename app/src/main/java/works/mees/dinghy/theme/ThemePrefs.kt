package works.mees.dinghy.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * DataStore(Preferences) persistence of the theme (THEME-02/D-02) — the FIRST DataStore in the repo,
 * and the mechanism that lands BEFORE any editor UI (the editor is Phase 4 SET-01). Persists three
 * things: the theme base (Dark/Light), the S/M/L `--fs` choice, and the sparse custom [TokenDelta]
 * (overridden roles → packed ARGB longs, D-02).
 *
 * DETERMINISTIC FAIL-SAFE CONTRACT (D-02, threat T-03-01): because persistence exists before any
 * validating editor, a corrupt/partial blob MUST NEVER crash or black-screen the printer display.
 * The read path ([sanitize]) ALWAYS resolves to a complete, usable ([ThemeBase], [TokenDelta], Float)
 * triple and never throws:
 *   • unknown/unparseable base       → fall back to the default base (Dark)
 *   • unknown/unparseable fs choice   → fall back to the default M (1.15f)
 *   • a malformed/partial delta map   → keep the valid entries, silently drop the junk ones
 *   • an out-of-range/garbage role/ARGB → drop just that override (inherit the base token)
 *
 * The sanitization is a PURE function over the stored primitives ([sanitize]) so it is unit-testable
 * host-side with no DataStore I/O (ThemePrefsFallbackTest stays host-pure). [flow] reads DataStore,
 * maps each [Preferences] snapshot through [sanitize], and (per DataStore guidance) recovers from a
 * read [IOException] by emitting empty prefs — which [sanitize] turns into the full default theme.
 */
class ThemePrefs(
    private val dataStore: DataStore<Preferences>,
) {
    /** Sanitized theme read — never throws; always a complete, usable theme (D-02). */
    val flow: Flow<Resolved> =
        dataStore.data
            .catch { e ->
                // A corrupt store / read error is a fail-safe case, not a crash (D-02).
                if (e is IOException) emit(emptyPreferences()) else throw e
            }
            .map { prefs ->
                sanitize(
                    rawBase = prefs[KEY_BASE],
                    rawFs = prefs[KEY_FS],
                    rawRoleKeys = prefs[KEY_DELTA_ROLES],
                    readArgb = { role -> prefs[longPreferencesKey(deltaArgbKey(role))] },
                )
            }

    suspend fun setBase(base: ThemeBase) {
        dataStore.edit { it[KEY_BASE] = base.name }
    }

    suspend fun setFs(choice: FontScale) {
        dataStore.edit { it[KEY_FS] = choice.name }
    }

    /** Persist the sparse delta: store the set of overridden role names + one ARGB long per role. */
    suspend fun setDeltas(delta: TokenDelta) {
        dataStore.edit { prefs ->
            // Clear any previously-persisted per-role ARGB values first.
            prefs[KEY_DELTA_ROLES]?.forEach { roleName ->
                prefs.remove(longPreferencesKey(deltaArgbKey(roleName)))
            }
            prefs[KEY_DELTA_ROLES] = delta.overrides.keys.map { it.name }.toSet()
            for ((role, argb) in delta.overrides) {
                // Persist the unsigned 32-bit ARGB form (TokenDelta normalizes, masked here for safety).
                prefs[longPreferencesKey(deltaArgbKey(role.name))] = argb and 0xFFFFFFFFL
            }
        }
    }

    /** A complete, ready-to-resolve theme triple. [resolve] turns it into [ThemeTokens]. */
    data class Resolved(
        val base: ThemeBase,
        val deltas: TokenDelta,
        val fs: Float,
    )

    companion object {
        private val KEY_BASE = stringPreferencesKey("theme_base")
        private val KEY_FS = stringPreferencesKey("fs_choice")
        private val KEY_DELTA_ROLES = stringSetPreferencesKey("delta_roles")
        private fun deltaArgbKey(roleName: String) = "delta_argb_$roleName"

        /** The fail-safe default theme: Dark base, no overrides, M text size. */
        val DEFAULT = Resolved(ThemeBase.Dark, TokenDelta.EMPTY, FontScale.M.multiplier)

        /**
         * PURE fail-safe sanitizer (D-02) — the heart of the fail-safe contract, deliberately free of
         * DataStore so it is host-pure-testable. Given the raw persisted primitives (any of which may
         * be null/garbage), returns a complete, usable [Resolved]. NEVER throws.
         *
         * @param rawBase    persisted base name (or null)
         * @param rawFs      persisted fs choice name (or null)
         * @param rawRoleKeys persisted set of overridden role names (or null)
         * @param readArgb   reads the ARGB long for a given role name (null when absent)
         */
        fun sanitize(
            rawBase: String?,
            rawFs: String?,
            rawRoleKeys: Set<String>?,
            readArgb: (String) -> Long?,
        ): Resolved {
            // base: unknown/unparseable → Dark default.
            val base = enumValuesOrNull<ThemeBase>(rawBase) ?: ThemeBase.Dark

            // fs: unknown/unparseable → M default.
            val fs = (enumValuesOrNull<FontScale>(rawFs) ?: FontScale.M).multiplier

            // deltas: keep valid role+ARGB pairs, silently drop every junk entry.
            val valid = mutableMapOf<TokenDelta.Role, Long>()
            for (roleName in rawRoleKeys.orEmpty()) {
                val role = enumValuesOrNull<TokenDelta.Role>(roleName) ?: continue // unknown role → drop
                val argb = readArgb(roleName) ?: continue                          // missing value → drop
                if (!argb.isValidArgb()) continue                                  // garbage ARGB → drop just this
                valid[role] = argb
            }
            return Resolved(base, TokenDelta(valid), fs)
        }

        /** Case-exact enum lookup that returns null instead of throwing on an unknown/null name. */
        private inline fun <reified E : Enum<E>> enumValuesOrNull(name: String?): E? {
            if (name == null) return null
            return enumValues<E>().firstOrNull { it.name == name }
        }

        /**
         * A valid persisted ARGB occupies the low 32 bits (it was written from an Int via `.toLong()`
         * or as an unsigned 0..0xFFFFFFFF). Anything outside [0, 0xFFFFFFFF] is garbage from a corrupt
         * blob — drop that single override (the role then inherits the base token, D-02).
         */
        private fun Long.isValidArgb(): Boolean = this in 0L..0xFFFFFFFFL
    }
}
