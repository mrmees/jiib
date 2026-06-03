package works.mees.dinghy.calibration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.render.BedMeshHeatmapView.ScaleMode
import works.mees.dinghy.state.BedMeshObject
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

/**
 * Host-side proof for [BedMeshHolder]: the toolkit-agnostic transform turning the store's
 * already-throttled `printerState` (the live `bed_mesh` object) into a [BedMeshVm] the screen renders,
 * plus the holder-owned cyclable scale mode and folded dispatcher Failure/Success.
 *
 *  - [BedMeshVm.model] comes from [BedMeshModel.from] (the holder reconstructs the raw JSON from the
 *    reduced [BedMeshObject] so the parse stays in ONE place).
 *  - Empty-state ([BedMeshModel.isEmpty]) is `mesh_matrix` empty / `profile_name == ""` — SEPARATE from
 *    `profiles` (saved list) non-emptiness (Pitfall 4).
 *  - [cycleScaleMode] walks RELATIVE → PLATE → ±0.10 → ±0.25 → ±0.50 → ±1.00 → RELATIVE without
 *    re-probing (pure view-layer color change).
 *
 * Mirrors [ScrewsTiltHolderTest] / [works.mees.dinghy.ui.extrude.ExtrudeHolderTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BedMeshHolderTest {

    private fun activeMesh(): BedMeshObject = BedMeshObject(
        profileName = "adaptive-7FA0AB1C50",
        meshMin = listOf(85.5, 148.8),
        meshMax = listOf(294.6, 237.5),
        probedMatrix = listOf(
            listOf(0.05, 0.01, 0.01, 0.03),
            listOf(0.05, 0.02, 0.01, 0.04),
        ),
        meshMatrix = listOf(
            listOf(0.05, 0.03, 0.02, 0.01),
            listOf(0.05, 0.03, 0.02, 0.04),
        ),
        profileNames = listOf("default", "post", "pre"),
    )

    @Test
    fun fixtureMeshSurfacesMatricesAndProfileNames() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = BedMeshHolder(backgroundScope, store)

        store.seed(PrinterState(bedMesh = activeMesh()))
        runCurrent()

        val vm = holder.vm.value
        assertEquals("adaptive-7FA0AB1C50", vm.model.profileName)
        assertEquals(2, vm.model.meshMatrix.size)
        assertEquals(4, vm.model.meshMatrix[0].size)
        assertEquals(2, vm.model.probedMatrix.size)
        assertFalse("an active mesh is NOT empty", vm.isEmpty)
        assertEquals(listOf("default", "post", "pre"), vm.profileNames)
    }

    @Test
    fun emptyMeshIsEmptyWhileProfilesStillList() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = BedMeshHolder(backgroundScope, store)

        // No active mesh (mesh_matrix empty / profile_name "") but the printer HAS saved profiles
        // (Pitfall 4 — these are SEPARATE conditions).
        store.seed(
            PrinterState(
                bedMesh = BedMeshObject(
                    profileName = "",
                    meshMatrix = null,
                    profileNames = listOf("default", "pre"),
                ),
            ),
        )
        runCurrent()

        val vm = holder.vm.value
        assertTrue("no loaded mesh → empty-state copy in the Focus", vm.isEmpty)
        assertEquals("saved profiles still list for Load", listOf("default", "pre"), vm.profileNames)
    }

    @Test
    fun cycleScaleModeWalksAllSixModesAndWraps() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = BedMeshHolder(backgroundScope, store)

        store.seed(PrinterState(bedMesh = activeMesh()))
        runCurrent()

        assertEquals("default = RELATIVE", ScaleMode.RELATIVE, holder.vm.value.scaleMode)
        val seen = mutableListOf(holder.vm.value.scaleMode)
        repeat(6) {
            holder.cycleScaleMode()
            seen.add(holder.vm.value.scaleMode)
        }
        // RELATIVE → PLATE → ±0.10 → ±0.25 → ±0.50 → ±1.00 → RELATIVE (wraps).
        assertEquals(
            listOf(
                ScaleMode.RELATIVE,
                ScaleMode.PLATE,
                ScaleMode.PM_010,
                ScaleMode.PM_025,
                ScaleMode.PM_050,
                ScaleMode.PM_100,
                ScaleMode.RELATIVE,
            ),
            seen,
        )
    }

    @Test
    fun dispatcherFailureFoldsIntoErrorText() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val events = MutableSharedFlow<DispatchEvent>(extraBufferCapacity = 8)
        val holder = BedMeshHolder(backgroundScope, store, events = events)

        store.seed(PrinterState(bedMesh = activeMesh()))
        runCurrent()
        assertNull(holder.vm.value.errorText)

        events.emit(DispatchEvent.Failure(key = "bed_mesh_calibrate", message = "bed level exceeds configured limits (0.42mm)!"))
        runCurrent()
        assertEquals("bed level exceeds configured limits (0.42mm)!", holder.vm.value.errorText)
    }

    @Test
    fun homedGateRequiresAllThreeAxes() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = BedMeshHolder(backgroundScope, store)

        // Unhomed → the screen shows Home-All instead of Activate (BED_MESH_CALIBRATE probes the bed).
        store.seed(PrinterState(bedMesh = activeMesh(), homedAxes = ""))
        runCurrent()
        assertFalse("no axes homed → not gated", holder.vm.value.homed)

        // Partial homing (X/Y only, no Z) is still NOT enough to probe.
        store.seed(PrinterState(bedMesh = activeMesh(), homedAxes = "xy"))
        runCurrent()
        assertFalse("X/Y but no Z → still not homed", holder.vm.value.homed)

        // All three homed → Activate is allowed.
        store.seed(PrinterState(bedMesh = activeMesh(), homedAxes = "xyz"))
        runCurrent()
        assertTrue("x+y+z homed → gate open", holder.vm.value.homed)
    }
}
