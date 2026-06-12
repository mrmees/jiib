# Phase 18: Preview Harness & Tokenization Foundation - Context

**Gathered:** 2026-06-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Build the design-iteration + adaptability **foundation** so every later UI phase is built
preview-first and tokenized-first with zero new debt. Two co-sequenced workstreams:

1. **Compose `@Preview` harness** — reusable themed fake-state/fixture infrastructure (no live
   Moonraker, no device), a Nexus-7 2013 preview device profile, the multi-state / `fs=L` / RTL-
   spot-check matrix, and a preview-safe Coil/`LocalInspectionMode` image strategy.
2. **Tokenization conventions** — `res/values/strings.xml` + `stringResource()` key convention
   (`<area>_<element>`, `cd_*`, format-args/plurals) and a semantic icon-token registry (token →
   primary glyph/drawable + alternate name, unifying `MaterialSymbol` ligatures and `ic_*`
   drawables).

Plus the debug-gated `start_dest` intent hook on `MainActivity` (mirroring `BenchActivity`'s
`EXTRA_SCENE`) for the must-be-live cases the harness can't cover.

**Scope = infrastructure + convention + 3 exemplar screens ONLY** (PrintStatus, FineTune, Spool).
The exhaustive every-screen backfill (all `@Previews` + ~240 string-literal extraction + icon-
registry migration + `@Stable` riders + `compose-preview-screenshot` regression) is **DEFERRED to
the Phase-22 conformance sweep** as ONE co-sequenced per-screen pass ("do not open every screen
twice").

**Hard boundary:** previews are iteration SPEED, not a substitute for on-device UAT — **flox stays
the system-of-record for performance and reality.** Previews are host-rendered (Layoutlib) and
say nothing reliable about Adreno-320 performance.

</domain>

<decisions>
## Implementation Decisions

### Exemplar screens (the convention proof set)
- **D-01:** Exactly **3 exemplar screens**, each chosen to de-risk a *different* downstream pattern:
  - **PrintStatusScreen** (anchor) — its four `PrintStatusMode` states (Standby / Printing / Paused /
    Terminal{Complete|Cancelled|Error}) are the textbook `@PreviewParameter` multi-state demo. Also
    forces the embedded-`GraphView` (classic-View) placeholder question (see D-06).
  - **FineTuneScreen** (Motion/Extrusion) — proves **present/absent capability-gating + busy-lock
    edge states**, which directly de-risks Phase 19 (Output Controls, capability-gated).
  - **SpoolScreen** — proves the **preview-safe Coil/image strategy** (filament thumbs / QR via the
    `LocalInspectionMode` branch) + dense data fixtures (and exercises a Views-in-Compose picker
    sub-surface, overlapping D-06).
- **Rationale:** PrintStatus + FineTune + Spool = three distinct fixture/pattern archetypes
  (multi-state, capability-gating, image+dense-data) rather than three variations of the same thing.

### Exemplar depth — the template Phases 19-21 inherit
- **D-02:** Each exemplar demonstrates the **"core three + mechanical riders"** template:
  `@Preview` matrix **+** string tokenization **+** icon tokens **+** RTL `start`/`end`-relative
  modifiers **+** tokenized `contentDescription` / ≥48dp touch targets.
- **D-03:** **`@Stable` / stability-report / `ImmutableList` work is EXCLUDED from the exemplar
  template** and deferred to the Phase-22 backfill. Rationale (per tokenization staging doc): it's
  *measure-then-fix*, hot-path-specific, and carpet-`@Immutable`-annotating a mutable class causes
  stale-UI bugs — too risky to bake into a copy-paste template. The mechanical riders (a11y, RTL
  start/end) propagate cheaply and SHOULD be in the template; the risky one stays out.
- **Why this matters:** whatever the exemplars do IS the convention Phases 19-21 copy. Riders not
  shown in the exemplars won't get built into the later phases either.

### Classic-View (non-Compose) surfaces
- **D-04:** **Exclude** `GraphView`, `BedMeshHeatmapView`, and `WebcamView` from dedicated Compose
  previews (`@Preview` is Compose-only) — documented as a known limitation.
- **D-05:** Add a **`LocalInspectionMode` placeholder branch** (same mechanism as the Coil image
  strategy) so screens that *embed* a View surface (e.g. PrintStatus's heater-trace `GraphView`)
  still preview cleanly — render a labeled stand-in box, not a blank/broken region.
- **D-06:** Live/perf truth for the View surfaces stays on **`start_dest` + flox + the existing
  `BenchActivity`/`ViewsBenchScene` path**. No new standalone View-rendering harness is built this
  phase (rejected as scope creep + still-host-rendered, so not flox truth anyway).

### Icon-token "alternate name" model
- **D-07:** This phase's goal for the alternate = **easy swap/redefine + localisation**: each
  semantic token (`DinghyIcon.Back`) carries a **primary reference** (Material Symbols font ligature
  OR custom `ic_*` drawable — the token type resolves transparently to either) **plus an
  alternate/canonical name** that is the **one-place remap handle** a community fork edits to switch
  the whole app to its own icon set without find-replacing ligatures across call sites.
- **D-08:** **Keep the icon token and the label string-token SEPARABLE** at the presentation layer
  — do NOT fuse them into a single "labeled-icon" primitive. This is a forward-compat requirement
  for the deferred icon/text/combo presentation mode (see Deferred Ideas).

### Claude's Discretion (left to research/planner)
- **`start_dest` readiness-gate interaction** (bypass vs no-op-until-Klippy-Ready) — owner explicitly
  left this to the planner. Note the caveat from the staging doc: a live screen's *content* depends
  on Moonraker/Klippy state, so `start_dest` lands on the screen with whatever real state exists; it
  is the SECONDARY tool (preview harness is primary for deterministic state-injected review).
- **Fixture-module location + naming convention** — must be reachable from the `main` sourceset so
  `@Preview` functions (rendered by the debug tooling) can use it; planner confirms exact location
  and naming.
- **Where the preview-first / tokenized-first convention is documented** so Phases 19-21 actually
  follow it (docs/ vs a module README vs CLAUDE.md section) — planner's call.
- **Build-wiring fix:** verify `compose-ui-tooling` is `debugImplementation` (renderer, stripped from
  release) and `compose-ui-tooling-preview` is on the compile classpath. ⚠ Currently
  `compose-ui-tooling-preview` is plain `implementation` (ships the preview-annotation lib in
  release) — planner/researcher confirms whether to leave or move it.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Primary spec — the two co-sequenced staging notes (out-of-repo)
- `../parallel_dinghy/phase-preview-harness-staging.md` — full intent for the `@Preview` harness:
  fake-state infrastructure (themed tokens / printer fixtures / `@PreviewParameter`), the multi-
  preview dimension matrix (print states, 6 theme combos, Nexus-7 device profile, `fs=L`, RTL
  spot-check, edge states), known limitations (classic Views, no live data, Coil blank in preview),
  the hard on-device-UAT boundary, the `start_dest` intent hook design, screenshot-regression as a
  future bolt-on, and the Open Planner Work list. (Absolute: `/mnt/e/claude/personal/github/parallel_dinghy/phase-preview-harness-staging.md`)
- `../parallel_dinghy/phase-tokenization-staging.md` — full intent for tokenization: Android
  string-resources standard + the IN/OUT scope boundary (tokenize app vocabulary; pass-through
  filenames/console/macro/Moonraker data raw), key-naming convention, pseudolocale + lint
  completeness gates, the icon-token registry design, the per-screen "sweep riders" (a11y, RTL,
  `@Stable`) and their Phase-22 timing, and Open Planner Work. **MUST be co-sequenced with the
  preview sweep** — when a screen gets its `@Preview`, it also gets strings + icons tokenized in the
  SAME edit. (Absolute: `/mnt/e/claude/personal/github/parallel_dinghy/phase-tokenization-staging.md`)

### UI / theming LAW (already binding on this project)
- `docs/ui_design/CLAUDE.md`, `docs/ui_design/LAYOUT.md`, `docs/ui_design/THEMING.md` — Focus/Field/
  Gutter grammar, semantic token system, button-intent colors, `--fs` text-size. Tokens render in
  previews via the theme provider (see code_context).
- `docs/adr/0001-ui-toolkit-decision.md` (+ Addendum 2) — the Compose + classic-Views hybrid
  decision and the reframed perf gate (no-frozen-frames + responsiveness); explains WHY the View
  surfaces (D-04..D-06) exist outside Compose.

### Roadmap
- `.planning/ROADMAP.md` §"Phase 18" (lines ~787-802) — goal, 5 success criteria, the Phase-22
  deferral, and the STANDARD research note naming the two staging docs as primary spec.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Theme provider entry point:** `app/.../theme/compose/DinghyTheme.kt:37-63` — has a
  benchmark/test overload `DinghyTheme(resolver: ThemeResolver, content)`. A `@Preview` wraps a
  screen as `DinghyTheme(resolver = ThemeResolver(<seed>)) { Screen(...) }`; `LocalTokens.current`
  (`theme/compose/LocalTokens.kt:20`, a `staticCompositionLocalOf<ThemeTokens>`) then resolves.
  `ThemeResolver.bake(tuple)` (`di/AppContainer.kt:455`) is the pure bake fn — the seed source for
  the six {Colorful,Simple,High-Contrast}×{light,dark} preview combos.
- **`fontScale = 1f` is pinned** at `DinghyTheme.kt:48` — confirms NO OS-fontScale preview axis; the
  app's own `fs` (`fsSp(base, fs)`) is the only text-size axis (use `fs=L` for the overflow check).
- **`BakedTokens`** (`theme/BakedTokens.kt:40-126`, `TokensDark`/`TokensLight`) — the only sanctioned
  home for literal sRGB; fail-safe defaults if runtime bake throws.
- **`PrintStatusMode`** (`ui/printstatus/PrintStatusMode.kt:23-38`) + pure `classifyPrintStatus()` —
  the 4 states for the PrintStatus `@PreviewParameter` provider.
- **`BenchActivity` `EXTRA_SCENE` pattern** (`bench/BenchActivity.kt:62`,
  `intent.getStringExtra(EXTRA_SCENE)`) — the precedent the `start_dest` hook mirrors on
  `MainActivity`. `ViewsBenchScene` already renders `GraphView`/`TempGraphView` (the existing View
  path for D-06). Application id = `works.mees.dinghy` → `am start -n works.mees.dinghy/.MainActivity --es start_dest <Dest>`.
- **Dev-enable flag** (NOT `BuildConfig.DEBUG`): `dev_cycler_enabled` in DataStore
  (`theme/ThemePrefs.kt:179`), read via `AppContainer.devCyclerEnabled` (`di/AppContainer.kt:242`),
  set via `setDevCyclerEnabled()` (default FALSE). The `start_dest` hook reuses THIS gate. Existing
  `DevThemeCyclerOverlay` (`ui/shell/DevThemeCyclerOverlay.kt`) is the dev-UI precedent.
- **`MaterialSymbol`** (`designsystem/MaterialSymbol.kt:31-44`, `name=ligature` render primitive,
  subsetted `material_symbols_outlined.ttf`) — the registry sits ABOVE it and becomes the new subset
  source. 56 `MaterialSymbol(...)` call sites; 7 custom `ic_*` drawables; 35 `painterResource` sites.

### Established Patterns
- **Navigation = flat enum + `when(dest)`:** `Dest` (`ui/route/TopRoute.kt:37`, 15 values:
  PrintStatus, Temperature, Move, Extrude, Files, Macros, Console, Calibration, FineTune, Webcam,
  Spool, Devices, Theme, Settings, About) rendered by `AppShell` `when(dest)`
  (`ui/shell/AppShell.kt:496-711`). `ShellNavState` (`ui/shell/ShellNavState.kt:43-124`) is hoisted
  in `RootController`; setting `dest` IS a direct jump (no deep-link infra) — so `start_dest` just
  seeds the initial `dest`.
- **Strings: 0% tokenized today** — NO `res/values/strings.xml`, 0 `stringResource()`, ~262
  hardcoded `Text("...")` + label/contentDescription literals. (Backfill of all ~240 → Phase 22; this
  phase tokenizes only the 3 exemplars + establishes the master file + convention.)
- **Write-scope cancellation trap** ([[dinghy-compose-write-scope-cancellation]]) — any DataStore
  write for the dev-enable/`start_dest` plumbing must route through the process-lifetime
  `AppContainer.writeScope`, never a composition scope.
- **Font-size floor** ([[dinghy-font-sizes-too-small]]) — exemplar previews must use the established
  `fsSp(base, fs)` scale; `fs=L` overflow check is mandatory in the matrix.

### Integration Points
- `MainActivity.kt:30-56` (`onCreate`, currently no intent handling) — where the `start_dest` extra
  is read and used to seed `ShellNavState.dest`; debug-gated via `devCyclerEnabled`; planner decides
  readiness-gate interaction (`RootController` gates on Klippy Ready).
- `app/build.gradle.kts:103-104` — the `compose-ui-tooling` (`debugImplementation`) /
  `compose-ui-tooling-preview` (currently `implementation`) wiring to verify/correct.
- Classic-View interop hosts (`render/GraphViewHost.kt`, `render/BedMeshHeatmapHost.kt`,
  `render/WebcamViewHost.kt`, all `ThemeableView`) — the embed points where the D-05
  `LocalInspectionMode` placeholder branch is inserted.

</code_context>

<specifics>
## Specific Ideas

- Owner's exact framing on icons: "every icon has a token along with an alternate name"; current
  goal = "easy swap/redefine and localisation." (→ D-07/D-08.)
- Owner picked Spool deliberately as the third exemplar — it's where the image/Coil preview-safe
  strategy and dense fixtures get proven (not an arbitrary pick).
- Previews + Live Edit for fast layout iteration → deploy to flox for performance + final truth.
  Strictly better than the prior Q&A-only loop, without weakening the device gate.

</specifics>

<deferred>
## Deferred Ideas

- **Icon / text / icon+text combo presentation mode** — owner wants this *eventually*: a per-control
  option to show the glyph, the text label, or both. This is a NEW user-facing capability → its own
  future phase, NOT Phase 18. Forward-compat is preserved by D-08 (keep icon token + label string-
  token separable so the combo mode composes them later without re-plumbing).
- **Exhaustive every-screen backfill** — all remaining `@Previews`, ~240 string-literal extraction,
  full icon call-site migration, a11y/RTL/`@Stable` riders, and the `compose-preview-screenshot`
  golden-image regression net — all run as ONE co-sequenced per-screen pass in **Phase 22**
  (verification-and-release). Recorded here per success-criterion #5.
- **`@Stable` stability-report + `ImmutableList` migration** — measure-then-fix, deferred to Phase 22
  (excluded from the exemplar template per D-03).
- **Test-matcher migration** (`onNodeWithText("Back")` → resource-id / `testTag`) — required side-
  effect of externalizing strings; only the 3 exemplars' tests are touched this phase, the rest ride
  the Phase-22 backfill.

### Reviewed Todos (not folded)
Three pending todos matched on the generic "ui"/"phase" keyword but are NOT Phase-18 foundation work
(reviewed and left in the backlog):
- *Phase 11 spool feature robustness hardening (Codex review)* — belongs to the Spool feature, not
  the preview/tokenization foundation. (Note: Spool is an *exemplar* here, but this is functional
  robustness, not tokenization.)
- *Bookmarked macros screen wastes vertical space — size to fit ~9-12 then scroll* — a Macros-screen
  layout fix, its own surface.
- *Dev theme/printer cycler overlay no longer drag-relocates* — a dev-overlay bug; tangential to the
  `start_dest` hook but not part of this phase's scope.

</deferred>

---

*Phase: 18-preview-harness-tokenization-foundation*
*Context gathered: 2026-06-06*
