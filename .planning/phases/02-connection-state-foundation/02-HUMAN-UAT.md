---
status: resolved
phase: 02-connection-state-foundation
source: [02-VERIFICATION.md]
started: 2026-05-30T00:00:00Z
updated: 2026-05-30T00:00:00Z
---

## Current Test

[all items resolved]

## Tests

### 1. Wi-Fi-yank reconnect + resync on the real Ender 5 Plus
expected: With the app connected to the live printer (static dev config), interrupting the
network mid-stream drives ConnectionState → Disconnected while the last-known PrinterState is
RETAINED (shown stale, NOT blanked). Restoring the network triggers backoff+jitter reconnect and
the full identify → objects.list → objects.query → objects.subscribe resync handshake, after which
displayed temps/position are correct (non-stale) again. (D-03 retain-on-drop → D-04 resync-overwrite;
ROADMAP Phase-2 success criterion #1.)
result: PASSED — proven on real hardware 2026-05-30 via LiveReconnectYankTest (commit dd99638),
  an orchestrated adb `svc wifi disable/enable` Wi-Fi drop over USB against the live Ender 5 Plus:
  READY bed=55.22 → DROP_SEEN Disconnected (temp retained, stale=true) → RECONNECTED bed=55.18
  (resync overwrote → non-stale). BUILD SUCCESSFUL. The deterministic reconnect/resync logic is also
  unit-proven (ReconnectSupervisorTest, ConnectionStateTest, HandshakeTest).

how_to_run: |
  1. Build + install debug, or run the instrumented test harness pointed at the live printer.
  2. With a live connection, disable the device Wi-Fi (or the printer's network) for ~10–20s.
     Observe ConnectionState=Disconnected and that the last temps remain on screen (stale, not blank).
  3. Re-enable the network. Observe automatic reconnect (backoff+jitter), the resync handshake,
     and temps/position returning to live (non-stale) values.
  Note: deterministic reconnect+resync is already proven in JVM unit tests
  (ReconnectSupervisorTest, ConnectionStateTest, HandshakeTest) against FakeWebSocket; this UAT
  proves the same against a REAL OkHttp socket failure on real hardware.

## Summary

total: 1
passed: 1
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps
