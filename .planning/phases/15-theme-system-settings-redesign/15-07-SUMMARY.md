---
phase: 15-theme-system-settings-redesign
plan: 07
subsystem: ui
tags: [theming, color-pool, graphview, temperature, printstatus, move, semantic-tokens]

# Dependency graph
requires:
  - phase: 15-03 (token model)
    provides: ThemeTokens.pool (contrast-ranked data pool), Directional(temperature, xy, z), the transitional @Deprecated violet shim
  - phase: 15-06 (Settings redesign)
    provides: theme-editor reseed / palette-mode entry points the UAT drives against
provides:
  - GraphView temperature traces wired to pool[i % size] (violet shim retired)
  - Print-Status + Temperature heater readouts on the matching pool index (stable same-sensor-same-color identity)
  - Move XY jog-pad + Z-row directional outlines on directional.xy / directional.z
  - Heat-semantic split honored exactly — only the named heater-readout sites migrate; the caution/Warn-intent .heat set stays
affects: [theming-follow-on, shape-status, conformance-sweep, ship]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Data-pool consumers wrap pool[i % pool.size] — no arbitrary pool-size cap; index never overruns a draw (D-14)"
    - "Stable sensor identity: a sensor's GraphView trace index == its Print-Status/Temperature readout index == its color, everywhere"
    - "Directional-plane outlines pull from Directional(xy, z); generic chrome/accent stays t.accentLine"

key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/render/GraphView.kt
    - app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt
    - app/src/main/java/works/mees/dinghy/theme/ThemeTokens.kt
    - app/src/main/java/works/mees/dinghy/theme/BakedTokens.kt

key-decisions:
  - "Heater identity = pool[0] (= directional.temperature), NOT the old amber .heat — heaters now read as heaters by color+label, not by amber"
  - "MAX_TRACES stays 3 (no current printer shows >3 graph traces); consumers wrap pool[i % size] so the cap never crashes"
  - "Only the named heater-readout sites migrate; the ~26-file caution/Warn-intent .heat set is untouched (heat = caution)"
  - "Move homed/unhomed status color + force-move lock-shape deliberately deferred to the shape-status follow-on (out of scope here)"

patterns-established:
  - "Pool wiring: pool[i % pool.size] for traces/readouts; pool[0] for the primary-trace fill at low alpha"
  - "Same-sensor-same-color is a load-bearing invariant across graph / Print-Status / Temperature"

requirements-completed: [D-13, D-14, D-16]

# Metrics
duration: ~40min
completed: 2026-06-05
---

# Phase 15 Plan 07: Pool-Wired Data Surfaces Summary

**GraphView traces + Print-Status/Temperature heater readouts rewired to the contrast-ranked data pool with a stable same-sensor-same-color index, Move directional outlines on directional.xy/.z, and the transitional violet shim deleted grep-clean — on-device flox UAT approved 6/6.**

## Performance

- **Duration:** ~40 min (code), spanning the UAT cycle
- **Started:** 2026-06-05T10:54:07-05:00 (first task commit)
- **Completed:** 2026-06-05 (UAT approved)
- **Tasks:** 3 (2 code + 1 on-device UAT gate)
- **Files modified:** 6

## Accomplishments

- **Three-consumer pool rewire** — the single payoff of the whole exercise:
  - **GraphView** (`applyTokens`): each temperature trace reads `pool[i % pool.size]` (D-13/D-14 wrap); the translucent under-fill reads `pool[0]` at low alpha. The old `heat`/`accent`/`violet` per-trace identities are retired.
  - **Heater readouts by canonical sensor index** — `TemperatureScreen.traceColor` returns `pool[index % size]`, and `PrintStatusScreen` nozzle/bed/chamber cells read the matching pool index (nozzle = `pool[0]`, bed = `pool[1]`, chamber = `pool[2]`). The readout index equals the GraphView trace index, so a sensor is the same color everywhere — same-sensor-same-color identity holds.
  - **Move directional outlines** — XY jog-pad arrows (`JogCell`) → `t.directional.xy`; Z-row controls (`JogTall`) → `t.directional.z`.
