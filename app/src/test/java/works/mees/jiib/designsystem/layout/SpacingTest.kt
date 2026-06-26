package works.mees.jiib.designsystem.layout

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class SpacingTest {
    @Test fun gapS_is_one_eighth_U_eq_8dp_at_64() {
        assertEquals(8f, gapS(64.dp).value, 0.01f)
    }
    @Test fun gapM_is_three_sixteenths_U_eq_12dp_at_64() {
        assertEquals(12f, gapM(64.dp).value, 0.01f)
    }
    @Test fun gaps_scale_monotonically_with_U() {
        // larger U → larger gaps (U-relative, not fixed)
        assert(gapS(96.dp).value > gapS(64.dp).value)
        assert(gapM(96.dp).value > gapM(64.dp).value)
        // gapM is always larger than gapS at the same U
        assert(gapM(64.dp).value > gapS(64.dp).value)
    }
    @Test fun padFloat_is_14dp_constant() {
        assertEquals(14f, padFloat.value, 0.01f)
    }
}
