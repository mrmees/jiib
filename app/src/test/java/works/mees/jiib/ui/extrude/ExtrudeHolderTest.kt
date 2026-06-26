package works.mees.jiib.ui.extrude

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import works.mees.jiib.command.CommandMap
import works.mees.jiib.command.PrinterCommands
import works.mees.jiib.state.Capabilities
import works.mees.jiib.state.FilamentSensorState
import works.mees.jiib.state.HeaterState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.state.PrinterStateStore

/**
 * Host-side proof for [ExtrudeHolder]: the toolkit-agnostic transform turning the store's
 * ALREADY-throttled `printerState` (PLUS the one-shot `minExtrudeTemp` / `maxExtrudeDistance`
 * StateFlows landed at handshake, 05-03) into the Extrude panel's [ExtrudeVm].
 *
 *  - `canExtrude` is the PER-TOOL live safety gate read off `heaters[activeTool].canExtrude`,
 *    fail-safe false when the extruder is missing or never reported it (EXTR-04 / D-07).
 *  - `tools` / `showToolSelector` are derived from `Capabilities.extruderCount` (>1 → selector, D-09).
 *  - `hasLoadMacro` / `hasUnloadMacro` go through `hasMacroIgnoreCase` (Moonraker lowercases macro
 *    object names — Pitfall 2 / D-10).
 *  - `minExtrudeTemp` / `maxExtrudeDistance` mirror the store's one-shot StateFlows (null when unread).
 *
 * The holder owns NO throttle (the store conflates at 250ms / mirrors MoveHolder); these tests drive a
 * real [PrinterStateStore] seeded synchronously and assert the holder's exposed StateFlow under the
 * `runTest` virtual clock with an [UnconfinedTestDispatcher] so the holder's `collect` runs eagerly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExtrudeHolderTest {

    private fun heaters(vararg pairs: Pair<String, HeaterState>): ImmutableMap<String, HeaterState> =
        linkedMapOf(*pairs).toImmutableMap()

    @Test
    fun canExtrudeTrueWhenActiveExtruderReportsTrue() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ExtrudeHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(extruderCount = 1, heaters = listOf("extruder")))

        store.seed(
            PrinterState(
                heaters = heaters("extruder" to HeaterState(temperature = 200.0, canExtrude = true)),
            ),
        )
        runCurrent()

        assertTrue("vm.canExtrude true when the active extruder reports can_extrude", holder.vm.value.canExtrude)
    }

    @Test
    fun canExtrudeFalseWhenActiveExtruderReportsFalse() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ExtrudeHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(extruderCount = 1, heaters = listOf("extruder")))

        store.seed(
            PrinterState(
                heaters = heaters("extruder" to HeaterState(temperature = 20.0, canExtrude = false)),
            ),
        )
        runCurrent()

        assertFalse("vm.canExtrude false (cold) — the boundary, fail-safe gate", holder.vm.value.canExtrude)
    }

    @Test
    fun canExtrudeFalseWhenExtruderMissing() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ExtrudeHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(extruderCount = 1, heaters = listOf("extruder")))

        // No extruder heater in state at all — must read fail-safe false, never NPE.
        store.seed(PrinterState(heaters = persistentMapOf()))
        runCurrent()

        assertFalse("vm.canExtrude fail-safe false when the active tool is absent", holder.vm.value.canExtrude)
    }

    @Test
    fun singleExtruderHidesToolSelector() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ExtrudeHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(extruderCount = 1, heaters = listOf("extruder")))

        store.seed(PrinterState(heaters = heaters("extruder" to HeaterState(canExtrude = true))))
        runCurrent()

        val vm = holder.vm.value
        assertFalse("single-extruder → no tool selector (D-09)", vm.showToolSelector)
        assertEquals(listOf("T0"), vm.tools)
    }

    @Test
    fun multiExtruderShowsToolSelector() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ExtrudeHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(extruderCount = 2, heaters = listOf("extruder", "extruder1")))

        store.seed(PrinterState(heaters = heaters("extruder" to HeaterState(canExtrude = true))))
        runCurrent()

        val vm = holder.vm.value
        assertTrue("multi-extruder → tool selector shown (D-09)", vm.showToolSelector)
        assertEquals(listOf("T0", "T1"), vm.tools)
    }

    @Test
    fun loadUnloadMacroPresenceIsCaseInsensitive() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ExtrudeHolder(backgroundScope, store)
        // Moonraker lowercases macro object names (Pitfall 2) — the gate must still match LOAD_FILAMENT.
        store.setCapabilities(
            Capabilities(
                extruderCount = 1,
                heaters = listOf("extruder"),
                macros = listOf("load_filament", "unload_filament"),
            ),
        )

        store.seed(PrinterState(heaters = heaters("extruder" to HeaterState(canExtrude = true))))
        runCurrent()

        val vm = holder.vm.value
        assertTrue("lowercase load_filament matches LOAD_FILAMENT (D-10)", vm.hasLoadMacro)
        assertTrue("lowercase unload_filament matches UNLOAD_FILAMENT (D-10)", vm.hasUnloadMacro)
    }

    @Test
    fun missingLoadUnloadMacrosReportFalse() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ExtrudeHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(extruderCount = 1, heaters = listOf("extruder"), macros = emptyList()))

        store.seed(PrinterState(heaters = heaters("extruder" to HeaterState(canExtrude = true))))
        runCurrent()

        val vm = holder.vm.value
        assertFalse("no load macro → hasLoadMacro false (popup path)", vm.hasLoadMacro)
        assertFalse("no unload macro → hasUnloadMacro false (popup path)", vm.hasUnloadMacro)
    }

    @Test
    fun oneShotMinTempAndMaxDistanceSurfaceOnVm() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ExtrudeHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(extruderCount = 1, heaters = listOf("extruder")))

        store.seed(PrinterState(heaters = heaters("extruder" to HeaterState(canExtrude = false))))
        // The one-shot handshake reads land on their StateFlows; the holder COMBINES them deterministically.
        store.setMinExtrudeTemp(170f)
        store.setMaxExtrudeDistance(50f)
        runCurrent()

        val vm = holder.vm.first()
        assertEquals(170f, vm.minExtrudeTemp!!, 0.001f)
        assertEquals(50f, vm.maxExtrudeDistance!!, 0.001f)
    }

    // --- CommandMap propagation proof (R4) ------------------------------------------------------
    // These cases reference CommandMap SYMBOLICALLY (never the string literal) so they stay
    // correct under any future fork-and-edit rename — that is what makes them a propagation
    // proof rather than a tautology: whatever name CommandMap carries, the holder gates on it
    // and PrinterCommands emits it.

    @Test
    fun commandMapMacro_gatesLoadUnloadThroughRealWiring() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ExtrudeHolder(backgroundScope, store)
        // Capabilities advertise EXACTLY the CommandMap macro names (lowercased, as Moonraker
        // reports them — the case-insensitive gate must still match the map's names).
        store.setCapabilities(
            Capabilities(
                extruderCount = 1,
                heaters = listOf("extruder"),
                macros = listOf(
                    CommandMap.loadFilament.macro.lowercase(),
                    CommandMap.unloadFilament.macro.lowercase(),
                ),
            ),
        )

        store.seed(PrinterState(heaters = heaters("extruder" to HeaterState(canExtrude = true))))
        runCurrent()

        val vm = holder.vm.value
        assertTrue("capabilities containing CommandMap.loadFilament.macro → load gate true", vm.hasLoadMacro)
        assertTrue("capabilities containing CommandMap.unloadFilament.macro → unload gate true", vm.hasUnloadMacro)
    }

    @Test
    fun differentMacroName_doesNotGateLoadUnload() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ExtrudeHolder(backgroundScope, store)
        // A macro list with names that are NOT CommandMap's slots — gates must stay false,
        // proving the gate reads the map (not some other source).
        store.setCapabilities(
            Capabilities(
                extruderCount = 1,
                heaters = listOf("extruder"),
                macros = listOf("some_other_macro", "filament_changer_9000"),
            ),
        )

        store.seed(PrinterState(heaters = heaters("extruder" to HeaterState(canExtrude = true))))
        runCurrent()

        val vm = holder.vm.value
        assertFalse("non-CommandMap macro names → load gate false", vm.hasLoadMacro)
        assertFalse("non-CommandMap macro names → unload gate false", vm.hasUnloadMacro)
    }

    @Test
    fun printerCommands_emitCommandMapGcode() {
        // The emission half of the R4 propagation proof: the wire builders return EXACTLY the
        // map's gcode strings. Together with the gating cases above, a one-constant rename in
        // CommandMap.kt would both gate (capability check) and fire (gcode emission) correctly.
        assertEquals(CommandMap.loadFilament.gcode, PrinterCommands.loadFilament())
        assertEquals(CommandMap.unloadFilament.gcode, PrinterCommands.unloadFilament())
    }

    // --- Task 5: sensors, velocity cap, pinned + all macros ------------------------------------

    @Test
    fun filamentSensorSurfacesAsSensorRow() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ExtrudeHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(extruderCount = 1, heaters = listOf("extruder")))

        store.seed(
            PrinterState(
                heaters = heaters("extruder" to HeaterState(canExtrude = true)),
                filamentSensors = persistentMapOf(
                    "filament_switch_sensor Runout" to FilamentSensorState(enabled = true, filamentDetected = true),
                ),
            ),
        )
        runCurrent()

        val sensors = holder.vm.value.sensors
        assertEquals("one discovered sensor row", 1, sensors.size)
        val row = sensors.single()
        assertEquals("filament_switch_sensor Runout", row.objectKey)
        assertEquals("bare name = SET_FILAMENT_SENSOR SENSOR= arg", "Runout", row.sensorName)
        assertEquals("Runout", row.prettyName)
        assertTrue("live enabled surfaces", row.enabled)
        assertEquals(true, row.filamentDetected)
    }

    @Test
    fun maxExtrudeVelocityFallsBackTo15WhenNull() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ExtrudeHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(extruderCount = 1, heaters = listOf("extruder")))

        store.seed(PrinterState(heaters = heaters("extruder" to HeaterState(canExtrude = true))))
        runCurrent()

        assertEquals(
            "null reported velocity → the 15 mm/s fallback",
            PrinterCommands.MAX_EXTRUDE_ONLY_VELOCITY_FALLBACK,
            holder.vm.value.maxExtrudeVelocity,
        )
    }

    @Test
    fun maxExtrudeVelocityReflectsReportedValue() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ExtrudeHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(extruderCount = 1, heaters = listOf("extruder")))

        store.seed(PrinterState(heaters = heaters("extruder" to HeaterState(canExtrude = true))))
        store.setMaxExtrudeVelocity(8f)
        runCurrent()

        assertEquals("reported 8 mm/s surfaces as 8", 8, holder.vm.value.maxExtrudeVelocity)
    }

    @Test
    fun maxExtrudeVelocityNeverExceedsBuilderCeiling() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ExtrudeHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(extruderCount = 1, heaters = listOf("extruder")))

        store.seed(PrinterState(heaters = heaters("extruder" to HeaterState(canExtrude = true))))
        // A wildly high reported velocity must clamp to MAX_EXTRUDE_FEED_MM_MIN / 60.
        store.setMaxExtrudeVelocity(9_999f)
        runCurrent()

        assertEquals(
            "velocity capped at the builder feed ceiling",
            PrinterCommands.MAX_EXTRUDE_FEED_MM_MIN / 60,
            holder.vm.value.maxExtrudeVelocity,
        )
    }

    @Test
    fun pinnedMacrosIncludeCustomAndExcludeLoadUnload() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val pins = MutableStateFlow(
            setOf("MY_PURGE", CommandMap.loadFilament.macro.lowercase()),
        )
        val holder = ExtrudeHolder(backgroundScope, store, pins)
        store.setCapabilities(
            Capabilities(
                extruderCount = 1,
                heaters = listOf("extruder"),
                macros = listOf(
                    "MY_PURGE",
                    CommandMap.loadFilament.macro,
                    CommandMap.unloadFilament.macro,
                    "_HIDDEN_HELPER",
                ),
            ),
        )

        store.seed(PrinterState(heaters = heaters("extruder" to HeaterState(canExtrude = true))))
        runCurrent()

        val vm = holder.vm.value
        val pinnedNames = vm.pinnedMacros.map { it.name }
        assertTrue("custom pinned macro present (case-recovered to canonical)", pinnedNames.contains("MY_PURGE"))
        assertFalse(
            "load macro excluded from pinned rows (it has its own action)",
            pinnedNames.any { it.equals(CommandMap.loadFilament.macro, ignoreCase = true) },
        )
        assertFalse(
            "unload macro excluded from pinned rows",
            pinnedNames.any { it.equals(CommandMap.unloadFilament.macro, ignoreCase = true) },
        )

        val allNames = vm.allMacros.map { it.name }
        assertTrue("visible list includes the custom macro", allNames.contains("MY_PURGE"))
        assertFalse("underscore-prefixed macros are hidden from the all-list", allNames.any { it.startsWith("_") })
        assertEquals("pinnedNames passes through raw", pins.value, vm.pinnedNames)
    }

    @Test
    fun unsetOneShotReadsAreNull() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ExtrudeHolder(backgroundScope, store)
        store.setCapabilities(Capabilities(extruderCount = 1, heaters = listOf("extruder")))

        store.seed(PrinterState(heaters = heaters("extruder" to HeaterState(canExtrude = false))))
        runCurrent()

        val vm = holder.vm.value
        assertNull("min-temp unknown → null (UI shows the generic hint)", vm.minExtrudeTemp)
        assertNull("max-distance unknown → null (UI keeps the 100mm default ceiling)", vm.maxExtrudeDistance)
    }
}
