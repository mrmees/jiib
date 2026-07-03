# Find on network

Scans your local network for Moonraker instances advertising over mDNS and lets you add one with a
single tap — no manual host entry required.

**Getting there:** Home → System → Manage printers → Find on network.

## The screen

The Focus card shows a status line that updates through the scan and connect flow. The list below
fills with discovered printers as they resolve. The foot bar has two buttons: **Back** and **Scan**.

## Options & controls

### Focus card

The status line cycles through four states in priority order (probing takes precedence over scanning):

- **Connecting…** — a printer row was tapped and jiib is probing both HTTP and WebSocket transports.
- **Scanning…** — an active mDNS scan is running (visible on open and after tapping Scan).
- **No printers found — enter the host manually.** — the scan window closed with zero results.
- **Tap a printer to add it.** — the scan window closed with at least one result in the list.

### List rows

Each row represents one `_moonraker._tcp` service resolved during the scan.

- **Primary label** — the hostname from the mDNS advertisement, with any leading `moonraker @ `
  prefix stripped. If the advertisement carried no usable hostname (or only the bare word
  "moonraker"), the printer's IP address is shown instead.
- **Trailing label** — the printer's IP address, shown only when a distinct hostname is also
  displayed.

Tap a row to probe it. jiib attempts both an HTTP `GET /server/info` and a WebSocket `server.info`
call without an API key. If both succeed the printer is added as a new profile and jiib connects
to it immediately; the profile name defaults to `hostname:port` (or `ip:port` when no hostname was
advertised). If either transport fails — for example because the printer requires an API key or
uses HTTPS — the connection editor opens pre-filled with the host and port so you can supply the
missing details.

Only one probe runs at a time. Starting a new probe cancels any in-flight probe from a previous
tap.

### Foot bar

- **Back** — returns to Manage printers without adding anything; cancels any in-flight probe.
- **Scan** — starts a fresh 8-second mDNS scan, clearing the current list. Disabled and labeled
  "Scanning…" while a scan is already running.

## Notes

The scan listens for `_moonraker._tcp` advertisements. Moonraker's mDNS advertisement is opt-in:
add a `[zeroconf]` section to your Moonraker config if your printer does not appear.
> Uses Android `NsdManager` (`_moonraker._tcp`); the probe hits `/server/info` over HTTP — no
> extra Moonraker config beyond `[zeroconf]` is needed.

An empty result after a scan is a normal outcome, not an error. Use the **Add** row on the Manage
printers screen for manual entry.

## Related

[System](system.md) · [Printer Settings](printer-settings.md)
