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
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore
import works.mees.dinghy.state.Screw
import works.mees.dinghy.state.ScrewConfig
import works.mees.dinghy.state.ScrewResult
import works.mees.dinghy.state.ScrewsTiltObject

/**
 * Host-side proof for [ScrewsTiltHolder]: the toolkit-agnostic transform that COMBINES the store's
 * already-throttled `printerState` (the live `screws_tilt_adjust` results) with the one-shot
 * `screwsTiltConfig` (coords + names) and folds dispatcher `Failure` events, producing the
 * [ScrewsTiltVm] the screen renders VERBATIM.
 *
 *  - [GuidedLoopState.worstScrew] / [GuidedLoopState.inToleranceCount] come from the canonical
 *    [parseScrewsTilt] (the holder reconstructs the raw JSON from the reduced models so the worst-screw
 *    math stays in ONE place — the parser; the holder NEVER re-ranks).
 *  - The to-scale bed points join config coords by 1-based index ([ScrewPoint.x]/[ScrewPoint.y]).
 *  - [ScrewsTiltVm.homedGate] reflects `homed_axes` (all of x/y/z present → ready to probe, D-13).
 *  - [ScrewsTiltVm.errorText] is folded from a dispatcher [DispatchEvent.Failure] (T-09-04-02).
 *
 * Drives a real [PrinterStateStore] seeded synchronously under the `runTest` virtual clock with an
 * [UnconfinedTestDispatcher] so the holder's `collect` runs eagerly (mirrors `ExtrudeHolderTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScrewsTiltHolderTest {

    /** The 4-screw E5-shape result: screw1 base, screw2 the worst (largest deviation from base z). */
    private fun fourScrewState(): PrinterState = PrinterState(
        homedAxes = "xyz",
        screwsTilt = ScrewsTiltObject(
            error = false,
            results = linkedMapOf(
                "screw1" to ScrewResult(z = 0.129, sign = "CW", adjust = "00:00", isBase = true),
                "screw2" to ScrewResult(z = 0.047, sign = "CCW", adjust = "00:07", isBase = false),
                "screw3" to ScrewResult(z = 0.196, sign = "CW", adjust = "00:06", isBase = false),
                "screw4" to ScrewResult(z = 0.156, sign = "CW", adjust = "00:02", isBase = false),
            ).toImmutableMap(),
        ),
    )

    private fun fourScrewConfig(): ScrewConfig = ScrewConfig(
        screws = listOf(
            Screw(x = 30.0, y = 30.0, name = "front left screw"),
            Screw(x = 270.0, y = 30.0, name = "front right screw"),
            Screw(x = 270.0, y = 270.0, name = "rear right screw"),
            Screw(x = 30.0, y = 270.0, name = "rear left screw"),
        ).toImmutableList(),
    )

    @Test
    fun worstScrewAndCountSurfaceVerbatimFromSeededState() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ScrewsTiltHolder(backgroundScope, store)

        store.setScrewsTiltConfig(fourScrewConfig())
        store.seed(fourScrewState())
        holder.setShowResults(true) // a Run completed this load → results surface
        runCurrent()

        val vm = holder.vm.value
        assertEquals("all four screws surfaced", 4, vm.loop.totalScrews)
        assertFalse("clean run → error false", vm.loop.error)
        // worst = the largest deviation-from-base (== Klipper's largest adjust clock) → screw2 "00:07".
        assertEquals("screw2", vm.loop.worstScrew?.key)
        assertEquals("00:07", vm.loop.worstScrew?.adjust)
        assertEquals("CCW", vm.loop.worstScrew?.sign)
        // 1-based config join → the screw's display name carried verbatim.
        assertEquals("front right screw", vm.loop.worstScrew?.name)
        assertTrue("at least the base screw is in tolerance", vm.loop.inToleranceCount >= 1)
    }

    @Test
    fun pointsJoinConfigCoordsByOneBasedIndex() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ScrewsTiltHolder(backgroundScope, store)

        store.setScrewsTiltConfig(fourScrewConfig())
        store.seed(fourScrewState())
        holder.setShowResults(true) // a Run completed this load → results surface
        runCurrent()

        val vm = holder.vm.value
        assertTrue("config coords present → draw the to-scale bed", vm.hasCoords)
        assertEquals(4, vm.points.size)
        // screw2 (index 2) → config[1] = (270, 30).
        val screw2 = vm.points.first { it.key == "screw2" }
        assertEquals(270.0, screw2.x!!, 0.001)
        assertEquals(30.0, screw2.y!!, 0.001)
    }

    @Test
    fun missingConfigFallsBackToNoCoords() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ScrewsTiltHolder(backgroundScope, store)

        // No config one-shot read landed → the screen must fall back to the labeled list (D-06).
        store.seed(fourScrewState())
        holder.setShowResults(true) // a Run completed this load → results surface
        runCurrent()

        val vm = holder.vm.value
        assertEquals("rows still surface from results", 4, vm.loop.totalScrews)
        assertFalse("no coords → list fallback (D-06)", vm.hasCoords)
        assertTrue("every point's coords are null", vm.points.all { it.x == null && it.y == null })
    }

    @Test
    fun homedGateReflectsHomedAxes() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ScrewsTiltHolder(backgroundScope, store)

        store.seed(PrinterState(homedAxes = "xy", screwsTilt = ScrewsTiltObject()))
        runCurrent()
        assertFalse("z unhomed → gate closed (offer Home, D-13)", holder.vm.value.homedGate)

        store.seed(fourScrewState()) // homedAxes = "xyz"
        runCurrent()
        assertTrue("all of x/y/z homed → gate open", holder.vm.value.homedGate)
    }

    @Test
    fun dispatcherFailureFoldsIntoErrorText() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val events = MutableSharedFlow<DispatchEvent>(extraBufferCapacity = 8)
        val holder = ScrewsTiltHolder(backgroundScope, store, events = events)

        store.seed(PrinterState(homedAxes = "xyz"))
        runCurrent()
        assertNull("no failure yet", holder.vm.value.errorText)

        // The printer rejects the probe — the dispatcher's REDACTED RpcError text (T-09-04-02).
        events.emit(DispatchEvent.Failure(key = "screws_tilt", message = "bed level exceeds configured limits"))
        runCurrent()
        assertEquals("bed level exceeds configured limits", holder.vm.value.errorText)
    }

    @Test
    fun unrelatedFailureKeyIsIgnored() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val events = MutableSharedFlow<DispatchEvent>(extraBufferCapacity = 8)
        val holder = ScrewsTiltHolder(backgroundScope, store, events = events)

        store.seed(PrinterState(homedAxes = "xyz"))
        runCurrent()

        // A failure from a DIFFERENT routine must not surface on the screws-tilt page.
        events.emit(DispatchEvent.Failure(key = "z_tilt", message = "Too many retries"))
        runCurrent()
        assertNull("only the screws-tilt dispatch key folds in", holder.vm.value.errorText)
    }

    @Test
    fun hidingResultsSuppressesMeasurementsButKeepsLayout() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = ScrewsTiltHolder(backgroundScope, store)
        store.setScrewsTiltConfig(fourScrewConfig())
        store.seed(fourScrewState()) // results persist in printer state across the session

        // A completed run this load → measured turns surface.
        holder.setShowResults(true)
        runCurrent()
        assertEquals(4, holder.vm.value.loop.totalScrews)
        assertTrue("measured points carry a turn", holder.vm.value.points.all { it.turn != null })

        // Fresh instance / mid-run (showResults=false): the bed/list LAYOUT stays (config names + coords),
        // but every measured turn is dropped — no stale turn directions for a screw maybe already adjusted.
        holder.setShowResults(false)
        runCurrent()
        assertEquals("no measured loop while hidden", 0, holder.vm.value.loop.totalScrews)
        assertTrue("layout (bed) still shows from config", holder.vm.value.hasCoords)
        assertEquals("all four screws still listed", 4, holder.vm.value.points.size)
        assertTrue("but with NO measurement", holder.vm.value.points.all { it.turn == null })

        // Completing a run again re-surfaces the measurements.
        holder.setShowResults(true)
        runCurrent()
        assertEquals(4, holder.vm.value.loop.totalScrews)
    }
}
