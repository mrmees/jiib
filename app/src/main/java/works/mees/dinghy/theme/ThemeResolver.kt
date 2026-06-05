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
 * The single source of truth for the active theme (D-05) — a GENERATE-AND-CACHE resolver (D-02):
 * it holds the seed/mode/pool inputs and, on each discrete change, calls [Palette.generate] →
 * [TokenBridge.build] ONCE, caching the complete [ThemeTokens] on the UNCHANGED `StateFlow<ThemeTokens>`
 * boundary. That boundary is the whole reason the substrate is "extended not rewritten" — Compose
 * `DinghyTheme` and the Views `ThemeableView` consume the same flow untouched, and the render loop only
 * ever reads CACHED Color ints (the device never does color math — the Adreno-320 fill-rate floor).
 *
 * Headless and synchronous (no conflation — a theme change is rare, unlike the ~4 Hz printer state).
 *
 * The OLD per-role `TokenDelta` chrome-override API was RETIRED in 15-06 (D-04): chrome is fully
 * seed-derived now, and the only editable surface is the data-pool ([setOverride]).
 */
class ThemeResolver(
    private var seedHex: String = DEFAULT_SEED_HEX,
    private var dark: Boolean = true,
    private var paletteMode: String = MODE_COLORFUL,
    private var poolShift: Int = 0,
    private var maxItems: Int = DEFAULT_POOL_MAX_ITEMS,
    private var poolOverrides: Map<Int, Color> = emptyMap(),
    private var fs: Float = FontScale.M.multiplier,
) {

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
        recompute()
    }

    /** Set the text-size multiplier (S/M/L → 1.0/1.15/1.32); recomputes + re-emits. fs is unchanged this phase. */
    fun setFs(next: Float) {
        fs = next
        recompute()
    }

    // ---------- generate-and-cache core ----------

    private fun recompute() {
        _tokens.value = compute()
    }

    /**
     * Produce the cached [ThemeTokens]: generate (Palette.generate → TokenBridge.build). Generation is
     * wrapped in a try/catch: on ANY throw it falls back to the [BakedTokens] default-seed snapshot — the
     * math is total so this should never fire, but the printer surface must NEVER go dark (T-15-04-01).
     */
    private fun compute(): ThemeTokens {
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
