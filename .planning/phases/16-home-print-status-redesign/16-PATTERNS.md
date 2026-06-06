# Phase 16: Home / Print-Status Redesign - Pattern Map

**Mapped:** 2026-06-06
**Files analyzed:** 13 (4 net-new, 6 modified, 3 doc deliverables)
**Analogs found:** 10 / 10 code files (every net-new/modified file has a strong in-repo analog)

> This is a UI rework of a mature codebase. Almost nothing is greenfield — every "new" file is a
> sibling or extension of an existing one. RESEARCH §2 already classified the work as
> "recomposition of existing parts into four modes + 4 small net-new additions." This map ties each
> file to the precise analog lines a plan can copy from.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `ui/printstatus/PrintStatusMode.kt` **(NEW)** | model (pure classifier) | transform | `ui/printstatus/PrintStatusControlModel.kt` (`derivePrintStatusControls`) | exact (same seam, pure-fun-of-PrinterState) |
| `command/CommandRegistry.kt` (babystep + dismiss specs) **(MOD)** | command/registry | request-response | existing gcode specs `setHeater`/`jog`/`testZ` (this file) | exact |
| `command/PrinterCommands.kt` (`setGcodeOffsetZAdjust`, `SDCARD_RESET_FILE`) **(MOD)** | command/builder | request-response | existing builders `testZ`/`jog`/const action gcodes (this file) | exact |
| `state/PrinterStateReducer.kt` (parse `homing_origin[2]` + glance temp sensor) **(MOD)** | state/reducer | streaming (notify_status_update) | existing `gcode_move` block @106-111 (this file) | exact |
| `state/PrinterState.kt` (`gcodeZOffset` + glance-temp field) **(MOD)** | model | — | existing nullable fields (`currentLayer`, `gcodePosition`) (this file) | exact |
| `state/DeriveCapabilities.kt` (subscribe a `temperature_sensor`) **(MOD)** | state/derivation | transform | `V1_SUBSCRIBE_CORE` + dynamic-object loop (this file) | exact |
| `ui/printstatus/PrintStatusControlModel.kt` (extend actions: Preheat/Dismiss/Power) **(MOD)** | model | transform | the existing control-set derivation (this file) | exact (self-extension) |
| `ui/printstatus/PrintStatusScreen.kt` (route off mode; Preheat; babystep row; glance) **(MOD)** | component (Compose) | event-driven / request-response | itself — harvest `StatGrid`/`PrintStatusFocus`/`StopButton`/`PrintStatusControlTile` | exact (in-place harvest) |
| Babystep app-setting prefs (`ui/.../BabystepPrefs.kt` or fold into existing) **(NEW)** | store (DataStore) | CRUD (persist) | `ui/macros/MacroPrefs.kt` + `AppContainer.writeScope` intent helpers | exact |
| Preheat spool-aware action (in `PrintStatusScreen` + reuse `PresetSelector`) **(MOD/reuse)** | component | request-response | `ui/temperature/TemperatureScreen.kt` `PresetSelector`/`applyPreset` path | exact |
| Drawer Output + System Info stubs **(MOD)** | component | — | `ui/shell/AppDrawer.kt` `DrawerTileSpec(dest=null)` greyed pattern | exact |
| `docs/ui_design/README.md` / `LAYOUT.md` / artboards **(MOD, deliverable)** | docs | — | existing doc sections | n/a (prose) |

---

## Pattern Assignments

### `ui/printstatus/PrintStatusMode.kt` (NEW — pure classifier, model, transform)

**Analog:** `ui/printstatus/PrintStatusControlModel.kt` — same package, same "pure function of
`PrinterState`, toolkit-agnostic, host-testable" discipline. The classifier is the formalized
upstream of this existing per-state derivation.

**Pattern to copy — pure `when(state.printState)` over the 6-variant enum.** The analog's
derivation (`PrintStatusControlModel.kt:43-75`) already switches on exactly the enum the classifier
needs (`PrintState.{Printing,Paused,Complete,Error,Cancelled,Standby}` from `PrinterState.kt:214`):

