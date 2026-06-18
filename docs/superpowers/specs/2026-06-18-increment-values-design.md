# Per-Printer Increment Values — Design Spec

**Date:** 2026-06-18
**Status:** Approved (brainstorming complete, Codex spec-review applied) — ready for implementation plan
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
| Fine-Tune · Print Speed | `SPEED` | **exactly 3** | `1, 5, 10` | % | `Speed` |
| Fine-Tune · Flow Rate | `FLOW` | exactly 3 | `1, 5, 10` | % | `OutputCircle` |
| Fine-Tune · Pressure Advance | `PRESSURE_ADVANCE` | exactly 3 | `0.001, 0.005, 0.01` | — | `PressureAdvance` |
| Fine-Tune · Smooth Time | `SMOOTH_TIME` | exactly 3 | `0.01, 0.02, 0.05` | s | `SmoothTime` |
| Fine-Tune · Part Fan | `PART_FAN` | exactly 3 | `1, 5, 10` | % | `FanMode` |
| Fine-Tune · Max Velocity | `MAX_VELOCITY` | exactly 3 | `10, 50, 100` | mm/s | `MaxVelocity` |
| Fine-Tune · Max Accel | `MAX_ACCEL` | exactly 3 | `100, 500, 1000` | mm/s² | `MaxAccel` |
| Fine-Tune · Min Cruise | `MIN_CRUISE` | exactly 3 | `1, 5, 10` | % | `MinCruise` |
| Fine-Tune · Square Corner Vel | `SCV` | exactly 3 | `0.1, 0.5, 1` | mm/s | `SquareCornerVelocity` |
| Fine-Tune · Retract Length | `RETRACT_LENGTH` | exactly 3 | `0.1, 0.5, 1` | mm | `OutputCircle` |
| Fine-Tune · Retract Speed | `RETRACT_SPEED` | exactly 3 | `1, 5, 10` | mm/s | `MaxAccel` |
| Fine-Tune · Unretract Extra | `UNRETRACT_EXTRA_LENGTH` | exactly 3 | `0.1, 0.5, 1` | mm | `InputCircle` |
| Fine-Tune · Unretract Speed | `UNRETRACT_SPEED` | exactly 3 | `1, 5, 10` | mm/s | `MaxVelocity` |
| Move · Microstep | `move_microstep` | **unlimited (min 1)** | `0.01, 0.025, 0.1, 0.25, 1, 2.5, 10` | mm | `FineTune` |
| Print · Babystep | `babystep` | unlimited (min 1) | `0.02, 0.05, 0.1, 0.15, 0.2` | mm | ⚠ `question_mark` (UAT) |
| Calibrate · Probe Z Test | `probe_testz` | unlimited (min 1) | `0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 5, 10` | mm | `RoutineProbeCalibrate` |

The 13 Fine-Tune keys are the existing `FineTuneTuner` enum names — the registry maps to the
live param descriptors by enum, it does not duplicate their metadata (see Consumption).

**Babystep is setting-only this phase.** There is currently **no live babystep selector** in
the UI (`CommandRegistry.babystepZ` / `PrinterCommands.BABYSTEP_STEPS` exist but nothing
dispatches them). This feature ships babystep's editable row, storage, seeding, and default
list, but **does not** build the Print Status selector or refactor the command-layer
consumption. The live selector + dynamic-step command API land in a later feature. Until then,
`BABYSTEP_STEPS` stays as-is and the registry's babystep default must mirror it.

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
  **canonical comma-joined string** (spaces already stripped, tokens preserved exactly as the
  user entered them so decimal precision like `0.001` is never reformatted away).
- `seedIfEmpty(profileId, defaults)` invoked from `AppContainer.saveProfile()` **only when
  the profile is new** (`isNew`), matching the Heat Presets seed call site. New printers get
  concrete jiib-default rows.
- **Existing printers are NOT retroactively seeded.** The read path is fallback-safe:
  `stored[key] ?: jiibDefault(key)`. So every control works immediately for existing printers
  (it shows the jiib default), and a control's stored entry is written the first time the user
  saves an edit for it. This also covers any control added in a later release.
- Reactivity is scoped to the active printer via `flatMapLatest` on
  `container.activeProfileId` (same pattern as heater-limits / `TraceStylePrefs`).

### Control registry (single source of truth for the SETTINGS UI)
A single `IncrementControls.ALL: List<IncrementControlSpec>` drives the settings list and the
seed defaults. It holds only what the settings UI needs — it does **not** try to be the source
of truth for control-specific runtime metadata (tuner, unit, decimals, default-step index,
fw-retraction gating, etc.). Those stay on each control's existing descriptor.

```kotlin
data class IncrementControlSpec(
    val key: String,            // stable persistence key (FineTuneTuner name, or "move_microstep" …)
    val group: String,          // "Fine-Tune", "Move", "Print", "Calibrate"
    val controlTitle: String?,  // "Print Speed" … ; null when its group has a single selector
    val icon: DinghyIcon,       // the control's existing Focus-header icon (never picked anew)
    val maxCount: Int?,         // 3 for Fine-Tune; null = unlimited
    val defaultValues: List<Double>,
)
```
- `minCount` is always 1.
- A small helper resolves a control's **active list**: `parse(stored[key]) ?: defaultValues`.

