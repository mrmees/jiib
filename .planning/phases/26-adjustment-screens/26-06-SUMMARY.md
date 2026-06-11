---
phase: 26-adjustment-screens
plan: 06
subsystem: ui/extrude
tags: [compose, extrude, footbuttonbar, numeric-ime, filament-presets, conformance, preview]

# Dependency graph
requires:
  - phase: 26-01
    provides: IncrementPicker, AdjusterPanel, FootButtonBar — shared design kit
  - phase: 26-05
    provides: AppShell NavHost route pattern + activeSpoolDetail collection at line 310
provides:
  - ExtrudeScreen conformance rebuild: FootButtonBar + gutter=null + numeric IME distance/speed + filament-preset takeover
  - ExtrudeFieldMode: sealed class (Main | FilamentPresets) for in-screen Field navigation
  - ExtrudePreviews: 15 @Preview annotation usages across 6 themes + cold-gate + multi-tool + spool + fs=L + landscape
affects:
  - AppShell.kt (activeSpoolDetail threaded into Extrude route)
  - strings.xml (extrude_* namespace: 15 new string keys)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "NumpadPage → BasicTextField(KeyboardType.Decimal) + coerceIn clamping (D-16)"
    - "ExtrudeFieldMode sealed class: Main / FilamentPresets in-screen navigation"
    - "FilamentPresets takeover: MATERIAL_PRESETS rows + conditional loaded-spool row from activeSpoolDetail"
    - "FootButtonBar in field lambda; gutter = null (D-15 conformance)"
    - "Stateless seam ExtrudeScreen(vm, activeSpoolDetail, onBack) for preview coverage"
    - "rememberSaveable for IME text to survive rotation; LaunchedEffect sync on state change"

key-files:
  modified:
    - app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/res/values/strings.xml
  created:
    - app/src/main/java/works/mees/dinghy/preview/ExtrudePreviews.kt

key-decisions:
  - "D-15 exemption honored: specialized command-centric layout (two-column Focus) preserved; conformance only"
  - "IME text synced to state via LaunchedEffect(distance/speed) so preset-selector taps update the readout display"
  - "rememberSaveable used for distanceText/speedText to survive config changes without re-triggering parse"
  - "Load = Intent.Accent/ExpandCircleUp; Unload = Intent.Warn/ExpandCircleDown (already in DinghyIcons registry)"
  - "FilamentPresets takeover: loaded-spool row shown only when activeSpoolDetail?.filament?.settingsExtruderTemp != null"
  - "onSetExtruderTemp dispatches SetHeaterArgs(activeHeater, clampHeaterTarget(temp)) — extruder only, never bed (D-17)"
  - "ExtrudePreviews: 11 @Nexus7Previews + 1 @Preview(landscape) = 15 total annotation usages covering all key states"

requirements-completed: []

# Metrics
duration: 45min
completed: 2026-06-10
---

# Phase 26 Plan 06: Extrude Conformance + Numeric IME + Filament Presets Summary

**ExtrudeScreen brought to full conformance (D-15 specialized-layout exemption): FootButtonBar replaces gutter, numeric IME replaces NumpadPage for distance/speed, filament-preset Field-takeover replaces temp NumpadPage (D-16/D-17)**

## Performance

- **Duration:** 45 min
- **Started:** 2026-06-10
- **Completed:** 2026-06-10
- **Tasks:** 2
- **Files modified:** 3 | **Files created:** 1

## Accomplishments

