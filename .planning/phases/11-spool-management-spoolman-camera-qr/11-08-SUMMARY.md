---
phase: 11-spool-management-spoolman-camera-qr
plan: 08
subsystem: spool
tags: [wave-5, integration, print-start-gate, d-01, warn-only, gcode-prefilter, d-04, change-during-print, d-10, d-16]
requires:
  - "11-03: evaluatePrintStartGate(activeSpool, status, fetchFailed, file) → List<SpoolWarning> (D-01 warn-only, never blocks)"
  - "11-06: SpoolHolder (SpoolPickerState + filter mutators) + SpoolScreen (Dest.Spool picker) + the post_spool_id set-active path"
  - "11-02: FilePreviewMetadata.filamentType[]/filamentColors[]/filamentWeightTotal arrays the gate + prefilter consume"
  - "11-04: AppContainer.activeSpool/spoolmanPresent + SpineHandle.spoolmanClient the Files gate reads"
provides:
  - files-warn-gate: "FilesScreen folds the warn-only PrintStartGate into the print-confirm: non-empty warnings → amber SpoolWarningGuard (Pick spool/Scan/Print anyway/Back), clean pass → unchanged Print file confirm; NEVER hard-blocks (D-01)"
  - gcode-prefilter: "SpoolHolder.seedPrefilter(SpoolPrefilterSeed) seeds material-family chips (D-05) + nearest-palette color HINT (D-04/D-06, not strict); carried via nav.spoolPrefilter from the Files Pick-spool action"
  - change-during-print: "SpoolHolder.setActiveSpool(dispatcher, id) re-points active-spool tracking mid-print with NO print-state gating; D-10 facade reconciles the card/picker to the new id"
affects:
  - "11-09 (live UAT) exercises the gate, prefilter, and change-during-print on a real printer"
tech-stack:
  added: []
  patterns:
    - "ConfirmGuard amber proceed-at-peril full-screen surface extended to a 4-action SpoolWarningGuard (Pick/Scan/Print-anyway/Back) — the same heat-soft opaque-backdrop grammar"
    - "PrintStatusScreen LaunchedEffect(activeSpoolId) getSpool detail-resolve idiom reused in FilesScreen for the gate's active-spool detail"
    - "Transient nav seed (macroPopupFor/scanActive precedent) for the one-time gcode prefilter — reset on recovery Splash"
key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt
    - app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt
decisions:
  - "The warn-only gate renders a NEW SpoolWarningGuard (not ConfirmGuard) because the D-01 contract needs FOUR actions (Pick spool/Scan/Print anyway/Back) and ConfirmGuard is a 2-button confirm/cancel. SpoolWarningGuard copies ConfirmGuard's amber heat-soft opaque-backdrop full-screen grammar verbatim; the clean-pass path still uses the unchanged ConfirmGuard"
  - "The gate computes warnings ONLY when spoolmanPresent && activeSpoolStatus != null && selectedPreview != null — a not-yet-loaded preview is a clean pass (no fabricated FilePreviewMetadata; it has no no-arg constructor and the gate would have nothing to compare)"
  - "FilesScreen takes the gate inputs as plain params (spoolmanPresent/activeSpoolStatus/spoolmanClient + onPickSpoolForFile(List,List)/onScanSpool) and resolves the active-spool DETAIL itself via the same LaunchedEffect(activeSpoolId) getSpool idiom PrintStatusScreen uses — a rejected read surfaces FetchFailed (amber), never a crash"
  - "The gcode prefilter color is a HINT not a hard filter (D-04/D-06): seedPrefilter maps the file's first valid color to the nearest fixed-palette swatch (squared-RGB distance) and pre-selects it for display (colorSwatchHex), but leaves colorFilamentIds null so NO rows are filtered out by color"
  - "Change-during-print is formalized as SpoolHolder.setActiveSpool(dispatcher, id) (greppable, documented no-print-state-gating) — the Load action and any change action route through it; it only dispatches post_spool_id, never a print-control command, so the running print is untouched (the Files Delete-blocks-all defect is deliberately NOT copied)"
  - "The prefilter seed rides a transient nav field (ShellNavState.spoolPrefilter, reset in resetTransient like scanActive/macroPopupFor) and is applied ONCE by SpoolScreen then cleared via onPrefilterConsumed, so a later manual reopen is unseeded"
metrics:
  duration: ~25m
  completed: 2026-06-04
  tasks: 2
  files: 5
---

