---
phase: 1
phase_slug: platform-gate-toolkit-benchmark-cleartext-smoke-test-scaffol
created: 2026-05-30
status: draft
---

# Phase 1 Validation Strategy

> **Nyquist principle:** Sample at sufficient resolution to reconstruct the signal.
> Test at sufficient coverage to verify the requirements. This document defines
> what "sufficient coverage" means for this phase.

## Validation Architecture

This is a **measurement-and-scaffold gate**, not a feature phase. Its "tests" are mostly
**on-device instrumented measurements + a build-output assertion**, not classic unit tests —
the nature of a platform go/no-go gate. The authoritative signals are produced on the **real
Nexus 7 2013 (API 23, Adreno 320, 1920×1200, armeabi-v7a) in release/R8 mode**, never on an
emulator, a debug build, or a modern phone (those lie about this hardware class — D-03/D-07).

Three measurement/verification surfaces:

1. **Frame-timing capture (toolkit go/no-go).** `:macrobenchmark` module drives an **identical
   UiAutomator script** against the **same worst-case scene built twice** (Compose + hybrid
   Views), fed by an **identical in-process synthetic 2–4 Hz feed** (D-02/D-06). System of
   record is **`adb shell dumpsys gfxinfo <pkg> framestats` CSV + a custom parser** (NOT the
   summary "Janky frames %" line), with `reset` / warmup-exclusion / fixed run-duration
   discipline and repeated dumps to beat the ~120-frame ring buffer. Macrobenchmark
   `FrameTimingMetric` (`frameDurationCpuMs`) is **corroboration only** — `frameOverrunMs`
   requires API 31+ and is absent on 23.
2. **Cleartext transport smoke test (CONN-05).** A minimal on-device release build runs the
   three-step D-10 sequence against the **live Ender 5 Plus Moonraker** (trusted-client/auth
   pre-configured per D-01b so failures are unambiguous).
3. **Build-output assertion (PKG-02).** A `verifyMinSdk` Gradle task (Variant API
   `SingleArtifact.MERGED_MANIFEST`, or `apkanalyzer manifest min-sdk` / `aapt2 dump badging`
   on the artifact) asserts the **merged-manifest** effective `minSdk == 23` — pins in
   `libs.versions.toml` alone do NOT prevent a transitive dep raising the merged floor.

Tooling: AndroidX **Macrobenchmark** (`benchmark-macro-junit4`, minSdk 23) + **UiAutomator**
(instrumented `connectedReleaseAndroidTest`); `dumpsys gfxinfo framestats` + host/JVM parser;
a `verifyMinSdk` Gradle task; OkHttp/Retrofit/kotlinx.serialization for the smoke test.

## Requirement → Test Mapping

