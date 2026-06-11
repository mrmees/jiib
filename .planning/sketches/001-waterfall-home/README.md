---
sketch: 001
name: waterfall-home
question: "Does one morphing Focus/Field surface feel coherent as printer state changes idle→printing→terminal, with no gutter?"
winner: "A"
tags: [layout, navigation, home, waterfall, focus-field]
---

# Sketch 001: Waterfall Home (morphing Focus/Field root)

## Design Question
The home is a single surface that morphs by printer state — Focus + Field, **no gutter**. Does that
hold together across all three states, and which relationship between the Focus region and the action
list feels right?

## How to View
open .planning/sketches/001-waterfall-home/index.html

Use the **top bar** to flip Variant (A/B/C), Printer state (Idle / Printing / Complete), and
Orientation (Landscape / Portrait). The **bottom-right tools** switch Dark/Light and text-size S/M/L.

## Variants
- **A: Focus-as-card** — Focus is a distinct rounded card; the action list is a separate region.
  Landscape = Focus | Field side-by-side; portrait = Focus card stacked above the list.
- **B: One-list (hero row)** — everything is *one continuous scroll column*; the Focus is just the
  oversized first item in the same list flow. The "it's all one list" purist take (single centred
  column even in landscape).
- **C: State-driven split** — like A, but **printer state controls the real estate**: printing → Focus
  dominates and the list shrinks to a rail; idle → the list dominates and Focus is a slim header;
  terminal → Focus-only stats box, no list.

## The unit grid (`U`)
Vertical layout snaps to a derived **unit `U`** — rows and foot buttons are exactly 1U. `U` comes from
the **landscape** content height (`U = landscapeHeight / N`, `N` = 5 phone-land → 7 tablet) and is held
**constant through rotation**, so portrait shows more units and scrolls rather than resizing rows.
Use the top bar **Device (units)** toggle to feel 5u (phone) vs 7u (tablet), and the bottom-right
**Unit grid → Show** to overlay the module lines. Design rule: the essential landscape layout must
survive at **5 units**.

## What to Look For
- **State morph:** cycle Idle → Printing → Complete on each variant. Does it feel like *one surface
  changing*, or like three different screens? Is the printing-state **narrowing** of the list
  (10 actions → 5) legible as "guard rails," not "things broke"?
- **Where the live print lives:** in Printing, the Focus carries thumb/%/ETA/temps/layer — confirm you
  never feel the need to tap into a sub-screen to see your own print.
- **Foot buttons** (pinned to the foot of the Field list): Idle = Preheat/Change-Spool/Power ·
  Printing = Pause/Cancel · Complete = Dismiss/Reprint. Do the **intent colors** read right
  (accent=command, amber=caution, red=destructive, green=accept)?
- **Floating e-stop** (red, printing-only): right spot? Landscape top-right vs portrait above-foot.
- **Portrait vs landscape:** does the same model survive the rotation cleanly in each variant?
- **List affordance:** scrollbar is gone — top/bottom **edge-fade hints** appear only when there's
  more in that direction. Enough, or do you want something stronger?

## Notes / Known placeholders
- **Icons are NOT chosen** — leading markers are neutral letter chips on purpose (owner picks real
  glyphs later). This sketch is about *layout*, not iconography.
- Colors/fonts are the **real jiib tokens** (Geist + Geist Mono, dark/light oklch from hifi.css).
- Button **intent assignments** are provisional (e.g. Power = caution-amber because power-off loses
  homing) — flag any you'd reassign.
