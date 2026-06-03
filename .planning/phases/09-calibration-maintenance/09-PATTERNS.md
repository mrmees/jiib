# Phase 9: Calibration & Maintenance - Pattern Map

**Mapped:** 2026-06-02
**Files analyzed:** ~22 new/modified files
**Analogs found:** 22 / 22 (every new file has a strong in-repo analog — this is a "compose existing seams" phase)

> All paths below are absolute-relative to repo root `app/src/main/java/works/mees/dinghy/`.
> The planner should treat each "Analog" line as "copy the structure/conventions from this file."
> This phase adds **zero new libraries, zero new tokens, zero new primitive types** (per RESEARCH Standard Stack).
> The ONLY genuinely new render artifact is `render/BedMeshHeatmapView.kt` (+ host).

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `command/CommandRegistry.kt` (EXTEND) | registry/config | request-response | self (gcode/jsonRpc builders) | self-extend |
| `command/PrinterCommands.kt` (EXTEND) | utility (pure builder) | transform | self (clamp-before-format) | self-extend |
| `state/DeriveCapabilities.kt` (EXTEND) | derive (pure) | transform | self (`V1_SUBSCRIBE_CORE`) | self-extend |
| `state/PrinterState.kt` (EXTEND) | model | data-holder | self (`HeaterState` nested) | self-extend |
| `state/PrinterStateReducer.kt` (EXTEND) | reducer (pure) | transform | self (`applyStatus` walker) | self-extend |
| `state/PrinterStateStore.kt` (EXTEND, maybe) | store | event-driven | self (capabilities/oneshot flows) | self-extend |
| `calibration/ScrewsTiltResult.kt` (parser) | utility (pure) | transform | `ui/console/GcodeStoreParse.kt` | exact |
| `calibration/BedMeshModel.kt` (parser) | utility (pure) | transform | `ui/console/GcodeStoreParse.kt` + reducer accessors | exact |
| `calibration/TiltResult.kt` (state machine, pure) | utility (pure) | transform | `ui/route/TopRoute.kt` `derive` + `DeriveCapabilities` | role-match |
| `calibration/ManualProbeState.kt` (parser) | utility (pure) | transform | `ui/console/GcodeStoreParse.kt` (line parse) | exact |
| `calibration/ProbePresentGate.kt` (predicate, pure) | utility (pure) | transform | `Capabilities.hasObject` + `derive` | exact |
| `calibration/ScrewsTiltHolder.kt` | holder (headless) | event-driven | `ui/extrude/ExtrudeHolder.kt` | exact |
| `calibration/BedMeshHolder.kt` | holder (headless) | event-driven | `ui/extrude/ExtrudeHolder.kt` | exact |
| `calibration/TiltHolder.kt` | holder (headless) | event-driven | `ui/extrude/ExtrudeHolder.kt` | exact |
| `calibration/ProbeCalibrateHolder.kt` | holder (headless) | event-driven | `ui/extrude/ExtrudeHolder.kt` | exact |
| `render/BedMeshHeatmapView.kt` | render (Views Canvas) | transform/draw | `render/GraphView.kt` (DISCIPLINE only) | role-match (sibling) |
| `render/BedMeshHeatmapHost.kt` | render host (interop) | request-response | `render/GraphViewHost.kt` | exact |
| `ui/calibration/CalibrationHubScreen.kt` | screen | request-response | `ui/shell/AppDrawer.kt` (tile grid) | role-match |
| `ui/calibration/ScrewsTiltScreen.kt` | screen | request-response | `ui/files/FilesScreen.kt` + `ScreenScaffold` | exact |
| `ui/calibration/TiltScreen.kt` | screen | request-response | `ui/files/FilesScreen.kt` + `ScreenScaffold` | exact |
| `ui/calibration/BedMeshScreen.kt` | screen | request-response | `ui/files/FilesScreen.kt` + `GraphViewHost` consumer | exact |
| `ui/calibration/ProbeCalibrateScreen.kt` | screen | request-response | `ui/move/MoveScreen.kt` jog grammar + `FilesScreen` | role-match |
| `ui/route/TopRoute.kt` (EXTEND `Dest`) | route enum | — | self (`enum class Dest`) | self-extend |
| `ui/shell/AppDrawer.kt` (EXTEND tile) | nav | — | self (`DRAWER_TILES`) | self-extend |
| `ui/shell/AppShell.kt` (EXTEND `when(dest)`) | nav/host | event-driven | self (holder wiring + `when(dest)`) | self-extend |
| `ui/files/FilesScreen.kt` (FIX D-15) | screen | — | self (`deleteEnabled` predicate) | self-fix |

