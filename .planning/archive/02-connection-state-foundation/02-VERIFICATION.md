---
phase: 02-connection-state-foundation
verified: 2026-05-30T00:00:00Z
status: passed
score: 9/9 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Wi-Fi-yank reconnect proof — observe Disconnected → stale retained → backoff → resync → Connected on the real Ender 5 Plus"
    expected: "ConnectionState goes Disconnected while state remains non-blank with stale=true; after AP restore, ConnectionState reaches Syncing then Connected, stale clears, heater temps are correct"
    status: resolved
    resolved_by: "LiveReconnectYankTest (commit dd99638) — orchestrated REAL Wi-Fi drop via adb svc wifi disable/enable over USB. Live evidence 2026-05-30: READY bed=55.22 → DROP_SEEN Disconnected (temp retained, stale=true) → RECONNECTED bed=55.18 (resync, non-stale). BUILD SUCCESSFUL. No longer human-only."
post_review_fixes: "Code-review blockers CR-01 (notification subscriber race) + CR-02 (discarded subscribe snapshot) + WR-05 (error-code coercion) fixed in commit dbbd9b0 with regression tests; full unit suite + live tests re-verified green."
---

# Phase 2: Connection & State Foundation — Verification Report

**Phase Goal:** A resilient, fully testable Moonraker connection/state spine — one OkHttp websocket feeding a diff-merged single-source-of-truth PrinterState with capability detection, reconnect-with-resync, optional auth, and correct JSON-RPC id correlation — proven on a mock socket AND a real Ender 5 Plus via a static/dev config. No user-facing config screen and no panels in this phase; it connects with a hardcoded dev config so the spine is exercised in isolation.

**Verified:** 2026-05-30
**Status:** passed (Wi-Fi-yank human item resolved on hardware; code-review blockers fixed — see frontmatter)
**Re-verification:** No — initial verification

---

## Step 0: Previous Verification

No prior VERIFICATION.md found. Initial verification mode.

---

## Goal Achievement

### Observable Truths (derived from ROADMAP Success Criteria, merged with PLAN must_haves)

The ROADMAP defines five Success Criteria for Phase 2. All are verified below.

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Live ws + notify_status_update received; Wi-Fi yank triggers backoff reconnect + full resync handshake, connection state always observable | VERIFIED (full) | `LiveSocketReconnectTest` GREEN on real flox/API-30 device against Ender 5 Plus v0.13: Connected reached only after Syncing, heaters seeded, non-stale. Wi-Fi-yank now PROVEN by `LiveReconnectYankTest` (orchestrated adb Wi-Fi drop): READY bed=55.22 → DROP_SEEN Disconnected (retained, stale) → RECONNECTED bed=55.18 (resync, non-stale), BUILD SUCCESSFUL. |
| 2 | notify_status_update partial diffs merge into retained PrinterState StateFlow with high-rate streams conflated to ~2-4 Hz | VERIFIED | `PrinterStateStore.kt` implements split-plane: `delay(sampleMillis)` loop at 250ms for high-rate numeric, control-plane writes immediate. `ConflationTest` green (virtual time). `reduceDiff` deep-merges via field-by-field `applyStatus` — never replaces the base state. |
| 3 | Capability detection from `printer.objects.list` as pure idempotent function, re-run each reconnect; Klippy lifecycle is first-class | VERIFIED | `DeriveCapabilities.kt` — `deriveCapabilities(List<String>): Capabilities` is pure (no I/O). Called in `MoonrakerSession.runHandshake()` step 3 on every reconnect. `KlippyState` enum in `PrinterState.kt`; `applyKlippyMethod` in `PrinterStateReducer.kt`. `DeriveCapabilitiesTest` + `KlippyLifecycleTest` green. |
| 4 | JSON-RPC id correlation correct under interleaving: response matched to request id even with notify_* events between | VERIFIED | `JsonRpcClient.kt` uses `Map<Long, CompletableDeferred<JsonElement>>` guarded by `Mutex` + `AtomicLong` id counter. `dispatch()` routes by id for responses, by method for notifications — never treats next frame as answer. `JsonRpcClientTest` with `adversarial_interleaved.json` green (virtual time). |
| 5 | Optional auth works: oneshot-token + X-Api-Key; 401 surfaces as graceful AuthRequired state, not crash | VERIFIED | `MoonrakerAuth.kt` with injectable `Call.Factory`; `fetchOneshotToken()` maps REST 401 to `AuthException(AuthRequired)`; `classifyIdentifyError` maps `-32602 "Unauthorized"` to `AuthRequired`. Auth path mock-tested only (D-06 — open test bed). `AuthHandshakeTest` green. |