| Requirement | Validation Method | Test Type | Success Signal |
|-------------|-------------------|-----------|----------------|
| **PKG-02** (pinned catalog protects minSdk 23 floor) | `./gradlew :app:assembleRelease` builds the pinned `libs.versions.toml` + wrapper(8.9)/JDK17/AGP8.7.0 quartet; `./gradlew verifyMinSdk` reads the **merged manifest** and fails if `minSdk != 23`; release APK installs+runs on the device | build / CI assertion | `assembleRelease` succeeds; `verifyMinSdk` prints/asserts `23`; a deliberately-introduced transitive dep with `minSdk>23` makes the build FAIL (adversarial check) |
| **CONN-05** (cleartext ws://+http:// from API 23) | On-device release build: (a) opens `ws://` to known Moonraker, (b) REST `GET server.info`/`printer.info`, (c) `objects/subscribe` to a known object and **awaits a deterministic update** (never a stray `notify_*`); manifest `usesCleartextTraffic="true"` (governs API 23) **and** NSC permitting cleartext (governs API 24+) both set | on-device integration | All three steps complete on the Nexus 7; logs distinguish cleartext-fail vs auth-reject vs timeout (no ambiguous failure) |

## Coverage Targets

Behavioral, not line coverage. The phase is "covered" when every success criterion below has a
**recorded, reproducible measurement from the real device in release mode**:

- **Toolkit verdict** is backed by raw `gfxinfo framestats` captures for **both** scenes under
  the identical workload, with the comparison recorded as an **ADR** (D-05). A verdict with only
  one toolkit measured, or measured on a debug build / emulator / modern phone, is NOT coverage.
- **Cleartext** is proven by the full three-step D-10 sequence completing — a bare `ws://` open
  is insufficient (an idle printer never emitting `notify_*` must not read as failure).
- **minSdk floor** is proven by an assertion on the **merged/shipping** artifact, not the source pin.

## Test Scenarios

### Critical Path Scenarios
Must-work scenarios. The phase fails if these don't pass.

1. **Head-to-head benchmark, both toolkits, real device, release/R8.** Compose scene and Views
   scene each run the identical UiAutomator script over the identical in-process feed at
   1920×1200. Capture framestats for each. **Pass bar (absolute floors, per D-04):** p50
   frame time **< 16.6 ms**; **zero frames > 700 ms** (frozen-frame stall detector); **no OOM
   and no GC storm** during thumbnail scroll. **Tolerance:** p95 in the **~33–50 ms** range is
   acceptable under the worst-case scene (do NOT require p95 ≤ 16.6 ms). A documented verdict +
   raw captures land in an ADR that gates Phase 2+.
2. **Real Coil decode under memory pressure (D-07).** The Files-style list decodes **actual
   PNGs via Coil 3** with downsample + real memory-cache pressure (no placeholder drawables, no
   predecoded bitmaps) on the 2 GB device — and does not OOM.
3. **Cleartext smoke test (CONN-05 / D-10).** Three-step sequence completes against the live
   Moonraker over `ws://`/`http://` from the API-23 device on the shipping `targetSdk 35`.
4. **Scaffold builds + installs (PKG-02 / D-08).** Multi-module `:app` + `:macrobenchmark`
   compiles with the pinned quartet, produces an **`armeabi-v7a`** release APK that installs and
   runs on the 32-bit device.

### Adversarial / Coverage-Gap Scenarios
- **Frozen-frame detector:** explicitly count frames > 700 ms — a single 700 ms stall fails the
  gate even if percentiles look fine.
- **GC storm / OOM watch:** scan logcat for repeated GC pauses + `OutOfMemoryError` during Coil
  thumbnail scroll.
- **Identical-workload assertion:** assert both scenes consume byte-identical feed events — any
  divergence (list length, image set, feed cadence, scroll distance) invalidates the verdict.
- **Ambiguous cleartext failure:** distinguish cleartext-block vs auth-reject vs printer-idle.
- **Merged-manifest minSdk drift:** a transitive dep declaring `minSdk > 23` MUST fail the build.
- **Alpha BOM trap:** assert `androidx.compose.ui:ui` resolves to **1.11.x**, never `1.12.x`
  (the alpha BOM channel routes to compileSdk 37 / AGP 9 — forbidden by D-09).

## Out of Scope

Explicitly NOT tested in this phase (deferred to Phase 2+ per ROADMAP / D-11):

- No connection-layer resilience / reconnect / backoff, no `PrinterState`, no capability gating,
  no panels, no auth UI.
- **No API 24+/targetSdk-35 cleartext validation** — this gate proves the **Marshmallow path
  only** (D-11); the same APK's cleartext behavior on newer devices is a later-phase concern.
- No WebRTC/MJPEG camera paths.
- Baseline Profile efficacy is **not** a gate criterion — it is a no-op on API 23 (full AOT at
  install via dex2oat), so the gate measures the plain release/R8 build as installed (D-03).
- Exact library **patch** versions are resolved by `./gradlew :app:dependencies` at scaffold
  time (all first-party), not asserted here.
