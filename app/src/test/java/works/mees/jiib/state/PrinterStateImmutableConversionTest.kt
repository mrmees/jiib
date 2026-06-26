package works.mees.jiib.state

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.net.MoonrakerJson

/**
 * D-02 (Phase 22): targeted tests for the four conversion-sensitive reducer paths, asserting both
 * VALUES (behavior preserved) and TYPES (the fields are ImmutableList/ImmutableMap instances).
 *
 * These tests close the explicit must-have in 22-02 PLAN.md: every path touched by the
 * .toImmutableList()/.toImmutableMap()/.toImmutable2d() assignment-boundary conversions must be
 * proven to (a) still deliver the correct value, (b) emit the correct immutable type, and (c)
 * honour the absent-vs-empty distinction the reducer has always guaranteed.
 */
class PrinterStateImmutableConversionTest {

    private fun diff(json: String): JsonObject =
        MoonrakerJson.parseToJsonElement(json).let { it as JsonObject }

    // -------------------------------------------------------------------------
    // 1. Bed mesh matrices (probedMatrix / meshMatrix)
    // -------------------------------------------------------------------------

    @Test
    fun bedMeshProbedMatrixIsImmutable2d() {
        val state = reduceDiff(
            PrinterState(),
            diff(
                """{"bed_mesh":{"profile_name":"default",
                   "mesh_min":[10.0,20.0],"mesh_max":[290.0,280.0],
                   "probed_matrix":[[0.01,0.02],[0.03,0.04]],
                   "mesh_matrix":[[0.01,0.015],[0.025,0.04]]}}""",
            ),
        )
        val bm = state.bedMesh!!

        // Type assertion — field is the declared ImmutableList<ImmutableList<Double>>
        assertTrue(
            "probedMatrix is ImmutableList",
            bm.probedMatrix is ImmutableList<*>,
        )
        assertTrue(
            "probedMatrix[0] is ImmutableList",
            bm.probedMatrix!![0] is ImmutableList<*>,
        )
        // Value assertion
        assertEquals(2, bm.probedMatrix!!.size)
        assertEquals(0.01, bm.probedMatrix!![0][0], 0.0001)
        assertEquals(0.04, bm.probedMatrix!![1][1], 0.0001)

        assertTrue("meshMatrix is ImmutableList", bm.meshMatrix is ImmutableList<*>)
        assertEquals(2, bm.meshMatrix!!.size)
        assertEquals(0.01, bm.meshMatrix!![0][0], 0.0001)
        assertEquals(0.04, bm.meshMatrix!![1][1], 0.0001)

        // meshMin / meshMax also ImmutableList
        assertTrue("meshMin is ImmutableList", bm.meshMin is ImmutableList<*>)
        assertEquals(10.0, bm.meshMin!![0], 0.0001)
        assertTrue("meshMax is ImmutableList", bm.meshMax is ImmutableList<*>)
        assertEquals(280.0, bm.meshMax!![1], 0.0001)
    }

    @Test
    fun bedMeshAbsentMatrixRetainsPrior_immutableTypePreserved() {
        // Seed a mesh, then apply a partial diff that omits the matrices — retain-and-keep.
        val seeded = reduceDiff(
            PrinterState(),
            diff(
                """{"bed_mesh":{"profile_name":"default",
                   "probed_matrix":[[0.01,0.02],[0.03,0.04]],
                   "mesh_matrix":[[0.01,0.015],[0.025,0.04]],
                   "profiles":{"default":{}}}}""",
            ),
        )
        assertNotNull(seeded.bedMesh?.probedMatrix)

        val afterSave = reduceDiff(
            seeded,
            diff("""{"bed_mesh":{"profiles":{"default":{},"new_profile":{}}}}"""),
        )
        // probedMatrix was OMITTED from the save diff — must RETAIN
        assertTrue(
            "retained probedMatrix is still ImmutableList",
            afterSave.bedMesh!!.probedMatrix is ImmutableList<*>,
        )
        assertEquals("retained value matches prior", seeded.bedMesh!!.probedMatrix, afterSave.bedMesh!!.probedMatrix)
        // profileNames was updated and is ImmutableList
        assertTrue("profileNames is ImmutableList", afterSave.bedMesh!!.profileNames is ImmutableList<*>)
        assertTrue("new_profile appears", "new_profile" in afterSave.bedMesh!!.profileNames)
    }

