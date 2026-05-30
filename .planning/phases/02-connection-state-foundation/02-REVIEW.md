---
phase: 02-connection-state-foundation
reviewed: 2026-05-30T00:00:00Z
depth: standard
files_reviewed: 15
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
findings:
  critical: 1
  warning: 6
  info: 5
  total: 12
status: fixed
resolution: "All Critical + Warning findings fixed via --fix pass (commits a4e2fdc CR-01, af0708a WR-01, 279828c WR-05, 3ccf125 WR-06, 1f195e5 WR-03, c7ae1db WR-04, f0358d9 WR-02). Re-verified green 2026-05-30: :app:testDebugUnitTest, LiveSocketReconnectTest, and the orchestrated LiveReconnectYankTest (real Wi-Fi drop) all pass. INFO items IN-01/IN-02/IN-04/IN-05 left as non-blocking (IN-03 subsumed by the CR-01 fix)."
---

# Phase 2: Code Review Report

**Reviewed:** 2026-05-30
**Depth:** standard
**Files Reviewed:** 15
**Status:** fixed (all Critical + Warning resolved via --fix; re-verified green incl. live + Wi-Fi-yank — see frontmatter `resolution`)

## Summary

Re-review of the Phase 2 connection + state spine after the prior blockers were fixed. The three
prior BLOCKER fixes are genuinely resolved: CR-01 (notification-subscriber race) is now gated on all
three `onSubscription` deferreds before any frame dispatches; CR-02/WR-04 (discarded subscribe
snapshot) now seeds from the subscribe reply in `runHandshake` step 5; WR-05 (error-code coercion)
parses via `intOrNull` and `RpcError.code` is nullable. Those are not re-reported.

This pass surfaces one new BLOCKER (a real classification bug in `classifyIdentifyError` whose first
conditional collapses to a blanket "any message mentioning Unauthorized → AuthRequired", mis-routing
code-bearing server errors into the gentle-quiescence park), confirms the six known-deferred items so
the fixer can act, and records the five known INFO items. OOM/jank posture for the Adreno 320 / 2GB
target is sound: notification buffers are bounded (`STATUS_BUFFER`/`GCODE_BUFFER`/`KLIPPY_BUFFER`) and
the high-rate plane is conflated to ~4 Hz; nothing here risks unbounded allocation.

## Critical Issues

### CR-01: `classifyIdentifyError` first conditional collapses to a blanket "Unauthorized substring → AuthRequired", parking the supervisor on code-bearing server errors

**File:** `app/src/main/java/works/mees/dinghy/net/RpcError.kt:66`
**Issue:**
```kotlin
if ((code == JsonRpcMethods.CODE_INVALID_PARAMS && saysUnauthorized) || saysUnauthorized) {
    return ConnectionError.AuthRequired
}
```
The left disjunct `(code == CODE_INVALID_PARAMS && saysUnauthorized)` is fully subsumed by the right
disjunct `saysUnauthorized` — the whole condition reduces to `if (saysUnauthorized)`. This is more than
the cosmetic redundancy noted as IN-03; it changes behavior. Any server-range error (e.g. a Klipper
host fault surfaced with `code = 500`, or any wrapped/localized message) whose `message` contains the
substring "unauthorized" case-insensitively (e.g. `"command unauthorized at this stage"`) is classified
`AuthRequired` and routed into the gentle-quiescence park in `MoonrakerSession.run()`
(line 101-108). The supervisor then stops retrying a transient, recoverable server error and waits for
a manual `requestReconnectNow()`. The documented A5 design is the opposite: recognizable shapes
classify by *code first*, and only an *unrecognized* shape falls back to `AuthRequired`. The
message-substring test must be gated to the actual auth cases (the `-32602` code, or a code-less
error), not applied as a blanket override ahead of the code-based branches.
**Fix:**
```kotlin
fun classifyIdentifyError(code: Int?, message: String?): ConnectionError {
    val msg = message.orEmpty()
    val saysUnauthorized = msg.contains("Unauthorized", ignoreCase = true)

    // 1. Canonical auth signal: the -32602 "Unauthorized", OR an unauthorized message with no code.
    if ((code == JsonRpcMethods.CODE_INVALID_PARAMS && saysUnauthorized) ||
        (code == null && saysUnauthorized)) {
        return ConnectionError.AuthRequired
    }

    // 2. A non-auth invalid-params error.
    if (code == JsonRpcMethods.CODE_INVALID_PARAMS) {
        return ConnectionError.ProtocolError(code, msg)
    }

    // 3. Other JSON-RPC method errors: protocol range vs. server range.
    if (code != null) {
        return if (code in -32700..-32600) ConnectionError.ProtocolError(code, msg)
        else ConnectionError.ServerError(code, msg)
    }

    // 4. Unknown / sentinel identify error with no recognizable shape — defensive A5 fallback.
    return ConnectionError.AuthRequired
}
```
This also resolves IN-03.

