# ShellPresenceTest whole-class on-device run is non-deterministic

**Found:** Phase 16 (plan 16-05 Task 2 on-device verification); recorded in
`.planning/phases/16-home-print-status-redesign/deferred-items.md` and `16-VERIFICATION.md`.
**Severity:** low — test-infrastructure flake, pre-existing, NOT introduced by Phase 16. The
androidTest sourceset compiles; the babystep unit gate (`BabystepPrefsTest`) is GREEN; the on-device
drawer gate was covered by the 16-08 checkpoint.

## Symptom
`:app:connectedDebugAndroidTest --class ShellPresenceTest` passes on a fresh first run, but a
whole-class re-run can fail all 5 tests with "Status / Dinghy Display not displayed" (shell/splash
not routing as the test seed expects).

## Root cause (pre-existing coupling)
`ShellPresenceTest.seedRoute` saves to `container.connectionStore` (the Phase-14 DEAD/retained store),
but routing now derives `hasConfig` from `profileStore` via `activeConfig`/`activeProfile`. On a device
with a persisted active profile (left by prior real-printer UAT), the seed and the live profile state
interact, making the route non-deterministic across runs. This predates Phase 16.

## Suggested fix (a later plan — candidate: Phase 20 WebRTC / Phase 21 Ship hardening)
Migrate `ShellPresenceTest.seedRoute` to seed the `profileStore` (an active `Profile`) instead of (or in
addition to) `connectionStore`, and clear the device's persisted profile/DataStore before the run, so the
whole-class run is deterministic. Relates to the broader Phase-14 `connectionStore` retirement
([[dinghy-display-mock-vs-reality]] / dead-store cleanup).
