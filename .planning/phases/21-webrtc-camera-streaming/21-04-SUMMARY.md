---
phase: 21-webrtc-camera-streaming
plan: 04
subsystem: webcam
tags: [h264, media3, exoplayer, rtsp, surfaceview, composite-feed, rung-ladder, on-device-binding]
requires:
  - "21-02: spike DECISION (RTSP lead / HLS fallback / square cutout / overlay-controls-above-SurfaceView)"
  - "21-03: Rung.H264 + NativeTransport + selectsH264Rung + nativeStreamUrlFor (the pure logic this binds)"
provides:
  - "Media3Feed: the COMPOSITE H.264-first WebcamFeed (main-thread ExoPlayer, forced-TCP RTSP/HLS, in-feed fall-through to MJPEG/Snapshot on decoder error)"
  - "compositeMedia3Feed(h264Attempt, lowerRung): the host-testable composite (routes per selectsH264Rung)"
  - "classifyPlaybackException / buildRtspMediaSourceFactory / buildHlsMediaSourceFactory: the pure mappers + factory builders (CAM-14/16)"
  - "Media3SurfaceHost: AndroidView<SurfaceView> + required controls as a Compose overlay Z-ORDERED ABOVE the surface (the spike requirement)"
  - "Media3SurfaceProvider: the host↔feed SurfaceView bridge + leak guard"
  - "webcamMedia3Holder: the production holder wiring the composite feed (RTSP lead) — reuses the reconnect machine verbatim"
affects:
  - "21-05: the on-device UAT gate (latency / hardware-decode / no-leak / no-crash — the load-bearing criteria; green units are not a passing camera)"
tech-stack:
  added: []
  patterns:
    - "COMPOSITE feed: H.264-first → in-feed fall-through to bitmapFeed on a decoder error (NEVER a holder-Terminal while a lower rung is viable — D-10/SC2)"
    - "main-thread ExoPlayer confinement (the flagged DIVERGENCE from the IO-dispatched bitmapFeed)"
    - "idempotent leak-free release: setVideoSurfaceView(null) THEN release() in a NonCancellable finally (WR-01)"
    - "SurfaceView punch-through → required controls drawn as a sibling Compose overlay ABOVE the surface"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/webcam/Media3Feed.kt
    - app/src/main/java/works/mees/dinghy/render/Media3SurfaceHost.kt
    - app/src/main/java/works/mees/dinghy/render/Media3SurfaceProvider.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt
    - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/AndroidManifest.xml
    - app/src/test/java/works/mees/dinghy/webcam/Media3FeedOutcomeTest.kt
    - app/src/test/java/works/mees/dinghy/webcam/RtspSourceConfigTest.kt
    - app/src/test/java/works/mees/dinghy/webcam/WebcamReconnectStateTest.kt
    - app/src/androidTest/java/works/mees/dinghy/webcam/WebcamLifecycleTest.kt
    - app/src/androidTest/java/works/mees/dinghy/webcam/WebcamUnsupportedCardTest.kt
  deleted:
    - app/src/main/java/works/mees/dinghy/spike/SpikeActivity.kt
decisions:
  - "The H.264 attempt sits behind an H264Attempt seam (non-generic, no onFrame — video renders to the SurfaceView, not Bitmap frames) so compositeMedia3Feed is host-testable WITHOUT a real ExoPlayer."
  - "compositeMedia3Feed routes per cam on selectsH264Rung: only an H.264-SELECTED cam takes the native attempt; every other cam goes straight to bitmapFeed. This keeps drive() untouched and prevents a plain MJPEG cam from wrongly deriving an rtsp:// URL."
  - "The player surface is nulled+released in the FEED's finally (the player lives in the feed, not the host); the Media3SurfaceHost leak guard CLEARS the surface-provider registration in onReset/onRelease. (Divergence from the plan's literal 'setVideoSurfaceView(null) in onReset/onRelease' grep — the host has no player reference.)"
  - "realH264Attempt awaits the host SurfaceView via Media3SurfaceProvider (a StateFlow bridge) before attaching the player — the feed is built in the shell before the screen composes, so it cannot own the SurfaceView."
  - "RTSP is wired as the LEAD (21-02 spike); the HLS factory is constructed + host-tested (D-04) but not the default transport."
metrics:
  duration: ~75m
  completed: 2026-06-08
  tasks: 2
  files: 13
---

# Phase 21 Plan 04: On-Device Media3 Player Binding Summary

