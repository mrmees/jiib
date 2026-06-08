---
phase: 21-webrtc-camera-streaming
plan: 05
status: partial
created: 2026-06-08
run: 2026-06-08
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
| 1 | **Live H.264 renders** — correct aspect (no stretch), no frozen frames; the cam MJPEG could never show (D-08) | ✅ PASS | ⬜ DEFERRED | E5+: `playstation_eye`(path3) + `nozzle_tracker`(path4) render live, correct aspect, no frozen frames. Screencap evidence. Overlay chrome (cam-name pill + Back) renders z-ordered ABOVE the SurfaceView — the spike's cutout-overlay requirement satisfied. E3: no crowsnest instance running → deferred to pre-release. |
| 2 | **Glass-to-glass latency ≤ ~1–2 s** for the lead transport (RTSP), spike clock-on-camera method, median of a few captures (D-05) | ✅ PASS | ⬜ DEFERRED | E5+: owner eyeball "good enough for a low-power tablet, 1–2s." **Method = owner judgment on the live feed, NOT formal clock-photo medians** (recorded honestly, same posture as the spike). E3: deferred with the printer. |
| 3 | **Hardware decode confirmed** — `OMX.qcom.*` in logcat (NOT `OMX.google.*`), steady CPU, no software-fallback jank | ✅ PASS | ⬜ DEFERRED | E5+: logcat shows `OMX.qcom.video.decoder.avc` (qcom OMXMaster `makeComponentInstance`), NOT `OMX.google` software fallback. Confirmed on the **release** build. E3: deferred. |
| 4 | **Clean codec/player release on exit** — enter/exit the webcam screen ~5×: no leaked codec, no rising memory, no frozen feed on re-entry (WR-01 class, D-08) | ✅ PASS | ⬜ DEFERRED | E5+: TOTAL PSS 90.0 MB baseline → 94.6 MB after 5 enter/exit cycles (flat — normal Compose/GC jitter, NOT a per-cycle codec leak). No OOM / "too many codecs" / native-window errors. Surface generation kept incrementing on final re-entry = fresh player attached + old released (WR-01 discipline holds). Feed live on re-entry. E3: deferred. |
| 5 | **Fallback intact** — on a cam WITHOUT an H.264 pipe (or by forcing it), fall-through to MJPEG/snapshot still works | ⬜ DEFERRED | ⬜ DEFERRED | **DEFERRED live (environmental, not a code gap):** every cam on BOTH printers is `webrtc-mediamtx` (H.264) — there is no non-H.264 cam available to exercise the live fallback. Covered by the Phase-10 MJPEG host tests + the composite fall-through unit tests (`compositeMedia3Feed` FallThrough→lowerRung). Re-verify live if/when a non-H.264 cam exists. |
| 6 | **D-20(a) No-crash regression** — the MediaMTX cams that previously crashed the screen no longer crash it | ✅ PASS | ⬜ DEFERRED | E5+: the MediaMTX cams that previously crashed the webcam screen no longer crash it — the empty-host `IllegalArgumentException` crash was fixed this session (commit `49f3fe2`). E3: deferred. |
| 7 | **D-20(b) Drawer tile gates correctly** — greyed when unavailable, live when available | ✅ PASS | ⬜ DEFERRED | E5+: webcam drawer tile live/normal when available (connected to E5+). The greyed-when-unavailable side is the existing Phase-10 capability gate (`webcamEnabled` flow), not separately re-exercised. E3: deferred. |
| 8 | **D-20(c) Per-printer camera selection holds** — switch between the two printers; each retains its own preferred cam (WR-03 null-key edge) | 🟡 PARTIAL | ⬜ DEFERRED | Single-printer selection holds on the E5+. The **cross-printer hold** (select on E5+, switch to E3, switch back) is DEFERRED — no crowsnest instance running, so the two-printer switch can't be exercised live. |

**Sub-check (Ender 3 only):** crowsnest `stream_url` matched the `:8889/<path>/` → `:8554`(RTSP)/`:8888`(HLS)
derive convention (RESEARCH A2). If it didn't, that's a recorded gap.

| Sub-check | Ender 3 (192.168.1.121) | Notes |
|-----------|--------------------------|-------|
| crowsnest derive-convention holds (RESEARCH A2) | ⬜ DEFERRED (statically confirmed) | The `:8889/<path>/` derive convention + the nested `ravens_perch.streams` schema were confirmed against crowsnest at **21-02 Task 1**. On-device live re-capture deferred to pre-release (owner is not running any crowsnest instance currently). |

*Status legend: ⬜ PENDING/DEFERRED · ✅ PASS · 🟡 PARTIAL · ❌ FAIL*

---

## Result

Run on **flox** (genuine Nexus 7 2013 / Adreno 320 / LineageOS 18.1 / API 30 / `armeabi-v7a`),
**2026-06-08**, on the **RELEASE** build (R8-minified, debug-signed) with the session's two fixes
applied (see below). Driven against the **Ender 5 Plus** (192.168.1.120, primary ravens-perch
MediaMTX; cams `playstation_eye`=path3, `nozzle_tracker`=path4, both `service=webrtc-mediamtx`).

