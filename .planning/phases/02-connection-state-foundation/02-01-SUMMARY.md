---
phase: 02-connection-state-foundation
plan: 01
subsystem: connection-state-spine (Wave 0 foundation)
tags: [build-config, json-rpc, contracts, test-harness, fixtures]
requires: []
provides:
  - "JVM unit-test source set (app/src/test) running via :app:testDebugUnitTest"
  - "kotlinx-coroutines-test in the version catalog (virtual time for Wave 2/3)"
  - "D-05 static config: local.properties -> BuildConfig -> DevConfig (host/port/apiKey/httpBase/wsUrl)"
  - "JSON-RPC envelope contracts (Request/Response/Error/Notification, loose JsonElement payloads)"
  - "Typed ConnectionError model + RpcError exception + classifyIdentifyError (defensive A5)"
  - "Toolkit-agnostic immutable PrinterState (KlippyState STATE-04, stale D-03, five-state ConnectionState CONN-06)"
  - "Toolkit-agnostic immutable Capabilities (powerDevices out-of-scope/empty A4)"
  - "FakeWebSocket harness + GoldenFixtures loader + golden/adversarial/fallback corpus"
affects:
  - "All Wave 2/3 plans in Phase 2 code against these contracts and test harness"
tech-stack:
  added:
    - "kotlinx-coroutines-test (test-only, tracks coroutines 1.9.0 pin)"
  patterns:
    - "Single shared MoonrakerJson { ignoreUnknownKeys; isLenient } for all parsing"
    - "Loose JsonElement payloads (no strict DTOs for status frames)"
    - "Plain Kotlin data classes for the headless spine — no Compose annotations"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/config/DevConfig.kt
    - app/src/main/java/works/mees/dinghy/net/JsonRpc.kt
    - app/src/main/java/works/mees/dinghy/net/RpcError.kt
    - app/src/main/java/works/mees/dinghy/state/PrinterState.kt
    - app/src/main/java/works/mees/dinghy/state/Capabilities.kt
    - app/src/test/java/works/mees/dinghy/net/FakeWebSocket.kt
    - app/src/test/java/works/mees/dinghy/net/GoldenFixtures.kt
    - app/src/test/java/works/mees/dinghy/FixtureSanityTest.kt
    - app/src/test/resources/golden/ (10 fixtures)
  modified:
    - gradle/libs.versions.toml
    - app/build.gradle.kts
decisions:
  - "Live Ender-5-Plus capture unavailable this autonomous run; copied the synthetic fallback corpus to the golden/*.json names — the fallback IS the autonomous floor (plan-sanctioned)"
metrics:
  duration_min: 7
  tasks: 3
  files: 15
  completed: 2026-05-30
---

# Phase 2 Plan 01: Connection & State Foundation (Wave 0) Summary

Stood up the Phase-2 testability bedrock and interface-first public contracts: a JVM unit-test source
set with virtual-time coroutines, D-05 static config (gitignored `local.properties` → `BuildConfig` →
`DevConfig`), the JSON-RPC envelope + typed-error + `PrinterState`/`Capabilities` contracts the later
waves implement against, and a redacted fixture corpus + `FakeWebSocket` harness that makes Wave 1
autonomous off-hardware.

## What Was Built

**Task 1 — Build/config foundation (commit `422f1ad`)**
- Added `kotlinx-coroutines-test` as the single new (test-only) catalog dependency, `version.ref`-tracking
  the existing `coroutines = "1.9.0"` pin. No runtime dep → the minSdk-23 floor cannot move
  (`verifyMinSdkDebug` re-confirmed green).
- Enabled `buildFeatures { buildConfig = true }` and emit `MOONRAKER_HOST/PORT/API_KEY` from a guarded
  `Properties()` read of the gitignored `local.properties`, with safe placeholder defaults
  (`192.168.1.50` / `7125` / empty) so a fresh clone compiles.
- Added the `testImplementation` block (junit, coroutines-test, serialization-json) standing up
  `app/src/test`.
- `config/DevConfig.kt` exposes `host`, `port: Int`, `apiKey: String?` (null when blank), `httpBase`,
  `wsUrl` from `BuildConfig` — mirrors the Phase-1 smoke probe's URL shapes.

**Task 2 — Public data contracts (commit `41f3e2f`)**
- `net/JsonRpc.kt`: `@Serializable` Request/Response/Error/Notification envelopes with loose
  `JsonElement?` params/result, the shared `MoonrakerJson` instance, and `JsonRpcMethods` constants.
- `net/RpcError.kt`: sealed `ConnectionError` (AuthRequired / NetworkUnavailable / ProtocolError /
  ServerError / ParseError), `RpcError` exception, and a pure `classifyIdentifyError(code, message)` —
  `-32602`/"Unauthorized" → AuthRequired, non-auth `-32602` → ProtocolError, JSON-RPC range vs server
  range split, and an UNKNOWN identify error falling back to AuthRequired defensively as the explicit
  last branch (A5).
- `state/PrinterState.kt`: plain (no Compose annotation) immutable `PrinterState` with first-class
  `klippyState: KlippyState` (STATE-04), a `stale` marker (D-03), and a five-state `ConnectionState`
  (`Connecting`/`Syncing`/`Connected`/`Disconnected`/`Error(ConnectionError)`) splitting socket-open
  (`Syncing`) from resynced-and-subscribed (`Connected`) per CONN-06.
