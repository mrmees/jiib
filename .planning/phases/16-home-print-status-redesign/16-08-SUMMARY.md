---
phase: 16-home-print-status-redesign
plan: 08
subsystem: testing
tags: [uat, on-device, perf, babystep, gfxinfo, adreno-320, moonraker, print-status]

# Dependency graph
requires:
  - phase: 16-06
    provides: four-state PrintStatusScreen + babystep row (SET_GCODE_OFFSET Z_ADJUST=±n MOVE=1) + PrintStatusUiModel
  - phase: 16-07
    provides: four-state Print-Status UI LAW doc-merge (README + LAYOUT flexible-tile law)
provides:
  - Recorded on-device UAT results closing the three binding SC gates (SC-5 babystep sign, SC-3 perf, SC-4 monitor loop)
  - Device-verified babystep sign correctness (Compress = nozzle closer = homing_origin[2] decreases) — no sign flip needed
  - Adreno-320 perf no-regression evidence (p95 53ms event-driven, 0 frozen frames) vs Phase-5 48.64ms baseline
  - Traceability for 6 live-UAT visual fixes already committed during the loop
affects: [17-fine-tune, 18-output, 19-system-info, 20-webrtc, 21-ship]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "On-device binding gates close phase: host suites cannot prove sign/perf on real Adreno 320 — flox + live printer is the only valid evidence ([[dinghy-display-mock-vs-reality]])"
    - "gfxinfo p95 for a sparse event-driven (no-animation, D-13) screen is a known artifact — judge on 0-frozen-frames + responsiveness (ADR-0001 Addendum 2), not the janky-% headline"

key-files:
  created:
    - .planning/phases/16-home-print-status-redesign/16-UAT.md
  modified: []

key-decisions:
  - "Babystep sign 16-06 implemented is CORRECT — device-verified on a live first layer, NOT guess-flipped"
  - "Terminal end-state VISUAL eyeball DEFERRED (owner declined to burn the running print); Terminal MODE logic is host-unit-tested (PrintStatusUiModelTest) — non-blocking, tracked open item"

patterns-established:
  - "Pattern: a plan whose sole deliverable is recorded on-device gate results + traceability for fixes committed during the live loop"

requirements-completed: [SC-3, SC-4, SC-5]

# Metrics
duration: 10min
completed: 2026-06-06
---

# Phase 16 Plan 08: On-Device Binding Gates Summary

**The three binding on-device gates (SC-5 live babystep sign, SC-3 Adreno-320 perf, SC-4 monitor loop) PASS on real flox + a live Ender 5 Plus — babystep direction is device-confirmed correct, no perf regression, monitor loop preserved; the Terminal end-state visual is the one tracked deferral.**

## Performance

- **Duration:** ~10 min (results-recording; gates were owner-run on-device)
- **Started:** 2026-06-06
- **Completed:** 2026-06-06
- **Tasks:** 3 (all `checkpoint:human-verify`, owner-run on-device)
- **Files modified:** 1 created (`16-UAT.md`)

## Accomplishments
- **SC-5 PASS** — On a live first layer (E5 Plus, release build): Compress = nozzle CLOSER + `gcode_move.homing_origin[2]` DECREASES; Expand = farther + increases. On-screen direction matches physical nozzle. The sign 16-06 implemented (did NOT guess-flip) is verified correct. Center cell cycles steps; row hides past the early-layer window; session-only (Z-offset → 0.000 after resume, no SAVE_CONFIG).
- **SC-3 PASS** — Steady-state printing framestats on genuine Adreno 320 (RELEASE/R8/armeabi-v7a): p50 42ms / p90 48ms / p95 53ms / p99 57ms; **0 missed vsync, 0 high input latency, 0 frozen frames**. The 95% "janky" is the known `gfxinfo` artifact for a sparse event-driven (~2.4 redraws/s, D-13 no-animation) screen. p95 53ms is ~4ms over the Phase-5 48.64ms baseline (richer four-state focus / Benchy hero) — within no-regression. Owner: interaction "feels fine."
- **SC-4 PASS (core)** — connect → temps/progress/Z-offset/layer update live; Pause → Resume round-trips; four states classify correctly off real printer state. Monitor → drive-a-print loop behavior-preserving (the core value).

