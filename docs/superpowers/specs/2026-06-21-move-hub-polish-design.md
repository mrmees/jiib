# Move Hub polish — design

**Date:** 2026-06-21
**Status:** approved (owner), pending Codex spec review
**Scope:** Five small polish items on the Move Hub screen. No new behavior beyond relocating
existing actions, gating an existing list, and conforming spacing/sizing to established patterns.

## Context

The Move screen is the "Move Hub": a `ListBlock` Field menu of `MoveMode` rows that swap the Focus,
plus a `FootButtonBar`. Primary files:

- `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` — Field list (~768–826), foot bar
  (~829–868), per-mode Focus content (~485–758), `MoveRow` (~956–972), `EndstopRow` (~977–1005).
- `app/src/main/java/works/mees/dinghy/ui/move/MoveAvailability.kt` — `MoveAvailability` gating
  (`touchMove`/`xy`/`z`/`microstep`/`saveLocation`), ~15–29.
- `app/src/main/java/works/mees/dinghy/ui/move/MoveHolder.kt` — `MoveVm` (`xHomed/yHomed/zHomed/allHomed`).
- Canonical adjuster reference: `app/src/main/java/works/mees/dinghy/designsystem/components/AdjusterPanel.kt`
  (~174–193) + `StepperRow.kt` (~99, ~116–155) + `designsystem/layout/Spacing.kt` (`gapS(uDp) = uDp * 0.125f`).

Design law: `docs/ui_design/` (Focus/Field grammar, intent colors R5/R18/R19, 1U control cap UAT-5,
`gapS`/`controlHeight` spacing). Rows translucent, buttons filled.

## Items

### 1. Disable Motors + Add Bookmark → Field menu rows

Today both are foot buttons (`MoveScreen.kt` ~843–849 Disable, ~859–866 Save Location). Move both
into the Field list; the foot bar shrinks to **Back · Home All** (2 actions → icon+text per the
FootButtonBar count rule).

**No new `MoveRow` variant is needed.** `MoveRow(label, icon, selected, uDp, tint) { onClick }`
(`MoveScreen.kt` ~956) already supports fire-an-action rows: Home XY / Home Z (~770, ~775) pass
`selected = false` and an `onClick` that fires an action without setting `mode` or swapping the Focus.
Both new rows follow that exact pattern.

- **Disable Motors row** —
  `MoveRow("Disable Motors", DinghyIcons.MoveDisableMotors, false, grid.uDp, t.stop) { onDisableSteppers() }`.
  Tap fires `onDisableSteppers()` **immediately** (one tap, owner-confirmed) — no `mode` change, no
  Focus swap, no confirm. Always present. Positioned at the **bottom of the list**, below Endstops.
  - **Red tint:** `t.stop` is the Danger color (`Intent.Danger → t.stop`, `OutlinedControl.kt:75`).
    The `tint` arg colors the **leading icon only** (`ListRowIcon`); `MoveRow` calls
    `ListRowLabel(label)` (`MoveScreen.kt:968`) without forwarding a color, so the label defaults to
    `t.text`. **Default: icon-only red** — matches the Home XY/Z pattern (icon-tinted, default label)
    and needs no component change. (`ListRowLabel` already accepts a color param, `ListRow.kt:160`, so
    making the **label** red too would only require `MoveRow` to take and forward a label color — a
    deliberate translucent-row exception; left as an optional UAT follow-up if the icon alone reads
    too weak.)
- **Add Bookmark row** —
  `MoveRow("Add Bookmark", DinghyIcons.SaveLocation, false, grid.uDp, t.accent) { mode = MoveMode.SaveDialog }`,
  accent-tinted like the other rows (uses `SaveLocation`, distinct from the saved entries'
  `SavedLocation` glyph). Tap opens the SaveDialog Focus (identical to the old Save Location foot
  button). Positioned **directly above the saved-locations group**. Gated per item 2.

Foot bar after change: `FootAction` Back (`Intent.Accent`) · Home All (`Intent.Go`). Remove the
Disable and Save Location `FootAction`s.

### 2. Gate bookmarks + Add Bookmark on all-axes-homed

The saved-location rows (`items(savedLocations…)`, ~808–816) currently render ungated. Wrap **both**
the Add Bookmark row and the saved-location rows in `if (vm.allHomed) { … }` so the whole bookmark
group is **hidden** (not merely disabled) until X/Y/Z are all homed. The motion rows
(TouchMove/XY/Z/Microstep) already gate via `MoveAvailability`; this extends the same homed-only
visibility to the bookmark group.

**Mode-fallback (must-fix — required by this gating).** `mode` (`MoveScreen.kt:211`, default
`MoveMode.TouchMove`) persists independently of availability; there is no existing reset effect. With
the new gating, `Bookmark`/`SaveDialog` become reachable **only when homed**, so un-homing (e.g.
tapping Disable Motors) while one is active would hide its row but leave its Focus (Move/Delete, or
the save form) dangling. Add a reset:

```
LaunchedEffect(vm.allHomed) {
    if (!vm.allHomed && (mode is MoveMode.Bookmark || mode == MoveMode.SaveDialog)) {
        mode = MoveMode.TouchMove   // hub default — same state as initial unhomed entry
    }
}
```

