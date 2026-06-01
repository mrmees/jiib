# Dinghy Display — project notes

## Design philosophy (bake into every screen)
- **Focus / Field / Gutter layout grammar.** Every screen is built from three regions —
  Focus (one primary item, with square visual content centered), Field (a divisible
  info/control grid), Gutter (touch actions). Portrait stacks them full-width (~40/40/20,
  gutter up to 2 rows); landscape is a single grid — Focus | Field as 50/50 columns with a
  full-width gutter row sharing the same column lines. Either Focus or Field may be omitted.
  No persistent status bar — context lives inside a region. See LAYOUT.md.
- **Layout non-negotiables (see LAYOUT.md ⚠ section):** (1) everything tabular on one shared
  grid — region divides align with gutter button edges/centers; (2) aspect ratios are sacred —
  a square must render square (use `aspect-ratio`, center square content in its cell); the
  1-row-landscape / 2-row-portrait gutter difference is the mechanism that protects this;
  (3) no hardcoded sizes — every dimension is a %, `fr`, `aspect-ratio`, or container-relative
  unit, never absolute px (except hairlines, the touch floor, and the `--fs` text step).
- **Fill the usable space.** This is primarily control-surface software that doubles as a
  nicely formatted display during standby / normal printing. Every screen should fill its
  usable area with useful, structured information. Empty/negative space is only acceptable
  when it is a *deliberate* focus choice, not a layout gap. Prefer a *useful control* over a
  passive status badge, and encode status as color on an existing element (e.g. axis labels
  green=homed / amber=unhomed) rather than spending a cell on an indicator. Choose items that
  are genuinely useful to the screen's current function.
- **Outline-led, touch-first controls.** Interactive elements are bounded by a 2px outline +
  soft glow on a transparent fill; primary/pressed states tint faintly with the accent. Touch
  targets are big (≥64px tall) — built for gloved, greasy, fat-fingered taps at arm's length.
- **Structured blocks over loose pills.** Prefer structured, aligned blocks of information to
  scattered chips.
- **Button intent = color, by SAFETY of the action** (esp. the gutter). Green = safe /
  non-destructive (accept, done, **Back** — backing out changes nothing) · Blue/accent = ordinary
  physical command, no special hazard (home, unload, fan) · Amber(yellow) = proceed at peril /
  caution (toolhead jog motion, load/heat filament, reset, undo, unexpected live change) · Red =
  destructive or dangerous (stop/e-stop, disable steppers [loses homing], force-move armed, host
  interruption) · White = basic setting adjustment. Overridable per case. See THEMING.md for the
  full spectrum + worked examples.
- **Icons: never the same glyph twice on one screen.** If you'd repeat one, use a 1–3 letter
  text label instead (e.g. "XY"/"Z" homes vs. arrows; chevrons for Z vs. arrows for the XY pad).
- **Square the smallest buttons.** When a Field stacks multiple button rows, the shortest row's
  height should equal its per-column width (equal spans) so the smallest targets are square.
- **Dense cells drop labels.** At ≥3 columns (portrait) / ≥6 (landscape), a cell shows a single
  icon or ≤3-char value scaled to ~75% of its constraining dimension — no text labels (the Gutter
  is exempt; it keeps icon+label). When a cell must show a value AND its source, overlay the value
  on a large background glyph (e.g. the big axis letter behind the live X/Y/Z value).
- **Theme-able via tokens.** Dark-first baseline; everything routes through semantic tokens
  (`--bg/--surface/--text/--outline/--accent/--heat/--go/--stop` …). See THEMING.md.

## Hi-fi visual language
- Type: Geist + Geist Mono (tabular numerals for live data).
- Accent: cool blue (signature). Heat: amber (nozzle/bed). Go/Stop: green/red.
- Shape: soft — 22px cards, 16px controls, pill chips.
- Motion: alive — progress fills + sheen, status dot breathes, ring draws on.
- Files: `hifi.css` (tokens + components), `Print Status Hi-Fi.html` (design canvas).
