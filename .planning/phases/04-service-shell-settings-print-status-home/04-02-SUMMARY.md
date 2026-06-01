---
phase: 04-service-shell-settings-print-status-home
plan: 02
subsystem: ui
tags: [kotlin, coroutines, stateflow, jsonrpc, moonraker, routing, debounce]

# Dependency graph
requires:
  - phase: 02-connection-state-foundation
    provides: JsonRpcClient.request() (timeout + id-correlation + fail-fast), RpcConnectionException, PrinterState (klippyState/printState/connection), KlippyState/PrintState/ConnectionState enums
provides:
  - CommandDispatcher (PRIM-05/D-18) — shared action wrapper adding debounce + in-flight/busy Set + UI timeout over JsonRpcClient.request(), emitting redacted DispatchEvent.Failure for toasting
  - DispatchEvent sealed type (Failure) — the host-toast seam
  - JsonRpcMethods action constants EMERGENCY_STOP / FIRMWARE_RESTART / RESTART (D-10/D-12)
  - TopRoute.derive(cfgPresent, PrinterState) — the SINGLE pure top-level routing authority (D-05/D-06)
  - TopRoute { Connect, Splash, Shell(Dest) } + enum Dest { PrintStatus, Settings }
affects: [MainActivity root controller (04-07), AppShell/AppDrawer, SplashScreen, PrintStatusScreen Stop control, MoonrakerService spine assembly]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "UI-affordance wrapper over the transport seam: dispatcher adds ONLY debounce/in-flight/timeout, never reimplements transport"
    - "Injectable timeSource: () -> Long → debounce windows deterministic under runTest virtual clock"
    - "Substitutable request lambda (primary ctor) + JsonRpcClient secondary ctor → host-testable without a fake socket"
    - "Pure derive() routing authority mirroring DeriveCapabilities idiom (no I/O, no Compose, host-unit-testable)"
    - "Redaction-by-construction: failure toasts use fixed '<method> failed/timed out' copy, never the cause message"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt
    - app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt
    - app/src/test/java/works/mees/dinghy/command/CommandDispatcherTest.kt
    - app/src/test/java/works/mees/dinghy/ui/route/TopRouteTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/net/JsonRpc.kt

key-decisions:
  - "CommandDispatcher takes a substitutable `request` lambda (primary ctor) with a `JsonRpcClient` secondary ctor — keeps it host-testable under runTest without a fake socket while still wrapping the real request() in production"
  - "Debounce reads an injectable timeSource(): Long (default System.nanoTime()/1e6) so the test drives the scheduler's virtual clock — no Thread.sleep, no real-time flake"
  - "Failure toast copy is fixed ('<method> failed'/'<method> timed out') and NEVER interpolates the cause message — redaction by construction (T-04-02-I), since the cause could carry a ?token= URL"
  - "Outer withTimeout in the dispatcher is a belt-and-braces UI deadline layered over request()'s own withTimeout — it is what cuts a hung stub in the test and guarantees key re-enable in finally (T-04-02-DoS)"
  - "TopRoute reads ONLY klippyState for routing; printState is a downstream CONTENT input the PrintStatus screen reads, not a route input — printing and idle share the one Shell(Dest.PrintStatus) surface (D-06, no Dest.Job)"
  - "derive() never references s.connection — proven by the socketStateDoesNotRoute test and a clean grep (D-05)"

patterns-established:
  - "PRIM-05 dispatch wrapper: in-flight Set busy guard + per-key debounce + UI timeout, typed-catch (RpcConnectionException/TimeoutCancellationException) → redacted Failure event"
  - "Single routing authority: one pure derive() the root controller branches on; Splash/Shell never self-route"

requirements-completed: [PRIM-05]

# Metrics
duration: 18min
completed: 2026-06-01
---

# Phase 4 Plan 02: Shell Primitives (CommandDispatcher + TopRoute) Summary

**CommandDispatcher wraps JsonRpcClient.request() with per-key debounce + an in-flight/busy Set + a UI timeout (emitting redacted failure toasts), and TopRoute.derive() is the single pure klippy-lifecycle routing authority that never lets a socket flap bounce the user off home.**

## Performance

- **Duration:** ~18 min
- **Started:** 2026-06-01T01:10:00Z
- **Completed:** 2026-06-01T01:28:00Z
- **Tasks:** 2 (both TDD)
- **Files modified:** 5 (4 created, 1 modified)

