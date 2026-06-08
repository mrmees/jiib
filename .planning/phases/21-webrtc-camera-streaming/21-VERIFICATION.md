---
phase: 21-webrtc-camera-streaming
verified: 2026-06-08T22:00:00Z
status: human_needed
score: 3/4 roadmap success criteria verified (SC4 partial — E3 live verification owner-deferred)
overrides_applied: 0
deferred:
  - truth: "Proven live on the real Ender 3 MediaMTX cameras (both printers)"
    addressed_in: "Phase 22"
    evidence: "21-UAT.md 'Deferred to pre-release (Phase 22 Ship)' — E3/crowsnest live verification, SC8 cross-printer selection, SC5 live MJPEG fallback; owner rationale: no crowsnest instance running"
  - truth: "Mid-playback rotation restores the H.264 feed without nav-away"
    addressed_in: "Phase 22"
    evidence: "21-UAT.md 'rotation-while-playing blanks the H.264 feed (CR-01, partially mitigated)' — documented v1 known-limitation; fix in Phase-22 pre-release pass"
human_verification:
  - test: "Verify H.264 feed renders on the Ender 3 (192.168.1.121) when crowsnest MediaMTX is running"
    expected: "Live H.264 renders from the E3 cams with OMX.qcom hardware decode, ~1-2s latency, no crash, leak-free on exit — matching the E5+ result"
    why_human: "Owner does not currently have a crowsnest instance running. Same webrtc-mediamtx H.264 stack, strong implication from E5+ proof, but live on-device measurement required for the phase-goal 'both printers' claim. Environmental blocker, not a code gap."
  - test: "Verify cross-printer camera selection holds (SC8 D-20c)"
    expected: "Select a cam on E5+, switch to E3, switch back — each printer retains its own preferred cam (WR-03 null-key edge)"
    why_human: "Requires both printers live simultaneously. E5+ single-printer selection verified. Cross-printer hold deferred per owner (no E3 crowsnest)."
  - test: "Verify live MJPEG/snapshot fallback with a real non-H.264 cam (SC5)"
    expected: "A cam without an H.264 pipe falls through to MJPEG/snapshot and renders frames; only dead-ends when all rungs are exhausted"
    why_human: "Every cam on both printers is webrtc-mediamtx (H.264). No non-H.264 cam exists to exercise the live fall-through path. Covered by Phase-10 MJPEG host tests and composite fall-through unit tests (compositeMedia3Feed FallThrough→lowerRung); live re-verify if/when a non-H.264 cam exists."
---

# Phase 21: Native H.264 Camera Streaming (MediaMTX) — Verification Report

**Phase Goal:** Real camera support for the project's OWN MediaMTX-backed printers (Ravens Perch + crowsnest) — extend the Phase-10 webcam rung-ladder with a new H.264 rung decoded natively by AndroidX Media3/ExoPlayer over RTSP/HLS (the spike-gated D-01..D-20 design), enumerated via Moonraker /server/webcams/list, presented low-latency on-device, perf-gated to the Adreno-320 floor. WebRTC was the deferred escape hatch (NOT triggered — the spike passed).