## Warnings

### WR-01: `ConnectAttempt.Served` resets `attempt = 0` before backing off → no progressive backoff on a flapping-after-connect socket

**File:** `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt:109-115`
**Issue:** A socket that connects, completes the handshake, then dies immediately and repeatedly (a
flapping AP, a Klippy that connects then shuts down in a loop) hits the `Served` branch every cycle,
which sets `attempt = 0` and then calls `waitBackoffOrTrigger(attempt)`. With `attempt` always 0 the
backoff is always `backoffDelay(0, ...)` ≈ one base interval (~500 ms jittered). The client hammers a
flapping printer at ~2 Hz forever with no escalation — defeating the LAN-gentleness backoff exists to
provide (T-02-09). Resetting on a *successful* connect is correct; resetting on every *served-then-died*
attempt is not.
**Fix:** Escalate on a serve that died, and reset `attempt` only when a connect+handshake actually
reaches `Connected`:
```kotlin
ConnectAttempt.Served -> {
    store.markStale(ConnectionState.Disconnected)
    emit(ConnectionState.Disconnected)
    attempt += 1                 // a serve that died still escalates the backoff
    waitBackoffOrTrigger(attempt)
}
```
and move `attempt = 0` to fire on reaching `Connected` (have `connectAndServe` signal success distinctly,
or reset only after the serve loop confirms it reached Connected).

### WR-02: `SessionTestHarness` maps `objects.query` AND `objects.subscribe` to the same fixture and never validates the requested object subset

**File:** `app/src/test/java/works/mees/dinghy/net/SessionTestHarness.kt:80-82`
**Issue:**
```kotlin
JsonRpcMethods.OBJECTS_QUERY -> reIdResult(snapshotJson, id)
JsonRpcMethods.OBJECTS_SUBSCRIBE -> reIdResult(subscribeSnapshotJson ?: snapshotJson, id)
```
The fake echoes the full snapshot fixture regardless of the `objects` subset the session requested. The
harness now correctly rejects a url-less identify (`missingIdentifyArg`), but query/subscribe accept any
params — including an empty or wrong subset. `deriveSubscribeSet` is the A3 correctness seam ("never
subscribe to an object the printer doesn't define"), and the mock never exercises it: a regression that
subscribed to the wrong set, or to nothing, would pass every test. Test leniency, not a runtime bug, but
it leaves a real correctness path unguarded.
**Fix:** In `replyFor`, parse `params.objects` keys for query/subscribe and assert they are a non-empty
subset of the fixture's available objects; return an error frame (or expose a captured-subset field the
test asserts on) when the subset is empty or names an absent object.

### WR-03: `RpcConnection.close(cause)` ignores `cause` and always hard-cancels — no graceful 1000 close on normal teardown

