# Layout grammar — Focus / Field / Gutter

The reusable skeleton for **every** Dinghy Display screen. One grammar, three regions,
**one shared grid**. Print Status is the first instance (`Print Status Hi-Fi.html`,
classes in `hifi.css`).

## The three regions

| Region | What it is | Example (Print Status) |
|---|---|---|
| **Focus** | one primary item; its **visual content is square** and centered in the region | the progress ring |
| **Field** | a divisible info/control surface — arbitrary grid of single cells or grouped cells | the 2×3 stat grid |
| **Gutter** | primary actions, big touch targets | Tune · Pause · Stop |

**Either Focus or Field may be omitted** — whichever is present takes the freed width/height.
The **Gutter is present unless the Field itself holds all the screen's actions/navigation** —
tool pages (Screws tilt), the confirm guard, and the app drawer omit it because their Field
buttons/tiles *are* the navigation. (The app drawer is the one exception that has **no** other
way out, so its tiles must include Settings + Power; a monitor screen like the graph keeps a
**Back** in its gutter so there's always an exit.) There is no persistent status bar;
printer/connection context lives *inside* a region, never as global chrome.

---

## ⚠ NON-NEGOTIABLES (read before writing any layout)

These three rules are the difference between "looks designed" and "looks broken." Do not
deviate without an explicit, deliberate reason.

### 1. Everything is tabular — one shared grid, balanced divisions
Regions **and** gutter buttons are drawn on **the same column grid**, and divisions must align.
All widths below are relative to the **content area** — the padded display box inside the
screen, *not* the raw canvas.

- **Landscape with Focus + Field:** each is **50% of the content-area width**. The Focus/Field
  divide sits at content-area center.
- **Gutter spans the full content-area width** on that same grid. With **3 equal buttons** (each
  1/3 width), the Focus/Field divide falls on the **exact center of the middle button**, and the
  **outer edges of Focus and Field align to the outer edges of the first and third buttons.**
- One region omitted → it takes 100% width; gutter still spans full width; alignment still holds.
- **Portrait:** Focus / Field / Gutter stack, each **full content-area width** (already
  edge-aligned). Vertical rhythm ≈ 40 / 40 / 20 and is tunable (`--focus-grow` / `--field-grow`),
  not fixed.
- Weighted buttons (e.g. a wider Stop) are allowed but must still be **integer-ish units of the
  same grid** (e.g. a 4-unit row of 1.5 / 1 / 1.5) so edges keep landing on grid lines.

### 2. Aspect ratios are sacred — a square must render square
- Anything meant to be square (jog pad, ring, the smallest Field buttons, knobs) uses
  `aspect-ratio` and is sized by its **constraining dimension**, then centered in its cell.
  A 1×1 outlined box and a 9×9 grid in the Focus must render the **identical square**.
- A square rendering as a rectangle reads as a *bug*, not a style. Never let it happen.
- **The portrait↔landscape gutter difference is the tool that protects this, not a nuisance:**
  Gutter is **one row in landscape, up to two rows in portrait**. That row-count change is what
  keeps the stage area correctly proportioned in each orientation so square content stays square.
  Embrace the padding complexity it creates — don't flatten it away.

### 3. No hardcoded sizes — ratios only
- Every dimension is a **percentage, `fr`, `aspect-ratio`, or container-relative unit** of the
  overall layout. **No absolute pixel sizes** for regions, cells, gaps-as-structure, or type
  that scales.
- Permitted fixed values are limited to: hairline border widths, the minimum touch-target
  floor, and the user text-size step (`--fs`). Everything else derives from the layout.
- Text fills its box: Field button labels scale to fill the button; Focus values are sized to
  be readable across the room (use `clamp()`/container units, never a fixed `px`).

---

## Orientation rules (mechanics)

**Portrait** — stacked column, full-width regions. Gutter may grow to **two rows**;
when it does, rows are equal height and buttons split equally.

**Landscape** — a single grid: two 50% columns for the stage (Focus | Field) **within the
content area**, with the **Gutter row spanning the full content-area width** below. All region
edges and the gutter's button grid share the same vertical grid lines. Square *content* inside
a region is centered.

## Content-display rules

- **Primary value display:** show `CURRENT / SETPOINT` for items with a separate sensor and
  controller (heaters); show `SETPOINT` only for output-only values (fans, LEDs).
- **Focus:** fit the visual/icon to the space with minimal padding; value centered; object
  title along the bottom, slightly smaller than the value.
- **Status via color, not a dedicated cell** (e.g. axis label green = homed / amber = unhomed).
- **Output-only live values need no Apply** — a single amber Back is enough; only committed
  setpoints (heaters) get Cancel / Apply.

## CSS scaffold (`hifi.css`)

```html
<div class="screen port|land">
  <div class="stage">
    <div class="focus"> … </div>   <!-- optional; square content centered -->
    <div class="field"> … </div>   <!-- optional -->
  </div>
  <div class="gutter"> … buttons on the same column grid … </div>
</div>
```
`.screen.port` stacks full-width regions; `.screen.land` lays a 2-column (50/50) stage grid
with a full-width gutter row sharing the same columns.
```