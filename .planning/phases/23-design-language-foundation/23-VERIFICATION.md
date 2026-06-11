---
phase: 23-design-language-foundation
verified: 2026-06-09T00:00:00Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
resolution: "Closed 2026-06-10 — owner re-tested on a fresh mtime-verified build and the filter WAS genuinely broken (stale-APK hypothesis refuted): buildSpoolQuery sent repeated filament.vendor.name params which FastAPI collapses to the last (Spoolman's OR contract is comma-joined terms in ONE param), and the option list came from the manufacturers table instead of being derived from physical spools (mfg → filament → spool). Both fixed in 0b2daaf via debug session spool-mfg-filter-multiselect (archived in .planning/debug/resolved/), owner-confirmed on flox across two checkpoint rounds."
---

# Phase 23: Design-Language Foundation — Verification Report

**Phase Goal:** Build the redesign's reusable foundation — rewrite LAYOUT.md off the Gutter (Focus/Field + unit grid U), author COMPONENTS.md catalog, build the reusable Compose component-class kit (ListRow, DetailCard, FillMeter, sort/filter row, FootButtonBar, unit grid, fill convention, intent colors — STEPPER/SCRUBBER DEFERRED to Phase 26), register the new icon assignments, and rebuild SpoolScreen as the owner-approved pilot.
**Verified:** 2026-06-09T00:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | LAYOUT.md rewritten (Focus/Field, NO gutter, unit-grid U rules) + COMPONENTS.md authored as the component-class catalog | VERIFIED | `docs/ui_design/LAYOUT.md` has two-region table, "Where the gutter's jobs went" section, §"The unit U" with fit-preserving formula (nTarget/nMaxFit/min), §fill convention, §foot-of-list, §floating e-stop; 5 NON-NEGOTIABLE instances + 10 C3/C6 references preserved. `docs/ui_design/COMPONENTS.md` has 8-class catalog table (ListRow/DetailCard/FillMeter/FootButtonBar/FloatingEStop/SortFilterControlRow/control-tile/ListBlock), field-takeover, Phase 26 deferral noted, NON-CONFORMANT bare-option-list called out, THEME-01 data carve-outs documented. |
| 2 | Component-class kit built and HOST-TESTED (ListRow, DetailCard, FillMeter, sort/filter row, FootButtonBar, unit grid, fill convention, intent colors) with @Preview matrices. Stepper/scrubber DEFERRED to Phase 26. | VERIFIED | All 8 kit files exist and are substantive (not stubs): `UnitGrid.kt` (fit-preserving `min(nTarget, nMaxFit)` formula, `rememberUnitGrid` with `remember(contentMinDim)` key), `ListBlock.kt` (edge-faded LazyColumn, fade Boxes have no pointer modifiers), `ListRow.kt` (accentSoft/transparent + listRowUsesAccentFill/listRowBorderWidthFor testable helpers), `DetailCard.kt` (rCard/cardSurface/THEME-01 KDoc), `FillMeter.kt` (clampFraction pure helper, no blurMaskFilter), `FootButtonBar.kt` (no internal padding after WR-01 fix), `FloatingEStop.kt` (Intent.Danger, DinghyIcons.StatusStop, uDp*0.7f), `SortFilterControlRow.kt` (leading recessed type tiles + DinghyIcon overload, no raw symbol strings). Host tests GREEN: UnitGridTest 6/6 (incl. 320dp and 360dp no-overflow cases), ListRowTest 3/3, FillMeterTest 3/3 — full 34-task suite passes. Preview matrices: `DesignKitLayoutPreviews.kt` (6-theme + FsLarge + RTL + pseudolocale + SmallPhoneFloor), `DesignKitComponentPreviews.kt` (6-theme + FsLarge + RTL + pseudolocale, all 6 classes). |
| 3 | SpoolScreen rebuilt onto the classes and owner-approved on flox in BOTH orientations (pilot). ONE item explicitly deferred: MFG/vendor multi-select filter to Phase 29, tracked in `.planning/todos/pending/2026-06-09-phase-23-mfg-multiselect-deferred.md`. | VERIFIED (documented deferral) | `SpoolScreen.kt` imports and uses all 6 kit components (DetailCard, FillMeter, FootButtonBar, FloatingEStop, ListRow, SortRow, FilterRow, ListBlock). `gutter = null` confirmed (3 instances). `rememberUnitGrid(minOf(maxWidth, maxHeight))` at root (2 instances). `FieldMode` sealed class in `SpoolHolder.kt`. `SpoolFilterPickerOverlay` absent from codebase (grep = 0). `SpoolHolderVendorTest` (11 tests, GREEN) + `SpoolPickerStateTest` (8 tests, GREEN). Owner-approved on flox (23-06-SUMMARY UAT table: 9/10 PASS, MFG deferred). MFG deferral todo file exists at the tracked path. |
| 4 | Pilot demonstrates the locked rules: unit grid, translucent-list/filled-controls, no-label grouping, icon-registry glyphs, sort-vs-filter + Field-takeover picker | VERIFIED | Unit grid: `rememberUnitGrid(minOf(maxWidth,maxHeight))` at SpoolScreen root, debug uDp log/badge (`BuildConfig.DEBUG` guarded — `Log.d("UnitGrid", ...)` at line 296 + badge at line 429). Fill convention: `OutlinedControl.kt` carries `.background(t.surface)` (UAT fix 94c3f8e — owner confirmed visible on flox). No-label grouping: SortFilterControlRow type tiles carry no Text words (verified in SortFilterControlRow.kt). Icon-registry glyphs: `icon = option.icon` (DinghyIcon overload) in SortFilterControlRow, `icon = DinghyIcons.Check` for Done button (WR-02 partial fix), Clear button has TODO awaiting owner assignment (intentional per icon law — not a defect). Sort-vs-filter + field-takeover: FieldMode.FilterPicker replaces SpoolFilterPickerOverlay, SortRow direction indicator present. |
| 5 | New icon assignments registered in DinghyIcons.kt + img/material-icon-bucket.json (sort, filter_list, expand_circle_up/down, reset_wrench, reset_settings; output_circle already present pre-Phase-23 as OutputCircle) with stale play_circle/stop_circle notes reconciled + .ctl.warn oklch color-mix bug fixed | VERIFIED | 6 new vals in DinghyIcons.kt: Sort/FilterList/ExpandCircleUp/ExpandCircleDown/ResetWrench/ResetSettings — all added to `DinghyIcons.all`. `output_circle` already registered as `OutputCircle` (Phase 19); icon-assignments note confirms "REUSE — already the extrude glyph" — no new registration needed for SC-5. Bucket notes for play_circle/stop_circle updated to reference expand_circle_up/down reassignment (valid JSON, python load passes). `.ctl.warn` in `hifi.css` uses `border-color: var(--heat)` directly (grep confirms 0 instances of `color-mix(in oklch[^)]*--heat[^)]*--outline`). `verify_ligatures.py` gate: 73 needed, 3953 in font, missing: [] (exits 0). DinghyIconsTest GREEN. |

