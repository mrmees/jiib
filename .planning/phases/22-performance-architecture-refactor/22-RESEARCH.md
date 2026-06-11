# Phase 22: Performance & Architecture Refactor — Research

**Researched:** 2026-06-08
**Domain:** Jetpack Compose recomposition discipline / AndroidView interop hygiene / Canvas fill-rate / gfxinfo measurement
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 FULL restructure:** split `PrintStatusScreen.kt` (1584 lines) at natural seams into `PrintStatusFocus` / `PrintStatusField` / `PrintStatusGutter` / `PrintStatusPreviews` (independently-restartable Compose scopes) AND push the 28 `AppShell` `collectAsStateWithLifecycle` collections down into the screens that actually consume each value (shell collects only connection status, active screen id, drawer open state).
- **D-02 P0 mandatory:** annotate `PrinterState` and every nested state value type (`HeaterState`, `OutputLiveValue`, `Screw`, `FanState`, etc.) `@Immutable`; replace `Map<>`/`List<>` fields with `ImmutableMap`/`ImmutableList` (`kotlinx-collections-immutable`); fix the inline lambda slots on `PrintStatusScreen` (lines ~490/522) passed to `ScreenScaffold`.
- **D-03:** P0–P2 from CONCERNS.md are core scope; P3 nits are opportunistic only (fix when the file is already open for a higher-priority fix).
- **D-04:** 6-DataStore consolidation is OUT (cold-start concern, not nav-lag; CONCERNS marks it "no immediate action required").
- **D-05:** Baseline-first, manual `gfxinfo framestats` in RELEASE mode on flox. No new tooling.
- **D-06:** Baseline sweep set: home/PrintStatusScreen steady-state at 4 Hz plane · App Drawer open · navigation into Files / Console / temp-graph / webcam · a representative control screen.
- **D-07:** Macrobenchmark module stays PARKED. Baseline Profile is a no-op on API 23 (full AOT at install).
- **D-08:** SC3's "stacked outline+glow layers" premise is STALE — no glow is drawn. `OutlinedControl` draws only a 2px border on a transparent fill; `ProgressRing` draws track + progress arcs only; `*Glow` tokens are defined but consumed by zero draw code. Do NOT hunt for control-glow overdraw.
- **D-09:** Retarget SC3 to REAL overdraw sources: `GraphView`'s per-trace gradient fills (CONCERNS P1) and any stacked alpha/scrim/dim layers (e.g. Paused-focus alpha-dim).
- **D-10:** Visual bar = reads-the-same on flox. Flattening provably invisible ships freely; anything that could alter a blend/falloff requires owner side-by-side approval on-device.
- **D-11:** KEEP the dead `*Glow` tokens. Do NOT prune them this phase (Phase 23/24 redesigns may revive glow; deletion has large blast radius across theme files).
- **D-12:** Guard `AndroidView` `update` blocks: `GraphViewHost`/`WebcamViewHost` call `view.applyTokens(tokens)` unconditionally on every recomposition → full `onDraw`. Cache last-applied tokens in the View, short-circuit on equality.

### Claude's Discretion

- Exact split seams/file names for `PrintStatusScreen` and which flows move where in the `AppShell` push-down (preserve behavior exactly; visual unchanged; run the full `@Preview` matrix + on-device flox check for any `ScreenScaffold`/layout-touching change).
- Whether `GraphView` overdraw relief is opacity reduction on secondary traces vs pre-rasterizing static portions (setpoint/axis) to an off-screen bitmap — researcher/planner pick per measurement.
- The `kotlinx-collections-immutable` version pin and converter wiring.

### Deferred Ideas (OUT OF SCOPE)

- H.264-while-rotating blank fix (→ Phase 24)
- FGS notification small-icon (Bluetooth glyph → 24dp jiib status icon) (→ Phase 25)
- Spool transient-failure robustness hardening (→ Phase 25)
- Pause/Resume + Tune gutter button wiring (→ Phase 25)
- Macrobenchmark module wiring (stays parked per D-07)
- 6-DataStore consolidation (backlog, per D-04)
- Pruning dead `*Glow` tokens (revisit in Phase 23/24 per D-11)
</user_constraints>

---

## Summary

Phase 22 is the last engineering phase before ship: it converts a feature-complete but recomposition-heavy codebase into one that meets the Adreno-320 floor perf budget without changing any visible behavior. The root causes are well-identified in the static audit (CONCERNS.md): `PrinterState` is an unstable type emitted at 4 Hz, causing the entire shell and home screen to re-execute four times per second; the 28 `AppShell` flow-collections share one composition scope so any state update re-evaluates all of them; `AndroidView` update-blocks unconditionally call `invalidate()` regardless of whether tokens changed; and the `SpoolGlyph` allocates a new `Brush` on every draw pass.

The research confirms all the CONTEXT.md decisions are technically sound and executable with the existing stack. The one non-obvious finding concerns `kotlinx-collections-immutable`: the library's HEAD now requires Kotlin 2.3.0 (too new for this project), but **0.3.8** requires only ≥1.9.21 and is fully compatible with Kotlin 2.1.21. It is also not currently in `libs.versions.toml` and must be added. A second notable finding: Kotlin 2.1.21 has **strong skipping mode enabled by default**, which partially mitigates the lambda-slot problem (the inline lambdas at lines 490/522 of `PrintStatusScreen`) — but `@Immutable` annotation on collection-bearing types remains required because strong skipping uses instance equality, not object equality, for comparison.

The measurement protocol already has all the infrastructure it needs: `tools/gfxinfo-parser/parse_framestats.py` exists and the ADR-0001 Addendum 2 reframed gate (zero frozen frames + responsiveness, not a millisecond threshold) is the acceptance standard for SC1.

**Primary recommendation:** Execute in this wave order: (Wave 0) measure release-mode baselines on flox for the screen sweep; (Wave 1) add `kotlinx-collections-immutable`, annotate `PrinterState` tree `@Immutable`, replace collection fields; (Wave 2) split `PrintStatusScreen`, push `AppShell` collections down; (Wave 3) guard AndroidView update-blocks, fix `SpoolGlyph` brush cache, fix `BoxWithConstraints` in cells, fix `LauncherGrid`, fix `spoolSwatches` allocation, fix `ImageRequest.Builder` remember, fix `WebcamView.measureText` cache; (Wave 4) `GraphView` overdraw relief, re-measure, owner sign-off.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Compose stability annotations | State layer (`state/PrinterState.kt`) | UI layer (consumers) | `@Immutable` lives on the data type; Compose reads it at the call site |
| Collection type migration | State layer | AppShell / holders | `ImmutableList`/`ImmutableMap` must be the declared field type; consumers see the stable interface |
| Flow collection push-down | UI feature screens | AppShell | Shell keeps only the 3 flows it directly renders; everything else moves to the screen that reads it |
| PrintStatusScreen split | UI feature layer | Design system (ScreenScaffold) | Each new slice file is its own independently-restartable scope |
| AndroidView update-block guarding | Render layer (`render/`) | None | Change is purely in the View-side cache (not in Compose code) |
| GraphView overdraw | Render layer (`render/GraphView.kt`) | None | Canvas draw logic; threshold decision is owner-gated on visual check |
| SpoolGlyph brush cache | Design system (`designsystem/icons/`) | None | Mirrors the existing `ColorWheel.kt` `rememberHueSweep()` pattern |
| gfxinfo measurement | Build/tooling (`tools/gfxinfo-parser/`) | Manual adb | Parser exists; measurement is owner-driven |

---

## Standard Stack

### Core — No New Libraries (Phase is Refactor-Only)

