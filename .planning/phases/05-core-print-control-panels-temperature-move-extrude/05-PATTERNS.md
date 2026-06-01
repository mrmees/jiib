# Phase 5: Core Print-Control Panels — Temperature, Move, Extrude - Pattern Map

**Mapped:** 2026-05-31
**Files analyzed:** 16 (10 new, 6 modified)
**Analogs found:** 16 / 16 (every file has a same-role in-repo analog — this is a wiring phase)

This phase is ~90% wiring existing primitives to verified Moonraker calls (per RESEARCH). The dominant
analog is the Phase-4 Print Status surface (`ui/printstatus/`), which already demonstrates EVERY pattern
the three new panels need: a toolkit-agnostic holder consuming the throttled store, a Compose screen on
`ScreenScaffold` + `OutlinedControl` + `LocalTokens`, dispatch via `CommandDispatcher`, `ConfirmGuard`
and `SeverityToast` usage, and `GraphViewHost` interop. **Mirror it.** Do NOT invent new patterns.

All paths below are under `app/src/main/java/works/mees/dinghy/`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `ui/temperature/TemperatureHolder.kt` (NEW) | holder/view-model | request-response (derived state) | `ui/printstatus/PrintStatusHolder.kt` | exact |
| `ui/temperature/TemperatureScreen.kt` (NEW) | component (Compose screen) | event-driven (tap→dispatch) | `ui/printstatus/PrintStatusScreen.kt` | exact |
| `ui/move/MoveHolder.kt` (NEW) | holder/view-model | request-response | `ui/printstatus/PrintStatusHolder.kt` | role-match |
| `ui/move/MoveScreen.kt` (NEW) | component (Compose screen) | event-driven | `ui/printstatus/PrintStatusScreen.kt` | role-match |
| `ui/extrude/ExtrudeHolder.kt` (NEW) | holder/view-model | request-response | `ui/printstatus/PrintStatusHolder.kt` | role-match |
| `ui/extrude/ExtrudeScreen.kt` (NEW) | component (Compose screen) | event-driven | `ui/printstatus/PrintStatusScreen.kt` | role-match |
| `command/PrinterCommands.kt` (NEW) | utility (pure gcode builders) | transform | `state/DeriveCapabilities.kt` (pure free-fn module) | role-match |
| `render/GraphView.kt` (MODIFY) | view (Canvas render) | streaming (high-churn) | itself (extend 1→N traces) | exact (in-place) |
| `render/GraphViewHost.kt` (MODIFY) | interop seam | streaming | itself (pass N snapshots+colors) | exact (in-place) |
| `state/PrinterState.kt` (MODIFY) | model (immutable state) | transform | itself (add fields) | exact (in-place) |
| `state/PrinterStateReducer.kt` (MODIFY) | utility (pure reducer) | transform | itself (read 2 new objects) | exact (in-place) |
| `state/Capabilities.kt` (MODIFY) | model | transform | itself (add macro/min-temp helpers) | exact (in-place) |
| `state/DeriveCapabilities.kt` (MODIFY) | utility (pure derivation) | transform | itself (case-insensitive macro) | exact (in-place) |
| `net/JsonRpc.kt` (MODIFY) | config (method-name registry) | n/a | itself (add 2 const) | exact (in-place) |
| `theme/ThemeTokens.kt` + `theme/BakedTokens.kt` (MODIFY) | config (token table) | n/a | itself (add `violet`/`trace3`) | exact (in-place) |
| `ui/route/TopRoute.kt` + `ui/shell/AppDrawer.kt` + `ui/shell/AppShell.kt` (MODIFY) | route | request-response | themselves (extend `Dest`) | exact (in-place) |

---

## Pattern Assignments

### `ui/temperature/TemperatureHolder.kt` / `MoveHolder.kt` / `ExtrudeHolder.kt` (holder, request-response)

