# Phase 12: Macro Prompt Protocol - Pattern Map

**Mapped:** 2026-06-04
**Files analyzed:** 18 new + 3 modified
**Analogs found:** 21 / 21 (every new file has a strong in-repo analog — this phase is reuse-heavy by design)

> All paths are absolute-from-repo-root under `app/src/main/java/works/mees/dinghy/` (main) or
> `app/src/test/java/works/mees/dinghy/` (test). Build is Windows-side:
> `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>"` (`./gradlew` does NOT run from WSL).
> The package root is `works.mees.dinghy.*`; the new pure engine lives in a new `prompt/` package.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `prompt/ParseAction.kt` (NEW) | utility (pure parser) | transform | `ui/spool/scan/QrPayloadParser.kt`, `ui/console/ConsoleSeverity.kt` | exact (tolerant total-function parser) |
| `prompt/PromptButton.kt` / `PromptImage.kt` / `PromptStyle.kt` sub-parsers (NEW, may fold into ParseAction) | utility (pure parser) | transform | `ui/spool/scan/QrPayloadParser.kt` | exact |
| `prompt/PromptReducer.kt` (NEW) | service (pure state machine) | transform / event-driven | (no exact reducer; closest behavioral) `calibration/ProbeCalibrateHolder.buildVm` derive idiom | role-match (pure derive) |
| `prompt/PromptModel.kt` (NEW — `PromptStateData`, `PromptView`, `PromptItem`, etc.) | model | — | `calibration/ProbeCalibrateHolder.kt` `ProbeCalibrateVm` data class; `state/PrinterState.kt` sealed/enum shapes | exact (immutable data class / sealed) |
| `prompt/Markup.kt` (NEW — AST + `markupToPlainText`) | utility (pure parser) | transform | `ui/console/ConsoleSeverity.kt` (total classify) | role-match (pure tolerant parser) |
| `prompt/PromptEngine.kt` (NEW — spine holder) | provider / store (state holder) | event-driven (stream→reduce) | `calibration/ProbeCalibrateHolder.kt` | **exact** (gcodeResponses collect + events fold) |
| `ui/prompt/PromptDialog.kt` (NEW — overlay) | component | request-response | `ui/macros/MacroExecutionPopup.kt` | **exact** (full-screen overlay) |
| `ui/prompt/PromptContentButton.kt` (NEW, thin) | component | request-response | `MacroExecutionPopup.PopupControl` + `designsystem/control/OutlinedControl.kt` | exact |
| `ui/prompt/PromptFooterBar.kt` + close control (NEW, thin) | component | request-response | `MacroExecutionPopup` gutter `Row` (lines 254-275) | exact |
| `ui/prompt/PromptMarkupText.kt` (NEW — AnnotatedString builder) | component | transform | (no direct analog) `theme/` tokens + `fsSp`; Spoolman hex-color precedent | role-match (novel render piece) |
| `ui/prompt/PromptImageItem.kt` (NEW, thin) | component | file-I/O (image decode) | Phase-10 webcam / Files Coil `AsyncImage` usage | role-match |
| `ui/prompt/PromptRowCells.kt` (NEW, thin) | component | — | `MacroExecutionPopup` weighted `Row` (lines 255-275) | role-match |
| `app/src/test/resources/prompt/fixtures.json` (NEW — committed corpus copy) | test fixture | — | `app/src/test/resources/golden/*.json` | exact |
| `prompt/PromptFixtureTest.kt` (NEW — 26-fixture gate) | test | — | `FixtureSanityTest.kt` + `net/GoldenFixtures.kt` | exact (loop-driven fixture test) |
| `prompt/PromptReducerTest.kt` / `PromptMarkupTest.kt` / `PromptParseTest.kt` (NEW) | test | — | `ConsoleSeverityTest` / `QrPayloadParserTest` idiom | exact |
| **MODIFY** `ui/shell/AppShell.kt` (overlay hoist + holder construct + drawer-suppress) | shell | — | self (existing `MacroExecutionPopup` / `nav.scanActive` overlays) | exact |
| **MODIFY** `di/AppContainer.kt` (engine construction seam, if spine-owned) | config | — | self (existing holder construction) | exact |
| **MODIFY** `docs/ui_design/CLAUDE.md` + `THEMING.md` (D-03 hex carve-out doc) | doc | — | existing carve-out wording (Spoolman spool colors) | n/a |

