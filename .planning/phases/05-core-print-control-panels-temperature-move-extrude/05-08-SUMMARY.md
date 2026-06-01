---
phase: 05-core-print-control-panels-temperature-move-extrude
plan: 08
subsystem: ui-shell + on-device-verification
tags: [navigation, app-drawer, routing, perf-gate, adreno-320, on-device-uat, sc-5, gaps]

# Dependency graph
requires:
  - phase: 05-05
    provides: TemperatureScreen + holder (multi-trace graph panel)
  - phase: 05-06
    provides: MoveScreen + MoveHolder (jog/home/override)
  - phase: 05-07
    provides: ExtrudeScreen + ExtrudeHolder (extrude/retract/load-unload, cold-extrude gate)
  - phase: 04-07
    provides: RootController + AppShell + AppDrawer (greyed tiles to wire live)
provides:
  - "Temperature/Move/Extrude reachable from the App Drawer; AppShell renders each full-bleed off per-session holders"
  - "05-PERF-RESULTS.md — the D-06 multi-trace perf re-measurement (live PASS on flox)"
  - "SC-5 end-to-end on-device UAT record + three discovered gaps (G1 blocker crash, G2 stale feed, G3 stale config)"
affects: [gap-closure-cycle, phase-05-completion]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Live drawer tile wiring: set dest on previously-greyed tiles; DrawerTile auto-renders accent + clickable (no other change)"
    - "AppShell when(dest) branch builds each holder remember(store){ *Holder(scope, store) } off the SAME live per-session store, onBack = { dest = Dest.PrintStatus }"

key-files:
  created:
    - .planning/phases/05-core-print-control-panels-temperature-move-extrude/05-PERF-RESULTS.md
    - .planning/phases/05-core-print-control-panels-temperature-move-extrude/05-08-SUMMARY.md
  modified:
    - app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/test/java/works/mees/dinghy/ui/shell/ShellPresenceTest.kt

key-decisions:
  - "Drawer tile→panel mapping: Move tile→Dest.Move, Temp tile→Dest.Temperature, Tools tile→Dest.Extrude. Files/Macros/Devices/Power remain greyed/inert."
  - "ShellPresenceTest inertness assertion retargeted Move→Files (Move is now live)."
  - "D-06 perf gate re-measured LIVE on the full Temperature screen (48.64 ms p95); Phase-3 50.1 ms superseded, NOT grandfathered."
  - "SC-5 UAT is PARTIAL — 3a/3b PASS, 3c surfaced a blocking app crash on printer-rejected gcode (G1). Phase NOT complete; routes to gap closure."

metrics:
  completed: 2026-06-01
  duration: "on-device session"
  tasks_completed: 3
  status: nav-wiring-complete; perf-PASS; SC-5-gaps-found
---

# Phase 5 Plan 8: Wire Panels + On-Device Perf & SC-5 UAT Summary

Wired the three new control panels (Temperature/Move/Extrude) into the App Drawer + shell routing, then
re-measured the D-06 multi-trace perf gate (LIVE PASS, ~17 ms margin) and ran the SC-5 end-to-end
print-control UAT on real flox + the live Ender 5 Plus. The panels are functionally and visually correct,
but the on-device UAT exposed three defects in the **shared command-dispatch + connection layers** (NOT in
the panels themselves) — one of which is a process-killing blocker. The phase's own deliverable (nav
wiring) is COMPLETE; the SC-5 UAT is `gaps_found` and routes to a gap-closure cycle.

## What landed

### Task 1 — Nav wiring — DONE (committed)

- `TopRoute.Dest` extended to `{ PrintStatus, Temperature, Move, Extrude, Settings }`.
- `AppDrawer.DRAWER_TILES`: Move tile → `Dest.Move`, Temp tile → `Dest.Temperature`, Tools tile →
  `Dest.Extrude` (live, accent, clickable). Files/Macros/Devices/Power stay greyed/inert.
- `AppShell.when(dest)`: three new branches each `remember(store){ *Holder(scope, store) }` off the SAME
  live per-session store the PrintStatusHolder uses, rendering the screen with `onBack = { dest = Dest.PrintStatus }`.
- `ShellPresenceTest` inertness assertion retargeted Move → Files (Move is now live).
- `:app:compileReleaseKotlin` + full unit suite GREEN.

Commits:
- `b338c8c` feat(05-08): wire Temperature/Move/Extrude into Dest + drawer + shell routing
- `8181218` test(05-08): retarget greyed-tile inertness assertion Move → Files
- `3c129e6` docs(05-08): record checkpoint-pending (Task 2 on-device perf gate)

Release built, debug-signed (`app/build/outputs/apk/release/app-armeabi-v7a-release-signed.apk`),
installed + launched on flox — confirmed it launches to the real routed RootController shell (NOT the
Phase-1 scaffold).

### Task 2 — D-06 multi-trace Temperature perf gate — PASS

Re-measured on flox `0a64b42e` (LineageOS 18.1 / Adreno 320, RELEASE debug-signed) against the live
Ender 5 Plus during a real PLA heat ramp. Full record in `05-PERF-RESULTS.md`.

- **LIVE multi-trace Temperature** during the real PLA ramp: **p95 48.64 ms** | p50 39.27 | p90 46.57 |
  max 61.00 | **frames > 700 ms: 0**.
- Gate (Phase-3 A-variant two-part): p95 ≤ ~66 ms AND 0 frozen frames → **PASS** (~17 ms headroom, 0 frozen).
- Cadence tracks the ~3 Hz data feed (no Choreographer loop); allocation-free onDraw (inherited from 05-04).
- **Fill-rate attribution baseline** (bench `render` scene): fill ON p95 44.45 vs fill OFF p95 30.28 →
  the translucent area-fill costs **~14 ms p95** on Adreno 320 (the primary fill-rate regression lever).
  Canonical filled aesthetic stays ON; `--ez nofill` is a bench-only A-B lever.
