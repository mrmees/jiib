---
phase: 10-webcam-streaming
plan: 03
subsystem: webcam
tags: [webcams, moonraker, json-rpc, one-shot, spine, cadence-contract, stateflow, command-registry]

# Dependency graph
requires:
  - phase: 10-01 (Wave-0 fixtures)
    provides: SessionTestHarness webcamsListRequests hit-counter + canned server.webcams.list reply; the COMPILING runtime-RED WebcamEnumerationCadenceTest scaffold this plan replaces
  - phase: 10-02 (pure core)
    provides: tolerant Webcam model + parseWebcamsList (malformed → empty) — the type SpineHandle.webcams carries
provides:
  - JsonRpcMethods.WEBCAMS_LIST + CommandRegistry.webcamsList — a registered paramless one-shot JSON-RPC spec (NO availability predicate, D-08; NOT subscribed, NOT polled — cadence contract Rule 3)
  - SpineHandle.webcams StateFlow<List<Webcam>> forwarded off the session (the metadata/lastJob one-shot precedent, Decision B)
  - WebcamsHolder — service-owned one-shot fired on the connectionState rising edge into Connected (once per handshake edge), best-effort (rejected/absent → empty list, T-10-08)
  - AppContainer.webcams + AppContainer.webcamCount derived flows — the D-08 drawer greyed-gating signal + the D-10 default-cam pick source
  - WebcamEnumerationCadenceTest GREEN — the T-10-07 cadence-contract Rule-3 regression guard (public request hit-count + subscribe-frame contents, never V1_SUBSCRIBE_CORE)
affects: [10-06, 10-07]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "One-shot-per-HANDSHAKE-EDGE holder: fire a single read on the connectionState `!Connected → Connected` rising edge (re-emitted by the in-session klippy_ready re-handshake), NOT off printerState (metadata/lastJob fire off printerState; webcams fire off the handshake edge — same service-owned-rpc-capture seam, different trigger)"
    - "Observable cadence assertion via the harness request hit-counter + captured outbound subscribe frame (public behavior) — proves once-per-edge + not-in-subscribe WITHOUT peeking at the private V1_SUBSCRIBE_CORE constant"
    - "docs/commands sidecar sync (catalog.json registered=true + always-availability; printer-matrix.json command_availability row) to keep CommandCatalogDriftTest green when registering a new always-available spec"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/webcam/WebcamsHolder.kt
  modified:
    - app/src/main/java/works/mees/dinghy/net/JsonRpc.kt
    - app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt
    - app/src/main/java/works/mees/dinghy/di/SpineHandle.kt
    - app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
    - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
    - app/src/test/java/works/mees/dinghy/webcam/WebcamEnumerationCadenceTest.kt
    - app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt
    - app/src/androidTest/java/works/mees/dinghy/ui/ShellPresenceTest.kt
    - docs/commands/catalog.json
    - docs/commands/printer-matrix.json

key-decisions:
  - "Decision B (recommended) — service-owned one-shot forwarded as a SpineHandle StateFlow, NOT a holder-driven page-open read. SpineHandle exposes `dispatcher` not `rpc`, so a holder cannot rpc.request off the handle; capturing the session rpc in the service (the metadata/lastJob seam) keeps every one-shot read where it already lives and exposes no raw rpc on the handle."
  - "The handshake-edge trigger is the connectionState RISING edge into Connected — NOT printerState (the metadata/lastJob trigger). connectionState reaches Connected exactly once per completed handshake (initial connect + the in-session klippy_ready re-handshake re-emits Syncing→Connected), which is precisely the once-per-handshake-edge cadence the contract requires. A printerState trigger would not re-enumerate across a SAVE_CONFIG restart."
  - "NO availability predicate on webcamsList (Always) — the webcam Moonraker component is universal on E5/E3, so D-08 greys the TILE on cam-count==0 rather than command-gating. Synced both docs/commands sidecars to match (catalog availability→always, registered=true; printer-matrix command_availability row with runtime_unconditional)."
  - "The cadence test asserts PUBLIC OBSERVABLE BEHAVIOR end-to-end against the REAL session + fake transport: it builds the SAME WebcamsHolder MoonrakerService wires, drives one then two handshake edges, and asserts the harness webcamsListRequests counter (1 then 2) + the captured objects.subscribe frame carrying no webcam objects — never the private V1_SUBSCRIBE_CORE."

