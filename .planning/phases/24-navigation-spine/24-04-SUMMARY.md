---
phase: 24-navigation-spine
plan: "04"
subsystem: ui
tags: [print-status, waterfall-home, morphing-root, idle-list, foot-button-bar, crossfade, home-action, nav-dest]
dependency_graph:
  requires:
    - phase: 24-01
      provides: [HomeAction, buildIdleActions, NavDest]
    - phase: 24-02
      provides: [LauncherWebcam, FootPreheat, FootSystem glyph tokens + strings]
    - phase: 24-03
      provides: [NavHost shell, hoisted FloatingEStop/ConfirmGuard (FIX-1), onNavigate seam]
  provides:
    - "Morphing waterfall root: Crossfade idle/printing/terminal + data-driven idle list + neutral foot bar"
    - "FIX-5 resolved: buildIdleActions Webcam row wired to DinghyIcons.LauncherWebcam"
    - "FIX-1 confirmed: in-screen FloatingEStop + showEstopGuard removed from PrintStatusScreen"
  affects: [PrintStatusScreen, PrintStatusField, HomeAction, AppShell, 24-05 on-device gate]
tech_stack:
  added: []
  patterns:
    - "Crossfade(targetState = mode, tween(150), label = PrintStatusMorph) wraps the when(mode) dispatch (D-13)"
    - "PrintStatusStandbyField rebuilt as BoxWithConstraints + rememberUnitGrid + ListBlock/ListRow + FootButtonBar"
    - "idleActions: List<HomeAction> built in PrintStatusScreen, passed through PrintStatusContent to PrintStatusStandbyField"
    - "Capability flags (outputsPresent, webcamTileEnabled) collected in PrintStatusScreen, fed to buildIdleActions"
    - "ScreenScaffold(gutter = null) for Standby mode — foot actions live in idle FootButtonBar (SC-3)"
key_files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
key-decisions:
  - "FIX-5: buildIdleActions Webcam row swapped from PLACEHOLDER(LauncherDrawer+cd_launcher_drawer) to owner-confirmed DinghyIcons.LauncherWebcam+cd_launcher_webcam"
  - "FIX-1: showEstopGuard state + in-screen ConfirmGuard removed from PrintStatusScreen; AppShell overlay layer (24-03) owns FloatingEStop+guard on every destination while printing"
  - "SC-3: Standby ScreenScaffold passes gutter=null; foot actions (Preheat+System) live at the foot of the idle ListBlock inside PrintStatusStandbyField"
  - "idleActions built once per capability-flag change via remember() in PrintStatusScreen, threaded through PrintStatusContent to PrintStatusStandbyField — avoids rebuilding on every recomposition"
  - "Crossfade wraps the when(mode) dispatch in PrintStatusContent, not in each ScreenScaffold call — single animation scope for the whole root surface (D-13)"
  - "LauncherGrid function retained as dead code (no callers in standby path); ShortcutRow still uses LauncherTile directly — clean structural boundary"
  - "outputsPresent + webcamTileEnabled collected from container in the live PrintStatusScreen overload; stateless preview overload defaults both to false (safe empty-list fallback)"
requirements-completed: [SC-1, SC-3, SC-4]
duration: ~45 minutes
completed: "2026-06-10"
---

# Phase 24 Plan 04: Morphing Waterfall Root Summary

**PrintStatusScreen reshaped into the jiib redesign home root: one-shot 150ms Crossfade idle/printing/terminal morph, data-driven buildIdleActions idle list with D-08 capability hides, neutral Preheat/System FootButtonBar, and FIX-1/FIX-5 closeout (in-screen e-stop removed, Webcam icon wired)**

## Performance

- **Duration:** ~45 minutes
- **Started:** 2026-06-10T04:45:00Z
- **Completed:** 2026-06-10T05:30:00Z
- **Tasks:** 2 (combined in one commit — both touched PrintStatusScreen.kt atomically)
- **Files modified:** 3

## Accomplishments

