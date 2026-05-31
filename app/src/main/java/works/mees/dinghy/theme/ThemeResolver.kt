package works.mees.dinghy.theme

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user-custom theme as a sparse OVERRIDE set on top of a base (D-01/D-02). Stores ONLY the
 * overridden role keys (a half-configured custom theme is still fully usable, D-02), so an empty
 * [TokenDelta] resolves identically to the chosen base (the "empty delta === base" invariant,
 * Pitfall 7). Custom scope is limited to the signature/status roles (D-01): accent, heat, go, stop,
 * and bg — every other role always inherits the base.
 *
 * Each override is stored as a packed ARGB [Long] (the same shape ThemePrefs persists), so this
 * model is plain Kotlin and host-pure (no Compose Color in the persisted shape — [overrides] maps a
 * [Role] to an ARGB int-in-a-Long). [Role] enumerates exactly the overridable roles so an invalid
 * key cannot be represented.
 */
data class TokenDelta(
    val overrides: Map<Role, Long> = emptyMap(),
) {
    init {
        // NORMALIZE to unsigned 32-bit ARGB. Color.toArgb() returns a signed Int; an opaque
        // 0xFF.. value sign-extends to a NEGATIVE Long via .toLong(). The persisted/compared
        // shape is the unsigned 32-bit form (0..0xFFFFFFFF) so producer paths (this ctor,
        // ThemePrefs.setDeltas) and the read-path sanitizer agree bit-for-bit.
        require(overrides.values.all { it == (it and 0xFFFFFFFFL) }) {
            "TokenDelta ARGB values must be normalized unsigned 32-bit (0..0xFFFFFFFF); " +
                "mask Color.toArgb().toLong() with 0xFFFFFFFFL before constructing."
        }
    }

    /** The user-overridable role set (D-01). Deliberately NOT every token. */
    enum class Role { Accent, Heat, Go, Stop, Bg }

    val isEmpty: Boolean get() = overrides.isEmpty()

    companion object {
        val EMPTY = TokenDelta()

        /** Build a delta from Compose ARGB ints, masking each to the unsigned 32-bit persisted form. */
        fun of(vararg pairs: Pair<Role, Int>): TokenDelta =
            TokenDelta(pairs.associate { (role, argb) -> role to (argb.toLong() and 0xFFFFFFFFL) })
    }
}

/**
 * Resolve a complete [ThemeTokens] from (base, deltas, fs). Pure function (the single place a theme
 * is assembled): pick the baked base table, apply each in-scope override, then stamp the fs. Because
 * an empty [deltas] applies nothing, `resolve(base, EMPTY, fs)` === the base table with fs set —
 * the Pitfall-7 invariant, exercised directly by ThemeResolverTest.
 */
fun resolve(base: ThemeBase, deltas: TokenDelta, fs: Float): ThemeTokens {
    val baseTokens = when (base) {
        ThemeBase.Dark -> TokensDark
        ThemeBase.Light -> TokensLight
    }
    var t = baseTokens.copy(fs = fs)
    for ((role, argb) in deltas.overrides) {
        val color = Color(argb.toInt())
        t = when (role) {
            TokenDelta.Role.Accent -> t.copy(accent = color)
            TokenDelta.Role.Heat -> t.copy(heat = color)
            TokenDelta.Role.Go -> t.copy(go = color)
            TokenDelta.Role.Stop -> t.copy(stop = color)
            TokenDelta.Role.Bg -> t.copy(bg = color)
        }
    }
    return t
}

/**
 * The single source of truth for the active theme (D-05) — mirrors [works.mees.dinghy.state.
 * PrinterStateStore]'s StateFlow idiom: a [MutableStateFlow] seeded from [resolve], exposed read-only
 * via [asStateFlow], with `setBase/setDeltas/setFs` mutators that each recompute and re-emit. The
 * resolver holds the three inputs and is the seam both toolkits consume: Compose collects [tokens]
 * at the theme boundary; the Views graph collects the same flow and repaints via push-tokens (D-06).
 *
 * Headless and synchronous (no conflation — a theme change is rare, unlike the ~4 Hz printer state).
 */
class ThemeResolver(
    base: ThemeBase = ThemeBase.Dark,
    deltas: TokenDelta = TokenDelta.EMPTY,
    fs: Float = FontScale.M.multiplier,
) {
    private var base: ThemeBase = base
    private var deltas: TokenDelta = deltas
    private var fs: Float = fs

    private val _tokens = MutableStateFlow(resolve(base, deltas, fs))

    /** The single resolved-theme source of truth (D-05). */
    val tokens: StateFlow<ThemeTokens> = _tokens.asStateFlow()

    /** Swap the base (Dark/Light), keeping the current deltas + fs; recomputes + re-emits. */
    fun setBase(next: ThemeBase) {
        base = next
        recompute()
    }

    /** Replace the custom overrides; recomputes + re-emits. */
    fun setDeltas(next: TokenDelta) {
        deltas = next
        recompute()
    }

    /** Set the text-size multiplier (S/M/L → 1.0/1.15/1.32); recomputes + re-emits. */
    fun setFs(next: Float) {
        fs = next
        recompute()
    }

    /** Apply a full (base, deltas, fs) triple at once — e.g. when ThemePrefs loads (one re-emit). */
    fun apply(base: ThemeBase, deltas: TokenDelta, fs: Float) {
        this.base = base
        this.deltas = deltas
        this.fs = fs
        recompute()
    }

    private fun recompute() {
        _tokens.value = resolve(base, deltas, fs)
    }
}
