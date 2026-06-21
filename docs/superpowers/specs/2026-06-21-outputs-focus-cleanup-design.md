# Outputs Focus pages — cleanup & RGB rework

**Date:** 2026-06-21
**Status:** approved (owner, 2026-06-21)
**Type:** UI cleanup + small feature (RGB/RGBW slider rework)

## Problem

The Outputs screen renders a per-output control surface in the Focus region
(`OutputFocusControl`, hosted in `OutputsScreen`'s `FocusFrame`). The surfaces have drifted out
of conformance with each other and with the redesign law:

- **Switches** (`OutputToggleControl`, digital on/off) wrap their content in a *second*
  `ScreenScaffold` nested inside the FocusFrame, still render a **redundant name `Text`** that
  duplicates the FocusFrame header, and put **On/Off as large buttons in the body** with a lone
  **Back** in the foot bar. The scrubber/LED surfaces are plain `Column`s inside the FocusFrame
  and already dropped their duplicate name — switches never got that cleanup.
- **Value line is orphaned.** The scrubber surfaces (servo, plain light, fan, heater) show the
  live value via the shared `Scrubber` header, which lays out `[name] start · value+unit end`.
  In Outputs `name` is blank, so the value floats alone, **right-aligned**, looking off-center.
- **RGB light** picks color with the bespoke `ColorWheel` (hue ring) + a brightness `Scrubber`,
  **saturation locked at 100%**. The theme color picker already has a clean, reusable
  `HsvSliders` (H/S/V) component that should be reused here.
- **Two different "Back" buttons exist** and one is redundant. The Field (list) has a
  `FootButtonBar` Back that *leaves the Outputs screen* (`onBack`). Each Focus surface has its
  **own** in-Focus Back that merely *deselects* (`onSelect(null)` → empty prompt). Outputs are
  switched by tapping list rows, so the in-Focus Back adds nothing.

## Goals

1. Every Outputs Focus surface follows one shape: **value/control rows fill the body; action
   buttons pinned at the bottom in a real `FootButtonBar`** (per class rules — 8dp spacing,
   count-driven labels).
2. Remove the redundant **in-Focus Back** from *all* output surfaces (owner decision).
3. **Center the value line** for the name-less scrubber surfaces.
4. **Rework RGB/RGBW** to theme-style sliders; delete `ColorWheel`.

Non-goals: changing the Field list, the empty-prompt state, the e-stop header morph, or any
non-Outputs screen's `Scrubber` usage.

## Design

### Unifying principle

`OutputFocusControl` renders, for every family, a plain `Column` inside the existing `FocusFrame`
body: control/value rows first (taking the slack via `weight`), then a `FootButtonBar` as the
last child. No nested `ScreenScaffold`. The FocusFrame's `contentInset` (currently `FocusInset/2`)
frames the bar; no extra horizontal padding is added at the call site.

### Per-surface result

| Surface | Body (fills space) | Foot bar |
|---|---|---|
| **Switch** (digital on/off, interactive) | Big **current-state readout** (On/Off, color-coded by state) | `[On]` (Go) · `[Off]` (`ControlSpecs.outputOff`) |
| **Switch, read-only** (static_value) | On/Off hero + "Read-only" caption | *(none — exit via Field Back)* |
| **Fan / Servo / Heater / Plain light (PWM/pwm_tool)** | `Scrubber` (name-less) with **centered value** | `[Off]` |
| **RGB light** | `HsvSliders` (H/S/V) | `[Off]` |
| **RGBW light** | `HsvSliders` (H/S/V + **White**) | `[Off]` |
| **White-only light** | brightness `Scrubber` (centered value) | `[Off]` |

Capability gating reuses the existing `descriptor.ledHasRgb` / `descriptor.ledHasWhite` flags
(GAP-B hide-not-grey):
- `ledHasRgb && ledHasWhite` → RGBW → H/S/V + W
- `ledHasRgb && !ledHasWhite` → RGB → H/S/V
- `!ledHasRgb && ledHasWhite` → white-only → single brightness `Scrubber`

`V` (Value) is the overall brightness of the RGB color, so it **replaces** the separate
brightness scrubber for RGB/RGBW. The independent White channel is its own slider because an
RGBW white emitter cannot be synthesized from H/S/V.

### Intent colors

- Switch **On** = `Intent.Go` (the expected "turn it on" action).
- Every "turn off" action across all surfaces = the shared `ControlSpecs.outputOff` (one spec,
  one color, app-wide).
- The current On/Off *state* is conveyed by the body readout, not by toggling button styling
  (the old active-button-wears-accent behavior is retired — state lives in the body).
- No in-Focus Back, so no Back coloring decision.

### Shared-component changes (one edit each → fixes everywhere)

1. **`Scrubber.kt`** — when `name` is blank, **center** the value in the header row instead of
   the `[name] … value]` space-between layout. Named scrubbers (Fine-Tune / Temperature / Move)
   are unchanged (name still start-aligned, value end-aligned). Blast radius is limited to the
   `name == ""` call sites (Outputs scrubber surfaces, white-only LED brightness).

2. **`HsvSliders.kt`** — add an **optional White track** (4th `Track`, black→white gradient).
   Surface it via an optional `white: Float?` + `onWhiteMove`/`onWhiteSettle` (null = 3 tracks,
   non-null = 4 tracks). The component keeps owning its own layout/spacing.

3. **`OutputToggleControl.kt`** — rewrite:
   - Drop the nested `ScreenScaffold` → plain `Column` (host is the FocusFrame body).
   - Drop the duplicate `prettyName` `Text` (FocusFrame header carries identity).
   - Body (`weight(1f)`) = current-state readout for **both** interactive and read-only pins.
   - Foot = `FootButtonBar` → interactive: `[On][Off]`; read-only: no foot bar.
   - Remove `onBack` (and its usage).

4. **`OutputFocusControl.kt`** —
   - Scrubber & LED surfaces: remove the in-Focus Back; convert the foot `Row` of
     `OutlinedControl` → `FootButtonBar` (`[Off]`).
   - RGB/RGBW route to `HsvSliders` (drop the brightness `Scrubber`; V is brightness); white-only
     keeps the brightness `Scrubber`.
   - Update the LED settle wiring to carry **saturation** and the **white** channel.
   - Drop the `onBack` param threading where it only fed the removed in-Focus Back.

5. **`ColorWheel.kt`** — **deleted** (sole consumer was the LED surface). Remove its references.

6. **Command layer (LED dispatch)** — the LED settle must send the **full R/G/B/W state** in one
   `SET_LED` (Klipper zeroes any channel not specified), and convert from full **HSV (incl.
   saturation)**, not hue-only. Update `dispatchColor`/`dispatchWhite` (or fold into a single
   full-state dispatch) accordingly. Verify the catalog command builds `RED= GREEN= BLUE= WHITE=`.

7. **Docs** — retire the UAT-5 ">1U ColorWheel is the sole sanctioned exception in Outputs" note
   in `docs/ui_design/LAYOUT.md` and `docs/ui_design/COMPONENTS.md`, and in the sketch-findings
   skill's `design_direction` UAT-5 line. The sliders are ≤1U each, so Outputs has no >1U
   control anymore.

## Testing

- Host/unit: `Scrubber` centers value when name blank, keeps space-between when named;
  `HsvSliders` renders 3 vs 4 tracks by `white` nullability; HSV→RGB(+W) conversion for the LED
  dispatch (full-state, saturation honored).
- Preview matrix: each output family's Focus surface (switch on/off/read-only, scrubber,
  RGB, RGBW, white-only) renders with body + foot bar, no in-Focus Back.
- On-device UAT (flox + moto per [[dinghy-test-devices]]): switch On/Off, a scrubber output's
  centered value, an RGB light's H/S/V, and (if available) an RGBW white channel.

## Risks / notes

- `Scrubber` is shared and load-bearing — the centering change must be strictly gated on
  `name.isBlank()` so no named scrubber shifts.
- LED `SET_LED` full-state dispatch: confirm the existing command builder doesn't already
  preserve channels; if it sends only specified params, RGBW would otherwise blank the other
  channels on each adjust.
- Read-only switch with no foot bar and no in-Focus Back relies on the Field's Back for exit —
  intended, but worth confirming the Field Back is always reachable in portrait (it is — the
  Field stacks below the Focus).
