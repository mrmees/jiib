---
phase: 09-calibration-maintenance
plan: 02
subsystem: state
tags: [kotlin, command-registry, reducer, calibration, moonraker, asvs-v5, spine]

# Dependency graph
requires:
  - phase: 09-calibration-maintenance
    provides: "Five REAL E5 calibration fixtures (the data contract) + the RED scaffolds that 09-03+ turn GREEN (09-01)"
  - phase: 06-command-reference-capability-matrix
    provides: "CommandRegistry gcode(...) factory + AvailabilityPredicate + CommandCatalogDriftTest + docs/commands sidecars"
  - phase: 05-temp-move-extrude
    provides: "CommandDispatcher G4 120s gcode timeout (keyed off method==GCODE_SCRIPT) + the minExtrudeTemp one-shot StateFlow seam"
provides:
  - "14 registered calibration CommandSpecs (screwsTiltCalculate/zTiltAdjust/quadGantryLevel/bedMeshCalibrate/bedMeshProfile{Save,Load,Remove}/probeCalibrate/zEndstopCalibrate/testZ/accept/abort/saveConfig) with ObjectPresent/GcodeCommandPresent/Always gating + inherited G4 120s timeout"
  - "PrinterState nullable calibration fields (screwsTilt/zTiltApplied/qglApplied/bedMesh/manualProbe) + ScrewsTiltObject/ScrewResult/BedMeshObject/ManualProbeObject/ScrewConfig/Screw models"
  - "Null-safe reducer walks of the five live objects + a double2dListOrNull matrix accessor"
  - "The five objects + probe added to V1_SUBSCRIBE_CORE (A3 intersect-with-detected)"
  - "PrinterStateStore.screwsTiltConfig one-shot StateFlow + setScrewsTiltConfig (the 09-04 holder wires the read)"
  - "Pure clamp-before-format testZ() + allowlist sanitizeProfileName() (the V5 gcode-injection gate for the keyboard-editable mesh profile name)"
affects: [09-03, 09-04, 09-05, 09-06, 09-07, calibration holders, screens, files-delete D-15]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Structured-live-object result source (RESEARCH Pattern 1): reducer surfaces the whole calibration object verbatim; console parsing is reserved for the in-progress feed only (09-04+)"
    - "Whole-object REPLACE in the reducer (vs the heaters' field-merge): a present calibration object replaces the prior value; absent = retained; garbage = skipped, never fatal"
    - "Clamp-before-format + strict allowlist for the ONE keyboard-editable gcode param (mesh profile name) — newline rejection blocks a second-gcode-line injection"

key-files:
  created:
    - app/src/test/java/works/mees/dinghy/command/BedMeshProfileNameTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt
    - app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterState.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt
    - app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt
    - app/src/test/java/works/mees/dinghy/command/PrinterCommandsTest.kt
    - app/src/test/java/works/mees/dinghy/state/DeriveCapabilitiesTest.kt
    - app/src/test/java/works/mees/dinghy/state/PrinterStateReducerTest.kt
    - docs/commands/catalog.json
    - docs/commands/printer-matrix.json

key-decisions:
  - "zEndstopCalibrate gates on GcodeCommandPresent(Z_ENDSTOP_CALIBRATE), NOT ObjectPresent(probe) — a probe predicate would hide the command on exactly the probe-LESS printers that need it (A3). Mirrors the FORCE_MOVE registration."
  - "saveConfig is AvailabilityPredicate.Always (host action, not object-gated, D-12); its catalog availability was aligned to `always` to stay honest with the runtime predicate."
  - "Reducer carries the calibration objects VERBATIM (clock-string adjust, [x,y] arrays, raw matrices) — the worst-screw clock math, mesh→color mapping, and ??????→null console parse are the 09-03..09-06 pure parsers' job, NOT the reducer's."
  - "bed_mesh mesh_min/mesh_max confirmed [x,y] JSON ARRAYS against the real fixture → read via doubleListOrNull (List<Double>), not an object walk (Pitfall 6 / 09-01 surprise #4)."
  - "Screws results-persistence hook NOT needed at the reducer level — 09-01 already RESOLVED that screws_tilt_adjust.results PERSISTS post-run; the belt-and-braces post-completion one-shot query is left as a 09-04 holder concern (screwsTiltConfig is its companion one-shot)."

