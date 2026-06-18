# Per-Printer Increment Values — Design Spec

**Date:** 2026-06-18
**Status:** Approved (brainstorming complete) — ready for implementation plan
**Author:** Matthew + Claude

## Summary

Make the step/increment value lists used by the app's discrete step **selectors**
configurable **per printer**, instead of hardcoded. The values currently baked into code
become the **default "jiib" presets**, seeded into each printer when it is added. Users edit
the lists from a new **"Increment Values"** door in Printer Settings, mirroring the existing
**Heat Presets** feature — but simpler (no multi-step wizard, no add/delete; edit only).

## Scope

**In scope — Group A discrete multi-step selectors only (16 controls):**

| Control | Key | Count policy | Default values (jiib) | Unit | Icon |
|---|---|---|---|---|---|
| Fine-Tune · Print Speed | `ft_speed` | **exactly 3** | `1, 5, 10` | % | `Speed` |
| Fine-Tune · Flow Rate | `ft_flow` | exactly 3 | `1, 5, 10` | % | `OutputCircle` |
| Fine-Tune · Pressure Advance | `ft_pressure_advance` | exactly 3 | `0.001, 0.005, 0.01` | — | `PressureAdvance` |
| Fine-Tune · Smooth Time | `ft_smooth_time` | exactly 3 | `0.01, 0.02, 0.05` | s | `SmoothTime` |
| Fine-Tune · Part Fan | `ft_part_fan` | exactly 3 | `1, 5, 10` | % | `FanMode` |
| Fine-Tune · Max Velocity | `ft_max_velocity` | exactly 3 | `10, 50, 100` | mm/s | `MaxVelocity` |
| Fine-Tune · Max Accel | `ft_max_accel` | exactly 3 | `100, 500, 1000` | mm/s² | `MaxAccel` |
| Fine-Tune · Min Cruise | `ft_min_cruise` | exactly 3 | `1, 5, 10` | % | `MinCruise` |
| Fine-Tune · Square Corner Vel | `ft_scv` | exactly 3 | `0.1, 0.5, 1` | mm/s | `SquareCornerVelocity` |
| Fine-Tune · Retract Length | `ft_retract_length` | exactly 3 | `0.1, 0.5, 1` | mm | `OutputCircle` |
| Fine-Tune · Retract Speed | `ft_retract_speed` | exactly 3 | `1, 5, 10` | mm/s | `MaxAccel` |
| Fine-Tune · Unretract Extra | `ft_unretract_extra_length` | exactly 3 | `0.1, 0.5, 1` | mm | `InputCircle` |
| Fine-Tune · Unretract Speed | `ft_unretract_speed` | exactly 3 | `1, 5, 10` | mm/s | `MaxVelocity` |
| Move · Microstep | `move_microstep` | **unlimited (min 1)** | `0.01, 0.025, 0.1, 0.25, 1, 2.5, 10` | mm | ⚠ see Icons |
| Print · Babystep | `babystep` | unlimited (min 1) | `0.02, 0.05, 0.1, 0.15, 0.2` | mm | ⚠ see Icons |
| Calibrate · Probe Z Test | `probe_testz` | unlimited (min 1) | `0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 5, 10` | mm | ⚠ see Icons |

**Out of scope:** single-value scrubber/stepper steps (Move XY scrubber 1.0 mm, Move Z fine
0.1 / full 1.0, Temperature fine ±1 °C, Outputs PWM 1%/5%, Delay 1 s). These expose one
hardcoded step, not a *list*, and stay as-is. `FineTuneShared` epsilon/convergence constants
(`SPEED_STEP` etc.) stay **decoupled** from the configurable lists — they are internal float
tolerances, not user-facing step choices.

## Architecture

Mirror the Heat Presets feature wherever possible.

### Data & persistence
- New `IncrementListPrefs` DataStore class (sibling of `HeatPresetPrefs`), per-printer,
  keyed `increments_<profileId>`. Stores `Map<controlKey, String>` where the value is the
  comma-joined value string (canonical, spaces already stripped).
- `seedIfEmpty(profileId, defaults)` invoked from `AppContainer.saveProfile()` on
  printer-add (same call site / pattern as `heatPresetPrefs.seedIfEmpty`). Seeds the jiib
  defaults so every printer has concrete rows.
- **Read path is fallback-safe:** `stored[key] ?: jiibDefault(key)`. This covers
  already-existing printers (seeded on first access) and any control added in a later
  release that predates a printer's seeding.
- Reactivity is scoped to the active printer via `flatMapLatest` on
  `container.activeProfileId` (same pattern as heater-limits / `TraceStylePrefs`).

