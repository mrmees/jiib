---
phase: 05-core-print-control-panels-temperature-move-extrude
plan: 10
subsystem: net
tags: [klippy-ready, re-handshake, firmware-restart, self-heal, one-shot-reads, json-rpc, moonraker, gap-closure]
gap_closure: true
requires:
  - MoonrakerSession.runHandshake (identify→list→derive→query→subscribe + 05-03 one-shot reads)
  - JsonRpcClient.klippyEvents SharedFlow (routes NOTIFY_KLIPPY_READY by bare method name)
  - PrinterStateStore.setMinExtrudeTemp / setTemperatureBackfill (05-03 one-shot setters)
provides:
  - notify_klippy_ready → full re-handshake (re-subscribe + re-run BOTH one-shot reads) on the live socket
  - FGS-held session self-heals after a Klipper FIRMWARE_RESTART without an app force-stop (G2)
  - one-shot config reads (min_extrude_temp / max_extrude_only_distance / temperature backfill) refresh on config reload (G3)
affects:
  - 05 verification re-run (closes G2 HIGH + G3 MED)
tech-stack:
  added: []
  patterns:
    - gated + serialized side-effect off a notification flow (handshakeComplete flag + rehandshakeMutex)
    - re-run launched on the attempt coroutineScope (off the frame/notification collector) so blocking rpc.request() never stalls inbound dispatch
    - best-effort re-handshake (runCatching) — a transient failure leaves prior state, next klippy_ready retries
key-files:
  created:
    - app/src/test/java/works/mees/dinghy/net/KlippyReadyResyncTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt
decisions:
  - "notify_klippy_ready drives a FULL runHandshake() (the same fn the initial connect uses), not a partial re-subscribe — so the 05-03 one-shot reads re-run and G3 is closed by the same change."
  - "Re-handshake gated by a per-attempt handshakeComplete flag set right BEFORE emit(Connected): a klippy_ready arriving as part of the initial connect sequence never duplicates the initial handshake, and one racing the Connected transition is still caught."
  - "Re-runs serialized by a rehandshakeMutex (withLock): a second klippy_ready mid-re-handshake waits then re-runs against the freshest config — no two overlapping handshakes."
  - "Re-handshake launched on the connectAndServe coroutineScope (attemptScope.launch), NOT inside the klippy collector continuation, so its blocking rpc.request() calls cannot stall klippy/frame dispatch."
  - "Re-handshake wrapped in runCatching (non-fatal, matching the existing one-shot-read posture) — a transient RpcError leaves prior state in place; the next klippy_ready or a socket-close→full-reconnect retries."
  - "MoonrakerService needs NO change — confirmed against the code: the session self-heals internally on the same FGS-held socket; the planner's determination held."
metrics:
  duration_min: 4
  completed: 2026-06-01
  tasks: 2
  files: 2
---

# Phase 5 Plan 10: notify_klippy_ready Re-Handshake (G2/G3 Gap Closure) Summary

After a Klipper `FIRMWARE_RESTART` / `printer.cfg` reload, `MoonrakerSession` now re-runs its full
handshake when `notify_klippy_ready` arrives on the still-open websocket — restoring the lost
`objects/subscribe` registration (so temps/positions resume) and re-running both 05-03 one-shot reads
(so edited config values refresh) — letting the persistent-FGS-held session self-heal without an app
force-stop. Closes G2 (HIGH) and G3 (MED), which share a root cause.

## What Was Built

**Task 2 (written first, TDD RED) — `KlippyReadyResyncTest.kt` (`36f4324`):**
- Drives the session to Connected, captures baseline counts of `objects.subscribe`,
  `server.temperature_store`, and the configfile-only `objects.query`, then injects a GENUINE no-id
  `notify_klippy_ready` frame into the live fake socket (routed through the real
  `JsonRpcClient.dispatch` → `klippyEvents` path — faithful-mock discipline, the project's
  mock-vs-reality lesson).
- Test 1 asserts each of the three frame counts strictly increased after injection (the FULL handshake
  re-ran, not just a re-subscribe).