---

## Pattern Assignments

### `prompt/ParseAction.kt` (utility, transform) — the tolerant line parser

**Analog:** `ui/spool/scan/QrPayloadParser.kt` (allow-list, never-throws) + `ui/console/ConsoleSeverity.kt` (prefix classify, order matters).

**Tolerant-parser total-function shape to replicate** (`QrPayloadParser.kt:32-80`): a `sealed interface` result type, prefix consts, a single pure entry function that returns a result for ANY input and never throws. Replicate as `parseAction(line: String): PromptEvent?` returning `null` for non-prompt / unknown / empty-label lines (the JS `parse-action.ts` contract).

```kotlin
// QrPayloadParser.kt:43-65 — the const-prefix + startsWith + total-return idiom to mirror:
private const val SPOOL_URI_PREFIX = "web+spoolman:s-"
fun parseSpoolId(payload: String): SpoolQrResult {
    val trimmed = payload.trim()
    val lower = trimmed.lowercase()
    if (lower.startsWith(SPOOL_URI_PREFIX)) {
        val idPart = trimmed.substring(SPOOL_URI_PREFIX.length)
        return idPart.toIntOrNull()?.let { SpoolQrResult.Spool(it) } ?: SpoolQrResult.NotASpoolCode
    }
    // ...
    return SpoolQrResult.NotASpoolCode   // TOTAL: any input → a result, never throws
}
```

**Prefix + order-matters classify** (`ConsoleSeverity.kt:41-47`) — the parse layer keys on `"// action:prompt_"` exactly as the console classifier already does (more-specific before generic):

```kotlin
fun classify(rawMessage: String): ConsoleSeverity = when {
    rawMessage.startsWith("!! ") -> ERROR
    rawMessage.startsWith("// action:") -> ACTION   // ← the SAME prefix the prompt engine keys on
    rawMessage.startsWith("// debug:") -> DEBUG
    rawMessage.startsWith("// ") -> WARNING
    else -> NORMAL
}
```

**Load-bearing fact (from RESEARCH):** `store.gcodeResponses` emits the verbatim line WITH the `// ` retained, so `PREFIX = "// action:prompt_"` matches as-is — DO NOT strip `// ` (mock-vs-reality trap, Pitfall 2). Sub-parsers `parseButtonFields` / `parseImageScale` / `isValidImagePath` / `normalizeStyle` follow the same const+regex+total-return shape (see RESEARCH §Sub-parsers for verbatim rules). `isValidImagePath` is the V5 path-traversal allow-list and is a direct sibling of `QrPayloadParser`'s "id carrier, not a navigation target" discipline.

---

### `prompt/PromptModel.kt` + `prompt/PromptReducer.kt` (model + pure state machine, transform)

**Analog:** `calibration/ProbeCalibrateHolder.kt` — its `ProbeCalibrateVm` data class (lines 231-242) and `buildVm` pure-derive (lines 162-217) are the closest in-repo "internal-state → projected-view" idiom.

**Immutable model shape** (`ProbeCalibrateHolder.kt:231-242`):

```kotlin
data class ProbeCalibrateVm(
    val state: ProbePageState = ProbePageState.Idle,
    val zPosition: Double? = null,
    // ... all immutable, defaulted, no mutable collections
)
```

Replicate for `PromptView` (the conformance shape — **EXACTLY 6 keys**: `visible, title, targets, size, items, footer_buttons` — see RESEARCH §view projection; no internal field may leak) and `PromptStateData` (the richer internal machine). Use `data class` + `List` (never `MutableList`) so the immutability boundary is free (RESEARCH §Immutability boundary). Lifecycle/size/align as `enum`/`sealed` mirroring `state/PrinterState.kt:227-232` (`sealed interface ConnectionState { data object Connecting … }`).

