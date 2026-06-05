package works.mees.dinghy.theme

import org.junit.Test

/**
 * RED scaffold for the D-05 N-series rule (`ThemeTokens.seriesColor(i)` — to be implemented in a
 * later wave). The distinguishable sequence is ALWAYS accent-led and wraps infinitely:
 *  - Colorful:      accent, pool[0], pool[1], … (wrap over pool)
 *  - Simple:        accent, text, accent, text, … (alternate)
 *  - HighContrast:  accent, stop, caution(=heat), go, text, … (wrap over the 5-element sequence)
 *
 * CRITICAL ([[dinghy-wave0-red-scaffold-compile]]): these bodies MUST NOT reference any symbol that
 * does not yet exist (`seriesColor`, `PaletteMode`, etc.). Gradle compiles the WHOLE test source
 * set before the `--tests` filter, so a single unresolved reference bricks every per-wave run. The
 * future behavior is named in PROSE comments only, with a `fail(...)` body.
 */
class SeriesColorTest {

    @Test
    fun colorfulMode_accentLeadsThenPoolWraps() {
        // TODO(15.1-0N): assert ThemeTokens.seriesColor(0) == accent and seriesColor(i>=1) walks
        // pool[(i-1) % pool.size], wrapping past the last pool slot back to pool[0].
        org.junit.Assert.fail("not yet implemented — wave N (seriesColor Colorful)")
    }

    @Test
    fun simpleMode_alternatesAccentAndText() {
        // TODO(15.1-0N): assert seriesColor alternates accent (even i) / text (odd i) in Simple mode.
        org.junit.Assert.fail("not yet implemented — wave N (seriesColor Simple)")
    }

    @Test
    fun highContrastMode_wrapsAccentStopCautionGoText() {
        // TODO(15.1-0N): assert seriesColor wraps the 5-element [accent, stop, caution, go, text]
        // sequence in HighContrast mode (i % 5 indexing).
        org.junit.Assert.fail("not yet implemented — wave N (seriesColor HighContrast)")
    }

    @Test
    fun emptyPool_guardsAgainstDivideByZero() {
        // TODO(15.1-0N): assert seriesColor does not crash when pool is empty (Colorful must fall
        // back gracefully — CR-02 empty-pool guard), returning accent rather than throwing.
        org.junit.Assert.fail("not yet implemented — wave N (seriesColor empty-pool guard)")
    }
}
