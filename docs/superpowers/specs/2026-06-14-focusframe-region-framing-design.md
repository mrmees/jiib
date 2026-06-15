# Region framing — the field-side conformance pass (Phase 2 of 2) — design

**Date:** 2026-06-14
**Status:** approved design (revised post-Codex review), pending implementation plan
**Supersedes mechanism of:** `2026-06-14-focusframe-conformance-design.md` (Phase 1). Phase 1 pushed the
8dp frame *down* into `FocusFrame` (component self-owns). Phase 2 hoists the frame *up* to the
**region** so the WHOLE screen shares one registration grid owned in ONE place. Phase 1 was the
conformance recon that standardised call sites and surfaced outliers; this is the real centralisation.
`FocusFramePlacement` (introduced in Phase 1) is **retired** here.

> **Revision note (Codex gpt-5.5/xhigh, 2026-06-14):** the first draft claimed a near-blanket
> "pixel-identical except two fixes" and a one-line "delete the `ListBlock` top-8" per screen. Codex
> review found that **false** — the codebase is NOT uniform: several active screens wrap their field in
> a single helper/Column that already owns `padding(vertical=8)+spacedBy(8)`, several inter-element gaps
> are **16dp today** (would normalise to 8dp), `ListBlock`/`FootButtonBar` appear **embedded** (not as
> region children) and **nested** in sub-scaffolds, frozen reading screens use `FocusFrame` directly,
> and `OldMoveScreen` references the to-be-deleted enum. This revision folds all of that in. **The pass
> is a per-screen migration governed by an audit table, not a blanket sweep.**

## Goal

Centralise the 8dp edge-registration frame (`LAYOUT.md` R26 / C-E2) so each region (focus, field) is
framed **exactly once** by one composable/constant, and every region-filling component (`FocusFrame`,
`ListBlock`, `FootButtonBar`, `SortRow`, `FilterRow`) is **flush-fill** with no self-owned frame
padding. A single edit to the region inset moves the whole app. This is the "single-location change to
app-wide screen layout."

**The honest outcome:** every migrated region converges onto the **uniform 8dp grid**. Where a screen's
current geometry already equals the grid (`4+4=8` gaps, self-8 frames) the result is **pixel-identical**;
where a screen is currently **off-grid** (16dp gaps from a padded body + self-padded foot bar, the 4dp
`ListBlock` rhythm in Macros, etc.) it is **normalised to 8dp** — an *intended* conformance change,
enumerated per-screen for owner sign-off at UAT (not silent).

## Model (owner-confirmed)

> A **region** = an optional primary element (`FocusFrame` card **or** `ListBlock` list) + an optional
> stack of **control rows** (`SortRow`/`FilterRow`/`FootButtonBar`) outside the primary. One 8dp outer
> frame, 8dp gaps between elements.

Control rows are a distinct thing — outside both list and focus card, and dockable under **either**
region — satisfied *by construction*: the region primitive is identical for focus and field and control
rows are flush children, so a control row renders identically under the card or under the list. No
separate control-stack composable.

Pure registration refactor: **no element is physically relocated**; the only deliberate visual changes
are the enumerated 8dp normalisations (§"Intended visual changes").

## The primitive — `RegisteredRegion`

```kotlin
@Composable
fun RegisteredRegion(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.padding(RegionInset),               // 8dp, ALL four sides — THE frame
        verticalArrangement = Arrangement.spacedBy(RegionGap),  // 8dp between DIRECT children
        content = content,
    )
}
```

- `RegionInset` / `RegionGap` = **8dp** (the repurposed `ListFrameInset` value); the ONE place the app's
  registration lives. Keep the `ListFrameInset` symbol to minimise churn; re-document it as the region
  frame source.
- Provides `ColumnScope`: a flexible child uses `Modifier.weight(1f)`, control rows take intrinsic height.
- ⚠ **`spacedBy` only spaces DIRECT children.** A region whose content is a single helper/Column gets
  no inter-child spacing and double-pads against that helper's own padding — see §"Three structural
  patterns".

### Equivalence (only for the direct-child pattern)

