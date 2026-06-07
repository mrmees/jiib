---
phase: 18-preview-harness-tokenization-foundation
verified: 2026-06-06T00:00:00Z
status: human_needed
score: 5/5 must-haves verified (code tier)
overrides_applied: 0
human_verification:
  - test: "Open PrintStatusPreviews.kt in Android Studio and render all preview functions"
    expected: "PrintStatusStateMatrix shows 4/6 modes across portrait+landscape; six PrintStatusTheme* functions show 6 distinct themed combos (Colorful dark/light, Simple dark/light, HighContrast dark/light); PrintStatusFsLargeOverflow shows larger text without overflow/clipping; PrintStatusRtlSpotCheck shows mirrored RTL layout; GraphView + Coil thumbnail sites show labeled placeholder boxes (not blank regions)"
    why_human: "Host-rendered @Preview correctness (color, layout, overflow) cannot be verified by grep. No golden-image CI net until Phase 22 (compose-preview-screenshot)."
  - test: "Open FineTunePreviews.kt in Android Studio and render all preview functions"
    expected: "ExtrusionVariantMatrix shows 3 variants — all-present (full tile column), FW-retraction absent (FW-retraction entry tile HIDDEN, not disabled), busy (whole-group lock, every tile dimmed); six FineTuneTheme* siblings show 6 distinct themed combos; FineTuneFsLargeOverflow + FineTuneRtlSpotCheck render correctly"
    why_human: "Same as above — host-rendered preview correctness, no CI golden-image net."
  - test: "Open SpoolPreviews.kt in Android Studio and render all preview functions"
    expected: "SpoolSelectionMatrix shows no-selection / selected states; dense 8-entry spool list renders with material/color/vendor/location data; six SpoolTheme* siblings show 6 distinct themes; SpoolFsLargeOverflow + SpoolRtlSpotCheck render correctly; camera scan placeholder is labeled (not blank), not a Coil thumb"
    why_human: "Same as above. Additionally the CameraX surface is genuinely unreachable under @Preview — the labeled stand-in is the only observable result."
  - test: "Install debug APK on flox (Nexus 7 / LineageOS API 30) with dev-enable ON; run: adb shell am start -n works.mees.dinghy/.MainActivity --es start_dest FineTune"
    expected: "App navigates directly to the FineTune Hub screen (after the Splash/Klippy-Ready gate). Repeat with start_dest=Spool and start_dest=PrintStatus — each lands on the named screen. Sending start_dest=NotARealScreen results in the default PrintStatus home with no crash."
    why_human: "The start_dest deep-jump requires a running app + live Klippy state to pass the RootController Splash gate. Cannot be verified by reading code alone."
  - test: "Confirm start_dest is release-inert: from a CLEAN app data state (pm clear works.mees.dinghy), install the debug APK and run the am start command WITHOUT previously enabling dev-cycler via the Settings UI"
    expected: "The start_dest extra is IGNORED (devCyclerEnabled defaults FALSE on clean data); app starts normally on the default PrintStatus screen"
    why_human: "The defense-in-depth 'release-inert from a CLEAN data state' check (as opposed to merely trusting the code default) requires running the app. The code path is correct per review but the behavioral gate needs device confirmation."
  - test: "Smoke the app on flox across non-exemplar screens (Move, Calibration, Console, Files, Temperature, Webcam, Macros, Spool-scan) — no screen should regress from pre-Phase-18 behavior"
    expected: "All non-exemplar screens navigate, render, and respond to input identically to Phase 17 UAT state. No visual regressions from the tokenization work (the 3 exemplar screens had behavior-neutral state-hoists; the D-05 host branches are preview-only and device-invisible)."
    why_human: "SC-5 no-regression claim. The full testDebugUnitTest suite is CI-green, but visual regression of non-exemplar screens requires a device walk."
  - test: "On the debug APK, switch device locale to English (XA) — the pseudolocale"
    expected: "User-facing strings that are tokenized (spool_*/finetune_*/printstatus_* keys) show accented/decorated text, confirming they route through strings.xml. Hardcoded literals (intentionally deferred per SC-3 fallback + Phase-22 backfill) show plain English — these are expected and do not constitute a failure for this phase."
    why_human: "The en-XA pseudolocale sweep is the manual SC-3 completeness check substituting for the deferred automated lint gate (detekt fallback fired in 18-01). Requires running the app with the pseudolocale active."
