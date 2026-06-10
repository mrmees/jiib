---
phase: 25-browse-screens
plan: "03"
subsystem: ui
tags: [files, compose, listrow, spike-verdict, redesign, preview]
dependency_graph:
  requires:
    - phase: 25-browse-screens
      plan: "01"
      provides: "Files=Compose spike verdict (p90 16.42ms PASS on flox)"
    - phase: 25-browse-screens
      plan: "02"
      provides: "Registered DinghyIcon tokens (Print, Delete, Back, CalendarClock, etc.)"
  provides:
    - "Rebuilt FilesScreen on Phase-23 kit (Compose ListBlock/ListRow, spike verdict applied)"
    - "FilesScreenState model for stateless preview seam"
    - "FilesPreviews.kt @Preview matrix (6 theme combos + fs=L + landscape)"
  affects:
    - "AppShell NavDest.Files composable (unchanged — live overload signature preserved)"
    - "25-07 spike teardown (FileListView.kt now unused by FilesScreen; ready for delete)"
tech_stack:
  added: []
  patterns:
    - "Two-overload seam (live + stateless): FilesScreen(holder,...) + FilesScreen(state,...)"
    - "FilesScreenState data class as preview-seam model (mirrors SpoolPickerState precedent)"
    - "D-05 flat-list: filter to FileBrowserRowKind.File only; loadRoot() in LaunchedEffect"
    - "D-06 single CalendarClock sort with direction toggle (SortRow, no filter)"
    - "FloatingEStop wired to isPrinting (Box sibling over DetailCard, TopStart)"
    - "Disabled button via alpha(0.38f) + semantics{disabled()} (OutlinedControl has no enabled param)"
    - "LocalInspectionMode branch → PreviewPlaceholderBox for Coil AsyncImage (D-05 idiom)"
key_files:
  created:
    - app/src/main/java/works/mees/dinghy/preview/FilesPreviews.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt
    - app/src/main/res/values/strings.xml
key_decisions:
  - "Files toolkit: Compose (literal ListBlock/ListRow) — binding spike verdict from 25-01 (p90 16.42ms, ADR-0001 PASS)"
  - "COMPONENTS.md class-equivalent exception NOT added — Compose verdict means no Views fallback needed"
  - "OutlinedControl disabled via alpha+semantics instead of an enabled param (the component lacks one)"
  - "MaterialSymbol(warning) retained verbatim in SpoolWarningGuard (pre-existing glyph, preserved not newly chosen)"
  - "FileListView.kt left unchanged (not deleted in this plan — teardown is 25-07)"
  - "SpoolWarningGuard E-stop guard added (EStop guard enum value) to handle mid-print Files screen"
requirements-completed: []
metrics:
  duration: "~9 min"
  completed: "2026-06-10"
  completed_tasks: 2
  total_tasks: 2
---

# Phase 25 Plan 03: Rebuild FilesScreen on the Phase-23 Kit — Summary

**One-liner:** FilesScreen rebuilt on the jiib kit with Compose ListBlock/ListRow (spike verdict applied), image-backed DetailCard Focus, age-sort SortRow, FootButtonBar, FloatingEStop, all guards preserved verbatim, and the full @Preview matrix shipped.

## Toolkit Verdict Applied

**Files: Compose** — the 25-01 spike returned p90 16.42ms (vs Views baseline 41.95ms), ADR-0001 gate PASS. This plan uses literal `ListBlock`/`ListRow` throughout. No `COMPONENTS.md` class-equivalent exception was added (that was only needed for a Views fallback, which was not required).

## What Was Built

### Task 1 — Rebuild FilesScreen (commit `c259460`)

Rebuilt `FilesScreen.kt` on the Phase-23 component kit:

**Two-overload seam (SpoolScreen pattern):**
- Live: `FilesScreen(holder, printerState, httpBase, canStartPrint, onBack, ...)` — collects `FileBrowserHolder.state`, derives `isPrinting`, wires all guards; delegates to `FilesContent`
- Stateless: `FilesScreen(state: FilesScreenState, ...)` — no holder/dispatcher, drives the @Preview matrix
- `FilesScreenState` data class as the preview-seam model

**ScreenScaffold layout (gutter = null):**
- Focus = `DetailCard` wrapping image-backed thumbnail (ContentScale.Fit, alpha 0.3f) with GeistMono future-print stats overlaid (est time, filament, layers, height, size, modified — never elapsed/status). `FloatingEStop` is a Box sibling (TopStart, printing-only).
- Field = flat `ListBlock`/`ListRow` file list + `SortRow` (CalendarClock, direction toggle) + `FootButtonBar`
- gutter = null

**D-05 flat list:** `directory.rows.filter { it.kind == FileBrowserRowKind.File }` — Up and Directory rows never render. `loadRoot()` in `LaunchedEffect(holder)`.

**D-06 age/date sort:** Single `SortOption(key=Unit, icon=DinghyIcons.CalendarClock)` in a `SortRow`. Re-tap on the same key toggles `sortAscending` (handled in screen state). No filter, no name/size sort.

**D-07 FootButtonBar:** Back (Neutral / DinghyIcons.Back) · Print (Accent / DinghyIcons.Print, startEnabled gate) · Delete (Danger / DinghyIcons.Delete, deleteEnabled gate). Disabled state via `Modifier.alpha(0.38f)` + `semantics { disabled() }` (OutlinedControl has no `enabled` param).

