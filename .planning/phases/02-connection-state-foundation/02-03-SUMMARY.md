---
phase: 02-connection-state-foundation
plan: 03
subsystem: transport-protocol-seam (Wave 2)
tags: [websocket, callbackflow, json-rpc, id-correlation, timeout, notifications]
requires:
  - "JSON-RPC envelope contracts + MoonrakerJson + JsonRpcMethods (02-01 net/JsonRpc.kt)"
  - "Typed ConnectionError + RpcError + classifyIdentifyError (02-01 net/RpcError.kt)"
  - "DevConfig.wsUrl (02-01 config/DevConfig.kt)"
  - "FakeWebSocket test harness (02-01 app/src/test/.../FakeWebSocket.kt)"
  - "kotlinx-coroutines-test virtual time (02-01 catalog)"
provides:
  - "SocketEvent (Open/Frame/Closed) sealed model + MoonrakerSocket callbackFlow bridge over OkHttp"
  - "WebSocketFactory injectable seam (FakeWebSocket-substitutable); MoonrakerSocket.real() carries readTimeout(0) ws posture"
  - "RpcConnection: send/close bound to the live socket lifecycle (send-before-open unrepresentable, send-after-close throws)"
  - "JsonRpcClient: id-correlation (STATE-05), per-request withTimeout, close(cause) fails+clears all pending, notify routing by method"
  - "Separate bounded un-throttled gcodeResponses SharedFlow distinct from statusUpdates (STATE-03 discretion)"
  - "RpcConnectionException (typed transport failure, plain Exception)"
affects:
  - "02-04 MoonrakerSession consumes MoonrakerSocket.events() + drives JsonRpcClient.bind/dispatch/close for the reconnect supervisor + handshake"
tech-stack:
  added: []
  patterns:
    - "callbackFlow push->Flow bridge with awaitClose cancellation (02-RESEARCH Pattern 1)"
    - "Map<id,CompletableDeferred> + Mutex + AtomicLong correlation (02-RESEARCH Pattern 2)"
    - "Send only via a connection minted in onOpen — no public pre-open send path (review HIGH #4)"
    - "close-fails-all-pending: snapshot+clear under mutex, completeExceptionally each (review HIGH #1)"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/net/MoonrakerSocket.kt
    - app/src/main/java/works/mees/dinghy/net/RpcConnection.kt
    - app/src/main/java/works/mees/dinghy/net/JsonRpcClient.kt
    - app/src/test/java/works/mees/dinghy/net/MoonrakerSocketTest.kt
    - app/src/test/java/works/mees/dinghy/net/RpcConnectionTest.kt
    - app/src/test/java/works/mees/dinghy/net/JsonRpcClientTest.kt
  modified: []
decisions:
  - "onFailure completes the callbackFlow NORMALLY (typed cause carried in SocketEvent.Closed), not with the throwable — so consumers observe Closed(cause) without try/catch"
  - "RpcConnectionException extends plain Exception, NOT CancellationException — else completeExceptionally would cancel the deferred instead of failing it with the typed reason"
  - "Tests use UnconfinedTestDispatcher (eager coroutine start) so callbackFlow collection + dispatch run deterministically without advanceUntilIdle gymnastics; failing-path requests wrapped in runCatching inside async so the exception doesn't fail the parent test scope"
metrics:
  duration_min: 14
  tasks: 2
  files: 6
  completed: 2026-05-30
---

# Phase 2 Plan 03: Transport + Protocol Seams Summary

