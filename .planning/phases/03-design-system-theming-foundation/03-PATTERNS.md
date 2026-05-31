# Phase 3: Design System & Theming Foundation - Pattern Map

**Mapped:** 2026-05-31
**Files analyzed:** ~22 new files (inferred from CONTEXT.md "Delivers" + Claude's-Discretion + RESEARCH.md "Recommended Project Structure")
**Analogs found:** 18 / 22 mapped to a concrete in-repo structural/infrastructural analog (4 are genuinely greenfield with no analog)

> **Read this first.** This is a **greenfield UI layer** — CONTEXT.md confirms there is NO existing
> UI/theme/designsystem code and no `res/font/`. The value here is **NOT** finding UI analogs (there are
> none) but mapping each NEW file to the closest **STRUCTURAL / INFRASTRUCTURAL** analog already in the
> repo, so the planner reuses settled conventions (package layout, StateFlow exposure, plain-Kotlin
> toolkit-agnostic holders, the version-catalog discipline, the bench/synthetic-feed perf harness, the
> verifyMinSdk floor guard) instead of inventing new ones. Excerpts below are load-bearing — copy the
> *shape*, not the domain.

---

## File Classification

| New File (from RESEARCH structure) | Role | Data Flow | Closest Analog | Match Quality |
|------------------------------------|------|-----------|----------------|---------------|
| `theme/ThemeTokens.kt` | model (value type) | transform / immutable snapshot | `state/PrinterState.kt` | exact (plain-Kotlin toolkit-agnostic holder) |
| `theme/BakedTokens.kt` | config (data table) | transform (build-time bake → literals) | `config/DevConfig.kt` | role-match (checked-in constants) |
| `theme/ThemeResolver.kt` | store / service | event-driven → `StateFlow` | `state/PrinterStateStore.kt` | exact (StateFlow exposure + combine seam) |
| `theme/ThemePrefs.kt` (DataStore) | persistence | request-response (read/write prefs) | `config/DevConfig.kt` (the *thing it replaces*) | role-match (the config source; new lib) |
| `theme/compose/LocalTokens.kt` | provider | request-response (CompositionLocal read) | *(none — greenfield Compose)* | no analog |
| `theme/compose/DinghyTheme.kt` | provider / composable | event-driven (collect flow at boundary) | `MainActivity.kt` `setContent`+`MaterialTheme` host | partial (Compose host idiom only) |
| `theme/views/ThemeableView.kt` | interface (Views adapter) | event-driven (push-tokens + `invalidate()`) | `bench/ViewsBenchScene.kt` `TempGraphView` | role-match (custom-View setter+invalidate) |
| `designsystem/layout/ScreenScaffold.kt` | component (layout) | request-response (slot layout) | *(none — greenfield Compose)* | no analog |
| `designsystem/control/OutlinedControl.kt` | component | request-response | *(none — greenfield Compose)* | no analog |
| `designsystem/ConfirmGuard.kt` | component | request-response | *(none — greenfield Compose)* | no analog |
| `designsystem/ScrubberPage.kt` | component | request-response | *(none — greenfield Compose)* | no analog |
| `designsystem/SeverityToast.kt` | component | request-response | *(none — greenfield Compose)* | no analog |
| `render/RingBuffer.kt` | model (bounded holder) | streaming (fed by throttled StateFlow) | `bench/BenchActivity.kt` `ArrayDeque` graph window | exact (bounded rolling window idiom) |
| `render/ProgressRing.kt` | component (Compose Canvas) | streaming (redraw at cadence) | RESEARCH Code Example (no in-repo Compose-Canvas yet) | no analog |
| `render/GraphView.kt` | component (Views Canvas) | streaming (push + `invalidate()`) | `bench/ViewsBenchScene.kt` `TempGraphView` | exact (custom-View Canvas, reused Paint) |
| `theme/Geist.kt` (FontFamily) + `res/font/*.ttf` | config / asset | file-I/O (bundled resource) | `res/xml/network_security_config.xml` (res-bundling convention) | partial (resource layout only) |
| `gallery/GalleryScreen.kt` | component (harness) | event-driven (drives feed → primitives) | `bench/ComposeBenchScene.kt` / `BenchActivity` | role-match (preview/measurement harness) |
| `src/debug/.../GalleryActivity.kt` | activity (launcher) | event-driven | `bench/BenchActivity.kt` + `MainActivity.kt` | exact (ComponentActivity host + manifest reg) |
| `src/debug/AndroidManifest.xml` | config (manifest) | — | `app/src/main/AndroidManifest.xml` | exact (LAUNCHER activity registration) |
| `gallery/SyntheticFeed` reuse for D-10 | test fixture | streaming (deterministic feed) | `bench/SyntheticFeed.kt` | exact (REUSE, do not re-author) |
| `macrobenchmark/.../*Benchmark.kt` ring+graph scene | test (on-device perf) | streaming → gfxinfo | `macrobenchmark/.../ToolkitBenchmark.kt` | exact (extend, same shape) |
| catalog/build wiring for DataStore | config (build) | — | `gradle/libs.versions.toml` + `app/build.gradle.kts` | exact (catalog-entry convention) |
| `app/src/test/.../theme|render/*Test.kt` | test (unit) | — | `app/src/test/.../state/ConflationTest.kt` | exact (runTest/virtual-time idiom) |

---

## Pattern Assignments

### `theme/ThemeTokens.kt` (model, transform) — and `render/RingBuffer.kt` (model, streaming)

**Analog:** `app/src/main/java/works/mees/dinghy/state/PrinterState.kt`

This is THE structural template for both new toolkit-agnostic value types (D-05 `ThemeTokens`, D-12
ring buffer). It is a **plain Kotlin `data class` with NO Compose stability annotations** — the exact
posture D-05/D-12 demand ("survives recreation, consumed from BOTH toolkits"). Copy this discipline; the
*only* deviation RESEARCH asks for is adding `@Immutable` to `ThemeTokens` (Pattern 3), which lives at
the Compose boundary, not in the headless holder.

**Package + doc-comment convention to mirror** (`PrinterState.kt` lines 1-19):
```kotlin
package works.mees.dinghy.state   // new files: works.mees.dinghy.theme / .render

/**
 * Toolkit-agnostic, immutable single-source-of-truth ... the public contract ... that BOTH Compose
 * and classic Views later consume (ADR 0001 hybrid). This is the HEADLESS spine: deliberately a
 * PLAIN Kotlin `data class` with NO Compose stability annotations — the Compose-stability wrapper,
 * if any, lives in the UI phase, not here.
 */
data class PrinterState( /* … */ )
```

**Default-valued immutable fields idiom** (lines 20-56) — `ThemeTokens`'s ~30 role colors + `rCard`/
`rCtrl`/`fs` should follow this exact style (typed, defaulted, KDoc per field tying back to a token name).

---

### `theme/ThemeResolver.kt` (store/service, event-driven → StateFlow)

**Analog:** `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt` — **the load-bearing analog
for D-05.** This is the Phase-2 spine the resolver must mirror: a class that takes a `CoroutineScope`,
holds a `MutableStateFlow`, exposes a read-only `.asStateFlow()`, and (here) conflates at ~4 Hz. The
resolver does the same shape minus conflation: combine (base, deltas, fs) → `StateFlow<ThemeTokens>`.

**StateFlow exposure pattern to copy exactly** (`PrinterStateStore.kt` lines 39-55):
```kotlin
class PrinterStateStore(
    scope: CoroutineScope,
    private val sampleMillis: Long = DEFAULT_SAMPLE_MS,
) {
    @Volatile
    private var accumulator: PrinterState = PrinterState()

    private val _printerState = MutableStateFlow(accumulator)
    /** The public single-source-of-truth state ... */
    val printerState: StateFlow<PrinterState> = _printerState.asStateFlow()
    // …_capabilities likewise…
}
```
The resolver: `private val _tokens = MutableStateFlow(resolve(...))` / `val tokens: StateFlow<ThemeTokens> = _tokens.asStateFlow()`; a `setBase()/setDeltas()/setFs()` recomputes `_tokens.value = resolve(...)`. This is the `setConnectionState()`/`seed()` mutator idiom (lines 78-112) applied to theme inputs.

**The ~4 Hz conflation half is ALREADY DONE** (lines 61-71, 145-149): `DEFAULT_SAMPLE_MS = 250L`. Phase 3
adds the *render* half (ring + graph) that CONSUMES `printerState`; the render primitive does NOT
re-implement throttling — it reads the already-conflated flow (D-14). Cross-reference: `companion object { const val DEFAULT_SAMPLE_MS = 250L }`.

---

### `theme/views/ThemeableView.kt` + `render/GraphView.kt` (Views Canvas, streaming push)

**Analog:** `app/src/main/java/works/mees/dinghy/bench/ViewsBenchScene.kt` — the inner `TempGraphView`
(lines 182-218) is a **direct prototype** of the D-11 line graph. It already demonstrates everything
D-06/D-11/Pitfall-4 prescribe, EXCEPT it hardcodes colors (the new version reads pushed tokens).

**Custom-View setter + `invalidate()` push pattern** (`ViewsBenchScene.kt` lines 182-195) — this IS the
D-06 "push-tokens + invalidate" mechanic; generalize `setHistory` into `applyTokens(t)` + `setData(...)`:
```kotlin
private class TempGraphView(context: Context) : View(context) {
    private var history: List<GraphSample> = emptyList()
    private val extruderPaint = Paint().apply {           // pre-allocated Paint (Pitfall 4)
        color = Color.parseColor("#FFFF7043"); strokeWidth = 2f; isAntiAlias = true
    }
    fun setHistory(next: List<GraphSample>) {             // → split into applyTokens()+setData()
        history = next
        invalidate()                                       // repaint trigger (D-06)
    }
    override fun onDraw(canvas: Canvas) { /* draws straight from history, no per-frame alloc */ }
}
```
**Required deltas for the new `GraphView`** (per RESEARCH Pitfall 4 + the Code Example): paints become
`applyTokens(t: ThemeTokens) { linePaint.color = t.accent.toArgb(); invalidate() }`; reuse ONE `Path`
(`line.rewind()` not new Path); cap points to pixel width. `ThemeableView` is a tiny interface
(`fun applyTokens(t: ThemeTokens)`) implemented by `GraphView`. The `dp()` helper (line 107:
`(v * resources.displayMetrics.density).toInt()`) is the established density-scaling idiom — reuse it.

**AndroidView host (D-06) collects the SAME flow** — the host composable does
`resolver.tokens.collectAsStateWithLifecycle()` and in `update = { view.applyTokens(currentTokens); view.setData(snapshot) }`. No in-repo `AndroidView` analog exists yet (greenfield interop); follow RESEARCH Pattern 3's snippet.

---

### `render/RingBuffer.kt` (bounded holder) — feeding pattern

**Analog:** `app/src/main/java/works/mees/dinghy/bench/BenchActivity.kt` (lines 76-90) — the bounded
rolling-window idiom the ring buffer formalizes. The bench drives it inline with `ArrayDeque`:
```kotlin
feedJob = lifecycleScope.launch {
    val graph = ArrayDeque<GraphSample>()
    feed.events().collect { event ->
        graph.addLast(event.graphSample)
        while (graph.size > GRAPH_MAX) graph.removeFirst()   // bounded window (→ RingBuffer.kt, D-12)
        view.render(event, graph.toList(), console.toList())
    }
}
// companion: private const val GRAPH_MAX = 120
```
D-12 promotes this into a **plain-Kotlin, unit-testable `RingBuffer`** with a stable snapshot
(`toFloatArray()`), fed by the throttled `StateFlow`. Same bounded-window semantics, now a testable holder
that survives rotation/theme swap. The `GRAPH_MAX = 120` window cap is the precedent value.

---

### `gallery/GalleryScreen.kt` + `src/debug/.../GalleryActivity.kt` (harness + launcher)

**Analog:** `app/src/main/java/works/mees/dinghy/bench/BenchActivity.kt` (whole file) and
`MainActivity.kt` (the Compose-host idiom). The gallery is morally the **Phase-3 equivalent of
BenchActivity** — a `ComponentActivity` that mounts a scene and drives it from `SyntheticFeed` for an
on-device perf proof, PLUS a live wire to the Phase-2 spine (D-14).

**ComponentActivity + Compose host** (`MainActivity.kt` lines 23-34) — the minimal release-launcher shape
the gallery's debug launcher copies (swap `Placeholder()` for `GalleryScreen()`):
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { Placeholder() } } }
    }
}
```

**Feed-driven scene wiring** (`BenchActivity.kt` lines 57-91) — the gallery's perf scene reuses this
exact `SyntheticFeed().events().collect { … }` + bounded-deque driving loop. For D-14, ALSO collect
`printerStateStore.printerState` for live temps (the gallery "constructs whatever minimal wiring it
needs" — CONTEXT integration note; there is no Application/DI yet).

**Keep-screen-on + full-res for the perf proof** (`BenchActivity.kt` lines 43-47) — reuse for D-10:
```kotlin
window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
```

---

### `src/debug/AndroidManifest.xml` (debug-only LAUNCHER, D-08)

**Analog:** `app/src/main/AndroidManifest.xml` (lines 28-47). The main manifest already shows BOTH the
LAUNCHER pattern (MainActivity, lines 29-37) and an `exported` non-launcher activity (BenchActivity,
lines 44-46). The D-08 gallery launcher is the LAUNCHER block, placed in `src/debug/` so it merges only
into the debug variant (RESEARCH Pitfall 5 — do NOT runtime-gate with `BuildConfig.DEBUG`):
```xml
<activity android:name=".MainActivity" android:exported="true" android:label="Dinghy Display">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```
Note the shared-manifest **single-owner rule** (main manifest header, lines 2-6): the main file is owned
by an earlier plan and must not be edited for the cleartext posture; the debug source set is the clean
seam for adding the gallery launcher without touching it.

---

### `theme/Geist.kt` (FontFamily) + `res/font/*.ttf` (bundled assets)

**Analog (resource-bundling convention only):** `app/src/main/res/` currently holds just `values/themes.xml`
and `xml/network_security_config.xml` — there is **NO `res/font/` yet** (this phase creates it). The only
in-repo precedent is the `res/<type>/<file>` layout and the `@xml/...` / `@style/...` reference idiom
(manifest lines 24-26: `android:networkSecurityConfig="@xml/network_security_config"`,
`android:theme="@style/Theme.DinghyDisplay"`). Mirror that: drop static TTFs in `app/src/main/res/font/`,
reference via `R.font.geist_*` (RESEARCH Pattern 2). Static weights only (API-23 floor; variable fonts are
API 26+ — Pitfall 2). The `themes.xml` `@style/Theme.DinghyDisplay` is the AppCompat/Material XML theme the
Activity declares — leave it as the host theme; the token system is a Compose/Views runtime layer on top,
not an XML-theme replacement.

---

## Shared Patterns

### Adding a new dependency (DataStore) — version-catalog discipline

**Source:** `gradle/libs.versions.toml` + `app/build.gradle.kts`
**Apply to:** the one new dep this phase adds (`androidx.datastore:datastore-preferences`). NEVER inline a
version — the whole repo risk model is "a lib silently raises minSdk above 23," so every version is pinned
in the catalog and the merged-manifest floor is asserted by `verifyMinSdk`.

**Catalog entry convention** (`libs.versions.toml` — `[versions]` line 32 area + `[libraries]` block lines
54-95). Add under a new comment banner, matching the existing grouping/comment style:
```toml
# [versions]
datastore = "1.1.7"   # minSdk-23-safe (AndroidX June-2025 floor); confirm exact via gw.bat + verifyMinSdk
# [libraries]
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

**Module wiring convention** (`app/build.gradle.kts` lines 95-146 — `libs.<alias>` references, grouped by
comment banner):
```kotlin
// --- Persistence (THEME-02/D-02): S/M/L + theme base + custom token deltas ---
implementation(libs.androidx.datastore.preferences)
```

**MANDATORY floor proof** (`build-logic/src/main/kotlin/verify-min-sdk.gradle.kts`, applied via
`app/build.gradle.kts` line 11 `id("verify-min-sdk")`): after adding DataStore, run `verifyMinSdk` — it
parses `SingleArtifact.MERGED_MANIFEST` and FAILS if the merged `minSdkVersion != 23` (Pitfall 6). The
plugin is the *real* control; the catalog pin alone cannot stop a transitive bump. Run command (CLAUDE.md
Local Build Environment — `./gradlew` does NOT work from WSL):
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:verifyMinSdk --no-daemon"
```

### Persistence seam this phase upgrades

**Source:** `app/src/main/java/works/mees/dinghy/config/DevConfig.kt` (lines 13-15 explicitly say so:
*"the Phase-2 stand-in for the user-facing connection screen, which lands in Phase 3 with UI + DataStore"*).
`ThemePrefs.kt` is the FIRST DataStore in the repo — there is no DataStore analog to copy; follow RESEARCH
Standard Stack + the catalog discipline above. `DevConfig` is the *config-source* role analog (a singleton
exposing typed config), not the persistence mechanism.

### The deterministic synthetic feed — REUSE, do not re-author (D-10/D-14)

**Source:** `app/src/main/java/works/mees/dinghy/bench/SyntheticFeed.kt`
**Apply to:** the gallery perf scene (D-10) and the macrobenchmark ring+graph scene. This 2–4 Hz
deterministic feed (`DEFAULT_PERIOD_MS = 333` ≈ 3 Hz, lines 130-132) with byte-identical replay
(`assertDeterministic()`, lines 144-153) is the SAME fixture RESEARCH says to reuse (Wave-0 gap:
*"reuse `SyntheticFeed`/`BenchActivity`/`parse_framestats.py`"*). Its `GraphSample(extruder, bed)` pairs
(lines 185-189) feed the ring buffer directly. Do not write a second feed.

### On-device perf proof (criterion #5) — extend the existing macrobenchmark

**Source:** `macrobenchmark/src/main/java/works/mees/dinghy/macrobenchmark/ToolkitBenchmark.kt` +
`macrobenchmark/build.gradle.kts` + `tools/gfxinfo-parser/parse_framestats.py`
**Apply to:** the D-10 ring/graph perf scene. The methodology is settled and **gfxinfo framestats is the
system of record** (ToolkitBenchmark header lines 21-29; parser docstring lines 5-30 — *"FrameTimingMetric
is corroboration only … the authoritative percentiles come from THIS parser"*). Add a new scene/`@Test`
that COLD-launches the gallery (or a bench-style scene) driven by `SyntheticFeed`, captures
`dumpsys gfxinfo <pkg> framestats`, and runs it through `parse_framestats.py` for p50/p90/p95 +
count(frames > 700 ms). Reuse the `measureScene(...)`/`driveScene()` shape (lines 52-93) and the
`benchmark`/`release` build-type + `matchingFallbacks += "release"` wiring (`macrobenchmark/build.gradle.kts`
lines 38-56). Floors to clear (ADR 0001 measured): Views graph p95 ≈ 41.9 ms, zero frames > 700 ms.

### Unit-test idiom (Wave-0 gaps: BakedTokens, ThemeResolver, FontScale, RingBuffer tests)

**Source:** `app/src/test/java/works/mees/dinghy/state/ConflationTest.kt`
**Apply to:** every new `app/src/test/.../theme|render/*Test.kt`. The repo's JVM-unit-test convention:
JUnit 4 + `kotlinx-coroutines-test` virtual time (`runTest`, `backgroundScope`, `advanceTimeBy`,
`UnconfinedTestDispatcher(testScheduler)` — lines 31-53). Test deps are already in `app/build.gradle.kts`
(lines 143-145: `testImplementation(libs.junit)` / `libs.kotlinx.coroutines.test`) — **no new test
framework needed** (RESEARCH confirms). For `ThemeResolverTest` (empty-delta-===-base, single-delta-
overrides-one-token, Pitfall 7) and `RingBufferTest` (wraparound/snapshot, D-12), mirror this file's
collect-into-list + assert-on-`.value` structure (lines 37-52):
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class ConflationTest {
    @Test fun highRateTempBurst_isConflated_latestWins() = runTest {
        val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
        // … drive inputs, advanceTimeBy(…), assertEquals(expected, store.printerState.value…) …
    }
}
```
Run: `cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon"`.

---

## No Analog Found

These are genuinely greenfield — no in-repo analog exists. Planner should use RESEARCH.md patterns/code
examples and `docs/ui_design/` (hifi.css) as the source of truth:

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `theme/compose/LocalTokens.kt` | provider | request-response | First `staticCompositionLocalOf` in the repo — no Compose token plumbing exists yet (RESEARCH Pattern 3, Pitfall 3) |
| `theme/compose/DinghyTheme.kt` | provider | event-driven | First `LocalDensity(fontScale=1f)` override + flow-collection theme boundary (RESEARCH Pattern 3 & 5). Only the bare `setContent`/`MaterialTheme` host (MainActivity) is a partial precedent |
| `designsystem/layout/ScreenScaffold.kt` | layout | request-response | First Focus/Field/Gutter primitive — no Compose layout code exists. Drive from `docs/ui_design/LAYOUT.md` + RESEARCH Pattern 1 |
| `designsystem/{OutlinedControl,ConfirmGuard,ScrubberPage,SeverityToast}.kt` | component | request-response | No Compose components exist yet — drive from `hifi.css` `.ctl`/`.alert`/`.setval`/severity vocab + RESEARCH Code Examples |
| `render/ProgressRing.kt` | component | streaming | First Compose `Canvas` in the repo (the bench graph is a Compose-Canvas head-to-head but lives in `bench/ComposeBenchScene.kt`, not reused as a primitive). Use RESEARCH ring Code Example |
| `theme/BakedTokens.kt` bake script | tooling | transform | First oklch→sRGB bake — no precedent. RESEARCH Pattern 4 (CSS Color 4 pipeline); commit script + output for traceability |

---

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/{state,bench,config,auth}`, `app/src/main/res`,
`app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`,
`build-logic/src/main/kotlin`, `macrobenchmark/`, `tools/gfxinfo-parser/`, `app/src/test`.
**Files scanned:** ~16 source/config files read in full or part.
**Key takeaway for the planner:** the four load-bearing reuse anchors are (1) `PrinterStateStore` for the
`ThemeResolver` StateFlow shape, (2) `PrinterState` for the plain-Kotlin toolkit-agnostic value types
(`ThemeTokens`, `RingBuffer`), (3) the `bench/` + `macrobenchmark/` + `parse_framestats.py` harness for the
D-10 perf proof (reuse `SyntheticFeed` verbatim), and (4) the `libs.versions.toml` + `verify-min-sdk`
discipline for the single new DataStore dependency. Everything Compose-visual is greenfield and driven by
`docs/ui_design/` + RESEARCH code examples.
**Pattern extraction date:** 2026-05-31
