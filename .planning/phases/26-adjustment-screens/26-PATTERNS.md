# Phase 26: Adjustment Screens — Pattern Map

**Mapped:** 2026-06-10
**Files analyzed:** 17 new/modified files
**Analogs found:** 17 / 17

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `designsystem/components/IncrementPicker.kt` | component (new) | request-response | `designsystem/components/FootButtonBar.kt` + `designsystem/control/OutlinedControl.kt` | role-match |
| `designsystem/components/AdjusterPanel.kt` | component (new) | request-response | `designsystem/components/DetailCard.kt` + `designsystem/ScrubberPage.kt` gutter zone | role-match |
| `ui/finetune/FineTuneScreen.kt` | screen (new replaces 4 files) | CRUD + request-response | `ui/spool/SpoolScreen.kt` (FieldMode + DetailCard Focus) | exact |
| `ui/finetune/FineTuneHolder.kt` | holder (UNCHANGED API) | event-driven + request-response | self | self |
| `ui/finetune/FineTuneShared.kt` | utility (PRESERVED) | transform | self | self |
| `ui/finetune/FineTuneVm.kt` | model (UNCHANGED) | transform | self | self |
| `ui/temperature/TemperatureScreen.kt` | screen (rebuild) | streaming + request-response | `ui/spool/SpoolScreen.kt` (FieldMode morph) | exact |
| `ui/temperature/TemperatureHolder.kt` | holder (EXTEND) | streaming | self | self |
| `render/GraphViewHost.kt` | view-host (EXTEND carefully) | streaming | self | self |
| `ui/outputs/OutputsScreen.kt` | screen (rebuild) | CRUD + request-response | `ui/spool/SpoolScreen.kt` (Detail-in-Focus shape) | exact |
| `ui/outputs/OutputScrubberDetail.kt` | component (DELETE, logic migrates) | request-response | self → migrates to OutputsScreen Focus | self |
| `ui/outputs/OutputPinDetail.kt` | component (DELETE, logic migrates) | request-response | self → migrates to OutputsScreen Focus | self |
| `ui/outputs/OutputLedDetail.kt` | component (DELETE, logic migrates) | request-response | self → migrates to OutputsScreen Focus | self |
| `ui/extrude/ExtrudeScreen.kt` | screen (conformance-only) | request-response | `ui/spool/SpoolScreen.kt` (FootButtonBar pattern) | role-match |
| `ui/spool/SpoolScreen.kt` | screen (add D-08 Field-takeover) | CRUD | self | self |
| `designsystem/NumpadPage.kt` | screen (DELETE as destination) | request-response | n/a — retired | n/a |
| `ui/spool/MeasuredWeightPage.kt` | screen (replace with IME takeover) | request-response | `ui/macros/BookmarkedMacrosScreen.kt` FieldMode.ParamEntry | role-match |

---

## Pattern Assignments

### `designsystem/components/IncrementPicker.kt` (component, new)

**Analog:** `designsystem/components/FootButtonBar.kt` + `designsystem/control/OutlinedControl.kt`

**Imports pattern** (FootButtonBar.kt lines 1–11, OutlinedControl usage from SpoolScreen.kt):
```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import kotlinx.collections.immutable.ImmutableList
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
```

**Core pattern** (FootButtonBar.kt lines 66–79 — identical structural Row, different semantic):
```kotlin
@Composable
fun IncrementPicker(
    steps: ImmutableList<Double>,   // e.g. persistentListOf(0.001, 0.005, 0.01) for PA
    activeStep: Double,
    onSelect: (Double) -> Unit,
    uDp: Dp,                        // from rememberUnitGrid — enforces ≥64dp touch floor
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = uDp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        steps.forEach { step ->
            OutlinedControl(
                label = formatStep(step),           // pure formatting: "±0.001", "±5", etc.
                onClick = { onSelect(step) },
                modifier = Modifier.weight(1f),
                intent = if (step == activeStep) Intent.Accent else Intent.Neutral,
            )
        }
    }
}

/** Format an increment step for display: drop .0 on whole numbers, show necessary decimals. */
internal fun formatStep(step: Double): String =
    if (step == step.toLong().toDouble()) "±${step.toLong()}"
    else "±${step.toBigDecimal().stripTrailingZeros().toPlainString()}"
```

**Key constraints:**
- Use `Intent.Accent` for the active step; `Intent.Neutral` for inactive (intent law — accent = active selection).
- Use `uDp` as the `heightIn` minimum — never hardcode a Dp touch floor.
- `steps` param uses `ImmutableList` from `kotlinx-collections-immutable` — stable for Compose skip.

**Test scaffold needed:**
```kotlin
// designsystem/components/IncrementPickerTest.kt
@Test fun formatStep_wholeNumber() = assertEquals("±5", formatStep(5.0))
@Test fun formatStep_decimalPA() = assertEquals("±0.001", formatStep(0.001))
@Test fun activeStep_usesAccentIntent() { /* assert Intent.Accent when step == activeStep */ }
```

---

### `designsystem/components/AdjusterPanel.kt` (component, new)

**Analog:** `designsystem/ScrubberPage.kt` (zone structure), `designsystem/components/DetailCard.kt` (surface container)

**Imports pattern** (ScrubberPage.kt lines 1–37 + FineTuneShared.kt lines 1–7):
```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.ui.finetune.DASH
import works.mees.dinghy.ui.finetune.fmtValue
```

