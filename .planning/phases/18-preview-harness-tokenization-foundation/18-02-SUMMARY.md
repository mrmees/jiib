---
phase: 18-preview-harness-tokenization-foundation
plan: 02
subsystem: preview-harness + theme-seam
tags: [preview-harness, theme-seam, fixtures, local-inspection-mode, multipreview, nexus7]
requires:
  - "18-01: SampleFixturesTest compile scaffold (converted live here) + pseudolocale/build wiring"
provides:
  - "PreviewBox wrapper — the ONE preview theme boundary (DinghyTheme(tokensFlow=)/ThemeResolver.bake seam, no preview-only token map) (SC-1)"
  - "6 named theme combo seeds {Colorful,Simple,HighContrast}x{dark,light} + an fs=L overflow seed + themeCombos list (SC-1)"
  - "Nexus7Previews + DeviceAndLocalePreviews multipreview annotations (device/uiMode/locale ONLY) (SC-1)"
  - "SampleFixtures — pure reusable fake-state (4/6 PrintStatus modes + forMode builder, 3 FineTune variants, dense spool list, temp series) (SC-2)"
  - "PreviewPlaceholderBox — token-aware labeled stand-in (D-05)"
  - "D-05 LocalInspectionMode branch in GraphViewHost (both overloads) + BedMeshHeatmapHost + WebcamViewHost"
  - "PreviewBoxSmokeTest — proves the bake/theme data seam (distinct non-null tokens for all 6 combos + fs=L)"
affects:
  - app/src/main/java/works/mees/dinghy/preview/PreviewTheming.kt
  - app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt
  - app/src/main/java/works/mees/dinghy/preview/DinghyPreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/PreviewPlaceholders.kt
  - app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt
  - app/src/main/java/works/mees/dinghy/render/BedMeshHeatmapHost.kt
  - app/src/main/java/works/mees/dinghy/render/WebcamViewHost.kt
  - app/src/test/java/works/mees/dinghy/preview/PreviewBoxSmokeTest.kt
  - app/src/test/java/works/mees/dinghy/preview/SampleFixturesTest.kt
tech-stack:
  added: []
  patterns:
    - "PreviewBox: DinghyTheme(tokensFlow = flowOf(ThemeResolver().bake(tuple))) — reuse the ONE token boundary, no preview-only palette"
    - "fs=L via ThemeTuple.fs (NOT @Preview(fontScale=), which is a NO-OP — OS fontScale pinned to 1f at DinghyTheme.kt:48)"
    - "theme combos = explicit named ThemeTuple seeds; @Preview multipreview = device/uiMode/locale ONLY (cannot select palette mode)"
    - "D-05: classic-View AndroidView hosts short-circuit to a token-aware PreviewPlaceholderBox under LocalInspectionMode.current"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/preview/PreviewTheming.kt
    - app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt
    - app/src/main/java/works/mees/dinghy/preview/DinghyPreviews.kt
    - app/src/main/java/works/mees/dinghy/preview/PreviewPlaceholders.kt
    - app/src/test/java/works/mees/dinghy/preview/PreviewBoxSmokeTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt
    - app/src/main/java/works/mees/dinghy/render/BedMeshHeatmapHost.kt
    - app/src/main/java/works/mees/dinghy/render/WebcamViewHost.kt
    - app/src/test/java/works/mees/dinghy/preview/SampleFixturesTest.kt
decisions:
  - "Used the DinghyTheme(tokensFlow=) overload (the production seam) for PreviewBox, NOT the resolver= overload — both compile; the tokensFlow+bake form is the one the running app uses (AppContainer.effectiveTokens), so previews resolve a tuple identically to runtime"
  - "fs=L is injected via ThemeTuple.fs because @Preview(fontScale=) is a verified NO-OP here (DinghyTheme pins OS fontScale to 1f); documented loudly in PreviewBox KDoc"
  - "Theme combos are explicit named seeds, NOT a multipreview annotation — a @Preview annotation cannot select the palette MODE; named the device annotation DeviceAndLocalePreviews (not DinghyThemePreviews) to make that contract un-mistakable for the Phase 19-21 copy-template"
