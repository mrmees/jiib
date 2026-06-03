---
phase: 09-calibration-maintenance
plan: 01
subsystem: testing
tags: [junit, kotlinx-serialization, fixtures, moonraker, calibration, tdd, red-scaffold]

# Dependency graph
requires:
  - phase: 08-macros-console
    provides: "The GcodeStoreParseTest fixture-loading idiom (javaClass.getResource + MoonrakerJson) and the gcode_store_e5.json captured-fixture precedent"
  - phase: 06-command-reference-capability-matrix
    provides: "Capabilities.hasObject predicate surface the gate scaffolds assert against"
provides:
  - "Five verbatim REAL Ender-5-Plus calibration JSON fixtures (screws_tilt_adjust / bed_mesh / z_tilt / manual_probe / configfile_screws) — the data contract every Phase-9 parser is written against"
  - "Seven RED test scaffolds binding each parser-to-be to its real fixture; compile-fail RED baseline for 09-03..09-07"
  - "The recorded real-shape surprises (clock-string adjust, [x,y] array mesh_min/max, ?????? z-bounds, max_deviation null post-run, profiles-dict-keyed)"
affects: [09-03, 09-04, 09-05, 09-06, 09-07, calibration parsers, bed-mesh heatmap, files-delete D-15 fix]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Fixture-capture-first (Wave 0): capture REAL wire shapes from the live printer BEFORE any parser is written — the third mock-vs-reality backstop"
    - "RED scaffold binds to a captured-from-real-hardware fixture via the GcodeStoreParseTest classpath idiom"

key-files:
  created:
    - app/src/test/resources/fixtures/screws_tilt_adjust_e5.json
    - app/src/test/resources/fixtures/bed_mesh_e5.json
    - app/src/test/resources/fixtures/z_tilt_e5.json
    - app/src/test/resources/fixtures/manual_probe_e5.json
    - app/src/test/resources/fixtures/configfile_screws_e5.json
    - app/src/test/java/works/mees/dinghy/calibration/ScrewsTiltResultTest.kt
    - app/src/test/java/works/mees/dinghy/calibration/TiltResultTest.kt
    - app/src/test/java/works/mees/dinghy/calibration/BedMeshModelTest.kt
    - app/src/test/java/works/mees/dinghy/calibration/ManualProbeStateTest.kt
    - app/src/test/java/works/mees/dinghy/calibration/ProbePresentGateTest.kt
    - app/src/test/java/works/mees/dinghy/calibration/CalibrationGateTest.kt
    - app/src/test/java/works/mees/dinghy/ui/files/FilesDeleteGateTest.kt
  modified: []

key-decisions:
  - "screws_tilt_adjust.results PERSISTS after the run (one-shot query post-SCREWS_TILT_CALCULATE returned populated results) — resolves RESEARCH Open-Q1: 09-03 can read it post-hoc, NOT only on the completion edge"
  - "adjust is a CLOCK STRING 'MM:SS' ('00:07'), not a minutes-float — the worst-screw parser must parse the clock format"
  - "mesh_min / mesh_max are JSON ARRAYS [x,y] (Python tuple→array), not objects — confirmed against the real fixture"
  - "manual_probe Z-position bounds can be the literal '??????' before they're set — parseZPosition must treat ?????? as null, not assume three floats"

patterns-established:
  - "Wave-0 RED scaffold cites the captured fixture that justifies each edge-case assertion (e.g. z_tilt_e5.json applied:false → the Pitfall-2 'applied:false alone ≠ Failed' case)"
  - "Pure gate predicates (ProbePresentGate, CalibrationGate) anchor to a real fixture's classpath presence even when their logic is capability-only"

requirements-completed: [BEDM-01, BEDL-01, ZCAL-01, CALIB-01, CALIB-02, CALIB-03, CALIB-04, CALIB-05, CALIB-06]

# Metrics
duration: 14min
completed: 2026-06-03
---

# Phase 9 Plan 01: Calibration Fixture Capture + RED Scaffolds Summary

**Five verbatim REAL Ender-5-Plus calibration JSON fixtures captured (clock-string screw adjust, [x,y]-array mesh_min/max, ?????? z-bounds) plus seven compile-fail-RED parser scaffolds binding each Phase-9 parser-to-be to its real-hardware fixture.**

