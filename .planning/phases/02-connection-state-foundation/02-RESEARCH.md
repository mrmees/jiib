# Phase 2: Connection & State Foundation - Research

**Researched:** 2026-05-30
**Domain:** Moonraker JSON-RPC over WebSocket + REST; Kotlin coroutines/Flow state spine on Android (minSdk 23)
**Confidence:** HIGH (API shapes verified against official Moonraker docs + repo source; stack already locked & verified in Phase 1)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01 — Uncapped exponential backoff + jitter, retry forever.** No ceiling on backoff interval, no give-up budget. A printer can sit powered-off for weeks; don't spam the LAN.
- **D-02 — Spine MUST expose a manual "reconnect now" trigger** (e.g. `requestReconnectNow()`) that **cancels the pending backoff `delay()` and fires a connection attempt immediately.** Phase 2 owns the *hook only* — the disconnected-overlay UI that calls it is Phase 3. Make it unit-testable (backoff cancels, immediate attempt issued).
- **D-03 — Retain last-known values + a `stale`/`disconnected` marker.** On socket drop, `PrinterState` keeps last temps/position/job and exposes an explicit staleness/connection marker. A Wi-Fi blip must NOT wipe state — dimmed last-reading beats blank on an always-on display.
- **D-04 — The resync handshake overwrites stale state with truth on reconnect.** After `identify → objects.query → objects.subscribe`, the fresh `objects.query` snapshot replaces retained values (CONN-04). The connection-state `StateFlow` (connecting/connected/disconnected/error) is always observable (CONN-06).
- **D-05 — Static config via gitignored `local.properties` → `BuildConfig` fields.** Host/port/optional API key read from a gitignored properties file into `BuildConfig` at build time. Nothing secret committed. App can be hand-launched on the tablet to watch it connect/reconnect.
- **D-06 — Auth reality: the test-bed Ender 5 Plus Moonraker is OPEN / trusted-client (no API key).** On-device run exercises the **no-auth** path only. The full optional-auth flow (oneshot-token + `X-Api-Key` + graceful `401`) is proven in **mock-socket tests only**. Success-criterion #5 is satisfied at the mock-socket level, not on live hardware. The planner must make the auth path fully mock-testable.

### Claude's Discretion
- **Golden-frame fixtures:** Capture real `notify_status_update` / `objects.query` frames from the live Ender 5 Plus to seed the mock-socket golden corpus. Hand-author only adversarial/edge cases (interleaved-`notify_*` for STATE-05, `401`, klippy shutdown/error).
- **Capabilities model scope (STATE-02):** Model what v1 panels gate on (heaters incl. bed, extruder count, fans, macros, power devices) as an immutable, pure-function-derived `Capabilities` object, re-run on every reconnect. Don't over-build a generic mirror of `objects.list`. Keep it a pure, unit-tested function (input: `printer.objects.list` response → output: `Capabilities`).
- **Throttle/conflation mechanism (STATE-03):** Planner's call, but one constraint is load-bearing — the ~2–4 Hz conflation applies to **status updates** (latest-wins is fine); it must **NOT** be applied to the `notify_gcode_response` line stream that Console (Phase 7) consumes, where conflation would silently drop lines. Keep the raw event bus separable from the throttled state.
- Module/package layout, the `callbackFlow` socket bridge, the JSON-RPC `Map<id, CompletableDeferred>` correlation layer, and reconnect coroutine supervision structure are the planner/executor's call (stack itself is locked).

### Deferred Ideas (OUT OF SCOPE)
- **Disconnected-printer overlay UI** (immediate-retry button calling `requestReconnectNow()`) → Phase 3.
- **User-facing connection config screen (CONN-01)** → Phase 3 (UI + DataStore). Phase 2 stays on static/dev config.
- No panels, no app shell, no foreground service, no navigation, no Emergency Stop button, no UI primitives.
- No reuse of `CleartextMoonrakerSmokeTest.kt` (disposable Phase-1 probe; reference only).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CONN-02 | Optional Moonraker auth (API key / trusted-client) on websocket + REST | Oneshot-token flow (`access.oneshot_token` → `ws://…/websocket?token=`), `X-Api-Key` REST header, graceful `401`/`-32602 Unauthorized` handling — all documented below (§ Auth Handshake). Mock-testable per D-06. |
| CONN-03 | Long-lived websocket + auto-reconnect with backoff | OkHttp `WebSocket` + `callbackFlow` bridge, structured-concurrency reconnect supervisor with uncapped backoff+jitter (D-01) and `requestReconnectNow()` (D-02) — § Reconnect / Backoff. |
| CONN-04 | Full resync handshake on every (re)connect | Exact sequence `identify → objects.query (snapshot) → objects.subscribe` with verified JSON shapes — § Resync Handshake. |
| CONN-06 | Connection state always visible | `ConnectionState` `StateFlow` (Connecting/Connected/Disconnected/Error) emitted by the reconnect supervisor — § State Architecture. |
| STATE-01 | Merge `notify_status_update` partial diffs into one retained source-of-truth | Diff-merge algorithm over `JsonObject` into `PrinterState`; verified that updates are partial diffs and client must init from the subscribe snapshot — § Diff Merge. |
| STATE-02 | Detect capabilities from `printer.objects.list`, gate UI | `objects.list` returns a flat `objects: [string]` array; pure derive function maps it to `Capabilities` — § Capability Detection. |
| STATE-03 | Throttle high-rate streams to ~2–4 Hz | Conflate at the StateFlow layer (`sample`/`conflate`), keep `notify_gcode_response` un-throttled — § Conflation. |
| STATE-04 | Klippy lifecycle drives primary route | `klippy_state` from `server.info` + `notify_klippy_ready/shutdown/disconnected` + `webhooks.state` — exposed as first-class `KlippyState` field — § Klippy Lifecycle. |
| STATE-05 | Correlate JSON-RPC responses by `id`, never assume in-order arrival | `Map<id, CompletableDeferred<JsonElement>>` correlation; notifications distinguished by `method`+absence of `id` — § JSON-RPC Correlation. |
</phase_requirements>

## Summary

This phase builds a **headless Moonraker connection/state spine**. The good news: the locked stack
(OkHttp websocket + `callbackFlow`, kotlinx.serialization JSON-RPC, `Map<id, CompletableDeferred>`,
coroutines/Flow `StateFlow`) is exactly the idiomatic shape for Moonraker's protocol, and the Phase-1
smoke test already proved the wire format end-to-end on real hardware. The research effort was
therefore concentrated on **pinning exact API shapes against official docs** so the planner can write
concrete, testable tasks rather than discover shapes during implementation.

The protocol is **JSON-RPC 2.0 over a single `/websocket`**. Requests carry a client-chosen `id`;
successful responses echo `{result, id}`; errors return `{error: {code, message}, id}`; **notifications
have a `method` and NO `id`** — that distinction is the entire basis of STATE-05 correlation. Status
updates arrive as `notify_status_update` whose `params` is a **2-element array** `[ {changed objects}, eventtime ]`
delivered as **partial diffs** — the client MUST seed its state from the `objects.subscribe` snapshot
and merge diffs on top (STATE-01). Capability detection is a pure function over `printer.objects.list`
(a flat `objects: [string]` array). The Klippy lifecycle (`klippy_state`: disconnected/startup/ready/
error/shutdown) comes from `server.info` and the `notify_klippy_*` notifications (which carry **no params**).

