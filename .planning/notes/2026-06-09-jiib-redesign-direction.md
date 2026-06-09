---
title: jiib Visual Redesign — Locked Design Direction
date: 2026-06-09
context: Pre-milestone design exploration (/gsd-explore). Pressure-tested for internal consistency before phasing. This is the reference doc every redesign phase points back to.
area: ui
status: direction-locked (not yet planned)
---

# jiib Visual Redesign — Locked Design Direction

A fundamental visual + interaction-architecture redesign. Owner accepts that this pushes public
release out significantly — a unified look/feel comes first, and the completed redesign is the seam
to cut over to the new **jiib** repo. It reshapes the back third of the roadmap (see the
roadmap-reshape todo) and **supersedes the current Focus/Field/Gutter LAW** in `docs/ui_design/`.

## The five philosophy shifts ("schisms")

1. **North star = the Spoolman page**, with tweaks (see below). It already nails the target look and
   works on both flox (Nexus 7 2013 / Adreno 320) and phones: many scrollable items, while filter
   buttons and item details stay big enough to use.
2. **Tiles → lists.** Big tiles (inherited from KlipperScreen) were a mistake — even flox scrolls
   smoothly. **Caveat (validated):** lists are for *browsable collections* only; **spatial controls
   (jog/XYZ pad, numpad) stay grids** — spatial arrangement carries meaning a list destroys.
3. **Hub-and-spoke → conditional waterfall.** Printer *state* surfaces the relevant actions as a list
   instead of making the user hunt an icon grid.
4. **Kill the gutter.** It eats space and Android soft-button positions are unreliable. Its jobs are
   rehomed (see below) — nav goes to the waterfall, actions go to the foot of their list, Stop becomes
   a floating button.
5. **CSS-like "component classes."** A named, documented catalog of component styles, the same
   token philosophy as the color system extended from color to component archetype. See the
   companion note: `2026-06-09-component-classes-catalog.md`.

## The locked model: one morphing Focus/Field surface

The home **is** a single surface that morphs by printer state. **No gutter.** Orientation-agnostic by
construction (Focus/Field, never "top/bottom").

| State | **Focus** | **Field** (list) | **Foot buttons** (pinned to Field list end) |
|-------|-----------|------------------|---------------------------------------------|
| **idle** | ready / hero / last print | **full** action list | Preheat · Change Spool *(if Spoolman)* · Power |
| **printing** | **live print progress** (thumb, %, ETA, layer, temps) | **narrowed**: Temp, Macros, Fine-Tuning, Console, Webcam | Pause · Cancel |
| **terminal** | **print-stats box** | collapses → "Reprint this file" | Dismiss · Reprint |

- **Portrait** stacks Focus/Field; **landscape** is Focus | Field side-by-side. (Focus/Field grammar
  survives the redesign — only the *Gutter* is removed.)
- The **printing-state narrowing is a feature, not a dead-end** — it guards the user from
  mid-print foot-guns (move, extrude, calibration wizards are intentionally hidden mid-print).

### Consequence: PrintStatus stops being a spoke
The print-status surface is **no longer a screen you navigate to** — it *is* the root whenever a print
exists (the Focus region while printing/terminal). One fewer screen. The Phase-22-refactored
PrintStatus becomes the home surface, not a destination.

### Focus stays lean (no duplication of row data)
The Focus leads with the **main display item** (print preview / jiib mark) and carries only the
primary readout (%, ETA, progress). **Never duplicate in Focus what a Field row already shows** —
secondary live readings (nozzle/bed temp, speed) live in the rows, sorted relevant-first during a
print (Temperature, Fine-Tune at top). Data that doesn't warrant a pill (layer, Z, last-print
summary) becomes a thin secondary line, not a padded pill. The **floating e-stop sits top-left of the
Focus**, so nothing important is placed at the very top of the Focus — useful info comes *after* the
main display item.

## The unit (`U`) — standard module height (LOCKED 2026-06-09)

Vertical layout is built on a single derived module, **the unit `U`**. Everything vertical is an
**integer number of units** — `ListRow` = 1U, foot button = 1U, group control / stepper = 1–2U, the
Focus region = the remaining units. No hardcoded row/button px anywhere (reinforces the LAYOUT.md
"ratio-only sizing" non-negotiable).