# Phase 11 Plan 08: Wire the Decision Logic into the Live Flows Summary

Folded the headless Wave-1 decision logic into the live UI: the warn-only `PrintStartGate` (11-03) now hooks the Files print-confirm as amber proceed-at-peril text the user taps past in one action (never blocking; a clean pass leaves the normal confirm UNCHANGED), the picker pre-filters by the selected file's material family + color hint when opened from a Files spool-warning (D-04 gcode-aware prefilter), and change-during-print re-points the active spool mid-print without interrupting the running print (D-10 reconcile). Every collaborator already existed — this is the integration wave that connects them. `:app:assembleDebug` and the full `:app:testDebugUnitTest` both pass with no regressions.

## What Was Built

### Task 1 — Warn-only print-start gate folds into the Files confirm (commit `7f499e2`)
- `FilesScreen.kt`: new gate inputs (`spoolmanPresent`, `activeSpoolStatus`, `spoolmanClient`, `onPickSpoolForFile(filamentType, filamentColors)`, `onScanSpool`). A `LaunchedEffect(activeSpoolId, spoolmanPresent, spoolmanClient)` resolves the active-spool DETAIL via the session `SpoolmanClient.getSpool` (the PrintStatusScreen idiom) — a rejected/absent read sets `spoolFetchFailed` so the gate surfaces a `FetchFailed` amber warning, never a crash.
- The gate runs **only** when `spoolmanPresent && activeSpoolStatus != null && selectedPreview != null`, calling `evaluatePrintStartGate(...)` (11-03). A non-empty list renders the new `SpoolWarningGuard` (amber `Intent.Warn` proceed-at-peril lines + **Pick spool / Scan / Print anyway / Back**); an empty list shows the existing `Print file` `ConfirmGuard` **UNCHANGED**.
- `SpoolWarningGuard` copies `ConfirmGuard`'s amber `heat-soft` opaque-backdrop full-screen grammar, but offers the four D-01 actions. **It NEVER hard-blocks** — "Print anyway" is one tap straight to `holder.requestStartSelected()`. "Pick spool" forwards the file's `filamentType[]`/`filamentColors[]` to `onPickSpoolForFile` (the D-04 seed). Warning body is `fsSp(18f)` (above the 15sp floor, matching the ConfirmGuard scale, D-16); all color via `LocalTokens` (THEME-01).
- `AppShell.kt`: threads `spoolEnabled`/`activeSpoolStatus`/`spoolmanClient` into the `Dest.Files` arm; the gate's Pick-spool seeds `nav.spoolPrefilter` (D-04) and opens `Dest.Spool`; Scan opens the QR sub-surface.
- `ShellNavState.kt`: a transient `spoolPrefilter: SpoolPrefilterSeed?` (reset in `resetTransient` alongside `scanActive`/`macroPopupFor`).

### Task 2 — gcode-aware picker prefilter + change-during-print mutator (commit `4bc7e60`)
- `SpoolHolder.kt`: `SpoolPrefilterSeed(filamentType, filamentColors)` + `seedPrefilter(seed)` — seeds the material-family chip(s) from `filamentType` (D-05, de-duped case-insensitively) and surfaces the file's first valid color as a **HINT**: `nearestPaletteSwatch(hex)` (squared-RGB distance to the fixed palette) pre-selects a display swatch (`colorSwatchHex`) while leaving `colorFilamentIds = null` so **no rows are filtered out by color** (D-04/D-06 hint-not-strict). The seed REPLACES the current filters (clearable one-time default); an empty seed applies no prefilter (T-11-08-03).
- `SpoolHolder.kt`: `setActiveSpool(dispatcher, id)` — the change-during-print path. Dispatches `post_spool_id {spool_id}` only (the same set-active path the picker Load uses), with **NO print-state gating** (the change is allowed mid-print — the whole point), so the running print is untouched. Moonraker reconciles via `notify_active_spool_set` and the holder's upstream collector flips the "Loaded" mark to the new id (D-10; card/picker reconcile, don't clobber).
- `SpoolScreen.kt`: accepts the `prefilter`/`onPrefilterConsumed` pair — a `LaunchedEffect(holder, prefilter)` applies the seed ONCE then clears it. The Load action now routes through `holder.setActiveSpool(dispatcher, id)`.

## Verification

