---
phase: 16-home-print-status-redesign
plan: 03
subsystem: command
tags: [gcode-builder, command-registry, babystep, terminal-dismiss, asvs-v5, catalog-drift]
requires:
  - "PrinterCommands (existing) — testZ clamp-before-format analog, COOLDOWN/SAVE_CONFIG const block"
  - "CommandRegistry (existing) — gcode(...) factory, ObjectPresent predicate, the `all` list"
  - "16-01 RED gate: PrinterCommandsTest setGcodeOffsetZAdjust + SDCARD_RESET_FILE cases"
  - "docs/commands/catalog.json + printer-matrix.json (existing reference rows for both gcodes)"
provides:
  - "PrinterCommands.setGcodeOffsetZAdjust builder (V5 canonicalize-against-set, Locale.US) -> 16-06 babystep wiring"
  - "PrinterCommands.BABYSTEP_STEPS canonical fixed cycle -> 16-02 classifier step-cycle (single source of truth)"
  - "PrinterCommands.SDCARD_RESET_FILE const -> 16-06 Terminal-Dismiss gutter action"
  - "CommandRegistry.babystepZ spec (ObjectPresent gcode_move) -> 16-06"
  - "CommandRegistry.dismissPrint spec (ObjectPresent virtual_sdcard) -> 16-06"
affects:
  - "16-02 (reads BABYSTEP_STEPS), 16-06 (wires both specs into the Print-Status gutter/row actions)"
tech-stack:
  added: []
  patterns:
    - "Total (never-rejecting) V5 builder: canonicalize-against-fixed-set with epsilon nearest-match + sign preservation, so off-grid/NaN/0 input snaps to a valid member"
    - "Locale.US 2dp-then-trim Double formatting for babystep (0.10 -> 0.1, 0.05 preserved)"
    - "Reuse existing catalog/matrix reference rows by catalogId; flip registered flag + add the missing command_availability rows (no duplicate catalog rows)"
key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt
    - app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt
    - app/src/test/java/works/mees/dinghy/command/PrinterCommandsTest.kt
    - docs/commands/catalog.json
    - docs/commands/printer-matrix.json
decisions:
  - "Reused the EXISTING catalog ids KGC-SET_GCODE_OFFSET / KGC-SDCARD_RESET_FILE (both already present as reference/planned rows) instead of the plan's proposed `KGC-SETGCODEOFFSET` — avoids a duplicate/dead catalog row and satisfies CommandCatalogDriftTest's existence + command_availability checks cleanly"
  - "Removed the now-unused `org.junit.Assert.fail` import from PrinterCommandsTest after the RED scaffolds went GREEN"
metrics:
  duration: ~15 min
  completed: 2026-06-06
---

# Phase 16 Plan 03: Babystep + Terminal-Dismiss CommandSpecs Summary

Added the two net-new gcode builders/specs Phase 16's Print-Status needs — the session-only Z-babystep
nudge (`SET_GCODE_OFFSET Z_ADJUST=±step MOVE=1`, SC-5) and the Terminal-Dismiss file clear
(`SDCARD_RESET_FILE`, D-05) — as V5-clean canonical registry entries appended to `CommandRegistry.all`,
turning the 16-01 RED PrinterCommandsTest cases GREEN while the correct `docs/commands/*.json` sidecars
stay drift-free.

## What Was Built

- **`PrinterCommands.BABYSTEP_STEPS`** — the canonical fixed cycle `[0.02, 0.05, 0.10, 0.15, 0.20]`,
  defined ONCE here as the single source of truth (16-02's classifier step-cycle reads this constant,
  per the cross-AI review wiring note).
- **`PrinterCommands.setGcodeOffsetZAdjust(deltaMm)`** — a TOTAL (never-rejecting) builder. Per ASVS V5
  / T-16-03-01: it takes `sign(deltaMm)` (NaN/0 default to positive), snaps `abs(deltaMm)` to the
  nearest `BABYSTEP_STEPS` member via an epsilon-tolerant `minByOrNull { abs(it - mag) }` (NaN guarded
  to the first member), re-applies the sign, and formats with `Locale.US` (2dp then trailing-zero trim:
  `0.10 → "0.1"`, `0.05` preserved). An off-grid/garbage value can never reach the gcode string.
  Negative = Compress (nozzle closer), positive = Expand. Mirrors the existing `testZ` clamp discipline.
- **`PrinterCommands.SDCARD_RESET_FILE`** const — fixed gcode, zero interpolation (T-16-03-02), placed
  next to `SAVE_CONFIG`.
- **`CommandRegistry.babystepZ`** (`BabystepArgs(deltaMm)`) — `gcode(...)` spec gated
  `ObjectPresent("gcode_move")` (gcode_move carries `homing_origin[2]`, the running offset the screen
  reads back), gcode `setGcodeOffsetZAdjust(it.deltaMm)`.
- **`CommandRegistry.dismissPrint`** — no-param const-gcode spec gated `ObjectPresent("virtual_sdcard")`
  (mirrors `printStart`), gcode `SDCARD_RESET_FILE`.
