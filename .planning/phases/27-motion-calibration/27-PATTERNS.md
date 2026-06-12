# Phase 27: Motion + Calibration — Pattern Map

**Mapped:** 2026-06-12
**Files analyzed:** 11 new/modified files
**Analogs found:** 11 / 11

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `ui/move/MoveScreen.kt` | component (screen) | request-response | `ui/move/MoveScreen.kt` (self — migrate-in-place) + `ui/calibration/ProbeCalibrateScreen.kt` (vertical col motif) | exact |
| `ui/calibration/CalibrationHubScreen.kt` | component (screen) | request-response | `ui/spool/SpoolScreen.kt` (list+Focus+DetailCard pilot) | exact |
| `ui/calibration/ProbeCalibrateScreen.kt` | component (screen) | event-driven (state machine) | `ui/calibration/ProbeCalibrateScreen.kt` (self — migrate-in-place) + `ui/temperature/TemperatureScreen.kt` (FootButtonBar state-adaptive) | exact |
| `ui/calibration/BedMeshScreen.kt` | component (screen) | CRUD + request-response | `ui/temperature/TemperatureScreen.kt` (FieldMode takeover pattern) + `ui/spool/SpoolScreen.kt` (list+selection) | exact |
| `ui/calibration/ScrewsTiltScreen.kt` | component (screen) | request-response | `ui/calibration/ScrewsTiltScreen.kt` (self — migrate-in-place, pure restyle) | exact |
| `ui/calibration/TiltScreen.kt` | component (screen) | request-response | `ui/calibration/TiltScreen.kt` (self — migrate-in-place, pure restyle) | exact |
| `ui/route/NavDest.kt` | route config | — | `ui/route/NavDest.kt` (self — extend existing sealed interface) | exact |
| `ui/shell/AppShell.kt` | shell / orchestrator | — | `ui/shell/AppShell.kt` (self — replace `when(calibrationRoutine)` block + BackHandler) | exact |
| `ui/shell/ShellNavState.kt` | state holder | — | `ui/shell/ShellNavState.kt` (self — remove `calibrationRoutine` field + applyEntryReset update) | exact |
| `preview/CalibrationPreviews.kt` | preview | — | `preview/MovePreviews.kt` or any existing `*Previews.kt` | role-match |
| `preview/MovePreviews.kt` | preview | — | any existing `*Previews.kt` | role-match |

---

## Pattern Assignments

---

### `ui/route/NavDest.kt` (route config — D-07 structural change)

**Analog:** `ui/route/NavDest.kt` (self)

**Current pattern to extend** (lines 23–41 — the sealed interface + objects block):
```kotlin
// NavDest.kt lines 23–41 (current)
@Serializable
sealed interface NavDest {
    @Serializable data object WaterfallHome  : NavDest
    @Serializable data object Temperature    : NavDest
    @Serializable data object Move           : NavDest
    @Serializable data object Extrude        : NavDest
    @Serializable data object Files          : NavDest
    @Serializable data object Macros         : NavDest
    @Serializable data object Console        : NavDest
    @Serializable data object Calibration    : NavDest   // → becomes CalibrationHub (D-07)
    @Serializable data object FineTune       : NavDest
    // … etc
}
```

**Target pattern after D-07 (Option A — add 6 sub-dests, rename Calibration → CalibrationHub):**
```kotlin
// Replace NavDest.Calibration with these 6 members:
@Serializable data object CalibrationHub        : NavDest
@Serializable data object CalibrationProbe      : NavDest
@Serializable data object CalibrationBedMesh    : NavDest
@Serializable data object CalibrationScrewsTilt : NavDest
@Serializable data object CalibrationZTilt      : NavDest
@Serializable data object CalibrationQgl        : NavDest
```

**`knownNavDests` list** (lines 49–67) — must add all 6 new members, replacing the single `NavDest.Calibration` entry.

**`FOOT_GUN_DESTS` current** (lines 84–88):
```kotlin
// NavDest.kt lines 84–88 (current)
val FOOT_GUN_DESTS: Set<NavDest> = setOf(
    NavDest.Move,
    NavDest.Extrude,
    NavDest.Calibration,   // → expand to all 6 CalibrationXxx members
)
```

**Target `FOOT_GUN_DESTS` after D-07:**
```kotlin
val FOOT_GUN_DESTS: Set<NavDest> = setOf(
    NavDest.Move,
    NavDest.Extrude,
    NavDest.CalibrationHub,
    NavDest.CalibrationProbe,
    NavDest.CalibrationBedMesh,
    NavDest.CalibrationScrewsTilt,
    NavDest.CalibrationZTilt,
    NavDest.CalibrationQgl,
)
```

**`shouldPopToRoot` predicate** (lines 107–108) — no change needed; it checks `current in FOOT_GUN_DESTS` which covers the expanded set automatically.

---

### `ui/shell/ShellNavState.kt` (state holder — D-07 cleanup)

**Analog:** `ui/shell/ShellNavState.kt` (self)

**Field to remove** (line 56):
```kotlin
// REMOVE this field — NavHost back-stack replaces it after D-07
var calibrationRoutine by mutableStateOf<CalibrationRoutine?>(null)
```

**`applyEntryReset` update** (lines 82–92 — current):
```kotlin
// ShellNavState.kt lines 82–92 (current)
internal fun applyEntryReset(target: NavDest) {
    if (target == NavDest.Macros) {
        macroShowSystem = false
        macroPopupFor = null
    }
    if (target == NavDest.Calibration) {   // ← update to NavDest.CalibrationHub
        calibrationRoutine = null           // ← REMOVE this line
    }
}
```

