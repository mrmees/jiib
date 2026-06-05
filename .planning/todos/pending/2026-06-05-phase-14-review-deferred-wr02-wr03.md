---
created: 2026-06-05T00:00:00Z
title: Phase 14 code-review deferred findings (WR-02 idle seedTheme, WR-03 webcam null-key)
area: ui
target_phase: 22
files:
  - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
  - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
---

## Problem

The Phase-14 code review (`.planning/phases/14-multi-printer-switching/14-REVIEW.md`,
0 critical / 3 warning / 5 info) flagged two non-blocking Warnings deferred to ship
hardening. The real Warning (WR-01, the Appearance lost-update) was fixed immediately
in commit `4e4d83b`; these two are genuinely minor edge cases:

- **WR-02 — `seedTheme` no-active branch is one-shot.** In `AppContainer.seedTheme`,
  the no-active-profile branch samples the global theme via `themePrefs.flow.firstOrNull()`
  (a single value), so an idle theme edit (Connect/Settings before any printer exists)
  is not re-seeded live into the resolver. Masked in practice because Settings ALSO calls
  the live `themeResolver.set*` setters at the tap site, so the screen reflects the change
  immediately; only a second observer wouldn't. Fix: collect the global theme flow
  reactively in the no-active branch (mirror the active branch's flatMapLatest).

- **WR-03 — webcam preferred-cam key collapses on the null edge.** The webcam pref is
  keyed `preferred_cam_<profileId>`, but the build site passes `profileId ?: ""`, so when
  there is no active profile the key collapses to a shared `preferred_cam_` — defeating
  D-06 per-printer isolation on that edge. Harmless today (null profileId ⇒ idle ⇒ no
  session ⇒ no webcam), but brittle. Fix: thread the nullable id through and skip the
  read/write entirely when null (or gate the holder on an active profile).

## Why deferred

Neither is a crash or a user-visible defect in the proven flows; both need a specific
idle/edge state to manifest. Batched with the other ship-phase (22) robustness hardening
todo so the final pass cleans them up together. Info-level findings (stale KDocs) were
already fixed in `4e4d83b`.
