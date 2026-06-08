# Phase 21: Native H.264 Camera Streaming (MediaMTX) - Research

**Researched:** 2026-06-08
**Domain:** AndroidX Media3/ExoPlayer H.264 transport (RTSP/HLS) over a MediaMTX backend, extending the Phase-10 webcam rung ladder, perf-floored to the Adreno 320.
**Confidence:** HIGH on stack/versions/integration shape; **MEDIUM-LOW on the deciding numbers (latency, Adreno-320 decode, SPS/PPS-in-fmtp)** — which is exactly why D-02 mandates an on-device spike. This report is prescriptive about the *build* and explicit about *what only the spike can settle*.

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
- **D-07: Camera codec is H.264 only, decode-friendly.** baseline/constrained-baseline, level 3.1, no B-frames (`-bf 0`), 1 s GOP, 640×480–1280×720 @ 5–30 fps. Under the Adreno 320's 1080p H.264 ceiling.
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
- **Roadmap phase rename** ("WebRTC Camera Streaming" → "Native H.264 Camera Streaming (MediaMTX)") — apply at planning.
</user_constraints>

<phase_requirements>
## Phase Requirements

Req IDs are coined at planning (continuing the CAM-01 lineage). Proposed CAM-* extension set + the research that enables each:

| ID (proposed) | Description | Research Support |
|---------------|-------------|------------------|
| CAM-10 | Throwaway on-device spike (D-02): Media3-RTSP vs Media3-HLS glass-to-glass latency + CPU/GPU + APK delta on flox against both printers | § Spike Harness; § Adreno 320 Decode; § Architecture Patterns (throwaway Activity) |
| CAM-11 | Media3 H.264 rung added as the new top rung above MJPEG/Snapshot (D-09) | § Standard Stack; § Rung-Ladder Integration; existing `Rung` enum |
| CAM-12 | Heuristic-selects / decoder-verifies rung routing (D-10), preserving T-10-06 | § Rung-Ladder Integration; § Pattern 2 |
| CAM-13 | URL resolution: explicit `extra_data` tag if present, else derive-from-convention port/path transform (D-11/D-12) | § URL Resolution (the integration crux) — **incl. the path-not-name landmine** |
| CAM-14 | ExoPlayer lifecycle: create→prepare→play→release with leak-free teardown mirroring WebcamHolder (D-08) | § ExoPlayer Lifecycle Contract; existing `WebcamHolder.cancel()` |
| CAM-15 | SurfaceView render surface hosted via AndroidView in the hybrid (ADR-0001), aspect-fit, token chrome reuse | § Surface Hosting; existing `WebcamViewHost`/`WebcamView` |
| CAM-16 | RTSP forced TCP interleaving (the MediaMTX stream is TCP-only) | § RTSP-over-TCP |
| CAM-17 | On-device UAT on both printers: latency met, no-frozen-frames, MJPEG/snapshot fallback intact, no-crash regression (D-08/D-20) | § Validation Architecture |

The planner refines IDs; this table maps the deliverables to the research.
</phase_requirements>

## Summary