**Core pattern — 3 zones** (sketch-003 spec from RESEARCH.md Pattern 1):
```kotlin
@Composable
fun AdjusterPanel(
    icon: DinghyIcon,
    name: String,
    value: Double?,          // null → DASH; controls disabled
    unit: String,
    baseline: Double?,       // captured on entry; null = no Reset affordance
    decimals: Int,
    onDecrement: () -> Unit, // caller pre-clamps + calls markPending
    onIncrement: () -> Unit,
    onReset: (() -> Unit)?,  // null when baseline is null
    enabled: Boolean,        // busy-lock from holder feeds this
    incrementPicker: @Composable () -> Unit,  // caller provides IncrementPicker
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        // Zone 1 — Header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DinghyIconView(icon = icon, tint = t.accent2, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(name, fontFamily = Geist, fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(18f, t.fs).sp, color = t.text)
            Spacer(Modifier.weight(1f))
            onReset?.let { reset ->
                OutlinedControl(
                    label = "Reset", onClick = reset,
                    modifier = Modifier.heightIn(min = 40.dp),
                    intent = Intent.Warn,   // caution/amber — D-21
                )
            }
        }
        // Zone 2 — Value + inline "was X" baseline (centered, weight(1f))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (value == null) DASH else fmtValue(value, decimals) + unit,
                    fontFamily = GeistMono, fontWeight = FontWeight.Bold,
                    fontSize = fsSp(48f, t.fs).sp, color = t.text,
                )
                if (shouldShowBaseline(value, baseline, decimals)) {
                    Text(
                        text = "  was ${fmtValue(baseline!!, decimals)}$unit",
                        color = t.text3, fontFamily = GeistMono,
                        fontSize = fsSp(18f, t.fs).sp,  // inline, same baseline row — NEVER stacked
                    )
                }
            }
        }
        // Zone 3 — Stepper + IncrementPicker (bottom)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedControl(
                    label = "−", onClick = onDecrement, modifier = Modifier.weight(1f),
                    intent = Intent.Accent, enabled = enabled && value != null,
                )
                OutlinedControl(
                    label = "+", onClick = onIncrement, modifier = Modifier.weight(1f),
                    intent = Intent.Accent, enabled = enabled && value != null,
                )
            }
            incrementPicker()
        }
    }
}
```

**Baseline predicate — host-testable pure function** (RESEARCH.md Code Examples):
```kotlin
// Extract as internal top-level for AdjusterPanelTest
internal fun shouldShowBaseline(value: Double?, baseline: Double?, decimalPrecision: Int): Boolean {
    if (value == null || baseline == null) return false
    val factor = 10.0.pow(decimalPrecision)
    return ((value * factor).roundToInt()) != ((baseline * factor).roundToInt())
}
```

**CRITICAL anti-pattern:** Never stack "was X" as a second Text row below the value. Must be inline on the SAME Row/baseline. On 5U phone-landscape the Focus has ~5 rows — a stacked baseline pushes stepper controls off-screen.

---

### `ui/finetune/FineTuneScreen.kt` (screen, new — replaces Hub + 3 group screens)

**Analog:** `ui/spool/SpoolScreen.kt` — the Detail-in-Focus / FieldMode pattern is the exact template.

**Two-overload stateless seam** (SpoolScreen.kt lines 98–120 pattern):
```kotlin
// LIVE overload
@Composable
fun FineTuneScreen(
    holder: FineTuneHolder,
    dispatcher: CommandDispatcher,
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm by holder.vm.collectAsStateWithLifecycle()
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val printerState by container.printerState.collectAsStateWithLifecycle(PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing || ...
    holder.setInFlight(inFlight)

    Box(modifier.fillMaxSize()) {
        FineTuneContent(vm = vm, isPrinting = isPrinting, …)
    }
}

// STATELESS preview seam
@Composable
fun FineTuneScreen(
    vm: FineTuneVm,
    isPrinting: Boolean = false,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) { … }
```

**ScreenScaffold + FieldMode + Focus/Field layout** (SpoolScreen.kt lines 289–442 pattern):
```kotlin
@Composable
private fun FineTuneContent(vm: FineTuneVm, …) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        val t = LocalTokens.current
        // D-04: remember last-adjusted param (session-only)
        var selectedTuner by remember { mutableStateOf<FineTuneTuner>(FineTuneTuner.SPEED) }
        var activeStep by remember { mutableStateOf(defaultStepFor(selectedTuner)) }

        ScreenScaffold(
            focus = {
                // Detail-in-Focus: AdjusterPanel (D-01)
                Box(Modifier.fillMaxWidth().weight(1f).padding(8.dp)) {
                    DetailCard(modifier = Modifier.fillMaxSize()) {
                        AdjusterPanel(
                            icon = iconForTuner(selectedTuner),
                            name = nameForTuner(selectedTuner),
                            value = vm.valueForTuner(selectedTuner),
                            unit = unitForTuner(selectedTuner),
                            baseline = vm.baselineForTuner(selectedTuner),
                            decimals = decimalsForTuner(selectedTuner),
                            onDecrement = { nudge(selectedTuner, -activeStep, vm, holder, dispatcher) },
                            onIncrement = { nudge(selectedTuner, +activeStep, vm, holder, dispatcher) },
                            onReset = vm.baselineForTuner(selectedTuner)?.let {
                                { nudge(selectedTuner, it, vm, holder, dispatcher) }
                            },
                            enabled = !vm.groupBusy,
                            incrementPicker = {
                                IncrementPicker(
                                    steps = stepsForTuner(selectedTuner),
                                    activeStep = activeStep,
                                    onSelect = { activeStep = it },
                                    uDp = grid.uDp,
                                )
                            },
                        )
                    }
                    // D-04 / P24: FineTune IS printing-list-valid — FloatingEStop shows when printing
                    FloatingEStop(
                        visible = isPrinting,
                        onClick = { showEstopGuard = true },
                        uDp = grid.uDp,
                        modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                    )
                }
            },
            field = {
                ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    // D-02: group identity = pool color on leading icon; no label words
                    items(visibleParams, key = { it.tuner.name }) { param ->
                        ListRow(
                            selected = param.tuner == selectedTuner,
                            onClick = { selectedTuner = param.tuner; activeStep = defaultStepFor(param.tuner) },
                            uDp = grid.uDp,
                            leadingContent = {
                                DinghyIconView(
                                    icon = param.icon,
                                    tint = param.groupColor,  // D-02: pool hue per group
                                    modifier = Modifier.size(22.dp).padding(end = 8.dp),
                                )
                            },
                            trailingContent = {
                                Text(
                                    text = currentValueText(vm, param.tuner),
                                    fontFamily = GeistMono,
                                    fontSize = fsSp(17f, t.fs).sp,
                                    color = t.text2,
                                )
                            },
                        ) {
                            Text(param.name, fontFamily = Geist, fontSize = fsSp(18f, t.fs).sp)
                        }
                    }
                }
                FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    OutlinedControl("", onBack, Modifier.weight(1f), Intent.Neutral, icon = DinghyIcons.ArrowBack)
                    OutlinedControl("Reset All", onResetAll, Modifier.weight(1f), Intent.Warn, icon = null)
                }
            },
            gutter = null,  // MANDATORY: redesigned screens null the gutter
        )
    }
}
```

