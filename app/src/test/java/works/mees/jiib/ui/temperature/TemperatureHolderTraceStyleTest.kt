package works.mees.jiib.ui.temperature

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.state.PrinterStateStore

/**
 * Host-side proof for the D-14 trace color + visibility extension to [TemperatureHolder] (Plan 26-03).
 *
 * Verifies:
 * 1. [TemperatureHolder.setTraceColor] → [TemperatureHolder.traceColors] state transition.
 * 2. [TemperatureHolder.setTraceVisibility] → [TemperatureHolder.traceVisibility] state transition.
 * 3. Absent-means-visible convention: a sensor not in the visibility map defaults to true.
 * 4. Multiple sensors can have independent colors/visibility without interference.
 *
 * These tests drive the in-memory stubs added in Task 1; the durable-persistence proof (Task 2)
 * seeding from [works.mees.jiib.ui.settings.TraceStylePrefs] is covered by the wiring test in
 * the Task 2 acceptance criteria (source assertions + existing test still green).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TemperatureHolderTraceStyleTest {

    private fun makeHolder() = TemperatureHolder(
        scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher()),
        store = PrinterStateStore(kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher())),
    )

    @Test
    fun setTraceColor_updatesTraceColorsStateFlow() = runTest(UnconfinedTestDispatcher()) {
        val holder = makeHolder()
        val red = Color.Red

        // Initially empty map
        assertTrue("traceColors initially empty", holder.traceColors.value.isEmpty())

        holder.setTraceColor("extruder", red)

        assertEquals("color is set for extruder", red, holder.traceColors.value["extruder"])
    }

    @Test
    fun setTraceColor_multiplesensors_storeIndependently() = runTest(UnconfinedTestDispatcher()) {
        val holder = makeHolder()
        val red = Color.Red
        val blue = Color.Blue

        holder.setTraceColor("extruder", red)
        holder.setTraceColor("heater_bed", blue)

        assertEquals("extruder color", red, holder.traceColors.value["extruder"])
        assertEquals("heater_bed color", blue, holder.traceColors.value["heater_bed"])
    }

    @Test
    fun setTraceColor_overwritesPreviousColor() = runTest(UnconfinedTestDispatcher()) {
        val holder = makeHolder()
        holder.setTraceColor("extruder", Color.Red)
        holder.setTraceColor("extruder", Color.Green)

        assertEquals("color replaced", Color.Green, holder.traceColors.value["extruder"])
        assertEquals("only one entry for the sensor", 1, holder.traceColors.value.size)
    }

    @Test
    fun setTraceVisibility_false_updateStateFlow() = runTest(UnconfinedTestDispatcher()) {
        val holder = makeHolder()

        holder.setTraceVisibility("extruder", false)

        assertFalse("extruder hidden", holder.traceVisibility.value["extruder"] ?: true)
    }

    @Test
    fun setTraceVisibility_true_explicitlyShowsSensor() = runTest(UnconfinedTestDispatcher()) {
        val holder = makeHolder()

        holder.setTraceVisibility("extruder", false)
        holder.setTraceVisibility("extruder", true)

        assertTrue("extruder visible after re-show", holder.traceVisibility.value["extruder"] == true)
    }

    @Test
    fun absentMeansVisible_defaultVisibilityForUnseenSensor() = runTest(UnconfinedTestDispatcher()) {
        val holder = makeHolder()

        // No setTraceVisibility call — absent entry must mean visible
        val visibility = holder.traceVisibility.value["extruder"]
        assertNull("absent entry is null", visibility)
        // Callers treat null as visible: null ?: true == true
        assertTrue("absent-means-visible convention", visibility ?: true)
    }

    @Test
    fun isAdjustable_isTrueOnSensorReadout() = runTest(UnconfinedTestDispatcher()) {
        // SensorReadout default value verification (D-11: all v1 heaters are adjustable)
        val readout = SensorReadout(
            name = "extruder",
            label = "NOZZLE",
            current = 200.0,
            target = 210.0,
        )
        assertTrue("default isAdjustable = true", readout.isAdjustable)
    }
}