**Analog:** `ui/printstatus/PrintStatusHolder.kt` — MIRROR EXACTLY. Plain Kotlin (no Compose
annotations), host-unit-testable, owns derived `StateFlow`s, **NO second throttle**.

**Holder shape — constructor + collect loop** (`PrintStatusHolder.kt` lines 46–74):
```kotlin
class PrintStatusHolder(
    scope: CoroutineScope,
    private val store: PrinterStateStore,
) {
    private val ring = RingBuffer()
    private val _grid = MutableStateFlow(PrintStatusGrid.EMPTY)
    val grid: StateFlow<PrintStatusGrid> = _grid.asStateFlow()

    init {
        // Consume the store's ALREADY-throttled flow — NO second sample/debounce/delay here (review #9).
        scope.launch {
            store.printerState.collect { state ->
                val caps = store.capabilities.value
                // ... build derived VM from state + caps ...
                _grid.value = buildGrid(state, caps, primaryName)
            }
        }
    }
}
```

**Capability-fallback discipline** (`PrintStatusHolder.kt` lines 79–99) — the holder resolves heater
names deterministically from `caps.heaters` (authoritative) falling back to live state keys; NEVER
fabricates a value. The three new holders extend this:
- `TemperatureHolder` — per-sensor current/target legend + the backfilled `RingBuffer` per drawn sensor
  (one ring per trace, mirroring the single-ring pattern at line 51).
- `MoveHolder` — `gcode_position` X/Y/Z + per-axis homed gating (`'x' in state.homedAxes`), reading the
  NEW `PrinterState.gcodePosition` field (see state changes below).
- `ExtrudeHolder` — `canExtrude` gate, `minExtrudeTemp` (queried-once, stashed on store like a capability),
  `tools = (0 until caps.extruderCount).map { "T$it" }`, `showToolSelector = caps.extruderCount > 1`,
  `hasLoadMacro = caps.hasMacroIgnoreCase("LOAD_FILAMENT")`.

**Test analog:** `app/src/test/java/works/mees/dinghy/ui/printstatus/PrintStatusHolderTest.kt`
(lines 28–45) — `runTest(UnconfinedTestDispatcher())`, drive a real `PrinterStateStore`, `store.seed(...)`,
`runCurrent()`, assert the exposed `StateFlow.value`. Copy this harness for `*HolderTest`.

---

### `ui/temperature/TemperatureScreen.kt` / `MoveScreen.kt` / `ExtrudeScreen.kt` (Compose screen, event-driven)

**Analog:** `ui/printstatus/PrintStatusScreen.kt` — same imports, same dispatch wiring, same failure-toast
plumbing, same `ScreenScaffold` Focus/Field/Gutter structure.

**Imports + signature** (`PrintStatusScreen.kt` lines 30–47, 80–90):
```kotlin
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.net.JsonRpcMethods
import works.mees.dinghy.theme.compose.LocalTokens

@Composable
fun PrintStatusScreen(container: AppContainer, holder: PrintStatusHolder, modifier: Modifier = Modifier) {
    val state by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val grid by holder.grid.collectAsStateWithLifecycle()
    val tokens = LocalTokens.current
```

**Dispatch failure → toast plumbing** (`PrintStatusScreen.kt` lines 92–113) — copy verbatim into every
panel so dispatch failures surface uniformly (PRIM-04). For Extrude's missing-macro popup (D-10), reuse the
same `failureText` + `SeverityToast` mechanism but with `Severity.Info` ("No LOAD_FILAMENT macro configured").

**`ScreenScaffold` slot layout** (`PrintStatusScreen.kt` lines 115–159) — Focus / Field / Gutter blocks.
Note the gutter row uses `OutlinedControl(intent = Intent.Danger)` for Stop and `weight(1f)` per tile.
- Temperature Gutter (D-04): `Back` (`Intent.Danger`) · `Presets` (`Intent.Neutral`) · `Cooldown`
  (`Intent.Warn`). Field hosts the multi-trace `GraphViewHost`.
