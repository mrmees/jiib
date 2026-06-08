# Phase 19: Output Controls — Fans, Lights & Generic Pins - Pattern Map

**Mapped:** 2026-06-07
**Files analyzed:** 14 new + 8 modified
**Analogs found:** 13 / 14 new have strong analogs (1 small new pure helper has none; 1 new toggle page reuses ScrubberPage scaffold)

This phase is unusually well-precedented: it composes the Calibration-hub flat-list grammar, the
ScrubberPage/ColorWheel single-setting controls, the PrinterCommands clamp authority + dispatch
spine, the configfile one-shot consumer, and the runtime-greyed drawer-tile gate — all existing.
Almost nothing is invented. **Concrete analog files and line ranges are below; copy from them.**

---

## File Classification

### New files (the `outputs/` feature package — mirror `calibration/`)

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `outputs/OutputDescriptor.kt` | model | transform | `calibration/CalibrationGate.kt` (RoutineEntry data class + pure support fn) | exact |
| `outputs/OutputsGate.kt` | utility (pure parse/gate) | transform | `calibration/CalibrationGate.kt` + `net/MoonrakerSession.kt:498-546` parse | exact |
| `outputs/OutputsHolder.kt` | holder/store | event-driven (subscribe→StateFlow) | `ui/finetune/FineTuneHolder.kt` (combine + markPending) / `calibration/CalibrationHubHolder.kt` | exact |
| `ui/outputs/OutputsScreen.kt` | screen (Compose) | request-response (list→detail) | `ui/calibration/CalibrationHubScreen.kt` | exact |
| `ui/outputs/OutputHeaterDetail.kt` | screen (Compose) | request-response | `designsystem/ScrubberPage.kt` + `setHeater` | role-match |
| `ui/outputs/OutputFanDetail.kt` | screen (Compose) | request-response | `designsystem/ScrubberPage.kt` | role-match |
| `ui/outputs/OutputLedDetail.kt` | screen (Compose) | request-response | `designsystem/ColorWheel.kt` + `ScrubberPage.kt` | role-match |
| `ui/outputs/OutputServoDetail.kt` | screen (Compose) | request-response | `designsystem/ScrubberPage.kt` | role-match |
| `ui/outputs/OutputPinDetail.kt` | screen (Compose) | request-response (branch pwm) | `ScrubberPage.kt` (pwm branch) + NEW toggle (digital) | partial |
| `ui/outputs/OutputPwmToolDetail.kt` | screen (Compose) | request-response | `designsystem/ScrubberPage.kt` | role-match |
| `ui/outputs/OutputToggleControl.kt` | component (NEW small) | request-response | `ui/calibration/CalibrationHubScreen.kt` `HubActionControl` + `OutlinedControl` | partial |
| `designsystem/HsvToRgb.kt` (or fold into ColorWheel area) | utility (pure helper) | transform | (no analog — `Color.hsv` usage in `ColorWheel.kt:140`) | **NONE** |
| `preview/OutputsPreviews.kt` | test (preview matrix) | n/a | `preview/SpoolPreviews.kt` | exact |
| `app/src/test/.../outputs/*Test.kt` | test (host JUnit) | n/a | `calibration/CalibrationGateTest`, `PrinterCommands*Test` | exact |

### Modified files

