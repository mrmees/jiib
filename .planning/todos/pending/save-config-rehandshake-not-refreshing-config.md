---
id: save-config-rehandshake-not-refreshing-config
created: 2026-06-03
source: Phase 9 Probe-Calibrate on-device UAT (Ender 3 klicky)
priority: medium
resolves_phase: 13
---

# SAVE_CONFIG re-handshake does not refresh config-derived one-shot reads

## Symptom (observed live, Ender 3)

After a Probe-Calibrate **Save** (`SAVE_CONFIG` → Klipper FIRMWARE_RESTART → reconnect), the
displayed saved `probe.z_offset` kept showing the **old** value. Only a full **app restart** picked
up the just-applied offset — re-opening the page (pre-fix) did not.

## Expected

The G2/G3 re-handshake (`05-10`) is supposed to re-run the FULL `runHandshake()` on
`notify_klippy_ready`, which includes the one-shot configfile read block in
`MoonrakerSession.runHandshake()` (`min_extrude_temp`, `max_extrude_only_distance`, screws-tilt
config, macro bodies, and now `probe.z_offset`). So after a SAVE_CONFIG restart the re-read should
republish the new values on the store. It apparently did not.

## Current mitigation (NOT the real fix)

Phase 9 added `MoonrakerSession.refreshProbeZOffset()` exposed via `SpineHandle.refreshProbeZOffset`,
called from the Probe-Calibrate screen's `onEnter` (AppShell). This makes **that one page**
self-correct on entry, but it papers over the underlying re-handshake gap — every OTHER
config-derived value (`minExtrudeTemp`, `maxExtrudeDistance`, screws config, macro bodies) would be
equally stale after a runtime config change until an app restart.

## Investigate in Phase 13

- Confirm whether `notify_klippy_ready` actually fires (and is caught, `handshakeComplete==true`) on a
  SAVE_CONFIG restart vs. the socket dropping → reconnect path. Check the `rehandshakeMutex` /
  `handshakeComplete` gating in `connectAndServe` (MoonrakerSession ~line 213–293).
- Confirm the configfile query runs AFTER Klipper has reloaded the new config (timing/ordering — a
  read that races the reload would return the stale value).
- If the re-handshake path is sound, the per-page `refreshProbeZOffset` mitigation can be removed in
  favor of the general refresh; if not, fix the re-handshake so ALL one-shot reads refresh.
- Add a regression test that drives a `notify_klippy_ready` after the initial handshake and asserts
  the configfile one-shot StateFlows are re-published (extend the `05-10` KlippyReadyResync tests).

Not blocking: the daily print-control loop is unaffected; this only touches config values that change
rarely at runtime (calibration saves, printer.cfg edits).
