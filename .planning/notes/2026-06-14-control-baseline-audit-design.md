# Control / Button Baseline Audit — design spec

**Date:** 2026-06-14
**Status:** design approved (owner, 2026-06-14) — pending master-list review before execution
**Predecessors:** the Phase-2 region-framing work (`RegisteredRegion` centralized the region FRAME);
this is the **contents-side successor** — centralize the CONTROLS inside those regions.
**Supersedes / reconciles:** R13 (named fixed-dp spacing set) — see §6.

---

## 1. Objective

Two intertwined goals, system-wide (every interactive control wherever it lives — foot bar, field,
or inside the Focus body):

1. **Collapse individualized padding/styling to defined component CLASSES**, one baseline per class
   for how a control is drawn / aligned / sized / shaded / colored / labeled / stated.
   **PRIMARY:** every control class holds **1U height** by default (the `uDp` unit-grid standard),
   and its padding / insets / inner spacing **derive from / are expressed relative to 1U**, not
   arbitrary fixed dp — consistent with the U-relative / ratios-only law and R23/R24 (icon at 0.6U).
2. **Give every NAMED control a defined title / icon / severity(intent) / type + a11y label, stored
   in ONE place** (`ControlSpec` catalog, §3) so standards apply uniformly. Every control MISSING an
   assigned icon is surfaced as an **owner ASK-list** — Claude picks NO glyphs (icon law,
   `docs/ui_design/CLAUDE.md`), sourced against `img/material-icon-bucket.json`.

The base primitive everything builds onto is **`OutlinedControl`**
(`designsystem/control/OutlinedControl.kt`) — filled `t.surface`, 2dp intent border,
`max(LocalUnitDp,64dp)` height, 0.6U glyph, `fsSp(18)` label, `fill` override for selected,
`enabled` true-disablement, `contentDescription`. We build ON it; we do not reinvent it.

---

## 2. Scope

### IN
- **ActionButton** — every `OutlinedControl` in a foot bar / focus-foot (Back, Home, Save, Done,
  Print, Delete, Load/Unload, Cooldown, Clear, Execute, Connect, Edit, …). ~175 sites, 28 screens.
- **Selector family** — `SortRow`, `FilterRow`, `IncrementPicker`.
- **StepperRow** — the `[−][value][+]` rows, including the non-conformant hand-rolled ones in
  calibration (literal `"−"`/`"+"`/`"Z−"` text → must become `DinghyIcons.Decrease/Increase`).
- **Toggle** — `OutputToggleControl`, the Temperature trace-visibility toggle.
- **ColorSwatch** — the inline swatch clones in **Temperature + Spool only**.
- **Docked e-stop** — NOT restyled (already centralized, 0.7U by law) but **cataloged** as a named
  control (`printer.estop`).

### OUT (protected zones — owner ruling 2026-06-14)
- **The entire Settings menu** — future rework owns it. Excludes `ThemeEditorScreen` swatches +
  palette-mode selector + dark/light toggle, `SettingsScreen` dense toggles. (Inline ColorSwatch
  clones in ThemeEditor are therefore OUT — only Temperature + Spool consolidate.)
- **Move's focus-body touchscreen controls** — touch-move, XY/Z position scrubbers, bookmarks, bed
  map. Move's **foot bar** (Back/Disable/HomeAll/Save, mode selectors) stays IN as ActionButtons.
- **Anything drawn inside a printer-bed representation** — `BedMapView`, `BedMeshHeatmapView`,
  jog-pads-over-bed, the calibration jog arrows (`≺≻∧∨`) and probe jog. These follow **per-page
  rules** (tie sizing to U within their own pages where possible) — NOT universal control laws.
  Consequence: calibration's NON-bed ± steppers (select-corner/screw, nudge-value) and foot bars
  ARE in scope and DO get fixed; the bed-area jog controls do not.
- **ColorWheel** — sanctioned >1U exception, left as-is.
- **Extrude screen** — excluded entirely.

### Explicitly NOT this pass
- **Field LIST ITEMS / `ListRow`** — a separate field-list audit.

### Cleanup folded into this pass
- **Delete `OldMoveScreen.kt`** (the legacy jog-button grid) + its debug-Gallery entry + any test
  refs. The Move Hub is the committed design; the old grid is not carried forward (owner, 2026-06-14).

---

## 3. Single source of identity — the `ControlSpec` catalog

