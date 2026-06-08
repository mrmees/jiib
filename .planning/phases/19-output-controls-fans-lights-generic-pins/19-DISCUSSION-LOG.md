# Phase 19: Output Controls — Fans, Lights & Generic Pins - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-07
**Phase:** 19-output-controls-fans-lights-generic-pins
**Areas discussed:** Output-type icons, Empty/no-outputs state, Navigation entry point, LED Off action

> Most of Phase 19 was pre-decided by the staging doc (`19-output-controls-staging.md`, sourced from
> the mis-titled `parallel_dinghy/phase-18-outputs-staging.md`). Discussion covered only the four
> gaps that doc left open for the owner.

---

## Output-type icons

| Option | Description | Selected |
|--------|-------------|----------|
| I'll curate the bucket myself | Owner adds glyphs to material-icon-bucket.json; planner reads them | |
| Use my candidate slate | Lock Claude's proposed glyphs verbatim | |
| Slate as starting point, I'll tweak | Accept most, override specific ones | ✓ (owner gave full explicit picks) |

**User's choice:** Explicit per-type glyph selection, overriding parts of the candidate slate:
`heater_generic`→`mode_heat`, `fan_generic`→`mode_fan_2`, LED family→`lightbulb_2`, `servo`→`cyclone`,
`output_pin` (both)→`check_box`, `pwm_tool`→`vital_signs`, Outputs tile→`output`.
**Notes:** Owner additionally directed reassigning the part-cooling fan icon (Fine-Tune / Phase 17)
from `mode_fan` → `air` to free fan-glyph space for the new generic-fan icon — a required cross-phase
side-change (D-08). Per icon law, every glyph is owner-chosen; planner must verify each resolves in
the bundled Material Symbols ttf and must not substitute (D-09).

---

## Empty / no-outputs state

| Option | Description | Selected |
|--------|-------------|----------|
| Hide the entry tile entirely | Capability-gate the Outputs tile away when zero whitelisted outputs | ✓ |
| Show tile + empty screen | Tile always present; empty-state copy on tap | |

**User's choice:** Hide the entry tile entirely.
**Notes:** Consistent with Phase-6 capability gating; no dead-end tile on minimal printers.

---

## Navigation entry point

| Option | Description | Selected |
|--------|-------------|----------|
| App Drawer tile | Swipe-up drawer tile, like Calibration/Macros/Console | ✓ |
| Home/Status tile | Tile on main home surface | |
| Both drawer + home | Drawer plus home shortcut | |

**User's choice:** App Drawer tile.
**Notes:** Consistent with established navigation; uses the `output` icon and is capability-gated.

---

## LED 'Off' action

| Option | Description | Selected |
|--------|-------------|----------|
| Add explicit Off | Off action (black / brightness 0) alongside color + brightness | ✓ |
| No Off — brightness 0 = off | Color + brightness only | |

**User's choice:** Add explicit Off.
**Notes:** Matches the Off action every other output type has; resolves the staging doc's LED silence.

## Claude's Discretion

- Exact control-page composition within the established `ScrubberPage` / `ColorWheel` patterns.
- All command-syntax / value-range / discovery-source resolution — research/planner work, not owner
  decisions (staging "Open Planner Work").

## Deferred Ideas

- Search/filter, hide/favorite/reorder, per-index addressable LED, "all off" action,
  `manual_stepper` / `pwm_cycle_time` controls — all out of Phase 19 per staging.
- Owner may later curate the chosen output glyphs into `material-icon-bucket.json` (non-blocking).
