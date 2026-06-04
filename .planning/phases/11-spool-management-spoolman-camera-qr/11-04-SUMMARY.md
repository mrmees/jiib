---
phase: 11-spool-management-spoolman-camera-qr
plan: 04
subsystem: spool
tags: [wave-2, spine-wiring, spoolman, json-rpc, notify-routing, d10-reconcile, capability-gate, proxy-v2]
requires:
  - "11-01: golden corpus + hardened fakes (FakeMoonrakerSpoolmanSession 1-element notify injectors, FakeSpoolmanClient proxy-v2 envelopes) + SpoolmanNotifyRouterTest RED scaffold"
  - "11-02: SpoolmanModels (SpoolmanStatus) + SpoolmanParsers (parseSpoolmanStatus) the facade decodes status into"
provides:
  - spoolman-json-rpc-constants: "JsonRpcMethods SPOOLMAN_STATUS/GET_SPOOL_ID/POST_SPOOL_ID/PROXY + NOTIFY_ACTIVE_SPOOL_SET/NOTIFY_SPOOLMAN_STATUS_CHANGED"
  - spoolman-notify-routing: "JsonRpcClient.activeSpoolSet/spoolmanStatusChanged bounded SharedFlows fed by the params[0] extractor; proc_stat falls through else -> Unit"
  - spoolman-command-specs: "spoolmanStatus/getSpoolId (ungated) + spoolmanPostSpoolId/spoolmanProxy (ComponentPresent(spoolman)) + SetSpoolArgs/SpoolmanProxyArgs"
  - spoolman-inventory-client: "MoonrakerSpoolmanClient (interface SpoolmanClient) — proxy-v2 reads over the session JsonRpcClient, dotted-key URL-encode"
  - active-spool-facade: "ActiveSpoolFacade — handshake-edge status fetch + D-10 notify reconciliation, constructed per-session by MoonrakerService and published on SpineHandle.activeSpool"
  - appcontainer-spool-flows: "AppContainer.activeSpool + spoolmanPresent derived flows (the D-02 drawer-greying input)"
affects:
  - "11-05+ UI waves consume AppContainer.activeSpool/spoolmanPresent + MoonrakerSpoolmanClient inventory reads"
  - "The Spool drawer tile greys off spoolmanPresent (mirrors webcamCount>0 for Webcam)"
tech-stack:
  added: []
  patterns:
    - "WebcamsHolder -> SpineHandle.webcams -> AppContainer.webcams/webcamCount chain replicated for activeSpool/spoolmanPresent"
    - "1-element params-array notify extractor (spoolNotifyParam) mirrors statusDiff/gcodeLine"
    - "Two-transport split: active-spool state via JSON-RPC session; inventory via server.spoolman.proxy use_v2_response (NOT through CommandDispatcher)"
    - "D-10 reconcile: active_spool_set OVERWRITES the local id (never re-asserts stale); status_changed re-fetches status for truth"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/spool/SpoolmanClient.kt
    - app/src/main/java/works/mees/dinghy/spool/ActiveSpoolFacade.kt
  modified:
    - app/src/main/java/works/mees/dinghy/net/JsonRpc.kt
    - app/src/main/java/works/mees/dinghy/net/JsonRpcClient.kt
    - app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt
    - app/src/main/java/works/mees/dinghy/di/SpineHandle.kt
    - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
    - app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
    - docs/commands/printer-matrix.json
    - app/src/test/java/works/mees/dinghy/spool/SpoolmanNotifyRouterTest.kt
    - app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt
    - app/src/androidTest/java/works/mees/dinghy/ui/ShellPresenceTest.kt
    - app/src/androidTest/java/works/mees/dinghy/webcam/DrawerWebcamGatingTest.kt
decisions:
  - "The live notify golden carries TEN notify_proc_stat_update frames (not nine as the RED-scaffold KDoc guessed) interleaved around the two active_spool_set frames — the ignore-test asserts 10"
  - "Added four command_availability rows to docs/commands/printer-matrix.json — CommandCatalogDriftTest.registryCommandsHaveMatrixAvailabilityRows requires every registry catalog ID to carry matrix evidence; spoolman is in both printers' components so the gated specs are backed (Rule 3 blocking fix)"
  - "spoolmanStatus/getSpoolId left UNGATED (universally answerable when the component exists); only the WRITE (post) and the proxy passthrough carry ComponentPresent(spoolman) per D-02"
  - "ActiveSpoolFacade reconcile preserves prior status fields and swaps ONLY activeSpoolId on active_spool_set (the 1-field notify carries id only); status_changed triggers a full re-fetch for connected/pending truth"
  - "measureSpool issues PUT /v1/spool/{id}/measure with weight=<grams> in the query (D-04); the proxy forwards the query to Spoolman"
  - "SpoolmanNotifyRouterTest RED fail() bodies replaced with typed golden-driven assertions in THIS plan (wave0-RED-scaffold-compile discipline), driven through the real JsonRpcClient.dispatch with UnconfinedTestDispatcher collectors (no-replay SharedFlows)"
