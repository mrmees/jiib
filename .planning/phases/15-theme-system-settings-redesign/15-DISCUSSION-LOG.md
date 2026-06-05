# Phase 15: Theme System & Settings Redesign - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-05
**Phase:** 15-theme-system-settings-redesign
**Areas discussed:** Parallel-work status, Scope split, Engine port & persistence, Theme editor UX, Settings IA, Pool wiring + defaults

---

## Pre-discussion: the "parallel theme work"

| Option | Description | Selected |
|--------|-------------|----------|
| No separate code — it's the committed substrate | Phase 3/4 substrate IS the parallel work | |
| There's uncommitted/branch work | Real theme work elsewhere | ✓ |
| It's design/spec, not code | Parallel session produced docs only | |

**User's choice:** "theme_theory repo on our machine." → Located `../theme_theory/` (a full
generative color-system: `COLOR-SYSTEM.md` 37KB + `app/color.js` 323-line generator + sandbox).
This reframed the entire phase — the parallel work is far bigger than the roadmap's one-liner.

---

## Scope split

| Option | Description | Selected |
|--------|-------------|----------|
| Split: engine first | Phase 15 = engine + pool + modes + Settings; shape-status + conformance → follow-on | ✓ |
| All of it in Phase 15 | One big phase incl. shape-status across all screens + conformance | |
| Engine its own phase; 15 stays Settings+conformance | Insert new color-engine phase; revert 15 | |
| Let me think / discuss | Talk through slicing first | |

**User's choice:** Split, engine first.
**Notes:** Shape-coded status touches every screen; the generator port + pool are substantial. Each
piece stays independently shippable/verifiable. Triggers a roadmap change (insert follow-on phase).

## Open-questions review status

| Option | Description | Selected |
|--------|-------------|----------|
| Not reviewed on-device yet | Decide architectural ones now, stage on-device calls | ✓ |
| Reviewed — decisions made | Capture the 8 answers | |
| Reviewed in browser only | Some calls still need the real screen | |

**User's choice:** Not reviewed on-device yet.
**Notes:** Architectural calls (gen model, scope, defaults) decided this session; the "pick on the
real screen" calls (D-5/D-6/D-8) ride with the follow-on. D-1 surfaces was decided now anyway.

---

## Engine port & persistence

| Option | Description | Selected |
|--------|-------------|----------|
| Single runtime path | Generate on change (incl. default at launch) + cache sRGB | ✓ |
| Bake default + runtime for custom | Baked default-seed palette, runtime only for custom | |
| All per-profile | seed+dark+mode+poolShift+maxItems all per profile | ✓ |
| Look per-profile, structure global | maxItems+poolShift global | |
| Seed-only, retire overrides | Seed is the whole theme; drop TokenDelta role overrides | ✓ |
| Seed + keep power-user overrides | Keep per-role override substrate on top | |

**User's choice:** Single runtime path · All per-profile · Seed-only (retire chrome overrides).
**Notes:** Hand-port color.js → Kotlin assumed. D-07 fresh-start, no migration. (Per-slot data-pool
editing added later under Theme editor — that's the one override path kept, pool-data only.)

## Theme editor UX

| Option | Description | Selected |
|--------|-------------|----------|
| Touch wheel + preset swatches | Wheel + curated preset seeds | ✓ |
| Preset swatches only | Fixed validated seeds, no free picker | |
| Wheel + hex entry | Wheel + optional hex field | |
| Apply on release/settle | Regen+retheme on lift / swatch-land | ✓ |
| Live continuous | Regen+retheme every pixel of drag | |
| Live retheme + swatch strip | App rethemes + editor shows generated set | ✓ |
| Live retheme only | No explicit swatch strip | |
| Dedicated preview screen | Sandbox-like mock-screen preview | |
| Randomize button only | One button rolls cached poolShift | ✓ (amended) |
| Randomize + rotation slider | Button + fine slider | |
| Neither — bake a good default shift | No user rotation | |

**User's choice:** Wheel + presets · On release/settle · Live retheme + swatch strip · Randomize
button **+ per-slot manual edit of data-pool colors**.
**Notes:** Amendment on randomize — "once the colors have been generated, the user should be allowed
to individually edit each one to whatever they want it to be. Only applies to datapool colors." →
`poolOverrides` sparse map persisted in the theme; Reset clears overrides + shift. Status-slot
editability left to the shape-status follow-on.

## Settings IA

| Option | Description | Selected |
|--------|-------------|----------|
| Hybrid: flat hub, editor as sub-page | Flat scroll for quick stuff, theme editor pushed page | ✓ |
| Stay one flat scroll | Everything inline incl. wheel | |
| Full hub + sub-pages | Landing list, every section its own page | |
| Scaffold now, greyed forward entries | All sections; live where built, greyed for 17–20 | ✓ |
| Only build what exists today | Sections only for shipped features | |
| System/About contents (multi) | version+build / printer-info / reset-theme / restart-actions | version+build ✓ |

**User's choice:** Hybrid · Scaffold now · System/About = app version + build only.
**Notes:** Reset-to-default-seed lives in the theme editor sub-page (not System). Printer/Klipper
info defers to Phase-19 System Info; restart actions stay on Splash.

## Pool wiring + defaults

| Option | Description | Selected |
|--------|-------------|----------|
| Temp/heater surfaces only | GraphView traces + readouts → pool index | |
| Temp/heater + Move directional | Also Move jog-pad/Z-row → directional.xy/.z | ✓ |
| Pool size 4 | nozzle+bed+chamber+headroom | (overridden) |
| Pool size 3 | the floor / directional set | |
| Pool size 5 | extra headroom | |
| Default mode Colorful | full pool | ✓ |
| Default mode High Contrast | mono+accent, status RYG | |
| Default mode Simple | near-mono+accent | |
| Surfaces: stage on-device, default tint | provisional cool tint, decide on flox | |
| Surfaces: go pure neutral now | adopt generator pure-neutral | ✓ |
| Surfaces: keep cool tint locked | never revisit | |

**User's choice:** Temp/heater + Move directional · **No arbitrary pool cap (cycle infinitely)** ·
Colorful · Pure neutral now.
**Notes:** Pool size — "the theme should cycle through the available list color pool infinitely; it
shouldn't matter to the theme work… I don't need you putting arbitrary limits on things." Consumers
wrap `pool[i % size]`; `maxItems` is an internal boundary only. Move's homed/unhomed status-color +
force-move shape stay with the status follow-on (only the directional plane colors land now).

---

## Claude's Discretion

- Kotlin module/class shape of the ported generator; token-bridge derivation of the in-between
  surface tiers; how `ThemeResolver` exposes the cached palette to Compose + Views.
- `ThemeTokens` field additions for `pool[]` / `directional` / status slots.
- Theme-switch flicker handling during rebind (carried open from Phase 14 D-09).

## Deferred Ideas

- Shape-coded status across all screens; status-from-pool recolor; HC stoplight-RYG status render;
  status-slot editability.
- D-5 back-button color, D-6 bed-mesh OKLCH ramp, D-8 force-move color; Move status/shape parts.
- Full conformance sweep; reconcile/rewrite THEMING.md vs COLOR-SYSTEM.md.
- ROADMAP update: shrink Phase 15 to engine-first + insert the shape-status/conformance follow-on.
