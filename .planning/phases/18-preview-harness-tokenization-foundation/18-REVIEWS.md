---
phase: 18
reviewers: [codex]
reviewed_at: 2026-06-06T23:11:23Z
plans_reviewed: [18-01-PLAN.md, 18-02-PLAN.md, 18-03-PLAN.md, 18-04-PLAN.md, 18-05-PLAN.md, 18-06-PLAN.md, 18-07-PLAN.md]
---

# Cross-AI Plan Review — Phase 18

> Reviewers invoked: **Codex** (codex-cli 0.137.0, default model). Claude was skipped for
> independence (this review ran inside Claude Code). Gemini/OpenCode/Qwen/Cursor/CodeRabbit and
> local servers were not available on this machine.

## Codex Review

**Summary**
The plans broadly achieve Phase 18: they establish preview infrastructure, token substrates, a debug live-jump hook, and three co-sequenced exemplars without taking on the Phase-22 full backfill. The architecture is mostly sound. The main execution risks are the automated Compose hardcoded-string gate, some preview-matrix ambiguity, and a few implementation details around `ThemeTuple`, `start_dest` gating, and sample fixture shape that could silently break builds or previews if not nailed down before implementation.

**Strengths**
- Correct call on build wiring: `compose-ui-tooling-preview` should stay `implementation` if `@Preview` functions live in `main`; `compose-ui-tooling` should stay `debugImplementation`.
- Good scope discipline: the plans repeatedly defer full string/icon migration, screenshot tests, and `@Stable`/`ImmutableList` to Phase 22.
- Co-sequencing is honored: each exemplar lands preview + strings + icons in the same plan.
- `LocalInspectionMode` strategy is correct for both Coil/network images and `AndroidView` placeholders.
- `start_dest` design is mostly sound: safe parse, dev gate, one-shot hoisted nav seed, Splash gate left intact.
- Wave-0 compile-safety rule for RED scaffolds is thoughtful: comment-only references to future symbols avoids bricking the test sourceset.
- Validation tiers are realistic: CI for compile/tests, Studio for preview rendering, flox for live truth.

**Concerns**
- **HIGH: 18-01 Task 3 detekt gate may become the phase’s biggest blocker.** Adding detekt plus a Compose ruleset under Kotlin 2.1.21 / AGP 8.7 is plausible but not guaranteed. The plan says “verified-compatible” but does not reserve a fallback implementation path if the ruleset lacks the exact hardcoded Compose string checks or is not cached/available. The negative test is good, but this can easily eat Wave 1.
- **HIGH: 18-02 Task 1 `PreviewBox` assumes `DinghyTheme(tokensFlow = flowOf(...))` is callable and appropriate.** Research cited the resolver overload, but the plan switches to a `tokensFlow` overload. If that overload is not public/internal-compatible or if `ThemeResolver().bake(tuple)` needs constructor state, this breaks early. Add a tiny compile-only preview/unit target around `PreviewBox` immediately.
- **MEDIUM: 18-02 Task 1 `@DinghyThemePreviews` is conceptually muddy.** `@Preview` annotations cannot inject the `ThemeTuple`, and the later plans correctly rely on explicit wrapper functions. A “theme multipreview annotation” can set device/uiMode/locale, but it cannot actually select Colorful/Simple/HighContrast tokens. Risk: future implementers think the annotation creates the six app themes when it does not.
- **MEDIUM: 18-01 RED scaffolds may not be meaningfully RED.** Comment-only future assertions compile, but they also do not fail until converted. That is acceptable for compile safety, but call them “compile scaffolds,” not RED tests, or require placeholder tests to assert current facts and include a pending marker convention checked by later plans.
- **MEDIUM: 18-04 dev gate read may be asynchronous.** `container.devCyclerEnabled` sounds like a `Flow` or state derived from DataStore. Reading it synchronously in `MainActivity.onCreate` may not be trivial. The plan needs an exact read seam: cached value, blocking first read with timeout, or a startup state already loaded by `AppContainer`.
- **MEDIUM: 18-04 release-inert claim is stronger than the implementation guarantees.** Gating by `devCyclerEnabled` means release is inert only if that flag cannot be enabled in release UI or persisted from a previous debug install. The plan should explicitly verify/install release with a clean data state or ensure release cannot expose/toggle the dev flag.
- **MEDIUM: 18-05/06/07 “all user-facing literals” can drift into Phase-22 backfill.** The exemplar screens may contain many nested components/shared labels. The plans should define the exact file/surface boundary: only literals in the exemplar files and directly edited child components, not shared components used across unrelated screens.
- **LOW: Icon registry shape is sound, but `alternate` semantics need one invariant.** Is `alternate` unique across `DinghyIcons`? If it is the remap handle, uniqueness should be tested in `DinghyIconsTest`.
- **LOW: `DinghyIconView` size uses `sizeSp` but drawable sizing should be `dp`, not `sp`.** If the parameter is typographic size for ligatures, drawables need a deliberate conversion/naming choice. Prefer `sizeDp` for icon box size and let `MaterialSymbol` derive font size if needed, or document the mismatch.
- **LOW: Dependency ordering is conservative but acceptable.** Serializing 05→06→07 avoids shared `strings.xml` / `DinghyIcons.kt` conflicts. It is slower but safer. No missed hard dependency stands out.

