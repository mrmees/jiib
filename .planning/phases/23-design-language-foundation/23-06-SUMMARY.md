---
phase: 23-design-language-foundation
plan: "06"
subsystem: ui/spool
tags: [redesign, spoolman, pilot, field-takeover, unit-grid, floating-estop, fill-meter, outlined-control]
dependency_graph:
  requires: ["23-02", "23-03", "23-04", "23-05"]
  provides: ["SpoolScreen on kit (pilot)", "FieldMode sealed class", "5 new icon tokens", "OutlinedControl surface fill convention"]
  affects: ["SpoolHolder.kt", "SpoolPicker.kt", "AppShell.kt", "DinghyIcons.kt"]
tech_stack:
  added:
    - "FieldMode sealed class (Spools / FilterPicker) in SpoolHolder.kt"
    - "5 new DinghyIcon tokens: MatchCase, CalendarClock, Experiment, Home, QrCode"
  patterns:
    - "ColumnScope receiver extension functions for composables needing weight() modifier"
    - "Stateless preview seam with isPrinting: Boolean for FloatingEStop preview exercise"
    - "LIVE printerState wiring via container.printerState (LastJobHolder convention)"
    - "OutlinedControl must carry .background(t.surface) so filled controls read as filled vs translucent list rows"
key_files:
  created:
    - "app/src/test/java/works/mees/dinghy/ui/spool/SpoolPickerStateTest.kt"
    - "app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderVendorTest.kt"
  modified:
    - "app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt"
    - "app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt"
    - "app/src/main/java/works/mees/dinghy/ui/spool/SpoolPicker.kt"
    - "app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt"
    - "app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt"
    - "app/src/main/java/works/mees/dinghy/designsystem/components/OutlinedControl.kt"
    - "app/src/main/res/values/strings.xml"
    - "app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt"
    - "app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt"
    - "tools/verify_ligatures.py"
decisions:
  - "ColumnScope receiver extension on SpoolListField/SpoolFilterPickerField (not modifier parameter) to give .weight() access without adding new callback surface"
  - "ColorSwatchGrid promoted from private to internal (same-package SpoolPicker.kt→SpoolScreen.kt call)"
  - "OptionButton removed (unused after SpoolFilterPickerOverlay deletion)"
  - "Home foot button navigates to Dest.PrintStatus (the hub screen) in AppShell.kt"
  - "5 new icons (MatchCase/CalendarClock/Experiment/Home/QrCode) registered from img/material-icon-bucket.json — all owner-curated, bundled font verified"
  - "OutlinedControl given explicit .background(t.surface) so controls render FILLED — the filled-vs-translucent distinction is not automatic"
  - "Vendor filter multi-select (MFG): code migrated to vendors:List<String> + OR-match (2b6c92a); on-device re-test of that specific build DEFERRED to Phase 29 — status is UNCONFIRMED vs STALE APK"
patterns-established:
  - "Fill convention enforcement: OutlinedControl.background(t.surface) required; translucent list rows get NO background — distinction is visible only if both are enforced"

requirements-completed: []

# Metrics
duration: "~90 minutes (Tasks 1-2 implementation + 2 UAT fix rounds)"
completed_date: "2026-06-09"
tasks_completed: 3
tasks_total: 3
files_changed: 11
---

# Phase 23 Plan 06: SpoolScreen Pilot on Kit Summary

**SpoolScreen rebuilt on the jiib design-kit as the Phase 23 pilot — FieldMode in-place filter, DetailCard+FillMeter in Focus, FootButtonBar with locked Load/Unload, FloatingEStop wired to live printerState, U from rememberUnitGrid — owner-approved on flox in both orientations with one UAT item (MFG multi-select) deferred to Phase 29.**

## Performance

- **Duration:** ~90 minutes (Tasks 1–2 implementation + two UAT-fix rounds)
- **Completed:** 2026-06-09
- **Tasks:** 3 of 3
- **Files modified:** 11

## Accomplishments

- SpoolScreen fully rebuilt on the new component-class kit (ListRow/DetailCard/FillMeter/FootButtonBar/FloatingEStop/SortFilterControlRow), gutter nulled, unit-grid root with `rememberUnitGrid(minOf(maxWidth,maxHeight))` — U is constant across rotation, confirmed via on-device debug uDp readout
- In-place Field-takeover filter picker (FieldMode sealed class; SpoolFilterPickerOverlay removed) with TYPE/polymer multi-select working on-device
- Two UAT fixes landed after owner testing: filled controls via `OutlinedControl.background(t.surface)` (fill convention was invisible without it) + vendor filter migrated from `vendor:String?` to `vendors:List<String>` with OR-match and explicit Done/Clear foot buttons
- Owner approved the pilot on flox (genuine Nexus 7 2013 / Adreno 320 / LineageOS 18.1 / API 30) in both portrait and landscape; all checks PASS except MFG multi-select (deferred — see below)

