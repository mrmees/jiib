---
phase: 11-spool-management-spoolman-camera-qr
plan: 03
subsystem: spool
tags: [wave-1, pure-decision, qr-parser, print-start-gate, scan-state-machine, d-12, d-01, d-15, security-v5, host-tested]
requires:
  - "11-01: RED scaffolds (QrPayloadParserTest/PrintStartGateTest/ScanStateMachineTest) + the SpoolmanModels types"
  - "11-02: SpoolmanSpool/SpoolmanStatus/SpoolmanFilament models + the extended FilePreviewMetadata filament arrays"
provides:
  - qr-payload-parser: "parseSpoolId(payload): SpoolQrResult — D-12 id-only allow-list (web+spoolman:s-<id> + /spool/show/<id>); host NEVER trusted; sealed Spool(id)/UnsupportedSpoolmanCode/NotASpoolCode"
  - print-start-gate: "evaluatePrintStartGate(...): List<SpoolWarning> — D-01 warn-only, never blocks; sealed SpoolWarning + materialFamilyMatches (D-05) + LOW_FILAMENT_MARGIN constants"
  - scan-state-machine: "deriveScanState(...): ScanState — D-15 headless permission/degrade machine; canFallBackToPicker invariant; AwaitingConfirm(id) confirm-first (D-12)"
affects:
  - "11-04 (notify router) is the last Wave-1 RED scaffold still failing — owns SpoolmanNotifyRouterTest"
  - "11-06 (CameraPermission Activity-Result glue) consumes deriveScanState + SpoolQrResult"
  - "11-08 (FilesScreen gate hook) consumes evaluatePrintStartGate + SpoolWarning"
tech-stack:
  added: []
  patterns:
    - "Pure host-tested predicate/derivation (DeleteGate + TopRoute.derive idiom) for all three decision fns"
    - "Allow-list parse of untrusted input (V5): id-only Int-validated, host never carried into the sealed result"
    - "Wave-0 RED scaffolds replaced with typed assertions in the building plan (wave0-RED-compile discipline)"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/spool/scan/QrPayloadParser.kt
    - app/src/main/java/works/mees/dinghy/ui/spool/PrintStartGate.kt
    - app/src/main/java/works/mees/dinghy/ui/spool/scan/ScanState.kt
  modified:
    - app/src/test/java/works/mees/dinghy/spool/QrPayloadParserTest.kt
    - app/src/test/java/works/mees/dinghy/spool/PrintStartGateTest.kt
    - app/src/test/java/works/mees/dinghy/spool/ScanStateMachineTest.kt
decisions:
  - "QR URL match uses a single IGNORE_CASE regex `^https?://[^/]+/spool/show/(\\d+)/?$` capturing the trailing id ONLY — the host group ([^/]+) is matched but never captured into the result (D-12 V5 boundary)"
  - "SpoolWarning modeled as a sealed interface with a stable `message` + typed payloads (MaterialMismatch/LowFilament carry the compared values) so the confirm UI (11-08) renders without re-deriving"
  - "Low-filament margin = max(needed*1.05, needed+10g) per RESEARCH discretion, exposed as LOW_FILAMENT_MARGIN_FRACTION/FLOOR_GRAMS consts"
  - "deriveScanState gates hardware/permission BEFORE decode (no-camera > denied > requesting > busy > decode); permissionResolved disambiguates Idle(requesting) from PermissionDenied"
  - "Unsupported/NotRecognized 'stay in scan flow' (plan body) reconciled with the scaffold's 'degrade to picker' by canFallBackToPicker==true on those states — both true simultaneously (stay scanning AND picker escape present)"
  - "deriveScanState named to avoid colliding with ui/route/TopRoute.derive; file is ScanState.kt (CameraPermission.kt name reserved for 11-06's Compose Activity-Result glue)"
metrics:
  duration: ~6m
  completed: 2026-06-04
  tasks: 3
  files: 6
---

# Phase 11 Plan 03: Pure Spool Decision Functions Summary

Built the three pure, host-tested decision functions that carry this phase's load-bearing logic — the D-12 QR payload parser (the Security V5 untrusted-input boundary), the D-01 warn-only print-start gate, and the D-15 headless scan/permission/degrade state machine. All no-Android, no-I/O, exhaustively unit-tested; the three Wave-0 RED scaffolds (`QrPayloadParserTest`, `PrintStartGateTest`, `ScanStateMachineTest`) are now GREEN. The parser allow-lists an `Int` spool id ONLY and never trusts a URL host; the gate provably never hard-blocks; the state machine confirms-first and always degrades to the manual picker.

## What Was Built

### Task 1 — D-12 QR payload parser (commit `2e426a1`)
- `QrPayloadParser.kt`: `sealed interface SpoolQrResult { Spool(val id: Int); UnsupportedSpoolmanCode; NotASpoolCode }` + `fun parseSpoolId(payload): SpoolQrResult`, the DeleteGate pure-predicate idiom with an exhaustive accept/reject KDoc.
- Accepts (case-insensitive) `web+spoolman:s-<digits>` and `http(s)://<any-host>/spool/show/<digits>` — parsing the TRAILING id ONLY. The host (incl. a hostile `evil.example`) is matched by `[^/]+` but **never captured, returned, navigated, or used** — `Spool` carries `val id: Int` and nothing else (V5 boundary, T-11-03-01).
- `web+spoolman:f-<id>` → `UnsupportedSpoolmanCode`; UPC/EAN, non-numeric, missing-id, foreign scheme, non-`/spool/show/` URL → `NotASpoolCode`. The id is `toIntOrNull`-validated so an overlong digit run yields `NotASpoolCode`, never an overflow throw (T-11-03-02).
- Test asserts all four classes incl. uppercase, both friendly + hostile hosts, and the overflow case.

