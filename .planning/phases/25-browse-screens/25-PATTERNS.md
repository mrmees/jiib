# Phase 25: Browse Screens — Pattern Map

**Mapped:** 2026-06-10
**Files analyzed:** 9 new/modified files
**Analogs found:** 9 / 9

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `ui/files/FilesScreen.kt` | screen (rebuild) | request-response + CRUD | `ui/spool/SpoolScreen.kt` | exact |
| `ui/files/FileListView.kt` | view (retain-or-replace) | streaming | `ui/console/ConsoleListView.kt` | exact |
| `ui/macros/BookmarkedMacrosScreen.kt` | screen (rebuild, absorbs System) | request-response + CRUD | `ui/spool/SpoolScreen.kt` | role-match |
| `ui/macros/MacroExecutionPopup.kt` | component (remove — logic migrates into screen) | event-driven | `ui/spool/SpoolScreen.kt` FieldMode.FilterPicker | role-match |
| `ui/macros/SystemMacrosScreen.kt` | screen (remove — absorb into Bookmarked) | CRUD | `ui/macros/SystemMacrosScreen.kt` | self |
| `ui/console/ConsoleScreen.kt` | screen (rebuild) | streaming | `ui/spool/SpoolScreen.kt` (field-only variant) | role-match |
| `ui/console/ConsoleListView.kt` | view (retain-or-replace) | streaming | `ui/files/FileListView.kt` | exact |
| `ui/webcam/WebcamScreen.kt` | screen (re-token + crash fix) | streaming | `ui/spool/SpoolScreen.kt` (focus-only variant) | role-match |
| `ui/shell/AppShell.kt` (line 214–215) | shell (bug fix) | request-response | self | self |

---

## Pattern Assignments

### `ui/files/FilesScreen.kt` (screen, rebuild)

**Analog:** `ui/spool/SpoolScreen.kt`

**Two-overload stateless seam** (SpoolScreen.kt lines 98–257):
```kotlin
// LIVE overload — holder-consuming
@Composable
fun FilesScreen(
    holder: FileBrowserHolder,
    dispatcher: CommandDispatcher?,
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // ... spool gate params preserved verbatim
) {
    val state by holder.state.collectAsStateWithLifecycle()
    val printerState by container.printerState.collectAsStateWithLifecycle(
        initialValue = PrinterState(),
    )
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    var showEstopGuard by remember { mutableStateOf(false) }

    LaunchedEffect(holder) { holder.loadRoot() }

    Box(modifier.fillMaxSize()) {
        FilesContent(state = state, isPrinting = isPrinting, …)
        if (showEstopGuard) { ConfirmGuard(…) }
        // ConfirmGuard overlays for Start / Delete are Box siblings here too
    }
}

// STATELESS preview seam
@Composable
fun FilesScreen(
    state: FilesScreenState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onPrint: () -> Unit = {},
    onDelete: () -> Unit = {},
    onEmergencyStop: () -> Unit = {},
    isPrinting: Boolean = false,
) { … }
```

**ScreenScaffold scaffold** (SpoolScreen.kt lines 289–442):
```kotlin
@Composable
private fun FilesContent(…) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        val t = LocalTokens.current
        ScreenScaffold(
            focus = {
                Box(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    DetailCard(modifier = Modifier.fillMaxSize()) {
                        // FilePreviewContent: image-backed card, future-print fields only
                        // thumbnail as dimmed AsyncImage background (alpha = 0.3f)
                        // stat lines: est time · filament · layers · height · size · modified
                        // FilesScreen.kt lines 379–467 = direct template for content inside DetailCard
                    }
                    FloatingEStop(
                        visible = isPrinting,
                        onClick = onEmergencyStop,
                        uDp = grid.uDp,
                        modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                    )
                }
                // D-06: single age-direction sort
                SortRow(
                    options = sortOptions,          // one SortOption with DinghyIcons.CalendarClock
                    activeKey = state.sortKey,
                    onSelect = onSelectSort,
                    uDp = grid.uDp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            },
            field = {
                // D-01 SPIKE GATE: either ListBlock+ListRow OR FileListView pinned-height
                // (decided by Wave-0 gfxinfo; see Views-in-Compose section below)
                FootButtonBar(
                    uDp = grid.uDp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    OutlinedControl("", onBack, Modifier.weight(1f), Intent.Neutral, icon = DinghyIcons.ArrowBack)
                    OutlinedControl("", onPrint, Modifier.weight(1f), Intent.Accent, icon = DinghyIcons.Print)
                    OutlinedControl("", onDelete, Modifier.weight(1f), Intent.Danger, icon = DinghyIcons.Delete)
                }
            },
            gutter = null,  // MANDATORY: redesigned screens null the gutter
        )
    }
}
```

