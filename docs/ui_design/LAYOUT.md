# Layout grammar — Focus / Field

The reusable skeleton for **every** jiib screen. One grammar, two regions, **one shared grid**.
Print Status is the foundational instance (`Print Status Hi-Fi.html`, classes in `hifi.css`).

> **Phase-23 note:** The old three-region Focus / Field / **Gutter** grammar was superseded in the
> jiib redesign. This document describes the **current law** — two regions (Focus + Field) with
> the Gutter's jobs rehomed (see §"Where the gutter's jobs went" below). The Kotlin
> `ScreenScaffold.kt` still carries an optional `gutter` slot for backward compatibility with
> pre-redesign screens; the grammar no longer names it a first-class region. For the component
> catalog (ListRow, DetailCard, FootButtonBar, etc.) see `COMPONENTS.md`.

---

## The two regions

| Region | What it is | Example (Spoolman, rebuilt) |
|---|---|---|
| **Focus** | one primary item; its **visual content is square** and centered in the region | spool detail card |
| **Field** | a divisible info/control surface — scrollable list + foot-pinned action bar | spool list + FootButtonBar |

**Either Focus or Field may be omitted** — whichever is present takes the freed width/height.
There is no persistent status bar; printer/connection context lives *inside* a region, never as
global chrome.

### Where the gutter's jobs went

The old Gutter was a dedicated bottom action row. Its responsibilities have been rehomed:

| Old gutter job | New home |
|---|---|
| **Navigation** | The morphing waterfall root *is* the home; no hub needed. |
| **Per-screen actions** | **`FootButtonBar`** — pinned to the foot of the screen's primary list, inside the Field slot. |
| **Stop / e-stop** | **`FloatingEStop`** overlay — top-left corner of Focus, visible on every screen but **only when printing**. Decoupled from layout flow. |
| **Power / device settings** | **System page** (also reachable as an idle foot-button). |

---

## ⚠ NON-NEGOTIABLES (read before writing any layout)

These three rules are the difference between "looks designed" and "looks broken." Do not
deviate without an explicit, deliberate reason.

### 1. Everything is tabular — one shared grid, balanced divisions
Regions **and** foot-button rows are drawn on **the same column grid**, and divisions must align.
All widths below are relative to the **content area** — the padded display box inside the
screen, *not* the raw canvas.

- **Landscape with Focus + Field:** each is **50% of the content-area width**. The Focus/Field
  divide sits at content-area center.
- **`FootButtonBar` spans the full content-area width** of the Field column on that same grid.
  With **3 equal buttons** (each 1/3 width of the Field), all edges stay on grid lines.
- One region omitted → it takes 100% width; alignment still holds.
- **Portrait:** Focus / Field stack, each **full content-area width** (already edge-aligned).
  Vertical rhythm ≈ 40 / 60 and is tunable (`--focus-grow` / `--field-grow`), not fixed.
- Weighted buttons (e.g. a wider primary action) are allowed but must still be **integer-ish
  units of the same grid** so edges keep landing on grid lines.

### 2. Aspect ratios are sacred — a square must render square
- Anything meant to be square (jog pad, ring, the smallest Field buttons, knobs) uses
  `aspect-ratio` and is sized by its **constraining dimension**, then centered in its cell.
  A 1×1 outlined box and a 9×9 grid in the Focus must render the **identical square**.
- A square rendering as a rectangle reads as a *bug*, not a style. Never let it happen.
- **The portrait↔landscape layout difference is the tool that protects this, not a nuisance:**
  In landscape Focus | Field are side-by-side (each constrained by height); in portrait they
  stack (each constrained by width). That orientation geometry is what keeps square content
  correctly proportioned in each orientation. Embrace the padding complexity — don't flatten it.

### 3. No hardcoded sizes — ratios only
- Every dimension is a **percentage, `fr`, `aspect-ratio`, or container-relative unit** of the
  overall layout. **No absolute pixel sizes** for regions, cells, gaps-as-structure, or type
  that scales.
