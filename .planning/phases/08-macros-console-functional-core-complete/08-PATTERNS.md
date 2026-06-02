# Phase 8: Macros & Console — Functional-Core Complete - Pattern Map

**Mapped:** 2026-06-02
**Files analyzed:** 19 new + 5 modified (extracted from 08-CONTEXT `<code_context>` + 08-RESEARCH reuse map + 08-UI-SPEC)
**Analogs found:** 24 / 24 (every new file has a concrete in-repo analog; 1 explicit no-analog — see No Analog Found)

> All analog excerpts below are verbatim from the live tree under
> `app/src/main/java/works/mees/dinghy/`. Package root abbreviated as `…/dinghy/` in paths.
> The single biggest reuse is `ui/files/FileListView.kt` (Views-in-Compose scroll, D-05) — excerpted in full.

---

## File Classification

### Console surfaces

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `ui/console/ConsoleScreen.kt` | screen (Compose shell) | streaming (read-only) | `ui/files/FilesScreen.kt` | role+flow (Field-only scroll screen) |
| `ui/console/ConsoleListView.kt` | view (RecyclerView-in-AndroidView) | streaming append | `ui/files/FileListView.kt` | **exact** (3rd such surface, D-05) |
| `ui/console/ConsoleRowsAdapter.kt` | view adapter (ListAdapter) | streaming append | `ui/files/FileRowsAdapter.kt` | exact |
| `ui/console/ConsoleHolder.kt` | state holder (toolkit-agnostic) | streaming → StateFlow | `ui/files/FileBrowserHolder.kt` | role-match (holder) |
| `ui/console/ConsoleLine.kt` | model (headless data class) | transform | `state/Capabilities.kt` (plain immutable model) | role-match |
| `ui/console/ConsoleSeverity.kt` | pure classifier | transform | `state/DeriveCapabilities.kt` (pure derive) | role-match |
| `ui/console/ConsoleFilters.kt` | pure view-layer filter | transform | `state/DeriveCapabilities.kt` (pure, filter idiom) | role-match |
| `state/ConsoleScrollback.kt` (or `render/BoundedRing.kt`) | bounded object-typed ring | batch/evict | `render/RingBuffer.kt` (idiom only — type mismatch) | partial (see No Analog) |

### Macro surfaces

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `ui/macros/MacroParamParser.kt` | pure parser | transform | `command/PrinterCommands.kt` (pure, host-testable) | role-match |
| `ui/macros/MacroModels.kt` | model (data classes) | transform | `state/Capabilities.kt` | role-match |
| `ui/macros/MacroHolder.kt` | state holder | request-response → StateFlow | `ui/files/FileBrowserHolder.kt` | role-match |
| `ui/macros/MacroPrefs.kt` | persistence (DataStore) | CRUD (prefs) | `config/ConnectionStore.kt` / `theme/ThemePrefs.kt` | role-match (DataStore) |
| `ui/macros/BookmarkedMacrosScreen.kt` | screen (Compose) | request-response | `ui/files/FilesScreen.kt` (ScreenScaffold) | role-match |
| `ui/macros/SystemMacrosScreen.kt` | screen (Compose) | request-response | `ui/files/FilesScreen.kt` (scroll-Field list) | role-match |
| `ui/macros/MacroExecutionPopup.kt` | screen/overlay (Compose) | request-response | `designsystem/NumpadPage.kt` + `designsystem/ConfirmGuard.kt` | role-match |
| `command/MacroInvocation.kt` (sanitizer) | utility (security) | transform | `command/PrinterCommands.kt` (the invariant it must respect) | **exact** (same file owns the rule) |

### Wiring (modified files)