```kotlin
val controls = when (state.printState) {
    PrintState.Printing -> activeControls(...)
    PrintState.Paused   -> activeControls(...)
    PrintState.Complete, PrintState.Error, PrintState.Cancelled -> terminalControls(...)
    PrintState.Standby  -> if (restartFilename != null) terminalControls(...) else standbyControls()
}
```

**⚠ BEHAVIOR CHANGE the classifier MUST make (Pitfall 1):** the analog's `Standby` branch
(`PrintStatusControlModel.kt:67-74`) renders **terminal** controls when `restartFilename != null`.
The new `classifyPrintStatus` must NOT — `Standby` is always `Standby` regardless of a leftover
file. The restart-from-idle affordance moves to the Standby launcher Files tile. Classify purely
off `printState`; do not read `printFilename`/`lastJob` in the classifier.

**Recommended shape (RESEARCH §1):**
```kotlin
sealed interface PrintStatusMode {
    data object Standby : PrintStatusMode
    data object Printing : PrintStatusMode
    data object Paused : PrintStatusMode
    data class Terminal(val kind: TerminalKind) : PrintStatusMode
    enum class TerminalKind { Complete, Cancelled, Error }
}
fun classifyPrintStatus(state: PrinterState): PrintStatusMode = when (state.printState) {
    PrintState.Printing  -> PrintStatusMode.Printing
    PrintState.Paused    -> PrintStatusMode.Paused
    PrintState.Complete  -> PrintStatusMode.Terminal(TerminalKind.Complete)
    PrintState.Cancelled -> PrintStatusMode.Terminal(TerminalKind.Cancelled)
    PrintState.Error     -> PrintStatusMode.Terminal(TerminalKind.Error)
    PrintState.Standby   -> PrintStatusMode.Standby
}
```

**Klippy axis is separate (verified):** read ONLY `printState`. `klippyState`
(`PrinterState.kt:33` "distinct axis from klippyState", enum @211) drives splash/recovery — never
folded into a terminal print-result mode.

---

### `command/CommandRegistry.kt` + `command/PrinterCommands.kt` (MOD — new gcode specs, request-response)

**Analog (CommandRegistry):** the `testZ` spec (`CommandRegistry.kt:483-488`) — a parameterized
gcode spec that clamps via a `PrinterCommands` builder; and `setHeater` (`@316-321`). The whole
gcode-spec plumbing is the private `gcode(...)` factory (`@590-603`), which auto-wraps the builder
output in `scriptParams` and inherits the 120s G4 timeout off `method == GCODE_SCRIPT`.

**Spec pattern to copy** (`testZ`, lines 483-488):
```kotlin
val testZ: CommandSpec<TestZArgs> = gcode(
    catalogId = "KGC-TESTZ",
    key = { "testz" },
    gcode = { args -> PrinterCommands.testZ(args.step) },
    availability = AvailabilityPredicate.ObjectPresent("manual_probe"),
)
```
For babystep: `BabystepArgs(deltaMm: Double)` → `gcode = { PrinterCommands.setGcodeOffsetZAdjust(it.deltaMm) }`;
availability `ObjectPresent("gcode_move")` (the object that carries `homing_origin`). For Dismiss:
`SDCARD_RESET_FILE` is a no-param const-gcode like `cooldown`/`disableSteppers`
(`CommandRegistry.kt:400-412`) → `gcode = { PrinterCommands.SDCARD_RESET_FILE }`, availability
`ObjectPresent("virtual_sdcard")` (mirrors `printStart` @192). **Both new specs MUST be appended to
the `all` list** (`@512-565`).

**Args data-class pattern** (top of `CommandRegistry.kt:19-25`):
```kotlin
data class SetHeaterArgs(val heater: String, val target: Int, val key: String? = null)
data class TestZArgs(val step: Double)   // @52 — exactly the babystep arg shape
```

