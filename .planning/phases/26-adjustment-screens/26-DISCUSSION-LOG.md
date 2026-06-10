# Phase 26: Adjustment Screens - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-10
**Phase:** 26-adjustment-screens
**Areas discussed:** Todo fold, Fine-Tune structure, Stepper vs scrubber vs numpad, Temperature anatomy, Extrude + Outputs anatomy

---

## Todo fold

| Option | Description | Selected |
|--------|-------------|----------|
| Fold it in | Phase 26 builds the increment picker per sketch-003 as a shared component; C2 todo superseded/closed | ✓ |
| Keep separate | Build only what the adjuster minimally needs; decide the reusable component later | |

**User's choice:** Fold it in.

---

## Fine-Tune structure

| Option | Description | Selected |
|--------|-------------|----------|
| One screen, flat list | Hub + group screens deleted; Field = one scrollable param list, Focus = adjuster | ✓ |
| One screen, visually grouped | Same collapse with icon/spacing group structure | |
| Keep hub → groups | Two-level structure restyled | |

**User's choice:** One screen, flat list.

| Option | Description | Selected |
|--------|-------------|----------|
| Current groups, flattened | Extrusion → Motion limits → FW-retraction | ✓ (amended) |
| Most-tuned first | Speed/Flow at top | |
| Planner's discretion | Research picks | |

**User's choice:** "current groups flattened, but lets use the pool colors to denote the grouping via icon colors"

| Option | Description | Selected |
|--------|-------------|----------|
| Keep in both | Fan in Fine-Tune AND Outputs, same command path | ✓ |
| Fine-Tune only | Remove part-cooling fan from Outputs | |
| Outputs only | Drop fan from Fine-Tune | |

**User's choice:** Keep in both.

| Option | Description | Selected |
|--------|-------------|----------|
| Preselect first param | Speed loads immediately | |
| Remember last-adjusted | Session memory, first-param fallback | ✓ |
| Empty state until tap | Muted prompt like Spoolman's empty DetailCard | |

**User's choice:** Remember last-adjusted.

---

## Stepper vs scrubber vs numpad

| Option | Description | Selected |
|--------|-------------|----------|
| Stepper default, scrubber for 0-100% | Scrubber only for fan/LED percent values | ✓ |
| Scrubber for all bounded ranges | Temp 0-300 etc. get scrubber | |
| Per-value mapping | Walk the list now | |

**User's choice:** Stepper default, scrubber for 0-100%.

| Option | Description | Selected |
|--------|-------------|----------|
| All in-place, retire pushes | Adjuster in Focus; takeovers for single-setting entry; full-screen pages retire | ✓ |
| Keep full-screen pages, restyled | Pages remain destinations | |
| Mixed, case-by-case | Per caller | |

**User's choice:** All in-place, retire pushes.

| Option | Description | Selected |
|--------|-------------|----------|
| Convert now, numpad | Field-takeover numpad in SpoolScreen | ✓ (amended) |
| Convert now, stepper+increments | 3-zone adjuster with coarse g increments | |
| Defer to Phase 29 | Keep the pilot pristine | |

**User's choice:** "go ahead and do 1, but we're going to drop our custom numpad, just use the android system keyboard but make sure we pull up the numpad version of it whenever appropriate."
**Notes:** NumpadPage retires app-wide → numeric system IME; supersedes P25 D-12's "NumpadPage for numeric" for macro params.

| Option | Description | Selected |
|--------|-------------|----------|
| On release | In-place drag visuals, one command on lift | ✓ |
| Throttled live | ~5-10/s dispatch during drag | |
| You decide | Planner picks per output type | |

**User's choice:** On release.

---

## Temperature anatomy

| Option | Description | Selected |
|--------|-------------|----------|
| Focus morphs: graph ↔ adjuster | Graph default; row tap swaps Focus to adjuster | ✓ (amended) |
| Graph in Field, adjuster in Focus | Graph always visible but smaller | |
| Graph stays Focus, adjuster takes Field | Adjuster works in Field region | |

