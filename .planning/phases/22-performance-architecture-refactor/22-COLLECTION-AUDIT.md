# AppShell Collection Audit — 22-07 Task 1

**Date:** 2026-06-09
**Auditor:** Plan-22-07 executor (Claude Sonnet 4.6)
**Source:** AppShell.kt — all `collectAsStateWithLifecycle` sites

## Purpose

This table is the contract that Tasks 2 and 3 execute. Every `collectAsStateWithLifecycle` call in
`AppShell.kt` is classified by:

- **Holder-Lifetime Scope:** Is the flow produced by a process-lifetime source (DataStore, AppContainer
  StateFlow, or a process-lifetime holder) — **PROCESS-SCOPE** — or is it produced by a session-keyed
  holder tied to `store` (which changes on reconnect/spine-rebuild) — **SESSION-SCOPE**?
- **Classification:**
  - **STAYS** — AppShell directly renders this value (drawer, overlay, nav routing, tile gates).
  - **MOVES** — collected ONLY to pass into a `when(dest)` screen; the screen should collect
    holder.vm/stateFlow internally instead.
  - **SPLIT** — session-scoped or partially consumed by the shell; cannot safely hoist to a
    process-lifetime scope; stays in AppShell with the scope reason recorded.

---

## Classification Table

| # | Line | Variable | Source Flow | Holder-Lifetime Scope | Classification | Target / Parameter Change |
|---|------|----------|-------------|----------------------|----------------|--------------------------|
| 1 | 185 | `spine` | `container.spine` — the FGS-published SpineHandle | PROCESS-SCOPE | **STAYS** | Shell re-keys ALL holders off this value; directly controls the idle/session state |
| 2 | 186 | `capabilities` | `container.capabilities` → `spine.flatMapLatest` | SESSION-SCOPE (follows spine) | **STAYS** | Shell uses it for `canStartPrint` gate in FilesScreen and `spoolmanPresent` derivation. Also passed to FilesScreen.canStartPrint. |
| 3 | 187 | `httpBase` | `container.httpBase` → `spine.map` | SESSION-SCOPE (follows spine) | **STAYS** | Passed to `PromptDialog.httpBase` AND `FilesScreen.httpBase` — both are shell-level pass-throughs; simpler to keep here |
| 4 | 203 | `printerState` | `printerStateFlow` (spine?.printerState or idle) | SESSION-SCOPE | **STAYS** | Used in `Dest.Outputs` selected-output detail pages to seed `currentValue` off live output data (`printerState.outputs[key]`, `printerState.heaters[key]`). Cannot move without restructuring the Outputs detail routing. |
| 5 | 212 | `webcamCount` | `container.webcamCount` → `webcams.map{size}` | SESSION-SCOPE | **STAYS** | Used as parameter to `webcamMedia3Holder(...)` key; also determines default cam. Shell-side holder construction dependency. |
| 6 | 213 | `webcamEnabled` | `container.webcamTileEnabled` → `combine(webcamCount, activeProfile)` | PROCESS-SCOPE (derived from activeProfile which is DataStore-backed) | **STAYS** | Passed to `AppDrawer(webcamEnabled=)` — drawer tile gate; directly shell-rendered. |
| 7 | 218 | `cfg` | `container.connectionStore.config` — DataStore-backed | PROCESS-SCOPE | **STAYS** | Used to build `activeCfg` which keys `webcamSurfaceProvider` + `webcamHolder` (both `remember(..., activeCfg.host, ...)`). Shell-side holder construction dependency. |
| 8 | 224 | `activeProfileId` | `container.activeProfile.map { it?.id }` — inline map, DataStore-backed | PROCESS-SCOPE | **MOVES (hoist to AppContainer.stateScope)** | Used for: (a) `webcamHolder` remember key, (b) `webcamSurfaceProvider` remember key, (c) `onCyclePrinter` logic in DevCycler, (d) passed to `webcamMedia3Holder(profileId=)`. The inline `.map{}` creates a new Flow wrapper on every recomposition. Hoist as `activeProfileId: StateFlow<String?>` on AppContainer via `stateIn(stateScope, WhileSubscribed(5000), null)`. AppShell collects the hoisted StateFlow instead. |
| 9 | 227 | `activeName` | `container.activeProfile.map { it?.displayName() }` — inline map, DataStore-backed | PROCESS-SCOPE | **MOVES (hoist to AppContainer.stateScope)** | Used for: (a) `AppDrawer(activeName=)` drawer subtitle, (b) `DevThemeCyclerOverlay(printerLabel=)`. Hoist as `activeName: StateFlow<String?>` via `stateIn(stateScope, WhileSubscribed(5000), null)`. AppShell collects the hoisted StateFlow instead. |
| 10 | 297 | `spoolEnabled` | `container.spoolmanPresent` → `capabilities.map` | SESSION-SCOPE (follows spine/session capabilities) | **STAYS** | Passed to `AppDrawer(spoolEnabled=)` AND `FilesScreen(spoolmanPresent=)`. Drawer tile gate is shell-level; FilesScreen is a pass-through but removing both would need restructure. |
| 11 | 308 | `activeSpoolStatus` | `activeSpoolFlow.collectAsStateWithLifecycle()` where `activeSpoolFlow = spine?.activeSpool ?: idle` | SESSION-SCOPE | **STAYS** | Passed to `FilesScreen(activeSpoolStatus=)` as the D-01 print-start gate input. Session-scoped (follows spine). Cannot hoist to process scope. |
| 12 | 313 | `activeSpoolDetail` | `spoolHolder.activeSpoolDetail` — from SpoolHolder which is `remember(store) { ... }` | SESSION-SCOPE (keyed on store) | **STAYS** | Used to compute `drawerSpoolSwatches` — the drawer Spool tile's reactive glyph color input. Shell renders this directly via `AppDrawer(spoolSwatches=)`. |
| 13 | 323 | `outputsEnabled` | `container.outputsPresent` → `spine.flatMapLatest{store.outputDescriptors}` | SESSION-SCOPE | **STAYS** | Passed to `AppDrawer(outputsEnabled=)` — drawer tile gate; directly shell-rendered. |
| 14 | 333 | `outputRows` | `outputsHolder.rows` where holder is `remember(store) { OutputsHolder(...) }` | SESSION-SCOPE (keyed on store) | **STAYS** | Used in the `Dest.Outputs` `LaunchedEffect` selection-reset logic AND to find `selectedDescriptor` for the detail routing. The LaunchedEffect is shell-level nav state management; cannot move without restructuring Outputs routing. |
| 15 | 352 | `systemInfoHolder` | `container.systemInfoHolder` — process-lifetime StateFlow on AppContainer | PROCESS-SCOPE | **STAYS** | Passed to `SystemInformationScreen(holder=)`. The holder itself is a `StateFlow<SystemInfoHolder?>` published on AppContainer; it is already the holder reference not the vm. The screen collects its own internal state from the holder. Pattern already correct. |
| 16 | 386 | `zTiltVm` | `zTiltHolder.vm` where holder is `remember(store) { TiltHolder(...) }` | SESSION-SCOPE (keyed on store) | **MOVES** | Collected ONLY to pass as `vm = zTiltVm` to `TiltScreen` at Dest.Calibration / Z_TILT. Migrate TiltScreen to take `holder: TiltHolder`; it collects `holder.vm` internally. See Task 2. |
| 17 | 387 | `qglVm` | `qglHolder.vm` where holder is `remember(store) { TiltHolder(...) }` | SESSION-SCOPE (keyed on store) | **MOVES** | Collected ONLY to pass as `vm = qglVm` to `TiltScreen` at Dest.Calibration / QGL. Migrate TiltScreen to take `holder: TiltHolder`; it collects `holder.vm` internally. See Task 2. |
| 18 | 388 | `bedMeshVm` | `bedMeshHolder.vm` where holder is `remember(store) { BedMeshHolder(...) }` | SESSION-SCOPE (keyed on store) | **MOVES** | Collected ONLY to pass as `vm = bedMeshVm` to `BedMeshScreen` at Dest.Calibration / BED_MESH. Migrate BedMeshScreen to take `holder: BedMeshHolder`; it collects `holder.vm` internally AND owns `onCycleScaleMode`. See Task 2. |
| 19 | 389 | `probeCalibrateVm` | `probeCalibrateHolder.vm` where holder is `remember(store) { ProbeCalibrateHolder(...) }` | SESSION-SCOPE (keyed on store) | **MOVES** | Collected ONLY to pass as `vm = probeCalibrateVm` to `ProbeCalibrateScreen` at Dest.Calibration / PROBE_CALIBRATE. Migrate ProbeCalibrateScreen to take `holder: ProbeCalibrateHolder`; it collects `holder.vm` internally AND owns `onEnter`/`onAbort`. See Task 2. |
| 20 | 421–429 | `errorLines` | `consoleHolder.state.map{...}` inline map/filter chain where `consoleHolder = remember(store) { ConsoleHolder(...) }` | SESSION-SCOPE (keyed on store; consoleHolder is session-keyed) | **SPLIT** | SESSION-SCOPE: consoleHolder is re-keyed on `store` (a new session brings a new ConsoleHolder). Cannot hoist to AppContainer's process-lifetime stateScope — the consoleHolder that backs it is torn down and rebuilt on reconnect. Stays shell-side. Classify as SPLIT (used in AppShell for the PrintStatusScreen `errorLines=` param; the map/filter is a P3 opportunistic nit). **Decision: leave as-is; it is correctly session-scoped. No hoist.** |
| 21 | 462 | `dispatcher` | `container.dispatcher` → `spine.map{it?.dispatcher}` | SESSION-SCOPE (follows spine) | **STAYS** | Passed to many screens (TiltScreen, BedMeshScreen, ProbeCalibrateScreen, SpoolScreen, MacroExecutionPopup, PromptDialog BackHandler). Consumed by shell-level BackHandler for prompt dispatch. Cannot move without duplicating collection in each screen. |
| 22 | 477 | `promptView` | `promptEngine.view` where `promptEngine = remember(store) { PromptEngine(...) }` | SESSION-SCOPE (keyed on store) | **STAYS** | Consumed directly by AppShell for the swipe-suppress set (`pointerInput(dest, promptView.visible)`) AND for the prompt overlay rendering (`if (promptView.visible) PromptDialog(...)`). Shell-level UI. |
| 23 | 481 | `consoleBackfillFailed` | `store.consoleBackfillFailed` where `store = spine?.store ?: idleStore` | SESSION-SCOPE (keyed on store) | **STAYS** | Passed to `ConsoleScreen(backfillFailed=)`. Session-scoped. Cannot hoist to process scope. |
| 24 | 929 | `inFlightKeys` | `dispatcher?.inFlight` — session-scoped in-flight set | SESSION-SCOPE | **STAYS** | Nested inside `if (promptView.visible)` block; drives `promptInFlight` for PromptDialog. Session-scoped, small scope-local collect inside the overlay block. Stays as-is. |
| 25 | 981 | `devCyclerEnabled` | `container.devCyclerEnabled` → `themePrefs.devEnableFlow` DataStore-backed | PROCESS-SCOPE | **STAYS** | Gates the entire DevThemeCyclerOverlay block. Shell-level UI gating. |
| 26 | 983 | `currentOverride` | `container.themeOverride` — process-lifetime StateFlow on AppContainer | PROCESS-SCOPE | **STAYS** | Nested inside `if (devCyclerEnabled)` block; passed to `DevThemeCyclerOverlay(currentOverride=)`. |
| 27 | 988 | `profiles` | `container.profileStore.profiles` — DataStore-backed | PROCESS-SCOPE | **STAYS** | Nested inside `if (devCyclerEnabled)` block; used to compute `profileIds` for printer cycler. |

