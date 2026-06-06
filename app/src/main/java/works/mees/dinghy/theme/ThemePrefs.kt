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
 * DataStore(Preferences) persistence of the theme (THEME-02/D-02) — the FIRST DataStore in the repo.
 * Persists the generate-and-cache theme TUPLE (seed, dark, paletteMode, poolShift, maxItems, the sparse
 * per-pool-slot overrides) + the S/M/L `--fs` choice. The OLD per-role TokenDelta chrome-override
 * persistence was RETIRED in 15-06 (D-04): chrome is fully seed-derived; only the data-pool is editable.
 *
 * DETERMINISTIC FAIL-SAFE CONTRACT (D-02/D-03, threat T-15-05-01): because persistence exists before any
 * validating editor, a corrupt/partial blob MUST NEVER crash or black-screen the printer display.
 * The read path ([sanitizeTuple]) ALWAYS resolves to a complete, usable [ThemeTuple] and never throws:
 *   • unparseable seed (not 6/8-hex)             → default seed
 *   • bad mode (∉ Colorful|Simple|HighContrast)  → Colorful
 *   • out-of-range poolShift / maxItems          → defaults
 *   • a malformed pool override                  → drop ONLY that slot, keep the good ones (per-entry)
 *
 * The sanitization is a PURE function over the stored primitives ([sanitizeTuple]) so it is unit-testable
 * host-side with no DataStore I/O (ThemePrefsFallbackTest stays host-pure). [tupleFlow] reads DataStore,
 * maps each [Preferences] snapshot through [sanitizeTuple], and (per DataStore guidance) recovers from a
 * read [IOException] by emitting empty prefs — which [sanitizeTuple] turns into the full default tuple.
 */
