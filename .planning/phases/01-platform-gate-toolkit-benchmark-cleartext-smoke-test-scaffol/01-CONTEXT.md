# Phase 1: Platform Gate — Toolkit Benchmark, Cleartext Smoke Test & Scaffold - Context

**Gathered:** 2026-05-30
**Status:** Ready for planning
**Reviewed:** 2026-05-30 by Codex (gpt-5-codex) + Claude — findings reconciled into the decisions below.

<domain>
## Phase Boundary

Settle the two most consequential go/no-go questions and lay the build foundation **before any
architecture is committed**. This phase delivers exactly three things and nothing more:

1. A recorded **Compose-everywhere vs. hybrid-Views** toolkit decision, produced by an on-device
   release-mode **head-to-head** benchmark on a real Nexus 7 2013 against a SYNTHETIC 2–4 Hz source.
2. A passing **cleartext `ws://`/`http://` smoke test** on the real API-23 device, proving the
   Marshmallow cleartext path reaches a real Moonraker on the LAN.
3. A pinned `gradle/libs.versions.toml` version catalog and a **multi-module build scaffold** that
   compiles, installs, and runs on the Nexus 7.

**Explicitly NOT in this phase:** no resilient websocket/state machine, no connection layer, no
`PrinterState`, no capability gating, no panels, no auth UI. Those start in Phase 2. This is a gate —
prove toolkit + build + cleartext, then stop.

</domain>

<decisions>
## Implementation Decisions

> **Note:** D-02 through D-11 were materially revised after the Codex/Claude review. The original
> draft conditioned the gate on an installed Baseline Profile and a p95 ≤ 16.6 ms bar — both were
> wrong for an API-23 target and have been corrected. See `01-DISCUSSION-LOG.md` for the raw choices.

### Target Hardware (the load-bearing reality)
- **D-01:** The gate runs against **real target hardware** — a Nexus 7 2013 (Android 6.0.1 / API 23)
  in hand, plus a **live Ender 5 Plus Moonraker** reachable on the LAN. No emulator, no fake server.
- **D-01a — HARDWARE CORRECTION (review):** The 2013 Nexus 7 is **Qualcomm Snapdragon S4 Pro
  (APQ8064) / Adreno 320, 32-bit ARMv7, 2 GB RAM, 1920×1200 (7″, 323 ppi)** — NOT the 2012 model's
  NVIDIA Tegra 3 / 1280×800. The upstream docs (`CLAUDE.md`, `.planning/PROJECT.md`) say
  "Tegra-era / 1280×800" and are describing the **wrong generation**; they should be corrected
  separately. Three consequences for this phase:
  - The perf gate is **harder** than the docs assume — ~4× the pixels of 1280×800 pushed by a
    2012-class GPU. The benchmark must render at the **real 1920×1200**.
  - The release APK must ship the **`armeabi-v7a`** ABI (the device is 32-bit; no arm64-only artifacts).
  - Any "Tegra K1 / Tegra-specific" framing in planning is irrelevant and should be struck.
- **D-01b:** The cleartext smoke test needs a **known, reachable Moonraker endpoint with auth /
  trusted-client pre-configured** (or a known trusted LAN), or a failure is ambiguous (auth rejection
  vs. cleartext failure).

### Toolkit Go/No-Go Bar (reworked after review)
- **D-02 — HEAD-TO-HEAD, not Compose-vs-perfection:** Implement the **same worst-case scene in BOTH
  Compose and hybrid-Views**, drive both with the **identical** scripted interaction + synthetic feed,
  and compare on the same workload. The decision is *relative* — "which toolkit is acceptable / less
  bad on this hardware" — because if the real bottleneck is 1920×1200 fill rate on an Adreno 320, it
  hits Views too, and an absolute 60 fps bar would wrongly condemn Compose.
- **D-03 — NO Baseline Profile precondition on the API-23 target.** Android 6 / API 23 ART does
  **full AOT compilation at install** (`dex2oat`); profile-guided compilation and Baseline Profiles
  are an **API 24+ feature and a no-op on Marshmallow** (UNVERIFIED on exact floor — planner confirms,
  but do NOT gate Phase 1 on "baseline profile installed"). Measure the **release / R8 build as
  installed** on the device. Profiling on a *modern* phone would give a falsely rosy number.
