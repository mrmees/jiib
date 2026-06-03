# Phase 13: Optimization, Network Efficiency & End-to-End Reliability - Research

**Researched:** 2026-06-03
**Domain:** Moonraker JSON-RPC session reliability (klippy-restart recovery), request-cadence auditing, in-session reconnect/resync hardening — Kotlin/Coroutines on Android
**Confidence:** HIGH on root-cause direction (Moonraker subscription lifecycle confirmed at source level); HIGH on cadence inventory (read from code); MEDIUM on the *exact* failing edge (needs the live wire capture D-10 mandates before the fix is final)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01: Root-cause first, then decide the fix.** Determine WHY the in-session re-subscribe dies before picking a remedy (does `notify_klippy_ready` actually fire + get caught? does `objects.subscribe` re-register get silently dropped after a klippy restart on the same socket? is the frame collector / `rpc.statusUpdates` torn down?). The force-full-reconnect-on-`klippy_ready` path (the one that demonstrably works on app restart) is the **named fallback** — NOT the default; understand the failure first.
- **D-02: Do NOT assume the bug is Ender-3-specific.** The "G2 verified on E5" sign-off was Phase 5 and predates any SAVE_CONFIG exercise on the E5 since. Treat printer-independence as **unproven** — the bug may be present everywhere. Investigation and verification both span both printers.
- **D-03: Recovery shows a brief Syncing splash EVERY time.** Every klippy-restart recovery surfaces the Connecting/Syncing state. The current in-session re-handshake runs **silently** (re-runs `runHandshake()` on `attemptScope` with no `ConnectionState` emit) — "emit Syncing on `klippy_ready` re-handshake start, Connected when the reseed lands" is a concrete code change to bake in, regardless of which recovery mechanism wins.
- **D-04: Audit is preventative** — no observed chattiness today. The goal is to PROVE the subscribe-driven model is clean before phases 10–12 stack on it.
- **D-05: Protect the host CPU, not just the LAN.** The Klipper hosts are weak SBCs (E5 = Pi 4, E3 = RockPro64); cadence discipline guards Moonraker's CPU as much as the WiFi. Both are audit targets.
- **D-06: Deliverable = a committed request-cadence contract doc + fixes** under `docs/`. The doc becomes the guardrail phases 10–12 must honor. Known suspect: `refreshProbeZOffset()` re-queries `configfile` on every Probe-Calibrate page open — if the re-handshake fix keeps config fresh, that masking re-query may become redundant.
- **D-07: Phase 13 owns ALL in-session resync** — klippy-restart recovery, network-drop-mid-print reconnect, and the print-state resync that rides along. If the SAVE_CONFIG fix strengthens general reconnect-resync, bank it now.
- **D-08: Phase 14 shrinks to the Android/OS/ship layer only** (process-death, Doze, burn-in, R8, signed APK). This moves ROADMAP Phase 14's "reconnect print-state resync" line into Phase 13 — flag for a roadmap touch-up.
- **D-09: Live UAT gate = BOTH printers, BOTH scenarios.** (a) SAVE_CONFIG → start print → assert feed stays live and new print registers (printState→Printing) without app restart, and (b) mid-print network drop → reconnect → print state resyncs. Run on E5 (Pi 4, 192.168.1.120:7125) AND E3 (RockPro64, 192.168.1.121:7125).
- **D-10: Fixture-first automated regression — harden the mock to the REAL wire contract.** Capture the actual klippy-restart wire sequence live (`notify_klippy_ready` + what Moonraker actually does to the subscription/socket) BEFORE coding the fix, then harden `FakeWebSocket` to replay that exact contract, then write the `SAVE_CONFIG→Printing` regression. `KlippyReadyResyncTest.kt` is the canonical mock that lied — harden it, don't trust it.

### Claude's Discretion
- The exact recovery mechanism (fixed in-session re-subscribe vs force-reconnect) — decide from the root cause, not up front.
- Specific cadence-contract format and where under `docs/` it lives.

### Deferred Ideas (OUT OF SCOPE)
- Process-death recovery, Doze / always-on survival, burn-in screensaver, R8 release, signed APK — Phase 14.
- CONS-01 arbitrary G-code SEND — still deferred (console read-only).
- Other pending todos: `console-macro-page-ux-flow`, `status-progress-ring-dual-source-jump`, `benchmark-harness-fairness-fixes`, `macrobenchmark-module-wiring`, `files-delete-gating-too-broad`. Revisit in their own phases.
</user_constraints>

<phase_requirements>
## Phase Requirements

Quality/refactor phase — no new functional REQ-IDs. Success is measured against ROADMAP success criteria:

