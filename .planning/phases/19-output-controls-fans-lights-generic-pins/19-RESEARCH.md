# Phase 19: Output Controls — Fans, Lights & Generic Pins - Research

**Researched:** 2026-06-07
**Domain:** Klipper/Moonraker output-object enumeration + per-type immediate-dispatch G-code control (native Android/Kotlin, Compose+Views hybrid)
**Confidence:** HIGH — every command, status field, and the discovery algorithm were verified live against BOTH real printers (E5P 192.168.1.120, E3P 192.168.1.121) AND cross-checked against the Klipper G-Code / Status reference.

## Summary

This phase is unusually well-grounded: the staging doc + CONTEXT lock ~90% of the design, and the two
remaining hard questions — discovery source and exact command/range/status per type — both resolved
cleanly against the live printers. **The discovery source is settled: `configfile.settings`** (a one-shot
query the app already runs at handshake), filtered to the whitelist, with `printer.objects.list`
(`Capabilities.objects`, already modeled) as the runtime confirmation. `configfile.settings` is strictly
better than `configfile.config` because it normalizes `pwm` to a real boolean even when the raw config
omits the key, and it parses every numeric. Its one trap: **it lowercases object names**, so it cannot be
used to build commands — commands must use the case-preserving name from `objects.list`/`configfile.config`.

Every whitelisted command was verified: `SET_FAN_SPEED FAN=<n> SPEED=<0..1>`, `SET_HEATER_TEMPERATURE
HEATER=<n> TARGET=<°C>`, `SET_LED LED=<n> RED= GREEN= BLUE= WHITE= (each 0..1)`, `SET_SERVO SERVO=<n>
ANGLE=<deg>`, `SET_PIN PIN=<n> VALUE=<0/1 digital | 0..1 PWM>`. **`pwm_tool` has NO dedicated command — it
is driven by `SET_PIN`** (confirmed against Klipper reference; neither dev printer has a `pwm_tool`). All
five LED families respond to the SAME `SET_LED` command and report the SAME `color_data` shape, so ONE
RGB+brightness page covers them all — no per-family fallback needed.

**D-09 ligature gate: ALL 8 glyphs PASS** (`mode_heat`, `mode_fan_2`, `lightbulb_2`, `cyclone`,
`check_box`, `vital_signs`, `output`, `air`) — verified via the bundled font's GSUB LigatureSubst table.
The existing `mode_fan` (being freed for `air`) also still resolves, so the D-08 reassignment is safe.

**Primary recommendation:** Discover via the existing one-shot `configfile.settings` query (add a Phase-19
consumer alongside the extruder/probe/screws/fine-tune consumers already in `MoonrakerSession.kt:498`),
filter to the whitelist, gate the drawer tile on a non-empty result (D-10), preserve the raw case-correct
name for commands, and route every set through new clamped builders in `PrinterCommands`.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Enumerate whitelisted outputs | API/Backend (Moonraker `configfile.settings`) | State (Capabilities/holder) | configfile is the only source with config metadata (pwm flag, servo angle range) |
| Confirm runtime availability | State (`Capabilities.objects` from objects.list) | — | objects.list is already modeled; proves the object is live, not just configured |
| Live current values | API (central `objects/subscribe`) → State reducer | UI | speed/value/temperature/color_data flow through the existing subscribe hot path |
| Build set-commands | Domain (`PrinterCommands` pure builders) | — | single-source clamp authority (Phase-17 lesson) |
| Dispatch | `CommandDispatcher` → `printer.gcode.script` | — | shared command primitive, immediate dispatch |
| Drawer-tile gating | UI (capability gate, like `*Gate.kt`) | State | hide tile entirely when zero outputs (D-10) |

## User Constraints (from CONTEXT.md)