- **FIX-5 resolved:** `buildIdleActions` Webcam row swapped from the Wave-0 placeholder (`DinghyIcons.LauncherDrawer` + `cd_launcher_drawer`) to the owner-confirmed `DinghyIcons.LauncherWebcam` + `R.string.cd_launcher_webcam`. `HomeActionTest` 12/12 GREEN with the real icon.
- **FIX-1 confirmed:** `showEstopGuard` state + its in-screen `ConfirmGuard` block removed from `PrintStatusScreen`. The `FloatingEStop` + Stop Confirm guard live at the AppShell overlay layer (24-03), rendering on every destination while printing per D-14.
- **Morphing root surface (SC-1/D-13):** `PrintStatusContent` wraps the `when(mode)` dispatch in `Crossfade(targetState = mode, tween(150), label = "PrintStatusMorph")` — one-shot transition, no continuous/looping animation.
- **Data-driven idle list (D-05/D-06/D-08):** `PrintStatusStandbyField` rebuilt around `ListBlock`/`ListRow` over `buildIdleActions` output; `BoxWithConstraints` + `rememberUnitGrid` provides the unit grid `U` for touch-target sizing; capability-absent rows are absent, never greyed (D-08 HIDE).
- **Neutral foot bar (D-09/D-10, SC-3):** Two `Intent.Neutral` `OutlinedControl`s — Preheat (`DinghyIcons.FootPreheat`, wired to `runPreheat`) + System (`DinghyIcons.FootSystem`, opens the App Drawer). No gutter in the Standby mode (`gutter = null`).
- **Capability gating:** `outputsPresent` and `webcamTileEnabled` collected in the live `PrintStatusScreen` overload and fed to `buildIdleActions`; both default to `false` in the stateless preview overload.

## Task Commits

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1+2 | Idle list + foot bar + FIX-5 + FIX-1 + Crossfade | ec0caf7 | HomeAction.kt, PrintStatusField.kt, PrintStatusScreen.kt |

(Tasks 1 and 2 both touched `PrintStatusScreen.kt`; committed together atomically per the overlap.)

## Files Created/Modified

- `app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt` — FIX-5: replace PLACEHOLDER Webcam row with owner-confirmed `DinghyIcons.LauncherWebcam` + `cd_launcher_webcam`; update KDoc
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt` — Replace `PrintStatusStandbyField` (old `LauncherGrid` tile-grid) with `BoxWithConstraints`+`ListBlock`/`ListRow`+`FootButtonBar` idle-action list; add imports for Phase-23 design-system components
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt` — Add `outputsPresent`+`webcamTileEnabled` collection; build `idleActions` via `buildIdleActions()`; remove `showEstopGuard` state + its ConfirmGuard block (FIX-1); wrap `when(mode)` in Crossfade (D-13); thread `onPreheat` + `idleActions` through `PrintStatusContent` to `PrintStatusStandbyField`; set `gutter=null` for Standby (SC-3)

## Decisions Made

- **FIX-1 scope:** The `PrintStatusControlAction.EmergencyStop` case was kept in `runAction` as a `Unit` no-op rather than removed, to keep the `when()` exhaustive. The AppShell FloatingEStop fires the actual dispatch; the in-screen path is intentionally inert.
- **Crossfade placement:** Wraps `when(mode)` inside `PrintStatusContent`, not each `ScreenScaffold` call — one animation scope covering the entire root surface (D-13 intent).
- **`LauncherGrid` dead code:** Retained without removal. It has no callers in the Standby path; `ShortcutRow` (printing path) calls `LauncherTile` directly. Removing it is a future cleanup, not a correctness concern.
- **`spoolSwatches` preserved:** The `spoolSwatches` parameter flows through `PrintStatusContent` for the Printing/Paused `ShortcutRow` path; the new Standby `ListRow` uses `DinghyIcons.LauncherSpool` (a VectorDrawable token) rather than the reactive `SpoolGlyph`. The reactive swatch tinting stays only in the printing shortcut tile.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all idle list icons use registered `DinghyIcons` tokens; all strings use `@StringRes` from `strings.xml`. No placeholders remain.

## Threat Flags

None. This plan reshapes existing local-Moonraker UI and removes the in-screen e-stop (now in AppShell). No new network surface, auth, or data handling.

## Self-Check: PASSED

Files modified:
- FOUND: app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt
- FOUND: app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt
- FOUND: app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt

Commits:
- FOUND: ec0caf7 (feat(24-04): idle action list + foot bar + FIX-5 Webcam icon swap)

Verification gates:
- HomeActionTest 12/12 GREEN
- assembleDebug BUILD SUCCESSFUL
- Crossfade >= 1 in PrintStatusScreen.kt: 3 found
- FloatingEStop() in PrintStatusScreen (non-comment): 0 found
- PLACEHOLDER(24-04) in HomeAction.kt: 0 found
- buildIdleActions in PrintStatusScreen.kt: 3 found
- Intent.Neutral foot buttons: 2 (lines 132, 139 in PrintStatusField.kt)
- gutter = null for Standby: confirmed (line 498 PrintStatusScreen.kt)
