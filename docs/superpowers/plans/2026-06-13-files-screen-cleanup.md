# Files Screen Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Four owner-requested cleanups on the Files browse screen — two-line list rows
(name over date/size), a Focus title that shows the selected file's name, removal of the
redundant name from the Focus detail body, and a new sort-by-file-size control.

**Architecture:** All UI lives in `FilesScreen.kt` (Compose). The sort model is mirrored from
the established `SpoolScreen`/`SpoolHolder` precedent: a `FileSortField` enum carrying a
per-field `defaultAscending`, plus a pure `sortFileRows()` helper extracted for host testing.
One new icon registry entry (`LineWeight`, owner-selected `line_weight`) and one new string.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit4 host tests. Windows-side Gradle via
`E:\Android\gw.bat` (see [[dinghy-display-build-env]]). Build command pattern:
`/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>"` piped through `tr -d '\r'`.

---

## File Structure

- `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` — add `LineWeight`
  token + append to `all`.
- `tools/verify_ligatures.py` — add `line_weight` to the `NEEDED` set (subset/source-of-truth
  conformance).
- `app/src/main/res/values/strings.xml` — add `cd_files_sort_size`.
- `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt` — the bulk: `FileSortField`
  enum + `sortFileRows()` helper, `FilesScreenState` shape change, both `FilesScreen`
  overloads, `FilesContent` (SortRow wiring), `FilesDetailContent` (remove name), `FocusFrame`
  (dynamic title), `FilesListRow` (two-line layout).
- `app/src/main/java/works/mees/dinghy/preview/FilesPreviews.kt` — update `FilesScreenState`
  construction sites.
- `app/src/test/java/works/mees/dinghy/ui/files/FilesSortTest.kt` — NEW host test for the
  pure sort helper.

---

## Task 1: Register the `line_weight` sort-by-size icon

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` (after the
  `Print`/`Delete` Files-action block, ~line 210; and in `all` ~line 303)
- Modify: `tools/verify_ligatures.py:100` (the Phase-23 sort glyph line in `NEEDED`)
- Test: `app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt` (existing —
  enforces alternate + rendered-IconRef uniqueness; no edit, just run)

- [ ] **Step 1: Add the registry token**

In `DinghyIcons.kt`, in the Browse/Files block near `Print`/`Delete`, add:

```kotlin
    // Files sort-by-size (owner-selected `line_weight`, 2026-06-13). Full Material Symbols font
    // bundled so it resolves; new ligature, no uniqueness conflict.
    val LineWeight = DinghyIcon(IconRef.Ligature("line_weight"), alternate = "files_sort_size")
```

- [ ] **Step 2: Append to `all`**

In the `all` list, on the `Print, Delete, …` line, add `LineWeight`:

```kotlin
        Print, Delete, LineWeight, HideTemps, HideTimelapse, HidePrompts,
```

- [ ] **Step 3: Add to verify_ligatures NEEDED**

In `tools/verify_ligatures.py`, on the sort-glyph line (currently
`"match_case", "calendar_clock", "experiment", "home", "qr_code",`) append `"line_weight"`:

```python
    "match_case", "calendar_clock", "experiment", "home", "qr_code", "line_weight",
```

- [ ] **Step 4: Run the icon registry test**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests *DinghyIconsTest --no-daemon" | tr -d '\r'`
Expected: PASS (uniqueness of alternate `files_sort_size` and rendered IconRef holds).

- [ ] **Step 5: Run the ligature gate**

Run: `python tools/verify_ligatures.py`
Expected: exit 0, `missing: []` (the bundled font contains `line_weight`).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt tools/verify_ligatures.py
git commit -m "feat(files): register line_weight icon for sort-by-size"
```

---

## Task 2: Add the sort-by-size content-description string

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (next to the existing `cd_files_sort_date`)

- [ ] **Step 1: Add the string**

Find `cd_files_sort_date` in `strings.xml` and add directly after it:

```xml
    <string name="cd_files_sort_size">Sort by file size</string>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(files): add cd_files_sort_size string"
```

---

## Task 3: Pure sort model — `FileSortField` enum + `sortFileRows()` helper (TDD)

Extract the sort logic into a pure, host-testable top-level function, mirroring the
`SpoolSortKey`/`listRowUsesAccentFill` precedent (pure helpers asserted without a Compose runtime).

**Files:**
- Create: `app/src/test/java/works/mees/dinghy/ui/files/FilesSortTest.kt`
- Modify: `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt` (add enum + helper near
  the bottom util section, ~line 880)

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/works/mees/dinghy/ui/files/FilesSortTest.kt`:

