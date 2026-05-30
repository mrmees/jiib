---
phase: 02-connection-state-foundation
plan: 04
subsystem: session-integration-spine (Wave 3)
tags: [reconnect-supervisor, backoff, jitter, handshake, resync, connectionstate, auth, oneshot-token, conflation, statеflow, on-device]
requires:
  - phase: 02-connection-state-foundation
    provides: "MoonrakerSocket callbackFlow bridge + RpcConnection + JsonRpcClient (id-correlation, per-request timeout, close-fails-all-pending, notify routing, separate gcode flow) — 02-03"
  - phase: 02-connection-state-foundation
    provides: "PrinterStateReducer (reduceSnapshot/reduceDiff), DeriveCapabilities (deriveCapabilities/deriveSubscribeSet), applyKlippyMethod — 02-02"
  - phase: 02-connection-state-foundation
    provides: "JSON-RPC envelopes + MoonrakerJson + JsonRpcMethods, typed ConnectionError/RpcError + classifyIdentifyError, ConnectionState (incl. Syncing), DevConfig.wsUrl/apiKey, FakeWebSocket harness, golden+adversarial fixtures — 02-01"
provides:
  - "Backoff.kt: pure overflow-safe backoffDelay(attempt, base, rng) — clamped exponent / safe Duration math, jitter, NO ceiling (D-01)"
  - "MoonrakerSession.kt: supervisorScope reconnect loop + ordered identify→objects.list→deriveCapabilities→query→subscribe handshake + five-state ConnectionState StateFlow (Connected only after resync) + re-derived Capabilities + requestReconnectNow() (D-02) + gentle AuthRequired quiescence + clientUrl identify arg"
  - "MoonrakerAuth.kt: oneshot-token fetch via injectable Call.Factory, ?token= URL build, X-Api-Key header, typed 401/identify-error classification (AuthRequired/ProtocolError/ServerError, A5 defensive default), secrets never logged"
  - "PrinterStateStore.kt: assembled PrinterState StateFlow (high-rate-only conflation ~2-4 Hz; control-plane immediate), separate un-throttled gcode SharedFlow, retain-last-known + stale marker on drop, Capabilities StateFlow"
  - "LiveSocketReconnectTest.kt: on-device live ws + handshake + Syncing→Connected ordering proof against the real Ender 5 Plus (no-auth path, D-06)"
  - "Phase-2 success criterion #1 PROVEN on real hardware: live ws + reconnect + resync handshake"
affects: [phase-3-service-shell, phase-6-job-status, reconnect, resync, auth, conflation, klippy-routing]
tech-stack:
  added: []
  patterns:
    - "supervisorScope reconnect loop: select { onTimeout(backoff){}; reconnectNow.onReceive{} } with Channel(CONFLATED) immediate-retry hook (02-RESEARCH Pattern 3)"
    - "Overflow-safe backoff: clamp the 2^attempt exponent / safe Long-ms math so a huge attempt count stays finite+positive — 'no ceiling' via bounded arithmetic, not a give-up cap (D-01)"
    - "Resync handshake as an explicit ordered sequence run exactly once per (re)connect; Connected gated behind Syncing until the subscribe seed lands (review HIGH #2/#3)"
    - "Split-plane state assembly: sample(~250ms) ONLY the high-rate numeric plane; merge ConnectionState/KlippyState/stale/auth/print-state un-sampled so control transitions are never swallowed by conflation (STATE-03)"
    - "Gentle auth quiescence: AuthRequired suspends the loop (no token-fetch storm against a bad key); only config-change or requestReconnectNow() resumes — distinct from D-01 network backoff"
    - "Injectable Call.Factory for auth so the whole oneshot-token/401 branch is unit-testable with no network (D-06)"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/auth/MoonrakerAuth.kt
    - app/src/main/java/works/mees/dinghy/net/Backoff.kt
    - app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt
    - app/src/test/java/works/mees/dinghy/auth/AuthHandshakeTest.kt
    - app/src/test/java/works/mees/dinghy/net/BackoffTest.kt
    - app/src/test/java/works/mees/dinghy/net/ReconnectSupervisorTest.kt
    - app/src/test/java/works/mees/dinghy/net/HandshakeTest.kt
    - app/src/test/java/works/mees/dinghy/net/ConnectionStateTest.kt
    - app/src/test/java/works/mees/dinghy/state/ConflationTest.kt
    - app/src/androidTest/java/works/mees/dinghy/connection/LiveSocketReconnectTest.kt
  modified: []
