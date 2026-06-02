---
phase: 06-command-reference-capability-matrix
plan: 02
subsystem: command-registry
tags: [kotlin, moonraker, json-rpc, command-registry, capabilities]

requires:
  - phase: 06-command-reference-capability-matrix/06-01
    provides: RED guard tests and command sidecar skeletons
provides:
  - Plain Kotlin command spec and registry model
  - Registry entries for current outbound Moonraker and PrinterCommands-backed operations
  - Thin registry dispatch/request helpers
  - Live Capabilities object/component predicate helpers
affects: [phase-06-command-registry, phase-07-files-print-control, phase-08-macros-console, phase-09-calibration]

tech-stack:
  added: []
  patterns:
    - Headless command specs with typed argument payload builders
    - Registry gcode entries wrapping PrinterCommands byte-identically
    - Live Capabilities predicates backed by objects.list and server.info.components

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/command/CommandSpec.kt
    - app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt
    - app/src/main/java/works/mees/dinghy/command/CommandDispatchExtensions.kt
  modified:
    - docs/commands/catalog.json
    - app/src/main/java/works/mees/dinghy/state/Capabilities.kt
    - app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt
    - app/src/test/java/works/mees/dinghy/command/CommandCatalogDriftTest.kt
    - app/src/test/java/works/mees/dinghy/state/DeriveCapabilitiesTest.kt

key-decisions:
  - "Registry entries use unique operation-level catalog IDs while retaining generic upstream catalog rows."
  - "JsonRpcClient registry helper rejects non-JsonRpc specs; gcode specs continue through CommandDispatcher."

patterns-established:
  - "CommandSpec<P>: catalog ID, transport, method, dispatch key, params builder, availability predicate, and semantics."
  - "AvailabilityPredicate leaves are drift-checked against printer-matrix evidence."
  - "Capabilities retains exact live object/component names while preserving existing typed helpers."

requirements-completed: [PHASE-06-REFERENCE]

duration: 10min
completed: 2026-06-02
---

# Phase 6 Plan 02: Command Registry Foundation Summary

**Headless command registry with byte-identical PrinterCommands wrappers and live object/component capability predicates**

## Performance

- **Duration:** 10 min
- **Started:** 2026-06-02T05:20:22Z
- **Completed:** 2026-06-02T05:30:27Z
- **Tasks:** 3
- **Files modified:** 8

## Accomplishments

- Added plain Kotlin command model types, availability predicates, command semantics, and a `CommandRegistry` covering current outbound Phase 1-5 operations.
- Registered gcode actions through `PrinterCommands.*` plus `PrinterCommands.scriptParams()` so clamping and mode-safety stay in the proven builders.
- Added thin dispatcher and JSON-RPC request helpers without changing existing dispatcher/client internals.
- Extended `Capabilities` with exact raw `objects`, exact `components`, `hasObject()`, and `hasComponent()` while preserving existing helpers.

## Task Commits

1. **Task 1: Define command spec types and registry entries** - `e5d1195` (feat)
2. **Task 2: Add thin registry dispatch and request helpers** - `ce2b138` (feat)
3. **Task 3: Extend live capabilities with raw objects and predicates** - `c58b84d` (feat)

## Files Created/Modified

- `app/src/main/java/works/mees/dinghy/command/CommandSpec.kt` - Plain Kotlin command transport, predicate, semantics, and spec model.
- `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` - Catalog-linked current outbound registry entries and typed args.
- `app/src/main/java/works/mees/dinghy/command/CommandDispatchExtensions.kt` - Thin dispatcher and JSON-RPC client helpers.
- `docs/commands/catalog.json` - Missing operation-level catalog IDs for registry entries.
- `app/src/main/java/works/mees/dinghy/state/Capabilities.kt` - Raw object/component retention and exact predicate helpers.
- `app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt` - Populates raw object/component sets.
- `app/src/test/java/works/mees/dinghy/command/CommandCatalogDriftTest.kt` - Duplicate registry catalog ID guard.
- `app/src/test/java/works/mees/dinghy/state/DeriveCapabilitiesTest.kt` - Component retention and lookup guard.

