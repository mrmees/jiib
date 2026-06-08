package works.mees.dinghy.spike

import android.media.MediaCodecList
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource

/**
 * THROWAWAY D-02 spike harness (CAM-10) — NOT a shipped surface. Deleted in plan 21-04.
 *
 * A bare temporary Activity that builds an [ExoPlayer] on the MAIN thread (ExoPlayer is
 * single-threaded by contract — RESEARCH Pitfall 3), plays a printer camera over Media3 RTSP
 * (forced TCP) OR HLS into a raw [SurfaceView] (NO PlayerView / media3-ui — footprint discipline),
 * and logs the selected MediaCodec decoder name + any [PlaybackException] verbatim so the on-device
 * spike (plan 21-02 Task 2) can record the D-02 readout (glass-to-glass latency, OMX.qcom-vs-OMX.google
 * decode, SPS/PPS-in-fmtp signal).
 *
 * It deliberately BYPASSES the holder / WebcamFeed seam — this is throwaway measurement code, not the
 * shipped H.264 rung ([works.mees.dinghy.ui.webcam.Media3Feed] / Media3SurfaceHost are the shipped surfaces).
 *
 * Launch contract (derived from the live Moonraker /server/webcams/list at spike time, 2026-06-08):
 *   # Ender 5 Plus (192.168.1.120), playstation_eye, MediaMTX path "3":
 *   adb shell am start -n works.mees.dinghy/.spike.SpikeActivity --es url "rtsp://192.168.1.120:8554/3" --es transport rtsp
 *   adb shell am start -n works.mees.dinghy/.spike.SpikeActivity --es url "http://192.168.1.120:8888/3/index.m3u8" --es transport hls
 *   # Ender 3 Pro (192.168.1.121), webrtc-mediamtx path "1" (crowsnest-class):
 *   adb shell am start -n works.mees.dinghy/.spike.SpikeActivity --es url "rtsp://192.168.1.121:8554/1" --es transport rtsp
 *   adb shell am start -n works.mees.dinghy/.spike.SpikeActivity --es url "http://192.168.1.121:8888/1/index.m3u8" --es transport hls
 *
 * If no `url` extra is supplied, falls back to the E5+ playstation_eye RTSP default below.
 */
@OptIn(UnstableApi::class)
class SpikeActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private var surfaceView: SurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the screen on for the full glass-to-glass measurement run.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Raw SurfaceView host — no PlayerView, no media3-ui (footprint discipline).
        val sv = SurfaceView(this)
        surfaceView = sv
        setContentView(
            FrameLayout(this).apply {
                addView(
                    sv,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            },
        )

        // Hardcoded-or-intent-supplied stream URL + transport toggle.
        val url = intent?.getStringExtra("url")?.trim()?.takeIf { it.isNotBlank() }
            ?: DEFAULT_RTSP_URL
        val transport = intent?.getStringExtra("transport")?.trim()?.lowercase()
            ?: if (url.startsWith("rtsp://", ignoreCase = true)) "rtsp" else "hls"

        Log.i(TAG, "SPIKE start: transport=$transport url=$url")
        logAvailableH264Decoders()

        // ExoPlayer is single-threaded by contract — built + driven on the MAIN thread (RESEARCH Pitfall 3).
        val exo = ExoPlayer.Builder(this).build()
        player = exo

        val source = when (transport) {
            "hls" -> HlsMediaSource.Factory(DefaultHttpDataSource.Factory())
                .createMediaSource(MediaItem.fromUri(url))
            else -> RtspMediaSource.Factory()
                // Media3 1.10.1 takes a BOOLEAN (setForceUseRtpTcp(forceUseRtpTcp: Boolean)), NOT a no-arg
                // call. MediaMTX RTSP is TCP-only; UDP-first attempts hit "461 Unsupported Transport"
                // (RESEARCH Pitfall 1). Force interleaved RTP/TCP.
                .setForceUseRtpTcp(true)
                .setTimeoutMs(8000)
                .setDebugLoggingEnabled(true) // dump the RTSP SDP to logcat for the SPS/PPS-in-fmtp check
                .createMediaSource(MediaItem.fromUri(url))
        }

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val name = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "?$playbackState"
                }
                Log.i(TAG, "SPIKE playbackState=$name")
                if (playbackState == Player.STATE_READY) {
                    // Log the SELECTED decoder name — OMX.qcom.* (hardware) vs OMX.google.* (software).
                    logSelectedDecoder(exo)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // A source/decoder-init PlaybackException on RTSP prepare() with no frames is the
                // SPS/PPS-in-fmtp-absent signal (RESEARCH Pitfall 1/2). Log it verbatim.
                Log.e(
                    TAG,
                    "SPIKE PlaybackException: errorCode=${error.errorCode} " +
                        "(${error.errorCodeName}) message=${error.message}",
                    error,
                )
            }
        })

        exo.setVideoSurfaceView(sv)
        exo.setMediaSource(source)
        exo.playWhenReady = true
        exo.prepare()
    }

    /**
     * Logs the format-selected video decoder once playback is READY. The renderer's chosen
     * [androidx.media3.common.Format.sampleMimeType] tells us H.264 was negotiated; the actual
     * codec name is read from the active codec info if available.
     */
    private fun logSelectedDecoder(exo: ExoPlayer) {
        val videoFormat = exo.videoFormat
        Log.i(
            TAG,
            "SPIKE videoFormat: codecs=${videoFormat?.codecs} mime=${videoFormat?.sampleMimeType} " +
                "w=${videoFormat?.width} h=${videoFormat?.height}",
        )
        // Enumerate the platform H.264 decoders so the readout records OMX.qcom (HW) vs OMX.google (SW).
        logAvailableH264Decoders()
    }

    /** Enumerate every platform H.264 decoder — confirms an OMX.qcom.* hardware decoder exists/was used. */
    private fun logAvailableH264Decoders() {
        runCatching {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            list.codecInfos
                .filter { !it.isEncoder }
                .filter { info -> info.supportedTypes.any { it.equals("video/avc", ignoreCase = true) } }
                .forEach { info ->
                    val hw = !info.name.startsWith("OMX.google.", ignoreCase = true) &&
                        !info.name.contains(".sw.", ignoreCase = true)
                    Log.i(TAG, "SPIKE H264 decoder: ${info.name} [${if (hw) "HW?" else "SW"}]")
                }
        }.onFailure { Log.w(TAG, "SPIKE decoder enumeration failed", it) }
    }

    override fun onDestroy() {
        // Idempotent leak-free teardown (WR-01 discipline): detach the surface BEFORE release().
        // A held MediaCodec keeps the device hot and starves the 2GB Adreno-320 floor.
        player?.let { p ->
            p.setVideoSurfaceView(null)
            p.release()
        }
        player = null
        surfaceView = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DinghySpike"

        // E5+ playstation_eye (MediaMTX path "3") RTSP-over-TCP default when no `url` extra is supplied.
        private const val DEFAULT_RTSP_URL = "rtsp://192.168.1.120:8554/3"
    }
}
