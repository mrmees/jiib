# Phase 21: Native H.264 Camera Streaming (MediaMTX) - Research

**Researched:** 2026-06-08 (force-refresh; supersedes the 2026-06-08 initial research of the same file)
**Domain:** AndroidX Media3/ExoPlayer H.264 transport (RTSP/HLS) over a MediaMTX backend, extending the Phase-10 webcam rung ladder, perf-floored to the Adreno 320.
**Confidence:** HIGH on stack/versions/integration shape. **The single biggest change vs the prior research:** the SPS/PPS-in-fmtp question — previously flagged as "the single highest-risk unknown" — is now **largely settled in MediaMTX's favour** (documented + a verbatim SDP example), and the #3121 version attribution in the prior research was **factually inverted** and is corrected here. Latency and Adreno-320 hardware-decode remain the genuine spike-gated unknowns (D-02 stands).

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01: Media3/ExoPlayer H.264 is the primary transport, NOT WebRTC.** Same H.264 over lighter transports (RTSP/HLS) that Media3 hardware-decodes; no `libwebrtc` AAR. Footprint win proven; latency win NOT — hence the spike.
- **D-02: A throwaway on-device spike is Phase 21's FIRST plan.** On the real Nexus 7 (flox) against both printers; measures glass-to-glass latency, CPU/GPU, APK-size delta for Media3-RTSP vs Media3-HLS (WebRTC as reference). Downstream feature design is provisional until the spike reports. Spike code is throwaway (not shipped).
- **D-03: WebRTC is built ONLY if the spike fails the latency bar.** Default: Media3 rung(s) only; WebRTC/WHEP path documented as designed-but-unbuilt. `libwebrtc` stays OUT unless the spike shows Media3-RTSP can't meet D-05.
- **D-04: Spike decides RTSP-vs-HLS lead.** No predetermined primary transport; measured latency picks the leader; the other becomes a fallback rung.
- **D-05: Latency bar = ~1–2 s glass-to-glass (tight).** Usable while jogging. RTSP-over-TCP with the encoder config should clear it; plain HLS likely will not (LL-HLS maybe). The spike's pass/fail + the D-03 trigger.
- **D-06: Exactly two providers — both MediaMTX-backed H.264.** Ravens Perch (PRIMARY, dev-controlled, MediaMTX) + crowsnest (SECONDARY, MediaMTX, output metadata not changeable).
- **D-07: Camera codec is H.264 only, decode-friendly.** baseline/constrained-baseline, level 3.1, no B-frames (`-bf 0`), 1 s GOP, 640×480–1280×720 @ 5–30 fps. Under the Adreno 320's 1080p H.264 ceiling. **[VERIFIED: ravens-perch `stream_manager.py` lines 694-728 — libx264 path is `-tune zerolatency -profile:v baseline -bf 0 -g <framerate>`.]**
- **D-08: "Done" = the ~1–2 s bar met on BOTH printers, on-device, for the leading transport, with clean codec/player release on screen exit.** Verified on flox against both SBCs.
- **D-09: H.264 becomes the new top/preferred rung.** Order best→worst: H.264 (Media3) → MJPEG → Snapshot → Unsupported.
- **D-10: Detection = `service`/scheme heuristic SELECTS the rung, the decoder VERIFIES it.** `service` startsWith `webrtc` and/or URL scheme selects; ExoPlayer either decodes or errors → falls to the next rung. Preserves T-10-06 ("decoder is the authority, `service` is a hint only").
- **D-11: Moonraker's advertised `stream_url` points at WebRTC `:8889`; Media3 needs RTSP `:8554` / HLS `:8888`.** App must resolve a Media3-playable URL from a WebRTC-advertising entry.
- **D-12: Resolution policy = explicit-if-present, else derive-from-convention.** Explicit: prefer ravens-perch `extra_data` RTSP/HLS tags when present. Derive (fallback): swap `:8889`→`:8554`(RTSP)/`:8888`(HLS), reuse `/<camera_id>` path (path = `id.replace(' ','_').lower()`).
- **D-13: ravens-perch KEEPS its standard Moonraker fields unchanged.** `stream_url` stays WebRTC `:8889/<id>/`, `service` stays `webrtc-mediamtx` so browsers keep working. Native RTSP/HLS info is additive `extra_data`.
- **D-14: Cross-repo dependency is non-blocking.** The `extra_data` tag work is a separate ravens-perch change; dinghy is NOT blocked (derive fallback covers both targets today).
- **D-20: No dedicated fixes for the 3 deferred webcam todos; fold into Phase-21 UAT as verification checks.** (no-crash on MediaMTX cams, drawer-tile gating, per-printer cam selection / WR-03 null-key).

### Claude's Discretion
- Media3 module selection / version pinning / R8 keep rules — **this research recommends the exact set below.**
- Exact spike harness shape (throwaway Activity/screen; how latency is measured on-device).
- Multi-camera UX inherits Phase-10's `preferred_cam` selection; no new decision.

### Deferred Ideas (OUT OF SCOPE)
- **WebRTC/WHEP client** (`io.github.webrtc-sdk:android`, stripped variant, POST SDP to MediaMTX `/api/webrtc`) — built ONLY if the spike fails D-05 (D-03).
- **H.265/HEVC** — not confirmed hardware-decodable on APQ8064; cameras are H.264 anyway. Out.
- **WR-02 idle `seedTheme` re-seed** — unrelated to webcams; Phase-22.
- **Roadmap phase rename** ("WebRTC Camera Streaming" → "Native H.264 Camera Streaming (MediaMTX)") — already applied in STATE.md (2026-06-08).
</user_constraints>

<phase_requirements>
## Phase Requirements

Req IDs are coined at planning (continuing the CAM-01 lineage). CAM-* extension set + the research that enables each:

| ID | Description | Research Support |
|----|-------------|------------------|
| CAM-10 | Throwaway on-device spike (D-02): Media3-RTSP vs Media3-HLS glass-to-glass latency + CPU/GPU + APK delta on flox against both printers | § Spike Harness; § Adreno 320 Decode; § Architecture Patterns |
| CAM-11 | Media3 H.264 rung added as the new top rung above MJPEG/Snapshot (D-09) | § Standard Stack; § Rung-Ladder Integration; existing `Rung` enum |
| CAM-12 | Heuristic-selects / decoder-verifies rung routing (D-10), preserving T-10-06 | § Rung-Ladder Integration; § Pattern 2 |
| CAM-13 | URL resolution: explicit `extra_data` tag if present, else derive-from-convention port/path transform (D-11/D-12) | § URL Resolution — **incl. the name≠path landmine** |
| CAM-14 | ExoPlayer lifecycle: create→prepare→play→release with leak-free teardown mirroring WebcamHolder (D-08) | § ExoPlayer Lifecycle Contract; existing `WebcamHolder.cancel()` |
| CAM-15 | SurfaceView render surface hosted via AndroidView in the hybrid (ADR-0001), aspect-fit, token chrome reuse | § Surface Hosting; existing `WebcamViewHost`/`WebcamView` |
| CAM-16 | RTSP forced TCP interleaving (the MediaMTX stream is TCP-only / UDP-disabled) | § RTSP-over-TCP |
| CAM-17 | On-device UAT on both printers: latency met, no-frozen-frames, MJPEG/snapshot fallback intact, no-crash regression (D-08/D-20) | § Validation Architecture |

The planner refines IDs; this table maps the deliverables to the research.
</phase_requirements>

## Summary

The phase adds one new rung to the existing Phase-10 webcam ladder: a **Media3/ExoPlayer H.264 player** that decodes the same H.264 the printers' MediaMTX backends already emit over **RTSP (`:8554`, TCP)** or **HLS (`:8888`)**. The existing ladder (`Rung` enum, `WebcamProbe`, `WebcamUrl`, `WebcamHolder`, `WebcamView`/`WebcamViewHost`) is the surface being extended — the new rung sits *above* MJPEG/Snapshot, not a rewrite. The verified stack is **`androidx.media3:media3-exoplayer:1.10.1` + `media3-exoplayer-rtsp:1.10.1` (and `media3-exoplayer-hls:1.10.1` for the spike / D-04 fallback)** — all minSdk 23 (raised in 1.9.0, Dec 2025), all live on Google's Maven (HTTP 200 verified this session), RTSP module depends only on `media3-exoplayer` + `annotation:1.6.0` (tiny, POM-verified). **1.10.1 is still the latest stable** as of June 2026 (no 1.10.2/1.11 exists — re-verified against the Google Maven `group-index.xml`).

