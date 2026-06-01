---
phase: 05-core-print-control-panels-temperature-move-extrude
verified: 2026-06-01T00:00:00Z
status: gaps_found
score: 10/12
gaps:
  - truth: "Any printer-rejected gcode crashes the whole app (+ FGS auto-restart)"
    status: failed
    reason: |
      CommandDispatcher.dispatch() launches a coroutine on scope and catches only
      RpcConnectionException and TimeoutCancellationException. RpcError — the exception
      thrown by JsonRpcClient.request() on a JSON-RPC error response — is NOT caught. A
      Moonraker error envelope for a gcode.script call (out-of-range move, failing macro,
      heater fault) propagates uncaught out of the scope.launch lambda, killing the
      coroutine and crashing the app. Confirmed on flox: an out-of-range jog move during
      UAT produced FATAL EXCEPTION on DefaultDispatcher-worker-N, triggering FGS auto-restart.
    artifacts:
      - path: "app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt"
        issue: >
          dispatch() launch block catches RpcConnectionException + TimeoutCancellationException
          but not RpcError (lines ~107-115). RpcError is not even imported in this file.
          A gcode.script error response from Moonraker completes the pending deferred with
          RpcError (JsonRpcClient line 155), which then re-throws in the launch block uncaught.
      - path: "app/src/main/java/works/mees/dinghy/net/RpcError.kt"
        issue: "RpcError extends Exception — it is a checked exception-class peer, not a transport error, and requires an explicit catch clause. It is distinct from RpcConnectionException."
    missing:
      - "Add `catch (e: RpcError)` inside the dispatch() launch block, emit DispatchEvent.Failure(key, e.message ?: method) so the SeverityToast pipeline surfaces the printer's rejection message to the user."
      - "Unit-test regression: harden FakeWebSocket (or the fake request() implementation) to return a JSON-RPC error response for printer.gcode.script so the mock-vs-reality gap that concealed this bug is closed."

  - truth: "After a Klipper FIRMWARE_RESTART / printer.cfg reload the status feed recovers without app force-stop"
    status: failed
    reason: |
      MoonrakerSession and MoonrakerService contain no handler for notify_klippy_ready.
      Grep across the full source confirms the constant NOTIFY_KLIPPY_READY is defined in
      JsonRpc.kt but is never consumed by session reconnect logic or the service. When
      Klipper restarts it emits notify_klippy_ready; without a re-handshake the
      objects/subscribe state is lost, temps and positions stop updating, and the persistent
      foreground service keeps the stale session alive. Reopening the app does NOT recover
      because the FGS is still running with the dead subscription. Only a force-stop+relaunch
      recovers. Confirmed live on flox during UAT.
    artifacts:
      - path: "app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt"
        issue: >
          runHandshake() is called once at connect-time. There is no branch in the push-
          dispatch path that recognises NOTIFY_KLIPPY_READY and triggers a re-handshake
          (re-identify + re-objects/subscribe + re-one-shot reads). The constant is only
          mentioned in a comment on line 173.
      - path: "app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt"
        issue: "No handling of notify_klippy_ready at the service level."
    missing:
      - "In MoonrakerSession, handle incoming notify_klippy_ready notifications in the push-dispatch path by triggering a full re-handshake (re-identify → re-objects/subscribe → re-run temperature_store + configfile one-shot reads)."
      - "In MoonrakerService or the session's supervising reconnect loop, ensure a klippy_ready event re-runs the same path as an initial connect so the FGS recovers without a force-stop."

  - truth: "One-shot reads (min_extrude_temp, max_extrude_only_distance, objects/capabilities) remain accurate after a printer.cfg reload"
    status: failed
    reason: |
      The 05-03 one-shot reads (temperature_store backfill, min_extrude_temp,
      max_extrude_only_distance) are performed once at handshake-time via runHandshake().
      Because there is no re-handshake on notify_klippy_ready (G2 root cause), these
      values are never refreshed after a printer.cfg reload or FIRMWARE_RESTART. Editing
      min_extrude_temp in printer.cfg and running FIRMWARE_RESTART leaves the ExtrudeHolder
      showing the stale value until app force-stop. The capabilities list (objects/list from
      the initial handshake) is similarly stale. Confirmed live with an edited
      min_extrude_temp on flox during UAT.
    artifacts:
      - path: "app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt"
        issue: "One-shot configfile reads at lines ~283-323 run only in runHandshake(); a config reload is never detected so they are never re-issued."
      - path: "app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt"
        issue: "setMinExtrudeTemp / setMaxExtrudeDistance / setTemperatureBackfill are called only from the initial handshake; no mechanism to refresh them on klippy_ready."
    missing:
      - "When G2 is fixed (re-handshake on notify_klippy_ready), re-run ALL one-shot reads as part of that re-handshake. No separate fix needed if G2 is addressed correctly — but the fix for G2 MUST include re-issuing these reads, not only re-subscribing."