**File:** `app/src/main/java/works/mees/dinghy/net/RpcConnection.kt:52-57`
**Issue:**
```kotlin
fun close(cause: ConnectionError? = null) {
    if (open.compareAndSet(true, false)) {
        // 1000 = normal closure; OkHttp's cancel() is the hard teardown for failure paths.
        webSocket.cancel()
    }
}
```
The `cause` parameter is accepted and documented as "advisory" but is entirely unused — every path calls
`webSocket.cancel()` (the hard, no-close-frame teardown). On a normal close (`MoonrakerSocket.onClosing`
calls `connection?.close()` with no cause) the client never sends a WebSocket 1000 close frame; it just
yanks the socket. Moonraker sees an abnormal disconnect, and the inline comment ("1000 = normal closure")
documents behavior the code does not implement. Functionally tolerable (OkHttp reaps the socket) but
dead-parameter code with a misleading comment.
**Fix:**
```kotlin
fun close(cause: ConnectionError? = null) {
    if (open.compareAndSet(true, false)) {
        if (cause == null) {
            if (!webSocket.close(1000, "client closing")) webSocket.cancel()
        } else {
            webSocket.cancel() // failure path: hard teardown
        }
    }
}
```

### WR-04: No test proves a `print_stats.state` change sharing a diff with high-rate fields bypasses the 250 ms sampler

**File:** `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt:90-98` (gap in `ConflationTest.kt`)
**Issue:** `onStatusDiff` publishes immediately when `touchesControlPlane(diff)` is true. The existing
`controlPlaneTransition_isImmediate_notSampled` test sends a *control-plane-only* diff
(`{"print_stats":{"state":"printing"}}`). It does NOT cover the realistic case where one
`notify_status_update` carries `print_stats.state` *together with* high-rate fields — e.g.
`{"print_stats":{"state":"complete"},"heater_bed":{"temperature":58.3},"virtual_sdcard":{"progress":0.99}}`
— which is exactly how Moonraker batches a print-finish frame. The code handles it correctly
(`touchesControlPlane` inspects the whole diff), but there is no regression guard: a refactor that split
control vs high-rate fields into separate paths could silently delay a print-complete behind a sample
tick. The reviewer brief asked to confirm this is tested; it is not.
**Fix:** Add a test that seeds, sends one mixed diff, and asserts (with `runCurrent()` and NO
`advanceTimeBy`) that the transition is visible immediately:
```kotlin
@Test fun controlPlaneMixedWithHighRate_isImmediate() = runTest {
    val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
    store.seed(PrinterState())
    store.onStatusDiff(statusDiff(
        """{"print_stats":{"state":"complete"},"heater_bed":{"temperature":58.3}}"""))
    runCurrent()
    assertEquals(PrintState.Complete, store.printerState.value.printState)
}
```

### WR-05: `run()` wraps `connectAndServe()` in `runCatching`, swallowing `CancellationException`

**File:** `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt:97-98`
**Issue:**
```kotlin
val outcome = runCatching { connectAndServe() }
val result = outcome.getOrElse { ConnectAttempt.Network }
```
`runCatching` catches `Throwable`, including `kotlinx.coroutines.CancellationException`. If the parent
scope is cancelled while `connectAndServe()` is suspended, the cancellation is caught and mapped to
`ConnectAttempt.Network`, and the loop falls through to `markStale`/`emit`/`waitBackoffOrTrigger` on an
already-cancelling job. The next `while (isActive)` check usually unwinds it, but swallowing
`CancellationException` is a structured-concurrency anti-pattern that can run cleanup/emit work on a
dead scope and mask cancellation. Benign on the target today, but fragile.
**Fix:** Re-throw cancellation explicitly:
```kotlin
val result = try {
    connectAndServe()
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Throwable) {
    ConnectAttempt.Network
}
```

### WR-06: `parseObjectsList` guards the whole `map` — one non-primitive entry discards the entire objects list (silent total capability loss)

