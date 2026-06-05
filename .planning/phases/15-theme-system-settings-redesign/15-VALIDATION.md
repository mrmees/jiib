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

> **Whole-sourceset compile gate (CONFIRMED, load-bearing):** Gradle compiles the ENTIRE main sourceset AND the ENTIRE test sourceset before any `--tests` filter runs. So a removed symbol with an un-migrated consumer = a hard compile failure that bricks EVERY plan's `--tests` gate, not just the owning test. This phase retires the old `TokenDelta`/`setBase`/`setDeltas`/`resolve()` API + `Profile.themeBase`/`themeDeltaArgb` in TWO STEPS: **deprecate-and-bridge in 15-04/15-05** (shims keep GalleryScreen + the theme/prompt/config tests compiling), **hard-delete + obsolete-test cleanup in 15-06**. Every wave boundary MUST leave both sourcesets compiling.

---

## Sampling Rate

- **After every task commit:** Run the golden + math unit tests (`PaletteGoldenTest`, `PaletteMathTest`) — sub-second, host-side.
- **After every plan wave:** Run the full `:app:testDebugUnitTest` suite (also proves both sourcesets still compile across the deprecate→delete retirement).
- **Before `/gsd-verify-work`:** Full unit suite green **AND** on-device eyeball UAT on flox (reseed, traces stay separated, pure-neutral surfaces, Simple/High-Contrast modes, Settings editor flow from BOTH entry paths). Hands-on UAT is mandatory — this project has a documented string of "green-suite mock-vs-reality" misses.
- **Max feedback latency:** < 60 seconds for the quick golden/math gate.

---

## Per-Task Verification Map

> The behavior→test map below (from research + the Codex cross-AI review) is the
> authoritative coverage contract the plans must satisfy.

| Behavior | Test Type | Automated Command | File | Owner plan |
|----------|-----------|-------------------|------|------------|
| Kotlin `Palette.generate` == `color.js` (default seed, dark) | unit (golden) | `…PaletteGoldenTest` | ❌ Wave 0 | 15-01/15-02 |
| Golden parity across light, Simple, High-Contrast modes | unit (golden) | `…PaletteGoldenTest` | ❌ Wave 0 | 15-02 |
| Golden parity for edge-hue seeds + poolShift | unit (golden) | `…PaletteGoldenTest` | ❌ Wave 0 | 15-02 |
| `hexToRgb` `/255.0` normalization (no Int-division channel collapse) | unit (golden) | `…PaletteGoldenTest` | ❌ Wave 0 | 15-02 |
| OKLCH↔sRGB round-trip + gamut clamp stays in [0,1] | unit (property) | `…PaletteMathTest` | ❌ Wave 0 | 15-02 |
| `tokensFromPalette` derivation (tiers, alpha) matches `dinghy.js` (via Palette internal helpers) | unit | `…TokenBridgeTest` | ❌ Wave 0 | 15-03 |
| `ThemeResolver` generate-and-cache emits expected `ThemeTokens` (new API) | unit | `…ThemeResolverTest` (extend) | exists — extend | 15-04; cleaned 15-06 |
| Theme-tuple sanitize fail-safe (junk → defaults, never throws) | unit | `…ProfileThemeSeedTest` (extend) | exists — extend | 15-05 |
| `poolOverrides` PER-ENTRY tolerance (one bad entry dropped, good survive; String-keyed map) | unit | `…ProfileThemeSeedTest` / `…ThemePrefsFallbackTest` | extend/rework | 15-05 |
| `poolOverrides` applied at correct index; Reset clears | unit | `…ThemeResolverTest` | extend | 15-04 |
| Fresh-start decode (old blob keys ignored, new fields default) | unit | `…TokenDeltaSerializationTest` (REWRITE — old TokenDelta contract retired) | rewrite | 15-05 |
| `ThemePrefs.sanitize` new tuple signature, no `r.deltas`/`TokenDelta.Role` | unit | `…ThemePrefsFallbackTest` (REWORK) | rework | 15-05; verify 15-06 |
| `promptStyleColor` mapping on a default-seed ThemeTokens (not `resolve(…,TokenDelta.EMPTY,…)`) | unit | `…PromptStyleColorsTest` (REWRITE) | rewrite | 15-06 |
| `fs` flows through the new resolver (not via `resolve(…,TokenDelta.EMPTY,fs)`) | unit | `…FontScaleTest` (REWRITE) | rewrite | 15-06 |
| `Profile` equality/round-trip w/o `themeBase` param | unit | `…ProfileStoreTest` / `…ActiveConfigDerivationTest` (drop themeBase arg) | edit | 15-06 |
| Zero references to retired symbols remain (`TokenDelta`/`setBase`/`setDeltas`/`themeBase`) | grep gate | `grep -rc … app/src/ == 0` | acceptance | 15-06 |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `PaletteGoldenTest.kt` — golden vectors from `color.js` (committed JSON fixture or inline verified hexes, e.g. default-seed accent `#3c75fb`, `bg #0b0b0b`).
- [ ] `tools/color-golden/dump.js` — committed Node script that emits the fixture from the sibling `color.js` (regenerable oracle; pin the hexes in-repo so CI does not depend on the sibling).
- [ ] `PaletteMathTest.kt` — OKLCH↔sRGB round-trip / gamut property checks.
- [ ] `TokenBridgeTest.kt` — `tokensFromPalette` derivation parity.
- [ ] Extend `ThemeResolverTest.kt`, `ProfileThemeSeedTest.kt` for the tuple + overrides + per-entry fail-safe.