---

# Phase 18: Preview Harness & Tokenization Foundation — Verification Report

**Phase Goal:** Preview Harness & Tokenization Foundation — Compose @Preview harness + reusable fake-state fixtures (no printer/device), themed-token preview providers, Nexus-7 device profile, the string-resource + semantic-icon tokenization conventions, and a debug start_dest hook — so Phases 19-21 build preview-first & tokenized-first; infra + convention + 2-3 exemplar screens only (exhaustive every-screen backfill → Phase 22).

**Verified:** 2026-06-06
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| #  | Truth | Status | Evidence |
|----|-------|--------|----------|
| 1 | Any in-scope Compose screen renders in Android Studio with realistic fixtures and themed tokens across 6 combos + fs=L, NO live Moonraker — proven on 2-3 exemplar screens | ? HUMAN NEEDED (CI tier VERIFIED) | `PrintStatusPreviews.kt` (129L), `FineTunePreviews.kt` (131L), `SpoolPreviews.kt` (142L) all exist with `PreviewBox(`×9 each, `@PreviewParameter` providers, `@Nexus7Previews`, `SampleFixtures` usage. CI: `assembleDebug` + full `testDebugUnitTest` green. Studio render: owner-deferred. |
| 2 | Reusable fixture module + preview-token provider + Nexus-7 device profile exist with a written convention doc | ✓ VERIFIED | `SampleFixtures.kt` (170L, pure, 0 Moonraker imports), `PreviewTheming.kt` (104L, `fun PreviewBox` ×1, 12 `ThemeTuple` seeds), `DinghyPreviews.kt` (52L, `NEXUS7`/`NEXUS7_PORTRAIT` with `dpi=320`, `@Nexus7Previews` + `@DeviceAndLocalePreviews` annotations). `docs/ui_design/PREVIEW_AND_TOKENS.md` (229L) exists with `PreviewBox`, `@Nexus7Previews`, `fsLargeSeed`, `<area>_<element>`, `DinghyIcon` content. CLAUDE.md has `PREVIEW_AND_TOKENS.md` pointer. `SampleFixturesTest` live and green (all 4/6 modes, 3 FineTune variants, spool + temp non-empty). `PreviewBoxSmokeTest` proves bake/theme seam (6 combos distinct, non-null). |
| 3 | strings.xml + key convention + format-arg/plurals documented; semantic icon-token registry exists; pseudolocale wired; lint gate present or downgrade recorded | ✓ VERIFIED | `strings.xml` (155L) exists with `printstatus_layer_progress` (format-arg `%1$d of %2$d`), `spool_count` (`<plurals>`), `printstatus_*`/`finetune_*`/`spool_*`/`cd_*` keys. `DinghyIcon.kt` (34L): `sealed interface IconRef`, `data class DinghyIcon(primary, alternate)`. `DinghyIcons.kt` (93L): `object DinghyIcons`, 39 entries, `val all: List<DinghyIcon>`. `DinghyIconView.kt` (81L): `fun DinghyIconView`, MaterialSymbol + painterResource delegation. `isPseudoLocalesEnabled = true` in `debug {}` block of `build.gradle.kts`. SC-3 automated lint-gate downgrade recorded in `18-VALIDATION.md` (fallback fired — no Compose hardcoded-string detekt rule available under Kotlin 2.1.21 / AGP 8.7; deferred to Phase 22). |
| 4 | Build wiring correct; debug start_dest hook present and release-inert | ✓ VERIFIED (code) / ? HUMAN NEEDED (device) | `build.gradle.kts` has `implementation(libs.compose.ui.tooling.preview)` with explaining comment + `debugImplementation(libs.compose.ui.tooling)`. `StartDestMapping.kt` (30L): `fun parseStartDest(raw: String?): Dest?` — total, never throws, garbage→null. `MainActivity.kt` (99L): reads extra ONLY when `container.devCyclerEnabledBlocking()` (defaults false). `RootController.kt` (157L): `startDest: Dest? = null` parameter seeded into `rememberShellNavState(startDest)`. `ShellNavState.kt` (135L): `class ShellNavState(startDest: Dest?)`, `var dest by mutableStateOf(startDest ?: Dest.PrintStatus)`. `StartDestMappingTest` (67L) is live with 5 test cases including injection-style garbage inputs. CI gate green. On-device flox test owner-deferred. |
| 5 | Exhaustive backfill explicitly deferred to Phase 22; foundation does not regress existing screens | ✓ VERIFIED | ROADMAP §Phase 22 explicitly covers "exhaustive preview/string/icon backfill + a LIGHT final conformance sweep". `18-VALIDATION.md` records the Phase-22 deferral (nyquist_compliant=false, per-plan). `assembleDebug` + full `testDebugUnitTest` + `compileDebugAndroidTestKotlin` all green on merged state (18-07 close-out). 3 host D-05 branches (GraphViewHost ×3 occurrences, BedMeshHeatmapHost ×2, WebcamViewHost ×2) are preview-only — device behavior unchanged. State-hoists in PrintStatus/FineTune/Spool are behavior-neutral (live container overloads delegate to the same *Content). |

