---
phase: 05-core-print-control-panels-temperature-move-extrude
plan: 03
subsystem: net/state
tags: [handshake, temperature_store, configfile, backfill, json-rpc, moonraker, stateflow, spine]
requires:
  - JsonRpcMethods.TEMPERATURE_STORE + GCODE_SCRIPT (05-01)
  - HeaterState.canExtrude live cold-extrude gate (05-01)
  - MoonrakerSession.runHandshake (identify→list→query→subscribe spine, 02-04)
  - PrinterStateStore capability-stash pattern (02-04)
  - SpineHandle atomic-publish discipline (04-07)
provides:
  - parseTemperatureStore (pure temperature_store → per-sensor FloatArray backfill mapper)
  - PrinterStateStore.minExtrudeTemp / maxExtrudeDistance / temperatureBackfill (one-shot StateFlows)
  - SpineHandle.minExtrudeTemp / maxExtrudeDistance / temperatureBackfill (forwarded off the live handle)
affects:
  - 05-05 Temperature (collects temperatureBackfill for a full graph on connect — closes Phase-4 G-1)
  - 05-07 Extrude (collects minExtrudeTemp for the real hint number + maxExtrudeDistance as the ceiling)
tech-stack:
  added: []
  patterns:
    - pure history-endpoint mapper mirroring DeriveCapabilities (host-testable, no I/O)
    - one-shot capability-like StateFlow (written once at handshake, OUT of the throttled accumulator)
    - best-effort handshake read (runCatching per one-shot read — a missing endpoint/field degrades, never breaks)
    - faithful-mock hardening (SessionTestHarness answers the REAL temperature_store/configfile shapes)
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/state/TemperatureStore.kt
    - app/src/test/java/works/mees/dinghy/state/TemperatureStoreBackfillTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt
    - app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt
    - app/src/main/java/works/mees/dinghy/di/SpineHandle.kt
    - app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
    - app/src/test/java/works/mees/dinghy/net/HandshakeTest.kt
    - app/src/test/java/works/mees/dinghy/net/SessionTestHarness.kt
    - app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt
    - app/src/androidTest/java/works/mees/dinghy/ui/ShellPresenceTest.kt
decisions:
  - "The two one-shot reads land on StateFlows holders OBSERVE — connect-time graph fullness + the min-temp hint are driven by the data arriving, NOT by a later notify_status_update diff (review determinism fix)."
  - "Both reads are best-effort (per-read runCatching) — they run AFTER the handshake already reached subscribe, so a printer lacking temperature_store/min_extrude_temp degrades (empty graph backfill / generic hint / 100 mm default ceiling) but never breaks Connected (T-05-03-D/Safety)."
  - "min_extrude_temp/max_extrude_only_distance read from the PRIMARY extruder (single-extruder-accurate); the per-tool safety gate stays the LIVE can_extrude boolean, not these static numbers (T-05-03-Safety: accept)."
  - "configfile is a real always-defined Moonraker object answered via a SEPARATE one-shot OBJECTS_QUERY of {configfile} — distinct from the subset query — so the A3 subset validator does not reject it."
metrics:
  duration_min: 12
  completed: 2026-06-01
  tasks: 2
  files: 10
---

# Phase 5 Plan 03: One-Shot History/Config Handshake Reads Summary

Wired the ONLY new networking in Phase 5 — two one-shot reads appended to the connection handshake
(`server.temperature_store` history backfill + a `configfile` query for `min_extrude_temp` /
`max_extrude_only_distance`) — plus a pure, host-tested backfill mapper, and exposed all three results as
capability-like `StateFlow`s on the spine. This is the proper fix for the Phase-4 sparkline gap G-1 (graph
full immediately on connect, survives restart) and the source of the real Extrude min-temp hint number,
both delivered deterministically because the holders OBSERVE the StateFlows rather than waiting on a later
status diff.

## What Was Built

**Task 1 — Pure `parseTemperatureStore` mapper (TDD, `f147cfe` RED → `d646782` GREEN):**
- `state/TemperatureStore.kt` (NEW): `parseTemperatureStore(result: JsonObject, sensors: Set<String>): Map<String, FloatArray>`.
- Maps ONLY the requested drawn-heater sensor names by exact object key; reads each `temperatures`
  FIFO array, keeps it in received order (index 0 = OLDEST → matches RingBuffer push order).
- Null-safe walk (never `!!`); a non-numeric / non-finite sample is skipped (reducer discipline);
  a sensor absent from the response, or present but missing its `temperatures` array, is OMITTED —
  never fabricated as an empty array. Pure `temperature_sensor X` entries not in `sensors` are ignored.
- Reuses the shared `MoonrakerJson` accessors; no second `Json` instance.

**Task 2 — Handshake one-shot reads + spine exposure (`6449381`):**
- `MoonrakerSession.runHandshake()`: after the existing subscribe seed, two BEST-EFFORT one-shot reads,
  each in its own `runCatching`:
  1. `rpc.request(TEMPERATURE_STORE)` → `parseTemperatureStore(result, capabilities.heaters.toSet())` →
     `store.setTemperatureBackfill(map)` (NOT subscribed — live points keep arriving on the existing
     `notify_status_update` stream).
  2. `rpc.request(OBJECTS_QUERY, {configfile})` → null-safe walk
     `status.configfile.settings.extruder.{min_extrude_temp, max_extrude_only_distance}` →
     `store.setMinExtrudeTemp(...)` / `store.setMaxExtrudeDistance(...)`.
  - Added null-safe `objectOrNull` / `floatOrNullAt` config walkers in the session.
