# Connection Editor

Edit the host address, port, API key, and (optionally) an advanced URL for a single printer
profile. You can also verify reachability before saving with the Test probe.

**Getting there:**

- Home → System → Printer Settings → **Connection** — edits the active printer's connection.
- Home → System → Printer Settings → **Manage printers** → arm **Edit** → tap a profile row — edits any profile.
- Home → System → Printer Settings → **Manage printers** → **Add** — creates a new profile.
- Home → System → Printer Settings → **Manage printers** → **Find on network** → (needs manual entry) — opens the editor pre-filled with a discovered host and port.

## The screen

The **Focus card** shows a live endpoint preview when no field row is selected: the derived
WebSocket URL on the first line, followed by the Test status below it (either "Not tested yet",
the word "Test" in muted caption text while probing, or two result lines — HTTP and WebSocket — each with a pass or
fail indicator and, on failure, the failure category).

When you tap a list row, the Focus card swaps to an inline field editor for that row: a text field,
an optional warning or hint message, and a **Done** button. For the API key row, a red **Clear
key** button also appears while a key is saved and not yet cleared. Back returns from the
field editor to the endpoint summary without saving.

The **list** below has five editable field rows (Name, Host, Port, API key, Advanced) and —
for existing profiles only — a red **Delete** row at the bottom.

The **foot bar** has three buttons in order: **Back**, **Test**, and **Save**.

## Options & controls

### Focus card — endpoint summary

Visible when no field row is selected.

- **WebSocket URL** — the `ws://` (or `wss://`) URL jiib will connect to, derived live from
  the current Host, Port, and Advanced values. Updates as you edit fields. This is what the
  printer connection actually uses; the HTTP base URL (used for REST) shares the same host and
  port.
- **Test status** — one of:
  - *Not tested yet* — displayed until the first Test run.
  - *Test* — probe is in progress; the word "Test" appears as muted caption text (no spinner).
  - **HTTP** / **Websocket** result lines — appear after a probe completes; each shows a
    checkmark (green, pass) or an X (red, fail) plus a failure description when applicable.
    Failure categories: timeout, connection refused, unauthorized, certificate error, or
    unknown.

### Focus card — field editor

Replaces the endpoint summary while a list row is selected. Every field editor includes:

- **Text field** — labeled with the field name; keyboard type varies per field (see each row
  below).
- **Warning / hint line** (optional) — shown below the field in caption style; red when the
  input is invalid, secondary color otherwise.
- **Done** (green) — commits the edited value and returns to the endpoint summary. For Host,
  Done also normalizes the input (see Host below).

### List rows

Each row shows the field's current value as trailing text. Tapping a row opens its editor in
the Focus card.

- **Name** — the display name for this printer profile. Keyboard: text. Blank is allowed; an
  unnamed profile falls back to the host address in every listing. If you save with no name
  and the profile is new, jiib will seed the name from the printer's hostname (`printer.info`)
  on first connect — that auto-seed happens once and locks.

- **Host** — the Moonraker host address: a hostname, IPv4 address, or bracketed IPv6 address.
  Keyboard: text. Do not include a scheme (`http://`, `ws://`, etc.) or a path — put those in
  Advanced. You may use the `host:port` shorthand here (e.g. `192.168.1.50:7130`); jiib
  splits the port automatically on Done or Test. Accepted formats: plain hostname / IP,
  `host:port`, `[::1]`, `[::1]:port`. Rejected: any scheme, paths (`/`), query strings,
  fragments, user-info (`user@host`), unclosed `[`, bare (unbracketed) IPv6.
  - **Warning: `.local` names** — a caption note appears when the host ends in `.local`;
    mDNS resolution can be unreliable on some networks. Prefer the numeric IP when possible.

- **Port** — the Moonraker port number. Keyboard: numeric. Valid range: 1–65535; defaults to
  `7125`. Non-digit characters are filtered as you type. If you entered a `host:port`
  shorthand in the Host field, the port is extracted and reflected here automatically.

- **API key** — the Moonraker API key, if your install requires one. Keyboard: password
  (characters masked). The raw stored key is **never** pre-filled in the text field; the row
  shows **Set** when a key is saved or **Not set** when none is configured.
  - Blank field + no Clear action = preserve the stored key on Save.
  - Typing a new value replaces the stored key on Save.
  - **Clear key** (red, visible only while a key is saved and not yet cleared in this
    session) — explicitly removes the key. After tapping, the row shows **Not set** and a
    typed value in the text field would set a fresh key rather than restore the old one.
  - Hint line: "Only needed if your install requires it" when no key is stored; "Set" when a
    key is stored and the field is blank (i.e., the preserve path is active).

- **Advanced** — a full Moonraker URL, for use with reverse proxies or non-standard paths.
  Keyboard: URI. When set, the Advanced URL **overrides host and port** entirely for both HTTP
  and WebSocket connections. Leave blank for the standard `host:port` setup.
  - Accepted schemes: `http://`, `https://`, `ws://`, `wss://`. A `ws://` or `wss://` input
    is treated as `http://` or `https://` internally; the WebSocket endpoint is derived by
    replacing the scheme and appending `/websocket`.
  - A trailing `/websocket` path segment is stripped from the base URL automatically, so
    entering either the HTTP base or the WebSocket endpoint works.
  - Query strings and fragments are rejected.
  - When the Advanced URL uses `https://` or `wss://`, the connection uses `wss://`
    (TLS). The legacy per-printer TLS toggle is retired; TLS is controlled here only.

### Foot bar

- **Back** (accent) — contextual: if a field editor is open, closes it and returns to the
  endpoint summary; otherwise exits the connection editor without saving.

- **Test** (accent) — runs a dual HTTP and WebSocket probe against the current host, port,
  API key, and Advanced URL settings. Also normalizes the Host field before probing.
  Disabled while a probe is in progress or while the Host field is blank. Both transport legs
  run concurrently with a 5-second per-leg timeout.
  - HTTP leg: `GET /server/info` with `X-Api-Key` if a key is configured; passes on HTTP 200,
    fails on any other status or network error.
  - WebSocket leg: opens a socket, fetches a oneshot token via `GET /access/oneshot_token`
    when a key is configured, appends `?token=` to the WebSocket URL, then sends `identify`
    and `server.info` RPC calls; passes only if both calls succeed.
  - After the probe, the Focus card updates with HTTP and WebSocket result lines.

- **Save** / **Save anyway** (green) — saves the profile and exits the editor. Validates host
  and port first; the Host field is normalized on save (same rules as Done in the Host editor).
  If the most recent Test probe failed, the label changes to **Save anyway** to acknowledge
  the failure; behavior is otherwise identical. API key resolution follows the rules described
  under API key above.

### Delete row

Visible only when editing an **existing** profile (not when adding a new one).

- **Delete** (red icon and label) — tapping opens a confirmation dialog titled
  **Remove Printer?** with the message "This will remove the connection profile. The printer
  itself is unaffected." The dialog has two buttons: **Delete** (destructive, confirms removal)
  and **Back** (cancels). Confirming deletes the profile and exits the editor; the physical
  printer is not affected.

## Related

[Concepts](concepts.md) · [Printer Settings](printer-settings.md) · [Manage printers](manage-printers.md)
