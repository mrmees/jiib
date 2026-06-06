# Handoff: Dinghy Display — Klipper printer touchscreen UI

## Overview
Dinghy Display is a touchscreen control interface for **Klipper / Moonraker** 3D printers.
It targets **phones through tablets**, in **both portrait and landscape**, with **dark and
light** themes and a user text-size setting. It is primarily *control-surface* software that
also doubles as a nicely-formatted status display during a print.

This bundle is the **design system + a high-fidelity reference implementation** of the core
screens, plus the written rules that govern every screen.

## About the design files
The files under `reference/` are **design references authored in HTML/CSS/JSX** — prototypes
that show the intended look, layout, and behavior. They are **not production code to copy
verbatim.** Your task is to **recreate these designs in the target codebase's environment**
using its established patterns and component libraries — or, if the app doesn't exist yet,
to pick an appropriate stack and implement them there.

> Note: the reference renders with React via in-browser Babel **only so the prototype runs in
> a browser.** React is *not* a requirement — the design is plain structure + CSS. The CSS
> (`hifi.css`) and the per-screen markup are the real spec; read them directly.

## Fidelity: HIGH
Final colors, typography, spacing, radii, motion, and interaction states. `reference/hifi.css`
is the actual token + component source — treat its values as canonical and reproduce pixel-for-pixel
within your stack.

## Read these first (they are the law)
1. **`CLAUDE.md`** — design philosophy + non-negotiables. *Place this at your repo root so
   Claude Code auto-loads it as project instructions.*
2. **`LAYOUT.md`** — the Focus / Field / Gutter layout grammar: regions, orientation rules,
   grid alignment, the ⚠ non-negotiables (tabular alignment, sacred aspect ratios, no hardcoded sizes).
3. **`THEMING.md`** — the two-layer token system, dark/light values, button-intent colors, `--fs`.

---

## The layout grammar in one paragraph
Every screen is built from three regions: **Focus** (one primary item; its visual content is
square and centered), **Field** (a divisible info/control grid), and **Gutter** (big touch
actions). **Portrait** stacks them full-width (≈40/40/20; the gutter may grow to two equal rows).
**Landscape** is one shared grid: **Focus | Field as 50/50 columns** of the content area, with a
**full-width gutter row** on the same column lines — so with 3 equal gutter buttons the Focus/Field
divide lands on the center of the middle button, and region edges align to the outer button edges.
Either Focus or Field may be omitted (the other takes the space). The Gutter is present **unless
the Field itself holds all navigation** (tool pages, the confirm guard, the app drawer). There is
**no persistent status bar** — context lives inside a region.

---

## Screens / Views
All live in `reference/Print Status Hi-Fi.html` as labeled artboards on a pan/zoom canvas
(portrait + landscape for each). Each is an *instance* of the grammar.

### 1. Splash / Connect
- **Purpose:** entry; doubles as the live connection state.
- **Layout:** Focus-only, centered. Pulsing logo mark, "Dinghy Display" wordmark, animated
  connecting dots, Moonraker endpoint in mono. Resolves into Print Status when connected.

### 2. App Drawer (navigation)
- **Purpose:** the swipe-up launcher; the app's primary navigation.
- **Layout:** Focus omitted; Field = grid of square destination tiles. **Density UPDATED 2026-06-01:
  4 columns** (was 2 — 2-col made tiles huge, only ~2 visible in landscape; 4-col shows ~8), scrolls
  beyond. **No gutter** — tiles are the navigation, and must include **Settings** and a red **Power**
  tile (the drawer is the one screen with no other exit). Each tile = **Material Symbol icon + label**
  (icons via the Material Symbols Outlined font — see "Icon system" below).

> **Icon system (established 2026-06-01):** app icons come from the **Google Material Symbols** (Outlined)
> glyph font (`res/font/material_symbols_outlined.ttf`), rendered by ligature name via the `MaterialSymbol`
> composable — one font, no per-icon assets. Prefer it for any new icon. (The bespoke `nozzle`/`heat_bed`
> vector drawables remain for the printer-specific glyphs Material Symbols lacks.) The full font ships in
> dev; subset to referenced names before release (Phase 8).

