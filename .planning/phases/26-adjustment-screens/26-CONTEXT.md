# Phase 26: Adjustment Screens - Context

**Gathered:** 2026-06-10
**Status:** Ready for planning

<domain>
## Phase Boundary

Migrate the numeric-adjustment screens onto the locked **sketch-003/004 adjustment archetype** —
the 3-zone adjuster (header + Reset / centered value with inline "was X" baseline / stepper +
increment picker), the canonical sketch-004 scrubber, all keyboard-free in the hot path. Screens:
**Temperature**, **Extrude** (specialized-layout exemption — conformance only), **Outputs** (+ its
fan/LED/pin/servo details), **Fine-Tune** (hub + Extrusion/Motion/FW-retraction groups collapsing
to ONE screen), and the **single-setting pages** (`ScrubberPage`/`NumpadPage`/`MeasuredWeightPage`,
which retire as full-screen pushes). Per-screen conformance (≥64px, `fsSp` S/M/L, rotation,
`@Preview` matrices, tokenized strings, registry icons) folds into each migration — no separate
sweep.

**This is a UX migration phase.** No new printer capability. Holders (`TemperatureHolder`,
`ExtrudeHolder`, `OutputsHolder`, `FineTuneHolder`) and command paths are NOT rebuilt — capability
gating and the Phase-17 clamp authority must survive intact (SC-5). The one feature-flavored
addition the owner explicitly pulled in: per-trace **show/hide + pool-color selection** on the
Temperature graph (see D-13/D-14).

**Out of scope:** Move/jog + calibration cluster (Phase 27); System/Settings cluster (Phase 28);
adding new temperature sensors to the Temperature page (deferred — but the row model must be ready
for them, D-12); deeper LED aesthetic redesign (deferred polish, D-23); reconnect/process-death
robustness (Phase 29).

</domain>

<decisions>
## Implementation Decisions

### Fine-Tune structure
- **D-01: One screen, flat list.** `FineTuneHubScreen` + the three group screens
  (`ExtrusionScreen`, `MotionScreen`, `FwRetractionScreen`) are DELETED and replaced by a single
  sketch-003 screen: Field = one scrollable param list (~12–14 rows), Focus = the 3-zone adjuster.
  Capability-gated rows (FW-retraction absent on printers without it) simply hide and the list
  compacts (the P24 D-08 hide-not-grey rule).
- **D-02: Order = current groups, flattened** — Extrusion (speed, flow, PA, smooth) → Motion
  limits (velocity, accel, min-cruise, SCV) → FW-retraction (length, speed, unretract extra,
  unretract speed). **Group membership is denoted by POOL COLORS on the leading param icons**
  (each group's icons share a data-pool hue) — no label words, per the no-label-grouping law.
- **D-03: Fan stays in BOTH Fine-Tune and Outputs.** The print-fan row remains a Fine-Tune param
  (routine mid-print tweak) AND fans remain controllable in Outputs. Same command path;
  intentional duplication.
- **D-04: Adjuster remembers last-adjusted param** (session memory); falls back to the first
  param on fresh entry. Focus is never empty.

### Control assignment (stepper / scrubber / keyboard)
- **D-05: Stepper is the default everywhere.** The sketch-004 scrubber is reserved for
  naturally-bounded 0–100% values ONLY (fan speed, PWM, LED brightness). Temperature, Fine-Tune
  params, extrude amounts = stepper + increment picker.
- **D-06: Everything in-place — full-screen single-setting pushes RETIRE.** The adjuster lives in
  the Focus; remaining single-setting callers (measured weight, macro params, LED channels)
  render as Field-takeovers. `ScrubberPage` and `NumpadPage` as whole-screen destinations are
  deleted; their internals survive only as embeddable components where needed.
- **D-07: Custom NumpadPage is DROPPED app-wide → Android SYSTEM keyboard in numeric mode.**
  Wherever exact numeric entry is appropriate, open the system numeric IME (inputType
  number/decimal), clamped to existing safe ranges. This **supersedes Phase 25 D-12's "NumpadPage
  for numeric"** for macro params — those get the numeric IME too. (Consistent with the owner's
  standing 2026-06-02 "numeric keyboard for numeric fields" rule; this is a deliberate amendment
  to the "no keyboard in printer controls" law for NUMERIC entry only — alphanumeric remains
  barred from printer controls.)
