package works.mees.jiib.state

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.jiib.net.MoonrakerJson

/**
 * Task 6: probe.last_query (int 0/1 → Boolean) + probe.last_z_result (Double) reducer walk.
 *
 * Hardware-verified probe shape (E5+ + E3):
 *   "probe": { "name": "probe", "last_query": 1, "last_probe_position": [0.0, 0.0, 0.0, 0], "last_z_result": 0.0 }
 *
 * last_query is an INTEGER on the wire (not a JSON boolean). Tolerate a boolean too.
 * Absent/garbage probe object → both fields null (null-safe walk).
 */
class PrinterStateReducerProbeTest {

    private fun diff(json: String) = MoonrakerJson.parseToJsonElement(json).jsonObject

    @Test
    fun probeLastQueryDefaultsToNull() {
        assertNull(PrinterState().probeLastQuery)
    }

    @Test
    fun probeLastZDefaultsToNull() {
        assertNull(PrinterState().probeLastZ)
    }

    @Test
    fun lastQueryInt1MapsToTrueAndLastZPopulates() {
        // Hardware-exact shape: last_query=1 (int), last_z_result=1.5 (float).
        val s = reduceDiff(
            PrinterState(),
            diff("""{ "probe": { "name": "probe", "last_query": 1, "last_probe_position": [0.0, 0.0, 0.0, 0], "last_z_result": 1.5 } }"""),
        )
        assertEquals(true, s.probeLastQuery)
        assertEquals(1.5, s.probeLastZ!!, 1e-9)
    }

    @Test
    fun lastQueryInt0MapsToFalse() {
        val s = reduceDiff(
            PrinterState(),
            diff("""{ "probe": { "last_query": 0, "last_z_result": 0.0 } }"""),
        )
        assertEquals(false, s.probeLastQuery)
    }

    @Test
    fun lastQueryBooleanTrueToleratedForRobustness() {
        // Not the real wire format, but the reducer should not crash on a boolean either.
        val s = reduceDiff(
            PrinterState(),
            diff("""{ "probe": { "last_query": true, "last_z_result": 2.3 } }"""),
        )
        assertEquals(true, s.probeLastQuery)
        assertEquals(2.3, s.probeLastZ!!, 1e-9)
    }

    @Test
    fun absentProbeObjectRetainsPriorNulls() {
        // No probe key in the diff → both fields stay null (retain-on-absent).
        val s = reduceDiff(
            PrinterState(),
            diff("""{ "heater_bed": { "temperature": 40.0 } }"""),
        )
        assertNull(s.probeLastQuery)
        assertNull(s.probeLastZ)
    }

    @Test
    fun garbageProbeObjectLeavesFieldsNull() {
        // A probe key with a non-object value is a type mismatch → both fields null (never crash).
        val s = reduceDiff(
            PrinterState(),
            diff("""{ "probe": "not_an_object" }"""),
        )
        assertNull(s.probeLastQuery)
        assertNull(s.probeLastZ)
    }

    @Test
    fun partialDiffWithOnlyLastZRetainsLastQuery() {
        // Start with a known-good state where probeLastQuery is true.
        val seeded = reduceDiff(
            PrinterState(),
            diff("""{ "probe": { "last_query": 1, "last_z_result": 1.0 } }"""),
        )
        // A subsequent diff carries only last_z_result — last_query must be RETAINED (merge, not replace).
        val after = reduceDiff(
            seeded,
            diff("""{ "probe": { "last_z_result": 2.0 } }"""),
        )
        assertEquals(true, after.probeLastQuery)
        assertEquals(2.0, after.probeLastZ!!, 1e-9)
    }
}