    @Test
    fun bedMeshExplicitEmptyMatrixHonoredAsRealClear() {
        // BED_MESH_CLEAR sends mesh_matrix [] — PRESENT-but-empty is a real clear (not absent→retain).
        val seeded = reduceDiff(
            PrinterState(),
            diff("""{"bed_mesh":{"profile_name":"default","mesh_matrix":[[0.1,0.2]]}}"""),
        )
        val cleared = reduceDiff(
            seeded,
            diff("""{"bed_mesh":{"profile_name":"","mesh_matrix":[]}}"""),
        )
        assertTrue("cleared meshMatrix is ImmutableList", cleared.bedMesh!!.meshMatrix is ImmutableList<*>)
        assertTrue("empty list is honored as a real clear", cleared.bedMesh!!.meshMatrix!!.isEmpty())
    }

    @Test
    fun bedMeshNullArrayKeyYieldsNullField() {
        // A diff where mesh_matrix key is absent → doubleListOrNull returns null → field stays null/retained.
        val state = reduceDiff(
            PrinterState(),
            diff("""{"bed_mesh":{"profile_name":"default"}}"""),
        )
        assertNull("absent mesh_matrix → null (retain default)", state.bedMesh?.meshMatrix)
    }

    // -------------------------------------------------------------------------
    // 2. Screws-tilt results map (ScrewsTiltObject.results: ImmutableMap)
    // -------------------------------------------------------------------------

    @Test
    fun screwsTiltResultsIsImmutableMap() {
        val state = reduceDiff(
            PrinterState(),
            diff(
                """{"screws_tilt_adjust":{
                   "error":false,
                   "results":{
                     "screw1":{"z":0.129,"sign":"CW","adjust":"00:00","is_base":true},
                     "screw2":{"z":0.047,"sign":"CCW","adjust":"00:07","is_base":false}
                   }}}""",
            ),
        )
        val st = state.screwsTilt!!
        assertTrue("results is ImmutableMap", st.results is ImmutableMap<*, *>)
        assertEquals("two results", 2, st.results.size)
        assertTrue("screw1 present", "screw1" in st.results)
        assertEquals("CCW", st.results["screw2"]!!.sign)
        assertEquals("00:07", st.results["screw2"]!!.adjust)
        assertTrue("screw1 is base", st.results["screw1"]!!.isBase)
    }

