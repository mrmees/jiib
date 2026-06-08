package works.mees.dinghy.webcam

import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import org.junit.Assert.assertNotNull
import org.junit.Test
import works.mees.dinghy.ui.webcam.buildHlsMediaSourceFactory
import works.mees.dinghy.ui.webcam.buildRtspMediaSourceFactory

/**
 * Typed assertions (Phase 21, CAM-16) — REPLACES the plan-21-01 runtime-RED scaffold.
 *
 * The H.264 rung's two transport source factories must BOTH construct without error:
 *  - The RTSP factory is built with `setForceUseRtpTcp(true)` — MediaMTX serves RTP over TCP only, so the
 *    default UDP attempt must be disabled or the stream never starts (the documented "461 Unsupported
 *    Transport" failure). The media3 1.10.1 signature is `setForceUseRtpTcp(forceUseRtpTcp: Boolean)` —
 *    a BOOLEAN argument, NOT the older no-arg `setForceUseRtpTcp()`.
 *  - The HLS factory (the D-04 NON-LEAD robustness fallback) is at least constructed/reachable even though
 *    only the recorded LEAD (RTSP) is UAT-exercised.
 *
 * These assert on the CONFIG (the factory builds), not on live playback (an on-device property, plan 21-05).
 * The `setForceUseRtpTcp(true)` boolean-arg call lives in [buildRtspMediaSourceFactory] (grep-asserted in
 * Media3Feed.kt by the plan); here we prove both factories construct.
 */
class RtspSourceConfigTest {

    @Test
    fun `rtsp source factory is built with setForceUseRtpTcp true (MediaMTX is TCP-only)`() {
        // Constructs the RTSP factory with the boolean-true forced-TCP form. If the boolean-arg signature
        // were wrong (e.g. the no-arg form on this media3 version) this would not compile / not construct.
        val factory: RtspMediaSource.Factory = buildRtspMediaSourceFactory()
        assertNotNull("the RTSP forced-TCP source factory constructs", factory)
    }

    @Test
    fun `hls source factory constructs (the D-04 non-lead robustness fallback is wired)`() {
        val factory: HlsMediaSource.Factory = buildHlsMediaSourceFactory()
        assertNotNull("the HLS fallback source factory constructs", factory)
    }
}
