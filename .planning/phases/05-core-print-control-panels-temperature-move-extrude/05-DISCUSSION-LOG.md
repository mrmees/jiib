# Phase 5: Core Print-Control Panels — Temperature, Move, Extrude - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-31
**Phase:** 5-Core Print-Control Panels — Temperature, Move, Extrude
**Areas discussed:** Temp target entry & presets, Move/jog layout, Temperature graph, Extrude controls & safety

---

## Temperature target entry & presets

Initial framing offered a "numeric keypad page" — then CAUGHT that the hi-fi mockups + README #7
LOCK this to the fill-bar scrubber single-setting page (the design system has no 0-9 keypad).
Re-asked as an explicit conflict-resolution.

| Option | Description | Selected |
|--------|-------------|----------|
| Follow the locked scrubber | Exact entry = Phase-3 ScrubberPage (tap-to-set + steppers), per single-setting page #7 | ✓ |
| Add a 0-9 keypad (deviation) | New keyboard-free numeric keypad, treat TEMP-02 literally | |

**User's choice:** Follow the locked scrubber. ("exact keypad entry" = numpad-style scrubber, not a telephone pad.)

| Option | Description | Selected |
|--------|-------------|----------|
| Built-in material set, fixed | PLA/PETG/ABS/TPU + Cooldown, hardcoded for v1 | ✓ |
| Built-in set, user-editable | Same but editable temps + persistence | |
| You decide | — | |

**User's choice:** Built-in fixed material set + cooldown.
**Notes:** Editing deferred to a future phase. Presets + Cooldown live in the temp-graph gutter (mockup #9).

---

## Move / jog layout

**Not open-discussed — LOCKED by `docs/ui_design/images/04-move.png` + README #4.** Captured verbatim
into CONTEXT D-03 (3×3 XY jog pad, Z row, 6-up distance selector, Home/Disable/Back gutter, homed/unhomed
color gating, amber Override). The discuss workflow mandates treating mockups as LAW — no re-litigation.

---

## Temperature graph

**Not open-discussed — LOCKED by `docs/ui_design/images/09-temperature-graph.png` + README #9.** Captured
into CONTEXT D-04/D-05/D-06 (multi-trace overlaid on one axis, dashed setpoint, sensor colors; EXTENDS the
GraphView primitive; G-1 fix via `server.temperature_store` backfill + stable Y-range; mandatory Adreno-320
perf re-measure).

---

## Extrude controls & safety

| Question | Selected |
|----------|----------|
| Cold-extrude guard | Disable + explain (gate on live `can_extrude`) ✓ |
| Extrude panel structure | Move-style panel (Extrude/Retract + distance + speed) ✓ |
| Load/unload macro absence | Show buttons + informational popup if missing ✓ |

**User's choice:** all three as above.
**Notes:** User asked whether Moonraker exposes `min_extrude_temp` — answered: gate on the live `can_extrude`
boolean; numeric threshold via `configfile.settings.extruder.min_extrude_temp`. Both flagged as research-verify.
Multi-extruder tool selector = capability-gated (only shown when >1 extruder).

## Claude's Discretion

- Exact preset temperatures (PLA/PETG/ABS/TPU pairs), extrude distance set + default speeds, graph time-window.
- Toolhead-position source field (gcode_position vs toolhead.position) — researcher picks.

## Deferred Ideas

- User-editable preset temperatures (editor + persistence) — future phase.
- Fan speed single-setting page — not in Phase 5's 12 reqs; later control-surface phase.
- A sanctioned 0-9 keypad component — only if added to the design language later.
