---
id: save-config-rehandshake-not-refreshing-config
created: 2026-06-03
source: Phase 9 Probe-Calibrate on-device UAT (Ender 3 klicky)
priority: high
resolves_phase: optimization-reliability
---

# SAVE_CONFIG re-handshake does NOT restore the live subscription (full feed freeze)

## Severity: HIGH — core-loop reliability

After a `SAVE_CONFIG` (or any Klipper FIRMWARE_RESTART / `printer.cfg` reload), the in-session
recovery is broken: **the entire live subscription stops updating** until the app is force-restarted.
This is the exact G2 freeze the `05-10` notify_klippy_ready re-handshake was supposed to prevent — and
it is **NOT holding on the Ender 3**. `SAVE_CONFIG` is a routine action (every Z-calibrate save, PID
tune, screws-tilt, bed-mesh save), so this silently breaks "monitor/drive a print" after common use.

## Repro (live, Ender 3 192.168.1.121, 2026-06-03)

1. Probe-Calibrate → Accept → Save → `SAVE_CONFIG` → Klipper restarts; app appears to reconnect.
2. Start a print. The printer prints (confirmed via Moonraker: `print_stats.state=printing`,
   `virtual_sdcard.is_active=true`), but:
   - Home/Status stays **stuck on the LAST FINISHED print's info** — the whole feed is frozen, not
     just `print_stats` (temps/position not ticking either; owner confirmed "stuck showing the last
     finished print info").
   - Files "Start" button stuck on "Starting…" (it waits for `printState→Printing`, which never lands).
3. **Force-closing + restarting the app** (fresh socket → fresh handshake) immediately shows the
   running print correctly. So reducer/subscription/routing are all correct — only the in-session
   re-handshake recovery is broken.

NOTE: the probe-calibrate screen *looked* fine post-SAVE_CONFIG only because `refreshProbeZOffset()`
re-queries `probe.z_offset` on page entry, masking the dead feed.

## Investigate (now the headline of the promoted reliability phase)

- Does `notify_klippy_ready` actually fire + get caught (`handshakeComplete==true`) on the E3
  SAVE_CONFIG, or does the socket behave differently than the E5 (where Phase-5 G2 was verified)?
- Does `runHandshake()`'s `objects.subscribe` re-register actually take effect after a klippy restart
  on the SAME socket, or does Moonraker silently drop it (timing/ordering vs the restart)?
- Is the frame collector / `rpc.statusUpdates` still delivering after the re-handshake, or did
  something tear it down? (MoonrakerSession `connectAndServe` ~213–315.)
- Consider: on `notify_klippy_ready`, force a full socket reconnect (the path that demonstrably works)
  instead of an in-session re-subscribe, if the in-session re-subscribe can't be made reliable.
- Add a LIVE repro/regression: SAVE_CONFIG → start print → assert `printState→Printing` reaches the
  store without a reconnect. Extend the `05-10` KlippyReadyResync suite with a payload-level check.

## Related

Subsumes the original "config one-shot reads go stale" framing — it's broader: the whole live feed
dies, config staleness was just the first symptom noticed.
