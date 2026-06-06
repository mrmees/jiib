# Phase 16 — Deferred Items

## 16-05: ShellPresenceTest whole-class on-device run is flaky (routing seed mismatch)

- **Found during:** 16-05 Task 2 (on-device verification of the new forward-stub drawer test).
- **Symptom:** `:app:connectedDebugAndroidTest --class ShellPresenceTest` — the new
  `shellRoute_forwardStubTilesAreInert` PASSED cleanly on a fresh first run, but a subsequent
  whole-class re-run had all 5 tests fail with "Status / Dinghy Display not displayed" (the shell/
  splash not routing as the test seed expects).
- **Root cause (pre-existing, NOT 16-05):** `ShellPresenceTest.seedRoute` saves to
  `container.connectionStore` (the Phase-14 DEAD/retained store), but routing now derives `hasConfig`
  from `profileStore` via `activeConfig`/`activeProfile`. On a device with a persisted active profile
  (left by prior real-printer UAT) the seed and the live profile state interact, making the route
  non-deterministic across runs. This coupling predates 16-05 and is unrelated to the babystep/drawer
  changes.
- **Why deferred:** out of 16-05's scope. The plan's Task-2 acceptance criterion is that the
  androidTest sourceset COMPILES (`:app:assembleDebugAndroidTest` SUCCESSFUL — it does); the on-device
  drawer gate is owned by the 16-08 autonomous:false checkpoint, not this autonomous plan. The babystep
  unit gate (`BabystepPrefsTest`) is GREEN.
- **Suggested fix (a later plan / 16-08):** migrate `ShellPresenceTest.seedRoute` to seed the
  `profileStore` (an active `Profile`) instead of (or in addition to) `connectionStore`, and clear the
  device's persisted profile/datastore before the run, so the whole-class run is deterministic.
