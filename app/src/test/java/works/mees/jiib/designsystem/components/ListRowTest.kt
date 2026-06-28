package works.mees.jiib.designsystem.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GREEN tests for the [ListRow] fill/border state-selection helpers (23-05).
 *
 * ListRow is the single-row scrollable-list primitive in the jiib redesign grammar.
 * The fill convention (from sketch-findings-jiib/references/foundations.md):
 *   - unselected: background = Color.Transparent, border = t.outline (1.5dp)
 *   - selected:   background = t.accentSoft, border = t.accentLine (2dp)
 *
 * These tests assert the pure state-→fill/border mapping helpers extracted from ListRow's
 * composable logic. The helpers are token-agnostic (boolean/Dp) so they run in a plain
 * JUnit4 host test without a Compose runtime.
 */
class ListRowTest {

    /**
     * When selected == true the fill helper returns true (meaning "use accentSoft"),
     * signalling the item is the active selection.
     * (23-PATTERNS.md §ListRow fill convention / sketch-findings foundations.md)
     */
    @Test
    fun selected_uses_accentSoft_fill() {
        // listRowUsesAccentFill(true) == true → caller applies t.accentSoft
        assertTrue(
            "selected=true should resolve to accentSoft fill (listRowUsesAccentFill returns true)",
            listRowUsesAccentFill(selected = true),
        )
    }

    /**
     * When selected == false the fill helper returns false (meaning "use Color.Transparent"),
     * because list rows are content surfaces — transparent by the fill convention.
     * (23-RESEARCH §"Component Class API Shapes / ListRow")
     */
    @Test
    fun unselected_uses_transparent_fill() {
        // listRowUsesAccentFill(false) == false → caller applies Color.Transparent
        assertFalse(
            "selected=false should resolve to transparent fill (listRowUsesAccentFill returns false)",
            listRowUsesAccentFill(selected = false),
        )
    }

    /**
     * When selected == true the border width is 2.dp (emphasised selection ring).
     * When unselected the border is 1.5.dp (standard outline weight).
     * (23-PATTERNS.md §ListRow generalized API shape)
     */
    @Test
    fun selected_uses_2dp_border() {
        assertEquals(
            "selected=true should use 2.dp border",
            2.dp,
            listRowBorderWidthFor(selected = true),
        )
        assertEquals(
            "selected=false should use 1.5.dp border",
            1.5.dp,
            listRowBorderWidthFor(selected = false),
        )
    }

    /**
     * The neutral press highlight applies ONLY to an unselected row that is currently pressed.
     * A selected row keeps its accent treatment on press (owner decision 2026-06-28: selection
     * wins; a selected row must not flash gray, which would read as "deselecting").
     */
    @Test
    fun pressed_unselected_uses_neutral_fill() {
        assertTrue(
            "unselected + pressed should resolve to the neutral press fill",
            listRowUsesPressedFill(selected = false, pressed = true),
        )
    }

    @Test
    fun pressed_selected_stays_accent() {
        assertFalse(
            "selected rows ignore the press fill (selection wins) even while pressed",
            listRowUsesPressedFill(selected = true, pressed = true),
        )
    }

    @Test
    fun resting_unselected_has_no_press_fill() {
        assertFalse(
            "unselected + not pressed should resolve to transparent (no press fill)",
            listRowUsesPressedFill(selected = false, pressed = false),
        )
    }
}
