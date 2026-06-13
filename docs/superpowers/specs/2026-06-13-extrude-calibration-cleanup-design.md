# Extrude + Calibration Hub Cleanup — Design

**Date:** 2026-06-13
**Scope:** Small, self-contained UI tweaks to two existing screens. No data-model, holder, or
Field/list changes. No new component classes.

## Goal

Two unrelated screens get a focused polish pass:
- **Extrude** — center the Focus readouts, correct footer button intents, wire the Spool button to
  the real Spoolman page with the color-reactive spool icon.
- **Calibration Hub** — adopt the owner's bucket-assigned routine icons, and restructure the Focus so
  the header tracks the current selection, the description fills the body, and the Open button is
  bottom-docked.

---

## Screen 1 — Extrude (`app/.../ui/extrude/ExtrudeScreen.kt`)

### 1.1 Center Focus readouts
The Distance and Speed readouts in the Focus right column (label / hero value / unit, each a
`BasicTextField`) currently lay their column content out start-aligned. Change the inner layout of
**both** readout composables to center horizontally:
- Column `horizontalAlignment = Alignment.CenterHorizontally`.
- The `BasicTextField` value text centered.

Only the two readout composables change. The readout frames/borders, the Extrude/Retract buttons,
and the left Focus column are untouched.

### 1.2 Footer button intents
In the `FootButtonBar`:
- **Back:** `Intent.Neutral` → `Intent.Accent`
- **Load:** `Intent.Accent` → `Intent.Warn`
- **Unload:** `Intent.Warn` (unchanged)

Result: Back = accent (nav), Load + Unload = caution amber (hazardous-but-in-process). Consistent
with the R5 intent scheme. No label/icon/handler changes.

### 1.3 Spool button → Spoolman nav + reactive icon
The Field's Spool button currently renders a `inventory_2` Material symbol with grey text and fires a
"coming soon" info toast.

Replace with:
- **Icon:** the color-reactive `SpoolGlyph` (the `img/spool.svg`-based render used on the home
  launcher, filament band tinted to the loaded spool color; empty when no spool loaded). **No text
  label.**
- **Action:** `onClick` navigates to `NavDest.Spool` (the existing Spoolman page). Remove the stub
  toast and its associated copy.

The button keeps its current cell and size within the two-button row; the adjacent nozzle-temp button
is unchanged. The Spool button is no longer conditionally a "placeholder."

---

## Screen 2 — Calibration Hub (`app/.../ui/calibration/CalibrationHubScreen.kt`, `app/.../designsystem/icons/DinghyIcons.kt`)

### 2.1 Routine icon swap
Update the five `Routine*` tokens in `DinghyIcons.kt` to the owner's bucket assignments
(`img/material-icon-bucket.json` notes):

| Token | Old ligature | New ligature |
|---|---|---|
| `RoutineProbeCalibrate` | `straighten` | `detector` |
| `RoutineBedMesh` | `grid_on` | `blur_linear` |
| `RoutineScrewsTilt` | `architecture` | `rule_settings` |
| `RoutineZTilt` | `vertical_align_center` | `linear_scale` |
| `RoutineQgl` | `crop_square` | `linked_services` |

The full Material Symbols Outlined font is bundled (`res/font/material_symbols_outlined.ttf`, ~10.6 MB),
so every ligature renders directly — no SVG-alternate fallback needed. Update the "OWNER-LOCKED" date
comment to reflect this 2026-06-13 revision.

### 2.2 Focus restructure: dynamic header + filling description + docked button
The Focus is rebuilt from "static title + a body that repeats the routine's icon/title/description +
Open button" into three zones:

1. **Header (dynamic).** The `FocusFrame` title + icon reflect the **selected routine** rather than a
   static "Calibration". Selecting a different routine in the Field updates the header. (This is the
   hub's title/icon law — the header tracks selection, distinct from a standard destination page where
   the header echoes the entry button.)
2. **Body (description fills space).** Delete the redundant icon and title from `HubRoutineFocus`.
   The body becomes the routine **description only**, allowed to grow and fill the available vertical
   space.
3. **Open button (bottom-docked).** The green `Intent.Go` Open button is pinned to the bottom of the
   Focus (e.g. `Spacer(Modifier.weight(1f))` above it, plus bottom padding), so it sits in the same
   location regardless of description length, orientation, or screen size.

The Field list (all five routines, supported-first ordering, tap-to-select, greyed-but-tappable
unsupported rows) and the single Back footer button are unchanged.

---

## Out of scope
- No changes to holders / view-models / data models.
- No changes to the Field list rows or ordering.
- No changes to calibration sub-routine screens.
- No new component classes (existing `FocusFrame`, `FootButtonBar`, `SpoolGlyph`, button-intent
  helpers are reused as-is).

## Verification
- **Extrude:** readouts visually centered in both portrait and landscape; Back renders accent, Load +
  Unload render amber; tapping Spool opens the Spoolman page and shows the reactive spool (tinted when
  a spool is loaded).
- **Calibration Hub:** each of the five routines shows its new glyph in the Field and in the header;
  selecting a routine updates the header title + icon; the description fills the body and the Open
  button stays bottom-docked across selections/orientations; Open still launches the routine.
- Build + existing test suite green; on-device check on flox + moto.