- Permitted fixed values are limited to: hairline border widths, the minimum touch-target
  floor, and the user text-size step (`--fs`). Everything else derives from the layout.
- Text fills its box: Field button labels scale to fill the button; Focus values are sized to
  be readable across the room (use `clamp()`/container units, never a fixed `px`).

---

## The unit `U`

Every vertical dimension in the redesign is an **integer multiple of a single derived module**,
the unit `U`. A `ListRow` = 1U. A `FootButtonBar` row = 1U. A stepper tile = 1–2U. The Focus
region = the remaining units.

### Formula — FIT-PRESERVING (no overflow, no dead remainder)

Given `contentMinDim` — the **minimum of the current content width and height** (the
landscape-constrained short edge, regardless of orientation):

```
nTarget = round(contentMinDim / 41dp).coerceIn(5, 7)
nMaxFit = (contentMinDim / 64dp).toInt().coerceAtLeast(5)
N       = min(nTarget, nMaxFit)
U       = contentMinDim / N
```

**Why this exact form:** Applying the 64dp floor *after* picking `N` (the naive
`(dim / N).coerceAtLeast(64dp)`) **overflows small phones** — a 360dp landscape height with
`N = 7` would require `7 × 64 = 448dp > 360dp`. Reducing `N` first (via `nMaxFit`) guarantees
`N × U == contentMinDim` (no dead remainder, no overflow) while `U` stays ≥ 64dp on the phone
floor.

In practice on the Nexus 7 2013 (1920×1200, ~320 dpi, ~48dp system-bar insets):
- landscape content height ≈ 1104px → ~69dp on a "320dpi" device → nTarget = 7, U ≈ ~49dp ✓
- on a 360dp landscape height: nMaxFit = 5 (360/64 = 5.6 → 5), N = 5, U = 72dp ✓

### dp-derived, not DPI-parameterized

`U` is **dp-derived** — the 41dp target is the anchor. Because Compose `Dp` is already
density-independent, that dp target IS the density-correct ("DPI-derived") anchor; there is no
separate runtime DPI or pixel-density parameter. The `U_target ≈ dpi / 2.4` figure in research
notes is the *derivation of why 41dp* (a physically comfortable tap target ≈ 9mm), not a
runtime input.

### Constant through rotation

`U` is derived from `minOf(contentWidth, contentHeight)` — the landscape-constrained short
edge — and held constant through rotation. In landscape that is the content height; in portrait
it is the content width (the same physical dimension). Portrait inherits the same `U` and simply
shows more units (scrolls). A `ListRow` is the same physical height whichever way you hold the
device.

### Design rules that fall out of the unit

- **Must survive at 5 units (phone landscape)** — that is the worst case every screen is
  measured against. The essential landscape layout must fit in 5U.
- **Count flexes, unit stays ≈ constant.** A bigger screen shows *more* units, not bigger rows.
- **Settings/config surfaces (C6) are exempt** — they are denser by design.

---

## Content vs controls — fill convention

A load-bearing visual rule that separates browsable content from interactive controls:

| Surface type | Background | Border | Typical component |
|---|---|---|---|
| **Content** (scrollable items) | Transparent | `outline` (1.5dp) | `ListRow` |
| **Content — selected** | `accentSoft` | `accentLine` (2dp) | `ListRow` selected |
| **Controls** (buttons, tiles, action bars) | `surface` shade | `outline` / intent-line if active | `FootButtonBar`, sort/filter tiles, `DetailCard` |
| **Danger controls** | `stopSoft` / transparent | `stop` | `FloatingEStop` |

Selection and active states layer accent tint on top of the base convention — they do not
change the fundamental category.

**No group-label words.** Control grouping is carried by outline + fill shade + accent alone.
Leading type-icons identify a group; text labels for grouping are non-conformant. See
`COMPONENTS.md §SortFilterControlRow` for the canonical compound-control example.

---

## Foot-of-list pattern

Any screen whose primary content is a scrollable list **pins its primary actions to the foot of
that list** inside a `FootButtonBar`. This replaces the old gutter for list-screens.