| Modified File | Change | Analog/Precedent |
|---------------|--------|------------------|
| `command/PrinterCommands.kt` | add clamp consts + `clampOutputPct`/clamp helpers + `setGenericFan`/`setLed`/`setServoAngle`/`setPinDigital`/`setPinPwm` builders (reuse existing `setHeater`) | Fine-Tune clamp block `:62-150`, builders `:225-417` |
| `net/MoonrakerSession.kt` | add a Phase-19 consumer to the SAME one-shot configfile block (`:490-547`); add output objects to the subscribe set | the four existing consumers at `:503-534` |
| `state/PrinterState.kt` | add `outputs: Map<String, OutputLiveValue>` (or per-field) for live speed/value/temperature/color_data keyed by rawName | `partFanSpeed: Double?` `:166-167`, structured objects pattern |
| `state/PrinterStateReducer.kt` | reduce `fan_generic`/`led`/`servo`/`output_pin`/`pwm_tool`/`heater_generic` live fields into the new map | `fan` reduce `:134`, `temperature_sensor` loop `:253-259`, heater loop `:232-237` |
| `state/DeriveCapabilities.kt` / `Capabilities.kt` | (optional) confirm whitelisted objects flow through `objects`; no new field strictly needed — gate reads `caps.objects` | `Capabilities.objects` `:13`, `hasObject` `:52` |
| `ui/route/TopRoute.kt` | add `Outputs` to the `Dest` enum (`:37`) | enum extension precedent (Calibration/FineTune/Spool added the same way) |
| `ui/shell/AppDrawer.kt` | flip the existing GREYED `Output` placeholder (`:203`, `dest = null`, symbol `bolt`) to a LIVE runtime-gated tile (`dest = Dest.Outputs`, symbol per D-07 = `output`), add `outputsEnabled` param + gate (mirror Spool/Webcam) | Spool tile `:179-185`, runtime gate `:228-230` |
| `ui/shell/AppShell.kt` | assemble `OutputsHolder`, host `Dest.Outputs` (hub + per-type detail local back-stack), suppress drawer, thread `outputsEnabled` into `AppDrawer` | Calibration host `:579-640`, holder assemble `:299`, drawer call `:850-859`, suppress set `:494` |
| `di/AppContainer.kt` | expose `outputsPresent: Flow<Boolean>` (non-empty descriptors) for D-10 tile gate | `spoolmanPresent` `:367`, `webcamCount`/`webcamTileEnabled` `:333-344` |
| `designsystem/icons/DinghyIcons.kt` | **D-08:** reassign `FanMode` `mode_fan`→`air` (`:80`); register new output ligatures (`mode_heat`/`mode_fan_2`/`lightbulb_2`/`cyclone`/`check_box`/`vital_signs`/`output`) + add to `all` (`:109-118`) | registry entries `:26-102`, `all` list `:109` |
| `tools/verify_ligatures.py` (NEEDED set) | extend with the 8 D-09 glyphs (already verified PASS) | research §D-09 gate |

---

## Pattern Assignments

### `ui/outputs/OutputsScreen.kt` (screen, request-response) — flat list → detail

**Analog:** `ui/calibration/CalibrationHubScreen.kt` (the EXACT precedent — a hub of tappable rows
→ detail pages, drawer-suppressed, Back-only gutter, static styling, token-routed).

**Differences to apply:** Calibration uses a `LazyVerticalGrid` of square tiles; the Outputs screen
is a flat scrollable **list** (`LazyColumn`) of rows (icon + prettyName + live value), alpha-sorted.
Otherwise the scaffold/gutter/Back grammar is identical.

**Scaffold + Back-only gutter pattern** (`CalibrationHubScreen.kt:58-107`):
```kotlin
@Composable
fun CalibrationHubScreen(holder, onNavigate, onBack, modifier) {
    val routines by holder.routines.collectAsStateWithLifecycle()
    val t = LocalTokens.current
    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            field = { /* title Text + Lazy{Grid|Column} of rows, items(key = {...}) */ },
            gutter = { Box(Modifier.fillMaxWidth().padding(8.dp)) {
                HubActionControl(label = "Back", onClick = onBack, intent = Intent.Neutral) // plain nav = neutral
            } },
        )
    }
}
```

**Row grammar** — adapt `RoutineTile` (`CalibrationHubScreen.kt:114-148`) into a horizontal row:
icon (left) via `MaterialSymbol(name = …, tint = …, sizeSp = fsSp(40f, t.fs))`, prettyName (middle),
live value (right, hidden when absent but row stays `clickable`). Outline = `t.outline`/`t.hair`,
fill `t.surface2`, shape `RoundedCornerShape(t.rCtrl)`. **Font sizes:** use the established
`fsSp(baseSp, t.fs)` scale (see CLAUDE memory: floor 15sp metadata, 17-18sp body, 24sp titles).

**Icon per row by output family** — `DinghyIcon` tokens (D-01..D-06), NOT raw ligature strings.
Use the `CalibrationRoutine.glyph` extension idiom (`CalibrationHubScreen.kt:155-162`) but resolve to
the new registry entries (`DinghyIcons.OutputHeater`, etc.). **Never invent — owner-locked D-01..D-06.**

---

### `outputs/OutputsHolder.kt` (holder, event-driven)

**Analog:** `ui/finetune/FineTuneHolder.kt` (combine of printerState + capabilities + inFlight +
pending into a vm; the markPending state-flip busy lock) AND the simpler
`calibration/CalibrationHubHolder.kt` template for the descriptor list.

