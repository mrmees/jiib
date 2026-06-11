package works.mees.dinghy.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host proof of the R10 step-3 swipe-up accumulation contract (26.5-03 Task 2).
 *
 * The 12-event profile mirrors `FineTuneNavTest`'s `swipeUp()` (~800px over 12 events, ~66.7px
 * per event) — the exact real-world gesture the old per-event `dragAmount < -80f` check could
 * never detect (no single event clears 80px). The threshold value (80f) is UNCHANGED from
 * `AppShell.SWIPE_UP_THRESHOLD_PX`; only the accumulation decision is new.
 */
class SwipeUpAccumulatorTest {

    private companion object {
        const val THRESHOLD_PX = 80f
    }

    @Test
    fun slowTwelveEventSwipe_firesExactlyOnce() {
        val acc = SwipeUpAccumulator(THRESHOLD_PX)
        acc.onDragStart()
        var fires = 0
        repeat(12) { if (acc.onDrag(-66.7f)) fires++ }
        assertEquals("the FineTuneNavTest swipeUp profile (12 × -66.7px) fires exactly once", 1, fires)
    }

    @Test
    fun fastSingleEventFlick_fires() {
        val acc = SwipeUpAccumulator(THRESHOLD_PX)
        acc.onDragStart()
        assertTrue("a single -100px event still fires (fast-flick regression guard)", acc.onDrag(-100f))
    }

    @Test
    fun oscillatingDrag_netNeverCrossing_neverFires() {
        val acc = SwipeUpAccumulator(THRESHOLD_PX)
        acc.onDragStart()
        assertFalse("net -60px does not cross -80px", acc.onDrag(-60f))
        assertFalse("net 0px does not cross -80px", acc.onDrag(+60f))
        assertFalse("net -60px again does not cross -80px", acc.onDrag(-60f))
    }

    @Test
    fun resetBetweenGestures_preventsCrossGestureAccumulation() {
        val acc = SwipeUpAccumulator(THRESHOLD_PX)
        acc.onDragStart()
        assertFalse("-70px in gesture 1 stays under threshold", acc.onDrag(-70f))
        // Gesture ends; a new gesture begins.
        acc.onDragStart()
        assertFalse("-70px in gesture 2 must NOT inherit gesture 1's travel", acc.onDrag(-70f))
    }
}