- `FootButtonBar` is a `Row` of `OutlinedControl` buttons, height = 1U.
- It lives **inside the Field slot lambda**, as the last element of the field column — NOT in
  the `gutter` slot of `ScreenScaffold`.
- With 3 buttons, each button is `weight(1f)` of the Field width.
- The Spoolman screen (3-button foot: Home · Scan · Load/Unload) is the canonical prototype.

**Structural note:** `ScreenScaffold.gutter` still exists for backward compatibility with
pre-redesign screens. Redesigned screens pass `gutter = null` and use `FootButtonBar` inside
the field lambda.

---

## Floating e-stop

A red emergency-stop button (`FloatingEStop`) is **overlaid top-left of the Focus region**,
shown on every screen but **only when the printer is actively printing**. It is:

- **Decoupled from layout flow** — positioned via `Modifier.align(Alignment.TopStart)` inside
  a `Box` that wraps the Focus content; it does not shift or compress Focus geometry.
- **Printing-only** — `visible = isPrinting`. Hidden at idle, visible while printing or paused.
- **Size = 1U × 1U** (square, 64dp touch-target minimum).
- **Intent = Stop (red/danger)** — styled with `stopSoft` fill + `stop` border.
- **Always accessible** — because it floats above the Focus, nothing important should sit at
  the very top-left of the Focus region. Place main focus content (progress ring, detail card)
  below this reserved corner.

Phase 23 builds and wires `FloatingEStop` on the SpoolScreen pilot. Full waterfall root
integration (every screen) is Phase 24.

---

## Interactive-grid flexible-tile rule (HARD LAW — Phase 16 D-?)

**Promoted to hard law in Phase 16** (the four-state Home / Print-Status redesign). This is a
layout rule with a **strictly scoped** application — read the scope before you use it.

> **When a grid OF USER-INTERACTION SURFACES has awkward leftover space, ONE explicitly chosen tile
> may grow** so the remaining controls stay regular, touch-friendly, and predictable. A single
> deliberate oversized tile is preferable to ragged, irregular, or undersized controls.

**SCOPE — interactive grids ONLY.** This rule applies **only** to grids whose cells are
user-interaction surfaces (launcher tiles, shortcut/action grids, jog-style control pads). It
does **NOT** apply to:
- **stat grids** (the print-stats frame — those stay tabular on the shared grid, NON-NEGOTIABLE 1),
- **text lists** (Files, console scrollback),
- **graphs** (temperature history, bed-mesh heatmap),
- any **non-interactive info frame**.

For those, the regular grid and NON-NEGOTIABLE 1 (everything tabular, balanced divisions) still
govern — do **not** grow a stat/info cell to absorb leftover space.

**The growing tile is named explicitly per screen — never implicit.** The Phase-16 flexible tiles:

| Screen / grid | Flexible (growing) tile |
|---|---|
| Standby **launcher grid** | **Drawer** (always last; the swipe-up App Drawer's tap alternative) |
| Active (Printing / Paused) **shortcut grid** | **Tune** (absorbs space when the shortcut row needs it) |

### Babystep row — explicit C3 exception (recorded here with the rule)

The **babystep control row** on the Printing/Paused Print-Status surface is a **fixed dedicated 3-cell
row** — `[ Compress ] [ step-size ] [ Expand ]` — and is **EXEMPT** from the flexible-tile rule (no tile
grows; it is not a flexible grid).

It is also an **explicit, documented exception to C3** (below — "vertical adjustments use a vertical
arrangement"). C3 would normally object that **Z** (a vertical quantity) sits in a **horizontal** row;
the Phase-16 staging note **explicitly grants this exception** for the babystep row (the Compress/Expand
direction is carried by distinct icon silhouettes, not a vertical spatial mapping). This is a sanctioned
exception with staging-note authority — **not** a C3 violation to flag in a conformance sweep. (The
*Move* screen's Z-row is a separate case still bound by C3 — see C3 / AUDIT R2 / Phase 17.)

---

## Conformance criteria — layout/density (Phase 15.2 D-10)

Two of the Phase-15.2 D-10 C-series criteria are layout/density rules (the full C-series lives in
THEMING.md → "Conformance criteria — the C-series"); they are restated here because they govern
arrangement and target sizing:

- **C3 — Vertical adjustments use a vertical arrangement when the orientation allows it.** A control
  for a vertical quantity (e.g. **Z**) must NOT be placed in a horizontal row when the active
  orientation (landscape vs portrait) layout affords a vertical arrangement. (The Z-row in *Move* is
  the worked example — see AUDIT R2 / Phase 17 for the rework.)
- **C6 — Config/settings-type surfaces are EXEMPT from the ≥64dp touch minimum and should be
  DENSIFIED.** Settings-class surfaces (Settings, Theme editor, and similar config pages) are a
  deliberate **close-interaction** use case — held in the hand, not read across the room — so they are
  **exempt from the ≥64dp minimum-target floor** (NON-NEGOTIABLE 3's touch floor) and should be
  **densified**: tighter rows, toggles / dropdowns / popups, fit-on-one-page. The **print-control
  surfaces remain fully bound by the ≥64dp floor** — C6 does not relax them. The unit `U` derivation
  is also exempt for settings-class screens (C6 applies).

---

## Orientation rules (mechanics)

**Portrait** — stacked column, full-width regions. Field grows as needed; scroll is native to
the `ListBlock`/`LazyColumn` inside Field.

**Landscape** — a single grid: two 50% columns for the stage (Focus | Field) **within the
content area**, with the `FootButtonBar` occupying the foot of the Field column on the same
grid. All region edges share the same vertical grid lines. Square *content* inside a region
is centered.

`U` is derived from `minOf(contentWidth, contentHeight)` — constant through both orientations.
Portrait shows more unit-rows (scrolls); landscape shows the same number side-by-side.

---

## Content-display rules

- **Primary value display:** show `CURRENT / SETPOINT` for items with a separate sensor and
  controller (heaters); show `SETPOINT` only for output-only values (fans, LEDs).
- **Focus:** fit the visual/icon to the space with minimal padding; value centered; object
  title along the bottom, slightly smaller than the value.
- **Status via color, not a dedicated cell** (e.g. axis label green = homed / amber = unhomed).
- **Output-only live values need no Apply** — a single amber Back is enough; only committed
  setpoints (heaters) get Cancel / Apply.
- **Content images fit, never crop.** A thumbnail/preview shown as content (incl. as a dimmed card
  background) uses `Fit` — the whole image, centered/letterboxed on the surface. `Crop` zooms into a
  center strip in the tall/narrow landscape panes (violates NON-NEGOTIABLE 2). Exception: a circular
  fill (Print Status ring center) is meant to be filled.
- **Image-backed info card.** A preview-plus-details panel = dimmed thumbnail BACKGROUND + left-
  aligned, vertically-centered icon-led stat lines OVERLAID (one grammar; Print Status last-job card
  and Files Focus share it, differing only in which fields show). Filenames are Geist Mono.
- **Panel text fills the cell width.** A text block takes the FULL cell width, not an arbitrary inner
  ruler. Size text to fit up to the box; a single line that overflows the full width may marquee —
  never lock scroll lines to a narrower width (reads as stopping mid-screen).
- **Scroll Field ⇒ no swipe-up drawer.** A screen whose Field is a finger-scrollable list suppresses
  the global swipe-up App Drawer (the drag fights the scroll) and MUST keep an explicit exit in its
  `FootButtonBar`.

---

## Post-Phase-26 UAT formatting rules (owner, 2026-06-10)

Five rules observed and locked as law after the Phase-26 on-device UAT session (flox / Nexus 7 2013
/ Adreno 320 / LineageOS 18.1). They apply to every screen built or rebuilt from Phase 26 onward;
Phase 26 screens must conform immediately. Rules are numbered UAT-1 through UAT-5.

### UAT-1 — Prominent icons are big and vibrant (~70-80% of U)

Icons that **anchor a control or Focus header** are sized to roughly 70-80% of the unit U — use the
U-relative idiom, e.g. `(uDp * 0.75f).coerceIn(minDp, maxDp)`, never a hardcoded absolute dp.
"Prominent" means: Focus-area heroes, adjuster-panel headers, big command-tile glyphs — anything the
eye goes to first.

**EXPLICIT EXCEPTION — dense list-pane icons:** Icons inside a detailed/dense list row (e.g.
`ListRow` leading glyphs in Fine-Tune param list, Temperature sensor list, Files list) are **not**
the prominent case. They stay at their current list-scale size (~22dp). A future conformance sweep
**must not** grow these under the misread that UAT-1 applies universally.

Existing precedent: `FloatingEStop` sizes its glyph at `(uDp * 0.7f).coerceAtLeast(64.dp)` — the
same U-relative idiom. Reuse it.

### UAT-2 — Icon · name · value rows use start/end alignment

An icon + name + value row puts the **name start-aligned with (and spaced from) the icon**, and the
**value or measurement end-aligned**. The gap between name and value carries the structure — the eye
tracks left to the type info and right to the live number.

This is what `ListRow`'s leading/trailing slots and `AdjusterPanel`'s Zone-1 header already do: icon
→ gap → name → `Spacer(weight(1f))` → trailing value. That is the **canonical pattern**. Do not
break the start/end alignment in new or rebuilt rows.

### UAT-3 — Current-style scrubber ≤ 1U tall

While the current fill-bar scrubber style is in use (`ScrubberControl` / `LedBrightnessControl`),
the scrubber track + thumb is **constrained to a single U of height**. Implement via
`Modifier.heightIn(max = uDp)` on the fill-bar track; do not let a weight-based slot grow the
scrubber to fill the entire Focus.

### UAT-4 — Keep useful content out of the Focus top-left e-stop reserve

The **top-left corner of the Focus region** (or of the list field in single-view) is reserved for
the `FloatingEStop` overlay. Because `FloatingEStop` is positioned via
`Modifier.align(Alignment.TopStart)`, nothing important should occupy that corner — place the
primary Focus content (progress ring, adjuster panel, detail card) clear of it. This extends and
formalises the existing note in §"Floating e-stop" above: *"nothing important should sit at the
very top-left of the Focus region."*

### UAT-5 — Controls cap at 1U height unless deliberately chosen otherwise

Most controls obey the 1U height limit. An oversized control must be an **explicit, named decision**
— not an accident of `weight(1f)` expanding unchecked. In the Outputs Focus, the LED `ColorWheel`
is the **sole sanctioned >1U exception**; every other Outputs control (fan/servo/heater/pin
scrubbers, LED brightness bar) respects the 1U cap. When adding a control that deliberately exceeds
1U in any future phase, document the name and reason in the relevant LAYOUT.md section or
COMPONENTS.md cross-reference.

---

## Scaffold structure

### Redesigned screens (Focus/Field + FootButtonBar, no gutter)

```html
<div class="screen port|land">
  <div class="stage">
    <div class="focus">
      <!-- optional; square content centered -->
      <!-- FloatingEStop sits top-left as an overlay Box child, NOT a layout child -->
    </div>
    <div class="field">
      <!-- list content (ListBlock / LazyColumn) -->
      <!-- FootButtonBar as the last column element, pinned to foot of list -->
    </div>
  </div>
  <!-- No gutter div on redesigned screens -->
</div>
```

### Pre-redesign screens (legacy gutter still active)

```html
<div class="screen port|land">
  <div class="stage">
    <div class="focus"> … </div>
    <div class="field"> … </div>
  </div>
  <div class="gutter"> … buttons on the same column grid … </div>
</div>
```

The Kotlin `ScreenScaffold.kt` exposes `gutter: @Composable (() -> Unit)? = null` for backward
compatibility. Pass `gutter = null` on redesigned screens; the `FootButtonBar` lives inside the
`field` lambda.
