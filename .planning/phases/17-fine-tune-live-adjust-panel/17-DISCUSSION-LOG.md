# Phase 17: Fine-Tune / Live-Adjust Panel - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-05
**Phase:** 17-fine-tune-live-adjust-panel
**Areas discussed:** Scope & cockpit-vs-lean, Power-user tier, Z babystep mechanics, Per-control behavior
**Note:** Pre-discussed out of dependency order (parallel track) while Phase 15 theme clarifications continue elsewhere. Outside ideation input gathered from Codex (gpt-5.x) before the discussion (first run crashed mid-research; a leaner retry delivered a ranked must-have/nice-to-have/belongs-elsewhere answer that informed the options below).

---

## Scope & cockpit-vs-lean

| Option | Description | Selected |
|--------|-------------|----------|
| Lean tuner + live readouts | New tunables only; temps/pause linked not duplicated | |
| Full mid-print cockpit | Embed temp setters + pause/resume too | |
| Minimal tunables only | Just babystep/speed/flow/fan, no power tier | |
| **(User reframe)** | **Split into Motion / Extrusion categories** | ✓ |

**User's choice:** "Probably split out into a motion category and an extrusion category. One about things that move the head around, the other about things that affect the filament."
**Notes:** Resolved into a lean, failure-mode-organized tuner. Follow-up edge calls settled in prose: (1) babystep OUT → moves to a Phase-16 Print-Status conditional control for the first ~10 layers; (2) temperature = clickable readout that jumps to the Temp panel (no setter here); (3) part-cooling fan only — generic/aux fans → Phase 18.

---

## Power-user tier (v1 inclusion)

| Option | Description | Selected |
|--------|-------------|----------|
| Pressure advance | SET_PRESSURE_ADVANCE ↔ extruder.pressure_advance | ✓ |
| Motion limits — accel / max-velocity / SCV | SET_VELOCITY_LIMIT ↔ toolhead.* | ✓ |
| Firmware retraction (if present) | SET_RETRACTION, capability-gated to [firmware_retraction] | ✓ |
| Cancel current object (EXCLUDE_OBJECT) | Destructive; needs object picker + confirm | (→ v2) |

**User's choice:** Pressure advance, Motion limits, Firmware retraction IN; "add object exclusion to the wishlist for the next milestone."
**Notes:** Object exclusion deferred to the v2/next-milestone seed list — out of v1 scope.

---

## Z babystep mechanics

| Option | Description | Selected |
|--------|-------------|----------|
| Discuss babystep mechanics for Phase 17 | — | (moot) |

**User's choice:** Babystep removed from Phase 17 entirely (see Scope). It moves to Phase 16 as a Print-Status conditional control (first ~10 layers).
**Notes:** Area became moot for 17. Codex's mechanics (incremental ±0.01/0.05, "nozzle closer/away" labels, live offset readout, session-only) carried forward as a forward-note for Phase 16 in CONTEXT.md Deferred.

---

## Per-control behavior

| Question | Options | Selected |
|----------|---------|----------|
| Availability | Always available / Print-only | **Always available** |
| Reset affordances | Per-control reset / No reset | **Per-control reset** (speed/flow→100%; limits+PA→config default) |
| Flow factor range | Wide 50–150% / Conservative 80–120% | **Wide 50–150%** |

**User's choice:** Always available; per-control reset; wide flow range.
**Notes:** Confirmation model = immediate dispatch + printer-object state-flip confirm, no ConfirmGuard (Move/Extrude pattern) — captured as default, not separately asked.

---

## Claude's Discretion

- Exact ranges/steps/defaults for accel / max-velocity / square-corner-velocity (verify vs Klipper at plan time).
- Whether PA SMOOTH_TIME is exposed alongside ADVANCE.
- Whether the motion-limit group is expanded or behind an "advanced" disclosure.
- Precise capability-gate predicates per control.
- Exact on-screen layout realization (tabs vs sections vs Focus/Field) — deferred to planner against Phase 15/16.

## Deferred Ideas

- Z babystep → Phase 16 (Print-Status conditional, first ~10 layers).
- Mid-print object exclusion (EXCLUDE_OBJECT) → v2 wishlist.
- Layout + entry point → planner / Phase 15+16.
- Temp setters → Phase 5 Temp panel; generic fans/LEDs/pins → Phase 18; Pause/Resume → Phase 7 gutter.

---
---

# Revision — 2026-06-06 (staging fold-in)

**Date:** 2026-06-06
**Areas discussed:** Interaction model, Move-rework scope, Icons, Control set + reset
**Trigger:** `/gsd-discuss-phase 17 review phase-17-fine-tune-staging.md`. Phase 16 is now COMPLETE, so the
previously-LOOSE layout/entry point could be locked. The staging note (`../parallel_dinghy/phase-17-fine-tune-staging.md`,
newer than the 2026-06-05 discussion) introduced value-tile interaction, `minimum_cruise_ratio` + `smooth_time`,
icons/increments/reset, and a firmware-retraction mini-screen — reconciled against the original CONTEXT.md below.

