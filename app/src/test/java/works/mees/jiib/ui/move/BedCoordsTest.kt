package works.mees.jiib.ui.move

import org.junit.Assert.assertEquals
import org.junit.Test

class BedCoordsTest {
    private val bed = BedExtent(xMin = -5.0, xMax = 355.0, yMin = 0.0, yMax = 355.0)

    @Test
    fun fit_isAspectLocked_noStretch() {
        val r = bedFitRect(bed, boxW = 200f, boxH = 100f)
        assertEquals(
            (bed.xMax - bed.xMin).toFloat() / (bed.yMax - bed.yMin).toFloat(),
            r.width / r.height,
            0.001f,
        )
    }

    @Test
    fun bedToScreen_originCornerMapsToBottomLeft_topRightToTop() {
        val r = bedFitRect(bed, boxW = 360f, boxH = 355f)
        val bl = bedToScreen(bed, r, x = -5.0, y = 0.0)
        val tr = bedToScreen(bed, r, x = 355.0, y = 355.0)
        assertEquals(r.left, bl.x, 0.5f)
        assertEquals(r.bottom, bl.y, 0.5f)
        assertEquals(r.right, tr.x, 0.5f)
        assertEquals(r.top, tr.y, 0.5f)
    }

    @Test
    fun screenToBed_isInverseOf_bedToScreen() {
        val r = bedFitRect(bed, boxW = 300f, boxH = 300f)
        val p = bedToScreen(bed, r, x = 100.0, y = 200.0)
        val back = screenToBed(bed, r, p.x, p.y)
        assertEquals(100.0, back.first, 0.01)
        assertEquals(200.0, back.second, 0.01)
    }
}
