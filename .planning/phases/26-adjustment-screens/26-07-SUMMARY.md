---
phase: 26-adjustment-screens
plan: "07"
subsystem: ui/macros, ui/spool, designsystem
tags: [numpad-retirement, numeric-ime, d07, d08, security, field-takeover]
dependency_graph:
  requires: ["26-06"]
  provides: ["NumpadPage retired", "MeasuredWeightPage retired", "D-07 complete"]
  affects: [BookmarkedMacrosScreen, SpoolScreen, SpoolHolder]
tech_stack:
  added: []
  patterns:
    - "BasicTextField(KeyboardType.Decimal/Number, ImeAction.Done) for numeric IME"
    - "FieldMode.MeasureWeight sealed class variant for SpoolHolder Field-takeover"
    - "ColumnScope extension composable for Field-takeover sections (SpoolMeasureWeightField)"
key_files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/macros/MacroModels.kt
    - app/src/main/java/works/mees/dinghy/command/MacroInvocation.kt
    - app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt
    - app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt
    - app/src/main/java/works/mees/dinghy/designsystem/control/OutlinedControl.kt
    - app/src/main/java/works/mees/dinghy/preview/MacrosPreviews.kt
    - app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt
    - app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt
    - app/src/test/java/works/mees/dinghy/command/MacroInvocationTest.kt
    - app/src/main/res/values/strings.xml
  deleted:
    - app/src/main/java/works/mees/dinghy/designsystem/NumpadPage.kt
    - app/src/main/java/works/mees/dinghy/ui/spool/MeasuredWeightPage.kt
decisions:
  - "SpoolMeasureWeightField implemented as ColumnScope extension (consistent with SpoolListField/SpoolFilterPickerField patterns) rather than a standalone Screen composable"
  - "MeasureWeight flow routed through SpoolHolder FieldMode (not local remembered state) — consistent with FilterPicker; avoids rememberCoroutineScope write-scope trap"
  - "KeyboardType.Decimal used for weight field (allows decimal input); KeyboardType.Number used in original MeasuredWeightPage (integers only) — Decimal is more correct for fractional gram weights"
  - "grams!! used after valid check (not val g = grams + null-check) to suppress compiler warnings about always-true null checks on captured vars"
metrics:
  duration_minutes: 95
  tasks_completed: 3
  files_changed: 13
  completed_date: "2026-06-10"
---

# Phase 26 Plan 07: NumpadPage Retirement (D-07 Complete) Summary

**One-liner:** Replaced NumpadPage and MeasuredWeightPage with inline `BasicTextField` numeric-IME Field-takeovers in BookmarkedMacrosScreen and SpoolScreen, then deleted both retired files.

## What Was Built

### Task 1 — Macro Numeric Params → Numeric IME (BookmarkedMacrosScreen)

`MacroNumericParamField` composable replaces the `NumpadPage` Field-takeover for `isNumeric` macro params:

- `BasicTextField` with `KeyboardType.Decimal` + `ImeAction.Done` rendered inline in the param list
- Two-phase update: `onValueChange` keeps the parent `values` map live (so Execute sees typed-but-not-committed values); `onDone` applies the clamp via `MACRO_NUMERIC_RANGE` before storing
- Security: `MacroInvocation.buildTyped` still runs the full REJECT-on-forbidden-char sanitizer; no raw IME text reaches gcode (T-26-07-01 / T-25-05-01)
- KDoc updated in `MacroInvocation.kt`, `MacroInvocationTest.kt`, `MacroModels.kt`, `MacrosPreviews.kt` to remove NumpadPage references

### Task 2 — SpoolScreen D-08 Measured-Weight → Numeric IME Field-takeover

`FieldMode.MeasureWeight(spool: SpoolmanSpool)` added to `SpoolHolder.FieldMode` sealed class.

`SpoolHolder` new methods:
- `openMeasureWeight(spool)` — transitions `fieldMode` to `MeasureWeight`
- `closeMeasureWeight()` — returns to `FieldMode.Spools`
- `measureSpool(spool, grossGrams)` — calls `client.measureSpool`, closes on success, refreshes

`SpoolMeasureWeightField` ColumnScope composable (replaces MeasuredWeightPage overlay):
- `BasicTextField` with `KeyboardType.Decimal` + `ImeAction.Done`; input filtered to digits + one decimal, max 8 chars
- Validity gate: `grams != null && grams > 0.0` before dispatch (T-26-07-02)
- Info card shows spool name, tare weight, current believed total via `SpoolMeasureWeightStat` helper
- `FootButtonBar`: Back = `Intent.Danger` (discards pending measurement, C7) + Set = `Intent.Go`
- 8 `stringResource` strings added (no hardcoded text)
- `SampleFixtures.spoolWithMeasureOpen` fixture + `SpoolMeasureWeightOpen` `@Nexus7Previews` preview added

### Task 3 — Delete Retired Files

- `NumpadPage.kt` deleted — zero callers remained after Task 1
- `MeasuredWeightPage.kt` deleted — zero callers remained after Task 2
- `OutlinedControl.kt` KDoc updated: `MeasuredWeightPage` C7 example → SpoolScreen measure-weight Field-takeover
- SpoolScreen.kt: `[MeasuredWeightPage]` KDoc links converted to plain-text historical notes

## Verification

- `assembleDebug` + `testDebugUnitTest` passed after each task
- Final build after deletion: BUILD SUCCESSFUL, 0 broken references to deleted symbols
- `MacroInvocationTest` all passing (covers both `build` and `buildTyped` sanitizer paths)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Compiler "condition always true" warnings from double null-check**
- **Found during:** Task 2 build
- **Issue:** `val g = grams; if (valid && g != null)` produced two compiler warnings — `valid` already guarantees `grams != null`, so `g != null` is always true
- **Fix:** Replaced `val g = grams; if (valid && g != null) onApply(g)` with `if (valid) onApply(grams!!)` in both the `keyboardActions` lambda and the Set button `onClick`
- **Files modified:** SpoolScreen.kt
- **Commit:** 994e80d (within Task 2 before commit)

None of the plan tasks required scope changes. Plan executed exactly as written.

## Known Stubs

None — all D-07/D-08 surfaces are fully wired with live numeric IME.

## Threat Flags

None — no new network endpoints or trust-boundary surface introduced. All IME input goes through existing validation gates (MACRO_NUMERIC_RANGE clamp + MacroInvocation.buildTyped sanitizer for macros; `grams > 0.0` gate + SpoolHolder.measureSpool for weight).

## Self-Check: PASSED

- BookmarkedMacrosScreen.kt exists and contains `MacroNumericParamField`: FOUND
- SpoolScreen.kt exists and contains `SpoolMeasureWeightField` + `SpoolMeasureWeightStat`: FOUND
- SpoolHolder.kt exists and contains `FieldMode.MeasureWeight` + `openMeasureWeight`: FOUND
- NumpadPage.kt deleted: CONFIRMED (zero grep hits in app/src/)
- MeasuredWeightPage.kt deleted: CONFIRMED (zero grep hits in app/src/ for import/symbol refs)
- Commits d67b06c (Task 1), 994e80d (Task 2), 809d0b6 (Task 3): all present in git log
