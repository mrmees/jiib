# Phase 19: Output Controls — Fans, Lights & Generic Pins - Context

**Gathered:** 2026-06-07
**Status:** Ready for planning

<domain>
## Phase Boundary

A dedicated **Outputs** screen: one flat, alphabetically-sorted list of the user-controllable,
non-standard printer outputs the active printer exposes, each opening a type-appropriate
immediate-dispatch detail page. Capability-gated by the Phase-6 matrix; current values come from
the central subscribe; commands go through the shared command primitive. **Available while printing —
no print-state gating.**

**Whitelist (only these Klipper families):** `heater_generic`, `fan_generic`, `led`, `neopixel`,
`dotstar`, `pca9533`, `pca9632`, `servo`, `output_pin`, `pwm_tool`.

**Explicitly out of scope:** all standard/automatic printer behavior (extruder/heater_bed/standard
part-cooling `fan`/heater_fan/controller_fan/temperature_fan, temperature & probe sensors, motion
steppers, TMC/digipot/board-expander/probe sections, `respond`, display, button/input sections);
`manual_stepper` and `pwm_cycle_time` (deferred — users expose safe custom macros if needed); search/
filter; hide/favorite/reorder customization; an "all off" action; per-index addressable LED control.

**The complete design is in the staging doc — read it (see Canonical References). It locks ~90% of
this phase.** This CONTEXT records only the decisions the staging doc left open for the owner.

</domain>

<decisions>
## Implementation Decisions

> The staging doc (`19-output-controls-staging.md`, see refs) is authoritative for everything not
> restated here. The decisions below are the gaps it left for the owner, settled in this discussion.

### Output-type icons (OWNER-SELECTED — do not substitute)
Per the project icon law ([[dinghy-never-pick-icons-ask]]), every glyph below was chosen by the owner.
The planner MUST wire exactly these and MUST NOT invent or swap any. All are Material Symbols Outlined
ligatures (axes FILL0/wght400/GRAD0), consistent with the existing `DinghyIcons` registry.

- **D-01:** `heater_generic` → **`mode_heat`**
- **D-02:** `fan_generic` → **`mode_fan_2`**
- **D-03:** `led` / `neopixel` / `dotstar` / `pca9533` / `pca9632` → **`lightbulb_2`**
- **D-04:** `servo` → **`cyclone`**
- **D-05:** `output_pin` (BOTH digital and PWM variants) → **`check_box`**
- **D-06:** `pwm_tool` → **`vital_signs`**
- **D-07:** Outputs section / App Drawer tile → **`output`**
- **D-08 (CROSS-PHASE side-change — REQUIRED):** Reassign the **part-cooling fan** icon (Fine-Tune,
  Phase 17) from `mode_fan` → **`air`**, to free the fan-glyph space for the new generic-fan icon.
  `mode_fan` is currently bound at `DinghyIcons.FanMode` (`DinghyIcons.kt:80`), consumed by the
  Fine-Tune part-fan control. The planner must update that binding AND verify no other call site
  regresses. This is owner-directed, not scope creep.
- **D-09 (planner verification gate):** Before wiring, verify each glyph above (`mode_fan_2`,
  `lightbulb_2`, `cyclone`, `check_box`, `vital_signs`, `output`, `air`) actually resolves in the
  bundled Material Symbols ttf (v2.944) via the Phase-18.1 `verify_ligatures.py` tooling. If any
  ligature is absent, STOP and ask the owner — do not silently fall back to a different glyph.

### Empty / no-outputs state
- **D-10:** When the active printer exposes **zero** whitelisted outputs, **hide the Outputs App
  Drawer tile entirely** — capability-gate it away, same as Phase-6 gating hides absent features.
  No empty-state screen, no dead-end tile.

### Navigation entry point
- **D-11:** The Outputs screen is reached via an **App Drawer tile** (swipe-up drawer), consistent
  with Calibration / Macros / Console. Not a home/Status tile. Tile uses icon `output` (D-07) and is
  capability-gated per D-10.

### LED control
- **D-12:** The LED detail page gets an **explicit Off action** (set color black / brightness 0),
  alongside the RGB color picker + brightness scrubber — matching the Off action every other output
  type has. Resolves the staging doc's silence on LED Off. (Whole-strip control only; no per-index.)

### Claude's Discretion
- Exact control-page composition (scrubber layout, where Off sits) within the established
  `ScrubberPage` / single-setting patterns — choose the shape that fits each hardware type per the
  staging doc's per-type spec.
- All command-syntax / value-range / discovery-source resolution is **research/planner work**, not an
  owner decision (see staging doc "Open Planner Work" and Research Notes below).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase design (authoritative — read FIRST)
- `.planning/phases/19-output-controls-fans-lights-generic-pins/19-output-controls-staging.md` —
  the full Phase-19 design (whitelist, exclusions, flat-list grammar, name-prettifying, per-type
  control shapes, immediate-dispatch / no-ConfirmGuard / no-Apply rules, drawer suppression,
  Back-only gutter, busy-state scoping, Open Planner Work). **NOTE:** the source file in the parallel
  scratch dir is mis-titled "Phase 18 Output Controls" — it is THIS phase (19). A copy is committed
  into the phase dir under the corrected name; treat it as canonical.