- Move Gutter (D-03): `Home` (`Intent.Accent`) · `Disable` (`Intent.Warn`) · `Back` (`Intent.Danger`).
  Distance/Z selectors are `OutlinedControl` grids in Focus/Field.
- Extrude Gutter (D-08): Move-template — Extrude/Retract primary + distance/speed selectors.

**GraphViewHost call site** (`PrintStatusScreen.kt` lines 131–137) — exact invocation to extend for
TEMP-04 (N traces):
```kotlin
GraphViewHost(
    tokens = tokens,
    snapshot = sparkline,
    modifier = Modifier.fillMaxWidth().weight(0.6f),
)
```

**ScrubberPage invocation** (heater target TEMP-02 / extrude distance EXTR-01) — reuse
`designsystem/ScrubberPage.kt` (lines 87–99 signature). Tap a live temp value → show a `ScrubberPage`:
```kotlin
ScrubberPage(
    label = "Nozzle", value = currentTarget, range = 0f..300f, step = 5f, unit = "°C",
    onValueChange = { /* live preview */ },
    onCancel = { showScrubber = false },
    onApply = { v ->
        dispatcher?.dispatch("set_extruder", JsonRpcMethods.GCODE_SCRIPT,
            scriptParams(PrinterCommands.setHeater("extruder", v.toInt())))
        showScrubber = false
    },
)
```

**ConfirmGuard invocation** (Disable steppers MOVE-03) — copy the estop pattern
(`PrintStatusScreen.kt` lines 161–176). ConfirmGuard dispatches NOTHING; `onConfirm` dispatches:
```kotlin
ConfirmGuard(
    title = "Disable steppers?",
    message = "Motors will release; the toolhead can be moved by hand and axes become un-homed.",
    confirmLabel = "DISABLE",
    onConfirm = {
        dispatcher?.dispatch("disable_steppers", JsonRpcMethods.GCODE_SCRIPT,
            scriptParams(PrinterCommands.DISABLE_STEPPERS))   // "M84"
        showDisableGuard = false
    },
    onCancel = { showDisableGuard = false },
    destructive = false,   // amber proceed-at-peril, not red (Intent.Warn semantics) — verify per design
)
```
(`ConfirmGuard.kt` signature at lines 53–62; `destructive` toggles confirm intent red vs green.)

---

### `command/PrinterCommands.kt` (NEW — utility, pure transform)

**Analog:** `state/DeriveCapabilities.kt` — a file of PURE free functions (no I/O, no coroutines, no
Compose), top-of-file KDoc explaining purity + host-testability, same input → same output. Mirror that
module shape. RESEARCH §5 + Code Examples give the exact gcode strings:
```kotlin
fun setHeater(heater: String, target: Int) = "SET_HEATER_TEMPERATURE HEATER=$heater TARGET=$target"
const val COOLDOWN = "TURN_OFF_HEATERS"
const val DISABLE_STEPPERS = "M84"
fun jog(axis: String, mm: Double, feedMmMin: Int) =
    "SAVE_GCODE_STATE NAME=dd_jog\nG91\nG1 $axis$mm F$feedMmMin\nRESTORE_GCODE_STATE NAME=dd_jog"
fun extrude(mm: Double, feedMmMin: Int) =
    "SAVE_GCODE_STATE NAME=dd_ext\nM83\nG1 E$mm F$feedMmMin\nRESTORE_GCODE_STATE NAME=dd_ext"
fun homeAll() = "G28"; fun homeAxis(a: String) = "G28 $a"
```
Plus the `scriptParams` helper (RESEARCH Pattern 2): `fun scriptParams(gcode: String): JsonElement =
buildJsonObject { put("script", gcode) }`. **Clamp numerics before formatting** (ASVS V5 — bounded
scrubber/selector input only, no free text). Test analog: `DeriveCapabilitiesTest.kt` /
`PrinterStateReducerTest.kt` (assert exact gcode strings).

---

