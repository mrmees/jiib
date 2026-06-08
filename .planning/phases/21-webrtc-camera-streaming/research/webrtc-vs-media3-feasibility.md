# Phase 20 Camera Streaming - Feasibility Research

Source project: `/mnt/e/claude/personal/github/dinghy-display`

This is **research**, not a staging design doc. Phase 20 is research-gated: the roadmap assumes a
WebRTC client, but that assumption was never validated against the target hardware. This report
tests it so a future staging/discuss pass can stage the RIGHT approach. No design decisions are
locked here.

Method: deep-research harness — 5 search angles, 23 sources fetched, 91 claims extracted, top 25
adversarially verified (3-vote, 2/3 to kill). 20 confirmed, 5 killed. Access date 2026-06-06.

## Verdict

**Both paths are viable on the Nexus 7 2013. The evidence tips toward a Media3/ExoPlayer
H.264-over-RTSP-or-fMP4 approach as the recommended PRIMARY, with WebRTC as a heavier,
defensible fallback.**

The roadmap's WebRTC assumption is not wrong — a real, maintained client exists and the tracks
are hardware-decodable H.264 — but it is **heavier than necessary** for this hardware, because the
exact same H.264 source is reachable over lighter protocols that AndroidX Media3 can hardware
decode with a far smaller footprint and no native libwebrtc bundle.

The one thing the research could NOT settle: **measured glass-to-glass latency on this specific
silicon.** That requires an on-device spike (see Open Questions). The recommendation is "Media3
first" on footprint + integration-simplicity grounds; the spike confirms whether its latency is
acceptable before committing.

## Why Media3 over WebRTC (the core argument)

The whole case rests on three verified facts lining up:

1. **The cameras emit H.264 over WebRTC, AND expose the same H.264 over non-WebRTC outputs.**
   - go2rtc WebRTC = H264/H265 only (no VP8 output). Also exposes: MSE/MP4 progressive
     (`/api/stream.mp4`), HLS + HLS/fMP4 (`/api/stream.m3u8`, `&mp4` for fMP4), RTSP, MJPEG,
     JPEG snapshot.
   - camera-streamer WebRTC = H264-only. Also runs an RTSP server via live555 at
     `rtsp://<ip>:8554/stream.h264` (with `--rtsp-port`).
   - All H.264, all ExoPlayer-compatible.
2. **The Adreno 320 / APQ8064 hardware-decodes H.264 up to 1080p** — printer cameras run
   640×480–1280×720, comfortably under the ceiling, so an H.264 live view stays in hardware
   decode regardless of which transport carries it.
3. **Media3 is modular and small; libwebrtc is a mandatory multi-ABI native bundle.** Media3
   lets you include only the source module you need (`media3-exoplayer` + `-rtsp` OR `-hls`,
   omit the rest) and shrinks ~40% under R8. The WebRTC artifact is a full libwebrtc AAR
   bundling `.so` files for four ABIs (arm64-v8a, armeabi-v7a, x86, x86_64) — the dominant
   footprint cost in a size-constrained sideloaded APK.

Net: same hardware-decoded H.264 picture, much smaller APK, simpler integration (a player vs a
PeerConnection + signaling), no native blob. That is why Media3 is the recommended primary.

## Findings (verified)

### WebRTC client viability — Q1
- **A maintained native WebRTC client exists; minSdk 23 is no blocker.** `io.github.webrtc-sdk:android`
  is at `144.7559.05` (Chromium M144, ~Apr 30 2026), live on Maven Central, exposes the standard
  `org.webrtc` namespace. Stream's `io.getstream:stream-webrtc-android` (1.3.x) is an independent
  maintained prebuilt libwebrtc (Google stopped publishing official `org.webrtc` Android binaries).
  Both support minSdk 21 < the app's 23. *(confidence: high, 3-0)*
