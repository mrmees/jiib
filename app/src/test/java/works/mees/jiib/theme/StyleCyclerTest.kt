package works.mees.jiib.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.ui.shell.nextSizeOverride
import works.mees.jiib.ui.shell.nextStyleOverride

/**
 * Host-pure proof of the D-06 STYLE cycler stepping logic (15.2-02 Task 1). The stepper MERGES onto
 * the current override so an active SIZE override survives a style tap (HIGH-4), and it never touches
 * the seed (seedHex is not even in [ThemeOverride] — D-06: the user's real seed falls through).
 */
class StyleCyclerTest {

    /** The exact 6 combos, in the order the cycler must visit them, with the SAME mode spelling as ThemePrefs. */
    private val expectedCombos = listOf(
        ThemeOverride(dark = true, paletteMode = "Colorful"),
        ThemeOverride(dark = true, paletteMode = "Simple"),
        ThemeOverride(dark = true, paletteMode = "HighContrast"),
        ThemeOverride(dark = false, paletteMode = "Colorful"),
        ThemeOverride(dark = false, paletteMode = "Simple"),
        ThemeOverride(dark = false, paletteMode = "HighContrast"),
    )

    @Test
    fun style_cycler_steps_six_combos_in_order() {
        // Start from "no override" (null), advance 12 times; expect the 6 combos then a clean wrap.
        var current: ThemeOverride? = null
        val seen = mutableListOf<Pair<Boolean?, String?>>()
        repeat(12) {
            current = nextStyleOverride(current)
            seen += current!!.dark to current!!.paletteMode
        }
        val expectedPairs = expectedCombos.map { it.dark to it.paletteMode }
        // First six = the six combos in order; next six = the same again (wrap after 6).
        assertEquals(expectedPairs, seen.subList(0, 6))
        assertEquals(expectedPairs, seen.subList(6, 12))
    }

    @Test
    fun style_cycler_leaves_seed_untouched() {
        // A style advance carries ONLY dark+paletteMode; there is no seed axis to carry, and fs stays null
        // when it started null (the size axis is not invented by a style tap).
        val o = nextStyleOverride(null)
        assertNull("style advance must not invent an fs axis", o.fs)
        assertTrue(o.dark != null && o.paletteMode != null)
    }

    @Test
    fun style_advance_preserves_active_size_override() {
        // HIGH-4: a SIZE is already applied (non-null fs). Advancing STYLE must keep that fs verbatim while
        // dark/paletteMode advance. Construct a `current` that genuinely carries the other axis.
        val current = ThemeOverride(dark = true, paletteMode = "Colorful", fs = FontScale.L.multiplier)
        val next = nextStyleOverride(current)
        assertEquals("size override must survive a style tap", FontScale.L.multiplier, next.fs)
        // The style axis actually advanced (Colorful -> Simple within dark).
        assertEquals(true, next.dark)
        assertEquals("Simple", next.paletteMode)
    }

    @Test
    fun size_advance_preserves_active_style_override_via_style_helper_roundtrip() {
        // Sanity that the two helpers compose: after a style step, a size step keeps the new style.
        val styled = nextStyleOverride(null) // dark+Colorful
        val sized = nextSizeOverride(styled)
        assertEquals(styled.dark, sized.dark)
        assertEquals(styled.paletteMode, sized.paletteMode)
        assertTrue(sized.fs != null)
    }
}
