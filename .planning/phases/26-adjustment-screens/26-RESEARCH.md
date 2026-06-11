# Phase 26: Adjustment Screens — Research

**Researched:** 2026-06-10
**Domain:** Android / Jetpack Compose — UX migration of numeric-adjustment screens onto the Phase-23 adjustment archetype
**Confidence:** HIGH (all material sourced from the in-repo codebase and locked design documents — no web research required for a codebase-internal mapping phase)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Fine-Tune structure**
- D-01: One screen, flat list. `FineTuneHubScreen` + `ExtrusionScreen` + `MotionScreen` + `FwRetractionScreen` → ONE screen. Field = scrollable param list (~12–14 rows), Focus = 3-zone adjuster.
- D-02: Order = current groups flattened. Group identity = pool colors on leading param icons; no label words.
- D-03: Print fan stays in BOTH Fine-Tune and Outputs (same command path).
- D-04: Adjuster remembers last-adjusted param (session memory), first-param fallback.

**Control assignment**
- D-05: Stepper is default everywhere. Scrubber ONLY for naturally-bounded 0–100% values (fan speed, PWM, LED brightness).
- D-06: All in-place — full-screen single-setting pushes RETIRE. Adjuster in Focus; remaining single-setting callers render as Field-takeovers.
- D-07: Custom NumpadPage DROPPED app-wide → Android system numeric IME. Supersedes P25 D-12 "NumpadPage for numeric" for macro params.
- D-08: Measured weight converts NOW — Field-takeover in SpoolScreen using numeric system keyboard.
- D-09: Scrubber dispatches ON RELEASE. Fill/thumb/value update in place during drag (build-once rule — the Phase-19 `fa97efb` regression must not recur). One command fires on finger-lift.

**Temperature anatomy**
- D-10: Focus MORPHS graph ↔ adjuster. `GraphViewHost` is default Focus; row tap swaps to adjuster; done/back returns to graph.
- D-11: Typed row model with read-only support (adjustable heater vs read-only sensor). v1 ships heater set.
- D-12: Presets = foot button → Field-takeover (scrim retires on this screen).
- D-13: Off = both levels: amber Cooldown foot (TURN_OFF_HEATERS) + per-heater Off in adjuster (target = 0, P19 GAP-A precedent).
- D-14: Every selectable row's minimum adjuster = show/hide trace toggle + 8-swatch Colorful-pool color row. Heaters add target-temp stepper. Row icon tinted to chosen trace color. Trace color/visibility writes through `AppContainer.writeScope` (never a composition scope).