**Score:** 5/5 truths verified

### Deferred Items

Items not yet met but explicitly addressed in later milestone phases.

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | Stepper/scrubber component restyle | Phase 26 | ROADMAP SC-2 explicit: "Stepper/scrubber RESTYLE is DEFERRED to Phase 26 (Adjustment Screens)". COMPONENTS.md §7 documents this. Phase 26 goal covers `ScrubberPage`/stepper pages. |
| 2 | MFG/vendor multi-select filter on-device confirmation | Phase 29 | Tracked in `.planning/todos/pending/2026-06-09-phase-23-mfg-multiselect-deferred.md`. Phase 29 (Release Hardening) is the re-test checkpoint. Host tests (SpoolHolderVendorTest 11/11) cover the state machine; only the on-device confirmation is deferred. |

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `docs/ui_design/LAYOUT.md` | Rewritten two-region grammar + unit U | VERIFIED | Two-region Focus/Field table, fit-preserving U formula, fill convention, foot-of-list, floating e-stop sections present; NON-NEGOTIABLEs preserved |
| `docs/ui_design/COMPONENTS.md` | New component-class catalog | VERIFIED | 8-class catalog, fill convention, field-takeover, SortFilterControlRow anatomy, Phase 26 deferral, THEME-01 carve-outs |
| `docs/ui_design/CLAUDE.md` | Updated pointer to no-gutter grammar + COMPONENTS.md | VERIFIED | References COMPONENTS.md, gutter-removed grammar, icon law preserved |
| `app/src/main/java/works/mees/dinghy/designsystem/layout/UnitGrid.kt` | FIT-PRESERVING unit grid formula | VERIFIED | `unitGridFor` with `min(nTarget, nMaxFit)`, `rememberUnitGrid` with `remember(contentMinDim)` key, dp-derived clarification KDoc |
| `app/src/main/java/works/mees/dinghy/designsystem/layout/ListBlock.kt` | Edge-faded LazyColumn, no touch-blocking fades | VERIFIED | `rememberLazyListState`, `canScrollForward`/`firstVisibleItem*` driven fades, fade Boxes have no clickable/pointerInput modifiers |
| `app/src/main/java/works/mees/dinghy/designsystem/components/ListRow.kt` | Translucent/accentSoft fill convention | VERIFIED | `listRowUsesAccentFill`/`listRowBorderWidthFor` pure helpers, `Color.Transparent`/`t.accentSoft`, `heightIn(min=uDp)` |
| `app/src/main/java/works/mees/dinghy/designsystem/components/DetailCard.kt` | Filled color-reactive card + cardSurface() | VERIFIED | `rCard`, `cardSurface` modifier extension, THEME-01 data carve-out KDoc |
| `app/src/main/java/works/mees/dinghy/designsystem/components/FillMeter.kt` | Read-only clamped fill bar, no glow | VERIFIED | `clampFraction` pure helper, no `blurMaskFilter`, GeistMono label at `fsSp(15f, t.fs).sp` |
| `app/src/main/java/works/mees/dinghy/designsystem/components/FootButtonBar.kt` | Foot-of-list action row, no internal padding | VERIFIED | No internal `.padding()` in Row modifier (WR-01 fixed); KDoc states placement in field lambda |
| `app/src/main/java/works/mees/dinghy/designsystem/components/FloatingEStop.kt` | Printing-only overlay, Intent.Danger, registered glyph | VERIFIED | `Intent.Danger`, `DinghyIcons.StatusStop`, `uDp * 0.7f`, `if (!visible) return` guard |
| `app/src/main/java/works/mees/dinghy/designsystem/components/SortFilterControlRow.kt` | Compound row with leading type tiles, DinghyIcon overload | VERIFIED | DinghyIcons.Sort/FilterList leading tiles, `OutlinedControl(..., icon = option.icon, ...)` overload, no raw symbol strings, ImmutableList API |
| `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` | 6 new icon registrations | VERIFIED | Sort, FilterList, ExpandCircleUp, ExpandCircleDown, ResetWrench, ResetSettings — all in `all` list |
| `docs/ui_design/reference/hifi.css` | .ctl.warn caution-reads-amber (no color-mix) | VERIFIED | `.ctl.warn { border-color: var(--heat); }` — zero `color-mix(in oklch…--heat…--outline)` instances |
| `img/material-icon-bucket.json` | play_circle/stop_circle notes reconciled | VERIFIED | Both notes reference expand_circle_up/down reassignment; valid JSON |
| `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt` | SpoolScreen rebuilt on kit, gutter=null, U at root | VERIFIED | All 8 kit imports present; gutter=null (3×); rememberUnitGrid at root; FieldMode; FloatingEStop wired to live printerState+emergencyStop; debug uDp readout |
| `app/src/main/java/works/mees/dinghy/preview/DesignKitLayoutPreviews.kt` | @Preview matrix for layout primitives | VERIFIED | 6-theme + FsLarge via fsLargeSeed + RTL + pseudolocale + SmallPhoneFloor (360dp) present |
| `app/src/main/java/works/mees/dinghy/preview/DesignKitComponentPreviews.kt` | @Preview matrix for all 6 component classes | VERIFIED | 6-theme + FsLarge + RTL + pseudolocale; all 6 classes exercised |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `SortFilterControlRow.kt` | `OutlinedControl.kt` | `icon = option.icon` DinghyIcon overload | VERIFIED | `grep -c 'icon = ' SortFilterControlRow.kt` > 0; `grep -c 'symbol = "' SortFilterControlRow.kt` = 0 |
| `UnitGridTest.kt` | `UnitGrid.kt` | calls `unitGridFor(contentMinDim)` | VERIFIED | 8 references to `unitGridFor` in test file; no fail() stubs remaining |
| `SpoolScreen.kt` | `designsystem/components/*` | imports DetailCard/FillMeter/FootButtonBar/FloatingEStop/ListRow | VERIFIED | All 8 imports present; used in rendering body |
| `SpoolScreen.kt` | `UnitGrid.kt` | `rememberUnitGrid(minOf(maxWidth, maxHeight))` at screen root | VERIFIED | 2 instances; BoxWithConstraints provides minOf(maxWidth, maxHeight) |
| `SpoolScreen.kt` | `CommandRegistry.emergencyStop` | `dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit)` via ConfirmGuard | VERIFIED | `grep -c 'emergencyStop' SpoolScreen.kt` = 8; ConfirmGuard shown before dispatch |
| `SpoolScreen.kt` | `container.printerState` | `collectAsStateWithLifecycle()` for `isPrinting` derivation | VERIFIED | `grep -c 'printerState' SpoolScreen.kt` = 8; `PrintState.Printing` check present |
| `SpoolHolder.kt` | `AppShell.kt` | `cancel()` + `DisposableEffect(spoolHolder)` (CR-01 fix) | VERIFIED | `fun cancel()` in SpoolHolder; `DisposableEffect(spoolHolder)` in AppShell; `holderScope`/`holderJob` present |
| `DinghyIcons.kt` | `DinghyIcons.all` | all 6 new vals added to list | VERIFIED | Grep confirms `Sort, FilterList, ExpandCircleUp, ExpandCircleDown, ResetWrench, ResetSettings` in the `all` list |
| `docs/ui_design/CLAUDE.md` | `docs/ui_design/COMPONENTS.md` | "See COMPONENTS.md for the component-class catalog" pointer | VERIFIED | grep -c 'COMPONENTS' CLAUDE.md > 0; gutter-removed grammar reflected |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `FloatingEStop` visibility in SpoolScreen | `isPrinting: Boolean` | `container.printerState.collectAsStateWithLifecycle()` → `printState == Printing \|\| Paused` | Yes — live StateFlow from MoonrakerSession | FLOWING |
| `DetailCard` ringColor in SpoolScreen | `resolvedSpoolColor: Color?` | `state.activeSpoolDetail?.colorHex` (Spoolman data via SpoolHolder) | Yes — live Spoolman spool data | FLOWING |
| `FillMeter` fraction in SpoolScreen | `remainingFraction` | `remaining / original` from `activeSpoolDetail` spool weights | Yes — live Spoolman data | FLOWING |
| `ListBlock` items in SpoolScreen | `filteredSpools: ImmutableList<SpoolmanSpool>` | `SpoolPickerState.filteredSpools` derived from `SpoolHolder.spools` StateFlow | Yes — live Spoolman REST data | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| ligature gate exits 0 (all required glyphs in font) | `python3 tools/verify_ligatures.py` | `73 needed, 3953 ligatures in font, missing: []` | PASS |
| Full unit test suite passes | `:app:testDebugUnitTest --rerun-tasks` | BUILD SUCCESSFUL, 34 tasks executed | PASS |
| UnitGridTest no-overflow at 320dp: N=5, uDp=64dp | `UnitGridTest.no_overflow_at_320dp` | count*uDp <= 320dp AND uDp >= 64dp AND count==5 AND uDp==64dp | PASS |
| UnitGridTest no-overflow at 360dp: N=5 | `UnitGridTest.no_overflow_at_360dp` | product <= 360dp AND uDp >= 64dp AND count==5 | PASS |

