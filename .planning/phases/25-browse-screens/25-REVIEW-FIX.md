---
phase: 25-browse-screens
fixed_at: 2026-06-10T17:04:17Z
review_path: .planning/phases/25-browse-screens/25-REVIEW.md
iteration: 1
findings_in_scope: 10
fixed: 10
skipped: 0
status: all_fixed
---

# Phase 25: Code Review Fix Report

**Fixed at:** 2026-06-10T17:04:17Z
**Source review:** .planning/phases/25-browse-screens/25-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 10 (2 Critical + 8 Warning; fix_scope = critical_warning — IN-01..IN-05 not addressed)
- Fixed: 10
- Skipped: 0

**Verification:** full host unit suite (`:app:testDebugUnitTest`) run Windows-side after all fixes —
**BUILD SUCCESSFUL** (includes the 3 new WR-04 regression tests, the DinghyIconsTest registry guards,
and MacrosPseudolocaleSpotCheck). `tools/verify_ligatures.py` re-run after WR-07: 87 needed, missing: [].
All compiler warnings in the run are pre-existing.

## Fixed Issues

### CR-01: FilesScreen emergency-stop confirm is a no-op — dead safety control

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt`, `app/src/main/java/works/mees/dinghy/preview/FilesPreviews.kt`, `app/src/main/res/values/strings.xml`
**Commit:** cd96fe2
**Applied fix:** Per the OWNER DECISION (2026-06-10): removed the Files-local `FloatingEStop`, the
`FileGuard.EStop` confirm guard, the `onEmergencyStop` plumbing through all three composables, the
`FilesScreenState.isPrinting` field (its only consumer), the now-meaningless printing preview fixture
(`filesFileSelectedPrinting` + its `@Nexus7Previews`), and the three `files_estop_guard_*` strings.
The AppShell-level e-stop overlay (Phase 24 FIX-1) already covers Files; no dispatcher was wired.

### CR-02: WebcamScreen Field slot is now always non-null — full-focus layout regressed to a half-screen feed

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt`, `app/src/main/java/works/mees/dinghy/preview/WebcamPreviews.kt`
**Commit:** 39343e9
**Applied fix:** Per the OWNER DECISION: the field slot is now `null` when `!showField`, so
`ScreenScaffold` gives the feed the full stage (single cam in any orientation; landscape feed on
landscape device). The Back `FootButtonBar` was extracted into a shared `WebcamBackBar` composable —
hosted at the foot of the picker Field when shown, or in the full-width gutter strip (the pre-25-06
placement) in full-focus mode, so Back stays reachable. File KDoc and the `WebcamSingleCam` preview
KDoc updated to match.

### WR-01: Console stateless seam builds and discards `rawLines`; `rawLineCount` is non-functional

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt`
**Commit:** 36193f4
**Applied fix:** `ConsoleContent` parameter changed from `rawLines: List<ConsoleLine>` to
`rawLineCount: Int` (it only consumed emptiness). Live overload passes `rawLines.size`; stateless
overload passes the value straight through; the dead fake-list construction is deleted. "Raw lines
exist but all filtered out" no longer shows the fresh-connect `EmptyConsole` overlay.

### WR-02: Macro dispatch failure / running feedback is unreachable — printer rejections silently dropped

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt`
**Commit:** aae945a
**Applied fix:** Hoisted the `DispatchEvent.Failure` collector + error `SeverityToast` to
`MacrosContent` (screen level, keyed on the `macro_` busy-key prefix), so a printer-side rejection
arriving after the ParamEntry → Launcher mode flip is still shown (auto-dismisses after 6 s so it never
permanently covers the FootButtonBar). The unreachable in-field collector was removed; the synchronous
local-rejection toast and the re-entry `running` Info toast remain in `MacroParamEntryField`.

