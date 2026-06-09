---
phase: 22-performance-architecture-refactor
verified: 2026-06-09T00:00:00Z
status: human_needed
score: 4/5
overrides_applied: 0
human_verification:
  - test: "Confirm recomposition skip behaviour is measurable on hot screens"
    expected: "With PrinterState @Immutable and the god-component split, Compose should skip unaffected scopes (sail logo, nav grid, shell) on the 4 Hz state emission. Layout Inspector recomposition count snapshot or Compose compiler report showing counts dropped on the split scopes vs baseline."
    why_human: "ROADMAP SC2 explicitly says 'verified via Layout-Inspector recomposition counts'. The phase used grep-verified annotations + structural code review as a proxy. The annotations are correct in the code, but the recomposition-count evidence the SC wording demands was never produced."
---

# Phase 22: Performance & Architecture Refactor — Verification Report

**Phase Goal:** With the feature set essentially complete (Phases 1–21), refactor for efficiency BEFORE ship — remove the structural wide-recomposition root causes on the Nexus 7 2013 (Adreno 320) perf floor without regressing any screen.
**Verified:** 2026-06-09
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | SC1: On flox in RELEASE mode, target screens show no frozen frames before or after; per-frame tail latency reclaimed on idle PrintStatus | VERIFIED | `22-GFXINFO-BASELINE.md` (pre-edit) + `22-GFXINFO-AFTER.md` (post): idle PrintStatus p90 −13% (56.8→49.6 ms), 0 frozen frames throughout all 8 sweep screens before and after. Mid-print AFTER capture deliberately skipped by owner with recorded rationale; baseline mid-print (54.2 ms, 0 frozen) already inside the ADR-0001 gate. Owner-accepted 2026-06-08. |
| 2 | SC2: `AppContainer` is `@Stable`; `PrinterState` tree is `@Immutable` with `ImmutableMap`/`ImmutableList` fields; no raw `Map<>`/`List<>` declarations on hot-path state fields | VERIFIED | Code confirmed: `@Stable` on `AppContainer` class (line 76); 11 `@Immutable` annotations on `PrinterState.kt` data types; zero raw `Map<`/`List<` field declarations survive in `PrinterState.kt`; `PrinterStateReducer.kt` uses `.toImmutableMap()`/`.toImmutableList()` at every `.copy(...)` assignment boundary; 14 targeted immutable-conversion tests green. HOWEVER: ROADMAP SC2 wording is "verified via Layout-Inspector recomposition counts" — the structural code change is correct but the numeric recomposition-drop evidence was not produced. See Human Verification. |
| 3 | SC3: GraphView overdraw gate met — zero frozen frames on the Temperature graph, D-12 guard alone sufficient, fill change owner-skipped | VERIFIED | `22-GFXINFO-GRAPH-POST05.md`: post-D-12 Temperature graph p50 −12%, p90 −7%, p95 −13%, 0 frozen frames. Owner reviewed the exact visual tradeoff and chose to keep the layered-blend aesthetic; SC3 gate was already met without the fill change. `GraphView.kt` fill path confirmed unchanged — `areaPath`/`fillPaint` retained, single-primary-trace fill still drawn. Owner decision recorded in 22-06 SUMMARY. |
| 4 | SC4: AndroidView interop surfaces (GraphView, WebcamView) have D-12 equality guards; three Views surfaces confirmed leak-free; no GC-churn | VERIFIED | `GraphView.applyTokens` has `private var lastTokens: ThemeTokens?`; returns early at line 208 when `t == lastTokens`. `WebcamView.applyTokens`, `setTransform`, `setChrome` all equality-guarded. On-device SC4 measurement: 24 enter/exit cycles on flox — PSS settled 60.3 MB (below 82.6 MB baseline), no monotonic climb. Dark↔light theme toggle confirmed equality guard allows genuine token change to invalidate and recolor. Owner-approved 2026-06-08. |
| 5 | SC5: No functional regressions — host unit suite green; nav-regression smoke test on flox passes; calibration screens feed live data through new holder pattern | VERIFIED | `assembleRelease` + `testReleaseUnitTest` green after every task wave. Nav smoke test on flox: Bed Mesh (live "No active mesh"), Probe Calibrate (live z-offset −4.816), Z-Tilt/QGL correctly capability-gated on E3, drawer `activeName` + spool tile ImmutableList swatches render, general nav clean. Owner-approved 2026-06-08. |