### Requirements Coverage

This phase maps to UX Foundation (no REQUIREMENTS.md numeric IDs). The `requirements: []` in all 6 PLAN frontmatter files is correct. No orphaned requirements were found for Phase 23 in REQUIREMENTS.md.

### Anti-Patterns Found

All anti-patterns from the code review (23-REVIEW.md) were fixed via the review-fix pass (23-REVIEW-FIX.md). One intentional open item remains:

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `SpoolScreen.kt` | 652 | `symbol = "close"` — raw ligature string, font-unvalidated | ⚠ Warning (intentional) | Clear button awaiting owner icon assignment per icon law; `// TODO(23-rev WR-02): close glyph awaiting owner icon assignment` comment present. Not an unresolved defect — it is the correct holding pattern per [[dinghy-never-pick-icons-ask]] |

This is explicitly documented as intentional in 23-REVIEW-FIX.md. It does not block goal achievement — the Clear button renders (the `"close"` ligature is present in Material Symbols v2.944 at runtime) but is not font-validated via the gate. The owner must assign the official close/dismiss glyph and register it before this can be routed through the registry.

### Human Verification Required

#### 1. MFG/Vendor Multi-Select Filter on flox

**Test:** At Phase 29 (Release Hardening), build a fresh APK (must be after commit `2b6c92a` — force-rebuild with `--rerun-tasks`), install on flox, navigate to Spool screen, tap the MFG/vendor filter tile, tap multiple vendor names. Confirm each selection adds an accent-highlighted chip. Then confirm the spool list is filtered to spools from ANY selected vendor (OR-match across the selected vendors). Tap Clear or Done to dismiss.

