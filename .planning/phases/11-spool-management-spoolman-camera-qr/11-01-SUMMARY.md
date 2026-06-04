---
phase: 11-spool-management-spoolman-camera-qr
plan: 01
subsystem: spool
tags: [wave-0, test-scaffold, goldens, hardened-fakes, mock-vs-reality, spoolman, qr, camera, catalog]
requires: []
provides:
  - golden-corpus: "10 verbatim live Spoolman fixtures on the test classpath (app/src/test/resources/golden/spoolman-live-*.json)"
  - hardened-fakes: "FakeSpoolmanClient (proxy-v2 envelope + empty-envelope miss path), FakeMoonrakerSpoolmanSession (status/get/post + 1-element notify injectors)"
  - red-scaffolds: "7 JVM-unit + 1 instrumented compile-clean RED stubs for every Wave-0 VALIDATION seam"
  - catalog: "notify_active_spool_set + notify_spoolman_status_changed documented in docs/commands/moonraker-api.md"
affects:
  - "Wave 1 (parsers/router/models) and Wave 2 (QR/gate/scan) conform real types to these fakes/scaffolds"
tech-stack:
  added: []
  patterns:
    - "Golden corpus lifted verbatim from live captures (no transformation = no weakening)"
    - "Hardened fake: exact wire envelope + hardened miss path (empty-envelope, not catch-all)"
    - "Wave-0 RED stubs: fail() bodies, zero unbuilt-symbol refs, dual compile gate"
key-files:
  created:
    - app/src/test/resources/golden/spoolman-live-ender5-proxy-pla.json
    - app/src/test/resources/golden/spoolman-live-ender5-proxy-spool3.json
    - app/src/test/resources/golden/spoolman-live-direct-spool3-before.json
    - app/src/test/resources/golden/spoolman-live-ender5-notify.json
    - app/src/test/resources/golden/spoolman-live-ender5-status-before-set.json
    - app/src/test/resources/golden/spoolman-live-ender5-proxy-materials.json
    - app/src/test/resources/golden/spoolman-live-ender5-proxy-vendors.json
    - app/src/test/resources/golden/spoolman-live-ender5-proxy-locations.json
    - app/src/test/resources/golden/spoolman-live-ender5-proxy-abs-asa.json
    - app/src/test/resources/golden/spoolman-live-ender5-proxy-color-red-filaments.json
    - app/src/test/java/works/mees/dinghy/spool/FakeSpoolmanClient.kt
    - app/src/test/java/works/mees/dinghy/spool/FakeMoonrakerSpoolmanSession.kt
    - app/src/test/java/works/mees/dinghy/spool/SpoolmanProxyParserTest.kt
    - app/src/test/java/works/mees/dinghy/spool/SpoolmanModelParserTest.kt
    - app/src/test/java/works/mees/dinghy/spool/SpoolmanNotifyRouterTest.kt
    - app/src/test/java/works/mees/dinghy/spool/QrPayloadParserTest.kt
    - app/src/test/java/works/mees/dinghy/spool/PrintStartGateTest.kt
    - app/src/test/java/works/mees/dinghy/spool/ScanStateMachineTest.kt
    - app/src/test/java/works/mees/dinghy/state/FilePreviewMetadataTest.kt
    - app/src/androidTest/java/works/mees/dinghy/spool/ScanSurfaceLifecycleTest.kt
  modified:
    - docs/commands/moonraker-api.md
decisions:
  - "FilePreviewMetadataTest created fresh in state/ (no prior file existed — only PrintMetadataParseTest, left untouched/green)"
  - "Plan filename FakeMoonrakerSpoolmanSession.kt used (frontmatter authoritative over VALIDATION's FakeMoonrakerSession alias)"
  - "ZxingDecodeVersionTest NOT scaffolded — out of this plan's files_modified (deferred to the ZXing-dependency plan, 11-05)"
  - "notify_spoolman_status_changed documented as {spoolman_connected: Boolean} per the live view-notes shape (not spool_id)"
metrics:
  duration: ~5m
  completed: 2026-06-04
  tasks: 3
  files: 21
---

# Phase 11 Plan 01: Spool Wave-0 Foundation Summary

Laid the entire phase's Wave-0 RED foundation: 10 verbatim live Spoolman fixtures copied into the test golden corpus, two hardened fakes (`FakeSpoolmanClient` / `FakeMoonrakerSpoolmanSession`) that replicate the EXACT Moonraker proxy-v2 envelope and 1-element notify-array contracts from the live captures, eight compile-clean failing test scaffolds (7 JVM-unit + 1 instrumented), and the closed catalog gap documenting the two Spoolman push notifications — all green-compile, all RED-on-run, zero references to unbuilt production symbols.

## What Was Built