## Task Commits

This plan's deliverable is the recorded `16-UAT.md` + this SUMMARY. The plan made **no production code changes** — the three tasks are on-device human-verify gates owner-ran on real hardware, and the visual fixes surfaced during the UAT loop were committed as production code during that loop (listed under Files / Deviations, NOT re-committed here).

**Plan metadata:** `docs(16-08): record on-device binding-gate UAT results (SC-3/4/5 PASS)` — covers `16-UAT.md`, `16-08-SUMMARY.md`, `STATE.md`, `ROADMAP.md`.

## Files Created/Modified
- `.planning/phases/16-home-print-status-redesign/16-UAT.md` — Recorded cleanup-gate result + SC-5/SC-3/SC-4 outcomes + the deferred Terminal-visual item + an "On-device findings fixed during UAT" traceability section.

## Decisions Made
- **Babystep sign is correct as-implemented** — device-verified on a live first layer rather than trusted from a mock. The single highest-risk item in the phase (a wrong sign is physically consequential on a first layer, invisible to unit tests) passed on the first run with no flip.
- **Terminal end-state VISUAL eyeball deferred by owner** (declined to burn a running print, mirroring the Phase-14 mid-print-switch deferral). The Terminal MODE logic is covered by host unit tests (`PrintStatusUiModelTest` — controls/launcher/error-flag), so the deferral is the on-device VISUAL only, tracked as an open `human_needed` item, non-blocking for phase completion.

## Deviations from Plan

None to this plan's own scope — the three gates were run and recorded as specified.

**Visual issues caught + fixed during the on-device UAT loop (already committed; recorded for traceability):**
hands-on UAT on the four-state screen surfaced six visual issues the green host suites missed (the recurring [[dinghy-display-mock-vs-reality]] pattern). All were fixed live + re-verified on flox and are already in git — they are **not** re-committed by this finalization:

1. Icon-only field buttons (LauncherTile + Tune stub) — `0950b57`
2. Standby launcher grid: Drawer tile absorbs the leftover cell (no empty gap / no double-width More) — `23205ec`
3. Standby focus enlarged (glance fonts + icon) — `c507fe7`
4. Ring-relative Paused pause overlay (was fixed-sp / cropped) — `cc2c6d4`
5. Standby Benchy brand image crop-FILLS the focus both orientations + launcher glyph swaps — `6815424`
6. Paused focus un-clipping: status label outside the dim layer; ProgressRing arcs inset by half-stroke; pause-circle glyph un-boxed — `e7b0126`

---

**Total deviations:** 0 to plan scope. 6 on-device visual fixes (committed during the UAT loop, recorded for the audit trail).
**Impact on plan:** None — all gates passed; the visual fixes are part of the live iteration the on-device gates exist to enable.

## Issues Encountered
None during the gate runs. The six on-device visual findings were resolved live and re-verified on flox.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- The four-state Print-Status home is the **visual foundation** for Phases 17–20 and is now device-proven: babystep sign correct, no perf regression on the Adreno-320 floor, monitor loop preserved.
- **Phase 16 execution is now 8/8 plans complete.** Orchestrator runs phase verification + `phase.complete` next (do NOT mark the phase complete here).
- **One tracked open item:** eyeball the Terminal end-state VISUAL (clean hero + Dismiss/Reprint passive; Terminal(Error) ≤3 error lines) opportunistically when a print next ends naturally. Close it + flip the corresponding UAT line then; non-blocking now.

## Self-Check: PASSED

- `16-UAT.md` exists ✓
- `16-08-SUMMARY.md` exists ✓
- All 6 referenced on-device finding-fix commits present in git (`0950b57`, `23205ec`, `c507fe7`, `cc2c6d4`, `6815424`, `e7b0126`) ✓

---
*Phase: 16-home-print-status-redesign*
*Completed: 2026-06-06*
