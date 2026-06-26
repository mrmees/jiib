// RED scaffold (Wave 0) — turns GREEN in 09-06 (parseZPosition + manual_probe page-state).
package works.mees.jiib.calibration

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import works.mees.jiib.net.MoonrakerJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave-0 RED scaffold (09-01) — turned GREEN by 09-06 (`parseZPosition` + page-state derive).
 *
 * REQ-CALIB-05. Pins the manual-probe parse against the REAL probed shape committed as
 * `/fixtures/manual_probe_e5.json` (captured MID-SESSION 2026-06-02, `is_active:true`).
 *
 * REAL-SHAPE CONTRACT (RESEARCH Pattern 3 + the real gcode_response line shape):
 *   - manual_probe mid-session: `is_active:true`, `z_position` (current Double),
 *     `z_position_lower` / `z_position_upper` (Doubles).
 *   - The gcode_response line from real hardware is
 *       `"// Z position: ?????? --> 7.624 <-- ??????"`
 *     — **the bounds can be the literal `??????` (unknown) before they're set.**
 *     `parseZPosition` MUST treat `??????` as null/unknown, NOT assume three floats.
 *   - A malformed line returns null.
 *   - The page state is driven by `is_active` (active → live jog page; false → idle).
 *
 * Production symbols referenced (NOT YET BUILT → RED): `parseZPosition(line): ZPositionBracket?`
 * and `manualProbeActive(manualProbe): Boolean` in `works.mees.jiib.calibration`.
 * `ZPositionBracket` exposes nullable `lower`, non-null `current`, nullable `upper`.
 */
class ManualProbeStateTest {

    private fun manualProbeFixture(): JsonObject {
        val res = javaClass.getResource("/fixtures/manual_probe_e5.json")
            ?: error("fixture /fixtures/manual_probe_e5.json missing from test classpath")
        return MoonrakerJson.parseToJsonElement(res.readText()).jsonObject
    }

    @Test
    fun allKnownBounds_parsesLowerCurrentUpper() {
        // The plan's all-known example: lower=5.001, current=4.901, upper=4.800.
        val b = parseZPosition("Z position: 5.001 --> 4.901 <-- 4.800")
        assertEquals(5.001, b?.lower!!, 1e-6)
        assertEquals(4.901, b.current, 1e-6)
        assertEquals(4.800, b.upper!!, 1e-6)
    }

    @Test
    fun unknownBounds_questionMarks_areNull_currentStillParsed() {
        // REAL hardware line: bounds unknown before they're set — "??????" → null, NOT a float.
        val b = parseZPosition("// Z position: ?????? --> 7.624 <-- ??????")
        assertNull("lower bound is unknown → null", b?.lower)
        assertEquals(7.624, b?.current!!, 1e-6)
        assertNull("upper bound is unknown → null", b.upper)
    }

    @Test
    fun malformedLine_returnsNull() {
        assertNull(parseZPosition("totally not a z position line"))
        assertNull(parseZPosition("Z position: ----"))
    }

    @Test
    fun pageState_readsIsActiveFromFixture() {
        // The captured fixture is mid-session: is_active == true.
        assertTrue(manualProbeActive(manualProbeFixture()))
        // An idle object → not active.
        val idle = MoonrakerJson.parseToJsonElement(
            """{"manual_probe":{"is_active":false}}"""
        ).jsonObject
        assertEquals(false, manualProbeActive(idle))
    }
}
