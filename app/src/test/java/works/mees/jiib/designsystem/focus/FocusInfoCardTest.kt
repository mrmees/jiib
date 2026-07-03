package works.mees.jiib.designsystem.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusInfoCardTest {
    @Test
    fun width_is_the_binding_constraint_when_rows_are_wide() {
        // wFrac = 960*0.96/1200 = 0.768 ; hFrac = (800-0)/400 = 2.0 → 0.768
        assertEquals(0.768f, infoCardScale(960f, 800f, 1200f, 400f, 0f, 0, 0.7f, 2.4f), 0.001f)
    }

    @Test
    fun height_binds_when_many_rows() {
        // hFrac = (300 - 10*4)/520 = 0.5 ; wFrac large → clamped to minFrac 0.7? no: 0.5 < 0.7 → 0.7
        assertEquals(0.7f, infoCardScale(2000f, 300f, 100f, 520f, 10f, 4, 0.7f, 2.4f), 0.001f)
    }

    @Test
    fun roomy_layouts_clamp_at_max() {
        assertEquals(2.4f, infoCardScale(4000f, 4000f, 100f, 100f, 0f, 0, 0.7f, 2.4f), 0.001f)
    }

    @Test
    fun degenerate_measurements_scale_to_one() {
        assertEquals(1f, infoCardScale(0f, 0f, 0f, 0f, 0f, 0, 0.7f, 2.4f), 0.001f)
    }
}
