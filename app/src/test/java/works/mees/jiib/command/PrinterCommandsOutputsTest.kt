// Wave-1 (19-03) — RED scaffold (19-02) turned GREEN: the PrinterCommands output-builders plan.
package works.mees.jiib.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave-1 (19-03) — the generic-output gcode builders are clamp-before-format (ASVS V5) and take the
 * BARE section name (HIGH-1 — never a `fan_generic `/`output_pin ` prefix). UI works in 0..100% and
 * degrees; the wire is 0..1 (SPEED/VALUE/RED…) or 0/1 (digital pin). Expected gcode strings
 * (RESEARCH § Code Examples):
 *
 *   setGenericFan(name, pct)     → "SET_FAN_SPEED FAN=$name SPEED=${pct/100 clamp 0..1, 2dp}"
 *   setLed(name, r,g,b,w?)       → "SET_LED LED=$name RED=.. GREEN=.. BLUE=.. [WHITE=..]"  (each clamp 0..1, 2dp)
 *                                  Off (D-12) = setLed(name,0,0,0,0) → all channels =0
 *   setServoAngle(name,deg,max)  → "SET_SERVO SERVO=$name ANGLE=${deg clamp 0..max}"
 *   setServoDisable(name)        → "SET_SERVO SERVO=$name WIDTH=0"   (A3 — disable, optional/per-servo)
 *   setPinDigital(name,on)       → "SET_PIN PIN=$name VALUE=${if on 1 else 0}"
 *   setPinPwm(name,pct)          → "SET_PIN PIN=$name VALUE=${pct/100 clamp 0..1, 2dp}"  (also pwm_tool — no SET_PWM_TOOL)
 */
class PrinterCommandsOutputsTest {

    @Test
    fun setGenericFanScalesPctToWire() {
        assertEquals("SET_FAN_SPEED FAN=FILTER_fan SPEED=0.5", PrinterCommands.setGenericFan("FILTER_fan", 50))
    }

    @Test
    fun setGenericFanClampsOutOfRange() {
        assertEquals("SET_FAN_SPEED FAN=FILTER_fan SPEED=1", PrinterCommands.setGenericFan("FILTER_fan", 150))
        assertEquals("SET_FAN_SPEED FAN=FILTER_fan SPEED=0", PrinterCommands.setGenericFan("FILTER_fan", -10))
    }

    @Test
    fun setGenericFanUsesBareName() {
        // HIGH-1: the family prefix must NEVER reach the wire.
        val cmd = PrinterCommands.setGenericFan("FILTER_fan", 50)
        assertFalse("family prefix leaked onto the wire", cmd.contains("fan_generic"))
        assertTrue(cmd.contains("FAN=FILTER_fan"))
    }

    @Test
    fun setLedClampsChannels() {
        assertEquals(
            "SET_LED LED=chamber_light RED=1 GREEN=0 BLUE=0.5",
            PrinterCommands.setLed("chamber_light", 2f, -1f, 0.5f),
        )
    }

    @Test
    fun setLedOffEmitsAllZero() {
        // D-12: explicit all-zero Off including WHITE when a white channel exists.
        assertEquals(
            "SET_LED LED=chamber_light RED=0 GREEN=0 BLUE=0 WHITE=0",
            PrinterCommands.setLed("chamber_light", 0f, 0f, 0f, 0f),
        )
    }

    @Test
    fun setLedOmitsWhiteWhenNull() {
        assertEquals(
            "SET_LED LED=chamber_light RED=0 GREEN=0 BLUE=0",
            PrinterCommands.setLed("chamber_light", 0f, 0f, 0f),
        )
    }

    @Test
    fun setServoClampsToMaxDeg() {
        assertEquals("SET_SERVO SERVO=camera_servo ANGLE=90", PrinterCommands.setServoAngle("camera_servo", 200, maxDeg = 90))
        assertEquals("SET_SERVO SERVO=camera_servo ANGLE=0", PrinterCommands.setServoAngle("camera_servo", -5, maxDeg = 90))
        // default ceiling = 180
        assertEquals("SET_SERVO SERVO=camera_servo ANGLE=180", PrinterCommands.setServoAngle("camera_servo", 270, 180))
    }

    @Test
    fun setServoDisableEmitsWidthZero() {
        // A3 — the servo Off/disable form.
        assertEquals("SET_SERVO SERVO=camera_servo WIDTH=0", PrinterCommands.setServoDisable("camera_servo"))
    }

    @Test
    fun setPinDigitalEmits0or1() {
        assertEquals("SET_PIN PIN=mosfet2 VALUE=1", PrinterCommands.setPinDigital("mosfet2", true))
        assertEquals("SET_PIN PIN=mosfet2 VALUE=0", PrinterCommands.setPinDigital("mosfet2", false))
    }

    @Test
    fun setPinPwmScalesAndClamps() {
        assertEquals("SET_PIN PIN=mosfet2 VALUE=0.5", PrinterCommands.setPinPwm("mosfet2", 50))
        assertEquals("SET_PIN PIN=mosfet2 VALUE=1", PrinterCommands.setPinPwm("mosfet2", 150))
        assertEquals("SET_PIN PIN=mosfet2 VALUE=0", PrinterCommands.setPinPwm("mosfet2", -10))
    }

    @Test
    fun pwmToolUsesSetPin() {
        // A pwm_tool has NO dedicated command — it routes through setPinPwm (SET_PIN), never SET_PWM_TOOL.
        val cmd = PrinterCommands.setPinPwm("toolchanger_pwm", 50)
        assertTrue(cmd.startsWith("SET_PIN PIN=toolchanger_pwm"))
        assertFalse(cmd.contains("SET_PWM_TOOL"))
    }

    @Test
    fun outputPctToWireMatchesDispatchedValue() {
        // 17-07 invariant: the markPending wire helper returns the SAME clamped value the builder formats.
        assertEquals(0.5, PrinterCommands.outputPctToWire(50), 0.0)
        assertEquals(1.0, PrinterCommands.outputPctToWire(150), 0.0)
        assertEquals(0.0, PrinterCommands.outputPctToWire(-10), 0.0)
    }
}