- **The artifact is a full native libwebrtc AAR** (bundled `.so` for four ABIs), so native-library
  size is the dominant APK cost. A size-optimized `android-prefixed-stripped` variant exists
  (removes software codecs, optimizes for size, forces hardware decode — acceptable here since the
  sources are H.264). *No verified MB figure, no measured CPU/GPU/latency on the Adreno 320.*
  *(high, 3-0)*

### Hardware decode floor — Q2
- **Real hardware H.264 decoder capped at 1080p**, above all in-spec printer-camera resolutions, so
  H.264 stays hardware-decoded. (GStreamer maintainer-attributed forum thread + spec-site
  corroboration.) **VP8/VP9/H.265 hardware decode is NOT confirmed on this SoC** — which only
  reinforces "use H.264 sources." *(high, 2-1)*

### Camera stack formats + codecs — Q3
- **All three stacks carry H.264 (not VP8) over WebRTC**, and Fluidd/Mainsail both enumerate
  camera-streamer, go2rtc, and MediaMTX as WebRTC backends. Any WebRTC track from these sources is
  hardware-decodable H.264. *(high, 3-0)*
- **The same sources expose non-WebRTC H.264 outputs (RTSP, MSE/fMP4, HLS/fMP4)** that Media3 can
  hardware-decode — the technical basis for avoiding libwebrtc entirely. *(high, 3-0)*

### WHEP signaling — Q4
- **WHEP against go2rtc is a single HTTP POST of an SDP offer to `/api/webrtc?src=<stream>`**
  (default port 1984), returning `201 Created` + `application/sdp` answer + `Location` header.
  Integration: pull in `org.webrtc`, create a PeerConnection, make an offer, POST it, apply the
  answer — no separate signaling SDK. (One real-world client observed a ~5s SDP-answer delay — that
  is signaling setup, NOT steady-state media latency.) *(high, 3-0)*

### Media3 footprint — Q5
- **Media3 is modular and R8-shrinks ~40%**; include only `media3-exoplayer` + one source module.
  Sharp contrast with the WebRTC mandatory multi-ABI `.so` bundle. *(high, 3-0)*

### Moonraker webcams schema — Q6
- **`/server/webcams/list` returns a `webcams[]` array** with `stream_url`, `snapshot_url` (empty
  if unsupported), and `service`. **`service` is a free-form string — no enforced enum** (Moonraker
  explicitly does not specify allowed values). Detect stream type **heuristically**: de-facto
  Mainsail/Fluidd `service` values are `webrtc-go2rtc`, `webrtc-camerastreamer`, `webrtc-mediamtx`,
  `mjpegstreamer`, `mjpegstreamer-adaptive`, `hlsstream`. Client logic: `service` startsWith
  `webrtc` → WebRTC variant; else fall back to `stream_url` scheme/path. *(high, 2-1)*

## Caveats (read before trusting the verdict)

- **No verified claim measures the two deciding numbers:** (1) the actual MB libwebrtc's `.so`
  payload adds for this app's ABIs, and (2) steady-state glass-to-glass latency for WebRTC vs RTSP
  vs fMP4/MSE on the Adreno 320. The verdict leans on footprint + integration simplicity, NOT a
  measured latency win.
- **The ~5s go2rtc figure is signaling-setup latency, not media latency** — do not read it as
  steady-state.
- **A Media3-RTSP "2-3s latency" claim and an ExoPlayer-RTSP "H.264-only / SPS-PPS-in-fmtp" claim
  were both REFUTED in verification** — so this report deliberately does NOT assert a specific
  Media3 RTSP latency number or codec limitation. Treat ExoPlayer RTSP latency + codec/SDP support
  as needing on-device confirmation.
- **camera-streamer's WebRTC is server-side Raspberry-Pi-only**, and its HLS/remuxed-MP4 endpoint
  claims were REFUTED — for camera-streamer rely on its confirmed **RTSP (live555)** path, not
  HLS/fMP4.
- **Time-sensitivity:** the WebRTC artifact version and go2rtc's "H265 on Chrome 136+" note are
  mid-2026 current and will drift; the APQ8064 hardware-decode ceiling is fixed silicon.