**Simple descriptor-list holder template** (`CalibrationHubHolder.kt:24-39`):
```kotlin
class CalibrationHubHolder(scope: CoroutineScope, store: PrinterStateStore) {
    private val _routines = MutableStateFlow(calibrationSupport(store.capabilities.value))
    val routines: StateFlow<List<RoutineEntry>> = _routines.asStateFlow()
    init { scope.launch { store.capabilities.collect { _routines.value = calibrationSupport(it) } } }
}
```
The OutputsHolder additionally folds in `store.printerState` (live values per `rawName`) — use the
`combine(...)` shape from `FineTuneHolder.kt:128-148`. Capabilities folded as a FLOW (re-emit on
reconnect) — `FineTuneHolder.kt:131` comment "REVIEW #10".

**Display scaling lives in the holder, not the reducer** (`FineTuneHolder.kt:47-49`, `buildVm:286-325`):
the reducer stores RAW 0..1; the holder converts `speed*100`/`value*100` to display %. Mirror this.

**Per-detail busy + markPending state-flip lock** (`FineTuneHolder.kt:154-203`): if Phase 19 wants
state-flip-confirmed feedback (SC-2 "confirmed by state flip"), copy `markPending`/`clearPending`/
`reached`/`toleranceFor` — **and** the 17-07 lesson: the markPending target MUST be the SAME clamped
value the wire uses (feed it `PrinterCommands.clamp*(...)`), or the busy lock wedges. The simpler
alternative (toast-only, no state-flip lock) is also acceptable per staging "toasts are enough" —
flag for planner. Busy state is **scoped to the detail page** (staging), not the whole list.

---

### `outputs/OutputsGate.kt` + `OutputDescriptor.kt` (pure parse + model, transform)

**Analog:** `calibration/CalibrationGate.kt` (pure predicate + `RoutineEntry` data class + sorted
support list) for the SHAPE; `net/MoonrakerSession.kt:490-546` for the configfile.settings PARSE.

**Pure-gate data-class + sorted-support pattern** (`CalibrationGate.kt:30-43`):
```kotlin
data class RoutineEntry(val routine: CalibrationRoutine, val isSupported: Boolean)
fun calibrationSupport(caps: Capabilities): List<RoutineEntry> =
    CalibrationRoutine.entries.map { RoutineEntry(it, calibrationSupported(caps, it)) }
        .sortedByDescending { it.isSupported }
```
Mirror as `OutputDescriptor(rawName, family, bare, prettyName, pwm, servoAngleMax, readOnly)` +
`fun parseOutputs(settings: JsonObject, liveObjects: Set<String>): List<OutputDescriptor>` sorted
alpha by prettyName. Pure, no I/O, no Compose — host-tested (matches `CalibrationGateTest`).

**configfile.settings parse + case-recovery** — research §Pattern 1 + Pitfall 1. `settings` keys are
LOWERCASED; recover the case-correct command name by case-insensitive match against
`Capabilities.objects` (the `hasMacroIgnoreCase` lesson, `Capabilities.kt:49`). Whitelist filter on
`it.key.substringBefore(' ')`. Read `pwm` (real bool) + `maximum_servo_angle` from settings.

**Walk JsonObject** with the existing helpers (`objectOrNull`/`booleanOrNull`/`floatOrNull`/
`doubleOrNullAt`) used throughout `MoonrakerSession.kt:499-534`.

---

### `command/PrinterCommands.kt` (utility, transform) — clamp authority + builders

**Analog:** itself — the Fine-Tune clamp block and existing builders. **`setHeater` already exists
and works for `heater_generic`** (`:223-226`, its KDoc literally cites `heater_generic chamber`).

**Clamp-authority pattern to mirror** (`PrinterCommands.kt:62-150`): named `*_MIN`/`*_MAX` consts +
one-liner `clamp*` helpers; builders delegate to the clamp; the markPending call site feeds the SAME
clamp (17-07). Add `OUTPUT_PCT_MIN/MAX = 0/100`, `SERVO_ANGLE_MAX` default 180 (per-descriptor
override), reuse `MIN/MAX_TEMP_C`. **0..100% → /100 → 0..1 wire** (research Pitfall 3).

