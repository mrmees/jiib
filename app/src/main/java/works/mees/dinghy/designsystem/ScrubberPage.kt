package works.mees.dinghy.designsystem

/**
 * The pure, host-tested math + contract helpers shared by the scrubber implementations.
 *
 * The legacy fill-bar composables that lived here (`ScrubberPage`, then `ScrubberControl` +
 * `ScrubberActions`) were deleted across the 2026-06-12 normalization sweep + structural slate
 * (R9): the sketch-004 ringed-thumb [works.mees.dinghy.designsystem.components.Scrubber] is THE
 * scrubber. These helpers remain because the 004 component funnels its gesture through the same
 * proven seams ([ScrubberMappingTest] / OutputScrubberSettleTest).
 */

/**
 * The pure offset→fraction mapping at the heart of the scrubber (G-3). Maps a horizontal touch
 * position [x] (px, relative to the track's left edge) over a measured [barWidthPx] to a
 * `0f..1f` fraction of the track. BOTH the zero-movement tap (down) and the drag funnel through
 * this single function, so a tap at x and a drag to the same x produce an identical value (no
 * second gesture detector to race the pointer stream — the WR-01 fix). Clamped to `0..1`; an
 * un-measured (`<= 0`) width returns `0f` rather than dividing by zero / yielding NaN.
 *
 * `internal` (not a local lambda) so [ScrubberMappingTest] can prove the mapping host-side — the
 * coverage gap that let the original on-device tap-swallow slip past green tests.
 */
internal fun fractionFromX(x: Float, barWidthPx: Float): Float {
    if (barWidthPx <= 0f) return 0f
    return (x / barWidthPx).coerceIn(0f, 1f)
}

/** Vertical-scrubber fraction: 0f at the bottom (y == height), 1f at the top (y == 0). Clamped. */
internal fun fractionFromY(y: Float, trackHeightPx: Float): Float {
    if (trackHeightPx <= 0f) return 0f
    return (1f - (y / trackHeightPx)).coerceIn(0f, 1f)
}

/**
 * The pure settle-decision at the heart of the settle dispatch contract (HIGH-3 / T-19-06-02).
 * Given a sequence of pointer phases for ONE gesture — any number of `move` frames bracketed by a
 * single `up` — it returns the number of times the value should be dispatched. The contract:
 * dispatch fires ONCE per gesture-END (`up`), NEVER on a `move` frame, so dragging across N
 * positions yields exactly ONE dispatch.
 *
 * Extracted as a pure function (not inlined in the gesture loop) so the "one call per
 * gesture-end, not per scrub frame" guarantee is proven host-side without a full
 * Compose/Robolectric harness (OutputScrubberSettleTest).
 */
enum class ScrubPhase { DOWN, MOVE, UP }

fun settleDispatchCount(phases: List<ScrubPhase>): Int = phases.count { it == ScrubPhase.UP }
