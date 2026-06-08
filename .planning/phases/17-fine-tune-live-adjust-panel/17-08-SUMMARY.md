---
phase: 17-fine-tune-live-adjust-panel
plan: 08
subsystem: ui-shell
tags: [fine-tune, navigation, shell-nav, same-dest-reentry, gap-closure, uat]
requires:
  - "ShellNavState dest/backStack + per-dest entry resets (13-05 nav hoist)"
  - "Fine-Tune Hub/group sub-nav (fineTuneGroup) (17-05/17-06)"
provides:
  - "navigateTo() runs per-dest entry resets on same-dest re-selection via applyEntryReset(target)"
  - "Re-entering Fine-Tune (or Calibration / Macros) from its own drawer tile always lands on its entry surface, never a stale sub-page"
affects:
  - "Every drawer-tile re-entry into Fine-Tune (Hub), Calibration (hub), and Macros (bookmarked launcher, no popup)"
tech-stack:
  added: []
  patterns:
    - "Per-dest ENTRY-RESET extracted to applyEntryReset(target); run on BOTH dest-change AND same-dest re-selection, before the no-push early-return"
key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt
decisions:
  - "Same-dest re-selection runs applyEntryReset(target) then returns WITHOUT touching backStack (re-selecting the current dest is not a new back entry — only the sub-nav reset is the intended effect)"
  - "One-spot fix (the shared applyEntryReset helper) closes Fine-Tune + Calibration + Macros simultaneously — REVIEW #6 holds for EVERY entry path"
  - "Manual on-device eyeball (flox) is the authoritative on-device gate; instrumented FineTuneNavTest swipe-gesture harness defect deferred to a separate test-hardening follow-up"
requirements-completed: [TUNE-01]
metrics:
  duration: ~10min
  completed: 2026-06-08
  tasks: 2
  files: 1
---

# Phase 17 Plan 08: Same-Dest Re-Entry Reset Summary

**`ShellNavState.navigateTo()` now runs the per-dest entry resets on a same-dest re-selection (via a shared `applyEntryReset(target)` helper), so re-entering Fine-Tune / Calibration / Macros from its own drawer tile always lands on the entry surface instead of a stale sub-page — UAT Check 8 / REVIEW #6 closed.**

## Performance

- **Duration:** ~10 min (continuation close-out after the blocking on-device checkpoint resolved)
- **Completed:** 2026-06-08
- **Tasks:** 2 (1 production + 1 on-device human-verify checkpoint)
- **Files modified:** 1

## Accomplishments
- Extracted the three per-dest ENTRY-RESET side-effects (Macros: `macroShowSystem=false` + `macroPopupFor=null`; Calibration: `calibrationRoutine=null`; Fine-Tune: `fineTuneGroup=null`) into a single private `applyEntryReset(target)` helper.
- Rewrote `navigateTo()` so the resets fire on EVERY entry into a destination — including a same-dest re-selection — instead of an unconditional early-return that skipped them.
- One-spot change closes the same-dest re-entry hole for Fine-Tune AND the identical latent Calibration + Macros holes.
- Dest-change `backStack` semantics unchanged (PrintStatus clears, else push the outgoing dest); same-dest re-selection still does NOT push a back entry (only the sub-nav reset is the intended effect).
- Host unit suite GREEN, androidTest sourceset compiles (Task 1 gate, verified at execution).
- Owner verified the fix manually on flox (live debug build): swipe up → Fine-Tune tile → Motion → swipe up → Fine-Tune tile again → lands on the **Hub**, not the stale Motion page. Owner: **"approved"**.

## Task Commits

1. **Task 1: Run per-dest entry resets on same-dest re-selection in `ShellNavState.navigateTo()`** — `2bebae0` (fix)
2. **Task 2: On-device flox verify (manual re-entry lands on Hub)** — no production commit (`checkpoint:human-verify`); owner-approved manually on flox

**Plan metadata:** `docs(17-08): complete plan` (this SUMMARY + STATE/ROADMAP tracking)

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt` — `navigateTo()` now calls a new private `applyEntryReset(target)` on a same-dest re-selection (before the no-push return) and on the dest-change path (before `dest = target`); the three per-dest reset branches moved verbatim into the helper.

## Decisions Made
- **Same-dest re-selection does not touch `backStack`** — re-selecting the current dest is not a new back entry; the only intended effect is resetting that dest's sub-nav so it returns to its entry surface.
- **One-spot fix via the shared helper** — closing the hole at the single `applyEntryReset` choke point makes "always opens the entry surface" hold for Fine-Tune, Calibration, AND Macros without three separate edits.
- **Manual on-device eyeball is the authoritative gate** — the owner chose the manual flox re-entry walk as the on-device proof and deferred the instrumented-test fix (see Deferred Follow-Ups).

## Deviations from Plan
None — plan executed exactly as written. Task 1 implemented the one-spot `applyEntryReset` refactor as specified; Task 2's on-device gate was satisfied by owner manual verification.

## Issues Encountered

### Deferred Follow-Up: FineTuneNavTest swipe-gesture harness defect (NOT introduced by 17-08)

The instrumented `FineTuneNavTest` (both `tuneAction_navigatesToFineTuneHub` and `reEnteringFineTune_opensHub_notStaleGroupPage`) **FAILED on flox — but NOT at the assertion under test.** They fail at the shared `openFineTuneViaDrawer()` setup step.

- **Root cause:** the test's `composeRule.onRoot().performTouchInput { swipeUp() }` spreads ~800px over a ~200ms / ~12-event gesture, so no single per-event `dragAmount` clears `SWIPE_UP_THRESHOLD_PX = 80f` in AppShell's `detectVerticalDragGestures { _, dragAmount -> if (dragAmount < -80f) drawerOpen = true }`. The drawer never opens in the test → both methods die before reaching the nav-reset logic under test.
- **Scope:** this is a **pre-existing test-harness gesture defect** (this instrumented test compiled but was never device-run before 17-08), independent of and not introduced by the 17-08 fix. It affects the baseline (pre-fix) test equally — it is a property of the swipe synthesis vs the per-event threshold, not of `navigateTo()`.
- **Resolution chosen:** the owner chose **manual eyeball as the authoritative on-device gate** (which PASSED — re-entering Fine-Tune lands on the Hub) and **deferred the instrumented-test fix to a separate test-hardening follow-up.**
- **DO NOT** modify `FineTuneNavTest.kt` in this plan — it was intentionally left untouched. The fix (e.g. a multi-event drag that clears `SWIPE_UP_THRESHOLD_PX` per event, or driving the drawer open via state rather than the synthesized gesture) belongs to a dedicated test-hardening pass so it surfaces in `/gsd-progress` and isn't silently lost.

## Next Phase Readiness
- UAT Check 8 (MINOR / REVIEW #6) closed on-device; the same-dest re-entry reset now holds for Fine-Tune, Calibration, and Macros.
- The two Phase-17 gap-closure plans (17-07 busy-lock wedge, 17-08 same-dest re-entry) are both executed and on-device-approved. Phase-17 closure (verifier + remaining UAT posture) is owned by the orchestrator — do NOT mark the phase verified here.
- **Outstanding (deferred, surfaces in /gsd-progress):** the `FineTuneNavTest` swipe-gesture harness defect above — a test-only follow-up, no production impact.

## Self-Check: PASSED
- `app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt` — FOUND
- Commit `2bebae0` — FOUND
- `17-08-SUMMARY.md` — FOUND

---
*Phase: 17-fine-tune-live-adjust-panel*
*Completed: 2026-06-08*
