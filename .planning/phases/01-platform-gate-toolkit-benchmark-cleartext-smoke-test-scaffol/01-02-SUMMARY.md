---
phase: 01-platform-gate-toolkit-benchmark-cleartext-smoke-test-scaffol
plan: 02
subsystem: cleartext-moonraker-smoke
tags: [cleartext, moonraker, websocket, okhttp, jsonrpc, conn-05, d-10, d-11, on-device]
dependency_graph:
  requires:
    - "01-01 (shared AndroidManifest cleartext posture + network_security_config + INTERNET; OkHttp/kotlinx.serialization deps from the catalog)"
  provides:
    - "CleartextMoonrakerSmokeTest — throwaway 3-step instrumented probe (ws:// open -> http:// REST GET -> objects/subscribe-and-await), host/port via instrumentation args"
    - "CONN-05 evidence: cleartext ws://+http:// to a live Moonraker proven on the real flox tablet"
  affects:
    - "Phase 2+ connection layer — this probe is explicitly NOT the start of it (no PrinterState/reconnect/capability-gating); it only proves the cleartext path works"
tech_stack:
  added:
    - "androidTest deps (androidx.test runner/rules/ext-junit) wired in app/build.gradle.kts androidTestImplementation"
  patterns:
    - "JSON-RPC id correlation for the subscribe ack, then await a SPECIFIC subscribed-object notify_status_update with timeout (deterministic, not a stray notify_*)"
key_files:
  created:
    - app/src/androidTest/java/works/mees/dinghy/smoke/CleartextMoonrakerSmokeTest.kt
  modified:
    - app/build.gradle.kts
decisions:
  - "Host/port read from instrumentation args (moonrakerHost/moonrakerPort, placeholder defaults 192.168.1.50/7125) so no real printer IP is baked into the committed throwaway test."
  - "ACCEPTED the API-30 on-device proof for CONN-05 (orchestrator + user decision). The physical flox tablet runs LineageOS 18.1 / Android 11 / API 30, NOT stock Android 6 / API 23. On API 30 the manifest usesCleartextTraffic flag is ignored and the NSC governs — so the run proves the NSC (API-24+) cleartext path. The API-23 manifest-flag path is config-validated (present + correct in the shared manifest, owned by 01-01) and deferred for emulator/stock-6 proof."
  - "minSdk 23 RETAINED as the install floor (keeps the APK installable on both stock-6 devices and this Lineage-11 one). The device-reality finding changes how on-metal evidence is labeled, not the floor."
metrics:
  duration_min: 9
  completed: 2026-05-30
  tasks: 3
  files: 2
---

# Phase 01 Plan 02: Cleartext Moonraker Smoke Test Summary

A disposable 3-step instrumented probe proving the cleartext `ws://` + `http://`
Moonraker path works on the real tablet against the live Ender 5 Plus Moonraker.
This is the CONN-05 go/no-go — the only Moonraker integration path on a trusted LAN —
and it is explicitly a throwaway probe, not the start of the Phase 2+ connection layer
(no PrinterState, no reconnect, no shared connection class).

## What Was Built

- **`CleartextMoonrakerSmokeTest`** (`app/src/androidTest/...`) — a self-contained instrumented
  test executing D-10's three steps in one method (`cleartextMoonraker_threeStepSmoke`):
  1. **ws:// onOpen** — OkHttp websocket to `ws://<host>:<port>/websocket` reaches `onOpen` (10s timeout).
  2. **http:// REST GET** — `GET .../server/info` (asserts 2xx + `result.klippy_state`) and `.../printer/info`
     (asserts 2xx + `result.state`).
  3. **objects/subscribe + await deterministic update** — sends `printer.objects.subscribe` for
     `heater_bed` with JSON-RPC `id=9001`, correlates the ack by that id, then awaits a
     `notify_status_update` naming `heater_bed` (15s timeout). FAILS on timeout; a stray `notify_*`
     for any other object does NOT pass. `objects/subscribe` forces an immediate snapshot, so it is
     deterministic even on an idle printer.
- Host/port are **instrumentation args** (`moonrakerHost`/`moonrakerPort`), overridden at run time —
  no printer IP committed.
- androidTest deps added to `app/build.gradle.kts` (`androidTestImplementation`) from the catalog.

## Verification Evidence (CONN-05)

**Task 1 (inherited cleartext config — read-only verify):** PASS. NSC sets `cleartextTrafficPermitted="true"`;
manifest has BOTH `usesCleartextTraffic="true"` and `networkSecurityConfig="@xml/network_security_config"`
and declares `android.permission.INTERNET`. No shared files edited; no dependency gap against 01-01.

