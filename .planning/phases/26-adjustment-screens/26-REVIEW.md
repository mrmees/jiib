---
phase: 26-adjustment-screens
reviewed: 2026-06-10T23:52:45Z
depth: standard
files_reviewed: 44
files_reviewed_list:
  - app/src/main/java/works/mees/dinghy/command/MacroInvocation.kt
  - app/src/main/java/works/mees/dinghy/designsystem/components/AdjusterPanel.kt
  - app/src/main/java/works/mees/dinghy/designsystem/components/IncrementPicker.kt
  - app/src/main/java/works/mees/dinghy/designsystem/control/OutlinedControl.kt
  - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
  - app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt
  - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
  - app/src/main/java/works/mees/dinghy/DinghyApp.kt
  - app/src/main/java/works/mees/dinghy/preview/AdjusterPreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/ExtrudePreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/FineTunePreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/MacrosPreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/OutputsPreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt
  - app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/TemperaturePreviews.kt
  - app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt
  - app/src/main/java/works/mees/dinghy/render/GraphView.kt
  - app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneParams.kt
  - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneShared.kt
  - app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/macros/MacroModels.kt
  - app/src/main/java/works/mees/dinghy/ui/outputs/OutputFocusControl.kt
  - app/src/main/java/works/mees/dinghy/ui/outputs/OutputsScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/outputs/OutputToggleControl.kt
  - app/src/main/java/works/mees/dinghy/ui/settings/TraceStylePrefs.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt
  - app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt
  - app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureHolder.kt
  - app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt
  - app/src/main/res/values/strings.xml
  - app/src/test/java/works/mees/dinghy/command/MacroInvocationTest.kt
  - app/src/test/java/works/mees/dinghy/designsystem/components/AdjusterPanelTest.kt
  - app/src/test/java/works/mees/dinghy/designsystem/components/IncrementPickerTest.kt
  - app/src/test/java/works/mees/dinghy/di/AppContainerTest.kt
  - app/src/test/java/works/mees/dinghy/shell/ShellNavStateTest.kt
  - app/src/test/java/works/mees/dinghy/theme/ThemeOverrideTest.kt
  - app/src/test/java/works/mees/dinghy/ui/finetune/FineTuneScreenNudgeTest.kt
  - app/src/test/java/works/mees/dinghy/ui/temperature/TemperatureHolderTraceStyleTest.kt
findings:
  critical: 4
  warning: 11
  info: 7
  total: 22
status: issues_found
---

# Phase 26: Code Review Report

**Reviewed:** 2026-06-10T23:52:45Z
**Depth:** standard
**Files Reviewed:** 44
**Status:** issues_found

## Summary

Phase 26 rebuilt the adjustment screens (Fine-Tune flat list, Temperature morph, Outputs detail-in-Focus, Extrude conformance, numeric-IME takeovers) on the new design-system primitives. The clamp-authority chain (D-22 / P17 Check-6 class) is **intact**: `nudge()` clamps via `clampForTuner` before `markPending`, `FineTuneHolder.markPending` rounds to per-tuner wire precision internally, the command builders re-clamp the raw target identically, and the Outputs dispatch keys match `CommandRegistry` exactly. `MacroInvocation.buildTyped` remains the sole gcode-assembly path for macro params (REJECT-not-escape verified, incl. the WR-04 unquoted-numeric guards), and all new DataStore stores (`TraceStylePrefs`) correctly route writes through `AppContainer.writeScope`.

However, the review found four Critical defects: the Temperature adjuster operates on a **frozen sensor snapshot** (repeated ± taps re-dispatch the same target and the displayed value never updates), an **off heater cannot be heated at all** from the adjuster, `fmtValue` **displays wildly wrong numbers** for 0-decimal tuners (e.g. 1500 mm/s² renders as "15"), and the Outputs Focus scrubber's stale `pointerInput` closure can **dispatch a command to the previously selected output** after switching between same-family outputs. Eleven warnings cover an ineffective in-flight guard, a silently dropped failure-toast path on Extrude, IME display/state divergence, a retraction-speed round-vs-truncate mismatch that re-opens an 8s transient busy dim, duplicated e-stops, and convention violations.

