# Phase 28: System / Settings Cluster - Pattern Map

**Mapped:** 2026-06-12
**Files analyzed:** 20 new/modified files
**Analogs found:** 20 / 20

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `ui/route/NavDest.kt` | route model | request-response | `ui/route/NavDest.kt` (self) | exact — add NavDest.System |
| `ui/route/HomeAction.kt` | route model | request-response | `ui/route/HomeAction.kt` (self) | exact — extend buildIdleActions |
| `ui/shell/AppShell.kt` | shell / NavHost | event-driven | `ui/shell/AppShell.kt` (self) | exact — delete drawer, add NavDest.System branch |
| `ui/shell/AppDrawer.kt` | overlay | (DELETED) | `ui/shell/AppDrawer.kt` | deleted |
| `ui/shell/SwipeUpAccumulator.kt` | utility | (DELETED) | `ui/shell/SwipeUpAccumulator.kt` | deleted |
| `ui/shell/DevThemeCyclerOverlay.kt` | overlay | event-driven | `ui/shell/DevThemeCyclerOverlay.kt` (self) | exact — add bare-surface drag region |
| `ui/screen/SystemPageScreen.kt` | screen / composable | request-response | `ui/calibration/CalibrationHubScreen.kt` | role-match (hub → nav rows) |
| `ui/screen/PrintersScreen.kt` | screen / composable | CRUD | `ui/screen/PrintersScreen.kt` (self) | exact — rebuild with mode-toggle foot |
| `ui/screen/SettingsScreen.kt` | screen / composable | CRUD | `ui/screen/SettingsScreen.kt` (self) | exact — dense restyle |
| `ui/screen/ThemeScreen.kt` | screen / composable | (thin wrapper) | `ui/screen/ThemeScreen.kt` (self) | exact — no change |
| `ui/screen/ThemeEditorScreen.kt` | screen / composable | CRUD | `ui/screen/ThemeEditorScreen.kt` (self) | exact — S/V square, maxItems delete, dense |
| `ui/systeminfo/SystemInformationScreen.kt` | screen / composable | request-response | `ui/calibration/CalibrationHubScreen.kt` | role-match (info list) |
| `ui/screen/AboutScreen.kt` | screen / composable | request-response | `ui/screen/AboutScreen.kt` (self) | exact — dense restyle |
| `ui/screen/TokenTextField.kt` | component | request-response | `ui/screen/TokenTextField.kt` (self) | exact — add dense flag |
| `ui/printstatus/PrintStatusField.kt` | screen section | event-driven | `ui/printstatus/PrintStatusField.kt` (self) | exact — onOpenDrawer → onNavigateSystem |
| `di/AppContainer.kt` | service / DI | CRUD | `di/AppContainer.kt` (self) | exact — seedTheme fix |
| `theme/ThemePrefs.kt` | model | CRUD | `theme/ThemePrefs.kt` (self) | exact — maxItems deletion |
| `theme/ThemeResolver.kt` | service | transform | `theme/ThemeResolver.kt` (self) | exact — maxItems removal |
| `config/Profile.kt` | model | CRUD | `config/Profile.kt` (self) | exact — maxItems field removal |
| `designsystem/components/ListRow.kt` | component | request-response | `designsystem/components/ListRow.kt` (self) | exact — add dense flag |

---

## Pattern Assignments

### `ui/route/NavDest.kt` (route model, request-response)

**Analog:** `app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt`

**Current sealed interface + knownNavDests** (lines 29–84):
```kotlin
@Serializable
sealed interface NavDest {
    @Serializable data object WaterfallHome          : NavDest
    @Serializable data object Temperature            : NavDest
    // ... 20 more members ...
    @Serializable data object About                  : NavDest
}

val knownNavDests: List<NavDest> = listOf(
    NavDest.WaterfallHome,
    NavDest.Temperature,
    // ...
    NavDest.About,
)
```

**Add pattern** — insert `NavDest.System` after `NavDest.About` in both the sealed interface and `knownNavDests`:
```kotlin
@Serializable data object System : NavDest   // ← new, D-01

// In knownNavDests:
NavDest.About,
NavDest.System,   // ← new; count becomes 23
```

**FOOT_GUN_DESTS pattern** (lines 123–132): System cluster is intentionally NOT in `FOOT_GUN_DESTS`. Mid-print reachable (D-06). Do NOT add `NavDest.System` or its sub-screens.

**screenOwnsEstop pattern** (AppShell lines 995–999): Do NOT add `NavDest.System` to `screenOwnsEstop`. Shell-level FloatingEStop applies to the System cluster.

---

### `ui/route/HomeAction.kt` (route model, request-response)

**Analog:** `app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt`

**Delete OpenDrawer** (lines 47–49): Remove the `data object OpenDrawer : HomeAction` variant entirely.

