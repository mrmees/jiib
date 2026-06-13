# Focus Frame — style law (design spec)

**Date:** 2026-06-12 · **Status:** approved design, pre-implementation
**Owner rulings captured live this session.** Parallel to the field/foot frame work
(`ListFrameInset`, COMPONENTS.md "Edge-alignment rule"). This canonizes the **Focus**
region — the other half of the two-region Focus/Field grammar — which was still per-screen
artisanal (every Focus freehanded `.padding(8.dp)` + bespoke treatment).

## Problem

The Focus region is "the main info display" on most screens but had no shared shell:
- ~9 distinct, hand-built treatments across 24 screens (audit §4 of the conformance matrix).
- Content overflowed the region with no fit guarantee — the standby jiib logo fits the Moto G
  but **clips on flox**.
- No standardized meaning to the bounding edge; spacing was ad-hoc.

Goal (owner): "we're making an at-printer control interface — the more we standardize what a
THING means, the better." A bounded Focus edge should be a consistent signal, content should
always fit, and visual values should be editable in one place.

## The law

### 1. `FocusFrame` — the universal Focus container
Rename/promote the existing `DetailCard` (`designsystem/components/DetailCard.kt`) →
**`FocusFrame`**. The component name now matches the region it fills (`ScreenScaffold(focus=…)`).
Every Focus is wrapped in `FocusFrame` **except Webcam** (full-bleed native-media special case —
explicitly exempt).

It is a **filled, bounded surface**:
- Background: `t.surface` fill — deliberately distinct from the translucent list/Field area (owner).
- Corner radius: `t.rCard`.
- Inner content padding: `focusInset` token (16dp).
- Outer inset (region-edge → frame): the shared `frame` token (8dp) — the SAME token the Field
  uses, so in landscape the two regions sit symmetric and the center gutter reads even.

### 2. The edge encodes meaning — default is NEUTRAL
The bounding edge is the constant "this is the Focus" signal; its **color/form carries the why**.
Accent is **reserved**, no longer the resting color (revised from the first pass this session).

| Mode | Edge treatment | Used for |
|---|---|---|
| **Neutral** (default) | `t.outline`, list-row stroke weight (same as a `ListRow` border) | Ordinary focus — calibrate, settings info, most screens |
| **Data** | edge tinted by the item's real data color (the existing `ringColor` carve-out — spool filament color) | Spool detail, anything whose identity is a physical color |
| **Progress** | the edge becomes a **perimeter progress bar** using the **Scrubber's exact visual language**: `surface3` track + **`accent` fill** sweeping the frame border + the **34dp ringed-thumb circle** (`surface` knob, 5dp `accent` ring) riding the perimeter as the exact progress marker | the actively-printing screen |

The Progress mode literally unwraps `Scrubber.kt`'s track/fill/ringed-thumb around the frame
perimeter. Reuse its rules: `surface3` track, `accent` left/start-anchored fill, rounded caps,
the ringed thumb as the position indicator.

### 3. Content fits, never clips (new capability)
`FocusFrame` constrains content to its inner box and **scales it down to fit** — graphical
content (jiib logo, hero glyphs, icons) uses `Fit`; the "square visual content centered" rule
(LAYOUT NON-NEGOTIABLE 2) still holds. Content adapts to the frame; the frame never overflows.
This is the fix for the jiib-logo-clips-on-flox bug — sizing becomes a property of the shell, not
each screen.

### 4. Spacing tokens (ties into the spacing-scale work)
Two named tokens own Focus spacing — edit once:
- `frame` = 8dp — region-edge → `FocusFrame` (shared with the Field frame).
- `focusInset` = 16dp — frame border → content.

### 5. Sizing & orientation ratios
- **General screens:** keep a **consistent Focus/Field balance across orientations** — proposed
  default **50/50 in landscape, 40/60 (focus/field) in portrait**, overridable per screen
  (`focusGrow`/`fieldGrow` on `ScreenScaffold`).
- **List-access-only pages** (a page that exists solely to reach a list — e.g. Console, Macros,
  SystemInfo): **free to deviate wildly** — shrink or drop the Focus so the list dominates. A
  wider list adds no value; a real information display beats "just a list of items." When such a
  page does keep a Focus, it need not match the general ratio.
- A Focus **may carry its own foot of buttons underneath** when the screen requires it
  (Focus-foot — distinct from the Field's `FootButtonBar`).

## Scope

**In:** the `FocusFrame` shell, its edge modes, content-fit, the two spacing tokens, the sizing
ratio law. Apply `FocusFrame` to every current Focus + bring the currently-unbounded ones under it:
- Standby hero (jiib sail + temps) → boxed in `FocusFrame` (also fixes the clip).
- Temperature graph mode → graph hosted inside `FocusFrame`.
- PrintStatus Printing/Paused → `FocusFrame` in **Progress** edge mode (the ring is the edge).
- PrintStatus Terminal (result thumbnail) → `FocusFrame`.
- Existing card focuses (Spool=Data edge, CalibrationHub/FineTune/Outputs/Printers/Files/Temp-
  adjuster=Neutral edge) → already `DetailCard`; become `FocusFrame`, default edge flips
  accent→neutral.

**Out (later passes):** the *internal* design of each archetype (how the graph/heatmap/jog-pad/
hero lays out inside the shell). This spec standardizes the SHELL only.

**Webcam:** exempt — stays full-bleed.

## Open / tunable details (resolve at implementation or UAT)
- **Progress sweep anchor/direction:** propose start top-left, sweep clockwise. Confirm at UAT.
- **Data-edge stroke weight:** default is list-row weight (1.5dp-ish). A 1.5dp tinted edge may
  read too thin for a color signal (spool) — may bump weight for Data/Progress modes. Tune on device.
- **About screen:** owner thought it was a Focus/Field setup; it's currently Field-only. Per the
  list-only rule, About is a "general informational screen" → should likely gain a real `FocusFrame`
  (jiib lockup/version) rather than stay list-only. Decide when the application pass reaches it.

## Related future concepts (owner-flagged this session — NOT this spec's scope)
- **Color-usage pass:** nail down what each color *means* app-wide (accent reserved for progress/
  active, neutral resting, data colors literal, etc.). For now Focus relies on border + shading;
  the edge-mode semantics here are the first deposit toward that pass.
- **Unified "drawing surface" primitive:** the owner correctly sensed a single base class under
  everything — a bounded surface = fill + edge(color/weight) + radius + inset. `FocusFrame`, control
  tiles, list rows, cards are all styled instances of it. The seed exists today as
  `Modifier.cardSurface(t)` (DetailCard.kt). `FocusFrame` will be built ON that seed (not a parallel
  implementation), but a full unification of every bounded surface under one `Surface` primitive is a
  later pass, deliberately out of scope here to avoid scope creep.

## Implementation surface (high level — full plan via writing-plans)
- Rename `DetailCard` → `FocusFrame` (9 call sites + previews + KDoc/docs); add `edge` parameter
  (sealed: `Neutral` / `Data(color)` / `Progress(fraction)`), default `Neutral`.
- Add the perimeter-progress draw (reuse Scrubber track/fill/ringed-thumb rules).
- Add content-fit wrapping (square-center + scale-to-fit).
- Add `frame` (=`ListFrameInset`, generalized) + `focusInset` tokens; route Focus call sites.
- Apply to standby / temp-graph / printing / terminal; flip card focuses to neutral default.
- Update `docs/ui_design/LAYOUT.md` (Focus region + sizing law) and `COMPONENTS.md` (FocusFrame).
- Verify on flox (clip fix) + moto; printing-mode progress edge needs a live print (owner-driven).
