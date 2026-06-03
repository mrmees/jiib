---
phase: 13-optimization-network-efficiency-end-to-end-reliability
plan: 01
subsystem: testing
tags: [moonraker, websocket, klipper, save-config, firmware-restart, reconnect, test-harness, fakewebsocket, d-10]

# Dependency graph
requires:
  - phase: 05-temp-move-extrude
    provides: notify_klippy_ready re-handshake path (runHandshake re-run) + configfile one-shot read
  - phase: 09-calibration
    provides: KlippyReadyResyncTest (the mock-that-lied keystone) + SessionTestHarness auto-reply fake
provides:
  - Live SAVE_CONFIG / FIRMWARE_RESTART wire-truth fixtures captured on BOTH printers (E5 + E3), JSON-valid
  - A capture script (tools/ws-capture.py) replaying the app's exact 5-step handshake pre- and post-restart
  - A subscription-gated FakeWebSocket (inject() drops resumed status diffs while unsubscribed) + cancel()→onFailure
  - A klippy-down window in SessionTestHarness owning subscriptionActive (armed only on a real subscribe success)
  - A rewritten resumed-diff KlippyReadyResyncTest keystone (no sent-frame counts) + RED self-heal escalation test
  - A RED KlippyRecoveryStateTest (D-03 Syncing→Connected on a collector reset past the initial connect)
  - A GREEN ProbeZOffsetFreshnessTest — the executable gate plan 13-03's refreshProbeZOffset removal depends on
  - An extended ReconnectSupervisorTest (D-07b mid-print socket-death resync)
affects: [13-02, 13-03, 13-04]

# Tech tracking
tech-stack:
  added: [websockets (WSL python, capture-only — never bundled)]
  patterns:
    - "Capture-first (D-10): no re-handshake fix code before the real wire sequence is captured live on both printers"
    - "Subscription-gated frame delivery in the fake (inject() refuses resumed status diffs while unsubscribed)"
    - "cancel() fires onFailure so RpcConnection.close(cause)→cancel() drives SocketEvent.Closed in tests"
    - "Resumed-diff assertions over sent-frame counts (assert the store, not the wire)"

key-files:
  created:
    - app/src/test/java/works/mees/dinghy/net/KlippyRecoveryStateTest.kt
    - app/src/test/java/works/mees/dinghy/net/ProbeZOffsetFreshnessTest.kt
    - tools/ws-capture.py
    - docs/commands/e5-saveconfig-capture.jsonl
    - docs/commands/e3-saveconfig-capture.jsonl
  modified:
    - app/src/test/java/works/mees/dinghy/net/FakeWebSocket.kt
    - app/src/test/java/works/mees/dinghy/net/SessionTestHarness.kt
    - app/src/test/java/works/mees/dinghy/net/KlippyReadyResyncTest.kt
    - app/src/test/java/works/mees/dinghy/net/ReconnectSupervisorTest.kt

key-decisions:
  - "Wire truth is identical on E5 and E3 — the freeze is an APP-side handling defect, NOT printer-dependent (D-02)"
  - "Recovery signal = notify_klippy_disconnected → notify_klippy_ready on the SAME socket; NO shutdown, NO webhooks.state (Pitfall-5 ruled OUT for this path)"
  - "Post-restart re-identify on the same socket ERRORS 400 'Connection already identified'; the other four steps succeed and diffs resume — so a re-handshake that re-sends identify is harmless and objects.subscribe is what flips the subscription live again"
  - "Keystone resumed-diff + probe-freshness + D-07b are GREEN against current production (the happy-path re-handshake already works); the genuine RED fix-drivers are the D-03 Syncing emission and the self-heal escalation when the re-subscribe FAILS"

patterns-established:
  - "subscriptionActive gate inside FakeWebSocket.inject() — a resumed status diff cannot reach the store without a real re-subscribe"
  - "Harness klippy-down window returns the real 503 'Klippy Not Connected' for subscribe/query mid-restart (no more-lenient-than-the-server fake)"

