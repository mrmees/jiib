package works.mees.dinghy.webcam

import org.junit.Assert.fail
import org.junit.Test

/**
 * WAVE-0 RED SCAFFOLD (Phase 21, CAM-16) — fails until plan 21-04 builds the RtspMediaSource.Factory.
 *
 * Behavior this file pins (converted to live assertions by 21-04):
 *  - The RTSP media source is built with `setForceUseRtpTcp(true)` — MediaMTX serves RTP over TCP only,
 *    so the default UDP attempt must be disabled or the stream never starts.
 *  - The media3 1.10.1 signature is `setForceUseRtpTcp(forceUseRtpTcp: Boolean)` — a BOOLEAN argument,
 *    NOT the older no-arg `setForceUseRtpTcp()`. 21-04 asserts the factory/config is built with the
 *    boolean-true form (asserting on the CONFIG, not on live playback).
 *
 * COMPILE DISCIPLINE (project wave-0 rule): references NO unbuilt symbol and does NOT import
 * androidx.media3.exoplayer.rtsp.RtspMediaSource — that import + the boolean-arg assertion land in 21-04,
 * which replaces these `fail(...)` bodies with a real factory-config assertion.
 */
class RtspSourceConfigTest {

    @Test
    fun `rtsp source is built with setForceUseRtpTcp true (MediaMTX is TCP-only)`() {
        fail("not yet implemented — 21-04: assert RtspMediaSource.Factory is built with setForceUseRtpTcp(true)")
    }

    @Test
    fun `setForceUseRtpTcp is called with the boolean-true arg, not the no-arg form`() {
        fail("not yet implemented — 21-04: assert the boolean-arg setForceUseRtpTcp(true) signature is used (media3 1.10.1)")
    }
}
