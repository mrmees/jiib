---
phase: 27-motion-calibration
plan: "01"
subsystem: design-system/icons
tags: [icons, registry, calibration, tokens, font-verify]
dependency_graph:
  requires: []
  provides:
    - DinghyIcons.RoutineProbeCalibrate
    - DinghyIcons.RoutineBedMesh
    - DinghyIcons.RoutineScrewsTilt
    - DinghyIcons.RoutineZTilt
    - DinghyIcons.RoutineQgl
  affects:
    - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
    - tools/verify_ligatures.py
    - app/src/main/res/values/strings.xml
tech_stack:
  added: []
  patterns:
    - icon-registry-only law (DinghyIcons token + DinghyIcons.all + verify_ligatures.py)
    - Routine* token naming convention (27-PATTERNS.md §"Icon Registration Gate")
key_files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
    - tools/verify_ligatures.py
    - app/src/main/res/values/strings.xml
decisions:
  - "Owner blessed all 5 current raw ligatures as registered tokens (no visual churn)"
  - "Distance-stepper +/- cells REUSE BabystepExpand/BabystepCompress (no new tokens)"
metrics:
  duration: "~10 minutes"
  completed: "2026-06-12"
  tasks_completed: 2
  tasks_total: 2
  files_modified: 3
---

# Phase 27 Plan 01: Calibration Routine Icon Registry Summary

**One-liner:** 5 owner-confirmed calibration hub routine glyph tokens registered, font-verified, and drift-guarded; Wave-1 screen plans now have registry tokens to consume.

## What Was Built

Registered 5 `DinghyIcons` tokens for the calibration hub routine icons (previously used as raw `MaterialSymbol(name = entry.routine.glyph)` in `CalibrationHubScreen.kt`). The owner confirmed the current ligatures are blessed as-is. Added all 5 to `DinghyIcons.all`, extended `verify_ligatures.py` NEEDED set, and added content-description strings.

**Owner decisions (verbatim, 2026-06-12 via orchestrator AskUserQuestion):**

### Group A — 5 calibration hub routine icons
> "Bless all 5 as-is."

| Token | Ligature | Meaning |
|-------|----------|---------|
| `RoutineProbeCalibrate` | `straighten` | Probe Calibrate routine |
| `RoutineBedMesh` | `grid_on` | Bed Mesh routine |
| `RoutineScrewsTilt` | `architecture` | Screws Tilt routine |
| `RoutineZTilt` | `vertical_align_center` | Z Tilt routine |
| `RoutineQgl` | `crop_square` | Quad Gantry Level routine |

### Group B — Move/TESTZ distance-stepper +/- cells
> "Reuse expand/compress."

No new stepper tokens added. The Wave-1 Move/TESTZ screens will reference the existing `DinghyIcons.BabystepExpand` and `DinghyIcons.BabystepCompress` tokens for the distance-selector increment/decrement cells.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | (pre-resolved) | Owner decisions captured via orchestrator checkpoint; no code commit |
| Task 2 | `6794339` | feat(27-01): register 5 owner-confirmed calibration routine glyph tokens |

## Verification Results

- `verify_ligatures.py`: **92 needed, 3953 ligatures in font, missing: []** — exit 0
- `DinghyIconsTest`: **BUILD SUCCESSFUL** — all 4 test methods GREEN
  - `everyEntry_hasNonBlankAlternate_andResolvableIconRef` PASS
  - `alternate_isUnique_acrossAllEntries` PASS
  - `iconRef_isUnique_acrossAllEntries` PASS
  - `registryDrawableEntries_areOnlyTheCustomKeepers` PASS

## Deviations from Plan

None — plan executed exactly as written. Task 1 was pre-resolved by the orchestrator; Task 2 registered exactly the owner-confirmed glyphs with zero Claude-picked choices.

## Known Stubs

None — this plan registers tokens only; no screen rendering or data wiring.

## Threat Flags

None — icon registry and string resource edits only; no network, auth, input, or data boundary crossed.

## Self-Check: PASSED

- `DinghyIcons.kt` defines `RoutineProbeCalibrate`, `RoutineBedMesh`, `RoutineScrewsTilt`, `RoutineZTilt`, `RoutineQgl` — confirmed
- All 5 present in `DinghyIcons.all` — confirmed
- `verify_ligatures.py` exits 0 with `missing: []` — confirmed
- `DinghyIconsTest` GREEN — confirmed
- Zero edits to any file under `ui/calibration/` or `ui/move/` — confirmed
- No glyph chosen by Claude — all match owner's Task-1 decisions — confirmed
