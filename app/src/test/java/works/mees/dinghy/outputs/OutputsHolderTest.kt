// RED scaffold (Wave 0, 19-02) — turns GREEN in Wave 1 (the OutputsHolder degrade-logic plan).
package works.mees.dinghy.outputs

import org.junit.Assert.fail
import org.junit.Test

/**
 * Wave-0 RED scaffold (19-02) — turned GREEN by the Wave-1 holder plan.
 *
 * The holder owns per-output display state + degrade logic for the live-status edge cases the real
 * captures expose: an absent `.value`/`.speed` (row shows no value but stays tappable), servo
 * `.value` being PWM not angle (value HIDDEN — unreadable as degrees, optimistic reached() never
 * confirms so it relies on the timeout backstop), static read-only pins, and the 0..1→0..100%
 * DISPLAY scaling living in the holder (not the reducer — RESEARCH § Live status field map).
 *
 * HARD RULE [[dinghy-wave0-red-scaffold-compile]]: typed `fail(...)` bodies only; no reference to
 * OutputsHolder / OutputDescriptor (built in Wave 1). The whole test sourceset must compile today.
 */
class OutputsHolderTest {

    @Test
    fun absentValueHidesRowValueButRowTappable() {
        // Wave 1: a descriptor whose live status lacks `.value`/`.speed` → row hides the value
        // readout but remains tappable (you can still command it).
        fail("not implemented — Wave 1")
    }

    @Test
    fun servoValueHidden() {
        // Wave 1: servo `.value` is PWM, NOT an angle → the holder HIDES the value (not shown as °);
        // optimistic reached() never confirms a servo, so the busy-lock clears via timeout only.
        fail("not implemented — Wave 1")
    }

    @Test
    fun staticPinReadOnly() {
        // Wave 1: an output_pin with a static_value (no run-time control) is surfaced READ-ONLY.
        fail("not implemented — Wave 1")
    }

    @Test
    fun displayScalingSpeedToPct() {
        // Wave 1: raw `.speed`/`.value` 0..1 → 0..100% happens in the HOLDER (display layer), not
        // the reducer (e.g. 0.5 → "50%"). The reducer keeps the raw wire float.
        fail("not implemented — Wave 1")
    }
}
