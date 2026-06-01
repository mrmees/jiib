---
phase: 05-core-print-control-panels-temperature-move-extrude
plan: 11
subsystem: command-dispatch
gap_closure: true
tags: [bugfix, dispatch-timeout, ux-messaging, tdd, mock-vs-reality, command-dispatcher]
requires:
  - "command/CommandDispatcher.kt (dispatch launch block + companion timeouts)"
  - "net/JsonRpcClient.kt (per-request withTimeout expiry)"
  - "net/RpcError.kt (ConnectionError sealed interface)"
provides:
  - "Per-command timeout selection — gcode.script gets 120s, instant calls keep the 10s default"
  - "Typed ConnectionError.Timeout distinguishing a request-await timeout from a true transport failure"
  - "Disambiguated dispatcher messaging — slow gcode -> calm 'still running'; true failure -> 'could not be sent'"
  - "Regression tests: delayed-but-successful gcode emits NO Failure; fast RpcError still emits Failure; timeout vs send-failure produce distinct messaging"
affects:
  - "Every Move/Temp/Extrude/home/disable action (all route through dispatch + GCODE_SCRIPT)"
  - "Z-home / G28, bed mesh, filament load/unload macros (all legitimately slow gcode)"
tech-stack:
  added: []
  patterns:
    - "Per-method timeout selection at the dispatch seam (long for completion-gated gcode.script)"
    - "Typed-reason branch (when(e.reason)) to map a request-await timeout to a non-alarming message"
    - "Failure messages built from non-secret method/key + fixed strings — never e.message (redaction)"
    - "Fake faithful to the real client's Timeout cause shape (closes mock-vs-reality gap)"
key-files:
  created: []
  modified:
    - "app/src/main/java/works/mees/dinghy/net/RpcError.kt"
    - "app/src/main/java/works/mees/dinghy/net/JsonRpcClient.kt"
    - "app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt"
    - "app/src/test/java/works/mees/dinghy/command/CommandDispatcherTest.kt"
decisions:
  - "GCODE_TIMEOUT_MS = 120_000L — generous but FINITE ceiling; the key is still removed in finally so even a truly-wedged gcode cannot permanently disable a control (T-04-02-DoS preserved)."
  - "A request-await timeout is typed ConnectionError.Timeout (distinct from NetworkUnavailable); the no-connection/send-failure paths in JsonRpcClient stay NetworkUnavailable — only the inner withTimeout expiry was retyped."
  - "Per-command timeout drives BOTH the inner request() deadline and the outer withTimeout so the two deadlines no longer race at 10s; the inner transport timeout stays authoritative."
  - "The Timeout branch message is composed from the non-secret method only, never e.message (which can carry a ?token= URL on a send failure) — redaction guard preserved."
metrics:
  duration: 4
  completed: 2026-06-01
  tasks: 2
  files: 4
requirements: [MOVE-01, MOVE-02, MOVE-03, MOVE-04, TEMP-01, TEMP-02, TEMP-03, TEMP-04, EXTR-01, EXTR-02, EXTR-03, EXTR-04]
---

# Phase 5 Plan 11: Close G4 — False Error on Slow-but-Successful Gcode Summary

A long-running but VALID gcode (Z-home / G28, bed mesh, filament load/unload macro) no longer pops a false "command could not be sent" toast. `printer.gcode.script` only returns its JSON-RPC reply when the gcode COMPLETES, so a Z-home (move-to-center + probe, >10s) tripped the flat `DEFAULT_TIMEOUT_MS = 10_000L` the dispatcher applied to EVERY command; the inner `JsonRpcClient.request()` timeout converted that expiry into an `RpcConnectionException` that the dispatcher reported as "command could not be sent" — wrong twice over (it WAS sent, and it did NOT fail). Fixed via TDD: a per-command timeout (120s for `gcode.script`, 10s default for instant calls), a typed `ConnectionError.Timeout` distinguishing a request-await timeout from a true transport failure, and a `when (e.reason)` split in the dispatcher that surfaces a calm "still running" message for the timeout while keeping the accurate "could not be sent" for a genuine send failure. G1 (fast printer rejections still surface) and the credential-redaction guard are intact.

## What Was Built

**Task 1 — typed Timeout reason + long per-command timeout (RED 4b938fa → GREEN 63eb8c4):**
- `RpcError.kt`: added `data object Timeout : ConnectionError` (KDoc: a request was sent and accepted by the transport but no reply arrived within the per-request deadline — EXPECTED for a long-running gcode, distinct from `NetworkUnavailable`).
- `JsonRpcClient.request()`: retyped ONLY the inner-`withTimeout` expiry from `ConnectionError.NetworkUnavailable` to `ConnectionError.Timeout`. The "no active connection" and "send failed" paths stay `NetworkUnavailable` (genuine transport failures). Pending entry is still removed first (T-02-10).
- `CommandDispatcher`: added `const val GCODE_TIMEOUT_MS = 120_000L` to the companion (KDoc justifies the value against Klipper homing/probe + mesh + load/unload macro durations and notes the finite ceiling preserves T-04-02-DoS). `dispatch()` selects `val perCmdTimeout = if (method == JsonRpcMethods.GCODE_SCRIPT) GCODE_TIMEOUT_MS else timeoutMs` and uses it for BOTH the inner `request(method, params, perCmdTimeout)` and the outer `withTimeout(perCmdTimeout)` so the deadlines no longer race at 10s.
- `FakeRpc` extended to record per-call `timeoutMs` (a `timeouts` list) so the selection test can assert it; the fake was NOT made to auto-complete or shorten any timeout — the slow-success test drives the virtual clock itself.

