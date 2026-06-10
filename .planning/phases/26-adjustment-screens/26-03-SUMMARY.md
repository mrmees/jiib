---
phase: 26-adjustment-screens
plan: 03
subsystem: data
tags: [temperature, datastore, persistence, tracestyle, compose, holder, tdd]

# Dependency graph
requires:
  - phase: 26-01
    provides: shared adjuster components (IncrementPicker, AdjusterPanel)
  - phase: 26-02
    provides: flat-list screen pattern established for FineTuneScreen
provides:
  - TraceStylePrefs: DataStore-backed persistence of per-sensor trace color (ARGB Int) + visibility (Boolean)
  - TemperatureHolder.traceColors: StateFlow<Map<String, Color>> seeded from TraceStylePrefs at construction
  - TemperatureHolder.traceVisibility: StateFlow<Map<String, Boolean>> seeded from TraceStylePrefs
  - TemperatureHolder.setTraceColor / setTraceVisibility: in-memory mutators (callers route persistence via AppContainer.writeScope)
  - SensorReadout.isAdjustable: Boolean = true field (D-11 typed row model)
  - AppContainer.traceStylePrefs + setTraceColor(sensorName, argb) + setTraceVisibility(sensorName, visible)
  - DinghyApp: 7th independent DataStore file 'tracestyle.preferences_pb'
affects:
  - 26-04 (TemperatureScreen rebuild — consumes traceColors/traceVisibility StateFlows + calls container.setTraceColor/setTraceVisibility)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "TraceStylePrefs: flat-key DataStore encoding — trace_color_<name> (Int ARGB) + trace_visible_<name> (Boolean)"
    - "Holder seeding pattern: scope.launch { prefs.traceColors.collect { _traceColors.value = it.mapValues { Color(argb) } } }"
    - "Optional prefs injection: TemperatureHolder(traceStylePrefs: TraceStylePrefs? = null) — null keeps tests clean"
    - "AppContainer writeScope discipline: setTraceColor/setTraceVisibility launch on writeScope, never composition scope"
    - "Absent-means-visible: null in traceVisibility map → visible (callers apply ?: true)"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/settings/TraceStylePrefs.kt
    - app/src/test/java/works/mees/dinghy/ui/temperature/TemperatureHolderTraceStyleTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureHolder.kt
    - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
    - app/src/main/java/works/mees/dinghy/DinghyApp.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt
    - app/src/test/java/works/mees/dinghy/theme/ThemeOverrideTest.kt

key-decisions:
  - "D-14 persistence approach: TraceStylePrefs collects into holder StateFlows (live sync), not a one-shot read — changes from other entry points also reflect automatically"
  - "Optional TraceStylePrefs injection (default null): keeps all 5 existing TemperatureHolderTest call-sites compiling unchanged; production always passes the real prefs"
  - "Holder DOES NOT call writeScope itself — setTraceColor/setTraceVisibility update in-memory state; persistence is the caller's (AppContainer's) responsibility. Clear separation of concerns."
  - "ARGB Int storage in DataStore (not hex String) — avoids String→Int parsing on hot path (T-26-03-01 acceptance)"
  - "traceStyleDataStore added as 7th param to AppContainer constructor before discovery — mirrors babystepDataStore position pattern"

requirements-completed: []

# Metrics
duration: ~7min
completed: 2026-06-10
---

# Phase 26 Plan 03: Temperature Data/Persistence Layer Summary

**TraceStylePrefs DataStore store + TemperatureHolder trace color/visibility StateFlows + AppContainer writeScope-backed persistence (D-14) + isAdjustable typed row field (D-11)**

## Performance

- **Duration:** ~7 min
- **Started:** 2026-06-10T22:09:46Z
- **Completed:** 2026-06-10T22:17:12Z
- **Tasks:** 3/3
- **Files modified:** 8 (2 created, 6 modified)

## Accomplishments

- `TraceStylePrefs.kt`: flat-key DataStore store with `traceColors: Flow<Map<String, Int>>` (ARGB) + `traceVisibility: Flow<Map<String, Boolean>>`; `setTraceColor`/`setTraceVisibility` as `suspend` single-`edit` writers; fail-safe `IOException` recovery; mirrors `BabystepPrefs` shape exactly
- `TemperatureHolder.kt`: `traceColors`/`traceVisibility` StateFlows + `setTraceColor`/`setTraceVisibility` mutators; optional `traceStylePrefs: TraceStylePrefs? = null` ctor param seeded via two `scope.launch` collectors in `init`; `SensorReadout.isAdjustable: Boolean = true` (D-11); `readout()` sets it explicitly
- `AppContainer.kt`: `traceStyleDataStore` ctor param + `traceStylePrefs: TraceStylePrefs` field + `setTraceColor(sensorName, argb)` + `setTraceVisibility(sensorName, visible)` writeScope-backed methods
- `DinghyApp.kt`: 7th independent DataStore file `tracestyle.preferences_pb` + passed to AppContainer
- `AppShell.kt`: `TemperatureHolder` construction updated to pass `container.traceStylePrefs`
- `TemperatureHolderTraceStyleTest.kt`: 7 tests (set/overwrite/multi-sensor/absent-means-visible/isAdjustable) — all GREEN with Task 1 stubs; still GREEN after Task 2 wiring
- Existing `TemperatureHolderTest.kt`: 5 tests all GREEN (optional param keeps them unchanged)
- Full `assembleDebug` + `testDebugUnitTest` GREEN