- `ExtrudeScreen` rebuilt for conformance WITHOUT changing its specialized command-centric layout (two-column Focus with BigCommand left / NumericSettingReadout right — D-15 exemption preserved)
- `FootButtonBar` placed as last element in `field` lambda; `gutter = null` (Back/Load/Unload)
- Load = `Intent.Accent` / `DinghyIcons.ExpandCircleUp`; Unload = `Intent.Warn` / `DinghyIcons.ExpandCircleDown` (existing registry icons); Back = `DinghyIcons.Back`
- Distance/Speed NumpadPage → `BasicTextField(KeyboardType.Decimal, ImeAction.Done)` with `coerceIn` clamping before state update — raw IME text never reaches gcode (T-26-06-01)
- `ExtrudeFieldMode` sealed class (`Main | FilamentPresets`) for Field-mode in-screen navigation
- Nozzle-temp button opens `FilamentPresets` mode: `MATERIAL_PRESETS` rows + conditional loaded-spool row (reads `activeSpoolDetail?.filament?.settingsExtruderTemp`) dispatching `SetHeaterArgs(activeHeater, clampHeaterTarget(nozzle))` — extruder ONLY (D-17)
- Stateless preview seam `ExtrudeScreen(vm, activeSpoolDetail, onBack)` added for `ExtrudePreviews.kt`
- `AppShell.kt`: `activeSpoolDetail = activeSpoolDetail` threaded into `composable<NavDest.Extrude>` (already collected at line 310)
- 15 string keys added under `extrude_*` namespace in `strings.xml`
- `ExtrudePreviews.kt`: 11 `@Nexus7Previews` + 1 landscape `@Preview` = 15 total annotation usages

## Task Commits

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Extrude conformance — FootButtonBar, numeric IME distance/speed | 755beb5 | ExtrudeScreen.kt, AppShell.kt, strings.xml |
| 2 | Filament-preset Field-takeover + previews | 429850d | ExtrudePreviews.kt |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] AppShell.kt Extrude call site missing activeSpoolDetail**
- **Found during:** Task 1 first compile
- **Issue:** `ExtrudeScreen` now requires `activeSpoolDetail: SpoolmanSpool?` but AppShell called the old 3-param signature → compile error
- **Fix:** Added `activeSpoolDetail = activeSpoolDetail` to the `composable<NavDest.Extrude>` call (the value was already collected at line 310, just not threaded in)
- **Files modified:** `AppShell.kt`
- **Commit:** 755beb5

### Plan Deviation — Task 1 + 2 combined

ExtrudeFieldMode, FilamentPresets takeover, and activeSpoolDetail wiring were implemented in Task 1 rather than split between tasks — the logic is tightly coupled (the sealed class drives both the Main IME field and the FilamentPresets field in the same `when` block), so a clean Task 1 commit naturally included the sealed class scaffold and Task 2 built only ExtrudePreviews.kt. All acceptance criteria for both tasks are met.

## Known Stubs

None — all dispatch routes are wired. The "Spool" placeholder button (`infoText = "Spoolman integration coming soon"`) is an intentional placeholder for a future Spoolman tile (not a missing feature for this plan's scope).

## Threat Flags

None — no new network endpoints, auth paths, or schema changes. The numeric IME input is clamped before any state update (`coerceIn`), and preset temps route through `clampHeaterTarget`. The extruder-only constraint is enforced by dispatching `SetHeaterArgs(activeHeater, ...)` never `heater_bed` (D-17 / T-26-06-02).

## Self-Check: PASSED

- `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt` — FOUND (modified)
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` — FOUND (modified)
- `app/src/main/res/values/strings.xml` — FOUND (modified)
- `app/src/main/java/works/mees/dinghy/preview/ExtrudePreviews.kt` — FOUND (created)
- Source assertion: `gutter = null` + FootButtonBar in field — PASS
- Source assertion: `KeyboardType.Decimal` + `coerceIn` clamping — PASS
- Source assertion: `ExtrudeFieldMode` sealed class (Main + FilamentPresets) — PASS
- Source assertion: extruder-only `SetHeaterArgs(vm.activeHeater, ...)` — PASS
- Source assertion: no `NumpadPage`/`NumpadTarget` in ExtrudeScreen.kt — PASS
- Source assertion: `activeSpoolDetail` param in both overloads — PASS
- Source assertion: AppShell passes `activeSpoolDetail` into Extrude route — PASS
- Source assertion: ExtrudePreviews has ≥7 @Preview annotations (15 total) — PASS
- Commits 755beb5 / 429850d — FOUND in git log
- `assembleDebug` + `testDebugUnitTest` — BUILD SUCCESSFUL
