# Phase 5: Core Print-Control Panels — Temperature, Move, Extrude - Research

**Researched:** 2026-05-31
**Domain:** Moonraker/Klipper manual-control API (heaters, toolhead, extruder) + extending the existing hybrid Compose/Views render & dispatch primitives
**Confidence:** HIGH (the Moonraker field paths and gcode calls are verified against official docs + the existing codebase; the perf re-measure plan is carried verbatim from Phase 3)

## Summary

This is a STANDARD-research phase: the architecture is already built (Phases 2–4) and the job is to
VERIFY the specific Moonraker field paths CONTEXT.md flagged, then map 12 requirements onto existing
primitives. Every flagged API fact was confirmed against the official Moonraker docs: `server.temperature_store`
(`GET /server/temperature_store`, per-sensor `temperatures`/`targets`/`powers` FIFO arrays, 1200 samples
@ 1 Hz), `extruder.can_extrude` (live boolean = "current temp above min extrude temp"),
`configfile.settings.extruder.min_extrude_temp` (parsed-config numeric), `toolhead.homed_axes` +
`gcode_move.gcode_position` (the user-facing X/Y/Z — offsets-stripped, the correct MOVE-04 source), and
`printer.gcode.script` (the one method that runs ALL the G-code actions: jog, home, set-temp, extrude,
disable, tool-select).

The single genuinely new mechanic is the `server.temperature_store` history backfill (first use of a
history endpoint), which closes the Phase-4 sparkline gap G-1, and the extension of the single-trace
`GraphView` to N pre-allocated traces. Everything else — `ScrubberPage`, `ConfirmGuard`, `CommandDispatcher`,
`ScreenScaffold`, the `PrintStatusHolder` pattern, capability gating — is consumed, not invented.

Two real gaps the planner must close, both surfaced below: (1) `deriveCapabilities`/`PrinterState` do NOT
yet carry `can_extrude`, `min_extrude_temp`, `gcode_position`, or load/unload macro presence — these are
pure-reducer additions; and (2) there is **no `violet` (chamber) semantic token** in `ThemeTokens` despite
README §9 calling for one — a 3rd trace color must be added to the token system, not hardcoded.

**Primary recommendation:** Build three `*Holder` classes mirroring `PrintStatusHolder` (consume the
already-throttled `PrinterStateStore.printerState`, NO second throttle), route every action through the
session `CommandDispatcher` as `printer.gcode.script` calls, extend `GraphView` to N traces with a fixed
Y-range and `temperature_store` backfill, and re-run the Phase-3 two-part Adreno-320 perf gate on the full
Temperature screen on `flox`.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Heater-target entry uses the Phase-3 `ScrubberPage` (tap-to-set + ± steppers). **NO 0-9 keypad** —
  the design system has none and the UI LAW forbids any keyboard in printer controls. "exact keypad entry"
  (TEMP-02) maps to the numpad-style scrubber+steppers, not a telephone pad. Tapping a live temperature value
  opens its single-setting page.
- **D-02:** Preheat presets = built-in FIXED material set — PLA / PETG / ABS / TPU nozzle+bed pairs — plus
  Cooldown (all heaters off). Hardcoded for v1, no in-app editor. Presets + Cooldown live in the
  temperature-graph gutter (mockup #9). Default temps are Claude's discretion (verify reasonable).
- **D-03:** Move panel is LAW per `04-move.png` + README #4. Focus = 3×3 XY jog pad (edge arrows; center =
  XY home; corner cells show live X/Y/Z value-on-glyph, label green=homed/amber=unhomed; one corner = amber
  Override to jog while unhomed). Field = Z row (∧/Z/∨) + 6-up distance selector (0.1–100 mm, square buttons).
  Gutter = Home (accent) · Disable (amber) · Back (red). Disable-steppers routes through `ConfirmGuard`.
  Live position is value-on-glyph in jog-pad corners (no separate readout cell). Un-homed axes visibly gated.
- **D-04:** Temperature graph is LAW per `09-temperature-graph.png` + README #9. Focus = live current/setpoint
  value(s) (single = value-on-glyph; multi = legend list). Field = time-series line graph: single trace with
  dashed setpoint line, OR multiple sensors overlaid on ONE axis with legend (nozzle=heat/amber, bed=accent/blue,
  chamber=violet). Gutter = Back (red) · Presets (neutral) · Cooldown (amber).
- **D-05:** Graph EXTENDS `GraphView`/`GraphViewHost` — do NOT fork. Closes Phase-4 G-1: (a) backfill from
  `server.temperature_store` on connect (full graph immediately, survives restart), (b) sensible/stable
  Y-range instead of noise-amplifying window min/max. Multi-trace = N series on one shared axis (extend the
  single-`Path` GraphView to N pre-allocated paths/paints; keep allocation-free onDraw).
- **D-06:** MANDATE: multi-trace Temperature graph MUST re-measure the combined surface against the Phase-3
  two-part Adreno-320 perf gate (liveness + sparse-redraw p95 ≤ ~66 ms) on real `flox`. Do NOT grandfather
  the 50.1 ms isolated number.
- **D-07:** Cold-extrude guard = disable + explain. Gate Extrude/Retract on the live `can_extrude` boolean;
  while false, controls disabled (reduced opacity) with inline reason ("Heat nozzle to extrude", ideally with
  the real `min_extrude_temp`). No failed taps.
- **D-08:** Extrude panel = Move-style (no mockup → follow Move template in Focus/Field/Gutter): Extrude/Retract
  primary actions + 6-up distance selector + speed selector. Keyboard-free; speed defaults Claude's discretion.
- **D-09:** Multi-extruder tool selection = capability-gated selector (T0/T1…) shown ONLY when >1 extruder;
  hidden entirely on single-extruder printers.
- **D-10:** Load/Unload filament = always show buttons; if `LOAD_FILAMENT`/`UNLOAD_FILAMENT` gcode_macro not
  configured, tapping shows an informational popup ("No LOAD_FILAMENT macro configured") via `SeverityToast`.
- **Cross-cutting:** Three panels are App Drawer destinations (extend `Dest` enum + `AppDrawer` tiles +
  `RootController`). EVERY action wrapped by `CommandDispatcher` (no raw `rpc.request` from a panel).
  Capability-gating via the holder pattern (hide unsupported). All layout via `ScreenScaffold`; all color via
  `LocalTokens` (zero raw Color); button intent = color.

