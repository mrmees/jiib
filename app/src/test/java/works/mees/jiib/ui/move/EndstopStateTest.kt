package works.mees.jiib.ui.move

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
    fun `real Moonraker stepper_ keys parse, order, and trigger correctly`() {
        // The shape the live server actually returns (verified 2026-06-18): keys are the owning
        // stepper, not bare axes.
        val result = parse("""{"stepper_z":"TRIGGERED","stepper_x":"open","stepper_y":"open"}""")
        assertEquals(
            listOf(
                EndstopStatus("stepper_x", triggered = false),
                EndstopStatus("stepper_y", triggered = false),
                EndstopStatus("stepper_z", triggered = true),
            ),
            result,
        )
    }

    @Test
    fun `multi-z stepper keys sort by axis then numeric suffix`() {
        val result = parse("""{"stepper_z1":"open","stepper_z":"open","stepper_x":"open"}""")
        assertEquals(listOf("stepper_x", "stepper_z", "stepper_z1"), result.map { it.name })
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
    fun `endstopLabel strips stepper_ prefix and formats the axis`() {
        // Real Moonraker keys.
        assertEquals("X", endstopLabel("stepper_x"))
        assertEquals("Y", endstopLabel("stepper_y"))
        assertEquals("Z", endstopLabel("stepper_z"))
        assertEquals("Z1", endstopLabel("stepper_z1"))
        // Bare axes still work (defensive).
        assertEquals("X", endstopLabel("x"))
        assertEquals("Z", endstopLabel("z"))
        // Non-stepper keys: capitalized word.
        assertEquals("Probe", endstopLabel("probe"))
        assertEquals("Manual_stepper", endstopLabel("manual_stepper"))
    }
}
