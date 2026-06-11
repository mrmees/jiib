package works.mees.dinghy.ui.temperature

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.HeaterState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

/**
 * Host-side proof for [TemperatureHolder]: the toolkit-agnostic transform turning the store's
 * ALREADY-throttled `printerState` (+ the one-shot `temperatureBackfill` StateFlow from 05-03) into
 * the Temperature panel's three consumable shapes — a per-sensor [legend] (current/target), the
 * N-trace [series] of ring snapshots, and the per-trace [setpoints] (current target, null when off).
 *
 * The holder owns NO throttle (the store conflates at 250ms) — these tests drive a real
 * [PrinterStateStore] seeded synchronously and assert the holder's exposed StateFlows under the
 * `runTest` virtual clock with an [UnconfinedTestDispatcher] so the holder's `collect` runs eagerly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TemperatureHolderTest {

    private fun heaters(vararg pairs: Pair<String, HeaterState>): ImmutableMap<String, HeaterState> =
        linkedMapOf(*pairs).toImmutableMap()

    @Test
    fun legendCarriesEachDrawnSensorAndSeriesHasOneTracePerSensor() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = TemperatureHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(hasBed = true, heaters = listOf("extruder", "heater_bed")))

        store.seed(
            PrinterState(
                heaters = heaters(
                    "extruder" to HeaterState(temperature = 200.0, target = 210.0),
                    "heater_bed" to HeaterState(temperature = 58.0, target = 60.0),
                ),
            ),
        )
        runCurrent()

        val legend = holder.legend.value
        assertEquals("two drawn sensors (nozzle + bed)", 2, legend.size)
        assertEquals("extruder", legend[0].name)
        assertEquals("NOZZLE", legend[0].label)
        assertEquals(200.0, legend[0].current, 0.001)
        assertEquals(210.0, legend[0].target!!, 0.001)
        assertEquals("heater_bed", legend[1].name)
        assertEquals("BED", legend[1].label)
        assertEquals(58.0, legend[1].current, 0.001)
        assertEquals(60.0, legend[1].target!!, 0.001)

        val series = holder.series.value
        assertEquals("one trace per drawn sensor", 2, series.size)
        assertEquals(200f, series[0].last(), 0.001f)
        assertEquals(58f, series[1].last(), 0.001f)
    }

    @Test
    fun backfillSeedsRingOldestFirstBeforeLivePushAndSeedsOnce() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = TemperatureHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(hasBed = false, heaters = listOf("extruder")))

        // Backfill lands first (the 05-03 one-shot StateFlow), oldest→newest.
        store.setTemperatureBackfill(mapOf("extruder" to floatArrayOf(20f, 21f, 22f)))
        runCurrent()

        // Then a live status diff pushes the current temp.
        store.seed(PrinterState(heaters = heaters("extruder" to HeaterState(temperature = 23.0))))
        runCurrent()

        val snap = holder.series.value[0]
        assertEquals("backfill seeds oldest-first then the live point appends", 4, snap.size)
        assertEquals(20f, snap[0], 0.001f)
        assertEquals(21f, snap[1], 0.001f)
        assertEquals(22f, snap[2], 0.001f)
        assertEquals(23f, snap[3], 0.001f)

        // A SECOND backfill emission must NOT re-prepend (seed once).
        store.setTemperatureBackfill(mapOf("extruder" to floatArrayOf(20f, 21f, 22f)))
        runCurrent()
        store.seed(PrinterState(heaters = heaters("extruder" to HeaterState(temperature = 24.0))))
        runCurrent()

        val snap2 = holder.series.value[0]
        // Original backfill (3) + two live pushes (23, 24) = 5; NOT re-seeded to 7+.
        assertEquals("seed happens once even if the backfill flow re-emits", 5, snap2.size)
        assertEquals(20f, snap2[0], 0.001f)
        assertEquals(24f, snap2.last(), 0.001f)
    }

    @Test
    fun targetZeroSurfacesAsNullSetpointAndNonZeroAtTraceIndex() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = TemperatureHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(hasBed = true, heaters = listOf("extruder", "heater_bed")))

        store.seed(
            PrinterState(
                heaters = heaters(
                    // nozzle off (target 0) → null setpoint; bed heating (target 60) → 60 at index 1.
                    "extruder" to HeaterState(temperature = 25.0, target = 0.0),
                    "heater_bed" to HeaterState(temperature = 58.0, target = 60.0),
                ),
            ),
        )
        runCurrent()

        val setpoints = holder.setpoints.value
        assertEquals(2, setpoints.size)
        assertNull("target 0 → null setpoint (off), mirrors PrintStatusHolder heaterCell rule", setpoints[0])
        assertEquals(60f, setpoints[1]!!, 0.001f)

        val legend = holder.legend.value
        assertNull("legend target null when off", legend[0].target)
        assertEquals(60.0, legend[1].target!!, 0.001)
    }

    @Test
    fun chamberIsThirdTraceAndExtraSensorsTruncated() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = TemperatureHolder(backgroundScope, store)
        // A rich printer: nozzle + bed + TWO generic heaters. Only the FIRST generic is drawn (3-trace cap).
        store.setCapabilities(
            Capabilities(
                hasBed = true,
                heaters = listOf("extruder", "heater_bed", "heater_generic chamber", "heater_generic exhaust"),
            ),
        )

        store.seed(
            PrinterState(
                heaters = heaters(
                    "extruder" to HeaterState(temperature = 200.0, target = 210.0),
                    "heater_bed" to HeaterState(temperature = 58.0, target = 60.0),
                    "heater_generic chamber" to HeaterState(temperature = 35.0, target = 40.0),
                    "heater_generic exhaust" to HeaterState(temperature = 30.0, target = 0.0),
                ),
            ),
        )
        runCurrent()

        val legend = holder.legend.value
        assertEquals("3-trace cap: nozzle/bed/chamber only", 3, legend.size)
        assertEquals("heater_generic chamber", legend[2].name)
        assertEquals("CHAMBER", legend[2].label)
        assertTrue("exhaust truncated", legend.none { it.name == "heater_generic exhaust" })
        assertEquals(3, holder.series.value.size)
        assertEquals(3, holder.setpoints.value.size)
    }

    @Test
    fun holderConsumesStoreFlowAndExposesStateFlows() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = TemperatureHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(hasBed = false, heaters = listOf("extruder")))

        store.seed(PrinterState(heaters = heaters("extruder" to HeaterState(temperature = 123.0))))
        runCurrent()
        val legend = holder.legend.first()
        assertEquals(123.0, legend[0].current, 0.001)
    }
}