- The isolated 97.72 ms fill-on **max** was a single outlier (not frozen, absent from the live capture).
- The Phase-3 isolated **50.1 ms is superseded** by this live full-screen number — NOT grandfathered.
- None of the three Phase-3 re-open conditions triggered.

### Task 3 — SC-5 end-to-end print-control UAT — PARTIAL (one blocker crash)

On flox + the live Ender 5 Plus:

- **3a — cold-extrude gate (EXTR-04): PASS.** Extrude/Retract correctly DISABLED while cold, ENABLED when
  hot. The live `can_extrude` boolean is the authoritative gate; confirmed safe at the temp boundary.
- **3b — extrude/retract/distance-ceiling/load-unload (EXTR-01/02/03): PASS.** Filament moved; the
  max-extrude-distance ceiling disabled over-limit steps; Load/Unload behaved (dispatch-vs-popup).
- **3c — move panel: PARTIAL.** Jog/home worked in-range, BUT jogging past the printer's kinematic limits
  **CRASHES THE APP (repeatable)** — see G1. Because the amber Override path (MOVE-02) deliberately jogs
  unhomed axes, it would trip the same crash and **could not be tested** until G1 is fixed.

## Outcome

- Nav wiring (this plan's own deliverable): **COMPLETE** + committed.
- D-06 perf gate: **PASS** (live, full screen, re-measured per the mandate).
- SC-5 UAT: **gaps_found** — 3a/3b PASS, 3c blocked by a crash. The panels are functionally/visually
  correct; the failures are in the **shared command-dispatch + connection layers** that 05-05/05-06/05-07
  all call. **The phase is NOT complete** — it routes to a gap-closure cycle (`/gsd-plan-phase 5 --gaps`).

## Gaps

Three gaps discovered during the on-device SC-5 UAT. All are in shared infrastructure, not the panels.

### G1 — HIGH / BLOCKER — printer-rejected gcode crashes the entire app

- **Symptom:** A gcode the printer rejects (e.g. an out-of-range move) **kills the whole process** (and the
  FGS, which then auto-restarts). Repeatable; reproduced 4× during the UAT.
- **Evidence (logcat, ×4):** `FATAL EXCEPTION: DefaultDispatcher-worker-N` /
  `T3.Y: Move out of range: 418.000 -9.000 32.000 [20.000]`, stack
  `T3.m.c (JSON-RPC response) → M3.c.b (command dispatcher) → h3.K.j → h3.h.n → J2.a.q → e3.F.run`
  (a bare coroutine task on `Dispatchers.Default`, unsupervised).
- **Root cause:** when Moonraker returns a JSON-RPC **ERROR** response for a gcode (`printer.gcode.script`
  rejected, e.g. "Move out of range"), the command-dispatch path lets that error throw **UNCAUGHT on an
  unsupervised background coroutine**, killing the process. This affects **ALL printer-rejected gcode**
  (out-of-range moves, failing macros, heater faults) — not just moves.
- **Implicated code:** the JSON-RPC request/response correlation + the command dispatch layer —
  `net/JsonRpc.kt`, `net/MoonrakerSession.kt`, and/or the dispatcher the 05-05/05-06/05-07 panels call via
  `GCODE_SCRIPT`.
- **Required fix:** catch JSON-RPC errors from `gcode.script` and surface them as a **NON-FATAL UI error**
  (reuse the existing Severity popup mechanism), never throw into an unsupervised scope.
- **Blocks:** the MOVE-02 amber Override path (jogs unhomed axes → would trigger the same crash), so
  Override is untested until G1 is fixed.

### G2 — HIGH — status feed dies after a Klipper FIRMWARE_RESTART / config reload

- **Symptom:** after a Klipper `FIRMWARE_RESTART` / `printer.cfg` reload, the **status feed dies** —
  temps/positions stop updating while gcode commands still work. **Reopening the app does NOT fix it** (the
  persistent FGS keeps the stale session alive); only a full force-stop recovers.
- **Root cause:** no re-handshake / re-`objects/subscribe` on Moonraker's `notify_klippy_ready` (klippy
  reconnect). The websocket to Moonraker **stays up** across a klippy restart, so the one-time handshake
  never re-runs and the cleared subscription is never re-established.
- **Implicated code:** `net/MoonrakerSession.kt` (handshake / subscription lifecycle),
  `service/MoonrakerService.kt`.

### G3 — MED (same root as G2) — one-shot reads go stale across a config reload

- **Symptom:** the 05-03 one-shot handshake reads (configfile `min_extrude_temp` /
  `max_extrude_only_distance`, and the `objects`/capabilities list — macros, sensors, heaters, extruder
  count) go **STALE across a config reload until app relaunch**. Confirmed live: an edited
  `min_extrude_temp` was not reflected until the app was killed and reopened.
- **Root cause:** these are only read once at handshake and never refreshed on `notify_klippy_ready` (the
  same missing re-handshake as G2).
- **Implicated code:** same as G2 — fixing the `notify_klippy_ready` re-handshake (re-running the one-shot
  reads + re-deriving capabilities) closes both G2 and G3.

## Deviations from Plan

None during this recording task. Task 1 executed as planned (prior executor). Tasks 2 and 3 are on-device
human-verify gates whose REAL measured results are recorded here verbatim. No source code was modified in
this recording cycle — the gap fixes happen in a later gap-closure cycle.

## Self-Check: PASSED

- `05-PERF-RESULTS.md` created — FOUND.
- `05-08-SUMMARY.md` created — FOUND.
- Task 1 commits `b338c8c`, `8181218`, `3c129e6` — all present in `git log`.