- **D-04 — Recorded metrics + thresholds:**
  - **Absolute floors (a toolkit must clear these to count as "viable"):** p50 frame time
    **< 16.6 ms** on the stress scene; **zero frames > 700 ms** (frozen-frame stall detector);
    **no OOM and no GC storm** during thumbnail scroll.
  - **Stress-scene tolerance:** **p95 in the ~33–50 ms range is acceptable** under the deliberately
    worst-case scene — do NOT require p95 ≤ 16.6 ms (unrealistic, and it made the now-dropped
    redundant `jank < 10%` rule the de-facto gate). p90 ≈ one missed-vsync budget is the target.
  - **System of record:** prefer Macrobenchmark **`FrameTimingMetric`** for repeatable release-mode
    numbers — **UNVERIFIED whether it functions on API 23** (frame metrics may require a newer API
    and/or degrade to the `gfxinfo` source); planner confirms. On the API-23 device, capture **raw
    `gfxinfo framestats` CSV + a parser** (not the summary line), with explicit reset /
    warmup-exclusion / run-duration discipline — framestats is operationally fragile on 23.
- **D-05:** Record **both** toolkits' numbers AND the head-to-head verdict as an **ADR** (with the raw
  captures attached). That ADR is what gates all Phase 2+ panel architecture.

### Benchmark Fidelity (sharpened after review)
- **D-06 — deterministic in-process fixture:** the synthetic 2–4 Hz feed must be an **in-process**
  fixture emitting realistic immutable state updates, list mutations, graph samples, and console
  appends at the planned throttle. NOT shell/adb-driven (measures IPC/scheduler noise) and NOT a toy
  in-process counter (under-tests recomposition / state-shaping). No connection layer.
- **D-07 — exercise the REAL hard paths at 1920×1200:** a scrolling Files-style list doing **actual
  Coil PNG decode + downsample with real memory-cache pressure** (NOT placeholder drawables or
  predecoded bitmaps), a live **Canvas** temperature graph, and a bounded console text spew. Anything
  less and the "worst-case" claim — and therefore the go/no-go — isn't credible.

### Scaffold Shape (rationale corrected)
- **D-08 — multi-module `:app` + `:macrobenchmark`,** justified by **measurement** (running the
  benchmark, `FrameTimingMetric` where it works, generating profiles useful on any API 24+ devices),
  NOT by "baseline profiles are mandatory on the target" (they are not — see D-03). The gate must also
  **pin the Gradle wrapper version + JDK + AGP** (not just library versions) and produce an
  **installable release/R8 variant that actually installs and runs on the real 32-bit API-23 device**.
- **D-09 — pinned `gradle/libs.versions.toml`** governs every dependency; minSdk 23 floor.
  - **PKG-02 gap (review):** pinning versions does NOT stop a *transitive* dep from raising the
    **merged-manifest** minSdk above 23. Add a **CI/lint gate that asserts the merged-manifest
    `minSdk` stays 23** — that is what actually delivers PKG-02, not the pins alone.
  - Pin to the **Compose 1.11 / AGP 8.7.x / compileSdk 35 / Kotlin 2.1.x** line; apply the
    **Kotlin Compose compiler plugin** separately (`org.jetbrains.kotlin.plugin.compose` — the BOM
    pins Compose *libraries*, not the compiler).
  - **UNVERIFIED (confirm at pin time before locking):** that **Compose BOM 2026.05.00** maps to
    Compose 1.11 and does NOT demand compileSdk > 35 via AndroidX `minCompileSdk` metadata; and the
    exact Kotlin 2.1.x ↔ AGP 8.7.x ↔ Gradle compatibility matrix.

### Cleartext Smoke Test (sharpened after review)
- **D-10 — "passes" =** the minimal release build, on the Nexus 7 (API 23, shipping targetSdk 35):
  (a) opens a `ws://` to the known Moonraker, (b) does a REST `GET` (e.g. `printer.info`/`server.info`),
  and (c) **subscribes to a known object and awaits a deterministic update** — do NOT rely on a stray
  `notify_*` frame (an idle printer may never emit one → false failure). Set **both**
  `res/xml/network_security_config.xml` (cleartext permitted) **and**
  `android:usesCleartextTraffic="true"` — NSC is honored only API 24+, the manifest flag covers
  Marshmallow.
- **D-11 — honest scope:** on API 23 cleartext is near-default (NSC ignored pre-24, M default-permits),
  so a green light here proves the **Marshmallow path only**. The same APK's cleartext behavior on
  API 24+/targetSdk 35 devices must be validated separately (later phase) or you ship a tablet that
  works and an NSC that silently blocks cleartext everywhere else.