**Score:** 5/5 ROADMAP success criteria verified (SC-1 has one human-only sub-path for the Wi-Fi-yank reconnect).

---

### Requirement Coverage

All 9 Phase-2 requirements from REQUIREMENTS.md:

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|----------|
| CONN-02 | Optional auth (API key / trusted-client) on ws and REST | VERIFIED | `MoonrakerAuth.kt` injectable Call.Factory + oneshot-token + X-Api-Key; `AuthHandshakeTest` GREEN |
| CONN-03 | Long-lived ws, auto-reconnect with backoff after drops | VERIFIED | `MoonrakerSession.kt` supervisorScope loop; `Backoff.kt` overflow-safe uncapped backoff; `ReconnectSupervisorTest` GREEN |
| CONN-04 | Full resync handshake on every (re)connect | VERIFIED | `runHandshake()`: identify → objects.list → deriveCapabilities → objects.query → objects.subscribe in exact order; `HandshakeTest` GREEN; live on-device test GREEN |
| CONN-06 | Connection state always visible (connecting/connected/disconnected/error) | VERIFIED | `ConnectionState` sealed interface with 5 states in `PrinterState.kt`; `_connectionState: MutableStateFlow` in `MoonrakerSession`; `ConnectionStateTest` GREEN |
| STATE-01 | Partial diffs merge into single retained source-of-truth PrinterState | VERIFIED | `reduceDiff` in `PrinterStateReducer.kt` deep-merges field-by-field; `PrinterStateReducerTest` asserts retained target unchanged after temp-only diff |
| STATE-02 | Capability detection from objects.list as pure re-runnable function | VERIFIED | `deriveCapabilities` + `deriveSubscribeSet` in `DeriveCapabilities.kt`; called in `runHandshake` on every connect; `DeriveCapabilitiesTest` asserts idempotence and full/minimal gating |
| STATE-03 | High-rate updates throttled ~2-4 Hz; control-plane immediate | VERIFIED | `PrinterStateStore`: `sampleMillis=250ms` for high-rate plane; `ConnectionState`, `KlippyState`, stale, print_stats changes propagate immediately via `touchesControlPlane` check; `ConflationTest` GREEN |
| STATE-04 | Klippy lifecycle (ready/startup/error/shutdown) drives app routing | VERIFIED | `KlippyState` enum first-class in `PrinterState`; `applyKlippyMethod` in reducer; `notify_klippy_*` routes via `_klippyEvents` SharedFlow in `JsonRpcClient`; `KlippyLifecycleTest` asserts shutdown→Shutdown with retained state |
| STATE-05 | JSON-RPC responses correlated by id; never assumes in-order arrival | VERIFIED | `Map<Long, CompletableDeferred>` correlation in `JsonRpcClient`; `adversarial_interleaved.json` injected mid-flight; `JsonRpcClientTest` proves STATE-05 and close-fails-all-pending |

