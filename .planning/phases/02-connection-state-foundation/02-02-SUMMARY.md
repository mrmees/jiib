---
phase: 02-connection-state-foundation
plan: 02
subsystem: pure-state-layer (Wave 2 reducer + capabilities)
tags: [reducer, diff-merge, capabilities, klippy-lifecycle, pure-functions, json]
requires:
  - "works.mees.dinghy.state.PrinterState / KlippyState / PrintState / HeaterState (Plan 01)"
  - "works.mees.dinghy.state.Capabilities (Plan 01)"
  - "works.mees.dinghy.net.JsonRpcMethods + MoonrakerJson (Plan 01)"
  - "GoldenFixtures loader + golden/fallback corpus (Plan 01)"
provides:
  - "reduceSnapshot(JsonObject): PrinterState — pure seed from objects.query/subscribe status"
  - "reduceDiff(PrinterState, JsonObject): PrinterState — pure deep-merge of a notify_status_update diff (Pitfall 1, no field-wipe)"
  - "applyKlippyMethod(PrinterState, String): PrinterState — pure notify_klippy_* -> KlippyState fold"
  - "deriveCapabilities(List<String>): Capabilities — pure objects.list -> Capabilities (powerDevices empty, A4)"
  - "deriveSubscribeSet(List<String>): Set<String> — v1 superset ∩ detected objects (A3)"
affects:
  - "Wave 3 (02-04) session/handshake wires these into the live PrinterState StateFlow and re-runs derive* on every reconnect"
tech-stack:
  added: []
  patterns:
    - "Pure, I/O-free, coroutine-free reducer functions (no socket/Context/CoroutineScope params)"
    - "Null-safe JsonObject walks (runCatching + as? + *OrNull), never !! on wire data (T-02-03 / ASVS V5)"
    - "Single shared status-walker for seed + diff so merge semantics are identical (only the base state differs)"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt
    - app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt
    - app/src/test/java/works/mees/dinghy/state/PrinterStateReducerTest.kt
    - app/src/test/java/works/mees/dinghy/state/KlippyLifecycleTest.kt
    - app/src/test/java/works/mees/dinghy/state/DeriveCapabilitiesTest.kt
  modified: []
decisions:
  - "Klippy/print/heater enums and Capabilities filters mirror the values present in the Plan-01 contracts and golden fixtures; no new public types introduced — the reducer consumes them"
  - "deriveSubscribeSet uses an ordered LinkedHashSet (deterministic iteration) for stable, idempotent output"
metrics:
  duration_min: 6
  tasks: 2
  files: 5
  completed: 2026-05-30
---

# Phase 2 Plan 02: Pure State Layer (Wave 2) Summary

Built the riskiest correctness surface of the phase as **pure, I/O-free functions** consuming the
Plan-01 contracts: a `PrinterStateReducer` that seeds from an `objects.query` snapshot and **deep-merges**
each `notify_status_update` partial diff without wiping retained fields (STATE-01, Pitfall 1), Klippy host
lifecycle folded from no-params `notify_klippy_*` and `webhooks.state` (STATE-04), and a
`deriveCapabilities` / `deriveSubscribeSet` pair that turns `objects.list` into an immutable `Capabilities`
and the exact subscribe set (v1 superset ∩ detected objects, A3/A4). All three JVM unit tests are green over
the golden + adversarial + fallback corpus — no socket, no hardware.

## What Was Built

**Task 1 — PrinterStateReducer (commit `a0de26a`)**
- `state/PrinterStateReducer.kt`: `reduceSnapshot(status)` seeds a full `PrinterState`; `reduceDiff(current, diff)`
  deep-merges a partial diff onto the retained state. Both delegate to one private `applyStatus` walker so
  seed and merge share identical semantics (only the base state differs — default vs retained).
- Heaters merge field-by-field: a temp-only `extruder`/`heater_bed` diff updates `temperature`/`power` and
  KEEPS the previously-set `target` (Pitfall 1, the central STATE-01 trap).
