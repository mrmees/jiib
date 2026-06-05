package works.mees.dinghy.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GREEN test for the D-05 N-series rule ([ThemeTokens.seriesColor]) — was the plan 15-01 Wave-0 RED
 * scaffold. The distinguishable sequence is ALWAYS accent-led and wraps infinitely (D-09):
 *  - Colorful:      accent, pool[0], pool[1], … (wrap over pool)
 *  - Simple:        accent, text, accent, text, … (alternate)
 *  - HighContrast:  accent, stop, caution(=heat), go, text, … (wrap over the 5-element sequence)
 *
 * Pins: accent leads in ALL modes, the per-mode sequence, the D-09 infinite wrap past pool length,
 * the empty-pool guard (fall back to accent, no `% 0`), the negative-index contract (reject), and a
 * fallback/default-token case (the baked TokensDark resolves seriesColor(0) == accent).
 */
class SeriesColorTest {

    /** A distinct-color tokens fixture in a chosen mode (baked default copied with the mode + a known pool). */
    private fun tokens(
        mode: PaletteMode,
        pool: List<Color> = listOf(Color(0xFF111111), Color(0xFF222222), Color(0xFF333333)),
    ): ThemeTokens = TokensDark.copy(
        accent = Color(0xFF0000FF),
        text = Color(0xFFEEEEEE),
        stop = Color(0xFFFF0000),
        heat = Color(0xFFFFAA00),
        go = Color(0xFF00FF00),
        pool = pool,
        mode = mode,
    )

    @Test
    fun accentLeads_inAllThreeModes() {
        for (mode in PaletteMode.entries) {
            val t = tokens(mode)
            assertEquals("seriesColor(0) must be accent in $mode", t.accent, t.seriesColor(0))
        }
    }

    @Test
    fun colorfulMode_accentLeadsThenPoolWraps() {
        val t = tokens(PaletteMode.Colorful)
        assertEquals(t.accent, t.seriesColor(0))
        assertEquals(t.pool[0], t.seriesColor(1))
        assertEquals(t.pool[1], t.seriesColor(2))
        assertEquals(t.pool[2], t.seriesColor(3))
        // D-09 wrap: index 4 → pool[(4-1) % 3] = pool[0]; well past the pool too.
        assertEquals(t.pool[0], t.seriesColor(4))
        assertEquals(t.pool[1], t.seriesColor(5))
    }

    @Test
    fun colorfulMode_neverEmitsStatusColors() {
        val t = tokens(PaletteMode.Colorful)
        val statusColors = setOf(t.stop, t.go, t.heat)
        for (i in 0..50) {
            assertTrue(
                "Colorful seriesColor($i) must stay within accent+pool (no status color)",
                t.seriesColor(i) !in statusColors || t.seriesColor(i) == t.accent,
            )
        }
        // Stronger: every value is accent or one of the pool slots — never stop/go/caution.
        val allowed = (listOf(t.accent) + t.pool).toSet()
        for (i in 0..50) {
            assertTrue("Colorful seriesColor($i) outside accent+pool", t.seriesColor(i) in allowed)
        }
    }

    @Test
    fun colorfulMode_wrapsWellPastPoolLength_noCap() {
        val t = tokens(PaletteMode.Colorful)
        // D-09: NO user-facing cap — a large index wraps via the modulo.
        val big = 1000
        assertEquals(t.pool[(big - 1) % t.pool.size], t.seriesColor(big))
    }

    @Test
    fun simpleMode_alternatesAccentAndText() {
        val t = tokens(PaletteMode.Simple)
        assertEquals(t.accent, t.seriesColor(0))
        assertEquals(t.text, t.seriesColor(1))
        assertEquals(t.accent, t.seriesColor(2))
        assertEquals(t.text, t.seriesColor(3))
        assertEquals(t.accent, t.seriesColor(100))
        assertEquals(t.text, t.seriesColor(101))
    }

    @Test
    fun highContrastMode_wrapsAccentStopCautionGoText() {
        val t = tokens(PaletteMode.HighContrast)
        assertEquals(t.accent, t.seriesColor(0))
        assertEquals(t.stop, t.seriesColor(1))
        assertEquals(t.heat, t.seriesColor(2)) // caution == heat (D-13)
        assertEquals(t.go, t.seriesColor(3))
        assertEquals(t.text, t.seriesColor(4))
        // i % 5 wrap.
        assertEquals(t.accent, t.seriesColor(5))
        assertEquals(t.stop, t.seriesColor(6))
        assertEquals(t.text, t.seriesColor(9))
    }

    @Test
    fun emptyPool_guardsAgainstDivideByZero() {
        val t = tokens(PaletteMode.Colorful, pool = emptyList())
        // Falls back to accent for every index rather than `% 0` crashing (CR-02 guard).
        assertEquals(t.accent, t.seriesColor(0))
        assertEquals(t.accent, t.seriesColor(1))
        assertEquals(t.accent, t.seriesColor(99))
    }

    @Test
    fun negativeIndex_isRejected() {
        val t = tokens(PaletteMode.Colorful)
        assertThrows(IllegalArgumentException::class.java) { t.seriesColor(-1) }
        assertThrows(IllegalArgumentException::class.java) { t.seriesColor(-42) }
    }

    @Test
    fun fallbackTokens_seriesColorZeroIsAccent() {
        // The baked fallback/default tokens carry mode = Colorful (the default field value); a
        // manually-built default theme must still resolve the lead series to accent.
        assertEquals(PaletteMode.Colorful, TokensDark.mode)
        assertEquals(TokensDark.accent, TokensDark.seriesColor(0))
        assertEquals(TokensLight.accent, TokensLight.seriesColor(0))
    }
}
