# Phase 7 Pattern Map - Files & Print Control

**Phase:** 07 - Files & Print Control - Core Print-Loop Gate
**Generated:** 2026-06-02
**Status:** planning reference

## Pattern Summary

Phase 7 should extend existing seams rather than introduce a new app architecture. The closest local patterns are:

- Phase 6 command registry and drift tests for every Moonraker operation.
- Phase 4/5 service-published `SpineHandle` plus `AppContainer` convenience flows.
- Phase 4/5 holder pattern: service/session constructs long-lived state; UI receives narrow typed handles.
- Phase 4/5 shell routing: one lean `Dest` enum, no Navigation-Compose.
- Phase 3/4 design primitives: `ScreenScaffold`, `OutlinedControl`, `ConfirmGuard`, `SeverityToast`, `MaterialSymbol`.
- Phase benchmark/ADR guidance: classic Views/RecyclerView for high-churn Files list content.

## Source Analogs

| Pattern | Primary Files | Apply In Phase 7 |
|---|---|---|
| Typed command registry specs | `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt`, `app/src/main/java/works/mees/dinghy/net/JsonRpc.kt` | Add Phase 7 method constants and register only commands the app dispatches at runtime, with typed args, keys, params, and availability predicates before UI uses them. |
| Registry sidecar drift | `app/src/test/java/works/mees/dinghy/command/CommandCatalogDriftTest.kt`, `docs/commands/catalog.json`, `docs/commands/printer-matrix.json` | Every new `CommandRegistry.all` entry must exist in both command docs sidecars in the same plan wave. |
| Dispatcher action path | `app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt`, `CommandDispatchExtensions.kt` | Start/delete/pause/resume/cancel/restart should use dispatcher semantics or a facade built over the dispatcher; do not dispatch raw strings in UI. |
| Session spine publication | `app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt`, `di/SpineHandle.kt`, `di/AppContainer.kt` | Add a narrow `FileBrowserClient` / holder to the session handle. Do not expose raw `JsonRpcClient` to screens. |
| One-shot holder tests | `ui/printstatus/PrintMetadataHolder.kt`, `PrintMetadataHolderTest.kt`, `LastJobHolderTest.kt` | Use injected fake fetch/client seams plus `MutableStateFlow<PrinterState>` under `runTest(UnconfinedTestDispatcher())` for Files holder tests. |
| Pure metadata parser | `state/PrintMetadata.kt`, `PrintMetadataParseTest.kt` | Add/extend pure file preview parser and path helpers with faithful JSON fixtures, null-safe walking, and no UI dependencies. |
| Shell routing | `ui/route/TopRoute.kt`, `ui/shell/AppShell.kt`, `ui/shell/AppDrawer.kt` | Add `Dest.Files`, make the drawer tile live, add a `when(dest)` branch, and update `ShellPresenceTest`. |
| Confirm guard | `designsystem/ConfirmGuard.kt` plus existing callers in Move/Print Status | Add optional `cancelLabel` while preserving default `"Cancel"` for existing callers; use UI-SPEC labels for Files and Print Status print-control guards. |
| High-churn list | `bench/ViewsBenchScene.kt`, `docs/adr/0001-ui-toolkit-decision.md` | Use production RecyclerView/ListAdapter/DiffUtil for Files Field. The benchmark `notifyDataSetChanged()` is stress-only and should not be copied. |
| Print Status gutter | `ui/printstatus/PrintStatusScreen.kt` | Replace disabled Tune/Pause placeholders with state-adaptive action model; keep Stop emergency path distinct. |

## Pattern Rules

### Registry And Commands

- Add Phase 7 Moonraker methods as constants in `JsonRpcMethods`.
- Add `CommandRegistry.all` entries only for Phase 7 commands with real runtime dispatch call sites. Keep `server.files.roots` and `server.files.list` catalog/reference-only unless execution proves the app dispatches them.
- Add typed arg classes in `CommandRegistry.kt`:
  - directory args with optional path and `extended`.
  - filename args for metadata/thumbnails/start.
  - delete args with root-prefixed path.
  - unit args for pause/resume/cancel.
- Use `AvailabilityPredicate.ComponentPresent("file_manager")` for file manager calls.
- Use explicit object predicates for print-control calls:
  - `printer.print.start` uses `AvailabilityPredicate.ObjectPresent("virtual_sdcard")`.
  - `printer.print.pause`, `printer.print.resume`, and `printer.print.cancel` use `AvailabilityPredicate.ObjectPresent("pause_resume")`.
  - `print_stats` and `pause_resume` should be represented in matrix evidence, subscription/reducer tests, and print-status control tests.
