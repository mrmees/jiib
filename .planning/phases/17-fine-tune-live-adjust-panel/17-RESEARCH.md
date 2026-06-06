# Phase 17: Fine-Tune / Live-Adjust Panel - Research

**Researched:** 2026-06-06
**Domain:** Klipper live-tuning g-code + Moonraker printer-object readback + Compose/Views value-tile UI (existing dinghy spine)
**Confidence:** HIGH (every command param, status-field shape, and code seam verified against official Klipper docs + the in-repo live-probed capability matrix + the actual source files)

## Summary

Phase 17 is **almost entirely a wiring + new-screen exercise on top of machinery that already exists**. The command-dispatch layer (`CommandRegistry`/`CommandDispatcher`), the immediate-dispatch + state-flip-confirm + in-flight-busy pattern (`ExtrudeScreen`/`MoveHolder`), the pure clamp-before-format g-code builders (`PrinterCommands`), the null-safe diff-merge reducer (`PrinterStateReducer`), the one-shot `configfile` handshake read (`MoonrakerSession`), the capability-object gating idiom (`CalibrationGate.hasObject`), and the per-icon vector-drawable rendering (`painterResource`) are all in place and battle-proven on the real flox + live Ender 5/3. Phase 17 adds: ~8 new `gcode()` registry specs, ~6 new pure builders, ~7 new reducer field-reads + ~5 new config-baseline one-shot reads, a `Dest.FineTune` route + AppShell arm wiring off the existing `PrintStatusControlAction.Tune` stub, and 3–4 new Compose screens (Hub, Motion, Extrusion, FW-Retraction) built from a shared C2-derived value tile.

All four "hardest open questions" resolve cleanly and concretely (see the dedicated sections): the Klipper command params are confirmed verbatim against the official G-Codes reference; the printer-object readback fields are confirmed against the official Status Reference **and** the in-repo live-probe matrix; the config-baseline reset paths map onto the existing `configfile.settings.<section>` one-shot seam; and the capability gates are plain `Capabilities.hasObject` predicates. **One material reality check:** neither dev printer (E5/E3) has `[firmware_retraction]` configured, so the FW-Retraction mini-screen is **build-blind** (gates itself OFF, exactly like `QUAD_GANTRY_LEVEL`) and **cannot be live-UAT'd on the dev hardware** — plan it as code-reasoned + fixture-tested, not on-device-gated.

**Primary recommendation:** Build Fine-Tune as "another Move/Extrude" — same `Holder(scope, store)` + `Screen(container, holder, onBack)` template, same `dispatchCommand` funnel, same in-flight-busy disable — but render compact **value tiles** (icon · name · live value · `−`/`+`) instead of opening a ScrubberPage, lock the **whole group screen busy** while any tile dispatch is in flight (D-15), and long-press the value to reset (D-16). Add the new commands as `gcode()` specs and the new readback fields as null-safe reducer walks, mirroring the exact patterns shown below.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Live-adjust g-code dispatch (M220/M221/SET_VELOCITY_LIMIT/SET_PRESSURE_ADVANCE/M106/SET_RETRACTION) | Command layer (`CommandRegistry` + `PrinterCommands` + `CommandDispatcher`) | — | All printer mutations funnel through the existing dispatcher (debounce, in-flight, timeout, redacted-failure). New commands are `gcode()` specs; never raw rpc from the UI. |
| Live value readback (current speed_factor, max_accel, pressure_advance, fan.speed, …) | State layer (`PrinterStateReducer` → `PrinterState`) | Holder (`FineTuneHolder`) | Reducer deep-merges `notify_status_update` diffs into immutable `PrinterState`; the holder transforms it into a tile view-model. State-flip confirmation is just observing the reduced value change. |
| Config-baseline (reset targets) | Session handshake one-shot (`MoonrakerSession` configfile read → `PrinterStateStore` StateFlows) | Holder | Baselines are static; read ONCE at handshake from `configfile.settings.<section>` (no live subscribe), exactly like `minExtrudeTemp`. |
| Capability gating (which tiles show) | Capability layer (`Capabilities.hasObject` / `hasComponent`) | Holder/Screen | Object-presence predicate, re-derived on every reconnect. No central predicate evaluator exists — screens/holders gate manually (the established idiom). |
| Tile UI + interaction (±nudge, long-press reset, busy-lock) | Compose UI (`FineTuneHubScreen`/`MotionScreen`/`ExtrusionScreen`/`FwRetractionScreen`) | Design system (`ScreenScaffold`, `OutlinedControl`, `painterResource` icons, `fsSp` scale) | New screens follow Focus/Field/Gutter; tiles are a new shared `@Composable`. |
| Navigation entry | Shell (`Dest` enum + `AppShell` + `PrintStatusScreen` Tune action) | — | Wire the existing `PrintStatusControlAction.Tune -> Unit` stub to a new `Dest.FineTune`. |

## User Constraints (from CONTEXT.md)

> The FULL set of locked decisions is in `17-CONTEXT.md` (D-01..D-21) — read it. Summarized here; **CONTEXT.md is authoritative, do not re-litigate.**

