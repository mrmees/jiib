# Phase 24: Navigation Spine — Pattern Map

**Mapped:** 2026-06-10
**Files analyzed:** 9 new/modified files + 3 new test files
**Analogs found:** 9 / 9

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `ui/route/NavDest.kt` (new — replaces `Dest` enum) | route model | request-response | `ui/route/TopRoute.kt` (sealed interface pattern) | role-match |
| `ui/route/HomeAction.kt` (new) | typed data model | transform | `ui/route/TopRoute.kt` (sealed interface) + `ui/printstatus/PrintStatusMode.kt` (capability-gated enum) | role-match |
| `ui/shell/AppShell.kt` (major rework — `when(dest)` → `NavHost`) | shell host | request-response | `ui/shell/AppShell.kt` itself — the existing holder-hoist + overlay patterns are the analog | exact (self) |
| `ui/shell/ShellNavState.kt` (slim down) | nav state holder | event-driven | `ui/shell/ShellNavState.kt` itself — trim `dest`/`backStack`/`goBack` | exact (self) |
| `ui/shell/StartDestMapping.kt` (update `Dest` → `NavDest`) | utility | transform | `ui/shell/StartDestMapping.kt` itself — same safe-parse pattern | exact (self) |
| `ui/shell/RootController.kt` (no change) | shell host | event-driven | `ui/shell/RootController.kt` itself — gate-above pattern stays | exact (self) |
| `ui/printstatus/PrintStatusScreen.kt` (major rework — morph + idle list) | screen | request-response | `ui/printstatus/PrintStatusScreen.kt` itself — existing `PrintStatusContent`/`classifyPrintStatus` | exact (self) |
| `ui/printstatus/PrintStatusMode.kt` (no change needed) | pure function | transform | — (already correct) | exact |
| `gradle/libs.versions.toml` (add nav dep) | config | — | existing `libs.versions.toml` pin pattern | exact |
| `app/build.gradle.kts` (add nav dep) | config | — | existing `build.gradle.kts` dep pattern | exact |
| `test/ui/route/HomeActionTest.kt` (new) | test | — | `test/ui/route/TopRouteTest.kt` | role-match |
| `test/ui/route/PopToRootTest.kt` (new) | test | — | `test/ui/route/TopRouteTest.kt` | role-match |
| `test/ui/shell/StartDestMappingTest.kt` (update) | test | — | existing `StartDestMappingTest.kt` | exact |

---

## Pattern Assignments

### `ui/route/NavDest.kt` (route model, replaces `Dest` enum)

**Analog:** `app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt` (lines 14–23) for the `sealed interface` + `data object` pattern, AND `app/src/main/java/works/mees/dinghy/net/JsonRpc.kt` (lines 26–32) for the `@Serializable` annotation pattern.

**Sealed interface pattern** (`TopRoute.kt` lines 14–23):
```kotlin
sealed interface TopRoute {
    data object Connect : TopRoute
    data object Splash : TopRoute
    data class Shell(val dest: Dest) : TopRoute
}
```

**@Serializable pattern for NavDest** (adapt from `JsonRpc.kt` line 26 + `TopRoute.kt` structure):
```kotlin
@Serializable sealed interface NavDest {
    @Serializable data object WaterfallHome  : NavDest
    @Serializable data object Temperature    : NavDest
    @Serializable data object Move           : NavDest
    @Serializable data object Extrude        : NavDest
    @Serializable data object Files          : NavDest
    @Serializable data object Macros         : NavDest
    @Serializable data object Console        : NavDest
    @Serializable data object Calibration    : NavDest
    @Serializable data object FineTune       : NavDest
    @Serializable data object Webcam         : NavDest
    @Serializable data object Spool          : NavDest
    @Serializable data object Outputs        : NavDest
    @Serializable data object SystemInfo     : NavDest
    @Serializable data object Devices        : NavDest
    @Serializable data object Theme          : NavDest
    @Serializable data object Settings       : NavDest
    @Serializable data object About          : NavDest
}
```

**Migration note:** The existing `Dest` enum (`TopRoute.kt` line 37) becomes `NavDest`. `StartDestMapping.kt`'s `Dest.entries.firstOrNull { it.name == name }` must be rewritten since sealed objects have no `.entries`; replace with a `when` exhaustive map or a `knownNavDests: List<NavDest>` list. `TopRoute.Shell(val dest: Dest)` becomes `TopRoute.Shell` (no `dest` payload needed — the NavHost owns routing). Run a grep-replace wave on `Dest.` → `NavDest.` across ~93 call sites before any other change.

