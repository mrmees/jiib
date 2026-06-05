# Phase 17: Fine-Tune / Live-Adjust Panel - Context

**Gathered:** 2026-06-05
**Status:** Ready for planning

> **Pre-discussed out of dependency order** (parallel track): Phase 17 was discussed while Phase 15
> (Theme System & Settings) clarifications continue in another session. These decisions are about the
> Fine-Tune feature's *behavior* and are independent of 15/16 being built — at most, 15/16 restyle the
> surface and provide the entry point. **Layout + entry point are intentionally left LOOSE** (see Deferred).

<domain>
## Phase Boundary

A lean, keyboard-free **live-adjust panel for the things you reach for mid-print**, organized by
failure-mode into two categories:

- **Motion** — what moves the head: speed factor, acceleration, max velocity, square-corner-velocity.
- **Extrusion** — what affects the filament: flow factor, pressure advance, firmware retraction (if
  present), part-cooling fan.

Each control is a capability-gated scrubber/stepper that dispatches immediately and confirms by the
resulting printer-object state flip (the Move/Extrude model). Temps/layer/progress appear as **clickable
readouts** (temps jump to the existing Temp panel) — they are NOT re-implemented here.

**Explicitly NOT this phase:** Z babystep (→ Phase 16, a Print-Status conditional control), temp *setters*
(→ Phase 5 Temp panel), generic/aux fans + LEDs + output pins (→ Phase 18 Output Controls), Pause/Resume
(→ Phase 7 Print-Status gutter), and mid-print object exclusion (→ v2 wishlist).

</domain>

<decisions>
## Implementation Decisions

### Scope & Organization
- **D-01:** Lean **failure-mode tuner**, two categories — **Motion** (moves the head) vs **Extrusion**
  (affects the filament). This is the user's mental model ("is it a movement problem or a flow problem?").
  NOT a full mid-print cockpit: temps and pause/resume are *linked/reused*, never duplicated as second
  control surfaces.
- **D-02:** **Always available** when connected (not print-gated) — set factors before/at print start and
  live-adjust during. Capability-gating hides controls the active printer doesn't expose; an idle printer
  just shows current values.

### Motion controls
- **D-03:** Speed factor — `M220 S<n>` ↔ `gcode_move.speed_factor`. Range **25–200%, step 5%**, default 100%.
  (`speed_factor` is ALREADY parsed in `PrinterState`.)
