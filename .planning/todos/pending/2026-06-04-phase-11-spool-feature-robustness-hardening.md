---
created: 2026-06-04T19:59:06Z
title: Phase 11 spool feature robustness hardening (Codex review)
area: ui
target_phase: 21
files:
  - app/src/main/java/works/mees/dinghy/spool/ActiveSpoolFacade.kt:91
  - app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt:275
  - app/src/main/java/works/mees/dinghy/ui/spool/ActiveSpoolCard.kt:175
  - app/src/main/java/works/mees/dinghy/ui/spool/scan/ScanSurface.kt:95
  - app/src/test/java/works/mees/dinghy/spool/FakeSpoolmanClient.kt:76
---

## Problem

The deferred Codex review of the Phase 11 spool feature (run 2026-06-04 after Codex's
runtime crashed twice during the phase) surfaced five transient-failure robustness gaps.
None are crashes; none were caught by flox/live UAT because the failures need a flaky
network or single-lens hardware to manifest. The two real bugs + stale comment + camera
disposal leak from the same review were fixed immediately; these five are deferred to
**Phase 22 (Release Hardening & Ship)** as network-resilience + test-mock hardening.

Source: Codex review thread (agentId a0e5c3855584d3df3).

1. **Failed status read clobbers prior truth** — `ActiveSpoolFacade.refetchStatus()`
   (ActiveSpoolFacade.kt:91): a null/failed `server.spoolman.status` read parses to a blank
   `SpoolmanStatus()` and overwrites the prior value, so a transient Moonraker hiccup during
   a `notify_spoolman_status_changed` event flashes "no active spool / Spoolman disconnected".
   Borderline — it fires on `status_changed`, where a genuine disconnect IS a legit reason to
   clear, so guard rather than rip out.

2. **Color-filter read failure → empty inventory** — `SpoolHolder.applyColorSwatch()` /
   `applyMultiColor()` (SpoolHolder.kt:275): a failed/null filament read parses to an empty id
   list, stored as an active filter → `buildSpoolQuery()` emits `filament.id=-1` → "no matching
   spools". A transient proxy failure shows empty inventory instead of failing/clearing the
   filter.

3. **Clear exposed while active-spool status unknown** — `ActiveSpoolCard` (ActiveSpoolCard.kt:175):
   `hasActiveSpool` is true for all `Loading` states incl. `Loading(spoolId = -1)` (produced when
   `status == null`), so the card can show Clear and dispatch `post_spool_id {}` before the app
   knows whether a spool is active — risking clearing valid tracking.

4. **Lens switch strands single-lens devices** — `ScanSurface` (ScanSurface.kt:95): the front/rear
   toggle is always offered without checking `CameraSelector.DEFAULT_FRONT_CAMERA` /
   `DEFAULT_BACK_CAMERA` availability; on a single-lens device a missing lens → bind fails → stuck
   `Busy` with the toggle gone. (Both home printers are rear-cam only, so flox never hits it.)

5. **Test mock looser than the live server** — `FakeSpoolmanClient` (FakeSpoolmanClient.kt:76) still
   documents the old PUT-query measure contract and only echoes `getSpool(id)`; it doesn't implement
   `SpoolmanClient` or assert that `MoonrakerSpoolmanClient` emits the right method/path/body. This is
   the meta-lesson of Phase 11's mock-vs-reality strikes — harden the fake so the measure proxy
   contract can't silently regress.

## Solution

Fold into Phase 22 (ship) planning as a "spool resilience" slice.

1. Keep prior `_activeSpool` value on a null/failed parse (only overwrite on a successful parse);
   accept a genuine disconnect signal separately if needed.
2. Distinguish read-failure from genuine-empty in the color-filter path; on failure, leave the
   filter unchanged / surface an error rather than committing `filament.id=-1`.
3. Don't expose Clear until active-spool status is actually known (treat `Loading(spoolId=-1)` /
   `status == null` as "unknown", not "has spool").
4. Query lens availability; only offer the toggle when both lenses exist, and fall back to the
   available lens instead of dropping into `Busy`.
5. Make `FakeSpoolmanClient` implement `SpoolmanClient` and assert the real proxy method/path/body
   for `measure` (and ideally the other writes) so the live contract is pinned by tests.
