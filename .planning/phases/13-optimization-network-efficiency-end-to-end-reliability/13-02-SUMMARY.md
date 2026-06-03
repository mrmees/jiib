---
phase: 13-optimization-network-efficiency-end-to-end-reliability
plan: 02
subsystem: net
tags: [moonraker, websocket, klipper, save-config, firmware-restart, re-handshake, self-heal, recovery, d-03, d-10]

# Dependency graph
requires:
  - phase: 13-optimization-network-efficiency-end-to-end-reliability
    plan: 01
    provides: live SAVE_CONFIG wire-truth (E5+E3 identical) + subscription-gated FakeWebSocket + klippy-down harness window + RED D-03/self-heal fix-drivers
  - phase: 05-temp-move-extrude
    provides: notify_klippy_ready re-handshake path (runHandshake re-run) + configfile one-shot read
provides:
  - Visible (Syncing→Connected, D-03), disconnect-driven (markStale + bounded watchdog off notify_klippy_disconnected, SC-2), self-healing (RpcConnection.close → run() reconnect on failure, Pitfall 4) klippy-restart recovery
  - A PER-DROP recovery watchdog (AtomicReference<CompletableDeferred>) correct for repeated same-socket SAVE_CONFIGs (the Codex-found dead-forever bug, fixed)
  - skipIdentify on the in-session re-handshake (same socket already identified; re-identify 400s per the capture); first-connect handshake unchanged
  - Deterministic, un-hangable KlippyRecoveryStateTest + KlippyReadyResyncTest (runCurrent/advanceTimeBy/withTimeout, clear klippyDown after observing escalation)
