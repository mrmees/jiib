---
phase: 04-service-shell-settings-print-status-home
plan: 05
subsystem: ui
tags: [compose, splash, recovery, klippy, moonraker, session-control, theming]

# Dependency graph
requires:
  - phase: 04-02
    provides: TopRoute.derive() routes to Splash when klippyState != Ready
  - phase: 04-03
    provides: SessionControl narrow contract (requestReconnectNow/restartFirmware/restartHost), AppContainer.sessionControl
  - phase: 02-02
    provides: pure reduceDiff/reduceSnapshot diff-merge state layer (STATE-01)
provides:
  - PrinterState.klippyStateMessage — nullable Klippy/Moonraker reason text from webhooks.state_message
  - reducer population of klippyStateMessage (set/clear-on-ready/retain-on-absence)
  - ui/screen/SplashScreen.kt — hard-override splash with self-contained recovery actions
affects: [04-06, print-status, shell-routing]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Hard-override surface = gutter-less ScreenScaffold (Field buttons ARE the navigation)"
    - "Recovery actions keyed off a derived RecoveryMode enum from the same state inputs as the reason text"
    - "Reason text prefers server-provided message, falls back to terse never-blank enum label"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/screen/SplashScreen.kt
  modified:
    - app/src/main/java/works/mees/dinghy/state/PrinterState.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt
    - app/src/test/java/works/mees/dinghy/state/PrinterStateReducerTest.kt

key-decisions:
  - "klippyStateMessage cleared to null on recovery to Ready, retained on webhooks-absent diffs (STATE-01 merge, no field-wipe)"
  - "Splash uses GeistMono for the reason line because it often carries a verbatim Klippy/MCU message (tabular)"
  - "RecoveryMode derived: FirstRun (no config) / KlippyDown (shutdown|error AND socket up) / Unreachable (socket down or klippy disconnected) / Connecting (no recovery row yet)"
  - "Unreachable case offers NO firmware/restart (Klippy not reachable, the commands would never land) — only Retry + Edit connection (D-13)"

patterns-established:
  - "Hard-override screens omit the gutter slot entirely; every escape hatch lives in the Field"
  - "All UI reconnect/restart dispatches route through the narrow SessionControl, never a raw session (review #1)"

requirements-completed: [SHELL-05, CONN-01]

# Metrics
duration: 9min
completed: 2026-06-01
---

# Phase 4 Plan 05: Splash / Initializing Surface Summary

**Gutter-less hard-override SplashScreen that surfaces the real Klippy reason (new `klippyStateMessage`) and presents per-state self-contained recovery actions (first-run → Settings, Klippy-down → Retry+firmware+host restart, unreachable → Retry+Edit), all dispatched through the narrow SessionControl.**

## Performance

- **Duration:** ~9 min
- **Completed:** 2026-06-01
- **Tasks:** 2
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments
- Added `PrinterState.klippyStateMessage: String?` (defaulted nullable — backward-compatible shape) carrying the Moonraker/Klippy human reason (review #8).
- Reducer populates it from `webhooks.state_message`: set when provided, cleared to null on recovery to Ready, retained on webhooks-absent diffs (STATE-01, never wipes).
- Built `ui/screen/SplashScreen.kt` — a hard override (gutter omitted, no drawer) whose reason text prefers the real message and whose recovery action set matches the trapped state (D-11/D-12/D-13), every action routed through `container.sessionControl`.

## Task Commits

1. **Task 1 (RED): failing tests for klippyStateMessage** - `b40ba70` (test)
2. **Task 1 (GREEN): populate klippyStateMessage from webhooks.state_message** - `6a4b33d` (feat)
3. **Task 2: SplashScreen hard-override with self-contained recovery** - `acbae4d` (feat)

_Task 1 followed the TDD RED→GREEN cycle (no refactor needed). Task 2 is a fresh file._

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/ui/screen/SplashScreen.kt` - Hard-override splash; reasonText + RecoveryMode derivation; recovery Row dispatching via SessionControl.
- `app/src/main/java/works/mees/dinghy/state/PrinterState.kt` - Added defaulted nullable `klippyStateMessage`.
- `app/src/main/java/works/mees/dinghy/state/PrinterStateReducer.kt` - Captures `webhooks.state_message`; set/clear-on-ready/retain logic in the webhooks block.
- `app/src/test/java/works/mees/dinghy/state/PrinterStateReducerTest.kt` - Three new cases (populate/clear/merge) + a JSON-quote helper.

## Decisions Made
- The reducer's `applyStatus` uses an accumulator (`var s` + `.copy()`), not a single `prev.copy(...)` as the plan's line-references described — adapted the three-way set/clear/retain logic into the existing webhooks block. Behavior identical to the plan's intent.
- Reason line uses GeistMono (verbatim server message is tabular/monospace-friendly), unlike ConfirmGuard which uses Geist for prose.
- Added a `RecoveryMode.Connecting` branch (no recovery row) so the plain "connecting/syncing" path — the mockup's default — does not show a dead recovery button before any trap exists.

## Deviations from Plan

None requiring deviation rules — the only adjustments were adapting the patch to the reducer's actual accumulator shape (the plan's L24-30/`prev.copy(...)` description predated the current code) and rewording a doc comment so the `MoonrakerSession` token does not appear anywhere in the file (satisfies the literal acceptance grep). No behavior change, no scope creep.

## Issues Encountered
None. Both verification gates passed first try after the planned edits: `PrinterStateReducerTest` green, `:app:compileDebugKotlin` green.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- The splash is the recovery surface 04-06's E-stop lands on when it drives `klippy_state → shutdown`; the firmware/host-restart recovery row is in place and SessionControl-wired.
- `SplashScreen` is wired to receive `container`, `hasConfig`, `state`, `onEditConnection` from the shell host (the host wiring/route is owned by the shell-routing plan, not this one).
- Visual/on-device UAT (theme flip recolor, recovery-tap behavior on a shut-down Klipper) is deferred to the phase's end-of-phase human-verify (config: `human_verify_mode: end-of-phase`).

## Self-Check: PASSED

---
*Phase: 04-service-shell-settings-print-status-home*
*Completed: 2026-06-01*