- Update `CommandRegistry.all`, `catalog.json`, and `printer-matrix.json` together.

### File Path Model

Keep file path forms distinct in code and tests:

- `relativeFilename`: path relative to `gcodes`, used by metadata, thumbnails, and start print.
- `rootPrefixedPath`: `gcodes/<relativeFilename>`, used by delete.
- `directoryPath`: Moonraker directory path for browsing, e.g. `gcodes` or `gcodes/subdir`.
- `displayPath`: breadcrumb/path chip for UI only.

The parser/model layer should own these conversions so UI code does not concatenate command paths.

### Review-Incorporated Constraints

- Pause/resume/cancel state confirmation is a Wave 1 reducer/subscription contract, not a late UI conditional. Tests must prove pause -> paused -> resume -> printing -> cancel from observed state.
- Files thumbnail loading must use explicit request sizing, downsample behavior, and bounded cache semantics before large-library UAT.
- Long-press graceful cancel needs both the existing long-press affordance and an accessibility custom action named `Cancel print`.
- Final Phase 7 verification must execute `ShellPresenceTest` on flox hardware and capture `dumpsys gfxinfo ... framestats` for a large mixed thumbnail/no-thumbnail Files library.

### Session Surface

Preferred shape:

```kotlin
interface FileBrowserClient {
    suspend fun getDirectory(path: String?, extended: Boolean = false): JsonElement?
    suspend fun getMetadata(filename: String): JsonElement?
    suspend fun getThumbnails(filename: String): JsonElement?
    fun deleteFile(path: String)
    fun startPrint(filename: String)
    fun pausePrint()
    fun resumePrint()
    fun cancelPrint()
}
```

The exact return types can be stronger than `JsonElement?` if the plan implementation chooses to parse below the client, but the surface must stay narrow and session-owned.

### Files Holder

The holder should be plain Kotlin and testable without Compose:

- Inputs: coroutine scope, `PrinterState` flow, `Capabilities` flow or snapshot, session/client facade, HTTP base if thumbnail URLs are built at holder level.
- State: current folder, entries, selected entry, preview, loading/error, pending action marker.
- Behavior:
  - load current folder with best-effort errors.
  - navigate folder and Up row without touching Android Back.
  - select row and lazily fetch preview metadata/thumbnails.
  - start print through ConfirmGuard callback and clear pending only on state-confirmed filename/lifecycle flip.
  - delete only when idle; after success remove row, clear selection/preview, stay in folder.
  - clear stale pending markers on incompatible state/session changes.

### Files UI

- Compose owns Focus preview and Gutter actions.
- RecyclerView owns Field rows.
- Rows are fixed-height, compact, and stable; selection changes should not remeasure the list.
- Thumbnail-less folder/file states have distinct placeholders.
- Portrait: Field-first browsing until selection; Focus appears after selection.
- Landscape: Focus + Field split.
- No search box, keyboard, upload, slicing, row long-press delete, or continuous animation.

### Print Status Gutter

Use a pure control model where practical, then consume it in `PrintStatusScreen`:

- Printing: Tune disabled, Pause tap dispatches pause, Pause long-press opens graceful cancel guard, Stop remains emergency stop.
- Paused: Tune disabled, Resume tap dispatches resume, Resume long-press opens graceful cancel guard, Stop remains emergency stop.
- Terminal with filename: Files opens `Dest.Files`, Restart opens guard then dispatches print start for that filename, Stop remains available only where existing emergency semantics allow.
- Pending labels/states clear from printer state, not from command ack.

## Tests To Copy In Style

- `CommandCatalogDriftTest`: sidecar coverage for every registry entry.
- `PrintMetadataParseTest`: faithful JSON fixtures and null-safe parse tests.
- `PrintMetadataHolderTest`: flow-driven one-shot behavior with injected fake fetch.
- `LastJobHolderTest`: holder behavior across state transitions.
- `TopRouteTest` and `ShellPresenceTest`: route/drawer behavior after `Dest.Files` becomes live.
- `MoonrakerServiceTest`: service-published handle shape when a new session-owned surface is added.

## Pattern Risks

- Same-wave file conflicts: route wiring and Print Status terminal `Files` action both want `AppShell.kt`; plan waves must sequence those edits.
- Overfetching metadata: selected-file preview should fetch rich metadata; rows should stay compact and cheap.
- Drift failures: `CommandRegistry.all` is tested against docs sidecars, so command registry work must not be separated from sidecar updates.
- UI primitive churn: `ConfirmGuard` can gain optional labels, but existing call sites must keep their current behavior by default.
- Emergency semantics: graceful cancel and emergency stop must remain separate code paths and copy paths.