**Expected:** Multiple vendors can be selected simultaneously; the list shows spools from any of the selected vendors; selecting then deselecting returns to the full list; TYPE and COLOR filters are unaffected.

**Why human:** The vendor field migration from `String?` to `List<String>` was committed (`2b6c92a`) and 11 host tests cover the state machine (SpoolHolderVendorTest). However the owner did not personally re-test after the fix — the executor force-rebuilt and believed it resolved, but the stale-APK hypothesis was unconfirmed. Only a fresh build + on-device test by the owner can confirm resolution.

---

## Gaps Summary

No gaps were found. All 5 Success Criteria are verified in the codebase. The one remaining human-needed item (MFG multi-select) is a deferred-confirmation not a code defect — the code was fixed and the host test suite covers it, but the on-device confirmation was explicitly deferred to Phase 29 with a tracking todo.

**Notable resolved items from the post-execution code review (23-REVIEW.md):**
- CR-01: SpoolHolder coroutine leak on reconnect — FIXED (`9a7125c`): `holderScope`/`holderJob` + `cancel()` + `DisposableEffect` in AppShell
- CR-02: Duplicate LazyColumn key crash in preview — FIXED (`b31a2fd`): index-based `items(size)` instead of key-collision form
- WR-01: FootButtonBar double-padding — FIXED (`ad575f7`/`e333199`): internal padding removed
- WR-02: Done button raw string — FIXED (`0f8ca67`): `icon = DinghyIcons.Check`; Clear button intentionally open (icon law)
- WR-03: Hardcoded UI strings — FIXED (`6cda3da`): 3 strings routed through `stringResource()`
- WR-04: Dead composables — FIXED (`5b10ad2`): `SpoolPicker()` and `SpoolFilterControls()` deleted
- WR-05: Stale "database" ligature gate entry — FIXED (`f784e7c`): removed from NEEDED set

---

_Verified: 2026-06-09_
_Verifier: Claude (gsd-verifier)_
