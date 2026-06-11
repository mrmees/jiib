# Codebase Concerns

**Status:** historical — findings resolved by Phases 22–26 (stamped 2026-06-11)

**Analysis Date:** 2026-06-08

> **Priority lens:** Adreno 320 / 2 GB / 1920×1200 / ARMv7 (Nexus 7 2013, "flox") is the perf floor.
> Owner is noticing navigation/interaction lag. All concerns are ranked by likely impact on that device.

---

## Performance Bottlenecks

### [CRITICAL — P0] `PrinterState` is an unstable Compose type emitted at 4 Hz

**Problem:** The core state object that drives the home screen is an unconstrained `data class` with
multiple `Map<>` and `List<>` fields. Compose's skip-optimization requires all composable parameters to
be `@Stable` or `@Immutable`. Because `PrinterState` is neither, Compose cannot skip ANY composable
that reads it — the entire home screen and shell re-execute every 250 ms.

**Files:**
- `app/src/main/java/works/mees/dinghy/state/PrinterState.kt` — missing `@Immutable` on `PrinterState` and all state value classes
- `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt` — 4 Hz (250 ms) emission on the sample plane

**Unstable fields in `PrinterState` (each blocks skip):**
- `heaters: Map<String, HeaterState>` — `Map` is not `@Stable`
- `temperatureSensors: Map<String, Double>` — same
- `outputs: Map<String, OutputLiveValue>` — same
- `toolheadPosition: List<Double>?` — `List` is not `@Stable`
- `gcodePosition: List<Double>?` — same
- `colorData: List<List<Double>>?` — doubly unstable
- `profileNames: List<String>` — same
- `screws: List<Screw>` — same

**Cause:** Wide recomposition — every composable observing printer state (PrintStatusScreen, AppShell,
all 28 `collectAsStateWithLifecycle` calls in those two files combined) re-executes 4 times per second.
On Adreno 320 fill-rate budget this crowds out the frame budget for touch-driven recompositions, causing
perceived input lag.

**Fix approach:**
1. Annotate `PrinterState` and every nested state value class with `@Immutable` (they are effectively
   immutable — replaced wholesale on each emission, never mutated in place).
2. Replace `Map<String, HeaterState>` with `@Immutable data class HeaterMap(val entries: ImmutableMap<String, HeaterState>)` or use `kotlinx.collections.immutable.ImmutableMap` directly.
3. Replace `List<*>` fields with `ImmutableList<*>` (`kotlinx-collections-immutable` 0.3.x — already in the
   stack rationale but check `libs.versions.toml` for actual inclusion).
4. Annotate all nested value types: `HeaterState`, `OutputLiveValue`, `Screw`, `FanState`, etc.

---

### [CRITICAL — P0] Lambda slots allocated in composition on the home screen

**Problem:** `PrintStatusScreen` builds two fat `@Composable` lambda values inside the composable body
and passes them as slots to `ScreenScaffold`. Because they are created inline (not `remember`ed and not
stable references), Compose treats them as unstable parameters — `ScreenScaffold` cannot skip even when
neither lambda body needs to change.

**Files:**
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt:490` — `val gutterContent: @Composable () -> Unit = { ... }`
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt:522` — `val activeFieldContent: @Composable () -> Unit = { ... }`
- `app/src/main/java/works/mees/dinghy/designsystem/layout/ScreenScaffold.kt:64` — receives these lambdas as slot params

**Cause:** Lambda allocations inside composable functions are new object instances every recomposition.
`ScreenScaffold` sees a different lambda reference each call → cannot skip → always re-lays-out the
entire scaffold including the inner `BoxWithConstraints` subcomposition.

**Fix approach:** Either (a) hoist the lambdas out to top-level `@Composable fun` and pass them as
named composable references, or (b) split `PrintStatusScreen` into a stateless composable that receives
already-resolved values as stable parameters. Option (b) is the larger architectural fix and the right
long-term move for the home screen.

---

### [HIGH — P1] `BoxWithConstraints` overuse — subcomposition multiplier on every recomposition

**Problem:** `BoxWithConstraints` forces a subcomposition pass (it measures its children in a separate
composition node). Used in the hot path, it multiplies the layout work per recomposition. In
`PrintStatusScreen` it appears in the stat grid cells, which are called 4–6 times per frame on the
home screen.