**Score:** 5/5 truths verified at the code/CI tier (2 truths have pending human-verification items for the on-device/Studio tiers)

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `preview/PreviewTheming.kt` | PreviewBox + 6 seeds + fs=L | ✓ VERIFIED | 104L, `fun PreviewBox` ×1, 12 ThemeTuple occurrences (6 combos + fs=L + helpers) |
| `preview/SampleFixtures.kt` | Pure fake-state, no Moonraker | ✓ VERIFIED | 170L, `object SampleFixtures`, 0 Moonraker/okhttp imports |
| `preview/DinghyPreviews.kt` | Nexus7 device profile + annotations | ✓ VERIFIED | 52L, `NEXUS7` + `NEXUS7_PORTRAIT` specs with dpi=320, `@Nexus7Previews`, `@DeviceAndLocalePreviews` (NOT `@DinghyThemePreviews`) |
| `preview/PreviewPlaceholders.kt` | PreviewPlaceholderBox token-aware | ✓ VERIFIED | 46L, `fun PreviewPlaceholderBox`, reads `LocalTokens.current` (3 occurrences) |
| `preview/PrintStatusPreviews.kt` | Anchor exemplar with state @PreviewParameter | ✓ VERIFIED | 129L, `class PrintStatusModeProvider : PreviewParameterProvider<PrintStatusMode>`, `PreviewBox(` ×9 |
| `preview/FineTunePreviews.kt` | Capability/busy exemplar | ✓ VERIFIED | 131L, `class FineTuneVariantProvider`, 3 variants (allPresent/noFwRetraction/busy) |
| `preview/SpoolPreviews.kt` | Dense-data/image exemplar | ✓ VERIFIED | 142L, `SpoolSelectionProvider`, `PreviewBox(` ×9, `SampleFixtures.spoolList` ×5 |
| `designsystem/icons/DinghyIcon.kt` | sealed IconRef + DinghyIcon data class | ✓ VERIFIED | 34L, `sealed interface IconRef`, `data class DinghyIcon(val primary: IconRef, val alternate: String)` |
| `designsystem/icons/DinghyIcons.kt` | Registry object with .all list | ✓ VERIFIED | 93L, `object DinghyIcons`, 39 entries, `val all: List<DinghyIcon>` |
| `designsystem/icons/DinghyIconView.kt` | Unified render primitive | ✓ VERIFIED | 81L, `fun DinghyIconView`, MaterialSymbol(Ligature) + painterResource(Drawable) delegation, dpToSp helper |
| `ui/shell/StartDestMapping.kt` | Pure parseStartDest, total function | ✓ VERIFIED | 30L, `fun parseStartDest(raw: String?): Dest?`, null/blank/garbage → null |
| `MainActivity.kt` | Dev-gated start_dest read | ✓ VERIFIED | 99L, reads extra ONLY when `devCyclerEnabledBlocking()`, `EXTRA_START_DEST` const |
| `ui/shell/RootController.kt` | startDest parameter threaded in | ✓ VERIFIED | 157L, `fun RootController(container, startDest: Dest? = null)`, seeds `rememberShellNavState(startDest)` |
| `ui/shell/ShellNavState.kt` | startDest seed applied once | ✓ VERIFIED | 135L, `class ShellNavState(startDest: Dest?)`, `var dest by mutableStateOf(startDest ?: Dest.PrintStatus)` |
| `res/values/strings.xml` | Master vocabulary + format-arg + plurals | ✓ VERIFIED | 155L, `printstatus_layer_progress` (%1$d format-arg), `spool_count` plurals, `printstatus_*`/`finetune_*`/`spool_*`/`cd_*` families |
| `docs/ui_design/PREVIEW_AND_TOKENS.md` | Convention LAW doc | ✓ VERIFIED | 229L, covers PreviewBox idiom, device/locale-only multipreview note, minimized-matrix shape, fs=L-via-fsLargeSeed gotcha, `<area>_<element>`/`cd_*` convention, DinghyIcon registration, RTL rules, D-03 exclusion |
| `render/GraphViewHost.kt` | LocalInspectionMode branch (×2 overloads) | ✓ VERIFIED | 3 LocalInspectionMode occurrences (both single-snapshot and multi-trace overloads) |
| `render/BedMeshHeatmapHost.kt` | LocalInspectionMode branch | ✓ VERIFIED | 2 LocalInspectionMode occurrences |
| `render/WebcamViewHost.kt` | LocalInspectionMode branch | ✓ VERIFIED | 2 LocalInspectionMode occurrences |
| `ui/spool/scan/ScanSurface.kt` | LocalInspectionMode branch (D-05 camera) | ✓ VERIFIED | 2 LocalInspectionMode occurrences (CameraX PreviewView branched) |
| Test: `PreviewBoxSmokeTest.kt` | Bake/theme seam proves distinct tokens | ✓ VERIFIED | 64L, live assertions: `bake` called for 6 combos + fs=L, `assertNotNull`, `assertNotEquals` for distinctness |
| Test: `SampleFixturesTest.kt` | Live fixture completeness assertions | ✓ VERIFIED | 86L, live assertions on `SampleFixtures.*`, no TODO markers remaining |
| Test: `DinghyIconsTest.kt` | Icon contract + alternate uniqueness | ✓ VERIFIED | 53L, `everyEntry_hasNonBlankAlternate_andResolvableIconRef` + `alternate_isUnique_acrossAllEntries` over `DinghyIcons.all` |
| Test: `StartDestMappingTest.kt` | Safe parse including injection inputs | ✓ VERIFIED | 67L, 5 live test cases including `"PrintStatus ; rm -rf"`, `"FineTune\nMove"` injection strings |
| Test: `FineTuneNavTest.kt` | Instrumented matchers migrated to resource strings | ✓ VERIFIED | Uses `context.getString(R.string.finetune_title/finetune_motion_label/finetune_extrusion_label)`, no hardcoded `"Fine-Tune"`/`"Motion"`/`"Extrusion"` literals in assertions |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `PreviewTheming.kt` | `DinghyTheme.kt tokensFlow overload` | `ThemeResolver().bake(tuple) → flowOf → DinghyTheme(tokensFlow=)` | ✓ WIRED | `PreviewBoxSmokeTest` proves the bake seam produces distinct non-null tokens; production `tokensFlow=` overload used (not the resolver= fallback) |
| `render/GraphViewHost.kt` | `preview/PreviewPlaceholders.kt` | `if (LocalInspectionMode.current) PreviewPlaceholderBox(...)` | ✓ WIRED | 3 LocalInspectionMode checks in GraphViewHost; PreviewPlaceholderBox imported and called |
| `MainActivity.onCreate` | `container.devCyclerEnabled gate` | `devCyclerEnabledBlocking()` returns false unless gate is on | ✓ WIRED | `if (container.devCyclerEnabledBlocking()) { parseStartDest(...) }` present; bounded `runBlocking { withTimeoutOrNull(500) { ... } } ?: false` |
| `MainActivity` | `RootController(startDest=)` | parsed `Dest?` threaded as parameter | ✓ WIRED | `RootController(container, startDest = startDest)` in `setContent` |
| `RootController` | `rememberShellNavState` | `startDest` seeded once in `remember {}` | ✓ WIRED | `rememberShellNavState(startDest)`, `ShellNavState(startDest)` seeds `dest` once |
| `DinghyIconView.kt` | `designsystem/MaterialSymbol.kt` | `Ligature branch → MaterialSymbol(name, ...)` | ✓ WIRED | `MaterialSymbol(` present in DinghyIconView.kt; `painterResource` for Drawable branch |
| `DinghyIcons entries` | `alternate remap handle` | every entry has non-blank unique alternate | ✓ WIRED | `DinghyIconsTest` `alternate_isUnique_acrossAllEntries` green; 39 entries all confirmed |
| `PrintStatusScreen` | stateless overload | state-hoist to `PrintStatusContent` | ✓ WIRED | Two `fun PrintStatusScreen(` signatures (lines 120, 386); stateless drives `PrintStatusContent` |
| `FineTune screens` | stateless overloads | `MotionContent`/`ExtrusionContent` extracted | ✓ WIRED | Two `fun MotionScreen(` + two `fun ExtrusionScreen(` signatures each |
| `SpoolScreen` | stateless overload | `SpoolContent` seam | ✓ WIRED | Two `fun SpoolScreen(` signatures (lines 79, 186) |