### Locked Decisions
- **Whitelist (ONLY these):** `heater_generic`, `fan_generic`, `led`, `neopixel`, `dotstar`, `pca9533`, `pca9632`, `servo`, `output_pin`, `pwm_tool`.
- **Out of scope:** all standard/automatic outputs (extruder, heater_bed, `fan`, heater_fan, controller_fan, temperature_fan, sensors, steppers, TMC/digipot/expander/probe, respond, display, buttons); `manual_stepper`; `pwm_cycle_time`; search/filter; hide/favorite/reorder; "all off"; per-index LED.
- **Available while printing** — no print-state gating.
- **Flat alphabetical list**, no group headers/subpages; icon denotes type; row = icon + prettified name + current value (value hidden if absent, row stays tappable).
- **Immediate dispatch** — no Apply flow, no ConfirmGuard even for risky pins/PWM; Off/zero dispatches immediately; busy-state scoped to the detail page; toasts for feedback; failure = toast + stay on page.
- **Output-type icons (OWNER-SELECTED, do not substitute):** D-01 `heater_generic`→`mode_heat`; D-02 `fan_generic`→`mode_fan_2`; D-03 all LED families→`lightbulb_2`; D-04 `servo`→`cyclone`; D-05 `output_pin` (digital AND pwm)→`check_box`; D-06 `pwm_tool`→`vital_signs`; D-07 Outputs section/tile→`output`; D-08 **reassign part-cooling fan (Fine-Tune) `mode_fan`→`air`** at `DinghyIcons.kt:80`.
- **D-09:** verify every glyph resolves in bundled font (v2.944) before wiring; STOP+ask owner if any FAIL.
- **D-10:** zero whitelisted outputs → hide the Outputs drawer tile entirely (no empty-state screen).
- **D-11:** reached via App Drawer tile (swipe-up), like Calibration/Macros/Console; icon `output`.
- **D-12:** LED detail page gets explicit **Off** action (black / brightness 0) alongside RGB picker + brightness scrubber. Whole-strip only.

### Claude's Discretion
- Exact control-page composition (scrubber layout, Off placement) within the `ScrubberPage`/single-setting patterns.
- All command-syntax / value-range / discovery-source resolution (this research resolves it).

### Deferred Ideas (OUT OF SCOPE)
- Search/filter; hide/favorite/reorder; per-index addressable LED; "all off"; `manual_stepper` & `pwm_cycle_time` controls; curating output glyphs into `material-icon-bucket.json` (optional, non-blocking).

## Phase Requirements

(No formal OUT-* IDs defined yet; the 4 ROADMAP Success Criteria are the requirement surface.)

| ID | Description | Research Support |
|----|-------------|------------------|
| SC-1 | User sees every controllable output the active printer exposes — and nothing it doesn't | Discovery algorithm (§2): configfile.settings ∩ whitelist ∩ objects.list |
| SC-2 | Set fan % (0–100), toggle/PWM pins, set LEDs via keyboard-free controls, confirmed by state flip | Command table (§1) + ScrubberPage/ColorWheel + central subscribe (§3); markPending pattern |
| SC-3 | Read-only/absent outputs degrade gracefully; no command the printer can't accept | §4 read-only detection + §3 absent-value handling |
| SC-4 | Proven live against the real printers' fan/pin/LED config | Validation Architecture §; live captures already taken from both printers |

## Standard Stack

No new external dependencies. This phase composes existing project libraries only:

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| kotlinx.serialization | 1.7.x (in catalog) | parse `configfile.settings` (JsonObject walking, already used for the configfile consumers) | matches existing MoonrakerSession parse path |
| Coroutines + Flow | 1.9.x | one-shot configfile query + central subscribe | existing spine |
| Jetpack Compose Material3 | BOM 2026.05 | Outputs list + detail pages | existing UI stack |

**No `npm`/`pip` — this is Android/Gradle.** No package-legitimacy audit required (no new dependencies added).

## Architecture Patterns

### System Architecture Diagram

```
                ┌─────────────────────────── handshake (once per connect) ──────────────────────────┐
                │                                                                                    │
  Moonraker ──► configfile.settings ──► [NEW Phase-19 consumer in MoonrakerSession ~L498]            │
   (REST/WS)        (one-shot)              │  filter to WHITELIST families                          │
                │                           │  parse: raw name (case), prettyName, type, pwm flag,   │
                │                           │         servo angle range, readOnly flag               │
                │                           ▼                                                        │
                │                    store.setOutputDescriptors(List<OutputDescriptor>) ────────────►│ holder/StateFlow
                │                                                                                    │
  Moonraker ──► objects/subscribe ──► PrinterStateReducer ──► live values keyed by raw object name   │
   (push)          (hot path)            (speed/value/temperature/target/color_data)                 │
                                                  │                                                   │
                                                  ▼                                                   │
                          ┌───────────────────────────────────────────────────┐                     │
                          │  OutputsScreen  (drawer tile, gated D-10)           │ ◄───────────────────┘
                          │   flat alpha list: icon + prettyName + live value   │
                          └───────────────┬───────────────────────────────────┘
                                          │ tap row
                                          ▼
                          ┌───────────────────────────────────────────────────┐
                          │ Detail page by type:                                │
                          │  heater→ScrubberPage(°C)+Off  fan→ScrubberPage(%)+Off│
                          │  led→ColorWheel+brightness+Off  servo→ScrubberPage(°)│
                          │  output_pin digital→Toggle  output_pin pwm→Scrubber% │
                          │  pwm_tool→ScrubberPage(%)+Off                        │
                          └───────────────┬───────────────────────────────────┘
                                          │ immediate dispatch (no Apply/Confirm)
                                          ▼
                          PrinterCommands.set*(raw name, clamped value) ──► scriptParams ──►
                          CommandDispatcher ──► printer.gcode.script ──► Moonraker
```

