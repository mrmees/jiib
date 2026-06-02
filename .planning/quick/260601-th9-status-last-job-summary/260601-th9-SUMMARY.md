---
phase: quick-260601-th9
plan: 01
subsystem: ui/printstatus + state + service spine
tags: [status, history, last-job, moonraker, compose, tdd]
requires:
  - PrintMetadata.kt (largestThumbRelPath / thumbnailUrl reuse)
  - PrintMetadataHolder one-shot discipline (mirror)
  - SpineHandle / AppContainer / MoonrakerService wiring pattern (Inc 2)
provides:
  - LastJob data class + parseLastJob (pure)
  - LastJobHolder (one-shot-on-idle history fetch)
  - SpineHandle.lastJob / AppContainer.lastJob
  - state-driven idle Status field (last-job card + file_copy_off empty)
affects:
  - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
tech-stack:
  added: []
  patterns:
    - one-shot-on-idle StateFlow holder (printing→not-printing edge trigger, no poll)
    - shared internal largestThumbRelPath helper (refactor-without-regression)
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/state/PrintHistory.kt
    - app/src/test/java/works/mees/dinghy/state/PrintHistoryParseTest.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/LastJobHolder.kt
    - app/src/test/java/works/mees/dinghy/ui/printstatus/LastJobHolderTest.kt
  modified:
    - docs/moonraker-capabilities.md
    - app/src/main/java/works/mees/dinghy/state/PrintMetadata.kt
    - app/src/main/java/works/mees/dinghy/net/JsonRpc.kt
    - app/src/main/java/works/mees/dinghy/di/SpineHandle.kt
    - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
    - app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
    - app/src/androidTest/java/works/mees/dinghy/ui/ShellPresenceTest.kt
    - app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt
decisions:
  - Filename rendered as basename (substringAfterLast '/') in the card — the directory path is
    redundant clutter in a single-line marquee; full path stays the thumbnail-URL key.
metrics:
  duration: ~25 min
  completed: 2026-06-01
---

# Phase quick-260601-th9: Status Inc 3 — idle last-job card + empty state Summary

Made the Status home FIELD area state-driven by `print_stats.state`: printing → the existing
`StatGrid` (untouched); idle + history → a "last completed job" card (gcode thumbnail + GeistMono
stat list); idle + no history → a centered `file_copy_off` glyph. Data comes from ONE one-shot
`server.history.list?limit=1&order=desc` read fetched on entering a not-printing state (initial idle
connect + every print-complete edge) and never polled — mirroring Inc 2's `PrintMetadataHolder`
discipline. Pure parser + one-shot holder both TDD'd against faithful catalog-shaped fixtures.

## What shipped

**Task 1 — catalog shape + pure parser (`abc1640`)**
- `docs/moonraker-capabilities.md`: new "Print history" section recording the CONFIRMED
  `server.history.list` / `server.history.totals` shape, the full `status` enum, and the
  `count==0`→empty-state + `exists:false`/absent-metadata→degrade rules.