**Delete-scoping predicate — preserve verbatim** (FilesScreen.kt lines 151–155):
```kotlin
val deleteEnabled = deleteAllowed(
    selectedPath = selected?.relativeFilename,
    activePrintFilename = printerState.printFilename,
    printState = printerState.printState,
)
```

**SpoolWarningGuard + ConfirmGuard as Box siblings** (FilesScreen.kt lines 161–280 — preserve full logic, only reskin the buttons using `OutlinedControl(intent=...)` with `DinghyIcons` tokens instead of hand-rolled `FileActionControl`).

**D-05 flat list — filter row kinds at render only** (FilesScreen.kt line 185–190):
```kotlin
// Only render File rows — ignore Up and Directory row kinds (D-05)
// Call holder.loadRoot() once on entry; never call enterFolder/goUp
onRowClick = { row ->
    if (row.kind == FileBrowserRowKind.File) holder.selectFile(row)
    // Up and Directory silently ignored
}
```

**Image-backed DetailCard content** (FilesScreen.kt FilePreviewFocus lines 396–467 = direct template):
```kotlin
// Inside DetailCard { … }:
AsyncImage(
    model = ImageRequest.Builder(context).data(url).build(),
    contentScale = ContentScale.Fit,   // Fit NOT Crop (CLAUDE.md law)
    alpha = 0.3f,
    modifier = Modifier.matchParentSize(),
)
// Foreground stats overlay (GeistMono, left-aligned, fsSp scales):
// filename: fsSp(20f, t.fs).sp, fontFamily = GeistMono
// stat rows: icon (fsSp(18f,t.fs)) + dim label (fsSp(15f,t.fs)) + value (fsSp(17f,t.fs))
```

---

### `ui/files/FileListView.kt` (view, retain-or-replace after D-01 spike)

**Analog:** `ui/console/ConsoleListView.kt` (identical pattern; Files is the original, Console is the direct copy)

**If spike RETAINS Views — load-bearing pattern** (FileListView.kt lines 49–78, ConsoleListView.kt lines 54–103):
```kotlin
// BoxWithConstraints wrapper in the screen (ConsoleScreen.kt lines 85–91):
BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
    FileListView(
        // ...
        modifier = Modifier.fillMaxWidth().height(maxHeight),  // EXACT height pin — load-bearing
    )
}

// Inside AndroidView factory (FileListView.kt lines 54–67):
AndroidView(
    modifier = modifier.clipToBounds(),  // prevents Android-layer rows painting over Compose siblings
    factory = { context ->
        RecyclerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,  // MATCH_PARENT — prevents UNSPECIFIED measure
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(true)
            itemAnimator = null  // Adreno-320: NO animated insertion
            this.adapter = adapter
        }
    },
    update = { adapter.submitRows(rows, selectedStableId, httpBase, palette) }
)
```

**Phase-22 applyTokens equality guard** — if Views retained, the D-12 guard from 22-05 must remain on the `update` lambda. Verify `FileRowsAdapter.applyTokens` equality check is present before rebuilding.

**If spike MIGRATES to Compose — ListBlock+ListRow replacement:**
```kotlin
// Replace FileListView with ListBlock (designsystem/layout/ListBlock.kt)
ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
    items(
        items = fileRows,               // FileBrowserRowKind.File only (D-05)
        key = { it.stableId },          // MANDATORY stable key (prevents whole-list recomposition)
    ) { row ->
        ListRow(
            selected = row.stableId == state.selectedFile?.stableId,
            onClick = { onRowClick(row) },
            uDp = grid.uDp,
            leadingContent = { /* thumbnail AsyncImage or placeholder */ },
            trailingContent = { /* file size + modified */ },
        ) {
            // filename in GeistMono, size fsSp(18f, t.fs).sp; modified date in text2
        }
    }
}
```

---

### `ui/macros/BookmarkedMacrosScreen.kt` (screen, rebuild — absorbs SystemMacrosScreen + MacroExecutionPopup)