The resync handshake is a fixed sequence on every (re)connect: `server.connection.identify` →
`printer.objects.query` (full snapshot, overwrites stale state per D-04) → `printer.objects.subscribe`
(register diffs). Auth is **optional and, on the test bed, absent (D-06)** — but the mock-tested path is:
oneshot-token (`access.oneshot_token`, base32, single-use, 5 s TTL) appended to the ws URL as `?token=`,
`X-Api-Key` header on REST, and the two failure shapes — **HTTP 401 on REST** and **JSON-RPC error
`code: -32602, message: "Unauthorized"` on the `server.connection.identify` call** — surfaced as a
graceful `ConnectionState.Error(AuthRequired)` rather than a crash.

**Primary recommendation:** Build the spine as four cleanly separated, independently-testable seams:
(1) a `callbackFlow` raw-frame socket over OkHttp; (2) a `JsonRpcClient` that owns the `id` map and
splits responses vs notifications; (3) a `MoonrakerSession`/reconnect supervisor that runs the
handshake and emits `ConnectionState`; (4) a pure `PrinterStateReducer` (snapshot-seed + diff-merge)
plus a pure `deriveCapabilities()`. Inject the socket factory so a `FakeWebSocket` can replay golden
Ender-5-Plus frames and inject adversarial interleaving — that mock seam is what makes all five success
criteria provable off-hardware.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Raw websocket transport, ping/pong keepalive | Transport (OkHttp `WebSocket`) | — | OkHttp owns TLS/keepalive; one client serves ws + REST. |
| Frame ↔ Flow bridge | Transport (`callbackFlow`) | — | `WebSocketListener.onMessage → trySend`; close on `onClosed`/`onFailure`. |
| JSON-RPC framing, `id` correlation, notify routing | Protocol (`JsonRpcClient`) | — | Splits `{result,id}`/`{error,id}` from `{method,params}` notifications; owns `Map<id, CompletableDeferred>`. |
| Resync handshake (identify→query→subscribe) | Session (reconnect supervisor) | Protocol | Orchestrates ordered RPC calls; emits `ConnectionState`. |
| Reconnect/backoff supervision | Session | — | Structured-concurrency loop; uncapped backoff+jitter; `requestReconnectNow()`. |
| Auth (oneshot-token, `X-Api-Key`, 401) | Session + Transport | Protocol (REST) | Token fetched via REST, appended to ws URL; identify error → auth state. |
| Snapshot seed + diff merge → `PrinterState` | State reducer (pure) | — | Pure function: (current, diff) → next. Unit-testable, no IO. |
| Capability detection | State reducer (pure) | — | Pure: `objects.list` → `Capabilities`. Re-run each reconnect. |
| Throttle/conflation to 2–4 Hz | State (Flow operators) | — | `sample`/`conflate` on the status StateFlow; raw gcode stream un-throttled. |
| Klippy lifecycle exposure | State | Session | `klippy_state` first-class field driving (future) routing. |

## Standard Stack

The stack is **LOCKED** (CLAUDE.md "Networking deep-dive" + Phase-1 `01-RESEARCH.md`). Do NOT re-decide.
All versions below are already pinned in `gradle/libs.versions.toml`. No new dependency is required to
build this phase — the catalog already has OkHttp, kotlinx.serialization, coroutines, and Retrofit.

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| OkHttp | 4.12.0 | WebSocket (`notify_*` push) + HTTP engine for REST | `[CITED: gradle/libs.versions.toml]` minSdk 21; one TLS/pool/timeout config for both ws and REST; trivially wrapped in `callbackFlow`. |
| kotlinx.serialization (json) | 1.7.3 | All JSON incl. JSON-RPC envelopes & loose `notify_*` payloads | `[CITED: libs.versions.toml]` compile-time codegen (fast on weak CPU), `JsonElement`/`JsonObject` walk is ideal for Moonraker's heterogeneous partial diffs. |
| kotlinx-coroutines | 1.9.0 | `callbackFlow` bridge, `StateFlow`/`SharedFlow`, structured-concurrency reconnect | `[CITED: libs.versions.toml]` `callbackFlow`+`CompletableDeferred`+supervisor scope are the exact primitives the design needs. |
| Retrofit | 2.11.0 | Moonraker REST (oneshot-token fetch, simple GETs) | `[CITED: libs.versions.toml]` pairs with OkHttp + kotlinx-serialization converter. **Note:** REST surface in Phase 2 is small (oneshot-token, optional `server/info` GET). A plain OkHttp `Call` is also acceptable — planner's call. |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| retrofit-converter-kotlinx-serialization | 2.11.0 | Typed REST deserialization | If REST goes through Retrofit. `[CITED: libs.versions.toml]` |
| kotlinx-coroutines-test | (add to catalog) | `runTest`, `TestScope`, virtual time for backoff/conflation tests | **GAP** — not yet in catalog. Needed for deterministic backoff/throttle unit tests. See § Validation Wave 0. `[ASSUMED]` version pin needed (typically tracks `coroutines` = 1.9.0). |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| OkHttp ws | Ktor client ws | More coroutine-idiomatic but pulls a 2nd HTTP engine. Only worth it for KMP — this is Android-only. (CLAUDE.md locked.) |
| OkHttp ws | Scarlet / Java-WebSocket | Scarlet unmaintained; Java-WebSocket too bare. Rejected in CLAUDE.md. |
| kotlinx.serialization | Moshi/Gson | Gson reflection-slow; Moshi fine but not Kotlin-native. Locked to kotlinx. |

**Installation:** No new runtime deps. The only catalog addition is the **test** dependency
`kotlinx-coroutines-test` (and `org.jetbrains.kotlinx:kotlinx-serialization-json` is already present
for fixtures). Add to `[versions]`/`[libraries]` in `gradle/libs.versions.toml`, do not inline.

**Version verification:** All runtime versions are already pinned and were verified in Phase-1
`01-RESEARCH.md` against the AndroidX/Square ecosystems. No registry re-verification performed this
session because no new runtime package is introduced — the locked catalog is the source of truth.

## Package Legitimacy Audit

> No new external runtime packages are introduced in this phase. All runtime dependencies (OkHttp,
> Retrofit, kotlinx.serialization, kotlinx-coroutines) are already pinned and in use from Phase 1.

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| com.squareup.okhttp3:okhttp 4.12.0 | Maven Central | mature | very high | github.com/square/okhttp | n/a (Maven, not npm/PyPI) | Already in use (Phase 1) |
| org.jetbrains.kotlinx:kotlinx-serialization-json 1.7.3 | Maven Central | mature | very high | github.com/Kotlin/kotlinx.serialization | n/a | Already in use |
| org.jetbrains.kotlinx:kotlinx-coroutines-android 1.9.0 | Maven Central | mature | very high | github.com/Kotlin/kotlinx.coroutines | n/a | Already in use |
| com.squareup.retrofit2:retrofit 2.11.0 | Maven Central | mature | very high | github.com/square/retrofit | n/a | Already in use |
| org.jetbrains.kotlinx:kotlinx-coroutines-test 1.9.0 | Maven Central | mature | very high | (same as coroutines) | n/a | **NEW (test-only)** — add to catalog |