---

## Summary

| Classification | Count | Variables |
|---------------|-------|-----------|
| **STAYS** | 22 | spine, capabilities, httpBase, printerState, webcamCount, webcamEnabled, cfg, spoolEnabled, activeSpoolStatus, activeSpoolDetail, outputsEnabled, outputRows, systemInfoHolder, dispatcher, promptView, consoleBackfillFailed, inFlightKeys, devCyclerEnabled, currentOverride, profiles, + the 2 STAYS-from-SPLIT (activeSpoolDetail, errorLines) |
| **MOVES** | 4 | zTiltVm, qglVm, bedMeshVm, probeCalibrateVm |
| **MOVES (hoist to AppContainer.stateScope)** | 2 | activeProfileId, activeName |
| **SPLIT (session-scoped, stays shell-side)** | 1 | errorLines |

**Net AppShell collection reduction:** 4 MOVES removed (the four calibration *Vm) + 2 replaced by hoisted AppContainer
StateFlows = **6 shell-side collections eliminated** (from 27 to 21 shell-level collections, though the
inline .map{} churn on activeProfileId/activeName is eliminated by the hoist).

---

## Task 2 Contract (MOVES — Calibration Screens)

### Pattern reference: `ScrewsTiltScreen` (already done correctly)
```kotlin
fun ScrewsTiltScreen(container: AppContainer, holder: ScrewsTiltHolder, onBack: () -> Unit) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val vm by holder.vm.collectAsStateWithLifecycle()
    // ... screen owns all its state
}
```

