# Spoolman View And Workflow Notes

This is the local Dinghy planning reference for Spoolman. It is not a plan to
clone the full Spoolman admin UI onto the printer tablet. The useful surface is
printer-workflow control: what spool is loaded, whether the selected file is
safe to print with it, quick spool changes, and QR-assisted load flows.

Primary source for the expanded API details:

- `/mnt/e/claude/personal/github/spoolman/DEVICE_CONTROL_API_CATALOG.md`

Local command catalog references:

- `docs/commands/spoolman-api.md`
- `docs/commands/moonraker-api.md`
- `docs/commands/catalog.json`

Companion product/design brief:

- `docs/view_specific_notes/spoolman_planning_brief.md`

Live probe companion:

- `docs/view_specific_notes/spoolman_live_probe.md`
- `docs/view_specific_notes/spoolman_live_validation.md`

External references checked during the deep pass:

- Moonraker Spoolman integration:
  `https://moonraker.readthedocs.io/en/latest/external_api/integrations/`
- Moonraker JSON-RPC notifications:
  `https://moonraker.readthedocs.io/en/latest/external_api/jsonrpc_notifications/`
- Moonraker `[spoolman]` configuration and Klipper macros:
  `https://github.com/Arksine/moonraker/blob/master/docs/configuration.md`
- Fluidd's comparable Spoolman UX:
  `https://docs.fluidd.xyz/features/spoolman`
- Spoolman FAQ and usage tracking notes:
  `https://github.com/Donkie/Spoolman/wiki/Frequently-Asked-Questions`
  and `https://github.com/Donkie/Spoolman/wiki/Automatic-Filament-Usage-Tracking`
- Spoolman issue/release examples behind the edge-case notes:
  `https://github.com/Donkie/Spoolman/issues/774`
  `https://github.com/Donkie/Spoolman/issues/780`
  `https://github.com/Donkie/Spoolman/issues/781`
  `https://github.com/Donkie/Spoolman/issues/788`
  `https://github.com/Donkie/Spoolman/issues/776`
  `https://github.com/Donkie/Spoolman/issues/783`
  `https://github.com/Donkie/Spoolman/issues/789`

## Architecture Rule

Use Moonraker for active-printer spool state. Use Spoolman for inventory data.

Spoolman owns inventory, filament metadata, weights, locations, extra fields,
and QR payloads. It does not own "this printer currently has spool X loaded."
That state belongs to Moonraker's Spoolman integration.

Core Moonraker active-spool surface:

```http
GET /server/spoolman/status
GET /server/spoolman/spool_id
POST /server/spoolman/spool_id
POST /server/spoolman/proxy
```

`GET /server/spoolman/status` is not only a connectivity probe. It returns:

- `spoolman_connected`: whether Moonraker has a live Spoolman connection.
- `pending_reports`: queued filament-usage reports not yet sent to Spoolman.
- `spool_id`: the currently tracked spool id, or `null`.

If `pending_reports` is non-empty, Dinghy should treat active-spool selection as
known but usage accounting as not fully synced yet.

Moonraker also exposes equivalent JSON-RPC methods for the same calls. Dinghy's
current command catalog entries are JSON-RPC (`server.spoolman.*`), while the
examples in this note use HTTP for readability. The practical split should be:

- active spool get/set/status: JSON-RPC through the existing Moonraker session
- inventory reads/writes: Moonraker Spoolman proxy, or direct Spoolman REST only
  if proxy proves insufficient

Do not use a host embedded in a scanned QR as a direct Spoolman base URL.

Set active spool:

```http
POST /server/spoolman/spool_id
Content-Type: application/json

{"spool_id": 123}
```

Clear active spool / unload:

```http
POST /server/spoolman/spool_id
Content-Type: application/json

{}
```

Inventory/detail reads come from Spoolman:

```http
GET /api/v1/spool
GET /api/v1/spool/{spool_id}
GET /api/v1/filament
GET /api/v1/material
GET /api/v1/vendor
```

If Dinghy should avoid configuring a separate Spoolman base URL, use Moonraker's
proxy from the same printer connection:

```http
POST /server/spoolman/proxy
Content-Type: application/json

{"use_v2_response":true,"request_method":"GET","path":"/v1/spool","query":"allow_archived=false"}
```

Prefer Moonraker proxy `use_v2_response=true`. Version 2 responses wrap the
Spoolman result as `{"response": ..., "error": null}` on success, or
`{"response": null, "error": {"status_code": ..., "message": ...}}` for a
Spoolman-originated error. Version 1 proxy responses are direct/deprecated and
make Moonraker errors harder to distinguish from Spoolman errors.

