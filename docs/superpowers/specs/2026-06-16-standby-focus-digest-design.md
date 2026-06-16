# Standby Focus Digest — Design

**Date:** 2026-06-16
**Status:** Approved (owner, 2026-06-16)
**Topic:** Clean up the text in the Focus area of the home/PrintStatus screen — replace the
current temp-glance block with a compact printer-state digest, for non-printing states.

## Problem

The home Focus glance block (`HomeFocus` in `PrintStatusFocus.kt`) currently renders a tight
top-start column of *current temperatures*: `Nozzle <temp>`, `Bed <temp>`, an arbitrary
`temperature_sensor` line (e.g. `chamber_air`/`SKR`), and a `Spool <g> g` line. Problems the owner
called out:

- Every temp carries a jittery `.x` decimal (`25.2`, `40.0`) — reads like a noisy readout, not a glance.
- Values are ragged — labels differ in width so the numbers don't form a clean column.
- Arbitrary `temperature_sensor` keys (`chamber_air`, `SKR`) show as raw config keys.
- It only shows current temps — no motor/homing status, no setpoints.

## Goal

For **non-printing** printer states, the Focus glance becomes a clean, two-column **printer-state
digest**: labels start-aligned, values end-aligned, integer temps, with motor and homing status.

## Scope

- **Applies to non-printing states only:** Standby, Complete, Cancelled, Error, Shutdown.
- **Printing & Paused keep today's glance block unchanged.** Their proper Focus treatment is a
  separate future design (out of scope here). `HomeFocus` gains exactly one branch:
  `isPrinting (Printing||Paused) → existing glance; else → new digest`.
- The FocusFrame header (`name · STATE` + docked e-stop) and the bottom-end brand watermark are
  **unchanged**.
- Arbitrary `temperature_sensor`s (`chamber_air`, `SKR`, …) are **dropped from the home digest**.
  They remain available on the Temperature screen.

## The digest

