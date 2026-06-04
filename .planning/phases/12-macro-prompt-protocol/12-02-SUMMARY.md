---
phase: 12-macro-prompt-protocol
plan: 02
subsystem: prompt-protocol
tags: [kotlin, reducer, state-machine, conformance, fixtures, host-testable, prompt-protocol]

# Dependency graph
requires:
  - phase: 12-macro-prompt-protocol
    provides: "12-01 PromptEvent model + parseAction/disconnectEvent + parseMarkup (the events the reducer folds)"
provides:
  - "Pure reduce(state, event) state machine + promptView(state) projection + initialPromptState(opts) (verbatim port of reducer.ts/view.ts)"
  - "PromptStateData (internal machine) + 6-key PromptView conformance shape + PromptItem/FooterButton/PromptOpts model"
  - "26-fixture conformance gate GREEN under Dinghy identity (+ 3 expected_by_frontend rows) + EXACT-6-keys structural guard"
  - "PromptReducerTest: 8 hardening.spec.ts edge cases + explicit nested-container-start (row + button_group)"
affects: [12-03, 12-04, macro-prompt-protocol]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure total reducer ported verbatim from the upstream TS oracle (reducer.ts/view.ts) — same (state,event) -> same fresh immutable value; never mutates; never wedges"
    - "Single homogeneous PromptItem data class (not a sealed hierarchy) so the items list mirrors the JS array 1:1 and the view projection is mechanical"
    - "Immutability-by-construction view boundary (data class + read-only List) replaces the JS deep-clone"
    - "View->JSON projection in the test (viewToJson) + a toMatchObject comparator (assertMatchesPartial) — absent != default, exact-length arrays, numeric Int-vs-Double, asserted-null distinct from absent"

key-files:
  created:
    - "app/src/main/java/works/mees/dinghy/prompt/PromptModel.kt"
    - "app/src/main/java/works/mees/dinghy/prompt/PromptReducer.kt"
    - "app/src/test/java/works/mees/dinghy/prompt/PromptReducerTest.kt"
  modified:
    - "app/src/test/java/works/mees/dinghy/prompt/PromptFixtureTest.kt"

key-decisions:
  - "PromptItem modeled as ONE immutable data class with nullable per-type fields (not a sealed hierarchy) so the reducer's items list is a homogeneous List<PromptItem> matching the JS union array exactly"
  - "Immutability is by construction (data class + read-only List) — no deep clone needed; the view.ts clone exists only because JS objects are mutably shared, Kotlin lists are not"
  - "The fixture comparator serializes the PromptView to a canonical conformance JsonObject (viewToJson: snake_case keys, align omitted at center) then partial-matches the fixture JSON — keeps the production model Compose-free and serializer-free"
  - "Dinghy VISIBLE on target-touch-only is proven by the klipperscreen expected_by_frontend row (both map to categories=[touch]); single-run `expected` fixtures replay under the real dinghy+[touch] identity"

patterns-established:
  - "assertMatchesPartial recursion: objects recurse partially, fully-specified arrays compare exact-length element-wise, primitives numeric-compare when expected is a non-string number, JsonNull asserted distinct from absent"

requirements-completed: [PROMPT-01, PROMPT-03]

# Metrics
duration: ~5min
completed: 2026-06-04
---

# Phase 12 Plan 02: Macro Prompt Protocol Reducer + Conformance Gate Summary

**Landed the pure `reduce`/`promptView`/`initialPromptState` state machine and immutable model (internal `PromptStateData` + the 6-key `PromptView` projection) as a verbatim Kotlin port of the clean-room JS `reducer.ts`/`view.ts`, then flipped the 26-fixture conformance gate from RED-by-`fail()` to fully GREEN — Dinghy is now the first native full-v1 prompt reducer (PROMPT-01 parse/reduce-tolerant; PROMPT-03 end/footer/re-entrancy/never-wedge).**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-06-04T22:33Z
- **Completed:** 2026-06-04
- **Tasks:** 2
- **Files modified:** 3 created, 1 modified

## Accomplishments
- `PromptReducer.kt` — `reduce(state, event)` total over the sealed `PromptEvent`: begin/show/end/disconnect/target/size/align + content routing (`appendContent`, `openContainer`/`closeContainer`, `stampAlign`, `targetsMatch`, `freshIdle`), each mirroring the JS line-for-line. Never mutates its input; a garbage/out-of-order stream degrades deterministically (T-12-05 never-wedge).
- The subtle asymmetries match the oracle exactly: pending targets/size **consumed even when the begin is suppressed** (T-12-06), `align` **preserves** on a bad value while `size` **clears**, a **nested container-start is ignored but children still append** to the open container, and a second begin **replaces the active prompt** from `freshIdle(epoch+1)`.
- `promptView` exposes **EXACTLY 6 keys** (`visible`, `title`, `targets`, `size`, `items`, `footer_buttons`) — no internal field (lifecycle/epoch/pending/activeContainer/opts) leaks (T-12-07); `prompt_size` is parsed + tracked in state even though Dinghy's renderer will clamp to full-screen (D-06).
- `PromptFixtureTest` GREEN: all 26 fixtures conform under Dinghy's identity (+ the 3 `expected_by_frontend` rows under klipperscreen→`[touch]` / mainsail+fluidd→`[web]`), with the EXACT-6-keys structural guard and a faithful `toMatchObject` comparator (absent ≠ default, exact-length arrays, numeric `scale:1` Int-vs-Double, asserted `size:null` distinct from absent).
- `PromptReducerTest` GREEN: the 8 `hardening.spec.ts` edge cases re-ported PLUS an explicit nested-container-start test for BOTH `row` and `button_group` (the Codex pre-execute finding — line-88 rule asserted directly).

