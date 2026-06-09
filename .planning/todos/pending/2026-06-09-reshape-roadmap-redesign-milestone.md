---
created: 2026-06-09T00:00:00Z
title: Reshape roadmap back-third into the jiib redesign milestone
area: planning
target_phase: pre-23
---

## Problem

The `/gsd-explore` session on 2026-06-09 concluded that the fundamental visual redesign (see
`.planning/notes/2026-06-09-jiib-redesign-direction.md`) is **bigger than the current Phase 23** and
reshapes the back third of the roadmap. The current framing is stale against the new direction:

- **Old Phase 23 (Interaction Coherence & Page Overhauls)** — its "interaction coherence" goal is now
  *delivered by* the waterfall nav; it is a subset of the redesign, not a standalone phase.
- **Old Phase 24 (Touch-Target / Scaling / Rotation Conformance Sweep)** — no point sweeping screens
  about to be rebuilt; **conformance folds in per-screen** during the redesign migrations instead.
- **Old Phase 25 (Release Hardening & Ship)** — stays last, but now ships the redesigned app and is the
  seam to cut over to the new **jiib** repo.

## How to apply

Reshape the back third into a **redesign milestone** structured foundation → spine → screens → ship:

1. **Layer 1 — design language**: rewrite LAYOUT.md grammar (drop Gutter, lists-first) + build the
   component-class kit + **rebuild Spoolman as the pilot** that proves the kit (owner-approved on flox).
2. **Layer 2 — navigation spine**: waterfall morphing Focus/Field root + gutter removal + floating
   printing-only e-stop; likely adopt Navigation-Compose for the drill-down back-stack.
3. **Layer 3 — screen-group migrations**: remaining screens onto lists + classes, in coherent groups;
   conformance (≥64px targets, fsSp scale, rotation) folds in per-screen.
4. **Ship** (last): release hardening + jiib repo cutover.

**Execute the reshape via `/gsd-new-milestone` or phase CRUD (`/gsd-phase`) — do NOT hand-append to
ROADMAP.md.** The old 23/24 entries get absorbed/replaced; renumber cleanly.

**Do the sketch first.** Run `/gsd-sketch` on the Spoolman pilot + the morphing waterfall root to lock
the *look* before reshaping phases or rewriting the LAW — the sketch output will inform the phase
breakdown (especially how many screen-group phases Layer 3 needs).

**Cross-links:** several existing pending todos are about screens/surfaces this redesign will rebuild
(e.g. settings densify/restyle, printers edit/delete mode buttons, move-z layout rework) — review them
during the reshape and fold the still-relevant ones into the appropriate redesign layer rather than
leaving them stranded against the old phase numbers.