requirements:
  - SC-1
  - SC-2
  - D-04
  - D-05
metrics:
  duration: ~10 min
  completed: 2026-06-06
---

# Phase 18 Plan 02: Preview/Fixture Harness Foundation Summary

Built the reusable `preview/` package — the ONE preview theme boundary (`PreviewBox` over the real `DinghyTheme(tokensFlow=)`/`ThemeResolver.bake` seam, no preview-only palette), the six theme-combo seeds + an fs=L overflow seed, the Nexus-7 device profile + honestly-named device/locale multipreviews, pure `SampleFixtures` fake-state, and the D-05 `LocalInspectionMode` placeholder branch in all three classic-View hosts — with a pure-JVM smoke test that proves the bake/theme data seam compiles and produces distinct tokens BEFORE any downstream exemplar depends on it.

## What Was Built

### Task 1 — PreviewBox theme seam + Nexus-7/locale multipreviews + bake-seam smoke test (SC-1)
- **Seam verification first (Codex HIGH-2):** confirmed in `DinghyTheme.kt:37-51` the public
  `fun DinghyTheme(tokensFlow: Flow<ThemeTokens>, content)` overload exists, `ThemeResolver()` no-arg
  constructs (all ctor params default), and `bake(tuple): ThemeTokens` (`ThemeResolver.kt:162`) is pure.
  Used the **`tokensFlow=` overload** — the production seam (`AppContainer.effectiveTokens` bakes the
  same way) — so a preview resolves a tuple byte-identically to runtime. Recorded in decisions.
- `preview/PreviewTheming.kt`: `@Composable fun PreviewBox(tuple, content)` =
  `DinghyTheme(tokensFlow = flowOf(ThemeResolver().bake(tuple))) { content() }`. Six named combo seeds
  (`colorfulDark/Light`, `simpleDark/Light`, `highContrastDark/Light`) built from
  `ThemePrefs.TUPLE_DEFAULT.copy(...)` so a tuple-shape change fails to compile HERE, plus `fsLargeSeed`
  (Colorful/dark at `FontScale.L`) and a `themeCombos` list. ⚠ fs=L is injected via `ThemeTuple.fs`,
  documented loudly that `@Preview(fontScale=)` is a NO-OP (OS fontScale pinned to 1f, `DinghyTheme.kt:48`).
- `preview/DinghyPreviews.kt`: `NEXUS7`/`NEXUS7_PORTRAIT` spec strings (`dpi=320`, the floor-device
  geometry), `@Nexus7Previews` (portrait + landscape) and `@DeviceAndLocalePreviews` (device default /
  `en-XA` pseudolocale / night uiMode). KDoc on BOTH states the contract loudly: a `@Preview`
  multipreview sets device/uiMode/locale ONLY and CANNOT select the palette mode — the six themes come
  from `PreviewBox` wrappers (Codex MEDIUM-3 naming guard for the Phase 19-21 copy-template).
- `PreviewBoxSmokeTest.kt`: the FIRST acceptance criterion — a pure JVM test (no Compose) that bakes
  each of the 6 combos + fs=L and asserts non-null, not-all-identical, dark≠light, and `fsLargeSeed`
  carries `FontScale.L.multiplier`. Proves the data seam compiles + works so a wrong overload fails in
  Wave 2, not silently in every downstream exemplar.
- Verification: `:app:compileDebugKotlin` + `PreviewBoxSmokeTest` → BUILD SUCCESSFUL (exit 0).
- Commit: `ec5386f`.

### Task 2 — SampleFixtures pure fake-state + PreviewPlaceholderBox + live SampleFixturesTest (SC-2)
- `preview/SampleFixtures.kt` (`object SampleFixtures`, pure/immutable, NO Moonraker/network):
  `printStatusModes` (the 4 states, Terminal expanded to its 3 kinds) + `forMode(mode)` builder (the
  inverse of `classifyPrintStatus` — a `PrinterState` whose `printState` classifies back to the mode);
  three FineTune variants `fineTuneAllPresent` / `fineTuneNoFwRetraction` (HIDDEN path) / `fineTuneBusy`
  (`groupBusy=true`) built off `FineTuneVm` + a `fineTuneVariants` list; a dense 8-entry `spoolList`
  (materials/colors incl. a multi-color spool, vendors, locations); a 60-sample `tempSeries` (heat-up
  ramp → plateau).
