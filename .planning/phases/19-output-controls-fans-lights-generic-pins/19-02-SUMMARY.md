---
phase: 19-output-controls-fans-lights-generic-pins
plan: 02
subsystem: testing
tags: [moonraker, fixtures, junit, red-scaffold, outputs, nyquist]

# Dependency graph
requires:
  - phase: 19-01
    provides: output-type icon registry (DinghyIcons.Output* tokens) the Wave-2 detail rows reference
provides:
  - 4 real Moonraker captures (E5P rich + E3P sparse) as committed test fixtures under app/src/test/resources/outputs/
  - 3 compiling RED test scaffolds (OutputsGateTest, OutputsHolderTest, PrinterCommandsOutputsTest) mapped to the VALIDATION SC->test names
  - a non-bricking scoped per-wave `--tests outputs.*` filter for downstream Wave-1+ plans
affects: [19-output Wave-1 parser/gate plan, Wave-1 holder plan, Wave-1 command-builder plan, 19-VALIDATION]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Mock-vs-reality: parser tests anchor to UNMODIFIED live-server sub-objects, not idealized mocks"
    - "Wave-0 RED scaffold: typed fail() bodies, zero refs to unbuilt symbols, whole sourceset compiles before --tests filters"
    - "Fixture-load via classloader.getResourceAsStream(\"outputs/*.json\") from test resources"

key-files:
  created:
    - app/src/test/resources/outputs/configfile_settings_e5p.json
    - app/src/test/resources/outputs/objects_list_e5p.json
    - app/src/test/resources/outputs/configfile_settings_e3p.json
    - app/src/test/resources/outputs/objects_list_e3p.json
    - app/src/test/java/works/mees/dinghy/outputs/OutputsGateTest.kt
    - app/src/test/java/works/mees/dinghy/outputs/OutputsHolderTest.kt
    - app/src/test/java/works/mees/dinghy/command/PrinterCommandsOutputsTest.kt
  modified: []

key-decisions:
  - "Captured the unmodified server sub-objects from both live printers; provenance (URLs+date) lives in the commit message, not inline (a bare JSON sub-object cannot carry a comment header)"
  - "Followed the plan's typed-fail() scaffold rule rather than the 09-era CalibrationGateTest precedent (which referenced not-yet-built symbols), per [[dinghy-wave0-red-scaffold-compile]]"
  - "Kept one fixture-resolve probe (realCapturesResolveOnClasspath) GREEN to prove the classpath link the rest of the gate test rides on"

patterns-established:
  - "Per-class scoped Wave-0 scaffolds (HIGH-4): a still-red scaffold owned by another plan never bricks the per-wave run"

requirements-completed: [SC-1, SC-2, SC-3]

# Metrics
duration: ~8min
completed: 2026-06-08
---

# Phase 19 Plan 02: Output Fixtures + RED Scaffolds Summary

**Real Moonraker `configfile.settings` + `objects.list` captures from both dev printers (E5P 9-output rich shape + E3P sparse) committed as fixtures, plus three compiling JUnit RED scaffolds for the Wave-1 output gate/holder/command-builder layers.**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-06-08T03:40:00Z
- **Completed:** 2026-06-08T03:48:00Z
- **Tasks:** 2
- **Files modified:** 7 created

## Accomplishments
- Captured all four fixtures from live printers (read-only GETs): E5P 192.168.1.120:7125 and E3P 192.168.1.121:7125. Both reachable; no synthesis needed.
- E5P capture carries the rich shape — 9 whitelisted outputs (1 fan_generic, 1 led, 1 neopixel, 2 servo, 4 output_pin) plus excluded negatives (extruder/heater_bed/fan/temperature_sensor) so the whitelist-filter test has real negatives.
- Case-recovery source proven in real data: settings is LOWERCASED (`fan_generic filter_fan`) while objects.list is case-PRESERVED (`fan_generic FILTER_fan`, `neopixel expanderPixel`).
- E3P sparse capture = single virtual output_pin (`output_pin ignore_m600`).
- Three RED scaffolds compile as part of the full test sourceset and fail (not error); method names match the 19-VALIDATION SC->test map (emptyHidesTile, pwmDetection, servoValueHidden, commandNameIsBareSectionName, setServoDisableEmitsWidthZero, etc.).

