package works.mees.jiib.ui.temperature

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.jiib.R

/** Pure contract for the resting Focus title (owner 2026-07-02: mode-aware, resting state only). */
class TempRestingTitleTest {
    @Test
    fun restingTitleTracksMode() {
        assertEquals(R.string.temp_mode_monitoring, tempRestingTitleRes(TempMode.Monitoring))
        assertEquals(R.string.temp_mode_adjust, tempRestingTitleRes(TempMode.Adjust))
    }
}
