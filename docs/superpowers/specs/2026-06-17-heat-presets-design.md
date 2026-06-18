# Heat Presets — Design

**Date:** 2026-06-17
**Status:** Design (approved pending spec review)
**Scope:** Per-printer preheat setpoint presets ("Heat Presets") that replace the hardcoded
`MATERIAL_PRESETS` (PLA/PETG/ABS/TPU) everywhere except the Extrude page.

---

## Goal

Give each printer its own list of preheat presets. A preset is a named set of target
temperatures spanning **every settable heating element Moonraker lists** — `extruder`(s),
`heater_bed`, any `heater_generic`, and any `temperature_fan`. Three defaults (Low / Medium /
High) are seeded when a printer is added. The list lives in **Printer Settings → Heat Presets**.
These presets replace the current hardcoded material presets in every preheat picker in the app
(the Extrude page is the one exception — it stays extruder-only).

## Locked decisions (from brainstorming)

1. **Blank = omit** a heater from the preset (absent key). **`0` is a real stored value** that
   commands `TARGET=0` (turn the heater off). Placeholder text: *"Leave blank to skip or 0 to turn off"*.
2. **Replace the hardcoded presets everywhere EXCEPT the Extrude page.** Extrude stays
   extruder-only (applies just the nozzle setpoint).
3. **Only new printers** get the three seeded presets. Existing printers start empty (no
   migration seeding).
4. **Numeric text box, clamped** to each heater's Moonraker min/max for value entry (Settings is
   the sanctioned keyboard exception).
5. **Icons:** use `question_mark` for any glyph not already selected (owner-authorized for this
   work) — Add (foot), Edit (Focus docked action), and the per-heater line rows. Reuse existing
   `TempPresets` (thermostat_auto) for the Printer Settings row and the existing Back/Delete foot
   glyphs.

---

## 1. Data model

```kotlin
data class HeatPreset(
    val id: String,                  // stable UUID — survives renames/edits
    val name: String,
    val setpoints: Map<String, Int>  // Moonraker object name -> degC, SPARSE
)
```

- Keys are full Moonraker object names: `extruder`, `heater_bed`, `heater_generic chamber`,
  `temperature_fan exhaust`. Stable across sessions → clean reconciliation on edit.
- **Absent key = skip** (preset never touches that heater). **Value `0` = explicitly off.**

## 2. Storage & seeding

- New per-printer **`HeatPresetStore`** backed by DataStore: a JSON list keyed
  `presets_<profileId>`. Mirrors `TraceStylePrefs` per-printer key scoping and
  `SavedLocationPrefs` JSON-list serialization. This is the 10th DataStore.
- Exposed via **`AppContainer.activeHeatPresets: Flow<List<HeatPreset>>`** (`flatMapLatest` on
  the active profileId, pre-sorted), plus `addOrUpdateHeatPreset(preset)` and
  `deleteHeatPreset(id)` routed through the process-lifetime `writeScope` (never a composition
  scope — see the write-scope-cancellation rule). Writes are read-modify-write inside one
  `dataStore.edit {}`.
- **Seed at profile creation** (hook into the add-printer path), only for new profiles. Seeds are
  extruder + bed only (generics/fans omitted), since add happens before first connect:
  - **Low** — `extruder=150, heater_bed=50`
  - **Medium** — `extruder=200, heater_bed=65`
  - **High** — `extruder=230, heater_bed=90`
- Existing printers: empty list until presets are added manually.

## 3. Heater enumeration

- `parseHeaterLimits` already discovers `extruder*` / `heater_bed` / `heater_generic *`. Add
  **temperature_fan** discovery (from the objects list) into a small
  `enumerateSettableHeaters()` returning ordered `(objectName, displayName, limits?)`.
