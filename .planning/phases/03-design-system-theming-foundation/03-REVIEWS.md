---
phase: 3
reviewers: [codex]
reviewed_at: 2026-05-31
plans_reviewed: [03-01-PLAN.md, 03-02-PLAN.md, 03-03-PLAN.md, 03-04-PLAN.md, 03-05-PLAN.md, 03-06-PLAN.md, 03-07-PLAN.md]
note: "Requested --all; running inside Claude Code so `claude` was skipped for independence. Of the installed CLIs, only `codex` is a different reviewer (gemini/coderabbit/opencode/qwen/cursor not installed). Single independent reviewer this run."
---

# Cross-AI Plan Review — Phase 3

## Codex Review

**Model:** codex-cli 0.134.0 (default model) · **Overall risk: MEDIUM**

### Summary
The phase plan set is strong overall: it tracks the locked D-01..D-14 decisions closely, keeps most scope inside the substrate/harness boundary, and covers the five success criteria with sensible wave ordering. The biggest gaps are not architectural relitigation; they are execution risks around persistence/wiring ownership, token completeness, layout verification rigor, and perf-proof isolation. In particular, `03-01`, `03-05`, and `03-06` need tighter contracts around malformed persisted theme state, graph snapshot/update costs, and how the gallery/live-spine wiring avoids accidentally drifting into Phase 4 app-bootstrap concerns.

### Strengths
- The wave structure is coherent. `03-01/03-02` establish the two headless seams in parallel, `03-03` builds the Compose boundary on top, `03-04/03-05` split reusable primitives from render primitives cleanly, and `03-06/03-07` reserve verification and perf proof for the end.
- `03-01` correctly treats baked sRGB as mandatory, not optional. That matches the API-23 trap and protects the floor.
- `03-01` also preserves D-02 properly: custom theme as base + deltas, not full token persistence.
- `03-03` gets the key toolkit boundary right: one `ThemeResolver` flow into Compose via `LocalTokens`, with `fontScale=1f` at the root.
- `03-04` keeps PRIM-01/03/04 within phase scope and avoids pulling in PRIM-05 or actual command dispatch.
- `03-05` respects ADR 0001: ring in Compose Canvas, graph in classic View, and token push via `applyTokens()+invalidate()` without recreation.
- `03-06` uses a debug source set for the gallery launcher, which is the right packaging boundary and avoids release leakage.
- `03-07` reuses the existing perf harness instead of inventing a new one, which reduces methodology drift.

### Concerns
- **HIGH** — `03-01 Task 2` does not define a clear failure policy for corrupted DataStore values beyond malformed token deltas. The threat model mentions fallback, but the task/acceptance criteria do not require tests for invalid `themeBase`, invalid `fsChoice`, or out-of-range ARGB payloads. Real gap because this phase introduces the persistence mechanism before the editor UI exists.
- **HIGH** — `03-06 Task 1` risks scope creep into Phase 4 by having `GalleryScreen` construct "whatever minimal wiring it needs" for `PrinterStateStore`. Without a stricter boundary this can turn into ad hoc connection/bootstrap logic, which Phase 3 explicitly excludes. The gallery should only consume an already-provided store or a stub/live adapter, not invent app-level ownership patterns.
- **MEDIUM** — `03-01 Task 1` says `BakedTokens.kt` should emit two full `ThemeTokens` instances before `ThemeTokens` is fully defined in Task 2 ("author Task 2 first or co-define if sequencing demands it"). Execution smell inside a single plan; the dependency between generated output shape and the type definition should be explicit.
- **MEDIUM** — `03-01`/`03-03` do not explicitly prove "every component references role tokens, never raw color" beyond the new files. Probably fine (greenfield), but the success criterion is absolute; a lightweight grep/static check would strengthen the claim.
- **MEDIUM** — `03-03 Task 2` relies heavily on manual gallery validation for the "one shared grid" requirement but does not require explicit fixture screens reproducing the critical portrait/landscape alignment cases from `docs/ui_design`. Without fixed validation scenes, `UI-01` can be declared done while still missing the hardest layout cases.
- **MEDIUM** — `03-05 Task 2` is careful about per-frame `Path` allocation, but the host contract still passes `FloatArray` snapshots into `GraphViewHost` on every update and does not explicitly cap snapshot length to pixel width before it reaches the View — unnecessary copying/work could still happen on the UI thread.
- **MEDIUM** — `03-05` does not specify clipping/sanitization rules for graph inputs: empty arrays, constant-value series, NaN/Infinity, or a zero-height range. A render-primitive phase should pin these down because later panels inherit the behavior.
- **LOW** — `03-02` maps font work to `THEME-02`, but the more important correctness issue is API-23 compatibility and design fidelity, not the text-size requirement. Slightly misleading traceability.
- **LOW** — `03-04 Task 2` cancel/apply intent phrasing ("`Intent.Danger/Neutral per the heaters-get-Cancel/Apply rule`") is ambiguous. A reusable primitive should have a single explicit semantic contract for cancel/dismiss intent.
- **LOW** — `03-06 Task 2` accepts two launcher icons in debug. Acceptable, but slightly weakens the "gallery is the temp dev launcher" intent and may cause manual-sign-off confusion.
- **LOW** — `03-07` maps to `UI-01` in front matter but the content is criterion #5 perf proof. Requirement mapping looks off and could confuse downstream audits.

