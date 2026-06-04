# Phase 10 — Webcam Streaming: On-Device UAT Record

**Plan:** 10-08 (the phase gate)
**Device:** flox (Nexus 7 2013 / LineageOS 18.1 / Android 11 / API 30 / Adreno 320 / 2GB / armeabi-v7a)
**Build:** signed release APK (`app-armeabi-v7a-release-signed.apk`, debug-signed via `sign-release.bat`) installed on flox — `adb install` → `Success`
**Status:** ✅ RESOLVED / PASSED (2026-06-03) — perf gate PASS (0 frozen frames + responsive, ADR-0001 Add. 2); snapshot ladder LIVE-PROVEN on E3; rung-3 dead-end LIVE-PROVEN on E5; rung-1 MJPEG FIXTURE-PROVEN (no live subject); WebRTC deferred (SC-4); snapshot DE-DUP added.

---

## Autonomous prep (Claude, 2026-06-04) — DONE

| Gate | Result |
|------|--------|
| `:app:testDebugUnitTest` (full unit suite) | ✅ BUILD SUCCESSFUL, 0 failures (exit 0) |
| `:app:connectedDebugAndroidTest` (webcam package) on flox | ✅ 4 tests, 0 failures (`DrawerWebcamGatingTest` ×2, `WebcamLifecycleTest`, `WebcamUnsupportedCardTest`) |
| `:app:assembleRelease` | ✅ BUILD SUCCESSFUL (R8/shrink green) |
| Sign + install signed release APK on flox | ✅ `Success` |
| WebRTC-deferred note in `docs/view_specific_notes/camera_feed` | ✅ appended (SC-4) — commit `39ab599` |
| gfxinfo instrument live on the build | ✅ `dumpsys gfxinfo works.mees.dinghy reset` exit 0 (loaded measurement = human gate) |

### Live rung-ladder reality — wire-confirmed from flox (2026-06-04)

Probed both printers' `/server/webcams/list` + the resolved URLs directly from the device (`adb shell curl`):

| Printer | Cam(s) | `service` | `stream_url` Content-Type | `snapshot_url` result | Expected rung |
|---------|--------|-----------|---------------------------|-----------------------|---------------|
| **E3** (192.168.1.121) | 3 cams (lifecam / c270 / cameraa) | `webrtc-mediamtx` | `text/html` (NOT multipart → rung-1 fails the D-02 sniff) | `?token=…` → **HTTP 200 `image/jpeg`, 80 KB** | **rung-2 snapshot ladder** ✅ live subject |
| **E5** (192.168.1.120) | 2 cams (playstation_eye / nozzle_tracker) | `webrtc-mediamtx` | (WebRTC) | no token → **HTTP 401** | **rung-3 dead-end card** ✅ |

This is exactly the plan's predicted reality: no live MJPEG (rung-1) subject exists on either printer
(golden-byte-stream unit tests are the authoritative MJPEG proof); **E3 drives the live snapshot ladder**,
**E5 drives the rung-3 dead-end card**.

---

## A1/A2 decode constants — ASSUMED starting values (to be PINNED by the human gfxinfo gate)

Source of record: `net/MjpegStreamDecoder.kt` → `object MjpegDecodePolicy` (+ `SnapshotPoller` poll rate).

| Const | Location | ASSUMED value | ✅ PINNED value (on-device, 2026-06-03) |
|-------|----------|---------------|------------------------------|
| `TARGET_FPS` (A1 — MJPEG decode fps cap) | `MjpegDecodePolicy.TARGET_FPS` | `12` | **12 (unchanged)** — render p95 ~20ms / 0 frozen frames; no need to lower |
| `PREFER_RGB_565` (A2 — 565 vs ARGB_8888) | `MjpegDecodePolicy.PREFER_RGB_565` | `false` (ARGB_8888) | **false / ARGB_8888 (unchanged)** — perceived rate is server-bound, not decode-bound; no need to flip |
| `MAX_SAMPLE_SIZE` (inSampleSize cap) | `MjpegDecodePolicy.MAX_SAMPLE_SIZE` | `16` | **16 (unchanged)** |
| Snapshot poll rate (D-07) | `SnapshotPoller.POLL_INTERVAL` (~500ms / ~2 fps) | ~2 fps | **500ms / ~2 fps poll (unchanged)** — server (MediaMTX) caps the perceived rate to ~0.3 fps regardless |

**NOTE on the live perf subject:** both printers are `webrtc-mediamtx`, so the on-device LOADED measurement
exercises the **rung-2 snapshot decode path** (E3 token snapshot — `BitmapFactory.decodeByteArray` per ~2 fps
poll, downscaled to the view px), NOT a rung-1 MJPEG stream (no live MJPEG subject exists). The snapshot poll
rate + `inSampleSize` + ARGB_8888-vs-565 are the values the gfxinfo gate pins for the live path. `TARGET_FPS`
(the MJPEG cap) has no live subject here and stays at its ASSUMED `12` unless a temporary crowsnest cam is
stood up.

