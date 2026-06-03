// RED scaffold (Wave 0) — turns GREEN in 09-06 (probeCalibrateGate).
package works.mees.dinghy.calibration

import kotlinx.serialization.json.jsonObject
import works.mees.dinghy.net.MoonrakerJson
import works.mees.dinghy.state.Capabilities
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Wave-0 RED scaffold (09-01) — turned GREEN by 09-06 (`probeCalibrateGate`).
 *
 * REQ-CALIB-05 (A3). The probe-present gate: a printer with a `probe` object calibrates Z via
 * PROBE_CALIBRATE; a probe-less printer falls back to Z_ENDSTOP_CALIBRATE. Pure predicate over
 * `Capabilities.hasObject("probe")` (re-derived every reconnect from printer.objects.list).
 *
 * Binds the REAL `/fixtures/configfile_screws_e5.json` to prove the E5's klicky probe surface is
 * present on the captured printer (the fixture documents the real `screws_tilt_adjust` config the
 * gate's sibling routines key off — the E5 has a probe, so PROBE_CALIBRATE is the live path).
 *
 * Production symbol referenced (NOT YET BUILT → RED): `probeCalibrateGate(caps): String` in
 * `works.mees.dinghy.calibration`, returning the gcode command name to dispatch.
 */
class ProbePresentGateTest {

    /** The E5 carries a klicky probe — assert the captured config fixture is on the classpath. */
    private fun e5HasScrewsConfig(): Boolean {
        val res = javaClass.getResource("/fixtures/configfile_screws_e5.json") ?: return false
        return MoonrakerJson.parseToJsonElement(res.readText())
            .jsonObject.containsKey("screws_tilt_adjust")
    }

    @Test
    fun probePresent_picksProbeCalibrate() {
        // The real E5 (klicky probe) → PROBE_CALIBRATE. The config fixture pins the captured printer.
        assertEquals(true, e5HasScrewsConfig())
        val caps = Capabilities(objects = setOf("probe", "manual_probe", "toolhead"))
        assertEquals("PROBE_CALIBRATE", probeCalibrateGate(caps))
    }

    @Test
    fun probeAbsent_fallsBackToZEndstopCalibrate() {
        val caps = Capabilities(objects = setOf("toolhead", "manual_probe"))
        assertEquals("Z_ENDSTOP_CALIBRATE", probeCalibrateGate(caps))
    }
}
