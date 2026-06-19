# Spoolman Live Validation

Captured against the local install on 2026-06-04 UTC / 2026-06-03
America/Chicago.

Hosts:

- Spoolman: `http://192.168.1.253:7912`
- Ender 5 Plus Moonraker: `http://192.168.1.120:7125`
- Ender 3 Pro Moonraker: `http://192.168.1.121:7125`

No delete calls were made. The only direct Spoolman inventory write was a
temporary `remaining_weight` change on spool `3`, followed by an immediate
restore from the saved pre-write record. The only Moonraker write was a temporary
Ender 5 active-spool change from `5` to `3`, followed by restore to `5`.

## Fixture Files

- `docs/commands/spoolman-live-ender5.json`
- `docs/commands/spoolman-live-ender3.json`
- `docs/commands/spoolman-live-ender5-notify.json`
- `docs/commands/spoolman-live-ender5-status-before-set.json`
- `docs/commands/spoolman-live-ender5-status-after-restore.json`
- `docs/commands/spoolman-live-ender5-proxy-pla.json`
- `docs/commands/spoolman-live-ender5-proxy-spool3.json`
- `docs/commands/spoolman-live-ender5-proxy-color-red-filaments.json`
- `docs/commands/spoolman-live-ender5-proxy-color-red-spools.json`
- `docs/commands/spoolman-live-ender5-proxy-materials.json`
- `docs/commands/spoolman-live-ender5-proxy-vendors.json`
- `docs/commands/spoolman-live-ender5-proxy-locations.json`
- `docs/commands/spoolman-live-ender5-proxy-no-location.json`
- `docs/commands/spoolman-live-ender5-proxy-sunlu-pla-by-weight.json`
- `docs/commands/spoolman-live-ender5-proxy-recent-spools.json`
- `docs/commands/spoolman-live-ender5-proxy-abs-asa.json`
- `docs/commands/spoolman-live-ender5-proxy-exact-pla.json`
- `docs/commands/spoolman-live-ender5-proxy-lot-numbers.json`
- `docs/commands/spoolman-live-ender5-proxy-article-numbers.json`
- `docs/commands/spoolman-live-ender5-proxy-fields-spool.json`
- `docs/commands/spoolman-live-ender5-proxy-fields-filament.json`
- `docs/commands/spoolman-live-direct-spool3-before.json`
- `docs/commands/spoolman-live-direct-spool3-after-restore.json`

## Direct Spoolman API

Health and version:

```bash
GET /api/v1/info
GET /api/v1/health
```

Observed:

- version `0.22.1`
- `git_commit` `1888240`
- SQLite backend
- health response `{"status":"healthy"}`

Important route detail:

- Direct Spoolman API routes are under `/api/v1`.
- Direct `GET /v1/spool` serves the SPA HTML shell on this install.
- The OpenAPI document for API paths is `/api/v1/openapi.json`.
- Moonraker's Spoolman proxy still expects proxy paths like `/v1/spool`.

PLA filter query:

```bash
GET /api/v1/spool?filament.material=PLA&allow_archived=false&limit=5&sort=filament.name:asc
```

Observed:

- HTTP `200`
- response body is a JSON array, not a pagination object
- `x-total-count: 7`
- first rows: Black, CMYK Yellow, Cherry Red, Olive Green, Red

Name filter query:

```bash
GET /api/v1/spool?filament.name=red&limit=10&sort=filament.name:asc
```

Observed:

- HTTP `200`
- `x-total-count: 2`
- rows: Cherry Red and Red

Material family filter query:

```bash
GET /api/v1/spool?filament.material=PLA&allow_archived=false&limit=50
```

Observed:

- HTTP `200`
- `x-total-count: 7`
- matches `PLA High Speed Matte`, `PLA+ 2.0`, `PLA Meta`, and `PLA+`

Non-matching combined material term:

```bash
GET /api/v1/spool?filament.material=PLA%2BPETG&allow_archived=false&limit=10
```

Observed:

- HTTP `200`
- `x-total-count: 0`

Planning consequence: material search is partial case-insensitive family
matching, not typo-tolerant fuzzy matching. Use comma-separated terms for
multi-family chips.

Filter discovery and shortcut endpoints through Moonraker proxy:

```bash
POST /server/spoolman/proxy {"request_method":"GET","path":"/v1/material","use_v2_response":true}
POST /server/spoolman/proxy {"request_method":"GET","path":"/v1/vendor","query":"limit=50&sort=name:asc","use_v2_response":true}
POST /server/spoolman/proxy {"request_method":"GET","path":"/v1/location","use_v2_response":true}
```