**Builder pattern** (`setFan:394-398`, `setRetraction:406-417`, `fmt:484-488`, `scriptParams:457`):
```kotlin
fun setGenericFan(name: String, pct: Int): String =      // SET_FAN_SPEED, wire 0..1
    "SET_FAN_SPEED FAN=$name SPEED=${fmt(pct.coerceIn(0,100)/100.0, 2)}"
fun setLed(name, r, g, b, w): String = buildString { append("SET_LED LED=$name"); /* RED= GREEN= BLUE= each clamped 0f..1f, fmt(,2) */ }
fun setServoAngle(name, deg, maxDeg): String = "SET_SERVO SERVO=$name ANGLE=${deg.coerceIn(0,maxDeg)}"
fun setPinDigital(name, on): String = "SET_PIN PIN=$name VALUE=${if (on) 1 else 0}"
fun setPinPwm(name, pct): String = "SET_PIN PIN=$name VALUE=${fmt(pct.coerceIn(0,100)/100.0, 2)}" // pwm_tool too — NO SET_PWM_TOOL
```
Use the single `fmt(v, decimals)` chokepoint (Locale.US, strips trailing zeros) — never inline
`String.format`. Wrap for dispatch via `scriptParams(gcode)` → `printer.gcode.script`.
Exact strings/ranges are research §Code-Examples (all live-verified).

---

### `ui/outputs/Output{Fan,Heater,Servo,PwmTool,Pin-pwm}Detail.kt` (screens, request-response)

**Analog:** `designsystem/ScrubberPage.kt` (the keyboard-free single-setting page — fan %, servo
angle, heater °C, output_pin/pwm_tool %). LED brightness scrubber too.

**ScrubberPage signature + immediate-dispatch caveat** (`ScrubberPage.kt:87-99`): it has an
`onApply`/`onCancel` **commit contract** — but staging mandates **immediate dispatch, no Apply flow**.
Per research §Per-Type-Control-Page-Shape, **dispatch on settle** (pointer-up) the way ColorWheel
does (`onSettle`), via `onValueChange` debounced/on-settle; hide or repurpose Apply. This is the one
ScrubberPage-fit decision — flag for planner. (Off/zero is a separate Off action that dispatches `…=0`.)

**Dispatch + failure-toast + busy wiring** (`ui/finetune/ExtrusionScreen.kt:60-104`):
```kotlin
val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
val inFlight by remember(dispatcher) { dispatcher?.inFlight ?: MutableStateFlow(emptySet()) }
                  .collectAsStateWithLifecycle(initialValue = emptySet())
var failureText by remember { mutableStateOf<String?>(null) }
LaunchedEffect(dispatcher) { dispatcher?.events?.collect { (it as? DispatchEvent.Failure)?.let { e -> failureText = e.message; holder.clearPending() } } }
// dispatch: dispatcher?.dispatch(key, JsonRpcMethods.GCODE_SCRIPT, PrinterCommands.scriptParams(gcode))
// failure renders SeverityToast(Severity.Error, msg) and STAYS on the page (staging rule)
```
Busy is per-detail-page (scope the `inFlight` check to this output's dispatch key). Off action stays
enabled (dispatches immediately). **No ConfirmGuard** even for pins/PWM (staging — do NOT wrap in
`ConfirmGuard`).

---

### `ui/outputs/OutputLedDetail.kt` (screen, request-response) — RGB + brightness + Off

**Analog:** `designsystem/ColorWheel.kt` (hue ring) + `ScrubberPage.kt` (brightness) + a NEW pure
`hsvToRgb` helper. **ColorWheel is HUE-ONLY** (`ColorWheel.kt:57-63`: `hue: Float, onHandleMove,
onSettle`) — research recommends fixing saturation = 1.0 and exposing hue + brightness for v1
(reuses ColorWheel verbatim). **Open Question 1 — confirm with owner if full saturation wanted.**

**Settle-not-stream contract** (`ColorWheel.kt:27-56` KDoc): `onHandleMove` is cheap (handle repaint
only); the actual command dispatch happens on `onSettle` (pointer-up). Use this for the LED color
dispatch cadence (D-07 perf rule — Adreno-320 floor; no per-frame wire spam).

**Carve-out** (`ColorWheel.kt:45-52`): the hue ring + handle + LED swatch render their LITERAL color
(the THEME-01 data-color carve-out, same as the spool filament color); all CHROME routes through
`LocalTokens`. CONTEXT canonical-refs reiterate this LED carve-out.

**D-12 Off action:** an explicit Off button → `setLed(name, 0,0,0,0)`. Intent = `Intent.Danger` (red)
or neutral per the established Off pattern; place it per Claude's Discretion. Off dispatches immediately.

---

### `ui/outputs/OutputPinDetail.kt` + `OutputToggleControl.kt` (digital toggle — NEW small piece)

**Analog:** no on/off page exists. Build a small toggle page mirroring the `ScrubberPage`
`ScreenScaffold` scaffold (`ScrubberPage.kt:129-248`) — Field = one big On/Off toggle, gutter = Back
(+ optional Off). Reuse `HubActionControl`/`OutlinedControl` (`CalibrationHubScreen.kt:174-200`) for
the toggle buttons with `Intent` colors. Branch on the descriptor's `pwm` flag (`OutputPinDetail`
routes to toggle when `pwm:false`, to the `%` scrubber when `pwm:true`). Both dispatch `SET_PIN`.

