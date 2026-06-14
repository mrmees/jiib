# Files Screen Cleanup — Design

**Date:** 2026-06-13
**Status:** approved
**Scope:** Small UI cleanup on the Files browse screen (`FilesScreen.kt`). No holder /
Moonraker / data-layer changes.

## Goals

Four owner-requested tweaks to the Files screen:

1. Two-line list rows — filename on the first line; date + size on the second.
2. Focus frame title shows the currently-selected file's name (scroll/marquee as needed).
3. Remove the redundant filename from the Focus detail body.
4. Add a sort-by-file-size button (icon `line_weight`) alongside the existing date sort.

All changes live in `FilesScreen.kt` plus a one-line icon registration in `DinghyIcons.kt`,
one new string resource, and matching updates to the preview seam / preview file / tests so
the project still compiles.

## 1. Two-line list rows (`FilesListRow`)

Today the row puts the filename in the main content slot (weight 1f, `maxLines = 2`) and a
right-aligned `trailingContent` Column holding size-over-date. Replace that with a left-aligned
two-line stack in the main content slot and **drop `trailingContent` entirely**:

```
[thumb] filename.gcode
        Jun 10, 14:32          12 MB
```

- **Line 1 — filename:** Geist Mono SemiBold, `fsSp(18f, t.fs)`, `maxLines = 1`,
  `TextOverflow.Ellipsis`. Color `t.accent2` when selected, else `t.text` (unchanged
  selection treatment). One line so it never grows the fixed 1U row (R23).
- **Line 2 — metadata row:** a `Row(Modifier.fillMaxWidth(), horizontalArrangement =
  Arrangement.SpaceBetween)`:
  - **Date, start-aligned:** `formatDate(modifiedEpochSeconds)`, Geist Mono,
    `fsSp(15f, t.fs)`, `t.text2`, `maxLines = 1`.
  - **Size, end-aligned:** `formatBytes(sizeBytes)`, Geist Mono, `fsSp(15f, t.fs)`,
    `t.text2`, `maxLines = 1`.
  - If a field is null, render only the present one (the other side collapses; SpaceBetween
    still anchors it to the correct edge — wrap each in its own slot so a missing date keeps
    size at the end and vice-versa).
- Thumbnail leading slot is unchanged.

Row stays at 1U: two short lines (~18sp + ~15sp) fit inside the ≥64dp unit height with the
existing 1U `heightIn(min = uDp)` floor.

## 2. Focus title = selected file name (`FilesContent` → `FocusFrame`)

Change the `FocusFrame` call:

- `title = state.selectedFile?.name ?: stringResource(R.string.cd_launcher_files)`
  — selected file's name, falling back to the "Files" launcher label when nothing is
  selected (preserves the empty-state title).
- Icon stays `DinghyIcons.LauncherFiles` (already the chosen glyph — icon law satisfied).
- No other change: `FocusFrame` already marquees the title on overflow, so long filenames
  scroll automatically.

## 3. Remove filename from the Focus detail body (`FilesDetailContent`)

Delete the `Text(text = selected.name, …)` block (current lines ~497–506). The title now
carries the name. The future-print stat rows (est time, filament, layers, height, size,
modified) and the empty-state glyph are unchanged. Remove any now-orphaned import / leftover
vertical spacing left by the deleted Text.

## 4. Sort by file size (sort model + icon + SortRow)

### Icon registration (`DinghyIcons.kt`)
Add one entry near the other Browse/Files glyphs and append it to `DinghyIcons.all`:

```kotlin
val LineWeight = DinghyIcon(IconRef.Ligature("line_weight"), alternate = "files_sort_size")
```

Owner-selected glyph — full Material Symbols font is bundled, so it resolves. New ligature,
so no uniqueness-test or `verify_ligatures.py` conflict.

### Sort state model (in the live `FilesScreen` overload + the preview-seam state)
Replace the single `sortAscending: Boolean` with an active-field + per-field-direction model:

```kotlin
enum class FileSortField { Date, Size }
```