- **Both specs appended to `CommandRegistry.all`** — a spec missing from `all` is dead wiring (the
  exact class the plan-checker caught on Phase 11); CommandCatalogDriftTest proves they're live.
- **Sidecars:** flipped the existing `KGC-SET_GCODE_OFFSET` + `KGC-SDCARD_RESET_FILE` catalog.json rows
  from reference/planned → `registered: true`, and added their `command_availability` rows to
  printer-matrix.json (gcode_move + virtual_sdcard both present on E5/E3, no `not_on_printers` needed).
- **Tests:** turned the three 16-01 RED PrinterCommandsTest cases GREEN (signed-delta exact string,
  off-grid snap, SDCARD_RESET_FILE literal) and added the two required invalid-input canonicalization
  cases (0.0 / NaN / out-of-range → a valid BABYSTEP_STEPS member) plus a BABYSTEP_STEPS fixed-cycle
  assertion.

## Verification

- Task 1: `:app:testDebugUnitTest --tests PrinterCommardsTest` → **BUILD SUCCESSFUL (exit 0)**; the RED
  cases are GREEN and the existing 42 cases still pass.
- Task 2: `:app:testDebugUnitTest --tests PrinterCommandsTest --tests CommandRegistryTest --tests
  CommandCatalogDriftTest` → **BUILD SUCCESSFUL (exit 0)**. CommandCatalogDriftTest GREEN proves: both
  new registry catalogIds exist in catalog.json, both have printer-matrix `command_availability`
  evidence, and their `ObjectPresent` predicates are backed by matrix evidence on both printers.

(The filtered Gradle run compiles the whole `testDebug` sourceset before applying `--tests`, so the
GREEN compile also confirms the other Phase-16 Wave-0 scaffolds still compile.)

## Commits

- `3fd7ef1` feat(16-03): setGcodeOffsetZAdjust builder + BABYSTEP_STEPS + SDCARD_RESET_FILE (Task 1)
- `e2d939e` feat(16-03): register babystepZ + dismissPrint CommandSpecs + catalog sidecars (Task 2)

## Deviations from Plan

### [Rule 3 - Blocking/cleaner wiring] Reused existing catalog ids instead of `KGC-SETGCODEOFFSET`

- **Found during:** Task 2
- **Issue:** The plan's must_haves named the babystep spec catalogId `"KGC-SETGCODEOFFSET"`, but
  catalog.json already carries `KGC-SET_GCODE_OFFSET` (reference_only) and `KGC-SDCARD_RESET_FILE`
  (planned_v1) rows. Introducing a fresh `KGC-SETGCODEOFFSET` id would either fail
  CommandCatalogDriftTest's `registryCatalogIdsExistInCatalogJson` check (no matching catalog row) or
  require adding a near-duplicate catalog/matrix row alongside the existing one.
- **Fix:** Reused the existing `KGC-SET_GCODE_OFFSET` / `KGC-SDCARD_RESET_FILE` catalogIds, flipped
  their `registered` flag, and added the missing `command_availability` rows — keeping the catalog
  drift-free with no duplicate rows.
- **Files modified:** CommandRegistry.kt, docs/commands/catalog.json, docs/commands/printer-matrix.json
- **Commit:** e2d939e

### [Rule 1 - Cleanup] Removed now-unused `fail` import

- **Found during:** Task 1
- **Issue:** After the three RED `fail(...)` scaffolds went GREEN, `org.junit.Assert.fail` was unused.
- **Fix:** Dropped the import (kept the file warning-clean).
- **Files modified:** PrinterCommandsTest.kt
- **Commit:** 3fd7ef1

## Threat Model Compliance

- **T-16-03-01** (V5 input validation, `setGcodeOffsetZAdjust`): canonicalized against the fixed
  BABYSTEP_STEPS set (epsilon nearest-match, sign preserved) before Locale.US formatting; only a
  validated signed numeric reaches the string — never free-text, never off-grid/NaN. Encoded by the
  signed-delta + off-grid-snap + invalid-input tests. ✅
- **T-16-03-02** (V5, `SDCARD_RESET_FILE` const): fixed const gcode, zero interpolation. ✅
- **T-16-03-03** (physical motion, `MOVE=1`): the `MOVE=1` Z jog is gated by the gcode_move
  availability predicate here; the Wave-3 screen adds the early-layer window gate; session-only, no
  SAVE_CONFIG. ✅
- **T-16-SC** (package installs): no package installs this phase. ✅

## Known Stubs

None — both specs are live (in `all`), object-gated, and proven by host tests. Screen wiring lands in
16-06 (out of this plan's scope by design).

## Self-Check: PASSED

- Files exist (modified): PrinterCommands.kt, CommandRegistry.kt, PrinterCommandsTest.kt,
  docs/commands/catalog.json, docs/commands/printer-matrix.json. ✅
- Commits exist: 3fd7ef1, e2d939e. ✅
- `setGcodeOffsetZAdjust` + `BABYSTEP_STEPS` + `SDCARD_RESET_FILE` present in PrinterCommands.kt;
  `BabystepArgs` + `babystepZ` + `dismissPrint` present in CommandRegistry.kt and appended to `all`. ✅
