---
phase: 18-preview-harness-tokenization-foundation
reviewed: 2026-06-06T00:00:00Z
depth: standard
files_reviewed: 31
files_reviewed_list:
  - app/build.gradle.kts
  - app/src/main/java/works/mees/dinghy/MainActivity.kt
  - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcon.kt
  - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
  - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIconView.kt
  - app/src/main/java/works/mees/dinghy/preview/DinghyPreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/FineTunePreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/PreviewPlaceholders.kt
  - app/src/main/java/works/mees/dinghy/preview/PreviewTheming.kt
  - app/src/main/java/works/mees/dinghy/preview/PrintStatusPreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt
  - app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt
  - app/src/main/java/works/mees/dinghy/render/BedMeshHeatmapHost.kt
  - app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt
  - app/src/main/java/works/mees/dinghy/render/WebcamViewHost.kt
  - app/src/main/java/works/mees/dinghy/ui/finetune/ExtrusionScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHubScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneShared.kt
  - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneTile.kt
  - app/src/main/java/works/mees/dinghy/ui/finetune/FwRetractionScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/finetune/MotionScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/RootController.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/StartDestMapping.kt
  - app/src/main/java/works/mees/dinghy/ui/spool/scan/ScanSurface.kt
  - app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt
  - app/src/main/res/values/strings.xml
  - app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt
  - app/src/test/java/works/mees/dinghy/preview/PreviewBoxSmokeTest.kt
  - app/src/test/java/works/mees/dinghy/preview/SampleFixturesTest.kt
  - app/src/test/java/works/mees/dinghy/shell/StartDestMappingTest.kt
  - app/src/androidTest/java/works/mees/dinghy/ui/finetune/FineTuneNavTest.kt
findings:
  critical: 0
  warning: 4
  info: 6
  total: 10
  resolved: [WR-01, WR-02, WR-04]
  deferred: [WR-03]
status: partially_resolved
---

# Phase 18: Code Review Report

**Reviewed:** 2026-06-06
**Depth:** standard
**Files Reviewed:** 31
**Status:** issues_found

## Summary

Phase 18 establishes the preview/tokenization foundation: a `preview/` package (PreviewBox theme seam, SampleFixtures, render-host inspection-mode placeholders), the `DinghyIcon`/`DinghyIcons`/`DinghyIconView` registry, a `strings.xml` substrate, a dev-gated `start_dest` deep-jump hook, and three tokenization exemplars (PrintStatus, FineTune, Spool).

The security-critical pieces hold up well. The `start_dest` deep-jump is correctly defended in depth: `AppContainer.devCyclerEnabled` defaults to `false` (ThemePrefs `devEnableFlow` maps a missing key to `false`), so the path is release-inert; and `parseStartDest` is total (null/blank/garbage → `null`, never throws), verified by `StartDestMappingTest` including injection-style inputs. The `dpToSp` conversion is correct under the `fontScale = 1f` pin (callers pass `fsSp(...).dp`, and both arms round-trip the numeric value identically). The render-host `LocalInspectionMode` branches (Graph/BedMesh/Webcam/CameraX) are all present and correct, and the CameraX `DisposableEffect` dispose-race handling in `ScanSurface` is sound.

No BLOCKERS. The findings are: a main-thread DataStore read on the cold-start path (bounded but real), a duplicate icon glyph that defeats the stated "icon-never-twice" convention and is invisible to the uniqueness test, a semantic icon mismatch carried into the FW-retraction screen during the icon migration, and a stale-import/KDoc-link nit. The Info items are mostly residual hardcoded strings in surfaces that are explicitly out of the exemplar tokenization boundary but were nonetheless touched this phase.

## Warnings

### WR-01: Main-thread DataStore read on every cold start (`devCyclerEnabledBlocking`) — RESOLVED (commit 5821408)

**Resolution:** Gated the whole gate read + `start_dest` extra read behind `BuildConfig.DEBUG` (`if (BuildConfig.DEBUG && container.devCyclerEnabledBlocking())`). Release cold start now pays zero main-thread DataStore I/O here; debug behavior unchanged. Verified `:app:assembleDebug` green.