**Clamp-routing nudge pattern** (FineTuneShared.kt lines 76–82 — the invariant from D-22):
```kotlin
// Every nudge MUST route through PrinterCommands.clamp* BEFORE markPending
fun nudge(tuner: FineTuneTuner, rawTarget: Double, vm: FineTuneVm, holder: FineTuneHolder, dispatcher: ...) {
    val clamped = PrinterCommands.clampForTuner(tuner, rawTarget)  // single source of truth
    holder.markPending(tuner, clamped)   // armed target == wire value (17-07 invariant)
    dispatcher?.dispatch(commandForTuner(tuner), argsForTuner(tuner, rawTarget))
}
// DO NOT: holder.markPending(tuner, rawTarget)  ← unclamped = P17 UAT Check-6 wedge
```

**FW-retraction capability gating** (P24 D-08 hide-not-grey):
```kotlin
// Visibility gate — hide rows entirely, do NOT show disabled/greyed
val visibleParams = ALL_FINE_TUNE_PARAMS.filter { param ->
    if (param.requiresFwRetraction) vm.hasFwRetraction else true
}
```

---

### `ui/finetune/FineTuneHolder.kt` (holder, UNCHANGED API)

**Analog:** self — no changes to public API.

**Load-bearing contract (read-only reference):**
```kotlin
// markPending — lines 169–197 (FineTuneHolder.kt)
// MUST survive the screen rebuild intact:
fun markPending(tuner: FineTuneTuner, target: Double) {
    val rounded = roundToWirePrecision(tuner, target)   // wire-precision rounding (WR-01)
    val current = currentFor(lastObservedState ?: store.printerState.value, tuner)
    if (current != null && abs(current - rounded) < toleranceFor(tuner)) {
        // skip-arm: no-op at cap, do NOT arm
        timeoutJob?.cancel(); _pendingStateFlip.value = null; return
    }
    timeoutJob?.cancel()
    val armed = PendingStateFlip(tuner, rounded, seq = ++flipSeq)  // monotonic seq token
    _pendingStateFlip.value = armed
    timeoutJob = scope.launch {
        delay(PENDING_FLIP_TIMEOUT_MS)
        if (_pendingStateFlip.value?.seq == armed.seq) _pendingStateFlip.value = null  // seq guard
    }
}

// toleranceFor — lines 230–244: step×0.1 per tuner (strict epsilon)
// wirePrecisionFor — lines 257–271: SCV=1dp, PA=3dp, smooth=2dp, others=0dp
// roundToWirePrecision — lines 274–277: (value * factor).roundToInt() / factor
```

---

### `ui/temperature/TemperatureScreen.kt` (screen, rebuild)

**Analog:** `ui/spool/SpoolScreen.kt` + current `ui/temperature/TemperatureScreen.kt`

**Two-overload stateless seam** (SpoolScreen.kt lines 98–257 pattern — same as all rebuilt screens):
```kotlin
// LIVE overload (consumes container + holder)
@Composable
fun TemperatureScreen(
    container: AppContainer,
    holder: TemperatureHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val series by holder.series.collectAsStateWithLifecycle()
    val setpoints by holder.setpoints.collectAsStateWithLifecycle()
    val legend by holder.legend.collectAsStateWithLifecycle()
    val traceColors by holder.traceColors.collectAsStateWithLifecycle()      // NEW — D-14
    val traceVisibility by holder.traceVisibility.collectAsStateWithLifecycle() // NEW — D-14
    val isPrinting = ...
    var selectedSensor by remember { mutableStateOf<SensorReadout?>(null) }
    var fieldMode by remember { mutableStateOf<TempFieldMode>(TempFieldMode.SensorList) }
    Box(modifier.fillMaxSize()) {
        TemperatureContent(series, setpoints, legend, traceColors, traceVisibility, selectedSensor, fieldMode, isPrinting, …)
    }
}
// STATELESS preview seam (all data as params, no holder, no dispatcher)
```