- `:app:compileDebugKotlin` → BUILD SUCCESSFUL (after each task).
- `:app:assembleDebug` → BUILD SUCCESSFUL — the gate + prefilter + change-during-print land in the live route graph and the APK packages.
- `:app:testDebugUnitTest` (full) → BUILD SUCCESSFUL, no regressions.
- Grep gates:
  - `FilesScreen.kt`: `evaluatePrintStartGate` (line 137) precedes `requestStartSelected`; the `SpoolWarningGuard` path's "Print anyway" reaches `requestStartSelected()` — no hard-block/early-return-blocking-print path exists (the only block-shaped branch is the clean-pass `ConfirmGuard`, which itself proceeds on confirm).
  - No hardcoded `.sp` in either touched UI file (`FilesScreen.kt`/`SpoolScreen.kt`) — every `.sp` sits on an `fsSp(...)` call; the warning body is `fsSp(18f)`.
  - `SpoolHolder.setActiveSpool` has **no** `printState`/`print_state` guard (change-during-print is ungated).
  - The prefilter reads `seed.filamentType`/`seed.filamentColors`; the color path sets `colorSwatchHex` but leaves `colorFilamentIds = null` (hint, not hard filter).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] The D-01 gate needed a 4-action surface; ConfirmGuard is 2-action**
- **Found during:** Task 1 (the plan says "surface them in the SAME ConfirmGuard", but `ConfirmGuard` is a fixed confirm/cancel pair and the D-01 contract requires four actions — Pick spool / Scan / Print anyway / Back).
- **Issue:** Reusing `ConfirmGuard` verbatim could only offer two of the four required actions, breaking the D-01 action set.
- **Fix:** Added a `SpoolWarningGuard` private composable that copies `ConfirmGuard`'s exact amber `heat-soft` opaque-backdrop full-screen grammar (the G-4 opaque-backdrop lesson) but lays out the four D-01 actions in two rows. The clean-pass path still uses the unchanged `ConfirmGuard`. This honors the plan's intent (same visual grammar, same `message`-slot file-details, amber proceed-at-peril) while satisfying the D-01 four-action requirement.
- **Files modified:** `FilesScreen.kt`.
- **Commit:** `7f499e2`.

**2. [Rule 2 - Missing functionality] FilesScreen had no path to the active-spool detail / gate inputs**
- **Found during:** Task 1 (the gate needs the active `SpoolmanSpool` detail, the `SpoolmanStatus`, and the `spoolmanPresent` gate, none of which FilesScreen received).
- **Issue:** Without these, the gate could not be evaluated.
- **Fix:** Added plain params to `FilesScreen` (`spoolmanPresent`/`activeSpoolStatus`/`spoolmanClient` + the two callbacks) — all defaulted so existing call sites/tests are unaffected — and resolved the active-spool DETAIL inside FilesScreen via the same `LaunchedEffect(activeSpoolId)` `getSpool` idiom PrintStatusScreen uses. `AppShell` supplies the live values off the already-collected `spoolEnabled`/`spoolmanClient`/`activeSpoolFlow`.
- **Files modified:** `FilesScreen.kt`, `AppShell.kt`, `ShellNavState.kt`.
- **Commit:** `7f499e2`.

## Known Stubs

None. The gate, prefilter, and change-during-print are wired to live flows (`AppContainer.activeSpool`/`spoolmanPresent`/`currentSpoolmanClient`, the session dispatcher), not mock/empty data. (Hands-on live verification of the three behaviors on a real printer is plan **11-09**'s UAT, as the plan's threat register T-11-08-02 prescribes.)

## Threat Flags

None. The two trust boundaries are mitigated as the register prescribes: the gate is warn-only with an always-available one-tap "Print anyway" (T-11-08-01); `setActiveSpool` only dispatches `post_spool_id` with no print-state gating, re-pointing tracking without touching the print (T-11-08-02); an empty/malformed prefilter seed applies no filter and never crashes (T-11-08-03). No new network endpoints, auth paths, or schema surface — only the existing proxy reads + `post_spool_id` write.

## Self-Check: PASSED

- Files modified: `FilesScreen.kt`, `SpoolHolder.kt`, `SpoolScreen.kt`, `AppShell.kt`, `ShellNavState.kt` — all FOUND.
- Commits: `4bc7e60`, `7f499e2` — both FOUND in git log.
- `:app:assembleDebug` + full `:app:testDebugUnitTest` BUILD SUCCESSFUL; the gate precedes `requestStartSelected` with no hard-block path; no hardcoded `.sp`; `setActiveSpool` ungated by print state.
