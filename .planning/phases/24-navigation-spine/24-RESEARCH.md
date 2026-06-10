# Phase 24: Navigation Spine — Research

**Researched:** 2026-06-10
**Domain:** Jetpack Navigation-Compose adoption + AppShell migration
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Spine-only migration. Migrate root + top-level drill-down destinations to NavHost.
  KEEP the four in-screen local sub-nav back-stacks (Calibration routine, Fine-Tune group,
  Outputs detail, Macros bookmarked-vs-system) as in-screen state. Do NOT promote them to
  nav routes this phase.
- **D-02:** Connection lifecycle is the TOP tier of the waterfall. Model no-printer-configured
  → connecting → Moonraker reachable → ready as the explicit top of the conditional waterfall.
- **D-03:** Wire existing surfaces only — no new onboarding. Back-stack cannot dismiss
  not-ready connection states (hard overrides).
- **D-04:** Print-state transition → pop-to-root on foot-gun screens only.
  **Pop:** Move, Extrude, Calibration if print STARTS or ENDS while user is on them.
  **Leave undisturbed:** Temperature, Macros, Fine-Tune, Console, Webcam.
- **D-05:** Data-driven typed `List<HomeAction>` idle action list, hardcoded v1 order. No
  customization editor and no inline controls this phase.
- **D-06:** v1 idle list order: `Spool → File → Move → Extrude → Macros (if bookmarks) →
  Calibration → Outputs → Webcam`.
- **D-07:** Temperature and Console are OFF the idle list. Preheat foot button covers idle
  heat prep. Fine-Tune is printing-only.
- **D-08:** Absent capabilities HIDE (drop out) the row — not grey. Spool (no Spoolman),
  Outputs (no controllable outputs), Webcam (no cam / toggle off), Macros (shown only if
  bookmarks exist).
- **D-09:** Foot button relabeled "System" (was "Power"). Neutral intent color, NOT red.
  Deliberate deviation from `layout-navigation.md` which named it "Power".
- **D-10:** No dedicated System-page screen this phase. "System" foot button opens the
  existing App Drawer (interim System hub). SC-5 satisfied via drawer.
- **D-11:** Power stays unwired. AppDrawer.kt:258 inert Power tile unchanged.
- **D-12:** App Drawer stays LIVE, as-is. Double duty: interim System hub + testing affordance.
- **D-13:** State morph = cheap one-shot cross-fade (~150ms), flox-verified. No continuous
  animation (Adreno-320 budget). Fall back to hard-cut if it janks.
- **D-14:** Floating e-stop = reuse locked behavior. Red, shown on every screen only when
  printing, top-left of Focus over preview corner. Tap → full-screen Stop Confirm guard.

### Claude's Discretion

- Exact NavHost route shape, holder lifetime/hoisting across the graph, back-stack semantics
  at root (Back at root falls through to OS), and how the connection-tier hard-overrides
  coexist with the NavHost (gate above vs redirect/start-destination switch).

### Deferred Ideas (OUT OF SCOPE)

- v2 user-customizable home action list (editor UI + InlineControlAction row variant).
- Dedicated System hub page + Power wiring (Phase 28).
- Trim/retire App Drawer's printer-action tiles.
</user_constraints>

---

## Summary

Phase 24 installs Jetpack Navigation-Compose as the drill-down back-stack host, retiring the
~1000-line `AppShell.kt` `when(dest)` + `ShellNavState` hub-and-spoke holder. The migration
is a SPINE operation: the ~20 session holders built in `AppShell` must be HOISTED ABOVE the
`NavHost` so destination changes do not tear them down. The connection-tier waterfall
(`RootController`) is reshaped from a binary Splash/Shell switch into an explicit stepped
progression, but it remains ABOVE the NavHost (gate-above pattern).

The critical constraint is that `navigation-compose:2.8.9` (the correct pin — NOT 2.9.x,
which raises minSdk to 23 but whose compileSdk-37/AGP-9 requirement kicks in at 2.10-alpha) is
both the safest and fully functional choice. Type-safe `@Serializable` routes were stabilized in
2.8.0 and are the recommended approach for new adoption. The `kotlinx.serialization` plugin is
already in the project, making this a zero-new-plugin add.

The 20 session holders are all `remember(store)`-keyed today and MUST NOT move inside the
NavHost. They hoist above the `NavHost` call site, inside the composable that creates the
`NavController`. Destinations receive their holders as parameters (the existing pattern).

**Primary recommendation:** Adopt `navigation-compose:2.8.9` with type-safe `@Serializable`
route objects. Gate the connection tier ABOVE the NavHost (mount NavHost only when in Shell
state). Hoist all session holders above NavHost in a single `AppShell`-equivalent composable.
Preserve `ShellNavState` as a thin in-screen-sub-nav carrier for the four D-01 holdouts.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|---|---|---|---|
| Connection tier waterfall | `RootController` (Compose logic above NavHost) | `TopRoute.derive` (pure function) | Hard override — must be undismissable by Back; gate-above is cleanest |
| NavHost back-stack | `NavHost` in `AppShell` | — | Standard Nav-Compose drill-down |
| Session holders (~20) | Hoisted above NavHost in `AppShell` | `AppContainer` (process-scoped) | Must survive destination changes; re-keyed on spine rebuild |
| In-screen sub-nav (Calib/FineTune/Outputs/Macros) | In-screen state (stays as-is per D-01) | `ShellNavState` thin carrier | Not promoted to NavHost routes this phase |
| Overlays (MacroPrompt, ScanSurface, MacroPopup) | Sibling Box above NavHost | — | Must float over ANY destination |
| PrintStatus morph (idle/printing/terminal) | `PrintStatusScreen` internal (`PrintStatusMode`) | `AnimatedContent`/`Crossfade` | One-shot, cheap; already classifies via `classifyPrintStatus()` |
| Floating e-stop | `FloatingEStop` overlay in `PrintStatusScreen`'s Focus | — | Decoupled from layout per UI LAW |
| Capability-gating (idle list hide) | `AppShell` (derived from `AppContainer` flows) | `PrintStatusScreen` for idle list | Capability flows already on AppContainer |
| Pop-to-root on print state change | `NavHost` host composable (LaunchedEffect on printState) | `navController.popBackStack` | Flow-driven single-shot pop |
| App Drawer (interim System hub) | Lives as overlay in `AppShell` (unchanged) | — | D-10/D-12: keep as-is |
| "System" foot button | `PrintStatusScreen` idle FootButtonBar | Opens existing App Drawer | D-09/D-10: neutral intent, opens drawer |

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---|---|---|---|
| `androidx.navigation:navigation-compose` | **2.8.9** | NavHost back-stack for drill-down destinations | Stable type-safe routes since 2.8.0; minSdk 21 (our floor is 23); does NOT require compileSdk 37 or AGP 9 (those are 2.10-alpha+ only) |
| `kotlinx.serialization` | 1.7.3 (already pinned) | Route `@Serializable` objects | Already in project; Navigation 2.8.x uses it for type-safe routes |