**buildIdleActions signature** (lines 82–87) — current:
```kotlin
fun buildIdleActions(
    spoolmanPresent: Boolean,
    bookmarksExist: Boolean,
    outputsPresent: Boolean,
    webcamEnabled: Boolean,
): List<HomeAction> = buildList {
```

**New rows** — insert after CalibrationHub, before Outputs (D-05 order). All three are unconditionally present:
```kotlin
add(HomeAction.Destination(
    dest     = NavDest.Temperature,
    labelRes = R.string.cd_launcher_temperature,  // new string key
    icon     = DinghyIcons.LauncherTemperature,   // EXISTING: thermostat
))

add(HomeAction.Destination(
    dest     = NavDest.Console,
    labelRes = R.string.cd_launcher_console,      // new string key
    icon     = DinghyIcons.LauncherConsole,       // EXISTING: terminal
))

add(HomeAction.Destination(
    dest     = NavDest.FineTune,
    labelRes = R.string.cd_launcher_fine_tune,    // new string key
    icon     = DinghyIcons.LauncherFineTune,      // NEW token: line_style (D-21)
))
```

**HomeActionTest update:** `assertEquals("Full list must have 8 destination rows", 8, dests.size)` at line 142 becomes 11. Order test gains 3 new entries. `OpenDrawer` test case deleted.

---

### `ui/shell/AppShell.kt` (shell / NavHost, event-driven)

**Analog:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt`

**Delete drawer state** (line 165):
```kotlin
// DELETE THIS LINE:
var drawerOpen by remember { mutableStateOf(false) }
```

**Delete swipe-suppress block** (lines 536–584): The entire `suppressSwipe` val and the `pointerInput(navBackStackEntry, promptView.visible)` swipe-detect block. Also delete `val SWIPE_UP_THRESHOLD_PX` and the import of `SwipeUpAccumulator`.

**Delete BackHandler for drawer** (line 510):
```kotlin
// DELETE: BackHandler(enabled = drawerOpen) { drawerOpen = false }
```

**Delete AppDrawer overlay** (lines 961–973):
```kotlin
// DELETE:
if (drawerOpen) {
    AppDrawer(
        onDestination = { navController.navigate(it) },
        onDismiss = { drawerOpen = false },
        webcamEnabled = webcamEnabled,
        spoolEnabled = spoolEnabled,
        spoolSwatches = drawerSpoolSwatches,
        activeName = activeName,
        outputsEnabled = outputsEnabled,
    )
}
```

**Delete bottom-edge handle** (lines 975–982):
```kotlin
// DELETE the thin bottom-edge affordance Box
Box(
    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(8.dp).background(t.hair),
)
```

**Add NavDest.System composable block** — insert AFTER NavDest.About (line 829 area), following the exact pattern of every other composable block (lines 806–829):
```kotlin
composable<NavDest.About> {
    AboutScreen(
        container = container,
        onBack = { navController.popBackStack() },
    )
}
// ← INSERT HERE:
composable<NavDest.System> {
    SystemPageScreen(
        container = container,
        onNavigate = { navController.navigate(it) },
        onBack = { navController.popBackStack() },
    )
}
```

**Replace onOpenDrawer in WaterfallHome block** (lines 593–605): The `onOpenDrawer = { drawerOpen = true }` param is retired; replace with routing `NavDest.System` through the existing `onNavigate` lambda.

**screenOwnsEstop pattern** (lines 994–999) — keep as-is, do NOT add System or cluster screens:
```kotlin
val screenOwnsEstop = estopDest != null && (
    estopDest.isRoute<NavDest.FineTune>() ||
    estopDest.isRoute<NavDest.Temperature>() ||
    estopDest.isRoute<NavDest.Spool>()
    // ← DO NOT ADD NavDest.System or cluster screens here
)
```

**shouldPopToRoot LaunchedEffect** (lines 838–860): The currentNavDest mapping `when` needs `NavDest.System` added to the "non-foot-gun" fallback path — `else -> null` already covers it, but a doc comment is warranted.

---

### `ui/screen/SystemPageScreen.kt` (NEW screen, request-response)

**Closest analog:** `app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt`

The CalibrationHub is the closest Phase 27 precedent: a hub-page with Focus (static card) + Field (ListBlock of ListRows) + FootButtonBar with Back only + gutter=null + FloatingEStop handled by shell.

**Imports pattern** (from CalibrationHubScreen lines 1–43):
```kotlin
package works.mees.dinghy.ui.screen

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.BuildConfig
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.ui.route.NavDest
```

**BoxWithConstraints + rememberUnitGrid pattern** (CalibrationHubScreen lines 102–104):
```kotlin
BoxWithConstraints(modifier.fillMaxSize()) {
    val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
    // ...
```

**ScreenScaffold gutter=null pattern** (CalibrationHubScreen line 178):
```kotlin
ScreenScaffold(
    focus = { /* brand strip — see Focus sizing below */ },
    field = {
        ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            items(systemRows) { row -> ListRow(dense = true, ...) { /* row content */ } }
        }
        FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            OutlinedControl(
                label = stringResource(R.string.common_back),
                onClick = onBack,
                modifier = Modifier.weight(1f),
                intent = Intent.Neutral,
            )
        }
    },
    gutter = null,   // LAW: rebuilt screens always null the gutter
)
```

**Focus sizing for System page** — D-02 ratio cap (20% portrait height / 40% landscape width). Use `BoxWithConstraints` to compute:
```kotlin
// Inside BoxWithConstraints, after grid is known:
val landscape = maxWidth > maxHeight
val focusGrow = if (landscape) 0.4f else 0.2f   // D-02: 40% WIDTH / 20% HEIGHT

