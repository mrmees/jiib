---
phase: 24-navigation-spine
plan: 02
subsystem: icon-registry
tags: [icons, DinghyIcons, tokens, ligatures, owner-confirmed]
dependency_graph:
  requires: []
  provides: [LauncherWebcam, FootPreheat, FootSystem tokens registered]
  affects: [24-04 morphing-root idle surface]
tech_stack:
  added: []
  patterns: [DinghyIcon(IconRef.Ligature), alternate token, verify_ligatures.py NEEDED]
key_files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
    - app/src/main/res/values/strings.xml
    - tools/verify_ligatures.py
decisions:
  - "Owner chose videocam for idle Webcam row (LauncherWebcam)"
  - "Owner chose chair_fireplace for idle Preheat foot button (FootPreheat)"
  - "Owner chose bottom_panel_open for idle System foot button (FootSystem, D-09 neutral)"
metrics:
  duration: "~8 minutes"
  completed: "2026-06-10"
  tasks_completed: 2
  files_modified: 3
---

# Phase 24 Plan 02: Idle Glyph Registry (Webcam / Preheat / System) Summary

Owner-confirmed `LauncherWebcam`, `FootPreheat`, and `FootSystem` tokens registered in DinghyIcons,
font-verified (76 NEEDED, missing: []), and drift-guarded — the morphing-root idle surface in 24-04
can now consume all three tokens with no raw ligature strings at the call site.

## Task Execution

### Task 1: RESOLVED (Pre-collected by orchestrator)

The three glyph decisions were collected by the orchestrator BEFORE dispatch, per the HARD OWNER LAW
([[dinghy-never-pick-icons-ask]]). Zero glyphs were chosen by Claude.

**Owner's exact glyph choices:**

| Surface | Token | Ligature | Verified in v2.944 |
|---------|-------|----------|--------------------|
| Idle-list Webcam row | `LauncherWebcam` | `videocam` | YES |
| Idle Preheat foot button | `FootPreheat` | `chair_fireplace` | YES |
| Idle System foot button (D-09, neutral) | `FootSystem` | `bottom_panel_open` | YES |

All three ligatures were pre-verified by the orchestrator using `tools/verify_ligatures.py`
machinery against the bundled v2.944 Material Symbols ttf before dispatch.

### Task 2: Register tokens + font-verify + drift-guard — COMPLETE

**DinghyIcons.kt changes:**
- Added `LauncherWebcam = DinghyIcon(IconRef.Ligature("videocam"), alternate = "launcher_webcam")` to
  the Launcher* block, with a comment noting owner-confirmation and the FIX-5 constraint (Webcam
  placeholder→LauncherWebcam swap is owned by 24-04, not this plan).
- Added `FootPreheat = DinghyIcon(IconRef.Ligature("chair_fireplace"), alternate = "home_foot_preheat")`
  and `FootSystem = DinghyIcon(IconRef.Ligature("bottom_panel_open"), alternate = "home_foot_system")`
  in a new `// --- Morphing-root idle foot-bar glyphs (24-02)` block.
- All three added to `DinghyIcons.all` drift-guard list.

**strings.xml changes:**
- `cd_launcher_webcam` = "Webcam"
- `home_foot_preheat` = "Preheat"
- `home_foot_system` = "System"

**verify_ligatures.py changes:**
- Three new ligatures added to NEEDED: `videocam`, `chair_fireplace`, `bottom_panel_open`.
- Gate result: 76 needed (was 73), 3953 ligatures in font, missing: []

**DinghyIconsTest result:** BUILD SUCCESSFUL, all 4 tests GREEN (uniqueness, alternate, iconRef
uniqueness, drawable-only-keepers checks all pass with the three new ligature-backed tokens).

**Commit:** `aea4618` — feat(24-02): register LauncherWebcam/FootPreheat/FootSystem owner-confirmed idle glyphs

## Acceptance Criteria Check

| Criterion | Status |
|-----------|--------|
| DinghyIcons.kt defines LauncherWebcam + FootPreheat + FootSystem | PASS |
| All three present in DinghyIcons.all drift-guard list | PASS |
| verify_ligatures.py exits 0 with missing: [] | PASS (76 needed, 0 missing) |
| DinghyIconsTest GREEN | PASS (BUILD SUCCESSFUL) |
| strings.xml contains cd_launcher_webcam, home_foot_preheat, home_foot_system | PASS |
| No glyph chosen by Claude — all match owner's Task-1 decision | PASS |

## Deviations from Plan

None — plan executed exactly as written. Task 1 was pre-resolved by the orchestrator per the HARD
OWNER LAW; Task 2 was executed per spec with zero auto-fix needed.

## FIX-5 Compliance

Per the plan's FIX-5 note: `HomeAction.kt` was NOT touched. The Webcam placeholder→`DinghyIcons.LauncherWebcam`
swap in the idle action list is owned by 24-04 (Wave 2), which depends on both 24-01 and 24-02 and is the
first plan that can legally reference symbols from both Wave-0 plans.

## Self-Check: PASSED

- `aea4618` exists in git log: confirmed
- `DinghyIcons.kt` contains `LauncherWebcam`: grep confirmed
- `verify_ligatures.py` exits 0: confirmed (76 needed, missing: [])
- `DinghyIconsTest` GREEN: confirmed (BUILD SUCCESSFUL)
- `strings.xml` contains all three new string keys: confirmed
