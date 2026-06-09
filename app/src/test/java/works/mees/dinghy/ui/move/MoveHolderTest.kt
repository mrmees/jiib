package works.mees.dinghy.ui.move

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.collections.immutable.toImmutableList
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

/**
 * Host-side proof for [MoveHolder]: the toolkit-agnostic transform turning the store's
 * ALREADY-throttled `printerState` into the Move panel's [MoveVm] — live `gcode_position` X/Y/Z
 * (MOVE-04, the offsets-stripped user-facing coordinates, NOT `toolheadPosition` / Pitfall 1) plus
 * the per-axis homed gating derived from the lowercase `homed_axes` string (RESEARCH §3).
 *
 * The holder owns NO throttle (the store conflates at 250ms) — these tests drive a real
 * [PrinterStateStore] seeded synchronously and assert the holder's exposed StateFlow under the
 * `runTest` virtual clock with an [UnconfinedTestDispatcher] so the holder's `collect` runs eagerly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoveHolderTest {

    @Test
    fun surfacesGcodePositionAndPerAxisHomedGating() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = MoveHolder(backgroundScope, store)

        store.seed(
            PrinterState(
                gcodePosition = listOf(12.5, 40.0, 3.2, 0.0).toImmutableList(),
                homedAxes = "xy",
            ),
        )
        runCurrent()

        val vm = holder.vm.value
        assertEquals(12.5, vm.x!!, 0.001)
        assertEquals(40.0, vm.y!!, 0.001)
        assertEquals(3.2, vm.z!!, 0.001)
        assertTrue("X homed (in 'xy')", vm.xHomed)
        assertTrue("Y homed (in 'xy')", vm.yHomed)
        assertFalse("Z NOT homed (absent from 'xy')", vm.zHomed)
        assertFalse("not all homed when Z is unhomed", vm.allHomed)
    }

    @Test
    fun nullGcodePositionNeverFabricatesZero() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = MoveHolder(backgroundScope, store)

        // No gcode_position yet (null) — the vm must surface null, never a fabricated 0.0.
        store.seed(PrinterState(gcodePosition = null, homedAxes = ""))
        runCurrent()

        val vm = holder.vm.value
        assertNull("x null when no position reported", vm.x)
        assertNull("y null when no position reported", vm.y)
        assertNull("z null when no position reported", vm.z)
        assertFalse(vm.xHomed)
        assertFalse(vm.yHomed)
        assertFalse(vm.zHomed)
        assertFalse(vm.allHomed)
    }

    @Test
    fun fullyHomedSetsAllHomed() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = MoveHolder(backgroundScope, store)

        store.seed(
            PrinterState(
                gcodePosition = listOf(0.0, 0.0, 10.0).toImmutableList(),
                homedAxes = "xyz",
            ),
        )
        runCurrent()

        val vm = holder.vm.first()
        assertTrue(vm.xHomed && vm.yHomed && vm.zHomed)
        assertTrue("allHomed true when x,y,z all present in homed_axes", vm.allHomed)
    }
}