### Task 1 — Goldens + catalog gap (commit `223a56d`)
- Copied the 10 named `docs/commands/spoolman-live-*.json` captures **byte-identical** into `app/src/test/resources/golden/` (verified via `diff -q` — all 10 identical). These load via `GoldenFixtures.raw("spoolman-live-...")`.
- Added a **Server-push notifications** section to `docs/commands/moonraker-api.md` documenting `notify_active_spool_set` (`params[0].spool_id`) and `notify_spoolman_status_changed` (`params[0].spoolman_connected`), both noting the 1-element-`params`-array contract and that unrelated `notify_proc_stat_update` frames must be ignored.

### Task 2 — Hardened fakes (commit `cc830e3`)
- `FakeSpoolmanClient`: every read returns the EXACT `{response, error, response_headers}` proxy-v2 envelope (the golden's `result` object), with `X-Total-Count` drawn from the seeded golden (proxy-pla → "7"). An **unmatched query returns the well-formed empty-envelope** (`X-Total-Count "0"`, `response []`), never a lenient catch-all — so a parser that ignores the query string fails the test. Read surface: `getSpool`, `listSpools`, `listFilaments`, `listMaterials`, `listVendors`, `listLocations`, `measureSpool`.
- `FakeMoonrakerSpoolmanSession`: `status()` returns the verbatim status-before-set golden (`spool_id 5`, `pending_reports []`); `postSpoolId({})` clears to null (D-13); `injectActiveSpoolSet`/`injectSpoolmanStatusChanged` emit frames whose `params` is a **1-element array**.
- Both parse with the shared `MoonrakerJson` (never a fresh `Json {}`); zero unbuilt-symbol references.

### Task 3 — RED scaffolds (commit `be5d84c`)
- 7 JVM-unit scaffolds (`SpoolmanProxyParserTest`, `SpoolmanModelParserTest`, `SpoolmanNotifyRouterTest`, `QrPayloadParserTest`, `PrintStartGateTest`, `ScanStateMachineTest`, `FilePreviewMetadataTest`) — each `fail("RED: ...")` with the target assertion + golden source documented in the class KDoc.
- 1 instrumented scaffold (`ScanSurfaceLifecycleTest`, androidTest spool/) — SPOOL-03/06 camera-release + no-camera-degrade stub, **zero reference to the unbuilt `ScanSurface.kt`** (compiled in Wave 0, run via `connectedDebugAndroidTest` once 11-07 ships ScanSurface).

## Verification

- `:app:compileDebugUnitTestKotlin` → **BUILD SUCCESSFUL** (whole unit sourceset compiles).
- `:app:compileDebugAndroidTestKotlin` → **BUILD SUCCESSFUL** (instrumented stub did NOT brick the androidTest sourceset).
- `:app:testDebugUnitTest --tests *SpoolmanProxyParserTest` → ran **RED** with `java.lang.AssertionError` at the `fail()` lines (a real run, not a compile error) — confirming the Wave-0-RED-must-compile rule holds.
- 10 goldens present and byte-identical to source; both fakes return live-accurate envelopes; catalog gap closed (4× `notify_active_spool_set`, 3× `notify_spoolman_status_changed` mentions in the doc).
- `CommandCatalogDriftTest` unaffected — it enforces against `docs/commands/catalog.json` / `printer-matrix.json` JSON sidecars, explicitly NOT the Markdown reference file I edited.

## Deviations from Plan

None affecting scope. Two clarifications resolved during execution (both pre-anticipated by the plan):
- **No prior `FilePreviewMetadataTest`** existed (only `PrintMetadataParseTest`), so it was created fresh in `state/` rather than extended; the existing green metadata suite was left untouched.
- **`notify_spoolman_status_changed` payload** documented as `{spoolman_connected: Boolean}` per the live `docs/view_specific_notes/spoolman.md` shape, not `{spool_id}` (the plan's task text generalized "spool_id"; the live contract is connection state for this notification).
- **`ZxingDecodeVersionTest`** (named in VALIDATION but absent from this plan's `files_modified`) was intentionally NOT scaffolded — it belongs with the ZXing dependency (plan 11-05), where the 3.3.3 pin lands.

## Known Stubs

All eight test scaffolds are intentional Wave-0 RED stubs (`fail()` bodies) — these are the planned failing seams Waves 1-2 implement against, not production stubs. No production-code stubs introduced (this plan adds test-only Kotlin + JSON).

## Self-Check: PASSED

- Goldens: all 10 `spoolman-live-*.json` present under `app/src/test/resources/golden/` (FOUND).
- Fakes + scaffolds: all 10 Kotlin files present (FOUND); both sourcesets compile.
- Commits: `223a56d`, `cc830e3`, `be5d84c` all present in `git log` (FOUND).
- Catalog: both notifications documented in `docs/commands/moonraker-api.md` (FOUND).
