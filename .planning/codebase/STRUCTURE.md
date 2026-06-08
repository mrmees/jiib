# Codebase Structure

**Analysis Date:** 2026-06-08

## Directory Layout

```
dinghy-display/
├── app/                          # Main Android module
│   └── src/
│       ├── main/
│       │   ├── java/works/mees/dinghy/
│       │   │   ├── DinghyApp.kt          # Application class, 6 DataStore files, AppContainer
│       │   │   ├── MainActivity.kt        # Single Activity, one DinghyTheme + RootController
│       │   │   ├── auth/                  # MoonrakerAuth — one-shot token fetch
│       │   │   ├── bench/                 # BenchActivity + synthetic scenes (perf testing)
│       │   │   ├── calibration/           # Headless calibration holders (Bed, Screws, Probe, Tilt)
│       │   │   ├── command/               # CommandSpec catalogue + dispatcher + PrinterCommands
│       │   │   ├── config/                # ConnectionConfig, ConnectionStore, Profile, ProfileStore, MoonrakerDiscovery
│       │   │   ├── designsystem/          # Shared Compose primitives (scrubbers, icons, scaffold)
│       │   │   │   ├── control/           # OutlinedControl
│       │   │   │   ├── icons/             # DinghyIcon, DinghyIcons, DinghyIconView, SpoolGlyph
│       │   │   │   └── layout/            # ScreenScaffold
│       │   │   ├── di/                    # AppContainer, SpineHandle, SessionControl
│       │   │   ├── gallery/               # GalleryScreen (dev preview gallery, not shipped)
│       │   │   ├── net/                   # Transport: MoonrakerSocket, JsonRpcClient, MoonrakerSession
│       │   │   ├── outputs/               # OutputDescriptor, OutputsGate (configfile parser), OutputsHolder
│       │   │   ├── preview/               # @Preview fixtures, PreviewTheming, SampleFixtures
│       │   │   ├── prompt/                # PromptEngine, PromptModel, PromptReducer (macro prompt protocol)
│       │   │   ├── render/                # AndroidView host wrappers + classic-Views implementations
│       │   │   ├── service/               # MoonrakerService (started FGS)
│       │   │   ├── spool/                 # SpoolmanClient, ActiveSpoolFacade, SpoolmanModels
│       │   │   ├── state/                 # PrinterState, PrinterStateStore, Capabilities, reducers
│       │   │   ├── systeminfo/            # SystemInfoHolder, SystemInfoModels, SysInfoFormat
│       │   │   ├── theme/                 # Token system, OKLCH palette, Geist font, ThemeResolver
│       │   │   │   ├── compose/           # DinghyTheme, LocalTokens
│       │   │   │   └── views/             # ThemeableView (Views token bridge)
│       │   │   ├── ui/                    # All feature screens + per-session state holders
│       │   │   │   ├── calibration/       # CalibrationHubScreen + sub-screens (5 routines)
│       │   │   │   ├── console/           # ConsoleScreen + ConsoleListView (RecyclerView host) + ConsoleHolder
│       │   │   │   ├── extrude/           # ExtrudeScreen + ExtrudeHolder
│       │   │   │   ├── files/             # FilesScreen + FileListView (RecyclerView host) + FileBrowserHolder
│       │   │   │   ├── finetune/          # FineTuneHubScreen + MotionScreen + ExtrusionScreen + FwRetractionScreen + FineTuneHolder
│       │   │   │   ├── macros/            # BookmarkedMacrosScreen + SystemMacrosScreen + MacroHolder
│       │   │   │   ├── move/              # MoveScreen + MoveHolder
│       │   │   │   ├── outputs/           # OutputsScreen + 3 detail pages + OutputsHolder
│       │   │   │   ├── printstatus/       # PrintStatusScreen + PrintStatusHolder + PrintMetadataHolder + LastJobHolder
│       │   │   │   ├── prompt/            # PromptDialog + content item composables
│       │   │   │   ├── route/             # TopRoute, Dest (enum), derive() pure function
│       │   │   │   ├── screen/            # Top-level screens: SplashScreen, SettingsScreen, AboutScreen, PrintersScreen, ThemeScreen, ThemeEditorScreen
│       │   │   │   ├── settings/          # BabystepPrefs
│       │   │   │   ├── shell/             # RootController, AppShell, AppDrawer, ShellNavState, DevThemeCyclerOverlay
│       │   │   │   ├── spool/             # SpoolScreen + SpoolHolder + SpoolPicker + scan/ sub-package
│       │   │   │   │   └── scan/          # ScanSurface, QrCodeAnalyzer, QrPayloadParser
│       │   │   │   ├── systeminfo/        # SystemInformationScreen
│       │   │   │   ├── temperature/       # TemperatureScreen + TemperatureHolder
│       │   │   │   └── webcam/            # WebcamScreen + WebcamHolder + Media3Feed + WebcamPrefs
│       │   │   └── webcam/                # WebcamsHolder (service-owned webcam enumeration)
│       │   └── res/
│       │       ├── drawable/              # VectorDrawable assets (jiib icons, launcher, Material Symbols)
│       │       ├── font/                  # Geist + GeistMono font files
│       │       ├── mipmap-anydpi/         # Launcher icons (pre-v26 fallback)
│       │       ├── mipmap-anydpi-v26/     # Adaptive launcher (foreground + monochrome)
│       │       ├── values/                # strings.xml, colors.xml, themes.xml
│       │       └── xml/                   # network_security_config.xml, file_provider_paths
│       ├── test/                          # JVM unit tests (no Android framework)
│       │   └── java/works/mees/dinghy/
│       │       ├── (many packages matching main)
│       └── androidTest/                   # Instrumented tests (real device / emulator)
│           └── java/works/mees/dinghy/
│               ├── connection/            # LiveReconnectYankTest, LiveSocketReconnectTest
│               ├── service/               # ProfileSurvivesRestartTest, ServiceSurvivesRotationTest
│               ├── smoke/                 # CleartextMoonrakerSmokeTest
│               ├── spool/                 # ScanSurfaceLifecycleTest
│               ├── ui/                    # ShellPresenceTest
│               └── webcam/               # DrawerWebcamGatingTest, WebcamLifecycleTest
├── build-logic/                           # Gradle convention plugins
├── docs/
│   ├── adr/                               # Architecture Decision Records (ADR-0001 UI toolkit)
│   ├── commands/                          # catalog.json, printer-matrix.json (D-10 drift guard source)
│   ├── ui_design/                         # LAW: CLAUDE.md, LAYOUT.md, THEMING.md, PREVIEW_AND_TOKENS.md, images/
│   └── view_specific_notes/               # Notes on Views interop surfaces
├── img/                                   # Source SVGs (jiib_icon.svg, spool.svg, jiib_name_icon_stack.svg)
├── macrobenchmark/                        # Macrobenchmark module (startup/frame metrics)
├── tools/                                 # Python/JS tooling (color-golden, gfxinfo-parser, oklch-bake)
└── .planning/                             # GSD planning artifacts (phases/, codebase/, research/, todos/)
```

