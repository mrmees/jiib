---
phase: 14-multi-printer-switching
plan: 06
subsystem: testing + on-device-uat + durable-profile-writes
tags: [instrumented-test, datastore, process-death, live-uat, multi-printer, write-scope, mock-vs-reality, SC-3, SC-4]
requires:
  - phase: 14-01
    provides: "ProfileStore (profiles/activeId flows + setActive/upsert writers) over the real profiles.preferences_pb"
  - phase: 14-02
    provides: "activeConfig (distinctUntilChanged) → runConfigLoop rebind seam; AppContainer.activeProfile"
  - phase: 14-05
    provides: "DevicesScreen switcher + drawer tile + onSwitched→Dest.PrintStatus landing (the surface this UAT exercises)"
provides:
  - "Instrumented ProfileSurvivesRestartTest — real DataStore cold re-read proves the persisted active-id survives process death (SC-3), where a host fake would lie"
  - "14-UAT.md — the recorded live two-printer hands-on UAT result (5 PASS + 1 owner-deferred), the binding SC-4 gate"
  - "AppContainer durable writeScope (process-lifetime SupervisorJob + Dispatchers.IO) + setActiveProfile/saveProfile/deleteProfile — the gap-closure that made the switch reliable on slow flash"
affects: [phase-15-fine-tune, phase-22-ship, any-future-profile-write-site]
tech-stack:
  added: []
  patterns:
    - "Profile persistence writes that race a same-frame navigation MUST run on a process-lifetime scope (AppContainer.writeScope), never rememberCoroutineScope() — composition teardown cancels the write mid-flight on slow flash"
    - "Survival-across-process-death is proven by an instrumented test over a real DataStore file (second store instance cold-reads persisted bytes), NOT a host fake (the host build's atomic rename is not persistence-faithful)"
key-files:
  created:
    - .planning/phases/14-multi-printer-switching/14-06-SUMMARY.md
  modified:
    - app/src/androidTest/java/works/mees/dinghy/service/ProfileSurvivesRestartTest.kt
    - .planning/phases/14-multi-printer-switching/14-UAT.md
    - app/src/main/java/works/mees/dinghy/... (AppContainer + DevicesScreen + SettingsScreen — durable writeScope, gap-closure 781277f)
key-decisions:
  - "All navigation-racing profile writes route through a process-lifetime AppContainer.writeScope; only the mDNS scan + idle global-theme-prefs branch stay composition-scoped (they don't race a nav)"
  - "Item 4 (mid-print switch) is owner-deferred, NOT blocking — the switch teardown/rebind path is identical to items 1/2 which passed, and Matthew declined to burn filament on a live print this session"
patterns-established:
  - "Pattern: process-lifetime writeScope for persistence writes that immediately precede a navigation (composition-scope cancellation = silent dropped write on slow flash)"
  - "Pattern: instrumented second-store cold-read is the only faithful proof of DataStore survival-across-process-death"
requirements-completed: [MULTI-01]
duration: ~live-uat-session
completed: 2026-06-04
---

# Phase 14 Plan 06: Survival Test + Live Two-Printer UAT + Durable-Write Gap-Closure Summary

**Closed the two gates a fake cannot prove — an instrumented `ProfileSurvivesRestartTest` over a real DataStore (active-id survives process death, SC-3) and the binding live two-printer hands-on UAT (SC-4) — and fixed the slow-flash write-cancellation bug the UAT surfaced (composition-scoped profile writes cancelled by same-frame navigation), routing all racing writes through a process-lifetime `AppContainer.writeScope` (`781277f`).**

## Performance

- **Duration:** one live-UAT session (instrumented test + hardware run + gap-closure fix + re-run)
- **Completed:** 2026-06-04
- **Tasks:** 2 (Task 1 instrumented test; Task 2 blocking live UAT — which spawned the `781277f` gap-closure)
- **Files modified:** instrumented test + UAT record + production write-call-sites (gap-closure)

## Accomplishments

- **Task 1 — instrumented survival proof (`7bb52dc`):** replaced the plan-01 `fail(...)` stub in
  `ProfileSurvivesRestartTest` with a real instrumented test over a real `DataStore<Preferences>`
  (`PreferenceDataStoreFactory.create` on a fresh `profiles.preferences_pb`-style file, mirroring
  `ServiceSurvivesRotationTest`). It upserts two profiles, `setActive(profileB.id)`, then constructs a SECOND
  `ProfileStore` over the SAME file (cold re-read of persisted bytes) and asserts `activeId.first() ==
  profileB.id` with both profiles present. **GREEN on flox** (Nexus 7, API 30): 1 test / 0 failures
  (`activeIdSurvivesProcessDeath`, 0.332s). This objectively proves SC-3 (D-02) where a host fake lies — the
  Windows build host's DataStore atomic rename is not persistence-faithful.
- **Task 2 — binding live two-printer UAT (recorded in `14-UAT.md`):** run on flox + live Ender 5 Plus
  (192.168.1.120:7125) + Ender 3 Pro (192.168.1.121:7125). **5/6 PASS, 1 owner-deferred, 0 FAIL** → gate
  PASSED. Fresh-add of both printers (D-07/D-11/D-10), switch+drive-each with no re-entry (SC-4/D-03/D-08),
  no-churn on theme edit (Pitfall 1/T-14-04), delete-active-auto-picks + delete-last→Connect (D-12/D-14/D-11),
  and force-stop survival (SC-3) all PASS. Item 4 (mid-print switch, D-04) owner-deferred.
- **Gap-closure (`781277f`):** root-caused and fixed the intermittent switch-revert the first UAT run caught.

## Task Commits