- **Violet shim deleted, grep-clean** — the `@Deprecated violet` get-only shim from 15-03 is removed from `ThemeTokens.kt` and the stale KDoc reference dropped from `BakedTokens.kt`. `grep -rn "\.violet\|val violet" app/src/main/java/` returns nothing.
- **Heat-semantic split honored exactly** — raw `.heat` occurrences went **66 → 61** across the source tree; only the named heater-readout sites (GraphView trace, `TemperatureScreen.traceColor`, the PrintStatus heater cells) migrated off `.heat`. The ~26 caution/Warn-intent `.heat` uses (Console WARNING, ConfirmGuard, OutlinedControl, AppDrawer, Files, Spool, Prompt, Move's own 5 control uses, etc.) are **untouched** — `heat` = caution stays.

## Task Commits

1. **Task 1: Rewire GraphView + Temperature + Print-Status heater readouts to the pool; delete the violet shim** — `9e0499a` (refactor)
2. **Task 2: Rewire Move directional-plane outlines to directional.xy / directional.z** — `bcc7102` (refactor)
3. **Task 3: On-device flox UAT — pool-wired data surfaces** — checkpoint gate, **APPROVED 6/6** (no commit; verification gate)

**Plan metadata:** (this SUMMARY + STATE + ROADMAP, committed separately)

## Files Created/Modified

- `render/GraphView.kt` — traces → `pool[i % size]`, fill → `pool[0]`; `MAX_TRACES` unchanged; KDoc updated to the pool model; no `violet`.
- `ui/temperature/TemperatureScreen.kt` — `traceColor(index, t)` → `pool[index % size]`; retired the heat/accent/violet switch.
- `ui/printstatus/PrintStatusScreen.kt` — nozzle/bed/chamber heater cells → matching pool index (stable identity with the graph).
- `ui/move/MoveScreen.kt` — `JogCell` (XY) outline → `directional.xy`; `JogTall` (Z) outline → `directional.z`; Home / active distance-step stay `accentLine`; status-color/lock-shape deferred; Move's 5 `.heat` caution uses untouched.
- `theme/ThemeTokens.kt` — deleted the `@Deprecated violet` shim.
- `theme/BakedTokens.kt` — dropped the stale violet-shim KDoc reference.

## On-Device UAT (Task 3 — APPROVED)

Run on the real flox device against a live printer. All six steps PASS:

1. **Temperature graph traces distinct** — PASS (contrast-ranked pool; no two traces collapse to the same hue on the Adreno-320 GPU).
2. **Same-sensor-same-color** across graph / Print-Status / Temperature — PASS (heaters read as heaters by color + label even though no longer amber).
3. **Reseed stability** — PASS (traces stay separated and identity holds across a reseed/randomize).
4. **Palette modes** — PASS (Simple → mono, High-Contrast → stoplight, Colorful → full pool).
5. **Move directional outlines** — PASS ("looks good for now"); Home + active distance-step unchanged (accent); homed/unhomed status color unchanged (deferred).
6. **Pure-neutral + caution intact** — PASS ("looks decent") in dark + light; caution surfaces (Console WARNING, Stop confirm guard, amber Warn buttons) still show the caution color.

## Decisions Made

- **Heater identity = `pool[0]` (= `directional.temperature`), not amber `.heat`.** Heaters now read as heaters by color + label, not by the old amber hue. This is the deliberate consequence of the heat-semantic split: amber `.heat` is freed up to mean *caution* exclusively.
- **`MAX_TRACES = 3` left unchanged** — no current printer shows >3 graph traces (RESEARCH Pitfall 5); consumers wrap `pool[i % size]` so the cap is a safe internal boundary, not a crash vector (mitigates T-15-07-01).
- **Move status-color + lock-shape deferred** — only the directional-plane outlines (XY/Z) change here; homed/unhomed status color and the force-move lock shape belong to the shape-status follow-on (out-of-scope guardrail, mitigates T-15-07-03).

## Deviations from Plan

None — plan executed exactly as written. The two code tasks landed at the planned sites with the planned guardrails; the UAT gate passed on the first on-device pass.

## Issues Encountered

None. The heat-semantic split was the principal risk (over-migrating would have recolored Console WARNING / ConfirmGuard / Warn-intent buttons, T-15-07-02); the re-grep confirms only the named readout sites moved and the caution set is intact.

## Open / Deferred

- **User-guided whole-app conformance pass (DEFERRED — do NOT implement here).** During UAT the owner noted a future item: once the theme framework is fully done, walk every screen with the user for a conformance sweep. This is the **already-planned conformance sweep** that lives in the **theming follow-on phase + Ship** — not new scope. Tracked, not actioned.
- **Move homed/unhomed status color + force-move lock-shape** — deferred to the shape-status follow-on (intentional out-of-scope guardrail this phase).

## Next Phase Readiness

- The pool's real render consumers are now wired with stable same-sensor-same-color identity; the violet shim is gone grep-clean; the heat/caution split is clean (66→61). The theme engine + Settings + pool-wired surfaces are in place.
- Remaining theming work (shape-coded status, full conformance sweep, `docs/ui_design/THEMING.md` reconciliation) is intentionally split to the theming follow-on phase to be inserted via `/gsd-phase`.
- Phase-completion / verifier is owned by the orchestrator (not run here).

## Self-Check: PASSED

- `15-07-SUMMARY.md` exists.
- Task commits `9e0499a` (Task 1) and `bcc7102` (Task 2) present in git log.

---
*Phase: 15-theme-system-settings-redesign*
*Completed: 2026-06-05*