---

# Phase 5: Core Print-Control Panels Verification Report

**Phase Goal:** The core print-control panels — Temperature (TEMP-01..04), Move (MOVE-01..04), Extrude (EXTR-01..04) — built per the UI design LAW and proven on the Adreno-320 floor.
**Verified:** 2026-06-01
**Status:** gaps_found
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Temperature panel shows per-heater current/target (TEMP-01) | VERIFIED | TemperatureHolder.kt exposes `legend: StateFlow<List<SensorReadout>>`; TemperatureScreen.kt collects and renders it |
| 2 | Tapping a temp value opens ScrubberPage → dispatches SET_HEATER_TEMPERATURE (TEMP-02) | VERIFIED | TemperatureScreen.kt contains `ScrubberPage(` + `dispatch(... GCODE_SCRIPT, scriptParams(PrinterCommands.setHeater(...)))` |
| 3 | Presets and Cooldown dispatch correct gcode (TEMP-03) | VERIFIED | PrinterCommands.MATERIAL_PRESETS + COOLDOWN = "TURN_OFF_HEATERS"; wired in TemperatureScreen.kt |
| 4 | Multi-trace GraphView seeded from temperature_store backfill, fixed 0..350 Y-range, dashed setpoint lines (TEMP-04, G-1 fix) | VERIFIED | GraphView.kt: Array<Path>, yRange = 0f..350f, setSetpoints(); TemperatureHolder.kt seeds rings from temperatureBackfill StateFlow; perf gate PASS (p95 48.64 ms vs ~66 ms bound) |
| 5 | Move panel jogs X/Y/Z by preset distance via SAVE/G91/G1/RESTORE (MOVE-01) | VERIFIED | PrinterCommands.jog() produces correct gcode; MoveScreen.kt dispatches via GCODE_SCRIPT |
| 6 | Center cell homes XY only (G28 X Y); gutter homes all (G28); axis corner homes that axis (MOVE-02) | VERIFIED | PrinterCommands.homeXY() = "G28 X Y", homeAll() = "G28", homeAxis(a); wired in MoveScreen.kt; UAT steps 5/7 passed on flox |
| 7 | Disable steppers routes through ConfirmGuard → M84 (MOVE-03) | VERIFIED | MoveScreen.kt contains `ConfirmGuard(destructive = false)` → `dispatch(... DISABLE_STEPPERS)` |
| 8 | Live gcode_position X/Y/Z renders value-on-glyph, green=homed/amber=unhomed (MOVE-04) | VERIFIED | MoveHolder reads `gcodePosition` (not toolheadPosition); per-axis homed from `homedAxes`; wired in MoveScreen.kt |
| 9 | Extrude/Retract dispatch SAVE/M83/G1 E±/RESTORE at selected distance and speed (EXTR-01) | VERIFIED | PrinterCommands.extrude() confirmed; ExtrudeScreen.kt dispatches via GCODE_SCRIPT; UAT step 3 passed on flox |
| 10 | Load/Unload always shown; missing macro shows informational SeverityToast; when present dispatches macro (EXTR-02) | VERIFIED | ExtrudeScreen.kt gutter has unconditional Load/Unload; `if (vm.hasLoadMacro)` gate; SeverityToast(Severity.Info) for absent macro |
| 11 | Extrude/Retract FAIL — any printer-rejected gcode crashes the app | FAILED | See G1 below. Confirmed on flox: out-of-range jog → FATAL EXCEPTION. Unit tests are green only because FakeWebSocket never returns a JSON-RPC error for gcode.script. |
| 12 | Status feed recovers after FIRMWARE_RESTART without force-stop (CONN-04 durability) | FAILED | See G2 below. No notify_klippy_ready handler exists in MoonrakerSession or MoonrakerService. One-shot reads also go stale (G3). |

**Score:** 10/12 truths verified (G1 is a BLOCKER; G2+G3 share a root cause and are HIGH)

---

### Requirement Coverage