A vertical list of rows. Each row is full-width: **label start-aligned, value end-aligned** (right
edges flush — the owner's "values aligned end"). Order, top → bottom:

1. **Heaters**
   - If **no** heater is on (`target == 0` for every heater): a single row `Heaters → OFF`.
   - If **any** heater is on: **one row per active heater** (no "Heaters" label row), e.g.
     `Extruder → 150/220`, `Bed → 55/60`.
   - "On" = `target > 0`. A hot-but-cooling heater (`target == 0`, temp still high) reads
     `Heaters OFF`. This is a status display, not a safety annunciator — intentional, no special case.
2. **Motors** — `Motors → ON` / `Motors → OFF` (see Data plumbing for the motion-stepper rule).
3. **Homed** — `Homed → XYZ` (only the homed axes, uppercased, in X Y Z order) / `Homed → NONE`.
4. **Spool** — `Spool → 848g` / `Spool → N/A`. **Only rendered when Spoolman is configured**
   (`spoolmanPresent`); when Spoolman is absent the row is omitted entirely. `N/A` covers
   configured-but-no-spool-loaded.

### Example states

```
 cold standby            preheating
─────────────────       ─────────────────
Heaters       OFF       Extruder  150/220
Motors        OFF       Bed         55/60
Homed        NONE       Motors         ON
Spool        848g       Homed         XYZ
                        Spool        848g
```

### Formatting rules

- **Temps are integers**, rounded (151/220, not 150.6/220.0). No `°` symbol.
- **Heater current/setpoint** format: `{round(current)}/{round(target)}`.
- **Spool weight**: `{round(weight)}g` — no space before `g`.
- **Heater labels — prettify the Klipper name, never invent a synonym:**
  - Strip a leading `heater_generic ` prefix, else a leading `heater_` prefix.
  - Capitalize the first letter.
  - Examples: `extruder` → **Extruder** (NOT "Nozzle"), `heater_bed` → **Bed**,
    `heater_generic chamber` → **Chamber**. Extra extruders (`extruder1`) → **Extruder1**
    (raw name, capitalized — no fabricated numbering).

## Data plumbing

| Datum | Source | Status |
|---|---|---|
| Heater current + setpoint | `HeaterState.temperature` / `.target` (per heater in `PrinterState.heaters`) | exists |
| Homed axes | `PrinterState.homedAxes` (`toolhead.homed_axes`, e.g. `"xyz"`/`""`) | exists |
| Spool weight + Spoolman presence | `ActiveSpoolCardState` / `spoolmanPresent` (already passed to `HomeFocus`) | exists |
| **Motors enabled** | **NEW** — see below | net-new |

### Motors — net-new `motorsEnabled`

- Add Klipper's **`stepper_enable`** object to the subscribe set (`V1_SUBSCRIBE_CORE` in
  `DeriveCapabilities.kt`, present-gated by the existing intersect loop so a printer that doesn't
  define it simply isn't subscribed).
- `stepper_enable` reports `{ "steppers": { "stepper_x": true, "stepper_z1": false, "extruder": true, … } }`.
- In `PrinterStateReducer`, parse `steppers` and derive **motion-stepper** enablement:
  - **Exclude extruder steppers** — any key matching `extruder` / `extruder1` / `extruder2` … (regex
    `^extruder\d*$`). Everything else is a motion stepper (`stepper_x/y/z`, extra `stepper_z1/z2/z3`,
    `dual_carriage`, CoreXY naming, etc.) — flexible to any motor topology, no hardcoded axis set.
  - `motorsEnabled = (any motion stepper is enabled)`.
- Add **`PrinterState.motorsEnabled: Boolean?`** — `true`/`false` when reported; **`null` when
  `stepper_enable` is absent/unreported** → the Motors row is **hidden** (don't show a guessed state).

## Type & layout

- **Labels in Geist, values in Mono.** Add one new type role to `DinghyType`:
  - **`focusHeroLabel`** = `TextRole(TypeRole.Ui, baseSp = 40f, weight = SemiBold, maxSp = 40f, minSp = 15f)`
    — a Geist (`Ui`) mirror of `focusHero`'s shrink-to-fit envelope so a label sits at the same height
    as its Mono value.
  - Values keep **`focusHero`** (`TypeRole.Data` → Geist Mono, tabular digits).
  - This is conformance-clean (`FontConformanceTest` requires role-based text; no inline
    `fontFamily`/`fontSize`).
- **Uniform size across the whole block.** The digest can run up to ~5–6 rows. Rather than let each
  line shrink independently (which would make the column uneven), the block is sized so all rows share
  one size, fitting the tallest case within the Focus. The exact mechanism (measure-then-size, or a
  fixed digest size derived from the row count / `uDp`) is a plan-level decision; the contract is
  **all rows render at one uniform size and the column never overflows the Focus**.
- Each row: full-width `Row(horizontalArrangement = Arrangement.SpaceBetween)` — label at start,
  value at end.

## Color

- **Heater values are colored to match their Temperature-graph trace:**
  - If the user set a custom trace color for that heater → use it
    (`TraceStylePrefs.traceColors(profileId)[heaterName]`, per-printer, async).
  - Else → `seriesColor(localIndex)` from the data **color pool, accent-first**
    (`seriesColor(0) == accent` in every palette mode). `localIndex` = the heater's slot in a **stable
    heater order** (`extruder` = 0 → accent, `heater_bed` = 1, generic heaters after, deterministic),
    so each heater keeps a fixed color whether or not it's currently active.
  - No legend-index coupling to the graph — the digest uses its own accent-first order. In practice
    Extruder/Bed land on the same colors in both surfaces; a user trace-color override is honored.
- **Motors / Homed / Spool values are neutral** (`t.text`). Only heater values carry color.

## Affected code

- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusFocus.kt` — `HomeFocus`: add the
  printing/non-printing branch; new digest composable + row composables; retire the
  glance-sensor/old-spool lines from the non-printing path. `GlanceRow`/`glanceLabel` stay for the
  printing path.
- `app/src/main/java/works/mees/dinghy/theme/DinghyType.kt` — add `focusHeroLabel` role (and to
  `all`).
- `app/src/main/java/works/mees/dinghy/state/PrinterState.kt` — add `motorsEnabled: Boolean?`.
- `app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt` — parse `stepper_enable.steppers`
  → `motorsEnabled`.
- `app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt` — add `stepper_enable` to
  `V1_SUBSCRIBE_CORE`.
- PrintStatus holder/screen — thread `profileId` + `TraceStylePrefs` trace colors into `HomeFocus`
  for heater coloring (plan to confirm exact wiring; PrintStatus already has the active profile).
- Heater label prettify + integer/format helpers — pure, unit-testable.

## Testing

- **Pure helpers (unit):** heater-label prettify (`extruder`→Extruder, `heater_bed`→Bed,
  `heater_generic chamber`→Chamber); heater value `current/target` integer format; homed-axes →
  `XYZ`/`NONE`; spool `848g`/`N/A`; motion-stepper filter (`extruder*` excluded, `stepper_*`/
  `dual_carriage` included) → `motorsEnabled`.
- **Reducer:** `stepper_enable` diff → `motorsEnabled`; absent object → `null`.
- **Compose previews:** cold standby, preheating (multi-heater), homed-partial, no-Spoolman
  (row absent), Spoolman-empty (`N/A`), motors-absent (row hidden).
- **On-device (flox + moto):** owner eyeball in portrait + landscape, both palette/theme modes.

## Out of scope

- Printing/Paused Focus treatment (separate future design).
- Print progress / % in the Focus.
- Any change to the Temperature screen's arbitrary-sensor handling.
- Hot-idle heater annunciation (deliberately not a feature).
