---
phase: 21-webrtc-camera-streaming
plan: 05
subsystem: testing
tags: [media3, exoplayer, h264, rtsp, hls, mediamtx, webcam, uat, on-device]

# Dependency graph
requires:
  - phase: 21-04
    provides: "the shipped Media3 H.264 rung (Media3Feed + Media3SurfaceHost + holder routing)"
  - phase: 21-02
    provides: "the D-02 spike baseline (RTSP lead, OMX.qcom HW decode, ~2.3 s READY on flox/E5+)"
provides:
  - "Recorded on-device UAT (21-UAT.md) — the load-bearing phase gate: core SCs PASS on flox/E5+ on the RELEASE build"
  - "On-device proof the shipped H.264 rung renders live MediaMTX cams the Phase-10 MJPEG path could never show"
  - "Two on-device-only bugs found, fixed, and verified live (empty-host crash + nested ravens-perch URL miss)"
affects: [phase-22-ship, webcam, pre-release-conformance]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "On-device UAT as the load-bearing phase gate (green host units cannot close a camera phase — mock-vs-reality)"
    - "Honest method recording: owner-eyeball latency labelled as such, not a fabricated clock-photo median"
    - "Owner-chosen environmental deferral (no E3/crowsnest, no non-H.264 cam) recorded as DEFERRED, not FAIL"

key-files:
  created:
    - .planning/phases/21-webrtc-camera-streaming/21-05-SUMMARY.md
  modified:
    - .planning/phases/21-webrtc-camera-streaming/21-UAT.md

key-decisions:
  - "Phase result is PARTIAL: 6/8 SC PASS + SC8 PARTIAL on E5+; E3/crowsnest, cross-printer SC8, and live SC5 fallback are owner-deferred to Phase 22 (environmental, not code gaps)"
  - "Latency SC2 accepted via owner eyeball (~1–2 s), recorded honestly as owner judgment NOT a clock-photo median (same posture as the 21-02 spike)"
  - "Two on-device bugs (49f3fe2 empty-host crash, a254469 nested ravens-perch URL) were the actual root causes of the webcam-screen crash and 'Feed unavailable'; fixed + verified live, so not open gaps"
  - "No ❌ FAIL recorded → no gap-closure plan spawned; deferred items routed to the pre-release pass"

patterns-established:
  - "Pattern: record the UAT method (owner eyeball vs formal median) inline so a future reader does not over-trust a sign-off"
  - "Pattern: environmental deferral (no hardware to exercise a path) is DEFERRED + tracked-to-pre-release, distinct from a FAIL"

requirements-completed: [CAM-17]

# Metrics
duration: ~20min
completed: 2026-06-08
---

# Phase 21 Plan 05: On-Device UAT (the load-bearing phase gate) Summary

**The shipped Media3 H.264 rung renders live on the real Adreno 320 (flox, RELEASE build) from the Ender 5 Plus MediaMTX cams — hardware-decoded (`OMX.qcom`), ~1–2 s latency, leak-free over 5 enter/exit cycles, no crash — proving on-device what the Phase-10 MJPEG path could never show; two on-device-only bugs (empty-host crash, nested ravens-perch URL) were found, fixed, and verified live.**

## Performance

- **Duration:** ~20 min (results-recording + tracking pass; the on-device run was owner-driven)
- **Started:** 2026-06-08
- **Completed:** 2026-06-08
- **Tasks:** 2 (Task 1 build+scaffold = `43706fb`, earlier; Task 2 on-device UAT = owner-driven, recorded here)
- **Files modified:** 2 (21-UAT.md, 21-05-SUMMARY.md) + STATE.md/ROADMAP.md/REQUIREMENTS.md tracking

## Accomplishments

