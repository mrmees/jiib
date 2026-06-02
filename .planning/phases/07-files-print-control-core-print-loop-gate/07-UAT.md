# Phase 07 Ender 5 Plus Core Print-Loop UAT

## Status

PENDING - live Ender 5 Plus UAT has not been executed.

## Preconditions

- `07-VERIFICATION.md` automated release gate is PASS.
- `07-PERF.md` flox route-render and large-library perf gate is PASS.
- Browser remains closed during the core workflow.
- Use a safe gcode file and safe operating conditions.
- Use a safe throwaway file for delete testing.

## Environment

- **Timestamp:** PENDING
- **Printer:** Ender 5 Plus
- **Device/tablet:** PENDING
- **App commit/build:** PENDING
- **Moonraker host:** PENDING
- **Safe start file:** PENDING
- **Safe delete file:** PENDING

## Checklist

| Step | Requirement | Expected Evidence | Result | Notes |
|---|---|---|---|---|
| Connect | FILE-01/JOB-01 | App connects without browser fallback; Print Status shows live state | PENDING |  |
| Open Files | FILE-01 | Files route opens from drawer or terminal Files action | PENDING |  |
| Browse folders | FILE-01/FILE-02 | Compact rows, Up row, path chip, stable scrolling | PENDING |  |
| Thumbnail fallback | FILE-02 | Thumbnail-less gcode rows render fallback visuals and stay selectable | PENDING |  |
| Select file | FILE-03 | Focus preview shows selected filename and available metadata | PENDING |  |
| Start print | FILE-03 | `Print file` ConfirmGuard appears; confirming starts selected filename | PENDING |  |
| Monitor Status | JOB-01/JOB-02 | Print Status shows progress, temps, Z/layer/elapsed/remaining as available | PENDING |  |
| Pause | JOB-03 | Pause dispatches; pending clears only when printer reports paused | PENDING |  |
| Resume | JOB-03 | Resume dispatches; pending clears only when printer reports printing | PENDING |  |
| Graceful cancel | JOB-04 | Long press/accessibility `Cancel print`; ConfirmGuard `Cancel print` / `Keep printing`; terminal state observed | PENDING |  |
| Emergency Stop separation | JOB-04 | Stop remains separate emergency path and is not reused for graceful cancel | PENDING |  |
| Return to Files | FILE-01/FILE-04 | Files remains usable after terminal/cancel state | PENDING |  |
| Delete idle-only | FILE-04 | `Delete file` only selected-file Focus action; disabled while active; throwaway file removed only while idle | PENDING |  |
| Terminal Files | JOB-05 | Terminal state with filename shows Files action and routes to Files | PENDING |  |
| Restart current filename | JOB-05/FILE-03 | Restart uses current `print_stats.filename` when terminal state retains it | PENDING |  |
| Restart last-job fallback | JOB-05/FILE-03 | Restart uses LastJob filename if terminal state clears current filename | PENDING |  |

## Failure Recording

If any item fails:

- Record exact observed app state.
- Record printer state and filename.
- Record whether browser was used.
- Record reproduction steps.
- Do not mark the item passed.

## Deferred Defects (fix + verify in a future UAT round)

- **Delete gating is too broad.** → **FOLDED INTO PHASE 9 UAT (2026-06-02, per Matthew).**
  Tracked in `.planning/todos/pending/files-delete-gating-too-broad.md` (`resolves_phase: 09`).
  Summary: `FilesScreen` computes `deleteEnabled = selected != null && !printingActive`, where
  `printingActive` is the GLOBAL print state — so while any print runs, Delete is disabled for
  EVERY file, not just the one being printed. Desired: only block deleting the active
  `print_stats.filename`; other idle files stay deletable during a print. (Relaxes the original
  UI-SPEC "Delete is idle-only" rule — confirm the rule change when fixing.) Fix + verify happen
  in Phase 9; see the todo for the fix location and the Phase-9 UAT check.

## Gate Result

PENDING - Phase 7 live print-loop UAT is not complete.