### Claude's Discretion
- Exact preset temperatures (PLA/PETG/ABS/TPU nozzle+bed pairs) and Cooldown behavior.
- Extrude distance set + default speeds (mm/min); jog distance set is LOCKED (0.1–100 mm by mockup).
- Graph time-window length (driven by `server.temperature_store` default, ~20 min @ 1 Hz).
- Toolhead-position source for MOVE-04: `toolhead.position` vs `gcode_move.gcode_position` (researcher picks
  the correct field — **gcode_position**, see below).

### Deferred Ideas (OUT OF SCOPE)
- User-editable preset temperatures (editor UI + DataStore persistence) — future phase; v1 fixed set.
- Fan-speed single-setting page — not in Phase 5's 12 requirements; defer.
- A dedicated 0-9 numeric keypad component — only if the design language later sanctions it.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TEMP-01 | See each heater/sensor current + target | `PrinterState.heaters` already carries `temperature`/`target`/`power` per heater (reducer done). `Capabilities.heaters` lists names. Holder maps to Focus value-on-glyph / legend. |
| TEMP-02 | Set heater target via presets + exact (scrubber) entry | `printer.gcode.script` → `SET_HEATER_TEMPERATURE HEATER=<name> TARGET=<v>`. Exact value via existing `ScrubberPage`. |
| TEMP-03 | Apply preheat presets + cooldown | Preset = N×`SET_HEATER_TEMPERATURE` dispatches (or `M104/M140`). Cooldown = `TURN_OFF_HEATERS` (single gcode, all heaters). |
| TEMP-04 | Temperature history graph backfilled from `server.temperature_store` | `GET /server/temperature_store` (or RPC) → per-sensor `temperatures` arrays seed the ring(s). Extend `GraphView` to N traces + fixed Y-range. Closes G-1. |
| MOVE-01 | Jog X/Y/Z by distance preset | `printer.gcode.script` → `G91`/`G1 <axis><±dist> F<feed>`/`G90` (or `SAVE_GCODE_STATE`-wrapped). |
| MOVE-02 | Home all / individual axes | `G28` (all) or `G28 X`/`G28 Y`/`G28 Z` (per-axis). |
| MOVE-03 | Disable steppers (confirmed) | `M84` via `printer.gcode.script`, routed through `ConfirmGuard`. |
| MOVE-04 | Live toolhead position | `gcode_move.gcode_position` (offsets-stripped, user-facing — NOT `toolhead.position`). Add to `PrinterState`. Homed gating from `toolhead.homed_axes`. |
| EXTR-01 | Extrude/retract selected distance at selected speed | `G91`/`M83`/`G1 E<±dist> F<feed>`/restore. Distance + speed via selectors/scrubber. |
| EXTR-02 | Run load/unload macros when present | `LOAD_FILAMENT`/`UNLOAD_FILAMENT` gcode_script when macro present; popup when absent (D-10). |
| EXTR-03 | Select active extruder/tool on multi-extruder | `Capabilities.extruderCount > 1` gates a `T0`/`T1`… selector (gcode `T0`/`ACTIVATE_EXTRUDER`). |
| EXTR-04 | Respect minimum extrusion temperature | Gate on live `extruder.can_extrude` boolean; hint from `configfile.settings.extruder.min_extrude_temp`. |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Heater/position/extruder live readings | State spine (`PrinterStateStore`, throttled) | Reducer (`PrinterStateReducer`) | Already the single source of truth; panels observe, never poll. |
| Capability derivation (extruder count, can_extrude, macros, homed) | Pure reducer (`deriveCapabilities` + `PrinterState`) | — | Pure, unit-testable, re-run every reconnect. |
| Per-panel derived view models | Toolkit-agnostic Holder (new `Temperature/Move/ExtrudeHolder`) | — | Mirror `PrintStatusHolder`; consume throttled flow, no 2nd throttle. |
| Action dispatch (jog/heat/extrude/home/disable/tool) | `CommandDispatcher` (PRIM-05) → `printer.gcode.script` | `JsonRpcClient` | Uniform timeout/in-flight/debounce; no raw rpc from panels. |
| History backfill | `JsonRpcClient.request("server.temperature_store")` once on connect | Holder seeds the ring(s) | First history-endpoint use; one-shot, not subscribed. |
| High-churn graph render | classic-Views `GraphView` (ADR 0001) | `GraphViewHost` interop | Views won the high-churn render; extend in place. |
| Panel chrome / controls | Compose (`ScreenScaffold`, `OutlinedControl`, `ScrubberPage`, `ConfirmGuard`) | `LocalTokens` | Everything but the graph is Compose per ADR 0001. |
| Top-level routing to the 3 panels | `RootController` + `Dest` enum + `AppDrawer` | — | One-line additions per CONTEXT. |

## Standard Stack

No new libraries. This phase uses ONLY the locked stack already in `libs.versions.toml` (OkHttp,
Retrofit, kotlinx.serialization, Coroutines/Flow, Compose BOM, Material3, classic Views for the graph).
Per CONTEXT and CLAUDE.md: **do NOT propose new dependencies or toolkit changes — extend existing primitives.**

### Reused Primitives (consume, never fork)
| Asset | Path | Use in Phase 5 |
|-------|------|----------------|
| `ScrubberPage` | `designsystem/ScrubberPage.kt` | Heater target (TEMP-02), extrude distance/speed (EXTR-01). Tap-to-set fixed (G-3). |
| `ConfirmGuard` | `designsystem/ConfirmGuard.kt` | Disable steppers (MOVE-03). Opaque scrim fixed (G-4). |
| `GraphView` + `GraphViewHost` | `render/GraphView.kt`, `render/GraphViewHost.kt` | EXTEND to N traces (TEMP-04, D-05). Keep allocation-free `onDraw`. |
| `CommandDispatcher` | `command/CommandDispatcher.kt` | Wrap EVERY action (PRIM-05). |
| `SeverityToast` | `designsystem/SeverityToast.kt` | Missing-macro popup (D-10), dispatch failures. |
| `ScreenScaffold` + `OutlinedControl` | `designsystem/layout/`, `designsystem/control/` | Focus/Field/Gutter + intent-colored controls. |
| `PrintStatusHolder` (pattern) | `ui/printstatus/PrintStatusHolder.kt` | Template for the 3 new holders. |
| `PrinterState` / `deriveCapabilities` | `state/` | Extend (pure reducer additions). |

