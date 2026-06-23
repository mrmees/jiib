package works.mees.dinghy.state

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BedMeshProfilePayloadReducerTest {
    private fun status(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    // A SAVE-style delta carries the profiles dict with points + mesh_params.
    private val saveDelta = """
      {"bed_mesh":{"profile_name":"cold","profiles":{
        "cold":{"points":[[0.1,0.2],[0.3,0.4]],
                "mesh_params":{"min_x":10.0,"max_x":210.0,"min_y":12.0,"max_y":208.0,
                               "x_count":2,"y_count":2,"algo":"bicubic","tension":0.2}}}}}
    """.trimIndent()

    // A LOAD-style delta carries matrices but NO profiles key — must retain the prior payload.
    private val loadDelta = """
      {"bed_mesh":{"profile_name":"cold","mesh_matrix":[[0.1,0.2],[0.3,0.4]]}}
    """.trimIndent()

    @Test fun parsesProfilePayload() {
        val s = reduceDiff(PrinterState(), status(saveDelta))
        val p = s.bedMesh!!.profiles["cold"]!!
        assertEquals(listOf(listOf(0.1, 0.2), listOf(0.3, 0.4)), p.points.map { it.toList() })
        assertEquals(10.0, p.minX, 0.0)
        assertEquals(208.0, p.maxY, 0.0)
    }

    @Test fun retainsPayloadAcrossLoadDeltaThatOmitsProfiles() {
        val afterSave = reduceDiff(PrinterState(), status(saveDelta))
        val afterLoad = reduceDiff(afterSave, status(loadDelta))
        // profiles omitted in the load delta -> retained, not wiped.
        assertEquals(setOf("cold"), afterLoad.bedMesh!!.profiles.keys)
        assertEquals(2, afterLoad.bedMesh!!.profiles["cold"]!!.points.size)
    }
}
