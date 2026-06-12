# Phase 22: Performance & Architecture Refactor - Context

**Gathered:** 2026-06-08
**Status:** Ready for planning

<domain>
## Phase Boundary

A release-mode performance & architecture refactor measured against the Adreno-320 floor
(flox = LineageOS 18.1 / API 30, Adreno 320 / 2 GB / 1920×1200 / armeabi-v7a). The owner is
noticing navigation/interaction lag; this phase fixes the hot paths so it goes away. **Visual
behavior is unchanged** (with one corrected premise — see D-08/D-09 below).

**In scope:** recomposition discipline (`@Stable`/`@Immutable`, `derivedStateOf`, state hoisting,
`ImmutableList`/`ImmutableMap`, `remember` for allocations), state-plumbing efficiency (push flow
collection down to consuming screens), **two architectural restructures** (split the
`PrintStatusScreen` god-component; push the 28 `AppShell` flow-collections down into screens),
genuine overdraw reduction, AndroidView interop hygiene (the three Views surfaces +
graph/webcam/image decode confirmed leak-free and not GC-churning), and image/thumbnail/webcam
decode paths.

**Out of scope (reassigned — see Deferred Ideas):** the H.264-while-rotating fix (→ Phase 24),
the FGS notification-icon fix, spool transient-failure robustness hardening, Pause/Resume + Tune
gutter wiring (→ Phase 25), interaction-grammar/page redesigns (Phase 23), touch-target/scaling
conformance (Phase 24), Doze/always-on/signed-APK ship work (Phase 25), and the 6-DataStore
consolidation (cold-start tech debt, not nav-lag — explicitly "no action required" per CONCERNS).

</domain>

<decisions>
## Implementation Decisions

### Refactor Aggressiveness
- **D-01:** **Full restructure.** Do the surgical hot-path fixes AND the two high-blast-radius
  architectural moves CONCERNS flags as the actual root causes:
  1. **Split `PrintStatusScreen.kt`** (1584-line god-component) at natural seams —
     `PrintStatusFocus` / `PrintStatusField` / `PrintStatusGutter` / `PrintStatusPreviews` — so each
     slice becomes an independently-restartable Compose scope.
  2. **Push the 28 `AppShell` `collectAsStateWithLifecycle` collections down** into the screens that
     actually consume each value. The shell should collect only what it directly renders
     (connection status, active screen id, drawer open state). This kills the wide-recomposition
     root cause where any shell update re-evaluates all 28 collected flows.
  Rationale: these are root causes, not cosmetics; Phase 23 explicitly expects structural work to
  land before redesigns ("if a screen needs both overhaul + optimize, do it once"); and there are
  3 phases (23/24/25) of runway + the host suite + on-device gate to catch regressions.
- **D-02:** **The P0 fixes are mandatory regardless of aggressiveness:** annotate `PrinterState`
  and every nested state value type (`HeaterState`, `OutputLiveValue`, `Screw`, `FanState`, etc.)
  `@Immutable`; replace `Map<>`/`List<>` fields with `ImmutableMap`/`ImmutableList`
  (`kotlinx-collections-immutable` — planner must verify/add to `libs.versions.toml`); fix the
  inline lambda slots on `PrintStatusScreen` (lines ~490/522) passed to `ScreenScaffold`.
- **D-03:** **Priority bar:** P0–P2 from CONCERNS are core scope. P3 nits (per-call
  `SimpleDateFormat`, `errorLines` sequence flow) are done **opportunistically** when a file is
  already open for a higher-priority fix — not as standalone tasks.
- **D-04:** **6-DataStore consolidation is OUT.** It's a cold-start concern, not nav-lag, and
  CONCERNS marks it "no immediate action required." Leave the 6 stores as-is.

### Measurement Strategy
- **D-05:** **Baseline-first, manual `gfxinfo framestats`.** Capture a release-mode baseline on
  flox for each suspect screen BEFORE touching code, fix, then re-capture the same screens for the
  before/after SC1 demands. No new tooling.
- **D-06:** **Baseline sweep set** (seeded from CONCERNS; "navigation lag" is the reported symptom,
  so include transitions, not just steady-state): home/`PrintStatusScreen` steady-state at the 4 Hz
  emission plane · App Drawer open · navigation into Files / Console / temp-graph / webcam · a
  representative control screen (e.g. Move/scrubber). The audit may add screens; this is the seed.