**Installation:** None. (Gradle, not npm — no package changes this phase.)

## Package Legitimacy Audit

> Not applicable — this phase installs **no external packages**. All work extends existing in-repo
> primitives and the already-pinned `libs.versions.toml` stack. No registry/slopcheck step required.

## Moonraker / Klipper API — Verified Field Paths & Calls

This is the load-bearing section. Every fact below is verified against the official Moonraker docs
(moonraker.readthedocs.io) and/or the existing codebase. The project has been bitten by a too-lenient
mock hiding a real server contract (the identify-needs-`url` incident) — so each of these MUST be
exercised against the **live Ender 5 Plus** during UAT, not just unit-mocked.

### 1. `server.temperature_store` (TEMP-04 backfill — D-05) — `[CITED: moonraker.readthedocs.io/external_api/server]`
- **JSON-RPC method:** `server.temperature_store`. **HTTP:** `GET /server/temperature_store`.
- **Response shape:** an object keyed by sensor object name; each value carries FIFO arrays:
  `temperatures` (float), and for heaters also `targets` (float) and `powers` (0–1). Fans add `speeds`.
  **Index 0 is the OLDEST** value; one measurement per second.
  ```json
  { "extruder": { "temperatures": [21.05, 21.1, 21.1], "targets": [0,0,0], "powers": [0,0,0] },
    "heater_bed": { "temperatures": [...], "targets": [...], "powers": [...] } }
  ```
- **Default history length:** 1200 values @ 1 Hz ⇒ **~20-minute window** (matches CONTEXT's discretion note).
- **Optional param:** `include_monitors` (bool, default false) — adds temperature *monitors* (sensors that
  may report `null`). v1 doesn't need monitors; **leave it false** so the arrays stay all-finite (the
  `GraphView.sanitize` NaN filter already guards, but avoiding nulls is cheaper).
- **Mechanics:** one-shot `rpc.request(...)` on connect (NOT subscribed). Backfill the per-sensor ring
  buffer(s) oldest→newest, then let the existing live `notify_status_update` stream append new points.
  This is what makes the graph full immediately and survive app restart (the G-1 fix).
- ⚠ **Sensor-name alignment:** the store keys are the SAME object names as `heaters`/`Capabilities.heaters`
  (`extruder`, `heater_bed`, `heater_generic chamber`). Map by exact key. The store may also contain
  pure `temperature_sensor X` entries that aren't in the heater set — only backfill the sensors the graph
  actually draws.

### 2. `extruder.can_extrude` + `min_extrude_temp` (EXTR-04 / D-07) — `[CITED: moonraker.readthedocs.io/printer_objects]`
- **`extruder.can_extrude`** is a live **boolean** on the `extruder` printer object: "a value of true
  indicates that the current temperature is above the minimum extrusion temp." Full extruder field set:
  `temperature, target, power, can_extrude, pressure_advance, smooth_time, motion_queue`.
  → Subscribe must request `can_extrude`. The existing subscribe uses `null` (all fields), so it's already
  covered — but `PrinterState`/reducer must START CAPTURING it (currently dropped, see Gaps).
- **`min_extrude_temp`** lives at **`configfile.settings.extruder.min_extrude_temp`** (the parsed-config
  numeric). `configfile.settings` is "the parsed configuration for all loaded Klipper objects." This is a
  STATIC value (config-time) — query it ONCE during the handshake (add `configfile` to a one-shot query,
  NOT the live subscribe — it never changes at runtime). Used only for the hint text "Heat nozzle to N°C".
- **Multi-extruder:** `extruder1.can_extrude` etc. exist the same way; gate per active tool.

### 3. Toolhead position + homed state (MOVE-04 / D-03) — `[CITED: moonraker.readthedocs.io/printer_objects]`
- **Use `gcode_move.gcode_position`** for the user-facing X/Y/Z, NOT `toolhead.position`. Verified field
  semantics:
  - `toolhead.position` = "commanded position" (raw kinematic, includes homing offsets) — internal.
  - `gcode_move.position` = "current position AFTER offsets applied".
  - **`gcode_move.gcode_position` = "the current position WITHOUT any offsets"** — this is what the slicer/
    user sees and what every Klipper UI (Mainsail/KlipperScreen) displays. **This is the MOVE-04 source.**
  `gcode_position` is a 4-element `[X, Y, Z, E]` array (E = extruder; ignore for the jog-pad corners).
- **Homed state:** `toolhead.homed_axes` is a string ("", "xy", "xyz"). Already captured in `PrinterState.homedAxes`.
  Per-axis gating: `'x' in homedAxes` etc. (case — Klipper reports lowercase). The amber Override cell jogs
  unhomed by wrapping the move with `SET_KINEMATIC_POSITION` is **NOT** needed — Klipper allows relative
  moves when homed; the Override path is for the *unhomed* case where you must enable motion. **Verify on
  flox**: on most Klipper configs a relative `G1` with un-homed axes is rejected; the standard escape is to
  home first, or the UI exposes a "force move" that issues `SET_KINEMATIC_POSITION` then a relative move.
  Flag this for live confirmation — the exact Override gcode is the one piece not nailed by docs alone.

### 4. Load/Unload macro presence (EXTR-02 / D-10) — `[CITED: moonraker.readthedocs.io/printer_objects]` + `[VERIFIED: codebase]`
- Configured macros appear in `printer.objects.list` as `gcode_macro <name>`. **Moonraker reports the name
  in LOWERCASE** (docs example: config `[gcode_macro PAUSE]` → object `gcode_macro pause`). Klipper macro
  names are case-INSENSITIVE when invoked (`klipper3d.org`: "MY_MACRO and my_macro evaluate the same").
- ⚠ **PITFALL:** the existing `deriveCapabilities` extracts macro names verbatim via `removePrefix("gcode_macro ")`.
  Detecting `LOAD_FILAMENT`/`UNLOAD_FILAMENT` MUST be **case-insensitive** (`equals(ignoreCase = true)` or
  `.lowercase()`), or it will miss a lowercased object name and wrongly show the "missing macro" popup. The
  popup (D-10) keys off this presence check.