| Edge / gap | Direct-child screen today | Under `RegisteredRegion` |
|---|---|---|
| Region top/bottom 8dp | caller `top=8` / `FootButtonBar` self | region `padding(8)` |
| Region horizontal 8dp | each component's self horizontal | region `padding(8)` |
| Gap between stacked elements | `4+4` composed | `spacedBy(8)` |

This holds for direct-child screens and the Spool/Files composed-focus (their `bottom4+top4` → one
region gap). It does **not** hold for the other two patterns below.

## Three structural patterns (the plan must classify EVERY screen into one)

1. **Direct-child** — slot content is `ListBlock(weight1, top8)` + `FootButtonBar` as direct children
   (e.g. SystemPage, SystemInfo, Printers, CalibrationHub field). Migration: delete the `ListBlock`
   top-pad; components go flush; region owns frame+gap. **Pixel-identical.**

2. **Helper-owned-framing** — slot content is a SINGLE child (a helper composable or `Column`) that
   itself owns `padding(vertical=8)` + `Arrangement.spacedBy(8)`, with `ListBlock`/`FootButtonBar`
   self-owning horizontal (e.g. **PrintStatusField** `PrintStatusField.kt:93,185`; **Probe** field Row
   `ProbeCalibrateScreen.kt:233`; **Tilt** `TiltScreen.kt:174`; **Extrude** `ExtrudeScreen.kt:333`).
   Migration: **dissolve the helper's framing Column** — hoist its children to be DIRECT region children
   (helper becomes a `ColumnScope.()` extension or its inner `Column` is removed) and drop its
   `padding`/`spacedBy`; the region now supplies both. ⚠ If left as-is, the region double-pads (region 8
   + helper 8 = 16) and `spacedBy` no-ops. Some of these currently produce **16dp** inter-element gaps
   (padded body bottom 8 + foot-bar self top 8) that **normalise to 8dp** — intended, enumerate for UAT.

3. **Embedded / non-region-child** — a `ListBlock` or `FootButtonBar` that is NOT a region child:
   - `ListBlock` inside a `FocusFrame` body — **Temperature `SensorPickerFocus`** `TemperatureScreen.kt:788`
     (`ListBlock(weight1)`, no top-pad). Flushing `ListBlock` removes its horizontal 8dp inset here.
   - `FootButtonBar` outside any region — **Webcam full-focus path** `WebcamScreen.kt:224` (`WebcamBackBar`
     via `modifier`), **OutputToggleControl** `:123` (inside a `padding(16)/spacedBy(12)` reading Column).
   Migration: **preserve the inset explicitly** at the call site (wrap in `Modifier.padding(...)` or a
   `RegisteredRegion`), since the global flush change removes it. The plan lists each and its fix.

## Scaffold integration — opt-out / default-on

`ScreenScaffold` wraps each non-null slot's content in `RegisteredRegion` **by default**, with
per-region opt-out booleans:

```kotlin
fun ScreenScaffold(…, focusFramed: Boolean = true, fieldFramed: Boolean = true)
```

Internally each slot: `if (framed) RegisteredRegion(regionMod, content) else Column(regionMod, content)`
where `regionMod` is the existing weight/fillMax modifier (landscape & portrait paths, incl. the
`portraitFocusAspect` jog-pad path — the framing changes ONLY whether the region Column carries
padding+`spacedBy`; the aspect-lock/weight split is untouched). **Default-on** so new screens are correct
for free and frozen screens are explicit, temporary exceptions (owner). Console is not a `ScreenScaffold`
— it calls `RegisteredRegion` directly as its single region.

## Components go flush

- **`FocusFrame`** — delete the `.padding(focusFramePadding(placement))` line; **remove
  `FocusFramePlacement` entirely** (enum, `focusFramePadding()` helper, the `placement` param, the
  Phase-1 `FocusFramePaddingTest`). Inner `contentInset` (16dp, incl. `FocusInset/2` adjuster screens)
  unchanged.
- **`ListBlock`** — drop `.padding(horizontal = ListFrameInset)`. (Edge fades / `LazyColumn` unchanged.)
  Note the `DesignListBlock` alias used in Spool (`SpoolScreen.kt:483,554,613`) is the same component —
  cover it.
- **`FootButtonBar`** — drop `.padding(horizontal = ListFrameInset, vertical = 8.dp)`; keep
  `fillMaxWidth()` + `heightIn(min = uDp)`.
