# Spoolman Live Probe

This repo has a small read-only probe for checking Dinghy's planned Spoolman
connection patterns against a real Moonraker + Spoolman setup before Phase 11
starts.

Tool:

- `tools/spoolman-probe.py`

Why it exists:

- The active-spool path Dinghy will use is Moonraker-owned, not direct Spoolman.
- The planned inventory path is Moonraker's Spoolman proxy with
  `use_v2_response=true`.
- The current camera/webcam work branch should not be touched just to gather
  Spoolman fixture truth.
- This produces repo-local JSON output that can be imported into later parser
  tests after review.

## Read-Only Probe

Preferred shape, because it matches Dinghy's existing websocket JSON-RPC spine:

```bash
python3 tools/spoolman-probe.py <moonraker-host> --port 7125 --out docs/commands/spoolman-live-probe.json
```

With auth:

```bash
python3 tools/spoolman-probe.py <moonraker-host> --api-key "$MOONRAKER_API_KEY" --out docs/commands/spoolman-live-probe.json
```

REST-only fallback if `websockets` is not installed:

```bash
python3 tools/spoolman-probe.py <moonraker-host> --http-only --out docs/commands/spoolman-live-probe-http.json
```

Install the websocket dependency WSL-native if needed:

```bash
python3 -m pip install --user websockets
```

The probe does not set or clear the active spool. It reads:

- `server.info`
- `server.spoolman.status`
- `server.spoolman.get_spool_id`
- `server.spoolman.proxy` for `/v1/spool`
- `server.spoolman.proxy` for `/v1/spool/<active id>` when an active id exists

Live captured examples from the local Spoolman and both printers are summarized
in `docs/view_specific_notes/spoolman_live_validation.md`.

## Notification Probe

To verify push payloads, run the probe with a listen window and change the active
spool from Fluidd/Mainsail while it waits:

```bash
python3 tools/spoolman-probe.py <moonraker-host> --listen 30 --out docs/commands/spoolman-live-notify.json
```

Expected notifications:

```json
{"method":"notify_active_spool_set","params":[{"spool_id":1}]}
{"method":"notify_spoolman_status_changed","params":[{"spoolman_connected":false}]}
```

## What To Check In The Output

The `summary` block is the quick signal:

- `has_spoolman_component`: Dinghy capability gate should be true.
- `spoolman_connected`: active UI can make set/clear calls only when true.
- `active_spool_id`: current Moonraker active spool.
- `pending_reports_count`: non-zero means usage/remaining may be stale.
- `proxy_v2_has_response_key`: should be true for the planned proxy parser.
- `sample_spool_count`: proves the inventory list path returns data.
- `spoolman_notifications_seen`: proves notification routing needs to handle
  active-spool/status changes.

The `calls` array keeps the raw request/response shape. API keys are redacted.
Inspect the fixture before committing; inventory names, locations, lot numbers,
and custom `extra` fields may be personally meaningful.

## Importable Follow-Up

Useful later tests can be written from a captured fixture without touching live
printers:

- `SpoolmanStatus` parser handles `spoolman_connected`, `pending_reports`, and
  `spool_id`.
- Moonraker proxy parser handles v2 `{response, error}` envelopes.
- Spool detail parser treats all fields as optional and keeps `extra` as
  `Map<String, String>`.
- Notification router handles `notify_active_spool_set` and
  `notify_spoolman_status_changed`.
