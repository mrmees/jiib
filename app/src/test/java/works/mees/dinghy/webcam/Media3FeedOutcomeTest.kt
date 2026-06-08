package works.mees.dinghy.webcam

import org.junit.Assert.fail
import org.junit.Test
import works.mees.dinghy.ui.webcam.FeedOutcome

/**
 * WAVE-0 RED SCAFFOLD (Phase 21, CAM-14) — fails until plan 21-04 maps media3 PlaybackException → outcome.
 *
 * Behavior this file pins (converted to live assertions by 21-04):
 *  - A media3 NETWORK error (ERROR_CODE_IO_NETWORK_CONNECTION_FAILED / ..._TIMEOUT) maps to
 *    [FeedOutcome.Transient] — the H.264 rung is RETRIED with backoff (a Wi-Fi hiccup, not a dead cam).
 *  - A DECODER-class error (decoder init failed / format unsupported / renderer-init) is classified as a
 *    "DECODER-VERIFIES" failure: the COMPOSITE feed FALLS THROUGH to the lower rungs (MJPEG → Snapshot)
 *    WITHIN the same run() — it must NOT short-circuit to [FeedOutcome.Terminal] while a lower rung is
 *    still viable. [FeedOutcome.Terminal] is reached ONLY when the lower rungs are ALSO dead.
 *  - Cancellation (page background / nav / spine rebuild) → [FeedOutcome.Cancelled] (clean exit, WR-01).
 *
 * Worked stub case (21-04 turns this into a live composite-feed assertion): a decoder-unsupported H.264
 * cam that ALSO has a working snapshot → snapshot frames flow (rung-2 fallback), and Terminal is returned
 * ONLY if the snapshot rung is also dead.
 *
 * COMPILE DISCIPLINE (project wave-0 rule): references ONLY the existing [FeedOutcome] enum. Does NOT
 * import androidx.media3 PlaybackException nor any unbuilt mapper — 21-04 introduces both and replaces
 * these `fail(...)` bodies with typed assertions against the real exception codes.
 */
class Media3FeedOutcomeTest {

    @Test
    fun `network connection failure maps to Transient (retry the H264 rung)`() {
        // Existing enum sanity — the target outcome value already exists; the mapper does not.
        check(FeedOutcome.Transient.name == "Transient")
        fail("not yet implemented — 21-04: assert IO_NETWORK_CONNECTION_FAILED → FeedOutcome.Transient")
    }

    @Test
    fun `network timeout maps to Transient`() {
        fail("not yet implemented — 21-04: assert IO_NETWORK_CONNECTION_TIMEOUT → FeedOutcome.Transient")
    }

    @Test
    fun `decoder-unsupported on an H264 cam with a working snapshot falls through to snapshot frames`() {
        // The load-bearing case: decoder failure must NOT short-circuit to Terminal while rung-2 is viable.
        check(FeedOutcome.Terminal.name == "Terminal")
        fail("not yet implemented — 21-04: assert a decoder error falls the COMPOSITE feed through to the snapshot rung (frames flow), NOT Terminal")
    }

    @Test
    fun `decoder failure returns Terminal only when the lower rungs are also dead`() {
        fail("not yet implemented — 21-04: assert Terminal ONLY when MJPEG + Snapshot rungs are also unusable")
    }

    @Test
    fun `cancellation maps to Cancelled (clean exit WR-01)`() {
        check(FeedOutcome.Cancelled.name == "Cancelled")
        fail("not yet implemented — 21-04: assert a cancelled playback scope → FeedOutcome.Cancelled")
    }
}
