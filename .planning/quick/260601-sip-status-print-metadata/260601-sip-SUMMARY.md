---
phase: quick-260601-sip
plan: 01
subsystem: ui
tags: [moonraker, metadata, coil3, thumbnail, print-status, statflow, jsonrpc]

requires:
  - phase: 04 (Print Status home, Inc 1)
    provides: PrintStatusScreen with `// until Inc 2` dashed cells + ProgressRing/StatGrid scaffold
  - phase: 05-03 (one-shot handshake reads)
    provides: the pure-mapper + StateFlow-on-store + injectable-rpc one-shot-read pattern this mirrors
provides:
  - Pure PrintMetadata model + parsePrintMetadata mapper + thumbnailUrl builder (host-testable)
  - One-shot-per-filename PrintMetadataHolder (StateFlow<PrintMetadata?>)
  - SpineHandle.httpBase + SpineHandle.metadata; AppContainer.printMetadata + AppContainer.httpBase
  - PrintStatusScreen wired: ring gcode thumbnail + Layer total fallback + Z final-height + Remaining ETA
affects: [Phase 6 Files/Print, future Tune/Finish-by surfaces, any per-file metadata consumer]

tech-stack:
  added: []
  patterns:
    - "One-shot-per-key reactive holder: cache lastKey, fetch only on transition to a new non-blank key, clear+reset on idle"
    - "Pure JSON mapper (catalog-strict, null-safe, no !!) decoupled from a thin StateFlow holder + injectable fetch seam"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/state/PrintMetadata.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintMetadataHolder.kt
    - app/src/test/java/works/mees/dinghy/state/PrintMetadataParseTest.kt
    - app/src/test/java/works/mees/dinghy/ui/printstatus/PrintMetadataHolderTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/net/JsonRpc.kt
    - app/src/main/java/works/mees/dinghy/di/SpineHandle.kt
    - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
    - app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
    - app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt
    - app/src/androidTest/java/works/mees/dinghy/ui/ShellPresenceTest.kt

key-decisions:
  - "Metadata read ONCE per active filename (keyed), re-fetched only on filename change; idle clears + resets the key so the next print re-fetches"
  - "Build STRICTLY against docs/moonraker-capabilities.md: only layer_count/object_height/estimated_time/thumbnails read; E5 sample omits object_height -> nullable, degrades to dashed"
  - "Remaining ETA = metadata estimated_time x (1 - LIVE progress); progress comes from virtual_sdcard/display_status (PrinterState.progress), NOT a metadata field"
  - "Printing-without-thumbnail leaves the ring center EMPTY (never Benchy mid-print); Benchy is idle-only"

patterns-established:
  - "PrintMetadataHolder: session-scoped one-shot-per-filename fetcher mirroring the 05-03 best-effort runCatching + StateFlow discipline"

requirements-completed: [SHELL-04]

duration: 15min
completed: 2026-06-01
---

# Quick 260601-sip: Status/Home Print Metadata (Inc 2) Summary

**Lit up the Status-home's previously-dashed cells off ONE cached `server.files.metadata` read keyed on the active filename — gcode thumbnail in the ProgressRing, total-layer fallback, object_height final-height context, and a slicer-estimate Remaining ETA — all built strictly against the confirmed Moonraker catalog.**

## Performance

- **Duration:** ~15 min
- **Tasks:** 3 / 3
- **Files created:** 4 · **Files modified:** 7

## Accomplishments

### Task 1 — Pure model + parser + thumbnail-URL builder (`e8ffd74`)
- `PrintMetadata` data class + pure `parsePrintMetadata(JsonObject)` reading ONLY the four catalog-confirmed keys (`layer_count`, `object_height`, `estimated_time`, `thumbnails[].{width,relative_path}`), null-safe throughout (no `!!`), largest-thumbnail-by-width pick.
- Pure `thumbnailUrl(httpBase, gcodeFilename, relPath)` — per-segment URL-encoding (spaces → `%20`), root-file vs subdirectory handling, `/` separators in relPath preserved.
- `JsonRpcMethods.FILES_METADATA = "server.files.metadata"`.
- 7 host-unit cases (faithful E5/E3 fixtures parsed via `MoonrakerJson`) — including the E5-omits-`object_height` → null path and a 32/48-only "no fabricated 300" case.

### Task 2 — Reactive one-shot-per-filename holder + session wiring (`e08a29d`)
- `PrintMetadataHolder(scope, printerState, fetch)` exposing `StateFlow<PrintMetadata?>`: fetches once per active filename, re-keys on change, clears + resets `lastKey` on idle (so the same file re-fetches next print), best-effort null-safe (a null/failed fetch leaves metadata null, never throws into the collector).
- `SpineHandle` gained `httpBase` + `metadata`; `AppContainer` exposes derived `printMetadata` + `httpBase` flows; `MoonrakerService.buildSpineAndLaunch` builds the holder via `rpc.request(FILES_METADATA, {filename})`.
- 4 `runTest` cases (counting injected fetch + `MutableStateFlow<PrinterState>`) prove once-per-filename, re-fetch-on-change, clear-on-idle-then-re-fetch, and null-fetch survival.

### Task 3 — Screen wiring (`5c1302b`)
- Ring center binds the gcode thumbnail (Coil 3 `AsyncImage`, default loader) while printing when a thumbnail URL is available; idle keeps Benchy; printing-without-thumb leaves the center empty.
- Layer total denominator = live `total_layer` ?? metadata `layer_count` ?? "—" (new `totalLayers()` helper, used in both the Focus Z/Layer line and the grid).
- Z cell inactive line = metadata `object_height` (final-height context), "—" when absent.
- Remaining cell = `estimated_time × (1 − live progress)` → H:MM via `fmtDuration`, recolored `t.text` when real / `t.text3` when "—".

## Verification

- `:app:testReleaseUnitTest --tests …PrintMetadataParseTest` — BUILD SUCCESSFUL (7 cases).
- `:app:testReleaseUnitTest --tests …PrintMetadataHolderTest` — BUILD SUCCESSFUL (4 cases).
- `:app:compileReleaseKotlin :app:testReleaseUnitTest` — BUILD SUCCESSFUL (full release unit suite).
- Token purity: `grep "Color(" PrintStatusScreen.kt` → none (all color via `LocalTokens`).
- Catalog fidelity: `grep` of `PrintMetadata.kt` confirms only `layer_count` / `object_height` / `estimated_time` / `thumbnails` (+ `width`/`relative_path`) read — no invented field.
- On-device thumbnail/ETA visual check is Matthew-driven (live Ender 5 Plus + flox) — NOT part of automated verify.

## Deviations from Plan

### Auto-fixed (Rule 3 — blocking compile)
**Added the two new required `SpineHandle` args to existing call sites.**
- `SpineHandle` gained required `httpBase` + `metadata`; two non-plan test files constructed `SpineHandle` and would no longer compile.
- Fix: added `httpBase = "http://test:7125"` + `metadata = MutableStateFlow(null)` to `app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt` and `app/src/androidTest/java/works/mees/dinghy/ui/ShellPresenceTest.kt` (both already imported `MutableStateFlow`).
- Committed with Task 2 (`e08a29d`).

## Known Stubs

None. Every new surface degrades gracefully (dashed cell / empty ring center / Benchy) when its source is null — by design, not a stub.

## Self-Check: PASSED

- Files exist: `PrintMetadata.kt`, `PrintMetadataHolder.kt`, `PrintMetadataParseTest.kt`, `PrintMetadataHolderTest.kt` — all FOUND.
- Commits exist: `e8ffd74`, `e08a29d`, `5c1302b` — all FOUND in git log.