**Suggestions**
- **18-01 Task 1/3:** Choose `detekt-baseline` if SC-3’s automated gate is non-negotiable, but add a hard fallback: if no compatible Compose string-literal rule is verified within Wave 1, record pseudolocale-only as a scoped downgrade and do not block the phase.
- **18-01 Task 3:** Add a uniqueness test for `DinghyIcon.alternate` once 18-03 turns the scaffold live.
- **18-02 Task 1:** Replace `@DinghyThemePreviews` wording with something like `@Nexus7LocalePreviews` unless it truly only documents layout configs. Keep the six app theme previews as explicit wrapper functions.
- **18-02 Task 1:** Add an early `PreviewBoxSmokeTest` or compile-only sample preview so the `ThemeResolver.bake` / `DinghyTheme(tokensFlow=...)` seam fails immediately if wrong.
- **18-04 Task 2:** Specify the exact `devCyclerEnabled` read mechanism in `MainActivity`. Avoid an unbounded blocking DataStore read on the UI thread.
- **18-04 Task 3:** Verify release-inert with a clean app data state, or explicitly prove the release UI cannot enable `devCyclerEnabled`.
- **18-05/06/07:** Define a per-exemplar tokenization boundary before edits: “these files and child components only.” That prevents accidental Phase-22 backfill creep.
- **18-07 Task 3:** Do not flip `nyquist_compliant: true` until Studio preview review and flox checks are actually recorded, not merely planned.

**Risk Assessment**
Overall risk: **MEDIUM**. The phase design is coherent and most Android/Compose choices are correct. The highest risk is not the preview architecture; it is execution friction from the detekt gate and a few underspecified seams (`PreviewBox` token seeding, synchronous dev-gate read, and exact exemplar tokenization boundaries). Tightening those before implementation should keep this phase controlled.

---

## Consensus Summary

Single external reviewer (Codex). Overall verdict: **MEDIUM risk — architecturally sound, with
execution-friction seams to tighten before `/gsd-execute-phase`.** Codex independently confirmed the
two calls the plan-checker also blessed (build wiring stays as-is; co-sequencing honored; scope
discipline holds), and surfaced new seam-level risks the in-house checker did not.

### Agreed Strengths (Codex + in-house plan-checker)
- Build wiring is correct: `compose-ui-tooling-preview` stays `implementation`, `compose-ui-tooling`
  stays `debugImplementation` (SC-4).
- Scope discipline holds — full string/icon backfill, `@Stable`/`ImmutableList`, and screenshot
  regression are deferred to Phase 22; no plan drifts into it at the file level.
- Co-sequencing honored: each exemplar lands `@Preview` + strings + icons in the same plan.
- `LocalInspectionMode` strategy correct for both Coil network images and `AndroidView` placeholders.
- `start_dest`: safe enum parse, dev gate, one-shot hoisted nav seed, Splash/Klippy gate left intact.
- Validation tiers realistic (CI / Studio / flox).

### Agreed / High-Priority Concerns
- **HIGH — detekt gate (18-01 T1/T3) is the phase's biggest execution risk.** detekt + a Compose
  hardcoded-string ruleset under Kotlin 2.1.21 / AGP 8.7 is plausible but unproven; the plan claims
  "verified-compatible" without reserving a fallback path if the exact rule is missing/incompatible.
  Could eat all of Wave 1. → **Fix:** add a hard fallback — if no compatible rule is verified within
  Wave 1, record pseudolocale-only as a scoped SC-3 downgrade and do NOT block the phase.
- **HIGH — 18-02 T1 `PreviewBox` assumes a `DinghyTheme(tokensFlow = flowOf(ThemeResolver().bake(tuple)))`
  overload.** RESEARCH cited the `resolver=` overload; the plan switched to a `tokensFlow` overload.
  If that overload isn't public/compatible or `ThemeResolver()` needs constructor state, the whole
  preview substrate breaks at the root. → **Fix:** add an immediate compile-only `PreviewBox` smoke
  preview/test so the bake/theme seam fails loudly in Wave 2, not in every downstream exemplar.

### Other Concerns Worth Folding In
- **MEDIUM — `@DinghyThemePreviews` multipreview is conceptually muddy:** a `@Preview` multipreview
  annotation can set device/uiMode/locale but CANNOT select Colorful/Simple/HighContrast tokens —
  those need explicit wrapper functions (which later plans correctly use). Rename to something like
  `@Nexus7LocalePreviews` so implementers of Phases 19-21 don't think the annotation mints the six
  themes. (Directly relevant since this IS the template they copy.)
- **MEDIUM — 18-04 dev-gate read may be async:** `container.devCyclerEnabled` is likely Flow/DataStore-
  derived; reading it synchronously in `MainActivity.onCreate` is non-trivial. Specify the exact read
  seam (cached value / already-loaded startup state) — avoid an unbounded blocking DataStore read on
  the UI thread.
- **MEDIUM — 18-04 "release-inert" is stronger than the gate guarantees:** inert only if the dev flag
  can't be enabled/persisted in release. Verify release with clean app-data state, or prove release
  UI can't toggle `devCyclerEnabled`.
- **MEDIUM — exemplar tokenization boundary (18-05/06/07) can creep into Phase-22 backfill:** define
  the exact surface up front — "literals in the exemplar files + directly-edited child components
  only, NOT shared components used across unrelated screens."
- **MEDIUM — RED scaffolds aren't meaningfully RED:** comment-only future assertions compile but never
  fail until converted. Call them "compile scaffolds," or have them assert current facts with a
  pending-marker convention.
- **LOW — icon registry:** test that `DinghyIcon.alternate` is unique across `DinghyIcons` (it's the
  remap handle). `DinghyIconView` size: drawables should size in `dp`, not `sp` — prefer `sizeDp`.
- **LOW — 18-07 T3:** don't flip `nyquist_compliant: true` until Studio + flox checks are actually
  recorded, not merely planned.

### Divergent Views
None — single reviewer. Note Codex's two HIGH items are *seam-precision* gaps the in-house plan-checker
(which verified structure/coverage) did not probe; they're complementary, not contradictory.
