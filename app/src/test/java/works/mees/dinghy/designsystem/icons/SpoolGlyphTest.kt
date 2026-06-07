package works.mees.dinghy.designsystem.icons

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure host test for [SpoolGlyph]'s swatch-count → render-branch decision (18.3-02, SC2/SC4).
 *
 * The render branch is factored OUT of the Compose `Canvas` into the pure [bandRenderFor] helper so the
 * D-03/D-04/D-08/D-09 decision is host-testable without a Compose harness or a Robolectric shadow. This
 * test pins:
 *   - 0 swatches  → [BandRender.Empty]            (D-03: the band is OMITTED — honest empty spool)
 *   - 1 swatch    → [BandRender.Solid]            (D-08: flat fill of the single color)
 *   - 2 swatches  → [BandRender.Gradient]         (D-04: two-stop gradient across both)
 *   - 3+ swatches → [BandRender.Gradient] of [0,1] (D-04/D-09: first two only, index [0..1])
 */
class SpoolGlyphTest {

    private val red = Color(0xFFFF0000)
    private val green = Color(0xFF00FF00)
    private val blue = Color(0xFF0000FF)

    @Test
    fun zeroSwatches_isEmpty_bandOmitted() {
        assertEquals(
            "0 swatches must omit the band entirely (D-03 empty spool)",
            BandRender.Empty,
            bandRenderFor(emptyList()),
        )
    }

    @Test
    fun oneSwatch_isSolidOfThatColor() {
        assertEquals(
            "1 swatch must be a flat solid fill of that color (D-08)",
            BandRender.Solid(red),
            bandRenderFor(listOf(red)),
        )
    }

    @Test
    fun twoSwatches_isGradientOfBoth() {
        assertEquals(
            "2 swatches must be a two-stop gradient across both (D-04)",
            BandRender.Gradient(red, green),
            bandRenderFor(listOf(red, green)),
        )
    }

    @Test
    fun threeOrMoreSwatches_isGradientOfFirstTwoOnly() {
        assertEquals(
            "3+ swatches must use ONLY the first two swatches (D-04/D-09 index [0..1])",
            BandRender.Gradient(red, green),
            bandRenderFor(listOf(red, green, blue)),
        )
    }
}