### TiltScreen migration
- **Current:** `TiltScreen(vm: TiltVm, variant, tokens, dispatcher, onRunDispatched, onHome, onEnter, onBack)`
- **New:** `TiltScreen(container: AppContainer, holder: TiltHolder, variant: TiltVariant, onBack: () -> Unit)`
- Screen collects `val vm by holder.vm.collectAsStateWithLifecycle()` internally
- Screen collects `val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)` internally
- Screen calls `holder.markDispatched()` instead of `onRunDispatched()`
- Screen calls `holder.reset()` in `LaunchedEffect(Unit)` instead of `onEnter()`
- `onHome` becomes `dispatcher?.dispatch(CommandRegistry.homeAll, Unit)` inline (was already a lambda wrapper)
- **AppShell when(dest) change:** `TiltScreen(container=container, holder=zTiltHolder, variant=TiltVariant.ZTilt, onBack={nav.calibrationRoutine=null})`
- Remove 4 lines: `val zTiltVm by zTiltHolder.vm.collectAsStateWithLifecycle()` etc.

### BedMeshScreen migration
- **Current:** `BedMeshScreen(vm: BedMeshVm, tokens: ThemeTokens, dispatcher, onCycleScaleMode, onBack)`
- **New:** `BedMeshScreen(container: AppContainer, holder: BedMeshHolder, onBack: () -> Unit)`
- Screen collects `val vm by holder.vm.collectAsStateWithLifecycle()` internally
- Screen collects `val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)` internally
- Screen collects `val tokens = LocalTokens.current` (already a Compose CompositionLocal — no collection needed; tokens is already available via `LocalTokens.current` in the composable)
- Screen calls `holder.cycleScaleMode()` instead of receiving `onCycleScaleMode`
- **AppShell when(dest) change:** `BedMeshScreen(container=container, holder=bedMeshHolder, onBack={nav.calibrationRoutine=null})`

