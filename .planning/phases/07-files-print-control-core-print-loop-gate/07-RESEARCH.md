# Phase 7: Files & Print Control - Core Print-Loop Gate - Research

**Researched:** 2026-06-02
**Status:** refreshed
**Domain:** Moonraker file manager APIs, Moonraker print-control APIs, Dinghy command registry, hybrid Android UI seams
**Confidence:** HIGH for API shape and local integration seams; MEDIUM for final large-library behavior until measured on flox hardware.

## User Constraints

### Locked Decisions

- D-01: Default file ordering is recent-first with folders grouped. [VERIFIED: 07-CONTEXT.md]
- D-02: Rows show compact essentials only: thumbnail/icon slot, filename, modified date, and size. [VERIFIED: 07-CONTEXT.md]
- D-03: Folder navigation is breadcrumb/path chip plus an in-list Up row. Android Back preserves the app shell back-stack, not folder traversal. [VERIFIED: 07-CONTEXT.md]
- D-04: Thumbnail-less folders/files have distinct placeholders. Thumbnail failures never block browse. [VERIFIED: 07-CONTEXT.md]
- D-05: Selected-file Focus shows thumbnail first, then filename, estimated time, size, modified date, filament/weight, and layer/object-height when available. [VERIFIED: 07-CONTEXT.md]
- D-06: Start print uses the existing full-screen ConfirmGuard, not an immediate-start shortcut. [VERIFIED: 07-CONTEXT.md]
- D-07: Print start success is based on printer state flipping to that file, not on command ack. [VERIFIED: 07-CONTEXT.md]
- D-08: Metadata/thumbnail failures do not block printing. [VERIFIED: 07-CONTEXT.md]
- D-09: Delete lives in selected-file Focus actions, not per-row or long-press row actions. [VERIFIED: 07-CONTEXT.md]
- D-10: Delete is blocked while printing or paused. [VERIFIED: 07-CONTEXT.md]
- D-11: Delete uses destructive ConfirmGuard with filename/path and known size/date. [VERIFIED: 07-CONTEXT.md]
- D-12: After delete, stay in current folder, remove the row, clear selection/preview, and show success. [VERIFIED: 07-CONTEXT.md]
- D-13: Printing gutter keeps Tune / Pause / Stop; Tune remains disabled/deferred. [VERIFIED: 07-CONTEXT.md]
- D-14: Graceful cancel is long-press Pause while printing; Stop remains firmware emergency stop. [VERIFIED: 07-CONTEXT.md]
- D-15: Paused gutter shows Tune / Resume / Stop; long-press Resume also opens graceful cancel. [VERIFIED: 07-CONTEXT.md]
- D-16: Complete/error/cancelled with a known filename shows Files / Restart / Stop. [VERIFIED: 07-CONTEXT.md]

### Discretionary Choices

- Use the existing Compose shell with a classic Views/RecyclerView field for the Files list, matching the project ADR for high-churn lists. [VERIFIED: docs/adr/0001-ui-toolkit-decision.md]
- Keep start/delete/cancel/restart actions on existing ConfirmGuard primitives, adding only label customisation needed by the UI spec. [VERIFIED: 07-UI-SPEC.md]
- Add a narrow session file-browser surface through `MoonrakerService` / `SpineHandle` / `AppContainer`; do not expose raw RPC transport to UI code. [VERIFIED: app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt]

### Deferred / Out Of Scope

- Uploading, slicing, and file creation are out of scope. [VERIFIED: 07-CONTEXT.md]
- Typed search and filtering are out of scope. [VERIFIED: 07-CONTEXT.md]
- Fetching rich metadata for every row is out of scope; selected-file preview is the enrichment target. [VERIFIED: 07-CONTEXT.md]
- Deep reconnect and process-death print-loop restoration is deferred to Phase 14. [VERIFIED: 07-CONTEXT.md]
- Tune panel implementation remains deferred; Phase 7 only keeps the Tune affordance disabled where the UI spec requires it. [VERIFIED: 07-CONTEXT.md]

## Summary

Phase 7 should build one coherent print loop: browse the Moonraker `gcodes` library, inspect a selected file, start printing through ConfirmGuard, observe live print state on the existing Print Status home, and control pause/resume/graceful-cancel/restart without opening the browser. [VERIFIED: 07-CONTEXT.md]