### Consumption — replace every hardcoded list (apply prefs onto existing descriptors)
Each selector derives its active list from `IncrementListPrefs` (jiib fallback) **without**
restructuring the consuming code's existing data model:

- **Fine-Tune** — `FineTuneScreen` reads `ALL_FINE_TUNE_PARAMS` in its live batcher and
  throughout stateless content. Derive an **active param list** once at the top:
  `activeParams = ALL_FINE_TUNE_PARAMS.map { it.copy(steps = activeList(it.tuner.name) ?: it.steps) }`
  and thread `activeParams` through `FineTuneContent`. Count stays 3, so each param's
  `defaultStepIndex` (0–2) stays valid.
- **Move Microstep** — `MoveScreen` microstep `steps` come from the active list.
- **Probe Z Test** — `ProbeCalibrateScreen.TESTZ_STEPS` comes from the active list (both the
  selected-step state and the stateless index/enabled usage).
- **Babystep** — consumption deferred (setting-only, see Scope). `BABYSTEP_STEPS` unchanged.

### Selected-step rebasing (REQUIRED whenever a control's active list changes)
Controls track the selected step differently, so a single "clamp the index" rule is not
enough. On any change to a control's active list (including the first composition after an
edit), re-resolve the selection:
- **Index-tracked controls** (Move indexes `steps[stepIndex]`): clamp `stepIndex` into
  `0..lastIndex`.
- **Value-tracked controls** (Probe stores the selected step value; Fine-Tune's
  `IncrementPicker` requires `activeStep` to be a member of `steps`): if the current value is
  not present in the new list, snap to the nearest present value; if none resolvable, fall
  back to the first value. For Fine-Tune (fixed count 3) this is equivalently "keep the same
  index position."

## UI

### "Increment Values" door (Printer Settings)
- New list row **"Increment Values"** in `PrinterSettingsScreen`, adjacent to **"Heat Presets"**.
- Opens a Field list with **4 rows**:
  1. **Fine-Tune** → opens the Fine-Tune submenu (below).
  2. **Microstep** → edit Focus.
  3. **Babystep** → edit Focus.
  4. **Probe Z Test** → edit Focus. (Title matches the existing in-app label — confirm at build.)
- Each row: **icon + title (marquee-scroll on overflow) + `/`-joined current-values summary**
  (e.g. `1 / 5 / 10`), reusing the compact summary convention from Heat Presets. Summary
  renders the stored tokens as-is (no fixed-decimal reformat).
- **No add / no delete.**

### Fine-Tune submenu (Field list)
- 13 rows, one per Fine-Tune param, each: icon (the param's own existing icon) + title
  (marquee on overflow) + `/`-joined summary. Tapping a row opens its edit Focus.

### Edit Focus (single screen — no wizard)
- `FocusFrame`; header = the control's icon + title (per the mandatory-header law).
- One text field bound to a **filtered system keyboard**:
  - Allowed characters only: `[0-9 . , space]`. Any other character is rejected as typed.
  - Sanctioned keyboard exception (Settings screen), consistent with the "no alphanumeric
    keyboard except Settings/Save-name" law.
- **Save validation** (block save + inline message on failure):
  - Strip spaces.
  - Split on `,`; reject empty tokens (trailing comma, `,,`).
  - Each token must parse as a number **> 0** (and have at most one decimal point).
  - At least 1 value; for Fine-Tune controls exactly 3 values.
  - Stored **in entered order** (no auto-sort), tokens preserved verbatim post-space-strip.
- Foot bar: **Save = green** (the expected action), **Back = accent**.

## Icons (per the never-pick-an-icon law)
- **Fine-Tune (13):** reuse each param's existing, owner-chosen icon. ✅
- **Microstep:** `DinghyIcons.FineTune` (existing). ✅
- **Probe Z Test:** `DinghyIcons.RoutineProbeCalibrate` (existing). ✅
- **Babystep:** no existing Focus-header icon (no live selector yet) → use **`question_mark`**
  placeholder and **flag for owner UAT review**. The only icon-review item this phase.

## Testing
- `IncrementListPrefs` round-trip: seed-if-empty on new profile, read fallback to default for
  absent key, edit-overwrite, per-`profileId` isolation (no cross-printer bleed — cf. the
  TraceStyle scoping lesson).
- Validation unit tests: allowed-char filter, space-strip, empty-token rejection, `> 0`
  rejection, multiple-decimal rejection, Fine-Tune exactly-3 rule, unlimited min-1 rule,
  entered-order + verbatim-token preservation (`0.001` survives round-trip).
- Consumption: Fine-Tune / Microstep / Probe read from prefs and reflect an edited list.
- Rebasing: index clamp on shrunk Move list; value-snap on Probe when the selected value is
  removed; Fine-Tune index-position preserved after a 3-value edit.
- Drift guard: every `IncrementControls.ALL` entry has a default and (except babystep, which
  is intentionally deferred) is consumed by a real control; no orphan keys.

## Open UAT items
- **Babystep icon** — `question_mark` placeholder; owner to choose the real glyph when the
  live selector is built.
- Confirm the Probe Z Test row title matches its existing in-app label.
