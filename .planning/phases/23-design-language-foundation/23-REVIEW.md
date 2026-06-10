---
phase: 23-design-language-foundation
reviewed: 2026-06-09T00:00:00Z
depth: standard
files_reviewed: 24
files_reviewed_list:
  - app/src/main/java/works/mees/dinghy/designsystem/components/DetailCard.kt
  - app/src/main/java/works/mees/dinghy/designsystem/components/FillMeter.kt
  - app/src/main/java/works/mees/dinghy/designsystem/components/FloatingEStop.kt
  - app/src/main/java/works/mees/dinghy/designsystem/components/FootButtonBar.kt
  - app/src/main/java/works/mees/dinghy/designsystem/components/ListRow.kt
  - app/src/main/java/works/mees/dinghy/designsystem/components/SortFilterControlRow.kt
  - app/src/main/java/works/mees/dinghy/designsystem/control/OutlinedControl.kt
  - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
  - app/src/main/java/works/mees/dinghy/designsystem/layout/ListBlock.kt
  - app/src/main/java/works/mees/dinghy/designsystem/layout/UnitGrid.kt
  - app/src/main/java/works/mees/dinghy/preview/DesignKitComponentPreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/DesignKitLayoutPreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt
  - app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
  - app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt
  - app/src/main/java/works/mees/dinghy/ui/spool/SpoolPicker.kt
  - app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt
  - app/src/test/java/works/mees/dinghy/designsystem/components/FillMeterTest.kt
  - app/src/test/java/works/mees/dinghy/designsystem/components/ListRowTest.kt
  - app/src/test/java/works/mees/dinghy/designsystem/control/OutlinedControlIconTest.kt
  - app/src/test/java/works/mees/dinghy/designsystem/layout/UnitGridTest.kt
  - app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderVendorTest.kt
  - app/src/test/java/works/mees/dinghy/ui/spool/SpoolPickerStateTest.kt
  - tools/verify_ligatures.py
findings:
  critical: 2
  warning: 5
  info: 3
  total: 10
status: issues_found
---

# Phase 23: Code Review Report

**Reviewed:** 2026-06-09T00:00:00Z
**Depth:** standard
**Files Reviewed:** 24
**Status:** issues_found

## Summary

Phase 23 delivers the new component-class kit (ListRow, DetailCard, FillMeter, FootButtonBar, FloatingEStop, SortFilterControlRow), two layout primitives (UnitGrid with the fit-preserving formula, ListBlock edge-faded LazyColumn), an icon→control bridge in OutlinedControl, and the rebuilt SpoolScreen pilot. The core design-system primitives are well-constructed: the UnitGrid formula is correct and comprehensively tested, the fill-convention helpers are correctly extracted and tested, the OutlinedControl icon bridge handles the Drawable vs Ligature cases properly, and the vendor multi-select toggle is regression-tested end-to-end in SpoolHolderVendorTest (the confirmed on-device gap is closed).

Two BLOCKERs require attention before ship: a coroutine leak in SpoolHolder that will compound on each reconnect, and a duplicate-key crash in a preview that will break preview-first development workflow. The five warnings are real quality defects: double-padding in FootButtonBar's documented usage pattern, raw ligature strings bypassing the icon registry in two places, hardcoded UI strings missing stringResource() routing, dead production composables left in SpoolPicker, and a stale entry in the ligature gate.

---

## Critical Issues

### CR-01: SpoolHolder init coroutine leaks on reconnect (WR-01 gap)

**File:** `app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt:204`

**Issue:** `SpoolHolder.init{}` fires a `scope.launch { activeSpool.collect { … } }` that runs for the lifetime of the passed `scope`. In `AppShell`, `scope` is `rememberCoroutineScope()` — a composition-scoped coroutine scope that lives as long as AppShell is composed (i.e., the entire app session). When `remember(store) { SpoolHolder(…) }` re-keys on a spine rebuild (reconnect or printer switch), a NEW SpoolHolder is created with the SAME live `scope`. The OLD holder's `init` collector is never cancelled because:
1. SpoolHolder has no `cancel()` method.
2. AppShell has no `DisposableEffect(spoolHolder) { onDispose { … } }` for it.

Every reconnect leaks one additional `activeSpool.collect` coroutine, each writing stale `_activeSpoolDetail.value`. This is the exact WR-01 pattern documented and fixed for `ConsoleHolder` (line 424) and `MacroHolder` (line 464) — SpoolHolder was missed.

**Fix:** Add a `cancel()` method to SpoolHolder mirroring ConsoleHolder's pattern, and add a `DisposableEffect` in AppShell:

```kotlin
// SpoolHolder.kt — add a Job-bound child scope and cancel() method
private val holderJob = SupervisorJob()
private val holderScope = CoroutineScope(scope.coroutineContext + holderJob)

init {
    holderScope.launch {        // use holderScope, not scope directly
        activeSpool.collect { status -> … }
    }
}

fun cancel() { holderJob.cancel() }
```

```kotlin
// AppShell.kt — add after spoolHolder definition (line ~309)
DisposableEffect(spoolHolder) { onDispose { spoolHolder.cancel() } }
```

---

### CR-02: Duplicate LazyColumn key crash in preview — blocks preview-first workflow

**File:** `app/src/main/java/works/mees/dinghy/preview/DesignKitLayoutPreviews.kt:102`

**Issue:** The `ListBlock` demo uses `key = { it + rowLabels.indexOf(it) }` to generate keys. `rowLabels` intentionally contains duplicates (row_1 appears at indices 0 and 5; row_2 at 1 and 6; row_3 at 2 and 7). `indexOf()` always returns the FIRST occurrence, so both the index-0 and index-5 instances of `layout_preview_row_1` produce the same key (`"row_1" + "0"`). Compose's `LazyColumn` with `key =` enforces uniqueness at runtime and throws `IllegalStateException: Two items with the same key...` when duplicate keys are detected. This crashes every preview in `DesignKitLayoutPreviews.kt` — all 10+ preview functions fail to render. Since this phase is built on preview-first development (PREVIEW_AND_TOKENS.md), a broken preview matrix defeats the primary SC-2 evidence gate.

**Fix:** Use `items` index as the key — indices are always unique:

```kotlin
items(rowLabels.size) { idx ->
    UnitRow(label = rowLabels[idx], uDp = grid.uDp)
}
```

Or use `itemsIndexed`:
```kotlin
itemsIndexed(rowLabels) { idx, label ->
    UnitRow(label = label, uDp = grid.uDp)
}
```

---

## Warnings

### WR-01: FootButtonBar double-padding — documented usage pattern applies padding twice

**File:** `app/src/main/java/works/mees/dinghy/designsystem/components/FootButtonBar.kt:76`

**Issue:** `FootButtonBar` applies `.padding(horizontal = 8.dp, vertical = 4.dp)` internally on its Row modifier (line 76). The KDoc usage example (line 34) AND every real call site in `SpoolScreen.kt` (lines 501, 645) additionally pass `modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)`. Since caller modifier is applied before internal modifier in Compose's modifier chain, the effective horizontal padding becomes 8 + 8 = 16dp per side (32dp total), and vertical 4 + 4 = 8dp — double what either site intends. The visual result is extra-tight button content on small screens.

**Fix:** Remove the internal padding from FootButtonBar and let callers own all spacing:

```kotlin
Row(
    modifier = modifier
        .fillMaxWidth()
        .heightIn(min = uDp),
    // Remove: .padding(horizontal = 8.dp, vertical = 4.dp)
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
)
```

If internal padding is desired as a convenience default, document it explicitly and remove it from the KDoc example and all call sites.

---

### WR-02: Raw ligature strings `"close"` and `"check"` bypass icon registry in SpoolScreen

**File:** `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt:652,659`

**Issue:** The filter picker footer's Clear and Done buttons use `symbol = "close"` and `symbol = "check"` (raw string ligature paths). This violates the icon law ([[dinghy-never-pick-icons-ask]]) and the Phase 23 icon-control bridge (OutlinedControl's DinghyIcon overload) that exists precisely to eliminate raw ligature strings from redesign components.

`"close"` is not present in `verify_ligatures.py`'s `NEEDED` set — it is not font-validated, so it may render as tofu (empty box) if the bundled v2.944 font happens not to carry it (the gate was never run on it). `"check"` IS registered as `DinghyIcons.Check` but is not being routed through that token.

**Fix:**
```kotlin
// Replace:
symbol = "close"
// With:
icon = DinghyIcons.Back  // or the appropriate registered close/dismiss glyph
// (if "close" is needed, request owner assignment and add to DinghyIcons + NEEDED set)

// Replace:
symbol = "check"
// With:
icon = DinghyIcons.Check
```

---

### WR-03: Three hardcoded UI strings not routed through `stringResource()`