metrics:
  duration: ~12m
  completed: 2026-06-04
  tasks: 3
  files: 16
---

# Phase 11 Plan 04: Spoolman Spine Wiring Summary

Wired the full Spoolman spine into the live Moonraker session graph: the four `server.spoolman.*` JSON-RPC method constants + the two server-push notification routes (1-element params array → bounded SharedFlows), the four capability-gated command specs, the lean proxy-v2 `MoonrakerSpoolmanClient` inventory reader, the edge-driven + D-10-reconciled `ActiveSpoolFacade`, and — the plan-check BLOCKER fix — the `MoonrakerService` construction of that facade (mirroring `WebcamsHolder`) with publication on `SpineHandle.activeSpool` and the `AppContainer.activeSpool`/`spoolmanPresent` derived flows. `SpoolmanNotifyRouterTest` is GREEN against the verbatim live notify golden; the full unit suite (510 tests) passes with zero failures and the live APK assembles.

## What Was Built

### Task 1 — JSON-RPC constants, notify routing, command specs (commit `7c1c378`)
- `JsonRpc.kt`: six new `JsonRpcMethods` constants (SPOOLMAN_STATUS/GET_SPOOL_ID/POST_SPOOL_ID/PROXY + NOTIFY_ACTIVE_SPOOL_SET/NOTIFY_SPOOLMAN_STATUS_CHANGED), mirroring the WEBCAMS_LIST/NOTIFY_* block.
- `JsonRpcClient.kt`: two bounded `MutableSharedFlow<JsonObject>` (activeSpoolSet/spoolmanStatusChanged, extraBufferCapacity 16), the `spoolNotifyParam(obj)` extractor pulling `params[0].jsonObject` inside `runCatching` (D-10 1-element array, mirrors `statusDiff`), and two `when(method)` arms in `dispatch()`. The `else -> Unit` fall-through is intact — proc_stat frames ignored.
- `CommandRegistry.kt`: `SetSpoolArgs(spoolId: Int?)` (null = clear, D-13 → `{}`) + `SpoolmanProxyArgs(method, path, query)`; four specs via the `jsonRpc(...)` helper. `spoolmanStatus`/`spoolmanGetSpoolId` ungated; `spoolmanPostSpoolId`/`spoolmanProxy` carry `ComponentPresent("spoolman")` (D-02). All four appended to `all`.
- `printer-matrix.json`: four `command_availability` rows (Rule 3 deviation — see below).

### Task 2 — Lean SpoolmanClient proxy-v2 inventory reader (commit `ba7d12d`)
- `SpoolmanClient.kt`: `interface SpoolmanClient` (getSpool/listSpools/listFilaments/listMaterials/listVendors/listLocations/measureSpool, all `suspend … : JsonElement?`, names matching `FakeSpoolmanClient`) + `MoonrakerSpoolmanClient(rpc)` — each method builds a `SpoolmanProxyArgs` and returns `runCatching { rpc.request(CommandRegistry.spoolmanProxy, args) }.getOrNull()`. Dotted query keys URL-encoded per-pair in this layer (D-07/Pitfall 5); `measureSpool` issues `PUT /v1/spool/{id}/measure`. Lean — wraps the session JsonRpcClient, NOT forced through CommandDispatcher (D-07/Discretion).

### Task 3 — ActiveSpoolFacade + service construction + derived flows (commit `a8bdb16`)
- `ActiveSpoolFacade.kt`: plain-Kotlin (no Android import) WebcamsHolder analogue. Three collectors: (a) rising `!Connected→Connected` edge → one-shot `fetchStatus()` → `parseSpoolmanStatus` into `_activeSpool` (best-effort); (b) `activeSpoolSet` → overwrite ONLY `activeSpoolId` with the pushed id (D-10, never stale); (c) `spoolmanStatusChanged` → re-fetch status for truth.
- `MoonrakerService.kt`: constructs `ActiveSpoolFacade(serviceScope, session.connectionState, rpc.activeSpoolSet, rpc.spoolmanStatusChanged, fetchStatus = { runCatching { rpc.request(CommandRegistry.spoolmanStatus, Unit) }.getOrNull() })` alongside the WebcamsHolder block, and assigns `activeSpool = activeFacade.activeSpool` on the `SpineHandle(...)` construction next to `webcams = webcamsHolder.webcams`. The atomic `publishSpine(handle)` swap is untouched.
- `SpineHandle.kt`: `activeSpool: StateFlow<SpoolmanStatus?>` field (webcams precedent).
- `AppContainer.kt`: `activeSpool = spine.flatMapLatest { it?.activeSpool ?: flowOf(null) }` + `spoolmanPresent = capabilities.map { it.hasComponent("spoolman") }` (the D-02 drawer-greying input).
- `SpoolmanNotifyRouterTest.kt`: RED `fail()` bodies replaced with typed assertions driving the real `JsonRpcClient.dispatch` over the live golden (routes 3 then 5 in order; status_changed routes spoolman_connected; the ten proc_stat frames are ignored).

