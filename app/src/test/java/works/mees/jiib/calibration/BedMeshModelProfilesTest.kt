package works.mees.jiib.calibration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BedMeshModelProfilesTest {
    private val raw = """
      {"bed_mesh":{"profile_name":"cold","profiles":{
        "cold":{"points":[[0.1,0.2],[0.3,0.4]],
                "mesh_params":{"min_x":10.0,"max_x":210.0,"min_y":12.0,"max_y":208.0}}}}}
    """.trimIndent()

    @Test fun fromParsesProfilePayloads() {
        val m = BedMeshModel.from(Json.parseToJsonElement(raw) as JsonObject)
        val p = m.profiles["cold"]!!
        assertEquals(listOf(listOf(0.1, 0.2), listOf(0.3, 0.4)), p.points)
        assertEquals(210.0, p.maxX, 0.0)
    }
}