**ScreenScaffold — graph ↔ adjuster morph (D-10):**
```kotlin
// CRITICAL: simple if/else is the CORRECT morph (not AnimatedContent over a state that removes
// GraphViewHost from tree — that would thrash the AndroidView factory and lose trace data).
// The equality guards from Phase-22 D-12 only function correctly when factory runs once.
ScreenScaffold(
    focus = {
        if (selectedSensor == null) {
            // DEFAULT: graph fills Focus (GraphViewHost — Views-in-Compose, ADR-0001 Addendum-2)
            GraphViewHost(
                tokens = t,
                series = visibleSeries,      // filtered by traceVisibility
                setpoints = setpoints,
                yRange = graphRange,
                showAxisLabels = true,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // ADJUSTER: sensor selected — show AdjusterPanel (heater) or graph-controls (read-only)
            selectedSensor?.let { sensor ->
                TemperatureAdjusterFocus(
                    sensor = sensor,
                    traceColor = traceColors[sensor.name],
                    traceVisible = traceVisibility[sensor.name] ?: true,
                    onColorSelect = { color ->
                        // D-14: route through AppContainer.writeScope (NOT rememberCoroutineScope)
                        container.writeScope.launch { /* persist trace color */ }
                        holder.setTraceColor(sensor.name, color)
                    },
                    onVisibilityToggle = {
                        container.writeScope.launch { /* persist visibility */ }
                        holder.setTraceVisibility(sensor.name, !(traceVisibility[sensor.name] ?: true))
                    },
                    onDone = { selectedSensor = null },
                    vm = vm,
                    grid = grid,
                )
            }
        }
    },
    field = {
        when (fieldMode) {
            TempFieldMode.SensorList -> {
                // Sensor rows (D-11 typed row model)
                ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    items(legend, key = { it.name }) { sensor ->
                        ListRow(
                            selected = selectedSensor?.name == sensor.name,
                            onClick = { selectedSensor = if (selectedSensor?.name == sensor.name) null else sensor },
                            uDp = grid.uDp,
                            leadingContent = {
                                // D-14: row icon tinted to chosen trace color
                                DinghyIconView(
                                    icon = iconForSensor(sensor.name),
                                    tint = traceColors[sensor.name] ?: seriesColor(legend.indexOf(sensor)),
                                )
                            },
                            trailingContent = { /* current / target readout in GeistMono */ },
                        ) {
                            Text(sensor.label, fontFamily = Geist, fontSize = fsSp(18f, t.fs).sp)
                        }
                    }
                }
                FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    OutlinedControl("", onBack, Modifier.weight(1f), Intent.Neutral, icon = DinghyIcons.ArrowBack)
                    OutlinedControl("Presets", { fieldMode = TempFieldMode.PresetPicker },
                        Modifier.weight(1f), if (heating) Intent.Neutral else Intent.Accent)
                    OutlinedControl("Cooldown", { dispatchCommand(CommandRegistry.cooldown, Unit) },
                        Modifier.weight(1f), Intent.Warn)
                }
            }
            TempFieldMode.PresetPicker -> {
                // D-12: Field-takeover preset picker (scrim retires)
                ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    items(PrinterCommands.MATERIAL_PRESETS, key = { it.name }) { preset ->
                        ListRow(selected = false, onClick = { applyPreset(preset); fieldMode = TempFieldMode.SensorList }, uDp = grid.uDp) {
                            Text(preset.name, fontFamily = Geist, fontSize = fsSp(18f, t.fs).sp)
                        }
                    }
                }
                FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    OutlinedControl("", { fieldMode = TempFieldMode.SensorList }, Modifier.weight(1f), Intent.Neutral, icon = DinghyIcons.ArrowBack)
                }
            }
        }
    },
    gutter = null,
)
```

**TemperatureAdjusterFocus — 8-swatch color row (D-14):**
```kotlin
// The "minimum adjuster" for every selectable row (heaters add stepper on top):
// 1. show/hide trace toggle
// 2. inline row of 8 Colorful-pool swatches (D-14: ALWAYS from Colorful pool regardless of active palette)
// 3. for heaters only: AdjusterPanel with temp stepper

// Pool colors: t.pool has the COLORFUL pool (ThemeTokens.pool: List<Color>)
// Take first 8 (the colorful pool has ≥8 per COLOR-SYSTEM.md)
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    t.pool.take(8).forEachIndexed { idx, poolColor ->
        Box(
            Modifier
                .weight(1f)
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(poolColor)                            // data color — THEME-01 carve-out
                .border(
                    BorderStroke(if (poolColor == currentTraceColor) 3.dp else 1.dp, t.accentLine),
                    CircleShape,
                )
                .clickable { onColorSelect(poolColor) }
        )
    }
}
```

**Per-heater Off action (D-13 — P19 GAP-A precedent):**
```kotlin
// Off = dispatch SetHeaterArgs(name, target=0) — from P19 Off-overlay fix pattern
// Route through the AdjusterPanel's onReset analog OR a dedicated Off OutlinedControl in the foot
OutlinedControl(
    label = "Off", onClick = { dispatchHeaterOff(sensor.name) },
    modifier = Modifier.weight(1f),
    intent = Intent.Warn,   // amber — off is "proceed at peril" for a hot nozzle
)
```

---

### `ui/temperature/TemperatureHolder.kt` (holder, EXTEND)

**Analog:** self — extend, do NOT rebuild.

**A4/A5 assessment:** `series: StateFlow<List<FloatArray>>` uses positional indexing (parallel to `drawn: List<String>`). The `SensorReadout` data class (line 187) has `name` as a key field. Extension pattern for per-trace color/visibility:

```kotlin
// NEW StateFlows added to TemperatureHolder — extend, do not replace
private val _traceColors = MutableStateFlow<Map<String, Color>>(emptyMap())
val traceColors: StateFlow<Map<String, Color>> = _traceColors.asStateFlow()

private val _traceVisibility = MutableStateFlow<Map<String, Boolean>>(emptyMap())
val traceVisibility: StateFlow<Map<String, Boolean>> = _traceVisibility.asStateFlow()

// NEW mutators (called from screen, persist via AppContainer.writeScope)
fun setTraceColor(sensorName: String, color: Color) {
    _traceColors.value = _traceColors.value + (sensorName to color)
}
fun setTraceVisibility(sensorName: String, visible: Boolean) {
    _traceVisibility.value = _traceVisibility.value + (sensorName to visible)
}

// SensorReadout data class — ADD isAdjustable field (D-11 typed row model)
// CURRENT (TemperatureHolder.kt ~line 187):
// data class SensorReadout(val name: String, val label: String, val current: Double, val target: Double?)
// EXTENDED:
data class SensorReadout(
    val name: String,
    val label: String,
    val current: Double,
    val target: Double?,
    val isAdjustable: Boolean = true,  // D-11: false = read-only non-heater sensor (future)
)
```

**Existing `readout()` function (line 140–143) — update to set `isAdjustable`:**
```kotlin
private fun readout(state: PrinterState, name: String): SensorReadout {
    val h: HeaterState = state.heaters[name] ?: HeaterState()
    val target = if (h.target > 0.0) h.target else null
    return SensorReadout(name = name, label = label(name), current = h.temperature, target = target,
        isAdjustable = true)  // v1: all drawn sensors are heaters (adjustable)
}
```

