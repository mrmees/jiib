package works.mees.dinghy.ui.outputs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.designsystem.hsvToRgb
import works.mees.dinghy.designsystem.rgbToHsv

/**
 * Host command-gen tests for the LED page (Phase 19, 19-06). The page computes RGB via [hsvToRgb] from
 * (hue, fixed sat = 1, brightness) and dispatches [PrinterCommands.setLed] with the BARE command name and
 * the WHITE-CHANNEL POLICY (every color dispatch sends WHITE=0, T-19-06-05). These tests pin the exact
 * SET_LED string the wire carries plus the rgbToHsv↔hsvToRgb round-trip the page uses to seed initial state.
 */
class OutputLedCommandTest {

    /** Mirror the page: hue + brightness → hsvToRgb(hue, 1f, brightness/100) → setLed with WHITE=0. */
    private fun ledCommand(name: String, hue: Float, brightnessPct: Float): String {
        val (r, g, b) = hsvToRgb(hue, 1f, brightnessPct / 100f)
        return PrinterCommands.setLed(name, r, g, b, w = 0f)
    }

    /** Mirror the white-only page (GAP-B): brightness → setLed(name, 0,0,0, w = brightness/100). */
    private fun whiteCommand(name: String, brightnessPct: Float): String =
        PrinterCommands.setLed(name, 0f, 0f, 0f, w = (brightnessPct / 100f).coerceIn(0f, 1f))

    @Test
    fun `red full brightness is RED=1 with WHITE=0`() {
        assertEquals(
            "SET_LED LED=caselight RED=1 GREEN=0 BLUE=0 WHITE=0",
            ledCommand("caselight", hue = 0f, brightnessPct = 100f),
        )
    }

    @Test
    fun `green half brightness is GREEN=0_5 with WHITE=0`() {
        assertEquals(
            "SET_LED LED=caselight RED=0 GREEN=0.5 BLUE=0 WHITE=0",
            ledCommand("caselight", hue = 120f, brightnessPct = 50f),
        )
    }

    @Test
    fun `explicit Off is all-zero with WHITE=0 (D-12)`() {
        assertEquals(
            "SET_LED LED=caselight RED=0 GREEN=0 BLUE=0 WHITE=0",
            PrinterCommands.setLed("caselight", 0f, 0f, 0f, w = 0f),
        )
    }

    @Test
    fun `every color dispatch carries WHITE=0 (white-channel policy)`() {
        // A spread of hue/brightness — WHITE=0 must always be present (no stale white lingers).
        for (hue in listOf(0f, 60f, 120f, 180f, 240f, 300f)) {
            for (b in listOf(25f, 50f, 100f)) {
                val cmd = ledCommand("strip", hue, b)
                assertTrue("WHITE=0 missing in: $cmd", cmd.endsWith("WHITE=0"))
            }
        }
    }

    @Test
    fun `command uses the BARE name with no family prefix (HIGH-1)`() {
        val cmd = ledCommand("FILTER_led", hue = 240f, brightnessPct = 100f)
        assertTrue("must use bare name: $cmd", cmd.startsWith("SET_LED LED=FILTER_led "))
        assertTrue("must not carry a family prefix", !cmd.contains("LED=led "))
    }

    @Test
    fun `white-only brightness drives the WHITE channel (GAP-B)`() {
        assertEquals(
            "SET_LED LED=caselight RED=0 GREEN=0 BLUE=0 WHITE=0.8",
            whiteCommand("caselight", brightnessPct = 80f),
        )
    }

    @Test
    fun `white-only Off is all-zero with WHITE=0`() {
        assertEquals(
            "SET_LED LED=caselight RED=0 GREEN=0 BLUE=0 WHITE=0",
            whiteCommand("caselight", brightnessPct = 0f),
        )
    }

    @Test
    fun `white-only command uses the BARE name (HIGH-1)`() {
        val cmd = whiteCommand("FILTER_led", brightnessPct = 100f)
        assertTrue("must use bare name: $cmd", cmd.startsWith("SET_LED LED=FILTER_led "))
        assertTrue("white channel present", cmd.endsWith("WHITE=1"))
        assertTrue("RGB channels zero", cmd.contains("RED=0 GREEN=0 BLUE=0"))
    }

    @Test
    fun `rgbToHsv round-trips primary hues (initial-state seed)`() {
        for (hue in listOf(0f, 120f, 240f)) {
            val (r, g, b) = hsvToRgb(hue, 1f, 1f)
            val (h2, s2, v2) = rgbToHsv(r, g, b)
            assertEquals("hue round-trip", hue, h2, 0.5f)
            assertEquals("saturation recovered", 1f, s2, 1e-3f)
            assertEquals("value recovered", 1f, v2, 1e-3f)
        }
    }
}