- `preview/PreviewPlaceholders.kt`: `@Composable fun PreviewPlaceholderBox(label, modifier)` — a
  `LocalTokens.current`-colored 2dp outline + centered label (the `MaterialSymbol` token idiom), the
  D-05 "labeled stand-in, not blank" the hosts consume.
- Turned the 18-01 `SampleFixturesTest.kt` compile scaffold (its `// TODO(18-02):` markers) into live
  SC-2 assertions: all 4/6 modes covered, `forMode` round-trips through `classifyPrintStatus`, the 3
  FineTune variants are present/absent/busy, spool+temp non-empty, a multi-color spool exists.
- Verification: `:app:testDebugUnitTest --tests *SampleFixturesTest` → BUILD SUCCESSFUL (exit 0).
- Commit: `54cff3c`.

### Task 3 — D-05 LocalInspectionMode branch in the 3 classic-View hosts
- Added `if (LocalInspectionMode.current) { PreviewPlaceholderBox(label, modifier); return }` BEFORE the
  `AndroidView` call in: **GraphViewHost** (BOTH the single-snapshot :27-43 AND multi-trace :64-86
  overloads), **BedMeshHeatmapHost**, **WebcamViewHost**. Distinct labels
  ("GraphView/Bed mesh/Webcam (live on device)"). D-04 (these Views get no dedicated Compose preview —
  known limitation, real rendering is the on-device gate) documented in each host's branch KDoc.
- Verification: `:app:assembleDebug` + full `:app:testDebugUnitTest` → BUILD SUCCESSFUL (exit 0, no regression).
- Commit: `7de1a99`.

## Deviations from Plan

None — plan executed exactly as written. No auto-fixes (Rules 1-3) needed; no architectural decisions (Rule 4). The plan's preferred `tokensFlow=` overload compiled as predicted, so the resolver-overload fallback was not used.

## Known Stubs

None. `PreviewPlaceholderBox` is the INTENDED D-05 deliverable (a labeled stand-in for non-Compose Views under preview), not a stub hiding missing functionality — its "live on device" label is the documented D-04 limitation, and the real Views still render on-device unchanged. `SampleFixtures` is pure intentional fake-state (the harness's reason for existing), reused by exemplars and the Phase-22 backfill.

## Verification Summary

- `:app:compileDebugKotlin` — exit 0.
- `PreviewBoxSmokeTest` (the bake/theme seam guard) — exit 0 (distinct non-null tokens for 6 combos + fs=L).
- `SampleFixturesTest` (live SC-2) — exit 0.
- `:app:assembleDebug` + full `:app:testDebugUnitTest` — exit 0 (no regression after the D-05 host branches).
- Grep criteria: `fun PreviewBox` ×1; ThemeTuple count ≥7 (12); `dpi=320` present; both annotation classes present (not `@DinghyThemePreviews`); SampleFixtures network imports = 0; `PreviewPlaceholderBox` reads `LocalTokens.current`; `LocalInspectionMode` in GraphViewHost ×3 (2 overloads), BedMeshHeatmapHost ×2, WebcamViewHost ×2.
- Studio render (PreviewBox 6 combos + fs=L distinct; placeholders labeled) — deferred to the phase gate (human-eyeball).

## Self-Check: PASSED
- app/src/main/java/works/mees/dinghy/preview/PreviewTheming.kt — FOUND
- app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt — FOUND
- app/src/main/java/works/mees/dinghy/preview/DinghyPreviews.kt — FOUND
- app/src/main/java/works/mees/dinghy/preview/PreviewPlaceholders.kt — FOUND
- app/src/test/java/works/mees/dinghy/preview/PreviewBoxSmokeTest.kt — FOUND
- Commit ec5386f — FOUND
- Commit 54cff3c — FOUND
- Commit 7de1a99 — FOUND
