---
phase: 26-adjustment-screens
plan: 04
subsystem: ui
tags: [temperature, graph, tracecolor, visibility, adjuster, morph, tdd, datastore, compose]

# Dependency graph
requires:
  - phase: 26-03
    provides: TraceStylePrefs + TemperatureHolder traceColors/traceVisibility StateFlows + AppContainer setTraceColor/setTraceVisibility writeScope-backed mutators
  - phase: 26-01
    provides: AdjusterPanel + IncrementPicker
provides:
  - GraphView.setTraceColorOverrides: equality-guarded per-trace ARGB Int override (D-14)
  - GraphViewHost multi-trace overload: traceColors param → setTraceColorOverrides wire
  - TemperatureScreen rebuilt: D-10 graph↔adjuster morph + D-11 typed rows + D-12 Field-takeover presets + D-13 Cooldown/Off + D-14 chosen-color pipeline end-to-end
  - TemperaturePreviews: 9-preview matrix (6-combo + fs=L + landscape + printing states)
affects:
  - Phase 25 Ship — Temperature is a printing-list destination; morph + color persistence fully wired

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Equality-guarded setTraceColorOverrides: lastOverrides compare → early return → reapplyOverrides() + invalidate() — mirrors yRange equality-guard (Phase-22 D-12)"
    - "reapplyOverrides() private helper re-layers lastOverrides on top of seriesColor(i) defaults; called from both setTraceColorOverrides AND applyTokens (override-wins after theme swap)"
    - "VisibleTraces data class: keeps(List<Int>) + series/setpoints/names/colors all filtered through the SAME kept-indices list (finding 4 alignment rule)"
    - "Colorful-8 swatch pool: Palette.generate(seedHex, dark, maxItems=8, simple=false, highContrast=false).pool.take(8) — dedicated pool, never t.pool.take(8)"
    - "D-10 morph via plain if/else (not AnimatedContent) — GraphViewHost factory never destroyed on sensor selection"
    - "writeScope discipline: holder.setTraceColor + container.setTraceColor(argb) called together; never rememberCoroutineScope"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/preview/TemperaturePreviews.kt
  modified:
    - app/src/main/java/works/mees/dinghy/render/GraphView.kt
    - app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt
    - app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt

key-decisions:
  - "D-14 override-wins pattern: reapplyOverrides() called from applyTokens (not just from setTraceColorOverrides) so theme swap never resets user-chosen trace colors to seriesColor(i) defaults"
  - "VisibleTraces data class for aligned filtering: filtering only series would misalign GraphView's setpoint-draw (raw-index access at GraphView.kt:~350); all four lists filter through identical kept indices"
  - "Plain if/else for D-10 morph: AnimatedContent was NOT used — it would remove+re-add GraphViewHost from the Compose tree, destroying the AndroidView instance and resetting trace history (factory-runs-once invariant)"
  - "onEmergencyStop callback added to TemperatureContent: fixes bug where ConfirmGuard.onConfirm only closed the guard without dispatching emergencyStop; live overload passes dispatcher dispatch, stateless defaults to {}"
  - "Reuse printstatus_estop_guard_* strings for ConfirmGuard: identical content to spool/status screens, avoids string duplication"

requirements-completed: []

# Metrics
duration: ~30min (cross-session including context reload)
completed: 2026-06-10
---

# Phase 26 Plan 04: Temperature Screen Rebuild Summary

**Rebuilt TemperatureScreen with graph↔adjuster morph Focus (D-10), typed sensor rows (D-11), Field-takeover presets (D-12), Cooldown + per-heater Off (D-13), and end-to-end chosen-color pipeline from DataStore through GraphView trace paint (D-14) via equality-guarded setTraceColorOverrides.**

## Performance

- **Duration:** ~30 min (cross-session with context reload)
- **Started:** 2026-06-10T22:09:46Z (cross-session)
- **Completed:** 2026-06-10T23:02:35Z
- **Tasks:** 2/2
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments

- `GraphView.kt`: `setTraceColorOverrides(List<Int?>)` with equality-guard (lastOverrides compare → early return → reapplyOverrides() + invalidate()); `reapplyOverrides()` private helper re-layers overrides on top of seriesColor(i) defaults; `applyTokens` now calls `reapplyOverrides()` AFTER updating token defaults so theme swaps do NOT reset user-chosen colors (override-wins invariant, Phase-22 D-12 discipline preserved)
- `GraphViewHost.kt`: `traceColors: List<Int?> = emptyList()` param added to multi-trace overload; wired into `update` lambda as `view.setTraceColorOverrides(traceColors)`; single-snapshot sparkline overload UNCHANGED
- `TemperatureScreen.kt`: Full rebuild with two-overload stateless seam (live: AppContainer+TemperatureHolder+dispatcher; stateless: fixture data + no-op callbacks). Internal `VisibleTraces` data class filters series+setpoints+names+colors through identical kept-indices; plain if/else Focus morph (no AnimatedContent); Colorful-8 swatch pool via dedicated `Palette.generate(maxItems=8, simple=false, highContrast=false)`; writeScope discipline via `container.setTraceColor/setTraceVisibility`; heater nudge via `clampHeaterTarget`; Field-takeover `TempFieldMode` sealed class; gutter=null with FootButtonBar inside field; `PresetSelector` retained for PrintStatusScreen backward compat
- `TemperaturePreviews.kt`: 9 `@Preview` annotations (6-combo + fs=L + landscape 800×480dp + AdjusterMorphState + PrintingError); 3 demo composables with fake 3-sensor legend + heat-up series curves + chosen-color/hidden-trace fixtures

