package works.mees.dinghy.ui.shell

/**
 * R10 (26.5-03, §R10 step 3): per-gesture drag accumulator for the shell's swipe-up drawer gesture.
 *
 * The old detector required a SINGLE pointer event's `dragAmount` to clear the 80px threshold —
 * per-event thresholding proven brittle (Part 5 cause #3): a slow deliberate swipe delivers many
 * small deltas that individually never cross 80px (it is exactly why `FineTuneNavTest`'s
 * `swipeUp()` — ~800px over 12 events — could not open the drawer). This class accumulates the
 * deltas across the gesture instead, firing once when the NET upward travel crosses the threshold.
 *
 * Pure Kotlin — zero Compose/Android imports — so the gesture decision is host-unit-testable
 * ([SwipeUpAccumulatorTest] proves the 12-event slow-swipe profile, the fast flick, the oscillating
 * non-cross, and the per-gesture reset).
 *
 * Contract:
 *  - [onDragStart] zeroes the accumulator and re-arms (call from `detectVerticalDragGestures`'
 *    `onDragStart`).
 *  - [onDrag] adds the delta (negative = upward) and returns `true` EXACTLY ONCE per gesture, the
 *    first time the accumulated total reaches `-thresholdPx`; it then latches until the next
 *    [onDragStart] so one long swipe cannot re-fire.
 *
 * @param thresholdPx the unchanged shell threshold (`SWIPE_UP_THRESHOLD_PX` = 80f).
 */
class SwipeUpAccumulator(private val thresholdPx: Float) {

    private var accumulated = 0f
    private var fired = false

    /** Running per-gesture total (negative = net upward travel) — exposed for debug logging. */
    val totalPx: Float get() = accumulated

    /** Begin a new gesture: zero the accumulator and re-arm the one-shot latch. */
    fun onDragStart() {
        accumulated = 0f
        fired = false
    }

    /**
     * Accumulate one drag event's [dragAmount]. Returns `true` the first time the per-gesture
     * total crosses the threshold; `false` otherwise (including every event after the latch).
     */
    fun onDrag(dragAmount: Float): Boolean {
        // RED scaffold (26.5-03 Task 2): accumulation not yet implemented.
        return false
    }
}