State held in the live overload:
- `var sortField by remember { mutableStateOf(FileSortField.Date) }`
- `var dateAscending by remember { mutableStateOf(false) }`  // newest first
- `var sizeAscending by remember { mutableStateOf(false) }`  // largest first

Tap behavior (passed into `SortRow.onSelect`):
- Tap the **inactive** field → make it active (keep its remembered direction; defaults:
  Date = newest-first, Size = largest-first).
- Tap the **active** field → flip that field's direction.

This satisfies the owner decisions: one sort active at a time; Size defaults largest-first.

### Comparator (`sortedRows`)
Branch on the active field:

```kotlin
val sortedRows = when (sortField) {
    FileSortField.Date -> if (dateAscending)
        fileRows.sortedBy { it.modifiedEpochSeconds ?: Double.NEGATIVE_INFINITY }
    else
        fileRows.sortedByDescending { it.modifiedEpochSeconds ?: Double.NEGATIVE_INFINITY }
    FileSortField.Size -> if (sizeAscending)
        fileRows.sortedBy { it.sizeBytes ?: Long.MIN_VALUE }
    else
        fileRows.sortedByDescending { it.sizeBytes ?: Long.MIN_VALUE }
}
```

(Null size/date sinks to the bottom in both directions' "missing" sense — acceptable; files
from Moonraker normally carry both.)

### SortRow wiring (`FilesContent`)
Two `SortOption<FileSortField>` entries:

```kotlin
val sortOptions = persistentListOf(
    SortOption(
        key = FileSortField.Date,
        icon = DinghyIcons.CalendarClock,
        contentDescriptionRes = R.string.cd_files_sort_date,
        directionUp = if (state.sortField == FileSortField.Date) state.dateAscending else null,
    ),
    SortOption(
        key = FileSortField.Size,
        icon = DinghyIcons.LineWeight,
        contentDescriptionRes = R.string.cd_files_sort_size,
        directionUp = if (state.sortField == FileSortField.Size) state.sizeAscending else null,
    ),
)
SortRow(
    options = sortOptions,
    activeKey = state.sortField,
    onSelect = { onSelectSort(it) },   // replaces onToggleSort
    uDp = grid.uDp,
    modifier = …,
)
```

`SortRow` already shows the direction arrow only on the active tile, so the inactive field's
`directionUp = null` is correct.

### New string
`R.string.cd_files_sort_size` — content description e.g. "Sort by file size".

## Preview-seam / test impact (`FilesScreenState`, `FilesPreviews.kt`, tests)

`FilesScreenState.sortAscending: Boolean` is replaced by the new fields:
- `val sortField: FileSortField = FileSortField.Date`
- `val dateAscending: Boolean = false`
- `val sizeAscending: Boolean = false`

The stateless `FilesScreen` overload swaps its `onToggleSort: () -> Unit` callback for
`onSelectSort: (FileSortField) -> Unit`. Update:
- `FilesContent`'s `onToggleSort` param → `onSelectSort: (FileSortField) -> Unit`.
- `FilesPreviews.kt` previews that construct `FilesScreenState` / pass `onToggleSort`.
- Any test referencing `sortAscending` / `onToggleSort` (grep `FilesScreen` test usages,
  `FilesDeleteGateTest` does not touch sort but verify).

## Non-goals / unchanged

- Holder, Moonraker, delete-scoping (D-15), spool warn-gate, FootButtonBar — untouched.
- `formatBytes` / `formatDate` helpers reused as-is.
- No layout/grammar changes beyond the four items above.

## Verification

- Build: `:app:assembleDebug` (Windows-side via `E:\Android\gw.bat`) compiles clean.
- Unit tests: existing Files tests compile and pass after the state-shape rename.
- On-device UAT (flox + moto, both ABIs per [[dinghy-test-devices]]): rows show name over
  date/size; selecting a file puts its name in the Focus title (long names marquee); detail
  body no longer repeats the name; tapping Size re-orders largest-first and shows the arrow,
  re-tap flips, tapping Date switches back.