patterns-established:
  - "One-shot-per-handshake-edge holder keyed on the connectionState rising edge"
  - "Public-behavior cadence regression (request hit-count + subscribe-frame inspection)"
  - "Always-available spec → docs sidecar sync for the catalog-drift guard"

requirements-completed: []  # CAM-01 is NOT closed by this plan — it is delivered across 10-02..10-08; this plan builds the enumeration spine seam only (no on-device UAT yet)

# Metrics
duration: ~22min
completed: 2026-06-04
---

# Phase 10 Plan 03: Webcam Enumeration Spine Wiring Summary

**Wired the `/server/webcams/list` enumeration into the spine the cadence-compliant way — a registered paramless one-shot JSON-RPC spec (`CommandRegistry.webcamsList`, NO availability predicate per D-08), forwarded off the session as `SpineHandle.webcams` by a service-owned `WebcamsHolder` that fires EXACTLY ONCE per handshake edge (the `connectionState` rising edge into `Connected`, NOT a subscribe and NOT a poll), plus the `AppContainer.webcamCount` derived flow the drawer greyed-gating (D-08) and the holder default-cam pick (D-10) consume — and replaced the plan-10-01 runtime-RED `WebcamEnumerationCadenceTest` scaffold with typed PUBLIC-behavior assertions proving the once-per-edge cadence (request hit-count 1→2, no re-fetch on virtual-time advance, no webcam objects in the subscribe frame) against the real session + fake transport.**

## Performance

- **Duration:** ~22 min
- **Tasks:** 2
- **Files modified:** 11 (1 created, 10 modified)

## Accomplishments
- **`JsonRpcMethods.WEBCAMS_LIST`** (`"server.webcams.list"`) added beside `FILES_GET_DIRECTORY`.
- **`CommandRegistry.webcamsList`** — a paramless one-shot spec mirroring `serverInfo` verbatim (`catalogId = "MR-server.webcams.list"`, `key = "webcams_list"`, `params = { null }`), with **NO availability predicate** (D-08: the `webcam` component is universal on E5/E3; gate the tile on cam-count, not the command). Registered in `CommandRegistry.all` so the catalog-drift guard sees it. NOT added to `V1_SUBSCRIBE_CORE` (one-shot, not a subscribe).
- **`SpineHandle.webcams: StateFlow<List<Webcam>>`** — forwarded off the session exactly like `metadata`/`lastJob`, KDoc'd as a one-shot-per-handshake read (empty list = no cams / rejected-or-absent read = the greyed-tile signal; a late collector still sees the value because a StateFlow carries it forward).
- **`WebcamsHolder`** (new, `webcam/` package) — the service-owned one-shot holder modeled on `PrintMetadataHolder`/`LastJobHolder`, but triggered on the `connectionState` `!Connected → Connected` **rising edge** (the handshake edge) rather than `printerState`. Fires a single `server.webcams.list` read per edge via the injected `fetch` seam, parses with the tolerant `parseWebcamsList`, publishes on `webcams`. Best-effort `runCatching` → empty list on a rejected/absent/malformed read (T-10-08), never throws into the collector. Plain Kotlin, host-unit-testable.
- **`MoonrakerService.buildSpineAndLaunch`** — builds the `WebcamsHolder` capturing the session `rpc` (`rpc.request(CommandRegistry.webcamsList, Unit)`) and triggered off `session.connectionState`; passes `webcamsHolder.webcams` into the `SpineHandle(... webcams = ...)` assembly. The `rpc` capture stays in the service (no raw `rpc` exposed on the handle).
- **`AppContainer.webcams` + `AppContainer.webcamCount`** — `webcams = spine.flatMapLatest { it?.webcams ?: flowOf(emptyList()) }`; `webcamCount = webcams.map { it.size }`. The D-08 gating signal (tile live ≥1 / greyed 0) and the D-10 default-pick source; 0 when idle.
- **`WebcamEnumerationCadenceTest`** — scaffold body REPLACED with the real typed assertions, driving the SAME `WebcamsHolder` the service wires over the real `MoonrakerSession` + `SessionTestHarness`/`FakeWebSocket`:
  - one handshake edge → `webcamsListRequests == 1` and the holder is populated from the canned 2-cam reply;
  - the captured outbound `objects.subscribe` frame's `params.objects` keys carry **no** webcam entry (one-shot read, not a subscribe);
  - advancing virtual time 60s → the count **stays 1** (edge-driven, not a wall-clock poll);
  - a SECOND handshake edge (klippy_drop → klippy_ready re-handshake) → `webcamsListRequests == 2` (one-per-edge, the metadata/lastJob precedent), and stays 2 across another 60s advance.
  All assertions are PUBLIC behavior (the harness hit-counter + the captured subscribe frame) — the private `V1_SUBSCRIBE_CORE` is never touched.
