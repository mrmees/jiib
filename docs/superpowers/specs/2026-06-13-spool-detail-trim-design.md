# Spool Detail Card Trim + Title Reorder — Design

**Date:** 2026-06-13
**Status:** Approved (pending user review of this spec)
**Scope:** Continue the Spool screen polish. Consolidate the selected-spool identity into the Focus
header title (`MFG · Chemistry · Color`) and strip the now-redundant rows from the detail card,
leaving a lean fill-bar/temps/date card. One file: `ui/spool/SpoolScreen.kt`.

## Problem

After the prior tweak (title = `material · name`, spool-colored `ev_shadow` header icon), the detail
card still repeats identity/data that now belongs in the title or the fill bar:
- The **vendor/color row** shows manufacturer + color name — color is already in the title; vendor
  should move into the title too.
- The **weight row** (`579/1000 g` + edit) duplicates the FillMeter's label (`579/1000 g · 58%`); it
  exists mainly as the tap target for the measure-weight correction.
- The temperature readings sit *below* the registration date, which is a less useful order.

## Decisions (owner-confirmed 2026-06-13)

1. **Focus title** = `vendor · material · name` (MFG · Chemistry · Color), middot separator —
   e.g. `Prusament · PLA · Galaxy Black`. Missing parts skipped; fallback `Spool <id>`.
2. **Drop the vendor/color row entirely** from the detail card (vendor → title, color → title).
3. **Drop the weight row**; move tap-to-measure onto the **fill bar**.
4. **Move the temps row above the date row.**

## Design

All edits are in `SpoolDetailContent` (and a new title helper) in `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt`.

### 1. Focus title: `vendor · material · name`

Add a dedicated helper (the focus header consolidates all three; the list rows keep their own
`material · name` title + vendor-in-meta, so this is a SEPARATE format from `spoolDisplayTitle`):

```kotlin
/**
 * The Focus-header identity for a selected spool: `vendor · material · name`
 * (MFG · Chemistry · Color), e.g. `Prusament · PLA · Galaxy Black`. Missing parts are skipped;
 * degrades to `Spool <id>`. Distinct from [spoolDisplayTitle] (the list-row / measure-card
 * `material · name`) because the list rows carry vendor in their meta line instead.
 */
@Composable
private fun spoolFocusTitle(spool: SpoolmanSpool): String =
    listOfNotNull(spool.filament?.vendor?.name, spool.filament?.material, spool.filament?.name)
        .joinToString(" · ")
        .ifBlank { stringResource(R.string.spool_unnamed, spool.id) }
```

In `SpoolContent`, change `focusTitle` to use it:

```kotlin
        val focusTitle = selected?.let { spoolFocusTitle(it) }
            ?: stringResource(R.string.cd_launcher_spool)
```

(`spoolDisplayTitle` remains, still used by `SpoolRowBody` and `SpoolMeasureWeightField` — unchanged.)

### 2. Fill bar becomes the tap target for measure-weight

The `FillMeter(...)` call gains a clickable wrapper carrying the existing edit content-description.
Replace the current call:

```kotlin
        FillMeter(
            fraction = fillFraction,
            fillColor = spoolColor ?: t.accent,
            modifier = Modifier.fillMaxWidth(),
            label = fillLabel,
        )
```

with:

```kotlin
        FillMeter(
            fraction = fillFraction,
            fillColor = spoolColor ?: t.accent,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(t.rCtrl))
                .clickable(onClick = onMeasure)
                .semantics { contentDescription = editWeightCd },
            label = fillLabel,
        )
```

where `editWeightCd = stringResource(R.string.cd_spool_weight_edit)` is resolved just above the
`FillMeter` (a `stringResource` can't be called inside the `semantics { }` lambda). `clip`,
`clickable`, `RoundedCornerShape` are already imported; add `androidx.compose.ui.semantics.semantics`
and `androidx.compose.ui.semantics.contentDescription` imports if not already present.

### 3. Remove the vendor/color row and the weight row

Delete these two blocks from `SpoolDetailContent` (the `spool != null` Column):

- The **vendor + color name** `Row` (storefront icon + vendor `DetailValue`, palette icon + color
  `DetailValue`).
- The **weight** `Row` (the `Modifier...clickable(onClick = onMeasure)` row with the scale icon,
  `spoolWeightText(spool)` Text, and the edit icon) — its `onMeasure` is now on the fill bar.

### 4. Reorder: temps before date

The resulting `Column` order becomes: FillMeter (tappable) → **Nozzle/Bed temps row** →
**Registration date row** → loaded/archived badges. (Move the existing temps `Row` above the date
`Row`; both blocks are otherwise unchanged.)

### 5. Dead-code cleanup (remove only what is truly orphaned — grep first)

After 3–4, these become unused **and should be removed**:
- `spoolWeightText(spool)` helper — only the weight row used it.
- `DetailValue` helper — only the vendor/color row used it.
- The `DinghyIcons.Edit` import — only the weight row's edit icon used it in this file.
- Strings `cd_spool_weight`, `cd_spool_vendor`, `cd_spool_color` in `res/values/strings.xml` —
  grep to confirm no other references, then remove.

**Keep:** `cd_spool_weight_edit` (now the fill-bar a11y label), `DinghyIcons.Scale` (sort option),
`DinghyIcons.Palette` (filter option), `DinghyIcons.Storefront` (filter option),
`DinghyIcons.Nozzle`/`HeatBed` (temps), `CalendarAddOn` (date), `filament`/`bodySp`/`iconSp` locals
(temps/date still use them).

## Out of scope / unchanged

- The spool **list rows** — trailing remaining-weight and the vendor/location meta line stay.
- `SpoolMeasureWeightField` (measure entry screen) and its `spoolDisplayTitle` usage.
- The FillMeter label format, the empty state, the header icon/tint, the e-stop morph, sort/filter
  rows, foot buttons.

## Affected files

- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt` — add `spoolFocusTitle`; switch
  `focusTitle`; make `FillMeter` tappable; remove vendor/color row + weight row; reorder temps/date;
  remove `spoolWeightText`, `DetailValue`, the `Edit` import.
- `app/src/main/res/values/strings.xml` — remove `cd_spool_weight`, `cd_spool_vendor`, `cd_spool_color`.

## Testing / verification

- `:app:compileDebugKotlin` clean (catches any orphaned import / unresolved reference).
- `:app:testDebugUnitTest` green (no logic touched; sanity).
- Clean `assembleDebug --rerun-tasks`; verify APK mtime > the fix commit (stale-APK gate); install
  the matching ABI on flox + moto.
- On-device eyeball (owner): select spools — Focus title reads `vendor · material · name`; the detail
  card shows fill bar → temps → date → badges only (no vendor/color/weight rows); tapping the fill
  bar opens measure-weight; the FillMeter label still shows the weight; list rows unchanged.
```