Observed materials:

```text
PLA High Speed Matte, PLA+ 2.0, PLA Meta, TPU, TPU 95A,
ABS - Translucent, ABS, PLA, PETG, PLA+/Pro, PLA+
```

Observed locations:

```text
Ender 5
```

Observed vendors:

```text
ABCWavePrint, Aceaddity, Jayo, Polymaker, Sunlu, yxpolyer
```

No-location query:

```bash
POST /server/spoolman/proxy
{
  "request_method": "GET",
  "path": "/v1/spool",
  "query": "location=&allow_archived=false&limit=10&sort=filament.name:asc",
  "use_v2_response": true
}
```

Observed:

- `X-Total-Count: 13`
- rows have `location: null`

Vendor + material + low-remaining query:

```bash
POST /server/spoolman/proxy
{
  "request_method": "GET",
  "path": "/v1/spool",
  "query": "filament.vendor.name=Sunlu&filament.material=PLA&allow_archived=false&sort=remaining_weight:asc&limit=20",
  "use_v2_response": true
}
```

Observed:

- `X-Total-Count: 7`
- first row is Sunlu White PLA+ at `275.76788317018827g` remaining

Recent-spools query:

```bash
POST /server/spoolman/proxy
{
  "request_method": "GET",
  "path": "/v1/spool",
  "query": "allow_archived=true&sort=last_used:desc,registered:desc&limit=10",
  "use_v2_response": true
}
```

Observed first rows:

- Olive Green, last used `2026-06-04T00:31:33Z`
- Cherry Red, last used `2026-05-30T23:35:44Z`
- Yellow TPU, last used `2026-05-30T21:52:58Z`

ABS/ASA comma-term query:

```bash
POST /server/spoolman/proxy
{
  "request_method": "GET",
  "path": "/v1/spool",
  "query": "filament.material=ABS,ASA&allow_archived=false&limit=20",
  "use_v2_response": true
}
```

Observed:

- `X-Total-Count: 5`
- matches `ABS - Translucent` and `ABS`

Exact material query:

```bash
POST /server/spoolman/proxy
{
  "request_method": "GET",
  "path": "/v1/spool",
  "query": "filament.material=%22PLA%22&allow_archived=false&limit=20",
  "use_v2_response": true
}
```

Observed:

- `X-Total-Count: 0`

Lot/article/extra-field discovery:

- `/v1/lot-number`: empty list
- `/v1/article-number`: empty list
- `/v1/field/spool`: empty list
- `/v1/field/filament`: empty list

Planning consequence: these endpoints exist, but they should be lower-priority
filters until an install actually uses them.

Spool detail:

```bash
GET /api/v1/spool/3
```

Observed key fields:

- `id: 3`
- filament `CMYK Yellow`
- material `PLA+ 2.0`
- `remaining_weight: 579.0`
- `used_weight: 421.0`
- `color_hex: F6FA00`

## Direct Spoolman Write/Restore

Saved pre-write fixture:

- `docs/commands/spoolman-live-direct-spool3-before.json`

Temporary write:

```bash
PATCH /api/v1/spool/3
{"remaining_weight": 579.1}
```

Observed response:

```json
{"id":3,"remaining_weight":579.1,"used_weight":420.9,"initial_weight":1000.0}
```

Restore:

```bash
PATCH /api/v1/spool/3
{"remaining_weight": 579.0}
```

Observed final `GET /api/v1/spool/3`:

```json
{"id":3,"remaining_weight":579.0,"used_weight":421.0,"initial_weight":1000.0}
```

Parser/UX note: setting `remaining_weight` changes `used_weight` as
`initial_weight - remaining_weight`. A Dinghy correction UI should show that the
two fields are linked rather than treating them as independent values.

## Moonraker Read Probe

Tool:

```bash
python3 tools/spoolman-probe.py 192.168.1.120 --out docs/commands/spoolman-live-ender5.json
python3 tools/spoolman-probe.py 192.168.1.121 --out docs/commands/spoolman-live-ender3.json
```

Ender 5 summary:

```json
{
  "has_spoolman_component": true,
  "spoolman_connected": true,
  "active_spool_id": 5,
  "pending_reports_count": 0,
  "proxy_v2_has_response_key": true,
  "proxy_v2_error": null,
  "sample_spool_count": 5
}
```

