---
phase: 25-browse-screens
plan: 02
subsystem: ui
tags: [icons, material-symbols, DinghyIcons, tokenization, drift-guard, browse-screens]

# Dependency graph
requires:
  - phase: 25-browse-screens
    provides: Phase-25 context, browse-screen migration scope (Files/Console/Macros)
  - phase: 24-conformance-sweep
    provides: DinghyIcons registry + verify_ligatures.py drift-guard baseline
provides:
  - 9 owner-sanctioned DinghyIcon tokens for all browse screens (Files + Console + Macros)
  - 25-ICONMAP.md — canonical glyph-to-token mapping for Phase 25
  - verify_ligatures.py NEEDED set extended to 85 ligatures (missing: [])
  - cd_* content-description strings for all 9 new tokens
affects: [25-03-files-screen, 25-04-console-screen, 25-05-macros-screen, future icon phases]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Owner-sanctioned out-of-bucket glyphs: record as owner-approved in ICONMAP with date; do not treat bucket as immutable"
    - "Phase-25 token section in DinghyIcons.kt grouped under // --- Browse screens (Phase 25) ---"

key-files:
  created:
    - .planning/phases/25-browse-screens/25-ICONMAP.md
  modified:
    - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
    - app/src/main/res/values/strings.xml
    - tools/verify_ligatures.py

key-decisions:
  - "play_arrow for Execute macro foot button: owner-sanctioned new entry (not in img/material-icon-bucket.json), approved 2026-06-10"
  - "radio_button_unchecked for Unbookmarked trailing affordance: owner-approved 2026-06-10 — existing code ligature explicitly sanctioned outside bucket"
  - "bookmark_manager chosen over tune for ManageMacros: more semantically precise, avoids reusing LauncherCalibration token across unrelated functions"
  - "video_camera_back for HideTimelapse: bucket assignment 'webcam menu icon, also show timelapse in console' — same glyph, distinct alternate handle from LauncherWebcam (videocam)"
  - "Sort-direction arrow NOT tokenized: SortFilterControlRow hardcodes arrow_upward/arrow_downward internally (owner-established Phase-23 component, not in scope)"

patterns-established:
  - "D-21 gate: any glyph not in img/material-icon-bucket.json requires explicit owner approval recorded in 25-ICONMAP.md with date"
  - "Browse token section: all Phase-25 registrations grouped under // --- Browse screens (Phase 25) --- comment block"

requirements-completed: []

# Metrics
duration: 25min
completed: 2026-06-10
---

# Phase 25 Plan 02: Browse-Screen Icon Registry Summary

**9 owner-sanctioned DinghyIcon tokens registered for Files/Console/Macros screens — verify_ligatures.py 85/85 NEEDED resolved, DinghyIconsTest green, D-21 gate honored throughout**

## Performance

- **Duration:** ~25 min (continuation agent)
- **Started:** 2026-06-10T13:15:00Z
- **Completed:** 2026-06-10T13:39:20Z
- **Tasks:** 2 (Task 1: ICONMAP finalization; Task 2: registration + verification)
- **Files modified:** 4

## Accomplishments

- Finalized 25-ICONMAP.md with owner-resolved D-21 gaps: `play_arrow` (ExecuteMacro) and `radio_button_unchecked` (UnbookmarkedMacro) both owner-approved 2026-06-10
- Registered 9 new DinghyIcon tokens in a Phase-25 Browse Screens section; all added to `DinghyIcons.all`
- Extended `verify_ligatures.py` NEEDED set to 85 ligatures; gate exits 0 (`missing: []`)
- Added 9 `cd_*` content-description strings in strings.xml; screen-label strings reserved for Wave-2 plans (25-03/04/05)

## Registered Tokens

| Token | Ligature | Source |
|-------|----------|--------|
| `DinghyIcons.Print` | `print` | bucket: "start a print" |
| `DinghyIcons.Delete` | `delete` | bucket entry |
| `DinghyIcons.HideTemps` | `mode_heat_off` | bucket: "hide temperature messages in console" |
| `DinghyIcons.HideTimelapse` | `video_camera_back` | bucket: "webcam menu icon, also show timelapse in console" |
| `DinghyIcons.HidePrompts` | `chat_error` | bucket: "hide macro prompt messages in console" |
| `DinghyIcons.MacrosLeader` | `code` | bucket: "macros" |
| `DinghyIcons.ManageMacros` | `bookmark_manager` | bucket entry |
| `DinghyIcons.ExecuteMacro` | `play_arrow` | owner-approved 2026-06-10 (new entry, not in bucket) |
| `DinghyIcons.UnbookmarkedMacro` | `radio_button_unchecked` | owner-approved 2026-06-10 (existing code ligature, outside bucket) |

**Reused (no re-registration):** Back, CalendarClock, CheckCircle, Inventory, QrCode

## Task Commits

1. **Task 1: Finalize 25-ICONMAP.md with owner-resolved glyph assignments** — `593dbb8` (docs)
2. **Task 2: Register browse-screen icon tokens in DinghyIcons + drift guard** — `b240a3c` (feat)

## Files Created/Modified

- `.planning/phases/25-browse-screens/25-ICONMAP.md` — canonical glyph-to-token map; all 14 browse-screen functions resolved; D-21 gaps recorded with owner-approval provenance
- `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` — 9 new tokens in Phase-25 section; `DinghyIcons.all` updated (+9 entries, now 74 total)
- `app/src/main/res/values/strings.xml` — 9 new `cd_*` content-description strings in Phase-25 browse section
- `tools/verify_ligatures.py` — NEEDED extended from 76 to 85 ligatures; Phase-25 comment block added

## Decisions Made

- `play_arrow` for Execute macro foot button: escalated per D-21 (not in bucket); owner approved it as a new entry rather than picking from existing bucket entries that would duplicate glyphs on-screen (bolt=LauncherMacros, code=MacrosLeader)
- `radio_button_unchecked` for Unbookmarked trailing affordance: escalated because it was not in bucket; owner explicitly sanctioned the existing code's ligature
- `bookmark_manager` over `tune` for ManageMacros: bucket has both; `tune` already serves LauncherCalibration — reusing it across unrelated functions would be semantically misleading; `bookmark_manager` is more precise
- Sort-direction arrow excluded from scope: the shipped `SortFilterControlRow` component owns those glyphs internally (Phase-23, owner-established)

## Deviations from Plan

None — plan executed exactly as written. The D-21 checkpoint gate was planned; owner provided both gap resolutions in the same session continuation.

## Issues Encountered

None.

## Next Phase Readiness

- Wave-2 screen plans (25-03 FilesScreen, 25-04 ConsoleScreen, 25-05 MacrosScreen) can now consume registered tokens without touching DinghyIcons.kt — tokenized-first foundation is complete
- Each Wave-2 plan appends its own screen-label strings to strings.xml (cd_* strings are already present here)
- verify_ligatures.py gate is green; any future ligature addition must pass this gate before merge

---
*Phase: 25-browse-screens*
*Completed: 2026-06-10*