## Task Commits

1. **Task 1: TraceStylePrefs + holder stubs + RED→GREEN tests** - `e70302f` (test)
2. **Task 2: Wire holder to TraceStylePrefs + isAdjustable** - `23adc10` (feat)
3. **Task 3: AppContainer + DinghyApp + AppShell wiring** - `c257aa6` (feat)

## Files Created/Modified

- `app/src/main/java/works/mees/dinghy/ui/settings/TraceStylePrefs.kt` — DataStore persistence store (created)
- `app/src/test/java/works/mees/dinghy/ui/temperature/TemperatureHolderTraceStyleTest.kt` — 7 state-transition tests (created)
- `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureHolder.kt` — traceColors/traceVisibility StateFlows + isAdjustable
- `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` — traceStylePrefs + setTraceColor/setTraceVisibility
- `app/src/main/java/works/mees/dinghy/DinghyApp.kt` — 7th DataStore file
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` — TemperatureHolder gets traceStylePrefs
- `app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt` — 7th FakeDataStore arg (Rule-3 fix)
- `app/src/test/java/works/mees/dinghy/theme/ThemeOverrideTest.kt` — 7th MemDataStore arg (Rule-3 fix)

## Decisions Made

- Optional `TraceStylePrefs? = null` injection: all 5 existing `TemperatureHolderTest` call-sites remain unchanged; production always passes the real prefs via `AppContainer.traceStylePrefs`
- Holder does NOT call `writeScope` itself — `setTraceColor`/`setTraceVisibility` perform in-memory optimistic updates only; persistence is the caller's (AppContainer's) responsibility. This matches the exact pattern of `setBabystepEnabled` + `babystepPrefs.setEnabled`.
- `prefs.traceColors.collect { ... }` inside `init` (not a one-shot read): changes from external write paths also automatically reflect in the holder StateFlows — fully live-synced
- ARGB Int stored in DataStore (not hex String): avoids parsing on the hot path; aligns with T-26-03-01 acceptance (color from fixed pool, not arbitrary user text)
- No `GraphView`/`GraphViewHost` changes: the graph color override wiring (per-trace `traceColorOverrides`) is plan 26-04's work, as specified in the plan's must_haves

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] AppContainerTest.kt + ThemeOverrideTest.kt construction call-sites broken**
- **Found during:** Task 3 (`assembleDebug :app:testDebugUnitTest`)
- **Issue:** Both test files construct `AppContainer` with 6 DataStores + discovery; adding the 7th `traceStyleDataStore` parameter broke both: `Argument type mismatch: actual type is 'MoonrakerDiscovery', but 'DataStore<Preferences>' was expected`
- **Fix:** Added a 7th `FakeDataStore()` / `MemDataStore()` argument at the traceStyleDataStore position in both test helpers
- **Files modified:** `AppContainerTest.kt`, `ThemeOverrideTest.kt`
- **Commit:** `c257aa6` (Task 3 commit)

## Known Stubs

None — `traceColors` and `traceVisibility` are fully wired to `TraceStylePrefs` seeding. The graph-level color override (D-14's "graph DRAWS the chosen color") is an intentional scope boundary: plan 26-04 adds the equality-guarded `setTraceColorOverrides` to `GraphView`/`GraphViewHost` and builds the visible-trace model.

## Threat Flags

None — no new network endpoints, auth paths, or file access patterns introduced. DataStore key-map encoding uses flat `trace_color_<name>` / `trace_visible_<name>` keys (stable, non-injection surface). All write paths route through the process-lifetime `writeScope` (T-26-03-02 mitigated).

## Self-Check: PASSED

- `app/src/main/java/works/mees/dinghy/ui/settings/TraceStylePrefs.kt` — FOUND
- `app/src/test/java/works/mees/dinghy/ui/temperature/TemperatureHolderTraceStyleTest.kt` — FOUND
- `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureHolder.kt` — contains `traceColors` (FOUND)
- `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` — contains `traceStylePrefs` + `setTraceColor` (FOUND)
- `app/src/main/java/works/mees/dinghy/DinghyApp.kt` — contains `tracestyle.preferences_pb` (FOUND)
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` — TemperatureHolder with `traceStylePrefs` (FOUND)
- Commits e70302f / 23adc10 / c257aa6 — FOUND in git log

## Next Phase Readiness

- **26-04 (TemperatureScreen rebuild):** All data/persistence infrastructure is in place. `container.traceStylePrefs` is accessible; `container.setTraceColor(name, argb)` / `container.setTraceVisibility(name, visible)` are the write path. `holder.traceColors` / `holder.traceVisibility` are the read path. Plan 26-04 adds the GraphView `setTraceColorOverrides` (equality-guarded) + builds the 8-swatch color picker + visible-trace model.

---
*Phase: 26-adjustment-screens*
*Completed: 2026-06-10*
