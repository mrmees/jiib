# Phase 2: Connection & State Foundation - Pattern Map

**Mapped:** 2026-05-30
**Files analyzed:** 13 new + 2 modified (build/catalog)
**Analogs found:** 2 reference-grade (smoke test, version catalog) + 1 convention source (bench) / 15 — this is a **greenfield connection layer**; most files have NO behavioral analog by design.

> **Honest framing (per CONTEXT.md D-09 / "none reusable"):** there is no existing connection/state
> code to mirror. The Phase-1 throwaway smoke test
> (`CleartextMoonrakerSmokeTest.kt`) is the **only** existing artifact that touches the Moonraker wire,
> and it explicitly **MUST NOT be reused as the layer** — it is a *call-shape reference only*. Where a
> file below says "none — greenfield," that is the accurate answer, not a gap to paper over. The planner
> should take call shapes + JSON posture from the smoke test and `Json`/Flow/data-class **conventions**
> from the existing code, then write clean new classes.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `net/MoonrakerSocket.kt` | transport / socket bridge | streaming (push→Flow) | `CleartextMoonrakerSmokeTest.kt` (ws open/listener only) | reference (call shape) |
| `net/JsonRpc.kt` | model (`@Serializable` envelopes) | transform | smoke test inline RPC build/parse | reference (shape) |
| `net/JsonRpcClient.kt` | protocol / service | request-response + pub-sub | smoke test's id-correlation + notify split | reference (shape) |
| `net/MoonrakerSession.kt` | service / supervisor | event-driven (reconnect) | none — greenfield | no analog |
| `auth/MoonrakerAuth.kt` | service / utility | request-response (REST) | smoke test `assertRestInfo` (REST GET shape) | reference (REST shape) |
| `state/PrinterState.kt` | model | n/a (immutable data) | `bench/SyntheticFeed.kt` `data class` convention | convention-only |
| `state/PrinterStateReducer.kt` | reducer (pure) | transform (diff-merge) | `SyntheticFeed.replay()` (pure-fn convention) | convention-only |
| `state/Capabilities.kt` | model | n/a | `bench` data classes | convention-only |
| `state/deriveCapabilities.kt` | reducer (pure) | transform | RESEARCH.md `deriveCapabilities` example | spec-only (no code analog) |
| `state/PrinterStateStore.kt` (or in Session) | store / state holder | pub-sub (StateFlow/SharedFlow) | none — greenfield | no analog |
| `config/DevConfig.kt` | config | n/a (reads BuildConfig) | smoke test instrumentation-arg pattern | reference (config-source shape) |
| `app/build.gradle.kts` (modify) | build config | n/a | existing file (add `buildConfigField` + test source set) | exact (extend in place) |
| `gradle/libs.versions.toml` (modify) | config | n/a | existing catalog entries | exact (extend in place) |
| `app/src/test/.../*Test.kt` (unit) | test | n/a | **none** — no `src/test/` exists yet | no analog (Wave 0) |
| `app/src/test/.../FakeWebSocket.kt` + `golden/` | test harness / fixtures | streaming (replay) | smoke test's `WebSocketListener` usage | reference (interface shape) |
| `app/src/androidTest/.../LiveSocketTest.kt` (NEW) | test (instrumented) | streaming | `CleartextMoonrakerSmokeTest.kt` (harness/arg pattern) | role+harness match (NEW class) |

---

## Pattern Assignments

### `net/MoonrakerSocket.kt` (transport, push→Flow streaming)

**Analog:** `CleartextMoonrakerSmokeTest.kt` — **call-shape reference only.** The smoke test uses
`OkHttpClient.Builder` + `newWebSocket(request, listener)` + a `WebSocketListener`. The real file wraps
that same OkHttp surface in a `callbackFlow` (RESEARCH.md Pattern 1) instead of `CountDownLatch`.

**OkHttp client construction — carry forward the websocket timeout posture** (smoke test lines 88–92):
```kotlin
val client = OkHttpClient.Builder()
    .connectTimeout(OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .readTimeout(0, TimeUnit.MILLISECONDS) // websocket: no read timeout
    .build()
```
`readTimeout(0)` is load-bearing — a websocket must not be killed by a read timeout. Reuse one client
for ws + REST (CLAUDE.md "Networking deep-dive").