### `render/GraphView.kt` (MODIFY — view, streaming) — the ONLY genuinely new render work (D-05)

**Analog:** itself. EXTEND in place — do NOT fork (CONTEXT D-05 / RESEARCH anti-pattern). Generalize the
single path/paint to N pre-allocated, keep the allocation-free `onDraw` discipline.

**Current single-trace allocation contract to generalize** (`GraphView.kt` lines 40–55):
```kotlin
private val linePath = Path()                                  // → Array<Path> (one per drawn sensor)
private val areaPath = Path()
private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; ... }  // → Array<Paint>
private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
```

**Token push (recolor) to extend** (`GraphView.kt` lines 85–92) — `applyTokens` currently sets ONE color
from `t.accent`; extend to set per-trace colors (`t.heat` nozzle, `t.accent` bed, `t.violet`/`t.trace3`
chamber — see token gap below). No raw hex (THEME-01).

**`onDraw` discipline to preserve** (`GraphView.kt` lines 106–156) — `rewind()` each path per draw, never
`Path()`/allocate in `onDraw` (Pitfall 4). The N-trace version loops over traces, each rewinding its own
pre-allocated path. **Replace the per-frame window min/max Y auto-range** (lines 116–125) with a FIXED
Y-range (D-05 / G-1 fix) — e.g. 0–300 °C shared across all traces on one axis. Per Pitfall 4, consider
drawing the area-fill for the primary trace only (or none) to bound fill cost — but MEASURE (D-06).

**Pure sanitize helper** (`GraphView.kt` lines 183–212) — already host-tested by `GraphDownsampleTest.kt`;
apply per-trace. Extend that test for the N-trace + backfill mapper (`TemperatureStoreBackfillTest`).

### `render/GraphViewHost.kt` (MODIFY — interop seam, streaming)

**Analog:** itself (`GraphViewHost.kt` lines 27–43). Extend the `factory`/`update` interop to pass N
snapshots + N colors. The `update` block pushes tokens + data on every recomposition into the SAME view
instance (`factory` runs once — no recreation on theme/data change). Keep this exact structure; widen
`snapshot: FloatArray` → per-sensor snapshots and add the trace colors derived from `tokens`.

---

### State-model additions (pure reducers, unit-tested) — extend in place

**`state/PrinterState.kt`** (MODIFY) — add fields following the existing KDoc-per-field style (lines 20–65).
`toolheadPosition` (line 39) and `homedAxes` (line 42) already exist. ADD:
- `gcodePosition: List<Double>? = null` — `gcode_move.gcode_position` (MOVE-04, the user-facing source,
  NOT `toolheadPosition` — see RESEARCH Pitfall 1).
- `canExtrude` per extruder — extend `HeaterState` (lines 68–72) or add an `ExtruderState`/`Map<String,
  Boolean>`. RESEARCH Open Question 3 leaves the exact shape to the planner; keep it pure + boundary-tested.
- `minExtrudeTemp` is STATIC config — do NOT put it in the throttled hot path; stash on the store like a
  capability (RESEARCH §2, queried once at handshake from `configfile.settings.extruder.min_extrude_temp`).

**`state/PrinterStateReducer.kt`** (MODIFY) — the `applyStatus` walker (lines 64–118) is the analog. The
`toolhead` block (lines 87–90) and `gcode_move` block (lines 92–95) show the exact null-safe accessor idiom
(`doubleListOrNull`, `doubleOrNullAt`). ADD:
- in the `gcode_move` block: `gm.doubleListOrNull("gcode_position")?.let { s = s.copy(gcodePosition = it) }`
- in the heater merge loop (lines 102–112) or a new `extruder` read: capture `can_extrude`
  (`obj["can_extrude"]?.jsonPrimitive?.booleanOrNull`). Add a `booleanOrNull` accessor mirroring
  `doubleOrNullAt` (lines 151–152). Every walk null-safe, never `!!` (house rule).