### Recommended placement (mirror existing feature packages)
```
app/src/main/java/works/mees/dinghy/
├── outputs/
│   ├── OutputDescriptor.kt   # pure model: rawName, prettyName, type, pwm, servoAngleMax, readOnly
│   ├── OutputsGate.kt        # pure: parse configfile.settings → List<OutputDescriptor> (whitelist filter)
│   ├── OutputsScreen.kt      # flat list (drawer-suppressed, Back-only gutter) — mirror CalibrationHubScreen
│   ├── OutputDetail*.kt      # per-type detail pages
│   └── OutputsHolder.kt      # StateFlow of descriptors + live values + per-detail busy state
```

### Pattern 1: Discovery via the existing one-shot configfile consumer
**What:** Add a Phase-19 consumer to the SAME `configfile.settings` block already at `MoonrakerSession.kt:498`
(it already serves extruder/probe/screws-tilt/fine-tune). Do NOT add a second configfile query (the file's
own "Pitfall 3 — no duplicate configfile query" rule).
**When:** at handshake, re-run on reconnect (same as Capabilities re-derive).
**Example (the parse, verified-shape):**
```kotlin
// Source: live capture, E5P configfile.settings (VERIFIED: 192.168.1.120/printer/objects/query?configfile)
// settings keys are LOWERCASED by Moonraker; the case-correct name for commands comes from objects.list.
val WHITELIST = setOf("heater_generic","fan_generic","led","neopixel","dotstar",
                      "pca9533","pca9632","servo","output_pin","pwm_tool")
fun parseOutputs(settings: JsonObject, liveObjects: Set<String>): List<OutputDescriptor> =
    settings.entries
        .filter { it.key.substringBefore(' ') in WHITELIST }
        .mapNotNull { (lcKey, sec) ->
            val family = lcKey.substringBefore(' ')
            // recover case-correct raw name from objects.list (case-insensitive match)
            val rawName = liveObjects.firstOrNull { it.equals(lcKey, ignoreCase = true) } ?: return@mapNotNull null
            OutputDescriptor(
                rawName = rawName,                       // "fan_generic FILTER_fan" — used in commands
                family  = family,
                bare    = rawName.substringAfter(' '),   // "FILTER_fan"
                pwm     = (sec as? JsonObject)?.booleanOrNull("pwm") ?: false,
                servoAngleMax = (sec as? JsonObject)?.floatOrNull("maximum_servo_angle") ?: 180f,
            )
        }
```

### Anti-Patterns to Avoid
- **Using `configfile.settings` keys verbatim in commands** — they are lowercased; `SET_FAN_SPEED FAN=filter_fan` will FAIL on a printer configured as `FILTER_fan`. Always command with the `objects.list` case.
- **Adding a second `configfile` query** — reuse the existing one-shot (Pitfall 3).
- **Continuous/looping animation** on a live LED swatch or value — Adreno-320 budget; static only (D-13 project law).
- **Reading a rapidly-changing value high in the list tree** — keep per-row live value reads scoped to the row (Compose skipping).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Single-setting numeric input | a new keypad/slider | `ScrubberPage.kt` | keyboard-free contract already met; tabular numerals; clamp |
| RGB color pick | a new color picker | `ColorWheel.kt` (hue) + a brightness scrubber | exists; but see note — it is HUE-only, needs HSV→RGB mapping |
| Command dispatch + busy/toast | new dispatch path | `CommandDispatcher` + `printer.gcode.script` | shared primitive, Phase-17-hardened |
| Value clamping | inline `coerceIn` at call sites | new clamp helpers in `PrinterCommands` | single-source clamp authority (Phase-17 17-07 lesson) |
| Drawer suppression + Back-only gutter | new scaffold | mirror `CalibrationHubScreen.kt` | exact precedent |
| Capability gating the tile | new gate framework | mirror `CalibrationGate.kt` pure-predicate pattern | host-testable, established |
| Configfile fetch | new RPC call | the one-shot at `MoonrakerSession.kt:498` | avoids duplicate query (Pitfall 3) |