- **D-07:** **Macrobenchmark module stays parked.** Do NOT wire the pending
  `macrobenchmark-module-wiring` todo this phase — `FrameTimingMetric` may not work on API 23 (falls
  back to gfxinfo anyway) and it's real setup cost for a solo measurement need. Manual gfxinfo is the
  gate. (Baseline Profile is a documented no-op on the API-23 floor — out regardless.)

### Overdraw & Visual Fidelity
- **D-08:** **SC3's "stacked outline+glow layers" premise is STALE — no glow is drawn.** Verified
  in-code during discussion: `OutlinedControl` (the most-reused primitive) draws only a 2px border on
  a transparent fill; `ProgressRing` draws track + progress arcs with no glow/shadow layer; the
  `*Glow` tokens (`edgeGlow`, `accentGlow`, `goGlow`, `stopGlow`, `heatGlow`) are defined in the
  token system but **consumed by zero draw code** (grep for any glow-token read outside `theme/`
  returns empty). The glow lives in the LAW (`hifi.css`) and got tokenized, but was never rendered
  (or was pulled). **Planner: do not go hunting for control glow overdraw to flatten — it isn't there.**
- **D-09:** **Retarget SC3 to REAL overdraw sources:** `GraphView`'s per-trace gradient fills
  (CONCERNS P1 — the line-283 comment already flags the Adreno-320 fill-rate cost) and any stacked
  alpha/scrim/dim layers (e.g. the Paused-focus alpha-dim that clipped the ring arc, 2026-06-06 UAT).
- **D-10:** **Visual bar = reads-the-same on flox.** Not a pixel diff. Flattening that's provably
  invisible ships freely; any change that could alter a blend/falloff requires owner side-by-side
  approval on-device. Owner is the gate.
- **D-11:** **Keep the dead `*Glow` tokens.** Do NOT prune them this phase — Phase 23/24 redesigns
  may legitimately revive glow, and deletion touches the token files (blast radius across all themes).

### AndroidView / Decode Hygiene (SC4)
- **D-12:** Guard the `AndroidView` `update` blocks: `GraphViewHost`/`WebcamViewHost` call
  `view.applyTokens(tokens)` (→ `invalidate()` → full `onDraw`) unconditionally on every
  recomposition, forcing graph + webcam redraws on every 250 ms `PrinterState` emission even when
  tokens are unchanged. Cache last-applied tokens in the View and short-circuit on equality.
  Confirm the three Views surfaces + decode paths leak-free and not GC-churning under sustained use.

