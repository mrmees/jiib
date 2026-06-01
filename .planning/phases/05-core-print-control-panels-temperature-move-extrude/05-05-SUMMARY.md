---
phase: 05-core-print-control-panels-temperature-move-extrude
plan: 05
subsystem: ui
tags: [temperature, multi-trace-graph, scrubber, presets, cooldown, backfill, theme-tokens, dispatcher]

# Dependency graph
requires:
  - phase: 05-02
    provides: PrinterCommands.setHeater/applyPreset/COOLDOWN/scriptParams/MATERIAL_PRESETS + ThemeTokens.violet
  - phase: 05-03
    provides: PrinterStateStore.temperatureBackfill one-shot StateFlow (per-sensor history on connect, G-1 fix)
  - phase: 05-04
    provides: multi-trace GraphView + GraphViewHost(series:List, setpoints, yRange) overload
provides:
  - "TemperatureHolder: per-sensor legend + per-sensor backfilled-then-live RingBuffers (series/setpoints/legend StateFlows)"
  - "TemperatureScreen: Focus legend (per-trace color) + Field multi-trace GraphViewHost + Back/Presets/Cooldown gutter + ScrubberPage heater-set + fixed material-preset selector"
  - "SensorReadout(name,label,current,target?) drawn-sensor model"
affects: [05-08-perf-gate, 05-08-uat, shell-routing-to-Temperature-dest]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Holder mirrors PrintStatusHolder/MoveHolder: consumes the store's already-throttled flow, NO second throttle"
    - "One RingBuffer per drawn sensor; backfill seeds oldest→newest ONCE (guarded), then live push appends"
    - "Deterministic 3-trace resolution from capabilities (nozzle→bed→chamber); extra heaters truncated"
    - "Single script() dispatch helper funnels every action through GCODE_SCRIPT + scriptParams (no raw rpc)"
    - "Per-sensor scrubber ceiling helper (bed/chamber 120, nozzle 300) on top of the setHeater 0..350 clamp"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureHolder.kt
    - app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt
    - app/src/test/java/works/mees/dinghy/ui/temperature/TemperatureHolderTest.kt
  modified: []

key-decisions:
  - "3-trace cap is enforced in the HOLDER (resolveDrawn takes ≤3), so a 4+-heater printer shows nozzle/bed/first-generic and silently truncates the rest — matches the heat/accent/violet token palette and the 05-04 MAX_TRACES=3 render contract."
  - "ScrubberPage seeds from the live target (or current when off) coerced into the graph Y-range, and applies via setHeater(name, v.roundToInt()) — the scrubber emits bounded numerics, setHeater clamps to 0..350 (defense in depth, T-05-05-T2)."
  - "Preset selector is a full-screen OPAQUE scrim (.background(t.bg), mirroring the ConfirmGuard 03-08 opaque-scrim fix) so the live graph behind it doesn't bleed through the keyboard-free tiles."
  - "Per-sensor scrubber ceiling (bed/chamber 120 °C, nozzle 300 °C) so the fill-bar doesn't waste travel on unreachable bed targets; setHeater's 0..350 clamp still backstops it."

patterns-established:
  - "Drawn-sensor legend trace color = traceColor(index): 0=heat (nozzle), 1=accent (bed), 2=violet (chamber) — same palette the GraphView paints, so Focus and Field agree."

requirements-completed: [TEMP-01, TEMP-02, TEMP-03, TEMP-04]

# Metrics
duration: 5min
completed: 2026-06-01
---

# Phase 5 Plan 05: Temperature Panel Summary

**The Temperature panel (TEMP-01..04) — a per-trace legend Focus (nozzle/bed/chamber, tap-to-scrub),
a Field hosting the 05-04 multi-trace GraphView seeded deterministically from the temperature_store
backfill (the Phase-4 sparkline gap G-1 fix in anger) with a fixed 0..350 Y-range and per-sensor dashed
setpoints, and a Back/Presets/Cooldown gutter — every heater action routed through the dispatcher as
`printer.gcode.script`, token-pure, keyboard-free (ScrubberPage, no keypad).**

