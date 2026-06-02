---
phase: 06-command-reference-capability-matrix
plan: 01
subsystem: testing
tags: [command-registry, capability-matrix, moonraker, klipper, junit]

requires:
  - phase: 05-core-print-control-panels
    provides: PrinterCommands builders, CommandDispatcher behavior, capability derivation, live handshake tests
provides:
  - Parseable command catalog and E5/E3 matrix sidecars under docs/commands/
  - RED registry drift and byte-identical gcode guard tests
  - Extended capability, dispatcher, and handshake regression guards for the planned registry refactor
affects: [phase-06-plan-02, phase-06-plan-03, phase-06-plan-04, phase-06-plan-05]

tech-stack:
  added: []
  patterns:
    - Machine-readable docs sidecars parsed by host tests
    - RED guard tests that intentionally name future registry APIs before production implementation

key-files:
  created:
    - docs/commands/catalog.json
    - docs/commands/printer-matrix.json
    - app/src/test/java/works/mees/dinghy/command/CommandCatalogDriftTest.kt
    - app/src/test/java/works/mees/dinghy/command/CommandRegistryGcodeTest.kt
  modified:
    - app/src/test/java/works/mees/dinghy/state/DeriveCapabilitiesTest.kt
    - app/src/test/java/works/mees/dinghy/command/CommandDispatcherTest.kt
    - app/src/test/java/works/mees/dinghy/net/HandshakeTest.kt

key-decisions:
  - "Wave 1 stays RED-only for registry/capability APIs; production CommandRegistry and Capabilities.objects/hasObject are deferred to Plan 02."
  - "docs/commands/*.json sidecars are the enforcement source; Markdown remains human-facing reference only."

patterns-established:
  - "Registry drift tests read docs/commands/catalog.json and printer-matrix.json via MoonrakerJson from repository-relative filesystem paths."
  - "Registry gcode tests compare registry params against PrinterCommands output instead of duplicating gcode literals."

requirements-completed: [PHASE-06-REFERENCE]

duration: 7min
completed: 2026-06-02
---

# Phase 06 Plan 01: Wave 1 Guardrail Layer Summary

**Parseable command sidecars plus RED guard tests for command-registry drift, byte-identical gcode wrapping, live capability predicates, dispatcher behavior, and handshake order**

## Performance

- **Duration:** 7 min
- **Started:** 2026-06-02T05:09:58Z
- **Completed:** 2026-06-02T05:16:36Z
- **Tasks:** 3
- **Files modified:** 7 task files plus this summary

## Accomplishments

- Seeded `docs/commands/catalog.json` with representative Phase-1-to-5 Moonraker and Klipper command IDs, transports, tiers, upstream URLs, semantics, and predicates.
- Seeded `docs/commands/printer-matrix.json` with Ender 5 Plus and Ender 3 capture metadata, objects, macros, components, predicate evidence, and explicit `not_on_printers` entries.
- Added RED tests for registry-to-catalog drift and registry-to-`PrinterCommands` byte-identical gcode wrapping.
- Extended existing capability, dispatcher, and handshake tests so Plan 02 cannot weaken raw object gating, dispatch semantics, or live-proven handshake order.

## Task Commits

Each task was committed atomically:

1. **Task 1: Seed parseable catalog and matrix sidecars** - `6b1864a` (docs)
2. **Task 2: Add registry drift and byte-identical gcode tests** - `9ca4c31` (test)
3. **Task 3: Extend capability, dispatcher, and handshake regression guards** - `d345d49` (test)

## Files Created/Modified

- `docs/commands/catalog.json` - Machine-readable command catalog skeleton for current outbound Moonraker methods and Klipper G-Code builders.
- `docs/commands/printer-matrix.json` - Machine-readable E5/E3 availability evidence and explicit missing-printer escape hatches.
- `app/src/test/java/works/mees/dinghy/command/CommandCatalogDriftTest.kt` - RED drift guard parsing JSON sidecars and referencing planned registry predicates.
- `app/src/test/java/works/mees/dinghy/command/CommandRegistryGcodeTest.kt` - RED byte-identical registry wrapper guard against `PrinterCommands`.
- `app/src/test/java/works/mees/dinghy/state/DeriveCapabilitiesTest.kt` - Added raw object retention and `hasObject()` guard.
- `app/src/test/java/works/mees/dinghy/command/CommandDispatcherTest.kt` - Added planned registry dispatch overload guards for timeouts, `RpcError`, and redaction.
- `app/src/test/java/works/mees/dinghy/net/HandshakeTest.kt` - Strengthened D-05/D-12 handshake order assertions.

## Decisions Made

- Kept this wave test-first and RED-only. No production `CommandRegistry`, registry dispatch overload, or `Capabilities.objects` implementation was added in this plan.
- Used JSON sidecars for enforcement. The new tests do not parse `docs/commands/*.md`.

## Verification

- `python3 -m json.tool docs/commands/catalog.json >/dev/null && python3 -m json.tool docs/commands/printer-matrix.json >/dev/null` - PASS.
- `grep -v '^#' docs/commands/catalog.json | grep -c 'MR-printer.gcode.script'` - PASS, count `1`.
- `grep -v '^#' docs/commands/printer-matrix.json | grep -c 'ender5plus'` - PASS, count `1`.
- `grep -v '^#' docs/commands/printer-matrix.json | grep -c 'ender3'` - PASS, count `4`.
- `rg` for required test names - PASS: `registryCatalogIdsExistInCatalogJson`, `predicateReferencesAreBackedByMatrixEvidence`, `gcodeRegistryWrapsPrinterCommandsByteIdentically`, `rawObjectNamesAreRetainedAndHasObjectWorks`, and registry dispatcher guard names exist.
- `rg "docs/commands/.*\\.md|\\.md\\\"|\\.md'" ...` over the new registry tests - PASS, no matches.
- `cmd.exe /c "E:\Android\gw.bat :app:compileReleaseKotlin --no-daemon"` - PASS.
- `cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests *CommandCatalogDriftTest --tests *CommandRegistryGcodeTest --tests *DeriveCapabilitiesTest --tests *CommandDispatcherTest --tests *HandshakeTest --no-daemon" || true` - EXPECTED RED. Fails at `:app:compileReleaseUnitTestKotlin` for missing planned APIs: `CommandRegistry`, `AvailabilityPredicate`, `CommandSpec`, registry arg types, and `Capabilities.objects` / `hasObject()`.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

The targeted test commands fail at test compilation by design because this wave creates RED guards before Plan 02 implements the registry and capability production APIs.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None. The RED references are intentional test guards, not runtime stubs.

## Next Phase Readiness

Plan 02 can implement the production `CommandRegistry`, `CommandSpec`, `AvailabilityPredicate`, registry dispatcher overloads, and `Capabilities.objects` / `hasObject()` against these committed RED gates.

## Self-Check: PASSED

- All key task files and this summary exist on disk.
- Task commits found in git history: `6b1864a`, `9ca4c31`, `d345d49`.
- Both JSON sidecars parse with `python3 -m json.tool`.

---
*Phase: 06-command-reference-capability-matrix*
*Completed: 2026-06-02*