This phase modifies existing code; the only net-new dependency is `kotlinx-collections-immutable`.

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `kotlinx-collections-immutable` | **0.3.8** | `ImmutableList`/`ImmutableMap` for Compose stability | Official Kotlin library; 0.3.8 = last stable compatible with Kotlin 2.1.x (0.4.0 also works; 0.5.0-beta01 requires Kotlin 2.3.0 — INCOMPATIBLE with this project's 2.1.21 pin) |

**Installation (add to `libs.versions.toml` and `app/build.gradle.kts`):**

```toml
# In [versions]:
kotlinxCollectionsImmutable = "0.3.8"

# In [libraries]:
kotlinx-collections-immutable = { group = "org.jetbrains.kotlinx", name = "kotlinx-collections-immutable", version.ref = "kotlinxCollectionsImmutable" }
```

```kotlin
// In app/build.gradle.kts dependencies block:
implementation(libs.kotlinx.collections.immutable)
```

**No R8 rules needed:** `ImmutableList` / `ImmutableMap` are interfaces with no reflective access; R8 shrinks them correctly without custom keep rules.

**Version verification:** [VERIFIED: github.com/Kotlin/kotlinx.collections.immutable/releases] — 0.3.8 released 2023-09-05, Kotlin dependency ≥1.9.21; Kotlin 2.1.21 ≫ 1.9.21. [VERIFIED: github.com/Kotlin/kotlinx.collections.immutable/releases] — 0.4.0 released 2024-05-14 (targets Kotlin 2.1.20, also compatible). 0.5.0-beta01 (Kotlin ≥2.3.0 required) = excluded.

### Existing Stack Entries This Phase Touches

All other libraries (Compose BOM 2026.05.00, OkHttp, Kotlin 2.1.21, etc.) are unchanged. Compose BOM 2026.05.00 uses Kotlin 2.1.x. The Compose Compiler plugin version equals the Kotlin version (managed by `org.jetbrains.kotlin.plugin.compose` since Kotlin 2.0+).

---

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| `kotlinx-collections-immutable` | Maven Central | 5+ yrs | Very high (JetBrains official) | [github.com/Kotlin/kotlinx.collections.immutable](https://github.com/Kotlin/kotlinx.collections.immutable) | [OK] — official JetBrains library | Approved |

**Packages removed due to slopcheck [SLOP] verdict:** none

**Packages flagged as suspicious [SUS]:** none

*slopcheck was not run (CLI unavailable in this environment), but `kotlinx-collections-immutable` is an official JetBrains library published under `org.jetbrains.kotlinx` with years of Maven Central history and a public GitHub source — no legitimacy concern. Tagged `[CITED: github.com/Kotlin/kotlinx.collections.immutable]` rather than `[VERIFIED: npm registry]` because this is a Maven/Gradle package, not npm.*

---

## Architecture Patterns

### System Architecture Diagram

```
MoonrakerService (FGS)
    │ PrinterStateStore (4 Hz emission)
    ▼
AppContainer.printerState: StateFlow<PrinterState>
    │
    ├── BEFORE: AppShell collects 28 flows here → any change recomposes entire shell
    │                                              → all 28 values re-evaluated together
    │
    └── AFTER: AppShell collects only 3 flows (connectionState / activeDest / drawerOpen)
              │
              ├── PrintStatusScreen collects its own printerState, metadata, etc.
              │   ├── PrintStatusFocus.kt  ← independently restartable scope
              │   ├── PrintStatusField.kt  ← independently restartable scope
              │   └── PrintStatusGutter.kt ← independently restartable scope
              │
              ├── TemperatureScreen collects its own flows
              ├── FilesScreen collects its own flows
              └── etc. — each screen owns its collection scope

PrinterState (BEFORE: unstable data class with Map/List fields → whole tree always recomposes)
PrinterState (AFTER: @Immutable + ImmutableMap/ImmutableList fields → Compose can skip)
```

### Recommended Project Structure (Changes Only)

```
app/src/main/java/works/mees/dinghy/
├── state/
│   └── PrinterState.kt          # Add @Immutable to PrinterState + all nested value types;
│                                #   replace Map/List fields with ImmutableMap/ImmutableList
├── ui/
│   ├── shell/
│   │   └── AppShell.kt          # Push 25 flows out; keep only 3 (conn / dest / drawerOpen)
│   └── printstatus/
│       ├── PrintStatusScreen.kt  # Orchestration + state collection shell; now smaller
│       ├── PrintStatusFocus.kt   # NEW: Focus-region composable (independently restartable)
│       ├── PrintStatusField.kt   # NEW: Field-region composable (independently restartable)
│       ├── PrintStatusGutter.kt  # NEW: Gutter composable (independently restartable)
│       └── PrintStatusPreviews.kt# NEW: All @Preview declarations (6 combos × portrait/landscape)
├── render/
│   ├── GraphViewHost.kt          # Add token-equality cache guard in update block
│   ├── GraphView.kt              # Reduce secondary-trace fill opacity OR pre-rasterize static
│   └── WebcamViewHost.kt         # Add token-equality cache guard in update block
└── designsystem/
    └── icons/
        └── SpoolGlyph.kt         # remember(render) { Brush.linearGradient(...) }
```

---

## Research Findings by Focus Area

### 1. Splitting a Compose God-Component for Restartability

**Mechanics of restartability:**
A Compose function is "restartable" when the compiler gives it a separate recomposition scope — meaning it can be re-executed independently from its callers when its own state inputs change. Every non-inline `@Composable` function is restartable by default. [CITED: developer.android.com/develop/ui/compose/performance/stability]

A Compose function is "skippable" when ALL its parameters are stable AND those parameters have not changed since the last composition. With strong skipping mode enabled (default in Kotlin 2.0.20+, and this project uses Kotlin 2.1.21), composables with unstable parameters also become skippable via instance equality. However, for collection-bearing types, strong skipping compares by reference — a new `List<>` instance is always unequal to the previous one, even if the contents are identical. This is why `@Immutable` + `ImmutableList` is required beyond just enabling strong skipping. [CITED: developer.android.com/develop/ui/compose/performance/stability/strongskipping]

**How to make `PrintStatusScreen` split work correctly:**

The split must produce files where each root composable (`PrintStatusFocus`, `PrintStatusField`, `PrintStatusGutter`) receives only the parameters it directly reads. If a slice receives a large `PrinterState` that contains fields it doesn't read, those fields still cause the slice to recompose on any `PrinterState` change — even after `@Immutable` annotation — unless the unused fields are absent from the call.

Best practice: pass only the specific values each slice needs (not the full `PrinterState`), using `@Immutable` value types or stable primitives. Example:

```kotlin
// GOOD: pass only what Focus needs — Compose can skip if these specific values match
@Composable
fun PrintStatusFocus(
    klippyState: KlippyState,       // @Immutable enum
    printState: PrintState,          // @Immutable enum
    progress: Float,                 // primitive (always stable)
    heaters: ImmutableMap<String, HeaterState>,  // stable after D-02
    thumbnailUrl: String?,           // primitive (stable)
    ...
) { ... }

// BAD: passing the full PrinterState even if only 4 fields are read
//   → Compose sees PrinterState as a parameter → checks if it changed → will skip only
//   if @Immutable is correctly applied to PrinterState AND all its fields
@Composable
fun PrintStatusFocus(state: PrinterState) { ... }  // OK if @Immutable applied correctly
```

The existing `PrintStatusContent` already establishes the right pattern: it is a stateless composable receiving resolved values. The split turns `PrintStatusContent`'s three logical regions into three separate files, each independently skippable. [ASSUMED: exact parameter decomposition per slice — planner determines exact seams; the principle is verified]

**Lambda slots at lines 490 and 522 (CONCERNS P0):**

`gutterContent` and `activeFieldContent` are `@Composable () -> Unit` lambdas constructed inline inside `PrintStatusContent`. These are new object instances on every recomposition. `ScreenScaffold` receives them as slot parameters and cannot skip because the lambda reference is always different.

**Important finding: strong skipping mode partially mitigates this.** Kotlin 2.1.21 (this project) has strong skipping mode on by default, which causes the Compose compiler to auto-`remember` lambdas with unstable captures. However, this only applies to lambdas that are non-capturing or whose captures are trackable. These specific lambdas capture `ui.gutter`, `state`, `metadata`, `spoolmanPresent`, etc. — all potentially unstable. The safest fix remains splitting these into separate named `@Composable fun` declarations, which makes them truly restartable scopes rather than anonymous lambdas. [CITED: developer.android.com/develop/ui/compose/performance/stability/strongskipping]

**Pitfalls that silently defeat restartability:**

1. **Inline functions:** `Row`, `Column`, `Box`, `LazyRow` are inline — they do NOT create their own recomposition scope. A `Column { SliceA(); SliceB() }` — if `Column` is inline — means `SliceA` and `SliceB` share the parent's scope. Use named top-level `@Composable fun` declarations (non-inline) to create independent scopes.

2. **Reading rapidly-changing state too high:** Any read of `PrinterState` (or any `collectAsStateWithLifecycle`) inside a composable scope makes that ENTIRE scope sensitive to 4 Hz changes. The split is only effective if each slice collects (or receives) only the values it genuinely needs.

3. **Non-skippable functions in the call path:** If `ScreenScaffold` is non-skippable (because it receives unstable lambda params), re-measuring that root-level composable re-measures everything inside it. The lambda fix (D-02) addresses this directly.

4. **`@Immutable` on a type with any mutable field:** The annotation is a promise, not enforcement. If any field of an `@Immutable` type is a `var` or a mutable container, Compose will read the "never changes" contract and stop checking — silently showing stale data. Every nested type in the `PrinterState` tree (HeaterState, OutputLiveValue, Screw, BedMeshObject, etc.) must have only `val` fields (they already do — they are data classes with `val` only). [CITED: developer.android.com/develop/ui/compose/performance/stability]

---

### 2. The AppShell Push-Down

**Why wide collection at the shell root is harmful:**

`AppShell` currently calls `collectAsStateWithLifecycle` 28 times before the `when(dest)` router. Each collection produces a `State<T>` read inside the `AppShell` composable scope. When any one of those 28 flows emits (including the 4 Hz `printerState`), Compose re-evaluates the entire `AppShell` function — including all 28 `by` reads. Because `AppShell` is the parent of the screen graph, every such re-evaluation also potentially recomposes children.

With the push-down, each screen collects only its own flows. The shell collects only:
- `container.connectionState` (drives the shell-level reconnection overlay)
- `nav.dest` (the active screen routing key — already a Compose `mutableStateOf`)
- `drawerOpen` (local `mutableStateOf`)

**Safe mechanical recipe:**

```kotlin
// BEFORE (in AppShell):
val printerState by container.printerState.collectAsStateWithLifecycle()
val capabilities by container.capabilities.collectAsStateWithLifecycle(...)
// ... 26 more ...
PrintStatusScreen(printerState = printerState, capabilities = capabilities, ...)

// AFTER (in AppShell):
// AppShell passes the container reference; screens collect their own flows
PrintStatusScreen(container = container, ...)  // already the actual call signature

// AFTER (inside PrintStatusScreen — already done this way!):
val state by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
```

**Key finding: `PrintStatusScreen` already receives `container` and does its own `collectAsStateWithLifecycle`.** Reviewing the actual code: `PrintStatusScreen.kt:135` has `val state by container.printerState.collectAsStateWithLifecycle(...)`. The problem is that `AppShell` ALSO collects `printerState` at line 203 for other purposes (building holders, passing to sub-screens). The push-down means:
- Remove collections in AppShell that are ONLY used to pass to screen calls
- Keep collections that AppShell itself uses for shell-level logic (drawer tile state, webcam visibility, etc.)
- Each screen receives `container` and collects what it needs

Inspecting AppShell lines 185–482 reveals which collections can move:
- `printerState` at line 203: used in AppShell to pass to the `when(dest)` screens AND for some holder builds. Examine carefully — some holder builds use `printerStateFlow` directly, not the collected value.
- `activeSpoolDetail` at line 313: only used to compute `drawerSpoolSwatches` for the drawer tile. This stays in AppShell (drawer is shell-level UI).
- `zTiltVm`, `qglVm`, `bedMeshVm`, `probeCalibrateVm` (lines 386–389): passed to the calibration screens — these can move into the calibration screens.
- `errorLines` (line 421): passed to `PrintStatusScreen` — this can stay in AppShell since it's a screen-slot parameter, or move to `PrintStatusScreen`; either is fine since the mapping is one-to-one.

**Lifecycle-aware collection safety:** `collectAsStateWithLifecycle` works identically whether called in the shell or in a child composable. The lifecycle owner is automatically resolved from the nearest `LocalLifecycleOwner`. Pushing collection down into a screen does not change the lifecycle awareness. [CITED: medium.com/androiddevelopers/consuming-flows-safely-in-jetpack-compose]

**Verification:** Android Studio's Layout Inspector "Recomposition counts" shows composition counts per composable. After the push-down, AppShell's recomposition count should be near-zero between navigation events; PrintStatusScreen's recomposition count will increase (it now owns those counts) but each slice's count should be smaller than the old monolithic count. This is the correct Layout Inspector workflow for before/after confirmation.

**AppShell collections that stay (shell-level):**
- `spine` — the FGS-published session handle; shell needs it to build/re-key holders
- `connectionState` / `webcamEnabled` / `spoolEnabled` / `outputsEnabled` — drawer tile visibility
- `drawerSpoolSwatches` — drawer spool icon
- `activeName` / `activeProfileId` — drawer subtitle; webcam key
- Holder builds (`remember(store) { ... }`) — these are shell-owned per-session state, not collections
- `errorLines` — either here or in PrintStatusScreen (both are valid)

---

### 3. `@Immutable`/`@Stable` on the Socket-Driven State Tree

**Correct annotations for `PrinterState`:**

`PrinterState` is a Kotlin `data class` with all `val` fields, replaced wholesale on each emission (never mutated in place). This is exactly the `@Immutable` contract: once created, it never changes. [CITED: developer.android.com/develop/ui/compose/performance/stability]

```kotlin
import androidx.compose.runtime.Immutable

@Immutable
data class PrinterState(
    val klippyState: KlippyState = KlippyState.Disconnected,
    val heaters: ImmutableMap<String, HeaterState> = persistentMapOf(),
    val temperatureSensors: ImmutableMap<String, Double> = persistentMapOf(),
    val toolheadPosition: ImmutableList<Double>? = null,
    val gcodePosition: ImmutableList<Double>? = null,
    val outputs: ImmutableMap<String, OutputLiveValue> = persistentMapOf(),
    // ... all other fields unchanged (they are primitives, Strings, enums, or
    //     other @Immutable data classes — already stable after annotation)
    val screwsTilt: ScrewsTiltObject? = null,  // @Immutable after annotation
    val bedMesh: BedMeshObject? = null,        // @Immutable after annotation (has List fields → fix too)
)
```

**Full annotation scope (every nested type):**
- `HeaterState` — `data class`, all `val`, no collections → `@Immutable`
- `OutputLiveValue` — `data class`, all `val`, has `List<List<Double>>? colorData` → `@Immutable` + replace `colorData` with `ImmutableList<ImmutableList<Double>>?`
- `FirmwareRetractionObject` — `data class`, all `val`, no collections → `@Immutable`
- `ScrewsTiltObject` — `data class`, has `Map<String, ScrewResult> results` → `@Immutable` + `ImmutableMap`
- `ScrewResult` — `data class`, all `val`, no collections → `@Immutable`
- `BedMeshObject` — `data class`, has multiple `List<*>` fields → `@Immutable` + replace all `List<*>` with `ImmutableList<*>`
- `ManualProbeObject` — `data class`, all `val`, no collections → `@Immutable`
- `Screw` — `data class`, all `val`, no collections → `@Immutable`
- `ScrewConfig` — has `List<Screw>` → `@Immutable` + `ImmutableList<Screw>`
- `ConnectionState` — sealed interface; each subtype is already a `data object` or `data class` with `val` only → `@Immutable` on each subtype (and the sealed interface itself if desired, though enum-equivalent sealed types are already inferred stable by the compiler)
- `KlippyState`, `PrintState` — enums; enums are always `@Stable` by the Compose compiler → no annotation needed

**Immutable collection factory functions:**

```kotlin
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableList

// Default values in PrinterState:
val heaters: ImmutableMap<String, HeaterState> = persistentMapOf()
val temperatureSensors: ImmutableMap<String, Double> = persistentMapOf()
val outputs: ImmutableMap<String, OutputLiveValue> = persistentMapOf()
val toolheadPosition: ImmutableList<Double>? = null
```

**Impact on `PrinterStateStore` (the reducer):**

The reducer builds `PrinterState` by accumulating into a mutable intermediate and emitting new instances. The emission site must call `.toImmutableMap()` / `.toImmutableList()` on the mutable working collections before building the `PrinterState`. The accumulator can remain a mutable `HashMap`/`MutableList` internally — only the emitted snapshot needs to be `ImmutableMap`/`ImmutableList`. This is the standard pattern. [ASSUMED: accumulator internals; confirmed pattern from kotlinx-collections-immutable docs]

**The `@Immutable` gotcha — what breaks if wrong:**

`@Immutable` is a developer promise, not compiler enforcement. If any field is secretly mutable (a `var` or a standard `MutableList` stored as `List`), Compose will assume the instance "never changes" and skip recomposition even when the content changes. On the `PrinterState` tree every field is already `val` and the nested types are all `data class` with `val` fields — so the annotation is safe to apply without risk of this pitfall. The `OutputLiveValue.colorData: List<List<Double>>?` field is a `val` holding a potentially mutable-at-runtime standard `List` — after replacing with `ImmutableList<ImmutableList<Double>>?`, the `@Immutable` contract is fully satisfied. [CITED: developer.android.com/develop/ui/compose/performance/stability]

**Strong skipping mode interaction:**

Kotlin 2.1.21 has strong skipping enabled by default. This means composables with unstable params become skippable (via instance equality). But with `@Immutable` + `ImmutableList`, Compose uses object equality (structural, via `equals()`). `ImmutableMap`/`ImmutableList` from `kotlinx-collections-immutable` implement structural equality correctly. This is superior to strong skipping alone because: (a) it works on Compose 1.x not just future runtimes; (b) it skips based on content equality, not reference identity — a rebuilt `PrinterState` with the same values still allows skipping. [CITED: developer.android.com/develop/ui/compose/performance/stability/strongskipping]

---

### 4. AndroidView Update-Block Hygiene

**The bug (confirmed by code inspection):**

Both `GraphViewHost` and `WebcamViewHost` call `view.applyTokens(tokens)` in every `update` block execution. `applyTokens` calls `invalidate()`, scheduling a full `onDraw`. The `update` block fires on every recomposition. Since `AppShell` and `PrintStatusScreen` recompose at 4 Hz (pre-fix), the graph and webcam Views also redraw at 4 Hz even when the theme has not changed. Post-fix (after `@Immutable` + push-down), they will still recompose whenever `tokens` is passed from a collecting parent — the update-block guard is an independent layer of protection.

**Canonical guard pattern:**

```kotlin
// Inside GraphView (add a private var):
private var lastTokens: ThemeTokens? = null

// Modified applyTokens:
fun applyTokens(tokens: ThemeTokens) {
    if (tokens == lastTokens) return      // short-circuit: no paint changes, no invalidate
    lastTokens = tokens
    // ... existing paint assignment code ...
    invalidate()
}
```

`ThemeTokens` is already annotated `@Immutable` (confirmed in `theme/ThemeTokens.kt:28`). Its `equals()` comparison works correctly because all fields are primitives or `Color` objects (value types). The equality check is O(1) per field — cheap. [VERIFIED: code inspection of ThemeTokens.kt]

The same pattern applies to `WebcamViewHost`/`WebcamView.applyTokens`.

**Additional `WebcamViewHost` calls in `update` block:**

`WebcamViewHost` also calls `view.setTransform(...)`, `view.setFrame(frame)`, `view.setChrome(mode, camName, serviceName, multiCam)`. Each of these also calls `invalidate()`. The same equality-guard pattern applies to each setter:

```kotlin
// setTransform guard:
private var lastFlipH = false; private var lastFlipV = false; private var lastRot = 0
fun setTransform(flipH: Boolean, flipV: Boolean, rotation: Int) {
    if (flipH == lastFlipH && flipV == lastFlipV && rotation == lastRot) return
    lastFlipH = flipH; lastFlipV = flipV; lastRot = rotation
    // ... existing transform code ...
    invalidate()
}
// Similarly for setChrome(mode, camName, serviceName, multiCam)
```

`setFrame(frame: Bitmap?)` should NOT be equality-guarded — a new bitmap reference (even at the same address) represents genuinely new frame data. Keep `setFrame` as unconditional invalidate.

**GraphViewHost multi-overload:** The multi-trace overload (lines 89–100) calls `view.applyTokens(tokens)` AND `view.setData(series)` AND `view.setSetpoints(setpoints)`. `applyTokens` gets the equality guard; `setData` and `setSetpoints` should remain unconditional (new data = new frame needed).

**Confirming leak-free (no LeakCanary):**

The existing `DisposableEffect(webcamHolder) { onDispose { webcamHolder.cancel() } }` pattern is the correct leak-prevention mechanism for holders. For the View-level: `AndroidView` with `factory = { ctx -> MyView(ctx) }` creates the View once. Compose disposes the View when the composable leaves the tree; the `update` block runs within the composition's coroutine scope so there are no cross-scope captures. The equality-guard modification is entirely within the View class (no new coroutine captures). Manual confirmation: inspect that no `Handler.postDelayed`, no `Runnable`, and no observer registration survives the View's `onDetachedFromWindow`. The existing View classes (`GraphView`, `WebcamView`) are custom Canvas Views with no such registrations — confirmed by code pattern (they only use `invalidate()`-driven draw). [ASSUMED: WebcamView internals not fully read; planner should verify no registered callbacks in WebcamView that need cleanup] [VERIFIED: GraphView code inspection — no registered callbacks, only invalidate-driven draw]

---

### 5. GraphView Overdraw Relief on Adreno 320 Fill-Rate

**The measured baseline (ADR-0001 Addendum 2):**

The existing graph redraw already measures ~24 ms GPU/composite just to composite the 1200×1920 native window — this is a hardware physics floor on the Adreno 320 / 1920×1200. The reframed perf gate is: zero frozen frames + renders complete comfortably under the 250 ms sample interval. The graph currently has up to 3 traces with full-width area fills. The existing comment at GraphView.kt:283 already flags this: *"Per-trace fill is extra Adreno-320 fill-rate vs the old single-trace fill."*

**Option A — Reduce secondary trace opacity (recommended first attempt):**

The current `FILL_ALPHA = 40` (out of 255, ~16%) for all traces. Reduce secondary traces (index 1 and 2) to a lower alpha, say 20–25, or disable their area fill entirely (primary trace keeps area fill; secondary traces draw line-only):

```kotlin
// In onDraw, change the per-trace fill logic:
if (drawArea && t == primaryTraceIndex) {  // only primary trace gets area fill
    // ... existing fill code ...
}
```

This halves the per-frame fill-rate coverage for 2-trace and 3-trace graphs (common cases on the temperature screen). It is visually distinguishable (secondary traces become line-only) but intentionally so — the line color still identifies the trace. Owner must approve the visual change on flox.

**Option B — Pre-rasterize static content (setpoint/axis lines) to off-screen Bitmap:**

The setpoint lines (horizontal dashed lines) and axis labels are static for the duration of a temperature hold. Pre-rasterizing them:

```kotlin
private var staticBitmap: Bitmap? = null
private var staticBitmapTokens: ThemeTokens? = null

private fun ensureStaticBitmap(w: Int, h: Int, tokens: ThemeTokens) {
    if (staticBitmap?.width == w && staticBitmap?.height == h
        && staticBitmapTokens == tokens) return
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    // ... draw setpoint + axis labels onto c ...
    staticBitmap = bmp
    staticBitmapTokens = tokens
}

override fun onDraw(canvas: Canvas) {
    ensureStaticBitmap(width, height, lastTokens ?: return)
    staticBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    // ... draw live trace lines on top ...
}
```

**Tradeoff analysis for Adreno 320:**

Option A is the leaner default and carries less code complexity. Reducing a translucent fill-width pass from 3 × full-width to 1 × full-width cuts fill-rate in the graph by ~2× for multi-trace use. This directly addresses the Adreno 320's fill-rate bottleneck at low marginal complexity cost.

Option B pre-rasterizes to an off-screen bitmap, which is a blit operation (cheap) instead of a fill-rate operation (expensive on this GPU). However, it adds memory allocation (a full-size ARGB_8888 bitmap = 1200 × H_graph × 4 bytes ≈ 3–6 MB for a 600–1200px-tall graph) and GC pressure on invalidation. It is most valuable when setpoints change rarely — which is true for printer temps — but the Bitmap allocation must be checked against the 2 GB ceiling.

**Recommendation (Claude's discretion, per CONTEXT.md):** Start with Option A (opacity reduction on secondary traces). Capture gfxinfo before/after. If the graph still misses the gate, layer Option B for setpoints. Never combine them in one wave (isolate the measurement delta).

Importantly: after D-12's update-block guard, the graph will only `invalidate()` when `setData` is called (every 250 ms) rather than on every recomposition. This alone may be sufficient for the gate — measure first. [ASSUMED: exact fill-rate improvement from guard alone; must be measured]

---

### 6. Cached-Brush Pattern

**Confirmed template: `ColorWheel.kt`**

`ColorWheel.kt:184` defines:
```kotlin
@Composable
private fun rememberHueSweep(): Brush =
    remember {
        Brush.sweepGradient(*stops.toTypedArray())
    }
```

The pattern: `remember { Brush.sweep/linearGradient(...) }` with no keys = cached forever (for the lifetime of the composable in the tree). This is correct when the gradient does NOT change.

**Fix for `SpoolGlyph.kt:90`:**

```kotlin
// BEFORE (allocates new Brush on every recomposition):
is SpiralRender.Gradient -> Brush.linearGradient(
    0f to render.start, 1f to render.end,
    start = Offset(spiralBounds.left, spiralBounds.center.y),
    end = Offset(spiralBounds.right, spiralBounds.center.y),
)

// AFTER (keyed on render so it rebuilds only when the filament color changes):
val gradientBrush = remember(render) {
    if (render is SpiralRender.Gradient) Brush.linearGradient(
        0f to render.start,
        1f to render.end,
        start = Offset(spiralBounds.left, spiralBounds.center.y),
        end = Offset(spiralBounds.right, spiralBounds.center.y),
    ) else null
}
val brush = when (render) {
    is SpiralRender.Solid -> SolidColor(render.color)
    is SpiralRender.Gradient -> gradientBrush ?: SolidColor(render.start) // fallback
    SpiralRender.Empty -> return@Canvas                                    // unreachable here
}
```

Key: `remember(render)` — `render` is a sealed class instance whose equality is defined by its `data class` fields (the two `Color` values). When the filament color changes, a new `SpiralRender.Gradient` instance is created with different `Color` fields → `remember` sees a different key → rebuilds the Brush. When the same filament color emits repeatedly (most of the time), `remember` returns the cached Brush. [CITED: CONCERNS.md code example; CITED: ColorWheel.kt:184 pattern]

`SpiralRender` is already an `internal sealed interface` with a `data class Gradient(val start: Color, val end: Color)` — `data class` provides structural equality, so `remember(render)` key comparison works correctly.

**Why this matters:** `SpoolGlyph` appears on 5 surfaces: launcher tile, drawer tile, mid-print shortcut, SpoolScreen empty-state, SpoolScreen detail. If any of these are inside a 4 Hz recomposition scope (which they are, pre-fix), the Brush allocation fires 4×/sec × 5 surfaces = 20 unnecessary `Brush.linearGradient` allocations per second. Post-fix this is 0 allocations unless the spool color changes.

---

### 7. gfxinfo Framestats Measurement Protocol

The existing `tools/gfxinfo-parser/parse_framestats.py` is the system of record. The exact protocol for a clean before/after per-screen capture:

**Setup prerequisites:**
- Build and install the RELEASE APK (R8/minify on): `cmd /c "E:\Android\gw.bat :app:assembleRelease --no-daemon"` then sign with `sign-release.bat` and `adb install`
- Enable GPU rendering profiling in Developer Options on flox: Settings → Developer options → Profile GPU rendering → "In adb shell dumpsys gfxinfo"
- Confirm package is `works.mees.dinghy`

**Per-screen capture sequence:**

```bash
# 1. Navigate to the target screen on the device (physically)
# 2. Let it stabilize 3 seconds (warm up any JIT/AOT that didn't get AOT-compiled)
# 3. Reset the ring buffer:
adb shell dumpsys gfxinfo works.mees.dinghy reset

# 4. Exercise the screen for 6–10 seconds:
#    For steady-state screens: just wait (PrintStatus emits 4 Hz)
#    For nav transitions: navigate in and out 3× via adb input or physical taps
#    For lists (Files/Console): adb shell input swipe 500 900 500 300 500  (6×)

# 5. Dump framestats (before ring buffer wraps — ~120 frames = ~2s at 60Hz = do this promptly):
adb shell dumpsys gfxinfo works.mees.dinghy framestats > capture_SCREENNAME_before.txt

# 6. Repeat steps 2–5 to get multiple captures for stability:
#    Concatenate and run through the parser (it deduplicates by IntendedVsync):
python3 tools/gfxinfo-parser/parse_framestats.py capture1.txt capture2.txt
```

**Output interpretation (parse_framestats.py):**
- `p50`, `p90`, `p95` frame time in ms
- `count(frames > 700ms)` — frozen frames (MUST be 0)
- ADR-0001 Addendum 2 gate: zero frozen frames + "renders complete comfortably under 250 ms sample interval" — the p95 is a sanity check, not a hard target

**Screens in the baseline sweep (D-06):**
1. `PrintStatusScreen` steady-state (idle and mid-print)
2. App Drawer open/close transition (swipe up + release)
3. Navigate into `FilesScreen` and scroll the file list
4. Navigate into `ConsoleScreen` (live gcode responses)
5. Navigate into `TemperatureScreen` (multi-trace graph)
6. Navigate into `WebcamScreen` (MJPEG + H.264 if connected)
7. Navigate into a scrubber/control screen (e.g. MoveScreen)

**Important capture note:** The ring buffer holds ~120 frames. At 4 Hz state updates with 60 Hz vsync, a 6-second window ≈ 360 vsync frames — capture multiple dumps or capture within the 2-second window. The parser concatenates and deduplicates, so multiple smaller dumps are fine. [CITED: tools/gfxinfo-parser/parse_framestats.py header comments]

**API 23 note:** The existing `parse_framestats.py` was built and validated on this exact setup (API 23 / API 30 flox). `FrameTimingMetric` from Macrobenchmark is parked (D-07). `gfxinfo framestats` is the only gate.

---

### 8. Remaining P2 Fixes (Claude's Discretion)

**`BoxWithConstraints` in `IconTwoRowCell` / `IconValueCell` (P1):**

Replacing with a fixed-size approach. The cells already know the column width (via `Modifier.weight(1f)` in the grid). `BoxWithConstraints` in cells is avoidable:

```kotlin
// BEFORE (IconTwoRowCell at line 857, IconValueCell at line 888):
BoxWithConstraints {
    val iconSize = maxWidth * 0.3f  // compute icon size from available width
    Icon(size = iconSize)
}

// AFTER: pass explicit sizeDp derived from the grid column width
@Composable
fun IconTwoRowCell(
    iconSize: Dp,  // caller passes this based on the column width / grid layout
    ...
) {
    // Use iconSize directly — no BoxWithConstraints needed
    Icon(modifier = Modifier.size(iconSize))
}
```

`ScreenScaffold`'s root `BoxWithConstraints` is acceptable (one per screen, not per-cell). Guard against adding any additional nesting inside `ScreenScaffold`. [CITED: CONCERNS.md P1 analysis]

**`LauncherGrid` (P2 → `LazyVerticalGrid`):**

```kotlin
// AFTER:
LazyVerticalGrid(
    columns = GridCells.Fixed(columns),
    modifier = modifier,
) {
    items(tiles, key = { it.id }) { tile ->
        TileComposable(tile)
    }
}
```

For the current ~4 tile count, this has minimal visible impact but is the correct pattern for future growth. Low risk; easy fix.

**`spoolSwatches: List<Color>` allocation (P1):**

```kotlin
// AFTER (in PrintStatusScreen):
val spoolSwatches: ImmutableList<Color> = remember(spoolDetail, metadata) {
    val spoolmanColors = spoolDetail?.filament?.colorSwatches.orEmpty()
        .mapNotNull(::parseNormalizedHex)
    if (spoolmanColors.isNotEmpty()) {
        spoolmanColors.toImmutableList()
    } else {
        val gcodeColor = metadata?.filamentColors?.firstOrNull()?.let(::parseNormalizedHex)
        if (gcodeColor != null) persistentListOf(gcodeColor) else persistentListOf()
    }
}

// AFTER (in AppShell for drawer):
val drawerSpoolSwatches: ImmutableList<Color> = remember(activeSpoolDetail) {
    activeSpoolDetail?.filament?.colorSwatches.orEmpty()
        .mapNotNull(::parseNormalizedHex)
        .toImmutableList()
}
```

**`ImageRequest.Builder` (P2):**

```kotlin
// AFTER (in PrintStatusScreen ~line 680):
val imageRequest = remember(thumbnailUrl, context) {
    ImageRequest.Builder(context)
        .data(thumbnailUrl)
        .build()
}
AsyncImage(model = imageRequest, ...)
```

**`WebcamView.measureText` caching (P2):**

In each draw helper in `WebcamView`:
```kotlin
private var lastBadgeText = ""; private var lastBadgeWidth = 0f
fun drawCornerBadge(canvas: Canvas, text: String, ...) {
    if (text != lastBadgeText) { lastBadgeText = text; lastBadgeWidth = paint.measureText(text) }
    // use lastBadgeWidth
}
```

**`AppShell.errorLines` flow (P3 — opportunistic):**

When AppShell is already being modified for the push-down:
```kotlin
// Move to AppContainer as a named StateFlow:
val errorLines: StateFlow<List<String>> = consoleScrollback
    .map { lines -> lines.asSequence().filter { it.severity == ERROR }.map { it.rawMessage }.toList().takeLast(3) }
    .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
```

**`SimpleDateFormat` in `FileRowsAdapter` (P3 — opportunistic):**

```kotlin
companion object {
    // Thread-safe alternative for API 26+; at minSdk 23 with single main-thread binding, companion val is fine:
    private val DATE_FORMAT = SimpleDateFormat("MMM d, HH:mm", Locale.US)
}
```

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Immutable collection types | Custom wrapper classes | `kotlinx-collections-immutable` `ImmutableList`/`ImmutableMap` | Official Kotlin library; Compose compiler recognizes the `@Immutable` annotation on these types |
| Brush caching | Manual cache fields | `remember(key) { Brush.linearGradient(...) }` | Compose composition-aware cache with correct key-based invalidation |
| Stability annotations | Manual skip logic | `@Immutable` / `@Stable` from `androidx.compose.runtime` | Compiler recognizes these and generates correct skip bytecode |
| gfxinfo parsing | New parser script | `tools/gfxinfo-parser/parse_framestats.py` (EXISTS) | Already validated on this exact device/setup; don't duplicate |

---

## Common Pitfalls

### Pitfall 1: Annotating `@Immutable` Without Converting Collection Fields
**What goes wrong:** Adding `@Immutable` to `PrinterState` without also replacing `Map<>` / `List<>` fields with `ImmutableMap<>` / `ImmutableList<>` has no effect. The Compose compiler infers stability from field types, and `Map`/`List` are always considered unstable regardless of the class-level annotation.
**Why it happens:** The annotation alone feels sufficient; the field type conversion is easy to overlook.
**How to avoid:** Treat D-02 as a 3-step atomic unit: (a) add `@Immutable`, (b) replace all Map/List field types, (c) update constructor call sites and factory/conversion code in the reducer.
**Warning signs:** Layout Inspector still shows high recomposition counts on `PrintStatusScreen` after annotation. Compose Compiler reports show types as "unstable" in the generated metrics (enable with `freeCompilerArgs += listOf("-P", "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=...")`).

### Pitfall 2: Forgetting to Update `PrinterStateStore` Reducer Call Sites
**What goes wrong:** `PrinterState` is built in `PrinterStateStore.onStatusDiff()` (and similar). After the field type changes to `ImmutableMap<String, HeaterState>`, every `mapOf()` / `emptyMap()` call in the reducer must become `persistentMapOf()` / `.toImmutableMap()`. The build will fail at these call sites — but only if you changed the field types correctly. If you kept the field types as `Map` but only added `@Immutable`, there are no compile errors and no benefit.
**Why it happens:** The reducer is headless state code and is not in the same file as `PrinterState`.
**How to avoid:** Change field types first, run the build, fix every compile error at reducer call sites — let the compiler guide the migration.

### Pitfall 3: Push-Down Breaking Holder Construction
**What goes wrong:** Some holders in `AppShell` are built with `remember(store) { ... }` using values that came from a `collectAsStateWithLifecycle` in `AppShell`. If a collected value is moved to a screen and the holder still tries to use it, it won't compile or will use a stale reference.
**Why it happens:** The 28 collections are interdependent — some collected values are keys in `remember(...)` calls for holders.
**How to avoid:** Before moving any collection, trace which `remember(...)` calls use it as a key. Holders keyed only on `store` are safe to move with the screen. Holders keyed on a collected value may need to be re-keyed on the `StateFlow` directly (not the collected value).

### Pitfall 4: `ScreenScaffold` Preview Regressions from the Split
**What goes wrong:** `ScreenScaffold` is used by all ~20 screens. Any change to how `PrintStatusScreen`'s slices call `ScreenScaffold` (e.g., passing different lambda types) can cause layout differences visible in previews and on-device.
**Why it happens:** `ScreenScaffold` receives `focus`, `field`, `gutter` as `@Composable () -> Unit` slots. The split moves these from inline lambdas in `PrintStatusContent` to named functions — the named function reference is stable (no re-allocation), which is the desired improvement, but the naming/hoisting must preserve exact layout behavior.
**How to avoid:** Run the full 6-combo `@Preview` matrix (existing `PrintStatusPreviews`) after EACH slice split step. Do NOT batch all splits into one commit before verifying previews.

### Pitfall 5: `ImmutableList`/`ImmutableMap` API Version Mismatch
**What goes wrong:** Using 0.5.0-beta01 which requires Kotlin 2.3.0 — this project uses Kotlin 2.1.21. The build will fail with Kotlin ABI or metadata version errors.
**Why it happens:** The library's README now advertises 0.5.0-beta01 with new naming conventions (`adding` instead of `add`).
**How to avoid:** Pin exactly `0.3.8` (or `0.4.0`) in `libs.versions.toml`. Both are compatible with Kotlin 2.1.x. `0.5.0-beta01` is explicitly excluded.

### Pitfall 6: `AndroidView` Update-Block Guard Breaking Theme Transitions
**What goes wrong:** If the equality guard on `applyTokens` compares by reference (not structural equality), a dark→light theme swap — which creates a new `ThemeTokens` instance with different `Color` values — will correctly trigger recolor. But if `ThemeTokens.equals()` is broken (e.g., if `Color` comparison doesn't work), the guard will short-circuit on a theme change, showing stale colors.
**Why it happens:** `ThemeTokens` is `@Immutable data class` — `data class` provides structural `equals()` by default, which IS correct here. `Color` in Compose is an `@JvmInline value class` wrapping a `Long` — equality is value equality.
**How to avoid:** Confirm `ThemeTokens` is a `data class` (it is: `theme/ThemeTokens.kt:28`). The structural equality implementation is correct. No risk.

---

## Code Examples

### @Immutable Data Class with ImmutableMap
```kotlin
// Source: developer.android.com/develop/ui/compose/performance/stability/fix
// Applied to PrinterState pattern
import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class PrinterState(
    val heaters: ImmutableMap<String, HeaterState> = persistentMapOf(),
    val toolheadPosition: ImmutableList<Double>? = null,
    // ...
)

@Immutable
data class HeaterState(
    val temperature: Double = 0.0,
    val target: Double = 0.0,
    val power: Double = 0.0,
    val canExtrude: Boolean = false,
)
```

### Reducer Emission Site
```kotlin
// In PrinterStateStore, at emission:
_printerState.value = accumulator.copy(
    heaters = mutableHeatersMap.toImmutableMap(),
    toolheadPosition = mutablePositionList?.toImmutableList(),
    // ...
)
```

### collectAsStateWithLifecycle Push-Down
```kotlin
// Source: consuming-flows-safely-in-jetpack-compose, developer.android.com
// Shell: collect only what the shell RENDERS
@Composable
fun AppShell(container: AppContainer, nav: ShellNavState, ...) {
    val connectionState by container.connectionState.collectAsStateWithLifecycle()
    val dest = nav.dest  // already Compose state (mutableStateOf)
    var drawerOpen by remember { mutableStateOf(false) }
    // ... drawer-tile flows stay (shell renders them) ...
    // Screen calls receive container — screens collect their own:
    when (dest) {
        Dest.PrintStatus -> PrintStatusScreen(container = container, ...)
        Dest.Temperature -> TemperatureScreen(container = container, ...)
    }
}

// In PrintStatusScreen — UNCHANGED from current code (it already does this):
@Composable
fun PrintStatusScreen(container: AppContainer, ...) {
    val state by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    // ...
}
```

### AndroidView Update-Block Equality Guard
```kotlin
// Source: AndroidView API pattern + CONCERNS.md D-12
// In GraphView.kt:
private var lastTokens: ThemeTokens? = null

fun applyTokens(tokens: ThemeTokens) {
    if (tokens == lastTokens) return  // structural equality via data class equals()
    lastTokens = tokens
    linePaints[0].color = tokens.tempNozzle.toArgb()
    // ... rest of existing applyTokens body ...
    invalidate()
}
```

### Cached Brush in SpoolGlyph
```kotlin
// Source: ColorWheel.kt:184 template + CONCERNS.md code example
val spiralPath = remember { PathParser().parsePathString(SPIRAL_PATH_DATA).toPath() }
val spiralBounds = remember(spiralPath) { spiralPath.getBounds() }
val render = spiralRenderFor(swatches)

val gradientBrush = remember(render) {
    if (render is SpiralRender.Gradient) Brush.linearGradient(
        0f to render.start,
        1f to render.end,
        start = Offset(spiralBounds.left, spiralBounds.center.y),
        end = Offset(spiralBounds.right, spiralBounds.center.y),
    ) else null
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual `enableStrongSkippingMode = true` | Default-on in Kotlin 2.0.20+ | Kotlin 2.0.20 | Lambda slot issue partially mitigated; `@Immutable` still required for collection types |
| `kotlinx-collections-immutable` 0.3.x (experimental) | 0.3.8 stable / 0.4.0 stable | 2023–2024 | Safe to use without `@Experimental` opt-in; structural equality works |
| Separate Compose Compiler version pin | Compiler version == Kotlin version (since Kotlin 2.0+) | Kotlin 2.0 | Project already correct; no separate compiler pin needed |

**Deprecated/outdated:**
- 0.5.0-beta01 (kotlinx-collections-immutable): requires Kotlin 2.3.0; not for this project
- Compose compiler `kotlinVersion` pin (pre-2.0): replaced by `kotlin.plugin.compose` plugin managing version automatically

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `WebcamView` has no registered callbacks (Handler/Runnable) that would leak | Section 4 (AndroidView Hygiene) | Medium — if WebcamView does register a callback, the equality-guard change doesn't affect that, but the "leak-free" confirmation would be incorrect; planner should add a code-read task for WebcamView internals |
| A2 | The reducer accumulator in `PrinterStateStore` uses mutable `HashMap`/`MutableList` internally, not the public `ImmutableMap` type | Section 3 (@Immutable annotation) | Low — if it already uses immutable types, the emission-site changes are trivial no-ops rather than actual conversions; risk is zero code breakage |
| A3 | Splitting `PrintStatusScreen` into slices that receive `container` (rather than decomposed parameters) is sufficient for Compose to apply skip | Section 1 (God-component split) | Medium — passing `container` means the slice reads `AppContainer` which is process-singleton and stable; the actual collected state is inside the slice. This is the CURRENT pattern and works correctly for `@Stable` AppContainer. If AppContainer is not annotated `@Stable`, it may not help. Planner: verify AppContainer has `@Stable` or annotate it. |
| A4 | Option A (opacity reduction on secondary GraphView traces) is visually acceptable to the owner without approval | Section 5 (GraphView overdraw) | Medium — D-10 requires owner side-by-side approval for any blend/falloff change. The planner must include a `checkpoint:human-verify` before committing any fill/opacity change to GraphView. |
| A5 | `kotlinx-collections-immutable` 0.3.8 does not require any new ProGuard/R8 rules | Standard Stack | Low — the library has no reflective access; R8 can shrink it freely. But if the project's existing R8 configuration has a blanket keep on `kotlinx.**`, it's a non-issue; if not, test that release build does not crash. |

---

## Open Questions

1. **Is AppContainer annotated `@Stable`?**
   - What we know: `AppContainer` is a process-scoped service-locator passed as a parameter to screens. If it is not stable, passing it as a parameter to composables prevents skipping even with `@Immutable` on the data types.
   - What's unclear: `ARCHITECTURE.md` notes it is a "process-scoped service-locator (no DI framework)" but does not confirm `@Stable`.
   - Recommendation: Planner adds a Wave 0 task to read `di/AppContainer.kt` and annotate `@Stable` if absent. [ASSUMED: absent until confirmed]

2. **Exact AppShell collections that CAN move vs MUST stay**
   - What we know: Holder builds (`remember(store) {...}`) stay in AppShell; screen-local flow values can move; drawer-visibility flows stay.
   - What's unclear: Some flows feed both the drawer tile AND a screen (e.g., `spoolEnabled` gates the drawer tile, but the SpoolScreen also uses spool data). The gate-for-drawer stays in AppShell; the screen-level data collection moves.
   - Recommendation: Planner performs a column-by-column audit of AppShell's 28 collections, tagging each: (a) stays — shell renders it directly, (b) moves — only used in a `when(dest)` screen call, (c) split — stays for drawer, moves for screen.

3. **Does `ScreenScaffold` need a `@Stable` annotation?**
   - What we know: Every screen uses `ScreenScaffold`. It receives `@Composable () -> Unit` slot params (inherently stable in Kotlin — function types are stable). After the lambda-slot fix, `ScreenScaffold` should be skippable.
   - Recommendation: Verify with Compose compiler metrics after the lambda fix.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| `adb` (Android Debug Bridge) | gfxinfo capture, install release APK | ✓ (via `E:\Android\Sdk\platform-tools\adb.exe`) | Platform-tools (existing) | — |
| Python 3 | `parse_framestats.py` | ✓ (WSL python 3.13) | 3.13 | — |
| `parse_framestats.py` | gfxinfo analysis | ✓ (exists at `tools/gfxinfo-parser/`) | Existing | — |
| `E:\Android\gw.bat` | Release build | ✓ (documented in CLAUDE.md) | Existing | — |
| `sign-release.bat` | Install release APK on flox | ✓ (documented in CLAUDE.md) | Existing | — |
| flox device (Nexus 7 2013) | On-device UAT and measurement | ✓ (documented as USB-connected) | LineageOS 18.1 / API 30 | — |
| `kotlinx-collections-immutable` | ImmutableList/ImmutableMap | NOT YET IN CATALOG | — | Must add to `libs.versions.toml` (Wave 1 task) |

**Missing dependencies with no fallback:** `kotlinx-collections-immutable` must be added to the version catalog and `app/build.gradle.kts` before any `ImmutableList`/`ImmutableMap` code compiles. This is a Wave 1 / Wave 0 setup task.

---

## Validation Architecture

> `workflow.nyquist_validation` not explicitly set to false — included.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 (host unit tests) |
| Config file | None required — existing host test infrastructure |
| Quick run command | `cmd /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.** --no-daemon"` |
| Full suite command | `cmd /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon"` |

### Validation Map by Fix Type

| Fix | Test Type | Automated Command | Verification |
|-----|-----------|-------------------|-------------|
| `@Immutable` on `PrinterState` + nested types | Build (type-check) | `assembleRelease` — compile errors at call sites confirm field changes | No unit test — annotation is structural |
| `ImmutableList`/`ImmutableMap` field migration | Build + existing reducer tests | `testReleaseUnitTest` — existing `PrinterStateStore` tests exercise the reducer | Reducer tests verify emission type at compile time |
| `PrintStatusScreen` split | `@Preview` matrix | Manual `@Preview` inspection in Android Studio; `assembleDebug` for build gate | 6-combo matrix (existing `PrintStatusPreviews`) must render without regression |
| `AppShell` flow push-down | Build + existing shell tests | `testReleaseUnitTest` (ShellPresenceTest if applicable) | Also on-device: all screens must still navigate correctly |
| `GraphViewHost` update-block guard | Manual gfxinfo | Release-mode gfxinfo capture before/after; parse with `parse_framestats.py` | Delta in graph-redraw rate (should drop from 4 Hz to data-driven only) |
| `WebcamViewHost` update-block guard | Manual gfxinfo + on-device visual | gfxinfo capture; owner verifies theme-change still recolors chrome | Eye-check: dark→light toggle must still visibly change webcam overlay colors |
| `GraphView` fill opacity reduction | Manual gfxinfo + owner side-by-side | gfxinfo before/after; `checkpoint:human-verify` for visual change | D-10: owner must approve any blend/opacity change on flox side-by-side |
| `SpoolGlyph` cached Brush | Host unit test (SpoolGlyphTest exists) | `testReleaseUnitTest --tests works.mees.dinghy.designsystem.icons.SpoolGlyphTest` | Existing test verifies `spiralRenderFor` logic; extend to verify Brush is same instance when render is unchanged |
| `BoxWithConstraints` cell removal | `@Preview` matrix + on-device | `assembleDebug` + on-device layout check | Icon cells must still size correctly at the `@Preview` level |
| `LauncherGrid` → `LazyVerticalGrid` | `@Preview` matrix | Existing standby-state previews must still render correctly | |
| `spoolSwatches` `ImmutableList` + `remember` | Build (type-check at param sites) | `assembleRelease` | Param type change to `ImmutableList<Color>` cascades through `SpoolGlyph` call sites |
| `ImageRequest.Builder` `remember` | Code review | No test — the correctness is "Coil doesn't restart a load for a structurally-equal URL" | Verified by no thumbnail flicker on PrintStatus recompositions |
| `WebcamView.measureText` caching | Code review (low risk) | No test — text changes infrequently | |
| gfxinfo baseline + post-fix capture | Manual measurement (SC1) | Manual — `parse_framestats.py` run | SC1 closes when before/after shows zero frozen frames + responsiveness maintained |
| On-device owner eyeball (SC2/SC3) | Owner UAT on flox | `checkpoint:human-verify` | Visual unchanged, no regressions |

### Sampling Rate

- **Per task commit:** `cmd /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon"` — full host unit test suite (fast; no device needed)
- **Per wave merge:** Full suite + `assembleRelease` (confirms R8 doesn't break any annotation-based code)
- **Phase gate (SC1):** gfxinfo before/after on flox in release mode; owner approves no frozen frames + responsiveness

### Wave 0 Gaps

- [ ] gfxinfo baseline capture — before any code change; execute the D-06 sweep set on flox in release mode
- [ ] `di/AppContainer.kt` — read to determine if `@Stable` annotation is needed
- [ ] Add `kotlinx-collections-immutable = "0.3.8"` to `libs.versions.toml` and `app/build.gradle.kts` (required before Wave 1 code compiles)
- [ ] Enable Compose Compiler metrics output (optional but recommended for before/after recomposition count verification):
  ```kotlin
  // In app/build.gradle.kts tasks block:
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
      compilerOptions.freeCompilerArgs.addAll(
          "-P", "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${project.buildDir.absolutePath}/compose_metrics",
          "-P", "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${project.buildDir.absolutePath}/compose_reports",
      )
  }
  ```

---

## Security Domain

This phase makes no changes to authentication, network communication, persistence encryption, or input validation. The only new dependency (`kotlinx-collections-immutable`) has no network, crypto, or I/O surface. No ASVS categories are implicated by a Compose stability refactor.

---

## Sources

### Primary (HIGH confidence)
- [VERIFIED: code inspection] `app/src/main/java/works/mees/dinghy/state/PrinterState.kt` — confirmed all nested types are `val`-only `data class`; no existing `@Immutable` annotations; `Map<>` and `List<>` fields identified
- [VERIFIED: code inspection] `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` — confirmed 28+ `collectAsStateWithLifecycle` calls in one scope
- [VERIFIED: code inspection] `app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt` — confirmed unconditional `view.applyTokens(tokens)` in update block
- [VERIFIED: code inspection] `app/src/main/java/works/mees/dinghy/render/WebcamViewHost.kt` — confirmed unconditional `view.applyTokens(tokens)` in update block
- [VERIFIED: code inspection] `app/src/main/java/works/mees/dinghy/designsystem/icons/SpoolGlyph.kt` — confirmed `Brush.linearGradient(...)` inside Canvas without `remember`
- [VERIFIED: code inspection] `app/src/main/java/works/mees/dinghy/designsystem/ColorWheel.kt:184` — confirmed `rememberHueSweep()` as the cached-Brush template
- [VERIFIED: code inspection] `app/src/main/java/works/mees/dinghy/theme/ThemeTokens.kt:28` — confirmed `@Immutable` already applied; `ThemeTokens` is a `data class`
- [VERIFIED: code inspection] `tools/gfxinfo-parser/parse_framestats.py` — gfxinfo parser exists; header confirms API 23 support and ring-buffer dedup
- [VERIFIED: code inspection] `gradle/libs.versions.toml` — confirmed `kotlinx-collections-immutable` is NOT currently in the version catalog
- [CITED: .planning/codebase/CONCERNS.md] — static profiling audit, P0–P3 ranking
- [CITED: .planning/codebase/ARCHITECTURE.md] — flow ownership map, AppShell role
- [CITED: .planning/phases/22-performance-architecture-refactor/22-CONTEXT.md] — locked decisions D-01 through D-12

### Secondary (HIGH confidence)
- [CITED: developer.android.com/develop/ui/compose/performance/stability] — `@Immutable` vs `@Stable` contracts; collection stability; annotation pitfalls
- [CITED: developer.android.com/develop/ui/compose/performance/stability/fix] — `ImmutableList` / `ImmutableList` pattern with kotlinx-collections-immutable
- [CITED: developer.android.com/develop/ui/compose/performance/stability/strongskipping] — strong skipping default-on in Kotlin 2.0.20+; lambda memoization; `@Stable` still needed for object equality
- [CITED: docs/adr/0001-ui-toolkit-decision.md + Addendum 2] — reframed perf gate (zero frozen frames + responsiveness); gfxinfo as system of record

### Tertiary (MEDIUM confidence)
- [CITED: github.com/Kotlin/kotlinx.collections.immutable/releases] — 0.3.8 stable (2023-09-05, Kotlin ≥1.9.21); 0.4.0 stable (2024-05-14); 0.5.0-beta01 requires Kotlin 2.3.0 (incompatible)
- [CITED: medium.com/androiddevelopers/consuming-flows-safely-in-jetpack-compose] — `collectAsStateWithLifecycle` in lower-level composables; push-down pattern

---

## Metadata

**Confidence breakdown:**
- Standard stack (`kotlinx-collections-immutable` version pin): HIGH — version compatibility confirmed from release notes
- Recomposition discipline (`@Immutable` + ImmutableList mechanics): HIGH — verified against official Android docs
- AppShell push-down mechanics: HIGH — verified by code inspection + Compose lifecycle docs
- AndroidView guard pattern: HIGH — verified by code inspection (ThemeTokens is `data class`; equality works)
- GraphView overdraw options: MEDIUM — A/B tradeoff analysis is sound but exact improvement requires measurement; `[ASSUMED]` tagged
- gfxinfo protocol: HIGH — parser exists and was validated on this setup in prior phases

**Research date:** 2026-06-08
**Valid until:** 2026-09-08 (Compose BOM / kotlinx-collections-immutable are stable; valid for 90 days unless either library has a major release)

---

## RESEARCH COMPLETE

**Phase:** 22 - Performance & Architecture Refactor
**Confidence:** HIGH

### Key Findings

1. **`kotlinx-collections-immutable` is NOT in `libs.versions.toml`** — must be added as Wave 0 setup. Pin `0.3.8` (Kotlin ≥1.9.21 compatible); explicitly exclude `0.5.0-beta01` (requires Kotlin 2.3.0, incompatible with this project's 2.1.21 pin).

2. **Strong skipping mode is already active** (Kotlin 2.1.21 ≥ 2.0.20 threshold). This partially mitigates the lambda-slot issue but does NOT replace `@Immutable` + `ImmutableList` for collection-bearing types — strong skipping uses instance equality, which fails for new `List` instances with identical content.

3. **All AndroidView update-block guards are straightforward** — `ThemeTokens` is already `@Immutable data class`, so `tokens == lastTokens` uses structural equality correctly. No risk of a guard silently blocking a legitimate theme change.

4. **No new glow overdraw exists to fix** (D-08 confirmed by code inspection) — `OutlinedControl` draws a 2px border only; `*Glow` tokens are defined but read by zero draw code. All SC3 effort targets `GraphView` fill-rate and stacked alpha-dim layers.

5. **The gfxinfo parser and measurement protocol are fully established** — `tools/gfxinfo-parser/parse_framestats.py` exists, was validated on this device, and is the system of record per ADR-0001.

6. **`PrintStatusScreen` already receives `container` and does its own `collectAsStateWithLifecycle`** — the push-down for this screen is primarily about removing duplicate collection in AppShell, not rewiring the screen's architecture.

### File Created
`.planning/phases/22-performance-architecture-refactor/22-RESEARCH.md`

### Confidence Assessment
| Area | Level | Reason |
|------|-------|--------|
| Standard Stack (kotlinx-collections-immutable pin) | HIGH | Release notes verified; compatibility matrix confirmed |
| Recomposition discipline (@Immutable / ImmutableList mechanics) | HIGH | Official Android docs + code inspection |
| AppShell push-down | HIGH | Code inspection confirms current structure + Compose lifecycle docs |
| AndroidView guard pattern | HIGH | Code inspection confirms ThemeTokens data class + Compose interop docs |
| GraphView overdraw options | MEDIUM | Sound analysis; exact improvement requires measurement |
| gfxinfo protocol | HIGH | Parser exists + validated in prior phases |

### Open Questions
- Is `AppContainer` annotated `@Stable`? (Read `di/AppContainer.kt` in Wave 0)
- Which AppShell collections feed both drawer tiles AND screen-level concerns? (Requires column-by-column audit in Wave 0 planning)

### Ready for Planning
Research complete. Planner can now create PLAN.md files.