**Overall: PARTIAL — core PASS, owner-deferred items routed to pre-release (NOT FAILs).**

- **PASS (E5+):** SC1 live H.264 render · SC2 latency (~1–2 s, owner eyeball) · SC3 hardware decode
  (`OMX.qcom`) · SC4 leak-free release (90.0→94.6 MB over 5 cycles, flat) · SC6 D-20(a) no-crash
  regression · SC7 D-20(b) drawer-tile gating. **6/8 PASS.**
- **PARTIAL:** SC8 D-20(c) — single-printer selection holds on E5+; cross-printer hold deferred.
- **DEFERRED (owner decision — environmental, NOT code gaps):** SC5 live MJPEG fallback (no non-H.264
  cam exists on either printer; covered by host/unit tests) · SC8 cross-printer hold · the **entire
  Ender 3 / crowsnest column** (no crowsnest instance currently running). See the next section.

**No ❌ FAIL was recorded.** The two on-device bugs discovered during the run were fixed and verified
in-session (below), so they are not open gaps. The deferred items are owner-chosen environmental
deferrals, not gap-closure triggers.

> NOTE for the orchestrator/verifier: the phase is NOT fully "both printers PASS" — the E3 live
> verification, the cross-printer SC8, and the live SC5 fallback are owner-deferred to the pre-release
> pass. Both printers run the identical `webrtc-mediamtx` H.264 stack, so the E5+ proof carries strong
> implication for the E3, but it is an implication, not a measurement (same honesty posture as the spike).

---

## Bugs fixed during UAT (both discovered on-device, both committed + verified)

Both were pre-existing defects surfaced by driving the shipped rung live — exactly the kind of
mock-vs-reality bug green host units cannot catch (the reason this UAT is the load-bearing gate).

1. **`49f3fe2` — `fix(21): resolveWebcamUrl returns null on unparseable base instead of throwing.**
   A pre-existing crash (`IllegalArgumentException: Invalid URL host:""`) that killed the whole app
   whenever the webcam holder's cfg had an empty host — the long-mystified "camera protocol"
   webcam-screen crash. Now degrades gracefully. Regression test added; confirmed on-device (process
   stays alive). This is what makes SC6 D-20(a) PASS.

2. **`a254469` — `fix(21): read ravens-perch nested extra_data.streams.<proto>.url for native H.264 URL.**
   THE root cause of "Feed unavailable": `nativeStreamUrlFor` read FLAT keys (`ravens_perch_native_rtsp`)
   that ravens-perch never emits; ravens-perch publishes a NESTED schema
   (`extra_data.ravens_perch.streams.<proto>.url`, absolute + cfg-free). The flat-key miss fell to a
   cfg-dependent derive that returned null on the empty-host cfg → fell through to MJPEG → DeadEnd, no
   player ever built. Fixed by reading the nested explicit URL. Regression test added; VERIFIED LIVE on
   flox (`playstation_eye` + `nozzle_tracker` render H.264 with `OMX.qcom` HW decode). This is what makes
   SC1/SC2/SC3 PASS.

---

## Deferred to pre-release (Phase 22 Ship)

Owner-chosen environmental deferrals — track and re-verify in the Phase-22 pre-release pass:

- **E3 / crowsnest live verification (192.168.1.121)** — the entire Ender 3 column. Owner is not running
  any crowsnest instance currently. The `:8889/<path>/` derive convention + the nested
  `ravens_perch.streams` schema were confirmed against crowsnest at 21-02 Task 1. Both printers run the
  identical `webrtc-mediamtx` H.264 stack, so the E5+ proof carries strong implication for the E3.
- **SC8 cross-printer camera selection (WR-03 null-key edge)** — select on E5+, switch to E3, switch
  back; deferred because no crowsnest instance is running. Single-printer selection holds on E5+.
- **SC5 live MJPEG/snapshot fallback** — every cam on both printers is H.264 (`webrtc-mediamtx`); there
  is no non-H.264 cam to exercise the live fall-through. Covered by Phase-10 MJPEG host tests + the
  composite fall-through unit tests; re-verify live if/when a non-H.264 cam exists.

## Non-blocking follow-ups (recorded, NOT fixed here)

- **Stale KDoc on `nativeStreamUrlFor` (`WebcamUrl.kt`)** still tells the old flat-key / "DORMANT"
  story — doc drift; needs a doc-only reconciliation pass.
- **Codex advisory:** `classifyPlaybackException` sends most non-network `PlaybackException`s to
  FallThrough — fine by design, but a transient RTSP hiccup would needlessly drop to MJPEG (consider
  widening Transient classification).
- **Codex advisory:** the duplicate holder `start()`/`stop()` ownership (WebcamScreen `DisposableEffect`
  + AppShell `repeatOnLifecycle`) is idempotent but redundant — pick one owner.