**Files:**
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt:608` — `"No manufacturers found."`
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt:897` — `"Loaded"`
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt:905` — `"Archived"`

**Issue:** Three visible UI strings are hardcoded English literals. The project convention (docs/ui_design/PREVIEW_AND_TOKENS.md, `tokenized-first` rule) requires all user-visible strings to go through `stringResource()`. These strings will fail pseudolocale checks, break RTL spot checks where expansion causes overflow, and block future i18n. `SpoolPicker.kt` has the same "Loaded" and "Archived" literals at lines 406/408.

**Fix:** Add `R.string.spool_mfg_empty`, `R.string.spool_badge_loaded`, `R.string.spool_badge_archived` resource entries (or reuse the existing `R.string.spool_badge_loaded` / `spool_badge_archived` if already defined for `SpoolDetailContent`) and replace with `stringResource(R.string.…)` at each call site.

---

### WR-04: `SpoolPicker` and `SpoolFilterControls` are dead production composables

**File:** `app/src/main/java/works/mees/dinghy/ui/spool/SpoolPicker.kt:89,134`

**Issue:** `SpoolPicker()` and `SpoolFilterControls()` are public `@Composable` functions that were superseded by `SpoolListField` and `SpoolFilterPickerField` inside `SpoolScreen.kt` in the Phase 23 rebuild. Neither is imported anywhere in production code. They compile, grow the APK, and may confuse future maintainers into using the old path. `SpoolPicker()` also contains a bare `LazyColumn` (line 110) without the `ListBlock` wrapper — exactly the pattern the new grammar was designed to replace. Leaving dead code with the old pattern adjacent to the new pattern is a maintenance hazard.

`ColorSwatchGrid` (line 224) IS still used by SpoolScreen and should be retained.

**Fix:** Delete `SpoolPicker()` and `SpoolFilterControls()` from `SpoolPicker.kt`. Run a build to confirm no consumers.

---

### WR-05: `verify_ligatures.py` NEEDED set contains stale `"database"` entry

**File:** `tools/verify_ligatures.py:68`

**Issue:** `"database"` remains in the `NEEDED` set despite being retired in Phase 18.3 (the `LauncherSpool` entry was flipped from `IconRef.Ligature("database")` to `IconRef.Drawable(R.drawable.spool)`). The comment in `DinghyIcons.kt` at line 53-57 documents this retirement. The stale entry means the gate is checking a glyph no longer used, and the gate's "these are all the ligatures the app needs" documentation claim is false. While the font almost certainly still carries `"database"`, the gate is no longer an accurate audit of production requirements. If the font is ever updated and `"database"` is dropped, the gate will spuriously fail.

**Fix:** Remove `"database"` from `NEEDED` in `verify_ligatures.py`.

---

## Info

### IN-01: `SpoolSortKey.icon` and `SpoolFilterCategory.icon` are unregistered raw `String` fields

**File:** `app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt:34` and `app/src/main/java/works/mees/dinghy/ui/spool/SpoolPicker.kt:70`

**Issue:** Both `SpoolSortKey` and `SpoolFilterCategory` store their glyph as a raw `String` ligature name (`val icon: String`) rather than a `DinghyIcon` token. These fields are used via `symbol = key.icon` / `symbol = category.icon` at `SpoolPicker.kt:150,196`. This predates the Phase 23 icon-control bridge and is now inconsistent with the DinghyIcon-aware `OutlinedControl` overload that the new component kit introduced. The specific ligatures happen to be correct (they match the DinghyIcons registry entries: `MatchCase`, `CalendarClock`, `Scale`, `Experiment`, `Palette`, `Storefront`), so no tofu risk currently. SpoolPicker.kt is heading toward deletion (WR-04), but SpoolHolder.kt's `SpoolSortKey` enum is a data model type that may persist.

**Fix:** Change `val icon: String` to `val icon: DinghyIcon` in both enums (aligns them with the new registry-first discipline) and update the usage sites to pass `icon = key.icon` instead of `symbol = key.icon`. This is a one-phase migration; if SpoolPicker.kt is deleted first, only `SpoolSortKey` in SpoolHolder.kt needs updating.

---

### IN-02: `SPOOL_LIMIT` constant defined but never referenced — dead constant

**File:** `app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt:480`

**Issue:** `private const val SPOOL_LIMIT = 50` is defined in the companion object but never used. The literal `50` is hardcoded directly in `buildSpoolQuery` at line 529 (`parts += "limit=50"`) instead. The constant exists in the companion but the body ignores it, so any future change to the page size requires updating two places.

**Fix:** Replace the literal with the constant: `parts += "limit=$SPOOL_LIMIT"`.

---

### IN-03: `OutlinedControl` background token change note — SortRow/FilterRow type-tile interaction confirmed safe

**File:** `app/src/main/java/works/mees/dinghy/designsystem/control/OutlinedControl.kt:109`

**Issue:** The phase context flagged the `.background(t.surface)` addition to OutlinedControl as potentially interacting with the recessed type-tile in SortRow/FilterRow. This was checked: the type tile is a plain `Box` with `Modifier.background(t.bg2)` — it is NOT an `OutlinedControl` and does NOT use the `OutlinedControl` composable. The `t.bg2` background on the type tile is set directly in SortFilterControlRow.kt and is unaffected by the OutlinedControl change. No interaction issue exists. This is an informational confirmation that the concern is resolved.

**No fix needed.**

---

_Reviewed: 2026-06-09_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