```kotlin
package works.mees.dinghy.ui.files

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.dinghy.state.FileBrowserRow
import works.mees.dinghy.state.FileBrowserRowKind

private fun fileRow(name: String, size: Long?, modified: Double?): FileBrowserRow =
    FileBrowserRow(
        kind = FileBrowserRowKind.File,
        name = name,
        stableId = name,
        relativeFilename = name,
        sizeBytes = size,
        modifiedEpochSeconds = modified,
    )

class FilesSortTest {
    private val a = fileRow("a.gcode", size = 100, modified = 10.0)
    private val b = fileRow("b.gcode", size = 300, modified = 30.0)
    private val c = fileRow("c.gcode", size = 200, modified = 20.0)
    private val rows = listOf(a, b, c)

    @Test
    fun `date descending is newest first`() {
        val out = sortFileRows(rows, FileSortField.Date, ascending = false)
        assertEquals(listOf(b, c, a), out)
    }

    @Test
    fun `date ascending is oldest first`() {
        val out = sortFileRows(rows, FileSortField.Date, ascending = true)
        assertEquals(listOf(a, c, b), out)
    }

    @Test
    fun `size descending is largest first`() {
        val out = sortFileRows(rows, FileSortField.Size, ascending = false)
        assertEquals(listOf(b, c, a), out)
    }

    @Test
    fun `size ascending is smallest first`() {
        val out = sortFileRows(rows, FileSortField.Size, ascending = true)
        assertEquals(listOf(a, c, b), out)
    }

    @Test
    fun `null size sinks to bottom when descending`() {
        val nullSize = fileRow("z.gcode", size = null, modified = 5.0)
        val out = sortFileRows(rows + nullSize, FileSortField.Size, ascending = false)
        assertEquals(nullSize, out.last())
    }

    @Test
    fun `Size default direction is largest first`() {
        assertEquals(false, FileSortField.Size.defaultAscending)
    }

    @Test
    fun `Date default direction is newest first`() {
        assertEquals(false, FileSortField.Date.defaultAscending)
    }
}
```

> `FileBrowserRow` (in `app/src/main/java/works/mees/dinghy/state/FileBrowserModels.kt`)
> requires `kind`, `name`, `stableId`; everything else defaults to null — the helper above
> matches it.

- [ ] **Step 2: Run the test to verify it fails**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests *FilesSortTest --no-daemon" | tr -d '\r'`
Expected: FAIL / compile error — `FileSortField` and `sortFileRows` do not exist yet.

- [ ] **Step 3: Add the enum + helper**

In `FilesScreen.kt`, in the utility-helpers section near the bottom (after `formatDuration`),
add:

```kotlin
/**
 * The two sort dimensions on the Files screen. One is active at a time (SpoolScreen precedent).
 * [defaultAscending] is the direction applied when the field is freshly selected; re-tapping the
 * active field flips it. Date defaults newest-first; Size defaults largest-first (owner, 2026-06-13).
 */
enum class FileSortField(val defaultAscending: Boolean) {
    Date(defaultAscending = false),
    Size(defaultAscending = false),
}

/**
 * Pure, host-testable sort over the flat file list. Null sort keys sink to the bottom regardless
 * of direction (NEGATIVE_INFINITY / MIN_VALUE), matching the prior date-sort behavior.
 */
internal fun sortFileRows(
    rows: List<FileBrowserRow>,
    field: FileSortField,
    ascending: Boolean,
): List<FileBrowserRow> = when (field) {
    FileSortField.Date ->
        if (ascending) rows.sortedBy { it.modifiedEpochSeconds ?: Double.NEGATIVE_INFINITY }
        else rows.sortedByDescending { it.modifiedEpochSeconds ?: Double.NEGATIVE_INFINITY }
    FileSortField.Size ->
        if (ascending) rows.sortedBy { it.sizeBytes ?: Long.MIN_VALUE }
        else rows.sortedByDescending { it.sizeBytes ?: Long.MIN_VALUE }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests *FilesSortTest --no-daemon" | tr -d '\r'`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt app/src/test/java/works/mees/dinghy/ui/files/FilesSortTest.kt
git commit -m "feat(files): pure FileSortField + sortFileRows helper (date + size)"
```

---

## Task 4: Wire the sort model into the screen state + SortRow

Replace the single `sortAscending: Boolean` with `(sortField, sortAscending)` everywhere, and
give `SortRow` two options. This touches `FilesScreenState`, both `FilesScreen` overloads,
`FilesContent`, and `FilesPreviews.kt` — they must all change together to compile.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt`
- Modify: `app/src/main/java/works/mees/dinghy/preview/FilesPreviews.kt`

- [ ] **Step 1: Update `FilesScreenState`**

Replace the `sortAscending` field (and its KDoc `@param sortAscending` line) with:

```kotlin
    val sortField: FileSortField = FileSortField.Date,
    val sortAscending: Boolean = false,
```

(Keep `sortAscending` as the direction of the *active* field; add `sortField`. Update the
KDoc: `@param sortField the active sort dimension; @param sortAscending direction of the active field.`)