Ender 3 summary:

```json
{
  "has_spoolman_component": true,
  "spoolman_connected": true,
  "active_spool_id": 1,
  "pending_reports_count": 0,
  "proxy_v2_has_response_key": true,
  "proxy_v2_error": null,
  "sample_spool_count": 5
}
```

Planning consequence: active spool is per-printer Moonraker state. The same
Spoolman server can legitimately report different active spools through
different printer hosts.

## Moonraker Proxy Query

Validated against Ender 5:

```bash
POST /server/spoolman/proxy
{
  "request_method": "GET",
  "path": "/v1/spool",
  "query": "filament.material=PLA&allow_archived=false&limit=5&sort=filament.name:asc",
  "use_v2_response": true
}
```

Observed:

- response envelope is under REST `result`
- proxy v2 payload is `{ "response": [...], "error": null, "response_headers": {...} }`
- `response_headers["X-Total-Count"] == "7"`
- rows match the direct Spoolman PLA query

Validated QR/detail lookup shape through the proxy:

```bash
POST /server/spoolman/proxy
{
  "request_method": "GET",
  "path": "/v1/spool/3",
  "use_v2_response": true
}
```

Observed:

```json
{
  "error": null,
  "spool": {
    "id": 3,
    "name": "CMYK Yellow",
    "material": "PLA+ 2.0",
    "remaining_weight": 579.0,
    "used_weight": 421.0,
    "color_hex": "F6FA00"
  }
}
```

Implementation note: query strings with dotted keys such as
`filament.material` must be encoded by the client API layer if it does not accept
raw dots in query names.

## Moonraker Proxy Color Flow

**HISTORICAL (pre-2026-06-18):** color filtering moved from Spoolman's server-side
`color_similarity_threshold` to client-side hue-family classification — see
`docs/superpowers/specs/2026-06-18-spool-color-family-filter-design.md`.

Validated against Ender 5 with the red palette swatch:

```bash
POST /server/spoolman/proxy
{
  "request_method": "GET",
  "path": "/v1/filament",
  "query": "color_hex=ff0000&color_similarity_threshold=20&limit=20",
  "use_v2_response": true
}
```

Observed matched filament ids:

```text
5,6,8,9,12,13,17
```

Then fetched spools by those filament ids:

```bash
POST /server/spoolman/proxy
{
  "request_method": "GET",
  "path": "/v1/spool",
  "query": "filament.id=5,6,8,9,12,13,17&allow_archived=false&limit=50&sort=filament.name:asc",
  "use_v2_response": true
}
```

Observed unarchived spool rows:

- Cherry Red, `E63034`
- Orange, `ff5100`
- PolyLite ABS Orange, `F57517`
- Red, `ff0000`

Planning consequence: let Spoolman own close-enough color matching, then use the
returned filament ids to get real spool rows. The endpoint can match colors a
human might not expect for a named swatch, so Dinghy should display the actual
swatch/color on every result row.

## Moonraker Active-Spool Set/Restore

Saved current Ender 5 status:

```json
{"spoolman_connected":true,"pending_reports":[],"spool_id":5}
```

Temporary set:

```bash
POST /server/spoolman/spool_id
{"spool_id": 3}
```

Observed:

```json
{"result":{"spool_id":3}}
```

Restore:

```bash
POST /server/spoolman/spool_id
{"spool_id": 5}
```

Observed:

```json
{"result":{"spool_id":5}}
```

Final Ender 5 status:

```json
{"spoolman_connected":true,"pending_reports":[],"spool_id":5}
```

Captured notifications:

```json
{"jsonrpc":"2.0","method":"notify_active_spool_set","params":[{"spool_id":3}]}
{"jsonrpc":"2.0","method":"notify_active_spool_set","params":[{"spool_id":5}]}
```

The listener also received unrelated Moonraker `notify_proc_stat_update` events.
The Dinghy notification router should ignore unrelated `method` values and only
fan out Spoolman-specific events to the Spoolman feature state.

## QR Source Confirmation

Local Spoolman source confirms QR values are generated as:

```text
WEB+SPOOLMAN:S-{id}
https?://<host>/spool/show/{id}
```

The scanner accepts both case-insensitively and navigates to `/spool/show/{id}`.
Dinghy should parse these locally, fetch the spool by id through Moonraker proxy,
and never trust the QR URL host as the Spoolman base URL.