**EQUALITY GUARD INVARIANT:** `GraphViewHost`'s `update` lambda must NOT receive new object references on every recomposition — only when data actually changes. The `traceColors` / `traceVisibility` extension must NOT be wired into `GraphViewHost.update` on every emission; the graph only needs to know about `visibleSeries` (filtered by visibility). Trace coloring in the graph remains token-based via `applyTokens` — the swatch color is shown in the row icon and the Focus panel, not in the graph itself (the graph continues to use the `seriesColor(i)` token sequence).

---

### `render/GraphViewHost.kt` (view-host, EXTEND carefully)

**Analog:** self — add per-trace color parameters carefully.

**Phase-22 equality guard pattern** (GraphViewHost.kt lines 89–100 — PRESERVE EXACTLY):
```kotlin
AndroidView(
    factory = { ctx -> GraphView(ctx) },  // runs ONCE — never re-runs on morph
    update = { view ->
        view.applyTokens(tokens)          // D-06: equality-guarded in GraphView.applyTokens
        view.drawArea = drawArea
        view.showAxisLabels = showAxisLabels
        view.yRange = yRange
        view.setData(series)              // pushes new samples, not new objects
        view.setSetpoints(setpoints)      // per-trace dashed lines
        // NEW for Phase 26: per-trace visibility filter (D-14)
        // series already pre-filtered by TemperatureScreen before passing here
        // (do NOT add new mutable parameters to GraphView for trace colors —
        //  graph trace colors remain token-based via applyTokens; only visibility matters)
    },
    modifier = modifier,
)
```

**Visibility approach:** filter `series` before passing to `GraphViewHost`, rather than adding a `visibility` param to `GraphViewHost`. This avoids disturbing the equality guards:
```kotlin
// In TemperatureContent — filter series by traceVisibility before passing to graph
val visibleSeries = remember(series, traceVisibility) {
    series.filterIndexed { i, _ ->
        traceVisibility[drawn.getOrNull(i)] ?: true
    }
}
// Pass visibleSeries to GraphViewHost — the graph never knows about visibility, it just draws what it receives
```

---

### `ui/outputs/OutputsScreen.kt` (screen, rebuild)

**Analog:** `ui/spool/SpoolScreen.kt` (Detail-in-Focus / Spoolman shape — exact match).

**Two-overload stateless seam** (SpoolScreen.kt lines 98–257 pattern):
```kotlin
// LIVE overload
@Composable
fun OutputsScreen(
    holder: OutputsHolder,
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows by holder.rows.collectAsStateWithLifecycle()
    var selectedKey by remember { mutableStateOf<String?>(null) }
    Box(modifier.fillMaxSize()) {
        OutputsContent(rows = rows, selectedKey = selectedKey, onSelect = { selectedKey = it }, onBack = onBack)
    }
}
```

**ScreenScaffold — Detail-in-Focus collapse** (D-18, mirrors SpoolScreen Focus pattern):
```kotlin
@Composable
private fun OutputsContent(rows: List<OutputRowVm>, selectedKey: String?, …) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        val t = LocalTokens.current
        val selectedRow = rows.firstOrNull { it.objectKey == selectedKey }

        ScreenScaffold(
            focus = {
                // Detail-in-Focus: selected output's control in place (D-18)
                selectedRow?.let { output ->
                    Box(Modifier.fillMaxWidth().weight(1f).padding(8.dp)) {
                        DetailCard(modifier = Modifier.fillMaxSize()) {
                            // Per-type inline control (see output control surface patterns below)
                            OutputFocusControl(output, holder, container, onBack = { selectedKey = null })
                        }
                    }
                } ?: run {
                    // No selection: empty Focus with prompt
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Select an output", color = t.text3, fontSize = fsSp(18f, t.fs).sp)
                    }
                }
            },
            field = {
                ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    items(rows, key = { it.objectKey }) { row ->
                        ListRow(
                            selected = row.objectKey == selectedKey,
                            onClick = { selectedKey = row.objectKey },
                            uDp = grid.uDp,
                            leadingContent = { DinghyIconView(icon = row.icon, tint = t.accent2, …) },
                            trailingContent = {
                                if (row.displayValue != null) {
                                    Text(row.displayValue, fontFamily = GeistMono, fontSize = fsSp(17f, t.fs).sp)
                                }
                            },
                        ) {
                            Text(row.prettyName, fontFamily = Geist, fontSize = fsSp(18f, t.fs).sp)
                        }
                    }
                }
                FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    OutlinedControl("", onBack, Modifier.weight(1f), Intent.Neutral, icon = DinghyIcons.ArrowBack)
                }
            },
            gutter = null,
        )
    }
}
```

**OutputFocusControl — dispatch patterns from OutputScrubberDetail.kt (lines 119–165):**
```kotlin
// FAN / PWM_PIN / PWM_TOOL → ScrubberPage OnSettle (D-05 — naturally bounded 0–100%)
// PRESERVE the exact OnSettle dispatch from OutputScrubberDetail.kt lines 127–153:
ScrubberPage(
    label = output.prettyName,
    value = currentPct.coerceIn(range.start, range.endInclusive),
    range = 0f..100f, step = 1f, unit = "%",
    actions = ScrubberActions.OnSettle(
        onSettle = { pct ->
            // markPending CLAMPED wire value (17-07 invariant):
            holder.markPending(output.objectKey, PrinterCommands.outputPctToWire(pct.roundToInt()))
            dispatchCommand(CommandRegistry.setGenericFan, SetGenericFanArgs(output.commandName, pct.roundToInt()))
        },
        onBack = { selectedKey = null },
        onOff = { dispatchOff(output) },
        offLabel = stringResource(R.string.output_off),
    ),
)

// output_pin (digital) → toggle OutlinedControl
OutlinedControl(
    label = if (isOn) stringResource(R.string.output_on) else stringResource(R.string.output_off),
    onClick = { dispatchToggle(output) },
    modifier = Modifier.fillMaxWidth().heightIn(min = grid.uDp),
    intent = if (isOn) Intent.Accent else Intent.Neutral,
)

// LED → brightness ScrubberPage + channel controls + ColorWheel (D-19)
// PRESERVE P19 GAP-B channel gating (OutputLedDetail.kt capability check)
```

