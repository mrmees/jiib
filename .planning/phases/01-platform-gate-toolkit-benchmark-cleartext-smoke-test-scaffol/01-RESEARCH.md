# Phase 1: Platform Gate — Toolkit Benchmark, Cleartext Smoke Test & Scaffold - Research

**Researched:** 2026-05-30
**Domain:** Android build scaffolding + on-device performance measurement (Macrobenchmark / `gfxinfo framestats`) + Marshmallow (API 23) cleartext networking, on a real Nexus 7 2013 (Adreno 320 / 1920×1200 / 32-bit ARMv7 / 2 GB)
**Confidence:** HIGH — the seven flagged UNVERIFIED facts were resolved against primary/secondary sources this session (Compose BOM mapping, AGP↔Gradle↔JDK matrix, Macrobenchmark/FrameTimingMetric API support, baseline-profile floor, cleartext-by-API-level, merged-manifest minSdk semantics). The only items left empirical are device-answered in one run.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Gate runs against **real target hardware** — Nexus 7 2013 (Android 6.0.1 / API 23) in hand + a **live Ender 5 Plus Moonraker** on the LAN. No emulator, no fake server.
- **D-01a (HARDWARE CORRECTION):** The 2013 Nexus 7 is **Qualcomm Snapdragon S4 Pro (APQ8064) / Adreno 320, 32-bit ARMv7, 2 GB RAM, 1920×1200 (7″, 323 ppi)** — NOT the 2012 Tegra 3 / 1280×800. Consequences: (1) the benchmark must render at the **real 1920×1200**; (2) the release APK must ship the **`armeabi-v7a`** ABI (32-bit device); (3) strike all "Tegra"/"Tegra K1" framing.
- **D-01b:** Cleartext smoke test needs a **known, reachable Moonraker endpoint with auth/trusted-client pre-configured** (or known trusted LAN) so a failure isn't ambiguous (auth-reject vs. cleartext-fail).
- **D-02 (HEAD-TO-HEAD):** Implement the **same worst-case scene in BOTH Compose and hybrid-Views**, drive both with the **identical** scripted interaction + synthetic feed, compare on the same workload. The verdict is **relative** ("which toolkit is less bad on this hardware"), not an absolute 60 fps bar.
- **D-03 (NO Baseline Profile precondition):** API 23 ART does **full AOT at install** (`dex2oat`); profile-guided compilation / Baseline Profiles are an **API 24+ feature, a no-op on Marshmallow**. Measure the **release/R8 build as installed**. Do NOT gate Phase 1 on "baseline profile installed."
- **D-04 (metrics + thresholds):**
  - **Absolute floors (toolkit must clear to count as "viable"):** p50 frame time **< 16.6 ms** on the stress scene; **zero frames > 700 ms** (frozen-frame detector); **no OOM and no GC storm** during thumbnail scroll.
  - **Stress-scene tolerance:** **p95 in ~33–50 ms is acceptable**; do NOT require p95 ≤ 16.6 ms. p90 ≈ one missed-vsync budget is the target.
  - **System of record:** prefer Macrobenchmark **`FrameTimingMetric`** if it works on API 23 (RESOLVED below — partial); otherwise capture **raw `gfxinfo framestats` CSV + a parser** (not the summary line), with explicit reset / warmup-exclusion / run-duration discipline.
