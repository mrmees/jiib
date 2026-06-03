// RED scaffold (Wave 0) — turns GREEN in 09-07 (calibration hub gating predicate).
package works.mees.dinghy.calibration

import kotlinx.serialization.json.jsonObject
import works.mees.dinghy.net.MoonrakerJson
import works.mees.dinghy.state.Capabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave-0 RED scaffold (09-01) — turned GREEN by 09-07 (hub gating predicate).
 *
 * REQ-CALIB-01. The Calibration hub marks each routine SUPPORTED iff the printer reports its
 * gating object via `Capabilities.hasObject(<object>)` (re-derived every reconnect). Per the
 * UI-SPEC §1 OWNER OVERRIDE, ALL five routines RENDER (unsupported greyed-but-tappable, not
 * omitted) — but `isSupported` is the live gate that drives accent-vs-grey + Run-enabled.
 *
 * Binds the REAL `/fixtures/configfile_screws_e5.json` to anchor the captured printer's object
 * surface (the E5 reports screws_tilt_adjust/z_tilt/bed_mesh/probe; no quad_gantry_level).
 *
 * Production symbol referenced (NOT YET BUILT → RED): `calibrationSupported(caps, routine)` in
 * `works.mees.dinghy.calibration`, where `routine` is a `CalibrationRoutine` enum carrying its
 * gating object name; returns Boolean isSupported.
 */
class CalibrationGateTest {

    /** Anchor the predicate to the captured-from-real-hardware config fixture. */
    private fun e5ConfigOnClasspath(): Boolean {
        val res = javaClass.getResource("/fixtures/configfile_screws_e5.json") ?: return false
        return MoonrakerJson.parseToJsonElement(res.readText())
            .jsonObject.containsKey("screws_tilt_adjust")
    }

    @Test
    fun e5LikeCaps_supportsScrewsZtiltBedmeshProbe_notQgl() {
        assertTrue("config fixture present", e5ConfigOnClasspath())
        // E5-like object surface (no quad_gantry_level on a bed-slinger).
        val caps = Capabilities(
            objects = setOf("screws_tilt_adjust", "z_tilt", "bed_mesh", "probe", "manual_probe"),
        )
        assertTrue(calibrationSupported(caps, CalibrationRoutine.SCREWS_TILT))
        assertTrue(calibrationSupported(caps, CalibrationRoutine.Z_TILT))
        assertTrue(calibrationSupported(caps, CalibrationRoutine.BED_MESH))
        assertTrue(calibrationSupported(caps, CalibrationRoutine.PROBE_CALIBRATE))
        // QGL gates itself off — built blind (D-02), unsupported on both test printers.
        assertFalse(calibrationSupported(caps, CalibrationRoutine.QUAD_GANTRY_LEVEL))
    }

    @Test
    fun eachRoutineGatesOnItsOwnObject() {
        // A printer with ONLY bed_mesh supports bed-mesh and nothing else.
        val caps = Capabilities(objects = setOf("bed_mesh"))
        assertTrue(calibrationSupported(caps, CalibrationRoutine.BED_MESH))
        assertFalse(calibrationSupported(caps, CalibrationRoutine.SCREWS_TILT))
        assertFalse(calibrationSupported(caps, CalibrationRoutine.Z_TILT))
        assertFalse(calibrationSupported(caps, CalibrationRoutine.PROBE_CALIBRATE))
    }

    @Test
    fun allFiveRoutinesEnumerated_forRenderAll() {
        // The hub renders ALL routines (greyed if unsupported) — the enum must cover all five.
        assertEquals(5, CalibrationRoutine.entries.size)
    }
}
