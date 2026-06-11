---
phase: 22
reviewers: [codex]
reviewed_at: 2026-06-08T23:55:48Z
plans_reviewed: [22-01-PLAN.md, 22-02-PLAN.md, 22-03-PLAN.md, 22-04-PLAN.md, 22-05-PLAN.md, 22-06-PLAN.md, 22-07-PLAN.md]
---

# Cross-AI Plan Review — Phase 22

> Reviewers invoked: **Codex** (gpt-5.5, `codex exec`). Claude (self) skipped for independence
> per cross-AI review rules; Gemini/OpenCode/Qwen/Cursor/CodeRabbit not installed on this machine.

## Codex Review

**Summary**
The plan set is directionally strong: it targets the real hot paths, preserves the “measure on flox in release mode” rule, and avoids the stale glow-overdraw trap. The main weaknesses are execution precision: several plans assume code ownership boundaries that are not quite true, some acceptance checks are too loose or inaccurate, and the biggest refactors are bundled with opportunistic fixes that will make regressions harder to isolate.

**Cross-Plan Strengths**
- Baseline-first release-mode `gfxinfo` is the right gate for this hardware.
- The wave order mostly follows dependency reality: dependency setup → state stability → screen split/View hygiene → overdraw/shell close-out.
- The plans consistently protect out-of-scope items: no macrobenchmark wiring, no DataStore consolidation, no glow-token pruning.
- Human gates are appropriate for flox visual/perf approval, especially GraphView blend changes and Print Status parity.
- Security risk is correctly low; this is a UI/state refactor plus one official JetBrains dependency.

**Cross-Plan Concerns**
- **HIGH:** Plan 02 omits [PrinterStateReducer.kt](/mnt/e/claude/personal/github/dinghy-display/app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt:1) from `files_modified`, but that is where most `List`/`Map` instances are created and assigned into `PrinterState`. `s.heaters + heaterUpdates`, `doubleListOrNull`, `double2dListOrNull`, `profileNames = keys.toList()`, and output `colorData` will need conversion.
- **HIGH:** Plan 07 assumes some screens already receive `container`, but calibration screens like [TiltScreen.kt](/mnt/e/claude/personal/github/dinghy-display/app/src/main/java/works/mees/dinghy/ui/calibration/TiltScreen.kt:82), `BedMeshScreen`, and `ProbeCalibrateScreen` currently take resolved `vm` params. Push-down must pass holders or add wrapper composables, not just “collect inside the screen from container.”
- **MEDIUM:** Manual `gfxinfo` capture quality needs tighter protocol. The plans say exercise 6-10s then dump, but the framestats ring buffer can wrap; capture multiple short windows or dump immediately after each transition.
- **MEDIUM:** Several plans rely on Layout Inspector or preview review without making the evidence repeatable. Compose compiler reports should be explicitly enabled or the summary should record exact Inspector screenshots/counts.
- **MEDIUM:** 22-04 and 22-07 are high-blast-radius refactors with opportunistic fixes mixed in. This is efficient, but it makes perf deltas and visual regressions harder to attribute.

**22-01 Review**
Summary: Good setup plan, but the baseline gate must be absolutely first.

Strengths:
- Correctly pins `kotlinx-collections-immutable` before state migration.
- Captures the irreplaceable before-anchor.
- Includes guardrails against DataStore and macrobenchmark scope creep.

Concerns:
- **MEDIUM:** Because this same plan edits files, executors could add the dependency or `@Stable` before baseline unless Task 1 is explicitly blocking for all edits.
- **MEDIUM:** `@Stable` on [AppContainer.kt](/mnt/e/claude/personal/github/dinghy-display/app/src/main/java/works/mees/dinghy/di/AppContainer.kt:64) is plausible, but it is a promise. The plan should verify no composable-visible mutable public property changes without Flow/Compose notification.
- **LOW:** Baseline docs should record commit SHA, APK variant, orientation, theme/text size, printer state, and exact screen exercise.

