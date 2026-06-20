# Connection editor + System Info — Focus/Field redesign

**Date:** 2026-06-19
**Status:** Design approved; Codex-reviewed (findings folded in 2026-06-19); pending owner spec review
**Scope:** Two screens reached from Printer Settings.
1. **Connection editor** — rewrite the hand-rolled connection form into the Focus/Field
   tap-row-to-edit grammar (mostly a reskin + the host-normalization bugfix + a new Test-Connection
   probe). Grounded in research, NOT in copying competitors.
2. **System Info** — rebuild the flat read-only row dump into a **device browser**: Field = list of
   devices (host SBC + each Klipper MCU), Focus = the selected device's full detail + contextual
   restart/power actions.

## Design-law change (applies repo-wide)

The "**no alphanumeric keyboard in printer controls**" rule is **relaxed**: the keyboard is now a
sanctioned tool used **sparingly, where text entry is the honest input** (host, name, API key). It is
no longer something to design around. `docs/ui_design/THEMING.md` and the root `CLAUDE.md` UI-law
bullet must be updated to reflect this (the Settings/Save-name "exceptions" framing becomes the
general rule: use the keyboard where typing is genuinely the right input).

---

# Part 1 — Connection editor

## Goal

Refit the add/edit-a-printer-connection screen into Focus/Field, fix the input bugs that currently
cause a **silent infinite hang at the jiib logo**, and make the common LAN case (no auth, plain ws,
port 7125) effortless while keeping reverse-proxy/TLS reachable for the minority.

## Context

- Current screen: `app/src/main/java/works/mees/dinghy/ui/screen/PrinterConnectionEditor.kt`
  (lines 48–335). Hand-rolled scrolling `Column` of `TokenTextField`s (name, host, port, apiKey) +
  a `SecureToggleRow` (per-printer wss/https) + Scan-mDNS button + `DiscoveredPrinterRow` list +
  Save/Back. **Not** Focus/Field.
- Data model: `Profile` (`app/src/main/java/works/mees/dinghy/config/Profile.kt`): `id`, `name`,
  `host`, `port` (default 7125), `apiKey?`, `useSecure`. Persisted as JSON via `ProfileStore`.
- mDNS discovery: `container.discovery.discover()` → `DiscoveredPrinter(name, host, port)`.
- Reached from `NavDest.PrinterSettings` ("Connection" row) and `NavDest.ManagePrinters` ("Edit").
  Rendered inline (BackHandler), no NavHost route.
- The established **tap-row-to-edit** pattern this clones: Increment Values + Theme screens (Field =
  selectable rows; tapping a row swaps an editor into Focus; Save docked in Focus; Back in foot-bar).

## Research findings driving the design

(Deep-research web sweep — Moonraker docs + Mainsail FAQ + forums, adversarially verified — plus a
code-read of the open-source Mobileraker client. Full reports in the brainstorm transcript.)

- **Default = no API key.** A LAN client whose IP is in Moonraker's `trusted_clients` allowlist gets
  full API access with no key; the key field must feel **optional/secondary**, never required.
  *Nuance (Codex):* stock Moonraker's default `trusted_clients` list is **empty** — it's the common
  Mainsail/Fluidd-style installs that pre-seed the LAN subnet. And `force_logins: True` overrides
  trusted-client auth once any user exists. So "no key" is the right *default*, but the editor must
  still handle a real Unauthorized.
- **The connection contract for the default case is `ws://HOST:7125/websocket`** (+ `http://HOST:7125`
  for REST): port defaults to 7125, the `/websocket` path is appended (user never types it), Moonraker
  binds `0.0.0.0`. *Nuance (Codex):* `/websocket` is **not unconditionally** the path — Moonraker
  supports a `route_prefix` that prepends a path to all endpoints. So the canonical stored thing is a
  **base URL** (`scheme://host[:port][/route_prefix]`) and the ws URL = base + `/websocket`; normalize
  a pasted endpoint URL to avoid a double `/websocket`.