**Verified:** 2026-06-08T22:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths (Roadmap Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | App selects and renders a Media3 H.264 stream (RTSP/HLS over MediaMTX), reusing Phase-10 webcam selection/rung-ladder (heuristic-selects / decoder-verifies, T-10-06 preserved) | VERIFIED | `selectsH264Rung` in `WebcamModels.kt:109`; `compositeMedia3Feed` in `Media3Feed.kt:149` routes per cam; `resolveSelectedCam` in `WebcamHolder.kt:261` provisions `Rung.H264`; the feed internally falls through to `bitmapFeed` on decoder error (T-10-06 intact). Live verified on flox/E5+ (playstation_eye + nozzle_tracker render H.264). |
| 2 | Live low-latency video shows on-device with correct aspect and no frozen frames, falling back to existing MJPEG/snapshot rungs when H.264 isn't offered | VERIFIED | E5+ SC1 PASS (live render, correct aspect, no frozen frames), SC2 PASS (~1-2s owner-eyeball), SC6 PASS (no-crash). Fallback path covered by host unit tests (Media3FeedOutcomeTest: decoder FallThrough → lower rung) and composite unit tests; live SC5 exercising deferred (no non-H.264 cam — environmental). |
| 3 | Decode respects the Adreno-320 floor (hardware decode; perf-gated) and releases cleanly on screen exit (no leaked codec) | VERIFIED | E5+ SC3 PASS: logcat shows `OMX.qcom.video.decoder.avc` (NOT `OMX.google.*`), ~0% app CPU at steady state. SC4 PASS: 90.0→94.6 MB PSS flat over 5 enter/exit cycles, no OOM/"too many codecs". `setVideoSurfaceView(null)` THEN `release()` in `NonCancellable + Dispatchers.Main` finally (Media3Feed.kt:268-278). |
| 4 | Proven live on real Ender 5 Plus AND Ender 3 MediaMTX cameras (both printers), meeting ~1-2s glass-to-glass bar (D-05/D-08) | UNCERTAIN | E5+ fully proven on release build on flox (Adreno 320, API 30). E3/crowsnest live verification owner-deferred to Phase 22 — no crowsnest instance running. Both printers run identical webrtc-mediamtx H.264 stack; E5+ proof carries strong implication but E3 is unmeasured. Routes to human verification. |

**Score:** 3/4 truths fully verified; SC4 uncertain (E3 not live-tested, owner-deferred).

### Deferred Items

Items not yet met but explicitly addressed in later milestone phases.

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | E3/crowsnest live H.264 verification, latency, hardware decode, no-leak | Phase 22 | 21-UAT.md "Deferred to pre-release (Phase 22 Ship)" — owner chose to defer (no crowsnest running) |
| 2 | SC8 cross-printer camera selection hold (WR-03 null-key edge) | Phase 22 | 21-UAT.md SC8 PARTIAL — single-printer verified on E5+; cross-printer hold requires E3 live |
| 3 | SC5 live MJPEG/snapshot fallback on a real non-H.264 cam | Phase 22 | 21-UAT.md SC5 DEFERRED — no non-H.264 cam on either printer; covered by host unit tests |
| 4 | Mid-playback rotation restores feed without nav-away (CR-01 full fix) | Phase 22 | 21-REVIEW.md CR-01 partially mitigated via d3f9d2c (surface-collect lifetime + holder no-re-key on rotation); remaining blank-on-mid-rotation documented as v1 known-limitation |

---

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `gradle/libs.versions.toml` | media3 = 1.10.1 + 3 library entries | VERIFIED | `media3 = "1.10.1"` at line 79; exactly 3 `androidx.media3` entries (grep count = 3) |
| `app/build.gradle.kts` | 3 media3 impl deps; no media3-ui | VERIFIED | `libs.media3.exoplayer`, `libs.media3.exoplayer.rtsp`, `libs.media3.exoplayer.hls` at lines 161-163; no media3-ui reference |
| `app/src/main/java/works/mees/dinghy/ui/webcam/Media3Feed.kt` | Composite H.264-first WebcamFeed; 50+ lines | VERIFIED | 355 lines; substantive: `compositeMedia3Feed`, `realH264Attempt`, `classifyPlaybackException`, `buildRtspMediaSourceFactory`, `buildHlsMediaSourceFactory`. All critical patterns present (see wiring section). |
| `app/src/main/java/works/mees/dinghy/render/Media3SurfaceHost.kt` | AndroidView<SurfaceView> + leak guard + overlay controls; 30+ lines | VERIFIED | 193 lines; substantive: AndroidView factory, onReset/onRelease clear, LocalInspectionMode preview short-circuit, Compose overlay controls drawn as sibling Z-ORDERED above SurfaceView |
| `app/src/main/java/works/mees/dinghy/render/Media3SurfaceProvider.kt` | SurfaceView StateFlow bridge + awaitSurface | VERIFIED | 46 lines; MutableStateFlow<SurfaceView?>, register/clear, awaitSurface (filterNotNull().first()), SURFACE_AWAIT_TIMEOUT_MS guard (WR-03) |
| `app/src/main/java/works/mees/dinghy/state/WebcamModels.kt` | Rung.H264 + NativeTransport + selectsH264Rung | VERIFIED | `Rung.H264(0)` as tier-0, `NativeTransport { Rtsp, Hls }`, `selectsH264Rung` function at line 109 |
| `app/src/main/java/works/mees/dinghy/net/WebcamUrl.kt` | nativeStreamUrlFor + deriveNativeStreamUrl + nested ravens-perch schema | VERIFIED | `explicitNativeStreamUrl` reads nested `extra_data.ravens_perch.streams.<proto>.url` (commit a254469); `deriveNativeStreamUrl` swaps :8889 ports; `nativeStreamUrlFor` explicit-if-present-else-derive (D-12) |
| `app/src/test/java/works/mees/dinghy/webcam/Media3FeedOutcomeTest.kt` | CAM-14 live assertions (not stub) | VERIFIED | 130 lines, 7 @Test methods; classification, fall-through, cancellation assertions present; no stub fail() bodies |
| `app/src/test/java/works/mees/dinghy/webcam/RtspSourceConfigTest.kt` | CAM-16 factory construction assertions | VERIFIED | 40 lines, 2 @Test methods; RTSP setForceUseRtpTcp(true) + HLS factory construct |
| `app/src/test/java/works/mees/dinghy/webcam/RungSelectTest.kt` | CAM-11/12 live assertions | VERIFIED | 111 lines, 8 @Test methods; selectsH264Rung truth table + rungFor never emits H264 |
| `app/src/test/java/works/mees/dinghy/webcam/WebcamUrlDeriveTest.kt` | CAM-13 URL derivation assertions | VERIFIED | 227 lines, 10 @Test methods; port-swap, name≠path, explicit-tag precedence |
| `app/src/main/java/works/mees/dinghy/spike/SpikeActivity.kt` | DELETED | VERIFIED | `test ! -f` confirms deleted; not in AndroidManifest.xml |
| `.planning/phases/21-webrtc-camera-streaming/21-UAT.md` | On-device UAT results recorded | VERIFIED | Exists; contains PASS markers; 6/8 SC PASS on E5+; E3 DEFERRED marked honestly |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `Media3Feed.kt` | `WebcamFeed` seam + `FeedOutcome` + `bitmapFeed` | `compositeMedia3Feed` implements `WebcamFeed`; on decoder `FallThrough` DELEGATES to `lowerRung.run(cam, onFrame)` within same `run()` | WIRED | `bitmapFeed` referenced at line 301; `FeedOutcome.Transient/Terminal/Cancelled` returned; in-feed fall-through proven by `Media3FeedOutcomeTest` |
| `Media3Feed.kt` | `Dispatchers.Main` | `withContext(Dispatchers.Main)` wraps all ExoPlayer ops | WIRED | Confirmed at line 227; `realH264Attempt` confines build/prepare/setVideoSurfaceView/release to Main |
| `Media3Feed.kt` | `setForceUseRtpTcp(true)` | `buildRtspMediaSourceFactory()` | WIRED | `setForceUseRtpTcp(true)` at line 115 (boolean-arg, media3 1.10.1); grep count ≥1 |
| `Media3Feed.kt` | `setVideoSurfaceView(null)` + `release()` | `NonCancellable + Dispatchers.Main` finally | WIRED | Lines 277-278; idempotent WR-01 teardown; `player = null` after release |
| `Media3Feed.kt` | `Media3SurfaceProvider.surface.collect` | surface flow collected for player lifetime (CR-01 fix, d3f9d2c) | WIRED | Line 255 — `surfaceProvider.surface.collect { sv -> player?.setVideoSurfaceView(sv) }` inside coroutineScope; re-attaches on rotation |
| `WebcamHolder.kt` | `selectsH264Rung` → `Rung.H264` | `resolveSelectedCam` at line 261 | WIRED | `val provisionalRung = if (selectsH264Rung(cam)) Rung.H264 else Rung.Mjpeg`; drive() unchanged (3 branches only) |
| `AppShell.kt` | `Media3SurfaceProvider` + `webcamMedia3Holder` | `remember(store, activeCfg.host, activeProfileId)` creates provider and holder | WIRED | Lines 250-264; `surfaceProvider = webcamSurfaceProvider` threaded into holder |
| `WebcamScreen.kt` | `Media3SurfaceHost` | `isLiveH264` guard at line 168-170 | WIRED | `selectsH264Rung(cam ?: Webcam()) && vm.frame == null` selects SurfaceView path; `Media3SurfaceHost(...)` composed when true |

---

## Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `Media3Feed.kt` | H.264 video to SurfaceView | `nativeStreamUrlFor` → RTSP/HLS URI → ExoPlayer MediaSource | YES — reads `extra_data.ravens_perch.streams.rtsp.url` (explicit) or derives from `stream_url` port-swap; PROVEN live (OMX.qcom decode on flox) | FLOWING |
| `WebcamScreen.kt` | `isLiveH264` | `selectsH264Rung(cam)` — reads `cam.service` (from Moonraker `/server/webcams/list`) | YES — live Moonraker data; webrtc-mediamtx service string selects H.264 rung | FLOWING |
| `AppShell.kt` | `webcamHolder` / `webcamSurfaceProvider` | `remember(...)` keyed on live `activeCfg.host` + `activeProfileId` | YES — constructed from live connection config; re-keyed on printer switch | FLOWING |

---

## Behavioral Spot-Checks

Step 7b is SKIPPED for the H.264 streaming path — requires a running MediaMTX server and the physical device to be live; not testable with a static command. All behavioral verification was conducted on-device (21-UAT.md).

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Spike deleted | `test ! -f app/src/main/java/works/mees/dinghy/spike/SpikeActivity.kt` | File absent | PASS |
| media3 entries exactly 3 | `grep -c 'androidx.media3' gradle/libs.versions.toml` | 3 | PASS |
| minSdk = 23 in build.gradle | grep minSdk app/build.gradle.kts | `minSdk = 23` confirmed | PASS |
| Key commits present | git log | `81cfb6c`, `e2f1fdc`, `49f3fe2`, `a254469`, `d3f9d2c` all present | PASS |

---

## Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| CAM-10 | 21-02 (spike) | Throwaway on-device latency spike; gating D-02 | SATISFIED | 21-SPIKE-RESULT.md: RTSP ~2.3s BUFFERING→READY, OMX.qcom hardware decode, SPS/PPS present. E5+ only, noted honestly. |
| CAM-11 | 21-01, 21-03 | Media3 H.264 rung above MJPEG/Snapshot (D-09) | SATISFIED | `Rung.H264(0)` tier-0 in WebcamModels.kt; `compositeMedia3Feed` implements H.264-first rung; `RungSelectTest` 8 tests green |
| CAM-12 | 21-03, 21-04 | Heuristic-selects / decoder-verifies routing (D-10/T-10-06) | SATISFIED | `selectsH264Rung` as pure hint; composite feed decoder-error → FallThrough to lowerRung (NOT Terminal); T-10-06 preserved (rungFor never emits H264); `WebcamReconnectStateTest` proves in-feed fall-through |
| CAM-13 | 21-03 | URL resolution: explicit extra_data else derive-from-convention (D-11/D-12) | SATISFIED | `nativeStreamUrlFor` + `explicitNativeStreamUrl` (nested ravens-perch schema, commit a254469) + `deriveNativeStreamUrl` (port-swap); `WebcamUrlDeriveTest` 10 tests green |
| CAM-14 | 21-01, 21-04 | ExoPlayer leak-free lifecycle/release (D-08) | SATISFIED | `setVideoSurfaceView(null)` THEN `release()` in NonCancellable finally; SC4 PASS on device (90→94.6 MB flat over 5 cycles); `Media3FeedOutcomeTest` 7 tests green |
| CAM-15 | 21-04 | SurfaceView render host via AndroidView | SATISFIED | `Media3SurfaceHost.kt` 193 lines; AndroidView<SurfaceView> factory-once/update-push; controls Z-ORDERED above SurfaceView as Compose sibling; onReset/onRelease clears provider registration |
| CAM-16 | 21-01, 21-04 | RTSP forced TCP interleaving | SATISFIED | `setForceUseRtpTcp(true)` (boolean arg, media3 1.10.1) in `buildRtspMediaSourceFactory()`; `RtspSourceConfigTest` 2 tests green; no 461 errors in spike/UAT |
| CAM-17 | 21-05 | On-device UAT both printers: latency/decode/no-leak/no-crash (D-08/D-20) | PARTIAL | E5+ core PASS on flox/release (SC1/2/3/4/6/7 PASS, SC8 PARTIAL). E3/crowsnest live verification owner-deferred to Phase 22 — no crowsnest instance running. REQUIREMENTS.md updated to reflect this. |

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `WebcamUrl.kt` | 149-161 | Stale KDoc: `nativeStreamUrlFor` still references old flat `[EXTRA_NATIVE_RTSP]`/`DORMANT` keys (IN-01 from 21-REVIEW.md) | INFO | Doc drift only; the implementation is correct (nested schema, commit a254469). No behavioral impact. |
| `Media3Feed.kt` | 182-183 | `@Suppress("UNUSED_VARIABLE") safeForLog` dead-code touch (IN-02 from 21-REVIEW.md) | INFO | Dead code; not a stub — the redaction control is live in WebcamProbe. Noise only. |
| `WebcamScreen.kt` | 168 | `selectsH264Rung(cam ?: Webcam())` allocates throwaway Webcam on null cam (IN-04 from 21-REVIEW.md) | INFO | Trivial allocation in composition; `selectsH264Rung(Webcam())` is always false anyway. Low perf concern on Adreno-320 but not a correctness issue. |

No TBD, FIXME, or XXX debt markers found in any phase-21 modified files. No unreferenced debt markers.

Code review findings from 21-REVIEW.md that were addressed:
- CR-01 (stale SurfaceView after rotation) — partially mitigated by commit d3f9d2c: surface-collect lifetime re-attach + holder no-re-key on rotation (removes player thrash). Full fix (force re-prepare on surface-size change) deferred to Phase 22 as documented v1 known-limitation.
- WR-01 (STATE_ENDED hang) — FIXED in d3f9d2c: `onPlaybackStateChanged(STATE_ENDED)` → `Transient`.
- WR-02 (CancellationException swallowed) — FIXED in d3f9d2c: `throw ce` after NonCancellable teardown.
- WR-03 (unbounded awaitSurface) — FIXED in d3f9d2c: `withTimeoutOrNull(SURFACE_AWAIT_TIMEOUT_MS = 5000L)` → FallThrough.
- WR-04 (KDoc threading mismatch) — INFO, KDoc doc drift; functional behavior is correct.
- WR-05 (H.264 re-litigated on retry) — WARNING, advisory perf improvement; not a correctness bug for v1.
- WR-06 (two independent remembers) — WARNING, narrow ordering window; benign in practice (old holder keeps its own old provider reference).

---

## Human Verification Required

### 1. E3/crowsnest H.264 Streaming (SC4 — both printers)

**Test:** Point the app at Ender 3 (192.168.1.121:7125) when a crowsnest MediaMTX instance is running. Open the Webcam screen and select a MediaMTX camera. Observe the live feed. Run `adb logcat | grep -iE "OMX.qcom|OMX.google"` to confirm hardware decode. Perform 5 enter/exit cycles and check PSS via `dumpsys meminfo`.

**Expected:** Live H.264 renders with correct aspect, ~1-2s latency, `OMX.qcom.video.decoder.avc` in logcat (not OMX.google.*), PSS flat over 5 cycles (no codec leak), no crash.

**Why human:** Owner does not currently have a crowsnest instance running on the E3. The E5+ proof carries strong implication (identical webrtc-mediamtx H.264 stack), but the phase goal specifically names both printers as the success condition. The `:8889/<path>/` URL derivation was statically confirmed against crowsnest at plan 21-02 Task 1 but was not live-measured on-device.

### 2. Cross-printer camera selection persistence (SC8 D-20c)

**Test:** Connect to E5+ (192.168.1.120:7125). Open webcam screen, select a specific camera. Switch to E3 profile in settings (192.168.1.121:7125). Switch back to E5+. Confirm the previously-selected camera is still selected (WR-03 null-key edge).

**Expected:** Each printer retains its own preferred camera across profile switches.

**Why human:** Requires both printers live simultaneously. Single-printer selection holds on E5+ (verified). The cross-printer persistence case uses the `webcamPrefs` DataStore keyed by `(host, camId)` — statically sound, but the null-key edge (WR-03: switching to a printer that has no saved preference then back) needs live exercise.

### 3. Live MJPEG/snapshot fallback (SC5)

**Test:** Either force an H.264 failure (e.g., configure a cam with a webrtc service label but no actual H.264 stream) or find/add a non-H.264 cam to either printer. Open the webcam screen for that cam and confirm it falls back to MJPEG or snapshot rendering.

**Expected:** Fallback path renders frames (MJPEG or snapshot bitmap shown in the standard `WebcamViewHost`); the cam does NOT dead-end with "feed unavailable" while a lower rung is viable.

**Why human:** Every cam on both printers is webrtc-mediamtx (H.264). The live fall-through path cannot be exercised in the current hardware setup. The behavior is covered by `Media3FeedOutcomeTest` (decoder FallThrough → lower rung → frames) and `WebcamReconnectStateTest` (in-feed fall-through, not holder Terminal), but live confirmation has not been possible.

---

## Gaps Summary

No blocking gaps. The three human verification items above are all environmental constraints (missing hardware: no running E3 crowsnest instance, no non-H.264 cam) — not code defects. The phase's core capability (H.264 streaming on the Adreno-320 floor, hardware decode, leak-free lifecycle, crash-free, drawer tile gating) is demonstrably working on the Ender 5 Plus. The two in-session bugs (empty-host crash, ravens-perch nested URL schema) were found on-device, fixed, committed (49f3fe2, a254469), and verified live.

The status is `human_needed` because the ROADMAP success criterion SC4 explicitly requires "both printers" and the E3 live verification is owner-deferred (not owner-waived). Once the E3 crowsnest instance is running and the three human checks above are completed and recorded in 21-UAT.md, this phase passes all success criteria.

---

_Verified: 2026-06-08T22:00:00Z_
_Verifier: Claude (gsd-verifier)_