**User's choice:** "1 - but we want to add additional, non heater related temperature readings to this page eventually, so we need to be able to handle readings that don't have an adjustment ability."

| Option | Description | Selected |
|--------|-------------|----------|
| Foot button → Field-takeover | Preset list swaps in place; scrim retires here | ✓ |
| Foot button → keep scrim | Zero rework, shared chooser | |
| Preset rows in the Field | Always-visible rows | |

**User's choice:** Foot button → Field-takeover.

| Option | Description | Selected |
|--------|-------------|----------|
| Both: foot Cooldown + per-heater Off | TURN_OFF_HEATERS foot + per-heater Off in adjuster | ✓ |
| Foot Cooldown only | All-off only | |
| Per-heater Off only | Drop global Cooldown | |

**User's choice:** Both.

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — icon tinted to trace color | Same-hue invariant survives | ✓ (amended) |
| Neutral icons, color elsewhere | Token-neutral rows | |
| You decide | Planner picks | |

**User's choice:** "yes, lets tint the icon to match. Also, i want to revise my previous remark about elements with no settings - when selecting an item that's in the list it's minimum settings should be show/hide and then a row of the color pool (which always pools from the 'colorful' theme pool on this page) that allows the user to select a color to draw the graph in. If we keep it to the 8 color pool colors we should be able to fit that on the screen without having to have a dedicated color picker."
**Notes:** Every selectable row's minimum adjuster = show/hide trace + 8-swatch Colorful-pool color row; heaters add the target-temp adjuster; chosen color drives the icon tint.

---

## Extrude + Outputs anatomy

| Option | Description | Selected |
|--------|-------------|----------|
| Focus = adjuster, actions in foot | Pure archetype | |
| Focus = command zone | Today's shape conformed | |
| You decide | Planner picks | |

**User's choice (free text):** "The extrude screen is not getting moved to the standard layout - it is similar to the move screen, where the function requires a specialized layout"
**Notes:** Extrude = specialized-layout exemption; conformance only.

| Option | Description | Selected |
|--------|-------------|----------|
| System numeric keyboard | Numeric-IME fields, clamped | ✓ (amended) |
| Presets + stepper only | No keyboard on this screen | |
| Temp links to Temperature screen | Navigate instead of inline | |

**User's choice:** "distance and speed popup the numeric system keyboard, temperature should pop up a page that is just a field list of the standard filament presets, along with the currenly loaded spools filament if available. Selecting a preset should only apply the extruder temperature when fired from the extrusion page. Selecting a preset closes the page, along with a back button at the bottom"

| Option | Description | Selected |
|--------|-------------|----------|
| Collapse into Focus | Field = output list, Focus = selected output's control | ✓ |
| Keep detail pushes, restyled | Pages rebuilt on the kit | |
| You decide | Planner picks | |

**User's choice:** Collapse into Focus.

| Option | Description | Selected |
|--------|-------------|----------|
| Fit in Focus, minimal rework | Brightness scrubber + gated channels + existing ColorWheel | ✓ |
| LED keeps a pushed page | The one exception | |
| Redesign LED properly now | Pull the deferred polish in | |

**User's choice:** Fit in Focus, minimal rework.

---

## Claude's Discretion

- Per-value increment step sets + clamp ranges (seeded from FineTuneShared/PrinterCommands).
- "Remember last-adjusted" persistence depth (session vs DataStore).
- PrintStatus Preheat: keep scrim vs adopt the new preset surface.
- Focus graph↔adjuster morph mechanics (cross-fade vs hard swap, flox-budgeted).
- Trace-color persistence shape (per-profile vs global).
- NavHost/route + Back behavior for the collapsed Fine-Tune and Outputs screens.

## Deferred Ideas

- Additional non-heater temperature readings (temperature_sensor: MCU/Pi/chamber) on the
  Temperature page — row model + graph controls designed for it now, sensors added later.
- Deeper LED aesthetic redesign — future polish.