**Analog (PrinterCommands builder + clamp discipline):** `testZ` (`PrinterCommands.kt:198`) — the
canonical "clamp-before-format, no free text" builder:
```kotlin
fun testZ(step: Double): String = "TESTZ Z=${step.coerceIn(-MAX_TESTZ_MM, MAX_TESTZ_MM)}"
```
Const-gcode pattern (no params): `COOLDOWN`/`SAVE_CONFIG` (`PrinterCommands.kt:60,94`):
```kotlin
const val COOLDOWN = "TURN_OFF_HEATERS"
```

**Babystep builder (RESEARCH Code Examples) — validate-against-set, not clamp-to-range** (the step
is one of 5 fixed values, mirror the `MATERIAL_PRESETS` fixed-set discipline at `PrinterCommands.kt:104`):
```kotlin
val BABYSTEP_STEPS = listOf(0.02, 0.05, 0.10, 0.15, 0.20)   // staging-note cycle
fun setGcodeOffsetZAdjust(deltaMm: Double): String =
    "SET_GCODE_OFFSET Z_ADJUST=$deltaMm MOVE=1"   // deltaMm must be ±BABYSTEP_STEPS (caller-validated)
const val SDCARD_RESET_FILE = "SDCARD_RESET_FILE"
```
ASVS V5 note (`PrinterCommands.kt` header @16-18): no user string is ever concatenated; the step
comes only from the fixed-cycle tap, so validate against the set rather than free-text.

---

### `state/PrinterStateReducer.kt` + `state/PrinterState.kt` (MOD — parse applied Z offset, streaming)

**Analog:** the existing `gcode_move` walk (`PrinterStateReducer.kt:106-111`) — `gcode_move` is
already subscribed and already walked for `speed_factor`/`extrude_factor`/`gcode_position`. Add ONE
line inside the SAME block:

```kotlin
status.objectOrNull("gcode_move")?.let { gm ->
    gm.doubleOrNullAt("speed_factor")?.let { s = s.copy(speedFactor = it) }
    gm.doubleOrNullAt("extrude_factor")?.let { s = s.copy(extrudeFactor = it) }
    gm.doubleListOrNull("gcode_position")?.let { s = s.copy(gcodePosition = it) }
    // NEW — applied Z offset readback (babystep), homing_origin = [X,Y,Z,E]; Z at index 2:
    gm.doubleListOrNull("homing_origin")?.let { s = s.copy(gcodeZOffset = it.getOrNull(2)) }
}
```

The `doubleListOrNull` helper already exists (`PrinterStateReducer.kt:252-253`) — reuse verbatim,
it null-safes a non-array / non-numeric read. Verify on device that `homing_origin` is `[X,Y,Z,E]`
with Z at index 2 (RESEARCH A4 / SC-5).

**Analog field (PrinterState):** the existing nullable fields are the exact shape — `currentLayer`
(`PrinterState.kt:75`) and `gcodePosition` (`@47`). Add:
```kotlin
/** gcode_move.homing_origin[2] — applied Z offset (babystep). Null until first reported. */
val gcodeZOffset: Double? = null,
```
**Glance temp field** follows the same nullable-with-doc convention; source it from a
`temperature_sensor` object (next file).

**Stat-row display of the offset:** the Applied-Z-offset row goes in `StatGrid`
(`PrintStatusScreen.kt:441`) next to the existing `fmtZ(state)` Current-Z cell (`@450`) — extend the
grid, do not add a new component (RESEARCH §2 harvest map).

---

### `state/DeriveCapabilities.kt` (MOD — subscribe a glance temperature_sensor, transform)

