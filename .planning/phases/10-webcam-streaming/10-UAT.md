# Phase 10 — Webcam Streaming: On-Device UAT Record

**Plan:** 10-08 (the phase gate)
**Device:** flox (Nexus 7 2013 / LineageOS 18.1 / Android 11 / API 30 / Adreno 320 / 2GB / armeabi-v7a)
**Build:** signed release APK (`app-armeabi-v7a-release-signed.apk`, debug-signed via `sign-release.bat`) installed on flox — `adb install` → `Success`
**Status:** ⏳ AUTONOMOUS PREP DONE — awaiting the BLOCKING human-verify gate (perf pin + visual UAT)

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

| Const | Location | ASSUMED value | Pinned value (fill at gate) |
|-------|----------|---------------|------------------------------|
| `TARGET_FPS` (A1 — MJPEG decode fps cap) | `MjpegDecodePolicy.TARGET_FPS` | `12` | ⬜ |
| `PREFER_RGB_565` (A2 — 565 vs ARGB_8888) | `MjpegDecodePolicy.PREFER_RGB_565` | `false` (ARGB_8888) | ⬜ |
| `MAX_SAMPLE_SIZE` (inSampleSize cap) | `MjpegDecodePolicy.MAX_SAMPLE_SIZE` | `16` | ⬜ |
| Snapshot poll rate (D-07) | `SnapshotPoller` (~500ms / ~2 fps) | ~2 fps | ⬜ |

**NOTE on the live perf subject:** both printers are `webrtc-mediamtx`, so the on-device LOADED measurement
exercises the **rung-2 snapshot decode path** (E3 token snapshot — `BitmapFactory.decodeByteArray` per ~2 fps
poll, downscaled to the view px), NOT a rung-1 MJPEG stream (no live MJPEG subject exists). The snapshot poll
rate + `inSampleSize` + ARGB_8888-vs-565 are the values the gfxinfo gate pins for the live path. `TARGET_FPS`
(the MJPEG cap) has no live subject here and stays at its ASSUMED `12` unless a temporary crowsnest cam is
stood up.

---

## BLOCKING human-verify gate — UAT script (run on flox + live E3/E5)

> Run each step on the installed signed release APK. Record the result inline. "Approved" only if the perf pin
> is clean (0 frozen frames) AND the snapshot ladder works on E3 AND the rung-3 card is correct on E5.

### Step 1 — PERF PIN (system-of-record, ADR-0001 Add. 2) ⬜
1. Configure the connection to **E3** (192.168.1.121:7125) in Settings.
2. `adb shell dumpsys gfxinfo works.mees.dinghy reset`
3. Open the **Webcam** page on an E3 cam; let the snapshot feed run **~15 s**.
4. `adb shell dumpsys gfxinfo works.mees.dinghy` → require **p95 within budget + 0 frozen frames**.
5. If jank/frozen frames appear: lower the snapshot decode work (step `inSampleSize` up / flip `PREFER_RGB_565`→true), re-install, RE-MEASURE.

- **Result:** ⬜ (paste the gfxinfo Total/Janky/95th/99th/frozen numbers here)
- **Pinned A1/A2 values:** ⬜

### Step 2 — SNAPSHOT LADDER (E3, the primary live path) ⬜
- Confirm the E3 Webcam shows a live **~2 fps snapshot feed** with the persistent **"Snapshot ~2fps" badge** (D-03).
- **Result:** ⬜

### Step 3 — FRAME RENDER PROPERTIES (on-device-gate-only — this is their verification home) ⬜
- (a) **PIXEL-SQUARE / NEVER-STRETCHED** — feed keeps the cam aspect ratio, never stretched/squashed, in **BOTH portrait and landscape** (rotate the device, re-check). ⬜
- (b) **ROUNDED-CLIP CUTOUT** — feed clipped to the rounded framing cutout (edge loss OK), not a hard rectangle. ⬜
- (c) **THEME FLIP** — badge/overlay chrome recolors correctly on a dark↔light theme flip (token-driven, no glitch/recreation). ⬜
- **Result:** ⬜

### Step 4 — RUNG-3 DEAD-END CARD (E5 — correct, NOT a bug) ⬜
- Select an **E5** cam (webrtc-mediamtx stream + 401 snapshot). Confirm the dead-end card **names the WebRTC service** and has **NO open-in-browser button** (D-04/SC-4).
- **Result:** ⬜

### Step 5 — GREYED TILE (D-08) ⬜
- On a printer/connection with zero cams (or before enumeration), confirm the Webcam drawer tile is **greyed/disabled**, and **live** once cams enumerate.
- **Result:** ⬜

### Step 6 — LIFECYCLE (SC-3/D-13) ⬜
- Navigate away / background the app while a feed runs → confirm the feed **stops** (no continued decode/bandwidth); returning **resumes** it.
- **Result:** ⬜

### Step 7 — (Optional) live MJPEG (rung-1) ⬜
- If a crowsnest/ustreamer MJPEG cam is stood up, point Dinghy at it and confirm a live moving feed. Otherwise the golden-byte-stream unit tests are the authoritative MJPEG proof (no live MJPEG subject on E3/E5 — expected).
- **Result:** ⬜ (N/A unless a temp MJPEG cam is stood up)

---

## Verdict

⬜ **PENDING** — type "approved" if the perf pin is clean (0 frozen frames) + snapshot ladder works on E3 +
rung-3 card correct on E5; OR describe the gaps (a FAIL spawns a gap-closure plan, NOT a phase-complete
mark — the 10-08 SUMMARY + CAM-01-complete are withheld until the re-run passes).
