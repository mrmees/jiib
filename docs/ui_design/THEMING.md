# Theming — Dinghy Display (hi-fi token system)

> This documents the **production token vocabulary** used in `reference/hifi.css`.
> (An earlier wireframe explorer used friendly aliases like `--paper`/`--ink`/`--a1`;
> ignore those — the names below are canonical.)

User customization is a first-class feature. Light/Dark is the baseline; a user can go
further and override colors. To keep that cheap, **every component references semantic role
tokens — never a raw color.** A theme is just a remap of those tokens.

## How themes are applied

In the reference, the theme lives on the screen root via a class (`.screen.light`); the dark
values are the `:root` defaults. In production, put the dark set on `:root` (or `[data-theme="dark"]`)
and the light set on `[data-theme="light"]`, and flip the attribute on the app root. A user's
**custom theme** is the same mechanism: override any role token at the root and the whole UI
follows — no component edits.

## Role tokens (exact values)

| Token | Role | Dark | Light |
|---|---|---|---|
| `--bg` | app background | `oklch(0.17 0.012 255)` | `oklch(0.966 0.004 255)` |
| `--bg-2` | sunken well / inset | `oklch(0.20 0.014 255)` | `oklch(0.93 0.006 255)` |
| `--surface` | raised surface (cards, screen) | `oklch(0.225 0.015 255)` | `oklch(0.995 0.001 255)` |
| `--surface-2` | raised +1 (tracks, wells) | `oklch(0.265 0.017 255)` | `oklch(0.93 0.006 255)` |
| `--surface-3` | raised +2 | `oklch(0.31 0.018 255)` | `oklch(0.88 0.008 255)` |
| `--text` | text strong | `oklch(0.96 0.004 255)` | `oklch(0.27 0.02 262)` |
| `--text-2` | text muted | `oklch(0.72 0.014 255)` | `oklch(0.46 0.02 262)` |
| `--text-3` | text faint | `oklch(0.56 0.016 255)` | `oklch(0.62 0.015 262)` |
| `--hair` | decorative hairline | `rgba(255,255,255,.08)` | `rgba(20,30,55,.11)` |
| `--outline` | **interactive control bound** | `oklch(0.44 0.02 255)` | `oklch(0.74 0.02 262)` |
| `--outline-2` | control, emphasised/hover | `oklch(0.55 0.03 255)` | `oklch(0.6 0.03 262)` |
| `--accent` | signature blue — primary/motion | `oklch(0.66 0.15 255)` | `oklch(0.55 0.18 256)` |
| `--accent-2` | accent, brighter (text/icon) | `oklch(0.74 0.13 255)` | `oklch(0.5 0.2 256)` |
| `--accent-soft` | accent tint (fills) | `…/ .16` | `…/ .12` |
| `--accent-line` | accent outline | `…/ .55` | `…/ .5` |
| `--accent-glow` | accent glow | `…/ .35` | `…/ .2` |
| `--heat` | nozzle/bed (amber) | `oklch(0.79 0.13 66)` | `oklch(0.62 0.16 52)` |
| `--heat-soft` / `--heat-glow` | heat tint / glow | `…/ .16` · `…/ .38` | `…/ .14` · `…/ .22` |
| `--go` | success / confirm (green) | `oklch(0.74 0.15 150)` | `oklch(0.56 0.16 150)` |
| `--go-soft` / `--go-glow` | go tint / glow | `…/ .16` · `…/ .4` | `…/ .14` · `…/ .22` |
| `--stop` | danger / destructive (red) | `oklch(0.66 0.2 25)` | `oklch(0.55 0.21 25)` |
| `--stop-soft` / `--stop-glow` | stop tint / glow | `…/ .15` · `…/ .42` | `…/ .12` · `…/ .22` |
| `--edge-glow` | neutral control glow | `oklch(0.64 0.07 255 / .24)` | `oklch(0.55 0.1 256 / .1)` |
| `--shadow` | drop shadow | `0 10px 30px -12px rgba(0,0,0,.6)` | `0 12px 32px -14px rgba(20,30,55,.3)` |

## Button intent = color (semantic, not decorative)

**Color is determined primarily by the SAFETY of the action** — a spectrum from safe → ordinary →
caution → dangerous. This especially governs the **gutter** (primary actions). Pick the color by asking
"how risky is this tap?", not by the kind of widget.