**Analog:** `V1_SUBSCRIBE_CORE` (`DeriveCapabilities.kt:50-68`) + the dynamic-object loop in
`deriveSubscribeSet` (`@86-95`). Today the subscribe set deliberately omits bare
`temperature_sensor X` entries (confirmed by the `MoonrakerSession.kt:470` ignore note). To surface
the Standby glance MCU/host temp, add a dynamic rule to the loop (mirrors the `heater_generic`
clause at `@93`):

```kotlin
for (name in objects) {
    when {
        EXTRUDER_N.matches(name) -> result += name
        name == "fan" || name.startsWith("fan_generic ") || ... -> result += name
        name.startsWith("heater_generic ") -> result += name
        name.startsWith("temperature_sensor ") -> result += name   // NEW (glance metric, §3)
    }
}
```

Then the reducer (above file) parses the chosen sensor's `temperature` into the glance field. The
glance rule (RESEARCH §3): prefer a `*mcu*` sensor, else `*host*`, else the first
`temperature_sensor`; OMIT the line entirely if none exist. **Do NOT pull `machine.proc_stats`
host-load into P16** (Pitfall 4 — that is a separate Moonraker surface, Phase 19). Heater parsing in
the reducer is gated by `isHeaterObject` (`PrinterStateReducer.kt:212-213`) which excludes
`temperature_sensor`, so the new sensor needs its own small walk, not the heater loop.

---

### `ui/printstatus/PrintStatusControlModel.kt` (MOD — extend control actions, transform)

**Analog:** itself. The `PrintStatusControlAction` enum (`@20-28`) currently lacks Preheat /
Dismiss / Power. Add members and per-mode control-set builders mirroring the existing
`activeControls`/`terminalControls`/`standbyControls` private helpers (`@104-155`). The
`controlColor` mapping in the screen (`PrintStatusScreen.kt:810-824`) must gain the new intents
(UI-SPEC "Per-state button intents"): Preheat=accent, Resume=go, Reprint=accent, Dismiss=neutral,
Power=inert-stop. The `PrintStatusPendingAction` debounce machinery (`@30-35`,
`clearPrintStatusPendingAction` @79-102) is already host-tested — reuse it, do not re-roll.

---

### Babystep app-setting persistence (NEW prefs class — store, CRUD)

**Analog (DataStore shape):** `ui/macros/MacroPrefs.kt` — the canonical injected-DataStore,
fail-safe-read, suspend-write prefs class. Copy its shape EXACTLY (its own header says it copies
ConnectionStore/ThemePrefs):

```kotlin
class BabystepPrefs(private val dataStore: DataStore<Preferences>) {
    val enabled: Flow<Boolean> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[KEY_ENABLED] ?: true }                 // default ENABLED (D-06)
    val layerCount: Flow<Int> = dataStore.data
        .catch { ... }.map { it[KEY_LAYERS] ?: 5 }        // default 5 (D-06)
    suspend fun setEnabled(on: Boolean)     { dataStore.edit { it[KEY_ENABLED] = on } }
    suspend fun setLayerCount(n: Int)       { dataStore.edit { it[KEY_LAYERS] = n.coerceAtLeast(1) } }
    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("babystep_enabled")
        private val KEY_LAYERS  = intPreferencesKey("babystep_layers")
    }
}
```

**⚠ Analog (writeScope intent helpers) — the load-bearing one:** `AppContainer.writeScope`
(`AppContainer.kt:109`) + the `setDevCyclerEnabled` intent helper (`@240-242`) is the EXACT pattern
to copy. UI calls a `container.setBabystepEnabled(...)` intent method that does
`writeScope.launch { babystepPrefs.setEnabled(...) }` — NEVER `rememberCoroutineScope().launch`
([[dinghy-compose-write-scope-cancellation]], reproduced on Phases 14/15/15.1/15.2). Do the
read-modify-write inside one `dataStore.edit` (the `mutateActive` discipline @133/@500).