**Extrude + Outputs anatomy**
- D-15: Extrude is a SPECIALIZED-LAYOUT EXEMPTION — conformance only (tokens, FootButtonBar, ≥64px, fsSp, rotation, previews).
- D-16: Extrude distance + speed → numeric system keyboard (replacing NumpadPage setpoints); existing clamp ranges apply.
- D-17: Extrude nozzle-temp button → filament-preset Field-list (standard presets + active spool's filament from Spoolman). Applies extruder temp ONLY. Selection closes page; Back at bottom.
- D-18: Outputs collapses detail pages into the Focus. Field = output list; Focus = selected output's control in place. `OutputScrubberDetail`/`OutputPinDetail`/`OutputLedDetail` page pushes gone.
- D-19: LED fits in Focus with minimal rework — brightness scrubber + capability-gated channels + existing `ColorWheel`, composed into Focus.

**Cross-cutting**
- D-20: Increment picker = sketch-003 spec, built as a shared design-system component (supersedes C2 two-mode design). Step sets seeded from `FineTuneShared` constants; active = accent.
- D-21: Baseline "was X" — captured on entry, inline (never stacked), shown only when changed, cleared at baseline. Reset = caution/amber top-right; Reset-all in foot.
- D-22: P17 clamp authority + busy-lock discipline LOAD-BEARING. All `markPending` targets route through `PrinterCommands` clamps + `roundToWirePrecision`; per-tuner epsilon + seq-guarded timeout backstop survive. Regression tests must stay green.
- D-23: Conformance + preview-first + tokenized-first folds into each screen.
- D-24: Icon law — never auto-pick. New glyphs needed that aren't already registered: STOP and ASK the owner.

### Claude's Discretion
- Per-value increment step sets (D-20) and exact stepper clamp ranges — seed from `FineTuneShared` constants and `PrinterCommands` clamps; owner judges at UAT.
- How "remember last-adjusted" persists (session-only state vs DataStore) — session memory required; durability beyond process death optional.
- Whether PrintStatus's Preheat keeps the old scrim or adopts the new preset Field-takeover surface (D-12).
- The Focus graph↔adjuster morph mechanics (cheap one-shot cross-fade per the P24 D-13 precedent, or hard swap) — must hold the flox frame budget.
- Trace-color persistence shape (per-printer-profile vs global) — match existing theme/profile persistence patterns.
- Exact NavHost/route + in-screen state shape for the collapsed Fine-Tune and Outputs screens.

### Deferred Ideas (OUT OF SCOPE)
- Additional non-heater temperature readings (`temperature_sensor`: MCU/Pi/chamber) on the Temperature page.
- Deeper LED aesthetic redesign.
</user_constraints>

---

## Summary

Phase 26 is a UX migration phase — no new printer capability is introduced. The work is reorganizing and restyling five functional screen clusters (Temperature, Extrude, Outputs, Fine-Tune, and single-setting pages) onto the Phase-23 adjustment archetype (stepper + 3-zone adjuster + inline baseline readout). The Phase-23 kit (`ListRow`, `DetailCard`, `FootButtonBar`, `ListBlock`, `UnitGrid`) and the Phase-25 SpoolScreen/two-overload/Field-takeover patterns are the primary build substrate.

Three prior-phase lessons are **load-bearing safety constraints**, not optional reading: the Phase-17 clamp-authority mechanism (single-source `PrinterCommands` clamps, `roundToWirePrecision`, per-tuner epsilon, seq-guarded timeout), the Phase-19 scrubber regression (build-once `working` MutableState, left-anchored fill, `awaitEachGesture` + `settle()` on pointer-up only), and the `AppContainer.writeScope` persistence rule (DataStore writes must never use a composition scope).

The `FineTuneHolder` is the most complex object in scope: it has a 5-flow `combine`, a monotonic `flipSeq` arm ID, a `lastObservedState` snapshot anti-racing guard, and the `toleranceFor`/`wirePrecisionFor` precision system — all of which must remain intact even as the display surface completely changes. The holder's contract is: `markPending(tuner, clampedTarget)` → `reached()` → auto-clear. The new screen wraps that contract in the 3-zone adjuster, it does not replace it.

**Primary recommendation:** Build the `IncrementPicker` shared component first (Wave 0 / design-system kit), then the 3-zone `AdjusterPanel` composable wrapping it plus the existing `FineTuneHolder` contract, then migrate screens one-by-one in capability order (Fine-Tune → Temperature → Outputs → Extrude).

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| 3-zone adjuster UI (value, stepper, increment picker, baseline) | UI / Compose composable | — | Pure presentation layer, stateless — data flows from holder |
| Fine-Tune clamp authority | `PrinterCommands` (command layer) | `FineTuneHolder` (enforces at markPending) | Must NOT be in the UI layer |
| Fine-Tune busy-lock / flip detection | `FineTuneHolder` (toolkit-agnostic) | — | host-testable Kotlin, no Compose |
| Temperature graph + ring buffers | `TemperatureHolder` + `GraphViewHost` (Views) | — | Views-in-Compose seam per ADR-0001; Phase-22 equality guards must not be disturbed |
| Focus graph↔adjuster morph | TemperatureScreen composable (UI) | — | Local state machine (var showingAdjuster) |
| Temperature trace color/visibility | `AppContainer.writeScope` (process-lifetime persistence) | `TemperatureHolder` (folded into series) | THEME-01 carve-out; data color, not chrome |
| Outputs capability gating | `PrinterState` Capabilities flow | `OutputsHolder` | hide-not-grey (P24 D-08) |
| Scrubber dispatch | `ScrubberPage.OnSettle` settle() path | `PrinterCommands.outputPctToWire` | one dispatch on pointer-up only |
| IncrementPicker component | `designsystem/components/` (new shared component) | — | reusable across all stepper call sites |
| Numeric entry (extrude distance/speed, measured weight, macro params) | Android IME (`inputType = InputType.TYPE_CLASS_NUMBER`) | — | D-07: system keyboard replaces NumpadPage |
| Filament-preset list (Extrude D-17) | UI / Field-takeover composable | `SpoolHolder.activeSpoolDetail` (live spool data) | read-only data surface |
| Navigation back-stack (Fine-Tune, Outputs collapse) | NavHost / in-screen state (`FieldMode` sealed class) | — | local sub-nav dissolves; sane Back re-anchors to NavHost |

---

## Standard Stack

No new external libraries are introduced in this phase. All tooling is already in `libs.versions.toml`.

### Reused Phase-23 Design-System Kit (in-repo)

| Component | Location | What It Provides |
|-----------|----------|------------------|
| `ListRow` | `designsystem/components/ListRow.kt` | Param rows in Fine-Tune Field; output rows in Outputs Field; sensor rows in Temperature Field |
| `DetailCard` | `designsystem/components/DetailCard.kt` | Adjuster Focus surface container for Fine-Tune, Outputs |
| `FootButtonBar` | `designsystem/components/FootButtonBar.kt` | Foot-of-field action row (Home, Reset-all, Cooldown, Back, etc.) |
| `ListBlock` | `designsystem/layout/ListBlock.kt` | Edge-faded LazyColumn wrapping param/output/sensor lists |
| `UnitGrid` / `rememberUnitGrid` | `designsystem/layout/UnitGrid.kt` | `U` derivation; `BoxWithConstraints` + `minOf(maxWidth, maxHeight)` at screen root |
| `FloatingEStop` | `designsystem/components/FloatingEStop.kt` | Visible when `isPrinting`; Temperature + Fine-Tune are printing-valid |
| `ScrubberPage` | `designsystem/ScrubberPage.kt` | Existing scrubber — reused as-is for 0–100% outputs; `OnSettle` mode dispatches on pointer-up only |
| `OutlinedControl` | `designsystem/control/OutlinedControl.kt` | All stepper `–`/`+` tiles, foot buttons, intent-routed |

### New Component to Build (Wave 0)

| Component | Location (proposed) | What It Provides |
|-----------|---------------------|------------------|
| `IncrementPicker` | `designsystem/components/IncrementPicker.kt` | Shared design-system component; row of 3 step tiles (e.g. ±1/±5/±10); active = accent; seeded with per-value step arrays from `FineTuneShared` |
| `AdjusterPanel` | `designsystem/components/AdjusterPanel.kt` | 3-zone Focus composable: header (icon + name + Reset), centered value + inline "was X" baseline, bottom stepper row + `IncrementPicker`. Generic over a double value; used by Fine-Tune, Temperature |

---

## Architecture Patterns

### System Architecture Diagram

```
User tap (param row) ───► Screen state (selectedParam / showingAdjuster)
                                     │
                          ┌──────────▼──────────────────────────┐
                          │   AdjusterPanel (Focus)             │
                          │  ┌─ header: icon + name + Reset ─┐  │
                          │  │  (Reset = caution/amber)      │  │
                          │  └───────────────────────────────┘  │
                          │  ┌─ value zone (centered) ────────┐  │
                          │  │  big tabular numeral + unit     │  │
                          │  │  "was X" inline (muted, when   │  │
                          │  │   changed)                     │  │
                          │  └───────────────────────────────┘  │
                          │  ┌─ controls (bottom) ────────────┐  │
                          │  │  – stepper  +  IncrementPicker  │  │
                          │  └───────────────────────────────┘  │
                          └─────────────┬───────────────────────┘
                                        │ nudge(clampedTarget)
                          ┌─────────────▼──────────────────────┐
                          │   PrinterCommands.clamp*(value)    │◄── SINGLE SOURCE OF TRUTH
                          └─────────────┬──────────────────────┘
                                        │ markPending(tuner, clampedTarget)
                          ┌─────────────▼──────────────────────┐
                          │   FineTuneHolder (toolkit-agnostic)│
                          │   PendingStateFlip / flipSeq       │
                          │   roundToWirePrecision(tuner, val) │
                          │   toleranceFor(tuner) epsilon      │
                          │   seq-guarded 8s timeout backstop  │
                          └─────────────┬──────────────────────┘
                                        │ dispatch gcode
                          ┌─────────────▼──────────────────────┐
                          │   CommandDispatcher (session)      │
                          └─────────────┬──────────────────────┘
                                        │ Moonraker JSON-RPC
                          ┌─────────────▼──────────────────────┐
                          │   Klipper / Printer                │
                          └─────────────┬──────────────────────┘
                                        │ notify_status_update
                          ┌─────────────▼──────────────────────┐
                          │   PrinterState (reduced, throttled)│
                          └─────────────┬──────────────────────┘
                                        │ combine() -> FineTuneVm
                          ┌─────────────▼──────────────────────┐
                          │   FineTuneHolder.vm StateFlow      │
                          └─────────────┬──────────────────────┘
                                        │ collectAsStateWithLifecycle
                          ┌─────────────▼──────────────────────┐
                          │   Field: ListBlock of param ListRows│
                          └────────────────────────────────────┘
```

**Temperature-specific morph path:**

```
Default Focus: GraphViewHost (Views, AndroidView D-12 equality guard intact)
                     │
              row tap (sensor)
                     │
                     ▼
           Focus swaps to AdjusterPanel (for heaters)
           OR temperature graph-controls panel (show/hide + 8-swatch color row)
                     │
              done/Back
                     │
                     ▼
           Focus returns to GraphViewHost
```

### Recommended Project Structure

No new packages required. File additions slot into existing packages:

```
designsystem/components/
├── IncrementPicker.kt       # NEW — shared step-set selector
├── AdjusterPanel.kt         # NEW — 3-zone adjuster composable
└── [existing Phase-23 kit]

ui/finetune/
├── FineTuneScreen.kt        # NEW — single screen, replaces Hub + 3 group screens
├── FineTuneHolder.kt        # UNCHANGED API — internal wiring unchanged
├── FineTuneVm.kt            # UNCHANGED
├── FineTuneShared.kt        # UNCHANGED — step consts + wire precision
└── [FineTuneHubScreen.kt, ExtrusionScreen.kt, MotionScreen.kt, FwRetractionScreen.kt] → DELETE

ui/temperature/
├── TemperatureScreen.kt     # REBUILD — morph, typed row model, graph controls
└── TemperatureHolder.kt     # EXTEND — per-trace color/visibility, do NOT rebuild

ui/outputs/
├── OutputsScreen.kt         # REBUILD — Field=list, Focus=in-place control
├── ColorWheel.kt            # RETAIN (unchanged — reused in LED Focus)
└── [OutputScrubberDetail.kt, OutputPinDetail.kt, OutputLedDetail.kt] → DELETE (logic migrates to Focus)

ui/extrude/
├── ExtrudeScreen.kt         # CONFORMANCE-ONLY — tokens, FootButtonBar, fsSp, rotation, previews
└── ExtrudeHolder.kt         # UNCHANGED

ui/spool/
└── SpoolScreen.kt           # ADD: measured-weight numeric IME Field-takeover (D-08)

designsystem/
└── NumpadPage.kt            # RETIRE as screen destination; internals removed (D-07)
```

### Pattern 1: The 3-Zone AdjusterPanel

Built per the sketch-003 spec in `adjustment-controls.md`. Key decisions:

```kotlin
// Source: .claude/skills/sketch-findings-dinghy-display/references/adjustment-controls.md
@Composable
fun AdjusterPanel(
    icon: DinghyIcon,
    name: String,
    value: Double?,          // null → "—" (DASH), controls disabled
    unit: String,
    baseline: Double?,       // captured on entry; drives "was X" (null = no baseline, no Reset)
    decimals: Int,
    onDecrement: () -> Unit, // caller applies clamp + markPending before calling
    onIncrement: () -> Unit,
    onReset: (() -> Unit)?,  // null when baseline is null (no Reset affordance)
    enabled: Boolean,        // busy-lock feeds this
    incrementPicker: @Composable () -> Unit,  // caller provides IncrementPicker
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    // Three zones: Column with space-between
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        // Zone 1 — Header: icon + name (left) + Reset (top-right)
        Row(Modifier.fillMaxWidth()) {
            DinghyIconView(icon = icon, tint = t.accent2, …)
            Text(name, …, fontFamily = Geist, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            onReset?.let {
                OutlinedControl("Reset", onClick = it, intent = Intent.Warn, …) // caution/amber
            }
        }
        // Zone 2 — Value + inline baseline (centered, flex:1)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.Baseline) {
                Text(
                    text = if (value == null) DASH else fmtValue(value, decimals) + unit,
                    fontFamily = GeistMono, fontWeight = FontWeight.Bold,
                    fontSize = fsSp(48f, t.fs).sp,  // big tabular numeral
                )
                // "was X" shown ONLY when value != baseline (never when null or at baseline)
                if (value != null && baseline != null && !approxEqual(value, baseline)) {
                    Text(
                        text = "  was ${fmtValue(baseline, decimals)}$unit",
                        color = t.text3,
                        fontFamily = GeistMono,
                        fontSize = fsSp(18f, t.fs).sp,  // inline, not stacked
                    )
                }
            }
        }
        // Zone 3 — Stepper + IncrementPicker (bottom)
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedControl("−", onClick = onDecrement, modifier = Modifier.weight(1f),
                    intent = Intent.Accent, enabled = enabled && value != null)
                OutlinedControl("+", onClick = onIncrement, modifier = Modifier.weight(1f),
                    intent = Intent.Accent, enabled = enabled && value != null)
            }
            incrementPicker()  // caller-provided IncrementPicker
        }
    }
}
```

### Pattern 2: IncrementPicker (shared design-system component)

```kotlin
// Source: .claude/skills/sketch-findings-dinghy-display/references/adjustment-controls.md
@Composable
fun IncrementPicker(
    steps: ImmutableList<Double>,   // e.g. persistentListOf(0.001, 0.005, 0.01) for PA
    activeStep: Double,
    onSelect: (Double) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEach { step ->
            OutlinedControl(
                label = formatStep(step),
                onClick = { onSelect(step) },
                modifier = Modifier.weight(1f).heightIn(min = uDp * 0.95f),
                intent = if (step == activeStep) Intent.Accent else Intent.Neutral,
            )
        }
    }
}
```

### Pattern 3: Fine-Tune Flat List (D-01/D-02)

The new `FineTuneScreen` replaces all four old screens. The Field is a `ListBlock` of `ListRow` items, one per param. Group identity = pool color on the leading icon (no text labels). Selected param → Focus shows `AdjusterPanel`. Capability-gated rows (FW-retraction) simply hide (D-08 hide-not-grey rule):

```kotlin
// Param rows — FW-retraction rows only rendered when hasFwRetraction
items(visibleParams, key = { it.tuner.name }) { param ->
    ListRow(
        selected = param.tuner == selectedTuner,
        onClick = { selectedTuner = param.tuner; /* session remember */ },
        uDp = grid.uDp,
        leadingContent = {
            DinghyIconView(
                icon = param.icon,
                tint = param.groupColor,  // D-02: pool hue identifies group
            )
        },
        trailingContent = {
            Text(
                text = currentValueText(vm, param.tuner),
                fontFamily = GeistMono,
                fontSize = fsSp(18f, t.fs).sp,
            )
        }
    ) {
        Text(param.name, fontFamily = Geist, fontSize = fsSp(18f, t.fs).sp)
    }
}
```

### Pattern 4: Temperature Graph↔Adjuster Morph (D-10)

The key constraint: the `GraphViewHost` uses Views-in-Compose with Phase-22 D-12 `applyTokens` equality guards. The morph must NOT thrash the AndroidView.

```kotlin
// Local state machine — simple boolean is fine (one-shot transition per tap)
var selectedSensor by remember { mutableStateOf<SensorReadout?>(null) }
val showAdjuster = selectedSensor != null

ScreenScaffold(
    focus = {
        // ONE slot, two children via Box + AnimatedContent or simple if/else
        // Cheap hard swap is acceptable (D-10 morph mechanics = Claude's discretion)
        if (!showAdjuster) {
            // The GraphViewHost does NOT get rebuilt when adjuster shows —
            // keep it in the tree but hidden (visibility:gone equivalent = Box with 0 size,
            // OR just use if-else, which is fine since Graph uses AndroidView factory re-use)
            GraphViewHost(
                tokens = t,
                series = series,
                setpoints = setpoints,
                yRange = graphRange,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            selectedSensor?.let { sensor ->
                // Heater: AdjusterPanel + graph controls (show/hide + color swatches)
                // Read-only sensor: only graph controls
                TemperatureAdjusterFocus(sensor, vm, ...)
            }
        }
    },
    ...
)
```

**CRITICAL:** Do NOT hold a reference to the `GraphViewHost` in the `if` branch that shows the adjuster — the old View node will be garbage-collected and the equality guards in `applyTokens` will work correctly only if the `AndroidView` factory is not re-invoked unnecessarily. A simple `if (!showAdjuster)` branch (not a `when` over a state that goes through null) is the safest approach.

### Pattern 5: Scrubber Build-Once Rule (P19 lesson — SC-3)

The Phase-19 regression (`fa97efb`) and the D-09 decision lock the scrubber interaction:

```kotlin
// CORRECT — ScrubberPage as-is (OnSettle mode)
// Internal working MutableState is seeded from value; updated in place during drag.
// settle() fires ONCE on pointer-up, dispatches to Moonraker once.
ScrubberPage(
    label = output.displayName,
    value = output.currentPct.toFloat(),
    range = 0f..100f,
    step = 1f,
    actions = ScrubberActions.OnSettle(
        onSettle = { pct ->
            markPendingOutput(output.objectKey, pct.roundToInt())
            dispatch(CommandRegistry.setGenericFan, SetGenericFanArgs(output.name, pct.toInt()))
        },
        onBack = { selectedOutput = null },
        onOff = { dispatch(...) },
    )
)
// DO NOT: rebuild ScrubberPage on each drag frame
// DO NOT: pass output.currentPct that changes while dragging (race condition)
```

### Pattern 6: Numeric IME (D-07)

Replacing `NumpadPage` for exact numeric entry:

```kotlin
// In Extrude for distance/speed fields, SpoolScreen for measured weight
BasicTextField(
    value = textState,
    onValueChange = { /* clamp + validate */ },
    keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Decimal,  // or KeyboardType.Number for integers
        imeAction = ImeAction.Done,
    ),
    keyboardActions = KeyboardActions(
        onDone = { /* apply + dismiss keyboard */ }
    ),
    // Clamped to existing PrinterCommands ranges before dispatch
)
```

No `NumpadPage` import anywhere in the codebase after this phase.

### Pattern 7: Field-Takeover (D-06/D-12/D-17)

Reuse the Phase-25 `FieldMode` sealed-class pattern from SpoolScreen/MacrosScreen:

```kotlin
// E.g. for TemperatureScreen's preset picker (D-12)
sealed class TempFieldMode {
    data object SensorList : TempFieldMode()
    data object PresetPicker : TempFieldMode()  // foot button → Field-takeover
}

var fieldMode by remember { mutableStateOf<TempFieldMode>(TempFieldMode.SensorList) }

// Inside ScreenScaffold field = { ... }:
when (fieldMode) {
    is TempFieldMode.SensorList -> { /* sensor ListBlock + FootButtonBar */ }
    is TempFieldMode.PresetPicker -> {
        ListBlock(Modifier.weight(1f)) {
            items(PrinterCommands.MATERIAL_PRESETS, key = { it.name }) { preset ->
                ListRow(selected = false, onClick = { applyPreset(preset); fieldMode = TempFieldMode.SensorList }, uDp = grid.uDp) {
                    // preset name + nozzle/bed temps
                }
            }
        }
        FootButtonBar(uDp = grid.uDp) {
            OutlinedControl("", { fieldMode = TempFieldMode.SensorList }, Modifier.weight(1f), Intent.Neutral, DinghyIcons.ArrowBack)
        }
    }
}
```

### Anti-Patterns to Avoid

- **`markPending` with unclamped target:** The P17 UAT Check 6 root cause — always call `PrinterCommands.clamp*(value)` BEFORE `markPending`. The wire builder re-clamps identically, so target == wire value.
- **Rebuilding `ScrubberPage` mid-drag:** The P19 inline-scrubber regression — stale `pointerInput` closure detaches the gesture. Use `ScrubberPage` as-is; never place it in a `key()`-wrapped block that changes during drag.
- **Stacked "was X" line:** Overflows the 5U phone-landscape Focus. Keep inline (same baseline row as the value).
- **`FootButtonBar` in the `gutter` slot:** Place it as the last element inside the `field` lambda; pass `gutter = null` on all rebuilt screens.
- **`rememberCoroutineScope()` for DataStore writes:** Use `AppContainer.writeScope` for trace-color/visibility persistence (D-14) and any other persistent write — composition scopes are cancelled on navigation.
- **`color-mix(in oklch, var(--heat), ...)` for caution outlines:** hue bleeds through red. Use `t.heat` directly (`Intent.Warn` already does this in `OutlinedControl`).
- **Disturbing `GraphViewHost`'s `applyTokens` equality guards:** The Phase-22 guards prevent token thrashing. Do not add new `update =` lambda code paths that pass non-equal objects on every recomposition.
- **Auto-picking icons for param rows or adjuster header:** Check `DinghyIcons.kt` + `img/material-icon-bucket.json` + the registry. If a glyph is not already assigned, STOP and ASK (D-24).
- **`okclh color-mix` caution border on Reset button:** Use `Intent.Warn` which routes to `t.heat` directly.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Per-output scrubber | Custom drag gesture | `ScrubberPage` (OnSettle mode) | P19 regression risk; `ScrubberPage` already has the correct `awaitEachGesture` pattern |
| Busy-lock / optimistic-state | New pending-flip system | `FineTuneHolder.markPending` / `clearPending` | P17 bug class — the flip detection, wire-precision rounding, seq guard, and epsilon are all pre-proven in `FineTuneHolderTest` |
| Clamp functions | Inline `coerceIn()` at call sites | `PrinterCommands.clamp*()` | Single source of clamp authority; mismatched inline clamp == P17 UAT Check 6 |
| Row list for params | Custom LazyColumn | `ListBlock` + `ListRow` from Phase-23 kit | Correct `key = { }`, spacing, scroll edge-fade, `heightIn(min = uDp)` touch floor already handled |
| Step-set picker | Inline Row of OutlinedControl | `IncrementPicker` (new shared component built in Wave 0) | Reused by ~14 tuners; step formatting varies by tuner (`fmtValue` + unit) |
| Exact numeric entry | Custom numpad | Android numeric IME (`KeyboardType.Decimal`) | D-07; `NumpadPage` retired |
| Graph trace color | Custom color-picker wheel | 8-swatch inline row from Colorful theme pool (D-14) | Owner decision; pool is fixed-8 colors, no picker page needed |

**Key insight:** The Phase-17 clamp-authority system and Phase-19 scrubber lesson together define the two biggest regression risks in this phase. Every adjuster dispatch must route through `PrinterCommands.clamp*()` + `markPending(tuner, clampedValue)`, and every scrubber interaction must go through `ScrubberPage` as-is.

---

## Current Screen Inventory (What Gets Migrated)

### Fine-Tune Cluster (DELETE 4, CREATE 1)

| File | Action | Key Notes |
|------|--------|-----------|
| `ui/finetune/FineTuneHubScreen.kt` | DELETE | Two-tile nav hub; replaced by single flat-list screen |
| `ui/finetune/ExtrusionScreen.kt` | DELETE | Speed/Flow/PA/Smooth tiles; logic absorbed into new screen |
| `ui/finetune/MotionScreen.kt` | DELETE | Velocity/Accel/MinCruise/SCV tiles; logic absorbed |
| `ui/finetune/FwRetractionScreen.kt` | DELETE | Retraction tiles; logic absorbed (capability-gated hide) |
| `ui/finetune/FineTuneScreen.kt` | CREATE | Single screen: ListBlock param list + AdjusterPanel Focus |
| `ui/finetune/FineTuneHolder.kt` | **UNCHANGED API** | All `markPending`/`clearPending`/`setInFlight` preserved exactly |
| `ui/finetune/FineTuneVm.kt` | **UNCHANGED** | Data class with all 13 tuner fields |
| `ui/finetune/FineTuneShared.kt` | **PRESERVED** | Step constants + `clampVelocityLimitTarget` + `fmtValue` + `VelocityLimitTile` — reuse in new screen |
| `ui/finetune/FineTuneGroup.kt` | DELETE (after migration) | No longer needed once hub is gone |
| `ui/finetune/FineTuneTile.kt` | RETIRE / DELETE | Internal tile replaced by `AdjusterPanel` |

**14 param rows in the flat list:**
1. Speed % (Extrusion group, SPEED_STEP=5)
2. Flow % (Extrusion group, FLOW_STEP=1)
3. Pressure Advance (Extrusion group, PA_STEP=0.001)
4. Smooth Time (Extrusion group, SMOOTH_STEP=0.01)
5. Part-cooling Fan % (Extrusion group, FAN_STEP_PCT=5) — D-03: stays here AND in Outputs
6. Max Velocity (Motion group, VEL_STEP=10.0)
7. Max Accel (Motion group, ACCEL_STEP=100.0)
8. Min Cruise % (Motion group, MIN_CRUISE_STEP_PCT=5)
9. SCV (Motion group, SCV_STEP=0.1)
10. Retract Length (FW-Ret group, RETRACT_LEN_STEP=0.1) — hide if !hasFwRetraction
11. Retract Speed (FW-Ret group, RETRACT_SPEED_STEP=1) — hide if !hasFwRetraction
12. Unretract Extra Length (FW-Ret group, RETRACT_LEN_STEP=0.1) — hide if !hasFwRetraction
13. Unretract Speed (FW-Ret group, RETRACT_SPEED_STEP=1) — hide if !hasFwRetraction

Fan stays position 5 (per group-flattened order, D-02).

**Group pool colors (D-02):** Three groups need three distinct data-pool hues from the theme's Colorful pool. The exact color assignments are Claude's discretion but must match available pool tokens from `ThemeResolver` / `TokenBridge`. Planner should pick three clearly distinct hues from the existing pool.

### Temperature Screen (REBUILD)

| File | Action | Key Notes |
|------|--------|-----------|
| `ui/temperature/TemperatureScreen.kt` | REBUILD | Morph Focus (graph↔adjuster), typed row model, Field-takeover presets, graph controls (D-10/D-11/D-12/D-13/D-14) |
| `ui/temperature/TemperatureHolder.kt` | EXTEND | Add per-trace color/visibility state; extend the existing `series` + `setpoints` derivation; `@Stable`/`@Immutable` must survive; Phase-22 D-12 equality guards in `GraphViewHost`'s `update =` lambda must remain |
| `render/GraphViewHost.kt` | EXTEND CAREFULLY | Add per-trace color override and visibility toggle parameters; preserve ALL Phase-22 equality guard patterns |

**TemperatureHolder extensions needed:**
- `traceColors: StateFlow<Map<String, Color>>` — per-sensor user-chosen trace color (D-14)
- `traceVisibility: StateFlow<Map<String, Boolean>>` — per-sensor show/hide (D-14)
- Persist both through `AppContainer.writeScope` (D-14 rule)
- `SensorReadout` data class: add `isAdjustable: Boolean` (D-11 typed row model; false = read-only non-heater)

**Existing `seriesColor` function in TemperatureScreen.kt line 461 area:** The same-hue-invariant (graph trace color = row icon = readout) now flows FROM the user's chosen trace color DOWN to the row icon tint (D-14). The function's output must equal the user-chosen color when set, or the default nozzle/bed/chamber token color when not.

### Outputs Screen (REBUILD)

| File | Action | Key Notes |
|------|--------|-----------|
| `ui/outputs/OutputsScreen.kt` | REBUILD | Field = `LazyColumn` of output `ListRow` (existing `OutputRowVm` list); Focus = selected output's inline control surface |
| `ui/outputs/OutputScrubberDetail.kt` | DELETE (logic migrates to Focus) | `OnSettle` dispatch pattern + Off gutter + clamp authority preserved |
| `ui/outputs/OutputPinDetail.kt` | DELETE (logic migrates to Focus) | Toggle + command path preserved |
| `ui/outputs/OutputLedDetail.kt` | DELETE (logic migrates to Focus) | `ColorWheel` + brightness scrubber + channel gating preserved |
| `ui/outputs/OutputToggleControl.kt` | RETAIN (composable reuse) or inline | Small toggle component used inside the new inline Focus |
| `outputs/OutputsHolder.kt` | UNCHANGED | Existing `rows: StateFlow<List<OutputRowVm>>` unchanged |
| `designsystem/ColorWheel.kt` | UNCHANGED | Reused inside LED Focus (D-19) |

**Focus control surface by output type:**
- `FAN` / `PWM_PIN` / `PWM_TOOL` → `ScrubberPage` (OnSettle, 0–100%, Off button)
- `output_pin` (digital) → Toggle button (`OutlinedControl` with on/off intent)
- `servo` → `ScrubberPage` (OnSettle, 0..servoAngleMax°)
- `led` → Brightness `ScrubberPage` + capability-gated channel controls + `ColorWheel` (D-19)
- `heater_generic` → `ScrubberPage` (OnSettle, 0..MAX_TEMP_C°)

**Phase-22 D-12 equality guard:** `OutputsHolder` and the AndroidView-wrapping code in `OutputScrubberDetail.kt` do NOT use `GraphViewHost`. The LED's `ColorWheel` is a pure Compose component — no equality guard needed there.

### Extrude Screen (CONFORMANCE-ONLY — D-15)

| File | Action | Key Notes |
|------|--------|-----------|
| `ui/extrude/ExtrudeScreen.kt` | CONFORMANCE ONLY | No layout migration; apply tokens, FootButtonBar replacing the gutter, ≥64px, fsSp, rotation, `@Preview` 6-combo + fs=L, `stringResource`, `DinghyIcons` |
| `ui/extrude/ExtrudeHolder.kt` | UNCHANGED | |

**Specific changes in ExtrudeScreen.kt:**
- Replace `NumpadPage` calls (for distance/speed entry) with numeric IME `BasicTextField` (D-16)
- Replace the nozzle-temp button's `PresetSelector` with a new filament-preset Field-list page (D-17). The preset data comes from `PrinterCommands.MATERIAL_PRESETS` + `SpoolHolder.activeSpoolDetail.filament?.name` / type. Applies `CommandRegistry.setHeater` for the extruder heater ONLY (never bed).
- Move gutter controls to `FootButtonBar` inside `field`; pass `gutter = null`
- `Load` / `Unload` / `Back` preserved; `Intent.Accent` / `Intent.Neutral` per the intent law

### Single-Setting Pages (RETIRE / CONVERT)

| File | Action |
|------|--------|
| `designsystem/ScrubberPage.kt` | RETAIN AS-IS (reused for Outputs inline Focus, D-06 embeddable) |
| `designsystem/NumpadPage.kt` | DELETE as a standalone screen destination; no callers remain after this phase |
| `ui/spool/MeasuredWeightPage.kt` | REPLACE with numeric IME Field-takeover inside SpoolScreen (D-08) |

---

## Phase-17 Clamp Authority — Load-Bearing Detail

All five files / mechanisms that MUST survive the Fine-Tune screen rebuild:

**1. `PrinterCommands.kt` (clamp functions — lines ~122–175):**
`clampSpeedPct`, `clampFlowPct`, `clampVelocity`, `clampAccel`, `clampScv`, `clampMinCruiseRatio`, `clampPressureAdvance`, `clampSmoothTime` — these are the SINGLE source of truth. Every `nudge()` call in the new screen feeds the result of the appropriate clamp to BOTH `markPending` AND the dispatch arg.

**2. `FineTuneHolder.kt` (markPending — lines ~168–196):**
- `roundToWirePrecision(tuner, target)` rounds the armed target to the wire's decimal precision
- Skip-arm: if `abs(current - rounded) < toleranceFor(tuner)` → do NOT arm
- Seq guard: `PendingStateFlip(tuner, rounded, seq = ++flipSeq)` — unique seq per arm
- 8s timeout backstop: seq-matched cancellation
- `lastObservedState` snapshot: ensures skip-arm and `reached()` compare the same throttled state

**3. `FineTuneHolder.kt` (wirePrecisionFor — lines ~257–271):**
SCV→1dp, PA→3dp, smooth→2dp, velocity/accel/speed/flow/fan→0dp (integers), MIN_CRUISE→0dp display (%), retraction→1dp. The new screen must pass targets through `markPending` EXACTLY as the old tiles did.

**4. Clamp routing invariant for the migration:**
The new `AdjusterPanel` will call a `nudge()` lambda provided by the screen. That lambda MUST follow:
```kotlin
fun nudge(target: Double) {
    val clamped = PrinterCommands.clampForTuner(tuner, target)  // exact clamp function
    holder.markPending(tuner, clamped)
    dispatcher.dispatch(commandForTuner(tuner), argsForTuner(tuner, target))  // wire re-clamps
}
```
The wire builder re-clamps identically, so `clamped == wire_value` always holds.

**5. Tests that must stay green (`FineTuneHolderTest`):**
- `vm_scales_ratio_to_percent` — display scaling lives in holder, not reducer
- `vm_scales_fan_0to1_to_percent` — fan 0..1 → percent at display boundary
- `vm_scales_minCruiseRatio_to_percent` — ratio×100 display
- State-flip / busy-lock tests (the off-grid SCV regression, WR-01/WR-02)
- `markPending_skipArm_at_cap` — no permanent wedge at ceiling
- Seq-guarded timeout backstop tests

---

## Phase-19 Scrubber Regression — Load-Bearing Detail (SC-3)

**Root cause (from `fa97efb`):** A `pointerInput` block with a stale closure over a value that changes during drag. When Compose rebuilds the composable mid-drag (because the value changes), the new `pointerInput` block captures the new closure while the gesture system still tracks the OLD node — the fill grows from center, and value doesn't stick.

**The fix (in `ScrubberPage.kt` lines 156–238):**
```kotlin
// Internal MutableState — isolated from external 'value' prop changes during drag
var working by remember(value, range) { mutableFloatStateOf(value.coerceIn(range.start, range.endInclusive)) }
// LEFT-ANCHORED fill (fillMaxWidth(fraction))
// pointerInput(range, step) — keys on range+step, NOT on working value
// awaitEachGesture — re-arms per gesture, not per frame
// settle() fires ONCE on the last pointer UP
```

**Rule for this phase:** Do NOT create new inline scrubber implementations. Use `ScrubberPage` as-is. If the new Outputs inline Focus needs a scrubber, it HOSTS `ScrubberPage`; it does not re-implement the gesture logic.

---

## Existing Tests

Tests that must stay green after this phase:

| Test File | What It Guards |
|-----------|----------------|
| `FineTuneHolderTest.kt` | All 13 tuner scale/clamp/busy-lock/wire-precision/timeout tests |
| `PrinterCommandsTest.kt` | All clamp function bounds (the single source of truth) |
| `PrinterCommandsOutputsTest.kt` | Output clamp authority (`clampOutputPct`, `outputPctToWire`) |
| `ScrubberMappingTest.kt` | `fractionFromX` pure mapping + `settleDispatchCount` (OnSettle once-per-gesture) |
| `OutputScrubberSettleTest.kt` | OnSettle dispatch-once guarantee |
| `OutputLedCommandTest.kt` | LED channel command gating |
| `CommandCatalogDriftTest.kt` | Any new command added must have `catalog.json` + `printer-matrix.json` rows |

**New tests needed (Wave 0 scaffolds — must compile from day one):**
- `IncrementPickerTest.kt` — step selection, active-step highlighting contract
- `AdjusterPanelTest.kt` — baseline "was X" shown/hidden logic (pure display predicate)
- `FineTuneScreenTest.kt` (or extend `FineTuneHolderTest`) — `nudge()` routes through clamp authority (integration)
- `TemperatureHolderTraceColorTest.kt` — trace color/visibility persistence shape

---

## Phase 24 / 25 Migration Precedent

Key patterns from Phases 24 and 25 that directly apply here:

| Pattern | Source | Applies To |
|---------|--------|------------|
| `FieldMode` sealed class for in-screen navigation | SpoolScreen, MacrosScreen (P25) | Fine-Tune (selected param), Outputs (selected output), Temperature (sensor morph + preset picker) |
| Two-overload stateless seam (`fun XxxScreen(holder)` + `fun XxxScreen(state)`) | SpoolScreen (P23/P25) | All rebuilt screens |
| `BoxWithConstraints` + `rememberUnitGrid(minOf(maxWidth, maxHeight))` | SpoolScreen, all P25 screens | All rebuilt screens |
| `gutter = null` + `FootButtonBar` in `field` | All P23/P25 rebuilds | All rebuilt screens |
| `FloatingEStop` as box overlay sibling | SpoolScreen (P23) | Temperature (printing-valid), Fine-Tune (printing-valid per P24 D-04) |
| `icon = DinghyIcons.X` overload (NOT `symbol = "ligature"`) | P25 pattern 7 | All rebuilt screens' `OutlinedControl` calls |
| `AppContainer.writeScope` for DataStore writes | P25 pattern 9 | Trace-color/visibility persistence (D-14) |
| P24 D-08 hide-not-grey capability gating | Phase 24 context | FW-retraction rows in Fine-Tune, output-pin type controls |
| P24 D-04 pop-to-root foot-guns (Extrude, Temperature, Fine-Tune stay mid-print) | Phase 24 context | FloatingEStop on Temperature + Fine-Tune; these screens are printing-list destinations |

**Fine-Tune + Temperature ARE printing-list destinations** (P24 D-07 / D-04): they are accessible during a print. Both must show `FloatingEStop` when `isPrinting == true`.

---

## Common Pitfalls

### Pitfall 1: Unclamped markPending target
**What goes wrong:** `holder.markPending(tuner, rawTarget)` where `rawTarget` was NOT passed through `PrinterCommands.clamp*()` first. If `rawTarget` is at or beyond the cap, the wire builder clamps it down — `markPending` armed an unreachable target — the busy lock wedges permanently until the 8s backstop fires.
**Why it happens:** Copy-paste of nudge logic without the clamp routing step.
**How to avoid:** ALWAYS follow the invariant: `val clamped = clamp*(target); markPending(tuner, clamped); dispatch(cmd, argsFor(target))`.
**Warning signs:** Fine-Tune group is dim + unresponsive for 8 seconds after hitting a ceiling value; `FineTuneHolderTest.markPending_skipArm_at_cap` fails.

### Pitfall 2: Rebuilding scrubber mid-drag
**What goes wrong:** The `working` MutableState is keyed in a way that causes Compose to re-create the `ScrubberPage` composable while the user is dragging. The new composable re-seeds `working` from the (now-changed) `value` prop; the fill jumps to the new position; the gesture is lost.
**Why it happens:** Wrapping `ScrubberPage` in a `key(output.currentPct)` block where `currentPct` changes because `OnSettle` dispatched a command that came back from Klipper mid-drag.
**How to avoid:** NEVER key `ScrubberPage` on the live value. Let it hold its own `working` state; only re-seed via `remember(value, range)` (which only fires on prop-change, not during drag).
**Warning signs:** Fill bar grows from center; value reverts to server value mid-drag. Matches P19 root cause exactly.

### Pitfall 3: Stacked "was X" baseline line overflowing
**What goes wrong:** Rendering "was X" as a second `Text` below the value text (a vertical stack) instead of inline on the same baseline row. On 5U phone-landscape the Focus has ~5 rows of usable vertical space; a stacked line pushes the stepper controls off-screen.
**Why it happens:** Natural instinct to stack text vertically.
**How to avoid:** Row/baseline-aligned; "was X" is a muted text span to the RIGHT of the value on the SAME line. See sketch-003 `adj-val` structure.
**Warning signs:** Controls disappear off the bottom of the Focus on a 5U portrait preview (`@Preview(widthDp=800, heightDp=480)` landscape check).

### Pitfall 4: FootButtonBar in the gutter slot
**What goes wrong:** New screens pass `FootButtonBar` to `ScreenScaffold`'s `gutter` parameter. In portrait orientation the gutter lives in a separate bottom pane — a gap appears between the list and the buttons.
**How to avoid:** Always `gutter = null`; `FootButtonBar` is the LAST element inside the `field` lambda Column.

### Pitfall 5: GraphViewHost thrashing (Temperature morph)
**What goes wrong:** The Focus morph (graph → adjuster) re-invokes the `AndroidView` factory for `GraphViewHost` on every swap, because the composable leaves the composition and re-enters.
**Why it happens:** Using `AnimatedContent` with a cross-fade that briefly removes the old child before showing the new one, or using a `when(condition)` that moves `GraphViewHost` between branches of the tree with different parent keys.
**How to avoid:** A simple `if (!showAdjuster) { GraphViewHost(…) } else { AdjusterFocus(…) }` in the same composition tree slot means Compose keeps or removes the node at the same position in the slot tree, and the `AndroidView` factory is only invoked on first composition — not on morph.
**Warning signs:** GraphViewHost flickers; trace data resets on adjuster open/close; Phase-22 `applyTokens` equality guard logs fire unexpectedly.

### Pitfall 6: DataStore write in composition scope (trace color persistence)
**What goes wrong:** D-14 requires trace color/visibility to persist. If the write is launched in `rememberCoroutineScope()`, a navigating-away cancels the coroutine before the write commits — a silent drop.
**How to avoid:** `container.writeScope.launch { container.dataStore.edit { … } }` — always the process-lifetime scope.
**Warning signs:** Trace color reverts on re-entry; same bug class as P14 switch-revert (see `dinghy-compose-write-scope-cancellation.md`).

### Pitfall 7: CommandCatalogDrift
**What goes wrong:** If the new screen needs a new CommandSpec (unlikely — this is a UX migration, not a new capability), adding it to `CommandRegistry.all` without updating `docs/commands/catalog.json` + `printer-matrix.json` rows causes `CommandCatalogDriftTest` to fail at the gate.
**How to avoid:** This phase is a UX migration — NO new CommandSpecs should be needed. All commands (`setHeater`, `setVelocityLimit`, `setSpeedFactor`, `setExtrudeFactor`, `setPressureAdvance`, etc.) are already registered. Verify before Wave 0.

---

## Code Examples

### FineTuneHolder — nudge pattern (exactly as existing VelocityLimitTile, reuse for new screen)

```kotlin
// Source: app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneShared.kt
// The clamp-routing pattern for motion limits — template for ALL tuner nudges in the new screen
fun nudge(target: Double) {
    // 17-07: feed markPending the SAME clamp the wire applies, so an at-cap '+' is a no-op
    val clamped = clampVelocityLimitTarget(field, target)
    markPending(tuner, clamped)
    dispatch(VelocityLimitArgs(field, target))
}
```

### ScrubberPage — OnSettle mode (Outputs inline Focus)

```kotlin
// Source: app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt
// For 0-100% output types in the new Outputs inline Focus
ScrubberPage(
    label = output.displayName,
    value = currentPct.toFloat(),
    range = 0f..100f,
    step = 1f,
    unit = "%",
    actions = ScrubberActions.OnSettle(
        onSettle = { pct ->
            val clamped = PrinterCommands.clampOutputPct(pct.roundToInt())
            val wire = PrinterCommands.outputPctToWire(clamped)
            // markPendingOutput(output.objectKey, wire)  // holder's pending flip for outputs
            dispatcher.dispatch(CommandRegistry.setGenericFan, SetGenericFanArgs(output.bareName, clamped))
        },
        onBack = { selectedOutput = null },
        onOff = { dispatcher.dispatch(CommandRegistry.setGenericFan, SetGenericFanArgs(output.bareName, 0)) },
        offLabel = "Off",
    ),
)
```

### Temperature — Field-takeover preset picker (D-12)

```kotlin
// Source pattern: SpoolScreen.kt FieldMode sealed class (Phase-23 pilot)
sealed class TempFieldMode {
    data object SensorList : TempFieldMode()
    data object PresetPicker : TempFieldMode()
}
// In TemperatureScreen content:
var fieldMode by remember { mutableStateOf<TempFieldMode>(TempFieldMode.SensorList) }
// ... in field = { when(fieldMode) { SensorList -> ...; PresetPicker -> ... } }
// FootButtonBar Presets button:
OutlinedControl("Presets", onClick = { fieldMode = TempFieldMode.PresetPicker }, ...)
```

### AdjusterPanel baseline invariant (host-testable pure predicate)

```kotlin
// Source: adjustment-controls.md sketch-003 spec
// "was X" shown only when changed from baseline — pure function, host-testable
internal fun shouldShowBaseline(value: Double?, baseline: Double?, decimalPrecision: Int): Boolean {
    if (value == null || baseline == null) return false
    val factor = 10.0.pow(decimalPrecision)
    return ((value * factor).roundToInt()) != ((baseline * factor).roundToInt())
}
```

---

## Validation Architecture

> `workflow.nyquist_validation` is `true` in `.planning/config.json`. This section is mandatory.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit4 (host-side) via Android Gradle; Kotlin coroutines test (`kotlinx-coroutines-test`) |
| Config file | `app/build.gradle.kts` (existing test config) |
| Quick run command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.finetune.* --tests works.mees.dinghy.designsystem.Scrubber* --tests works.mees.dinghy.command.PrinterCommands*"` |
| Full suite command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest"` |

### Success Criteria → Test Map

| SC | Behavior | Test Type | Command / Gate |
|----|----------|-----------|----------------|
| SC-1 | Temperature/Extrude/Outputs/Fine-Tune/FwRetraction/single-setting pages rebuilt on adjuster archetype; owner-approved flox both orientations | on-device UAT | Human verify at phase end |
| SC-2 | Keyboard-free in hot path; step-based where appropriate; inline "was X" shown when changed | Preview matrix + host unit | `@Preview` 6-combo + fs=L; `shouldShowBaseline()` unit test |
| SC-3 | Scrubber build-once; fill/thumb/value in-place during drag; one command on release | host unit | `ScrubberMappingTest`; `OutputScrubberSettleTest`; new `IncrementPickerTest` |
| SC-4 | ≥64px targets, fsSp S/M/L, rotation, `@Preview`, tokenized strings, registry icons | Preview matrix | `@Preview` matrix (6-combo + fs=L + landscape orientation) |
| SC-5 | No functional regressions; clamp authority + busy-lock intact | host unit | `FineTuneHolderTest` full; `PrinterCommandsTest` full; `CommandCatalogDriftTest` |

### Sampling Rate

- **Per task commit:** `E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.finetune.* --tests works.mees.dinghy.designsystem.* --tests works.mees.dinghy.command.PrinterCommands*`
- **Per wave merge:** Full host suite: `E:\Android\gw.bat :app:testDebugUnitTest`
- **Phase gate:** Full suite green + build `assembleDebug` + human UAT on flox before `/gsd-verify-work`

### Wave 0 Gaps (test scaffolds needed before implementation)

- [ ] `designsystem/components/IncrementPickerTest.kt` — step selection contract, active-state predicate
- [ ] `designsystem/components/AdjusterPanelTest.kt` — `shouldShowBaseline()` pure predicate
- [ ] `ui/finetune/FineTuneScreenNudgeTest.kt` — verifies `nudge()` routes through `PrinterCommands.clamp*()` before `markPending` (fails without the clamp-routing invariant)
- [ ] `ui/temperature/TemperatureHolderTraceColorTest.kt` — trace-color/visibility state transitions

*(Existing `FineTuneHolderTest`, `ScrubberMappingTest`, `OutputScrubberSettleTest`, `PrinterCommandsTest` require no new scaffolding — they guard existing behavior that must not regress.)*

---

## Security Domain

> `security_enforcement` is enabled (and `security_block_on: "high"`).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | No new auth surface |
| V3 Session Management | No | No new session state |
| V4 Access Control | No | Capability gating via `PrinterState.Capabilities` (existing) |
| V5 Input Validation | **Yes** | All numeric values MUST be clamped via `PrinterCommands.clamp*()` BEFORE formatting into gcode strings. This applies to numeric IME values (D-16) — read the value from the `TextField`, parse to `Double`/`Int`, clamp via `PrinterCommands`, then dispatch. Never concatenate unvalidated user input into a gcode string. |
| V6 Cryptography | No | No new crypto |

### Known Threat Patterns for This Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Numeric IME injection (D-07/D-16: new `TextField` fields for extrude distance/speed) | Tampering | `KeyboardType.Number`/`Decimal` restricts input; ALWAYS parse + `PrinterCommands.clamp*()` before building gcode. Never pass raw IME text to the wire. |
| At-cap dispatch (markPending target mismatch) | Denial of Service (busy-lock wedge) | The Phase-17 clamp invariant; mitigated by `clampVelocityLimitTarget` + wire-precision rounding. Regression test `FineTuneHolderTest.markPending_skipArm_at_cap`. |
| THEME-01 data-color carve-out (trace colors, filament hex in DetailCard) | None (visual, not functional) | Data carve-out is documented; colors are user-selected from a fixed 8-swatch pool — no arbitrary color construction from user text. |

**Input validation note for D-07 (numeric IME):** `NumpadPage` had a fixed input range baked into its range parameter. The replacement `BasicTextField` + numeric IME does NOT inherently enforce a range. The screen MUST add a range-clamped `onValueChange` handler (e.g., clamp the parsed double to `PrinterCommands.RETRACT_LEN_MIN..RETRACT_LEN_MAX`) or a `Done` handler that clamps before dispatch. This is the single new input-validation obligation introduced by the D-07 change.

---

## Environment Availability

Step 2.6: SKIPPED — no external tools or services are required beyond the existing build environment. All capabilities are Android SDK + the existing `gw.bat` build helper documented in `CLAUDE.md`.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Hub + group screens (FineTuneHub → Extrusion/Motion/FwRetraction) | Single flat-list screen with AdjusterPanel Focus (D-01) | This phase | Simpler nav; session memory replaces hub back-stack |
| ScrubberPage full-screen push for temp/fine-tune | AdjusterPanel (stepper) in Focus in-place | This phase | No push/pop; adjuster is always at hand |
| NumpadPage full-screen push | Android numeric IME in-place field | This phase | D-07; no custom UI needed for exact numeric entry |
| OutputScrubberDetail / OutputPinDetail / OutputLedDetail page pushes | In-Focus inline control (D-18) | This phase | Consistent with Spoolman's Detail-in-Focus shape |
| Preset scrim overlay (TemperatureScreen) | Field-takeover preset picker (D-12) | This phase | Matches the Field-takeover grammar; scrim retires on this screen |

**Deprecated/outdated:**
- `FineTuneHubScreen.kt` / `ExtrusionScreen.kt` / `MotionScreen.kt` / `FwRetractionScreen.kt`: deleted by this phase
- `NumpadPage.kt`: retired as a screen destination; deleted from the call graph
- `MeasuredWeightPage.kt`: replaced by Field-takeover with numeric IME
- `OutputScrubberDetail.kt` / `OutputPinDetail.kt` / `OutputLedDetail.kt`: logic migrates to Focus inline; files deleted

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The 8 Colorful pool colors for D-14 trace swatches are accessible from `ThemeResolver`/`TokenBridge` at runtime without needing a new API | Temperature anatomy | If the pool colors are not directly addressable as a list at runtime, a new `ThemeResolver` method will be needed (medium risk; the color system is already ported from `theme_theory`) |
| A2 | The Fine-Tune flat list of 14 params (including part-fan) fits comfortably in a 5U phone-landscape Field without scrolling being frustrating | Fine-Tune structure | At 5U, 14 rows at 1U each = 14U of content, definitely scrollable; the design (D-01) explicitly says "scrollable param list" so this is by design, not a bug |
| A3 | Group pool colors for D-02 can be picked from the COLORFUL theme pool (same pool used for D-14 temp trace swatches) — three distinct hues are available that are visually distinguishable | Fine-Tune structure (D-02) | Low risk — the Colorful pool has 8 colors; 3 distinct ones are trivially available |
| A4 | `TemperatureHolder`'s `series` and `setpoints` StateFlows already expose per-trace data structures that can be extended with color/visibility metadata without a full rewrite | Temperature anatomy | Medium risk — if the internal structure is a flattened list (not keyed by sensor name), extension requires a refactor of the holder's data model |
| A5 | `GraphViewHost` has parameters for per-trace color override (or can be extended cleanly) | Temperature anatomy | Medium — Phase-22 added `applyTokens` equality guards; the per-trace color extension must not break those guards |

**If this table's A4/A5 items are wrong:** The planner must add a Wave 0 investigation task to read `TemperatureHolder.kt` and `render/GraphViewHost.kt` in full before designing the extension API.

---

## Open Questions

1. **D-02 group pool color token names**
   - What we know: The COLORFUL theme pool is the source; `ThemeResolver`/`TokenBridge` in `works/mees/dinghy/theme/` port the `theme_theory` color system
   - What's unclear: Whether pool colors are directly addressable as a typed list (e.g. `pool.color(0)..pool.color(7)`) or only accessible via semantic roles
   - Recommendation: Planner adds a Wave 0 task to grep `ThemeResolver` for pool-color access patterns before coding D-02/D-14

2. **`TemperatureHolder.series` structure for per-trace color/visibility extension**
   - What we know: `series: StateFlow<List<GraphSeries>>` feeds `GraphViewHost`; the `GraphSeries` type carries the trace data
   - What's unclear: Whether `GraphSeries` has a `sensorName` key field that can serve as the Map key for trace color/visibility; whether `GraphViewHost` currently takes per-trace colors or only a fixed palette
   - Recommendation: Planner should read `TemperatureHolder.kt` in full and `render/GraphViewHost.kt` in full before Wave 1 planning

3. **NavHost route shape for the collapsed Fine-Tune**
   - What we know: Phase 24 established Navigation-Compose back-stack for the app; Fine-Tune's old sub-routes (MOTION, EXTRUSION, FW_RETRACTION) must be removed and the single route wired
   - What's unclear: Whether the NavHost route for FineTune uses `NavGraph.fineTune` style or a flat destination (check Phase 24's nav wiring)
   - Recommendation: Read `ui/shell/AppShell.kt` routing section before Phase 26 plan Wave 1

