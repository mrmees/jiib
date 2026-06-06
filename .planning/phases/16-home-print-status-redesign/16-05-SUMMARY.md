---
phase: 16-home-print-status-redesign
plan: 05
subsystem: settings-persistence
tags: [babystep, datastore, write-scope, settings, drawer-stubs, D-06, D-02]
requires:
  - "DinghyApp DataStore creation pattern (macros/webcam/profiles, existing)"
  - "MacroPrefs prefs-class shape (existing analog)"
  - "AppContainer writeScope + intent-helper pattern (Phase 14, existing)"
  - "BabystepPrefsTest RED scaffold (16-01)"
  - "SettingsScreen four-tile IA + TokenTextField + ToggleRow (15.2)"
  - "AppDrawer DRAWER_TILES dest=null greyed pattern (existing)"
provides:
  - "babystep.preferences_pb DataStore (6th file) + BabystepPrefs (enabled/layerCount)"
  - "AppContainer.babystepEnabled / babystepLayers flows -> 16-02/16-06 Wave-3 babystep row"
  - "AppContainer.setBabystepEnabled / setBabystepLayers durable writeScope intent helpers"
  - "Settings Z-babystep section (toggle + numeric layer-count) -> the user-editable setting"
  - "Greyed Output (P18) + System Info (P19) drawer forward stubs (SC-1 / D-02)"
affects:
  - "16-02 / 16-06 (the Print-Status babystep row reads babystepEnabled + babystepLayers)"
  - "P18 Output / P19 System Info (the greyed stubs they will light up)"
tech-stack:
  added: []
  patterns:
    - "Sixth separate-file DataStore created ONCE in DinghyApp, threaded into AppContainer (Codex blocker fix)"
    - "Persistence writes route through process-lifetime writeScope intent helpers, never a composition scope"
    - "Drawer forward stubs as dest=null greyed tiles (icon-no-repeat: bolt / memory)"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/settings/BabystepPrefs.kt
  modified:
    - app/src/main/java/works/mees/dinghy/DinghyApp.kt
    - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
    - app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
    - app/src/androidTest/java/works/mees/dinghy/ui/ShellPresenceTest.kt
    - app/src/test/java/works/mees/dinghy/ui/settings/BabystepPrefsTest.kt
    - app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt
    - app/src/test/java/works/mees/dinghy/theme/ThemeOverrideTest.kt
decisions:
  - "babystep is PROCESS-SCOPED + connection-independent (like macros/webcam), NOT per-profile — survives reconnects/printer swaps"
  - "layer-count field re-seeds from the persisted value (remember key=babystepLayers); blank/invalid buffer allowed mid-typing, only a valid positive int persists (coerce >=1 in BabystepPrefs)"
  - "Output glyph = bolt, System Info glyph = memory (icon-no-repeat law); both drawer-ONLY (not the Standby launcher grid, D-02)"
metrics:
  duration: ~11 min
  completed: 2026-06-06
---

# Phase 16 Plan 05: Babystep Setting Persistence + Forward-Stub Drawer Tiles Summary

Gave the babystep app setting (D-06) a real backing DataStore created at the canonical DinghyApp
creation point and persisted through the durable writeScope (no composition-scope trap), surfaced it
as a Settings section, and added the greyed Output/System-Info forward-stub drawer tiles (D-02 / SC-1).

## What Was Built

- **`babystep.preferences_pb` (6th DataStore)** created ONCE in `DinghyApp.onCreate` mirroring the
  macros/webcam blocks verbatim, threaded into the `AppContainer(...)` ctor as `babystepDataStore`
  (the blocker Codex caught — `BabystepPrefs` had no backing store without this creation site).
- **`BabystepPrefs.kt`** (new) copying the `MacroPrefs` shape exactly: injected `DataStore`,
  `enabled: Flow<Boolean>` default `true` (`babystep_enabled`), `layerCount: Flow<Int>` default `5`
  (`babystep_layers`), both IOException fail-safe → defaults; `setEnabled` / `setLayerCount` write
  inside one `dataStore.edit`, with `setLayerCount` coercing `>= 1` (V5 guard, T-16-05-01).
