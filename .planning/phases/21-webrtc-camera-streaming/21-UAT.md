---
phase: 21-webrtc-camera-streaming
plan: 05
status: pending
created: 2026-06-08
device: flox (genuine Nexus 7 2013 / Adreno 320 / APQ8064 / LineageOS 18.1 / API 30 / armeabi-v7a)
build: app-armeabi-v7a-release-signed.apk (release/R8, debug-signed for sideload — unsigned-for-store until PKG-01/Phase 8)
printers:
  - { name: "Ender 5 Plus", host: "192.168.1.120:7125", backend: "ravens-perch MediaMTX (PRIMARY)" }
  - { name: "Ender 3",      host: "192.168.1.121:7125", backend: "crowsnest-class MediaMTX (SECONDARY)" }
---

# Phase 21 — On-Device UAT (the load-bearing phase gate, D-08)

> THE LOAD-BEARING PHASE GATE. Green host unit tests CANNOT close this phase — latency, hardware
> decode, no-leak, and no-crash-regression are inherently on-device (the mock-vs-reality discipline:
> a too-lenient mock once hid a real server-contract bug behind green units). This UAT proves the
> shipped Media3 H.264 rung (plan 04: `Media3Feed` + `Media3SurfaceHost` + holder routing) LIVE on
> flox against BOTH MediaMTX printers — the exact cameras the Phase-10 MJPEG path could never render.
>
> **A FAIL spawns a gap-closure plan, NOT a phase-complete mark.** Do not mark the phase verified
> until every criterion below is PASS on both printers (or a FAIL is recorded and routed to gap-closure).

**Build/install gate (plan 21-05 Task 1):** `:app:assembleRelease` exit 0; debug-signed via
`sign-release.bat` (exit 0); `adb install -r` on flox = **Success**; launches to `.MainActivity`
(no crash). ✅

**Spike baseline to confirm against** (`21-SPIKE-RESULT.md`, E5+ only, owner-eyeball latency):
RTSP `rtsp://192.168.1.120:8554/3` forced-TCP → READY ~2.3 s, `OMX.qcom.video.decoder.avc` (HARDWARE),
app CPU ~0%, `codecs=avc1.42C01F` 640×480. RTSP = lead, HLS = fallback, no WebRTC escape. The E3 was
NOT separately measured on-device in the spike — **21-05 UAT MUST measure both printers** (D-08).

---

## How to drive the UAT (navigation + capture)

**Navigation path (per printer):** point the app's Moonraker connection at the printer (Settings →
host/port), then **jiib home → swipe up to open the App Drawer → tap the webcam tile → select the
MediaMTX camera**. Live H.264 should render through the `Media3SurfaceHost` SurfaceView with the
required overlay controls (camera picker / pills) drawn ABOVE the video.

**Hardware-decode confirmation (criterion 3):**
```
adb -s 0a64b42e logcat -c   # clear, then open the webcam screen
adb -s 0a64b42e logcat | grep -iE "OMX.qcom|OMX.google|MediaCodec|avc"
```
Expect `OMX.qcom.video.decoder.avc` (HARDWARE) — NOT `OMX.google.*` (software fallback). Watch CPU:
```
adb -s 0a64b42e shell dumpsys cpuinfo | grep -i mees   # ~0% app CPU at steady state = HW-offloaded
```
The orchestrator can assist by capturing logcat, `dumpsys cpuinfo`/`meminfo`, and `screencap` over adb.

**Latency (criterion 2):** spike clock-on-camera method — film a visible running clock with the
printer camera, screencap the tablet, compare timestamps; take the median of a few captures; bar = ~1–2 s.

---

## Success Criteria — record PASS/FAIL per printer

| # | Criterion (Success Criterion / D-ref) | Ender 5 Plus (192.168.1.120) | Ender 3 (192.168.1.121) | Notes |
|---|----------------------------------------|------------------------------|--------------------------|-------|
| 1 | **Live H.264 renders** — correct aspect (no stretch), no frozen frames; the cam MJPEG could never show (D-08) | ⬜ PENDING | ⬜ PENDING | |
| 2 | **Glass-to-glass latency ≤ ~1–2 s** for the lead transport (RTSP), spike clock-on-camera method, median of a few captures (D-05) | ⬜ PENDING | ⬜ PENDING | |
| 3 | **Hardware decode confirmed** — `OMX.qcom.*` in logcat (NOT `OMX.google.*`), steady CPU, no software-fallback jank | ⬜ PENDING | ⬜ PENDING | |
| 4 | **Clean codec/player release on exit** — enter/exit the webcam screen ~5×: no leaked codec, no rising memory, no frozen feed on re-entry (WR-01 class, D-08) | ⬜ PENDING | ⬜ PENDING | |
| 5 | **Fallback intact** — on a cam WITHOUT an H.264 pipe (or by forcing it), fall-through to MJPEG/snapshot still works | ⬜ PENDING | ⬜ PENDING | |
| 6 | **D-20(a) No-crash regression** — the MediaMTX cams that previously crashed the screen no longer crash it | ⬜ PENDING | ⬜ PENDING | |
| 7 | **D-20(b) Drawer tile gates correctly** — greyed when unavailable, live when available | ⬜ PENDING | ⬜ PENDING | |
| 8 | **D-20(c) Per-printer camera selection holds** — switch between the two printers; each retains its own preferred cam (WR-03 null-key edge) | ⬜ PENDING | ⬜ PENDING | |

**Sub-check (Ender 3 only):** crowsnest `stream_url` matched the `:8889/<path>/` → `:8554`(RTSP)/`:8888`(HLS)
derive convention (RESEARCH A2). If it didn't, that's a recorded gap.

| Sub-check | Ender 3 (192.168.1.121) | Notes |
|-----------|--------------------------|-------|
| crowsnest derive-convention holds (RESEARCH A2) | ⬜ PENDING | spike confirmed paths 1/2/4 at Task-1 time; on-device live not re-captured |

*Status legend: ⬜ PENDING · ✅ PASS · ❌ FAIL*

---

## Result

> Fill in after the on-device run. Each ❌ FAIL → record (printer, criterion, observed behavior) and
> route to a gap-closure plan per D-08/D-20. Do NOT mark the phase verified with any PENDING/FAIL row.

**Overall:** _pending owner on-device UAT on both printers._
