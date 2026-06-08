---
phase: 22
slug: performance-architecture-refactor
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-08
---

# Phase 22 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> This is a perf/architecture refactor: most fixes are validated by **build + existing host tests + @Preview matrix**, with a hard on-device gfxinfo + owner-eyeball gate for the perceptual SCs (SC1/SC3). See `22-RESEARCH.md` § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 (host unit tests) + Compose `@Preview` matrix (visual regression net) |
| **Config file** | None required — existing host test infrastructure |
| **Quick run command** | `cmd /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.** --no-daemon"` |
| **Full suite command** | `cmd /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon"` |
| **Estimated runtime** | ~120–240 seconds (host suite) |

---

## Sampling Rate

- **After every task commit:** Run quick run command (full host unit suite — fast, no device).
- **After every plan wave:** Full suite + `assembleRelease` (confirms R8 doesn't break annotation-based code).
- **Before `/gsd-verify-work`:** Full host suite green AND `assembleRelease` green.
- **Phase gate (SC1/SC3):** gfxinfo before/after on flox in RELEASE mode (`tools/gfxinfo-parser/parse_framestats.py`) + owner approves no frozen frames, responsiveness maintained, visual reads-the-same.
- **Max feedback latency:** ~240 seconds (host suite).

---

## Per-Task Verification Map

> Task IDs are placeholders until the planner finalizes plan/wave numbering; the planner MUST attach the matching validation row to each task. `gfxinfo`/owner rows are the SC1/SC3 perceptual gate, not per-task host tests.

| Fix | Wave | Test Type | Automated Command / Method | Verification |
|-----|------|-----------|----------------------------|--------------|
| gfxinfo baseline capture (D-05/D-06 sweep set) | 0 | Manual measurement | `parse_framestats.py` on flox, release mode | Baseline recorded BEFORE any code change — the before/after anchor for SC1 |
| Read `di/AppContainer.kt` for `@Stable` need | 0 | Code read | manual | Determines whether push-down needs an `@Stable` annotation |
| Add `kotlinx-collections-immutable = 0.3.8` to catalog + module | 0 | Build | `assembleRelease` | Dependency resolves; Kotlin 2.1.21 compatible (NOT 0.5.0-beta01) |
| `@Immutable` on `PrinterState` + nested types | 1 | Build (type-check) | `assembleRelease` | Compile-clean; annotation is structural — call sites still compile |
| `ImmutableList`/`ImmutableMap` field migration | 1 | Build + existing reducer tests | `testReleaseUnitTest` (`PrinterStateStore` tests) | Reducer emits immutable types; existing tests green |
| Fix inline-lambda slots on `PrintStatusScreen` (~490/522) | 1 | Build + `@Preview` | `assembleDebug` | Compiles; preview renders unchanged |
| `SpoolGlyph` cached `Brush` (ColorWheel template) | 1 | Host unit (`SpoolGlyphTest`) | `testReleaseUnitTest --tests works.mees.dinghy.designsystem.icons.SpoolGlyphTest` | Existing render logic green; brush instance reused when render unchanged |
| `spoolSwatches` → `ImmutableList` + `remember` | 1 | Build (type-check at param sites) | `assembleRelease` | Param-type cascade compiles through call sites |
| `GraphViewHost` update-block token guard (D-12) | 2 | Manual gfxinfo + on-device visual | gfxinfo before/after; owner dark→light toggle | Graph redraw rate drops from 4 Hz to data-driven; theme recolor still works |
| `WebcamViewHost` update-block token guard (D-12) | 2 | Manual gfxinfo + on-device visual | gfxinfo before/after; owner dark→light toggle | Webcam overlay still recolors on theme change |
| `WebcamView.measureText` caching | 2 | Code review (low risk) | review | Text changes infrequently; no behavior change |
| `ImageRequest.Builder` `remember` (Coil) | 2 | Code review | review / on-device | No thumbnail flicker on PrintStatus recompositions |
| `PrintStatusScreen` split (Focus/Field/Gutter/Previews) | 2 | `@Preview` matrix + on-device | `assembleDebug` + on-device flox layout check | 6-combo × portrait/landscape matrix renders without regression |
| `AppShell` flow push-down (28 collections) | 3 | Build + existing shell tests + on-device | `testReleaseUnitTest` + on-device nav check | All screens still navigate; recomposition counts drop (Layout Inspector) |
| `GraphView` fill opacity / pre-raster (D-09) | 3 | Manual gfxinfo + owner side-by-side | gfxinfo before/after; `checkpoint:human-verify` | D-10: owner approves any blend/opacity change on flox side-by-side |
| gfxinfo post-fix capture (SC1) | 3 | Manual measurement | `parse_framestats.py` | Zero frozen frames + responsiveness vs baseline |
| Owner on-device eyeball (SC2/SC3/SC5) | 3 | Owner UAT on flox | `checkpoint:human-verify` | Visual unchanged; connect→monitor→control-a-print loop works |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] gfxinfo **baseline** capture on flox (release mode) across the D-06 sweep set — BEFORE any code change. This is the SC1 before/after anchor; it cannot be reconstructed after the fact.
- [ ] Read `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` — determine whether `@Stable` is needed before the push-down.
- [ ] Add `kotlinx-collections-immutable = "0.3.8"` to `gradle/libs.versions.toml` + wire into `app/build.gradle.kts` (required before Wave 1 code compiles; explicitly NOT `0.5.0-beta01` — needs Kotlin 2.3.0).
- [ ] (Optional but recommended) Enable Compose Compiler metrics/reports output for before/after recomposition-count evidence (SC2). See `22-RESEARCH.md` § Wave 0 Gaps for the exact `freeCompilerArgs` block.

*Existing host test infrastructure (JUnit 4.13.2) + the `@Preview` matrix cover the testable surface; no new framework install needed.*

---

## Manual-Only Verifications

| Behavior | SC | Why Manual | Test Instructions |
|----------|----|------------|-------------------|
| No frozen frames / responsiveness improved | SC1 | Perceptual perf gate; debug Compose lies 5–10× about jank (must be release) | Release build on flox; run D-06 sweep set; `adb shell dumpsys gfxinfo <pkg> framestats` before/after → `parse_framestats.py`; compare jank percentile |
| Recomposition hygiene on hot screens | SC2 | Requires Layout-Inspector recomposition counts / Compose metrics | Layout Inspector on the hot screens (or Compose compiler reports) — confirm socket-driven types skip; temps read low in tree |
| Overdraw reduced without breaking aesthetic | SC3 | Visual blend/falloff judgement is owner-gated (D-10) | Owner side-by-side on flox for any GraphView opacity / scrim change; must read-the-same |
| Three Views surfaces + decode leak-free, no GC churn | SC4 | Sustained-use behavior; no LeakCanary dependency desired | Sustained on-device session; watch logcat GC churn / memory; confirm guards short-circuit |
| connect → monitor → control-a-print loop intact | SC5 | End-to-end printer control on real hardware | Owner drives a print on flox; no functional regression |

---

## Validation Sign-Off

- [ ] Every code-changing task has a host-test, build, or `@Preview` verify — OR an explicit Wave 0 dependency / manual-only row
- [ ] Sampling continuity: no 3 consecutive tasks without an automated (build/host/preview) verify
- [ ] Wave 0 covers all MISSING references (gfxinfo baseline, AppContainer read, immutable-collections dep)
- [ ] No watch-mode flags in any command
- [ ] Feedback latency < 240s (host suite)
- [ ] `nyquist_compliant: true` set in frontmatter once the planner attaches per-task rows

**Approval:** pending