- ADD `"gcode_move"` already in the subscribe set (`DeriveCapabilities.kt` line 54); `gcode_position` rides
  the same object — no subscribe-set change needed.

**`state/Capabilities.kt` + `state/DeriveCapabilities.kt`** (MODIFY):
- `Capabilities` (lines 10–35) already carries `extruderCount` (line 14, gates EXTR-03 tool selector) and
  `macros` (line 20). ADD a helper: `fun hasMacroIgnoreCase(name: String) = macros.any { it.equals(name,
  ignoreCase = true) }` (Pitfall 2 — Moonraker lowercases macro names; case-sensitive check WILL miss).
- `deriveCapabilities` (lines 20–41) extracts macro names verbatim (line 21) — the derivation stays; the
  case-insensitive comparison is the new helper. Test analog: `DeriveCapabilitiesTest.kt` (assert a
  lowercase `gcode_macro load_filament` is detected).

**`net/JsonRpc.kt`** (MODIFY) — `JsonRpcMethods` (lines 93–117) is the registry. ADD:
```kotlin
const val GCODE_SCRIPT = "printer.gcode.script"          // runs ALL action gcodes (RESEARCH §5)
const val TEMPERATURE_STORE = "server.temperature_store" // one-shot backfill (RESEARCH §1)
```
Follow the existing grouping comments (Requests / Action requests / Notifications).

---

### History backfill (TEMP-04, first history-endpoint use)

**Analog:** `net/MoonrakerSession.kt` `runHandshake()` (lines 247–271) — the one-shot
`identify → list → query → subscribe` sequence using `rpc.request(method, params)`. Add a one-shot
`rpc.request(JsonRpcMethods.TEMPERATURE_STORE)` AFTER subscribe completes, parse per-sensor `temperatures`
arrays (index 0 = OLDEST), and seed each drawn sensor's `RingBuffer` oldest→newest. Live points then arrive
via the existing `notify_status_update` stream (do NOT subscribe to the store). `JsonRpcClient.request`
signature (`JsonRpcClient.kt` lines 89–93): `suspend fun request(method, params: JsonElement? = null,
timeoutMs): JsonElement`. Pure mapper (store-arrays → ring) is host-testable (`TemperatureStoreBackfillTest`).

---

### Routing (extend `Dest` enum + tiles + shell)

**`ui/route/TopRoute.kt`** (MODIFY) — `enum class Dest { PrintStatus, Settings }` (line 30). Extend to
`{ PrintStatus, Temperature, Move, Extrude, Settings }`. (CONTEXT: "Extra panels are a one-line addition.")

**`ui/shell/AppDrawer.kt`** (MODIFY) — `DRAWER_TILES` list (lines 112–122). Wire the currently-greyed tiles:
`DrawerTileSpec(label = "Move", dest = null)` → `dest = Dest.Move`; same for "Temp" → `Dest.Temperature`.
A live tile (`dest != null`) auto-renders with the accent outline + becomes clickable (`DrawerTile`, lines
130–169) — no other change needed. ("Extrude" may share the "Tools" tile or get its own per the planner.)

**`ui/shell/AppShell.kt`** (MODIFY) — the lean `when (dest)` route holder (lines 97–103) is the analog.
Add `Dest.Temperature -> TemperatureScreen(...)`, `Dest.Move -> MoveScreen(...)`, `Dest.Extrude ->
ExtrudeScreen(...)`. Each needs its holder `remember(store)`'d off the live spine store exactly like
`PrintStatusHolder` (lines 76–80). NO Navigation-Compose (D-05).

---

## Shared Patterns

