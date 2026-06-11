---
phase: 23-design-language-foundation
plan: "01"
subsystem: designsystem-tooling
tags: [wave-0, red-scaffolds, ligature-gate, unit-grid, list-row, fill-meter]
dependency_graph:
  requires: []
  provides:
    - tools/verify_ligatures.py NEEDED set extended with 6 Phase-23 redesign ligatures
    - UnitGridTest RED scaffold (fit-preserving formula contract, 6 named tests)
    - ListRowTest RED scaffold (fill/border state selection, 3 named tests)
    - FillMeterTest RED scaffold (fraction clamping, 3 named tests)
  affects:
    - 23-03 (DinghyIconsTest drift guard will see the 6 registered icons added there)
    - 23-04 (turns UnitGridTest GREEN by extracting unitGridFor() pure helper)
    - 23-05 (turns ListRowTest + FillMeterTest GREEN by extracting testable helpers)
tech_stack:
  added: []
  patterns:
    - RED scaffold with fail() bodies + zero unbuilt-symbol references (wave0-compile rule)
    - verify_ligatures.py NEEDED-set-append pattern (same as Phase 19/20)
key_files:
  created:
    - app/src/test/java/works/mees/dinghy/designsystem/layout/UnitGridTest.kt
    - app/src/test/java/works/mees/dinghy/designsystem/components/ListRowTest.kt
    - app/src/test/java/works/mees/dinghy/designsystem/components/FillMeterTest.kt
  modified:
    - tools/verify_ligatures.py
decisions:
  - "A1 discharged: all 6 new Phase-23 ligatures (sort, filter_list, expand_circle_up, expand_circle_down, reset_wrench, reset_settings) resolve in the bundled v2.944 font — gate exits 0 with missing:[]"
  - "DinghyIconsTest derives dynamically from DinghyIcons.all — no edit needed for 23-01; 23-03 registers the 6 new icons and DinghyIconsTest will pick them up automatically"
metrics:
  duration: "~20 minutes"
  completed: "2026-06-09"
  tasks_completed: 3
  files_modified: 4
---

# Phase 23 Plan 01: Wave-0 Tooling and RED Scaffolds Summary

**One-liner:** Extended verify_ligatures.py with 6 Phase-23 redesign glyphs (A1 gate discharged) and created three compile-day-one RED test scaffolds for UnitGrid/ListRow/FillMeter.

## What Was Built

### Task 1: `tools/verify_ligatures.py` extended

Added the 6 owner-assigned Phase-23 redesign ligatures to the NEEDED set:

| Ligature | Function |
|----------|----------|
| `sort` | Sort-row control-group leader |
| `filter_list` | Filter-row control-group leader |
| `expand_circle_up` | Load spool (replaces old `play_circle` bucket note) |
| `expand_circle_down` | Unload spool (replaces old `stop_circle` bucket note) |
| `reset_wrench` | Reset a single setting |
| `reset_settings` | Reset all settings |

Gate result: `69 needed, 3953 ligatures in font, missing: []` — exits 0. Assumption A1 from 23-RESEARCH discharged. All 6 are present in the bundled v2.944 Material Symbols font.

### Task 2: DinghyIconsTest GREEN baseline confirmed

DinghyIconsTest was run against the unchanged registry. Result: `BUILD SUCCESSFUL` — all 4 tests pass. Confirmed the test **derives its uniqueness/drift checks dynamically from `DinghyIcons.all`** (no hardcoded count). No edit needed for 23-01. Plan 23-03 registers the 6 new icons and DinghyIconsTest will pick them up automatically without any test-file modification.

### Task 3: Three RED test scaffolds

Created the `designsystem/layout` and `designsystem/components` test packages (neither existed):

**`UnitGridTest.kt`** — 6 named tests documenting the fit-preserving formula contract:
- `n_clamps_to_5_floor` — at 320dp, nMaxFit=5 wins over nTarget=7
- `n_clamps_to_7_ceiling` — at 600dp, N clamps to ceiling of 7
- `udp_never_below_64dp` — U is always at or above the 64dp touch-target floor
- `udp_equals_dim_over_n` — the fit-preserving invariant: N*uDp == contentMinDim
- `no_overflow_at_320dp` — count*uDp <= 320dp AND uDp >= 64dp (the overflow bug the reviewers flagged)
- `no_overflow_at_360dp` — same invariant at 360dp

**`ListRowTest.kt`** — 3 named tests for fill/border state selection:
- `selected_uses_accentSoft_fill`
- `unselected_uses_transparent_fill`
- `selected_uses_2dp_border`

**`FillMeterTest.kt`** — 3 named tests for fraction clamping:
- `fraction_clamps_below_zero_to_zero`
- `fraction_clamps_above_one_to_one`
- `fraction_in_range_unchanged`

All scaffolds: `fail()` bodies only, zero references to `rememberUnitGrid`/`UnitGrid`/`ListRow`/`FillMeter` composable symbols (those don't exist until Waves 2-3). Compile verification: `:app:compileDebugUnitTestKotlin` BUILD SUCCESSFUL.

## Verification

- `python tools/verify_ligatures.py` → `69 needed, 3953 ligatures in font, missing: []` — exits 0
- `:app:testDebugUnitTest --tests ...DinghyIconsTest` → BUILD SUCCESSFUL (4 tests, all GREEN)
- `:app:compileDebugUnitTestKotlin` → BUILD SUCCESSFUL (whole test sourceset compiles)

## Commits

| Hash | Type | Description |
|------|------|-------------|
| `36d7fd1` | chore | extend verify_ligatures.py with 6 Phase-23 redesign ligatures |
| `647e593` | test | add RED scaffolds for UnitGrid, ListRow, FillMeter (Wave 0) |

## Deviations from Plan

None — plan executed exactly as written.

Task 2 confirmed: DinghyIconsTest derives dynamically from `DinghyIcons.all` — expected outcome per STATE.md 19-01 note ("DinghyIconsTest needed NO edit — it derives uniqueness/drift checks dynamically from `DinghyIcons.all`"). No edit needed; 23-03 can register the 6 new icons without any test-file changes.

## Known Stubs

None — this plan is tooling + RED scaffolds only. The `fail()` bodies are intentional RED markers, not stubs in the product sense. They are documented to turn GREEN in plans 23-04 (UnitGrid) and 23-05 (ListRow, FillMeter).

## Threat Flags

No new attack surface — this plan is Python tooling + JUnit4 host-test scaffolds over a committed font asset.

## Self-Check: PASSED

- `tools/verify_ligatures.py` exists and contains all 6 new ligatures ✓
- `UnitGridTest.kt` exists at `app/src/test/.../designsystem/layout/` ✓
- `ListRowTest.kt` exists at `app/src/test/.../designsystem/components/` ✓
- `FillMeterTest.kt` exists at `app/src/test/.../designsystem/components/` ✓
- Commits `36d7fd1` and `647e593` present in git log ✓
- No references to unbuilt symbols in scaffolds (compile BUILD SUCCESSFUL confirms) ✓
