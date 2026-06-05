package works.mees.dinghy.theme

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The validated out-of-box default seed (D-02). The resolver generates this palette at first launch
 * so the printer surface is themed before any persisted tuple is applied. The sanitize layer (15-05)
 * is the last line of defence against junk seeds; until then this constant IS the live default.
 */
const val DEFAULT_SEED_HEX: String = "#3f78ff"

/** Default size of the contrast-ranked data pool the generator emits (D-13/D-14). */
const val DEFAULT_POOL_MAX_ITEMS: Int = 4

/**
 * @deprecated (D-04 two-step retirement) — the user-custom theme as a sparse OVERRIDE set on top of a
 * base. This per-role chrome-override model is RETIRED: chrome is now seed-derived (the seed owns
 * accent/heat/go/stop/bg), and the only editable surface is the data-pool (see [ThemeResolver.setOverride]).
 * Kept ONLY as a thin bridge shim so [resolve], [ThemeResolver]'s deprecated ctor/mutators, GalleryScreen,
 * and the legacy theme/prompt tests keep compiling at this wave boundary. DELETED in 15-06.
 */
@Deprecated(
    "Retired — seed-only chrome (D-04). The data-pool is the only editable surface now; " +
        "deleted in 15-06.",
    level = DeprecationLevel.WARNING,
)
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
 * @deprecated (D-04 two-step retirement) — resolve a complete [ThemeTokens] from (base, deltas, fs)
 * via the BAKED token tables. Superseded by the generate-and-cache path inside [ThemeResolver]
 * ([ThemeResolver.recompute] → [Palette.generate] → [TokenBridge.build]). Kept as a `@Deprecated` shim
 * so the legacy theme/prompt tests (FontScaleTest / PromptStyleColorsTest / ThemePrefsFallbackTest /
 * ThemeResolverTest) keep compiling AND keep asserting on a complete baked token set. DELETED in 15-06.
 *
 * Behaviour is UNCHANGED from the pre-15-04 implementation: pick the baked base table, apply each
 * in-scope override, then stamp fs (so `resolve(base, EMPTY, fs)` === the base table with fs set).
 */
@Deprecated(
    "Retired — the live path is ThemeResolver's generate-and-cache (Palette.generate + " +
        "TokenBridge.build). This baked-table resolve is a compile shim; deleted in 15-06.",
    level = DeprecationLevel.WARNING,
)
@Suppress("DEPRECATION")
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
 * The single source of truth for the active theme (D-05), now a GENERATE-AND-CACHE resolver (D-02):
 * it holds the seed/mode/pool inputs and, on each discrete change, calls [Palette.generate] →
 * [TokenBridge.build] ONCE, caching the complete [ThemeTokens] on the UNCHANGED `StateFlow<ThemeTokens>`
 * boundary. That boundary is the whole reason the substrate is "extended not rewritten" — Compose
 * `DinghyTheme` and the Views `ThemeableView` consume the same flow untouched, and the render loop only
 * ever reads CACHED Color ints (the device never does color math — the Adreno-320 fill-rate floor).
 *
 * Headless and synchronous (no conflation — a theme change is rare, unlike the ~4 Hz printer state).
 *
 * The OLD per-role [TokenDelta] API ([setBase]/[setDeltas]/[setFs], the `base`/`deltas`/`fs` ctor, and
 * the 3-arg [apply]) survives ONLY as `@Deprecated` bridge shims so GalleryScreen + the legacy
 * theme/prompt tests keep compiling at this wave boundary (D-04 two-step retirement — deleted in 15-06).
 */
