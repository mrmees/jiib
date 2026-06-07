---
phase: quick-260606-ttd
plan: 01
subsystem: preview-harness / design-system gallery
tags: [preview, tokenization, i18n, icons, phase-18-foundation]
requires:
  - DinghyIcons.all (designsystem/icons)
  - DinghyIconView render primitive
  - PreviewBox + theme-combo seeds (preview/PreviewTheming.kt)
  - SampleFixtures (preview)
provides:
  - GalleryScreen DinghyIcons registry section (on-device icon proof)
  - "*PseudolocaleSpotCheck @Preview(locale=en-XA) on the 3 Phase-18 exemplars"
  - de-noised preview harness (misleading @DeviceAndLocalePreviews removed)
  - PREVIEW_AND_TOKENS.md pseudolocale convention (§2/§3/§7)
affects:
  - Phases 19-21 (inherit the pseudolocale spot-check convention)
tech-stack:
  added: []
  patterns:
    - per-screen dedicated @Preview(locale="en-XA") i18n-completeness spot-check
    - chunked(N) Row grid for icon galleries inside an existing verticalScroll
key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt
    - app/src/main/java/works/mees/dinghy/preview/PrintStatusPreviews.kt
    - app/src/main/java/works/mees/dinghy/preview/FineTunePreviews.kt
    - app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt
    - app/src/main/java/works/mees/dinghy/preview/DinghyPreviews.kt
    - docs/ui_design/PREVIEW_AND_TOKENS.md
decisions:
  - "@DeviceAndLocalePreviews: REMOVED (preferred path) — defined-but-unused, no-op night-uiMode panel"
metrics:
  duration: ~10 min
  completed: 2026-06-06
  tasks: 3
  files: 6
---

# Quick Task 260606-ttd: Preview Pseudolocale + Icon Gallery Summary

Rounded out the Phase-18 preview/tokenization foundation: added an on-device DinghyIcons
registry gallery section, a per-exemplar `en-XA` pseudolocale i18n-completeness spot-check on
all 3 Phase-18 exemplars, removed the misleading no-op `@DeviceAndLocalePreviews` annotation, and
documented the pseudolocale convention so Phases 19-21 inherit it — replacing a reverted Studio-AI
attempt that broke the font-scale LAW (`8.sp`) and added a misleading Night-uiMode preview panel.

## What was built

**Task 1 — DinghyIcons gallery section (`GalleryScreen.kt`):**
Added a section after "OutlinedControl — five intents", before "SeverityToast". Iterates
`DinghyIcons.all.chunked(4)` into a wrapping `Column` of `Row`s (each cell `weight(1f)`), rendering
every registered icon via `DinghyIconView(tint = tokens.text, sizeDp = 28.dp, contentDescription = null)`
with its `alternate` (D-07 remap handle) as a centered label at `fsSp(13f, tokens.fs).sp`. No
`LazyVerticalGrid` (the screen is already inside a `verticalScroll`). Added imports: `DinghyIconView`,
`DinghyIcons`, `TextAlign`.

**Task 2 — Pseudolocale spot-checks + annotation cleanup (4 preview files):**
- Added one `private *PseudolocaleSpotCheck` `@Composable` to each exemplar
  (`PrintStatusPseudolocaleSpotCheck`, `FineTunePseudolocaleSpotCheck`, `SpoolPseudolocaleSpotCheck`),
  each annotated with a single `@Preview(device = NEXUS7, locale = "en-XA", showBackground = true)` —
  NOT `@Nexus7Previews`, NOT a multipreview. Each mirrors its file's existing `*RtlSpotCheck` fixture
  (Printing / fineTuneAllPresent / firstSelected) wrapped in `PreviewBox(colorfulDark)`, with no
  `CompositionLocalProvider` (locale comes from the annotation alone). Added the
  `androidx.compose.ui.tooling.preview.Preview` import to each of the 3 files.
- **`@DeviceAndLocalePreviews`: REMOVED (preferred path).** The grep confirmed it was
  defined-but-unused (only its own declaration + self-referential KDoc), so the entire annotation
  class + its three `@Preview` lines + KDoc were deleted, along with the now-unused
  `import android.content.res.Configuration`. Replaced with an explanatory NOTE comment in
  `DinghyPreviews.kt` documenting that the pseudolocale check is now a dedicated per-screen `@Preview`
  and that the removed annotation's night-uiMode panel was a verified no-op (theme is `PreviewBox`/
  tuple-driven, not uiMode-driven). `@Nexus7Previews`'s KDoc did not reference the removed annotation,
  so nothing to fix there.

**Task 3 — Documentation (`PREVIEW_AND_TOKENS.md`):**
- §2: retitled "device/orientation ONLY"; dropped the `@DeviceAndLocalePreviews` bullet (annotation
  removed), leaving only `@Nexus7Previews`, with a pointer that the pseudolocale check is now a
  dedicated per-screen `@Preview`.
- §3: added the `*PseudolocaleSpotCheck` row to the minimized-matrix table.
- §7: added an "i18n companion" note documenting the `en-XA` spot-check as the tokenization-completeness
  proof alongside the RTL check, noting it is its own single `@Preview`, not part of `@Nexus7Previews`.

## Deviations from Plan

None — plan executed exactly as written. Task 2 took the explicitly-PREFERRED path (full removal of
`@DeviceAndLocalePreviews`); the FALLBACK (strip only the night-uiMode line) was not needed because the
annotation was genuinely unused and removal compiled cleanly.

## Verification

Authoritative gate `:app:compileDebugKotlin :app:testDebugUnitTest` — **BUILD SUCCESSFUL, EXIT=0**.

Static facts confirmed:
- `grep 'locale = "en-XA"' preview/` → 3 new spot-check `@Preview` sites (PrintStatus/FineTune/Spool);
  other hits are intentional doc/KDoc references.
- `grep "DeviceAndLocalePreviews" app/` → annotation declaration gone; remaining hits are explanatory
  prose noting the removal.
- `grep "DinghyIcons.all" GalleryScreen.kt` → present (`.chunked(4)` loop).
- No raw unscaled `.sp` literal added in `GalleryScreen.kt` — all sizes route through `fsSp(...).sp`
  (the banned `8.sp` is absent).
- `GalleryPreviews.kt` was NOT created (explicitly out of scope).

## Self-Check: PASSED

Modified files all present; all three commits exist:
- 1c1a0c7 feat(quick-260606-ttd): add DinghyIcons registry section to gallery
- 4afbc9c feat(quick-260606-ttd): add en-XA pseudolocale spot-checks; remove no-op @DeviceAndLocalePreviews
- 7259807 docs(quick-260606-ttd): document en-XA pseudolocale spot-check in PREVIEW_AND_TOKENS