Suggestions:
- Split “baseline capture” into a hard pre-edit gate or add “no source edits before owner baseline approval.”
- Add multiple captures per screen and per transition window.
- Record release APK SHA/build timestamp in `22-GFXINFO-BASELINE.md`.

Risk Assessment: **MEDIUM**. Mostly setup, but a bad baseline undermines the whole phase.

**22-02 Review**
Summary: This is the right P0 fix, but the plan underestimates the compile cascade.

Strengths:
- Correctly pairs `@Immutable` with immutable collection field types.
- Keeps reducer internals mutable and only freezes emitted snapshots.
- Uses host tests as the behavior-preservation gate.

Concerns:
- **HIGH:** `PrinterStateReducer.kt` is missing from modified files and task text underplays it. The reducer, not only `PrinterStateStore.kt`, creates most affected collections.
- **HIGH:** `ImmutableMap` as the declared type may not support persistent update APIs directly. The reducer must use `.toImmutableMap()` / `.toImmutableList()` or declare `PersistentMap`/`PersistentList` where mutation-style persistent updates are needed.
- **MEDIUM:** Adjacent socket-driven or hot-path models with `List` fields, such as `Capabilities` and Print Status UI models, should be audited even if not all are migrated.

Suggestions:
- Add `PrinterStateReducer.kt` and relevant tests to `files_modified`.
- Add acceptance checks for no remaining `Map<` / `List<` fields inside `PrinterState.kt` except documented non-hot exceptions.
- Add targeted reducer tests for bed mesh, screws tilt, outputs `colorData`, and position lists after conversion.

Risk Assessment: **HIGH** until the reducer scope is corrected. The idea is sound; the current plan would almost certainly hit compile failures outside listed files.

**22-03 Review**
Summary: Well-scoped, low-risk allocation cleanup.

Strengths:
- Uses an existing local pattern from `ColorWheel`.
- Behavior is effectively unchanged.
- Good parallel candidate with Plan 02.

Concerns:
- **LOW:** A host test cannot meaningfully assert `Brush` instance reuse unless using Compose runtime test infrastructure. Equality of `SpiralRender` is enough for a unit test.
- **LOW:** Ensure the `remember(render)` block is outside the `Canvas` draw lambda; otherwise it will not compile or will not cache at the intended composition scope.

Suggestions:
- Keep tests focused on `spiralRenderFor` equality and behavior.
- Treat brush reuse as code-review verification rather than forcing a brittle instance test.

Risk Assessment: **LOW**. Good plan.

**22-04 Review**
Summary: Necessary refactor, but too much is packed into one plan.

Strengths:
- Splitting Print Status into restartable scopes directly addresses the home-screen hot path.
- Preserves previews and adds owner flox parity.
- Opportunistic fixes are relevant to the same file.

Concerns:
- **HIGH:** The “lambda-slot fix” acceptance is technically shaky. `ScreenScaffold` slot params will still usually be lambdas because values are captured; the real fix is named child composables creating restartable scopes, not eliminating `{ ... }` slot bodies.
- **HIGH:** Splitting a 1584-line screen plus changing grid layout, image request allocation, swatch typing, duration formatting, and launcher grid in one plan increases regression attribution risk.
- **MEDIUM:** `LazyVerticalGrid` may alter measurement/scroll behavior for a tiny fixed tile set inside `ScreenScaffold`.
- **MEDIUM:** Removing per-cell `BoxWithConstraints` needs a concrete sizing source; deriving icon size from column width can accidentally move the same subcomposition problem upward.

Suggestions:
- Reword acceptance: “slot lambdas contain only calls to named top-level composables with stable/minimal params,” not “no inline lambdas.”
- Split Task 2 into separate commits/checkpoints after the structural split.
- Add screenshot capture or preview-render artifacts if available, not only Android Studio visual preview.

