# Phase 17: Fine-Tune / Live-Adjust Panel - Context

**Gathered:** 2026-06-05
**Revised:** 2026-06-06 — folded in `phase-17-fine-tune-staging.md` (newer than the original parallel-track
discussion). The staging notes **lock the previously-LOOSE layout/interaction**, add `minimum_cruise_ratio`
+ `smooth_time` controls, replace the ScrubberPage interaction model with **value tiles**, and specify icons,
increments, and reset. Conflicts with the original D-09 (ScrubberPage) were resolved in discussion (see below).
**Status:** Ready for planning

> **Originally pre-discussed out of dependency order** (parallel track, while Phase 15 was in flight).
> Phase 16 (Home/Print-Status) is now COMPLETE, so the entry point + layout that were left LOOSE are now
> locked here.

<domain>
## Phase Boundary

A lean, keyboard-free **live-adjust panel for the things you reach for mid-print**, organized by
failure-mode into two categories:

- **Motion** — what moves the head: speed factor, max velocity, max acceleration, minimum cruise ratio,
  square-corner velocity.
- **Extrusion** — what affects the filament: flow factor, pressure advance, smooth time, part-cooling fan,
  firmware retraction (if present, via its own mini-screen).

Each control is a capability-gated **value tile** that dispatches immediately on a ±tap and confirms by the
resulting printer-object state flip (the Move/Extrude model). It is **not** a Focus-heavy status screen.

**Screen stack (locked):**
1. **Fine-Tune Hub** — two large entries only (Motion, Extrusion). **No live summary values on the hub.**
2. **Motion group screen** — control-surface Field layout, tiles adjusted in place.
3. **Extrusion group screen** — same; surfaces the Firmware-Retraction entry only when configured.
4. **Firmware-Retraction mini-screen** — opened from the Extrusion screen, only when `[firmware_retraction]`
   is present.

**Explicitly NOT this phase:** Z babystep (shipped in Phase 16 as a Print-Status conditional control), temp
*setters* (→ Phase 5 Temp panel), generic/aux fans + LEDs + output pins (→ Phase 18 Output Controls),
Pause/Resume (→ Phase 7 Print-Status gutter), mid-print object exclusion (→ v2 wishlist), and the
shared-component / Move-screen rework (→ deferred todos, see below).

</domain>

<decisions>
## Implementation Decisions

### Scope & Organization
- **D-01:** Lean **failure-mode tuner**, two categories — **Motion** (moves the head) vs **Extrusion**
  (affects the filament). The user's mental model ("is it a movement problem or a flow problem?"). NOT a full
  mid-print cockpit: temps and pause/resume are *linked/reused*, never duplicated as second control surfaces.
- **D-02:** **Always available** when connected (not print-gated) — set factors before/at print start and
  live-adjust during. Capability-gating hides controls the active printer doesn't expose; an idle printer
  just shows current values.

### Motion controls (5, always visible when the toolhead exposes them)
- **D-03:** Speed % — `M220 S<n>` ↔ `gcode_move.speed_factor`. Default 100%, step **5%**.
  (`speed_factor` is ALREADY parsed in `PrinterState`.)
- **D-04:** Max velocity — `SET_VELOCITY_LIMIT VELOCITY=<n>` ↔ `toolhead.max_velocity`. Step **10 mm/s**.
- **D-05:** Max acceleration — `SET_VELOCITY_LIMIT ACCEL=<n>` (or `M204`, planner verifies) ↔
  `toolhead.max_accel`. Step **100 mm/s²**.
- **D-06:** **Minimum cruise ratio (NEW)** — `SET_VELOCITY_LIMIT MINIMUM_CRUISE_RATIO=<n>` ↔
  `toolhead.minimum_cruise_ratio`. Step **5 percentage points**. *(This field was missing from the original
  context — added per staging. Planner verifies exact param + range on the pinned Klipper version.)*
- **D-07:** Square-corner velocity — `SET_VELOCITY_LIMIT SQUARE_CORNER_VELOCITY=<n>` ↔
  `toolhead.square_corner_velocity`. Step **0.1 mm/s**.