Why only `Bookmark`/`SaveDialog` are reset: item 2's gating is what newly makes them reachable
**only when homed**, so they are the only modes this pass can orphan. The motion modes are unchanged
by this pass — Disable already un-homed the printer before (it was a foot button), so their un-homed
behavior is pre-existing, not a regression introduced here. (Note their guards differ: `Microstep`
guards on homed state, `MoveScreen.kt:485`; `TouchMove`/`XY`/`Z` guard on **bounds availability**, not
homed, ~270/~310/~420 — either way, out of scope for this pass.) A `Bookmark` whose location was just
deleted already renders a "Bookmark not found" hint (~589); folding that into the same reset is optional.

### 3. Microstep Focus button spacing → match Fine-Tune

**Diagnose-then-fix — the delta is NOT visible from static code.** A direct comparison shows the
Microstep Focus (`MoveScreen.kt` ~485–583) already uses the **same spacing primitives** as Fine-Tune's
`AdjusterPanel` Zone 2 (`AdjusterPanel.kt` ~174–193): both wrap their control rows in
`Column(verticalArrangement = Arrangement.spacedBy(8.dp))` and both use `StepperRow` (which applies
`gapS(uDp)` horizontally and `controlHeight(uDp)` 1U internally). So the original assumption ("replace
hardcoded `8.dp` with `gapS`") was **wrong** — Fine-Tune itself uses literal `8.dp` vertical (the
`AdjusterPanel` comment notes it is "pixel-identical to the prior hand-rolled Row").

Because the difference the owner sees is not derivable from the code, the implementer **must screenshot
the Microstep Focus and a Fine-Tune Focus on the moto, diff them, and identify the actual delta first**,
then conform Microstep to match. Candidate causes to investigate (do NOT pre-assume one):

- the outer Focus content inset / where the bottom-docked control rows sit under the weighted coord
  readout (Microstep weights the readout `weight(1f)` and bottom-docks 3 rows; Fine-Tune has only the
  value zone + a 2-element Zone-2 column),
- the step-cycler `StepperRow`'s **center value slot** styling/width vs Fine-Tune's plain ± stepper,
- `AxisSelectorRow` tile height/spacing (the extra 3rd/4th row Fine-Tune doesn't have),
- any `LocalUnitDp` / `controlHeight` provision difference.

Scope: match Fine-Tune's button spacing/padding; no behavior change to jog/step logic.

### 4. Bookmark Focus — buttons + coordinate line

Bookmark Focus (`MoveScreen.kt` ~585–645).

- **Buttons:** the Move/Delete row (~627–643) is a raw `Row(horizontalArrangement =
  Arrangement.spacedBy(8.dp))` of two `OutlinedControl`s with **no `controlHeight(uDp)`** and a
  hardcoded gap. `OutlinedControl` only floors at 1U when `LocalUnitDp` is in scope — otherwise it
  falls back to a ~64dp minimum. Re-wrap to the full canonical 1U pattern (mirror `StepperRow`'s
  internals): `CompositionLocalProvider(LocalUnitDp provides uDp) { Row(horizontalArrangement =
  Arrangement.spacedBy(gapS(uDp))) { OutlinedControl(Modifier.weight(1f).controlHeight(uDp) …) ×2 } }`
  so the buttons match Fine-Tune height/spacing and scale with U.
- **Coordinate line:** the readout (~594–605) is a fixed `DinghyType.statValue` `Text` with
  `maxLines = 1, softWrap = false` → it **clips on the moto** when Z is present. Replace with the
  **same `TextAutoSize` shrink-to-fit** the Microstep readout already uses (~527–541):
  `DinghyType.focusHero`, `TextAutoSize.StepBased(min = fsSp(15f), max = fsSp(40f), step = 1.sp)`,
  one line, centered — so the full X/Y/Z line always fits the Focus width.

### 5. Endstops Focus — note + vertical centering

Endstops Focus (`MoveScreen.kt` ~716–757; rows via `EndstopRow` ~977–1005).

- Add a **top note "Polls Every 500ms"** — accurate (the poll loop is literally `delay(500)` at
  ~739). Style: dim `DinghyType.caption` (`t.text2`/`t.text3`), centered, pinned at the top of the
  Focus content.
- **Center the endstop rows vertically** in the space below the note (note at top; the
  `current.forEach { EndstopRow(...) }` block centered in the remaining height). Applies to the
  loaded state; the `errored`/loading hints already center via `FocusCenteredHint`.
- No new glyph (text only) — icon law not triggered.

## Out of scope / non-goals

- No change to `onDisableSteppers`/`onHomeAll`/save-dialog logic, Moonraker calls, or
  `MoveAvailability` for motion rows.
- No confirm dialog on Disable (owner chose immediate).
- The only mode-fallback added is the narrow `Bookmark`/`SaveDialog`→`TouchMove` reset in item 2;
  motion-mode self-guards are untouched, and no broader availability→mode reconciliation is added.
- No `BedMapView`, endstop parsing, or saved-location persistence changes.

## Verification

- Build Windows-side (`E:\Android\gw.bat`), unit suite green (incl. `FontConformanceTest` — all text
  uses role `toTextStyle`, sized variants only via the sanctioned overload).
- **On-device screenshots (moto, arm64-v8a):** items 3 and 4-buttons are spacing/padding — capture
  the Microstep Focus and a bookmark Focus and match Fine-Tune pixel-for-pixel rather than eyeball.
  Confirm the bookmark coord line no longer clips with a Z value.
- Install matching split-ABI slices on flox (armeabi-v7a) + moto for owner UAT.

## Open defaults (owner may flip)

- Disable Motors row at the **bottom** of the list (vs. top).
- Disable row **tinted red** (vs. plain like other rows).