| Modified File | Role | Change | Analog (in-file precedent) |
|---------------|------|--------|----------------------------|
| `command/CommandRegistry.kt` | command registry | add `gcodeStore` `CommandSpec` + reuse `GCODE_SCRIPT` for macro run | `temperatureStore` spec (line 110), `setHeater` gcode spec (line 228) |
| `net/JsonRpc.kt` (`JsonRpcMethods`) | constants | add `GCODE_STORE = "server.gcode_store"` | `TEMPERATURE_STORE` (line 112) |
| `net/MoonrakerSession.kt` | session handshake | add `runCatching { gcode_store }` read in `runHandshake()` step 7 | the `temperature_store` + `configfile` reads (lines 344-362) |
| `state/PrinterStateStore.kt` | state store | add `setGcodeBackfill(...)` + a raw-console StateFlow seam | `setTemperatureBackfill` (line 165), `gcodeResponses` SharedFlow (line 57-59) |
| `ui/route/TopRoute.kt` (`Dest`) | nav enum | `Dest { … , Macros, Console }` | the existing `enum class Dest` (line 30) |
| `ui/shell/AppDrawer.kt` | nav surface | wire `Macros` tile `dest = Dest.Macros`; add `Console` tile (`symbol="terminal"`) | `DRAWER_TILES` greyed Macros entry (line 122) |
| `ui/shell/AppShell.kt` | shell host | add `Dest.Macros`/`Dest.Console` to `when(dest)`; **suppress drawer swipe** on Console (+ System list) | Files swipe-suppress `pointerInput(dest)` (lines 136-142) |

---

## Pattern Assignments

### `ui/console/ConsoleListView.kt` (view, streaming append) — THE LOAD-BEARING ANALOG

**Analog:** `ui/files/FileListView.kt` — copy VERBATIM. D-05 requires this exact pattern (the 3rd Views-in-Compose scroll surface). The three load-bearing pieces are `Modifier.clipToBounds()` on the `AndroidView`, `MATCH_PARENT` layout params, and `itemAnimator = null`.

**Full pattern** (`FileListView.kt:49-78`):
```kotlin
AndroidView(
    // Clip the embedded RecyclerView to its Compose bounds. AndroidView-hosted views draw in the
    // Android layer and are NOT clipped by sibling Compose layout, so rows scrolling past the top/
    // bottom edge would paint over the path chip and gutter without this.
    modifier = modifier.clipToBounds(),
    factory = { context ->
        RecyclerView(context).apply {
            // Pin to the AndroidView's measured bounds. Without explicit MATCH_PARENT params a
            // RecyclerView hosted in AndroidView can be measured UNSPECIFIED, grow to wrap ALL
            // rows, overlap the gutter, and never scroll. MATCH_PARENT forces the bounded height.
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(true)
            clipToPadding = false
            itemAnimator = null          // Adreno-320: NO animated insertion
            this.adapter = adapter
        }
    },
    update = { adapter.submitRows(...) },
)
```

**Palette-via-tokens pattern** (`FileListView.kt:35-43`) — the View side cannot read `LocalTokens`, so the Composable resolves tokens to `.toArgb()` ints and hands them down as a palette object. Console rows need the SAME bridge for severity colors:
```kotlin
val palette = FileRowPalette(
    background = t.surface.toArgb(),
    selectedBackground = t.surface2.toArgb(),
    outline = t.hair.toArgb(),
    text = t.text.toArgb(),
    textSecondary = t.text2.toArgb(),
    textMuted = t.text3.toArgb(),
)
```
> For Console, add `error = t.stop.toArgb()`, `warning = t.heat.toArgb()`, `normal = t.text.toArgb()`, `success = t.go.toArgb()` to the palette and pick per `ConsoleSeverity` in the ViewHolder bind. Use `GeistMono` (UI-SPEC: mandatory tabular for console lines).

