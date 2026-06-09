---
phase: 23-design-language-foundation
plan: "06"
subsystem: ui/spool
tags: [redesign, spoolman, pilot, field-takeover, unit-grid, floating-estop, fill-meter]
dependency_graph:
  requires: ["23-02", "23-03", "23-04", "23-05"]
  provides: ["SpoolScreen on kit (pilot)", "FieldMode sealed class", "5 new icon tokens"]
  affects: ["SpoolHolder.kt", "SpoolPicker.kt", "AppShell.kt", "DinghyIcons.kt"]
tech_stack:
  added:
    - "FieldMode sealed class (Spools / FilterPicker) in SpoolHolder.kt"
    - "5 new DinghyIcon tokens: MatchCase, CalendarClock, Experiment, Home, QrCode"
  patterns:
    - "ColumnScope receiver extension functions for composables needing weight() modifier"
    - "Stateless preview seam with isPrinting: Boolean for FloatingEStop preview exercise"
    - "LIVE printerState wiring via container.printerState (LastJobHolder convention)"
key_files:
  created:
    - "app/src/test/java/works/mees/dinghy/ui/spool/SpoolPickerStateTest.kt"
  modified:
    - "app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt"
    - "app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt"
    - "app/src/main/java/works/mees/dinghy/ui/spool/SpoolPicker.kt"
    - "app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt"
    - "app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt"
    - "app/src/main/res/values/strings.xml"
    - "app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt"
    - "app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt"
    - "tools/verify_ligatures.py"
decisions:
  - "ColumnScope receiver extension on SpoolListField/SpoolFilterPickerField (not modifier parameter) to give .weight() access without adding new callback surface"
  - "ColorSwatchGrid promoted from private to internal (same-package SpoolPicker.kt→SpoolScreen.kt call)"
  - "OptionButton removed (unused after SpoolFilterPickerOverlay deletion)"
  - "Home foot button navigates to Dest.PrintStatus (the hub screen) in AppShell.kt"
  - "5 new icons (MatchCase/CalendarClock/Experiment/Home/QrCode) registered from img/material-icon-bucket.json — all owner-curated, bundled font verified"
metrics:
  duration: "~50 minutes (Tasks 1-2)"
  completed_date: "2026-06-09"
  tasks_completed: 2
  tasks_total: 3
  files_changed: 9
---

# Phase 23 Plan 06: SpoolScreen Pilot on Kit Summary

**One-liner:** SpoolScreen rebuilt on the jiib design-kit (FieldMode in-place filter, DetailCard+FillMeter in Focus, FootButtonBar with locked Load/Unload, FloatingEStop wired to live printerState, U from rememberUnitGrid) — host tests GREEN, debug APK installed on flox.

## Status

**Tasks 1 and 2 complete; paused at Task 3 (on-device owner approval checkpoint).**

Tasks 1 and 2 committed and verified. Debug APK installed on flox (Nexus 7 / LineageOS / Adreno 320). Task 3 requires owner eyeball on the real hardware in both portrait and landscape.

## What Was Built

### Task 1: SpoolScreen + SpoolPicker + AppShell + DinghyIcons

**FieldMode state machine** — Added `sealed class FieldMode { data object Spools; data class FilterPicker(category) }` to SpoolHolder.kt; `SpoolPickerState.fieldMode` field (default `Spools`); `SpoolHolder.openFilterPicker(category)` and `closeFilterPicker()`.

**SpoolScreen full rebuild** on the new component kit:
- `ScreenScaffold(focus={…}, field={…}, gutter=null)` — gutter nulled; FootButtonBar lives inside `field`
- Focus = `DetailCard(ringColor=filamentColor) { SpoolDetailContent + FillMeter }` with `FloatingEStop` Box-sibling overlay (TopStart, printing-only, wired to LIVE `container.printerState`)
- SortRow + FilterRow pinned at Focus foot (icon-only, no group-label words)
- Field = `when(fieldMode)`: `Spools` → `ListBlock + FootButtonBar`; `FilterPicker` → in-place TYPE/COLOR/MFG option list + Clear/Done FootButtonBar
- `BoxWithConstraints { val grid = rememberUnitGrid(minOf(maxWidth, maxHeight)) }` at the screen root; U passed down to all components
- DEBUG-only uDp log (`Log.d("UnitGrid", "uDp=…")`) + corner badge guarded by `BuildConfig.DEBUG`

