---
phase: 23-design-language-foundation
fixed_at: 2026-06-09T00:00:00Z
review_path: .planning/phases/23-design-language-foundation/23-REVIEW.md
iteration: 1
findings_in_scope: 7
fixed: 7
skipped: 0
status: all_fixed
---

# Phase 23: Code Review Fix Report

**Fixed at:** 2026-06-09
**Source review:** `.planning/phases/23-design-language-foundation/23-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 7 (CR-01, CR-02, WR-01, WR-02 Done-button only, WR-03, WR-04, WR-05)
- Fixed: 7
- Skipped: 0

Final build gate: `:app:compileDebugKotlin` GREEN + `:app:testDebugUnitTest` GREEN (34 tasks).

---

## Fixed Issues

### CR-01: SpoolHolder coroutine leak on reconnect

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt`, `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt`
**Commit:** `9a7125c`
**Applied fix:** Added `Job` and `SupervisorJob` imports to SpoolHolder. Created `holderJob = SupervisorJob()` and `holderScope = CoroutineScope(scope.coroutineContext + holderJob)` fields. Changed `init {}` to use `holderScope.launch` instead of `scope.launch`, and added `scope.coroutineContext[Job]?.invokeOnCompletion { holderJob.cancel() }` at the top of `init`. Added a `cancel()` method that calls `holderJob.cancel()`. In AppShell, added `DisposableEffect(spoolHolder) { onDispose { spoolHolder.cancel() } }` immediately after the `spoolHolder = remember(store) { … }` line. Mirrors the ConsoleHolder/MacroHolder pattern exactly.

---

### CR-02: Duplicate LazyColumn key crash in preview

**Files modified:** `app/src/main/java/works/mees/dinghy/preview/DesignKitLayoutPreviews.kt`
**Commit:** `b31a2fd`
**Applied fix:** Replaced `items(rowLabels, key = { it + rowLabels.indexOf(it) }) { label -> … }` with `items(rowLabels.size) { idx -> UnitRow(label = rowLabels[idx], uDp = grid.uDp) }`. The index-based form is always unique even when the list contains duplicate strings. Also removed the now-unused `import androidx.compose.foundation.lazy.items` (the count-based `items(Int)` overload is a method on `LazyListScope`, not an extension requiring import).

---

### WR-01: FootButtonBar double-padding

**Files modified:** `app/src/main/java/works/mees/dinghy/designsystem/components/FootButtonBar.kt`
**Commit:** `ad575f7` + `e333199` (dp import restore)
**Applied fix:** Removed `.padding(horizontal = 8.dp, vertical = 4.dp)` from the Row's internal modifier chain. Callers (SpoolScreen call sites + the KDoc example) already supply `Modifier.padding(horizontal = 8.dp, vertical = 4.dp)` — the internal padding was applying it twice. Also removed the now-unused `padding` import. Note: an initial edit also accidentally removed the `dp` import (needed for `Arrangement.spacedBy(8.dp)`); the dp import was restored in a follow-up commit (`e333199`) caught by the compile check.

---

### WR-02: Done button routed through DinghyIcons.Check (partial)

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt`
**Commit:** `0f8ca67`
**Applied fix:** In `SpoolFilterPickerField`, changed the Done button from `symbol = "check"` to `icon = DinghyIcons.Check`, routing it through the OutlinedControl DinghyIcon overload and the icon registry.

**Intentionally left unchanged — Clear button `symbol = "close"`:**
The Clear button's `symbol = "close"` is intentionally left as-is, with a `// TODO(23-rev WR-02): close glyph awaiting owner icon assignment` comment added. Reason: `"close"` is not registered in `DinghyIcons`, is not in the `verify_ligatures.py` NEEDED set (font-unvalidated), and the project's hard owner rule (`[[dinghy-never-pick-icons-ask]]`) forbids Claude from choosing or substituting any icon glyph independently. The owner must assign the appropriate close/dismiss glyph and register it before this can be routed through the registry.

---

### WR-03: Hardcoded UI strings routed through stringResource()

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt`, `app/src/main/res/values/strings.xml`
**Commit:** `6cda3da`
**Applied fix:** Added three new string resource entries to `strings.xml` under a `23-rev WR-03` comment:
- `spool_mfg_empty` = "No manufacturers found."
- `spool_row_badge_loaded` = "Loaded"
- `spool_row_badge_archived` = "Archived"

(These are distinct from the existing `spool_badge_loaded`/`spool_badge_archived` which are verbose accessibility-description strings, not the short row-level badges.)

Replaced the three hardcoded literals in `SpoolScreen.kt` with `stringResource(R.string.spool_mfg_empty)`, `stringResource(R.string.spool_row_badge_loaded)`, and `stringResource(R.string.spool_row_badge_archived)`. The same literals in `SpoolPicker.kt`'s `SpoolRow` function were in the dead `SpoolPicker()` composable deleted by WR-04, so no separate fix was needed there.

---

### WR-04: Dead SpoolPicker() and SpoolFilterControls() composables deleted

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/spool/SpoolPicker.kt`
**Commit:** `5b10ad2`
**Applied fix:** Deleted the following dead composables and their private helpers (confirmed no production or test references):
- `SpoolPicker()` — superseded by `SpoolListField` in SpoolScreen.kt
- `SpoolFilterControls()` — superseded by `SpoolFilterPickerField` in SpoolScreen.kt
- `CategoryButton()` — private, used only by SpoolFilterControls
- `SpoolRow()` — private, used only by SpoolPicker
- `RowSwatch()` — private, used only by SpoolRow

Also cleaned up 8 imports that became unused after the deletions (`size`, `LazyColumn`, `items`, `roundToInt`, `MaterialSymbol`, `Intent`, `OutlinedControl`, `SpoolmanSpool`, `GeistMono`). `ColorSwatchGrid` (still used by SpoolScreen) was retained, along with its private helpers (`ColorSwatchCircle`, `ColorTile`, `ColorChoice`, `MULTICOLOR_BRUSH`) and the `PALETTE_SWATCHES` constant.

---

### WR-05: Stale "database" entry removed from verify_ligatures.py

**Files modified:** `tools/verify_ligatures.py`
**Commit:** `f784e7c`
**Applied fix:** Removed `"database"` from the `NEEDED` set. It was retired in Phase 18.3 when `LauncherSpool` was flipped from `IconRef.Ligature("database")` to `IconRef.Drawable(R.drawable.spool)`, documented in `DinghyIcons.kt` lines 53–57. The gate now accurately reflects production ligature requirements.

---

## Skipped Issues

None — all in-scope findings were applied.

---

_Fixed: 2026-06-09_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