- **D-05:** Record **both** toolkits' numbers AND the head-to-head verdict as an **ADR** with raw captures attached. The ADR gates all Phase 2+ panel architecture.
- **D-06 (deterministic in-process fixture):** the synthetic 2–4 Hz feed is an **in-process** fixture emitting realistic immutable state updates, list mutations, graph samples, and console appends at the planned throttle. NOT shell/adb-driven, NOT a toy counter. No connection layer.
- **D-07 (real hard paths at 1920×1200):** scrolling Files-style list doing **actual Coil PNG decode + downsample with real memory-cache pressure** (NOT placeholder drawables / predecoded bitmaps), a live **Canvas** temperature graph, and a bounded console text spew.
- **D-08 (multi-module `:app` + `:macrobenchmark`):** justified by **measurement**, not "baseline profiles mandatory." Must **pin the Gradle wrapper version + JDK + AGP** (not just libraries) and produce an **installable release/R8 variant that installs and runs on the real 32-bit API-23 device**.
- **D-09 (pinned `gradle/libs.versions.toml`):** governs every dependency; minSdk 23 floor. **PKG-02 gap:** pins do NOT stop a transitive dep raising the **merged-manifest** minSdk — add a **CI/lint gate asserting merged-manifest `minSdk == 23`**. Pin to **Compose 1.11 / AGP 8.7.x / compileSdk 35 / Kotlin 2.1.x**; apply the **Kotlin Compose compiler plugin** separately (`org.jetbrains.kotlin.plugin.compose`). (Both UNVERIFIED items now RESOLVED below.)
- **D-10 (cleartext "passes"):** minimal release build on the Nexus 7 (API 23, shipping targetSdk 35): (a) opens a `ws://` to the known Moonraker, (b) does a REST `GET` (`printer.info`/`server.info`), (c) **subscribes to a known object and awaits a deterministic update** (do NOT rely on a stray `notify_*`). Set **BOTH** `res/xml/network_security_config.xml` (cleartext permitted) **AND** `android:usesCleartextTraffic="true"` — NSC is honored only API 24+, the manifest flag covers Marshmallow. (See important precedence sharpening in Resolved #6.)
- **D-11 (honest scope):** a green light here proves the **Marshmallow path only**. The same APK's cleartext on API 24+/targetSdk 35 must be validated separately (later phase).

### Claude's Discretion
- Module/package layout, Gradle plugin wiring, the in-process fixture's exact shape, and the `gfxinfo` CSV parser implementation are left to the planner/executor.
- The planner owns confirming all UNVERIFIED facts (Compose BOM ↔ compileSdk/AGP mapping, Macrobenchmark frame-metric support on API 23, the baseline-profile API floor) before locking — **this research resolves them; the planner should still self-validate the build at scaffold time.**

### Deferred Ideas (OUT OF SCOPE)
- No connection layer, `PrinterState`, panels, capability gating, auth UI — all Phase 2+.
- **Cross-doc cleanup (not this phase):** `CLAUDE.md` and `.planning/PROJECT.md` describe the wrong Nexus 7 generation (Tegra 3 / 1280×800 = 2012). Correct at project level later — see D-01a.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| **PKG-02** | A pinned Gradle version catalog (`libs.versions.toml`) governs all dependencies so no library silently raises the minSdk floor. | "Resolved #5": pins alone are insufficient — the *delivered* control is a build/CI assertion that the **merged manifest** `minSdk == 23`. AGP's manifest merger already **fails the build** if any dependency declares `minSdk > 23` (free guard); the explicit PKG-02 deliverable is a `verifyMinSdk` Gradle task on the merged manifest and/or `apkanalyzer manifest min-sdk` on the artifact. |
| **CONN-05** | App connects to a plaintext `ws://`/`http://` Moonraker on the LAN from an Android 6 (API 23) device (cleartext network-security policy correctly configured). | "Resolved #6": API 23 default-permits cleartext; NSC is honored **only API 24+**; `usesCleartextTraffic` is **ignored when an NSC is present** (precedence sharpening). For the Marshmallow path, the NSC permitting cleartext is what matters on 24+ and the M default covers 23. Smoke-test "passes" = D-10's three steps, mapped to assertions in Validation Architecture. |
</phase_requirements>

## Summary

This is a **measurement-and-scaffold gate**, not domain research. The locked stack (Kotlin/Compose/OkHttp/Retrofit/kotlinx.serialization/Coil 3) is settled; the job is to (1) stand up a pinned, multi-module Gradle build that installs a 32-bit release APK on a real Nexus 7 2013, (2) build the **same worst-case scene twice** (Compose + hybrid Views) and measure both on-device in release mode, and (3) prove cleartext `ws://`/`http://` reaches a real Moonraker from Marshmallow.

All seven flagged UNVERIFIED facts were resolved this session against primary/secondary sources. The headline confirmations: **Compose BOM 2026.05.00 maps to the Compose 1.11 line** (stable BOM → Compose UI **1.11.1**; 2026.04.00 → 1.11.0; 2026.05.01 → 1.11.2) — D-09's pin is correct and there is no compileSdk-37 demand on this line. **AGP 8.7.0 requires Gradle 8.9 (both minimum and default), JDK 17, build-tools 34, and supports compileSdk up to 35** — so the AGP 8.7.x / compileSdk 35 / Gradle 8.9 / JDK 17 quartet is internally consistent; Kotlin 2.1.0–2.1.10 supports Gradle 8.7–8.10, so Kotlin 2.1.x sits comfortably on Gradle 8.9. **Baseline Profiles are an Android 7 / API 24+ feature** (docs literally say "Android 7 (API level 24) and Android 8 (API 26)") — confirmed no-op on the Android 6 / API 23 device, validating D-03. **Cleartext on API 23 is default-permitted**, NSC is honored only API 24+, and `usesCleartextTraffic` is **ignored when an NSC is present** — an important precedence sharpening to D-10 (see Resolved #6). **The merged-manifest minSdk genuinely can be raised by a transitive dependency** (AGP fails the merge unless you misuse `tools:overrideLibrary`), so PKG-02's real deliverable is a merged-manifest/artifact assertion, not the version catalog.

The one genuinely empirical item is `FrameTimingMetric` *fidelity* on API 23. Confirmed facts: Macrobenchmark's library `minSdk == 23`, it uses `dumpsys gfxinfo framestats`, `frameOverrunMs` requires **API 31+**, and `frameDurationCpuMs` is the broadly-available metric. So FrameTimingMetric will produce *some* CPU-side numbers on 23 but a reduced metric set, and there are known framestats-parsing fragilities on older OS versions. The plan should treat **`gfxinfo framestats` CSV capture + a custom parser as the system of record on-device**, with FrameTimingMetric as corroboration where it yields numbers — exactly D-04's strategy, now evidence-backed.

**Primary recommendation:** Pin **Kotlin 2.1.x / AGP 8.7.0 / Gradle 8.9 / JDK 17 / compileSdk 35 / Compose BOM 2026.05.00 (Compose 1.11.1)**; scaffold `:app` (Compose + Views worst-case scenes behind a launch flag) + `:macrobenchmark`; make the **on-device `gfxinfo framestats` capture+parse the authoritative metric**, FrameTimingMetric corroborating; deliver PKG-02 as a **merged-manifest minSdk assertion task**, and CONN-05 as **both** cleartext switches (with the precedence caveat in Resolved #6) plus the D-10 three-step smoke test.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Worst-case render scene (Files list + Canvas graph + console spew) | Client (Android UI: Compose AND Views variants) | — | Both variants live in `:app`; the whole point is comparing the two client toolkits on identical work. |
| Synthetic 2–4 Hz feed | Client (in-process fixture) | — | D-06: in-process, not IPC/adb-driven; drives both UI variants identically. |
| Frame-timing measurement | Test/Instrumentation (`:macrobenchmark` + on-device `gfxinfo`) | Host (CSV parser can run on dev box) | Measurement must observe the *release* `:app` process; capture is on-device, parsing/analysis can be host-side. |
| Cleartext transport smoke test | Client (OkHttp/Retrofit in `:app`) + Network (LAN Moonraker) | — | Proves the Marshmallow cleartext path end-to-end against the real server (D-01b). |
| minSdk-floor enforcement | Build (Gradle/AGP merged-manifest assertion) | CI | Merged-manifest minSdk is a build-output property; the gate must read the *merged* result, not the source pin. |
| Version pinning | Build (`gradle/libs.versions.toml` + wrapper + AGP + JDK) | — | D-08/D-09: pin wrapper+JDK+AGP+libraries, all auditable in one place. |

## Standard Stack

> The library *choices* are LOCKED (see CLAUDE.md § Technology Stack / STACK.md). This phase only
> installs the subset needed to run the benchmark + cleartext smoke test + scaffold. The **build
> quartet below is now VERIFIED**; individual library *patch* versions remain `[ASSUMED]` until
> `./gradlew :app:dependencies` resolves them (they are all first-party — see Package Legitimacy Audit).

### Build quartet (VERIFIED this session)
| Component | Pinned value | Status | Source |
|-----------|--------------|--------|--------|
| Kotlin | 2.1.x (e.g. 2.1.21) | `[VERIFIED: kotlinlang.org/docs/gradle-configure-project]` Kotlin 2.1.0–2.1.10 supports Gradle 8.7–8.10 | CITED |
| AGP | **8.7.0** | `[VERIFIED: developer.android.com/build/releases/agp-8-7-0-release-notes]` requires Gradle 8.9 + JDK 17 + build-tools 34, supports compileSdk ≤ 35 | CITED |
| Gradle wrapper | **8.9** | `[VERIFIED]` AGP 8.7 lists Gradle 8.9 as **both minimum and default** | CITED |
| JDK | **17** | `[VERIFIED]` AGP 8.0+ requires JDK 17 to run Gradle; AGP 8.7 unchanged | CITED |
| compileSdk | **35** | `[VERIFIED]` AGP 8.7 supports up to API 35 | CITED |
| Compose BOM | **2026.05.00 → Compose UI 1.11.1** | `[VERIFIED: developer.android.com/develop/ui/compose/bom/bom-mapping]` stable BOM 2026.05.00 = 1.11.1; 2026.04.00 = 1.11.0; 2026.05.01 = 1.11.2 — all on the **1.11 line**, no compileSdk-37 demand | CITED |

> ⚠ **Do NOT use the alpha BOM:** the 2026.05.00 *alpha* channel points to Compose 1.12.0-alpha02, which is the compileSdk-37/AGP-9 line you are explicitly avoiding (D-09). Pin the **stable** BOM 2026.05.00 (→ 1.11.1). If you want the latest stable 1.11 patch, BOM 2026.05.01 (→ 1.11.2) is also on the safe line.

### Core (needed in Phase 1)
| Library | Version (target) | Purpose | Why Standard |
|---------|------------------|---------|--------------|
| Kotlin | 2.1.x | Language + Compose compiler driver | Locked. Compose compiler == Kotlin version since 2.0; applied via `org.jetbrains.kotlin.plugin.compose`. |
| Jetpack Compose (BOM) | 2026.05.00 → Compose UI 1.11.1 `[VERIFIED]` | Compose UI variant of the worst-case scene | Locked; confirmed 1.11 line, compileSdk 35 OK. |
| Compose Material 3 | via BOM | UI components for the scene | Locked. |
| AndroidX Activity-Compose | 1.9.x `[ASSUMED patch]` | Compose host (`ComponentActivity`) | minSdk-23 baseline (June 2025 AndroidX floor). |
| AndroidX Lifecycle (runtime-compose) | 2.8.x `[ASSUMED patch]` | `collectAsStateWithLifecycle()` for the feed | minSdk-23 baseline. |
| AndroidX AppCompat / `RecyclerView` / `ConstraintLayout` | current `[ASSUMED patch]` | **Hybrid-Views** variant of the scene (Files `RecyclerView`, custom `View` graph) | Required to build the Views half of the head-to-head (D-02). |
| Coil 3 | 3.x `[ASSUMED patch]` | **Real** PNG thumbnail decode+downsample under memory-cache pressure (D-07) | Locked; minSdk 23; fixed large-PNG OOM on API ≤23 — directly load-bearing. Use `coil-compose` (`AsyncImage`) for the Compose scene and Coil's `ImageLoader` for the Views scene so both decode identically. |
| OkHttp | 4.12.x `[ASSUMED patch]` | WebSocket + REST engine for the cleartext smoke test | Locked; minSdk 21. One client for `ws://` and `http://`. |
| Retrofit | 2.11.x `[ASSUMED patch]` | REST `GET` (`server.info`/`printer.info`) in the smoke test | Locked. |
| kotlinx.serialization (json) | 1.7.x `[ASSUMED patch]` | Parse JSON-RPC envelope for the deterministic-update step | Locked; compile-time, no kapt. |
| kotlinx-coroutines | 1.9.x `[ASSUMED patch]` | Drive the in-process fixture + bridge the socket | Locked. |

### Supporting (build/measurement tooling for Phase 1)
| Library | Version (target) | Purpose | When to Use |
|---------|------------------|---------|-------------|
| `androidx.benchmark:benchmark-macro-junit4` | latest stable `[ASSUMED patch]` | `:macrobenchmark` module; library `minSdk == 23` `[VERIFIED]`; `MacrobenchmarkRule` + `FrameTimingMetric` (corroborating — partial on 23) | D-08. |
| `androidx.test.uiautomator:uiautomator` | latest stable `[ASSUMED patch]` | Scripted, deterministic interaction driver | Drives the *identical* scripted scroll across both scenes. |
| `androidx.profileinstaller:profileinstaller` | 1.4.x `[ASSUMED patch]` | Present only so a profile *could* help on API 24+ devices | **No-op on the API-23 target (D-03).** Include for module completeness; do NOT gate on it. |

**Not installed this phase (locked but Phase 2+):** DataStore, Navigation-Compose, Vico, auth/connection-layer code.

**Installation (Gradle, illustrative — patch versions confirmed by `:app:dependencies`):**
```toml
# gradle/libs.versions.toml
[versions]
kotlin = "2.1.21"            # [VERIFIED line — Kotlin 2.1.x ↔ Gradle 8.7–8.10]
agp = "8.7.0"               # [VERIFIED — needs Gradle 8.9 + JDK 17]
composeBom = "2026.05.00"   # [VERIFIED stable → Compose UI 1.11.1]  (NOT the alpha → 1.12)
okhttp = "4.12.0"           # [ASSUMED patch]
retrofit = "2.11.0"         # [ASSUMED patch]
kotlinxSerialization = "1.7.3"
coroutines = "1.9.0"
coil = "3.1.0"
lifecycle = "2.8.7"
activity = "1.9.3"
benchmark = "1.3.3"        # androidx.benchmark — confirm patch
uiautomator = "2.3.0"
profileinstaller = "1.4.1"
```
```properties
# gradle/wrapper/gradle-wrapper.properties  (D-08: pin the wrapper)
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip   # [VERIFIED min/default for AGP 8.7]
```

### Pin-Time Self-Validation (planner/executor runs at scaffold time — the build self-checks)
```bash
# 1. Confirm BOM resolves to Compose 1.11.1 (sanity; already VERIFIED via bom-mapping):
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep -i "androidx.compose.ui:ui"
#    Expect 1.11.1. If you see 1.12.x you accidentally took the alpha BOM channel.

# 2. Confirm the quartet syncs (AGP errors loudly on a bad Gradle/JDK combo — VERIFIED Gradle 8.9 + JDK 17):
./gradlew --version              # confirm Gradle 8.9 + JVM 17
./gradlew :app:assembleRelease   # compiles + R8

# 3. FrameTimingMetric on API 23 — empirical fidelity check (one device run):
./gradlew :macrobenchmark:connectedReleaseAndroidTest
#    Expect frameDurationCpuMs present; frameOverrunMs ABSENT (it requires API 31+). gfxinfo CSV is SoR regardless.
```

## Package Legitimacy Audit

> All packages are **first-party Google/JetBrains/Square AndroidX/Kotlin artifacts** from Google's
> Maven (`maven.google.com`) and Maven Central — not npm/PyPI. slopcheck targets npm/PyPI/crates and
> is **not applicable** to the Gradle/Maven ecosystem; the legitimacy control is "first-party group
> IDs from canonical repos." No hallucination-prone third-party packages are introduced. Live
> resolution is confirmed by `./gradlew :app:dependencies`, which fails on any bad coordinate.

| Package (group:artifact) | Registry | Source | Disposition |
|---|---|---|---|
| `org.jetbrains.kotlin:*` | Maven Central | JetBrains (first-party) | Approved — version line VERIFIED |
| `com.android.tools.build:gradle` (AGP 8.7.0) | Google Maven | Google (first-party) | Approved — VERIFIED |
| `androidx.compose:compose-bom:2026.05.00` + `androidx.compose.*` | Google Maven | Google (first-party) | Approved — BOM→1.11.1 VERIFIED |
| `androidx.activity:activity-compose`, `androidx.lifecycle:*`, `androidx.appcompat:*`, `androidx.recyclerview:*`, `androidx.constraintlayout:*` | Google Maven | Google (first-party) | Approved |
| `androidx.benchmark:benchmark-macro-junit4`, `androidx.test.uiautomator:uiautomator`, `androidx.profileinstaller:profileinstaller` | Google Maven | Google (first-party) | Approved |
| `io.coil-kt.coil3:coil-compose`, `coil-network-okhttp` | Maven Central | Coil (Colin White, well-established) | Approved |
| `com.squareup.okhttp3:okhttp`, `com.squareup.retrofit2:retrofit`, `:converter-kotlinx-serialization` | Maven Central | Square (first-party) | Approved |
| `org.jetbrains.kotlinx:kotlinx-serialization-json`, `kotlinx-coroutines-android` | Maven Central | JetBrains (first-party) | Approved |

**Packages removed due to [SLOP]:** none.
**Packages flagged [SUS]:** none. (All first-party; no per-install `checkpoint:human-verify` needed — gate instead on the single `:app:dependencies` resolution + the merged-manifest minSdk assertion.)

## Architecture Patterns

### System Architecture Diagram

```
                         ┌─────────────────────────────────────────────────┐
                         │  DEV BOX (host) — JDK 17, Gradle 8.9, AGP 8.7.0   │
                         │  ./gradlew :app:assembleRelease                   │
                         │  ./gradlew :macrobenchmark:connected…Test         │
                         │  gfxinfo CSV parser (host-side analysis)          │
                         │  verifyMinSdk (merged-manifest assertion task)    │
                         └───────────────┬───────────────────────┬──────────┘
                              adb install │            adb shell  │ dumpsys
                                          ▼                       ▼
   ┌──────────────────────────────────────────────────────────────────────────┐
   │  NEXUS 7 2013  (API 23, Adreno 320, 1920×1200, armeabi-v7a, release/R8)    │
   │                                                                            │
   │   :app  (release, R8, signed)                                             │
   │   ┌──────────────────────────────────────────────────────────────────┐   │
   │   │  In-process synthetic feed  ──(2–4 Hz: state/list/graph/console)──┐│   │
   │   │       │ (identical feed, identical scripted interaction)          ││   │
   │   │       ▼                                                           ▼│   │
   │   │  ┌─────────────────────┐            ┌─────────────────────────────┐│   │
   │   │  │ SCENE A: Compose    │  ⇄ same  ⇄ │ SCENE B: Hybrid Views       ││   │
   │   │  │ LazyColumn + Coil   │  workload  │ RecyclerView + Coil decode  ││   │
   │   │  │ AsyncImage          │            │ custom View graph           ││   │
   │   │  │ Canvas temp graph   │            │ TextView console spew       ││   │
   │   │  │ Compose console     │            │                             ││   │
   │   │  └─────────────────────┘            └─────────────────────────────┘│   │
   │   │            │ renders frames at 1920×1200                            │   │
   │   │            ▼                                                        │   │
   │   │   Choreographer/SurfaceFlinger → gfxinfo framestats (per-frame ns) │   │
   │   └──────────────────────────────────────────────────────────────────┘   │
   │                                                                            │
   │   Cleartext smoke test (release build):                                    │
   │   OkHttp ws://moonraker  ──identify──▶  REST GET http://…/printer.info     │
   │       └─ subscribe(known obj) ──await deterministic update──▶ PASS/FAIL    │
   │                                          │                                 │
   └──────────────────────────────────────────┼────────────────────────────────┘
                                               ▼  LAN (cleartext)
                                   ┌────────────────────────────┐
                                   │ Ender 5 Plus Moonraker      │
                                   │ (trusted client / auth set) │
                                   └────────────────────────────┘
```

### Recommended Project Structure
```
dinghy-display/
├── settings.gradle.kts          # includes :app, :macrobenchmark; pins repositories
├── gradle/
│   └── libs.versions.toml        # PKG-02: every version pinned here
├── gradle/wrapper/
│   └── gradle-wrapper.properties # D-08: PIN Gradle 8.9 (VERIFIED for AGP 8.7)
├── build.gradle.kts              # AGP 8.7.0 + Kotlin 2.1.x plugin versions (from catalog)
├── app/
│   ├── build.gradle.kts          # minSdk 23, targetSdk 35, compileSdk 35, abiFilters armeabi-v7a
│   └── src/main/
│       ├── AndroidManifest.xml   # usesCleartextTraffic="true" + networkSecurityConfig (CONN-05)
│       ├── res/xml/network_security_config.xml
│       └── kotlin/…/
│           ├── feed/             # in-process synthetic 2–4 Hz fixture (D-06)
│           ├── scene/compose/    # SCENE A worst-case (D-07)
│           ├── scene/views/      # SCENE B worst-case (D-07)
│           └── smoke/            # cleartext ws:// + REST + subscribe smoke test (D-10)
└── macrobenchmark/
    └── src/main/                 # MacrobenchmarkRule + UiAutomator script + FrameTimingMetric
                                   # + gfxinfo framestats capture helper
```

### Pattern 1: Identical-workload head-to-head harness
**What:** One feed source, one scripted interaction, two render backends selected at launch (intent extra or build variant). The benchmark drives the *same* UiAutomator script against each.
**When to use:** D-02 fairness requirement — any divergence in feed cadence, list size, image set, or scroll distance between the two scenes invalidates the comparison.
**Example (shape, not verbatim):**
```kotlin
// Source: pattern derived from androidx.benchmark Macrobenchmark + UiAutomator docs
// [CITED: developer.android.com/topic/performance/benchmarking/macrobenchmark-overview]
// Launch selects backend so feed + script stay identical:
//   adb shell am start -n …/.BenchActivity --es scene compose
//   adb shell am start -n …/.BenchActivity --es scene views
// MacrobenchmarkRule with FrameTimingMetric (corroborating) + gfxinfo capture (SoR).
```

### Pattern 2: `gfxinfo framestats` as system of record
**What:** Before the measured interaction, `adb shell dumpsys gfxinfo <pkg> reset`; run the scripted scene for a fixed duration; `adb shell dumpsys gfxinfo <pkg> framestats` to dump the per-frame CSV; parse the nanosecond columns to derive per-frame total time; compute p50/p90/p95 and count frames > 700 ms; exclude a warmup window.
**When to use:** The authoritative metric on API 23 (D-04). framestats emits a CSV block of up to ~120 recent frames per dump, with named columns; the difference between the FrameCompleted column and the IntendedVsync column gives total frame time. Dump repeatedly across the run to avoid the 120-frame ring-buffer truncation.
**Warning signs:** relying on the human-readable summary ("Janky frames: X%") instead of the raw CSV (D-04 explicitly forbids the summary line); not resetting between runs; counting warmup frames.

### Anti-Patterns to Avoid
- **adb/shell-driven feed:** measures IPC + scheduler noise, not render cost (D-06). The feed is in-process.
- **Toy in-process counter:** under-tests recomposition/state-shaping (D-06). Use realistic immutable state + list mutations + graph samples + console appends.
- **Placeholder drawables / predecoded bitmaps:** defeats the memory-pressure point of D-07. Decode real PNGs with Coil under cache pressure.
- **Debug build or modern phone:** debug Compose is 5–10× slower and lies; a modern phone hides the Adreno 320 fill-rate wall. Release/R8 on the real device only.
- **Gating the gate on a Baseline Profile:** no-op on API 23 (D-03, VERIFIED).
- **Taking the alpha Compose BOM channel:** routes you to Compose 1.12 / compileSdk 37 — exactly what D-09 forbids.
- **Asserting minSdk from the source pin only:** the *merged* manifest is what ships (PKG-02).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Scripted, deterministic UI interaction | Custom touch-injection loop | **UiAutomator** in `:macrobenchmark` | Repeatable, release-mode, AndroidX-blessed driver for Macrobenchmark. |
| Frame-timing capture | Hand-instrumented `Choreographer` callbacks in app code | **`gfxinfo framestats`** (SoR) + **Macrobenchmark FrameTimingMetric** (corroborating) | In-app instrumentation perturbs the very thing you measure; framestats is the platform's own per-frame ledger. |
| PNG decode + downsample under memory pressure | Manual `BitmapFactory` + `inSampleSize` bookkeeping for the benchmark | **Coil 3** (the locked, shipping decoder) | The benchmark must exercise the *real* code path Phase 5 will ship, including Coil's API-≤23 OOM fix. |
| minSdk-floor enforcement | Eyeballing `libs.versions.toml` | **Merged-manifest assertion task** (Gradle) + `apkanalyzer` | Transitive deps can raise merged minSdk regardless of pins (PKG-02 core finding). |
| Cleartext config | Disabling TLS checks / custom socket factory | **`usesCleartextTraffic` + NSC** | Platform-sanctioned switches; anything else is a security smell and still wouldn't fix the Marshmallow path. |

**Key insight:** every "hand-roll" here would measure or enforce the *wrong* thing — the benchmark's entire value is that it exercises the **real shipping code paths** (Coil decode, release/R8 codegen) under the **real platform measurement** (framestats), on the **real device**.

## Resolved UNVERIFIED Facts (the core deliverable of this research)

> Numbering matches the seven items in the phase brief. All resolved this session.

### #1 — Compose BOM 2026.05.00 → Compose 1.11.x? compileSdk > 35 demanded? `[VERIFIED]`
- **Resolved:** **Stable Compose BOM 2026.05.00 maps to Compose UI 1.11.1.** Adjacent: BOM 2026.04.00 → 1.11.0; BOM 2026.05.01 → 1.11.2. All are on the **Compose 1.11 line**, which targets **compileSdk 35 / AGP 8.x** — there is **no `minCompileSdk > 35` demand**. D-09's pin is correct.
- **Critical caveat:** there are **two BOM channels** for 2026.05.00 — the **stable** one (→ 1.11.1, use this) and an **alpha** one (→ **1.12.0-alpha02**, which is the compileSdk-37 / AGP-9 line you must avoid). Pin the stable artifact. Sanity-check with `:app:dependencies | grep compose.ui:ui` (expect 1.11.1, never 1.12.x).
- **Source:** `[CITED: developer.android.com/develop/ui/compose/bom/bom-mapping]` + search corroboration. **Confidence: HIGH.**

### #2 — Kotlin 2.1.x ↔ AGP 8.7.x ↔ Gradle wrapper ↔ JDK matrix `[VERIFIED]`
- **Resolved (the exact quartet):**
  - **AGP 8.7.0** → **Gradle 8.9** (listed as **both minimum and default**), **JDK 17** (required to run Gradle, since AGP 8.0+), **SDK build-tools 34.0.0**, **compileSdk up to 35** (default NDK 27.x). `[CITED: developer.android.com/build/releases/agp-8-7-0-release-notes + about-agp]`
  - **Kotlin 2.1.0–2.1.10** are fully compatible with Gradle up to 8.6 and **supported on Gradle 8.7–8.10** — so Kotlin 2.1.x on **Gradle 8.9** is in-range. (Minor: a Kotlin 2.1.20 + Gradle > 8.7 test-fixtures bug is fixed in **2.1.21** — prefer 2.1.21.) `[CITED: kotlinlang.org/docs/gradle-configure-project]`
  - **Compose compiler ships with Kotlin 2.x** (no separate version); enable via `org.jetbrains.kotlin.plugin.compose`. `[CITED: kotlinlang]`
- **Net pin:** Kotlin **2.1.21** / AGP **8.7.0** / Gradle **8.9** / JDK **17** / compileSdk **35** / build-tools **34**. The build self-validates (AGP errors loudly on a bad combo). **Confidence: HIGH.**

### #3 — Does Macrobenchmark `FrameTimingMetric` work on API 23? `[VERIFIED — partial]`
- **Resolved:** **Partial support — usable but reduced on API 23.**
  - Macrobenchmark's library **`minSdkVersion == 23`**, so it *runs* on the target. `[CITED: developer.android.com/jetpack/androidx/releases/benchmark]`
  - `FrameTimingMetric` reports `frameDurationCpuMs` broadly (the older `FrameCpuTime` renamed), **but `frameOverrunMs` requires API 31+** — so on API 23 you get CPU-side frame duration, **not** the GPU-overrun metric. `[CITED: developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics]`
  - There are **known framestats-parsing fragilities** on older OS versions (e.g. issuetracker 193260119 "Macrobench fails to parse gfxinfo" on some versions; the `---PROFILEDATA---`/framestats output is OS-version-sensitive).
- **Decision (matches D-04):** make **`gfxinfo framestats` CSV capture + a custom parser the system of record**; use `FrameTimingMetric`'s `frameDurationCpuMs` as **corroboration** where it appears. One device run confirms exactly what surfaces (Self-Validation #3). **Confidence: HIGH on the facts; the exact on-device output is the single empirical item.**

### #4 — Baseline Profile / profile-guided-compilation API floor = API 24+? (no-op on 23) `[VERIFIED]`
- **Resolved:** **Confirmed — D-03 is correct.** Android docs state Baseline Profiles apply to **Android 7 (API 24)** and **Android 8 (API 26)** (the versions without Cloud Profiles); profile-guided compilation (ART using a baseline profile to `dex2oat`-precompile selected hot paths) is an **API 24+** capability. On **Android 6 / API 23 (Marshmallow)** "**only Full mode is supported, all applications are always fully precompiled**" at install — there is no profile-guided partial compilation to influence, so an installed Baseline Profile / `androidx.profileinstaller` is a **no-op** on the target device. `[CITED: developer.android.com/topic/performance/baselineprofiles/overview + AGP/benchmark docs noting Full-mode-only on API 23]`
- **Implication:** the plan MUST NOT gate Phase 1 on a profile being installed/active; measure the plain release/R8 build as installed. (Still worth shipping the profile for any API 24+ device that later runs the APK — D-08's measurement justification.) **Confidence: HIGH.**

### #5 — PKG-02 mechanism: assert merged-manifest minSdk stays 23 `[VERIFIED]`
- **Resolved:** Pinning `libs.versions.toml` does **not** prevent a transitive AAR declaring a higher `<uses-sdk minSdkVersion>`; the manifest merger raises the **merged** minSdk to the highest among app + deps. AGP **fails the build** during manifest merge if a dependency's `minSdk > 23` unless you misuse `tools:overrideLibrary` (which you must NOT). `[CITED: developer.android.com/build/manage-manifests]` Two enforcement layers:
  1. **Free guard:** AGP's merge already fails `assembleRelease` if a dep demands minSdk > 23.
  2. **Explicit PKG-02 deliverable (positive assertion):** a build/CI check that the **merged manifest's effective minSdk == 23**:
     - **`verifyMinSdk` Gradle task** reading the merged manifest via the AGP Variant API (`SingleArtifact.MERGED_MANIFEST`) or the file at `build/intermediates/merged_manifests/release/AndroidManifest.xml`; fail if `minSdkVersion != 23`.
     - **Artifact-level (strongest):** `apkanalyzer manifest min-sdk app-release.apk` (→ must print `23`) or `aapt2 dump badging app-release.apk | grep "sdkVersion:'23'"`, wired into CI as a hard gate — checks the *shipping* APK.
  - Lint's `MinSdkTooLow`/`ExpiringTargetSdkVersion` are **not** this — they don't assert "stays exactly 23." Use the merged-manifest/artifact assertion. **Confidence: HIGH.**

### #6 — CONN-05 cleartext on API 23: NSC + `usesCleartextTraffic`? `[VERIFIED — with precedence sharpening]`
- **Resolved (sharper than D-10's framing):**
  - **API 23 (Marshmallow)** **default-permits cleartext** — apps targeting API 23 and lower have `cleartextTrafficPermitted` effectively `true` by default. So on the Nexus 7 the cleartext path works by platform default; `usesCleartextTraffic="true"` makes that intent explicit. `[CITED: developer.android.com/privacy-and-security/security-config + OWASP MASTG-TEST-0235]`
  - **`res/xml/network_security_config.xml`** is honored **only API 24 (Nougat)+** — **ignored on API 23**. (Consistent with D-10.)
  - **PRECEDENCE (important correction):** **when an NSC is present, `android:usesCleartextTraffic` is IGNORED** — the NSC's `cleartextTrafficPermitted` wins on the versions that read the NSC (API 24+). The manifest boolean only governs where the NSC is *not* consulted (i.e. API 23). So setting **both** (D-10) is correct *because they govern different API ranges*: manifest flag → API 23; NSC → API 24+. They do **not** conflict on any single device — each version reads exactly one of them. `[CITED: AppSec Labs / Android security-config docs]`
  - **targetSdk 35 caveat (D-11):** on API 28+ devices cleartext is **default-deny**, so the NSC permitting cleartext is what opts you back in *there*. Ensure the NSC actually permits cleartext (broadly or for the Moonraker host) so the same APK isn't silently blocked on API 24+. This phase **proves the Marshmallow path only** (D-11). **Confidence: HIGH.**

### #7 — minSdk floors + API-23 runtime gotchas for the locked libs `[VERIFIED/ASSUMED mix]`
- **OkHttp 4.12.x:** minSdk 21. **API-23 cleartext note (in your favor):** historically OkHttp on Marshmallow couldn't call the API-24 `NetworkSecurityPolicy.isCleartextTrafficPermitted(host)` overload and **fell back to permitting cleartext** (OkHttp #3325) — since you *want* cleartext and set `usesCleartextTraffic`, this helps. TLS cipher concerns are moot for `ws://`/`http://`. `[VERIFIED: minSdk 21 + #3325 — per PITFALLS.md HIGH]`
- **Retrofit 2.11.x:** follows OkHttp (minSdk 21). No API-23 gotcha for plain REST. `[ASSUMED patch]`
- **Coil 3.x:** **minSdk 23 exactly** — your floor; **fixed OOM decoding large PNGs on API ≤23**, precisely the benchmark's memory-pressure + Phase 5 thumbnail path (D-07). This is *why* the benchmark must use real Coil decode. `[VERIFIED: Coil 3 minSdk 23 + OOM fix — per STACK.md HIGH]`
- **kotlinx.serialization 1.7.x:** pure-Kotlin, compile-time, fine on 23. `[ASSUMED patch]`
- **AndroidX (Activity/Lifecycle/AppCompat/RecyclerView/ConstraintLayout):** **minSdk 23 baseline since June 2025** — this is *why* the floor is safe. `[VERIFIED: AndroidX June-2025 21→23 bump — per STACK.md HIGH]`

## Common Pitfalls

### Pitfall 1: Accidentally taking the alpha Compose BOM (→ 1.12 / compileSdk 37)
**What goes wrong:** BOM 2026.05.00 exists in both stable (1.11.1) and alpha (1.12.0-alpha02) channels; grabbing the alpha pulls you onto the AGP-9 / compileSdk-37 line you're avoiding.
**How to avoid:** pin the stable BOM; assert `compose.ui:ui` resolves to 1.11.1 via `:app:dependencies`.
**Warning signs:** sync demands compileSdk 37 or AGP 9; `1.12.x` in the dependency tree.

### Pitfall 2: Trusting `FrameTimingMetric` as the sole metric on API 23
**What goes wrong:** the gate "passes" on a metric set that's reduced on Marshmallow (`frameOverrunMs` is API 31+; framestats parsing is OS-version-fragile), or a parse error makes the run inconclusive.
**How to avoid:** `gfxinfo framestats` CSV + custom parser is the **system of record** (D-04). Treat `frameDurationCpuMs` from FrameTimingMetric as corroboration only.
**Warning signs:** missing `frameOverrunMs`; "fails to parse gfxinfo"; zero frames reported.

### Pitfall 3: framestats ring-buffer truncation
**What goes wrong:** `dumpsys gfxinfo framestats` holds only ~120 recent frames; a long scroll loses early frames, skewing percentiles.
**How to avoid:** `reset` before the window; dump framestats **repeatedly** during the run (or keep the window short/well-defined), concatenate, dedupe by frame timestamp, compute percentiles; exclude warmup.
**Warning signs:** frame counts far below what you scrolled.

### Pitfall 4: The two scenes aren't actually identical (D-02 fairness break)
**What goes wrong:** Compose scene scrolls a different list length / images / feed cadence than Views → the "winner" is a harness artifact.
**How to avoid:** one feed source, one image set, one UiAutomator script, one scroll distance/timing; select backend only at launch; assert both scenes receive byte-identical feed events.
**Warning signs:** wildly different memory/frame counts before any real render difference.

### Pitfall 5: Measuring a debug build or skipping R8
**What goes wrong:** debug Compose is 5–10× slower; numbers are meaningless and would wrongly condemn Compose.
**How to avoid:** build **release + R8 + signed**, `adb install`, measure that exact artifact (D-03/D-08). Keep R8 keep-rules for kotlinx.serialization + Retrofit models so the smoke test works.
**Warning signs:** giant frame times even on the Views scene; `BuildConfig.DEBUG == true`.

### Pitfall 6: Shipping an NSC that silently blocks cleartext on API 24+ (D-11)
**What goes wrong:** `usesCleartextTraffic="true"` covers API 23, but if the NSC forbids cleartext, any API 24+ device running the same APK silently can't reach Moonraker (NSC wins on 24+, per Resolved #6).
**How to avoid:** make the NSC *permit* cleartext too; remember this phase proves the Marshmallow path only; defer API-24+ validation (D-11).
**Warning signs:** works on the Nexus 7, fails on a newer test device.

### Pitfall 7: Ambiguous cleartext smoke-test failure (D-01b)
**What goes wrong:** the test fails and you can't tell cleartext-blocked from auth-rejected from printer-idle.
**How to avoid:** pre-configure trusted-client/auth on Moonraker (D-01b); use the D-10 three-step definition (ws connect + REST GET + **subscribe-and-await-deterministic-update**, never a stray `notify_*`); log the distinguishing failure (cleartext exception vs. 401 vs. timeout).
**Warning signs:** intermittent "pass" depending on whether the printer happened to emit a notification.

## Code Examples

### Cleartext config (CONN-05 — both switches, govern different API ranges per Resolved #6)
```xml
<!-- res/xml/network_security_config.xml — honored API 24+; MUST permit cleartext for the API-24+ path (D-11) -->
<!-- [CITED: developer.android.com/privacy-and-security/security-config] -->
<network-security-config>
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
```
```xml
<!-- AndroidManifest.xml <application> -->
<!-- usesCleartextTraffic governs API 23 (where NSC is ignored); on API 24+ it is IGNORED in favor of NSC. -->
<application
    android:usesCleartextTraffic="true"
    android:networkSecurityConfig="@xml/network_security_config" >
```

### `gfxinfo framestats` capture loop (system of record)
```bash
# [CITED: developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics] (framestats backs FrameTimingMetric)
PKG=com.example.dinghy
adb shell dumpsys gfxinfo $PKG reset      # clear before the measured window
# … trigger the scripted scene (UiAutomator) …
adb shell dumpsys gfxinfo $PKG framestats # CSV: per-frame ns columns; dump repeatedly to beat the ~120-frame ring buffer
# Parser: total_frame_ms = (FrameCompleted - IntendedVsync) / 1e6 ; p50/p90/p95 ; count(>700ms) ; drop warmup
```

### app/build.gradle.kts essentials (D-01a ABI + verified floors)
```kotlin
android {
    compileSdk = 35                 // [VERIFIED in-range for AGP 8.7]
    defaultConfig {
        minSdk = 23                 // PKG-02 floor (assert MERGED minSdk separately)
        targetSdk = 35              // D-10/D-11 cleartext caveat applies on API 24+
        ndk { abiFilters += "armeabi-v7a" }   // D-01a: 32-bit device
    }
    buildTypes { release { isMinifyEnabled = true /* R8 */ } }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| AndroidX minSdk 21 | **AndroidX minSdk 23 baseline** | June 2025 | The project's floor == modern baseline; the locked stack is in-bounds. |
| "Baseline Profile fixes Compose startup" (general advice) | True **only API 24+**; **no-op on API 23 (Full-mode-only)** | platform behavior | D-03 correct: measure plain release on the target. |
| `usesCleartextTraffic` as the universal cleartext control | **NSC (API 24+) wins when present; manifest flag governs API 23** | API 24 (NSC introduced) | D-10/D-11; this phase proves Marshmallow path only. |
| Macrobenchmark FrameTimingMetric as universal SoR | On API 23, **`gfxinfo framestats` CSV is SoR**; FrameTimingMetric gives `frameDurationCpuMs` only (no `frameOverrunMs` < API 31) | — | D-04 metric strategy. |
| Compose BOM "just pin latest" | **Pin stable BOM 2026.05.00 → 1.11.1**; avoid the alpha channel (→ 1.12 / compileSdk 37) | — | D-09; new pitfall surfaced this session. |

**Deprecated/outdated for this phase:**
- Compose 1.12 / AGP 9 / compileSdk 37 — deliberately avoided (D-09).
- Any "Tegra / 1280×800" framing — wrong device generation (D-01a).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Exact **patch** versions of OkHttp/Retrofit/Coil/serialization/coroutines/AndroidX/benchmark/uiautomator | Stack | Resolved by `:app:dependencies` at scaffold time; all first-party. Low. |
| A2 | Macrobenchmark `FrameTimingMetric` emits `frameDurationCpuMs` (not just nothing) on this specific Nexus 7 | Resolved #3 | None for the gate — gfxinfo is SoR; FrameTimingMetric is bonus. |
| A3 | `apkanalyzer`/`aapt2` present in the build env for the artifact-level minSdk check | Resolved #5 / Env | Fallback: Variant-API `verifyMinSdk` Gradle task (no external tool). |
| A4 | Kotlin **2.1.21** chosen (vs 2.1.10) | Resolved #2 | 2.1.21 fixes a Gradle>8.7 test-fixtures bug; safest 2.1.x. Low. |

**Newly VERIFIED this session (moved OUT of assumptions):** Compose BOM 2026.05.00 → 1.11.1 (#1); AGP 8.7.0 ↔ Gradle 8.9 ↔ JDK 17 ↔ compileSdk 35 (#2); FrameTimingMetric partial-on-23 / minSdk 23 / frameOverrunMs API 31+ (#3); baseline-profile API 24+ no-op on 23 (#4); merged-manifest minSdk semantics (#5); cleartext precedence by API level (#6).

## Open Questions

1. **Exact on-device output of `FrameTimingMetric` on this Nexus 7.**
   - What we know: library runs on API 23; `frameDurationCpuMs` available; `frameOverrunMs` needs API 31+; framestats parsing is OS-version-fragile.
   - What's unclear: whether *this* device/OS build parses cleanly under Macrobenchmark.
   - Recommendation: one `connectedReleaseAndroidTest` run answers it; gfxinfo CSV is SoR regardless. **Not a blocker.**

2. **Exact library patch versions.**
   - What we know: the build quartet (Kotlin/AGP/Gradle/JDK/compileSdk/BOM) is VERIFIED; the choices are first-party.
   - What's unclear: latest stable patch of each AndroidX/Square/Coil lib at scaffold time.
   - Recommendation: `:app:dependencies` + `assembleRelease`; the build self-validates. **Not a blocker.**

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| **JDK 17** | AGP 8.7.0 build (REQUIRED — VERIFIED) | ✗ unverified on dev box | — | Install Temurin/Adoptium 17; AGP errors clearly if wrong/missing. No fallback — hard requirement. |
| **Gradle 8.9** (wrapper) | AGP 8.7.0 (min+default — VERIFIED) | created by scaffold | 8.9 | `gradle wrapper --gradle-version 8.9`; pin in `gradle-wrapper.properties`. |
| Android SDK (compileSdk 35, build-tools 34, platform-tools/adb) | build + install + gfxinfo | ✗ unverified | — | Install via `sdkmanager`; required, no fallback. |
| `adb` | install release APK, `dumpsys gfxinfo` | ✗ unverified | — | Ships with platform-tools; required. |
| `aapt2` / `apkanalyzer` | PKG-02 artifact-level minSdk check (#5) | ✗ unverified | — | **Fallback:** Variant-API `verifyMinSdk` Gradle task — no external tool needed. |
| Real Nexus 7 2013 (API 23) | the entire gate (D-01) | ✓ (in hand) | API 23 | none — non-negotiable. |
| Live Ender 5 Plus Moonraker + trusted client/auth | cleartext smoke test (D-01b) | ✓ (per D-01) | — | none — non-negotiable. |

**Missing dependencies with no fallback:** JDK 17 + Android SDK/adb must be present on the build box (hard requirements; not project-specific risks but must be confirmed at scaffold start).
**Missing dependencies with fallback:** `aapt2`/`apkanalyzer` (→ Variant-API Gradle task).

## Validation Architecture

> Nyquist validation is **enabled** (`workflow.nyquist_validation: true`). This phase's "tests" are
> largely **on-device instrumented measurements + a build assertion**, not classic unit tests — the
> nature of a platform gate. Each success criterion below is *measured/proven*.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | AndroidX **Macrobenchmark** (`benchmark-macro-junit4`, minSdk 23) + **UiAutomator** (instrumented `connectedAndroidTest`); plus `adb dumpsys gfxinfo framestats` (SoR) parsed by a host/JVM unit-test-style parser; plus a **`verifyMinSdk` Gradle task** for PKG-02 |
| Config file | none yet — **Wave 0** creates `:app` + `:macrobenchmark`, `gradle/libs.versions.toml`, pinned `gradle-wrapper.properties` (Gradle 8.9) |
| Quick run command | `./gradlew :app:assembleRelease` (compile + R8); `./gradlew verifyMinSdk` (PKG-02) |
| Full suite command | `./gradlew :macrobenchmark:connectedReleaseAndroidTest` + the gfxinfo capture/parse run + the cleartext smoke test run (all on the Nexus 7) |

### Phase Requirements → Test Map
| Req / Criterion | Behavior | Test Type | Automated Command / Method | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| **PKG-02** | Merged-manifest effective `minSdk == 23` (transitive-proof) | build assertion | `./gradlew verifyMinSdk` (Variant-API) **and/or** `apkanalyzer manifest min-sdk app-release.apk` == 23 | ❌ Wave 0 |
| **PKG-02** | No dependency raises merged minSdk (free guard) | build | AGP manifest-merger fails `assembleRelease` if a dep declares minSdk>23 | ❌ Wave 0 |
| **CONN-05 / D-10** | `ws://` opens to known Moonraker | instrumented/smoke | smoke-test asserts socket open within timeout | ❌ Wave 0 |
| **CONN-05 / D-10** | REST `GET` `server.info`/`printer.info` over `http://` | instrumented/smoke | smoke-test asserts 2xx + parseable JSON-RPC result | ❌ Wave 0 |
| **CONN-05 / D-10** | subscribe known object → **deterministic** update | instrumented/smoke | smoke-test subscribes + asserts the awaited update arrives (NOT a stray `notify_*`) | ❌ Wave 0 |
| **Toolkit gate / D-04** | p50 frame time < 16.6 ms (both scenes) | on-device measure | gfxinfo framestats CSV → parser → p50; FrameTimingMetric `frameDurationCpuMs` corroborating | ❌ Wave 0 |
| **Toolkit gate / D-04** | zero frames > 700 ms (frozen-frame detector) | on-device measure | parser counts frames > 700 ms == 0 | ❌ Wave 0 |
| **Toolkit gate / D-04** | p95 within ~33–50 ms tolerance | on-device measure | parser p95 ≤ ~50 ms under stress scene | ❌ Wave 0 |
| **Toolkit gate / D-04** | no OOM / no GC storm during thumbnail scroll | on-device measure | no `OutOfMemoryError` (logcat); GC pause/alloc within bound (`dumpsys meminfo`/logcat) | ❌ Wave 0 |
| **Toolkit gate / D-02/D-05** | head-to-head verdict recorded | artifact | ADR file with both toolkits' raw captures attached | ❌ Wave 0 |
| **Scaffold / D-08** | release/R8 APK installs + runs on real 32-bit API-23 device | manual+instrumented | `adb install app-release.apk` succeeds; app launches; armeabi-v7a present (`aapt2 dump badging`) | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :app:assembleRelease` (compile + R8) and `./gradlew verifyMinSdk`.
- **Per wave merge:** full `:macrobenchmark:connectedReleaseAndroidTest` + gfxinfo capture/parse + cleartext smoke test on the device.
- **Phase gate:** ADR recorded with both scenes' raw captures (D-05); cleartext three-step smoke test green (D-10); release APK installs+runs on the Nexus 7 (D-08); `verifyMinSdk` green (PKG-02).

### Pass/Fail definitions (the "system of record")
- **Toolkit (per D-04):** a toolkit is **viable** iff p50 < 16.6 ms AND zero frames > 700 ms AND no OOM/GC-storm on the stress scene; p95 ≤ ~50 ms is *tolerated*. The **verdict** is **relative** (D-02): pick the toolkit with the better/acceptable numbers on the identical workload; if both viable, record the comparison and choose; if Compose fails and Views passes, the hybrid-Views fallback (Files list / temp graph / Console scrollback) is selected. **SoR = gfxinfo framestats CSV percentiles**, FrameTimingMetric `frameDurationCpuMs` corroborating.
- **Cleartext (per D-10):** **pass** iff all three steps (ws open + REST GET + subscribe-and-await-deterministic-update) succeed on the release build on the Nexus 7 against the trusted Moonraker; a stray `notify_*` does NOT count.
- **Scaffold/PKG-02 (per D-08/D-09):** release/R8 APK installs and runs on the real device AND `verifyMinSdk` reports merged minSdk == 23.

### Wave 0 Gaps
- [ ] `gradle/libs.versions.toml` + pinned `gradle-wrapper.properties` (Gradle 8.9) + root/`:app`/`:macrobenchmark` `build.gradle.kts` — establishes PKG-02 and the build floor (Kotlin 2.1.21 / AGP 8.7.0 / JDK 17 / compileSdk 35 / Compose BOM 2026.05.00)
- [ ] `:app` worst-case scenes (Compose + Views) + in-process synthetic feed (D-06/D-07)
- [ ] `:macrobenchmark` module: `MacrobenchmarkRule` + UiAutomator script + `FrameTimingMetric` + gfxinfo capture helper
- [ ] gfxinfo framestats CSV **parser** (host/JVM; unit-testable on captured sample CSV) — percentiles + >700 ms counter + warmup exclusion
- [ ] `verifyMinSdk` Gradle task (merged-manifest Variant API) — PKG-02 deliverable
- [ ] cleartext smoke-test entry point (release-buildable) implementing the D-10 three steps
- [ ] `network_security_config.xml` + manifest `usesCleartextTraffic` (CONN-05)
- [ ] ADR template for the toolkit verdict (D-05)

## Security Domain

> `security_enforcement: true`, ASVS level 1. A build/measurement gate with a **deliberate cleartext**
> requirement on a LAN-only appliance — narrow but real surface.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | partial | Moonraker trusted-client/auth is *pre-configured on the server* for the smoke test (D-01b); the app sends no credentials this phase. Real auth is Phase 2 (CONN-02). |
| V3 Session Management | no | No sessions this phase. |
| V4 Access Control | no | No app-side access control this phase. |
| V5 Input Validation | minimal | Smoke test parses Moonraker JSON-RPC with kotlinx.serialization (typed, no eval). |
| V6 Cryptography | n/a-by-design | **Cleartext is an intentional, documented requirement** (CONN-05/D-11): LAN-only, old hardware, Moonraker speaks `ws://`/`http://`. Not hand-rolled crypto — the *absence* of TLS is a scoped, acknowledged decision, validated for Marshmallow only (D-11). |
| V9 Communications | yes (with caveat) | Cleartext permitted **deliberately and scoped**: `usesCleartextTraffic` (API 23) + NSC (API 24+); D-11 flags that the API-24+ cleartext path must be re-validated, preventing a silent "secure-looking but broken" ship. |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Cleartext on a hostile network (MITM/eavesdrop) | Information Disclosure / Tampering | **Accepted & scoped:** LAN-only appliance, documented in CONN-05/D-11; not a public-internet client. |
| Over-broad NSC permitting cleartext everywhere | Information Disclosure | Scope the NSC to the Moonraker host(s) where practical when the API-24+ path is hardened (Phase 2+); this phase proves the path, later phases tighten it. |
| R8 stripping smoke-test/serialization classes → false failure (correctness, not security) | — | Keep-rules for kotlinx.serialization + Retrofit models in the release config. |
| Debug-signed / over-permissioned scaffold leaking into release | Elevation/Tampering | Release variant R8-minified; request only `INTERNET`; real keystore signing arrives in PKG-01 (Phase 8). |

**Security block level:** `high` — nothing in this phase rises to a high-severity finding; the cleartext use is an explicit, documented project decision (CONN-05), not an accidental weakness.

## Sources

### Primary (HIGH confidence — verified this session)
- `[CITED: developer.android.com/develop/ui/compose/bom/bom-mapping]` — Compose BOM → library mapping (2026.05.00 → 1.11.1; 2026.04.00 → 1.11.0; 2026.05.01 → 1.11.2)
- `[CITED: developer.android.com/build/releases/agp-8-7-0-release-notes]` + `/build/releases/about-agp` + `/build/jdks` — AGP 8.7 ↔ Gradle 8.9 (min+default), JDK 17, build-tools 34, compileSdk ≤ 35
- `[CITED: kotlinlang.org/docs/gradle-configure-project]` — Kotlin 2.1.0–2.1.10 ↔ Gradle 8.7–8.10; 2.1.21 fixes a test-fixtures bug
- `[CITED: developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics]` + `/jetpack/androidx/releases/benchmark` — FrameTimingMetric: `frameDurationCpuMs` broad, `frameOverrunMs` API 31+; Macrobenchmark minSdk 23
- `[CITED: developer.android.com/topic/performance/baselineprofiles/overview]` — Baseline Profiles = Android 7 (API 24)/8 (API 26); API 23 is Full-mode-only (no-op)
- `[CITED: developer.android.com/privacy-and-security/security-config]` + OWASP MASTG-TEST-0235 + AppSec Labs — cleartext default-permit ≤ API 23; NSC honored API 24+; `usesCleartextTraffic` ignored when NSC present
- `[CITED: developer.android.com/build/manage-manifests]` — merged-manifest minSdk semantics; `tools:overrideLibrary`; build.gradle minSdk overrides manifest
- Project prior research (HIGH): `.planning/research/STACK.md`, `SUMMARY.md`, `PITFALLS.md` — AndroidX minSdk-23 baseline; Coil 3 minSdk 23 + OOM fix; OkHttp #3325 cleartext fallback

### Secondary (MEDIUM — corroboration)
- WebSearch corroboration (jetc.dev, mvnrepository, Medium AGP-compat, getstream baseline-profiles) — cross-checked against the primary docs above
- issuetracker.google.com/issues/193260119 — "Macrobench fails to parse gfxinfo" (framestats parsing fragility on older OS)

### Tertiary (LOW — empirical, device-answered)
- Macrobenchmark `FrameTimingMetric` *exact* output on this Nexus 7 / OS build — answered by one on-device run

## Metadata

**Confidence breakdown:**
- Build quartet (BOM→Compose #1, Kotlin/AGP/Gradle/JDK #2): **HIGH** — verified against official AGP/Compose/Kotlin docs this session.
- Platform behaviors (baseline-profile floor #4, cleartext-by-API #6, merged-minSdk #5): **HIGH** — official docs + OWASP + prior project HIGH research.
- FrameTimingMetric on API 23 (#3): **HIGH on the facts** (minSdk 23, frameOverrunMs API 31+, framestats-backed); the **exact device output** is the single empirical item (gfxinfo is SoR regardless).
- Benchmark design + validation architecture: **HIGH** — derived from locked D-02/D-04/D-06/D-07.

**Research date:** 2026-05-30
**Valid until:** build-quartet pins ~14 days (re-confirm patch versions at scaffold time); platform-behavior findings ~90 days (stable).