| Requirement | Plan | Description | Status | Notes |
|-------------|------|-------------|--------|-------|
| TEMP-01 | 05-05 | Per-heater current/target visible | SATISFIED | TemperatureHolder legend + TemperatureScreen rendering |
| TEMP-02 | 05-05 | Set heater target via scrubber | SATISFIED | ScrubberPage → dispatch setHeater |
| TEMP-03 | 05-05 | Presets + cooldown | SATISFIED | MATERIAL_PRESETS dispatched; COOLDOWN wired |
| TEMP-04 | 05-03, 05-04, 05-05 | Temperature history graph, backfilled | SATISFIED | BackfillStateFlow seeded; fixed Y-range; perf PASS |
| MOVE-01 | 05-06 | Jog X/Y/Z by preset distance | SATISFIED | jog() builders + MoveScreen dispatch |
| MOVE-02 | 05-06 | Home all / per-axis | SATISFIED | homeAll / homeXY / homeAxis all wired |
| MOVE-03 | 05-06 | Disable steppers with confirm | SATISFIED | ConfirmGuard → M84 |
| MOVE-04 | 05-01, 05-06 | Live toolhead position | SATISFIED | gcodePosition field + MoveHolder |
| EXTR-01 | 05-07 | Extrude/retract by distance + speed | SATISFIED | extrude() builder + ExtrudeScreen |
| EXTR-02 | 05-07 | Load/unload with missing-macro popup | SATISFIED | hasMacroIgnoreCase + SeverityToast.Info |
| EXTR-03 | 05-07 | Tool selector on multi-extruder | SATISFIED | `if (vm.showToolSelector)` guard in ExtrudeScreen |
| EXTR-04 | 05-01, 05-03, 05-07 | Cold-extrude gate with real min-temp hint | SATISFIED (static) — THREATENED by G1 | canExtrude gating works; however G1 means a firmware-rejected gcode still crashes the app |

---

### Required Artifacts

| Artifact | Expected | Status | Notes |
|----------|----------|--------|-------|
| `state/PrinterState.kt` | gcodePosition + HeaterState.canExtrude | VERIFIED | Lines 47, 86 |
| `state/Capabilities.kt` | hasMacroIgnoreCase helper | VERIFIED | Line 42 |
| `net/JsonRpc.kt` | GCODE_SCRIPT + TEMPERATURE_STORE constants | VERIFIED | Lines 109, 112 |
| `command/PrinterCommands.kt` | All gcode builders + scriptParams | VERIFIED | All builders present; clamping confirmed |
| `theme/ThemeTokens.kt` + `BakedTokens.kt` | Third trace token (violet), baked dark+light | VERIFIED | Both files contain `violet` |
| `state/TemperatureStore.kt` | parseTemperatureStore pure mapper | VERIFIED | Contains `fun parseTemperatureStore(` |
| `state/PrinterStateStore.kt` | minExtrudeTemp / maxExtrudeDistance / temperatureBackfill StateFlows | VERIFIED | Lines 69-79; set* methods at 155-166 |
| `di/SpineHandle.kt` | Three one-shot StateFlows exposed | VERIFIED | Lines 51-55 |
| `render/GraphView.kt` | N-trace Array<Path>, fixed yRange, setSetpoints | VERIFIED | MAX_TRACES=3; yRange=0f..350f; setSetpoints present |
| `render/GraphViewHost.kt` | Multi-snapshot + yRange series overload | VERIFIED | Both overloads present |
| `ui/temperature/TemperatureHolder.kt` | Per-sensor legend + backfilled rings | VERIFIED | series/setpoints/legend StateFlows |
| `ui/temperature/TemperatureScreen.kt` | Focus values + Field graph + gutter + scrubber/presets | VERIFIED | GraphViewHost(series, setpoints, yRange=0f..350f) + ScrubberPage + 3 gutter controls |
| `ui/move/MoveHolder.kt` | gcodePosition + per-axis homed gating | VERIFIED | Reads gcodePosition, not toolheadPosition |
| `ui/move/MoveScreen.kt` | 3×3 jog pad + Z row + distance selector + gutter + ConfirmGuard | VERIFIED | ConfirmGuard(destructive=false); all dispatch paths present |
| `ui/extrude/ExtrudeHolder.kt` | canExtrude gate + tool list + macro presence + hint | VERIFIED | All fields in ExtrudeVm |
| `ui/extrude/ExtrudeScreen.kt` | Move-style extrude/retract + selectors + load/unload + tool select | VERIFIED | SeverityToast.Info for missing macro; distance gating on maxExtrudeDistance |
| `ui/route/TopRoute.kt` | Dest enum extended with Temperature/Move/Extrude | VERIFIED | Line 30 |
| `ui/shell/AppShell.kt` | when(dest) routes three new panels with per-session holders | VERIFIED | Lines 110-120 |
| `ui/shell/AppDrawer.kt` | Move/Temp/Tools tiles point to new Dest values | VERIFIED | Dest.Move / Dest.Temperature / Dest.Extrude wired |
| `.planning/.../05-PERF-RESULTS.md` | D-06 perf re-measurement record with p95 | VERIFIED | p95 48.64 ms vs ~66 ms bound; 0 frozen frames; PASS |