**Analog:** `ui/spool/SpoolScreen.kt` (FieldMode sealed class pattern)

**MacroFieldMode sealed class** (modelled after SpoolHolder.kt lines 125–131):
```kotlin
// Define in the macros package (alongside BookmarkedMacrosScreen or MacroHolder)
sealed class MacroFieldMode {
    data object Launcher : MacroFieldMode()        // Bookmarked list
    data class ParamEntry(val macro: MacroVm) : MacroFieldMode()  // Field-takeover per-param
    data object ManageMode : MacroFieldMode()      // System manage list (D-09)
}
```

**Two-overload seam** (SpoolScreen.kt pattern — lines 98–257):
```kotlin
// LIVE overload
@Composable
fun BookmarkedMacrosScreen(
    holder: MacroHolder,
    dispatcher: CommandDispatcher,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by holder.state.collectAsStateWithLifecycle()
    var fieldMode by remember { mutableStateOf<MacroFieldMode>(MacroFieldMode.Launcher) }
    Box(modifier.fillMaxSize()) {
        MacrosContent(state = state, fieldMode = fieldMode, …)
    }
}
// STATELESS preview seam — no holder, no dispatcher
```

**ScreenScaffold + field when()** (SpoolScreen.kt lines 392–425):
```kotlin
ScreenScaffold(
    focus = null,  // Macros has no detail Focus
    field = {
        when (val mode = fieldMode) {
            is MacroFieldMode.Launcher -> MacroLauncherField(…)
            is MacroFieldMode.ParamEntry -> MacroParamEntryField(mode.macro, …)
            is MacroFieldMode.ManageMode -> MacroManageField(…)
        }
    },
    gutter = null,
)
```

**Launcher Field — ListBlock of ListRow (D-10, replaces LazyVerticalGrid)**:
```kotlin
// MacroLauncherField — ColumnScope extension (same pattern as SpoolListField)
// BookmarkedMacrosScreen.kt lines 61–106 = old grid, replace entirely:
DesignListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
    items(state.bookmarkedMacros, key = { it.name }) { macro ->
        ListRow(
            selected = false,  // tapping executes, not selects
            onClick = { fieldMode = MacroFieldMode.ParamEntry(macro) },
            uDp = uDp,
        ) {
            Text(
                text = macro.name,
                fontFamily = GeistMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(18f, t.fs).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
        }
    }
}
FootButtonBar(uDp = uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
    OutlinedControl("", onBack, Modifier.weight(1f), Intent.Neutral, icon = DinghyIcons.ArrowBack)
    OutlinedControl("", { fieldMode = MacroFieldMode.ManageMode }, Modifier.weight(1f),
        Intent.Neutral, icon = DinghyIcons.Tune)
}
```

**ParamEntry Field — preserve ALL MacroExecutionPopup logic** (MacroExecutionPopup.kt lines 104–277):
```kotlin
// MacroParamEntryField — ColumnScope extension
// Re-resolve live VM from holder by NAME (WR-03 — reactive params on cold connect)
val holderState by holder.state.collectAsStateWithLifecycle()
val liveMacro = remember(holderState, macro.name) {
    holderState.macros.firstOrNull { it.name.equals(macro.name, ignoreCase = true) } ?: macro
}
val bodyLoaded = remember(holderState, macro.name) { holder.paramsKnown(macro.name) }
val params = liveMacro.params

// values — seeded from param.default, re-seeded when param SET changes (WR-03):
val values = remember(macro.name, params) {
    mutableStateMapOf(*params.map { it.name to (it.default ?: "") }.toTypedArray())
}

// numpadParam — which numeric param has its NumpadPage sub-page open
var numpadParam by remember(macro.name) { mutableStateOf<MacroParam?>(null) }

// NumpadPage sub-page takes over the Field (Field-takeover pattern):
val editing = numpadParam
if (editing != null) {
    NumpadPage(
        label = editing.name,
        initial = values[editing.name].orEmpty().toDoubleOrNull() ?: 0.0,
        range = MACRO_NUMERIC_RANGE,  // -100_000.0..100_000.0
        onCancel = { numpadParam = null },
        onSet = { committed ->
            values[editing.name] = formatNumeric(committed)
            numpadParam = null
        },
    )
    return  // Field entirely replaced while numpad is open
}

// Param list in ListBlock (one ListRow per param):
DesignListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
    items(params, key = { it.name }) { param ->
        if (param.isNumeric) {
            // Tappable numeric param row → opens NumpadPage
            ListRow(selected = false, onClick = { numpadParam = param }, uDp = uDp) {
                // param.name + current value (GeistMono)
            }
        } else {
            // String param — TokenTextField (D-12 the ONE sanctioned alpha keyboard)
        }
    }
}
FootButtonBar(uDp = uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
    OutlinedControl("", { fieldMode = MacroFieldMode.Launcher }, Modifier.weight(1f),
        Intent.Neutral, icon = DinghyIcons.ArrowBack)
    // Execute: disabled while !bodyLoaded or running (WR-03 / PRIM-05)
    OutlinedControl("Execute", ::execute, Modifier.weight(1f), Intent.Accent, icon = null)
}
```