**Packages removed due to slopcheck [SLOP] verdict:** none.
**Packages flagged as suspicious [SUS]:** none.

*slopcheck targets npm/PyPI hallucination vectors; these are mature first-party Maven Central artifacts
from Square and JetBrains with well-known coordinates already vetted in Phase 1. The one new addition
is the official test sibling of an already-trusted coroutines package. Risk is negligible.*

## Architecture Patterns

### System Architecture Diagram

```
                         BuildConfig (host/port/apiKey?)         [D-05 static config]
                                    │
                                    ▼
   ┌──────────────────────── MoonrakerSession (reconnect supervisor) ──────────────────────┐
   │  emits ConnectionState StateFlow: Connecting│Connected│Disconnected│Error(AuthReq…)    │
   │                                                                                         │
   │   reconnect loop (uncapped backoff+jitter, D-01) ◄── requestReconnectNow() [D-02]      │
   │        │                                                                                │
   │        ▼  (per connect attempt)                                                         │
   │   [optional auth: REST access.oneshot_token ──► append ?token= to ws URL]   CONN-02    │
   │        │                                                                                │
   │        ▼                                                                                │
   │   OkHttp WebSocket  ──onMessage──►  callbackFlow<String> (raw frames)                  │
   │        ▲                                   │                                            │
   │        │ send(jsonText)                    ▼                                            │
   │   ┌────┴───────────────── JsonRpcClient ───────────────────────┐                       │
   │   │  request(method,params,id) → suspend  (Map<id,Deferred>)   │  ◄── STATE-05         │
   │   │  inbound split:                                            │                        │
   │   │    has "id" + result/error ─► complete Deferred[id]        │                        │
   │   │    has "method", no "id"   ─► route to notify SharedFlow   │                        │
   │   └──────────┬───────────────────────────────┬────────────────┘                        │
   │              │ (handshake calls)              │ (notifications by method)               │
   │              ▼                                ▼                                          │
   │   HANDSHAKE [CONN-04]:              notify_status_update ─► statusDiff SharedFlow        │
   │     identify                        notify_gcode_response ─► gcodeLine SharedFlow ───────┼──► (raw, UN-throttled, Console/Phase7)
   │     objects.query (snapshot) ──┐    notify_klippy_ready/shutdown/disconnected ─► klippy │
   │     objects.subscribe          │                                                         │
   └────────────────────────────────┼─────────────────────────────────────────────────────┘
                                     ▼
                      ┌─── PrinterStateReducer (PURE) ───┐
   objects.list ─► deriveCapabilities() ─► Capabilities  │   [STATE-02]
   snapshot ─► seed ; diff ─► merge into ────────────────┼─► PrinterState StateFlow
                                                          │      (retains last-known + stale marker D-03)
                                                          │      conflated to 2–4 Hz [STATE-03]
                                                          │      klippyState first-class field [STATE-04]
                                                          └──────────────────────────────────────────────►
                                                                 (consumed later by Compose AND Views)
```

### Recommended Project Structure
```
app/src/main/java/works/mees/dinghy/
├── net/
│   ├── MoonrakerSocket.kt        # OkHttp WebSocket → callbackFlow<String> bridge (+ send)
│   ├── JsonRpcClient.kt          # id-correlation map, request(suspend), notify routing
│   ├── JsonRpc.kt                # @Serializable envelopes (Request, Response, Error, Notification)
│   └── MoonrakerSession.kt       # reconnect supervisor, handshake, ConnectionState, requestReconnectNow()
├── auth/
│   └── MoonrakerAuth.kt          # oneshot-token fetch, ?token= URL build, X-Api-Key, 401→AuthRequired
├── state/
│   ├── PrinterState.kt           # immutable @Immutable data model + ConnectionState + KlippyState
│   ├── PrinterStateReducer.kt    # PURE seed + diff-merge
│   ├── Capabilities.kt           # immutable Capabilities model
│   └── deriveCapabilities.kt     # PURE objects.list → Capabilities
└── config/
    └── DevConfig.kt              # reads BuildConfig host/port/apiKey (D-05)

app/src/test/…                    # JVM unit: reducer, capabilities, id-correlation, backoff (virtual time)
app/src/test/resources/golden/    # captured Ender-5-Plus frames + hand-authored adversarial frames
```

### Pattern 1: callbackFlow bridge over OkHttp WebSocketListener
**What:** Convert push-based `WebSocketListener` callbacks into a cold `Flow<String>` of raw text frames.
**When to use:** The single inbound path for all JSON-RPC traffic.
```kotlin
// Source: idiomatic OkHttp+coroutines pattern; OkHttp WebSocket API [CITED: square.github.io/okhttp]
fun OkHttpClient.moonrakerFrames(request: Request): Flow<SocketEvent> = callbackFlow {
    val listener = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, r: Response) { trySend(SocketEvent.Open(ws)) }
        override fun onMessage(ws: WebSocket, text: String) { trySend(SocketEvent.Frame(text)) }
        override fun onClosing(ws: WebSocket, code: Int, reason: String) { close() }
        override fun onFailure(ws: WebSocket, t: Throwable, r: Response?) { close(t) }
    }
    val ws = newWebSocket(request, listener)
    awaitClose { ws.cancel() }   // structured-concurrency cancel propagates to the socket
}
// readTimeout(0) on the OkHttpClient — websockets have no read timeout (see Phase-1 smoke test).
```

### Pattern 2: JSON-RPC id correlation (STATE-05)
**What:** Suspend `request()` returns the matching response regardless of interleaved notifications.
**When to use:** Every command/query (identify, objects.query, objects.subscribe, emergency_stop later).
```kotlin
// Correlation map keyed by request id; notifications (no id) never resolve a Deferred.
private val pending = mutableMapOf<Long, CompletableDeferred<JsonElement>>()  // guard with a Mutex
private val nextId = AtomicLong(1)

suspend fun request(method: String, params: JsonElement? = null): JsonElement {
    val id = nextId.getAndIncrement()
    val deferred = CompletableDeferred<JsonElement>()
    mutex.withLock { pending[id] = deferred }
    socket.send(buildRequest(method, params, id))         // {"jsonrpc":"2.0","method":…,"params":…,"id":id}
    return deferred.await()                                 // resolved out-of-band by dispatch()
}

fun dispatch(text: String) {
    val obj = json.parseToJsonElement(text).jsonObject
    val id = obj["id"]?.jsonPrimitive?.longOrNull
    when {
        id != null && obj.containsKey("result") -> resolve(id, obj["result"]!!)
        id != null && obj.containsKey("error")  -> fail(id, obj["error"]!!.toRpcError())
        obj.containsKey("method")               -> routeNotification(obj["method"]!!, obj["params"])
    }
}
```
**Why it's correct:** A response is matched by `id`, NEVER by arrival position. A `notify_status_update`
arriving between an `objects.query` request and its reply does not satisfy the query — it routes to the
status SharedFlow instead. This is the literal STATE-05 requirement and the success-criterion-#4 test.

