# Visual Pass — Six Tweaks (2026-06-17)

**Status:** approved (owner, 2026-06-17)
**Type:** visual/UI polish across Files, Move, Macros, Temperature
**Scope:** six independent, well-bounded tweaks. No new screens, no data-flow changes.

A quick visual pass over already-built screens. Each item is self-contained; two touch shared
design-system components and must be done carefully to avoid collateral damage.

---

## 1. Sort/Filter rows — drop the type-tiles, label the buttons (Files + Spoolman)

**Scope (owner, 2026-06-17):** apply to **both** `SortRow` AND `FilterRow` globally — Files and
Spoolman. "If you're removing the sort icon from Spoolman also remove the filter icon."

**Today:** `SortRow` / `FilterRow` (`designsystem/components/SortFilterControlRow.kt`) are presets over
`SelectorRow` that each LEAD with a mandatory recessed type-tile (`DinghyIcons.Sort` / `FilterList`,
`t.bg2`) and render the options as **icon-only** tiles, with (Sort only) an asc/desc direction-arrow
overlay on the active tile. Consumers:
- `SortRow`: `ui/files/FilesScreen.kt` (Date/Size), `ui/spool/SpoolScreen.kt` (Name/Date/Remaining),
  `preview/DesignKitComponentPreviews.kt` (sample).
- `FilterRow`: `ui/spool/SpoolScreen.kt` (Type/Color/Mfg), `preview/DesignKitComponentPreviews.kt`.

**Change:**
- Remove the leading type-tile from **both** presets (pass `leadingTypeTile = null`).
- Render the option tiles as **text-only** short labels (drop the per-option glyph), like the existing
  `AxisSelectorRow` (X/Y/Z text tiles). Keep the active asc/desc direction-arrow overlay on Sort.
  - In `SortOption` / `FilterOption`, **replace `icon: DinghyIcon` with `label: String`** (the glyph is
    no longer rendered). Keep `contentDescriptionRes` for TalkBack.
  - Labels — Files: "Date", "Size". Spool sort: "Name", "Date", "Remaining". Spool filter: "Material",
    "Color", "Brand". Preview: mirror. *(Spool labels are author picks — owner to confirm at UAT.)*
  - New string resources: `files_sort_date`/`files_sort_size`, `spool_sort_name`/`spool_sort_date`/
    `spool_sort_remaining`, `spool_filter_type`/`spool_filter_color`/`spool_filter_mfg`.
  - The **DesignKit preview** (`DesignKitComponentPreviews.kt`) is debug-only — it uses **literal**
    label strings, NOT string resources (amended 2026-06-17 per Codex review; avoids adding preview-only
    resources that lint would flag as unused outside debug).
- Update `SortRow`/`FilterRow` KDoc and `docs/ui_design/COMPONENTS.md` "leading TYPE tile is MANDATORY"
  note — the type-tile is RETIRED; rows are text-label tiles. (Design-system LAW change.)

**Containment:** `SelectorRow` already supports text-label tiles (`AxisSelectorRow`) and the direction
overlay together. The `leadingTypeTile` param stays on `SelectorRow` (now always null from presets).

**Test impact:** any test asserting an icon-only sort/filter tile, a leading type-tile, or
`SortOption(icon=…)`/`FilterOption(icon=…)` construction must update.

---

## 2. Move → Z screen, five columns

**Today** (`ui/move/MoveScreen.kt`, `MoveMode.Z`, ~L391–450): three columns —
`ZScrubberColumn(Fine)` | center Z value | `ZScrubberColumn(Full)`, where each `ZScrubberColumn`
bundles its top/bottom range labels *inside* the scrubber column (`dataMeta` 15sp, `t.text2`). Center
Z value is `dataInline` (20sp).

**Change — five columns:**

| Col | Content | Alignment | Style |
|-----|---------|-----------|-------|
| 1 | Fine range: `50` (top), `0` (bottom) | top / bottom of full height | `dataInline` (20sp), `t.text2` |
| 2 | Fine vertical scrubber, range 0–50, step 0.1 | — | unchanged |
| 3 | Current Z value, `"%.2f" + "mm"` | vertically centered | **`statValue` (26sp)** |
| 4 | Full vertical scrubber, range 0–zMax, step 1 | — | unchanged |
| 5 | Full range: `floor(zMax)` int (top), `0` (bottom) | top / bottom of full height | `dataInline` (20sp), `t.text2` |