- **Derived, never hardcoded** — from screen **DPI** (so a 1U row is the same *physical* finger-size
  across densities) and the available height.
- **Count flexes, unit stays ~constant.** The constrained **landscape** viewport holds **5 units
  (phone-landscape = the floor) → 7 units (tablet)**. A bigger screen shows *more* units, not bigger
  rows.
- **`U` is derived from the LANDSCAPE content height and held constant through rotation.** Portrait
  inherits the *same* `U` and simply shows more units (scrolls). A row is the same physical height
  whichever way you hold the device. (Owner decision, 2026-06-09.)
- **Formula:** `N = clamp(round(landscapeContentHeight / U_target_from_dpi), 5, 7)`, then
  `U = landscapeContentHeight / N`. `U` **flexes slightly to divide evenly** (chosen over a rigidly
  constant `U`) so there is **no dead-space remainder**.
- **DPI floor:** `U` can never fall below the ≥64dp touch minimum on the perf-floor device.
- **Design rule that falls out of it:** the essential landscape layout must **survive at 5 units** —
  that is the worst case every screen is measured against (mirrors Nexus-7-as-floor).
- **Settings/config surfaces stay exempt** (denser, per the existing C6 LAW carve-out).

## Where the gutter's jobs went

- **Navigation** → the waterfall *is* the home; no central hub needed.
- **Per-screen actions** → **foot-of-list pattern**, generalized: *any screen with a primary list pins
  its actions to the foot of that list.* (Spoolman's 3-buttons-at-bottom is the prototype.)
- **Stop** → a **floating emergency-stop button**, shown on **every** screen but **only when printing**.
  Decoupled from layout entirely.
- **Power** → moves to the **System page** (also reachable as an idle foot-button).
- **Device/system settings** (Printers, Theme, About, System Info, Power) were never "printer actions"
  — they split off to their own **System page / settings list**, off the printer waterfall.

## The escape-hatch question — resolved

There is **no separate "everything" menu** that would re-create the hub. The idle root list *is*
everything (printer-side); printing deliberately narrows; device/system stuff lives on the System page.
The **App Drawer does not die by decree** — it stays as a testing affordance and becomes vestigial once
the waterfall covers real use.

## Spoolman tweaks (the north-star delta to extract)

1. Replace the full-width gutter with **three action buttons at the bottom of the scrollable filament
   list**, freeing the other pane for filament info + filter buttons.
2. **Filter buttons get a shaded background** to differentiate them from the scrollable list.
3. **Detail pane:** remove the Spoolman icon (redundant on the Spoolman page); add small **edit/delete**
   buttons top-right; add a **horizontal "fill meter"** giving an immediate read of how much filament
   remains. (Owner reference mock: an auth-gated Anthropic "Filament Manager.html" design — paste HTML
   at sketch time to match precisely.)

## Sequencing (the phasing crux)

**Foundation-first, but the foundation ships a real screen — not an abstract kit.**

- **Layer 1 — design language.** Rewrite the LAYOUT.md grammar (drop Gutter, lists-first), build the
  component-class kit, **and prove it by rebuilding Spoolman onto the classes.** Spoolman is the
  **pilot** — already the north star *and* already exercises the real components (list, filter chips,
  detail card, fill meter, foot-button bar). The kit and its first real consumer ship together, giving
  something to approve on flox. Avoids the "foundation phase produces no demo" momentum trap.
- **Layer 2 — navigation spine.** Waterfall morphing root + gutter removal + floating e-stop; likely
  **adopt Navigation-Compose** (currently deferred in CLAUDE.md) for the drill-down back-stack.
- **Layer 3 — screen-group migrations.** Re-skin/rebuild remaining screens onto lists + classes in
  coherent groups; **conformance (touch targets, fsSp scale, rotation) folds in per-screen** rather
  than as a separate sweep.
- **Ship last.**

## Constraints unchanged

minSdk 23 · Nexus 7 2013 Adreno 320 = perf floor · portrait+landscape · full semantic-token theming ·
Compose+Views hybrid (ADR-0001). **HARD RULE: never invent/choose icons independently — ask the owner.**

## Next action

`/gsd-sketch` — mock the Spoolman pilot + the morphing waterfall root in throwaway HTML to lock the
*look* before any Kotlin or LAW rewrite.
