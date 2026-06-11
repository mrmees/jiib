# 22-01 SUMMARY — Wave-0 setup + gfxinfo baseline (hard pre-edit gate)

**Plan:** 22-01 · **Wave:** 0 · **Status:** ✅ Complete · **Date:** 2026-06-08

## What was built

Wave-0 setup that gates the whole phase:

1. **Release-mode gfxinfo baseline on flox (the SC1 before-anchor)** — captured across all 8 D-06
   sweep screens against the RELEASE (R8, debug-signed) APK on the real Nexus 7 2013 (flox, E3 Pro,
   dark/landscape), parsed via `parse_framestats.py`. Owner-approved as a **hard pre-edit gate** —
   no source file was touched until approval. Recorded in `22-GFXINFO-BASELINE.md` with a full
   reproducibility header (commit SHA, build timestamp, variant, device, orientation, theme, `--fs`,
   printer state) and per-window exercise steps.
2. **`kotlinx-collections-immutable` 0.3.8** added to the version catalog + app module (D-02).
3. **`AppContainer` annotated `@Stable`** (A3/SC2) with a recorded public-property stability audit.

## Baseline results (the anchor)

| Screen | p50 | p90 | p95 | frozen >700ms |
|--------|----:|----:|----:|:---:|
| PrintStatus idle | 43.0 | 56.8 | 58.5 | **0** |
| PrintStatus mid-print | 54.2 | 62.4 | 67.6 | **0** |
| App Drawer | 21.4 | 39.1 | 39.4 | **0** |
| Files scroll | 8.9 | 42.0 | 43.0 | **0** |
| Console | 7.8 | 9.3 | 10.3 | **0** |
| Temperature graph | 55.0 | 65.8 | 69.5 | **0** |
| Webcam (live H.264) | SurfaceView → 0 app-HWUI frames (not measurable) | | | **0** |
| Move | 17.4 | 23.8 | 25.1 | **0** |

**Zero frozen frames everywhere** → app already passes the ADR-0001 Addendum-2 hard gate *before*
the refactor. The before/after targets are **PrintStatus mid-print (54 ms) / idle (43 ms) and
Temperature graph (55 ms)** — all far over the 16.6 ms budget due to whole-screen recomposition on
the unstable `PrinterState` 4 Hz stream. Console/Files already excellent (Views paths) → regression-watch only.

## Key files

- **Created:** `.planning/phases/22-performance-architecture-refactor/22-GFXINFO-BASELINE.md`
- **Modified:** `gradle/libs.versions.toml`, `app/build.gradle.kts` (dependency), `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` (`@Stable`)

## Decisions / findings

- **Webcam is structurally unmeasurable by app gfxinfo.** The live feed renders on a dedicated
  `SurfaceView` (H.264 HW decode composited by SurfaceFlinger) — `dumpsys gfxinfo` reports
  "Total frames rendered: 0" during playback. The Phase-22 Compose/collection refactor does not
  touch this path, so there is no before/after delta. (Rotation re-prepare blank is a separate,
  already-tracked deferred item.)
- **`AppContainer` stability audit:** all public properties are `val`s (store/pref instances,
  `Flow`/`StateFlow` exposures). Only `var` is `private @Volatile sessionControlDelegate` (not
  composable-visible). Two synchronous snapshot getters (`currentSpoolmanClient`,
  `currentFileBrowser`) return live `spine.value` snapshots that change without Compose
  notification, but are by-design non-composable accessors with `Flow` equivalents — recorded in the
  class KDoc; they do not undermine the singleton-identity skip guarantee. `@Stable` annotated.
- **Build env note:** the release `assembleRelease` produces an *unsigned* APK; debug-signed via
  `sign-release.bat` for on-device install (matches flox's installed signature → `install -r`
  preserved the DataStore printer connection).
- **Pre-existing CRLF churn** on `app/build.gradle.kts` (working-tree only, zero content diff) was
  normalized to HEAD before editing so the dependency commit stayed clean.

## Verification

- ✅ Owner approved the baseline (hard pre-edit gate) with all 8 sweep screens recorded + full
  reproducibility header. No source edited at capture (`git status` confirmed clean of source).
- ✅ `assembleRelease` resolves `kotlinx-collections-immutable:0.3.8` and builds.
- ✅ `testReleaseUnitTest` green after the `@Stable` annotation (no behavior change).
- ✅ `grep "@Stable" di/AppContainer.kt` → hit on the class.

## Self-Check: PASSED

Commits: `b291c96` (baseline), `461ba18` (dependency), `7fe4e9e` (@Stable).
