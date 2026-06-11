# Phase 27: Motion + Calibration - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-10
**Phase:** 27-motion-calibration
**Areas discussed:** Move screen anatomy, Calibration hub & nav shape, Wizard flow grammar, Bed Mesh surfaces

---

## Todo Cross-Reference

| Option | Description | Selected |
|--------|-------------|----------|
| Fold C3 Move Z-vertical todo | The Move rebuild is the natural home for the rework | |
| Leave it pending | Keep the todo open; Move migrates without folding it | ✓ |

**User's choice:** Leave it pending — though the subsequent discussion locked vertical Z anyway
(D-01); the todo stays open as a tracker until the shipped result is owner-verified.

---

## Move screen anatomy

### Q1 — Z control placement / vertical (C3)

| Option | Description | Selected |
|--------|-------------|----------|
| Vertical Z column | Z becomes a vertical up/down stack beside/with the jog pad | ✓ (with addition) |
| Keep horizontal Z row | Z stays a horizontal Field row; C3 stays open | |
| Vertical in landscape only | Vertical where landscape affords; portrait keeps the row | |

**User's choice:** "Vertical z column. increment picker needs to change to a simple plus/minus
button with a display readout, giving us 3 equally spaced rows, just like the vertical z column."

### Q2 — Composition of pad + columns

| Option | Description | Selected |
|--------|-------------|----------|
| Pad + columns side-by-side | One spatial cluster, FootButtonBar below | |
| Focus=pad, Field=columns | Strict Focus/Field split | |
| You decide | Planner picks for 5U fit | |

**User's choice:** Free-text — open to foot buttons under the columns; flagged the
portrait/landscape tension: full square pad fine in landscape (tablet) but crowds the columns in
portrait; proposed capping the pad (~60%) in portrait.

### Q3 — Layout confirmation

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, that's it | Lock the shape; planner tunes ratios | |
| Close, but adjust | Correct a detail | ✓ |
| Rethink it | Walk through differently | |

**User's choice:** "XY pad capped at 60% HEIGHT. Ratio rule should cap the width appropriately in
portrait." Reiterated (interrupt message): the goal is to leave enough room for the Z controls when
the X/Y pad fights to maintain its square ratio.

### Q4 — Distance stepper semantics + pad internals

| Option | Description | Selected |
|--------|-------------|----------|
| Yes to both | +/− cycles fixed distance set; pad internals carry over restyled | ✓ |
| Fixed set, but change pad internals | | |
| Different stepper behavior | | |

**User's choice:** Yes to both.

---

## Calibration hub & nav shape

### Q1 — Hub shape

| Option | Description | Selected |
|--------|-------------|----------|
| List hub, routines push | ListRow list; routines push as nav routes | ✓ (with addition) |
| Spoolman-style collapse | Field list + routine surface in Focus | |
| Keep tile grid, restyled | | |

**User's choice:** "do #1 for now. Screen should come up with the list of items in the field, the
focus should include the icon / title for the procedure, and a brief description of how it works."

### Q2 — Unsupported routines: grey vs hide

| Option | Description | Selected |
|--------|-------------|----------|
| Hide unsupported | Consistent with P24/P26 hide-not-grey | |
| Keep greyed-but-listed | Preserve Phase-9 override, dimmed + openable | ✓ |
| Greyed, not tappable | Show all, inert when unsupported | |

**User's choice:** "2 - eventually this will go away, but I need the visual reminder for now."

### Q3 — Launch affordance + first-entry selection

| Option | Description | Selected |
|--------|-------------|----------|
| Open button in Focus | Row tap selects; accent Open launches; first supported pre-selected | ✓ |
| Row tap pushes directly | | |
| Second tap launches | | |

**User's choice:** Open button in Focus (Recommended).

---

## Wizard flow grammar

### Q1 — Probe-Calibrate TESTZ controls

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, match Move | Vertical nudge column + vertical step column, one grammar app-wide | ✓ |
| Keep current arrangement | Horizontal selector over arrows, restyled | |
| You decide | | |

**User's choice:** Yes, match Move (Recommended).

### Q2 — State-adaptive action semantics → FootButtonBar

| Option | Description | Selected |
|--------|-------------|----------|
| Carry over verbatim | Same states/intents/Back suppression; nav back blocked while Active | ✓ |
| Adjust the states | | |

**User's choice:** Carry over verbatim (Recommended).

### Q3 — Tilt + Screws-Tilt

| Option | Description | Selected |
|--------|-------------|----------|
| Pure restyle | Same flows/data; bed viz spatial carve-out; screw rows → ListRow | ✓ |
| Rework something | | |
| You decide | | |

**User's choice:** Pure restyle (Recommended).

---

## Bed Mesh surfaces

### Q1 — Profile management shape

| Option | Description | Selected |
|--------|-------------|----------|
| Profiles AS the Field list | Field = profile list; Load dialog dies; Save-name → takeover | ✓ |
| Keep buttons + takeovers | Action-centric Field; dialogs → takeovers | |
| Keep dialogs as-is | Pure restyle | |

**User's choice:** Profiles AS the Field list (Recommended).

### Q2 — Row tap + Remove placement

| Option | Description | Selected |
|--------|-------------|----------|
| Tap loads; trailing remove icon | Immediate load on tap | |
| Tap selects, foot applies | Apply foot button loads; Remove acts on selection | ✓ |
| You decide | | |

**User's choice:** Tap selects, foot applies.

### Q3 — Save-name entry

| Option | Description | Selected |
|--------|-------------|----------|
| Yes | Field-takeover, system alphanumeric keyboard (carve-out), timestamp default, validation kept | ✓ |
| Drop free naming | Always auto timestamp name, no keyboard | |
| Different approach | | |

**User's choice:** Yes (Recommended).

### Q4 — Foot bar composition

| Option | Description | Selected |
|--------|-------------|----------|
| State-adaptive foot | Unhomed → Home All; no selection → Calibrate+Save+Back; selected → Apply+Remove+Back | ✓ |
| Fixed foot + row actions | | |
| You decide | | |

**User's choice:** State-adaptive foot (Recommended).

---

## Claude's Discretion

- Exact Move portrait/landscape ratios + foot placement (60% height is a starting point)
- Hub routine description copy (owner reviews at UAT)
- Profile-list selection/active-marking visuals; Bed Mesh takeover composition
- NavHost route shapes, holder hoisting, nav-layer back-block enforcement
- TiltScreen stays one parameterized screen

## Deferred Ideas

- Calibration hub flips greyed-but-listed → hide-not-grey once the owner no longer needs the
  visual reminder (explicitly temporary).