## Task Commits

1. **Task 1: Capture real Moonraker fixtures from both printers** - `196e06b` (test)
2. **Task 2: Compiling RED scaffolds for gate, holder, and command builders** - `1b4da47` (test)

## Files Created/Modified
- `app/src/test/resources/outputs/configfile_settings_e5p.json` - E5P parsed/defaulted settings view (lowercased keys, real `pwm` booleans), 9 whitelisted + excluded sections
- `app/src/test/resources/outputs/objects_list_e5p.json` - E5P case-PRESERVED object names (the command-name / case-recovery source)
- `app/src/test/resources/outputs/configfile_settings_e3p.json` - E3P sparse settings (single virtual output_pin)
- `app/src/test/resources/outputs/objects_list_e3p.json` - E3P object list
- `app/src/test/java/works/mees/dinghy/outputs/OutputsGateTest.kt` - whitelist filter, case-recovery (objectKey), bare commandName (HIGH-1), empty-hides-tile, pwm detection, servo angle-max, excluded-never-appear + a fixture-resolve probe
- `app/src/test/java/works/mees/dinghy/outputs/OutputsHolderTest.kt` - absent-value-degrade, servoValueHidden (PWM not angle), static read-only pin, 0..1->0..100% display scaling in the holder
- `app/src/test/java/works/mees/dinghy/command/PrinterCommandsOutputsTest.kt` - builder gcode strings (bare name), 0-100->0-1 scaling + clamp, LED off all-zero (D-12), servo clamp + WIDTH=0 disable, digital 0/1, pwm scale, pwm_tool-uses-SET_PIN

## Decisions Made
- Captured the unmodified server sub-objects; provenance recorded in the Task-1 commit message (JSON sub-object can't carry a header comment).
- Used typed `fail("not implemented — Wave 1")` bodies with the exact intended assertion in a comment, referencing NO unbuilt symbol — the plan's rule and [[dinghy-wave0-red-scaffold-compile]] override the older CalibrationGateTest precedent that referenced not-yet-built symbols.
- Kept one probe (`realCapturesResolveOnClasspath`) GREEN to prove the classpath resource link.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None. Both printers responded HTTP 200 on the first probe; all four extracted sub-objects parsed as valid JSON.

## Verification
- All 4 fixtures parse as valid JSON (`python3 json.load` per file OK).
- E5P settings contains `fan_generic `/`led `/`neopixel `/`servo `/`output_pin ` (lowercased) AND excluded negatives (extruder/heater_bed/fan/temperature_sensor).
- E5P objects.list preserves case (`fan_generic FILTER_fan`, `neopixel expanderPixel`).
- E3P is the sparse case (single `output_pin ignore_m600`).
- `:app:compileDebugUnitTestKotlin` BUILD SUCCESSFUL with the 3 new files present.
- Running `--tests works.mees.dinghy.outputs.* --tests ...PrinterCommandsOutputsTest`: 22 tests, 21 RED via `AssertionError` (not ERROR), 1 GREEN (the fixture-resolve probe) — the `outputs.*` filter runs without a compile brick.

## Next Phase Readiness
- Wave-1 plans (parser/gate, holder, command builders) have real fixtures + compiling scaffolds to turn GREEN.
- Each scaffold is per-class scoped (HIGH-4) so a still-red scaffold owned by another plan never bricks a per-wave run.
- No blockers.

## Self-Check: PASSED

All 7 created files verified on disk; both task commits (`196e06b`, `1b4da47`) verified in git log.

---
*Phase: 19-output-controls-fans-lights-generic-pins*
*Completed: 2026-06-08*
