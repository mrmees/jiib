---
phase: 21-webrtc-camera-streaming
plan: 03
subsystem: webcam
tags: [h264, media3, rung-ladder, url-derive, moonraker, pure-logic]
requires:
  - "21-01: Wave-0 RED scaffolds (RungSelectTest, WebcamUrlDeriveTest) + media3 deps"
  - "21-02: spike DECISION (RTSP lead / HLS fallback) — consumed for NativeTransport ordering"
provides:
  - "Rung.H264 (tier-0 top rung) + NativeTransport enum (Rtsp/Hls)"
  - "selectsH264Rung(cam): pure host-testable H.264-rung HINT selector"
  - "deriveNativeStreamUrl(streamUrl, transport): pure HttpUrl port/scheme swap keeping the stream_url path"
  - "nativeStreamUrlFor(cam, transport, cfg): explicit-extra_data-tag-else-derive resolution (D-12)"
affects:
  - "21-04: wires Media3Feed + holder rung-select branch onto selectsH264Rung + nativeStreamUrlFor (RTSP lead)"
tech-stack:
  added: []
  patterns:
    - "heuristic SELECTS / decoder VERIFIES (D-10) — preserves the service-blind Content-Type probe (T-10-06)"
    - "HttpUrl-parse-never-string-concat URL transform (mirrors resolveWebcamUrl)"
    - "tolerant never-fabricate extra_data read (missing/garbage → fall through, never throws)"
key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/state/WebcamModels.kt
    - app/src/main/java/works/mees/dinghy/net/WebcamUrl.kt
    - app/src/main/java/works/mees/dinghy/net/WebcamProbe.kt
    - app/src/test/java/works/mees/dinghy/webcam/RungSelectTest.kt
    - app/src/test/java/works/mees/dinghy/webcam/WebcamUrlDeriveTest.kt
decisions:
  - "Added a defensive (unreachable) Rung.H264 branch in WebcamProbe.rungFor's when — required for when-exhaustiveness once H264 joined the enum; falls through like Unsupported, preserving T-10-06 (the probe still never SELECTS H264)."
  - "Explicit extra_data tag keys = ravens_perch_native_rtsp / ravens_perch_native_hls (per-transport, ravens-perch namespace); dormant today (D-14, ravens-perch ships no tag), read tolerantly."
  - "NativeTransport.Rtsp documented as the LEAD, Hls as the FALLBACK, per the 21-02 spike DECISION."
metrics:
  duration: ~25m
  completed: 2026-06-08
  tasks: 2
  files: 5
---

# Phase 21 Plan 03: H.264 Rung Pure Logic Summary

Pure, host-testable H.264 rung logic: `Rung.H264` (tier-0 top rung) + `NativeTransport`, the `selectsH264Rung` service/scheme HINT selector, and `deriveNativeStreamUrl`/`nativeStreamUrlFor` URL resolution (RTSP-lead per the 21-02 spike) — all spike-independent, no Android, no I/O, with CAM-11/12/13 unit coverage green.

## What Was Built

**Task 1 — `WebcamModels.kt` (commit `88d4f6a`):**
- `Rung.H264(0)` added as the new tier-0 (best/top) rung above `Mjpeg(1)` — D-09. KDoc records it is decoded natively by Media3 over RTSP/HLS, selected by heuristic, verified by decoder.
- `NativeTransport { Rtsp, Hls }` enum co-located with `Rung` (the `ResolvedWebcam` co-location precedent). RTSP documented as the lead, HLS the fallback (21-02 spike DECISION).
- `selectsH264Rung(cam): Boolean` — pure HINT: `service.startsWith("webrtc", ignoreCase=true) || streamUrl?.startsWith("rtsp://", ignoreCase=true) == true`.
- `RungSelectTest` converted from RED scaffold to live assertions: tier ordering, the hint truth table (webrtc/webrtc-mediamtx/rtsp case-insensitive TRUE; plain mjpeg/snapshot-only FALSE), and a full Content-Type × code × snapshot grid proving `rungFor` NEVER emits `Rung.H264`.