- The gcode to RUN them is just the macro name as a script: `printer.gcode.script` with `script="LOAD_FILAMENT"`.

### 5. Action gcodes — ALL via `printer.gcode.script` — `[CITED: moonraker.readthedocs.io/external_api/printer]`
- **One method runs everything:** `printer.gcode.script` with params `{ "script": "<gcode>" }`, returns `"ok"`.
  Add `GCODE_SCRIPT = "printer.gcode.script"` to `JsonRpcMethods`.
| Action | G-code (script param) | Notes |
|--------|-----------------------|-------|
| Set heater target | `SET_HEATER_TEMPERATURE HEATER=extruder TARGET=200` | Works for any heater object name (`heater_bed`, `heater_generic chamber`). Cleaner than `M104/M140` (which only address nozzle/bed). |
| Cooldown (all off) | `TURN_OFF_HEATERS` | Single gcode turns off EVERY heater — ideal for D-02 Cooldown. |
| Jog relative | `SAVE_GCODE_STATE NAME=dd_jog` ⏎ `G91` ⏎ `G1 X10 F3000` ⏎ `RESTORE_GCODE_STATE NAME=dd_jog` | Wrap in SAVE/RESTORE so jog never leaves the printer in relative mode (matches KlipperScreen practice). Feed in mm/min. |
| Home all | `G28` | |
| Home axis | `G28 X` / `G28 Y` / `G28 Z` | |
| Disable steppers | `M84` | Behind `ConfirmGuard` (MOVE-03). |
| Extrude / retract | `SAVE_GCODE_STATE NAME=dd_ext` ⏎ `M83` ⏎ `G1 E5 F300` ⏎ `RESTORE_GCODE_STATE NAME=dd_ext` | Positive E = extrude, negative = retract. F in mm/min. `M83` = relative extruder. |
| Select tool | `T0` / `T1` (or `ACTIVATE_EXTRUDER EXTRUDER=extruder1`) | `T<n>` is standard; `ACTIVATE_EXTRUDER` is the explicit Klipper form. |
| Load/Unload | `LOAD_FILAMENT` / `UNLOAD_FILAMENT` | Macro names; only when present (case-insensitive check). |

  **Multi-line scripts:** newline-join into ONE `script` string (Klipper executes them in order). A single
  `printer.gcode.script` call per logical action keeps the `CommandDispatcher` in-flight/debounce semantics
  clean (one key, one dispatch).

### 6. Sensible default values (Claude's discretion — verified reasonable)
- **Preset temps** (CONTEXT's example values, all within recommended ranges per Sovol/3DPrinterBee Ender-5
  guides — `[CITED: sovol3d.com, the3dprinterbee.com]`, MEDIUM): **PLA 200/60, PETG 240/80, ABS 245/100,
  TPU 220/50** (nozzle/bed). These are sane defaults; PETG bed 70–80 and ABS bed 100–110 are both common —
  80/100 are safe mid-range picks. Hardcode as a fixed list (D-02). `[ASSUMED]` that the user is fine with
  these exact pairs — surfaced for confirmation, but reasonable.
- **Extrude distance set:** Klipper's `max_extrude_only_distance` defaults to **50 mm** `[CITED: klipper3d.org
  Config_Reference]` — so longer single extrudes than 50 mm are rejected by firmware. The conservative
  slicer-era "extrude no more than 5 mm" is NOT a Klipper hard limit; Mainsail/KlipperScreen default to a
  small set like **1 / 5 / 10 / 50 mm** at **~5 mm/s (300 mm/min)**. Recommend a 6-up distance selector
  (e.g. `1 / 5 / 10 / 25 / 50 / 100` capped at the firmware limit) and a speed selector defaulting to
  **300 mm/min** (5 mm/s) — slow and safe. `[ASSUMED]` exact set — discretion, flag for confirmation.
- **Jog distances:** LOCKED by mockup at 0.1–100 mm (6-up: `0.1 / 1 / 5 / 10 / 50 / 100`).

## Architecture Patterns

### System Architecture Diagram

```
                    Moonraker (Ender 5 Plus, LAN)
                            │  ws JSON-RPC + REST
        ┌───────────────────┴───────────────────────┐
        │  one-shot on connect          live stream  │
        │  server.temperature_store     notify_status_update
        ▼                               ▼
  [JsonRpcClient.request]        [JsonRpcClient.statusUpdates]
        │ per-sensor arrays             │ partial diffs
        │ (backfill, oldest→newest)     ▼
        │                        [PrinterStateReducer]  (+ can_extrude, gcode_position)
        │                               ▼
        │                        [PrinterStateStore]  ── throttled ~4Hz ──┐
        │                          + capabilities (deriveCapabilities)    │
        │                               │                                 │
        ▼                               ▼                                 ▼
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │  TemperatureHolder        MoveHolder           ExtrudeHolder                  │
  │  (rings per sensor +      (jog-pad model +     (can_extrude gate +            │
  │   legend + presets)        homed gating +       distance/speed + tool sel +   │
  │                            gcode_position)       macro presence)              │
  └───────┬───────────────────────┬───────────────────────┬─────────────────────┘
          │ StateFlow              │ StateFlow              │ StateFlow
          ▼                        ▼                        ▼
  TemperatureScreen          MoveScreen               ExtrudeScreen   (Compose: ScreenScaffold,
   │  Focus=values            Focus=3×3 jog pad        Focus/Field per Move template   OutlinedControl,
   │  Field=GraphViewHost──┐   Field=Z row+dist sel    actions + selectors             ScrubberPage,
   │  (N-trace GraphView)  │   Gutter=Home/Disable/Bk  Gutter                          ConfirmGuard)
   │  Gutter=Back/Presets/ │
   │       Cooldown        ▼
   │                 [GraphView]  N pre-alloc Path/Paint, FIXED Y-range, allocation-free onDraw
   │
   └─── every action tap ──► [CommandDispatcher.dispatch(key, "printer.gcode.script", {script})]
                                      │ timeout / in-flight / debounce (PRIM-05)
                                      ▼
                              [JsonRpcClient.request]  ──► Moonraker