The genuinely-new shipped H.264 work: a COMPOSITE `Media3Feed` (H.264-first `WebcamFeed` that builds an
ExoPlayer on the main thread, forces RTSP-over-TCP, renders to a raw SurfaceView, and on a decoder failure
FALLS THROUGH to the existing MJPEG/Snapshot `bitmapFeed` IN-FEED — never a holder-Terminal while a lower
rung is viable), a `Media3SurfaceHost` (SurfaceView + required controls drawn as a Compose overlay
Z-ORDERED ABOVE the punch-through surface — the spike's non-negotiable requirement), holder routing on
`selectsH264Rung`, and the throwaway spike deleted. The reconnect machine is reused verbatim.

## What Was Built

**Task 1 — composite `Media3Feed.kt` (commit `81cfb6c`):**
- `compositeMedia3Feed(h264Attempt, lowerRung)`: the host-testable composite `WebcamFeed<T>`. Routes per
  cam on `selectsH264Rung` (non-H.264 cams skip straight to the lower rung). On the H.264 attempt:
  `Transient` (network) → `FeedOutcome.Transient` (holder retries the H.264 rung); `Cancelled` → `Cancelled`;
  `FallThrough` (decoder) → DELEGATES to the lower-rung feed within the SAME `run()`, returning its outcome
  — `FeedOutcome.Terminal` ONLY when the lower rung is also dead (D-10 / SC2 / T-21-04-01).
- `classifyPlaybackException(errorCode)`: pure — network codes → `Transient`, everything else (decoder-init
  / format-unsupported / renderer / source) → `FallThrough` (the decoder verifies; a hostile/wrong service
  can never force a fatal mis-decode).
- `buildRtspMediaSourceFactory()` (`setForceUseRtpTcp(true)` boolean-arg, media3 1.10.1, + `setTimeoutMs`)
  and `buildHlsMediaSourceFactory()` (the D-04 non-lead robustness fallback) — both host-constructed.
- `realH264Attempt(context, cfg, transport, surfaceProvider)`: the production attempt. ALL player ops
  (`build`/`prepare`/`setVideoSurfaceView`/`release`) on `Dispatchers.Main` (the flagged DIVERGENCE — the
  holder drives on IO); awaits the host SurfaceView; maps `onPlayerError` via `classifyPlaybackException`;
  idempotent leak-free teardown (`setVideoSurfaceView(null)` THEN `release()` in a `NonCancellable +
  Dispatchers.Main` `finally`, every exit path — WR-01 / T-21-04-02). No native URL derivable → `FallThrough`.
  The H.264 URL routes through `surfaceWebcamUrl` (Security V7 / T-21-04-03).
- `Media3SurfaceProvider`: the host↔feed SurfaceView bridge (a `StateFlow<SurfaceView?>` + `awaitSurface()`).
- `Media3FeedOutcomeTest` + `RtspSourceConfigTest` (CAM-14/16) converted from RED scaffolds to live
  assertions — classification, the in-feed fall-through (decoder + working snapshot → frames; Terminal only
  when the lower rung is also dead), cancellation, and both factories construct.

**Task 2 — `Media3SurfaceHost` + holder routing + spike deletion (commit `e2f1fdc`):**
- `Media3SurfaceHost.kt`: `AndroidView<SurfaceView>` (square video surface) registering its SurfaceView into
  the provider on factory and CLEARING it on `onReset`/`onRelease` (the Compose-AndroidView leak guard); the
  `LocalInspectionMode` preview short-circuit; and — the spike's non-negotiable requirement — the required
  controls (cycle-name pill, `Reconnecting…`, the D-04 dead-end card) drawn as a Compose sibling Z-ORDERED
  ABOVE the punch-through SurfaceView (a later `Box` child), THEME-tokened.
- `WebcamHolder.resolveSelectedCam`: an `selectsH264Rung` cam gets a provisional `Rung.H264`, else
  `Rung.Mjpeg`. `drive()`/`cancel()`/`FeedOutcome`/`start()`/`stop()` REUSED VERBATIM — still exactly the
  three `Transient`/`Terminal`/`Cancelled` branches, NO new H.264-specific Terminal branch.
- `webcamMedia3Holder` + AppShell wiring: threads the application `Context` + the re-keyed
  `Media3SurfaceProvider` into the composite feed (RTSP lead); IO `driverContext` kept, only player ops on
  Main.
- `WebcamScreen`: an H.264-selected cam playing live (no Bitmap frame) renders through `Media3SurfaceHost`;
  the moment the composite falls through to MJPEG/Snapshot (a Bitmap frame arrives) it switches to the
  existing `WebcamViewHost` Bitmap path. New `surfaceProvider` param threaded (incl. the two androidTest
  call sites).
- `WebcamReconnectStateTest` extended: (a) decoder-FallThrough + a working lower rung → frames flow and
  DeadEnd is reached ONLY when the lower rung is also dead (the in-feed fall-through, not a holder Terminal);
  (b) network-Transient → backs off + retries (the reconnect machine reused verbatim).
- Throwaway `spike/SpikeActivity.kt` deleted + de-registered from `AndroidManifest.xml`.

## Consumed Spike Decision

`21-SPIKE-RESULT.md`: lead = RTSP · fallback = HLS · WebRTC escape = N · cutout = **square**. All honored:
RTSP is the default `NativeTransport` in `webcamMedia3Holder`; HLS factory constructed + host-tested; no
WebRTC; the SurfaceView is unclipped (square). The **overlay-controls-above-the-SurfaceView** requirement
is satisfied by `Media3SurfaceHost` drawing the controls as a sibling Compose layer above the SurfaceView.

## Deviations from Plan

**1. [Design] Player-surface null lives in the FEED, not the host's `onReset`/`onRelease`.**
- The plan's literal grep expectation was `setVideoSurfaceView(null)` inside `Media3SurfaceHost`'s
  `onReset`/`onRelease`. The host has NO player reference (the ExoPlayer is owned by `realH264Attempt` in
  the feed), so the host instead CLEARS the `Media3SurfaceProvider` registration there (the leak guard that
  matters at the host: a torn-down surface is never re-attached), and the player's
  `setVideoSurfaceView(null)` THEN `release()` runs in the feed's `NonCancellable` `finally` (every exit
  path). This is the same leak-free guarantee, placed where the player actually lives. `setVideoSurfaceView(null)`
  is present in `Media3Feed.kt` (grep ≥1) as required.