- Test 2 (G3) sets `harness.configfileResultJson` to `min_extrude_temp = 220.0` before injecting, then
  asserts `store.minExtrudeTemp.value == 220.0f` after the re-handshake — proving the stale one-shot
  read is refreshed.
- Confirmed RED against the pre-fix session (sanity asserts of "1 each" passed; the re-handshake asserts
  failed — the right reason).

**Task 1 (GREEN) — `MoonrakerSession` re-handshake path (`272d7a3`):**
- In `connectAndServe`, the `klippyEvents` collector now, in addition to the unchanged
  `store.onKlippyMethod(method)`, fires a re-handshake when `method == NOTIFY_KLIPPY_READY && handshakeComplete`.
- The re-handshake is `attemptScope.launch { rehandshakeMutex.withLock { runCatching { runHandshake() } } }`:
  launched on the `connectAndServe` coroutineScope (off the collector continuation so blocking
  `rpc.request()` cannot stall dispatch), serialized by a `Mutex` (no overlap), and non-fatal.
- New per-attempt state: `@Volatile handshakeComplete` (reset `false` at the top of each attempt; set
  `true` right before `emit(Connected)` so the initial connect-sequence klippy_ready never duplicates the
  initial handshake) and `rehandshakeMutex`.
- The line-173 comment block was rewritten to document that `notify_klippy_ready` now drives a
  re-handshake (G2/G3), not merely a KlippyState routing note. `shutdown`/`disconnected` routing is
  UNCHANGED.

## Why a full runHandshake(), and why MoonrakerService is untouched

`runHandshake()` is the single function that does identify → list → derive → query → subscribe **and**
the two 05-03 one-shot reads. Re-running it whole is what closes G3 for free: re-subscribing alone would
leave `min_extrude_temp` stale. The planner's determination that `MoonrakerService` needs no change was
verified against the code — the re-handshake happens INSIDE the session on the same FGS-held socket, so
the service (which only builds + launches the spine) requires nothing.

## Verification

- `:app:compileReleaseKotlin` — BUILD SUCCESSFUL.
- `:app:testReleaseUnitTest --tests KlippyReadyResyncTest --tests HandshakeTest` — both GREEN; the
  initial-handshake contract is unchanged (no duplicate handshake on connect).
- Full `:app:testReleaseUnitTest` suite — BUILD SUCCESSFUL.
- `grep "NOTIFY_KLIPPY_READY" MoonrakerSession.kt` → line 216, an executable `if` branch (not the
  comment, which uses lowercase). `grep -c "runHandshake" MoonrakerSession.kt` → 6 (≥ 3).

## Deviations from Plan

None — plan executed exactly as written. The plan's "verify MoonrakerService needs no change against the
code" instruction was followed and the determination held (no service edit).

## Known Stubs

None. The re-handshake path is real and proven by the injected-frame regression test. On-device
re-verification (FIRMWARE_RESTART on the live Ender 5 Plus, observing temps resume + an edited
min_extrude_temp refresh without force-stop) is for the Phase-5 verification re-run, not this plan.

## Threat Surface

No new endpoints or trust boundaries. The re-handshake re-issues the SAME requests over the SAME already-
authenticated socket; the new push-driven side-effect is gated (handshakeComplete), serialized
(rehandshakeMutex), and best-effort (runCatching) so a hostile/garbage klippy_ready burst cannot stack
overlapping handshakes or crash the session.

## Self-Check: PASSED

- `app/src/test/java/works/mees/dinghy/net/KlippyReadyResyncTest.kt` exists, injects a no-id
  `notify_klippy_ready` frame, asserts subscribe + temperature_store + configfile re-issue + 220.0f refresh ✓
- `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt` contains an executable
  `NOTIFY_KLIPPY_READY` branch calling `runHandshake()` under `runCatching` + `rehandshakeMutex.withLock`,
  gated by `handshakeComplete` ✓
- Commits present: `36f4324` (RED test), `272d7a3` (GREEN fix) ✓
- `:app:compileReleaseKotlin` + full unit suite green ✓