- [ ] **Step 2: Update the live `FilesScreen` overload state + comparator**

Replace `var sortAscending by remember { mutableStateOf(false) }` (line ~148) with:

```kotlin
    var sortField by remember { mutableStateOf(FileSortField.Date) }
    var sortAscending by remember { mutableStateOf(FileSortField.Date.defaultAscending) }
```

Replace the inline `sortedRows` block (lines ~199-204) with:

```kotlin
    // D-06: one active sort field at a time (date | size); direction toggles on re-tap.
    val sortedRows = sortFileRows(fileRows, sortField, sortAscending)
```

In the `FilesScreenState(...)` construction (line ~210), add `sortField = sortField,` alongside
`sortAscending = sortAscending,`.

Replace the `onToggleSort = { sortAscending = !sortAscending }` lambda (line ~233) passed to
`FilesContent` with an `onSelectSort`:

```kotlin
            onSelectSort = { field ->
                if (field == sortField) {
                    sortAscending = !sortAscending
                } else {
                    sortField = field
                    sortAscending = field.defaultAscending
                }
            },
```

- [ ] **Step 3: Update the stateless `FilesScreen` overload**

Change its callback param `onToggleSort: () -> Unit = {}` to
`onSelectSort: (FileSortField) -> Unit = {}` and pass it through to `FilesContent` as
`onSelectSort = onSelectSort`.

- [ ] **Step 4: Update `FilesContent`**

Change the param `onToggleSort: () -> Unit` to `onSelectSort: (FileSortField) -> Unit`.

Replace the single-option `sortOptions` (lines ~364-371) with two options:

```kotlin
        // D-06 sort: date (CalendarClock) + size (LineWeight). One active at a time; the active
        // tile shows its direction arrow (SortRow renders the arrow only on the active key).
        val sortOptions = persistentListOf(
            SortOption(
                key = FileSortField.Date,
                icon = DinghyIcons.CalendarClock,
                contentDescriptionRes = R.string.cd_files_sort_date,
                directionUp = if (state.sortField == FileSortField.Date) state.sortAscending else null,
            ),
            SortOption(
                key = FileSortField.Size,
                icon = DinghyIcons.LineWeight,
                contentDescriptionRes = R.string.cd_files_sort_size,
                directionUp = if (state.sortField == FileSortField.Size) state.sortAscending else null,
            ),
        )
```

Replace the `SortRow(...)` call's `activeKey` and `onSelect`:

```kotlin
                SortRow(
                    options = sortOptions,
                    activeKey = state.sortField,
                    onSelect = { onSelectSort(it) },
                    uDp = grid.uDp,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                )
```

- [ ] **Step 5: Update `FilesPreviews.kt`**

At both `FilesScreenState(` construction sites (lines ~103, ~112), the `sortAscending = false,`
line stays valid; add nothing required (sortField defaults to Date). If either preview passed
`onToggleSort`, rename to `onSelectSort`. (Grep the file for `onToggleSort` — none expected, but
confirm.)

- [ ] **Step 6: Build to verify the whole state-shape change compiles**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL (no unresolved `onToggleSort` / `sortAscending` references).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt app/src/main/java/works/mees/dinghy/preview/FilesPreviews.kt
git commit -m "feat(files): two-field sort (date + size) wired into SortRow"
```

---

## Task 5: Two-line list rows (name over date/size)

Restructure `FilesListRow` so the main content slot holds a two-line stack and the
`trailingContent` slot is removed. Line 2 is a full-width row with **date start-aligned, size
end-aligned**.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt` (`FilesListRow`, lines ~656-734)

- [ ] **Step 1: Replace the `ListRow` trailing + content with a two-line column**

Replace the `trailingContent = { … }` block and the final `content` lambda (the whole
`trailingContent`/`content` portion of the `ListRow(...)` call) with NO `trailingContent` and a
two-line column as `content`:

```kotlin
    ListRow(
        selected = selected,
        onClick = { onRowClick(row) },
        uDp = uDp,
        leadingContent = {
            if (thumbUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(thumbUrl)
                        .size(FileThumbnailLoader.ROW_THUMBNAIL_SIZE_PX, FileThumbnailLoader.ROW_THUMBNAIL_SIZE_PX)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(fsSp(40f, t.fs).dp)
                        .padding(end = 8.dp),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Line 1 — filename. One line, ellipsized, so the row never grows past 1U (R23).
            Text(
                text = row.name,
                color = if (selected) t.accent2 else t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(18f, t.fs).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            // Line 2 — date (start) ... size (end). SpaceBetween anchors each to its edge even
            // when one is null (the absent slot collapses but the present one keeps its edge).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.modifiedEpochSeconds?.let { formatDate(it) }.orEmpty(),
                    color = t.text2,
                    fontFamily = GeistMono,
                    fontSize = fsSp(15f, t.fs).sp, // metadata floor 15sp ([[dinghy-font-sizes-too-small]])
                    maxLines = 1,
                )
                Text(
                    text = row.sizeBytes?.let { formatBytes(it) }.orEmpty(),
                    color = t.text2,
                    fontFamily = GeistMono,
                    fontSize = fsSp(15f, t.fs).sp,
                    maxLines = 1,
                )
            }
        }
    }
```

