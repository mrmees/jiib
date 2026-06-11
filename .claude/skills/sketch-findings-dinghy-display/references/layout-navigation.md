# Layout & Navigation — the morphing waterfall home

From sketch **001** (winner: Variant A · Focus-as-card). The home is the conditional-waterfall root
that replaces hub-and-spoke nav.

## Design Decisions
- **The home is ONE surface that morphs by printer state** — Focus + Field, no gutter. PrintStatus is
  **no longer a destination**; it *is* the Focus whenever a print exists.
- **Focus-as-card** beat the one-list (B) and state-driven-split (C) takes: a distinct rounded Focus
  card + a separate action list reads cleanest on both flox (landscape) and phone (portrait).
- **State → content mapping:**

| State | Focus | Field (list) | Foot buttons |
|-------|-------|--------------|--------------|
| **idle** | jiib hero + status + one "last print" line | **full** action list | Preheat · Change Spool · Power |
| **printing** | live print: preview, %, ETA, progress, one meta line (layer/Z/elapsed) | **narrowed** (Temp/Macros/Fine-Tune/Console/Webcam) | Pause · Cancel |
| **terminal** | preview + "Print Complete" | stats rows (replace the action list) | Dismiss · Reprint |

- **Printing narrowing is a feature** (guard rails) — move/extrude/calibration are intentionally
  hidden mid-print.
- **Focus stays lean:** lead with the main display item (preview / jiib mark); useful info comes
  *after* it; **never duplicate data a Field row already shows** (live temps/speed live in the rows,
  sorted relevant-first). Data that doesn't warrant a pill (layer/Z) is a thin meta line.
- **Foot-of-list pattern:** any screen with a primary list pins its actions to the foot of that list
  (the generalized gutter replacement).
- **Floating e-stop:** red (`--stop`), shown on every screen **only when printing**, top-left of the
  Focus over the preview corner (so it covers nothing important — hence "lead with the display item").
- **Split:** landscape ≈ Focus 44% / Field; portrait ≈ 50/50.

## CSS / HTML Structures
```html
<div class="home land|port">      <!-- flex row (landscape) / column (portrait) -->
  <div class="focus"><div class="focus-card"> e-stop? + focusInner </div></div>
  <div class="field"> listBlock(rows) + footButtonBar </div>
</div>
```
```css
.home.land { flex-direction: row; }   .home.port { flex-direction: column; }
.focus-card { background: var(--surface); border-radius: var(--r-card); position: relative; overflow: hidden; }
.focus-card .estop { position:absolute; top:14px; left:14px; }   /* printing only */
.foot { display:grid; gap:12px; }   /* foot-of-list action bar; .n2/.n3 columns */
```

## List affordance (shared with 002/003)
No scrollbar. **Top/bottom edge-fade hints** appear only when there's more content in that direction;
no position indicator.
```css
.list { overflow-y:auto; scrollbar-width:none; }   .list::-webkit-scrollbar{ display:none; }
.fade.top/.fade.bot { position:absolute; opacity:0; transition:opacity .2s; }  /* JS toggles by scrollTop */
```

## What to Avoid
- **One continuous list (Variant B):** the print/hero as the first list item — felt cramped in
  landscape, lost the Focus emphasis. Rejected.
- A separate "everything" menu behind the waterfall — that just rebuilds the hub. The idle list *is*
  everything (printer-side); device/system settings split to a System page.
- Putting status/useful info at the very top of the Focus — collides with the floating e-stop.

## Origin
Sketch 001. Source: `sources/001-waterfall-home/index.html` (all 3 variants; A marked ★).