### 3. Print Status (home / monitor) — the four-state `PrintStatusMode` model
- **Purpose:** the app's home surface. It is **state-driven**, not a single "monitor" screen:
  ONE Moonraker-derived classifier, **`PrintStatusMode`**, maps the live print state to exactly four
  states — **Standby · Printing · Paused · Terminal(kind)** — and every composable renders **from that
  mode** rather than scattering raw `printState` checks. (Implemented Phase 16; supersedes the old
  single-screen "Print Status" + idle "last-job card" treatment.)

- **The classifier (Moonraker-derived only):** `classifyPrintStatus(state)` reads **only**
  `state.printState`:
  - `printing → Printing` · `paused → Paused` · `complete → Terminal(Complete)` ·
    `cancelled → Terminal(Cancelled)` · `error → Terminal(Error)`.
  - **`standby → Standby` ALWAYS — even with a stale filename / leftover job data.** (Behavior change:
    the old screen could masquerade a Standby-with-leftover-`restartFilename` as a terminal screen; that
    branch is **deleted**. Standby is always the launcher dashboard.)
  - **Klippy shutdown/error is NOT terminal.** A klippy lifecycle fault does not manufacture a
    `Terminal` print-result mode — that is a separate axis owned by app-level recovery routing (the
    Syncing Splash), not this classifier.

- **Each state's Focus / Field / Gutter:**

  - **Standby** — *idle dashboard + launcher.*
    - **Focus:** the app icon (P16; future: per-printer user image) with a **minimal centered glance-list
      overlay** — Nozzle temp · Bed temp · an MCU/host temperature glance (omitted when no usable sensor,
      no host-load fallback in P16) · Active-spool remaining (only when Spoolman is present). **No
      connection-state line** (the app being live already implies it).
    - **Field:** an **adaptive launcher grid** — Files · Temperature · Move · Extrude · Calibration ·
      Spool *(if Spoolman present)* · Macros *(only if bookmarked macros exist)* · Console *(low-priority
      filler)* · **Drawer (always last, the flexible/growing tile → opens the App Drawer)**. Every tile
      dispatches a real navigation; the Drawer tile is the swipe-up alternative.
    - **Gutter:** **Preheat** (accent — ordinary physical command; spool-aware: fires the active spool's
      filament temps directly when Spoolman exposes them, else opens the `PresetSelector`) · **Power**
      (red, **inert/design-only** in P16). **No E-Stop in Standby.**

  - **Printing** — *live print cockpit.* Preserves the `03-print-status.png` composition.
    - **Focus:** thumbnail/preview base + the accent **progress ring** overlaid + numeric progress and
      time-remaining at the ring's center; filename + printer subtitle below. No animated ring loop —
      it draws on once and updates at the throttled cadence (Adreno-320 budget).
    - **Field:** ONE framed **print-stats list** (icon-led rows; nozzle + bed emphasized): Nozzle · Bed ·
      Elapsed / Remaining · Current layer / total *(placeholder if unavailable)* · Current Z · **Applied
      Z offset** (shown when non-zero OR during the babystep window). **Speed % and Flow % are EXCLUDED**
      — they belong under Tune (Phase 17). An optional **Spoolman line** (job filament vs available;
      informational; rendered in **accent** for attention when available < required — not warning/stop)
      shows only when Spoolman is present. Below the stats sits the **shortcut row** (see flexible-tile
      note) **OR** the **babystep row** during the early-layer window (the babystep row *replaces* the
      shortcut row).
    - **Gutter:** **Pause** (accent — immediate, no guard) · **Cancel** (red — destructive, routes
      through the full-screen ConfirmGuard) · **E-Stop** (red, octagon glyph — tap→confirm,
      long-press→immediate).

  - **Paused** — *same job context, resume/cancel priority.*
    - **Focus:** the Printing focus **dimmed**, with a static pause-icon overlay — the Focus carries the
      paused state (no extra status row).
    - **Field:** identical structure/toolset to Printing (babystep replacement still applies if in-window).
    - **Gutter:** **Resume** (green — returns to a safe running state) · **Cancel** (red, ConfirmGuard).
      **No E-Stop** (the user is already intentionally intervening; Cancel ends the job).

  - **Terminal(Complete | Cancelled | Error)** — *result screen.* Moonraker-derived only (no
    app-remembered terminal state); visible until dismissed, a reprint starts, or Moonraker reports
    another state. It is **passive** — a mode of Home, not a screen that yanks the user off another view.
    - **Focus:** a clean **result hero** — the print preview/thumbnail shown cleanly (**no ring, no dim,
      no result-icon overlay**); app-icon fallback when no preview exists. Complete and Cancelled share
      the identical treatment.
    - **Field:** ONE framed stats element (reuses the Printing stats-frame, omitting live-only fields that
      no longer make sense; em-dash placeholders keep core rows stable). **`Terminal(Error)` only**
      appends the last **≤3 meaningful printer error lines** from console history (the area is hidden if
      there are none). **No** special stop/error chrome beyond those lines.
    - **Gutter:** **Dismiss** (neutral — issues `SDCARD_RESET_FILE`; clears the terminal presentation;
      allowed even after an error, hot heaters don't block it; on failure it surfaces feedback rather than
      silently losing context) · **Reprint** (accent — the terminal screen's natural primary action;
      directly re-issues the existing print-start on Moonraker's current/last file path, **no confirm
      guard, no `SDCARD_RESET_FILE` first**).

