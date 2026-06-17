# Extrude Screen Rework — Design

**Date:** 2026-06-16
**Status:** Approved (owner brainstorm 2026-06-16)
**Branch:** `extrude-rework`

## Goal

Rebuild the Extrude/Extrusion screen to comply with the current jiib UI law (Focus/Field
grammar, intent colors, component classes), and broaden it from a bare feed pad into the
filament-handling hub: feed controls, filament-runout sensor control, screen-scoped filament
macros, inline nozzle preheat, and a jump to the spool library.

This is a **reorganization + targeted additions**, not a from-scratch rebuild — roughly 80% of
the behavior already exists on `ExtrudeScreen.kt` (extrude/retract, distance/speed presets, tool
selector, cold-extrude gate, preheat takeover, Load/Unload). The cold-extrude safety gate and the
existing preset values are preserved.

## Grammar

**Stable feed Focus + action Field.** The Focus never swaps — it always shows the core feed
surface. The Field is a scrolling list of actions arranged around it, each acting in place
(apply / run / toggle / navigate) without disturbing the Focus.

- **Portrait:** Focus stacked over a scrolling Field.
- **Landscape:** 50/50.

Rationale: the primary action (extrude) stays on screen at all times — critical in the cold-gated
state where the user must heat before extruding — and a fixed Focus minimizes recomposition churn
on the Adreno-320 floor.

## Focus (fixed) — core feed surface

- **Extrude / Retract** — two `BigCommand` buttons. Governed by the existing cold-extrude safety
  gate (`extruder.can_extrude`, fail-safe false); when cold they dim and show the existing
  "heat first" hint. Reused as-is.
- **Distance ±stepper** — `[ − ] value [ + ]`, value centered, +/− index through the discrete
  preset list **1 / 5 / 25 / 50 mm**. Clamped at the ends. Steps above the printer's
  `max_extrude_only_distance` are skipped/disabled. The selected value is the amount fed per
  Extrude/Retract press.
- **Speed scrubber** — capped to the printer's reported `max_extrude_only_velocity` (fallback
  ~15 mm/s when unreported). Default 5 mm/s.
- **Nozzle readout** — live `current / target °C` display (display only; arbitrary temp entry
  lives on the Temperature screen).

## Field (scrolling action list) — top to bottom

1. **Runout sensor toggle(s)** — one live `ToggleRow` per discovered `filament_switch_sensor` /
   `filament_motion_sensor`, reflecting the real `enabled` state from printer objects.
   **The entire section is hidden when the printer reports no such sensors.** Toggling sends
   `SET_FILAMENT_SENSOR SENSOR=<name> ENABLE=[0|1]`. Placed immediately under the feed controls
   per owner request.
2. **Macros** — `Load Filament` / `Unload Filament` auto-included at the top when those macros
   exist, followed by the user's **Extrude-scoped pinned macros**. **All macro rows run bare on
   tap — no parameter entry.** Custom macros with params are expected to run without them. Pins
   are a separate namespace (`ExtrudeMacroPrefs`), independent of the global Macros screen pins.
3. **Thermal preset chips** — inline (always visible, no takeover), **nozzle-only** (no bed):
   loaded-spool temp (when a spool with a temp is active) + PLA / PETG / ABS / TPU. One tap
   applies the nozzle setpoint; heating opens the cold gate.
4. **Spool Manager →** — nav row, jumps to the existing spool library via `onOpenSpool`.

## Foot bar

**Back** (accent / neutral-nav) · **Cooldown** (turn off heaters — no confirm guard) ·
**Macro-settings** (curate this screen's pinned filament macros — the `ExtrudeMacroPrefs`
management surface).

E-stop is not in the foot bar — `FocusFrame` docks it in the header when a print is active.

## New plumbing required

- **`SET_FILAMENT_SENSOR`** — promote from catalog `reference_only` → registered:
  - `SetFilamentSensorArgs(sensor, enable)` + `CommandSpec` in `CommandRegistry`.
  - Clamped/escaped builder in `PrinterCommands` (`SET_FILAMENT_SENSOR SENSOR=… ENABLE=0|1`).
  - Matching `docs/commands/catalog.json` + `printer-matrix.json` rows, or `CommandCatalogDriftTest`
    fails (known trap — see memory `dinghy-command-catalog-drift`).
- **`max_extrude_only_velocity`** — parse from `configfile.settings.<extruder>` alongside the
  existing `max_extrude_only_distance`; surface into the Extrude view-model. Fallback ~15 mm/s.
- **Sensor discovery + live state** — subscribe/surface `filament_switch_sensor` /
  `filament_motion_sensor` objects (names + `enabled`) into the Extrude view-model. Cleared on
  reconnect.
- **`ExtrudeMacroPrefs`** — new DataStore-backed pin set mirroring `MacroPrefs` /
  `SavedLocationPrefs`. Writes routed through a process-lifetime scope (not a composition scope —
  see memory `dinghy-compose-write-scope-cancellation`).

## Reused as-is

Cold-extrude gate, tool selector (only when extruderCount > 1), `FocusFrame` (header + docked
e-stop), `BigCommand`, `ToggleRow`, `Scrubber`, the existing preheat/material preset values
(nozzle component only), `onOpenSpool` navigation.

## Out of scope

- Cooldown availability/status filter (a later screen-availability pass will gate it).
- Arbitrary nozzle-temp entry (Temperature screen owns that).
- Macro parameter entry (macros run bare).
- Per-sensor friendly naming beyond Klipper's section name.
- WebRTC / MJPEG camera concerns.

## Component / token compliance

All controls route through existing component classes (`FocusFrame`, `FootButtonBar`, `ListRow`,
`ToggleRow`, `Scrubber`, `BigCommand`) and role tokens — no inline `fontFamily`/`fontSize`/raw
colors (enforced by `FontConformanceTest`). Button intents follow R5: accent = neutral/nav
(Back), the heat-off Cooldown is hazardous-but-deliberate. New icons, if any are needed, are
**NOT** chosen by Claude — owner is asked (icon law).
