---
phase: 11-spool-management-spoolman-camera-qr
plan: 02
subsystem: spool
tags: [wave-1, spoolman, parsers, models, null-safe, proxy-v2, color-d08, filament-metadata]
requires:
  - "11-01: golden corpus + hardened fakes + RED scaffolds (SpoolmanModelParserTest/SpoolmanProxyParserTest/FilePreviewMetadataTest)"
provides:
  - spoolman-models: "Null-safe @Serializable SpoolmanSpool/Filament/Vendor/Status/PendingSpoolmanReport + D-08 color-normalize/multi-split computed vals + normalizeColorHex() free fn"
  - spoolman-parsers: "parseProxyEnvelope (error:null=success, X-Total-Count) + typed parseSpoolmanSpools/Filaments/Vendors + parseSpoolmanMaterials/Locations + hand-walked parseSpoolmanStatus"
  - filepreview-filament-arrays: "FilePreviewMetadata.filamentType/Name/Colors/Weights lifted from gcode metadata"
affects:
  - "11-03+ (SpoolmanClient/router/active-spool card) consume these pure models + parsers"
  - "Print-start gate (later plan) reads the extended FilePreviewMetadata filament arrays"
tech-stack:
  added: []
  patterns:
    - "Webcam @Serializable/@SerialName nullable-or-defaulted idiom applied to Spoolman wire types"
    - "Proxy-v2 envelope walk: error:null=SUCCESS (not no-data), X-Total-Count nullable (absent on non-paginated endpoints)"
    - "mapNotNull per-row decode (one bad row drops); malformed envelope -> empty, never throws"
    - "Hand-walked runCatching status parse (parseSpoolmanStatus) for the bare non-envelope status reply"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/spool/SpoolmanModels.kt
    - app/src/main/java/works/mees/dinghy/spool/SpoolmanParsers.kt
  modified:
    - app/src/main/java/works/mees/dinghy/state/PrintMetadata.kt
    - app/src/test/java/works/mees/dinghy/spool/SpoolmanModelParserTest.kt
    - app/src/test/java/works/mees/dinghy/spool/SpoolmanProxyParserTest.kt
    - app/src/test/java/works/mees/dinghy/state/FilePreviewMetadataTest.kt
decisions:
  - "Spoolman lotNumber mapped @SerialName(\"lot_nr\") (the real Spoolman REST key), not \"lot_number\" — absent in goldens so untested but accurate"
  - "X-Total-Count modeled nullable Int? on ProxyEnvelope: materials/locations endpoints DON'T paginate and omit the header (proven by goldens), so totalCount is null there while pla=7/vendors=6/abs-asa=5 carry it"
  - "proxy-pla golden has 5 rows in response[] but X-Total-Count=7 (server-side pagination cap) — totalCount(7) != rows.size(5) is the CORRECT contract, matching the Wave-0 scaffold's stated assertion"
  - "extra kept as Map<String,String> (Spoolman stores JSON-encoded strings per key) + on-demand extraJson() safe parse; goldens have extra:{} so the map decodes clean, invalid inner JSON only surfaces (null) when extraJson() is called"
  - "error:null detection uses `error == null || error is JsonNull` (the wire sends literal JSON null); a present non-null error => success=false even with a populated response"
  - "Replaced the three Wave-0 fail() RED scaffold bodies with typed assertions in THIS building plan (per the wave0-RED-scaffold-compile discipline) rather than leaving fail() stubs"
metrics:
  duration: ~6m
  completed: 2026-06-04
  tasks: 3
  files: 6
---

# Phase 11 Plan 02: Spoolman Pure-Core Models + Parsers Summary

Built the headless Spoolman data layer — null-safe `@Serializable` models with D-08 defensive color normalization and multi-color split, the Moonraker proxy-v2 envelope parser that correctly reads `error:null` as success and lifts `X-Total-Count` from `response_headers`, the typed spool/filament/vendor/material/location parsers, the hand-walked `server.spoolman.status` parser, and the four `filament_*[]` arrays on `FilePreviewMetadata`. All three Wave-0 RED parser/model scaffolds (`SpoolmanModelParserTest`, `SpoolmanProxyParserTest`, `FilePreviewMetadataTest`) are now GREEN against the verbatim live goldens; no production Android/I/O — fully host-testable.

## What Was Built

### Task 1 — Null-safe models + D-08 color (commit `2447660`)
- `SpoolmanModels.kt`: `SpoolmanSpool` / `SpoolmanFilament` / `SpoolmanVendor` / `SpoolmanStatus` / `PendingSpoolmanReport`, every wire field nullable-or-defaulted with snake_case `@SerialName`, mirroring `Webcam`'s tolerant idiom.
- `normalizeColorHex(raw)` free function + `SpoolmanFilament.normalizedColorHex`/`colorSwatches` computed vals (the `safeRotation`/`hasSnapshot` precedent): accepts with/without `#`, uppercases, requires exactly 6 or 8 hex post-normalize else null (neutral marker); `multi_color_hexes` comma-split → per-color swatch list, falling back to the single normalized color.
- `extra: Map<String,String>` + `SpoolmanSpool.extraJson(key)` safe per-value JSON parse (invalid inner JSON → null, never throws).
- `SpoolmanModelParserTest` GREEN against `proxy-spool3` (envelope-nested) + `direct-spool3-before` (bare REST) goldens.

