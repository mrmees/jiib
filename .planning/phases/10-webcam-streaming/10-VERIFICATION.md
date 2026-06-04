---
phase: 10-webcam-streaming
verified: 2026-06-04T00:00:00Z
status: human_needed
score: 4/4 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Live MJPEG rung-1 eyeball — stand up a crowsnest/ustreamer camera, connect the app, confirm the MJPEG stream (multipart/x-mixed-replace) renders live frames with no jank, correct aspect, and the snapshot badge does NOT appear"
    expected: "Smooth live video stream renders in the rounded cutout at the declared decode policy (TARGET_FPS=12, ARGB_8888, inSampleSize from real frame bounds); no frozen frames; rung-2 snapshot badge absent"
    why_human: "Neither home printer exposes a multipart/x-mixed-replace stream (both are webrtc-mediamtx). The MJPEG decode path is proven by golden byte-stream unit tests (MjpegStreamDecoderTest) and the fix commits (WR-04 inJustDecodeBounds, WR-03 double-buffer) but has no live environmental subject on this developer's network. Requires a temporary crowsnest/ustreamer source to eyeball."
---

# Phase 10: Webcam Streaming Verification Report

**Phase Goal:** View the printer's webcam(s) on-device — decode the MJPEG stream (Moonraker `/server/webcams/list`), hard-downscaled for the Adreno-320 fill-rate floor; WebRTC deferred.
**Verified:** 2026-06-04
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| #  | Truth | Status | Evidence |
|----|-------|--------|----------|
| SC-1 | App enumerates configured webcams (`/server/webcams/list`) and streams the selected feed, rendering live frames | VERIFIED | `WebcamsHolder.kt` issues one-shot `server.webcams.list` on each handshake edge; result parsed by `parseWebcamsList()`; fed via `SpineHandle.webcams` → `AppContainer.webcams`/`webcamCount`; `WebcamHolder.bitmapFeed` drives rung-1/2/3 per cam. Rung-2 snapshot LIVE-PROVEN on E3 (UAT Step 2); rung-3 dead-end LIVE-PROVEN on E5 (UAT Step 4). Rung-1 MJPEG FIXTURE-PROVEN (golden byte-stream unit tests — no live MJPEG subject on either home printer; see human_verification). |
| SC-2 | Frames decoded off the UI thread and downscaled to view size (bounded memory) — no OOM or sustained jank on Adreno-320 during a stream | VERIFIED | `bitmapFeed` drives the decoder on `Dispatchers.IO` (NetworkOnMainThreadException guard). WR-04 fix (`eb09aa3`) confirmed in `bitmaps()`: `inJustDecodeBounds` first-frame measurement feeds `computeInSampleSize(realW, realH, viewPx, viewPx)` — not the old `src==req` always-1 bug. WR-03 fix (`b824d5a`) confirmed: double-buffer ping-pong in `bitmaps()` prevents torn-frame across decode→UI thread. Snapshot path: two-pass bounds + `computeInSampleSize` confirmed in `snapshotBitmaps()`. UAT perf gate PASSED: render p95 ~20ms, 0 frozen frames on flox/Adreno-320 (UAT Step 1). |
| SC-3 | Stream pauses/stops cleanly when page is backgrounded; missing/unreachable cam degrades gracefully | VERIFIED | `WebcamScreen` `DisposableEffect` calls `holder.start()` on compose, `holder.stop()` on dispose. `AppShell` `LaunchedEffect` additionally stops on `lifecycleOwner.lifecycle.State.STARTED` exit. `DisposableEffect(webcamHolder)` calls `holder.cancel()` on spine rebuild. CR-01 fix (`a98aa15`) confirmed in `WebcamHolder`: `holderScope` is a child scope cancelled in `cancel()`, tearing down the init `webcams.collect` collector, driver, and prefs writes together. WR-01 fix (`e0fd181`) confirmed: `start()` does `previous?.cancelAndJoin()` before launching the new driver — serialized teardown. All three launches in `WebcamHolder.kt` use `holderScope.launch` (not `scope.launch`). UAT Step 6 PASSED on-device. |
| SC-4 | WebRTC documented as explicitly deferred (MJPEG-only this phase) | VERIFIED | `10-CONTEXT.md` `<deferred>` section names WebRTC. `docs/view_specific_notes/camera_feed` has the WebRTC-deferred note (commit `39ab599`). `WebcamView.Mode.DeadEnd` card shows `"This camera uses WebRTC — not supported yet"` (no browser button per D-04). UAT Step 4 confirmed the dead-end card appears correctly on E5. |