---

## Project Constraints (from CLAUDE.md)

- **minSdk 23 / Adreno 320 performance floor** — no new library deps; no looping animations; static glow only
- **`fsSp(baseSp, t.fs).sp` on ALL text** — floor: 15sp metadata, 17–18sp body, 20–22sp titles, 26sp stats
- **`DinghyIcons` registry only** — never auto-pick a glyph; check registry + `img/` + ASK if missing
- **`AppContainer.writeScope`** for ALL DataStore writes (trace-color/visibility persistence)
- **Build via `gw.bat`** from `E:\Android\` — `./gradlew` does not work from WSL
- **No `gutter` slot on redesigned screens** — `gutter = null`; `FootButtonBar` in `field`
- **Scrollable Fields suppress swipe-up drawer** — Fine-Tune and Outputs both have scrollable Fields; must be checked
- **Build-once scrubber rule** — never rebuild `ScrubberPage` mid-drag (P19 lesson; SC-3)
- **Clamp authority is in `PrinterCommands`, not in UI** — every `markPending` call feeds a pre-clamped target (D-22)
- **GSD workflow enforcement** — no direct repo edits outside a GSD workflow

---

## Sources

### Primary (HIGH confidence)
- `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneHolder.kt` — complete clamp-authority mechanism, markPending/wirePrecision/tolerance system
- `app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt` — all clamp constants and clamp functions (lines 62–180)
- `app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt` — build-once rule implementation
- `.claude/skills/sketch-findings-dinghy-display/references/adjustment-controls.md` — 3-zone adjuster spec + scrubber build-once rule
- `.claude/skills/sketch-findings-dinghy-display/references/foundations.md` — unit U, intent colors, icon registry law
- `.planning/phases/26-adjustment-screens/26-CONTEXT.md` — locked decisions D-01..D-24
- `.planning/phases/23-design-language-foundation/23-PATTERNS.md` — Phase-23 kit patterns
- `.planning/phases/25-browse-screens/25-PATTERNS.md` — FieldMode, two-overload seam, gutter=null patterns
- `docs/ui_design/COMPONENTS.md` — component class catalog
- `docs/ui_design/CLAUDE.md` — design philosophy non-negotiables + icon law

### Secondary (MEDIUM confidence — prior-phase design artifacts)
- `.planning/phases/24-navigation-spine/24-CONTEXT.md` — D-04 (pop-to-root: Extrude/Temp/FineTune mid-print valid), D-08 (hide-not-grey)
- `.planning/phases/25-browse-screens/25-CONTEXT.md` — D-12 (macro param Field-takeover, superseded by P26 D-07)
- `memory/MEMORY.md` — Phase 17 UAT lessons (Check 6 root cause), Phase 19 scrubber regression

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new external libraries; all patterns from verified in-repo code
- Architecture: HIGH — screens to migrate and their contracts are explicitly mapped in CONTEXT.md and directly inspected in source
- Pitfalls: HIGH — all documented from actual prior-phase bugs (P17 busy-lock wedge, P19 scrubber regression, P22 GraphViewHost equality guards) with root-cause analysis
- Phase-17 clamp mechanism detail: HIGH — read FineTuneHolder.kt in full
- Temperature graph extension feasibility: MEDIUM (A4/A5) — holder + GraphViewHost internal structure needs direct inspection before Wave 1 planning

**Research date:** 2026-06-10
**Valid until:** This is codebase-internal mapping — valid until the source files it references are modified (no external time decay)