### Extrusion controls
- **D-08:** Flow % — `M221 S<n>` ↔ `gcode_move.extrude_factor`. Default 100%, step **1%**. Range stays WIDE
  (≈50–150%, rescue headroom; scrubber/clamp at plan time). (`extrude_factor` ALREADY parsed in `PrinterState`.)
- **D-09:** Pressure advance — `SET_PRESSURE_ADVANCE ADVANCE=<n>` ↔ `extruder.pressure_advance`. Step **0.001**.
  **Single-extruder assumption for v1** (no active-extruder selector; test printers are single-extruder).
- **D-10:** **Smooth time (NEW, separate control)** — `SET_PRESSURE_ADVANCE SMOOTH_TIME=<n>` ↔
  `extruder.smooth_time`. Step **0.01 s**. **Its own tile, NOT a combined PA editor.** *(Resolves the original
  "planner's call" — staging makes it first-class.)*
- **D-11:** Part-cooling fan — `M106 S<0..255>` ↔ `fan.speed`. **Part-cooling `fan` ONLY.** Range 0–100%,
  step **5%**. Generic/aux fans, LEDs, output pins → Phase 18.
- **D-12:** Firmware retraction — **own mini-screen**, capability-gated on `[firmware_retraction]`. Four
  controls, all `SET_RETRACTION ...` ↔ `firmware_retraction.*`:
  - Retract length (step **0.1 mm**), Unretract extra length (step **0.1 mm**),
    Retract speed (step **1 mm/s**), Unretract speed (step **1 mm/s**).
  - **No Z-hop** (not part of standard Klipper firmware retraction).

### Interaction model — VALUE TILES (supersedes the original ScrubberPage D-09)
- **D-13:** Adjustment is done **directly on the group screen via compact value tiles** — no detail/scrubber
  page per control. Each tile shows: **setting-specific icon · setting name · current (printer-reported)
  value · generic − action · generic + action**.
- **D-14:** **Pure nudge.** The −/+ arrows step the fixed increment, **one command per tap**, dispatch
  **immediately**. **Tapping the value does nothing.** **No long-press repeat.** **No Apply/Cancel, no
  ConfirmGuard** (non-destructive tweaks, mirrors Move/Extrude). UI confirms by observing the printer-object
  **state flip**, not a bare ack. *(Big sweeps = many taps is accepted — mid-print is nudging, not sweeping.
  No coarse/fine toggle this phase.)*
- **D-15:** **Busy model — lock the whole group screen.** While a tuning command is in flight, disable/busy
  the **entire group screen** until the app sees the ready/confirmed state again. **Busy/disabled tile styling
  is sufficient** — no separate global busy indicator. *(Chosen over per-tile or optimistic for simplicity +
  no race; the cost — serialized rapid nudging — is accepted.)*