- **D-08: Measured weight converts NOW** — a Field-takeover inside SpoolScreen's flow using the
  numeric system keyboard. The Phase-23 pilot edit is confined to the takeover wiring.
- **D-09: Scrubber dispatches ON RELEASE.** Fill/thumb/value update in place during drag (the
  locked build-once rule — never rebuild mid-drag, the Phase-19 `fa97efb` regression); one
  command fires on finger-lift.

### Temperature anatomy
- **D-10: Focus MORPHS graph ↔ adjuster.** The Views `GraphViewHost` is the default Focus;
  tapping a sensor row swaps the Focus to that item's adjuster; done/back returns to the graph.
  Field = sensor rows + FootButtonBar throughout. Both surfaces get full Focus size.
- **D-11: Typed row model with read-only support.** Rows are typed (adjustable heater vs
  read-only sensor) so future non-heater temperature readings (`temperature_sensor` — MCU, Pi,
  chamber sensors) can join WITHOUT rework. v1 ships the heater set (nozzle/bed/chamber-class as
  today); extra sensors deferred.
- **D-12: Presets = foot button → Field-takeover.** A Presets foot button swaps the Field to the
  preset list in place. The full-screen `PresetSelector` scrim retires on this screen
  (PrintStatus's Preheat entry keeps working per P24 — planner decides whether it keeps the scrim
  or shares the new surface).
- **D-13: Off = both levels.** Amber Cooldown foot button keeps `TURN_OFF_HEATERS` (all heaters,
  one tap) AND the heater adjuster carries an explicit per-heater Off action (target = 0; the P19
  GAP-A Off-overlay precedent).
- **D-14: Every selectable row's MINIMUM adjuster = graph controls.** When any row is selected
  (heaters now, read-only sensors later), the adjuster surface includes: **show/hide trace
  toggle** + **an inline row of the 8 pool colors to pick the trace draw color** — always pulled
  from the **COLORFUL theme pool on this page**, regardless of the active palette mode. 8 swatches
  fit inline; NO dedicated color-picker page. Heaters get this PLUS the target-temp stepper.
  Trace color/visibility choices persist (route writes through `AppContainer.writeScope`, never a
  composition scope). The leading row icon is tinted to the CHOSEN trace color — the
  same-hue-everywhere invariant (graph trace = row icon = readout) survives, now user-colorable.

### Extrude + Outputs anatomy
- **D-15: Extrude is a SPECIALIZED-LAYOUT EXEMPTION** (like Move's jog grid in Phase 27): the
  function requires its command-centric layout; it is NOT migrated to list+adjuster. It still
  gets full conformance within its own shape: tokens, FootButtonBar replacing the gutter
  (Load/Unload/Back intents preserved), ≥64px, `fsSp`, rotation, previews. Cold-extrude icon
  gating (EXTR-04) unchanged.
- **D-16: Extrude distance + speed → numeric system keyboard** (replacing their NumpadPage
  setpoints). Presets remain the fast path; existing clamp ranges apply.
- **D-17: Extrude's nozzle-temp button → a filament-preset list page**: a Field-list of the
  standard material presets **PLUS the currently-loaded spool's filament** (when Spoolman
  provides one). Selecting a preset applies the **EXTRUDER temperature ONLY** (never bed) when
  fired from the Extrude screen; selection closes the page; a Back button sits at the bottom.