requirements-completed: []

# Metrics
duration: ~45min
completed: 2026-06-03
---

# Phase 13 Plan 01: Capture-First + Harden the Mock-That-Lied Summary

**Live SAVE_CONFIG/FIRMWARE_RESTART wire captured on both printers (E5 + E3, identical — the freeze is app-side, not printer-dependent), the FakeWebSocket bypass closed with a subscription gate inside inject() + a cancel()→onFailure lever, and the keystone rewritten to assert resumed diffs through a real re-subscribe — plus the RED D-03/self-heal fix-drivers and the GREEN probe-freshness gate 13-03 removes refreshProbeZOffset against.**

## Performance

- **Duration:** ~45 min (Tasks 2–3; Task 1 was a prior checkpoint)
- **Completed:** 2026-06-03
- **Tasks:** 3 (Task 1 capture was a completed human-action checkpoint; Tasks 2–3 executed here)
- **Files modified:** 4 test files modified, 2 test files created (+ Task-1 capture artifacts already committed)

## Wire-Capture Findings (Task 1 — the evidence Tasks 2/3 model)

Captured via `tools/ws-capture.py` firing FIRMWARE_RESTART while replaying the app's exact five-step
handshake pre- and post-restart on the same socket. **Identical on BOTH printers (E5 192.168.1.120 / E3
192.168.1.121):**

| Question (RESEARCH § "What to capture") | E5 (Pi 4) | E3 (RockPro64, repro printer) |
|---|---|---|
| Klippy-state signal ordering | `notify_klippy_disconnected` → [gap] → `notify_klippy_ready`, **same socket** | identical |
| `notify_klippy_shutdown`? | absent | absent |
| `webhooks.state` transition (Pitfall 5)? | **none** in any notify_status_update | identical (Pitfall-5 ruled OUT for this path) |
| Post-restart `identify` reply | **ERROR 400 "Connection already identified"** | identical |
| Post-restart `server.info` reply | `result` (OK) | identical |
| Post-restart `objects.list` reply | `result` (OK) | identical |
| Post-restart `objects.query` reply | `result` (OK) | identical |
| Post-restart `objects.subscribe` reply | `result` (OK) | identical |
| Diffs resume after re-subscribe? | **yes** — 118 `notify_status_update` frames after the post-restart subscribe | identical |
| Freeze reproduced (D-02)? | The wire is identical to the repro E3 — so the freeze is an **APP-side handling defect, not a wire difference**. The E5 wire does NOT differ from the E3. |

**Implication the harness now models:** the down-window is `notify_klippy_disconnected … gap … notify_klippy_ready`
on the same socket; a re-handshake that re-sends `identify` gets a harmless 400 but the subsequent
`objects.subscribe` still SUCCEEDS and diffs resume. The `objects.subscribe`-success branch is what flips
`subscriptionActive` back true.

## Accomplishments

- **Closed the inject() bypass (D-10):** `FakeWebSocket.inject()` now DROPS a `notify_status_update` while
  `subscriptionActive == false`. A resumed diff can no longer reach the store without a genuine re-subscribe —
  the exact mock-lie that let a "resumed diffs" test pass with the fix absent.
- **Made the self-heal reconnect observable:** `FakeWebSocket.cancel()` now fires
  `listener.onFailure(this, IOException, null)` once (mirroring real OkHttp), so
  `RpcConnection.close(cause != null) → webSocket.cancel() → onFailure → SocketEvent.Closed` is reachable in
  tests and the supervisor's reconnect actually unblocks.
- **Klippy-down window in the harness:** while `klippyDown`, `objects.subscribe`/`objects.query` return the
  real 503 "Klippy Not Connected" (no more-lenient-than-the-server fake); `subscriptionActive` is armed TRUE
  only on a SUCCESSFUL subscribe reply and cleared on the injected captured drop signal.
