---
phase: 25-browse-screens
plan: 05
subsystem: macros
tags: [macros, field-mode, list-rows, sanitizer, security, preview]
depends_on: ["25-02"]
provides: ["merged-macros-screen", "macros-preview-matrix", "buildTyped-test-coverage"]
affects: ["app-shell-macros-nav", "macros-ui-surface"]
tech_stack:
  added: []
  patterns: ["MacroFieldMode sealed class (Launcher/ParamEntry/ManageMode)", "FieldMode pattern (mirrors SpoolScreen)", "two-overload seam (live + stateless)", "buildTyped sanitizer on typed param path"]
key_files:
  created:
    - app/src/main/java/works/mees/dinghy/preview/MacrosPreviews.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/test/java/works/mees/dinghy/command/MacroInvocationTest.kt
    - app/src/main/res/values/strings.xml
  deleted:
    - app/src/main/java/works/mees/dinghy/ui/macros/SystemMacrosScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/macros/MacroExecutionPopup.kt
decisions:
  - "MacroFieldMode sealed class (Launcher/ParamEntry/ManageMode) mirrors SpoolScreen FieldMode"
  - "Legacy @Deprecated bridge overload added in Task 1 for compile safety, removed in Task 2 (same pattern as any refactoring involving 2-step deletion)"
  - "Visibility/VisibilityOff icons used as raw symbol strings (verbatim from old SystemMacrosScreen — no new icon choice; pre-registry glyphs, D-21 gate respected)"
  - "paramsKnown gate: null dispatcher treated as loaded in stateless preview overload (preview safety)"
metrics:
  completed_date: "2026-06-10"
---

# Phase 25 Plan 05: Merge Macros to One FieldMode Screen Summary

**One-liner:** Three Macros surfaces (BookmarkedMacrosScreen + SystemMacrosScreen + MacroExecutionPopup) collapsed into one `MacroFieldMode`-driven FieldMode screen with ListRow launcher, ManageMode, and per-param NumpadPage/TokenTextField Field-takeover, preserving V5 sanitizer + WR-03 gate.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Merge Macros to one FieldMode screen + buildTyped coverage | `89742bf` | BookmarkedMacrosScreen.kt (rewritten), MacroInvocationTest.kt (+2 buildTyped cases), strings.xml (+12 keys) |
| 2 | Rewire AppShell + delete absorbed files + @Preview matrix | `283c938` | AppShell.kt (macro routes collapsed), MacrosPreviews.kt (created), SystemMacrosScreen.kt (deleted), MacroExecutionPopup.kt (deleted) |

## What Was Built

### Merged Macros Screen (BookmarkedMacrosScreen.kt)

The 363-line `MacroExecutionPopup.kt` + the separate `SystemMacrosScreen.kt` + the old grid-based `BookmarkedMacrosScreen.kt` were replaced by a single screen with three in-screen `MacroFieldMode` states:

**`MacroFieldMode.Launcher` (D-10):**
- `ListBlock` of `ListRow`s over `state.bookmarkedMacros` — never a `LazyVerticalGrid` or `MacroTile`
- Tap → `MacroFieldMode.ParamEntry(macro)` Field-takeover
- Foot: Back (Neutral) + Manage (Neutral, `DinghyIcons.ManageMacros` → ManageMode)

**`MacroFieldMode.ParamEntry(macro)` (D-12):**
- Full MacroExecutionPopup logic migrated; WR-03 live re-resolve from holder by name each recomposition
- `paramsKnown(name)` cold-connect gate: Execute disabled until body loaded
- Numeric params → tappable `ListRow` opens `NumpadPage` Field-takeover (keyboard-free)
- String params → `TokenTextField` (the ONE sanctioned alpha keyboard, D-12)
- Execute: `MacroInvocation.buildTyped(...)` → `MacroParamRejected` caught → toast; on success: `dispatcher.dispatch(key="macro_${name}")` (PRIM-05)
- `DispatchEvent.Failure` collected per busy key → toast
- Disabled Execute: `alpha(0.38f) + semantics { disabled() }` (no `enabled` param on OutlinedControl — FilesScreen 25-03 convention)

**`MacroFieldMode.ManageMode` (D-09):**
- `ListBlock` over `state.visibleMacros`; trailing `DinghyIconView(CheckCircle/UnbookmarkedMacro)`
- Show-hidden toggle foot button (raw `symbol="visibility"/"visibility_off"` — verbatim pre-registry from old SystemMacrosScreen)

**Two-overload seam:**
- Live: `BookmarkedMacrosScreen(holder, dispatcher, onToggleBookmark, onSetRevealHidden, onBack)` — collects from holder, owns `fieldMode` state
- Stateless: `BookmarkedMacrosScreen(state, fieldMode, onFieldModeChange, ...)` — preview/test seam, no holder, no network

### MacroInvocationTest.kt (V5 / D-12 / T-25-05-01)