### ProbeCalibrateScreen migration
- **Current:** `ProbeCalibrateScreen(vm, tokens, dispatcher, onStartDispatched, onEnter, onHome, onAbort, onBack)`
- **New:** `ProbeCalibrateScreen(container: AppContainer, holder: ProbeCalibrateHolder, onBack: () -> Unit)`
- Screen collects `val vm by holder.vm.collectAsStateWithLifecycle()` internally
- Screen collects `val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)` internally
- Screen calls `holder.reset()` in `LaunchedEffect(Unit)` instead of `onEnter()`
- Screen calls `holder.markAborted()` instead of `onAbort()`
- `onStartDispatched` was a no-op (`{ }` in AppShell) — remove entirely
- `onHome` was `dispatcher?.dispatch(CommandRegistry.homeAll, Unit)` — inline directly
- **AppShell when(dest) change:** `ProbeCalibrateScreen(container=container, holder=probeCalibrateHolder, onBack={nav.calibrationRoutine=null})`

---

## Task 3 Contract (MOVES hoist + drawerSpoolSwatches ImmutableList)

### AppContainer.stateScope design
```kotlin
// Add to AppContainer (BELOW writeScope, above or after activeProfile):
private val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
// NOT writeScope (IO dispatcher, DataStore writes); stateScope is Default for cheap map transforms
```

### Hoisted StateFlows
```kotlin
// Replace inline .map{} in AppShell with named StateFlows on AppContainer:
val activeProfileId: StateFlow<String?> =
    activeProfile.map { it?.id }
        .stateIn(stateScope, SharingStarted.WhileSubscribed(5000), null)

val activeName: StateFlow<String?> =
    activeProfile.map { it?.displayName() }
        .stateIn(stateScope, SharingStarted.WhileSubscribed(5000), null)
```

### errorLines decision (classified SESSION-SCOPE → stays shell-side)
`consoleHolder` is re-keyed on `store` (a new session brings a new ConsoleHolder with its own `gcodeResponses`
+ `consoleBackfill`). The `errorLines` flow is `consoleHolder.state.map{...}` — its producer is session-scoped.
**Decision: errorLines stays collected in AppShell. Do NOT hoist to AppContainer.stateScope.**
This is recorded as the SPLIT classification above.

### drawerSpoolSwatches ImmutableList
```kotlin
// In AppShell, change:
val drawerSpoolSwatches: List<Color> =
    activeSpoolDetail?.filament?.colorSwatches.orEmpty().mapNotNull(::parseNormalizedHex)

// To:
val drawerSpoolSwatches: ImmutableList<Color> = remember(activeSpoolDetail) {
    activeSpoolDetail?.filament?.colorSwatches.orEmpty()
        .mapNotNull(::parseNormalizedHex)
        .toImmutableList()
}
```

### AppDrawer / DrawerTile signature change
Change `AppDrawer(spoolSwatches: List<Color>)` → `AppDrawer(spoolSwatches: ImmutableList<Color>)`.
Propagate to `DrawerTile` if it also receives the swatches.

---

## Pitfall 3 — Holder Build Trace

None of the MOVES/SPLIT rows involve a holder that is re-keyed on a collected value:
- `zTiltHolder`, `qglHolder`, `bedMeshHolder`, `probeCalibrateHolder` are all `remember(store) { ... }` — keyed on `store` (not a collected value). After removing `zTiltVm/qglVm/bedMeshVm/probeCalibrateVm`, the holder builds at lines 366-385 STAY in AppShell unchanged. ✓
- `activeProfileId` is used as a key in `remember(store, activeCfg.host, activeProfileId) { ... }` for `webcamHolder` and `webcamSurfaceProvider`. After the hoist, AppShell collects `container.activeProfileId` (the hoisted StateFlow) instead of the inline `.map{}` — the collected value is the same type (`String?`), so the remember key semantics are unchanged. ✓
