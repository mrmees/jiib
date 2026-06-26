package works.mees.jiib.webcam

import androidx.media3.common.PlaybackException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.state.ResolvedWebcam
import works.mees.jiib.state.Rung
import works.mees.jiib.state.Webcam
import works.mees.jiib.ui.webcam.FeedOutcome
import works.mees.jiib.ui.webcam.H264Attempt
import works.mees.jiib.ui.webcam.H264AttemptResult
import works.mees.jiib.ui.webcam.WebcamFeed
import works.mees.jiib.ui.webcam.classifyPlaybackException
import works.mees.jiib.ui.webcam.compositeMedia3Feed

/**
 * Typed assertions (Phase 21, CAM-14) — REPLACES the plan-21-01 runtime-RED scaffold.
 *
 * Two layers are proven here WITHOUT a real ExoPlayer (the player attempt is behind the [H264Attempt] seam,
 * the on-device property is plan-21-05's gate):
 *
 *  1. The PlaybackException CLASSIFICATION ([classifyPlaybackException]):
 *     - a NETWORK error (ERROR_CODE_IO_NETWORK_CONNECTION_FAILED / ..._TIMEOUT) → [H264AttemptResult.Transient]
 *       (the H.264 rung is RETRIED with backoff — a Wi-Fi hiccup, not a dead cam);
 *     - a DECODER-class error (decoder init / format unsupported) → [H264AttemptResult.FallThrough]
 *       (the COMPOSITE feed falls through to the lower MJPEG/Snapshot rungs WITHIN the same run()).
 *
 *  2. The COMPOSITE feed's fall-through ([compositeMedia3Feed]) with an injected attempt + lower rung:
 *     - network → Transient (the holder retries the H.264 rung);
 *     - decoder FallThrough + a working lower rung (snapshot frames flow) → those frames flow and the
 *       outcome is whatever the lower rung returns — NOT a short-circuit Terminal while a lower rung is viable;
 *     - decoder FallThrough + a DEAD lower rung → Terminal (only when the lower rung is also dead);
 *     - cancellation → Cancelled (clean exit, WR-01).
 */
class Media3FeedOutcomeTest {

    private val h264Cam = ResolvedWebcam(
        webcam = Webcam(name = "cam1", uid = "cam1", streamUrl = "rtsp://h/3", snapshotUrl = "http://h/snap"),
        rung = Rung.H264,
    )

    // --- Layer 1: PlaybackException classification -------------------------------------------------

    @Test
    fun `network connection failure classifies as Transient (retry the H264 rung)`() {
        assertEquals(
            H264AttemptResult.Transient,
            classifyPlaybackException(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED),
        )
    }

    @Test
    fun `network timeout classifies as Transient`() {
        assertEquals(
            H264AttemptResult.Transient,
            classifyPlaybackException(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT),
        )
    }

    @Test
    fun `decoder init failure classifies as FallThrough (NOT Terminal while a lower rung is viable)`() {
        assertEquals(
            H264AttemptResult.FallThrough,
            classifyPlaybackException(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED),
        )
        assertEquals(
            H264AttemptResult.FallThrough,
            classifyPlaybackException(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED),
        )
    }

    // --- Layer 2: composite fall-through (injected attempt + lower rung, no real player) -----------

    @Test
    fun `network error returns Transient — the holder retries the H264 rung`() = runTest {
        val attempt = H264Attempt { _ -> H264AttemptResult.Transient }
        // A lower rung that would yield frames if reached — it must NOT be reached on a network error.
        var lowerReached = false
        val lower = WebcamFeed<android.graphics.Bitmap> { _, _ -> lowerReached = true; FeedOutcome.Transient }

        val feed = compositeMedia3Feed(h264Attempt = attempt, lowerRung = lower)
        val outcome = feed.run(h264Cam) {}

        assertEquals(FeedOutcome.Transient, outcome)
        assertTrue("a network error retries the H.264 rung — the lower rung is NOT engaged", !lowerReached)
    }

    @Test
    fun `decoder-unsupported with a working snapshot falls through to snapshot frames`() = runTest {
        // The load-bearing case (D-10 / SC2): a decoder failure must NOT short-circuit to Terminal while
        // the snapshot rung is viable — it falls through IN-FEED and the snapshot frames flow.
        val attempt = H264Attempt { _ -> H264AttemptResult.FallThrough }
        val frames = mutableListOf<String>()
        val lower = WebcamFeed<String> { _, onFrame ->
            onFrame("SNAPSHOT-FRAME") // the working lower rung yields frames
            FeedOutcome.Transient     // …and behaves like a live snapshot poll (transient, keeps retrying)
        }

        val feed = compositeMedia3Feed(h264Attempt = attempt, lowerRung = lower)
        val outcome = feed.run(h264Cam) { frames += it }

        assertEquals("snapshot frames flowed through the composite (fell through in-feed)", listOf("SNAPSHOT-FRAME"), frames)
        assertEquals("the outcome is the lower rung's outcome, NOT a short-circuit Terminal", FeedOutcome.Transient, outcome)
    }

    @Test
    fun `decoder failure returns Terminal only when the lower rung is also dead`() = runTest {
        val attempt = H264Attempt { _ -> H264AttemptResult.FallThrough }
        // The lower rung is ALSO dead (no MJPEG, no reachable snapshot) → Terminal.
        val lower = WebcamFeed<String> { _, _ -> FeedOutcome.Terminal }

        val feed = compositeMedia3Feed(h264Attempt = attempt, lowerRung = lower)
        val outcome = feed.run(h264Cam) {}

        assertEquals("Terminal ONLY when H.264 AND the lower rungs are all exhausted", FeedOutcome.Terminal, outcome)
    }

    @Test
    fun `cancellation maps to Cancelled (clean exit WR-01)`() = runTest {
        val attempt = H264Attempt { _ -> H264AttemptResult.Cancelled }
        val lower = WebcamFeed<String> { _, _ -> FeedOutcome.Transient }

        val feed = compositeMedia3Feed(h264Attempt = attempt, lowerRung = lower)
        val outcome = feed.run(h264Cam) {}

        assertEquals(FeedOutcome.Cancelled, outcome)
    }
}