---

## Pattern Assignments

### `command/CommandRegistry.kt` — register the calibration commands (EXTEND)

**Analog:** self — every new calibration command is a `gcode(...)` spec gated by an `AvailabilityPredicate`.

The `gcode(...)` private factory (lines 394-407) already routes through `PrinterCommands.scriptParams` AND gets the G4 120s timeout automatically (the dispatcher keys the long timeout off `method == GCODE_SCRIPT`, NOT off the spec). So a new calibration command is just:

```kotlin
val screwsTiltCalculate: CommandSpec<Unit> = gcode(
    catalogId = "KGC-SCREWS_TILT_CALCULATE",
    key = { "screws_tilt" },
    gcode = { PrinterCommands.SCREWS_TILT_CALCULATE },
    availability = AvailabilityPredicate.ObjectPresent("screws_tilt_adjust"),
)
```

**Gating predicate types** (`command/CommandSpec.kt:19-26`):
```kotlin
sealed interface AvailabilityPredicate {
    data object Always
    data class ObjectPresent(val name: String)        // ← gate routines on z_tilt / bed_mesh / quad_gantry_level / screws_tilt_adjust / manual_probe / probe
    data class MacroPresent(val name: String)
    data class ComponentPresent(val name: String)
    data class GcodeCommandPresent(val name: String)  // ← available alternative gate (e.g. SAVE_CONFIG presence)
    data class AnyOf(val predicates: List<AvailabilityPredicate>)
    data class NotOnOurPrinters(val reason: String)
}
```

**Predicate map (RESEARCH Pattern 2 / Code Examples):**
- `SCREWS_TILT_CALCULATE` → `ObjectPresent("screws_tilt_adjust")`
- `Z_TILT_ADJUST` → `ObjectPresent("z_tilt")`
- `QUAD_GANTRY_LEVEL` → `ObjectPresent("quad_gantry_level")` (built blind, D-02 — gates itself off both test printers)
- `BED_MESH_CALIBRATE` / `BED_MESH_PROFILE` → `ObjectPresent("bed_mesh")`
- `PROBE_CALIBRATE` → `ObjectPresent("probe")`; `Z_ENDSTOP_CALIBRATE` is the probe-less sibling (A3 — `hasObject("probe") ? PROBE_CALIBRATE : Z_ENDSTOP_CALIBRATE`)
- `TESTZ` / `ACCEPT` / `ABORT` → `ObjectPresent("manual_probe")` (or `probe`)
- `SAVE_CONFIG` → `Always` (it is a host action, not object-gated)

**IMPORTANT (timeout):** do NOT add a new timeout path. `CommandDispatcher.kt:118-123,166-175` already gives EVERY `printer.gcode.script` call the 120s `GCODE_TIMEOUT_MS` — multi-minute probes are covered for free (RESEARCH "Don't Hand-Roll").

**Remember** to add each new spec to the `all` list (lines 334-369) — `CommandCatalogDriftTest` will flag any spec missing from the catalog.

---

### `command/PrinterCommands.kt` — pure gcode string builders (EXTEND)

**Analog:** self — mirrors the existing clamp-before-format / fixed-identifier discipline (lines 24-159).

Add constant action gcodes (no params) exactly like `COOLDOWN`/`DISABLE_STEPPERS` (lines 47-51):
```kotlin
const val SCREWS_TILT_CALCULATE = "SCREWS_TILT_CALCULATE"
const val Z_TILT_ADJUST = "Z_TILT_ADJUST"
const val QUAD_GANTRY_LEVEL = "QUAD_GANTRY_LEVEL"
const val BED_MESH_CALIBRATE = "BED_MESH_CALIBRATE"   // bare — let printer/KAMP defaults apply (RESEARCH Open-Q2)
const val PROBE_CALIBRATE = "PROBE_CALIBRATE"
const val SAVE_CONFIG = "SAVE_CONFIG"
const val ACCEPT = "ACCEPT"
const val ABORT = "ABORT"
```