**Score: 4/4 truths verified**

---

### Deferred Items

No items identified as addressed in a later phase — all gaps are either verified or routed to human verification.

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `net/MjpegStreamDecoder.kt` | Okio multipart MJPEG decoder, drop-behind, OOM guard, inJustDecodeBounds WR-04 fix, double-buffer WR-03 fix | VERIFIED | Full implementation confirmed. `bitmaps()` function: `inJustDecodeBounds` first-frame measurement, double-buffer ping-pong, `inSampleSize` from real frame bounds. |
| `net/SnapshotPoller.kt` | ~2fps snapshot poll, terminal-401 stop, backoff, two-pass downsample, de-dup | VERIFIED | Full implementation confirmed. De-dup (`daae32c`), two-pass `inJustDecodeBounds` downsample (`1083108` fix), `POLL_INTERVAL=500ms`, terminal 401/403. |
| `net/WebcamProbe.kt` | D-02 Content-Type probe, GET (not HEAD), rung selector, surfaceWebcamUrl wired | VERIFIED | Uses GET; `rungFor()` on `Content-Type`; CR-02 fix (`7859014`) confirmed — `ProbeResult.Unsupported.reason` built via `surfaceWebcamUrl()`, the only URL-surface path. |
| `net/WebcamUrl.kt` | D-09 resolver (relative join + loopback rewrite + token-preserve), redactWebcamUrl, surfaceWebcamUrl | VERIFIED | `resolveWebcamUrl` uses `HttpUrl.resolve()` for relative join; `LOOPBACK_HOSTS` rewrite preserves `?token=`. `surfaceWebcamUrl` is the sole sanctioned URL surface path (CR-02 enforced). |
| `state/WebcamModels.kt` | Webcam model, Rung enum, rungFor(), parseWebcamsList() | VERIFIED | All present and substantive. `parseWebcamsList` is tolerant (per-entry `runCatching`, empty-on-malformed). `rungFor` uses Content-Type as authority (D-02). |
| `ui/webcam/WebcamHolder.kt` | Orchestration holder, holderScope (CR-01), cancelAndJoin (WR-01), rung-ladder drive, D-11 reconnect state machine | VERIFIED | `holderScope` correctly initialized as child scope; all three `scope.launch` sites use `holderScope.launch`; `start()` has `previous?.cancelAndJoin()`; D-11 reconnect state machine with `FeedOutcome.Transient/Terminal/Cancelled`. |
| `ui/webcam/WebcamScreen.kt` | Focus/Field/Gutter layout, aspect-aware Field show/hide, page-visible lifecycle, cam picker | VERIFIED | `DisposableEffect(holder)` start/stop; `isPortraitFeed()` drives `showField` logic; cam picker field; gutter = red Back only. |
| `render/WebcamView.kt` | Classic-Views raster surface, pixel-square never-stretch, rounded cutout, token chrome (D-03/D-04/D-11/cycle overlay) | VERIFIED | `WebcamView.Mode` enum with Live/SnapshotFallback/Reconnecting/DeadEnd; pre-allocated `frameMatrix`/`cutoutPath`; `ThemeableView` implementation. File exists and is substantive (>80 lines; full `onDraw` logic present). |
| `render/WebcamViewHost.kt` | AndroidView host, `setFrame`/`setChrome` wired, theme push | VERIFIED | `setFrame(frame)` and `setChrome(mode, camName, serviceName, multiCam)` called in `update` block. Factory creates `WebcamView` once. |
| `webcam/WebcamsHolder.kt` | One-shot-per-handshake enumeration, rising-edge only, best-effort tolerant | VERIFIED | Rising-edge detection (`wasConnected` flag); `runCatching` best-effort; publishes to `_webcams` StateFlow. |
| `di/AppContainer.kt` | `webcamPrefs`, `webcamHttpClient`, `webcams` Flow, `webcamCount` | VERIFIED | All four present: `WebcamPrefs(webcamDataStore)`, `webcamHttpClient by lazy`, `webcams = spine.flatMapLatest`, `webcamCount = webcams.map { it.size }`. |
| `service/MoonrakerService.kt` | `WebcamsHolder` instantiated, `server.webcams.list` RPC request, wired to `SpineHandle.webcams` | VERIFIED | `WebcamsHolder` instantiated at line 166; `CommandRegistry.webcamsList` RPC; result forwarded to `SpineHandle.webcams`. |
| `ui/shell/AppShell.kt` | Webcam holder keyed, `DisposableEffect(webcamHolder)` cancel, page-visible `LaunchedEffect`, `Dest.Webcam → WebcamScreen` routing | VERIFIED | All four wiring points confirmed in grep output (lines 190, 206, 214–221, 478). |
| `ui/shell/AppDrawer.kt` | Webcam tile greyed (D-08) when `!webcamEnabled`, ALWAYS shown | VERIFIED | `DrawerTileSpec(label = "Webcam", symbol = "photo_camera", dest = Dest.Webcam)` always present; `live = tile.dest != null && (tile.dest != Dest.Webcam || webcamEnabled)` correctly greys when `!webcamEnabled`. |
| `command/CommandRegistry.kt` | `webcamsList` CommandSpec | VERIFIED | `val webcamsList: CommandSpec<Unit> = jsonRpc(catalogId = "MR-server.webcams.list", ...)` present. |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `MoonrakerService` | `SpineHandle.webcams` | `WebcamsHolder.webcams` StateFlow | WIRED | `webcams = webcamsHolder.webcams` confirmed in `MoonrakerService.kt:187` |
| `AppContainer.webcamCount` | `AppShell.webcamEnabled` | `collectAsStateWithLifecycle` | WIRED | `val webcamCount by container.webcamCount.collectAsStateWithLifecycle(initialValue = 0)` at `AppShell.kt:171` |
| `AppShell.webcamHolder` | `WebcamScreen` | `Dest.Webcam → WebcamScreen(holder = webcamHolder)` | WIRED | `AppShell.kt:478` |
| `WebcamHolder.holderScope` | `cancel()` | `holderScope.cancel()` | WIRED | `cancel()` calls `holderScope.cancel()` — tears down init collector + driver + prefs writes atomically (CR-01) |
| `bitmapFeed MJPEG path` | `bitmaps(boundary, viewWidthPx, viewHeightPx)` | WR-04 `inJustDecodeBounds` first-frame | WIRED | `val decoder = bitmaps(probe.boundary, viewWidthPx = viewWidthPx, viewHeightPx = viewHeightPx)` confirmed |
| `WebcamProbe.Unsupported.reason` | `surfaceWebcamUrl` | `reason = surfaceWebcamUrl(resolvedStreamUrl)` | WIRED | CR-02 fix confirmed; only URL→string path is `surfaceWebcamUrl` |
| `WebcamUrl.resolveWebcamUrl` | loopback rewrite | `LOOPBACK_HOSTS.contains(resolved.host)` + `newBuilder().host(cfg.host)` | WIRED | D-09 confirmed; `?token=` preserved via `newBuilder()` |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `WebcamScreen` | `vm` (frame + mode + cams) | `holder.vm.collectAsStateWithLifecycle()` | Yes — `bitmapFeed` drives JPEG decode → `_vm.value = _vm.value.copy(frame = frame)` | FLOWING |
| `AppDrawer` webcam tile | `webcamEnabled` | `container.webcamCount` → `webcams.map { it.size } > 0` | Yes — live enumeration count from `WebcamsHolder` | FLOWING |
| `WebcamHolder.drive()` | `cam` (selected cam) | `resolveSelectedCam()` reads `webcams.value` + `webcamPrefs.preferredCam(host).first()` | Yes — from live StateFlow + DataStore | FLOWING |

