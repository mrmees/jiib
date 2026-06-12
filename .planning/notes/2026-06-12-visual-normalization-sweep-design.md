# Visual Normalization Sweep — design note

**Date:** 2026-06-12 · **Owner:** Matthew · **Status:** approved direction, pre-execution
**Mode:** direct work, NOT a GSD phase (owner call, 2026-06-12 — GSD too slow at this point).
Sits between Phase 28 (complete) and Ship.

## Goal

One coherent UI law in `docs/ui_design/`, and every screen visibly conforming to it — verified by
a written conformance checklist plus an owner screen-by-screen walk on flox.

## Why now

The `docs/ui_design/` set straddles the 2026-06-09 jiib redesign line:

- `README.md` (Jun 6) still teaches the dead three-region Focus/Field/Gutter grammar (40/40/20)
  and the swipe-up App Drawer as primary nav (drawer deleted in Phase 28).
- `THEMING.md` (Jun 7) — token law valid; worked examples all gutter-based; still defers the
  Back sweep to "Phase 15.2".
- `PREVIEW_AND_TOKENS.md` (Jun 7) — cites the retired "Focus/Field/Gutter grammar" as sibling law.
- Even post-redesign docs carry orphans: `LAYOUT.md` still lists a Drawer tile (~line 213) and the
  swipe-up-suppression rule (~lines 288–289); design `CLAUDE.md` mid-file rules still reference
  Gutter exemptions and a gutter Back position.

Code-side (drift survey, 2026-06-12): token/icon/intent compliance is near-100%, but structure
drifts — PrintStatus (the waterfall root) has near-zero unit-grid/ListRow adoption and a living
`PrintStatusGutter.kt`; five screens (About, Printers, Spool, Move, Extrude) roll local row
anatomy instead of `ListRow`; residual `gutter` slots are inconsistent across ~20 screens;
ThemeScreen + ThemeEditorScreen are pre-redesign islands.

## The sweep — three steps, three owner touchpoints

### Step 1 — Spec reconciliation (interactive)
1. Build the full inventory: every stale passage in the six docs + every hidden policy question.
   Known policy questions going in (inventory may add more):
   - Fate of the backward-compat `ScreenScaffold.gutter` slot (survive to ship vs retire).
   - Fate of `README.md` (rewrite as new front door vs demote/archive).
   - Drawer remnants in `LAYOUT.md` / design `CLAUDE.md` (delete vs rewrite the underlying rules,
     e.g. scroll-suppression now that there is no swipe-up).
   - Sketch MANIFEST open follow-ups: scrubber style TBD; oklch caution-reads-red fix.
   - Back-position rule (still pointing at a Phase-15.2 sweep that never became law).
   - ThemeEditor/ThemeScreen exemption status (documented carve-out vs normalize).
2. The inventory ALSO flags **law gaps**: fine-grained visual values the checklist needs that no
   doc currently specifies (per-state outline widths, internal control padding, exact radii,
   font-role assignments, the fsSp size scale — currently memory/code convention, not law).
   Matthew ratifies or changes the de-facto value for each; the ruling gets written into the docs.
3. Matthew rules through the inventory in batched question rounds (~3-4 rounds).
4. Rewrite the docs into one law; commit.

### Step 2 — Conformance audit (autonomous; overnight-able)
1. Derive a written per-screen conformance checklist FROM the reconciled law. The checklist
   covers BOTH structure and fine-grained visual properties (owner requirement, 2026-06-12 —
   "shading/outlines/layout within a control/text size/font used/etc."):
   - **Structure:** unit grid `U` (integer-U heights, 1U control cap, UAT-5), ListRow vs local
     row anatomy, FootButtonBar vs gutter slot, Focus/Field composition, FloatingEStop corner
     reservation (UAT-4).
   - **Fill & shading:** content-translucent vs controls-filled; selection/pressed tints
     (accentSoft); glow treatment (static only); surface layering vs flat.
   - **Outlines:** stroke widths per state (e.g. 1.5dp/2dp ListRow states, 3dp DetailCard ring);
     outline color tokens; no ad-hoc borders.
   - **Layout within a control:** UAT-2 icon · name · value anatomy (name start-aligned with
     icon, value end-aligned, `Spacer(weight(1f))` gap); internal padding consistency; icon
     sizing (prominent ~70-80% of U per UAT-1, dense list-pane icons stay small).
   - **Typography:** font family per role (Geist UI text vs Geist Mono for data values /
     filenames / tabular numerals); `fsSp()` everywhere with the established size scale
     (15sp metadata floor · 17-18sp body · 20-22sp titles · 26sp tabular stats · 30sp+ focus);
     text-fills-box rule; S/M/L `--fs` survival.
   - **Shape & color:** corner radii (22px cards / 16px controls / pill chips); intent colors by
     safety; token routing (no raw colors outside sanctioned carve-outs); icon registry only.
   - **Touch:** ≥64px targets; scrubber ≤1U (UAT-3).
2. Score all 24 screens → per-screen conformance matrix (single doc artifact). Structural and
   token checks are code-greppable; fill/outline/typography/internal-layout checks need rendered
   evidence — use the `@Preview` matrices and/or on-device screenshots, not grep counts alone.
3. **Touchpoint:** Matthew rules rebuild / polish / leave per screen off the matrix.
   PrintStatus's fate is decided here, with evidence.

### Step 3 — Execute (autonomous with checkpoints)
1. Fix per verdict, highest-traffic screens first. Build + install along the way
   (force-rebuild before any on-device check — stale-APK trap).
2. Final gate: Matthew walks every screen on flox against the checklist, screen-by-screen approve.

## Acceptance bar

Checklist + owner eyeball: every screen scored against the written checklist AND approved by
Matthew on flox. Taste flags during the walk are in-scope fixes.

## Artifacts

- Reconciled `docs/ui_design/` (the law itself — primary deliverable).
- Conformance matrix doc (durable: the flox-walk script now, the scoring rubric for future screens).
- This note.

## Follow-on pass (owner, 2026-06-12): Focus standardization

Focus-region treatments are still per-screen artisanal (detail cards, jog pad, glance overlays,
adjuster zones, webcam, ring). Standardizing them needs DESIGN decisions (archetype definitions),
not conformance — so it is a SEPARATE pass after this sweep. This sweep's audit contributes the
input: a per-screen Focus-treatment inventory grouped into candidate archetypes.

## Out of scope

- Non-visual hardening (e.g. the deferred H.264 rotation re-prepare fix, MJPEG fallback testing).
- New features or interaction redesigns beyond what a per-screen verdict requires.
- Icon/glyph selection without Matthew (owner law stands: never pick icons independently — ask).
