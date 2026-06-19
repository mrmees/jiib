# The "Heaters" Concept — Design Spec

**Date:** 2026-06-18
**Status:** Approved (brainstorm complete) — ready for implementation plan
**Supersedes:** the ambiguous "preheat / cooldown" split (`2026-06-17-preheat-cooldown-toggle-switch-design.md`) and the bespoke per-screen preset/cooldown UIs. Builds directly on the user-definable Heat Presets system (`2026-06-17-heat-presets-design.md`).

## Problem

The app currently has three different, inconsistent ways to reach heating controls:

- **Home/standby:** a foot button that *swaps between* "Preheat" (`chair_fireplace`) and "Cooldown" (`mode_heat_off`) based on whether any heater is on — reactive to current temperature, two different concepts.
- **Extrude:** only a "Cooldown" foot button, plus inline nozzle-only preset chips (PLA/PETG/ABS + a loaded-spool chip) living in the filament hub Field.
- **Temperature/Adjust:** a two-row foot bar — a full-width "Presets" button (opens a `PresetPicker` Field takeover) plus a bottom row of `[Back · Cooldown · Monitor-return]`.

This is ambiguous. "Preheat" vs "Cooldown" is a false dichotomy, the surfaces don't match each other, and a requirement was missed in the original heat-preset switchover: **the loaded spool's temperatures are not folded into the preset list** (Home and Extrude still use bespoke spool code paths).

## Goal

Collapse all of it into one clear **"Heaters"** concept:

1. A single, reusable **Heaters list** (Focus/Field takeover) used identically on every surface, parameterized only by apply **scope**.
2. A **built-in OFF row**, always present, pinned to the top of that list, separate from user presets.
3. The **loaded spool** appears as a dynamic row in that same list (the missed requirement), via one mechanism — no bespoke per-screen spool code.
4. No reactivity to current temperature anywhere — the entry points are static.
5. Consistent iconography: `mode_heat` on every Heaters entry button (one exception: the Temperature Monitor→Adjust toggle becomes `fire_check`).

## Non-Goals

- No new Klipper/Moonraker command surface — all dispatch reuses `applyHeatPreset`, `setHeater`, and `cooldown` (`TURN_OFF_HEATERS`).
- No change to the Heat Presets create/edit wizard or its DataStore.
- No change to which heaters are "recognized" — reuse the existing enumeration.
- No multi-color / advanced spool handling beyond reading the loaded spool's nozzle/bed temps and color.

---

## The Heaters List (the core, reused everywhere)

A single Field-content composable — `HeatersList` — parameterized by an **apply scope**:

```
enum class HeatScope { Full, ExtruderOnly }
```

Rows are produced by a **pure function** (unit-testable, no Compose) so ordering and scope→command mapping can be verified directly:

```
buildHeatersRows(
    presets: List<HeatPreset>,        // user presets, already sorted by extruder temp asc
    loadedSpool: LoadedSpoolTemps?,   // null if no spool / no usable temp
    scope: HeatScope,
    extruderObject: String,           // the heater to target under ExtruderOnly — the ACTIVE tool,
                                      //   not literally "extruder" (multi-tool: "extruder"/"extruder1"…)
    capabilities: Capabilities,       // to drop setpoints for heaters this printer lacks
): List<HeatersRow>
```

Each `HeatersRow` carries display data (icon, label, summary, tint) **plus** a `HeatAction` that is already **capability-filtered** and **heater-correct**, so the call site just dispatches it.