The authoritative Moonraker docs support the whole workflow over JSON-RPC. Browsing should use `server.files.get_directory` because it returns one folder at a time and the docs recommend directory requests for UI-style browsing over walking a whole root with `server.files.list`. [CITED: https://moonraker.readthedocs.io/en/latest/external_api/file_manager/]

Print-control methods (`printer.print.start`, `printer.print.pause`, `printer.print.resume`, `printer.print.cancel`) return only command acknowledgement, so Dinghy must preserve the phase decision that user-visible success is confirmed by printer state (`print_stats`, `virtual_sdcard`, and paused state), not by the RPC return value. [CITED: https://moonraker.readthedocs.io/en/latest/external_api/printer/] [CITED: https://moonraker.readthedocs.io/en/latest/printer_objects/]

The main implementation gap is not transport; it is shaping a narrow session-owned file browsing/action surface that follows the Phase 6 command registry and existing holder patterns. [VERIFIED: app/src/main/java/works/mees/dinghy/di/AppContainer.kt] [VERIFIED: app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt]

## Architectural Responsibility Map

| Concern | Owner | Evidence | Phase 7 Direction |
|---|---|---|---|
| JSON-RPC method names and typed args | `JsonRpcMethods`, `CommandRegistry` | `JsonRpc.kt`, `CommandRegistry.kt` [VERIFIED] | Register only runtime-dispatched Phase 7 methods: directory, thumbnails, delete, start, pause, resume, and cancel. Keep roots/list reference-only unless execution adds real dispatch call sites. |
| Catalog and printer matrix drift | `docs/commands/catalog.json`, `docs/commands/printer-matrix.json`, `CommandCatalogDriftTest` | `CommandCatalogDriftTest.kt` [VERIFIED] | Promote runtime Phase 7 commands into registry plus sidecars in the same plan wave; do not add catalog-only rows to `CommandRegistry.all`. |
| Session-owned RPC access | `MoonrakerService` publishes `SpineHandle`; `AppContainer` exposes stable handles | `MoonrakerService.kt`, `SpineHandle.kt`, `AppContainer.kt` [VERIFIED] | Add a narrow `FileBrowserClient`/holder seam instead of exposing `JsonRpcClient` to UI code. |
| Pure metadata parsing | `PrintMetadata.kt` | `PrintMetadataParseTest.kt` [VERIFIED] | Extend or reuse parser for selected-file preview fields and thumbnail URL reuse. |
| High-churn file list | Classic Views inside Compose shell | `ViewsBenchScene.kt`, ADR 0001 [VERIFIED] | Production Files Field should use RecyclerView/ListAdapter/DiffUtil, not Compose lazy list for this screen. |
| Print Status controls | `PrintStatusScreen.kt` | Existing disabled Tune/Pause placeholders and Stop ConfirmGuard [VERIFIED] | Replace placeholders with state-adaptive pause/resume/cancel/restart controls. |
| Navigation | `TopRoute.kt`, `AppDrawer.kt`, `AppShell.kt` | Files tile currently inert [VERIFIED] | Add `Dest.Files`, live drawer tile, and Print Status terminal `Files` action. |

## Standard Stack

- Kotlin/JVM Android app with minSdk 23 and a Nexus 7 / Adreno 320 / 2GB performance floor. [VERIFIED: CLAUDE.md]
- Compose shell using `ScreenScaffold`, Focus/Field/Gutter layout, `OutlinedControl`, `ConfirmGuard`, semantic tokens, and Material Symbol helpers. [VERIFIED: docs/ui_design/CLAUDE.md]
- Classic RecyclerView for high-churn Files list content; Compose remains appropriate for Focus preview and Gutter actions. [VERIFIED: docs/adr/0001-ui-toolkit-decision.md]
- Coil-backed thumbnail image loading through existing HTTP thumbnail URL construction; do not hand-build new image pipelines unless tests prove the current helper cannot cover the Files preview. [VERIFIED: app/src/main/java/works/mees/dinghy/state/PrintMetadata.kt]
- Windows Gradle helper is the valid verification path from WSL: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>"`. [VERIFIED: CLAUDE.md]

## Architecture Patterns

### Moonraker File API Use

- Browse: `server.files.get_directory` with root/path scoped to `gcodes`; request `extended` only where it pays for the selected UX. [CITED: https://moonraker.readthedocs.io/en/latest/external_api/file_manager/]
- Metadata: `server.files.metadata` takes a filename relative to the `gcodes` root and returns slicer metadata, including estimated time, filament totals/weight, object height, layer count, and thumbnails where available. [CITED: https://moonraker.readthedocs.io/en/latest/external_api/file_manager/]
- Thumbnails: `server.files.thumbnails` accepts the same relative filename and may return an empty array for thumbnail-less files. [CITED: https://moonraker.readthedocs.io/en/latest/external_api/file_manager/]
- Delete: `server.files.delete_file` uses a root-prefixed path such as `gcodes/subdir/file.gcode`; delete is destructive and must be gated in UI and by printer state. [CITED: https://moonraker.readthedocs.io/en/latest/external_api/file_manager/]
- Start: `printer.print.start` takes a filename relative to `gcodes`. Pause/resume/cancel take no params. [CITED: https://moonraker.readthedocs.io/en/latest/external_api/printer/]

### State Confirmation Pattern

- Treat command acks as "command accepted", not "operation visibly complete". [CITED: https://moonraker.readthedocs.io/en/latest/external_api/printer/]
- Start is complete only when state reports the selected filename as active and print state transitions into the printing lifecycle. [CITED: https://moonraker.readthedocs.io/en/latest/printer_objects/]
- Pause/resume/cancel/restart controls should hold a pending state until `print_stats.state` / `pause_resume.is_paused` transitions match the requested operation. [CITED: https://moonraker.readthedocs.io/en/latest/printer_objects/]
- Wave 1 must make `pause_resume` a reducer/subscription contract, with golden-frame tests for pause -> paused -> resume -> printing -> cancel; later UI work consumes that seam instead of adding conditional state late. [VERIFIED: app/src/main/java/works/mees/dinghy/state]

### Path Discipline

- Keep three path forms distinct:
  - UI display path: readable breadcrumb/path chip.
  - Moonraker filename: relative to `gcodes`, used by metadata/thumbnails/start.
  - Root-prefixed file path: `gcodes/<relative>`, used by delete. [CITED: https://moonraker.readthedocs.io/en/latest/external_api/file_manager/]
- Reuse existing thumbnail URL construction for filenames with spaces and subdirectories. [VERIFIED: app/src/main/java/works/mees/dinghy/state/PrintMetadata.kt]
- Sort locally after parsing: folders grouped first; files recent-first; unknown modified times last. [VERIFIED: 07-CONTEXT.md]

### UI Pattern

- Files screen uses the approved UI contract: landscape Focus + Field split, portrait Field-first until selection, fixed Gutter actions, no search keyboard, no continuous animation. [VERIFIED: 07-UI-SPEC.md]
- Start, delete, cancel, and restart use ConfirmGuard. Copy must use the labels from `07-UI-SPEC.md`, including safe dismiss labels such as `Keep browsing`, `Keep file`, `Keep printing`, and `Not now`. [VERIFIED: 07-UI-SPEC.md]
- Delete is a selected-file Focus action only and is blocked when printing or paused. [VERIFIED: 07-CONTEXT.md]
- Stop remains firmware emergency stop and stays visually/semantically distinct from graceful cancel. [VERIFIED: 07-CONTEXT.md]

## Do Not Hand-Roll

- Do not parse JSON with string slicing; use the existing `org.json` / Kotlin model style used by `PrintMetadata`. [VERIFIED: app/src/main/java/works/mees/dinghy/state/PrintMetadata.kt]
- Do not expose `JsonRpcClient` directly to Compose screens; add a narrow file-browser/action facade through service/container seams. [VERIFIED: AppContainer.kt]
- Do not build an unbounded image cache or ad hoc bitmap pipeline; use Coil and existing thumbnail URL helpers with explicit request sizing/downsample behavior, adding only a small Files-specific helper where tests need to assert cache and decode bounds. [VERIFIED: PrintMetadata.kt]
- Do not use a Compose `LazyColumn` for the production Files list. The ADR and design direction reserve classic Views for high-churn lists. [VERIFIED: docs/adr/0001-ui-toolkit-decision.md]
- Do not use per-row delete or row long-press destructive behavior. [VERIFIED: 07-CONTEXT.md]
- Do not treat `printer.print.*` `"ok"` responses as success. [CITED: https://moonraker.readthedocs.io/en/latest/external_api/printer/]

## Common Pitfalls

- Registry/catalog drift: adding `CommandRegistry` specs without matching `catalog.json` and `printer-matrix.json` rows will fail existing drift tests. [VERIFIED: CommandCatalogDriftTest.kt]
- Path confusion: `server.files.delete_file` uses `gcodes/<relative>`, while `printer.print.start` and metadata operations use the relative filename. [CITED: https://moonraker.readthedocs.io/en/latest/external_api/file_manager/]
- Preview overfetch: fetching metadata/thumbnails for every row violates the phase scope and can jank old hardware. Fetch selected-file preview enrichment lazily. [VERIFIED: 07-CONTEXT.md]
- Ack-driven UI: marking print start, pause, resume, cancel, or restart complete from command ack violates D-07 and the UI spec pending-state requirement. [VERIFIED: 07-UI-SPEC.md]
- Stop/cancel conflation: graceful cancel is long-press Pause/Resume plus ConfirmGuard; Stop remains emergency stop. [VERIFIED: 07-CONTEXT.md]
- Back navigation confusion: Android Back should leave Files according to shell navigation, not walk folders. Folder Up is an in-list row. [VERIFIED: 07-CONTEXT.md]
- Touch target regression: all new controls must keep the 64dp target floor from the UI spec. [VERIFIED: 07-UI-SPEC.md]

## Code Examples

### Directory Browse Request Shape

```json
{
  "jsonrpc": "2.0",
  "method": "server.files.get_directory",
  "params": {
    "path": "gcodes/subdir",
    "extended": true
  },
  "id": 123
}
```

Use this to load one directory at a time. Omit `path` or use `gcodes` for the root depending on local path model tests. [CITED: https://moonraker.readthedocs.io/en/latest/external_api/file_manager/]

### Print Start Request Shape

```json
{
  "jsonrpc": "2.0",
  "method": "printer.print.start",
  "params": {
    "filename": "subdir/part.gcode"
  },
  "id": 124
}
```

The response is an acknowledgement. The UI pending state must clear from printer state observation, not from this response. [CITED: https://moonraker.readthedocs.io/en/latest/external_api/printer/]

## Validation Architecture

The phase has enough pure seams to keep automated feedback dense before live-printer UAT:

| Layer | Automated Coverage | Manual Coverage |
|---|---|---|
| Registry and command sidecars | `CommandCatalogDriftTest`, command registry unit tests | None needed beyond live command evidence already in matrix. |
| File models and path mapping | Pure JVM parser/path tests for directory, metadata, thumbnails-empty, root-prefixed delete paths, malformed JSON, and sort order | None. |
| Files holder/session seam | JVM coroutine tests with fake file client and fake printer state for browse, select, preview failure, delete clear-state, and state-confirmed start | Live browse/start confirms real Moonraker behavior. |
| Files UI routing and list | Host-side model tests where possible plus `compileReleaseKotlin`; instrumented shell presence test for live Files route | Required flox ShellPresence route proof plus large mixed-library scroll check. |
| Print Status gutter | JVM tests for control model across printing, paused, complete, error, and cancelled states | Live pause/resume/cancel against Ender 5 Plus. |
| Core print loop | Full unit suite and release Kotlin compile | Required Ender 5 Plus UAT: connect -> browse -> start -> monitor -> pause -> resume -> cancel. |

Verification commands must use the Windows Gradle helper from WSL:

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon"
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest :app:compileReleaseKotlin --no-daemon"
```

## Security Domain

| Threat | Risk | Mitigation |
|---|---|---|
| T-07-01 destructive file deletion | User deletes the wrong file or deletes during a print | Delete only from selected-file Focus, idle-only, destructive ConfirmGuard with filename/path/details, clear selection after success. |
| T-07-02 unintended print start | User starts wrong file or UI claims success early | Full-screen ConfirmGuard with thumbnail/details; pending state clears only from print-state flip to selected filename. |
| T-07-03 cancel vs emergency stop confusion | User hits emergency stop intending graceful cancel, or vice versa | Graceful cancel lives behind long-press Pause/Resume and ConfirmGuard; Stop keeps emergency semantics and styling. |
| T-07-04 command drift | Wrong Moonraker method/path/availability predicate is shipped | Registry specs, catalog rows, matrix rows, and drift tests land together before UI work. |
| T-07-05 stale pending action | UI remains pending across reconnect/session changes or reports stale success | Holder/action model observes session/printer state and clears or errors pending markers on incompatible state/session changes. |

Every implementation plan must include a `<threat_model>` section referencing these threats.

## Open Questions

All phase-planning questions are resolved enough for implementation:

- Use `server.files.get_directory` as the primary browsing API; keep `server.files.roots` and `server.files.list` reference-only unless implementation adds real runtime dispatch call sites. [RESOLVED]
- Keep metadata enrichment selected-file only. [RESOLVED]
- Use RecyclerView for production Files list. [RESOLVED]
- Route Print Status terminal `Files` action to the new `Dest.Files` screen. [RESOLVED]
- Confirm success from state, not ack, for start and print-control actions. [RESOLVED]

## Package Legitimacy Audit

No new third-party packages are required for Phase 7. Existing Kotlin coroutines, AndroidX, RecyclerView, Compose, and Coil dependencies cover the implementation. [VERIFIED: gradle/libs.versions.toml]
