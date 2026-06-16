package works.mees.dinghy.ui.printstatus

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivePrintFormatTest {
    @Test fun percent_rounds_to_int() {
        assertEquals(0, progressPercent(0.0))
        assertEquals(42, progressPercent(0.4249))
        assertEquals(43, progressPercent(0.425))
        assertEquals(100, progressPercent(1.0))
        assertEquals(100, progressPercent(1.5))   // clamps
        assertEquals(0, progressPercent(-0.2))    // clamps
    }

    @Test fun layer_height_full() {
        assertEquals("1.2/55mm · 5/220 layers", formatLayerHeight(1.2, 55.0, 5, 220))
    }

    @Test fun layer_height_no_total_height() {
        assertEquals("1.2mm · 5/220 layers", formatLayerHeight(1.2, null, 5, 220))
    }

    @Test fun layer_height_no_layers_drops_clause() {
        assertEquals("1.2/55mm", formatLayerHeight(1.2, 55.0, null, 220))
        assertEquals("1.2/55mm", formatLayerHeight(1.2, 55.0, 5, null))
    }

    @Test fun layer_height_no_z_falls_back_to_dash() {
        assertEquals("—", formatLayerHeight(null, 55.0, null, null))
        assertEquals("— · 5/220 layers", formatLayerHeight(null, 55.0, 5, 220))
    }

    @Test fun basename_strips_path_keeps_extension() {
        assertEquals("benchy.gcode", printFileBasename("prints/calib/benchy.gcode"))
        assertEquals("benchy.gcode", printFileBasename("benchy.gcode"))
        assertEquals("", printFileBasename(""))
    }
}
