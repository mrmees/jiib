# Phase 17: Fine-Tune / Live-Adjust Panel - Pattern Map

**Mapped:** 2026-06-06
**Files analyzed:** 19 (7 new, 12 modified)
**Analogs found:** 19 / 19 (all in-repo; this is a wiring + new-screen phase on existing machinery)

## File Classification

| New/Modified File | New? | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|------|-----------|----------------|---------------|
| `ui/finetune/FineTuneHolder.kt` | NEW | holder (headless) | transform (StateFlow→Vm) | `ui/extrude/ExtrudeHolder.kt` | exact |
| `ui/finetune/FineTuneVm.kt` | NEW | view-model (data class) | transform | `ui/extrude/ExtrudeHolder.kt` (`ExtrudeVm`) | exact |
| `ui/finetune/FineTuneTile.kt` | NEW | component (shared) | request-response (±dispatch) | `ui/extrude/ExtrudeScreen.kt` (`SettingReadout`/`BigCommand`) | role-match |
| `ui/finetune/FineTuneHubScreen.kt` | NEW | screen (hub) | event-driven (nav) | `ui/calibration/CalibrationHubScreen.kt` | exact |
| `ui/finetune/MotionScreen.kt` | NEW | screen (group) | request-response | `ui/extrude/ExtrudeScreen.kt` | exact |
| `ui/finetune/ExtrusionScreen.kt` | NEW | screen (group) | request-response | `ui/extrude/ExtrudeScreen.kt` | exact |
| `ui/finetune/FwRetractionScreen.kt` | NEW | screen (mini, build-blind) | request-response | `ui/extrude/ExtrudeScreen.kt` | exact |
| `command/PrinterCommands.kt` | MOD | utility (pure builders) | transform | self (`jog`/`setGcodeOffsetZAdjust`/`extrude`) | exact |
| `command/CommandRegistry.kt` | MOD | config (command specs) | request-response | self (`babystepZ`/`extrude`/`testZ` `gcode()` specs) | exact |
| `state/PrinterState.kt` | MOD | model (immutable state) | transform | self (`speedFactor`/`gcodeZOffset` fields) | exact |
| `state/PrinterStateReducer.kt` | MOD | reducer | transform (diff-merge) | self (`toolhead`/`gcode_move` walks) | exact |
| `state/DeriveCapabilities.kt` | MOD | config (subscribe set) | transform | self (`V1_SUBSCRIBE_CORE`) | exact |
| `state/PrinterStateStore.kt` | MOD | store (one-shot StateFlows) | transform | self (`minExtrudeTemp`/`screwsTiltConfig`) | exact |
| `net/MoonrakerSession.kt` | MOD | service (handshake one-shot) | request-response | self (configfile `runCatching` block) | exact |
| `ui/route/TopRoute.kt` | MOD | route (enum) | event-driven | self (`Dest` enum) | exact |
| `ui/shell/AppShell.kt` | MOD | shell (nav arm) | event-driven | self (`Dest.Calibration` arm + sub-nav) | exact |
| `ui/shell/AppDrawer.kt` | MOD | shell (drawer tile) | event-driven | self (`DRAWER_TILES`) | exact |
| `ui/printstatus/PrintStatusScreen.kt` | MOD | screen (entry wire) | event-driven | self (`PrintStatusControlAction.Tune` stub) | exact |
| `res/drawable/*.xml` (11 new) | NEW | asset (vector drawable) | n/a | `res/drawable/add.xml`, `nozzle.xml` | exact |

**Wave-0 test extensions** (mirror existing siblings): `command/PrinterCommandsTest.kt`, `command/CommandRegistryGcodeTest.kt` (NOT `CommandRegistryTest` — the gcode-spec test is `CommandRegistryGcodeTest`), `state/PrinterStateReducerTest.kt`, new `ui/finetune/FineTuneHolderTest.kt` (mirror `ui/extrude/ExtrudeHolderTest.kt`), instrumented nav test.

