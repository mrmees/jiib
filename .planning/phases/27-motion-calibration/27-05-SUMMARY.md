---
phase: 27
plan: "05"
subsystem: calibration-ui
tags: [bed-mesh, field-mode, preview, jiib-redesign, threat-mitigation]
dependency_graph:
  requires: [27-04]
  provides: [BedMeshScreen-rebuilt, BedMeshContent-stateless-seam, CalibrationPreviews-BedMesh-section]
  affects: [BedMeshHolder, BedMeshHeatmapHost, strings.xml, CalibrationPreviews, SampleFixtures]
tech_stack:
  added: []
  patterns:
    - MeshFieldMode sealed class (mirrors TempFieldMode — Field-mode switching without full-screen dialogs)
    - rememberSaveable + MeshFieldModeSaver (rotation-survival for fieldMode)
    - stateless seam WARNING-5 compliance (thin BedMeshScreen wrapper + BedMeshContent with all state as params)
    - state-adaptive FootButtonBar D-14 (3 branches: unhomed / homed+no-selection / profile-selected)
    - isValidProfileName gate for threat T-27-05-01 (profile name input sanitization)
    - ConfirmGuard overlays for T-27-05-02 (Remove) and T-27-05-03 (amber SAVE_CONFIG)
key_files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt
    - app/src/main/res/values/strings.xml
    - app/src/main/java/works/mees/dinghy/preview/CalibrationPreviews.kt
    - app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt
decisions:
  - "MeshFieldMode sealed class visibility raised to internal (mirrors BedMeshContent visibility) to avoid compiler error 'internal function exposes private-in-file parameter type'"
  - "FloatingEStop(visible=false) — calibration screens are pop-to-root foot-guns; no live E-stop needed on BedMesh screen"
  - "SampleFixtures bedMeshVm() builds BedMeshModel with 2x2 meshMatrix + non-empty profileName to produce isEmpty=false for non-empty-state previews"
metrics:
  duration: "~4h (including prior session context)"
  completed: "2026-06-11"
  tasks_completed: 2
  files_modified: 4
---

# Phase 27 Plan 05: BedMesh Screen Rebuild (Field Profile List + MeshFieldMode Takeover) Summary

BedMeshScreen rebuilt per jiib redesign grammar — dialogs deleted, Field-mode sealed class driving a save-name takeover, state-adaptive foot, stateless seam for previews, threat mitigations all wired.

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Rebuild BedMeshScreen — Field profile list + MeshFieldMode SaveName takeover (D-11..D-14) | f893559 | BedMeshScreen.kt, strings.xml |
| 2 | Extend CalibrationPreviews with BedMesh section + SampleFixtures bedMesh fixtures | f7c05e4 | CalibrationPreviews.kt, SampleFixtures.kt |

## What Was Built

**Task 1 — BedMeshScreen rebuild:**

- Deleted `SaveNameDialog`, `LoadSelectorDialog`, `MeshDialog` enum, `ProfileRow`, `MeshFieldButton`, `MeshGutterButton` composables (old dialog-heavy approach)
- Added `internal sealed class MeshFieldMode { data object ProfileList; data class SaveName(val prefill: String) }` — mirrors TempFieldMode pattern from TemperatureScreen
- Added `internal val MeshFieldModeSaver` — ListSaver for rotation survival of MeshFieldMode
- `defaultProfileName()` visibility raised from `private` to `internal` (needed by SaveName prefill logic)
- Thin wrapper `BedMeshScreen(container, holder, onBack)` — collects dispatcher + vm, hoists state via `rememberSaveable` (selectedProfile, fieldMode, showRemoveGuard, showSaveConfigGuard)
- Stateless seam `internal fun BedMeshContent(...)` with all state as params — WARNING-5 compliance for preview targeting
- BoxWithConstraints + rememberUnitGrid at root; `ScreenScaffold(gutter=null)` per jiib grammar
- Focus region: `BedMeshHeatmapHost` (P22 equality guards preserved — factory runs once, update-only) + ScaleToggle overlay + empty-state branch
- Field region: `when(fieldMode)` dispatch — ProfileList (ListBlock with active badge on `vm.model.profileName` + state-adaptive FootButtonBar per D-14) OR SaveName (TokenTextField with `isValidProfileName` gate + FootButtonBar [Save accent disabled until valid | Cancel C7])
- D-14 state-adaptive FootButtonBar: unhomed → HomeAll + Back; homed + no selection → Calibrate + Save + Back; profile selected → Apply + Remove + Back
- `FloatingEStop(visible=false)` — calibration screens pop to root (no live E-stop)
- ConfirmGuard overlays: Remove (T-27-05-02) + amber SAVE_CONFIG (T-27-05-03)
- Toast overlay for transient error display