- `PrintMetadata.kt`: factored the inline "largest thumbnail by width" loop into a shared
  `internal fun largestThumbRelPath(JsonObject?)` — `parsePrintMetadata` output is byte-identical
  (Inc 2's `PrintMetadataParseTest` stayed GREEN, the refactor guard).
- `PrintHistory.kt`: pure `LastJob` data class + null-safe `parseLastJob` (count==0 → null;
  `exists:false`/absent metadata → per-field null, never crash).
- `PrintHistoryParseTest`: 6 host cases from faithful raw JSON (full E5 job, count==0, jobs
  absent/empty, deleted-file degrade, garbage-metadata per-field-null, cancelled-status verbatim).

**Task 2 — one-shot holder + spine wiring (`d7fb1de`)**
- `JsonRpcMethods.HISTORY_LIST = "server.history.list"`.
- `LastJobHolder`: tracks `wasPrinting` (null = unseen); fetches ONLY when entering a not-printing
  state — first-ever emission if already idle, OR a printing→idle edge; never while printing, never
  on a repeated idle emission (no poll). Best-effort `runCatching`; null/throw retains the prior
  value; `count==0` → null empty-state.
- `SpineHandle.lastJob` + `AppContainer.lastJob` (flat-mapped off the live handle) +
  `MoonrakerService` builds the holder via `rpc.request(HISTORY_LIST, {limit:1, order:"desc"})`.
- `LastJobHolderTest`: 6 host cases (initial-idle fetch-once, printing→complete refresh-once,
  repeated-idle no-poll, while-printing zero fetches, count==0→null, null-fetch prior-value-retained).

**Task 3 — state-driven field UI (`c8584ed`)**
- `PrintStatusScreen` field lambda branches printing→`StatGrid` / idle+history→`LastJobCard` /
  idle+none→`LastJobEmpty`; the estop-failure toast stays reachable in all branches.
- `LastJobCard`: clickable token surface — left gcode thumbnail (Coil 3 `AsyncImage`, shown only
  when `exists && httpBase blank-checked && thumb known`, else a quiet `image` glyph), right a
  GeistMono icon-led stat list (status/elapsed/est-vs-actual/filament/total), "—" for absent fields.
- `LastJobEmpty`: centered `file_copy_off` sized by ratio of the field's smaller dimension (no px).
- Both idle surfaces are clickable nav seams with no-op `// TODO(nav):` onClicks — destinations
  NOT built. Token-pure (zero raw `Color(` literals).

## Verification

- `:app:testReleaseUnitTest --tests *PrintHistoryParseTest --tests *PrintMetadataParseTest` → GREEN
  (new parser passes; Inc 2 refactor un-regressed).
- `:app:compileReleaseKotlin :app:testReleaseUnitTest --tests *LastJobHolderTest` → GREEN.
- `:app:compileReleaseKotlin :app:testReleaseUnitTest` (FULL release unit suite) → GREEN.
- grep: `HISTORY_LIST` present; `lastJob` wired in SpineHandle/AppContainer/MoonrakerService;
  zero `Color(` literals in `PrintStatusScreen.kt`; `TODO(nav)` seams present on both idle surfaces.

## Deviations from Plan

**1. [Rule 3 - Blocking] Two test `SpineHandle(...)` constructors needed the new `lastJob` field**
- **Found during:** Task 2 (adding the required `SpineHandle.lastJob` field).
- **Issue:** `ShellPresenceTest.kt` (androidTest) and `AppContainerTest.kt` (test) construct
  `SpineHandle` by named args — adding a non-default field broke their compilation.
- **Fix:** added `lastJob = MutableStateFlow(null)` to both constructors (mirrors the existing
  `metadata = MutableStateFlow(null)` line). No behavior change to either test.
- **Files modified:** `app/src/androidTest/.../ShellPresenceTest.kt`, `app/src/test/.../AppContainerTest.kt`
- **Commit:** `d7fb1de`

Otherwise the plan executed as written. (Minor: the card filename renders as basename rather than the
full path — a readability call, logged under decisions; the full path remains the thumbnail key.)

## Known Stubs

The two idle surfaces (`LastJobCard`, `LastJobEmpty`) have intentional no-op `// TODO(nav):` onClicks
— this is BY DESIGN per the plan (one-line nav seams; destination screens — past-print detail (B) and
file browser (C) — are deliberately NOT built this increment). Not a blocking stub; the increment's
goal (a glanceable idle "what did I just print?" summary) is fully achieved with the click seams stubbed.

## On-device UAT (Matthew-driven — NOT run here)

Stopped at compile + unit-test green per the plan. To verify on flox + live Ender 5 Plus: idle Status
with print history shows the card + thumbnail; a freshly-completed print refreshes it; a printer with
no history shows `file_copy_off`; while printing the field is still `StatGrid`.

## Self-Check: PASSED

- Files exist: PrintHistory.kt, PrintHistoryParseTest.kt, LastJobHolder.kt, LastJobHolderTest.kt — all FOUND.
- Commits exist: abc1640, d7fb1de, c8584ed — all FOUND in `git log`.
