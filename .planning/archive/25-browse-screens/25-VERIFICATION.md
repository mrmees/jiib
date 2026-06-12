---
phase: 25-browse-screens
verified: 2026-06-10T18:00:00Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
human_needed_resolution: >
  Both human_verification items resolved 2026-06-10. Owner adjudicated via checkpoint:
  CR-01 = remove the Files-local e-stop (redundant with AppShell's working app-level overlay)
  - removed in fix commit cd96fe2. CR-02 = regression, fix to full-focus - fixed in 39343e9
  (field slot null when picker hidden, Back bar moved to gutter) and re-verified live on flox
  (fresh debug build, full-screen H.264 feed confirmed by screenshot). All 10 Critical+Warning
  review findings fixed via the code-review fix pass (cd96fe2..cde6725); host suite green;
  verify_ligatures 87/87.
human_verification:
  - test: "Confirm FilesScreen e-stop ConfirmGuard actually stops the printer (CR-01)"
    expected: "Confirming the e-stop guard on Files screen should dispatch EMERGENCY_STOP to Moonraker — the printer halts. Currently onConfirm = { guard = null } with no dispatch, so confirming does nothing."
    why_human: "Requires a live printer mid-print to test; the no-op is code-visible but the functional impact (user believing an emergency stop was issued) needs owner judgment on severity and fix priority."
  - test: "Confirm WebcamScreen feed fills the full screen in the single-cam / full-focus case (CR-02)"
    expected: "With one camera configured, the live feed should fill the entire content area. Currently the field lambda is always non-null (FootButtonBar always rendered inside it), so ScreenScaffold weights focus/field 50/50 even when the picker is hidden — the feed is squeezed to ~50% in both portrait and landscape."
    why_human: "The layout regression is code-confirmed but the visual severity needs owner eye on flox. Owner may accept the current layout or may require the fix before proceeding."
---

# Phase 25: Browse Screens Verification Report

**Phase Goal:** Migrate the list/collection screens onto the Phase-23 component classes — FilesScreen, Macros (BookmarkedMacrosScreen + SystemMacrosScreen), ConsoleScreen, and WebcamScreen. Reuse ListRow, the sort-vs-filter control row, and the Field-takeover picker proven on the Spoolman pilot. Per-screen conformance (≥64px targets, fsSp S/M/L, portrait+landscape rotation) folds into each migration rather than a separate sweep.
**Verified:** 2026-06-10T18:00:00Z
**Status:** passed (human_needed items resolved 2026-06-10 — see frontmatter)
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths (from ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| SC-1 | Files, Macros (bookmarked+system), Console, and Webcam rebuilt onto component classes and owner-approved on flox in both orientations | ✓ VERIFIED | 25-UAT.md: all 4 screens approved in portrait+landscape 2026-06-10; UAT frontmatter `status: approved`, owner = Matthew Mees |
| SC-2 | Each screen consumes shared classes (translucent ListRow, sort/filter + Field-takeover, FootButtonBar) — no bespoke list styling | ✓ VERIFIED | FilesScreen uses `ListBlock`/`ListRow` (grep: ScreenScaffold ≥1, gutter=null ≥1); ConsoleScreen uses Views+FootButtonBar (gutter=null ≥1); MacrosScreen uses `MacroFieldMode` + `ListBlock`/`ListRow` (grep confirms, LazyVerticalGrid=0); WebcamScreen uses FootButtonBar (grep confirmed); COMPONENTS.md §8 documents the Console Views exception |
| SC-3 | Per-screen conformance: ≥64px targets (or documented exception), fsSp S/M/L without clipping, correct rotation | ✓ VERIFIED | UAT rows 11 (conformance table) all `approved`; S/M/L cycling and rotation stable for all 4 screens on flox. Code uses `rememberUnitGrid` + `fsSp` throughout. One sub-floor **13sp** modified-date text found (WR-06) — not blocking since owner approved on-device |
| SC-4 | `@Preview` matrices + tokenized strings/icons (registry glyphs) ship with each screen | ✓ VERIFIED | All four preview files exist: FilesPreviews.kt (16 annotations), ConsolePreviews.kt (15), MacrosPreviews.kt (13), WebcamPreviews.kt (15). All four use `DinghyIcons.*` registered tokens. assembleDebug green per summaries. |
| SC-5 | No functional regressions — Files delete/print, macro run, console scrollback, webcam playback intact | ✓ VERIFIED | UAT row 12 (functional smokes) all `approved`; `deleteAllowed` predicate wired in FilesScreen; `MacroInvocation.buildTyped` on Macros dispatch path; `ConsoleFilters.apply` at render only (ConsoleFiltersTest green); Webcam H.264 playback confirmed no-crash on flox |

**Score: 5/5 truths verified**

---

### Deferred Items

None. All five success criteria verified in codebase and on-device.

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt` | Rebuilt Files screen on Phase-23 kit | ✓ VERIFIED | ScreenScaffold, ListBlock/ListRow, gutter=null, deleteAllowed, loadRoot all grep-confirmed |
| `app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt` | Rebuilt field-only Console | ✓ VERIFIED | focus=null, gutter=null, ConsoleFilters.apply at render, FootButtonBar filter toggles all grep-confirmed |
| `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt` | Merged single Macros screen with MacroFieldMode | ✓ VERIFIED | MacroFieldMode sealed class present; Launcher/ParamEntry/ManageMode; buildTyped+paramsKnown wired; LazyVerticalGrid=0 |
| `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt` | Token-conformed Webcam with FootButtonBar | ✓ VERIFIED | FootButtonBar, gutter=null, DisposableEffect, WR-02 fix all grep-confirmed |
| `app/src/main/java/works/mees/dinghy/preview/FilesPreviews.kt` | @Preview matrix for FilesScreen | ✓ VERIFIED | Exists; 16 @Preview/@Nexus7Previews annotations; no holder ref |
| `app/src/main/java/works/mees/dinghy/preview/ConsolePreviews.kt` | @Preview matrix for ConsoleScreen | ✓ VERIFIED | Exists; 15 annotations; stateless overload used |
| `app/src/main/java/works/mees/dinghy/preview/MacrosPreviews.kt` | @Preview matrix for MacrosScreen | ✓ VERIFIED | Exists; 13 annotations; Launcher + ParamEntry modes covered |
| `app/src/main/java/works/mees/dinghy/preview/WebcamPreviews.kt` | @Preview matrix for WebcamScreen | ✓ VERIFIED | Exists; 15 annotations; LocalInspectionMode internal to Media3SurfaceHost/WebcamViewHost |
| `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` | 9 new Phase-25 tokens registered | ✓ VERIFIED | Phase-25 Browse section present; Print, Delete, HideTemps, HideTimelapse, HidePrompts, MacrosLeader, ManageMacros, ExecuteMacro, UnbookmarkedMacro all registered; all in `all` list |
| `.planning/phases/25-browse-screens/25-SPIKE.md` | Per-surface gfxinfo verdict | ✓ VERIFIED | Files: Compose (p90 16.42ms, PASS); Console: Views (p90 73.35ms, ~8× regression, FAIL) |
| `.planning/phases/25-browse-screens/25-UAT.md` | Owner on-device approval | ✓ VERIFIED | status: approved; all 12 sections approved 2026-06-10 on flox |
| `app/src/main/java/works/mees/dinghy/ui/macros/SystemMacrosScreen.kt` | Deleted (absorbed) | ✓ VERIFIED | File does not exist (confirmed with test -f) |
| `app/src/main/java/works/mees/dinghy/ui/macros/MacroExecutionPopup.kt` | Deleted (absorbed) | ✓ VERIFIED | File does not exist (confirmed with test -f) |
| `app/src/main/java/works/mees/dinghy/dev/BrowseSpikeActivity.kt` | Deleted (throwaway harness cleanup) | ✓ VERIFIED | File does not exist; BrowseSpikeActivity count in Manifest = 0 |
| `app/src/test/java/works/mees/dinghy/command/MacroInvocationTest.kt` | buildTyped regression coverage | ✓ VERIFIED | 2 `buildTyped` cases present (forbidden-char rejection + clean typed invocation) |
| `docs/ui_design/COMPONENTS.md` | §8 class-equivalent Views exception for Console | ✓ VERIFIED | §8 present; ConsoleListView documented with Phase-25 measurement provenance |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| FilesScreen Delete button | `deleteAllowed(selectedPath, activePrintFilename, printState)` | enabled gate | ✓ WIRED | `deleteEnabled = deleteAllowed(...)` line 195; wired to button alpha+semantics at lines 654-658 |
| FilesScreen | `FileBrowserHolder.loadRoot` | `LaunchedEffect(holder)` | ✓ WIRED | Lines 212-213; LaunchedEffect(holder) → holder.loadRoot() |
| ConsoleScreen render | `ConsoleFilters.apply(rawLines, ...)` | render-time filter off raw holder state | ✓ WIRED | Line 84: `val filtered = ConsoleFilters.apply(rawLines, ...)`; ConsoleHolder grep returns 0 ConsoleFilters references |
| Macro ParamEntry Execute | `MacroInvocation.buildTyped(...)` | sanitized dispatch | ✓ WIRED | `MacroInvocation.buildTyped` grep ≥1 in BookmarkedMacrosScreen; `paramsKnown` gate present |
| AppShell webcam config read | `container.activeConfig` | `collectAsStateWithLifecycle` | ✓ WIRED | AppShell line 217: `val activeCfg by container.activeConfig.collectAsStateWithLifecycle`; `connectionStore.config` = 0 non-comment occurrences |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|-------------------|--------|
| FilesScreen (live overload) | `holderState` | `FileBrowserHolder.state.collectAsStateWithLifecycle()` | Yes — holder feeds from Moonraker REST + WebSocket | ✓ FLOWING |
| ConsoleScreen (live overload) | `rawLines` | `holder.state.collectAsStateWithLifecycle()` — RAW ConsoleHolder | Yes — live Moonraker gcode_response feed | ✓ FLOWING |
| BookmarkedMacrosScreen (live overload) | `state` | `holder.state.collectAsStateWithLifecycle()` — MacroHolder | Yes — live Moonraker macro/bookmarks | ✓ FLOWING |
| WebcamScreen (live overload) | `vm` | `holder.vm.collectAsStateWithLifecycle()` — WebcamHolder | Yes — live webcam URL from activeConfig | ✓ FLOWING |

---

### Behavioral Spot-Checks

Step 7b: Behavioral spot-checks partially superseded by the owner's on-device UAT gate (25-07). The following code-level checks confirm key behaviors without running the app:

| Behavior | Check | Result | Status |
|----------|-------|--------|--------|
| Delete gate only blocks the printing file | `deleteAllowed` predicate present and wired to Delete button enabled state | Lines 195, 654-658 confirm | ✓ PASS |
| Console filter is render-time only (raw holder untouched) | ConsoleFilters in ConsoleHolder = 0 | ConsoleHolder KDoc confirms raw-only; grep = 0 | ✓ PASS |
| Macros sanitizer on dispatch path | `buildTyped` reachable from Execute button | Lines 338-351 in BookmarkedMacrosScreen | ✓ PASS |
| Spike harness removed from APK | BrowseSpikeActivity absent from Manifest | grep count = 0 | ✓ PASS |

---

### Probe Execution

Step 7c: No conventional `scripts/*/tests/probe-*.sh` probes found. Phase does not declare probes. SKIPPED (no probe infrastructure for this phase).

---

### Requirements Coverage

Phase maps to no REQUIREMENTS.md IDs (per ROADMAP: "Requirements: — (UX migration)"). Verified consistent with REQUIREMENTS.md — Phase 25 is not referenced from any requirement row. No orphaned requirements for this phase.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `ui/files/FilesScreen.kt` | 306 | `onConfirm = { guard = null }` — FilesScreen EStop ConfirmGuard confirms but dispatches nothing (no EMERGENCY_STOP) | BLOCKER (CR-01) | User believes emergency stop was issued; printer is not stopped. Also duplicates AppShell-level FloatingEStop (two overlapping e-stop buttons). |
| `ui/webcam/WebcamScreen.kt` | 171-194 | `field` lambda always non-null — FootButtonBar inside field means ScreenScaffold weights focus/field 50/50 even when `showField=false` | WARNING (CR-02) | Single-cam and full-focus cases render the feed at ~50% screen width instead of full-focus. Feed is squeezed. |
| `ui/console/ConsoleScreen.kt` | 134-138 | `rawLines = List(rawLineCount){null}.map{...}.let{ if(rawLineCount==0) emptyList() else lines }` — constructed list thrown away; rawLineCount parameter is non-functional | WARNING (WR-01) | With rawLineCount>0 and empty filtered lines, shows "Console is quiet" overlay incorrectly. Latent bug — current previews don't expose it. |
| `ui/macros/BookmarkedMacrosScreen.kt` | 335-360 | `execute()` calls `onExecuted()` immediately, removing `MacroParamEntryField` from composition; `DispatchEvent.Failure` collector only lives inside that surface — printer rejections silently dropped after the mode flip | WARNING (WR-02) | Macro execution failures (e.g. MACRO_NUMERIC_RANGE violations) are never surfaced to the user. |
| `ui/files/FilesScreen.kt` | 780 | `text = "Print $fileName?"` — hardcoded string (not stringResource) in SpoolWarningGuard title | WARNING (WR-03) | Should use `R.string.files_confirm_print_title`; defined-but-unused `macros_rejected` resource in strings.xml |
| `ui/files/FilesScreen.kt` | 248, 262, 287, 871-879 | Multiple `?: "file"` fallbacks and hardcoded detail labels ("Time", "Size", "Modified", "Filament", "Layers", "Metadata is unavailable...") in user-facing ConfirmGuard messages | WARNING (WR-03) | Not routed through stringResource; breaks pseudolocale testing |
| `ui/macros/BookmarkedMacrosScreen.kt` | 338, 351 | `"${macro.name} was rejected: ..."` hardcoded rejection toast strings | WARNING (WR-03) | `R.string.macros_rejected` was added this phase and is never referenced — dead resource |
| `ui/macros/BookmarkedMacrosScreen.kt` | 324-326, 343-353 | `buildTyped` numeric path: empty values emit malformed `KEY=`; raw default expressions with spaces emit unquoted (token-splits on macro line) | WARNING (WR-04) | Malformed gcode could reach printer. NumpadPage bypass via default-seed path unguarded. |
| `ui/console/ConsoleScreen.kt` | 205-235 | Icon-only foot buttons with no `contentDescription`; 9 `cd_*` strings added this phase are dead resources (referenced nowhere in Kotlin) | WARNING (WR-05) | TalkBack gets raw ligature text or nothing. |
| `ui/files/FilesScreen.kt` | 719-727 | `fontSize = fsSp(13f, t.fs).sp` — modified-date text at 13sp, below the owner's 15sp metadata floor | WARNING (WR-06) | Violates own floor (ConsoleScreen correctly uses 15sp for metadata). Owner-approved on-device but owner expectation from `[[dinghy-font-sizes-too-small]]` is 15sp floor. |
| `ui/macros/BookmarkedMacrosScreen.kt` | 583-589 | Raw `symbol = "visibility"/"visibility_off"` bypasses DinghyIcons registry and verify_ligatures NEEDED set | WARNING (WR-07) | Font subsetting would silently drop these glyphs. Screen was re-authored this phase — right moment to register. |
| `ui/files/FilesScreen.kt` | 802-804 | Raw `symbol = "warning"` not in DinghyIcons | WARNING (WR-07, informational) | Pre-existing verbatim preservation per 25-03 SUMMARY; flagged for eventual tokenization |
| `ui/shell/AppShell.kt` | 666-667 | `scope.launch { container.macroPrefs.toggleBookmark/setRevealHidden }` — DataStore writes on composition scope `rememberCoroutineScope()` | WARNING (WR-08) | Recurring trap ([[dinghy-compose-write-scope-cancellation]]): write can be cancelled by navigation in the same frame. Should use `AppContainer.writeScope`. |
| `ui/files/FilesScreen.kt` | 698-700 | `Modifier.size(fsSp(40f,...).dp).padding(end=8.dp)` — padding after size carves 8dp from the image box | INFO (IN-01) | Thumbnail renders 32×40 (squashed by Crop). Cosmetic. |
| `ui/files/FilesScreen.kt` | 881-888 | `formatBytes` uses integer division (4,892,000 B → "4 MB"); `formatDate` omits year | INFO (IN-04) | Inaccurate file size display and ambiguous dates for old files. |

**Debt marker gate:** No `TBD`, `FIXME`, or `XXX` markers found in the phase-modified files. Gate PASSES.

---

## Human Verification Required

### 1. CR-01: FilesScreen Emergency-Stop Confirm Is a No-Op

**Test:** While printing, navigate to FilesScreen. Tap the FloatingEStop button in the Focus area (TopStart). Confirm the e-stop dialog. Observe whether the printer halts.
**Expected:** The printer should emergency-stop. The current implementation has `onConfirm = { guard = null }` with no dispatch — confirming the guard closes the dialog but does not stop the printer. AppShell also renders a working FloatingEStop at the same position, so two overlapping e-stop affordances are present on Files while printing, and the local one is a no-op.
**Why human:** Requires a live printer mid-print to verify the functional gap. The no-op is visible in code at `FilesScreen.kt:306`. Owner must decide: (a) remove the Files-local FloatingEStop entirely (the AppShell overlay already covers Files), or (b) thread a `CommandDispatcher` into `FilesScreen` and dispatch `CommandRegistry.emergencyStop` in `onConfirm`, as the `SpoolScreen` precedent does.

### 2. CR-02: WebcamScreen Feed Squeezed to ~50% in Single-Cam / Full-Focus Case

**Test:** Open the Webcam screen with only one camera configured (or in landscape with a landscape-aspect feed). Observe how much of the screen the feed occupies.
**Expected:** In the single-cam case and any case where `showField=false`, the feed should fill the full content area (full-focus). Currently the `field` lambda is always non-null because `FootButtonBar` is rendered inside it unconditionally. `ScreenScaffold` weights focus/field 50/50 whenever both slots are non-null — so the feed is always squeezed to ~50% of the screen.
**Why human:** On-device observation needed to judge visual severity. The code pattern is confirmed at `WebcamScreen.kt:171-194`. The UAT row was owner-approved (a single-cam feed was present on flox), but it is unclear whether the owner saw the full-focus regression or just confirmed the screen opened without crashing. Owner must confirm whether the layout seen during UAT was acceptable or if the fix (restore conditional `field = if (showField) {...} else null`) is required before proceeding.

---

## Gaps Summary

No FAILED truths. All 5 ROADMAP success criteria are VERIFIED in the codebase and confirmed by on-device UAT.

Two code-quality findings from the code review (CR-01 and CR-02) require owner judgment before this verification can be promoted to `passed`:

**CR-01** (BLOCKER in code review, human judgment needed here): The Files-local emergency stop is a silent no-op — confirming the guard does not stop the printer. The AppShell overlay already provides a working e-stop on every screen including Files, so the practical risk is mitigated by the duplicate working control. However, a user tapping the local (non-working) affordance will believe a stop was issued. Owner must decide fix priority and approach.

**CR-02** (code review warning, human judgment needed here): The webcam feed is structurally squeezed to ~50% in the single-cam and full-focus cases. The UAT approved the Webcam screen, but the approval may have been based on a multi-cam setup or may not have noticed the layout regression. Owner must confirm whether the current layout was what was seen and approved, or whether the full-focus regression needs to be fixed.

The 8 code-review warnings (WR-01 through WR-08) and 2 info findings (IN-01, IN-04) are quality issues but do not block the phase goal. They are suitable inputs for a follow-up fix plan or folding into Phase 26.

---

_Verified: 2026-06-10T18:00:00Z_
_Verifier: Claude (gsd-verifier)_