**Scrubber build-once rule (P19 lesson — SC-3):**
```kotlin
// DO NOT key ScrubberPage on live value:
// WRONG: key(output.currentPct) { ScrubberPage(value = output.currentPct, …) }
// CORRECT: no key wrapper; let ScrubberPage hold its internal 'working' state
// The P19 fix (fa97efb): internal working state is seeded from value only via remember(value, range)
```

---

### `ui/extrude/ExtrudeScreen.kt` (screen, conformance-only — D-15)

**Analog:** `ui/spool/SpoolScreen.kt` (FootButtonBar + gutter=null pattern); `ui/outputs/OutputScrubberDetail.kt` (numeric IME validation pattern).

**Conformance changes only — preserve specialized layout:**
```kotlin
// 1. Gutter → FootButtonBar in field (D-15 + shared pattern):
ScreenScaffold(
    focus = { /* existing extrude controls — UNCHANGED */ },
    field = {
        /* existing Field content */
        FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            OutlinedControl("", onBack, Modifier.weight(1f), Intent.Neutral, icon = DinghyIcons.ArrowBack)
            OutlinedControl("", onLoad, Modifier.weight(1f), Intent.Accent, icon = /* Load icon — ASK owner */)
            OutlinedControl("", onUnload, Modifier.weight(1f), Intent.Neutral, icon = /* Unload icon — ASK owner */)
        }
    },
    gutter = null,  // migrated to FootButtonBar above
)

// 2. NumpadPage → numeric IME BasicTextField (D-16):
// Replace distance/speed NumpadPage pushes with BasicTextField
BasicTextField(
    value = distanceText,
    onValueChange = { raw ->
        val parsed = raw.toDoubleOrNull()
        if (parsed != null) {
            distanceText = raw
            workingDistance = PrinterCommands.clampExtrudeDistance(parsed)  // ALWAYS clamp
        }
    },
    keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Decimal,
        imeAction = ImeAction.Done,
    ),
    keyboardActions = KeyboardActions(
        onDone = { /* dismiss keyboard + apply */ }
    ),
)
// NEVER pass raw IME text to gcode — always parse + PrinterCommands.clamp* before dispatch

// 3. Nozzle-temp button → filament-preset Field-takeover (D-17):
// fieldMode = ExtrudeFieldMode.Main | ExtrudeFieldMode.FilamentPresets
// PresetPicker reuses PrinterCommands.MATERIAL_PRESETS + SpoolHolder.activeSpoolDetail?.filament?.type
// Applies SetHeaterArgs(extruderName, presetNozzleTemp) ONLY — never bed (D-17)
```

---

### `ui/spool/SpoolScreen.kt` (screen, ADD D-08 measured-weight takeover)

**Analog:** self — minimal addition only. Pattern from `ui/macros/BookmarkedMacrosScreen.kt` FieldMode.ParamEntry (the numeric IME takeover).

**Current measureSpool state** (SpoolScreen.kt lines 112–113 — existing pattern to EXTEND):
```kotlin
// EXISTING: var measureSpool by remember { mutableStateOf<SpoolmanSpool?>(null) }
// This already drives the MeasuredWeightPage full-screen push.
// D-08: replace the MeasuredWeightPage push with an in-Field-takeover using numeric IME.
```

**Numeric IME Field-takeover (D-08):**
```kotlin
// Add MeasureMode to FieldMode sealed class:
sealed class FieldMode {
    // existing modes...
    data class MeasureWeight(val spool: SpoolmanSpool) : FieldMode()
}

// In field content when (fieldMode):
is FieldMode.MeasureWeight -> {
    val spool = fieldMode.spool
    var weightText by remember(spool.id) { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.spool_measure_weight_label),
            fontFamily = Geist, fontSize = fsSp(18f, t.fs).sp)
        BasicTextField(
            value = weightText,
            onValueChange = { raw ->
                // digits + optional decimal only; no alpha
                if (raw.all { c -> c.isDigit() || c == '.' }) weightText = raw
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                val weight = weightText.toDoubleOrNull()
                if (weight != null && weight >= 0.0) {
                    applyMeasuredWeight(spool, weight)
                    fieldMode = FieldMode.SpoolList
                }
            }),
        )
    }
    FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        OutlinedControl("", { fieldMode = FieldMode.SpoolList }, Modifier.weight(1f),
            Intent.Neutral, icon = DinghyIcons.ArrowBack)
        OutlinedControl("Apply", { /* onDone logic */ }, Modifier.weight(1f), Intent.Go)
    }
}
```

---

## Shared Patterns

### 1. Two-Overload Stateless Seam (all rebuilt screens)

**Source:** `ui/spool/SpoolScreen.kt` lines 98–257
**Apply to:** `FineTuneScreen`, `TemperatureScreen`, `OutputsScreen`, `ExtrudeScreen` (conformance adds stateless seam)

Every migrated screen ships two overloads:
1. `fun XxxScreen(holder: XxxHolder, container: AppContainer, …)` — live; collects StateFlow; delegates to `private fun XxxContent(…)`.
2. `fun XxxScreen(state: XxxScreenState, …)` — stateless; all callbacks default `= {}`; drives `@Preview`.

---

### 2. BoxWithConstraints + rememberUnitGrid (all rebuilt screens)

**Source:** `ui/spool/SpoolScreen.kt` lines 289–293, `designsystem/layout/UnitGrid.kt`
**Apply to:** `FineTuneScreen`, `TemperatureScreen`, `OutputsScreen`, `ExtrudeScreen`