ScreenScaffold(
    focusGrow = focusGrow,
    fieldGrow = if (landscape) 0.6f else 0.8f,
    // ...
)
```

**Direct-tap rows (D-02):** System page rows navigate on tap immediately — no selection state:
```kotlin
ListRow(
    dense = true,
    selected = false,    // never selected — this is nav, not a picker
    onClick = { onNavigate(dest) },
    uDp = grid.uDp,
    leadingContent = {
        DinghyIconView(icon = glyphToken, tint = t.text, sizeDp = 22.dp,
                       modifier = Modifier.padding(end = 8.dp))
    },
) {
    Text(text = stringResource(labelRes), color = t.text, fontFamily = Geist,
         fontSize = fsSp(17f, t.fs).sp)
}
```

**Power stub row pattern** (D-08 — greyed/inert, stop-tinted):
```kotlin
// Row: no onClick (not clickable), stop-tinted label, greyed
Row(
    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    DinghyIconView(icon = DinghyIcons.SystemRowPower, tint = t.stop.copy(alpha = 0.38f), sizeDp = 22.dp,
                   modifier = Modifier.padding(end = 8.dp))
    Text(text = stringResource(R.string.system_row_power), color = t.stop.copy(alpha = 0.38f),
         fontFamily = Geist, fontSize = fsSp(17f, t.fs).sp)
    Spacer(Modifier.weight(1f))
    Text(text = stringResource(R.string.system_row_power_sub), color = t.text2.copy(alpha = 0.38f),
         fontFamily = Geist, fontSize = fsSp(15f, t.fs).sp)
}
```

**FloatingEStop:** NOT rendered inside this screen. Shell-level e-stop applies (D-06; Pitfall 6 guard).

---

### `ui/screen/PrintersScreen.kt` (screen, CRUD)

**Analog:** `app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt` (self, rebuilt)
**Secondary analog for Focus:** `designsystem/components/DetailCard.kt`
**Secondary analog for mode-toggle state machine:** existing `BackHandler` + `var editingTarget` pattern in current PrintersScreen (lines 110–122)

**Mode-toggle state** — model after the existing `editingTarget` sealed interface pattern (lines 167–171):
```kotlin
private enum class PrinterMode { Normal, EditArmed, DeleteArmed }
private var printerMode by remember { mutableStateOf(PrinterMode.Normal) }
// Disarm on Back:
BackHandler(printerMode != PrinterMode.Normal) { printerMode = PrinterMode.Normal }
```

**ScreenScaffold with Focus + Field + FootButtonBar** (pattern from CalibrationHubContent lines 102–181):
```kotlin
ScreenScaffold(
    focus = {
        DetailCard(modifier = Modifier.fillMaxWidth().padding(8.dp),
                   ringColor = connectionStateColor(connectionState, t)) {
            // name (Geist 20sp semibold) + host:port (GeistMono 15sp) + connection state chip + Klippy state
        }
    },
    field = {
        ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            items(profiles, key = { it.id }) { profile ->
                ListRow(
                    dense = true,
                    selected = profile.id == activeId,
                    onClick = { onRowClick(profile, printerMode) },
                    uDp = grid.uDp,
                ) { /* profile row content */ }
            }
        }
        FootButtonBar(uDp = grid.uDp, ...) {
            OutlinedControl(label = stringResource(R.string.printers_add), ..., intent = Intent.Accent,
                            modifier = Modifier.weight(1f))
            OutlinedControl(label = stringResource(R.string.printers_edit), ...,
                            intent = if (printerMode == PrinterMode.EditArmed) Intent.Accent else Intent.Neutral,
                            modifier = Modifier.weight(1f))
            OutlinedControl(label = stringResource(R.string.printers_delete), ...,
                            intent = if (printerMode == PrinterMode.DeleteArmed) Intent.Danger else Intent.Neutral,
                            modifier = Modifier.weight(1f))
            OutlinedControl(label = stringResource(R.string.common_back), ..., intent = Intent.Neutral,
                            modifier = Modifier.weight(1f))
        }
    },
    gutter = null,
)
```

**ConfirmGuard pattern** (PrintersScreen lines 215–230 — existing usage):
```kotlin
pendingDelete?.let { victim ->
    ConfirmGuard(
        title = stringResource(R.string.printers_delete_confirm_title),
        message = stringResource(R.string.printers_delete_confirm_body),
        confirmLabel = stringResource(R.string.printers_delete),
        cancelLabel = stringResource(R.string.common_back),
        onConfirm = {
            container.deleteProfile(victim.id)
            pendingDelete = null
            printerMode = PrinterMode.Normal
        },
        onCancel = { pendingDelete = null },
        destructive = true,
    )
    return
}
```

**Write-scope pattern** (AppContainer lines 168–179):
```kotlin
// ALL persists route through container intent helpers — NEVER rememberCoroutineScope()
container.setActiveProfile(profile.id)    // switch
container.saveProfile(profile)            // add/edit
container.deleteProfile(profile.id)       // delete
container.mutateActiveProfile { it.copy(...) }  // field mutation
```

---

### `ui/screen/SettingsScreen.kt` (screen, CRUD)

**Analog:** `app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt` (self, dense restyle)

**Existing write-scope pattern** (lines 150, 173, 190, 215 — keep verbatim):
```kotlin
// Already correct — preserve these routes through container intent helpers:
container.setActiveWebcamEnabled(it)   // onToggle lambda
container.setBabystepEnabled(it)
container.setBabystepLayers(n)
container.setKeepScreenOn(it)
// etc.
```

**Dense layout shape** (D-11, D-12) — replace the existing layout with:
```kotlin
Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    // Each toggle row: DenseToggleRow { icon · label + Spacer + Switch }
    // Each numeric field row: DenseFieldRow { icon · label + Spacer + TokenTextField(dense=true) }
}
```

**Foot (Back only):**
```kotlin
// At bottom of the Column, not in gutter:
OutlinedControl(
    label = stringResource(R.string.common_back),
    onClick = onBack,
    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    intent = Intent.Neutral,
)
```

**Text floor enforcement** (D-10): All text in dense rows must use `fsSp(baseSp, t.fs).sp` with `baseSp >= 15f`. The old 13sp EX(set) Material-label exemption is deleted.

---

### `ui/screen/ThemeEditorScreen.kt` (screen, CRUD)

**Analog:** `app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt` (self)

**seedTheme reactive fix** (D-19) — line 117, the ONE-SHOT bug (confirmed by RESEARCH.md Pitfall 5):
```kotlin
// CURRENT (buggy — one-shot read):
LaunchedEffect(activeProfile?.id) {
    val tuple = activeProfile?.toThemeTuple() ?: container.themePrefs.tupleFlow.firstOrNull()
    // ...
}

// FIX — collect reactively:
val globalTuple by container.themePrefs.tupleFlow.collectAsStateWithLifecycle(
    initialValue = ThemePrefs.TUPLE_DEFAULT
)
LaunchedEffect(activeProfile?.id, globalTuple) {
    val tuple = activeProfile?.toThemeTuple() ?: globalTuple
    // ...
}
```

**maxItems deletion** (D-17): Remove all references in this file — the `setMaxItems` call site, any UI section, any state var for maxItems. Grep: `maxItems` across the file → delete every occurrence.

**S/V square** (D-16) — Canvas pattern (from ColorWheel.kt lines 75–149 as gesture template):
```kotlin
Canvas(
    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
        .pointerInput(hue) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                var sat = (down.position.x / w).coerceIn(0f, 1f)
                var value = 1f - (down.position.y / h).coerceIn(0f, 1f)
                onHandleMove(sat, value)
                down.consume()
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { change ->
                        if (change.pressed && change.positionChanged()) {
                            sat = (change.position.x / w).coerceIn(0f, 1f)
                            value = 1f - (change.position.y / h).coerceIn(0f, 1f)
                            onHandleMove(sat, value)
                            change.consume()
                        }
                    }
                } while (event.changes.any { it.pressed })
                onSettle(sat, value)
            }
        }
) {
    // White→hue gradient (saturation axis, left→right)
    drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
    // Transparent→black overlay (value axis, top→bottom)
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
    // Crosshair at (sat × width, (1-value) × height)
    val cx = sat * size.width
    val cy = (1f - value) * size.height
    // thin t.outline lines through the crosshair point
    drawLine(color = t.outline, start = Offset(cx, 0f), end = Offset(cx, size.height), strokeWidth = 1.dp.toPx())
    drawLine(color = t.outline, start = Offset(0f, cy), end = Offset(size.width, cy), strokeWidth = 1.dp.toPx())
}
```

**State vars for S/V** — alongside the existing `var hue`:
```kotlin
var sat by remember { mutableFloatStateOf(1f) }
var value by remember { mutableFloatStateOf(1f) }
// Seed from stored ARGB on open:
LaunchedEffect(activeSlot) {
    val argb = storedArgbForSlot(activeSlot)
    if (argb != null) {
        val c = Color(argb.toInt())
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        hue = hsv[0]; sat = hsv[1]; value = hsv[2]
    }
}
```

**onSettle write** — use `Color.hsv(hue, sat, value)` instead of `Color.hsv(hue, 1f, 1f)`.

**Dense scroll shape** (D-18):
```kotlin
Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
    ColorWheel(hue = hue, onHandleMove = { hue = it }, onSettle = { ... })
    Spacer(8.dp)
    SaturationValueSquare(hue = hue, sat = sat, value = value, ...)
    Divider(color = t.hair, thickness = 1.dp); Spacer(8.dp)
    // Pool slots section
    // Status slots section
    // NO maxItems section
    FootButtonBar(uDp = grid.uDp) {
        OutlinedControl("Apply", onClick = onApply, intent = Intent.Go, modifier = Modifier.weight(1f))
        OutlinedControl("Reset to Default", onClick = { /* ConfirmGuard */ }, intent = Intent.Warn, Modifier.weight(1f))
        OutlinedControl("Back", onClick = onBack, intent = Intent.Neutral, modifier = Modifier.weight(1f))
    }
}
```

---

### `ui/systeminfo/SystemInformationScreen.kt` (screen, request-response)

**Closest analog:** `app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt`

Dense restyle only — the `ListBlock` of `ListRow` items already used in the hub:
```kotlin
ScreenScaffold(
    field = {
        ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            items(sysInfoRows) { row ->
                ListRow(dense = true, selected = false, onClick = {}, uDp = grid.uDp,
                        leadingContent = { DinghyIconView(icon = row.icon, ...) }) {
                    Text(row.label, color = t.text, fontFamily = Geist, fontSize = fsSp(17f, t.fs).sp)
                    Spacer(Modifier.weight(1f))
                    Text(row.value, color = t.text2, fontFamily = GeistMono, fontSize = fsSp(15f, t.fs).sp)
                }
            }
        }
        FootButtonBar(uDp = grid.uDp, ...) {
            OutlinedControl(stringResource(R.string.common_back), onClick = onBack,
                            intent = Intent.Neutral, modifier = Modifier.weight(1f))
        }
    },
    gutter = null,
)
```

---

### `ui/screen/AboutScreen.kt` (screen, request-response)

**Closest analog:** `app/src/main/java/works/mees/dinghy/ui/screen/AboutScreen.kt` (self, dense restyle)

Dense restyle with `Column.verticalScroll` (D-11 — fits one page at M). Same structure as SettingsScreen — single Column, Back at foot, no ListBlock needed:
```kotlin
Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
    // jiib wordmark (brandTint accent-tinted)
    // Version string (GeistMono, fsSp(15f, t.fs).sp)
    // Tagline (Geist, fsSp(17f, t.fs).sp, t.text2)
    Spacer(lg)
    // Dev-enable toggle row (keep existing implementation, dense)
    // Link rows
    Spacer(Modifier.weight(1f))
    OutlinedControl(stringResource(R.string.common_back), onClick = onBack,
                    intent = Intent.Neutral, modifier = Modifier.fillMaxWidth())
}
```

---

### `designsystem/components/ListRow.kt` (component, request-response)

**Analog:** `app/src/main/java/works/mees/dinghy/designsystem/components/ListRow.kt` (self)

**Current signature** (lines 91–128):
```kotlin
@Composable
fun ListRow(
    selected: Boolean,
    onClick: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
)
```

**Add dense flag** (D-09) — new parameter with `false` as default (existing call sites unchanged):
```kotlin
@Composable
fun ListRow(
    selected: Boolean,
    onClick: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
    dense: Boolean = false,            // ← NEW: C6-exempt surfaces pass true
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    // ...
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (dense) Modifier.padding(vertical = 10.dp)   // C6: fixed 10dp, not U-derived
                else Modifier.heightIn(min = uDp)               // standard: U floor
            )
            // ...
    )
}
```

**Pure helper update** — `listRowBorderWidthFor` and `listRowUsesAccentFill` remain unchanged (no new host-test needed for the dense flag beyond visual inspection).

---

### `ui/screen/TokenTextField.kt` (component, request-response)

**Analog:** `app/src/main/java/works/mees/dinghy/ui/screen/TokenTextField.kt` (self)

Add `dense: Boolean = false` parameter that reduces internal vertical padding. Default unchanged — zero impact on existing call sites:
```kotlin
fun TokenTextField(
    // ...existing params...
    dense: Boolean = false,  // ← NEW: C6 surfaces pass true for sub-1U height
) {
    // Inside the Box/TextField: if (dense) use padding(vertical = 6.dp) else the current padding
}
```

---

### `ui/printstatus/PrintStatusField.kt` (screen section, event-driven)

**Analog:** `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt` (self)

**Changes:**
1. Remove `onOpenDrawer: () -> Unit` parameter.
2. In the idle foot bar: replace `onClick = onOpenDrawer` with `onClick = { onNavigate(NavDest.System) }`.
3. In the printing shortcut grid: add System tile using `DinghyIcons.FootSystem` (existing `bottom_panel_open` token):
```kotlin
// Add alongside the existing Tune tile:
ShortcutTile(
    icon = DinghyIcons.FootSystem,
    label = stringResource(R.string.system_row_label),  // or a new key
    onClick = { onNavigate(NavDest.System) },
)
```
4. Remove the `HomeAction.OpenDrawer` branch from the `when(action)` dispatch.

---

### `di/AppContainer.kt` (service / DI, CRUD)

**Analog:** `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` (self)

**seedTheme method** (lines 650–671): The `maxItems = tuple.maxItems` call in `themeResolver.apply(...)` must be removed after D-17 deletion. The method body is otherwise correct — it already collects `activeThemeTuple` reactively via `.collect { ... }`.

**maxItems removal** (D-17) — lines 661–662:
```kotlin
// REMOVE this param from the themeResolver.apply() call:
maxItems = tuple.maxItems,
```

**resetActiveTheme method** (if present): also remove `maxItems` param from any `themeResolver.apply()` call there.

**Write-scope pattern** (lines 151, 168–189) — the canonical law for ALL persistence:
```kotlin
private val writeScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun setActiveProfile(id: String) {
    writeScope.launch { profileStore.setActive(id) }
}