**Task 2 — disambiguated messaging (RED 8696772 → GREEN ea20617):**
- `CommandDispatcher` `catch (e: RpcConnectionException)`: switched on `e.reason` —
  `is ConnectionError.Timeout -> "$method is taking longer than expected — still running"`;
  `else -> "$method failed: command could not be sent"`. Both messages are built from the non-secret `method` + a fixed string; neither interpolates `e.message` (the only `e.message` use remains the `RpcError` branch). The `RpcError` and `TimeoutCancellationException` catches are unchanged.
- Updated the `DispatchEvent.Failure` KDoc to document the benign Timeout message.

## Tests (4 new, 2 guards preserved)

- `slowButSuccessfulGcode_emitsNoFailureEvent` — dispatch `GCODE_SCRIPT`, `advanceTimeBy(11_000L)` past the old 10s with the call still pending, then `complete(0)`; asserts NO `DispatchEvent.Failure` and the key is removed.
- `gcodeScript_usesLongTimeout_notTheDefault` — asserts `GCODE_SCRIPT` -> `GCODE_TIMEOUT_MS` and `EMERGENCY_STOP` -> `DEFAULT_TIMEOUT_MS` via the captured `FakeRpc.timeouts`.
- `requestTimeout_surfacesNonAlarmingStillRunningMessage` — feeds the exact `RpcConnectionException(ConnectionError.Timeout, ...)` the client now produces; asserts a Failure IS emitted, its message contains "still running"/"taking longer" and does NOT contain "could not be sent".
- `genuineConnectionFailure_stillReportsCouldNotBeSent` — a `NetworkUnavailable` failure still reads "could not be sent".
- GUARD (preserved): `gcodeScriptRpcError_emitsFailureEvent_andDoesNotCrash` (G1) — a fast `RpcError("Move out of range")` STILL emits a Failure carrying the printer's text.
- GUARD (preserved): `failureMessageNeverEmbedsApiKeyOrToken` — neither RpcConnectionException branch leaks a `?token=` secret.

## TDD Gate Compliance

- **Task 1 RED** (4b938fa): the two new tests FAILED — `gcodeScript_usesLongTimeout_notTheDefault` could not compile (`Unresolved reference 'GCODE_TIMEOUT_MS'`), proving the constant did not exist; the slow-gcode test would have emitted a Failure at the flat 10s.
- **Task 1 GREEN** (63eb8c4): dispatcher suite green after adding the constant, per-command selection, and the typed Timeout reason.
- **Task 2 RED** (8696772): `requestTimeout_surfacesNonAlarmingStillRunningMessage` FAILED (current code emitted "could not be sent" for the Timeout reason).
- **Task 2 GREEN** (ea20617): full dispatcher suite green after the `when (e.reason)` split.
- No REFACTOR commits needed (each fix is a focused edit).

## Verification

- `:app:testReleaseUnitTest --tests works.mees.dinghy.command.CommandDispatcherTest` — BUILD SUCCESSFUL (all 11 dispatcher tests, incl. the 4 new G4 tests + the preserved G1 and redaction guards).
- `:app:testReleaseUnitTest` (full module) — BUILD SUCCESSFUL, no regression. `JsonRpcClientTest`'s request-timeout test (`request without a reply fails on its per-request timeout`) passed unchanged — it only asserts the message contains "timed out", never the old `NetworkUnavailable` reason, so the retype to `Timeout` did not break it.
- `:app:compileReleaseKotlin` — BUILD SUCCESSFUL (the new sealed-interface member compiles; the only `when (e.reason)` without an `else` does not exist — MoonrakerSession.kt:175 already has an `else ->` branch, confirmed before editing).
- Acceptance greps all pass: `GCODE_TIMEOUT_MS = 120_000L`=1, `data object Timeout : ConnectionError`=1, `GCODE_SCRIPT) GCODE_TIMEOUT_MS`=1, `is ConnectionError.Timeout` (dispatcher)=1, `still running|taking longer`=2, `e.message` only in the `RpcError` branch (line 149), no bare `catch (Throwable|Exception)`.
- All Gradle via `E:\Android\gw.bat`, piped through `tr -d '\r'`, exit code authoritative.

## Deviations from Plan

None — plan executed exactly as written. The plan flagged that `MoonrakerSession.kt:175`'s `when (e.reason)` might need an `else` branch for exhaustiveness; it ALREADY has one (`else -> ConnectAttempt.Network`), and `ConnectionError` is a `sealed interface` so adding a member only breaks `else`-less exhaustive `when`s — of which there are none. No other call site required a new branch. `JsonRpcClientTest` needed no edit (it never asserted the timeout reason).

## On-Device Note

The G4 false-error was confirmed live on flox + a real Ender 5 Plus (tapping Z-home popped "command could not be sent" while the printer homed successfully). The fix and regressions are proven host-side (RED→GREEN under `runTest`'s virtual clock, with a `FakeRpc` faithful to the exact `ConnectionError.Timeout` cause the real `JsonRpcClient` now produces — closing the mock-vs-reality gap). A follow-up on-device re-check of Z-home / G28 + a load/unload macro on flox + the live Ender 5 Plus would confirm the calm "still running" toast (or no toast on completion under 120s) end-to-end.

## Self-Check: PASSED

- FOUND: app/src/main/java/works/mees/dinghy/net/RpcError.kt
- FOUND: app/src/main/java/works/mees/dinghy/net/JsonRpcClient.kt
- FOUND: app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt
- FOUND: app/src/test/java/works/mees/dinghy/command/CommandDispatcherTest.kt
- FOUND commit 4b938fa (Task 1 RED)
- FOUND commit 63eb8c4 (Task 1 GREEN)
- FOUND commit 8696772 (Task 2 RED)
- FOUND commit ea20617 (Task 2 GREEN)