> The `content` lambda is a `RowScope`, so `Modifier.weight(1f)` on the Column is valid (the
> `ListRow` body is a `RowScope` between leading and trailing slots).

- [ ] **Step 2: Prune now-unused imports if the compiler flags them**

`Alignment.End` (was used by the removed trailing Column) may now be unused — only remove if the
build warns; `Arrangement` and `Alignment` are still used elsewhere in the file.

- [ ] **Step 3: Build**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt
git commit -m "feat(files): two-line list rows — name over date(start)/size(end)"
```

---

## Task 6: Dynamic Focus title + remove name from detail body

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt` (`FilesContent`
  `FocusFrame` title ~line 375; `FilesDetailContent` name Text ~lines 497-506)

- [ ] **Step 1: Make the Focus title the selected file name**

In `FilesContent`, change the `FocusFrame` `title` argument:

```kotlin
                FocusFrame(
                    title = state.selectedFile?.name ?: stringResource(R.string.cd_launcher_files),
                    icon = DinghyIcons.LauncherFiles,
                    uDp = grid.uDp,
```

(Icon stays `LauncherFiles`. `FocusFrame` marquees long titles automatically, satisfying
"scroll as necessary".)

- [ ] **Step 2: Remove the filename from the detail body**

In `FilesDetailContent`, delete the filename `Text` block (the
`Text(text = selected.name, … modifier = Modifier.fillMaxWidth())` call, current lines ~497-506).
Leave the `Column` and its `verticalArrangement = Arrangement.spacedBy(4.dp)` and all stat rows
intact — the first child is now the est-time stat row.

- [ ] **Step 3: Build**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL. (If `FontWeight`/`TextOverflow` become unused, remove only if warned.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt
git commit -m "feat(files): focus title shows selected file name; drop name from detail body"
```

---

## Task 7: Full verification + on-device UAT

**Files:** none (verification only).

- [ ] **Step 1: Full unit-test suite**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL — `FilesSortTest`, `DinghyIconsTest`, `FilesDeleteGateTest` all pass.

- [ ] **Step 2: Assemble both ABIs**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" | tr -d '\r'`
Expected: BUILD SUCCESSFUL. Confirm the APK mtime is newer than the last commit
([[dinghy-stale-apk-uat-gate]] — verify before installing).

- [ ] **Step 3: Install to BOTH test devices**

Per [[dinghy-test-devices]], push the matching ABI slice to flox (`0a64b42e`) and moto
(`ZY22LBDRM9`). Use the split-ABI debug APKs from the assemble output.

- [ ] **Step 4: Owner UAT checklist (Matthew drives — [[dinghy-display-ondevice-iteration]])**

  - List rows show **filename on line 1**, **date (left) + size (right)** on line 2.
  - Selecting a file puts its **name in the Focus title**; a long name **marquees**.
  - The Focus **detail body no longer repeats the name** (stats only).
  - The sort row shows **two tiles**: date (calendar) + size (`line_weight`).
  - Tapping **Size** re-orders **largest-first** and shows the up/down arrow on the size tile;
    re-tap flips to smallest-first.
  - Tapping **Date** switches back to date sort (newest-first) and moves the arrow to the date tile.

- [ ] **Step 5: Final commit (if any UAT tweaks were needed)**

```bash
git add -A
git commit -m "polish(files): on-device UAT adjustments"
```

---

## Self-Review Notes

- **Spec coverage:** (1) two-line rows → Task 5; (2) dynamic Focus title → Task 6 Step 1;
  (3) remove name from detail → Task 6 Step 2; (4) sort by size → Tasks 1–4. All four covered.
- **Deviation from spec:** spec sketched per-field-remembered direction (`dateAscending` +
  `sizeAscending`); this plan uses the simpler SpoolScreen model (single `sortAscending` + reset
  to `field.defaultAscending` on field switch). Behavior matches owner's stated rules; only
  difference is switching back to a field resets to its default direction rather than recalling
  the prior one — acceptable and consistent with the rest of the app.
- **Type consistency:** `FileSortField` (Date/Size) and `sortFileRows(rows, field, ascending)`
  signatures are identical across Tasks 3, 4, and the test.
- **Line-2 null handling:** `SpaceBetween` + `.orEmpty()` keeps date at start / size at end even
  when one is missing (empty string still occupies its slot edge).