**Foot buttons LOCKED:** Home (`DinghyIcons.Home`, accent) · Scan (`DinghyIcons.QrCode`, accent) · conditional Load (`DinghyIcons.ExpandCircleUp`, accent) when NOT loaded / Unload (`DinghyIcons.ExpandCircleDown`, neutral) when loaded. No Set-Active button.

**FloatingEStop** wired to LIVE state: `container.printerState.collectAsStateWithLifecycle()` → `isPrinting = printState == PrintState.Printing || PrintState.Paused`. Tap → ConfirmGuard → `dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit)`. No new CommandSpec added.

**Callback renames:** `onSetActive` → `onLoad`, `onClearActive` → `onUnload`.

**Stateless preview seam preserved:** `SpoolScreen(state: SpoolPickerState, …, isPrinting: Boolean = false)` for @Preview matrix.

**SpoolFilterPickerOverlay removed** from SpoolPicker.kt (replaced by in-place FieldMode; ColorSwatchGrid promoted internal, OptionButton removed as unused).

**AppShell.kt** updated: `onBack` → `onHome = { navigateTo(Dest.PrintStatus) }`, `container = container` added.

**DinghyIcons.kt** — 5 new owner-sanctioned tokens registered (from `img/material-icon-bucket.json`): `MatchCase`, `CalendarClock`, `Experiment`, `Home`, `QrCode`. Added to `all` list. `tools/verify_ligatures.py` NEEDED set updated (74 total), font gate exits 0.

**SpoolPickerStateTest.kt** — 8 host-side tests covering FieldMode state machine (default, copy transitions, category distinctness, Spools singleton) and SpoolPickerState defaults.

### Task 2: SpoolPreviews + SampleFixtures

- `SampleFixtures.spoolWithFilterOpen` fixture added (`FieldMode.FilterPicker(TYPE)` with first spool selected)
- `SpoolSelectionProvider` updated: 3 entries (no-selection, first-selected, filter-open)
- `SpoolFilterPickerOpen` preview: Field-takeover TYPE picker state (colorfulDark)
- `SpoolPrintingState` preview: `isPrinting=true` — FloatingEStop visible in Focus (colorfulDark)
- Matrix shape preserved: 6 theme wrappers + fs=L + RTL + pseudolocale + 2 new 23-06 states

## Verification

- `:app:testDebugUnitTest --tests works.mees.dinghy.ui.spool.*` — **GREEN** (Task 1)
- `verify_ligatures.py` — **74 needed, 0 missing** (font gate, Task 1 + chore)
- DinghyIconsTest drift guard — **GREEN** (new icons in `all` list)
- FULL `:app:testDebugUnitTest :app:assembleDebug` — **BUILD SUCCESSFUL** (Task 2)
- `installDebug` on flox — **Installed on 1 device** (Nexus 7 / LineageOS 18.1 / API 30)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Missing `import works.mees.dinghy.command.dispatch` extension**
- **Found during:** Task 1 build
- **Issue:** `dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit)` resolved to the base `dispatch(String, String, JsonElement?)` overload — "Argument type mismatch: actual type is 'CommandSpec<Unit>', but 'String' was expected"
- **Fix:** Added `import works.mees.dinghy.command.dispatch` (the extension in CommandDispatchExtensions.kt)
- **Files modified:** SpoolScreen.kt
- **Commit:** 733d868

**2. [Rule 1 - Bug] `Modifier.weight()` unavailable in standalone composable functions**
- **Found during:** Task 1 build
- **Issue:** 7 compile errors — `SpoolListField`, `SpoolFilterPickerField`, and `SpoolRowBody` used `.weight(1f)` but had no `ColumnScope`/`RowScope` receiver; weight is a scoped modifier extension
- **Fix:** Made `SpoolListField` and `SpoolFilterPickerField` `ColumnScope` receiver extensions (they're always called from the `field = ColumnScope.()->Unit` lambda); made `SpoolRowBody` a `RowScope` receiver (always called from `ListRow { ... }` which is `RowScope.()->Unit`)
- **Files modified:** SpoolScreen.kt
- **Commit:** 733d868

**3. [Rule 1 - Bug] `originalWeight` smart cast impossible on open getter property**
- **Found during:** Task 1 build
- **Issue:** `SpoolmanSpool.originalWeight` and `remainingWeight` have custom getters — Kotlin can't smart-cast after a null check on a re-readable property; "Smart cast to 'Double' is impossible"
- **Fix:** Captured to locals (`val remaining = spool.remainingWeight; val original = spool.originalWeight`) before the conditional
- **Files modified:** SpoolScreen.kt
- **Commit:** 733d868

**4. [Rule 1 - Bug] Invalid import for ListBlock — `components` vs `layout` package**
- **Found during:** Pre-build code review
- **Issue:** SpoolScreen.kt had `import works.mees.dinghy.designsystem.components.ListBlock` (package doesn't exist) and the correct `import works.mees.dinghy.designsystem.layout.ListBlock as DesignListBlock`
- **Fix:** Removed the invalid `components.ListBlock` import
- **Files modified:** SpoolScreen.kt
- **Commit:** 733d868

**5. [Rule 2 - Missing functionality] ColorSwatchGrid was private — cross-file access**
- **Found during:** Pre-build code review
- **Issue:** `ColorSwatchGrid` in SpoolPicker.kt was `private` (file-private in Kotlin); SpoolScreen.kt calls it for the COLOR filter picker field
- **Fix:** Promoted `ColorSwatchGrid` from `private` to `internal` (same package)
- **Files modified:** SpoolPicker.kt
- **Commit:** 733d868

**6. [Rule 2 - Missing functionality] 5 missing DinghyIcon tokens (MatchCase, CalendarClock, Experiment, Home, QrCode)**
- **Found during:** Pre-build code review
- **Issue:** SpoolScreen.kt referenced `DinghyIcons.MatchCase`, `.CalendarClock`, `.Experiment`, `.Home`, `.QrCode` which didn't exist in the registry; all 5 are in `img/material-icon-bucket.json` (owner-curated) and in the bundled v2.944 font
- **Fix:** Registered all 5 in DinghyIcons.kt (ligature-backed), added to `all` list, updated verify_ligatures.py NEEDED set
- **Files modified:** DinghyIcons.kt, tools/verify_ligatures.py
- **Commit:** 733d868, 361f034

## Known Stubs

None — SpoolScreen renders live Spoolman data through the existing SpoolHolder wire; no new hardcoded placeholders introduced.

## Threat Flags

None — pure presentation refactor of an existing screen over existing trusted-LAN Spoolman data; the active-spool command path (setActiveSpool/clearActiveSpool/emergencyStop) is unchanged (T-23-06-02 accepted, T-23-06-01 mitigated: color parsed to numeric Color, names rendered as plain Compose Text).

## Awaiting: Task 3 (On-Device Owner Approval)

Debug APK is installed on flox. Owner needs to verify in BOTH portrait and landscape:

1. Fill convention visible (translucent list rows / filled controls)
2. Smooth scroll + edge-fade (list scrolls UNDER the fade)
3. DetailCard color ring + FillMeter on spool tap
4. Field-takeover filter picker (in-place, no full-screen overlay)
5. Sort direction indicator on active tile
6. Foot buttons: Home · Scan · conditional Load/Unload
7. U constant through rotation (debug uDp readout EQUAL portrait vs landscape via corner badge or `adb logcat -s UnitGrid`)
8. FloatingEStop printing-only (absent when idle, present when Printing/Paused, raises red ConfirmGuard on tap)

## Self-Check: PASSED

Tasks 1 and 2 verified above. Files exist, commits verified in git log.

- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt` — FOUND
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt` — FOUND (FieldMode)
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolPicker.kt` — FOUND (overlay removed)
- `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` — FOUND (5 new tokens)
- `app/src/test/java/works/mees/dinghy/ui/spool/SpoolPickerStateTest.kt` — FOUND
- `app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt` — FOUND (new previews)
- `app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt` — FOUND (spoolWithFilterOpen)
- Commit 733d868 — FOUND
- Commit 8bedac5 — FOUND
- Commit 361f034 — FOUND