## Task Commits

1. **Task 1: SpoolScreen + SpoolPicker + AppShell + DinghyIcons rebuild** — `733d868` (feat)
2. **Task 2: SpoolPreviews matrix + SampleFixtures FieldMode states** — `8bedac5` (feat)
3. **Task 1 icon tooling** — `361f034` (chore — verify_ligatures.py NEEDED set update, 74 total, 0 missing)
4. **UAT fix 1 — OutlinedControl surface fill** — `94c3f8e` (fix — `.background(t.surface)` so controls read FILLED)
5. **UAT fix 2 — multi-select vendor filter** — `2b6c92a` (fix — `vendor:String?` → `vendors:List<String>`, OR-within-facet, explicit Done/Clear; COMPONENTS.md interaction note synced)
6. **UAT fix 2 tests** — `42a5ec2` (test — SpoolHolderVendorTest: 11 tests, vendor multi-select toggle/OR-match/no-auto-close)
7. **MFG deferral tracking** — `a9d43b2` (docs — todo created at `.planning/todos/pending/2026-06-09-phase-23-mfg-multiselect-deferred.md`)

## Files Created/Modified

- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt` — Full rebuild: ScreenScaffold(gutter=null), FieldMode, DetailCard+FillMeter, FootButtonBar, FloatingEStop wired to live printerState, rememberUnitGrid at root, debug uDp badge/log
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt` — FieldMode sealed class added; SpoolPickerState.fieldMode field; openFilterPicker/closeFilterPicker; vendor field migrated to vendors:List<String>
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolPicker.kt` — SpoolFilterPickerOverlay removed; ColorSwatchGrid promoted to internal; OptionButton removed; vendor OR-filter predicate updated
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` — onBack→onHome wired to Dest.PrintStatus; container= added for FloatingEStop's printerState
- `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` — 5 new tokens: MatchCase, CalendarClock, Experiment, Home, QrCode
- `app/src/main/java/works/mees/dinghy/designsystem/components/OutlinedControl.kt` — `.background(t.surface)` added for the fill convention
- `app/src/main/res/values/strings.xml` — New cd_* strings for foot button labels
- `app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt` — Full matrix: no-selection / selected / FilterPicker-open / isPrinting=true (FloatingEStop)
- `app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt` — spoolWithFilterOpen fixture (FieldMode.FilterPicker(TYPE))
- `tools/verify_ligatures.py` — NEEDED set updated to 74 glyphs
- `app/src/test/java/works/mees/dinghy/ui/spool/SpoolPickerStateTest.kt` — Created: 8 tests, FieldMode state machine
- `app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderVendorTest.kt` — Created: 11 tests, vendor multi-select toggle/OR-match/no-auto-close

## Decisions Made

- **ColumnScope receivers** for SpoolListField/SpoolFilterPickerField instead of a modifier parameter — they're always called from a `ColumnScope` lambda; no new callback surface exposed
- **ColorSwatchGrid internal** (not private) — cross-file access required when the COLOR filter picker moved to SpoolScreen.kt's Field-takeover path
- **OptionButton removed** — dead code after SpoolFilterPickerOverlay deletion
- **Home foot button** → `Dest.PrintStatus` (the hub screen); AppShell handles it via the new onHome callback
- **5 icon tokens** registered from `img/material-icon-bucket.json` (owner-curated, all present in bundled v2.944 font — fonttools confirmed)
- **OutlinedControl fill** — the fill convention requires an explicit `.background(t.surface)` on every `OutlinedControl`; the translucent list rows carry no background. This distinction is not automatic from the component — it must be enforced at call sites. Caught at owner UAT.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Missing `import works.mees.dinghy.command.dispatch` extension**
- **Found during:** Task 1 build
- **Issue:** Dispatch call resolved to the wrong overload — "Argument type mismatch: actual type is 'CommandSpec<Unit>', but 'String' was expected"
- **Fix:** Added the typed dispatch extension import
- **Files modified:** SpoolScreen.kt
- **Commit:** 733d868