- `.planning/ROADMAP.md` §"Phase 19" — goal + 4 success criteria + STANDARD research note
  (SET_FAN_SPEED / SET_PIN / SET_LED verified against Klipper config reference).

### UI law
- `docs/ui_design/LAYOUT.md` — Focus/Field/Gutter grammar; the Outputs Field is the scrollable list,
  Gutter holds Back only, global swipe-up drawer is suppressed on this screen.
- `docs/ui_design/THEMING.md` — semantic tokens; LED swatch/color picker must route through tokens
  (with the THEME-01-style carve-out for the live filament/LED color itself).
- `docs/ui_design/CLAUDE.md` — icon law (never invent icons) reinforcing D-01..D-09.
- `docs/ui_design/PREVIEW_AND_TOKENS.md` — preview-first / tokenized-first build convention; the new
  Outputs list + each detail page ship a `@Preview` matrix and `stringResource` strings from day one.

### Reusable code (see Code Context)
- `app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt`
- `app/src/main/java/works/mees/dinghy/designsystem/ColorWheel.kt`
- `app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt`,
  `CommandDispatcher.kt`, `CommandRegistry.kt`, `CommandSpec.kt`
- `app/src/main/java/works/mees/dinghy/calibration/CalibrationGate.kt` (+ `*Gate.kt`) — capability
  gating pattern for D-10.
- `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` — icon registry; `FanMode`
  binding at line 80 is the D-08 reassignment target.
- `img/material-icon-bucket.json` — owner-curated glyph map (does NOT yet contain the D-01..D-08
  output glyphs — owner may add them; planner verifies via D-09).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`ScrubberPage.kt`** — the single-setting scrubber/stepper. Directly reusable for: fan % (0–100),
  servo angle, output_pin PWM %, pwm_tool duty %, heater_generic setpoint, LED brightness.
- **`ColorWheel.kt`** — existing RGB picker; reuse for the LED color picker (D-12). `SpoolPicker.kt`
  / `PromptStyleColors.kt` are related color-UI precedents.
- **`PrinterCommands.kt` + `CommandDispatcher.kt`** — the shared command primitive; all output
  commands (SET_FAN_SPEED / SET_HEATER_TEMPERATURE / SET_LED / SET_SERVO / SET_PIN / pwm_tool path)
  dispatch through it. Single-source clamp authority lives here (Phase-17 lesson) — output ranges
  should clamp here too.
- **Capability-gate pattern** (`calibration/*Gate.kt`) — the model for D-10 (hide tile when no
  whitelisted outputs present).

### Established Patterns
- **Drawer suppression + Back-only gutter** — precedents: `CalibrationHubScreen.kt`,
  `ConsoleScreen.kt`, `SystemMacrosScreen.kt`. `CalibrationHubScreen` is the closest analog to the
  flat Outputs list (a Hub of tappable rows → detail pages).
- **Immediate dispatch + toast feedback, busy-state scoped to the detail page** — matches the
  staging doc; no Apply flow, no ConfirmGuard even for risky pins/PWM.
- **`DinghyIcons` ligature registry** — `IconRef.Ligature("name")`; new glyphs added here, font is
  v2.944 (carries needed glyphs per Phase-18.1), verified by `verify_ligatures.py`.

### Integration Points
- Central subscribe (`objects/subscribe`) supplies live values; rows hide only the value/status when
  a field is absent, item stays tappable (staging rule).
- App Drawer tile registration (alongside Calibration/Macros/Console tiles) for D-11.
- `DinghyIcons.FanMode` (D-08) — the one place the part-fan glyph is bound; reassignment point.

</code_context>

<specifics>
## Specific Ideas

- Owner picked every output icon explicitly (D-01..D-08) and directed the cross-phase part-fan glyph
  reassignment to `air`. These are hard inputs, not suggestions.
- Discovery source is intentionally unresolved (staging "Discovery" section): planner/researcher must
  compare `printer.objects.list` vs `configfile.config` vs `configfile.settings` on the REAL printers
  (E5P 192.168.1.120:7125, E3P 192.168.1.121:7125) — working assumption: `configfile` identifies
  configured whitelisted sections, `objects.list`/live status confirms runtime availability.
- output_pin detail page branches on `pwm: true` (digital toggle page vs PWM % scrubber page) — both
  share the `check_box` row icon (D-05).

</specifics>

<deferred>
## Deferred Ideas

- **Search / filter** on the Outputs list — explicitly out of Phase 19 (staging). Future phase if the
  output count ever warrants it.
- **Hide / favorite / reorder** output customization — out of Phase 19.
- **Per-index addressable LED control** — out; whole-strip only in 19.
- **"All off" action** — out of Phase 19.
- **`manual_stepper` and `pwm_cycle_time` controls** — deferred; safe custom macros cover the gap.
- **Curating the output glyphs into `material-icon-bucket.json`** — owner may do this so the bucket
  stays the canonical glyph record; not blocking (planner can register ligatures directly).

</deferred>

---

*Phase: 19-output-controls-fans-lights-generic-pins*
*Context gathered: 2026-06-07*