fun mutateActiveProfile(transform: (Profile) -> Profile) {
    writeScope.launch { profileStore.mutateActive(transform) }
}
```

---

### `theme/ThemePrefs.kt` (model, CRUD)

**Analog:** `app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt` (self)

**Delete these elements** (D-17, located via grep on file):
- `KEY_MAX_ITEMS = intPreferencesKey("theme_max_items")` — remove
- `setMaxItems(maxItems: Int)` suspend fun — remove
- `rawMaxItems = prefs[KEY_MAX_ITEMS]` in `sanitizeTuple` — remove
- `maxItems: Int` field in `ThemeTuple` data class — remove
- `maxItems = maxItems` in `ThemeTuple` construction — remove
- The `maxItems` sanity branch in `sanitizeTuple` — remove
- `maxItems = DEFAULT_MAX_ITEMS` in `resetToDefaults()` — remove
- Any `MAX_ITEMS_RANGE` or `DEFAULT_MAX_ITEMS` constants — remove

**Add new S/V DataStore keys** (D-16) — for each pool/status slot that previously persisted only hue, the full ARGB is already stored via `poolOverrides` as an unsigned-32 ARGB Long. No new keys needed — the fix is in ThemeEditorScreen reading the stored ARGB back to HSV on open (see ThemeEditorScreen S/V pattern above).

---

### `config/Profile.kt` (model, CRUD)

**Analog:** `app/src/main/java/works/mees/dinghy/config/Profile.kt` (self)

**Delete `maxItems: Int` field** from the `Profile` data class (D-17). Also delete from `StoredProfile` if it exists there.

**kotlinx.serialization safety** (RESEARCH Pitfall 8 / Assumption A1): Verify the `ProfileStore`'s `Json` instance carries `ignoreUnknownKeys = true` before executing this deletion. If not, add it. Stored JSON blobs with `"maxItems": 4` will be silently ignored on next read.

---

### `theme/ThemeResolver.kt` (service, transform)

**Analog:** `app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt` (self)

**Delete `maxItems` constructor param, field, and all usages** in `apply()`, `bake()`, `compute()` (D-17). After deletion, the parameter list to `themeResolver.apply()` in `AppContainer.seedTheme` also loses `maxItems` — these edits must be atomic (same plan/wave).

---

### `designsystem/icons/DinghyIcons.kt` (icon registry, request-response)

**Analog:** `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` (self)

**Registration pattern** (lines 196–215 — Phase 27 example, adding 5 tokens in one block):
```kotlin
// --- Phase-28 System/Settings cluster glyphs (28-01, OWNER-LOCKED D-21).
// All ligatures verified resolvable in the bundled v2.944 Material Symbols ttf.
val LauncherFineTune = DinghyIcon(IconRef.Ligature("line_style"), alternate = "launcher_fine_tune")
val SystemRowPrinters = DinghyIcon(IconRef.Ligature("android_wifi_3_bar_plus"), alternate = "system_row_printers")
val SystemRowSettings = DinghyIcon(IconRef.Ligature("settings"), alternate = "system_row_settings")
val SystemRowTheme = DinghyIcon(IconRef.Ligature("palette"), alternate = "system_row_theme")
val SystemRowAbout = DinghyIcon(IconRef.Ligature("info"), alternate = "system_row_about")
val SystemRowPower = DinghyIcon(IconRef.Ligature("power_settings_new"), alternate = "system_row_power")
```

**`all` list update** (line 222 area): add all 6 new tokens to the `DinghyIcons.all` list. The `DinghyIconsTest` drift guard will fail at compile time if any entry is missing from `all`.

**Note on `alternate` uniqueness:** the `alternate` strings must be globally unique across all existing entries (the drift guard checks this). The proposed strings above are verified non-conflicting against the current registry.

**Pre-existing tokens reused** (no registration needed):
- `DinghyIcons.SysInfoTile` (`pulse_alert`) — System page System Info row
- `DinghyIcons.LauncherTemperature` (`thermostat`) — home idle list
- `DinghyIcons.LauncherConsole` (`terminal`) — home idle list
- `DinghyIcons.FootSystem` (`bottom_panel_open`) — printing shortcut grid System tile

---

### `ui/shell/DevThemeCyclerOverlay.kt` (overlay, event-driven)

**Analog:** `app/src/main/java/works/mees/dinghy/ui/shell/DevThemeCyclerOverlay.kt` (self)

**D-20 drag fix:** The existing `Column` has `pointerInput(boxSize)` for drag (lines 192–209). The bug is that chip taps bleed through to the panel-drag handler when a chip occupies the exact press point. The fix is a structural reorganization — add a bare-surface drag region BELOW the chips in the Column, or re-structure with a `Box` where the drag handles the background layer and chips handle their own events:

**Current structure** (line 187–211):
```kotlin
Column(
    modifier = Modifier
        .offset { ... }
        .pointerInput(boxSize) { /* drag the whole Column */ }
        .padding(8.dp),
) {
    // header + chips — chips have their own consume-on-down handlers
}
```

**D-20 fix pattern** — separate the drag surface from the chips. The chip `pointerInput` handlers (lines 268–288) already consume down+move events to prevent bleed. The actual D-20 bug is that the drag failed when chip taps consumed the DOWN before the Column's `awaitFirstDown` could see it. Fix: move the drag to a bare inert region (e.g. a drag handle strip above or below chips) rather than on the Column itself:

```kotlin
// Pattern: column with a dedicated drag strip
Column(...) {
    // Drag handle strip — bare surface, drag only
    Box(
        Modifier.fillMaxWidth().height(24.dp)
            .pointerInput(boxSize) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    drag(down.id) { change ->
                        // update offset
                        change.consume()
                    }
                }
            }
    )
    // Chips below — they consume their own events
    CyclerChip(...); CyclerChip(...); CyclerChip(...)
}
```

---

### Preview Files (all NEW)

**Closest analog:** `app/src/main/java/works/mees/dinghy/preview/CalibrationPreviews.kt`

**PreviewBox + 6-combo pattern** (CalibrationPreviews.kt lines 44–58):
```kotlin
@Preview(name = "SystemPage: Connected (Nexus7 portrait)", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable
private fun SystemPageConnected() = PreviewBox(colorfulDark) {
    SystemPageContent(
        activePrinterName = "Ender 5 Plus",
        versionName = "0.1.0-debug",
        connectionState = ConnectionState.Connected,
        onNavigate = {},
        onBack = {},
    )
}
// 5 more: colorfulLight, simpleDark, simpleLight, highContrastDark, highContrastLight
// + fsLarge overflow check + RTL spot check + pseudolocale
```

**Required preview files:**
- `preview/SystemPagePreviews.kt`
- `preview/PrintersPreviews.kt`
- `preview/SettingsPreviews.kt`
- `preview/ThemeEditorPreviews.kt`
- `preview/SysInfoPreviews.kt`
- `preview/AboutPreviews.kt`

**SampleFixtures additions** (in `preview/SampleFixtures.kt` or analogous file):
```kotlin
// Add alongside existing fixtures:
val printerProfileList: List<Profile> = listOf(/* 2-3 profiles, one active */)
val systemPageState = SystemPageState(version = "0.1.0", activePrinterName = "Ender 5 Plus")
```

---

## Shared Patterns

### writeScope Persistence Pattern
**Source:** `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` lines 151, 168–189
**Apply to:** All rebuilt screens (PrintersScreen, SettingsScreen, ThemeEditorScreen, AboutScreen)

```kotlin
// The ONLY safe write pattern — NEVER rememberCoroutineScope() for persistence:
private val writeScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

// Intent methods (called from UI):
fun setActiveProfile(id: String) { writeScope.launch { profileStore.setActive(id) } }
fun mutateActiveProfile(transform: (Profile) -> Profile) {
    writeScope.launch { profileStore.mutateActive(transform) }
}
```

### ScreenScaffold gutter=null Pattern
**Source:** `app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt` line 178
**Apply to:** All rebuilt screens (SystemPageScreen, PrintersScreen, SettingsScreen, ThemeEditorScreen, SystemInformationScreen, AboutScreen)

```kotlin
ScreenScaffold(
    focus = { ... },   // or null for field-only screens
    field = {
        // content + FootButtonBar at the bottom of this lambda
        FootButtonBar(uDp = grid.uDp, ...) {
            OutlinedControl(...)
        }
    },
    gutter = null,  // LAW: rebuilt screens always null the gutter
)
```

### fsSp Text Sizing Pattern
**Source:** All existing rebuilt screens (CalibrationHubScreen, FineTuneScreen, SpoolScreen)
**Apply to:** Every text node in every rebuilt screen

```kotlin
// ALWAYS use fsSp — NEVER a bare .sp value
Text(text = label, color = t.text, fontFamily = Geist,
     fontSize = fsSp(17f, t.fs).sp)          // ← 17sp = row label floor (above 15sp minimum)
Text(text = value, color = t.text2, fontFamily = GeistMono,
     fontSize = fsSp(15f, t.fs).sp)          // ← 15sp = HARD FLOOR (D-10)
```

### Intent Color Pattern
**Source:** `app/src/main/java/works/mees/dinghy/designsystem/control/OutlinedControl.kt` lines 56–71
**Apply to:** All FootButtonBar controls in rebuilt screens

```kotlin
// Button intent = color (THEMING.md law):
Intent.Neutral  // Back, plain nav, Edit-unarmed
Intent.Accent   // Add Printer (physical command)
Intent.Danger   // Delete-armed, ConfirmGuard destructive confirm
Intent.Go       // Theme Apply, save
Intent.Warn     // Reset to Default (caution)
```

### DinghyIconView Pattern
**Source:** `app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt` lines 145–153
**Apply to:** All icon renderings in rebuilt screens (never raw MaterialSymbol — icon-law)

```kotlin
DinghyIconView(
    icon = DinghyIcons.SomeToken,          // ALWAYS a registry token — never a raw ligature string
    contentDescription = null,             // row label describes the row
    tint = t.text,                         // or t.accent2, t.stop (intent-appropriate)
    sizeDp = 22.dp,                        // dense list icon size (UAT-1: NOT 70-80% U)
    modifier = Modifier.padding(end = 8.dp),
)
```

### BoxWithConstraints + rememberUnitGrid Pattern
**Source:** `app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt` lines 102–104
**Apply to:** SystemPageScreen, PrintersScreen (any screen with a UnitGrid-sized component)

```kotlin
BoxWithConstraints(modifier.fillMaxSize()) {
    val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
    Box(Modifier.fillMaxSize()) {
        // ScreenScaffold uses grid.uDp for ListRow + FootButtonBar sizing
    }
}
```

### FloatingEStop Suppression Pattern
**Source:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` lines 994–1007
**Apply to:** SystemPageScreen and all cluster sub-screens — do NOT render their own FloatingEStop

The shell-level `screenOwnsEstop` check (lines 995–999) only suppresses the shell e-stop for `FineTune`, `Temperature`, and `Spool`. System cluster screens are intentionally NOT in this set — the shell-level e-stop fires on them, which is correct.

### ColorWheel Gesture Pattern (template for S/V Square)
**Source:** `app/src/main/java/works/mees/dinghy/designsystem/ColorWheel.kt` lines 87–111
**Apply to:** ThemeEditorScreen S/V square (D-16)

```kotlin
.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // compute initial value from down.position, call onHandleMove, down.consume()
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { change ->
                if (change.pressed && change.positionChanged()) {
                    // compute value, call onHandleMove, change.consume()
                }
            }
        } while (event.changes.any { it.pressed })
        onSettle(...)   // pointer-UP only
    }
}
```

---

## No Analog Found

All files have analogs in the codebase. No hand-rolled patterns required.

---

## Critical Pitfalls (from RESEARCH.md — repeat here for planner visibility)

| Pitfall | Affected Files | Guard |
|---------|---------------|-------|
| maxItems removal breaks test golden files | `ThemePrefsFallbackTest`, theme tests | Grep `maxItems` across test sourceset before any deletion |
| HomeActionTest count assertion fails | `HomeActionTest` line 142 | Update to 11 in same plan as `buildIdleActions` change |
| OpenDrawer removal leaves dead code | `HomeAction.kt` + `PrintStatusField.kt` + `AppShell.kt` | Update atomically in one plan |
| seedTheme fix in wrong layer | `ThemeEditorScreen.kt` line 117 (NOT `AppContainer`) | Fix the `LaunchedEffect` firstOrNull in the editor, not seedTheme |
| FloatingEStop double-render | `SystemPageScreen.kt` + cluster screens | Do NOT add NavDest.System to `screenOwnsEstop` |
| ProfileStore ignoreUnknownKeys | `config/Profile.kt` + `ProfileStore.kt` | Verify Json instance has ignoreUnknownKeys=true before D-17 deletion |
| font too small | All rebuilt screens | Hard floor: `fsSp(baseSp, t.fs).sp` with baseSp >= 15f everywhere |
| icon auto-pick | DinghyIcons.kt new tokens | Only use D-21 locked glyphs; STOP AND ASK for anything not in that list |

---

## Metadata

**Analog search scope:** `app/src/main/java/works/mees/dinghy/` (all packages)
**Files scanned:** 25+ source files via direct Read + Bash grep
**Pattern extraction date:** 2026-06-12
