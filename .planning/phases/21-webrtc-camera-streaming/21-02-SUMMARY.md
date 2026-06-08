---
phase: 21-webrtc-camera-streaming
plan: 02
subsystem: ui
tags: [media3, exoplayer, rtsp, hls, webcam, h264, spike, adreno320]

# Dependency graph
requires:
  - phase: 21-01
    provides: media3 1.10.1 (exoplayer + rtsp + hls) wired into the build, minSdk-23 floor proven, Wave-0 RED scaffolds
provides:
  - "21-SPIKE-RESULT.md — the recorded D-02 verdict that plans 21-03/21-04 consume (lead/fallback/WebRTC-escape/cutout + the 21-04 overlay-controls requirement)"
  - "On-device proof that Media3 RTSP-over-forced-TCP hardware-decodes (OMX.qcom.video.decoder.avc) the ravens-perch MediaMTX H.264 on the real Adreno 320"
  - "RTSP-vs-HLS lead settled: RTSP leads (~2.3s startup vs HLS ~5.0s), HLS is the fallback rung"
  - "SPS/PPS-in-fmtp Open Question resolved (PRESENT on MediaMTX); WebRTC escape (D-03) NOT triggered"
affects: [21-03, 21-04, 21-05]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Throwaway on-device spike gates the shipped design (D-02): measure on real silicon before building the rung"
    - "Decoder name + PlaybackException-absence as the SPS/PPS-in-fmtp presence signal"

key-files:
  created:
    - .planning/phases/21-webrtc-camera-streaming/21-SPIKE-RESULT.md
  modified: []

key-decisions:
  - "Lead transport = RTSP (forced TCP); fallback = HLS — RTSP startup ~2.3s vs HLS ~5.0s on flox"
  - "WebRTC escape (D-03) NOT needed — D-05 latency bar accepted via owner eyeball sign-off on the live RTSP feed"
  - "Cutout = square corners for the H.264 video surface (unclipped); owner aesthetic call, not silently picked"
  - "21-04 MUST overlay the required webcam controls (camera picker, rung pills/badges) z-ordered ABOVE the SurfaceView — the chrome-less spike is not the shipped UX"
  - "Decoder is OMX.qcom.video.decoder.avc (HARDWARE) on both RTSP and HLS; app CPU ~0% steady-state (HW-offloaded)"
  - "SPS/PPS-in-fmtp PRESENT on MediaMTX (RTSP prepare decoded with zero decoder-init error) — RESEARCH Pitfall 1 resolved"

patterns-established:
  - "Spike-gated design: the recorded DECISION line in 21-SPIKE-RESULT.md is the single source of truth plans 03/04 build against"

requirements-completed: [CAM-10]

# Metrics
duration: 1day (spike build fc7e854 Task 1, owner ran the on-device measurement, then verdict recorded)
completed: 2026-06-08
---

# Phase 21 Plan 02: Throwaway On-Device Spike (D-02) Summary

**Media3 RTSP-over-forced-TCP hardware-decodes (OMX.qcom.video.decoder.avc, ~0% CPU) the ravens-perch MediaMTX H.264 on the real Adreno 320 with ~2.3s startup — RTSP leads, HLS falls back, WebRTC escape not needed, square-corner cutout, and 21-04 must overlay controls above the SurfaceView.**

## Performance

- **Duration:** Task 1 spike build committed `fc7e854`; Task 2 was a blocking human-verify checkpoint — owner ran the spike on flox and returned the measured verdict; this plan close-out records it.
- **Completed:** 2026-06-08
- **Tasks:** 2 (Task 1 auto/committed; Task 2 checkpoint resolved by owner on-device run)
- **Files modified:** 1 new artifact (21-SPIKE-RESULT.md) + the metadata commit

## Accomplishments
- **The gating D-02 deliverable is settled.** The throwaway `SpikeActivity` (`fc7e854`) played live RTSP AND HLS of the Ender 5 Plus camera on flox; the measured readout + owner verdict are recorded in `21-SPIKE-RESULT.md`.
- **RTSP-vs-HLS lead decided (D-04):** RTSP leads (~2.3s BUFFERING→READY, repeatable) vs HLS (~5.0s segment tax). HLS retained as the fallback rung.
- **Hardware decode confirmed (CAM-10 / Adreno 320 question):** `OMX.qcom.video.decoder.avc` on both transports, app CPU ~0% steady-state — decode fully offloaded.
- **SPS/PPS-in-fmtp resolved (RESEARCH Pitfall 1):** RTSP `prepare()` rendered with zero decoder-init exception → parameter sets PRESENT on MediaMTX; the highest-historical-risk unknown clears in MediaMTX's favour on this silicon.
- **D-03 WebRTC escape NOT triggered:** the ~1–2s D-05 bar accepted by owner eyeball sign-off on the live RTSP feed. Default Media3-only path holds; `libwebrtc` stays out.

## Task Commits

1. **Task 1: Build the throwaway SpikeActivity** - `fc7e854` (feat) — Media3 RTSP (forced-TCP) + HLS, raw SurfaceView, decoder/SDP logging. Committed prior to this close-out.
2. **Task 2: Run the spike on flox + record DECISION** - checkpoint (blocking human-verify). Resolved by the owner running the installed spike on flox against the Ender 5 Plus and returning the measured data + the lead/fallback/WebRTC/cutout decision. Recorded in `21-SPIKE-RESULT.md`.

**Plan metadata:** docs commit (this SUMMARY + STATE.md + ROADMAP.md).

## Files Created/Modified
- `.planning/phases/21-webrtc-camera-streaming/21-SPIKE-RESULT.md` — the recorded D-02 readout + DECISION line consumed by plans 21-03/21-04 (lead=RTSP, fallback=HLS, no WebRTC escape, square cutout, + the 21-04 overlay-controls requirement).

## Decisions Made
- **lead = RTSP · fallback = HLS** — RTSP's ~2.3s startup beat HLS's ~5.0s on flox; both hardware-decode the same 640×480 avc1.
- **WebRTC escape (D-03) = N** — owner accepted the latency bar by eyeball on the live RTSP feed (recorded honestly as the method, not a clock-photo median).
- **cutout = square** — owner aesthetic call: the H.264 video surface stays unclipped (no rounded-mask chrome on the SurfaceView).
- **21-04 overlay requirement (owner-raised, non-negotiable):** the camera picker, rung pills/badges, and any other required controls MUST render as a sibling layer Z-ordered ABOVE the SurfaceView. The bare spike was chrome-less by design; the shipped rung must draw control chrome on top of the video. Captured as a consumable finding in 21-SPIKE-RESULT.md.

## Deviations from Plan

None - plan executed as written. The plan's must-have wording asked for "median of repeated captures … on BOTH printers"; the owner instead accepted RTSP latency by on-device eyeball sign-off and measured only the Ender 5 Plus this session. This is recorded transparently (not as a deviation requiring auto-fix) — the E3 number and the formal glass-to-glass medians are explicitly deferred to the load-bearing UAT (plan 21-05, D-08), and no E3/APK/latency numbers were fabricated.

## Issues Encountered
None. RTSP played first try with `setForceUseRtpTcp(true)` (no 461), HLS played with benign OMXNodeInstance extension-probe noise (not playback errors).

## Known Stubs / Honesty Notes
- **E3 (Ender 3 / crowsnest MediaMTX):** NOT separately measured on-device this session. Derive convention confirmed at Task-1 time; RTSP lead expected to hold (same stack) — to be MEASURED in plan 21-05 UAT. No E3 latency number invented.
- **APK-size delta (RTSP-only vs +HLS):** not separately captured this session; footprint win vs WebRTC documented in the feasibility doc. No APK KB number fabricated.
- **SpikeActivity is throwaway:** `app/src/main/java/works/mees/dinghy/spike/SpikeActivity.kt` is deleted in plan 21-04 — an intentional, plan-scheduled removal, not a lingering stub.

## User Setup Required
None - dev-controlled LAN, tokenless RTSP/HLS pipes; no external service configuration.

## Next Phase Readiness
- **21-03 (pure logic)** can proceed: lead=RTSP, fallback=HLS is locked; `selectsH264Rung` + `deriveNativeStreamUrl` build against the confirmed `:8889→:8554/:8888` convention.
- **21-04 (player binding)** MUST honor: forced-TCP RTSP as lead, square cutout, and the overlay-controls-above-SurfaceView requirement; it also deletes the throwaway spike.
- **21-05 (UAT)** MUST close the deferred items: the E3 on-device measurement and the formal glass-to-glass latency on BOTH printers (D-08).

## Self-Check: PASSED
- `21-SPIKE-RESULT.md` exists, contains "DECISION", "lead", and the 21-04 overlay requirement; no fabricated E3/APK/latency numbers.
- Task 1 commit `fc7e854` confirmed in `git log`.

---
*Phase: 21-webrtc-camera-streaming*
*Completed: 2026-06-08*