- **D-04:** Motion limits (power-user, capability-present on any `toolhead`) — acceleration
  (`M204` / `SET_VELOCITY_LIMIT ACCEL` ↔ `toolhead.max_accel`), max velocity
  (`SET_VELOCITY_LIMIT VELOCITY` ↔ `toolhead.max_velocity`), square-corner-velocity
  (`SET_VELOCITY_LIMIT SQUARE_CORNER_VELOCITY` ↔ `toolhead.square_corner_velocity`). Exact
  ranges/steps = Codex defaults, planner verifies vs Klipper (Claude's discretion below).

### Extrusion controls
- **D-05:** Flow factor — `M221 S<n>` ↔ `gcode_move.extrude_factor`. Range **WIDE 50–150%, step 1–2%**,
  default 100% (rescue headroom over the conservative 80–120%; scrubber clamps). (`extrude_factor` is
  ALREADY parsed in `PrinterState`.)
- **D-06:** Pressure advance — `SET_PRESSURE_ADVANCE ADVANCE=<n>` ↔ `extruder.pressure_advance`
  (`smooth_time` secondary/advanced — planner's call). **Single-extruder assumption for v1** (no
  active-extruder selector; our test printers are single-extruder).
- **D-07:** Firmware retraction — `SET_RETRACTION ...` ↔ `firmware_retraction.*`. **Capability-gated:
  only appears when `[firmware_retraction]` is configured** (invisible on most setups).
- **D-08:** Part-cooling fan — `M106 S<0..255>` ↔ `fan.speed`. **Part-cooling `fan` ONLY.** Range 0–100%,
  step 5%. Generic/aux fans (`fan_generic`/`heater_fan`/`controller_fan`), LEDs, output pins → Phase 18.

### Interaction model
- **D-09:** Keyboard-free **scrubber/stepper** pages (reuse the PRIM-01 `ScrubberPage`). Non-destructive
  tweaks **dispatch immediately and confirm by the printer-object state flip** — **NO ConfirmGuard**
  (mirrors the Move/Extrude panels, not the calibration SAVE_CONFIG gate).
- **D-10:** **Per-control reset.** Speed/flow → **100%**; motion limits + pressure advance → the printer's
  **configured-default baseline** (read from the `configfile` object / each object's configured value).

### Readouts
- **D-11:** Temps shown as **clickable readouts that navigate to the Temp panel** (no temp setter here).
  Plus current layer (`print_stats.info.current_layer/total_layer`), live speed/flow %, and print progress.

### Claude's Discretion
- Exact ranges/steps/defaults for accel / max-velocity / square-corner-velocity (start from Codex's
  suggestions — accel 500–20000/step 100–500; velocity 20–500/step 5–10; SCV 1–20/step 0.5–1 — and verify
  against the Klipper config reference at plan time).
- Whether PA `SMOOTH_TIME` is exposed alongside `ADVANCE`.
- Whether the motion-limit group is shown expanded or behind an "advanced" disclosure.
- The precise capability-gate predicates (which `printer.objects.list` entries gate each control).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Klipper / Moonraker command + object truth
- `docs/commands/klipper-gcode.md` — gcode reference for `M220`/`M221`/`M204`/`SET_VELOCITY_LIMIT`/`SET_PRESSURE_ADVANCE`/`SET_RETRACTION`/`M106`.
- `docs/moonraker-capabilities.md` — live printer-object field shapes (`gcode_move`, `toolhead`, `extruder`, `fan`, `firmware_retraction`) and per-printer availability.
- The Phase-6 in-code command registry + per-printer capability/availability matrix (the canonical source for capability-gating; see `docs/commands/` + `CommandRegistry`).

### UI law
- `docs/ui_design/CLAUDE.md`, `docs/ui_design/LAYOUT.md`, `docs/ui_design/THEMING.md` — Focus/Field/Gutter grammar, intent colors, role tokens, `fsSp` scale. **Note:** the theme system is itself being reworked in Phase 15 — at plan time, confirm against whatever 15 lands.

### Code templates (read for pattern, not to copy verbatim)
- `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeHolder.kt` + `ExtrudeScreen.kt` — the exact headless-holder + screen + scrubber + immediate-dispatch + cold-guard pattern to mirror.
- `app/src/main/java/works/mees/dinghy/ui/move/MoveHolder.kt` + `MoveScreen.kt` — sibling pattern.
- `app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt` — PRIM-01 single-setting page.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`ExtrudeHolder`/`ExtrudeScreen` + `MoveHolder`/`MoveScreen`** — the precise template: a toolkit-agnostic
  headless holder folds dispatched-vs-applied state, the screen renders scrubber controls, and a tweak is
  confirmed by the printer-object state flip (not a bare ack). Fine-Tune is "another Move/Extrude."
- **`ScrubberPage` (PRIM-01)** — the keyboard-free single-setting page every control opens into.
- **`CommandDispatcher` + `CommandRegistry` (Phase 6)** — dispatch path + the canonical command specs and
  capability gating (add `gcode()`-factory specs for M220/M221/M204/SET_VELOCITY_LIMIT/SET_PRESSURE_ADVANCE/
  SET_RETRACTION/SET_FAN_SPEED, mirroring Phase 9's calibration command specs).
- **`PrinterState` already carries `speedFactor` + `extrudeFactor`** (from `gcode_move`) — M220/M221 live
  state is free; the scrubbers read these for current value + reset-detection.
- **`DeriveCapabilities` / `Capabilities`** — already enumerates `gcode_move`, `toolhead`, fans; the
  capability-gate source for which controls to show.

### Established Patterns
- Capability-gated command specs via the `gcode(...)` factory + object-presence predicates (Phase 9).
- Cold-extrude / homed guards (Extrude) — Fine-Tune controls are mostly always-safe, but PA/flow tie to a
  hot extruder; reuse the guard idiom where relevant.
- Immediate dispatch + state-flip confirm, **no ConfirmGuard** for non-destructive tweaks.

### Integration Points
- **Entry point:** `PrintStatusControlAction.Tune` is currently a disabled placeholder
  (`PrintStatusScreen.kt`: `PrintStatusControlAction.Tune -> Unit`). This phase wires it to open Fine-Tune.
  The exact nav (a `Dest.FineTune` route + AppShell arm, and/or a drawer tile) is LOOSE pending the Phase-16
  Home/Print-Status redesign — see Deferred.
- **New live-state to parse** (reducer additions): `extruder.pressure_advance`/`smooth_time`, `fan.speed`,
  `toolhead.max_accel`/`max_velocity`/`square_corner_velocity`, `firmware_retraction.*`. (`speed_factor`/
  `extrude_factor` already exist.)

</code_context>

<specifics>
## Specific Ideas

- **User's organizing model (D-01):** split the screen into **Motion** (things that move the head around)
  and **Extrusion** (things that affect the filament). Drove both the layout grammar and the
  control-vs-belongs-elsewhere boundaries.
- **Outside input:** the control set, ranges, and scope boundaries were cross-checked against an
  ideation pass from **Codex (gpt-5.x)**, which ranked must-have / nice-to-have / belongs-elsewhere and
  independently drew the same Fine-Tune ↔ Output-Controls ↔ Calibration boundaries. Notable confirmations:
  part-fan-here / generic-fans-elsewhere; save-Z-to-config is a Calibration action, never mid-print;
  babystep deserves prominent placement (→ realized as the Phase-16 first-layers conditional).

</specifics>

<deferred>
## Deferred Ideas

- **Z babystep → Phase 16 (Home / Print-Status Redesign).** Not a Fine-Tune row — it becomes a
  **conditional Print-Status control shown only for the first ~10 layers** (auto-appears during the squish
  window, hides after). Carry forward Codex's mechanics: incremental `SET_GCODE_OFFSET Z_ADJUST=±n MOVE=1`
  ↔ `gcode_move.homing_origin[2]`, step 0.01 fine / 0.05 coarse, **"nozzle closer / away"** labels (not bare
  ±), live applied-offset readout, **session-only** (saving via `Z_OFFSET_APPLY_PROBE/ENDSTOP` + `SAVE_CONFIG`
  stays a Calibration action — never mid-print).
- **Mid-print object exclusion (`EXCLUDE_OBJECT`) → v2 wishlist.** Cancel one failed part, let the rest of
  the plate finish. Destructive; needs a different UI (object picker + confirm guard) and depends on
  `[exclude_object]` + the slicer emitting labeled objects. More "print action" than "tune."
- **Layout + entry point → planner / Phase 15+16.** The Motion/Extrusion *grouping* is locked; the exact
  realization (tabs vs two sections vs Focus/Field) and the entry affordance depend on the Home redesign
  (16) and the theme system (15). Plan the controls/behavior now; finalize chrome against 15/16.
- **Temp *setters* → Phase 5 Temp panel; generic fans/LEDs/output pins/enclosure → Phase 18; Pause/Resume →
  Phase 7 Print-Status gutter.** Surfaced as readouts/links here, not re-implemented.

</deferred>

---

*Phase: 17-fine-tune-live-adjust-panel*
*Context gathered: 2026-06-05*