**Reducer = pure function**, NOT a holder method: `fun reduce(state: PromptStateData, event: PromptEvent): PromptStateData`. The per-event semantics are fully specified in RESEARCH §"The Reducer State Machine" — **port the JS `reducer.ts` verbatim; do not improvise** (Pitfall 1). `ProbeCalibrateHolder.buildVm` is the *style* reference (pure, exhaustive `when`, returns a fresh immutable value), not the logic.

---

### `prompt/Markup.kt` (utility, transform) — AST + plain-text

**Analog:** `ui/console/ConsoleSeverity.kt` (pure, total, host-tested, no Compose).

Keep the AST builder + `markupToPlainText` in this PURE layer (no Compose import) so it is host-unit-testable; the `AnnotatedString` translation lives separately in `ui/prompt/PromptMarkupText.kt` (RESEARCH §PromptMarkup recommendation). Grammar rules (case-sensitive `<b>/<i>/<u>`, `<color:#hex>` 6-hex regex, `<size:...>` case-insensitive value, entity decode once) are verbatim in RESEARCH §PromptMarkup Grammar.

---

### `prompt/PromptEngine.kt` (state holder, event-driven) — THE wiring centerpiece

**Analog:** `calibration/ProbeCalibrateHolder.kt` — **exact template.** Same ctor shape `(scope, store, events?)`, same `MutableStateFlow` exposed as read-only `StateFlow`, same `init { scope.launch { … collect } }`, NO Compose annotations (host-testable).

**The gcode-stream subscribe** (`ProbeCalibrateHolder.kt:104-108`) — collect the un-throttled raw stream, run each line through the pure parser, fold into state:

```kotlin
scope.launch {
    store.gcodeResponses.collect { line ->
        parseZPosition(line)?.let { _bracket.value = it }   // ← prompt: parseAction(line)?.let { state = reduce(state, it) }
    }
}
```

**The dispatcher-Failure fold** (`ProbeCalibrateHolder.kt:110-125`) — fold `events: SharedFlow<DispatchEvent>` for THIS feature's keys into the surfaced error/toast (mirror the `PROBE_PAGE_KEYS` set with prompt button keys `prompt:<epoch>:<index>` / `prompt:close`):

```kotlin
if (events != null) {
    scope.launch {
        events.collect { event ->
            if (event is DispatchEvent.Failure && event.key in PROBE_PAGE_KEYS) { latestError = event.message; /* rebuild */ }
        }
    }
}
```

**Disconnect (D-10) — NEW collect, LOCAL close, NO `prompt_end` dispatch** (the reducer disconnect branch is purely local; the holder must NOT dispatch — Pitfall 6). Source: `store.printerState.map { it.connection }.distinctUntilChanged()`; `ConnectionState` enum at `state/PrinterState.kt:227-232` (`Connecting/Syncing/Connected/Disconnected/Error`). Trigger on `Disconnected`/`Error` only, NOT `Syncing`/`Connecting` (Assumption A3; coordinate with Phase-13).

**Subscribe seam confirmed:** `store.gcodeResponses` is fed by `PrinterStateStore.onGcodeLine` (`state/PrinterStateStore.kt:193-196`, `tryEmit`, un-throttled) — the engine and the console are independent subscribers; the engine is never starved (D-13).

---

### `ui/prompt/PromptDialog.kt` (component, request-response) — the full-screen overlay

**Analog:** `ui/macros/MacroExecutionPopup.kt` — **exact template** (D-05 explicitly names it).

**The overlay-hoist shape** (`MacroExecutionPopup.kt:183-198`) — opaque full-screen `Box(t.bg)` + `Column(header / weighted-scrollable-field / gutter)`:

```kotlin
Box(modifier.fillMaxSize().background(t.bg)) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "Run ${macro.name}", color = t.text, fontFamily = GeistMono,
             fontWeight = FontWeight.SemiBold, fontSize = fsSp(28f, t.fs).sp,   // ← title 28sp; prompt uses Geist (author text), NOT Mono (UI-SPEC)
             maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), ...) { /* Field: content items */ }
        // toast slot (SeverityToast) then gutter Row of buttons
    }
}
```

**Title:** UI-SPEC overrides the analog — use `fontFamily = Geist` (author text, not a filename) at `fsSp(28f, t.fs).sp`, `maxLines = 3`.
**In-flight + failure toasts** (`MacroExecutionPopup.kt:243-252`): same `SeverityToast(Severity.Error/Info, msg, Modifier.fillMaxWidth())` slots.
**Re-entrancy:** key the dialog/recomposition on `promptEpoch` so a replace forces clean recompose (Pitfall 3).

---

### `ui/prompt/PromptContentButton.kt` + `PromptFooterBar.kt` + close control (components, request-response)

**Analog:** `MacroExecutionPopup.PopupControl` (lines 322-358) + `designsystem/control/OutlinedControl.kt`.

**The 64dp outlined intent-colored control** (`MacroExecutionPopup.kt:322-358`):

```kotlin
@Composable
private fun PopupControl(label: String, onClick: () -> Unit, intent: Intent, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val outline = intentColor(intent, t)
    val base = modifier.heightIn(min = 64.dp).clip(shape)
        .border(BorderStroke(2.dp, if (enabled) outline else t.hair), shape)
        .background(if (enabled) Color.Transparent else t.surface).padding(horizontal = 12.dp, vertical = 18.dp)
    // ... clickable when enabled; centered Text label at fsSp(18f, t.fs)
}
private fun intentColor(intent: Intent, t: ThemeTokens): Color = when (intent) {
    Intent.Neutral -> t.outline; Intent.Accent -> t.accentLine; Intent.Warn -> t.heat; Intent.Danger -> t.stop; Intent.Go -> t.go
}
```

**The 6 protocol styles → tokens** (UI-SPEC §Color): `Intent` stays 5; resolve `info` via a SEPARATE small `style→color` map to `t.accent2` (do NOT add a 6th `Intent` member). Map: primary→`accentLine`, secondary→`outline`, info→`accent2`, warning→`heat`, error→`stop`, success→`go`.

**Footer bar** = the gutter weighted `Row` (`MacroExecutionPopup.kt:254-275`): `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(...))` with each cell `Modifier.weight(1f)`. The close control is an `accentLine` outlined control (`close` MaterialSymbol + "Close").

**Button press → dispatch** (`MacroExecutionPopup.kt:158-163`) — the dispatcher call shape to replicate:

```kotlin
dispatcher.dispatch(
    key = busyKey,                              // ← prompt: stable "prompt:<epoch>:<index>" / "prompt:close" (Pitfall 4)
    method = JsonRpcMethods.GCODE_SCRIPT,
    params = PrinterCommands.scriptParams(gcode),
)
```

Close control dispatches `scriptParams("""RESPOND TYPE=command MSG="action:prompt_end"""")` — NOT a local teardown (RESEARCH §Wiring 3). Buttons do NOT auto-close (D-11). In-flight busy via `dispatcher.inFlight.collectAsStateWithLifecycle()` keyed on the button's key (`MacroExecutionPopup.kt:106-107`).

---

### `ui/prompt/PromptImageItem.kt` (component, file-I/O)

**Analog:** Phase-10 webcam / Files Coil `AsyncImage` usage (Coil 3, present). Bound HARD: `ContentScale.Fit`, square box ≈ contentWidth/3 × clamped scale, request size tied to the cell (`inSampleSize` discipline), alt-text fallback on error (UI-SPEC §Image bounding). Path safety enforced upstream in `prompt/ParseAction.isValidImagePath` before any load. **Flag (A1):** `coil-svg` artifact may need adding for SVG prompt images — planner verifies against the catalog.