After D-07 the `applyEntryReset(NavDest.CalibrationHub)` call remains in AppShell's `LaunchedEffect(Unit)` block for the hub composable — but the body no longer mutates `calibrationRoutine` (removed field).

**Grep before declaring complete:** `calibrationRoutine` appears in ShellNavState.kt line 56, AppShell.kt lines 56, 183–184, 511–514, 696–729. All sites must be cleaned up.

---

### `ui/shell/AppShell.kt` (shell — D-07 route wiring + D-09 BackHandler)

**Analog:** `ui/shell/AppShell.kt` (self) + P24's composable block pattern for non-calibration screens.

**Current `when(calibrationRoutine)` block to replace** (lines 687–730):
```kotlin
// AppShell.kt lines 687–730 (current — DELETED in P27)
composable<NavDest.Calibration> {
    LaunchedEffect(Unit) { nav.applyEntryReset(NavDest.Calibration) }
    when (val routine = calibrationRoutine) {
        null -> CalibrationHubScreen(
            holder = calibrationHubHolder,
            onNavigate = { nav.calibrationRoutine = it },
            onBack = { navController.popBackStack() },
        )
        CalibrationRoutine.PROBE_CALIBRATE -> ProbeCalibrateScreen(
            container = container,
            holder = probeCalibrateHolder,
            onBack = { nav.calibrationRoutine = null },
        )
        // … other routines
    }
}
```

**Target pattern after D-07 (6 separate composable<> blocks):**
```kotlin
// AppShell.kt — replacement block (add after existing composable<NavDest.Extrude>)
composable<NavDest.CalibrationHub> {
    LaunchedEffect(Unit) { nav.applyEntryReset(NavDest.CalibrationHub) }
    CalibrationHubScreen(
        holder = calibrationHubHolder,
        onOpen = { routine ->
            navController.navigate(routine.toNavDest())  // helper mapping CalibrationRoutine → NavDest
        },
        onBack = { navController.popBackStack() },
    )
}

composable<NavDest.CalibrationProbe> {
    val vm by probeCalibrateHolder.vm.collectAsStateWithLifecycle()
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val starting = vm.state == ProbePageState.Idle &&
        ("probe_calibrate" in inFlight || "z_endstop_calibrate" in inFlight)
    // D-09: suppress system Back while session is Active OR starting (Pitfall 6)
    BackHandler(enabled = vm.state == ProbePageState.Active || starting) {
        // swallow — user must Accept or Abort
    }
    ProbeCalibrateScreen(
        container = container,
        holder = probeCalibrateHolder,
        onBack = { navController.popBackStack() },
    )
}

composable<NavDest.CalibrationBedMesh> {
    BedMeshScreen(
        container = container,
        holder = bedMeshHolder,
        onBack = { navController.popBackStack() },
    )
}

composable<NavDest.CalibrationScrewsTilt> {
    ScrewsTiltScreen(
        container = container,
        holder = screwsTiltHolder,
        onBack = { navController.popBackStack() },
    )
}

composable<NavDest.CalibrationZTilt> {
    TiltScreen(
        container = container,
        holder = zTiltHolder,
        variant = TiltVariant.ZTilt,
        onBack = { navController.popBackStack() },
    )
}

composable<NavDest.CalibrationQgl> {
    TiltScreen(
        container = container,
        holder = qglHolder,
        variant = TiltVariant.Qgl,
        onBack = { navController.popBackStack() },
    )
}
```

**BackHandler to remove** (lines 511–515 — the old in-screen calibration Back):
```kotlin
// REMOVE — replaced by NavHost back-stack + composable<NavDest.CalibrationProbe> BackHandler
BackHandler(
    enabled = !drawerOpen && navBackStackEntry?.destination?.isRoute<NavDest.Calibration>() == true && calibrationRoutine != null
) {
    nav.calibrationRoutine = null
}
```

**Imports to add:** All 6 new NavDest objects + `ProbePageState` + updated CalibrationHubScreen signature.

---

### `ui/move/MoveScreen.kt` (component — specialized-layout exemption, D-01..D-04)

**Analog:** `ui/move/MoveScreen.kt` (self — migrate-in-place) + `ui/calibration/ProbeCalibrateScreen.kt` (vertical 3-cell column motif)

**Imports pattern** (current lines 1–55 — keep most; add):
```kotlin
import androidx.compose.foundation.layout.BoxWithConstraints
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.designsystem.layout.ListBlock  // NOT needed for Move
import androidx.compose.ui.res.stringResource
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
```

**Screen root pattern — BoxWithConstraints for manual sizing** (D-03, specialized exemption; `portraitFocusAspect` NOT used — Box-level manual sizing instead):
```kotlin
// MoveScreen.kt — new screen root (replaces current ScreenScaffold)
BoxWithConstraints(modifier.fillMaxSize()) {
    val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
    val landscape = maxWidth > maxHeight

    // Portrait: JogPad capped at 60% of HEIGHT; remaining height = Z col + distance col + foot bar
    // Landscape: JogPad fills full height; Z col + distance col sit beside pad in the other half
    if (landscape) {
        Row(Modifier.fillMaxSize()) {
            // Left half: JogPad (existing, unchanged internals per D-04)
            JogPad(…, modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp))
            // Right half: Z column + distance stepper column + FootButtonBar
            Column(Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZColumn(…, modifier = Modifier.weight(1f).fillMaxHeight())
                    DistanceStepperColumn(…, modifier = Modifier.weight(1f).fillMaxHeight())
                }
                FootButtonBar(uDp = grid.uDp) { /* Home All | Disable | Back */ }
            }
        }
    } else {
        val padSize = (maxHeight * 0.60f).coerceAtMost(maxWidth)
        Column(Modifier.fillMaxSize()) {
            // Top: JogPad at padSize × padSize, centered
            Box(Modifier.fillMaxWidth().height(padSize), contentAlignment = Alignment.Center) {
                JogPad(…, modifier = Modifier.size(padSize).padding(8.dp))
            }
            // Bottom: Z col + distance col + FootButtonBar
            Row(Modifier.fillMaxWidth().weight(1f).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZColumn(…, modifier = Modifier.weight(1f).fillMaxHeight())
                DistanceStepperColumn(…, modifier = Modifier.weight(1f).fillMaxHeight())
            }
            FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                /* Home All | Disable | Back */
            }
        }
    }
    // ConfirmGuard for Disable (existing — keep as-is)
}
```