**Task 2 (compile):** `:app:assembleAndroidTest` → BUILD SUCCESSFUL, EXIT 0 (proven via `E:\Android\gw.bat`).
grep tokens present (`objects/subscribe`, `server.info`).

**Task 3 (on-device run — the CONN-05 proof):**
```
:app:connectedAndroidTest -P...moonrakerHost=192.168.1.120 -P...moonrakerPort=7125
> Task :app:connectedDebugAndroidTest
Starting 1 tests on Nexus 7 - 11
Finished 1 tests on Nexus 7 - 11
BUILD SUCCESSFUL in 1m 9s
```
Test report (`app/build/outputs/androidTest-results/connected/debug/`):
`testsuite CleartextMoonrakerSmokeTest tests=1 failures=0 errors=0 skipped=0 time=4.354`,
`testcase cleartextMoonraker_threeStepSmoke time=1.235`, timestamp `2026-05-30T15:59:25`.

- **Device (real hardware):** `device=flox` (Nexus 7 2013), `abi=armeabi-v7a` (32-bit), serial `0a64b42e`,
  on `192.168.1.122` (same `/24` as Moonraker). **NOT an emulator, NOT a mock.**
- **Live Moonraker:** `ender5plus`, `klippy_state=ready`, `printer.info state=ready` at `192.168.1.120:7125`.

**CONN-05 SATISFIED** — cleartext ws:// connected, REST GET server/printer.info returned 2xx, and a
deterministic `heater_bed` `objects/subscribe` update was awaited and received, on the real device against
the live printer.

## Device-Reality Finding (IMPORTANT — affects how this evidence is read)

The physical "Nexus 7 2013" is running **LineageOS 18.1 = Android 11 = API 30** (`ro.build.version.sdk=30`,
`ro.lineage.version=18.1-20251011-UNOFFICIAL-flox`), **not** stock Android 6.0.1 / API 23. The hardware is
genuine flox (Adreno 320, 2GB, 1920×1200, `armeabi-v7a`).

Consequence for cleartext: on **API ≥ 24** Android **ignores the manifest `usesCleartextTraffic` flag** and
honors the **NSC**. So this run proves the **NSC (API-24+) cleartext path** — which is exactly the path this
tablet uses in real life. The **API-23 manifest-flag path** (governs stock-6 devices) was NOT exercised on
metal; it is **config-validated** (flag present + correct in the shared manifest) and **deferred** for an
emulator/stock-6 proof if stock-6 ever becomes a shipping target.

Per user decision: **accept this API-30 proof for CONN-05**, keep **minSdk 23** as the install floor.
This is the realized form of the plan's D-11 deferred-validation item (broad cleartext also permits API-24+).

## Threat Register Coverage

- **T-01-04 / T-01-05 (cleartext + LAN MITM):** accepted — trusted-LAN scope; Moonraker is the only
  integration surface; printer + tablet share the LAN. Proven working, documented as a deliberate decision.
- **T-01-06 (broad NSC permits cleartext on API-24+):** accepted/flagged-deferred (D-11). Now concretely
  relevant because the actual device is API 30. Later phase should tighten the NSC to a Moonraker-host-scoped
  `<domain-config>`.

## Deviations from Plan

- **Device API mismatch (environmental, not a code deviation):** plan assumed API 23; real device is API 30.
  Handled by the decision above (accept NSC-path proof, retain minSdk 23, defer the manifest-flag-path proof).
- **androidTest deps:** added `androidTestImplementation` entries to `app/build.gradle.kts` (owned by 01-01).
  Legitimate consumer change required to compile/run the instrumented probe; shared manifest/NSC untouched.

## Known Stubs / Throwaway

`CleartextMoonrakerSmokeTest` is a disposable Phase-1 probe (marked as such in code). It deliberately
introduces no PrinterState, reconnect, capability gating, or shared connection class — that is Phase 2+.

## Notes for Downstream

- The Phase 2 connection layer should reuse the OkHttp-websocket + JSON-RPC-id-correlation pattern this probe
  exercised, but as a proper supervised connection (reconnect/backoff, StateFlow), not this throwaway.
- The benchmark (01-04) will run on THIS same Lineage-11 device — record the Compose-vs-Views verdict with an
  explicit ART caveat (API-30 runtime is newer/faster than stock-6; same GPU/panel).
- If stock-6 support is ever firmed up: prove the API-23 manifest-flag cleartext path on an API-23 emulator,
  and scope the NSC to the Moonraker host.

## Self-Check: PASSED