## Task Commits

1. **Task 1: GraphView/GraphViewHost color overrides + TemperatureScreen rebuild** — `51f3553` (feat)
2. **Task 2: Temperature preview matrix** — `c4e607f` (feat)

## Files Created/Modified

- `app/src/main/java/works/mees/dinghy/preview/TemperaturePreviews.kt` — 9-preview matrix (created)
- `app/src/main/java/works/mees/dinghy/render/GraphView.kt` — setTraceColorOverrides + reapplyOverrides + applyTokens override-wins
- `app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt` — traceColors param in multi-trace overload
- `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt` — full rebuild (D-10..D-14)

## Decisions Made

- Override-wins after theme swap: `reapplyOverrides()` called from `applyTokens` (not just from `setTraceColorOverrides`) so a dark→light flip never resets user-chosen colors to token defaults
- `VisibleTraces` aligned filter: GraphView's setpoint line draw uses raw trace index; filtering only `series` would silently misalign `setpoints[t]` and produce wrong setpoint-dashes. All four lists (series, setpoints, names, colors) filter through identical kept-indices
- Plain if/else for D-10 morph: `AnimatedContent` removes+re-adds the composable it wraps — using it around `GraphViewHost` would destroy the `AndroidView` instance and reset trace history each time a sensor is selected/deselected. Plain `if/else` keeps `GraphViewHost` in the Compose tree at all times
- `onEmergencyStop` callback added to `TemperatureContent`: the original `ConfirmGuard.onConfirm` only closed the guard without dispatching emergency stop — a silent bug. Fixed by adding the callback with proper dispatch in the live overload

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] SeverityToast param name wrong**
- **Found during:** Task 1 build (first `assembleDebug` attempt)
- **Issue:** `SeverityToast` uses `text` not `message`; generated code used `message = msg`
- **Fix:** Renamed to `text = msg`
- **Files modified:** `TemperatureScreen.kt`
- **Commit:** `51f3553`

**2. [Rule 1 - Bug] ConfirmGuard missing required params + silent no-dispatch bug**
- **Found during:** Task 1 build (first `assembleDebug` attempt)
- **Issue:** `ConfirmGuard` requires `title`, `message`, `confirmLabel`; also `onConfirm` only closed the guard without dispatching `emergencyStop`
- **Fix:** Added all three required string params (reusing `printstatus_estop_guard_*` strings); added `onEmergencyStop` callback to `TemperatureContent` so the live overload dispatches `CommandRegistry.emergencyStop` on confirm; stateless overload defaults to `{}`
- **Files modified:** `TemperatureScreen.kt`
- **Commit:** `51f3553`

**3. [Rule 1 - Bug] Missing R + stringResource imports**
- **Found during:** Task 1 build (companion to fix #2)
- **Issue:** `R.string.*` and `stringResource()` referenced but not imported
- **Fix:** Added `import androidx.compose.ui.res.stringResource` and `import works.mees.dinghy.R`
- **Files modified:** `TemperatureScreen.kt`
- **Commit:** `51f3553`

## Known Stubs

None — all D-14 pipelines (color choice → DataStore → StateFlow → GraphView override → trace paint) are fully wired. The 8-swatch row in TemperaturePreviews.kt uses a hardcoded fake list (8 ARGB constants) because `@Preview` inspection mode cannot call `Palette.generate`; on-device the live overload calls the real generator.

## Threat Flags

None — color/visibility persistence is visual-only (no trust boundary crossed). Heater target nudge routes through `clampHeaterTarget` (P17 clamp authority). No new network endpoints or auth paths introduced.

## Self-Check: PASSED

- `app/src/main/java/works/mees/dinghy/render/GraphView.kt` — contains `setTraceColorOverrides` + `lastOverrides` + `reapplyOverrides` (FOUND)
- `app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt` — contains `traceColors: List<Int?>` param (FOUND)
- `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt` — contains `VisibleTraces` + `Palette.generate` + `if (selectedSensor == null)` + `container.setTraceColor` + `clampHeaterTarget` + `gutter = null` (FOUND)
- `app/src/main/java/works/mees/dinghy/preview/TemperaturePreviews.kt` — 9 `@Preview` annotations (FOUND)
- Commits `51f3553` / `c4e607f` — FOUND in git log
- `assembleDebug` + `testDebugUnitTest` — BUILD SUCCESSFUL (2× verified)