---

## Obsolete / reworked tests (CONFIRMED Codex consumer inventory)

These existing tests reference the retired API and MUST be reworked/deleted to the new model (they keep compiling on the 15-04/15-05 `@Deprecated` shims, then are cleaned at the noted wave):

| File | Issue | Action | Wave |
|------|-------|--------|------|
| `theme/TokenDeltaSerializationTest.kt` | entire purpose = round-tripping the retired `TokenDelta` | REWRITE to fresh-start decode + tuple round-trip (or delete + fold into ProfileThemeSeedTest) | 15-05 |
| `theme/ThemePrefsFallbackTest.kt` | calls old `sanitize(base,fs,roleKeys,argb)` + asserts `r.deltas`/`TokenDelta.Role` | REWORK to the new tuple sanitize + per-entry override tolerance | 15-05 (verify 15-06) |
| `theme/FontScaleTest.kt` | `resolve(…, TokenDelta.EMPTY, fs)` | REWRITE to the new-API fs path | 15-06 |
| `prompt/PromptStyleColorsTest.kt` | `resolve(ThemeBase.Dark, TokenDelta.EMPTY, 1f)` | REWRITE to build a default-seed ThemeTokens via the new resolver/bridge | 15-06 |
| `theme/ThemeResolverTest.kt` | may retain shim-backed setBase/setDeltas/resolve tests from 15-04 | DELETE residual shim tests; keep new-API tests | 15-06 |
| `config/ProfileStoreTest.kt` | `themeBase` test factory + equality (L70-71/153-154) | DROP the `themeBase` param/arg | 15-06 |
| `di/ActiveConfigDerivationTest.kt` | `Profile(…, themeBase="Dark")` (L40-41) | DROP the `themeBase` arg | 15-06 |
| `gallery/GalleryScreen.kt` (MAIN, not a test) | `TokenDelta`/`setBase`/`setDeltas`/`SAMPLE_CUSTOM_DELTA` | RE-POINT to the new seed API (`setDark`/`setSeed`/`setFs`) | 15-06 |

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Pure-neutral surfaces render (D-16) | THEME/UI | Visual color judgment | On flox, dark + light: backgrounds are neutral, no cool tint |
| Traces stay visually separated (pool wiring D-13) | THEME/UI | Visual distinctness on real GPU | Temp graph on flox: nozzle/bed/chamber traces are distinct, stable across reseed |
| Palette modes differ visibly (Colorful/Simple/High-Contrast) | THEME | Perceptual | Switch modes in Settings; confirm visible difference |
| Settings editor flow from BOTH entry paths | SET | Touch interaction + routing on device | Drive the theme-editor sub-page on flox via the in-shell route AND the first-run/escape (RootController) path; regen-on-settle, not per-pixel; cached ring (no jank) |
| No theme-switch flicker / connection churn on reseed | THEME | Timing/visual on device | Reseed while connected; spine stays up, no black-screen flash |
| Caution `.heat` surfaces intact after pool migration | THEME/UI | Visual regression check | Console WARNING / Stop confirm guard / Warn buttons STILL show caution color (not recolored to a pool color) |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (Palette golden/math, TokenBridge, oracle dump)
- [ ] Every wave boundary leaves BOTH main + test sourcesets compiling (two-step retirement)
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