---

## Interaction model (resolves the original ScrubberPage D-09 conflict)

| Option | Description | Selected |
|--------|-------------|----------|
| Pure nudge (arrows only) | −/+ step the fixed increment; tap inert; long-press = reset. Big sweeps = many taps (accepted). | ✓ |
| Nudge + tap-to-scrub | Tapping the value opens ScrubberPage for big moves | |
| Nudge + coarse/fine toggle | Tap cycles the increment (C2 mode-a) | |

**User's choice:** Pure nudge — arrows only.
**Notes:** Value tiles replace per-control ScrubberPage. Tile is a C2-*derived* 3-cell (arrows do the action, tap inert, long-press = reset) — intentionally diverges from C2 mode-(b)'s "tap = adjust".

---

## Move-rework scope

| Option | Description | Selected |
|--------|-------------|----------|
| Component + Move retrofit + Z rework | Build shared C2 picker, retrofit Move, do Move-Z vertical rework | |
| Component + Move retrofit, defer Z | Build C2, retrofit Move, defer C3 | |
| Fine-Tune only | Build tile shared-ready, wire only in Fine-Tune; C2/C3 stay deferred todos | ✓ |

**User's choice:** Fine-Tune only.
**Notes:** Todos `2026-06-05-increment-picker-3cell-component.md` (C2) and `2026-06-05-move-z-vertical-layout-rework.md` (C3) reviewed, NOT folded — remain deferred.

---

## Icons

| Option | Description | Selected |
|--------|-------------|----------|
| Per-icon drawables (established pattern) | Export Material Symbols to res/drawable; reuse add/remove for ± | ✓ |
| Text/label-only | No tile icons | |

**User's choice:** Per-icon drawables — and (follow-up) **`img/material-icon-bucket.json` is the canonical icon registry going forward**; for any NEW need, ask the owner rather than inventing.
**Notes:** CORRECTION — the owner had ALREADY chosen a per-control glyph for every Fine-Tune control in the staging note §Icons (Speed→`speed`, max-vel→`arrow_shape_up_stack_2`, accel→`sprint`, cruise→`directions_boat`, SCV→`rounded_corner`, flow→`output_circle`, PA→`text_select_move_forward`, smooth→`avg_time`, fan→`mode_fan`, FW-retract→`input_circle`). Claude initially mis-flagged these as bucket "gaps"; they are authoritative choices that just need folding into the bucket. Generic ± = `add`/`remove`. Open: fold chosen glyphs into bucket; verify two unusual ligatures (`arrow_shape_up_stack_2`, `text_select_move_forward`); reconcile bucket's `sprint` note (bucket=speed, staging=accel). Tracked as todo `2026-06-06-phase-17-fine-tune-icons-bucket.md`. `img/spool.svg` recorded as the project-wide filament-roll graphic (D-17b).

---

## Control set + reset (ratified from staging)

| Item | Decision | Selected |
|------|----------|----------|
| `minimum_cruise_ratio` | Add to Motion (`SET_VELOCITY_LIMIT MINIMUM_CRUISE_RATIO`, step 5pp) | ✓ |
| `smooth_time` | Separate Extrusion control (`SET_PRESSURE_ADVANCE SMOOTH_TIME`, step 0.01s) | ✓ |
| Firmware retraction | Own mini-screen (4 controls, no Z-hop), gated on `[firmware_retraction]` | ✓ |
| Reset | Long-press value → immediate, no prompt, config baseline (speed/flow→100%) | ✓ |
| Busy model | **Busy whole group** while a command confirms (vs per-tile / optimistic) | ✓ |

**User's choice:** All ratified; busy-the-whole-group chosen for simplicity + no race (serialized rapid nudging accepted).
**Notes:** Part-fan reset baseline is genuinely fuzzy (Klipper `fan` has no persistent runtime configured-speed) → planner discretion; intent = "never assumed off".

## Claude's Discretion (revision additions)
- Exact ranges/clamping per tuner; exact `configfile.settings.*` baseline paths; part-fan reset-baseline resolution.
- Motion-limit group expanded vs "advanced" disclosure; 5-tile touch-friendliness in portrait AND landscape.
- Group-screen readout strip placement (light, links-not-setters); Material Symbols ligature resolution from the bucket.

## Deferred Ideas (revision additions)
- C2 shared 3-cell increment-picker full build + Move retrofit → todo `2026-06-05-increment-picker-3cell-component.md`.
- Move-Z vertical-layout rework (C3) → todo `2026-06-05-move-z-vertical-layout-rework.md`.
- User-customizable tuner increments → future Settings section.
