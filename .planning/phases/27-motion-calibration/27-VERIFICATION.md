---
phase: 27-motion-calibration
verified: 2026-06-12T14:30:00Z
status: human_needed
score: 5/5
overrides_applied: 0
human_verification:
  - test: "On-device Back-path spot-check — three paths introduced or affected by CR-01"
    expected: |
      Path 1 (Scan→Back): Navigate to Spool screen, tap Scan, press system Back → scan overlay closes, Spool screen remains visible underneath (NOT: NavHost pops Spool while scan is open).
      Path 2 (Prompt→Back): Trigger a macro prompt over any drilled-down screen, press system Back → prompt dismisses (action:prompt_end dispatched), screen stays put.
      Path 3 (Prompt-during-Active-probe→Back): Start a probe calibration session to Active state, if a klicky macro prompt appears, press system Back → prompt closes, probe session continues (NOT: CalibrationProbe popped).
    why_human: "CR-01 fix moved scan/prompt BackHandlers inside overlay if-blocks after NavHost. The fix is structurally correct by code inspection (BackHandler at AppShell:873 and :896, both post-NavHost:589), but these three Back paths involve runtime BackHandler registration ordering that cannot be verified by grep — only on-device execution with the probe Active and a concurrent macro prompt proves path 3 specifically (klicky probe routines use the prompt protocol mid-routine, which is the exact failure mode CR-01 targeted). Host tests do not cover BackHandler registration order."
---

# Phase 27: Motion + Calibration — Verification Report

**Phase Goal:** Migrate the spatial + calibration surfaces. MoveScreen/MotionScreen — the jog/XYZ controls stay GRIDS, not lists. The calibration cluster — CalibrationHubScreen, ProbeCalibrateScreen, BedMeshScreen, ScrewsTiltScreen, TiltScreen — restyled onto the classes with wizard/step-through flows reconciled to the redesigned grammar. Per-screen conformance folds in.
**Verified:** 2026-06-12T14:30:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Move/Motion restyled with spatial jog controls KEPT as grids, owner-approved on flox both orientations | VERIFIED | MoveScreen.kt: `BoxWithConstraints`, `padSize = (maxHeight * 0.60f).coerceAtMost(maxWidth)`, `ZColumn`, `DistanceStepperColumn` — all equal `Modifier.weight(1f)` cells. Owner blanket "approved" 2026-06-12 per 27-UAT.md (all SC-1 rows PASSED). |
| 2 | Calibration Hub + Probe-Calibrate + Bed-Mesh + Screws-Tilt + Tilt rebuilt onto the classes; wizard flows coherent with the redesigned nav/back-stack | VERIFIED | 6 real `NavDest.CalibrationXxx` routes confirmed in NavDest.kt (lines 38-43). `CalibrationHubScreen.kt` uses `DetailCard + ListBlock + ListRow + onOpen` callback. All 6 `composable<NavDest.CalibrationXxx>` blocks in AppShell.kt (lines 682, 692, 719, 726, 733, 741). Owner UAT 27-UAT.md: SC-2a through SC-2e all PASSED. |
| 3 | Per-screen conformance met (≥64px, fsSp S/M/L, rotation) | VERIFIED | `fsSp(N, t.fs).sp` usage confirmed in all 6 rebuilt screens (MoveScreen, CalibrationHub, ProbeCalibrate, BedMesh, ScrewsTilt, TiltScreen). `rememberSaveable` + `MeshFieldModeSaver` for BedMesh rotation survival. BoxWithConstraints in MoveScreen for rotation-adaptive layout. UAT conformance spot-checks PASSED. |
| 4 | @Preview matrices + tokenized strings/icons ship with each screen | VERIFIED | `MovePreviews.kt` exists with 15 `@Preview`/`@Nexus7Previews` annotations. `CalibrationPreviews.kt` exists with 70 annotations covering Hub, Probe, BedMesh, ScrewsTilt, and Tilt sections. All 5 Routine* tokens registered in `DinghyIcons.kt` (lines 195-199). Post-review WR-03 fix extracted hardcoded English literals to `strings.xml`. Post-review WR-04 fix added 9 additional tokens to registry. Zero raw `MaterialSymbol(name=)` in rebuilt screens. |
| 5 | No functional regressions (host tests green; on-device smoke) — homing/jog, probe/mesh/screws routines intact | VERIFIED | All plan-level build gates passed: `assembleDebug BUILD SUCCESSFUL` and `testDebugUnitTest GREEN` confirmed across 27-01 through 27-06 SUMMARYs. New tests added: `NavDestRoundTripTest.kt`, `FootGunDestsTest.kt`. `PopToRootTest` rewritten to assert `FOOT_GUN_DESTS.size == 8`. `HomeActionTest` + `ShellNavStateTest` rewritten. `assembleRelease` also BUILD SUCCESSFUL (27-06). UAT SC-5 PASSED. |