**File:** `app/src/main/java/works/mees/dinghy/MainActivity.kt:55-56`, `98-99`
**Issue:** `onCreate` calls `container.devCyclerEnabledBlocking()` UNCONDITIONALLY (it must read the gate to decide whether to read the extra), which does `runBlocking { withTimeoutOrNull(500) { devCyclerEnabled.first() } }` on the main thread. `devCyclerEnabled` is `ThemePrefs.devEnableFlow`, backed by `dataStore.data` — the first emission performs disk I/O. On the Nexus-7-class slow-flash floor this can stall the UI thread for up to the full 500ms timeout at every launch, on the hottest startup path, purely to support a dev-only feature that is `false` in release. The timeout caps the worst case (good — it can't ANR), but a bounded main-thread stall on cold start is still a startup-jank regression on the exact device class the project optimizes for, and it runs even in release where the answer is always `false`.
**Fix:** Gate the blocking read behind `BuildConfig.DEBUG` so release never pays it, or hoist the gate read off the main thread (e.g. resolve `startDest` inside a `LaunchedEffect`/`produceState` in `setContent` and seed `ShellNavState` once it resolves), or read the value from an already-warm in-memory copy if the theme layer has cached `devEnableFlow`'s first emission by the time the Activity starts. Example minimal guard:
```kotlin
val startDest: Dest? =
    if (BuildConfig.DEBUG && container.devCyclerEnabledBlocking()) {
        parseStartDest(intent?.getStringExtra(EXTRA_START_DEST))
    } else null
```
(Note: the KDoc at line 49 explicitly argues the gate is "NOT BuildConfig.DEBUG"; that is fine for *honoring* the extra, but the blocking *read itself* can still be skipped in release where the DataStore flag cannot be true anyway.)

### WR-02: `LauncherSpool` and `Progress` share the `donut_large` glyph — silent "icon-never-twice" violation — RESOLVED (commit 814885a)

**Resolution:** (a) `LauncherSpool` now uses a distinct `database` ligature (`Progress` keeps `donut_large`). (b) Added `DinghyIconsTest.iconRef_isUnique_acrossAllEntries`, asserting on `it.primary` (the rendered `IconRef`), not just `alternate` — non-vacuous (would have failed on the pre-fix dup: refs 45 != distinct 44). Test ran and passed in `:app:testDebugUnitTest`. (Note for WR-03: any *intentional* future shared-drawable token, e.g. RetractSpeed/UnretractSpeed both on `R.drawable.sprint`, must be explicitly allow-listed in that test rather than weakening it.)

**File:** `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt:29` and `:45`
**Issue:** `Progress = DinghyIcon(IconRef.Ligature("donut_large"), …)` and `LauncherSpool = DinghyIcon(IconRef.Ligature("donut_large"), …)` use the SAME Material Symbols ligature. The registry KDoc and the launcher-block comment both assert the "icon-never-twice" / "icon-no-repeat" rule, and both icons render on the SAME PrintStatus screen: `Progress` is the Spoolman-print-line glyph (`SpoolmanPrintLine`, PrintStatusScreen.kt:1446) and `LauncherSpool` is the Spool launcher/shortcut tile (PrintStatusScreen.kt:1160). So two distinct affordances draw an identical donut glyph in the same view — exactly the duplication the convention forbids. `DinghyIconsTest` only enforces uniqueness of `alternate`, NOT of the underlying `IconRef`, so this passes green and the regression is invisible to the suite.
**Fix:** Give `LauncherSpool` a distinct spool glyph (the rest of the Spool feature uses `inventory_2` via `DinghyIcons.Inventory`, or use a dedicated spool/filament symbol). Separately, extend `DinghyIconsTest` to assert `IconRef` uniqueness across `all` (or at least across ligature names) so future duplicates fail loudly:
```kotlin
@Test fun iconRef_isUnique_acrossAllEntries() {
    val refs = DinghyIcons.all.map { it.primary }
    assertEquals(refs.size, refs.toSet().size)
}
```

### WR-03: FW-retraction speed tiles relabeled to the `MaxAccel` icon token during migration

**File:** `app/src/main/java/works/mees/dinghy/ui/finetune/FwRetractionScreen.kt:154`, `173`
**Issue:** The icon-tokenization migration replaced `iconRes = R.drawable.sprint` on the Retract-speed and Unretract-speed tiles with `icon = DinghyIcons.MaxAccel`. `MaxAccel` is *defined* as `R.drawable.sprint` (DinghyIcons.kt:67), so the rendered glyph is unchanged — but the registry has no semantic token for "a sprint glyph used as retraction speed," so these tiles now reference a token whose `alternate` is `"max_accel"`. That is a semantic mislabel: a future fork using `DinghyIcon.alternate` as the D-07 one-place remap handle would remap the retraction-speed icons when it edits "max_accel," coupling two unrelated controls. Because FwRetractionScreen is build-blind (no on-device UAT) the mislabel will never be caught visually.
**Fix:** Add dedicated `RetractSpeed`/`UnretractSpeed` tokens (both can wrap `R.drawable.sprint` today) so the remap handle matches the control's meaning, e.g.:
```kotlin
val RetractSpeed = DinghyIcon(IconRef.Drawable(R.drawable.sprint), alternate = "retract_speed")
val UnretractSpeed = DinghyIcon(IconRef.Drawable(R.drawable.sprint), alternate = "unretract_speed")
```
(They will collide with WR-02's proposed `IconRef`-uniqueness test, which is correct to call out — a shared *drawable* across distinct *semantic* tokens is the one legitimate exception and should be allow-listed, not the accidental ligature dup in WR-02.)

### WR-04: Stale/dangling references left after edits (unused import + unresolvable KDoc link) — RESOLVED (commit 4847a0e)

**Resolution:** (1) Alphabetized the `works.mees.dinghy.*` import block in `ScanSurface.kt` (moved `preview.PreviewPlaceholderBox` into order after the `designsystem.*` imports). (2) Fully-qualified the `[works.mees.dinghy.preview.PreviewPlaceholderBox]` KDoc link in `SpoolPreviews.kt`. Verified `:app:assembleDebug` green.

**File:** `app/src/main/java/works/mees/dinghy/ui/spool/scan/ScanSurface.kt:37`; `app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt:47`
**Issue:** Two loose ends from this phase's edits:
1. `ScanSurface.kt:37` adds `import works.mees.dinghy.preview.PreviewPlaceholderBox` — used (line 210), so fine — but the same file still imports `androidx.compose.ui.unit.sp` (line 31) and `MaterialSymbol` etc.; the genuinely suspicious one is that the new `import` is placed mid-block out of alphabetical order (between `MaterialSymbol` and `designsystem.control.Intent`), a lint/ordering smell that suggests a hand-merge.
2. `SpoolPreviews.kt:47` KDoc references `[PreviewPlaceholderBox]` but the file does NOT import it. KDoc `[...]` links to an unimported, unqualified symbol resolve to nothing (silent dead link in generated docs); not a compile error, but the link the doc promises does not navigate.
**Fix:** Re-run the import organizer on `ScanSurface.kt` (alphabetize the `works.mees.dinghy.*` block). In `SpoolPreviews.kt`, either fully-qualify the KDoc link (`[works.mees.dinghy.preview.PreviewPlaceholderBox]`) or drop the brackets. Low-risk, but these are the kind of residue that compounds across the 19-21 copy-paste of this exemplar template.

## Info

### IN-01: `PreviewPlaceholderBox` label "Thumbnail" is a hardcoded literal in a production composable

**File:** `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt:653`, `1313`
**Issue:** The Coil `LocalInspectionMode` preview branches pass `label = "Thumbnail"` as a raw string, whereas the parallel CameraX branch in `ScanSurface` uses `stringResource(R.string.cd_spool_camera)`. The label only renders under `@Preview`, never on-device, so it is harmless for users — but it is inconsistent with the tokenization discipline this very phase establishes, and it will show through the en-XA pseudolocale sweep as plain English.
**Fix:** Add a `preview_thumbnail_placeholder` string (or reuse a generic `cd_*` key) and route both call sites through `stringResource`.

### IN-02: Residual hardcoded strings in `ScanSurface` degrade/hint copy (touched this phase)

**File:** `app/src/main/java/works/mees/dinghy/ui/spool/scan/ScanSurface.kt:124-163`, `176`, `339`
**Issue:** `ScanSurface` still hardcodes user-facing copy ("Camera permission denied", "Grant camera access…", "Use picker instead", the `ScanHint` strings, "Back"/"Front cam"/"Rear cam"). This file was edited this phase (the D-05 branch + the `cd_spool_camera` resource) so it is in-scope for review, though the staging note explicitly defers the full Spool-scan vocabulary to the Phase-22 backfill. Flagging for traceability, not as a phase defect.
**Fix:** None required this phase; ensure these land in the Phase-22 backfill inventory.

### IN-03: Residual hardcoded strings in `FwRetractionScreen` ("Retract Len", "Back", …)

**File:** `app/src/main/java/works/mees/dinghy/ui/finetune/FwRetractionScreen.kt:117`, `136`, `155`, `174`, `197`
**Issue:** The build-blind FW-retraction screen keeps hardcoded tile `name`s and a hardcoded "Back" label even though its sibling group screens (Motion/Extrusion) were tokenized to `stringResource(R.string.cd_finetune_*)` / `R.string.common_back`. It was touched this phase (icon migration) but deliberately excluded from the tokenization exemplar set (no preview, no UAT). Documented limitation, not a blocker.
**Fix:** Roll into the Phase-22 backfill; add `cd_finetune_retract_length` etc. keys when FW-retraction gets on-device verification.

### IN-04: `seriesColor`/glyph-size literals and `"—"` placeholders remain as magic values

**File:** `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt:9` (`DASH`-equivalent `"—"` scattered), `FineTuneShared.kt:9`
**Issue:** FineTune centralizes the em-dash as `internal const val DASH = "—"`, but PrintStatusScreen and SpoolScreen open-code `"—"` in ~10 places (e.g. PrintStatusScreen.kt:748, 753, 1354, 1361; tempActive/tempInactive). The strings.xml `spool_value_unset` token also exists ("—"). Three representations of the same placeholder. Per the strings.xml scope note these are DATA placeholders (legitimately untokenized), so this is consistency-only.
**Fix:** Optional: standardize on one `DASH` constant (or the `spool_value_unset` resource) app-wide in the Phase-22 pass.

### IN-05: `GraphViewHost` exposes `tempSeries` fixture but no host overload consumes a single FloatArray in previews

**File:** `app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt:164`
**Issue:** `SampleFixtures.tempSeries` is built and asserted non-empty by `SampleFixturesTest`, but no Phase-18 preview actually renders a `GraphViewHost` with it (the hosts short-circuit to a placeholder under inspection mode, and PrintStatus previews show the "GraphView (live on device)" stand-in). The fixture is dead weight for this phase's previews (it is presumably seed for the Phase-20/21 backfill). Not a defect — just unused-by-current-consumers data carried forward intentionally per the SC-2 "reuse in backfill" rationale.
**Fix:** None; confirm it is referenced by the Temperature/Webcam phases so it does not rot.

### IN-06: `NEXUS7` and `NEXUS7_PORTRAIT` spec strings are byte-identical except orientation, with width≥height in BOTH

**File:** `app/src/main/java/works/mees/dinghy/preview/DinghyPreviews.kt:17`, `20`
**Issue:** `NEXUS7_PORTRAIT` keeps `width=1920px,height=1200px` and only flips `orientation=portrait` (the KDoc claims "1200×1920 … swapped orientation," but the dimensions are NOT swapped — width is still the larger value). Compose `@Preview(device=…)` derives portrait/landscape from the `orientation` token and ignores the w/h ordering, so the render is correct; but the comment is misleading and a reader copying this into Phases 19-21 may assume the dimensions were transposed.
**Fix:** Either swap the literals (`width=1200px,height=1920px`) for the portrait spec to match the KDoc, or correct the KDoc to say the orientation flag drives it.

---

_Reviewed: 2026-06-06_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
