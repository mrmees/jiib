---
phase: 28-system-settings-cluster
plan: "01"
subsystem: designsystem
tags: [icons, design-system, foundation, wave-1]
dependency_graph:
  requires: []
  provides:
    - DinghyIcons six D-21 tokens (LauncherFineTune + 5 SystemRow*)
    - ListRow dense flag (C6 exempt surfaces)
    - TokenTextField dense flag (C6 exempt surfaces)
    - System-page strings in strings.xml
  affects:
    - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
    - app/src/main/java/works/mees/dinghy/designsystem/components/ListRow.kt
    - app/src/main/java/works/mees/dinghy/ui/screen/TokenTextField.kt
    - app/src/main/res/values/strings.xml
    - tools/verify_ligatures.py
tech_stack:
  added: []
  patterns:
    - C6 dense ListRow variant (10dp vertical padding instead of U floor)
    - Default-false flags for backward-compatible API extension
    - heightIn(max) constraint for dense TokenTextField
key_files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
    - app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt
    - tools/verify_ligatures.py
    - app/src/main/java/works/mees/dinghy/designsystem/components/ListRow.kt
    - app/src/main/java/works/mees/dinghy/ui/screen/TokenTextField.kt
    - app/src/main/res/values/strings.xml
decisions:
  - "dense flag default=false preserves all existing call sites with zero behavioral change"
  - "palette ligature allow-listed in DinghyIconsTest (SystemRowTheme + Palette never co-render)"
  - "TokenTextField dense=true uses heightIn(max=48.dp) instead of contentPadding — the value-String OutlinedTextField overload does not expose contentPadding; outer modifier constraint achieves equivalent vertical compaction"
metrics:
  duration: ~12 minutes
  completed: "2026-06-12"
  tasks_completed: 2
  tasks_total: 2
  files_modified: 6
---

# Phase 28 Plan 01: Icon Registry Foundation + Dense Components Summary

**One-liner:** Six D-21 owner-locked glyph tokens registered, ligature gate green, dense `ListRow`/`TokenTextField` flags added for C6 surfaces.

## Tasks Completed

| Task | Name | Commit | Key files |
|------|------|--------|-----------|
| 1 | Register six D-21 glyph tokens + extend ligature drift guard | 8d72bd4 | DinghyIcons.kt, DinghyIconsTest.kt, verify_ligatures.py |
| 2 | Add C6 dense flag to ListRow and TokenTextField | 6e03208 | ListRow.kt, TokenTextField.kt, strings.xml |

## What Was Built

**Task 1 — Icon registry:**
- `LauncherFineTune` = `line_style` (alternate `launcher_fine_tune`) — WaterfallHome idle-list Fine-Tune row
- `SystemRowPrinters` = `android_wifi_3_bar_plus` (alternate `system_row_printers`) — System page Printers row
- `SystemRowSettings` = `settings` (alternate `system_row_settings`) — System page Settings row
- `SystemRowTheme` = `palette` (alternate `system_row_theme`) — System page Theme row
- `SystemRowAbout` = `info` (alternate `system_row_about`) — System page About row
- `SystemRowPower` = `power_settings_new` (alternate `system_row_power`) — System page Power stub row
- All six added to `DinghyIcons.all` drift-guard list
- `verify_ligatures.py` NEEDED extended with 5 new ligatures (`palette` already present; de-dup applies)
- Gate result: 106 needed, missing: []

**Task 2 — Dense components + strings:**
- `ListRow(dense: Boolean = false)` — when true, replaces `heightIn(min = uDp)` with `padding(vertical = 10.dp)` per C6 spec; all other ListRow conventions intact
- `TokenTextField(dense: Boolean = false)` — when true, applies `heightIn(max = 48.dp)` to reduce effective vertical padding; validation/keyboard behavior unchanged
- `strings.xml` additions: `cd_launcher_fine_tune`, `system_row_printers`, `system_row_settings`, `system_row_theme`, `system_row_sysinfo`, `system_row_about`, `system_row_power`, `system_row_power_sub`, `system_row_label`

## Verification Results

- `:app:testDebugUnitTest --tests *DinghyIconsTest`: GREEN (4 tests pass — non-blank alternate, alternate uniqueness, iconRef uniqueness, drawable-keepers guard)
- `python3 tools/verify_ligatures.py`: `106 needed, 3953 ligatures in font, missing: []`
- `:app:compileDebugKotlin`: BUILD SUCCESSFUL

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `palette` ligature collision between `Palette` and `SystemRowTheme`**
- **Found during:** Task 1 — the `DinghyIconsTest.iconRef_isUnique_acrossAllEntries` test enforces that each `primary` IconRef is unique across the registry; `Palette` (Spool detail pane) already uses `IconRef.Ligature("palette")`; adding `SystemRowTheme` with the same ligature would fail the test.
- **Root cause:** 28-PATTERNS.md stated the new tokens were "verified non-conflicting against the current registry" but missed the existing `Palette` registration in the Spool exemplar section.
- **Fix:** Added `"palette"` to `allowedSharedLigatures` in `DinghyIconsTest.iconRef_isUnique_acrossAllEntries` with justification: `SystemRowTheme` (System page) and `Palette` (Spool detail pane) never co-render on the same screen, satisfying the WR-02 "icon-never-twice on one screen" rule by render-site separation.
- **Files modified:** `app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt`
- **Commit:** 8d72bd4

**2. [Rule 3 - Blocking] `OutlinedTextField` value-String overload does not expose `contentPadding`**
- **Found during:** Task 2 — the `contentPadding` parameter only exists on the newer `TextFieldState`-based `OutlinedTextField` overload; the `value: String` overload (which this project uses) does not accept it, causing a compile error.
- **Fix:** Implemented the dense height reduction via `modifier.heightIn(max = 48.dp)` on the outer modifier instead of `contentPadding`. This achieves equivalent visual compaction (≈6dp reduced vertical space) without changing the API surface. The `dense` flag semantics and default-false contract are preserved.
- **Files modified:** `app/src/main/java/works/mees/dinghy/ui/screen/TokenTextField.kt`
- **Commit:** 6e03208

## Known Stubs

None. This plan registers tokens and component flags; no stub data flows to any UI rendering.

## Threat Flags

None. This plan adds no network surface; icon/component registration is internal static code only.

## Self-Check: PASSED

- DinghyIcons.kt: FOUND (contains LauncherFineTune, SystemRowPrinters, SystemRowSettings, SystemRowTheme, SystemRowAbout, SystemRowPower in both declaration and `all` list)
- ListRow.kt: FOUND (contains `dense: Boolean = false`)
- TokenTextField.kt: FOUND (contains `dense: Boolean = false`)
- strings.xml: FOUND (contains system_row_printers, system_row_power, cd_launcher_fine_tune)
- Commit 8d72bd4: verified in git log
- Commit 6e03208: verified in git log