**No other new dependencies.** The `kotlin.plugin.serialization` Gradle plugin is already declared in `libs.versions.toml`.

### Version Verification

`navigation-compose` is NOT governed by the Compose BOM (confirmed via developer.android.com BOM
mapping page — BOM covers `androidx.compose.*` artifacts only, not `androidx.navigation.*`).
It must be pinned independently. [VERIFIED: developer.android.com/jetpack/androidx/releases/navigation]

**Version line decision: 2.8.9, NOT 2.9.x:**
- 2.8.9 (latest stable of the 2.8.x line, released March 12, 2025) — minSdk 21, no compileSdk
  or AGP floor beyond the project's existing 36/8.7.
- 2.9.8 (current stable) — minSdk 23 (fine for us), but the compileSdk 37 / AGP 9.2 requirement
  enters at 2.10.0-alpha. Gemini asserted 2.9.8 is the BOM-governed version; this was WRONG —
  nav is outside the BOM. The 2.9.x line is safe at AGP 8.7 for the stable branch itself, but
  the project's conservative "no AGP-9 / compileSdk-37" stance means 2.8.9 is the pinned choice.
  [VERIFIED: developer.android.com/jetpack/androidx/releases/navigation]

### Installation (libs.versions.toml additions)

```toml
[versions]
# --- Add after existing entries ---
navigation = "2.8.9"     # navigation-compose 2.8.x — minSdk 21, safe on AGP 8.7/compileSdk 36
                          # NOT BOM-governed; 2.9.x is also safe but pin 2.8 per AGP-conservative stance

[libraries]
# --- Add after existing androidx entries ---
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
```

`app/build.gradle.kts` `dependencies` block:
```kotlin
implementation(libs.androidx.navigation.compose)
```

The `kotlin-serialization` plugin is already declared — no new plugin needed.

---

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---|---|---|---|---|---|---|
| `androidx.navigation:navigation-compose` | Google Maven | ~5 yrs | Tens of millions | github.com/androidx/androidx | N/A (AndroidX first-party) | Approved |

slopcheck was not run (AndroidX first-party artifacts are not subject to npm slopcheck; they live on
`dl.google.com/dl/android/maven2/`, not npm). The artifact's provenance is beyond question.

**Packages removed:** none. **Packages flagged suspicious:** none.

---

## Architecture Patterns

### System Architecture Diagram

```
MainActivity
  └── RootController (composed always)
        ├── ShellNavState (remember above Splash/Shell — preserved across blips)
        ├── TopRoute.derive(cfgPresent, printerState)  ←── pure function
        │
        ├── [TopRoute.Connect / settingsEscape]  →  PrintersScreen (connection escape)
        │
        ├── [showSplash = rawSplash || splashHeld]  →  SplashScreen (hard override)
        │       └── onEditConnection → settingsEscape = true
        │
        └── [else → AppShell]
              │
              ├── ─── ALL SESSION HOLDERS (hoisted above NavHost) ──────────────────
              │   temperatureHolder, moveHolder, extrudeHolder, filesHolder,
              │   webcamHolder/surfaceProvider, spoolHolder, outputsHolder,
              │   calibrationHubHolder, screwsTiltHolder, zTiltHolder, qglHolder,
              │   bedMeshHolder, probeCalibrateHolder, fineTuneHolder,
              │   consoleHolder, macroHolder, promptEngine, systemInfoHolder
              │   (all remember(store)-keyed; DisposableEffect leak-cancel as today)
              │
              ├── NavHost(navController, startDestination = WaterfallHome)
              │     │
              │     ├── composable<WaterfallHome>  →  PrintStatusScreen (morphing root)
              │     │     ├── Standby: idle action list (HomeAction typed items)
              │     │     │     └── onAction(dest) → navController.navigate(dest)
              │     │     ├── Printing: narrowed list
              │     │     ├── Terminal: stats / reprint
              │     │     └── FloatingEStop overlay (printing-only, top-left Focus)
              │     │
              │     ├── composable<NavDest.Temperature>   → TemperatureScreen(holder)
              │     ├── composable<NavDest.Move>           → MoveScreen(holder)
              │     ├── composable<NavDest.Extrude>        → ExtrudeScreen(holder)
              │     ├── composable<NavDest.Files>          → FilesScreen(holder)
              │     ├── composable<NavDest.Macros>         → BookmarkedMacros/System (in-screen sub-nav)
              │     ├── composable<NavDest.Console>        → ConsoleScreen(holder)
              │     ├── composable<NavDest.Calibration>    → CalibrationHub/Routine (in-screen sub-nav)
              │     ├── composable<NavDest.FineTune>       → FineTuneHub/Group (in-screen sub-nav)
              │     ├── composable<NavDest.Webcam>         → WebcamScreen(holder)
              │     ├── composable<NavDest.Spool>          → SpoolScreen(holder)
              │     ├── composable<NavDest.Outputs>        → OutputsList/Detail (in-screen sub-nav)
              │     ├── composable<NavDest.SystemInfo>     → SystemInformationScreen
              │     ├── composable<NavDest.Devices>        → PrintersScreen
              │     ├── composable<NavDest.Theme>          → ThemeScreen
              │     ├── composable<NavDest.Settings>       → SettingsScreen
              │     └── composable<NavDest.About>          → AboutScreen
              │
              ├── ─── OVERLAYS (sibling Box above NavHost) ──────────────────────────
              │   MacroExecutionPopup (dest==Macros && popupFor!=null)
              │   ScanSurface (scanActive)
              │   PromptDialog (promptView.visible)
              │   DevThemeCyclerOverlay (devCyclerEnabled)
              │   App Drawer Dialog (drawerOpen)
              │   Bottom-edge swipe affordance (thin Bar)
              │
              └── LaunchedEffect(printState)
                    → pop-to-root if print starts/ends AND current dest is foot-gun
                      (Move, Extrude, Calibration) per D-04
```