### Claude's Discretion
- Exact split seams/file names for `PrintStatusScreen` and which flows move where in the `AppShell`
  push-down (preserve behavior exactly; visual unchanged; run the full `@Preview` matrix + on-device
  flox check for any `ScreenScaffold`/layout-touching change — it's a fragile all-screens surface).
- Whether `GraphView` overdraw relief is opacity reduction on secondary traces vs pre-rasterizing
  static portions (setpoint/axis) to an off-screen bitmap — researcher/planner pick per measurement.
- The `kotlinx-collections-immutable` version pin and converter wiring.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### The audit / fix list (start here)
- `.planning/codebase/CONCERNS.md` — the static profiling audit (dated 2026-06-08): ranked P0→P3
  performance bottlenecks with exact `file:line`, plus Tech Debt / Fragile Areas. This IS the
  candidate fix list. NOTE the stale "deferred to Phase 22" labels inside it predate the 22→25
  restructure (see D-08 and Deferred Ideas for the corrections).
- `.planning/codebase/ARCHITECTURE.md` — state plumbing / flow ownership map (informs the AppShell
  push-down).
- `.planning/codebase/STACK.md` — pinned versions; confirm `kotlinx-collections-immutable` inclusion.

### Perf law & gates
- `CLAUDE.md` (project) — "The Big Decision: Compose vs Views on Adreno 320" + recomposition
  discipline rules; baseline-profile is a no-op on API 23; profile in release mode.
- `docs/adr/0001-ui-toolkit-decision.md` (+ Addendum 2) — hybrid Compose/Views rationale and the
  **reframed perf gate** (no frozen frames + responsiveness, not a synthetic FPS number) that SC1
  measures against.

### UI law (for the visual-unchanged constraint)
- `docs/ui_design/CLAUDE.md`, `docs/ui_design/LAYOUT.md`, `docs/ui_design/THEMING.md` — the LAW the
  refactor must not visibly violate (static glow only / no continuous animation; Focus/Field/Gutter).
- `reference/hifi.css` — canonical token/component source (where glow is specified but, per D-08,
  not rendered in code).

### Hot-path files named by the audit (non-exhaustive)
- `app/src/main/java/works/mees/dinghy/state/PrinterState.kt`, `state/PrinterStateStore.kt`
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt` (1584 lines — split target)
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` (28 collections — push-down target),
  `ui/shell/AppDrawer.kt`
- `app/src/main/java/works/mees/dinghy/render/GraphView.kt`, `render/GraphViewHost.kt`,
  `render/WebcamView.kt`, `render/WebcamViewHost.kt`
- `app/src/main/java/works/mees/dinghy/designsystem/layout/ScreenScaffold.kt`,
  `designsystem/icons/SpoolGlyph.kt`, `designsystem/control/OutlinedControl.kt`
- `app/src/main/java/works/mees/dinghy/ui/files/FileRowsAdapter.kt`

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`ColorWheel.kt`** demonstrates the correct cached-`Brush` pattern (sweep gradient pre-built once)
  — the template for fixing `SpoolGlyph`'s per-draw `Brush.linearGradient` allocation.
- **`deleteAllowed` / pure host-tested helpers** show the established "pull logic into a pure,
  host-tested function" pattern — apply when extracting formatters (`fmtDuration`) out of composition.
- **Existing `@Preview` matrix convention** (6 theme combos × portrait/landscape, `PreviewBox`
  paints the themed shell bg) — the regression-safety net for the god-component split.

### Established Patterns
- **Hybrid Compose/Views (ADR-0001):** the three high-churn surfaces (Files list, temp graph,
  Console scrollback) are classic Views hosted via `AndroidView`/`ComposeView`; view-models stay
  toolkit-agnostic (StateFlow to both). The refactor preserves this boundary.
- **State emitted at 4 Hz (250 ms)** on the sample plane — the central perf constraint; everything
  reading `PrinterState` re-executes 4×/sec until D-02 lands.
- **`fsSp(baseSp, t.fs)` font scale** — must be preserved through any extraction (no raw `.sp`).

### Integration Points
- `MainActivity` → `AppShell` → screen graph is the recomposition spine the push-down reshapes.
- `PrinterStateStore` is where derived/formatted values (e.g. duration strings) could be hoisted to
  compute-once instead of per-recomposition.

</code_context>

<specifics>
## Specific Ideas

- The owner caught the stale glow premise from memory ("I thought we already took all the glow
  out?") — confirmed correct in-code. This is the headline finding: don't chase a layer that isn't
  drawn; retarget overdraw work to graph fills + stacked alpha/scrim (D-08/D-09).
- Measurement and visual approval are **owner-gated on flox** — consistent with the established
  on-device iteration loop (Claude edits/builds/installs, owner navigates/eyeballs).

</specifics>

<deferred>
## Deferred Ideas

**Correcting the stale "deferred to Phase 22" labels** (they predate the 22→25 quality-slate split;
Phase 22 is now narrowly perf/architecture):

- **H.264-while-rotating blank fix (Phase-21 CR-01)** → **Phase 24** — its SC3 already owns it
  ("either fixed via re-prepare on surface-size change, or documented as a known v1 constraint").
- **FGS notification small-icon (Bluetooth glyph → 24dp jiib status icon)** → **Phase 25** (release
  hardening). `ic_jiib_foreground` is 108dp (adaptive foreground), not a drop-in; needs a real 24dp
  status glyph — and per the icon-ASK law, the owner picks/approves any new glyph.
- **Spool transient-failure robustness hardening** (5 findings from the Phase-11 Codex review,
  todo `2026-06-04-phase-11-spool-feature-robustness-hardening`) → **Phase 25**.
- **Pause/Resume + Tune gutter button wiring** (currently disabled placeholders; label flips but
  dispatches nothing) → **Phase 25** ("verify both fire real Moonraker calls before v1").
- **Macrobenchmark module wiring** (`macrobenchmark-module-wiring` todo) → stays parked (D-07);
  revisit only if manual gfxinfo proves insufficient.
- **6-DataStore consolidation** → backlog (cold-start tech debt, not this milestone's perf target).
- **Pruning the dead `*Glow` tokens** → revisit in Phase 23/24 if redesigns don't revive glow (D-11).

**Doc-hygiene follow-up (note for a future GSD edit, not this discussion):** the "deferred to Phase
22" notes inside `CONCERNS.md` (H.264 §, FGS §) and `STATE.md` should be corrected to point at
Phases 24/25 so future agents aren't misled.

### Reviewed Todos (not folded)
None reviewed via the todo cross-reference step — deferrals above were surfaced from CONCERNS/STATE
during analysis, not from the pending-todo matcher.

</deferred>

---

*Phase: 22-performance-architecture-refactor*
*Context gathered: 2026-06-08*
