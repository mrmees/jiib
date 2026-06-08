---
phase: 19-output-controls-fans-lights-generic-pins
verified: 2026-06-08T00:00:00Z
status: passed
score: 4/4
overrides_applied: 0
---

# Phase 19: Output Controls — Fans, Lights & Generic Pins Verification Report

**Phase Goal:** A dedicated page to control the printer's auxiliary outputs without the browser — [fan_generic] aux/part fans, [output_pin] switches, and [led]/[neopixel] lighting where present. Capability-gated by the Phase-6 matrix; each output's current value comes from the central subscribe and is set through the shared command primitive.
**Verified:** 2026-06-08
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User sees every controllable output the active printer exposes (SC-1) | VERIFIED | `OutputsGate.parseOutputs` whitelist-filters configfile.settings ∩ objects.list; `OutputsScreen` renders a flat alpha `LazyColumn` keyed by `objectKey`; `visibleDrawerTiles` hides the Outputs tile entirely when `outputsPresent == false` (D-10 gate proven by `AppDrawerOutputsGateTest`); E5P fixture parses 9 outputs, excluded sections (extruder/heater_bed/fan) never appear |
| 2 | User can set fan speeds, toggle/PWM pins, and set LEDs via keyboard-free controls with state-flip confirmation (SC-2) | VERIFIED | `OutputScrubberDetail` dispatches fan/heater/servo/pwm-pin/pwm_tool through `CommandRegistry.setGenericFan`/`setHeater`/`setServo`/`setOutputPin` via `ScrubberActions.OnSettle` (settle-once, never per-frame); `OutputLedDetail` dispatches RGB via `hsvToRgb`+`CommandRegistry.setLed` on wheel+brightness settle; white-only branch dispatches WHITE channel (GAP-B fix); `OutputToggleControl` dispatches digital On/Off; every page has an Off action; live gcode_store wire evidence in 19-UAT.md confirms bare names on the wire; 854 host tests, 0 failures |
| 3 | Read-only or absent outputs degrade gracefully; no control sends a command the printer can't accept (SC-3) | VERIFIED | `OutputsHolder.buildRow` emits `displayValue=null` for servo (PWM≠angle) and absent live values while keeping `isSettable=true` and the row tappable; `readOnly` descriptor sets `isSettable=false`; `OutputsGate` derives `ledHasRgb`/`ledHasWhite` from configfile.settings (GAP-B) preventing RGB write to white-only hardware; single-source clamp authority in `PrinterCommands` (0..100→0..1, servo 0..maxDeg, LED 0f..1f) |
| 4 | Proven live against the real printers' actual fan/pin/LED configuration (SC-4) | VERIFIED | 19-UAT.md status: RESOLVED, owner-approved 2026-06-08; 9/9 checks PASS across E5P (rich) + E3P (sparse); required gcode_store wire evidence captured for fan (`SET_FAN_SPEED FAN=FILTER_fan SPEED=0.87`), output_pin (`SET_PIN PIN=bed_safety_switch VALUE=1`), white-only LED (`SET_LED LED=chamber_light … WHITE=0.92`), RGB LED (`SET_LED LED=expanderPixel RED=1 … WHITE=0`), servo (`SET_SERVO SERVO=camera_servo ANGLE=78` + `WIDTH=0`); E3P confirms tile NOT hidden when exactly one output present |

**Score:** 4/4 truths verified

