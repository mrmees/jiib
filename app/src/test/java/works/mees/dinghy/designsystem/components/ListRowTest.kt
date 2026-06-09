package works.mees.dinghy.designsystem.components

import org.junit.Assert.fail
import org.junit.Test

/**
 * RED scaffold for ListRow fill/border state selection (Wave 0 / 23-01).
 *
 * ListRow is the single-row scrollable-list primitive in the jiib redesign grammar.
 * The fill convention (from sketch-findings-dinghy-display/references/foundations.md):
 *   - unselected: background = Color.Transparent, border = t.outline (1.5dp)
 *   - selected:   background = t.accentSoft, border = t.accentLine (2dp)
 *
 * These tests are pure-logic assertions on the state-→fill/border mapping function extracted
 * from ListRow's composable logic. The bodies are fail() — 23-05 builds ListRow and extracts a
 * testable helper, then turns these GREEN.
 *
 * ⚠ Wave-0 compile contract: NO references to `ListRow` composable or any Compose symbol that
 * does not exist yet. Only JUnit4 + Kotlin standard imports are used.
 */
class ListRowTest {

    /**
     * When selected == true the fill should use the accentSoft token (translucent accent fill),
     * signalling the item is the active selection.
     * (23-PATTERNS.md §ListRow fill convention / sketch-findings foundations.md)
     */
    @Test
    fun selected_uses_accentSoft_fill() {
        fail("RED: implemented in 23-05 — listRowFillFor(selected = true) should resolve to accentSoft token")
    }

    /**
     * When selected == false the fill should be Color.Transparent — list rows are content
     * surfaces (translucent by the content-vs-controls fill convention).
     * (23-RESEARCH §"Component Class API Shapes / ListRow")
     */
    @Test
    fun unselected_uses_transparent_fill() {
        fail("RED: implemented in 23-05 — listRowFillFor(selected = false) should resolve to Color.Transparent")
    }

    /**
     * When selected == true the border width is 2dp (emphasised selection ring).
     * When unselected the border is 1.5dp (standard outline weight).
     * (23-PATTERNS.md §ListRow generalized API shape)
     */
    @Test
    fun selected_uses_2dp_border() {
        fail("RED: implemented in 23-05 — listRowBorderWidthFor(selected = true) == 2.dp; false == 1.5.dp")
    }
}