---

### Data-Flow Trace (Level 4)

These exemplars are @Preview-only consumers (no live data path) — their data source is `SampleFixtures` (pure immutable objects, no async). Level 4 (DB query / fetch verification) does not apply. The `SampleFixturesTest` asserts the fixture data is non-empty and correctly typed. This is the expected design for a preview harness phase.

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|-------------------|--------|
| `PrintStatusPreviews.kt` | `state = SampleFixtures.forMode(mode)` | `SampleFixtures.forMode()` pure builder | Yes (fixture) | ✓ FLOWING (preview-fixture path, correct) |
| `FineTunePreviews.kt` | `vm = fineTuneAllPresent/...` | `SampleFixtures.fineTune*` | Yes (fixture) | ✓ FLOWING |
| `SpoolPreviews.kt` | `state = SampleFixtures.spoolList` | `SampleFixtures.spoolList` (8 entries) | Yes (fixture) | ✓ FLOWING |

---

### Behavioral Spot-Checks

Build-side spot-checks (grep assertions replacing live-run CI that cannot be run from this environment):

| Behavior | Check | Result | Status |
|----------|-------|--------|--------|
| `build.gradle.kts` pseudolocale wired | `grep isPseudoLocalesEnabled` | 1 match in `debug {}` block | ✓ PASS |
| `SampleFixtures` is pure (no Moonraker) | `grep -c MoonrakerClient\|okhttp\|RpcConnection` | 0 | ✓ PASS |
| `@Stable/@Immutable/ImmutableList` excluded from exemplars (D-03) | grep in PrintStatusScreen | 0 | ✓ PASS |
| PrintStatusPreviews has 9 `PreviewBox(` calls (6-theme matrix + fs=L + RTL + state-matrix) | `grep -c "PreviewBox("` | 9 | ✓ PASS |
| SpoolPreviews: `SampleFixtures.spoolList` used ×5 | `grep -c "SampleFixtures.spoolList"` | 5 | ✓ PASS |
| `printstatus_*` stringResource sites in PrintStatusScreen | `grep -c "stringResource(R.string.printstatus_"` | 23 (≥16 required) | ✓ PASS |
| `DinghyIconView(` in PrintStatusScreen | `grep -c "DinghyIconView("` | 13 | ✓ PASS |
| `spool_*` stringResource sites in SpoolScreen | `grep -c "stringResource(R.string.spool_"` | 20 | ✓ PASS |
| No TBD/FIXME/XXX in Phase-18 modified files | debt-marker grep across 15 files | 0 | ✓ PASS |
| CLAUDE.md has PREVIEW_AND_TOKENS pointer | `grep PREVIEW_AND_TOKENS CLAUDE.md` | 1 match in UI LAW block | ✓ PASS |
| `parseStartDest` handles injection-style inputs | `StartDestMappingTest` coverage | `"PrintStatus ; rm -rf"`, `"FineTune\nMove"` → null | ✓ PASS |