- The 1080p decode finding rests on one maintainer-attributed forum thread + spec sites, not a
  Qualcomm datasheet.

## Open Questions (the on-device spike must answer)

1. **Measured glass-to-glass latency + CPU/GPU load** for each path (org.webrtc, Media3 RTSP,
   Media3 fMP4/MSE, Media3 LL-HLS) on the Nexus 7 2013 at 720p H.264. *This is the only thing that
   empirically settles WebRTC vs Media3 — no verified claim pins it down.*
2. **Actual APK MB delta**: libwebrtc native `.so` (stripped vs standard, chosen ABIs) vs
   `media3-exoplayer` + a single source module.
3. **Does Media3's RTSP module reliably play go2rtc's and camera-streamer's H.264 RTSP** on this
   device (the codec/SDP-constraint claim was refuted → test directly), and is its TCP/UDP
   interleaving stable on LineageOS API 30?
4. **Is the existing MJPEG/snapshot fallback failing because the cameras are H.264-only (no MJPEG
   track)?** If so, the real fix is decoding H.264 — which validates the pivot toward EITHER
   WebRTC or Media3 H.264 regardless of which wins.

## Refuted claims (killed in verification — do NOT rely on these)

- "Adreno 320 fails to hardware-decode H.264 at 2560×720, falls to software." *(1-2 — only bounds
  ABOVE-1080p streams, irrelevant to in-spec resolutions)*
- "camera-streamer serves remuxed MP4/MKV + HLS/MP4 `/video` endpoint." *(1-2 — use its RTSP path
  instead)*
- "Media3 RTSP shows 2-3s latency out of the box." *(0-3)*
- "Media3 RTSP supports only H.264 and needs SPS/PPS in fmtp." *(1-2 — test codec support
  on-device rather than assuming this limit)*
- "Moonraker `service` is the only describing field with no way to distinguish WebRTC." *(1-2 —
  `stream_url` + de-facto `service` convention together suffice)*

## Recommended path into Phase 20

1. **Spike first (throwaway).** Before any staging notes, run an on-device spike on the real
   Nexus 7 against both printers: load go2rtc/camera-streamer H.264 over Media3 RTSP (and fMP4/MSE
   for go2rtc), measure latency + load + APK delta. Answers Open Questions 1-4. This is the
   gate the roadmap demanded.
2. **If Media3 latency is acceptable** (likely): stage Phase 20 around a Media3 H.264 player that
   EXTENDS the Phase-10 rung ladder — add RTSP/fMP4 rungs above the existing MJPEG/snapshot rungs,
   selected via the `/server/webcams/list` `service` heuristic. No libwebrtc.
3. **If Media3 latency is NOT acceptable**: fall back to the WebRTC path (`io.github.webrtc-sdk:android`,
   stripped variant), WHEP POST to go2rtc `/api/webrtc`. Heavier APK, but proven H.264 hardware
   decode.
4. **Either way**, keep the feature perf-gated / amber-flagged like the existing camera BETA, and
   release the PeerConnection/player cleanly on screen exit (no leaked codec).

## Sources (primary)

- WebRTC client: github.com/webrtc-sdk/android · central.sonatype.com/artifact/io.github.webrtc-sdk/android · github.com/GetStream/webrtc-android
- Hardware decode: gstreamer-devel.narkive.com/ms9g2RMW (maintainer thread) · phonedb APQ8064 specs · en.wikipedia.org/wiki/Adreno
- Camera stacks: github.com/AlexxIT/go2rtc/blob/master/api/README.md · go2rtc.org/internal/webrtc · github.com/ayufan/camera-streamer/blob/main/docs/streaming.md · github.com/AlexxIT/go2rtc/issues/1392
- Media3: developer.android.com/media/media3/exoplayer/rtsp · .../shrinking · github.com/androidx/media/issues/1179
- Moonraker/UI: moonraker.readthedocs.io/en/latest/external_api/webcams · docs.fluidd.xyz/features/cameras · docs.mainsail.xyz/settings/webcams