```kotlin
// AppContainer.kt — copy the setDevCyclerEnabled shape @240
fun setBabystepEnabled(on: Boolean) { writeScope.launch { babystepPrefs.setEnabled(on) } }
fun setBabystepLayers(n: Int)       { writeScope.launch { babystepPrefs.setLayerCount(n) } }
```

The toggle + numeric-keyboard layer-count UI lives under the Settings tile (Phase-15.2 four-tile
IA). The numeric keyboard is allowed here — Settings is keyboard-permitted
([[numeric-keyboard-for-numeric-fields]]).

**Babystep gating (RESEARCH §4) — strict nullable layer, no time fallback:**
```kotlin
val babystepVisible = settingEnabled &&
    state.currentLayer?.let { it <= layerThreshold } == true   // null layer → hidden
```
`currentLayer` (`PrinterState.kt:75`) is already the slicer-dependent nullable field.

---

### Spool-aware Preheat (MOD in `PrintStatusScreen` + reuse `PresetSelector`)

**Analog (selector + dispatch):** `ui/temperature/TemperatureScreen.kt` `PresetSelector`
(`@405-450`) and the `applyPreset` dispatch (`@221-234`):
```kotlin
PresetSelector(
    inFlight = inFlight,
    onPreset = { p ->
        dispatchCommand(CommandRegistry.applyPreset,
            ApplyPresetArgs(nozzle = p.nozzle, bed = p.bed, key = "preset_${p.name}"))
        showPresets = false
    },
    onDismiss = { showPresets = false },
)
```
The selector is a full-screen opaque scrim of `OutlinedControl(Intent.Accent)` tiles over
`PrinterCommands.MATERIAL_PRESETS` — reuse for the fallback path.

**Analog (spool detail resolution):** already present in `PrintStatusScreen.kt:133-155` —
`spoolmanPresent`, `activeSpool` → `activeSpoolId` → `spoolDetail: SpoolmanSpool` via
`container.currentSpoolmanClient.getSpool(id)`. Read `spoolDetail.filament` temps:
`SpoolmanFilament.settingsExtruderTemp` / `settingsBedTemp` (`SpoolmanModels.kt:64-65`, both
`Int?`, never fabricated).

**Rule (D-01 / RESEARCH §5) — per-temp `setHeater`, NEVER pass 0:**
```kotlin
val noz = spoolDetail?.filament?.settingsExtruderTemp
val bed = spoolDetail?.filament?.settingsBedTemp
if (spoolmanPresent && (noz != null || bed != null)) {
    noz?.let { dispatcher?.dispatch(CommandRegistry.setHeater, SetHeaterArgs("extruder", it, "preheat_noz")) }
    bed?.let { dispatcher?.dispatch(CommandRegistry.setHeater, SetHeaterArgs("heater_bed", it, "preheat_bed")) }
} else { showPresets = true }   // no Spoolman / both null → fall through to PresetSelector
```
`setHeater` per-temp (`CommandRegistry.kt:316`) avoids the `applyPreset` two-line trap that would
send `TARGET=0` for a missing temp and cool the heater (Pitfall 5). `SetHeaterArgs` takes a non-null
`Int` and a `key` (`@19`).

---

### Drawer Output + System Info forward stubs (MOD `ui/shell/AppDrawer.kt`)

**Analog:** the greyed-tile pattern in the same file. The inert Power tile
(`AppDrawer.kt:186`) is the template for a `dest = null` greyed tile:
```kotlin
DrawerTileSpec(label = "Power", symbol = "power_settings_new", dest = null, danger = true),
```
For D-02 add to `DRAWER_TILES` (`@144-187`) — `dest = null` greys them (the live/greyed decision is
`tile.dest != null` at `@208`; greyed tiles get no `clickable` and `semantics { disabled() }` @227-231):
```kotlin
DrawerTileSpec(label = "Output",      symbol = "<unique-glyph>", dest = null),   // P18
DrawerTileSpec(label = "System Info", symbol = "<unique-glyph>", dest = null),   // P19
```
**Honor the icon-no-repeat law** (every comment in `DRAWER_TILES` enforces unique glyphs). The
greyed styling (`t.hair` outline, `t.text3` content @212-238) is already correct — only the spec
entries are new. `ShellPresenceTest` already asserts greyed tiles do not navigate — extend it.

