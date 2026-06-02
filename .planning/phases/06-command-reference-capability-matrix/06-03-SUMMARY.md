---
phase: 06-command-reference-capability-matrix
plan: 03
subsystem: command-dispatch
tags: [kotlin, moonraker, jsonrpc, command-registry, tdd]

requires:
  - phase: 06-command-reference-capability-matrix
    plan: 02
    provides: CommandRegistry, CommandSpec, registry request/dispatch helpers, Capabilities.components
provides:
  - Registry-backed MoonrakerSession handshake and one-shot reads
  - Registry-backed MoonrakerService metadata/history reads and restart actions
  - Registry-backed Print Status, Temperature, Move, and Extrude action dispatch
  - RED/GREEN guards for server.info handshake order and UI busy-key preservation
affects: [moonraker-session, foreground-service, print-status, temperature, move, extrude, command-registry]

tech-stack:
  added: []
  patterns:
    - "Thin registry helper use: CommandDispatcher and JsonRpcClient retain timeout, redaction, and request semantics"
    - "UI screens preserve local in-flight checks by deriving keys from CommandRegistry before dispatch"
    - "server.info.components is read during handshake and threaded into Capabilities.components"

key-files:
  created:
    - .planning/phases/06-command-reference-capability-matrix/06-03-SUMMARY.md
  modified:
    - app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt
    - app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
    - app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt
    - app/src/test/java/works/mees/dinghy/net/HandshakeTest.kt
    - app/src/test/java/works/mees/dinghy/net/SessionTestHarness.kt
    - app/src/test/java/works/mees/dinghy/command/CommandRegistryGcodeTest.kt

key-decisions:
  - "Keep JsonRpcMethods notification constants in place; only outbound sends moved behind CommandRegistry."
  - "Allow SetHeaterArgs and ApplyPresetArgs to carry UI dispatch-key overrides where the pre-existing busy key is not derivable from command params alone."
  - "Task 3 was verification-only; no empty process commit was created because no source file changed."

patterns-established:
  - "Session handshake order: identify -> server.info -> objects.list -> objects.query -> objects.subscribe -> temperature_store -> configfile objects.query"
  - "Registry gcode entries wrap PrinterCommands without re-templating strings."
  - "UI action wrappers check command.dispatchKey(args) before calling dispatcher.dispatch(command, args)."

requirements-completed: [PHASE-06-REFERENCE]

duration: 8min
completed: 2026-06-02
---

# Phase 6 Plan 03: Registry Call-Site Migration Summary

**Phase 1-5 outbound Moonraker and gcode send paths now route through CommandRegistry while preserving handshake order, e-stop transport, dispatcher timeouts, redaction, UI busy keys, and PrinterCommands output.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-06-02T05:34:26Z
- **Completed:** 2026-06-02T05:42:51Z
- **Tasks:** 3
- **Files modified:** 10 source/test files plus this summary

## Accomplishments

- `MoonrakerSession` now requests identify, `server.info`, object list/query/subscribe, temperature store, and configfile query through registry request helpers.
- `server.info.components` now populates `Capabilities.components`, making `ComponentPresent` predicates live instead of catalog-only.
- `MoonrakerService` now uses registry helpers for metadata/history one-shot reads and firmware/host restart dispatch.
- Print Status, Temperature, Move, and Extrude action sends now use registry dispatch entries, with existing busy keys preserved.
- Emergency stop remains `printer.emergency_stop` through the dispatcher; it was not converted to `M112` or `printer.gcode.script`.

## Task Commits

1. **Task 1 RED: server.info handshake guard** - `a7a49ec` (test)
2. **Task 1 GREEN: session/service registry sends** - `f85ef4f` (feat)
3. **Task 2 RED: UI dispatch-key guard** - `3a8d123` (test)
4. **Task 2 GREEN: UI registry dispatch migration** - `3000e6b` (feat)
5. **Task 3: full host regression** - verification-only; no source changes to commit

## Verification

