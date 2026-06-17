package works.mees.dinghy.designsystem.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure mapping contract for [focusEdgeStroke] (Focus Frame law §2 — edge encodes meaning). */
class FocusEdgeTest {
    @Test
    fun neutral_uses_the_resting_edge_color_at_listrow_weight() {
        // The resting edge maps the caller-supplied color through at list-row weight; the call site
        // feeds t.accentLine (soft accent) for the soft-accent resting edge.
        val s = focusEdgeStroke(FocusEdge.Neutral, outline = Color.Gray)
        assertEquals(Color.Gray, s!!.color)
        assertEquals(1.5f, s.widthDp, 0.001f)
    }

    @Test
    fun data_uses_the_literal_data_color_heavier_so_it_reads() {
        val red = Color.Red
        val s = focusEdgeStroke(FocusEdge.Data(red), outline = Color.Gray)
        assertEquals(red, s!!.color)
        assertEquals(3f, s.widthDp, 0.001f)
    }

    @Test
    fun progress_is_drawn_specially_not_a_uniform_border() {
        // Progress draws a perimeter bar in FocusFrame, so the uniform-stroke helper returns null.
        assertNull(focusEdgeStroke(FocusEdge.Progress(0.5f, Color.Red), outline = Color.Gray))
    }

    @Test
    fun header_shows_estop_only_when_printing_and_handler_present() {
        assertEquals(true, headerShowsEStop(isPrinting = true, onEmergencyStop = {}))
        assertEquals(false, headerShowsEStop(isPrinting = false, onEmergencyStop = {}))
        assertEquals(false, headerShowsEStop(isPrinting = true, onEmergencyStop = null))
        assertEquals(false, headerShowsEStop(isPrinting = false, onEmergencyStop = null))
    }
}
