---
phase: 05-core-print-control-panels-temperature-move-extrude
plan: 09
subsystem: command-dispatch
gap_closure: true
tags: [bugfix, crash-fix, tdd, mock-vs-reality, command-dispatcher]
requires:
  - "command/CommandDispatcher.kt (dispatch launch block)"
  - "net/RpcError.kt (server-side RPC error model)"
provides:
  - "RpcError handling in CommandDispatcher.dispatch() — printer-rejected gcode is a non-fatal toast, not a crash"
  - "Regression test exercising the real RpcError completion path for gcode.script"
affects:
  - "Every Move/Temp/Extrude action (all route through dispatch + GCODE_SCRIPT)"
  - "MOVE-02 amber Override path (jogs unhomed axes → printer rejection → previously crashed)"
tech-stack:
  added: []
  patterns:
    - "Typed-exception catch alongside transport/timeout branches; never widen to Throwable"
    - "Fake faithful to the real client's failure cause (RpcError) — closes mock-vs-reality gap"
key-files:
  created: []
  modified:
    - "app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt"
    - "app/src/test/java/works/mees/dinghy/command/CommandDispatcherTest.kt"
decisions:
  - "Surface the printer's own RpcError.message (e.g. 'Move out of range') in the Failure toast — it is gcode-rejection text, never a credential; fall back to the bare method name when null."
  - "Catch RpcError specifically (peer of Exception, distinct from RpcConnectionException); do NOT widen any catch to Throwable/Exception so cancellation + genuinely-unexpected exceptions still propagate."
metrics:
  duration: 9
  completed: 2026-06-01
  tasks: 2
  files: 2
requirements: [EXTR-04, MOVE-02, MOVE-01]
---

# Phase 5 Plan 9: Close G1 — RpcError Crash on Printer-Rejected Gcode Summary

Added a `catch (e: RpcError)` clause to `CommandDispatcher.dispatch()` so a printer-rejected gcode (out-of-range move, failing macro, heater fault) is surfaced as a non-fatal `DispatchEvent.Failure` carrying the printer's rejection text — instead of re-throwing uncaught into the unsupervised `scope.launch` lambda and crashing the app (the G1 BLOCKER confirmed live on flox during Phase-5 UAT). Closed via TDD: a faithful regression test feeds the exact `RpcError` the real `JsonRpcClient` produces for a `gcode.script` rejection, closing the mock-vs-reality gap that let the crash ship behind green tests.

## What Was Built

**Task 1 — RpcError catch in dispatch() (GREEN, commit a2b5739):**
- Imported `works.mees.dinghy.net.RpcError`.
- Added `catch (e: RpcError)` alongside the existing `RpcConnectionException` and `TimeoutCancellationException` catches, before the `finally`. It emits exactly one `DispatchEvent.Failure(key, e.message ?: method)` so the existing `SeverityToast` pipeline shows the user WHY the command was rejected; the unchanged `finally` re-enables the key (`_inFlight.update { it - key }`).
- No catch was widened to `Throwable`/`Exception` — cancellation and genuinely-unexpected exceptions still propagate as before.
- Updated the `DispatchEvent.Failure` KDoc to note it now also covers a server-side gcode rejection (`RpcError`), not only transport/timeout.

**Task 2 — Regression test (RED, commit 1d5fe9f):**
- Added `gcodeScriptRpcError_emitsFailureEvent_andDoesNotCrash()`, modeled on the existing `rpcConnectionException_emitsFailureEvent_andReEnablesKey` test (same `runTest(UnconfinedTestDispatcher())` + events collector + `runCurrent()` cadence).
- Dispatches `JsonRpcMethods.GCODE_SCRIPT` under key `move_x`, then calls `rpc.fail(0, RpcError(code = -32000, message = "Move out of range: ..."))` to complete the underlying deferred exceptionally — mirroring `JsonRpcClient.dispatch()` line 155, the exact cause a rejected `gcode.script` produces.
- Asserts: the key is removed from `inFlight` (no leak), a `DispatchEvent.Failure` is emitted, and its message contains "Move out of range". `runTest` completing without an unhandled exception proves no crash escaped the launch scope.
- No loosening of `FakeRpc` — it already supported `fail(index, cause)`; the regression is closed by feeding it the *right* cause (`RpcError`), which the fake had never produced for `gcode.script` before.

## TDD Gate Compliance

- **RED** (1d5fe9f): the new test FAILED against the pre-fix dispatcher — `RpcError` escaped uncaught (`works.mees.dinghy.net.RpcError at CommandDispatcherTest.kt:165`), proving the bug and that the test exercises the real crash path.
- **GREEN** (a2b5739): with the catch in place the full `CommandDispatcherTest` suite passed; `:app:compileReleaseKotlin` succeeded.
- No REFACTOR commit needed (the fix is a single catch clause).

## Verification

- `:app:compileReleaseKotlin` — BUILD SUCCESSFUL.
- `:app:testReleaseUnitTest --tests works.mees.dinghy.command.CommandDispatcherTest` — BUILD SUCCESSFUL (all dispatcher tests including the new regression).
- `:app:testReleaseUnitTest` (full suite) — BUILD SUCCESSFUL, no other regressions.
- `grep -c "catch (e: RpcError)" CommandDispatcher.kt` → 1.
- `grep "import works.mees.dinghy.net.RpcError" CommandDispatcher.kt` → present.
- `grep "DispatchEvent.Failure(key, e.message" CommandDispatcher.kt` → present (line 123).
- No `catch (Throwable)` / bare `catch (Exception)` in CommandDispatcher.kt.

## Deviations from Plan

None — plan executed exactly as written.

## Scope Note (G2/G3 NOT in this plan)

The Phase-5 VERIFICATION report listed three gaps. This plan (05-09) closes **only G1 (BLOCKER)**. **G2 (HIGH — no `notify_klippy_ready` re-handshake) and G3 (MED — one-shot reads go stale after a config reload, same root cause as G2) remain OPEN** and are out of scope here; they require changes to `MoonrakerSession.kt` / `MoonrakerService.kt`, not the dispatcher. Whoever closes those should re-run the full re-handshake (re-identify → re-objects/subscribe → re-run ALL one-shot reads) on `notify_klippy_ready`.

## On-Device Note

The G1 crash was reproduced live on flox during UAT (FATAL EXCEPTION on `DefaultDispatcher-worker-N`, "Move out of range"). The fix and regression are proven host-side (RED→GREEN under `runTest`). The MOVE-02 amber Override path (which jogs unhomed axes → same rejection) was untestable on-device before this fix; a follow-up on-device re-check of the move/jog + Override flow on flox + the live Ender 5 Plus would confirm the toast-not-crash behavior end-to-end.

## Self-Check: PASSED

- FOUND: app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt
- FOUND: app/src/test/java/works/mees/dinghy/command/CommandDispatcherTest.kt
- FOUND commit 1d5fe9f (RED test)
- FOUND commit a2b5739 (GREEN fix)