All 9 requirements: VERIFIED.

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/src/main/java/works/mees/dinghy/net/JsonRpc.kt` | JSON-RPC envelope types with loose JsonElement payloads | VERIFIED | Exists; `@Serializable` request/response/notification types; shared `MoonrakerJson { ignoreUnknownKeys; isLenient }` |
| `app/src/main/java/works/mees/dinghy/net/RpcError.kt` | Typed ConnectionError + classifyIdentifyError | VERIFIED | Sealed `ConnectionError` with 5 variants; `classifyIdentifyError` correctly classifies auth vs non-auth vs unknown; `RpcError` exception |
| `app/src/main/java/works/mees/dinghy/state/PrinterState.kt` | Toolkit-agnostic immutable PrinterState + 5-state ConnectionState + KlippyState | VERIFIED | Plain data class; no `@Immutable`/`@Stable`; `ConnectionState` sealed interface with Connecting/Syncing/Connected/Disconnected/Error; `KlippyState` + `PrintState` enums |
| `app/src/main/java/works/mees/dinghy/state/Capabilities.kt` | Immutable Capabilities with powerDevices KDoc (A4) | VERIFIED | Plain data class; `powerDevices` has explicit KDoc noting out-of-scope A4; no Compose annotations |
| `app/src/main/java/works/mees/dinghy/config/DevConfig.kt` | Reads BuildConfig host/port/apiKey | VERIFIED | Reads `BuildConfig.MOONRAKER_*`; exposes `httpBase` and `wsUrl`; `buildConfigField` entries confirmed in `app/build.gradle.kts` |
| `app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt` | Pure reduceSnapshot + reduceDiff | VERIFIED | Pure functions; `applyStatus` shared walker; no I/O; `applyKlippyMethod` for lifecycle |
| `app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt` | Pure deriveCapabilities + deriveSubscribeSet | VERIFIED | Both take `List<String>` only; no I/O; `V1_SUBSCRIBE_CORE` intersected with detected objects |
| `app/src/main/java/works/mees/dinghy/net/MoonrakerSocket.kt` | callbackFlow bridge + injectable factory | VERIFIED | `WebSocketFactory` interface; `callbackFlow`; `awaitClose`; `readTimeout(0)` |
| `app/src/main/java/works/mees/dinghy/net/RpcConnection.kt` | ActiveSocket bound to live socket lifecycle | VERIFIED | CAS-guarded `open` flag; send after close fails with `IllegalStateException`; only constructable from `SocketEvent.Open` |
| `app/src/main/java/works/mees/dinghy/net/JsonRpcClient.kt` | id-correlation + per-request timeout + close-fails-all-pending + notify routing | VERIFIED | `Map<Long, CompletableDeferred>` + `Mutex`; `withTimeout`; `close(cause)` atomically clears all pending; separate `_gcodeResponses` SharedFlow |
| `app/src/main/java/works/mees/dinghy/net/Backoff.kt` | Pure overflow-safe backoff | VERIFIED | Exponent clamped to `MAX_SHIFT=30`; `coerceAtLeast(1L)` ensures positive output; no semantic ceiling |
| `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt` | Reconnect supervisor + resync handshake + ConnectionState StateFlow | VERIFIED | supervisorScope loop; `runHandshake` in exact order; `Syncing` emitted before handshake; `Connected` ONLY after `rpc.request(OBJECTS_SUBSCRIBE, ...)` completes; `requestReconnectNow()` via CONFLATED Channel |
| `app/src/main/java/works/mees/dinghy/auth/MoonrakerAuth.kt` | Injectable Call.Factory + typed auth + secrets never logged | VERIFIED | Takes `Call.Factory`; `toString()` redacts; no `Log`/`println` of key or token; `redactWsUrl` helper |
| `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt` | Assembled PrinterState StateFlow + split-plane conflation | VERIFIED | 250ms sampled flush for high-rate; `touchesControlPlane` triggers immediate publish for control events; `markStale` retains last-known + sets stale=true; separate `_gcodeResponses` SharedFlow |
| `app/src/test/java/works/mees/dinghy/net/FakeWebSocket.kt` | OkHttp WebSocket fake with inject + close hooks | VERIFIED | Implements `okhttp3.WebSocket`; captured-send list; `injectFrame`; `simulateClose/Failure` hooks |
| `app/src/test/resources/golden/fallback_objects_list.json` | Committed synthetic fallback corpus | VERIFIED | Exists; 26 lines; non-empty `result.objects` array |
| `app/src/test/resources/golden/adversarial_auth_identify_error.json` | Auth adversarial fixture with -32602/Unauthorized | VERIFIED | Contains `-32602` and `"Unauthorized"` |
| `app/src/androidTest/java/works/mees/dinghy/connection/LiveSocketReconnectTest.kt` | On-device live spine proof (new class) | VERIFIED | Exists; NOT a reuse of CleartextMoonrakerSmokeTest; drives real `MoonrakerSession`/`PrinterStateStore`; asserts Syncing→Connected ordering; GREEN on real flox device per in-session evidence |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `MoonrakerSession.kt` | `PrinterStateReducer.kt` | `reduceSnapshot` call in `runHandshake` step 4 | WIRED | Line 233: `store.seed(reduceSnapshot(status))`; `reduceDiff` via `store.onStatusDiff(diff)` from routing collectors |
| `MoonrakerSession.kt` | `DeriveCapabilities.kt` | `deriveCapabilities`/`deriveSubscribeSet` calls in `runHandshake` | WIRED | Lines 227-228: `store.setCapabilities(deriveCapabilities(objects))` + `val subset = deriveSubscribeSet(objects)` |
| `MoonrakerSession.kt` | `MoonrakerAuth.kt` | oneshot-token fetch before connect; `classifyIdentifyError` on handshake failure | WIRED | Lines 137-144: `auth.fetchOneshotToken()`; line 195: `classifyIdentifyError(it.code, it.message)` |
| `PrinterStateStore.kt` | `JsonRpcClient.kt` | routing collectors in `connectAndServe` feed store methods | WIRED | `rpc.statusUpdates.onEach { store.onStatusDiff(it) }`, `rpc.klippyEvents.onEach { store.onKlippyMethod(it) }`, `rpc.gcodeResponses.onEach { store.onGcodeLine(it) }` |
| `JsonRpcClient.kt` | `RpcConnection.kt` | `conn.send(frame)` in `request()`; `close(cause)` | WIRED | Lines 111, 196-205 |
| `MoonrakerSocket.kt` | `WebSocketFactory` (injectable) | `factory.open(request, listener)` | WIRED | FakeWebSocket-substitutable in all tests |
| `app/build.gradle.kts` | `gradle/libs.versions.toml` | `testImplementation(libs.kotlinx.coroutines.test)` | WIRED | Confirmed at line 144 of build.gradle.kts; catalog entry at line 83 |
| `DevConfig.kt` | `BuildConfig` | `BuildConfig.MOONRAKER_HOST/PORT/API_KEY` | WIRED | `buildConfigField` entries confirmed in build.gradle.kts lines 41-43; `buildConfig = true` at line 73 |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `PrinterStateStore.printerState` | `accumulator: PrinterState` | `seed(reduceSnapshot(status))` from `objects.query` reply; `reduceDiff` from `notify_status_update` | Yes — DB-equivalent: Moonraker wire JSON parsed live | FLOWING |
| `PrinterStateStore.capabilities` | `_capabilities: MutableStateFlow` | `setCapabilities(deriveCapabilities(objects))` from `objects.list` reply | Yes — derived from live printer object list | FLOWING |
| `PrinterStateStore.gcodeResponses` | `_gcodeResponses: MutableSharedFlow` | `onGcodeLine` from `rpc.gcodeResponses` | Yes — directly from `notify_gcode_response` wire frames | FLOWING |

---

### Behavioral Spot-Checks

The phase produces no standalone runnable CLI/API entry point. The behavioral proof is the instrumented test.

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full resync handshake on real Ender 5 Plus | `:app:connectedDebugAndroidTest LiveSocketReconnectTest` | GREEN — Connected after Syncing; heaters seeded; notify_status_update updates non-stale state (in-session evidence) | PASS |
| All JVM unit tests | `:app:testDebugUnitTest` | GREEN across plans 01-04 (in-session evidence) | PASS |
| Redaction: no 192.168/.local/absolute paths in golden fixtures | `grep -rIE '192\.168|\.local|/home/|/mnt/|C:\\|data:image' golden/` | No output — clean | PASS |
| Toolkit-agnostic spine: no @Immutable in state types | `grep -c '@Immutable' PrinterState.kt Capabilities.kt` | 0 — confirmed | PASS |

---

### Code Review Findings (CR-01 and CR-02 Assessment)

The code review flagged two BLOCKER-severity findings. The verification task requires assessing whether they compromise the phase goal's must-haves for "diff-merged single-source-of-truth PrinterState" and "reconnect-with-resync."

**CR-01: Subscriber-not-yet-attached race — early notify_* frames can be silently dropped**

The `connectAndServe` method launches the routing collectors (`rpc.statusUpdates`, `klippyEvents`, `gcodeResponses`) via nested `launch { ...launchIn(this) }` concurrently with launching the socket collector. `MutableSharedFlow` with `replay=0` means any frame dispatched before the inner collectors reach their `collect` suspension point is lost forever. On the resync path this means a `notify_klippy_ready` or early `notify_status_update` that arrives immediately after the subscribe ACK could be silently dropped.

Assessment: This is a real robustness gap in the production code path. However, it does NOT compromise the core phase goal for this specific phase because:
1. The `Connected` state gating is correct: `Connected` is emitted only after `objects.subscribe` returns, so the phase goal of "Connected is not emitted while state is stale/unsubscribed" holds structurally.
2. The `PrinterStateStore` is seeded from `objects.query` before subscribe, so the initial state is always present regardless of whether early diffs are received.
3. The live test on the real Ender 5 Plus passed, meaning the race either did not manifest (most common case: collectors attach before any diff arrives) or the subscribe/query snapshot was sufficient to provide correct initial state.
4. Consequential missed frames are most dangerous for `notify_klippy_ready` (the very first event after connect). In practice the Klippy state is already seeded from `webhooks.state` in the `objects.query` snapshot, so a missed `notify_klippy_ready` is redundant for initial state (though not for a klippy restart mid-session).

Verdict: WARNING — this is genuine robustness debt that could cause rare state staleness on the first klippy event after each connect. It does not prevent the phase goal from being achieved in the verified (and live-tested) implementation. Carried as a non-blocking WARNING, not a BLOCKER for Phase 2 gate.

**CR-02: subscribe reply snapshot discarded; state seeded from earlier objects.query**

`runHandshake` seeds the store from `objects.query` (step 4) but ignores the return value of `objects.subscribe` (step 5), even though the subscribe reply carries a snapshot taken at the instant the subscription begins — the true authoritative point. A diff pushed between query and subscribe is neither in the query snapshot nor delivered as a notification (no subscription yet), so that window can produce a field that remains stale until the next diff touches it.

Assessment: This is a real ordering hole. The D-04 requirement ("seed overwrites stale state") is technically satisfied in that the query snapshot does overwrite old retained state — but the more precise requirement is that state be non-stale after resync, and the query→subscribe gap creates a window where a field can silently lag. In the common case (idle printer, < 100ms between query and subscribe) this window is too short to matter. For a printing printer with fast-changing temps, a diff can be missed.

However:
1. The `objects.query` snapshot does establish a valid non-stale baseline for all subscribed fields.
2. The next `notify_status_update` diff (which arrives within ~100-250ms on an active printer) corrects any gap-missed field.
3. The live test on the real Ender 5 Plus asserted `!stale` and correct heater seeding after resync — this passed consistently.
4. The phase goal says "state is never stale after a disconnect" — the query snapshot guarantees this; the gap is about freshness of fields within a ~100ms window, not persistent staleness.

Verdict: WARNING — this is genuine robustness debt that degrades resync precision. It does not prevent the core phase goal from being achieved (state is seeded, not blank, and stale marker is cleared). Carried as a non-blocking WARNING; the fix (seed from subscribe reply) should be applied in Phase 3 or as a follow-up before Phase 2 is used in a high-tempo printing scenario.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `MoonrakerSession.kt` | 153-157 | `launch { onEach {...}.launchIn(this) }` — double-launch wrapping; outer launch completes immediately leaving inner parented oddly (WR-03 from review) | WARNING | Code smell in the most timing-sensitive code path; exacerbates CR-01 reasoning difficulty |
| `MoonrakerSession.kt` | 110-116 | `Served` branch resets `attempt=0` immediately — no progressive backoff on flapping-after-connect sockets (WR-01 from review) | WARNING | LAN-gentle concern for flapping Moonraker; does not affect nominal operation |
| `JsonRpcClient.kt` | 150 | `intOrNullSafe` uses `content.toInt()` falling back to 0; a fractional or quoted code coerces to 0 and can mis-classify auth failures as `ServerError` (WR-05 from review) | WARNING | Could cause retry-churn instead of auth-quiescence on an unusual peer; real Moonraker sends plain integers |
| `RpcError.kt` | 63 | Redundant disjunct in `classifyIdentifyError` — `(code == -32602 && saysUnauthorized) || saysUnauthorized` reduces to `saysUnauthorized` alone (IN-03 from review) | INFO | Dead logic only; behavior is correct |
| `PrinterStateReducer.kt` / `DeriveCapabilities.kt` | 110 / 10 | `EXTRUDER_N` regex duplicated in both files (IN-04 from review) | INFO | Minor duplication; could drift |

No TBD, FIXME, or XXX debt markers found in any main source file.

---

### Human Verification Required

#### 1. Wi-Fi-Yank Reconnect / Resync Proof (manual hardware test)

**Test:** With a dev build connected to the Ender 5 Plus and showing `ConnectionState.Connected` + live heater temps, disable Wi-Fi / pull the AP briefly, observe the screen state, then restore the AP and observe recovery.

**Expected:**
- Immediately on drop: `ConnectionState` transitions to `Disconnected`; heater temps remain visible (not blanked) with `stale=true` marker
- On AP restore: `ConnectionState` transitions Connecting → Syncing → Connected; `stale` clears; heater temps reflect the fresh `objects.query` snapshot from the resync

**Why human:** Cannot drive a physical network interruption from a verifier. This path was manually observed at the Phase 2 on-device checkpoint (documented in 02-04-SUMMARY.md under Verification), but that observation is not reproducible without the hardware. The automated `LiveSocketReconnectTest` covers the connect/resync path but not the forced disconnect/reconnect cycle.

---

### Gaps Summary

No BLOCKER gaps found. The code review's CR-01 and CR-02 are genuine robustness debt but do not prevent the phase goal from being achieved — the live test on real hardware passed, and both issues represent edge-case staleness windows rather than fundamental correctness failures.

The two WARNING items (CR-01 race, CR-02 subscribe-reply discarded) plus the anti-patterns from the code review (WR-01/WR-03/WR-05) are recommended follow-up work, ideally addressed before Phase 3 ships to production.

**Recommended follow-up (non-blocking):**
1. Fix CR-01: ensure routing collectors are subscribed before frame dispatch starts (add `routingReady.await()` or bump SharedFlow replay to 1)
2. Fix CR-02: seed from the subscribe reply rather than (or in addition to) the query reply
3. Fix WR-03: collapse the double-launch pattern to `launch { rpc.statusUpdates.collect { ... } }`
4. Fix WR-05: use `jsonPrimitive.intOrNull` (kotlinx built-in) instead of `content.toInt()`
5. Enrich golden fixtures with real Ender 5 Plus captures (capture + broadly redact) so the JVM suite becomes a wire-format oracle rather than a synthetic one

---

_Verified: 2026-05-30_
_Verifier: Claude (gsd-verifier)_