### Recommended Project Structure

```
ui/
├── shell/
│   ├── AppShell.kt           # NavHost host + session holders (replaces the when(dest) body)
│   ├── RootController.kt     # unchanged — still owns connection-tier waterfall
│   ├── ShellNavState.kt      # trimmed: in-screen sub-nav state only (Macros/Calib/FineTune/Outputs)
│   ├── AppDrawer.kt          # unchanged (D-12)
│   └── DevThemeCyclerOverlay.kt  # unchanged
├── route/
│   ├── TopRoute.kt           # unchanged (derive() stays pure)
│   ├── Dest.kt               # renamed/split: NavDest sealed hierarchy (route objects)
│   └── HomeAction.kt         # NEW: typed data-driven idle action list model (D-05)
└── printstatus/
    └── PrintStatusScreen.kt  # morphing waterfall root (major rework)
```

### Pattern 1: Type-Safe Routes with @Serializable

```kotlin
// Source: developer.android.com/develop/ui/compose/navigation
@Serializable sealed interface NavDest {
    @Serializable data object WaterfallHome : NavDest
    @Serializable data object Temperature   : NavDest
    @Serializable data object Move          : NavDest
    @Serializable data object Extrude       : NavDest
    @Serializable data object Files         : NavDest
    @Serializable data object Macros        : NavDest
    @Serializable data object Console       : NavDest
    @Serializable data object Calibration   : NavDest
    @Serializable data object FineTune      : NavDest
    @Serializable data object Webcam        : NavDest
    @Serializable data object Spool         : NavDest
    @Serializable data object Outputs       : NavDest
    @Serializable data object SystemInfo    : NavDest
    @Serializable data object Devices       : NavDest
    @Serializable data object Theme         : NavDest
    @Serializable data object Settings      : NavDest
    @Serializable data object About         : NavDest
}

// NavHost call (hosts are hoisted above this call site):
val navController = rememberNavController()
NavHost(navController, startDestination = NavDest.WaterfallHome) {
    composable<NavDest.WaterfallHome> { PrintStatusScreen(...) }
    composable<NavDest.Move>          { MoveScreen(holder = moveHolder, onBack = { navController.popBackStack() }) }
    // ... remaining destinations
}
```

Type-safe routes compile-check navigation calls, IDE-refactorable, no runtime string-typo crashes.
[VERIFIED: developer.android.com/jetpack/androidx/releases/navigation — stable since 2.8.0]

### Pattern 2: Session Holders Hoisted Above NavHost

The critical correctness constraint: ALL `remember(store)`-keyed holders must live ABOVE the
`NavHost` call in the composition tree. Holders inside `NavHost` composable destinations are
scoped to that destination and will be recreated on every entry (tearing down loop/socket connections).

```kotlin
@Composable
fun AppShell(container: AppContainer, nav: ShellNavState, modifier: Modifier = Modifier) {
    // ALL session holders here — above NavHost
    val spine by container.spine.collectAsStateWithLifecycle()
    val store = spine?.store ?: idleStore
    val temperatureHolder = remember(store) { TemperatureHolder(scope = scope, store = store) }
    val moveHolder        = remember(store) { MoveHolder(scope = scope, store = store) }
    // ... all 20 holders, DisposableEffect leak-cancels as today ...

    // NavHost is BELOW all holders — destinations receive holders as parameters
    NavHost(navController, startDestination = NavDest.WaterfallHome) {
        composable<NavDest.Move> {
            MoveScreen(container = container, holder = moveHolder, onBack = { navController.popBackStack() })
        }
        // ...
    }
}
```