key-decisions:
  - "server.connection.identify REQUIRES a non-empty `url` arg (alongside client_name/version/type) — Moonraker rejects a url-less identify with {code:400,\"No data for argument: url\"}. Discovered AT the on-device gate, not in any JVM test."
  - "MoonrakerSession takes a clientUrl ctor param (default https://mees.works/dinghy-display) sent as the identify `url`."
  - "Overflow-safe backoff keeps D-01's 'no ceiling' semantics but clamps arithmetic so a huge attempt count yields a large finite positive Duration (never overflow/negative/zero)."
  - "Connected is emitted ONLY after query+subscribe land; Syncing covers socket-open-but-not-resynced (review HIGH #3)."
  - "AuthRequired → gentle quiescence (loop suspends, no churn); uncapped backoff is for network-offline only (D-01)."
  - "Conflation is split-plane: sample only the high-rate numeric fields; control-plane + gcode are immediate (STATE-03)."
patterns-established:
  - "Pattern: a synthetic mock that is MORE LENIENT than the real server hides protocol bugs — the fake must enforce the server's required-field contract; the on-device gate is the backstop that catches what the mock encoded as an assumption."
  - "Pattern: live-spine instrumented test asserts ordering by collecting the ConnectionState sequence with a single UNDISPATCHED collector and checking index(Syncing) < index(Connected), not racy two-collector atomics."

requirements-completed: [CONN-02, CONN-03, CONN-04, CONN-06, STATE-02, STATE-03]

duration: ~110min (multi-session incl. on-device gate)
completed: 2026-05-30
---

# Phase 2 Plan 04: Session Integration Spine Summary

**The reconnect supervisor + ordered resync handshake + five-state ConnectionState + oneshot-token auth + split-plane conflated PrinterState — wired into one working connection and PROVEN live on the real Ender 5 Plus (where it caught a real Moonraker protocol bug the entire green JVM suite had missed).**

## Performance

- **Duration:** ~110 min (spanned implementation + the on-device gate, which surfaced and fixed a protocol bug)
- **Tasks:** 3 (Task 3 was the human-verify on-device phase gate)
- **Files created:** 11 (4 main, 6 JVM tests, 1 instrumented test)
- **Files modified:** 0 (all-new spine)

## Accomplishments