### Claude's Discretion
- Module/package layout, Gradle plugin wiring, the in-process fixture's exact shape, and the
  `gfxinfo` CSV parser implementation are left to the planner/executor.
- The planner owns confirming all **UNVERIFIED** facts above (Compose BOM ↔ compileSdk/AGP mapping,
  Macrobenchmark frame-metric support on API 23, the baseline-profile API floor) before locking.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope & requirements
- `.planning/ROADMAP.md` § "Phase 1: Platform Gate" — the three success criteria this gate must close.
- `.planning/REQUIREMENTS.md` — **PKG-02** (pinned version catalog protecting minSdk 23) and
  **CONN-05** (cleartext `ws://`/`http://` on API 23) are the two requirements mapped to this phase.
- `.planning/PROJECT.md` § Constraints & Key Decisions — minSdk 23 floor, sideloaded-APK distribution.
  ⚠ **Device specs in this file are the wrong generation** (says Tegra / 1280×800 = the 2012 model);
  see D-01a. Correct separately.

### Locked technology stack (verify the UNVERIFIED pins in D-09 at pin time; do not re-decide the choices)
- `CLAUDE.md` (repo root) § "Technology Stack" — the pinned stack: Kotlin 2.1.x, Compose BOM 2026.05
  (Compose 1.11), AGP 8.7.x, compileSdk 35, OkHttp 4.12, Retrofit 2.11, kotlinx.serialization 1.7,
  Coil 3 (minSdk 23), DataStore, R8, and the Macrobenchmark module. Also the "Compose vs Views"
  guidance and the named **hybrid-Views fallback set** (Files list, temperature graph, Console
  scrollback) this head-to-head benchmark exists to settle empirically.
  ⚠ Same device-spec error as PROJECT.md ("Tegra-era / 1280×800") — see D-01a.

### Reference only (NOT to port; informs the worst-case panel sim)
- `E:\claude\personal\github\gtk4_klipperscreen\docs\Screen_Catalog.md` — inventory of the eventual
  panels. Relevant here only as the source of what the real Files/Temp/Console surfaces look like, so
  the worst-case sim (D-07) resembles them. Reference, not a dependency.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **None — greenfield.** The repo currently contains only `CLAUDE.md` and `.planning/`. No Kotlin
  source, no Gradle scaffold, no modules yet. This phase creates the first code.

### Established Patterns
- No code patterns exist yet. The pattern this phase *establishes* is the pinned version catalog +
  multi-module (`:app` + `:macrobenchmark`) structure every later phase inherits.

### Integration Points
- N/A — nothing to integrate with. The git repo was initialized with Windows git (filemode false)
  per the workspace `CLAUDE.md`; Linux git operates on it via the `safe.directory` exception.

</code_context>

<specifics>
## Specific Ideas

- **The benchmark is a fair fight, not a Compose audition.** Build the worst-case scene **twice**
  (Compose + hybrid Views), run the same scripted interaction at 1920×1200, and pick the winner on
  identical load. The hybrid-Views fallback set, if Compose loses, is explicitly the three high-churn
  surfaces named in STATE.md: **Files list, temperature graph, Console scrollback** — which is exactly
  what the scene exercises (D-07), so the result maps directly onto the fallback decision.
- **Trust only the real device in release mode.** Emulators, debug builds, and modern-phone numbers
  are all distrusted for this hardware class — and Baseline Profiles are a no-op on the API-23 target
  (D-03), so don't let their presence/absence color the numbers.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope. (The connection layer, `PrinterState`, panels, and auth
that naturally came up are already scoped to Phases 2+ by the roadmap and were not pulled forward.)

**Cross-doc cleanup flagged (not this phase):** `CLAUDE.md` and `.planning/PROJECT.md` describe the
**2012** Nexus 7 (Tegra 3 / 1280×800) while the project targets the **2013** model (Snapdragon S4 Pro /
Adreno 320 / 1920×1200 / 32-bit). Worth correcting at the project level so future phases don't inherit
the wrong perf assumptions — see D-01a.

</deferred>

---

*Phase: 1-platform-gate-toolkit-benchmark-cleartext-smoke-test-scaffol*
*Context gathered: 2026-05-30 · Reviewed & revised 2026-05-30 (Codex + Claude)*
