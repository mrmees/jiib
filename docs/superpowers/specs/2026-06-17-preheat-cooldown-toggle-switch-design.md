# Preheat⇄Cooldown button + switch-style ToggleRow

**Date:** 2026-06-17
**Status:** approved (owner sign-off in brainstorming session)
**Scope:** two focused UI fixes — a state-driven foot button and an on/off control consolidation.

## Problem

1. The idle home (PrintStatus standby) foot bar always shows **Preheat**, even when heaters are already on. There is no quick way to turn everything off from that screen.
2. On/off list controls are inconsistent: Extrude uses the canonical `ToggleRow` (text "On/Off" pill); Macros manage-mode uses a one-off `ListRow` + icon (`CheckCircle` / `UnbookmarkedMacro`). The owner wants one formalized toggle style.

## Decisions (owner-locked)

- **Cooldown icon:** Material Symbol ligature `mode_heat_off` (literal inverse of the `chair_fireplace` preheat glyph). Owner-specified — do not substitute.
- **Cooldown intent:** `accent` (neutral). It removes a hazard, so amber/Warn is wrong. Retunable on-device.
- **Canonical on/off look:** **switch widget** (sliding knob), replacing the text pill. Switch position *is* the state — no redundant On/Off text.
- **Scope:** "Macros only" — restyle the shared `ToggleRow` (Extrude inherits the new look automatically) and migrate Macros manage-mode onto it. **Do NOT touch** Printers `SecureToggleRow` or Settings `DenseToggleRow` this pass.

## Change A — Preheat ⇄ Cooldown on the idle foot bar

**Where:** `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt`, idle branch of the foot-bar `when` (~L121–149), and its caller `PrintStatusScreen.kt`.

**Behavior:**
- "On" is defined exactly as elsewhere: a heater with `target > 0.0`. Reuse the existing helper `activeHeaterKeys(heaters)` (in `HomeDigest.kt`) — `anyHeaterOn = activeHeaterKeys(heaters).isNotEmpty()`.
- **`anyHeaterOn == true`** → render a **Cooldown** `FootAction`: label from new string `home_foot_cooldown` ("Cooldown"), icon `DinghyIcons.FootCooldown`, intent `accent`, onClick = `onCooldown`.
- **`anyHeaterOn == false`** → render **Preheat** exactly as today (Intent.Warn, `DinghyIcons.FootPreheat` / `chair_fireplace`, onClick = `onPreheat`). No other change to the idle branch.
- The button flips back to Preheat automatically once all targets reach 0 (state is observed, not latched).

**Wiring (keep the Field dumb):**
- `PrintStatusField` gains `anyHeaterOn: Boolean` and `onCooldown: () -> Unit` params.
- `PrintStatusScreen` computes `anyHeaterOn` from observed `PrinterState.heaters` and provides `onCooldown` = dispatch of the **existing** `Commands.cooldown` spec (`catalogId = "KGC-TURN_OFF_HEATERS"`, gcode `TURN_OFF_HEATERS`, gated `ObjectPresent("extruder")`). **No new CommandSpec, no catalog.json / printer-matrix.json change** — avoids the D-10 command-catalog drift trap.

**New assets:**
- `DinghyIcons.FootCooldown` = `IconRef.Ligature("mode_heat_off")` (with a sensible alternate name, following the `FootPreheat` pattern), registered in the `DinghyIcons` registry so `verify_ligatures.py` scrapes it.
- String resource `home_foot_cooldown` = "Cooldown".
- If `mode_heat_off` is absent from the bundled Material Symbols subset, add it to the font generation input so `verify_ligatures.py` passes.

## Change B — Switch-style `ToggleRow` + Macros migration

**Restyle the canonical control:** `app/src/main/java/works/mees/dinghy/designsystem/components/ToggleRow.kt`
- Replace the trailing text "On/Off" pill (and its `pillText`-style pure helpers) with a tokenized **switch**: a rounded-capsule track + a circular knob.
  - **On:** track filled `accent` (or `accentSoft` per token fit), knob aligned to the trailing edge.
  - **Off:** track `outline`/neutral, knob aligned to the leading edge.
  - One-shot knob-position transition on toggle (a single `animate*AsState`) — cheap, allowed under the "no continuous/looping animation" rule. No looping/breathing.
- Built from Compose shapes (`Box`/`Canvas`) + **role tokens only** (no raw colors) — not Material3 `Switch` — to keep token control and stay within the Adreno-320 budget.
- **Preserve:** left-side label (Geist SemiBold) + optional subLabel, `Modifier.toggleable(role = Role.Switch)`, disabled alpha 0.38 + `disabled()` semantics, full-width row + border. Sizing in `U` / existing row metrics; switch fits the 1U control cap.
- All existing call sites keep the same `ToggleRow(label, subLabel?, checked, onToggle, enabled)` signature — Extrude's runout-sensor and macro-pin rows (`ExtrudeScreen.kt`) inherit the switch look with **no edit**.

**Migrate Macros manage-mode:** `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt`, `MacroManageField` (~L650–714)
- Replace the per-macro `ListRow` + trailing `CheckCircle`/`UnbookmarkedMacro` icon with `ToggleRow`:
  - `label` = macro name, `checked` = bookmarked, `onToggle` = the existing bookmark/unbookmark action.
- Confirm during implementation that manage-mode rows do nothing but toggle bookmark state (recon indicates yes); if a row also drives Focus selection, preserve that — but the expectation is a clean toggle swap.
- Remove now-unused manage-mode icon plumbing if nothing else references it.

**Out of scope:** Printers `SecureToggleRow`, Settings `DenseToggleRow` — untouched this pass (owner "macros only").

## Docs

- Update `docs/ui_design/COMPONENTS.md` ToggleRow section: the canonical trailing affordance is now a **switch**, not a text pill. Note Macros manage-mode as a current consumer.

## Verification

- Build via `E:\Android\gw.bat` (Windows-side); `verify_ligatures.py`, `FontConformanceTest`, and the existing suite green.
- Install the matching ABI slice on **both** flox (Nexus 7 2013) and moto (Moto G Play 2024).
- Owner UAT:
  - Idle home with all heaters off → **Preheat**. Set a heater target → button becomes **Cooldown**; tap → `TURN_OFF_HEATERS` fires, all targets drop to 0, button returns to **Preheat**.
  - Extrude runout-sensor + macro-pin rows and Macros manage-mode rows all render the **same switch** and toggle correctly (incl. disabled state).

## Risks / notes

- `verify_ligatures.py` is the gate that catches a missing `mode_heat_off` glyph — heed it (3 glyphs were caught this way in the FootBar conformance pass).
- The switch is the first "moving" affordance in the app; keep the transition to a single cheap one-shot to respect the motion rule and the perf floor.