## Critical Issues

### CR-01: Temperature adjuster reads a frozen SensorReadout — repeated ± taps dispatch the same target and the display never updates

**File:** `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt:305, 373, 381, 386-393`
**Issue:** `selectedSensor` is a `remember { mutableStateOf<SensorReadout?>(null) }` set once from the row tap (line 440). The adjuster morph reads `val sensor = selectedSensor!!` and seeds `currentTarget = sensor.target` from that **snapshot**. Live `legend` updates never refresh `selectedSensor`, so:
1. The big value in `AdjusterPanel` is frozen at the target captured at selection time.
2. Each `onIncrement`/`onDecrement` computes `base = currentTarget` from the same frozen value — tapping "+5" three times dispatches 205, 205, 205 (not 205, 210, 215). The adjuster can never move more than one step from the selection-time target.
This is exactly the stale-Compose-state class the phase was warned about (domain invariant #2).
**Fix:** Store the selected sensor **name**, and resolve the live readout from `legend` on every composition:
```kotlin
var selectedName by remember { mutableStateOf<String?>(null) }
val selectedSensor = selectedName?.let { n -> legend.firstOrNull { it.name == n } }
```
(`selectedSensor` then disappears from the list automatically if the sensor vanishes on reconnect.)

### CR-02: An off heater cannot be turned on from the Temperature adjuster — the intended fallback is dead code

**File:** `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt:653-654, 661-673`
**Issue:** `TemperatureAdjusterFocus` passes `value = currentTarget` to `AdjusterPanel`. When the heater is off (`target == null`), `AdjusterPanel` renders DASH and its `onClick` guard `if (enabled && value != null)` makes both stepper tiles inert — so the primary heater-setpoint control cannot set a temperature on an idle heater (only Presets can). The locals at lines 653-654:
```kotlin
val currentValue: Double? = currentTarget ?: sensor.current.let { if (it > 0.0) it else null }
val baselineTarget: Double? = currentTarget
```
are computed and **never used** — clear evidence the intent was to seed the adjuster from the live temperature when no target exists. The `?: 0.0` fallbacks in `onDecrement`/`onIncrement` (lines 666, 670) are unreachable because the panel guard already blocks the null-value case.
**Fix:** Pass `value = currentValue` (the unused local) to `AdjusterPanel`, and base the nudges on it so an off heater steps up from its current temperature (then `clampHeaterTarget` keeps it legal). Delete the dead `baselineTarget`.

### CR-03: `fmtValue` corrupts 0-decimal displays ending in zero — "1500" renders as "15", "3000" as "3"

**File:** `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneShared.kt:23-31`
**Issue:** For a non-whole value with `decimals = 0`, the formatted string has **no decimal point**, yet `trimEnd('0')` is applied unconditionally:
```kotlin
"%.${decimals}f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')
```
`fmtValue(1499.5, 0)` → `"1500"` → `trimEnd('0')` → `"15"`. `fmtValue(2999.9999, 0)` → `"3"`. Reachable for every Double-typed 0-decimal tuner — MAX_VELOCITY, MAX_ACCEL, RETRACT_SPEED, UNRETRACT_SPEED — whenever Klipper reports a fractional value (float noise like `2999.9999999` or a config like `max_accel: 1499.5`). The corrupted number feeds both the Fine-Tune list trailing readout and the `AdjusterPanel` hero value/baseline — a user adjusting physical motion limits sees an order-of-magnitude-wrong reading.
**Fix:** Only strip trailing zeros when the string actually contains a decimal point:
```kotlin
val s = "%.${decimals}f".format(java.util.Locale.US, v)
return if ('.' in s) s.trimEnd('0').trimEnd('.') else s
```
Add regression tests: `fmtValue(1499.5, 0) == "1500"`, `fmtValue(2999.9999, 0) == "3000"`.

### CR-04: Outputs Focus scrubber — stale `pointerInput` closure can dispatch to the PREVIOUSLY selected output, and the drag preview freezes after the first echo

**File:** `app/src/main/java/works/mees/dinghy/ui/outputs/OutputsScreen.kt:146-155`; `app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt:152, 203-222`; `app/src/main/java/works/mees/dinghy/ui/outputs/OutputFocusControl.kt:423-437`
**Issue:** `ScrubberControl`'s gesture detector is `pointerInput(range, step)`. The suspend block (and the local `set`/`setFromX`/`settle` functions it captures) is only restarted when `range`/`step` change — a documented Compose foot-gun: recomposition with new captured values does NOT refresh a running handler. Two consequences:
1. **Command misdirection (the Critical one).** Switching the selection between two outputs of the same family (e.g. fan A → fan B: identical `range = 0f..100f`, `step = 1f`) reuses the same composition slot and the same pointer-input node — no `key()` wrapper exists (the comment at OutputsScreen.kt:146 explicitly forbids `key(selectedKey)`, over-generalizing the P19 "no `key(value)`" rule). If fan A's scrubber was ever touched (the handler coroutine is live), the next drag on "fan B's" scrubber settles through fan A's captured `dispatchFan`/`descriptor` — the wire command goes to **fan A**. A physical command lands on the wrong device.
2. **Frozen live preview.** `working` is re-seeded by `remember(value, range)` whenever the dispatched echo updates `value`; the still-running gesture closure keeps writing the **old** `MutableFloatState` object, which nothing reads anymore. After the first settle+echo, subsequent drags no longer move the fill/value visually (the dispatched value is still correct, the UI just stops tracking) — the P19 fa97efb "value-not-sticking" class resurfacing through the inline-Focus hosting where `value` is now live.
**Fix:** Two parts:
- In `OutputsContent`, wrap the Focus control in `key(selectedRow.descriptor.objectKey) { OutputFocusControl(...) }` — keying on output **identity** (which cannot change mid-drag) is not the forbidden `key(value)` per-frame rebuild.
- In `ScrubberControl`, route the changing captures through `rememberUpdatedState` so the long-lived gesture handler always sees current values:
```kotlin
val currentActions by rememberUpdatedState(actions)
val currentOnValueChange by rememberUpdatedState(onValueChange)
```
and read those inside `set`/`settle`. (The same hardening applies to `ScrubberPage`/`LedBrightnessControl`.)

## Warnings

### WR-01: Temperature heater-nudge in-flight guard compares the clamped VALUE against dispatch KEYS — never matches

**File:** `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt:196-203`
**Issue:** `if (clamped.toString() !in inFlight)` — `inFlight` holds dispatch keys like `"set_heater_extruder"`; `clamped.toString()` is `"210"`. The condition is always true, so the dedup guard is a no-op and rapid ± taps stack dispatches.
**Fix:** `if ("set_heater_$sensorName" !in inFlight) { ... }` (the same key passed into `SetHeaterArgs`).

### WR-02: Extrude dispatch failures are silently dropped — the promised failure toast can never appear

**File:** `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt:184, 235, 347`
**Issue:** The live overload's `onDispatchFailure = { /* surfaced by LaunchedEffect from dispatcher events below */ }` references a collector that does not exist — nothing in the file collects `dispatcher.events`. `failureText` in `ExtrudeContent` is declared, auto-cleared by a `LaunchedEffect`, and rendered, but **never set**; `onDispatchFailure` is a dead parameter. A failed extrude/retract/heater dispatch gives the user zero feedback (the KDoc explicitly promises a `SeverityToast` on `DispatchEvent.Failure`).
**Fix:** Add the same `LaunchedEffect(dispatcher) { d.events.collect { if (it is DispatchEvent.Failure) ... } }` collector used by FineTuneScreen/TemperatureScreen, plumb the message into `ExtrudeContent` (hoist `failureText` to the live overload), and delete the dead `onDispatchFailure` parameter.

### WR-03: Extrude numeric IME display diverges from the clamped dispatched value

**File:** `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt:489-535`
**Issue:** `onTextChange` keeps the raw text and clamps only the parsed state. The resync `LaunchedEffect(distance)` fires only when `distance` *changes* — so typing `500` with a 50 mm ceiling leaves `distance = 50` (unchanged after the second keystroke) while the field keeps displaying `500`; `onDone` does not restore either (the text parses fine). Same for negative input (`-5` displays, state unchanged) and for the speed field. The dispatch itself stays safe (always the clamped state — good), but the hero readout shows a value the printer will not receive, on a control surface.
**Fix:** On `onDone` (and ideally on focus loss), always resync the text from state: `distanceText = fmtDist(distance)` unconditionally, not only when parsing fails.

### WR-04: RETRACT_SPEED/UNRETRACT_SPEED — markPending rounds half-up, the wire truncates → fractional speeds arm an unreachable flip (8s transient dim)

**File:** `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneParams.kt:313-324, 420-423`
**Issue:** `clampForTuner` clamps the retraction speeds as Doubles without integer conversion; `FineTuneHolder.markPending` then rounds to 0dp **half-up** (`roundToInt`). But `dispatchForTuner` builds `RetractionArgs(retractSpeed = (...).toInt())` — **truncation**. For a live off-grid value (Klipper accepts `retract_speed: 22.5`), a +1 nudge arms target 24 (round 23.5 up) while the wire sends 23; the echo never lands within the 0.1 epsilon, so the group dims for the full `PENDING_FLIP_TIMEOUT_MS` backstop — the exact transient-dim class the P17 WR-01/02 fix closed for SCV/PA.
**Fix:** Make the integer conversion identical on both sides — e.g. round in `dispatchForTuner` (`rawTarget.roundToInt()`) so it matches the holder's half-up rounding, or truncate in `clampForTuner` so `markPending` arms the truncated value.

### WR-05: Duplicate FloatingEStop — AppShell renders an app-level e-stop on every screen, and the new screens render their own on top

**File:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:910-915`; `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt:275-282`; `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt:406-413`
**Issue:** Phase 24 (FIX-1) hoisted the printing-only `FloatingEStop` + ConfirmGuard to AppShell so it appears over **every** destination. The Phase-26 FineTune and Temperature screens (and SpoolScreen) each add their **own** `FloatingEStop` at `Alignment.TopStart` + 14.dp inside the Focus — while printing, two e-stop buttons render stacked/overlapping in the same corner, each opening its own guard. Redundant tap targets and a visual defect.
**Fix:** Remove the per-screen `FloatingEStop`/guard from FineTuneScreen and TemperatureScreen (the shell-level one already covers them), or — if the per-screen placement is the design intent — gate the shell-level one off on these destinations. One owner, not two.

### WR-06: Swipe-up drawer suppress set not updated for the Phase-26 screens

**File:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:523-548`
**Issue:** The suppress list's own criteria are "finger-scrollable picker/scrollback screens and overlay-heavy screens... keyboard content". The new Temperature and FineTune destinations host scrollable `ListBlock` Fields, and Extrude hosts `BasicTextField` IME entry — none of the three is in the suppress set, while comparable screens (Files, Console, Macros, Outputs) are. A vertical drag on the non-list portions (Focus adjuster, IME column) opens the drawer mid-interaction; behavior is also inconsistent across sibling screens.
**Fix:** Add `NavDest.Temperature`, `NavDest.FineTune`, and `NavDest.Extrude` to the suppress predicate (or document why these three are exempt).

### WR-07: Busy-locked AdjusterPanel steppers give no visual or accessibility disabled state

**File:** `app/src/main/java/works/mees/dinghy/designsystem/components/AdjusterPanel.kt:161-172`
**Issue:** `enabled = false` (the whole-group busy lock) only short-circuits the `onClick` lambdas — the "−"/"+" tiles render at full opacity, identical to enabled, and TalkBack still announces them as actionable. The P17 UX was "group dims + inert"; the rebuilt panel kept "inert" but dropped "dims". The codebase already has the convention for `OutlinedControl`'s missing `enabled` param (BookmarkedMacrosScreen.kt:517-531: `alpha(0.38f) + semantics { disabled() }`).
**Fix:** Apply the 25-03 convention to both stepper tiles (and Reset) when `enabled == false || value == null`.

### WR-08: Duplicate glyphs co-rendered on the Fine-Tune flat list — violates the "never the same glyph twice on one screen" icon law

**File:** `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneParams.kt:119/201, 161/234, 171/213`
**Issue:** The flat list shows all params on one screen (when `hasFwRetraction`): `DinghyIcons.OutputCircle` is used for both Flow Rate and Retract Length, `MaxVelocity` for both Max Velocity and Unretract Speed, `MaxAccel` for both Max Accel and Retract Speed. The registry KDoc states the owner law: "never the same glyph twice on one screen", and [[dinghy-never-pick-icons-ask]] forbids unilateral glyph reuse for new functions (the four FW-retraction rows are new assignments on this screen).
**Fix:** Ask the owner for four distinct FW-retraction glyph assignments (do NOT pick replacements unilaterally); until then this is a known law violation on fw-retraction printers.

### WR-09: Macro numeric clamp (T-26-07-01) is bypassed by Execute-without-Done; input filter admits NaN/Infinity/exponent forms

**File:** `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt:75, 453-466, 581-587`
**Issue:** `MACRO_NUMERIC_RANGE` clamping happens only in `onValueCommit` (ImeAction.Done). `onValueChange` stores the raw text into the `values` map, and `execute()` reads that map — typing a value and tapping Execute without Done sends the unclamped number. Additionally the keystroke filter's first branch (`raw.toDoubleOrNull() != null`) accepts `"NaN"`, `"Infinity"`, and `"1e5"`, all of which also pass `rejectNonNumeric` (NaN/Infinity parse to non-null Doubles with no whitespace) and are emitted unquoted on the macro line. Injection-safe (alphanumeric only) and the printer rejects them server-side, but the declared clamp ownership is not real.
**Fix:** Clamp in `execute()` before building the line (`values[p.name]` → parse → `coerceIn(MACRO_NUMERIC_RANGE)` → `formatNumeric`), and drop the `toDoubleOrNull()` branch from the keystroke filter (the regex branch already covers legitimate input).

### WR-10: `measureSpool` (a remote Spoolman write) launched on `rememberCoroutineScope` — cancelled silently by same-frame navigation

**File:** `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt:119, 166`
**Issue:** `onApplyMeasure = { spool, grams -> scope.launch { holder.measureSpool(spool, grams) } }` uses the composition scope. If the user taps Set and immediately navigates (Home foot button, system Back, recovery Splash decomposing the shell), the coroutine is cancelled before/while the HTTP write fires — the measured weight is silently dropped while the user believes it was set. This is the [[dinghy-compose-write-scope-cancellation]] class applied to a network write (the rule's rationale — fire-and-forget writes must outlive the composition — applies equally).
**Fix:** Route the write through a process- or holder-lifetime scope (e.g. a `SpoolHolder` method launching on its own `holderScope`, mirroring how the holder already owns its collector), keeping only the UI-state flips composition-side.

### WR-11: Hardcoded user-facing strings on the new surfaces — violates the PREVIEW_AND_TOKENS day-one `stringResource` convention

**File:** `app/src/main/java/works/mees/dinghy/designsystem/components/AdjusterPanel.kt:115, 143, 162, 168`; `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt:346`; `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt:494, 500, 618, 688, 746`; `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt:336, 365, 375`; `app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt:362-407`
**Issue:** New Phase-26 UI ships literal strings — "Reset", "Reset All", "  was …", "−"/"+", "Done", "Presets", "Cooldown", "Off", "Preheat preset", "Spoolman integration coming soon", "No LOAD_FILAMENT macro configured", "No UNLOAD_FILAMENT macro configured", and ScrubberPage's "Cancel"/"Apply"/"Back"/"Off" — while the same files use `stringResource` for other labels. `docs/ui_design/PREVIEW_AND_TOKENS.md` requires `stringResource` strings from day one on new screens.
**Fix:** Move user-visible labels to `strings.xml` (the "−"/"+" glyph tiles and "was" baseline span may warrant a documented exemption — decide explicitly rather than by omission).

## Info

### IN-01: `holder.pendingStateFlip` read non-reactively in composition

**File:** `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt:99`
**Issue:** `groupBusy = inFlight.isNotEmpty() || holder.pendingStateFlip != null || vm.groupBusy` reads a plain `StateFlow.value` getter in composition — it cannot trigger recomposition by itself and is redundant: `vm.groupBusy` already folds `pendingStateFlip != null` (FineTuneHolder.buildVm line 323). Harmless today only because `vm` re-emission forces the recompose.
**Fix:** Drop the direct `holder.pendingStateFlip` read; rely on `vm.groupBusy` (plus `inFlight` for the pre-emission frame).

### IN-02: DinghyApp KDoc says "SIX SEPARATE preference files" — there are now seven

**File:** `app/src/main/java/works/mees/dinghy/DinghyApp.kt:20-22`
**Issue:** The class KDoc was not updated when `tracestyle.preferences_pb` (the seventh store) was added; the in-body comments are correct.
**Fix:** Update the KDoc count and add the seventh file to the list.

### IN-03: GraphView `setData`/`setSetpoints` invalidate unconditionally on every AndroidView update pass

**File:** `app/src/main/java/works/mees/dinghy/render/GraphView.kt:288-299, 330-333`; `app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt:97-105`
**Issue:** `applyTokens`, `setTraceColorOverrides`, `yRange`, `drawArea`, and `showAxisLabels` are all equality-guarded (Phase-22 D-12 discipline preserved — verified), but `setData`/`setSetpoints` re-sanitize and `invalidate()` on every recomposition of the host, including recompositions unrelated to a new sample (step taps, toast appearance). Bounded cost, but a cheap `if (setpoints == this.setpoints) return` guard on `setSetpoints` would trim redundant repaints.
**Fix:** Optional equality guard on `setSetpoints`; `setData` allocates a new array per call so guard upstream if desired.

### IN-04: Temperature Field-takeover preset rows dropped the in-flight dedup the old PresetSelector had

**File:** `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt:511-517` (vs `PresetSelector` at 753-757)
**Issue:** The retained `PresetSelector` guards `if (key !in inFlight) onPreset(p)`; the new D-12 in-Field picker dispatches unconditionally. Double-taps stack `apply_preset` dispatches.
**Fix:** Thread `inFlight` into the picker rows and guard on `"preset_${preset.name}"`.

### IN-05: Dead constant and lingering raw-ligature TODO in the Spool layer

**File:** `app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt:536` ; `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt:662`
**Issue:** `SPOOL_LIMIT = 50` is unused (`buildSpoolQuery` hardcodes `limit=50`); SpoolScreen still carries `symbol = "close"` with a `TODO(23-rev WR-02)` raw ligature outside the registry.
**Fix:** Use the constant in `buildSpoolQuery`; resolve the close-glyph owner assignment.

### IN-06: TemperatureHolder trace-style dual-write can transiently regress under rapid taps

**File:** `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureHolder.kt:118-129, 136-148`
**Issue:** `setTraceColor` updates the in-memory map immediately while the caller persists separately; the init collector then **replaces** the whole in-memory map from each DataStore emission. Two quick swatch taps can momentarily flash back to the first color when the first write's emission lands. Converges; cosmetic only.
**Fix:** None required; if it ever shows on-device, fold the in-memory update into the prefs round-trip (single source).

### IN-07: ShellNavState KDoc still lists "Outputs detail" as a held sub-nav

**File:** `app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt:25-27`
**Issue:** Outputs detail selection now lives in `OutputsScreen`-local state (26-05); ShellNavState carries no Outputs field. Doc drift only.
**Fix:** Drop "Outputs detail" from the KDoc holdout list.

---

_Reviewed: 2026-06-10T23:52:45Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
