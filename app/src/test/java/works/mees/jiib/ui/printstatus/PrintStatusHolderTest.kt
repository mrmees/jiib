package works.mees.jiib.ui.printstatus

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.render.RingBuffer
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import works.mees.jiib.state.Capabilities
import works.mees.jiib.state.HeaterState
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.state.PrinterStateStore

/**
 * Host-side proof for [PrintStatusHolder]: the toolkit-agnostic transform turning the store's
 * ALREADY-throttled `printerState` into (a) a bounded primary-heater ring snapshot for the 04-06b
 * sparkline and (b) a 2×3 numeric grid model with EXPLICIT capability fallback (review #9).
 *
 * The holder owns NO throttle (the store conflates at 250ms) — these tests drive a real
 * [PrinterStateStore] seeded synchronously and assert the holder's exposed StateFlows under the
 * `runTest` virtual clock with an [UnconfinedTestDispatcher] so the holder's `collect` runs eagerly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrintStatusHolderTest {

    private fun heaters(vararg pairs: Pair<String, HeaterState>): ImmutableMap<String, HeaterState> =
        linkedMapOf(*pairs).toImmutableMap()

    @Test
    fun primarySnapshotReflectsLatestExtruderTemp() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = PrintStatusHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(hasBed = true, heaters = listOf("extruder", "heater_bed")))

        store.seed(
            PrinterState(heaters = heaters("extruder" to HeaterState(temperature = 200.0, target = 210.0))),
        )
        runCurrent()

        val snap = holder.sparkline.value
        assertTrue("ring should have at least one sample", snap.isNotEmpty())
        assertEquals(200f, snap.last(), 0.001f)
    }

    @Test
    fun ringStaysBoundedPastCapacity() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = PrintStatusHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(hasBed = false, heaters = listOf("extruder")))

        // Feed more than RingBuffer.DEFAULT_CAPACITY distinct states; each seed publishes immediately.
        val n = RingBuffer.DEFAULT_CAPACITY + 50
        for (i in 1..n) {
            store.seed(PrinterState(heaters = heaters("extruder" to HeaterState(temperature = i.toDouble()))))
            runCurrent()
        }

        val snap = holder.sparkline.value
        assertTrue("ring must stay bounded ≤ capacity", snap.size <= RingBuffer.DEFAULT_CAPACITY)
        assertEquals("latest temp must be the last pushed", n.toFloat(), snap.last(), 0.001f)
    }

    @Test
    fun standardPrinterGridHasNozzleAndBedSlots() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = PrintStatusHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(hasBed = true, heaters = listOf("extruder", "heater_bed")))

        store.seed(
            PrinterState(
                heaters = heaters(
                    "extruder" to HeaterState(temperature = 200.0, target = 210.0),
                    "heater_bed" to HeaterState(temperature = 58.0, target = 60.0),
                ),
                progress = 0.42,
            ),
        )
        runCurrent()

        val grid = holder.grid.value
        assertEquals("extruder", grid.primary?.objectName)
        assertEquals(200.0, grid.primary!!.current, 0.001)
        assertEquals(210.0, grid.primary!!.target!!, 0.001)
        assertEquals("heater_bed", grid.secondary?.objectName)
        assertEquals(58.0, grid.secondary!!.current, 0.001)
        assertEquals(60.0, grid.secondary!!.target!!, 0.001)
    }

    @Test
    fun noBedPrinterOmitsBedSlotAndPromotesNextHeater() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = PrintStatusHolder(backgroundScope, store)
        // No heater_bed; a chamber heater exists as the only secondary candidate.
        store.setCapabilities(
            Capabilities(hasBed = false, heaters = listOf("extruder", "heater_generic chamber")),
        )

        store.seed(
            PrinterState(
                heaters = heaters(
                    "extruder" to HeaterState(temperature = 200.0, target = 210.0),
                    "heater_generic chamber" to HeaterState(temperature = 35.0, target = 40.0),
                ),
            ),
        )
        runCurrent()

        val grid = holder.grid.value
        // No fabricated bed cell — the secondary is the promoted chamber heater by its object name.
        assertEquals("heater_generic chamber", grid.secondary?.objectName)
        assertEquals(35.0, grid.secondary!!.current, 0.001)
    }

    @Test
    fun noBedNoSecondaryHeaterOmitsSecondaryEntirely() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = PrintStatusHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(hasBed = false, heaters = listOf("extruder")))

        store.seed(PrinterState(heaters = heaters("extruder" to HeaterState(temperature = 200.0))))
        runCurrent()

        val grid = holder.grid.value
        assertEquals("extruder", grid.primary?.objectName)
        assertNull("no fabricated bed/secondary cell when none exists", grid.secondary)
    }

    @Test
    fun nonstandardExtruderNameResolvesViaPrefixRule() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = PrintStatusHolder(backgroundScope, store)
        // No exact "extruder" key — only a prefixed one (multi-tool naming).
        store.setCapabilities(Capabilities(hasBed = false, heaters = listOf("extruder1")))

        store.seed(PrinterState(heaters = heaters("extruder1" to HeaterState(temperature = 195.0, target = 200.0))))
        runCurrent()

        val grid = holder.grid.value
        assertEquals("extruder1", grid.primary?.objectName)
        assertEquals(195.0, grid.primary!!.current, 0.001)
        // The ring follows whichever heater resolved as primary.
        assertEquals(195f, holder.sparkline.value.last(), 0.001f)
    }

    @Test
    fun fewerThanSixStatsRenderPlaceholdersNeverFabricatedValues() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = PrintStatusHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(hasBed = false, heaters = listOf("extruder")))

        store.seed(
            PrinterState(
                heaters = heaters("extruder" to HeaterState(temperature = 200.0, target = 210.0)),
                progress = 0.0,
                printState = PrintState.Standby,
                printFilename = "",
            ),
        )
        runCurrent()

        val grid = holder.grid.value
        // The 2×3 grid always exposes 6 logical cells; ones with no source are EMPTY, not 0-fabricated.
        assertEquals(6, grid.cells.size)
        val empties = grid.cells.count { it == null }
        assertTrue("at least one cell must be an empty placeholder with this sparse printer", empties >= 1)
    }

    @Test
    fun holderConsumesStoreFlowAndExposesStateFlows() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = PrintStatusHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(hasBed = true, heaters = listOf("extruder", "heater_bed")))

        // first() proves the exposed flows are live StateFlows fed by the store's collect.
        store.seed(PrinterState(heaters = heaters("extruder" to HeaterState(temperature = 123.0))))
        runCurrent()
        val grid = holder.grid.first()
        assertEquals(123.0, grid.primary!!.current, 0.001)
    }
}
