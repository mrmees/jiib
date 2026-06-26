# jiib — UI design system (front door)

> **Rewritten 2026-06-12** as part of the visual-normalization sweep (rulings R1–R14,
> `.planning/notes/2026-06-12-spec-recon-inventory.md`). The previous README was the original
> 2026-05-31 design-handoff bundle; it described the pre-redesign three-region grammar and is
> preserved in git history only. Where any doc in this directory contradicts a newer one, the
> doc with the later "supersedes" header wins; file a correction.

jiib is a touchscreen control surface for **Klipper / Moonraker** 3D printers: phones through
tablets, portrait and landscape, dark + light + user-custom themes, S/M/L text size. It is
primarily *control-surface* software that doubles as a well-formatted status display during a
print. The **Nexus 7 2013 (Adreno 320)** is the performance floor.

## Reading order (the law, in order)

1. **`CLAUDE.md`** — design philosophy + non-negotiables (icon law, carve-outs, fill-the-space).
2. **`LAYOUT.md`** — the two-region **Focus / Field** grammar, the unit **U**, the
   NON-NEGOTIABLES (shared grid, sacred aspect ratios, ratio-only sizing), UAT-1..5.
3. **`COMPONENTS.md`** — the component-class catalog (ListRow, FocusFrame, FootButtonBar,
   FloatingEStop, scrubber, …), stroke/floor table, spacing tokens.
4. **`THEMING.md`** — the seed-generated token system, button-intent colors, the type ramp,
   status shape vocabulary, palette modes, carve-outs.
5. **`PREVIEW_AND_TOKENS.md`** — the preview-first / tokenized-first screen-build convention.

## The grammar in one paragraph

Every screen is built from two regions on one shared grid: **Focus** (one primary item, square
visual content centered) and **Field** (a scrollable list or control surface). Portrait stacks
them full-width; landscape is Focus | Field as 50/50 columns. Either region may be omitted.
Every vertical element is an integer number of the derived unit **U** (constant through
rotation; layouts must survive at 5U). Collections are translucent, outline-only **list rows**;
**buttons are filled** — fill is what says "button." A screen's primary actions pin to the foot
of its list in a **`FootButtonBar`** (the old full-width Gutter region is retired; this is its
successor, and it is optional per page). A red **`FloatingEStop`** overlays the Focus top-left,
printing-only. There is no persistent status bar and no app drawer — the morphing waterfall
home (Print Status) is the root, and context lives inside regions as color on existing elements.

## Button intent = color (the four-class scheme, R5 2026-06-12)

| Intent | Token | Meaning |
|---|---|---|
| **stop** | `stop` (red) | could be destructive (cancel, e-stop, disable steppers) |
| **warning** | `heat` (amber) | could be destructive but part of the process (jog, load filament, resets) |
| **go** | `go` (green) | the screen's EXPECTED action (Print, Load, Save, accept) |
| **accent** | `accent` | neutral items and plain navigation (Back, Home) |

Neutral/outline is retired as a button intent. Back is always the **first** button in a
`FootButtonBar`. Full law + worked examples: `THEMING.md`.

## Visual north star of record

The **as-built Spoolman screen**, the **jiib sketch sources**
(`.claude/skills/sketch-findings-jiib/sources/`, incl. `themes/default.css` and the
`004-scrubber-style` control), and the **`THEMING.md` token law**. Type is Geist + Geist Mono
(tabular numerals for live data).

## Historical reference (superseded — do not build from these)

`reference/` (`hifi.css`, `Print Status Hi-Fi.html`) and `images/*.png` are the original
pre-redesign hi-fi bundle. They were canonical until the 2026-06-09 jiib redesign (sketches
001–004) superseded the look: tile grids → lists, three regions → two, transparent+glow
controls → filled buttons, octagon status glyph → square-✕ `StatusStop`. They remain useful
for archaeology only; nothing in them overrides the five docs above.

## Assets

- **Fonts:** Geist + Geist Mono (bundled). Material Symbols Outlined font for icons, rendered
  by ligature via the `JiibIcons` registry — never auto-pick a glyph (owner law, `CLAUDE.md`).
- **Custom printer-domain glyphs** (nozzle, bed, bed-tilt, spool) are owner-approved vector
  drawables converted from `img/*.svg` source art.
