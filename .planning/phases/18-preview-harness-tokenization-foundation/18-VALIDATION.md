---
phase: 18
slug: preview-harness-tokenization-foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
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
| 18-XX-XX | XX | N | SC-{1..5} | unit / compile / eyeball | `{command}` | CI / Studio / flox | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

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

- [ ] Confirm test framework deps present in `app/build.gradle.kts` (JUnit4 already used by existing tests).
- [ ] Decide + (if adopted) install detekt + Compose ruleset with a **baseline** capturing the ~240
      deferred literals (so only NEW literals fail the gate) — RESEARCH A5/A6, Codex-review per
      [[codex-review-final-plans]].
- [ ] Wire `en-XA` pseudolocale generation (`resConfigs`/`pseudoLocalesEnabled` on the debug build type).

*If detekt is deferred to pseudolocale-only, record that decision and downgrade SC-3's lint sub-criterion accordingly.*

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

- [ ] Every task has a CI-assertable verify OR an explicitly-tiered Studio/flox check with instructions
- [ ] Sampling continuity: no 3 consecutive tasks without an automated (CI) verify where one is possible
- [ ] Wave 0 covers the lint-gate + pseudolocale + framework decisions
- [ ] No watch-mode flags in test commands
- [ ] Feedback latency budget recorded (~300s)
- [ ] `nyquist_compliant: true` set in frontmatter once planner fills the per-task map

**Approval:** pending