### Suggestions
- Add explicit invalid-prefs tests to `03-01 Task 2`: unknown base string, unknown fs string, malformed/partial delta map, and invalid color payload all fall back deterministically to base theme + default `M`.
- Split `03-01` internally so `ThemeTokens.kt` is created before `BakedTokens.kt`, or state the bake script emits an intermediate table consumed by `ThemeTokens` once defined. Remove the sequencing ambiguity.
- Add a token-purity enforcement step in `03-03/03-04/03-05`: grep/lint for raw `Color(` literals / hex constants in `designsystem/`, `render/`, `gallery/` except `BakedTokens.kt`.
- Tighten `03-06 Task 1` so `GalleryScreen` accepts injected deps (`ThemeResolver`, `PrinterStateStore?`, feed-source selectors) while `GalleryActivity` is the only place allowed to assemble them — keeps app wiring out of the reusable screen.
- Add an explicit "no network connection ownership in gallery" rule to `03-06`. The live-spine demo reads from an existing store or a fake/local provider, not app bootstrap patterns.
- Add one or two named gallery fixtures in `03-06` for `UI-01`: a portrait screen and a landscape screen that exercise sacred-square content, 3-button gutter alignment, and null-focus/null-field cases — makes manual sign-off concrete.
- Add input-edge handling to `03-05`: empty snapshot draws nothing, 1-point draws nothing/a dot, constant series centers cleanly, NaN/Infinity discarded before draw.
- Move downsampling into `GraphView.setData(...)` or the snapshot path with an explicit rule that rendered points never exceed horizontal pixel count — make it an acceptance criterion, not prose.
- Clarify `ScrubberPage` action semantics in `03-04`: cancel/dismiss neutral unless design law explicitly requires danger for a destructive revert.
- Add a release-manifest verification command directly to `03-06`'s automated verification (not only acceptance criteria) for the packaging guard.
- Fix front-matter traceability on `03-07` to point at criterion #5 / render-perf closure rather than `UI-01`.

### Risk Assessment — MEDIUM
Architecture and sequencing are solid; plans mostly implement the locked decisions faithfully. Remaining risk is execution-detail risk, not conceptual: persistence fallback behavior, gallery wiring boundaries, graph edge-case handling, and making the manual layout/perf checks concrete enough that success criteria are proven rather than assumed.

---

## Consensus Summary

Only one independent reviewer ran this pass (Codex). There is no second AI to cross-validate, so treat the items below as *Codex's findings*, not multi-reviewer consensus. They are nonetheless concrete and worth folding in.

### Agreed Strengths
- Wave ordering and the two-headless-seams-then-boundary structure is sound.
- The load-bearing API-23/Adreno decisions are correctly implemented: baked sRGB (not runtime oklch), ring=Compose/graph=Views split, `fontScale=1f`, debug-source-set gallery, perf-harness reuse.

### Highest-Priority Concerns (the two HIGHs)
1. **`03-01` persistence fail-safe is under-specified.** Add tests/acceptance for corrupted DataStore values — invalid `themeBase`, invalid `fsChoice`, out-of-range ARGB — all deterministically falling back to base theme + default `M`. (This *is* the phase that introduces the persistence mechanism, so the fail-safe must be proven here.)
2. **`03-06` gallery wiring risks Phase-4 scope creep.** Constrain `GalleryScreen` to *consume* injected deps (`ThemeResolver`, `PrinterStateStore?`, feed selector) with `GalleryActivity` as the sole assembler; add an explicit "no connection/bootstrap ownership in the gallery" rule.

### Worth-fixing MEDIUM/LOW (cheap, high-leverage)
- Token-purity grep/lint gate (no raw `Color(`/hex outside `BakedTokens.kt`) — turns success criterion #1's "never raw color" from assumed to proven.
- `03-05` graph input sanitization (empty/1-point/constant/NaN/Infinity/zero-range) + cap rendered points to horizontal pixel width as an acceptance criterion.
- Named `UI-01` gallery fixtures (portrait + landscape) exercising sacred squares + 3-button gutter alignment (the "A4 risk" research already flagged) — makes manual sign-off concrete.
- Resolve the `03-01` `BakedTokens.kt`↔`ThemeTokens.kt` definition-order ambiguity.
- `03-06` release-manifest guard as an automated verify command, not just acceptance prose.
- Clarify `ScrubberPage` cancel/dismiss intent contract (`03-04`).
- Traceability nits: `03-02`→THEME-02 and `03-07`→UI-01 mappings both read slightly off (note: `03-07`→UI-01 was a deliberate choice when UI-01 was removed from `03-05` during the checker-revision pass; revisit if it confuses audits).

### Divergent Views
None — single reviewer.