    @Test
    fun screwsTiltEmptyResultsYieldsEmptyImmutableMap() {
        // A screws_tilt_adjust diff without a results block → results is the default (empty ImmutableMap).
        val state = reduceDiff(
            PrinterState(),
            diff("""{"screws_tilt_adjust":{"error":false}}"""),
        )
        assertTrue("results is ImmutableMap", state.screwsTilt!!.results is ImmutableMap<*, *>)
        assertTrue("results is empty", state.screwsTilt!!.results.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 3. Outputs colorData (ImmutableList<ImmutableList<Double>>)
    // -------------------------------------------------------------------------

    @Test
    fun outputsColorDataIsImmutable2d() {
        val state = reduceDiff(
            PrinterState(),
            diff(
                """{"led chamber_light":{"color_data":[[0.9,0.3,0.0,0.0],[0.5,0.1,0.0,0.0]]}}""",
            ),
        )
        val cd = state.outputs["led chamber_light"]!!.colorData!!
        assertTrue("colorData outer is ImmutableList", cd is ImmutableList<*>)
        assertTrue("colorData inner rows are ImmutableList", cd[0] is ImmutableList<*>)
        assertEquals(2, cd.size)
        assertEquals(0.9, cd[0][0], 0.0001)
        assertEquals(0.5, cd[1][0], 0.0001)
    }

    @Test
    fun outputsColorDataAbsentRetainsPrior_typePreserved() {
        // Update-on-present: a diff without color_data retains the prior value unchanged.
        val seeded = reduceDiff(
            PrinterState(),
            diff("""{"led chamber_light":{"color_data":[[0.9,0.3,0.0,0.0]]}}"""),
        )
        val merged = reduceDiff(
            seeded,
            diff("""{"led chamber_light":{}}"""),
        )
        val cd = merged.outputs["led chamber_light"]!!.colorData!!
        assertTrue("retained colorData is still ImmutableList", cd is ImmutableList<*>)
        assertEquals(0.9, cd[0][0], 0.0001)
    }

    // -------------------------------------------------------------------------
    // 4. Toolhead / gcode_move position lists (ImmutableList<Double>)
    // -------------------------------------------------------------------------

    @Test
    fun toolheadPositionIsImmutableList() {
        val state = reduceDiff(
            PrinterState(),
            diff("""{"toolhead":{"position":[110.0,120.0,5.0,0.0]}}"""),
        )
        assertTrue("toolheadPosition is ImmutableList", state.toolheadPosition is ImmutableList<*>)
        assertEquals(4, state.toolheadPosition!!.size)
        assertEquals(110.0, state.toolheadPosition!![0], 0.0001)
    }

    @Test
    fun gcodePositionIsImmutableList() {
        val state = reduceDiff(
            PrinterState(),
            diff("""{"gcode_move":{"gcode_position":[12.5,40.0,3.2,0.0]}}"""),
        )
        assertTrue("gcodePosition is ImmutableList", state.gcodePosition is ImmutableList<*>)
        assertEquals(12.5, state.gcodePosition!![0], 0.0001)
        assertEquals(40.0, state.gcodePosition!![1], 0.0001)
    }

    @Test
    fun partialDiffOmittingPositionRetainsPrior_typePreserved() {
        // A partial toolhead diff without a position key retains the prior ImmutableList.
        val seeded = reduceDiff(
            PrinterState(),
            diff("""{"toolhead":{"position":[110.0,120.0,5.0,0.0]}}"""),
        )
        val afterOmission = reduceDiff(
            seeded,
            diff("""{"toolhead":{"max_velocity":300.0}}"""),
        )
        assertTrue(
            "retained toolheadPosition is still ImmutableList",
            afterOmission.toolheadPosition is ImmutableList<*>,
        )
        assertEquals(110.0, afterOmission.toolheadPosition!![0], 0.0001)
        assertEquals(300.0, afterOmission.maxVelocity!!, 0.0001)
    }

    @Test
    fun nullPositionKeyRetainsNullField() {
        // A fresh PrinterState has null positions; a non-position diff must not fabricate a list.
        val state = reduceDiff(
            PrinterState(),
            diff("""{"extruder":{"temperature":200.0}}"""),
        )
        assertNull("toolheadPosition remains null when never reported", state.toolheadPosition)
        assertNull("gcodePosition remains null when never reported", state.gcodePosition)
    }

    // -------------------------------------------------------------------------
    // 5. Heater merge map (heaters: ImmutableMap)
    // -------------------------------------------------------------------------

    @Test
    fun heaterMergeProducesImmutableMap() {
        val seeded = reduceDiff(
            PrinterState(),
            diff("""{"extruder":{"temperature":200.0,"target":210.0}}"""),
        )
        assertTrue("heaters is ImmutableMap after seed", seeded.heaters is ImmutableMap<*, *>)
        assertEquals(200.0, seeded.heaters["extruder"]!!.temperature, 0.0001)

        val merged = reduceDiff(
            seeded,
            diff("""{"heater_bed":{"temperature":55.0,"target":60.0}}"""),
        )
        // The merge (s.heaters + heaterUpdates).toImmutableMap() must retain both heaters.
        assertTrue("heaters is ImmutableMap after merge", merged.heaters is ImmutableMap<*, *>)
        assertEquals(2, merged.heaters.size)
        assertEquals(200.0, merged.heaters["extruder"]!!.temperature, 0.0001)
        assertEquals(55.0, merged.heaters["heater_bed"]!!.temperature, 0.0001)
    }

    @Test
    fun heaterUpdateRetainedAndUpdatedInImmutableMap() {
        // Seed extruder + bed; then a temp-only extruder diff must UPDATE extruder while RETAINING bed.
        val seeded = reduceDiff(
            PrinterState(),
            diff(
                """{"extruder":{"temperature":200.0,"target":210.0},
                   "heater_bed":{"temperature":55.0,"target":60.0}}""",
            ),
        )
        val updated = reduceDiff(
            seeded,
            diff("""{"extruder":{"temperature":205.0}}"""),
        )
        assertTrue("heaters is ImmutableMap after update", updated.heaters is ImmutableMap<*, *>)
        assertEquals("extruder temp updated", 205.0, updated.heaters["extruder"]!!.temperature, 0.0001)
        assertEquals("extruder target retained (Pitfall 1)", 210.0, updated.heaters["extruder"]!!.target, 0.0001)
        assertEquals("bed retained untouched", 55.0, updated.heaters["heater_bed"]!!.temperature, 0.0001)
    }
}