**Task 2 — CalibrationPreviews BedMesh section:**

- `SampleFixtures`: `bedMeshVm()` factory (2×2 meshMatrix + non-empty profileName → isEmpty=false), `bedMeshEmpty` (unhomed, no profiles), `bedMeshProfilesNoActive` (profiles list, no loaded mesh)
- `CalibrationPreviews`: 13+ @Preview renders in BedMesh section — 4-state matrix (ProfileList no-selection, ProfileList selected landscape, SaveName takeover portrait, empty-state), 6-theme matrix (Colorful/Simple/HighContrast × dark/light on @Nexus7Previews), 2 fs=L overflow (portrait+landscape SaveName), 1 pseudolocale en-XA spot-check
- All previews target stateless `BedMeshContent` seam (no VM/holder wiring in previews)

**Strings added (15):** mesh_calibrate, mesh_apply, mesh_save, mesh_remove, mesh_remove_confirm, mesh_save_name_hint, mesh_save_name_label, mesh_save_confirm, mesh_save_name_invalid, mesh_profile_active, mesh_empty_state, mesh_empty_state_hint, cd_mesh_heatmap (+ probe_starting already existed from 27-04)

## Threat Mitigations Applied

| Threat | Mitigation | Status |
|--------|-----------|--------|
| T-27-05-01 | `isValidProfileName` gate in SaveName FootButtonBar — Save button disabled until name passes regex | Implemented |
| T-27-05-02 | ConfirmGuard overlay for Remove action | Implemented |
| T-27-05-03 | Amber ConfirmGuard for SAVE_CONFIG dispatch | Implemented |

## Verification

- `assembleDebug` → BUILD SUCCESSFUL (both tasks)
- `testDebugUnitTest` → BUILD SUCCESSFUL (UP-TO-DATE, all existing BedMesh tests pass)
- `compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL (1 pre-existing deprecation warning in FineTuneNavTest, out of scope)

## Deviations from Plan

**1. [Rule 1 - Bug] MeshFieldMode visibility raised to internal**
- Found during: Task 1 compile
- Issue: `BedMeshContent` is `internal` but `MeshFieldMode` was `private sealed class` — Kotlin compiler error "internal function exposes its 'private-in-file' parameter type"
- Fix: Changed `private sealed class MeshFieldMode` → `internal sealed class MeshFieldMode` and `private val MeshFieldModeSaver` → `internal val MeshFieldModeSaver`
- Files modified: BedMeshScreen.kt
- Commit: f893559

**2. [Rule 1 - Bug] FloatingEStop signature — removed non-existent printerState parameter**
- Found during: Task 1 compile
- Issue: Initial implementation passed `printerState` to `FloatingEStop` which doesn't have that parameter
- Fix: Used correct `FloatingEStop(visible=false, onClick={}, uDp=grid.uDp, modifier=...)` signature
- Files modified: BedMeshScreen.kt
- Commit: f893559

**3. [Rule 1 - Bug] Removed unused imports (MutableStateFlow, BedMeshModel)**
- Found during: Task 1 compile
- Issue: Old pattern used `dispatcher?.inFlight ?: MutableStateFlow(emptySet())` — new implementation doesn't need it; BedMeshModel not directly referenced in UI layer
- Fix: Removed the two unused imports
- Files modified: BedMeshScreen.kt
- Commit: f893559

## Known Stubs

None — all profile operations wire through `dispatcher?.dispatch(...)` to the real CommandRegistry. Empty states are real data-driven branches (vm.isEmpty / vm.model.profileNames.isEmpty()).

## Threat Flags

None — no new network endpoints, auth paths, file access patterns, or schema changes introduced.

## Self-Check: PASSED

- BedMeshScreen.kt: FOUND (/mnt/e/claude/personal/github/dinghy-display/app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt)
- CalibrationPreviews.kt: FOUND (/mnt/e/claude/personal/github/dinghy-display/app/src/main/java/works/mees/dinghy/preview/CalibrationPreviews.kt)
- SampleFixtures.kt: FOUND (/mnt/e/claude/personal/github/dinghy-display/app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt)
- strings.xml (mesh_calibrate et al): FOUND
- Commit f893559: FOUND
- Commit f7c05e4: FOUND
