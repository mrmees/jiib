# Splash & connecting

The startup and recovery screen. It appears automatically whenever jiib cannot hand control
to the main app — on first launch, while connecting, when the printer firmware is down, or
when the saved connection can't be reached.

**Getting there:** This screen is a hard override, not a navigation destination. It appears
automatically based on connection state; main navigation is structurally absent while it
is showing. Tapping "Edit connection" or "Set up your printer" opens the
[Manage printers](manage-printers.md) screen, which is the only deliberate escape.

## The screen

The screen centers the jiib brand lockup (icon and wordmark) with a status line below it and
recovery buttons below that. There is no foot bar; the recovery buttons are the only controls.

The screen has two visual modes:

- **Connecting** (launch only): black background, white lockup and text, "Connecting…" status
  line, no buttons. This mode is active only during the initial connection — before the app
  has ever connected this session, and for up to ten seconds. A hard fault (connection error,
  Klippy `Shutdown`, or Klippy `Error`) causes an immediate switch to themed recovery without
  waiting for the window to expire. If no hard fault occurs and the printer remains unreachable,
  the switch happens at the ten-second mark.

- **Recovery** (themed): matches your active theme. The status line and button set change to
  reflect the specific fault. There are three distinct recovery sets depending on what is
  wrong (see Options & controls).

## Options & controls

The full set of possible controls, and the condition under which each appears:

### Status line

The status line shows the reason for the current state. It always displays something; it is
never blank. Priority order:

1. If no printer is configured: "Set up your printer".
2. If a TLS certificate trust failure occurred (secure/wss connection): "TLS certificate not
   trusted — check the Moonraker reverse-proxy certificate".
3. If Moonraker has provided a `webhooks.state_message` for the current fault: that message,
   verbatim (it often carries the raw MCU error or shutdown reason from Klipper). Rendered
   as plain text; never interpreted.
4. Enum-derived fallback: "Printer starting up…" / "Printer is shut down" / "Printer firmware
   error" / "Can't reach the printer" / "Connected". The last appears only during the 600 ms
   minimum-dwell window when Klippy has recovered to `Ready` and the connection is `Connected`
   but the splash is still being held so the recovery is perceptible.

### First run — no printer configured

Shown when no printer profile has been saved yet.

- **Set up your printer** (green) — opens [Manage printers](manage-printers.md) to add and
  configure your first printer. Once a printer is saved and connected, this screen exits
  automatically.

### Klippy down — Moonraker reachable, Klipper shutdown or errored

Shown when the WebSocket to Moonraker is open (`Connected` or `Syncing`) but Klipper itself
is in `Shutdown` or `Error` state. Moonraker is up; the firmware needs recovery.

- **Retry** (green) — cancels any reconnect backoff and attempts an immediate reconnect.
  Use this after clearing the fault manually from a terminal.

- **Restart firmware** (amber) — issues `printer.firmware_restart` via Moonraker's JSON-RPC.
  Use when the MCU has halted and needs a firmware reset. Routes through the session
  dispatcher (debounce, timeout); failures surface as a toast.

- **Restart Klipper** (amber) — issues `printer.restart` via Moonraker's JSON-RPC.
  Performs a soft Klipper process restart without resetting the MCU firmware. Routes through
  the session dispatcher.

### Unreachable — saved connection failing

Shown when the connection cannot be established: the socket is `Disconnected`, `Error`, or
Klippy reports `Disconnected`. Firmware and host restart are not offered here because Klippy
is not reachable.

- **Retry** (green) — cancels any reconnect backoff and fires an immediate reconnect attempt.

- **Edit connection** (accent) — opens [Manage printers](manage-printers.md) so you can
  correct the saved host, port, or API key. On TLS trust failure the same Unreachable surface
  appears; use Edit connection to switch to a plain `ws://` connection or replace the
  certificate.

### Automatic exit

The splash exits automatically once Klippy reaches the `Ready` state and the connection is
fully synced. A minimum 600 ms dwell is enforced on recovery splashes so a fast reconnect
is perceptible; the dwell only delays hiding the screen — it never delays the actual
reconnect.

## Related

[Concepts](concepts.md) — connection states and gating behavior.
[Manage printers](manage-printers.md) — adding and editing printer profiles.