### Pattern 3: Reconnect supervisor with uncapped backoff + immediate-retry hook (D-01/D-02)
```kotlin
// Structured-concurrency reconnect loop; uncapped backoff, jitter, retry forever.
private val reconnectNow = Channel<Unit>(Channel.CONFLATED)
fun requestReconnectNow() { reconnectNow.trySend(Unit) }   // D-02: cancels pending delay

private suspend fun runForever() = supervisorScope {
    var attempt = 0
    while (isActive) {
        _connectionState.value = ConnectionState.Connecting
        val ok = runCatching { connectAndServe() }.isSuccess   // returns when socket dies
        attempt = if (ok) 0 else attempt + 1
        _connectionState.value = ConnectionState.Disconnected
        val backoff = jitter(base * 2.0.pow(attempt))          // NO ceiling (D-01)
        select {                                               // backoff OR manual trigger
            onTimeout(backoff) {}
            reconnectNow.onReceive {}                          // D-02: skip the wait
        }
    }
}
```
**Note for tests:** drive `connectAndServe` against a `FakeWebSocket`; use `kotlinx-coroutines-test`
virtual time so backoff delays are deterministic and instantaneous in unit tests.

### Pattern 4: Snapshot-seed + diff-merge (STATE-01) and conflation (STATE-03)
```kotlin
// Reducer is PURE — (current, incoming) -> next. No IO, no coroutines. Trivially unit-tested.
fun reduceSnapshot(status: JsonObject): PrinterState   // from objects.query / objects.subscribe reply
fun reduceDiff(current: PrinterState, diff: JsonObject): PrinterState  // from notify_status_update[0]

// Conflation: applied to the STATUS StateFlow only.
val printerState: StateFlow<PrinterState> =
    statusDiffFlow.scan(seed) { acc, diff -> reduceDiff(acc, diff) }
        .sample(250.milliseconds)        // ~4 Hz; latest-wins is fine for temps/position (STATE-03)
        .stateIn(scope, SharingStarted.Eagerly, seed)

// gcode lines are SEPARATE and NEVER conflated (Console/Phase 7 must see every line) — Discretion.
val gcodeResponses: SharedFlow<String> = gcodeLineFlow.shareIn(scope, SharingStarted.Eagerly)
```

### Anti-Patterns to Avoid
- **Assuming the next frame is your answer.** Moonraker interleaves notifications with responses. Always correlate by `id` (STATE-05). The Phase-1 smoke test already does this — carry the discipline, not the file.
- **Conflating the gcode-response stream.** `notify_gcode_response` lines are a log; `sample`/`conflate` silently drops lines → broken Console later. Keep it a separate un-throttled `SharedFlow` (Discretion constraint).
- **Treating `notify_status_update` as a full state.** It's a **partial diff**. If you replace instead of merge, fields not in the diff vanish. Seed from the subscribe/query snapshot, then merge diffs (STATE-01).
- **Blanking state on disconnect.** D-03 — retain last-known + set a stale flag. Don't reset `PrinterState` to empty on socket drop; only overwrite on the post-reconnect `objects.query` (D-04).
- **Calling `server.connection.identify` twice.** It is one-shot per connection; repeated calls error. Call it exactly once, first, per (re)connect.
- **Capped/give-up backoff.** D-01 forbids a ceiling and a give-up budget. Retry forever; let `requestReconnectNow()` be the escape hatch.
- **Reflection JSON / `Json{}` per-message.** Reuse one `Json { ignoreUnknownKeys = true; isLenient = true }` instance (Moonraker payloads are loose/heterogeneous — the Phase-1 posture is correct; carry it forward).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| WebSocket framing, ping/pong keepalive, TLS | Custom socket | OkHttp `WebSocket` | Battle-tested since 2016; one stack for ws+REST. |
| Push→Flow bridge | Manual listener+queue | `callbackFlow` + `awaitClose` | Correct backpressure & cancellation; ~20 lines. |
| Request/response correlation | Sequential await / index matching | `Map<id, CompletableDeferred>` | The ONLY correct approach under interleaving (STATE-05). |
| JSON-RPC envelope parsing | Manual string parsing | kotlinx.serialization `JsonObject`/`JsonElement` walk | Loose Moonraker payloads; compile-time, no reflection. |
| Throttling a hot stream | Manual timers/`Handler.postDelayed` | Flow `sample()` / `conflate()` | Coroutine-native, testable with virtual time. |
| Backoff scheduling | `Thread.sleep`/`Handler` | coroutine `delay` in a `select`/`supervisorScope` | Cancellable, deterministic in `runTest`. |
| Reconnect lifecycle | Manual reconnect flags | structured-concurrency supervisor | Clean cancellation; one place owns connection state. |

**Key insight:** Every piece of this spine maps onto a coroutine/Flow primitive that already exists and is
deterministically testable. The risk is not "missing library" — it's **getting the diff-merge and the
id-correlation semantics exactly right**, which is precisely what the golden-frame mock tests pin down.

## Moonraker API Reference (verified shapes — the load-bearing deliverable)

All shapes `[CITED: moonraker.readthedocs.io/en/latest/external_api/…]` and cross-checked against
`[CITED: github.com/Arksine/moonraker/blob/master/docs/external_api/…]`. Confidence **HIGH**.

### JSON-RPC framing (over `ws://<host>:<port>/websocket`)
```jsonc
// Request (client → server) — id is client-chosen, unique
{ "jsonrpc": "2.0", "method": "printer.info", "params": { ... }, "id": 5445 }
// Success response (server → client) — echoes id
{ "jsonrpc": "2.0", "result": { ... }, "id": 5445 }
// Error response — echoes id (may be omitted for malformed requests)
{ "jsonrpc": "2.0", "error": { "code": 36000, "message": "Error Message" }, "id": 5445 }
// Notification (server → client, UNSOLICITED) — has method, has NO id
{ "jsonrpc": "2.0", "method": "notify_status_update", "params": [ { ... }, 578243.578 ] }
```
The `method`-present / `id`-absent distinction is the basis of STATE-05 routing.

### Resync handshake (CONN-04) — exact sequence & shapes
**1. `server.connection.identify`** (websocket-only; call ONCE, first):
```jsonc
// Request
{ "jsonrpc":"2.0", "method":"server.connection.identify",
  "params": { "client_name":"Dinghy Display", "version":"<app ver>",
              "type":"display", "url":"https://github.com/<repo>",
              "access_token": null, "api_key": null /* set only if auth */ }, "id": 4509 }
// Response
{ "result": { "connection_id": 1730367696 } }
```
- `type` must be one of: `web`, `mobile`, `desktop`, `display`, `bot`, `agent`, `other`. Use **`"display"`** — semantically correct for this product. `[CITED: external_api/server/]`

