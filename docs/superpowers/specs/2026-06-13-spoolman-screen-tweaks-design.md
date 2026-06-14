# Spoolman Screen Tweaks — Design

**Date:** 2026-06-13
**Status:** Approved (pending user review of this spec)
**Scope:** Two quick polish tweaks to the Spool screen (`ui/spool/SpoolScreen.kt`): (1) the Focus
header title shows the selected spool's identity instead of a static "Spool"; (2) the custom spool
glyph on this screen is replaced by a spool-colored `ev_shadow` icon, which lets the redundant color
chip + material-name row be dropped from the detail area.

## Problem

- The Focus pane header always reads the static launcher label "Spool" (`R.string.cd_launcher_spool`),
  even when a spool is selected — it doesn't identify *which* spool you're looking at.
- The selected spool's identity + color are conveyed by three things in the detail area: the
  `FocusFrame` header glyph (the custom side-view spool drawable), a color **chip** (`DetailSwatch`),
  and a bare material-name label ("PLA"). With the title about to carry `material · name` and the
  header icon about to carry the color, the chip and the material label become redundant.

## Decisions (owner-confirmed 2026-06-13)

1. **Focus title format:** mirror the list row exactly — `material · name` (e.g. `PLA · Galaxy Black`),
   middot separator. Fallback to "Spool" when no spool is selected.
2. **Detail top row:** drop the WHOLE first row (color chip + material-name text), not just the chip.
3. **Empty state:** switch it too — use a neutral `ev_shadow` everywhere on this screen, dropping the
   custom spool glyph from the empty state as well.

## Design

### 1. Focus title = selected spool's identity

`SpoolContent` computes a title string and passes it to `FocusFrame(title = …)` instead of the static
`stringResource(R.string.cd_launcher_spool)`:

```kotlin
val focusTitle = selected?.let { sp -> spoolDisplayTitle(sp) }
    ?: stringResource(R.string.cd_launcher_spool)
```

`spoolDisplayTitle` is a new `@Composable` helper (file-private, in `SpoolScreen.kt`) extracting the
identity string the list row and the measure-weight header already build by hand:

```kotlin
@Composable
private fun spoolDisplayTitle(spool: SpoolmanSpool): String =
    listOfNotNull(spool.filament?.material, spool.filament?.name).joinToString(" · ")
        .ifBlank { stringResource(R.string.spool_unnamed, spool.id) }
```

Then `SpoolRowBody` (currently line ~1009) and `SpoolMeasureWeightField`'s info card (currently line
~759) are updated to call `spoolDisplayTitle(spool)` instead of repeating the `listOfNotNull(...)`
expression — DRY, one definition. (Pure refactor; same output string.)

`FocusFrame`'s header title is centered, 20sp SemiBold, marquee-on-overflow — a `material · name`
string fits or scrolls; no layout change needed.

### 2. New `ev_shadow` icon, spool-colored; replaces the custom spool glyph on this screen

**Registry (`designsystem/icons/DinghyIcons.kt`):**
- Add `val SpoolFilament = DinghyIcon(IconRef.Ligature("ev_shadow"), alternate = "spool_filament")`,
  with a comment noting it is the owner-chosen (2026-06-13) Spool-screen identity glyph and that
  `ev_shadow` is verified resolvable in the bundled v2.944 Material Symbols ttf.
- Add `SpoolFilament` to the `all` list.
- This passes all `DinghyIconsTest` checks: non-blank/unique `alternate` ("spool_filament" is new),
  unique `primary` (`ev_shadow` is used nowhere else), and it is a Ligature so the drawable-keepers
  test is unaffected. `verify_ligatures.py` already resolves `ev_shadow`.