- `applyKlippyMethod(current, method)` folds the no-params `notify_klippy_ready/shutdown/disconnected` into
  the first-class `klippyState` while RETAINING last-known values (D-03 retain vs blanking). `webhooks.state`
  in a snapshot/diff and `print_stats.state` also drive `klippyState`/`printState`.
- Every wire-JSON walk is null-safe (`as?` + `*OrNull` + `runCatching`), never `!!` — T-02-03 mitigation: a
  malformed field is skipped, not fatal.
- `PrinterStateReducerTest` + `KlippyLifecycleTest`: seed-from-snapshot, temp-only-diff-keeps-target, full
  golden-stream replay yielding expected final temps/position/progress with no field reset, adversarial
  shutdown flips `klippyState` while temps are retained, plus ready/disconnected/unknown-method transitions.

**Task 2 — deriveCapabilities + deriveSubscribeSet (commit `d5ca72e`)**
- `state/DeriveCapabilities.kt`: `deriveCapabilities(objects)` filters `objects.list` into
  `hasBed` / `extruderCount` / `macros` (prefix-stripped) / `fans` / `heaters`; `powerDevices` is always
  empty (A4 — sourced later from `machine.device_power.devices`, a different API).
- `deriveSubscribeSet(objects)` intersects the v1 subscribe superset (webhooks, print_stats, virtual_sdcard,
  display_status, toolhead, gcode_move, heater_bed, extruder) with the detected objects and adds dynamic
  objects the printer defines (extra extruders, fans, heater_generic.*) — so the handshake (02-04) NEVER
  subscribes to an object the printer lacks (A3).
- Both functions take only `List<String>` and return `Capabilities`/`Set<String>` — no I/O, no coroutine
  params — so 02-04 re-runs them on every reconnect (STATE-02).
- `DeriveCapabilitiesTest`: full golden printer yields bed/macros/heaters/extruder≥1 with empty powerDevices;
  a hand-authored minimal printer (no heater_bed, single extruder) gates down and `deriveSubscribeSet` omits
  `heater_bed`; idempotence asserted for both functions.

## Verification

- `:app:testDebugUnitTest --tests *PrinterStateReducerTest* --tests *KlippyLifecycleTest*` — SUCCESS (Task 1).
- `:app:testDebugUnitTest --tests *DeriveCapabilitiesTest*` — SUCCESS (Task 2).
- Combined `--tests *PrinterStateReducerTest* --tests *DeriveCapabilitiesTest* --tests *KlippyLifecycleTest*` — BUILD SUCCESSFUL.
- Source review: all five public functions are pure — no `CoroutineScope`/socket/`Context` params, no I/O;
  wire walks are null-safe (no `!!`).

## Deviations from Plan

None - plan executed exactly as written. No bugs, missing functionality, blocking issues, or architectural
changes encountered; no auth gates (pure JVM, no network).

## Known Stubs

None introduced by this plan. `Capabilities.powerDevices` remains intentionally empty per A4 (documented in
the Plan-01 KDoc; population deferred to a later phase) — this is a contract placeholder owned by Plan 01,
not a new stub.

## Notes for Downstream Waves

- **Live capture still pending (inherited from Plan 01).** The golden `objects_query_snapshot.json` /
  `notify_status_update_stream.json` / `objects_list.json` are currently copies of the synthetic fallback
  corpus. `GoldenFixtures.resolve()` prefers live frames automatically when captured; these tests stay green
  on the fallback floor and will simply exercise richer data once real Ender-5-Plus frames are dropped in.
- 02-04 (session/handshake) should: call `deriveCapabilities` after `objects.list`, request exactly
  `deriveSubscribeSet(objects)` from `objects.subscribe`, seed via `reduceSnapshot(result.status)`, then feed
  each `notify_status_update[0]` to `reduceDiff` and each `notify_klippy_*` method name to `applyKlippyMethod`,
  re-running `deriveCapabilities`/`deriveSubscribeSet` on every reconnect (STATE-02). The reducer does not own
  `stale`/`ConnectionState` transitions — those are the session layer's responsibility (D-03/D-04).

## Self-Check: PASSED

All 5 created files exist on disk; both task commits (a0de26a, d5ca72e) are in git history.