- **Keystone rewritten:** `KlippyReadyResyncTest` deleted all sent-frame-count assertions and now drives the
  captured drop → ready window and asserts a post-restart `print_stats.state=printing` diff reaches the store
  ONLY via a real re-subscribe (through the gated `inject()`).
- **Fix-driver + lock tests landed:** RED `KlippyRecoveryStateTest` (D-03 Syncing→Connected on a fresh
  collector reset past the initial connect), RED self-heal escalation test (opens++ AND a second full
  handshake on a fresh socket), GREEN `ProbeZOffsetFreshnessTest`, and the GREEN D-07b mid-print resync lock.

## Task Commits

1. **Task 1: Live wire-capture (E5 + E3)** — `4be675b` (script) + `d3eda89` (captures) — completed checkpoint
2. **Task 2: Harden FakeWebSocket + harness + rewrite keystone** — `87d7a43` (test)
3. **Task 3: D-03 recovery-state + probe-freshness gate + D-07b resync** — `c220371` (test)

## Test Wave-0 Color (load-bearing — what Wave 1 / 13-02 turns green, what gates 13-03)

| Test | Color @ Wave 0 | Meaning |
|---|---|---|
| `KlippyReadyResyncTest.klippyRestart_resumesLiveDiffs_onlyViaRealReSubscribe` | **GREEN** | The happy-path re-handshake already re-subscribes; the resumed diff lands through the hardened gate. Regression LOCK. |
| `KlippyReadyResyncTest.klippyReadyRefreshesStaleConfig_changedMinExtrudeTempPropagates` | **GREEN** | Re-handshake already re-reads configfile; a changed min_extrude_temp propagates. Regression LOCK. |
| `KlippyReadyResyncTest.klippyDownReSubscribeRejected_escalatesToFullReconnect_reEntersConnectAndServe` | **RED** | Self-heal fix-driver. Current `runCatching { runHandshake() }` SWALLOWS a failed re-subscribe (no socket close, no reconnect). Wave 1 / 13-02 makes it escalate. |
| `KlippyRecoveryStateTest.klippyRestart_emitsSyncingThenConnected_…` | **RED** | D-03 fix-driver. The silent re-handshake emits NEITHER Syncing nor Connected. Wave 1 / 13-02 wraps it with emit(Syncing)→…→emit(Connected). |
| **`ProbeZOffsetFreshnessTest.reHandshakeAfterRestart_refreshesProbeZOffsetToNewSavedValue`** | **GREEN** | **The 13-03 gate.** Current production already re-reads configfile on the re-handshake, so a changed `probe.z_offset` propagates WITHOUT `refreshProbeZOffset()`. Plan 13-03's removal of that method GATES on THIS test staying GREEN — not on a SUMMARY sentence. |
| `ReconnectSupervisorTest.midPrintSocketDeath_reconnects_andResyncsPrintStateToPrinting` | **GREEN** | D-07b regression lock — the socket-death → reconnect → resync path is already correct. |

**Net:** 2 RED fix-drivers (D-03 + self-heal escalation), the rest GREEN locks/gates. All COMPILE and RUN.

## The down-window failure mode the harness now models

- The real Moonraker contract: `notify_status_update` diffs push ONLY while a subscription is registered.
  After a FIRMWARE_RESTART the subscription is gone and diffs do NOT resume until `objects.subscribe` is
  re-sent AND the server (now klippy-ready) accepts it.
- Modeled by: `subscriptionActive` on `FakeWebSocket`, gated inside `inject()` (resumed status diffs dropped
  while false). Cleared on the injected captured `notify_klippy_disconnected`; set true only when the harness
  PRODUCES a successful `objects.subscribe` reply (the success branch — not the down-window 503 branch).
- The self-heal lever: `cancel()` fires `onFailure`, so a re-handshake that fails its re-subscribe in the
  down-window can drive `close(cause) → cancel() → SocketEvent.Closed` and a genuine `connectAndServe`
  re-entry — proven end-to-end by the self-heal test asserting a second full handshake on a fresh socket.