### Action dispatch (PRIM-05) — applies to ALL three screens, every action tap
**Source:** `command/CommandDispatcher.kt` (`dispatch` at lines 93–117) + `PrintStatusScreen.kt` call site
(line 170). EVERY move/heat/extrude/home/disable/tool tap goes through the per-session dispatcher; never a
raw `rpc.request` from a panel. Unique `key` per control drives in-flight/disabled state + debounce.
```kotlin
dispatcher?.dispatch(
    key = "jog_x_pos",                    // unique per control
    method = JsonRpcMethods.GCODE_SCRIPT,
    params = scriptParams(PrinterCommands.jog("X", +10.0, 3000)),
)
```
Dispatcher source-of-truth: `inFlight: StateFlow<Set<String>>` (line 72) for disabling controls;
`events: SharedFlow<DispatchEvent>` (line 79) for failure toasts. Constructed per session
(`CommandDispatcher(rpc, scope)`, secondary ctor lines 56–68); reached via `container.dispatcher`
(`AppContainer.kt` line 93).

### Theming — all color via LocalTokens (THEME-01), applies to every screen + GraphView
**Source:** `theme/compose/LocalTokens` (collected via `LocalTokens.current`), intent→color mapping
`designsystem/control/OutlinedControl.kt` `Intent.outlineColor` (lines 43–49: Neutral→outline,
Accent→accentLine, Warn→heat, Danger→stop, Go→go). Zero raw `Color(...)` outside `theme/BakedTokens.kt`.

### Layout — all screens on ScreenScaffold (UI-01), Focus/Field/Gutter
**Source:** `designsystem/layout/ScreenScaffold.kt` (lines 47–112). Handles portrait stack /
landscape Focus|Field 50/50 + full-width gutter automatically via `BoxWithConstraints`. Ratio-only sizing;
the only sanctioned fixed values are the `OutlinedControl` ≥64dp touch floor and `--fs` text.

### Failure / informational toast (PRIM-04)
**Source:** `designsystem/SeverityToast.kt` (lines 77–120). `Severity.Error` for dispatch failures
(red/×), `Severity.Info` for the missing-macro popup (D-10, blue/i). Color + icon + text — never color
alone. Host owns show/hide timing (see `PrintStatusScreen.kt` auto-dismiss `LaunchedEffect`, lines 108–113).

---

## Gaps the planner must close (no existing analog / additive)

| Item | Role | Why no analog | Resolution |
|------|------|---------------|------------|
| `violet` / `trace3` chamber token | config | `ThemeTokens` has NO 3rd sensor color (only `heat`+`accent`); README §9 calls for chamber=violet | Add a `violet` (or `trace3`) field to `ThemeTokens` (`ThemeTokens.kt` lines 25–92, follow the per-field KDoc) AND bake dark/light values in `BakedTokens.kt` (lines 33–60, the ONLY home for `Color(0x..)`). Re-bake via `tools/oklch-bake/bake_tokens.py`. NEVER hardcode a hex in `GraphView`. OR explicitly scope v1 to 2 traces and defer chamber (RESEARCH Open Q2). Decidable, small. |
| Override-cell unhomed-jog gcode | utility (gcode) | Not nailed by docs (RESEARCH Open Q1 / Pitfall 5 / A3) | Confirm the exact sequence LIVE on flox (likely `SET_KINEMATIC_POSITION` then relative `G1`); keep behind the amber Override affordance; surface gcode errors via `SeverityToast`. |
| `can_extrude` / `min_extrude_temp` state placement | model | Current `HeaterState` models only temp/target/power | Planner decides shape (RESEARCH Open Q3): `can_extrude` on a per-extruder model/`Map`; `min_extrude_temp` stashed on the store (capability-like, queried once), NOT the throttled hot path. |
| Multi-trace Adreno-320 perf re-measure (D-06) | test/gate | Phase-3 gate measured ONE isolated trace (50.1 ms) | MANDATE: re-run the two-part gate (liveness + p95 ≤ ~66 ms) on the FULL Temperature screen on real flox `0a64b42e`; do NOT grandfather. Document in `05-PERF-RESULTS.md` mirroring `03-PERF-RESULTS.md`. |

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/{ui,state,render,command,designsystem,net,theme,di}/`
**Files scanned:** 24 source + 5 test analogs
**Pattern extraction date:** 2026-05-31
