package works.mees.dinghy.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host unit tests for the [brandTint] D-06 contrast-floor helper. `androidx.compose.ui.graphics.Color`
 * is a value class and its `.luminance()` extension is pure math (no Android runtime), so these run on
 * the JVM with no Robolectric/instrumentation.
 */
class BrandTintTest {

    private val white = Color(0xFFFFFFFF)
    private val darkNavy = Color(0xFF0B1020)
    private val accentBlue = Color(0xFF3B82F6)

    /** WCAG-style relative-luminance contrast ratio mirror — independent of the helper's internals. */
    private fun ratio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun highContrastAccentOnDarkBg_returnsAccent() {
        // Accent blue on a dark navy bg clears the 3.0:1 large-graphic floor → keep the accent.
        assertTrue("test premise: accent must clear the floor", ratio(accentBlue, darkNavy) >= BRAND_CONTRAST_FLOOR)
        assertEquals(accentBlue, brandTint(accent = accentBlue, bg = darkNavy, text = white))
    }

    @Test
    fun lowContrastAccentNearBg_returnsText() {
        // An accent nearly indistinguishable from the bg washes out → fall back to the legible text token.
        val nearBg = Color(0xFF0C1122) // a hair off darkNavy
        assertTrue("test premise: near-bg accent must fail the floor", ratio(nearBg, darkNavy) < BRAND_CONTRAST_FLOOR)
        assertEquals(white, brandTint(accent = nearBg, bg = darkNavy, text = white))
    }

    @Test
    fun floorIsTheDocumentedWcagLargeGraphicMinimum() {
        assertEquals(3.0f, BRAND_CONTRAST_FLOOR, 0.0001f)
    }

    @Test
    fun boundaryAtOrAboveFloorKeepsAccent_belowFallsBack() {
        // White-on-black is the maximum ratio (21:1) → always keeps the accent.
        assertEquals(white, brandTint(accent = white, bg = Color(0xFF000000), text = darkNavy))
        // Identical accent and bg is ratio 1.0 (well below floor) → always falls back to text.
        assertEquals(darkNavy, brandTint(accent = darkNavy, bg = darkNavy, text = darkNavy))
    }
}