Built the transport + JSON-RPC correlation seams the rest of Phase 2 stands on: a `MoonrakerSocket`
that bridges an OkHttp WebSocket into a cold `callbackFlow<SocketEvent>` through an injectable factory,
a concrete `RpcConnection` whose send/close are bound to the live socket lifecycle, and a `JsonRpcClient`
that correlates responses by id under interleaved `notify_*` (STATE-05), enforces per-request timeouts,
and atomically fails+clears all pending deferreds on socket death so a reconnect/handshake can never
deadlock (review HIGH #1).

## What Was Built

**Task 1 — MoonrakerSocket bridge + RpcConnection (commit `ce4dbbc`)**
- `net/MoonrakerSocket.kt`: a sealed `SocketEvent { Open(connection), Frame(text), Closed(cause) }` and a
  `callbackFlow`-based `events()` bridge. `onOpen` mints an `RpcConnection` over the live socket and emits
  `Open`; `onMessage` → `Frame`; `onClosing` → `Closed(null)` + normal complete; `onFailure` →
  invalidate + `Closed(NetworkUnavailable)` + normal complete; `awaitClose` invalidates the connection
  (cancelling the socket) on scope cancel. A `WebSocketFactory` fun-interface is the injected seam (the
  one deliberate difference from the smoke test — no hard-coded `newWebSocket`). `MoonrakerSocket.real()`
  carries the smoke posture: one OkHttp client, finite `connectTimeout`, `readTimeout(0)`; `wsUrl` defaults
  to `DevConfig.wsUrl` but is overridable for the Plan-04 `?token=` variant.
- `net/RpcConnection.kt`: a concrete class with an `internal` constructor (only `net` may mint one over a
  live `okhttp3.WebSocket`). `send(text)` forwards to `WebSocket.send` while an `AtomicBoolean` open flag is
  set, and `check()`s both the flag and OkHttp's enqueue result — `send` after `close` throws a clean
  `IllegalStateException` (no silent drop, no NPE). `close(cause)` is idempotent and cancels the socket.
- Tests: `MoonrakerSocketTest` (open→frame emission, send captured by the fake, awaitClose no-leak,
  onFailure typed `Closed`) and `RpcConnectionTest` (send-after-close throws, idempotent close, pre-open
  send unrepresentable by construction).

**Task 2 — JsonRpcClient (commit `5d1fcbd`)**
- `net/JsonRpcClient.kt`: `pending = Map<Long, CompletableDeferred<JsonElement>>` guarded by a `Mutex`, an
  `AtomicLong` id, and the active `RpcConnection` set via `bind()`. `request(method, params, timeoutMs)`
  registers a deferred under a fresh id, sends via the active connection (failing fast with a typed
  `RpcConnectionException` on no-connection / send-failure — never silent), then awaits under `withTimeout`;
  on timeout it removes its own pending entry and throws a typed cause. `dispatch(text)` splits inbound
  frames: `id`+`result` → resolve; `id`+`error` → fail with typed `RpcError(code,message)`; `id` present but
  no result/error → fail just that deferred (ParseError) without wedging the map; `method`+no-id → route to
  `statusUpdates` / `klippyEvents` / a SEPARATE bounded un-throttled `gcodeResponses` SharedFlow; unparseable
  → dropped. `close(cause)` clears the connection, snapshots+clears `pending` under the mutex, and
  `completeExceptionally`s every deferred — reusable after a fresh `bind()`.
- Tests: STATE-05 interleaving proof (a `notify_status_update` between request and its `{result,id}` does
  NOT resolve the request — it routes to the status flow; only the id-matched reply resolves), `{error:-32602}`
  → typed `RpcError` → `classifyIdentifyError` → `AuthRequired`, gcode/klippy routing on separate flows,
  close-fails-all-pending (request fails not hangs), per-request timeout in virtual time, no-connection
  fail-fast, malformed-frame-dropped.

## Verification

- `:app:testDebugUnitTest --tests *MoonrakerSocketTest* --tests *RpcConnectionTest* --tests *JsonRpcClientTest* --no-daemon`
  — BUILD SUCCESSFUL, exit 0 (6 + 8 = 14 tests green, no compiler warnings).
- STATE-05 adversarial interleaving proven; close-fails-all-pending + per-request virtual-time timeout
  proven (no deadlock); gcode stream is a separate bounded flow; send is bound to the live RpcConnection.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] callbackFlow collection didn't run under StandardTestDispatcher**
- **Found during:** Task 1 (first `MoonrakerSocketTest` run — `factory.open was never invoked`)
- **Issue:** `launchIn(backgroundScope)` + `testScheduler.advanceUntilIdle()` under the default
  `StandardTestDispatcher` did not start the `callbackFlow` producer, so the injected factory was never
  invoked and the captured fake was null.
