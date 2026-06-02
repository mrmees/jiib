---
phase: 07-files-print-control-core-print-loop-gate
plan: 01
subsystem: command-registry
tags: [moonraker, json-rpc, files, print-control, state]
requires:
  - phase: 06-command-reference-capability-matrix
    provides: command registry sidecars and drift tests
provides:
  - Phase 7 file and print-control JSON-RPC command specs
  - Runtime command catalog and printer-matrix evidence rows
  - pause_resume subscribe and reducer state-confirmation seam
affects: [files-ui, print-status, command-dispatch, printer-state]
tech-stack:
  added: []
  patterns: [typed CommandSpec registry entries, sidecar-backed runtime commands, state-confirmed print actions]
key-files:
  created:
    - app/src/test/java/works/mees/dinghy/command/CommandRegistryFilesTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/net/JsonRpc.kt
    - app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterState.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt
    - app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt
    - docs/commands/catalog.json
    - docs/commands/printer-matrix.json
key-decisions:
  - "server.files.roots and server.files.list remain reference-only; only commands with Phase 7 runtime send paths were registered."
  - "Print-control acknowledgements are transport success only; UI holders must confirm success from print_stats, virtual_sdcard, and pause_resume state."
patterns-established:
  - "File delete commands accept root-prefixed path args while print start and metadata commands use filenames relative to gcodes."
  - "pause_resume.is_paused is retained in PrinterState so Wave 4 controls do not need to invent confirmation state."
requirements-completed: [FILE-01, FILE-02, FILE-03, FILE-04, JOB-03, JOB-04, JOB-05]
duration: 35min
completed: 2026-06-02
---

# Phase 07 Plan 01: Runtime Command Registry Summary

**Moonraker file and print-control commands are typed, sidecar-backed, and connected to state-confirmed print-control truth.**

## Performance

- **Duration:** 35 min
- **Started:** 2026-06-02T14:12:00Z
- **Completed:** 2026-06-02T14:47:43Z
- **Tasks:** 3
- **Files modified:** 11

## Accomplishments

- Added typed registry specs for directory browse, thumbnails, delete, start, pause, resume, and graceful cancel.
- Updated command catalog and printer matrix evidence for every new runtime command while leaving roots/list reference-only.
- Added `pauseResumePaused` and `pause_resume` subscription coverage so Print Status controls can clear pending state from reducer truth.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Phase 7 JSON-RPC constants and registry specs** - `4b38d93` (feat)
2. **Task 2: Update command catalog and printer matrix evidence** - `a840b73` (docs)
3. **Task 3: Lock pause/resume state confirmation in reducer and subscribe set** - `c06bd5d` (feat)

## Files Created/Modified

- `app/src/main/java/works/mees/dinghy/net/JsonRpc.kt` - Phase 7 Moonraker method constants.
- `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` - Typed file and print-control `CommandSpec` entries.
- `app/src/test/java/works/mees/dinghy/command/CommandRegistryFilesTest.kt` - Registry params, availability, and state-confirmation tests.
- `docs/commands/catalog.json` - Runtime registration and acceptance semantics for Phase 7 commands.
- `docs/commands/printer-matrix.json` - Availability evidence rows for both printers.
- `app/src/main/java/works/mees/dinghy/state/PrinterState.kt` - `pauseResumePaused` reducer truth.
- `app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt` - `pause_resume.is_paused` merge handling.
- `app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt` - `pause_resume` in the subscribe set.
- `app/src/test/java/works/mees/dinghy/state/PrinterStateReducerTest.kt` - pause, resume, cancel transition coverage.
- `app/src/test/java/works/mees/dinghy/state/DeriveCapabilitiesTest.kt` - subscribe-set assertion for `pause_resume`.

## Decisions Made

- `server.files.roots` and `server.files.list` remain catalog reference rows because no Phase 7 call site dispatches them.
- `printer.print.start`, pause, resume, and cancel are modeled as state-confirmed actions; a JSON-RPC result does not equal user-visible success.

## Deviations from Plan

None - plan executed as written.

## Issues Encountered

None.

## Verification

`/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests *CommandRegistryFilesTest --tests works.mees.dinghy.state.PrinterStateReducerTest --tests works.mees.dinghy.state.DeriveCapabilitiesTest --tests *CommandCatalogDriftTest --no-daemon" | tr -d '\r'`

Result: passed, `BUILD SUCCESSFUL in 33s`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Wave 2 can build file browser models and holders against typed command specs and reducer-confirmed pause/resume state.

## Self-Check: PASSED

- All Phase 7 runtime commands are typed and sidecar-backed.
- Registry drift tests pass.
- File and print-control params match the planned Moonraker request shapes.
- Pause/resume/cancel confirmation is covered by reducer and subscribe-set tests before UI execution.

---
*Phase: 07-files-print-control-core-print-loop-gate*
*Completed: 2026-06-02*