### Task 2 — Proxy-v2 envelope + status parsers (commit `cbfe404`)
- `SpoolmanParsers.kt`: generic `parseProxyEnvelope(result, strategy)` walks `{response, error, response_headers}` — `error:null`/absent = SUCCESS (RESEARCH anti-pattern, T-11-02-03), `X-Total-Count` (nullable) from headers, `mapNotNull` per row so one bad row drops; malformed/non-object → empty + unsuccessful, never throws (T-11-02-02).
- Typed `parseSpoolmanSpools`/`parseSpoolmanFilaments`/`parseSpoolmanVendors` + string-list `parseSpoolmanMaterials`/`parseSpoolmanLocations`.
- `parseSpoolmanStatus(result)` hand-walks the bare (non-envelope) status object field-by-field via `runCatching`, tolerating missing fields → defaults.
- `SpoolmanProxyParserTest` GREEN: `proxy-pla` → totalCount **7** + **5** rows + `error:null` success; materials(11)/vendors(6)/locations(1)/color-red filaments goldens; bad-row-drops + malformed-empty cases; status golden + sparse/pending/non-object status cases.

### Task 3 — FilePreviewMetadata filament arrays (commit `5b8c64f`)
- Extended `FilePreviewMetadata` with `filamentType`/`filamentName`/`filamentColors` (`List<String>`) + `filamentWeights` (`List<Double>`), all empty-defaulted.
- `parseFilePreviewMetadata` lifts each via null-safe `stringArray`/`doubleArray` helpers (`runCatching{ (result[key] as? JsonArray)?.mapNotNull{...} }.getOrNull().orEmpty()`), mirroring `largestThumbRelPath`. Missing/non-array/garbage → empty list, never throws; `filament_total`/`filament_weight_total` untouched.
- `FilePreviewMetadataTest` GREEN.

## Verification

- `--tests *SpoolmanModelParserTest` → BUILD SUCCESSFUL (GREEN).
- `--tests *SpoolmanProxyParserTest` → BUILD SUCCESSFUL (GREEN).
- `--tests *FilePreviewMetadataTest` → BUILD SUCCESSFUL (GREEN).
- Full `:app:testDebugUnitTest` → 504 tests, 22 failing — **all 22 are the OTHER Wave-0 RED scaffolds owned by later plans** (`PrintStartGateTest`, `QrPayloadParserTest`, `ScanStateMachineTest`, `SpoolmanNotifyRouterTest`), confirmed via the per-class XML results. None of this plan's three target tests fail; no pre-existing green test regressed (those 4 were already RED from 11-01).
- Verification gate: no fresh `Json {}` in `spool/` (only the doc-comment guidance string mentions it) — the shared `MoonrakerJson` is used everywhere.

## Deviations from Plan

None affecting scope. Clarifications resolved during execution:
- **`X-Total-Count` is nullable** — the plan implied it's always present; the materials/locations goldens prove those endpoints don't paginate and omit the header, so `ProxyEnvelope.totalCount: Int?` is null there (pla=7/vendors=6/abs-asa=5 carry it). This is the correct live contract, not a weakening.
- **proxy-pla = 7 total but 5 rows** — the objective text said "7 rows"; the live golden's `response[]` has 5 elements while `X-Total-Count` is 7 (server pagination). `totalCount(7) != rows.size(5)` is the real Spoolman contract and matches the Wave-0 scaffold's documented "5 spool rows / X-Total-Count==7" assertion exactly.
- **RED scaffolds turned into typed assertions in this plan** (per the wave0-RED-scaffold-compile memory) rather than left as `fail()` stubs — the three target tests now carry real golden-driven assertions.

## Known Stubs

None. All three files are production implementations; the remaining `fail()` bodies in the repo belong to the four non-owned Wave-1/2 scaffolds (router/gate/QR/scan), which their own plans (11-03+) implement.

## Self-Check: PASSED

- Files: `SpoolmanModels.kt`, `SpoolmanParsers.kt`, `PrintMetadata.kt` all FOUND.
- Commits: `2447660`, `cbfe404`, `5b8c64f` all FOUND in git log.
- No fresh `Json {}` in `spool/`; all parsers use the shared `MoonrakerJson`.
- Three target tests GREEN; the 22 full-suite failures are exclusively the later-plan RED scaffolds (no regression).
