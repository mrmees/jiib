package works.mees.jiib.calibration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.state.Capabilities
import works.mees.jiib.state.PrinterState
import works.mees.jiib.state.PrinterStateStore

/**
 * Host-side proof for [ApplyBabystepHolder]: pure math layer that computes the proposed
 * new probe z_offset by baking the live babystep offset into the saved value.
 *
 * Rules under test:
 *  - saved, babystep → newOffset = saved - babystep (subtract: babystep shifts the whole
 *    coordinate frame; mirrors Klipper Z_OFFSET_APPLY_PROBE); canApply = true when both non-null and babystep != 0.0.
 *  - babystep == 0.0 → canApply false (nothing to bake).
 *  - either value null → newOffset null, canApply false.
 *  - probe caps → applyCommand "Z_OFFSET_APPLY_PROBE"; probe-less → "Z_OFFSET_APPLY_ENDSTOP".
 *
 * Mirrors [ProbeCalibrateHolderTest]: real [PrinterStateStore] under bounded coroutines
 * ([backgroundScope] + [UnconfinedTestDispatcher]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApplyBabystepHolderTest {

    @Test
    fun savedAndBabystepProducesNewOffsetAndCanApply() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ApplyBabystepHolder(backgroundScope, store)

        store.setCapabilities(Capabilities(objects = setOf("probe", "manual_probe")))
        store.setProbeZOffset(2.04f)
        store.seed(PrinterState(gcodeZOffset = -0.06))
        runCurrent()

        val vm = holder.vm.value
        // savedOffset is probeZOffset (Float) converted to Double — Float precision ~1e-7, use 1e-5
        assertEquals("savedOffset", 2.04, vm.savedOffset!!, 1e-5)
        assertEquals("liveBabystep", -0.06, vm.liveBabystep!!, 1e-9)
        // Klipper: new_calibrate = z_offset - homing_origin.z → 2.04 - (-0.06) = 2.10
        assertEquals("newOffset = saved - babystep ≈ 2.10", 2.10, vm.newOffset!!, 1e-5)
        assertTrue("canApply when both non-null and babystep != 0.0", vm.canApply)
    }

    @Test
    fun positiveBabystepSubtractsFromSaved() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ApplyBabystepHolder(backgroundScope, store)

        store.setCapabilities(Capabilities(objects = setOf("probe", "manual_probe")))
        store.setProbeZOffset(1.00f)
        store.seed(PrinterState(gcodeZOffset = 0.10))
        runCurrent()

        val vm = holder.vm.value
        // 1.00 - 0.10 = 0.90
        assertEquals("newOffset = saved - babystep ≈ 0.90", 0.90, vm.newOffset!!, 1e-5)
        assertTrue("canApply when babystep non-zero", vm.canApply)
    }

    @Test
    fun babystepZeroMeansCanApplyFalse() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ApplyBabystepHolder(backgroundScope, store)

        store.setCapabilities(Capabilities(objects = setOf("probe", "manual_probe")))
        store.setProbeZOffset(2.04f)
        store.seed(PrinterState(gcodeZOffset = 0.0))
        runCurrent()

        val vm = holder.vm.value
        assertFalse("canApply false when babystep == 0.0", vm.canApply)
    }

    @Test
    fun savedNullMeansNewOffsetNullAndCanApplyFalse() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ApplyBabystepHolder(backgroundScope, store)

        // probeZOffset not set → null
        store.seed(PrinterState(gcodeZOffset = -0.06))
        runCurrent()

        val vm = holder.vm.value
        assertNull("newOffset null when savedOffset null", vm.newOffset)
        assertFalse("canApply false when savedOffset null", vm.canApply)
    }

    @Test
    fun babystepNullMeansNewOffsetNullAndCanApplyFalse() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ApplyBabystepHolder(backgroundScope, store)

        store.setCapabilities(Capabilities(objects = setOf("probe", "manual_probe")))
        store.setProbeZOffset(2.04f)
        store.seed(PrinterState(gcodeZOffset = null))
        runCurrent()

        val vm = holder.vm.value
        assertNull("newOffset null when liveBabystep null", vm.newOffset)
        assertFalse("canApply false when liveBabystep null", vm.canApply)
    }

    @Test
    fun probeCapsGivesApplyProbeCommand() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ApplyBabystepHolder(backgroundScope, store)

        store.setCapabilities(Capabilities(objects = setOf("probe", "manual_probe")))
        store.seed(PrinterState())
        runCurrent()

        assertEquals("Z_OFFSET_APPLY_PROBE", holder.vm.value.applyCommand)
    }

    @Test
    fun probeLessCapsGivesApplyEndstopCommand() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ApplyBabystepHolder(backgroundScope, store)

        store.setCapabilities(Capabilities(objects = setOf("manual_probe")))
        store.seed(PrinterState())
        runCurrent()

        assertEquals("Z_OFFSET_APPLY_ENDSTOP", holder.vm.value.applyCommand)
    }

    @Test
    fun probeLessEndstopOffsetProducesNewOffsetAndCanApply() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ApplyBabystepHolder(backgroundScope, store)

        // No probe in caps → Z_OFFSET_APPLY_ENDSTOP; savedOffset sourced from stepper_z.position_endstop
        store.setCapabilities(Capabilities(objects = setOf("manual_probe")))
        store.setEndstopZOffset(0.50f)
        store.seed(PrinterState(gcodeZOffset = -0.06))
        runCurrent()

        val vm = holder.vm.value
        // savedOffset from endstopZOffset (Float→Double, use 1e-5 tolerance)
        assertEquals("savedOffset from endstopZOffset", 0.50, vm.savedOffset!!, 1e-5)
        assertEquals("liveBabystep", -0.06, vm.liveBabystep!!, 1e-9)
        // Klipper Z_OFFSET_APPLY_ENDSTOP: new = saved - babystep → 0.50 - (-0.06) = 0.56
        assertEquals("newOffset = saved - babystep ≈ 0.56", 0.56, vm.newOffset!!, 1e-5)
        assertEquals("applyCommand", "Z_OFFSET_APPLY_ENDSTOP", vm.applyCommand)
        assertTrue("canApply when endstopZOffset set and babystep non-zero", vm.canApply)
    }
}
