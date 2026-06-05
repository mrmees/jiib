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
