---
phase: 15
slug: theme-system-settings-redesign
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-05
---

# Phase 15 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `15-RESEARCH.md` § Validation Architecture. This phase ports pure
> deterministic color math with an executable oracle (`../theme_theory/app/color.js`
> in Node), making it a **strong** golden-value validation candidate.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 (host unit tests, `app/src/test/`) |
| **Config file** | `app/build.gradle.kts` (testOptions) |
| **Quick run command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.theme.PaletteGoldenTest --tests works.mees.dinghy.theme.PaletteMathTest --no-daemon"` |
| **Full suite command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| **Estimated runtime** | golden+math sub-second; full host suite ~tens of seconds |

> **AGP gotcha:** list test classes explicitly — the `--tests 'pkg.*'` glob false-fails on this AGP. Builds run Windows-side via `E:\Android\gw.bat`, never `./gradlew` from WSL.

---

## Sampling Rate

- **After every task commit:** Run the golden + math unit tests (`PaletteGoldenTest`, `PaletteMathTest`) — sub-second, host-side.
- **After every plan wave:** Run the full `:app:testDebugUnitTest` suite.
- **Before `/gsd-verify-work`:** Full unit suite green **AND** on-device eyeball UAT on flox (reseed, traces stay separated, pure-neutral surfaces, Simple/High-Contrast modes, Settings editor flow). Hands-on UAT is mandatory — this project has a documented string of "green-suite mock-vs-reality" misses.
- **Max feedback latency:** < 60 seconds for the quick golden/math gate.

---

## Per-Task Verification Map

> Filled per-plan by the planner/executor once PLAN.md files exist. The behavior→test
> map below (from research) is the authoritative coverage contract the plans must satisfy.

| Behavior | Test Type | Automated Command | File |
|----------|-----------|-------------------|------|
| Kotlin `Palette.generate` == `color.js` (default seed, dark) | unit (golden) | `…PaletteGoldenTest` | ❌ Wave 0 |
| Golden parity across light, Simple, High-Contrast modes | unit (golden) | `…PaletteGoldenTest` | ❌ Wave 0 |
| Golden parity for edge-hue seeds (red ~25°, yellow ~85°) + poolShift | unit (golden) | `…PaletteGoldenTest` | ❌ Wave 0 |
| OKLCH↔sRGB round-trip + gamut clamp stays in [0,1] | unit (property) | `…PaletteMathTest` | ❌ Wave 0 |
| `tokensFromPalette` derivation (tiers, alpha) matches `dinghy.js` | unit | `…TokenBridgeTest` | ❌ Wave 0 |
| `ThemeResolver` generate-and-cache emits expected `ThemeTokens` | unit | `…ThemeResolverTest` (extend) | exists — extend |
| Theme-tuple sanitize fail-safe (junk → defaults, never throws) | unit | `…ProfileThemeSeedTest` (extend) | exists — extend |
| `poolOverrides` applied at correct index; Reset clears | unit | `…ThemeResolverTest` | extend |
| Fresh-start decode (old blob keys ignored, new fields default) | unit | `…ProfileStore`/serialization test | extend |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `PaletteGoldenTest.kt` — golden vectors from `color.js` (committed JSON fixture or inline verified hexes, e.g. default-seed accent `#3c75fb`, `bg #0b0b0b`).
- [ ] `tools/color-golden/dump.js` — committed Node script that emits the fixture from the sibling `color.js` (regenerable oracle; pin the hexes in-repo so CI does not depend on the sibling).
- [ ] `PaletteMathTest.kt` — OKLCH↔sRGB round-trip / gamut property checks.
- [ ] `TokenBridgeTest.kt` — `tokensFromPalette` derivation parity.
- [ ] Extend `ThemeResolverTest.kt`, `ProfileThemeSeedTest.kt` for the tuple + overrides + fail-safe.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Pure-neutral surfaces render (D-16) | THEME/UI | Visual color judgment | On flox, dark + light: backgrounds are neutral, no cool tint |
| Traces stay visually separated (pool wiring D-13) | THEME/UI | Visual distinctness on real GPU | Temp graph on flox: nozzle/bed/chamber traces are distinct, stable across reseed |
| Palette modes differ visibly (Colorful/Simple/High-Contrast) | THEME | Perceptual | Switch modes in Settings; confirm visible difference |
| Settings editor flow (wheel + presets + Randomize + per-slot override + Reset) | SET | Touch interaction on device | Drive the theme-editor sub-page on flox; regen-on-settle, not per-pixel |
| No theme-switch flicker / connection churn on reseed | THEME | Timing/visual on device | Reseed while connected; spine stays up, no black-screen flash |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (Palette golden/math, TokenBridge, oracle dump)
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