affects: [13-03, 13-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Per-drop recovery deferred in an AtomicReference (one-shot shared deferred is a repeated-event trap)"
    - "Idempotent self-heal: escalation calls RpcConnection.close (its own AtomicBoolean dedups), NO caller-side per-attempt latch that would block a 2nd escalation"
    - "Bounded drop-recovery watchdog via the existing select/onTimeout idiom — no new poll loop, no timer thread"
    - "Virtual-time test discipline: runCurrent between inject-drop/inject-ready (don't advance past the watchdog); clear the permanently-failing harness toggle before advancing; advanceTimeBy + withTimeout deadman instead of open-ended advanceUntilIdle"

key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt
    - app/src/test/java/works/mees/dinghy/net/KlippyReadyResyncTest.kt
    - app/src/test/java/works/mees/dinghy/net/KlippyRecoveryStateTest.kt

key-decisions:
  - "Drop signal = notify_klippy_disconnected (branch (a), per the 13-01 capture: NO shutdown, NO webhooks.state). markStale + arm the watchdog directly off the bare method name in the klippy collector — no store/reducer klippyState edge exposed (branch (b) NOT needed)."
  - "Recovery mechanism = same-socket ready→re-handshake (the capture proves notify_klippy_ready reliably returns on the same socket) PLUS a 30s bounded escalation watchdog as the safety net."
  - "Per-DROP watchdog deferred (AtomicReference), not a single shared one-shot deferred — fixes the Codex-found dead-forever bug for repeated SAVE_CONFIGs in one session."
  - "Self-heal escalation closes the REAL RpcConnection (→ OkHttp cancel → SocketEvent.Closed → closed.await() unblocks → run() reconnects), NEVER rpc.close() (which would park closed.await() forever)."
  - "No caller-side escalation latch: RpcConnection.close is already idempotent, so a 2nd drop's failed re-handshake is free to escalate (a per-attempt latch would re-introduce dead-forever)."

requirements-completed: []

# Metrics
duration: ~35min
completed: 2026-06-03
---

# Phase 13 Plan 02: Visible, Disconnect-Driven, Self-Healing Klippy-Restart Recovery Summary

**The headline reliability fix landed: the in-session klippy-restart re-handshake is now VISIBLE (Syncing→Connected, D-03), DISCONNECT-DRIVEN (markStale + bounded watchdog off `notify_klippy_disconnected`, SC-2) and SELF-HEALING (a failed re-subscribe closes the REAL socket → the proven supervisor reconnects, Pitfall 4) — plus a Codex-found dead-forever bug fixed (a one-shot shared `recovered` deferred replaced with a PER-DROP `AtomicReference<CompletableDeferred>` so a 2nd SAVE_CONFIG on the same socket can re-arm the escalate-watchdog), and the two previously-RED 13-01 fix-drivers turned GREEN with deterministic, un-hangable tests.**

## Performance

- **Duration:** ~35 min
- **Completed:** 2026-06-03
- **Tasks:** 2 (the WIP core fix from a prior interrupted executor was the base; this run applied the Codex-found production bug fix + the test-hang fix and verified/committed)
- **Files modified:** 3 (1 production: MoonrakerSession.kt; 2 test: KlippyReadyResyncTest.kt, KlippyRecoveryStateTest.kt)

## What the fix does (per the plan's must-haves)

- **D-03 (visible):** the `notify_klippy_ready` re-handshake path wraps `runHandshake(skipIdentify=true)` with `emit(ConnectionState.Syncing)` before and `emit(ConnectionState.Connected)` on success — recovery now looks identical to a reconnect (Syncing splash → fresh seed → Connected), never a silent frozen-but-"Connected" screen.
- **SC-2 (disconnect-driven, branch (a)):** `markStale(Syncing)` + `emit(Syncing)` fire the instant `notify_klippy_disconnected` arrives (the drop signal the 13-01 capture identified — NO `notify_klippy_shutdown`, NO `webhooks.state`, so branch (b) / a store klippyState edge was **not** needed). A 30s bounded watchdog (`RECOVERY_WINDOW`, the existing `select { onTimeout }` idiom — no new poll loop) escalates if no successful re-handshake lands, so a missing/late ready can never freeze forever.
- **Pitfall 4 / D-01 fallback (self-healing):** a rejected/withheld/timed-out re-subscribe in the klippy-down window closes the REAL `RpcConnection` (`liveConnection.close(reason)` → OkHttp cancel → `SocketEvent.Closed` → `closed.await()` unblocks → the `run()` supervisor reconnects on a fresh socket). It does **NOT** call `rpc.close()` (which only nulls the connection + fails pendings and would leave `connectAndServe` parked on `closed.await()` forever — the mechanical trap Codex flagged in the plan).
- **Don't-Hand-Roll:** every change reuses existing plumbing — `emit()`/`markStale`/`seed`/`runHandshake()`, the `run()` supervisor's reconnect, `RpcConnection.close()`. No new recovery machinery, no new backoff loop, no timer thread.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] Codex-found dead-forever recovery bug: one-shot shared `recovered` deferred → per-drop `AtomicReference`**
- **Found during:** applying the Codex correctness review on top of the WIP core fix (Task 1/2 production path)
- **Issue:** the WIP held a single `recovered = CompletableDeferred<Unit>()` per socket attempt. After the FIRST same-socket recovery completed it, a SECOND `notify_klippy_disconnected` on the SAME socket armed a watchdog whose `select { recovered.onAwait{} ; onTimeout(RECOVERY_WINDOW){escalate} }` returned **immediately** off the already-completed deferred — the escalate-timeout was dead. Multiple `SAVE_CONFIG`s in one session is a common user flow, so a stuck 2nd restart could never self-heal (the exact dead-forever class Phase 13 exists to kill).
- **Fix:** replaced the single deferred with a **PER-DROP** deferred held in an `AtomicReference<CompletableDeferred<Unit>>` (initialized already-completed so a stray pre-drop ready is a no-op). Each `notify_klippy_disconnected` mints a fresh `CompletableDeferred`, `getAndSet`s it into the reference, and completes the prior one harmlessly (so any still-parked earlier watchdog unblocks). That drop's watchdog awaits **its own** deferred; a successful `notify_klippy_ready` re-handshake completes the **current** deferred (read from the reference at success time, never a stale captured-at-arm reference).
- **Companion fix:** dropped the per-attempt `escalated` `AtomicBoolean` latch from the WIP. A per-attempt latch would let only the FIRST escalation on a socket fire — a 2nd drop's failed re-handshake could then NEVER escalate, re-introducing dead-forever. Double-close safety belongs in `RpcConnection.close` (its own idempotent `compareAndSet`), so `escalateReconnect` now just calls `liveConnection?.close(reason)` unconditionally — every genuine recovery-failure/timeout is free to escalate, and a repeat call is a harmless no-op, not a close storm.
- **Files modified:** `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt`
- **Commit:** `c2f2970`

