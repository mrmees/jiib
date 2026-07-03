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
    fun top_inset_is_8dp_except_flush() {
        // LAW 4 + owner UAT 2026-07-02: top = 8dp for Default/Dense (content was kissing the
        // header divider); 0dp for Flush (media fill). Supersedes the 2026-06-15 zero-top ruling.
        for (tier in FocusZoneInset.entries) {
            val p = focusZonePadding(tier)
            val expectedTop = if (tier == FocusZoneInset.Flush) 0.dp else 8.dp
            assertEquals(expectedTop, p.top)
            assertEquals(focusZoneInsetDp(tier), p.start)
            assertEquals(focusZoneInsetDp(tier), p.end)
            assertEquals(focusZoneInsetDp(tier), p.bottom)
        }
    }

    @Test
    fun dock_presence_selects_the_dense_tier() {
        assertEquals(FocusZoneInset.Dense, focusZoneInsetFor(hasDock = true))
        assertEquals(FocusZoneInset.Default, focusZoneInsetFor(hasDock = false))
    }
}
