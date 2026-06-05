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

    /**
     * The GLOBAL theme TUPLE (D-03/D-09) — the no-active-profile idle theme AND the new-profile default
     * look. Never throws: a corrupt/partial blob fails safe per-entry through [sanitizeTuple] to a
     * complete, usable [ThemeTuple]. This is the source the no-active branch of `AppContainer.seedTheme`
     * collects REACTIVELY (WR-02), so a global edit while idle re-emits.
     */
    val tupleFlow: Flow<ThemeTuple> =
        dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }
            .map { prefs ->
                sanitizeTuple(
                    rawSeed = prefs[KEY_SEED],
                    rawDark = prefs[KEY_DARK],
                    rawMode = prefs[KEY_MODE],
                    rawShift = prefs[KEY_SHIFT],
                    rawMaxItems = prefs[KEY_MAX_ITEMS],
                    rawFs = prefs[KEY_FS],
                    rawOverrides = readOverrides(prefs),
                )
            }

    suspend fun setSeed(seedHex: String) {
        dataStore.edit { it[KEY_SEED] = seedHex }
    }

    suspend fun setDark(dark: Boolean) {
        dataStore.edit { it[KEY_DARK] = dark }
    }

    suspend fun setMode(mode: String) {
        dataStore.edit { it[KEY_MODE] = mode }
    }

    suspend fun setShift(shift: Int) {
        dataStore.edit { it[KEY_SHIFT] = shift }
    }

    suspend fun setMaxItems(maxItems: Int) {
        dataStore.edit { it[KEY_MAX_ITEMS] = maxItems }
    }

    /** Persist the sparse pool overrides: one ARGB long per pool-index-as-string. Replaces the whole set. */
    suspend fun setOverrides(overrides: Map<String, Long>) {
        dataStore.edit { prefs ->
            prefs[KEY_OVERRIDE_KEYS]?.forEach { k ->
                prefs.remove(longPreferencesKey(overrideArgbKey(k)))
            }
            prefs[KEY_OVERRIDE_KEYS] = overrides.keys.toSet()
            for ((k, argb) in overrides) {
                prefs[longPreferencesKey(overrideArgbKey(k))] = argb and 0xFFFFFFFFL
            }
        }
    }

    @Deprecated("Retired — use setDark(Boolean) (seed-only chrome, D-04); deleted in 15-06.", level = DeprecationLevel.WARNING)
    suspend fun setBase(base: ThemeBase) {
        dataStore.edit { it[KEY_BASE] = base.name }
    }

    suspend fun setFs(choice: FontScale) {
        dataStore.edit { it[KEY_FS] = choice.name }
    }

    /** Persist the sparse delta: store the set of overridden role names + one ARGB long per role. */
    @Deprecated("Retired — per-role chrome override is gone (seed owns chrome, D-04); deleted in 15-06.", level = DeprecationLevel.WARNING)
    @Suppress("DEPRECATION")
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

    /**
     * The complete, validated theme TUPLE (D-03/15-05) — the generate-and-cache inputs the
     * [ThemeResolver] applies. `poolOverrides` is Int-keyed/[Color]-valued here (the sanitized runtime
     * form); persistence stores the String→Long wire form. NEVER carries a baked [ThemeTokens].
     */
    data class ThemeTuple(
        val seedHex: String,
        val dark: Boolean,
        val paletteMode: String,
        val poolShift: Int,
        val maxItems: Int,
        val poolOverrides: Map<Int, Long>,
        val fs: Float,
    )

    /**
     * @deprecated (D-04) — the old per-role resolved triple. Kept so the still-standing SettingsScreen +
     * the legacy theme tests compile at THIS wave; deleted in 15-06. [resolve] turns it into [ThemeTokens].
     */
    @Deprecated("Retired — use ThemeTuple (the generate-and-cache model); deleted in 15-06.", level = DeprecationLevel.WARNING)
    @Suppress("DEPRECATION")
    data class Resolved(
        val base: ThemeBase,
        val deltas: TokenDelta,
        val fs: Float,
    )

    companion object {
        // Tuple keys (15-05) — the live persisted theme.
        private val KEY_SEED = stringPreferencesKey("theme_seed")
        private val KEY_DARK = androidx.datastore.preferences.core.booleanPreferencesKey("theme_dark")
        private val KEY_MODE = stringPreferencesKey("theme_mode")
        private val KEY_SHIFT = androidx.datastore.preferences.core.intPreferencesKey("theme_shift")
        private val KEY_MAX_ITEMS = androidx.datastore.preferences.core.intPreferencesKey("theme_max_items")
        private val KEY_OVERRIDE_KEYS = stringSetPreferencesKey("pool_override_keys")
        private fun overrideArgbKey(idxName: String) = "pool_override_argb_$idxName"

        // Legacy keys (retained for the deprecated base/delta path; deleted in 15-06).
        private val KEY_BASE = stringPreferencesKey("theme_base")
        private val KEY_FS = stringPreferencesKey("fs_choice")
        private val KEY_DELTA_ROLES = stringSetPreferencesKey("delta_roles")
        private fun deltaArgbKey(roleName: String) = "delta_argb_$roleName"

        // ---- Tuple defaults + validation (the fail-safe contract, V5/T-15-05-01) ---------------------

        /** The validated default seed + mode + pool (D-02 out-of-box look). */
        const val DEFAULT_SEED: String = "#3f78ff"
        const val DEFAULT_MODE: String = "Colorful"
        const val DEFAULT_SHIFT: Int = 0
        const val DEFAULT_MAX_ITEMS: Int = 4
        private val VALID_MODES = setOf("Colorful", "Simple", "HighContrast")
        private val HEX_SEED = Regex("^#?[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$")
        private val SHIFT_RANGE = 0..360
        private val MAX_ITEMS_RANGE = 1..64

        /** The fail-safe default TUPLE: default seed, dark, Colorful, no shift, 4 items, no overrides, M fs. */
        val TUPLE_DEFAULT = ThemeTuple(
            seedHex = DEFAULT_SEED,
            dark = true,
            paletteMode = DEFAULT_MODE,
            poolShift = DEFAULT_SHIFT,
            maxItems = DEFAULT_MAX_ITEMS,
            poolOverrides = emptyMap(),
            fs = FontScale.M.multiplier,
        )

        /** Read the sparse String→Long pool overrides off a Preferences snapshot. */
        private fun readOverrides(prefs: Preferences): Map<String, Long> {
            val keys = prefs[KEY_OVERRIDE_KEYS] ?: return emptyMap()
            val out = mutableMapOf<String, Long>()
            for (k in keys) {
                prefs[longPreferencesKey(overrideArgbKey(k))]?.let { out[k] = it }
            }
            return out
        }

        /**
         * PURE per-entry fail-safe sanitizer for the theme TUPLE (D-03, V5/T-15-05-01). Given the raw
         * persisted primitives (any of which may be null/garbage), returns a complete usable [ThemeTuple].
         * NEVER throws, NEVER black-screens:
         *   • unparseable seed (not 6/8-digit hex)     → default seed
         *   • bad mode (not Colorful|Simple|HighContrast) → Colorful
         *   • out-of-range poolShift (0..360) / maxItems (1..64) → defaults
         *   • poolOverrides: parse each String key → Int index, validate the ARGB; DROP only the bad
         *     entry, KEEP the good ones (kotlinx ignoreUnknownKeys does NOT cover malformed VALUES, so a
         *     String→Long map + this PER-ENTRY parse is what keeps one bad slot from nuking the whole map).
         */
        fun sanitizeTuple(
            rawSeed: String?,
            rawDark: Boolean?,
            rawMode: String?,
            rawShift: Int?,
            rawMaxItems: Int?,
            rawFs: String?,
            rawOverrides: Map<String, Long>?,
        ): ThemeTuple {
            val seed = if (rawSeed != null && HEX_SEED.matches(rawSeed)) rawSeed else DEFAULT_SEED
            val dark = rawDark ?: true
            val mode = if (rawMode in VALID_MODES) rawMode!! else DEFAULT_MODE
            val shift = if (rawShift != null && rawShift in SHIFT_RANGE) rawShift else DEFAULT_SHIFT
            val maxItems = if (rawMaxItems != null && rawMaxItems in MAX_ITEMS_RANGE) rawMaxItems else DEFAULT_MAX_ITEMS
            val fs = (enumValuesOrNull<FontScale>(rawFs) ?: FontScale.M).multiplier

            val overrides = mutableMapOf<Int, Long>()
            for ((rawKey, argb) in rawOverrides.orEmpty()) {
                val idx = rawKey.toIntOrNull() ?: continue   // non-numeric key → drop just this entry
                if (idx < 0) continue                         // negative pool index → drop just this entry
                if (!argb.isValidArgb()) continue             // garbage ARGB → drop just this entry
                overrides[idx] = argb
            }
            return ThemeTuple(seed, dark, mode, shift, maxItems, overrides, fs)
        }

        /** The fail-safe default theme: Dark base, no overrides, M text size (LEGACY; deleted in 15-06). */
        @Deprecated("Retired — use TUPLE_DEFAULT; deleted in 15-06.", level = DeprecationLevel.WARNING)
        @Suppress("DEPRECATION")
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
        @Deprecated("Retired — use sanitizeTuple; deleted in 15-06.", level = DeprecationLevel.WARNING)
        @Suppress("DEPRECATION")
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