**2. [Rule 3 — Blocking] Test hang: `advanceUntilIdle()` chasing the permanently-failing klippy-down harness + firing the watchdog prematurely**
- **Found during:** the prior executor was interrupted because this test hung; Codex confirmed the cause.
- **Issue:** `advanceUntilIdle()` between inject-drop and inject-ready advanced virtual time past the 30s `RECOVERY_WINDOW`, firing a spurious escalate-reconnect before the ready could land the recovery; and in the self-heal test it chased an endless down-window reconnect loop (klippy never came back up), hanging the JVM.
- **Fix:**
  - Between inject-drop and inject-ready: `runCurrent()` (drains only tasks due now, leaving the watchdog pending so the ready cancels it).
  - Self-heal test: after observing the FIRST escalation re-enter `connectAndServe`, set `harness.klippyDown = false` **before** advancing so the reconnect's handshake SUCCEEDS and the session QUIESCES; then `advanceTimeBy(60_000)` past the supervisor backoff + `runCurrent()` + a `withTimeout(5_000) { connectionState.first { Connected } }` deadman — never an open-ended `advanceUntilIdle()`.
  - `KlippyRecoveryStateTest`: same `runCurrent`/`withTimeout` discipline; no unbounded advance that could fire the watchdog.
- **Files modified:** `app/src/test/java/works/mees/dinghy/net/KlippyReadyResyncTest.kt`, `app/src/test/java/works/mees/dinghy/net/KlippyRecoveryStateTest.kt`
- **Commit:** `cceb51b`

**Total deviations:** 2 auto-fixed (1 production bug from the Codex review, 1 blocking test-hang fix). No architectural changes, no scope creep — both were correctness fixes on the headline-fix path.

## Verification (all hard-timeout-guarded; exit codes authoritative)

| Run | Filter | Result |
|---|---|---|
| Net package | `--tests works.mees.dinghy.net.*` | **exit=0 GREEN** |
| Full unit suite | `:app:testReleaseUnitTest` (no filter) | **exit=0 GREEN** |
| Release build | `:app:assembleRelease` | **exit=0 BUILD SUCCESSFUL** |

**Fix-driver test report (from `app/build/test-results/testReleaseUnitTest/`):**

| Test | tests/skipped/failures/errors | Was @ 13-01 |
|---|---|---|
| `KlippyRecoveryStateTest.klippyRestart_emitsSyncingThenConnected_…` | 1/0/0/0 GREEN | **RED** (D-03 fix-driver) → now GREEN |
| `KlippyReadyResyncTest.klippyDownReSubscribeRejected_escalatesToFullReconnect_reEntersConnectAndServe` | (in 3/0/0/0) GREEN | **RED** (self-heal fix-driver) → now GREEN |
| `KlippyReadyResyncTest.klippyRestart_resumesLiveDiffs_onlyViaRealReSubscribe` | (in 3/0/0/0) GREEN | GREEN lock (held) |
| `KlippyReadyResyncTest.klippyReadyRefreshesStaleConfig_changedMinExtrudeTempPropagates` | (in 3/0/0/0) GREEN | GREEN lock (held) |
| `ProbeZOffsetFreshnessTest.reHandshakeAfterRestart_refreshesProbeZOffsetToNewSavedValue` | 1/0/0/0 GREEN | GREEN gate (held) |

**The 13-03 unblock (Pitfall 3 sequencing):** `ProbeZOffsetFreshnessTest.reHandshakeAfterRestart_refreshesProbeZOffsetToNewSavedValue` is **GREEN** (1/0/0/0) — recovery re-runs `runHandshake`, which re-reads `configfile`, so a changed `probe.z_offset` propagates after the re-handshake without `refreshProbeZOffset()`. That GREEN test is the executable gate plan 13-03 removes the redundant `refreshProbeZOffset` against.

## Recovery does NOT re-send identify

The in-session re-handshake runs `runHandshake(skipIdentify = true)` — the same socket is already identified and a re-identify 400s "Connection already identified" per the E5/E3 capture. The first-connect handshake (fresh, unidentified socket) is unchanged (default `skipIdentify = false`, still sends identify).

## Known Stubs

None.

## Threat Flags

None — no new network endpoints, auth paths, file access, or schema changes. The recovery consumes the same frames as before and the escalation reuses the proven socket-death → reconnect path (threat register T-13-04/05/06 dispositions unchanged: the self-heal forces ONE real-socket close governed by the existing jittered backoff, not a tight reconnect loop).

## Task Commits

1. **Production fix (per-drop watchdog + visible self-healing recovery, FIX 1)** — `c2f2970`
2. **Test fix (deterministic, un-hangable recovery tests, FIX 2)** — `cceb51b`

## Self-Check: PASSED

- Both task commits verified in git log (`c2f2970`, `cceb51b`).
- All three modified files exist on disk.
- Net package, full suite, and assembleRelease all returned exit=0 under the hard-timeout guard.
- The two previously-RED fix-drivers are GREEN in the JUnit XML report; the GREEN locks/gate held.

---
*Phase: 13-optimization-network-efficiency-end-to-end-reliability*
*Completed: 2026-06-03*