### Reset
- **D-16:** **Long-press the displayed value = reset that tuner**, dispatched **immediately, no prompt**.
  - Reset = the printer's **config baseline**, not Dinghy hardcoded defaults. Source of truth: live value from
    `printer.<object>`; configured baseline from `printer.configfile.settings.<section>`. Dinghy may send the
    normal setter using the baseline value.
  - **Speed and flow** are protocol-neutral resets to **100%** (`M220 S100` / `M221 S100`) — they're live
    overrides, not config-baseline fields.
  - **Part-cooling fan** resets to its printer config/default value, **never assumed off** (planner verifies
    where the baseline lives — see Claude's Discretion).

### Icons
- **D-17:** **Per-control glyphs are CHOSEN in the staging note** (`phase-17-fine-tune-staging.md` §Icons) —
  these are authoritative, NOT gaps to re-ask. Render via the established **per-icon vector-drawable pattern**
  (`res/drawable/*.xml` + `painterResource`); the app uses individual vector drawables, **NOT the Material
  Symbols font**, so no font dependency / no Adreno-320 concern. **`img/material-icon-bucket.json` is the
  canonical icon registry going forward** — fold these chosen glyphs into it; for any NEW need not already
  chosen, ask the owner rather than inventing.
  - **Motion:** Speed % → `speed` · Max velocity → `arrow_shape_up_stack_2` · Max acceleration → `sprint` ·
    Minimum cruise ratio → `directions_boat` · Square-corner velocity → `rounded_corner`.
  - **Extrusion:** Flow % → `output_circle` · Pressure advance → `text_select_move_forward` ·
    Smooth time → `avg_time` · Part-cooling fan → `mode_fan` · Firmware-retraction entry → `input_circle`.
  - **Generic ± nudge:** `add` (+) / `remove` (−) — every tile shares them (staging said "generic add/subtract
    icons" without naming; plain +/− chosen over up/down arrows). · **Back** → `keyboard_return`.
  - **FW-retraction sub-controls** (retract length/speed, unretract extra length/speed): not separately named
    in staging — planner may reuse `input_circle`/`output_circle`/`sprint`, owner confirms if they want
    distinct glyphs.
  - **Planner verification (per staging's own instruction):** confirm the two unusual ligatures
    **`arrow_shape_up_stack_2`** and **`text_select_move_forward`** are real Material Symbols Outlined names;
    fall back to a near equivalent only if a name doesn't exist.
  - **Bucket note conflict to reconcile:** the bucket currently notes `sprint` = "generic speed fields," but
    staging uses `speed` for Speed % and `sprint` for Max acceleration. Staging per-field assignment wins;
    the bucket's `sprint` note should be tidied.
- **D-17b:** **`img/spool.svg`** (black spool + red filament path) is the **canonical filament-roll graphic
  going forward**, tinted to each spool's color (the committed red = an example color). Primarily a Spoolman
  surface concern; recorded here so it isn't lost. Not a Fine-Tune tile, but it's the project-wide spool glyph.

### Default tap increments (app-defined, fixed)
- **D-18:** Increments are **fixed and app-defined** this phase (per-control values in D-03..D-12 above).
  **Increment customization is deferred** to a future Settings section — do NOT build it in P17 unless
  explicitly re-scoped.

### Component build
- **D-19:** Build the value tile as a **clean, shared-ready component**, but **wire it ONLY in Fine-Tune this
  phase.** The full two-mode C2 3-cell increment-picker (THEMING.md), the Move-screen retrofit to it, and the
  Move-Z vertical-layout rework (C3) all **remain deferred todos** (see Deferred). The Fine-Tune tile is a
  C2-*derived* 3-cell (arrows do the action, tap inert, long-press = reset) — it intentionally diverges from
  C2 mode-(b)'s "tap = adjust" semantics, which is fine for this phase.

### Readouts
- **D-20:** Temps remain **links, not setters** wherever shown (navigate to the Phase-5 Temp panel; no temp
  setter here). The **hub carries no summary values** (D, staging). Whether the Motion/Extrusion group screens
  carry a small layer/progress/temp readout strip is **planner discretion** within the Field grammar — keep it
  light and don't re-implement status.

### Entry point / navigation
- **D-21:** Wire from the stubbed **`PrintStatusControlAction.Tune`** (currently
  `PrintStatusScreen.kt: PrintStatusControlAction.Tune -> Unit`) to open the Fine-Tune Hub. Add a
  `Dest.FineTune` route + AppShell arm (and/or a drawer tile — planner's call, conform to the Phase-16 shell).

### Claude's Discretion
- Exact **ranges + clamping** for every tuner (start from the staging increments; verify bounds against the
  Klipper config reference at plan time). Staging seeds: accel, max-velocity, SCV, minimum-cruise-ratio.
- Exact **config-baseline paths** for each reset (staging lists candidates: `configfile.settings.printer.{
  max_velocity,max_accel,minimum_cruise_ratio,square_corner_velocity}`, `configfile.settings.extruder.{
  pressure_advance,pressure_advance_smooth_time}`, `configfile.settings.fan.*`, `configfile.settings.
  firmware_retraction.*`). **Part-fan baseline is genuinely fuzzy** — Klipper `fan` has no persistent runtime
  "configured speed"; planner resolves what "reset, never assumed off" means in practice (likely returns
  control to the print's last-commanded value, or the config default if one exists).
- Whether the Motion-limit group is shown expanded or behind an "advanced" disclosure (5 tiles per group must
  stay touch-friendly in **portrait AND landscape** — staging open item).
- The precise capability-gate predicates (which `printer.objects.list` / `Capabilities` entries gate each tile).
- Exact Material Symbols ligature resolution + fallbacks (D-17).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Primary spec
- `../parallel_dinghy/phase-17-fine-tune-staging.md` (out-of-repo, relative to repo root =
  `/mnt/e/claude/personal/github/parallel_dinghy/phase-17-fine-tune-staging.md`) — the **primary design
  spec**: screen stack, tile grammar, control mappings, icons, increments, reset, and the "Open Planner Work"
  checklist. **Read first.**

### Klipper / Moonraker command + object truth
- `docs/commands/klipper-gcode.md` — gcode reference for `M220`/`M221`/`M204`/`SET_VELOCITY_LIMIT` (incl.
  `MINIMUM_CRUISE_RATIO`)/`SET_PRESSURE_ADVANCE` (incl. `SMOOTH_TIME`)/`SET_RETRACTION`/`M106`.
- `docs/moonraker-capabilities.md` — live printer-object field shapes (`gcode_move`, `toolhead`, `extruder`,
  `fan`, `firmware_retraction`, `configfile.settings`) and per-printer availability.
- `docs/commands/catalog.json` + `docs/commands/printer-matrix.json` + the Phase-6 `CommandRegistry` — the
  canonical source for command specs + capability gating.

### Iconography (primary source)
- `img/material-icon-bucket.json` — **canonical icon source** (owner-maintained Material Symbols Outlined
  bookmarks + usage notes). Every tile/affordance glyph MUST come from here; missing icons get added to the
  bucket by the owner, not invented (D-17).
- `img/spool.svg` — canonical filament-roll graphic, color-tinted per spool (D-17b).

### UI law
- `docs/ui_design/CLAUDE.md`, `docs/ui_design/LAYOUT.md`, `docs/ui_design/THEMING.md` — Focus/Field/Gutter
  grammar, intent colors (C1/C5 — arrows performing the screen's expected action = accent), role tokens,
  `fsSp` scale, and the **C2 3-cell increment-picker** + **C3 vertical-quantity** criteria (the Fine-Tune tile
  is C2-derived; full C2/C3 adoption is deferred — D-19).

### Code templates (read for pattern, not to copy verbatim)
- `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeHolder.kt` + `ExtrudeScreen.kt` — headless-holder +
  immediate-dispatch + state-flip-confirm + cold-guard pattern to mirror.
- `app/src/main/java/works/mees/dinghy/ui/move/MoveHolder.kt` + `MoveScreen.kt` — sibling pattern; also the
  reference for `painterResource` tile icons.

### Deferred-todo references (NOT in P17 scope — see Deferred)
- `.planning/todos/2026-06-05-increment-picker-3cell-component.md` (C2 shared component build).
- `.planning/todos/2026-06-05-move-z-vertical-layout-rework.md` (C3 Move-Z vertical layout).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`ExtrudeHolder`/`ExtrudeScreen` + `MoveHolder`/`MoveScreen`** — the precise template: a toolkit-agnostic
  headless holder folds dispatched-vs-applied state; the screen renders controls; a tweak is confirmed by the
  printer-object state flip (not a bare ack). Fine-Tune is "another Move/Extrude," but with **value tiles**
  instead of opening a ScrubberPage.
- **`CommandDispatcher` + `CommandRegistry` (Phase 6)** — dispatch path + canonical command specs + capability
  gating (add `gcode()`-factory specs for M220/M221/M204/SET_VELOCITY_LIMIT [incl. MINIMUM_CRUISE_RATIO]/
  SET_PRESSURE_ADVANCE [incl. SMOOTH_TIME]/SET_RETRACTION/SET_FAN_SPEED, mirroring Phase 9's calibration specs).
- **`PrinterState` already carries `speedFactor` + `extrudeFactor`** (from `gcode_move`) — M220/M221 live
  state is free; tiles read these for current value + reset detection.
- **`DeriveCapabilities` / `Capabilities`** — already enumerates `gcode_move`, `toolhead`, fans; the
  capability-gate source for which tiles to show.
- **Icon pattern:** `res/drawable/*.xml` vector drawables via `painterResource` (e.g. `add.xml`, `remove.xml`,
  `arrow_upward.xml`, `nozzle.xml`, `bed_tilt.xml`). **No Material Symbols font dependency** — add per-icon
  vectors on demand (D-17).
- **`ScrubberPage` (PRIM-01)** — NOT used by Fine-Tune (value tiles replace it). Listed only so the planner
  knows it exists and deliberately isn't the model here.

### Established Patterns
- Capability-gated command specs via the `gcode(...)` factory + object-presence predicates (Phase 9).
- Cold-extrude / homed guards (Extrude) — reuse the guard idiom for PA/flow which tie to a hot extruder.
- Immediate dispatch + state-flip confirm, **no ConfirmGuard** for non-destructive tweaks.

### Integration Points
- **Entry point:** `PrintStatusControlAction.Tune` is a disabled placeholder
  (`PrintStatusScreen.kt`: `PrintStatusControlAction.Tune -> Unit`). This phase wires it to open Fine-Tune
  (D-21). Phase 16 is complete, so the shell/nav target is now stable.
- **New live-state to parse** (reducer additions): `extruder.pressure_advance` + `extruder.smooth_time`,
  `fan.speed`, `toolhead.max_accel`/`max_velocity`/`minimum_cruise_ratio`/`square_corner_velocity`,
  `firmware_retraction.*`, plus the relevant `configfile.settings.*` baselines for reset.
  (`speed_factor`/`extrude_factor` already exist.)

</code_context>

<specifics>
## Specific Ideas

- **User's organizing model (D-01):** split into **Motion** (things that move the head) vs **Extrusion**
  (things that affect the filament). Drove the layout grammar and the control-vs-belongs-elsewhere boundaries.
- **Tile grammar (D-13/D-14, staging):** compact value tiles, generic ±, one command per tap, no detail
  screen, no long-press repeat, busy-the-whole-group, long-press value = reset. This is the user's refined
  interaction model and **supersedes** the earlier ScrubberPage-per-control idea.
- **Outside input:** control set, ranges, and scope boundaries were cross-checked against a **Codex (gpt-5.x)**
  ideation pass that independently drew the same Fine-Tune ↔ Output-Controls ↔ Calibration boundaries
  (part-fan-here / generic-fans-elsewhere; save-Z-to-config is a Calibration action, never mid-print).

</specifics>

<deferred>
## Deferred Ideas

- **Z babystep — DONE in Phase 16** (shipped as a Print-Status first-layer conditional control). No longer a
  Fine-Tune row.
- **Mid-print object exclusion (`EXCLUDE_OBJECT`) → v2 wishlist.** Destructive; needs an object picker + confirm
  guard and depends on `[exclude_object]` + a labeling slicer. More "print action" than "tune."
- **Temp *setters* → Phase 5 Temp panel; generic fans/LEDs/output pins/enclosure → Phase 18; Pause/Resume →
  Phase 7 Print-Status gutter.** Surfaced as readouts/links here, not re-implemented.
- **User-customizable tuner increments → future Settings section** (D-18). Not P17.

### Reviewed Todos (not folded)
- **`2026-06-05-increment-picker-3cell-component.md` (C2 shared component build)** — reviewed, **not folded.**
  Owner chose "Fine-Tune only" (D-19): build the tile shared-ready but don't build the full two-mode C2
  component or retrofit Move this phase. Stays a deferred todo.
- **`2026-06-05-move-z-vertical-layout-rework.md` (C3 Move-Z vertical layout)** — reviewed, **not folded.**
  Orientation-aware layout surgery on the already-shipped Move screen; out of P17's Fine-Tune scope. Stays a
  deferred todo.

*(Other todos surfaced by the keyword matcher — bookmarked-macros density, dev-overlay drag, printers
edit-mode, settings densify, webcam crash, etc. — are unrelated to Fine-Tune and were not considered for
folding.)*

</deferred>

---

*Phase: 17-fine-tune-live-adjust-panel*
*Context gathered: 2026-06-05 · revised 2026-06-06 (staging fold-in)*