- Range labels land at **20sp** (owner decision 2026-06-17 — the Z value's *current* size; not the new 26sp).
- `zMax` top label is **floored to integer** (`floor(zMax).toInt()`), not `fmt1`.
- Center Z value bumps **`dataInline` → `statValue`** to match the X/Y coordinate readouts on the Touch
  Move and XY-position focuses (both use `statValue`, 26sp).
- Scrubber columns keep equal weight; label/value columns wrap content. Total = 5 side-by-side columns.
- Refactor: split `ZScrubberColumn` into a bare scrubber column + a separate range-label column (Column
  with the two labels pushed to top/bottom via a `Spacer(weight(1f))` between them).

---

## 3. Microstep — `stat_minus_1` / `stat_1` on the step-size cycler

**Today** (`ui/move/MoveScreen.kt`, `MoveMode.Microstep`, ~L515–528): the **step-size cycler** is a
`StepperRow` (`Intent.Accent`, step value in the center slot) using the shared default
`DinghyIcons.Decrease` (`remove`) / `DinghyIcons.Increase` (`add`). A second `StepperRow` below it is
the jog ± pair (`Intent.Go`, drives real motion) — **out of scope, keep its icons.**

**Change:**
- Register two icons in `designsystem/icons/DinghyIcons.kt`:
  - `val StatMinus1 = DinghyIcon(IconRef.Ligature("stat_minus_1"), alternate = "decrement_one")`
  - `val StatPlus1  = DinghyIcon(IconRef.Ligature("stat_1"),        alternate = "increment_one")`
- Add registry entries with **notes** in `img/material-icon-bucket.json` for `stat_minus_1` and `stat_1`:
  note = "increment-selection step − / + — use wherever an increment SELECTION is adjusted".
- Add optional icon-override params to `StepperRow` (`designsystem/components/StepperRow.kt`):
  `decrementIcon: DinghyIcon = DinghyIcons.Decrease`, `incrementIcon: DinghyIcon = DinghyIcons.Increase`.
  Default behavior unchanged for all existing callers.
- Pass `decrementIcon = DinghyIcons.StatMinus1`, `incrementIcon = DinghyIcons.StatPlus1` to the
  microstep **step-size cycler only**.

**Gate:** `python tools/verify_ligatures.py` scrapes every `IconRef.Ligature` and checks the bundled
`material_symbols_outlined.ttf` GSUB table. **Verified 2026-06-17: both `stat_minus_1` and `stat_1` are
PRESENT** in the bundled font — no font bump needed. Re-run after registering to keep the gate green.

---

## 4. Edit Macros — bookmark Focus copy + hide/show label

**Today** (`ui/macros/BookmarkedMacrosScreen.kt`):
- ManageMode Focus shows a generic centered prompt `macros_focus_select_prompt` = "Select a macro to run."
- `MacroManageField` shows the underscore note `macros_helper_hint` = "Underscore-prefixed helper macros
  are hidden. Show hidden to reveal them." at the **top of the Field list**.
- Foot button: label is always `macros_foot_show_hidden` = "Show hidden"; icon flips
  `VisibilityOff`↔`Visibility` with `state.revealHidden`.

**Change:**
- **Focus (ManageMode):** replace the generic prompt with a short stacked explainer:
  1. Bookmarking note (new string `macros_bookmark_explainer`): *"Bookmarked macros show on the home
     screen for quick access. Pin the ones you use most."*
  2. The underscore note (`macros_helper_hint`), **moved here** from the Field list.
  - Keep it readable: centered/started column, `body`/`caption` roles, `t.text2`.
- **Remove** the underscore note from the top of `MacroManageField`.
- **Foot button label tracks icon:**
  - `revealHidden == false` → icon `VisibilityOff`, label **"Show"** (new `macros_foot_show`).
  - `revealHidden == true`  → icon `Visibility`,    label **"Hide"** (new `macros_foot_hide`).
  - (Icon pairing owner-specified — not an independent glyph pick.)

---

## 5. Temperature graph fills to the frame edges

**Today** (`ui/temperature/TemperatureScreen.kt`, ~L526): the Monitoring graph's `FocusFrame` is given
`contentInset = FocusInset / 2` (8dp). `FocusFrame` clips the content column to the rounded `shape` and
pads start/end/bottom by `contentInset`, so the `GraphView` sits 8dp inside, leaving a gap at the
rounded bottom corners.

**Change:** set that FocusFrame's `contentInset = 0.dp` so the GraphView fills to the rounded edge (the
`clip(shape)` rounds the corners — exactly the "drawn just inside the rounded corner" look wanted).
- Only the **Monitoring graph** FocusFrame — the Adjust-mode frame keeps its inset.
- `GraphView` draws its own axis labels with `LABEL_PAD` (6px), so labels stay legible at 0 frame inset.
- Header (1U) is above the content; `FocusFrame` already uses top inset 0 — unaffected.

---

## 6. Title Case heater names

**Today** (`ui/temperature/TemperatureHolder.kt`, `label()`): names are UPPERCASED —
`extruder→NOZZLE`, `extruderN→NOZZLE N`, `heater_bed→BED`, `heater_generic X→X.uppercase()`,
`temperature_sensor X→X.uppercase()`, else `objectName.uppercase()`.

**Change:** Title Case the display names:
- `extruder → "Nozzle"`, `extruderN → "Nozzle N"`, `heater_bed → "Bed"`.
- generic/sensor/else → a `titleCase()` helper: lowercase, split on whitespace **and underscore**,
  capitalize each word, join with spaces. e.g. `mcu_temp → "Mcu Temp"`, `chamber → "Chamber"`.
- Apply the same `titleCase()` to `sensorPickerLabel()` in `TemperatureScreen.kt` for consistency.

**Test impact:** any test asserting uppercase heater labels must update.

---

## Cross-cutting

- **Shared components touched:** `SortRow` + `FilterRow` (item 1 — global, both Files & Spoolman +
  preview), `StepperRow` (item 3). The `StepperRow` change is purely additive (defaulted params) so its
  5 existing callers are safe. The `SortOption`/`FilterOption` `icon→label` swap touches all 3 sort + 2
  filter call sites (Files, Spool, preview).
- **Icon law:** all glyphs here are owner-specified (`stat_minus_1`, `stat_1`, `visibility`/
  `visibility_off`). No independent glyph picks. New glyphs gated by `verify_ligatures.py`.
- **Build/verify:** assemble both ABI slices; install + owner UAT on flox (Nexus 7) and moto.
- **Out of scope:** jog ± pair icons, FilterRow, Adjust-mode temp frame, any data-flow change.