## Existing Dinghy Integration Points

- `CommandTransport.SpoolmanRest` already exists, but the current dispatcher path
  is JSON-RPC-only. Keep inventory lookup in a small `SpoolmanClient`/proxy
  client instead of forcing it through `CommandDispatcher` too early.
- `AvailabilityPredicate.ComponentPresent("spoolman")` and
  `Capabilities.hasComponent("spoolman")` are already the right gates.
- `SpineHandle` is the right place to expose a session-owned Spoolman facade or
  state holder once implemented.
- `AppContainer` should expose derived `spoolman`/`activeSpool` flows the same
  way it exposes `fileBrowser`, `printMetadata`, and `lastJob`.
- `JsonRpcClient` currently routes only status, Klippy, and gcode notifications.
  A Spoolman phase should route `notify_active_spool_set` and
  `notify_spoolman_status_changed`, or add a generic notification flow.
- Known local catalog gap: `docs/commands/moonraker-api.md` currently lists the
  four `server.spoolman.*` methods but not these two notifications. Add catalog
  entries or phase-local specs before wiring parser tests.
- The Files print-start confirmation is the right place for spool checks before
  dispatching `printer.print.start`.

Notification payloads to support:

```json
{"method":"notify_active_spool_set","params":[{"spool_id":1}]}
{"method":"notify_spoolman_status_changed","params":[{"spoolman_connected":false}]}
```

Moonraker sends notification `params` as arrays, even when there is only one
object.

## Active Spool Card

First useful UI slice: a compact active-spool card on Print Status.

Initial reads:

```http
GET /server/spoolman/status
GET /server/spoolman/spool_id
```

If the active id is non-null, fetch full details:

```http
GET /api/v1/spool/{spool_id}
```

Proxy form:

```json
{"use_v2_response":true,"request_method":"GET","path":"/v1/spool/123"}
```

Display fields from the spool detail response:

- `id`
- `filament.vendor.name`
- `filament.name`
- `filament.material`
- `filament.color_hex`
- `filament.multi_color_hexes`
- `filament.multi_color_direction`
- `filament.settings_extruder_temp`
- `filament.settings_bed_temp`
- `remaining_weight`
- `remaining_length`
- `used_weight`
- `used_length`
- `location`
- `lot_nr`
- `archived`
- `extra`

The Spoolman API omits many null fields, so parsers must treat every field as
optional. The nested `filament` and `vendor` objects are included in
`GET /spool/{id}`, so one detail call is enough for the card.

`extra` fields are `Map<String, String>` where each value is JSON-encoded data.
If Dinghy later reads drying/calibration badges from `extra`, parse the map
value as JSON only for agreed local keys and degrade cleanly when the value is
not valid JSON.

Card actions:

- `Change` -> open spool picker.
- `Scan` -> open QR scanner.
- `Clear` -> `POST /server/spoolman/spool_id` with `{}`.
- `Details` -> expanded spool detail view if/when needed.

Do not use `spool.location` as the source of truth for the active printer spool.
Location is useful inventory context only.

Swatches should be defensive. Accept `color_hex` with or without a leading `#`
and require 6 or 8 hex digits after normalization; otherwise show a neutral
unknown-color marker. If `multi_color_hexes` is present, split the comma-separated
colors and render a compact split swatch instead of assuming a single color.

## Spool Picker And Filters

Default list of available spools:

```http
GET /api/v1/spool?allow_archived=false&sort=filament.material:asc,filament.vendor.name:asc,filament.name:asc,id:asc&limit=50
```

All PLA or material containing PLA:

```http
GET /api/v1/spool?allow_archived=false&filament.material=PLA&sort=filament.vendor.name:asc,filament.name:asc,id:asc&limit=50
```

Spoolman string filters are partial and case-insensitive, so `PLA` should match
values such as `PLA+` as well as plain `PLA`.

That is an API behavior, not automatically the product policy. Material chips
need an explicit choice:

- exact chip: quote the term, for example `filament.material=%22PLA%22`
- family chip: leave it unquoted, so `PLA` catches `PLA+` and similar values

This is family matching, not typo-tolerant fuzzy search. A query like `PLA`
matches `PLA+`, `PLA High Speed Matte`, and `PLA Meta`; a combined typo-like
term such as `PLA+PETG` does not match separate PLA and PETG materials. For a
chip that means more than one material family, send comma-separated terms, for
example `filament.material=ABS,ASA`.