---

### Behavioral Spot-Checks

Step 7b is SKIPPED for the MJPEG decode path — no runnable MJPEG source is available without starting a camera server. The snapshot and rung-3 paths are LIVE-PROVEN on-device (UAT). Unit suite behaviors are covered by the test files below.

---

### Probe Execution

No probe scripts (`scripts/*/tests/probe-*.sh`) defined for Phase 10. The phase gate is the on-device UAT record (`10-UAT.md`), which is RESOLVED/PASSED.

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| CAM-01 | 10-01..10-08 | Camera — native MJPEG/snapshot stream view; promoted from v2 to Phase 10 | SATISFIED | Full rung ladder implemented (rung-1 MJPEG decoder, rung-2 snapshot poller, rung-3 dead-end card); enumeration spine wired; render surface live; on-device UAT PASSED for rungs 2+3; rung-1 fixture-proven. REQUIREMENTS.md marks Complete. |

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `MjpegStreamDecoder.kt` | 96, 101, 106 | `return null` | INFO | Legitimate stream-end and malformed-part signals, not stubs. Context confirms they are stream-control returns (no further boundary, trailer, no header terminator). Not rendering stubs. |
| `WebcamHolder.kt` | 245 | `return null` | INFO | `resolveSelectedCam()` returns null when cam list is empty — correct "no cam" signal, not a stub. |