| ID | Description | Research Support |
|----|-------------|------------------|
| SC-1 | Cadence audit complete + applied: every subscribed object justified, high-rate data coalesced to display cadence, no screen polls outside central subscribe; measurably reduced per-second LAN volume | § Cadence Audit (full call inventory + the one real fix: `refreshProbeZOffset`) |
| SC-2 | Error/staleness handling principled across screens (Phase-6 command/error reference) | § Thread 3 Reliability — the `markStale`/`stale` model already exists; gaps are the silent re-handshake (D-03) and disconnect/shutdown not driving recovery |
| SC-3 | Optimization behavior-preserving — every screen still shows correct live data — provable on E5 + E3 with on-device gates green | § Validation Architecture (D-09 dual-printer dual-scenario gate) |
| SC-4 | Cross-screen reliability holds (rapid nav, reconnect mid-feature, capability changes) without leaks or stale subscriptions | § Thread 1 root-cause + § Thread 3 (the re-handshake is the cross-screen reliability spine) |
</phase_requirements>

## Summary

The headline bug is **not an architecture flaw** — it is a **timing/sequencing defect in the in-session `notify_klippy_ready` re-handshake path**, made invisible by a mock that is structurally incapable of reproducing the failure and made unrecoverable by a fire-and-forget `runCatching` that swallows the failure with no retry and no fallback.

Three facts pin the root cause down to the session layer (verified, not assumed):
1. **Moonraker source confirms the contract the app relies on is real:** on klippy disconnect Moonraker does `self.subscriptions = {}` and `self.subscription_cache.clear()`, and it does **NOT** auto-re-register a client's subscription on klippy-ready — "each client must explicitly re-send `objects/subscribe`." So the *design* (re-run `runHandshake()` on `notify_klippy_ready`) is the correct response. [VERIFIED: github.com/Arksine/moonraker klippy_connection.py source]
2. **A fresh socket fixes it instantly** (the repro proves force-close → reopen shows the running print correctly), so the reducer, routing, and subscribe-set derivation are all correct. The defect is exclusively in the *in-session* recovery, on the *same still-open socket*.
3. **The green test cannot fail the way reality fails:** `SessionTestHarness` auto-replies to every `objects.subscribe` synchronously and unconditionally the instant the frame is sent. It models a server that is *always ready and always answers*. The live failure window — Moonraker has dropped the subscription, klippy is mid-restart, and a re-subscribe sent at the wrong moment is dropped, delayed, or answered against a not-yet-ready klippy — does not exist in the mock.

**Primary recommendation:** Do the **live wire capture FIRST** (D-10, capture-first discipline) to settle the single load-bearing unknown — *what exact notification sequence does Moonraker emit on this socket across a SAVE_CONFIG, and does `notify_klippy_ready` actually arrive on the already-open socket?* Then make the targeted fix the evidence points to. Based on code + source analysis, the highest-probability fix is **(a) emit `Syncing` and drive recovery off `notify_klippy_disconnected`/`shutdown` (not only `ready`), and (b) make the re-handshake non-silent and self-healing: on re-handshake failure, force a full socket reconnect** (the path that demonstrably works) rather than `runCatching{}`-swallowing it forever. This is the D-01 fallback, reached *from* the root cause, not defaulted to. The cadence audit is genuinely "verify-and-document" — the architecture is clean; the only real fix is collapsing `refreshProbeZOffset`'s redundant `configfile` re-query once the re-handshake keeps config fresh.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| klippy-restart detection | Session (`MoonrakerSession`) | JsonRpcClient (routes `klippyEvents`) | Notifications arrive on the socket; the session supervisor owns recovery decisions |
| Re-subscribe after restart | Session (`runHandshake()`) | Moonraker (clears subs, requires re-send) | Moonraker mandates per-connection re-subscribe; the session is the only place that holds the subscribe set |
| Connection-state surfacing (Syncing) | Session (`emit()`) → Store (control-plane) | UI (observes `connectionState`) | D-03 requires a visible Syncing splash every recovery — owned by the emit path |
| Request cadence / throttling | Store (high-rate plane) + Session (one-shot reads) | UI (must NOT poll) | Two-plane model already centralizes this; the contract polices the boundary |
| Print-state resync | Reducer (`reduceSnapshot`) seeded by Session | Store (`seed` clears stale) | Resync is a re-seed from `objects.query`/`subscribe` — pure reducer, driven by session |
| Network-drop reconnect | Session supervisor (`run()` backoff loop) | Socket (`callbackFlow` close → `Closed`) | Already structured-concurrency-correct for full socket death |

## Standard Stack

No new dependencies. This phase is a refactor of existing session-layer Kotlin. The stack is locked by CLAUDE.md/ADR-0001 and unchanged:

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| kotlinx-coroutines | 1.9.x | structured concurrency, `StateFlow`/`SharedFlow`, `runTest` virtual time | Already the session/reconnect backbone |
| OkHttp | 4.12.x | one websocket (callbackFlow-bridged) | Already the transport; the re-handshake rides the same socket |
| kotlinx.serialization | 1.7.x | JSON-RPC envelope + loose status JSON | Already used for every frame |

**No `npm`/`pip`/`cargo` install — Android Gradle, no new artifacts.** Package Legitimacy Audit is therefore **N/A** (no external packages added this phase).

