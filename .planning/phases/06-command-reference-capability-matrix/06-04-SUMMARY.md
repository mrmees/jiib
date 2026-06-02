---
phase: 06-command-reference-capability-matrix
plan: 04
subsystem: documentation
tags: [klipper, moonraker, spoolman, command-catalog, capability-matrix]
requires:
  - phase: 06-command-reference-capability-matrix
    provides: [registry sidecar drift tests and CommandRegistry migration from 06-03]
provides:
  - Comprehensive Klipper G-Code, Moonraker API, and Spoolman API command catalog
  - Machine-readable catalog.json with stable KGC/MR/SPM IDs and upstream URLs
  - Machine-readable E5/E3 printer matrix with live capture metadata and command availability rows
  - Drift tests that enforce catalog shape and registry predicate evidence from JSON sidecars
affects: [command-reference, capability-gating, future-ui-planning, docs]
tech-stack:
  added: []
  patterns: [JSON sidecar enforcement, TDD drift guards, live-probe provenance docs]
key-files:
  created:
    - docs/commands/klipper-gcode.md
    - docs/commands/moonraker-api.md
    - docs/commands/spoolman-api.md
    - docs/commands/printer-availability-matrix.md
  modified:
    - docs/commands/catalog.json
    - docs/commands/printer-matrix.json
    - docs/moonraker-capabilities.md
    - app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt
    - app/src/test/java/works/mees/dinghy/command/CommandCatalogDriftTest.kt
key-decisions:
  - "Treat printer.gcode.help as positive evidence only; absence is not unsupported proof."
  - "Reconcile FORCE_MOVE availability from gcode-help positives instead of a force_move status object."
  - "Keep public LOAD_FILAMENT/UNLOAD_FILAMENT registry gates and document private underscore helpers as not-on-printers caveats."
patterns-established:
  - "Catalog entries carry stable id/catalog_id, source_api, params, availability, runtime_registry, and upstream_url fields."
  - "Printer evidence is committed in JSON sidecars; tests consume JSON, not Markdown prose."
requirements-completed: [PHASE-06-REFERENCE]
duration: 16m 24s
completed: 2026-06-02
---

# Phase 06 Plan 04: Command Reference Capability Matrix Summary

**Source-cited Klipper, Moonraker, and Spoolman command catalog with live E5/E3 printer evidence and JSON-enforced registry predicate drift checks**

## Performance

- **Duration:** 16m 24s
- **Started:** 2026-06-02T11:45:08Z
- **Completed:** 2026-06-02T12:01:32Z
- **Tasks:** 3
- **Files modified:** 9

## Accomplishments

- Refreshed approved read-only Moonraker evidence for Ender 5 Plus and Ender 3 Pro, including versions, components, objects, macros, endpoint support, and gcode-help positives.
- Expanded `catalog.json` to 324 stable command rows: 158 Klipper G-Code, 128 Moonraker, and 38 Spoolman entries.
- Created human-facing command reference docs for Klipper G-Code, Moonraker API, Spoolman API, and printer availability.
- Added drift tests that enforce catalog shape, planned v1 reference rows, JSON-only sidecar coverage, and registry command availability rows.

## Task Commits

1. **Task 1: Refresh live E5/E3 command availability evidence** - `543899c` (docs)
2. **Task 2 RED: Catalog shape guard** - `5db5548` (test)
3. **Task 2 GREEN: Comprehensive catalog docs and sidecar** - `fdc2987` (feat)
4. **Task 3 RED: Matrix availability guard** - `d4cbc0e` (test)
5. **Task 3 GREEN: Printer availability reconciliation** - `5c60d2d` (feat)

## Files Created/Modified

- `docs/commands/klipper-gcode.md` - Human-facing Klipper sendable G-Code catalog with KGC IDs and upstream links.
- `docs/commands/moonraker-api.md` - Human-facing Moonraker JSON-RPC/REST catalog with MR IDs and upstream links.
- `docs/commands/spoolman-api.md` - Human-facing Spoolman REST/QR catalog with SPM IDs and upstream links.
- `docs/commands/printer-availability-matrix.md` - Human-facing E5/E3 command availability and caveat table.
- `docs/commands/catalog.json` - Machine-readable command catalog.
- `docs/commands/printer-matrix.json` - Machine-readable live printer evidence and command availability rows.
- `docs/moonraker-capabilities.md` - Capture provenance and capability caveats.
- `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` - FORCE_MOVE predicate reconciled to gcode-help evidence.
- `app/src/test/java/works/mees/dinghy/command/CommandCatalogDriftTest.kt` - JSON sidecar drift guards.