**Vertical 3-cell column motif (D-01, D-02) — copy from `ProbeCalibrateScreen.kt` lines 421–439 (step column) and lines 441–461 (Z nudge column):**
```kotlin
// ProbeCalibrateScreen.kt lines 421–439 — the EXACT pattern both new columns follow
Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    ProbeIconButton(
        glyphName = "add",
        contentDescription = "Larger step",
        onClick = { onSelectStep(TESTZ_STEPS[(idx + 1).coerceAtMost(TESTZ_STEPS.lastIndex)]) },
        modifier = Modifier.weight(1f).fillMaxWidth(),
        intent = Intent.Neutral,
        enabled = idx < TESTZ_STEPS.lastIndex,
    )
    StepDisplay(value = step, modifier = Modifier.weight(1f).fillMaxWidth())
    ProbeIconButton(
        glyphName = "remove",
        contentDescription = "Smaller step",
        onClick = { onSelectStep(TESTZ_STEPS[(idx - 1).coerceAtLeast(0)]) },
        modifier = Modifier.weight(1f).fillMaxWidth(),
        intent = Intent.Neutral,
        enabled = idx > 0,
    )
}
```

**Distance stepper column (D-02 — vertical 3-cell cycling `DISTANCES`):**
```kotlin
// Adapted from ProbeCalibrateScreen step-column pattern
private val DISTANCES = listOf(0.1, 1.0, 10.0, 25.0, 50.0, 100.0)  // existing constant

@Composable
private fun DistanceStepperColumn(
    distance: Double,
    onSelect: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val idx = DISTANCES.indexOf(distance).coerceAtLeast(0)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedControl(
            label = "",
            icon = DinghyIcons.BabystepExpand,  // or owner-approved + glyph — ASK per D-16
            contentDescription = stringResource(R.string.move_distance_increase),
            onClick = { onSelect(DISTANCES[(idx + 1).coerceAtMost(DISTANCES.lastIndex)]) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            intent = Intent.Neutral,
            enabled = idx < DISTANCES.lastIndex,
        )
        StepDisplay(value = distance, modifier = Modifier.weight(1f).fillMaxWidth())
        OutlinedControl(
            label = "",
            icon = DinghyIcons.BabystepCompress,  // or owner-approved − glyph — ASK per D-16
            contentDescription = stringResource(R.string.move_distance_decrease),
            onClick = { onSelect(DISTANCES[(idx - 1).coerceAtLeast(0)]) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            intent = Intent.Neutral,
            enabled = idx > 0,
        )
    }
}
```

**Z column (D-01 — vertical 3-cell; uses `t.directional.z` outline per UI-SPEC):**
```kotlin
// Adapted from ProbeCalibrateScreen Z-nudge column (lines 441–461)
@Composable
private fun ZColumn(
    zHomed: Boolean,
    zValue: Double,
    inFlight: Set<String>,
    forceMove: Boolean,
    onJogZ: (Double) -> Unit,
    onHomeZ: () -> Unit,
    distance: Double,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Z+ jog (top cell) — t.directional.z outline
        JogCell(
            symbol = "expand",  // existing JogCell private composable — keep
            onClick = { onJogZ(distance) },
            disabled = jogDisabledZ(zHomed, "jog_Z" in inFlight, forceMove),
            forceMove = forceMove,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // Z column outline: t.directional.z (overrides JogCell's xy default — planner decides exact override)
        )
        // Z home + live readout (center cell — Geist Mono tabular, Modifier.weight(1f))
        HomeZCell(
            zHomed = zHomed,
            zValue = zValue,
            onHomeZ = onHomeZ,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        // Z- jog (bottom cell)
        JogCell(
            symbol = "compress",
            onClick = { onJogZ(-distance) },
            disabled = jogDisabledZ(zHomed, "jog_Z" in inFlight, forceMove),
            forceMove = forceMove,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}
```

**FootButtonBar for Move (replaces gutter rows at lines 204–233):**
```kotlin
// Current gutter pattern (lines 204–233) → FootButtonBar inside field/column
FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
    OutlinedControl(
        label = stringResource(R.string.move_home_all),
        onClick = { dispatchCommand(CommandRegistry.homeAll, Unit) },
        modifier = Modifier.weight(1f),
        intent = Intent.Accent,
        icon = DinghyIcons.LauncherMove,  // or owner-confirmed home-all icon
    )
    OutlinedControl(
        label = stringResource(R.string.move_disable_steppers),
        onClick = { showDisableGuard = true },
        modifier = Modifier.weight(1f),
        intent = Intent.Danger,
    )
    OutlinedControl(
        label = "",
        onClick = onBack,
        modifier = Modifier.weight(1f),
        intent = Intent.Neutral,
        icon = DinghyIcons.Back,
        contentDescription = stringResource(R.string.common_back),
    )
}
```

**Gutter MUST be removed:** `gutter = null` — all actions move to FootButtonBar as above.