## Root-Cause Analysis (Thread 1 — D-01)

### The confirmed Moonraker contract (the load-bearing unknown, now resolved)

[VERIFIED: github.com/Arksine/moonraker `klippy_connection.py` source via raw.githubusercontent] On a klippy disconnect, `_on_connection_closed()` runs:
```python
self.subscriptions = {}
self.subscription_cache.clear()
```
and on klippy-ready, Moonraker calls only `_request_initial_subscriptions()` (webhooks + gcode output for its *own* internal use) — it does **NOT** restore any external client's subscription. [VERIFIED] The docs corroborate: *"A new request will override a previous request. If `objects` is set to an empty object then the subscription will be cancelled,"* and the recommended recovery is *"watch for `notify_klippy_disconnected` … repeat the steps above to determine when klippy is ready"* then re-subscribe. [CITED: moonraker.readthedocs.io/external_api/introduction, /external_api/printer]

**Conclusion:** the app's strategy (re-run the full handshake, which re-issues `objects.subscribe`) is the *correct, documented* recovery. The bug is in *execution*, not strategy. This is the evidence D-01 demands for choosing the fix.

### What the code actually does on `notify_klippy_ready` (MoonrakerSession.kt ~231–250)

```kotlin
rpc.klippyEvents.onSubscription { klippyReady.complete(Unit) }.collect { method ->
    store.onKlippyMethod(method)                          // KlippyState only
    if (method == JsonRpcMethods.NOTIFY_KLIPPY_READY && handshakeComplete) {
        attemptScope.launch {
            rehandshakeMutex.withLock {
                runCatching { runHandshake() }            // ← fire-and-forget, swallows ALL failure
            }
        }
    }
}
```

### Ranked root-cause hypotheses (to be confirmed by the D-10 capture)