```kotlin
BoxWithConstraints(Modifier.fillMaxSize()) {
    val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
    // grid.uDp passed to ListRow, FootButtonBar, FloatingEStop, IncrementPicker
}
```

---

### 3. gutter = null + FootButtonBar in field (all rebuilt screens)

**Source:** `designsystem/components/FootButtonBar.kt` lines 19–27 (KDoc); `ui/spool/SpoolScreen.kt` line 425
**Apply to:** ALL rebuilt screens

```kotlin
ScreenScaffold(
    focus = { … },
    field = {
        // list content (Modifier.weight(1f))
        FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            OutlinedControl(…, modifier = Modifier.weight(1f), …)
        }
    },
    gutter = null,   // ALWAYS null on rebuilt screens
)
```

---

### 4. FloatingEStop wiring (Temperature + Fine-Tune — printing-list-valid)

**Source:** `ui/spool/SpoolScreen.kt` lines 114–120, 171–185; `designsystem/components/FloatingEStop.kt`
**Apply to:** `TemperatureScreen`, `FineTuneScreen` (both are printing-list destinations per P24 D-07)

```kotlin
// In the live overload:
val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
val isPrinting = printerState.printState == PrintState.Printing ||
    printerState.printState == PrintState.Paused
var showEstopGuard by remember { mutableStateOf(false) }

// In Focus Box:
FloatingEStop(
    visible = isPrinting,
    onClick = { showEstopGuard = true },
    uDp = grid.uDp,
    modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
)
// ConfirmGuard as Box sibling (SpoolScreen.kt lines 171–185 pattern):
if (showEstopGuard) {
    ConfirmGuard(
        onConfirm = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit); showEstopGuard = false },
        onCancel = { showEstopGuard = false },
        destructive = true,
    )
}
```

---

### 5. P17 Clamp Authority (all screens with tuner dispatch)

**Source:** `ui/finetune/FineTuneShared.kt` lines 45–50 (clampVelocityLimitTarget); `command/PrinterCommands.kt` lines ~122–175
**Apply to:** `FineTuneScreen` (all 13 tuners), `TemperatureScreen` (setHeater), `OutputsScreen` (all output types)

```kotlin
// INVARIANT for every markPending call:
val clamped = PrinterCommands.clamp*(rawTarget)   // single source of truth
holder.markPending(tuner, clamped)               // armed target == wire value
dispatcher.dispatch(command, argsFor(rawTarget)) // wire re-clamps identically
// NEVER: holder.markPending(tuner, rawTarget) without clamping first
```

---

### 6. ScrubberPage Build-Once Rule (Outputs Focus)

**Source:** `designsystem/ScrubberPage.kt` lines 156–157 (the `remember(value, range)` seed)
**Apply to:** `OutputsScreen` inline Focus for FAN/PWM/SERVO outputs

```kotlin
// ScrubberPage's internal: var working by remember(value, range) { mutableFloatStateOf(value.coerceIn(…)) }
// This seeds only when value OR range changes between compositions — NOT on every recomposition.
// NEVER key(output.currentPct) { ScrubberPage(…) } — that forces a rebuild mid-drag (P19 regression)
// NEVER pass output.currentPct as value into a key block that changes while dragging
```

---

### 7. DataStore writes via AppContainer.writeScope

**Source:** `di/AppContainer.kt` lines 132 + 150–186 (writeScope usage patterns)
**Apply to:** Trace color/visibility persistence (D-14), any other DataStore writes in these screens

```kotlin
// WRONG (cancelled on navigation — silent write drop):
val scope = rememberCoroutineScope()
scope.launch { container.dataStore.edit { … } }

// CORRECT (process-lifetime scope):
container.writeScope.launch { /* write via preferences accessor */ }
// Pattern: AppContainer exposes methods like setTraceColor(sensorName, hex) that internally use writeScope
```

---

### 8. OutlinedControl with DinghyIcon token + Intent routing

**Source:** `designsystem/control/OutlinedControl.kt` lines 177–194; `ui/spool/SpoolScreen.kt` lines 503–534
**Apply to:** all FootButtonBar contents, Reset buttons, Off buttons, stepper buttons

```kotlin
// CORRECT — DinghyIcon token overload (icon registry law D-24):
OutlinedControl(label = "", onClick = onBack, modifier = Modifier.weight(1f),
    intent = Intent.Neutral, icon = DinghyIcons.ArrowBack)

// Intent mapping (button-intent law):
// Back / neutral nav      → Intent.Neutral  (outline, no safety color spent)
// Accent commands (++)    → Intent.Accent   (accentLine, physical command)
// Reset / Cooldown / Off  → Intent.Warn     (heat/amber, proceed-at-peril)
// Delete / E-stop         → Intent.Danger   (stop/red)
// Apply / Execute / Go    → Intent.Go       (green, accept)

// WRONG — raw ligature (bypasses icon registry):
OutlinedControl(label = "Back", onClick = onBack, symbol = "arrow_back")
```

---

### 9. @Preview Matrix (all rebuilt screens — D-23)

**Source:** `docs/ui_design/PREVIEW_AND_TOKENS.md`; Phase-18/25 established pattern
**Apply to:** `IncrementPicker`, `AdjusterPanel`, `FineTuneScreen`, `TemperatureScreen`, `OutputsScreen`, `ExtrudeScreen`

```kotlin
// 6 required combos + landscape check + fs=L:
@Preview(name = "FineTune Dark M portrait",  widthDp = 480, heightDp = 800)
@Preview(name = "FineTune Dark L portrait",  widthDp = 480, heightDp = 800)
@Preview(name = "FineTune Light M portrait", widthDp = 480, heightDp = 800)
@Preview(name = "FineTune Custom M portrait",widthDp = 480, heightDp = 800)
@Preview(name = "FineTune Dark S portrait",  widthDp = 480, heightDp = 800)
@Preview(name = "FineTune Light S portrait", widthDp = 480, heightDp = 800)
@Preview(name = "FineTune Dark M landscape", widthDp = 800, heightDp = 480)  // 5U phone-landscape check
@Composable fun FineTuneScreenPreview() {
    DinghyTheme(darkTheme = true, fs = FontScale.M) {
        FineTuneScreen(vm = fakeFineTuneVm())  // stateless overload — no live Moonraker
    }
}
```

