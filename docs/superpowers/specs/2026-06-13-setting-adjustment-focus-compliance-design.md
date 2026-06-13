# Setting-Adjustment Focus — Compliance Pass + Codified Rules

**Date:** 2026-06-13
**Status:** Design approved (verbal), pending written-spec review
**Author:** brainstorm session (Matthew + Claude)

## Goal

Bring every "change a value" Focus surface up to one shared, compliant standard, and write
down the rules that govern it so future settings screens inherit the same grammar. The shared
adjustment building blocks already exist (`AdjusterPanel`, `Scrubber`, `IncrementPicker`,
`FocusFrame`); this pass makes them *consistent* and *codified*, and develops the theme/component
rules that are currently missing.

This is a cross-component compliance sweep, not a rewrite.

## Scope — the three stepper sites

"All the different controls" means the adjustment grammar lives in three places, all in scope:

| Screen | Adjuster component | Stepper location |
|--------|--------------------|------------------|
| Fine-Tune | `AdjusterPanel` | its `−`/`+` row |
| Temperature (heater adjust) | `TemperatureAdjusterFocus` | its step controls |
| Outputs (fans / LEDs / servos / PWM pins) | `Scrubber` | the `Scrubber` `±` row |

Shared shell: `FocusFrame` (all). Shared step-set selector: `IncrementPicker`.

## The refinements

### A. Header carries the current selection's identity

