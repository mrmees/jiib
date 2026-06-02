---
phase: 07-files-print-control-core-print-loop-gate
plan: 04
subsystem: files-ui
tags: [files, compose, recyclerview, coil, shell-route, confirm-guard]
requires:
  - phase: 07-files-print-control-core-print-loop-gate
    provides: 07-03 session FileBrowserClient and FileBrowserHolder
provides:
  - Live Files shell destination
  - Compose Files Focus/Field/Gutter screen
  - AndroidView-hosted RecyclerView file list
  - Bounded Coil row thumbnail helper
affects: [print-status-controls, files-uat, shell-presence]
tech-stack:
  added: []
  patterns: [hybrid Compose plus RecyclerView field, bounded thumbnail loader, focus-level guards]
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/files/FileListView.kt
    - app/src/main/java/works/mees/dinghy/ui/files/FileRowsAdapter.kt
    - app/src/main/java/works/mees/dinghy/ui/files/FileThumbnailLoader.kt
    - app/src/test/java/works/mees/dinghy/ui/files/FileThumbnailLoaderTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
    - app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt
    - app/src/main/java/works/mees/dinghy/designsystem/ConfirmGuard.kt
    - app/src/test/java/works/mees/dinghy/ui/route/TopRouteTest.kt
    - app/src/androidTest/java/works/mees/dinghy/ui/ShellPresenceTest.kt
key-decisions:
  - "Files composes through AppShell using the session FileBrowserHolder and idle-safe fallback client."
  - "Rows remain compact in a RecyclerView/ListAdapter; rich slicer metadata stays in Focus."
  - "Start and delete use Focus-level ConfirmGuard labels from the UI spec."
  - "Row thumbnails use an explicit 96px request size and a 2 MiB Files-specific Coil memory cache."
patterns-established:
  - "Files list row updates are DiffUtil-backed rather than benchmark-style full-list invalidation."
  - "Shell route additions re-key holders from the atomic SpineHandle and keep Android Back as caller-stack pop."
requirements-completed: [FILE-01, FILE-02, FILE-03, FILE-04]
duration: 50min
completed: 2026-06-02
---

# Phase 07 Plan 04: Files Shell Screen Summary

**Live Files drawer route with hybrid Compose/RecyclerView browsing, selected-file preview, guarded print/delete actions, and bounded row thumbnails.**

## Performance

- **Duration:** 50 min
- **Started:** 2026-06-02T09:21:00-05:00
- **Completed:** 2026-06-02T10:11:02-05:00
- **Tasks:** 3
- **Files modified:** 12

## Accomplishments

- Added `Dest.Files`, made the drawer Files tile live, and routed AppShell to a per-session `FileBrowserHolder`.
- Built `FilesScreen` with path chip, RecyclerView Field, selected-file Focus preview, `Cancel picker` / `Print file` gutter, and focus-level `Delete file`.
- Added UI-SPEC ConfirmGuard safe-dismiss labels via a defaulted `cancelLabel`.
- Added `FileRowsAdapter` with stable IDs, compact rows, selected-state styling, and no row delete/long-press destructive action.
- Added `FileThumbnailLoader` with explicit row thumbnail request sizing and a bounded memory cache.

## Task Commits

Each task was committed atomically:

1. **Tasks 1-3: Files route, screen, adapter, and bounded thumbnails** - `6c6f986` (feat)

## Files Created/Modified

- `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt` - Compose Focus/Field/Gutter Files screen and ConfirmGuard wiring.
- `app/src/main/java/works/mees/dinghy/ui/files/FileListView.kt` - AndroidView wrapper that hosts the RecyclerView field.
- `app/src/main/java/works/mees/dinghy/ui/files/FileRowsAdapter.kt` - Stable compact file/folder/up row adapter.
- `app/src/main/java/works/mees/dinghy/ui/files/FileThumbnailLoader.kt` - Files-specific bounded Coil loader and row request spec.
- `app/src/test/java/works/mees/dinghy/ui/files/FileThumbnailLoaderTest.kt` - Source-level tests for row thumbnail dimensions and cache bounds.
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` - Files holder construction and route branch.
- `app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt` - Live Files drawer destination.
- `app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt` - `Dest.Files`.
- `app/src/main/java/works/mees/dinghy/designsystem/ConfirmGuard.kt` - Defaulted safe-dismiss label.
- `app/src/test/java/works/mees/dinghy/ui/route/TopRouteTest.kt` - Files destination proof.
- `app/src/androidTest/java/works/mees/dinghy/ui/ShellPresenceTest.kt` - Files treated as live; Macros remains inert.

## Decisions Made

- Files print start is UI-gated on `virtual_sdcard` capability, selected file, and no pending start.
- Delete remains visible as a selected-file Focus action but disabled while printing or paused; holder enforcement remains the final guard.
- The thumbnail cache cap is fixed at 2 MiB for the Files list before large-library UAT.

## Deviations from Plan

None - plan executed as written.

## Issues Encountered

- Adding `Dest.Files` correctly forced an exhaustive `AppShell` branch before the route test could pass.
- The Android test source set needed its fake `SpineHandle` updated with the new file browser facade from 07-03.

## Verification

`/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.ui.route.TopRouteTest :app:compileReleaseKotlin --no-daemon" | tr -d '\r'`

Result: passed, `BUILD SUCCESSFUL in 35s`.

`/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.ui.files.FileBrowserHolderTest :app:compileReleaseKotlin --no-daemon" | tr -d '\r'`

Result: passed, `BUILD SUCCESSFUL in 13s`.

`/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.ui.files.FileBrowserHolderTest --tests works.mees.dinghy.ui.files.FileThumbnailLoaderTest :app:compileReleaseKotlin --no-daemon" | tr -d '\r'`

Result: passed, `BUILD SUCCESSFUL in 20s`.

`/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugAndroidTestKotlin --no-daemon" | tr -d '\r'`

Result: passed, `BUILD SUCCESSFUL in 35s`.

Source grep:

- `rg -n "notifyDataSetChanged|delete|longClick|setOnLongClickListener|server.files.roots|server.files.list" app/src/main/java/works/mees/dinghy/ui/files app/src/main/java/works/mees/dinghy/ui/shell app/src/main/java/works/mees/dinghy/command`
- Result: no production Files `notifyDataSetChanged`, no row long-click/delete affordance, no roots/list usage.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 07-05 can wire Print Status print-loop controls to the live Files route and existing print/file command registry. Plan 07-06 still needs the connected-device route-render and large-library thumbnail-scroll UAT.

## Self-Check: PASSED

- Files is a live drawer destination.
- Files UI follows Focus/Field/Gutter with RecyclerView row browsing.
- Start/delete use ConfirmGuard copy from the UI spec.
- Thumbnail decode/cache behavior has a source-level bound before live UAT.

---
*Phase: 07-files-print-control-core-print-loop-gate*
*Completed: 2026-06-02*