---

## Pattern Assignments

### `ui/finetune/FineTuneHolder.kt` + `FineTuneVm.kt` (holder, transform)

**Analog:** `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeHolder.kt`

This is the closest template because Fine-Tune, like Extrude, needs to COMBINE the live throttled `printerState` with one-shot handshake StateFlows (the config baselines for reset). Use `combine(...)` exactly as ExtrudeHolder does (NOT the bare `store.printerState.collect` MoveHolder uses, since Fine-Tune needs the baselines folded in deterministically).

**Holder structure** (`ExtrudeHolder.kt:44-114`):
```kotlin
class ExtrudeHolder(
    scope: CoroutineScope,
    private val store: PrinterStateStore,
) {
    private val _vm = MutableStateFlow(ExtrudeVm())
    val vm: StateFlow<ExtrudeVm> = _vm.asStateFlow()

    init {
        // COMBINE the throttled state with the one-shot StateFlows so the baseline is
        // deterministic the instant those reads land (NOT dependent on a later status diff).
        scope.launch {
            combine(
                store.printerState,
                store.minExtrudeTemp,
                store.maxExtrudeDistance,
            ) { state, minTemp, maxDist ->
                buildVm(state, store.capabilities.value, minTemp, maxDist)
            }.collect { _vm.value = it }
        }
    }

    private fun buildVm(state: PrinterState, caps: Capabilities, ...): ExtrudeVm { ... }
}
```
For Fine-Tune: `combine(store.printerState, <each new baseline StateFlow>) { ... buildVm(...) }`. Caps come from `store.capabilities.value` (snapshot inside the combine — same as ExtrudeHolder line 68). NOTE Pitfall: if you add MANY baselines, `combine` overloads cap at a fixed arity — wrap baselines in a single `Baselines` data class StateFlow OR use the list-form `combine(flows) { array -> }`. The RESEARCH "Code Examples" section shows `buildVm(s, caps, baselines)` taking a single `baselines` arg — prefer that shape.

