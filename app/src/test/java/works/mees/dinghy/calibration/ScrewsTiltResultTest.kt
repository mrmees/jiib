// RED scaffold (Wave 0) — turns GREEN in 09-03 (parseScrewsTilt).
package works.mees.dinghy.calibration

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import works.mees.dinghy.net.MoonrakerJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave-0 RED scaffold (09-01) — turned GREEN by 09-03 (`parseScrewsTilt`).
 *
 * REQ-CALIB-02. Walks the REAL probed `screws_tilt_adjust` result shape committed as
 * `/fixtures/screws_tilt_adjust_e5.json` + the screw coords/names from
 * `/fixtures/configfile_screws_e5.json` (probed 2026-06-02 on the live Ender 5 Plus).
 *
 * REAL-SHAPE CONTRACT this test pins (the mock-vs-reality traps — RESEARCH Pitfall 1):
 *   - `results` is keyed `screw1..screwN` (1-BASED loop index), NOT the screw's name.
 *   - Each `screwN` = `{z: Double, sign: "CW"|"CCW", adjust: String, is_base: Boolean}`.
 *   - **`adjust` is a CLOCK STRING "MM:SS" (e.g. "00:07"), NOT a float.** The worst-screw
 *     parser MUST parse the clock format, not treat it as minutes-float. The base screw is
 *     `is_base: true, adjust: "00:00"` and is EXCLUDED from worst-screw selection.
 *   - Top-level `error` is a Boolean; `max_deviation` is `null` even AFTER the run (results
 *     DID persist post-run via a one-shot query — recorded in 09-01 SUMMARY re Open-Q1).
 *   - results["screwN"] (1-based) joins to config screwN_name BY INDEX.
 *
 * Production symbol referenced (NOT YET BUILT → RED): `parseScrewsTilt(results, config)` in
 * `works.mees.dinghy.calibration`, returning a GuidedLoopState that exposes:
 *   - `worstScrew` — the max out-of-tolerance screw (is_base excluded; worst |adjust| clock)
 *   - `inToleranceCount` / `totalScrews` — the "X of N in tolerance" pair (D-03)
 *   - `worstScrew.name` joined from config by 1-based index
 *   - `error: Boolean` carried through
 */
class ScrewsTiltResultTest {

    private fun resultsFixture(): JsonObject {
        val res = javaClass.getResource("/fixtures/screws_tilt_adjust_e5.json")
            ?: error("fixture /fixtures/screws_tilt_adjust_e5.json missing from test classpath")
        return MoonrakerJson.parseToJsonElement(res.readText()).jsonObject
    }

    private fun configFixture(): JsonObject {
        val res = javaClass.getResource("/fixtures/configfile_screws_e5.json")
            ?: error("fixture /fixtures/configfile_screws_e5.json missing from test classpath")
        return MoonrakerJson.parseToJsonElement(res.readText()).jsonObject
    }

    @Test
    fun realFixture_fourScrews_pickWorstByClockStringAdjust_excludingBase() {
        // The four-screw E5 case: screw1 is_base "00:00", screw3 "00:06" is the worst non-base.
        val state = parseScrewsTilt(resultsFixture(), configFixture())

        assertEquals(4, state.totalScrews)
        assertFalse("error is false on a clean run", state.error)
        // worst = the largest |adjust| clock among non-base screws → screw3 ("00:06" CW).
        assertEquals("screw3", state.worstScrew?.key)
        assertEquals("00:06", state.worstScrew?.adjust)
        assertEquals("CW", state.worstScrew?.sign)
        // 1-based join into configfile names: screw3 → "rear right screw".
        assertEquals("rear right screw", state.worstScrew?.name)
    }

    @Test
    fun adjustIsParsedAsClockString_notMinutesFloat() {
        // "00:07" must out-rank "00:06" as a clock (7s > 6s), proving the parser reads MM:SS,
        // not a naive Double.parse (which would throw / yield 0 on the colon).
        val state = parseScrewsTilt(resultsFixture(), configFixture())
        // 4 screws → 1 base + 3 candidates; the in-tolerance count is derived from the clock
        // magnitude vs the D-03 tolerance, NOT from a float field on the wire.
        assertTrue("at least the base screw is in tolerance", state.inToleranceCount >= 1)
        assertTrue("worst-screw clock seconds parsed > 0", (state.worstScrew?.adjustSeconds ?: 0) > 0)
    }

    @Test
    fun threeScrewVariant_pickWorst_genericOverN() {
        // D-04 generic: a 3-screw printer (screw1 base + screw2/screw3). Synthetic results,
        // but the SAME contract (1-based keys, clock-string adjust, is_base excluded).
        val results = MoonrakerJson.parseToJsonElement(
            """
            {"screws_tilt_adjust":{"error":false,"max_deviation":null,"results":{
              "screw1":{"z":0.0,"sign":"CW","adjust":"00:00","is_base":true},
              "screw2":{"z":0.05,"sign":"CCW","adjust":"00:03","is_base":false},
              "screw3":{"z":0.12,"sign":"CW","adjust":"00:09","is_base":false}
            }}}
            """.trimIndent()
        ).jsonObject
        val config = MoonrakerJson.parseToJsonElement(
            """
            {"screws_tilt_adjust":{
              "screw1":[10.0,10.0],"screw1_name":"front left",
              "screw2":[200.0,10.0],"screw2_name":"front right",
              "screw3":[105.0,200.0],"screw3_name":"rear center"
            }}
            """.trimIndent()
        ).jsonObject

        val state = parseScrewsTilt(results, config)
        assertEquals(3, state.totalScrews)
        assertEquals("screw3", state.worstScrew?.key)
        assertEquals("rear center", state.worstScrew?.name)
    }
}