## Accomplishments
- `CommandDispatcher` (PRIM-05/D-18): every Moonraker action tap now gets debounce (a stray double-tap fires e-stop at most once), an in-flight/busy guard (re-tap is a no-op while running; the host disables controls off `inFlight: StateFlow<Set<String>>`), and a UI timeout (a dropped packet times out instead of hanging, key re-enabled in `finally`). Typed failures surface as a redacted `DispatchEvent.Failure` for the toast layer.
- Action method constants `EMERGENCY_STOP` / `FIRMWARE_RESTART` / `RESTART` added to `JsonRpcMethods` (D-10/D-12).
- `TopRoute.derive(cfgPresent, PrinterState)`: the single pure routing authority — `!cfg → Connect`, `klippy != Ready → Splash`, else `Shell(Dest.PrintStatus)`. Reads only `klippyState`; never `connection` (D-05). No `Dest.Job` — printing and idle share the one Print Status surface (D-06).
- Both primitives are toolkit-agnostic (no Compose) and proven host-side under `runTest` virtual time / pure assertions.

## Task Commits

1. **Task 1 (RED): failing CommandDispatcher test** - `ad59030` (test)
2. **Task 1 (GREEN): CommandDispatcher + action constants** - `93d2f29` (feat)
3. **Task 2 (RED): failing TopRoute test** - `a2658a7` (test)
4. **Task 2 (GREEN): TopRoute.derive()** - `ec26529` (feat)

_No REFACTOR commits needed — both implementations were clean at GREEN. The contrived no-op `printState` reference initially drafted in TopRoute was removed before the GREEN commit (honest code: routing keys off klippyState only)._

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt` - PRIM-05 action wrapper (debounce + in-flight Set + UI timeout over `request()`), `DispatchEvent` toast seam.
- `app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt` - pure `derive()` routing authority + `TopRoute`/`Dest` types.
- `app/src/main/java/works/mees/dinghy/net/JsonRpc.kt` - added `EMERGENCY_STOP`/`FIRMWARE_RESTART`/`RESTART` to `JsonRpcMethods`.
- `app/src/test/java/works/mees/dinghy/command/CommandDispatcherTest.kt` - virtual-time proof: double-tap→1 call, in-flight blocks re-entry, timeout fires + re-enables, RpcConnectionException→Failure + re-enable, secret-redaction, distinct keys independent.
- `app/src/test/java/works/mees/dinghy/ui/route/TopRouteTest.kt` - routing cases incl. socket-state-does-not-route.

## Decisions Made
See `key-decisions` frontmatter. Headlines: substitutable `request` lambda + injectable `timeSource` keep the dispatcher host-testable under virtual time; failure copy is redacted by construction (never the cause message); `derive()` routes off `klippyState` only and never reads `s.connection`.

## Deviations from Plan

None - plan executed exactly as written. (One drafted-but-discarded contrived `printState` reference in TopRoute was cleaned up before the GREEN commit; it never shipped, so it is not a deviation — just honest code over a literal pattern-match.)

## Issues Encountered
None. The Windows-build-host DataStore atomic-rename quirk noted in the build-environment guidance did not apply — neither primitive touches DataStore or file IO.

## Threat-Model Coverage
All three registered threats are mitigated in code and proven:
- **T-04-02-DoS** — outer `withTimeout(timeoutMs)` + key removal in `finally` (test: `timeoutOnNeverCompletingCall_firesAndReEnablesKey`).
- **T-04-02-R** — debounce window + in-flight Set (tests: `doubleTapWithinDebounceWindow_firesUnderlyingCallExactlyOnce`, `inFlightKey_blocksReEntry_*`).
- **T-04-02-I** — fixed redacted failure copy (test: `failureMessageNeverEmbedsApiKeyOrToken`).

No new threat surface introduced beyond the plan's threat model.

## Known Stubs
None. Both files are fully implemented and wired against their real dependencies (`JsonRpcClient`, `PrinterState`) with no placeholder/empty-data flows. They are leaf primitives that downstream Wave-3/4 screens and the 04-07 root controller will consume.

## Next Phase Readiness
- `CommandDispatcher` is ready for: the Print Status Stop control (via `ConfirmGuard.onConfirm → dispatch("estop", EMERGENCY_STOP)`), Splash recovery buttons (`FIRMWARE_RESTART`/`RESTART`), and any future action tile.
- `TopRoute.derive()` is ready for the 04-07 root controller (MainActivity) — it is the only caller; Splash/AppShell must not self-route.
- No blockers. Both primitives are pure/host-proven; the MoonrakerService spine assembly (04-03) constructs the production `CommandDispatcher(rpc, scope)` via the secondary ctor and publishes it into the container.

## Self-Check: PASSED

All 4 created files exist on disk; all 4 task commits (`ad59030`, `93d2f29`, `a2658a7`, `ec26529`) present in git history. Both test classes green (`:app:testDebugUnitTest --tests …CommandDispatcherTest --tests …TopRouteTest` → BUILD SUCCESSFUL).

---
*Phase: 04-service-shell-settings-print-status-home*
*Completed: 2026-06-01*
