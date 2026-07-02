package works.mees.jiib.theme.compose

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure size-selection walk behind [FocusText] (measurement-based height-fit).
 * Mirrors the DigestColumnTest harness: a synthetic measurement model stands in for the real
 * [androidx.compose.ui.text.TextMeasurer], so the fit logic is testable with no Compose/Android
 * runtime. The +0.5px tolerance and the multiply-from-base precedent are `digestFit`'s.
 */
class FocusTextFitTest {

    /**
     * Synthetic full-wrap height model: a body of [totalTextPx] worth of glyphs, rendered at [sp],
     * wraps into `ceil(totalTextPx*sp / widthPx)`-ish lines. We model it simpler: rendered height =
     * number of lines * lineHeight, where lines shrink as the font shrinks. For determinism we use
     * height(sp) = base * sp — smaller font, proportionally shorter block (fewer/tighter lines).
     */
    private fun linearHeight(base: Float): (Int) -> Float = { sp -> base * sp }

    private val singleLineAtMin: (Int) -> Float = { sp -> 2f * sp }

    @Test
    fun picks_max_size_when_it_already_fits() {
        // height(sp) = 10 * sp; at maxSp 34 → 340 <= 400 → chosen 34, no line cap.
        val fit = focusTextFit(
            maxHeightPx = 400f,
            minSp = 15,
            maxSp = 34,
            fullHeightAt = linearHeight(10f),
            singleLineHeightAt = singleLineAtMin,
        )
        assertEquals(FocusTextFit(34, Int.MAX_VALUE), fit)
    }

    @Test
    fun steps_down_to_the_first_size_that_fits() {
        // height(sp) = 10 * sp; budget 250 → first sp from 34 downward with 10*sp <= 250 is 25.
        val fit = focusTextFit(
            maxHeightPx = 250f,
            minSp = 15,
            maxSp = 34,
            fullHeightAt = linearHeight(10f),
            singleLineHeightAt = singleLineAtMin,
        )
        assertEquals(FocusTextFit(25, Int.MAX_VALUE), fit)
    }

    @Test
    fun tolerance_forgives_a_sub_half_pixel_overshoot() {
        // height(sp) = 10*sp; at sp 30 → 300.0003 (just over 300) must still count as fitting 300.
        val fit = focusTextFit(
            maxHeightPx = 300f,
            minSp = 15,
            maxSp = 34,
            fullHeightAt = { sp -> 10f * sp + if (sp == 30) 0.0003f else 0f },
            singleLineHeightAt = singleLineAtMin,
        )
        assertEquals(FocusTextFit(30, Int.MAX_VALUE), fit)
    }

    @Test
    fun clamps_to_min_and_caps_lines_when_nothing_fits() {
        // Even minSp 15 needs 150 > 100 budget. Fall back to minSp with maxLines from the
        // ACTUAL single-line height at min (2*15 = 30) → floor(100 / 30) = 3.
        val fit = focusTextFit(
            maxHeightPx = 100f,
            minSp = 15,
            maxSp = 34,
            fullHeightAt = linearHeight(10f),
            singleLineHeightAt = singleLineAtMin,
        )
        assertEquals(FocusTextFit(15, 3), fit)
    }

    @Test
    fun clamped_line_cap_is_at_least_one() {
        // Single line at min taller than the whole box → still render one line, never zero.
        val fit = focusTextFit(
            maxHeightPx = 10f,
            minSp = 15,
            maxSp = 34,
            fullHeightAt = linearHeight(10f),
            singleLineHeightAt = { 40f },
        )
        assertEquals(FocusTextFit(15, 1), fit)
    }

    @Test
    fun min_equals_max_fits_at_that_single_size() {
        val fit = focusTextFit(
            maxHeightPx = 400f,
            minSp = 20,
            maxSp = 20,
            fullHeightAt = linearHeight(10f),   // 200 <= 400
            singleLineHeightAt = singleLineAtMin,
        )
        assertEquals(FocusTextFit(20, Int.MAX_VALUE), fit)
    }
}
