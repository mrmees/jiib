---
phase: 22-performance-architecture-refactor
plan: "02"
subsystem: state
tags: [immutable, compose-stability, recomposition, performance, p0]
dependency_graph:
  requires: ["22-01"]
  provides: ["22-03", "22-04", "22-05", "22-06", "22-07"]
  affects: [PrinterState, PrinterStateReducer, MoonrakerSession, OutputsHolder, ScrewsTiltHolder, BedMeshHolder, ExtrudeHolder, PrintStatusHolder, TemperatureHolder, MoveHolder]
tech_stack:
  added: []
  patterns:
    - "@Immutable annotation on data classes in the PrinterState tree"
    - "ImmutableMap/ImmutableList declared on fields; .toImmutableMap()/.toImmutableList() at reducer assignment boundaries"
    - "toImmutable2d() private extension for List<List<Double>> conversion"
    - "persistentMapOf()/persistentListOf() for empty/default field values"
key_files:
  created:
    - app/src/test/java/works/mees/dinghy/state/PrinterStateImmutableConversionTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/state/PrinterState.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt
    - app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt
    - app/src/test/java/works/mees/dinghy/ui/extrude/ExtrudeHolderTest.kt
    - app/src/test/java/works/mees/dinghy/ui/printstatus/PrintStatusHolderTest.kt
    - app/src/test/java/works/mees/dinghy/ui/temperature/TemperatureHolderTest.kt
    - app/src/test/java/works/mees/dinghy/ui/move/MoveHolderTest.kt
    - app/src/test/java/works/mees/dinghy/calibration/BedMeshHolderTest.kt
    - app/src/test/java/works/mees/dinghy/calibration/ScrewsTiltHolderTest.kt
    - app/src/test/java/works/mees/dinghy/outputs/OutputsHolderTest.kt
decisions:
  - "D-02 field types use ImmutableMap/ImmutableList (read-only interfaces), NOT PersistentMap/PersistentList — the reducer never performs in-place mutations on PrinterState fields; it always uses .copy() with recomputed maps, so the read-only interface is the correct declared type"
  - "Capabilities.kt List<String> fields (fans/macros/powerDevices/heaters) are a deferred non-blocker — they only emit on reconnect (not the 4 Hz hot path) and are not read in any hot composable scope; no change in this plan"
  - "toImmutable2d() private extension added to PrinterStateReducer to avoid repeated inline conversion for the three 2D-list sites (probedMatrix, meshMatrix, colorData)"
metrics:
  duration: "~45 minutes"
  completed: "2026-06-09"
  tasks_completed: 3
  files_modified: 10
  files_created: 1
---

# Phase 22 Plan 02: @Immutable PrinterState Tree + ImmutableMap/ImmutableList Migration Summary

D-02 P0 root-cause fix: `PrinterState` and its entire nested type tree annotated `@Immutable`; every `Map`/`List` field migrated to `ImmutableMap`/`ImmutableList`; `PrinterStateReducer` (the dominant construction site) updated to emit immutable types at every assignment boundary.

## What Was Built

### Task 1: @Immutable annotation + field type migration (PrinterState.kt)

Ten types annotated `@Immutable`:
- `PrinterState`, `OutputLiveValue`, `FirmwareRetractionObject`, `ScrewsTiltObject`, `ScrewResult`, `BedMeshObject`, `ManualProbeObject`, `ScrewConfig`, `Screw`, `HeaterState`

Field type changes (all migrated to read-only immutable interfaces):
- `PrinterState.heaters: ImmutableMap<String, HeaterState> = persistentMapOf()`
- `PrinterState.temperatureSensors: ImmutableMap<String, Double> = persistentMapOf()`
- `PrinterState.toolheadPosition: ImmutableList<Double>? = null`
- `PrinterState.gcodePosition: ImmutableList<Double>? = null`
- `PrinterState.outputs: ImmutableMap<String, OutputLiveValue> = persistentMapOf()`
- `OutputLiveValue.colorData: ImmutableList<ImmutableList<Double>>? = null`
- `ScrewsTiltObject.results: ImmutableMap<String, ScrewResult> = persistentMapOf()`
- `BedMeshObject.meshMin/meshMax: ImmutableList<Double>? = null`
- `BedMeshObject.probedMatrix/meshMatrix: ImmutableList<ImmutableList<Double>>? = null`
- `BedMeshObject.profileNames: ImmutableList<String> = persistentListOf()`
- `ScrewConfig.screws: ImmutableList<Screw> = persistentListOf()`

No raw `: List<` or `: Map<` field declarations remain on `PrinterState.kt`.

### Task 2: PrinterStateReducer + call-site conversion

`PrinterStateReducer.kt` updated at every collection-assignment boundary:
- `th.doubleListOrNull("position")?.let { s = s.copy(toolheadPosition = it.toImmutableList()) }`
- `gm.doubleListOrNull("gcode_position")?.let { s = s.copy(gcodePosition = it.toImmutableList()) }`
- `bm.doubleListOrNull("mesh_min")?.toImmutableList()` / `"mesh_max"`
- `bm.double2dListOrNull("probed_matrix")?.toImmutable2d()` / `"mesh_matrix"`
- `bm.objectOrNull("profiles")?.keys?.toImmutableList()` (profile names)
- `results.toImmutableMap()` in `screws_tilt_adjust`
- `(s.heaters + heaterUpdates).toImmutableMap()`
- `(s.temperatureSensors + sensorUpdates).toImmutableMap()`
- `obj.double2dListOrNull("color_data")?.toImmutable2d()` in outputs
- `(s.outputs + outputUpdates).toImmutableMap()`