---

### `designsystem/HsvToRgb.kt` (utility, transform) — **NO ANALOG**

The one genuinely-new pure piece: `fun hsvToRgb(hue, sat, value): Triple<Float,Float,Float>` (each
0f..1f) feeding `PrinterCommands.setLed`. Not a hand-rolled picker — a tiny pure function. Host-test
it. Precedent for the math: `Color.hsv(hue, sat, value)` is used in `ColorWheel.kt:140`.

---

### `preview/OutputsPreviews.kt` (test, preview matrix)

**Analog:** `preview/SpoolPreviews.kt` — the canonical PREVIEW_AND_TOKENS exemplar
(`SpoolPreviews.kt:14-58`). Copy its structure: a `@Preview` matrix over 6 theme combos + `fs=L`
overflow + RTL spot-check, driven by `PreviewParameterProvider` + `PreviewBox` seed wrappers, using
**stateless screen overloads** fed pure `SampleFixtures` (NO live Moonraker/holder/dispatcher).
**fs is injected via the seed, never the `@Preview(fontScale=…)` annotation (no-op).** Each Outputs
screen + detail page ships a preview matrix from day one (per `docs/ui_design/PREVIEW_AND_TOKENS.md`).

---

### Tests (host JUnit) — `app/src/test/.../outputs/`

**Analogs:** `CalibrationGateTest` (pure gate), `PrinterCommands*Test` (builder/clamp/scaling).
Research §Validation maps each SC → test. **Use the live capture JSON as fixtures** (E5P: 9 outputs;
E3P: 1 virtual pin) — closes the mock-vs-reality trap (`OutputsGateTest` must parse the REAL
Moonraker shape, not an idealized mock). Wave-0 RED stubs must COMPILE (memory:
wave0-red-scaffold-compile).

---

## Shared Patterns

### Command dispatch + clamp authority
**Source:** `command/CommandDispatcher.kt` (`dispatch` `:108-157`, busy/inFlight `:85-87`, failure
events `:89-94`) + `command/PrinterCommands.kt` (clamp `:62-150`, `scriptParams:457`, `fmt:484`).
**Apply to:** every detail page. Dispatch via `dispatcher.dispatch(key, JsonRpcMethods.GCODE_SCRIPT,
PrinterCommands.scriptParams(gcode))`. Single-source clamp in PrinterCommands (17-07). Failure →
`SeverityToast`, stay on page. Object names are printer-supplied identifiers (ASVS V5 — no free-text).

### Drawer-tile capability gate (D-10)
**Source:** `ui/shell/AppDrawer.kt` runtime-greyed Spool tile (`:179-185`, gate `:228-230`) +
`di/AppContainer.kt` `spoolmanPresent` (`:367`). **Apply to:** the Output tile. Note the existing
GREYED placeholder `DrawerTileSpec(label = "Output", symbol = "bolt", dest = null)` (`:203`) — flip
it to `dest = Dest.Outputs`, change symbol to `output` (D-07), add an `outputsEnabled` runtime gate
threaded from `AppContainer.outputsPresent` exactly as Spool/Webcam do. **D-10 = hide entirely when
zero outputs.** ⚠ The Spool/Webcam pattern GREYS the tile when disabled; D-10 wants it HIDDEN. Either
filter `DRAWER_TILES` (omit when `!outputsEnabled`) or extend the gate to drop-not-grey — planner picks.

### Drawer suppression + Back-only gutter
**Source:** `ui/shell/AppShell.kt` suppress set (`:494` — Calibration/Files/Console/Macros/Spool are
listed) + `CalibrationHubScreen.kt` Back-only gutter (`:95-104`). **Apply to:** add `Dest.Outputs` to
the suppression set; the Outputs list (a scroll Field) suppresses the swipe-up drawer. Gutter = Back only.