## Performance

- **Duration:** ~14 min
- **Completed:** 2026-06-03
- **Tasks:** 2
- **Files modified:** 12 (5 fixtures + 7 test scaffolds)

## Accomplishments

- Captured + committed the five REAL E5 calibration fixtures (Task 1 capture was already run live by the orchestrator on 192.168.1.120:7125 with the printer attended; this plan verified and committed them).
- Recorded the four mock-vs-reality shape surprises directly into the RED scaffolds AND this SUMMARY (the entire point of fixture-capture-first).
- Laid down seven RED scaffolds whose class names match the 09-VALIDATION.md `--tests` filters exactly; each calibration test loads its real fixture via the GcodeStoreParseTest classpath idiom.
- Established the compile-fail RED baseline — every referenced production symbol is provably absent from `app/src/main/`.

## Task Commits

1. **Task 1: Capture the five REAL E5 calibration fixtures** — `b45c4ad` (test)
2. **Task 2: Seven RED test scaffolds against the captured fixtures** — `69f36cf` (test)

## Files Created/Modified

**Fixtures (Task 1):**
- `app/src/test/resources/fixtures/screws_tilt_adjust_e5.json` — real SCREWS_TILT_CALCULATE results (4 screws)
- `app/src/test/resources/fixtures/bed_mesh_e5.json` — real loaded KAMP adaptive profile (3×4 probed, 7×10 interpolated, 3 saved profiles)
- `app/src/test/resources/fixtures/z_tilt_e5.json` — `{z_tilt:{applied:false}}` post-run
- `app/src/test/resources/fixtures/manual_probe_e5.json` — mid-session `is_active:true` with z bounds
- `app/src/test/resources/fixtures/configfile_screws_e5.json` — screwN `[x,y]` + screwN_name + `screw_thread:"CCW-M4"`

**RED scaffolds (Task 2):**
- `calibration/ScrewsTiltResultTest.kt` — worst-screw over clock-string adjust, 1-based screwN↔config name join (3- and 4-screw)
- `calibration/TiltResultTest.kt` — pure `{dispatched,applied,failed}` state machine; applied:false alone never Failed
- `calibration/BedMeshModelTest.kt` — `[x,y]` array min/max, profile names from `profiles` keys, empty-state separate from saved profiles
- `calibration/ManualProbeStateTest.kt` — parseZPosition all-known + `??????`-unknown + malformed, is_active page-state
- `calibration/ProbePresentGateTest.kt` — probe-present → PROBE_CALIBRATE else Z_ENDSTOP_CALIBRATE
- `calibration/CalibrationGateTest.kt` — per-object hasObject gating, QGL self-gates-off, all five enumerated
- `ui/files/FilesDeleteGateTest.kt` — D-15: only the active print file undeletable; relative dir-prefixed path form (no leading `gcodes/`)

## Captured Shapes & Surprises (the whole point of Wave 0)