---

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `CommandDispatcher.kt` | `MoonrakerSession.request()` | `withTimeout { request(method, params) }` | PARTIAL — wired but RpcError not caught (G1 BLOCKER) |
| `MoonrakerSession.kt` | `server.temperature_store` | `rpc.request(TEMPERATURE_STORE)` in runHandshake | VERIFIED |
| `MoonrakerSession.kt` | `configfile.settings.extruder.*` | one-shot configfile query at handshake | VERIFIED |
| `MoonrakerSession.kt` | `notify_klippy_ready` | re-handshake trigger | NOT WIRED (G2) |
| `TemperatureHolder.kt` | `temperatureBackfill` StateFlow | seed per-sensor rings from backfill | VERIFIED |
| `TemperatureScreen.kt` | `CommandDispatcher` | `dispatch(GCODE_SCRIPT, scriptParams(...))` | VERIFIED |
| `MoveScreen.kt` | `CommandDispatcher` | `dispatch(GCODE_SCRIPT, scriptParams(PrinterCommands.*))` | VERIFIED |
| `ExtrudeScreen.kt` | `CommandDispatcher` | `dispatch(GCODE_SCRIPT, scriptParams(PrinterCommands.*))` | VERIFIED |
| `GraphViewHost.kt` | `GraphView.kt` | `applyTokens` + `setData(series)` + `setSetpoints(setpoints)` | VERIFIED |

---

### D-06 Perf Gate (on-device — authoritative)

| Gate | Result | Evidence |
|------|--------|----------|
| Liveness: 0 frozen frames, no animation loop | PASS | 0 frames > 700 ms across all three captures |
| Sparse-redraw p95 ≤ ~66 ms | PASS | 48.64 ms p95 on live PLA ramp (~17 ms margin) |
| Area-fill attribution baseline | RECORDED | fill-ON p95 44.45 ms vs fill-OFF 30.28 ms; Δ ~14 ms |

---

### Anti-Patterns

| File | Issue | Severity |
|------|-------|----------|
| `command/CommandDispatcher.kt` | RpcError not caught in dispatch() launch block | BLOCKER — confirmed crash on flox |
| `net/MoonrakerSession.kt` | NOTIFY_KLIPPY_READY not handled (appears only in a comment) | HIGH — confirmed status-feed death on flox |

No TBD/FIXME/XXX/TODO debt markers found in phase-modified files.

---

## Gaps Summary

Three confirmed gaps were observed on real hardware (flox, adb 0a64b42e, LineageOS 18.1 / API 30) during Phase-5 on-device UAT against the live Ender 5 Plus. The D-06 multi-trace perf gate PASSED (p95 48.64 ms). SC-5 was PARTIAL: steps 3a (cold-extrude gate) and 3b (extrude/retract/distance/load-unload) passed; step 3c (move/jog) CRASHED on an out-of-range jog, which also made the Override path untestable.

**G1 — BLOCKER:** `CommandDispatcher.dispatch()` is missing a `catch (e: RpcError)` clause. Any printer-rejected gcode (out-of-range move, failing macro, heater fault) throws an uncaught `RpcError` — which extends `Exception` and is distinct from `RpcConnectionException` — into the unsupervised `scope.launch` lambda. The exception propagates to the coroutine scope, terminating it, and the app process crashes with FATAL EXCEPTION. The FGS then auto-restarts. The unit suite is green only because the `FakeWebSocket`/mock `request()` never emits a JSON-RPC error response for `gcode.script` — a direct instance of the mock-vs-reality lesson. Fix: add `catch (e: RpcError)` in the `dispatch()` launch block and emit `DispatchEvent.Failure`; harden the test fake to emit error responses.

**G2 — HIGH:** No re-handshake on `notify_klippy_ready`. After a `FIRMWARE_RESTART` or `printer.cfg` reload, Klipper emits `notify_klippy_ready`. `MoonrakerSession` has no handler for it — the constant is defined in `JsonRpc.kt` and referenced only in a comment. The `objects/subscribe` state is lost silently, temperatures and positions stop updating, but gcode dispatch still routes through the live socket. Reopening the app does not recover because the persistent FGS keeps the stale session alive. Only a force-stop+relaunch recovers. Fix: handle `notify_klippy_ready` in `MoonrakerSession`'s push-dispatch path by triggering a full re-handshake (re-identify → re-`objects/subscribe` → re-run all one-shot reads).

**G3 — MED (same root as G2):** The 05-03 one-shot reads (`min_extrude_temp`, `max_extrude_only_distance`, temperature backfill, capabilities list) are never refreshed after a config reload, because there is no `notify_klippy_ready` re-handshake. The `setMinExtrudeTemp`/`setMaxExtrudeDistance`/`setTemperatureBackfill` methods in `PrinterStateStore` are called only from the initial `runHandshake()`. Confirmed live: editing `min_extrude_temp` in `printer.cfg`, running `FIRMWARE_RESTART`, and observing the Extrude panel still shows the old value until force-stop. Fix: included in G2 — the G2 re-handshake fix MUST re-run all one-shot reads, not only re-subscribe.

---

_Verified: 2026-06-01_
_Verifier: Claude (gsd-verifier)_