## Directory Purposes

**`di/`:**
- Purpose: Process-scoped service-locator. No DI framework.
- Contains: `AppContainer.kt` (the single service-locator object), `SpineHandle.kt` (immutable per-session snapshot), `SessionControl.kt` (narrow reconnect/restart interface)
- Key files: `di/AppContainer.kt`, `di/SpineHandle.kt`

**`net/`:**
- Purpose: All networking — OkHttp WebSocket bridge, JSON-RPC client, reconnect supervisor, webcam HTTP helpers
- Contains: `MoonrakerSocket.kt`, `MoonrakerSession.kt`, `JsonRpcClient.kt`, `RpcConnection.kt`, `RpcError.kt`, `Backoff.kt`, `SnapshotPoller.kt`, `MjpegStreamDecoder.kt`, `WebcamClients.kt`, `WebcamProbe.kt`, `WebcamUrl.kt`
- Key file: `net/MoonrakerSession.kt` (reconnect loop + 6-step handshake)

**`state/`:**
- Purpose: Headless, toolkit-agnostic data model and mutable store. No Compose imports.
- Contains: `PrinterState.kt` (data class), `PrinterStateStore.kt` (dual-plane StateFlow accumulator), `PrinterStateReducer.kt`, `Capabilities.kt`, `DeriveCapabilities.kt`, `TemperatureStore.kt`, `ConsoleScrollback.kt`, `FileBrowserModels.kt`, `WebcamModels.kt`, `PrintMetadata.kt`, `PrintHistory.kt`
- Key file: `state/PrinterStateStore.kt`

**`command/`:**
- Purpose: Typed command catalogue + dispatch wrapper
- Contains: `CommandRegistry.kt` (all CommandSpecs), `CommandSpec.kt`, `CommandDispatcher.kt` (debounce/busy/timeout), `CommandDispatchExtensions.kt`, `PrinterCommands.kt` (wire-building helpers), `MacroInvocation.kt`
- Key file: `command/CommandRegistry.kt` — adding a spec here ALSO requires rows in `docs/commands/catalog.json` + `printer-matrix.json` (D-10 drift guard)