**Display-scaling in buildVm** (the #1 trap — ratio→percent, 0–1→percent). Compute display values here, never in the reducer. From RESEARCH Code Examples:
```kotlin
speedPct = (s.speedFactor * 100).roundToInt()                  // ratio → %
flowPct = (s.extrudeFactor * 100).roundToInt()                 // ratio → %
partFanPct = s.partFanSpeed?.let { (it * 100).roundToInt() }   // 0..1 → %
hasFan = caps.hasObject("fan")
hasFwRetraction = caps.hasObject("firmware_retraction")
```

**Vm data class** (`ExtrudeHolder.kt:125-139`): plain data class, all fields defaulted, NO Compose annotations (host-testable). Null = unreported → tile shows "—", never fabricate a 0.

---

### `ui/finetune/FineTuneTile.kt` (component, request-response)

**Analog:** `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt` (`SettingReadout` 430-473 + `FieldButton` 481-513 + `BigCommand` 393-423)

The tile is a NEW composable but composed entirely from established idioms in ExtrudeScreen. Key divergence from `SettingReadout`: the value cell is **tap-inert** and **long-press = reset** (D-14/D-16); the ± arrows are the action. Use `pointerInput { detectTapGestures(onLongPress = onReset) }` on the value cell (RESEARCH Pattern 1), NOT `.clickable`.

**Outline + token border idiom** (`ExtrudeScreen.kt:438-443`):
```kotlin
val t = LocalTokens.current
val shape = RoundedCornerShape(t.rCtrl)
Box(
    modifier
        .clip(shape)
        .border(BorderStroke(2.dp, t.outline), shape)
        .clickable(onClick = onClick),     // for the ± arrows; value cell uses detectTapGestures instead
    contentAlignment = Alignment.Center,
) { ... }
```

**Disabled-dim idiom for the busy-lock** (`ExtrudeScreen.kt:403-407`, `BigCommand`):
```kotlin
var box = modifier
    .alpha(if (disabled) 0.4f else 1f)
    .clip(shape)
    .border(BorderStroke(2.dp, if (disabled) t.hair else t.accentLine), shape)
if (!disabled) box = box.clickable(onClick = onClick)
```
For Fine-Tune the whole tile dims when `!enabled` (where `enabled == !groupBusy`).

**Per-icon `painterResource` (NOT MaterialSymbol font for the chosen glyphs)** (`ExtrudeScreen.kt:234-241`):
```kotlin
Icon(
    painter = painterResource(R.drawable.nozzle),
    contentDescription = null,
    tint = tempColor,
    modifier = Modifier.size(fsSp(48f, t.fs).dp),
)
```
Use `painterResource(R.drawable.<glyph>)` for the 11 new exported drawables (`speed`, `sprint`, `mode_fan`, etc.) and the existing `add`/`remove`. `MaterialSymbol(name=...)` exists but D-17 mandates per-icon vector drawables for the tile glyphs.

**Intent color (C1/C5 — arrows perform the screen's expected action = accent)** : the ± arrows are the natural physical action → use `t.accent2`/`t.accentLine` (THEMING C5), per the BigCommand accent treatment. See `docs/ui_design/THEMING.md` C1/C5.

**Font scale — MANDATORY** (recurring lesson `[[dinghy-font-sizes-too-small]]`): use `fsSp(baseSp, t.fs)`. ExtrudeScreen value hero = `fsSp(48f, t.fs).sp` (line 462), label = `fsSp(18f)`, metadata floor = `fsSp(15f)` (line 524). GeistMono + tabular for live numerics.

---

### `ui/finetune/FineTuneHubScreen.kt` (screen, hub)

**Analog:** `app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt` (the hub-of-tiles → onNavigate pattern)

CONTEXT D-29 locks: TWO large entries only (Motion, Extrusion), NO summary values. CalibrationHubScreen is the exact precedent (a grid of routine tiles, each calling `onNavigate(routine)`):
```kotlin
@Composable
fun CalibrationHubScreen(
    holder: CalibrationHubHolder,
    onNavigate: (CalibrationRoutine) -> Unit,
    onBack: () -> Unit,
) { /* LazyVerticalGrid(GridCells...) of RoutineTile(entry, onClick = { onNavigate(entry.routine) }) */ }
```
Fine-Tune hub is simpler — only two entries — but reuse the tile grammar (outline tiles, `aspectRatio`, accent). Mirror `AppDrawer`'s `DrawerTile` grammar as CalibrationHubScreen does.

---

### `ui/finetune/MotionScreen.kt` / `ExtrusionScreen.kt` / `FwRetractionScreen.kt` (screen, request-response)

**Analog:** `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt`

Mirror the full `ExtrudeScreen` skeleton: `ScreenScaffold(focus/field/gutter)`, the dispatcher wiring, the in-flight collection, the dispatch funnel, and the Back gutter.

**Signature** (`ExtrudeScreen.kt:117-122`): `(container: AppContainer, holder: FineTuneHolder, onBack: () -> Unit, modifier)`.

**Dispatcher + in-flight wiring** (`ExtrudeScreen.kt:123-127`):
```kotlin
val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
val inFlight by remember(dispatcher) {
    dispatcher?.inFlight ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet())
}.collectAsStateWithLifecycle(initialValue = emptySet())
val vm by holder.vm.collectAsStateWithLifecycle()
```

**Whole-group busy-lock (D-15 — STRONGER than ExtrudeScreen's per-key check)**. RESEARCH Pattern 2:
```kotlin
val groupBusy = inFlight.isNotEmpty()    // any in-flight key disables ALL tiles
// every tile: enabled = !groupBusy
```
This DIVERGES from ExtrudeScreen's per-key `"extrude" in inFlight` (line 359). D-15 mandates locking the entire group, so gate every tile on `inFlight.isEmpty()`, not on its own key.

**Dispatch funnel** (`ExtrudeScreen.kt:168-171`) — copy verbatim:
```kotlin
fun <P> dispatchCommand(command: CommandSpec<P>, args: P) {
    if (command.dispatchKey(args) in inFlight) return
    dispatcher?.dispatch(command, args)
}
```

**Compute target from current displayed value, then dispatch** (D-14 anti-pattern: NO optimistic local state). The tile's `onIncrement` reads the live `vm` value, adds the step, dispatches; the readout flips only when the reducer reports back. See RESEARCH Pitfall 5 + "Anti-Patterns to Avoid".

**Gutter Back = Neutral intent** (`ExtrudeScreen.kt:283-288`) — Back spends no safety color (15.2 C7 Back=Neutral law):
```kotlin
OutlinedControl(label = "Back", onClick = onBack, intent = Intent.Neutral)
```

**Failure toast** (`ExtrudeScreen.kt:144-158`): collect `dispatcher.events`, surface `DispatchEvent.Failure.message` as a `SeverityToast(Severity.Error, ...)`, auto-dismiss after 4s. Out-of-range rejections from Klipper land here (RESEARCH Pitfall 5 / G1 lesson).

**FwRetractionScreen is BUILD-BLIND** (RESEARCH Pitfall 3): gates OFF on both dev printers (no `firmware_retraction` object). Mirror `QUAD_GANTRY_LEVEL` — code-reasoned + host-fixture-tested only, NO on-device UAT gate. The Extrusion screen surfaces the FW-retraction ENTRY only when `vm.hasFwRetraction`.

---

### `command/PrinterCommands.kt` (utility, pure builders)

**Analog:** self — `jog()` (150-155), `extrude()` (195-199), `setGcodeOffsetZAdjust()` (231-241), `formatZ()` (300-304)

**Clamp-before-format + named consts (ASVS V5)** — every new builder follows this. Add named bound consts at the top (the `MAX_TEMP_C`/`MAX_JOG_MM` block, lines 29-59). RESEARCH supplies the suggested MIN/MAX/step table.

**Builder idiom** (mirror `setGcodeOffsetZAdjust` for the Double formatting, `jog` for clamping):
```kotlin
fun speedFactor(pct: Int): String = "M220 S${pct.coerceIn(SPEED_PCT_MIN, SPEED_PCT_MAX)}"
fun setFan(pct: Int): String =
    "M106 S${(pct.coerceIn(0, 100) / 100.0 * 255).roundToInt()}"   // 0..100% → 0..255
```

**`Locale.US` + trailing-zero strip for Doubles** (`PrinterCommands.kt:300-304`) — reuse the `formatZ` pattern (rename to a generic `fmt` or per-precision helper) for PA/smooth-time/SCV/min-cruise-ratio so a locale comma (`0,05`) never reaches gcode:
```kotlin
private fun formatZ(v: Double): String {
    var s = String.format(Locale.US, "%.2f", v)
    if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
    return s
}
```
PA needs up to 3dp (`%.3f`); smooth-time/SCV/min-cruise 2dp — parameterize precision.

**`SET_VELOCITY_LIMIT` multi-field builder** (RESEARCH Pattern 3) — one field set per dispatch:
```kotlin
fun setVelocityLimit(velocity: Double? = null, accel: Double? = null,
                     minCruiseRatio: Double? = null, scv: Double? = null): String =
    buildString {
        append("SET_VELOCITY_LIMIT")
        velocity?.let { append(" VELOCITY=${fmt(it.coerceIn(VEL_MIN, VEL_MAX))}") }
        accel?.let { append(" ACCEL=${fmt(it.coerceIn(ACCEL_MIN, ACCEL_MAX))}") }
        minCruiseRatio?.let { append(" MINIMUM_CRUISE_RATIO=${fmt(it.coerceIn(0.0, 1.0))}") }
        scv?.let { append(" SQUARE_CORNER_VELOCITY=${fmt(it.coerceIn(SCV_MIN, SCV_MAX))}") }
    }
```
NOTE `scriptParams()` (line 281) already wraps a string into `{"script": ...}` — builders return plain strings; the `gcode()` factory calls `scriptParams` for you.

---

### `command/CommandRegistry.kt` (config, command specs)

**Analog:** self — `babystepZ` (526-531), `extrude` (379-384), `testZ` (490-495), the `gcode()` factory (624-637)

**Arg data classes** (mirror `BabystepArgs` line 62, `ExtrudeArgs` line 25) — add e.g. `VelocityLimitArgs`, `PressureAdvanceArgs`, `FanArgs`, `RetractionArgs`, `SpeedFactorArgs`. Pattern:
```kotlin
data class BabystepArgs(val deltaMm: Double)
```

**`gcode()` spec idiom** (`CommandRegistry.kt:526-531`) — copy this shape per new command:
```kotlin
val babystepZ: CommandSpec<BabystepArgs> = gcode(
    catalogId = "KGC-SET_GCODE_OFFSET",
    key = { "babystep" },
    gcode = { PrinterCommands.setGcodeOffsetZAdjust(it.deltaMm) },
    availability = AvailabilityPredicate.ObjectPresent("gcode_move"),
)
```

**Distinct dispatchKey per motion-limit field** (RESEARCH Pitfall 4) — do NOT share `"set_vel_limit"`:
```kotlin
key = { args -> "set_vel_${args.field}" },   // "set_vel_velocity", "set_vel_accel", ...
```
The `extrude` spec already shows per-arg keys (`if (args.mm < 0) "retract" else "extrude"`, line 381).

**Availability = ObjectPresent (gate on the OWNING object, never a help-list)** (RESEARCH Capability-Gate Predicates): `toolhead` for motion limits, `extruder` for PA/smooth, `fan` for part-fan, `gcode_move` for speed/flow, `firmware_retraction` for retraction. The `quadGantryLevel` spec (438-444) is the build-blind precedent for `firmware_retraction`.

**`SET_PRESSURE_ADVANCE`/`SET_RETRACTION` are built-in commands** — gate on the OBJECT, not `GcodeCommandPresent` (which is only used by FORCE_MOVE-class specs and is NOT runtime-evaluated; see `forceMove` line 355).

**Register in `CommandRegistry.all`** (list begins line 544) — the `CommandRegistryGcodeTest` asserts membership; add each new spec or the test fails.

---

### `state/PrinterState.kt` + `PrinterStateReducer.kt` (model + reducer, transform)

**Analog:** self — `PrinterState.kt:61-72` (`speedFactor`/`gcodeZOffset`), `PrinterStateReducer.kt:101-114` (toolhead/gcode_move walks), `194-224` (heater + temperature_sensor merge loops)

**New nullable fields** (mirror `gcodeZOffset` line 72 — nullable, never fabricated):
```kotlin
val maxVelocity: Double? = null
val maxAccel: Double? = null
val minimumCruiseRatio: Double? = null
val squareCornerVelocity: Double? = null
val pressureAdvance: Double? = null
val smoothTime: Double? = null
val partFanSpeed: Double? = null              // fan.speed, 0.0..1.0
val firmwareRetraction: FirmwareRetractionObject? = null
```

**Null-safe object-walk idiom** (`PrinterStateReducer.kt:101-114`) — extend the EXISTING `toolhead` and `gcode_move` blocks; add `fan`/`firmware_retraction`:
```kotlin
status.objectOrNull("toolhead")?.let { th ->
    th.doubleOrNullAt("max_velocity")?.let { s = s.copy(maxVelocity = it) }
    th.doubleOrNullAt("max_accel")?.let { s = s.copy(maxAccel = it) }
    th.doubleOrNullAt("minimum_cruise_ratio")?.let { s = s.copy(minimumCruiseRatio = it) }
    th.doubleOrNullAt("square_corner_velocity")?.let { s = s.copy(squareCornerVelocity = it) }
}
status.objectOrNull("fan")?.doubleOrNullAt("speed")?.let { s = s.copy(partFanSpeed = it) }
```
PA/smooth-time live on the `extruder` object but are NOT per-heater concepts — read them in a SEPARATE `extruder`-keyed walk (NOT the heater merge loop at 195-207), per RESEARCH. For `firmware_retraction`, use a field-by-field merge-onto-retained (like the `bed_mesh`/`manual_probe` blocks 154-192) so a partial diff retains omitted fields.

**Reducer stores RAW ratios/0-1 — scale ONLY at display/command boundary** (RESEARCH Pitfall 1, "Anti-Patterns"). `speedFactor` stays `1.05`; the holder/builder converts to `S105`.

---

### `state/DeriveCapabilities.kt` (config, subscribe set)

**Analog:** self — `V1_SUBSCRIBE_CORE` (50-68), the dynamic `fan` clause (89-92)

`toolhead`/`gcode_move`/`extruder`/`fan` are ALREADY subscribed (fan via the dynamic `name == "fan"` clause line 89). **Add `firmware_retraction` to `V1_SUBSCRIBE_CORE`** (line 50-68) — the intersect loop (81-83) already guards absence, so it's safe to add unconditionally:
```kotlin
"firmware_retraction",  // P17 Fine-Tune (gated off on both test printers — build-blind)
```

---

### `state/PrinterStateStore.kt` + `net/MoonrakerSession.kt` (store + service, config baselines)

**Analog:** `PrinterStateStore.kt:70-76,116-128` (`minExtrudeTemp`/`screwsTiltConfig` one-shot StateFlows + `setMinExtrudeTemp` setters 204-257); `MoonrakerSession.kt:490-527` (the configfile `runCatching` block)

**One-shot StateFlow + setter idiom** (`PrinterStateStore.kt:70-76`):
```kotlin
private val _minExtrudeTemp = MutableStateFlow<Float?>(null)
val minExtrudeTemp: StateFlow<Float?> = _minExtrudeTemp.asStateFlow()
fun setMinExtrudeTemp(value: Float?) { _minExtrudeTemp.value = value }
```
Add one per baseline (max_velocity / max_accel / min_cruise / scv / pressure_advance / smooth_time / retraction).

**Read baselines in the EXISTING configfile block — NO second query** (RESEARCH Don't-Hand-Roll / Pitfall 3). `MoonrakerSession.kt:490-527` already fetches `configfile.settings` once; add `store.set*` calls in the SAME `runCatching`:
```kotlin
val printerCfg = settings?.objectOrNull("printer")
store.setBaselineMaxVelocity(printerCfg?.floatOrNullAt("max_velocity"))
store.setBaselineMaxAccel(printerCfg?.floatOrNullAt("max_accel"))
store.setBaselineMinCruise(printerCfg?.floatOrNullAt("minimum_cruise_ratio"))
store.setBaselineScv(printerCfg?.floatOrNullAt("square_corner_velocity"))
val extruderCfg = settings?.objectOrNull("extruder")  // ALREADY read at line 504
store.setBaselinePressureAdvance(extruderCfg?.floatOrNullAt("pressure_advance"))
store.setBaselineSmoothTime(extruderCfg?.floatOrNullAt("pressure_advance_smooth_time"))  // ⚠ config key ≠ status field
```
**⚠ Smooth-time config-key mismatch (RESEARCH Pitfall 2):** live status field = `smooth_time`, config key = `pressure_advance_smooth_time`. Read the baseline from `pressure_advance_smooth_time`.

**Speed/flow reset = protocol 100%** (D-16): `M220 S100`/`M221 S100`, no baseline read. **Part-fan reset (A2/Open-Q1): NO baseline exists** — recommendation is NO reset affordance on the part-fan tile; confirm with owner at plan time.

---

### Navigation wiring (route + shell + drawer + entry)

**Analog:** `TopRoute.kt:37` (`Dest` enum), `AppShell.kt:150-155,542-585` (Calibration hub→routine local back-stack), `AppDrawer.kt:144-156` (`DRAWER_TILES`), `PrintStatusScreen.kt:260` (Tune stub)

**`Dest.FineTune`** — add to the enum (`TopRoute.kt:37`):
```kotlin
enum class Dest { PrintStatus, ..., Settings, About, FineTune }
```

**AppShell arm with LOCAL sub-nav (mirror Calibration)** — Fine-Tune Hub → Motion/Extrusion/FwRetraction is a local back-stack WITHIN `Dest.FineTune`, NOT four top-level Dests. Copy the `Dest.Calibration` arm (`AppShell.kt:542-585`) + the hoisted `nav.calibrationRoutine` sub-state (lines 150-155) + the `BackHandler` pop (line 406):
```kotlin
Dest.FineTune -> when (val group = nav.fineTuneGroup) {     // null = hub
    null -> FineTuneHubScreen(holder = fineTuneHolder, onNavigate = { nav.fineTuneGroup = it }, onBack = { goBack() })
    FineTuneGroup.MOTION -> MotionScreen(container, fineTuneHolder, onBack = { nav.fineTuneGroup = null })
    FineTuneGroup.EXTRUSION -> ExtrusionScreen(...)
    FineTuneGroup.FW_RETRACTION -> FwRetractionScreen(...)
}
```
Build `FineTuneHolder` next to the other panel holders (`AppShell.kt:171+`, where `extrudeHolder`/`moveHolder`/`calibrationHubHolder` are `remember(store)`'d).

**Entry from Print-Status Tune** (`PrintStatusScreen.kt:260`) — replace the stub:
```kotlin
PrintStatusControlAction.Tune -> onNavigate(Dest.FineTune)   // was: -> Unit
```

**Drawer tile (planner's call, D-21)** — add to `DRAWER_TILES` (`AppDrawer.kt:144`) with a unique glyph (icon-no-repeat law; `tune` is taken by Calibration). Live `dest = Dest.FineTune`.

---

### Icons: `res/drawable/*.xml` (asset)

**Analog:** `res/drawable/add.xml`, `res/drawable/nozzle.xml`

11 new vector drawables to export (`add`/`remove` already exist). D-17 names them (`speed`, `arrow_shape_up_stack_2`, `sprint`, `directions_boat`, `rounded_corner`, `output_circle`, `text_select_move_forward`, `avg_time`, `mode_fan`, `input_circle`, `keyboard_return`). Source from Material Symbols Outlined per `img/material-icon-bucket.json`.

**Drawable shape** (`add.xml`) — white fill, recolored at runtime via `Icon(tint=...)` to honor tokens:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="..." android:fillColor="#FFFFFFFF" />
</vector>
```
Render via `painterResource(R.drawable.<name>)` + `Icon(tint = t.<role>)` (NOT the `MaterialSymbol` font path).

---

### Wave-0 test extensions

**Analogs:** `command/PrinterCommandsTest.kt`, `command/CommandRegistryGcodeTest.kt`, `state/PrinterStateReducerTest.kt`, `ui/extrude/ExtrudeHolderTest.kt`

**New `FineTuneHolderTest.kt`** — mirror `ExtrudeHolderTest.kt:34-54`: `runTest(UnconfinedTestDispatcher())`, drive a real `PrinterStateStore`, `store.setCapabilities(...)` + `store.seed(PrinterState(...))`, `runCurrent()`, assert `holder.vm.value`. Cover: ratio→% scaling (1.05→105), 0–1→% fan scaling, caps gates, baseline folding, busy-lock derivation.

**`PrinterCommandsTest`** — assert exact gcode incl. scaling: `M220` from 1.05 ratio = `S105`; `M106` from 60% = `S153`; `SET_VELOCITY_LIMIT` single-field; `Locale.US` formatting.

**`CommandRegistryGcodeTest`** — new specs in `.all`, correct `availability` predicate.

**`PrinterStateReducerTest`** — new field walks + a SYNTHETIC `firmware_retraction` fixture (the only way to test build-blind retraction) + diff-merge retention.

**RECURRING TRAP — Wave-0 RED scaffolds must compile** (`[[dinghy-wave0-red-scaffold-compile]]`): Gradle compiles the WHOLE test sourceset before the `--tests` filter; RED stubs must only reference symbols introduced in the SAME plan (use `fail()` bodies, no forward-refs to a later wave's symbols).

---

## Shared Patterns

### Immediate-dispatch + state-flip confirm (NO ConfirmGuard)
**Source:** `ui/extrude/ExtrudeScreen.kt:168-171` (funnel), `ui/move/MoveHolder.kt` (render reduced state only)
**Apply to:** all three group screens + the tile. One command per tap, dispatch immediately, confirm by the reduced `PrinterState` value flipping — never optimistic local state (D-14, RESEARCH Pitfall 5).

### Clamp-before-format, `Locale.US`, named-const bounds (ASVS V5)
**Source:** `command/PrinterCommands.kt:29-59` (consts), `150-155` (`jog` clamp), `300-304` (`formatZ`)
**Apply to:** every new builder in `PrinterCommands.kt`. All Fine-Tune inputs are bounded ±-nudges (no keyboard) — still `coerceIn` every numeric before formatting.

### Capability gating via `hasObject` (no central evaluator)
**Source:** `state/Capabilities.kt:52` (`hasObject`), `command/CommandRegistry.kt:438-444` (`quadGantryLevel` build-blind precedent)
**Apply to:** every tile + the FW-retraction screen. Gate on the owning object name; FW-retraction gates OFF on dev hardware (build-blind, fixture-tested only).

### Token-driven outline controls + `fsSp` font scale
**Source:** `ui/extrude/ExtrudeScreen.kt:438-443` (border/clip/token), `fsSp(48f, t.fs)` heroes
**Apply to:** the tile + all screens. `LocalTokens.current`, `RoundedCornerShape(t.rCtrl)`, `BorderStroke(2.dp, t.outline)`, GeistMono + tabular for live numerics, `fsSp` floors (15sp metadata / 18sp body / 48sp focus value).

### One-shot configfile handshake read (no duplicate query)
**Source:** `net/MoonrakerSession.kt:490-527`, `state/PrinterStateStore.kt:70-76`
**Apply to:** all reset baselines — add `store.set*` calls inside the existing `runCatching` block; expose via one-shot StateFlows the holder COMBINEs (deterministic, not snapshot).

### Back = Neutral intent
**Source:** `ui/extrude/ExtrudeScreen.kt:283-288`
**Apply to:** every group-screen + hub gutter Back (15.2 C7 law).

---

## No Analog Found

None. Every surface maps to an in-repo analog. The ONLY genuinely-novel pieces are:
- The **value-tile interaction semantics** (tap-inert, long-press-reset) — assembled from existing `SettingReadout`/`BigCommand`/`pointerInput` idioms but no exact composite exists. Built fresh per RESEARCH Pattern 1.
- The **whole-group busy-lock** (D-15) — STRONGER than ExtrudeScreen's per-key check; RESEARCH Pattern 2 gives the exact `inFlight.isNotEmpty()` derivation.

---

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/{ui/extrude,ui/move,ui/calibration,calibration,command,state,net,ui/route,ui/shell,ui/printstatus}/`, `app/src/main/res/drawable/`, `app/src/test/java/works/mees/dinghy/{command,state,ui/extrude}/`
**Files scanned:** ~22 source + test files read; RESEARCH.md pre-resolved exact line numbers (HIGH confidence)
**Pattern extraction date:** 2026-06-06