**MacroInvocation.buildTyped — must not be bypassed** (MacroExecutionPopup.kt lines 145–163):
```kotlin
fun execute() {
    val gcode = try {
        MacroInvocation.buildTyped(
            macro.name,
            params.map { p -> Triple(p.name, values[p.name].orEmpty(), p.isNumeric) },
        )
    } catch (e: MacroParamRejected) {
        toast = "${macro.name} was rejected: ${e.reason}"
        return
    }
    dispatcher.dispatch(
        key = "macro_${macro.name}",   // PRIM-05
        method = JsonRpcMethods.GCODE_SCRIPT,
        params = PrinterCommands.scriptParams(gcode),
    )
    fieldMode = MacroFieldMode.Launcher
}
```

**ManageMode Field** (SystemMacrosScreen.kt lines 83–117 — translated to ListBlock+ListRow):
```kotlin
// MacroManageField — ColumnScope extension
DesignListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
    items(state.visibleMacros, key = { it.name }) { macro ->
        ListRow(
            selected = macro.isBookmarked,
            onClick = { onToggleBookmark(macro.name) },
            uDp = uDp,
            trailingContent = {
                // check_circle icon when bookmarked, radio_button_unchecked when not
                // (SystemMacrosScreen.kt MacroSelectRow lines 149–158 = direct template)
            },
        ) { /* macro.name in GeistMono, fsSp(18f, t.fs) */ }
    }
}
FootButtonBar(uDp = uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
    OutlinedControl("", { fieldMode = MacroFieldMode.Launcher }, Modifier.weight(1f),
        Intent.Neutral, icon = DinghyIcons.ArrowBack)
    // Show hidden toggle (SystemMacrosScreen.kt lines 110–115 = template):
    OutlinedControl(
        label = "", onClick = { onSetRevealHidden(!state.revealHidden) },
        modifier = Modifier.weight(1f),
        intent = if (state.revealHidden) Intent.Accent else Intent.Neutral,
        icon = if (state.revealHidden) DinghyIcons.Visibility else DinghyIcons.VisibilityOff,
    )
}
```

**Dispatcher failure toast — LaunchedEffect** (MacroExecutionPopup.kt lines 136–142):
```kotlin
LaunchedEffect(dispatcher, macro.name) {
    dispatcher.events.collect { event ->
        if (event is DispatchEvent.Failure && event.key == "macro_${macro.name}") {
            toast = "${macro.name} was rejected: ${event.message}"
        }
    }
}
```

---

### `ui/console/ConsoleScreen.kt` (screen, rebuild)

**Analog:** `ui/spool/SpoolScreen.kt` (field-only variant — no Focus)

**Two-overload seam** (same pattern as SpoolScreen.kt):
```kotlin
// LIVE overload
@Composable
fun ConsoleScreen(
    holder: ConsoleHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backfillFailed: Boolean = false,
) {
    val rawLines by holder.state.collectAsStateWithLifecycle()
    var hideTemps by remember { mutableStateOf(false) }
    var hideTimelapse by remember { mutableStateOf(false) }
    var hidePrompt by remember { mutableStateOf(false) }
    val filtered = ConsoleFilters.apply(rawLines, hideTemps, hideTimelapse, hidePrompt)

    Box(modifier.fillMaxSize()) {
        ConsoleContent(
            lines = filtered,
            rawLines = rawLines,
            backfillFailed = backfillFailed,
            hideTemps = hideTemps,
            hideTimelapse = hideTimelapse,
            hidePrompt = hidePrompt,
            onToggleTemps = { hideTemps = !hideTemps },
            onToggleTimelapse = { hideTimelapse = !hideTimelapse },
            onTogglePrompt = { hidePrompt = !hidePrompt },
            onBack = onBack,
        )
    }
}
// STATELESS preview seam — receives pre-filtered lines + toggle states
```

**ScreenScaffold — field-only, gutter = null** (ConsoleScreen.kt lines 78–133 = current; migrate gutter → FootButtonBar):
```kotlin
@Composable
private fun ConsoleContent(…) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        ScreenScaffold(
            focus = null,   // D-14: no Focus; full height goes to the scrollback
            field = {
                // D-01 SPIKE GATE: either ConsoleListView pinned-height OR LazyColumn+reverseLayout
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    // Spike-decides path (Views or Compose)
                    ConsoleListView(lines = filtered, modifier = Modifier.fillMaxWidth().height(maxHeight))
                }
                // D-15: filter toggles as FootButtonBar items (not gutter)
                FootButtonBar(
                    uDp = grid.uDp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    // Each toggle is an OutlinedControl with intent-driven state:
                    OutlinedControl(
                        label = "",
                        onClick = onToggleTemps,
                        modifier = Modifier.weight(1f),
                        intent = if (hideTemps) Intent.Accent else Intent.Neutral,
                        icon = DinghyIcons.Thermostat,  // CHECK registry before using — D-21
                    )
                    OutlinedControl("", onToggleTimelapse, Modifier.weight(1f),
                        if (hideTimelapse) Intent.Accent else Intent.Neutral, DinghyIcons.Videocam)
                    OutlinedControl("", onTogglePrompt, Modifier.weight(1f),
                        if (hidePrompt) Intent.Accent else Intent.Neutral, DinghyIcons.ChatBubble)
                    OutlinedControl("", onBack, Modifier.weight(1f), Intent.Neutral, DinghyIcons.ArrowBack)
                }
            },
            gutter = null,
        )
    }
}
```

**CRITICAL: ConsoleFilters applied at render, never in holder** (ConsoleScreen.kt lines 70–75):
```kotlin
// D-15 (D-04): filters applied HERE, off rawLines from holder
val filtered = ConsoleFilters.apply(
    lines = rawLines,          // holder.state = RAW unfiltered
    hideTemperatures = hideTemps,
    hideTimelapse = hideTimelapse,
    hidePrompt = hidePrompt,
)
// filtered is passed to ConsoleListView; rawLines is never mutated by filter state
```

---

### `ui/console/ConsoleListView.kt` (view, retain-or-replace after D-01 spike)

**Analog:** `ui/files/FileListView.kt` (ConsoleListView.kt self-describes as a "VERBATIM" copy of the FileListView pattern)

**Load-bearing RecyclerView pattern** (ConsoleListView.kt lines 37–103 — if Views retained):
```kotlin
// The three non-negotiable bits from FileListView + the console-specific additions:
AndroidView(
    modifier = modifier.clipToBounds(),  // (1) Android-layer clip
    factory = { context ->
        RecyclerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)  // (2)
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true  // Console-specific: newest line at bottom
            }
            setHasFixedSize(false)
            itemAnimator = null       // (3) no animation on Adreno-320
            this.adapter = adapter
        }
    },
    update = { recycler ->
        val lm = recycler.layoutManager as LinearLayoutManager
        val oldCount = adapter.itemCount
        val wasAtBottom = oldCount == 0 || lm.findLastVisibleItemPosition() >= oldCount - 1
        // isSingleAppend / isAppendEvict incremental paths (ConsoleListView.kt lines 82–101)
        // These optimizations are console-specific and must NOT be dropped if Views retained
        val isSingleAppend = lines.size == oldCount + 1 && adapter.matches(lines.subList(0, oldCount))
        val isAppendEvict = !isSingleAppend && adapter.isAppendEvict(lines)
        if (isSingleAppend || isAppendEvict) {
            val pos = adapter.appendLine(lines.last())
            if (wasAtBottom) recycler.scrollToPosition(pos)
        } else if (!adapter.matches(lines)) {
            adapter.submitRows(lines)
            if (wasAtBottom && lines.isNotEmpty()) {
                recycler.post { recycler.scrollToPosition(lines.size - 1) }
            }
        }
    },
)
```

**If spike MIGRATES to Compose — LazyColumn with reversed layout:**
```kotlin
// Compose replacement for ConsoleListView (no append-evict optimization needed —
// LazyColumn handles key-based diffing efficiently)
val listState = rememberLazyListState()
LaunchedEffect(lines.size) {
    if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
}
LazyColumn(
    state = listState,
    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
) {
    items(lines, key = { it.id }) { line ->  // stable key required
        ConsoleLineRow(line, t)
    }
}
```

---

### `ui/webcam/WebcamScreen.kt` (screen, re-token + crash fix + WR-02)

**Analog:** `ui/spool/SpoolScreen.kt` (structural reference); `ui/webcam/WebcamScreen.kt` (self — minimal touch)

**WR-02 fix — AppShell.kt lines 214–215** (confirmed via grep `activeConfig` in AppContainer.kt):
```kotlin
// CURRENT (WRONG — connectionStore is write-dead since Phase 14):
val cfg by container.connectionStore.config.collectAsStateWithLifecycle(initialValue = null)
val activeCfg = cfg ?: ConnectionConfig(host = "")

// FIX — AppContainer.activeConfig is a Flow<ConnectionConfig?>:
val activeCfg by container.activeConfig.collectAsStateWithLifecycle(initialValue = null)
// Note: activeCfg is now ConnectionConfig? (nullable). The remember() keys on lines 247/250
// use activeCfg.host — update to activeCfg?.host ?: "" for null safety.
// The webcamHolder construction on line 255 passes cfg = activeCfg — update to cfg = activeCfg ?: ConnectionConfig(host = "")
```

**Lifecycle pattern — preserve verbatim** (WebcamScreen.kt lines 92–95):
```kotlin
DisposableEffect(holder) {
    holder.start()
    onDispose { holder.stop() }
}
```

**Token conformance — restyle chrome only, render path UNTOUCHED** (WebcamScreen.kt lines 199–250):
```kotlin
// CamPicker: replace hand-rolled border colors with token-routed values
// CURRENT (already uses t.accentLine / t.outline — mostly conformant):
Column(
    Modifier.fillMaxWidth().clip(shape)
        .border(BorderStroke(2.dp, if (isSelected) t.accentLine else t.outline), shape)
        .clickable { onSelect(cam) }
        .padding(12.dp),
)
// Text sizes: replace any bare .sp with fsSp(n, t.fs).sp

// FeedFocus (lines 150–194): UNTOUCHED — render path is locked (D-16)
// Media3SurfaceHost / WebcamViewHost calls: UNTOUCHED
```

**FootButtonBar migration — move Back from gutter to field** (WebcamScreen.kt lines 132–144):
```kotlin
// CURRENT:
gutter = {
    Row(Modifier.fillMaxWidth().padding(8.dp)) {
        OutlinedControl("Back", onBack, Modifier.fillMaxWidth(), Intent.Neutral)
    }
}

// MIGRATED:
field = {
    // CamPicker (if showField) + FootButtonBar replacing the gutter Back
    FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        OutlinedControl("", onBack, Modifier.weight(1f), Intent.Neutral, icon = DinghyIcons.ArrowBack)
    }
},
gutter = null,
```

**BoxWithConstraints + grid for conformance** (SpoolScreen.kt lines 289–293):
```kotlin
// WebcamScreen currently uses BoxWithConstraints for the showField logic — extend to add grid:
BoxWithConstraints(modifier.fillMaxSize()) {
    val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
    val landscapeDevice = maxWidth > maxHeight
    // ... existing showField logic unchanged
    ScreenScaffold(
        focus = { FeedFocus(…) },       // UNTOUCHED render path
        field = if (showField) { { CamPicker(…) } } else null,
        gutter = null,
    )
}
```

---

## Shared Patterns

### 1. Two-Overload Stateless Seam (apply to all four screens)

**Source:** `ui/spool/SpoolScreen.kt` lines 98–257
**Apply to:** FilesScreen, BookmarkedMacrosScreen, ConsoleScreen, WebcamScreen

Every migrated screen ships two function overloads with the same name:
1. `fun XxxScreen(holder: XxxHolder, …)` — live overload, collects StateFlow, delegates to `private fun XxxContent(…)`.
2. `fun XxxScreen(state: XxxScreenState, …)` — stateless overload for `@Preview`, accepts all callbacks with `= {}` defaults.

Both delegate to a `private fun XxxContent(…)` that does the actual rendering.

---

### 2. BoxWithConstraints + rememberUnitGrid (apply to all four screens)

**Source:** `ui/spool/SpoolScreen.kt` lines 289–293, `designsystem/layout/UnitGrid.kt`
**Apply to:** all four screens (FilesScreen, BookmarkedMacrosScreen, ConsoleScreen, WebcamScreen)

```kotlin
BoxWithConstraints(Modifier.fillMaxSize()) {
    val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
    // grid.uDp passed to ListRow, FootButtonBar, SortRow, FilterRow, FloatingEStop
}
```

The `remember(contentMinDim)` key inside `rememberUnitGrid` prevents recomputation on every recomposition — only recalculates on orientation flip.

---

### 3. gutter = null + FootButtonBar in field (apply to all four screens)

**Source:** `ui/spool/SpoolScreen.kt` line 425, `designsystem/components/FootButtonBar.kt`
**Apply to:** all four screens

```kotlin
ScreenScaffold(
    focus = { … },
    field = {
        // list content (weight(1f))
        FootButtonBar(
            uDp = grid.uDp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            OutlinedControl(…, modifier = Modifier.weight(1f), …)
        }
    },
    gutter = null,  // ALWAYS null on rebuilt screens
)
```

---

### 4. FloatingEStop wiring (Files only — mid-print-valid)

**Source:** `ui/spool/SpoolScreen.kt` lines 116–119, 367–376, `designsystem/components/FloatingEStop.kt`
**Apply to:** `FilesScreen.kt` (Files is valid during print; Phase-24 D-04)

```kotlin
val printerState by container.printerState.collectAsStateWithLifecycle(
    initialValue = PrinterState(),
)
val isPrinting = printerState.printState == PrintState.Printing ||
    printerState.printState == PrintState.Paused
var showEstopGuard by remember { mutableStateOf(false) }

// In the Focus Box:
FloatingEStop(
    visible = isPrinting,
    onClick = { showEstopGuard = true },
    uDp = grid.uDp,
    modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
)

// ConfirmGuard overlay as Box sibling (SpoolScreen.kt lines 172–185):
if (showEstopGuard) {
    ConfirmGuard(
        title = stringResource(R.string.…),
        onConfirm = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit); showEstopGuard = false },
        onCancel = { showEstopGuard = false },
        destructive = true,
    )
}
```

---

### 5. SortRow with mandatory leading type-tile (Files only)

**Source:** `designsystem/components/SortFilterControlRow.kt` lines 101–168, `ui/spool/SpoolScreen.kt` lines 303–322
**Apply to:** `FilesScreen.kt` (the single age-direction sort control, D-06)

```kotlin
// One SortOption for date/age (D-06 — single metric, no filter facet)
val sortOptions = persistentListOf(
    SortOption(
        key = FileSortKey.DATE,
        icon = DinghyIcons.CalendarClock,   // D-21 CHECK: CalendarClock must be in DinghyIcons; ASK if not
        contentDescriptionRes = R.string.cd_files_sort_age,
        directionUp = if (state.sortKey == FileSortKey.DATE) state.sortAscending else null,
    ),
)
// The direction-toggle fires by re-tapping the active key (SpoolScreen.kt line 304 pattern:
// SpoolHolder.applySort toggles sortAscending when the same key is re-selected)

SortRow(
    options = sortOptions,
    activeKey = state.sortKey,
    onSelect = onSelectSort,
    uDp = grid.uDp,
    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
)
```

---

### 6. @Preview Matrix (apply to all four screens, D-20)

**Source:** `docs/ui_design/PREVIEW_AND_TOKENS.md`; Phase-18 established pattern
**Apply to:** all four migrated screens