Private `toImmutable2d()` extension added (avoids repetition at the three 2D-list sites).

Internal accumulators (`mutableMapOf`, `LinkedHashMap`) remain mutable — conversion happens only at the `s.copy(...)` boundary.

`MoonrakerSession.kt` (sole non-reducer call site constructing a PrinterState-tree type):
- Line 642: `ScrewConfig(screws = screws.toImmutableList(), ...)`

`PrinterStateStore.kt` needed no changes — it takes the already-reduced `PrinterState` from the reducer.

### Task 3: Targeted reducer tests + Capabilities audit

`PrinterStateImmutableConversionTest.kt` — 14 tests covering the four conversion-sensitive paths:

**Bed mesh matrices** (5 tests):
- Type assertion (`is ImmutableList<*>`) + value correctness for `probedMatrix`, `meshMatrix`, `meshMin`, `meshMax`
- Absent-key retain semantics with type preservation
- Present-but-empty key honored as real clear (BED_MESH_CLEAR distinction)
- Null array key yields null field

**Screws-tilt results map** (2 tests):
- `ImmutableMap` type + correct key/value pairs
- Empty results case yields empty `ImmutableMap`

**Outputs colorData** (2 tests):
- `ImmutableList<ImmutableList<Double>>` type assertion on nested list
- Absent-key retain + type preservation

**Position lists + heater merge** (5 tests):
- `toolheadPosition` type (`ImmutableList`) + values
- `gcodePosition` type + values
- Partial diff omitting position retains prior value with type
- Null position key retains null default
- Heater merge produces `ImmutableMap` with both heaters retained and updated correctly

**Capabilities audit:** `Capabilities.kt` has `fans/macros/powerDevices/heaters: List<String>`. These are populated once at connection time (not on the 4 Hz `notify_status_update` path) and are not read inside any hot composable scope. Decision: deferred non-blocker. No change in this plan.

## Deviations from Plan

### Auto-fixed Issues (Rule 3)

**[Rule 3 - Blocking] Test compilation cascade from field type changes (7 test files)**

After Task 1 changed `PrinterState` field types from raw `Map`/`List` to `ImmutableMap`/`ImmutableList`, 7 test files that construct `PrinterState` directly failed to compile with `Argument type mismatch: actual type is 'Map<...>', but 'ImmutableMap<...>' was expected`. Fixed inline as a Rule 3 blocking issue:

- `ExtrudeHolderTest.kt` — `heaters()` return type → `ImmutableMap`; `emptyMap()` → `persistentMapOf()`
- `PrintStatusHolderTest.kt` — `heaters()` return type → `ImmutableMap`
- `TemperatureHolderTest.kt` — `heaters()` return type → `ImmutableMap`
- `MoveHolderTest.kt` — `listOf(...)` gcodePosition args wrapped with `.toImmutableList()`
- `BedMeshHolderTest.kt` — all BedMeshObject `listOf(...)` fields wrapped with `.toImmutableList()` / nested `.toImmutableList()`
- `ScrewsTiltHolderTest.kt` — `results` wrapped with `.toImmutableMap()`; `screws` wrapped with `.toImmutableList()`
- `OutputsHolderTest.kt` — `mapOf(...)` → `.toImmutableMap()`; `emptyMap()` → `persistentMapOf()`; nested `colorData` wrapped with `.toImmutableList()`

**[Rule 3 - Blocking] MoonrakerSession.kt call-site (line 642)**

`ScrewConfig(screws = screws)` where `screws` was `List<Screw>` from `buildList { ... }`, not `ImmutableList<Screw>`. Fixed by adding `import kotlinx.collections.immutable.toImmutableList` and calling `.toImmutableList()`.

Note: `PrinterStateStore.kt` listed in `files_modified` in the plan required no changes — the store constructs no collection fields directly.

## Known Stubs

None. All collection fields are fully wired.

## Threat Flags

None. This plan is purely internal state refactoring — no new network endpoints, auth paths, or schema changes at trust boundaries.

## Self-Check: PASSED

Files created/modified:

- [x] `app/src/main/java/works/mees/dinghy/state/PrinterState.kt` — @Immutable + ImmutableMap/ImmutableList fields
- [x] `app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt` — .toImmutableMap()/.toImmutableList() at assignment boundaries
- [x] `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt` — screws.toImmutableList()
- [x] `app/src/test/java/works/mees/dinghy/state/PrinterStateImmutableConversionTest.kt` — 14 targeted reducer tests
- [x] 7 test files updated for cascading type changes

Commits verified:
- [x] `c26facc` — refactor(22-02): annotate PrinterState tree @Immutable, migrate Map/List fields
- [x] `c000e0a` — refactor(22-02): update holder/calibration tests to use immutable collection factories
- [x] `6919e4a` — test(22-02): add targeted immutable-conversion tests for four reducer paths + Capabilities audit

Build gates: `testReleaseUnitTest` GREEN (35 tasks, all pass) | `assembleRelease` GREEN (56 tasks)
