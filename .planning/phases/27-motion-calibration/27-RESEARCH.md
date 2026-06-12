# Phase 27: Motion + Calibration - Research

**Researched:** 2026-06-11
**Domain:** Android Jetpack Compose UX migration — spatial jog controls + calibration wizard flows
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Move screen (D-01 to D-04)**
- D-01: Z control → vertical 3-cell column (Z-up / readout-home / Z-down). Closes the C3 todo at completion once on-device verified.
- D-02: DistanceSelector row-of-preset-buttons dies → vertical 3-cell stepper ( + / active-step readout / − ) cycling the fixed DISTANCES set. No free entry.
- D-03: Portrait XY pad capped at ~60% of HEIGHT; ratio rule caps width accordingly. Landscape: pad fills full height. Foot buttons may sit under the columns section — planner tunes exact ratios within 5U budget.
- D-04: Pad internals carry over unchanged (home-XY center, axis-corner live readouts + tap-to-home, red force-move toggle). Foot actions (All / Disable / Back) and Disable ConfirmGuard semantics carry over onto FootButtonBar.

**Calibration hub (D-05 to D-07)**
- D-05: List hub with info Focus. Field = routine list as ListRows; Focus = selected routine's icon + title + brief description + accent Open button. First entry pre-selects so Focus is never empty.
- D-06: Greyed-but-listed unsupported routines KEPT (owner override; NOT hidden).
- D-07: Routines become real nav routes — hub → routine pushes, Back pops to hub. The current `when(calibrationRoutine)` in-screen sub-nav converts to NavHost routes per P24 D-01.

**Wizard flow grammar (D-08 to D-10)**
- D-08: Probe-Calibrate TESTZ controls adopt the Move grammar — paired vertical columns (Z-nudge + step columns), same 3-equal-rows motif as the vertical Z column.
- D-09: State-adaptive action semantics carry over VERBATIM onto FootButtonBar. Back suppressed (nav layer) while a manual-probe session is Active.
- D-10: Tilt + Screws-Tilt = pure restyle. ScrewsTilt bed-scale visualization keeps spatial carve-out; screw rows become ListRows. FootButtonBar + intent colors.

**Bed Mesh surfaces (D-11 to D-14)**
- D-11: Field = saved-profile ListRow list; full-screen Load dialog dies entirely; Focus = BedMeshHeatmapHost (Views, keep P22 equality guards).
- D-12: Row tap selects (no immediate dispatch); Apply foot loads; Remove behind red ConfirmGuard.
- D-13: Save-name = Field-takeover with alphanumeric keyboard (sanctioned carve-out). Pre-fill YY.MM.DD_HH.MM default. `PrinterCommands.isValidProfileName` gating preserved.
- D-14: State-adaptive foot: unhomed → Home All; homed + no selection → Calibrate + Save + Back; profile selected → Apply + Remove + Back. Amber SAVE_CONFIG restart gate after profile-save survives.

**Cross-cutting (D-15 to D-17)**
- D-15: Conformance + preview-first + tokenized-first fold into each screen (≥64px, fsSp S/M/L, rotation, @Preview 6-combo + fs=L, stringResource, DinghyIcons registry only).
- D-16: Icon law — NEVER auto-pick. Any new glyph not already registered: STOP and ASK the owner.
- D-17: Move/Calibration remain P24 D-04 pop-to-root foot-guns. UAT-1..UAT-5 apply.

### Claude's Discretion
- Exact Move portrait/landscape ratios and foot-button placement (under-columns vs full-width) — 60% height is starting point, not sacred; owner judges at UAT.
- Hub routine descriptions (brief how-it-works copy) — Claude authors; owner reviews at UAT.
- Profile-list "active" marking style, selection visuals, how Bed Mesh Field-takeover composes — consistent with Spoolman/P26 takeover precedents.
- NavHost route shapes for hub + 5 routines, holder hoisting, how D-09 live-session back-block is enforced at nav layer — grounded in P24 spine patterns.
- Whether TiltScreen's two variants stay one parameterized screen (current shape) — no reason to split.

### Deferred Ideas (OUT OF SCOPE)
- CalibrationHub flips to hide-not-grey (future quick task).
- Todo C3 stays open as tracker; close at completion once vertical Z ships on-device.
- Temperature adjuster fire-without-wait → Phase 28.
- Webcam aspect ratio overlay back → Phase 25 (already shipped).
- FW-retraction glyph assignment → Phase 26 follow-up.
- C6 settings densify, R4 Printers edit/delete, Phase 15.1 review deferred findings → Phase 28.
- ARM64 ABI ship requirement → Phase 29.
</user_constraints>

---

## Summary

Phase 27 is a pure UX migration: six screens (MoveScreen, CalibrationHubScreen, ProbeCalibrateScreen, BedMeshScreen, ScrewsTiltScreen, TiltScreen) are rebuilt onto the jiib redesign grammar established in Phases 23–26. No new printer capability. The holders, state machines, command paths, and capability gating are NOT touched.

The critical structural change is the conversion of the `when(calibrationRoutine)` in-screen sub-nav (currently controlled by `ShellNavState.calibrationRoutine`) to real NavHost routes per P24 D-01. This requires adding new `@Serializable` NavDest members for the calibration sub-screens and wiring them into the AppShell NavHost — a moderate structural change with a well-understood template (the Fine-Tune flat-collapse from P26 is the closest analog).