---

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/src/main/java/works/mees/dinghy/outputs/OutputDescriptor.kt` | Model with `objectKey`/`commandName`/`ledHasRgb`/`ledHasWhite` | VERIFIED | 53 lines; data class with documented HIGH-1 split + GAP-B channel flags; non-null defaults for backward compat |
| `app/src/main/java/works/mees/dinghy/outputs/OutputsGate.kt` | Pure `parseOutputs` whitelist-filtered, case-recovered | VERIFIED | 165 lines; `WHITELIST` set of 10 families; `parseOutputs` 7-step algorithm; `ledCapability` for RGB/white/fixed-driver derivation; host-testable |
| `app/src/main/java/works/mees/dinghy/outputs/OutputsHolder.kt` | StateFlow of typed `OutputRowVm` + per-family `reached()` + clamped `markPending` | VERIFIED | 259 lines; `combine(outputDescriptors, printerState, _pending)`; per-family `reached()` (servo always false → timeout-only); `markPending` fed `outputPctToWire(pct)` (17-07); seq-guarded 8s backstop |
| `app/src/main/java/works/mees/dinghy/ui/outputs/OutputsScreen.kt` | Flat alpha list, drawer-suppressed, Back-only gutter, stateless seam | VERIFIED | 237 lines; `OutputsContent` stateless seam; `LazyColumn` keyed by `objectKey`; `glyphFor()` maps all 6 families to owner-locked tokens; `displayValue` null-omits value but row stays `clickable`; `stringResource` strings; `LocalTokens` chrome |
| `app/src/main/java/works/mees/dinghy/ui/outputs/OutputScrubberDetail.kt` | Settle-dispatch for fan/heater/servo/pwm-pin/pwm_tool via CommandRegistry | VERIFIED | `dispatchCommand(CommandRegistry.<spec>, args)` confirmed; `descriptor.commandName` (bare, HIGH-1) confirmed; `ScrubberActions.OnSettle` with `onOff` gutter slot (GAP-A); no `GCODE_SCRIPT`; no `ConfirmGuard` |
| `app/src/main/java/works/mees/dinghy/ui/outputs/OutputLedDetail.kt` | hsvToRgb+ColorWheel (RGB) / brightness-slider (white-only) + Off; via CommandRegistry | VERIFIED | `hsvToRgb` confirmed in file; `CommandRegistry.setLed` call ≥2 (color+Off); `ledHasRgb` branch confirmed; `descriptor.commandName` used; `WHITE=0` policy enforced; no `GCODE_SCRIPT` |
| `app/src/main/java/works/mees/dinghy/ui/outputs/OutputPinDetail.kt` | Branches on `descriptor.pwm`: toggle vs scrubber, both via CommandRegistry | VERIFIED | File exists; PWM branch → scrubber; digital branch → `OutputToggleControl` |
| `app/src/main/java/works/mees/dinghy/ui/outputs/OutputToggleControl.kt` | Digital On/Off toggle for non-PWM output_pin | VERIFIED | File exists; dispatches `CommandRegistry.setOutputPin` |
| `app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt` | `ScrubberActions.OnSettle` with optional `onOff` gutter slot | VERIFIED | `ScrubberActions` sealed interface confirmed; `OnSettle` with `onOff: (() -> Unit)? = null` and `offLabel`; `ApplyCancel` unchanged |
| `app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt` | `setGenericFan`/`setLed`/`setServoAngle`/`setServoDisable`/`setPinDigital`/`setPinPwm`/`outputPctToWire` | VERIFIED | All builders confirmed; `SET_FAN_SPEED`/`SET_LED`/`SET_SERVO`/`SET_PIN` present; `SET_PWM_TOOL` absent (zero grep hits in `app/src/main`); `outputPctToWire` exposed for markPending (17-07) |
| `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` | `SetGenericFanArgs`/`SetLedArgs`/`SetServoArgs`/`SetOutputPinArgs` + typed `CommandSpec`s in `all` | VERIFIED | All four arg classes confirmed; `setGenericFan`/`setLed`/`setServo`/`setOutputPin` specs in `all` list at lines 812-815 |
| `app/src/main/java/works/mees/dinghy/designsystem/HsvToRgb.kt` | Pure `hsvToRgb` + `rgbToHsv` for LED page seeding | VERIFIED | `fun hsvToRgb(hue, saturation=1f, value)` confirmed; `rgbToHsv` inverse for seeding initial state; no androidx imports |
| `app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt` | `Dest.Outputs` in enum | VERIFIED | `Dest.Outputs` confirmed in enum line 37 |
| `app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt` | Live Output tile (D-07 token), hidden when empty via `visibleDrawerTiles` | VERIFIED | `OUTPUT_SYMBOL` sourced from `DinghyIcons.OutputSection` token (not a hand-typed string); `visibleDrawerTiles` pure helper confirmed; `dest = Dest.Outputs`; `bolt` freed |
| `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` | OutputsHolder assembled; Dest.Outputs host with list↔detail back-stack + selection-reset; drawer suppressed | VERIFIED | `OutputsHolder` assembled via `remember(store)`; `selectedOutputKey` local state; `LaunchedEffect` selection-reset when key absent; `BackHandler` at Dest.Outputs; `Dest.Outputs` in suppression set (line 540); `outputsEnabled` threaded from `AppContainer.outputsPresent` |
| `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` | `outputsPresent: Flow<Boolean>` spine-scoped | VERIFIED | `spine.flatMapLatest { it?.store?.outputDescriptors ?: flowOf(emptyList()) }.map { it.isNotEmpty() }` — idles to false on disconnect/switch |
| `app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt` | Output whitelist objects added to subscribe set | VERIFIED | `name.substringBefore(' ') in OutputsGate.WHITELIST -> result += name` at line 107 |
| `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt` | 5th configfile consumer; `setOutputDescriptors` called; cleared on failure/reconnect | VERIFIED | `store.setOutputDescriptors(emptyList())` called before re-read (line 493) AND on configfile failure (line 568); `parseOutputs` consumed inside the existing one-shot block (no duplicate configfile query — Pitfall 3) |
| `app/src/main/java/works/mees/dinghy/state/PrinterState.kt` | `outputs: Map<String, OutputLiveValue>` | VERIFIED | `outputs: Map<String, OutputLiveValue> = emptyMap()` confirmed; heater_generic reads via `heaters` (single source) |
| `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` | 7 output tokens (D-01..D-07) + FanMode→air reassignment (D-08) | VERIFIED | `OutputHeater`/`OutputFan`/`OutputLed`/`OutputServo`/`OutputPin`/`OutputPwmTool`/`OutputSection` all in `all` list; `FanMode = DinghyIcon(IconRef.Ligature("air"), alternate = "fan_mode")` confirmed; no raw `mode_fan` ligature string remains |
| `tools/verify_ligatures.py` NEEDED set | 8 D-09 glyphs: mode_heat/mode_fan_2/lightbulb_2/cyclone/check_box/vital_signs/output/air | VERIFIED | All 8 confirmed in NEEDED set at line 78 |
| `app/src/test/resources/outputs/configfile_settings_e5p.json` + 3 other fixtures | Real Moonraker captures from E5P + E3P | VERIFIED | All 4 files exist and parse as valid JSON; E5P has 9 whitelisted keys (fan_generic/led/neopixel/servo×2/output_pin×4) and excluded keys (extruder/heater_bed) |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `OutputsGate.parseOutputs` | `OutputDescriptor.objectKey`/`commandName` | settings lowercased + objects.list case-recovery | WIRED | `firstOrNull { it.equals(lcKey, ignoreCase=true) }` case-recovers; `commandName = objectKey.substringAfter(' ')` (HIGH-1) |
| `DeriveCapabilities.deriveSubscribeSet` | output objects in subscribe set | `name.substringBefore(' ') in OutputsGate.WHITELIST` | WIRED | Confirmed at line 107 of DeriveCapabilities.kt; runs pre-configfile so live status diffs are registered at subscribe time |
| `MoonrakerSession` | `store.setOutputDescriptors` | 5th consumer of existing one-shot configfile block | WIRED | Confirmed at lines 557-568; clear-on-failure at line 493+568; no new configfile query added (Pitfall 3 — grep confirms single `ObjectSubsetArgs(setOf("configfile"))`) |
| `AppContainer.outputsPresent` | `AppDrawer(outputsEnabled=...)` | `spine.flatMapLatest { … }.map { it.isNotEmpty() }` | WIRED | `val outputsEnabled by container.outputsPresent.collectAsStateWithLifecycle(…)` threaded into AppDrawer call at line 981 of AppShell.kt |
| `AppDrawer.visibleDrawerTiles` | D-10 hide gate | pure filter on `Dest.Outputs || outputsEnabled` | WIRED | `AppDrawerOutputsGateTest` (4 tests) proves the gate — tile absent when false, present+correct when true |
| `OutputsScreen.glyphFor(family)` | `DinghyIcons.Output*` tokens | family→token mapping (D-01..D-06) | WIRED | 6 token references confirmed in `glyphFor()`; grep `IconRef.Ligature` in OutputsScreen.kt = 0 (no raw strings) |
| `OutputScrubberDetail.dispatchValue` | `CommandRegistry.setGenericFan` / `setHeater` / `setServo` / `setOutputPin` | `dispatchCommand(CommandRegistry.<spec>, args)` with `descriptor.commandName` | WIRED | `dispatchCommand(CommandRegistry.setGenericFan, SetGenericFanArgs(descriptor.commandName, v))` confirmed; HIGH-1 (commandName not objectKey); HIGH-2 (CommandRegistry not GCODE_SCRIPT); gcode_store wire evidence in UAT confirms bare name reached real printer |
| `OutputLedDetail.dispatchColor` | `hsvToRgb` → `CommandRegistry.setLed` | hue+brightness settle → `hsvToRgb(hue,1f,…)` → `SetLedArgs` | WIRED | Both `hsvToRgb` import and `CommandRegistry.setLed` confirmed; `WHITE=0` policy on color dispatch; white-only branch dispatches `w=brightness` |
| `OutputsHolder.markPending` | `PrinterCommands.outputPctToWire` | clamped wire target = dispatched value (17-07) | WIRED | `outputPctToWire(pct)` is the public helper; holder uses it per the KDoc; seq-guarded 8s timeout backstop prevents wedge for servo |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `OutputsScreen` / `OutputsContent` | `rows: List<OutputRowVm>` | `OutputsHolder.rows` StateFlow | YES — `combine(outputDescriptors, printerState, _pending)` pulls live output values from `PrinterState.outputs` (fan speed, pin value, LED color_data) sourced from Moonraker central subscribe; display scaling applied in holder | FLOWING |
| `OutputsHolder.buildRow` LED swatch | `swatchColor: Long?` | `PrinterState.outputs[objectKey]?.colorData[0]` | YES — `colorData` reduced from Moonraker `notify_status_update` LED `color_data` field; white-only branch packs grey/white from WHITE component | FLOWING |
| `OutputLedDetail` initial hue/brightness | `initialHue`, `initialBrightness` | `colorData[0]` via `rgbToHsv` | YES — seeded from live `color_data[0]` via `rgbToHsv` inverse on each page open; white-only seeds from `colorData[0][3]` (white channel) | FLOWING |
| `AppContainer.outputsPresent` | `Boolean` | `store.outputDescriptors` via `flatMapLatest` | YES — derived from the live descriptor list pushed by MoonrakerSession on each handshake; idles false on disconnect | FLOWING |

---

### Behavioral Spot-Checks

Step 7b is SKIPPED for this phase. The app requires a running Android device for behavioral verification. The on-device UAT in 19-UAT.md is the authoritative behavioral gate (SC-4 resolved with live gcode_store wire evidence).

---

### Probe Execution

Step 7c: No `scripts/*/tests/probe-*.sh` discovered; no probes declared in PLAN files. SKIPPED.

---

### Requirements Coverage

Phase 19 plans reference `SC-1`, `SC-2`, `SC-3`, `SC-4` (ROADMAP success criteria). The `OUT-*` requirement family was explicitly noted in REQUIREMENTS.md as "TBD at discuss" (REQUIREMENTS.md line 263 reads: `Phase 18 (Output Controls): new OUT-* family — TBD at discuss`). This is an informational gap: no formal OUT-* IDs were ever coined in REQUIREMENTS.md. The SCs are the operative contract and are all SATISFIED. This does NOT block the phase.

| Requirement | Source | Description | Status | Evidence |
|-------------|--------|-------------|--------|----------|
| SC-1 | ROADMAP Phase 19 | User sees every controllable output (and nothing extra) | SATISFIED | parseOutputs whitelist-filter + case-recovery + capability gating + D-10 hide gate proven by host test + live E5P/E3P UAT |
| SC-2 | ROADMAP Phase 19 | Keyboard-free set controls with state-flip confirmation | SATISFIED | ScrubberActions.OnSettle (settle-once), LED hue+brightness+Off, digital toggle — all via CommandRegistry; markPending/reached() per-family; live gcode_store wire evidence |
| SC-3 | ROADMAP Phase 19 | Graceful degrade; no illegal command | SATISFIED | servo displayValue=null; absent → displayValue null, row tappable; readOnly→isSettable=false; ledHasRgb/ledHasWhite prevents wrong-channel dispatch (GAP-B); single-source clamp authority |
| SC-4 | ROADMAP Phase 19 | Proven live on real printers | SATISFIED | 19-UAT.md RESOLVED; 9/9 checks PASS; gcode_store evidence for all 4 families; heater_generic accepted as automated-only exclusion |
| OUT-* | REQUIREMENTS.md | Never formally defined | INFO | Not a gap — the SCs are the operative contract per the REQUIREMENTS.md note at line 263 |

---

### Anti-Patterns Found

No `TBD`, `FIXME`, or `XXX` markers found in any phase-modified source files. No stub patterns (no `return null` on main logic paths, no empty/placeholder implementations). No `GCODE_SCRIPT` raw dispatch in detail pages.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | — | — | None found |

---

### Human Verification Required

None. All automated checks and on-device UAT are complete with owner sign-off.

---

### Gaps Summary

None. All 4 SC must-haves are VERIFIED through code inspection and the resolved on-device UAT (19-UAT.md, owner-approved 2026-06-08).

**Informational notes (non-blocking):**
- `OUT-*` requirement family was never formally minted in REQUIREMENTS.md — this predates Phase 19 and is a process note, not a code gap.
- `heater_generic` live control is accepted as automated-only (no dev printer exposes one); documented in 19-UAT.md as an accepted exclusion.
- RGB LED detail page aesthetics noted as "rough but good enough" by owner — captured as future polish in 19-UAT.md, non-blocking.

---

_Verified: 2026-06-08_
_Verifier: Claude (gsd-verifier)_