**2. `printer.objects.query`** — full snapshot (overwrites stale state, D-04):
```jsonc
// Request — null = all fields; or array of field names
{ "jsonrpc":"2.0", "method":"printer.objects.query",
  "params": { "objects": { "gcode_move": null, "toolhead": ["position","status"] } }, "id":4654 }
// Response
{ "result": { "eventtime": 578243.57824499,
              "status": { "gcode_move": { ... }, "toolhead": { "position":[0,0,0,0], "status":"Ready" } } } }
```

**3. `printer.objects.subscribe`** — register for diffs (same params/response shape as query):
```jsonc
{ "jsonrpc":"2.0", "method":"printer.objects.subscribe",
  "params": { "objects": { "heater_bed": null, "extruder": null, "print_stats": null, ... } }, "id":5434 }
// Response = same {eventtime, status} snapshot as query → ALSO use it to seed state.
```
> **Key behavior `[CITED: external_api/printer/]`:** "Subscriptions are now pushed as **diffs**. Clients
> will only receive updates for subscribed items **when that data changes**. This requires that clients
> **initialize their local state with the data returned from the subscription request**." → STATE-01.

**`printer.objects.list`** (capability source, STATE-02):
```jsonc
// Request
{ "jsonrpc":"2.0", "method":"printer.objects.list", "id":1454 }
// Response — FLAT array of available object names
{ "result": { "objects": ["gcode","webhooks","configfile","heater_bed","extruder",
                           "fan","print_stats","virtual_sdcard","display_status","toolhead",
                           "gcode_macro START_PRINT","gcode_macro LOAD_FILAMENT", ...] } }
```
Capability derivation walks these strings (see § Capability Detection).

### Notifications (the `notify_*` set this phase must handle)
| Method | `params` shape | Contents | Phase-2 routing |
|--------|----------------|----------|-----------------|
| `notify_status_update` | **array[2]** | `[ {changed objects, keyed by name}, eventtime:float ]` | → status diff merge (throttled) STATE-01/03 |
| `notify_gcode_response` | **array[1]** | `[ "response message string" ]` | → gcode line `SharedFlow` (UN-throttled) — Console/Phase 7 |
| `notify_klippy_ready` | **no params** | — | → `KlippyState.Ready` STATE-04 |
| `notify_klippy_shutdown` | **no params** | — | → `KlippyState.Shutdown` |
| `notify_klippy_disconnected` | **no params** | — | → `KlippyState.Disconnected` (Klippy↔Moonraker link down; ws may still be up) |
| `notify_proc_stat_update` | **array[1]** | `[ {moonraker proc stats object} ]` | optional; low value for a printer screen — can ignore in v1 |

`[CITED: external_api/jsonrpc_notifications/ + github …/jsonrpc_notifications.md]`. The three
`notify_klippy_*` carrying **no params** is verified directly from repo source (HIGH confidence).

**Other notifications present (NOT needed in Phase 2, noted for later phases):**
`notify_history_changed` (array[1] obj — Job Status/Phase 6), `notify_filelist_changed` (array[1] obj —
Files/Phase 5), `notify_service_state_changed`, `notify_power_changed`-equivalents via service state.
There is **no `notify_klippy_started`** in current docs — `startup` is observed via `server.info`'s
`klippy_state` and `webhooks.state`, not a dedicated notification.

### Klippy lifecycle (STATE-04) — how to observe ready/printing/startup/error/shutdown
Two independent axes — keep them distinct:
- **`klippy_state`** (the Klipper *host* lifecycle): from `server.info.klippy_state` ∈
  `disconnected | startup | ready | error | shutdown` `[CITED: external_api/server/]`, kept current by
  `notify_klippy_ready` / `notify_klippy_shutdown` / `notify_klippy_disconnected`. Also exposed per-object
  as `webhooks.state` (+ `webhooks.state_message` for the human-readable reason) in `objects.query`.
- **`print_stats.state`** (the *print job* lifecycle): `standby | printing | paused | complete | error |
  cancelled` `[CITED: docs/printer_objects.md]`. This is what drives "printing → job status" routing later.

> Routing note for Phase 3: `ready→main`, `printing→job` is `klippy_state==ready && print_stats.state==printing`;
> `startup/error/shutdown→splash` keys off `klippy_state`. Phase 2 only **exposes** these fields; it does
> not route. Seed `klippy_state` at connect from `server.info` (REST or RPC), then keep it live via the
> notifications.

### Auth handshake (CONN-02) — mock-tested only per D-06
- **Oneshot token (websocket auth path):**
  ```jsonc
  // REST or RPC: access.oneshot_token  →  result is a base32 string, single-use, expires in 5s
  { "jsonrpc":"2.0", "method":"access.oneshot_token", "id":1323 }
  // result: "APDBEGHUTBUD6SOAYBPF3KE5BRMO7YSL"
  ```
  Then connect: `ws://<host>:<port>/websocket?token=APDBEGHUTBUD6SOAYBPF3KE5BRMO7YSL`.
  `[CITED: external_api/authorization/]`. **5 s TTL / single-use** → fetch the token immediately before
  opening the socket, on every (re)connect attempt when a key is configured.
- **`X-Api-Key` (REST auth path):** include header `X-Api-Key: <key>` on each HTTP request when configured.
  `[CITED: external_api/authorization/]` Also can be passed to `server.connection.identify` as `api_key`.
- **Failure shapes (surface as `ConnectionState.Error(AuthRequired)`, never crash):**
  - **REST:** HTTP **401** on the request (e.g. the oneshot-token fetch itself if a key is required and absent/wrong). `[CITED: external_api/authorization/]`
  - **WebSocket:** the socket **opens**, but `server.connection.identify` returns a JSON-RPC error
    **`{ "error": { "code": -32602, "message": "Unauthorized" } }`** when credentials are missing/invalid.
    `[VERIFIED: moonraker repo issue tracker + Mainsail docs]` (the `-32602 Unauthorized` on identify is the
    canonical "auth required" signal over ws). Map this error to the graceful auth state.
  - Also possible: unauthorized due to client IP not in `trusted_clients` range → same `-32602` surface.
- **Default reality (D-06):** the test-bed printer is OPEN / trusted-client. `access_token`/`api_key`
  are `null`; identify succeeds; no token needed. The whole auth branch is exercised **only** in
  mock-socket tests that inject the 401 / `-32602` frames.

### REST endpoints used this phase
| Endpoint | Use | Result key(s) |
|----------|-----|---------------|
| `GET /server/info` | seed `klippy_state` / connectivity at connect | `klippy_connected`, `klippy_state`, `warnings`, `components` |
| `GET /printer/info` | optional secondary state seed | `state`, `state_message`, `hostname`, `software_version` |
| `access.oneshot_token` (RPC or `GET /access/oneshot_token`) | ws token when auth configured | base32 token string |

(`server/info` + `printer/info` `result` keys match what the Phase-1 smoke test already asserts.)

## Runtime State Inventory