**2. [Rule 3 - Blocking] `H264Attempt` made non-generic (no `onFrame`).**
- A `fun interface` SAM with a generic suspend method (`attempt<T>(cam, onFrame)`) cannot be cleanly
  implemented via a lambda with call-site-inferred `T`. Since the H.264 video renders DIRECTLY to the
  SurfaceView (it never pushes Bitmap frames — the frame seam stays for the lower rungs), the attempt needs
  neither the generic nor `onFrame`. Simplified to `attempt(cam: ResolvedWebcam): H264AttemptResult`.

**3. [Rule 1 - Bug] Per-cam routing added to `compositeMedia3Feed` (gate on `selectsH264Rung`).**
- `nativeStreamUrlFor` derives a native RTSP URL from ANY `stream_url` (it only swaps ports), so a plain
  MJPEG cam would have wrongly attempted H.264. The composite now checks `selectsH264Rung(cam.webcam)` and
  delegates straight to the lower rung for non-H.264 cams — correct routing (and it satisfies the plan's
  `selectsH264Rung` key-link).

**4. [Rule 3 - Blocking] `setTimeoutMs` expects `Long`; `RTSP_TIMEOUT_MS` typed `8000L`.** Fixed a build break.

## Verification

- `:app:testDebugUnitTest --tests *Media3FeedOutcomeTest --tests *RtspSourceConfigTest` → BUILD SUCCESSFUL.
- `:app:testDebugUnitTest --tests *Webcam*` → BUILD SUCCESSFUL.
- FULL `:app:testDebugUnitTest` → BUILD SUCCESSFUL (no regressions).
- `:app:assembleRelease :app:verifyMinSdkRelease` → BUILD SUCCESSFUL (R8 + minSdk-23 floor survive media3).
- `:app:assembleDebug` (via `:app:installDebug`) → BUILD SUCCESSFUL; **installed on flox (Nexus 7, API 30)**;
  app launches, spike activity confirmed DE-REGISTERED on-device, no fatal/AndroidRuntime crash in logcat.
- grep proofs in `Media3Feed.kt`: `setForceUseRtpTcp(true)` ≥1, both `RtspMediaSource.Factory` +
  `HlsMediaSource.Factory` present, `bitmapFeed` referenced, `withContext(Dispatchers.Main)` present,
  `setVideoSurfaceView(null)` + `.release()` present.
- `drive()` still has exactly the 3 `Transient`/`Terminal`/`Cancelled` branches (reconnect machine reused
  verbatim, no new H.264 Terminal branch).

NOTE: latency, hardware-decode, no-leak-over-time, and no-crash-regression are the **plan-21-05 on-device
UAT gate** (the load-bearing criteria — green units are not a passing camera). flox's saved Moonraker host
was not pointed at a live H.264 printer (192.168.1.120) for this plan; the H.264 video path is exercised in
21-05. This plan's gate (assembleDebug + installDebug exit 0 + host tests green) is met.

## Command-Catalog Drift (D-10) check

No `CommandSpec` added to `CommandRegistry.all` — webcam feed/render only. `CommandCatalogDriftTest` unaffected.

## Known Stubs

None functionally blocking. The HLS transport is constructed + host-tested (D-04) but RTSP is the wired
lead — HLS is an exercisable fallback, not dead code (intentional, per the spike). The explicit
`extra_data` native-URL tag remains dormant (21-03 / D-14, ravens-perch ships no tag), covered by the
derive fallback.

## Threat Flags

None. No new network surface beyond the planned RTSP/HLS player path (threat register T-21-04-01..04 all
mitigated: in-feed fall-through prevents fatal mis-decode + permanent dead-end; idempotent release;
`surfaceWebcamUrl` redaction; player ops confined to Main).

## Self-Check: PASSED

- FOUND: app/src/main/java/works/mees/dinghy/ui/webcam/Media3Feed.kt
- FOUND: app/src/main/java/works/mees/dinghy/render/Media3SurfaceHost.kt
- FOUND: app/src/main/java/works/mees/dinghy/render/Media3SurfaceProvider.kt
- GONE: app/src/main/java/works/mees/dinghy/spike/SpikeActivity.kt (deleted, de-registered)
- FOUND: commit 81cfb6c (Task 1)
- FOUND: commit e2f1fdc (Task 2)