For the two parameterized builders, follow the **clamp-before-format** SECURITY discipline (lines 14-18, 88-93):
```kotlin
/** TESTZ Z=±step — the manual-probe jog nudge (D-01). [step] clamped to a fine bounded range. */
fun testZ(step: Double): String = "TESTZ Z=${step.coerceIn(-MAX_TESTZ_MM, MAX_TESTZ_MM)}"

/** BED_MESH_PROFILE SAVE=<name>. [name] is APP-GENERATED (YY.MM.DD_HH.MM, D-10) — no user free-text. */
fun bedMeshProfileSave(name: String): String = "BED_MESH_PROFILE SAVE=$name"
fun bedMeshProfileLoad(name: String): String = "BED_MESH_PROFILE LOAD=$name"
fun bedMeshProfileRemove(name: String): String = "BED_MESH_PROFILE REMOVE=$name"
```
The Material preset block (lines 56-66) is the precedent for the bed-preheat preset (D-13) — fixed `Preset` list. `scriptParams` (line 148-149) wraps the string into `{"script": ...}`.

**SECURITY (ASVS V5):** the bed-mesh Save name must be an app-generated timestamp (D-10) so no free-text is ever concatenated; clamp the TESTZ step like `jog`/`extrude` already clamp magnitude. Mirror the class header comment block (lines 14-22) verbatim in spirit.

---

### `state/DeriveCapabilities.kt` — extend the subscribe superset (EXTEND)

**Analog:** self — `V1_SUBSCRIBE_CORE` (lines 50-60) + `deriveSubscribeSet` (lines 68-90).

Add the five calibration objects to the **static core superset** (they are always-requested-IF-present, intersected with detected — A3 discipline already enforced by the `for (core in V1_SUBSCRIBE_CORE) { if (core in present) ... }` loop):
```kotlin
private val V1_SUBSCRIBE_CORE: Set<String> = setOf(
    // …existing…
    "screws_tilt_adjust",   // CALIB-02 guided-loop result source
    "z_tilt",               // CALIB-03 applied flag
    "quad_gantry_level",    // CALIB-03 applied flag (gated off both test printers)
    "bed_mesh",             // CALIB-04 mesh matrices/profiles
    "manual_probe",         // CALIB-05 is_active / z_position session state
    "probe",                // CALIB-05 probe-present gate
)
```
**Anti-pattern (RESEARCH):** NEVER subscribe an object unconditionally — `deriveSubscribeSet` already intersects with detected objects (A3). Add to the superset, never to an unconditional request.

`deriveCapabilities` itself needs NO change for gating: `Capabilities.hasObject(name)` (`Capabilities.kt:52`) already answers every routine's presence predicate live, re-derived every reconnect. Confirm `configfile.settings["screws_tilt_adjust"]` is reachable for screw coords/names (D-04/D-06) — that comes via a one-shot read, not the subscribe set (see Store note below).

**Test analog:** `state/DeriveCapabilitiesTest.kt` — add cases proving each new object is subscribed-when-present and omitted-when-absent.

---

### `state/PrinterState.kt` + `PrinterStateReducer.kt` — surface the new objects (EXTEND)

**Analog:** self — `HeaterState` nested model (`PrinterState.kt:96-109`) + `applyStatus` null-safe walker (`PrinterStateReducer.kt:66-140`).

**Model (PrinterState.kt):** add nullable fields / nested models, one per object, mirroring `HeaterState`'s fail-safe-defaults pattern (every field defaulted, NEVER `!!`):
```kotlin
val screwsTilt: ScrewsTiltObject? = null,   // results{}, error, max_deviation
val zTiltApplied: Boolean? = null,          // null=never run, false=run-in-progress/failed, true=converged
val qglApplied: Boolean? = null,
val bedMesh: BedMeshObject? = null,         // profile_name, mesh_min/max, probed_matrix, mesh_matrix, profiles
val manualProbe: ManualProbeObject? = null, // is_active, z_position, z_position_lower/upper
```
Keep these PLAIN data classes (no Compose annotations — headless spine, ADR-0001), exactly like `HeaterState`.