```kotlin
// 6 required combos + landscape (SpoolScreen format):
@Preview(name = "Files Dark M portrait", widthDp = 480, heightDp = 800)
@Preview(name = "Files Dark L portrait", widthDp = 480, heightDp = 800)
@Preview(name = "Files Light M portrait", widthDp = 480, heightDp = 800)
@Preview(name = "Files Custom M portrait", widthDp = 480, heightDp = 800)
@Preview(name = "Files Dark S portrait", widthDp = 480, heightDp = 800)
@Preview(name = "Files Light S portrait", widthDp = 480, heightDp = 800)
@Preview(name = "Files Dark M landscape", widthDp = 800, heightDp = 480)
@Composable fun FilesScreenPreview() {
    // Stateless overload with FakeState fixtures — NO live Moonraker
    DinghyTheme(darkTheme = true, fs = FontScale.M) {
        FilesScreen(state = fakeFilesState())
    }
}
```

---

### 7. OutlinedControl with DinghyIcon token (intent routing)

**Source:** `designsystem/control/OutlinedControl.kt` lines 177–194
**Apply to:** all FootButtonBar contents, all filter toggles

Always use the `icon: DinghyIcon?` overload rather than `symbol: String?` for redesigned screens. The `symbol` overload is retained for pre-redesign back-compat only.

```kotlin
// CORRECT — DinghyIcon token overload:
OutlinedControl(
    label = "",
    onClick = onBack,
    modifier = Modifier.weight(1f),
    intent = Intent.Neutral,
    icon = DinghyIcons.ArrowBack,   // registered DinghyIcon token
)

// WRONG — raw ligature string (bypasses icon registry):
OutlinedControl(label = "Back", onClick = onBack, symbol = "arrow_back")
```

**Intent mapping** (OutlinedControl.kt lines 53–58, SpoolScreen.kt lines 503–534):
- Back (plain nav) → `Intent.Neutral` (outline color)
- Print / Execute / Load / Accent commands → `Intent.Accent` (accentLine)
- Delete / E-stop → `Intent.Danger` (stop/red)
- Filter active state → `Intent.Accent` / `Intent.Neutral` toggle

---

### 8. fsSp font-scale compliance (apply to all screens)

**Source:** `ui/spool/SpoolScreen.kt` lines 691–792; established floor from `dinghy-font-sizes-too-small`

```kotlin
// All text sizes route through fsSp(baseSp, t.fs):
// filename / primary row text:    fsSp(18f, t.fs).sp
// metadata / secondary row text:  fsSp(15f, t.fs).sp  (floor — never lower)
// stat row values (GeistMono):    fsSp(17f, t.fs).sp
// section headers:                fsSp(20f–22f, t.fs).sp
// tabular/stat focus values:      fsSp(26f, t.fs).sp
// NEVER bare 15.sp or 18.sp without fsSp wrapping
```

---

### 9. DataStore writes via AppContainer.writeScope (not composition scope)

**Source:** `memory/dinghy-compose-write-scope-cancellation.md`
**Apply to:** any persistence write in all four screens

```kotlin
// WRONG — rememberCoroutineScope() is cancelled on navigation (write silently dropped):
val scope = rememberCoroutineScope()
scope.launch { container.dataStore.edit { … } }

// CORRECT — process-lifetime scope:
container.writeScope.launch { container.dataStore.edit { … } }
```

---

## No Analog Found

All files have close analogs. No entries in this section.

---

## Key Anti-Patterns to Avoid (from RESEARCH.md)

| Anti-pattern | Where it bites | Correct pattern |
|---|---|---|
| `gutter = …` on rebuilt screens | All 4 screens | `gutter = null`; FootButtonBar in field |
| Raw ligature `symbol = "arrow_back"` | All screens' OutlinedControls | `icon = DinghyIcons.ArrowBack` |
| Missing `key` on `items(…)` | ListBlock consumers | `key = { it.stableId }` or `key = { it.name }` |
| Execute before `bodyLoaded` | MacroParamEntryField | Guard `!running && bodyLoaded` |
| Views without `clipToBounds()` | FileListView, ConsoleListView if retained | `modifier.clipToBounds()` on AndroidView |
| ConsoleFilters in holder | ConsoleScreen rebuild | Apply ONLY in `ConsoleContent`, off `rawLines` |
| `connectionStore.config` | AppShell.kt line 214 | `container.activeConfig` |
| Auto-pick icon glyph | All screens | Check DinghyIcons, then ASK owner (D-21) |
| Directory/Up rows in Files list | FilesScreen rebuild | Only render `FileBrowserRowKind.File` rows |

---

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/` (ui/, designsystem/)
**Files scanned:** 17 source files read directly
**Pattern extraction date:** 2026-06-10
