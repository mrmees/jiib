# Phase 21 — D-02 Spike Result (Native H.264 / Media3 over MediaMTX)

**Measured:** 2026-06-08 on **flox** (genuine Nexus 7 2013 / Adreno 320 / APQ8064 / LineageOS 18.1 / API 30 / `armeabi-v7a`), throwaway `SpikeActivity` (commit `fc7e854`), driven over adb against **Ender 5 Plus** (192.168.1.120, ravens-perch MediaMTX, webcam path `3`).
**Spike harness:** `app/src/main/java/works/mees/dinghy/spike/SpikeActivity.kt` — ExoPlayer 1.10.1, raw `SurfaceView`, RTSP via `RtspMediaSource.Factory().setForceUseRtpTcp(true)`, HLS via `HlsMediaSource.Factory(DefaultHttpDataSource.Factory())`. **Throwaway — deleted in plan 21-04.**
**Gates:** D-02 (the gating measurement) · D-04 (RTSP-vs-HLS lead) · D-05 (≤~1–2 s glass-to-glass bar) · D-03 (WebRTC escape trigger) · the SurfaceView-cutout owner aesthetic call (Pitfall 6).

---

## Pass/fail clean readout (flox, LineageOS 18.1 / API 30)

```
SPIKE RESULT (flox, LineageOS 18.1 / API 30, Adreno 320 / armeabi-v7a):
  Ender 5 Plus (RPi4, ravens-perch MediaMTX, path 3):
      RTSP (rtsp://192.168.1.120:8554/3, forced TCP):
          PLAYS + renders live video (real picture confirmed via screencap).
          Startup BUFFERING→READY: ~2.3 s (repeatable across 3 launches).
          No 461 Unsupported-Transport. No PlaybackException. No decoder-init error.
          videoFormat: codecs=avc1.42C01F, mime=video/avc, 640x480.
          App-process CPU steady-state: ~0% (codec offloaded to the HW block; per dumpsys cpuinfo).
      HLS (http://192.168.1.120:8888/3/index.m3u8):
          PLAYS + renders live video (real picture confirmed via screencap).
          Startup BUFFERING→READY: ~5.0 s (HLS segment download adds latency vs RTSP).
          Same decoder, same 640x480 avc1. Benign OMXNodeInstance UnsupportedIndex/
          NotImplemented extension-probe noise during init (NOT playback errors — reached
          READY and rendered).
  Ender 3 (RockPro64, crowsnest MediaMTX): NOT separately measured on-device this session
      (see "E3 honesty note" below).
  Decoder: OMX.qcom.video.decoder.avc  [HARDWARE — qcom OMX-VDEC engaged, NOT the
           OMX.google.* software fallback]  ← same HW decoder on BOTH RTSP and HLS.
  SPS/PPS in RTSP SDP fmtp: PRESENT (inferred definitively — see "SPS/PPS finding" below).
  crowsnest stream_url shape matched the :8889/<path>/ derive convention? confirmed at
           Task-1 time (paths 1/2/4); live on-device latency not re-captured this session (A2).
  CPU steady-state: RTSP ~0% (HW-offloaded)  HLS ~0% (HW-offloaded).
  APK delta (armeabi-v7a, R8): NOT separately captured this session (see "APK-size note").
  Latency: owner eyeball sign-off "fine" on the live RTSP feed (method note below) —
           NOT formal clock-photo medians.
  DECISION: lead = RTSP · fallback = HLS · WebRTC escape needed? N (D-03) · cutout = square
            (video surface unclipped; BUT 21-04 MUST overlay required controls/pills z-ordered
            above the SurfaceView — spike was chrome-less by design)
```

---

## Findings (what plans 21-03 / 21-04 consume)

### Decoder — HARDWARE confirmed (CAM-10 / Adreno 320 decode question SETTLED)
The selected MediaCodec is **`OMX.qcom.video.decoder.avc`** — the Qualcomm OMX-VDEC hardware path, NOT the `OMX.google.*` software fallback — on **both** RTSP and HLS. App-process CPU during steady-state playback measured **~0%** (per `dumpsys cpuinfo`), consistent with decode fully offloaded to the hardware block. The "does decode stay in hardware on the real Adreno 320 for 640×480 avc1?" spike question (RESEARCH § Adreno 320 Decode) is **answered YES** on this silicon.