```

### Recommended Project Structure
```
ui/temperature/    TemperatureHolder.kt, TemperatureScreen.kt   # mirror ui/printstatus/
ui/move/           MoveHolder.kt, MoveScreen.kt
ui/extrude/        ExtrudeHolder.kt, ExtrudeScreen.kt
state/             PrinterState.kt (+ canExtrude, gcodePosition, minExtrudeTemp),
                   Capabilities.kt (+ loadMacro?/unloadMacro? presence), DeriveCapabilities.kt
render/            GraphView.kt (extend to N traces), GraphViewHost.kt (pass N snapshots + colors)
command/           PrinterCommands.kt (NEW — gcode-script builders, pure + unit-tested)
net/JsonRpc.kt     + GCODE_SCRIPT, + TEMPERATURE_STORE method names
ui/route/TopRoute.kt  Dest enum += Temperature, Move, Extrude
ui/shell/AppDrawer.kt  wire the 3 greyed tiles live
```

### Pattern 1: Per-panel Holder (mirror `PrintStatusHolder`)
**What:** A plain-Kotlin, host-testable class that consumes `store.printerState` + `store.capabilities`,
owns derived `StateFlow`s, and applies explicit capability fallback. NO Compose annotations, NO second
throttle (the store already conflates to ~250 ms — a second throttle compounds latency, the Phase-4 lesson).
**When to use:** All three panels.
```kotlin
// Source: pattern from ui/printstatus/PrintStatusHolder.kt (codebase)
class ExtrudeHolder(scope: CoroutineScope, private val store: PrinterStateStore) {
    private val _vm = MutableStateFlow(ExtrudeVm.EMPTY)
    val vm: StateFlow<ExtrudeVm> = _vm.asStateFlow()
    init {
        scope.launch {
            store.printerState.collect { s ->
                val caps = store.capabilities.value
                val active = "extruder" // or the selected tool
                _vm.value = ExtrudeVm(
                    canExtrude = s.heaters[active]?.let { /* canExtrude */ } ?: false,
                    minExtrudeTemp = store.minExtrudeTemp, // queried once at handshake
                    tools = (0 until caps.extruderCount).map { "T$it" }, // D-09 gate
                    showToolSelector = caps.extruderCount > 1,
                    hasLoadMacro = caps.hasMacroIgnoreCase("LOAD_FILAMENT"), // D-10
                )
            }
        }
    }
}
```

### Pattern 2: Action dispatch as a gcode script (PRIM-05)
**What:** Build the gcode string with a pure helper, dispatch through the session `CommandDispatcher`.
**When to use:** Every move/heat/extrude/home/disable/tool tap.
```kotlin
// Source: command/CommandDispatcher.kt + verified printer.gcode.script contract
fun scriptParams(gcode: String): JsonElement = buildJsonObject { put("script", gcode) }

