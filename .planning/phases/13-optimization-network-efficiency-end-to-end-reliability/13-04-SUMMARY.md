---
phase: 13-optimization-network-efficiency-end-to-end-reliability
plan: 04
subsystem: testing
tags: [moonraker, on-device-uat, save-config, firmware-restart, network-drop, reconnect, d-09, flox, binding-gate]

# Dependency graph
requires:
  - phase: 13-optimization-network-efficiency-end-to-end-reliability
    plan: 02
    provides: visible/disconnect-driven/self-healing klippy-restart recovery (the headline fix under test)
  - phase: 13-optimization-network-efficiency-end-to-end-reliability
    plan: 03
    provides: refreshProbeZOffset removal (the cadence-fix backstop the UAT proves did not regress probe freshness)
provides:
  - 13-UAT.md — the recorded dual-printer dual-scenario on-device UAT (the binding D-09 phase gate)
  - First-run FAIL evidence (G-A1 Home-bounce + G-B1 silent mid-print network drop) that drove the 13-05 gap-closure
  - PASSING re-run result (Run #2 after 13-05) — the binding D-09 gate is satisfied on live hardware
affects: [13-05]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "The binding live gate is the backstop the project's mock-vs-reality lessons mandate — green units are necessary, NOT sufficient"
    - "A FAIL at the live gate spawns a gap-closure plan (13-05) rather than marking the phase complete — the gate is honored, not bypassed"

key-files:
  created: []
  modified:
    - .planning/phases/13-optimization-network-efficiency-end-to-end-reliability/13-UAT.md

key-decisions:
  - "Task 2 (the binding gate) FAILED on the first run — caught two real, on-hardware-only defects (G-A1 Home-bounce on recovery + G-B1 silent half-open mid-print drop) that the full GREEN unit suite missed. The gate worked exactly as designed."
  - "Per the plan's own output rule, a FAIL does NOT mark the phase complete — it spawns a gap-closure plan (13-05). 13-04 was held open (no SUMMARY) until the re-run passed."
  - "The re-run (Run #2, after 13-05) PASSED on both live printers per Matthew (\"Both pass\") — so 13-04's binding gate is now satisfied and finalized via this SUMMARY."

patterns-established:
  - "Capture FAIL evidence in 13-UAT.md ## Gaps with code-confirmed root causes so the gap-closure plan has a precise target (not a vibe)"

requirements-completed: []

# Metrics
duration: ~ (manual on-device gate; spans the 13-05 gap-closure between the two runs)
completed: 2026-06-03
---

# Phase 13 Plan 04: Dual-Printer Dual-Scenario On-Device UAT — The Binding D-09 Gate Summary

**The binding phase gate ran on the real flox tablet against both live printers (E5 + E3): Task 1 built/signed/installed the release APK on flox and scaffolded 13-UAT.md to match Task 2's acceptance rows one-to-one; Task 2 — the dual-printer dual-scenario human-verify gate — FAILED on the first run, catching two on-hardware-only defects that the full GREEN unit suite missed (G-A1: recovery bounced the app to Home; G-B1: a silent mid-print WiFi drop was never detected, the feed just froze). That FAIL spawned the 13-05 gap-closure (it did NOT mark the phase complete — the gate was honored). After 13-05's three fixes, the RE-RUN (Run #2) PASSED on both live printers per Matthew ("Both pass"): SAVE_CONFIG recovery shows a visible Syncing splash → resumes the feed → registers a new print → stays on the current screen; a mid-print WiFi drop is now detected via keepalive → full Syncing splash → resync. The binding D-09 gate is satisfied.**

## Performance

- **Duration:** spans the 13-05 gap-closure (Task 1 build + first-run FAIL → 13-05 fixes → passing re-run)
- **Completed:** 2026-06-03
- **Tasks:** 2 (Task 1 build+install+scaffold; Task 2 human-verify gate — first run FAIL, re-run PASS after 13-05)
- **Files modified:** 1 (13-UAT.md — scaffolded, recorded the FAIL with code-confirmed gaps, then finalized PASSED on Run #2)

## Accomplishments

- **Task 1 (auto):** ran the full `:app:testReleaseUnitTest` GREEN, built `:app:assembleRelease` SUCCESSFUL, debug-signed via `E:\Android\sign-release.bat`, `adb install -r` onto flox, and scaffolded 13-UAT.md with every check slot (E5×a, E5×b, E3×a, E3×b, the D-03 Syncing-splash visual, the Probe-Calibrate stale-config backstop, and the Temp/Move/Files live-data spot-checks) matching Task 2's acceptance rows one-to-one — no fabricated results. (Commit `b07a511`.)
- **Task 2 (human-verify, first run) — FAILED, and that is the point:** the binding gate caught two defects no green unit test could:
  - **G-A1 (MINOR/UX):** Scenario A (SAVE_CONFIG / klippy-restart recovery) worked — feed resumed, print registered — but the app bounced to the Home/Print-Status screen instead of staying where the user was.
  - **G-B1 (BLOCKING):** Scenario B (mid-print network drop) failed entirely — toggling the tablet WiFi off produced NO Syncing splash and NO resync; the feed just froze (numbers stopped). Code-confirmed root cause: `MoonrakerSocket.defaultClient()` had **no `pingInterval`**, so a half-open TCP socket was never detected → `onFailure` never fired → `SocketEvent.Closed` was never emitted → the reconnect supervisor never ran. (The exact 4th mock-vs-reality strike: `FakeWebSocket` synthesizes `Closed`; real OkHttp never does without keepalive.) (First-run result recorded in `07402c2`.)
- **Held the gate honestly:** per the plan's output rule, the FAIL did NOT mark the phase complete — it spawned the 13-05 gap-closure plan with both gaps root-caused in 13-UAT.md ## Gaps. 13-04 was deliberately left without a SUMMARY until the re-run passed.
- **Task 2 (re-run, Run #2 after 13-05) — PASSED:** on both live printers per Matthew ("Both pass"):
  - **Scenario A (E5 + E3):** SAVE_CONFIG → visible Syncing splash → feed resumes (no app restart) → new print registers (printState→Printing) → **stays on the current screen** (G-A1 fixed).
  - **Scenario B (E5 + E3):** mid-print WiFi off → within ~one keepalive interval a **full Syncing splash** appears (drop now DETECTED) → restore WiFi → print state resyncs (G-B1 fixed).
  - **D-03:** the ~600ms min-dwell makes the Syncing splash perceptible on EVERY recovery (both the klippy-restart and socket-reconnect paths).
  - **Backstops:** Probe-Calibrate z_offset fresh after SAVE_CONFIG (no stale-config regression from the 13-03 `refreshProbeZOffset` removal); Temp graph / Move / Files still show correct live data (SC-3 behavior-preserving).

## Task Commits

1. **Task 1: build + sign + install on flox + scaffold 13-UAT.md** — `b07a511` (docs)
2. **Task 2: first-run on-device UAT result (Scenario A PASS w/ Home-bounce, Scenario B FAIL)** — `07402c2` (test)

The re-run PASS is recorded in 13-UAT.md and finalized with this plan's metadata commit (alongside 13-05's). The 13-05 fixes that turned the gate green: `404e00e` (G-B1a pingInterval keepalive), `5451638` (G-A1 nav-state hoist), `777a74d` (G-B1b socket-reconnect Splash + ~600ms min-dwell).

## Files Created/Modified

- `.planning/phases/13-.../13-UAT.md` — scaffolded to match Task 2 acceptance one-to-one; recorded the first-run FAIL with code-confirmed gaps (G-A1, G-B1); finalized to **PASSED (Run #2 after 13-05)**.

## Decisions Made

- **The gate's FAIL was a success of the methodology, not a failure of execution.** Two on-hardware-only defects (one BLOCKING) surfaced exactly where the project's mock-vs-reality lessons predict — at the live gate, behind a green unit suite. Documenting them precisely (with code-confirmed root causes) gave 13-05 a clean target.
- **Honored the plan's output rule:** a FAIL spawns a gap-closure plan, never a phase-complete mark. 13-04 stayed SUMMARY-less until the re-run passed; this SUMMARY closes it only now that it has.

## Deviations from Plan

None — the plan executed exactly as written, including its FAIL path. The first-run FAIL is the plan's designed behavior (its acceptance criteria explicitly state "any FAIL spawns a gap-closure plan; do NOT mark the phase complete on a FAIL"), not a deviation.

## Known Stubs

None.

## Threat Flags

None — this plan EXERCISES the existing flox↔printer LAN surface (real klippy restart + real mid-print drop); it introduces no new endpoints, auth paths, file access, or schema. Threat register T-13-09 (reconnect-storm DoS) and T-13-10 (mid-restart subscribe rejection) were the live pass conditions and held on the re-run.

## Issues Encountered

The first-run FAIL is documented above as the gate's designed outcome. Both gaps were root-caused tablet-side (independent of printer) and fixed in 13-05; the re-run confirmed the fixes on real hardware.

## User Setup Required

None — no external service configuration. (The UAT itself requires the human operator + the two live printers, which is the nature of the binding manual gate.)

## Next Phase Readiness

- **The binding D-09 gate is SATISFIED.** The headline klippy-restart freeze AND the silent mid-print-drop class are both dead on real hardware (both printers). Phase 13 may proceed to verification/completion.
- No blockers. (Phase-complete is the orchestrator's call after the verifier runs — not marked here.)

## Self-Check: PASSED

- 13-UAT.md exists and reads **PASSED (2026-06-03, Run #2 after 13-05)** with all Scenario-A/B rows on both printers PASS, D-03 PASS, Probe + Temp/Move/Files spot-checks PASS.
- Task commits verified in git log: `b07a511` (Task 1 scaffold), `07402c2` (Task 2 first-run result).
- The 13-05 fix commits referenced are present: `404e00e`, `5451638`, `777a74d`.

---
*Phase: 13-optimization-network-efficiency-end-to-end-reliability*
*Completed: 2026-06-03*
