package works.mees.jiib.designsystem.layout

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusZonesTest {

    @Test
    fun default_tier_is_the_full_focus_inset() {
        assertEquals(FocusInset, focusZoneInsetDp(FocusZoneInset.Default))
        assertEquals(16.dp, focusZoneInsetDp(FocusZoneInset.Default))
    }

    @Test
    fun dense_tier_is_half_the_focus_inset() {
        assertEquals(FocusInset / 2, focusZoneInsetDp(FocusZoneInset.Dense))
        assertEquals(8.dp, focusZoneInsetDp(FocusZoneInset.Dense))
    }

    @Test
    fun flush_tier_is_zero() {
        assertEquals(0.dp, focusZoneInsetDp(FocusZoneInset.Flush))
    }

    @Test
    fun padding_never_adds_a_top_inset() {
        // LAW 4 + the 2026-06-15 owner ruling: sides+bottom only, TOP always 0.
        for (tier in FocusZoneInset.entries) {
            val p = focusZonePadding(tier)
            assertEquals(0.dp, p.top)
            assertEquals(focusZoneInsetDp(tier), p.start)
            assertEquals(focusZoneInsetDp(tier), p.end)
            assertEquals(focusZoneInsetDp(tier), p.bottom)
        }
    }
}