// in the screen, off a button tap:
dispatcher.dispatch(
    key = "jog_x_pos",                       // unique per control → drives in-flight/disabled state
    method = JsonRpcMethods.GCODE_SCRIPT,    // "printer.gcode.script"
    params = scriptParams(PrinterCommands.jog(axis = "X", mm = +10.0, feedMmMin = 3000)),
)
```

### Pattern 3: N-trace GraphView extension (D-05, the only new render work)
**What:** Generalize the single `linePath`/`linePaint` to `Array<Path>`/`Array<Paint>` (pre-allocated,
one per drawn sensor), keep `rewind()`-per-draw and the allocation-free discipline, and replace the
window-min/max Y auto-range with a **fixed Y-range** (e.g. 0–300 °C, or a stable rounded range derived
once). The shared X axis is a fixed time window (the `temperature_store` length). Pass per-sensor color
from tokens via `GraphViewHost` (heat / accent / **violet — see Gaps**).
**Anti-pattern avoided:** re-deriving Y min/max per frame (G-1's noise amplification) and per-sensor
re-allocation in `onDraw`.

### Anti-Patterns to Avoid
- **Forking `GraphView`** for the multi-trace graph — CONTEXT D-05 forbids it; extend in place so the
  allocation-free contract and the perf gate stay anchored to ONE primitive.
- **A second throttle in a holder** — the store is the only throttle (Phase-4 lesson).
- **Raw `rpc.request` from a panel** — bypasses PRIM-05; every action goes through `CommandDispatcher`.
- **Subscribing to `configfile`/`temperature_store` live** — both are one-shot reads (config is static;
  the store is a backfill seed, not a live stream — live points come from `notify_status_update`).
- **Case-sensitive macro detection** — Moonraker lowercases object names; use `ignoreCase`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Numeric value entry | A custom keypad/dialog | `ScrubberPage` | Locked design component; tap-to-set already fixed (G-3); UI LAW forbids keyboard. |
| Destructive confirm | A custom dialog/hold gesture | `ConfirmGuard` | Opaque scrim fixed (G-4); PRIM-03 the consistent guard. |
| Timeout/debounce/in-flight | Per-button `launch{}` + flags | `CommandDispatcher` | PRIM-05; redacted failures, uniform busy state. |
| Live line graph | A new Compose Canvas / chart lib | extend `GraphView` | ADR 0001 routed high-churn render to Views; perf gate anchored here. |
| Throttling high-rate state | `sample`/`debounce` in the holder | `PrinterStateStore` (already conflates) | Second throttle compounds latency (Phase-4 lesson). |
| Capability gating | Ad-hoc `if printer has X` in the UI | `deriveCapabilities` + holder fallback | Pure, unit-tested, re-run every reconnect. |
| Running multiple gcodes | N separate dispatches | one newline-joined `script` string | One dispatch = clean in-flight/debounce; Klipper runs in order. |

**Key insight:** Phase 5 is 90% wiring existing primitives to verified Moonraker calls. The temptation to
"just add a chart library" or "pop a number dialog" directly violates the locked design system and the
perf discipline. The only genuinely new code is the `temperature_store` backfill + N-trace GraphView, and
the pure capability/state additions.

## Runtime State Inventory

> Not a rename/refactor/migration phase — greenfield panel additions. Section omitted by trigger rule.
> (No stored data, live-service config, OS-registered state, secrets, or build artifacts carry a string
> that this phase changes.)

## Common Pitfalls

### Pitfall 1: `gcode_position` vs `toolhead.position` (MOVE-04)
**What goes wrong:** Displaying `toolhead.position` shows offset-laden internal coordinates that don't
match what the user/slicer expects; numbers look "wrong" by the homing offset.
**Why it happens:** `toolhead.position` is the commanded kinematic position (with offsets);
`gcode_move.gcode_position` is the offset-stripped user-facing value.
**How to avoid:** Capture and display `gcode_move.gcode_position`. Add it to `PrinterState` (the reducer
already reads `gcode_move` for factors — add the array). **Verify on flox** the displayed XYZ matches
Mainsail/the printer's own readout.
**Warning signs:** Position off by a constant per axis after homing/offset.

### Pitfall 2: Case-sensitive macro detection (EXTR-02 / D-10)
**What goes wrong:** `LOAD_FILAMENT` configured but the missing-macro popup still fires.
**Why it happens:** Moonraker reports `gcode_macro load_filament` (lowercase); a `== "LOAD_FILAMENT"` check fails.
**How to avoid:** Compare case-insensitively. Add a `Capabilities.hasMacro(name, ignoreCase=true)` helper.
**Warning signs:** Popup on a printer that clearly has the macro.

### Pitfall 3: Dropping `can_extrude` in the reducer (EXTR-04)
**What goes wrong:** The cold-extrude gate never enables/disables because `PrinterState` doesn't carry it.
**Why it happens:** The current `HeaterState` only models temperature/target/power; `can_extrude` lives on
the `extruder` object but isn't captured.
**How to avoid:** Extend the reducer to read `extruder.can_extrude` (and per-tool). Decide: store on a new
`ExtruderState` or a field — keep it pure and unit-test the boolean transition at the min-extrude-temp boundary.
**Warning signs:** Extrude buttons always enabled (or always disabled) regardless of nozzle temp.

### Pitfall 4: N-trace fill-rate on the Adreno 320 (TEMP-04 / D-06)
**What goes wrong:** More traces = more stroke + (if filled) more translucent fill = higher GPU composite
cost; could breach the ~66 ms p95 sparse-redraw bound.
**Why it happens:** The Phase-3 gate measured ONE trace (50.1 ms p95, isolated). The full Temperature screen
composites N traces + Focus value chrome + gutter.
**How to avoid:** Pre-allocate all N paths/paints; keep `onDraw` allocation-free. Consider drawing the
area-fill only for the primary trace (or none) for multi-trace to bound fill cost — but **MEASURE, don't
assume** (D-06). Re-run the two-part gate on the FULL screen on flox.
**Warning signs:** Frozen frames > 0, or p95 > ~66 ms during a live heat ramp with multiple heaters active.

### Pitfall 5: Unhomed jog rejection (MOVE-03 Override cell)
**What goes wrong:** The amber Override cell taps but Klipper rejects the relative move ("Must home axis first").
**Why it happens:** Klipper refuses moves on un-homed axes unless `SET_KINEMATIC_POSITION` (or force-move)
establishes a position first.
**How to avoid:** Confirm the exact Override gcode on flox. The likely path is `SET_KINEMATIC_POSITION
X=<cur> Y=<cur> Z=<cur>` (or a `FORCE_MOVE`-based macro) then the relative `G1`. This is the one action not
fully nailed by docs — **flag for live confirmation** and keep it behind the explicitly amber Override affordance.
**Warning signs:** Override tap produces a `notify_gcode_response` error instead of motion.

## Code Examples

### Set a heater target (TEMP-02)
```kotlin
// Source: verified printer.gcode.script contract (moonraker.readthedocs.io/external_api/printer)
fun setHeater(heater: String, target: Int) = "SET_HEATER_TEMPERATURE HEATER=$heater TARGET=$target"
// dispatch(key="set_$heater", GCODE_SCRIPT, scriptParams(setHeater("extruder", 200)))
```

### Apply a preset / cooldown (TEMP-03)
```kotlin
// Preset = set each heater; Cooldown = one gcode for all heaters.
fun applyPreset(nozzle: Int, bed: Int) =
    "SET_HEATER_TEMPERATURE HEATER=extruder TARGET=$nozzle\n" +
    "SET_HEATER_TEMPERATURE HEATER=heater_bed TARGET=$bed"
const val COOLDOWN = "TURN_OFF_HEATERS"
```

### Relative jog, state-safe (MOVE-01)
```kotlin
fun jog(axis: String, mm: Double, feedMmMin: Int) =
    "SAVE_GCODE_STATE NAME=dd_jog\nG91\nG1 $axis$mm F$feedMmMin\nRESTORE_GCODE_STATE NAME=dd_jog"
```

### Extrude / retract, state-safe (EXTR-01)
```kotlin
fun extrude(mm: Double, feedMmMin: Int) = // negative mm = retract
    "SAVE_GCODE_STATE NAME=dd_ext\nM83\nG1 E$mm F$feedMmMin\nRESTORE_GCODE_STATE NAME=dd_ext"
```

### One-shot history backfill (TEMP-04)
```kotlin
// Source: server.temperature_store contract. Call once after Connected.
val store = rpc.request("server.temperature_store").jsonObject   // { sensor: { temperatures:[...], ... } }
store["extruder"]?.jsonObject?.get("temperatures")?.jsonArray
    ?.map { it.jsonPrimitive.float }     // index 0 = OLDEST → push oldest→newest into the ring
    ?.forEach { ring.push(it) }
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `M104`/`M140` for temps | `SET_HEATER_TEMPERATURE HEATER=… TARGET=…` | Klipper-native | Works for arbitrary heater names (chamber/generic), not just nozzle/bed. |
| Window min/max Y auto-range (Phase-4 sparkline) | Fixed/stable Y-range + history backfill | This phase (G-1 fix) | Steady temps no longer noise-fill the panel; full graph on connect. |
| `toolhead.position` for display | `gcode_move.gcode_position` | This phase | User-facing coordinates match the slicer/Mainsail. |