### SPS/PPS in RTSP SDP fmtp — PRESENT (inferred definitively)
RESEARCH Pitfall 1 / the SPS/PPS-in-fmtp Open Question is **RESOLVED in MediaMTX's favour on this stack.** The RTSP `prepare()` decoded to a rendered frame with **zero** decoder-init exception — the plan's stated "absence signal" (a source/decoder-init error *before the first frame*) **never fired**. Per the RESEARCH contract (a decoder-init `PlaybackException` on RTSP prepare with no frames = SPS/PPS-absent), the absence of that error with a successfully rendered frame is the definitive PRESENT signal: MediaMTX's RTSP SDP carries the parameter sets (`sprop-parameter-sets`) on this device. RTSP can lead; HLS is not forced to be the leader by any SDP gap.

### Transport — no 461, forced TCP holds
The `setForceUseRtpTcp(true)` path (CAM-16) cleanly defeated the documented "461 Unsupported Transport" failure mode (RESEARCH Pitfall 1 / Open Question): no 461, no transport `PlaybackException`. MediaMTX's TCP-only RTSP plays over interleaved RTP/TCP as designed.

### Startup latency — RTSP wins the lead
RTSP reaches BUFFERING→READY in **~2.3 s** (repeatable across 3 launches); HLS in **~5.0 s** (segment-download tax). RTSP is the lower-latency transport on this stack — the **lead**. HLS plays the same HW-decoded H.264 and is retained as the **fallback** rung (D-04).

### Glass-to-glass latency — owner eyeball sign-off (method recorded honestly)
The D-05 ~1–2 s bar was **accepted via the owner eyeballing the live RTSP feed on flox** ("fine"), **NOT** via the formal clock-on-camera photo-median method described in RESEARCH § glass-to-glass. Recorded here as the actual method used so a future reader does not mistake "fine" for a measured median. The bar is met to the owner's satisfaction for the lead transport; the D-03 WebRTC escape is **NOT** triggered.

---

## Honesty notes (do NOT treat absent numbers as measured)

### E3 (Ender 3 / RockPro64, crowsnest MediaMTX) — NOT separately measured on-device this session
The `:8889/<path>/` → `:8554`(RTSP)/`:8888`(HLS) derive convention was **confirmed to hold on crowsnest at Task-1 time** (paths 1/2/4 resolved). Live on-device latency/decode against the E3 was **not separately captured** this session. **RTSP lead is expected to hold** on the E3 (same MediaMTX / same H.264 baseline-no-B-frame stack), but this is an expectation, not a measurement. Plan 21-05 (the load-bearing on-device UAT, D-08) MUST measure both printers; this spike settled the E5 and the design gate, not the E3 number. **No E3 latency figure is invented here.**

### APK-size delta (RTSP-only vs +HLS) — NOT separately captured this session
Both `media3-exoplayer-rtsp` and `media3-exoplayer-hls` are already wired into the build (plan 21-01). A discrete RTSP-only-vs-+HLS armeabi-v7a R8 footprint delta was **not captured** this session. The footprint *win vs WebRTC* (no bundled `libwebrtc` `.so`; Media3 is pure Java/Kotlin over the platform MediaCodec) is documented in `research/webrtc-vs-media3-feasibility.md` and the RESEARCH § APK/ABI note. **No APK KB number is fabricated here.**

---

## ⚠ CONSUMABLE REQUIREMENT FOR PLAN 21-04 — overlay controls above the SurfaceView

The owner's cutout call is **square corners** for the H.264 rung (the **video surface itself stays unclipped** — no rounded-mask chrome on the SurfaceView). BUT the SurfaceView **punches through the view hierarchy** (RESEARCH Pitfall 6), so in the bare throwaway spike the existing webcam **overlay controls were entirely absent** — there was no camera picker, no rung pills/badges, nothing on top of the video.

**Plan 21-04 MUST render the required webcam overlay controls — the camera picker, the rung pills/badges, and any other required buttons — as a SIBLING layer Z-ORDERED ABOVE the SurfaceView** (a Compose overlay / higher view layer), so they are NOT hidden behind the video. "Square corners" applies ONLY to the video surface; the **control chrome must still be drawn on top.** This is an owner-raised, non-negotiable requirement for the shipped rung — the chrome-less spike is not the shipped UX.

---

DECISION: lead = RTSP · fallback = HLS · WebRTC escape needed? N (D-03) · cutout = square (video surface unclipped; BUT 21-04 MUST overlay required controls/pills z-ordered above the SurfaceView — spike was chrome-less by design)

---
*Phase: 21-webrtc-camera-streaming · Plan 21-02 (D-02 spike) · Recorded 2026-06-08*