- **Order:** extruder(s) → bed → `heater_generic` (alpha) → `temperature_fan` (alpha).
- **Display names** via the existing Title-Case heater helper, extended:
  `extruder`→"Nozzle", `heater_bed`→"Bed", `heater_generic chamber`→"Chamber",
  `temperature_fan exhaust`→"Exhaust". (Reconciled 2026-06-17: "Nozzle" not "Extruder" — matches
  the app's existing `TemperatureHolder.label` convention shown everywhere else in the UI.)
- The create/edit wizard enumerates from the **live connected printer's** heater set. If the
  printer has never connected (no live config), it falls back to extruder + bed only.

## 4. Heat Presets screen (Printer Settings → new row)

- New `ListRow` in `PrinterSettingsScreen` (before the Power stub), icon `TempPresets`, label
  "Heat Presets", → new `NavDest.HeatPresets`.
- **Field:** list of presets, **sorted by extruder temp ascending**; presets with no extruder
  setpoint sort to the end (then by name).
- **Select a preset** → Focus shows its setpoints as a fit-to-display line-item list
  (`DisplayName — value°C`, with `0` rendered as "Off"), and a docked **Edit** button at the
  bottom (docked-action Focus law; icon `question_mark`).
- **Foot bar:** `Back · Delete · Add`.
  - **Delete** enabled only when a preset is selected; fires a `ConfirmGuard` before deleting.
  - **Add** always enabled (icon `question_mark`).
- **Empty state:** foot is just `Back · Add`.

## 5. Create / Edit wizard (chain of Focus screens — shared by both)

A `FocusFrame`-based wizard. State holder tracks the heater list, the working sparse map, and the
current step index.

- **Screen 1 — Name:** header "Preset Name"; a `TokenTextField` (alpha keyboard — sanctioned
  save-name exception). Foot: `Cancel · Next`. On edit, prefilled with the current name.
- **Screens 2…N — one per live heater:** header = stripped heater display name; a numeric
  `TokenTextField` below, **clamped to that heater's min/max**, placeholder
  *"Leave blank to skip or 0 to turn off"*. Foot: `Cancel · Next` (last screen: `Cancel · Save`).
  - On **edit**, each field is prefilled from the saved value if present; **new** heaters (added
    to the printer since the preset was made) appear blank; heaters no longer in config don't
    appear → the saved preset is rebuilt fresh against current config. This satisfies "edit picks
    up config changes."
- **Save** writes the sparse map (blank → key omitted; any number incl. `0` → stored); the preset
  lands in the Field list (re-sorted). Create assigns a new UUID; edit keeps the existing id.

## 6. Applying a preset (the replacement everywhere)

- New `PrinterCommands.applyHeatPreset(setpoints: Map<String, Int>)` builds one multi-line gcode
  script, every value clamped:
  - `extruder` / `heater_bed` / `heater_generic` → `SET_HEATER_TEMPERATURE HEATER=<arg> TARGET=<t>`
    (strip the `heater_generic ` prefix for `HEATER=`).
  - `temperature_fan` → `SET_TEMPERATURE_FAN_TARGET FAN=<name> TARGET=<t>` — **new command**;
    needs rows in `docs/commands/catalog.json` + `printer-matrix.json` (catalog-drift rule).
- A new `CommandSpec` wraps `applyHeatPreset` for dispatch.
- **Replace `MATERIAL_PRESETS`** in these pickers, applying **all** heaters in the chosen preset:
  - Temperature screen `PresetPicker`
  - Print Status home `PresetSelector`
  - Active-print foot preheat
- **Extrude page** keeps nozzle-only behavior: shows the same per-printer presets but applies
  **only the `extruder` setpoint**; presets without an extruder value are **hidden** there
  (nothing to do).
- Remove `MATERIAL_PRESETS` and `data class Preset(name, nozzle, bed)` once all consumers migrate.

## 7. Components reused

- `FocusFrame`, `ListBlock`, `ListRow` / `ListRowIcon` / `ListRowLabel`, `OutlinedControl`,
  `FootButtonBar` / `FootAction`, `TokenTextField`, `ConfirmGuard`.
- Wizard pattern modeled on `BedMeshScreen` save-name field-takeover.
- Per-printer Flow seeding modeled on `TraceStylePrefs` (`flatMapLatest` on active profileId).

## 8. Edge cases & notes

- **Sorting with no extruder value:** sort to the end of the list, then alphabetically by name.
- **Not connected while creating/editing:** wizard falls back to extruder + bed only.
- **Deleting all presets:** allowed (incl. the seeded three); list goes empty, pickers show only
  loaded-spool (if any). No re-seeding.
- **`0` vs blank:** `0` is stored and commanded (`TARGET=0`); blank omits the key entirely.
- **Existing printers** never auto-seed; they start empty.

## 9. Out of scope (v1)

- Per-preset cooldown / "all off" preset (cooldown already exists elsewhere).
- Sharing presets across printers.
- Reordering presets manually (the list is auto-sorted by extruder temp).
