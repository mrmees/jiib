package works.mees.dinghy.designsystem.components

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import works.mees.dinghy.designsystem.layout.ListFrameInset
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure mapping contract for [focusFramePadding] — the Focus registration frame (LAYOUT.md R26). */
class FocusFramePaddingTest {
    @Test
    fun region_self_owns_the_8dp_frame_on_all_four_sides() {
        val p = focusFramePadding(FocusFramePlacement.Region)
        assertEquals(ListFrameInset, p.calculateTopPadding())
        assertEquals(ListFrameInset, p.calculateBottomPadding())
        assertEquals(ListFrameInset, p.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(ListFrameInset, p.calculateRightPadding(LayoutDirection.Ltr))
    }

    @Test
    fun composed_keeps_horizontal_only_so_the_caller_column_owns_vertical() {
        val p = focusFramePadding(FocusFramePlacement.Composed)
        assertEquals(0.dp, p.calculateTopPadding())
        assertEquals(0.dp, p.calculateBottomPadding())
        assertEquals(ListFrameInset, p.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(ListFrameInset, p.calculateRightPadding(LayoutDirection.Ltr))
    }
}
