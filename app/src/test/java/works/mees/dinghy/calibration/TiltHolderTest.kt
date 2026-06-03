package works.mees.dinghy.calibration

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

/**
 * Host-side proof for [TiltHolder]: the toolkit-agnostic transform folding the live `z_tilt.applied`
 * (or `quad_gantry_level.applied` for the QGL variant), a `dispatched` flag, and dispatcher `Failure`
 * events into the [TiltVm] via the canonical [tiltState] state machine (Pitfall 2).
 *
 *  - `applied == true` → [TiltState.Done] (the explicit convergence signal).
 *  - a dispatcher [DispatchEvent.Failure] for the routine's key → [TiltState.Failed] (NEVER inferred
 *    from `applied == false` alone).
 *  - dispatched + `applied == false`/null + no failure → [TiltState.Running].
 *  - never dispatched → [TiltState.Idle].
 *
 * Mirrors [ScrewsTiltHolderTest] / [works.mees.dinghy.ui.extrude.ExtrudeHolderTest]: a real
 * [PrinterStateStore] seeded synchronously under `runTest` with an [UnconfinedTestDispatcher].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TiltHolderTest {

    @Test
    fun appliedTrueIsDone() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = TiltHolder(backgroundScope, store, applied = { it.zTiltApplied })

        holder.markDispatched()
        store.seed(PrinterState(homedAxes = "xyz", zTiltApplied = true))
        runCurrent()

        assertEquals(TiltState.Done, holder.vm.value.state)
        assertNull("clean converge → no error", holder.vm.value.errorText)
    }

    @Test
    fun dispatchedAndNotAppliedNoFailureIsRunning() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = TiltHolder(backgroundScope, store, applied = { it.zTiltApplied })

        holder.markDispatched()
        // The captured z_tilt_e5 post-dispatch shape is {applied:false} — that is RUNNING, not Failed.
        store.seed(PrinterState(homedAxes = "xyz", zTiltApplied = false))
        runCurrent()

        assertEquals(TiltState.Running, holder.vm.value.state)
    }

    @Test
    fun dispatcherFailureIsFailed() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val events = MutableSharedFlow<DispatchEvent>(extraBufferCapacity = 8)
        val holder = TiltHolder(
            backgroundScope,
            store,
            applied = { it.zTiltApplied },
            events = events,
            dispatchKey = "z_tilt_adjust",
        )

        holder.markDispatched()
        store.seed(PrinterState(homedAxes = "xyz", zTiltApplied = false))
        runCurrent()
        assertEquals(TiltState.Running, holder.vm.value.state)

        // The printer rejects the routine — the dispatcher's REDACTED RpcError text surfaces.
        events.emit(DispatchEvent.Failure(key = "z_tilt_adjust", message = "Too many retries"))
        runCurrent()

        assertEquals(TiltState.Failed, holder.vm.value.state)
        assertEquals("Too many retries", holder.vm.value.errorText)
    }

    @Test
    fun neverDispatchedIsIdle() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = TiltHolder(backgroundScope, store, applied = { it.zTiltApplied })

        store.seed(PrinterState(homedAxes = "xyz", zTiltApplied = null))
        runCurrent()

        assertEquals(TiltState.Idle, holder.vm.value.state)
    }

    @Test
    fun homedGateReflectsHomedAxes() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = TiltHolder(backgroundScope, store, applied = { it.zTiltApplied })

        store.seed(PrinterState(homedAxes = "xy"))
        runCurrent()
        assertEquals(false, holder.vm.value.homedGate)

        store.seed(PrinterState(homedAxes = "xyz"))
        runCurrent()
        assertEquals(true, holder.vm.value.homedGate)
    }

    @Test
    fun qglVariantReadsQuadGantryApplied() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        // The SAME holder serves QGL by reading qglApplied (D-02 shared code path).
        val holder = TiltHolder(backgroundScope, store, applied = { it.qglApplied })

        holder.markDispatched()
        store.seed(PrinterState(homedAxes = "xyz", qglApplied = true, zTiltApplied = null))
        runCurrent()

        assertEquals(TiltState.Done, holder.vm.value.state)
    }

    @Test
    fun unrelatedFailureKeyIsIgnored() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val events = MutableSharedFlow<DispatchEvent>(extraBufferCapacity = 8)
        val holder = TiltHolder(
            backgroundScope,
            store,
            applied = { it.zTiltApplied },
            events = events,
            dispatchKey = "z_tilt_adjust",
        )

        holder.markDispatched()
        store.seed(PrinterState(homedAxes = "xyz", zTiltApplied = false))
        runCurrent()

        // A failure from a DIFFERENT routine must not flip this page to Failed.
        events.emit(DispatchEvent.Failure(key = "screws_tilt", message = "bed level exceeds configured limits"))
        runCurrent()

        assertEquals(TiltState.Running, holder.vm.value.state)
        assertNull(holder.vm.value.errorText)
    }
}