**Listener → frame plumbing** (smoke test lines 102–135) is the exact surface to bridge; in the real
layer each callback becomes a `trySend`/`close` on a `callbackFlow` (RESEARCH.md Pattern 1, lines 220–232):
```kotlin
override fun onOpen(webSocket: WebSocket, response: Response) { /* trySend(Open) */ }
override fun onMessage(webSocket: WebSocket, text: String) { /* trySend(Frame(text)) */ }
override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { /* close(t) */ }
```
URL shape (smoke test line 84): `ws://$host:$port/websocket` (plus `?token=` when auth — § auth).

**Testability seam (REQUIRED):** inject the socket-opening lambda / factory so `FakeWebSocket` can be
substituted (RESEARCH.md "Testable Seams"). The smoke test hard-codes `client.newWebSocket(...)`; the
real file must NOT — that is the one thing it deliberately does differently.

---

### `net/JsonRpc.kt` (model — `@Serializable` envelopes, transform)

**Analog:** none for the *types*; smoke test for the **wire shapes** they model.

The request build shape to formalize (smoke test lines 146–156):
```kotlin
val subscribeRpc = buildJsonObject {
    put("jsonrpc", "2.0")
    put("method", "printer.objects.subscribe")
    putJsonObject("params") { putJsonObject("objects") { put(SUBSCRIBED_OBJECT, JsonNull) } }
    put("id", ID_SUBSCRIBE)
}
```
Envelope set to model (RESEARCH.md "JSON-RPC framing", lines 336–345): Request `{jsonrpc,method,params,id}`,
success `{result,id}`, error `{error:{code,message},id}`, notification `{method,params}` (NO id).
Keep `params`/`result` as `JsonElement`/`JsonObject` (loose Moonraker payloads), not strict DTOs.

The serialization plugin is already applied — `alias(libs.plugins.kotlin.serialization)` in
`app/build.gradle.kts` line 5. No build change needed for `@Serializable`.

---

### `net/JsonRpcClient.kt` (protocol — request-response + pub-sub)

**Analog:** the smoke test's id-correlation + notify split is the **reference**, hand-rolled inline there;
the real file generalizes it (RESEARCH.md Pattern 2, lines 238–260).

The smoke test's `onMessage` already encodes the **exact STATE-05 discipline** to carry forward
(lines 107–124): match a response by `id`, route notifications by `method`:
```kotlin
val id = root["id"]?.jsonPrimitive?.intOrNull
if (id == ID_SUBSCRIBE && root.containsKey("result")) { /* ack by id */ }
val method = root["method"]?.jsonPrimitive?.content
if (method == "notify_status_update") { /* route notification */ }
```
The real layer replaces the single-id `CountDownLatch` with `Map<id, CompletableDeferred<JsonElement>>`
(guarded by a `Mutex`) + an `AtomicLong` id counter, and routes notifications to `SharedFlow`s by method
(RESEARCH.md lines 240–259). **Anti-pattern to avoid (RESEARCH.md line 306):** never treat "next frame"
as the answer — correlate by id only.

`notify_status_update` `params` is a **2-element array** `[ {changed objects}, eventtime ]`; the smoke
test's guard (lines 210–214) shows how to walk it:
```kotlin
val arr = params as? JsonArray ?: return false
val first = arr.firstOrNull() as? JsonObject ?: return false   // element [0] = the status diff
```

---

### `net/MoonrakerSession.kt` (service / reconnect supervisor, event-driven)

**Analog:** **none — greenfield.** No reconnect/supervisor code exists. Follow RESEARCH.md Pattern 3
(lines 266–287) verbatim as the spec: `supervisorScope` loop, uncapped `jitter(base * 2^attempt)` (D-01,
**no ceiling**), and a `Channel<Unit>(CONFLATED)` `reconnectNow` consumed in a `select { onTimeout(backoff){}; reconnectNow.onReceive{} }`
for `requestReconnectNow()` (D-02). Owns the handshake order **identify → objects.query → objects.subscribe**
(CONN-04, exactly once each per connect — RESEARCH.md Pitfall 5) and emits `ConnectionState` (CONN-06).
Drive it against `FakeWebSocket` under `kotlinx-coroutines-test` virtual time.

---

### `auth/MoonrakerAuth.kt` (service / utility, REST request-response)

**Analog:** smoke test `assertRestInfo` (lines 181–203) is the **REST-call-shape reference** — same
OkHttp `newCall(...).execute().use { }` and `{ "result": {...} }` envelope unwrap:
```kotlin
client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
    // resp.isSuccessful, resp.body?.string(), json.parseToJsonElement(body).jsonObject["result"]
}
```
Behavior is otherwise greenfield (RESEARCH.md "Auth handshake", lines 425–446): fetch `access.oneshot_token`
(base32, 5 s TTL, single-use) immediately before each connect when a key is set, append `?token=` to the
ws URL, send `X-Api-Key` header on REST, and map **HTTP 401** and identify **`{code:-32602,"Unauthorized"}`**
to `ConnectionState.Error(AuthRequired)` (never crash). **Mock-tested only** (D-06). **Security (RESEARCH.md
V7):** never log the key or the full `?token=` URL.