> NOTE: forward stubs live in the **drawer only** (D-02) — the Standby launcher grid does NOT carry
> them. The launcher's flexible/growing tile is the **Drawer** tile (UI-SPEC); the active-print
> shortcut grid's flexible tile is **Tune**.

---

## Shared Patterns

### Gcode command discipline (clamp/validate-before-format, ASVS V5)
**Source:** `command/PrinterCommands.kt` header (@7-22) + every builder (e.g. `testZ` @198, `jog`
@131).
**Apply to:** both new builders (`setGcodeOffsetZAdjust`, `SDCARD_RESET_FILE`). No user string is
ever concatenated into a script; numerics are clamped or validated-against-a-fixed-set BEFORE
formatting. Babystep step = one of 5 fixed values → validate-against-set (mirror `MATERIAL_PRESETS`).

### Command spec registration
**Source:** `command/CommandRegistry.kt` private `gcode(...)`/`jsonRpc(...)` factories (@573-603) +
the `all` list (@512-565).
**Apply to:** every new spec — use the factory (it auto-wraps `scriptParams` and inherits the
gcode 120s timeout) and APPEND to `all` (a spec missing from `all` is dead — this exact
"compiles-but-dead-wiring" class was caught by the plan-checker on Phase 11).

### Pure, host-testable state derivation
**Source:** `state/DeriveCapabilities.kt` + `state/PrinterStateReducer.kt` + `PrintStatusControlModel.kt`
headers — all "no I/O, no coroutines, no Compose; same input → same output."
**Apply to:** `PrintStatusMode.kt` (Wave-0 `PrintStatusModeTest`), the reducer additions, the
babystep gating helper, the per-temp Preheat selection logic. These are the unit-testable seams
(RESEARCH Validation Architecture). Wave-0 RED scaffolds MUST compile day-one
([[dinghy-wave0-red-scaffold-compile]]).

### DataStore persistence via process-lifetime writeScope (NOT composition scope)
**Source:** `ui/macros/MacroPrefs.kt` (prefs shape) + `di/AppContainer.kt:109,240` (`writeScope` +
intent helpers).
**Apply to:** the babystep enable/layer-count setting. THE recurring project trap
([[dinghy-compose-write-scope-cancellation]] — 4 strikes). Route every write through a
`container.setBabystep*` intent method backed by `writeScope.launch`; RMW inside one `dataStore.edit`.

### Semantic tokens + fsSp type scale (no raw colors, no bare .sp)
**Source:** every composable here — e.g. `AppDrawer.kt` (`LocalTokens.current`, `fsSp(16f, t.fs)`),
`PrintStatusScreen.kt` `StatGrid` (`t.seriesColor(0/1)`).
**Apply to:** all new/modified UI. Nozzle/bed = `seriesColor(0)`/`seriesColor(1)` (accent/pool,
Phase-15.1 — NOT amber; the `03-print-status.png` amber is superseded, update artboard). Use the
`fsSp(baseSp, t.fs)` scale only ([[dinghy-font-sizes-too-small]]).

### Reused render primitives (do NOT re-roll)
**Source / Apply (all in `PrintStatusScreen.kt`):**
- `StopButton` (@828-849) — E-Stop tile, octagon glyph `ic_status_octagon` @843, tap+hold via
  `combinedClickable`. Exactly the staging-note EStop semantics. Reuse verbatim.
- `StatGrid` (@441-498) + `IconTwoRowCell` (@700) + `IconValueCell` (@732) — the framed stats list;
  extend rows (Applied-Z-offset), drop nothing.
- `PrintStatusFocus` (@350) — ring+thumb+%+filename; reuse for Printing, wrap (dim+pause) for Paused,
  variant for Terminal.