**D-08 guards preserved verbatim:**
- `deleteAllowed(selectedPath, activePrintFilename, printState)` gates the Delete button AND survives in `FileBrowserHolder.requestDeleteSelected`
- `SpoolWarningGuard` with the four D-01 actions (Pick spool / Scan / Print anyway / Back)
- ConfirmGuard for Start (with thumbnail/details) and Delete
- ConfirmGuard for E-stop (new for mid-print Files validity)

**Tokens:** All icons via `DinghyIcons.*` registered tokens; all text via `stringResource` + `fsSp`; colors via `LocalTokens.current`.

**Tests green:** `FilesDeleteGateTest` (4 tests) + `FileBrowserHolderDeleteTest` (all pass).

**Acceptance criteria all met:**
- `ScreenScaffold` count: 4 ✓
- `gutter = null` count: 3 ✓
- `deleteAllowed` count: 1 ✓
- `loadRoot` count: 1 ✓
- `FileBrowserRowKind.File` count: 2 ✓
- No `enterFolder`/`goUp` calls: 0 ✓
- No raw `"print"/"delete"/"arrow_back"` ligatures in FootButtonBar: 0 ✓
- `:app:compileDebugKotlin` GREEN ✓

### Task 2 — @Preview matrix + tokenized strings (commit `222e3ae`)

**FilesPreviews.kt:** 11 `@Nexus7Previews` + 1 explicit landscape `@Preview` = 23 total renders.

Matrix:
- Selection axis (3 variants × 2 orientations via `@Nexus7Previews`): nothing selected / file selected / file selected while printing
- 6-theme matrix on dense file-selected state: colorfulDark/Light, simpleDark/Light, highContrastDark/Light
- fs=L overflow shot (`fsLargeSeed` — NOT `@Preview(fontScale=)` which is a NO-OP)
- Explicit landscape spot-check: `@Preview(widthDp=800, heightDp=480)`

**Fake fixtures:** 5 `FileBrowserRow` entries (kind=File, realistic names/sizes/timestamps) + `FilePreviewMetadata` with est time/filament/layers/height.

**Preview-safe:** `AsyncImage` branches on `LocalInspectionMode.current` → `PreviewPlaceholderBox` (D-05 idiom). No `collectAsStateWithLifecycle` or holder reference.

**strings.xml additions:** 34 new Files-screen strings (`files_foot_*`, `files_stat_*`, `files_confirm_*`, `files_estop_*`, `files_spool_warning_*`, `cd_files_sort_date`).

`:app:assembleDebug` GREEN (SC-4 @Preview compile gate).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] OutlinedControl has no `enabled` parameter**
- **Found during:** Task 1 implementation
- **Issue:** The plan specified `enabled = startEnabled` / `enabled = deleteEnabled` on `OutlinedControl` calls, but the component's API has no `enabled` parameter (the kit was designed without one)
- **Fix:** Applied `Modifier.alpha(if (enabled) 1f else 0.38f)` + `Modifier.semantics { disabled() }` inline on the FootButtonBar controls; guard lambdas skip if disabled
- **Files modified:** `FilesScreen.kt`

**2. [Rule 2 - Missing critical functionality] E-stop ConfirmGuard added**
- **Found during:** Task 1 design review
- **Issue:** Files screen is mid-print-valid (per Phase-24 D-04). The `FloatingEStop` needs to open a ConfirmGuard, but the original FilesScreen lacked an e-stop guard
- **Fix:** Added `FileGuard.EStop` enum value + `ConfirmGuard` for the e-stop path (using `files_estop_guard_*` strings mirroring the spool/printstatus estop guards)
- **Files modified:** `FilesScreen.kt`, `strings.xml`

### Design decisions made during implementation

1. **`MaterialSymbol("warning")` retained in SpoolWarningGuard** — this is a verbatim preservation of the pre-existing glyph from the old `FilesScreen`'s `SpoolWarningGuard`. Not a new icon choice; no `DinghyIcons` token exists for a warning indicator. Recorded as verbatim preservation.

2. **`FileListView.kt` left unchanged** — the file is no longer referenced by `FilesScreen.kt` but is not deleted here. Plan 25-07 (spike teardown) is the designated cleanup plan.

## Known Stubs

None. All visible data fields are wired to `FilesScreenState` (fake fixture in previews, live holder state in production).

## Threat Surface Scan

No new network endpoints, auth paths, or schema changes. The `deleteAllowed` predicate is preserved verbatim; the delete+start commands route through the existing `FileBrowserHolder` dispatch path. T-25-03-01 (deleting active print file mid-print) is fully mitigated by the preserved predicate; `FilesDeleteGateTest` enforces it.

## Self-Check: PASSED

- `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt` — FOUND ✓
- `app/src/main/java/works/mees/dinghy/preview/FilesPreviews.kt` — FOUND ✓
- Commit `c259460` (Task 1) — FOUND ✓
- Commit `222e3ae` (Task 2) — FOUND ✓
- `FilesDeleteGateTest` + `FileBrowserHolderDeleteTest` GREEN ✓
- `:app:assembleDebug` GREEN ✓
- `ScreenScaffold` / `gutter = null` / `deleteAllowed` / `loadRoot` / `FileBrowserRowKind.File` grep gates: ALL PASS ✓
- No `enterFolder`/`goUp` in new FilesScreen: 0 ✓
- No raw ligature strings in FootButtonBar: 0 ✓