Validated by a Codex (gpt-5.5) architecture consult, 2026-06-14. **Presentation-only** catalog of
NAMED controls that COMPOSES the three existing registries without duplicating them:

- `R.string.*` owns user-facing **titles** + a11y strings.
- `DinghyIcons` owns **icon** tokens (`DinghyIcon`).
- `CommandRegistry` owns **dispatch** (`CommandSpec` — transport/method/params/availability/semantics).

```kotlin
// works.mees.dinghy.control.ControlSpecs  (app-level — composes the registries)
@JvmInline value class ControlKey(val value: String)

data class ControlSpec(
    val key: ControlKey,
    @StringRes val labelRes: Int? = null,            // null = icon-only; label from context
    @StringRes val contentDescriptionRes: Int? = null,
    val icon: DinghyIcon? = null,
    val intent: Intent = Intent.Neutral,
    val type: ControlType = ControlType.Button,
    val commandCatalogId: String? = null,            // REFERENCE to CommandRegistry — never copied
    val longPressCommandCatalogId: String? = null,
)
```

Rules (from the consult):
- Store `@StringRes Int` + `DinghyIcon` tokens **directly** (compile-time safety, grepable; the
  actual text/glyph stays a one-place edit in its owning registry).
- Command-firing controls store only `commandCatalogId` (audit/link metadata) — dispatch still uses
  the typed `CommandRegistry` spec at the call site. Never copy dispatch fields.
- **Key by semantic ROLE, not label or placement.** Same label + different intent → different keys
  (`common.back` = Accent nav; `common.back.discard` = Danger cancel-with-loss, the C7 rule).
- **Named/recurring + safety-significant controls only** get catalog entries. **One-offs stay
  inline** through the same classes — do not over-catalog (it kills the audit signal).
- Compose call site: `OutlinedControl(spec = ControlSpecs.Back, onClick = onBack)` overload
  resolves `stringResource(labelRes)`, passes `icon`, applies `intent`. Existing inline overloads
  remain for one-offs / dynamic labels — no forced migration.
- **New `ControlCatalogDriftTest`** from day one: unique keys, every `icon` exists in
  `DinghyIcons.all`, every `commandCatalogId` resolves in `CommandRegistry.all`, every icon-only
  entry has a non-null `contentDescriptionRes`. Extends the `CommandCatalogDriftTest` discipline
  (`[[dinghy-command-catalog-drift]]`).

---

## 4. The control classes (taxonomy)

Each class OWNS its padding/spacing/state styling — a tweak is a one-file edit; never hardcode style
per-screen (`[[dinghy-component-class-workflow]]`).

| Class | Role | Today | Action |
|---|---|---|---|
| **ActionButton** | foot/focus-foot `OutlinedControl` | clean | catalog + a11y sweep |
| **SelectorRow** | row of option tiles, one active | `SortRow`/`FilterRow`/`IncrementPicker` near-clones | **one primitive; the three become NAMED presets** (locked SortFilter anatomy preserved as a preset). Toggles join as presets. |
| **StepperRow** | `[−][value][+]` | duplicated 6× (AdjusterPanel, Scrubber, 4 calibration screens) | one shared `StepperRow`; calibration inline ± migrate onto it w/ `Decrease/Increase` icons |
| **Toggle** | on/off · this/that (inactive = `Intent.Neutral`, R18) | fragmented (OutputToggle, MUI `Checkbox`, custom pill) | unify the in-scope ones onto SelectorRow presets; kill the rogue `Checkbox`/pill |
| **Scrubber** | 004 ringed-thumb drag | centralized ✓ | no change (verify U-relative spacing) |
| **ColorSwatch** | tappable color tile | `ColorSwatchGrid` ✓ + inline clones | consolidate Temperature + Spool inline swatches |
| **DenseValueCell** | value over background glyph | `IconValueCell` ✓ | no change |
| **DockedEStop** | header morph + shell fallback | centralized ✓ | catalog only |

---

## 5. Attribute + STATE + a11y schema (one baseline per class)

| State | Baseline treatment |
|---|---|
| **default** | filled `t.surface`, 2dp intent border, 1U height |
| **pressed** | Compose ripple (one-shot only); Scrubber's `accentSoft` halo — NO continuous animation (Adreno-320 motion law) |
| **selected / active** | `fill = accentSoft` + `accentLine` border (the ListRow-selected convention; already in `IncrementPicker`) |
| **disabled** | `enabled = false` → no click installed (true disablement, R10); visual = alpha 0.38 + `semantics { disabled() }` |
| **pending / busy** | alpha 0.38 but **stays clickable** — taps accumulate (the AdjusterPanel `busy` convention) |

