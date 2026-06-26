package works.mees.jiib.ui.calibration

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrewBoxAspectTest {
    @Test fun square_box_is_one() {
        assertEquals(1f, screwBoxAspect(300.0, 300.0), 0.0001f)
    }

    @Test fun wide_box_preserves_ratio() {
        assertEquals(1.5f, screwBoxAspect(300.0, 200.0), 0.0001f)
    }

    @Test fun tall_box_preserves_ratio() {
        assertEquals(0.5f, screwBoxAspect(150.0, 300.0), 0.0001f)
    }

    @Test fun clamps_extreme_wide_layout() {
        assertEquals(4f, screwBoxAspect(1000.0, 1.0), 0.0001f)
    }

    @Test fun clamps_extreme_tall_layout() {
        assertEquals(0.25f, screwBoxAspect(1.0, 1000.0), 0.0001f)
    }
}