- **A prominent ws/wss "secure" toggle is harmful for the direct-default case.** Direct Moonraker
  serves plain HTTP on 7125 by default; HTTPS only with explicit certs and on a *separate* `ssl_port`
  (default 7130). So secure-on-7125 is a **guaranteed TLS-handshake failure for direct default
  Moonraker** (not for a reverse proxy, which can terminate TLS on any port). → **scheme is derived
  from the address, the toggle is deleted.**
- **Host normalization is the #1 input bug** (= our `dinghy-host-normalization-todo`): a bare
  `192.168.1.50` parses with an **empty host** unless a scheme is injected first.
- **"Unauthorized" should be guided recovery, not a dead end.** Official remedy: add the device IP
  (CIDR) to `trusted_clients` + restart Moonraker, OR enter an API key.
- **Reverse-proxy path prefixes are a real-but-minority case** → an opt-in Advanced field, not a
  primary field.
- **`.local`/mDNS is unreliable on Android** (Mobileraker wrote a dedicated rejector) → at least warn.
- Mobileraker's highest-value pattern: **probe both transports before save, echo the derived URLs
  back, show per-transport pass/fail, allow Save-Anyway.**

## Design

### 1. Field — the list of grouped setting rows

Translucent `ListRow`s, leading icon + value, no group labels (structure via icons/spacing):

| Row | Value shown | Editor / behavior |
|-----|-------------|-------------------|
| **Name** | display name (placeholder: "From printer hostname") | text editor in Focus; auto-seeded from hostname (existing `nameAutoSeeded`), editable |
| **Host** | IP/hostname (or "Not set") | text editor in Focus; **required** |
| **Port** | port (default 7125) | numeric editor in Focus |
| **API key** | "Set" / "Not set" (masked; never echo raw key) | text/password editor; helper "Only needed if your install requires it"; Clear-key action |
| **Find on network** | — | tapping opens mDNS scan + results in Focus |
| **Advanced (proxy / full URL)** | the override URL, or "—" | text editor in Focus; when set, overrides host+port+scheme |

### 2. Focus — three contextual states

1. **Resting / summary** (no row being edited): FocusFrame header (title "Add a printer" /
   "Edit <name>", icon) + a **live endpoint preview** that rebuilds as fields change —
   `ws://192.168.1.120:7125/websocket` — + the **last Test result** ("Not tested yet" until run).
2. **Editing a row**: the tapped row's editor swaps into Focus, keyboard up, **Save-this-field**
   docked. Numeric keyboard for Port; text for the rest.
3. **Find-on-network**: mDNS scan control + discovered-printer list in Focus; tapping a result fills
   Host + Port back into the list.

### 3. Foot bar

`FootButtonBar`, count-driven: **Back** (accent, first per R8) · **Test** (accent) · **Save** (go).

### 4. Test Connection probe (new "B" functionality)

On **Test** (and optionally implicitly on Save): fire two probes concurrently. **The probe must
actually exercise auth, not just reachability** (Codex BLOCKER): `/access/info` is reachable by
*unauthorized* clients, so a 200 there proves only that Moonraker answers — not that we can use the
API. So:
- **HTTP**: GET `/access/info` first (reachability + auth context), then probe a **protected**
  endpoint — `/server/info` (with the `X-Api-Key` header when a key is set) — to actually test auth.
- **Websocket**: open the socket **and complete the real handshake** — `server.connection.identify`
  with the required metadata (`client_name`, `version`, `type`, **and `url`** — the identify call
  fails without `url`, per `dinghy-display-mock-vs-reality`) plus the optional `api_key`/`access_token`
  when set — then make a real call (`server.info`). Auth is connection-level, not per-frame; just
  opening the socket is **not** a valid success signal.

Render in the Focus summary state: the two **derived URLs**, two **pass/fail rows**, and a
**classified failure** message:
- `Connection timeout` — printer off / wrong host / not on this network.
- `Connection refused` — wrong port / Moonraker not listening there.
- `Unauthorized` — reached Moonraker but the protected call/identify was rejected →
  "Add this device's IP to Moonraker's `trusted_clients`, or enter an API key."
- `Certificate error` — TLS against a plain-HTTP listener (the secure-on-7125 footgun) or self-signed.

