---
phase: 26
plan: 05
subsystem: ui/outputs
tags: [detail-in-focus, list-detail, screen-collapse, outputs, scrubber, led, pin, cleanup]
dependency_graph:
  requires: [26-01, 26-02]
  provides: [outputs-detail-in-focus]
  affects: [AppShell, OutputsScreen, OutputFocusControl, ScrubberPage]
tech_stack:
  added: []
  patterns:
    - Detail-in-Focus two-overload stateless seam (SpoolScreen precedent)
    - ScrubberControl embeddable core extracted from ScrubberPage
    - LedBrightnessControl migrated to OutputFocusControl (self-contained)
key_files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/outputs/OutputFocusControl.kt
  modified:
    - app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt
    - app/src/main/java/works/mees/dinghy/ui/outputs/OutputsScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/outputs/OutputToggleControl.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/java/works/mees/dinghy/preview/OutputsPreviews.kt
    - app/src/main/res/values/strings.xml
  deleted:
    - app/src/main/java/works/mees/dinghy/ui/outputs/OutputScrubberDetail.kt
    - app/src/main/java/works/mees/dinghy/ui/outputs/OutputPinDetail.kt
    - app/src/main/java/works/mees/dinghy/ui/outputs/OutputLedDetail.kt
    - app/src/main/java/works/mees/dinghy/preview/OutputDetailPreviews.kt
decisions:
  - LedBrightnessControl migrated into OutputFocusControl (private) rather than a shared designsystem component — it is LED-output-specific logic with no other callers
  - OutputDetailPreviews.kt deleted alongside the three detail pages — the stateless content seams (OutputScrubberContent, OutputLedContent) it drove no longer exist; preview coverage now in OutputsPreviews.kt's selected-state variant
  - Task 2+3 compiled together (AppShell still referenced old OutputsScreen(holder, onRowTap, onBack) while Task 2 was in progress) — committed separately but build verified after both were complete
metrics:
  duration: ~35min (Tasks 1-3 combined across sessions)
  completed: 2026-06-10
  task_count: 3
  file_count: 10
---

# Phase 26 Plan 05: Outputs Detail-in-Focus Collapse Summary

Collapsed the three Outputs detail pages (OutputScrubberDetail, OutputPinDetail, OutputLedDetail) into a single `OutputsScreen` using the Detail-in-Focus pattern: list in Field, per-output inline control in Focus via the new `OutputFocusControl`, no separate nav destinations.

## Tasks Completed

| Task | Description | Commit |
|------|-------------|--------|
| 1 | Extract ScrubberControl + create OutputFocusControl per-type inline surface | 543c8ce |
| 2 | Rebuild OutputsScreen as two-overload Detail-in-Focus seam + update previews | cce7c78 |
| 3 | AppShell route surgery + delete 3 detail pages + clean up stale references | c02b529 |

## What Was Built

**ScrubberControl (extracted from ScrubberPage.kt):** New embeddable composable that is the gesture+state core of the scrubber — fill-bar + steppers, no ScreenScaffold, no background. `working` state seeded via `remember(value, range)` (P19 build-once rule). `ScrubberPage` becomes a thin wrapper that delegates to `ScrubberControl` and adds only its own foot buttons.

**OutputFocusControl:** Per-output-type inline control surface hosted inside `DetailCard` in the Outputs Focus region. Routes by `descriptor.family`:
- `fan_generic` / `pwm_tool` / `output_pin(pwm)` → `FocusScrubberSurface` (0..100%)
- `servo` → `FocusScrubberSurface` (0..servoAngleMax°)
- `heater_generic` → `FocusScrubberSurface` (0..MAX_TEMP_C°C)
- LED families → `FocusLedSurface` (brightness + GAP-B gated ColorWheel)
- `output_pin(digital)` → `OutputToggleControl` (On/Off)
All dispatch routes through `PrinterCommands.clamp*`/`outputPctToWire` before `markPending` (P17 invariant). `LedBrightnessControl` migrated from deleted `OutputLedDetail.kt` into this file.

**OutputsScreen (rebuilt):** Two-overload stateless seam:
- Live overload: `OutputsScreen(holder, container, onBack)` — collects rows, holds `selectedKey` internally
- Stateless overload: `OutputsScreen(rows, selectedKey, onSelect, onBack)` — drives `@Preview` matrix
`ScreenScaffold` with `focus=DetailCard(OutputFocusControl) | empty-state prompt`, `field=ListBlock+FootButtonBar`, `gutter=null`. glyphFor() preserved (D-01..D-06 icon law).

**AppShell simplified:** `composable<NavDest.Outputs>` reduced to a single `OutputsScreen(holder, container, onBack)` call. Removed ~80 lines of selection state, LaunchedEffect reset, and BackHandler that are now internal to OutputsScreen.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] LedBrightnessControl missing after OutputLedDetail deletion**
- **Found during:** Task 3 build verification
- **Issue:** `OutputFocusControl.kt` referenced `LedBrightnessControl` which was defined in `OutputLedDetail.kt` (deleted in Task 3). Build error: "Unresolved reference 'LedBrightnessControl'"
- **Fix:** Migrated `LedBrightnessControl` (private) from the deleted `OutputLedDetail.kt` into `OutputFocusControl.kt` with all required gesture-handling imports. The function is LED-output-specific with no other callers; no separate file warranted.
- **Files modified:** `OutputFocusControl.kt`
- **Commit:** c02b529

**2. [Rule 3 - Blocking] OutputDetailPreviews.kt referenced deleted stateless seams**
- **Found during:** Task 3 file deletion
- **Issue:** `OutputDetailPreviews.kt` imported `OutputScrubberContent`, `OutputLedContent`, `OutputScrubberType` — all were internal to the deleted detail pages. Would have broken build.
- **Fix:** Deleted `OutputDetailPreviews.kt`. Preview coverage for the outputs flow is now in `OutputsPreviews.kt` (selected-state variant drives the Focus path).
- **Files deleted:** `OutputDetailPreviews.kt`
- **Commit:** c02b529

**3. [Rule 1 - Bug] Stale KDoc in OutputToggleControl.kt**
- **Found during:** Task 3 reference grep
- **Issue:** KDoc referenced `[OutputPinDetail]` as the live-wiring host (deleted).
- **Fix:** Updated to `[OutputFocusControl]`.
- **Files modified:** `OutputToggleControl.kt`
- **Commit:** c02b529

**4. [Rule 3 - Blocking] DinghyIcons.ArrowBack → DinghyIcons.Back**
- **Found during:** Task 2 first build attempt
- **Issue:** `OutputsScreen.kt` used `DinghyIcons.ArrowBack` (doesn't exist); correct name is `DinghyIcons.Back`.
- **Fix:** Corrected the icon reference.
- **Files modified:** `OutputsScreen.kt`
- **Commit:** cce7c78

## Known Stubs

None — all outputs routing is complete and wired to live dispatch.

## Threat Flags

None — no new network endpoints, auth paths, or schema changes introduced.

## Self-Check: PASSED

- OutputsScreen.kt exists: FOUND
- OutputFocusControl.kt exists: FOUND
- ScrubberPage.kt (modified) exists: FOUND
- OutputScrubberDetail.kt: DELETED (intentional)
- OutputPinDetail.kt: DELETED (intentional)
- OutputLedDetail.kt: DELETED (intentional)
- Commits exist: 543c8ce / cce7c78 / c02b529 — FOUND
- assembleDebug: BUILD SUCCESSFUL
- testDebugUnitTest: BUILD SUCCESSFUL