## Verification

- RED baseline: targeted Task 1 guard command failed before implementation with unresolved `CommandRegistry`, `CommandSpec`, `AvailabilityPredicate`, dispatch overload, and capability predicate symbols.
- Task 1 targeted: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests *CommandCatalogDriftTest --tests *CommandRegistryGcodeTest --no-daemon" | tr -d '\r'` - PASS.
- Task 2 targeted: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests *CommandDispatcherTest --no-daemon" | tr -d '\r'` - PASS.
- Task 2 acceptance: `grep -R "M112" app/src/main/java/works/mees/dinghy | grep -v '^#' || true` - PASS, no production `M112`.
- Task 3 targeted: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests *DeriveCapabilitiesTest --tests *CommandCatalogDriftTest --no-daemon" | tr -d '\r'` - PASS.
- Full suite: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon" | tr -d '\r'` - PASS.
- Acceptance: `grep -R "data class CommandSpec" app/src/main/java/works/mees/dinghy/command/CommandSpec.kt` - PASS, one match.

## Decisions Made

- Used unique operation-level catalog IDs for registry entries that share generic upstream commands, because the plan requires `CommandRegistry.all.map { it.catalogId }` to have no duplicates.
- Kept `printer.gcode.script` registry entries on `CommandTransport.GcodeScript`; the direct `JsonRpcClient.request(command)` helper only accepts `CommandTransport.JsonRpc`.
- Left `JsonRpcMethods` notification constants in place; the registry models outbound commands only.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added missing catalog sidecar rows for registry entries**
- **Found during:** Task 1
- **Issue:** The plan required current outbound operations plus unique registry catalog IDs, but `catalog.json` did not yet include `access.oneshot_token`, `server.info`, or operation-level IDs for several PrinterCommands-backed actions.
- **Fix:** Added the missing catalog rows and a duplicate-ID drift guard.
- **Files modified:** `docs/commands/catalog.json`, `CommandCatalogDriftTest.kt`
- **Verification:** Task 1 targeted guard tests pass.
- **Committed in:** `e5d1195`

**2. [Rule 3 - Blocking] Completed cross-task API surfaces before Task 1 targeted tests could compile**
- **Found during:** Task 1 verification
- **Issue:** Gradle compiles the whole release unit-test source set before applying `--tests`, so Task 1's targeted test command was blocked by Task 2 and Task 3 RED guard symbols.
- **Fix:** Implemented the missing helper and capability APIs, then re-ran each task's targeted command before committing each task slice.
- **Files modified:** `CommandDispatchExtensions.kt`, `Capabilities.kt`, `DeriveCapabilities.kt`, `DeriveCapabilitiesTest.kt`
- **Verification:** All targeted plan commands and the full release unit-test suite pass.
- **Committed in:** `ce2b138`, `c58b84d`

---

**Total deviations:** 2 auto-fixed (1 missing critical, 1 blocking)
**Impact on plan:** Both were required to satisfy the guard tests and acceptance criteria; no user-facing scope was added.

## Issues Encountered

- The Windows Gradle wrapper command returns through a pipe to `tr`, so failure detection was based on `BUILD FAILED` / `BUILD SUCCESSFUL` output rather than shell exit status for the initial RED baseline.

## Stub And Threat Scan

- **Known stubs:** None. Stub-pattern scan only found intentional nullable defaults / loop sentinels.
- **Threat flags:** None beyond the plan's threat model. The new request helper mitigates T-06-02-E by rejecting non-JSON-RPC command specs.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 06-03 can refactor call sites onto the registry helpers. The source registry, dispatch helpers, and live predicate surface are in place and guard-tested.

## Self-Check: PASSED

- Found summary file: `.planning/phases/06-command-reference-capability-matrix/06-02-SUMMARY.md`
- Found created files: `CommandSpec.kt`, `CommandRegistry.kt`, `CommandDispatchExtensions.kt`
- Found task commits: `e5d1195`, `ce2b138`, `c58b84d`

---
*Phase: 06-command-reference-capability-matrix*
*Completed: 2026-06-02*
