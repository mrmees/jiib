---
phase: 08-macros-console-functional-core-complete
plan: 04
subsystem: net
tags: [console, backfill, macros, gcode-store, configfile, handshake, moonraker, kotlin]

# Dependency graph
requires:
  - phase: 08-01
    provides: RED GcodeStoreParseTest + real probed fixture gcode_store_e5.json (the GREEN target)
  - phase: 08-02
    provides: ConsoleLine model + ConsoleSeverity.classify (the parser's output type + severity source)
  - phase: 05-03
    provides: the runHandshake step-7 one-shot-read pattern (temperature_store backfill + configfile extruder query) this plan mirrors
  - phase: 06
    provides: CommandRegistry + the catalog/printer-matrix drift guards that gate a new registry command
provides:
  - parseGcodeStore(JsonObject) -> List<ConsoleLine> (pure, null-safe, skip-bad-field tolerant; CONS-02 backfill parser)
  - CommandRegistry.gcodeStore CommandSpec + GcodeStoreArgs(count) + JsonRpcMethods.GCODE_STORE
  - PrinterStateStore.consoleBackfill StateFlow + setGcodeBackfill (REPLACE semantics, D-02) — ConsoleHolder seam (08-05)
  - PrinterStateStore.macroBodies StateFlow + setMacroBodies (lowercased name -> gcode body) — MacroHolder seam (08-06)
  - the in-handshake gcode_store backfill read (reruns on reconnect + notify_klippy_ready) + macro-body extract from the single configfile query
affects: [08-05 (ConsoleHolder collects consoleBackfill), 08-06 (MacroHolder/param parser reads macroBodies)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "One-shot best-effort handshake read (runCatching, in runHandshake step 7) — inherits reconnect + notify_klippy_ready reruns for free (Pitfall 4)"
    - "REPLACE-on-(re)connect backfill (Mainsail-parity Option A) — full server snapshot supersedes prior, recovers disconnect-window lines with no dedup logic (D-02)"
    - "One query, two consumers — the single configfile query feeds BOTH the extruder config AND the macro bodies (Pitfall 3, no duplicate query)"
    - "Null-safe wire walk (mapNotNull + runCatching) — a malformed entry is skipped, a malformed read leaves the seam at its empty default, never breaks Connected"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/console/GcodeStoreParse.kt
  modified:
    - app/src/main/java/works/mees/dinghy/net/JsonRpc.kt
    - app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt
    - app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt
    - app/src/test/java/works/mees/dinghy/net/HandshakeTest.kt
    - app/src/test/java/works/mees/dinghy/net/SessionTestHarness.kt
    - docs/commands/catalog.json
    - docs/commands/printer-matrix.json

key-decisions:
  - "consoleBackfill is a StateFlow<List<ConsoleLine>> (not a SharedFlow) per the plan recommendation — deterministic on-connect fullness; the snapshot is authoritative truth, not a transient event. 08-05's ConsoleHolderTest defines its OWN SharedFlow stub source, so it is unaffected by the store seam's shape."
  - "Backfill uses REPLACE (each (re)connect/klippy_ready fetches the full still-growing server buffer and supersedes prior contents) — Mainsail-parity Option A; recovers disconnect-window lines with no append/dedup logic (D-02)."
  - "gcode_store + macro-body reads live INSIDE runHandshake() step 7 (not a one-time connect path) so they auto-rerun on reconnect AND on the 05-10 notify_klippy_ready re-handshake (Pitfall 4) — stale console/macros after a FIRMWARE_RESTART would otherwise be a regression."
  - "The macro bodies are extracted from the SAME single {configfile} query that already reads the extruder min_extrude_temp — exactly ONE configfile query (Pitfall 3), asserted by HandshakeTest."
  - "gcodeBodyOrNull tolerates BOTH a single newline-joined string (the confirmed live shape, A1) AND an array-of-strings (joined with \\n) — belt-and-suspenders for a printer that returns the array form."

requirements-completed: [CONS-02, MACRO-02]

# Metrics
duration: 16min
completed: 2026-06-02
---

# Phase 8 Plan 04: Backend One-Shot Reads — Console Backfill + Macro Bodies Summary

**The two new backend one-shot reads for CONS-02 (console `gcode_store` backfill, REPLACE-on-reconnect for disconnect-window recovery) and MACRO-02 (macro gcode bodies extracted from the single existing configfile query) — both riding `runHandshake()` so they auto-rerun on reconnect and `notify_klippy_ready`, turning `GcodeStoreParseTest` GREEN.**

## What was built

**Task 1 (commit `17423b0`) — parser + registration:**
- `JsonRpcMethods.GCODE_STORE = "server.gcode_store"`.
- `CommandRegistry.gcodeStore` CommandSpec (`MR-server.gcode_store`, params `{count}`) + `GcodeStoreArgs(count = 1000)`, appended to `all`.
- Pure `parseGcodeStore(JsonObject): List<ConsoleLine>` — walks `result["gcode_store"]` as a `JsonArray`, per-element `mapNotNull` extracting `message` (required → skip if absent), `time` (`doubleOrNull`), severity via `ConsoleSeverity.classify`; whole walk in `runCatching → emptyList` on malformed (house rule). Raw prefix kept in `rawMessage` (D-04).
- `GcodeStoreParseTest` GREEN (3/3): real 20-entry fixture severities, missing-`message` skip, empty/garbage store → empty.

**Task 2 (commit `5f2d568`) — handshake wiring + store seams:**
- `PrinterStateStore`: `setGcodeBackfill`/`consoleBackfill: StateFlow<List<ConsoleLine>>` (REPLACE) + `setMacroBodies`/`macroBodies: StateFlow<Map<String,String>>` (lowercased name → body).
- `MoonrakerSession.runHandshake()` step 7: a NEW best-effort `runCatching` gcode_store read → `store.setGcodeBackfill(parseGcodeStore(...))`; and the EXISTING configfile `runCatching` extended to also walk every `settings["gcode_macro <name>"]` section into `store.setMacroBodies(...)` — one query, two consumers.
- `gcodeBodyOrNull` null-safe walker (string OR array `.gcode`).
- `SessionTestHarness` extended with a faithful `gcode_store` reply + `gcode_macro` configfile sections; `HandshakeTest` updated to assert the new in-order read (`…TEMPERATURE_STORE → GCODE_STORE → configfile OBJECTS_QUERY`), the backfill REPLACE, the populated macro bodies, and **exactly one** `{configfile}` query.
- Full `:app:testReleaseUnitTest` BUILD SUCCESSFUL (sibling-RED ConsoleHolderTest/MacroHolderTest set aside for the run, then restored — tree clean).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Phase-6 catalog/matrix drift guard required a registry-command row**
- **Found during:** Task 2 (first full-suite run)
- **Issue:** `CommandCatalogDriftTest.registryCommandsHaveMatrixAvailabilityRows` FAILED — registering `gcodeStore` made `MR-server.gcode_store` a runtime registry command, which the Phase-6 guard requires to have a `command_availability` row in `docs/commands/printer-matrix.json` (it was only present in `catalog.json` as `planned_v1`).
- **Fix:** Added an `always`-predicate `command_availability` row for `MR-server.gcode_store` to `printer-matrix.json` (E5 = probed-fixture evidence, E3 = Moonraker-core-since-2021 evidence) and flipped the `catalog.json` `runtime_registry` to `registered: true` with a Phase-08-04 note.
- **Files modified:** docs/commands/printer-matrix.json, docs/commands/catalog.json
- **Commit:** `5f2d568`

**2. [Rule 1 - Test fixture] HandshakeTest's exact handshake-sequence assertion needed the new read**
- **Found during:** Task 2
- **Issue:** `HandshakeTest.handshakeRunsInOrder_onceEach_andSeedsState` asserts the EXACT ordered method list; inserting the gcode_store read between `TEMPERATURE_STORE` and the configfile `OBJECTS_QUERY` would have failed the stale assertion.
- **Fix:** Updated the expected list to include `GCODE_STORE` in its real position and extended the test to assert the backfill + macro bodies + single-configfile-query guarantee.
- **Files modified:** app/src/test/java/works/mees/dinghy/net/HandshakeTest.kt, SessionTestHarness.kt
- **Commit:** `5f2d568`

## Threat Surface

No new surface beyond the plan's `<threat_model>`. Both reads are best-effort `runCatching` over untrusted wire JSON, walked with `mapNotNull` (entry-level skip) — a malformed/garbage read leaves the seam at its empty default and Connected is unaffected (T-08-04-T mitigated). No new packages (T-08-04-SC).

## Known Stubs

None. The two seams (`consoleBackfill`, `macroBodies`) are populated by real handshake reads; they are intentionally not yet *consumed* — that is the explicit job of the still-RED siblings ConsoleHolder (08-05) and MacroHolder (08-06), which this plan's seams unblock.

## Self-Check: PASSED
- FOUND: app/src/main/java/works/mees/dinghy/ui/console/GcodeStoreParse.kt
- FOUND: app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt (setGcodeBackfill + setMacroBodies)
- FOUND: app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt (gcodeStore)
- FOUND commit 17423b0 (Task 1), 5f2d568 (Task 2)
- GcodeStoreParseTest GREEN + full :app:testReleaseUnitTest BUILD SUCCESSFUL (siblings set aside, restored — tree clean)
