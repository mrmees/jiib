package works.mees.dinghy.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.dinghy.ui.shell.nextSizeOverride

/**
 * Host-pure proof of the D-07 TEXT-SIZE cycler stepping logic (15.2-02 Task 1). The stepper MERGES onto
 * the current override so an active STYLE override survives a size tap (HIGH-4), and it steps `fs`
 * through the FontScale.S/M/L multipliers independently of the style axis.
 */
class TextSizeCyclerTest {

    private val sizes = listOf(FontScale.S.multiplier, FontScale.M.multiplier, FontScale.L.multiplier)

    @Test
    fun size_cycler_steps_S_M_L() {
        // From "no override" the first size step lands on the first size; advancing 6 times cycles S/M/L twice.
        var current: ThemeOverride? = null
        val seen = mutableListOf<Float?>()
        repeat(6) {
            current = nextSizeOverride(current)
            seen += current!!.fs
        }
        // The cycle visits the three sizes in order, wrapping after L.
        val cycle = listOf(seen[0], seen[1], seen[2])
        assertEquals(sizes.toSet(), cycle.toSet())
        // Wrap: positions 3/4/5 repeat positions 0/1/2.
        assertEquals(seen[0], seen[3])
        assertEquals(seen[1], seen[4])
        assertEquals(seen[2], seen[5])
    }

    @Test
    fun size_advance_does_not_invent_a_style_axis() {
        val o = nextSizeOverride(null)
        assertNull("size advance must not invent a dark axis", o.dark)
        assertNull("size advance must not invent a paletteMode axis", o.paletteMode)
    }

    @Test
    fun size_advance_preserves_active_style_override() {
        // HIGH-4: a STYLE is already applied (non-null dark+paletteMode). Advancing SIZE must keep them
        // verbatim while fs advances. Construct a `current` that genuinely carries the other axis.
        val current = ThemeOverride(dark = false, paletteMode = "HighContrast", fs = FontScale.S.multiplier)
        val next = nextSizeOverride(current)
        assertEquals("style dark must survive a size tap", false, next.dark)
        assertEquals("style paletteMode must survive a size tap", "HighContrast", next.paletteMode)
        // fs actually advanced off S.
        assertEquals(FontScale.M.multiplier, next.fs)
    }
}
