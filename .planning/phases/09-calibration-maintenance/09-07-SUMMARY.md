---
phase: 09-calibration-maintenance
plan: 07
subsystem: ui
tags: [calibration, nav-wiring, probe-calibrate, on-device-uat, reliability]

# Dependency graph
requires:
  - phase: 09-04
    provides: CalibrationHubScreen, screws-tilt screen
  - phase: 09-05
    provides: Tilt (z-tilt/qgl) + bed-mesh screens, amber ConfirmGuard
  - phase: 09-06
    provides: ProbeCalibrateHolder/Screen, D-15 delete gate
provides:
  - Calibration nav wiring (single drawer tile -> hub -> five routine sub-routes, D-14)
  - On-device UAT sign-off (flox + live Ender 3) for all five calibration routines + D-15 delete scoping
  - Probe-Calibrate UAT rework (display/jog/safety; manual_probe partial-diff fix; macro-feedback hero)
affects: [phase-close, promoted-reliability-phase]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Calibration is a lean LOCAL hub->routine back-stack under Dest.Calibration (mirrors Dest.Macros), not five top-level Dests"
    - "Status objects (manual_probe, like bed_mesh/heaters) MUST field-merge partial diffs; rebuilding from a delta wipes omitted fields (is_active)"
    - "The manual-probe live Z is the // Z position macro feedback, NOT the divergent manual_probe.z_position status field"

key-files:
  modified:
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/java/works/mees/dinghy/ui/calibration/ProbeCalibrateScreen.kt
    - app/src/main/java/works/mees/dinghy/calibration/ProbeCalibrateHolder.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt
    - app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt
  created:
    - app/src/main/res/drawable/{detector,expand,arrow_upward,arrow_downward,add,remove}.xml
---

# Phase 9 Plan 7: Calibration Nav Wiring + On-Device UAT Sign-Off

**Wire the five calibration routines into the shell behind one drawer tile, then verify the whole
calibration surface on real flox + a live printer. UAT passed for all calibration features; a
cross-cutting session-layer reliability bug (SAVE_CONFIG re-handshake does not restore the live feed)
was discovered and deferred to the promoted reliability phase.**

## Task 1 — Nav wiring + G2 regression (committed `ea95a19`)

- `Dest.Calibration` + ONE "Calibration" drawer tile (tune glyph, D-14). `AppShell` builds all five
  calibration holders via `remember(store)` (re-keyed on spine rebuild) and renders a lean LOCAL
  hub->routine back-stack (mirrors `Dest.Macros`, NOT five top-level Dests); BackHandler pops a routine
  to the hub. Two `TiltHolder`s (z_tilt/qgl) share ONE `TiltScreen` via an applied-selector lambda +
  per-variant dispatch key. `Dest.Calibration` added to the swipe-drawer-suppression set.
- `KlippyReadyResyncTest` EXTENDED with a SAVE_CONFIG shutdown->ready test asserting the re-issued
  `objects.subscribe` frame CONTAINS the calibration objects (payload-level regression guard).

## Task 2 — On-device UAT (flox + live Ender 3, 2026-06-03)

The app was driven on real flox against a **live Ender 3 Pro (`192.168.1.121`, klicky probe)**. All six
checklist items were exercised; items 3, 4 and (the probe page in) 6 were substantially reworked on
device — those reworks are committed.

| # | Item | Result |
|---|------|--------|
| 1 | Calibration hub + routing | PASS |
| 2 | Bed-mesh heatmap render + Adreno-320 gfxinfo gate | PASS |
| 3 | Z-Tilt / QGL run-and-converge | PASS (reworked on device, `517ab4a`) |
| 4 | Screws-tilt guided loop | PASS (reworked on device, `b47a955`) |
| 5 | Probe-Calibrate Accept -> SAVE_CONFIG | PASS (page reworked this session — see below) |
| 6 | D-15 delete-during-print scoping | PASS — only the active/paused file undeletable; others deletable; all deletable when idle |

### Probe-Calibrate rework (committed `2287acb`, `76a4598`, `3d87e5a`, `4f65fa0`)

On-device iteration turned up real bugs and UX gaps that the green unit suite missed (the recurring
mock-vs-reality strike):

- **`manual_probe` partial-diff field-wipe (real bug):** the reducer rebuilt `ManualProbeObject` from
  each partial `notify_status_update`, so a `TESTZ` move (which omits the unchanged `is_active`) reset
  `isActive->false` and collapsed the live session to "Accepted" mid-probe. Now field-merges onto the
  retained object like `bed_mesh`/heaters; regression test replays the real klicky probe->TESTZ Z=20->end.
- **Macro feedback vs status field:** the live hero + captured offset now read the `// Z position:`
  console value (`bracket.current`), not the divergent `manual_probe.z_position` (which reported 0.001
  while the console showed 4.8).
- **Display:** detector->expand->nozzle icons; three-value readout — resulting offset (hero, accent, 1.5x)
  = `Z - saved`, with `saved` (negated; Moonraker stores positive) and head `Z` on line 2.
- **Jog:** rotated to two vertical columns; +/- step selector (.005-10mm); arrow up/down nudge;
  `MAX_TESTZ_MM` 5->25 (the 10mm step was clamped to 5).
- **Safety/UX:** Home All gate (no PROBE_CALIBRATE unhomed), "Starting..." feedback, TESTZ in-flight
  disables the nudge, Abort->Idle (no Save of a discarded run), dismissable error toasts (Probe-Calibrate
  + Bed Mesh), fresh-instance `reset()` on entry, `refreshProbeZOffset()` requery on entry.

## DEFERRED — session-layer reliability bug (promoted phase)

The 09-07 must-have *"SAVE_CONFIG ... self-heals via the existing G2 re-handshake — no force-stop"* did
**NOT hold on the Ender 3**. After a SAVE_CONFIG the entire live subscription froze (Home stuck on the
last finished print; a new print never registered; Files "Start" stuck on "Starting...") until the app
was force-restarted — the `05-10` G2 fix is not holding on the E3. The calibration FEATURES are
unaffected and verified; this is a cross-cutting session bug. Captured as a HIGH-priority item
(`.planning/todos/pending/save-config-rehandshake-not-refreshing-config.md`) and assigned to the
**promoted Optimization / Network Efficiency / End-to-End Reliability phase** (moved to next-after-9 by
owner decision — see STATE.md). The probe-calibrate screen masks it via the on-entry `refreshProbeZOffset`.

## Status

UAT-PASSED for the calibration surface (BEDM-01 / BEDL-01 / ZCAL-01 / CALIB-01..06). Phase 9 closes with
the reliability defect documented and deferred to the promoted next phase.