The Focus header (`FocusFrame` `title` + `icon`) reflects the **currently-selected control**, not
the screen name. This generalizes the calibration-hub ruling ("the icon + title that used to live
here are gone — they're the FocusFrame header now") to every *single-item* Focus.

- **Fine-Tune** → selected param's name + icon (e.g. "Print Speed" + speed glyph).
- **Outputs** → selected output's name + icon.
- **Temperature** → generic **"Temperature" + launcher icon ONLY in the graph-overview state**
  (no sensor selected — the multi-trace graph is the aggregate display of the list items).
  Selecting a heater swaps the header to that **sensor's name + icon** for the heater-adjust
  state (owner ruling 2026-06-13). Temperature has two `FocusFrame` call sites; the overview one
  stays generic, the adjuster one becomes sensor-specific.

**Rule (to codify):** the Focus header reflects the **most specific thing currently loaded** —
the selected item's identity when a single item is in the Focus, the collection/screen identity
when the Focus shows an aggregate/overview (no single item). One uniform rule; Temperature's
generic overview is just the aggregate case of it.

**Consequence:** `AdjusterPanel`'s **Zone 1** (icon + name + reset) is **deleted** — its identity
is now redundant with the header. The panel collapses to two zones:
1. **Value** (absorbs slack, centered) — keeps the inline "was {baseline}" span and the R10
   one-shot rejection flash.
2. **Controls** (bottom-docked) — `−`/`+` stepper row + `IncrementPicker`.

**Note — e-stop interaction:** while a print is active, `FocusFrame`'s start icon morphs into the
e-stop (existing Focus-Frame law). During a print the selected control's *glyph* is therefore
replaced by the e-stop; its identity is carried by the centered **title** + the big **value**.
This is intended (e-stop wins the slot) and is not a regression.

### B. Revert-to-default moves to the header end, as a bare glyph

A new **optional, end-aligned header action slot** on `FocusFrame`: a bare tappable glyph
(neutral `text2` tint, sized/placed to mirror the start identity icon — no outline, no fill).
It is the symmetric end-side counterpart to the start identity icon.

- First consumer = **revert-to-default**, using the **`refresh`** ligature (owner-chosen).
  `refresh` is present in `material-icon-bucket.json` but has no `DinghyIcons` token yet — register
  one (proposed token name: `Revert`).
- **Visibility:** shown **only when the value deviates from default** (the same `shouldShowBaseline`
  predicate that drives the inline "was X"). At default → no glyph.
- **Why bare (not amber/caution):** reverting to the default is a *safe* move (owner ruling
  2026-06-13). It does not throw away anything the user can't trivially redo, so it spends no
  intent color.
- **Slot policy (to codify):** the bare-glyph header action is reserved for **safe** actions only.
  Anything caution/destructive stays a content button under the normal four-class intent law.
- **Temperature heaters / Outputs have no "default" baseline** → **no revert glyph** there. Their
  reset affordance is the existing **Off** button, unchanged.

The `onReset`/reset-button responsibility leaves `AdjusterPanel` entirely (Zone 1 is gone); the
screen wires `onReset` to the `FocusFrame` header action instead. `AdjusterPanel` keeps `baseline`
(still needed for the inline "was X").

### C. ± become icons, not typed characters

Stepper tiles switch from the literal `"−"` / `"+"` strings to the registered icon tokens
`DinghyIcons.Decrease` (`remove`) and `DinghyIcons.Increase` (`add`), rendered through
`OutlinedControl`'s icon path (icon-only control → auto-sizes to the 0.6U glyph tier = "typical
icon sizing"). Applies at all three stepper sites.

This **supersedes the WR-11 "locale-independent literal math glyph" exemption** currently
documented in `AdjusterPanel`. Document the supersession so it is not read as a regression.

### D. 1U caps on in-Focus controls

Enforce a 1U **cap** (`height(uDp)`, not merely a floor) on every interactive element inside the
adjuster Focuses:
- the `−`/`+` stepper row,
- `IncrementPicker` (today `heightIn(min = uDp)` only — can grow; add the cap),
- the `Scrubber` `±` row,
- the Temperature step controls.

UAT-5 already mandates this ("controls cap at 1U unless deliberately named otherwise"); this makes
it true in code. The LED `ColorWheel` remains the sole sanctioned >1U exception.

### E. Bottom-docked control group + shared content inset

Adopt the calibration-hub Focus layout as the canonical **"Focus with a docked action region"**
pattern:
- a weighted body absorbs slack,
- the control group docks to the bottom (constant position across params / orientations / screen
  sizes),
- the Focus uses the **same `contentInset` the calibration focus uses** (tighter than the 16dp
  default) so adjusters and the calibration hub share one padding rhythm.

Drop the redundant inner `padding(12.dp)` Fine-Tune currently stacks on top of `FocusFrame`'s
own content inset.

### F. Codify the rules in `docs/ui_design/`

Make the above law, not tribal knowledge:

- **COMPONENTS.md** — the header trailing-action slot (bare glyph, end-aligned, mirrors start
  identity sizing/tint); the "header = current selection identity (single-item) vs collection
  identity (aggregate)" rule; "no icon+name duplicated in content when the header carries it";
  `±` = `Increase`/`Decrease` icon tokens (WR-11 supersession noted).
- **LAYOUT.md** — the docked-action-region Focus pattern + the shared `contentInset`; reinforce
  the 1U control cap with the now-enforced sites.
- **THEMING.md** — bare-glyph header action = safe-actions-only (revert-to-default qualifies);
  spends no intent color.

## Components touched

- `FocusFrame` — add the optional end-aligned bare-glyph header action slot.
- `AdjusterPanel` — delete Zone 1; collapse to value + bottom-docked controls; icon steppers;
  1U cap; drop `onReset`/reset button (moves to header); keep `baseline` for "was X".
- `Scrubber` — icon `±`; 1U cap on the `±` row.
- `TemperatureAdjusterFocus` — icon steppers; 1U cap; header wiring stays generic (exception A).
- `IncrementPicker` — 1U cap.
- `DinghyIcons` — register the `refresh` token (`Revert`).
- `FineTuneScreen`, `OutputsScreen` — pass selected item to `FocusFrame` title/icon; wire revert
  to the header action; adopt shared inset / drop redundant padding.
- `TemperatureScreen` — overview `FocusFrame` stays generic; adjuster `FocusFrame` swaps to the
  selected sensor's name + icon; icon steppers / 1U via `TemperatureAdjusterFocus` (which composes
  `AdjusterPanel`); adopt shared inset. No revert glyph (heaters have no default baseline; Off
  button unchanged).
- `docs/ui_design/{COMPONENTS,LAYOUT,THEMING}.md` — codify the rules.

## Out of scope

- No change to the dispatch / trailing-commit batching, clamp authority (D-22), or rejection-flash
  mechanics — behavior is preserved.
- No change to the Off-button semantics for heaters / outputs.
- `FocusEdge.Progress` perimeter edge stays deferred.
- No new adjustment archetype; this consolidates the existing ones.

## Testing / verification

- Host unit tests for any pure logic added (e.g. revert-visibility predicate reuse).
- On-device UAT on **both** test targets (flox + moto) per the standing two-device rule:
  Fine-Tune, Temperature (overview + heater adjust), Outputs (fan/LED/servo) in portrait and
  landscape, at fs = S/M/L, verifying:
  - header shows the selected control (Fine-Tune/Outputs) / stays generic (Temperature),
  - revert glyph appears only off-default and reverts correctly,
  - `±` render as icons at the right size,
  - all in-Focus controls sit at ≤1U,
  - control group docked at the bottom with the calibration padding rhythm,
  - nothing clips at 5U phone-landscape.