**Score:** 5/5 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` | Rebuilt Move screen — thin wrapper + stateless MoveContent, BoxWithConstraints, vertical Z column + distance stepper, FootButtonBar | VERIFIED | Exists; `MoveContent`, `BoxWithConstraints`, `ZColumn`, `DistanceStepperColumn`, `FootButtonBar` all present; 0 raw `MaterialSymbol(name=)` |
| `app/src/main/java/works/mees/dinghy/preview/MovePreviews.kt` | @Preview matrix on MoveContent: portrait/landscape + 6 theme combos + fs=L + pseudolocale | VERIFIED | Exists; 15 @Preview/@Nexus7Previews annotations in main source set |
| `app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt` | list+Focus hub with DetailCard Focus + ListBlock routine list + onOpen nav | VERIFIED | Exists; `DetailCard`, `ListBlock`, `ListRow`, `DinghyIconView`, `onOpen` callback all confirmed |
| `app/src/main/java/works/mees/dinghy/ui/calibration/ProbeCalibrateScreen.kt` | Vertical TESTZ columns + state-adaptive FootButtonBar, gutter=null | VERIFIED | Exists; `FootButtonBar`, `ProbePageState`, `ZReadoutDisplay`, `starting` verbatim semantics present |
| `app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt` | Field profile list + MeshFieldMode SaveName takeover + ConfirmGuards | VERIFIED | Exists; `MeshFieldMode`, `MeshFieldModeSaver`, `isValidProfileName` gate (via Save button enabled check), ConfirmGuards, WR-02 (Remove raises SAVE_CONFIG) and WR-05 (Save gated on `!vm.isEmpty`) fixes confirmed |
| `app/src/main/java/works/mees/dinghy/ui/calibration/ScrewsTiltScreen.kt` | ListRow screw list + spatial Focus preserved + FootButtonBar | VERIFIED | Exists; `ListBlock`/`ListRow`, spatial Focus (BedScale+BoxWithPoints) preserved, `FootButtonBar`, `stringResource` throughout |
| `app/src/main/java/works/mees/dinghy/ui/calibration/TiltScreen.kt` | Parameterized ZTilt+QGL screen + FootButtonBar | VERIFIED | Exists; `TiltVariant`, `TiltContent`, `TiltFocus`, `FootButtonBar`, `stringResource` throughout |
| `app/src/main/java/works/mees/dinghy/preview/CalibrationPreviews.kt` | @Preview matrices for all 5 calibration screens | VERIFIED | Exists; 70 @Preview/@Nexus7Previews annotations covering all 5 calibration screen sections |
| `app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt` | 6 CalibrationXxx data objects + FOOT_GUN_DESTS size==8 + CalibrationRoutine.toNavDest() | VERIFIED | All 6 members present (lines 38-43); `FOOT_GUN_DESTS` contains all 8 members; `toNavDest()` extension confirmed |
| `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` | 5 Routine* tokens + 9 additional WR-04 tokens | VERIFIED | RoutineProbeCalibrate/BedMesh/ScrewsTilt/ZTilt/Qgl (lines 195-199); JogXPlus/HomeStateHomed/HomeStateUnhomed/ScrewPending/ScrewBase/ScrewInTolerance/ScrewTurnCcw/ScrewTurnCw/MeshEmpty (lines 207-215); all in `DinghyIcons.all` |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `CalibrationHub composable<>` | `navController.navigate(NavDest.CalibrationXxx)` | `onOpen = { routine -> navController.navigate(routine.toNavDest()) }` | WIRED | AppShell.kt:682 block; `CalibrationRoutine.toNavDest()` confirmed in NavDest.kt |
| `CalibrationProbe composable<> BackHandler` | probe session `ProbePageState.Active \|\| starting` | `enabled = (Active \|\| starting) && !drawerOpen && !nav.scanActive && !promptView.visible` | WIRED | AppShell.kt:707-710; gating confirmed |
| Swipe-suppress set | All 6 CalibrationXxx routes | `d.isRoute<NavDest.CalibrationXxx>()` checks | WIRED | AppShell.kt:540-545; all 6 confirmed |
| `currentNavDest` mapping | All 6 CalibrationXxx routes | 6 `isRoute<NavDest.CalibrationXxx>() ->` branches | WIRED | AppShell.kt:846-851; all 6 confirmed |
| Scan overlay BackHandler | After NavHost (CR-01 fix) | `if (nav.scanActive) { BackHandler { ... } }` inside overlay block | WIRED | AppShell.kt:869-873; NavHost at :589, BackHandler at :873 — correct priority ordering |
| Prompt overlay BackHandler | After NavHost (CR-01 fix) | `if (promptView.visible) { BackHandler { ... } }` inside overlay block | WIRED | AppShell.kt:891-896; NavHost at :589, BackHandler at :896 — correct priority ordering |
| BedMesh Remove → SAVE_CONFIG guard | `showSaveConfigGuard = true` after remove dispatch (WR-02) | `onRemoveConfirm` body sets `showSaveConfigGuard = true` | WIRED | BedMeshScreen.kt:166-175; fix commit `68072ca` confirmed |
| BedMesh Save button | `!vm.isEmpty` gate (WR-05) | `enabled = dispatcherPresent && !vm.isEmpty` | WIRED | BedMeshScreen.kt:416; fix commit `f97b72b` confirmed |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `MoveContent` | `vm.z`, `vm.x`, `vm.y`, `vm.xHomed`, etc. | `MoveScreen` thin wrapper reads `MoveHolder.vm` (StateFlow from Moonraker) | Yes — live Moonraker WebSocket via `MoveHolder` | FLOWING |
| `CalibrationHubContent` | `routines` | `CalibrationHubHolder.routines` (StateFlow from capability detection) | Yes — derived from live printer capabilities | FLOWING |
| `ProbeCalibrateContent` | `vm.state`, `vm.zPosition`, `starting` | `ProbeCalibrateHolder.vm` + `dispatcher?.inFlight` (StateFlow) | Yes — live probe state from Moonraker | FLOWING |
| `BedMeshContent` | `vm.model.profileNames`, `vm.isEmpty`, `fieldMode` | `BedMeshHolder.vm` (StateFlow from Moonraker) + local `rememberSaveable` state | Yes — profile list from live Moonraker bed_mesh object | FLOWING |
| `ScrewsTiltContent` | `vm.screws`, `vm.state`, `vm.totalScrews` | `ScrewsTiltHolder.vm` (StateFlow from Moonraker) | Yes — live screws result data | FLOWING |
| `TiltContent` | `vm.state`, `vm.adjustments` | `TiltHolder.vm` (StateFlow from Moonraker, parameterized by `TiltVariant`) | Yes — live tilt result data | FLOWING |

---

### Behavioral Spot-Checks

Step 7b: SKIPPED for test-compilation/build gates (covered by plan-level assembleDebug + testDebugUnitTest gates across all 7 plans). The app requires a running Moonraker WebSocket server to exercise motion/calibration paths.

---

### Probe Execution

Step 7c: No probe scripts declared in plans or SUMMARY files. Conventional `scripts/*/tests/probe-*.sh` not present in this project. SKIPPED.

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| SC-1 | 27-03, 27-07 | Move/Motion spatial jog stays a grid, both orientations, owner-approved | SATISFIED | UAT 27-UAT.md all SC-1 checks PASSED; MoveContent BoxWithConstraints portrait/landscape layout confirmed in code |
| SC-2 | 27-02, 27-04, 27-05, 27-06, 27-07 | Calibration cluster rebuilt onto redesign classes; wizard flows coherent | SATISFIED | 6 NavDest routes live; CalibrationHub/Probe/BedMesh/ScrewsTilt/Tilt all rebuilt with stateless seams; UAT SC-2a..2e all PASSED |
| SC-3 | 27-03 through 27-06 | Per-screen conformance (≥64px, fsSp, rotation) | SATISFIED | fsSp confirmed in all 6 screens; rememberSaveable rotation survival in BedMesh; BoxWithConstraints in Move for orientation-adaptive layout; UAT conformance spot-checks PASSED |
| SC-4 | 27-01 through 27-06 | @Preview matrices + tokenized strings/icons ship with each screen | SATISFIED | MovePreviews.kt (15 annotations), CalibrationPreviews.kt (70 annotations); 5 Routine* tokens + 9 WR-04 tokens registered; WR-03 fix extracted hardcoded literals; assembleDebug green (previews in main source set) |
| SC-5 | 27-03 through 27-07 | No functional regressions — host tests green, on-device smoke | SATISFIED | assembleDebug + testDebugUnitTest GREEN across all plans; assembleRelease GREEN (27-06); UAT SC-5 PASSED; new round-trip + FOOT_GUN tests added |

No orphaned requirements: the phase declares "requirements: — (UX migration); plans map to ROADMAP success criteria SC-1..SC-5." REQUIREMENTS.md has no Phase 27 entries.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| BedMeshScreen.kt | 236, 552 | `placeholder` in KDoc comments | Info | Describes `LocalInspectionMode → placeholder branch` for `BedMeshHeatmapHost` in preview mode — intentional and correct usage; not a stub in rendering path |

No TBD/FIXME/XXX markers found in any phase-modified files. No unreferenced debt markers. The six Info findings in 27-REVIEW.md (IN-01 dead code, IN-02 unused strings, IN-03 unused parameter, IN-04 locale formatting, IN-05 FloatingEStop stubs, IN-06 direction glyph tints) were explicitly scoped out of the fix pass by owner direction and do not block the phase goal. None is a BLOCKER — all are clean-up items deferred to later phases or future polish.

---

### Human Verification Required

#### 1. CR-01 Back-Path Correctness — Three Paths Under Real Navigation Conditions

**Test:** With a live printer connected (E3 192.168.1.121:7125 or E5+ 192.168.1.120:7125):

**Path 1 — Scan overlay dismissal:**
Navigate from home → Spool screen (depth 2 in back-stack). Tap "Scan" to open the barcode scan surface. Press system Back.
**Expected:** Scan overlay closes cleanly. Spool screen remains visible. The app does NOT pop back to home/waterfall while the scan is open.

**Path 2 — Macro prompt dismissal:**
Trigger any macro that uses the prompt protocol (any macro that shows the overlay prompt/dialog). While the prompt is visible over a drilled-down screen, press system Back.
**Expected:** Prompt closes (action:prompt_end dispatched to Moonraker). The drilled-down screen remains visible. The app does NOT navigate away while the prompt is shown.

**Path 3 — Prompt during Active probe session (the CR-01 target case):**
Navigate to Calibration → Probe Calibrate. Home all axes. Start the probe session (reaches Active state). If a klicky macro prompt appears mid-probe (klicky probes use the prompt protocol during the probe routine), press system Back.
**Expected:** The prompt closes. The probe session remains Active. The screen does NOT pop to CalibrationHub (which would abandon the nozzle mid-descent — the D-09 safety intent).

**Why human:** CR-01 corrected BackHandler registration ordering by moving scan/prompt BackHandlers into their overlay blocks after the NavHost (AppShell.kt:873 and :896). The structural fix is verified by code inspection (no pre-NavHost scan/prompt BackHandlers remain between lines 510 and 589). However, `OnBackPressedDispatcher` priority ordering under real Compose recomposition with concurrent states (Active probe + klicky macro prompt) is runtime behavior that cannot be proven by grep. Path 3 in particular (the original CR-01 failure mode) requires a live printer running a klicky probe routine to exercise. The initial UAT on 2026-06-12 pre-dated the CR-01 fix and did not cover this path.

---

### Gaps Summary

No gaps identified. All 5 success criteria are verified against the codebase. Post-review fixes CR-01, WR-02, WR-03, WR-04, and WR-05 are all committed and confirmed in code. The single human verification item (CR-01 back-path correctness) does not indicate a code defect — the fix is structurally correct — but the runtime behavior of the three Back paths under real navigation conditions requires on-device confirmation before the phase is fully closed.

---

_Verified: 2026-06-12T14:30:00Z_
_Verifier: Claude (gsd-verifier)_
