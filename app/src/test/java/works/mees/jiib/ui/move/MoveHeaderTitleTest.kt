package works.mees.jiib.ui.move

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure contract for the dynamic Focus-header title (owner 2026-07-02: coords replace the static
 *  mode title in coordinate modes; " / " separates axes; Z is two-decimals with unit). */
class MoveHeaderTitleTest {
    @Test
    fun touchMove_stagedWinsOverLive() {
        assertEquals(
            "X 10.5 / Y 20.0",
            moveDynamicHeaderTitle(MoveMode.TouchMove, 10.5, 20.0, 1f, 2f, 3f, 100.0, 200.0),
        )
    }

    @Test
    fun touchMove_fallsBackToLiveThenDash() {
        assertEquals(
            "X 100.0 / Y 200.0",
            moveDynamicHeaderTitle(MoveMode.TouchMove, null, null, 1f, 2f, 3f, 100.0, 200.0),
        )
        assertEquals(
            "X — / Y —",
            moveDynamicHeaderTitle(MoveMode.TouchMove, null, null, 1f, 2f, 3f, null, null),
        )
    }

    @Test
    fun xy_showsWorkingTarget() {
        assertEquals(
            "X 12.0 / Y 34.5",
            moveDynamicHeaderTitle(MoveMode.XY, null, null, 12f, 34.5f, 0f, 1.0, 2.0),
        )
    }

    @Test
    fun z_twoDecimalsWithUnit() {
        assertEquals(
            "Z 12.40 mm",
            moveDynamicHeaderTitle(MoveMode.Z, null, null, 0f, 0f, 12.4f, null, null),
        )
    }

    @Test
    fun staticModesKeepStaticTitles() {
        assertNull(moveDynamicHeaderTitle(MoveMode.Microstep, null, null, 0f, 0f, 0f, null, null))
        assertNull(moveDynamicHeaderTitle(MoveMode.Endstops, null, null, 0f, 0f, 0f, null, null))
        assertNull(moveDynamicHeaderTitle(MoveMode.SaveDialog, null, null, 0f, 0f, 0f, null, null))
        assertNull(moveDynamicHeaderTitle(MoveMode.Bookmark("b"), null, null, 0f, 0f, 0f, null, null))
    }
}
