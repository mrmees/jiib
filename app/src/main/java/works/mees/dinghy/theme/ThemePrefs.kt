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
 * Persists the generate-and-cache theme TUPLE (seed, dark, paletteMode, poolShift, the sparse
 * per-pool-slot overrides) + the S/M/L `--fs` choice. The OLD per-role TokenDelta chrome-override
 * persistence was RETIRED in 15-06 (D-04): chrome is fully seed-derived; only the data-pool is editable.
 * maxItems was removed as a user-configurable axis in D-17 (28-04): the 4-slot pool grid is hardcoded at
 * the [works.mees.dinghy.theme.ThemeResolver] → [Palette.generate] boundary (DEFAULT_POOL_MAX_ITEMS=4).
 *
 * DETERMINISTIC FAIL-SAFE CONTRACT (D-02/D-03, threat T-15-05-01): because persistence exists before any
 * validating editor, a corrupt/partial blob MUST NEVER crash or black-screen the printer display.
 * The read path ([sanitizeTuple]) ALWAYS resolves to a complete, usable [ThemeTuple] and never throws:
 *   • unparseable seed (not 6/8-hex)             → default seed
 *   • bad mode (∉ Colorful|Simple|HighContrast)  → Colorful
 *   • out-of-range poolShift                     → defaults
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
                    rawOverrides = readOverrides(prefs),
                    rawAccent = prefs[KEY_ACCENT_OVERRIDE],
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

    /** Persist (or clear, null) the accent override ARGB. */
    suspend fun setAccentOverride(argb: Long?) {
        dataStore.edit {
            if (argb == null) it.remove(KEY_ACCENT_OVERRIDE) else it[KEY_ACCENT_OVERRIDE] = argb and 0xFFFFFFFFL
        }
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
     * Reset the whole theme tuple (seed/dark/mode/shift/overrides) to the validated out-of-box
     * defaults in ONE [dataStore.edit] (WR-03). fsChoice is a SEPARATE setting and is deliberately NOT
     * reset here. Doing all writes in a single edit means [tupleFlow] emits ONCE — the old sequential
     * `set*()` edits each re-emitted, producing partial-reset theme repaints (RESEARCH Pitfall 3).
     */
    suspend fun resetToDefaults() {
        dataStore.edit { prefs ->
            prefs[KEY_SEED] = DEFAULT_SEED
            prefs[KEY_DARK] = true
            prefs[KEY_MODE] = DEFAULT_MODE
            prefs[KEY_SHIFT] = DEFAULT_SHIFT
            writeOverrides(prefs, emptyMap())
            prefs.remove(KEY_ACCENT_OVERRIDE)
        }
    }

    /** Atomically persist a whole tuple to the global idle theme (one edit → one tupleFlow re-emit). */
    suspend fun applyTuple(tuple: ThemeTuple) {
        dataStore.edit { prefs ->
            prefs[KEY_SEED] = tuple.seedHex
            prefs[KEY_DARK] = tuple.dark
            prefs[KEY_MODE] = tuple.paletteMode
            prefs[KEY_SHIFT] = tuple.poolShift
            // recombine the split runtime maps back into the single String→Long wire map
            val wire = tuple.poolOverrides.mapKeys { it.key.toString() } + tuple.statusOverrides
            writeOverrides(prefs, wire)
            if (tuple.accentOverride == null) prefs.remove(KEY_ACCENT_OVERRIDE)
            else prefs[KEY_ACCENT_OVERRIDE] = tuple.accentOverride and 0xFFFFFFFFL
        }
    }

    /**
     * The complete, validated theme TUPLE (D-03/15-05) — the generate-and-cache inputs the
     * [ThemeResolver] applies. `poolOverrides` is Int-keyed/[Color]-valued here (the sanitized runtime
     * form); persistence stores the String→Long wire form. NEVER carries a baked [ThemeTokens].
     * maxItems was removed as a configurable axis in D-17 (28-04); the pool size is hardcoded at 4 in
     * the resolver's [works.mees.dinghy.theme.ThemeResolver.DEFAULT_POOL_MAX_ITEMS] boundary constant.
     */
    data class ThemeTuple(
        val seedHex: String,
        val dark: Boolean,
        val paletteMode: String,
        val poolShift: Int,
        val poolOverrides: Map<Int, Long>,
        // The 3 status-slot overrides (D-03), split out of the SAME String→Long wire map as
        // [poolOverrides] (the Int-keyed map cannot hold "stop"/"caution"/"go"). Keyed by the canonical
        // [StatusSlot.key]; per-entry fail-safe sanitized exactly like the pool overrides.
        val statusOverrides: Map<String, Long> = emptyMap(),
        /** Optional accent override (opaque unsigned-32 ARGB), null = seed-derived accent. */
        val accentOverride: Long? = null,
        val fs: Float,
    )

    companion object {
        // Tuple keys (15-05) — the live persisted theme.
        private val KEY_SEED = stringPreferencesKey("theme_seed")
        private val KEY_DARK = androidx.datastore.preferences.core.booleanPreferencesKey("theme_dark")
        private val KEY_MODE = stringPreferencesKey("theme_mode")
        private val KEY_SHIFT = androidx.datastore.preferences.core.intPreferencesKey("theme_shift")
        private val KEY_ACCENT_OVERRIDE = longPreferencesKey("theme_accent_override")
        // D-17 (28-04): pool size hardcoded at ThemeResolver.DEFAULT_POOL_MAX_ITEMS=4; the old theme_max_items key is orphaned and ignored on read.
        private val KEY_OVERRIDE_KEYS = stringSetPreferencesKey("pool_override_keys")
        private fun overrideArgbKey(idxName: String) = "pool_override_argb_$idxName"

        // The APP-GLOBAL dev-widget enable boolean (D-08, 15.2-01) — NOT profile-keyed, NOT BuildConfig.DEBUG.
        private val KEY_DEV_ENABLE = androidx.datastore.preferences.core.booleanPreferencesKey("dev_cycler_enabled")

        // ---- Tuple defaults + validation (the fail-safe contract, V5/T-15-05-01) ---------------------

        /** The validated default seed + mode + pool (D-02 out-of-box look). */
        const val DEFAULT_SEED: String = "#3f78ff"
        const val DEFAULT_MODE: String = "Colorful"
        const val DEFAULT_SHIFT: Int = 0
        // D-17 (28-04): the old per-axis pool-size constants are gone; pool size is now hardcoded at
        // ThemeResolver.DEFAULT_POOL_MAX_ITEMS=4; no user-configurable axis exists anymore.
        private val VALID_MODES = setOf("Colorful", "Simple", "HighContrast")
        private val HEX_SEED = Regex("^#?[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$")
        // WR-04: 0..359, not 0..360 — a shift of 360 wraps to 0 (`(seedH + shift) % 360`), so 360 is a
        // duplicate of 0. Bound the validation to the 360 meaningfully-distinct hue rotations.
        private val SHIFT_RANGE = 0..359

        /** The fail-safe default TUPLE: default seed, dark, Colorful, no shift, no overrides, M fs. */
        val TUPLE_DEFAULT = ThemeTuple(
            seedHex = DEFAULT_SEED,
            dark = true,
            paletteMode = DEFAULT_MODE,
            poolShift = DEFAULT_SHIFT,
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
         *   • out-of-range poolShift (0..359)          → default shift
         *   • poolOverrides: parse each String key → Int index, validate the ARGB; DROP only the bad
         *     entry, KEEP the good ones (kotlinx ignoreUnknownKeys does NOT cover malformed VALUES, so a
         *     String→Long map + this PER-ENTRY parse is what keeps one bad slot from nuking the whole map).
         *   • statusOverrides (D-03): the SAME wire map also carries the reserved status keys
         *     "stop"/"caution"/"go"; route a key matching [StatusSlot.fromKey] into the String-keyed
         *     statusOverrides (validating the ARGB per-entry). A key that is NEITHER an integer-string NOR
         *     a valid status key is DROPPED — it never reaches `key.toInt()` (malformed-key safety).
         * maxItems was a configurable axis prior to D-17 (28-04); it is now hardcoded in ThemeResolver.
         */
        fun sanitizeTuple(
            rawSeed: String?,
            rawDark: Boolean?,
            rawMode: String?,
            rawShift: Int?,
            rawOverrides: Map<String, Long>?,
            rawAccent: Long? = null,
        ): ThemeTuple {
            val seed = if (rawSeed != null && HEX_SEED.matches(rawSeed)) rawSeed else DEFAULT_SEED
            val dark = rawDark ?: true
            val mode = if (rawMode in VALID_MODES) rawMode!! else DEFAULT_MODE
            val shift = if (rawShift != null && rawShift in SHIFT_RANGE) rawShift else DEFAULT_SHIFT
            val fs = FontScale.M.multiplier   // app-global font scale overrides this downstream (activeThemeTuple)

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
            val accent = if (rawAccent != null && rawAccent.isValidArgb()) rawAccent else null
            return ThemeTuple(
                seedHex = seed,
                dark = dark,
                paletteMode = mode,
                poolShift = shift,
                poolOverrides = overrides,
                statusOverrides = statusOverrides,
                accentOverride = accent,
                fs = fs,
            )
        }

        /**
         * A valid persisted ARGB occupies the low 32 bits (it was written from an Int via `.toLong()`
         * or as an unsigned 0..0xFFFFFFFF). Anything outside [0, 0xFFFFFFFF] is garbage from a corrupt
         * blob — drop that single override (the role then inherits the base token, D-02).
         */
        private fun Long.isValidArgb(): Boolean = this in 0L..0xFFFFFFFFL
    }
}