- `state/Capabilities.kt`: plain immutable `Capabilities` (hasBed / extruderCount / fans / macros /
  powerDevices / heaters); `powerDevices` carries a KDoc noting it is out-of-scope/empty for Phase 2 (A4).

**Task 3 — Fixtures + harness (commit `8fc23ad`)**
- Synthetic fallback corpus (`fallback_objects_list` / `fallback_objects_query_snapshot` /
  `fallback_notify_status_update_stream`) — synthetic by construction, the autonomous floor.
- Adversarial fixtures: `adversarial_interleaved` (STATE-05), `adversarial_klippy_shutdown` (no-params,
  STATE-04), `adversarial_auth_identify_error` (`-32602` "Unauthorized", CONN-02), `adversarial_rest_401`
  (REST 401 marker, CONN-02).
- `FakeWebSocket` implements `okhttp3.WebSocket`: captured-send list, `replay`/`inject`-mid-sequence,
  and `simulateClosing`/`simulateFailure` hooks — frames flow through the real `onMessage` path.
- `GoldenFixtures` loader with `resolve()` that prefers live capture, falls back to the `fallback_`
  sibling.
- `FixtureSanityTest`: all 10 fixtures parse, fallback `result.objects` non-empty, identify error
  carries `-32602`+"Unauthorized", klippy shutdown has no params, redaction holds (no
  `192.168`/`.local`/abs-path/`data:image`), and `FakeWebSocket` replays a golden diff + an injected
  adversarial frame + close/failure hooks. Green on the fallback corpus alone.

## Verification

- `:app:compileDebugKotlin` — SUCCESS (BuildConfig + all contracts compile).
- `:app:verifyMinSdkDebug` — SUCCESS (merged-manifest minSdk == 23; no runtime dep added).
- `:app:testDebugUnitTest --tests *FixtureSanityTest*` — SUCCESS (exit 0 on fallback corpus alone).
- Redaction grep `192\.168|\.local|/home/|/mnt/|C:\\|data:image` over `golden/` — no matches.
- Source assertions: `@Immutable` count == 0 in both state files; `Syncing` present in PrinterState.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] KDoc `*/`-in-backticks closed a comment block early**
- **Found during:** Task 3 (first `testDebugUnitTest` run)
- **Issue:** `GoldenFixtures.kt`'s class KDoc contained `` `/golden/*.json` `` — Kotlin does not honor
  markdown backticks in comments, so the embedded `*/` closed the KDoc block, cascading into
  "Unresolved reference 'GoldenFixtures'" across the test.
- **Fix:** Reworded the KDoc to avoid the `*/` sequence.
- **Files modified:** app/src/test/java/works/mees/dinghy/net/GoldenFixtures.kt
- **Commit:** 8fc23ad

**2. [Rule 1 - Bug] `klippyShutdownHasNoParams` test called `.jsonObject` on a JSON array**
- **Found during:** Task 3 (authoring the sanity test)
- **Issue:** The first draft did `obj["frames"]!!.jsonObject` but `frames` is an array — would throw at
  runtime.
- **Fix:** Rewrote the test to pull the first frame string via `GoldenFixtures.frames(...)`, parse it,
  and assert `method == notify_klippy_shutdown` with no `params` key.
- **Files modified:** app/src/test/java/works/mees/dinghy/FixtureSanityTest.kt
- **Commit:** 8fc23ad

**3. [Rule 3 - Blocking] Reworded PrinterState KDoc to satisfy the `@Immutable`==0 grep**
- **Found during:** Task 2 (acceptance-criteria check)
- **Issue:** The acceptance criterion greps `grep -c '@Immutable' == 0`, but the KDoc explained "no
  Compose `@Immutable`/`@Stable`" — the literal token tripped the grep.
- **Fix:** Reworded to "NO Compose stability annotations" so the verifier's grep passes; intent
  (no Compose annotation leaking into the headless spine) is unchanged and code-true.
- **Files modified:** app/src/main/java/works/mees/dinghy/state/PrinterState.kt
- **Commit:** 41f3e2f

## Known Stubs

- `Capabilities.powerDevices` is intentionally always empty in Phase 2 (A4): power devices come from
  the `machine.device_power.devices` API, not `printer.objects.list`. Documented in a KDoc on the
  field; population deferred to a later phase. This is a deliberate, documented contract placeholder,
  not an unresolved stub.

## Notes for Downstream Waves

- **Live capture is still pending.** Per the plan, the golden `objects_list.json` /
  `objects_query_snapshot.json` / `notify_status_update_stream.json` are currently COPIES of the
  synthetic fallback corpus (live Ender-5-Plus capture was not available in this autonomous run). When
  hardware is available, capture + broadly-redact real frames into those three golden names as
  enrichment; `GoldenFixtures.resolve()` already prefers them automatically and the suite stays green
  either way.
- All contracts are the public surface — Wave 2 (reducer/JSON-RPC client/session) and Wave 3 (auth)
  implement against `JsonRpc`, `RpcError`, `PrinterState`, `Capabilities` and drive `FakeWebSocket`
  under `runTest` virtual time.

## Self-Check: PASSED

All 9 spot-checked created files exist on disk; all 3 task commits (422f1ad, 41f3e2f, 8fc23ad) are in git history.