Spoolman's own frontend moved some column filters toward exact matching after
multi-color/material changes, so Dinghy should make this policy visible in tests
instead of relying on the label alone.

Multiple material terms:

```http
GET /api/v1/spool?allow_archived=false&filament.material=PLA,PETG
```

Color family filter (client-side classification):

```http
GET /api/v1/filament?limit=1000
GET /api/v1/spool?allow_archived=false&filament.id=5,6,12&sort=filament.name:asc&limit=50
```

Spool list does not expose a direct color-family filter. Fetch the full filament
library once (`GET /api/v1/filament?limit=1000`, via Moonraker proxy path
`/v1/filament`), classify each filament's color(s) into one of 12 fixed palette
families CLIENT-SIDE using a pure `colorFamily(hex)` function, then fetch spools
by the matching `filament.id=<csv>` (the second step is unchanged). A multicolor
filament (`multi_color_hexes`) matches a family if ANY of its sub-colors
classifies to it.

This replaced Spoolman's server-side `color_similarity_threshold` (CIE76)
matching, which could not reliably find muted or dark colors (e.g. olive green)
because perceptual nearness to a saturated swatch does not equal color family.
See `docs/superpowers/specs/2026-06-18-spool-color-family-filter-design.md` for
the full classification algorithm.

The 12 fixed palette families (the swatch names):

- Black
- White
- Natural
- Gray
- Red
- Orange
- Yellow
- Green
- Blue
- Purple
- Pink
- Brown

The picker shows the selected swatch as a filter chip and lets material chips
combine with it. There is no Multi-color swatch tile — multicolor spools are
matched via their sub-colors classifying to a family.

Dynamic chip data:

```http
GET /api/v1/material
GET /api/v1/vendor?limit=50&sort=name:asc
GET /api/v1/location
```

Through Moonraker proxy, use `/v1/material`, `/v1/vendor`, and `/v1/location`.
Use the real inventory values to populate secondary chips or shortcut menus, but
keep the first row intentionally small. Suggested first-row chips stay
human-oriented: material family, color family, location shortcut, and no-location.

By filament name:

```http
GET /api/v1/spool?allow_archived=false&filament.name=PolyTerra
```

Exact filament name:

```http
GET /api/v1/spool?allow_archived=false&filament.name=%22PolyTerra%20Charcoal%20Black%22
```

By vendor:

```http
GET /api/v1/spool?allow_archived=false&filament.vendor.name=Polymaker
```

Vendor plus material, sorted by lowest remaining amount:

```http
GET /api/v1/spool?allow_archived=false&filament.vendor.name=Sunlu&filament.material=PLA&sort=remaining_weight:asc&limit=50
```

By printer/shelf location:

```http
GET /api/v1/spool?allow_archived=false&location=Printer%201
```

No location:

```http
GET /api/v1/spool?allow_archived=false&location=
```

Recent spools:

```http
GET /api/v1/spool?allow_archived=true&sort=last_used:desc,registered:desc&limit=10
```

Low-remaining sort:

```http
GET /api/v1/spool?allow_archived=false&sort=remaining_weight:asc&limit=50
```

Lot/article number discovery exists, but should be later-phase unless the
operator actually tracks those fields:

```http
GET /api/v1/lot-number
GET /api/v1/article-number
GET /api/v1/spool?allow_archived=false&lot_nr=<term>
GET /api/v1/filament?article_number=<term>
```

Extra fields are discoverable:

```http
GET /api/v1/field/spool
GET /api/v1/field/filament
```

However, they are not general server-side spool-list filters. Treat them as
detail/card badges or later advanced filters only if a local install defines
fields that matter at the printer, such as dried date, storage bin, or calibrated
printer.

Useful picker columns:

- swatch from `filament.color_hex`
- material
- vendor/name
- remaining grams
- location
- last used
- archived marker

On row select:

```http
POST /server/spoolman/spool_id
Content-Type: application/json

{"spool_id": 123}
```

Optional bookkeeping after a successful load:

```http
PATCH /api/v1/spool/123
Content-Type: application/json

{"location":"Printer 1"}
```

## QR Scan Load Flow

Spoolman spool QR labels can encode either the proprietary scheme or a full
Spoolman URL.

```text
web+spoolman:s-<id>
https://<spoolman-host>/spool/show/<id>
```

Example:

```text
web+spoolman:s-123
https://spoolman.local/spool/show/123
```

Recommended parser contract:

- Accept `web+spoolman:s-<id>` case-insensitively, including Spoolman's printed
  uppercase `WEB+SPOOLMAN:S-<id>` variant.
- Accept full `http://` or `https://` labels whose path ends in
  `/spool/show/<id>`. Parse only the id; do not navigate to the URL and do not
  trust the host as a Spoolman base URL.
- Treat other `web+spoolman:` payloads as unsupported Spoolman codes rather than
  valid loads. Only `s-<id>` is a loadable spool id.
- Reject invalid schemes and non-numeric ids with a clear "Not a Spoolman spool
  code" state.

Recommended scanner flow:

1. Open a dedicated scan surface from the Active Spool card or picker.
2. Request `CAMERA` permission if not already granted.
3. Use the intended mounted camera and analyze frames for QR codes.
4. Parse the payload into `spool_id`.
5. Fetch `GET /api/v1/spool/{spool_id}`.
6. Show a confirmation card with material, color, vendor/name, remaining amount,
   location, and archived status.
7. User taps `Load`.
8. Send `POST /server/spoolman/spool_id` with `{"spool_id": id}`.
9. Refresh active-spool state or wait for `notify_active_spool_set`.

Do not auto-load immediately after decoding a QR code. The user should confirm
the spool before Dinghy changes Moonraker's active-spool id.

Error states:

- invalid QR payload -> not a Spoolman spool code
- valid Spoolman scheme but not a spool id, such as `web+spoolman:f-123` if
  encountered -> unsupported Spoolman code, not loaded
- `GET /spool/{id}` returns 404 -> spool not found
- archived spool -> warning or blocked load, depending on later policy
- Spoolman disconnected -> block active-spool changes; optionally keep cached
  display data read-only

Current app gap: Dinghy has no camera permission or scanner dependency yet. The
scanner phase must add a camera stack deliberately and test on old hardware.
Roadmap Phase 11 names ZXing and a GMS-free target, so use ZXing or document a
deliberate replacement. Do not hard-code "front camera" until the physical device
is fixture-proven: on Nexus 7 class hardware the front camera may be fixed-focus
and poor at close labels, while rear/autofocus can be more reliable. Use frame
throttling, but keep analysis resolution high enough to decode the real label
size at the expected scan distance.

Release the camera on pause/background/navigation-away. The scanner is a
short-lived task surface, not a persistent stream.

## Print-Start Spool Gate

The print-start gate belongs in the Files flow, before
`FileBrowserClient.startPrint(filename)` dispatches `printer.print.start`.

Inputs:

- selected file metadata from `server.files.metadata`
- active spool id from Moonraker
- full active spool detail from Spoolman

File metadata already confirmed locally includes:

- `filament_total`
- `filament_weight_total`
- `filament_name[]`
- `filament_type[]`
- `filament_colors[]`
- `filament_temps[]`
- `filament_weights[]`
- first-layer nozzle/bed temps

Current `FilePreviewMetadata` only lifts `filament_total` and
`filament_weight_total`, so the gate phase should extend parsing for material
and per-tool arrays.

Checks:

- no active spool -> require pick or scan before print
- active spool archived -> warn/block
- active spool material does not match slicer `filament_type[]` -> warn
- `remaining_weight` is less than `filament_weight_total` plus safety margin ->
  warn/block
- Moonraker reports `pending_reports` -> warn that prior usage is queued and
  remaining amount may be stale; do not report "usage synced" until clear
- slicer metadata lacks `filament_weight_total` -> skip low-filament weight check
  unless the phase explicitly implements a length/density fallback
- optional: filament temp hints differ significantly from slicer temps -> warn
- optional: custom `extra` fields say not calibrated/dried for this printer ->
  warn

The confirm modal should preserve the existing Files confirmation shape:

- pass -> normal `Print file`
- warning -> show reason and require explicit confirm
- block -> offer `Pick spool`, `Scan`, or `Back`

## Change Spool During Print Or Pause

Useful for runout, M600/manual swaps, and multicolor jobs.

Keep this simple:

1. User opens Active Spool card while printing/paused.
2. User picks/scans the replacement spool.
3. Dinghy sends `POST /server/spoolman/spool_id {"spool_id": id}`.
4. Moonraker reports following usage against the new spool.