patterns-established:
  - "A new registered runtime command needs THREE sidecar touches to keep CommandCatalogDriftTest green: catalog.json row (exists + registered=true), printer-matrix.json command_availability row, and not_on_printers exclusions wherever a printer lacks the predicate's evidence."
  - "GREEN-over-real-fixture reducer tests feed the captured *_e5.json wrapped object straight in as a one-object notify_status_update diff (the fixtures ARE the diff shape)."

requirements-completed: []

# Metrics
duration: 30min
completed: 2026-06-03
---

# Phase 9 Plan 02: Calibration Spine (Commands + Live Objects + Subscribe Superset) Summary

**The load-bearing Phase-9 plumbing: ~14 object-gated calibration commands (G4-timeout inherited), the five calibration live objects surfaced verbatim through the null-safe reducer into retained PrinterState fields, those objects added to the A3 subscribe superset, and the `[screws_tilt_adjust]` config one-shot seam — all pure/headless, ADR-0001 toolkit-agnostic, no UI.**

## Performance

- **Duration:** ~30 min
- **Completed:** 2026-06-03
- **Tasks:** 2 (both `tdd="true"`, both GREEN over the real fixtures)
- **Files modified:** 12 (6 main + 3 test extended + 1 test new + 2 sidecars)

## Accomplishments

### Task 1 — calibration commands + pure gcode builders (commit `5506fbf`)
- `PrinterCommands`: nine constant action gcodes (SCREWS_TILT_CALCULATE / Z_TILT_ADJUST / QUAD_GANTRY_LEVEL / **bare** BED_MESH_CALIBRATE / PROBE_CALIBRATE / Z_ENDSTOP_CALIBRATE / ACCEPT / ABORT / SAVE_CONFIG); `testZ(step)` clamp-before-format to `±MAX_TESTZ_MM` (T-09-02-01); `sanitizeProfileName` strict allowlist `[A-Za-z0-9_.-]+` + the three `bedMeshProfile{Save,Load,Remove}` builders routing through it (T-09-02-02 — a newline would inject a second gcode line).
- `CommandRegistry`: 14 calibration `CommandSpec`s via the existing `gcode(...)` factory (so each inherits the G4 120s timeout automatically — no new timeout code). `zEndstopCalibrate` uses `GcodeCommandPresent("Z_ENDSTOP_CALIBRATE")` (the A3 probe-less sibling), `saveConfig` is `Always`, the rest are `ObjectPresent`. All added to `all`.
- Sidecars: added the five missing catalog rows (TESTZ/ACCEPT/BED_MESH_PROFILE_SAVE/LOAD/REMOVE), flipped `registered=true` for all 13 registered calibration ids, aligned SAVE_CONFIG→`always`/ABORT→`object_present:manual_probe` catalog availability to the registry, and added `command_availability` rows + `not_on_printers` exclusions to `printer-matrix.json` (z_tilt ∉ ender3, quad_gantry_level ∉ ender5plus, Z_ENDSTOP_CALIBRATE ∉ both — both printers have probes).
- `BedMeshProfileNameTest` (9) GREEN — newline/`\r\n`/space/tab/`;`/`=`/emoji/empty/over-length rejected; the `YY.MM.DD_HH.MM` timestamp + benign names accepted; rejection propagates through every builder.