MoveScreen has a spatial-layout exemption (like Extrude's D-15): the 3×3 jog pad stays a sacred-square grid; the new requirement is the vertical Z column and vertical distance stepper replacing the ZRow and DistanceSelector. The ProbeCalibrateScreen's TESTZ controls adopt the same 3-equal-rows vertical column motif (D-08). BedMeshScreen is the most structurally changed screen: the full-screen Load dialog and Save dialog die entirely, replaced by a Field list + Field-takeover (the P26 pattern).

**Primary recommendation:** Treat D-07 nav-route conversion as Wave 0 structural work (add NavDest sub-members + AppShell wiring + back-suppression at nav layer); all six screen rebuilds flow in subsequent waves. Re-use the P26 component kit (FootButtonBar, ListRow, IncrementPicker) throughout.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| XY jog pad (spatial grid, 3×3) | UI Screen (Compose) | MoveHolder (state) | Spatial layout carries meaning; stays grid per LAW |
| Z jog + distance stepper | UI Screen (Compose) | MoveHolder (state) | New vertical columns are pure UI geometry change |
| Calibration hub list + Focus | UI Screen (Compose) | CalibrationHubHolder (routines flow) | Hub is UI concern; holder already provides RoutineEntry list |
| Calibration sub-nav back-stack | NavHost routes (AppShell) | ShellNavState | D-07 converts from in-screen `when` to real NavDest routes |
| Back suppression while probe active | NavHost (BackHandler at AppShell) | ProbeCalibrateHolder.vm.state | D-09: nav-layer back-block; currently BackHandler inside screen |
| TESTZ nudge controls | UI Screen (Compose) | ProbeCalibrateHolder | State machine (Idle/Active/Accepted) lives in holder, unchanged |
| BedMesh profile list | UI Screen (Compose, Field) | BedMeshHolder.vm.profileNames | Full-screen Load dialog dies; Field list reads same flow |
| BedMesh heatmap (Views) | AndroidView host (BedMeshHeatmapHost) | BedMeshHolder.vm.model | Views surface stays Views per ADR-0001; P22 equality guards survive |
| Screws-Tilt bed-scale visualization | UI Screen (Compose, Focus) | ScrewsTiltHolder | Spatial carve-out; stays a custom drawn surface, not a list |
| Calibration pop-to-root on print | AppShell LaunchedEffect | FOOT_GUN_DESTS predicate | Pre-existing D-04 wiring; must cover the whole sub-route tree |

---

## Standard Stack

This phase uses the existing project stack. No new dependencies.

### Core (already in project)
| Component | Purpose | Phase 27 Usage |
|-----------|---------|---------------|
| Jetpack Compose (BOM 2026.05.00) | UI | All six rebuilt screens |
| Navigation-Compose 2.8.9 | NavHost routing | D-07 calibration sub-routes |
| `FootButtonBar` (P23 kit) | Foot action row | Replaces all gutter rows |
| `ListRow` / `ListBlock` (P23 kit) | Scrollable list items | Hub routine list, screw rows, mesh profile list |
| `IncrementPicker` (P26 kit) | Step selector row | Distance picker in Move, TESTZ step in Probe |
| `DetailCard` (P23 kit) | Focus surface container | Hub Focus (routine info) |
| `rememberUnitGrid` / `UnitGrid` (P23 kit) | U-based sizing | All rebuilt screens |
| `BedMeshHeatmapHost` / `BedMeshHeatmapView` (Views) | Heatmap rendering | BedMesh Focus — stays Views, untouched |
| `ConfirmGuard` | Destructive action gates | Disable steppers, Remove profile, SAVE_CONFIG |
| `OutlinedControl` | Individual foot buttons | Inside FootButtonBar |
| `ScreenScaffold` | Focus/Field layout | All screens, `gutter = null` |
| `DinghyIcons` | Icon registry | All screens — no auto-pick |
| `fsSp` | Font-scale-aware sizes | All text |
| `LocalTokens` | Semantic color tokens | All chrome |

**Installation:** No new packages. This is purely a restyle/migration phase.

---

## Package Legitimacy Audit

Not applicable. This phase installs no new external packages.

---

## Architecture Patterns

### System Architecture Diagram

```
App Drawer
    └─► NavDest.Move ──► MoveScreen (spatial-exempt)
                            ├── Focus: 3×3 JogPad (sacred square grid) — UNCHANGED
                            ├── Field: [Z-column | ZReadout | Z-down] + [+ | step-readout | -]
                            └── FootButtonBar: [Home All | Disable | Back]

App Drawer
    └─► NavDest.Calibration ──► CalibrationHubScreen (list+Focus, D-05)
            ├── Focus: DetailCard [routine icon + title + description + Open button]
            ├── Field: ListBlock(ListRow × 5 routines, supported-first, greyed-listed)
            └── FootButtonBar: [Back]
                    │
                    ├── NavDest.CalibrationProbe ──► ProbeCalibrateScreen
                    │       ├── Focus: probe/offset display (current, unchanged)
                    │       ├── Field: [Z-col | Z-readout | Z-down] + [+ | step | -]  (D-08)
                    │       └── FootButtonBar: (state-adaptive per D-09, back-suppressed when Active)
                    │
                    ├── NavDest.CalibrationBedMesh ──► BedMeshScreen
                    │       ├── Focus: BedMeshHeatmapHost (Views, P22 guards intact)
                    │       ├── Field: ListBlock(mesh profile ListRows) | Field-takeover (save-name IME)
                    │       └── FootButtonBar: (state-adaptive D-14)
                    │
                    ├── NavDest.CalibrationScrewsTilt ──► ScrewsTiltScreen
                    │       ├── Focus: BedScale spatial visualization (carve-out, unchanged shape)
                    │       ├── Field: ListBlock(ScrewRow × N — now ListRows, not hand-rolled)
                    │       └── FootButtonBar: [Run/Home | Back]
                    │
                    ├── NavDest.CalibrationZTilt ──► TiltScreen(variant=ZTilt)
                    │       ├── Focus: bed_tilt icon + state headline (unchanged)
                    │       ├── Field: status body + adjustments (unchanged content, restyle)
                    │       └── FootButtonBar: [Run/Home | Back]
                    │
                    └── NavDest.CalibrationQgl ──► TiltScreen(variant=Qgl)
                            (same shape as ZTilt)
```

### D-07 Navigation Route Conversion

The current `when(calibrationRoutine)` sub-nav is controlled by `ShellNavState.calibrationRoutine: CalibrationRoutine?` — a nullable field mutated directly at the screen tier. D-07 converts this to real NavHost routes.

**Two valid approaches (Claude's discretion):**

Option A — **Add sub-destinations to the sealed NavDest interface** (five new data objects):
```kotlin
@Serializable data object CalibrationHub          : NavDest
@Serializable data object CalibrationProbe        : NavDest
@Serializable data object CalibrationBedMesh      : NavDest
@Serializable data object CalibrationScrewsTilt   : NavDest
@Serializable data object CalibrationZTilt        : NavDest
@Serializable data object CalibrationQgl          : NavDest
```
`NavDest.Calibration` becomes `CalibrationHub`. `knownNavDests` gains 5 entries. `FOOT_GUN_DESTS` must cover ALL six calibration dests.

Option B — **Nested NavController inside the `composable<NavDest.Calibration>` lambda** — a nested NavHost owned by that composable, invisible to AppShell's outer NavController. The inner controller handles hub→routine pushes; Back from a routine pops the inner stack; the outer Back exits the Calibration destination entirely.

**Option A** is the cleaner match to P24's stated goal ("routines become real NavHost routes") and easier to test (parseStartDest round-trips; BackHandler logic is at AppShell level). Option B keeps the outer NavHost smaller but hides the sub-routes from AppShell's back-suppression and pop-to-root machinery.

**Recommendation (Option A):** Add 6 `@Serializable` NavDest sub-members. Remove `ShellNavState.calibrationRoutine`. Update `FOOT_GUN_DESTS` to the full set of 6 calibration dests. `applyEntryReset(NavDest.CalibrationHub)` replaces the current `calibrationRoutine = null` reset. The D-09 back-suppression in ProbeCalibrateScreen must move from the in-screen `BackHandler` to an AppShell-level `BackHandler` checking whether the probe session is Active AND the current route is `NavDest.CalibrationProbe`.

### Pattern 1: Move Screen Layout — 60% Portrait Cap

The sacred-square 3×3 pad must not crowd the Z/distance columns. The existing `portraitFocusAspect` mechanism in `ScreenScaffold` does NOT apply here — Move uses `BoxWithConstraints` + manual sizing.

**Portrait layout (D-03):**
```kotlin
// The pad is the largest centered square fitting 60% of the available HEIGHT
// (not the focus-area width, which is the current bug it replaces).
BoxWithConstraints(modifier) {
    val padSize = (maxHeight * 0.60f).coerceAtMost(maxWidth)
    // [padSize × padSize] JogPad
    // remaining HEIGHT goes to the Z-column section
}
```

**Landscape layout:** pad fills full height of its half (the existing `fillMaxHeight` approach is correct for landscape — no change needed). Z column and distance stepper sit beside the pad in the other half.

### Pattern 2: Vertical 3-Cell Column (D-01, D-02, D-08)

The owner's verbatim steer: "3 equally spaced rows, just like the vertical z column."

Both the new Z column and the new distance stepper column follow the same motif:
```kotlin
Column(
    modifier = Modifier.weight(1f).fillMaxHeight(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    OutlinedControl(      // top cell: + or Z-up arrow
        modifier = Modifier.weight(1f).fillMaxWidth(),
        …
    )
    StepDisplay(          // middle cell: readout (step mm or Z position)
        modifier = Modifier.weight(1f).fillMaxWidth(),
        …
    )
    OutlinedControl(      // bottom cell: - or Z-down arrow
        modifier = Modifier.weight(1f).fillMaxWidth(),
        …
    )
}
```

This is EXACTLY the `ProbeJogPad` column pattern already in `ProbeCalibrateScreen.kt` (lines 419–462). The column layout for Move and TESTZ must mirror it.

For the **distance stepper** column cycling `DISTANCES`:
```kotlin
var distance by remember { mutableStateOf(10.0) }
val idx = DISTANCES.indexOf(distance).coerceAtLeast(0)

Column(…) {
    OutlinedControl("+", onClick = { distance = DISTANCES[(idx+1).coerceAtMost(DISTANCES.lastIndex)] },
        enabled = idx < DISTANCES.lastIndex, intent = Intent.Neutral, …)
    StepDisplay(value = distance, modifier = …)
    OutlinedControl("−", onClick = { distance = DISTANCES[(idx-1).coerceAtLeast(0)] },
        enabled = idx > 0, intent = Intent.Neutral, …)
}
```

For the **Z column** (replacing ZRow, keeping Z plane color):
```kotlin
Column(…) {
    // Z+ jog button (Z-plane outline color: t.directional.z)
    JogCell("expand", onClick = { onJogZ(distance) }, disabled = …, forceMove = forceMove, …)
    // Z home + readout center cell (taller read-only; home on tap)
    HomeZCell(zHomed = vm.zHomed, zValue = vm.z, onHomeZ = onHomeZ, modifier = Modifier.weight(1f))
    // Z- jog button
    JogCell("compress", onClick = { onJogZ(-distance) }, disabled = …, forceMove = forceMove, …)
}
```

### Pattern 3: Calibration Hub List + Focus (D-05)

The hub follows the same list+Focus grammar as SpoolScreen (P23 pilot). The existing `CalibrationHubHolder.routines` StateFlow already provides the `List<RoutineEntry>` in supported-first order — no holder changes.

```kotlin
@Composable
fun CalibrationHubScreen(
    holder: CalibrationHubHolder,
    onOpen: (CalibrationRoutine) -> Unit,  // now calls navController.navigate(NavDest.CalibrationXxx)
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val routines by holder.routines.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(routines.firstOrNull()?.routine) }
    // D-05: pre-select first on load
    LaunchedEffect(routines) { if (selected == null) selected = routines.firstOrNull()?.routine }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        ScreenScaffold(
            focus = {
                selected?.let { routine ->
                    DetailCard(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        // icon + title + description + Open button (D-05)
                        HubRoutineFocus(routine = routine, onOpen = { onOpen(routine) }, grid = grid)
                    }
                }
            },
            field = {
                ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    items(routines, key = { it.routine.name }) { entry ->
                        ListRow(
                            selected = entry.routine == selected,
                            onClick = { selected = entry.routine },
                            uDp = grid.uDp,
                            leadingContent = {
                                // DinghyIconView with registered glyph — NOT raw MaterialSymbol
                                // D-06: greyed-but-listed: use t.text3 tint for !isSupported
                                DinghyIconView(icon = routineIcon(entry.routine),
                                    tint = if (entry.isSupported) t.accent2 else t.text3)
                            },
                        ) {
                            Text(entry.routine.label, color = if (entry.isSupported) t.text else t.text3)
                        }
                    }
                }
                FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    OutlinedControl("", onBack, Modifier.weight(1f), Intent.Neutral, icon = DinghyIcons.ArrowBack)
                }
            },
            gutter = null,
        )
    }
}
```

**Hub routine glyph mapping** (already defined in `CalibrationHubScreen.kt` as `CalibrationRoutine.glyph`): `architecture` / `vertical_align_center` / `crop_square` / `grid_on` / `straighten`. These ligature names are used by the current `RoutineTile` — they must be verified as registered in `DinghyIcons` or added. **ICON LAW (D-16):** planner must check the registry before assigning; the glyphs are currently used as raw `MaterialSymbol(name = entry.routine.glyph)` which bypasses the registry. The migration must route them through `DinghyIcons` tokens. This is a Wave 0 icon-registration task.

### Pattern 4: BedMesh Field Takeover (D-13)

The existing `SaveNameDialog` (full-screen overlay) and `LoadSelectorDialog` (full-screen overlay) are DELETED. The save-name flow becomes a Field-takeover:

```kotlin
sealed class MeshFieldMode {
    data object ProfileList : MeshFieldMode()
    data class SaveName(val name: String) : MeshFieldMode()
}

// In field = { when (fieldMode) {
//   is MeshFieldMode.ProfileList → LazyColumn of ListRows + FootButtonBar
//   is MeshFieldMode.SaveName   → Column with TokenTextField (alphanumeric, D-13) + FootButtonBar
// }}
```

The `LoadSelectorDialog` (with inline Remove buttons) becomes the `ProfileList` Field itself — tap selects, Apply foot loads, Remove behind ConfirmGuard. The full-screen background overlay and nested `ScreenScaffold` in `LoadSelectorDialog` are eliminated.

### Pattern 5: D-09 Back-Suppression at Nav Layer

The current back-suppression is a `BackHandler` inside `ProbeCalibrateScreen` itself:
```kotlin
// CURRENT (pre-P27) — implicit, via the Idle/Active/Accepted gutter state
// Active gutter has NO Back button → user can't tap Back
// But system Back (gesture/button) is NOT suppressed — gap!
```

D-09 requires "Back suppressed — including the nav back-stack/system back" while a session is Active. The migration must add an explicit `BackHandler` in AppShell (or inside the `composable<NavDest.CalibrationProbe>` lambda):

```kotlin
composable<NavDest.CalibrationProbe> {
    val vm by probeCalibrateHolder.vm.collectAsStateWithLifecycle()
    // D-09: suppress system Back while session is Active
    BackHandler(enabled = vm.state == ProbePageState.Active) {
        // swallow — no navigation; the user must Accept or Abort
    }
    ProbeCalibrateScreen(
        container = container,
        holder = probeCalibrateHolder,
        onBack = { navController.popBackStack() },
    )
}
```

### Pattern 6: FootButtonBar + gutter = null (universal)

ALL rebuilt screens in P27 follow the P26 mandatory rule:
```kotlin
ScreenScaffold(
    focus = { … },
    field = {
        // … list/content …
        FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            OutlinedControl(…, modifier = Modifier.weight(1f))
        }
    },
    gutter = null,   // ALWAYS null on rebuilt screens — gutter slot is legacy
)
```

The current screens all use `gutter = { Row(…) { … } }`. The migration must null the gutter and move all actions into FootButtonBar inside the field.

### Recommended Project Structure

```
ui/move/
├── MoveScreen.kt          # rebuilt — vertical Z col + distance stepper, gutter=null
└── MoveHolder.kt          # UNCHANGED
ui/calibration/
├── CalibrationHubScreen.kt    # rebuilt — list+Focus, D-05
├── ProbeCalibrateScreen.kt    # rebuilt — vertical TESTZ col, D-08/D-09
├── BedMeshScreen.kt           # rebuilt — Field list + takeover, D-11..D-14
├── ScrewsTiltScreen.kt        # rebuilt — ListRows, FootButtonBar
├── TiltScreen.kt              # rebuilt — FootButtonBar, conformance; one screen, two variants
├── CalibrationHubHolder.kt    # UNCHANGED
├── ProbeCalibrateHolder.kt    # UNCHANGED
├── BedMeshHolder.kt           # UNCHANGED
├── ScrewsTiltHolder.kt        # UNCHANGED
├── TiltHolder.kt              # UNCHANGED
└── CalibrationGate.kt         # UNCHANGED
ui/route/
└── NavDest.kt                 # ADD 6 calibration sub-NavDests (Option A); update knownNavDests + FOOT_GUN_DESTS
ui/shell/
└── AppShell.kt                # UPDATE composable<NavDest.Calibration> → 6 composable<NavDest.CalibrationXxx>
preview/
└── CalibrationPreviews.kt     # NEW — @Preview matrices for all 6 screens
```

### Anti-Patterns to Avoid

- **Gutter rows on rebuilt screens:** Do NOT pass actions to `ScreenScaffold`'s `gutter` slot. Always `gutter = null` + `FootButtonBar` inside `field`. (Every current calibration screen uses the gutter — all must be migrated.)
- **Raw MaterialSymbol for hub icons:** The current `RoutineTile` uses `MaterialSymbol(name = entry.routine.glyph)` directly. The rebuilt hub must route through `DinghyIconView(icon = DinghyIcons.XxxRoutine)` — but these icons may not be registered yet (see Wave 0 task).
- **Keeping full-screen BedMesh dialogs:** `SaveNameDialog` and `LoadSelectorDialog` are full-screen Compose overlays over `ScreenScaffold`. Delete them entirely; replace with FieldMode takeover. Any `if (dialog == MeshDialog.Save)` overlay must go.
- **rememberCoroutineScope for DataStore writes:** Bed Mesh profile name and calibration trace color changes (if any) must route through `AppContainer.writeScope.launch { }`. Composition scope is cancelled on navigation.
- **calibrationRoutine state field after D-07:** Once sub-routes are real NavDest members, `ShellNavState.calibrationRoutine` must be removed. It cannot coexist with NavHost back-stack management (double source of truth for the active screen).
- **In-screen BackHandler for probe (current pattern):** The current `ProbeCalibrateScreen` gutter simply hides the Back button — system Back is NOT blocked. D-09 requires an explicit `BackHandler(enabled = vm.state == ProbePageState.Active)` at the route-composable level.
- **BedMesh P22 equality guards regression:** When re-parenting `BedMeshHeatmapHost` into the new layout, the `applyTokens` equality guard in `BedMeshHeatmapView` must be preserved. Do NOT recreate the AndroidView factory on each recomposition.
- **Auto-picking icons for hub routines:** The 5 routine glyphs (`architecture`, `vertical_align_center`, `crop_square`, `grid_on`, `straighten`) are CURRENTLY used as raw Material Symbols ligatures, NOT as registered DinghyIcons tokens. Adding them to the registry requires owner confirmation per D-16.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Hub routine list | New tile/card grid | `ListRow` + `ListBlock` (P23 kit) | Established kit; consistent selection state |
| Hub Focus info panel | Custom composable | `DetailCard` wrapping a Column | P26 pattern; consistent card chrome |
| Foot actions | Row inside gutter slot | `FootButtonBar` (P23 kit) | P26 mandatory rule; gutter=null |
| Vertical 3-cell step columns | Custom layout | Weighted Column of 3 `OutlinedControl`s | Already proven in ProbeJogPad; reuse pattern |
| Mesh profile list | Custom `LazyColumn` | `ListBlock` + `ListRow` | Selection state, outline/fill convention handled |
| Back-suppression while probe Active | Boolean flag in screen | `BackHandler(enabled = vm.state == Active)` in composable | NavHost-layer suppression required per D-09 |
| Mesh Field-takeover | Full-screen overlay Composable | `sealed class MeshFieldMode` + `when(fieldMode)` in `field` | P26 FieldMode pattern; no overlay needed |
| Per-screen U grid | Hardcoded dp values | `rememberUnitGrid(minOf(maxWidth, maxHeight))` | Required for UAT-1..UAT-5 touch floor compliance |

**Key insight:** Every "custom" component in the current calibration screens (hand-rolled gutter buttons, hand-rolled grid tiles, hand-rolled dialog composables) is replaced by a P23/P26 kit component. The kit was built for exactly this pattern.

---

## Common Pitfalls

### Pitfall 1: Forgetting the ScrollableField → suppress swipe-up drawer

**What goes wrong:** The rebuilt hub Field is a scrollable `ListBlock`; the screw-rows Field is a scrollable `ListBlock`; the mesh-profile Field is a scrollable `ListBlock`. Scrollable Fields must suppress the swipe-up App Drawer gesture (THEMING.md rule). The current hub uses a `LazyVerticalGrid` (NOT scrollable in the same way as a scroll list) — this suppression was NOT needed. The new list hub IS a scroll list and must suppress.

**Why it happens:** The LAW rule is easy to forget when converting from a grid to a list.

**How to avoid:** `ListBlock` handles this automatically if wired correctly. Verify at UAT with an upward swipe inside the list.

### Pitfall 2: FOOT_GUN_DESTS not covering sub-routes

**What goes wrong:** If Option A nav route conversion is used and `FOOT_GUN_DESTS` only contains `NavDest.CalibrationHub`, then `shouldPopToRoot` won't pop the user out of a ProbeCalibrate session when a print starts.

**Why it happens:** The `FOOT_GUN_DESTS` set was authored with one `NavDest.Calibration` entry; expanding to 6 dests requires updating the set.

**How to avoid:** `FOOT_GUN_DESTS` must include ALL six calibration NavDest members. Add a host unit test asserting each calibration sub-dest is in `FOOT_GUN_DESTS`.

### Pitfall 3: BedMesh FieldMode selection state resetting on profile update

**What goes wrong:** When `holder.vm.collect` delivers a new `BedMeshVm` (e.g., a profile was loaded), any `var selected by remember { mutableStateOf<String?>(null) }` will correctly persist across recompositions BUT may be reset if the Composable re-enters.

**Why it happens:** P26 pattern: `LaunchedEffect(Unit) { holder.reset() }` is used in ProbeCalibrateScreen to clear session state on entry. BedMesh does NOT have a reset() call — but the planner must not accidentally add one.

**How to avoid:** The BedMesh selection state (`selectedProfile`) is local to the Composable. Do NOT add a `LaunchedEffect(Unit) { holder.reset() }` to BedMeshScreen (BedMeshHolder has no such method, and the profile list survives navigation).

### Pitfall 4: Vertical Z column sizing — not 3 equal cells

**What goes wrong:** The three cells ( Z-up / Z-readout-home / Z-down ) must be EQUALLY SPACED (verbatim owner steer). Using different `weight()` values (e.g., giving the center cell more space for the readout) violates D-01.

**Why it happens:** The readout cell naturally wants more space; developers give it `weight(2f)` vs `weight(1f)` for the arrow cells.

**How to avoid:** All three cells use `Modifier.weight(1f)`. The center cell content (Z value + home icon) must be vertically compact enough to fit in the same 1U slot.

### Pitfall 5: ShellNavState.calibrationRoutine removal cascade

**What goes wrong:** `ShellNavState.calibrationRoutine` is read in AppShell at line ~183 and in the `BackHandler` at line ~512. Removing it as part of D-07 requires updating both sites. Missing the `BackHandler` at line ~512 leaves a dangling reference.

**Why it happens:** The field is used in two different places in a 750+ line file.

**How to avoid:** Grep for `calibrationRoutine` across the entire codebase before declaring the migration complete. Expected sites: `AppShell.kt` (~183, ~510–514, ~696–729), `ShellNavState.kt`.

### Pitfall 6: The "Starting…" feedback state in ProbeCalibrateScreen

**What goes wrong:** `ProbeCalibrateScreen` has a `starting` local state: `vm.state == ProbePageState.Idle && ("probe_calibrate" in inFlight || "z_endstop_calibrate" in inFlight)`. This drives a disabled "Starting…" button. The state-adaptive FootButtonBar must replicate this exactly or the user loses visual feedback during the klicky macro's multi-second run.

**Why it happens:** The `starting` check is easy to miss when translating the gutter `when(vm.state)` branches into FootButtonBar.

**How to avoid:** Copy the starting logic verbatim into the D-09 FootButtonBar branch. The `starting` state suppresses Back (same as Active) — this must also carry over.

### Pitfall 7: MoveHolder API — vm.z lives in the JogPad corner, not in a separate Z column

**What goes wrong:** In the current design, the Z value (live position) is shown in the BOTTOM-LEFT corner of the 3×3 JogPad (the `AxisCorner("Z", vm.z, vm.zHomed, …)` cell). The new vertical Z column also needs the live Z readout in its center cell. Both the JogPad corner and the Z column center need `vm.z`.

**Why it happens:** The Z readout needs to appear in two places after D-01: the JogPad's Z corner (unchanged, per D-04 "pad internals carry over") AND the center cell of the new vertical Z column (new).

**How to avoid:** Both uses read `vm.z` from the same `MoveVm`. No holder change needed. Verify in both orientations that the live Z is shown in BOTH locations.

---

## Code Examples

### Vertical 3-cell column pattern (already in ProbeCalibrateScreen.kt)

```kotlin
// Source: ProbeCalibrateScreen.kt ProbeJogPad (lines 419-462)
// This is the EXACT pattern D-01/D-02/D-08 require — already in the codebase
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

### CalibrationGate — RoutineEntry for hub list

```kotlin
// Source: CalibrationGate.kt (existing)
data class RoutineEntry(
    val routine: CalibrationRoutine,
    val isSupported: Boolean,
)
// Already provides the supported-first ordered list via CalibrationHubHolder.routines
// No changes needed — the new ListRow hub consumes this directly
```

### D-09 Back suppression at nav layer

```kotlin
// Source: AppShell.kt (to be added in Wave 0)
composable<NavDest.CalibrationProbe> {
    val vm by probeCalibrateHolder.vm.collectAsStateWithLifecycle()
    // Explicit nav-layer back suppression while session is Active (D-09 upgrade from T-09-06-02)
    BackHandler(enabled = vm.state == ProbePageState.Active || vm.state == ProbePageState.Idle &&
        ("probe_calibrate" in inFlight || "z_endstop_calibrate" in inFlight)) {
        // swallow — must Accept or Abort
    }
    ProbeCalibrateScreen(
        container = container,
        holder = probeCalibrateHolder,
        onBack = { navController.popBackStack() },
    )
}
```

### BedMesh FieldMode sealed class (D-11/D-12/D-13)

```kotlin
// Source: P26 FieldMode pattern (SpoolScreen, TemperatureScreen)
sealed class MeshFieldMode {
    data object ProfileList : MeshFieldMode()
    data class SaveName(val prefill: String) : MeshFieldMode()
}

// In BedMeshScreen field = { when (fieldMode) {
//   is MeshFieldMode.ProfileList → ListBlock(profile ListRows) + FootButtonBar
//   is MeshFieldMode.SaveName   → Column(TokenTextField + validation text) + FootButtonBar
// }}
```

### FOOT_GUN_DESTS update (D-07 Option A)

```kotlin
// Source: NavDest.kt (to be updated)
val FOOT_GUN_DESTS: Set<NavDest> = setOf(
    NavDest.Move,
    NavDest.Extrude,
    // After D-07: replace the single Calibration entry with all 6 sub-dests
    NavDest.CalibrationHub,
    NavDest.CalibrationProbe,
    NavDest.CalibrationBedMesh,
    NavDest.CalibrationScrewsTilt,
    NavDest.CalibrationZTilt,
    NavDest.CalibrationQgl,
)
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact for P27 |
|--------------|------------------|--------------|----------------|
| Hub tile grid (LazyVerticalGrid 3×fixed) | List+Focus (ListBlock + DetailCard) | P23 kit + P27 D-05 | Hub screen fully rebuilt |
| In-screen `when(calibrationRoutine)` sub-nav | Real NavDest routes | P27 D-07 | Major structural change at AppShell |
| Full-screen Save/Load dialogs (BedMesh) | FieldMode Field-takeovers | P26 D-06 pattern | Both BedMesh overlay composables deleted |
| `gutter = { Row { … } }` actions | `gutter = null` + `FootButtonBar` in field | P26 mandatory | All 6 screens must be migrated |
| DistanceSelector row-of-6-tiles | Vertical 3-cell stepper column | P27 D-02 | DistanceSelector composable deleted |
| ZRow (horizontal 3-cell) | Vertical Z column (vertical 3-cell) | P27 D-01 | ZRow composable deleted/replaced |
| ProbeJogPad (2 vertical cols, no StepDisplay) | 2 vertical 3-cell cols with StepDisplay center | P27 D-08 | ProbeJogPad already close; needs StepDisplay in Z-nudge col too |
| In-screen `BackHandler` in ProbeCalibrate | NavHost-level `BackHandler` | P27 D-09 | Back-suppression now covers system Back gesture |

**Deprecated/outdated after P27:**
- `DistanceSelector` composable — deleted; replaced by vertical stepper column
- `ZRow` composable — deleted; replaced by vertical Z column
- `RoutineTile` composable — deleted; replaced by `ListRow` in the hub
- `HubActionControl` composable — deleted; replaced by `OutlinedControl` inside `FootButtonBar`
- `MeshDialog` enum + `SaveNameDialog` + `LoadSelectorDialog` composables — deleted; replaced by `MeshFieldMode` sealed class + in-field rendering
- `ProbeGutterButton`, `ScrewsTiltActionControl`, `TiltGutterButton`, `MeshGutterButton`, `MeshFieldButton` — deleted; replaced by `OutlinedControl` inside `FootButtonBar`
- `ShellNavState.calibrationRoutine` field — removed; replaced by NavHost back-stack

---

## Runtime State Inventory

Not applicable. This is a pure UX migration phase with no data migration, no stored state rename, no OS-registered state, no service config changes, and no build artifacts that need updating beyond a normal source rebuild.

---

## Environment Availability

Step 2.6: SKIPPED — this phase is purely code changes within the existing project. The standard build environment (Windows-side `gw.bat`, JDK 21, Android SDK, `adb` to flox) is unchanged from prior phases.

**On-device UAT requirement:** flox (Nexus 7 2013 / LineageOS 18.1 / API 30 / Adreno 320) is required for SC-1 (Move/Motion jog controls) and SC-2 (calibration hub + routines) UAT. Owner drives the printer; UAT is owner-conducted.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit4 + Kotlin host tests (existing) |
| Config file | none (inline; run via Gradle) |
| Quick run command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.calibration.* --tests works.mees.dinghy.ui.move.* --no-daemon"` |
| Full suite command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| Build gate command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon"` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | Notes |
|--------|----------|-----------|-------------------|-------|
| SC-1 | Move/Motion jog controls spatial grid intact, both orientations | on-device UAT | — | Owner-conducted; adb gfxinfo for perf gate |
| SC-2 | Calibration Hub + all 5 routine screens functional | on-device UAT | — | Owner-conducted |
| SC-3 | FootButtonBar conformance (≥64px, D-17 UAT-1..UAT-5) | on-device UAT + Preview | `gw.bat :app:assembleDebug` | Preview matrices verify at build time |
| SC-4 | @Preview matrices ship with each screen | build gate | `gw.bat :app:assembleDebug` | Compile-time verification |
| SC-5 | No functional regressions — homing/jog, probe/mesh/screws intact | host unit tests | Quick run command above | Existing holder/state-machine tests must stay green |

### Sampling Rate
- **Per task commit:** `gw.bat :app:assembleDebug --no-daemon` (compile gate)
- **Per wave merge:** `gw.bat :app:testDebugUnitTest --no-daemon` (full host suite)
- **Phase gate:** Full suite green + on-device owner UAT before `/gsd-verify-work`

### Wave 0 Gaps

The following are needed before implementation waves begin:

1. **Hub routine icon registration** — the 5 routine glyphs (`architecture`, `vertical_align_center`, `crop_square`, `grid_on`, `straighten`) are currently used as raw ligature names in `CalibrationHubScreen.kt`. They must be added to `DinghyIcons.kt` as registered tokens **OR** confirmed by the owner that the existing ligature usage is acceptable for migration. **ICON LAW (D-16): ASK owner before assigning.**

2. **NavDest sub-members** (Option A) — `NavDest.kt` must add 6 new `@Serializable data object` members, update `knownNavDests`, and update `FOOT_GUN_DESTS`. `NavDestRoundTripTest` must cover the new members.

3. **ShellNavState.calibrationRoutine removal** — `ShellNavState.kt` and `AppShell.kt` must remove the old field and BackHandler that uses it. All 6 calibration screen wiring must move to the NavHost `composable<NavDest.CalibrationXxx>` blocks.

4. **@Preview scaffold files** — `preview/CalibrationPreviews.kt` and `preview/MovePreviews.kt` need stubs (compiling fail() bodies) so the preview compile gate can be declared in Wave 0.

None found in this category: "test files for existing functionality." — Existing calibration holder tests (`CalibrationHubHolderTest`, `ProbeCalibrateHolderTest`, `BedMeshHolderTest`, `ScrewsTiltHolderTest`) are already present and must stay GREEN throughout the migration (SC-5 gate).

---

## Security Domain

Standard P26 assessment applies. This phase has no network-facing new surface, no auth changes, no new data storage. The one relevant ASVS category is V5 (input validation):

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V5 Input Validation | YES (BedMesh profile name) | `PrinterCommands.isValidProfileName` (existing; already gated) |
| All others | No | — |

The alphanumeric keyboard for the BedMesh save-name field (D-13) is the sanctioned carve-out. The IME input is validated by `PrinterCommands.isValidProfileName` before any dispatch — this guard is explicitly preserved in D-13 and must remain in the FieldMode takeover implementation.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Option A (add 6 NavDest sub-members) is the correct D-07 nav approach | Architecture Patterns — D-07 | Option B (nested NavHost) would change AppShell structure significantly; planner should validate |
| A2 | The 5 hub routine glyph names (`architecture`, `vertical_align_center`, etc.) are registered in the Geist v2.944 font — confirmed by Phase 18.1 `verify_ligatures.py` | Don't Hand-Roll | Low risk — font was exhaustively verified in P18.1 |
| A3 | `BedMeshHolder` exposes `profileNames: StateFlow<List<String>>` readable as the profile Field list | Code Examples | Verified in BedMeshScreen.kt line `vm.profileNames` |
| A4 | The `starting` probe state (`vm.state == Idle && dispatch key in inFlight`) should also suppress system Back | Common Pitfalls — Pitfall 6 | If wrong, Back during the klicky attach sequence could abort the session silently |

---

## Open Questions

1. **Hub routine icon registration (Wave 0 blocker for D-16)**
   - What we know: 5 routine glyphs are currently raw ligature strings, not `DinghyIcons` tokens.
   - What's unclear: Are any of these already in `material-icon-bucket.json` under different names? Do any conflict with existing tokens?
   - Recommendation: Wave 0 investigation task — grep `DinghyIcons.kt` and `material-icon-bucket.json` for each of the 5 glyph names; if not found, STOP and ask the owner per D-16 before registering.

2. **Move screen foot-button placement (Claude's Discretion)**
   - What we know: Owner is open to foot buttons (Home All / Disable / Back) sitting under the Z/distance columns section.
   - What's unclear: Does "under the columns" mean the FootButtonBar is only under the right-side columns half (landscape), or full-width? Portrait ambiguity.
   - Recommendation: Default to full-width FootButtonBar at the bottom of the entire screen (landscape and portrait) — the standard P23/P26 pattern. Owner reviews at UAT.

3. **ProbeJogPad Z-nudge column (D-08) — StepDisplay in the middle cell**
   - What we know: D-08 says adopt "the same paired vertical columns as Move: a vertical Z-nudge column (▲ / live-Z readout / ▼ firing TESTZ ±step) beside a vertical step column."
   - What's unclear: The current ProbeJogPad Z-nudge column has only 2 cells (up / down) with `weight(1f)` each. D-08 adds a CENTER cell (the live Z readout). But the current `ProbeFocus` already shows the live Z in the Focus (the big accent number). Is the Z readout in the center of the Z-nudge column redundant?
   - Recommendation: Include the Z readout in the center cell as D-08 specifies (muscle-memory consistency with the Move Z column). The Focus shows the resulting offset; the column center shows the live position — both are useful context during fine paper-test nudging.

---

## Sources

### Primary (HIGH confidence)
- `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` — read directly; full understanding of current layout, DistanceSelector, ZRow, JogPad
- `app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt` — read directly; current tile-grid hub
- `app/src/main/java/works/mees/dinghy/ui/calibration/ProbeCalibrateScreen.kt` — read directly; state machine, TESTZ columns, gutter branches
- `app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt` — read directly; full-screen dialogs, profile flow
- `app/src/main/java/works/mees/dinghy/ui/calibration/ScrewsTiltScreen.kt` — read directly; bed scale, point list
- `app/src/main/java/works/mees/dinghy/ui/calibration/TiltScreen.kt` — read directly; TiltVariant, gutter
- `app/src/main/java/works/mees/dinghy/designsystem/components/FootButtonBar.kt` — read directly; P26 mandatory rule and placement
- `app/src/main/java/works/mees/dinghy/designsystem/components/IncrementPicker.kt` — read directly; step-selector pattern
- `app/src/main/java/works/mees/dinghy/designsystem/components/AdjusterPanel.kt` — read directly; 3-zone adjuster
- `app/src/main/java/works/mees/dinghy/designsystem/components/ListRow.kt` — read directly; selection fill pattern
- `app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt` — read directly; existing NavDest sealed interface + FOOT_GUN_DESTS
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` — grep; calibration routing lines 687–729, BackHandler at line 510–514
- `.planning/phases/27-motion-calibration/27-CONTEXT.md` — canonical phase decisions
- `.planning/phases/26-adjustment-screens/26-PATTERNS.md` — P26 patterns (FieldMode, FootButtonBar rule, gutter=null law)
- `.claude/skills/sketch-findings-dinghy-display/SKILL.md` — jiib redesign grammar, UAT-1..UAT-5

### Secondary (MEDIUM confidence)
- `.planning/archive/26-adjustment-screens/26-CONTEXT.md` — P26 decisions (D-15 specialized-layout exemption precedent; D-20 increment picker; D-06 FieldMode replaces pushes)
- `.planning/phases/26-adjustment-screens/26-PATTERNS.md` — proven implementation patterns from P26 execution
- `app/src/main/java/works/mees/dinghy/calibration/CalibrationHubHolder.kt` — RoutineEntry flow
- `app/src/main/java/works/mees/dinghy/calibration/CalibrationGate.kt` — CalibrationRoutine enum + calibrationSupport()
- `app/src/main/java/works/mees/dinghy/ui/move/MoveHolder.kt` — MoveVm fields (xHomed, yHomed, zHomed)

---

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — existing project stack, no new dependencies
- Architecture (D-07 route conversion): HIGH — NavDest sealed interface is well-understood; two options documented; Option A recommended with clear rationale
- Architecture (screen layouts): HIGH — all six current screens read directly; P26 patterns verified in execution
- Pitfalls: HIGH — drawn from actual P26 execution experience and code inspection

**Research date:** 2026-06-11
**Valid until:** 2026-07-11 (stable Android Compose project; internal codebase research)