**Mandatory a11y:** every control carries a `contentDescription` — required for icon-only,
label-doubles for labeled. New conformance check: an icon-only `OutlinedControl`/control with no
`contentDescription` = **fail**. (The inventory found dozens missing it — this closes that backlog.)

---

## 6. 1U + U-relative spacing (reduce, then go U-relative)

Owner directive: reduce distinct styles first, THEN express relative to 1U.

- **Height:** all in-scope control classes floor/cap at 1U where `LocalUnitDp` is provided; make that
  provision universal (the R26 mechanism — already in FootButtonBar/IncrementPicker/AdjusterPanel/
  Scrubber; extend to the rest).
- **Inner spacing — collapse then fractionize.** Distinct values today across in-scope controls:
  `{4, 8, 10, 12, 14}dp`. Collapse to **two inner-spacing tokens** (+ `padFloat` edge case):
  - `gapS ≈ U × 0.125`  → 8dp @ U=64  — tile/element gaps (absorbs the stray 4dp + 10dp)
  - `gapM ≈ U × 0.1875` → 12dp @ U=64 — inter-row rhythm
  - `padFloat` (14dp) retained ONLY for the floating-overlay edge case (`FloatingEStop` shell fallback)
  Exact fractions are tuned on-device (flox + moto) and confirmed at master-list review.
- **Icons** already U-relative (R23/R24 = 0.6U); **±** already icon-backed (`Decrease`/`Increase`).
- **R13 reconciliation:** this **supersedes R13's "spacing is a named FIXED-dp set, never
  U-derived" clause** — the NAMES survive (`gapS`/`gapM`) but now resolve to U-fractions. Record the
  supersession in `THEMING.md §7b` and `COMPONENTS.md §7b/§4` so the docs stop contradicting.

---

## 7. Reset / revert standardization

`FocusFrame`'s header **trailing-action revert** (bare refresh glyph, shown only when value deviates
from baseline, `text2`, SAFE-only) becomes the **app-wide "reset to default" affordance**. Cull the
redundant foot-bar **Reset** buttons wherever the revert is safely revertible (FineTune "Reset all",
the adjuster screens). **Caveat:** a calibration "Reset" that re-arms a *sequence* (not just a value)
is NOT a safe revert and stays a foot button — verified per-screen, not blindly culled.

---

## 8. Execution order — MASTER-LIST review is the hard gate

1. **System-wide audit** → finalize the in-scope inventory (this doc's findings + the two explorer
   sweeps already run).
2. **Master list (owner review gate).** Produce ONE reviewable table: every control output, its
   **screen**, its **current status**, and the **proposed mapping** (icon / label / intent / type /
   ControlSpec key). This is the icon ASK-list + ControlSpec draft combined, presented as an easy
   source for owner corrections. **Claude assigns NO glyph it must invent** — unassigned icons are
   flagged as owner asks, sourced against `img/material-icon-bucket.json`.
3. **Owner corrects the master list.** ONLY after feedback:
4. **writing-plans → subagent-driven execution.**

---

## 9. Workflow & verification

- Flow: brainstorm (done) → writing-plans → subagent-driven-development. GSD OFF.
- **Codex (gpt-5.5/xhigh) reviews the final SPEC *and* the final PLAN** before execute, via the
  non-reaping background path (`[[codex-rescue-bg-dispatch-dies-midstream]]`); fix agreeable
  show-stoppers, surface borderline calls. Alert owner on criticals.
- Builds run **Windows-side** via `E:\Android\gw.bat` (`[[dinghy-display-build-env]]`).
- **Host tests** verify pure style-resolution (intent→token, U→spacing, ControlSpec→render);
  **UAT the look on BOTH** flox (armeabi-v7a, `0a64b42e`) + moto (arm64-v8a, `ZY22LBDRM9`) —
  push the matching split-ABI slice to both (`[[dinghy-test-devices]]`).
- Implementers **stage explicit paths**, never `git add -A`.
- **HARD RULE: never pick an icon — ASK.**

---

## Open items confirmed at master-list review
- SelectorRow "one primitive, named presets" collapse (default = yes).
- Spacing fractions `gapS = U×0.125`, `gapM = U×0.1875` (default = these).
- Per-screen Reset-cull decisions (safe-revert vs sequence-rearm).