### Task 2 — five live objects + subscribe superset + screws config one-shot (commit `bcb8265`)
- `PrinterState`: nullable `screwsTilt`/`zTiltApplied`/`qglApplied`/`bedMesh`/`manualProbe` + the `ScrewsTiltObject`/`ScrewResult`/`BedMeshObject`/`ManualProbeObject`/`ScrewConfig`/`Screw` models — plain data classes, no Compose annotations (ADR-0001).
- `PrinterStateReducer`: null-safe `objectOrNull(...)` walks for all five objects + a new `double2dListOrNull` matrix accessor; whole-object REPLACE (vs heaters' field-merge); a garbage/missing field degrades to null/retained, never throws.
- `DeriveCapabilities`: `screws_tilt_adjust`/`z_tilt`/`quad_gantry_level`/`bed_mesh`/`manual_probe`/`probe` added to `V1_SUBSCRIBE_CORE` (the `deriveSubscribeSet` intersect loop enforces A3 — no unconditional requests).
- `PrinterStateStore`: `screwsTiltConfig: StateFlow<ScrewConfig?>` + `setScrewsTiltConfig` modeled exactly on the `minExtrudeTemp` one-shot seam (the 09-04 holder wires the single `configfile` read; Pitfall 3 — no duplicate query).
- Tests: `PrinterStateReducerTest` (22) fed the REAL `bed_mesh_e5`/`screws_tilt_adjust_e5`/`manual_probe_e5`/`z_tilt_e5` fixtures + a malformed-bed_mesh-retains case; `DeriveCapabilitiesTest` (12) proves present-when-detected AND absent-when-missing per object.

## Output-spec answers (per the plan)

- **mesh_min / mesh_max wire shape:** confirmed JSON **arrays `[x, y]`** against the real `bed_mesh_e5.json` (e.g. `mesh_min: [85.521…, 148.79]`). Read via `doubleListOrNull` into `List<Double>`; a `.jsonObject["x"]` walk would have thrown. (Pitfall 6 / 09-01 surprise #4.)
- **Screws results-persistence hook:** **not needed at the reducer/spine level.** 09-01 already resolved that `screws_tilt_adjust.results` persists post-run, so the reducer just surfaces it from the subscribe diff. The belt-and-braces post-completion one-shot `objects/query?screws_tilt_adjust` is deferred to the 09-04 holder; `screwsTiltConfig` (the config one-shot) is the companion seam provided here.

## Deviations from Plan

None — plan executed exactly as written. No bugs, missing functionality, or blocking issues required Rules 1–3; no architectural decisions triggered Rule 4. The threat register's five `mitigate` dispositions were all satisfied by the planned work (testZ clamp, sanitizeProfileName allowlist, null-safe reducer, ObjectPresent gating, zero packages installed).

## Process note (sibling-RED scaffolds)

The seven Wave-0 RED scaffolds (`calibration/*Test.kt` + `ui/files/FilesDeleteGateTest.kt`) reference production symbols 09-03..09-07 introduce (`parseScrewsTilt`, `BedMeshModel`, `tiltState`, `parseZPosition`, `probeCalibrateGate`, `calibrationSupported`, `deleteAllowed`) and are intentionally compile-fail RED. Because `testReleaseUnitTest` compiles the whole test source set, they were temporarily SET ASIDE to run this plan's GREEN tests, then RESTORED byte-identical (git shows no drift) — the established Phase-8 pattern. The combined Task-1+Task-2 run was **BUILD SUCCESSFUL** (BedMeshProfileNameTest 9, CommandCatalogDriftTest 6, PrinterCommandsTest 17, DeriveCapabilitiesTest 12, PrinterStateReducerTest 22 — all 0 failures).

## Verification

- `gw.bat :app:testReleaseUnitTest --tests '*CommandCatalogDrift*' --tests '*CommandRegistry*' --tests '*PrinterCommands*' --tests '*BedMeshProfileName*' --tests '*DeriveCapabilities*' --tests '*PrinterStateReducer*' --no-daemon` → **BUILD SUCCESSFUL**, 0 failures across all six classes.
- The release Kotlin main source compiled clean (`compileReleaseKotlin` succeeded), implying `assembleRelease` would compile.
- Per-wave full-suite green + on-device UAT remain the ORCHESTRATOR/Phase-gate concern (this plan is the headless spine).

## Requirements

Per the prior-wave instruction and REQUIREMENTS.md traceability, the umbrella reqs **BEDM-01 / BEDL-01 / ZCAL-01 stay In Progress** (Pending until Phase-9 on-device UAT closes) and **CALIB-02..05 stay Planned** — they are closed by the 09-03..09-07 screens + the on-device UAT, NOT by this spine plumbing. **No requirements marked Complete here.**

## Next Phase Readiness

- 09-03 (screws-tilt), 09-04 (z-tilt/QGL + screwsTiltConfig holder + results one-shot hook), 09-05 (bed-mesh heatmap), 09-06 (manual-probe/probe-gate), 09-07 (hub + D-15) can now each code against the registered commands, the retained PrinterState fields, the subscribe superset, and the `screwsTiltConfig` seam.
- The bed-mesh heatmap fill-rate gate (CALIB-04, gfxinfo on flox) and the full on-device calibration UAT remain downstream device checkpoints.

## Self-Check: PASSED

All 12 created/modified files exist on disk; both task commits (5506fbf, bcb8265) present in git history; combined unit run BUILD SUCCESSFUL with 0 failures.

---
*Phase: 09-calibration-maintenance*
*Completed: 2026-06-03*
