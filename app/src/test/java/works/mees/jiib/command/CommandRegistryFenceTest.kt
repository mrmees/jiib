package works.mees.jiib.command

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRegistryFenceTest {
    private fun scriptOf(spec: CommandSpec<Unit>): String =
        ((spec.params(Unit) as JsonObject)["script"]!!).jsonPrimitive.content

    @Test
    fun homeAll_isFenced_scriptEndsWithM400() {
        val script = scriptOf(CommandRegistry.homeAll)
        assertTrue("homeAll fence flag", CommandRegistry.homeAll.fence)
        assertTrue("homeAll script ends with M400, was: $script", script.endsWith("\nM400"))
    }

    @Test
    fun babystepZ_isNotFenced_noM400() {
        val spec = CommandRegistry.babystepZ
        assertFalse("babystep must not fence (runs during print)", spec.fence)
        val script = (spec.params(BabystepArgs(0.01)) as JsonObject)["script"]!!.jsonPrimitive.content
        assertFalse("babystep script must not contain M400, was: $script", script.contains("M400"))
    }

    @Test
    fun homeAll_gating_isHardLock() {
        assertEquals(GatingMode.HardLock, CommandRegistry.homeAll.gating)
    }
}
