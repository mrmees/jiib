# Phase 16: Home / Print-Status Redesign - Research

**Researched:** 2026-06-06
**Domain:** Android/Compose UI rework of an existing 942-line state screen against a LOCKED design system (Focus/Field/Gutter, semantic tokens, fsSp). Native Kotlin, Moonraker-only data.
**Confidence:** HIGH (all findings verified against the real repo source, not assumed)

## Summary

This is a UI/UX rework phase against a mature codebase. The four-state model, every state's layout,
babystep mechanics, terminal behavior, and gutter semantics are **already locked** by the staging
note + UI-SPEC + CONTEXT — this research does **not** re-litigate those. It maps the concrete code
seams a planner needs and resolves the three gray areas CONTEXT delegated.

The headline implementation reality: **three data sources the new design depends on do not yet exist
in `PrinterState`**, and one command does not exist in the registry. They are all small, well-bounded
additions on top of the existing spine — but they ARE net-new work the planner must schedule, not
"reuse what's there":

1. **`gcode_move.homing_origin[2]`** (applied Z offset readback for babystep) — the `gcode_move`
   object is *already subscribed* (`MoonrakerSession` core subscribe set) and the reducer already
   walks it; `homing_origin` is simply *not parsed into a field yet*. **One reducer line + one
   `PrinterState` field.** `[VERIFIED: docs/moonraker-capabilities.md:69 confirms homing_origin lives on gcode_move]`
2. **MCU/host temp** (Standby glance metric) — comes from `temperature_sensor <name>` objects
   (chamber/frame/MCU sensors confirmed present on the dev printers) which are **NOT currently
   subscribed** (the subscribe set deliberately omits bare `temperature_sensor` entries). Needs a
   subscribe-set addition + reducer + state field, OR is sourced from the existing one-shot path.
3. **Host load** (Standby glance fallback) — `machine.system_info` / `proc_stats` is a *different
   Moonraker surface* entirely (not in `printer.objects`); **not modeled at all today** (it is the
   subject of the future Phase 19). 
4. **`SDCARD_RESET_FILE`** (Terminal Dismiss) — **does not exist in `CommandRegistry`/`PrinterCommands`** today. New gcode command spec.

**Primary recommendation:** Restructure around a new **pure, host-testable `PrintStatusMode`
classifier** (toolkit-agnostic, ADR-0001) that extends the existing `derivePrintStatusControls`
seam, render each mode through dedicated composables, and reuse the existing `StatGrid` /
`IconTwoRowCell` / `IconValueCell` / `ProgressRing` / `StopButton` / `ConfirmGuard` / `PresetSelector`
primitives as-is. Do **not** rewrite from scratch and do **not** touch the Views-based GraphView
render path. For the Standby glance metric, prefer a real `temperature_sensor` reading when one
exists and fall back to host load only if Phase-16 chooses to model it — recommendation below is to
**source the glance temp from a `temperature_sensor` and DEFER host-load to Phase 19** to avoid
pulling the `machine.system_info` surface forward.

## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01 — Preheat is spool-aware:** if Spoolman available AND active spool exposes filament temps →
  `applyPreset` **directly** to `SpoolmanFilament.settingsExtruderTemp`/`settingsBedTemp` (no chooser),
  guarding each temp independently. Otherwise → open the Phase-5 `PresetSelector` (fixed PLA/PETG/ABS/TPU,
  keyboard-free). If spool exists but BOTH temps null → fall through to selector (don't fire a half preset).
- **D-02 — Forward stubs live in the App Drawer only**, using the existing greyed-tile pattern
  (`DrawerTileSpec.dest = null`). Add greyed **"Output"** (P18) + **"System Info"** (P19) tiles now. The
  Standby launcher grid does NOT carry forward stubs.
- **D-03 — Active-print row Tune** = the P17 Fine-Tune stub. WebRTC (P20) = existing runtime-gated Webcam tile, no new stub.
- **D-04 — Standby gutter Power** is design/layout-only & nonfunctional in P16; render as the drawer's red Power tile (stop-intent outline, inert).
- **D-05 — Terminal is passive** — a mode of the home screen; does NOT auto-yank the user from another screen. Seen next time they navigate home (honors shell rule G-A1). Visible until dismissed, reprint, or Moonraker reports another state.
- **D-06 — Babystep app setting** (enable toggle + positive-integer layer-count, default **enabled / first 5 layers**) lives under the **"Settings" tile** of the Phase-15.2 four-tile IA. Overall app preference (not per-printer). Layer-count uses the **standard numeric keyboard** (allowed — Settings is keyboard-permitted).

### Claude's Discretion (resolved in this research — see Architecture Patterns)
- **Standby glance metric "MCU/host temp OR host load"** — RESOLVED: prefer a real `temperature_sensor`
  reading; recommend deferring host-load modeling to Phase 19. (§3)
- **Rework strategy** (refactor in place vs restructure around classifier) — RESOLVED: restructure
  around a pure classifier; reuse primitives. (§2)
- **Babystep `SET_GCODE_OFFSET Z_ADJUST=±n MOVE=1` ↔ `gcode_move.homing_origin[2]` wiring** — detailed
  in §4; mechanics locked by staging note, wiring verified-on-paper here, MUST be proven on a live first layer.

### Deferred Ideas (OUT OF SCOPE)
- Per-printer Standby Focus image (P16 uses app icon; don't bake in choices preventing it later).
- User-customizable/reorderable Standby launcher grid (ship fixed curated order; don't prevent reorder later).
- Power → full host/system power dialog (P16 Power is inert).
- Mid-print object exclusion `EXCLUDE_OBJECT` (v2).
- Saving Z-offset to config (`Z_OFFSET_APPLY_*` + `SAVE_CONFIG`) — stays a Calibration action; P16 babystep is **session-only**.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| (refines SHELL-* / JOB-*) | UX rework of the home/Print-Status surface into its definitive state-driven form; no new functional REQ-IDs | This research maps the classifier seam (§1), rework strategy (§2), glance-metric rule (§3), babystep wiring (§4), spool-aware preheat (§5), doc-merge work (§6), and pitfalls (§7). No new backend REQ to map. |

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| `PrintStatusMode` classification | Headless state (pure Kotlin) | — | ADR-0001: toolkit-agnostic, host-testable; classifier is a pure function of `PrinterState` (Moonraker-derived). Same discipline as `derivePrintStatusControls`/`deriveCapabilities`. |
| Mode → composable routing | Compose UI | — | Each mode renders its own Focus/Field/Gutter via Compose; the high-churn Focus ring stays Compose (already is), GraphView stays Views (unchanged). |
| Applied Z-offset readback | Headless state (reducer) | — | `gcode_move.homing_origin[2]` parsed into `PrinterState` on the existing subscribe stream. |
| Babystep dispatch | Command layer (`CommandRegistry`/`PrinterCommands`) | — | `SET_GCODE_OFFSET` via `printer.gcode.script`, same pattern as every other action. |
| Standby glance MCU/host temp | Headless state (subscribe + reducer) | — | `temperature_sensor` object readout; same StateFlow spine. |
| Babystep app-setting persistence | DataStore via `AppContainer.writeScope` | — | [[dinghy-compose-write-scope-cancellation]]: must NOT use `rememberCoroutineScope()`. |
| Terminal Dismiss (`SDCARD_RESET_FILE`) | Command layer | — | New gcode command spec. |
| Reprint | Command layer (existing `printStart`) | — | Reuse `CommandRegistry.printStart(PrintStartArgs(filename))` — already wired as the current "Restart print". |

## Standard Stack

No new external libraries. This phase uses only what is already pinned and on the classpath. All UI
work is Compose + the in-house design system; all data work extends the existing headless spine.

### Reused In-Project Components (the real "stack" for this phase)
| Component | File | Reuse As |
|-----------|------|----------|
| `PrintStatusScreen` | `ui/printstatus/PrintStatusScreen.kt` (942 lines) | The screen being reworked; harvest its composables (below). |
| `derivePrintStatusControls` / `PrintStatusControlModel` | `ui/printstatus/PrintStatusControlModel.kt` | Existing per-state gutter-control derivation — the classifier formalizes & extends this (today only `active`/`terminal`/`standby` control sets). |
| `PrintStatusHolder` / `PrintStatusGrid` / `HeaterCell` / `ProgressCell` / `InfoCell` | `ui/printstatus/PrintStatusHolder.kt` | Toolkit-agnostic StateFlow holder + capability-fallback cell model. Reuse for stats frames. |
| `StatGrid` / `IconTwoRowCell` / `IconValueCell` | `ui/printstatus/PrintStatusScreen.kt` (private) | The framed print-stats list — reuse AS-IS for Printing/Paused/Terminal stats; **extend** with new rows (Current Z exists via `fmtZ`; ADD Applied-Z-offset row). |
| `PrintStatusFocus` (ProgressRing + thumbnail + % + filename) | `ui/printstatus/PrintStatusScreen.kt` (private) | The Printing/Paused Focus; reuse the composition (matches `03-print-status.png`). |
| `ProgressRing` | `render/ProgressRing` | Single-series accent ring, draw-once + throttled fill (Adreno-320 safe). |
| `StopButton` | `ui/printstatus/PrintStatusScreen.kt` (private) | E-Stop tile: tap=ConfirmGuard / hold=immediate + octagon glyph — **exactly the staging-note EStop semantics**. Reuse verbatim. |
| `ConfirmGuard` | `designsystem/ConfirmGuard` | Full-screen confirm for Cancel + E-Stop tap. |
| `ScreenScaffold` | `designsystem/layout/ScreenScaffold` | Focus/Field/Gutter host — already drives this screen. |
| `PresetSelector` + `MATERIAL_PRESETS` + `applyPreset` | `ui/temperature/TemperatureScreen.kt` + `command/PrinterCommands` | D-01 Preheat fallback path. `CommandRegistry.applyPreset(ApplyPresetArgs(nozzle,bed,key))`. |
| `ActiveSpoolCard` / `deriveActiveSpoolCardState` | `ui/spool/*` | Existing spool glance (currently shown above the field). Standby glance "spool remaining" can read the same `spoolDetail`. |
| `DrawerTileSpec` / `DrawerTile` / `DRAWER_TILES` | `ui/shell/AppDrawer.kt` | D-02 add greyed Output + System Info tiles (`dest = null`). |
| `StatusSlot` + shape glyphs (`ic_status_octagon` / `ic_status_triangle`) | `theme/StatusSlot.kt` | Status-by-shape; E-Stop octagon already wired in `StopButton`. |

**No `npm install` — this is Android/Gradle. No new dependencies. No Package Legitimacy Audit needed
(no external packages installed this phase).**

## Architecture Patterns

### System Architecture Diagram

```
Moonraker (websocket notify_status_update + REST)
        │
        ▼
  MoonrakerSession ──subscribe set (gcode_move, print_stats, heaters, temperature_sensor*…)
        │
        ▼
  PrinterStateReducer ──deep-merges diffs──▶ PrinterStateStore.printerState : StateFlow<PrinterState>
        │                                          │
        │  (ADD: homing_origin[2], temp-sensor)    │
        ▼                                          ▼
  ┌─────────────────────────────────────────────────────────────┐
  │  PrintStatusMode classifier  (NEW, pure fun of PrinterState) │  ◀── host-testable, toolkit-agnostic
  │  printing→Printing · paused→Paused · complete/cancelled/     │
  │  error→Terminal(kind) · standby→Standby (even if stale file) │
  └─────────────────────────────────────────────────────────────┘
        │
        ▼   when(mode)
  ┌──────────┬──────────┬──────────┬──────────────┐
  │ Standby  │ Printing │ Paused   │ Terminal     │   ◀── Compose composables, each Focus/Field/Gutter
  │ launcher │ cockpit  │ dimmed   │ result hero  │
  │ + glance │ + babystep (early-layer window)    │
  └──────────┴──────────┴──────────┴──────────────┘
        │ gutter actions
        ▼
  CommandRegistry / dispatcher (printer.gcode.script + JSON-RPC)
  Preheat·Pause·Resume·Cancel·EStop·Dismiss(SDCARD_RESET_FILE NEW)·Reprint(printStart)·Babystep(SET_GCODE_OFFSET NEW)
        │
        ▼
  Babystep app-setting toggle/threshold ──DataStore via AppContainer.writeScope (NOT composition scope)
```

### Pattern 1: `PrintStatusMode` classifier (the locked core move) — §1 of research focus

**What:** ONE pure function mapping `PrinterState` → a sealed `PrintStatusMode`. Composables route off
the mode instead of scattering `printState`/`printing` checks.

**Recommended shape** (new file `ui/printstatus/PrintStatusMode.kt`, plain Kotlin, no Compose — host-testable):
```kotlin
sealed interface PrintStatusMode {
    data object Standby : PrintStatusMode
    data object Printing : PrintStatusMode
    data object Paused : PrintStatusMode
    data class Terminal(val kind: TerminalKind) : PrintStatusMode
    enum class TerminalKind { Complete, Cancelled, Error }
}

fun classifyPrintStatus(state: PrinterState): PrintStatusMode = when (state.printState) {
    PrintState.Printing  -> PrintStatusMode.Printing
    PrintState.Paused    -> PrintStatusMode.Paused
    PrintState.Complete  -> PrintStatusMode.Terminal(Complete)
    PrintState.Cancelled -> PrintStatusMode.Terminal(Cancelled)
    PrintState.Error     -> PrintStatusMode.Terminal(Error)
    PrintState.Standby   -> PrintStatusMode.Standby   // even if printFilename/lastJob is stale
}
```

**Realizability check (CONTEXT classification rules vs real data) — VERIFIED:**
- `PrintState` enum (`state/PrinterState.kt:214`) has **exactly** the 6 raw states the rules map from:
  `Standby, Printing, Paused, Complete, Error, Cancelled`. One-to-one with the staging note. `[VERIFIED: PrinterState.kt:214]`
- **"standby → Standby even if stale filename exists"**: classify purely off `printState`, ignoring
  `printFilename`/`lastJob`. ⚠ NOTE the existing `derivePrintStatusControls` does the OPPOSITE today —
  on `Standby` *with* a `restartFilename` it currently renders **terminal** controls (Files/Restart),
  not standby controls (`PrintStatusControlModel.kt:67`). The new classifier must **override** that:
  Standby is Standby regardless of a leftover restartable filename. The restart-from-standby affordance
  moves into the Standby launcher (Files tile) — it is no longer a terminal masquerade. This is a
  deliberate behavior change the planner must call out.
- **"klippy shutdown/error does NOT create a terminal print-result mode"**: VERIFIED separable —
  `klippyState` (`KlippyState` enum) is a **distinct axis** from `printState` (`PrinterState.kt:33`
  comment: "distinct axis from klippyState"). The classifier reads ONLY `printState`; klippy lifecycle
  is owned by the app-level splash/recovery routing (Phase-3/4 shell), untouched here. `[VERIFIED: PrinterState.kt:33,210-214]`

**When to use:** Every render decision on this surface routes through `classifyPrintStatus(state)`.
Keep the classifier free of `lastJob`, DataStore, Spoolman — those are layered in the composable,
not the mode.

### Pattern 2: Rework strategy — restructure around the classifier (RESOLVED discretion) — §2

**Recommendation: restructure around the classifier; do NOT rewrite from scratch; do NOT refactor in
place as one monolithic when-branch.** Evidence:

- The current screen already has a clean Focus/Field/Gutter split via `ScreenScaffold` and already
  delegates gutter controls to `derivePrintStatusControls`. The *composables* are reusable; only the
  *routing* is too coarse (a single `printing` boolean at `PrintStatusScreen.kt:220,357`).
- The 942 lines are mostly **reusable primitives**, not screen logic. Harvest map:

| Existing composable | Disposition | Notes |
|---------------------|-------------|-------|
| `PrintStatusFocus` (ring+thumb+%+filename) | **Reuse as-is** for Printing; **wrap** for Paused (dim + pause-icon overlay); **variant** for Terminal (clean thumbnail, no ring/dim per staging note). | Already matches `03-print-status.png`. |
| `StatGrid` + `IconTwoRowCell` + `IconValueCell` | **Reuse; extend rows.** | Add an Applied-Z-offset row (`gcode_move.homing_origin[2]`, show when non-zero OR in babystep window). Current Z already present via `fmtZ`. Drop nothing. |
| `LastJobCard` / `LastJobEmpty` / `LastJobStatRow` | **Repurpose for Terminal Field** (single framed stats element) — staging note says "reuse the Printing stats-frame, omit live-only fields." Prefer extending `StatGrid` over `LastJobCard` for the terminal stats frame to keep ONE stats component; `LastJobCard`'s thumbnail-background treatment becomes the **Standby launcher / Terminal hero** material, not the stats frame. | Planner's call which becomes the canonical "stats frame"; staging note says reuse Printing's. |
| `StopButton` | **Reuse verbatim** (E-Stop). | tap=guard / hold=immediate + octagon already correct. |
| `PrintStatusControlTile` + `controlColor` | **Reuse**, extend `controlColor` for new intents (Preheat=accent, Resume=go, Reprint=accent, Dismiss=neutral, Power=inert-stop). | Intent map in UI-SPEC "Per-state button intents". |
| `derivePrintStatusControls` | **Extend** to emit the per-mode gutter sets the staging note specifies (Standby: Preheat·Power; Printing: Pause·Cancel·EStop; Paused: Resume·Cancel; Terminal: Dismiss·Reprint). | Today it has no Preheat/Power/Dismiss actions — `PrintStatusControlAction` enum needs `Preheat`, `Dismiss`, `Power(inert)` members. |
| `DisabledTile` | Reuse for inert Power. | |
| formatters (`fmtDuration`/`fmtZ`/`tempActive`/`totalLayers`) | Reuse as-is. | |

- **SC-3 perf constraint (no regression to Views primitives):** the GraphView/temperature Views render
  path is **not on this screen's hot path** — `PrintStatusScreen` uses Compose `ProgressRing` + Compose
  `StatGrid`, throttled by `PrinterStateStore` (DEFAULT_SAMPLE_MS=250; `PrintStatusHolder` adds NO
  second throttle). **Do not add a second throttle, do not animate the ring fill, do not add
  per-frame recomposition above the leaf temp cells.** The classifier restructure is a routing change,
  not a render-path change — perf risk is low IF the babystep row and stats additions stay leaf-level.
  Re-measure on flox regardless (SC mandate).

**Why not refactor-in-place:** a single growing `when(printState)` inside the existing 942-line file
recreates the exact "scattered raw state checks" the phase exists to kill. A pure classifier + per-mode
composables is the locked intent and the testable shape.

### Pattern 3: Standby glance metric rule (RESOLVED discretion) — §3

**The available fields, verified against the real model:**
- **Nozzle / bed temp:** `PrinterState.heaters["extruder"]` / `["heater_bed"]` → `HeaterState.temperature`.
  Already used by `StatGrid`/`primaryHeater`. `[VERIFIED: PrinterState.kt:192, PrintStatusScreen.kt:869]`
- **Active spool remaining:** `container.activeSpool` → `spoolDetail: SpoolmanSpool` (already resolved in
  `PrintStatusScreen.kt:135-150`); hide when `spoolmanPresent == false`. `[VERIFIED: PrintStatusScreen.kt:133]`
- **MCU/host temp:** comes from `temperature_sensor <name>` objects (e.g. `temperature_sensor mcu`,
  `temperature_host`, chamber/frame sensors). The caps doc confirms these sensors exist on the dev
  printers (`docs/moonraker-capabilities.md:47,200` — "chamber/frame/MCU temp sensors"). **BUT they are
  NOT subscribed today:** `MoonrakerSession.kt:470` *deliberately ignores* bare `temperature_sensor X`
  entries, and `deriveSubscribeSet` (`DeriveCapabilities.kt`) does not add them. So a glance temp
  requires (a) adding selected `temperature_sensor` objects to the subscribe set, and (b) a reducer
  field. `[VERIFIED: MoonrakerSession.kt:470, DeriveCapabilities.kt:50-90]`
- **Host load:** `machine.system_info` / `proc_stats` — a **separate Moonraker API surface, not in
  `printer.objects`** (caps doc `pause_resume, idle_timeout, system_stats, exclude_object present` lists
  `system_stats` as an *object*, but CPU load/throttle is the `machine.proc_stats`/`notify_proc_stat_update`
  surface). **Not modeled at all today** and is the explicit subject of **Phase 19** (ROADMAP: "Phase 19
  System Information Page — from `machine.system_info`/`proc_stats`"). `[VERIFIED: ROADMAP.md Phase 19; Capabilities.kt has no proc_stats]`

**Recommended rule (concrete, implementable from fields that genuinely exist):**
> The third glance line = **a real MCU/host temperature when a usable `temperature_sensor` exists**
> (prefer an object named `*mcu*`, else `*host*`, else the first non-heater `temperature_sensor`),
> rendered as a single `NN°` value with a small label. **If no usable temperature_sensor exists, OMIT
> the line entirely** (the glance list stays minimal — never a dense dump). **Do NOT pull the
> `machine.proc_stats` host-load surface forward into Phase 16** — defer host-load to Phase 19, where
> the System Info page models it properly.

Rationale: this keeps Phase 16 scoped (no new Moonraker *surface*, only a new *object* on the existing
subscribe stream), satisfies the "whichever is available/useful" hedge with a real sensor when present,
and degrades to a clean minimal glance when absent — which is exactly the "minimal, glanceable, not a
dense stats list" directive. Adding host-load would drag the entire `machine.system_info` model and
its `notify_proc_stat_update` plumbing into a UI phase, violating "no new backend."

**Planner note:** if Matthew wants the host-load fallback in P16 anyway, it is a *known cost* — a new
subscribe to `machine.proc_stats` (a JSON-RPC `machine.proc_stats` one-shot + `notify_proc_stat_update`
notification subscriber) + a `PrinterState` field. Flag it as an explicit scope question, not a silent inclusion.

### Pattern 4: Babystep wiring (SC-5 — the on-device-proof item) — §4

**Locked by staging note (do not re-litigate):** dedicated 3-cell row `Compress | step | Expand`,
exempt from flexible-tile + C3-vertical rules; center cell shows step only, tap cycles
`.02 → .05 → .10 → .15 → .20`; Compress=closer/Expand=farther, icon-only distinct silhouettes;
applied offset lives in the **stats frame**, not the row; replaces the entire shortcut row during the
early-layer window; hidden if layer data unavailable (NO time-based fallback); session-only.

**Wiring — verified against the model, MUST be proven on a live first layer:**
- **Dispatch:** `SET_GCODE_OFFSET Z_ADJUST=<±step> MOVE=1` via `printer.gcode.script`. This is a NEW
  builder in `PrinterCommands` + a NEW `CommandRegistry` gcode spec (pattern: see `setHeater`/`jog` at
  `CommandRegistry.kt:316,330`; builders in `PrinterCommands`). Compress fires `Z_ADJUST=-<step>`
  (nozzle closer), Expand fires `Z_ADJUST=+<step>` (nozzle farther). **Clamp the step** to the fixed
  cycle set before formatting (ASVS V5 — `PrinterCommands` clamps every numeric param; the step is one
  of 5 fixed values, so validate-against-set, never free-text). `MOVE=1` applies the offset immediately
  to the live toolhead.
- **Readback:** the applied offset reads from **`gcode_move.homing_origin[2]`**. The `gcode_move`
  object is **already subscribed** (`DeriveCapabilities.kt:57`) and the reducer **already walks
  gcode_move** (`PrinterStateReducer.kt:106-111`) for `speed_factor`/`extrude_factor`/`gcode_position`.
  `homing_origin` is confirmed present on `gcode_move` (`docs/moonraker-capabilities.md:69`) but is
  **not parsed into a field yet**. **This is the single net-new state addition for babystep:** add
  `val gcodeZOffset: Double? = null` (or `homingOrigin: List<Double>?`) to `PrinterState` and one
  reducer line `gm.doubleListOrNull("homing_origin")?.let { ... [2] ... }`. No new subscribe needed.
  `[VERIFIED: PrinterStateReducer.kt:106, DeriveCapabilities.kt:57, moonraker-capabilities.md:69]`
- **Layer-window gating source:** `PrinterState.currentLayer: Int?` (`print_stats.info.current_layer`,
  `PrinterState.kt:75`). Show babystep only when `currentLayer != null && currentLayer <= threshold`.
  **If `currentLayer` is null → babystep stays hidden** (the field is explicitly nullable and
  slicer-dependent; no `floor((Z-fh)/lh)+1` fallback for babystep gating, no time-based fallback). The
  staging note's "no time-based fallback" maps cleanly: gate strictly on the nullable layer field.
  `[VERIFIED: PrinterState.kt:75 — currentLayer nullable, slicer-dependent]`
- **Session-only semantics:** P16 never issues `Z_OFFSET_APPLY_*` / `SAVE_CONFIG` (those stay
  Calibration actions — `PrinterCommands.SAVE_CONFIG` exists but is out of scope here). The offset lives
  only in `homing_origin` for the session; reset on print end is Klipper's job, not the app's.
- **App-setting persistence (toggle + layer-count threshold):** DataStore via **`AppContainer.writeScope`
  intent helpers — NEVER `rememberCoroutineScope()`** ([[dinghy-compose-write-scope-cancellation]] — this
  trap has bitten the project on Phases 14, 15, 15.1, 15.2; do read-modify-write inside one
  `dataStore.edit`). The layer-count input is the standard numeric keyboard (D-06; allowed under
  Settings). Default enabled / 5 layers.

**⚠ MANDATORY on-device gate (SC-5, [[dinghy-display-mock-vs-reality]]):** the `SET_GCODE_OFFSET
Z_ADJUST=±n MOVE=1 ↔ homing_origin[2]` round trip MUST be verified on a **live first layer on real
hardware** (E5 = 192.168.1.120:7125 / E3 = 192.168.1.121:7125). Green host suites have repeatedly
missed real-device bugs on this project (documented 5+ times). The babystep direction sign
(Compress=−=closer) in particular must be eyeballed against actual nozzle behavior — a sign error is
invisible to unit tests and dangerous on a first layer.

### Pattern 5: Spool-aware Preheat (D-01) — §5

**Data path, verified:**
- `container.spoolmanPresent: StateFlow<Boolean>` — capability gate (`PrintStatusScreen.kt:133`).
- `container.activeSpool` → `activeSpoolId` → `spoolDetail: SpoolmanSpool` (resolved via
  `currentSpoolmanClient.getSpool(id)`, `PrintStatusScreen.kt:135-150`).
- `SpoolmanSpool.filament.settingsExtruderTemp` / `settingsBedTemp` (nullable, never fabricated —
  `spool/SpoolmanModels.kt`). `[VERIFIED: PrintStatusScreen.kt resolves spoolDetail; CONTEXT D-01 cites the fields]`

**Rule (locked):**
```
if (spoolmanPresent && spoolDetail != null) {
    val noz = spoolDetail.filament.settingsExtruderTemp   // nullable
    val bed = spoolDetail.filament.settingsBedTemp        // nullable
    if (noz != null || bed != null) {
        // fire applyPreset for whichever temps exist, guarding EACH independently
        dispatcher.dispatch(CommandRegistry.applyPreset, ApplyPresetArgs(nozzle = noz ?: <skip>, bed = bed ?: <skip>, key = "preheat_spool"))
    } else openPresetSelector()   // both null → fall through
} else openPresetSelector()       // no Spoolman / no spool
```
**Independent-guard caveat:** `ApplyPresetArgs(nozzle:Int, bed:Int)` takes non-null Ints and
`PrinterCommands.applyPreset` emits BOTH `SET_HEATER_TEMPERATURE` lines. To fire only one heater you
must either (a) add a nullable-aware preheat builder that emits only the provided line(s), or (b) call
`CommandRegistry.setHeater(SetHeaterArgs(heater, target))` per-temp for the ones that exist. **Do NOT
pass 0 for a missing temp** — that would actively turn a heater off. Recommend option (b): per-temp
`setHeater` dispatch for whichever of nozzle/bed the spool provides. Fallback selector = the Phase-5
`PresetSelector` (`TemperatureScreen.kt:406`, `onPreset → applyPreset`).

### Anti-Patterns to Avoid
- **Scattering `printState` checks** across composables — defeats the phase. Route everything through `classifyPrintStatus`.
- **Standby-as-terminal masquerade** — the existing `derivePrintStatusControls` treats `Standby + restartFilename` as terminal. The new classifier must NOT; Standby is always Standby.
- **`rememberCoroutineScope()` for the babystep setting** — recurring project disaster ([[dinghy-compose-write-scope-cancellation]]).
- **Firing `applyPreset` with a 0 for a missing spool temp** — turns the heater off instead of leaving it.
- **Pulling `machine.proc_stats` host-load into P16** — drags a whole new Moonraker surface (Phase-19 territory) into a "no new backend" UI phase.
- **A second throttle / animated ring fill** — Adreno-320 fill-rate floor; the store already samples at 250ms.
- **Undersizing fonts** — use `fsSp(baseSp, t.fs)` only ([[dinghy-font-sizes-too-small]]).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Per-state gutter control sets | A fresh control-derivation | Extend `derivePrintStatusControls` + `PrintStatusControl(Action)` | Already host-tested; pending-action debounce already handled. |
| Confirm dialogs (Cancel/EStop) | A custom dialog | `ConfirmGuard` | Full-screen guard is LAW; already wired with the exact copy slots. |
| Stats cells / heater readout colors | New cell composables | `StatGrid`/`IconTwoRowCell` + `seriesColor(0/1)` | Cross-screen color identity (nozzle=seriesColor(0)=accent) already reconciled in Phase 15.1. |
| Progress ring | A Canvas ring | `ProgressRing` | Throttle/perf already tuned for the floor. |
| Preheat material selection | A new chooser | `PresetSelector` + `MATERIAL_PRESETS` | Phase-5 keyboard-free selector, already LAW-conformant. |
| Greyed forward stubs | New disabled-tile UI | `DrawerTileSpec(dest = null)` | Established greyed pattern (Devices/Power precedent); `ShellPresenceTest` already asserts greyed tiles are no-op. |
| Spool detail fetch | New Spoolman call | The existing `currentSpoolmanClient.getSpool(id)` path in PrintStatusScreen | Already resolves `spoolDetail` once-per-id. |

**Key insight:** the design system + spine already provide every primitive. The net-new code is small
and surgical: one classifier, two state-field additions (`homing_origin[2]`, a glance temp-sensor),
two command specs (`SET_GCODE_OFFSET`, `SDCARD_RESET_FILE`), and the babystep app-setting. Everything
else is recomposition of existing parts into the four modes.

## Runtime State Inventory

> This is a UI rework, not a rename/migration. The only "runtime state" concern is the babystep
> session offset and the app-setting persistence.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | Babystep app-setting (enable toggle + layer-count) → DataStore (Preferences). | New DataStore key(s) via `AppContainer.writeScope` intent helpers. |
| Live service config | None — babystep offset is session-only on the printer (`homing_origin`); P16 never writes config. | None. |
| OS-registered state | None. | None — verified, no OS-level registration. |
| Secrets/env vars | None. | None — verified. |
| Build artifacts | None — no package rename, no pyproject/egg-info. | None — verified. |

## Common Pitfalls

### Pitfall 1: Standby treated as terminal when a stale filename exists
**What goes wrong:** copying the existing `derivePrintStatusControls` Standby branch verbatim — it
renders Files/Restart (terminal) controls when `restartFilename != null`. The new four-state model says
Standby is **always** Standby.
**Why it happens:** the current screen never had a real Standby mode; it overloaded the terminal
controls to provide a "restart last job" affordance from idle.
**How to avoid:** classify off `printState` only; provide restart-from-idle via the Standby launcher's
Files tile, not a terminal-control masquerade.
**Warning signs:** Standby gutter shows "Restart print" instead of Preheat/Power.

### Pitfall 2: Babystep round-trip green in tests, wrong on the printer
**What goes wrong:** mock confirms `SET_GCODE_OFFSET Z_ADJUST=-0.05` is sent and `homing_origin[2]`
updates, but the sign is inverted / the offset doesn't visibly move the nozzle on a real first layer.
**Why it happens:** [[dinghy-display-mock-vs-reality]] — the project's most repeated failure class
(5+ strikes). A FakeWebSocket can't model nozzle-to-bed physics.
**How to avoid:** mandatory live first-layer UAT on E5/E3; eyeball Compress=closer direction.
**Warning signs:** unit suites green; no on-device verification recorded.

### Pitfall 3: `currentLayer` null hides babystep on slicers that don't set it
**What goes wrong:** babystep never appears because `print_stats.info.current_layer` is null (slicer
didn't call `SET_PRINT_STATS_INFO`).
**Why it happens:** the field is explicitly slicer-dependent (`PrinterState.kt:75`).
**How to avoid:** this is **correct, intended behavior** (staging note: hidden if layer data
unavailable, no time-based fallback). Document it; don't "fix" it with a Z-derived layer estimate for
babystep gating. (The stats *display* may still show the Z-derived layer fallback — but babystep
*gating* must use the real nullable field.)
**Warning signs:** someone proposes a `floor((Z-fh)/lh)+1` fallback for the babystep window.

### Pitfall 4: Glance metric quietly pulls in the host-load backend
**What goes wrong:** "MCU/host temp OR host load" gets read as "model `proc_stats`," dragging the whole
Phase-19 system-info surface into P16.
**How to avoid:** §3 rule — `temperature_sensor` only; omit the line if absent; defer host-load to P19.
**Warning signs:** a new `machine.proc_stats` subscribe appears in a P16 plan.

### Pitfall 5: `applyPreset` turns a heater off on a half-temp spool
**What goes wrong:** spool exposes only nozzle temp; code passes `bed = 0`; `applyPreset` emits
`SET_HEATER_TEMPERATURE HEATER=heater_bed TARGET=0` and cools the bed.
**How to avoid:** per-temp `setHeater` for the temps that exist; never pass 0 for a missing one (§5).

### Pitfall 6: Write-scope cancellation on the babystep setting
**What goes wrong:** the enable toggle / layer-count write on a composition scope is cancelled by
same-frame nav and silently dropped on slow flash.
**How to avoid:** `AppContainer.writeScope` intent helpers, RMW inside one `dataStore.edit`
([[dinghy-compose-write-scope-cancellation]]).

## Code Examples

### Adding the `SET_GCODE_OFFSET` babystep command (pattern from existing builders)
```kotlin
// PrinterCommands.kt — new builder, clamp/validate-before-format (ASVS V5)
val BABYSTEP_STEPS = listOf(0.02, 0.05, 0.10, 0.15, 0.20)   // fixed cycle (staging note)
fun setGcodeOffsetZAdjust(deltaMm: Double): String {
    // delta must be one of ±BABYSTEP_STEPS; validate against the set, never free-text
    return "SET_GCODE_OFFSET Z_ADJUST=$deltaMm MOVE=1"
}
// CommandRegistry.kt — new gcode spec mirroring setHeater/jog at lines 316/330
// data class BabystepArgs(val deltaMm: Double)
// val babystepZ: CommandSpec<BabystepArgs> = gcode(... gcode = { PrinterCommands.setGcodeOffsetZAdjust(it.deltaMm) })
```

### Parsing the applied offset (one reducer line — gcode_move already walked)
```kotlin
// PrinterStateReducer.kt — inside the existing status.objectOrNull("gcode_move")?.let { gm -> ... } block (line ~106)
gm.doubleListOrNull("homing_origin")?.let { s = s.copy(gcodeZOffset = it.getOrNull(2)) }
// PrinterState.kt — new nullable field
// val gcodeZOffset: Double? = null   // gcode_move.homing_origin[2], applied Z offset; null until known
```

### Babystep gating (strict nullable layer, no time fallback)
```kotlin
val babystepVisible = settingEnabled &&
    state.currentLayer?.let { it <= layerThreshold } == true   // null layer → hidden
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Single `printing: Boolean` flag drives Focus/Field | `PrintStatusMode` four-state classifier | This phase | Kills scattered raw-state checks; testable. |
| Standby+restartFilename → terminal controls | Standby is always Standby; restart from launcher | This phase | Behavior change — call out in plan. |
| Heater readout amber (mockup) | nozzle/bed = `seriesColor(0/1)` = accent/pool | Phase 15.1 (already shipped) | Mockup `03-print-status.png` amber is superseded; update artboard during doc-merge. |
| `--heat` as heater identity | `--heat` = caution only; temp identity = `directional.temperature`=accent | Phase 15.1 D-13 | Preheat = accent (ordinary physical command), not caution. |

**Deprecated/outdated for this surface:**
- The `03-print-status.png` amber nozzle/bed — superseded by the accent-temperature LAW (update artboard, §6).
- Treating Standby-with-leftover-file as a terminal state — replaced by the explicit Terminal modes.

## Documentation Merge Direction (planned phase deliverable) — §6

This is **in-scope work** for Phase 16, not optional cleanup (staging note + CONTEXT specifics):
- **`docs/ui_design/README.md`** — REPLACE the stale Print Status section with the four-state model
  (Standby / Printing / Paused / Terminal) and their Focus/Field/Gutter layouts.
- **`docs/ui_design/LAYOUT.md`** — PROMOTE the **interactive-grid flexible-tile rule** to hard law,
  **scoped to grids of user-interaction surfaces ONLY** (not stat grids, lists, graphs, info frames);
  record the **babystep horizontal-3-cell-row C3 exception** alongside it.
- **`docs/ui_design/images/03-print-status.png`** + new state artboards — UPDATE to the four-state
  model and the accent-temperature resolution (drop the amber nozzle/bed).
- **DO NOT edit `THEMING.md`** — no color/shape-status rule changes this phase.

Docs need not become strict declarative law, but MUST explain the state layouts + merge direction.
**Planner: schedule this as an explicit task/wave (it's a deliverable with checker sign-off in the
UI-SPEC), not a trailing afterthought.**

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `temperature_sensor` objects (mcu/host/chamber) are NOT in the current subscribe set and need adding for the glance temp. | §3 | If one is already subscribed via a dynamic rule, the work is smaller. Verified the core set omits them (`MoonrakerSession.kt:470` ignores bare `temperature_sensor`); low risk. |
| A2 | `SpoolmanFilament.settingsExtruderTemp`/`settingsBedTemp` are the correct nullable fields on the resolved `spoolDetail`. | §5 | CONTEXT D-01 asserts these exist; not re-opened `SpoolmanModels.kt` line-by-line this pass. If the accessor path differs, the preheat wiring shifts — verify the exact property names when planning. |
| A3 | The babystep step cycle values are the on-screen *step magnitudes* in mm (0.02–0.20), dispatched as `Z_ADJUST=±step`. | §4 | Locked by staging note; standard Klipper babystep semantics. Low risk; confirm sign on device. |
| A4 | `homing_origin` is the live applied gcode offset (index [2] = Z). | §4 | Confirmed by caps doc + standard Klipper; low risk. Verify the array is `[X,Y,Z,E]`/`[X,Y,Z]` ordering on device (Z at index 2). |

## Open Questions

1. **Which existing composable becomes the canonical "stats frame" for Terminal — `StatGrid` or `LastJobCard`?**
   - What we know: staging note says "reuse the **Printing** stats-frame, omitting live-only fields." That points to `StatGrid`.
   - What's unclear: `LastJobCard` already renders rich terminal-relevant stats (status/finished/elapsed/filament/slicer) and a thumbnail background. There's overlap.
   - Recommendation: make `StatGrid` (the Printing frame) the single stats component per the staging note; reuse `LastJobCard`'s thumbnail-hero treatment for the Terminal **Focus** hero, not its stats. Planner decides; both are reusable.

2. **Independent per-temp Preheat: add a nullable preheat builder, or per-temp `setHeater`?**
   - Recommendation: per-temp `setHeater` (option b, §5) — no new builder, no 0-passing risk.

3. **Does Matthew want the host-load glance fallback in P16, or defer to P19?**
   - Recommendation: defer to P19 (§3). Flag as an explicit scope question in planning — it's the one place P16 could quietly grow a backend.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Windows Gradle build (`E:\Android\gw.bat`) | All builds (./gradlew won't run from WSL) | ✓ | AGP 8.7.x / JDK 21 | — |
| flox test device (LineageOS 18.1 / API 30, real Adreno 320) | SC-3 perf + SC-5 babystep UAT | ✓ | API 30 | — (no substitute; emulators lie about old-GPU perf) |
| Ender 5 Plus Moonraker | Live babystep first-layer UAT | ✓ | 192.168.1.120:7125 | E3 Pro 192.168.1.121:7125 |
| Ender 3 Pro Moonraker | Live UAT alternate | ✓ | 192.168.1.121:7125 | E5 |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** live UAT can run on either printer; both have klicky probes and report `homing_origin`/`current_layer` (OrcaSlicer sets `info.current_layer` per caps doc).

## Validation Architecture

> Project uses host JVM unit tests + on-device instrumented tests. Nyquist validation enabled (no config override found).

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit (host unit) + AndroidX instrumented (`connectedAndroidTest`) — existing pattern (`PrintStatusControlModel`, `PrintStatusHolder` are already host-tested) |
| Config file | Gradle (`app/build.gradle.kts`); run via `E:\Android\gw.bat` |
| Quick run command | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.printstatus.*'"` (list classes explicitly — glob false-fails on this AGP, per memory) |
| Full suite command | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest"` |

### Phase Requirements → Test Map
| Behavior | Test Type | Automated Command | File Exists? |
|----------|-----------|-------------------|-------------|
| `classifyPrintStatus` maps all 6 raw states + Standby-stays-Standby + klippy-not-terminal | unit (pure) | `gw.bat :app:testDebugUnitTest --tests '...PrintStatusModeTest'` | ❌ Wave 0 |
| Babystep gating: null layer hides; ≤threshold shows | unit (pure) | included above | ❌ Wave 0 |
| `SET_GCODE_OFFSET` builder sign/format (Compress=−, Expand=+, step validated) | unit (pure) | `--tests '...PrinterCommandsTest'` (exists — extend) | ✅ extend |
| Spool-aware Preheat selection (direct vs selector vs per-temp guard) | unit (pure) | `--tests '...PreheatTest'` | ❌ Wave 0 |
| Babystep round-trip + sign on a live first layer | **manual on-device (SC-5)** | flox + E5/E3 — NOT automatable | manual gate |
| Adreno-320 perf no-regression (SC-3) | on-device gfxinfo / eyeball | flox release build | manual gate |

### Sampling Rate
- **Per task commit:** the quick `printstatus` + `command` unit run.
- **Per wave merge:** full `:app:testDebugUnitTest`.
- **Phase gate:** full suite green + **live babystep UAT on flox/E5** (SC-5) + perf eyeball (SC-3) before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `PrintStatusModeTest.kt` — classifier mapping (all 6 states + Standby/klippy edges) (REQ: classifier core)
- [ ] Babystep gating + step-cycle tests (REQ: babystep)
- [ ] Preheat selection tests (REQ: D-01)
- [ ] Extend existing `PrinterCommandsTest` for `SET_GCODE_OFFSET` + (new) `SDCARD_RESET_FILE`
- [ ] (Wave-0 RED scaffolds MUST compile day-one — `fail()` bodies, no refs to unbuilt symbols; [[dinghy-wave0-red-scaffold-compile.md]])

## Security Domain

> `security_enforcement` not explicitly false → enabled. This is a local-LAN printer-control surface; the relevant control is input/command-injection safety, already established by `PrinterCommands`.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Moonraker trusted-client/API-key handled at the session layer, not this screen. |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation | **yes** | `PrinterCommands` clamps/validates every numeric param before formatting into a gcode string; the babystep step is one of 5 fixed values (validate-against-set, never free-text); the layer-count input is a bounded positive integer. |
| V6 Cryptography | no | — |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Gcode command injection via interpolated user input | Tampering | Never concatenate user strings into a script; only fixed identifiers + clamped numerics (existing `PrinterCommands` discipline). The new `SET_GCODE_OFFSET`/`SDCARD_RESET_FILE` builders follow the same rule. |
| Cold-extrude / unsafe motion | Tampering (physical) | Babystep `MOVE=1` is a small Z jog gated to the early-layer window; session-only; no `SAVE_CONFIG`. |

## Sources

### Primary (HIGH confidence — verified against the real repo this session)
- `ui/printstatus/PrintStatusScreen.kt` (942 lines), `PrintStatusControlModel.kt`, `PrintStatusHolder.kt` — the screen + control derivation + cell model.
- `state/PrinterState.kt`, `state/Capabilities.kt`, `state/PrinterStateReducer.kt` (gcode_move block @106), `state/DeriveCapabilities.kt` (subscribe set @50-90) — the headless spine + what's parsed/subscribed.
- `net/MoonrakerSession.kt` (handshake/subscribe @440-510; temperature_sensor ignore @470) — what the spine actually requests.
- `command/CommandRegistry.kt` (@316/330 gcode specs) + `command/PrinterCommands.kt` (builders, clamp discipline) — command pattern; confirmed NO `SDCARD_RESET_FILE`, NO `SET_GCODE_OFFSET` today.
- `ui/temperature/TemperatureScreen.kt` (PresetSelector @406, applyPreset @227) — D-01 fallback path.
- `ui/shell/AppDrawer.kt` (DrawerTileSpec/greyed pattern @122-161) — D-02 stubs.
- `docs/moonraker-capabilities.md` (@69 homing_origin on gcode_move; @47/200 MCU/chamber temp sensors; @75 current_layer slicer-dependent) — field reality on the dev printers.
- `.planning/ROADMAP.md` (Phase 16 SC; Phase 19 = system_info/proc_stats) — scope boundary for host-load.
- Staging note + `16-CONTEXT.md` + `16-UI-SPEC.md` — the locked design (not re-litigated).

### Secondary (MEDIUM)
- Project memory lessons: [[dinghy-compose-write-scope-cancellation]], [[dinghy-display-mock-vs-reality]], [[dinghy-font-sizes-too-small]], [[dinghy-wave0-red-scaffold-compile]] — recurring traps.

### Tertiary (LOW)
- None — no WebSearch needed; everything is in-repo or in the locked specs.

## Metadata

**Confidence breakdown:**
- Classifier seam (§1): HIGH — verified the exact `PrintState` enum, the distinct `klippyState` axis, and the existing control derivation.
- Rework strategy (§2): HIGH — read all 942 lines; harvest map is concrete.
- Glance metric (§3): HIGH on field availability (verified subscribe set omits temperature_sensor; proc_stats not modeled); the *recommendation* (defer host-load) is a judgment call flagged as an open question for Matthew.
- Babystep wiring (§4): HIGH — `gcode_move` already subscribed + walked; `homing_origin` confirmed on the object; net-new = one field + one reducer line + one command. Sign/direction MUST be device-verified (SC-5).
- Spool preheat (§5): HIGH on path; MEDIUM on exact `SpoolmanFilament` property names (A2 — verify when planning).
- Pitfalls (§7): HIGH — drawn from verified code + the project's documented recurring failures.

**Research date:** 2026-06-06
**Valid until:** ~30 days (stable mature codebase; the only churn risk is parallel-session edits to PrintStatusScreen/spine).

## RESEARCH COMPLETE

**Phase:** 16 - Home / Print-Status Redesign
**Confidence:** HIGH

### Key Findings
- **Four things are net-new, not "reuse what's there":** (1) `gcode_move.homing_origin[2]` parsing (one reducer line — object already subscribed/walked), (2) a `temperature_sensor` glance temp (not currently subscribed), (3) `SET_GCODE_OFFSET` command spec, (4) `SDCARD_RESET_FILE` command spec. None exist in the model/registry today.
- **Restructure around a pure `PrintStatusMode` classifier** (host-testable, ADR-0001) and reuse every primitive (StatGrid/ProgressRing/StopButton/ConfirmGuard/PresetSelector/DrawerTileSpec) — do NOT rewrite, do NOT touch the Views GraphView path (SC-3).
- **Behavior change to call out:** the existing `derivePrintStatusControls` treats Standby-with-leftover-file as *terminal*; the new classifier must keep Standby = Standby (restart moves to the launcher).
- **Glance metric resolved:** prefer a real `temperature_sensor` reading; **defer host-load to Phase 19** (don't pull `machine.proc_stats` into a UI phase) — flagged as a scope question for Matthew.
- **Babystep SC-5 is a mandatory live first-layer on-device gate** — sign (Compress=−=closer) and `homing_origin[2]` round-trip can't be proven by mocks (the project's #1 recurring failure class).

### File Created
`.planning/phases/16-home-print-status-redesign/16-RESEARCH.md`

### Confidence Assessment
| Area | Level | Reason |
|------|-------|--------|
| Standard Stack (reused components) | HIGH | All verified in real source. |
| Architecture (classifier + rework) | HIGH | Read all 942 lines + the spine. |
| Pitfalls | HIGH | Verified code + documented recurring project failures. |

### Open Questions
- Which composable is the canonical Terminal stats frame (StatGrid vs LastJobCard) — recommend StatGrid.
- Host-load glance fallback in P16 vs defer to P19 — recommend defer (scope question for Matthew).
- Exact `SpoolmanFilament` property names for the per-temp Preheat guard (A2) — verify when planning.

### Ready for Planning
Research complete. The planner can now create PLAN.md files with concrete seams, field/command additions, the classifier extraction, the reuse map, the doc-merge deliverable, and the mandatory on-device babystep gate.