### Locked Decisions (D-01..D-21 — verbatim summary)
- **D-01/D-02:** Lean failure-mode tuner, two categories — **Motion** (moves the head) vs **Extrusion** (affects filament). **Always available when connected** (not print-gated); capability-gating hides controls the printer doesn't expose.
- **Motion (5 tiles):** D-03 Speed % `M220 S<n>` ↔ `gcode_move.speed_factor` (step 5%); D-04 Max velocity `SET_VELOCITY_LIMIT VELOCITY=<n>` ↔ `toolhead.max_velocity` (step 10 mm/s); D-05 Max accel `SET_VELOCITY_LIMIT ACCEL=<n>` (or M204, planner verifies) ↔ `toolhead.max_accel` (step 100 mm/s²); D-06 Minimum cruise ratio `SET_VELOCITY_LIMIT MINIMUM_CRUISE_RATIO=<n>` ↔ `toolhead.minimum_cruise_ratio` (step 5 pct-points); D-07 Square-corner velocity `SET_VELOCITY_LIMIT SQUARE_CORNER_VELOCITY=<n>` ↔ `toolhead.square_corner_velocity` (step 0.1 mm/s).
- **Extrusion:** D-08 Flow % `M221 S<n>` ↔ `gcode_move.extrude_factor` (step 1%, range ≈50–150%); D-09 Pressure advance `SET_PRESSURE_ADVANCE ADVANCE=<n>` ↔ `extruder.pressure_advance` (step 0.001, single-extruder v1); D-10 Smooth time `SET_PRESSURE_ADVANCE SMOOTH_TIME=<n>` ↔ `extruder.smooth_time` (step 0.01 s, own tile); D-11 Part-cooling fan `M106 S<0..255>` ↔ `fan.speed` (part-cooling `fan` ONLY, 0–100%, step 5%); D-12 Firmware retraction = own mini-screen gated on `[firmware_retraction]`, four `SET_RETRACTION` controls (retract length/unretract extra length step 0.1 mm; retract/unretract speed step 1 mm/s), **no Z-hop**.
- **Interaction (D-13/D-14/D-15):** value tiles directly on the group screen (no per-control detail page); pure nudge, one command per tap, dispatch immediately; **tapping the value does nothing**; no long-press repeat; no Apply/Cancel, no ConfirmGuard; confirm by printer-object **state flip**; **busy model = lock the WHOLE group screen** while any command is in flight (busy/disabled tile styling is sufficient).
- **Reset (D-16):** long-press the displayed value = reset that tuner, immediate no-prompt. Reset = config baseline (from `printer.configfile.settings.<section>`), NOT Dinghy defaults. Speed/flow are protocol-neutral resets to 100% (`M220 S100`/`M221 S100`). Part-cooling fan resets to config/default, **never assumed off**.
- **Icons (D-17):** per-control glyphs are CHOSEN and confirmed in the staging note + folded into `img/material-icon-bucket.json` (82 icons; all 13 P17 names verified PRESENT — see audit). Render via per-icon vector drawables + `painterResource`, NOT the Material Symbols font. Generic `add`/`remove` for ±; `keyboard_return` for Back. FW sub-control glyphs may reuse `input_circle`/`output_circle`/`sprint` (owner confirms if distinct glyphs wanted).
- **D-18:** increments are fixed/app-defined this phase; customization deferred.
- **D-19:** build the value tile as a clean shared-ready component but **wire it ONLY in Fine-Tune** this phase. Tile is **C2-derived 3-cell** (arrows do the action, tap inert, long-press = reset) — **intentionally diverges** from THEMING C2 mode-(b)'s "tap = adjust". Full C2 component + Move retrofit + Move-Z vertical (C3) stay deferred.
- **D-20:** temps remain links not setters; hub carries NO summary values; whether group screens carry a small readout strip is planner discretion within Field grammar.
- **D-21:** wire from the stubbed `PrintStatusControlAction.Tune` to open Fine-Tune Hub; add `Dest.FineTune` + AppShell arm (and/or a drawer tile — planner's call, conform to Phase-16 shell).

### Claude's Discretion (research resolves these below)
- Exact **ranges + clamping** per tuner → see [Command Param + Range Reference](#command-param--range-reference-d-claudes-discretion).
- Exact **config-baseline paths** per reset → see [Config-Baseline Reset Paths](#config-baseline-reset-paths-the-reset-source-of-truth). **Part-fan baseline is genuinely fuzzy** — resolved below.
- Motion group expanded vs "advanced" disclosure (5 tiles touch-friendly portrait AND landscape) → see [Layout](#architecture-patterns).
- Precise capability-gate predicates → see [Capability-Gate Predicates](#capability-gate-predicates-d-claudes-discretion).
- Material Symbols ligature resolution + fallbacks → resolved: all PRESENT in bucket; export to `res/drawable/*.xml`.

### Deferred Ideas (OUT OF SCOPE)
- Z babystep (shipped in Phase 16); mid-print object exclusion (`EXCLUDE_OBJECT`) → v2; temp setters → Phase 5; generic/aux fans + LEDs + output pins + enclosure → Phase 18; Pause/Resume → Phase 7; user-customizable increments → future Settings; the C2 shared-component build + Move-Z vertical-layout rework → deferred todos.

## Phase Requirements

> No pre-existing TUNE-* IDs. The requirement surface = the four ROADMAP Success Criteria + CONTEXT D-01..D-21. Suggested TUNE-* IDs the planner can assign:

| ID (suggested) | Description | Research Support |
|----|-------------|------------------|
| TUNE-01 | Wire `PrintStatusControlAction.Tune` → Fine-Tune Hub via `Dest.FineTune` + AppShell arm | [Navigation Wiring](#integration-points) — exact stub at `PrintStatusScreen.kt:260`; `Dest` enum at `TopRoute.kt:37` |
| TUNE-02 | Motion group: 5 capability-gated value tiles (speed/velocity/accel/min-cruise/SCV), ±nudge immediate dispatch, state-flip confirm | [Command + Param Reference](#command-param--range-reference-d-claudes-discretion); [Reducer additions](#reducer-additions-printerstate--printerstatereducer) |
| TUNE-03 | Extrusion group: flow/PA/smooth-time/part-fan tiles + FW-retraction entry-if-configured | same |
| TUNE-04 | FW-Retraction mini-screen (4 tiles), gated on `[firmware_retraction]` | [Capability gates](#capability-gate-predicates-d-claudes-discretion); **build-blind — not on dev printers** |
| TUNE-05 | Long-press value = reset to config baseline (speed/flow→100%; fan→config/last; others→`configfile.settings.<section>`) | [Config-Baseline Reset Paths](#config-baseline-reset-paths-the-reset-source-of-truth) |
| TUNE-06 | Whole-group busy-lock while any tile dispatch is in flight (D-15) | [Busy-lock pattern](#pattern-2-whole-group-busy-lock-d-15) |
| TUNE-07 | Per-icon vector drawables exported for the chosen glyphs + generic ±/Back | [Icon audit](#package-legitimacy--asset-audit) |

## Standard Stack

**No new external dependencies.** This phase is built entirely on the existing, locked stack (Kotlin 2.1.x, Compose BOM 2026.05, OkHttp/Retrofit, kotlinx.serialization, Coroutines/Flow — all per dinghy CLAUDE.md). Zero `npm`/Gradle additions. The only "assets" added are project-local vector drawable XMLs.

### Reused in-repo components (the real "stack" for this phase)
| Component | Path | Purpose | Pattern to mirror |
|-----------|------|---------|-------------------|
| `CommandRegistry` | `command/CommandRegistry.kt` | Register new `gcode()` specs | the `babystepZ`/`extrude` specs (lines 379–531) |
| `PrinterCommands` | `command/PrinterCommands.kt` | Pure clamp-before-format builders | `setGcodeOffsetZAdjust` / `jog` (clamp + `Locale.US` format) |
| `CommandDispatcher` + `dispatch(spec, args)` | `command/CommandDispatcher.kt`, `CommandDispatchExtensions.kt` | Debounce/in-flight/timeout/redacted-failure | `ExtrudeScreen.dispatchCommand` funnel (lines 168–171) |
| `PrinterStateReducer` | `state/PrinterStateReducer.kt` | Null-safe diff-merge of new readback fields | the `toolhead`/`gcode_move`/`extruder` blocks (lines 101–114, 194–210) |
| `PrinterState` | `state/PrinterState.kt` | New nullable readback fields | `gcodeZOffset` / `speedFactor` |
| `MoonrakerSession` configfile read | `net/MoonrakerSession.kt:490–527` | New config-baseline one-shots | `setMinExtrudeTemp` / `setScrewsTiltConfig` |
| `PrinterStateStore` one-shot StateFlows | `state/PrinterStateStore.kt:108–128` | Expose baselines to holder | `minExtrudeTemp` / `screwsTiltConfig` |
| `Capabilities.hasObject` | `state/Capabilities.kt:52` | Tile gating | `CalibrationGate.calibrationSupported` |
| `ExtrudeHolder`/`MoveHolder` | `ui/extrude/`, `ui/move/` | `Holder(scope, store)` template | combine/collect → `MutableStateFlow<Vm>` |
| `ScreenScaffold`, `OutlinedControl`, `MaterialSymbol`/`painterResource` | `designsystem/` | Focus/Field/Gutter + tiles | `ExtrudeScreen` |
| `Dest` + `AppShell` | `ui/route/TopRoute.kt`, `ui/shell/AppShell.kt` | Route + nav arm | existing per-Dest arms |

### Alternatives Considered (g-code transport for accel)
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `SET_VELOCITY_LIMIT ACCEL=<n>` (D-05) | `M204 S<n>` | Both set max_accel. `SET_VELOCITY_LIMIT` is the single coherent command for ALL four motion-limit fields (velocity/accel/min-cruise/SCV) and reads back identically into `toolhead.*`. **Recommendation: use `SET_VELOCITY_LIMIT ACCEL=` for consistency** — one command family, one readback object, no M204 P/T ambiguity. `M204 S` is a fine fallback but adds a second mental model for no benefit. |

## Package Legitimacy / Asset Audit

> No external packages installed this phase. The "audit" is the **icon-asset provenance** check (D-17), since the only added assets are project-local vector drawables exported from owner-curated Material Symbols.

All 13 P17 glyph names were verified PRESENT in the canonical owner-maintained registry `img/material-icon-bucket.json` (82 bookmarks, version-stamped) via direct lookup:

| Glyph | In bucket? | Drawable exists in `res/drawable/`? | Action |
|-------|-----------|-------------------------------------|--------|
| `speed` | ✅ PRESENT | ❌ | export `.xml` |
| `arrow_shape_up_stack_2` | ✅ PRESENT (owner-confirmed REAL) | ❌ | export `.xml` |
| `sprint` | ✅ PRESENT | ❌ | export `.xml` |
| `directions_boat` | ✅ PRESENT | ❌ | export `.xml` |
| `rounded_corner` | ✅ PRESENT | ❌ | export `.xml` |
| `output_circle` | ✅ PRESENT | ❌ | export `.xml` |
| `text_select_move_forward` | ✅ PRESENT (owner-confirmed REAL) | ❌ | export `.xml` |
| `avg_time` | ✅ PRESENT | ❌ | export `.xml` |
| `mode_fan` | ✅ PRESENT | ❌ | export `.xml` |
| `input_circle` | ✅ PRESENT | ❌ | export `.xml` |
| `add` | ✅ PRESENT | ✅ `add.xml` | reuse |
| `remove` | ✅ PRESENT | ✅ `remove.xml` | reuse |
| `keyboard_return` | ✅ PRESENT | ❌ | export `.xml` |

**Disposition:** 11 new vector drawables to export (`add`/`remove` already exist). Source the SVGs from the Material Symbols Outlined set the bucket bookmarks reference; convert to Android vector XML. `[VERIFIED: img/material-icon-bucket.json — direct membership check this session]` for presence; the SVG→XML export is a mechanical asset task, not a dependency.

## Command Param + Range Reference (D, Claude's Discretion)

**All command params confirmed VERBATIM** `[CITED: klipper3d.org/G-Codes.html]` (fetched 2026-06-06). The exact documented syntax:

| Tuner (D#) | Command (exact) | Param spelling | Notes |
|------------|-----------------|----------------|-------|
| Speed % (D-03) | `M220 S<percent>` | `S` | percent (100 = no override) |
| Flow % (D-08) | `M221 S<percent>` | `S` | percent |
| Max velocity (D-04) | `SET_VELOCITY_LIMIT VELOCITY=<value>` | `VELOCITY` | mm/s |
| Max accel (D-05) | `SET_VELOCITY_LIMIT ACCEL=<value>` | `ACCEL` | mm/s² (recommend over M204) |
| Min cruise ratio (D-06) | `SET_VELOCITY_LIMIT MINIMUM_CRUISE_RATIO=<value>` | `MINIMUM_CRUISE_RATIO` | ratio 0.0–1.0 (see scaling note) |
| Square-corner vel (D-07) | `SET_VELOCITY_LIMIT SQUARE_CORNER_VELOCITY=<value>` | `SQUARE_CORNER_VELOCITY` | mm/s |
| Pressure advance (D-09) | `SET_PRESSURE_ADVANCE ADVANCE=<value>` | `ADVANCE` | s; optional `EXTRUDER=<name>` (omit for single-extruder v1) |
| Smooth time (D-10) | `SET_PRESSURE_ADVANCE SMOOTH_TIME=<value>` | `SMOOTH_TIME` | s |
| Part-cooling fan (D-11) | `M106 S<0..255>` | `S` | 0–255 (see scaling note) |
| FW retract (D-12) | `SET_RETRACTION RETRACT_LENGTH=<mm> RETRACT_SPEED=<mm/s> UNRETRACT_EXTRA_LENGTH=<mm> UNRETRACT_SPEED=<mm/s>` | all four exact | **NO Z_HOP param exists** (confirmed) |

**`ACCEL_TO_DECEL` is GONE / replaced.** The official `SET_VELOCITY_LIMIT` syntax lists `MINIMUM_CRUISE_RATIO`, **not** `ACCEL_TO_DECEL`. `[CITED: klipper3d.org/G-Codes.html]` + `[CITED: klipper3d.org/Status_Reference.html — toolhead lists minimum_cruise_ratio, "does not mention max_accel_to_decel"]`. The dev printers run Klipper `v0.13.0-662` (post-rename), so `toolhead.minimum_cruise_ratio` is the live field. **This resolves the staging "Open Planner Work" verify item.**

### Scaling notes (load-bearing — display vs wire mismatch)
- **`gcode_move.speed_factor` / `extrude_factor` are RATIOS (1.0 = 100%)** `[CITED: Status_Reference.html]` — but `M220`/`M221` take a **percent** (`S100`). So: read `speedFactor` (already parsed, `1.0`), display `×100` as `%`, send `S<percent>`. The reducer already stores `speedFactor`/`extrudeFactor` as Doubles. **(This is the #1 off-by-100 trap.)**
- **`fan.speed` is 0.0–1.0** `[CITED: Status_Reference.html]` — but `M106` takes **`S0..255`**. So: read `fan.speed` (e.g. `0.6`), display `×100` as `%`, send `S<round(pct/100*255)>`. Step 5% (D-11) ⇒ ~12.75 PWM per step (round each absolute target, don't accumulate rounding error — compute from the displayed % each tap).
- **`minimum_cruise_ratio` is a ratio 0.0–1.0**, displayed as a percentage; D-06 step is "5 percentage points" = 0.05 ratio. Clamp 0.0–1.0.
- **`pressure_advance` / `smooth_time` are seconds** (e.g. PA 0.04–0.07, smooth_time 0.02–0.04 observed live). No scaling; format with `Locale.US`, strip trailing zeros (mirror `PrinterCommands.formatZ`).
- **`max_velocity`/`max_accel`/`square_corner_velocity` are plain units** (mm/s, mm/s², mm/s). No scaling.

### Suggested ranges + clamps (ASVS V5 clamp-before-format — add named consts to `PrinterCommands`)
These are UI-side guard rails; Klipper enforces its own config maxima and rejects out-of-range (surfaced as a non-fatal toast). Conservative bounds that still allow rescue headroom:

| Tuner | MIN | MAX | Step | Format |
|-------|-----|-----|------|--------|
| Speed % | 25 | 300 | 5 | int % |
| Flow % | 50 | 150 | 1 | int % (D-08 WIDE) |
| Max velocity | 1 | 1000 | 10 | int mm/s |
| Max accel | 100 | 50000 | 100 | int mm/s² |
| Min cruise ratio | 0.0 | 1.0 | 0.05 | 2dp |
| Square-corner vel | 0.1 | 20.0 | 0.1 | 1–2dp |
| Pressure advance | 0.0 | 1.0 | 0.001 | up to 3dp |
| Smooth time | 0.0 | 0.2 | 0.01 | 2dp |
| Part-fan % | 0 | 100 | 5 | int % → S0..255 |
| Retract length | 0.0 | 10.0 | 0.1 | 1dp |
| Unretract extra | -5.0 | 5.0 | 0.1 | 1dp |
| Retract speed | 1 | 100 | 1 | int mm/s |
| Unretract speed | 1 | 100 | 1 | int mm/s |

`[ASSUMED]` ranges — these are sensible engineering bounds, NOT from a single authority; the planner should treat the MIN/MAX as a clamp safety net (Klipper is the real authority) and may tighten/widen. All STEP values are LOCKED by CONTEXT D-03..D-12 (not assumed).

## Reducer additions (`PrinterState` + `PrinterStateReducer`)

`speed_factor` and `extrude_factor` are **already parsed** (lines 107–108). The new readback fields slot into the existing null-safe object walks. **All confirmed present on BOTH dev printers** by the live-probe matrix `[VERIFIED: docs/moonraker-capabilities.md — E5 extruder pressure_advance 0.07 / smooth_time 0.02; E3 pressure_advance 0.04 / smooth_time 0.04; toolhead max_velocity/max_accel]`.

New `PrinterState` fields (all nullable, default null/0 — never fabricate):
- `maxVelocity: Double?`, `maxAccel: Double?`, `minimumCruiseRatio: Double?`, `squareCornerVelocity: Double?` ← `toolhead.*`
- `pressureAdvance: Double?`, `smoothTime: Double?` ← `extruder.*` (PRIMARY extruder; single-extruder v1, D-09)
- `partFanSpeed: Double?` ← `fan.speed` (0.0–1.0)
- `firmwareRetraction: FirmwareRetractionObject?` ← `firmware_retraction.*` (new data class: retractLength, retractSpeed, unretractExtraLength, unretractSpeed — all nullable)

Reducer walk additions (mirror existing blocks, null-safe `doubleOrNullAt`):
```kotlin
status.objectOrNull("toolhead")?.let { th ->
    th.doubleOrNullAt("max_velocity")?.let { s = s.copy(maxVelocity = it) }
    th.doubleOrNullAt("max_accel")?.let { s = s.copy(maxAccel = it) }
    th.doubleOrNullAt("minimum_cruise_ratio")?.let { s = s.copy(minimumCruiseRatio = it) }
    th.doubleOrNullAt("square_corner_velocity")?.let { s = s.copy(squareCornerVelocity = it) }
}
// extruder.pressure_advance/smooth_time: add to the heater loop OR a dedicated extruder walk
// (the heater loop currently reads temp/target/power/can_extrude only — PA/smooth live on the
// same `extruder` object, so either extend HeaterState or read them in a separate walk; a
// separate `extruder`-keyed walk is cleaner since PA/smooth are NOT per-heater concepts).
status.objectOrNull("fan")?.doubleOrNullAt("speed")?.let { s = s.copy(partFanSpeed = it) }
status.objectOrNull("firmware_retraction")?.let { fr -> /* build FirmwareRetractionObject */ }
```
**Subscribe-set:** `toolhead`/`gcode_move`/`extruder`/`fan` are already in `deriveSubscribeSet` (fan via the dynamic `name == "fan"` clause, line 91). **Add `firmware_retraction` to `V1_SUBSCRIBE_CORE`** so its live values flow when the object exists (intersect loop already guards absence — safe to add unconditionally).

## Config-Baseline Reset Paths (the reset source of truth)

Reset (D-16) = send the normal setter using the **config baseline**, read ONCE at handshake from `configfile.settings.<section>` via the EXISTING one-shot in `MoonrakerSession.kt:490–527` (the `setMinExtrudeTemp`/`setScrewsTiltConfig` seam). Add new `PrinterStateStore` one-shot StateFlows + new `store.set*` calls in that same `runCatching` block (no extra query — Pitfall 3).

Confirmed config-section field names `[CITED: klipper3d.org config reference — these are the `[printer]`/`[extruder]`/`[firmware_retraction]` config keys]`:

| Reset target | Config path | Reset command |
|--------------|-------------|---------------|
| Speed % | — (live override) | `M220 S100` (D-16 protocol-neutral 100%) |
| Flow % | — (live override) | `M221 S100` (D-16 protocol-neutral 100%) |
| Max velocity | `configfile.settings.printer.max_velocity` | `SET_VELOCITY_LIMIT VELOCITY=<baseline>` |
| Max accel | `configfile.settings.printer.max_accel` | `SET_VELOCITY_LIMIT ACCEL=<baseline>` |
| Min cruise ratio | `configfile.settings.printer.minimum_cruise_ratio` | `SET_VELOCITY_LIMIT MINIMUM_CRUISE_RATIO=<baseline>` |
| Square-corner vel | `configfile.settings.printer.square_corner_velocity` | `SET_VELOCITY_LIMIT SQUARE_CORNER_VELOCITY=<baseline>` |
| Pressure advance | `configfile.settings.extruder.pressure_advance` | `SET_PRESSURE_ADVANCE ADVANCE=<baseline>` |
| Smooth time | `configfile.settings.extruder.pressure_advance_smooth_time` ⚠ | `SET_PRESSURE_ADVANCE SMOOTH_TIME=<baseline>` |
| FW retraction (×4) | `configfile.settings.firmware_retraction.{retract_length,retract_speed,unretract_extra_length,unretract_speed}` | `SET_RETRACTION ...=<baseline>` |
| Part-cooling fan | **see below** | **see below** |

⚠ **Smooth-time config key mismatch (load-bearing):** the live STATUS field is `extruder.smooth_time`, but the CONFIG key is `extruder.pressure_advance_smooth_time`. The staging note already flags this. **The planner must read the baseline from `pressure_advance_smooth_time`, not `smooth_time`.** `[CITED: klipper3d.org config reference]`

### Part-cooling fan reset — the genuinely-fuzzy one (RESOLVED)
Klipper's `[fan]` config has **no persistent "configured runtime speed"** — a `[fan]` section has no `speed`/`default_speed` key; its only runtime state is whatever was last commanded (slicer `M106`, a macro, or 0). `configfile.settings.fan.*` carries pin/PWM/tach config, **not a target speed**. So "reset to config baseline" has no literal baseline to read.

**Recommendation (resolves D-16 "reset, never assumed off"):** treat fan-reset as **"hand control back to the print"** — during a print, the slicer's `M106`/`M107` lines continuously re-command the fan, so Dinghy should NOT slam it to 0 (that's "assumed off", explicitly forbidden) and should NOT slam it to 100. **The least-surprising reset is a no-op-equivalent that yields to the print:** either (a) **omit a fan reset entirely** (the part-fan tile simply has no long-press-reset, documented as "no config baseline exists"), or (b) **reset = re-issue the current live `fan.speed` rounded** (a true no-op that visibly "snaps to current"). **Prefer (a): no reset affordance on the part-fan tile**, with a one-line in-tile note or just the absence of the long-press behavior. This is honest (there is nothing to reset TO) and matches "never assumed off." `[ASSUMED — design recommendation; owner should confirm in discuss/plan]`

## Capability-Gate Predicates (D, Claude's Discretion)

There is **NO central `AvailabilityPredicate` evaluator** — the predicate enum exists on `CommandSpec` but is informational; **screens/holders gate manually via `Capabilities.hasObject`** (the established `CalibrationGate.calibrationSupported` idiom). Use plain object-presence:

| Tile / screen | Gate predicate | On dev printers? |
|---------------|----------------|------------------|
| Speed % / Flow % | `hasObject("gcode_move")` | ✅ both |
| Max velocity/accel/min-cruise/SCV (Motion limits) | `hasObject("toolhead")` | ✅ both |
| Pressure advance / Smooth time | `hasObject("extruder")` | ✅ both |
| Part-cooling fan | `hasObject("fan")` | ✅ both (E5 + E3 both list `fan`) `[VERIFIED: printer-matrix]` |
| FW-Retraction entry + mini-screen | `hasObject("firmware_retraction")` | ❌ **NEITHER** — gates OFF, build-blind `[VERIFIED: printer-matrix — 0 occurrences, no firmware_retraction object on E5 or E3]` |

**No `gcode_command_present` runtime source exists in the app.** `SET_VELOCITY_LIMIT`/`SET_PRESSURE_ADVANCE`/`SET_RETRACTION` are built-in Klipper commands present whenever their owning object exists — gate on the OBJECT (`toolhead`/`extruder`/`firmware_retraction`), not on a help-list. (The `GcodeCommandPresent` predicate is only used by FORCE_MOVE-class specs and is not evaluated at runtime; do not invent a help-query path.)

## Runtime State Inventory

> Phase 17 is NOT a rename/refactor/migration — it adds new code + reads new fields. No stored data, OS-registered state, secrets, or build artifacts carry a renamed string.
- **Stored data:** None — verified; no datastore keys change (the deferred increment-customization would add a `*.preferences_pb`, but it's out of scope D-18).
- **Live service config:** None — verified; Fine-Tune only reads existing Moonraker objects and sends standard g-code.
- **OS-registered state:** None — verified.
- **Secrets/env vars:** None — verified.
- **Build artifacts:** New `res/drawable/*.xml` icons + new Kotlin files only — normal additive build output, no stale artifact risk.

## Architecture Patterns

### System Architecture Diagram
```
[user taps − / +]                         [long-press value]
       │                                          │
       ▼                                          ▼
 FineTuneTile (Compose) ── computes target ──> resetTile() ── reads baseline (StateFlow)
       │  (one command per tap, tap-on-value inert)            │
       ▼                                                        ▼
 dispatchCommand(spec, args)  ◄─── whole-group busy-lock (inFlight non-empty → disable all tiles)
       │
       ▼
 CommandDispatcher (debounce / in-flight / timeout / redacted failure)
       │  gcode.script {"script": "M220 S105"}  (120s timeout for gcode)
       ▼
 JsonRpcClient ──► Moonraker ──► Klipper
                                    │
        notify_status_update diff   │
       ┌────────────────────────────┘
       ▼
 PrinterStateReducer.reduceDiff (null-safe deep-merge)
       │  speed_factor 1.0 → 1.05
       ▼
 PrinterState (immutable StateFlow)  ──► FineTuneHolder.buildVm ──► tile re-renders new value
       (STATE-FLIP CONFIRM: the tile shows the new value only after the printer reports it)
                                                     ▲
 [handshake one-shot] MoonrakerSession configfile read ─► PrinterStateStore baseline StateFlows ─┘
       (configfile.settings.printer/extruder/firmware_retraction → reset source of truth)
```

### Recommended Project Structure
```
ui/finetune/
├── FineTuneHolder.kt        # Holder(scope, store) → FineTuneVm (live values + baselines + caps gates)
├── FineTuneVm.kt            # data class: per-tuner current value (display-scaled) + present flags + baselines
├── FineTuneHubScreen.kt     # two large entries (Motion, Extrusion); NO summary values (D-20)
├── MotionScreen.kt          # 5 capability-gated value tiles
├── ExtrusionScreen.kt       # flow/PA/smooth/part-fan + FW-retraction entry-if-present
├── FwRetractionScreen.kt    # 4 tiles (build-blind — gates off on dev printers)
└── FineTuneTile.kt          # SHARED C2-derived value tile (icon · name · value · − · +; long-press=reset)
command/  (additions to existing files)
├── PrinterCommands.kt       # + setVelocityLimit(...), setPressureAdvance(...), setFan(pct), setRetraction(...), speedFactor(pct)=M220, flowFactor(pct)=M221
└── CommandRegistry.kt       # + speedFactor, flowFactor, setVelocityLimit, setPressureAdvance, setFan, setRetraction gcode() specs
state/  (additions)
├── PrinterState.kt          # + new nullable readback fields + FirmwareRetractionObject
├── PrinterStateReducer.kt   # + null-safe walks for toolhead/extruder/fan/firmware_retraction
├── DeriveCapabilities.kt    # + "firmware_retraction" in V1_SUBSCRIBE_CORE
└── PrinterStateStore.kt     # + baseline one-shot StateFlows (max_velocity/accel/min_cruise/scv/PA/smooth/retraction)
net/MoonrakerSession.kt      # + store.set* baseline reads inside the existing configfile runCatching block
ui/route/TopRoute.kt         # + Dest.FineTune
ui/shell/AppShell.kt         # + FineTune arm
ui/printstatus/PrintStatusScreen.kt  # wire PrintStatusControlAction.Tune -> navigate(FineTune)
```

### Pattern 1: Immediate-dispatch value tile with state-flip confirm
```kotlin
// Mirrors ExtrudeScreen.dispatchCommand (the funnel) + the in-flight disable. The tile NEVER holds
// optimistic local state for the printed value — it renders ONLY the reduced PrinterState value, so
// the readout flips only after the printer confirms (D-14 state-flip confirm).
@Composable
private fun FineTuneTile(
    iconRes: Int,            // R.drawable.speed etc. (painterResource — NOT the Material Symbols font)
    name: String,
    valueText: String,       // formatted from the LIVE reduced value (display-scaled)
    onDecrement: () -> Unit, // computes target from current displayed value, dispatches one command
    onIncrement: () -> Unit,
    onReset: () -> Unit,     // long-press the value (D-16)
    enabled: Boolean,        // == !groupBusy  (whole-group lock, D-15)
) { /* − [icon · name · value(long-press=reset)] +  ; all dimmed when !enabled */ }
```
**Source pattern:** `ExtrudeScreen.kt` `dispatchCommand` (168–171), `BigCommand`/`SettingReadout` disabled-dim (393–423), `painterResource` icon use (234–241). Use `pointerInput { detectTapGestures(onLongPress = onReset) }` on the value cell; the +/- are plain `clickable`.

### Pattern 2: Whole-group busy-lock (D-15)
```kotlin
// The simplest correct interpretation: the group screen is busy whenever the dispatcher has ANY key
// in flight (these tiles are the only dispatch source on the screen). No per-tile key tracking needed.
val inFlight by dispatcher.inFlight.collectAsStateWithLifecycle(emptySet())
val groupBusy = inFlight.isNotEmpty()
// every tile: enabled = !groupBusy. Busy/disabled tile styling is sufficient — NO separate spinner (D-15).
```
This is *stronger* than the per-key in-flight check `ExtrudeScreen` uses, and matches D-15 verbatim ("lock the entire group screen"). The dispatcher's debounce + in-flight already prevents double-fire; the group-lock additionally serializes rapid nudging (the accepted cost, D-15).

### Pattern 3: New gcode() spec + pure builder (mirror babystepZ/extrude)
```kotlin
// PrinterCommands.kt — pure, clamp-before-format, Locale.US (ASVS V5)
fun speedFactor(pct: Int): String = "M220 S${pct.coerceIn(SPEED_PCT_MIN, SPEED_PCT_MAX)}"
fun setVelocityLimit(velocity: Double? = null, accel: Double? = null,
                     minCruiseRatio: Double? = null, scv: Double? = null): String =
    buildString {
        append("SET_VELOCITY_LIMIT")
        velocity?.let { append(" VELOCITY=${fmt(it.coerceIn(VEL_MIN, VEL_MAX))}") }
        accel?.let { append(" ACCEL=${fmt(it.coerceIn(ACCEL_MIN, ACCEL_MAX))}") }
        minCruiseRatio?.let { append(" MINIMUM_CRUISE_RATIO=${fmt(it.coerceIn(0.0, 1.0))}") }
        scv?.let { append(" SQUARE_CORNER_VELOCITY=${fmt(it.coerceIn(SCV_MIN, SCV_MAX))}") }
    }
fun setFan(pct: Int): String =
    "M106 S${(pct.coerceIn(0, 100) / 100.0 * 255).roundToInt()}"   // 0..255 from a 0..100% display
// CommandRegistry.kt
val setVelocityLimit: CommandSpec<VelocityLimitArgs> = gcode(
    catalogId = "KGC-SET_VELOCITY_LIMIT",
    key = { args -> "set_vel_limit_${args.field}" },  // distinct key per field so they don't collide
    gcode = { args -> PrinterCommands.setVelocityLimit(/* one field set */) },
    availability = AvailabilityPredicate.ObjectPresent("toolhead"),
)
// ... add each new spec to CommandRegistry.all (the registry test asserts membership).
```
**One command per nudge sets ONE field** (each tap changes one tuner) — so each `SET_VELOCITY_LIMIT` dispatch passes exactly the one changed field. Use a distinct `dispatchKey` per field (e.g. `"set_vel_velocity"`, `"set_vel_accel"`) so the dispatcher doesn't treat two different motion-limit nudges as the same in-flight key.

### Anti-Patterns to Avoid
- **Optimistic local value state on a tile.** Don't store the displayed value in `remember` and mutate it on tap — render the reduced `PrinterState` value so confirmation is genuinely state-flip (D-14). Compute the *target* from the current displayed value at tap time, dispatch, then let the reducer update the readout.
- **Off-by-100 / off-by-255.** `speed_factor`/`extrude_factor` are ratios; `fan.speed` is 0–1; the commands take percent / 0–255. See [Scaling notes](#scaling-notes-load-bearing--display-vs-wire-mismatch). The reducer stores ratios — scale at the display/command boundary, not in the reducer.
- **Reading smooth-time baseline from `smooth_time`.** The config key is `pressure_advance_smooth_time`. Status field ≠ config key.
- **Adding a central AvailabilityPredicate evaluator.** Out of scope; gate with `hasObject` like every other screen.
- **Gating part-fan on `fan_generic`/`heater_fan`.** D-11 is the part-cooling `fan` ONLY — gate strictly on `hasObject("fan")`. Generic/aux/heater fans are Phase 18.
- **Slamming the part-fan to 0 on reset.** "Never assumed off" (D-16). See [fan reset](#part-cooling-fan-reset--the-genuinely-fuzzy-one-resolved).
- **Building the full C2 component or retrofitting Move.** D-19: tile only, wired only in Fine-Tune.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Command dispatch (debounce/in-flight/timeout/error) | A new dispatch path | `CommandDispatcher` + `dispatch(spec,args)` | Already handles double-tap, gcode 120s timeout, redacted failure toasts (G1/G4 lessons baked in) |
| g-code string building | Inline string concat in the UI | `PrinterCommands` pure builders | Clamp-before-format (ASVS V5) + `Locale.US` already the house rule; host-testable |
| Status diff parsing | A new parser | `PrinterStateReducer` null-safe walks | Deep-merge-not-replace semantics already correct (Pitfall 1); a missing field retains, never crashes |
| Config-baseline fetch | A second `configfile` query | The existing handshake one-shot (`MoonrakerSession:490`) | Pitfall 3 — no duplicate configfile query; add `store.set*` calls in the same block |
| Capability gating | A predicate evaluator | `Capabilities.hasObject` | The whole app gates this way (`CalibrationGate`) |
| Icons | Material Symbols font dependency | per-icon vector drawable + `painterResource` | minSdk-23 / Adreno-320: no font dep (D-17); bucket is the registry |
| Holder lifecycle | A ViewModel/DI rework | `Holder(scope, store)` + `MutableStateFlow<Vm>` | The toolkit-agnostic headless-holder template (ADR-0001) — host-unit-testable |

**Key insight:** every "hard" part of this phase is already solved in-repo. The risk is NOT the plumbing; it's the **scaling boundaries** (ratio↔percent, 0–1↔0–255), the **smooth-time config-key mismatch**, and the **FW-retraction build-blindness**. Spend planning rigor there.

## Common Pitfalls

### Pitfall 1: The ratio/percent/255 scaling mismatch
**What goes wrong:** Tile shows `1.05%` instead of `105%`, or fan jumps to absurd values, because the reduced ratio/0–1 value is sent or displayed without scaling.
**Why:** `gcode_move.speed_factor`/`extrude_factor` are ratios (1.0=100%); `fan.speed` is 0–1; but `M220`/`M221` take percent and `M106` takes 0–255.
**How to avoid:** scale ONLY at the display/command boundary. Reducer stores raw. Add a unit test asserting `M220` from a 1.05 ratio = `S105` and `M106` from 60% = `S153`.
**Warning signs:** tile readout shows `0.xx%`; fan won't move past 1%.

### Pitfall 2: Smooth-time status field vs config key
**What goes wrong:** reset-smooth-time reads null (the key isn't there) because the code looked for `configfile.settings.extruder.smooth_time`.
**Why:** live status = `smooth_time`; config = `pressure_advance_smooth_time`.
**How to avoid:** read the baseline from `pressure_advance_smooth_time`; read the live value from `smooth_time`.
**Warning signs:** smooth-time reset is a no-op / sends `SMOOTH_TIME=` empty.

### Pitfall 3: FW-retraction can't be live-tested on dev hardware
**What goes wrong:** the team expects an on-device UAT gate for the FW-Retraction mini-screen; it can't happen — neither E5 nor E3 has `[firmware_retraction]`, so the entry never appears.
**Why:** `firmware_retraction` object is absent on both printers (live-probe matrix, 0 occurrences).
**How to avoid:** treat FW-Retraction as **build-blind** (like `QUAD_GANTRY_LEVEL`): gate on `hasObject("firmware_retraction")`, prove it with a fixture/host test (a synthetic `firmware_retraction` snapshot), and DO NOT put it behind an on-device gate. The on-device gates cover Motion + Extrusion (flow/PA/smooth/fan) only.
**Warning signs:** a plan task says "UAT the retract tiles on flox" — that's impossible; flag it.

### Pitfall 4: Same dispatch key for different motion-limit nudges
**What goes wrong:** nudging max-accel right after max-velocity is dropped because both used key `"set_vel_limit"` and the first is still in flight.
**Why:** the dispatcher dedupes by `dispatchKey`.
**How to avoid:** distinct key per field (`"set_vel_velocity"`, `"set_vel_accel"`, …). (The whole-group busy-lock already serializes taps, but distinct keys keep semantics clean and the busy-lock is the user-facing serialization.)

### Pitfall 5: Optimistic readout breaks state-flip confirm
**What goes wrong:** the value "confirms" instantly even when the printer rejects the command (out-of-range), because the tile rendered local state.
**Why:** D-14 requires confirmation by printer-object state flip, not a bare ack.
**How to avoid:** render the reduced value only; on rejection the dispatcher toasts the printer's reason and the readout simply doesn't move.

### Pitfall 6: Wave-0 RED scaffolds must compile (recurring project lesson)
**What goes wrong:** RED test stubs reference unbuilt symbols (new `PrinterState` fields, new specs) and brick the whole test sourceset (`--tests` filter still compiles everything).
**Why:** Gradle compiles the full test sourceset before filtering (`[[dinghy-wave0-red-scaffold-compile]]`).
**How to avoid:** Wave-0 RED tests use `fail()` bodies / typed assertions only against symbols introduced in the SAME plan; don't forward-reference symbols a later wave adds.

## Code Examples

### Reading + display-scaling a live tuner value (holder → vm)
```kotlin
// FineTuneHolder.buildVm — mirror MoveHolder/ExtrudeHolder
private fun buildVm(s: PrinterState, caps: Capabilities, baselines: Baselines): FineTuneVm {
    return FineTuneVm(
        // display-scaled current values (null = unreported → show "—", never fabricate)
        speedPct = (s.speedFactor * 100).roundToInt(),               // ratio → %
        flowPct = (s.extrudeFactor * 100).roundToInt(),              // ratio → %
        maxVelocity = s.maxVelocity, maxAccel = s.maxAccel,
        minCruiseRatio = s.minimumCruiseRatio, scv = s.squareCornerVelocity,
        pressureAdvance = s.pressureAdvance, smoothTime = s.smoothTime,
        partFanPct = s.partFanSpeed?.let { (it * 100).roundToInt() }, // 0..1 → %
        // capability gates
        hasFan = caps.hasObject("fan"),
        hasFwRetraction = caps.hasObject("firmware_retraction"),
        // baselines for reset (from the configfile one-shot)
        baselines = baselines,
    )
}
```

### Wiring the Tune entry point (D-21)
```kotlin
// PrintStatusScreen.kt:260 — replace the stub
PrintStatusControlAction.Tune -> onNavigate(Dest.FineTune)   // was: -> Unit
// TopRoute.kt:37 — add FineTune to the enum
enum class Dest { PrintStatus, Temperature, Move, Extrude, Files, Macros, Console,
                  Calibration, Webcam, Spool, Devices, Theme, Settings, About, FineTune }
// AppShell.kt — add a Dest.FineTune arm hosting FineTuneHubScreen (+ in-shell sub-nav to
// Motion/Extrusion/FwRetraction, mirroring the Calibration hub→routine sub-nav).
```

## State of the Art

| Old approach | Current approach | When changed | Impact |
|--------------|------------------|--------------|--------|
| `SET_VELOCITY_LIMIT ACCEL_TO_DECEL=` + `toolhead.max_accel_to_decel` | `SET_VELOCITY_LIMIT MINIMUM_CRUISE_RATIO=` + `toolhead.minimum_cruise_ratio` | Klipper ~2023 (well before v0.13) | Dev printers (v0.13.0-662) expose `minimum_cruise_ratio`; D-06 is correct, `ACCEL_TO_DECEL` is gone. Resolves the staging verify item. |

**Deprecated/outdated:** `ACCEL_TO_DECEL` / `max_accel_to_decel` — replaced by `MINIMUM_CRUISE_RATIO` / `minimum_cruise_ratio`. Do not reference the old names.

## Validation Architecture

> `workflow.nyquist_validation` not set to false → section included.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit (host) + AndroidX instrumented (on-device) — existing dinghy setup |
| Config file | Gradle (`app/build.gradle`); host tests in `app/src/test`, instrumented in `app/src/androidTest` |
| Quick run command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.command.PrinterCommandsTest' --no-daemon"` (list classes explicitly — glob `*` false-fails on this AGP, MEMORY lesson) |
| Full suite command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| On-device | `connectedAndroidTest` for instrumented; hands-on flox UAT against live E5/E3 for visual + state-flip (per `[[dinghy-display-ondevice-iteration]]`) |

### Phase Requirements → Test Map
| Req | Behavior | Test type | Automated command | File |
|-----|----------|-----------|-------------------|------|
| TUNE-02/03 | Each builder produces exact gcode (incl. scaling) | unit | `... --tests 'works.mees.dinghy.command.PrinterCommandsTest'` | ❌ Wave 0 — extend `PrinterCommandsTest` |
| TUNE-02/03 | New specs in `CommandRegistry.all`, correct availability | unit | `... --tests 'works.mees.dinghy.command.CommandRegistryTest'` | ❌ Wave 0 |
| TUNE-02/03 | Reducer parses new fields null-safe (toolhead/extruder/fan/firmware_retraction); diff-merge retains | unit | `... --tests 'works.mees.dinghy.state.PrinterStateReducerTest'` | ❌ Wave 0 — add fixtures (incl. synthetic firmware_retraction) |
| TUNE-05 | Baseline one-shot reads correct config keys (esp. `pressure_advance_smooth_time`); speed/flow reset = 100% | unit | reducer/session-parse test | ❌ Wave 0 |
| TUNE-02 | Scaling: 1.05 ratio→`S105`; 60%→`M106 S153`; 0.6 fan.speed→60% display | unit | `PrinterCommandsTest` / holder test | ❌ Wave 0 |
| TUNE-04 | FW-retraction tiles gate ON only when object present (host fixture) | unit | holder/gate test | ❌ Wave 0 |
| TUNE-06 | Whole-group busy-lock disables all tiles while inFlight non-empty | unit (holder) / instrumented | host + `connectedAndroidTest` | ❌ Wave 0 |
| TUNE-01 | Tune action navigates to FineTune | instrumented | `connectedAndroidTest` | ❌ Wave 0 |
| Live | State-flip confirm + perf on flox | manual UAT | hands-on flox + live E5/E3 | n/a (Motion+Extrusion+fan only; FW-retraction NOT testable) |

### Sampling Rate
- **Per task commit:** the relevant `--tests` class (PrinterCommands / CommandRegistry / Reducer).
- **Per wave merge:** full host suite (`:app:testDebugUnitTest`).
- **Phase gate:** full host suite green + on-device UAT of Motion + Extrusion (flow/PA/smooth/fan) state-flip on flox + an Adreno-320 perf check (gfxinfo, judge on no-frozen-frames + responsiveness per ADR-0001 Add.2 — the value-tile screens are static, not high-churn, so perf risk is LOW).

### Wave 0 Gaps
- [ ] Extend `PrinterCommandsTest` — new builders + scaling assertions
- [ ] Extend `CommandRegistryTest` — new specs in `.all` + availability
- [ ] Extend `PrinterStateReducerTest` — new field walks + synthetic `firmware_retraction` fixture + diff-merge retention
- [ ] New `FineTuneHolderTest` — vm scaling, gates, baselines, busy-lock derivation
- [ ] Instrumented nav test — Tune → FineTune route

## Security Domain

> `security_enforcement` absent = enabled. Phase 17 is local-LAN, no auth/session/access-control surface beyond what the spine already enforces.

### Applicable ASVS Categories
| Category | Applies | Standard control |
|----------|---------|------------------|
| V2 Authentication | no | Moonraker API-key handled by the existing identify/session layer; unchanged |
| V3 Session | no | unchanged |
| V4 Access Control | no | local control surface |
| V5 Input Validation | **yes** | **clamp-before-format in `PrinterCommands` (named consts, `coerceIn`), `Locale.US` formatting — no free-text into a g-code string.** All Fine-Tune inputs are bounded ±-nudges (no keyboard), so the injection surface is nil; still clamp every numeric before formatting (the house rule). |
| V6 Cryptography | no | none |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Mitigation |
|---------|--------|-----------|
| G-code injection via a numeric param | Tampering | All params are ±-stepped numbers, clamped + `Locale.US`-formatted; no user string is concatenated (unlike the bed-mesh name field). |
| Out-of-range value crashing the app | DoS | Dispatcher catches `RpcError` → non-fatal redacted toast (G1 lesson); never an uncaught crash. |
| Locale-comma in a formatted Double (`0,05`) reaching gcode | Tampering | `String.format(Locale.US, ...)` + trailing-zero strip (mirror `PrinterCommands.formatZ`). |

## Assumptions Log

| # | Claim | Section | Risk if wrong |
|---|-------|---------|---------------|
| A1 | Suggested MIN/MAX clamp ranges (steps are LOCKED, ranges are engineering bounds) | Command Param Reference | Low — Klipper rejects out-of-range with a non-fatal toast; over-tight clamp would silently cap a legit value (planner should sanity-check against typical printer maxima) |
| A2 | Part-fan reset = **no reset affordance** (no config baseline exists) | Fan reset | Medium — owner may prefer "reset = re-issue current" or "reset to a defined %"; confirm in discuss/plan. "Never assumed off" is locked; the implementation of "reset" is the open bit |
| A3 | `pressure_advance` / `smooth_time` belong to the PRIMARY `extruder` only (single-extruder v1) | Reducer additions | Low — D-09 locks single-extruder v1; multi-tool deferred |
| A4 | A small Motion/Extrusion readout strip (D-20 planner discretion) is optional, not required | Layout | Low — pure layout choice |
| A5 | FW-retraction sub-control glyphs reuse `input_circle`/`output_circle`/`sprint` | Icons | Low — D-17 says owner confirms if distinct glyphs wanted; reuse is the documented fallback |

## Open Questions

1. **Part-cooling fan "reset" semantics (A2).**
   - Known: Klipper `[fan]` has no persistent configured speed; "never assumed off" is locked.
   - Unclear: should the part-fan tile have NO reset, reset-to-current (no-op snap), or reset-to-a-fixed-%?
   - Recommendation: **no reset affordance** on the part-fan tile (honest: nothing to reset to); confirm with owner in discuss/plan.
2. **Motion limits: expanded vs "advanced" disclosure (D, staging open item).**
   - Known: 5 tiles per group must stay touch-friendly portrait AND landscape (≥64px targets, Focus/Field grammar).
   - Unclear: do all 5 Motion tiles show at once, or speed-only + an "Advanced limits" disclosure?
   - Recommendation: show all 5 in a Field grid (a 2×3 or 1×5 responsive grid keeps targets ≥64px on a Nexus-7-class screen); planner validates on-device. The two screens (Motion/Extrusion) each carry ≤5 tiles — comfortably fits without disclosure.
3. **`SET_PRESSURE_ADVANCE EXTRUDER=` omission.**
   - Known: single-extruder v1 (D-09); omitting `EXTRUDER=` targets the active extruder.
   - Recommendation: omit `EXTRUDER=` (active-extruder default) — matches single-extruder assumption; revisit only if multi-tool is scoped.

## Environment Availability

| Dependency | Required by | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Live Ender 5 Plus (192.168.1.120:7125) | Motion/Extrusion/fan UAT | ✓ | Klipper v0.13.0-662 / Moonraker v0.10.0 | E3 (192.168.1.121:7125) |
| Live Ender 3 Pro (192.168.1.121:7125) | UAT | ✓ | same | E5 |
| `[firmware_retraction]` on a dev printer | FW-Retraction live UAT | ✗ | — | **None — build-blind; fixture/host-test only** (Pitfall 3) |
| flox (Nexus 7 2013, LineageOS 18.1 / API 30, Adreno 320) | on-device UAT + perf floor | ✓ | API 30 | — |
| Windows build (`E:\Android\gw.bat`) + adb | build/install | ✓ | JDK 21, SDK 35 | — |

**Missing with no fallback:** `[firmware_retraction]` config on a test printer — the FW-Retraction mini-screen cannot be live-validated; plan it code-reasoned + host-fixture-tested, gated build-blind (mirror `QUAD_GANTRY_LEVEL`).
**Missing with fallback:** none else.

## Sources

### Primary (HIGH confidence)
- `klipper3d.org/G-Codes.html` — exact command params for SET_VELOCITY_LIMIT (VELOCITY/ACCEL/MINIMUM_CRUISE_RATIO/SQUARE_CORNER_VELOCITY, no ACCEL_TO_DECEL), M204/M220/M221/M106, SET_PRESSURE_ADVANCE (ADVANCE/SMOOTH_TIME/EXTRUDER), SET_RETRACTION (4 params, no Z_HOP), GET_RETRACTION — fetched 2026-06-06.
- `klipper3d.org/Status_Reference.html` — exact status fields: toolhead.{max_velocity,max_accel,minimum_cruise_ratio,square_corner_velocity}, gcode_move.{speed_factor=ratio 1.0, extrude_factor}, extruder.{pressure_advance,smooth_time,can_extrude}, fan.speed (0.0–1.0), firmware_retraction.{retract_length,retract_speed,unretract_extra_length,unretract_speed} — fetched 2026-06-06.
- In-repo source (read this session): `CommandRegistry.kt`, `CommandDispatcher.kt`, `CommandSpec.kt`, `CommandDispatchExtensions.kt`, `PrinterCommands.kt`, `PrinterState.kt`, `PrinterStateReducer.kt`, `Capabilities.kt`, `DeriveCapabilities.kt`, `MoonrakerSession.kt`, `PrinterStateStore.kt`, `ExtrudeHolder/Screen.kt`, `MoveHolder.kt`, `CalibrationGate.kt`, `CalibrationHubHolder.kt`, `TopRoute.kt`, `THEMING.md`, `LAYOUT.md`, `ui_design/CLAUDE.md`.
- `docs/moonraker-capabilities.md` + `docs/commands/printer-matrix.json` — LIVE-PROBED ground truth: E5/E3 both expose pressure_advance/smooth_time/max_velocity/max_accel/fan; **neither exposes firmware_retraction**; Klipper v0.13.0-662.
- `img/material-icon-bucket.json` — all 13 P17 glyph names verified PRESENT (direct membership check).

### Secondary (MEDIUM confidence)
- `docs/commands/klipper-gcode.md` — in-repo command catalog (confirms SET_VELOCITY_LIMIT/SET_PRESSURE_ADVANCE/SET_RETRACTION/M204/M220/M221/M106 entries + availability predicates).

### Tertiary (LOW confidence)
- None — every claim is grounded in official docs or in-repo verified source. The only `[ASSUMED]` items are the engineering clamp RANGES (A1) and the fan-reset semantics recommendation (A2), both flagged.

## Metadata

**Confidence breakdown:**
- Command params + status fields: HIGH — verified verbatim against official Klipper docs AND in-repo live-probe.
- Code seams / patterns: HIGH — read the actual source; patterns are directly reusable.
- Capability gating: HIGH — confirmed both printers' object lists; FW-retraction absence confirmed.
- Clamp ranges: MEDIUM — engineering bounds (A1), steps locked by CONTEXT.
- Fan-reset semantics: MEDIUM — design recommendation (A2), needs owner confirm.

**Research date:** 2026-06-06
**Valid until:** 2026-07-06 (stable — Klipper command surface and in-repo patterns are settled; re-verify only if Klipper version on the dev printers changes materially).
