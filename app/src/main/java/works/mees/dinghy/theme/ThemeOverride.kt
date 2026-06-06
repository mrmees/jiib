package works.mees.dinghy.theme

/**
 * The transient theme override (15.2-01, D-06/D-08) — a SPARSE, nullable substitution of ONLY the
 * dev-cycled axes (dark / paletteMode / fs). A null field falls through to the base [ThemePrefs.ThemeTuple]
 * so the user's real seed/pool/status/shift are NEVER touched; only the cycled axis is overridden.
 *
 * The override lives entirely IN MEMORY (a [kotlinx.coroutines.flow.MutableStateFlow] on the container) —
 * setting or clearing it writes NOTHING to DataStore (D-08), so it is the deliberate non-persisting
 * exception to [[dinghy-compose-write-scope-cancellation]]: there is no write to lose.
 */
data class ThemeOverride(
    val dark: Boolean? = null,
    val paletteMode: String? = null,
    val fs: Float? = null,
)

/**
 * Merge this sparse override ONTO a base tuple, substituting ONLY the cycled axes (HIGH-3). Uses
 * `base.copy(...)` so EVERY other [ThemePrefs.ThemeTuple] field — seedHex, poolShift, maxItems,
 * poolOverrides, statusOverrides — passes through VERBATIM. Do NOT reconstruct the tuple field-by-field:
 * `copy` guarantees a future-added field is never silently dropped.
 */
fun ThemeOverride.mergeOnto(base: ThemePrefs.ThemeTuple): ThemePrefs.ThemeTuple =
    base.copy(
        dark = this.dark ?: base.dark,
        paletteMode = this.paletteMode ?: base.paletteMode,
        fs = this.fs ?: base.fs,
    )
