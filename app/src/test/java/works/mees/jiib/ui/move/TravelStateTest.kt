package works.mees.jiib.ui.move

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelStateTest {
    @Test fun pending_whileFarFromTarget() {
        assertTrue(travelPending(curX = 0.0, curY = 0.0, tgtX = 100.0, tgtY = 0.0, epsilon = 0.5))
    }
    @Test fun complete_whenWithinEpsilon_onBothAxes() {
        assertFalse(travelPending(curX = 99.9, curY = 50.05, tgtX = 100.0, tgtY = 50.0, epsilon = 0.5))
    }
    @Test fun pending_whenOneAxisStillFar() {
        assertTrue(travelPending(curX = 100.0, curY = 10.0, tgtX = 100.0, tgtY = 50.0, epsilon = 0.5))
    }
}