---

### `ui/route/HomeAction.kt` (new — typed idle action list model)

**Analog:** `ui/printstatus/PrintStatusMode.kt` (lines 23–38) for the sealed interface + data object/class pattern with a pure builder function; `ui/route/TopRoute.kt` for the sealed interface declaration.

**Sealed interface model pattern** (`PrintStatusMode.kt` lines 23–38):
```kotlin
sealed interface PrintStatusMode {
    data object Standby  : PrintStatusMode
    data object Printing : PrintStatusMode
    data object Paused   : PrintStatusMode
    data class Terminal(val kind: TerminalKind) : PrintStatusMode
}
```

**Capability-gated pure builder pattern** (`PrintStatusMode.kt` lines 45–52 — `classifyPrintStatus` uses only one input; `HomeAction` builder uses four capability booleans):
```kotlin
fun classifyPrintStatus(state: PrinterState): PrintStatusMode = when (state.printState) {
    PrintState.Printing -> PrintStatusMode.Printing
    // ...
}
```

**HomeAction shape to produce:**
```kotlin
sealed interface HomeAction {
    data class Destination(
        val dest: NavDest,
        @StringRes val labelRes: Int,
        val icon: IconRef,               // DinghyIcons token — never raw string
    ) : HomeAction
    data object OpenDrawer : HomeAction  // "System" foot button
    // v2: data class InlineControl(val controlId: String) : HomeAction
}

fun buildIdleActions(
    spoolmanPresent: Boolean,
    bookmarksExist: Boolean,
    outputsPresent: Boolean,
    webcamEnabled: Boolean,
): List<HomeAction> = buildList {
    if (spoolmanPresent)  add(HomeAction.Destination(NavDest.Spool, …, DinghyIcons.…))
    add(HomeAction.Destination(NavDest.Files,       …, DinghyIcons.…))
    add(HomeAction.Destination(NavDest.Move,        …, DinghyIcons.…))
    add(HomeAction.Destination(NavDest.Extrude,     …, DinghyIcons.…))
    if (bookmarksExist)   add(HomeAction.Destination(NavDest.Macros,  …, DinghyIcons.…))
    add(HomeAction.Destination(NavDest.Calibration, …, DinghyIcons.…))
    if (outputsPresent)   add(HomeAction.Destination(NavDest.Outputs, …, DinghyIcons.…))
    if (webcamEnabled)    add(HomeAction.Destination(NavDest.Webcam,  …, DinghyIcons.…))
}
```

**Icon law:** ALL icons in `HomeAction.Destination` must reference `DinghyIcons.*` registry tokens — NEVER raw Material ligature strings. Per `dinghy-never-pick-icons-ask`: if an existing screen already uses a glyph for a destination (e.g. Move uses `open_with` in `AppDrawer.kt` line 196), copy that registry mapping. If ANY destination has no established registry token, insert an ASK-OWNER task before implementing. The existing drawer tile symbols (`AppDrawer.kt` lines 195–228) are the current glyph assignments:

```
Status      → "monitoring"       → look up DinghyIcons registry
Move        → "open_with"
Temp        → "thermostat"
Files       → "folder"
Extrude     → "output_circle"
Macros      → "code"
Console     → "terminal"
Calibration → "tune"
Fine-Tune   → "instant_mix"
Webcam      → "photo_camera"
Spool       → "inventory_2"
```

These are the raw ligature strings from the drawer. Verify each maps to a `DinghyIcons.*` token before using in `HomeAction`.

---

### `ui/shell/AppShell.kt` (major rework — `when(dest)` → `NavHost`)

**Analog:** `ui/shell/AppShell.kt` itself is the primary analog — all patterns to preserve are in the existing file.

**Session holder hoist pattern** (`AppShell.kt` lines 186–489, abridged key examples):

Exact `remember(store)` + `DisposableEffect` leak-cancel discipline to copy literally:

```kotlin
// Lines 199–204: basic holder hoist (no cancel needed)
val temperatureHolder = remember(store) { TemperatureHolder(scope = scope, store = store) }
val moveHolder        = remember(store) { MoveHolder(scope = scope, store = store) }
val extrudeHolder     = remember(store) { ExtrudeHolder(scope = scope, store = store) }
val filesHolder = remember(fileBrowser, printerStateFlow) {
    FileBrowserHolder(scope = scope, client = fileBrowser, printerState = printerStateFlow)
}
```

```kotlin
// Lines 256–275: complex holder with TWO keys + DisposableEffect cancel (webcam — the critical leak-cancel)
val webcamHolder: WebcamHolder<Bitmap> = remember(store, activeCfg.host, activeProfileId) {
    webcamMedia3Holder(…)
}
DisposableEffect(webcamHolder) { onDispose { webcamHolder.cancel() } }  // WR-01 — load-bearing
```

```kotlin
// Lines 309–313: spool holder with cancel
val spoolHolder = remember(store) { SpoolHolder(…) }
DisposableEffect(spoolHolder) { onDispose { spoolHolder.cancel() } }
```

```kotlin
// Lines 418–428: console holder with cancel
val consoleHolder = remember(store) { ConsoleHolder(…) }
DisposableEffect(consoleHolder) { onDispose { consoleHolder.cancel() } }
```

```kotlin
// Lines 456–468: macro holder with TWO keys + cancel
val macroHolder = remember(store, capabilitiesFlow) { MacroHolder(…) }
DisposableEffect(macroHolder) { onDispose { macroHolder.cancel() } }
```

**CRITICAL MIGRATION RULE:** ALL of the above `remember(…)` blocks must remain ABOVE the `NavHost(…)` call — they must not move into `composable<NavDest.*>` lambdas. Destinations receive holders as parameters.

**Webcam lifecycle effect — key must change** (`AppShell.kt` lines 283–294):
```kotlin
// BEFORE (old dest enum key):
androidx.compose.runtime.LaunchedEffect(webcamHolder, dest, lifecycleOwner) {
    if (dest == Dest.Webcam) { … }
}

// AFTER (NavBackStackEntry key — collect outside NavHost, pass in):
val navBackStackEntry by navController.currentBackStackEntryAsState()
LaunchedEffect(webcamHolder, navBackStackEntry, lifecycleOwner) {
    val isWebcam = navBackStackEntry?.destination?.hasRoute<NavDest.Webcam>() == true
    if (isWebcam) { lifecycleOwner.lifecycle.repeatOnLifecycle(STARTED) { webcamHolder.start(); … } }
}
```

**Output selection-reset LaunchedEffect** (`AppShell.kt` lines 353–358 — stays above NavHost, keyed on rows):
```kotlin
LaunchedEffect(outputRows, selectedOutputKey) {
    val key = selectedOutputKey
    if (key != null && outputRows.none { it.descriptor.objectKey == key }) {
        selectedOutputKey = null
    }
}
```

**BackHandler registration order** (`AppShell.kt` lines 499–543 — ordering is load-bearing):
```kotlin
// Register BEFORE NavHost in AppShell body (lower priority than in-screen handlers inside NavHost):
BackHandler(enabled = drawerOpen) { drawerOpen = false }
// NOTE: the old generic "pop backStack" BackHandler is REMOVED — NavHost owns pop now
BackHandler(enabled = !drawerOpen && nav.scanActive) { nav.scanActive = false }
BackHandler(enabled = !drawerOpen && promptView.visible) { dispatcher?.dispatch(…) }
// The in-screen sub-nav BackHandlers (Macros/Calibration/Outputs/FineTune) remain INSIDE their
// destination composable lambdas and are NOT moved to AppShell level (D-01).
```

**Overlay-above-NavHost pattern** (`AppShell.kt` lines 882–1022):
```kotlin
// Box wraps BOTH NavHost and overlay siblings (later children paint above earlier)
Box(modifier.fillMaxSize().background(t.bg).pointerInput(…) { … }) {
    NavHost(navController, startDestination = NavDest.WaterfallHome) {
        composable<NavDest.WaterfallHome> { PrintStatusScreen(…) }
        composable<NavDest.Move> { MoveScreen(container, holder = moveHolder, onBack = { navController.popBackStack() }) }
        // … remaining destinations …
    }

    // Overlays compose OVER the NavHost — identical to existing AppShell overlay positions:
    val popupMacro = macroPopupFor
    val liveDispatcher = dispatcher
    // MacroExecutionPopup: was gated on dest==Dest.Macros; now check navBackStackEntry
    val isMacros = navBackStackEntry?.destination?.hasRoute<NavDest.Macros>() == true
    if (isMacros && popupMacro != null && liveDispatcher != null) { MacroExecutionPopup(…) }

    if (nav.scanActive) { ScanSurface(…) }
    if (promptView.visible) { PromptDialog(…) }
    if (devCyclerEnabled) { DevThemeCyclerOverlay(…) }
    if (drawerOpen) { AppDrawer(onDestination = { navController.navigate(it) }, onDismiss = { drawerOpen = false }, …) }
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(8.dp).background(t.hair))
}
```

**Pop-to-root on print state change** (new LaunchedEffect below NavHost creation — `AppShell.kt` has no existing analog; pattern from RESEARCH §Pattern 4):
```kotlin
val printState by printerStateFlow.collectAsStateWithLifecycle()
LaunchedEffect(printState.printState) {
    // Only pop if current destination is a foot-gun (D-04)
    val isFootGun = navBackStackEntry?.destination?.let { d ->
        d.hasRoute<NavDest.Move>() || d.hasRoute<NavDest.Extrude>() || d.hasRoute<NavDest.Calibration>()
    } == true
    if (isFootGun) {
        navController.popBackStack<NavDest.WaterfallHome>(inclusive = false)
    }
}
```

**Swipe-suppress set** (`AppShell.kt` lines 554–592 — `dest !in setOf(…)` becomes backstack route checks):
```kotlin
// BEFORE (old enum check):
.pointerInput(dest, promptView.visible) {
    if (!promptView.visible && dest !in setOf(Dest.Files, Dest.Console, …)) { … }
}

// AFTER (key on navBackStackEntry instead of dest):
.pointerInput(navBackStackEntry, promptView.visible) {
    val suppressSwipe = !promptView.visible && run {
        val d = navBackStackEntry?.destination ?: return@run false
        d.hasRoute<NavDest.Files>() || d.hasRoute<NavDest.Console>() || d.hasRoute<NavDest.Macros>()
            || d.hasRoute<NavDest.Calibration>() || d.hasRoute<NavDest.Webcam>()
            || d.hasRoute<NavDest.Spool>() || d.hasRoute<NavDest.Outputs>()
            || d.hasRoute<NavDest.SystemInfo>() || d.hasRoute<NavDest.Devices>()
            || d.hasRoute<NavDest.Theme>() || d.hasRoute<NavDest.Settings>()
            || d.hasRoute<NavDest.About>()
    }
    if (!suppressSwipe) { detectVerticalDragGestures { _, dragAmount ->
        if (dragAmount < -SWIPE_UP_THRESHOLD_PX) drawerOpen = true
    }}
}
```

---

### `ui/shell/ShellNavState.kt` (slim down — remove `dest`/`backStack`/`goBack`)

**Analog:** `ui/shell/ShellNavState.kt` itself (lines 43–152).

**Fields to KEEP** (these survive the migration):
```kotlin
// Lines 55–87: all of these stay
var macroShowSystem by mutableStateOf(false)          // Macros in-screen sub-nav (D-01)
var macroPopupFor by mutableStateOf<MacroVm?>(null)   // TRANSIENT — reset on Splash return
var calibrationRoutine by mutableStateOf<CalibrationRoutine?>(null)  // Calibration in-screen sub-nav
var fineTuneGroup by mutableStateOf<FineTuneGroup?>(null)            // FineTune in-screen sub-nav
var scanActive by mutableStateOf(false)              // TRANSIENT
var spoolPrefilter by mutableStateOf<SpoolPrefilterSeed?>(null)      // TRANSIENT
```

**Fields to REMOVE** (NavHost takes over):
```kotlin
var dest by mutableStateOf(…)          // → NavHost owns startDestination
val backStack = mutableStateListOf<Dest>()  // → navController.backStack
fun navigateTo(target: Dest) { … }     // → navController.navigate(NavDest.*)
fun goBack() { … }                     // → navController.popBackStack()
```

**`applyEntryReset` to keep** (`ShellNavState.kt` lines 109–125) — still fires on same-dest re-selection for in-screen sub-nav resets (Macros/Calibration/FineTune). It now only resets sub-nav state, not the NavHost destination. The caller context changes: instead of `navigateTo(target)` calling `applyEntryReset`, the NavHost's `composable<NavDest.*>` entries call `nav.applyEntryReset(NavDest.*)` from a `LaunchedEffect(Unit)` at the top of each affected destination — OR use NavHost's `onEnter` when available.

**`resetTransient` to keep** (`ShellNavState.kt` lines 137–141):
```kotlin
fun resetTransient() {
    macroPopupFor = null
    scanActive = false
    spoolPrefilter = null
}
```

**`startDest` seed note** (`ShellNavState.kt` lines 43–50): The `startDest: Dest?` constructor param becomes `startDest: NavDest?`, used to set `navController`'s `startDestination` — but `navController` lives in AppShell, not ShellNavState. The seed approach changes: `rememberShellNavState(startDest)` now STORES the seed as a nullable field that AppShell reads ONCE to override the NavHost `startDestination` param. It cannot be a LaunchedEffect (RESEARCH Pitfall 2).

---

### `ui/shell/RootController.kt` (no structural change)

**Analog:** `ui/shell/RootController.kt` itself — the gate-above pattern stays unchanged.

**Gate-above pattern to preserve** (`RootController.kt` lines 115–149):
```kotlin
when {
    rawRoute is TopRoute.Connect || settingsEscape -> {
        PrintersScreen(container = container, …)
    }
    showSplash -> {
        SplashScreen(container = container, hasConfig = hasConfig, state = state, …)
    }
    else -> {
        AppShell(container = container, nav = nav)  // NavHost lives inside AppShell
    }
}
```

**ShellNavState recovery Splash reset** (`RootController.kt` lines 111–113 — stays unchanged):
```kotlin
LaunchedEffect(showSplash) {
    if (!showSplash) nav.resetTransient()
}
```

**Back-stack recovery note** (`RootController.kt` line 69 + RESEARCH Pitfall 5): After migration, `AppShell` decomposes when `showSplash` is true (the NavHost decomposes with it). On return, the NavHost restarts at `WaterfallHome`. This is the **accepted trade-off** — per the CONTEXT.md open question answer: the owner has explicitly accepted landing-on-root after a recovery Splash (the planner does NOT need to preserve the drill-down back-stack across the Splash blip). The in-screen sub-nav state (Calibration routine page, FineTune group) IS still preserved via `ShellNavState`.

---

### `ui/printstatus/PrintStatusScreen.kt` (morphing waterfall root rework)

**Analog:** `ui/printstatus/PrintStatusScreen.kt` itself — the existing `PrintStatusContent` / `classifyPrintStatus` / `ScreenScaffold` patterns are the direct base.

**Existing `PrintStatusContent` pattern** (`PrintStatusScreen.kt` lines 417–540) — the `when(mode)` dispatch and ScreenScaffold slot pattern to copy:
```kotlin
@Composable
private fun PrintStatusContent(mode: PrintStatusMode, …, onNavigate: (Dest) -> Unit, onOpenDrawer: () -> Unit) {
    when (mode) {
        is PrintStatusMode.Standby  -> ScreenScaffold(
            focus = { StandbyFocus(…) },
            field = { PrintStatusStandbyField(ui = ui, onNavigate = onNavigate, onOpenDrawer = onOpenDrawer, …) },
            gutter = { PrintStatusGutter(…) },
        )
        is PrintStatusMode.Printing -> ScreenScaffold(focus = { PrintStatusFocus(…) }, field = { … }, gutter = { … })
        is PrintStatusMode.Paused   -> ScreenScaffold(…)
        is PrintStatusMode.Terminal -> ScreenScaffold(…)
    }
}
```

**Signature update required:** `onNavigate: (Dest) -> Unit` → `onNavigate: (NavDest) -> Unit`. This is the primary seam change. All `onNavigate(Dest.*)` call sites inside the screen become `onNavigate(NavDest.*)`.

**Crossfade morph pattern** (new — no existing Crossfade in the current PrintStatusScreen; pattern from RESEARCH §Pattern 7):
```kotlin
// Wrap the when(mode) block in Crossfade for the one-shot ~150ms transition (D-13):
Crossfade(
    targetState = classifyPrintStatus(state),
    animationSpec = tween(durationMillis = 150),
    label = "PrintStatusMorph",
) { mode ->
    when (mode) {
        is PrintStatusMode.Standby  -> StandbyContent(…)
        is PrintStatusMode.Printing -> PrintingContent(…)
        is PrintStatusMode.Paused   -> PausedContent(…)
        is PrintStatusMode.Terminal -> TerminalContent(…)
    }
}
```

**FloatingEStop overlay placement** (`designsystem/components/FloatingEStop.kt` lines 17–29 — the Box-sibling rule):
```kotlin
// FloatingEStop.kt KDoc (lines 17–29) — the load-bearing placement rule:
// Box sibling of Focus content, NOT inside the Focus Column:
Box(Modifier.fillMaxWidth().weight(1f)) {
    DetailCard(ringColor = spoolColor, modifier = Modifier.fillMaxSize()) { … }
    FloatingEStop(
        visible = isPrinting,
        onClick = onEmergencyStop,
        uDp = grid.uDp,
        modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
    )
}
```

**`FloatingEStop` signature** (`FloatingEStop.kt` lines 52–69):
```kotlin
FloatingEStop(
    visible  = mode is PrintStatusMode.Printing || mode is PrintStatusMode.Paused,
    onClick  = { showEstopGuard = true },  // existing ConfirmGuard wiring stays
    uDp      = grid.uDp,
    modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
)
```

**Idle action list (new Field content for Standby mode)** — uses Phase-23 `ListBlock` + `ListRow` + `FootButtonBar` components:

`ListBlock` pattern (`designsystem/layout/ListBlock.kt` lines 56–63 + 71–132):
```kotlin
ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
    items(idleActions, key = { it::class.simpleName }) { action ->
        ListRow(
            selected = false,
            onClick = { when (action) {
                is HomeAction.Destination -> onNavigate(action.dest)
                is HomeAction.OpenDrawer  -> onOpenDrawer()
            }},
            uDp = grid.uDp,
        ) { /* row content: icon + label */ }
    }
}
```

`FootButtonBar` pattern for the idle foot bar (`FootButtonBar.kt` lines 65–79 + KDoc lines 28–50):
```kotlin
// "System" foot button opens the existing App Drawer (D-09/D-10):
FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(8.dp)) {
    OutlinedControl(
        label = stringResource(R.string.home_foot_preheat),
        onClick = onPreheat,
        modifier = Modifier.weight(1f),
        icon = DinghyIcons.…,
        intent = Intent.Neutral,
    )
    OutlinedControl(
        label = stringResource(R.string.home_foot_system),
        onClick = onOpenDrawer,   // "System" = neutral intent (D-09), opens drawer
        modifier = Modifier.weight(1f),
        icon = DinghyIcons.…,
        intent = Intent.Neutral,  // NOT Intent.Danger — "System" is NOT red (D-09)
    )
}
```

**UnitGrid setup** (`UnitGrid.kt` lines 44–50 + 107–109 — call-site contract):
```kotlin
BoxWithConstraints(Modifier.fillMaxSize()) {
    val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
    // use grid.uDp as the height of each list row, control tile, etc.
}
```

---

### `gradle/libs.versions.toml` + `app/build.gradle.kts` (navigation-compose dependency)

**Analog:** existing `libs.versions.toml` version + library entry pattern (e.g. `okhttp = "4.12.0"` block).

**toml additions** (RESEARCH §Installation):
```toml
[versions]
navigation = "2.8.9"     # navigation-compose 2.8.x — minSdk 21, safe on AGP 8.7/compileSdk 36

[libraries]
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
```

**build.gradle.kts addition** (place alongside existing `implementation(libs.androidx.…)` lines):
```kotlin
implementation(libs.androidx.navigation.compose)
```

**No new plugin needed** — `kotlin-serialization` plugin is already declared. Navigation 2.8.9 uses `kotlinx.serialization` (already in the project) for type-safe routes.

---

### Test files

#### `test/ui/route/HomeActionTest.kt` (new)

**Analog:** `test/ui/route/TopRouteTest.kt` (full file — pure JUnit4, no Compose, no I/O).

**Test structure to copy** (`TopRouteTest.kt` lines 22–136):
```kotlin
class HomeActionTest {
    // Test the hide rules for all 4 capability combinations (D-08)

    @Test fun spoolmanAbsent_hidesSpool() { … }
    @Test fun spoolmanPresent_showsSpool() { … }
    @Test fun bookmarksEmpty_hidesMacros() { … }
    @Test fun bookmarksNonEmpty_showsMacros() { … }
    @Test fun outputsAbsent_hidesOutputs() { … }
    @Test fun webcamDisabled_hidesWebcam() { … }
    @Test fun allCapabilitiesPresent_fullIdleList() { … }
    @Test fun allCapabilitiesAbsent_minimalIdleList() { … }
    @Test fun v1OrderIsPreserved() {
        // Spool → File → Move → Extrude → Macros → Calibration → Outputs → Webcam (D-06)
    }
}
```

#### `test/ui/route/PopToRootTest.kt` (new)

**Analog:** `test/ui/route/TopRouteTest.kt` for test structure; uses `TestNavController` from `androidx.navigation:navigation-testing`.

**Test pattern:**
```kotlin
class PopToRootTest {
    private lateinit var navController: TestNavController

    @Before fun setUp() {
        navController = TestNavController(…)
        navController.setGraph(…NavDest…)
    }

    @Test fun printingToMove_printEnds_popsToRoot() {
        navController.navigate(NavDest.Move)
        // simulate printState change to Standby
        navController.popBackStack<NavDest.WaterfallHome>(inclusive = false)
        assertEquals(NavDest.WaterfallHome, navController.currentDestination?.…)
    }

    @Test fun printingToTemperature_printEnds_staysOnTemperature() {
        navController.navigate(NavDest.Temperature)
        // Temperature is NOT a foot-gun — should not pop
        assertFalse(navController.popBackStack<NavDest.WaterfallHome>(inclusive = false))
    }
}
```

Note: `TestNavController` requires `androidx.navigation:navigation-testing` — add this as a `testImplementation` in `build.gradle.kts`. Check whether 2.8.9 has a matching `-testing` artifact (it does: `navigation-testing:2.8.9`).

---

## Shared Patterns

### Session holder hoist + leak-cancel
**Source:** `AppShell.kt` lines 186–489 (all 20 holders)
**Apply to:** NavHost migration in `AppShell.kt`

The invariant: every `remember(store)` or `remember(store, key2)` holder block must appear ABOVE the `NavHost(…)` call. Any holder with a `DisposableEffect(holder) { onDispose { holder.cancel() } }` must keep that cancel — these are load-bearing leak-guards.

```kotlin
// Pattern: holder with cancel (copy from AppShell.kt lines 309-313)
val spoolHolder = remember(store) { SpoolHolder(scope = scope, …) }
DisposableEffect(spoolHolder) { onDispose { spoolHolder.cancel() } }

// Pattern: holder without cancel (copy from AppShell.kt lines 199-201)
val temperatureHolder = remember(store) { TemperatureHolder(scope = scope, store = store) }
```

### BackHandler priority ordering
**Source:** `AppShell.kt` lines 499–543
**Apply to:** `AppShell.kt` post-migration

Registration order = priority (last registered = first served). After migration:
1. AppShell-level handlers (drawer, scan, prompt) register BEFORE the `NavHost` call → lowest priority
2. NavHost's own back handler (registered implicitly) → middle priority
3. In-screen sub-nav BackHandlers inside `composable<NavDest.*>` lambdas → highest priority (innermost)

```kotlin
// Load-bearing order — AppShell body BEFORE NavHost { … }:
BackHandler(enabled = drawerOpen) { drawerOpen = false }
BackHandler(enabled = !drawerOpen && nav.scanActive) { nav.scanActive = false }
BackHandler(enabled = !drawerOpen && promptView.visible) { dispatcher?.dispatch(promptEngine.closeKey, …) }
```

### Token compliance
**Source:** `ListRow.kt` lines 101–122; `FootButtonBar.kt` lines 70–79; `FloatingEStop.kt` lines 60–68
**Apply to:** All new UI in `PrintStatusScreen.kt`

```kotlin
// THEME-01: all colors via LocalTokens.current — never raw Color(0x…)
val t = LocalTokens.current
val bgColor = if (selected) t.accentSoft else Color.Transparent
val borderColor = if (selected) t.accentLine else t.outline
// Font sizes via fsSp(baseSp, t.fs).sp — never bare .sp
fontSize = fsSp(15f, t.fs).sp  // metadata floor
```

