package works.mees.jiib.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BedMeshPreviewModelTest {
    private val base = BedMeshModel(
        profileName = "live",
        meshMatrix = listOf(listOf(9.0)),
        profiles = mapOf("cold" to BedMeshProfile(
            points = listOf(listOf(0.1, 0.2), listOf(0.3, 0.4)),
            minX = 10.0, maxX = 210.0, minY = 12.0, maxY = 208.0,
        )),
        profileNames = listOf("cold"),
    )

    @Test fun previewSetsBothMatricesToPoints() {
        val p = base.previewOf("cold")!!
        assertEquals("cold", p.profileName)
        assertEquals(p.meshMatrix, p.probedMatrix)
        assertEquals(listOf(listOf(0.1, 0.2), listOf(0.3, 0.4)), p.meshMatrix)
        assertEquals(MeshPoint(10.0, 12.0), p.meshMin)
        assertEquals(MeshPoint(210.0, 208.0), p.meshMax)
        assertFalse(p.isEmpty) // non-empty matrix + non-blank name
    }

    @Test fun previewOfUnknownIsNull() {
        assertNull(base.previewOf("nope"))
    }
}