The phase adds one new rung to the existing Phase-10 webcam ladder: a **Media3/ExoPlayer H.264 player** that decodes the same H.264 the printers' MediaMTX backends already emit over **RTSP (`:8554`, TCP)** or **HLS (`:8888`)**. The existing ladder (`Rung` enum, `WebcamProbe`, `WebcamUrl`, `WebcamHolder`, `WebcamView`/`WebcamViewHost`) is the surface being extended — the new rung sits *above* MJPEG/Snapshot, not a rewrite. The verified stack is **`androidx.media3:media3-exoplayer:1.10.1` + `media3-exoplayer-rtsp:1.10.1` (and optionally `media3-exoplayer-hls:1.10.1`)** — all minSdk 23 (raised in 1.9.0), all live on Google's Maven, RTSP module depends only on `media3-exoplayer` + `annotation:1.6.0` (tiny). **1.10.1 specifically** fixes an RTSP H.264 regression (#3121: access units spanning multiple RTP packets corrupted) — directly relevant because 720p + 1s-GOP keyframes span many RTP packets, so 1.10.0 would visibly corrupt and 1.10.1 is the required floor.

The integration is largely a known quantity. The genuinely **unsettled, spike-gated** questions are three, and the planner MUST treat each as a measurement, not an assumption: (1) **glass-to-glass latency** of Media3-RTSP-over-TCP on the actual Adreno 320 silicon (no verified source pins it; D-05 is the pass/fail); (2) whether the **Adreno 320 OMX.qcom hardware H.264 decoder** initializes and stays in hardware for these streams (it's a 2012 tiled-format decoder); and (3) **whether MediaMTX's RTSP DESCRIBE SDP carries SPS/PPS in the fmtp attribute** — the official Media3 RTSP guide states H.264 *requires* SPS/PPS in fmtp for decoder init, and the feasibility doc's REFUTAL of that as a "limit" does NOT mean MediaMTX satisfies it; it means *test it on-device*. If MediaMTX's SDP omits in-band/fmtp SPS/PPS, RTSP fails to init the decoder and HLS (which carries the parameter sets in-band per fMP4/TS) becomes the leader — that is a legitimate spike outcome, not a bug to plan around.

**Primary recommendation:** Build the spike FIRST as a throwaway Activity (`media3-exoplayer` + `media3-exoplayer-rtsp` + `media3-exoplayer-hls`, raw `SurfaceView`, `setForceUseRtpTcp()`), measure glass-to-glass with a millisecond-clock-on-camera method on flox against both printers, and let D-04/D-05 decide RTSP-vs-HLS (or trigger the D-03 WebRTC escape hatch). Only then stage the shipped rung. Derive-from-convention URL resolution is the only path that works today (ravens-perch passes NO `extra_data` yet — verified in source), and it must parse the path segment out of the WebRTC `stream_url`, NOT reconstruct from the webcam `name` (they are different identifiers — landmine documented below).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| H.264 decode | Android client (MediaCodec via Media3) | — | The whole point: native hardware decode on the tablet, not a browser/WebRTC stack |
| RTSP/HLS transport | Android client (Media3 source module) | Printer SBC (MediaMTX) | Media3 pulls the pipe directly; MediaMTX serves it |
| H.264 encode | Printer SBC (FFmpeg → MediaMTX) | — | Already done; encoder config is ideal (D-07) — out of dinghy scope |
| Rung selection / detection | Android client (`WebcamProbe`/holder heuristic) | — | `service` is a hint; decoder verifies — T-10-06 boundary stays client-side |
| URL resolution (derive/explicit) | Android client (`WebcamUrl`) | Printer SBC (ravens-perch `extra_data`, future, non-blocking) | Derive covers both targets today; explicit tag is an additive SBC enhancement |
| Render surface | Android client (SurfaceView under AndroidView) | — | Hybrid ADR-0001; reuse `WebcamViewHost` hosting discipline |
| Lifecycle / leak-free release | Android client (`WebcamHolder` analog) | — | The codec + player MUST release on screen exit; mirror existing `cancel()` |
| Latency measurement | Spike (throwaway client Activity) | flox + both SBCs | Only on-device measurement settles RTSP-vs-HLS (D-02/D-04/D-05) |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `androidx.media3:media3-exoplayer` | **1.10.1** | The ExoPlayer engine + MediaCodec video pipeline | `[VERIFIED: Google Maven]` HTTP 200 at `dl.google.com/.../media3-exoplayer/1.10.1`; latest stable (released ~May 2026); minSdk 23 since 1.9.0 |
| `androidx.media3:media3-exoplayer-rtsp` | **1.10.1** | RTSP source module (the D-04/D-05 lead candidate) | `[VERIFIED: Google Maven]` HTTP 200; POM depends ONLY on `media3-exoplayer` + `annotation:1.6.0` (minimal). 1.10.1 fixes the H.264-multi-RTP-packet corruption (#3121) `[CITED: developer.android.com/jetpack/androidx/releases/media3]` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `androidx.media3:media3-exoplayer-hls` | **1.10.1** | HLS source module (the D-04 fallback / LL-HLS candidate) | Include in the SPIKE (D-02 measures both). Keep in the ship build only if the spike makes HLS the leader OR keeps it as a robustness fallback rung (D-04). `[VERIFIED: Google Maven]` HTTP 200 |
| `androidx.media3:media3-common` | **1.10.1** | Shared types (`MediaItem`, `Player` interface) | Pulled transitively by `media3-exoplayer`; **do NOT also declare a different version** — all media3 modules MUST be the same version `[CITED: developer.android.com/jetpack/androidx/releases/media3]` |

**Deliberately NOT included** (footprint discipline; the planner should resist adding these):
- `media3-ui` / `media3-ui-compose` — the project already hosts a custom render surface via `AndroidView` (`WebcamViewHost`). Use a **raw `SurfaceView` + `player.setVideoSurfaceView(surfaceView)`**, not `PlayerView`/`PlayerSurface`. Avoids dragging in Material-widget UI + extra minSdk surface. `[ASSUMED — confirm no missing capability during the spike]`
- `media3-datasource` extras (Cronet/OkHttp datasource) — the RTSP module manages its own sockets; HLS uses the default HTTP datasource. No OkHttp-datasource bridge needed for v1.

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Media3 RTSP/HLS | `io.github.webrtc-sdk:android` (WHEP) | The D-03 escape hatch ONLY. Full libwebrtc `.so` for armeabi-v7a (the one ABI this APK ships — see ABI-split note); heavier; PeerConnection + SDP POST vs a one-line player. Built only if the spike fails D-05. |
| Raw SurfaceView + AndroidView | `PlayerView` (media3-ui) | PlayerView is convenient but adds the UI module + Material deps and replaces the existing custom chrome (badge/dead-end/reconnect token chrome the project already paints). Hybrid already solves hosting. |
| Raw SurfaceView | `TextureView` | TextureView's surface lifecycle is window-attach-tied (vs SurfaceView's visibility-tied) and is heavier on fill-rate (the Adreno-320 bottleneck — CLAUDE.md). SurfaceView is the floor-correct default; only switch to TextureView if the spike shows a SurfaceView z-order/clipping problem with the rounded cutout. |

**Installation (Gradle version catalog — `gradle/libs.versions.toml`):**
```toml
[versions]
media3 = "1.10.1"   # RTSP H.264 multi-RTP-packet fix landed in 1.10.1 (#3121) — do NOT pin 1.10.0

[libraries]
media3-exoplayer       = { group = "androidx.media3", name = "media3-exoplayer",       version.ref = "media3" }
media3-exoplayer-rtsp  = { group = "androidx.media3", name = "media3-exoplayer-rtsp",  version.ref = "media3" }
media3-exoplayer-hls   = { group = "androidx.media3", name = "media3-exoplayer-hls",   version.ref = "media3" }
```
```kotlin
// app/build.gradle.kts dependencies {}
implementation(libs.media3.exoplayer)
implementation(libs.media3.exoplayer.rtsp)
implementation(libs.media3.exoplayer.hls)   // SPIKE always; ship per D-04 outcome
```

**⚠ Floor-discipline gate (the project's central trap):** After wiring, run `verifyMinSdkRelease` (the existing merged-manifest assertion task). Media3 1.10.x declares minSdk 23, but the project enforces the floor on the MERGED manifest, not the declared value — a transitive dep could move it. This is mandatory, same as the CameraX/DataStore wiring did.

**⚠ Build-requirement note:** Media3 requires Java-8 compatibility in consuming modules `[CITED: developer.android.com/media/media3 migration]`. The project is already at `sourceCompatibility/targetCompatibility = VERSION_17` and `jvmTarget = "17"` — satisfied. Media3 1.10.x publishes no Kotlin/AGP minimum in its POM (pure AndroidX, `annotation:1.6.0` is the only non-media3 dep); Kotlin 2.1.21 / AGP 8.7.0 / compileSdk 35 are comfortably in range. `[VERIFIED: POM inspection]`

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| `androidx.media3:media3-exoplayer` | Google Maven (`dl.google.com/dl/android/maven2`) | 1.0 since 2023; 1.10.1 ~May 2026 | first-party AndroidX | github.com/androidx/media | n/a (PyPI-only tool) | Approved |
| `androidx.media3:media3-exoplayer-rtsp` | Google Maven | same line | first-party AndroidX | github.com/androidx/media | n/a | Approved |
| `androidx.media3:media3-exoplayer-hls` | Google Maven | same line | first-party AndroidX | github.com/androidx/media | n/a | Approved |

**Packages removed due to slopcheck [SLOP] verdict:** none.
**Packages flagged as suspicious [SUS]:** none.

**slopcheck caveat (important):** `slopcheck install` reported `[SLOP]` for all four media3 coordinates — this is a **false positive**: the installed slopcheck only checks **PyPI**, and these are **Maven/Google** (AndroidX) artifacts that legitimately do not exist on PyPI. Ecosystem-correct verification was done instead by HTTP-probing Google's Maven repo (`dl.google.com/dl/android/maven2/androidx/media3/<artifact>/1.10.1/<artifact>-1.10.1.pom` → HTTP 200 for all four) and reading the `group-index.xml` version list (1.10.1 is the newest stable). These are Google's own first-party libraries (`com.android.support`/AndroidX lineage), the canonical ExoPlayer successor — zero hallucination risk. Note: they are **NOT** on Maven Central (HTTP 404 there) — AndroidX publishes only to Google's Maven, which `google()` in `settings.gradle.kts` already serves.

## Architecture Patterns

### System Architecture Diagram

```
                    ┌──────────────── Printer SBC (RPi4 / RockPro64) ─────────────┐
  USB camera ──────►│  FFmpeg (libx264, baseline, -bf 0, -g=fps, -rtsp_transport  │
                    │   tcp) ──► MediaMTX ──┬─ RTSP  :8554/<camera_id>  (TCP only) │
                    │                       ├─ HLS   :8888/<camera_id>/             │
                    │                       ├─ WebRTC:8889/<camera_id>/  (browsers) │
                    │  Moonraker /server/webcams/list  (service=webrtc-mediamtx,    │
                    │     stream_url=:8889/<id>/, snapshot_url=…?token=, extra_data) │
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
  │   Media3 player ── setForceUseRtpTcp() ── MediaCodec (OMX.qcom H.264, HW)      │
  │        │            └─ decoder VERIFIES (D-10): error → fall to MJPEG/Snapshot │
  │        ▼                                                                       │
  │   SurfaceView (player.setVideoSurfaceView) hosted via AndroidView              │
  │        │   ← existing token chrome (badge/dead-end/reconnect) reused           │
  │        ▼                                                                       │
  │   Focus region (ScreenScaffold) — aspect-fit, never-stretch                   │
  └────────────────────────────────────────────────────────────────────────────────┘
```

### Recommended Project Structure
The new code follows the existing `net/` + `ui/webcam/` + `render/` split — it is an *addition*, not a new module:
```
app/src/main/java/works/mees/dinghy/
├── net/
│   ├── WebcamUrl.kt          # EXTEND: add deriveNativeStreamUrl() (port/path transform, D-12)
│   └── (existing probe/decoder/poller untouched)
├── state/
│   └── WebcamModels.kt       # EXTEND: add Rung.H264 (tier 0, above Mjpeg=1); extra_data tag read helper
├── ui/webcam/
│   ├── WebcamHolder.kt       # EXTEND: rung select recognizes H264; new feed branch builds/releases player
│   ├── Media3Feed.kt (NEW)   # the WebcamFeed<…> H.264 binding: build player, attach surface, map outcomes
│   └── (Screen/Prefs mostly untouched — inherit preferred_cam, gating, lifecycle)
├── render/
│   └── Media3SurfaceHost.kt (NEW)  # AndroidView<SurfaceView> + applyTokens chrome, mirrors WebcamViewHost
└── (THROWAWAY) spike/
    └── SpikeActivity.kt (NEW, deleted after D-02)   # not shipped; measures latency/CPU/APK
```

### Pattern 1: ExoPlayer over RTSP forced to TCP (the core player)
**What:** Build an `RtspMediaSource` with TCP interleaving forced (the MediaMTX RTSP is TCP-only — `-rtsp_transport tcp` in ravens-perch `stream_manager.py:728`), feed it to an `ExoPlayer`, render to a `SurfaceView`.
**When to use:** The H.264 rung, RTSP transport (the D-05 lead candidate).
**Example:**
```kotlin
// Source: developer.android.com/media/media3/exoplayer/rtsp (CITED)
// MUST run player ops on the MAIN thread (ExoPlayer is single-threaded by contract).
val player = ExoPlayer.Builder(context).build()
val source = RtspMediaSource.Factory()
    .setForceUseRtpTcp()        // MediaMTX RTSP is TCP-only — force interleaved RTP/TCP from the start
    .setTimeoutMs(8000)         // also gates EOS detection; keep generous for the weak SBC + LAN
    .createMediaSource(MediaItem.fromUri(rtspUri))
player.setMediaSource(source)
player.setVideoSurfaceView(surfaceView)   // raw SurfaceView, no PlayerView
player.playWhenReady = true
player.prepare()
// … on screen exit:
player.setVideoSurfaceView(null)  // detach before release (prevents the "covered surface" leak class)
player.release()
```

### Pattern 2: Heuristic-selects / decoder-verifies (T-10-06 preserved, D-10)
**What:** The `service`/scheme heuristic only *chooses* the H.264 rung; the ExoPlayer either decodes or raises a `PlaybackException`, which the holder maps to "fall to the next rung." A hostile/wrong `service` can never force a fatal mis-decode — a bad guess just falls through.
**When to use:** Rung routing for the new H.264 rung.
**Example:**
```kotlin
// Heuristic SELECT (a hint only — never the authority)
fun selectsH264Rung(cam: Webcam): Boolean =
    cam.service.startsWith("webrtc", ignoreCase = true) ||
    cam.streamUrl?.startsWith("rtsp://", ignoreCase = true) == true
// Decoder VERIFY: Player.Listener.onPlayerError(PlaybackException) → emit FeedOutcome.Transient
//   on a recoverable error (retry/backoff, keep last frame) or .Terminal on an unrecoverable
//   decoder-init/source error → holder falls to MJPEG/Snapshot/Unsupported (existing ladder).
```

### Pattern 3: Player error → existing reconnect state machine (reuse, don't reinvent)
**What:** Map ExoPlayer's `onPlayerError` to the existing `FeedOutcome` enum the `WebcamHolder` already drives (`Transient` → keep-last-frame + backoff; `Terminal` → dead-end card; `Cancelled` → clean exit). The holder's D-11 reconnect/backoff state machine is already host-tested — the H.264 rung plugs into it as a new `WebcamFeed<…>` implementation.
**When to use:** The new `Media3Feed`. The whole reconnect/dead-end/lifecycle machinery is reused verbatim.

### Anti-Patterns to Avoid
- **Calling ExoPlayer off the main thread.** ExoPlayer requires all method calls on the thread that built it (the app main thread). The existing MJPEG feed runs on `Dispatchers.IO`; the Media3 feed must marshal player ops back to `Dispatchers.Main`. This is a real divergence from the existing feed — call it out in the plan.
- **Using `PlayerView` + custom Compose chrome simultaneously.** Pick the raw-SurfaceView path; the project already owns its chrome. Mixing PlayerView's built-in controls with the token chrome is duplicate, conflicting UI.
- **Reconstructing the derive path from the webcam `name`.** The Moonraker `name` and the MediaMTX `path` are *different identifiers* (see URL Resolution landmine). Always parse the path out of the existing `stream_url`.
- **Pinning Media3 1.10.0.** It corrupts H.264 access units that span multiple RTP packets — exactly what 720p/1s-GOP keyframes do. 1.10.1 is the floor.
- **Leaking the codec on nav-away.** A held MediaCodec keeps the FGS hot and starves the 2 GB device. Mirror `WebcamHolder.cancel()` idempotent teardown; detach surface before `release()`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| H.264 NAL/RTP depacketization | A custom RTSP/RTP parser | `media3-exoplayer-rtsp` | RTP reassembly, SDP parsing, RTCP, TCP interleaving, the multi-packet-access-unit edge (#3121) are all solved + battle-tested |
| MediaCodec lifecycle | A raw `MediaCodec` + `MediaExtractor` loop | ExoPlayer | Codec init, surface binding, A/V sync, error recovery, format changes — ExoPlayer is the canonical wrapper |
| HLS segment/playlist handling | A custom m3u8 fetcher | `media3-exoplayer-hls` | Playlist refresh, segment fetch, discontinuities, LL-HLS partial segments |
| Reconnect/backoff/dead-end state | A new state machine | The existing `WebcamHolder` + `FeedOutcome` | Already host-tested (D-11); the H.264 rung is just a new `WebcamFeed` |
| URL host-rewrite / token preservation | New string concat | The existing `resolveWebcamUrl` + `redactWebcamUrl` | Relative-join, loopback rewrite, `?token=` preservation + V7 redaction already correct |
| Render surface hosting in the hybrid | A new Compose-interop scheme | `AndroidView` mirroring `WebcamViewHost` | The hybrid hosting + token-push (`applyTokens`) discipline is established |

**Key insight:** Almost everything except "build an ExoPlayer, force TCP, attach a SurfaceView, map its errors into the existing `FeedOutcome`" is already built. The phase's real work is the **spike measurement** and the **URL-derive transform**, not new infrastructure.

## URL Resolution (the integration crux — D-11/D-12, CAM-13)

This is the highest-leverage correctness section. **Verified against the live ravens-perch source**, not assumed.

### What Moonraker actually advertises (verified)
From `ravens-perch/daemon/moonraker_client.py` `register_camera()` (lines 183–197):
```python
data = {
  "name": webcam_name,            # = friendly_name.replace(' ','_').lower()   ← NOT the path
  "service": "webrtc-mediamtx",
  "stream_url": stream_url,       # = http://<host>:8889/<path_name>/          (WebRTC, browsers)
  "snapshot_url": snapshot_url,   # = http://<host>/cameras/snapshot/<camera_id>.jpg?token=…
  # NO extra_data key is passed today.
}
```
And `build_stream_url()` (lines 414–424) builds the WebRTC URL with `path_name = camera_id.replace(' ','_').lower()`.

### ⚠ LANDMINE: `name` ≠ `path`. The derive MUST come from `stream_url`, not `name`.
- The Moonraker webcam **`name`** = `friendly_name.replace(' ','_').lower()`.
- The MediaMTX **`path`** (the last segment of `stream_url`) = `camera_id.replace(' ','_').lower()`.
- These are **different strings** (friendly name vs camera id). Reconstructing `:8554/<name>` would point at a non-existent MediaMTX path and 404/timeout.
- **Correct derive:** take the existing `stream_url` (`http://host:8889/<path>/`), keep its host + `<path>`, swap only the port (`8889`→`8554` for RTSP, `8889`→`8888` for HLS), and change scheme to `rtsp://` for RTSP. Reuse `okhttp3.HttpUrl` parsing (the existing `WebcamUrl` precedent), do not string-concat.

### D-12 explicit-tag path is UNBUILT in ravens-perch today (confirms D-14)
`register_camera()` passes **no `extra_data`** — verified. So the **derive fallback is the ONLY path that works for both targets today.** The `Webcam.extraData: JsonObject` field already exists in the dinghy model (`WebcamModels.kt:40`), so reading a future `extra_data.ravens_perch_native_rtsp` (or whatever ravens-perch chooses) is a field lookup the app can add now and have lie dormant until ravens-perch ships the tag. Plan the explicit-tag read as a **prefer-if-present** layer over derive; it is non-blocking (D-14). (Note: ravens-perch already reads `extra_data.ravens_perch_id` for its own matching at `moonraker_client.py:289`, so the namespace convention exists.)

### Derive transform (the concrete shape for `WebcamUrl.kt`)
```kotlin
// EXTEND WebcamUrl.kt — pure, HttpUrl-based (mirrors resolveWebcamUrl). Returns null if not derivable.
// rtspFromWebrtc("http://192.168.1.120:8889/0/") == "rtsp://192.168.1.120:8554/0"
fun deriveNativeStreamUrl(webrtcStreamUrl: String?, transport: NativeTransport): String? {
    val url = webrtcStreamUrl?.trim()?.toHttpUrlOrNull() ?: return null
    val path = url.encodedPath.trim('/')   // MediaMTX path = the single segment; trailing slash dropped
    if (path.isBlank()) return null
    return when (transport) {
        NativeTransport.Rtsp -> "rtsp://${url.host}:8554/$path"
        NativeTransport.Hls  -> "http://${url.host}:8888/$path/"  // HLS keeps trailing slash + index.m3u8 resolution
    }
}
```
**Port constants are confirmed** from `stream_manager.py` `get_stream_urls()` (lines 755–759): RTSP `:8554`, HLS hardcoded `:8888`, WebRTC `:8889`. `[VERIFIED: ravens-perch source]`

**crowsnest caveat (D-06 secondary):** crowsnest-on-MediaMTX follows the same `webrtc-mediamtx` + port convention, but the dev cannot change its metadata. The derive transform is *assumed* to apply identically — **flag as a spike/UAT verification item**: confirm crowsnest's advertised `stream_url` is the `:8889/<path>/` shape the derive expects. `[ASSUMED — verify on the crowsnest printer during UAT]`

## ExoPlayer Lifecycle Contract (CAM-14, mirrors WebcamHolder discipline)

The existing `WebcamHolder` already embodies the leak-free teardown the project demands (its whole `cancel()` design exists *because* of "frozen-feed-after-restart" history — `WebcamHolder.kt:32-79`). The H.264 rung must honor the same contract, plus ExoPlayer's main-thread + release rules:

| Phase | Action | Mirror in existing code |
|-------|--------|-------------------------|
| Create | `ExoPlayer.Builder(ctx).build()` on **main thread**; build `RtspMediaSource` | `WebcamHolder.start()` launches the driver |
| Attach | `player.setVideoSurfaceView(surfaceView)`; register `Player.Listener` | `WebcamView.setFrame` push seam |
| Prepare/Play | `setMediaSource` → `prepare()` → `playWhenReady = true` | feed loop entry |
| Error | `onPlayerError(PlaybackException)` → map to `FeedOutcome.Transient`/`.Terminal` | existing `FeedOutcome` reconnect machine |
| Stop (nav-away, D-13) | `player.pause()` / stop; keep instance if returning soon, or release | `WebcamHolder.stop()` (cancels driver) |
| Release (screen exit / spine rebuild, WR-01) | `setVideoSurfaceView(null)` **then** `player.release()`; idempotent | `WebcamHolder.cancel()` (idempotent, tears down everything) |

**Critical divergence to plan for:** the existing feed runs blocking decode on `Dispatchers.IO`. ExoPlayer must be created and called on `Dispatchers.Main`. The `Media3Feed` needs a main-thread confinement (e.g. build/control the player via `withContext(Dispatchers.Main)` or a main-thread `Handler`), while still being driven by the holder's IO-dispatched driver loop. Host-testability: the holder's state machine stays host-provable via the injected `WebcamFeed` seam (the real player binding is an on-device property, exactly as the MJPEG `bitmapFeed` is — `WebcamHolder.kt:320-330`).

**Surface hosting (CAM-15):** Add a `Media3SurfaceHost` composable: `AndroidView({ SurfaceView(it) }, update = { … })`, with `onReset`/`onRelease` setting the player surface to null (the documented Compose-AndroidView player-leak guard). For the rounded-cutout chrome (badge/dead-end/reconnect) the project already paints in `WebcamView`, either (a) overlay the existing chrome as a sibling Compose layer above the SurfaceView, or (b) keep a thin chrome view on top. SurfaceView punches through the view hierarchy (z-order), so the rounded-corner clip + chrome must be drawn by a sibling on top, NOT by clipping the SurfaceView itself — note this for the planner (a known SurfaceView clipping limitation; the spike should confirm the rounded cutout still reads acceptably). `[ASSUMED — confirm clip/overlay approach in the spike]`

## Adreno 320 Decode (CAM-10 spike target — what to confirm, what not to assume)

| Claim | Status | How the spike settles it |
|-------|--------|--------------------------|
| Adreno 320 / APQ8064 hardware-decodes H.264 up to 1080p | `[CITED: feesibility doc, maintainer forum + spec sites — MEDIUM]`; not a Qualcomm datasheet | Enumerate `MediaCodecList`; confirm an `OMX.qcom.*` H.264 decoder is selected (not `OMX.google.*` software). Log `MediaFormat`/codec name at runtime in the spike. |
| Stays in hardware decode for 640×480–1280×720 | `[ASSUMED]` — under the ceiling, but the 2012 OMX.qcom decoder uses a tiled output format | Watch CPU during playback: software fallback shows as high CPU + dropped frames. Capture `dumpsys gfxinfo` / CPU% on flox. |
| Hardware H.265/VP8/VP9 NOT available | `[CITED: feasibility — HIGH]` | Out of scope (cameras are H.264); no action. |
| flox is API 30 (LineageOS 18.1), not stock API 23 | `[VERIFIED: CLAUDE.md build-env]` | flox's newer ART/MediaCodec stack is *more* capable than stock-23 would be — so a flox pass is necessary but the minSdk-23 floor remains an install-only guarantee (no API-23 device on hand). Note this asymmetry. |

**The on-device measurement matters because no source pins the deciding numbers.** The feasibility doc explicitly refuted a "Media3 RTSP 2-3s latency" claim and a "needs SPS/PPS in fmtp" *limit* claim — but the **official Media3 RTSP guide DOES state H.264 needs SPS/PPS in the fmtp attribute for decoder init** `[CITED: developer.android.com/media/media3/exoplayer/rtsp]`. Reconcile: the refutal means "don't assume it's a blanket Media3 limitation," NOT "MediaMTX definitely provides it." **Spike MUST verify MediaMTX's RTSP DESCRIBE SDP carries SPS/PPS in fmtp** — if it doesn't, RTSP fails decoder-init and HLS (parameter sets in-band) becomes the D-04 leader. This is the single highest-risk unknown.

## The Throwaway Spike (D-02 / CAM-10)

### Glass-to-glass latency measurement (on-device, real camera)
The only reliable method against a real camera without instrumenting the pipe: **point the printer camera at a millisecond clock/stopwatch displayed on a second screen, then photograph (or screen-record) the flox tablet showing the live feed *and* the original clock in the same frame**, and subtract. Concretely:
1. Display a running millisecond timer (a phone stopwatch app, or `https://vclock.com` ms display) where the printer camera can see it.
2. On flox, run the spike Activity showing the live RTSP feed of that timer.
3. Photograph both the source timer and the flox screen simultaneously (a third device, or a mirror so one camera sees both). The difference in displayed ms = glass-to-glass latency.
4. Repeat ~10 captures, take median; do for RTSP and HLS, on both printers.
*(Alternative if a second screen is awkward: the spike overlays `System.currentTimeMillis()` on its own UI and the source side shows synced NTP time — more setup, less robust. The clock-on-camera method is the lowest-friction.)*

### What to capture (the D-02 readout)
| Metric | Tool | Pass/fail anchor |
|--------|------|------------------|
| Glass-to-glass latency (RTSP) | clock-on-camera, median of ~10 | ≤ ~1–2 s = PASS (D-05) → RTSP leads |
| Glass-to-glass latency (HLS) | same | compare to RTSP; if RTSP fails and HLS ≤ ~2 s, HLS leads; else D-03 WebRTC |
| CPU% during steady-state | `adb shell top -m 10` / `dumpsys cpuinfo` on flox | qualitative: software-fallback shows as pegged CPU + dropped frames |
| Decoder name | `MediaCodecList` log in the spike | must be `OMX.qcom.*` (hardware), not `OMX.google.*` |
| Frame health | `dumpsys gfxinfo works.mees.dinghy framestats` / visual | no frozen frames; no decode artifacts (the #3121 class — confirms 1.10.1) |
| APK-size delta | `unzip -l` the armeabi-v7a release APK before/after media3; compare RTSP-only vs +HLS | informational footprint number (D-02) |

### Pass/fail clean readout (what the spike reports back to staging)
```
SPIKE RESULT (flox, LineageOS 18.1):
  Ender 5 Plus (RPi4):  RTSP median = ___ ms [PASS/FAIL vs 2000ms]  HLS = ___ ms
  Ender 3 (RockPro64):  RTSP median = ___ ms [PASS/FAIL]            HLS = ___ ms
  Decoder: OMX.qcom.video.decoder.avc [HW] / OMX.google.* [SW-FALLBACK]
  SPS/PPS in RTSP SDP fmtp: YES/NO  (if NO → RTSP decoder-init fails → HLS leads)
  CPU steady-state: RTSP ~__%  HLS ~__%
  APK delta (armeabi-v7a, R8): +RTSP ___ KB   +HLS additional ___ KB
  DECISION: lead = RTSP|HLS  ·  fallback = the other  ·  WebRTC escape needed? Y/N (D-03)
```

### APK / ABI note (strengthens the D-01 footprint argument)
The release APK is **`armeabi-v7a` ONLY** (ABI split, `app/build.gradle.kts` `splits { abi { include("armeabi-v7a") } }`) `[VERIFIED: build.gradle.kts]`. Media3 is pure Java/Kotlin + uses the platform MediaCodec (no bundled `.so` for the decoder) — so its APK cost is small and ABI-independent. The deferred WebRTC path *would* bundle a libwebrtc `armeabi-v7a` `.so` (the other ABIs are split out, but the v7a slice is the one the device runs and is the dominant cost). This is a concrete, measured-in-the-spike footprint contrast favoring Media3 (D-01).

## Runtime State Inventory

> Not a rename/refactor phase — but it adds a dependency + a per-printer cam pref already exists. The relevant runtime-state surfaces:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `preferred_cam_<profileId>` in `webcam.preferences_pb` (DataStore) — already exists, inherited by the H.264 rung unchanged | None — opaque cam-id string; H.264 cams round-trip the same way |
| Live service config | ravens-perch Moonraker webcam entries (`service=webrtc-mediamtx`, `stream_url=:8889`) live in Moonraker's DB, written by the daemon — NOT changed by dinghy | None for dinghy. The future D-12 `extra_data` tag is a ravens-perch-side change (non-blocking, D-14) |
| OS-registered state | None — no Task Scheduler / pm2 / systemd state involved | None — verified (Android app, no host registrations) |
| Secrets/env vars | Snapshot URL carries `?token=` (existing, handled by snapshot poller + `redactWebcamUrl`). RTSP/HLS pipes are **open / no token** (`stream_manager.py` — only snapshot is tokened) | None new — H.264 rung uses the open RTSP/HLS URL; no auth to thread. (RTSP BASIC/DIGEST-in-URI exists in Media3 if ever needed.) |
| Build artifacts | New media3 deps land in the version catalog + APK | Run `verifyMinSdkRelease` after wiring (floor guard); rebuild release for the spike APK-delta measurement |

## Common Pitfalls

### Pitfall 1: RTSP decoder-init failure because the SDP lacks SPS/PPS in fmtp
**What goes wrong:** Media3's RTSP H.264 path needs SPS/PPS in the SDP `fmtp` attribute to initialize the decoder; if MediaMTX's DESCRIBE response omits it, the player errors before the first frame.
**Why it happens:** Documented Media3 RTSP requirement; MediaMTX's SDP shape for libx264-baseline is unverified here.
**How to avoid:** The spike verifies the SDP directly (log it, or `ffprobe rtsp://…`). If absent, HLS becomes the leader (D-04). Don't plan the ship build assuming RTSP works until the spike confirms.
**Warning signs:** `PlaybackException` with a source/decoder-init error immediately on `prepare()`, no frames ever.

### Pitfall 2: Calling ExoPlayer off the main thread
**What goes wrong:** `IllegalStateException` / undefined behavior; ExoPlayer is single-threaded.
**Why it happens:** The existing webcam feed runs on `Dispatchers.IO`; copying that pattern for the player breaks it.
**How to avoid:** Confine all player method calls to `Dispatchers.Main`. The IO driver loop can still own orchestration; marshal player ops to main.
**Warning signs:** Crashes on `prepare()`/`release()` from a background thread.

### Pitfall 3: Codec/player leak on nav-away (the frozen-feed regression class)
**What goes wrong:** A held MediaCodec keeps the FGS hot, starves the 2 GB device, and a returning screen doubles players.
**Why it happens:** Forgetting the `setVideoSurfaceView(null)` + `release()` on dispose; the project has explicit history here (WR-01).
**How to avoid:** Mirror `WebcamHolder.cancel()` idempotent teardown; detach surface before release; `AndroidView` `onRelease` nulls the surface.
**Warning signs:** Frozen feed after a restart; rising memory; FGS staying awake off-page.

### Pitfall 4: Pinning Media3 1.10.0 instead of 1.10.1
**What goes wrong:** Visual corruption/artifacts on keyframes (H.264 access units spanning multiple RTP packets — exactly 720p/1s-GOP keyframes).
**Why it happens:** 1.10.0 has the regression; 1.10.1 fixes it (#3121).
**How to avoid:** Pin 1.10.1 in the version catalog.
**Warning signs:** Smeared/blocky frames specifically on the periodic keyframe.

### Pitfall 5: SurfaceView won't clip to the rounded cutout
**What goes wrong:** The existing `WebcamView` paints a rounded-corner clip; a SurfaceView punches through the hierarchy and ignores the parent clip, so corners look square.
**Why it happens:** SurfaceView is a separate surface composited below the window; standard `clipPath` doesn't apply.
**How to avoid:** Draw the chrome + rounded mask as a sibling overlay *above* the SurfaceView (or accept square corners for the H.264 rung — owner call). Confirm the aesthetic in the spike. **Do NOT invent a new clipping hack; ask the owner if square corners are acceptable for this rung.**
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
            // Source/transport drops → retry via the existing D-11 backoff machine
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> FeedOutcome.Transient
            // Decoder-init / unsupported → fall to MJPEG/Snapshot/Unsupported (decoder VERIFIES, D-10)
            else -> FeedOutcome.Terminal
        }
        onOutcome(outcome)
    }
})
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `com.google.android.exoplayer:exoplayer-*` (legacy) | `androidx.media3:media3-*` | Legacy ExoPlayer EOL'd; Media3 is the maintained successor | Use `androidx.media3` exclusively; legacy artifacts are dead |
| `PlayerView` + AndroidView interop | `media3-ui-compose` `PlayerSurface` (Compose-native) | media3 1.8+ | We deliberately use neither (raw SurfaceView + existing chrome) — but know the option exists if the spike reveals a SurfaceView problem |
| Media3 1.10.0 RTSP H.264 | Media3 1.10.1 (#3121 fix) | May 2026 | Multi-RTP-packet access units no longer corrupt — required for keyframe integrity |

**Deprecated/outdated:**
- Legacy `com.google.android.exoplayer` namespace — replaced by `androidx.media3`.
- The feasibility doc's `io.github.webrtc-sdk:android 144.x` reference — only relevant if D-03 fires; re-verify the version at that time (it drifts).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Raw SurfaceView + `setVideoSurfaceView` covers all rendering needs without `media3-ui` | Standard Stack / Surface Hosting | Might need media3-ui (small extra dep) — caught in spike |
| A2 | The derive transform applies identically to crowsnest's `stream_url` shape | URL Resolution (D-06 secondary) | crowsnest path could differ → derive 404s → confirm in UAT on the crowsnest printer |
| A3 | Adreno 320 stays in hardware H.264 decode at 720p with these streams | Adreno 320 Decode | Software fallback → high CPU/jank → spike CPU% catches it; could force lower res ask to ravens-perch |
| A4 | MediaMTX RTSP SDP carries SPS/PPS in fmtp | Pitfall 1 / Adreno Decode | RTSP decoder-init fails → HLS leads (legitimate D-04 outcome, not a blocker) |
| A5 | SurfaceView rounded-cutout chrome can be done via sibling overlay acceptably | Pitfall 5 | Square corners for the H.264 rung — owner aesthetic call |
| A6 | Media3 1.10.1 has no minSdk-floor-raising transitive dep | Standard Stack | `verifyMinSdkRelease` is the guard; declared minSdk is 23 |

**These are the items the planner/discuss-phase should treat as spike/UAT-gated, not locked.**

## Open Questions

1. **RTSP vs HLS lead (D-04) — unresolved by design.** Only the spike decides. Recommendation: spike both; report the readout; let D-05 pick.
2. **SPS/PPS-in-fmtp for MediaMTX RTSP.** Could flip the lead to HLS. Recommendation: spike verifies the SDP first thing; if RTSP can't init, don't burn time on it.
3. **SurfaceView rounded-cutout chrome.** Sibling-overlay vs accept square corners — owner aesthetic call. Recommendation: ask the owner during the spike review (do NOT silently pick).
4. **crowsnest `stream_url` shape.** Derive assumes the `:8889/<path>/` convention. Recommendation: UAT verifies on the crowsnest printer; the ravens-perch printer is the controlled primary.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| `androidx.media3:media3-exoplayer` 1.10.1 | H.264 rung | ✓ | 1.10.1 | — (Google Maven, `google()` already in repos) |
| `androidx.media3:media3-exoplayer-rtsp` 1.10.1 | RTSP transport | ✓ | 1.10.1 | HLS module if RTSP fails (D-04) |
| `androidx.media3:media3-exoplayer-hls` 1.10.1 | HLS transport | ✓ | 1.10.1 | RTSP / WebRTC (D-03) |
| flox device (Nexus 7 2013, Adreno 320) | Spike + UAT (D-02/D-08) | ✓ | LineageOS 18.1 / API 30 | none — the spike REQUIRES the real silicon |
| Ender 5 Plus MediaMTX (RPi4) | UAT primary | ✓ | `192.168.1.120:7125` (Moonraker); MediaMTX :8554/:8888/:8889 | — |
| Ender 3 MediaMTX (RockPro64) | UAT secondary (crowsnest-class) | ✓ | `192.168.1.121:7125` | — |
| Windows-side build (`E:\Android\gw.bat`) | All builds | ✓ | JDK 21 / SDK 35 | — (`./gradlew` does NOT run from WSL — CLAUDE.md) |

**Missing dependencies with no fallback:** none — all verified.
**Missing dependencies with fallback:** RTSP↔HLS↔WebRTC ladder is itself the fallback chain (D-03/D-04).

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
The hard truth for this phase: **latency, hardware decode, and no-leak are inherently on-device / UAT — not unit-testable.** What IS unit-testable is the *logic around* the player (rung select, URL derive, lifecycle state mapping). Be explicit:

| Req | Behavior | Test Type | Automated Command | File Exists? |
|-----|----------|-----------|-------------------|-------------|
| CAM-11 | `Rung.H264` selected above MJPEG when service/scheme matches | unit | `…testDebugUnitTest --tests *RungSelectTest*` | ❌ Wave 0 |
| CAM-12 | Heuristic selects, decoder error → falls to next rung (T-10-06) | unit (scripted feed) | `…--tests *WebcamReconnectStateTest*` (extend) | ⚠ extend existing |
| CAM-13 | `deriveNativeStreamUrl` swaps port, keeps path from `stream_url` (NOT name); loopback rewrite preserved | unit | `…--tests *WebcamUrlDeriveTest*` | ❌ Wave 0 |
| CAM-13 | Explicit `extra_data` tag preferred over derive when present | unit | same | ❌ Wave 0 |
| CAM-14 | PlaybackException → correct `FeedOutcome` mapping | unit (fake listener) | `…--tests *Media3FeedOutcomeTest*` | ❌ Wave 0 |
| CAM-16 | RTSP source built with `setForceUseRtpTcp` (construction asserted, not played) | unit (constructs Factory) | `…--tests *RtspSourceConfigTest*` | ❌ Wave 0 |
| CAM-10 | Glass-to-glass ≤ ~1–2 s on both printers | **UAT-only** (spike) | manual on flox — clock-on-camera | n/a |
| CAM-10 | Hardware decoder selected (OMX.qcom) | **UAT-only** (spike) | manual — `MediaCodecList` log on flox | n/a |
| CAM-17 | No frozen frames; MJPEG/snapshot fallback intact; no-crash regression (D-20) | **UAT-only** | manual on flox, both printers | n/a |
| CAM-14 | No codec/player leak on nav-away | **UAT-only** | manual — memory/FGS observation on flox | n/a |

### Sampling Rate
- **Per task commit:** `…:app:testDebugUnitTest --no-daemon` (the URL-derive / rung-select / outcome-mapping units; fast, host-side).
- **Per wave merge:** full `testDebugUnitTest` + `assembleRelease` (proves the floor + R8 keep rules survive).
- **Phase gate:** the **spike readout** (D-02) + the **on-device UAT** on both printers (D-08/D-20). The unit suite alone CANNOT close this phase — the load-bearing criteria are on-device. State this plainly in the plan so no one mistakes green unit tests for a passing camera (the [[dinghy-display-mock-vs-reality]] lesson — a too-lenient mock once hid a real server-contract bug).

### Wave 0 Gaps
- [ ] `RungSelectTest.kt` — H.264 rung selection heuristic (CAM-11/12)
- [ ] `WebcamUrlDeriveTest.kt` — derive transform incl. the name≠path landmine + explicit-tag precedence (CAM-13)
- [ ] `Media3FeedOutcomeTest.kt` — PlaybackException → FeedOutcome mapping (CAM-14)
- [ ] `RtspSourceConfigTest.kt` — Factory built with forceUseRtpTcp (CAM-16)
- [ ] Extend `WebcamReconnectStateTest` — H.264 feed plugs into the existing reconnect machine (CAM-12)
- [ ] Throwaway `SpikeActivity` — NOT a test; the D-02 measurement harness (deleted after)

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
| V7 Errors/Logging | **yes** | The H.264 rung's URLs must route any surfaced/logged string through the existing `surfaceWebcamUrl`/`redactWebcamUrl` (the `?token=` redaction control) — even though RTSP/HLS are tokenless, the snapshot fallback URL is not, and a mixed log must not leak it |

### Known Threat Patterns for the H.264 rung
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Hostile `service`/`stream_url` routing bytes into a fatal mis-decode | Tampering | Decoder-verifies (D-10/T-10-06): a bad guess falls through, never renders garbage; ExoPlayer errors → next rung |
| Malformed `stream_url` crashing the derive | DoS | `toHttpUrlOrNull` → null → no usable URL → rung falls through (existing fail-safe pattern) |
| Token leak via a logged RTSP/snapshot URL | Information Disclosure | `surfaceWebcamUrl` is the only sanctioned surface (existing V7 control); reuse it for H.264 diagnostics |
| Held codec exhausting the 2 GB device | DoS (self) | Idempotent leak-free release (CAM-14); mirrors `WebcamHolder.cancel()` |

## Sources

### Primary (HIGH confidence)
- Google Maven repo (`dl.google.com/dl/android/maven2/androidx/media3/…`) — verified 1.10.1 POM HTTP 200 for media3-exoplayer/-rtsp/-hls/-common; `group-index.xml` confirms 1.10.1 is newest stable; RTSP POM deps = media3-exoplayer + annotation:1.6.0 only.
- developer.android.com/jetpack/androidx/releases/media3 — latest 1.10.1, minSdk 23 since 1.9.0, RTSP H.264 multi-RTP-packet fix (#3121) in 1.10.1, "all modules same version," Java-8 compatibility requirement.
- developer.android.com/media/media3/exoplayer/rtsp — RTSP dependency, `RtspMediaSource.Factory`, `setForceUseRtpTcp()`, `setTimeoutMs()`, **H.264 needs SPS/PPS in fmtp**, TCP-interleaved support.
- developer.android.com/reference/.../RtspMediaSource.Factory — Factory builder methods.
- ravens-perch source (READ-ONLY, this session): `daemon/stream_manager.py` (RTSP :8554, HLS :8888, WebRTC :8889, `_path_name` = `id.replace(' ','_').lower()`, `-rtsp_transport tcp`, baseline/`-bf 0`/`-g fps`); `daemon/moonraker_client.py` (`register_camera` passes NO `extra_data` today; `name`=friendly vs path=camera_id; `extra_data.ravens_perch_id` namespace precedent); `HANDOFF_2026-06-04.md` (live FPS/res; RockPro64 RKMPP encode failed → libx264 software encode).
- dinghy source: `WebcamModels.kt`, `WebcamUrl.kt`, `WebcamProbe.kt`, `WebcamHolder.kt`, `WebcamView.kt`, `WebcamScreen.kt`, `WebcamPrefs.kt`, `app/build.gradle.kts` (armeabi-v7a-only ABI split; R8 release; verifyMinSdk), `libs.versions.toml`.

### Secondary (MEDIUM confidence)
- developer.android.com/media/media3/ui/surface + ProAndroidDev/Medium — SurfaceView vs PlayerView vs PlayerSurface lifecycle, AndroidView player-leak guard (null surface on onReset/onRelease), SurfaceView clipping limitation.
- android-developers.googleblog Media3 1.8/1.9/1.10 posts — module evolution, minSdk-23 timing.

### Tertiary (LOW confidence — spike must verify)
- Feasibility doc (`research/webrtc-vs-media3-feasibility.md`) — Adreno 320 1080p H.264 ceiling (maintainer forum + spec sites, MEDIUM); refuted "Media3 RTSP 2-3s" and "SPS/PPS-in-fmtp limit" claims (do NOT re-rely — spike on-device).

## Metadata

**Confidence breakdown:**
- Standard stack / versions: HIGH — Google Maven verified, POM-inspected, latest-stable confirmed.
- Integration shape (rung ladder, URL derive, lifecycle): HIGH — grounded in the actual existing code + verified ravens-perch source.
- Latency / Adreno decode / SPS-PPS-in-fmtp: LOW (deliberately) — D-02 spike settles these; the report flags them as measurements, not assumptions.
- Security: HIGH — reuses existing V5/V7 controls; new surfaces are tokenless.

**Research date:** 2026-06-08
**Valid until:** ~2026-07-08 for the Media3 version pin (fast-moving — re-check for a 1.10.2/1.11 before a late execution); the ravens-perch convention + Adreno silicon facts are stable.