- **`SortRow`/`FilterRow`** — already flush internally; only their call sites lose hand-padding.

## ScreenScaffold framing-decision audit (all 30 sites; plan confirms each on-device)

**Active — `focusFramed=true, fieldFramed=true`** (migrate per its pattern above):
BedMesh, CalibrationHub, Probe, ScrewsTilt, Tilt, Extrude, FineTune, BookmarkedMacros, Move, Outputs,
PrintStatus ×4, Printers, SystemPage, SystemInformation, Temperature, Spool, Files (main `:388`).
Console (not a scaffold) → uses `RegisteredRegion` directly.

**Per-region split / special:**
- **Webcam `:170`** — `focusFramed=false` (full-bleed video keeps its hand `padding(8)`), `fieldFramed=true`
  (cam-picker + bar). **PLUS** the non-scaffold **full-focus path** `WebcamScreen.kt:197-224` whose
  `WebcamBackBar` (`FootButtonBar`) must get explicit padding once the component is flush (pattern 3).
  Decide: cam-picker's current `8+8` gap → keep frozen vs collapse to region 8 (owner UAT).
- **OutputToggleControl `:67`** — settings-class detail; `fieldFramed=false` (keeps its `padding(16)/
  spacedBy(12)` reading layout); its `FootButtonBar :123` then needs explicit padding (pattern 3).