**Shared component (`designsystem/components/FocusFrame.kt`):** add an optional identity-glyph tint
override so a screen can color the header icon with item data (THEME-01 data carve-out, parallel to
the existing `FocusEdge.Data` border):
- `FocusFrame(..., iconTint: Color? = null, ...)` — threaded into `FocusHeader`.
- In `FocusHeader`, the inert identity glyph's tint becomes `iconTint ?: t.text2` (today's default
  when null). The e-stop morph path is untouched — `iconTint` only affects the idle identity glyph.

**Spool screen (`ui/spool/SpoolScreen.kt`):**
- `FocusFrame(icon = DinghyIcons.SpoolFilament, iconTint = spoolColor, title = focusTitle, …)` —
  `spoolColor` is already computed in `SpoolContent` (the first parseable filament color swatch).
  When no spool is selected `spoolColor` is null → the header glyph falls back to `t.text2` (neutral).
- Empty state in `SpoolDetailContent` (the `spool == null` branch): replace the `SpoolGlyph(...)`
  with `DinghyIconView(DinghyIcons.SpoolFilament, tint = t.text3, sizeDp = fsSp(64f, t.fs).dp,
  contentDescription = stringResource(R.string.cd_spool_empty))`.
- Remove the now-unused `SpoolGlyph` import from this file. `R.drawable.spool` / `DinghyIcons.LauncherSpool`
  / the `SpoolGlyph` composable all REMAIN in the codebase (still used by PrintStatus launcher, idle
  list, etc.) — they are simply no longer referenced from `SpoolScreen.kt`.

### 3. Drop the redundant detail top row

In `SpoolDetailContent` (the `spool != null` path), remove the entire first `Row` (currently
lines ~865–880) that renders `DetailSwatch(...)` + the material-name `Text`. The new title + colored
icon now carry that identity. The `Column`'s remaining children (FillMeter, vendor/color row, weight
row, dates, temps, badges) shift up.

Remove the `DetailSwatch` composable (currently ~line 1077) — after this change it has no callers.
(Confirm with a grep before deleting; the row-level `SpoolRowSwatch` is a DIFFERENT composable and
stays.)

## Out of scope / unchanged

- The list rows themselves (still show their leading `SpoolRowSwatch` color dots + `material · name`).
- The FillMeter (already the spool's color-bearing fullness visual), vendor/color/weight/date/temp
  rows, badges, foot buttons, sort/filter rows, filter-picker, measure-weight field behavior.
- E-stop morph behavior (header icon → red e-stop while printing) — unchanged.
- The custom spool drawable/glyph and its other consumers across the app.

## Known caveat (eyeball on-device)

A near-black spool color tinting the `ev_shadow` glyph may be low-contrast against the dark `surface`
(the same risk the `FocusEdge.Data` border already carries). Shipping as directed; iterate if dark
spools' icons disappear (a possible future mitigation: a hairline/halo behind the glyph, as the
swatch chips use — not in scope now).

## Affected files

- `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` — add `SpoolFilament`; add to `all`.
- `app/src/main/java/works/mees/dinghy/designsystem/components/FocusFrame.kt` — add `iconTint` param, thread to `FocusHeader`.
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt` — title helper + focus title, header
  icon + tint, empty-state icon, drop detail top row, remove `DetailSwatch` + `SpoolGlyph` usages/import.

## Testing / verification

- `./… :app:testDebugUnitTest --tests *DinghyIcons*` — registry contract still green with the new icon.
- `python tools/verify_ligatures.py` — exit 0 (ev_shadow resolves; already confirmed).
- `:app:compileDebugKotlin` then a clean `assembleDebug --rerun-tasks`; verify APK mtime > the fix
  commit (stale-APK gate) and install the matching ABI on flox + moto.
- On-device eyeball (owner): select spools of different colors — header title reads `material · name`,
  header icon is `ev_shadow` tinted to the spool color, the color chip + material row are gone, the
  detail area still shows fill/vendor/color/weight/date/temps. Empty state shows a neutral `ev_shadow`.
  Check a black/very-dark spool for icon contrast. While printing, the header still morphs to e-stop.
```