## Decisions Made

- **Kept the resumed-diff keystone GREEN where production already works.** The plan allowed the keystone to be
  RED at Wave 0, but production's existing `notify_klippy_ready` handler already re-runs `runHandshake()` on
  the happy path, so the captured drop→ready window genuinely re-subscribes and the gated diff lands. Forcing
  it artificially RED would have been dishonest. The GENUINE bugs (D-03 silent emission + self-heal escalation
  on a FAILED re-subscribe) are captured by the two RED fix-drivers. This faithfully reflects the wire truth.
- **`ProbeZOffsetFreshnessTest` is GREEN at Wave 0.** Production already re-reads configfile on the
  re-handshake, so the probe-freshness premise 13-03 relies on is already true. 13-03 gates its
  `refreshProbeZOffset` removal on this test staying GREEN.
- **Folded the self-heal escalation test into `KlippyReadyResyncTest`** (the plan permitted "in
  KlippyReadyResyncTest or a new file") to keep the klippy-down-window machinery co-located with the keystone.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug-in-test-design] Removed the superseded `klippyReadyAfterSaveConfig_reSubscribesCalibrationObjects` test**
- **Found during:** Task 2 (rewriting the keystone)
- **Issue:** That test asserted `count(after, OBJECTS_SUBSCRIBE) > subscribeBefore` — a sent-frame-count
  assertion the plan explicitly directed to DELETE — alongside a payload-content check. Its payload guarantee
  is now subsumed by the resumed-diff keystone (a diff only lands if the re-subscribe set is genuinely live).
- **Fix:** Removed it; replaced the file's assertions with the resumed-diff keystone + the self-heal escalation
  test. The calibration-object re-subscribe is still exercised by the GREEN `09-07` payload guard elsewhere.
- **Files modified:** `app/src/test/java/works/mees/dinghy/net/KlippyReadyResyncTest.kt`
- **Verification:** `:app:testReleaseUnitTest --tests KlippyReadyResyncTest` compiles and runs (2 GREEN, 1 RED self-heal as designed).
- **Committed in:** `87d7a43` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 test-design correction aligning with the plan's explicit "delete sent-frame-count assertions" directive)
**Impact on plan:** No scope creep — the removed assertion was the exact lie the plan targeted. The keystone is strictly stronger (asserts the store, not the wire).

## Issues Encountered

None. Test sources compiled clean on the first `:app:compileReleaseUnitTestKotlin`; all tests ran with the
expected Wave-0 colors (2 RED fix-drivers, the rest GREEN).

## User Setup Required

None — no external service configuration required. (Task 1's `pip install websockets` was a WSL-only,
capture-only dependency, never bundled into the APK — threat T-13-01 mitigated.)

## Next Phase Readiness

- **Wave 1 is UNBLOCKED.** The fix has its evidence (the captures) and its regression targets:
  - D-03 → `KlippyRecoveryStateTest` (RED): wrap the re-handshake with `emit(Syncing)` → `runHandshake()` → `emit(Connected)`.
  - Self-heal → `KlippyReadyResyncTest.klippyDownReSubscribeRejected_…` (RED): on a re-handshake failure, force a socket close so the supervisor reconnects instead of swallowing forever.
- **Plan 13-03's `refreshProbeZOffset` removal gate is executable and GREEN** (`ProbeZOffsetFreshnessTest`) — 13-03 removes the method only while this test stays GREEN.
- No blockers.

## Self-Check: PASSED

- All created/modified files exist on disk (6 test files + 2 capture fixtures + capture script + SUMMARY).
- Task commits verified in git log: `87d7a43` (Task 2), `c220371` (Task 3); Task-1 captures `4be675b` + `d3eda89`.

---
*Phase: 13-optimization-network-efficiency-end-to-end-reliability*
*Completed: 2026-06-03*