---

### `state/PrinterState.kt`, `state/Capabilities.kt` (models)

**Analog:** `bench/SyntheticFeed.kt` (lines 161–189) for the **immutable-`data class` convention** only —
NOT its content. The bench `FeedEvent`/`GcodeFile`/`GraphSample` are the established style: flat immutable
`data class`, KDoc noting "immutable ⇒ Compose-stable." Carry that (`@Immutable`/`@Stable` per the
toolkit-agnostic requirement). `PrinterState` must include the **stale/connection marker** (D-03), a
first-class `klippyState: KlippyState` and `print_stats.state` (STATE-04). Field set per RESEARCH.md
"Code Examples" subscribe list (lines 514–528) + the `Capabilities` shape (lines 537–544).

---

### `state/PrinterStateReducer.kt`, `state/deriveCapabilities.kt` (pure reducers, transform)

**Analog (convention):** `SyntheticFeed.replay()` (lines 39–114) is the repo's **pure-function precedent** —
KDoc explicitly states "Pure function of (seed, eventCount): no clock, no shared mutable state, no I/O."
Mirror that posture: `reduceSnapshot(status)` / `reduceDiff(current, diff)` and `deriveCapabilities(objects)`
are pure, IO-free, coroutine-free, unit-tested over golden JSON (RESEARCH.md Pattern 4 + lines 546–563).

**Analog (code):** `deriveCapabilities` has a concrete reference implementation in RESEARCH.md (lines 546–563) —
copy that as the starting point; there is no existing repo code for it. **Critical merge semantics
(RESEARCH.md Pitfall 1):** deep-**merge** diffs per object, never replace — seed from the subscribe snapshot
first or fields vanish.

---

### `config/DevConfig.kt` (config) + `app/build.gradle.kts` modification

**Analog:** smoke test's instrumentation-arg pattern (lines 78–84) is the **shape reference** for
"read host/port/optional-key from an external source, with placeholder defaults":
```kotlin
private fun arg(key: String, default: String): String =
    InstrumentationRegistry.getArguments().getString(key) ?: default
private val httpBase get() = "http://$host:$port"
private val wsUrl   get() = "ws://$host:$port/websocket"
```
For Phase 2 the source changes from instrumentation args → `BuildConfig` (D-05). Add `buildConfigField`
entries in `app/build.gradle.kts` reading gitignored `local.properties` (RESEARCH.md "Runtime State
Inventory", line 467), and enable `buildFeatures { buildConfig = true }` (only `compose = true` is set
today, line 52). **Provide safe placeholder defaults** so a fresh clone compiles (mirror smoke test's
`DEFAULT_HOST`/`DEFAULT_PORT` placeholders, lines 57–61). Never commit a real IP/key.

---

### Unit tests + `FakeWebSocket` (`app/src/test/`) — NO ANALOG, Wave 0

**There is no `app/src/test/` source set yet** — confirmed: only `app/src/main` and `app/src/androidTest`
exist. This is a Wave-0 gap (RESEARCH.md lines 648–653). The planner must:
1. Create the `app/src/test/` JVM source set + add `testImplementation(libs.junit, kotlinx-coroutines-test, kotlinx-serialization-json)`.
2. Add `kotlinx-coroutines-test` to the catalog (see § Shared Patterns / catalog).
3. Add `app/src/test/resources/golden/` (captured Ender-5-Plus `objects.list`, `objects.query` snapshot,
   real `notify_status_update` diffs) + hand-authored adversarial frames.

`FakeWebSocket` analog: the smoke test's `WebSocketListener` (lines 102–133) shows the OkHttp interface
to fake; implement OkHttp's `WebSocket` interface to replay golden frames into the same `onMessage` path.

### Instrumented live-socket test (`app/src/androidTest/`) — NEW class, harness analog

**Analog:** `CleartextMoonrakerSmokeTest.kt` is the **harness pattern** to mirror (AndroidJUnit4,
`@RunWith`, instrumentation-arg host/port, lines 53–84) — but a **NEW class exercising the real spine**,
NOT a reuse/edit of the throwaway (CONTEXT.md domain block + RESEARCH.md line 653). The Wi-Fi-yank
reconnect proof goes here.

---