### Control registry (single source of truth)
A single `IncrementControls.ALL: List<IncrementControlSpec>` drives both the settings UI and
the consuming controls.

```kotlin
data class IncrementControlSpec(
    val key: String,            // stable persistence key, e.g. "ft_speed"
    val group: String,          // "Fine-Tune", "Move", "Print", "Calibrate"
    val controlTitle: String?,  // "Print Speed" … ; null when its group has a single selector
    val icon: DinghyIcon,       // the control's existing Focus-header icon (never picked anew)
    val maxCount: Int?,         // 3 for Fine-Tune; null = unlimited
    val defaultValues: List<Double>,
)
```
- `minCount` is always 1.
- `displayTitle` = `controlTitle` when present, else the `group` label. (Fine-Tune is the only
  multi-selector group; its rows live in a submenu so the `group` prefix is not repeated on
  every row — the submenu header carries the "Fine-Tune" context.)

### Consumption — replace every hardcoded list
Each selector reads its list from `IncrementListPrefs` (with jiib fallback) instead of a
literal:
- `FineTuneParams` — the 13 `steps` lists. Count stays 3, so each param's `defaultStepIndex`
  (0–2) remains valid; no clamping needed.
- `ProbeCalibrateScreen.TESTZ_STEPS`, `MoveScreen` microstep `steps`,
  `PrinterCommands.BABYSTEP_STEPS` — the three unlimited lists.
- **Selected-index clamping (unlimited only):** when a user shrinks a list below a
  remembered/selected index, clamp into range, falling back to the first value.

## UI

### "Increment Values" door (Printer Settings)
- New list row **"Increment Values"** in `PrinterSettingsScreen`, adjacent to **"Heat Presets"**.
- Opens a Field list with **4 rows**:
  1. **Fine-Tune** → opens the Fine-Tune submenu (below).
  2. **Microstep** → edit Focus.
  3. **Babystep** → edit Focus.
  4. **Probe Z Test** → edit Focus.
- Each row: **icon + title (marquee-scroll on overflow) + `/`-joined current-values summary**
  (e.g. `1 / 5 / 10`), reusing the compact summary convention from Heat Presets.
- **No add / no delete.**

### Fine-Tune submenu (Field list)
- 13 rows, one per Fine-Tune param, each: icon (the param's own existing icon) + title
  (marquee on overflow) + `/`-joined summary. Tapping a row opens its edit Focus.

### Edit Focus (single screen — no wizard)
- `FocusFrame`; header = the control's icon + title (per the mandatory-header law).
- One text field bound to a **filtered system keyboard**:
  - Allowed characters only: `[0-9 . , space]`. Any other character is rejected as typed.
  - This field is a sanctioned keyboard exception (Settings screen), consistent with the
    "no alphanumeric keyboard except Settings/Save-name" law.
- **Save validation** (block save + inline message on failure):
  - Strip spaces.
  - Split on `,`; reject empty tokens (e.g. trailing comma, `,,`).
  - Each token must parse as a number **> 0**.
  - At least 1 value; for Fine-Tune controls exactly 3 values.
  - Stored **in entered order** (no auto-sort).
- Foot bar: **Save = green** (the expected action), **Back = accent**.

## Icons (per the never-pick-an-icon law)
- **Fine-Tune (13):** reuse each param's existing, already owner-chosen icon. ✅
- **Microstep / Babystep / Probe Z Test (3):** reuse each control's existing Focus-header
  icon. If any of these three has no clear existing symbol, use **`question_mark`** and
  **flag it for owner UAT review** — do not invent a glyph. These three are the expected
  UAT icon-review items.

## Testing
- `IncrementListPrefs` round-trip: seed-if-empty, read fallback to default for absent key,
  edit-overwrite, per-`profileId` isolation (no cross-printer bleed — cf. the TraceStyle
  scoping lesson).
- Validation unit tests: allowed-char filter, space-strip, empty-token rejection, `> 0`
  rejection, Fine-Tune exactly-3 rule, unlimited min-1 rule, entered-order preservation.
- Consumption: each of the 16 selectors reads from prefs and reflects an edited list;
  selected-index clamp on shrunk unlimited lists.
- Drift guard: every `IncrementControls.ALL` entry has a default and is consumed by a real
  control (no orphan keys).

## Open UAT items
- Confirm the existing Focus icons for Microstep, Babystep, Probe Z Test (or accept
  `question_mark` placeholders flagged during the build).
- Confirm row titles exactly match the existing in-app labels for those three screens.