- **Green** (`--go`) — **safe / non-destructive**: accept, done, commit, and plain **Back** navigation
  (backing out doesn't change printer state, so it is NOT red).
- **Blue / accent** (`--accent`) — an **ordinary physical command with no special hazard**: home, unload
  filament, toggle a fan.
- **Amber** (`--heat` family, "warn") — **proceed at peril / caution**: anything that moves the toolhead
  or drives heat/filament where a mistake can crash or burn — jog motion, load/heat filament — plus
  reset / undo / unexpected live change.
- **Red** (`--stop`) — **destructive or dangerous**: stop / e-stop, disable steppers (loses the homing
  state), force-move while armed, host interruption.
- **White / neutral** (`--text` on `--outline`) — basic setting adjustment / secondary follow-up.

These are defaults, overridable per case, but keep them consistent — color *is* the affordance signal.

**Worked examples (the v1 panels):**
- *Move gutter* — **All** (home, blue) · **Disable** (red, un-homes) · **Back** (green).
- *Move jog pad* — directional arrows keep the blue accent OUTLINE but the ICON carries state: gray
  (unavailable) / amber (normal jog — caution) / red (force-move armed). The force-move toggle is a
  green `lock` when safe, red `lock_open_right` when armed.
- *Extrude gutter* — **Load** (amber, heats + drives filament) · **Unload** (blue) · **Back** (green).

## Shape & type tokens

| Token | Value | Use |
|---|---|---|
| `--r-screen` | `30px` | screen/bezel radius |
| `--r-card` | `22px` | cards |
| `--r-ctrl` | `16px` | controls / buttons |
| `--r-pill` | `999px` | pills / chips |
| `--ui` | `'Geist', system-ui, sans-serif` | all UI text |
| `--mono` | `'Geist Mono', ui-monospace, monospace` | live numeric data (tabular) |
| `--fs` | `1.15` (M) | **user text-size multiplier** — S≈1.0 / M≈1.15 / L≈1.32 |

`--fs` scales type app-wide (the S/M/L user setting); **M is the larger default** tuned for
reading a phone at arm's length (~3 ft). Every font-size is `calc(<px> * var(--fs))` or a
`clamp()` — never a bare px. Wire `--fs` to a persisted user preference.

## Carve-out: macro-authored PromptMarkup author-hex (D-03)

> Bounded exception to "every component references semantic role tokens — never a raw color."

The **Macro Prompt Protocol** (Phase 12) lets a Klipper macro author render rich text inside a
prompt via inline `PromptMarkup` runs: `<color:#rrggbb>…</color>` and `<bgcolor:#rrggbb>…</bgcolor>`.
Those runs render the author's **exact literal hex** (`Color(0xFF000000 or #rrggbb)` in
`PromptMarkupText.kt`) — they do **not** route through the role tokens. **This is intentional and
correct, not a token-purity violation:**

- The hex is **content DATA supplied by the macro author**, the same class of thing as a Spoolman
  spool's filament color — not app **chrome**. A theme remap must not silently recolor a value the
  author deliberately chose (a red "DANGER" run must stay red in light mode too).
- The carve-out is **bounded to markup text runs ONLY.** Every piece of prompt *chrome* — the
  dialog background, header, button outlines/intents, the close control, severity toasts, the
  scrollable Field — stays fully token-routed. The author-hex `Color(...)` constructor in
  `PromptMarkupText` is the **only** raw color in the entire prompt UI.
- **Precedent:** the Spoolman spool-color detail border (Phase 11) already renders an
  author/inventory-chosen color directly as content; this is the same principle applied to inline
  text runs. The protocol's *semantic* button styles (`primary`/`info`/`warning`/`error`/`success`)
  do still map to tokens (`promptStyleColor` → `accent`/`accent-2`/`heat`/`stop`/`go`).

The Phase-21 theme/UI conformance audit should treat author-hex inside PromptMarkup text runs as
sanctioned by this carve-out, not flag it.

## The control language (the outline rule)

Interactive elements are a **2px outline + soft glow on a transparent fill**; primary/pressed
states tint faintly with the relevant color token. The outline bounds the touch target without
competing with content. See `.ctl` and its variants in `hifi.css`.