## What Was Built

**Task 1 — TemperatureHolder (TDD, `2eb663a` RED → `3b28e7b` GREEN):**
- `ui/temperature/TemperatureHolder.kt` — plain Kotlin, host-testable, mirrors PrintStatusHolder/MoveHolder.
- Resolves up to 3 DRAWN sensors deterministically from `store.capabilities.heaters` (falling back to live
  state keys for seed-only paths) in trace order: nozzle (`extruder`/first extruder-prefixed) → bed
  (`heater_bed`) → chamber (first `heater_generic `). Owns one `RingBuffer` per drawn sensor.
- **Backfill collector:** collects `store.temperatureBackfill` (the 05-03 one-shot StateFlow); on the first
  non-empty emission seeds each ring oldest→newest from `backfill[sensorName]` BEFORE any live point, guarded
  by a `seeded` flag so a re-emission never re-prepends. This makes the graph full the instant the one-shot
  read lands — not contingent on a later printerState diff (the review determinism point).
- **Live collector:** on every `store.printerState` tick pushes each drawn sensor's current temp into its ring
  (no second throttle — consumes the store's 250 ms conflation), and exposes `series: StateFlow<List<FloatArray>>`
  (one snapshot per trace), `setpoints: StateFlow<List<Float?>>` (live target, null when ≤ 0 / off, mirroring
  the PrintStatusHolder heaterCell rule), and `legend: StateFlow<List<SensorReadout>>` (`SensorReadout(name,label,current,target?)`).
- 5 host tests: legend+series for nozzle+bed; backfill-seeds-oldest-first-then-live-push AND seeds-once-on-re-emit;
  target-0→null setpoint + non-zero at trace index; 3-trace cap truncates a 4th generic heater; StateFlow liveness.

**Task 2 — TemperatureScreen (`761b27f`):**
- `ui/temperature/TemperatureScreen.kt`, `@Composable TemperatureScreen(container, holder, onBack, modifier)`.
  Mirrors the Move panel's `ScreenScaffold` + dispatch-failure→SeverityToast plumbing + in-flight disable verbatim.
  - **Focus:** the legend — single sensor renders large value-on-glyph (current + `→ target` / "off"); multiple
    render a compact per-row list, each outlined in its trace color (nozzle=heat / bed=accent / chamber=violet).
    Tapping a sensor sets `scrubberTarget` to open the ScrubberPage (TEMP-02).
  - **Field:** `GraphViewHost(tokens = t, series = series, setpoints = setpoints, yRange = 0f..350f, …)` — the
    05-04 N-trace host (TEMP-04, G-1 fix), plus the error toast slot.
  - **Gutter:** `OutlinedControl("Back", onBack, Intent.Danger)` · `"Presets" (Intent.Neutral)` · `"Cooldown"
    → script("cooldown", PrinterCommands.COOLDOWN) (Intent.Warn)`.
  - **ScrubberPage** (when `scrubberTarget != null`): label = sensor label, value seeded from the live target
    (or current when off) coerced into 0..350, `range = 0f..maxTempFor(name)`, `step = 5f`, `unit = "°C"`;
    Apply → `script("set_${name}", PrinterCommands.setHeater(name, v.roundToInt()))`. No keypad (D-01).
  - **PresetSelector** (when `showPresets`): an opaque full-screen scrim of the fixed `MATERIAL_PRESETS`
    (PLA/PETG/ABS/TPU, each showing its `nozzle°/bed°`) → `applyPreset(p.nozzle, p.bed)`, plus Cancel. Keyboard-free.
  - All actions funnel through the single `script()` helper → `dispatcher.dispatch(key, GCODE_SCRIPT,
    scriptParams(PrinterCommands.*))`; never a raw rpc. Token-pure (`grep "Color(0x"` empty).

## Verification

- `:app:testReleaseUnitTest --tests *TemperatureHolderTest` — green (5 cases).
- `:app:compileReleaseKotlin` — BUILD SUCCESSFUL.
- Full `:app:testReleaseUnitTest` suite — BUILD SUCCESSFUL, no regressions.
- Acceptance greps confirmed: `GraphViewHost(` with `series`/`setpoints`/`yRange = Y_RANGE` (0f..350f); a
  `ScrubberPage(` invocation; three gutter Intents (Danger/Neutral/Warn); dispatch via `setHeater`/`COOLDOWN`/
  `applyPreset` through `GCODE_SCRIPT` + `scriptParams`; `grep "Color(0x"` returns nothing (token-pure).

The live temperature_store contract, the multi-heater graph during an active heat, TEMP-02/03 dispatch on
real hardware, and the **D-06 Adreno-320 perf re-measure of THIS screen** are the 05-08 UAT/perf-gate items
(per the plan's verification note + mock-vs-reality lesson) — not closed here.

## TDD Gate Compliance

Task 1 followed RED → GREEN with committed gates (`2eb663a` test — compile-fail on the unresolved
`TemperatureHolder`/`SensorReadout`; `3b28e7b` feat). No unexpected RED-phase pass. No REFACTOR commit
(landed clean). Task 2 was a non-TDD compile-gated UI task per the plan (`type="auto"`, no `tdd`).

## Deviations from Plan

None — plan executed exactly as written. (The plan left the per-sensor scrubber ceiling and the preset-selector
chrome to Claude's discretion; chosen as documented above — within the stated latitude, not deviations.)

## 3-Trace Truncation (documented per plan)

A printer with MORE than three heaters (e.g. nozzle + bed + two `heater_generic` zones) draws only the first
three: nozzle, bed, and the FIRST generic (labelled by its name, e.g. "CHAMBER"). Additional generics /
`temperature_sensor` entries are INTENTIONALLY omitted in v1 — the trace palette (heat/accent/violet) and the
05-04 `MAX_TRACES = 3` render contract cap it at three, and fill-rate is the Adreno-320 budget. This is a
known, deliberate v1 limit, not an accidental gap — a richer printer's extra zones simply don't appear on the
graph or legend. (Proven by the `chamberIsThirdTraceAndExtraSensorsTruncated` test.)

## Known Stubs

None — the legend, graph, scrubber-set, presets, and cooldown are all wired to real `PrinterCommands` gcode
through the live dispatcher; series/setpoints/legend come from `TemperatureHolder` fed by the real store +
the 05-03 backfill. The "No heaters" Focus state and the "off" setpoint label are correct empty/off states,
not stubs.

## Threat Surface

No new endpoints beyond the planned threat model. All heater-set / preset / cooldown taps cross the documented
`User tap → action dispatch` boundary via the CommandDispatcher (debounce + in-flight key disable, PRIM-05 /
T-05-05-T/D); the scrubber emits only bounded numerics and `setHeater` clamps to 0..350 before formatting
(T-05-05-T2); a `gcode_response` failure is shown verbatim via SeverityToast, never interpreted (T-05-05-I).

## Self-Check: PASSED

- `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureHolder.kt` — FOUND (`class TemperatureHolder`).
- `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt` — FOUND (`GraphViewHost(`).
- `app/src/test/java/works/mees/dinghy/ui/temperature/TemperatureHolderTest.kt` — FOUND.
- Commits present in git log: `2eb663a` (test), `3b28e7b` (feat), `761b27f` (feat).
- `:app:testReleaseUnitTest` (incl. TemperatureHolderTest) green + `:app:compileReleaseKotlin` succeeds.

---
*Phase: 05-core-print-control-panels-temperature-move-extrude*
*Completed: 2026-06-01*
