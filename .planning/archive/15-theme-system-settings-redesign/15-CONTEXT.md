# Phase 15: Theme System & Settings Redesign - Context

**Gathered:** 2026-06-05
**Status:** Ready for planning

<domain>
## Phase Boundary

**Port the generative color ENGINE and rebuild Settings to drive it.** The "parallel theme
work" turned out to be a complete generative color-system redesign living in a **sibling repo**
(`../theme_theory/`, `COLOR-SYSTEM.md` + `app/color.js`), far larger than the roadmap's one-line
description. Phase 15 was **scoped down to engine-first** (owner decision this session):

**IN scope for Phase 15:**
1. Hand-port the `color.js` palette generator to Kotlin (OKLCH→sRGB seed→palette), wired into the
   existing `ThemeResolver`/`ThemeTokens` substrate as a **single runtime generate-on-change +
   cache** path (replaces build-time `BakedTokens`).
2. Add the **data pool** (+ directional standards) to the token set; wire it to the pure-color
   heater/temperature surfaces and Move's directional plane colors.
3. **Palette modes** (Colorful / Simple / High-Contrast) as a persisted setting.
4. **Per-profile theme persistence** rework: theme = `(seed, dark, paletteMode, poolShift,
   maxItems, poolOverrides)` (D-08 full-theme-per-profile).
5. **Settings redesign** — hybrid hub + a pushed theme-editor sub-page; all sections scaffolded
   (profiles, connection, appearance, feature-toggles, system/about) with greyed forward entries.

**OUT of scope → dedicated follow-on phase (insert after 15):**
- **Shape-coded status** (octagon/triangle/circle) across all screens — touches every surface.
- Status-from-pool visual recolor + High-Contrast RYG *status* rendering.
- D-5 (back-button color), D-6 (bed-mesh OKLCH sequential ramp), D-8 (force-move color).
- Move's homed/unhomed status-color + force-move lock shape (the *status* parts only).
- The **full conformance sweep** of existing surfaces (token purity / button-intent / FFG /
  ≥64px / `fsSp` / dark-light-custom).
- Reconcile/rewrite `THEMING.md` against `COLOR-SYSTEM.md` (status no longer fixed RYG; surfaces
  pure-neutral; pool added).

> ⚠ **ROADMAP CHANGE REQUIRED:** Phase 15 shrinks to engine-first; a new phase (shape-coded status
> + status-from-pool + conformance sweep + THEMING.md reconciliation) must be inserted after it.
> Formalize with `/gsd-phase` before planning the follow-on. The old "light final conformance
> re-sweep folds into Ship (Phase 21)" still holds for late surfaces 16–20.

</domain>

<decisions>
## Implementation Decisions

### Engine port & persistence
- **D-01:** **Hand-port `color.js` → Kotlin** (no JS engine on Android). `Palette.generate()` is
  pure/dependency-free — it's the piece that lifts in; everything in `theme_theory/app/dinghy.*`
  is sandbox around it. Ship `statusFromPool: true` (the canonical config, not the JS default).
- **D-02:** **Single runtime generation path.** Generate the palette in Kotlin whenever the seed /
  mode / poolShift / maxItems changes (incl. the **default seed at first launch**) and cache the
  sRGB ints; the render loop reads cached values, never does color math. Retires build-time
  `BakedTokens` as the source of truth. Math is ~15 colors + the pool — cheap, once per change.
- **D-03:** **All theme params stored per-profile** — `(seed, dark, paletteMode, poolShift,
  maxItems, poolOverrides)` on the `Profile`; fall back to a global default (the idle / new-profile
  look) when no profile is active. Consistent with Phase-14 D-08 (full theme per printer).