**Files:**
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt:640` — `PrintStatusFocus` root
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt:857` — `IconTwoRowCell` (called 4× per stat grid render)
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt:888` — `IconValueCell` (called 2× per stat grid render)
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt:1361` — `TerminalFocus`
- `app/src/main/java/works/mees/dinghy/designsystem/layout/ScreenScaffold.kt:64` — root scaffold (every screen)

**Cause:** `BoxWithConstraints` is used to detect portrait vs landscape and size icons proportionally.
At the cell level this is avoidable — the grid already knows the column width.

**Fix approach:**
- Replace `BoxWithConstraints` in `IconTwoRowCell` and `IconValueCell` with `BoxWithConstraints`-free
  sizing: pass explicit `Dp` or use `Modifier.fillMaxWidth()` / `aspectRatio()` — the cell's parent grid
  already constrains the width.
- For screen-level orientation detection in `ScreenScaffold`, the single root `BoxWithConstraints` is
  acceptable; guard against further nesting.
- The `PrintStatusFocus` use can likely be replaced with a `SubcomposeLayout` or plain `Layout` that
  reads `constraints.maxWidth` once.

---

### [HIGH — P1] `spoolSwatches: List<Color>` allocated on every recomposition in two hot paths

**Problem:** The spool color swatches list is re-computed inline on every recomposition in both
`PrintStatusScreen` and `AppShell`, producing new `List<Color>` instances that defeat Compose skip.

**Files:**
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt:187` — `val spoolSwatches: List<Color> = ...`  computed inline
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:315` — `val drawerSpoolSwatches: List<Color> = activeSpoolDetail?.filament?.colorSwatches.orEmpty().mapNotNull(::parseNormalizedHex)` — allocation on every recomposition
- `app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt` — receives `List<Color>` (unstable) propagated to every `DrawerTile`

**Cause:** Even without the `PrinterState` instability problem above, a new `List<Color>` reference on
every emission means every composable that receives it as a parameter is always dirty.

**Fix approach:** Wrap in `remember(activeSpoolDetail)` and return `ImmutableList<Color>`. Propagate
`ImmutableList<Color>` as the param type so Compose recognises stability.

---

### [HIGH — P1] `AndroidView` `update` block fires `invalidate()` on every recomposition unconditionally

**Problem:** Both `GraphViewHost` and `WebcamViewHost` call `view.applyTokens(tokens)` in their
`AndroidView` `update` block without a change-guard. `applyTokens` calls `invalidate()`, which schedules
a full `onDraw` pass on the Canvas View. The `update` block fires on every recomposition — so the temp
graph and webcam surface are forced to redraw on every 250 ms `PrinterState` emission even when the
theme tokens have not changed.

**Files:**
- `app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt:46` — `view.applyTokens(tokens)` unconditional in `update`
- `app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt:89-100` — multi-trace overload, same issue
- `app/src/main/java/works/mees/dinghy/render/WebcamViewHost.kt:61` — `view.applyTokens(tokens)` unconditional in `update`

**Fix approach:** Cache last-applied tokens in the `View` and short-circuit `applyTokens` if the new
value equals the cached one: `if (tokens == lastTokens) return; lastTokens = tokens; …; invalidate()`.

---

### [HIGH — P1] `GraphView.onDraw` — per-trace fill-rate pressure on Adreno 320

**Problem:** `GraphView.onDraw` fills each of up to 3 temperature traces individually with gradient
fills. Each fill is a full-screen-width area coverage operation. The existing comment at line 283
explicitly flags this: "Per-trace fill is extra Adreno-320 fill-rate vs the old single-trace fill."
On an Adreno 320, GPU fill-rate (pixels rendered per frame) is the primary bottleneck; blending
multiple transparent full-width fills in `onDraw` is the dominant frame-budget consumer for the graph.

**Files:**
- `app/src/main/java/works/mees/dinghy/render/GraphView.kt:283` — warning comment; fill loop begins
- `app/src/main/java/works/mees/dinghy/render/GraphView.kt:307-326` — per-trace fill loop
- `app/src/main/java/works/mees/dinghy/render/GraphView.kt:330-340` — setpoint path draw per trace