## Task Commits

1. **Task 1: Pure model + reducer state machine + view projection** - `bd3fa59` (feat)
2. **Task 2: 26-fixture conformance gate GREEN + 8 hardening cases + nested-container** - `94f69ba` (test)

_Task 1 was authored `tdd="true"` but the project-level MVP+TDD gate is inactive (`tdd_mode: false`), so it is a single feat commit (matching the 12-01 precedent) — the RED fixture gate already existed from 12-01, so Task 2 is the GREEN-turning commit._

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/prompt/PromptModel.kt` (created) — `PromptStateData` internal machine + 6-key `PromptView` + `PromptItem`/`PromptItemType`/`FooterButton`/`PromptOpts`/`PromptLifecycle`; all immutable `data class` + read-only `List`.
- `app/src/main/java/works/mees/dinghy/prompt/PromptReducer.kt` (created) — `initialPromptState` / `reduce` / `promptView` + private `freshIdle`/`targetsMatch`/`openContainer`/`closeContainer`/`stampAlign`/`appendContent`/`appendToLastContainer`; Compose-free.
- `app/src/test/java/works/mees/dinghy/prompt/PromptFixtureTest.kt` (modified) — replaced the two 12-01 `fail()` stubs with the real fold-and-compare driver + `assertMatchesPartial` comparator + `viewToJson` projection.
- `app/src/test/java/works/mees/dinghy/prompt/PromptReducerTest.kt` (created) — 8 hardening cases + 2 nested-container cases (10 tests).

## Decisions Made
- **`PromptItem` is one immutable data class with nullable per-type fields**, not a sealed hierarchy — so the reducer's `items` is a homogeneous `List<PromptItem>` matching the JS union array 1:1, and `appendToLastContainer`/`stampAlign`/the view projection stay mechanical (no per-variant casts).
- **The fixture comparator serializes the view to JSON in the TEST** (`viewToJson`), not via `@Serializable` on the production model — this keeps `PromptModel.kt` Compose-free and serializer-free, and the snake_case + `align`-omitted-at-center shaping lives where the conformance contract is asserted.
- **Immutability is by construction** — Kotlin `data class` + read-only `List` means a consumer cannot reach back into reducer state, so the `view.ts` deep-clone is unnecessary (asserted by the `viewIsImmutableByConstruction` regression test).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] KDoc comment-terminator collision broke the first compile**
- **Found during:** Task 1
- **Issue:** A KDoc line in `PromptModel.kt` listing the suppressed internal fields as `pending*/activeContainer/opts` contained the literal `*/` sequence, which terminated the block comment prematurely → a cascade of "Expecting a top level declaration" syntax errors.
- **Fix:** Reworded the prose to `pending bookkeeping, activeContainer, opts` (no `*/`).
- **Files modified:** `app/src/main/java/works/mees/dinghy/prompt/PromptModel.kt`
- **Commit:** `bd3fa59` (folded into the Task 1 commit)

Otherwise the plan executed exactly as written.

## Issues Encountered
- None beyond the KDoc fix above. The full prompt suite is GREEN on the first real run: PromptFixtureTest 2 (corpusGuard + allFixturesConform looping all 26 fixtures + 3 frontend rows), PromptReducerTest 10, PromptParseTest 17, PromptMarkupTest 8 — 0 failures, 0 skipped.

## Next Phase Readiness
- The conformance core is proven host-side: PROMPT-01 (full-v1 parse/reduce) and PROMPT-03 (end/footer/re-entrancy/never-wedge) are satisfied at the reducer layer. 12-03/12-04 consume `reduce`/`promptView` to wire the console action-line stream into the reducer and translate the markup AST + items into the Compose/Views render layer.
- No blockers.

## Self-Check: PASSED

All 3 created files + 1 modified file exist on disk; both task commits (`bd3fa59`, `94f69ba`) are present in git history. Full prompt suite verified GREEN via `:app:testDebugUnitTest` (0 failures across the 4 prompt test classes).

---
*Phase: 12-macro-prompt-protocol*
*Completed: 2026-06-04*
