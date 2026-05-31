---
phase: 4
slug: service-shell-settings-print-status-home
status: superseded
superseded_by: docs/ui_design/
superseded_on: 2026-05-31
---

# Phase 4 — UI Design Contract → SUPERSEDED

> **This generated UI-SPEC is DEAD. Do not use it.**
>
> The canonical UI design contract for the entire app now lives in **`docs/ui_design/`**
> (authored by Matthew, 2026-05-31) and is declared LAW in the repo-root `CLAUDE.md`
> (§ "UI Design System"). It supersedes everything that was in this file.

## Where to look instead

| Need | Read |
|------|------|
| Design philosophy + non-negotiables | `docs/ui_design/CLAUDE.md` |
| Focus / Field / Gutter layout grammar | `docs/ui_design/LAYOUT.md` |
| Tokens, dark/light/custom, intent colors, `--fs` | `docs/ui_design/THEMING.md` |
| Canonical tokens + components (source of truth) | `docs/ui_design/reference/hifi.css` |
| Hi-fi mockups (all 10 screens, portrait + landscape) | `docs/ui_design/images/*.png` |

## Phase-4 screens specifically (per the design system)

- **Splash / Connect** — `images/01-splash.png`
- **App Drawer** (swipe-up, full-screen tiles incl. Settings + Power) — `images/02-app-drawer.png`
- **Print Status** (home / monitor; Tune · Pause · Stop, Stop → Confirm guard) — `images/03-print-status.png`
- **Confirm guard** — `images/08-confirm.png` (the primitive built in Phase 3)
- **Settings screen** — conventional Android settings (keyboard allowed); not a hi-fi mockup, follow Android conventions + the token theme.

> Why this file still exists: the earlier `/gsd-ui-phase 3` run generated a full UI-SPEC for the
> pre-design-system shell (persistent rail → edge-swipe drawer → … all obsolete). Rather than delete
> the history, it's reduced to this pointer. **Do not run `/gsd-ui-phase` for Phases 3–4** — the design
> contract is the `docs/ui_design/` bundle, not a generated spec.