**Fix approach:**
- Reduce fill opacity for secondary traces (tool1, bed) to reduce blend cost, or limit gradient fills to
  the primary active heater only.
- Pre-rasterize static portions (setpoint lines, axis labels) into an off-screen `Bitmap` and blit it
  once; only re-render the live trace region.

---

### [MEDIUM — P2] Inline `.map {}` before `collectAsStateWithLifecycle` in `AppShell` creates new `Flow` on each recomposition

**Problem:** Two `collectAsStateWithLifecycle` calls in `AppShell` chain an inline `.map {}` on the
Flow directly in the composable body. Kotlin creates a new `Flow` wrapper object on every recomposition.
While `collectAsStateWithLifecycle` debounces collection starts, this pattern creates short-lived
garbage on every recomposition at 4 Hz.

**Files:**
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:224` — `container.activeProfile.map { it?.id }.collectAsStateWithLifecycle(...)`
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:227` — `container.activeProfile.map { it?.displayName() }.collectAsStateWithLifecycle(...)`

**Fix approach:** Hoist the mapped flows to `AppContainer` as named `StateFlow` properties (`stateIn(
scope, SharingStarted.Eagerly, null)`) so they are created once. The composable then `collectAsStateWithLifecycle` on a cold `StateFlow` reference.

---

### [MEDIUM — P2] `ImageRequest.Builder` allocated on every recomposition in `PrintStatusFocus`

**Problem:** `PrintStatusScreen` creates a new `ImageRequest.Builder(context).data(thumbnailUrl)...
.build()` in the composable body without `remember`. On every 4 Hz recomposition Coil receives a
structurally-equal but reference-different `ImageRequest` and must re-evaluate whether to start a load.

**Files:**
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt:680-690` (approx.) — `AsyncImage` with inline `ImageRequest.Builder`

**Fix approach:** Wrap the `ImageRequest` in `remember(thumbnailUrl) { ... }` so it is only rebuilt
when the URL changes.

---

### [MEDIUM — P2] `WebcamView.onDraw` calls `measureText()` inside draw methods

**Problem:** `Paint.measureText()` is not allocation-free but it is a method call that forces
layout-font metric lookups. Calling it inside `onDraw` helpers on every frame prevents the Canvas
from being fully allocation-free.

**Files:**
- `app/src/main/java/works/mees/dinghy/render/WebcamView.kt:316` — `drawCornerBadge`
- `app/src/main/java/works/mees/dinghy/render/WebcamView.kt:333` — `drawCenteredOverlay`
- `app/src/main/java/works/mees/dinghy/render/WebcamView.kt:360` — `drawDeadEndCard`
- `app/src/main/java/works/mees/dinghy/render/WebcamView.kt:385` — `drawCycleOverlay`

**Fix approach:** Cache the last text + measured width as a pair (`lastText`/`lastWidth`) in each draw
helper. Only remeasure when the text string changes (these overlay strings are infrequently updated).

---

### [MEDIUM — P2] `LauncherGrid` on home screen uses manual `chunked` + `forEach` instead of `LazyVerticalGrid`

**Problem:** `LauncherGrid` in `PrintStatusScreen` uses `chunked(columns).forEach { row -> Row { ... } }`
rather than `LazyVerticalGrid`. This eagerly composes ALL tiles regardless of whether they're visible.
For the current tile count this is not catastrophic, but it also means every recomposition evaluates
all tile state, and adding tiles later will compound the cost linearly.

**Files:**
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt:1073` — `LauncherGrid` composable

**Fix approach:** Replace with `LazyVerticalGrid(columns = GridCells.Fixed(columns))` with `key = { tile.id }` on each `item`. For the ~4-tile default count this is low priority but easy to fix correctly.

---

### [MEDIUM — P2] `fmtDuration` string allocations on every recomposition

**Problem:** `fmtDuration` is called on each recomposition of `PrintStatusScreen`'s print-progress
region. It creates `String` + `padStart` instances. While minor individually, this runs at 4 Hz
alongside all other composition work on an already-constrained system.