- **The four Phase-2 seams became a working connection.** `MoonrakerSession` orchestrates `MoonrakerSocket`/`JsonRpcClient` (02-03) + `PrinterStateReducer`/`DeriveCapabilities` (02-02) into a self-healing reconnect supervisor.
- **Phase-2 success criterion #1 is proven on real hardware** — live ws, full resync handshake, and live `notify_status_update` updating non-stale state on the actual Ender 5 Plus.
- **The on-device gate did its job:** it caught a real Moonraker protocol contract violation (`identify` requires `url`) that every JVM unit test reported green, because the mock was more lenient than the server. This is the single most important takeaway of the plan (see Deviations).
- Overflow-safe uncapped backoff+jitter (D-01) + `requestReconnectNow()` immediate-retry (D-02), virtual-time proven.
- Ordered handshake `identify → objects.list → deriveCapabilities → query → subscribe` exactly once per (re)connect, fresh query overwrites stale state (D-04), Capabilities re-derived each reconnect (STATE-02), review HIGH #2.
- `Connected` gated behind `Syncing` until the subscribe seed lands (review HIGH #3, CONN-06).
- Oneshot-token + `X-Api-Key` + typed 401/identify-error classification via an injectable `Call.Factory`, mock-tested per D-06 (CONN-02); secrets never logged.
- Split-plane conflation: high-rate numeric status sampled to ~2-4 Hz while ConnectionState/KlippyState/stale/auth/print-state + gcode lines stay immediate (STATE-03); retain-last-known + stale marker on drop (D-03).

## Task Commits

1. **Task 1: MoonrakerAuth** — `e4a4273` (feat) — oneshot-token + `X-Api-Key` + typed auth/identify classification (CONN-02)
2. **Task 2: Backoff + MoonrakerSession + PrinterStateStore** — `cbee68c` (feat) — supervisor + handshake + assembled StateFlow (CONN-03/04/06, STATE-02/03, D-01/02/03/04, review HIGH #2/#3)
3. **Task 3 (RED/authoring): LiveSocketReconnectTest** — `452a1c8` (test) — on-device live spine proof (the phase gate)
4. **Task 3 (gate fix): identify `url` bug** — `74034b9` (fix) — identify must send required `url` arg; harden harness + live test ← **bug found AT the gate**

**Plan metadata:** docs(02-04) commit (this SUMMARY + STATE + ROADMAP + REQUIREMENTS).

## Files Created/Modified

- `auth/MoonrakerAuth.kt` — oneshot-token fetch via injectable `Call.Factory`, `?token=` URL build, `X-Api-Key`, typed failure classification (AuthRequired/ProtocolError/ServerError, A5 default), redaction by construction
- `net/Backoff.kt` — pure overflow-safe `backoffDelay` (clamped exponent / safe Duration math, jitter, no ceiling — D-01)
- `net/MoonrakerSession.kt` — supervisorScope reconnect loop, ordered resync handshake (now incl. the required identify `url` arg), five-state ConnectionState StateFlow, re-derived Capabilities, `requestReconnectNow()`, gentle AuthRequired quiescence
- `state/PrinterStateStore.kt` — assembled PrinterState StateFlow (split-plane conflation), separate un-throttled gcode SharedFlow, retain+stale on drop, Capabilities StateFlow
- `*Test.kt` (6 JVM, virtual-time/FakeWebSocket) — Backoff, ReconnectSupervisor, Handshake, ConnectionState, AuthHandshake, Conflation
- `androidTest/.../LiveSocketReconnectTest.kt` — on-device live spine proof (NEW class, not the throwaway smoke test)

## Decisions Made

- **`server.connection.identify` requires a non-empty `url`** — added `clientUrl` ctor param (default `https://mees.works/dinghy-display`) and send it as the identify `url`. (See Deviations — this was the gate finding.)
- Overflow-safe backoff preserves D-01 "no ceiling" via bounded arithmetic, not a give-up cap.
- Connected gated behind Syncing (review HIGH #3); AuthRequired → gentle quiescence distinct from network backoff; conflation is split-plane (STATE-03).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `server.connection.identify` was missing the REQUIRED `url` argument — the live connection never reached Connected (caught ONLY at the on-device gate)**

- **Found during:** Task 3 (the on-device phase gate — the live test initially FAILED, timing out at 20s with `ConnectionState` never reaching `Connected`)
- **Issue:** Moonraker's `server.connection.identify` REQUIRES a non-empty `url` argument alongside `client_name`/`version`/`type`. The spine sent identify without `url`, so Moonraker replied `{code:400,"No data for argument: url"}` → the handshake threw → the reconnect supervisor looped on backoff forever, never resyncing. **The connection silently never worked against a real printer.**
- **Why every JVM unit test was GREEN anyway (the important part):** the `FakeWebSocket`/`SessionTestHarness` replied SUCCESS to identify regardless of params — the synthetic mock encoded *the code's own assumption*, not Moonraker's real contract. Compounding it: the golden fixtures are synthetic copies, never captured from a live printer (a debt flagged in the 02-01 SUMMARY). **A mock more lenient than the real server hides protocol bugs.** This is *exactly* why the on-device gate exists, and it earned its keep here.
- **Fix:** (commit `74034b9`)
  - `MoonrakerSession` now sends `url` via a new `clientUrl` ctor param (default `https://mees.works/dinghy-display`).
  - `SessionTestHarness` now REJECTS a url-less/incomplete identify with the REAL `{code:400,"No data for argument: <arg>"}` shape — a permanent regression guard so the mock can no longer be more lenient than the server.
  - `HandshakeTest` asserts identify carries non-blank `client_name`/`version`/`type`/`url`.
  - `LiveSocketReconnectTest`'s racy two-collector + atomics ordering check was replaced with a single UNDISPATCHED sequence collector asserting `index(Syncing) < index(Connected)`.
- **Verification:** full JVM suite green (exit 0) after the harness was tightened; on-device `LiveSocketReconnectTest` then went GREEN against the live printer (evidence below).
- **Committed in:** `74034b9`

**Forward-looking lesson (the takeaway of this plan):** Synthetic mocks that are looser than the real server will pass while the real integration is broken. Mocks must enforce the server's required-field contract, and a real-hardware gate must back-stop protocol assumptions. Do not trust a green JVM suite as proof the wire format is correct.

---

**Total deviations:** 1 auto-fixed (Rule 1 — bug). **Impact:** essential for correctness — without it the connection never worked on a real printer. No scope creep; the fix also hardened the test harness against the class of bug that hid it.

## Issues Encountered

- The initial live test ordering assertion (two collectors + atomics) was racy/flaky; replaced with a single undispatched sequence collector checking emission order by index. Resolved in `74034b9`.

## Verification

- **`:app:testDebugUnitTest` — GREEN (exit 0):** full JVM suite across plans 01–04 together (fixtures, reducer, capabilities, klippy, socket, rpcconnection, jsonrpc, backoff, reconnect, handshake, connstate, conflation, auth), including the tightened url-required harness/handshake guards.
- **`:app:connectedDebugAndroidTest` (LiveSocketReconnectTest) — GREEN (exit 0)** on the REAL `flox` device (armeabi-v7a, API 30 / LineageOS 18.1) against the LIVE **Ender 5 Plus Moonraker v0.13 at 192.168.1.120:7125**:
  - connect → `identify(+url)` → `objects.list` (**156 objects**) → `objects.query` (live `heater_bed`/`extruder` seeded) → `objects.subscribe`
  - `Connected` emitted ONLY after `Syncing`
  - live `notify_status_update` updated non-stale `PrinterState`
- **Independent raw-websocket probe** confirmed the same handshake end-to-end against the live printer.
- **Wi-Fi-yank reconnect/resync** observed on the real printer (human-verify checkpoint): Disconnected → state RETAINED with stale marker (not blanked, D-03) → backoff reconnect → fresh `objects.query` restores correct non-stale temps/position (D-04).

## Requirements Completed

- **CONN-02** — optional auth on ws + REST (oneshot-token + X-Api-Key + typed 401/identify classification), mock-tested per D-06
- **CONN-03** — long-lived ws + auto-reconnect with overflow-safe backoff+jitter, no user intervention
- **CONN-04** — full resync handshake (identify → objects.query snapshot → objects.subscribe) on every (re)connect, displayed state never stale — proven live
- **CONN-06** — five-state ConnectionState always observable; Connected only after resync
- **STATE-02** — capabilities re-derived from `objects.list` on every reconnect, exposed as Capabilities StateFlow
- **STATE-03** — high-rate streams throttled to ~2-4 Hz at the state layer; control-plane + gcode immediate

## Notes for Downstream Phases (non-blocking follow-up)

- **Golden-fixture enrichment (NON-BLOCKING, do later):** the three `golden/*.json` live fixtures are still SYNTHETIC. Capture + redact REAL Ender 5 Plus frames into them as corpus enrichment — `GoldenFixtures.resolve()` already prefers live frames when present. The url-bug is the concrete argument for why real captured frames matter: synthetic frames can't encode the server's required-field contract. Not required to close Phase 2; recommended before relying on the JVM suite as a wire-format oracle.
- **Phase 3 wiring:** the foreground service should own a single `MoonrakerSession`; the shell observes its `ConnectionState`/`PrinterState`/`Capabilities` StateFlows. The user-facing config screen (CONN-01) replaces the static `DevConfig` and feeds host/port (and optional key) into the session; a config change should trigger `requestReconnectNow()` and lifts AuthRequired quiescence.

## Next Phase Readiness

- **Phase 2 is functionally complete and gate-proven.** All four seams are integrated and the live spine works on real hardware. All 9 Phase-2 requirements are satisfied across plans 01–04 (CONN-02/03/04/06, STATE-01..05).
- Ready for Phase 3 (Service, Shell & State-Driven Navigation): the spine exposes everything the shell/service need (ConnectionState, PrinterState, KlippyState, Capabilities, gcode flow, requestReconnectNow).
- **Concern (carry forward):** synthetic golden fixtures — see follow-up above.

## Self-Check: PASSED

- All 11 created files exist on disk (4 main, 6 JVM tests, 1 instrumented test — verified).
- All 4 task commits in git history: `e4a4273`, `cbee68c`, `452a1c8`, `74034b9` (verified via `git log`).
- Full JVM suite + on-device live test both green (cited above); no source modified during finalization.

---
*Phase: 02-connection-state-foundation*
*Completed: 2026-05-30*