### ConfirmGuard overlay for E-stop
**Source:** `PrintStatusScreen.kt` lines 292–305 (existing estop guard wiring):
```kotlin
// Box sibling above PrintStatusContent (same as existing cancel/stop guards):
if (showEstopGuard) {
    ConfirmGuard(
        title = stringResource(R.string.printstatus_estop_guard_title),
        message = …,
        confirmLabel = …,
        cancelLabel = …,
        onConfirm = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit); showEstopGuard = false },
        onCancel = { showEstopGuard = false },
        destructive = true,
    )
}
```

### collectAsStateWithLifecycle pattern
**Source:** `AppShell.kt` lines 187–215 (multiple examples)
**Apply to:** all new flow collections

```kotlin
val spine       by container.spine.collectAsStateWithLifecycle()
val webcamCount by container.webcamCount.collectAsStateWithLifecycle(initialValue = 0)
val spoolEnabled by container.spoolmanPresent.collectAsStateWithLifecycle(initialValue = false)
```

---

## Migration Critical Paths

### Dest enum → NavDest sealed objects (Pitfall 6)

The `Dest` enum appears in ~93 call sites across main source (93 grep hits excluding AppShell, TopRoute, ShellNavState). Files to update include:
- `AppDrawer.kt` — `DrawerTileSpec(dest = Dest.*)` → `DrawerTileSpec(dest = NavDest.*)`
- `PrintStatusScreen.kt` — `onNavigate: (Dest) -> Unit` → `onNavigate: (NavDest) -> Unit`
- `StartDestMapping.kt` — `Dest.entries.firstOrNull { it.name == name }` → exhaustive `when` or `knownNavDests` list
- `StartDestMappingTest.kt` — update test for `NavDest` cases
- `TopRouteTest.kt` — `TopRoute.Shell(Dest.PrintStatus)` → `TopRoute.Shell` (if `Shell` no longer carries `dest`)
- All screen `onBack` lambdas that use `Dest.*` for navigation

**Strategy:** Wave 0 mechanical rename. After the rename, `TopRoute.Shell` no longer needs a `dest: NavDest` payload (the NavHost owns startDestination). Update `TopRoute.kt` accordingly.

### `ShellNavState.navigateTo(Dest)` call sites

`navigateTo(target)` calls appear throughout AppShell and PrintStatusScreen (`navigateTo(Dest.*)`) — these all become `navController.navigate(NavDest.*)`. The `goBack()` calls become `navController.popBackStack()`. `applyEntryReset(target)` remains but must be called differently (see ShellNavState section above).

---

## No Analog Found

| File / Seam | Role | Data Flow | Reason |
|---|---|---|---|
| `NavHost { composable<NavDest.*> { … } }` block | nav graph | request-response | No NavHost exists in the codebase yet; use RESEARCH §Pattern 1 as the template |
| `navController.popBackStack<NavDest.WaterfallHome>(inclusive = false)` | pop-to-root | event-driven | No existing NavHost pop logic; use RESEARCH §Pattern 4 |
| `navigation-testing:2.8.9` (TestNavController) | test infra | — | No existing nav-testing in the project; add `testImplementation(libs.androidx.navigation.testing)` alongside the main dep |

---

## Metadata

**Analog search scope:**
- `app/src/main/java/works/mees/dinghy/ui/shell/` (AppShell, RootController, ShellNavState, AppDrawer, StartDestMapping)
- `app/src/main/java/works/mees/dinghy/ui/route/` (TopRoute, Dest)
- `app/src/main/java/works/mees/dinghy/ui/printstatus/` (PrintStatusScreen, PrintStatusMode)
- `app/src/main/java/works/mees/dinghy/designsystem/components/` (FloatingEStop, FootButtonBar, ListRow, FillMeter)
- `app/src/main/java/works/mees/dinghy/designsystem/layout/` (UnitGrid, ListBlock)
- `app/src/test/java/works/mees/dinghy/ui/route/` (TopRouteTest, PrintStatusModeTest)
- `gradle/libs.versions.toml` + `app/build.gradle.kts`

**Files scanned:** ~25 source files read directly + grep searches across full source tree
**Pattern extraction date:** 2026-06-10