- **docs/commands sidecars synced** so `CommandCatalogDriftTest` stays green with the newly-registered spec: `catalog.json` `MR-server.webcams.list` flipped to `availability/predicate: always` + `runtime_registry.registered: true`; `printer-matrix.json` gained a `MR-server.webcams.list` `command_availability` row (`always` predicate, `runtime_unconditional` per printer).

## Task Commits

1. **Task 1: Register the server.webcams.list one-shot spec (JsonRpc + CommandRegistry + docs sidecars)** — `22e924e` (feat)
2. **Task 2: Forward as SpineHandle StateFlow + AppContainer count flow + replace the cadence scaffold** — `f2020c6` (feat)

**Plan metadata:** (final docs commit below)

## Verification
- **Task 1 gate:** `:app:testDebugUnitTest --tests *CommandCatalogDrift* --tests *Registry*` → BUILD SUCCESSFUL (the new always-available spec + the two synced sidecars keep the catalog-drift guard green; `predicateReferencesAreBackedByMatrixEvidence` skips `Always`).
- **Task 2 cadence gate:** `:app:testDebugUnitTest --tests *WebcamEnumerationCadence*` → BUILD SUCCESSFUL (the once-per-edge cadence is proven GREEN).
- **No-regression proof:** `:app:testDebugUnitTest` across `webcam.*` + `net.*` + `command.*` + `di.*` (121 tests) → only the **4 still-RED scaffolds owned by other waves** fail (`FrameDropBehindTest`/`MjpegStreamDecoderTest` = 10-04, `WebcamBackoffTest` = 10-04/10-06, `WebcamReconnectStateTest` = 10-06) — exactly the RED-by-design `fail()` stubs the 10-02 SUMMARY documented; the other 117 (incl. my cadence test + the entire net/command/di suites) pass. No real test regressed; the decode/render/holder scaffolds remain RED for their own waves, untouched.
- **Production compile:** `:app:assembleRelease` → BUILD SUCCESSFUL (the SpineHandle field, WebcamsHolder, service wiring, and AppContainer flows compile and survive R8).
- No new dependency (`gradle/libs.versions.toml` unchanged — minSdk-23 floor stays auditable, T-10-SC N/A).

## Decisions Made
- **Decision B (service-owned StateFlow), recommended by PATTERNS.md.** The `SpineHandle` exposes `dispatcher` (fire-and-forget), not a `JsonRpcClient` — so a holder cannot `rpc.request` off the handle. Capturing the session `rpc` in the service (the `metadata`/`lastJob` seam) keeps every one-shot read where it already lives and avoids newly exposing raw `rpc` on the handle (which nothing else does).
- **Trigger = the `connectionState` rising edge into `Connected`, NOT `printerState`.** This is the one real difference from the `metadata`/`lastJob` holders (which trigger off `printerState`). The webcam enumeration must fire **once per handshake** — `connectionState` reaches `Connected` exactly once per completed handshake (initial connect + the in-session `klippy_ready` re-handshake re-emits `Syncing → Connected`), which is precisely the once-per-handshake-edge cadence the contract demands and makes `webcamsListRequests == 2` after a second edge. A `printerState` trigger would not re-enumerate across a `SAVE_CONFIG`/`FIRMWARE_RESTART` restart.
- **`Always` availability (no predicate) on the spec; docs sidecars synced to match.** Per D-08 the webcam component is universal, so the command is unconditional and the drawer **tile** is greyed on cam-count==0 — `catalog.json` and `printer-matrix.json` were updated so `CommandCatalogDriftTest` (`registryCommandsHaveMatrixAvailabilityRows`) stays green with the new registry entry.