### Live values from central subscribe
**Source:** `state/PrinterStateReducer.kt` (`fan` reduce `:134`, `temperature_sensor` UPDATE-ON-PRESENT
loop `:253-259`, heater field-merge `:232-237`) + `state/PrinterState.kt` (`partFanSpeed:166`).
**Apply to:** reduce `fan_generic .speed`, `heater_generic .temperature/.target`, `led .color_data`,
`servo/output_pin/pwm_tool .value` into a new `outputs` map keyed by rawName. RAW values (no scaling
in reducer — Pitfall 1). Absent field → retained/null → row hides value, stays tappable (SC-3).
Add the whitelisted object names to the subscribe set (where the subscribe subset is built).

### Icon registry (D-01..D-08)
**Source:** `designsystem/icons/DinghyIcons.kt` (entries `:26-102`, `all` list `:109-118`, FanMode
`:80`). **Apply to:** register the 7 new output ligatures as `DinghyIcon(IconRef.Ligature("..."),
alternate = "...")`, add to `all`, and **reassign `FanMode` `mode_fan`→`air` (D-08)** — its ONE
consumer is `ui/finetune/ExtrusionScreen.kt:271` (verify no regression). All 8 glyphs verified PASS
(research §D-09); extend `verify_ligatures.py` NEEDED set. Owner-locked — never substitute.

### Holder assembly + dest hosting
**Source:** `ui/shell/AppShell.kt` (`CalibrationHubHolder` assemble `:299`
`remember(store) { CalibrationHubHolder(scope, store) }`, Calibration dest host with local
hub↔detail back-stack `:579-640`, BackHandler `:437`, drawer call `:850-859`). **Apply to:** assemble
`OutputsHolder`, host `Dest.Outputs` as hub-list ↔ per-type detail (a local back-stack like
Calibration's `calibrationRoutine` / FineTune's group), wire system Back to pop detail→list.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `designsystem/HsvToRgb.kt` | utility | transform | No HSV→RGB helper exists; ColorWheel is hue-only and uses `Color.hsv` for RENDER, not for emitting `SET_LED` floats. Tiny pure fn — host-test it. Planner: confirm fixed-saturation v1 (Open Q1). |

(The digital **toggle page** has no exact analog page, but it reuses the `ScreenScaffold` scaffold
from `ScrubberPage` + `OutlinedControl` buttons — a partial match, not a true greenfield.)

---

## Planner Decision Flags (from research, surfaced here)

1. **ScrubberPage immediate-dispatch wiring** — `onApply`/`onCancel` commit contract vs staging's
   "no Apply flow". Recommend **dispatch on settle** (pointer-up), like ColorWheel `onSettle`. (Research Q2.)
2. **LED saturation** — ColorWheel is hue-only; recommend hue + brightness (fixed saturation 1.0) for
   v1. Owner-confirm if full saturation wanted. (Research Q1 / Assumption A4.)
3. **D-10 hide-vs-grey** — the existing Spool/Webcam tile gate GREYS; D-10 wants the Output tile HIDDEN
   when zero outputs. Filter `DRAWER_TILES` or extend the gate. (CONTEXT D-10.)
4. **D-07 tile icon** — the existing `Output` placeholder uses `bolt`; D-07 mandates `output`. Change it.
5. **State-flip busy lock vs toast-only** — staging says "toasts are enough"; FineTuneHolder's
   markPending lock is available if SC-2 "confirmed by state flip" is read strictly. If used, feed the
   CLAMPED target (17-07). (CONTEXT / FineTuneHolder.)

---

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/{outputs(new),calibration,ui/calibration,
ui/finetune,designsystem,designsystem/icons,command,net,state,ui/shell,ui/route,di,preview}`
**Files scanned:** ~25 (read in full: CalibrationHubScreen, CalibrationGate, CalibrationHubHolder,
ProbePresentGate, ScrubberPage, ColorWheel, PrinterCommands, CommandDispatcher, CommandSpec,
DinghyIcons, FineTuneHolder, AppDrawer, TopRoute, Capabilities; grepped: MoonrakerSession,
PrinterState, PrinterStateReducer, AppShell, ExtrusionScreen, SpoolPreviews, AppContainer)
**Pattern extraction date:** 2026-06-07
