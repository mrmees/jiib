package works.mees.dinghy.state

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PrinterStateReducerAxisLimitsTest {
    private fun status(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun toolhead_axisLimits_areReducedIntoState() {
        val s = applyStatus(
            PrinterState(),
            status("""{"toolhead":{"axis_minimum":[-5.0,0.0,0.0,0.0],"axis_maximum":[355.0,355.0,340.0,0.0]}}"""),
        )
        assertEquals(listOf(-5.0, 0.0, 0.0, 0.0), s.axisMinimum)
        assertEquals(listOf(355.0, 355.0, 340.0, 0.0), s.axisMaximum)
    }

    @Test
    fun partialToolheadDiff_retainsExistingAxisLimits() {
        val seeded = applyStatus(
            PrinterState(),
            status("""{"toolhead":{"axis_maximum":[355.0,355.0,340.0,0.0]}}"""),
        )
        val after = applyStatus(seeded, status("""{"toolhead":{"homed_axes":"xyz"}}"""))
        assertEquals(listOf(355.0, 355.0, 340.0, 0.0), after.axisMaximum)
    }
}
