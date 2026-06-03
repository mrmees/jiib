---
phase: 09-calibration-maintenance
plan: 06
subsystem: ui
tags: [calibration, manual-probe, probe-calibrate, files-delete, moonraker, compose]

# Dependency graph
requires:
  - phase: 09-03
    provides: parseZPosition / ZPositionBracket, probeCalibrateGate (A3), deleteAllowed (D-15) pure predicate
  - phase: 09-05
    provides: amber ConfirmGuard warn variant (SAVE_CONFIG restart gate), TiltHolder/TiltScreen template
  - phase: 09-02
    provides: probeCalibrate/zEndstopCalibrate/testZ/accept/abort/saveConfig CommandSpecs; manual_probe live object
provides:
  - ProbeCalibrateHolder + ProbeCalibrateScreen — the ONE interactive/stateful calibration page (manual-probe Z session)
  - D-15 Files delete-gate fix — scoped to the active print file at BOTH the screen and holder gates
  - docs/ui_design scoped-delete rule (supersedes idle-only) + 09-UAT.md (five manual-only verifications)
affects: [09-07-nav-wiring, on-device-UAT]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Interactive calibration page driven by manual_probe.is_active (Pattern 3); bracket sourced from the un-throttled gcodeResponses stream, not printerState"
    - "Both delete gates (screen + holder) route ONE host-tested pure predicate so the path-form match can't regress"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/calibration/ProbeCalibrateHolder.kt
    - app/src/main/java/works/mees/dinghy/ui/calibration/ProbeCalibrateScreen.kt
    - app/src/test/java/works/mees/dinghy/calibration/ProbeCalibrateHolderTest.kt
    - app/src/test/java/works/mees/dinghy/ui/files/FileBrowserHolderDeleteTest.kt
    - .planning/phases/09-calibration-maintenance/09-UAT.md
  modified:
    - app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/files/FileBrowserHolder.kt
    - app/src/test/java/works/mees/dinghy/ui/files/FileBrowserHolderTest.kt
    - docs/ui_design/CLAUDE.md

key-decisions:
  - "ProbeCalibrateHolder reads the TYPED manual_probe (ManualProbeObject.isActive/zPosition) off printerState rather than re-parsing JSON — the parser already surfaced it in 09-02"
  - "The // Z position bracket is collected from store.gcodeResponses (the raw Phase-8 D-04 stream), parsed via parseZPosition, cleared when is_active flips false so no stale bracket lingers on Idle/Accepted"
  - "The session offset is captured as the last live z_position on the Active→inactive edge (no separate ACCEPT confirmation field on the wire)"
  - "errorText is NOT cleared on the Active branch — a TESTZ rejection mid-session must persist as the toast, not be wiped by the next status diff"
  - "The shared internal intentColor() from CalibrationHubScreen is reused (no per-file copy) to avoid a same-package overload clash"

patterns-established:
  - "State-adaptive gutter with Back suppressed in the Active branch (T-09-06-02) — only Accept + Abort render mid-session"

requirements-completed: []  # ZCAL-01 stays Pending (on-device UAT); CALIB-05/CALIB-06 are closed by code here but not marked Complete until the 09-07 UAT gate.

# Metrics
duration: 18min
completed: 2026-06-03
---

# Phase 9 Plan 6: Probe-Calibrate Interactive Page + Files Delete-Gate Fix Summary

**The one stateful calibration page — a live manual-probe Z-calibrate session (Start → TESTZ jog → Accept/Abort → amber SAVE_CONFIG) — plus the Phase-7 deferred Files-delete defect fixed so during a print only the actively-printing file is undeletable.**

## Performance

- **Duration:** ~18 min
- **Tasks:** 2/2
- **Files modified/created:** 9

## Accomplishments

### Task 1 — ProbeCalibrateHolder + ProbeCalibrateScreen (CALIB-05 / D-01)

- **ProbeCalibrateHolder** (headless, ADR-0001, no Compose, no second throttle): `combine`s
  `store.printerState` + `store.capabilities` + an internal bracket StateFlow into a `ProbeCalibrateVm`.
  Page state (`Idle`/`Active`/`Accepted`) derives from `manual_probe.is_active` (Pattern 3); the live
  `z_position` hero comes straight off the typed `ManualProbeObject`. A second `scope.launch` collects
  the **un-throttled `store.gcodeResponses` SharedFlow** (the Phase-8 D-04 raw stream — the bracket is
  NOT a printerState field), runs each line through `parseZPosition`, and keeps the latest parseable
  bracket — **tolerating the real `// Z position: ?????? --> 7.624 <-- ??????` shape** (`??????` bounds
  → null, never a crash/blank). The bracket clears when the session ends. The Start command resolves via
  `probeCalibrateGate(caps)` (A3 — `PROBE_CALIBRATE` vs `Z_ENDSTOP_CALIBRATE` for probe-less printers).
  Dispatcher `Failure` events for this page's keys fold into a redacted `errorText`.