**Deprecated/outdated:** none relevant — the stack is current and pinned.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Preset temps PLA 200/60, PETG 240/80, ABS 245/100, TPU 220/50 are acceptable | Default values | Low — all within recommended ranges; user may tweak; easily changed (hardcoded list). |
| A2 | Extrude distance set `1/5/10/25/50/100` mm + default 300 mm/min speed | Default values | Low — within Klipper's 50 mm default `max_extrude_only_distance` for the small picks; >50 mm picks may be firmware-rejected (surface a toast). Discretion. |
| A3 | The Override-cell unhomed-jog gcode (`SET_KINEMATIC_POSITION` then relative move) | Pitfall 5 / API §3 | Medium — exact gcode not confirmed by docs; MUST verify on flox or the Override does nothing. |
| A4 | Drawing area-fill for only the primary trace (or none) keeps multi-trace within the perf bound | Pitfall 4 | Medium — perf, not correctness; D-06 mandates a real re-measure regardless. |
| A5 | `temperature_store` keys exactly match heater object names for the drawn sensors | API §1 | Low — docs confirm per-sensor keys; verify chamber/generic naming on flox. |

## Open Questions

1. **Exact Override-cell gcode for unhomed jog (MOVE-03/D-03).**
   - What we know: Klipper rejects moves on un-homed axes; `SET_KINEMATIC_POSITION` / force-move is the escape.
   - What's unclear: the precise, safe command sequence the amber Override should issue on this printer.
   - Recommendation: confirm live on flox; keep behind the explicit amber Override; surface gcode errors via `SeverityToast`.

2. **`violet` (chamber) trace color is undefined in the token system (see Gaps below).**
   - What we know: README §9 says chamber=violet; `ThemeTokens` has no `violet` role (only an example custom-accent hex).
   - What's unclear: whether to add a `violet` semantic token (dark/light/custom values) or cap v1 at 2 traces (nozzle/bed) since the Ender 5 Plus has no chamber sensor.
   - Recommendation: planner adds a `violet` (or generic `trace3`) token to `ThemeTokens` + both palettes, OR explicitly scopes v1 to 2 traces and defers chamber. Either is a small, decidable task — must not be left as a hardcoded hex (THEME-01 violation).

3. **Where to store `can_extrude` / `min_extrude_temp` on the state model.**
   - What we know: `can_extrude` is live (per extruder); `min_extrude_temp` is static config.
   - Recommendation: add `can_extrude` to a per-extruder model (or a `Map<String, Boolean>` on `PrinterState`); query `min_extrude_temp` once at handshake and stash on the store (like a capability) — not in the throttled hot path.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Live Moonraker (Ender 5 Plus) | UAT of all 12 reqs, API contract verify | ✓ (proven Phases 2–4) | Moonraker v0.13-class | none — live UAT is mandatory (D-06, mock-vs-reality lesson) |
| Real flox tablet (Adreno 320) | Perf gate re-measure (D-06) | ✓ (`adb 0a64b42e`, LineageOS 18.1 / API 30) | — | none — emulators lie about old-GPU perf |
| Windows-side Gradle build (`E:\Android\gw.bat`) | Build/install | ✓ | JDK 21 / SDK 35 | none |
| `tools/gfxinfo-parser/parse_framestats.py` | p95 capture | ✓ (Phase 3) | — | raw `dumpsys gfxinfo` |

**Missing dependencies with no fallback:** none — all UAT/perf infra exists from prior phases.

## Validation Architecture

> nyquist_validation assumed enabled (no `.planning/config.json` override observed).

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 host unit tests (pure reducers/holders) + AndroidX instrumented tests (on-device gates), Macrobenchmark/`gfxinfo` for perf (ADR 0001) |
| Config file | Gradle (`app/build.gradle.kts`); existing `state/*Test`, `GraphDownsampleTest`, `ShellPresenceTest`, `RenderBenchmark` |
| Quick run command | `cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon"` |
| Full suite command | `cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest :app:connectedReleaseAndroidTest --no-daemon"` + on-device gfxinfo capture |

### Phase Requirements → Test Map
| Req | Behavior | Test Type | Automated / Gate | File |
|-----|----------|-----------|------------------|------|
| TEMP-01 | heater current/target surfaced | unit (holder) | `TemperatureHolderTest` | ❌ Wave 0 |
| TEMP-02 | set-target gcode built correctly | unit (pure builder) | `PrinterCommandsTest` (asserts `SET_HEATER_TEMPERATURE …`) | ❌ Wave 0 |
| TEMP-03 | preset → N dispatches; cooldown = `TURN_OFF_HEATERS` | unit | `PrinterCommandsTest` | ❌ Wave 0 |
| TEMP-04 (backfill) | store arrays → ring oldest→newest; fixed Y-range | unit (pure mapper) | `TemperatureStoreBackfillTest`, extend `GraphDownsampleTest` | ❌ Wave 0 |
| TEMP-04 (render perf) | multi-trace full screen clears two-part gate | **on-device gfxinfo** | flox capture + parser; liveness + p95 ≤ ~66 ms (D-06) | live UAT |
| MOVE-01 | jog gcode (SAVE/G91/G1/RESTORE) | unit | `PrinterCommandsTest` | ❌ Wave 0 |
| MOVE-02 | home all / per-axis gcode | unit | `PrinterCommandsTest` | ❌ Wave 0 |
| MOVE-03 | disable routes through ConfirmGuard → `M84` | unit (holder) + on-device UAT | `MoveHolderTest` + live | ❌ Wave 0 |
| MOVE-04 | position from `gcode_position`; homed gating | unit (reducer) + on-device | `PrinterStateReducerTest` (gcode_position), `DeriveCapabilitiesTest` | extend existing |
| EXTR-01 | extrude/retract gcode incl. feed/sign | unit | `PrinterCommandsTest` | ❌ Wave 0 |
| EXTR-02 | macro presence (case-insensitive) + popup | unit | `DeriveCapabilitiesTest` (lowercase name) | extend existing |
| EXTR-03 | tool selector gated on extruderCount>1 | unit (holder) | `ExtrudeHolderTest` | ❌ Wave 0 |
| EXTR-04 | can_extrude boolean gate at temp boundary | unit (reducer) | `PrinterStateReducerTest` (can_extrude) | ❌ Wave 0 |
| PRIM-05 reuse | in-flight/timeout/debounce on a real action | on-device UAT | end-to-end on flox | live UAT |

**Provable as pure unit tests (off-hardware):** all gcode builders (`PrinterCommands`), the
`temperature_store`→series mapper, `can_extrude`/`gcode_position` reducer additions, case-insensitive
macro detection, capability-gating derivation, holder view-model fallback.

