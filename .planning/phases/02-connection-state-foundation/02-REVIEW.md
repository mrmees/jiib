---
phase: 02-connection-state-foundation
reviewed: 2026-05-30T00:00:00Z
depth: standard
files_reviewed: 17
files_reviewed_list:
  - app/src/main/java/works/mees/dinghy/net/JsonRpc.kt
  - app/src/main/java/works/mees/dinghy/net/JsonRpcClient.kt
  - app/src/main/java/works/mees/dinghy/net/MoonrakerSocket.kt
  - app/src/main/java/works/mees/dinghy/net/RpcConnection.kt
  - app/src/main/java/works/mees/dinghy/net/RpcError.kt
  - app/src/main/java/works/mees/dinghy/net/Backoff.kt
  - app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt
  - app/src/main/java/works/mees/dinghy/auth/MoonrakerAuth.kt
  - app/src/main/java/works/mees/dinghy/config/DevConfig.kt
  - app/src/main/java/works/mees/dinghy/state/PrinterState.kt
  - app/src/main/java/works/mees/dinghy/state/Capabilities.kt
  - app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt
  - app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt
  - app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt
  - app/src/test/java/works/mees/dinghy/net/SessionTestHarness.kt
  - app/src/test/java/works/mees/dinghy/net/FakeWebSocket.kt
  - app/src/test/java/works/mees/dinghy/net/GoldenFixtures.kt
findings:
  critical: 2
  warning: 7
  info: 5
  total: 14
status: issues_found
---

# Phase 2: Code Review Report

**Reviewed:** 2026-05-30
**Depth:** standard
**Files Reviewed:** 17
**Status:** issues_found

## Summary

This is a well-architected, defensively-written connection/state spine. The id-correlation,
close-fails-all-pending, overflow-safe backoff, typed-error classification, and pure-reducer
seams are genuinely solid and clearly address the prior cross-AI review concerns. The Json
posture is correct (loose, null-safe, no `!!` on wire data) and secret redaction is handled.

However, adversarial tracing surfaces two correctness BLOCKERS in the coroutine plumbing
(`connectAndServe`) plus several robustness gaps. The most important: a **subscriber-not-yet-
attached race** that silently drops early notification frames, and a **resync ordering hole**
where `objects.subscribe` diffs can be dispatched and dropped before the seed query lands. There
is also a real mock-leniency gap (the reviewer-flagged area): the harness validates identify args
but does NOT validate that `objects.subscribe` actually requested the objects it later pushes
diffs for, and the snapshot/subscribe replies reuse the SAME fixture id-blind — meaning a wrong
subscribe payload would pass tests.

## Critical Issues

### CR-01: Notification frames dropped due to subscriber-not-attached race in `connectAndServe`

**File:** `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt:153-174`

**Issue:** The notification flows (`rpc.statusUpdates`, `klippyEvents`, `gcodeResponses`) are
plain `MutableSharedFlow` with `replay = 0` and a finite `extraBufferCapacity` (see
`JsonRpcClient.kt:59,63,67`). In `connectAndServe`, the `routing` job is `launch`ed and its three
inner collectors begin subscribing **asynchronously**. Immediately afterward the `collector` job
is `launch`ed and starts calling `rpc.dispatch(event.text)`. There is no happens-before guarantee
that the three collectors have actually subscribed before frames start dispatching.

For a `SharedFlow` with `replay = 0`, any value `tryEmit`ted while there are **zero subscribers**
is buffered into `extraBufferCapacity` but is **never replayed** to a subscriber that attaches
later — those values are lost the moment a subscriber that wasn't present misses them. So a
`notify_klippy_ready` or an early `notify_status_update` that arrives in the window between socket
open and the routing collectors attaching is silently dropped. On the target's resync path the
very first klippy/status pushes can race exactly here.

**Fix:** Make subscription deterministic before any dispatch. Either (a) use
`shareIn`/`stateIn` with `SharingStarted.Eagerly` upstream, or (b) gate the collector on the
routing collectors being live. Simplest robust fix — collect the notification flows via
`MutableSharedFlow(replay = ...)` only as a fallback; the durable fix is to ensure subscription
ordering:

```kotlin
val routingReady = CompletableDeferred<Unit>()
val routing = launch {
    coroutineScope {
        launch { rpc.statusUpdates.collect { store.onStatusDiff(it) } }
        launch { rpc.klippyEvents.collect { store.onKlippyMethod(it) } }
        launch { rpc.gcodeResponses.collect { store.onGcodeLine(it) } }
        // all three subscribed once the launches above have run their first suspension
        routingReady.complete(Unit)
    }
}
routingReady.await()           // do not start dispatching until subscribers are attached
val collector = launch { socketEvents(token).collect { ... } }
```

