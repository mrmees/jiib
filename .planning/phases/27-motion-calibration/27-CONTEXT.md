# Phase 27: Motion + Calibration - Context

**Gathered:** 2026-06-10
**Status:** Ready for planning

<domain>
## Phase Boundary

Migrate the spatial + calibration surfaces onto the jiib redesign. **MoveScreen** — the XY jog
pad **stays a GRID** (the LAW's spatial carve-out; spatial arrangement carries meaning) — gains a
vertical Z column and a matching vertical distance stepper. The **calibration cluster**
(`CalibrationHubScreen`, `ProbeCalibrateScreen`, `BedMeshScreen`, `ScrewsTiltScreen`, `TiltScreen`)
restyles onto the Phase-23 component classes, with the hub becoming a list+Focus screen and the
in-screen calibration sub-nav converting to real nav routes (the P24 D-01 conversion this phase
owns). Wizard/step-through flows keep their existing state-machine semantics, re-rendered on
FootButtonBar + intent colors. Per-screen conformance (≥64px, `fsSp` S/M/L, rotation, `@Preview`
matrices, tokenized strings, registry icons) folds into each migration — no separate sweep.

**This is a UX migration phase.** No new printer capability. Holders (`MoveHolder`,
`CalibrationHubHolder`, `ProbeCalibrateHolder`, `BedMeshHolder`, `ScrewsTiltHolder`, `TiltHolder`)
and command paths are NOT rebuilt — homed-gating, capability gating (`CalibrationGate`), the
manual-probe state machine, and profile-name validation must survive intact (SC-5).

**Out of scope:** System/Settings cluster (Phase 28); reconnect/process-death robustness and ship
items (Phase 29); new calibration routines or probe types; mid-print motion (Move/Calibration stay
pop-to-root foot-guns per P24 D-04).

</domain>

<decisions>
## Implementation Decisions

### Move screen anatomy (specialized-layout exemption, like Extrude P26 D-15)
- **D-01: Vertical Z column.** Z control becomes a vertical 3-cell column (Z-up / readout-home /
  Z-down reading the way the axis moves). This implements the long-deferred C3 LAW rule as part of
  the rebuild (the C3 todo file stays open until verified on-device — owner chose not to pre-fold
  it; close it at phase completion if the vertical Z ships).
- **D-02: Distance picker → vertical 3-cell stepper.** The row-of-preset-buttons
  `DistanceSelector` is replaced by a simple +/− stepper with a display readout — a vertical
  column of 3 equally spaced rows ( + / active-step readout / − ), visually matching the Z column.
  +/− cycles the existing FIXED jog-distance set (no free entry).
- **D-03: Orientation behavior (the load-bearing rule):** the square XY pad must NEVER crowd out
  the Z/distance columns when it fights to maintain its 1:1 ratio. Landscape: the pad fills its
  half (full height). Portrait: the pad is capped at **~60% of HEIGHT** — the square ratio rule
  then derives (caps) its width — leaving guaranteed room for the columns. Owner is open to the
  foot buttons sitting underneath the columns section; planner tunes exact ratios + foot placement
  against the 5U budget.
- **D-04: Pad internals carry over unchanged** — home-XY center, axis-corner live readouts +
  tap-to-home, red force-move toggle — restyled onto tokens/registry icons only. Foot actions
  (All / Disable / Back) and the Disable ConfirmGuard semantics carry over onto FootButtonBar.

### Calibration hub & nav shape
- **D-05: List hub with info Focus.** Field = the routine list as `ListRow`s; Focus = the selected
  routine's icon + title + a brief description of how the procedure works, plus an **accent Open
  button** that pushes the routine screen. Row tap selects (populates Focus); Open launches.
  First entry pre-selects the first supported routine so the Focus is never empty (P26 D-04
  pattern). The tile grid dies.
- **D-06: Greyed-but-listed unsupported routines KEPT** (the Phase-9 owner override survives the
  redesign): all five routines always render, unsupported dimmed and still selectable/openable.
  Owner: "eventually this will go away, but I need the visual reminder for now" — flipping to
  hide-not-grey is a deferred idea, NOT this phase.
- **D-07: Routines become real nav routes.** The in-screen calibration sub-nav back-stack
  (BackHandler-based) converts to NavHost routes per P24 D-01 — hub → routine pushes, Back pops to
  hub. Back-suppression rules (D-09) must hold at the nav layer too.

### Wizard flow grammar
- **D-08: Probe-Calibrate TESTZ controls adopt the Move grammar.** The TESTZ nudge surface becomes
  the same paired vertical columns as Move: a vertical Z-nudge column (▲ / live-Z readout / ▼
  firing TESTZ ±step) beside a vertical step column ( + / step readout / − ) walking the existing
  `TESTZ_STEPS` set. One spatial muscle-memory app-wide.
- **D-09: State-adaptive action semantics carry over VERBATIM onto FootButtonBar.** Idle-unhomed →
  Home All (accent) + Back; Idle-homed → Start (accent) + Back; Active → Accept (go) + Abort
  (danger) with **Back suppressed — including the nav back-stack/system back** while a manual-probe
  session is live (T-09-06-02 upgraded to the nav layer); Accepted → amber Save (ConfirmGuard
  SAVE_CONFIG restart gate) + Back. Same intents, same guards.
- **D-10: Tilt (Z-tilt/QGL variants) + Screws-Tilt = pure restyle.** Same flows and data. The
  Screws-Tilt bed-scale visualization keeps its spatial carve-out (a graphic, not a list); screw
  rows with turn instructions become `ListRow`s; run/result states render via FootButtonBar +
  intent colors.

### Bed Mesh surfaces
- **D-11: Profiles ARE the Field list.** Field = the saved-profile `ListRow` list (active profile
  marked). The full-screen Load selector dialog disappears entirely. Focus stays the Views
  `BedMeshHeatmapHost` heatmap (+ scale-mode toggle, empty-state copy preserved).
- **D-12: Tap selects, foot applies.** Row tap selects a profile (no immediate dispatch); an Apply
  foot button loads it (`BED_MESH_PROFILE LOAD`); Remove acts on the selection behind the existing
  red "cannot be undone" ConfirmGuard.
- **D-13: Save-name = Field-takeover with the system ALPHANUMERIC keyboard** — the one sanctioned
  alphanumeric carve-out in printer controls (pre-existing owner carve-out for this control).
  Pre-filled with the auto timestamp default (`YY.MM.DD_HH.MM`); `PrinterCommands.isValidProfileName`
  gating preserved (invalid name never reaches dispatch).
- **D-14: State-adaptive foot** (mirrors Probe's pattern): unhomed → Home All; homed + no selection
  → Calibrate + Save + Back; profile selected → Apply + Remove + Back. The amber SAVE_CONFIG
  restart gate after profile-save survives.

### Cross-cutting (apply to every screen)
- **D-15: Conformance + preview-first + tokenized-first fold into each screen** (≥64px or
  documented exception, `fsSp` S/M/L, rotation, `@Preview` 6-combo + fs=L matrix, `stringResource`,
  `DinghyIcons` registry only) — the P25/P26 pattern.
- **D-16: Icon law — never auto-pick.** New glyphs needed (hub routine icons exist; anything new
  like stepper +/− or remove glyphs not already registered): **STOP and ASK the owner.**
- **D-17: Move/Calibration remain P24 D-04 pop-to-root foot-guns** on print start; the rebuilt
  screens must not break that wiring. Post-Phase-26 UAT rules UAT-1..UAT-5 apply (FloatingEStop
  corner reservation, controls cap at 1U, icon sizing).

### Claude's Discretion
- Exact Move portrait/landscape ratios and foot-button placement (under-columns vs full-width) —
  60%-height is the starting point, not sacred; owner judges at UAT.
- Hub routine descriptions (brief how-it-works copy) — Claude authors; owner reviews at UAT.
- Profile-list "active" marking style, selection visuals, and how the Bed Mesh Field-takeover
  composes — consistent with Spoolman/P26 takeover precedents.
- NavHost route shapes for hub + 5 routines, holder hoisting, and how D-09's live-session
  back-block is enforced at the nav layer — grounded in the P24 spine patterns.
- Whether TiltScreen's two variants stay one parameterized screen (current shape) — no reason to
  split.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### jiib redesign LAW (read first)
- `.claude/skills/sketch-findings-dinghy-display/SKILL.md` — design-direction index incl.
  Post-Phase-26 UAT rules UAT-1..UAT-5 (auto-load before building any redesigned UI).
- `.claude/skills/sketch-findings-dinghy-display/references/foundations.md` — unit grid `U`,
  content/control fill, intent colors, icon registry, Focus/Field-no-gutter.
- `.claude/skills/sketch-findings-dinghy-display/references/adjustment-controls.md` — stepper
  anatomy + intent colors (the D-02/D-08 vertical steppers are this grammar turned vertical).
- `.claude/skills/sketch-findings-dinghy-display/references/lists-and-detail.md` — list+Focus
  selection grammar (hub D-05, mesh profiles D-11/D-12).
- `docs/ui_design/LAYOUT.md` — two-region grammar, unit `U`, §"Post-Phase-26 UAT formatting
  rules", spatial-grid carve-out, C3 vertical-quantity rule (D-01).
- `docs/ui_design/COMPONENTS.md` — Phase-23 kit (`ListRow`/`FootButtonBar`/`OutlinedControl`/
  `DetailCard`), Field-takeover pattern §5.
- `docs/ui_design/THEMING.md` + `docs/ui_design/CLAUDE.md` — tokens, intent-by-safety, icon
  never-auto-pick law, scrollable-Field-suppresses-swipe-drawer.
- `docs/ui_design/PREVIEW_AND_TOKENS.md` — `@Preview` matrix shape, `fsSp` (D-15).

### Prior phase contracts that bind this phase
- `.planning/phases/24-navigation-spine/24-CONTEXT.md` — D-01 (calibration sub-nav converts THIS
  phase), D-04 (Move/Calibration pop-to-root foot-guns), D-08 (hide-not-grey — deliberately
  OVERRIDDEN for the calibration hub by this phase's D-06).
- `.planning/phases/26-adjustment-screens/26-CONTEXT.md` — D-04 (Focus never empty), D-06
  (Field-takeovers replace pushes/dialogs), D-07 (system keyboard policy), D-15 (specialized-layout
  exemption precedent Move inherits), D-20 (shared increment-picker component — the Move/TESTZ
  vertical stepper should share or extend it, planner decides).
- `docs/adr/0001-ui-toolkit-decision.md` + Addendum-2 — Views surfaces stay Views
  (`BedMeshHeatmapHost`); no-frozen-frames budget on flox.

### Code being migrated (current implementations)
- `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` + `MoveHolder.kt` — jog pad
  (sacred-square 3×3, force-move, corner readouts), `ZRow` (dies → vertical column),
  `DistanceSelector` (dies → vertical stepper), Disable ConfirmGuard.
- `app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt` +
  `works/mees/dinghy/calibration/CalibrationHubHolder.kt` + `CalibrationGate.kt` — tile grid →
  list+Focus (D-05); ALL-FIVE-render owner override (D-06); supported-first ordering preserved.
- `app/src/main/java/works/mees/dinghy/ui/calibration/ProbeCalibrateScreen.kt` +
  `works/mees/dinghy/calibration/ProbeCalibrateHolder.kt` + `ManualProbeState.kt` — the state
  machine (Idle/Active/Accepted), `TESTZ_STEPS`, homed pre-flight, Back suppression (D-08/D-09).
- `app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt` + `BedMeshHolder.kt` +
  `BedMeshModel.kt` + `works/mees/dinghy/render/BedMeshHeatmapHost.kt`/`BedMeshHeatmapView.kt` —
  heatmap Focus, profile dialogs (die → Field list/takeover), `defaultProfileName()`,
  `PrinterCommands.isValidProfileName` (D-11..D-14).
- `app/src/main/java/works/mees/dinghy/ui/calibration/ScrewsTiltScreen.kt` + `TiltScreen.kt` +
  their holders/results — pure restyle (D-10).

### The component kit + reference implementations
- `app/src/main/java/works/mees/dinghy/designsystem/components/` + `designsystem/layout/` —
  Phase-23 kit; P26's shared increment-picker + adjuster components (reuse/extend for the
  vertical steppers).
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt` — list+Focus selection +
  Field-takeover pilot the hub and mesh-profile surfaces follow.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Phase-23 kit** (`ListRow`, `ListBlock`, `FootButtonBar`, `OutlinedControl`, `DetailCard`,
  `UnitGrid`) — carries the hub list, mesh profile list, screw rows, and every foot bar.
- **P26 increment-picker/stepper component** (D-20 of P26) — the natural base for the new
  vertical 3-cell steppers (Move distance, TESTZ step); planner decides share-vs-extend.
- **`CalibrationHubHolder`** already provides the supported-first `RoutineEntry` list reactively —
  the new list Field consumes it as-is.
- **`ProbeCalibrateHolder` state machine** — Idle/Active/Accepted with `markAborted` — drives the
  state-adaptive foot verbatim (D-09).
- **`BedMeshHolder`** — heatmap model + scale mode + `profileNames` — the profile Field list reads
  the same flow the Load dialog read.

### Established Patterns
- **State-adaptive gutter → state-adaptive FootButtonBar** — Probe/BedMesh already branch buttons
  on vm state; this phase re-renders the same branches in the new component.
- **ConfirmGuard for destructive/restart actions** (Disable steppers, Remove profile, SAVE_CONFIG
  restart) — all survive unchanged.
- **Views-in-Compose hosts** (`BedMeshHeatmapHost`) with Phase-22 `applyTokens` equality guards —
  don't regress the guards when re-parenting into the new layout.
- **Scrollable Fields suppress the swipe-up drawer** — applies to the hub list, profile list,
  screw list. (The old hub tile grid did NOT suppress; the new list MUST.)
- **Dispatch hygiene** — every action funnels through `CommandRegistry` specs with in-flight
  dispatchKey gating; no raw RPC from screens.

### Integration Points
- **Nav spine (P24)**: `Calibration` is an idle-list entry; hub + routines become routes on the
  NavHost; pop-to-root-on-print-start wiring (D-04) must keep working for Move + the whole
  calibration subtree; live-session back-block (D-09) integrates with the nav BackHandler chain.
- **`Capabilities.hasObject`** gating drives supported/greyed routine rendering (D-06) and the
  Probe start-command selection (PROBE_CALIBRATE vs Z_ENDSTOP_CALIBRATE).
- **FloatingEStop overlay** (UAT-4) — Focus top-left corner stays clear on every rebuilt screen;
  Move/Calibration are idle-only screens but the reservation rule still applies.

</code_context>

<specifics>
## Specific Ideas

- **The 3-equal-rows column motif (verbatim owner steer):** "increment picker needs to change to a
  simple plus/minus button with a display readout, giving us 3 equally spaced rows, just like the
  vertical z column."
- **The portrait crowding rule (verbatim owner steer):** "XY pad capped at 60% HEIGHT. Ratio rule
  should cap the width appropriately in portrait." Goal restated: "leave enough room for the z
  controls when the x/y controls are going to fight to maintain ratio."
- **Hub Focus content (verbatim owner steer):** "Screen should come up with the list of items in
  the field, the focus should include the icon / title for the procedure, and a brief description
  of how it works."
- **Greyed routines:** "eventually this will go away, but I need the visual reminder for now."

</specifics>

<deferred>
## Deferred Ideas

- **Calibration hub flips to hide-not-grey** once the owner no longer needs the greyed-routine
  visual reminder (D-06 is explicitly temporary) — future phase / quick task.

### Reviewed Todos (not folded)
- `2026-06-05-move-z-vertical-layout-rework.md` (C3) — owner chose to leave the todo PENDING as a
  tracker, but the discussion then locked vertical Z anyway (D-01). Close the todo at phase
  completion once the vertical Z column ships and is owner-verified.
- `2026-06-10-temperature-adjuster-fire-without-wait.md` — Temperature screen (P26 surface),
  targeted at Phase 28; not motion/calibration scope.
- `2026-06-10-webcam-aspect-ratio-overlay-back.md` — Webcam (P25 surface); not this phase.
- `2026-06-10-fw-retraction-glyph-assignment.md` — Fine-Tune rows (P26 follow-up); not this phase.
- `2026-06-05-settings-densify-one-page-restyle.md` (C6) +
  `2026-06-05-printers-edit-delete-mode-buttons.md` (R4) +
  `2026-06-05-phase-15.1-review-deferred-findings.md` — System/Settings cluster → Phase 28.
- `2026-06-08-phase-22-arm64-abi-ship-requirement.md` — ship requirement → Phase 29.

</deferred>

---

*Phase: 27-motion-calibration*
*Context gathered: 2026-06-10*