**Files:**
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt:1537-1543`

**Fix approach:** Wrap in `derivedStateOf { fmtDuration(durationSeconds) }` keyed on the integer
duration, or hoist duration formatting into `PrinterStateStore` so the formatted string is only
recomputed when the value changes.

---

### [MEDIUM — P2] `SpoolGlyph` allocates a new `Brush` on every draw when gradient mode is active

**Problem:** `SpoolGlyph` computes `Brush.linearGradient(...)` inside the `Canvas` draw block when the
spiral render is `SpiralRender.Gradient`. The `Brush` is not `remember`ed — a new instance is created
on every recomposition of any surface that shows the spool icon.

**Files:**
- `app/src/main/java/works/mees/dinghy/designsystem/icons/SpoolGlyph.kt:90` — `Brush.linearGradient(...)` inside Canvas composable body

**Context:** The `spiralPath` IS correctly `remember`ed at line 77 — the same pattern should apply to
the `Brush`. `ColorWheel.kt` demonstrates the correct pattern: the sweep gradient brush is pre-built
and cached (line 40 KDoc).

**Fix approach:**
```kotlin
val gradientBrush = remember(render) {
    if (render is SpiralRender.Gradient) Brush.linearGradient(
        0f to render.start, 1f to render.end,
        start = Offset(spiralBounds.left, spiralBounds.center.y),
        end = Offset(spiralBounds.right, spiralBounds.center.y),
    ) else null
}
```
Then `val brush = gradientBrush ?: SolidColor(...)`.

---

### [LOW — P3] `SimpleDateFormat` instantiated per-call in `FileRowsAdapter.onBindViewHolder`

**Problem:** A new `SimpleDateFormat("MMM d, HH:mm", Locale.US)` is constructed each time a file row
is bound. On fast scrolling through a large gcode library (RecyclerView recycles eagerly), this
allocates one formatter per visible row per scroll event — sustained GC pressure.

**Files:**
- `app/src/main/java/works/mees/dinghy/ui/files/FileRowsAdapter.kt:259` — `SimpleDateFormat(...)` in `onBindViewHolder`
- `app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt:296` — `SimpleDateFormat` for a one-shot save (low impact, acceptable)

**Fix approach:** Hoist to a `companion object val DATE_FORMAT = SimpleDateFormat("MMM d, HH:mm", Locale.US)`.
Note: `SimpleDateFormat` is not thread-safe, so if binding ever moves off the main thread, use
`ThreadLocal<SimpleDateFormat>` or migrate to `java.time.format.DateTimeFormatter` (API 26+) / a
compat shim.

---

### [LOW — P3] AppShell errorLines flow performs sequence operations on every Moonraker event

**Problem:** The `errorLines` state in `AppShell` is computed via an inline `.map { lines -> ... }`
on the flow that includes sequence transformations. This runs on every Moonraker notification
emission (which can be rapid during active printing).

**Files:**
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:421-429`

**Fix approach:** Move the transform into `AppContainer` as a named `StateFlow` with `stateIn(scope, SharingStarted.WhileSubscribed(5000), ...)`.

---

## Tech Debt

### Multiple separate DataStore files — 6 distinct on-disk stores

**Issue:** The app has 6 separate `PreferenceDataStore` files: `theme`, `connection`, `macros`, `webcam`,
`profiles`, `babystep`. Each is a separate I/O actor and a separate disk file. For now this is
functional, but it means 6 separate proto-buf write cycles on any settings change.

**Files:**
- `app/src/main/java/works/mees/dinghy/DinghyApp.kt:42-84` — instantiates all 6 stores

**Impact:** Cold-start time — DataStore opens all stores lazily, but app startup subscribes to several
of them eagerly (theme, connection). On slow flash (common on old tablets), 6 separate MMap operations
add measurable cold-start latency.

**Fix approach:** No immediate action required. If cold-start latency is measured as a problem, merge
logically-related prefs (e.g., `theme` + `babystep`) into fewer files. Proto DataStore with a single
schema would be the clean long-term direction.

---

### `PrintStatusScreen.kt` is 1584 lines — single-file god component

**Issue:** The home screen is a single 1584-line file containing: the primary screen composable,
focus/field/gutter slot builders, all stat-grid cells, the LauncherGrid, duration formatters, and
preview functions. This is the highest-traffic file in the codebase and the hardest to profile
incrementally.