Note `routingReady.complete` after the launches still does not strictly prove the children reached
their collect suspension point — prefer giving each notification `SharedFlow` a small `replay` (1)
OR using `onSubscription`. The current code has no guarantee at all.

### CR-02: `objects.subscribe` diffs can be applied before/around the seed, racing the snapshot overwrite

**File:** `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt:231-236`

**Issue:** The handshake does query (`seed`) at step 4, then `objects.subscribe` at step 5. But
the moment `objects.subscribe` is sent, Moonraker begins pushing `notify_status_update` diffs, and
the **subscribe reply itself carries a snapshot** (documented at line 235 "reply is the same
snapshot shape") that is **thrown away** — `rpc.request(OBJECTS_SUBSCRIBE, ...)` ignores its
return value. Combined with CR-01, the ordering of "seed lands → subscribe sent → diffs flow" is
not enforced relative to the store accumulator. If a diff for an object is dispatched and reduced
onto the accumulator and THEN a delayed/duplicate seed runs, or if the subscribe-reply snapshot
(which is the authoritative post-subscribe truth) is discarded, the store can hold values that
disagree with the server. D-04 ("seed overwrites stale state") is only correct if the seed is the
last authoritative write; here the subscribe reply is the real post-subscribe snapshot and it is
silently dropped.

**Fix:** Use the subscribe reply as the authoritative seed (it is the snapshot taken at
subscription time, so no diff can be lost between query and subscribe):

```kotlin
// 5. subscribe — its reply IS the at-subscription snapshot; seed from it, not the earlier query.
val subResult = rpc.request(JsonRpcMethods.OBJECTS_SUBSCRIBE, objectsParam(subset))
parseStatus(subResult)?.let { store.seed(reduceSnapshot(it)) }
```

This also makes step 4's separate query redundant for seeding — keep query only if you need state
before subscribe, otherwise drop it. As written, diffs arriving between the query reply and the
subscribe call (or before the routing subscribers attach) corrupt the seeded state.

## Warnings

### WR-01: `Served` branch resets `attempt` to 0 then immediately backs off — no progressive backoff on a flapping-after-connect socket

**File:** `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt:110-116`

**Issue:** When a socket connects successfully then dies (`ConnectAttempt.Served`), `attempt` is
reset to 0 and `waitBackoffOrTrigger(0)` is called, i.e. `backoffDelay(0)` ≈ up to one `base`
(500ms) wait. If the printer/Moonraker is in a crash-restart loop where it accepts the socket,
completes resync, then drops repeatedly, every cycle resets `attempt=0`, so the client hammers
reconnect at ~500ms forever with no progressive backoff — exactly the "gentle to the LAN" goal
(T-02-09) defeated for the flapping case. Resetting on *successful sustained* connect is right;
resetting on *every* served-then-died cycle is not.

**Fix:** Only reset `attempt` after the connection has been `Connected` for some minimum duration,
or track served-then-died as its own backed-off case:

```kotlin
ConnectAttempt.Served -> {
    attempt += 1   // a connect that died still counts toward backoff; reset only on durable Connected
    store.markStale(ConnectionState.Disconnected)
    emit(ConnectionState.Disconnected)
    waitBackoffOrTrigger(attempt)
}
```

### WR-02: Test harness reuses the SAME fixture for `objects.query` AND `objects.subscribe`, and never validates the subscribe payload — hides protocol bugs

**File:** `app/src/test/java/works/mees/dinghy/net/SessionTestHarness.kt:72-74`

**Issue:** This is the exact class of mock-leniency the review flagged. `replyFor` maps both
`OBJECTS_QUERY` and `OBJECTS_SUBSCRIBE` to the same `snapshotJson` and **does not inspect the
requested `objects` subset at all**. Real Moonraker only returns status for objects you actually
subscribed to, and rejects/ignores unknown objects. So a bug where `deriveSubscribeSet` produces
the wrong set (or `objectsParam` builds a malformed `{objects:{...}}`) would still get a full happy
snapshot back and pass every test — the same failure mode as the url-less-identify bug that
already bit you. The harness validates identify args (good, lines 84-91) but applies no equivalent
validation to subscribe/query.

**Fix:** Validate the `objects.subscribe`/`objects.query` params in the harness — assert the
requested object set is non-empty and well-formed, and project the snapshot fixture down to only
the requested objects, mirroring Moonraker:

```kotlin
JsonRpcMethods.OBJECTS_SUBSCRIBE, JsonRpcMethods.OBJECTS_QUERY -> {
    val requested = obj["params"]?.jsonObject?.get("objects")?.jsonObject?.keys
        ?: return errorFrame(id, 400, "No data for argument: objects")
    if (requested.isEmpty()) return errorFrame(id, 400, "objects empty")
    reIdResult(projectSnapshotTo(snapshotJson, requested), id)
}
```

### WR-03: `routing` uses pointless double-launch (`launch { ...launchIn(this) }`)

**File:** `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt:153-157`

**Issue:** Each line is `launch { rpc.statusUpdates.onEach { ... }.launchIn(this) }`.
`onEach{}.launchIn(this)` already starts a collecting coroutine; wrapping it in an outer `launch`
creates a redundant coroutine whose only body is to start another one and then complete
immediately. The outer coroutine completes the instant `launchIn` returns, leaving the inner
collector parented to it — behavior is *probably* fine because `launchIn(this)` uses the inner
`launch`'s scope, but the intent is muddled and it makes the CR-01 subscription-timing reasoning
harder. This is a correctness-adjacent smell in the most timing-sensitive code in the phase.

**Fix:** Collapse to a single collector each:

```kotlin
val routing = launch {
    launch { rpc.statusUpdates.collect { store.onStatusDiff(it) } }
    launch { rpc.klippyEvents.collect { store.onKlippyMethod(it) } }
    launch { rpc.gcodeResponses.collect { store.onGcodeLine(it) } }
}
```

### WR-04: Subscribe-reply snapshot discarded means a diff lost in the query→subscribe gap is never reconciled

**File:** `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt:231-236`

**Issue:** (Companion to CR-02, kept distinct because it stands even if CR-02's race is otherwise
mitigated.) Between sending `objects.query` and `objects.subscribe`, the printer state can change
(a temp tick, a print-state transition). Those changes are not in the query snapshot and there is
no subscription yet to deliver them as diffs, so they are simply missing until the next diff that
happens to touch the same field. The subscribe reply is the canonical reconciliation point and it
is discarded.

**Fix:** Seed from the subscribe reply (see CR-02 fix); it is the snapshot at the instant the
subscription begins, so no window exists.

### WR-05: `intOrNullSafe` parses the JSON-RPC error code via `content.toInt()` — fails on a numeric-but-fractional or quoted code, silently coercing to `0`

**File:** `app/src/main/java/works/mees/dinghy/net/JsonRpcClient.kt:150,219`

**Issue:** Error-code parsing does `content.toInt()` inside `runCatching`, falling back to `0`. If
a server (or a hostile/buggy peer) sends `"code": -32602.0` or `"code":"−32602"` with a non-ASCII
minus, `toInt()` throws and the code silently becomes `0`. Code `0` then flows into
`classifyIdentifyError(0, msg)`: with no "Unauthorized" text and `code != null`, `0` is not in
`-32700..-32600`, so it classifies as **`ServerError(0, ...)`** — a real auth/protocol failure can
be mis-typed, and the supervisor will retry-churn instead of quiescing. Wire data should never
coerce to a sentinel that changes classification.

**Fix:** Use kotlinx's `intOrNull` and treat a missing/unparseable code as `null` (which
`classifyIdentifyError` already handles via its A5 fallback), not `0`:

```kotlin
val code = errObj["code"]?.jsonPrimitive?.intOrNull   // null, not 0, when absent/garbage
val message = errObj["message"]?.jsonPrimitive?.contentOrNull ?: "JSON-RPC error"
deferred.completeExceptionally(RpcError(code ?: 0, message))
```

and make `RpcError.code` nullable, or pass `code` straight through to classification rather than a
coerced `0`.

### WR-06: `RpcConnection.close(cause)` ignores `cause` — dead parameter, and uses `cancel()` for the normal-close path

**File:** `app/src/main/java/works/mees/dinghy/net/RpcConnection.kt:52-57`

**Issue:** `close(cause)` takes a `ConnectionError?` and documents it as "advisory," but the body
never reads `cause` — it is purely dead. Worse, both the normal-close path
(`MoonrakerSocket.onClosing` → `connection?.close()`) and the failure path call the same
`webSocket.cancel()`. `cancel()` is a hard, non-graceful teardown that does not send a close frame;
for a normal server-initiated close this skips the WebSocket closing handshake. The comment at
line 54 even says "1000 = normal closure" but no `close(1000, ...)` is ever issued — only
`cancel()`. The comment is misleading dead intent.

**Fix:** Either drop the unused `cause` param, or use it; and distinguish graceful close from hard
cancel:

```kotlin
fun close(cause: ConnectionError? = null) {
    if (open.compareAndSet(true, false)) {
        if (cause == null) webSocket.close(1000, "client closing") else webSocket.cancel()
    }
}
```

### WR-07: High-rate sampling can hide a terminal `Complete`/`Cancelled`/`Error` print-state if it arrives via `virtual_sdcard`/`display_status` only

**File:** `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt:90-98,137-143`

**Issue:** `touchesControlPlane` treats only `print_stats`, `webhooks`, and `toolhead.homed_axes`
as immediate. A `display_status`/`virtual_sdcard` `progress` reaching `1.0` (print finished) or a
`progress` reset is purely high-rate, so it can sit in the accumulator up to `sampleMillis` (250ms)
before publishing. That is acceptable for progress. BUT the reducer also derives nothing terminal
from progress, so this is borderline. The real latent issue: if a printer reports a print state
transition by mutating `print_stats.state` in the SAME diff as a burst of heater noise, the
control-plane check fires (good) — but the reverse is fine. Flagging because the conflation
boundary is subtle and the 250ms window on a weak device could batch a `Connected`-adjacent
flicker; verify there is a test asserting a `print_stats.state` change is never delayed when it
shares a diff with high-rate fields.

**Fix:** Add an explicit test: a diff containing both `extruder.temperature` and
`print_stats.state` must publish immediately with both fields, and confirm no terminal lifecycle
signal can be delayed by the sampler. If progress-derived terminal UI is added later, route it
through the control plane.

## Info

### IN-01: `MoonrakerSocket.events()` `awaitClose` may double-close but `connection?.close()` is idempotent — confirm no leak on `onFailure`

**File:** `app/src/main/java/works/mees/dinghy/net/MoonrakerSocket.kt:88-105`

**Issue:** On `onFailure`, `connection?.close(...)` runs, then `close()` completes the flow, which
triggers `awaitClose { connection?.close() }` again. `RpcConnection.close` is CAS-guarded so the
second call is a no-op — fine. Noting only so a future change to `close()` semantics doesn't break
idempotency silently.

**Fix:** No change required; add a comment that double-close is intentional and relies on the CAS.

### IN-02: `GoldenFixtures.frames`/`objectsList` use `!!` on resource JSON

**File:** `app/src/test/java/works/mees/dinghy/net/GoldenFixtures.kt:44,48`

**Issue:** `loadObject(name)["frames"]!!` and the chained `!!`s will throw an opaque NPE if a
fixture is malformed/missing a key, rather than a descriptive failure. Test-only, so low impact,
but a bad fixture gives a useless stack trace.

**Fix:** `requireNotNull(loadObject(name)["frames"]) { "fixture $name missing 'frames'" }`.

### IN-03: `classifyIdentifyError` condition has a redundant disjunct

**File:** `app/src/main/java/works/mees/dinghy/net/RpcError.kt:63`

**Issue:** `if ((code == CODE_INVALID_PARAMS && saysUnauthorized) || saysUnauthorized)` — the first
disjunct is fully subsumed by the second (`saysUnauthorized` alone). The whole condition reduces to
`if (saysUnauthorized)`. Dead logic; harmless but confusing about intent.

**Fix:** `if (saysUnauthorized) return ConnectionError.AuthRequired`.

### IN-04: `EXTRUDER_N` regex duplicated across two files

**File:** `app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt:110` and
`app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt:10`

**Issue:** `Regex("""extruder\d+""")` and the `isExtruder`/`isHeaterObject` logic are defined twice.
They can drift (e.g. one updated to handle `extruder` vs `extruder0`). Minor duplication.

**Fix:** Hoist a single `internal val EXTRUDER_N` / `isExtruder` into one shared file in the `state`
package.

### IN-05: `objects.subscribe`/`query` reply parsing assumes `result.status` for query but subscribe shape may differ

**File:** `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt:262-263`

**Issue:** `parseStatus` reads `result.status`. Moonraker's `objects.query` returns
`{eventtime, status}`; the `objects.subscribe` reply is the same shape, so this is fine for the
CR-02 fix. Noting only that if the subscribe reply shape is ever assumed elsewhere to be the
2-element notify array (`[{...}, eventtime]`), `parseStatus` would silently return null and seed
nothing. Confirm a fixture-backed test pins the subscribe reply shape.

**Fix:** Keep a golden fixture for the subscribe reply distinct from `notify_status_update` so the
two shapes can't be conflated.

---

_Reviewed: 2026-05-30_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