**Task 2 — `WebcamUrl.kt` (commit `7c54456`):**
- `deriveNativeStreamUrl(webrtcStreamUrl, transport)` — pure, `toHttpUrlOrNull()` + `encodedPath.trim('/')` (the MediaMTX path segment from `stream_url`, NEVER the friendly `name` — the RESEARCH-flagged landmine). `Rtsp -> rtsp://host:8554/<path>` (slash dropped), `Hls -> http://host:8888/<path>/` (slash kept). Null/blank/un-parseable/path-less → `null` (fail-safe, never throws).
- `nativeStreamUrlFor(cam, transport, cfg)` — D-12 explicit-if-present-else-derive: reads `cam.extraData["ravens_perch_native_rtsp"|"ravens_perch_native_hls"]` (prefer-if-present, tolerant — missing/blank/non-string falls through), else resolves `stream_url` via `resolveWebcamUrl` (preserving the D-09 loopback rewrite) and derives.
- `WebcamUrlDeriveTest` converted to live assertions: RTSP/HLS truth table, the name≠path landmine (friendly name never leaks; path `0` carried), blank/garbage/path-less → null, loopback (127.0.0.1 + localhost) rewrite through the derive, explicit-tag precedence, and tolerant blank/non-string tag fall-through.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `when`-exhaustiveness break in `WebcamProbe.rungFor` consumer**
- **Found during:** Task 1 (adding `Rung.H264` to the enum).
- **Issue:** `WebcamProbe.probe`'s `when (rungFor(...))` enumerates `Mjpeg`/`Snapshot`/`Unsupported` with no `else`; adding the `H264` enum member made that `when` non-exhaustive → compile error.
- **Fix:** Added a defensive `Rung.H264 -> { response.close(); ProbeResult.Unsupported(terminal = true) }` branch documented as UNREACHABLE-by-construction (the byte probe stays service-blind and never SELECTS H264 — T-10-06 intact; the branch only satisfies exhaustiveness and falls through like Unsupported if ever hit).
- **Files modified:** `app/src/main/java/works/mees/dinghy/net/WebcamProbe.kt`
- **Commit:** `88d4f6a`

**2. [Rule 1 - Test bug] Incorrect null assertion in WebcamUrlDeriveTest**
- **Found during:** Task 2 full-slice run.
- **Issue:** A test asserted `nativeStreamUrlFor(stream_url="::::not a url::::")` → null, but `resolveWebcamUrl` (correctly, by its documented contract) treats a non-blank garbage string as a *relative path* and resolves it against the base host, so the derive is non-null. The test claim was wrong, not the code (direct `deriveNativeStreamUrl("::::not a url::::")` → null is already asserted separately).
- **Fix:** Replaced with the honest null cases (`streamUrl=""` and `streamUrl=null` → `resolveWebcamUrl` → null → null).
- **Files modified:** `app/src/test/java/works/mees/dinghy/webcam/WebcamUrlDeriveTest.kt`
- **Commit:** `7c54456`

## Verification

- `:app:testDebugUnitTest --tests *RungSelectTest` → BUILD SUCCESSFUL (exit 0).
- `:app:testDebugUnitTest --tests *WebcamUrlDeriveTest --tests *Webcam*` → BUILD SUCCESSFUL (exit 0).
- grep proofs: `rungFor` body contains no `H264`; derive uses `toHttpUrlOrNull` + `encodedPath` (no stream-url string concat); no `import android.` added to `WebcamModels.kt`.

## Deferred Issues

**Full `:app:testDebugUnitTest` reports 7 failures — ALL expected-RED 21-04 scaffolds, NOT a regression.**
The failures are in `Media3FeedOutcomeTest` (5) and `RtspSourceConfigTest` (2), both 21-01 Wave-0 RED scaffolds (commit `067ad83`) explicitly marked `not yet implemented — 21-04` (CAM-14/16 — `FeedOutcome` mapping + `setForceUseRtpTcp(true)`). This plan (21-03) closes only two of the four Wave-0 scaffolds (RungSelect + WebcamUrlDerive); the other two are 21-04's gate by design. The full test sourceset COMPILES (these are runtime `fail()` bodies, not compile breaks), and every slice this plan owns is green. No file touched by 21-03 caused these.

## Command-Catalog Drift (D-10) check

This plan added NO `CommandSpec` to `CommandRegistry.all` — it touches only the webcam state/net pure logic. No `docs/commands/catalog.json` / `printer-matrix.json` rows needed; `CommandCatalogDriftTest` is unaffected.

## Known Stubs

None. The explicit-tag path (`ravens_perch_native_rtsp`/`_hls`) is intentionally dormant (D-12/D-14, non-blocking) — ravens-perch ships no tag today; the derive fallback (the only path that works now) is fully implemented and tested. This is documented-intentional, resolved upstream when ravens-perch ships the tag.

## Self-Check: PASSED

- FOUND: app/src/main/java/works/mees/dinghy/state/WebcamModels.kt (Rung.H264, NativeTransport, selectsH264Rung)
- FOUND: app/src/main/java/works/mees/dinghy/net/WebcamUrl.kt (deriveNativeStreamUrl, nativeStreamUrlFor)
- FOUND: commit 88d4f6a (Task 1)
- FOUND: commit 7c54456 (Task 2)