- `PrinterStateStore`: three new read-only `StateFlow`s — `minExtrudeTemp: StateFlow<Float?>`,
  `maxExtrudeDistance: StateFlow<Float?>`, `temperatureBackfill: StateFlow<Map<String, FloatArray>>` —
  each backed by a `MutableStateFlow` written ONCE by `setMinExtrudeTemp` / `setMaxExtrudeDistance` /
  `setTemperatureBackfill` at handshake. Kept entirely OUT of the conflated `accumulator` (not the
  throttled hot path).
- `SpineHandle`: three new fields forwarding the store's StateFlows; `MoonrakerService.buildSpineAndLaunch`
  wires them in the single atomic handle publish (StateFlows carry their value forward to late collectors —
  no re-publish needed, atomic-publish discipline intact).

## Why StateFlow, not `@Volatile var`

The review determinism fix: a holder that COLLECTS these StateFlows reacts the instant the one-shot read
lands, so "graph full immediately on connect" and the real min-temp hint are driven by the data arriving,
not contingent on a later `notify_status_update` diff touching the same field. The handle is published
before the handshake fills them, but a StateFlow replays its current value to a late collector.

## TDD Gate Compliance

Task 1 followed RED → GREEN with committed gates:
- `f147cfe` (test, RED — unresolved `parseTemperatureStore`, compile-fail).
- `d646782` (feat, GREEN — mapper + JUnit4-idiom test fix).
No unexpected RED-phase pass. No REFACTOR commit (landed clean). Task 2 is non-TDD wiring/integration.

## Verification

- `:app:testReleaseUnitTest --tests *TemperatureStoreBackfillTest` — green (order oldest-first, sensor
  selection, absence-omission, missing-array omission, garbage-skip).
- Full `:app:testReleaseUnitTest` suite — BUILD SUCCESSFUL. `HandshakeTest` updated to assert the new
  6-step handshake order (identify → list → query → subscribe → temperature_store → configfile query) and
  that the three StateFlows are populated (extruder/heater_bed backfill, `min_extrude_temp=170`,
  `max_extrude_only_distance=50`).
- `:app:compileReleaseKotlin` — succeeds.
- The REAL `temperature_store` / `configfile` shapes are exercised live on the Ender 5 Plus during 05-08
  UAT; `SessionTestHarness` was hardened to faithful Moonraker-shaped replies (mock-vs-reality lesson) so a
  lenient mock can't hide a protocol mismatch.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Threaded the three new SpineHandle fields through two test constructions**
- **Found during:** Task 2 (release compile).
- **Issue:** `AppContainerTest.handle(...)` and `ShellPresenceTest`'s test `SpineHandle(...)` construct the
  handle directly; the three new required fields broke their compile.
- **Fix:** Both already have a real `PrinterStateStore` in scope — forwarded `store.minExtrudeTemp` /
  `store.maxExtrudeDistance` / `store.temperatureBackfill` (refactored `AppContainerTest.handle` to a block
  body binding the store to a local val first).
- **Files modified:** `app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt`,
  `app/src/androidTest/java/works/mees/dinghy/ui/ShellPresenceTest.kt`.
- **Commit:** `6449381`.

**2. [Rule 3 - Blocking] Hardened SessionTestHarness to answer the two new one-shot reads faithfully**
- **Found during:** Task 2 (full unit suite — `HandshakeTest` failures).
- **Issue:** The new reads added two outbound frames the harness didn't model; the `else` branch returned
  `{"result":{}}` (acceptable for temperature_store but unrealistic) and the `configfile` query was
  rejected by the A3 subset validator (not in the objects.list fixture).
- **Fix:** Added realistic `temperatureStoreResultJson` (heater-keyed FIFO arrays + a pure
  `temperature_sensor mcu` entry the mapper must ignore) and `configfileResultJson`
  (`status.configfile.settings.extruder.{min_extrude_temp, max_extrude_only_distance}`); routed the
  `{configfile}`-only query to its own reply (real Moonraker always defines `configfile`). Faithful to the
  live contract per the mock-vs-reality lesson.
- **Files modified:** `app/src/test/java/works/mees/dinghy/net/SessionTestHarness.kt`,
  `app/src/test/java/works/mees/dinghy/net/HandshakeTest.kt`.
- **Commit:** `6449381`.

## Threat Surface

No new endpoints beyond the planned threat model. The two reads cross the documented
`Moonraker socket → backfill/config parse` boundary; mitigations applied as planned (null-safe walks,
non-finite skip, absent-omission, per-read `runCatching` with the JsonRpcClient per-request timeout so a
slow/failed read is best-effort and never blocks Connected).

## Known Stubs

None. No UI in this plan; the three StateFlows are real, populated by the live handshake (proven in
`HandshakeTest`). Their CONSUMERS (graph backfill in 05-05, hint text in 05-07) are downstream-plan work.

## Self-Check: PASSED

- `app/src/main/java/works/mees/dinghy/state/TemperatureStore.kt` exists, contains `fun parseTemperatureStore(` ✓
- `app/src/main/java/works/mees/dinghy/di/SpineHandle.kt` contains `minExtrudeTemp` ✓
- `PrinterStateStore.kt` contains `setMinExtrudeTemp` / `setMaxExtrudeDistance` / `setTemperatureBackfill`,
  exposes the three as read-only `StateFlow`s (not `@Volatile var`), none in the `accumulator` ✓
- `MoonrakerSession.kt` runHandshake contains a `TEMPERATURE_STORE` request and a `configfile` read of both
  `min_extrude_temp` and `max_extrude_only_distance`, each in `runCatching` ✓
- Commits present: f147cfe ✓, d646782 ✓, 6449381 ✓
- Full unit suite green + `:app:compileReleaseKotlin` succeeds ✓