## Verification

- `:app:compileDebugKotlin` → BUILD SUCCESSFUL (after each task).
- `:app:testDebugUnitTest --tests *SpoolmanNotifyRouterTest` → 3/3 GREEN.
- `:app:testDebugUnitTest` (full) → **510 tests, 0 failures, 0 errors**. The previously-RED QR/scan/gate unit scaffolds were already closed by 11-02/11-03; the only remaining `fail("RED")` is `ScanSurfaceLifecycleTest` (an **androidTest**, owned by 11-07, not in the unit run).
- `:app:testDebugUnitTest --tests *CommandCatalogDriftTest` → GREEN (the four new catalog IDs + matrix rows pass the drift guard).
- `:app:assembleDebug` → BUILD SUCCESSFUL — the facade wires into the live MoonrakerService session graph and the APK packages.
- Grep gates: `ActiveSpoolFacade` present in MoonrakerService.kt (construct + `activeSpool = activeFacade.activeSpool`); `import android` count in ActiveSpoolFacade.kt = 0; JsonRpcClient exposes both SharedFlows + `spoolNotifyParam` reads `params[0]`; both `ComponentPresent("spoolman")` predicates present; AppContainer exposes `activeSpool` + `spoolmanPresent`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added printer-matrix command_availability rows for the four new specs**
- **Found during:** Task 1 (`CommandCatalogDriftTest` would fail on the new registry catalog IDs).
- **Issue:** `CommandCatalogDriftTest.registryCommandsHaveMatrixAvailabilityRows` requires EVERY `CommandRegistry.all` catalog ID to have a `command_availability` entry in `docs/commands/printer-matrix.json`; the four spoolman specs had none. Additionally `predicateReferencesAreBackedByMatrixEvidence` requires the `ComponentPresent("spoolman")` predicate to have matrix evidence per printer.
- **Fix:** Added four `command_availability` rows (status/get ungated `always`; post/proxy `component_present:spoolman`). The `spoolman` component is already listed in both printers' `components` arrays (proven by the live `/server/info` goldens), so the gated specs are backed.
- **Files modified:** `docs/commands/printer-matrix.json`.
- **Commit:** `7c1c378`.

**2. [Rule 3 - Blocking] Added SpineHandle.activeSpool arg to three other construction sites**
- **Found during:** Task 3 (adding a required `SpineHandle` field broke three test construction call sites).
- **Issue:** `SpineHandle` is a data class with all-required fields; adding `activeSpool` left `AppContainerTest`, `ShellPresenceTest`, and `DrawerWebcamGatingTest` non-compiling.
- **Fix:** Added `activeSpool = MutableStateFlow(null)` to each (next to `webcams = ...`).
- **Files modified:** `AppContainerTest.kt`, `ShellPresenceTest.kt`, `DrawerWebcamGatingTest.kt`.
- **Commit:** `a8bdb16`.

### Clarifications resolved during execution
- **The live notify golden has TEN proc_stat frames, not nine** — the RED-scaffold KDoc guessed nine; the verbatim `notifications` array carries ten `notify_proc_stat_update` frames around the two `notify_active_spool_set` frames. The ignore-test asserts 10 (the real contract).
- **SpoolmanNotifyRouterTest collectors use `UnconfinedTestDispatcher`** — the bounded SharedFlows have no replay, so a `StandardTestDispatcher` collector launched in `backgroundScope` did not observe emissions; subscribing on an unconfined test dispatcher (then `advanceUntilIdle`) makes the no-replay collect deterministic.

## Known Stubs

None. Both new files (`SpoolmanClient`, `ActiveSpoolFacade`) are production implementations; the facade is fully wired into the live service graph (not an orphaned flow). The lean `MoonrakerSpoolmanClient` is constructed by the UI waves (11-05+), not this plan — that is by design (the spec provides the type; the consumer is a later wave), not a stub.

## Threat Flags

None. The two trust boundaries this plan touches (Moonraker notify frame → facade state; proxy query string → Spoolman REST) are mitigated exactly as the threat register prescribes: D-10 overwrite + status re-fetch (T-11-04-01), `runCatching` extractor/parser (T-11-04-02), and per-pair URL-encode in the client layer (T-11-04-03). No new network endpoints, auth paths, or schema surface beyond the planned spoolman methods.

## Self-Check: PASSED

- Files created: `SpoolmanClient.kt`, `ActiveSpoolFacade.kt` — both FOUND.
- Commits: `7c1c378`, `ba7d12d`, `a8bdb16` — all FOUND in git log.
- SpoolmanNotifyRouterTest GREEN; full suite 510/0; APK assembles; all grep gates pass.
