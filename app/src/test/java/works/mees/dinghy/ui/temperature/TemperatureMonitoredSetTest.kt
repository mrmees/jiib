package works.mees.dinghy.ui.temperature

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.HeaterState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

/**
 * Host-side proof for the Task-6 monitored-set rework of [TemperatureHolder]: the drawn/monitored
 * set is now DYNAMIC = all heaters (always) ∪ the user-selected `temperature_sensor` objects, with
 * heaters-first-then-sensors ordering, each group alphabetical. Sensors are NOT adjustable and have
 * a `null` target; their live value comes from [PrinterState.temperatureSensors]. The old 3-trace
 * cap and the resolve-once guard are gone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TemperatureMonitoredSetTest {

    private fun heaters(vararg pairs: Pair<String, HeaterState>): ImmutableMap<String, HeaterState> =
        linkedMapOf(*pairs).toImmutableMap()

    @Test
    fun monitoredSetOrdersHeatersAlphaThenSelectedSensorsAlpha() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = TemperatureHolder(backgroundScope, store)
        // Caps heaters intentionally NOT alphabetical to prove the holder sorts them.
        store.setCapabilities(Capabilities(hasBed = true, heaters = listOf("heater_bed", "extruder")))

        store.seed(
            PrinterState(
                heaters = heaters(
                    "extruder" to HeaterState(temperature = 200.0, target = 210.0),
                    "heater_bed" to HeaterState(temperature = 58.0, target = 60.0),
                ),
                temperatureSensors = persistentMapOf(
                    "temperature_sensor mcu" to 44.0,
                    "temperature_sensor chamber" to 30.0,
                ),
            ),
        )
        runCurrent()

        // Select both sensors (sensor object names, full Moonraker form).
        holder.setSensorSelected("temperature_sensor mcu", true)
        holder.setSensorSelected("temperature_sensor chamber", true)
        runCurrent()

        val legend = holder.legend.value
        assertEquals(
            listOf("extruder", "heater_bed", "temperature_sensor chamber", "temperature_sensor mcu"),
            legend.map { it.name },
        )
    }

    @Test
    fun heatersAdjustableSensorsNotAndSensorCurrentFromSensorMap() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = TemperatureHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(hasBed = true, heaters = listOf("extruder", "heater_bed")))

        store.seed(
            PrinterState(
                heaters = heaters(
                    "extruder" to HeaterState(temperature = 200.0, target = 210.0),
                    "heater_bed" to HeaterState(temperature = 58.0, target = 60.0),
                ),
                temperatureSensors = persistentMapOf("temperature_sensor chamber" to 30.5),
            ),
        )
        runCurrent()
        holder.setSensorSelected("temperature_sensor chamber", true)
        runCurrent()

        val legend = holder.legend.value
        val nozzle = legend.first { it.name == "extruder" }
        val chamber = legend.first { it.name == "temperature_sensor chamber" }

        assertTrue("heater is adjustable", nozzle.isAdjustable)
        assertFalse("sensor is not adjustable", chamber.isAdjustable)
        assertNull("sensor has no target", chamber.target)
        assertEquals("sensor current read from temperatureSensors", 30.5, chamber.current, 0.001)
        assertEquals("CHAMBER", chamber.label)

        // setpoints: sensor index is null even though it is in the monitored set.
        val setpoints = holder.setpoints.value
        val chamberIdx = legend.indexOfFirst { it.name == "temperature_sensor chamber" }
        assertNull("sensor setpoint is null", setpoints[chamberIdx])
    }

    @Test
    fun deselectingSensorDropsItFromLegend() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = TemperatureHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(hasBed = false, heaters = listOf("extruder")))

        store.seed(
            PrinterState(
                heaters = heaters("extruder" to HeaterState(temperature = 200.0, target = 210.0)),
                temperatureSensors = persistentMapOf("temperature_sensor chamber" to 30.0),
            ),
        )
        runCurrent()
        holder.setSensorSelected("temperature_sensor chamber", true)
        runCurrent()
        assertTrue(
            "sensor present after select",
            holder.legend.value.any { it.name == "temperature_sensor chamber" },
        )

        holder.setSensorSelected("temperature_sensor chamber", false)
        runCurrent()
        assertFalse(
            "sensor dropped after deselect",
            holder.legend.value.any { it.name == "temperature_sensor chamber" },
        )
        assertEquals("only the heater remains", listOf("extruder"), holder.legend.value.map { it.name })
    }
}
