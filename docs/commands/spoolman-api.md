# Spoolman API Command Catalog

**Purpose:** Spoolman `/api/v1/` REST surface plus QR payload semantics from official ReDoc and repository sources.

## Sources

- https://donkie.github.io/Spoolman/
- https://github.com/Donkie/Spoolman/blob/master/client/src/pages/printing/spoolQrCodePrintingDialog.tsx

## Full-detail entries

| ID | Name | Tier | Runtime | Availability | Upstream |
|---|---|---|---|---|---|
| `SPM-GET-field-by-entity-type` | `GET /api/v1/field/{entity_type}` | full | planned_v1 | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-filament` | `GET /api/v1/filament` | full | planned_v1 | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-filament-by-id` | `GET /api/v1/filament/{filament_id}` | full | planned_v1 | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-health` | `GET /api/v1/health` | full | planned_v1 | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-info` | `GET /api/v1/info` | full | planned_v1 | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-material` | `GET /api/v1/material` | full | planned_v1 | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-spool` | `GET /api/v1/spool` | full | planned_v1 | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-spool-by-id` | `GET /api/v1/spool/{spool_id}` | full | planned_v1 | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-vendor` | `GET /api/v1/vendor` | full | planned_v1 | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-QR-SPOOL` | `web+spoolman:s-<id>` | full | planned_v1 | `component_present: spoolman` | https://github.com/Donkie/Spoolman/blob/master/client/src/pages/printing/spoolQrCodePrintingDialog.tsx |

## Light/reference entries

| ID | Name | Tier | Runtime | Availability | Upstream |
|---|---|---|---|---|---|
| `SPM-DELETE-field-by-entity-type-by-key` | `DELETE /api/v1/field/{entity_type}/{key}` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-DELETE-filament-by-id` | `DELETE /api/v1/filament/{filament_id}` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-DELETE-spool-by-id` | `DELETE /api/v1/spool/{spool_id}` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-DELETE-vendor-by-id` | `DELETE /api/v1/vendor/{vendor_id}` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-article-number` | `GET /api/v1/article-number` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-export-filaments` | `GET /api/v1/export/filaments` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-export-spools` | `GET /api/v1/export/spools` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-export-vendors` | `GET /api/v1/export/vendors` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-external-filament` | `GET /api/v1/external/filament` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-external-material` | `GET /api/v1/external/material` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-location` | `GET /api/v1/location` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-lot-number` | `GET /api/v1/lot-number` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-setting` | `GET /api/v1/setting/` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-setting-by-key` | `GET /api/v1/setting/{key}` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-GET-vendor-by-id` | `GET /api/v1/vendor/{vendor_id}` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-PATCH-filament-by-id` | `PATCH /api/v1/filament/{filament_id}` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-PATCH-location-by-location` | `PATCH /api/v1/location/{location}` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-PATCH-spool-by-id` | `PATCH /api/v1/spool/{spool_id}` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-PATCH-vendor-by-id` | `PATCH /api/v1/vendor/{vendor_id}` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-POST-backup` | `POST /api/v1/backup` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-POST-field-by-entity-type-by-key` | `POST /api/v1/field/{entity_type}/{key}` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-POST-filament` | `POST /api/v1/filament` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-POST-setting-by-key` | `POST /api/v1/setting/{key}` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-POST-spool` | `POST /api/v1/spool` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-POST-vendor` | `POST /api/v1/vendor` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-PUT-spool-by-id-measure` | `PUT /api/v1/spool/{spool_id}/measure` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-PUT-spool-by-id-use` | `PUT /api/v1/spool/{spool_id}/use` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
| `SPM-PUT-spool-use` | `PUT /api/v1/spool/{spool_id}/use` | light | reference_only | `component_present: spoolman` | https://donkie.github.io/Spoolman/ |
