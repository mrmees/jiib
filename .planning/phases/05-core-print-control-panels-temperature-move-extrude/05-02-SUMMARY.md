---
phase: 05-core-print-control-panels-temperature-move-extrude
plan: 02
subsystem: command
tags: [gcode, moonraker, kotlinx-serialization, theme-tokens, oklch]

# Dependency graph
requires:
  - phase: 05-01
    provides: GCODE_SCRIPT / TEMPERATURE_STORE method names, HeaterState.canExtrude cold-extrude gate, gcodePosition Move source
  - phase: 03-01
    provides: oklch→sRGB bake pipeline (bake_tokens.py + BakedTokens.kt), ThemeTokens value type
provides:
  - "PrinterCommands: pure gcode builders for every Phase-5 action (set-heater, preset, cooldown, jog, home-all/XY/axis, override-jog, disable-steppers, extrude/retract, tool-select, load/unload) + scriptParams() JSON wrapper"
  - "MATERIAL_PRESETS fixed preheat list (PLA/PETG/ABS/TPU) + Preset data class"
  - "ThemeTokens.violet third sensor-trace token, baked dark+light, for the multi-trace GraphView (05-04)"
affects: [05-03-temperature-panel, 05-04-temperature-graph, 05-05-move-panel, 05-06-extrude-panel, dispatch-layer]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure free-function/object gcode-builder module (mirrors DeriveCapabilities purity discipline) — host-testable, no I/O"
    - "Clamp-before-format for all numeric gcode params (ASVS V5) with named bound consts"
    - "Mode-changing moves wrapped in SAVE_GCODE_STATE/RESTORE_GCODE_STATE (never leave G91/M83)"
    - "New semantic token added via bake script source + re-bake, never hand-typed hex (THEME-01)"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt
    - app/src/test/java/works/mees/dinghy/command/PrinterCommandsTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/theme/ThemeTokens.kt
    - app/src/main/java/works/mees/dinghy/theme/BakedTokens.kt
    - tools/oklch-bake/bake_tokens.py

key-decisions:
  - "applyPreset targets PRIMARY extruder + heater_bed only; multi-tool per-preset targeting deferred (v1 scope)"
  - "violet token in-gamut at authored chroma (dark oklch(0.70 0.16 300), light oklch(0.52 0.18 300)) — no gamut mapping needed"
  - "overrideJog prepends SET_KINEMATIC_POSITION X=0 Y=0 Z=0 then reuses the jog() SAVE/G91/G1/RESTORE body (DRY)"

patterns-established:
  - "Bounded gcode contract: only clamped numerics + fixed axis/heater identifiers ever interpolated — no free-text concatenation"
  - "Trace palette: nozzle=heat, bed=accent, chamber=violet (3 traces max in v1)"

requirements-completed: [TEMP-02, TEMP-03, MOVE-01, MOVE-02, MOVE-03, EXTR-01]

# Metrics
duration: 9min
completed: 2026-06-01
---

# Phase 5 Plan 02: Action-string contract + third trace token Summary

**Pure PrinterCommands gcode-builder module (clamped, SAVE/RESTORE-wrapped) + scriptParams JSON wrapper, plus the violet third sensor-trace token baked dark+light**

## Performance

- **Duration:** 9 min
- **Started:** 2026-06-01T05:30:00Z
- **Completed:** 2026-06-01T05:39:00Z
- **Tasks:** 2
- **Files modified:** 5 (2 created, 3 modified)

## Accomplishments
- `PrinterCommands` object: every Phase-5 action string (setHeater, applyPreset, jog, overrideJog, homeAll/homeXY/homeAxis, extrude, selectTool, load/unloadFilament, COOLDOWN, DISABLE_STEPPERS) plus `scriptParams(gcode)` → `{"script": gcode}` JsonElement.
- All numeric params clamped to named bounds BEFORE formatting (ASVS V5 / T-05-02-T); axis/heater identifiers are fixed sets, no free-text concatenation.
- jog/overrideJog/extrude wrapped in SAVE_GCODE_STATE/RESTORE_GCODE_STATE so the printer never sticks in G91/M83 (T-05-02-Safety).
- `MATERIAL_PRESETS` fixed preheat list (PLA 200/60, PETG 240/80, ABS 245/100, TPU 220/50).
- `ThemeTokens.violet` third sensor-trace token added + baked for dark (`0xFFAE84F2`) and light (`0xFF7B47BF`) via the bake script — the 05-04 multi-trace GraphView reads it via the token, no raw hex (THEME-01).
- 14 host-side unit tests prove exact strings, clamping, axis rejection, and `scriptParams` serialization; baked-token drift guard re-passes.

## Task Commits

Each task was committed atomically:

1. **Task 1: PrinterCommands pure gcode builders + scriptParams** - `2c1df56` (feat)
2. **Task 2: Add the third sensor-trace token (violet) baked dark+light** - `cb52545` (feat)

**Plan metadata:** _(this commit)_ (docs: complete plan)

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt` - pure gcode builders + scriptParams + MATERIAL_PRESETS + named clamp bounds
- `app/src/test/java/works/mees/dinghy/command/PrinterCommandsTest.kt` - exact-string, clamping, axis-guard, serialization tests
- `app/src/main/java/works/mees/dinghy/theme/ThemeTokens.kt` - added `val violet: Color` (3rd trace) with role KDoc
- `app/src/main/java/works/mees/dinghy/theme/BakedTokens.kt` - re-baked: `violet` literal in TokensDark + TokensLight
- `tools/oklch-bake/bake_tokens.py` - added violet oklch source entry (keeps the next bake consistent)

## Decisions Made
- **applyPreset = primary extruder + heater_bed only.** Multi-tool per-preset targeting deferred to a later phase; v1 presets address the two heaters every target printer has.
- **violet sourced via the bake script, not hand-typed.** Added the oklch source to `bake_tokens.py` and re-ran `--write`; the literal lands ONLY in BakedTokens (THEME-01 honored, drift-guard test re-passes). violet was in-gamut at the authored chroma so it needed no gamut mapping.
- **overrideJog reuses jog().** It prepends `SET_KINEMATIC_POSITION X=0 Y=0 Z=0\n` then delegates to `jog()`, so the clamp + SAVE/RESTORE body stays single-sourced.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- The action-string contract is settled: 05-03 (Temperature) / 05-05 (Move) / 05-06 (Extrude) panels can dispatch via `PrinterCommands` + `scriptParams` through the existing `CommandDispatcher` → `GCODE_SCRIPT`.
- The trace palette (heat/accent/violet) is complete for the 05-04 multi-trace GraphView extension.
- No blockers. Disjoint from 05-01; both were Wave-1 pure/config additions.

## Self-Check: PASSED

- All 5 files present (2 created, 3 modified).
- Both task commits present in git log (2c1df56, cb52545).

---
*Phase: 05-core-print-control-panels-temperature-move-extrude*
*Completed: 2026-06-01*