**Two material corrections / de-risks vs the prior research** (the reason this refresh exists):

1. **The #3121 version attribution was INVERTED in the prior research — corrected here.** Issue #3121 ("Corrupted RTSP stream after upgrading from 1.8.0" — visual artifacts on H.264/H.265 access units that span multiple RTP packets, reproduced specifically on **MediaMTX + ffmpeg H.264 over TCP RTSP**) was a **regression introduced in 1.9.0** and **fixed in 1.10.0** (March 26 2026). The prior research claimed the fix landed in 1.10.1 and that "1.10.0 would visibly corrupt." That is wrong: **1.10.0 already contains the fix**, and the corrupting versions were **1.9.0–1.9.4**. The corrected pin rationale: pin **≥1.10.0** to clear #3121; **1.10.1 is the current stable and the right pin.** `[CITED: developer.android.com/jetpack/androidx/releases/media3 — "Version 1.10.0" RTSP changelog bullet citing #3121]`. The plans already softened the language (commit 1fd8081: "≥1.10.1, confirm the fix is present rather than trust a specific point-release") — that hedge absorbs the error, but the inverted "do NOT pin 1.10.0, it corrupts" warning in the prior research/Pitfall 4 must NOT be carried forward.

2. **SPS/PPS-in-fmtp is now largely settled in MediaMTX's favour — the highest-risk unknown is downgraded.** The prior research called "does MediaMTX's RTSP SDP carry SPS/PPS in the fmtp attribute?" the *single highest-risk unknown* and said only the spike could settle it. New evidence: **MediaMTX documents and emits `sprop-parameter-sets` in its RTSP SDP fmtp** — a verbatim server SDP from the canonical "How to make it work with Android ExoPlayer?" discussion shows `a=fmtp:96 packetization-mode=1; sprop-parameter-sets=Z3oAHry0BQHtCAAA...,aM48gA==; profile-level-id=7A001E`. The real failure mode users actually hit is **not** missing SPS/PPS — it's **"461 Unsupported Transport"** because ExoPlayer defaults to UDP while MediaMTX disables UDP; **forcing TCP (`setForceUseRtpTcp(true)`) is the fix, which the plans already do (CAM-16).** `[CITED: github.com/bluenviron/mediamtx/discussions/783 + the androidx/media #2208 thread "MediaMTX supports the sprop-parameter-sets parameter, making it compatible with ExoPlayer"]`. This drops the SPS/PPS concern from "highest-risk, could flip the lead to HLS" to "low risk on the ravens-perch MediaMTX target; the spike confirms but is unlikely to fail on it."

The integration is otherwise a known quantity, confirmed against the live dinghy + ravens-perch source. The genuinely **unsettled, spike-gated** questions are now TWO (down from three): (1) **glass-to-glass latency** of Media3-RTSP-over-TCP on the actual Adreno 320 silicon (no verified source pins it; D-05 is the pass/fail); and (2) whether the **Adreno 320 OMX.qcom hardware H.264 decoder** initializes and stays in hardware for these streams. D-02 still stands — these are real on-device measurements, not assumptions.

**Primary recommendation:** Build the spike FIRST as a throwaway Activity (`media3-exoplayer` + `media3-exoplayer-rtsp` + `media3-exoplayer-hls`, raw `SurfaceView`, `setForceUseRtpTcp(true)`, optionally `setDebugLoggingEnabled(true)` to dump the RTSP SDP). Expect RTSP-over-TCP to play on the ravens-perch MediaMTX cam (SPS/PPS present, encoder ideal) — the open question is latency, not playability. Measure glass-to-glass with a millisecond-clock-on-camera method on flox against both printers; let D-04/D-05 decide RTSP-vs-HLS (or, if the bar is missed, trigger the D-03 WebRTC escape hatch). Derive-from-convention URL resolution is the only path that works today (ravens-perch passes NO `extra_data` yet — re-verified in source), and it must parse the path segment out of the WebRTC `stream_url`, NOT reconstruct from the webcam `name` (different identifiers — landmine below).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| H.264 decode | Android client (MediaCodec via Media3) | — | Native hardware decode on the tablet, not a browser/WebRTC stack |
| RTSP/HLS transport | Android client (Media3 source module) | Printer SBC (MediaMTX) | Media3 pulls the pipe directly; MediaMTX serves it |
| H.264 encode | Printer SBC (FFmpeg → MediaMTX) | — | Already done; encoder config is ideal (D-07, verified) — out of dinghy scope |
| Rung selection / detection | Android client (`selectsH264Rung` heuristic) | — | `service` is a hint; decoder verifies — T-10-06 boundary stays client-side |
| URL resolution (derive/explicit) | Android client (`WebcamUrl`) | Printer SBC (ravens-perch `extra_data`, future, non-blocking) | Derive covers both targets today; explicit tag is an additive SBC enhancement |
| Render surface | Android client (SurfaceView under AndroidView) | — | Hybrid ADR-0001; reuse `WebcamViewHost` hosting discipline |
| Lifecycle / leak-free release | Android client (`WebcamHolder` analog) | — | The codec + player MUST release on screen exit; mirror existing `cancel()` |
| Latency measurement | Spike (throwaway client Activity) | flox + both SBCs | Only on-device measurement settles latency (D-02/D-04/D-05) |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `androidx.media3:media3-exoplayer` | **1.10.1** | The ExoPlayer engine + MediaCodec video pipeline | `[VERIFIED: Google Maven]` POM HTTP 200 at `dl.google.com/.../media3-exoplayer/1.10.1`; latest stable (released 2026-05-12); minSdk 23 since 1.9.0 (2025-12-17) |
| `androidx.media3:media3-exoplayer-rtsp` | **1.10.1** | RTSP source module (the D-04/D-05 lead candidate) | `[VERIFIED: Google Maven]` POM HTTP 200 + POM-inspected this session: deps = `media3-exoplayer:1.10.1` (compile) + `androidx.annotation:1.6.0` (runtime) ONLY. The #3121 RTSP H.264 multi-RTP-packet corruption fix is present (landed 1.10.0). `[CITED: developer.android.com/jetpack/androidx/releases/media3]` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `androidx.media3:media3-exoplayer-hls` | **1.10.1** | HLS source module (the D-04 fallback / LL-HLS candidate) | Include in the SPIKE (D-02 measures both). Keep in the ship build only if the spike makes HLS the leader OR keeps it as a robustness fallback rung (D-04). `[VERIFIED: Google Maven]` POM HTTP 200 |
| `androidx.media3:media3-common` | **1.10.1** | Shared types (`MediaItem`, `Player`, `PlaybackException`) | Pulled transitively by `media3-exoplayer`; **do NOT also declare a different version** — all media3 modules MUST be the same version. `[VERIFIED: POM HTTP 200]` `[CITED: media3 release notes]` |

**Deliberately NOT included** (footprint discipline — the planner should resist adding these):
- `media3-ui` / `media3-ui-compose` — the project already hosts a custom render surface via `AndroidView` (`WebcamViewHost`). Use a **raw `SurfaceView` + `player.setVideoSurfaceView(surfaceView)`**, not `PlayerView`/`PlayerSurface`. `[ASSUMED — confirm no missing capability during the spike]`
- `media3-datasource` extras (Cronet/OkHttp datasource) — the RTSP module manages its own sockets; HLS uses the default HTTP datasource. No OkHttp-datasource bridge needed for v1.

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Media3 RTSP/HLS | `io.github.webrtc-sdk:android` (WHEP) | The D-03 escape hatch ONLY. Full libwebrtc `.so` for armeabi-v7a (the one ABI this APK ships); heavier; PeerConnection + SDP POST vs a one-line player. Built only if the spike fails D-05. |
| Raw SurfaceView + AndroidView | `PlayerView` (media3-ui) | PlayerView adds the UI module + Material deps and replaces the existing custom chrome the project already paints. Hybrid already solves hosting. |
| Raw SurfaceView | `TextureView` | TextureView is heavier on fill-rate (the Adreno-320 bottleneck) and its surface lifecycle is window-attach-tied. SurfaceView is the floor-correct default; only switch if the spike shows a SurfaceView z-order/clipping problem with the rounded cutout. |