1. **Task 1: instrumented ProfileSurvivesRestartTest** — `7bb52dc` (test) — GREEN on flox (1/0).
2. **14-UAT.md scaffold** — `138786c` (docs) — the 6-item blocking UAT script.
3. **Gap-closure: durable writeScope for profile writes** — `781277f` (fix) — production code; made the
   headline switch reliable on slow flash (see Deviations).
4. **Closeout (this commit): record live UAT PASS + gap-closure, complete plan** — docs (UAT + SUMMARY +
   STATE + ROADMAP).

## Files Created/Modified

- `app/src/androidTest/java/works/mees/dinghy/service/ProfileSurvivesRestartTest.kt` — real instrumented
  survival test (DataStore cold re-read; no `fail(...)` stub remains).
- `.planning/phases/14-multi-printer-switching/14-UAT.md` — recorded 6-item UAT result (5 PASS + item-4
  owner-deferred) + the G-1 gap/resolution; status PASSED.
- `AppContainer` + `DevicesScreen` + `SettingsScreen` (production, committed `781277f`) — durable
  `writeScope` (`SupervisorJob` + `Dispatchers.IO`) + `setActiveProfile`/`saveProfile`/`deleteProfile`; all
  navigation-racing write call-sites converted.

## Decisions Made

- **Item 4 owner-deferred, not blocking.** Matthew declined to start a live print to avoid burning filament.
  The switch's teardown+rebind path is the SAME one proven repeatedly in items 1/2 (the switch only changes
  which printer the tablet watches — it sends no print-affecting gcode), so residual risk is low. Re-run
  opportunistically next time a print is genuinely running.
- **writeScope ownership.** Only navigation-racing writes (switch, save, delete, clear-key,
  active-profile theme-persist) moved to the durable scope; the mDNS scan and idle/global-theme-prefs branch
  legitimately stay composition-scoped (no navigation race).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Composition-scoped profile writes cancelled by same-frame navigation (slow-flash dropped-write)**
- **Found during:** Task 2 (the blocking live UAT — item 2, the headline SC-4 switch).
- **Issue:** the first UAT run FAILED item 2: tapping a printer in the Devices switcher INTERMITTENTLY
  reverted to the old printer and the active (⚡) marker didn't move. Logcat showed ~4 switch attempts
  producing only ONE new `sessionInstanceId` rebind. Root cause: every profile-persistence write ran on a
  `rememberCoroutineScope()` (composition-scoped), but the Devices switch + Settings save/delete navigate
  away in the SAME frame — composition teardown cancels the scope mid-write, and the Nexus 7's slow flash
  loses the DataStore `.tmp → rename` race, silently dropping the active-id write so `activeConfig` never
  emits → no spine rebind. The Nth **mock-vs-reality** strike: green units + fast hardware hide a
  write-cancellation bug only the real slow-flash device surfaces.
- **Fix:** `AppContainer` now owns a process-lifetime `writeScope` (`SupervisorJob` + `Dispatchers.IO`) and
  exposes `setActiveProfile(id)` / `saveProfile(profile)` / `deleteProfile(id)`. All UI write call-sites
  converted to these durable methods (DevicesScreen switch; SettingsScreen save, delete, clear-key,
  active-profile theme-persist). mDNS scan + idle global-theme-prefs stay composition-scoped.
- **Files modified:** `AppContainer`, `DevicesScreen`, `SettingsScreen`.
- **Verification:** compile + full `:app:testReleaseUnitTest` GREEN; debug APK rebuilt + reinstalled on flox;
  item 2 (and 1/5/6) re-run PASS on flox + both live printers — active marker moves every tap.
- **Committed in:** `781277f` (by the orchestrator, prior to this closeout).

---

**Total deviations:** 1 auto-fixed (Rule 1 — bug). **Impact on plan:** the fix was necessary for the
headline SC-4 to pass on the target floor; no scope creep (write-site plumbing only, no new feature).

## Issues Encountered

- The intermittent switch-revert (above) — caught only by the on-device UAT, not the GREEN unit suite. This
  is exactly why the live two-printer gate is mandatory for this feature (server-derived capabilities + a
  real slow-flash write race cannot be reproduced in unit tests).

## Threat Mitigations Applied

- **T-14-13 (stale-state leak across a switch):** the `SpineHandle` atomic republish — proven on-device
  against two live printers (UAT item 2 PASS) where a fake session would lie.
- **T-14-14 (corrupt/dropped persisted active-id → wrong/no printer on cold start):** the survival test +
  UAT item 6 confirm the happy path; the gap-closure `781277f` specifically removes the slow-flash
  dropped-write failure mode (the most likely real-world cause of a "wrong printer on switch").

## Known Stubs

None. The plan-01 `ProfileSurvivesRestartTest` `fail(...)` stub is now a real instrumented test.

## Self-Check: PASSED

- `ProfileSurvivesRestartTest.kt` present on disk and is a real instrumented test (no `fail(...)` stub) — verified.
- `14-06-SUMMARY.md` and updated `14-UAT.md` (status PASSED) present on disk — verified.
- Commits exist in git log: `7bb52dc` (test), `138786c` (UAT scaffold), `781277f` (gap-closure fix) — all verified via `git log --oneline -1 <hash>`.
- UAT records all 6 items (5 PASS + item-4 owner-deferred) + the G-1 gap/resolution; gate PASSED.

## Next Phase Readiness

- Phase 14 plan-level work complete (6/6 plans). **Phase-level completion is left to the orchestrator's
  verification pass** (this closeout marks only plan 14-06 progress).
- One non-blocking follow-up: opportunistically run UAT item 4 (mid-print switch) the next time a print is
  actually running on one printer.

---
*Phase: 14-multi-printer-switching*
*Completed: 2026-06-04*
