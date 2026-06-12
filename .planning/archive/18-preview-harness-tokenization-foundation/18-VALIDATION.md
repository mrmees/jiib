---
phase: 18
slug: preview-harness-tokenization-foundation
status: final
nyquist_compliant: false
wave_0_complete: true
created: 2026-06-06
---

# Phase 18 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
>
> ⚠ **Host-render boundary (from RESEARCH §Validation Architecture):** `@Preview` is rendered
> host-side by Layoutlib — it validates *layout/token correctness*, NOT Adreno-320 performance.
> Nothing in this phase may claim a perf result. flox stays the system-of-record for performance
> and reality (see [[dinghy-display-mock-vs-reality]]).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 (unit) + Robolectric/AndroidX-test where a Context is needed; `compose-ui-tooling` previews (human-eyeball, Android Studio); detekt (lint gate, pending Wave-0 decision) |
| **Config file** | `app/build.gradle.kts` (test deps); `detekt.yml` if detekt adopted |
| **Quick run command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` (from repo root, pipe through `tr -d '\r'`) |
| **Full suite command** | `cmd.exe /c "E:\Android\gw.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon"` (+ `:app:lintDebug` / detekt if wired) |
| **Estimated runtime** | ~2–5 min (Windows-side Gradle; guard hangs per [[dinghy-display-gradle-hang-interop]]) |

**Build reality:** `./gradlew` does NOT run from WSL — drive Windows-side via `E:\Android\gw.bat`
(see CLAUDE.md "Local Build Environment"). Exit code is authoritative.

---

## Sampling Rate

- **After every task commit:** Run the quick command for any task touching Kotlin (`:app:testDebugUnitTest`); for pure-resource/`@Preview` tasks, `:app:assembleDebug` must stay green.
- **After every plan wave:** Run the full suite.
- **Before `/gsd-verify-work`:** Full suite green + the 3 exemplar previews visually confirmed across the 6 theme combos + `fs=L` in Android Studio.
- **Max feedback latency:** ~300 seconds (Gradle build dominated).

---

## Per-Task Verification Map

> Populated by the planner per task. Each task's `<acceptance_criteria>` must map to a CI-assertable
> command, a Studio human-eyeball check, or a flox on-device check — labeled by tier (see below).

| Task ID | Plan | Wave | Requirement (SC) | Test Type | Automated Command / Check | Tier | Status |
|---------|------|------|------------------|-----------|---------------------------|------|--------|
| 18-02-T1 | 02 | 1 | SC-1 | unit | `:app:testDebugUnitTest --tests *PreviewBoxSmokeTest` (6 combos + fs=L bake distinct) | CI | ✅ |
| 18-02-T2 | 02 | 1 | SC-2 | unit | `:app:testDebugUnitTest --tests *SampleFixturesTest` | CI | ✅ |
| 18-02-T3 | 02 | 1 | D-05 | unit/grep | `LocalInspectionMode` in the 3 render hosts; `assembleDebug` green | CI | ✅ |
| 18-03-T2 | 03 | 1 | SC-3 | unit/grep | `:app:testDebugUnitTest --tests *DinghyIconsTest` (resolvable + unique alternate) | CI | ✅ |
| 18-03-T3 | 03 | 1 | SC-3 | compile | `:app:assembleDebug` (strings.xml format-arg + plurals merge) | CI | ✅ |
| 18-04 | 04 | 1 | SC-4 | unit/flox | `start_dest` parse unit test (CI); jump lands on a live screen (flox) | CI / flox | CI ✅ · flox ⬜ pending |
| 18-05-T1 | 05 | 4 | SC-1/D-01 | grep/compile | `PreviewParameterProvider<PrintStatusMode>`; `PreviewBox(`/`SampleFixtures`; `compileDebugKotlin` | CI | ✅ |
| 18-05-T2 | 05 | 4 | SC-3/D-02/D-05 | grep/unit | 16 `stringResource(printstatus_*)`, 13 `DinghyIconView`, both Coil `AsyncImage` on `LocalInspectionMode`; `assembleDebug`+`testDebugUnitTest` | CI | ✅ |
| 18-05-eye | 05 | 4 | SC-1 | eyeball | PrintStatus: 4/6 states × 6 combos + fs=L + RTL render; placeholders labeled | Studio | ⬜ pending |
| 18-06-T1 | 06 | 4 | SC-1/D-01 | grep/compile | `FineTuneVariantProvider`; present/absent/busy fixtures; `compileDebugKotlin` | CI | ✅ |
| 18-06-T2 | 06 | 4 | SC-3/D-02 | grep/unit | `stringResource(finetune_*/cd_finetune_*)`, `DinghyIcon` routing; `assembleDebug`+`testDebugUnitTest` | CI | ✅ |
| 18-06-T3 | 06 | 4 | SC-1 | compile | `:app:compileDebugAndroidTestKotlin` (FineTuneNavTest matchers migrated) | CI | ✅ |
| 18-06-eye | 06 | 4 | SC-1 | eyeball | FineTune: present/absent/busy × 6 combos + fs=L + RTL; absent HIDES FW-retraction; busy locks group | Studio | ⬜ pending |
| 18-07-T1 | 07 | 5 | SC-1/SC-3/D-01/D-02/D-05 | grep/unit | `SpoolPreviews.kt` `PreviewBox(`×9 + `SampleFixtures.spoolList`×5; SpoolScreen 20 `stringResource(spool_*/cd_spool_*/common_back)` + 7 `DinghyIconView`; `LocalInspectionMode` in ScanSurface; no `@Stable/@Immutable/ImmutableList`; `assembleDebug`+full `testDebugUnitTest` | CI | ✅ |
| 18-07-T2 | 07 | 5 | SC-2 | compile/grep | `:app:compileDebugAndroidTestKotlin`; PREVIEW_AND_TOKENS.md content keywords; CLAUDE.md `PREVIEW_AND_TOKENS` pointer | CI | ✅ |
| 18-07-T2b | 07 | 5 | SC-1 | flox | `:app:connectedDebugAndroidTest` Spool tests (string values unchanged → behave identically) | flox | ⬜ pending |
| 18-07-T3 | 07 | 5 | SC-5 | unit | full `:app:testDebugUnitTest` green (no regression) | CI | ✅ |
| 18-07-eye | 07 | 5 | SC-1 | eyeball | Spool: no-selection/selected × 6 combos + fs=L + RTL; dense list renders; camera placeholder labeled | Studio | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

> **nyquist note (Codex LOW-9):** every CI tier row is ✅ (all three exemplars compile, tokenize, and
> pass the full unit suite with no regression). The Studio human-eyeball rows (the 3 exemplars across
> 6 combos + fs=L + RTL) and the flox rows (the 18-04 `start_dest` jump + the Spool `connectedAndroidTest`
> smoke) are **⬜ pending** — they are owner-DEFERRED on-device review, the same posture as the Phase-17
> UAT. `nyquist_compliant` therefore STAYS `false` until those Studio + flox rows are actually recorded ✅
> (not merely planned); flip it in a follow-up once recorded. The CI foundation is complete and additive
> (the 3 host D-05 branches are preview-only; the 3 exemplars preserve layout/tokens — no screen regresses).

### Validation tiers (RESEARCH §Validation Architecture)

- **CI-assertable** — compiles (`assembleDebug`), unit tests pass (token-bake distinctness, icon-registry
  resolution, `start_dest` parsing, string-key lookup), detekt/lint gate fails on NEW hardcoded literals.
- **Studio-only (human eyeball)** — `@Preview` renders correctly across 6 theme combos + `fs=L` + RTL
  spot-check; `LocalInspectionMode` placeholders render (not blank). Cannot be CI-gated without
  `compose-preview-screenshot` (deferred to Phase 22).
- **flox-only (on-device)** — anything claiming performance or real Moonraker/Klippy behavior; the
  `start_dest` jump landing on a live screen. Previews say NOTHING about this tier.

---

## Wave 0 Requirements

- [x] Confirm test framework deps present in `app/build.gradle.kts` (JUnit4 already used by existing tests).
- [x] Decide + (if adopted) install detekt + Compose ruleset with a **baseline** capturing the ~240
      deferred literals (so only NEW literals fail the gate) — RESEARCH A5/A6, Codex-review per
      [[codex-review-final-plans]]. **→ DECISION: detekt-baseline route SELECTED; Task-3 verification
      FAILED → FALLBACK FIRED (see SC-3 downgrade below).**
- [x] Wire `en-XA` pseudolocale generation (`pseudoLocalesEnabled` on the debug build type) — done
      in 18-01 Task 2 (`app/build.gradle.kts` debug build type, commit c30aa57).

*If detekt is deferred to pseudolocale-only, record that decision and downgrade SC-3's lint sub-criterion accordingly.*

### SC-3 lint-gate downgrade — FALLBACK FIRED (18-01 Task 3, 2026-06-06)

The Task-1 owner decision was **detekt-baseline with `detekt-fallback-to-pseudolocale` as the Task-3
escape hatch**. During Task 3 the two candidate Compose rulesets were resolved from Maven Central and
their rule classes inspected:

- `io.nlopez.compose.rules:detekt:0.4.22` (mrmans0n / compose-rules) — ships ONLY Compose API-convention
  rules (`NamingCheck`, `ParameterNamingCheck`, `ModifierMissingCheck`, `ParameterOrderCheck`,
  `UnstableCollectionsCheck`, etc.). **No hardcoded-string / `Text("…")` literal rule.**
- `ru.kode:detekt-rules-compose:1.4.0` (appKODE) — ships `ReusedModifierInstance`,
  `ModifierDefaultValue`, `ComposableParametersOrdering`, `ModifierHeightWithText`, etc.
  **No hardcoded-string / `contentDescription` literal rule either.**

Neither verified ruleset (nor the platform `HardcodedText` lint, which is XML-only — RESEARCH Q7)
exposes a working Compose hardcoded-string check under Kotlin 2.1.21 / AGP 8.7. Per the explicit
fallback branch, **no detekt was wired** (zero residue: no plugin, no `config/detekt/`, no catalog
entry), and SC-3's automated "can't-regress" hardcoded-literal lint gate is **DEFERRED to Phase 22**.

**What SC-3 STILL ships this phase (not downgraded):** the `en-XA` / `ar-XB` pseudolocale generation on
the debug build (`isPseudoLocalesEnabled = true`) — the i18n completeness sweep. The `strings.xml`
convention + semantic icon registry land in plans 18-02/03/04. Only the *automated lint gate*
sub-criterion of SC-3 is the scoped downgrade; the pseudolocale sweep is the manual completeness check
in its place until Phase 22 restores an automated gate.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Exemplar `@Preview` renders correctly across 6 theme combos + `fs=L` | SC-1 | Host-rendered; no golden-image net until Phase 22 | Open PrintStatus/FineTune/Spool previews in Android Studio; verify all 6 combos + `fs=L` render distinctly with no overflow/clipping |
| RTL spot-check on exemplars | SC-1/D-02 | Visual `start`/`end` correctness | Toggle preview locale to an RTL pseudolocale; confirm mirrored layout |
| `start_dest` jumps running app to a screen | SC-4 | Needs running app + live Klippy state | flox: `am start -n works.mees.dinghy/.MainActivity --es start_dest FineTune` with dev-enable on; confirm lands; confirm inert in release |
| No existing screen regresses | SC-5 | Visual + behavioral | Smoke the app on flox; spot non-exemplar screens |

---

## Validation Sign-Off

- [x] Every task has a CI-assertable verify OR an explicitly-tiered Studio/flox check with instructions
- [x] Sampling continuity: no 3 consecutive tasks without an automated (CI) verify where one is possible
- [x] Wave 0 covers the lint-gate + pseudolocale + framework decisions
- [x] No watch-mode flags in test commands
- [x] Feedback latency budget recorded (~300s)
- [ ] `nyquist_compliant: true` — HELD `false` (Codex LOW-9): CI rows all ✅, but the Studio-eyeball +
      flox rows are owner-DEFERRED ⬜ pending; flip only once those are recorded ✅ (not merely planned)

**Approval:** CI tier complete (all exemplars compile/tokenize/no-regression); Studio + flox tiers pending owner on-device review (deferred, Phase-17-UAT posture).