**`extruderObject`** comes from `vm.activeHeater` on Extrude (`ExtrudeScreen.kt:196` already dispatches there via `holder.setActiveTool`). On `Full`-scope surfaces the nozzle maps to the primary `"extruder"` (matching today's `selectPreheatPath`); `extruderObject` is only consulted under `ExtruderOnly`.

### Row order (top → bottom)

| Order | Row | Icon | Label | Shown when |
|-------|-----|------|-------|------------|
| 1 — **pinned top** | **OFF** | `thermometer` | "OFF" | always |
| 2 — **pinned** | **Loaded spool** | `ev_shadow`, tinted to filament color | filament `name` → `material` → "Loaded filament" | a spool is loaded **and** it carries ≥1 usable temp |
| 3… | **User presets** | `thermostat` | preset name | one row per user preset, sorted by extruder temp ascending (existing `HeatPresetPrefs` sort) |

- Preset rows keep their existing summary text (sparse setpoints joined by `/`).
- The spool row's summary shows its temps (e.g. nozzle/bed) — reuse the existing preset-row summary formatting.
- The OFF row needs no temp summary.

**Scope-aware visibility (no dead rows):** under `ExtruderOnly`, the spool row and any preset row that defines **no extruder temp** are **omitted** (they would otherwise be no-op rows). OFF is always shown. Under `Full`, all presets and the spool row (with ≥1 usable temp) are shown. This mirrors today's Extrude chip filter (`.filter { it.extruderTemp != null }`).

### Apply behavior (scope-dependent)

Selecting **any** row applies immediately **and returns the Field to its normal list**. A **Back** foot button dismisses the takeover without applying.

| Row | `Full` scope (Home, Temperature) | `ExtruderOnly` scope (Extrude) |
|-----|----------------------------------|-------------------------------|
| **OFF** | `cooldown` → `TURN_OFF_HEATERS` (turns off **all** recognized heaters) | `setHeater(extruderObject, 0)` (active nozzle only) |
| **Loaded spool** | nozzle → `setHeater("extruder", nozzle)` **and** bed → `setHeater("heater_bed", bed)`, each emitted only if the spool defines that temp **and** the printer has that heater (capability filter) | `setHeater(extruderObject, nozzle)` only |
| **User preset** | `applyHeatPreset(setpoints)` with the map **capability-filtered** (drop heaters this printer lacks) — preserves `temperature_fan` routing | `applyHeatPreset({ extruderObject : preset.extruderTemp })`; row omitted when the preset has no extruder temp |

Notes:
- **Capability filtering is mandatory** (Codex BLOCKER): seeded/default presets always include `heater_bed`, and `applyHeatPreset`/`setHeater` are **not** per-heater gated at the dispatcher (`CommandRegistry.kt:443,457`). So `buildHeatersRows` must drop any setpoint whose heater fails `Capabilities.hasObject(...)` before building the action — otherwise a default preset sends invalid bed gcode on a bedless printer. This filtering lives in the pure builder so it's directly testable.
- Preset apply keeps using **`applyHeatPreset`** (not naive per-heater `setHeater`) so presets that carry a `temperature_fan` entry still route to `SET_TEMPERATURE_FAN_TARGET`.
- `Full`-scope OFF deliberately uses the `TURN_OFF_HEATERS` macro (not per-heater zeros) so it kills heaters that aren't referenced by any preset — matching "turns off all recognized heaters." The macro is inherently global, so no capability filter is needed there.
- `ExtruderOnly` OFF must **not** use the macro (it would kill the bed); it sets the **active** extruder to 0 explicitly.
- The spool row reuses the existing `settingsExtruderTemp` / `settingsBedTemp` / color logic — see "Loaded-spool source" below.

### Dispatch keys & guaranteed OFF delivery

The dispatcher silently drops a duplicate key while it is in-flight or inside its 400 ms debounce (`CommandDispatcher.kt:119`) — this previously caused a heater-stays-hot bug (`TemperatureScreen.kt:317`), fixed by routing Off through the heater batcher's guaranteed-delivery path (`onHeaterOff → batcher.tap(0.0)`).

The spec therefore requires:
- **Distinct keys per row/action:** `heat_off` (OFF), `heat_spool` (loaded spool), `preset_<id>` per preset — **never** the shared `apply_heat_preset` default key (which would let one apply drop another).
- **OFF must be guaranteed-delivery, never droppable — achieved via a dedicated non-colliding key.** `Full` OFF uses the unique `cooldown`/`TURN_OFF_HEATERS` key. `ExtruderOnly` OFF (the active extruder → 0) is dispatched on the dedicated `heat_off` key. Because nothing else dispatches on `heat_off`, the dispatcher's per-key dedupe can never drop it, and — critically — the Extrude screen has **no quiet-window batcher** scheduling a later same-heater commit (its inline manual-set path is removed with the chips), so there is no pending nonzero command that could re-heat after OFF. This is simpler than, and strictly sufficient versus, the heater batcher that the Temperature screen's *scrubber* needs (the scrubber continuously re-commits on one shared per-heater key, which is the only reason it required the quiet-window machinery). _(Revised after review confirmed Extrude has no competing batcher.)_

---

## Entry points (3 surfaces)

The entry button icon is **`mode_heat`** on all three. No reactivity to current heater state — the button is always the same, always present.

### 1. Home / standby — `PrintStatusField.kt`

- **Scope: the idle/standby foot bar only.** Printing, paused, **and Complete** keep their existing foot bars unchanged (Pause/Cancel while printing; `[Dismiss · System]` on Complete — owner decision 2026-06-18: manage heaters by dismissing to idle first). No Heaters button during a print; the e-stop remains the emergency path, and live heat tweaks during a print happen on the Temperature screen. "Always present" means *within the idle state*, not literally every state.
- In the standby states: **delete** the Preheat↔Cooldown swapping foot button (`FootPreheat` / `FootCooldown`, the `anyHeaterOn`-driven swap) and **replace** it with one static **Heaters** foot button (`mode_heat`, `Intent.Accent`) — no longer reactive to heater state.
- Tap → Field swaps the waterfall destination list → `HeatersList(scope = Full)`.
- **Delete** the `selectPreheatPath` / `PreheatPath.DirectTemps` spool branch and the `PresetSelector` scrim fallback — the spool is now a list row, and the list *is* the selector.

### 2. Extrude — `ExtrudeScreen.kt`

- **Replace** the Cooldown foot button with a **Heaters** foot button (`mode_heat`, `Intent.Accent`).
- Tap → Field swaps the filament hub (slider, runout toggles, macros, spool link) → `HeatersList(scope = ExtruderOnly)`.
- **Remove** the inline nozzle preset chips and the inline loaded-spool chip (`HeatPresetRow` list at ~L330–382). Their content is now in the Heaters list. ("The chips literally become the same rows in the new preset structure.")

### 3. Temperature — `TemperatureScreen.kt`

**Monitoring mode foot bar** `[Back · Settings · Adjust-enter]`:
- Change only the **Adjust-enter** icon: `OutputHeater` (`mode_heat`, L688) → **`fire_check`**. Everything else unchanged.

**Adjust mode foot bar** — today it is *two* rows (`[Presets]` full-width, then `[Back · Cooldown · Monitor-return]`). Collapse to **one** row:

> `[ Back · Heaters · Monitor-return ]`

- **Delete** the full-width `Presets` foot row entirely.
- **Replace** `Cooldown` (`TempCooldown` / `onCooldown`) with **Heaters** (`mode_heat`, `Intent.Accent`) → opens `HeatersList(scope = Full)`.
- **Keep** `Back` (exits the screen, `onBack`) and **Monitor-return** (`MonitorMode` glyph, flips `mode → Monitoring`) unchanged.
- The existing `TempFieldMode.PresetPicker` Field takeover **becomes** the unified `HeatersList`: it already renders a loaded-spool row first (L742) and presets after, so it's the right host — but its current `spoolPreset` (`TemperatureScreen.kt:256`) only builds when a **nozzle** temp exists, dropping bed-only spools. **Replace** `spoolPreset` with the shared `LoadedSpoolTemps` mechanism (which keeps a bed-only spool under `Full`), pin the **OFF row at top**, and let the spool row fall to 2nd. Its `Back` returns to `SensorList` (stays in Adjust mode).

---

## Loaded-spool source (the missed requirement, unified)

The loaded spool becomes row #2 of `HeatersList` on **all** surfaces — one mechanism, replacing the two bespoke paths (Home `DirectTemps`, Extrude chip).

- Source: the active spool detail (`activeSpoolDetail` / `spoolDetail`) →
  - nozzle: `filament.settingsExtruderTemp` (`Int?`)
  - bed: `filament.settingsBedTemp` (`Int?`)
  - color: `filament.colorSwatches.firstNotNullOfOrNull { parseNormalizedHex(it) }`
  - label: `filament.name` → `filament.material` → "Loaded filament"
- The row is shown only when a spool is loaded **and** at least one of nozzle/bed is non-null.
- Icon tint reuses the existing spool-icon tint treatment (the same `iconTint = spoolColor` applied to `DinghyIcons.SpoolFilament` / `ev_shadow` on `SpoolScreen`). Neutral fallback if no color.
- Wrap the read into a small `LoadedSpoolTemps?` (nozzle, bed, color, label) at each call site so `buildHeatersRows` stays Compose-free and testable.

---

## Icons — registry status & verification

| Use | Glyph | Status |
|-----|-------|--------|
| Heaters entry buttons (Home, Extrude, Temp-Adjust) | `mode_heat` | ✅ registered (`OutputHeater`) |
| Preset rows | `thermostat` | ✅ registered (`LauncherTemperature`) |
| Loaded-spool row | `ev_shadow` tinted | ✅ registered (`SpoolFilament`) |
| Temp Monitor→Adjust toggle | `fire_check` | ✅ **verified resolvable** in bundled v2.944 font (Codex checked GSUB ligatures directly); token to be added |
| OFF row | `thermometer` | ✅ **verified resolvable** (bare `thermometer`, distinct from the existing `thermometer_gain`); token to be added |
| Monitor-return (unchanged) | `MonitorMode` | ✅ registered |

**Icon law:** both `fire_check` and `thermometer` are **owner-chosen** and confirmed present in the font, so no substitution question arises. New icon tokens follow the `val <Name> = DinghyIcon(IconRef.Ligature("…"), alternate = "…")` pattern and must be added to the `all` list so `verify_ligatures.py` gates them.

**Token cleanup:**
- **Remove if unreferenced after this change:** `FootPreheat` (`chair_fireplace`), `FootCooldown` (`mode_heat_off`), `TempCooldown` (`mode_heat_off`) — grep the repo before deleting.
- **KEEP:** `TempPresets` (`thermostat_auto`) — still used by `HeatPresetsScreen.kt:126`; only its use in the Temperature Adjust foot row is removed.

---

## Architecture & isolation

- **`buildHeatersRows(...)` — pure, Compose-free.** Owns ordering (OFF top, spool 2nd, presets sorted) and the scope→action mapping. Each returned `HeatersRow` carries its display data + a sealed `HeatAction` describing what to dispatch.
- **`HeatersList(...)` — the Field composable.** Renders rows via the existing `ListBlock`/list-row classes (reuse `PresetListRow`/equivalent; the OFF and spool rows are the same row class with different icon/label/tint). Owns the Back foot button. Dispatches each row's `HeatAction`, then calls `onApplied()` to return the Field to its normal list.
- **Call sites** (Home, Extrude, Temp) own only: the `mode_heat` entry button, the Field-mode toggle, the `HeatScope`, the `extruderObject` (Extrude = `vm.activeHeater`), the `Capabilities`, and the `LoadedSpoolTemps?` read. Everything else is shared.
- **Field-mode state:** matches the existing screens, which use plain `remember` (`TemperatureScreen.kt:466`, `ExtrudeScreen.kt:282`) — **not** `rememberSaveable`. A rotation mid-takeover resets the Field to its normal list (acceptable: reopen with one tap). Don't claim rotation survival; if it's ever wanted, back the mode with an enum `Saver`.
- This deletes more than it adds: `selectPreheatPath`/`PreheatPath` (+ `Preheat.kt` and `PreheatTest.kt`), the Extrude chip block, the Home `PresetSelector` scrim, and the second Temp foot row all go away in favor of one component. `PreheatPath` is still wired through `PrintStatusScreen.kt:171` — update that call site when removing it.

---

## Testing

- **Ordering** (`buildHeatersRows`): OFF always index 0; spool (when present) index 1; presets follow in temp-ascending order; spool absent when no temps; spool absent when no spool.
- **Scope → command mapping:**
  - `Full` OFF → `TURN_OFF_HEATERS`; `ExtruderOnly` OFF → set the **active** heater to 0.
  - `Full` spool → extruder **and** bed (each gated on presence + capability); `ExtruderOnly` spool → active extruder only.
  - `Full` preset → capability-filtered map; `ExtruderOnly` preset → active-extruder-only map (and no-op/omitted when the preset has no extruder temp).
- **Active-heater targeting:** with `extruderObject = "extruder1"`, `ExtruderOnly` OFF/spool/preset actions target `extruder1`, never the primary `extruder`.
- **Capability filtering (BLOCKER regression test):** a `Full` preset containing `heater_bed` on a printer whose `Capabilities` lacks `heater_bed` produces an action with the bed setpoint **dropped** (no invalid gcode).
- **Spool mapping:** nozzle/bed pulled from `settingsExtruderTemp`/`settingsBedTemp`; **bed-only** spool still yields a row under `Full` (and is omitted under `ExtruderOnly`); label fallback chain (`name → material → "Loaded filament"`); color tint derived from `colorSwatches`.
- **Dispatch keys:** each row's action carries a distinct key (`heat_off`, `heat_spool_<heater>`, `preset_<id>`); OFF rides the guaranteed-delivery path (asserted at the call-site/integration level, mirroring the existing `onHeaterOff` test if one exists).
- **Ligature gate:** `verify_ligatures.py` passes with `thermometer` and `fire_check` tokens added to `DinghyIcons.all`.

---

## Open risks

1. **Extrude takeover is a heavier swap** (replaces the whole filament hub Field, not a list) — accepted; consistent with the established mode-swap pattern.

_(Resolved during review: `fire_check` and `thermometer` are confirmed present in the bundled font; active-heater targeting, capability filtering, and dispatch-key/OFF semantics — via dedicated non-colliding keys — are specified above.)_