**JogPad internals carry over unchanged (D-04):** Lines 254–307 are untouched. Only the wrapping screen layout changes.

---

### `ui/calibration/CalibrationHubScreen.kt` (component — list+Focus, D-05)

**Analog:** `ui/spool/SpoolScreen.kt` (list+Focus+DetailCard pilot)

**Imports pattern** (from SpoolScreen.kt lines 1–83 — adapt for Calibration):
```kotlin
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.designsystem.components.DetailCard
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.FloatingEStop
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
```

**Screen signature (replacing current lines 58–107):**
```kotlin
// CalibrationHubScreen.kt — new signature
@Composable
fun CalibrationHubScreen(
    holder: CalibrationHubHolder,
    onOpen: (CalibrationRoutine) -> Unit,  // now calls navController.navigate(NavDest.CalibrationXxx)
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val routines by holder.routines.collectAsStateWithLifecycle()
    val t = LocalTokens.current
    var selected by remember { mutableStateOf<CalibrationRoutine?>(null) }
    // D-05: pre-select first so Focus is never empty on load
    LaunchedEffect(routines) {
        if (selected == null) selected = routines.firstOrNull()?.routine
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        Box(Modifier.fillMaxSize()) {
            ScreenScaffold(
                focus = {
                    selected?.let { routine ->
                        DetailCard(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            HubRoutineFocus(
                                routine = routine,
                                onOpen = { onOpen(routine) },
                                grid = grid,
                                t = t,
                            )
                        }
                    }
                },
                field = {
                    // ListBlock suppresses swipe-up drawer automatically (scrollable Field)
                    ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        items(routines, key = { it.routine.name }) { entry ->
                            ListRow(
                                selected = entry.routine == selected,
                                onClick = { selected = entry.routine },
                                uDp = grid.uDp,
                                leadingContent = {
                                    // DinghyIconView — NOT raw MaterialSymbol (D-16 / anti-pattern)
                                    DinghyIconView(
                                        icon = routineIconToken(entry.routine),  // Wave-0 gate: owner registers
                                        tint = if (entry.isSupported) t.accent2 else t.text3,
                                        sizeDp = grid.uDp * 0.5f,
                                    )
                                },
                            ) {
                                Text(
                                    text = entry.routine.localizedLabel(),  // use stringResource
                                    color = if (entry.isSupported) t.text else t.text3,
                                    fontSize = fsSp(16f, t.fs).sp,
                                )
                            }
                        }
                    }
                    FootButtonBar(uDp = grid.uDp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        OutlinedControl(
                            label = "",
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Neutral,
                            icon = DinghyIcons.Back,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                gutter = null,   // LAW: always null on rebuilt screens
            )
            FloatingEStop(…)  // UAT-4: top-left corner reservation
        }
    }
}
```

**D-06 greyed-but-listed rendering (supported vs unsupported — per current `RoutineTile` logic at lines 116–148):**
```kotlin
// Current RoutineTile (lines 116–120) provides the color logic to carry forward:
val contentColor = if (supported) t.text else t.text3
val outline = if (supported) t.accentLine else t.hair
// → In ListRow: color = if (entry.isSupported) t.text else t.text3 (same semantic, kit component)
```

**Hub Focus `DetailCard` content pattern (from SpoolScreen Detail pattern):**
```kotlin
// DetailCard from designsystem/components/DetailCard.kt — call site:
DetailCard(modifier = Modifier.fillMaxSize().padding(8.dp)) {
    // icon (UAT-1: ~70-80% of U, owner-confirmed glyph via routineIconToken())
    DinghyIconView(icon = routineIconToken(routine), sizeDp = grid.uDp * 0.75f,
        modifier = Modifier.align(Alignment.CenterHorizontally))
    // title
    Text(text = routine.localizedTitle(), fontSize = fsSp(20f, t.fs).sp,
        fontWeight = FontWeight.SemiBold)
    // description (author-written copy, D-05)
    Text(text = routine.localizedDescription(), fontSize = fsSp(15f, t.fs).sp)
    // Open button (accent)
    OutlinedControl(
        label = stringResource(R.string.calibration_open_routine),
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        intent = Intent.Accent,
    )
}
```

---

### `ui/calibration/ProbeCalibrateScreen.kt` (component — vertical TESTZ cols, D-08/D-09)

**Analog:** `ui/calibration/ProbeCalibrateScreen.kt` (self) — the existing `ProbeJogPad` already has the 2-column pattern; D-08 adds a `StepDisplay` center cell to the Z-nudge column.

**Screen signature is unchanged** (lines 100–104):
```kotlin
fun ProbeCalibrateScreen(
    container: AppContainer,
    holder: ProbeCalibrateHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**The `starting` state — COPY VERBATIM** (lines 122–126):
```kotlin
val inFlight by remember(dispatcher) {
    dispatcher?.inFlight ?: MutableStateFlow(emptySet())
}.collectAsStateWithLifecycle(initialValue = emptySet())
val starting = vm.state == ProbePageState.Idle &&
    ("probe_calibrate" in inFlight || "z_endstop_calibrate" in inFlight)
