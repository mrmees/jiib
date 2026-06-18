package works.mees.dinghy.ui.move

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class EndstopStateTest {

    private fun parse(json: String) = parseEndstops(Json.parseToJsonElement(json))

    @Test
    fun `triggered is case-insensitive, everything else is open`() {
        val result = parse("""{"x":"open","y":"TRIGGERED","z":"triggered"}""")
        assertEquals(
            listOf(
                EndstopStatus("x", triggered = false),
                EndstopStatus("y", triggered = true),
                EndstopStatus("z", triggered = true),
            ),
            result,
        )
    }

    @Test
    fun `canonical x,y,z order first, then remaining keys alphabetically`() {
        val result = parse("""{"probe":"open","z":"open","x":"open","y":"open"}""")
        assertEquals(listOf("x", "y", "z", "probe"), result.map { it.name })
    }

    @Test
    fun `empty object yields empty list`() {
        assertEquals(emptyList<EndstopStatus>(), parse("{}"))
    }

    @Test
    fun `non-object element yields empty list`() {
        assertEquals(emptyList<EndstopStatus>(), parse(""""nope""""))
    }

    @Test
    fun `endstopLabel title-cases known axes and capitalizes others`() {
        assertEquals("X", endstopLabel("x"))
        assertEquals("Y", endstopLabel("y"))
        assertEquals("Z", endstopLabel("z"))
        assertEquals("Probe", endstopLabel("probe"))
        assertEquals("Manual_stepper", endstopLabel("manual_stepper"))
    }
}