[ASSUMED: standard Compose state hoisting principle applied to NavHost context — not a NavHost-specific
official doc claim, but consistent with the Compose state-hoisting law and the project's existing pattern]

### Pattern 3: Gate-Above Connection Tier (D-02/D-03)

The connection waterfall stays ABOVE the NavHost — the NavHost only mounts when
`TopRoute.Shell` is active. This is the cleanest approach for a hard-override that Back
cannot dismiss.

**Why gate-above over redirect/start-destination:**
- **Gate-above:** NavHost never composes while not-connected. No route-guard LaunchedEffect
  needed. No risk of a frame flash. No back-stack entry to dismiss. The existing `RootController`
  already implements this correctly — the migration just replaces `AppShell`'s internal `when(dest)`
  with a `NavHost`, without changing the outer switch.
- **Redirect/LaunchedEffect guard:** composable mounts then immediately navigates away — a visible
  frame flash. Back can (in theory) return to the guarded route. Harder to host-test.
- **Dynamic start-destination:** requires rebuilding the NavHost on every connection state change —
  destroys the back-stack and retriggers holder recomposition.

`RootController`'s existing `when { showSplash → SplashScreen; else → AppShell }` IS the gate-above.
`TopRoute.derive()` stays unchanged (pure function). `AppShell` receives a `NavHost` body. No new
routing logic needed at the RootController level. [ASSUMED: judgment based on codebase analysis]

### Pattern 4: Pop-to-Root on Print State Change (D-04)

```kotlin
// Source: developer.android.com/develop/ui/compose/navigation (popBackStack API)
// Inside AppShell, after navController is created:
val printState by printerStateFlow.collectAsStateWithLifecycle()
val footGunDests = setOf(NavDest.Move, NavDest.Extrude, NavDest.Calibration)

LaunchedEffect(printState.printState) {
    // Fire on every printState change; only pop if current dest is a foot-gun
    val currentDest = navController.currentBackStackEntry?.destination?.route
    // Check if the current destination matches a foot-gun dest
    if (footGunDests.any { navController.currentBackStackEntry?.toRoute<NavDest>() != null }) {
        val popped = navController.popBackStack<NavDest.WaterfallHome>(inclusive = false)
        // popBackStack returns true if it popped, false if already at root
    }
}
```

**Double-navigation guard:** `LaunchedEffect(printState.printState)` — keyed on the STATE enum
value, not on a Flow that emits continuously. It fires once per state transition. `popBackStack`
is a no-op (returns false) if already at the root. [CITED: developer.android.com/develop/ui/compose/navigation#nav-back]

**Correct type-safe form for pop-to-root:**
```kotlin
navController.popBackStack<NavDest.WaterfallHome>(inclusive = false)
```
`inclusive = false` keeps `WaterfallHome` on the stack; `inclusive = true` would pop it too and
leave an empty stack. Use `false` here. [CITED: developer.android.com/develop/ui/compose/navigation]

### Pattern 5: Overlays Floating Above NavHost

Overlays (MacroPrompt, ScanSurface, MacroExecutionPopup, DevThemeCycler, AppDrawer) are placed
as siblings to NavHost inside a `Box`, composing on top of the NavHost regardless of the current
destination. This is identical to how they work today (outside the `when(dest)` block):

```kotlin
Box(modifier.fillMaxSize()) {
    NavHost(navController, startDestination = NavDest.WaterfallHome) { ... }

    // Overlays compose OVER the NavHost:
    if (promptView.visible) { PromptDialog(...) }
    if (nav.scanActive)     { ScanSurface(...) }
    if (drawerOpen)         { AppDrawer(...) }
    // etc.
}
```

[ASSUMED: standard Compose z-ordering — later Box children paint above earlier ones; not NavHost-
specific, consistent with existing AppShell overlay pattern]

### Pattern 6: BackHandler Priority with NavHost

BackHandler registration order = priority: the LAST-registered BackHandler gets first dibs on
Back. This is unchanged by NavHost — NavHost registers its own back handler, and in-screen
BackHandlers registered AFTER the NavHost composable (i.e., lower in the tree) take priority.
[CITED: developer.android.com search results cross-referencing Android BackHandler documentation]

The existing four in-screen sub-nav BackHandlers (Calibration/FineTune/Outputs/Macros) are
registered INSIDE their destination composables, which are inside NavHost. They naturally take
priority over NavHost's own back handling. The AppShell-level BackHandlers (drawer collapse,
prompt dismiss) move to the AppShell composable body, above NavHost — they are registered BEFORE
NavHost and therefore have LOWER priority. This is the correct ordering:

1. In-screen sub-nav BackHandler (innermost, highest priority)
2. NavHost's back handling (standard back-stack pop)
3. AppShell-level BackHandlers (drawer, prompt — outermost, lowest priority — only fire if
   nothing else consumed Back)

**Implication for migration:** The drawer `BackHandler(enabled = drawerOpen)` and prompt
`BackHandler(enabled = promptView.visible)` must remain ABOVE the NavHost to preserve their
current behavior: drawer closes before any back-stack pop, prompt dismisses before any back-stack
pop. This matches the existing registration order in AppShell.

### Pattern 7: One-Shot Cross-Fade for PrintStatus Morph (D-13)

`Crossfade` is cheaper than `AnimatedContent` for a simple opacity transition — it performs
only a fade (alpha animation) with no size/layout change. `AnimatedContent` with non-default
`transitionSpec` would add layout measurement overhead that is unnecessary for this morph.

```kotlin
// Source: developer.android.com/develop/ui/compose/animation/composables-modifiers
Crossfade(
    targetState = classifyPrintStatus(state),  // PrintStatusMode enum
    animationSpec = tween(durationMillis = 150),
    label = "PrintStatusMorph"
) { mode ->
    when (mode) {
        PrintStatusMode.Standby   -> StandbyContent(...)
        PrintStatusMode.Printing  -> PrintingContent(...)
        PrintStatusMode.Paused    -> PausedContent(...)
        is PrintStatusMode.Terminal -> TerminalContent(mode.kind, ...)
    }
}
```

D-13 specifies ~150ms. On Adreno 320: Crossfade is a single-layer alpha compositing operation
— the two composables cross-fade via alpha, both rendered to the same layer. On weak GPUs this
is lighter than `AnimatedContent` with slide + fade, which requires two separate layout
passes during the animation window.
[CITED: developer.android.com/develop/ui/compose/animation/composables-modifiers]

**Fallback:** if the 150ms crossfade causes jank on flox (measure via gfxinfo), replace with
a hard-cut (`if/else` with no animation spec) — zero GPU overhead, zero visual drama.

### Anti-Patterns to Avoid

- **Holders inside NavHost composable destinations:** They will be recreated on every navigation
  to that destination, tearing down socket connections and leaking the old instances.
- **`LaunchedEffect` for initial navigation:** Navigating in a `LaunchedEffect(Unit)` inside a
  destination fires every time the composable enters the composition (including on process
  restoration). The existing dev-gated `startDest` pattern (seeded in `ShellNavState` initializer,
  NOT a `LaunchedEffect`) is the correct model.
- **`popUpTo` with `inclusive = true` for pop-to-root:** Pops the root off the stack, leaving
  an empty NavHost — the app shows nothing. Always use `inclusive = false`.
- **Calling `navController.navigate` from a non-composable scope:** `navController` is a
  Compose state object; navigate calls must happen from the UI thread / composition context.
  Use a `LaunchedEffect` or a callback parameter.
- **Migrating in-screen sub-navs to NavHost routes this phase (D-01 violation):** Double-touching
  screens that will be rebuilt anyway in phases 25–27.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---|---|---|---|
| Back-stack management | Custom `mutableStateListOf<Dest>()` back-stack (the current `ShellNavState.backStack`) | `NavHost` + `navController.popBackStack()` | Handles predictive-back, process death restoration, deep-links, animation; hand-rolled stacks don't |
| Route type safety | String route literals | `@Serializable` data objects | Compile-time safety; string routes crash at runtime on typos |
| Destination state scoping | ViewModels per-destination | Holders hoisted above NavHost (existing pattern) | Holders are already re-keyed on spine rebuild; ViewModel scoping would fight the reconnect rekey |
| Cross-fade animation | Custom `alpha` animating state | `Crossfade` | Already handles cancellation, interruption, and the entering/exiting state lifecycle |

---

## NavHost Route Graph Sketch

```
NavDest.WaterfallHome  (start destination — the morphing root)
  │
  ├── NavDest.Temperature    [stays valid mid-print]
  ├── NavDest.Move           [foot-gun: pop-to-root on print start/end, D-04]
  ├── NavDest.Extrude        [foot-gun: pop-to-root on print start/end, D-04]
  ├── NavDest.Files          [stays valid mid-print]
  ├── NavDest.Macros         [stays valid mid-print; in-screen sub-nav: Bookmarked↔System]
  ├── NavDest.Console        [stays valid mid-print]
  ├── NavDest.Calibration    [foot-gun: pop-to-root on print start/end, D-04;
  │                           in-screen sub-nav: Hub + 5 routine pages]
  ├── NavDest.FineTune       [stays valid mid-print; in-screen sub-nav: Hub + 3 group pages]
  ├── NavDest.Webcam         [stays valid mid-print]
  ├── NavDest.Spool          [depends on Spoolman capability; in-screen: list+detail]
  ├── NavDest.Outputs        [depends on outputs capability; in-screen sub-nav: list+detail]
  ├── NavDest.SystemInfo     [valid any time]
  ├── NavDest.Devices        [valid any time]
  ├── NavDest.Theme          [valid any time]
  ├── NavDest.Settings       [valid any time]
  └── NavDest.About          [valid any time]
```

All destinations are FLAT (no nested graphs). The in-screen sub-navs (Macros, Calibration,
FineTune, Outputs) remain as in-screen state, not as nested NavHost graphs. This is per D-01.

**Back at WaterfallHome:** `NavHost` handles this — when the back-stack is empty and Back is
pressed, the Activity's default Back (close app) fires. No custom `BackHandler` needed at root.
This matches the existing behavior (`backStack.isEmpty()` → OS handles Back).

---

## State-Hoisting Map

### Above NavHost (MUST NOT move into destinations)

| Holder | Key | Leak-Cancel | Why Above |
|---|---|---|---|
| `temperatureHolder` | `remember(store)` | no (no detached collector) | session-scoped; survives route changes |
| `moveHolder` | `remember(store)` | no | session-scoped |
| `extrudeHolder` | `remember(store)` | no | session-scoped |
| `filesHolder` | `remember(fileBrowser, printerStateFlow)` | no | session-scoped |
| `webcamHolder` | `remember(store, cfg.host, activeProfileId)` | YES — `DisposableEffect { webcamHolder.cancel() }` | decode/poll loops; MUST cancel on spine rebuild |
| `webcamSurfaceProvider` | `remember(store, cfg.host, activeProfileId)` | no (abandoned with holder) | SurfaceView bridge tied to holder lifecycle |
| `spoolHolder` | `remember(store)` | YES — `DisposableEffect { spoolHolder.cancel() }` | detached collector |
| `outputsHolder` | `remember(store)` | no | session-scoped |
| `calibrationHubHolder` | `remember(store)` | no | session-scoped |
| `screwsTiltHolder` | `remember(store)` | no | session-scoped |
| `zTiltHolder` | `remember(store)` | no | session-scoped |
| `qglHolder` | `remember(store)` | no | session-scoped |
| `bedMeshHolder` | `remember(store)` | no | session-scoped |
| `probeCalibrateHolder` | `remember(store)` | no | session-scoped |
| `fineTuneHolder` | `remember(store)` | no | session-scoped |
| `consoleHolder` | `remember(store)` | YES — `DisposableEffect { consoleHolder.cancel() }` | detached collector pair |
| `macroHolder` | `remember(store, capabilitiesFlow)` | YES — `DisposableEffect { macroHolder.cancel() }` | detached combine collector |
| `promptEngine` | `remember(store)` | no (no explicit cancel — the scope manages it) | session-scoped |
| `systemInfoHolder` | via `container.systemInfoHolder` flow | no | AppContainer-owned, nullable |

**Process-scoped (survive across all sessions — already on AppContainer, no change):**
`bookmarksFlow`, `revealHiddenFlow`, `macroPrefs`, `webcamPrefs`, `profileStore`, `writeScope`

### Inside Destinations (acceptable — this is stateless or destination-lifetime data)

- `selectedOutputKey` (Outputs list↔detail toggle) — shell-local transient state; OK to stay
  either as hoisted ShellNavState field or as LaunchedEffect-reset composable state inside the
  Outputs destination.
- `drawerOpen` — stays shell-local (as today).
- `errorLines` projection — stays shell-local (derived from consoleHolder above NavHost).

### ShellNavState After Migration

`ShellNavState` slims down significantly. It no longer needs `dest`, `backStack`, or `goBack()`
(NavHost takes over those). It retains:
- `macroShowSystem` + `macroPopupFor` (Macros in-screen sub-nav, D-01)
- `calibrationRoutine` (Calibration in-screen sub-nav, D-01)
- `fineTuneGroup` (FineTune in-screen sub-nav, D-01)
- `scanActive` (QR scan overlay)
- `spoolPrefilter` (file→spool prefilter seed)
- `resetTransient()` (still called on recovery Splash return)
- `navigateTo()` is replaced by `navController.navigate()`; `goBack()` is replaced by
  `navController.popBackStack()`

The hoisting reason for `ShellNavState` still applies: it survives the Splash/Shell flip. But
it now carries only the in-screen sub-nav state (not the top-level routing).

---

## Connection-Tier Integration (D-02/D-03)

### Recommendation: Gate-Above (keep the existing RootController structure)

The existing `RootController` already implements the correct gate-above pattern:
1. `derive(cfgPresent, state)` produces a `TopRoute`
2. `when { showSplash → SplashScreen; else → AppShell }` is the gate
3. `AppShell` mounts the `NavHost` ONLY when the Shell route is active

**No changes needed to `RootController`** or `TopRoute.derive()`. Phase 24 replaces the
`when(dest) { ... }` inside `AppShell` with a `NavHost { ... }`. The connection-tier hard-
override lives above both.

This means:
- The back-stack cannot reach the connection tier — `NavHost` is not even composed while
  Splash/Connect is showing.
- `TopRoute.derive()` remains a pure function — host-testable, no navigation side-effects.
- The perceptibility latch (`splashHeld`) is undisturbed.
- `settingsEscape` (the "Edit connection" path from Splash) is undisturbed.

**D-02 "reshape as a coherent stepped progression"** is achieved by documenting the existing
`RootController` flow as the waterfall tier (no-config → Splash/Connect → Shell), not by
restructuring it.

---

## Common Pitfalls

### Pitfall 1: NavHost Tears Down Holders on Destination Entry
**What goes wrong:** A holder placed inside a destination composable (`composable<NavDest.Move>
{ MoveHolder(...) }`) is recreated every time `Move` is navigated to. Socket connections,
live data collectors, and ExoPlayer instances are destroyed and re-created, causing visible
jank and potential streaming interruptions.
**Why it happens:** NavHost creates/destroys destination composable instances as they enter/leave
the back-stack. State created with `remember` inside a destination survives only for that
back-stack entry's lifetime.
**How to avoid:** ALL session holders must be created with `remember(store)` above the `NavHost`
call in `AppShell`. Destinations receive holders as parameters.
**Warning signs:** A `remember(store)` inside a `composable<NavDest.*> { }` lambda.

### Pitfall 2: Double-Navigation from LaunchedEffect
**What goes wrong:** `LaunchedEffect(Unit)` inside a destination that calls `navigate()` fires
every time the destination enters composition — including on process restoration, config changes,
or the back-stack being re-entered. This causes unexpected extra navigation.
**Why it happens:** `LaunchedEffect(Unit)` re-fires every time the composable enters the
composition tree; it is NOT a one-shot application-lifecycle event.
**How to avoid:** Key `LaunchedEffect` on a specific state value (e.g., `printState.printState`).
Use `navController.popBackStack()` which is idempotent (returns false if already at root).
Prefer callbacks/lambdas passed into destinations for navigation rather than exposing
`navController` directly.
**Warning signs:** `LaunchedEffect(Unit)` that calls `navController.navigate()`.

### Pitfall 3: `inclusive = true` Pops the Root
**What goes wrong:** `navController.popBackStack<WaterfallHome>(inclusive = true)` removes
`WaterfallHome` from the stack, leaving an empty NavHost — the app shows a blank screen.
**Why it happens:** `inclusive = true` pops the specified destination AND everything above it.
**How to avoid:** Always use `inclusive = false` when popping to a root destination you want
to keep visible.

### Pitfall 4: Swipe-Suppress Set Must Be Maintained for NavHost Destinations
**What goes wrong:** The global swipe-up drawer gesture (detected in AppShell's BoxWithConstraints
`pointerInput`) must still be suppressed on scrollable destinations. After migrating to NavHost,
the current destination must be known to decide suppression. With the old `when(dest)` the
`dest: Dest` enum was directly available. With NavHost it requires reading
`navController.currentBackStackEntryAsState()` and comparing against route type.
**Why it happens:** NavHost doesn't expose a typed current-destination directly in the composition
— it requires `currentBackStackEntry?.toRoute<NavDest>()` or `currentBackStackEntryAsState()`.
**How to avoid:** In AppShell, collect the current back-stack entry state:
```kotlin
val navBackStackEntry by navController.currentBackStackEntryAsState()
val isScrollableDest = navBackStackEntry?.destination?.hasRoute<NavDest.Files>() == true
    || navBackStackEntry?.destination?.hasRoute<NavDest.Console>() == true
    // ... etc.
```
**Warning signs:** Swipe-to-open-drawer fighting scroll on Files/Console/Macros/Calibration after migration.

### Pitfall 5: ShellNavState Must Still Survive the Splash Blip
**What goes wrong:** The G-A1 invariant — that in-screen sub-nav state (calibrationRoutine,
fineTuneGroup, macroShowSystem) survives a transient recovery Splash — must be preserved. After
migration, `ShellNavState` (the carrier for those) is still `remember`-ed in `RootController`
and passed into `AppShell`. But `NavHost`'s own back-stack does NOT survive the Splash blip
(the NavHost is NOT composed during Splash — it is decomposed and recomposed on return).
**Why it happens:** NavHost back-stack is composition-local. When AppShell decomposes (Splash
override), the NavHost and its back-stack are destroyed.
**How to avoid:** The back-stack surviving a Splash blip is acceptable — after reconnect, the
user returns to `WaterfallHome` (the root). This is a deliberate simplification over the old
behavior where `dest` was preserved. The IN-SCREEN sub-nav state (calibration routine page,
fine-tune group) IS still preserved via ShellNavState.
**Implication:** The existing G-A1 guarantee ("user returns to their screen") weakens slightly:
they return to the root (`WaterfallHome`), not the specific drill-down screen. This is the
accepted trade-off of adopting NavHost. The in-screen sub-nav state (which page they were on
WITHIN Calibration) is still preserved. Document this change explicitly in the PLAN.
**Warning signs:** User testing a reconnect while deep in a drill-down expecting to return to
that exact destination.

### Pitfall 6: The Dest Enum Must Be Migrated Without Breaking Tests
**What goes wrong:** `Dest` is currently an enum used throughout tests, `StartDestMapping.kt`,
`AppDrawer.kt`, `ShellNavState`, and `RootController`. Migrating to `@Serializable` sealed
objects requires updating every call site.
**Why it happens:** The enum is pervasive — `Dest.Files`, `Dest.PrintStatus`, etc. appear in
~25 files.
**How to avoid:** Rename to `NavDest` and introduce a `typealias Dest = NavDest` shim, OR do a
mechanical grep-and-replace in a single wave. Plan the migration as a Wave 0 compile task.
**Warning signs:** Post-migration build failure on `Dest.*` references.

### Pitfall 7: The Webcam Lifecycle Effect Must Survive NavHost
**What goes wrong:** The webcam start/stop LaunchedEffect is keyed on `(webcamHolder, dest,
lifecycleOwner)` where `dest: Dest` was the current destination enum. After migration, `dest`
is no longer available as a simple enum; the current destination must be read from NavController.
**Why it happens:** `dest == Dest.Webcam` as the lifecycle gate becomes
`navController.currentDestination?.hasRoute<NavDest.Webcam>() == true`.
**How to avoid:** Replace the `dest` parameter with the navBackStackEntry check in the
lifecycle effect. Key the effect on `navBackStackEntry` instead of `dest`.

---

## Capability Flows (confirmed in AppContainer)

These flows are already present and feed D-08's HIDE rule:

| Flow | Source | D-08 Usage |
|---|---|---|
| `container.spoolmanPresent` | `capabilities.map { it.hasComponent("spoolman") }` | Hides Spool row |
| `container.outputsPresent` | `capabilities.map { descriptors.isNotEmpty() }` | Hides Outputs row |
| `container.webcamTileEnabled` | capability × per-profile toggle | Hides Webcam row |
| `container.macroPrefs.bookmarks` | process-scoped DataStore | Hides Macros row (if empty set) |

`PrinterState.printState` (via `container.printerState`) drives the morph classifier
`classifyPrintStatus()` and the D-04 pop-to-root trigger.

---

## HomeAction Typed List Model (D-05/D-06)

```kotlin
// NEW — src/main/java/works/mees/dinghy/ui/route/HomeAction.kt
sealed interface HomeAction {
    /** A destination to navigate to. */
    data class Destination(val dest: NavDest, val label: Int /* @StringRes */, val icon: /* DinghyIcons token */ Any) : HomeAction

    /** Opens the App Drawer (the "System" foot button's onTap, and the flexible Drawer launcher tile). */
    data object OpenDrawer : HomeAction

    // v2 extension point (D-05 deferred):
    // data class InlineControl(val controlId: String) : HomeAction
}

// D-06: v1 idle list order (owner-specified). Capabilities gate hides individual rows (D-08).
fun buildIdleActions(
    spoolmanPresent: Boolean,
    bookmarksExist: Boolean,
    outputsPresent: Boolean,
    webcamEnabled: Boolean,
): List<HomeAction> = buildList {
    if (spoolmanPresent) add(HomeAction.Destination(NavDest.Spool, …))
    add(HomeAction.Destination(NavDest.Files, …))
    add(HomeAction.Destination(NavDest.Move, …))
    add(HomeAction.Destination(NavDest.Extrude, …))
    if (bookmarksExist) add(HomeAction.Destination(NavDest.Macros, …))
    add(HomeAction.Destination(NavDest.Calibration, …))
    if (outputsPresent) add(HomeAction.Destination(NavDest.Outputs, …))
    if (webcamEnabled) add(HomeAction.Destination(NavDest.Webcam, …))
}
```

[ASSUMED: design based on D-05/D-06/D-08 CONTEXT.md decisions and existing capability flow shapes]

---

## Validation Architecture

> `workflow.nyquist_validation` not explicitly set to `false` in `.planning/config.json`
> — treating as enabled.

### Test Framework

| Property | Value |
|---|---|
| Framework | JUnit 4 + `kotlinx-coroutines-test` (existing; `testDebugUnitTest` is the gate) |
| Config file | None explicit — standard Gradle test task |
| Quick run command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests \"works.mees.dinghy.ui.route.*\" -x"` |
| Full suite command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest"` |

### Phase Requirements → Test Map

| Behavior | Test Type | Automated Command | Status |
|---|---|---|---|
| `TopRoute.derive()` still pure and correct | unit | `--tests "*TopRouteTest*"` | Existing tests (13-05) — verify pass after migration |
| `buildIdleActions()` hides correctly per capability flags | unit | `--tests "*HomeActionTest*"` | Wave 0 gap — new |
| `classifyPrintStatus()` still maps correctly | unit | `--tests "*PrintStatusModeTest*"` | Existing tests (16-xx) |
| `navController.popBackStack<WaterfallHome>()` pops foot-gun dests | unit (host) | `--tests "*PopToRootTest*"` | Wave 0 gap — new (TestNavController) |
| Morph cross-fade renders without crash in @Preview | compile-time | `assembleDebug` | On-device verification |
| FloatingEStop present on every screen while printing | on-device (flox) | manual | flox UAT |
| Back at WaterfallHome closes app (not crashes) | on-device (flox) | manual | flox UAT |
| Orientation change on any drill-down preserves NavHost back-stack | on-device (flox) | manual | flox UAT |
| Recovery Splash → returns to WaterfallHome (not drill-down) | on-device (flox) | manual | Expected regression; document |
| App Drawer opens from "System" foot button | on-device (flox) | manual | flox UAT |

### Sampling Rate

- **Per task commit:** `gw.bat :app:testDebugUnitTest --tests "works.mees.dinghy.ui.*" -x`
- **Per wave merge:** `gw.bat :app:testDebugUnitTest` (full unit suite)
- **Phase gate:** Full unit suite GREEN + on-device flox UAT (morph, e-stop, both orientations,
  popBackStack on print-state-change, System foot button)

### Wave 0 Gaps

- [ ] `HomeActionTest.kt` — covers `buildIdleActions()` hide rules for all 4 capability combinations
- [ ] `PopToRootTest.kt` — covers D-04 foot-gun pop-to-root logic using `TestNavController`
- [ ] Verify `navigation-compose:2.8.9` dependency resolves and `verifyMinSdkRelease` still passes
      after adding it

*(No new test infrastructure: JUnit 4 + coroutines-test already in place)*

---

## Security Domain

`security_enforcement` is not explicitly set to `false` in `.planning/config.json` — treating
as enabled.

| ASVS Category | Applies | Standard Control |
|---|---|---|
| V2 Authentication | No | Not applicable — navigation is local-only |
| V3 Session Management | Partial | NavHost back-stack cannot reach connection-tier screens; gate-above pattern enforces this structurally |
| V4 Access Control | No | No new access control surfaces |
| V5 Input Validation | No | No new user inputs in the navigation spine itself |
| V6 Cryptography | No | No new crypto |

**Threat: unauthorized back-navigation to not-connected state** — mitigated by gate-above pattern.
The `NavHost` is not composed while Splash/Connect is active; there is no route that could
navigate back to a connection-tier screen from within the NavHost.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|---|---|---|---|---|
| Windows JDK 21 (gw.bat) | All Gradle builds | ✓ | JDK 21.0.10.7 | — |
| Android SDK / build-tools 34 | assembleDebug/Release | ✓ | Confirmed | — |
| `navigation-compose:2.8.9` | NavHost | New dep | 2.8.9 on Google Maven | — |
| flox device (Nexus 7 2013) | on-device UAT | ✓ | LineageOS 18.1 / API 30 | — |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|---|---|---|
| A1 | Session holders inside NavHost destinations ARE recreated on destination entry (standard Compose state hoisting) | State-Hoisting Map | Holders could survive if Compose optimizes — but the safe/correct approach is to hoist regardless |
| A2 | Gate-above (RootController) is the right choice for connection-tier integration | Connection-Tier Integration | If redirect/start-dest approach is chosen instead, some RootController refactoring is needed |
| A3 | NavHost back-stack does NOT survive the Splash blip (AppShell decomposes) | Pitfall 5 | If NavHost state is somehow restored on recompose, back-stack recovery would be free; test on device to confirm |
| A4 | `buildIdleActions()` shape for the typed list model | HomeAction Typed List Model | D-05 leaves list internals to implementation; the exact sealed hierarchy may need adjustment |
| A5 | 2.8.9 is the correct pin (not 2.9.x) given the project's AGP-conservative stance | Standard Stack | 2.9.x stable branch is also safe on AGP 8.7 per official docs; 2.8.9 is the more conservative choice |
| A6 | `ShellNavState.dest` / `backStack` are replaced by NavHost internal state (back-stack no longer needs `dest` field) | ShellNavState After Migration | The `dest` field is heavily referenced in tests; mechanical migration wave needed |

---

## Open Questions

1. **Splash-blip back-stack loss: acceptable trade-off?**
   - What we know: NavHost back-stack is composition-local; AppShell decomposes on Splash.
   - What's unclear: Whether the owner is aware that mid-drill-down reconnects will now return
     to `WaterfallHome` instead of the previous screen (the current behavior preserves `dest`
     across the blip via `ShellNavState`).
   - Recommendation: Document this regression explicitly in the PLAN and confirm at the
     discussion stage. The in-screen sub-nav state IS preserved; only top-level destination
     is reset to root.

2. **`Dest` enum migration approach: typealias shim or clean rename?**
   - What we know: `Dest` enum appears in ~25+ files; tests reference `Dest.Files`, etc.
   - What's unclear: Whether a `typealias Dest = NavDest` bridge is wanted or a clean Wave 0 rename.
   - Recommendation: Clean Wave 0 rename with a mechanical grep-replace — the shim would cause
     confusion since `Dest` is an enum and `NavDest` sealed objects have different declaration syntax.

3. **Icon tokens for idle list HomeAction rows**
   - What we know: Per the HARD OWNER LAW, icons are NEVER chosen independently. The idle list
     rows need icons for each destination (Spool, File, Move, Extrude, Macros, Calibration,
     Outputs, Webcam).
   - What's unclear: Whether the planner should include icon-selection tasks or if icons are
     already determined from existing screens.
   - Recommendation: Most idle list destinations already exist as destination screens with
     established glyphs (Move uses `open_with`, Extrude uses `vital_signs`-adjacent, etc.). The
     planner should map existing DinghyIcons registry tokens to the list rows, NOT choose new
     ones. If any are missing, insert an ASK-OWNER task before implementing.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|---|---|---|---|
| String routes (`"destination/{id}"`) | Type-safe `@Serializable` route objects | Navigation 2.8.0 (Sept 2024) | Compile-time safety, no runtime string-typo crashes |
| Separate `NavigationUI` integration for Views | First-class `NavHost` Composable | Navigation-Compose inception | Compose-native, no XML navgraph |
| `ViewModel` scoped to nav back-stack entry | ViewModels OR manually-hoisted `remember` holders | Ongoing | Both valid; this project uses manually-hoisted holders |

---

## Sources

### Primary (HIGH confidence)
- [developer.android.com/jetpack/androidx/releases/navigation](https://developer.android.com/jetpack/androidx/releases/navigation) — version history, minSdk changes, compileSdk-37/AGP-9 requirement confirmed at 2.10-alpha; 2.8.9 = latest 2.8.x stable; 2.9.8 = current stable. [VERIFIED]
- [developer.android.com/develop/ui/compose/navigation](https://developer.android.com/develop/ui/compose/navigation) — type-safe routes API, `@Serializable`, `composable<T>`, `popBackStack`, overlays, BackHandler usage. [VERIFIED]
- [developer.android.com/develop/ui/compose/animation/composables-modifiers](https://developer.android.com/develop/ui/compose/animation/composables-modifiers) — `Crossfade` vs `AnimatedContent`, GPU performance notes. [VERIFIED]

### Secondary (MEDIUM confidence)
- Gemini (GEMINI_CLI_TRUST_WORKSPACE=true): confirmed navigation-compose 2.8.x type-safe routes stable since 2.8.0; noted 2.9.8 as current BOM-governed version (INCORRECT — nav is NOT in the Compose BOM; Gemini's answer was factually wrong on the BOM claim). Gemini's answer on minSdk for 2.9.x was consistent with official docs (minSdk 23). **Gemini output on version: PARTIALLY WRONG — do not rely for version pinning without official doc verification.**
- Multiple WebSearch results on BackHandler priority and NavHost interactions — consistent with "last-registered wins" principle. [MEDIUM — no single authoritative source cited]

### Codebase (HIGH confidence — direct read)
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` — full holder inventory, overlay positions, swipe-suppress set, BackHandler registrations [VERIFIED: direct read]
- `app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt` — nav state fields and hoist rationale [VERIFIED: direct read]
- `app/src/main/java/works/mees/dinghy/ui/shell/RootController.kt` — connection-tier waterfall implementation [VERIFIED: direct read]
- `app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt` — pure derive function [VERIFIED: direct read]
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusMode.kt` — four-state classifier [VERIFIED: direct read]
- `gradle/libs.versions.toml` — current dependency pins, build quartet [VERIFIED: direct read]

### Tertiary (LOW confidence — noted)
- Gemini research on state-hoisting above NavHost: not completed (rate-limited); claimed pattern is consistent with standard Compose hoisting law. [ASSUMED]

---

## Metadata

**Confidence breakdown:**
- Nav-Compose version pin: HIGH — official release notes verified directly
- Architecture (gate-above): MEDIUM-HIGH — grounded in codebase analysis + standard patterns
- State-hoisting map: HIGH — direct codebase read of all 20 holders
- BackHandler priority: MEDIUM — multiple corroborating sources, no single official spec
- Morph cross-fade (Crossfade choice): MEDIUM-HIGH — official animation docs verified
- Pitfalls: HIGH — grounded in existing project history and direct codebase read

**Research date:** 2026-06-10
**Valid until:** 90 days (stable libs; nav 2.8.x is a maintenance-only line now)