No TBD/FIXME/XXX markers found in any production webcam file. No unreferenced debt markers. No placeholder implementations.

---

### Human Verification Required

#### 1. Live MJPEG rung-1 eyeball

**Test:** Stand up a crowsnest or ustreamer camera on the local network (one that serves `multipart/x-mixed-replace`), configure it in Moonraker, open the Webcam page, and observe the live stream.

**Expected:** Smooth live video at the configured fps cap (TARGET_FPS=12); no frozen frames; aspect ratio preserved (never stretched); rounded cutout visible; the "Snapshot ~2fps" badge does NOT appear (that is rung-2 only); no OOM/crash. On navigate-away the stream stops (no continued network activity); on return it resumes.

**Why human:** Neither home printer exposes a `multipart/x-mixed-replace` MJPEG stream (both E3 and E5 run ravens-perch/MediaMTX as `webrtc-mediamtx`). The MJPEG decode path is exercised by the golden byte-stream unit tests (MjpegStreamDecoderTest: with-Content-Length, no-Content-Length, split-JPEG fixtures) and all critical code-review fixes are verified in source (WR-04 `inJustDecodeBounds`, WR-03 double-buffer, WR-01 `cancelAndJoin`), but live visual confirmation requires an environmental MJPEG source that does not currently exist on this developer's network. This is a documented environmental limitation, not a code gap.

---

### Gaps Summary

No blocking gaps. All four ROADMAP success criteria are verified in code. CAM-01 is satisfied. The five code-review fixes (CR-01, CR-02, WR-01, WR-03, WR-04) are confirmed present in source with their corresponding regression tests and git commits. Deferred findings (WR-02, WR-05, WR-06) are user-accepted and tracked in `10-REVIEW.md`. The sole `human_needed` item is the live MJPEG eyeball, which is an environmental limitation (no crowsnest/ustreamer source on the developer's home network) — not a missing implementation.

---

_Verified: 2026-06-04_
_Verifier: Claude (gsd-verifier)_