> Greenfield connection layer — but this phase introduces **build-time configuration state** worth an
> explicit inventory because D-05 routes secrets through `local.properties`.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — no DB/datastore in this phase (DataStore is Phase 3). | None — verified by REQUIREMENTS traceability (CONN-01/DataStore → Phase 3). |
| Live service config | None added by this phase. | None. |
| OS-registered state | None — no service/foreground component (Phase 3). | None. |
| Secrets/env vars | **`local.properties` keys → `BuildConfig` fields** (host, port, optional API key) per D-05. `local.properties` is already gitignored (confirmed in repo CLAUDE.md). | Add `buildConfigField` wiring in `app/build.gradle.kts`; document the property keys; ensure NO real IP/key committed. Provide safe placeholder defaults so a fresh clone still compiles. |
| Build artifacts | `BuildConfig` is regenerated from `local.properties` each build — stale values possible if a dev edits the file without rebuilding. | Document "edit `local.properties` → rebuild" in plan; not a migration, just a build note. |

## Common Pitfalls

### Pitfall 1: Diff-merge that replaces instead of merges
**What goes wrong:** Using a `notify_status_update` payload to *replace* the object's state drops every
field not present in that particular diff (e.g. a temp-only update wipes the target).
**Why it happens:** Treating diffs like snapshots; not seeding from the subscribe reply.
**How to avoid:** Pure `reduceDiff(current, diff)` deep-merges per object; seed from the `objects.subscribe`
(or `objects.query`) `status` snapshot first. Golden-frame test: seed → apply N captured diffs → assert
the full expected state.
**Warning signs:** Fields flicker to 0/empty between updates; target temp resets after a temperature tick.

### Pitfall 2: Out-of-order response correlation
**What goes wrong:** A query's reply is mismatched because a `notify_*` arrived first and was consumed as "the answer."
**Why it happens:** Sequential `await next frame` instead of id-keyed correlation.
**How to avoid:** `Map<id, CompletableDeferred>`; notifications (no id) never resolve a deferred.
**Warning signs:** Intermittent "wrong result" failures that only appear on a busy/printing printer (lots of notifications).

### Pitfall 3: Conflating the gcode-response line stream
**What goes wrong:** Console (Phase 7) silently misses lines because the status throttle also throttled gcode responses.
**Why it happens:** One shared throttled bus for all notifications.
**How to avoid:** Separate the un-throttled `notify_gcode_response` `SharedFlow` from the conflated status `StateFlow` at the routing layer (explicit Discretion constraint).
**Warning signs:** Multi-line `M115`/error bursts show only the last line later.

### Pitfall 4: Blanking state on a transient drop
**What goes wrong:** A 2-second Wi-Fi blip clears the whole screen on an always-on display.
**Why it happens:** Resetting `PrinterState` on `onFailure`/`onClosed`.
**How to avoid:** D-03 — retain last-known values, flip a `stale`/`disconnected` marker; only overwrite on the post-reconnect `objects.query` (D-04).
**Warning signs:** Screen flashes empty on every reconnect; temps blank for a beat after recovery.

### Pitfall 5: Calling identify twice / out of order
**What goes wrong:** Second `server.connection.identify` errors; or notifications arrive before identify and are dropped.
**Why it happens:** Re-running handshake on a still-open socket, or subscribing before identify.
**How to avoid:** Strict per-connection sequence: identify → query → subscribe, exactly once each, before processing diffs.
**Warning signs:** `identify` error responses in logs; missing initial state.

### Pitfall 6: Backoff that never fires immediately on user intent
**What goes wrong:** Printer comes back online but the app waits out a multi-minute (uncapped) backoff.
**Why it happens:** `requestReconnectNow()` not wired to cancel the pending `delay`.
**How to avoid:** `select { onTimeout(backoff){} ; reconnectNow.onReceive{} }` (D-02). Unit-test: trigger mid-backoff → immediate attempt.
**Warning signs:** "Reconnect now" tap (Phase 3) does nothing for minutes.

## Code Examples

### Building a subscribe request for the v1 object set
```kotlin
// Subscribe to exactly what the spine + future v1 panels need; null = all fields of the object.
private val v1Subscriptions = buildJsonObject {
    putJsonObject("objects") {
        put("webhooks", JsonNull)        // klippy state/state_message (STATE-04)
        put("print_stats", JsonNull)     // job lifecycle (STATE-04 / Phase 6)
        put("virtual_sdcard", JsonNull)  // progress (Phase 6)
        put("display_status", JsonNull)  // progress/message (Phase 6)
        put("toolhead", JsonNull)        // position, homed_axes (Move/Phase 4)
        put("gcode_move", JsonNull)      // speed/extrude factor, gcode_position (Phase 4/6)
        put("heater_bed", JsonNull)      // temp/target/power (Temp/Phase 4)
        put("extruder", JsonNull)        // temp/target/power/can_extrude (Temp/Extrude/Phase 4)
        // fans, additional extruders, heater_generic.* added from Capabilities at runtime
    }
}
// NOTE: the concrete object set per printer comes from objects.list; this is the v1 superset to request
// (Moonraker silently ignores objects the printer doesn't have? — verify: planner should test against
//  the golden objects.list and only request objects present, to avoid errors).  [ASSUMED — see A3]
```

