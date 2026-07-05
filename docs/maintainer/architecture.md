# Maintainer Architecture

This document describes the current jiib architecture. It intentionally does not preserve old
`dinghy` package names, retired drawer navigation, or the retired gutter region except when naming
them as historical concepts.

## System Shape

jiib is a native Android app in package `works.mees.jiib`. Normal app runtime enters through the
single launcher `MainActivity`; the manifest also declares `BenchActivity` for benchmark harness
launches. The app turns an Android phone or tablet into a Klipper/Moonraker control surface. The
architecture is a foreground-service-owned printer session with a Compose shell and a few classic
View render surfaces where measured performance requires them.

Primary runtime path:

1. `JiibApp` creates process-wide stores and the `AppContainer`.
2. `MainActivity` starts `MoonrakerService` and hosts `RootController`.
3. `MoonrakerService` owns the live Moonraker connection and constructs a per-session spine.
4. The service publishes a whole `SpineHandle` into `AppContainer`.
5. `RootController` chooses splash, connection, printer-selection, or shell entry based on app state.
6. `AppShell` collects the current spine and routes in-shell screens through Navigation-Compose `NavDest` routes.
7. Feature holders derive screen view-model state from `PrinterStateStore`, session facades, persisted prefs, and dispatchers.

## Process And Session Ownership

`JiibApp` is the process owner. It creates the app-level DataStore instances once and constructs
`AppContainer`. Do not create duplicate DataStore instances for the same file elsewhere.

`AppContainer` is a process-scoped service locator. It exposes persistent preferences, derived flows,
and the current `StateFlow<SpineHandle?>`. It does not own the live Moonraker WebSocket, session, or
raw `JsonRpcClient`; those belong to the service. It may own process-scoped helper clients such as
webcam HTTP and one-shot connection probes.

`MoonrakerService` is the foreground service that owns the live printer session. On active-printer
changes it cancels the old session, builds a new `PrinterStateStore`, transport/client stack,
dispatcher, holders/facades, and publishes one atomic `SpineHandle`.

`SpineHandle` is the immutable per-session snapshot consumed by most UI. Treat it as the normal
boundary between the service-owned live session and the shell-owned UI tree. `SystemInfoHolder` is
the named exception: it is built per session and published on `AppContainer` beside the spine because
it needs host telemetry updates from the session `JsonRpcClient`.

## Transport And State

The transport layer uses OkHttp for WebSocket traffic and direct HTTP-style calls such as auth,
connection probes, and webcam fetches. Command, file, and Spoolman flows go through Moonraker
JSON-RPC/proxy facades rather than a production Retrofit service. `JsonRpcClient` handles JSON-RPC
request IDs and routes notifications. `MoonrakerSession` owns connect/reconnect/handshake behavior
and writes live updates into `PrinterStateStore`.

`PrinterStateStore` is the live state accumulator. It keeps the current `PrinterState`, capabilities,
one-shot handshake results, and high-rate/control-plane publication behavior. UI code should read
store-derived flows or holder view models rather than opening transport objects directly.

## Navigation

`RootController` owns the top-level routing gate before the shell: splash, connection setup, printer
selection, or the running shell. Inside the shell, `AppShell` uses Navigation-Compose with type-safe
`NavDest` routes from `ui/route/NavDest.kt`. The morphing home/root route is `NavDest.WaterfallHome`.

The old swipe-up app drawer is gone. The old `Dest` enum is gone. Do not add new code that depends
on drawer navigation or `Dest` names.

## UI Layout

The shared layout grammar is Focus/Field. `ScreenScaffold` exposes Focus and Field regions only.
The old gutter region is retired; screen actions live in `FootButtonBar` instances or screen-specific
Focus/header affordances documented by the UI design system.

Read [the UI design system](../ui_design/README.md) before changing screen structure, visual tokens,
icons, text sizing, or control intent colors.

## Compose And View Interop

Compose is the default shell and screen toolkit. Classic Views remain appropriate for high-churn or
render-heavy surfaces where the project has measured a performance need, such as RecyclerView-backed
lists or custom Canvas renderers. The accepted rationale lives in [ADR 0001](../adr/0001-ui-toolkit-decision.md).

Keep feature state toolkit-agnostic where practical: holders should produce plain Kotlin/Flow state
that Compose screens and View hosts can consume.

## Dependency Direction

Preferred dependency direction:

- `state/` contains headless state models and reducers.
- `net/` owns transport and protocol handling.
- `command/` owns command specs and dispatch helpers.
- `service/` assembles and owns the live session.
- `di/` exposes process-level wiring and the current session spine.
- `ui/` packages render Compose/View screens and shell routing.
- Feature-domain packages such as `calibration/`, `outputs/`, `spool/`, `systeminfo/`, `webcam/`, and `prompt/`
  may own headless holders, clients, and facades consumed by UI.
- `designsystem/`, `theme/`, and `render/` provide shared UI primitives, tokens, and View/Canvas hosts.

Avoid letting UI code construct sockets, sessions, or raw JSON-RPC clients. The service constructs
the live spine; UI consumes the spine.

## Local-Only Historical Docs

`.planning/` contains useful historical project memory but is gitignored and absent from normal public
clones. It may contain stale names or retired concepts. Use it for archaeology, not current truth.