Added two `buildTyped` regression cases to close the typed-path gap (the old map overload `build(...)` was covered; `buildTyped(...)` — the path the redesigned ParamEntry uses — was not):

- `buildTyped_forbiddenCharInStringParam_rejected`: newline injection → `assertThrows(MacroParamRejected)`
- `buildTyped_cleanMixedParams_producesCorrectGcodeLine`: string + numeric → `LOAD_FILAMENT MATERIAL="PLA" TEMP=210`

### MacrosPreviews.kt (D-20)

`@Preview` matrix (6 theme combos + fs=L + pseudolocale + explicit ParamEntry landscape) driving the stateless `BookmarkedMacrosScreen(state, fieldMode)` overload in all three modes. No live Moonraker — pure fixture state.

### AppShell.kt Changes

- Removed `import ... SystemMacrosScreen` and `import ... MacroExecutionPopup`
- Removed `macroShowSystem` / `macroPopupFor` local aliases and their BackHandler blocks
- `composable<NavDest.Macros>` now calls the single merged `BookmarkedMacrosScreen(holder, dispatcher, onToggleBookmark, onSetRevealHidden, onBack)` — no popup overlay, no second route
- PROMPT-protocol overlay (`PromptDialog`) untouched — D-13 preserved

## Acceptance Criteria Results

| Criterion | Result |
|-----------|--------|
| `MacroInvocationTest` passes with buildTyped coverage (≥2 buildTyped cases) | PASS — 2 new cases |
| `MacroFieldMode` present in BookmarkedMacrosScreen.kt | PASS |
| `MacroInvocation.buildTyped` + `paramsKnown` in BookmarkedMacrosScreen.kt | PASS |
| No `LazyVerticalGrid` or `MacroTile` in non-comment code | PASS |
| `gutter = null` in BookmarkedMacrosScreen.kt | PASS |
| Execute dispatch uses `"macro_"` key (PRIM-05) | PASS |
| SystemMacrosScreen.kt + MacroExecutionPopup.kt deleted in same commit as AppShell rewire | PASS — commit `283c938` |
| `:app:assembleDebug` clean | PASS — BUILD SUCCESSFUL |
| `MacrosPreviews.kt` with ≥6 `@Preview` + ≥1 landscape, stateless overload, no holder/collect | PASS |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing functionality] @Deprecated legacy bridge overload for Task 1 compile safety**
- **Found during:** Task 1 — AppShell used old `BookmarkedMacrosScreen(holder, onRunMacro, onManage, onBack)` signature; new merged screen changed the API
- **Issue:** Task 1 required "DO NOT delete old files" for compile safety, but also changed the BookmarkedMacrosScreen signature — AppShell would fail to compile on the Task-1 commit
- **Fix:** Added a `@Deprecated` legacy compatibility bridge overload in Task 1 that delegated to `MacrosContent`; removed it in Task 2 after AppShell was rewired
- **Files modified:** BookmarkedMacrosScreen.kt (Task 1 add, Task 2 remove)

**2. [Rule 1 - Bug] `Visibility`/`VisibilityOff` DinghyIcons tokens don't exist**
- **Found during:** Task 1 — tried to use `DinghyIcons.Visibility`/`DinghyIcons.VisibilityOff` for the show-hidden toggle
- **Issue:** Registry only has tokens per the D-21 gate (owners curate — no independent icon choices); these weren't registered
- **Fix:** Used raw `symbol = "visibility"` / `symbol = "visibility_off"` strings — verbatim preservation from old SystemMacrosScreen (same precedent as `MaterialSymbol("warning")` in FilesScreen 25-03; not a new icon choice, just the pre-existing pre-registry code)
- **Files modified:** BookmarkedMacrosScreen.kt

**3. [Rule 3 - Blocking] `DinghyIconView` parameter name: `sizeSp` → `sizeDp`**
- **Found during:** Task 1 — used `sizeSp = fsSp(24f, t.fs)` but `DinghyIconView` signature has `sizeDp: Dp`
- **Fix:** Changed to `sizeDp = fsSp(24f, t.fs).dp`
- **Files modified:** BookmarkedMacrosScreen.kt

## Known Stubs

None — all data paths are wired to `MacroHolder.state` / `CommandDispatcher`. The stateless preview overload uses fixture data but is preview-only and not a product stub.

## Threat Flags

None — no new network endpoints or auth paths. The `MacroInvocation.buildTyped` V5 sanitizer (T-25-05-01) and `paramsKnown` cold-connect gate (T-25-05-02) are both confirmed present on the dispatch path.

## Self-Check: PASSED

- `BookmarkedMacrosScreen.kt` exists: FOUND
- `MacrosPreviews.kt` exists: FOUND
- `SystemMacrosScreen.kt` deleted: CONFIRMED
- `MacroExecutionPopup.kt` deleted: CONFIRMED
- Commit `89742bf` exists: CONFIRMED
- Commit `283c938` exists: CONFIRMED