```

**Current `ProbeJogPad` (lines 421–462) — basis for D-08 enhancement.** Add a live-Z readout `StepDisplay` to the Z-nudge column's CENTER cell (currently 2-cell; D-08 makes it 3-cell):
```kotlin
// ProbeCalibrateScreen.kt lines 441–461 (current Z-nudge column — becomes 3-cell per D-08)
Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    ProbeIconButton(                         // top: Z-up (TESTZ +step)
        glyphName = "arrow_upward",
        modifier = Modifier.weight(1f).fillMaxWidth(),
        intent = Intent.Accent,
        enabled = enabled,
    )
    // D-08 ADDS: center cell = live Z readout (Geist Mono, t.directional.z outline)
    ZReadoutDisplay(                         // NEW center cell
        zValue = vm.z,
        modifier = Modifier.weight(1f).fillMaxWidth(),
    )
    ProbeIconButton(                         // bottom: Z-down (TESTZ -step)
        glyphName = "arrow_downward",
        modifier = Modifier.weight(1f).fillMaxWidth(),
        intent = Intent.Accent,
        enabled = enabled,
    )
}
```

**State-adaptive FootButtonBar (D-09 — replaces gutter `when(vm.state)` at lines 162–255):**
```kotlin
// COPY the state branches from lines 167–255 verbatim — only the container changes:
// OLD: Row { when (vm.state) { … ProbeGutterButton(…) … } }
// NEW: FootButtonBar(uDp = grid.uDp) { when (vm.state) { … OutlinedControl(…) … } }

FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
    when (vm.state) {
        ProbePageState.Idle -> if (starting) {
            OutlinedControl("Starting…", {}, Modifier.weight(1f), Intent.Neutral, enabled = false)
        } else if (!vm.homedGate) {
            OutlinedControl(stringResource(R.string.calibration_home_all),
                { dispatcher?.dispatch(CommandRegistry.homeAll, Unit) },
                Modifier.weight(1f), Intent.Accent)
            OutlinedControl("", onBack, Modifier.weight(1f), Intent.Neutral, icon = DinghyIcons.Back)
        } else {
            OutlinedControl(stringResource(R.string.calibration_start), { /* start cmd */ },
                Modifier.weight(1f), Intent.Accent)
            OutlinedControl("", onBack, Modifier.weight(1f), Intent.Neutral, icon = DinghyIcons.Back)
        }
        ProbePageState.Active -> {
            // Back SUPPRESSED — NavHost-level BackHandler in AppShell handles system Back (D-09)
            OutlinedControl(stringResource(R.string.calibration_accept),
                { dispatcher?.dispatch(CommandRegistry.accept, Unit) },
                Modifier.weight(1f), Intent.Go)
            OutlinedControl(stringResource(R.string.calibration_abort),
                { holder.markAborted(); dispatcher?.dispatch(CommandRegistry.abort, Unit) },
                Modifier.weight(1f), Intent.Danger)
        }
        ProbePageState.Accepted -> {
            OutlinedControl(stringResource(R.string.calibration_save_config),
                { saveGuard = true }, Modifier.weight(1f), Intent.Warn)
            OutlinedControl("", onBack, Modifier.weight(1f), Intent.Neutral, icon = DinghyIcons.Back)
        }
    }
}
```

**Gutter must become `gutter = null`** (current line 162: `gutter = { Row(…) { … } }` → deleted).

**`LaunchedEffect(Unit) { holder.reset() }` MUST remain** (line 112) — per Pitfall 3 analysis, BedMesh does NOT have reset() but Probe does; keep this call.

---

### `ui/calibration/BedMeshScreen.kt` (component — Field list + MeshFieldMode takeover, D-11..D-14)

**Analog:** `ui/temperature/TemperatureScreen.kt` (FieldMode sealed class takeover) + `ui/spool/SpoolScreen.kt` (list+selection in Field)

**`MeshFieldMode` sealed class (D-11/D-13) — copy structure from `TempFieldMode` (TemperatureScreen.kt lines 91–94):**
```kotlin
// TemperatureScreen.kt lines 91–93 — exact structural model
private sealed class TempFieldMode {
    data object SensorList : TempFieldMode()
    data object PresetPicker : TempFieldMode()
}

// BedMeshScreen.kt — adapt as:
private sealed class MeshFieldMode {
    data object ProfileList : MeshFieldMode()
    data class SaveName(val prefill: String) : MeshFieldMode()
}
```

**Field `when(fieldMode)` pattern (from TemperatureScreen.kt lines 529–645 — adapt):**
```kotlin
// TemperatureScreen.kt lines 529–530 — the structural model:
when (fieldMode) {
    TempFieldMode.SensorList -> { /* list + FootButtonBar */ }
    TempFieldMode.PresetPicker -> { /* takeover content + FootButtonBar */ }
}

// BedMeshScreen.kt field lambda:
var fieldMode by remember { mutableStateOf<MeshFieldMode>(MeshFieldMode.ProfileList) }
var selectedProfile by remember { mutableStateOf<String?>(null) }
// (do NOT add LaunchedEffect(Unit) { holder.reset() } — BedMeshHolder has no reset() — Pitfall 3)

// field = {
when (fieldMode) {
    is MeshFieldMode.ProfileList -> {
        ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            items(vm.profileNames, key = { it }) { name ->
                ListRow(
                    selected = name == selectedProfile,
                    onClick = { selectedProfile = name },
                    uDp = grid.uDp,
                    trailingContent = if (name == vm.activeProfileName) {
                        { Text(stringResource(R.string.mesh_profile_active),
                            color = t.accent2, fontSize = fsSp(14f, t.fs).sp) }
                    } else null,
                ) { Text(name, color = t.text, fontSize = fsSp(16f, t.fs).sp) }
            }
        }
        // State-adaptive FootButtonBar (D-14):
        FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            // … branch on vm.homedGate + selectedProfile (D-14 table — see UI-SPEC)
        }
    }
    is MeshFieldMode.SaveName -> {
        // TokenTextField + validation (D-13, alphanumeric keyboard carve-out)
        // Cancel = Intent.Danger (C7 — discards pending input)
    }
}
```

**Views `BedMeshHeatmapHost` in Focus — P22 equality guard must survive (lines from current BedMeshScreen.kt):**
```kotlin
// Focus = the existing BedMeshHeatmapHost wrapped in AndroidView — keep P22 applyTokens guard intact
// Preview placeholder per PREVIEW_AND_TOKENS.md §9:
if (LocalInspectionMode.current) {
    PreviewPlaceholderBox(label = stringResource(R.string.cd_mesh_heatmap), modifier = modifier)
    return
}
// Normal: BedMeshHeatmapHost(model = vm.model, …) — same as before, DO NOT recreate factory
```

**`SaveNameDialog` + `LoadSelectorDialog` must be DELETED** — no overlay composables survive the migration. Any `if (dialog == MeshDialog.Save)` overlay code is removed.

**No `rememberCoroutineScope` for writes** — the BedMesh profile operations go through `dispatcher?.dispatch(CommandRegistry.bedMeshProfileSave, …)` etc., not through a composition scope.

---

### `ui/calibration/ScrewsTiltScreen.kt` (component — pure restyle, D-10)

**Analog:** `ui/calibration/ScrewsTiltScreen.kt` (self) + `ui/calibration/TiltScreen.kt` (pattern for FootButtonBar state-adaptive structure)

**Current layout structure (lines 56–79):** Focus = spatial bed-scale visualization (spatial carve-out — keep), Field = vertical scrollable point list (currently hand-rolled), Gutter = `Run` + `Back`.

**Field change — replace hand-rolled point Row with `ListBlock` + `ListRow`:**
```kotlin
// OLD (current): custom Row composable per screw point, not kit-based
// NEW: ListBlock + ListRow (per kit)
ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
    items(vm.loop?.points ?: emptyList(), key = { it.name }) { point ->
        ListRow(
            selected = false,   // no selection state on screw rows
            onClick = {},       // tap = no-op (informational list)
            uDp = grid.uDp,
            leadingContent = {
                // OWNER DECISION GATE per D-16: glyph for screw-point state indicator
                // (currently: point_scan / rotate_left / rotate_right / commit as MaterialSymbol)
            },
            trailingContent = {
                Text(
                    text = point.turnInstruction,  // e.g. "1.25 CW"
                    fontFamily = GeistMono,
                    fontSize = fsSp(14f, t.fs).sp,
                    color = t.text2,
                )
            },
        ) { Text(point.name, color = t.text, fontSize = fsSp(16f, t.fs).sp) }
    }
}
```

**FootButtonBar (D-10 — replaces gutter `Run`/`Back`):**
```kotlin
// OLD: gutter = { Row { … } } with custom HubActionControl
// NEW: gutter = null + FootButtonBar inside field

FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
    if (!vm.homedGate) {
        OutlinedControl(stringResource(R.string.calibration_home_all),
            { dispatcher?.dispatch(CommandRegistry.homeAll, Unit) },
            Modifier.weight(1f), Intent.Accent)
    } else {
        OutlinedControl(stringResource(R.string.calibration_run),
            { dispatcher?.dispatch(CommandRegistry.screwsTiltCalculate, Unit) },
            Modifier.weight(1f), Intent.Accent)
    }
    OutlinedControl("", onBack, Modifier.weight(1f), Intent.Neutral, icon = DinghyIcons.Back)
}
```

**Focus spatial visualization stays unchanged** — `ScrewsTiltVm.hasCoords`, `ScrewsTiltVm.loop?.points`, the custom drawn canvas/box layout remain as-is. Only the chrome tokens are updated.

**Scrollable Field suppresses swipe-up App Drawer** — `ListBlock` handles this automatically.

---

### `ui/calibration/TiltScreen.kt` (component — pure restyle, D-10)

**Analog:** `ui/calibration/TiltScreen.kt` (self)

**`TiltVariant` enum stays unchanged** (line 53):
```kotlin
enum class TiltVariant { ZTilt, Qgl }
```

**Screen signature stays unchanged** (lines 80–86) — `TiltHolder`, `TiltVariant`, `onBack`. The two variants remain ONE parameterized screen.

**Layout change:** `ScreenScaffold(gutter = { … })` → `ScreenScaffold(gutter = null)` + FootButtonBar inside field.

**Current gutter pattern to replace (from TiltScreen.kt):**
```kotlin
// OLD gutter with custom clickable boxes
// NEW: FootButtonBar inside field, same button semantics
FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
    if (!vm.homedGate) {
        OutlinedControl(stringResource(R.string.calibration_home_all),
            { dispatcher?.dispatch(CommandRegistry.homeAll, Unit) },
            Modifier.weight(1f), Intent.Accent)
    } else {
        OutlinedControl(stringResource(R.string.calibration_run),
            onClick = { /* variant-selected run command */ },
            modifier = Modifier.weight(1f),
            intent = Intent.Accent,
            enabled = dispatcher != null && tiltState != TiltState.Running,
        )
    }
    OutlinedControl("", onBack, Modifier.weight(1f), Intent.Neutral, icon = DinghyIcons.Back)
}
```

**Focus content stays unchanged** — bed-tilt icon + state headline. Token-restyle only.

---

### `preview/CalibrationPreviews.kt` (new preview file, D-15)

**Analog:** any existing `*Previews.kt` in the codebase (preview pattern is uniform project-wide)

**Required preview matrix shape (from PREVIEW_AND_TOKENS.md §3 + 27-UI-SPEC.md):**
```kotlin
// CalibrationPreviews.kt — file structure
package works.mees.dinghy.preview

import androidx.compose.ui.tooling.preview.Preview
import works.mees.dinghy.theme.compose.DinghyTheme
// … imports for all 5 calibration screens + SampleFixtures

// Per screen: 6 theme combos (Colorful/Simple/HighContrast × Dark/Light) + fs=L variant
// Hub — PresetPicker/Selected states
@Preview(name = "Hub_ColorfulDark_ProbeSelected")
@Composable
fun CalibrationHubPreview_ColorfulDark_ProbeSelected() {
    DinghyTheme(palette = PaletteMode.Colorful, dark = true) {
        CalibrationHubScreen(/* SampleFixtures.calibrationRoutineList … */)
    }
}
// + 5 more theme combos + fsLarge variant
// … similar for Probe (Idle/Active/Accepted states), BedMesh (ProfileList/SaveName/empty), etc.
```

**Wave-0 stub pattern** (must compile with `fail()` bodies per [[dinghy-wave0-red-scaffold-compile]]):**
```kotlin
// Wave-0: stub that compiles but is not yet a real preview
@Preview
@Composable
fun CalibrationHubPreview_Stub() {
    fail()
}
```

---

### `preview/MovePreviews.kt` (new preview file, D-15)

**Same structure as CalibrationPreviews.kt above.** Preview matrix must cover:
- Portrait vs landscape (the 60%-height cap must be visible in portrait)
- 6 theme combos on portrait (Colorful/Simple/HighContrast × Dark/Light)
- fs=L portrait — verifies center-cell readout doesn't clip

---

## Shared Patterns

### ScreenScaffold `gutter = null` + FootButtonBar inside `field` (LAW for all rebuilt screens)

**Source:** `designsystem/components/FootButtonBar.kt` + `designsystem/layout/ScreenScaffold.kt`

**Apply to:** ALL rebuilt screens (MoveScreen, CalibrationHubScreen, ProbeCalibrateScreen, BedMeshScreen, ScrewsTiltScreen, TiltScreen)

```kotlin
// FootButtonBar.kt lines 65–79 — the canonical usage pattern
ScreenScaffold(
    focus = { … },
    field = {
        ListBlock(modifier = Modifier.weight(1f)) { … }   // or other field content
        FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(8.dp)) {
            OutlinedControl(
                label = "",
                onClick = onBack,
                modifier = Modifier.weight(1f),
                icon = DinghyIcons.Back,
                intent = Intent.Neutral,
            )
        }
    },
    gutter = null,   // ALWAYS null — never pass actions to gutter on rebuilt screens
)
```

### `rememberUnitGrid` — U-based sizing at screen root

**Source:** `designsystem/layout/UnitGrid.kt` lines 108–end

**Apply to:** ALL rebuilt screens

```kotlin
// UnitGrid.kt call-site contract:
BoxWithConstraints(Modifier.fillMaxSize()) {
    val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
    // pass grid.uDp explicitly to: FootButtonBar, ListRow, OutlinedControl
}
```

### `ListRow` selection fill pattern (kit convention)

**Source:** `designsystem/components/ListRow.kt` lines 104–106

**Apply to:** CalibrationHubScreen (routine rows), BedMeshScreen (profile rows)

```kotlin
// ListRow.kt lines 104–106 — selection colors, NEVER raw Color(0x…)
val bgColor = if (listRowUsesAccentFill(selected)) t.accentSoft else Color.Transparent
val borderColor = if (selected) t.accentLine else t.outline
val borderWidth = listRowBorderWidthFor(selected)   // 2.dp selected, 1.5.dp unselected
```

### `ConfirmGuard` — destructive action gate

**Source:** `designsystem/ConfirmGuard.kt` lines 1–60

**Apply to:** MoveScreen (Disable steppers, `destructive = true`), BedMeshScreen (Remove profile `destructive = true`, SAVE_CONFIG `warn = true`)

```kotlin
// ConfirmGuard — the two intent variants used in P27:
// Destructive (red):
ConfirmGuard(
    title = stringResource(R.string.move_disable_confirm_title),
    message = stringResource(R.string.move_disable_confirm),
    confirmLabel = "DISABLE",
    onConfirm = { dispatchCommand(CommandRegistry.disableSteppers, Unit); showDisableGuard = false },
    onCancel = { showDisableGuard = false },
    destructive = true,   // red confirm
)
// Proceed-at-peril (amber, SAVE_CONFIG):
ConfirmGuard(
    title = stringResource(R.string.calibration_save_config),
    message = stringResource(R.string.calibration_save_config_confirm),
    confirmLabel = stringResource(R.string.calibration_save_config),
    onConfirm = { dispatcher?.dispatch(CommandRegistry.saveConfig, Unit); saveGuard = false },
    onCancel = { saveGuard = false },
    warn = true,   // amber, not red
)
```

### `AppContainer.writeScope` — process-lifetime write scope

**Source:** `di/AppContainer.kt` line 151 (`private val writeScope`) + lines 169, 174, 179 (usage pattern)

**Apply to:** BedMeshScreen profile operations (mesh save = `dispatcher?.dispatch(…)` via command registry, not `rememberCoroutineScope`)

```kotlin
// AppContainer.kt line 151 — the process-lifetime scope
private val writeScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