**Save Anyway** always available (don't trap the user if the probe is wrong about their network).

### 5. Input normalization + scheme derivation (the bug fixes)

- **Normalize host on save/test** (adopt Mobileraker's `_normalizeURL`): trim → inject `http://` if
  no scheme present → strip a single trailing slash → strip embedded `user:pass@`. Fixes the
  empty-host hang. *Edge cases (Codex):*
  - **IPv6 literals** — require/accept bracket form `[fe80::1]`; a bare IPv6 in the Host field is
    rejected with clear copy ("wrap IPv6 in brackets").
  - **A path or full URL pasted into Host** — if the Host field contains a path or scheme, either
    redirect the user to the **Advanced URL** field or show an inline error; the plain Host field is
    host(:nothing-else). Path/scheme expression belongs to Advanced URL only.
  - **Advanced URL path semantics are explicit:** it is a Moonraker *base* URL
    (`scheme://host[:port][/route_prefix]`); the app appends `/websocket`, collapses default ports,
    and de-dupes a trailing `/websocket` if the user already typed the full endpoint.
- **Derive scheme:** plain `ws`/`http` from Host+Port; `wss`/`https` + path **only** when the
  Advanced full-URL field is filled (parse its scheme/path). The websocket URL is always built as
  `<httpUri>/websocket` with default-port collapse — one source of truth, never a separate ws field.
- **`.local` warning:** if the Host ends in `.local`, show a gentle inline note (Android mDNS
  resolution is flaky; suggest the numeric IP).
- **Connect-timeout escape:** first-connect must time out to a labeled error, never hang forever
  (covers the case where the user saved without testing).

### 6. Data model migration

- `Profile` gains an optional **`advancedUrl: String?`** (proxy / full override) and **drops the
  user-facing `useSecure` toggle** from the editor.
- One-time migration: existing profiles with `useSecure = true` are rewritten into an `advancedUrl`
  of `https://<host>:<port>` so no saved connection silently breaks. Keep `useSecure` in the stored
  schema if needed for back-compat read, but it is no longer surfaced or independently editable.

### 7. E-stop

This screen is reachable mid-print; the FocusFrame docked-e-stop law applies. **Keep existing
behavior** — do not expand e-stop coverage here. The known `dinghy-estop-modal-gap` (e-stop
unreachable in the connection editor while printing) stays on its existing deferred follow-up unless
the owner asks to address it now.

## Connection — out of scope

- Paid remote-access tunnels (OctoEverywhere / Obico / Tailscale). v1 is LAN-only.
- QR-code key import, custom HTTP headers, cert pinning (Mobileraker built these but hides them;
  not for v1).
- Multi-printer switching (this is the single-connection editor, not the switcher).

---

# Part 2 — System Info device browser

## Goal

Turn the flat 10-row read-only dump into a **device browser**: pick a device (host SBC, mainboard,
sub-boards), see *everything* about it in a space-filling Focus detail, and run the restart/power
actions relevant to that device. Each Field row is one device; the Focus is never a single lonely
line.

## Context

- Current screen: `app/src/main/java/works/mees/dinghy/ui/systeminfo/SystemInformationScreen.kt`
  (44–351). Already Focus/Field, but the **Focus is header-only (renders nothing)** and the Field is
  a flat `ListBlock` of 10 rows mixing static identity and live stats.
- Holder: `app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoHolder.kt` — three flows:
  `identity` (one-shot `machine.system_info`), `procStats` (one-shot `machine.proc_stats`, gives
  throttle + uptime), `live` (~1 Hz `notify_proc_stat_update` push: cpu%, mem, temp).
- Models: `app/src/main/java/works/mees/dinghy/systeminfo/SystemInfoModels.kt`.
- RPC request/response seam: `net/JsonRpcClient.kt` `request(method, params)` (same seam the Move
  endstops view uses). Config parsing precedent exists (`parseHeaterLimits` reads `configfile`).
- We already fetch `printer.info` (for the hostname name-seed) — it also carries the Klipper
  `software_version`.
- Reached via `NavDest.SystemInfo` ← `NavDest.PrinterSettings` ("System Info" row).

## Design

### 1. Device discovery

- The **Host** device = the SBC (existing `system_info` / `proc_stats` data). Always present.
- **MCU devices** = each Klipper MCU object. Use **`printer.objects.list`** (or `configfile`) as the
  object-name **truth source**, then subscribe/query the exact object keys: the primary is `mcu`,
  additional ones are `mcu <name>` (note the space). Including a `[mcu host]` (Linux-process host MCU)
  as its own row, labeled "Host MCU". Do **not** guess names — read them from the live object list.
- Example real shapes: Ender 3 = Host + 1 mainboard (2 rows); Ender 5 = Host (RPi) + SKR3 +
  EZBoard + EBB_CAN (4 rows). Treatment is **uniform** — no special single-MCU collapse.

### 2. Field — device list

One translucent `ListRow` per device: leading icon by device type (Host vs MCU), device name,
trailing a single **live glance value**:
- Host row → health dot (color + shape per THEMING status vocabulary) + CPU %.
- MCU row → MCU load % (or CAN/comms status). **Not** board temp — Klipper exposes no generic MCU
  temperature field (see MCU detail note).

Default selection: **Host** (its detail shown in Focus at rest).

### 3. Focus — selected device detail (space-filling)

A rich multi-field `FocusFrame` card; content by device type:

**Host detail**
- Static: model · distro · kernel · CPU (model · cores) · RAM total.
- Live: CPU load · CPU temp · memory used/total · uptime.
- **Software versions (B):** Klipper version (`printer.info.software_version`) + Moonraker version
  (`server.info.moonraker_version`).
- **Throttle / health detail (B):** decode the Pi `throttled_state` bits into human conditions
  ("under-voltage detected", "temperature-limit throttling", "previously throttled") instead of only
  feeding a chip. **`throttled_state` is `null` off Raspberry Pi** → hide the whole block on non-Pi
  hosts. Use the canonical Pi bit map (bit0 under-voltage now, bit1 freq-capped now, bit2 throttled
  now, bit3 soft-temp-limit now; bit16/17/18/**19** = the "occurred since boot" mirror, previous
  soft-temp-limit = bit 19 = `0x80000`). Do **not** copy Moonraker's docs table verbatim — it has a
  duplicated `1 << 16` line; trust the Pi firmware bit map.

**MCU detail (mainboard & sub-boards)** — exact JSON paths on the `mcu`/`mcu <name>` object:
- Firmware version: `mcu_version`.
- MCU chip / clock: from `mcu_constants` (e.g. `MCU` → `stm32f407`, `CLOCK`).
- Interface (USB / serial / CAN uuid): from `configfile` (the MCU section's `serial`/`canbus_uuid`).
- Live load: `last_stats.mcu_awake`, `last_stats.mcu_task_avg` (+ `mcu_task_stddev`).
- Bandwidth / link health: `last_stats.bytes_write`, `last_stats.bytes_read`,
  `last_stats.bytes_retransmit` (the "is my CAN bus healthy" numbers).
- **No board temperature** unless a sensor is *clearly* mapped to this board — Klipper has no generic
  MCU temp field; temps live on separate `temperature_sensor`/heater objects and mapping a sensor to a
  board needs config parsing. Board-temp inference is explicitly **out of scope** for v1 (the rabbit
  hole Codex flagged); show it only if a trivially-named match exists, else omit.

Every formatter degrades **per-field** to "—" on missing data (preserve the SYS-04 stable-layout
contract; MCUs vary — a CAN toolhead board won't report the same fields as the mainboard).

### 4. Restart / power actions (B) — contextual, docked in Focus

Intent-colored (R5) + `ConfirmGuard`:
- **Host device:** Reboot (red, `machine.reboot`) · Shutdown (red, `machine.shutdown`) ·
  Restart Moonraker (amber, `machine.services.restart service=moonraker`).
- **Mainboard / MCU device:** Firmware Restart (amber, `printer.firmware_restart`) · Restart Klipper
  (amber, **`printer.restart`** — the Klipper soft restart; **not** `machine.services.restart
  service=klipper`, which is a heavier system-service restart). These are **Klipper-global**, so they
  hang on the printer-MCU device but their confirm copy must say so.

**Per-board reset — FINAL (Codex-confirmed):** stock Klipper has **no per-MCU restart**;
`FIRMWARE_RESTART` / `printer.firmware_restart` always restarts Klippy + *all* connected MCUs. This is
settled, not a planning question. The MCU detail therefore shows **one** Firmware Restart labeled
*"restarts all boards"* — no per-board promise.

**Degraded / unavailable actions (Codex):** these actions depend on Moonraker's `provider`,
`available_services`, permissions, and host type (a container host can't reboot the metal). Gate each
button on `machine.system_info` (`provider`, `available_services` / service state) and **disable +
explain** the ones that can't run rather than letting them fail silently. After an *accepted*
reboot/shutdown/restart, **expect the websocket to drop** — surface that as the normal expected
outcome (reconnect/“host is restarting”), not as a connection error.

### 5. Foot bar

`FootButtonBar`: **Back**. Docked e-stop per the FocusFrame law (reachable mid-print).

### 6. Existing System-page power button

The standalone power button on the System page is currently undecided. **Not resolved here** — when
the System page is next touched, decide whether it routes into this device browser or is retired.
No duplication is introduced by this spec (System Info gains the per-device actions; the System-page
button is left as-is).

### 7. Data sourcing (new work — this is the real cost)

- New holder flows for MCU enumeration + per-MCU status (`mcu` objects' `mcu_version`,
  `mcu_constants`, `last_stats`), via the `JsonRpcClient.request` seam and `configfile` parsing.
- New models for a `Device` (sealed: Host vs Mcu) and per-type detail.
- Software versions: extend the existing `printer.info` fetch + add `server.info`.
- Restart/power: command-dispatcher rows for `machine.reboot`, `machine.shutdown`,
  `machine.services.restart`, `printer.firmware_restart`, klippy `RESTART`.

## System Info — out of scope

- Moonraker `update_manager` status (are components up to date) — heavier, separate.
- Network/IP echo of the connection (considered, not selected).
- Editing any host/MCU config from this screen (read-only except the restart/power actions).

---

# Cross-cutting

## Icons

Any new glyphs (device-type icons, restart/power, test-status) must be **owner-selected** — never
auto-picked (`dinghy-never-pick-icons-ask`). New `DinghyIcons` entries need the `val` **and** the
`all` registry list, and ligatures verified via `verify_ligatures.py` (add to the font if missing).

## Testing

- Connection: unit tests for host normalization (bare IP, pasted scheme, trailing slash, userInfo,
  `.local`), scheme derivation (plain vs advanced URL), and the `useSecure→advancedUrl` migration.
  Harden any fake websocket to the real Moonraker identify contract (`dinghy-display-mock-vs-reality`
  — the identify call needs `url`).
- System Info: tests for MCU enumeration from a multi-MCU config (the Ender-5 4-device shape) and
  graceful degrade to "—" on sparse/non-Pi data. Exhaustive `when(device)` branches are
  compile-enforced.
- On-device UAT on **both** flox (Nexus 7, armeabi-v7a) and moto (arm64-v8a) per
  `dinghy-test-devices`; verify against the real Ender 5 (multi-board) and Ender 3 (single-board).

## Risks / notes

- The System Info rebuild is **bigger than "a little B"** — it needs genuine new data sourcing (MCU
  discovery + per-MCU status). Host half is mostly wired already. **Plan it as its own implementation
  plan, separate from the Connection editor** (Codex agreed: Connection = bugfix/model/probe;
  System Info = new data-source + restart actions). Keep board-temp inference out of scope.
- Per-board restart is **impossible** in stock Klipper (global-only) — settled; UI promises only a
  global Firmware Restart.
- Restart/power while printing is dangerous; `ConfirmGuard` + clear copy are mandatory.
- Watch DataStore writes for the Profile migration: route through the process-lifetime write scope,
  not a composition scope (`dinghy-compose-write-scope-cancellation`).
- Verify no stale APK is installed before any UAT (`dinghy-stale-apk-uat-gate`).
