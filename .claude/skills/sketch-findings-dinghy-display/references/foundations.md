# Foundations — the cross-cutting design system

Validated across sketches 001/002/003. These rules govern **every** screen in the jiib redesign.
They supersede the old Focus/Field/**Gutter** grammar (the Gutter is removed).

## The unit (`U`) — standard vertical module
Everything vertical is an **integer number of units**. `ListRow` = 1U, foot button = 1U, control
tile = 1U, stepper ≈ 0.95U, a group control = 1–2U, the Focus region = the remaining units.
- **Derived, never hardcoded.** From screen DPI (so 1U is the same *physical* finger-size across
  densities) and the available height.
- **Count flexes, unit stays ~constant:** the constrained **landscape** viewport holds **5 units
  (phone-landscape = the floor) → 7 (tablet)**. Bigger screen → more units, not bigger rows.
- **Derived from the LANDSCAPE height, held constant through rotation.** Portrait inherits the same
  `U` and shows more units (scrolls). A row is the same physical height either orientation.
- Formula: `N = clamp(round(landscapeContentHeight / U_target_from_dpi), 5, 7)`, then
  `U = landscapeContentHeight / N` (flexes slightly to divide evenly — no dead-space remainder).
- **DPI floor:** `U` never drops below the ≥64dp touch minimum on the perf-floor device (Nexus 7 2013).
- **Design rule:** the essential landscape layout must **survive at 5 units**.
- Settings/config surfaces are exempt (denser, per the existing C6 LAW).

```css
/* sketch implementation: U set in JS from the landscape height, applied as a CSS var */
:root { --u: 100px; } /* = (landscapeContentHeight - pad) / N, N∈[5,7] */
.row    { min-height: calc(var(--u) - 12px); }  /* 1U row, minus inter-row gap */
.btn    { min-height: calc(var(--u) - 10px); }  /* 1U button */
```
```js
const U = (deviceLandscapeContentHeight - PAD) / N;   // N=5 phone, 7 tablet
document.documentElement.style.setProperty('--u', U + 'px');
```

## Content vs controls — fill convention
**List items are TRANSLUCENT** (transparent fill, outline only — they are *content*). **Buttons /
control tiles / action bars / cards are FILLED** (`--surface` shade — they are *controls*). This is
what makes a tappable `ListRow` read differently from a button even though both carry an outline.
Selection/active layers accent tint on top.
```css
.row { background: transparent; border: 1.5px solid var(--outline); }   /* content */
.row:hover { background: var(--hair); }
.row.sel { background: var(--accent-soft); border-color: var(--accent-line); border-width: 2px; }
.btn, .control-tile { background: var(--surface); border: 2px solid var(--outline); } /* control */
```

## No labels where structure suffices
**Never label a group with a word** ("Sort", "Filter", "N spools" headers are all removed).
Grouping is carried by **a leading type-icon + outline/fill shade + accent-on-active**. Don't add
embellishment (value labels under active states, redundant chevrons, count badges) that doesn't pass
a real value threshold. Owner: *"context + styling should be all we need to group things naturally."*

## Intent colors (button/control intent = color)
From `THEMING.md`. Pick by asking "how dangerous / what kind of action is this?"
| Intent | Token | Use |
|--------|-------|-----|
| **accent** (blue) | `--accent` / `--accent-line` / `--accent-2` | Ordinary physical command **and the screen's expected primary action** (Home, jog, Load spool, the `–`/`+` on an adjuster) |
| **go** (green) | `--go` | Safe/positive: accept, confirm, Reprint, Add |
| **caution/warn** (amber) | `--heat` | Proceed-at-peril: Pause, Power-off, **Reset a setting / Reset-all** |
| **stop** (red) | `--stop` | Destructive/dangerous: Cancel, e-stop, delete |
| **neutral** (white) | `--text` on `--outline` | Basic setting / secondary nav with no state change |

⚠️ **oklch `color-mix` hazard:** `color-mix(in oklch, var(--heat) …, var(--outline))` interpolates hue
the short way **through red** (amber hue 66 → blue-gray hue 255 crosses hue 0), so caution borders
render orange-red. **Use `--heat` directly** for caution outlines (or add a `--heat-line` token / mix
in `srgb`). `hifi.css` `.ctl.warn` has this bug — fix in Layer-1.

## Icon registry — never auto-pick
Icons come from the owner-curated registry: `img/material-icon-bucket.json` (Material Symbols + usage
notes) + `app/.../designsystem/icons/DinghyIcons.kt`. Rendered from the bundled
`material_symbols_outlined.ttf` by ligature. **Any function without an assigned glyph must STOP and
ASK — never auto-pick** ([[dinghy-never-pick-icons-ask]]). Icon tiles should fill ~70% of `U`.
New/owner-assigned glyphs this redesign → see `.planning/notes/2026-06-09-icon-assignments-redesign.md`.
```css
.msym { font-family:'Material Symbols Outlined'; font-variation-settings:'FILL' 0,'wght' 500,'GRAD' 0,'opsz' 40; }
.control-tile .msym { font-size: calc(var(--u) * 0.72); }   /* ~70% of U */
```

## Focus / Field grammar (no Gutter)
Orientation-agnostic (never "top/bottom"). **Portrait stacks Focus/Field; landscape is Focus | Field
side-by-side.** Default split ~50/50 unless a screen declares its own ratio. No persistent gutter —
the gutter's jobs are rehomed to foot-of-list action buttons + a floating printing-only e-stop + a
separate System page.

## Theme
Real jiib tokens (dark default + `[data-theme="light"]`), Geist + Geist Mono, S/M/L text-size via
`--fs`. Full token set in `sources/themes/default.css` (ported from `docs/ui_design/reference/hifi.css`).
Surfaces are achromatic; the seed accent + filament data colors carry the hue. **Filament swatch colors
are literal data** (THEME-01 carve-out) — everything else routes through semantic role tokens, never
raw color.

## Origin
Synthesized from sketches 001, 002, 003. Sources in `sources/`. Full rationale:
`.planning/notes/2026-06-09-jiib-redesign-direction.md` + `…-component-classes-catalog.md`.