**2. [Rule 1 - Bug] `Modifier.weight()` unavailable in standalone composable functions**
- **Found during:** Task 1 build
- **Issue:** 7 compile errors — SpoolListField, SpoolFilterPickerField, SpoolRowBody used `.weight(1f)` without a ColumnScope/RowScope receiver
- **Fix:** Made SpoolListField + SpoolFilterPickerField ColumnScope receiver extensions; SpoolRowBody a RowScope receiver
- **Files modified:** SpoolScreen.kt
- **Commit:** 733d868

**3. [Rule 1 - Bug] `originalWeight` smart cast impossible on open getter property**
- **Found during:** Task 1 build
- **Issue:** `spool.remainingWeight` / `spool.originalWeight` have custom getters — Kotlin can't smart-cast after a null check on a re-readable property
- **Fix:** Captured to locals before the conditional
- **Files modified:** SpoolScreen.kt
- **Commit:** 733d868

**4. [Rule 1 - Bug] Invalid import for ListBlock — `components` vs `layout` package**
- **Found during:** Pre-build code review
- **Issue:** SpoolScreen.kt had `import works.mees.dinghy.designsystem.components.ListBlock` (wrong package)
- **Fix:** Removed the invalid import; the correct `designsystem.layout.ListBlock` import was already present
- **Files modified:** SpoolScreen.kt
- **Commit:** 733d868

**5. [Rule 2 - Missing functionality] ColorSwatchGrid was private — cross-file access blocked**
- **Found during:** Pre-build code review
- **Issue:** `ColorSwatchGrid` in SpoolPicker.kt was `private` (file-private); SpoolScreen.kt's Field-takeover COLOR picker needed it
- **Fix:** Promoted to `internal`
- **Files modified:** SpoolPicker.kt
- **Commit:** 733d868

**6. [Rule 2 - Missing functionality] 5 missing DinghyIcon tokens (MatchCase, CalendarClock, Experiment, Home, QrCode)**
- **Found during:** Pre-build code review
- **Issue:** SpoolScreen.kt referenced 5 icons not yet in the registry; all 5 are in `img/material-icon-bucket.json` (owner-curated)
- **Fix:** Registered all 5 in DinghyIcons.kt, added to `all` list, updated verify_ligatures.py NEEDED set
- **Files modified:** DinghyIcons.kt, tools/verify_ligatures.py
- **Commit:** 733d868, 361f034

**7. [Rule 1 - Bug (UAT)] OutlinedControl missing surface background — fill convention invisible**
- **Found during:** Task 3 owner UAT on flox
- **Issue:** Controls (foot buttons, filter tiles) did not visually read as FILLED vs the translucent list rows — the fill convention was a no-op without an explicit surface background
- **Fix:** Added `.background(t.surface)` to `OutlinedControl.kt`
- **Files modified:** OutlinedControl.kt
- **Commit:** 94c3f8e

**8. [Rule 1 - Bug (UAT)] Vendor filter multi-select — single-String field didn't support OR-match**
- **Found during:** Task 3 owner UAT on flox (MFG filter)
- **Issue:** `vendors: String?` on SpoolFilterState only permitted single-vendor selection; the TYPE/polymer filter already multi-selected via a List; MFG needed parity
- **Fix:** Migrated SpoolFilterState.vendor→vendors:List<String>, filter predicate to OR-within-facet, added explicit Done/Clear foot buttons in the FilterPicker FieldMode
- **Files modified:** SpoolHolder.kt, SpoolPicker.kt, SpoolScreen.kt, docs/ui_design/COMPONENTS.md
- **Commit:** 2b6c92a (fix), 42a5ec2 (tests)

---

**Total deviations:** 8 (6 build-time auto-fixes + 2 UAT-driven fixes)
**Impact on plan:** All fixes necessary for correctness. UAT fixes 7 and 8 were caught at the on-device gate as designed (mock-vs-reality discipline). No scope creep.

## Deferred / Known Issues

### MFG (Vendor) Multi-Select Filter — DEFERRED to Phase 29

**Item:** SpoolScreen MFG/vendor filter multi-select on-device verification
**Status:** UNCONFIRMED — neither confirmed resolved nor confirmed broken
**Deferred to:** Phase 29 (Release Hardening)
**Tracked at:** `.planning/todos/pending/2026-06-09-phase-23-mfg-multiselect-deferred.md`