- **ProbeCalibrateScreen** (token-pure, ratio-only): Focus idle = `nozzle` glyph + prompt; active = the
  big GeistMono live-Z hero + the parsed bracket (`—` for unknown bounds) + nudge prompt; accepted = the
  captured offset + Save prompt. Field = the jog pad (disabled until Active): white TESTZ step pills
  (1/0.1/0.05/0.025 mm — setting intent) + blue Z▲/Z▼ firing `testZ(±step)`. **State-adaptive gutter:**
  Idle → Start (accent, gated command) + Back (go); Active → Accept (go) + Abort (red), **Back
  suppressed** (T-09-06-02); Accepted → Save (amber → ConfirmGuard restart gate → `saveConfig`) + Back.
- **ProbeCalibrateHolderTest GREEN (8/8):** Idle/Active off is_active, live z_position, a real gcode
  frame surfaces the parsed bracket, `??????` bounds → null without crashing, bracket clears on session
  end, Accept captures the offset → Accepted, start command via the probe-present gate, dispatcher
  Failure → redacted error.

### Task 2 — D-15 Files delete-gate fix (CALIB-06)

- **Both gates scoped** through the SAME host-tested `deleteAllowed(selectedPath, activePrintFilename,
  printState)` helper (09-03):
  - **FilesScreen (screen gate):** `deleteEnabled` now calls `deleteAllowed(selected.relativeFilename,
    printerState.printFilename, printerState.printState)`; the too-broad `!printingActive` gate and its
    now-unused `printingActive`/`PrintState` import are gone.
  - **FileBrowserHolder (holder gate):** `requestDeleteSelected()` rejects ONLY the actively-printing
    file via the same predicate (was a bare unconditional `Printing||Paused` block that made the screen
    fix invisible); a non-printing file now reaches `client.deleteFile(rootPrefixedPath)`. The error text
    narrowed to "Cannot delete the file that is currently printing."
- **FileBrowserHolderTest** updated from the old too-broad assertion to the scoped behavior; new
  **FileBrowserHolderDeleteTest GREEN** (matching file rejected mid-print, different idle file proceeds
  mid-print, idle proceeds).
- **docs/ui_design/CLAUDE.md** records the relaxed scoped-delete rule (supersedes "idle-only"). The
  bed-mesh Save-name keyboard carve-out cross-reference was already recorded by 09-05 (single source).
- **09-UAT.md** created mirroring 07-UAT format — the five manual-only verifications (bed-mesh heatmap +
  gfxinfo, Z_TILT_ADJUST converge, screws-tilt guided loop, Z-calibrate Accept→SAVE_CONFIG→spine
  recovers, and the D-15 delete-during-print scoping check).

## Verification

- `ProbeCalibrateHolderTest` 8/8 GREEN; `FilesDeleteGateTest` + `FileBrowserHolderDeleteTest` +
  `FileBrowserHolderTest` GREEN.
- Full `:app:testReleaseUnitTest` GREEN; `:app:assembleRelease` SUCCESSFUL (only pre-existing R8
  retrofit/okhttp warnings — out of scope).
- All Task-1 and Task-2 acceptance greps pass (gcodeResponses in holder; state-adaptive gutter; testZ;
  probeCalibrateGate; ConfirmGuard→saveConfig order; Active gutter Accept+Abort only; no raw hex in the
  screen; no Compose import in the holder; both delete gates call deleteAllowed; bare blocks gone;
  09-UAT.md present; docs rule recorded).

## Deviations from Plan

**1. [Plan inaccuracy — corrected] `deleteAllowed` lives in `ui/files/DeleteGate.kt`, not
`calibration/DeleteGate.kt`.** The plan's `read_first` referenced both `ui/files/DeleteGate.kt` and
`calibration/DeleteGate.kt`; the helper (with the `selectedPath, activePrintFilename, printState`
signature and its green `FilesDeleteGateTest`) is in `ui/files/`. Used the existing helper as-is — no
new file, same-package import in both gates. No behavior change.

**2. [Plan inaccuracy — corrected] `manual_probe` is a TYPED field, not a JsonObject the holder walks.**
The plan's Task-1 behavior implied reading `manualProbePageState(printerState.manualProbe)` (a
JsonObject helper). 09-02 already surfaces `manual_probe` as the typed `ManualProbeObject`
(`isActive`/`zPosition`/...) on `PrinterState.manualProbe`, so the holder reads the typed field directly
(`state.manualProbe?.isActive`). The pure `manualProbeActive(JsonObject)` / `parseZPosition` functions
remain the parse authority for the bracket. Cleaner and avoids re-serializing.

**3. [Rule 1 — bug avoided] errorText must NOT clear on the Active branch.** The first holder draft
cleared `latestError` whenever `is_active` was true, which wiped a TESTZ rejection toast on the very next
status diff (the `dispatcherFailureSurfacesRedactedError` test caught it). Removed the clear so a
mid-session rejection persists. Found during Task 1; fixed before commit `cd8731f`.

## Self-Check: PASSED

- Files created exist: ProbeCalibrateHolder.kt, ProbeCalibrateScreen.kt, ProbeCalibrateHolderTest.kt,
  FileBrowserHolderDeleteTest.kt, 09-UAT.md — all present.
- Commits exist: `cd8731f` (Task 1), `5be428a` (Task 2).