## Deviations from Plan

**1. [Rule 3 - Blocking] Synced the docs/commands sidecars for the newly-registered always-available spec.**
- **Found during:** Task 1 (catalog-drift verification).
- **Issue:** Registering `webcamsList` in `CommandRegistry.all` makes `CommandCatalogDriftTest.registryCommandsHaveMatrixAvailabilityRows` require a `command_availability` row in `docs/commands/printer-matrix.json` — which had no `MR-server.webcams.list` row (the catalog had a `planned_v1`/`component_present:webcam`/`registered:false` reference entry only). Without the row the catalog-drift gate (the Task-1 verify step) would FAIL.
- **Fix:** Added a `MR-server.webcams.list` `command_availability` row to `printer-matrix.json` (mirroring the `MR-printer.emergency_stop` `Always`-predicate shape, `runtime_unconditional` per printer), and updated the `catalog.json` entry to `availability/predicate: always` + `runtime_registry.registered: true` to keep the catalog honest about the now-registered, D-08-unconditional command. This is the documented "docs/commands sidecar touches kept CommandCatalogDriftTest green" pattern (09-02 precedent).
- **Files modified:** `docs/commands/printer-matrix.json`, `docs/commands/catalog.json`.
- **Commit:** `22e924e`.

No architectural decisions (Rule 4) arose. No auth gates.

## Known Stubs

None. The enumeration spine is fully wired and the cadence is proven GREEN end-to-end. `SpineHandle.webcams` / `AppContainer.webcamCount` are live forward seams the downstream consumers (10-06 holder default-cam pick, 10-07 drawer greyed-gating) read from — they carry real session data (empty list = a genuine "no cams / unavailable" signal, not a fabricated placeholder).

## Threat Flags

None. This plan introduces no new network endpoint, auth path, or trust boundary beyond the plan's `<threat_model>`:
- **T-10-07 (DoS / cadence)** — mitigated and now regression-guarded GREEN: `webcamsList` is a one-shot edge-driven read (per handshake), NOT a subscribe and NOT polled; `WebcamEnumerationCadenceTest` proves the request hit-count fires once-per-edge, never re-fetches on ticks, and the subscribe frame carries no webcam objects (public-behavior assertions).
- **T-10-08 (Tampering / rejected enumeration)** — mitigated: `runCatching{}.getOrNull()` + tolerant `parseWebcamsList` → empty list (greyed tile), never crash.
- **T-10-SC** — N/A (no package installs; `libs.versions.toml` unchanged).

## Next Phase Readiness
- The enumeration integration seam every later webcam plan reads from (`SpineHandle.webcams` / `AppContainer.webcamCount`) is in place and cadence-proven GREEN.
- 10-04 (decode I/O) and 10-05 (render surface) — the wave-3 parallel siblings — are unaffected (disjoint files); their RED scaffolds remain RED for their own waves.
- 10-06 (wave 4) can build the holder's default-cam pick off `webcamCount`; 10-07 can build the runtime-greyed drawer tile off the same flow.
- No blockers.

## Self-Check: PASSED

Created file verified present on disk (`app/src/main/java/works/mees/dinghy/webcam/WebcamsHolder.kt`); both task commits (`22e924e`, `f2020c6`) verified in git log; `WebcamEnumerationCadenceTest` verified GREEN via Gradle `--tests`; `:app:assembleRelease` verified SUCCESSFUL; the 4 still-RED scaffolds verified to be the untouched `fail()`-stubs owned by 10-04/10-06 (no regression).

---
*Phase: 10-webcam-streaming*
*Completed: 2026-06-04*