Risk Assessment: **HIGH**. Required work, high blast radius.

**22-05 Review**
Summary: Strong AndroidView hygiene plan with a few verification gaps.

Strengths:
- Correctly guards token/chrome changes inside the View.
- Correctly leaves `setFrame`, `setData`, and `setSetpoints` unconditional.
- Includes theme-toggle validation, which catches the main guard failure mode.

Concerns:
- **MEDIUM:** SC4 includes the Console RecyclerView path, but console files are not part of `files_modified` or detailed read/verification.
- **MEDIUM:** `ThemeTokens` is described as only primitives/Colors, but it has at least `pool: List<Color>`. Structural equality still works, but the plan language should be corrected.
- **MEDIUM:** “Flat PSS” and “no GC churn” need thresholds or concrete sample counts.
- **LOW:** `SimpleDateFormat` companion reuse is safe only if binding stays main-thread-only.

Suggestions:
- Add Console view files to read/verification scope.
- Define PSS acceptance, e.g. no monotonic climb after N cycles and within a small MB tolerance after settling.
- Record logcat GC counts/time window in the summary.

Risk Assessment: **MEDIUM**. Implementation is low risk; SC4 proof needs tightening.

**22-06 Review**
Summary: Correctly targets real overdraw, but should measure after Plan 05 before changing visuals.

Strengths:
- Honors D-08/D-11 by avoiding nonexistent glow work.
- Owner-gates a visible blend/fill change.
- Keeps Option B isolated.

Concerns:
- **MEDIUM:** The plan jumps directly to Option A. Since Plan 05 may already reduce redraws, capture a Temperature graph measurement after Plan 05 first.
- **MEDIUM:** “Primary trace” needs a deterministic definition and fallback when series ordering changes.
- **LOW:** Acceptance says Option B can be documented if frozen frames remain, but the phase still needs SC1 closure later; do not mark SC3/SC1 as satisfied by documentation alone.

Suggestions:
- Add Task 0: measure Temperature after D-12 guard, before fill changes.
- Define primary trace selection explicitly.
- If owner rejects line-only secondary traces, retain reduced-alpha mode as the first fallback.

Risk Assessment: **MEDIUM**. The change is isolated, but it intentionally changes visual blending.

**22-07 Review**
Summary: Correct final close-out concept, but this is the riskiest plan because current screen and container wiring do not fully match its assumptions.

Strengths:
- Targets the root shell recomposition problem.
- Includes final `gfxinfo` after sweep and print-loop UAT.
- Correctly keeps drawer-rendered flows in the shell.

Concerns:
- **HIGH:** Calibration screens do not all receive `container`; several take `vm` and callbacks. The plan needs a precise wrapper/holder-passing strategy.
- **HIGH:** AppContainer currently exposes many `Flow`s but does not obviously have a general `stateIn` app scope beyond `writeScope`. Hoisting `activeName`, `activeProfileId`, and `errorLines` to `StateFlow` needs an explicit lifetime/dispatcher design.
- **MEDIUM:** If Plan 06 is skipped or rejected, Plan 07 loses its transitive dependency on 22-05. Add `22-05` as an explicit dependency.
- **MEDIUM:** Moving collection sites can affect holder lifetime, `LaunchedEffect(Unit)` entry resets, and local nav-state behavior. Those need targeted navigation regression checks.

Suggestions:
- First produce a collection audit table: `stays / moves / split`, with target file and parameter change for each moved flow.
- For calibration, either pass holders and collect `holder.vm` inside screen wrappers, or keep those collections in shell until a separate calibration refactor.
- Add an explicit AppContainer scope design before `stateIn`.
- Make recomposition evidence concrete: compiler reports or named Layout Inspector count snapshots.

Risk Assessment: **HIGH**. This is the right architectural move, but the plan needs more mechanical specificity before execution.