**Require live-printer / on-device UAT (mock cannot prove — mock-vs-reality lesson):**
- The actual Moonraker contract for `server.temperature_store`, `can_extrude`, `min_extrude_temp`,
  `gcode_position` (harden the FakeWebSocket to the REAL shapes captured from flox — do NOT let a lenient
  mock pass).
- PRIM-05 in-flight/timeout/debounce behavior under real network latency.
- End-to-end **preheat → extrude → jog → home** on the real Ender 5 Plus.
- The amber Override unhomed-jog path (Open Question 1).
- **The D-06 perf re-measure** (below).

### Sampling Rate
- **Per task commit:** `:app:testReleaseUnitTest` (pure tests, <30 s).
- **Per wave merge:** full unit suite + relevant `connectedReleaseAndroidTest`.
- **Phase gate:** full suite green + the D-06 on-device perf capture + live preheat/extrude/jog UAT on flox.

### Two-part Adreno-320 perf gate re-measurement plan (D-06 — do NOT grandfather 50.1 ms)
Carry the Phase-3 harness verbatim (`tools/gfxinfo-parser/parse_framestats.py`, RELEASE build debug-signed,
real flox `0a64b42e`), but target the **full Temperature screen** (N-trace graph + Focus values + gutter),
not the isolated ring+graph scene:
1. **LIVENESS GATE (hard floor):** allocation-free `onDraw` (extend the Pitfall-4 discipline to N paths/paints),
   NO animation loop (redraw count tracks the ~3 Hz feed, not Choreographer), **ZERO frozen frames** (0 > 700 ms).
2. **SPARSE-REDRAW LATENCY GATE:** **p95 ≤ ~66 ms** for value-driven redraws, measured during a LIVE multi-heater
   heat ramp (worst case: more traces actively moving). Capture via `dumpsys gfxinfo … framestats` parsed by the
   existing parser.
3. **Re-open triggers (from 03-PERF-RESULTS FINAL VERDICT):** if the ~24 ms composite floor proves unstable,
   if the full screen composites materially heavier (p95 breaches ~66 ms), or if the ~3 Hz cadence assumption
   breaks → criterion re-opens and the budget is re-derived against the real measured floor. Document the
   capture in a `05-PERF-RESULTS.md` mirroring the Phase-3 file.

## Security Domain

> `security_enforcement` assumed enabled (absent = enabled).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | no (handled in Phase 2 — oneshot token / API key on the socket) | — |
| V3 Session Management | no | — |
| V4 Access Control | no (LAN appliance, single trusted user) | — |
| V5 Input Validation | yes | gcode built from a FIXED set of presets/distances/speeds — never free-text. Scrubber emits bounded numerics. Clamp targets/distances to sane ranges before building the script string. |
| V6 Cryptography | no | — |

### Known Threat Patterns for this surface
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Double-tap re-issues a destructive action (disable steppers mid-print) | Tampering | `CommandDispatcher` debounce + in-flight (PRIM-05); `ConfirmGuard` on disable (MOVE-03). |
| Hung/dropped action wedges a control | DoS | `CommandDispatcher` timeout removes the in-flight key in `finally` (existing). |
| Server-provided text (gcode_response errors) rendered as UI | Injection (XSS-analog) | Render verbatim as plain text via `SeverityToast`; never interpret (existing house rule). |
| gcode string built from user numeric input | Tampering | Bounded numerics from scrubber/selectors only; clamp before formatting; no string concatenation of free text. |
| Cold extrude damaging the printer | (Safety, not classic STRIDE) | `can_extrude` gate (EXTR-04/D-07) — disable + explain, no failed taps. |

## Sources

### Primary (HIGH confidence)
- moonraker.readthedocs.io/en/latest/external_api/server/ — `server.temperature_store` shape, 1200@1Hz, `include_monitors`.
- moonraker.readthedocs.io/en/latest/printer_objects/ — `extruder.can_extrude`, `configfile.settings`, `toolhead.homed_axes`/`position`, `gcode_move.gcode_position`/`position`, `gcode_macro` lowercase naming.
- moonraker.readthedocs.io/en/latest/external_api/printer/ — `printer.gcode.script` (script param), `objects.list`/`objects.query`.
- Codebase (`works.mees.dinghy.*`) — verified existing primitives: `GraphView`, `CommandDispatcher`, `PrinterState(Reducer/Store)`, `DeriveCapabilities`, `PrintStatusHolder`, `MoonrakerSession` handshake, `JsonRpcMethods`, `ScreenScaffold`, `GraphViewHost`, `ThemeTokens` (no `violet`).
- `03-PERF-RESULTS.md` — the two-part Adreno-320 gate + re-open triggers (D-06).
- `04-HUMAN-UAT.md` — gap G-1 the TEMP-04 backfill closes.

### Secondary (MEDIUM confidence)
- klipper3d.org/Config_Reference.html — `max_extrude_only_distance` default 50 mm; `min_extrude_temp`.
- klipper3d.org/Command_Templates.html, G-Codes.html — macro case-insensitivity, `SET_HEATER_TEMPERATURE`, `TURN_OFF_HEATERS`, relative E (`M83`).
- sovol3d.com, the3dprinterbee.com — Ender-5 Plus PLA/PETG/ABS temperature ranges (preset reasonableness).

### Tertiary (LOW confidence)
- KlipperScreen/Mainsail manual-extrude defaults (~50 mm @ 5 mm/s) — community convention, not a hard spec; informs discretion only.

## Metadata

**Confidence breakdown:**
- Moonraker field paths / gcode calls: HIGH — verified against official docs + existing codebase contract.
- Architecture / primitive reuse: HIGH — directly read from the codebase; patterns proven Phases 2–4.
- Default values (presets/distances/speed): MEDIUM — within recommended ranges; flagged `[ASSUMED]` for user confirmation.
- Override-cell unhomed gcode: LOW-MEDIUM — needs live confirmation on flox.
- Multi-trace perf headroom: MEDIUM — physics suggests it fits, but D-06 mandates a real re-measure.

**Research date:** 2026-05-31
**Valid until:** ~2026-06-30 (stable APIs; the only fast-moving piece is the on-device perf number, which is re-measured this phase regardless).