- **D-18: Outputs collapses detail pages into the Focus** (Spoolman shape): Field = output list
  (today's capability-discovered rows), Focus = the selected output's control surface in place —
  fan/PWM = the 0–100% scrubber, output_pin = toggle, servo = its control. `OutputScrubberDetail`
  / `OutputPinDetail` / `OutputLedDetail` page pushes are gone.
- **D-19: LED fits in Focus with MINIMAL rework** — brightness scrubber + capability-gated
  channel controls (P19 GAP-B gating preserved) + the existing `ColorWheel`, composed into the
  Focus. Deeper LED aesthetic polish stays deferred.

### Cross-cutting (apply to every screen)
- **D-20: Increment picker = the sketch-003 spec, built as a shared design-system component**
  (folds/supersedes the C2 3-cell todo). Per-value step sets are planner's discretion seeded from
  the existing `FineTuneShared` step constants; active increment = accent.
- **D-21: Baseline "was X"** per sketch 003 — captured on entry, inline (never stacked), shown
  only when changed, cleared at baseline. Reset = caution/amber, top-right; Reset-all in the foot
  where the sketch names it.
- **D-22: P17 clamp authority + busy-lock discipline are LOAD-BEARING.** All markPending targets
  route through `PrinterCommands` clamps + wire-precision rounding (`roundToWirePrecision`); the
  per-tuner epsilon + seq-guarded timeout backstop survive the rebuild. Regression tests must
  stay green.
- **D-23: Conformance + preview-first + tokenized-first fold into each screen** (≥64px or
  documented exception, `fsSp` S/M/L, rotation, `@Preview` 6-combo + fs=L matrix,
  `stringResource`, `DinghyIcons` registry only).
- **D-24: Icon law — never auto-pick.** New glyphs needed by the adjuster/param rows that aren't
  already registered (in `DinghyIcons`, the sketches, or `img/`): **STOP and ASK the owner.**

### Claude's Discretion
- Per-value increment step sets (D-20) and exact stepper clamp ranges — seed from
  `FineTuneShared` constants and `PrinterCommands` clamps; owner judges at UAT.
- How "remember last-adjusted" persists (session-only state vs DataStore) — session memory is the
  requirement; durability beyond process death is optional.
- Whether PrintStatus's Preheat keeps the old scrim or adopts the new preset Field-takeover
  surface (D-12) — don't regress the P16-06 fallback either way.
- The Focus graph↔adjuster morph mechanics (cheap one-shot cross-fade per the P24 D-13 precedent,
  or hard swap) — must hold the flox frame budget.
- Trace-color persistence shape (per-printer-profile vs global) — pick what matches the existing
  theme/profile persistence patterns.
- Exact NavHost/route + in-screen state shape for the collapsed Fine-Tune and Outputs screens
  (their P24 local sub-nav back-stacks dissolve with the collapse — preserve sane Back behavior).

### Folded Todos
- `2026-06-05-increment-picker-3cell-component.md` (C2) → **D-20**: the sketch-003 increment
  picker is built as the shared design-system component; the older C2 two-mode design is
  superseded. Close the todo at phase completion.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### The adjustment archetype (the law for this phase — read FIRST)
- `.claude/skills/sketch-findings-dinghy-display/references/adjustment-controls.md` — **THE
  primary spec**: 3-zone adjuster anatomy, inline "was X" baseline, increment picker, intent
  colors, 5U phone-landscape fit budget, the sketch-004 scrubber spec + the build-once drag rule,
  what-to-avoid list.
- `.claude/skills/sketch-findings-dinghy-display/sources/003-finetune-adjust/index.html` — the
  winning Fine-Tune sketch source (visual ground truth for the one-screen collapse, D-01).
- `.claude/skills/sketch-findings-dinghy-display/sources/004-scrubber-style/` — the canonical
  scrubber source (thin track, ringed thumb, ~74px invisible touch target).
- `.claude/skills/sketch-findings-dinghy-display/references/foundations.md` — unit grid `U`,
  content/control fill convention, intent colors, icon registry, Focus/Field-no-gutter.
- `.claude/skills/sketch-findings-dinghy-display/SKILL.md` — redesign direction index (auto-load).

### UI law
- `docs/ui_design/COMPONENTS.md` — component catalog; **§7 names this phase as where the
  stepper/scrubber restyle lands — the restyle specs must be ADDED to §3 when this phase
  executes**; Field-takeover pattern §5; fill convention §2; unit `U` §4.
- `docs/ui_design/LAYOUT.md` — two-region Focus/Field grammar, unit `U` formula, NON-NEGOTIABLES.
- `docs/ui_design/THEMING.md` — semantic tokens, button-intent-by-safety, `--fs`, THEME-01
  carve-outs (LED swatch color is data; trace pool colors are theme-pool-sourced, see D-14).
- `docs/ui_design/CLAUDE.md` — non-negotiables; icon never-auto-pick law (D-24);
  scrollable-Field-suppresses-swipe-drawer; "fill the usable space."
- `docs/ui_design/PREVIEW_AND_TOKENS.md` — `@Preview` matrix shape, `fsSp` usage (D-23).

### Prior phase contracts that bind this phase
- `.planning/phases/25-browse-screens/25-CONTEXT.md` — D-12 (macro param Field-takeover — its
  "NumpadPage for numeric" wording is superseded by this phase's D-07 numeric-IME decision),
  Field-takeover precedents, conformance-folds-in pattern.
- `.planning/phases/24-navigation-spine/24-CONTEXT.md` — D-04 (pop-to-root foot-guns: Extrude IS
  one; Temperature/Fine-Tune stay valid mid-print), D-07 (Fine-Tune is printing-list; Temperature
  off the idle list), D-08 (hide-not-grey capability gating).
- `docs/adr/0001-ui-toolkit-decision.md` + Addendum-2 — the live temp graph STAYS Views
  (`GraphViewHost`); no-frozen-frames / p90 floor budget for the Focus morph (D-10) and scrubber.

### The reference implementations
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt` — the pilot (Field-takeover,
  DetailCard Focus, FootButtonBar); D-08 touches its measured-weight flow ONLY.
- `app/src/main/java/works/mees/dinghy/designsystem/components/` + `designsystem/layout/` — the
  Phase-23 kit (`ListRow`, `ListBlock`, `FootButtonBar`, `OutlinedControl`, `UnitGrid`).

### Screens being migrated (current implementations)
- `app/src/main/java/works/mees/dinghy/ui/finetune/` — `FineTuneHubScreen.kt`,
  `ExtrusionScreen.kt`, `MotionScreen.kt`, `FwRetractionScreen.kt` (all replaced by ONE screen),
  `FineTuneShared.kt` (step constants + clamp plumbing to PRESERVE), `FineTuneHolder.kt`,
  `FineTuneVm.kt`.
- `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt` +
  `TemperatureHolder.kt` (trace resolution nozzle→bed→chamber; ring buffers; the same-hue
  invariant at TemperatureScreen.kt:461) + `works/mees/dinghy/render/` (`GraphViewHost`, the
  Views graph w/ Phase-22 D-12 `applyTokens` equality guards — extend for per-trace color/
  visibility, don't regress the guards).
- `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt` + `ExtrudeHolder.kt`
  (specialized-layout exemption, D-15; PresetSelector reuse at TemperatureScreen.kt:405-439).
- `app/src/main/java/works/mees/dinghy/ui/outputs/` — `OutputsScreen.kt`,
  `OutputScrubberDetail.kt`, `OutputLedDetail.kt`, `OutputPinDetail.kt`,
  `OutputToggleControl.kt` (details collapse into Focus, D-18/D-19).
- `app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt` + `NumpadPage.kt` (retire
  as screens, D-06/D-07) + `ColorWheel.kt` (reused in LED Focus) +
  `app/src/main/java/works/mees/dinghy/ui/spool/MeasuredWeightPage.kt` (converts, D-08).
- P17 clamp authority: `app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt`
  (clampVelocity/clampAccel/clampScv/clampMinCruiseRatio, `roundToWirePrecision`) — D-22.

### Theme pool (for D-02 group colors + D-14 trace swatches)
- `../theme_theory/COLOR-SYSTEM.md` + the in-repo port (`works/mees/dinghy/theme/` —
  `ThemeResolver`/`TokenBridge`, palette modes) — the COLORFUL pool is the fixed source for the
  Temperature swatch row regardless of active palette mode.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Phase-23 kit** — `ListRow`/`ListBlock`/`FootButtonBar`/`OutlinedControl`/`UnitGrid` carry the
  Field side of every rebuilt screen; `DetailCard` may frame the adjuster.
- **`FineTuneShared.kt`** — per-control step constants (D-03..D-12 lineage), `fmtValue`, the
  clamp-routed `nudge` pattern with `markPending` — the semantic core the new adjuster wraps.
- **`TemperatureHolder`** — trace resolution + ring buffers + dashed setpoint lines; extend for
  per-trace visibility/color rather than rebuild.
- **`PresetSelector`** (TemperatureScreen.kt:405) — the preset data + dispatch logic feeds both
  the new Temperature Field-takeover (D-12) and Extrude's filament-preset page (D-17).
- **`ColorWheel`** — reused inside the LED Focus surface (D-19).
- **P19 scrubber lessons** — `OutputScrubberDetail`'s working drag internals + the GAP-A Off
  overlay + GAP-B channel gating all carry forward into the new surfaces.

### Established Patterns
- **Clamp authority (P17)** — markPending targets MUST equal the wire-clamped value
  (`clampVelocityLimitTarget` pattern); per-tuner epsilon + timeout backstop; regression tests
  exist and must stay green (D-22).
- **Build-once scrubber (P19 `fa97efb`)** — internal `working` MutableState + left-anchored
  fill; never a stale `pointerInput` closure (SC-3 of the phase).
- **DataStore writes ride `AppContainer.writeScope`** — trace-color/visibility persistence (D-14)
  and any remembered state must NOT use a composition scope.
- **Views-in-Compose graph** — `GraphViewHost` with Phase-22 `applyTokens` equality guards; the
  Focus morph must not thrash the AndroidView.
- **Scrollable Fields suppress the swipe-up drawer** — applies to every rebuilt list Field here.

### Integration Points
- Nav entry points exist from Phase 24 (idle list: Extrude/Outputs; printing list:
  Temp/Fine-Tune; drawer) — rebuilds slot in behind existing routes. The Fine-Tune and Outputs
  local sub-nav back-stacks dissolve with the collapses; Back behavior re-anchors to the NavHost.
- `PrinterState` capability flows gate FW-retraction rows, output rows, channel controls —
  hide-not-grey (P24 D-08).
- Spoolman's active-spool detail flow (P18.3 `SpoolHolder.activeSpoolDetail`) feeds D-17's
  loaded-filament preset row.

</code_context>

<specifics>
## Specific Ideas

- **Pool colors as group identity** (verbatim owner steer): Fine-Tune's flattened list uses
  data-pool hues on the leading icons to mark the old group boundaries — color does the grouping,
  not labels.
- **Temperature graph controls** (verbatim owner steer): "when selecting an item that's in the
  list its minimum settings should be show/hide and then a row of the color pool (which always
  pools from the 'colorful' theme pool on this page) that allows the user to select a color to
  draw the graph in. If we keep it to the 8 pool colors we should be able to fit that on the
  screen without having to have a dedicated color picker."
- **Keyboard pivot** (verbatim owner steer): "we're going to drop our custom numpad, just use the
  android system keyboard but make sure we pull up the numpad version of it whenever appropriate."
- **Extrude temp preset page**: standard filament presets + the currently-loaded spool's
  filament; applies extruder temp ONLY from this screen; closes on selection; Back at bottom.
- **Extrude is like Move**: "the function requires a specialized layout" — don't force the
  archetype onto it.

</specifics>

<deferred>
## Deferred Ideas

- **Additional non-heater temperature readings** (`temperature_sensor`: MCU, Pi, chamber sensors
  beyond the heater set) join the Temperature page — the typed row model + per-trace graph
  controls are designed for it NOW (D-11/D-14); actually surfacing the extra sensors is a future
  phase.
- **Deeper LED aesthetic redesign** — D-19 composes the existing pieces into the Focus; the real
  LED-surface polish stays on the future-polish list (owner-noted at P19).

### Reviewed Todos (not folded)
- `2026-06-05-move-z-vertical-layout-rework.md` (C3) — Move screen → Phase 27.
- `2026-06-05-settings-densify-one-page-restyle.md` (C6) +
  `2026-06-05-printers-edit-delete-mode-buttons.md` (R4) +
  `2026-06-05-phase-14-review-deferred-wr02-wr03.md` +
  `2026-06-05-phase-15.1-review-deferred-findings.md` (theme editor WR-01/WR-02) — System/
  Settings cluster → Phase 28.
- `2026-06-08-phase-22-arm64-abi-ship-requirement.md`, `console-macro-page-ux-flow.md`,
  `2026-06-09-phase-23-mfg-multiselect-deferred.md` — ship/polish → Phase 29.
- `2026-06-05-dev-overlay-panel-drag.md`, `2026-06-04-phase-11-spool-feature-robustness…`,
  `status-progress-ring-dual-source-jump.md`, benchmark/macrobenchmark items — unrelated to the
  adjustment screens; remain pending.

</deferred>

---

*Phase: 26-adjustment-screens*
*Context gathered: 2026-06-10*