**Overall Risk Assessment**
Overall risk is **MEDIUM-HIGH**. The phase goals are achievable and the plans target the right bottlenecks, but Plans 02, 04, and 07 need correction before execution. The most important fixes are: include `PrinterStateReducer.kt` in the immutable migration, correct the calibration/shell push-down assumptions, make `gfxinfo` capture reproducible, and split the largest mixed refactors into smaller verified checkpoints.

---

## Consensus Summary

Only one independent reviewer (Codex) was available this run, so there is no multi-reviewer
consensus to triangulate. The following is Codex's signal distilled to the highest-priority,
must-address items before execution.

### Agreed Strengths
- Baseline-first, **release-mode `gfxinfo` on flox** is the correct perf gate for this hardware.
- Wave order follows dependency reality (deps → state stability → screen split/View hygiene → overdraw/shell close-out).
- Out-of-scope discipline is good: no macrobenchmark wiring, no DataStore consolidation, no glow-token pruning.
- Owner/human gates correctly placed for flox visual + perf approval (GraphView blend, Print Status parity).
- Security risk is genuinely LOW — UI/state refactor + one official JetBrains dependency.

### Agreed Concerns (highest priority — fix before execute)
1. **HIGH — Plan 02 scope gap:** `PrinterStateReducer.kt` is missing from `files_modified` but is where
   most `List`/`Map` instances are actually created (`s.heaters + heaterUpdates`, `doubleListOrNull`,
   `double2dListOrNull`, `profileNames = keys.toList()`, `colorData`). The immutable migration will hit
   compile failures outside listed files. Also: `ImmutableMap` as a *declared field type* may not expose
   persistent-update APIs — use `.toImmutableMap()`/`.toImmutableList()` or declare `PersistentMap`/`PersistentList`.
2. **HIGH — Plan 07 wiring assumption:** Calibration screens (`TiltScreen`, `BedMeshScreen`,
   `ProbeCalibrateScreen`) take resolved `vm`/callbacks, **not** `container`. The "collect inside the screen
   from container" push-down doesn't apply as-written; needs an explicit holder/wrapper strategy. Also,
   AppContainer has no general `stateIn` app scope beyond `writeScope` — hoisting `activeName`/`activeProfileId`/
   `errorLines` to `StateFlow` needs an explicit lifetime/dispatcher design first.
3. **HIGH — Plan 04 acceptance is shaky + blast radius too wide:** The "eliminate inline lambda slots"
   acceptance is technically wrong — slot params stay lambdas; the real fix is *named child composables that
   create restartable scopes*. Reword to "slot lambdas contain only calls to named top-level composables."
   And splitting a 1584-line screen **plus** grid layout + image-request + swatch typing + duration formatting
   + launcher grid in one plan makes regressions un-attributable — split into checkpointed commits.
4. **MEDIUM — `gfxinfo` capture reproducibility:** framestats ring buffer can wrap; capture multiple short
   windows / dump immediately after each transition. Record commit SHA, APK variant, orientation, theme,
   text size, and printer state in the baseline doc.
5. **MEDIUM — SC4 (Plan 05) proof gap:** Console RecyclerView path is in SC4 but Console files aren't in
   `files_modified`/read scope. "Flat PSS"/"no GC churn" need concrete thresholds + sample counts.
6. **MEDIUM — Plan 06 ordering:** Measure the Temperature graph *after* Plan 05 (which may already cut
   redraws) **before** changing visual blending. Don't let SC1/SC3 be satisfied by documentation alone.

### Divergent Views
N/A — single reviewer.

### Overall
Codex rates the phase **MEDIUM-HIGH** risk. Goals achievable, bottlenecks correctly targeted, but
**Plans 02, 04, and 07 need correction before execution.** Top fixes: add `PrinterStateReducer.kt` to the
immutable migration, fix the calibration/shell push-down assumptions (+ AppContainer scope design),
make `gfxinfo` capture reproducible, and split the largest mixed refactors into smaller verified checkpoints.
