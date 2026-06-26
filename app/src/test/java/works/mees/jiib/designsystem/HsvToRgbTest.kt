package works.mees.jiib.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure host tests for [hsvToRgb] (Phase 19 LED page). Primary hues map to pure R/G/B, value scales the
 * output, saturation 0 yields grey, hue wraps, and every channel stays within 0f..1f.
 */
class HsvToRgbTest {

    private val eps = 1e-4f

    private fun assertRgb(expected: Triple<Float, Float, Float>, actual: Triple<Float, Float, Float>) {
        assertEquals(expected.first, actual.first, eps)
        assertEquals(expected.second, actual.second, eps)
        assertEquals(expected.third, actual.third, eps)
    }

    @Test
    fun primaryHuesMapToPureChannels() {
        assertRgb(Triple(1f, 0f, 0f), hsvToRgb(0f, 1f, 1f)) // red
        assertRgb(Triple(0f, 1f, 0f), hsvToRgb(120f, 1f, 1f)) // green
        assertRgb(Triple(0f, 0f, 1f), hsvToRgb(240f, 1f, 1f)) // blue
    }

    @Test
    fun valueScalesOutput() {
        assertRgb(Triple(0.5f, 0f, 0f), hsvToRgb(0f, 1f, 0.5f))
    }

    @Test
    fun saturationZeroIsGreyAtValue() {
        assertRgb(Triple(1f, 1f, 1f), hsvToRgb(0f, 0f, 1f))
        assertRgb(Triple(1f, 1f, 1f), hsvToRgb(200f, 0f, 1f))
        assertRgb(Triple(0.5f, 0.5f, 0.5f), hsvToRgb(123f, 0f, 0.5f))
    }

    @Test
    fun hueWrapsAndClamps() {
        // 360 == 0 (red); negative hue wraps; >360 wraps.
        assertRgb(hsvToRgb(0f, 1f, 1f), hsvToRgb(360f, 1f, 1f))
        assertRgb(hsvToRgb(0f, 1f, 1f), hsvToRgb(720f, 1f, 1f))
        assertRgb(hsvToRgb(240f, 1f, 1f), hsvToRgb(-120f, 1f, 1f))
    }

    @Test
    fun defaultSaturationIsOne() {
        // The v1 fixed-saturation decision: omitting saturation == full saturation.
        assertRgb(hsvToRgb(0f, 1f, 1f), hsvToRgb(hue = 0f, value = 1f))
    }

    @Test
    fun channelsAlwaysWithinUnitRange() {
        for (h in 0..360 step 15) {
            for (vTenth in 0..10) {
                val (r, g, b) = hsvToRgb(h.toFloat(), 1f, vTenth / 10f)
                assertTrue(r in 0f..1f && g in 0f..1f && b in 0f..1f)
            }
        }
    }
}
