---
phase: 26-adjustment-screens
plan: 01
subsystem: ui
tags: [compose, designsystem, components, adjusterpanel, incrementpicker, tdd, preview]

# Dependency graph
requires:
  - phase: 25-browse-screens
    provides: FootButtonBar, OutlinedControl, ListRow, UnitGrid — the shared Phase-23 design kit consumed here
  - phase: 17-fine-tune-live-adjust
    provides: fmtValue, DASH, FineTuneShared constants — imported by AdjusterPanel
provides:
  - IncrementPicker: shared step-tile row component (ImmutableList<Double> steps, active=Intent.Accent)
  - AdjusterPanel: shared 3-zone adjuster Focus surface (header+Reset/centered value/"was X"/stepper+picker)
  - formatStep: internal pure function (whole-number and decimal formatting with ± prefix)
  - shouldShowBaseline: internal pure predicate (null-guard + rounding comparison at arbitrary decimal precision)
affects:
  - 26-02 (Fine-Tune rebuild — primary consumer of AdjusterPanel + IncrementPicker)
  - 26-03 (Temperature rebuild — second consumer)
  - 26-04 (Outputs rebuild — third consumer)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "3-zone adjuster column (SpaceBetween): header / centered value / bottom controls"
    - "Inline baseline: Row(verticalAlignment=Bottom) with value Text + 'was X' Text side by side — NEVER stacked"
    - "formatStep: whole-number branch via toLong(), decimal branch via BigDecimal.stripTrailingZeros()"
    - "shouldShowBaseline: 10^precision rounding to avoid floating-point noise in the comparison"
    - "IncrementPicker: Intent.Accent for active step, Intent.Neutral for inactive — sketch-003 law"
    - "AdjusterPanel Reset = Intent.Warn (amber heat, not color-mix — prevents red-bleed)"
    - "TDD RED/GREEN: production stubs compile first (Wave-0 brick guard), then real bodies in next task"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/designsystem/components/IncrementPicker.kt
    - app/src/main/java/works/mees/dinghy/designsystem/components/AdjusterPanel.kt
    - app/src/test/java/works/mees/dinghy/designsystem/components/IncrementPickerTest.kt
    - app/src/test/java/works/mees/dinghy/designsystem/components/AdjusterPanelTest.kt
    - app/src/main/java/works/mees/dinghy/preview/AdjusterPreviews.kt
  modified: []

key-decisions:
  - "Inline baseline 'was X' implemented in same Row(verticalAlignment=Bottom) as value text — never stacked (adjustment-controls.md anti-pattern: stacked line overflows 5U phone-landscape focus)"
  - "Reset button uses Intent.Warn (t.heat amber) directly — not color-mix() which produces red-bleed bug"
  - "IncrementPicker heightIn uses uDp from UnitGrid (never hardcoded 64.dp literal)"
  - "formatStep uses BigDecimal.stripTrailingZeros().toPlainString() for decimal cases — avoids scientific notation and trailing zero noise"
  - "shouldShowBaseline uses 10^precision rounding before comparing — eliminates floating-point representation differences"
  - "Preview matrix: 6 theme combos + fs=L (fsLargeSeed) + landscape (800x480dp) + DASH path = 10 @Preview annotations"
  - "AdjusterPanel stepper uses onClick guard (enabled && value != null) rather than a visual disabled state — OutlinedControl has no enabled param"

patterns-established:
  - "Wave-0 compile-brick guard: production stubs with placeholder bodies first, then real implementations — test source set always compiles"
  - "AdjusterPanel contract: icon/name/value/unit/baseline/decimals/onDecrement/onIncrement/onReset/enabled/incrementPicker slot"
  - "IncrementPicker contract: steps: ImmutableList<Double>/activeStep/onSelect/uDp — caller owns the step set"

requirements-completed: []

# Metrics
duration: 25min
completed: 2026-06-10
---

# Phase 26 Plan 01: Shared Adjuster Components Summary

**`IncrementPicker` + `AdjusterPanel` design-system components with `formatStep`/`shouldShowBaseline` pure predicates, 10 @Preview annotations, and TDD RED→GREEN cycle**

## Performance

- **Duration:** 25 min
- **Started:** 2026-06-10T21:39:35Z
- **Completed:** 2026-06-10T22:04:00Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments

- `IncrementPicker` composable: step-tile Row using `ImmutableList<Double>`, active=`Intent.Accent`, `heightIn(min=uDp)` (no hardcoded floor)
- `AdjusterPanel` composable: 3-zone `Column(SpaceBetween)` with correct inline baseline (same `Row(verticalAlignment=Bottom)` as value), Reset=`Intent.Warn`, stepper enabled gate
- `formatStep` + `shouldShowBaseline` extracted as `internal` host-testable pure functions; 10 test cases all GREEN
- Preview matrix in `AdjusterPreviews.kt`: 6 theme combos + `fs=L` + landscape + DASH path (10 `@Preview` entries)
- All gates pass: `compileDebugUnitTestKotlin` (Task 1) + both test classes GREEN (Tasks 2/3) + `assembleDebug` GREEN (Task 3)

## Task Commits

Each task was committed atomically:

1. **Task 1: Production stubs + RED test scaffolds** - `bd0ea7c` (test)
2. **Task 2: IncrementPicker shared component** - `488c41a` (feat)
3. **Task 3: AdjusterPanel 3-zone composable + previews** - `69f4dba` (feat)

## Files Created/Modified

- `app/src/main/java/works/mees/dinghy/designsystem/components/IncrementPicker.kt` — `IncrementPicker` composable + `formatStep` pure function
- `app/src/main/java/works/mees/dinghy/designsystem/components/AdjusterPanel.kt` — `AdjusterPanel` 3-zone composable + `shouldShowBaseline` pure predicate
- `app/src/test/java/works/mees/dinghy/designsystem/components/IncrementPickerTest.kt` — 4 `formatStep` test cases
- `app/src/test/java/works/mees/dinghy/designsystem/components/AdjusterPanelTest.kt` — 6 `shouldShowBaseline` test cases
- `app/src/main/java/works/mees/dinghy/preview/AdjusterPreviews.kt` — 10 `@Preview` entries (6 combos + fs=L + landscape + DASH path)

## Decisions Made

- Stepper onClick guards `(enabled && value != null)` inside the lambda because `OutlinedControl` has no `enabled` param — visual state shows intent-colored border regardless, callback is guarded
- `formatStep` uses `BigDecimal.stripTrailingZeros().toPlainString()` to avoid scientific notation (e.g. `1E-3`) and trailing zeros without manual regex
- Preview DASH path uses a separate `AdjusterPanelDashDemo` to ensure null-value + no-Reset rendering compiles and is visible

## Deviations from Plan

None - plan executed exactly as written. TDD RED/GREEN/REFACTOR sequence honored:
- Task 1 = RED (stubs + tests fail on assertions, not compile)
- Task 2 = GREEN (formatStep → IncrementPickerTest passes)
- Task 3 = GREEN (shouldShowBaseline → AdjusterPanelTest passes + assembleDebug)

## Known Stubs

None — both components are fully wired. The `incrementPicker: @Composable () -> Unit` slot in `AdjusterPanel` is intentionally a caller-provided lambda (not a stub); it is populated by downstream screen plans (26-02/26-03/26-04).

## Threat Flags

None — these are pure presentation components. No network endpoints, no auth paths, no file access. Value formatting is display-only; no gcode is built here (threat model T-26-01-01 accepted).

## Self-Check: PASSED

- `app/src/main/java/works/mees/dinghy/designsystem/components/IncrementPicker.kt` — FOUND
- `app/src/main/java/works/mees/dinghy/designsystem/components/AdjusterPanel.kt` — FOUND
- `app/src/test/java/works/mees/dinghy/designsystem/components/IncrementPickerTest.kt` — FOUND
- `app/src/test/java/works/mees/dinghy/designsystem/components/AdjusterPanelTest.kt` — FOUND
- `app/src/main/java/works/mees/dinghy/preview/AdjusterPreviews.kt` — FOUND
- Commits bd0ea7c / 488c41a / 69f4dba — FOUND in git log

## Next Phase Readiness

- `IncrementPicker` + `AdjusterPanel` ready for use in Plans 26-02 (Fine-Tune), 26-03 (Temperature), 26-04 (Outputs)
- No blockers. Both components compile and pass all tests.

---
*Phase: 26-adjustment-screens*
*Completed: 2026-06-10*