**`service/`:**
- Purpose: Android Foreground Service that owns the Moonraker spine
- Key file: `service/MoonrakerService.kt`

**`config/`:**
- Purpose: Connection and profile persistence models
- Contains: `ConnectionConfig.kt`, `ConnectionStore.kt`, `Profile.kt`, `ProfileStore.kt`, `DevConfig.kt`, `MoonrakerDiscovery.kt` (mDNS scanner)

**`calibration/`:**
- Purpose: Headless calibration-routine holders (no UI). Built in AppShell, re-keyed on store.
- Contains: `BedMeshHolder.kt`, `BedMeshModel.kt`, `CalibrationHubHolder.kt`, `CalibrationGate.kt`, `ManualProbeState.kt`, `ProbeCalibrateHolder.kt`, `ProbePresentGate.kt`, `ScrewsTiltHolder.kt`, `ScrewsTiltResult.kt`, `TiltHolder.kt`, `TiltResult.kt`

**`render/`:**
- Purpose: `AndroidView` host wrappers for classic-Views surfaces. All interop seams live here.
- Contains: `GraphViewHost.kt` + `GraphView.kt`, `BedMeshHeatmapHost.kt` + `BedMeshHeatmapView.kt`, `Media3SurfaceHost.kt` + `Media3SurfaceProvider.kt`, `WebcamViewHost.kt` + `WebcamView.kt`, `RingBuffer.kt`, `ProgressRing.kt`

**`designsystem/`:**
- Purpose: Reusable Compose UI primitives shared across feature packages
- Key files: `designsystem/ScrubberPage.kt` (numeric scrubber), `designsystem/NumpadPage.kt` (numpad input), `designsystem/layout/ScreenScaffold.kt` (Focus/Field/Gutter layout), `designsystem/icons/DinghyIcons.kt` (icon token registry)

**`theme/`:**
- Purpose: Semantic token system, OKLCH palette generator, font scale (`--fs`), theme prefs + resolver
- Key files: `theme/ThemeTokens.kt` (token type), `theme/ThemeResolver.kt` (bakes tokens from tuple), `theme/ThemePrefs.kt` (DataStore-backed prefs), `theme/compose/LocalTokens.kt` (CompositionLocal), `theme/compose/DinghyTheme.kt` (the ONE theme boundary)

**`ui/shell/`:**
- Purpose: Top-level routing authority, shell host, nav state
- Key files: `ui/shell/RootController.kt`, `ui/shell/AppShell.kt`, `ui/shell/ShellNavState.kt`, `ui/shell/AppDrawer.kt`

**`ui/route/`:**
- Purpose: Routing types and the pure `derive()` function
- Key files: `ui/route/TopRoute.kt` (sealed interface + `Dest` enum + `derive()`)

**`ui/screen/`:**
- Purpose: Screens not tied to a single feature: Splash, Settings, About, Printers/Devices, Theme, ThemeEditor
- Key files: `ui/screen/SplashScreen.kt`, `ui/screen/SettingsScreen.kt`, `ui/screen/PrintersScreen.kt`

**`ui/<feature>/`:**
- Purpose: Per-feature screen composable + per-session headless holder
- Feature packages: `printstatus/`, `files/`, `move/`, `extrude/`, `finetune/`, `macros/`, `console/`, `calibration/`, `outputs/`, `spool/`, `prompt/`, `settings/`, `temperature/`, `webcam/`, `systeminfo/`

**`preview/`:**
- Purpose: `@Preview` fixtures, sample data, preview theming utilities (shipped in debug only)
- Key files: `preview/SampleFixtures.kt`, `preview/PreviewTheming.kt`, `preview/PreviewPlaceholders.kt`

**`outputs/`:**
- Purpose: Output-controls model and the configfile parser that discovers controllable outputs
- Key files: `outputs/OutputDescriptor.kt`, `outputs/OutputsGate.kt` (parseOutputs), `outputs/OutputsHolder.kt`

**`spool/`:**
- Purpose: Spoolman API client, active-spool facade, data models
- Key files: `spool/SpoolmanClient.kt`, `spool/ActiveSpoolFacade.kt`, `spool/SpoolmanModels.kt`

**`prompt/`:**
- Purpose: Macro prompt protocol engine (state machine + reducer, no UI)
- Key files: `prompt/PromptEngine.kt`, `prompt/PromptReducer.kt`, `prompt/PromptModel.kt`

**`systeminfo/`:**
- Purpose: System information holder and models (published separately from spine)
- Key files: `systeminfo/SystemInfoHolder.kt`, `systeminfo/SystemInfoModels.kt`, `systeminfo/SystemInfoParse.kt`