---

### 10. fsSp font-scale compliance (all screens)

**Source:** `ui/spool/SpoolScreen.kt` lines 691–792; `dinghy-font-sizes-too-small.md`
**Apply to:** all text in all rebuilt screens

```kotlin
// Floors from established convention:
// param row name / primary row text:  fsSp(18f, t.fs).sp
// value readout (trailing):           fsSp(17f, t.fs).sp  (floor)
// metadata / secondary row text:      fsSp(15f, t.fs).sp  (floor — NEVER lower)
// adjuster big value:                 fsSp(48f, t.fs).sp
// "was X" baseline inline text:       fsSp(18f, t.fs).sp
// section headers / names:            fsSp(20f–22f, t.fs).sp
// NEVER: bare 18.sp without fsSp wrapping
```

---

### 11. FieldMode Sealed Class (Temperature + Fine-Tune + Outputs + Extrude)

**Source:** `ui/spool/SpoolScreen.kt` (FieldMode pattern established in Phase-23 pilot); `ui/macros/BookmarkedMacrosScreen.kt` (MacroFieldMode with data class variant)
**Apply to:** All screens with in-screen navigation (Field-takeover, preset picker, measure weight)

```kotlin
// Template per screen:
sealed class TempFieldMode {
    data object SensorList : TempFieldMode()
    data object PresetPicker : TempFieldMode()  // D-12
}

sealed class ExtrudeFieldMode {
    data object Main : ExtrudeFieldMode()
    data object FilamentPresets : ExtrudeFieldMode()  // D-17
}

// In field = { when (fieldMode) { … } }
// Back from any takeover → parent data object mode (no NavHost route change)
```

---

## No Analog Found

All files have analogs or are clearly derived from existing patterns. No entries in this section.

---

## Key Anti-Patterns to Avoid

| Anti-pattern | Where it bites | Correct pattern |
|---|---|---|
| `markPending(tuner, rawTarget)` without `PrinterCommands.clamp*()` | FineTuneScreen, TemperatureScreen nudge | D-22: clamp first, then markPending with clamped value |
| `key(output.currentPct) { ScrubberPage(…) }` | OutputsScreen inline Focus | ScrubberPage as-is; never key on live value |
| Stacked "was X" line below value | AdjusterPanel | Inline same-row Row(verticalAlignment = Alignment.Bottom) |
| `rememberCoroutineScope()` for trace color DataStore writes | TemperatureScreen | `container.writeScope.launch { … }` |
| `gutter = { FootButtonBar(…) }` | All rebuilt screens | `gutter = null`; FootButtonBar inside `field` lambda |
| Re-invoking `GraphViewHost` factory on Focus morph | TemperatureScreen | Simple `if (!showAdjuster)` branch (not AnimatedContent that removes/re-adds the node) |
| Auto-picking icons for param rows | FineTuneScreen AdjusterPanel header, ListRow leading | Check DinghyIcons.kt + img/material-icon-bucket.json + ASK owner (D-24) |
| Raw ligature `symbol = "arrow_back"` | All OutlinedControls in rebuilt screens | `icon = DinghyIcons.ArrowBack` |
| `NumpadPage` import anywhere after this phase | ExtrudeScreen, SpoolScreen | Android numeric IME (`KeyboardType.Decimal`) |
| Passing raw IME text to gcode | ExtrudeScreen D-16, SpoolScreen D-08 | Parse → `PrinterCommands.clamp*()` → dispatch |
| Pool color lookup without `PaletteMode.Colorful` guard | TemperatureScreen 8-swatch row | D-14: ALWAYS use Colorful pool (`t.pool`) for swatch row regardless of active mode |
| Bare `t.pool[i]` without `.take(8)` / size check | Temperature swatch row | `t.pool.take(8)` — guard against pools with fewer than 8 colors |

---

## Open Questions Requiring Wave 0 Investigation

Per RESEARCH.md A4/A5 — the planner MUST add Wave 0 investigation tasks for:

1. **Pool color token access (D-02/D-14):** Verify `ThemeTokens.pool: List<Color>` has ≥8 entries in COLORFUL mode at runtime. Grep `ThemeResolver` / `TokenBridge` for pool-population code path. If pool has <8 entries, the 8-swatch Temperature row needs a fallback.

2. **NavHost route shape for collapsed Fine-Tune:** Read `ui/shell/AppShell.kt` routing section before Wave 1 planning. The old Fine-Tune sub-routes (`MOTION`, `EXTRUSION`, `FW_RETRACTION`) must be removed; confirm the single flat route wiring.

3. **`GraphView.setTraceColors()` feasibility:** Does `GraphView` currently expose per-trace color overrides, or does it derive all colors from `applyTokens`? Read `render/GraphView.kt` before designing the D-14 extension. If no per-trace API exists, confirm the "filter series by visibility, trace colors stay token-based" approach is acceptable (the swatch = row icon + Focus panel only, not graph lines).

---

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/` (ui/, designsystem/, render/, di/, theme/)
**Files read directly:** FineTuneShared.kt, FineTuneHolder.kt (lines 1–285), ScrubberPage.kt, SpoolScreen.kt, TemperatureScreen.kt, TemperatureHolder.kt, OutputsScreen.kt, OutputScrubberDetail.kt, GraphViewHost.kt, ListRow.kt, DetailCard.kt, FootButtonBar.kt, AppContainer.kt (grep), ThemeTokens.kt (grep), SeriesColor.kt (grep), 25-PATTERNS.md
**Pattern extraction date:** 2026-06-10