Treat Dinghy as one of several possible active-spool mutators. Fluidd/Mainsail,
runout macros, or Klipper macros using Moonraker's `spoolman_set_active_spool`
remote method can change the active spool while Dinghy's picker/card is open.
On `notify_active_spool_set`, reconcile local pending UI with the new id instead
of assuming Dinghy caused the change.

Dinghy should not manually consume filament for the old spool if Moonraker's
Spoolman integration is already reporting usage. Manual `/use` is only for a
control surface that is itself the consumption reporter.

## Optional Correction And Inventory Actions

Measured gross weight correction:

```http
PUT /api/v1/spool/123/measure
Content-Type: application/json

{"weight": 942.5}
```

`weight` is gross grams: filament plus empty spool. Spoolman subtracts
`spool_weight` to calculate remaining filament.

Manual usage adjustment:

```http
PUT /api/v1/spool/123/use
Content-Type: application/json

{"use_weight": 5.3}
```

or:

```http
PUT /api/v1/spool/123/use
Content-Type: application/json

{"use_length": 1200}
```

Only send one of `use_weight` or `use_length`.

These are later features. For mounted-printer use, active-spool selection and
print-start checks are higher leverage than inventory administration.

Potential later extras:

- measured remaining weight entry
- one-tap "mark dried" stored in spool or filament `extra`
- custom calibration/profile warnings from `extra`
- per-tool/lane spool assignment for multicolor/toolchanger systems
- manufacturer UPC/EAN inventory creation, if the tablet becomes an inventory
  station

## Suggested Data Model

Keep parsing null-safe and headless.

```kotlin
data class SpoolmanStatus(
    val spoolmanConnected: Boolean,
    val activeSpoolId: Int?,
    val pendingReports: List<PendingSpoolmanReport>,
)

data class PendingSpoolmanReport(
    val spoolId: Int,
    val filamentUsedMm: Double,
)

data class SpoolmanSpool(
    val id: Int,
    val filament: SpoolmanFilament?,
    val remainingWeight: Double?,
    val remainingLength: Double?,
    val usedWeight: Double?,
    val usedLength: Double?,
    val location: String?,
    val lotNumber: String?,
    val archived: Boolean,
    val extra: Map<String, String>,
)

data class SpoolmanFilament(
    val id: Int?,
    val name: String?,
    val material: String?,
    val colorHex: String?,
    val multiColorHexes: String?,
    val multiColorDirection: String?,
    val vendor: SpoolmanVendor?,
    val settingsExtruderTemp: Int?,
    val settingsBedTemp: Int?,
)

data class SpoolmanVendor(
    val id: Int?,
    val name: String?,
)
```

Suggested UI state:

- unavailable: Moonraker has no `spoolman` component
- disconnected: component present, Spoolman not connected
- no active spool
- active spool loading
- active spool loaded
- usage reports pending
- active spool changed externally
- picker loading/results/error
- scan active/decoded/error
- set/clear pending

## Recommended Implementation Order

1. Add Moonraker Spoolman command specs:
   `server.spoolman.status`, `server.spoolman.get_spool_id`,
   `server.spoolman.post_spool_id`, and `server.spoolman.proxy`.
2. Add a small `SpoolmanClient` that uses Moonraker proxy first with
   `use_v2_response=true`. Direct Spoolman REST can wait unless proxy proves
   insufficient.
3. Add fixture/probe capture for `server.spoolman.status`,
   `notify_active_spool_set`, and `notify_spoolman_status_changed` before parser
   implementation.
4. Add null-safe parsers for `SpoolmanStatus`, `SpoolmanSpool`, filament, and
   vendor.
5. Add notification routing for active-spool/status changes.
6. Add active-spool card on Print Status.
7. Add manual picker with material/vendor/name/location filters and color
   palette swatches.
8. Add clear/unload and change-spool actions.
9. Add QR parser tests for `web+spoolman:s-<id>`, uppercase
   `WEB+SPOOLMAN:S-<id>`, full `/spool/show/<id>` URLs, and rejected payloads.
10. Add camera scan surface and load confirmation.
11. Add print-start gate in Files.
12. Add optional measured-weight/correction actions.

## Non-Goals For The First Spoolman Phase

- Full Spoolman CRUD admin UI.
- Creating filaments/vendors from the printer screen.
- Bulk inventory intake.
- Manufacturer barcode inventory creation from UPC/EAN labels.
- NFC/OpenPrintTag/RFID intake.
- Manual consumption reporting while Moonraker is already configured to report
  active-spool usage.
- Treating `location` as active-printer state.