**Key insight:** `ColorWheel` is **hue-only** (`hue: Float`, `onHandleMove/onSettle`). The LED page needs
hue + a brightness/value scrubber, and a pure HSV→RGB conversion to emit `SET_LED RED= GREEN= BLUE=`. Saturation
is not currently a control. **Decision for the planner:** either (a) fix saturation at 1.0 and expose
hue + brightness (simplest, matches the existing wheel + one scrubber, covers the common "set the strip to a
color at a brightness" case), or (b) add a saturation control. Recommend (a) for v1 — it reuses ColorWheel
verbatim and matches the staging "simple RGB color picker" intent. This is the one place a small NEW pure
helper (`hsvToRgb`) is needed; it is NOT a hand-rolled picker.

## Runtime State Inventory

Not a rename/refactor phase — N/A. (Cross-phase icon reassignment D-08 is a code edit, covered below, not runtime state.)

## Common Pitfalls

### Pitfall 1: configfile.settings lowercases object names
**What goes wrong:** Commands built from settings keys fail silently (Klipper "Unknown ... " error toast).
**Why:** Moonraker lowercases all `configfile.settings` keys (`fan_generic filter_fan`), while `objects.list`
and `configfile.config` preserve case (`fan_generic FILTER_fan`).
**How to avoid:** Discover the family/metadata from `settings`, but resolve the command name by
case-insensitive match against `Capabilities.objects` (or read names from `configfile.config`). Store the
case-correct `rawName` in the descriptor. (This is the SAME lesson `hasMacroIgnoreCase` exists for.)
**Warning sign:** command works on one printer, fails on another whose section name has uppercase.

### Pitfall 2: pwm flag only appears in `.config` when explicitly set
**What goes wrong:** Using `configfile.config` to detect digital-vs-PWM output_pin → a digital pin (no `pwm:`
line) has no `pwm` key at all, branch defaults wrong.
**Why:** `.config` is the raw as-typed config; `.settings` is the parsed/defaulted view.
**How to avoid:** Read `pwm` from **`configfile.settings`** where it is always present as a real boolean
(VERIFIED: `bed_safety_switch` has no `pwm:` line in `.config` but `.settings` reports `"pwm": false`).

### Pitfall 3: `SET_FAN_SPEED` SPEED is 0.0–1.0, the UI shows 0–100%
**What goes wrong:** Sending `SPEED=50` blasts the fan to max (clamped to 1.0) instead of 50%.
**How to avoid:** Builder takes display % and divides by 100: `SET_FAN_SPEED FAN=$name SPEED=${pct/100.0}`.
Same scaling for output_pin PWM, pwm_tool, and LED brightness (all 0–1 on the wire).

### Pitfall 4: servo reports `value` (PWM 0..1), NOT angle
**What goes wrong:** Expecting a readable `angle` status field to show current angle.
**Why:** `servo` exposes only `value` = last PWM setting (0..1), not degrees (VERIFIED live: `servo
camera_servo` → `{"value": 0.0}`; Status_Reference confirms "last setting of the PWM pin").
**How to avoid:** Angle is **write-only / not readable as degrees**. The row hides the value (degrade per
staging rule — "hide value if unavailable, row stays tappable"); the detail page is a stateless angle
scrubber over `0..maximum_servo_angle` (from config). Optionally show the raw PWM `value` as a coarse
indicator, but do not back-compute angle (the pulse-width mapping isn't a clean linear inverse for display).

### Pitfall 5: ColorWheel is hue-only
Covered in Don't-Hand-Roll. Needs an `hsvToRgb` helper + a brightness scrubber.

## Code Examples

### Command builders to add to `PrinterCommands` (clamp-before-format, ASVS V5)
```kotlin
// Source: Klipper G-Codes.html (CITED) + live gcode/help on E5P (VERIFIED: 192.168.1.120)
// SPEED/VALUE/WHITE/RED... all 0..1 on the wire; UI uses 0..100% and degrees.

fun setGenericFan(name: String, pct: Int): String =                       // fan_generic
    "SET_FAN_SPEED FAN=$name SPEED=${fmt(pct.coerceIn(0,100)/100.0, 2)}"

fun setGenericHeater(name: String, targetC: Int): String =                // heater_generic (Off = 0)
    "SET_HEATER_TEMPERATURE HEATER=$name TARGET=${targetC.coerceIn(0, MAX_TEMP_C)}"

fun setLed(name: String, r: Float, g: Float, b: Float, w: Float? = null): String = buildString {
    append("SET_LED LED=$name")
    append(" RED=${fmt(r.coerceIn(0f,1f).toDouble(),2)} GREEN=${fmt(g.coerceIn(0f,1f).toDouble(),2)} BLUE=${fmt(b.coerceIn(0f,1f).toDouble(),2)}")
    w?.let { append(" WHITE=${fmt(it.coerceIn(0f,1f).toDouble(),2)}") }
}                                                                          // Off (D-12) = setLed(name,0,0,0,0)

fun setServoAngle(name: String, deg: Int, maxDeg: Int): String =          // servo
    "SET_SERVO SERVO=$name ANGLE=${deg.coerceIn(0, maxDeg)}"

fun setPinDigital(name: String, on: Boolean): String =                    // output_pin (pwm:false)
    "SET_PIN PIN=$name VALUE=${if (on) 1 else 0}"

fun setPinPwm(name: String, pct: Int): String =                           // output_pin (pwm:true) AND pwm_tool
    "SET_PIN PIN=$name VALUE=${fmt(pct.coerceIn(0,100)/100.0, 2)}"        // pwm_tool has NO dedicated cmd
```

### Live status field map (VERIFIED against both printers + Status_Reference)
```
fan_generic     → .speed   (Float 0..1)   [+ .rpm, usually null]   → row shows round(speed*100)%
heater_generic  → .temperature, .target (Float °C) [+ .power 0..1] → row shows current temp
led/neopixel/   → .color_data : [[r,g,b,w], ...]  each 0..1        → row shows swatch + brightness%
  dotstar/pca*       (whole-strip = index 0; per-index out of scope)
output_pin      → .value  (Float; 0/1 for digital, 0..1 for pwm)  → digital On/Off | pwm round(value*100)%
servo           → .value  (Float 0..1, PWM — NOT angle)           → row HIDES value (not readable as °)
pwm_tool        → .value  (Float 0..1) [groups with output_pin]   → row shows round(value*100)%
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Guess discovery from objects.list alone | configfile.settings (metadata) ∩ objects.list (runtime) | this research | objects.list can't tell digital vs PWM or servo range; settings can |
| Assume `SET_PWM_TOOL` exists | `pwm_tool` uses `SET_PIN` | confirmed | no special command path needed |
| Per-LED-family commands | one `SET_LED` for all 5 families | confirmed live + ref | single RGB+brightness page, no fallback |

## Discovery Algorithm (the key deliverable)

**Source decision (CONFIRMED): use `configfile.settings`, filtered to the whitelist, with `objects.list`
as the runtime/case-correct cross-check.** Comparison of the three candidate sources on the real printers:

| Source | Gives | Verdict |
|--------|-------|---------|
| `printer.objects.list` | live object names, case-PRESERVED; nothing about pwm/range | Necessary for runtime confirm + command case; insufficient alone |
| `configfile.config` | raw config; `pwm` key present ONLY if typed; values are strings | Not used — pwm absent on digital pins, strings need parsing |
| **`configfile.settings`** | parsed/defaulted; `pwm` ALWAYS a real bool; numbers parsed; names LOWERCASED | **Chosen source** for type/metadata |

**Algorithm the planner should specify:**
1. At handshake, in the existing one-shot `configfile` block (`MoonrakerSession.kt:498`), read
   `configfile.settings` (already in hand — add a consumer, no new query).
2. Filter entries whose key's first token ∈ whitelist.
3. For each, recover the case-correct `rawName` by case-insensitive match against `Capabilities.objects`
   (drop any settings entry with no matching live object — configured-but-not-loaded).
4. Build `OutputDescriptor`: `rawName`, `family`, `bare` (name after the space), `pwm` (from settings),
   `servoAngleMax` (servo `maximum_servo_angle`, default 180), `readOnly` (see §4).
5. Sort the resulting list alphabetically by `prettyName` (§ Name prettifying).
6. **D-10 gate:** if the list is empty, hide the Outputs drawer tile (mirror `CalibrationGate`).
7. Live values come from the central `objects/subscribe`, keyed by `rawName` (§ status map).

**How each family appears (VERIFIED live):**
- `fan_generic FILTER_fan`, `led chamber_light`, `neopixel expanderPixel`, `servo camera_servo`,
  `output_pin mosfet2` (E5P); `output_pin ignore_m600` (E3P). Object key = `"<family> <bare>"` in all three
  sources; settings lowercases both the family stays same-case (already lowercase) and the bare name.

## Name Prettifying (the exact transform)

From the verified names. Transform: `bare = rawName.substringAfter(' ')` → replace `_` and `-` with spaces
→ title-case each word → trim collapse of multiple spaces.
```
fan_generic FILTER_fan  → "FILTER fan"  → title-case → "Filter Fan"
led chamber_light       → "chamber light" → "Chamber Light"
neopixel expanderPixel  → "expanderPixel" → (no _/-) → "Expanderpixel"  ⚠ camelCase edge case
output_pin bed_safety_switch → "Bed Safety Switch"
servo camera_servo      → "Camera Servo"
output_pin ignore_m600  → "Ignore M600"
```
**Edge case observed (`expanderPixel`):** camelCase bare names don't word-split on case; a naive title-case
yields "Expanderpixel". **Recommendation:** keep the transform simple (split on `_`/`-`/space only, do NOT
attempt camelCase splitting — it's lossy and ambiguous); accept "Expanderpixel". The staging doc says rely
on icon/type for disambiguation and preserve the raw name internally; do NOT add raw-name suffixes for
duplicate pretty names. [VERIFIED: live names from both printers]

## Read-only / Non-settable Detection (SC-3)

Most whitelisted outputs ARE settable. The cases to degrade:
- **`output_pin` with `static_value`** (the `[static_digital_output]`/static-pin pattern): `SET_PIN` is
  rejected. Detect: a `static_value` key in settings, OR absence of a `value`/`pwm` settable surface.
  Neither dev printer has one, but the planner should mark `readOnly = true` and render the row value-only
  (no detail control, or a disabled detail). [CITED: Klipper Config_Reference — output_pin static_value]
- **`virtual_pin`-backed output_pins** (E5P `virtual_pause`, E3P `ignore_m600`): these ARE settable via
  `SET_PIN` (they're real `[output_pin]` sections; `pin: virtual_pin:...`). They are flow-control flags, not
  hardware, but the staging whitelist includes all `output_pin` — show them. (Owner can refine later; not a
  read-only case.) [VERIFIED: live status `{"value":0.0}`, settable]
- **servo:** angle not readable (Pitfall 4) — degrade the row VALUE only; control still works.
- **`pca9632`/`pca9533`:** respond to `SET_LED` like other LED families (per ref); none on dev printers, so
  built blind — treat as LED, no special read-only case. [CITED: Status_Reference groups them under LED]
- **General rule:** the staging "no command the printer can't accept" is satisfied by (a) only ever
  whitelisting settable families, (b) marking static-value pins read-only, and (c) clamping every value to
  config bounds so Klipper never rejects an out-of-range value.

## Per-Type Control-Page Shape Mapping

| Family | Detail control | Reuse | Off action | New shape needed? |
|--------|---------------|-------|-----------|-------------------|
| heater_generic | ScrubberPage °C + current-temp readout + Off | ScrubberPage | TARGET=0 | No |
| fan_generic | ScrubberPage % + Off | ScrubberPage | SPEED=0 | No |
| led/neopixel/dotstar/pca9533/pca9632 | ColorWheel(hue) + brightness ScrubberPage + Off | ColorWheel + ScrubberPage + `hsvToRgb` | RED=0 GREEN=0 BLUE=0 WHITE=0 | Small pure helper only |
| servo | ScrubberPage ° (0..maxDeg) | ScrubberPage | (staging "disable" — WIDTH=0; verify per-servo, optional) | No |
| output_pin (pwm:false) | Toggle page (On/Off) | NEW small toggle page (or 2-step scrubber) | VALUE=0 | Small new toggle page |
| output_pin (pwm:true) | ScrubberPage % | ScrubberPage | VALUE=0 | No |
| pwm_tool | ScrubberPage % | ScrubberPage | VALUE=0 | No |

**Two NEW pieces only:** (1) a digital **Toggle page** for non-PWM output_pin (no existing on/off page; the
red/green Off-everywhere-else pattern + a single big toggle — small, mirror ScrubberPage scaffold), and
(2) an `hsvToRgb` pure function for the LED page. Everything else reuses ScrubberPage/ColorWheel verbatim.

**ScrubberPage immediate-dispatch caveat:** `ScrubberPage` has an Apply/Cancel commit contract
(`onApply`/`onCancel`). The staging doc mandates **immediate dispatch, no Apply flow**. The planner must
either (a) wire `onValueChange`→dispatch (debounced/on-settle) and hide/repurpose Apply, or (b) keep Apply
as the dispatch trigger but the doc says no Apply flow — so prefer **dispatch on settle** (pointer-up), the
same cadence ColorWheel uses (`onSettle`). This matches "immediate dispatch" without spamming the wire on
every scrub frame. Flag this as the one ScrubberPage-fit decision for the planner.

## Clamp Authority (Phase-17 lesson — single source in PrinterCommands)

Add named bounds + clamp helpers in `PrinterCommands` (alongside the Fine-Tune block). Every detail page's
optimistic `markPending` target MUST feed the SAME clamp output as the dispatched command (17-07 lesson —
unclamped markPending wedged the busy lock). Bounds:

| Output | Clamp | Notes |
|--------|-------|-------|
| fan_generic %, output_pin pwm %, pwm_tool % | 0..100 (→ /100 → 0..1 wire) | display %; wire 0..1 |
| heater_generic °C | 0..MAX_TEMP_C (350) | reuse existing MIN/MAX_TEMP_C |
| servo angle | 0..`maximum_servo_angle` (per-descriptor, default 180) | config-driven ceiling |
| LED R/G/B/W | 0f..1f each | clamp in setLed |
| output_pin digital | {0,1} | boolean → 0/1 |

## Validation Architecture

> nyquist_validation is enabled (config.json) — section included.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 host unit tests (existing `app/src/test`) + Robolectric/Compose where used; build Windows-side via `E:\Android\gw.bat` |
| Quick run command | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.outputs.* --no-daemon"` |
| Full suite command | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |

### Success Criterion → Test Map
| SC | Behavior | Test Type | Automated Command | Exists? |
|----|----------|-----------|-------------------|---------|
| SC-1 | parseOutputs filters whitelist, recovers case, excludes std outputs | unit (pure, fixture from live captures) | `--tests works.mees.dinghy.outputs.OutputsGateTest` | ❌ Wave 0 |
| SC-1 | D-10 empty → tile hidden | unit | `OutputsGateTest#emptyHidesTile` | ❌ Wave 0 |
| SC-2 | every builder emits correct cmd + 0–100%→0–1 scaling + clamp | unit | `PrinterCommandsOutputsTest` | ❌ Wave 0 |
| SC-2 | digital vs pwm output_pin branch on settings `pwm` | unit | `OutputsGateTest#pwmDetection` | ❌ Wave 0 |
| SC-3 | absent live value → row value hidden, row tappable; static pin read-only | unit (holder/state) | `OutputsHolderTest` | ❌ Wave 0 |
| SC-3 | servo value not mapped to angle (degrade) | unit | `OutputsHolderTest#servoValueHidden` | ❌ Wave 0 |
| SC-4 | live proof against real printers | **manual on-device + live REST capture** | owner UAT on flox + E5P/E3P (see below) | manual |
| D-09 | all 8 ligatures resolve | tooling gate | `python tools/verify_ligatures.py` (extend NEEDED set) | ✅ extend |

### Fixtures from live captures (use the real JSON — mock-vs-reality lesson)
The live captures taken in this research (E5P: 9 whitelisted outputs incl. fan_generic/led/neopixel/servo×2/
output_pin×4; E3P: 1 virtual output_pin) MUST be saved as test fixtures so `OutputsGateTest` parses the REAL
Moonraker shape, not an idealized mock. This directly closes the [[dinghy-display-mock-vs-reality]] trap.

### SC-4 "proven live" plan
- Read-only REST verification (already done in research): GET `/printer/objects/query?<obj>` returns the
  exact status shape per type — re-runnable as a smoke check.
- On-device UAT on flox connected to E5P: open Outputs tile → see Filter Fan, Chamber Light, Expanderpixel,
  Camera Servo ×2, Mosfet2/3, the virtual pins → set Filter Fan %, set Chamber Light color+brightness, toggle
  a mosfet (PWM scrubber), set a servo angle → confirm each via the object state flip (and cross-check
  server-side `/server/gcode_store` per [[dinghy-stream-uat-via-moonraker]]). Owner is the authoritative gate.
- E3P shows only "Ignore M600" — confirms a sparse printer renders (and that the tile is NOT hidden when ≥1).

### Sampling Rate
- Per task commit: quick unit run for `outputs.*` + `PrinterCommandsOutputsTest`.
- Per wave merge: full `:app:testDebugUnitTest`.
- Phase gate: full suite green + `verify_ligatures.py` exit 0 + owner on-device UAT before `/gsd-verify-work`.

## Security Domain

> security_enforcement enabled, ASVS L1.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V5 Input Validation | yes | Clamp-before-format in `PrinterCommands` (existing discipline); object NAMES come from configfile/objects.list (printer-controlled, fixed set), never free-text |
| V5 Injection | yes | No user free-text is concatenated into gcode. Names are printer-supplied identifiers; values are bounded numerics. (Unlike bed-mesh profile names, there is NO keyboard input on these pages.) |
| V6 Cryptography | no | — |
| V2/V3/V4 | no | local LAN, Moonraker trusted-client model (project-wide) |

### Known Threat Patterns
| Pattern | STRIDE | Mitigation |
|---------|--------|-----------|
| Out-of-range value Klipper rejects (or drives hardware unsafely) | Tampering | clamp to config bounds before format (servo to maximum_servo_angle, fan/pwm 0..1, temp ≤ MAX_TEMP_C) |
| Object name with shell/gcode metachars | Injection | names are printer-config identifiers, not user input; still, dispatch via `printer.gcode.script` single-line — a malformed config name would already break the printer, not this app |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `pca9533`/`pca9632`/`dotstar` respond to `SET_LED` and report `color_data` identically to led/neopixel (none present on dev printers — built blind) | §1/§3 | LOW — Status_Reference groups them under LED; if a family differs, that one LED page degrades, others unaffected |
| A2 | `output_pin` with `static_value` is the read-only case (none present on dev printers) | §4 | LOW — degrade-to-readonly is conservative; worst case a settable pin shows read-only |
| A3 | servo "disable" via `WIDTH=0` is acceptable; some servos need a different disable | §7 | LOW — staging marks disable optional; angle scrubber is the primary control |
| A4 | HSV-with-fixed-saturation (hue+brightness) is sufficient for v1 LED control | Don't-Hand-Roll | MEDIUM — owner may want full saturation; flagged as a planner decision, ColorWheel is hue-only today |

## Open Questions

1. **LED saturation control?** ColorWheel is hue-only. Recommend hue + brightness (fixed saturation=1.0) for
   v1 — reuses ColorWheel verbatim. Planner/owner confirm if full RGB/saturation is wanted. (Low risk — can extend later.)
2. **ScrubberPage immediate-dispatch wiring.** Dispatch on settle (pointer-up) vs on Apply — recommend
   on-settle to honor "no Apply flow." Planner picks the exact wiring (a small ScrubberPage usage choice, not a new component).
3. **Pretty-name camelCase** (`expanderPixel`→"Expanderpixel") — recommend NOT splitting camelCase (lossy);
   accept it. Confirm acceptable.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Moonraker E5P (192.168.1.120:7125) | live discovery/command verification + SC-4 UAT | ✓ | (queried live) | E3P |
| Moonraker E3P (192.168.1.121:7125) | sparse-printer UAT | ✓ | (queried live) | E5P |
| fonttools (verify_ligatures.py) | D-09 gate | ✓ | 4.63.0 (WSL py3.13) | — |
| Windows Gradle build (gw.bat) | host tests + on-device install | ✓ | JDK21/SDK35 | — |

No missing dependencies. Both printers reachable and queried successfully during this research.

## Sources

### Primary (HIGH confidence)
- **Live capture, E5P** `http://192.168.1.120:7125/printer/objects/{list,query}` — whitelisted objects, status shapes (servo→value, fan→speed, led→color_data array, output_pin→value), `configfile.config` vs `configfile.settings` (pwm-bool normalization, name lowercasing), `gcode/help` (SET_PWM_TOOL absent, SET_PIN/SET_LED/SET_SERVO/SET_FAN_SPEED/SET_HEATER_TEMPERATURE present) — VERIFIED 2026-06-07
- **Live capture, E3P** `http://192.168.1.121:7125/...` — sparse printer (1 virtual output_pin), confirms degrade/empty paths — VERIFIED 2026-06-07
- **`tools/verify_ligatures.py` run** against `app/src/main/res/font/material_symbols_outlined.ttf` (v2.944) — all 8 D-09 glyphs PASS via GSUB LigatureSubst; `mode_fan` still resolves — VERIFIED 2026-06-07
- klipper3d.org/G-Codes.html — SET_FAN_SPEED (0..1), SET_HEATER_TEMPERATURE (TARGET default 0), SET_LED (RED/GREEN/BLUE/WHITE 0..1, INDEX/TRANSMIT/SYNC), SET_SERVO (ANGLE/WIDTH, WIDTH=0 disable), SET_PIN (digital 0/1, pwm 0..1/scale), no SET_PWM_TOOL — CITED
- klipper3d.org/Status_Reference.html — exact status fields per object family — CITED
- Existing code: `MoonrakerSession.kt:498` (one-shot configfile consumer pattern), `Capabilities.kt` (objects/hasObject, hasMacroIgnoreCase case lesson), `PrinterCommands.kt` (clamp authority), `ScrubberPage.kt`/`ColorWheel.kt` (reuse + hue-only caveat), `CalibrationGate.kt`/`CalibrationHubScreen.kt` (gate + hub precedents), `DinghyIcons.kt:80` (D-08 target) — VERIFIED

### Secondary (MEDIUM)
- klipper3d.org/Config_Reference.html — output_pin static_value, pwm_tool grouping (excerpt sparse; cross-checked against Status_Reference)

## Metadata

**Confidence breakdown:**
- Discovery algorithm: HIGH — compared all three sources on two real printers; reuses existing one-shot query
- Command syntax/ranges: HIGH — verified against live gcode/help + Klipper G-Code reference
- Status fields: HIGH — verified live per type on both printers
- D-09 ligatures: HIGH — all 8 PASS via the canonical font GSUB tool
- LED families pca/dotstar: MEDIUM — built blind (absent on dev printers), grouped under SET_LED by reference

**Research date:** 2026-06-07
**Valid until:** ~2026-09-07 (stable Klipper/Moonraker surface; re-verify if printers reconfigured)
