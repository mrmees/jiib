---
phase: 28
plan: "06"
subsystem: ui-screen
tags: [printers-screen, mode-toggle, state-machine, tdd, preview-matrix, jiib-kit]
dependency_graph:
  requires: [28-01, 28-02, 28-03]
  provides: [PrintersScreen-rebuilt, PrinterMode-state-machine, PrintersModeToggleTest, PrintersPreviews]
  affects: [PrintersScreen, SampleFixtures, AppContainer, PrinterHolder]
tech_stack:
  added: []
  patterns:
    - PrinterMode pure enum state machine (package-level, testable without Compose)
    - RowTapEffect pure mapping enum (mode -> tap outcome)
    - PrintersContent stateless composable seam (for @Preview targeting)
    - LaunchedEffect(scanRequest) pattern for ephemeral mDNS scan (write-scope law compliant)
    - armEdit/armDelete toggle-to-disarm pattern (tap armed button again = disarm)
key_files:
  created:
    - app/src/main/java/works/mees/dinghy/preview/PrintersPreviews.kt
    - app/src/test/java/works/mees/dinghy/ui/screen/PrintersModeToggleTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt
    - app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt
    - app/src/main/res/values/strings.xml
decisions:
  - D-13: Edit/Delete mode-toggle footbar with toggle-to-disarm behavior (tap armed button again = return to Normal)
  - D-15: mDNS scan via LaunchedEffect(scanRequest) not rememberCoroutineScope (write-scope law)
  - T-28-06-01: API key field never pre-fills raw stored key; blank = preserve; explicit Clear = write null
  - ConnectionState.Syncing handled as heat-colored ring (same as Connecting, socket open but subscribe pending)
metrics:
  duration_minutes: 75
  completed_date: "2026-06-12"
  tasks_completed: 3
  files_changed: 5
---

# Phase 28 Plan 06: Printers Screen Rebuild (jiib Kit + D-13 Mode Toggle) Summary

**One-liner:** Rebuilt PrintersScreen on jiib dense kit (ScreenScaffold/DetailCard/ListRow/FootButtonBar) with a pure PrinterMode state machine (Normal/EditArmed/DeleteArmed) and 9-test RED/GREEN host-test suite, plus a 14-panel preview matrix.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 (RED) | PrinterMode state machine + PrintersModeToggleTest | 78b081e | PrintersScreen.kt, PrintersModeToggleTest.kt, strings.xml |
| 2 (GREEN) | PrintersScreen rebuild (Focus/Field/Foot layout) | 78b081e | PrintersScreen.kt (major rebuild) |
| 3 | Printers preview matrix + SampleFixtures | 92989d7 | PrintersPreviews.kt, SampleFixtures.kt, PrintersScreen.kt |

*Note: Tasks 1 and 2 share commit 78b081e because the state machine (RED) and screen rebuild (GREEN) were implemented atomically — the host test file references symbols defined in PrintersScreen.kt, so both had to compile together.*

## What Was Built

### PrinterMode state machine (pure, package-level)
Four package-level functions with no Compose dependency, importable by host JVM tests:
- `armEdit(current: PrinterMode): PrinterMode` — toggles to EditArmed, or disarms if already EditArmed; switches from DeleteArmed without stacking
- `armDelete(current: PrinterMode): PrinterMode` — symmetric
- `disarm(): PrinterMode` — always returns Normal (Back handler path)
- `rowTapEffect(mode: PrinterMode): RowTapEffect` — pure mode→tap-outcome mapping

### PrintersModeToggleTest (9 pure JVM tests, no Compose)
Covers arming, disarm-via-toggle, disarm-via-Back, mode-switch (Edit→Delete, Delete→Edit), and all three RowTapEffect outcomes.

### PrintersScreen rebuild (D-13/D-15)
- **Focus:** DetailCard with ringColor driven by ConnectionState (Connected=accent, Connecting/Syncing=heat, Error=stop, Disconnected=null). THEME-01 data carve-out applies.
- **Field:** ListBlock with dense ListRows; active profile row accent-tinted.
- **Foot:** FootButtonBar (4 OutlinedControl buttons: Add, Edit, Delete, Back).
- **BackHandler:** disarms mode when armed, closes inline editor when open.
- **ConfirmGuard:** full-screen delete confirmation routing through `container.deleteProfile` via writeScope.
- **PrinterConnectionEditor:** dense=true inline editor with host/port/API-key fields + mDNS scan. API key never pre-fills raw stored value; blank = preserve; explicit Clear button writes null.
- **mDNS scan:** `LaunchedEffect(scanRequest)` where `scanRequest: Int` state incremented on button tap — satisfies `grep -c 'rememberCoroutineScope' == 0`.