## Decisions Made

- `printer.gcode.help` is positive evidence only; missing help output is not treated as command absence.
- `FORCE_MOVE` gates on `gcode_command_present:FORCE_MOVE` because both printers expose it in gcode help and neither exposes a `force_move` status object.
- `LOAD_FILAMENT` and `UNLOAD_FILAMENT` remain public macro predicates because only private underscore helpers were observed on the printers.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Reconciled FORCE_MOVE predicate source**
- **Found during:** Task 2 and Task 3 drift verification
- **Issue:** Live captures showed `FORCE_MOVE` in gcode-help positives on both printers, but no `force_move` object existed.
- **Fix:** Changed `CommandRegistry.forceMove` to `AvailabilityPredicate.GcodeCommandPresent("FORCE_MOVE")`, removed the interim `force_move` object caveat, and documented gcode-help as positive-only evidence.
- **Files modified:** `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt`, `docs/commands/printer-matrix.json`, `docs/commands/printer-availability-matrix.md`, `docs/moonraker-capabilities.md`
- **Verification:** `CommandCatalogDriftTest` passed with `FORCE_MOVE` backed by gcode-help evidence.
- **Committed in:** `5c60d2d`

**2. [Rule 1 - Bug] Corrected filament macro caveat evidence**
- **Found during:** Task 3 matrix reconciliation
- **Issue:** The interim caveat overstated private unload macro evidence on Ender 3 Pro.
- **Fix:** Recorded that Ender 5 Plus has `_LOAD_FILAMENT` and `_UNLOAD_FILAMENT`, while Ender 3 Pro has `_LOAD_FILAMENT` only; public `LOAD_FILAMENT` and `UNLOAD_FILAMENT` remain not-on-printers.
- **Files modified:** `docs/commands/printer-matrix.json`, `docs/commands/printer-availability-matrix.md`, `docs/moonraker-capabilities.md`
- **Verification:** JSON sidecar checks and `CommandCatalogDriftTest` passed.
- **Committed in:** `5c60d2d`

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug)
**Impact on plan:** Both fixes corrected predicate/evidence accuracy without adding runtime static per-printer behavior.

## Issues Encountered

- TDD RED tests failed as expected before catalog and matrix implementation.
- No auth gates or package install gates occurred.
- Metadata close-out caveats: `state.update-progress` reported no progress field in the current `STATE.md`, and `requirements.mark-complete PHASE-06-REFERENCE` reported that the requirement ID is not defined in `REQUIREMENTS.md`. `state.advance-plan`, metric recording, session recording, decisions, and `roadmap.update-plan-progress` completed.

## Known Stubs

- `docs/moonraker-capabilities.md:51` contains the pre-existing phrase `"Spool" placeholder` for a future UI surface. This is documentation context, not a new code/UI stub, and it does not block the command catalog or matrix goal.

## User Setup Required

None - no external service configuration required.

## Verification

- `python3 -m json.tool docs/commands/catalog.json >/dev/null` - passed.
- `python3 -m json.tool docs/commands/printer-matrix.json >/dev/null` - passed.
- Catalog required-field and artifact check - passed (`KGC-`: 158, `MR-`: 128, `SPM-`: 38).
- `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests *CommandCatalogDriftTest --no-daemon" | tr -d '\r'` - passed.

## Next Phase Readiness

Future phases can plan against stable catalog IDs, source-cited command docs, and JSON sidecar evidence. Runtime gating remains live through `Capabilities`; the E5/E3 matrix is dev-facing evidence only.

## Self-Check: PASSED

- Verified created/modified files exist.
- Verified task commits exist: `543899c`, `5db5548`, `fdc2987`, `d4cbc0e`, `5c60d2d`.

---
*Phase: 06-command-reference-capability-matrix*
*Completed: 2026-06-02*