**File:** `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt:273-276`
**Issue:**
```kotlin
private fun parseObjectsList(result: JsonElement): List<String> =
    runCatching {
        (result.jsonObject["objects"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
    }.getOrDefault(emptyList())
```
`runCatching` wraps the entire `map`, so if Moonraker's `objects` array contains a single
non-string/non-primitive element (a nested object, or a JSON `null`), `it.jsonPrimitive` throws and the
WHOLE list collapses to `emptyList()`. Empty objects → empty `Capabilities` and an empty
`deriveSubscribeSet` → the session subscribes to nothing and the screen shows no heaters/temps: a
silent, total capability loss from one malformed entry. This violates the codebase's own house rule
(in `PrinterStateReducer`: "a bad field is skipped, never fatal") at the collection level. Adversarial
or version-skewed `objects.list` payloads are exactly the hostile-JSON case T-02-04 calls out.
**Fix:** Guard per element so one bad entry is dropped, not the whole list:
```kotlin
private fun parseObjectsList(result: JsonElement): List<String> =
    runCatching {
        (result.jsonObject["objects"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            ?: emptyList()
    }.getOrDefault(emptyList())
```

## Info

### IN-01: Misleading close/cancel comment in `MoonrakerSocket.onClosing` vs `RpcConnection.close`

**File:** `app/src/main/java/works/mees/dinghy/net/MoonrakerSocket.kt:82-86`, `RpcConnection.kt:54`
**Issue:** `onClosing` calls `connection?.close()` (cause = null), then `trySend(Closed(null))`, then
flow `close()`; `awaitClose` also calls `connection?.close()`. The second is a no-op (idempotent
AtomicBoolean) — fine — but the `RpcConnection.close` "1000 = normal closure" comment describes behavior
that never occurs (see WR-03). Align the comment once WR-03 lands.
**Fix:** Update the comment to match implemented behavior.

### IN-02: `GoldenFixtures` uses `!!` on `frames`/`objectsList` resource lookups

**File:** `app/src/test/java/works/mees/dinghy/net/GoldenFixtures.kt:44,48-49`
**Issue:** `loadObject(name)["frames"]!!.jsonArray` and `result!!.jsonObject["objects"]!!.jsonArray`
use `!!` on fixture JSON. Test-only, so no runtime risk, but a renamed/truncated fixture yields a bare
NPE instead of a message naming the missing key — and production code rightly forbids `!!` on wire data.
**Fix:** Replace with `requireNotNull(...) { "fixture <name> missing 'frames'/'result.objects'" }`.

### IN-03: Redundant disjunct in `classifyIdentifyError` (subsumed by CR-01)

**File:** `app/src/main/java/works/mees/dinghy/net/RpcError.kt:66`
**Issue:** The `(code == CODE_INVALID_PARAMS && saysUnauthorized)` term is logically dead given the
trailing `|| saysUnauthorized`. Resolved by the CR-01 rewrite.
**Fix:** Resolved by CR-01.

### IN-04: `EXTRUDER_N` regex (and heater-name predicates) duplicated across two files

**File:** `app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt:107-110`,
`app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt:10-12`
**Issue:** `Regex("""extruder\d+""")` is declared privately in both files, alongside near-identical
`isExtruder`/`isHeaterObject` heater-name predicates. Two copies will drift if multi-extruder naming
rules change, producing inconsistent capability-vs-reduce behavior.
**Fix:** Hoist a single `internal val EXTRUDER_N` and shared heater predicates into one location in the
`state` package; reference from both.

### IN-05: `objects.subscribe` reply uses the same fixture as `objects.query` by default

**File:** `app/src/test/java/works/mees/dinghy/net/SessionTestHarness.kt:82`
**Issue:** The subscribe reply defaults to `snapshotJson` (the query fixture). Real Moonraker returns
the same shape, so this is acceptable, but a distinct fixture would make the CR-02 seed-from-subscribe
path self-documenting and let a default test prove the subscribe seed is consumed (not just the override
path).
**Fix:** Add a distinct golden `objects_subscribe_snapshot.json` and default `subscribeSnapshotJson` to it.

---

_Reviewed: 2026-05-30_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