- **AppContainer**: `babystepPrefs` field + `babystepEnabled` / `babystepLayers` flows the Settings UI
  and the Wave-3 Print-Status row collect; `setBabystepEnabled` / `setBabystepLayers` intent helpers
  that `writeScope.launch { … }` — the process-lifetime path, NEVER `rememberCoroutineScope()`
  ([[dinghy-compose-write-scope-cancellation]], the project's #1 recurring trap).
- **SettingsScreen**: a "Z-Babystep (first layer)" section under the Settings tile — an enable toggle
  (default ON, app-global so always editable) and a numeric `TokenTextField` layer-count using
  `KeyboardType.Number` (Settings is keyboard-permitted). Both writes go through the container intent
  helpers; the field re-seeds from the persisted value and persists only a valid positive int. New
  `LabeledRow` helper hosts the field; all UI via `fsSp` + role tokens (no bare `.sp`, no raw color).
- **AppDrawer**: two greyed `dest = null` forward stubs — `Output` (`bolt`, P18) and `System Info`
  (`memory`, P19), unique glyphs per the icon-no-repeat law, rendered greyed + inert via the existing
  `tile.dest != null` gate. Drawer-only (NOT the Standby launcher grid, D-02).
- **ShellPresenceTest**: new `shellRoute_forwardStubTilesAreInert` asserting both stubs are present and
  non-navigating; also refreshed two stale 15.2-era assertions (see Deviations).

## Verification

- **Task 1:** `:app:testDebugUnitTest --tests …BabystepPrefsTest` → **5/5 GREEN** (was 5/5 RED in
  16-01): defaults (enabled=true / layers=5), IOException fail-safe to defaults, setEnabled/setLayerCount
  write-through, setLayerCount(0) coerce ≥1.
- **Task 2:** `:app:assembleDebug` + `:app:assembleDebugAndroidTest` **BUILD SUCCESSFUL** (only
  pre-existing `createComposeRule` deprecation warnings, out of scope).
- **On-device (flox, API 30):** the new `shellRoute_forwardStubTilesAreInert` PASSED on a clean run
  (Output + System Info present, greyed, non-navigating). See Deferred Issues for the whole-class
  re-run flakiness.
- Grep gates: `DinghyApp.kt` contains the `babystep.preferences_pb` literal + threads it to the ctor;
  `AppContainer` has the `babystepDataStore` ctor param + `setBabystepEnabled`/`setBabystepLayers`;
  SettingsScreen uses `KeyboardType.Number` + `container.setBabystep*`; no `rememberCoroutineScope`
  in any touched main file; two `dest = null` "Output"/"System Info" stubs present.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] AppContainer ctor param broke two test construction sites**
- **Found during:** Task 1.
- **Issue:** Adding the required `babystepDataStore` ctor param broke the positional `AppContainer(...)`
  calls in `AppContainerTest` and `ThemeOverrideTest`.
- **Fix:** added a sixth fake/in-memory `DataStore` to each call (inserted before `discovery`, matching
  the new param order), with a clarifying comment.
- **Files modified:** `AppContainerTest.kt`, `ThemeOverrideTest.kt`.
- **Commit:** `d5ba7ec`.

**2. [Rule 1 - Stale test assertions] ShellPresenceTest referenced retired 15.2-era UI**
- **Found during:** Task 2 on-device run.
- **Issue:** Two pre-existing assertions no longer match the current UI after the 15.2-04 IA dissolve:
  `shellRoute_drawerOpens…` asserted a "Connection" header on the Settings screen (Connection was moved
  to the Printers destination); `shellRoute_greyedTilesAreInert` scrolled to a greyed "Devices" tile
  (relabeled "Printers" and made LIVE in 15.2-04).
- **Fix:** retargeted the Settings assertion to the live "Feature toggles" section header; removed the
  obsolete Devices block (greyed-tile inertness is now covered by Power + the new Output/System-Info
  stub test). These are net-more-correct against the current UI and compile in
  `:app:assembleDebugAndroidTest`.
- **Files modified:** `ShellPresenceTest.kt`.
- **Commit:** `5358bde`.

## Deferred Issues

**ShellPresenceTest whole-class on-device run is flaky (pre-existing routing-seed mismatch).** The new
forward-stub test passed in isolation on a fresh run, but a whole-class re-run had all 5 tests fail
("Status / Dinghy Display not displayed"). Root cause is pre-existing and unrelated to 16-05:
`seedRoute` saves to the Phase-14 DEAD `connectionStore`, but routing now derives `hasConfig` from
`profileStore` (`activeConfig`/`activeProfile`), so a device with a persisted active profile makes the
route non-deterministic across runs. Out of 16-05 scope (Task-2 criterion is androidTest COMPILE, met;
the babystep unit gate is GREEN; the binding on-device drawer gate is owned by 16-08). Logged to
`.planning/phases/16-home-print-status-redesign/deferred-items.md` with a suggested fix (seed
`profileStore` + clear device datastore before the run).

## Threat Model Compliance

- **T-16-05-01** (V5 input validation, layer-count): the field is `KeyboardType.Number`, the
  `onValueChange` filters to digits, and `BabystepPrefs.setLayerCount` coerces `>= 1`. The value gates
  UI visibility only — never concatenated into a gcode string. ✅
- **T-16-05-02** (DataStore write race / data loss): the read-modify-write is inside one
  `dataStore.edit` on the process-lifetime `writeScope`; the babystep file is its own single-writer
  instance (one per process, created in DinghyApp). ✅
- **T-16-SC** (package installs): no installs this phase. ✅

## Self-Check: PASSED

- Files exist: `BabystepPrefs.kt` (created); `DinghyApp.kt`, `AppContainer.kt`, `SettingsScreen.kt`,
  `AppDrawer.kt`, `ShellPresenceTest.kt`, `BabystepPrefsTest.kt`, `AppContainerTest.kt`,
  `ThemeOverrideTest.kt` (modified). ✅
- Commits exist: `d5ba7ec`, `5358bde`. ✅