### Task 2 — D-01 warn-only print-start gate (commit `1552cff`)
- `PrintStartGate.kt`: `sealed interface SpoolWarning` (NoActiveSpool / MaterialMismatch / LowFilament / ArchivedSpool / PendingReports / FetchFailed, each with a stable `message`) + `fun evaluatePrintStartGate(activeSpool, status, fetchFailed, file): List<SpoolWarning>`.
- **No block path exists** — every condition appends a warning and execution continues. No-spool and fetch-failed short-circuit to a single warning (nothing further to compare); the resolved-spool path runs material-mismatch + low-filament + archived + pending checks in D-01 order.
- `materialFamilyMatches` is partial + case-insensitive (substring both ways: file `PLA` matches spool `PLA+` and vice-versa, D-05); a multi-family file matches if ANY family matches.
- Low-filament threshold = `max(needed*1.05, needed+10g)` (RESEARCH discretion), **SKIPPED entirely** when `file.filamentWeightTotal == null` (no length/density fallback).
- Test covers each warning, the family-match no-warn case, the multi-family any-match case, the clean-pass empty list, and the null-weight skip.

### Task 3 — D-15 scan/permission/degrade state machine (commit `3bc8702`)
- `ScanState.kt`: `sealed interface ScanState` (Idle / Scanning / PermissionDenied / NoCamera / Busy / Unsupported / NotRecognized / AwaitingConfirm(id)) each exposing `canFallBackToPicker`, + `fun deriveScanState(permissionGranted, permissionResolved, cameraAvailable, bindFailed, lastDecode): ScanState`, the `TopRoute.derive` pure-`when` idiom.
- Hardware/permission gates win first (no-camera > denied > still-requesting > busy), then the decode routes: `null` → keep Scanning (transient unreadable/no-QR), `Spool(id)` → `AwaitingConfirm(id)` (**confirm-first, never auto-loads** — D-12 / T-11-03-03), `UnsupportedSpoolmanCode`/`NotASpoolCode` → stay in the scan flow with the picker escape.
- `canFallBackToPicker` is the D-15 invariant: `true` for every degrade/terminal state, `false` only while actively Scanning. No CameraX / no Context / no `rememberLauncherForActivityResult` — that glue is 11-06's.

## Verification

- `--tests *QrPayloadParserTest` → BUILD SUCCESSFUL (GREEN).
- `--tests *PrintStartGateTest` → BUILD SUCCESSFUL (GREEN).
- `--tests *ScanStateMachineTest` → BUILD SUCCESSFUL (GREEN).
- Full `:app:testDebugUnitTest` → **510 tests, 3 failed** — all 3 are `SpoolmanNotifyRouterTest` (the Wave-1 RED scaffold owned by plan **11-04**), confirmed by name in the failure output. None of this plan's three target tests fail; no pre-existing green test regressed. (The camera/ZXing 11-05 tests were intentionally not scaffolded in 11-01, so they don't appear in the run.)
- Threat scan: `grep` of `QrPayloadParser.kt` confirms no host/url field on `SpoolQrResult.Spool` — the host appears ONLY inside the local regex matcher, never carried into the result (T-11-03-01 mitigated). Int-validation mitigates T-11-03-02; `AwaitingConfirm`-only valid-decode path (no auto-set state) mitigates T-11-03-03.

## Deviations from Plan

None affecting scope. Clarifications resolved during execution:
- **RED scaffolds turned into typed assertions in this plan** (per the wave0-RED-scaffold-compile memory) rather than leaving `fail()` stubs — the three target tests now carry real decision-table assertions.
- **`Unsupported`/`NotRecognized` "stay in scan flow" vs the scaffold KDoc's "degrade to picker"** — reconciled, not chosen between: those states keep the scan surface AND set `canFallBackToPicker == true`, so both the plan body (D-12 stay-in-flow) and the scaffold contract (D-15 picker escape always present) hold simultaneously.
- **`deriveScanState` naming** chosen over `derive` to avoid colliding with `ui/route/TopRoute.derive`; the file is `ScanState.kt` (the `CameraPermission.kt` filename in PATTERNS is reserved for 11-06's net-new Compose Activity-Result glue, which this pure machine deliberately omits).

## Known Stubs

None. All three files are complete production implementations of their decision contracts. The remaining `fail()` body in the repo is `SpoolmanNotifyRouterTest`, owned by plan 11-04.

## Threat Flags

None — no new security surface beyond the QR parser already enumerated in the plan's `<threat_model>` (T-11-03-01/02/03 all mitigated as designed; no new endpoints/auth/file/schema surface introduced by these pure functions).

## Self-Check: PASSED

- Files: `QrPayloadParser.kt`, `PrintStartGate.kt`, `ScanState.kt` all FOUND under `app/src/main/.../ui/spool/`.
- Commits: `2e426a1`, `1552cff`, `3bc8702` all present in `git log`.
- Three target tests GREEN; the only 3 full-suite failures are the later-plan (11-04) `SpoolmanNotifyRouterTest` RED scaffold — no regression.
