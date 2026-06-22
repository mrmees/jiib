package works.mees.dinghy.ui.outputs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.command.SetLedArgs
import works.mees.dinghy.designsystem.hsvToRgb
import works.mees.dinghy.designsystem.rgbToHsv

/**
 * Host command-gen tests for the LED Focus surface. The surface now uses FULL H/S/V (+ optional White)
 * and dispatches the complete r/g/b/w state via [ledChannelsFromHsv] → [PrinterCommands.setLed]:
 *  - RGB-only LED (white = null): `WHITE=` is OMITTED.
 *  - RGBW LED (white non-null): `WHITE=` is sent (an independent channel).
 *  - White-only LED: dispatched as `SET_LED … RED=0 GREEN=0 BLUE=0 WHITE=w` (built in the surface's
 *    dispatchLed guard, not via [ledChannelsFromHsv]).
 * This supersedes the retired "every color dispatch carries WHITE=0" policy (T-19-06-05): saturation
 * is now user-controlled and white is independent.
 */
class OutputLedCommandTest {

    @Test
    fun `rgbw dispatch sends all four channels from hsv plus white`() {
        val args = ledChannelsFromHsv("strip", h = 0f, s = 1f, v = 1f, white = 0.5f)
        assertEquals("strip", args.name)
        assertEquals(1f, args.r)
        assertEquals(0f, args.g)
        assertEquals(0f, args.b)
        assertEquals(0.5f, args.w)
        assertEquals(
            "SET_LED LED=strip RED=1 GREEN=0 BLUE=0 WHITE=0.5",
            PrinterCommands.setLed(args.name, args.r, args.g, args.b, args.w),
        )
    }

    @Test
    fun `rgb dispatch omits white when no white channel`() {
        val args = ledChannelsFromHsv("strip", h = 120f, s = 1f, v = 1f, white = null)
        assertEquals(0f, args.r)
        assertEquals(1f, args.g)
        assertEquals(0f, args.b)
        assertNull("RGB-only LED must omit the white channel so SET_LED does not send WHITE=", args.w)
        assertEquals(
            "SET_LED LED=strip RED=0 GREEN=1 BLUE=0",
            PrinterCommands.setLed(args.name, args.r, args.g, args.b, args.w),
        )
    }

    @Test
    fun `saturation is honored (no longer fixed at 1)`() {
        // hue=0 (red) at half saturation, full value → r=1, g=b=0.5 (HSV 0,0.5,1).
        val args = ledChannelsFromHsv("strip", h = 0f, s = 0.5f, v = 1f, white = null)
        assertEquals(1f, args.r, 1e-3f)
        assertEquals(0.5f, args.g, 1e-3f)
        assertEquals(0.5f, args.b, 1e-3f)
    }

    @Test
    fun `white-only dispatch is RGB zero plus white`() {
        // The white-only branch builds this directly (RGB forced to 0, white = brightness).
        assertEquals(
            "SET_LED LED=caselight RED=0 GREEN=0 BLUE=0 WHITE=0.8",
            PrinterCommands.setLed("caselight", 0f, 0f, 0f, w = 0.8f),
        )
    }

    @Test
    fun `command uses the BARE name with no family prefix (HIGH-1)`() {
        val args = ledChannelsFromHsv("FILTER_led", h = 240f, s = 1f, v = 1f, white = null)
        val cmd = PrinterCommands.setLed(args.name, args.r, args.g, args.b, args.w)
        assertTrue("must use bare name: $cmd", cmd.startsWith("SET_LED LED=FILTER_led "))
        assertTrue("must not carry a family prefix", !cmd.contains("LED=led "))
    }

    @Test
    fun `pending target channels match the 2dp wire format (busy-lock clears)`() {
        // The optimistic pending tuple must equal the 2dp values SET_LED actually sends (the printer
        // echoes those in color_data); raw HSV floats could miss by ~epsilon at half-step values and
        // wedge the busy lock until timeout.
        val args = SetLedArgs("strip", 0.125f, 0.336f, 1f, 0.0f)
        assertEquals(listOf(0.13, 0.34, 1.0, 0.0), ledTargetChannels(args))
        // …and those are exactly the channels the wire command carries.
        assertEquals(
            "SET_LED LED=strip RED=0.13 GREEN=0.34 BLUE=1 WHITE=0",
            PrinterCommands.setLed(args.name, args.r, args.g, args.b, args.w),
        )
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