---

### Probe Execution

Step 7c: No conventional `scripts/*/tests/probe-*.sh` files exist in this repo. No explicit probe paths declared in any Phase-18 PLAN or SUMMARY. CI gate is `E:\Android\gw.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon` run Windows-side.

The close-out pass on 18-07 recorded: `:app:assembleDebug` + full `:app:testDebugUnitTest` + `:app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL, exit 0 on the merged state. The verifier cannot re-run this gate from WSL (Windows-side Gradle, USB device required). The SUMMARY self-check and STATE.md both record this as the authoritative result.

---

### Requirements Coverage

Phase 18 has no standalone REQ-* IDs in `REQUIREMENTS.md` — confirmed by ROADMAP.md entry: "Requirements: *(new PREV-*/I18N-* families — defined at phase discuss)*". The note in `REQUIREMENTS.md` mentioning "Phase 18 (Output Controls)" is stale pre-insertion numbering; the current Phase 18 is the Preview Harness insertion. All acceptance codes (SC-1..SC-5, D-01..D-08) are phase-scoped and tracked in `18-VALIDATION.md`.

| SC Code | Description | Status | Evidence |
|---------|-------------|--------|----------|
| SC-1 | @Preview renders 6 combos + fs=L + exemplars | CI: ✓ / Studio: ? HUMAN NEEDED | PreviewBox seam proven by PreviewBoxSmokeTest; Studio visual confirmed pending |
| SC-2 | Fixture module + device profile + written convention | ✓ VERIFIED | SampleFixtures, DinghyPreviews, PREVIEW_AND_TOKENS.md, CLAUDE.md pointer |
| SC-3 | strings.xml + icon registry + pseudolocale (lint gate downgraded+documented) | ✓ VERIFIED (with documented downgrade) | strings.xml + DinghyIcons + `isPseudoLocalesEnabled`; automated lint gate deferred to Phase 22 (fallback fired, recorded in VALIDATION.md) |
| SC-4 | Build wiring + start_dest hook + release-inert | CI: ✓ / flox: ? HUMAN NEEDED | tooling-preview wiring confirmed; parseStartDest CI-green; on-device gate deferred |
| SC-5 | Backfill deferred; no regression | CI: ✓ / flox smoke: ? HUMAN NEEDED | Phase-22 deferral in ROADMAP; testDebugUnitTest green; flox smoke deferred |

---

### Anti-Patterns Found

Code-review findings from `18-REVIEW.md` (0 Critical, 4 Warning, 6 Info):

| File | Finding | Severity | Impact |
|------|---------|----------|--------|
| `MainActivity.kt:55-56,98-99` | WR-01: `devCyclerEnabledBlocking()` runs a bounded `runBlocking { withTimeoutOrNull(500) }` on the main thread at every cold start, even in release (where the answer is always false) | WARNING | Potential 500ms UI stall on cold start on Nexus-7-class slow flash. Not a BLOCKER — timeout-bounded, cannot ANR, release always returns false. Suggested fix: gate the blocking read behind `BuildConfig.DEBUG`. |
| `DinghyIcons.kt:29,45` | WR-02: `Progress` and `LauncherSpool` both use `donut_large` ligature — silent "icon-never-twice" violation. Both appear on PrintStatus. `DinghyIconsTest` only checks `alternate` uniqueness, not `IconRef` uniqueness. | WARNING | Semantically wrong; two distinct controls render identical glyphs. Not a BLOCKER — alternates ARE unique, test passes, convention technically allows glyph reuse when semantic tokens differ. Fix deferred per 18-REVIEW. |
| `FwRetractionScreen.kt:154,173` | WR-03: Retract/Unretract speed tiles use `DinghyIcons.MaxAccel` token (semantic mislabel). Glyph renders correctly but the D-07 remap handle is wrong. | WARNING | Future remap of `max_accel` token would unintentionally remap retraction tiles. Not a BLOCKER for this phase — FwRetraction is build-blind and deferred to Phase 22. |
| `ScanSurface.kt:37`, `SpoolPreviews.kt:47` | WR-04: Stale import ordering in ScanSurface; unresolvable KDoc `[PreviewPlaceholderBox]` link in SpoolPreviews (missing import = silent dead link) | INFO | Cosmetic; not a runtime or correctness issue. |
| `PrintStatusScreen.kt:653,1313` | IN-01: `label = "Thumbnail"` hardcoded literal in Coil LocalInspectionMode branch (preview-only, never user-visible) | INFO | Inconsistent with phase tokenization discipline; shows through en-XA sweep as plain English. Phase-22 scope. |
| `ScanSurface.kt:124-163` | IN-02: Residual hardcoded user-facing copy in ScanSurface (camera permission strings, scan hints) — out of exemplar boundary | INFO | Explicitly deferred to Phase-22 backfill. Not a phase defect. |
| `FwRetractionScreen.kt` | IN-03: Residual hardcoded literals (tile names + "Back") — build-blind, out of exemplar boundary | INFO | Phase-22 backfill. Not a phase defect. |
| Various | IN-04/IN-05/IN-06: em-dash consistency, unused tempSeries fixture in previews, NEXUS7_PORTRAIT dimension KDoc misleading | INFO | All minor consistency/clarity issues. None affect correctness. |

No TBD/FIXME/XXX debt markers found in any Phase-18 modified file. No BLOCKER anti-patterns.

---

### Human Verification Required

**1. Studio Preview Render — 3 Exemplars (SC-1 visual tier)**

**Test:** Open `PrintStatusPreviews.kt`, `FineTunePreviews.kt`, `SpoolPreviews.kt` in Android Studio. Render all preview functions.
**Expected:** PrintStatus: 4/6 states across 6 themed combos + fs=L + RTL; GraphView and Coil thumbnail placeholders are labeled (not blank). FineTune: all-present / FW-retraction-HIDDEN / busy-lock variants across 6 themes + fs=L + RTL. Spool: no-selection / selected with dense 8-entry list across 6 themes + fs=L + RTL; camera placeholder labeled.
**Why human:** Host-rendered @Preview visual correctness (color fidelity, overflow/clipping, RTL mirroring) requires eyeballing. No golden-image CI net until Phase 22.

**2. start_dest Deep-Jump — flox on-device gate (SC-4 flox tier)**

**Test:** Install debug APK on flox with dev-enable ON. Run:
```
adb shell am start -n works.mees.dinghy/.MainActivity --es start_dest FineTune
adb shell am start -n works.mees.dinghy/.MainActivity --es start_dest Spool
adb shell am start -n works.mees.dinghy/.MainActivity --es start_dest PrintStatus
adb shell am start -n works.mees.dinghy/.MainActivity --es start_dest NotARealScreen
```
**Expected:** First three land on the named screen after Splash/Klippy-Ready gate. NotARealScreen → default PrintStatus, no crash.
**Why human:** Requires running app + live Klippy state to pass the Splash gate.

**3. start_dest Release-Inert Check (SC-4 security tier)**

**Test:** `pm clear works.mees.dinghy` (clean data state), install debug APK WITHOUT enabling dev-cycler. Run the `am start --es start_dest FineTune` command.
**Expected:** Extra is IGNORED; app opens normally on PrintStatus home. Confirm the release APK also has no UI path to set `devCyclerEnabled=true`.
**Why human:** Defense-in-depth "inert from clean data state" behavioral check. The code default is correct per review; this confirms the runtime behavior.

**4. Non-Exemplar Regression Smoke — flox (SC-5 flox tier)**

**Test:** Navigate through Move, Calibration, Console, Files, Temperature, Webcam, Macros, Spool-scan on the debug APK installed from the Phase-18 codebase.
**Expected:** All non-exemplar screens render and respond identically to Phase-17 state. The state-hoists in PrintStatus/FineTune/Spool are behavior-neutral; the D-05 host branches are invisible on-device.
**Why human:** Visual regression of non-exemplar screens requires a device walk; testDebugUnitTest green but covers only code paths, not rendered output.

**5. Pseudolocale en-XA Sweep (SC-3 manual tier)**

**Test:** On the debug APK, switch device locale to "English (XA)".
**Expected:** Tokenized strings (`spool_*`, `finetune_*`, `printstatus_*`) show accented/decorated text. Intentionally deferred Phase-22 backfill literals show plain English — expected and not a failure for this phase.
**Why human:** The pseudolocale sweep is the manual SC-3 completeness check substituting for the deferred automated lint gate (detekt fallback fired in 18-01). Requires running the app.

---

### Gaps Summary

No code-tier gaps found. All must-have artifacts exist, are substantive (non-stub), wired, and have live tests proving their data seams. The five ROADMAP success criteria are either fully verified at the code/CI tier (SC-2, SC-3, SC-5 foundations) or have their code-tier gates verified with on-device/Studio tiers pending owner confirmation (SC-1 Studio, SC-4 flox, SC-5 flox smoke).

The four review warnings (WR-01 through WR-04) are carried forward per the review's own disposition — none is a BLOCKER for this phase's goal. WR-01 (main-thread DataStore read) is a startup-jank concern worth addressing but does not prevent the preview harness from working. WR-02 and WR-03 are icon-convention issues deferred to Phase-22 cleanup. WR-04 is cosmetic.

Phase-18 goal (infra + convention + 2-3 exemplar screens preview-first and tokenized-first) is achieved at the code level. The remaining human items are the intended Studio-eyeball + flox tiers that the phase design explicitly deferred to an owner session (Phase-17 UAT posture).

---

_Verified: 2026-06-06_
_Verifier: Claude (gsd-verifier)_