**Frozen — opt out, byte-for-byte:**
- **About `:105`, Settings `:186`** — ⚠ **`focusFramed=true, fieldFramed=false`** (NOT both off): their
  focus is a real `FocusFrame` that must keep the 8dp frame; their field is a `verticalScroll` reading
  column at `padding(16,12)` that must NOT be region-framed. *(Codex BLOCKER #1 fix.)*
- **Splash `:81`, ConfirmGuard `:93`** — field-only centred specials → both off.
- **Files `SpoolWarningGuard :773`** — nested guard/overlay → both off.
- **Gallery `:272`, OldMoveScreen** — debug → both off.

## `FocusFramePlacement` retirement worklist (compile-breaking if missed)

Removing the enum breaks every reference. Beyond Spool/Files/Console, **`OldMoveScreen.kt:250,298`**
passes `FocusFramePlacement.Composed` twice (added in Phase 1) and imports it — remove both args + the
import. Delete `FocusFramePaddingTest`. Grep `FocusFramePlacement|focusFramePadding` must return zero
hits before build. *(Codex BLOCKER #2 fix.)*

## Intended visual changes (everything else pixel-identical)

1. **Spool focus card** — 16dp horizontal (Box 8 + internal 8) → uniform 8dp, aligning with the
   sort/filter tiles. *(The Phase-1-deferred bug.)*
2. **Console card top** — 0dp → 8dp, registering on the grid.
3. **Off-grid gap normalisations (enumerate in plan Task 1, confirm at UAT):** pattern-2 screens whose
   current padded-body-+-foot-bar gap is 16dp (candidates: Probe, Tilt, Extrude, PrintStatus fields);
   Macros' `ListBlock(vertical=4)` rhythm `:508,656`; Temperature Adjust's two stacked `FootButtonBar`s
   `:690,698` inter-bar gap. Each becomes the uniform region 8dp.

## Per-screen edits (driven by the audit; not blanket)

- **Direct-child screens:** delete `ListBlock` top-pad → flush; components flush. (Pixel-identical.)
- **Helper-owned-framing screens:** dissolve the helper's framing Column; hoist children to direct region
  children; drop helper `padding`/`spacedBy`. (PrintStatusField, Probe, Tilt, Extrude.)
- **Embedded/non-region uses:** add explicit padding at the call site (Temperature `SensorPickerFocus`
  ListBlock; Webcam full-focus bar; OutputToggleControl bar).
- **Spool/Files composed focus:** delete the `Box` wrapper + per-row `SortRow`/`FilterRow` paddings →
  flush `FocusFrame(weight1)` + `SortRow()` (+ `FilterRow()`).
- **Console:** swap the hand-rolled inner `Column(fillMaxSize)` for `RegisteredRegion(fillMaxSize)`;
  flush `FocusFrame(weight1)` + `FootButtonBar`. Keep the outer `Box(bg)` + `BoxWithConstraints` (the
  `rememberUnitGrid` source) and the pinned-height feed wrapper.
- **About/Settings:** add `fieldFramed = false` (focus stays framed).
- **Frozen specials:** add the opt-out flags per the audit.

## Docs / law updates

- `FocusFrame` KDoc — frame is region-owned; component flush; `FocusFramePlacement` retired.
- `COMPONENTS.md` — add a `RegisteredRegion` entry; update §FocusFrame/§ListBlock/§FootButtonBar/
  §SortFilterControlRow to "the region owns the 8dp frame; components are flush."
- `LAYOUT.md` R26 — the 8dp frame is owned once per region by `RegisteredRegion` (default-on in
  `ScreenScaffold`); components never add frame padding; replace the Phase-1 `Region/Composed` bullet.

## Verification

Geometry is not host-testable → gate is on-device UAT on **flox + moto** (both ABIs), driven by the plan's
per-screen audit table (before/after per screen):

1. **Direct-child + Files:** pixel-identical (CalibrationHub, Printers, SystemPage, SystemInfo, Outputs,
   Move, FineTune, ScrewsTilt, BedMesh, Files).
2. **Pattern-2 screens:** correct (no double-pad, no missing frame) and the enumerated 16dp→8dp
   normalisations look right (Probe, Tilt, Extrude, PrintStatus ×4, Macros, Temperature).
3. **Fixes:** Spool card full-width-to-8dp aligned with its tiles; Console top at 8dp, feed tight to bar.
4. **Embedded uses:** Temperature sensor-picker list keeps its inset; Webcam full-focus back bar + cam
   picker + OutputToggle bar all still padded.
5. **Frozen unchanged:** About, Settings (card still framed, reading field unchanged), Splash,
   ConfirmGuard, Files spool-warning guard, Gallery — all as before.
6. **Landscape cross-region alignment** (load-bearing): focus card bottom aligns with field
   `FootButtonBar` bottom on every two-region screen.

Host: a tiny test asserting `RegionInset == 8.dp` && `RegionGap == 8.dp` replaces the deleted
`FocusFramePaddingTest`. Wrap the `@Preview` bare-`FocusFrame` renders (`DesignKitComponentPreviews`,
`DesignKitLayoutPreviews`, `WebcamPreviews`) in `RegisteredRegion` so they keep the inset (cosmetic).

## Decision log

- **Frame ownership:** hoisted to the **region** (`RegisteredRegion`), reversing Phase 1's component
  self-frame (Phase 1 was the deliberate recon step) — owner, 2026-06-14.
- **Mechanism:** `RegisteredRegion` + `ScreenScaffold` default-on with per-region `focusFramed`/
  `fieldFramed` opt-out booleans (opt-out so new screens are correct by default) — owner, 2026-06-14.
- **"sort/filter rows move to field ownership":** registration ownership only; rows stay physically in
  the focus column (owner, Option A).
- **Control rows dockable under either region:** by construction, no new composable (owner).
- **Frozen set:** About (`field` off), Settings (`field` off), Splash, ConfirmGuard, Files SpoolWarning,
  Gallery, OldMove, Webcam (`focus` off) — most reworked/deleted later (owner, 2026-06-14).
- **Scope is a per-screen migration, not a blanket sweep; off-grid screens normalise to 8dp as intended
  conformance changes, enumerated for UAT** — revised after Codex review (gpt-5.5/xhigh, 2026-06-14).
- **Codex review findings folded:** #1 About/Settings `focusFramed=true,fieldFramed=false`; #2 OldMove +
  test in the enum-retirement worklist; #3 per-screen geometry audit replaces the blanket pixel-identical
  claim; #4 dissolve helper-owned framing Columns (PrintStatus et al.); #5 full 30-site scaffold audit
  incl. nested (OutputToggleControl, Files SpoolWarningGuard, Gallery); #6 Webcam both branches + cam-
  picker decision; #7 embedded `ListBlock`/`FootButtonBar` get explicit insets.
- **Final plan** to be Codex-reviewed via the non-reaping background path before execute
  (`[[codex-review-final-plans]]`).