**Adapter analog:** `ui/files/FileRowsAdapter.kt` (a `ListAdapter` + DiffUtil) → `ConsoleRowsAdapter.kt`. For stick-to-bottom, append + `scrollToPosition(itemCount-1)` only when already at bottom (planner's call per D-02).

---

### `ui/console/ConsoleScreen.kt` (screen, streaming) — Field-only ScreenScaffold

**Analog:** `ui/files/FilesScreen.kt` — specifically the **pinned-height host** that wraps `FileListView` (`FilesScreen.kt:195-205`):
```kotlin
BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
    // Pin the RecyclerView to an EXACT height. A real Android View hosted via AndroidView
    // over-measures (wraps all rows) when handed a loose height, then composites OVER its
    // Compose neighbors — that's why the list bled over the path chip and the gutter.
    FileListView(
        directory = state.directory,
        modifier = Modifier.fillMaxWidth().height(maxHeight),   // ← .height(maxHeight) is load-bearing
    )
}
```

**Scaffold shape:** `ScreenScaffold(focus = null, field = { … }, gutter = { … })` — Console is Field-only (UI-SPEC: "Focus is omitted, freed height goes to scrollback"). `ScreenScaffold` signature (`designsystem/layout/ScreenScaffold.kt:49-61`): `focus`/`field`/`gutter` are nullable `@Composable` slots — pass `focus = null`.

**Gutter** = the 3 filter toggles + a green **Back** control (UI-SPEC). Copy the gutter `Row` + weighted controls + `intentColor(...)` mapping from `FilesScreen.kt:118-137` and `FilesScreen.kt:418-424`:
```kotlin
private fun intentColor(intent: Intent, t: ThemeTokens): Color = when (intent) {
    Intent.Neutral -> t.outline
    Intent.Accent  -> t.accentLine   // active filter toggle edge
    Intent.Warn    -> t.heat
    Intent.Danger  -> t.stop
    Intent.Go      -> t.go           // Back (safe dismiss — green, per safety doctrine)
}
```

**State collection** (`FilesScreen.kt:73`): `val state by holder.state.collectAsStateWithLifecycle()`.

---

### `ui/console/ConsoleHolder.kt` (state holder, streaming → StateFlow)

**Analog:** `ui/files/FileBrowserHolder.kt` — the canonical toolkit-agnostic holder shape.

**Holder skeleton** (`FileBrowserHolder.kt:32-53`):
```kotlin
class FileBrowserHolder(
    scope: CoroutineScope,
    private val client: FileBrowserClient,
    private val printerState: StateFlow<PrinterState>,
) {
    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    init {
        scope.launch {
            printerState.collect { printer -> /* react to spine state */ }
        }
    }
}
```

**For ConsoleHolder**, collect the ALREADY-WIRED raw line stream in `init` (no new transport — RESEARCH §2):
```kotlin
// store.gcodeResponses is the consumer-facing SharedFlow<String> (PrinterStateStore.kt:57-59)
init {
    scope.launch { store.gcodeResponses.collect { line -> appendRaw(line) } }   // RAW, upstream of filter (D-04)
    scope.launch { store.consoleBackfill.collect { snapshot -> replaceRaw(snapshot) } }  // backfill replace
}
```
> D-04 (LOAD-BEARING): the holder stores the RAW prefixed line; filtering is the VIEW layer's job (`ConsoleScreen` applies `ConsoleFilters` on render). Never filter before the ring.

**Severity classify at append** uses `ConsoleSeverity.classify(...)` (pure). **Backfill replace-on-(re)connect** = Mainsail-parity Option A (RESEARCH §1): replace the whole ring with the `server.gcode_store` snapshot.

---

### `state/ConsoleScrollback.kt` — bounded OBJECT ring (NOT RingBuffer)

**Analog:** `render/RingBuffer.kt` — copy the **idiom only**, not the type. `RingBuffer` is `FloatArray`-backed and physically cannot hold `ConsoleLine` (confirmed `RingBuffer.kt:30`: `private val data = FloatArray(capacity)`). RESEARCH Pitfall 1 verified.

**Idiom to copy** (`RingBuffer.kt:41-71`) — `@Synchronized push` with O(1) eviction + defensive `snapshot()`:
```kotlin
@Synchronized
fun push(value: Float) {              // ← change to push(line: ConsoleLine)
    if (count < capacity) { data[(head + count) % capacity] = value; count++ }
    else { data[head] = value; head = (head + 1) % capacity }   // overwrite oldest, advance head
}
@Synchronized
fun snapshot(): FloatArray { … defensive copy oldest→newest … }
```
Simplest correct form per RESEARCH: a `@Synchronized` `ArrayDeque<ConsoleLine>` capped at ~1000 with `removeFirst()` over cap. Default cap aligns 1:1 with Moonraker's `gcode_store_size` default of 1000.

---

### `ui/console/ConsoleSeverity.kt` + `ConsoleFilters.kt` (pure transforms)

**Analog:** `state/DeriveCapabilities.kt` — the project's pure-function discipline (no I/O, no Compose, host-testable). `ConsoleSeverity.classify(raw)` and `ConsoleFilters.apply(lines, flags)` mirror `deriveCapabilities(...)`.

**Exact classifier** (verbatim from RESEARCH Code Examples, mirrors Mainsail):
```kotlin
enum class ConsoleSeverity { ERROR, WARNING, NORMAL, ACTION, DEBUG }
fun classify(rawMessage: String): ConsoleSeverity = when {
    rawMessage.startsWith("!! ")        -> ConsoleSeverity.ERROR     // → t.stop (red)
    rawMessage.startsWith("// action:") -> ConsoleSeverity.ACTION    // dimmed; Phase-12 hook (keep raw)
    rawMessage.startsWith("// debug:")  -> ConsoleSeverity.DEBUG     // dimmed
    rawMessage.startsWith("// ")        -> ConsoleSeverity.WARNING   // → t.heat (amber)
    else                                -> ConsoleSeverity.NORMAL    // → t.text (or t.go for ok)
}
```

**Exact filter regexes** (verbatim from fork @76fcbd2, all default OFF, applied at VIEW layer only):
```kotlin
val HIDE_TEMPERATURES = Regex("""^(?:ok\s+)?(B|C|T\d*):""")
val HIDE_TIMELAPSE = listOf(
    Regex("""^_TIMELAPSE_NEW_FRAME"""), Regex("""^TIMELAPSE_TAKE_FRAME"""),
    Regex("""^TIMELAPSE_RENDER"""), Regex("""^_SET_TIMELAPSE_SETUP"""),
    Regex("""^HYPERLAPSE ACTION="""), Regex("""^SET_GCODE_VARIABLE MACRO=TIMELAPSE_"""),
)
val HIDE_PROMPT_COMMANDS = Regex("""^(?:// )?action:prompt""")
```
Tests follow the `deriveCapabilities` host-test precedent (no hardware). RESEARCH Wave 0: `ConsoleSeverityTest`, `ConsoleFiltersTest`.

---

### `ui/macros/MacroParamParser.kt` (pure parser, transform)

**Analog:** `command/PrinterCommands.kt` — same purity contract (`PrinterCommands.kt:7-23`: "PURE … NO I/O, NO coroutines, NO Compose; same input → same output; fully host-testable").

**Exact algorithm** (verbatim from RESEARCH Code Examples, Mainsail `getMacroParams`):
```kotlin
val PARAM_REGEX = Regex(
    """\{%?.*?params\.([A-Za-z_0-9]+)(?:\|(int|string|double))?(?:\|default\('?"?(.*?)"?'?\))?(?:\|(int|string))?.*?%?\}"""
)
val PARAM_IN_REGEX = Regex("""\{%?.*?if.*?'([A-Za-z_0-9]+)' (?:not )?in params.*?%?\}""")

fun parseMacroParams(gcodeBody: String): List<MacroParam> {
    val out = linkedMapOf<String, MacroParam>()          // first-seen order, dedup by name
    for (m in PARAM_REGEX.findAll(gcodeBody)) {
        val name = m.groupValues[1]
        val type = m.groupValues[2].ifEmpty { m.groupValues[4] }.ifEmpty { null }
        val default = m.groupValues[3].ifEmpty { null }
        out.putIfAbsent(name, MacroParam(name, type, default))
    }
    for (m in PARAM_IN_REGEX.findAll(gcodeBody)) out.putIfAbsent(m.groupValues[1], MacroParam(m.groupValues[1], null, null))
    return out.values.toList()
}
```
> `type` drives the entry widget (D-10): `int`/`double` → `NumpadPage`; `string`/null → keyboard popup. Heuristic — never block Execute on a missed param.

**Test fixture discipline (Pitfall 6 / mock-vs-reality):** fixtures MUST come from a REAL probed macro body (`curl …/printer/objects/query?configfile` on Ender 5 `192.168.1.120:7125`), not invented strings. Record the shape in `docs/moonraker-capabilities.md` first.

---

### `command/MacroInvocation.kt` (sanitizer, security — V5 / block_on:high)

**Analog:** `command/PrinterCommands.kt` — this sanitizer is the *explicit exception* to the invariant that file states (`PrinterCommands.kt:14-18`):
```
SECURITY (ASVS V5, T-05-02-T): … No user-supplied string is ever concatenated into a
script. Callers feed these builders ONLY bounded scrubber/selector input.
```
Macro string params (D-10 alpha keyboard) break that invariant, so the sanitizer must restore it. Build `MACRO KEY="<sanitized>"`:
- strip `\r` / `\n` / control chars (a newline is the injection — `name=val\nM112`);
- reject (or quote) anything that after sanitization still contains a line break or a leading gcode token;
- numeric params: clamp via `NumpadPage` `range` BEFORE formatting (same as every other control).
- assemble final line and dispatch via `PrinterCommands.scriptParams(gcode)` (`PrinterCommands.kt:148-149`).

**Wrap-into-script helper to reuse** (`PrinterCommands.kt:148-149`):
```kotlin
fun scriptParams(gcode: String): JsonElement = buildJsonObject { put("script", gcode) }
```
RESEARCH Wave 0 test: `MacroInvocationTest` — newline / second-command injection MUST be neutralized.

---

### `ui/macros/MacroExecutionPopup.kt` (overlay screen, request-response)

**Analogs:** `designsystem/NumpadPage.kt` (numeric param entry) + `designsystem/ConfirmGuard.kt` (overlay shape) + `FilesScreen.kt` ConfirmGuard usage.

**NumpadPage signature to call** (`designsystem/NumpadPage.kt:71-78`):
```kotlin
@Composable
fun NumpadPage(
    label: String,
    initial: Double,
    range: ClosedFloatingPointRange<Double>,   // ← clamp source (security: numeric clamp before format)
    unit: String = "",
    onCommit: (Double) -> Unit,
    onCancel: () -> Unit,
)
```
> Numeric macro param → route to `NumpadPage` with `initial = parsedDefault`, a sane `range`. String param → system keyboard (the ONE sanctioned alpha-keyboard site, D-10) → feed through `MacroInvocation` sanitizer.

**Popup IS the action gate (D-08):** do NOT layer a `ConfirmGuard` on top. Build the overlay as its own full-screen surface (`--r-card` 22px) with a gutter `Execute` (`Intent.Accent` → `t.accentLine`) + `Cancel` (`Intent.Go` → `t.go`). Use `FilesScreen.kt:386-416` `FileActionControl` as the gutter-control template (64dp min, 2px outline, intent color).

**Execute → dispatch** (RESEARCH Pattern 2):
```kotlin
val gcode = MacroInvocation.build(macroName, paramValues)   // sanitized
dispatcher.dispatch(
    key = "macro_$macroName",                               // per-macro busy key (PRIM-05)
    method = JsonRpcMethods.GCODE_SCRIPT,
    params = PrinterCommands.scriptParams(gcode),
)
```

---

### `ui/macros/MacroHolder.kt` + `MacroPrefs.kt`

**Holder analog:** `ui/files/FileBrowserHolder.kt` (StateFlow holder). Combine `Capabilities.macros` (already derived) + `MacroPrefs` (bookmarks/reveal) + parsed bodies.

**Capability gating** (`state/Capabilities.kt:27,49`): macros are `Capabilities.macros` (the `NAME` of `gcode_macro NAME`); gate the whole feature on `macros.isNotEmpty()`. Underscore default-hide is a render-time filter (`name.startsWith("_")`), reveal toggle in `MacroPrefs`. `hasMacroIgnoreCase(...)` handles Moonraker's lowercase quirk.

**Prefs analog:** `config/ConnectionStore.kt` / `theme/ThemePrefs.kt` (DataStore Preferences). New separate `.preferences_pb` for `macros` (bookmarks `Set<String>`, `revealHidden: Boolean`). RESEARCH Wave 0 test: `MacroPrefsTest` (round-trip).

---

### Handshake read: `gcode_store` backfill (modified `MoonrakerSession.kt`)

**Analog:** the `temperature_store` + `configfile` one-shot reads ALREADY in `runHandshake()` step 7 (`MoonrakerSession.kt:344-362`). Copy the `runCatching` best-effort shape EXACTLY:
```kotlin
runCatching {
    val storeResult = rpc.request(CommandRegistry.temperatureStore, Unit)
    val backfill = parseTemperatureStore(storeResult.jsonObject, capabilities.heaters.toSet())
    store.setTemperatureBackfill(backfill)
}
```
**New read to add (same step 7):**
```kotlin
runCatching {
    val storeResult = rpc.request(CommandRegistry.gcodeStore, GcodeStoreArgs(count = 1000))
    val lines = parseGcodeStore(storeResult.jsonObject)   // List<ConsoleLine>, raw, prefix-preserved
    store.setGcodeBackfill(lines)                          // REPLACE (Mainsail parity, D-02)
}
```
> Placing it INSIDE `runHandshake()` means it auto-reruns on every reconnect AND on `notify_klippy_ready` (re-handshake at `MoonrakerSession.kt:222`) — satisfies D-02 "don't drop disconnect-window lines" (Pitfall 4).
> **Pitfall 3:** the `configfile` query at `MoonrakerSession.kt:355` already runs — EXTEND that single read to also pull `gcode_macro *` sections rather than issuing a 2nd `configfile` query.

**Null-safe wire walk to mirror** (`MoonrakerSession.kt:355-361` + `:389-392`): `objectOrNull(key)` / `floatOrNullAt(key)` + `runCatching{}.getOrNull()`. `parseGcodeStore` follows the same "bad field skipped, never fatal" rule (`parseObjectsList` at `:365-374` is the per-element-guard template).

---

### Registry + methods (modified `CommandRegistry.kt`, `JsonRpc.kt`)

**Analog:** `temperatureStore` spec (`CommandRegistry.kt:110-116`):
```kotlin
val temperatureStore: CommandSpec<Unit> = jsonRpc(
    catalogId = "MR-server.temperature_store",
    method = JsonRpcMethods.TEMPERATURE_STORE,
    key = { "temperature_store" },
    params = { null },
    availability = AvailabilityPredicate.ComponentPresent("history"),
)
```
**Add** a `gcodeStore: CommandSpec<GcodeStoreArgs>` (params `{ "count": <int> }`) the same way + a `GCODE_STORE = "server.gcode_store"` const beside `TEMPERATURE_STORE` (`JsonRpc.kt:112`), and append `gcodeStore` to `CommandRegistry.all` (line 326).

**Macro run needs NO new spec** — reuse the `gcode(...)` factory pattern that wraps `GCODE_SCRIPT` (`CommandRegistry.kt:385-398`); the macro sanitizer produces the script string. (`setHeater` at line 228 is the per-arg gcode-spec template.)

---

### Nav wiring (modified `TopRoute.kt`, `AppDrawer.kt`, `AppShell.kt`)

**Dest enum** (`TopRoute.kt:30`): `enum class Dest { PrintStatus, Temperature, Move, Extrude, Files, Settings }` → add `Macros, Console`. (Comment at `:30` notes "Extra panels are a one-line addition" — this is that.)

**Drawer tile** (`AppDrawer.kt:122`) — flip the greyed Macros entry live + add Console:
```kotlin
DrawerTileSpec(label = "Macros",  symbol = "code",     dest = Dest.Macros),    // was dest = null
DrawerTileSpec(label = "Console", symbol = "terminal", dest = Dest.Console),   // new — UNUSED glyph (icon-no-repeat law)
```
> `terminal` is unused on the current drawer (existing: monitoring/open_with/thermostat/folder/output_circle/code/cable/settings/power_settings_new) — RESEARCH Open Q1. The Macros tile opens the **Bookmarked launcher** (D-07).

**Shell `when(dest)`** (`AppShell.kt:145-176`) — add `Dest.Macros -> BookmarkedMacrosScreen(...)` and `Dest.Console -> ConsoleScreen(...)`, each `onBack = { goBack() }` per the existing pattern.

**Drawer-swipe suppression (D-05 — LOAD-BEARING)** — `AppShell.kt:136-142`:
```kotlin
.pointerInput(dest) {
    if (dest != Dest.Files) {          // ← extend to: dest !in setOf(Dest.Files, Dest.Console, Dest.Macros)
        detectVerticalDragGestures { _, dragAmount ->
            if (dragAmount < -SWIPE_UP_THRESHOLD_PX) drawerOpen = true
        }
    }
}
```
Suppress on Console AND on the System macro list whenever it is finger-scrollable (UI-SPEC). Keep an explicit green **Back** in each gutter as the exit.

---

## Shared Patterns

### Toolkit-agnostic StateFlow holder
**Source:** `ui/files/FileBrowserHolder.kt:32-53`
**Apply to:** `ConsoleHolder`, `MacroHolder`
`private val _state = MutableStateFlow(X)` + `val state: StateFlow<X> = _state.asStateFlow()`; collect spine flows in `init { scope.launch { … } }`; mutate via `_state.update { it.copy(...) }`. Screens read with `holder.state.collectAsStateWithLifecycle()` (`FilesScreen.kt:73`).

### Pure, host-testable function (no I/O / no Compose)
**Source:** `state/DeriveCapabilities.kt`, `command/PrinterCommands.kt:7-23`
**Apply to:** `ConsoleSeverity.classify`, `ConsoleFilters.apply`, `MacroParamParser.parseMacroParams`, `MacroInvocation.build`
Same input → same output; tested off-hardware (Wave 0 unit tests).

### Best-effort one-shot handshake read
**Source:** `net/MoonrakerSession.kt:344-362`
**Apply to:** the `gcode_store` backfill read
Each read wrapped in its OWN `runCatching` so a missing endpoint never breaks `Connected`; result lands on a `PrinterStateStore` StateFlow setter (`setTemperatureBackfill` template, `PrinterStateStore.kt:165`); inside `runHandshake()` so it reruns on reconnect/`klippy_ready`.

### Command dispatch (pending/debounce/timeout/redacted-failure)
**Source:** `command/CommandDispatcher.kt:108-157`
**Apply to:** macro Execute
`dispatcher.dispatch(key, JsonRpcMethods.GCODE_SCRIPT, scriptParams(gcode))`. Gives debounce, in-flight busy (`inFlight: StateFlow<Set<String>>` → disable Execute), 120s gcode timeout, and `DispatchEvent.Failure` (printer rejection text) → `SeverityToast`. **Failure messages are composed from `method`/`key` only, NEVER `e.message`** (`CommandDispatcher.kt:135-149`) — macro failures inherit this redaction (V7).

### View-side token bridge (Views can't read LocalTokens)
**Source:** `ui/files/FileListView.kt:35-47`
**Apply to:** `ConsoleListView` severity colors
Composable resolves `t.stop/heat/text/go` → `.toArgb()` ints into a palette object handed to the adapter; ViewHolder picks per `ConsoleSeverity`.

### DataStore preferences
**Source:** `config/ConnectionStore.kt`, `theme/ThemePrefs.kt`
**Apply to:** `MacroPrefs` (separate `.preferences_pb`; bookmarks `Set<String>` + `revealHidden`).

---

## No Analog Found

| File | Role | Data Flow | Reason / Resolution |
|------|------|-----------|---------------------|
| `state/ConsoleScrollback.kt` (object-typed bounded ring) | bounded collection | batch/evict | `render/RingBuffer.kt` is `FloatArray`-only (`RingBuffer.kt:30`) — CANNOT hold `ConsoleLine`. **Build fresh** copying only the `@Synchronized push`/`snapshot` O(1)-eviction idiom (`RingBuffer.kt:41-71`), object-typed (a capped `ArrayDeque<ConsoleLine>` is simplest). Confirmed by RESEARCH Pitfall 1. |

> Everything else has a concrete in-repo analog. `MacroExecutionPopup` is a *composition* of two existing primitives (`NumpadPage` + an overlay surface) rather than a single-file clone — noted as role-match, not exact.

---

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/{ui,command,net,state,render,designsystem,config,theme}/`
**Files scanned (read in full or targeted):** `FileListView.kt`, `FilesScreen.kt`, `FileBrowserHolder.kt`, `CommandRegistry.kt`, `CommandDispatcher.kt`, `PrinterCommands.kt`, `AppShell.kt`, `AppDrawer.kt`, `TopRoute.kt`, `RingBuffer.kt`, `Capabilities.kt`, `DeriveCapabilities.kt`, `MoonrakerSession.kt` (handshake + walkers), `PrinterStateStore.kt` (gcodeResponses + setters), `JsonRpcClient.kt` (gcodeLine + request), `JsonRpc.kt` (methods), `NumpadPage.kt`, `ScreenScaffold.kt`, `ConfirmGuard.kt` (signatures).
**Confirmed:** the live `notify_gcode_response` path IS already plumbed end-to-end (`JsonRpcClient._gcodeResponses` `:68` → `MoonrakerSession` `:228` → `PrinterStateStore.gcodeResponses` `:57-59`) — Console is a pure consumer, no new transport.
**Pattern extraction date:** 2026-06-02