**`bench/`:**
- Purpose: `BenchActivity` for synthetic Compose vs Views perf measurement (not shipped in release)
- Key files: `bench/BenchActivity.kt`, `bench/ComposeBenchScene.kt`, `bench/ViewsBenchScene.kt`

## Key File Locations

**Entry Points:**
- `app/src/main/java/works/mees/dinghy/DinghyApp.kt`: Application class; builds AppContainer + 6 DataStores
- `app/src/main/java/works/mees/dinghy/MainActivity.kt`: Single Activity; starts FGS; hosts DinghyTheme + RootController
- `app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt`: FGS; the spine owner

**Routing:**
- `app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt`: `TopRoute` + `Dest` enum + `derive()` pure function
- `app/src/main/java/works/mees/dinghy/ui/shell/RootController.kt`: Single routing authority
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt`: `when(dest)` shell router + holder construction

**DI / Service-locator:**
- `app/src/main/java/works/mees/dinghy/di/AppContainer.kt`: The service-locator (no Hilt)
- `app/src/main/java/works/mees/dinghy/di/SpineHandle.kt`: Immutable per-session snapshot

**State:**
- `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt`: Dual-plane StateFlow accumulator (central state hub)
- `app/src/main/java/works/mees/dinghy/state/PrinterState.kt`: The immutable printer snapshot data class

**Transport:**
- `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt`: Reconnect supervisor + handshake (683 lines)
- `app/src/main/java/works/mees/dinghy/net/JsonRpcClient.kt`: JSON-RPC id-correlation + notification routing

**Commands:**
- `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt`: All CommandSpecs (D-10 drift guard)
- `app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt`: Debounce + busy + timeout wrapper
- `docs/commands/catalog.json`: Drift-guard companion — must stay in sync with CommandRegistry

**Theme:**
- `app/src/main/java/works/mees/dinghy/theme/compose/LocalTokens.kt`: The `LocalTokens` CompositionLocal
- `app/src/main/java/works/mees/dinghy/theme/compose/DinghyTheme.kt`: The ONE Compose theme boundary
- `app/src/main/java/works/mees/dinghy/theme/ThemeTokens.kt`: Semantic token type definition
- `docs/ui_design/THEMING.md`: LAW — read before any theming work

**AndroidView interop:**
- `app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt`: Temperature graph host
- `app/src/main/java/works/mees/dinghy/ui/files/FileListView.kt`: File browser RecyclerView host
- `app/src/main/java/works/mees/dinghy/ui/console/ConsoleListView.kt`: Console scrollback RecyclerView host
- `app/src/main/java/works/mees/dinghy/render/Media3SurfaceHost.kt`: H.264 SurfaceView host

**Design law:**
- `docs/ui_design/CLAUDE.md`: Design philosophy + non-negotiables (read before any screen work)
- `docs/ui_design/LAYOUT.md`: Focus/Field/Gutter grammar
- `docs/ui_design/PREVIEW_AND_TOKENS.md`: Preview-first + tokenized-first build convention

**Source SVGs:**
- `img/jiib_icon.svg`: The jiib icon (tintable front-view spool, used for launcher + Print Status)
- `img/spool.svg`: The spool source SVG with front-view wound-filament spiral (18.3 color-reactive)
- `img/jiib_name_icon_stack.svg`: Brand wordmark+lockup

## Naming Conventions

**Files:**
- Screens: `<Feature>Screen.kt` (e.g. `PrintStatusScreen.kt`, `FilesScreen.kt`)
- Headless holders: `<Feature>Holder.kt` (e.g. `PrintStatusHolder.kt`, `TemperatureHolder.kt`, `FineTuneHolder.kt`)
- AndroidView hosts: `<View>Host.kt` in `render/` (e.g. `GraphViewHost.kt`, `Media3SurfaceHost.kt`)
- Classic View classes: `<Concept>View.kt` in `render/` (e.g. `GraphView.kt`, `BedMeshHeatmapView.kt`)
- Models/data classes: `<Feature>Models.kt` or descriptive (e.g. `SpoolmanModels.kt`, `FileBrowserModels.kt`)
- State reducers: `<State>Reducer.kt` (e.g. `PrinterStateReducer.kt`, `PromptReducer.kt`)

**Packages under `ui/`:**
- `ui/shell/` — routing + navigation
- `ui/route/` — routing types + pure derive function
- `ui/screen/` — top-level non-feature screens (Splash, Settings, About, Printers, Theme)
- `ui/<feature>/` — one package per feature screen

**Holders (naming discipline):**
- Headless holders live alongside their screen in the same `ui/<feature>/` package, NOT in a separate `viewmodel/` package
- Exception: `calibration/` package (root level, not under `ui/`) holds the calibration holders because they're shared across the `CalibrationHubScreen` sub-navigation

**Kotlin:**
- Classes and files: PascalCase
- Functions and properties: camelCase
- Private backing StateFlows: `_fieldName` (MutableStateFlow); public: `fieldName` (StateFlow/asStateFlow())
- DataStore keys: snake_case string literals

## Where to Add New Code

**New feature screen:**
- Screen composable: `app/src/main/java/works/mees/dinghy/ui/<feature>/<Feature>Screen.kt`
- Headless holder: `app/src/main/java/works/mees/dinghy/ui/<feature>/<Feature>Holder.kt`
- Add `Dest.<Feature>` to `ui/route/TopRoute.kt` (one line to the `Dest` enum)
- Add `when(dest) { Dest.<Feature> -> ... }` in `ui/shell/AppShell.kt`
- Add drawer tile to `ui/shell/AppDrawer.kt`
- Build the holder with `remember(store) { FeatureHolder(scope, store) }` in AppShell
- Cancel the holder in `DisposableEffect(holder) { onDispose { holder.cancel() } }` if it launches detached coroutines

**New Moonraker command:**
- Add `CommandSpec` to `command/CommandRegistry.kt`
- Add args data class to `command/CommandRegistry.kt` (or `PrinterCommands.kt` for wire-building helpers)
- Add corresponding rows to `docs/commands/catalog.json` + `docs/commands/printer-matrix.json` (D-10 — CommandCatalogDriftTest will fail otherwise)

**New one-shot handshake read:**
- Add a `MutableStateFlow<T?>` to `state/PrinterStateStore.kt` (follow the `_minExtrudeTemp` pattern)
- Add a setter `fun set<Name>(value: T?)` on the store
- Wire the read inside `MoonrakerSession.runHandshake()` using `runCatching` (best-effort, never breaks Connected)
- Forward the `StateFlow` on `di/SpineHandle.kt`
- Expose it on `di/AppContainer.kt` via `flatMapLatest`

**New DataStore preference:**
- If it's per-printer/profile: add a field to `config/Profile.kt` + `config/ProfileStore.kt`
- If it's connection-independent (like macros/webcam): create a new `DataStore<Preferences>` file in `DinghyApp.onCreate()` and a new prefs class (follow `ui/macros/MacroPrefs.kt`)
- ALWAYS route writes through `AppContainer.writeScope` intent methods — never `rememberCoroutineScope()`

**New theme token:**
- Add to `theme/ThemeTokens.kt` (the token type)
- Add backing values to `theme/BakedTokens.kt`
- Bridge to classic Views in `theme/views/ThemeableView.kt` if a View surface needs it

**New AndroidView surface (new classic-Views component):**
- View class: `render/<Concept>View.kt` (custom View, Canvas drawing)
- Host composable: `render/<Concept>Host.kt` (follows `GraphViewHost.kt` pattern: factory-once, update-push, `LocalInspectionMode` short-circuit, token bridge via params)
- Always use `Modifier.clipToBounds()` + `MATCH_PARENT` layout params for scrollable Views

**Utilities:**
- Shared Compose primitives: `designsystem/` (scrubbers, icons, scaffold, confirm guards)
- Pure functions / formatters: alongside their consumer (e.g. `systeminfo/SysInfoFormat.kt`)

## Special Directories

**`.planning/`:**
- Purpose: GSD workflow artifacts (phase plans, codebase maps, research notes, todo tracking)
- Generated: Partially (by GSD commands)
- Committed: Yes

**`docs/commands/`:**
- Purpose: `catalog.json` and `printer-matrix.json` — the D-10 command-catalog drift guard
- Generated: No (manually maintained alongside CommandRegistry)
- Committed: Yes — **CRITICAL: must be updated when CommandRegistry gains new specs**

**`docs/ui_design/`:**
- Purpose: Canonical UI law (Layout, Theming, Preview conventions, hi-fi mockups)
- Generated: No (authored by Matthew)
- Committed: Yes — **read before any screen or component work**

**`img/`:**
- Purpose: Source SVGs for all custom drawables (not generated from code)
- Committed: Yes — **check here before authoring any new drawable**

**`macrobenchmark/`:**
- Purpose: Macrobenchmark module for startup/frame metrics; also generates Baseline Profile (no-op on API 23)
- Generated: No (manual module)
- Committed: Yes

**`tools/`:**
- Purpose: Python/JS dev tooling (oklch color generation, gfxinfo frame parser, color golden tests)
- Generated: No
- Committed: Yes

---

*Structure analysis: 2026-06-08*