**Files:**
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt` (1584 lines)

**Impact:** Correctness risk — any edit touches a large blast radius. Perf risk — all composables share
one compilation unit and one large recomposition scope by default.

**Fix approach:** Split at natural seams: `PrintStatusFocus.kt`, `PrintStatusField.kt`,
`PrintStatusGutter.kt`, `PrintStatusPreviews.kt`. Each slice becomes independently restartable by
Compose.

---

### `AppShell.kt` hosts 28 `collectAsStateWithLifecycle` calls in one composable scope

**Issue:** `AppShell` collects 28 flows at line 200–450 (approx) before rendering the screen graph.
All 28 subscriptions share the same composition scope and re-evaluate together on any parent state
change.

**Files:**
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:200-450` (approx)

**Impact:** Navigation cost — any state update that triggers shell recomposition re-evaluates all
28 collected values, not just the ones consumed by the visible screen. On a 4 Hz update plane this
is significant.

**Fix approach:** Push flow collections down to the screen-level composable that actually consumes
each value. The shell should collect only the flows it directly renders (connection status, active
screen id, drawer open state). Screen-local flows (temp values, file lists) belong in the screen
composable or a screen-scoped `ViewModel`-equivalent.

---

## Known Bugs / Deferred Issues

### H.264 feed blanks on rotation — deferred from Phase 21

**Symptoms:** Rotating the device while the H.264 webcam feed is playing causes the feed to go black.
The player survives but the surface is lost during `SurfaceView` resize/decoder renegotiation.

**Files:**
- `app/src/main/java/works/mees/dinghy/ui/webcam/Media3Feed.kt` — player lifecycle
- Tracked as CR-01 in Phase 21 review

**Trigger:** Device orientation change while `ExoPlayer` is actively decoding H.264.

**Workaround:** Works correctly on fresh entry in either orientation.

**Deferred to:** Phase 22 (pre-release). Fix = re-prepare player on surface-size change.

---

### FGS notification shows Bluetooth icon instead of jiib icon — deferred from Phase 18.2

**Symptoms:** The foreground-service notification (active during Moonraker connection) displays
`android.R.drawable.stat_sys_data_bluetooth` (a Bluetooth glyph) as the small icon.

**Files:**
- `app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt:302` — `.setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)`

**Trigger:** Always visible when the Moonraker FGS notification is shown.

**Deferred to:** Phase 22 (pre-release). Fix = create a 24dp notification-appropriate jiib status icon
and wire it here. Note: `ic_jiib_foreground` is 108dp (adaptive icon foreground), not suitable as a
notification small icon.

---

### Files Delete during print — RESOLVED in Phase 9 (not an open bug)

**Status:** ✅ FIXED. The original Phase-7 symptom (delete blocked on ALL files during any print)
was resolved in Phase 9 (plan 09-03). Delete is now scoped to the actively-printing file only —
every other idle file stays deletable mid-print. Routed through the pure, host-tested
`deleteAllowed` helper.

**Files:**
- `app/src/main/java/works/mees/dinghy/ui/files/DeleteGate.kt:22` — `deleteAllowed(...)` gate
- `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt:147-151` — D-15 comment + call site
- Tests: `app/src/test/java/works/mees/dinghy/ui/files/FilesDeleteGateTest.kt`,
  `FileBrowserHolderDeleteTest.kt`

**Note:** the stale `07-UAT.md` open item and the earlier draft of this section predate the Phase-9
fix. No action needed for Phases 23/25.

---

### ravens-perch/dinghy stream-metadata contract can drift

**Symptoms:** If the ravens-perch server changes its stream metadata schema, the webcam feed shows
"Feed unavailable" on the dinghy tablet. Root cause in Phase 21: app was reading flat keys instead of
nested `extra_data.ravens_perch.streams.<proto>.url`.

**Files:**
- `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamHolder.kt` — URL parsing from Moonraker camera objects

**Risk:** Any ravens-perch update that changes the `extra_data` nesting will silently break webcam on
all connected tablets.

**Fix approach:** Add a schema-version field to the ravens-perch metadata and a version check in
`WebcamHolder`; surface a clear error when the schema is unrecognised rather than silently showing
"Feed unavailable".

---

## Fragile Areas

### `CommandRegistry.all` — `catalog.json` and `printer-matrix.json` must stay in sync