class ThemeResolver private constructor(
    private var seedHex: String,
    private var dark: Boolean,
    private var paletteMode: String,
    private var poolShift: Int,
    private var maxItems: Int,
    private var poolOverrides: Map<Int, Color>,
    private var fs: Float,
    private var baked: Boolean,
) {

    /**
     * The live generate-and-cache resolver (D-02). Constructs with the validated default seed
     * (`#3f78ff`), dark, Colorful (D-15 default), poolShift 0, maxItems 4, no overrides, and the
     * default fs — and generates the out-of-box palette at first launch.
     */
    constructor() : this(
        seedHex = DEFAULT_SEED_HEX,
        dark = true,
        paletteMode = MODE_COLORFUL,
        poolShift = 0,
        maxItems = DEFAULT_POOL_MAX_ITEMS,
        poolOverrides = emptyMap(),
        fs = FontScale.M.multiplier,
        baked = false,
    )

    /**
     * @deprecated (D-04) — the OLD (base, deltas, fs) ctor, kept as a `@Deprecated` bridge shim that
     * seeds + operates via the baked [resolve] path so the legacy ThemeResolverTest still passes.
     * DELETED in 15-06. New code uses the no-arg generate-and-cache ctor.
     */
    @Deprecated(
        "Retired — the live resolver is the no-arg generate-and-cache ThemeResolver(); deleted in 15-06.",
        level = DeprecationLevel.WARNING,
    )
    @Suppress("DEPRECATION")
    constructor(
        base: ThemeBase = ThemeBase.Dark,
        deltas: TokenDelta = TokenDelta.EMPTY,
        fs: Float = FontScale.M.multiplier,
    ) : this(
        seedHex = DEFAULT_SEED_HEX,
        dark = base == ThemeBase.Dark,
        paletteMode = MODE_COLORFUL,
        poolShift = 0,
        maxItems = DEFAULT_POOL_MAX_ITEMS,
        poolOverrides = emptyMap(),
        fs = fs,
        baked = true,
    ) {
        // The secondary ctor body runs AFTER the property initializers (incl. the initial _tokens
        // compute()), so re-seed the baked inputs and re-emit the correct seeded value here.
        this.bakedBase = base
        this.bakedDeltas = deltas
        recompute()
    }

    // Held state for the deprecated baked path (set ONLY by the deprecated ctor / setBase / setDeltas).
    @Suppress("DEPRECATION")
    private var bakedBase: ThemeBase = ThemeBase.Dark

    @Suppress("DEPRECATION")
    private var bakedDeltas: TokenDelta = TokenDelta.EMPTY

    private val _tokens = MutableStateFlow(compute())

    /** The single resolved-theme source of truth (D-05) — the UNCHANGED boundary both toolkits consume. */
    val tokens: StateFlow<ThemeTokens> = _tokens.asStateFlow()

    // ---------- the NEW live API: seed / mode / pool ----------

    /** Replace the seed hex; recomputes + re-emits once. */
    fun setSeed(next: String) {
        seedHex = next
        recompute()
    }

    /** Replace the palette mode ([MODE_COLORFUL]/[MODE_SIMPLE]/[MODE_HIGH_CONTRAST]); recomputes + re-emits. */
    fun setMode(next: String) {
        paletteMode = next
        recompute()
    }

    /** Replace the pool-shift (hue rotation of the data pool); recomputes + re-emits. */
    fun setShift(next: Int) {
        poolShift = next
        recompute()
    }

    /**
     * Edit ONE data-pool override slot (D-09 — the data-pool is the only editable surface now). A
     * non-null [argb] sets `pool[i]`; a `null` clears the slot back to the seed-derived color. Recomputes.
     */
    fun setOverride(i: Int, argb: Color?) {
        poolOverrides = if (argb == null) {
            poolOverrides - i
        } else {
            poolOverrides + (i to argb)
        }
        recompute()
    }

    /** Swap dark/light polarity (chrome stays seed-derived); recomputes + re-emits. */
    fun setDark(next: Boolean) {
        dark = next
        baked = false
        recompute()
    }

    /**
     * Apply the FULL tuple at once (e.g. when the persisted theme loads, 15-05). Sets every field then
     * recomputes ONCE — Pitfall 3: never a sequence of set*() calls (each would re-emit = multiple
     * theme-switch flickers). Exactly ONE new [ThemeTokens] is emitted.
     */
    fun apply(
        seedHex: String,
        dark: Boolean,
        paletteMode: String,
        poolShift: Int,
        maxItems: Int,
        overrides: Map<Int, Color>,
        fs: Float,
    ) {
        this.seedHex = seedHex
        this.dark = dark
        this.paletteMode = paletteMode
        this.poolShift = poolShift
        this.maxItems = maxItems
        this.poolOverrides = overrides
        this.fs = fs
        this.baked = false
        recompute()
    }

    // ---------- @Deprecated bridge shims (D-04; deleted in 15-06) ----------

    /**
     * @deprecated (D-04) — bridge shim. Maps the old Dark/Light base onto the seed-driven [setDark]
     * (chrome is seed-derived now). DELETED in 15-06.
     */
    @Deprecated(
        "Retired — chrome is seed-derived; use setDark / setSeed. Deleted in 15-06.",
        level = DeprecationLevel.WARNING,
    )
    fun setBase(next: ThemeBase) {
        bakedBase = next
        dark = next == ThemeBase.Dark
        recompute()
    }

    /**
     * @deprecated (D-04) — bridge shim. Under the baked path it still applies the deltas (so the legacy
     * tests keep asserting); under the live generate path it is a NO-OP (per-role chrome override is
     * retired — the seed owns chrome; the gallery's custom toggle visibly does nothing here). 15-06
     * re-points the gallery onto the data-pool override API. DELETED in 15-06.
     */
    @Deprecated(
        "Retired — per-role chrome override is gone (seed owns chrome). Deleted in 15-06.",
        level = DeprecationLevel.WARNING,
    )
    @Suppress("DEPRECATION")
    fun setDeltas(next: TokenDelta) {
        if (baked) {
            bakedDeltas = next
            recompute()
        }
        // live path: no-op (seed-only chrome, D-04).
    }

    /** Set the text-size multiplier (S/M/L → 1.0/1.15/1.32); recomputes + re-emits. fs is unchanged this phase. */
    fun setFs(next: Float) {
        fs = next
        recompute()
    }

    /**
     * @deprecated (D-04) — the OLD 3-arg apply, kept as a `@Deprecated` overload delegating to the baked
     * path so any legacy caller/test still compiles. The in-module caller (AppContainer) uses the new
     * tuple [apply]. DELETED in 15-06.
     */
    @Deprecated(
        "Retired — use the tuple apply(seedHex, dark, paletteMode, poolShift, maxItems, overrides, fs). " +
            "Deleted in 15-06.",
        level = DeprecationLevel.WARNING,
    )
    @Suppress("DEPRECATION")
    fun apply(base: ThemeBase, deltas: TokenDelta, fs: Float) {
        this.baked = true
        this.bakedBase = base
        this.bakedDeltas = deltas
        this.dark = base == ThemeBase.Dark
        this.fs = fs
        recompute()
    }

    // ---------- generate-and-cache core ----------

    private fun recompute() {
        _tokens.value = compute()
    }

    /**
     * Produce the cached [ThemeTokens]. The LIVE path generates (Palette.generate → TokenBridge.build);
     * the deprecated baked path delegates to [resolve]. Generation is wrapped in a try/catch: on ANY
     * throw it falls back to the [BakedTokens] default-seed snapshot — the math is total so this should
     * never fire, but the printer surface must NEVER go dark (T-15-04-01).
     */
    @Suppress("DEPRECATION")
    private fun compute(): ThemeTokens {
        if (baked) {
            return resolve(bakedBase, bakedDeltas, fs)
        }
        return try {
            val simple = paletteMode == MODE_SIMPLE
            val highContrast = paletteMode == MODE_HIGH_CONTRAST
            val generated = Palette.generate(
                seedHex = seedHex,
                dark = dark,
                maxItems = maxItems,
                poolShift = poolShift,
                statusFromPool = true,
                simple = simple,
                highContrast = highContrast,
            )
            TokenBridge.build(generated, poolOverrides, fs)
        } catch (_: Throwable) {
            // Fail-safe: a complete, usable default theme — the load-bearing Phase-3 contract.
            (if (dark) TokensDark else TokensLight).copy(fs = fs)
        }
    }

    companion object {
        /** Palette modes (D-15: Colorful is the default). The resolver maps these to simple/highContrast flags. */
        const val MODE_COLORFUL: String = "Colorful"
        const val MODE_SIMPLE: String = "Simple"
        const val MODE_HIGH_CONTRAST: String = "HighContrast"
    }
}
