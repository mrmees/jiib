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
- **Error / Shutdown / stale (R-CDX-5):** the home shell renders the digest during Klippy
  `Error`/`Shutdown` (both are in-scope non-printing states). The digest shows the **last-known
  `PrinterState`** values with **no per-row dimming or fault styling** — consistent with how the rest
  of the home shell presents last-known state. Connection-loss / stale-value handling is a pre-existing
  shell concern (the planned startup/stale pass) and is **explicitly inherited, not solved here**.
  `motorsEnabled` reset-on-(dis)connect, if any, follows whatever the store already does for other
  control-plane fields — the plan confirms; no new clear-logic is introduced by this spec.

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
   (`spoolmanPresent`); when Spoolman is absent the row is omitted entirely.
   - **Value rule across `ActiveSpoolCardState` variants (R-CDX-4):** show `{round(weight)}g` **only**
     for `Loaded` with a non-null numeric `remainingWeight`. Every other case — `Loaded` with null
     weight, `NoActive`, `Loading`, `Disconnected` — shows **`N/A`**. (A status display; we don't show
     a spinner or stale guess in one digest line.)
   - **Distinct from the Field Spool row (R-CDX-4):** `HomeField` already renders a data-rich
     `SpoolStatusRow` (`PrintStatusField.kt:141`) — a *navigable* ListRow showing filament **identity**
     (`name / material / vendor`, icon tinted to the filament color) that taps through to the Spool
     screen. The Focus digest row is the opposite job: a non-interactive **remaining-weight glance**.
     Both intentionally coexist (identity+nav in the Field; weight glance in the Focus) — this is per
     the owner's row list, not a duplication to collapse.

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

### Canonical heater order (R-CDX-2)

`PrintStatusHolder` and `TemperatureHolder` currently order heaters differently (capability order vs.
alphabetical). The digest defines **one** canonical order, used for **both the row order and the
color `localIndex`** so a heater's color is stable:

1. `extruder` (always first → `localIndex 0` → accent)
2. `heater_bed`
3. all remaining heaters (`extruder1`, `extruder2`, `heater_generic *`, …) sorted by key.

A pure helper produces this ordered list from `PrinterState.heaters`; both the active-heater rows and
the color assignment read it.

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
- **Merge semantics (R-CDX-1):** the reducer uses retain-on-absent merge — a partial diff that does
  *not* touch `stepper_enable` must keep the prior `motorsEnabled`. So only `copy(motorsEnabled = …)`
  when the diff actually contains a `stepper_enable` object; the field starts `null` and stays `null`
  until first reported. `null` therefore unambiguously means "never reported", never "reported false".
- **Control-plane publish (R-CDX-1):** motor enable/disable is a user-visible control event. Add
  `stepper_enable` to the immediate-publish trigger in `PrinterStateStore` (the set that today fast-
  publishes `print_stats` / `webhooks` / `toolhead.homed_axes`, ~`PrinterStateStore.kt:437`) so the
  Motors row flips promptly on `M84` / re-enable rather than waiting for the next coalesced tick.

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

### Wiring the trace-color override (R-CDX-3)

`PrintStatusScreen` collects neither `traceColors` nor the active `profileId` today; both inputs exist
but must be threaded in:

- `TraceStylePrefs.traceColors(profileId)` exists (`TraceStylePrefs.kt:64`); `AppContainer` exposes
  `activeProfileId` (`AppContainer.kt`). Mirror `TemperatureHolder`'s pattern
  (`flatMapLatest` on `activeProfileId` → `traceStylePrefs.traceColors(pid)`,
  `TemperatureHolder.kt:~179`): have `PrintStatusHolder` expose the per-heater override colors as a
  `StateFlow`.
- **Compose stability:** pass the resolved colors into `HomeFocus` as a **stable** type — an
  `ImmutableMap<String, Color>` (kotlinx-collections-immutable) or a small `@Immutable` holder — so the
  digest composable stays skippable. Do NOT pass a raw `Map<String, Int>`/`Map<String, Color>` (unstable
  param → defeats skipping).
- Override is per-printer and async; the accent-first `seriesColor` fallback applies until/unless an
  override is present for that heater key.

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
- `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt` — add `stepper_enable` to the
  immediate control-plane publish trigger (R-CDX-1).
- `PrintStatusHolder` / `PrintStatusScreen` — expose per-heater override colors as a `StateFlow`
  (`flatMapLatest` on `activeProfileId` → `TraceStylePrefs.traceColors`) and thread them, as a
  **stable** `ImmutableMap<String, Color>`/`@Immutable` holder, into `HomeFocus` (R-CDX-3).
- Heater helpers — pure, unit-testable: label prettify, `current/target` integer format, the canonical
  heater-order list (R-CDX-2), and the motion-stepper → `motorsEnabled` filter.

## Testing

- **Pure helpers (unit):** heater-label prettify (`extruder`→Extruder, `heater_bed`→Bed,
  `heater_generic chamber`→Chamber); canonical heater order (R-CDX-2 — extruder, bed, then key-sorted);
  heater value `current/target` integer format; homed-axes → `XYZ`/`NONE`; spool weight rule across all
  `ActiveSpoolCardState` variants → `848g`/`N/A` (R-CDX-4); motion-stepper filter (`extruder*` excluded,
  `stepper_*`/`dual_carriage` included) → `motorsEnabled`.
- **Reducer (R-CDX-1, R-CDX-7):** `stepper_enable` diff with a `steppers` map → `motorsEnabled`;
  partial diff without `stepper_enable` retains prior value; object never seen → `null`. Add a
  subscribe-payload fixture carrying the `steppers` map (object-list fixtures already include
  `stepper_enable`, so `deriveSubscribeSet` picks it up once it's in `V1_SUBSCRIBE_CORE` — add a
  derive test asserting that).
- **Type (R-CDX-6):** a `DinghyTypeTest` assertion that `focusHeroLabel` is `Ui`/Geist with the
  expected weight + 40→15 size envelope (the `FontConformanceTest` only proves role-usage, not the
  role's own values).
- **Compose previews:** cold standby, preheating (multi-heater), homed-partial, no-Spoolman
  (row absent), Spoolman-empty (`N/A`), motors-absent (row hidden), Error/Shutdown (last-known values).
- **On-device (flox + moto):** owner eyeball in portrait + landscape, both palette/theme modes.

## Out of scope

- Printing/Paused Focus treatment (separate future design).
- Print progress / % in the Focus.
- Any change to the Temperature screen's arbitrary-sensor handling.
- Hot-idle heater annunciation (deliberately not a feature).