**What makes it fragile:** `CommandCatalogDriftTest` (D-10) will fail if a `CommandSpec` is added to
`CommandRegistry.all` without a corresponding row in `docs/commands/catalog.json` AND
`printer-matrix.json`. This is a planner blind spot — it was missed at Phase 19 and will recur at
Phase 20 (SysInfo, which adds MR-* specs).

**Files:**
- `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` (879 lines) — `all` registry list
- `docs/commands/catalog.json` — must mirror `CommandRegistry.all`
- `docs/commands/printer-matrix.json` — must mirror `CommandRegistry.all`

**Safe modification:** Any new `CommandSpec` requires a corresponding update to both JSON files in the
same commit. The D-10 test will catch it at build time if missed.

---

### `FineTuneViewModel` — off-grid `markPending` / clamp mismatch

**What makes it fragile:** The clamp authority in `PrinterCommands` and the optimistic target set by
`markPending` must use the same numeric precision. If a Reset or initial value is off-grid (not a
multiple of the tuner's step), the flip-tolerance check can never be satisfied → the tuner UI dims
permanently until restart. Fixed in Phase 17 (WR-01/02/03/04) but the fix introduces per-tuner
`wirePrecisionFor` — any new tuner type added to `FineTuneViewModel` MUST also add a
`wirePrecisionFor` entry and a clamped `markPending` call.

**Files:**
- `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneViewModel.kt` — `markPending`, `wirePrecisionFor`
- `app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt` — `clampMinCruiseRatio` and per-command clamp functions

**Safe modification:** When adding a new tuner, add `wirePrecisionFor` mapping + ensure `markPending`
calls `roundToWirePrecision`. Run the off-grid regression tests before merge.

---

### `ScreenScaffold` / `BoxWithConstraints` — ALL screens are affected by any change here

**What makes it fragile:** Every screen in the app routes through `ScreenScaffold`, which uses a root
`BoxWithConstraints`. Any change to the scaffold's measurement contract or constraint propagation
breaks layout on all ~20 screens simultaneously.

**Files:**
- `app/src/main/java/works/mees/dinghy/designsystem/layout/ScreenScaffold.kt`

**Safe modification:** Run the full `@Preview` matrix (6 theme combos × portrait/landscape) before
committing any change. Verify on-device on flox (portrait AND landscape) for any scaffold change.

---

## Security Considerations

### Cleartext HTTP allowed via network security config (NSC)

**Risk:** Moonraker connections to local network printers use HTTP (not HTTPS) by default. The app's
network security config allows cleartext for local network targets (API 30 / LineageOS NSC path
confirmed in Phase 1 findings).

**Files:**
- `app/src/main/res/xml/network_security_config.xml` (existence noted — not read for contents)
- `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt` — connection setup

**Current mitigation:** LAN-only use case; user controls their own printer network. API key /
trusted-client auth is optional but supported.

**Recommendations:** Document that the app is LAN-only and should not be pointed at internet-exposed
Moonraker instances without a reverse proxy providing TLS.

---

## Test Coverage Gaps

### No instrumented UI tests for navigation flows that pass on-device

**What's not tested:** App Drawer swipe-up open/close, per-screen navigation entry/exit, orientation
transitions.

**Files:**
- `app/src/androidTest/` — `FineTuneNavTest` is the only nav instrumented test; confirmed broken on
  real hardware (swipe threshold issue, `dinghy-instrumented-swipe-threshold` memory entry)

**Risk:** Regressions in navigation/interaction lag (the reported symptom) cannot be caught by
automated tests. All navigation verification is manual on-device.

**Priority:** Medium. The workaround is on-device UAT on flox for each phase, but a fragile nav test
suite is better than none.

---

### Fine-Tune FW retraction — host-test-only, build-blind on E5/E3

**What's not tested:** Firmware retraction enable/disable path (`M207`/`M208`) is host-tested but has
never been exercised against a real printer with firmware retraction compiled in. Both E5 and E3 have
unknown FW retraction compile state.

**Files:**
- `app/src/main/java/works/mees/dinghy/ui/finetune/` — FW retraction tuners

**Risk:** Silent no-op on both test printers; any logic error in the retraction command builder
goes undetected until a user with FW retraction enabled reports it.

**Priority:** Low for v1. Annotate with a `// UNVERIFIED on real FW-retraction-enabled printers` comment.

---

*Concerns audit: 2026-06-08*