**Score:** 4/5 truths VERIFIED (SC2 structural implementation verified; ROADMAP-specified verification *method* not fulfilled)

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `state/PrinterState.kt` | `@Immutable` on all nested types; `ImmutableMap`/`ImmutableList` fields | VERIFIED | 11 `@Immutable` annotations; no raw `Map<`/`List<` fields remain; imports `kotlinx.collections.immutable.*` |
| `state/PrinterStateReducer.kt` | `.toImmutableMap()`/`.toImmutableList()` at every assignment boundary | VERIFIED | All 10+ assignment sites confirmed; private `toImmutable2d()` extension for 2D matrices |
| `di/AppContainer.kt` | `@Stable`; `stateScope`; `activeProfileId` + `activeName` as `StateFlow`; `writeScope` for persistence | VERIFIED | `@Stable` class annotation; `stateScope: CoroutineScope` at line 146; both flows `stateIn(stateScope, WhileSubscribed(5000), null)` |
| `ui/printstatus/PrintStatusScreen.kt` | Reduced from 1585 lines; slot lambdas call named composables (restartable scopes) | VERIFIED | 624 lines; `focus = { PrintStatusFocus(...) }`, `field = { ... }`, `gutter = { PrintStatusGutter(...) }` in all 4 mode branches |
| `ui/printstatus/PrintStatusFocus.kt` | Exists and substantive | VERIFIED | 301 lines |
| `ui/printstatus/PrintStatusField.kt` | Exists and substantive | VERIFIED | 747 lines; `fmtDuration` memoised via `remember(roundToInt())` |
| `ui/printstatus/PrintStatusGutter.kt` | Exists and substantive | VERIFIED | 188 lines |
| `render/GraphView.kt` | `lastTokens` equality guard in `applyTokens`; fill path UNCHANGED | VERIFIED | Guard at lines 187/208; single-primary `areaPath`/`fillPaint` fill preserved |
| `render/WebcamView.kt` | `lastTokens` guard in `applyTokens`; `setTransform` + `setChrome` equality-guarded | VERIFIED | Guards at lines 149/179 (`applyTokens`), line 244 (`setTransform`), line 263 (`setChrome`) |
| `ui/calibration/BedMeshScreen.kt` | Takes `holder: BedMeshHolder` + `container: AppContainer` | VERIFIED | Function signature confirmed at line 96–97 |
| `ui/calibration/ProbeCalibrateScreen.kt` | Takes `holder: ProbeCalibrateHolder` + `container: AppContainer` | VERIFIED | Function signature confirmed at lines 101–102 |
| `ui/calibration/TiltScreen.kt` | Takes `holder: TiltHolder` + `container: AppContainer` | VERIFIED | Function signature confirmed at lines 85–86 |
| `gradle/libs.versions.toml` | `kotlinxCollectionsImmutable = "0.3.8"` | VERIFIED | Pin confirmed; `kotlinx-collections-immutable` alias present |
| `app/build.gradle.kts` | `implementation(libs.kotlinx.collections.immutable)` | VERIFIED | Line present with D-02 comment |
| `designsystem/icons/SpoolGlyph.kt` | `gradientBrush` via `remember(render)` outside Canvas | VERIFIED | `val gradientBrush = remember(render) { ... }` at line 90, above the Canvas block |
| `22-GFXINFO-BASELINE.md` | Pre-edit baseline with reproducibility header and 8 sweep screens | VERIFIED | Commit SHA `da363ee`, full header, all 8 screens tabulated |
| `22-GFXINFO-AFTER.md` | Post-phase measurement vs baseline; SC1/SC2/SC5 owner-accepted | VERIFIED | p90 −13% idle PrintStatus; 0 frozen frames; owner-accepted |
| `22-COLLECTION-AUDIT.md` | Every `AppShell` `collectAsStateWithLifecycle` site classified | VERIFIED | 27 sites audited; 4 MOVES (calibration VMs), 2 HOISTED (activeName/activeProfileId), 1 SPLIT (errorLines correctly session-scoped) |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `PrinterStateReducer.kt` | `PrinterState.kt` immutable fields | `.toImmutableList()`/`.toImmutableMap()` at `s.copy(...)` boundaries | WIRED | 10+ conversion sites verified in reducer |
| `AppContainer.activeName` | `AppShell` drawer subtitle | `container.activeName.collectAsStateWithLifecycle()` | WIRED | Line 230 in AppShell; flows from `stateIn(stateScope, ...)` singleton |
| `AppContainer.activeProfileId` | webcam holder key | `container.activeProfileId.collectAsStateWithLifecycle()` | WIRED | Line 228 in AppShell |
| `AppShell` | `BedMeshScreen` | passes `holder = bedMeshHolder, container = container` | WIRED | Confirmed by screen signature migration |
| `AppShell` | `TiltScreen` | passes `holder = zTiltHolder/qglHolder, container = container` | WIRED | Confirmed by screen signature migration |
| `AppShell` | `ProbeCalibrateScreen` | passes `holder = probeCalibrateHolder, container = container` | WIRED | Confirmed by screen signature migration |
| `GraphViewHost` update block | `GraphView.applyTokens` | equality guard short-circuits before `invalidate()` | WIRED | `lastTokens` guard at GraphView lines 187/208 |
| `WebcamViewHost` update block | `WebcamView.applyTokens` | equality guard short-circuits before repaint | WIRED | `lastTokens` guard at WebcamView lines 149/179 |
| `errorLines` (consoleHolder) | `AppShell` shell-side collection | explicitly KEPT session-scoped (not hoisted to stateScope) | WIRED | Correctly stays `consoleHolder.state` collect in AppShell; `AppContainer` has no `errorLines` |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|-------------------|--------|
| `PrintStatusField.kt` | `state: PrinterState` | `printerStateFlow` in AppShell → 4 Hz `notify_status_update` | Yes — live Klipper data | FLOWING |
| `BedMeshScreen.kt` | `vm` (BedMeshVm) | `holder.vm.collectAsStateWithLifecycle()` | Yes — live z-offset −4.816 verified on flox | FLOWING |
| `ProbeCalibrateScreen.kt` | `vm` (ProbeCalibrateVm) | `holder.vm.collectAsStateWithLifecycle()` | Yes — live z-offset −4.816 verified on flox | FLOWING |
| `AppDrawer` spool tile | `drawerSpoolSwatches: ImmutableList<Color>` | `remember(activeSpoolDetail) { ... .toImmutableList() }` in AppShell | Yes — colored spiral glyph rendered on flox | FLOWING |
| `AppContainer.activeName` | `activeName: StateFlow<String?>` | `activeProfile.map { it?.displayName() }.stateIn(stateScope, ...)` | Yes — "192.168.1.121" shown in drawer on flox | FLOWING |

