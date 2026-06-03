# Phase 13: Optimization, Network Efficiency & End-to-End Reliability - Pattern Map

**Mapped:** 2026-06-03
**Files analyzed:** 9 (5 production-modify, 3 test-harden/extend, 2+ test-new, 1 doc-new)
**Analogs found:** 9 / 9 — this is a refactor phase; every changed file IS its own analog (modify-in-place)

> **Read first (orchestrator-mandated context, the law for this phase):**
> `MoonrakerSession.kt` (the spine being fixed), `KlippyReadyResyncTest.kt` + `SessionTestHarness.kt`
> (the mock-that-lied to harden), `docs/moonraker-capabilities.md` (the doc-format analog), ADR-0001
> (holders stay toolkit-agnostic). The "no per-screen polling" invariant is LAW — no fix may add a timer query.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt` | production-modify | event-driven (push) + request-response (handshake) | *itself* (in-place) | exact (self) |
| `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt` | production-modify | event-driven (control + high-rate plane) | *itself* (reuse `emit`/`markStale`/`seed`) | exact (self) |
| `app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt` | production-read (audit, likely no change) | transform (pure) | *itself* | exact (self) |
| `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` | production-read (cadence inventory source) | request-response (spec table) | *itself* | exact (self) |
| `app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt` | production-modify (cadence: `refreshProbeZOffset` wiring) | event-driven (FGS owns scope) | *itself* | exact (self) |
| `app/src/test/.../net/KlippyReadyResyncTest.kt` | test-harden (D-10 keystone) | event-driven assertion | *itself* + `KlippyLifecycleTest` (store-assert style) | exact (self) |
| `app/src/test/.../net/SessionTestHarness.kt` | test-harden (klippy-down window) | request-response fake | *itself* | exact (self) |
| `app/src/test/.../net/ReconnectSupervisorTest.kt` | test-extend (mid-print resync, D-07b) | event-driven supervisor | *itself* | exact (self) |
| `app/src/test/.../net/KlippyRecoveryStateTest.kt` (NEW) | test-new (D-03 Syncing emit) | event-driven assertion | `KlippyReadyResyncTest` + `ReconnectSupervisorTest` | exact (structure clone) |
| Self-heal test (NEW, in `KlippyReadyResyncTest` or new file) | test-new | event-driven | `ReconnectSupervisorTest.networkFailure_retriesForever` | exact |
| `docs/request-cadence-contract.md` (NEW) | doc-new | n/a | `docs/moonraker-capabilities.md` | exact (format twin) |
| `docs/commands/*-saveconfig-capture.jsonl` (NEW fixtures) | fixture-new | captured wire | `docs/commands/printer-matrix.json` (committed wire-truth) | role-match |

---

## Shared Patterns (apply across all reliability fixes)

### Reuse the proven paths — do NOT invent recovery machinery
RESEARCH § "Don't Hand-Roll" is binding: every fix is a *reuse*, not a new mechanism.
- **Re-subscribe** = re-run the existing `runHandshake()` (MoonrakerSession.kt:322–425). It already does
  identify → server.info → objects.list → derive → query(seed) → subscribe(seed) → one-shots.
- **Recovery-on-failure** = force a socket close → let the existing `run()` supervisor reconnect
  (the path proven by force-restart). Do NOT write a new backoff loop.
- **Connection-state surface** = the existing `emit()` + `ConnectionState.Syncing` + `markStale`/`seed`.

### The `emit()` helper is the single ConnectionState seam (MoonrakerSession.kt:501–504)
```kotlin
private fun emit(state: ConnectionState) {
    _connectionState.value = state      // observed by MoonrakerService notification + UI shell
    store.setConnectionState(state)     // control-plane, IMMEDIATE (PrinterStateStore:177)
}
```
**Apply to:** the D-03 fix — the silent re-handshake must call `emit(ConnectionState.Syncing)` at start
and `emit(ConnectionState.Connected)` on success, exactly as `connectAndServe` already does at lines
285 (`emit(Syncing)`) and 311 (`emit(Connected)`). The notification text already maps `Syncing` →
"Syncing…" (MoonrakerService.kt:255–261) and the store already publishes it immediately — no new
surface needed (ADR-0001: holders stay toolkit-agnostic, StateFlow drives both toolkits).

### ConnectionState lifecycle (PrinterState.kt:227–232) + ConnectionError (RpcError.kt:13–42)
```kotlin
sealed interface ConnectionState { Connecting; Syncing; Connected; Disconnected; Error(reason: ConnectionError) }
sealed interface ConnectionError { AuthRequired; NetworkUnavailable; Timeout; ProtocolError; ServerError; ParseError }
```
A slow re-subscribe in the klippy-down window surfaces as `ConnectionError.Timeout` (RpcError.kt:24–32,
the G4 distinction) — the self-heal logic must treat `Timeout`/`RpcError` as "escalate to reconnect," NOT
"give up" (RESEARCH Pitfall 4 / cross-check § Phase-6 ref).

### Store plumbing the recovery reuses (PrinterStateStore.kt)
- `seed(snapshot)` (147–152) — overwrites data state, CLEARS the `stale` marker, preserves connection. Published immediately.
- `markStale(connection)` (187–191) — sets `stale=true` + connection, RETAINS last-known values. Immediate.
- `setConnectionState(state)` (177–181) — control-plane immediate.
- `onKlippyMethod(method)` (170–174) — folds `notify_klippy_*` into KlippyState only (this is ALL the klippy events currently do besides the ready re-handshake).
- High-rate throttle: `DEFAULT_SAMPLE_MS = 250L` (273); the `init` sampled-flush loop (130–140). The cadence contract documents this as the "never surface faster than 250ms" rule.

---

## Pattern Assignments

### `MoonrakerSession.kt` (production-modify — the headline fix surface)

**Analog:** itself, in-place. Three concrete edit sites, all pinned to current line numbers.

**EDIT SITE 1 — the silent fire-and-forget re-handshake (lines 234–248), the bug:**
```kotlin
rpc.klippyEvents.onSubscription { klippyReady.complete(Unit) }.collect { method ->
    store.onKlippyMethod(method)                                  // KlippyState only (control-plane)
    if (method == JsonRpcMethods.NOTIFY_KLIPPY_READY && handshakeComplete) {
        attemptScope.launch {
            rehandshakeMutex.withLock {
                runCatching { runHandshake() }                    // ← D-03/Pitfall-4: silent, dead-forever
            }
        }
    }
}
```
**Direction (RESEARCH § Code Examples, line 245–248):** wrap with `emit(Syncing)` → `runHandshake()`
→ `emit(Connected)`; on failure, force a socket close so the supervisor's `run()` loop reconnects
(the proven path) instead of `runCatching{}`-swallowing forever. The exact close-the-socket mechanism:
reuse the same teardown the served-then-died path uses (cancel routing/collector + `rpc.close(reason)`
at lines 315–318), or signal the collector to complete `closed`. Decide the precise shape from the
**D-10 capture** (do NOT code before capturing — Pitfall 2).

**EDIT SITE 2 — drive recovery off disconnect, not just ready (same collect block, line 236):**
`store.onKlippyMethod(method)` currently only folds `notify_klippy_disconnected`/`shutdown` into
KlippyState. RESEARCH § Reliability says route them to `markStale(Syncing-or-Disconnected)` too so the
screen dims the instant klippy drops. NOTE the real signal may be `webhooks.state` in a
`notify_status_update` (Pitfall 5) — the reducer already maps that (PrinterStateReducer.kt:69–82,
`klippyFromWebhook`); the capture must enumerate which signal each printer emits.

**EDIT SITE 3 — `refreshProbeZOffset()` removal (lines 114–122), the cadence fix:**
```kotlin
suspend fun refreshProbeZOffset() {
    runCatching {
        val cfgResult = rpc.request(CommandRegistry.objectsQuery, ObjectSubsetArgs(setOf("configfile")))
        // … re-reads configfile.settings.probe.z_offset, republishes via store.setProbeZOffset(…)
    }
}
```
This is the ONE applied cadence fix (RESEARCH § "single real finding"). It duplicates the handshake's
configfile read (lines 387–424, which already calls `store.setProbeZOffset(...)` at 406). Remove it ONLY
AFTER the re-handshake fix proves it keeps config fresh on-device (Pitfall 3 — sequence as a follow-on
task, not parallel). Removal also touches the wiring in `MoonrakerService.kt` (see below).

**The handshake to re-run unchanged (lines 322–425):** the re-handshake just re-invokes this. The seed
path to reuse (RESEARCH lines 252–257) — query SEED then subscribe-reply SEED (lines 347–356) — closes
the query→subscribe gap and is already correct. Do not touch the seed ordering.

**Per-attempt gate state (lines 99–101, 190, 309):** `handshakeComplete` (Volatile) +
`rehandshakeMutex`. H2 check (RESEARCH): verify these are correctly armed/reset across a re-handshake —
`handshakeComplete=false` at attempt top (190), `=true` after the initial subscribe seed (309).

---

### `MoonrakerService.kt` (production-modify — `refreshProbeZOffset` wiring teardown)

**Analog:** itself, in-place. The cadence fix's other half.

The service wires `refreshProbeZOffset` into the `SpineHandle` (lines 177–179):
```kotlin
refreshProbeZOffset = { serviceScope.launch { session.refreshProbeZOffset() } },
```
When EDIT SITE 3 removes the method, this wiring + the `SpineHandle.refreshProbeZOffset` field + its
caller (the Probe-Calibrate page entry, UI side) must be removed/no-op'd together. The one-shot read
pattern to PRESERVE (and document as compliant in the contract) is the edge-driven holder style at
lines 144–158 (`PrintMetadataHolder` per-filename, `LastJobHolder` on-idle) — both fire one-shots off a
StateFlow edge, never a timer. This is the canonical "one-shot off the throttled hot path" pattern the
cadence contract polices.

---

### `CommandRegistry.kt` (production-read — cadence-contract inventory source)

**Analog:** itself. No code change expected; it is the **source of truth** for the contract doc.
Every `CommandSpec` is the call inventory. Map each to one-shot vs persistent for the doc:
- Persistent (the ONE subscribe): `objectsSubscribe` (110–115).
- One-shot seeds (per handshake): `identify`(67), `serverInfo`(89), `objectsList`(96),
  `objectsQuery`(103), `temperatureStore`(117), `gcodeStore`(125).
- One-shot edge-driven: `filesMetadata`(132), `filesGetDirectory`(140), `filesThumbnails`(154),
  `historyList`(208).
- Per-tap user actions: all `gcode(...)` specs (242+) + print-control RPCs (172–206).
The `availability` predicates (`ComponentPresent`/`ObjectPresent`) and the `all` list (438–486) give the
complete enumeration the contract table must cover.

---

### `DeriveCapabilities.kt` (production-read — subscribe-set audit)

**Analog:** itself. `V1_SUBSCRIBE_CORE` (50–68) + `deriveSubscribeSet` (76–98) are the subscribe subset.
The audit's job (RESEARCH § Subscribe-set justification) is to DOCUMENT the object→consumer mapping in
the contract, not change code. Note in the contract: `quad_gantry_level` (64, gated off on both test
printers, forward-compat) and the calibration objects (only diff during calibration). The intersect-with-
detected loop (81–84) enforces A3 ("never subscribe to an undefined object") — call this out as a rule.

---

### `SessionTestHarness.kt` + `FakeWebSocket.kt` (test-harden — the D-10 keystone)

**Analog:** itself. This is the **mock that lied** — the central hardening target.

**Why it lies (the structural defect):** `RespondingFakeWebSocket.send()` (lines 228–234) synchronously
injects `harness.replyFor(text)` for EVERY outbound frame, **unconditionally and instantly**:
```kotlin
override fun send(text: String): Boolean {
    val accepted = super.send(text)
    if (accepted) harness.replyFor(text)?.let { inject(it) }   // ← always-ready, never-drops server
    return accepted
}
```
`replyFor` (112–152) always returns a success reply for `objects.subscribe` (146–149). There is no
"klippy is down" state — so the live failure window cannot exist in the mock.

**Hardening (RESEARCH § "How to harden FakeWebSocket"):**
1. **Add a klippy-down window mode** to `SessionTestHarness` (a `@Volatile var klippyDown: Boolean` plus
   the captured timing). While down, `replyFor` for `OBJECTS_SUBSCRIBE`/`OBJECTS_QUERY` must either (a)
   return the REAL mid-restart error Moonraker sends (mirror the existing error-frame builders at 138/148:
   `{"error":{"code":...},"id":$id}`), or (b) withhold the reply until an explicit "klippy ready" step
   (do NOT auto-inject). Model this on the EXISTING `failOnOpen` (92–94) and `identifyErrorFrame` (89–90)
   toggles — same pattern, new mode.
2. **Inject the captured notification sequence verbatim** via `FakeWebSocket.inject(frame)` (FakeWebSocket.kt:55–57),
   e.g. `notify_klippy_disconnected` → [gap] → `notify_klippy_ready`, instead of a lone synthetic ready.
3. The `missingIdentifyArg`/`invalidObjectsSubset` validators (159–201) are the precedent: "the fake is
   no more lenient than the real server." Extend that philosophy to the restart window.

`FakeWebSocket` itself (base class) likely needs no change — it already exposes `inject`, `simulateClosing`,
`simulateFailure`, and `sentFrames`. The down-window logic lives in the responding subclass / harness.

---

### `KlippyReadyResyncTest.kt` (test-harden — assert resumed diffs, not sent frames)

**Analog:** itself + `KlippyLifecycleTest` (store-assertion style).

**The lie (lines 104–117):** the keystone test asserts only that subscribe/temp/configfile FRAME COUNTS
increased after `notify_klippy_ready` — never that the server accepted them and **diffs resumed**:
```kotlin
assertTrue("notify_klippy_ready must re-issue objects.subscribe …",
    count(after, JsonRpcMethods.OBJECTS_SUBSCRIBE) > subscribeBefore)   // ← sent-frame count only
```
**Hardening (RESEARCH Wave 0):** after the re-subscribe, inject a post-restart `notify_status_update`
carrying `print_stats.state=printing` via `fake.inject(...)`, then assert it reaches the store:
```kotlin
fake.inject("""{"jsonrpc":"2.0","method":"notify_status_update","params":[{"print_stats":{"state":"printing"}},123.0]}""")
advanceUntilIdle()
assertEquals(PrintState.Printing, store.printerState.value.printState)   // proves the subscription is LIVE again
```
The existing test setup (76–88) is the exact harness to reuse: `runTest(UnconfinedTestDispatcher())`,
`PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)`, `JsonRpcClient(defaultTimeoutMs = ...)`,
`launch { session.run() }`, `session.connectionState.first { it is Connected }`. The store-assert idiom
(`store.printerState.value.printState`) mirrors `KlippyLifecycleTest` (reduce → assert `klippyState`).
Drive the down-window via the hardened harness BEFORE injecting the resumed diff.

---

### `ReconnectSupervisorTest.kt` (test-extend — mid-print socket-death resync, D-07b)

**Analog:** itself. Add a test that drives a mid-print socket `Closed` → reconnect → asserts
`printState` resyncs to Printing. Reuse the existing scaffolding:
- `networkFailure_retriesForever_withBackoff` (40–61): `harness.failOnOpen`, `advanceTimeBy`, `harness.opens`.
- Drive the close via `harness.current.get()!!.driveClosing()` (RespondingFakeWebSocket.driveClosing, SessionTestHarness.kt:226 → FakeWebSocket.simulateClosing) or `driveFailure`.
- After reconnect (`harness.opens` increments, state returns to `Connected`), inject the running-print
  status and assert the store resyncs — the same resumed-diff assertion as the hardened keystone.
The socket-death path is already correct (RESEARCH § Current reconnect behavior); this test LOCKS it.

---

### `KlippyRecoveryStateTest.kt` (test-NEW — D-03 Syncing→Connected emission)

**Analog:** `KlippyReadyResyncTest` (structure) + `ReconnectSupervisorTest` (connectionState assertions).

Clone the `runTest` + harness + `launch { session.run() }` setup. Collect `session.connectionState`
transitions (e.g. into a list via a `launch { session.connectionState.toList(...) }` or assert sequential
`.first { it is Syncing }` then `.first { it is Connected }`). Inject `notify_klippy_ready` on the live
socket and assert the recovery emits `Syncing` THEN `Connected` (currently emits neither — the D-03
defect). Mirror `ReconnectSupervisorTest.authRequired_…`'s `connectionState.first { it is ... }` idiom
(lines 104–119).

### Self-heal test (test-NEW)

**Analog:** `ReconnectSupervisorTest.networkFailure_retriesForever` + the hardened down-window harness.
Put the harness in klippy-down mode so the re-handshake's `objects.subscribe` is rejected/withheld;
assert the session escalates to a full socket reconnect (`harness.opens` increments — a NEW socket)
rather than swallowing the failure (`opens` flat = the current dead-forever bug). This is the regression
guard for the D-01 fallback.

---

### `docs/request-cadence-contract.md` (doc-NEW)

**Analog:** `docs/moonraker-capabilities.md` — its sibling wire-truth guardrail. Match its structure:
- Top **Purpose** block ("source of truth … kill the recurring mock-vs-reality / chattiness bug class").
- A **RULES FOR FUTURE PHASES** block (RESEARCH § Recommended cadence-contract doc, line 192): (1) no
  `objects.subscribe` outside the central handshake; (2) no per-screen polling — derive from the central
  subscribe; (3) one-shot reads go through SpineHandle off the throttled hot path, edge-driven, never
  timer-driven; (4) high-rate numeric data conflated to `DEFAULT_SAMPLE_MS` (250ms).
- The call-inventory TABLE (RESEARCH lines 167–182, sourced from `CommandRegistry.all`).
- The subscribe-set justification (object → consumer, from `DeriveCapabilities`).
- Live `.jsonl` SAVE_CONFIG captures committed under `docs/commands/` (model on the committed
  `docs/commands/printer-matrix.json` wire-truth fixture; same discipline as Phase 8/9 `gcode_store_e5.json`).

---

## No Analog Found

None. Every file is a modification of, or a structural clone of, existing code. This is the expected
shape of a refactor/reliability phase — the value is the precise line-pinned edit sites above, not
discovering new patterns.

---

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/{net,state,command,service}/`,
`app/src/test/java/works/mees/dinghy/net/`, `docs/`.
**Files scanned:** ~14 (read in full or targeted: MoonrakerSession, DeriveCapabilities, CommandRegistry,
MoonrakerService, PrinterStateStore, PrinterStateReducer, PrinterState, RpcError, JsonRpcClient,
SessionTestHarness, FakeWebSocket, KlippyReadyResyncTest, ReconnectSupervisorTest, KlippyLifecycleTest,
moonraker-capabilities.md, the headline todo).
**Pattern extraction date:** 2026-06-03
