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

- **Red** (`--stop`) — stop / cancel / back / host interruption.
- **Green** (`--go`) — accept / done / commit a positive action.
- **Amber** (`--heat` family for "warn") — proceed at peril (reset, disable, undo, unexpected live change).
- **Blue / accent** (`--accent`) — functional command with a direct physical effect (move, heat, fan).
- **White / neutral** (`--text` on `--outline`) — basic setting adjustment / secondary follow-up.

These are defaults, overridable per case, but keep them consistent — color *is* the affordance signal.

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

## The control language (the outline rule)

Interactive elements are a **2px outline + soft glow on a transparent fill**; primary/pressed
states tint faintly with the relevant color token. The outline bounds the touch target without
competing with content. See `.ctl` and its variants in `hifi.css`.