- **Closed the load-bearing on-device phase gate (D-08) for the Ender 5 Plus** on the RELEASE
  (R8-minified, debug-signed) build: SC1 live H.264 render · SC2 ~1–2 s latency (owner eyeball) ·
  SC3 hardware decode (`OMX.qcom.video.decoder.avc`, NOT `OMX.google`) · SC4 leak-free release
  (TOTAL PSS 90.0→94.6 MB over 5 enter/exit cycles, flat) · SC6 D-20(a) no-crash regression ·
  SC7 D-20(b) drawer-tile gating. **6/8 PASS, SC8 PARTIAL.**
- **Proved the spike→shipped capability live:** the spike (21-02) measured a chrome-less RTSP feed
  on E5+; this UAT confirms the SHIPPED rung — overlay chrome (cam-name pill + Back) z-ordered ABOVE
  the SurfaceView (the spike's consumable cutout-overlay requirement), fresh-player-on-re-entry surface
  discipline (WR-01), and both `playstation_eye` + `nozzle_tracker` cams rendering H.264 on real silicon.
- **Found, fixed, and verified two on-device-only bugs** (commits `49f3fe2`, `a254469`) — the actual
  root causes of the long-mystified webcam-screen crash and the "Feed unavailable" failure. Green host
  units never caught either; the live run did (the mock-vs-reality discipline that makes this gate
  load-bearing).
- **Recorded owner-chosen deferrals honestly** (E3/crowsnest, cross-printer SC8, live SC5 fallback) as
  DEFERRED-to-pre-release, NOT FAILs — no gap-closure plan spawned.

## Task Commits

1. **Task 1: Build + install the release H.264 build + scaffold 21-UAT.md** — `43706fb` (docs) — committed earlier in the session.
2. **Task 2: On-device UAT on flox (owner-driven) + record results** — results recorded into `21-UAT.md` in the plan-metadata commit (no separate per-task code commit; this task is the human-verify gate).

**Bugs fixed during the UAT (committed in-session, outside the per-task commits):**
- `49f3fe2` — `fix(21): resolveWebcamUrl returns null on unparseable base instead of throwing`
- `a254469` — `fix(21): read ravens-perch nested extra_data.streams.<proto>.url for native H.264 URL`

**Plan metadata:** (this commit) `docs(21-05): complete on-device UAT plan`

## Files Created/Modified

- `.planning/phases/21-webrtc-camera-streaming/21-UAT.md` — filled in: SC1/2/3/4/6/7 PASS (E5+) with
  evidence, SC8 PARTIAL, SC5 + E3 column + cross-printer SC8 DEFERRED; status → `partial`; the two fixes
  recorded under "Bugs fixed during UAT"; a "Deferred to pre-release (Phase 22)" section and a
  non-blocking-follow-ups section added.
- `.planning/phases/21-webrtc-camera-streaming/21-05-SUMMARY.md` — this summary.

## Decisions Made

- **Result = PARTIAL, not FAIL.** Core SCs PASS on E5+ on the release build; the un-run items
  (E3/crowsnest, cross-printer SC8, live SC5 fallback) are environmental — there is no crowsnest
  instance running and no non-H.264 cam on either printer — so they are owner-deferred to the
  Phase-22 pre-release pass, not gap-closure triggers. Both printers run the identical
  `webrtc-mediamtx` H.264 stack, so the E5+ proof carries strong implication for the E3 (an
  implication, not a measurement).
- **SC2 latency method recorded honestly:** owner eyeball ("good enough for a low-power tablet, 1–2s"),
  NOT formal clock-photo medians — same posture as the 21-02 spike, so a future reader does not mistake
  the sign-off for a measured median.

## Deviations from Plan

The plan's Task 2 was a blocking `checkpoint:human-verify`; the owner ran the UAT and reported results.
Two on-device-only bugs were discovered during the run and auto-fixed (correctness — Rule 1):

### Auto-fixed Issues

**1. [Rule 1 - Bug] Empty-host webcam URL crashed the whole app**
- **Found during:** Task 2 (on-device UAT, webcam screen)
- **Issue:** `resolveWebcamUrl` threw `IllegalArgumentException: Invalid URL host:""` when the webcam
  holder's cfg had an empty host — the long-mystified "camera protocol" webcam-screen crash, fatal to
  the whole process.
- **Fix:** `resolveWebcamUrl` now returns null on an unparseable base instead of throwing → degrades
  gracefully.
- **Files modified:** the webcam URL resolution path (`WebcamUrl.kt` area) + regression test.
- **Verification:** regression test added; confirmed on-device (process stays alive). Makes SC6 PASS.
- **Committed in:** `49f3fe2`

**2. [Rule 1 - Bug] Native H.264 URL read the wrong (flat) schema → "Feed unavailable"**
- **Found during:** Task 2 (on-device UAT, attempting to play the MediaMTX cams)
- **Issue:** `nativeStreamUrlFor` read FLAT keys (`ravens_perch_native_rtsp`) that ravens-perch never
  emits; ravens-perch publishes a NESTED schema (`extra_data.ravens_perch.streams.<proto>.url`, absolute
  + cfg-free). The flat-key miss fell to a cfg-dependent derive that returned null on the empty-host cfg
  → fell through to MJPEG → DeadEnd, no player ever built. THE root cause of "Feed unavailable".
- **Fix:** read the nested explicit URL.
- **Files modified:** the native-URL resolution path (`WebcamUrl.kt` area) + regression test.
- **Verification:** regression test added; VERIFIED LIVE on flox (`playstation_eye` + `nozzle_tracker`
  render H.264 with `OMX.qcom` HW decode). Makes SC1/SC2/SC3 PASS.
- **Committed in:** `a254469`

---

**Total deviations:** 2 auto-fixed (both Rule 1 — correctness bugs that were the actual root causes of
the webcam crash + "Feed unavailable"). Both committed and verified live in-session.
**Impact on plan:** Essential for the gate to pass — without them no feed renders. No scope creep; they
are the on-device discoveries the UAT exists to surface.

## Issues Encountered

- The shipped rung initially showed "Feed unavailable" on real cams (root cause = the nested-URL schema
  miss) and the webcam screen could crash on an empty-host cfg. Both were on-device-only — green host
  units passed throughout. Resolved by `a254469` and `49f3fe2` respectively, then re-verified live.

## Non-blocking follow-ups (recorded, NOT fixed here)

- **Stale KDoc on `nativeStreamUrlFor` (`WebcamUrl.kt`)** still tells the old flat-key / "DORMANT" story
  — doc drift; needs a doc-only reconciliation pass.
- **Codex advisory:** `classifyPlaybackException` sends most non-network `PlaybackException`s to
  FallThrough — fine by design, but a transient RTSP hiccup would needlessly drop to MJPEG (consider
  widening the Transient classification).
- **Codex advisory:** the duplicate holder `start()`/`stop()` ownership (WebcamScreen `DisposableEffect`
  + AppShell `repeatOnLifecycle`) is idempotent but redundant — pick one owner.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- **Phase 21 execution is complete (5/5 plans).** The load-bearing on-device gate is satisfied on the
  Ender 5 Plus on the release build. **Orchestrator runs phase verification next — do NOT mark the
  phase verified here.**
- **Deferred to the Phase-22 pre-release pass** (tracked in 21-UAT.md): live E3/crowsnest verification,
  cross-printer SC8 (WR-03 null-key edge), and the live SC5 MJPEG fallback (needs a non-H.264 cam).
- The three non-blocking follow-ups above are candidates for the Phase-22 conformance/cleanup sweep.

## Self-Check: PASSED

- 21-UAT.md exists (contains "PASS") ✓
- 21-05-SUMMARY.md exists ✓
- Bug-fix commits verified in git log: `49f3fe2`, `a254469` ✓; Task-1 scaffold `43706fb` ✓

---
*Phase: 21-webrtc-camera-streaming*
*Completed: 2026-06-08*