- **Fix:** Switched the socket tests to `runTest(UnconfinedTestDispatcher())` so coroutines start eagerly
  and collection (hence `factory.open`) runs deterministically; frames driven synchronously between
  assertions. (`advanceUntilIdle()` calls left in as harmless no-ops.)
- **Files modified:** app/src/test/java/works/mees/dinghy/net/MoonrakerSocketTest.kt
- **Commit:** ce4dbbc

**2. [Rule 1 - Bug] onFailure closed the flow WITH the throwable, crashing collectors**
- **Found during:** Task 1 (`onFailure` test threw the IOException into the test scope)
- **Issue:** `callbackFlow { ... close(t) }` completes the flow exceptionally, forcing every consumer to
  wrap collection in try/catch and surfacing the raw transport throwable instead of the typed reason.
- **Fix:** `onFailure` now emits `SocketEvent.Closed(NetworkUnavailable)` and completes the flow NORMALLY
  (`close()`); the typed cause lives in the event. The 02-04 reconnect supervisor observes `Closed(cause)`
  and decides, no try/catch.
- **Files modified:** app/src/main/java/works/mees/dinghy/net/MoonrakerSocket.kt
- **Commit:** ce4dbbc

**3. [Rule 1 - Bug] RpcConnectionException as CancellationException would cancel, not fail, deferreds**
- **Found during:** Task 2 (designing close-fails-all-pending)
- **Issue:** A first draft made the typed transport exception extend `CancellationException`.
  `CompletableDeferred.completeExceptionally(CancellationException)` CANCELS the deferred, so `await()`
  would surface a generic cancellation instead of the typed `ConnectionError` reason — silently defeating
  the close-fails-all-pending contract (review HIGH #1).
- **Fix:** `RpcConnectionException` extends plain `Exception`; documented why in its KDoc.
- **Files modified:** app/src/main/java/works/mees/dinghy/net/JsonRpcClient.kt
- **Commit:** 5d1fcbd

**4. [Rule 3 - Blocking] async-launched failing requests failed the parent test scope**
- **Found during:** Task 2 (error/close/timeout tests failed with the very exception they assert)
- **Issue:** Under `UnconfinedTestDispatcher`, an `async { client.request(...) }` that throws propagates the
  exception to the parent `TestScope` on completion, failing the whole test even though the test awaits it.
- **Fix:** Wrapped the failing-path requests in `runCatching` INSIDE the `async`, so the exception is
  captured as a `Result` and never escapes to the parent scope; the test inspects
  `reply.await().exceptionOrNull()`.
- **Files modified:** app/src/test/java/works/mees/dinghy/net/JsonRpcClientTest.kt
- **Commit:** 5d1fcbd

## Threat Surface

All three threat-model `mitigate` dispositions for this plan are implemented:
- **T-02-04** (hostile JSON): every parse is `runCatching`-guarded; malformed frame dropped, malformed
  response fails only its deferred; null-safe walks, no `!!` on wire data.
- **T-02-05** (unbounded gcode): `gcodeResponses` (and the other notify flows) use bounded
  `extraBufferCapacity`; gcode kept on its own un-throttled-but-bounded flow.
- **T-02-10** (pending RPC hang): per-request `withTimeout` + `close(cause)` failing+clearing all pending.

No new security surface beyond the threat model was introduced.

## Notes for Downstream Waves

- **02-04 wiring:** collect `MoonrakerSocket.events()`; on `Open` call `JsonRpcClient.bind(conn)`; feed every
  `Frame.text` to `JsonRpcClient.dispatch(text)`; on `Closed(cause)` call `JsonRpcClient.close(cause)` (also
  on cancel). The supervisor owns the handshake order identify → objects.query → objects.subscribe.
- `JsonRpcClient.dispatch` is `suspend` (it takes the pending mutex) — call it from the socket-collecting
  coroutine, which is already structured-concurrency-bound.
- The notify flows are hot `MutableSharedFlow`s with `tryEmit` (non-suspending). A late subscriber misses
  prior emissions (replay=0) — the reducer/store in 02-04 must subscribe before driving the socket, exactly
  as the tests do.

## Self-Check: PASSED

All 6 created files exist on disk; both task commits (ce4dbbc, 5d1fcbd) are in git history; the full
three-class unit-test run is green (exit 0).