**Reducer (PrinterStateReducer.kt):** add `status.objectOrNull("bed_mesh")?.let { ... }` blocks in `applyStatus` (lines 66-140), reusing the existing null-safe accessors at the bottom of the file (lines 168-184): `objectOrNull`, `stringOrNull`, `doubleOrNullAt`, `booleanOrNull`, `doubleListOrNull`. The matrices (`mesh_matrix`/`probed_matrix`) are arrays-of-arrays — add a `double2dListOrNull` accessor following the `doubleListOrNull` pattern (line 182-183). `mesh_min`/`mesh_max` are JSON arrays `[x,y]` (Python tuple → array; RESEARCH Pitfall 6) → read via `doubleListOrNull`.

**House rule (carry verbatim):** every walk null-safe, a missing/garbage field is SKIPPED (retained), never fatal (reducer header lines 18-19). This is THE mock-vs-reality defense (RESEARCH Pitfall 6).

**Test analog:** `state/PrinterStateReducerTest.kt` — feed the captured `*_e5.json` fixtures (Wave-0 gaps).

---

### `state/PrinterStateStore.kt` — one-shot reads + capabilities flow (EXTEND if needed)

**Analog:** self — the store already exposes `printerState`, `capabilities`, and one-shot handshake StateFlows (`minExtrudeTemp`/`maxExtrudeDistance`), consumed by `ExtrudeHolder` (`ExtrudeHolder.kt:62-69`). The `[screws_tilt_adjust]` config (screw coords/names for D-04/D-06) is a one-shot `configfile.settings` read — model it on the existing `minExtrudeTemp`/`maxExtrudeDistance` one-shot seam (set at handshake, exposed as a StateFlow, COMBINEd into the holder so it's deterministic — `ExtrudeHolder.kt:60-71` doc block). RESEARCH Open-Q1 recommends a belt-and-braces one-shot `objects/query?screws_tilt_adjust` on dispatch completion.

---

### Pure result parsers (`calibration/*.kt`) — the Nyquist core

**Analog (THE template):** `ui/console/GcodeStoreParse.kt` — the canonical pure JSON walker.

Every parser is a free function (or `companion`/`object`) with the exact `GcodeStoreParse` discipline (lines 9-26): NO I/O, NO coroutines, NO Compose; `runCatching { ... }.getOrDefault(...)` so a malformed top-level shape degrades to a safe empty default; `mapNotNull` per-entry so one bad entry is skipped not fatal. Fed a **captured-from-real-hardware JSON fixture** (Wave-0 `*_e5.json`), host-testable with no device.

```kotlin
// parseGcodeStore (the template) — lines 27-38:
fun parseGcodeStore(result: JsonObject): List<ConsoleLine> = runCatching {
    (result["gcode_store"] as? JsonArray).orEmpty().mapNotNull { el ->
        val entry = el as? JsonObject ?: return@mapNotNull null
        val message = entry["message"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        …
    }
}.getOrDefault(emptyList())
```

The five parsers + their data sources (RESEARCH Code Examples / Pitfalls):

| Parser | Source object | Key shape gotcha | Test analog |
|--------|---------------|------------------|-------------|
| `parseScrewsTilt(results, config): GuidedLoopState` | `screws_tilt_adjust.results` keyed `screw1..screwN` (1-based) joined to config `screwN`/`screwN_name` by index | **Pitfall 1** — results keyed by 1-based index, NOT screw name; worst = max `\|adjust-minutes\|`; done = `error==false` | `GcodeStoreParseTest` |
| `BedMeshModel.from(bedMesh): BedMeshModel` | `bed_mesh` (`mesh_matrix`/`probed_matrix`/`mesh_min`/`mesh_max`/`profile_name`/`profiles`) | **Pitfall 4** — empty-state = `mesh_matrix` empty / `profile_name==""`, SEPARATE from `profiles` (saved list); matrices are array-of-arrays, min/max are `[x,y]` arrays | new `BedMeshModelTest` |
| `tiltState(dispatched, applied, failed): TiltState` | `z_tilt.applied` / `quad_gantry_level.applied` + dispatcher `Failure` | **Pitfall 2** — `applied==false` is BOTH "running" AND "failed"; never infer failed from `applied==false` alone. Model on the `derive(...)` pure state machine in `ui/route/TopRoute.kt:47-51` | new `TiltResultTest` |
| `parseZPosition(line): ZPositionBracket?` | console line `"Z position: %s --> %.3f <-- %s"` | nullable bracket parse; drive page off `manual_probe.is_active` (Pattern 3) | new `ManualProbeStateTest` |
| `deleteAllowed(selectedPath, activePrintFilename, printState): Boolean` | `PrinterState.printFilename` (D-15) | filename is RELATIVE, dir-prefixed, NO leading `gcodes/` (verify vs `docs/moonraker-capabilities.md`) | new `FilesDeleteGateTest` |
| `probeCalibrateGate(caps): Command` | `Capabilities.hasObject("probe")` | A3 — `probe` present → PROBE_CALIBRATE else Z_ENDSTOP_CALIBRATE | new `ProbePresentGateTest` |

**State-machine purity reference** (`ui/route/TopRoute.kt:47-51`) — the `tiltState` parser should mirror this `when`-based pure derive idiom:
```kotlin
fun derive(cfgPresent: Boolean, s: PrinterState): TopRoute = when {
    !cfgPresent -> TopRoute.Connect
    s.klippyState != KlippyState.Ready -> TopRoute.Splash
    else -> TopRoute.Shell(Dest.PrintStatus)
}
```

---

### Headless holders (`calibration/*Holder.kt`)

**Analog (THE template):** `ui/extrude/ExtrudeHolder.kt` — toolkit-agnostic StateFlow holder.

Copy the structure exactly (`ExtrudeHolder.kt:44-114`):
- ctor takes `scope: CoroutineScope` + `store: PrinterStateStore` (the holder CONSUMES the spine, never opens a session — line 41-42 doc);
- private `MutableStateFlow(Vm())` exposed as `val vm: StateFlow<Vm>` (lines 55-57);
- `init { scope.launch { combine(store.printerState, …).collect { _vm.value = buildVm(...) } } }` (lines 59-71) — NO second throttle (the store conflates at 250ms; lines 35-39 doc);
- a pure `buildVm(state, caps, …): Vm` (lines 85-113);
- the Vm is a plain `data class` with defaulted fields (lines 125-139).

Each calibration holder reads its object off `store.printerState` + `store.capabilities` and folds the dispatcher's `Failure` events for done/failed detection (Pattern 2 — done from the object, failed from `CommandDispatcher.events`). `ProbeCalibrateHolder` is the stateful one (drive enable/disable off `manual_probe.is_active`, Pattern 3) — model the `setActiveTool`-style imperative setter (lines 73-83) for the step-preset selection if needed.

**Test analog:** `ui/extrude/ExtrudeHolderTest.kt:35-70` — `runTest(UnconfinedTestDispatcher())`, real `PrinterStateStore(backgroundScope)`, `store.seed(PrinterState(...))` + `runCurrent()`, then assert `holder.vm.value`.

---

### `render/BedMeshHeatmapView.kt` (+ `BedMeshHeatmapHost.kt`) — the ONE new render surface

**Analog (DISCIPLINE only — do NOT fork):** `render/GraphView.kt` + `render/GraphViewHost.kt`.

GraphView is a 1D line primitive; the heatmap is a 2D draw — build a **sibling** `View`, not a GraphView extension (RESEARCH Alternatives / Anti-Patterns). Copy GraphView's allocation-free contract verbatim:
- `class BedMeshHeatmapView(context: Context) : View(context), ThemeableView` (`GraphView.kt:62`);
- **pre-allocate** all `Paint`/`Path`/`Rect` in init — NEVER `new` in `onDraw` (the Adreno-320 GC-churn trap; `GraphView.kt:64-90`, Pitfall 4);
- implement `ThemeableView.applyTokens(tokens)` → recolor from role tokens + `invalidate()` (the heat ramp endpoints = `--stop` high ↔ `--accent` low per UI-SPEC color note; dots = `--text-3` low-opacity);
- repaint trigger is an imperative `setMesh(model)` called on a new sample, NOT an animation loop (`GraphView.kt:55-59` Motion doc / D-13);
- NO raw hex literal (THEME-01).

**Host (`BedMeshHeatmapHost.kt`):** copy `GraphViewHost.kt:27-43` verbatim — `AndroidView(factory = { ctx -> BedMeshHeatmapView(ctx) }, update = { view -> view.applyTokens(tokens); view.setMesh(model) })`. `factory` runs once; `update` pushes tokens + data so a theme flip recolors without recreation.

**On-device gate:** heatmap fill-rate must be re-measured on flox via gfxinfo (the Phase-3/5 two-part liveness+latency gate) — 50×50 interpolated fill is the worst case.

---

### `ui/calibration/CalibrationHubScreen.kt` — the hub (CALIB-01 / D-14)

**Analog:** `ui/shell/AppDrawer.kt:61-192` — the square-outline-tile grid grammar.

Copy the `LazyVerticalGrid(GridCells.Fixed(...))` + `DrawerTile` pattern (`AppDrawer.kt:72-93, 137-192`): square `aspectRatio(1f)` tiles, `MaterialSymbol` + label, accent outline for supported / hairline+dimmed for unsupported. **OWNER OVERRIDE (UI-SPEC §1):** ALL routines shown — supported first (accent), unsupported greyed-but-tappable sorted last (NOT inert like the drawer's greyed tiles — these DO navigate so the page can be inspected). Gutter = single green `Back` via `ScreenScaffold` gutter slot. Tile glyphs (unique per screen): `architecture` / `vertical_align_center` / `crop_square` / `grid_on` / `straighten`.

Gating: read `container.capabilities` (a StateFlow), `caps.hasObject("z_tilt")` etc. to mark supported vs greyed.

---

### `ui/calibration/{ScrewsTilt,Tilt,BedMesh,ProbeCalibrate}Screen.kt`

**Analog:** `ui/files/FilesScreen.kt` (full Focus/Field/Gutter screen with `ConfirmGuard` + `SeverityToast` + dispatch) and `designsystem/layout/ScreenScaffold.kt`.

**Scaffold pattern** (`ScreenScaffold.kt:48-63`): `focus` / `field` / `gutter` slots, `portraitFocusAspect` for the sacred-square Focus (the heatmap, the `bed_tilt.svg`, the to-scale bed). Landscape = Focus|Field 50/50 + full-width gutter; portrait = stacked.

**Gutter button pattern** (`FilesScreen.kt:118-137`): a `Row` of `OutlinedControl`-class buttons with `Intent` per THEMING.md. `Intent` enum colors (`FilesScreen.kt:418-424`): `Neutral→outline`, `Accent→accentLine`, `Warn→heat`(amber), `Danger→stop`(red), `Go→go`(green). Per UI-SPEC: `Run`=blue/accent, `Back`=green/go, `Abort`=red/danger, `Accept`=green/go, `Save`=amber/warn.

**ConfirmGuard pattern** (`FilesScreen.kt:141-173` consumer; `designsystem/ConfirmGuard.kt:54-122` definition): full-screen guard, `destructive` flag picks red-vs-green. **EXTEND `ConfirmGuard` with an amber `proceed-at-peril` variant** for the reusable SAVE_CONFIG restart gate (UI-SPEC: D-12) — add a `warn: Boolean`/intent param alongside `destructive` (currently lines 63-67 toggle red `Intent.Danger` vs green `Intent.Go`; add the amber `Intent.Warn` + `t.heatSoft` tint branch).

**ProbeCalibrateScreen** additionally mirrors `ui/move/MoveScreen.kt` jog-pad grammar (Z▲/Z▼ nudge + step-preset pills) — the only stateful page (state driven by `manual_probe.is_active`, Pattern 3; gutter is state-adaptive Idle/Active/Accepted per UI-SPEC §5).

**Heatmap consumer (BedMeshScreen):** host `BedMeshHeatmapHost` in the Focus slot, passing the collected `tokens` + the holder's `BedMeshModel`, exactly as `TemperatureScreen` hosts `GraphViewHost`.

**Dispatch from a screen** (`command/CommandDispatchExtensions.kt:6-11`):
```kotlin
fun <P> CommandDispatcher.dispatch(command: CommandSpec<P>, args: P) =
    dispatch(command.dispatchKey(args), command.method!!, command.params(args))
```
Call `dispatcher.dispatch(CommandRegistry.screwsTiltCalculate, Unit)`. Toast failures by collecting `dispatcher.events` (`CommandDispatcher.kt:94`) into `SeverityToast` (Severity.Error) — RpcError text surfaced verbatim (UI-SPEC error copy; never `e.message` transport text — T-05-11-01).

---

### Navigation wiring (EXTEND `Dest`, `AppDrawer`, `AppShell`)

**Analog:** self.

- `ui/route/TopRoute.kt:30` — add `Calibration` (and any sub-dests) to `enum class Dest`. (Note: in-shell sub-routing for the hub→routine pages can be a lean local back-stack within `Dest.Calibration`, mirroring how `Dest.Macros` hosts Bookmarked-vs-System sub-screens — `AppShell.kt:103-123, 257-277` — rather than five new top-level Dests.)
- `ui/shell/AppDrawer.kt:116-129` — add ONE `DrawerTileSpec(label = "Calibration", symbol = <unique>, dest = Dest.Calibration)` to `DRAWER_TILES` (D-14, single tile).
- `ui/shell/AppShell.kt:136-143` — build the calibration holders with `remember(store) { …Holder(scope, store) }` (re-keyed on spine rebuild); add a `Dest.Calibration -> CalibrationHubScreen(...)` arm to the `when(dest)` (lines 230-287). If a calibration page has a scrollable Field (bed-mesh Load selector), add `Dest.Calibration` to the swipe-drawer-suppression set (line 222) per the Files Views-in-Compose scroll lesson.

---

### `ui/files/FilesScreen.kt` — D-15 delete-gating fix (FIX)

**Analog:** self — the existing predicate is the bug.

```kotlin
// CURRENT (too broad) — FilesScreen.kt:77, 94:
val printingActive = printerState.printState == PrintState.Printing || printerState.printState == PrintState.Paused
deleteEnabled = selected != null && !printingActive   // blocks ALL files during any print

// FIX (D-15) — only the CURRENTLY-PRINTING file is undeletable:
val activePrint = printerState.printFilename          // "" when idle (PrinterState.kt:59)
val isActiveFile = activePrint.isNotEmpty() && selected?.path == activePrint
deleteEnabled = selected != null && !isActiveFile
```
Extract the comparison into the PURE `deleteAllowed(selectedPath, activePrintFilename, printState)` helper (above) so the path-form match is host-tested (`FilesDeleteGateTest`). Verify `selected.path` and `printFilename` are the SAME relative form (no leading `gcodes/`) against `docs/moonraker-capabilities.md`. Then record the relaxed rule in `docs/ui_design/` and add the scoping check to `09-UAT.md` (D-15 deliverables).

---

## Shared Patterns

### Capability gating (live, every reconnect)
**Source:** `state/Capabilities.kt:52` (`hasObject`) + `state/DeriveCapabilities.kt` (`deriveSubscribeSet`)
**Apply to:** the hub (which tiles render supported), each screen's Run button, each CommandSpec's `availability`.
```kotlin
fun hasObject(name: String): Boolean = name in objects   // Capabilities.kt:52
```
Re-derived on every reconnect from `printer.objects.list`; works on any user's printer (no static per-printer table — RESEARCH "Don't Hand-Roll").

### Done/failed detection (NEVER a bare gcode ack)
**Source:** `command/CommandDispatcher.kt:94` (`events: SharedFlow<DispatchEvent>`) + the live object.
**Apply to:** all four automatic-routine holders + screws-tilt.
- done := the live-object signal (`z_tilt.applied==true`, populated `screws_tilt_adjust.results` w/ `error==false`, non-empty `bed_mesh.mesh_matrix`, `manual_probe.is_active==false`).
- failed := `DispatchEvent.Failure` (RpcError text). The dispatcher already catches RpcError non-fatally (G1, lines 142-149) — "Too many retries" surfaces as a toast, never a crash.

### Long-gcode timeout (G4)
**Source:** `command/CommandDispatcher.kt:118-123, 166-175`
**Apply to:** every calibration `gcode.script` command — automatic, no action needed. The dispatcher keys `GCODE_TIMEOUT_MS=120_000L` off `method == GCODE_SCRIPT`.

### SAVE_CONFIG → firmware-restart re-handshake (G2 reuse)
**Source:** `di/SessionControl.kt`, `net/MoonrakerSession.kt`, `net/JsonRpcClient.kt` — the proven Phase-5 G2 path; `state/PrinterStateReducer.kt:51-59` (`applyKlippyMethod` folding `notify_klippy_ready`/`_shutdown`).
**Apply to:** bed-mesh Save (D-10) + Probe-Calibrate Save (D-01), both via D-12.
Flow (RESEARCH Code Examples 5): amber `ConfirmGuard` → dispatch `SAVE_CONFIG` → expect `notify_klippy_shutdown` then `notify_klippy_ready` on the still-open socket → the existing `runHandshake()` re-runs objects/subscribe. No new reconnect code. **Test analog:** `state/KlippyLifecycleTest.kt` / `net/KlippyReadyResyncTest.kt` — extend, don't fork.

### In-progress live feed (D-11)
**Source:** `ui/console/GcodeStoreParse.kt` + `ui/console/ConsoleSeverity.kt` + `state/ConsoleScrollback.kt` + `render/RingBuffer.kt`
**Apply to:** every routine's running state.
Tap the raw `notify_gcode_response` stream UPSTREAM of the console display filter (Phase-8 D-04 — independent of the console's filtered view). Reuse `ConsoleSeverity.classify` for severity coloring. Done/failed still come from the object/dispatcher, NEVER from parsing the feed text (Pitfall 2/3).

### Error/success surfacing
**Source:** `designsystem/SeverityToast.kt` (consumer pattern at `FilesScreen.kt:212-214`)
**Apply to:** all screens — `SeverityToast(Severity.Error, rpcErrorText)` for rejection, `Severity.Success` for save-success. Verbatim RpcError text; never the transport `e.message` (T-05-11-01).

### Headless holder + StateFlow (ADR-0001)
**Source:** `ui/extrude/ExtrudeHolder.kt` (+ test `ExtrudeHolderTest.kt`)
**Apply to:** all four calibration holders. Plain Kotlin, no Compose annotations, `combine(store flows)` → `StateFlow<Vm>`, no second throttle.

### Pure-parser purity discipline
**Source:** `ui/console/GcodeStoreParse.kt` + `state/PrinterStateReducer.kt` null-safe accessors (lines 168-184) + `state/DeriveCapabilities.kt` `derive*` idiom
**Apply to:** all five parsers. `runCatching { … }.getOrDefault(safe)`, `mapNotNull`, never `!!` on wire JSON — THE mock-vs-reality defense.

---

## No Analog Found

None. Every Phase-9 file maps to an existing in-repo analog. The closest thing to "new" is `render/BedMeshHeatmapView.kt`, which is a deliberate **sibling** of `GraphView.kt` (reusing its discipline, not its code — a 2D heatmap vs a 1D line graph). The planner should NOT use RESEARCH.md code examples in place of these analogs except for the verbatim Klipper object/console SHAPES (RESEARCH Code Examples 1-6), which are the data contract the parsers are written against.

---

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/` (command, state, render, designsystem, ui/{files,extrude,move,console,shell}, ui/route) + `app/src/test/...` (holder/parser test patterns + fixtures).
**Files scanned:** ~30 (CommandRegistry, PrinterCommands, CommandDispatcher, CommandDispatchExtensions, CommandSpec, Capabilities, DeriveCapabilities, PrinterState, PrinterStateReducer, GcodeStoreParse, GraphView, GraphViewHost, ConfirmGuard, ScreenScaffold, AppDrawer, AppShell, RootController, TopRoute, FilesScreen, ExtrudeHolder, ExtrudeHolderTest, plus directory + fixture inventory).
**Wave-0 test fixtures to capture (per RESEARCH, before finalizing parsers):** `screws_tilt_adjust_e5.json`, `bed_mesh_e5.json`, `z_tilt_e5.json`, `manual_probe_e5.json`, `configfile_screws_e5.json` (model on the existing `app/src/test/resources/fixtures/gcode_store_e5.json`).
**Pattern extraction date:** 2026-06-02