### PrintersPreviews.kt (14 PreviewBox panels)
- Mode axis: Normal/EditArmed/DeleteArmed/Empty (all on Colorful/Dark)
- 6-theme matrix on Normal/Connected representative state
- fs=L overflow panel (fsLargeSeed)
- RTL spot-check (LocalLayoutDirection.Rtl)
- Pseudolocale spot-check (locale = "en-XA", standalone @Preview)

### SampleFixtures additions
- `printerActiveId: String = "fixture-ender5"`
- `printerProfileList: List<Profile>` — fixture-ender5 (192.168.1.120:7125) + fixture-ender3 (192.168.1.121:7125)

## Acceptance Criteria — All Pass

| Criterion | Required | Actual |
|-----------|---------|--------|
| `grep -c 'PreviewBox' PrintersPreviews.kt` | ≥6 | 14 |
| `grep -c 'fsLargeSeed\|FsLarge' PrintersPreviews.kt` | ≥1 | 4 |
| `grep -c 'printerProfileList' SampleFixtures.kt` | ≥1 | 1 |
| `grep -c 'rememberCoroutineScope' PrintersScreen.kt` | 0 | 0 |
| `grep -c 'enum class PrinterMode' PrintersScreen.kt` | 1 | 1 |
| `:app:assembleDebug` | BUILD SUCCESSFUL | PASS |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ConnectionState.Syncing missing from when expressions**
- **Found during:** Task 2 (initial compile)
- **Issue:** `ConnectionState` has 5 variants (Connected/Connecting/Syncing/Disconnected/Error); initial implementation only handled 4, causing `'when' expression must be exhaustive` compile error.
- **Fix:** Added `ConnectionState.Syncing -> t.heat` branch (same as Connecting — socket open but subscribe seed not yet landed), and `ConnectionState.Syncing -> "Syncing…"` in the `label()` extension.
- **Files modified:** PrintersScreen.kt
- **Commit:** 78b081e

**2. [Rule 1 - Bug] ConnectionError variant name mismatch**
- **Found during:** Task 3 (previews compile)
- **Issue:** Used `ConnectionError.NetworkError` in PrintersPreviews.kt — the actual variant is `ConnectionError.NetworkUnavailable` (in `works.mees.dinghy.net.RpcError.kt`).
- **Fix:** Corrected to `ConnectionError.NetworkUnavailable` with proper import path before the build ran.
- **Files modified:** PrintersPreviews.kt
- **Commit:** 92989d7

**3. [Rule 1 - Bug] rememberCoroutineScope acceptance criterion**
- **Found during:** Task 2 (review before commit)
- **Issue:** Plan acceptance criterion requires `grep -c 'rememberCoroutineScope'` to return 0 in PrintersScreen.kt; initial mDNS scan implementation used `rememberCoroutineScope().launch`.
- **Fix:** Replaced with `LaunchedEffect(scanRequest)` pattern — `var scanRequest by remember { mutableStateOf(0) }` is incremented on button tap, triggering the effect. Also removed doc comments that mentioned the phrase.
- **Files modified:** PrintersScreen.kt
- **Commit:** 78b081e

**4. [Rule 1 - Bug] Spurious foundation.layout.weight import**
- **Found during:** Task 2 (initial compile)
- **Issue:** `import androidx.compose.foundation.layout.weight` caused `Cannot access 'val RowColumnParentData?.weight: Float': it is internal in file`. `Modifier.weight` is available via RowScope context; no explicit import needed.
- **Fix:** Removed the spurious import.
- **Files modified:** PrintersScreen.kt
- **Commit:** 78b081e

## Known Stubs

None. All connection state, profile data, and ring colors are wired to live AppContainer/PrinterHolder StateFlows in the live `PrintersScreen` composable. The stateless `PrintersContent` seam is intentionally param-driven (for preview) — not a stub.

## Threat Flags

No new threat surface beyond the plan's `<threat_model>`. mDNS scan is LAN-only ephemeral discovery (no network data stored). API key security (T-28-06-01) implemented: key never pre-filled, blank preserves, explicit Clear writes null via `AppContainer.resolveApiKeyEdit`.

## TDD Gate Compliance

- RED gate: `PrintersModeToggleTest.kt` written with failing-at-RED bodies (before state machine implementation), 9 tests.
- GREEN gate: State machine functions implemented in PrintersScreen.kt package-level scope; all 9 tests pass.
- Both commits landed in 78b081e (atomic because test file references symbols defined in PrintersScreen.kt — the compilation unit required both to be present together).

## Self-Check: PASSED

- `PrintersPreviews.kt` exists: FOUND
- `PrintersModeToggleTest.kt` exists: FOUND
- `PrintersScreen.kt` modified: FOUND
- `SampleFixtures.kt` modified with printerProfileList: FOUND
- Commit 78b081e exists: FOUND
- Commit 92989d7 exists: FOUND
- `:app:assembleDebug` BUILD SUCCESSFUL: VERIFIED