class ThemePrefs(
    private val dataStore: DataStore<Preferences>,
) {
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
            writeOverrides(prefs, overrides)
        }
    }

    /**
     * Atomically read-modify-write the sparse pool overrides inside ONE [dataStore.edit] (WR-02). The
     * read (current overrides), the [transform], and the write all happen under the single edit, so two
     * fast per-slot edits cannot each re-encode a stale snapshot and drop one change — the same
     * lost-update fix `ProfileStore.mutateActive` applies to the active-profile path
     * ([[dinghy-compose-write-scope-cancellation]] / Phase-14 mutateActive lesson).
     */
    suspend fun mutateOverrides(transform: (Map<String, Long>) -> Map<String, Long>) {
        dataStore.edit { prefs ->
            val current = readOverrides(prefs)
            writeOverrides(prefs, transform(current))
        }
    }

    suspend fun setFs(choice: FontScale) {
        dataStore.edit { it[KEY_FS] = choice.name }
    }

    /**
     * The APP-GLOBAL dev-widget enable boolean (D-08, 15.2-01 Task 4) — NOT keyed by profile.id, NOT
     * gated on `BuildConfig.DEBUG`. The owner sideloads RELEASE APKs to flox, so the dev theme cyclers
     * must be reachable in a release build via this RUNTIME flag, never the build type. Defaults FALSE so
     * a normal user never sees the dev widgets.
     *
     * The durable enable path is the **About** screen's "Developer" dev-enable toggle (D-05/D-08, wired in
     * 15.2-04): turning it ON lights the cyclers, turning it OFF hides them AND clears any active override
     * ([AppContainer.setDevCyclerEnabled], HIGH-5). The default is FALSE so the cyclers never ship
     * on-by-default in release. (15.2-02 temporarily flipped this true for its pre-About on-device walk;
     * that TEMP flip was REVERTED here in 15.2-04 Task 1 once About became the durable control, MEDIUM-3.)
     */
    val devEnableFlow: Flow<Boolean> =
        dataStore.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }
            .map { it[KEY_DEV_ENABLE] ?: false }

    /** Persist the app-global dev-widget enable boolean. */
    suspend fun setDevEnable(on: Boolean) {
        dataStore.edit { it[KEY_DEV_ENABLE] = on }
    }

    /**
     * Reset the whole theme tuple (seed/dark/mode/shift/maxItems/overrides) to the validated out-of-box
     * defaults in ONE [dataStore.edit] (WR-03). fsChoice is a SEPARATE setting and is deliberately NOT
     * reset here. Doing all writes in a single edit means [tupleFlow] emits ONCE — the old six sequential
     * `set*()` edits each re-emitted, producing up to six partial-reset theme repaints (RESEARCH Pitfall 3).
     */
    suspend fun resetToDefaults() {
        dataStore.edit { prefs ->
            prefs[KEY_SEED] = DEFAULT_SEED
            prefs[KEY_DARK] = true
            prefs[KEY_MODE] = DEFAULT_MODE
            prefs[KEY_SHIFT] = DEFAULT_SHIFT
            prefs[KEY_MAX_ITEMS] = DEFAULT_MAX_ITEMS
            writeOverrides(prefs, emptyMap())
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
        // The 3 status-slot overrides (D-03), split out of the SAME String→Long wire map as
        // [poolOverrides] (the Int-keyed map cannot hold "stop"/"caution"/"go"). Keyed by the canonical
        // [StatusSlot.key]; per-entry fail-safe sanitized exactly like the pool overrides.
        val statusOverrides: Map<String, Long> = emptyMap(),
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

        // The S/M/L text-size key (a SEPARATE setting, D-05) — read by tupleFlow + written by setFs.
        private val KEY_FS = stringPreferencesKey("fs_choice")

        // The APP-GLOBAL dev-widget enable boolean (D-08, 15.2-01) — NOT profile-keyed, NOT BuildConfig.DEBUG.
        private val KEY_DEV_ENABLE = androidx.datastore.preferences.core.booleanPreferencesKey("dev_cycler_enabled")

        // ---- Tuple defaults + validation (the fail-safe contract, V5/T-15-05-01) ---------------------

        /** The validated default seed + mode + pool (D-02 out-of-box look). */
        const val DEFAULT_SEED: String = "#3f78ff"
        const val DEFAULT_MODE: String = "Colorful"
        const val DEFAULT_SHIFT: Int = 0
        const val DEFAULT_MAX_ITEMS: Int = 4
        private val VALID_MODES = setOf("Colorful", "Simple", "HighContrast")
        private val HEX_SEED = Regex("^#?[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$")
        // WR-04: 0..359, not 0..360 — a shift of 360 wraps to 0 (`(seedH + shift) % 360`), so 360 is a
        // duplicate of 0. Bound the validation to the 360 meaningfully-distinct hue rotations.
        private val SHIFT_RANGE = 0..359
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

        /** Write the sparse String→Long pool overrides into a mutable Preferences (replaces the whole set). */
        private fun writeOverrides(prefs: androidx.datastore.preferences.core.MutablePreferences, overrides: Map<String, Long>) {
            prefs[KEY_OVERRIDE_KEYS]?.forEach { k ->
                prefs.remove(longPreferencesKey(overrideArgbKey(k)))
            }
            prefs[KEY_OVERRIDE_KEYS] = overrides.keys.toSet()
            for ((k, argb) in overrides) {
                prefs[longPreferencesKey(overrideArgbKey(k))] = argb and 0xFFFFFFFFL
            }
        }

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
         *   • statusOverrides (D-03): the SAME wire map also carries the reserved status keys
         *     "stop"/"caution"/"go"; route a key matching [StatusSlot.fromKey] into the String-keyed
         *     statusOverrides (validating the ARGB per-entry). A key that is NEITHER an integer-string NOR
         *     a valid status key is DROPPED — it never reaches `key.toInt()` (malformed-key safety).
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
            val statusOverrides = mutableMapOf<String, Long>()
            for ((rawKey, argb) in rawOverrides.orEmpty()) {
                if (!argb.isValidArgb()) continue             // garbage ARGB → drop just this entry
                val slot = StatusSlot.fromKey(rawKey)
                if (slot != null) {                           // reserved status key (D-03)
                    statusOverrides[slot.key] = argb
                    continue
                }
                val idx = rawKey.toIntOrNull() ?: continue    // non-numeric, non-status key → drop (never key.toInt() crash)
                if (idx < 0) continue                          // negative pool index → drop just this entry
                overrides[idx] = argb
            }
            return ThemeTuple(
                seedHex = seed,
                dark = dark,
                paletteMode = mode,
                poolShift = shift,
                maxItems = maxItems,
                poolOverrides = overrides,
                statusOverrides = statusOverrides,
                fs = fs,
            )
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