- **Babystep row (early-layer window only).** A dedicated **3-cell row** — `[ Compress ] [ step-size ]
  [ Expand ]` — that *replaces* the active-print shortcut row while layer data is available and within the
  configured early-layer window (no time-based fallback; hidden if layer data is unavailable). Compress =
  nozzle closer to bed; Expand = nozzle farther; **icons only** (distinct silhouettes, no text labels),
  each accent-outlined (the icon carries the live-jog action). The center cell shows the step size only and
  cycles `.02 → .05 → .10 → .15 → .20` on tap. The current **applied Z offset lives in the print-stats
  frame**, not in this row. This row is an intentional, documented exception to the usual
  "vertical quantity → vertical arrangement" concern — see LAYOUT.md (C3 exception) and the flexible-tile
  rule.

- **Temperature identity = accent, not amber (supersedes the `03-print-status.png` mockup).** The hi-fi
  mockup shows nozzle/bed in **amber**; that predates the Phase-15.1 D-13 / N-series rules. `--heat` is now
  the **caution** color (no longer a heater identity); live temperature identity rides
  `directional.temperature` (**= accent**). Nozzle/bed temperature values render in **accent**. The
  mockup's amber is **superseded** — the artboards are to be regenerated to match (see the merge note).

### 4. Move — jog & home
- **Purpose:** position the toolhead.
- **Layout (2026-06-01 cleanup):** Focus = **3×3 XY jog pad** sized as the largest centered SQUARE
  that fits (portrait uses `ScreenScaffold.portraitFocusAspect = 1f` so it fills the WIDTH; the field
  absorbs the rest). Edge cells are Material-Symbol jog arrows (`arrow_*`) — **blue accent outline,
  icon colored by state: gray unavailable / amber normal / red force-move**. Center = **XY home**;
  the Y/Z/X corner cells stack the live value under a green=homed / amber=unhomed axis letter (tap to
  home that axis). Top-right cell = the **force-move toggle** (green `lock` off → normal G1 jog gated
  on homed; red `lock_open_right` on → jog issues `FORCE_MOVE`, allowed while unhomed). Field = two
  EQUAL-height rows — a Z row (**expand / Z-home button / compress**) + a 6-up increment selector
  (0.1–100 mm). Gutter = **All** (home_and_garden, accent/blue) · **Disable** (red — un-homes, with a
  destructive confirm guard) · **Back** (green — non-destructive nav).

### 5. Screws Tilt — calibration tool (the tool-page template)
- **Purpose:** run a guided procedure.
- **Layout:** Focus = live bed diagram; each screw shows its required turn from Moonraker, the
  screw to adjust pulses, in-tolerance ones go green, the instruction is a **centered overlay**
  (not a header). Field = the 4 procedure actions (Initiate / Adjust / Accept / Cancel) as a 2×2
  of **icon-over-label** buttons. **No gutter** (Field holds the actions). This templates every
  other calibration/tool screen.