---

### `app/src/test/resources/prompt/fixtures.json` + `prompt/PromptFixtureTest.kt` (test)

**Analog:** `app/src/test/resources/golden/*.json` + `net/GoldenFixtures.kt` loader + `FixtureSanityTest.kt` driver.

**The fixture loader idiom** (`GoldenFixtures.kt:17-27`): resource read off the classpath + `Json.parseToJsonElement`:

```kotlin
fun raw(name: String): String =
    requireNotNull(GoldenFixtures::class.java.getResourceAsStream("/golden/$name")) {
        "Fixture /golden/$name not found on the test classpath"
    }.bufferedReader().use { it.readText() }
fun load(name: String): JsonElement = MoonrakerJson.parseToJsonElement(raw(name))
```

Mirror as a `PromptFixtures` loader for `/prompt/fixtures.json`, walk `["fixtures"].jsonArray`.

**The loop-driven fixture test idiom** (`FixtureSanityTest.kt:37-43`) — NOTE: the project uses a plain `@Test` + `forEach` loop, NOT `@RunWith(Parameterized::class)` (no parameterized test exists in the repo):

```kotlin
@Test fun allFixturesLoadAndParse() {
    allFixtures.forEach { name ->
        val obj = GoldenFixtures.loadObject(name)
        assertTrue("Fixture $name parsed empty", obj.isNotEmpty())
    }
}
```

Replicate as: for each of the 26 fixtures (and each `expected_by_frontend` key), `initialPromptState(dinghyOpts)` → fold `events` (`"__disconnect__"` → disconnect event, else `parseAction`, skip nulls) → `promptView(state)` → `assertMatchesPartial(view, expectedJson)` (the `toMatchObject` comparator — absent ≠ default; arrays fully-specified → exact length; `size:null` distinguishes asserted-null from absent). Add the EXACT-6-keys structural assertion and the `schema_version==1` + count==26 guard (Pitfall 7). Add the `dinghy → ["touch"]` frontend row (D-08). RESEARCH §"Concrete Kotlin porting approach" steps 1-6 is the spec.

> **AGP gotcha (MEMORY):** the `--tests 'works.mees.dinghy.prompt.*'` glob false-fails on this AGP — list the test classes explicitly when running.

**Per-module unit tests** (`PromptReducerTest` / `PromptMarkupTest` / `PromptParseTest`) follow `ConsoleSeverityTest` / `QrPayloadParserTest`; also port the 8 `hardening.spec.ts` edge cases (RESEARCH Wave-0 gaps).

---

### MODIFY `ui/shell/AppShell.kt` (shell)

**Self-analog:** the existing `MacroExecutionPopup` overlay hoist (lines 549-561) and `nav.scanActive` scan overlay (567-580) — overlays float OUTSIDE the `when(dest)` block (392-547), shown by hoisted state.

**Holder construction** (the `remember(store) { …Holder(scope, store) }` seam, `AppShell.kt:165-167`, 279):

```kotlin
val probeCalibrateHolder = remember(store) {  // ← prompt: val promptEngine = remember(store) { PromptEngine(scope, store, events = dispatcher?.events) }
    ProbeCalibrateHolder(scope = scope, store = store, events = calibEvents)
}
```

**Overlay hoist** (mirror `AppShell.kt:554-561`, OUTSIDE `when(dest)`, NOT a `Dest`):

```kotlin
if (promptEngine.visible) {                    // collectAsStateWithLifecycle on the view StateFlow
    PromptDialog(view, onButton = { idx -> /* dispatch */ }, onClose = { /* dispatch prompt_end */ })
}
```

**Drawer suppression:** add prompt-visible to the swipe-up suppress condition (`AppShell.kt:384` `dest !in setOf(...)`), per LAYOUT.md "Scroll Field ⇒ no swipe-up" + UI-SPEC. Consider a `BackHandler(enabled = promptEngine.visible)` mirroring the scan/popup back handlers (343-362).