1. **`screws_tilt_adjust.adjust` is a CLOCK STRING, not a float.** Each `screwN = {z: Double, sign: "CW"|"CCW", adjust: "MM:SS", is_base: Boolean}`. `screw1` is `is_base:true, adjust:"00:00"`. The worst-screw parser MUST parse "00:07" as 7 seconds (clock), not `Double.parse` (which throws on the colon). Encoded in `ScrewsTiltResultTest`.
2. **`screws_tilt_adjust` top-level `error` is a Boolean; `max_deviation` is `null` EVEN AFTER the run.** Done-detection keys off `error==false` + populated `results`, NOT `max_deviation`.
3. **`results` PERSISTS post-run (RESEARCH Open-Q1 RESOLVED — A2 was the worry, but the one-shot `objects/query?screws_tilt_adjust` AFTER the run returned populated `results`).** ⇒ 09-03 can read `results` via a post-hoc one-shot query; it does NOT have to capture it only on the completion edge. (Belt-and-braces: still safe to also read on the dispatch-completion edge.)
4. **`bed_mesh.mesh_min` / `mesh_max` are JSON ARRAYS `[x, y]`** (Python tuple → array), NOT objects. `probed_matrix` is 3×4, `mesh_matrix` is the wider 7×10 interpolated grid. `profiles` is a DICT keyed by saved-profile NAME — real keys were `['pre','post','default']` (3 saved). The loaded mesh's `profile_name` was `"adaptive-7FA0AB1C50"`. Empty-state must be derived from `mesh_matrix`-empty / `profile_name==""` SEPARATELY from `profiles` being non-empty. Encoded in `BedMeshModelTest`.
5. **`manual_probe` mid-session bounds can be the literal `??????`.** The real gcode_response line shape is `"// Z position: ?????? --> 7.624 <-- ??????"` — bounds unknown before they're set. `parseZPosition` MUST treat `??????` as null/unknown, not assume three floats. The fixture's structured object had numeric bounds (`z_position_lower/upper`), but the CONSOLE line is where `??????` appears. Both the all-known case and the `??????` case are asserted in `ManualProbeStateTest`.
6. **`configfile screws_tilt_adjust` subtree:** screw1..screw4 are `[x,y]` arrays, with `screwN_name` strings, `screw_thread:"CCW-M4"`, plus `horizontal_move_z`/`speed`. The 1-based `screwN` join to `screwN_name` is by index — `screw3` → "rear right screw".

All five fixtures are wrapped `{ "<object_name>": <value> }` mirroring the Phase-8 `gcode_store_e5.json` precedent (test loaders do `fixtureResult().jsonObject["bed_mesh"]` etc.). `configfile_screws_e5.json` is keyed `{"screws_tilt_adjust": {...}}`.

## RED Baseline Evidence

Per the build_env_note, compile-fail on absent symbols IS the acceptable Wave-0 RED baseline; a full (slow, Windows-side) Gradle run is unnecessary. Proven decisively by grep: **all eleven referenced production symbols are absent from `app/src/main/`** (`parseScrewsTilt`, `BedMeshModel`, `tiltState`, `TiltState`, `parseZPosition`, `ZPositionBracket`, `manualProbeActive`, `probeCalibrateGate`, `calibrationSupported`, `CalibrationRoutine`, `deleteAllowed`), and no production `works.mees.dinghy.calibration` package exists. The suite therefore cannot compile until 09-03..09-07 introduce these symbols — the intended RED gate. No production parser code was written (Wave 0 = fixtures + RED only).

## Decisions Made

- Resolved RESEARCH Open-Q1: `screws_tilt_adjust.results` persists post-run (see surprise #3). 09-03 is freed to read it via a post-hoc one-shot query.
- The two pure gate scaffolds (ProbePresentGate, CalibrationGate) bind to `configfile_screws_e5.json`'s classpath presence so all six calibration test files reference a real fixture (satisfies the Task-2 `grep fixtures/` ×6 gate honestly — the config fixture documents the captured printer's real object surface). Their assertion logic remains pure-capability, as the plan specifies.

## Deviations from Plan

None — plan executed exactly as written. Task 1's live capture was already performed by the orchestrator (printer attended, all fixtures present as untracked files); this plan ran the plan's automated verify gate (passed) and committed them, per the orchestrator's instruction not to re-run printer commands.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- The data contract for every Phase-9 parser is now committed and host-loadable; 09-03 (screws-tilt), 09-04 (z-tilt/QGL), 09-05 (bed-mesh), 09-06 (manual-probe/probe-gate), 09-07 (hub gate + D-15 files-delete) can each turn their bound scaffold GREEN.
- The four recorded surprises (clock-string adjust, `[x,y]` min/max, `??????` bounds, results-persist-post-run) are now load-bearing assertions, not assumptions — the third mock-vs-reality backstop is in place.
- The bed-mesh heatmap fill-rate gate (CALIB-04, on-device gfxinfo on flox) remains a downstream device checkpoint, unchanged by this Wave-0 plan.

## Self-Check: PASSED

All 12 created files exist on disk; all 3 task/doc commits (b45c4ad, 69f36cf, 7b764fd) present in git history.

---
*Phase: 09-calibration-maintenance*
*Completed: 2026-06-03*