### 6. File / Job Picker
- **Purpose:** browse and start a print.
- **Layout:** Gutter = **Cancel** (red) · **Print** (green, disabled until a file is selected).
  Per-orientation Focus: **portrait** has no Focus while browsing (the list fills); selecting a
  file reveals a Focus (preview + centered info overlay). **Landscape** always shows Focus + Field
  (placeholder preview until a file is chosen) — because widening a list buys nothing but a preview
  does. Selected row gets a subtle accent outline only (it does **not** expand; details live in the Focus).

### 7. Single-setting pages (Extruder temp, Fan speed)
- **Purpose:** adjust one value. *Any numeric-only adjustment uses this pattern — never an
  alpha keyboard.*
- **Layout:** Focus = the icon as a faint background glyph with the value centered on top and
  the object title along the bottom (title slightly smaller than the value). Primary readout is
  **`CURRENT / SETPOINT`** for sensor+controller items (heaters), **`SETPOINT`** only for
  output-only items (fans). Field fills with two equal-height rows: an increment/On-Off row +
  a full-height **fill-bar scrubber**. Gutter: committed setpoints (heater) get **Cancel / Apply**;
  output-only live values (fan) get a single amber **Back** (no Apply — nothing to commit).

### 8. Confirm / Destructive guard
- **Purpose:** gate a dangerous or committing action.
- **Layout:** Focus = alert icon (destructive) or check icon (positive) + message. Field = the
  two choices filling it. **No gutter.** Destructive confirm is **red**, positive is **green**,
  the safe dismiss stays **neutral**.

### 9. Temperatures — graph (monitor)
- **Purpose:** watch temperature history.
- **Layout:** Focus = per-sensor cards; Field = time-series line graph; Gutter = **Back** (red) ·
  **Presets** (neutral) · **Cooldown** (amber).
- **Sensor card (UPDATED 2026-06-01, supersedes the `09-temperature-graph.png` card style — icons-over-text):**
  each card splits in half — LEFT = the heater glyph (`nozzle` / `heat_bed` vector in `res/drawable/`,
  sourced from `img/*.svg`), tinted to the trace color (nozzle=heat, bed=accent, chamber=violet); RIGHT =
  the hero current temperature, **auto-sized to fit width** (never wraps/clips at any `--fs`), centered,
  with the **setpoint value centered directly beneath it** (smaller, trace-colored, no arrow) only when a
  target is active. Tapping a card opens the scrubber. Cards with no icon asset fall back to the text label.
- **Graph (UPDATED 2026-06-01 — dynamic range, supersedes the fixed `0..350`):** multiple sensors overlaid
  on one axis. **Dynamic Y-range** fits all visible history + active setpoints with ±5° absolute padding,
  bounds rounded outward to 5°, clamped to 0..350, **no hard floor** (the absolute pad keeps a steady
  reading from noise-zooming — the old G-1 trap). **Min/max Y labels** drawn right-aligned, muted, sized to
  the button text. Each trace shades to the baseline in its own translucent color, drawn **highest-value
  first so the coolest trace sits on top** (its fill/line wins the shared lower band). Per-trace dashed
  current-setpoint lines. (Perf: this is the Adreno-320 fill-rate surface — see the ADR-0001 perf-gate
  reframing; the graph is network-paced/sparse-redraw, gated on no-frozen-frames + responsiveness, not a
  per-frame ms budget.)

### 10. Foundations (design-language spec)
- A reference card showing type, color roles, the outline-control states, and shape — the
  visual summary of `THEMING.md`.

---

## Interactions & behavior
- **Navigation:** swipe-up opens the App Drawer (full screen). Tools/monitors return via a
  **Back** in the gutter (or a Cancel on tool pages). Tapping a live value opens its single-setting page.
- **No alphanumeric keyboard, anywhere.** All selecting/sorting/filtering is **scroll + tap**;
  any numeric entry uses a single-setting page (numpad-style steppers/scrubber), never the OS keyboard.
- **Text size:** `--fs` multiplier drives a user **S / M / L** setting (M is the larger default).
- **Motion (alive but restrained):** progress bar fills + sheen; status dot breathes; the ring
  draws on; logo pulses on connect; meters/fills animate in. Keep easing soft (`cubic-bezier(.4,0,.2,1)`).
- **States:** controls have hover/active (scale 0.97) and pressed-tint states; disabled = reduced
  opacity (e.g. Print before a file is picked). Status is encoded as **color on an existing element**
  rather than a dedicated indicator cell wherever possible.

## State management (data the UI binds to)
Sourced live from Moonraker: print progress / filename / layer / time estimates; nozzle & bed
**current + target** temps (+ history for the graph); toolhead **X/Y/Z position** and **per-axis
homed** state; fan/output levels; connection state; file list with metadata/thumbnails; screw-tilt
results. Reflect: homed → axis label color; printing → status pill; current vs target → dual readout.

## Design tokens
See **`THEMING.md`** for the exact dark + light values (colors, glows, radii, type, `--fs`).
Every dimension in the reference is a %, `fr`, `aspect-ratio`, `clamp()`, or container-relative
unit — **no absolute px** except hairline borders, the minimum touch floor, and the `--fs` step.

## Components inventory (in `hifi.css`)
- **Shell:** `.screen` (`.port`/`.land`/`.light`), `.stage`, `.focus`, `.field`, `.gutter`.
- **Buttons:** `.ctl` + `.accent` / `.warn` / `.danger` / `.go` / `.grow` / `.g-wide`; in-Field
  action buttons are icon-over-label, Gutter buttons are icon+label inline.
- **Data:** `.card`, `.statgrid`/`.s`, `.lbl`, `.pill`, `.status` (breathing dot), `.chip`.
- **Specialised:** `.prog` ring, `.bar` progress, `.fillbar` scrubber, `.step` increment,
  `.jogpad`/`.jbtn`, `.tile` (drawer), `.alert` (confirm), `.graph`/`.plot` + `LineGraph`,
  `.setval` (single-setting Focus), `.frow2` (file row).

## Assets
- **Fonts:** Geist + Geist Mono (Google Fonts). Use tabular numerals for all live data.
- **Icons:** inline SVG, hand-drawn in the markup — **no icon-library dependency.** Rule: never
  repeat the same glyph twice on one screen (use a 1–3 char text label instead).
- **`reference/image-slot.js`** and **`reference/design-canvas.jsx`** are *prototype scaffolding
  only* (the drag-drop preview placeholder and the pan/zoom presentation canvas). They are **not
  part of the product** — ignore them when implementing; they only make the reference viewable.

## Files in this bundle
```
design_handoff_dinghy_display/
├── README.md            ← you are here
├── CLAUDE.md            ← philosophy + non-negotiables (put at repo root)
├── LAYOUT.md            ← Focus/Field/Gutter grammar
├── THEMING.md           ← tokens, dark/light, color intent
└── reference/
    ├── Print Status Hi-Fi.html   ← all screens as labeled artboards (open in a browser)
    ├── hifi.css                  ← canonical tokens + component CSS
    ├── design-canvas.jsx         ← prototype harness only (ignore for impl)
    └── image-slot.js             ← prototype harness only (ignore for impl)
```

## Screen previews (`images/`)
High-res PNGs of each screen (portrait + landscape together where applicable):

| File | Screen |
|---|---|
| `images/01-splash.png` | Splash / connect |
| `images/02-app-drawer.png` | App drawer (2× tile grid) |
| `images/03-print-status.png` | Print Status (home) |
| `images/04-move.png` | Move — jog & home |
| `images/05-screws-tilt.png` | Screws tilt — calibration tool |
| `images/06-file-picker.png` | File / job picker (browsing + selected) |
| `images/07-single-setting.png` | Single-setting pages (temp + fan) |
| `images/08-confirm.png` | Confirm / destructive guard |
| `images/09-temperature-graph.png` | Temperatures — graph (single + overlaid) |
| `images/10-foundations.png` | Foundations — design-language spec |

## Viewing the reference
Open `reference/Print Status Hi-Fi.html` in a browser (needs network for the Geist fonts +
React/Babel CDN). Pan/zoom the canvas; each artboard is a screen in portrait or landscape.
Toggle the **S/M/L** control (top-right) to see text scaling. To preview a real print image,
drag an image onto a preview slot.