### WR-03: New-code hardcoded English literals — including a defined-but-unused `macros_rejected` resource

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt`, `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt`, `app/src/main/res/values/strings.xml`
**Commit:** 60e3210
**Applied fix:** SpoolWarningGuard title now uses `files_confirm_print_title`; both macro rejection
toast sites consume the previously-dead `macros_rejected` via `context.getString` (non-composable
contexts); the three `?: "file"` fallbacks use new `files_fallback_filename`; `selectedFileDetails`
is now `@Composable` and builds from new `files_detail_time/size/modified/filament/layers/unavailable`
resources.

### WR-04: `buildTyped` numeric path defence gap — empty values emit malformed `KEY=`; raw default expressions are emitted unquoted

**Files modified:** `app/src/main/java/works/mees/dinghy/command/MacroInvocation.kt`, `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt`, `app/src/test/java/works/mees/dinghy/command/MacroInvocationTest.kt`
**Commit:** 2f87374
**Applied fix:** `buildTyped` now REJECTS (throws `MacroParamRejected`) any `isNumeric` value that is
not a plain numeric literal — non-empty, no whitespace, parses as a double. The explicit no-whitespace
check goes beyond the suggested `toDoubleOrNull()`-only check because `Double.parseDouble` trims
surrounding whitespace (`" 210"` would parse yet still token-split when emitted verbatim). The screen
omits blank-valued params from the line so the macro's own Jinja default applies. Three regression
tests added (empty numeric, Jinja-expression numeric, whitespace-padded numeric) — all green.

### WR-05: Icon-only foot buttons expose no contentDescription; the new `cd_*` strings are dead

**Files modified:** `app/src/main/java/works/mees/dinghy/designsystem/control/OutlinedControl.kt`, `app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt`, `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt`, `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt`, `app/src/main/res/values/strings.xml`
**Commit:** e47c0b9
**Applied fix:** Both `OutlinedControl` overloads gain `contentDescription: String? = null`; a non-null
value overrides the raw-ligature semantics on the `MaterialSymbol` (the DinghyIconView Amendment-1
precedent); null is deliberately a no-op so pre-existing call sites (FloatingEStop, SortFilterControlRow,
SpoolScreen) are unaffected. Wired: Console's three filter toggles (`cd_console_hide_*`) **plus
`semantics { selected = … }` toggle-state** and Back (`common_back`); Files Print/Delete
(`cd_files_print`/`cd_files_delete`); Macros Manage/Execute (`cd_macros_manage`/`cd_macros_execute`);
the ManageMode bookmark `DinghyIconView` now speaks its state (`cd_macros_unbookmarked` + new
`cd_macros_bookmarked`). NOTE: `cd_macros_leader` remains unreferenced — the `MacrosLeader` icon token
itself has no call site in the merged screen (no icon-only site exists for it to label).

### WR-06: 13sp metadata text below the 15sp floor

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt`
**Commit:** 2c0727c
**Applied fix:** File-row modified-date trailing text raised `fsSp(13f → 15f)` with the standard
metadata-floor annotation. (Reviewer's "re-check the fs=L overflow preview" remains a preview-pass
item — flagged for UAT.)

### WR-07: Raw ligature strings bypass the icon registry and the subset gate

**Files modified:** `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt`, `tools/verify_ligatures.py`, `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt`, `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt`
**Commit:** 24dc943
**Applied fix:** Registered `Visibility`/`VisibilityOff`/`Warning` tokens in `DinghyIcons` + `all`
(same pre-existing glyphs — NO new icon choices, per [[dinghy-never-pick-icons-ask]] this is a
registry-routing conformance fix only); added `"visibility", "visibility_off"` to the
`verify_ligatures.py` NEEDED set (`"warning"` already present via the D-08 list); switched the Macros
Show-hidden toggle to the `icon =` registry overload and the SpoolWarningGuard glyph to
`DinghyIconView(DinghyIcons.Warning)`. Gate re-run: 87 needed, missing: [].

### WR-08: Macro bookmark DataStore writes still ride the AppShell composition scope

**Files modified:** `app/src/main/java/works/mees/dinghy/di/AppContainer.kt`, `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt`
**Commit:** cde6725
**Applied fix:** Added `AppContainer.toggleMacroBookmark(name)` / `setMacroRevealHidden(reveal)` intent
methods launching on the process-lifetime `writeScope` (the `setActiveProfile` precedent), and wired
AppShell's Macros destination to them via method references — no `scope.launch` remains in AppShell
([[dinghy-compose-write-scope-cancellation]] guard).

## Skipped Issues

None — all in-scope findings were fixed.

---

_Fixed: 2026-06-10T17:04:17Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