### Capability detection (pure, STATE-02)
```kotlin
// Source: objects.list → Capabilities. Pure function, unit-tested with golden objects.list.
data class Capabilities(
    val hasBed: Boolean,
    val extruderCount: Int,          // "extruder", "extruder1", "extruder2", …
    val fans: List<String>,          // "fan", "fan_generic X", "heater_fan X", "controller_fan X"
    val macros: List<String>,        // "gcode_macro NAME" → NAME (underscore-prefixed hidden later)
    val powerDevices: List<String>,  // (from machine/device_power — see note)
    val heaters: List<String>,       // heater_bed, extruder*, heater_generic *
)

fun deriveCapabilities(objects: List<String>): Capabilities {
    val ext = objects.filter { it == "extruder" || it.matches(Regex("""extruder\d+""")) }
    val macros = objects.filter { it.startsWith("gcode_macro ") }.map { it.removePrefix("gcode_macro ") }
    val fans = objects.filter {
        it == "fan" || it.startsWith("fan_generic ") || it.startsWith("heater_fan ") ||
        it.startsWith("controller_fan ")
    }
    val heaters = objects.filter { it == "heater_bed" || it == "extruder" ||
        it.matches(Regex("""extruder\d+""")) || it.startsWith("heater_generic ") }
    return Capabilities(
        hasBed = "heater_bed" in objects,
        extruderCount = ext.size,
        fans = fans, macros = macros, heaters = heaters,
        powerDevices = emptyList(), // NOTE: power devices come from `machine.device_power.devices`,
                                    // NOT printer.objects.list. Out-of-scope detail for Phase 2;
                                    // model the field but leave population to a later phase. [ASSUMED A4]
    )
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Full-status pushes on every update | **Diff-only** subscription pushes | Moonraker API change (documented in api_changes) | Client MUST seed from snapshot + merge (STATE-01). Don't expect full objects each tick. |
| Docs at `/web_api/` single page | Split `/external_api/<topic>/` pages | Docs reorg (2025-ish) | Old `web_api` deep-links 404; use `external_api/{printer,server,authorization,jsonrpc_notifications,introduction}`. |

**Deprecated/outdated:**
- The monolithic `moonraker.readthedocs.io/en/latest/web_api/` URL — now 404; superseded by `external_api/*`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `notify_proc_stat_update` is low-value for a printer screen and can be ignored in v1. | Notifications | Low — purely additive if later needed. |
| A2 | `kotlinx-coroutines-test` should be added (test-only) for virtual-time backoff/throttle tests; version tracks coroutines 1.9.0. | Stack/Validation | Low — standard sibling artifact; planner verifies the exact coordinate at catalog-edit time. |
| A3 | Requesting an object in `objects.subscribe` that the printer lacks may error (so subscribe only to objects present in `objects.list`). | Code Examples | Medium — if Moonraker errors on unknown objects, a naive superset-subscribe fails on differently-configured printers. **Planner should make subscribe set derive from `objects.list` and add a golden-frame test.** Verify behavior against live printer. |
| A4 | Power devices come from `machine.device_power.devices`, not `printer.objects.list`. | Capability detection | Low — affects only where `powerDevices` is populated (deferred past Phase 2 anyway). |
| A5 | The `-32602 Unauthorized` error on `server.connection.identify` is the canonical ws auth-required signal (vs the socket upgrade being rejected). | Auth | Medium — drives how the mock 401/auth test is shaped. Sourced from issue tracker + Mainsail, not the primary API page; confirm the exact code/message string when authoring the adversarial fixture. |

## Open Questions (RESOLVED)

1. **Does `objects.subscribe`/`query` error on objects the printer doesn't define?** (A3) — **RESOLVED (planning):** Phase 2 derives the subscribe set from `objects.list` via the pure `deriveSubscribeSet(objects)` function (v1 superset ∩ detected objects), so the client never requests an undefined object. `DeriveCapabilitiesTest`/`deriveSubscribeSet` tests assert a minimal printer (no second extruder, no chamber heater) omits those from the subscribe set. The live-printer behavior for an undefined object is moot because we never send one; the on-device run confirms the derived set works against the real Ender 5 Plus.
   - Original: `objects.list` enumerates available objects; subscribe takes a subset. Unclear whether requesting a non-existent object errors or is silently dropped → sidestepped by deriving the subset from `objects.list`.

2. **Exact `error.code` for ws auth failure.** (A5) — **RESOLVED (planning, accepted uncertainty):** the exact code is left as MEDIUM-confidence and handled defensively. `classifyIdentifyError(...)` maps clearly-unauthorized shapes (`code:-32602 / "Unauthorized"`) → `AuthRequired`, classifies other RPC errors as `ProtocolError`/`ServerError`, and falls back to `AuthRequired` for unknown identify errors. The adversarial fixture is hand-authored with `-32602 / "Unauthorized"`. If a key can later be set on the test printer, capture the real frame to tighten the mapping — but no plan depends on knowing the exact code.
   - Original: identify returns a JSON-RPC error; community sources report `code:-32602, message:"Unauthorized"`. Unclear whether all auth-required paths use that exact code/message across Moonraker versions → handled by the typed-error classifier with a defensive `AuthRequired` fallback.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Live Ender 5 Plus Moonraker on LAN | golden-frame capture + on-device proof | ✓ (test bed exists, OPEN/no-auth) | Moonraker (current) | Mock-socket replay covers all logic; live run proves real wire format |
| Android build toolchain (JDK 21, SDK 35) via `E:\Android\gw.bat` | building/running tests | ✓ | Adoptium JDK 21 / SDK 35 | — (Phase-1 established) |
| Real flox device (LineageOS 18.1 / API 30) | on-device connect/reconnect proof | ✓ | API 30 (genuine Adreno 320 hw) | Emulator for JVM unit tests; device only for the live-socket success criterion |
| `kotlinx-coroutines-test` | deterministic backoff/throttle unit tests | ✗ (not in catalog) | add 1.9.0 | Could use real delays (slow, flaky) — not recommended |

**Missing dependencies with no fallback:** none (live printer is available; only a test-lib catalog entry is missing).
**Missing dependencies with fallback:** `kotlinx-coroutines-test` — add to catalog; otherwise tests use real time (avoid).

## Validation Architecture

> nyquist_validation is enabled (config.json `workflow.nyquist_validation: true`). This section seeds VALIDATION.md.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 (already in catalog: `junit 4.13.2`) + `kotlinx-coroutines-test` (Wave 0 add) for JVM unit tests; AndroidJUnit4 for any instrumented live-socket check |
| Config file | none yet for `:app` unit tests — `app/src/test/` source set + `testImplementation` deps in `app/build.gradle.kts` (Wave 0) |
| Quick run command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| Full suite command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:test --no-daemon"` (add `:app:connectedDebugAndroidTest` for the device live-socket proof) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| STATE-05 | response matched to request `id` despite interleaved `notify_*` | unit | `…gw.bat :app:testDebugUnitTest --tests *JsonRpcClientTest*` | ❌ Wave 0 |
| STATE-01 | partial diffs merge into retained state (seed→merge) | unit | `…--tests *PrinterStateReducerTest*` | ❌ Wave 0 |
| STATE-02 | `objects.list` → `Capabilities` (pure) incl. minimal-printer gating | unit | `…--tests *DeriveCapabilitiesTest*` | ❌ Wave 0 |
| STATE-03 | status conflated to ~2–4 Hz; gcode lines NOT conflated | unit (virtual time) | `…--tests *ConflationTest*` | ❌ Wave 0 |
| STATE-04 | `klippy_state` + `print_stats.state` exposed; updated by notify_klippy_* | unit | `…--tests *KlippyLifecycleTest*` | ❌ Wave 0 |
| CONN-03 | uncapped backoff+jitter; reconnect loop; `requestReconnectNow()` cancels delay | unit (virtual time) | `…--tests *ReconnectSupervisorTest*` | ❌ Wave 0 |
| CONN-04 | identify→query→subscribe runs on (re)connect; query snapshot overwrites stale (D-04) | unit (FakeWebSocket) | `…--tests *HandshakeTest*` | ❌ Wave 0 |
| CONN-06 | `ConnectionState` transitions Connecting→Connected→Disconnected→Error observable | unit | `…--tests *ConnectionStateTest*` | ❌ Wave 0 |
| CONN-02 | oneshot-token appended to ws URL; `X-Api-Key` on REST; 401/`-32602`→AuthRequired (no crash) | unit (FakeWebSocket + adversarial frames) | `…--tests *AuthHandshakeTest*` | ❌ Wave 0 |
| CONN-03 (live) | real ws stays alive, receives `notify_status_update`; Wi-Fi-yank → reconnect+resync | instrumented (device) | `…gw.bat :app:connectedDebugAndroidTest` | ❌ Wave 0 |

### Testable Seams (the architecture that makes the above provable)
- **`FakeWebSocket` / injectable socket factory:** the `MoonrakerSocket`/`MoonrakerSession` takes a socket-opening lambda. Tests inject a fake that (a) replays a **golden Ender-5-Plus frame corpus** (captured `objects.list`, `objects.query` snapshot, and a stream of real `notify_status_update` diffs — per Discretion), and (b) injects **adversarial frames** the idle printer won't emit: a `notify_status_update` interleaved *between* an `objects.query` request and its reply (STATE-05), a `notify_klippy_shutdown` mid-stream (STATE-04), and an `identify` error `{-32602,"Unauthorized"}` + REST `401` (CONN-02).
- **Pure reducers** (`PrinterStateReducer`, `deriveCapabilities`) need no socket at all — plain JVM function tests over JSON fixtures.
- **Virtual time** (`runTest`/`TestScope`) makes backoff (D-01) and conflation (STATE-03) deterministic and instant.
- **Golden corpus capture:** a small throwaway script (or a one-off instrumented run) records frames from the live printer into `app/src/test/resources/golden/`. Hand-author only the adversarial frames.

### Sampling Rate
- **Per task commit:** `:app:testDebugUnitTest` (pure reducers + correlation — fast, no device).
- **Per wave merge:** full `:app:test` (all JVM unit tests incl. virtual-time backoff/conflation).
- **Phase gate:** full JVM suite green **+** the on-device `connectedDebugAndroidTest` live-socket proof (Wi-Fi-yank reconnect on the real Ender 5 Plus) before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] Add `app/src/test/` source set + `testImplementation(junit, kotlinx-coroutines-test, kotlinx-serialization-json)` to `app/build.gradle.kts`.
- [ ] Add `kotlinx-coroutines-test` (and confirm `kotlinx-serialization-json` available to tests) to `gradle/libs.versions.toml`.
- [ ] `app/src/test/resources/golden/` corpus: captured `objects.list`, `objects.query` snapshot, real `notify_status_update` diff stream (from live Ender 5 Plus).
- [ ] Hand-authored adversarial fixtures: interleaved-`notify_*` (STATE-05), `notify_klippy_shutdown`/`error` (STATE-04), identify-`-32602`/REST-`401` (CONN-02).
- [ ] `FakeWebSocket` test double + injectable socket factory on the session.
- [ ] Instrumented `:app:androidTest` live-socket reconnect test (host/port via instrumentation args, mirroring the Phase-1 smoke harness — but a NEW class, not reusing the throwaway).

## Security Domain

> security_enforcement enabled (config `workflow.security_enforcement: true`, ASVS level 1). This is a
> LAN client to a local Moonraker; threat surface is narrow but real (credential handling + cleartext).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Optional Moonraker API key / oneshot-token (CONN-02). Don't invent auth; use Moonraker's `X-Api-Key` + oneshot-token exactly. |
| V3 Session Management | partial | The ws connection IS the session (stays authed until closed). Re-fetch a fresh oneshot-token per (re)connect (5 s TTL, single-use). |
| V4 Access Control | no | No multi-user model in this app; single trusted operator on the LAN. |
| V5 Input Validation | yes | All inbound JSON is untrusted-ish (loose Moonraker payloads): `ignoreUnknownKeys=true; isLenient=true`, defensive `?.`/`orNull` walks, never `!!` on wire data, bound any buffered stream (gcode lines) to avoid unbounded growth. |
| V6 Cryptography | no (v1) | Cleartext `ws://`/`http://` on the LAN is an explicit project decision (CONN-05). No crypto hand-rolled. **Note the residual risk below.** |
| V7 Error Handling/Logging | yes | Never log the API key or oneshot-token. Surface auth failure as a state, not a stack trace. |

### Known Threat Patterns for {Android LAN client ↔ Moonraker}
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| API key committed to git via `local.properties`→`BuildConfig` | Information Disclosure | `local.properties` is gitignored (verified); placeholder defaults only; never log the key (D-05). |
| Oneshot-token leaked in logs/URLs | Information Disclosure | Token in query string is by-design (5 s/single-use); do NOT log the full ws URL with `?token=`. |
| Cleartext ws/http sniffed on shared LAN | Information Disclosure | Accepted project risk (CONN-05 LAN-only). Document; no mitigation in v1. Residual. |
| Unbounded gcode-response buffer (memory exhaustion on 2GB) | Denial of Service | Bound the gcode `SharedFlow` replay/backing buffer; Console (Phase 7) caps scrollback. |
| Malformed/hostile JSON frame crashes the client | Tampering/DoS | Defensive parsing (lenient Json, null-safe walks, try/catch around `parseToJsonElement`); a bad frame is dropped, not fatal. |
| Reconnect storm hammering the LAN | DoS (self-inflicted) | Uncapped backoff + jitter (D-01) is itself the mitigation; no give-up needed. |

**Block-on policy:** config `security_block_on: high`. No HIGH findings introduced by this phase — cleartext
is a pre-accepted project decision (CONN-05), credential handling follows Moonraker's documented mechanisms,
and inbound JSON is defensively parsed. Proceed.

## Sources

### Primary (HIGH confidence)
- `moonraker.readthedocs.io/en/latest/external_api/printer/` — objects.list/query/subscribe, printer.info, emergency_stop shapes
- `moonraker.readthedocs.io/en/latest/external_api/jsonrpc_notifications/` + `github.com/Arksine/moonraker/blob/master/docs/external_api/jsonrpc_notifications.md` — exact `notify_*` params shapes; klippy_* no-params confirmed from repo source
- `moonraker.readthedocs.io/en/latest/external_api/server/` — server.info (klippy_state etc.), server.connection.identify, temperature_store, gcode_store
- `moonraker.readthedocs.io/en/latest/external_api/authorization/` — oneshot_token, X-Api-Key, login, 401
- `moonraker.readthedocs.io/en/latest/external_api/introduction/` — JSON-RPC 2.0 framing (request/result/error), recommended connect sequence, /websocket URL
- `github.com/Arksine/moonraker/blob/master/docs/printer_objects.md` — print_stats/virtual_sdcard/display_status/webhooks/heater/extruder/toolhead field shapes
- `gradle/libs.versions.toml` (repo) — pinned, verified stack versions
- Repo CLAUDE.md "Networking deep-dive" + Phase-1 `01-RESEARCH.md` — locked stack rationale & build-quartet verification

### Secondary (MEDIUM confidence)
- Mainsail docs (connection-unauthorized) + `github.com/Arksine/moonraker` issues — `-32602 "Unauthorized"` on identify as the ws auth-required signal (A5)
- OkHttp docs (`square.github.io/okhttp`) — WebSocket API surface for the callbackFlow bridge

### Tertiary (LOW confidence)
- Community discussion threads on websocket location/auth — corroborating only; superseded by official `external_api/*` pages where they conflict

## Metadata

**Confidence breakdown:**
- API shapes (notify set, handshake, objects): **HIGH** — quoted from official docs + repo source, cross-checked.
- Auth failure codes: **MEDIUM** — REST 401 is official; ws `-32602 "Unauthorized"` is from issue tracker/Mainsail (A5). Defensive handling recommended.
- Stack: **HIGH** — locked & pre-verified in Phase 1; no new runtime deps.
- Architecture/patterns: **HIGH** — idiomatic coroutine/Flow shapes for exactly this protocol; Phase-1 smoke test already proved the wire format on hardware.
- Subscribe-unknown-object behavior: **LOW/OPEN** — A3, verify against live printer.

**Research date:** 2026-05-30
**Valid until:** ~2026-06-29 (30 days; Moonraker API is stable but docs were recently reorganized — re-check `external_api/*` URLs if they 404).
