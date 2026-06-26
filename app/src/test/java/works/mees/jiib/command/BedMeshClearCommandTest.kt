package works.mees.jiib.command

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BedMeshClearCommandTest {
    @Test fun clearConstantIsBareGcode() {
        assertEquals("BED_MESH_CLEAR", PrinterCommands.BED_MESH_CLEAR)
    }

    @Test fun clearSpecEmitsClearGcode() {
        val spec = CommandRegistry.bedMeshClear
        assertEquals("bed_mesh_clear", spec.dispatchKey(Unit))
        assertEquals(
            "BED_MESH_CLEAR",
            spec.params(Unit)!!.jsonObject["script"]!!.jsonPrimitive.content,
        )
    }

    @Test fun defaultProfileNameIsRejectedBySaveGate() {
        // isValidProfileName blocks creating a new "default" profile (Klipper rejects SAVE=default).
        assertFalse(PrinterCommands.isValidProfileName("default"))
        assertTrue(PrinterCommands.isValidProfileName("bed_cold"))
    }

    @Test fun renameEmitsSaveNewThenRemoveOldAsOneScript() {
        val spec = CommandRegistry.bedMeshProfileRename
        val gcode = spec.params(BedMeshRenameArgs(old = "cold", new = "cool"))!!
            .jsonObject["script"]!!.jsonPrimitive.content
        // ONE script, newline-joined -> Klipper executes SAVE before REMOVE (ordering guaranteed).
        assertEquals("BED_MESH_PROFILE SAVE=cool\nBED_MESH_PROFILE REMOVE=cold", gcode)
    }
}