**Construction seam (Open Question 1):** the engine is spine-level/long-lived; `di/AppContainer.kt` is the construction analog if it must outlive `AppShell` recomposition — planner confirms whether it lives in `AppContainer` or as a `remember`'d shell holder (the calibration holders are `remember`'d in the shell; the dispatcher comes from the live spine).

---

## Shared Patterns

### Command dispatch (button + close gcode)
**Source:** `command/CommandDispatcher.kt` (`dispatch(key, method, params)`, lines 108-157) + `MacroExecutionPopup.kt:158-163`.
**Apply to:** `PromptContentButton`, `PromptFooterBar` close control, `PromptEngine` key bookkeeping.
Stable keys `prompt:<epoch>:<index>` / `prompt:close` (Pitfall 4). The dispatcher already gives debounce + in-flight guard + the long 120s `GCODE_TIMEOUT_MS` for `gcode.script` (G4 fix) + redacted `DispatchEvent.Failure` (never `e.message` → never leaks `?token=`). Reuse, do not re-roll.

### Error/in-flight surfacing
**Source:** `designsystem/SeverityToast.kt` + `MacroExecutionPopup.kt:243-252` + `ProbeCalibrateHolder` events-fold (110-125).
**Apply to:** `PromptDialog` (toast slot), `PromptEngine` (fold Failure for prompt keys).
Toast copy: `"{gcode} was rejected: {redacted message}"` (UI-SPEC copy contract). Never build a toast from raw exception text.

### Tokens + font scale (chrome only)
**Source:** `theme/compose/LocalTokens.kt` (`LocalTokens.current`), `theme/ThemeTokens.kt`, `fsSp(baseSp, t.fs)`, `Geist`/`GeistMono`.
**Apply to:** ALL dialog chrome. Sizes 15/18/22/28 only (UI-SPEC); floor 15sp (fonts-too-small lesson). The author-hex carve-out (D-03) is the ONLY raw color, and ONLY inside `PromptMarkupText` spans — everything else routes through tokens.

### Raw gcode stream (transport — no new transport)
**Source:** `state/PrinterStateStore.kt:193-196` (`onGcodeLine`/`gcodeResponses`), fed by `net/MoonrakerSession` ← `net/JsonRpcClient.gcodeLine`. `ui/console/ConsoleFilters.HIDE_PROMPT_COMMANDS` (line 35) already hides prompt lines from the console view while the raw stream still carries them.
**Apply to:** `PromptEngine` subscribes the SAME un-throttled stream as the console; independent subscriber (D-13).

---

## No Analog Found

| File | Role | Data Flow | Reason / Mitigation |
|------|------|-----------|---------------------|
| `ui/prompt/PromptMarkupText.kt` | component | transform | No existing markup→`AnnotatedString` builder in the repo. It is the one genuinely-new Compose piece (RESEARCH). Pattern source = `SpanStyle` Compose API + the spool-color hex precedent + `fsSp` size ladder; logic spec'd verbatim in RESEARCH §"Compose AnnotatedString builder". Keep the pure AST in `prompt/Markup.kt` (analog: ConsoleSeverity) and only the span translation here. |

(All other new files have strong in-repo analogs — see table above.)

---

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/{ui/macros,ui/spool/scan,ui/console,command,designsystem,designsystem/control,calibration,state,theme,ui/shell,di}`, `app/src/test/java/works/mees/dinghy/{net,...}`, `app/src/test/resources/`.
**Files scanned/read:** MacroExecutionPopup, QrPayloadParser, ConsoleSeverity, ConsoleFilters, CommandDispatcher, ProbeCalibrateHolder, GoldenFixtures, FixtureSanityTest, OutlinedControl, AppShell (overlay + holder regions), PrinterStateStore (gcode stream), PrinterState (ConnectionState). 12 analog files read + directory enumeration.
**Pattern extraction date:** 2026-06-04

## PATTERN MAPPING COMPLETE