**H1 (HIGHEST probability) — recovery is gated on the WRONG notification + the failure is swallowed.**
The app only re-handshakes on `notify_klippy_ready`. But on a SAVE_CONFIG → FIRMWARE_RESTART, Moonraker emits `notify_klippy_disconnected` (and/or `notify_klippy_shutdown`) **first**, clearing the subscription immediately; `notify_klippy_ready` arrives later, *if* it is delivered to this already-open socket at all. Two failure modes fall out:
  - If `notify_klippy_ready` *does* arrive but the re-handshake's `objects.subscribe` is sent in a window where klippy/Moonraker rejects or drops it (mid-reload), `rpc.request()` either throws an `RpcError` or times out — and `runCatching{}` **swallows it silently with no retry and no fallback**. The socket stays open, so the supervisor's full-reconnect path (which works) is never reached. The feed is dead forever until force-close. This exactly matches the repro: "stuck on the last finished print, force-restart fixes it."
  - If `notify_klippy_ready` is *not* re-delivered on the same socket (only the initial connect's klippy_ready was seen), the re-handshake never even fires.

**H2 (MEDIUM) — the re-handshake succeeds but the seed is dropped/ordered wrong.** `runHandshake()` re-issues `objects.subscribe` and seeds from the reply. But the `routing` collectors (`statusUpdates`, `klippyEvents`, `gcodeResponses`) were attached once per *socket attempt* via `onSubscription`-gated `CompletableDeferred`s. Those deferreds are NOT re-armed on a re-handshake (they already completed). The SharedFlows are `replay=0`, so this is fine for an *already-attached* collector — but it means the collectors must still be alive. If `attemptScope` or the routing job is in any way disturbed, diffs silently stop. (Lower probability — the collectors live on `attemptScope` which outlives the re-handshake — but the capture must rule it out.)

**H3 (LOWER) — `print_stats` seed overwrite vs. merge.** After re-subscribe, `store.seed(reduceSnapshot(status))` REPLACES state from the fresh snapshot. If the fresh `objects.query` after a restart returns a transient/incomplete `print_stats` (klippy just came up, print not yet re-registered), the seed could plant a stale snapshot that later diffs never correct because no diff touches the unchanged fields. The "stuck on last finished print" symptom is consistent with H1 (no re-subscribe at all) but the capture should confirm whether a partial seed is also in play.

### Why the green test lies (D-10 — the canonical mock-that-lied)

`SessionTestHarness.RespondingFakeWebSocket.send()` synchronously calls `harness.replyFor(text)` and injects the canned reply for **every** outbound frame, **unconditionally and instantly**. It models a server that:
- never drops a subscribe,
- never delays a reply across a restart window,
- always has klippy "ready" to answer `objects.query`/`subscribe`,
- re-delivers `notify_klippy_ready` exactly when injected by the test.

The live failure is precisely the *absence* of those guarantees during the restart window. `KlippyReadyResyncTest` asserts only that the *frames were sent* (subscribe count increased) — never that the server *accepted them, replied, and diffs resumed against a not-yet-ready-then-ready klippy*. **It is structurally incapable of failing the way reality fails.** This is the third strike of the project's mock-vs-reality class (after the Phase-2 identify-`url` bug and the Phase-5 G1/G2/G4 runtime bugs).

## Wire-Capture Discipline (Thread 1b — D-10, MUST precede the fix)

A plan task must capture the **real** klippy-restart wire sequence on BOTH printers before any fix code is written. The researcher cannot run the printer; here is the concrete method a plan task executes.

### What to capture
The complete frame sequence on a single still-open websocket, from just before a `SAVE_CONFIG` to ~30s after klippy returns to ready, specifically:
1. The exact ordering and presence of `notify_klippy_disconnected`, `notify_klippy_shutdown`, `notify_klippy_ready` (does `ready` actually arrive on the *same* socket?).
2. Any `notify_status_update` with `webhooks.state` transitions (`ready`→`shutdown`/`startup`→`ready`) — these may be the *real* signal, not the bare klippy notifications.
3. Whether an `objects.subscribe` sent immediately on `notify_klippy_ready` gets a normal `{result:{status:...}}` reply, an error, or silence.
4. Whether `notify_status_update` diffs resume after a re-subscribe.

### How to capture (no app build required)
Moonraker speaks the same JSON-RPC over a raw websocket. Use a scriptable websocket client against the live printer:

```bash
# On any machine on the LAN (E5 = 192.168.1.120, E3 = 192.168.1.121). websocat or a tiny python script.
# 1. Connect, identify, list, subscribe core objects.
# 2. Log EVERY inbound frame with a timestamp to a file.
# 3. From a second shell (or Mainsail), trigger SAVE_CONFIG (or FIRMWARE_RESTART).
# 4. Keep logging through klippy down → up. Then send objects.subscribe again and log the reply + subsequent diffs.

websocat -t ws://192.168.1.120:7125/websocket | ts '%.s' | tee e5-saveconfig-capture.jsonl
# (send identify/subscribe frames via a second websocat --text or a small python websockets script)
```
A short Python `websockets` script is more reliable for sending the handshake frames and timestamping (the project already uses read-only `curl` probes for capability capture — same discipline). Commit the raw `.jsonl` captures as fixtures alongside `docs/commands/*.json`, exactly as Phase 8/9 committed `gcode_store_e5.json`.

### How to harden `FakeWebSocket` to the captured contract
Once the real sequence is known, the fix to `SessionTestHarness`/`FakeWebSocket` is:
- **Model a "klippy-down" window.** Add a mode where, after an injected `notify_klippy_disconnected`, the responding fake either (a) rejects `objects.subscribe`/`objects.query` with the real error Moonraker returns mid-restart, or (b) withholds the reply until an explicit "klippy ready" step — replaying the captured timing, not an instant success.
- **Inject the captured notification sequence verbatim** (disconnect → [gap] → ready, or whatever the capture shows) instead of a lone synthetic `notify_klippy_ready`.
- **Assert on resumed diffs, not sent frames.** The hardened regression must inject a post-restart `notify_status_update` carrying `print_stats.state=printing` and assert it reaches `store.printerState` (printState→Printing) — proving the *subscription is live again*, not merely that a subscribe frame left the client.

## Cadence Audit (Thread 2 — D-04/05/06)

### Confirmed: no per-screen polling
[VERIFIED: grep across `ui/`] Every `delay(4_000)` is a toast/failure-text auto-dismiss timer inside a `LaunchedEffect(failureText)`, NOT a poll (confirmed in MoveScreen.kt:134–139 and the same pattern in Temperature/Extrude/PrintStatus/ScrewsTilt). The claim in CONTEXT.md holds.

### Full Moonraker call inventory (the raw material for the contract doc)

| Call | Method | Transport | Type | Trigger | Frequency | Cadence verdict |
|------|--------|-----------|------|---------|-----------|-----------------|
| identify | `server.connection.identify` | JSON-RPC | one-shot | each (re)connect + each re-handshake | per connect | OK |
| server.info | `server.info` | JSON-RPC | one-shot | each handshake | per connect | OK |
| objects.list | `printer.objects.list` | JSON-RPC | one-shot | each handshake | per connect | OK |
| objects.query(subset) | `printer.objects.query` | JSON-RPC | one-shot (seed) | each handshake | per connect | OK |
| **objects.subscribe(subset)** | `printer.objects.subscribe` | JSON-RPC | **persistent** | each handshake | per connect — drives ALL live data | **The single subscribe. Justify every object below.** |
| temperature_store | `server.temperature_store` | JSON-RPC | one-shot | each handshake | per connect | OK (graph backfill) |
| gcode_store(1000) | `server.gcode_store` | JSON-RPC | one-shot | each handshake | per connect | OK (console backfill) |
| **configfile query** | `printer.objects.query` `{configfile}` | JSON-RPC | one-shot | each handshake | per connect | OK in handshake |
| **configfile query (DUP)** | `printer.objects.query` `{configfile}` | JSON-RPC | one-shot | **`refreshProbeZOffset()` on every Probe-Calibrate page open** | per page-open | **D-06 SUSPECT — see below** |
| files.metadata | `server.files.metadata` | JSON-RPC | one-shot-per-filename | active filename change | per filename | OK (one-shot-per, never polled) |
| files.get_directory | `server.files.get_directory` | JSON-RPC | one-shot | file browser open / navigate | per nav | OK (user-driven) |
| files.thumbnails | `server.files.thumbnails` | JSON-RPC | one-shot | thumbnail need | per file | OK |
| history.list(1) | `server.history.list` | JSON-RPC | one-shot | every not-printing transition | per idle edge | OK (edge-driven, never polled) |
| gcode/print/calibration commands | `printer.gcode.script` etc. | JSON-RPC | one-shot | user action | per tap | OK |

### The single real finding: `refreshProbeZOffset()` (MoonrakerSession.kt:114)
`refreshProbeZOffset()` issues a **second, redundant** `objects.query{configfile}` every time the Probe-Calibrate page opens, purely to mask the dead-config symptom of the headline bug. The handshake already reads `configfile` (including `probe.z_offset`) and publishes it. **Once the re-handshake fix keeps config fresh after SAVE_CONFIG, this masking re-query is redundant and should be removed** (D-06 explicitly anticipates this). Removing it is the cadence audit's one applied fix; it is *causally coupled* to the Thread-1 fix — plan it as a follow-on task, not a parallel one (remove it only after the re-handshake is proven to refresh config).

### Subscribe-set justification (DeriveCapabilities V1_SUBSCRIBE_CORE)
Every object in the subscribe set maps to a live consumer (see DeriveCapabilities.kt:50–68). The set is already intersected with `objects.list` (A3 — never subscribe to an undefined object) and re-derived each reconnect. The audit's documentation job is to record this mapping; no object is unjustified. Two to *note* in the contract for phases 10-12: `quad_gantry_level` (gated off on both test printers — kept for forward compat) and the calibration objects (only push diffs during calibration — cheap when idle).

### Recommended cadence-contract doc
- **Location:** `docs/request-cadence-contract.md` (sibling to `docs/moonraker-capabilities.md`; both are wire-truth guardrails).
- **Format:** the inventory table above + the subscribe-set justification + a top "RULES FOR FUTURE PHASES" block: (1) no `objects.subscribe` outside the central handshake; (2) no per-screen polling — derive from the central subscribe; (3) one-shot reads go through the SpineHandle off the throttled hot path, edge-driven (filename change, idle transition), never timer-driven; (4) high-rate numeric data is conflated to `DEFAULT_SAMPLE_MS` (250ms) — never surface a faster cadence to the UI.

## Reliability Hardening (Thread 3 — D-07)

### Current reconnect behavior (full socket death — works)
The supervisor `run()` loop (MoonrakerSession.kt:129–177) handles full socket death correctly: `Closed`/`onFailure` → `markStale(Disconnected)` → backoff (uncapped, jittered) → reconnect → fresh `runHandshake()` → seed overwrites stale → `Connected`. Network-drop-mid-print is covered by *this* path: on reconnect, `runHandshake()` re-queries + re-subscribes, and `store.seed()` re-seeds `print_stats` from the live snapshot — so the running print re-registers. **The gap is NOT the socket-death path; it is the same-socket klippy-restart path (Thread 1).**

### The resync gap and the principled fix
- **D-03 silence is a real defect:** the in-session re-handshake emits no `ConnectionState`. Fix: emit `Syncing` at re-handshake start, `Connected` when the reseed lands — reusing the *exact* `emit()` + `markStale`/`seed` plumbing the socket-death path already uses. This makes klippy-restart recovery visually identical to a reconnect (honest, unambiguous).
- **Drive recovery off disconnect, not just ready:** route `notify_klippy_disconnected`/`shutdown` to `markStale(Syncing-or-Disconnected)` (they currently only fold into `KlippyState`), so the screen dims the instant klippy drops — matching the existing stale model rather than freezing on stale values until `ready`.
- **Self-heal on re-handshake failure (the D-01 fallback, reached from root cause):** replace the silent `runCatching { runHandshake() }` with a recovery that, on failure (or on a configurable number of failed re-subscribes), **forces a full socket reconnect** via the existing supervisor (close the socket → the `run()` loop reconnects, which demonstrably works). This converts the dead-forever failure into a self-healing one and reuses proven code rather than inventing a parallel path.

### Network-drop-mid-print print-state resync (D-07, rides along)
Already largely handled by the socket-death path above. The plan should add it to the live UAT gate (D-09 scenario b) and to the hardened fixture regression (inject a socket `Closed` mid-print → reconnect → assert printState resyncs to Printing). No new mechanism needed — verify and lock it.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Re-subscribe after klippy restart | A bespoke partial re-subscribe | Re-run the existing `runHandshake()` (already does identify→list→derive→query→subscribe + one-shots) | One code path = one place for bugs; Moonraker requires a full re-subscribe anyway |
| Recovery when re-handshake fails | A new retry loop with its own backoff | Force a socket close → let the existing `run()` supervisor reconnect | The supervisor's backoff/jitter/seed path is proven on socket death; reuse it |
| Connection-state surfacing | A new "syncing" boolean | The existing `ConnectionState.Syncing` + `markStale`/`seed` | D-03 wants the *same* surface as a reconnect; it already exists |
| A "fast poll while syncing" workaround | Any timer-based re-query | The central subscribe + edge-driven one-shots | Violates the no-polling contract this very phase is documenting |

**Key insight:** every fix in this phase should be a *reuse* of an existing, proven path (the socket-death reconnect, the `emit`/`markStale`/`seed` plumbing, `runHandshake()`). Inventing new recovery machinery is how the silent re-handshake became a dead-forever trap in the first place.

## Common Pitfalls

### Pitfall 1: Trusting the green KlippyReadyResyncTest
**What goes wrong:** the fix "passes" but reality still fails. **Why:** the mock auto-replies instantly and unconditionally. **How to avoid:** D-10 — harden the mock to the captured contract (klippy-down window, withheld/rejected replies) and assert on *resumed diffs*, not sent frames, BEFORE touching the fix.

### Pitfall 2: Defaulting to force-reconnect without the capture
**What goes wrong:** you skip the root cause, force-reconnect on every klippy_ready, and ship a fix that papers over a still-misunderstood failure (and may mask a worse bug like H2/H3). **Why:** the fallback "obviously works." **How to avoid:** D-01 — capture first, understand the sequence, *then* choose. Force-reconnect is the fallback if the in-session path can't be made reliable, not the default.

### Pitfall 3: Removing `refreshProbeZOffset` before the re-handshake fix lands
**What goes wrong:** Probe-Calibrate shows stale z_offset after SAVE_CONFIG. **Why:** the re-query is currently the *only* thing masking the dead config. **How to avoid:** sequence it — remove the masking re-query only after (and in the same phase as) the re-handshake proves it keeps config fresh on-device.

### Pitfall 4: Silent best-effort recovery
**What goes wrong:** `runCatching{}` swallows the re-handshake failure; the user sees a frozen screen with no indication and no recovery. **Why:** best-effort was the right call for one-shot config reads, wrong for the load-bearing subscription. **How to avoid:** the subscription re-register must be *self-healing* (escalate to full reconnect on failure) and *visible* (emit Syncing), not best-effort-silent.

### Pitfall 5: notify_klippy_shutdown vs disconnected vs webhooks.state
**What goes wrong:** keying recovery on only one signal misses the real transition on one printer. **Why:** the capture may show the signal arrives via `webhooks.state` in a `notify_status_update` rather than a bare `notify_klippy_*`. **How to avoid:** the capture must enumerate ALL klippy-state signals on both printers; the reducer already maps `webhooks.state` → KlippyState (PrinterStateReducer.kt:69–82) — recovery may need to key off that too.

## Code Examples

### The silent re-handshake to make visible + self-healing (current — MoonrakerSession.kt:238–246)
```kotlin
// CURRENT (silent, best-effort, dead-forever on failure):
if (method == JsonRpcMethods.NOTIFY_KLIPPY_READY && handshakeComplete) {
    attemptScope.launch {
        rehandshakeMutex.withLock { runCatching { runHandshake() } }
    }
}
// DIRECTION (emit Syncing; on failure escalate to full reconnect — reuse existing paths):
//   emit(Syncing) → runHandshake() → emit(Connected) on success
//   on failure → close the socket so run()'s supervisor reconnects (the proven path)
// Exact shape decided by the D-10 capture.
```

### The proven socket-death seed path to reuse (MoonrakerSession.kt runHandshake step 5-6)
```kotlin
// objects.query → SEED overwrites stale; objects.subscribe reply → SEED from authoritative snapshot.
val queryResult = rpc.request(CommandRegistry.objectsQuery, ObjectSubsetArgs(subset))
parseStatus(queryResult)?.let { store.seed(reduceSnapshot(it)) }
val subResult = rpc.request(CommandRegistry.objectsSubscribe, ObjectSubsetArgs(subset))
parseStatus(subResult)?.let { store.seed(reduceSnapshot(it)) }   // closes the query→subscribe gap
```

## Runtime State Inventory

Not a rename/migration phase, but the recovery-state question is analogous. The capture must inventory what the *server* does to subscription state across a restart:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Server-side subscription registry | Moonraker `self.subscriptions` + `self.subscription_cache` — **cleared on klippy disconnect; NOT auto-restored** [VERIFIED: source] | Client must re-subscribe — the fix's whole job |
| Client-side connection state | `handshakeComplete` flag, `rehandshakeMutex` (per-attempt) | Verify they're correctly armed/reset across re-handshake (H2 check) |
| Stored data | None — no DB; settings in DataStore unaffected | None |
| OS-registered state | FGS holds the session scope; survives the restart | None — confirmed the session self-heals on the same FGS-held socket (05-10 summary) |
| Build artifacts | None | None |

## Common Pitfalls cross-check with the Phase-6 command/error reference
The `ConnectionError.Timeout` vs `NetworkUnavailable` distinction (G4 fix) is already in place — a slow `objects.subscribe` reply during a restart will surface as `Timeout`, not a transport failure. The recovery logic must treat a re-subscribe `Timeout`/`RpcError` during the klippy-down window as "retry/escalate," not "give up."

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Assume subscriptions survive klippy restart | Confirmed: Moonraker clears them, client must re-subscribe | Verified this session (source) | Validates the re-handshake strategy; bug is execution not design |
| Silent best-effort re-handshake (05-10) | Visible, self-healing recovery (this phase) | Phase 13 | Closes the dead-forever trap |
| Mock auto-replies instantly | Mock replays a captured klippy-down window | D-10, this phase | Kills the mock-that-lied class |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | On SAVE_CONFIG, Moonraker emits `notify_klippy_disconnected`/`shutdown` *before* `notify_klippy_ready` on the same socket | Root-Cause H1 | If `ready` never arrives on the open socket, the fix MUST be disconnect-driven recovery / force-reconnect; the capture (D-10) settles this — do not code before capturing |
| A2 | The re-handshake's `objects.subscribe` is being sent but dropped/rejected/timed-out in the restart window (vs never sent) | Root-Cause H1 | If never sent (klippy_ready not re-delivered), it's a routing problem not a timing one; capture distinguishes |
| A3 | Force-full-reconnect is a reliable fallback (it's the path that works on app restart) | Reliability fix | LOW risk — directly evidenced by the repro (force-close fixes it); but verify it survives an in-session trigger (no FGS teardown) |
| A4 | Removing `refreshProbeZOffset` is safe once the re-handshake keeps config fresh | Cadence audit | Probe-Calibrate could show stale z_offset; mitigated by sequencing (remove only after on-device proof) |

## Open Questions

1. **Does `notify_klippy_ready` actually arrive on the already-open socket on each printer?**
   - Known: Moonraker clears subs and requires re-subscribe; docs say watch for `notify_klippy_disconnected`.
   - Unclear: whether `ready` is reliably re-pushed to a long-lived socket, or whether clients are expected to poll `server.info`.
   - Recommendation: the D-10 capture answers this definitively on BOTH printers before the fix is chosen.
2. **Is the failure printer-dependent (E3 vs E5)?**
   - Known: repro is on the E3; E5 unproven since Phase 5 (D-02).
   - Recommendation: capture on BOTH; the fix and the UAT gate both span both (D-09).
3. **Does a post-restart `objects.query` ever return a transient/incomplete `print_stats` that the seed plants and diffs never correct (H3)?**
   - Recommendation: the capture should include the post-ready `objects.query` reply content, not just frame counts.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Live Moonraker E5 (Pi 4) | D-09 UAT scenario a+b, D-10 capture | ✓ | 192.168.1.120:7125, Klipper v0.13, Moonraker v0.10 / API 1.5.0 | none — required for the gate |
| Live Moonraker E3 (RockPro64) | D-09 UAT, D-10 capture | ✓ | 192.168.1.121:7125, same versions | none — required (the repro printer) |
| Physical flox (Nexus 7 2013, LineageOS 18.1 / API 30) | on-device UAT gate | ✓ | adb via `E:\Android\Sdk\platform-tools` | none — perf/behavior floor |
| Windows-side Gradle (`E:\Android\gw.bat`) | build/test (./gradlew won't run from WSL) | ✓ | JDK 21, AGP 8.7.x | none |
| websocket capture tool (websocat / python `websockets`) | D-10 wire capture | needs install | — | `curl` for REST-mirror probes can't capture push notifications; a ws client is required for the capture task |

**Missing dependencies with no fallback:** A scriptable websocket client for the D-10 capture is not confirmed installed — the capture task should `pip install websockets` (WSL-native python) or install `websocat`. This is the one environment gap; verify/install in the capture task.

## Validation Architecture

> nyquist_validation = true (config.json) — section REQUIRED.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 + kotlinx-coroutines-test (`runTest`, `UnconfinedTestDispatcher`, virtual time) |
| Config file | `app/build.gradle` test deps; no separate config |
| Quick run command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests KlippyReadyResyncTest --no-daemon"` |
| Full suite command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon"` |

> Pipe through `tr -d '\r'` (Gradle CR progress bars); the process exit code is authoritative.

### Phase Requirements → Test Map
| Req | Behavior | Test Type | Automated Command | File Exists? |
|-----|----------|-----------|-------------------|-------------|
| SC-1 | `refreshProbeZOffset` redundant query removed; no per-screen polling reintroduced | unit + doc grep | `... --tests *Session* ` + grep for `objects.subscribe`/timer polls | ⚠ remove-test new (Wave 0) |
| SC-2/SC-4 (D-10) | klippy-down window → re-subscribe → **diffs resume** (printState→Printing reaches store) | unit (hardened mock) | `... --tests KlippyReadyResyncTest` | ❌ MUST HARDEN — current asserts sent-frames only |
| SC-2 (D-03) | re-handshake emits `Syncing` then `Connected` | unit | `... --tests *Session*`/new `KlippyRecoveryStateTest` | ❌ Wave 0 |
| SC-2/D-07 | mid-print socket `Closed` → reconnect → printState resyncs | unit | `... --tests ReconnectSupervisorTest` (extend) | ⚠ extend existing |
| SC-2 | re-handshake FAILURE escalates to full reconnect (self-heal) | unit (mock rejects subscribe in down-window) | new test against hardened mock | ❌ Wave 0 |
| SC-3 (D-09a) | SAVE_CONFIG → start print → feed live + printState→Printing, no app restart | **manual on-device, BOTH printers** | live UAT (Matthew + flox) | manual-only — justified (real klippy restart) |
| SC-3 (D-09b) | mid-print network drop → reconnect → print resyncs | **manual on-device, BOTH printers** | live UAT | manual-only — justified |

### Sampling Rate
- **Per task commit:** `:app:testReleaseUnitTest --tests KlippyReadyResyncTest` (the hardened regression — the keystone).
- **Per wave merge:** full `:app:testReleaseUnitTest`.
- **Phase gate:** full unit suite green + the **D-09 dual-printer dual-scenario on-device UAT** (both E5 and E3, both scenarios) — this is the binding gate, consistent with the project's "live gates catch what green units miss" lesson.

### Wave 0 Gaps
- [ ] **Capture-first task** (D-10): live websocket capture of the SAVE_CONFIG sequence on E5 AND E3; commit `.jsonl` fixtures. **This blocks all fix tasks.**
- [ ] Harden `FakeWebSocket`/`SessionTestHarness` with a klippy-down window mode (reject/withhold replies; replay captured notification sequence).
- [ ] Rewrite `KlippyReadyResyncTest` to assert **resumed diffs** (inject post-restart `notify_status_update` printState→Printing, assert it reaches `store`), not sent-frame counts.
- [ ] New `KlippyRecoveryStateTest` — asserts `Syncing`→`Connected` emission on re-handshake (D-03).
- [ ] New self-heal test — re-handshake failure in down-window escalates to full reconnect.
- [ ] Extend `ReconnectSupervisorTest` for mid-print socket-death resync (D-07b).
- [ ] Install a websocket capture tool (`pip install websockets` WSL-native, or `websocat`).

## Sources

### Primary (HIGH confidence)
- `github.com/Arksine/moonraker` `moonraker/components/klippy_connection.py` (raw source) — `self.subscriptions = {}` + `self.subscription_cache.clear()` on disconnect; no auto-re-subscribe; client must re-send — **the load-bearing root-cause fact**.
- Codebase (read this session): `MoonrakerSession.kt`, `JsonRpcClient.kt`, `PrinterStateStore.kt`, `PrinterStateReducer.kt`, `DeriveCapabilities.kt`, `CommandRegistry.kt`, `SessionTestHarness.kt`, `FakeWebSocket.kt`, `KlippyReadyResyncTest.kt`, `MoonrakerService.kt`, `CommandDispatcher.kt`, UI `delay(4_000)` grep — direct verification of behavior, cadence, and the mock gap.
- `docs/moonraker-capabilities.md` — live-captured printer versions/objects (E5/E3 both Klipper v0.13, Moonraker v0.10, API 1.5.0).

### Secondary (MEDIUM confidence)
- `moonraker.readthedocs.io/external_api/introduction` — recovery sequence: watch `notify_klippy_disconnected`, re-determine ready, re-subscribe.
- `moonraker.readthedocs.io/external_api/printer` — "A new subscribe request overrides a previous request; empty objects cancels."
- `moonraker.readthedocs.io/external_api/jsonrpc_notifications` — definitions of the three klippy-state notifications + `notify_status_update` format.

### Tertiary (LOW confidence — needs the live capture to confirm)
- Exact notification ordering/timing on SAVE_CONFIG is NOT documented — flagged A1/A2/Open-Q1, settled only by the D-10 capture.

## Metadata

**Confidence breakdown:**
- Root-cause direction: HIGH — Moonraker source confirms the subscription-clear contract; code analysis pins the defect to the silent in-session re-handshake; the mock gap is structurally demonstrable.
- Exact failing edge: MEDIUM — the precise notification sequence on each printer is undocumented; D-10 capture resolves it before the fix (by design — capture-first).
- Cadence audit: HIGH — full call inventory read from code; "no polling" verified; one real fix (`refreshProbeZOffset`) identified.
- Reliability hardening: HIGH — socket-death path already correct; gaps (silence, disconnect-driven recovery, self-heal) are well-scoped reuses of existing plumbing.

**Research date:** 2026-06-03
**Valid until:** 2026-07-03 (stable — Moonraker v0.10/API 1.5.0 pinned on both printers; re-verify if either printer's Moonraker is updated)