**What happened:**
- Owner reported "mfg isn't [working]" during UAT on the initial build
- A continuation executor migrated vendor from `String?` to `List<String>` with OR-match (`2b6c92a`) and added `SpoolHolderVendorTest` (`42a5ec2`, 11 host tests green)
- The executor claimed the earlier failure was a STALE APK (Gradle `UP-TO-DATE` without `--rerun-tasks`) and force-rebuilt a fresh APK
- **The owner did NOT re-test the fresh build** before choosing to defer; the stale-APK hypothesis is plausible but unconfirmed
- TYPE/polymer multi-select was confirmed working on-device throughout

**What is NOT a defect:** COLOR filter is intentionally single-select + auto-close (Spoolman color-similarity endpoint takes one color).

**Action at Phase 29:** Build fresh (APK mtime must be after `2b6c92a`), re-test MFG multi-select on flox. If works → stale-APK confirmed, close. If still broken → trace the vendor path vs the working TYPE path and fix.

## On-Device UAT Results

Tested on flox (Nexus 7 2013 / LineageOS 18.1 / API 30 / Adreno 320) in both portrait and landscape:

| Check | Result |
|-------|--------|
| Fill convention visible (translucent list / filled controls) | PASS (after UAT fix 7) |
| Smooth scroll + edge-fade (list scrolls UNDER fade) | PASS |
| DetailCard color-reactive ring + FillMeter | PASS |
| In-place Field-takeover filter (no full-screen overlay) | PASS |
| Sort direction indicator on active tile | PASS |
| Foot buttons: Home · Scan · conditional Load/Unload | PASS |
| U constant through rotation (debug uDp readout EQUAL portrait vs landscape) | PASS |
| FloatingEStop printing-only (absent idle / present printing, raises ConfirmGuard) | PASS |
| TYPE/polymer multi-select filter | PASS |
| MFG/vendor multi-select filter | DEFERRED (see above) |

**Overall: owner-approved with one item deferred to Phase 29.**

## Verification

- `:app:testDebugUnitTest --tests works.mees.dinghy.ui.spool.*` — GREEN (Task 1)
- `verify_ligatures.py` — 74 needed, 0 missing (font gate)
- DinghyIconsTest drift guard — GREEN (new icons in `all` list)
- FULL `:app:testDebugUnitTest :app:assembleDebug` — BUILD SUCCESSFUL (Task 2)
- SpoolHolderVendorTest (11 tests) — GREEN (vendor multi-select host tests)
- On-device flox owner approval — APPROVED with MFG multi-select deferred to Phase 29

## Issues Encountered

- Gradle `UP-TO-DATE` stale-APK trap: the continuation executor encountered a Gradle UP-TO-DATE no-op that may have installed an old APK for MFG re-testing. This is a recurring on-device iteration hazard (see [[dinghy-display-ondevice-iteration]]). Force-rebuild (`--rerun-tasks`) is the mitigation; Phase 29 must use it for the MFG re-test.

## Next Phase Readiness

Phase 23 pilot (SC-3 + SC-4) is owner-approved. The Phase 23 Design-Language Foundation phase is complete at 6/6 plans. The new component-class kit (ListRow/DetailCard/FillMeter/FootButtonBar/FloatingEStop/SortFilterControlRow) + unit-grid root + FieldMode pattern are locked and demonstrated on real hardware. Phase 24 (Spine & Core Screens) may proceed.

One pending item for Phase 29: re-verify MFG multi-select on a confirmed-fresh build.

## Self-Check: PASSED

All committed files verified present in git log. Implementation was already committed by prior executors; this SUMMARY reflects the final owner-approved state.

- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt` — FOUND
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt` — FOUND (FieldMode + vendors:List)
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolPicker.kt` — FOUND (overlay removed)
- `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` — FOUND (5 new tokens)
- `app/src/main/java/works/mees/dinghy/designsystem/components/OutlinedControl.kt` — FOUND (surface bg)
- `app/src/test/java/works/mees/dinghy/ui/spool/SpoolPickerStateTest.kt` — FOUND
- `app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderVendorTest.kt` — FOUND
- `app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt` — FOUND (new previews)
- `app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt` — FOUND (spoolWithFilterOpen)
- Commit 733d868 — FOUND
- Commit 8bedac5 — FOUND
- Commit 361f034 — FOUND
- Commit 94c3f8e — FOUND
- Commit 2b6c92a — FOUND
- Commit 42a5ec2 — FOUND
- Commit a9d43b2 — FOUND

---
*Phase: 23-design-language-foundation*
*Completed: 2026-06-09*
