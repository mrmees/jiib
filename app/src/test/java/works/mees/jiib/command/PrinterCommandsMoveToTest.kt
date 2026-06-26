package works.mees.jiib.command

import org.junit.Assert.assertEquals
import org.junit.Test

class PrinterCommandsMoveToTest {
    @Test
    fun moveTo_buildsModeSafeAbsoluteMove_withAllAxes() {
        val g = PrinterCommands.moveTo(x = 100.0, y = 120.5, z = 5.0, feedMmMin = 9000)
        assertEquals(
            "SAVE_GCODE_STATE NAME=dd_moveto\nG90\nG1 X100 Y120.5 Z5 F9000\nRESTORE_GCODE_STATE NAME=dd_moveto",
            g,
        )
    }

    @Test
    fun moveTo_omitsNullAxes() {
        val g = PrinterCommands.moveTo(x = 100.0, y = 120.0, z = null, feedMmMin = 6000)
        assertEquals(
            "SAVE_GCODE_STATE NAME=dd_moveto\nG90\nG1 X100 Y120 F6000\nRESTORE_GCODE_STATE NAME=dd_moveto",
            g,
        )
    }

    @Test
    fun moveTo_clampsToProvidedBounds() {
        val g = PrinterCommands.moveTo(
            x = 999.0, y = 50.0, z = -10.0, feedMmMin = 6000,
            minBounds = listOf(-5.0, 0.0, 0.0), maxBounds = listOf(355.0, 355.0, 340.0),
        )
        assertEquals(
            "SAVE_GCODE_STATE NAME=dd_moveto\nG90\nG1 X355 Y50 Z0 F6000\nRESTORE_GCODE_STATE NAME=dd_moveto",
            g,
        )
    }

    @Test
    fun moveTo_clampsFeedToJogFeedCeiling() {
        val g = PrinterCommands.moveTo(x = 10.0, y = null, z = null, feedMmMin = 999_999)
        assertEquals(
            "SAVE_GCODE_STATE NAME=dd_moveto\nG90\nG1 X10 F30000\nRESTORE_GCODE_STATE NAME=dd_moveto",
            g,
        )
    }
}
