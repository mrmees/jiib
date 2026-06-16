# Home Spool row → data-rich color-coded status row

**Date:** 2026-06-16
**Status:** design approved, pre-plan
**Owner:** Matthew

## Problem

On the collapsed home/status screen, the idle-action list (`HomeField` in
`ui/printstatus/PrintStatusField.kt`) renders a generic "Spool" nav row — a plain
`LauncherSpool` icon (neutral tint) + the static "Spool" label. It carries no information
about the loaded filament, even though that data is already on the screen (the
`ActiveSpoolCardState` that feeds the Focus glance line). The owner wants this row to surface
the active spool at a glance, matching the color-coded identity the Spool screen already uses.

## Goal

Make the home "Spool" row data-rich: a filament-color-coded icon + the loaded filament's
identity text (color / type / mfg), scrolling on overflow. Fall back cleanly when no spool is
loaded.

## Non-goals

- No change to the capability gate — the row still only appears when Spoolman is configured
  (`buildIdleActions(spoolmanPresent = …)`), unchanged.
- No change to navigation — the row still taps through to `NavDest.Spool` in every state.
- No new shared component class — this is a single bespoke row local to `HomeField`.
- No change to the Spool screen, the Focus glance "Spool remaining" line, or `SpoolGlyph`
  (the custom spiral glyph on the Extrude screen — explicitly NOT what this uses).

## Design

The "Spool" row becomes **special-cased** in `HomeField`'s row loop (today it is one of the
generic `HomeAction.Destination` rows). When the iterated destination is `NavDest.Spool`, render
a `SpoolStatusRow` instead of the generic `ListRow`+`ListRowLabel`; all other rows are
unchanged.

### Icon

Swap `DinghyIcons.LauncherSpool` → `DinghyIcons.SpoolFilament` (the `ev_shadow` ligature),
tinted to the loaded filament color using the **same derivation the SpoolScreen detail header
uses** (`SpoolScreen.kt:283`):

```kotlin
val spoolColor = spool?.filament?.colorSwatches?.firstNotNullOfOrNull { parseNormalizedHex(it) }
```

Rendered via `ListRowIcon(DinghyIcons.SpoolFilament, uDp, tint = spoolColor ?: t.text2)` — the
standard 0.6U list-row icon size. Tinting the glyph a literal filament color is the established
THEME-01 data carve-out (same as the Spool screen). **Uncolored (`t.text2`) when no spool is
loaded.**

### Text (replaces the "Spool" label)

- **Loaded** (`ActiveSpoolCardState.Loaded`): `name / material / vendor.name` — the non-null
  fields of `filament` joined with `" / "`, e.g. **"Galaxy Black / PLA / Hatchbox"**. (In
  Spoolman, `filament.name` is the color/variant label — there is no dedicated color-name
  field; color-as-hex is conveyed by the tinted icon.) Rendered as a single-line `Text`
  (`DinghyType.listLabel`, `t.text`) with `basicMarquee()` so it scrolls when it overflows the
  row width — the approved motion-law exception (same as the FocusFrame title,
  `FocusFrame.kt:244`). If a loaded spool has none of those three fields, fall back to "Spool".
- **Not loaded** (Spoolman present but `NoActive` / `Loading` / any non-`Loaded` state): text
  **"No Spool Loaded"** (new string), icon uncolored. The same `ev_shadow` glyph shape is kept
  across both states; only the color changes (owner decision 2026-06-16).

### Data flow

`HomeField` does not currently receive the active-spool data. Thread `activeSpoolCardState:
ActiveSpoolCardState` into `HomeField` (it already flows from `PrintStatusScreen` →
`PrintStatusContent` → `HomeFocus`; add it to the `HomeField` call too). Inside, extract the
loaded spool: `(activeSpoolCardState as? ActiveSpoolCardState.Loaded)?.spool`.

### Units to build

1. **`spoolRowText(filament: SpoolmanFilament?): String?`** — pure helper:
   `listOfNotNull(filament?.name, filament?.material, filament?.vendor?.name).joinToString(" / ").ifBlank { null }`.
   Returns null when no usable field exists. Unit-tested (all-present, partial, all-null/blank).
2. **`SpoolStatusRow`** — private composable in `HomeField`: builds the tinted `ListRowIcon`
   leading + the marquee text content; `onClick = onNavigate(NavDest.Spool)`.
3. One new string: `printstatus_spool_none` = "No Spool Loaded".

Reuses existing `parseNormalizedHex` (`SpoolScreen.kt`) and `basicMarquee`
(`FocusFrame.kt`) patterns. `parseNormalizedHex` is currently `internal` in `SpoolScreen.kt` —
confirm it's reachable from the printstatus package (same module); if not, hoist it to a shared
util rather than duplicate it.

## State → row matrix

| State | Icon | Text |
|-------|------|------|
| Loaded, with fields | `ev_shadow` tinted to filament color | `name / material / vendor` (marquee) |
| Loaded, no usable fields | `ev_shadow` tinted (or `text2` if no color) | "Spool" |
| Spoolman present, not loaded / resolving | `ev_shadow`, `text2` (uncolored) | "No Spool Loaded" |
| Spoolman absent | — (row not present; capability gate) | — |

## Success criteria

1. With a spool loaded, the home Spool row shows the `ev_shadow` icon tinted to the filament
   color and the `name / material / vendor` text, scrolling if it overflows.
2. With no spool loaded (Spoolman present), the row shows the uncolored `ev_shadow` icon and
   "No Spool Loaded".
3. The row still navigates to the Spool screen; all other idle-list rows are unchanged.
4. `spoolRowText` is unit-tested; build + suite green.
