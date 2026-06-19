package works.mees.dinghy.ui.spool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [colorFamily] — the client-side hue-family classifier that replaced Spoolman's
 * CIE76 color_similarity matching (which could not find muted colors like olive #64794b).
 * See docs/superpowers/specs/2026-06-18-spool-color-family-filter-design.md.
 */
class ColorFamilyTest {

    @Test fun `palette swatches self-classify`() {
        val palette = mapOf(
            "Black" to "#000000", "White" to "#FFFFFF", "Gray" to "#808080",
            "Natural" to "#EDE6D6", "Red" to "#FF0000", "Orange" to "#FF8000",
            "Yellow" to "#FFFF00", "Green" to "#00C000", "Blue" to "#0050FF",
            "Purple" to "#8000FF", "Pink" to "#FF60C0", "Brown" to "#7A4A20",
        )
        palette.forEach { (name, hex) -> assertEquals("$hex should be $name", name, colorFamily(hex)) }
    }

    @Test fun `olive green is Green`() = assertEquals("Green", colorFamily("#64794b"))

    @Test fun `real owner filaments classify intuitively`() {
        assertEquals("Brown", colorFamily("#886543"))
        assertEquals("Blue", colorFamily("#5dc0f0"))
        assertEquals("Yellow", colorFamily("#F6FA00"))
        assertEquals("Red", colorFamily("#E63034"))
        assertEquals("Gray", colorFamily("#3A3C3B"))
    }

    @Test fun `natural family catches creams and ivories`() {
        listOf("#FFFDD0", "#FFFFF0", "#F5F5DC", "#F0EAD6", "#E3DAC9", "#E8E0CE")
            .forEach { assertEquals("$it should be Natural", "Natural", colorFamily(it)) }
    }

    @Test fun `pure and off-white stay White`() {
        assertEquals("White", colorFamily("#FFFFFF"))
        assertEquals("White", colorFamily("#FAFAFA"))
    }

    @Test fun `saturated pale yellow is Yellow not Natural`() = assertEquals("Yellow", colorFamily("#FDFD96"))
    @Test fun `tan is a light Brown`() = assertEquals("Brown", colorFamily("#D2B48C"))
    @Test fun `pastel blue stays Blue`() = assertEquals("Blue", colorFamily("#AEC6CF"))

    @Test fun `absent or garbage hex is null`() {
        assertNull(colorFamily(null))
        assertNull(colorFamily(""))
        assertNull(colorFamily("not-a-hex"))
        assertNull(colorFamily("#12"))
    }
}