- **D-04:** **Seed-only for chrome — retire per-role `TokenDelta` overrides.** The seed (+ dark /
  mode / poolShift) derives accent, surfaces, text. No per-role chrome override path (one knob that
  can't produce a broken palette). **EXCEPTION (D-09):** data-pool slots ARE individually editable.
- **D-05:** **D-07 fresh-start, no migration** (carried from Phase 14): on upgrade the theme tuple
  starts at the validated default seed; old `themeBase`/`fsChoice`/`themeDeltaArgb` fields are
  replaced, not migrated. `fsChoice` (S/M/L `--fs`) stays a separate, unchanged setting.

### Theme editor UX (Appearance sub-page)
- **D-06:** **Seed picker = touch color wheel/ring + a row of curated preset seed swatches.**
  Gloved-finger friendly; presets cover the 90% case. (Generator mostly cares about the seed's HUE;
  lightness/chroma normalize to the cusp.) Hex entry NOT required (Settings allows keyboard, but the
  wheel + presets is the chosen affordance).
- **D-07:** **Regenerate + retheme on release/settle**, not per-pixel of a wheel drag — protects the
  Adreno-320 floor from a regen-storm. The picker handle moves freely during the drag; the palette
  resolves on lift / swatch-land.
- **D-08:** **Preview = live app retheme + a generated-swatch strip** in the editor (accent,
  `pool[0..n]`, status colors + their shapes). See both chrome and data colors at a glance. (No
  separate sandbox-style preview screen.)
- **D-09:** **Randomize button + per-slot data-pool override.** A "Randomize" button rolls a new
  `poolShift` (cached with the theme — never random-per-render, that breaks stable identity). AND
  after generation the user can **individually edit each DATA-POOL color** to any value
  (`poolOverrides: sparse map<poolIndex, hex>`, persisted in the theme). **Reset** (in the editor)
  clears overrides + poolShift back to the seed-derived set / default seed. *(Owner: applies to
  data-pool colors only. Whether STATUS slots are hand-editable rides with the shape-status
  follow-on phase — not decided here.)*

### Settings information architecture
- **D-10:** **Hybrid structure** — Settings stays a flat token-themed scroll (FFG-exempt) for the
  quick stuff (profiles, dark/light, S/M/L, palette mode, toggles), but the **full theme/seed editor
  opens as its own pushed sub-page** (wheel + per-slot palette is too big to inline).
- **D-11:** **Scaffold all sections now, greyed forward entries.** Sections: profiles, connection,
  appearance, feature-toggles, system/about. Live toggles for shipped features (webcam); not-yet-
  built features (outputs/WebRTC/fine-tune, Phases 17–20) appear as **greyed / capability-gated
  placeholders** (the established forward-entry pattern). Later phases just light them up.
- **D-12:** **System/About = app version + build only** this phase. Reset-to-default-seed lives in
  the **theme editor sub-page** (not System). Printer/Klipper/Moonraker info is NOT duplicated here
  (it's the dedicated Phase-19 System Info surface). Restart firmware/host stays on the Splash
  recovery surface only.

### Pool wiring & defaults
- **D-13:** **Pool wiring this phase = temp/heater surfaces + Move directional.** `GraphView` traces
  → `pool[i]` (retire the hand-picked `violet`); nozzle/bed/chamber readouts on Print-Status + Temp
  screen + heater scrubber → matching **pool index** by canonical order (D-2 resolved: `heat` token
  now means *only* caution; heater identity = `pool[0]` = the `temperature` directional). Move's
  jog-pad/Z-row outlines → `directional.xy` / `directional.z` (D-4). **Move's homed/unhomed status
  color + force-move lock shape stay with the status follow-on.**
- **D-14:** **No arbitrary pool-size cap — cycle infinitely.** Consumers wrap `pool[i % size]`; no
  UI limit on the number of series. `maxItems` (the data-range / status-slot boundary, default ~4
  for nozzle+bed+chamber+headroom) is a generator detail that fully settles when status lands in the
  follow-on — it must NOT impose a user-facing limit. *(Owner: "build to cycle through the available
  list pool infinitely; don't put arbitrary limits on things.")*
- **D-15:** **Default palette mode = Colorful.** Full pool (data + directional + status in color);
  the doc's default, shows the system off. Simple + High-Contrast remain user-selectable.
- **D-16:** **Surfaces go pure neutral now** (generator default) — drop dinghy's faint cool tint
  (`oklch .012` chroma); background flips polarity per the generator. **This supersedes
  `THEMING.md`'s `--bg`** value. (D-1 resolved now, not staged on-device, per owner.)

### Claude's Discretion
- Exact Kotlin module/class shape of the ported generator, the token-bridge derivation of the
  in-between surface tiers (`bg2`/`surface2/3`/`text3`/`hair` — generator emits a slim set; see
  `theme_theory/app/dinghy.js` `tokensFromPalette()`), and how `ThemeResolver` exposes the cached
  palette to Compose + Views — planner/researcher's call within the substrate.
- The `ThemeTokens` field additions for `pool[]` + `directional{temperature,xy,z}` + status slots.
- Theme-switch flicker handling during the rebind (carried open item from Phase 14 D-09).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### The color system (PARALLEL WORK — sibling repo `../theme_theory/`)
- `../theme_theory/COLOR-SYSTEM.md` — **prose source of truth.** Part I (intent: roles, the one
  pool, seed control, light/dark, status-by-shape, directional, palette modes, platform
  constraints), Part II (the grammar / earns-color gate), Part III (per-screen application + the 8
  open questions D-1…D-9), Part IV (the **generator contract** `Palette.generate(opts)` + return
  shape, maintainer handoff, changelog of what it supersedes).
- `../theme_theory/app/color.js` — **CODE source of truth.** The portable, dependency-free generator
  to hand-port to Kotlin (OKLCH↔sRGB, cusp-anchored accent, contrast-ranked pool, status slots).
- `../theme_theory/app/dinghy.js` — the **token bridge** (`tokensFromPalette()`): how generator
  output maps onto dinghy's real token vocabulary + derives the in-between surface tiers dinghy needs.
- `../theme_theory/app/dinghy.html` — sandbox rendering the real app screens against the generator
  (the on-device review surface for the staged calls; A/B layout toggle).
- `../theme_theory/app/README.md` — generator usage / opts summary.

### Existing UI LAW (`docs/ui_design/` — some now superseded)
- `docs/ui_design/THEMING.md` — current token vocabulary + button-intent-by-safety. ⚠ **PARTIALLY
  SUPERSEDED** by COLOR-SYSTEM.md: status no longer fixed RYG (becomes pool-slot + shape), surfaces
  go pure-neutral (D-16), the pool is added. Engine phase already changes surfaces + adds the pool;
  the full reconciliation/rewrite is the follow-on phase.
- `docs/ui_design/CLAUDE.md` — design philosophy + non-negotiables (token-routed chrome; the D-03
  PromptMarkup author-hex carve-out; Settings is the keyboard-allowed, FFG-exempt screen).
- `docs/ui_design/LAYOUT.md` — Focus/Field/Gutter grammar (Settings is FFG-exempt).
- `docs/ui_design/reference/hifi.css` — canonical token/component reference values.

### Existing theme substrate to extend/rewire (`app/.../theme/`)
- `app/src/main/java/works/mees/dinghy/theme/ThemeTokens.kt` — the immutable resolved token set to
  EXTEND with `pool[]` + directional + status slots (note its current "custom-theme scope D-01"
  KDoc is replaced by the seed model).
- `app/src/main/java/works/mees/dinghy/theme/BakedTokens.kt` — build-time baked dark/light tables;
  superseded as source of truth by runtime generation (keep only the validated default-seed output).
- `app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt` — the `StateFlow` resolver
  (`setBase`/`setFs`/`setDeltas`) to rewire to the seed-based generate-and-cache model.
- `app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt` — global theme DataStore (the idle /
  default fallback).
- `app/src/main/java/works/mees/dinghy/theme/compose/DinghyTheme.kt` +
  `app/src/main/java/works/mees/dinghy/theme/compose/LocalTokens.kt` — Compose token boundary.
- `app/src/main/java/works/mees/dinghy/theme/views/ThemeableView.kt` — Views token application.
- `app/src/main/java/works/mees/dinghy/theme/Geist.kt` (+ `FontScale`/`fsSp`) — Geist fonts + S/M/L
  `--fs`; **unchanged** this phase.

### Settings, profile & pool-consumer wiring
- `app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt` — the current flat Settings to
  rebuild (its Appearance section is the single-accent-override being replaced).
- `app/src/main/java/works/mees/dinghy/config/Profile.kt` +
  `app/src/main/java/works/mees/dinghy/config/ProfileStore.kt` — per-profile theme fields
  (`themeBase`/`fsChoice`/`themeDeltaArgb`) → replace with the new tuple.
- `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` — `seedTheme` / `activeProfile
  .toThemeResolved()` / `mutateActiveProfile` / `writeScope` (D-09 persist path, WR-02).
- `app/src/main/java/works/mees/dinghy/render/GraphView.kt` (~`:182-188`) — hard-binds trace
  0=`heat`, 1=`accent`, 2=`violet`; rewire to `pool[i]` (the single screen the exercise turns on).

### Prior phase context
- `.planning/phases/14-multi-printer-switching/14-CONTEXT.md` — D-06/07/08/09 (per-profile full
  theme; theme edited via Settings Appearance acting on the active profile; fresh-start, no
  migration; the `writeScope` lesson).
- `.planning/phases/03-design-system-theming-foundation/03-CONTEXT.md` — the original theming
  foundation decisions (token resolver, baked oklch→sRGB, fail-safe DataStore).

### Recurring lessons (project memory)
- `[[dinghy-font-sizes-too-small]]` — use the established `fsSp(baseSp, t.fs)` scale (15sp metadata
  floor … 30sp+ focus). Applies to every new Settings/editor surface.
- `[[dinghy-compose-write-scope-cancellation]]` — DataStore writes route through
  `AppContainer.writeScope` (process-lifetime), NEVER a `rememberCoroutineScope()` cancelled by
  same-frame nav; read-modify-write inside one `dataStore.edit`.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **The whole Phase-3 theme substrate** is the integration target, not a rewrite: `ThemeTokens`
  (immutable resolved set), `ThemeResolver` (StateFlow), `LocalTokens`/`DinghyTheme` (Compose
  boundary), `ThemeableView` (Views) all stay — the seed generator feeds *into* them.
- **`tokensFromPalette()` in `theme_theory/app/dinghy.js`** already solves the "generator emits a
  slim set, dinghy wants more tiers" gap — port its derivations for `bg2`/`surface2/3`/`text3`/`hair`.
- **`Profile` + `ProfileStore` + `mutateActiveProfile` + `writeScope`** (Phase 14) are the proven
  per-profile persistence machinery — extend the theme fields, reuse the durable write path.
- **`ConfirmGuard`, `OutlinedControl`/`Intent`, the single-setting Scrubber/Numpad pages** —
  reusable for the editor sub-page + any destructive Settings action.

### Established Patterns
- Settings is the **one FFG-exempt, keyboard-allowed conventional screen** (plain
  `Column.verticalScroll`, token-themed sections) — the hybrid hub keeps this; the editor sub-page
  follows the same discipline.
- **Greyed/capability-gated forward entry tiles** is the established pattern for not-yet-built
  features — reuse for D-11's feature-toggle scaffolding.
- **Live retheme on tap + persist target = active profile (fallback global)** (D-09) — the existing
  Appearance pattern; the seed editor follows it (live retheme on settle, D-07).

### Integration Points
- `AppContainer.activeConfig` drives the `MoonrakerService` spine rebind; the theme tuple change
  must NOT disturb the connection rebind (theme is independent of host/port/key).
- `GraphView.applyTokens()` is the pool's first real consumer — pool index must match the
  Print-Status readout index for stable identity (same sensor = same color everywhere).

</code_context>

<specifics>
## Specific Ideas

- **"Cycle the pool infinitely; no arbitrary limits."** (Owner.) The pool must support any number of
  series by wrapping; `maxItems` is an internal generator boundary, never a user-facing cap.
- **Per-slot data-pool editing after generation.** (Owner.) The seed gives a good starting palette;
  the user can then hand-tune any individual data-pool color. Chrome (accent/surfaces) stays
  seed-only.
- **Pure-neutral surfaces, decided now** (not staged on-device) — owner overrode the doc's
  "decide on device" for D-1.
- The `theme_theory` sandbox (`app/dinghy.html`) is the on-device reference for the staged calls
  that DID move to the follow-on (back-button, bed-mesh ramp, force-move, surface-tint-if-it-had-
  been-staged).

</specifics>

<deferred>
## Deferred Ideas

**→ New shape-status + conformance follow-on phase (insert after 15 via `/gsd-phase`):**
- Shape-coded status (octagon = stop / triangle = caution / circle = go) + icon + position across
  every status signal — the load-bearing safety mechanism (COLOR-SYSTEM.md §7, §D).
- Status-from-pool visual recolor; High-Contrast mode's stoplight-RYG *status* rendering; whether
  status slots are user-editable (open from D-09).
- **D-5** back-button color (doctrine green vs artboard red — pick one rule app-wide).
- **D-6** bed-mesh: dedicated perceptually-uniform OKLCH sequential ramp off red/green (vs the
  current accent→neutral→red lerp) — `render/BedMeshHeatmapView.kt`.
- **D-8** force-move toggle color (README green-lock/red-open vs prototype amber).
- Move's homed/unhomed status-color + force-move lock-shape (the *status/shape* parts).
- Full conformance sweep of existing surfaces (token purity / button-intent / FFG / ≥64px / `fsSp` /
  dark-light-custom) against the reconciled LAW.
- Reconcile/rewrite `docs/ui_design/THEMING.md` against `COLOR-SYSTEM.md` (canonicalize the new model).

**→ Sequencing/admin:**
- Update ROADMAP.md: Phase 15 → engine-first; insert the shape-status + conformance phase after it.

### Reviewed Todos (not folded)
- **`2026-06-05-phase-14-review-deferred-wr02-wr03.md` (WR-02 idle `seedTheme` one-shot)** —
  theme-seeding edge; **naturally subsumed** by the Phase-15 `seedTheme`/persistence rewrite. The
  planner should ensure the no-active branch collects the global theme reactively (mirror the active
  branch's `flatMapLatest`) when rebuilding the seed path. WR-03 (webcam null-key) stays deferred to
  Phase 21 (unrelated to theme).
- Other `todo.match-phase` hits (spool hardening, Phase-14 D-04 mid-print switch, console-macro UX,
  files-delete gating, macrobenchmark wiring) were keyword-fuzzy matches, not theme/Settings work —
  left in their own phases/ship.

</deferred>

---

*Phase: 15-Theme System & Settings Redesign*
*Context gathered: 2026-06-05*
