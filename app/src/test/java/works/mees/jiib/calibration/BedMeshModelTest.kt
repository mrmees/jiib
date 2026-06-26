// RED scaffold (Wave 0) — turns GREEN in 09-05 (BedMeshModel.from).
package works.mees.jiib.calibration

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import works.mees.jiib.net.MoonrakerJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave-0 RED scaffold (09-01) — turned GREEN by 09-05 (`BedMeshModel.from`).
 *
 * REQ-CALIB-04. Walks the REAL probed `bed_mesh` shape committed as
 * `/fixtures/bed_mesh_e5.json` (probed 2026-06-02 on the live Ender 5 Plus — a loaded
 * KAMP adaptive profile).
 *
 * REAL-SHAPE CONTRACT this test pins (the mock-vs-reality traps — RESEARCH Pitfall 4/6):
 *   - **`mesh_min` / `mesh_max` are JSON ARRAYS `[x, y]`** (Python tuple → JSON array),
 *     NOT objects. A `.jsonObject["x"]` walk would throw — the model reads them as `[x,y]`.
 *   - `probed_matrix` is the raw dot grid (here 3×4); `mesh_matrix` is the wider interpolated
 *     grid (here 7×10) — both arrays-of-arrays of Doubles.
 *   - `profiles` is a DICT keyed by saved-profile NAME (here 'pre','post','default') — the
 *     profile name list comes from `profiles` KEYS, not an array.
 *   - empty-state is derived from `mesh_matrix` empty / `profile_name == ""`, SEPARATE from
 *     `profiles` being non-empty (a printer can have saved profiles but no LOADED mesh).
 *
 * Production symbol referenced (NOT YET BUILT → RED): `BedMeshModel.from(bedMesh)` in
 * `works.mees.jiib.calibration`, exposing `meshMatrix`, `probedMatrix`, `meshMin`/`meshMax`
 * as `[x,y]` Double pairs, `profileNames` (from `profiles` keys), and `isEmpty`.
 */
class BedMeshModelTest {

    private fun bedMeshFixture(): JsonObject {
        val res = javaClass.getResource("/fixtures/bed_mesh_e5.json")
            ?: error("fixture /fixtures/bed_mesh_e5.json missing from test classpath")
        return MoonrakerJson.parseToJsonElement(res.readText()).jsonObject
    }

    @Test
    fun realFixture_parsesMatricesAndMinMaxAsArrays() {
        val model = BedMeshModel.from(bedMeshFixture())

        // probed_matrix = 3 rows × 4 cols; mesh_matrix = 7 rows × 10 cols (interpolated wider).
        assertEquals(3, model.probedMatrix.size)
        assertEquals(4, model.probedMatrix.first().size)
        assertEquals(7, model.meshMatrix.size)
        assertEquals(10, model.meshMatrix.first().size)

        // mesh_min / mesh_max are [x, y] arrays — NOT objects (the load-bearing Pitfall-6 assertion).
        assertEquals(85.52119999999996, model.meshMin.x, 1e-6)
        assertEquals(148.79, model.meshMin.y, 1e-6)
        assertEquals(294.59119999999996, model.meshMax.x, 1e-6)
        assertEquals(237.48999999999998, model.meshMax.y, 1e-6)
    }

    @Test
    fun realFixture_profileNamesFromProfilesKeys() {
        val model = BedMeshModel.from(bedMeshFixture())
        // The saved-profile list is the KEYS of `profiles`, not an array.
        assertEquals(setOf("pre", "post", "default"), model.profileNames.toSet())
        // A loaded mesh → NOT empty even though `profiles` is also populated (the SEPARATE check).
        assertFalse(model.isEmpty)
        assertEquals("adaptive-7FA0AB1C50", model.profileName)
    }

    @Test
    fun emptyMesh_isEmpty_separateFromSavedProfiles() {
        // profile_name "" + empty mesh_matrix → empty, EVEN IF profiles carries saved entries.
        val empty = MoonrakerJson.parseToJsonElement(
            """
            {"bed_mesh":{"profile_name":"","mesh_min":[0.0,0.0],"mesh_max":[0.0,0.0],
              "probed_matrix":[],"mesh_matrix":[],
              "profiles":{"default":{"points":[[0.0]],"mesh_params":{}}}}}
            """.trimIndent()
        ).jsonObject
        val model = BedMeshModel.from(empty)
        assertTrue("empty mesh_matrix + blank profile_name → isEmpty", model.isEmpty)
        // ...yet the saved-profile list still reflects `profiles` keys.
        assertTrue("saved profiles still listed", model.profileNames.contains("default"))
    }

    @Test
    fun meshSpan_isMaxMinusMin_andNullWhenEmpty() {
        // max 0.40, min -0.05 → span 0.45
        assertEquals(0.45, meshSpan(listOf(listOf(0.10, 0.40), listOf(-0.05, 0.25)))!!, 1e-9)
        // flat mesh → 0.0 span
        assertEquals(0.0, meshSpan(listOf(listOf(0.2, 0.2)))!!, 1e-9)
        // empty / empty-rows → null (no span to show)
        org.junit.Assert.assertNull("empty → null", meshSpan(emptyList()))
        org.junit.Assert.assertNull("empty rows → null", meshSpan(listOf(emptyList())))
    }
}