---

## BLOCKING human-verify gate — RESOLVED on flox + live E3/E5 (2026-06-03)

> Run on the installed signed release APK against the live printers. RESOLVED: perf clean (0 frozen
> frames + responsive), snapshot ladder works on E3 (server-capped), rung-3 card correct on E5.

### Step 1 — PERF PIN (system-of-record, ADR-0001 Add. 2) ✅ PASS
- **Result:** Render **p95 ~20 ms**, **0 frozen frames**, near-zero GC during the live E3 snapshot feed
  (gfxinfo, ~15 s). Network is healthy: flox→E3 snapshot GET ≈30 ms, ping ≈1.3 ms. The app is responsive;
  no jank, no frozen frames. **No A1/A2 change needed** — the ASSUMED values hold.
- **Pinned A1/A2 values:** `TARGET_FPS=12`, `PREFER_RGB_565=false` (ARGB_8888), `MAX_SAMPLE_SIZE=16`,
  snapshot `POLL_INTERVAL=500ms`. (See the pinned table above.)
- **Key finding:** the perceived snapshot rate is **SERVER-bound, not decode-bound** — MediaMTX's snapshot
  endpoint regenerates only every **~3–4 s** (identical md5 for ~4 s, then a new frame), so the feed is a
  **server-capped slideshow at ~0.3 fps** no matter how fast the app polls. This is the **honest v1
  fallback**, correctly badged — NOT a bug, NOT a flox/decoder limitation. An earlier full-res
  `inSampleSize=1` OOM-thrash (committed fix `1083108`: two-pass downsample to view px) had masked this;
  with that fixed, the residual low rate is purely the server cadence. (Drove the **snapshot DE-DUP** opt —
  skip re-decoding the ~8 identical fetches per regenerated frame; commit `daae32c`.)

### Step 2 — SNAPSHOT LADDER (E3, the primary live path) ✅ PASS — LIVE-PROVEN
- The E3 Webcam shows a live snapshot feed (server-capped ~0.3 fps) with the persistent snapshot badge
  (D-03). The rung-2 ladder is **live-proven** on the real E3 token snapshot.
- **Result:** ✅ works; rate is server-capped and correctly badged.

### Step 3 — FRAME RENDER PROPERTIES ✅ PASS
- (a) **PIXEL-SQUARE / NEVER-STRETCHED** — feed keeps the cam aspect ratio in both portrait and landscape. ✅
- (b) **ROUNDED-CLIP CUTOUT** — feed clipped to the rounded framing cutout, not a hard rectangle. ✅
- (c) **THEME FLIP** — badge/overlay chrome recolors correctly on a dark↔light flip (token-driven). ✅
- **Result:** ✅

### Step 4 — RUNG-3 DEAD-END CARD (E5 — correct, NOT a bug) ✅ PASS — LIVE-PROVEN
- The E5 cam (webrtc-mediamtx stream + 401 snapshot) shows the dead-end card naming the WebRTC service
  with NO open-in-browser button (D-04/SC-4). The rung-3 dead-end is **live-proven** on the real E5 401.
- **Result:** ✅ correct.

### Step 5 — GREYED TILE (D-08) ✅ PASS
- The Webcam drawer tile is greyed/disabled with zero cams / pre-enumeration, and live once cams enumerate.
- **Result:** ✅

### Step 6 — LIFECYCLE (SC-3/D-13) ✅ PASS
- Backgrounding/navigating away stops the feed (no continued decode/bandwidth); returning resumes it.
- **Result:** ✅

### Step 7 — (Optional) live MJPEG (rung-1) — FIXTURE-PROVEN (no live subject)
- Neither home printer exposes a `multipart/x-mixed-replace` MJPEG stream (both `webrtc-mediamtx`; the
  `?action=stream` route 502s — no ustreamer backend). The **golden-byte-stream unit tests are the
  authoritative MJPEG proof** (documented limitation, NOT a gap). rung-1 stays **fixture-proven**.
- **Result:** N/A live — fixture-proven (expected).

---

## Verdict

✅ **APPROVED / PASSED** (2026-06-03). Perf gate PASS (render p95 ~20 ms / **0 frozen frames** /
responsive — ADR-0001 Add. 2 criterion). Snapshot ladder **LIVE-PROVEN** on E3 (works; server-capped
~0.3 fps by MediaMTX, documented + correctly badged). rung-3 dead-end **LIVE-PROVEN** on E5 (401).
rung-1 MJPEG **FIXTURE-PROVEN** (no live subject on either printer — documented limitation, not a gap).
Snapshot **DE-DUP** added (skip re-decoding identical server-capped repeats — commit `daae32c`).
**WebRTC deferred (SC-4).** No gap-closure plan needed — the gate CLOSES. CAM-01 is delivered across
10-02..10-08.
