package works.mees.jiib.designsystem.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusGlyphTest {

    @Test
    fun side_is_the_fraction_of_the_smaller_dimension() {
        // landscape-ish body: height is the limit
        assertEquals(100.dp, focusGlyphSideDp(maxWidth = 400.dp, maxHeight = 200.dp, fraction = 0.5f))
        // portrait-ish body: width is the limit
        assertEquals(150.dp, focusGlyphSideDp(maxWidth = 300.dp, maxHeight = 500.dp, fraction = 0.5f))
    }

    @Test
    fun fraction_scales_linearly() {
        assertEquals(80.dp, focusGlyphSideDp(maxWidth = 200.dp, maxHeight = 300.dp, fraction = 0.4f))
    }

    @Test
    fun degenerate_zero_space_yields_zero() {
        assertEquals(0.dp, focusGlyphSideDp(maxWidth = 0.dp, maxHeight = 300.dp, fraction = 0.5f))
    }
}