---

### Behavioral Spot-Checks

Step 7b: SKIPPED — this is a perf/architecture refactor phase; the verification gate is on-device gfxinfo + grep code analysis, not runnable CLI/API spot-checks. All behavioral verification was done by the owner on flox hardware (see SC1/SC4/SC5 in 22-GFXINFO-AFTER.md).

---

### Probe Execution

Step 7c: No probes declared or expected for this phase (perf refactor; measurement via `parse_framestats.py` is a measurement tool driven by the owner, not a CI probe). SKIPPED.

---

### Requirements Coverage

No formal requirement IDs — Phase 22 is a cross-cutting refactor measured against the ADR-0001 floor perf budget per ROADMAP.md: "Requirements: — (cross-cutting refactor; measured against the ADR-0001 floor perf budget, no new requirement IDs)". Coverage is against the 5 ROADMAP success criteria (verified above).

---

### Anti-Patterns Found

No TBD, FIXME, or XXX markers found in any file modified by Phase 22. No TODO markers found in the modified source files. No stub implementations detected — all split composables call substantive named sub-composables; all conversion sites call real `toImmutableList()`/`toImmutableMap()`; equality guards have real prior-value caching.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| *(none)* | — | — | — | — |

---

### Human Verification Required

#### 1. SC2 Recomposition-Count Evidence

**Test:** Open Android Studio's Layout Inspector with the app running a release build on flox. Navigate to PrintStatus while the E3 is printing (or streaming at 4 Hz idle). Observe per-composable recomposition counts. Compare the counts on the split scopes (`PrintStatusFocus`, `PrintStatusField`, `PrintStatusGutter`) versus the outer shell composables (AppShell, AppDrawer). The AppShell and scope-uninvolved surfaces should skip (count near 0) on each 4 Hz `PrinterState` emission; only the composables that actually consume changing state values should recompose.

**Expected:** With `PrinterState @Immutable` + `AppContainer @Stable` + the god-component split, Compose should correctly skip stable scopes. Recomposition counts for the split child composables that consume temps/progress (e.g. `PrintStatusField`) should be non-zero; counts for sibling scopes (e.g. the shell nav bar, AppDrawer) should be near zero while PrintStatus is visible.

**Why human:** ROADMAP SC2 explicitly specifies "verified via Layout-Inspector recomposition counts". The phase used annotation-presence grep + structural code review as the verification method. The annotations are correctly applied in the codebase, but numeric recomposition-drop evidence — the verification method the roadmap contract names — was never produced. A Layout Inspector session (even a qualitative "yes the splits skip") would fulfill the ROADMAP wording. This is the only gap between what was built and what the SC promised to prove.

---

### Gaps Summary

No blocking gaps. The phase delivered all five success criteria structurally:

- SC1 (gfxinfo before/after): VERIFIED with documented measurements, owner-accepted including the owner-directed skip of the mid-print AFTER capture.
- SC2 (@Stable/@Immutable propagation): The code is correct and fully verified by code inspection. The roadmap's prescribed verification *method* (Layout Inspector recomposition counts) was not executed — the phase used grep + structural analysis instead. This is a verification-method gap, not an implementation gap.
- SC3 (overdraw reduction): VERIFIED — D-12 guard met the gate; fill change owner-skipped with documented rationale.
- SC4 (AndroidView leak-free): VERIFIED with on-device PSS measurement and dark/light recolor regression test.
- SC5 (no regressions): VERIFIED — full host unit suite green, nav smoke test on flox owner-approved.

The single human-verification item (SC2 recomposition counts) is a qualitative confirmation of already-shipped structural work, not a gap requiring code changes.

---

_Verified: 2026-06-09_
_Verifier: Claude (gsd-verifier)_
