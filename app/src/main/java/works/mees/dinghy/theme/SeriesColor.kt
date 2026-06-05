package works.mees.dinghy.theme

import androidx.compose.ui.graphics.Color

/**
 * The D-05 N-series data-color helper: the color of the `i`-th item in any "Nth distinct data color"
 * series (graph traces, multi-bar readouts, any list that needs visually-distinct per-item colors).
 * A toolkit-agnostic, host-testable top-level extension that reads ONLY [ThemeTokens] fields (the
 * resolved [ThemeTokens.mode] enum + accent/pool/status tokens) — no Android view imports, so the
 * data-series rule is one pure function both Compose and classic-Views consumers share.
 *
 * The sequence is ALWAYS accent-led — `seriesColor(0) == accent` in EVERY mode — so the lead series
 * keeps its identity across a palette-mode switch (D-05). Past index 0 the rule depends on
 * [ThemeTokens.mode]:
 *  - [PaletteMode.Colorful]:     `0`=accent, then `pool[(i-1) % pool.size]` — accent + the full data
 *    pool, wrapping INFINITELY (D-09). NO status colors (`stop`/`go`/`caution`) ever appear in data.
 *  - [PaletteMode.Simple]:       alternates accent (even `i`) / text (odd `i`) — near-monochrome data.
 *  - [PaletteMode.HighContrast]: cycles `[accent, stop, caution(=heat), go, text][i % 5]` — stoplight.
 *
 * D-09 (infinite wrap, NO user-facing cap): the series wraps forever via the modulo; the generator's
 * `maxItems` is an INTERNAL pool-size boundary only and does NOT cap how many series colors a consumer
 * may request. An index well past the pool length is valid and wraps.
 *
 * Empty-pool guard: a manually-built [ThemeTokens] could carry an empty [ThemeTokens.pool]; rather
 * than divide-by-zero, Colorful falls back to [ThemeTokens.accent] for every index (mirrors the
 * `GraphView` CR-02 empty-pool guard — fall back to accent, never crash the render surface). Simple
 * and HighContrast never read the pool, so they are unaffected.
 *
 * Negative-index contract (the public contract — Item 8): a negative `i` is a CALLER BUG, not a wrap
 * case (`seriesColor` is an index INTO a series; there is no "−1th data color"). It is REJECTED with
 * an [IllegalArgumentException] via [require] rather than silently returning a wrong color. No current
 * consumer passes a negative index; if one ever legitimately needs to, normalize at the call site.
 *
 * @param i the zero-based series index; MUST be `>= 0` (a negative index throws).
 * @throws IllegalArgumentException if `i < 0`.
 */
fun ThemeTokens.seriesColor(i: Int): Color {
    require(i >= 0) { "seriesColor index must be >= 0 (a negative series index is a caller bug), was $i" }
    // Line 1 (i == 0) is accent in ALL three modes — the lead series keeps identity across a mode switch.
    if (i == 0) return accent
    return when (mode) {
        PaletteMode.Colorful -> {
            // accent at 0; pool[(i-1) % size] thereafter, wrapping infinitely (D-09).
            // Empty-pool guard (CR-02): fall back to accent rather than `% 0`.
            if (pool.isEmpty()) accent else pool[(i - 1) % pool.size]
        }
        // Even i = accent (handled at i==0 and here), odd i = text.
        PaletteMode.Simple -> if (i % 2 == 0) accent else text
        // Cycle the 5-element stoplight sequence; i==0 already returned accent above.
        PaletteMode.HighContrast -> when (i % 5) {
            0 -> accent
            1 -> stop
            2 -> heat // caution (D-13: heat IS the caution color)
            3 -> go
            else -> text
        }
    }
}
