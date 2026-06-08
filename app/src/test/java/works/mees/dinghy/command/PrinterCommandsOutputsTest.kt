// RED scaffold (Wave 0, 19-02) — turns GREEN in Wave 1 (the PrinterCommands output-builders plan).
package works.mees.dinghy.command

import org.junit.Assert.fail
import org.junit.Test

/**
 * Wave-0 RED scaffold (19-02) — turned GREEN by the Wave-1 command-builder plan.
 *
 * The builders are clamp-before-format (ASVS V5) and take the BARE section name (HIGH-1 — never a
 * `fan_generic `/`output_pin ` prefix). UI works in 0..100% and degrees; the wire is 0..1 (SPEED/
 * VALUE/RED…) or 0/1 (digital pin). Expected gcode strings (RESEARCH § Code Examples, lines 240-256):
 *
 *   setGenericFan(name, pct)     → "SET_FAN_SPEED FAN=$name SPEED=${pct/100 clamp 0..1, 2dp}"
 *   setLed(name, r,g,b,w?)       → "SET_LED LED=$name RED=.. GREEN=.. BLUE=.. [WHITE=..]"  (each clamp 0..1, 2dp)
 *                                  Off (D-12) = setLed(name,0,0,0,0) → all channels =0
 *   setServoAngle(name,deg,max)  → "SET_SERVO SERVO=$name ANGLE=${deg clamp 0..max}"
 *   setServoDisable(name)        → "SET_SERVO SERVO=$name WIDTH=0"   (A3 — disable, optional/per-servo)
 *   setPinDigital(name,on)       → "SET_PIN PIN=$name VALUE=${if on 1 else 0}"
 *   setPinPwm(name,pct)          → "SET_PIN PIN=$name VALUE=${pct/100 clamp 0..1, 2dp}"  (also pwm_tool — no SET_PWM_TOOL)
 *
 * HARD RULE [[dinghy-wave0-red-scaffold-compile]]: typed `fail(...)` only; do NOT call the new
 * builders (they don't exist on PrinterCommands yet). The whole test sourceset must compile today.
 */
class PrinterCommandsOutputsTest {

    @Test
    fun setGenericFanScalesPctToWire() {
        // Wave 1: setGenericFan("FILTER_fan", 50) == "SET_FAN_SPEED FAN=FILTER_fan SPEED=0.5"
        fail("not implemented — Wave 1")
    }

    @Test
    fun setGenericFanClampsOutOfRange() {
        // Wave 1: setGenericFan("FILTER_fan", 150) clamps → SPEED=1 ; (-10) → SPEED=0
        fail("not implemented — Wave 1")
    }

    @Test
    fun setGenericFanUsesBareName() {
        // Wave 1 (HIGH-1): output is the BARE name — must NOT contain the "fan_generic" prefix.
        fail("not implemented — Wave 1")
    }

    @Test
    fun setLedClampsChannels() {
        // Wave 1: setLed("chamber_light", 2f, -1f, 0.5f) clamps each channel to 0..1 (RED=1 GREEN=0 BLUE=0.5).
        fail("not implemented — Wave 1")
    }

    @Test
    fun setLedOffEmitsAllZero() {
        // Wave 1 (D-12): setLed("chamber_light", 0f,0f,0f,0f) → RED=0 GREEN=0 BLUE=0 WHITE=0 (off).
        fail("not implemented — Wave 1")
    }

    @Test
    fun setServoClampsToMaxDeg() {
        // Wave 1: setServoAngle("camera_servo", 200, maxDeg=90) clamps → ANGLE=90 ; (-5) → ANGLE=0
        fail("not implemented — Wave 1")
    }

    @Test
    fun setServoDisableEmitsWidthZero() {
        // Wave 1 (A3): setServoDisable("camera_servo") == "SET_SERVO SERVO=camera_servo WIDTH=0"
        fail("not implemented — Wave 1")
    }

    @Test
    fun setPinDigitalEmits0or1() {
        // Wave 1: setPinDigital("mosfet2", true) → VALUE=1 ; (false) → VALUE=0
        fail("not implemented — Wave 1")
    }

    @Test
    fun setPinPwmScalesAndClamps() {
        // Wave 1: setPinPwm("mosfet2", 50) → VALUE=0.5 ; (150) → VALUE=1 ; (-10) → VALUE=0
        fail("not implemented — Wave 1")
    }

    @Test
    fun pwmToolUsesSetPin() {
        // Wave 1: a pwm_tool has NO dedicated command — it routes through setPinPwm (SET_PIN), never
        // a hypothetical SET_PWM_TOOL.
        fail("not implemented — Wave 1")
    }
}