// AppContainer.kt lines 169–179 — usage pattern for any DataStore writes:
fun setActivePrinterProfile(id: String) {
    writeScope.launch { profileStore.setActive(id) }
}
// NOTE: Bed Mesh P27 sends profile ops through CommandRegistry (not DataStore) — no writeScope
// needed for P27. The relevant writeScope trap to avoid: never use rememberCoroutineScope for
// any DataStore/setting writes added in this phase (none expected, but guard the pattern).
```

### `fsSp` — S/M/L-aware font size

**Source:** `theme/fsSp.kt` (function referenced throughout)

**Apply to:** ALL rebuilt screens — every `fontSize = …` must use `fsSp(baseSp, t.fs).sp`

```kotlin
// Pattern used throughout existing screens (e.g. CalibrationHubScreen.kt line 80):
fontSize = fsSp(24f, t.fs).sp     // heading
fontSize = fsSp(16f, t.fs).sp     // body / list label
fontSize = fsSp(14f, t.fs).sp     // metadata / trailing
// NEVER: fontSize = 16.sp   ← bare .sp is forbidden
```

### Back intent = Neutral for plain nav, Danger for cancel-with-loss (C7)

**Source:** `designsystem/control/OutlinedControl.kt` lines 40–54 (Intent KDoc)

**Apply to:** ALL rebuilt screens

```kotlin
// Plain navigation Back → Intent.Neutral (outline)
OutlinedControl("", onBack, Modifier.weight(1f), Intent.Neutral, icon = DinghyIcons.Back)

// Cancel-with-loss Back → Intent.Danger (red) — applies to BedMesh SaveName "Cancel" (C7):
OutlinedControl(stringResource(R.string.common_cancel),
    { fieldMode = MeshFieldMode.ProfileList },
    Modifier.weight(1f), Intent.Danger)
```

### `applyEntryReset` pattern for NavDest composable blocks

**Source:** `ui/shell/AppShell.kt` line 689 (current Calibration block)

**Apply to:** `composable<NavDest.CalibrationHub>` lambda in AppShell

```kotlin
// AppShell.kt line 689 — copy this pattern to CalibrationHub block:
composable<NavDest.CalibrationHub> {
    LaunchedEffect(Unit) { nav.applyEntryReset(NavDest.CalibrationHub) }
    CalibrationHubScreen(…)
}
// The 5 routine sub-screens do NOT need applyEntryReset — they have no sub-nav to reset.
```

---

## No Analog Found

All files in Phase 27 have close analogs. No new-from-scratch patterns are required.

---

## Icon Registration Gate (Wave 0 blocker — D-16)

The following 5 glyphs are currently used as raw `MaterialSymbol(name = entry.routine.glyph)` strings in `CalibrationHubScreen.kt` (lines 155–162) and are NOT registered in `DinghyIcons.kt`. The D-16 icon law requires owner confirmation before these can be registered as `DinghyIcon` tokens.

| Routine | Current raw string | Proposed token name | Status |
|---------|-------------------|---------------------|--------|
| SCREWS_TILT | `architecture` | `DinghyIcons.RoutineScrewsTilt` | **OWNER DECISION GATE** |
| Z_TILT | `vertical_align_center` | `DinghyIcons.RoutineZTilt` | **OWNER DECISION GATE** |
| QUAD_GANTRY_LEVEL | `crop_square` | `DinghyIcons.RoutineQgl` | **OWNER DECISION GATE** |
| BED_MESH | `grid_on` | `DinghyIcons.RoutineBedMesh` | **OWNER DECISION GATE** |
| PROBE_CALIBRATE | `straighten` | `DinghyIcons.RoutineProbeCalibrate` | **OWNER DECISION GATE** |

All 5 ligature names are confirmed present in the Geist v2.944 bundled font (Phase 18.1 `verify_ligatures.py` — Research A2). The font is not the blocker; the icon-registry-law is. Wave 0 plan must resolve these with the owner before the hub screen can be implemented.

**DinghyIcons registration pattern** (source: `designsystem/icons/DinghyIcons.kt` lines 23–64):
```kotlin
// DinghyIcons.kt — add in the appropriate section (e.g., after LauncherCalibration):
val RoutineProbeCalibrate = DinghyIcon(IconRef.Ligature("straighten"), alternate = "routine_probe_calibrate")
val RoutineBedMesh        = DinghyIcon(IconRef.Ligature("grid_on"), alternate = "routine_bed_mesh")
val RoutineScrewsTilt     = DinghyIcon(IconRef.Ligature("architecture"), alternate = "routine_screws_tilt")
val RoutineZTilt          = DinghyIcon(IconRef.Ligature("vertical_align_center"), alternate = "routine_z_tilt")
val RoutineQgl            = DinghyIcon(IconRef.Ligature("crop_square"), alternate = "routine_qgl")
```

**DinghyIconsTest drift guard** (`DinghyIconsTest.kt`) must include the new tokens — any new `DinghyIcon` added to the object must appear in the test's exhaustive token list to trigger the drift-guard failure.

---

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/` — all Kotlin source files
**Files scanned:** 15 source files + 4 design-system components
**Pattern extraction date:** 2026-06-12

**Key patterns:**
- All rebuilt screens: `gutter = null` + `FootButtonBar` inside the `field` lambda (P26 mandatory rule — enforced by `FootButtonBar.kt` KDoc anti-pattern warning)
- `ScreenScaffold` `portraitFocusAspect` is NOT used for Move (the 60%-height cap is `BoxWithConstraints` manual sizing — more precise than the aspect helper)
- Vertical 3-cell column pattern already exists in `ProbeCalibrateScreen.kt` lines 421–462 — copy, don't reinvent
- `MeshFieldMode` sealed class mirrors `TempFieldMode` in `TemperatureScreen.kt` lines 91–94 exactly
- The `when(calibrationRoutine)` block at AppShell lines 687–730 is the single biggest structural delete; replacing it with 6 `composable<>` blocks follows the established `composable<NavDest.FineTune>` pattern at line 731
- `ShellNavState.calibrationRoutine` (line 56) + old `BackHandler` (lines 511–515) are the two required removals in existing files