- RED Task 1: targeted suite failed as expected because `HandshakeTest` expected `server.info` after identify before production code requested it.
- GREEN Task 1: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests *HandshakeTest --tests *MoonrakerServiceTest --no-daemon" | tr -d '\r'` - BUILD SUCCESSFUL.
- RED Task 2: targeted suite failed as expected because registry args did not yet support heater/preset key overrides.
- GREEN Task 2: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests *CommandDispatcherTest --tests *CommandRegistryGcodeTest --no-daemon" | tr -d '\r'` - BUILD SUCCESSFUL.
- Task 3 full gate: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest :app:compileReleaseKotlin --no-daemon" | tr -d '\r'` - BUILD SUCCESSFUL.
- Acceptance grep: no raw `rpc.request(JsonRpcMethods.IDENTIFY|OBJECTS_LIST|OBJECTS_QUERY|OBJECTS_SUBSCRIBE|TEMPERATURE_STORE|FILES_METADATA|HISTORY_LIST)` call sites remain in `app/src/main/java`.
- Acceptance grep: no raw UI/service `JsonRpcMethods.GCODE_SCRIPT`, `JsonRpcMethods.EMERGENCY_STOP`, or `PrinterCommands.scriptParams` action call sites remain outside registry/helper definitions.

## Files Created/Modified

- `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt` - moved outbound request sequence through registry helpers and added `server.info.components` parsing.
- `app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt` - moved metadata/history reads and restart dispatches through registry helpers.
- `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` - adjusted dispatch keys for current UI behavior and added key overrides where needed.
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt` - emergency stop dispatches `CommandRegistry.emergencyStop`.
- `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt` - heater, preset, and cooldown actions dispatch registry entries.
- `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` - jog, force move, home, and disable steppers dispatch registry entries.
- `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt` - extrude/retract, tool select, load/unload, and nozzle setpoint dispatch registry entries.
- `app/src/test/java/works/mees/dinghy/net/HandshakeTest.kt` - locks server.info handshake order and components propagation.
- `app/src/test/java/works/mees/dinghy/net/SessionTestHarness.kt` - adds a canned server.info response.
- `app/src/test/java/works/mees/dinghy/command/CommandRegistryGcodeTest.kt` - locks existing UI busy-key strings.

## Decisions Made

- `server.info` is a normal handshake request immediately after identify, not a best-effort read, because `Capabilities.components` is now correctness data for component predicates.
- `SetHeaterArgs.key` and `ApplyPresetArgs.key` are UI dispatch-key overrides only; gcode generation still delegates to `PrinterCommands` unchanged.
- `forceMove` intentionally shares `jog_$axis` with normal jog, matching the existing screen's busy-state behavior.
- Task 3 produced no commit because it was a verification task and the worktree stayed clean.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. The only expected failures were the planned RED gates before implementation.

## Known Stubs

These are pre-existing UI placeholders in files touched by the registry migration; they remain out of scope for this reference phase.

| File | Line | Stub | Reason |
|------|------|------|--------|
| `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt` | 155 | `TODO(nav): open past-print detail` | Files/print navigation belongs to Phase 7. |
| `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt` | 159 | `TODO(nav): open file browser` | Files browser belongs to Phase 7. |
| `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt` | 175 | Tune/Pause disabled placeholders | Print controls are deferred to Phase 7. |
| `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt` | 245 | `Spoolman integration coming soon` | Spoolman is Phase 11 scope. |

## Threat-Model Coverage

- **T-06-03-R:** Existing dispatch keys are locked by `CommandRegistryGcodeTest` and preserved in UI calls.
- **T-06-03-D:** `CommandDispatcher` remains the timeout owner; registry dispatch still routes `printer.gcode.script` through the long gcode timeout test path.
- **T-06-03-I:** Dispatcher failure redaction tests pass unchanged through registry overloads.
- **T-06-03-E:** E-stop remains `CommandRegistry.emergencyStop` -> `printer.emergency_stop`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 6 can continue into catalog/matrix completion with the runtime call sites now using the registry as the outbound source of truth. Manual flox/live-printer regression remains part of the later Phase 6 verification plan, not this host-only call-site migration plan.

## Self-Check: PASSED

- FOUND: `.planning/phases/06-command-reference-capability-matrix/06-03-SUMMARY.md`
- FOUND all 10 source/test files listed under key-files.modified.
- FOUND commits `a7a49ec`, `f85ef4f`, `3a8d123`, and `3000e6b` in git history.
- No missing files or commit hashes.

---
*Phase: 06-command-reference-capability-matrix*
*Completed: 2026-06-02*
