package works.mees.jiib.designsystem.layout

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The single source of the 8dp edge-registration grid (LAYOUT.md R26). */
class RegisteredRegionTest {
    @Test
    fun region_inset_and_gap_are_the_8dp_grid() {
        assertEquals(8.dp, RegionInset)
        assertEquals(8.dp, RegionGap)
    }

    @Test
    fun region_constants_share_one_source() {
        // One knob: both derive from ListFrameInset so a single edit moves the whole app.
        assertEquals(ListFrameInset, RegionInset)
        assertEquals(ListFrameInset, RegionGap)
    }
}