**Installation (Gradle version catalog — `gradle/libs.versions.toml`):**
```toml
[versions]
media3 = "1.10.1"   # latest stable (2026-05-12). Pin >=1.10.0: that is where the RTSP H.264
                    # multi-RTP-packet corruption fix (#3121) landed; 1.9.x is the BROKEN line.
                    # Do NOT mistake this for "1.10.0 is broken" — 1.10.0 contains the fix.

[libraries]
media3-exoplayer       = { group = "androidx.media3", name = "media3-exoplayer",       version.ref = "media3" }
media3-exoplayer-rtsp  = { group = "androidx.media3", name = "media3-exoplayer-rtsp",  version.ref = "media3" }
media3-exoplayer-hls   = { group = "androidx.media3", name = "media3-exoplayer-hls",   version.ref = "media3" }
```
```kotlin
// app/build.gradle.kts dependencies {} — adjacent to the CameraX block (the prior floor-checked-native-dep precedent)
implementation(libs.media3.exoplayer)
implementation(libs.media3.exoplayer.rtsp)
implementation(libs.media3.exoplayer.hls)   // SPIKE always; ship per D-04 outcome
```

**⚠ Floor-discipline gate (the project's central trap):** After wiring, run `verifyMinSdkRelease` (the existing merged-manifest assertion task). Media3 1.10.x declares minSdk 23, but the project enforces the floor on the MERGED manifest — a transitive dep could move it. Mandatory, same as the CameraX/DataStore wiring did. `[VERIFIED: libs.versions.toml comment header + build-logic/verify-min-sdk.gradle.kts referenced in build.gradle.kts]`

**⚠ Build-requirement note:** The project is already at `sourceCompatibility/targetCompatibility = VERSION_17` and `jvmTarget = "17"`. Media3 1.10.x's only non-media3 dep is `annotation:1.6.0` (POM-verified); Kotlin 2.1.21 / AGP 8.7.0 / compileSdk 35 are comfortably in range. `[VERIFIED: POM inspection + libs.versions.toml]`

## Package Legitimacy Audit

| Package | Registry | Age | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-------------|-----------|-------------|
| `androidx.media3:media3-exoplayer` | Google Maven (`dl.google.com/dl/android/maven2`) | 1.0 since 2023; 1.10.1 2026-05-12 | github.com/androidx/media | n/a (PyPI-only tool) | Approved |
| `androidx.media3:media3-exoplayer-rtsp` | Google Maven | same line | github.com/androidx/media | n/a | Approved |
| `androidx.media3:media3-exoplayer-hls` | Google Maven | same line | github.com/androidx/media | n/a | Approved |

**Packages removed due to slopcheck [SLOP] verdict:** none.
**Packages flagged as suspicious [SUS]:** none.

**slopcheck caveat (unchanged from prior research, re-confirmed):** slopcheck only checks **PyPI**; these are **Maven/Google** (AndroidX) artifacts that legitimately do not exist on PyPI, so a `[SLOP]` from slopcheck is a known false positive. Ecosystem-correct verification was done this session by HTTP-probing Google's Maven repo — all four coordinates (`media3-exoplayer`, `-rtsp`, `-hls`, `-common`) returned **HTTP 200** at `1.10.1`, and the `group-index.xml` version list confirms **1.10.1 is the newest stable** (no 1.10.2/1.11). These are Google's own first-party libraries (the canonical ExoPlayer successor) — zero hallucination risk. They are **NOT** on Maven Central; `google()` in `settings.gradle.kts` already serves them.

## Architecture Patterns

### System Architecture Diagram

```
                    ┌──────────────── Printer SBC (RPi4 / RockPro64) ─────────────┐
  USB camera ──────►│  FFmpeg (libx264, -tune zerolatency, baseline, -bf 0,       │
                    │   -g=fps, -rtsp_transport tcp) ──► MediaMTX                 │
                    │                       ├─ RTSP  :8554/<path>   (TCP; UDP off)│
                    │                       ├─ HLS   :8888/<path>/                │
                    │                       ├─ WebRTC:8889/<path>/  (browsers)    │
                    │  Moonraker /server/webcams/list  (service=webrtc-mediamtx,  │
                    │     stream_url=:8889/<path>/, snapshot_url=…?token=, extra_data)│
                    └───────────┬──────────────────────────────────────────────────┘
                                │ websocket + REST (existing spine)
                                ▼
  ┌──────────────────────── dinghy (Android, flox) ───────────────────────────────┐
  │  Webcam model (existing, tolerant)                                             │
  │        │                                                                       │
  │        ▼   ① rung SELECT (heuristic, D-10): service startsWith "webrtc"        │
  │   ┌─ rung router ─┐       OR stream_url scheme → choose H.264 rung             │
  │   │ H.264 (NEW)   │──② URL RESOLVE (D-12): extra_data tag? → use it.           │
  │   │ MJPEG (rung1) │            else DERIVE: parse path from :8889 stream_url,  │
  │   │ Snapshot(r2)  │            swap port →:8554(RTSP)/:8888(HLS), keep /<path> │
  │   │ Unsupported   │                                                            │
  │   └───────────────┘                                                            │
  │        │                                                                       │
  │        ▼   ③ ExoPlayer (NEW): MediaItem.fromUri → RtspMediaSource.Factory      │
  │   Media3 player ─ setForceUseRtpTcp(true) ─ MediaCodec (OMX.qcom H.264, HW)    │
  │        │            └─ decoder VERIFIES (D-10): error → fall to MJPEG/Snapshot │
  │        ▼                                                                       │
  │   SurfaceView (player.setVideoSurfaceView) hosted via AndroidView              │
  │        │   ← existing token chrome (badge/dead-end/reconnect) reused           │
  │        ▼                                                                       │
  │   Focus region (ScreenScaffold) — aspect-fit, never-stretch                   │
  └────────────────────────────────────────────────────────────────────────────────┘
```

### Recommended Project Structure
An *addition* to the existing `net/` + `ui/webcam/` + `render/` split, not a new module:
```
app/src/main/java/works/mees/dinghy/
├── net/
│   └── WebcamUrl.kt          # EXTEND: deriveNativeStreamUrl() (port/path transform, D-12)
├── state/
│   └── WebcamModels.kt       # EXTEND: Rung.H264 (tier 0, above Mjpeg=1); NativeTransport; selectsH264Rung; extra_data tag read
├── ui/webcam/
│   ├── WebcamHolder.kt       # EXTEND: route H.264-selected cams to the composite feed; main-thread player marshaling
│   ├── Media3Feed.kt (NEW)   # the COMPOSITE WebcamFeed: H.264 first → falls through to bitmapFeed on decoder error
│   └── (Screen/Prefs mostly untouched — inherit preferred_cam, gating, lifecycle)
├── render/
│   └── Media3SurfaceHost.kt (NEW)  # AndroidView<SurfaceView> + leak guard, mirrors WebcamViewHost
└── (THROWAWAY) spike/
    └── SpikeActivity.kt (NEW, deleted in plan 04)   # not shipped; measures latency/decoder/APK
```

### Pattern 1: ExoPlayer over RTSP forced to TCP (the core player)
**What:** Build an `RtspMediaSource` with TCP interleaving forced (the MediaMTX RTSP is TCP / UDP-disabled — `-rtsp_transport tcp` in ravens-perch `stream_manager.py:728`), feed it to an `ExoPlayer`, render to a `SurfaceView`.
**When to use:** The H.264 rung, RTSP transport (the D-05 lead candidate).
**⚠ API surface (VERIFIED against the 1.10.1 source this session):** `setForceUseRtpTcp` takes a **`boolean`** — `setForceUseRtpTcp(boolean forceUseRtpTcp)` — NOT the no-arg call the *docs page* still shows in its example. Pass `true`. (The official RTSP doc example `setForceUseRtpTcp()` is stale vs the actual 1.10.1 method signature. The plans' boolean correction in commit 1fd8081 is correct.) `[VERIFIED: github.com/androidx/media tag 1.10.1 RtspMediaSource.java]`
```kotlin
// Source: developer.android.com/media/media3/exoplayer/rtsp (CITED) + 1.10.1 source (VERIFIED)
// MUST run player ops on the MAIN thread (ExoPlayer is single-threaded by contract).
val player = ExoPlayer.Builder(context).build()
val source = RtspMediaSource.Factory()
    .setForceUseRtpTcp(true)    // boolean arg (1.10.1). MediaMTX RTSP is TCP-only / UDP-disabled — force interleaved RTP/TCP
    .setTimeoutMs(8000)         // also gates EOS detection; keep generous for the weak SBC + LAN
    // optional in the SPIKE only: .setDebugLoggingEnabled(true)  // dumps RTSP/SDP to logcat — confirms sprop-parameter-sets
    .createMediaSource(MediaItem.fromUri(rtspUri))
player.setMediaSource(source)
player.setVideoSurfaceView(surfaceView)   // raw SurfaceView, no PlayerView
player.playWhenReady = true
player.prepare()
// … on screen exit:
player.setVideoSurfaceView(null)  // detach before release (prevents the "covered surface" leak class)
player.release()
```
> **Other verified `RtspMediaSource.Factory` setters (1.10.1):** `setTimeoutMs(long)`, `setUserAgent(String)`, `setSocketFactory(SocketFactory)`, `setDebugLoggingEnabled(boolean)`. The last is genuinely useful in the spike to confirm the SDP carries `sprop-parameter-sets` without a separate `ffprobe`. `[VERIFIED: 1.10.1 source]`

### Pattern 2: Heuristic-selects / decoder-verifies (T-10-06 preserved, D-10)
**What:** The `service`/scheme heuristic only *chooses* the H.264 rung; ExoPlayer either decodes or raises a `PlaybackException`, which the composite feed maps to "fall to the next rung." A hostile/wrong `service` can never force a fatal mis-decode — a bad guess just falls through.
**When to use:** Rung routing for the new H.264 rung.
```kotlin
// Heuristic SELECT (a hint only — never the authority). Pure, host-testable like rungFor.
fun selectsH264Rung(cam: Webcam): Boolean =
    cam.service.startsWith("webrtc", ignoreCase = true) ||
    cam.streamUrl?.startsWith("rtsp://", ignoreCase = true) == true
// Decoder VERIFY: Player.Listener.onPlayerError(PlaybackException) → network error → Transient (retry);
//   decoder/format/unsupported → the COMPOSITE feed internally delegates to bitmapFeed (MJPEG→Snapshot)
//   within the SAME run() (NOT a holder-Terminal that would dead-end a viable lower rung).
```

### Pattern 3: Composite feed → existing reconnect state machine (reuse, don't reinvent)
**What:** Map ExoPlayer's `onPlayerError` to the existing `FeedOutcome` enum the `WebcamHolder` already drives (`Transient` → keep-last-frame + backoff; `Terminal` → dead-end card; `Cancelled` → clean exit). ⚠ The H.264-then-lower-rung fall-through MUST live INSIDE the composite feed's single `run()` — `WebcamHolder.drive()` STOPS on `FeedOutcome.Terminal` (sets DeadEnd, `return`s) and does NOT try a lower rung. `bitmapFeed` is the model: it internally chains MJPEG→Snapshot and returns `Terminal` only when both are dead. `Media3Feed` mirrors that: H.264 → (on decoder error) delegate to `bitmapFeed` → return whatever it returns.
**When to use:** The new `Media3Feed`. The whole reconnect/dead-end/lifecycle machinery is reused verbatim.

### Anti-Patterns to Avoid
- **Calling ExoPlayer off the main thread.** ExoPlayer requires all method calls on the thread that built it. The existing `bitmapFeed` runs on `Dispatchers.IO` (`WebcamHolder` `driverContext = Dispatchers.IO`, verified at line 408); the Media3 feed must marshal player ops to `Dispatchers.Main` while the holder's IO driver loop owns orchestration. This is the single biggest structural divergence from the existing feed — flag it in the plan.
- **A standalone Media3Feed that returns `Terminal` on a decoder error.** That dead-ends the screen instead of falling to MJPEG/Snapshot (breaks SC2/D-10). The fall-through is in-feed, not via a holder-Terminal.
- **Using `PlayerView` + custom Compose chrome simultaneously.** Pick the raw-SurfaceView path; the project already owns its chrome.
- **Reconstructing the derive path from the webcam `name`.** The Moonraker `name` and the MediaMTX `path` are *different identifiers* (see URL Resolution landmine). Always parse the path out of the existing `stream_url`.
- **Carrying forward "do NOT pin 1.10.0 — it corrupts."** ❌ FACTUALLY INVERTED. 1.10.0 *fixes* #3121; 1.9.x is the corrupting line. Pin ≥1.10.0; 1.10.1 is current.
- **Leaking the codec on nav-away.** A held MediaCodec keeps the FGS hot and starves the 2 GB device. Mirror `WebcamHolder.cancel()` idempotent teardown; detach surface before `release()`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| H.264 NAL/RTP depacketization | A custom RTSP/RTP parser | `media3-exoplayer-rtsp` | RTP reassembly, SDP parsing (incl. `sprop-parameter-sets`), RTCP, TCP interleaving, the multi-packet-access-unit edge (#3121, fixed 1.10.0) are all solved |
| MediaCodec lifecycle | A raw `MediaCodec` + `MediaExtractor` loop | ExoPlayer | Codec init, surface binding, A/V sync, error recovery, format changes — ExoPlayer is the canonical wrapper |
| HLS segment/playlist handling | A custom m3u8 fetcher | `media3-exoplayer-hls` | Playlist refresh, segment fetch, discontinuities, LL-HLS partial segments |
| Reconnect/backoff/dead-end state | A new state machine | The existing `WebcamHolder` + `FeedOutcome` | Already host-tested (D-11); the H.264 rung is just a new composite `WebcamFeed` |
| URL host-rewrite / token preservation | New string concat | The existing `resolveWebcamUrl` + `redactWebcamUrl` | Relative-join, loopback rewrite, `?token=` preservation + V7 redaction already correct |
| Render surface hosting in the hybrid | A new Compose-interop scheme | `AndroidView` mirroring `WebcamViewHost` | The hybrid hosting + token-push (`applyTokens`) discipline is established |

**Key insight:** Almost everything except "build an ExoPlayer, force TCP, attach a SurfaceView, map its errors into the existing `FeedOutcome` (with an in-feed fall-through)" is already built. The phase's real work is the **spike measurement** and the **URL-derive transform**, not new infrastructure.

## URL Resolution (the integration crux — D-11/D-12, CAM-13)

The highest-leverage correctness section. **Re-verified against the live ravens-perch source this session.**

### What Moonraker actually advertises (verified)
From `ravens-perch/daemon/moonraker_client.py` `register_camera()`:
```python
data = {
  "name": webcam_name,            # = friendly_name.replace(' ','_').lower()   ← NOT the path
  "service": "webrtc-mediamtx",
  "stream_url": stream_url,       # = http://<host>:8889/<path_name>/          (WebRTC, browsers)
  "snapshot_url": snapshot_url,   # = http://<host>/cameras/snapshot/<camera_id>.jpg?token=…
  # NO extra_data key is passed today (re-confirmed in source this session).
}
```
`get_stream_urls()` (re-verified, `stream_manager.py:741-760`) returns:
`rtsp://<host>:8554/<path>` (no trailing slash) · `http://<host>:8888/<path>/` (HLS, trailing slash) · `http://<host>:8889/<path>/` (WebRTC) · `http://<host>/cameras/snapshot/<camera_id>.jpg?token=…`. Path = `_path_name(camera_id)` = `camera_id.replace(' ','_').lower()` (`stream_manager.py:48-50`).

### ⚠ LANDMINE: `name` ≠ `path`. The derive MUST come from `stream_url`, not `name`.
- Moonraker webcam **`name`** = `friendly_name.replace(' ','_').lower()`.
- MediaMTX **`path`** (last segment of `stream_url`) = `camera_id.replace(' ','_').lower()`.
- **Different strings** (friendly name vs camera id). Reconstructing `:8554/<name>` would 404/timeout.
- **Correct derive:** take the existing `stream_url` (`http://host:8889/<path>/`), keep its host + `<path>`, swap only the port (`8889`→`8554` RTSP / `8889`→`8888` HLS), set scheme `rtsp://` for RTSP. Use `okhttp3.HttpUrl` parsing (the `WebcamUrl.resolveWebcamUrl` precedent), never string-concat.

### D-12 explicit-tag path is UNBUILT in ravens-perch today (confirms D-14)
`register_camera()` passes **no `extra_data`** — re-verified. The **derive fallback is the ONLY path that works for both targets today.** The `Webcam.extraData: JsonObject` field already exists in the dinghy model (`WebcamModels.kt:40`, verified), so reading a future `extra_data.<ravens_perch_native_rtsp>` is a field lookup that can be added now and lie dormant until ravens-perch ships the tag. Plan it as a **prefer-if-present** layer over derive; non-blocking (D-14).

### Derive transform (the concrete shape for `WebcamUrl.kt`)
```kotlin
// EXTEND WebcamUrl.kt — pure, HttpUrl-based (mirrors resolveWebcamUrl). Returns null if not derivable.
// deriveNativeStreamUrl("http://192.168.1.120:8889/0/", Rtsp) == "rtsp://192.168.1.120:8554/0"
fun deriveNativeStreamUrl(webrtcStreamUrl: String?, transport: NativeTransport): String? {
    val url = webrtcStreamUrl?.trim()?.toHttpUrlOrNull() ?: return null
    val path = url.encodedPath.trim('/')   // MediaMTX path = the single segment; trailing slash dropped
    if (path.isBlank()) return null
    return when (transport) {
        NativeTransport.Rtsp -> "rtsp://${url.host}:8554/$path"
        NativeTransport.Hls  -> "http://${url.host}:8888/$path/"  // HLS keeps trailing slash + index.m3u8 resolution
    }
}
// Also apply the existing loopback-host rewrite (LOOPBACK_HOSTS at WebcamUrl.kt:29 → swap host to cfg.host).
```
**Port constants confirmed** from `stream_manager.py get_stream_urls()`: RTSP `:8554`, HLS `:8888`, WebRTC `:8889`. `[VERIFIED: ravens-perch source this session]`

**crowsnest caveat (D-06 secondary) — nuance added this session:** Phase-10 observed BOTH printers advertising `service: webrtc-mediamtx` (STATE.md line 352: "E5/E3 both webrtc-mediamtx, no live MJPEG"), so the heuristic SELECT will fire on the crowsnest printer too — that part is confirmed. BUT: crowsnest's *stock* backends are ustreamer (MJPEG) + camera-streamer (WebRTC via live555); "WebRTC (MediaMTX)" is an **optional, non-default crowsnest backend** the dev configured. Whether crowsnest-MediaMTX advertises the same `:8889/<path>/` port/path shape the derive assumes is **not guaranteed** — the dev cannot change its metadata. **Flag as a spike/UAT verification item:** confirm the Ender 3's advertised `stream_url` is the `:8889/<path>/` shape the derive expects. `[ASSUMED — verify on the crowsnest printer during the spike/UAT]` `[CITED: docs.mainsail.xyz/crowsnest; mainsail-crew/crowsnest README]`

## ExoPlayer Lifecycle Contract (CAM-14, mirrors WebcamHolder discipline)

The existing `WebcamHolder.cancel()` (verified at lines 172-179) already embodies the leak-free idempotent teardown the project demands (its design exists *because* of the "frozen-feed-after-restart" history, WR-01). The H.264 rung must honor the same contract, plus ExoPlayer's main-thread + release rules:

| Phase | Action | Mirror in existing code |
|-------|--------|-------------------------|
| Create | `ExoPlayer.Builder(ctx).build()` on **main thread**; build `RtspMediaSource` | `WebcamHolder.start()` launches the driver |
| Attach | `player.setVideoSurfaceView(surfaceView)`; register `Player.Listener` | `WebcamView.setFrame` push seam |
| Prepare/Play | `setMediaSource` → `prepare()` → `playWhenReady = true` | feed loop entry (`bitmapFeed`) |
| Error | `onPlayerError(PlaybackException)` → network → `Transient`; decoder → in-feed fall-through | existing `FeedOutcome` reconnect machine |
| Stop (nav-away) | `player.pause()`/stop; release if leaving | `WebcamHolder.stop()` |
| Release (screen exit / spine rebuild) | `setVideoSurfaceView(null)` **then** `player.release()`; idempotent | `WebcamHolder.cancel()` (idempotent) |

**Critical divergence to plan for:** the existing feed runs blocking decode on `Dispatchers.IO` (verified). ExoPlayer must be created and called on `Dispatchers.Main`. The `Media3Feed` needs main-thread confinement (`withContext(Dispatchers.Main)` or a main `Handler`) for player ops, while still being driven by the holder's IO-dispatched driver loop. Host-testability: the holder's state machine stays host-provable via the injected `WebcamFeed` seam (`fun interface WebcamFeed<T>`, verified at line 316) — the real player binding is an on-device property, exactly as `bitmapFeed` is.

**Surface hosting (CAM-15):** Add a `Media3SurfaceHost` composable: `AndroidView({ SurfaceView(it) }, update = { … })`, with `onReset`/`onRelease` setting the player surface to null (the documented Compose-AndroidView player-leak guard), and a `LocalInspectionMode` preview short-circuit (a live RTSP feed never renders under `@Preview` — mirror `WebcamViewHost`). For the rounded-cutout chrome: SurfaceView punches through the view hierarchy (z-order), so the rounded-corner clip + chrome must be drawn by a **sibling overlay on top**, NOT by clipping the SurfaceView. **OWNER AESTHETIC CALL (do NOT silently pick — [[dinghy-never-pick-icons-ask]] discipline):** sibling-overlay rounded mask vs accept square corners for the H.264 rung. The spike checkpoint records this; plan 04 implements exactly the recorded call. `[ASSUMED — confirm in the spike]`

## Adreno 320 Decode (CAM-10 spike target — what to confirm, what not to assume)

| Claim | Status | How the spike settles it |
|-------|--------|--------------------------|
| Adreno 320 / APQ8064 hardware-decodes H.264 up to 1080p | `[CITED: feasibility doc — maintainer forum + spec sites, MEDIUM]`; not a Qualcomm datasheet | Enumerate `MediaCodecList`; confirm an `OMX.qcom.*` H.264 decoder is selected (not `OMX.google.*` software). Log the codec name in the spike. |
| Stays in hardware decode for 640×480–1280×720 | `[ASSUMED]` — under the ceiling, but the 2012 OMX.qcom decoder uses a tiled output format | Watch CPU during playback: software fallback shows as high CPU + dropped frames. Capture CPU% / `gfxinfo` on flox. |
| Hardware H.265/VP8/VP9 NOT available | `[CITED: feasibility — HIGH]` | Out of scope (cameras are H.264); no action. |
| flox is API 30 (LineageOS 18.1), not stock API 23 | `[VERIFIED: CLAUDE.md build-env]` | flox's newer ART/MediaCodec stack is *more* capable than stock-23 — a flox pass is necessary but the minSdk-23 floor stays an install-only guarantee (no API-23 device on hand). Note this asymmetry. |
| MediaMTX RTSP SDP carries SPS/PPS in fmtp | **`[CITED: bluenviron/mediamtx discussion #783 — verbatim SDP shows sprop-parameter-sets]` — DE-RISKED this session** | The spike still confirms via `setDebugLoggingEnabled(true)` / `ffprobe`, but this is now EXPECTED to pass on ravens-perch MediaMTX, not a coin-flip. |

**The on-device measurement still matters for latency + hardware decode.** The feasibility doc refuted a "Media3 RTSP 2-3s latency" claim and an "ExoPlayer-RTSP needs SPS/PPS in fmtp as a blanket limit" claim. Reconcile with the new evidence: ExoPlayer *does* require SPS/PPS in fmtp for H.264 RTSP `[CITED: developer.android.com/media/media3/exoplayer/rtsp]`, AND MediaMTX *does* provide it `[CITED: #783]` — so on this stack RTSP H.264 is expected to play. The remaining failure mode is transport ("461 Unsupported Transport" if UDP is attempted), defeated by `setForceUseRtpTcp(true)`. **What the spike genuinely settles is latency and whether decode stays in hardware on the Adreno 320 — those, not SPS/PPS, are the D-05 pass/fail and the D-03 escape trigger.**

## The Throwaway Spike (D-02 / CAM-10)

### Glass-to-glass latency measurement (on-device, real camera)
The only reliable method against a real camera without instrumenting the pipe: **point the printer camera at a millisecond clock displayed on a second screen, then photograph (or screen-record) the flox tablet showing the live feed *and* the original clock in the same frame**, and subtract. Concretely:
1. Display a running millisecond timer (a phone stopwatch, or a ms web clock) where the printer camera can see it.
2. On flox, run the spike Activity showing the live RTSP feed of that timer.
3. Photograph both the source timer and the flox screen simultaneously (a third device, or a mirror). The ms difference = glass-to-glass latency.
4. Repeat ~10 captures, take median; do for RTSP and HLS, on both printers.

### What to capture (the D-02 readout)
| Metric | Tool | Pass/fail anchor |
|--------|------|------------------|
| Glass-to-glass latency (RTSP) | clock-on-camera, median of ~10 | ≤ ~1–2 s = PASS (D-05) → RTSP leads |
| Glass-to-glass latency (HLS) | same | compare to RTSP; if RTSP fails and HLS ≤ ~2 s, HLS leads; else D-03 WebRTC |
| CPU% during steady-state | `adb shell top` / `dumpsys cpuinfo` on flox | qualitative: software-fallback shows as pegged CPU + dropped frames |
| Decoder name | `MediaCodecList` log in the spike | must be `OMX.qcom.*` (hardware), not `OMX.google.*` |
| SPS/PPS in RTSP SDP | `setDebugLoggingEnabled(true)` logcat dump, or `ffprobe rtsp://…` | EXPECTED present on MediaMTX (#783) — confirm, don't assume failure |
| Frame health | `gfxinfo framestats` / visual | no frozen frames; no decode artifacts (the #3121 class — confirms ≥1.10.0) |
| APK-size delta | `unzip -l` the armeabi-v7a release APK before/after media3; RTSP-only vs +HLS | informational footprint number (D-02) |

### Pass/fail clean readout (what the spike reports back to staging)
```
SPIKE RESULT (flox, LineageOS 18.1):
  Ender 5 Plus (RPi4):  RTSP median = ___ ms [PASS/FAIL vs 2000ms]  HLS = ___ ms
  Ender 3 (RockPro64):  RTSP median = ___ ms [PASS/FAIL]            HLS = ___ ms
  Decoder: OMX.qcom.video.decoder.avc [HW] / OMX.google.* [SW-FALLBACK]
  SPS/PPS in RTSP SDP fmtp: YES/NO  (EXPECTED YES on MediaMTX — if NO, investigate the encoder/path)
  crowsnest stream_url shape matched the :8889/<path>/ derive convention? YES/NO  (A2)
  CPU steady-state: RTSP ~__%  HLS ~__%
  APK delta (armeabi-v7a, R8): +RTSP ___ KB   +HLS additional ___ KB
  DECISION: lead = RTSP|HLS · fallback = the other · WebRTC escape needed? Y/N (D-03) · cutout = overlay|square
```

### APK / ABI note (strengthens the D-01 footprint argument)
The release APK is **`armeabi-v7a` ONLY** (ABI split, `app/build.gradle.kts` lines 50-54 `splits { abi { include("armeabi-v7a") } }`, R8 `isMinifyEnabled = true` line 61) `[VERIFIED: build.gradle.kts this session]`. Media3 is pure Java/Kotlin + uses the platform MediaCodec (no bundled `.so` for the decoder) — APK cost is small and ABI-independent. The deferred WebRTC path *would* bundle a libwebrtc `armeabi-v7a` `.so` (the dominant cost). A concrete, measured-in-the-spike footprint contrast favoring Media3 (D-01).

## Runtime State Inventory

> Not a rename/refactor phase — but it adds a dependency + a per-printer cam pref already exists.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `preferred_cam_<profileId>` in `webcam.preferences_pb` (DataStore) — exists, inherited by the H.264 rung unchanged | None — opaque cam-id string; H.264 cams round-trip the same way |
| Live service config | ravens-perch Moonraker webcam entries (`service=webrtc-mediamtx`, `stream_url=:8889`) live in Moonraker's DB, written by the daemon — NOT changed by dinghy | None for dinghy. The future D-12 `extra_data` tag is a ravens-perch-side change (non-blocking, D-14) |
| OS-registered state | None — Android app, no Task Scheduler / pm2 / systemd state | None — verified |
| Secrets/env vars | Snapshot URL carries `?token=` (existing, handled by snapshot poller + `redactWebcamUrl`). RTSP/HLS pipes are **open / no token** (`stream_manager.py` — only snapshot is tokened) | None new — H.264 rung uses the open RTSP/HLS URL; no auth to thread |
| Build artifacts | New media3 deps in the version catalog + APK | Run `verifyMinSdkRelease` after wiring (floor guard); rebuild release for the spike APK-delta measurement |

## Common Pitfalls

### Pitfall 1: RTSP "461 Unsupported Transport" because ExoPlayer defaults to UDP
**What goes wrong:** ExoPlayer's RTSP first tries UDP; MediaMTX commonly has UDP disabled → "461 Unsupported Transport", no frames. (This — NOT missing SPS/PPS — is the real-world MediaMTX+ExoPlayer failure.)
**Why it happens:** ExoPlayer default is UDP-first; the MediaMTX deployment is TCP-only.
**How to avoid:** `setForceUseRtpTcp(true)` (CAM-16). Already in the plans.
**Warning signs:** `PlaybackException` referencing transport / "461" on `prepare()`. `[CITED: mediamtx discussion #783]`

### Pitfall 2: (Was "highest risk" — now LOW) RTSP decoder-init from missing SPS/PPS
**What goes wrong:** ExoPlayer H.264 RTSP needs SPS/PPS in the SDP `fmtp`; if absent, decoder-init fails before the first frame.
**Why it happens:** A documented ExoPlayer requirement (`[CITED: media3 RTSP doc]`).
**How to avoid:** MediaMTX **does** emit `sprop-parameter-sets` in fmtp (`[CITED: #783]` verbatim SDP) — so this is **expected to pass** on the ravens-perch target. The spike confirms via `setDebugLoggingEnabled(true)`. If it ever fails, HLS (parameter sets in-band) is the legitimate D-04 fallback. Demoted from "the highest-risk unknown" to "low-risk confirm."
**Warning signs:** decoder-init `PlaybackException` immediately on `prepare()`, no frames — but distinguish from the 461/transport case (Pitfall 1).

### Pitfall 3: Calling ExoPlayer off the main thread
**What goes wrong:** `IllegalStateException` / undefined behavior; ExoPlayer is single-threaded.
**Why it happens:** The existing webcam feed runs on `Dispatchers.IO`; copying that pattern for the player breaks it.
**How to avoid:** Confine all player calls to `Dispatchers.Main`. The IO driver loop owns orchestration; marshal player ops to main.
**Warning signs:** Crashes on `prepare()`/`release()` from a background thread.

### Pitfall 4: Codec/player leak on nav-away (the frozen-feed regression class, WR-01)
**What goes wrong:** A held MediaCodec keeps the FGS hot, starves the 2 GB device; a returning screen doubles players.
**Why it happens:** Forgetting `setVideoSurfaceView(null)` + `release()` on dispose.
**How to avoid:** Mirror `WebcamHolder.cancel()` idempotent teardown; detach surface before release; `AndroidView` `onRelease` nulls the surface.
**Warning signs:** Frozen feed after a restart; rising memory; FGS staying awake off-page.

### Pitfall 5: ❌ "Pinning Media3 1.10.0 corrupts" — DO NOT carry this forward (it's inverted)
**What the prior research said (WRONG):** "1.10.0 corrupts H.264 access units spanning multiple RTP packets; 1.10.1 fixes it."
**The corrected fact:** #3121 corruption was a **1.9.0–1.9.x regression**; the fix landed in **1.10.0** (2026-03-26). **1.10.0 and 1.10.1 both contain the fix.** Pin ≥1.10.0; 1.10.1 is the current stable and the right pin. `[CITED: media3 release notes "Version 1.10.0" RTSP bullet]`

### Pitfall 6: SurfaceView won't clip to the rounded cutout
**What goes wrong:** SurfaceView punches through the hierarchy; the parent `clipPath` doesn't apply → square corners.
**How to avoid:** Draw the chrome + rounded mask as a sibling overlay above the SurfaceView, OR accept square corners. **OWNER AESTHETIC CALL — do NOT silently pick.** Recorded in the spike checkpoint.
**Warning signs:** Rounded cutout reads square only for the H.264 feed.

## Code Examples

### HLS source (the D-04 fallback / LL-HLS candidate)
```kotlin
// Source: developer.android.com/media/media3/exoplayer/hls (CITED)
val source = HlsMediaSource.Factory(DefaultHttpDataSource.Factory())
    .createMediaSource(MediaItem.fromUri(hlsUri))   // http://host:8888/<path>/index.m3u8
player.setMediaSource(source)
player.prepare()
// HLS carries SPS/PPS in-band (TS/fMP4) — no fmtp dependency, the RTSP-init risk doesn't apply.
```

### Player error → existing FeedOutcome (the reuse seam)
```kotlin
player.addListener(object : Player.Listener {
    override fun onPlayerError(error: PlaybackException) {
        val outcome = when (error.errorCode) {
            // Source/transport drops → retry via the existing backoff machine
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> FeedOutcome.Transient
            // Decoder-init / unsupported → composite feed delegates to bitmapFeed (MJPEG→Snapshot), D-10
            else -> /* trigger in-feed fall-through, NOT a direct Terminal */ FeedOutcome.Terminal
        }
        onOutcome(outcome)
    }
})
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `com.google.android.exoplayer:exoplayer-*` (legacy) | `androidx.media3:media3-*` | Legacy ExoPlayer EOL'd; Media3 is the maintained successor | Use `androidx.media3` exclusively; legacy artifacts are dead |
| `PlayerView` + AndroidView interop | `media3-ui-compose` `PlayerSurface` (Compose-native) | media3 1.8+ | We deliberately use neither (raw SurfaceView + existing chrome) — option exists if the spike reveals a SurfaceView problem |
| Media3 1.9.x RTSP H.264 (BROKEN — #3121 regression) | Media3 1.10.0+ (#3121 fixed) | 1.10.0, 2026-03-26 | Multi-RTP-packet access units no longer corrupt — required for keyframe integrity. **(Corrects the prior research's inverted attribution.)** |
| "MediaMTX RTSP SDP-shape unknown / SPS-PPS a coin flip" | MediaMTX emits `sprop-parameter-sets` in fmtp (verbatim SDP) | documented; confirmed this session | RTSP H.264 expected to play on the ravens-perch target; transport (force-TCP), not SDP, is the gotcha |

**Deprecated/outdated:**
- Legacy `com.google.android.exoplayer` namespace — replaced by `androidx.media3`.
- The feasibility doc's `io.github.webrtc-sdk:android 144.x` reference — only relevant if D-03 fires; re-verify the version at that time (it drifts).
- The official RTSP doc's `setForceUseRtpTcp()` no-arg example — the 1.10.1 source signature is `setForceUseRtpTcp(boolean)`; use `(true)`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Raw SurfaceView + `setVideoSurfaceView` covers all rendering needs without `media3-ui` | Standard Stack / Surface Hosting | Might need media3-ui (small extra dep) — caught in spike |
| A2 | The crowsnest (Ender 3) `stream_url` is the same `:8889/<path>/` shape the derive expects | URL Resolution (D-06 secondary) | crowsnest-MediaMTX is a non-default backend; if its shape differs, derive 404s → confirm in spike/UAT (heuristic SELECT already confirmed: both printers advertise `webrtc-mediamtx`) |
| A3 | Adreno 320 stays in hardware H.264 decode at 720p with these streams | Adreno 320 Decode | Software fallback → high CPU/jank → spike CPU% catches it; could force a lower-res ask to ravens-perch |
| A4 | MediaMTX RTSP SDP carries SPS/PPS in fmtp on the ravens-perch deployment | Pitfall 2 / Adreno Decode | **DE-RISKED** (CITED #783 verbatim SDP) — now low risk; if it ever fails, HLS leads (D-04) |
| A5 | SurfaceView rounded-cutout chrome can be done via sibling overlay acceptably | Pitfall 6 | Square corners for the H.264 rung — OWNER aesthetic call (recorded in spike) |
| A6 | Media3 1.10.1 has no minSdk-floor-raising transitive dep | Standard Stack | `verifyMinSdkRelease` is the guard; declared minSdk is 23; only non-media3 dep is annotation:1.6.0 (POM-verified) |

**The planner/discuss-phase should treat A2, A3, A5 as spike/UAT-gated. A4 is now low-risk-confirmed, not a coin flip.**

## Open Questions

1. **RTSP vs HLS lead (D-04) — unresolved by design.** Only the spike decides (latency). Recommendation: spike both; report the readout; let D-05 pick. (Note: RTSP playability itself is now *expected*, given MediaMTX's SDP + force-TCP — the open part is latency.)
2. **Glass-to-glass latency on the Adreno 320.** The genuine pass/fail. No verified source pins it on this silicon — the spike is the only authority (D-05 → D-03 trigger).
3. **Hardware decode stays in hardware (OMX.qcom) at 720p.** Spike `MediaCodecList` + CPU% settles it.
4. **SurfaceView rounded-cutout chrome.** Sibling-overlay vs square corners — owner aesthetic call. Ask the owner during the spike review (do NOT silently pick).
5. **crowsnest `stream_url` shape.** Derive assumes `:8889/<path>/`. UAT verifies on the crowsnest printer (A2).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| `androidx.media3:media3-exoplayer` 1.10.1 | H.264 rung | ✓ | 1.10.1 | — (Google Maven, `google()` in repos; HTTP 200 verified) |
| `androidx.media3:media3-exoplayer-rtsp` 1.10.1 | RTSP transport | ✓ | 1.10.1 | HLS module if RTSP fails (D-04) |
| `androidx.media3:media3-exoplayer-hls` 1.10.1 | HLS transport | ✓ | 1.10.1 | RTSP / WebRTC (D-03) |
| flox device (Nexus 7 2013, Adreno 320) | Spike + UAT (D-02/D-08) | ✓ | LineageOS 18.1 / API 30 | none — the spike REQUIRES the real silicon |
| Ender 5 Plus MediaMTX (RPi4) | UAT primary | ✓ | `192.168.1.120:7125` (Moonraker); MediaMTX :8554/:8888/:8889 | — |
| Ender 3 MediaMTX (RockPro64) | UAT secondary (crowsnest-class) | ✓ | `192.168.1.121:7125` | — |
| Windows-side build (`E:\Android\gw.bat`) | All builds | ✓ | JDK 21 / SDK 35 | `./gradlew` does NOT run from WSL — CLAUDE.md |

**Missing dependencies with no fallback:** none — all verified.
**Missing dependencies with fallback:** RTSP↔HLS↔WebRTC is itself the fallback chain (D-03/D-04).

## Validation Architecture

> nyquist_validation is enabled (no `workflow.nyquist_validation: false` in config) — section included.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 (host JVM unit tests) + AndroidX instrumented (androidTest) — existing |
| Config file | none separate; `app/build.gradle.kts` `testImplementation`/`androidTestImplementation` blocks |
| Quick run command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| Full suite command | `… "E:\Android\gw.bat :app:testDebugUnitTest :app:assembleRelease --no-daemon"` (instrumented gates run on flox manually) |

### Phase Requirements → Test Map
The hard truth: **latency, hardware decode, and no-leak are inherently on-device / UAT — not unit-testable.** What IS unit-testable is the *logic around* the player (rung select, URL derive, lifecycle state mapping).

| Req | Behavior | Test Type | Automated Command | File Exists? |
|-----|----------|-----------|-------------------|-------------|
| CAM-11 | `Rung.H264` selected above MJPEG when service/scheme matches | unit | `…testDebugUnitTest --tests *RungSelectTest*` | ❌ Wave 0 (scaffold in plan 01) |
| CAM-12 | Heuristic selects, decoder error → in-feed fall-through (T-10-06) | unit (scripted feed) | `…--tests *WebcamReconnectStateTest*` (extend) | ⚠ extend existing |
| CAM-13 | `deriveNativeStreamUrl` swaps port, keeps path from `stream_url` (NOT name); loopback rewrite preserved; explicit-tag preferred | unit | `…--tests *WebcamUrlDeriveTest*` | ❌ Wave 0 |
| CAM-14 | PlaybackException → correct `FeedOutcome` mapping / in-feed fall-through | unit (fake listener + injected lower rung) | `…--tests *Media3FeedOutcomeTest*` | ❌ Wave 0 |
| CAM-16 | RTSP source built with `setForceUseRtpTcp(true)` + HLS factory constructs | unit (constructs Factory) | `…--tests *RtspSourceConfigTest*` | ❌ Wave 0 |
| CAM-10 | Glass-to-glass ≤ ~1–2 s on both printers | **UAT-only** (spike) | manual on flox — clock-on-camera | n/a |
| CAM-10 | Hardware decoder selected (OMX.qcom) | **UAT-only** (spike) | manual — `MediaCodecList` log on flox | n/a |
| CAM-17 | No frozen frames; MJPEG/snapshot fallback intact; no-crash regression (D-20) | **UAT-only** | manual on flox, both printers | n/a |
| CAM-14 | No codec/player leak on nav-away | **UAT-only** | manual — memory/FGS observation on flox | n/a |

### Sampling Rate
- **Per task commit:** `…:app:testDebugUnitTest --no-daemon` (URL-derive / rung-select / outcome-mapping units; fast, host-side).
- **Per wave merge:** full `testDebugUnitTest` + `assembleRelease` (proves the floor + R8 keep rules survive).
- **Phase gate:** the **spike readout** (D-02) + the **on-device UAT** on both printers (D-08/D-20). The unit suite alone CANNOT close this phase — the load-bearing criteria are on-device. State this plainly (the [[dinghy-display-mock-vs-reality]] lesson — a too-lenient mock once hid a real server-contract bug).

### Wave 0 Gaps
- [ ] `RungSelectTest.kt` — H.264 rung selection heuristic (CAM-11/12)
- [ ] `WebcamUrlDeriveTest.kt` — derive transform incl. the name≠path landmine + explicit-tag precedence (CAM-13)
- [ ] `Media3FeedOutcomeTest.kt` — PlaybackException → FeedOutcome / in-feed fall-through (CAM-14)
- [ ] `RtspSourceConfigTest.kt` — Factory built with `setForceUseRtpTcp(true)` + HLS factory constructs (CAM-16)
- [ ] Extend `WebcamReconnectStateTest` — composite H.264 feed plugs into the existing reconnect machine; decoder-unsupported + working lower rung → frames, DeadEnd only when lower rung also dead (CAM-12)
- [ ] Throwaway `SpikeActivity` — NOT a test; the D-02 measurement harness (deleted in plan 04)

*(All six already appear in the existing plans 01/03/04 — no change to the Wave-0 plan.)*

## Security Domain

> `security_enforcement` not set to false — section included.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | RTSP/HLS pipes are open (no token — verified `stream_manager.py`); only the snapshot URL is tokened (existing) |
| V3 Session Management | no | Stateless media pull |
| V4 Access Control | no | LAN-local, read-only viewing |
| V5 Input Validation | **yes** | All `Webcam` fields untrusted (existing tolerant model); the derive transform must `HttpUrl`-parse, never string-trust the path; a garbage `stream_url` → null → falls to next rung (fail-safe) |
| V6 Cryptography | no | No new crypto; cleartext RTSP/HTTP on LAN is the existing posture (NSC cleartext path, API-30 device) |
| V7 Errors/Logging | **yes** | The H.264 rung's URLs must route any surfaced/logged string through `surfaceWebcamUrl`/`redactWebcamUrl` (the `?token=` redaction control) — RTSP/HLS are tokenless but the snapshot fallback URL is not. ⚠ NOTE: `setDebugLoggingEnabled(true)` in the SPIKE logs raw RTSP/SDP to logcat — acceptable for throwaway dev-LAN code; the SHIPPED rung must NOT enable it (it bypasses the redaction surface). |

### Known Threat Patterns for the H.264 rung
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Hostile `service`/`stream_url` routing bytes into a fatal mis-decode | Tampering | Decoder-verifies (D-10/T-10-06): a bad guess SELECTS H.264 (a hint), ExoPlayer errors → composite feed falls through to MJPEG/Snapshot in-feed; never renders garbage, never dead-ends a viable lower rung |
| Malformed `stream_url` crashing the derive | DoS | `toHttpUrlOrNull` → null → rung falls through (existing fail-safe) |
| Token leak via a logged RTSP/snapshot URL | Information Disclosure | `surfaceWebcamUrl` is the only sanctioned surface; shipped rung must NOT use `setDebugLoggingEnabled` |
| Held codec exhausting the 2 GB device | DoS (self) | Idempotent leak-free release (CAM-14); mirrors `WebcamHolder.cancel()` |

## Sources

### Primary (HIGH confidence)
- Google Maven (`dl.google.com/dl/android/maven2/androidx/media3/…`) — this session: 1.10.1 POM **HTTP 200** for media3-exoplayer/-rtsp/-hls/-common; `group-index.xml` confirms **1.10.1 is newest stable** (no 1.10.2/1.11); RTSP POM deps = media3-exoplayer:1.10.1 + annotation:1.6.0 only.
- developer.android.com/jetpack/androidx/releases/media3 — **#3121 RTSP H.264 multi-RTP-packet corruption fix landed in 1.10.0** (2026-03-26); 1.10.1 latest stable (2026-05-12); **minSdk 23 since 1.9.0** (2025-12-17); "all modules same version."
- developer.android.com/media/media3/exoplayer/rtsp — RTSP dependency, `RtspMediaSource.Factory`, **H.264 needs SPS/PPS in fmtp**, force-TCP, TCP-interleaved support. (Doc example uses stale no-arg `setForceUseRtpTcp()`.)
- github.com/androidx/media tag 1.10.1 `RtspMediaSource.java` — **VERIFIED `setForceUseRtpTcp(boolean)`** (+ `setTimeoutMs`, `setUserAgent`, `setSocketFactory`, `setDebugLoggingEnabled`).
- github.com/bluenviron/mediamtx/discussions/783 ("How to make it work with Android ExoPlayer?") — **MediaMTX SDP carries `sprop-parameter-sets`** (verbatim SDP); the real failure is "461 Unsupported Transport" from UDP-default → force TCP.
- github.com/androidx/media issues #3121 ("Corrupted RTSP stream after upgrading from 1.8.0", MediaMTX+ffmpeg H.264 over TCP) + #2208 (make sprop-parameter-sets optional; notes MediaMTX supports it → ExoPlayer-compatible).
- ravens-perch source (READ-ONLY, this session): `daemon/stream_manager.py` (RTSP :8554, HLS :8888, WebRTC :8889; `_path_name` = `id.replace(' ','_').lower()`; libx264 `-tune zerolatency -profile:v baseline -bf 0 -g <fps>`, `-rtsp_transport tcp`); `daemon/moonraker_client.py` (`register_camera` passes NO `extra_data`; `name`=friendly vs path=camera_id).
- dinghy source (this session): `WebcamModels.kt` (Rung enum, tolerant Webcam, extraData:40, rungFor service-blind), `WebcamUrl.kt` (LOOPBACK_HOSTS:29, resolveWebcamUrl:39, redact/surface:63-76), `WebcamHolder.kt` (WebcamFeed:316, bitmapFeed:331, drive:205, cancel:172, resolveSelectedCam:245, driverContext=IO:408), `build.gradle.kts` (armeabi-v7a-only :50-54, R8 :61), `libs.versions.toml`, `STATE.md` (E5/E3 both webrtc-mediamtx :352; phase rename :259).

### Secondary (MEDIUM confidence)
- developer.android.com/media/media3/ui/surface + ProAndroidDev — SurfaceView vs PlayerView lifecycle, AndroidView player-leak guard (null surface on onReset/onRelease), SurfaceView clipping limitation.
- docs.mainsail.xyz/crowsnest + github.com/mainsail-crew/crowsnest — crowsnest backends (ustreamer/camera-streamer default; WebRTC-MediaMTX optional/non-default) → A2 caveat.

### Tertiary (LOW confidence — spike must verify)
- Feasibility doc (`research/webrtc-vs-media3-feasibility.md`) — Adreno 320 1080p H.264 ceiling (maintainer forum + spec sites, MEDIUM); refuted "Media3 RTSP 2-3s" and "SPS/PPS-in-fmtp blanket limit" claims (the latter reconciled this session: ExoPlayer needs it AND MediaMTX provides it).

## Metadata

**Confidence breakdown:**
- Standard stack / versions: HIGH — Google Maven HTTP-200 verified, POM-inspected, 1.10.1 confirmed newest stable; #3121-fix version corrected to 1.10.0.
- Integration shape (rung ladder, URL derive, lifecycle): HIGH — grounded in re-read existing code + re-verified ravens-perch source.
- RTSP H.264 playability on MediaMTX: MEDIUM-HIGH (up from LOW) — SPS/PPS-in-fmtp documented + verbatim SDP; force-TCP is the known gotcha.
- Latency / Adreno-320 hardware decode: LOW (deliberately) — D-02 spike settles these; they remain the genuine pass/fail.
- Security: HIGH — reuses existing V5/V7 controls; new surfaces tokenless; one new note (no `setDebugLoggingEnabled` in the shipped rung).

**Research date:** 2026-06-08 (force-refresh)
**Valid until:** ~2026-07-08 for the Media3 version pin (re-check for a 1.10.2/1.11 before a late execution); the ravens-perch convention, the #3121-fixed-in-1.10.0 fact, and the Adreno silicon facts are stable.