## Shared Patterns

### kotlinx.serialization `Json` posture (apply to ALL parsing — net/, state/, fixtures)
**Source:** `CleartextMoonrakerSmokeTest.kt` line 76
```kotlin
private val json = Json { ignoreUnknownKeys = true; isLenient = true }
```
**Apply to:** every JSON touch point. Reuse **one** `Json` instance (RESEARCH.md anti-pattern line 312 —
do NOT build `Json{}` per message). This posture is mandatory for Moonraker's loose/heterogeneous payloads
(CONTEXT.md "Established Patterns"). Walk with null-safe `?.`/`orNull`, never `!!` on wire data (RESEARCH.md V5).

### Defensive frame parsing (apply to socket inbound path)
**Source:** `CleartextMoonrakerSmokeTest.kt` line 108
```kotlin
val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
```
A malformed frame is dropped, not fatal (RESEARCH.md V5 / "hostile JSON" threat).

### Version-catalog additions (apply to: catalog + app/build.gradle.kts)
**Source:** `gradle/libs.versions.toml` — the established add-a-dep pattern (versions pinned in
`[versions]`, coordinates in `[libraries]`, **never inline**; CONTEXT.md "Established Patterns").
Coroutines version already present: `coroutines = "1.9.0"` (line 41). Add the test sibling tracking it:
```toml
# [versions]  — already has: coroutines = "1.9.0"
# [libraries] — add:
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
```
This is the **only new dependency** in the phase (test-only; RESEARCH.md lines 123–125, 605). All runtime
deps (okhttp, retrofit, kotlinx-serialization-json, kotlinx-coroutines-android) are **already declared** in
`app/build.gradle.kts` lines 96–101 — do NOT re-add. **Do not add any dep that raises merged-manifest
minSdk** above 23 (the `verify-min-sdk` gate, build.gradle.kts line 9, enforces this).

### Code-style conventions (apply to all new Kotlin)
**Source:** `bench/SyntheticFeed.kt` — the established house style:
- Heavy KDoc with requirement/decision IDs cited inline (e.g. "(D-06)", "STATE-01").
- Immutable `data class` models; pure functions explicitly documented as IO-free.
- `companion object` for constants/placeholders (smoke test lines 56–74; SyntheticFeed lines 129–137).
- `kotlinx.coroutines.flow.Flow` for streams (SyntheticFeed line 121).
- Package root `works.mees.dinghy`; new sub-packages `net/`, `auth/`, `state/`, `config/` (RESEARCH.md
  "Recommended Project Structure", lines 196–215). No package-layout precedent beyond `bench/` and
  `smoke/` — these new packages are greenfield but follow the same flat single-namespace convention.

---

## No Analog Found

Files with no behavioral match in the codebase (planner uses RESEARCH.md patterns/spec, not a repo file):

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `net/MoonrakerSession.kt` | supervisor | event-driven | No reconnect/backoff/supervisor code exists. Spec: RESEARCH.md Pattern 3. |
| `state/PrinterStateReducer.kt` (logic) | reducer | transform | No diff-merge precedent. Spec: RESEARCH.md Pattern 4 + Pitfall 1. (Convention from `SyntheticFeed.replay`.) |
| `state/PrinterStateStore.kt` / StateFlow wiring | store | pub-sub | No StateFlow/SharedFlow + conflation precedent. Spec: RESEARCH.md Pattern 4 (`sample(250.ms)` status, un-throttled gcode SharedFlow). |
| `auth/MoonrakerAuth.kt` (logic) | service | request-response | No auth code; test bed is open (D-06). Spec: RESEARCH.md "Auth handshake". REST *shape* from smoke `assertRestInfo`. |
| `app/src/test/**` unit tests | test | n/a | `app/src/test/` source set does not exist yet (Wave 0). |

---

## Metadata

**Analog search scope:** `app/src/{main,androidTest}/java/works/mees/dinghy/**`, `gradle/libs.versions.toml`,
`app/build.gradle.kts`, `build-logic/`, `settings.gradle.kts`.
**Files scanned:** 10 Kotlin/XML source files + 6 build scripts.
**Key finding:** Genuinely greenfield connection layer. Two reference-grade artifacts
(`CleartextMoonrakerSmokeTest.kt` for Moonraker call shapes + `Json` posture; `gradle/libs.versions.toml`
for the dep-add pattern) and one convention source (`bench/SyntheticFeed.kt` for pure-fn/immutable-`data class`
style). No `src/test/` source set exists — its creation is part of this phase.
**Pattern extraction date:** 2026-05-30
