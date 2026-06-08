package works.mees.dinghy.ui.outputs

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.dinghy.designsystem.ScrubPhase
import works.mees.dinghy.designsystem.settleDispatchCount

/**
 * Host proof of the OnSettle dispatch contract (HIGH-3 / T-19-06-02): in [works.mees.dinghy.designsystem
 * .ScrubberPage]'s OnSettle mode the value dispatches EXACTLY ONCE per gesture-end (pointer-up), NEVER on
 * an intermediate scrub frame. Dragging across N positions yields exactly one onSettle call.
 *
 * The settle decision is the pure [settleDispatchCount] helper the ScrubberPage gesture loop calls at
 * gesture-end — this proves the "one call per gesture, not per frame" guarantee without a Compose harness
 * (the coverage seam the plan asks for: a Compose test of the awaitEachGesture loop is impractical here).
 */
class OutputScrubberSettleTest {

    @Test
    fun `a pure tap dispatches exactly once`() {
        // down → up (no moves) = one gesture = one dispatch.
        val phases = listOf(ScrubPhase.DOWN, ScrubPhase.UP)
        assertEquals(1, settleDispatchCount(phases))
    }

    @Test
    fun `a drag across many positions dispatches exactly once on release`() {
        // down + N moves + one up — the N intermediate frames must NOT dispatch (Adreno-320 budget).
        val phases = buildList {
            add(ScrubPhase.DOWN)
            repeat(20) { add(ScrubPhase.MOVE) }
            add(ScrubPhase.UP)
        }
        assertEquals("a drag is ONE settle, never per-frame", 1, settleDispatchCount(phases))
    }

    @Test
    fun `intermediate move frames never dispatch on their own`() {
        // A stream of moves with no terminating up (pointer still down) = zero dispatches so far.
        val phases = listOf(ScrubPhase.DOWN, ScrubPhase.MOVE, ScrubPhase.MOVE)
        assertEquals(0, settleDispatchCount(phases))
    }

    @Test
    fun `two distinct gestures dispatch twice`() {
        // Two separate drag-release gestures = two settles (each release is its own dispatch).
        val phases = listOf(
            ScrubPhase.DOWN, ScrubPhase.MOVE, ScrubPhase.UP,
            ScrubPhase.DOWN, ScrubPhase.MOVE, ScrubPhase.MOVE, ScrubPhase.UP,
        )
        assertEquals(2, settleDispatchCount(phases))
    }
}