- `LastJobCard` (@509) — thumbnail-background hero treatment; candidate Terminal Focus hero material.
- `PrintStatusControlTile` (@761) + `controlColor` (@810) — gutter tiles; extend `controlColor` for
  the new intents.
- `ConfirmGuard` (designsystem) — Cancel + E-Stop tap guard (LAW; copy slots in UI-SPEC).
- `ProgressRing` (render) — single-series accent ring; do NOT animate the fill (Adreno-320 floor,
  SC-3 — the store already samples at 250ms; `PrintStatusHolder` adds NO second throttle).

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| (none — code) | — | — | Every net-new code file has a strong in-repo analog (this is a rework, not greenfield). |
| Babystep compress/expand vector glyphs (`res/drawable/*.xml`) | asset | — | New distinct silhouettes (icon-never-twice rule); no existing closer analog beyond the `ic_status_octagon` drawable convention. Author 2 distinct glyphs + the verbatim `contentDescription` contract (UI-SPEC Accessibility). |

> The host-load glance fallback (`machine.proc_stats`) has **no analog and is intentionally OUT OF
> SCOPE** — it is a separate Moonraker surface modeled in Phase 19, not P16 (RESEARCH §3 / Pitfall
> 4 / Open Question 3). If Matthew wants it in P16, flag as an explicit scope addition, not a silent
> inclusion.

---

## Metadata

**Analog search scope:** `ui/printstatus/`, `ui/temperature/`, `ui/shell/`, `ui/macros/`,
`command/`, `state/`, `net/`, `spool/`, `di/`.
**Files scanned (read in full or targeted):** PrintStatusControlModel, PrintStatusHolder,
PrintStatusScreen (targeted), CommandRegistry, PrinterCommands, PrinterStateReducer, PrinterState,
DeriveCapabilities, MoonrakerSession (handshake region), AppDrawer, TemperatureScreen (preset
region), MacroPrefs, AppContainer (writeScope region), SpoolmanModels (filament temps).
**Pattern extraction date:** 2026-06-06

---

## PATTERN MAPPING COMPLETE

**Phase:** 16 - Home / Print-Status Redesign
**Files classified:** 13 (4 net-new code, 6 modified code, 1 new prefs, 3 doc deliverables, + 1 asset)
**Analogs found:** 10 / 10 code files (every code file has a strong in-repo analog)

### Coverage
- Files with exact analog: 10
- Files with role-match analog: 0 (all exact — rework, not greenfield)
- Files with no analog: 1 asset (babystep glyphs) + intentionally-out-of-scope host-load

### Key Patterns Identified
- Pure, toolkit-agnostic state derivation is the project's spine discipline — the `PrintStatusMode`
  classifier copies `derivePrintStatusControls`' exact shape (pure fun of `PrinterState`, host-tested),
  with the ONE deliberate behavior change: Standby is always Standby (no terminal masquerade).
- Gcode commands route through `CommandRegistry` specs (factory + `all` list) backed by clamp/
  validate-before-format `PrinterCommands` builders — `testZ` is the babystep template; `cooldown`/
  const-gcode is the `SDCARD_RESET_FILE` template. A spec missing from `all` is dead.
- The 4 net-new data/command additions are all small extensions of already-walked/-subscribed seams:
  `homing_origin[2]` = one reducer line in the existing `gcode_move` block; the glance temp = one
  dynamic subscribe rule mirroring `heater_generic`; two command specs.
- Persistence MUST use the `AppContainer.writeScope` intent-helper pattern (MacroPrefs shape), never
  a composition scope — the project's #1 recurring trap.

### File Created
`.planning/phases/16-home-print-status-redesign/16-PATTERNS.md`

### Ready for Planning
Pattern mapping complete. Each new/modified file maps to a concrete analog with copy-from line
references. Planner can now reference analogs directly in PLAN.md action sections.
