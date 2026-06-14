# Adjustment Controls — Fine-Tune

From sketch **003** (winner: step-based). The numeric-adjustment archetype — every "change a value"
page in the app follows this.

## Design Decisions
- **Step-based, no scrubber** (for Fine-Tune). The Adreno-320 floor makes drag risky; tap-only steppers
  are safer. (A scrubber style for *other future* screens is still TBD — owner has a reference; see
  MANIFEST open follow-ups.)
- **Focus = the adjuster**, Field = the param list (tap a param to load it). Same Focus/Field shape as
  Spoolman, but Focus is editable instead of read-only.
- **Three vertical zones** in the adjuster, distributed (`space-between`): **title/desc hug the top,
  the value is centered, the controls hug the bottom.**
  1. **Header:** large accent param icon + bold name (makes the selection unmistakable) · **Reset**
     button (`reset_wrench`, caution/amber) in the **top-right corner**.
  2. **Description line:** one plain-language sentence of what the setting does.
  3. **Value (centered):** big tabular numeral + unit. The **unit is a SEPARATE span** — smaller
     (~0.58× the numeric size, e.g. 28sp vs 48sp), lighter (`text2` vs the value's `text` color),
     **baseline-aligned** (`alignByBaseline()` — NOT box-bottom, which reads as a subscript), and with
     **NO separating space** (compact `"120mm/s²"`). The numeric value still carries the R10 rejection
     flash; the unit stays `text2`. **Stacked baseline (owner UAT 2026-06-13):** once changed from
     entry, show `was {original}{unit}` **directly below the value** (text3, 18sp, `text3` color),
     visible only when off-baseline; it disappears when returned to baseline. The value zone carries
     `weight(1f)` and absorbs the extra line — a stacked "was" line does NOT overflow the 5U
     phone-landscape budget. *(This reverses the earlier "inline, never stacked" rule — that rule
     predated units like mm/s² that made the inline form too long; the weight-1f zone was always the
     correct fix.)*
  4. **Controls (bottom):** big `–`/`+` stepper tiles (**accent** — the screen's expected action) +
     an **increment picker** (±step, e.g. ±1/±5/±10 or ±0.005/0.01/0.05; active tile =
     `accentSoft` fill + accent outline — same selected-state convention as `ListRow`).
- **Intent colors here:** `–`/`+` = accent; Reset / Reset-all = caution/amber; Home = accent.
- **Foot:** Home (`home`, accent) · Reset-all (`reset_settings`, caution).
- **Unit-fit budgeting:** the adjuster must fit within its units on **phone-landscape (5U)** — keep
  fixed elements small (stepper ≈ 0.95U), let the value zone absorb slack (`flex:1`), and clip as a
  backstop. Verify at fs=L too.
- **Param row** (list): leading icon + name + trailing value (mono); translucent; selected = accent.

## CSS / HTML Structures
```html
<div class="adj">
  <div class="adj-top">  icon + name ........ reset(top-right)   |   description line  </div>
  <div class="adj-mid">
    <div class="adj-val">
      105<unit>mm/s²</unit>          <!-- unit: smaller, lighter, baseline-aligned, no space -->
    </div>
    <div class="adj-orig">was 100mm/s²</div>   <!-- STACKED below value; shown only when changed -->
  </div>
  <div class="adj-bot">  stepper(– / +)  +  increment-picker  </div>
</div>
```
```css
.adj { display:flex; flex-direction:column; justify-content:space-between; overflow:hidden; }
.adj-mid { flex:1 1 auto; display:flex; flex-direction:column; align-items:center; justify-content:center; }
.adj-val { display:flex; align-items:baseline; justify-content:center; }  /* baseline-align value+unit */
unit { font-size:0.58em; color:var(--text-2); }                       /* smaller/lighter span, no gap */
.adj-orig { font:var(--mono); font-size:var(--fs-sm); color:var(--text-3); }  /* STACKED below, shown only when changed */
.stepbtn { min-height: calc(var(--u) * 0.95); border:2px solid var(--accent-line); color:var(--accent-2); }
.resetbtn { border:1.5px solid var(--heat); color:var(--heat); }     /* caution — NOT color-mixed (red-bleed bug) */
/* Increment picker selected tile: accentSoft fill + accent outline (same convention as ListRow selected state) */
.inc-tile.active { background:var(--accent-soft); border-color:var(--accent-line); }
```
```js
for (const k in P) P[k].orig = P[k].val;   // capture baseline on entry → drives "was X"
```

## Scrubber (drag-adjust) — for future non-stepper screens (sketch 004)
Fine-Tune is step-only, but other screens will need drag. Canonical scrubber (owner-referenced
Android SeekBar style, ported to tokens):
- **Thin track** (~6px, rounded). **Filled side = `--accent`**, **remainder = `--surface-3`**.
- **Ringed thumb:** `--surface` knob center + thick **5px `--accent` ring** (~34px visible) with an
  **enlarged invisible touch target (~74px)** for the floor. Press = `--accent-soft` halo.
- Min/max labels under; value above. Snap to step.
```css
.scrub-track { height:6px; border-radius:999px; background:var(--surface-3); }   /* remainder */
.scrub-fill  { position:absolute; left:0; top:0; bottom:0; background:var(--accent); }  /* progress */
.scrub-thumb { width:34px; height:34px; border-radius:999px; background:var(--surface); border:5px solid var(--accent); }
.scrub-thumb::after { content:''; position:absolute; inset:-20px; }   /* touch target */
```
**Load-bearing rule:** build the element ONCE; update fill-width + thumb-position + value **in place**
during drag. **Never rebuild/recompose the dragged element per move** — it detaches the node / stales
the drag closure and the gesture dies (fill-from-middle, value-not-sticking) = the Phase-19
inline-scrubber regression (`fa97efb`). Left-anchored fill. Source: `sources/004-scrubber-style/`.

## What to Avoid
- Scrubber on the floor hardware for routine numeric entry (drag failure risk) — steppers instead.
- ~~A stacked "was X" line under the value — overflows the 5U phone-landscape Focus. Keep it inline.~~
  **REVERSED (owner UAT 2026-06-13).** "was X" now STACKS below the value; the weight-1f zone absorbs
  the extra line. The old inline rule predated multi-word units (mm/s²) that made inline too long.
- Putting the unit INLINE with a space (`"105 %"`) or at the same size/color as the numeral —
  the unit is a SEPARATE span: ~0.58× size, `text2` color, `alignByBaseline()`, no preceding space.
- Box-bottom-aligning the unit (looks like a subscript) — use `alignByBaseline()` instead.
- Increment picker selected tile with only a border — selected tile gets `accentSoft` FILL +
  accent outline (same convention as ListRow selected state, via `OutlinedControl`'s `fill` param).
- `color-mix(in oklch, var(--heat), var(--outline))` for the caution outline — renders red. Use
  `--heat` directly.
- Cramming everything top-aligned — distribute into top/center/bottom zones.
- In-Focus adjuster controls floating at 64dp inside a taller 1U row — `LocalUnitDp` must be
  PROVIDED inside AdjusterPanel ± row, IncrementPicker, and Scrubber ± row so `OutlinedControl`
  floors at 1U and fills the row (not just the 64dp touch floor).

## Origin
Sketch 003. Source: `sources/003-finetune-adjust/index.html`.
