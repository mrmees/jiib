package works.mees.jiib.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeZEndstopTest {
    @Test fun `virtual z endstop pin detected (probe is the Z endstop)`() {
        assertTrue(probeIsZEndstopFromPin("probe:z_virtual_endstop"))
        assertTrue(probeIsZEndstopFromPin("PROBE:Z_VIRTUAL_ENDSTOP"))   // case-insensitive
        assertTrue(probeIsZEndstopFromPin("bltouch:z_virtual_endstop"))
    }

    @Test fun `plain pin, sensorless XY, missing - not detected`() {
        assertFalse(probeIsZEndstopFromPin("^PA7"))                      // plain physical pin
        assertFalse(probeIsZEndstopFromPin("tmc2209_stepper_x:virtual_endstop")) // sensorless X, not z_
        assertFalse(probeIsZEndstopFromPin(null))
        assertFalse(probeIsZEndstopFromPin(""))
    }
}
