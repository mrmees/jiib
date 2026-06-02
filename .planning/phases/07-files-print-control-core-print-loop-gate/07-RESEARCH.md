# Phase 7: Files & Print Control - Core Print-Loop Gate - Research

**Researched:** 2026-06-02
**Status:** Complete
**Domain:** Moonraker file manager, print job control, existing Dinghy command registry and hybrid UI seams
**Confidence:** HIGH for API shape and local integration seams; MEDIUM for final file-list performance until measured on flox.

## Summary

Phase 7 is the first complete print-loop phase: browse a real Moonraker gcode library, preview a
selected file, start it, monitor it on the existing Print Status home, and control it with
pause/resume/cancel/restart. The implementation should extend the Phase 4-6 spine rather than add a new
transport or navigation stack.

The official Moonraker split External API docs confirm all needed operations are available over
JSON-RPC except binary file transfer/download, which is out of scope. For Phase 7, prefer
`server.files.get_directory` over `server.files.list` for browsing because `get_directory` returns one
folder at a time, avoids walking the whole root, and can request extended metadata when useful. Use
`server.files.metadata` and `server.files.thumbnails` as best-effort preview enrichment, and use
`server.files.delete_file` with a `gcodes/<relative path>` path after the idle-state ConfirmGuard.

Print start/pause/resume/cancel are direct Moonraker JSON-RPC methods. Their official response is only
`"ok"`, so the UI must not treat the command acknowledgement as user-visible success. Phase 7's success
semantics should be state-confirmed: start is complete only when `print_stats`/`virtual_sdcard` flips to
the selected file; pause/resume/cancel are complete only when `print_stats.state` and
`pause_resume.is_paused` reflect the requested transition.

The key architectural gap is that `AppContainer` exposes a session `CommandDispatcher` for action taps
and exposes service-owned one-shot holders for metadata/history, but it does not expose a general read
surface for a user-driven file browser. The plan should add a narrow, typed file-browser read/action
surface owned by the session, not a raw `JsonRpcClient` reachable by UI code.

## Authoritative Sources Read

### Moonraker Official Docs

- File Management, latest split docs:
  `https://moonraker.readthedocs.io/en/latest/external_api/file_manager/`
- Printer Administration, latest split docs:
  `https://moonraker.readthedocs.io/en/latest/external_api/printer/`
- Printer Objects:
  `https://moonraker.readthedocs.io/en/latest/printer_objects/`
- Legacy monolithic Web API, used only as cross-check:
  `https://moonraker.readthedocs.io/en/stable/web_api/`

Findings:

- File manager operations are available over both HTTP and JSON-RPC except uploads/downloads. Upload is
  out of scope, and thumbnail/file image download should continue through Coil HTTP URLs.
- Roots include `gcodes`, `config`, `logs`, `config_examples`, and `docs`; write operations are only
  for writable roots such as `gcodes` and sometimes `config`. Phase 7 should browse/delete only the
  `gcodes` root.
- `server.files.list` walks a whole root and returns detected files. The docs explicitly recommend
  requesting directory information in most UI scenarios.
- `server.files.get_directory` returns files and subdirectories in one path, does not recurse, accepts
  optional `extended=true`, and falls back to the `gcodes` root when path is omitted.
- `server.files.metadata` accepts a path relative to `gcodes`. It returns slicer metadata such as
  `estimated_time`, `filament_total`, `filament_weight_total`, `layer_count`, `object_height`, and
  `thumbnails[]` where present.
- `server.files.thumbnails` accepts the same relative filename and returns an array of thumbnail
  details, with an empty array when no thumbnails exist.
- `server.files.delete_file` uses params `{ "path": "gcodes/<relative-file>" }` and returns deleted
  item information plus `action: "delete_file"`.
- `printer.print.start` accepts `{ "filename": "<relative gcode path>" }`; pause, resume, and cancel
  take no params. All return `"ok"`.
- `print_stats` fields include `filename`, `total_duration`, `print_duration`, `filament_used`, and
  `state`; `display_status.progress` and `virtual_sdcard.progress` are the progress sources. Klipper's
  `pause_resume.is_paused` is the authoritative paused flag.

### Local Command and Capability References

- `docs/commands/moonraker-api.md` already lists Phase 7 operations as planned v1 rows:
  `server.files.get_directory`, `server.files.list`, `server.files.roots`,
  `server.files.thumbnails`, `server.files.delete_file`, `printer.print.start`,
  `printer.print.pause`, `printer.print.resume`, and `printer.print.cancel`.
- `docs/commands/printer-availability-matrix.md` confirms both observed printers expose
  `file_manager`, `virtual_sdcard`, and `pause_resume` through live read-only capture evidence.
- `docs/moonraker-capabilities.md` confirms the current metadata shape, thumbnail URL construction,
  progress/state fields, and nullable layer behavior for both Ender 5 Plus and Ender 3 Pro.

## User Constraints From CONTEXT.md

### Locked Decisions

- D-01: Default file ordering is recent-first with folders grouped.
- D-02: Rows show compact essentials only: thumbnail/icon slot, filename, modified date, and size.
- D-03: Folder navigation is breadcrumb/path chip plus an in-list Up row. Android Back preserves the
  app shell back-stack, not folder traversal.
- D-04: Thumbnail-less folders/files have distinct placeholders. Thumbnail failures never block browse.
- D-05: Selected-file Focus shows thumbnail first, then filename, estimated time, size, modified date,
  filament/weight, and layer/object-height when available.
- D-06: Start print uses the existing full-screen ConfirmGuard, not an immediate-start shortcut.
- D-07: Print start success is based on printer state flipping to that file, not on command ack.
- D-08: Metadata/thumbnail failures do not block printing.
- D-09: Delete lives in selected-file Focus actions, not per-row or long-press row actions.
- D-10: Delete is blocked while printing or paused.
- D-11: Delete uses destructive ConfirmGuard with filename/path and known size/date.
- D-12: After delete, stay in current folder, remove the row, clear selection/preview, and show success.
- D-13: Printing gutter keeps Tune / Pause / Stop; Tune remains disabled/deferred.
- D-14: Graceful cancel is long-press Pause while printing; Stop remains firmware emergency stop.
- D-15: Paused gutter shows Tune / Resume / Stop; long-press Resume also opens graceful cancel.
- D-16: Complete/error/cancelled with a known filename shows Files / Restart / Stop.

### Explicitly Out Of Scope

- Upload/slicing.
- Typed search/filtering.
- Rich metadata fetches for every visible row.
- Deep reconnect/process-death print-loop robustness, deferred to Phase 14.
- Tune panel.

## Existing Code Findings

### Transport and Registry

- `JsonRpcMethods` currently has `FILES_METADATA` and `HISTORY_LIST`, but not Phase 7 directory,
  thumbnail, delete, or print-control constants.
- `CommandRegistry` currently includes `filesMetadata` and `historyList`. It must gain Phase 7 specs
  and include them in `CommandRegistry.all`.
- `CommandCatalogDriftTest` enforces that every registry `catalogId` exists in `docs/commands/catalog.json`
  and `docs/commands/printer-matrix.json`. Phase 7 registry expansion must update sidecars and tests in
  the same plan wave.
- `CommandDispatcher` centralizes timeout, in-flight/busy state, tap debounce, non-fatal `RpcError`,
  redaction, and event toasts. Print-control actions and delete/start actions should go through this
  path or an equivalently narrow typed wrapper; do not duplicate dispatcher behavior in UI code.

### Session and Holder Seams

- `MoonrakerService` owns the live `JsonRpcClient`, constructs session-scoped holders, and publishes an
  immutable `SpineHandle`.
- `AppContainer` exposes derived flows: printer state, connection state, capabilities, dispatcher,
  print metadata, last job, and HTTP base.
- The UI cannot currently issue a typed read such as `server.files.get_directory`. Adding a raw
  `JsonRpcClient` to `SpineHandle` would violate the existing narrow-surface discipline.
- Recommended seam: add a session-owned `FileBrowserClient` or `FileBrowserHolder` that provides only
  the typed operations this phase needs:
  - `getDirectory(path: String?, extended: Boolean = false)`
  - `getMetadata(filename: String)` or reuse `CommandRegistry.filesMetadata`
  - `getThumbnails(filename: String)`
  - `deleteFile(path: String)` as an action with dispatcher semantics
  - `startPrint(filename: String)`, `pausePrint()`, `resumePrint()`, `cancelPrint()` as actions.
- A UI-owned `FilesHolder` can be acceptable if it receives a narrow typed client from the current
  `SpineHandle`; it still must not hold a socket or raw RPC client.

### UI and Routing

- `Dest` currently includes `PrintStatus`, `Temperature`, `Move`, `Extrude`, and `Settings`; Files tile
  is inert in `AppDrawer`.
- `AppShell` is a lean state holder, not Navigation-Compose. Phase 7 should add `Dest.Files`, make the
  Files tile live, and render a full-bleed `FilesScreen`.
- `PrintStatusScreen` already has disabled Tune/Pause gutter placeholders and a wired emergency Stop.
  Phase 7 changes that gutter behavior by print state without introducing a separate Job route.
- `ConfirmGuard`, `SeverityToast`, `MaterialSymbol`, `ScreenScaffold`, and tokenized controls already
  exist and should be consumed.
- `docs/adr/0001-ui-toolkit-decision.md` mandates classic Views for the Files list. The selected-file
  Focus and gutter can remain Compose, but the high-churn list should be RecyclerView hosted through
  `AndroidView`/`ComposeView`.

### Data and Thumbnail Reuse

- `PrintMetadata`, `parsePrintMetadata`, `largestThumbRelPath`, and `thumbnailUrl()` already model
  metadata and thumbnail URL behavior for active/last jobs.
- The Files preview needs a richer, selected-file snapshot than current `PrintMetadata`: size, modified,
  filament totals/weight, layer count, object height, estimated time, largest thumbnail relative path,
  and maybe slicer name. Extend the pure parser deliberately rather than parsing ad hoc in UI.
- `thumbnailUrl(httpBase, gcodeFilename, relPath)` is already correct for subdirectories and spaces.
  Reuse it for Files preview thumbnails. Do not invent a second URL joiner.

## Recommended Implementation Architecture

### Plan Shape

1. Registry and catalog expansion first.
   Add method constants, typed args, registry specs, catalog/matrix rows, and drift tests for all Phase
   7 Moonraker operations. This reduces later UI work to consuming typed specs.

2. Pure file models and parsing.
   Add file-directory item models and parser tests around faithful Moonraker JSON:
   - folders and files
   - root/subdirectory path handling
   - missing/garbage fields degrade safely
   - `permissions` carried through for delete gating
   - recent-first sorting with folders grouped
   - thumbnail and metadata absent cases.

3. Narrow session read surface and Files holder.
   Add a typed file-browser surface built in `MoonrakerService` from the live `JsonRpcClient`, then expose
   it through `SpineHandle`/`AppContainer`. The holder owns:
   - current folder path
   - entries
   - selected entry
   - preview metadata loading
   - loading/error/toast state
   - folder Up row behavior
   - delete/start state-confirmation pending markers.

4. Files screen with hybrid UI.
   Compose hosts Focus/Field/Gutter. Field uses RecyclerView for the file list. Rows are stable-height,
   tap-to-select, never expanding. Selected row gets accent outline only. Portrait browsing fills the
   screen until selection; landscape always shows Focus plus Field.

5. Print Status gutter wiring.
   Replace disabled Pause/Resume placeholder with state-adaptive actions:
   - printing: Tune disabled, Pause tap dispatches `printer.print.pause`, Pause long-press opens graceful
     Cancel ConfirmGuard, Stop remains emergency stop.
   - paused: Tune disabled, Resume tap dispatches `printer.print.resume`, Resume long-press opens
     graceful Cancel ConfirmGuard, Stop remains emergency stop.
   - complete/error/cancelled with known filename: Files opens `Dest.Files`, Restart uses ConfirmGuard
     and dispatches `printer.print.start` for that file, Stop remains emergency stop if Klippy Ready.

6. End-to-end core-loop verification on Ender 5 Plus.
   Verify connect -> browse -> select -> confirm print -> state-confirmed start -> monitor progress/temps/Z
   -> pause -> resume -> graceful cancel, without opening the browser.

### Command Registry Targets

Add JSON-RPC constants and registry specs for:

| Operation | Args | Availability | Acceptance Semantics |
|---|---|---|---|
| `server.files.roots` | none | `ComponentPresent("file_manager")` | response parsed into available roots; use for diagnostics if needed |
| `server.files.get_directory` | `path`, `extended` | `ComponentPresent("file_manager")` | directory response parsed; UI shows stable entries |
| `server.files.list` | `root` | `ComponentPresent("file_manager")` | fallback/reference, not primary browsing |
| `server.files.thumbnails` | `filename` | `ComponentPresent("file_manager")` | preview thumbnail list parsed or empty |
| `server.files.delete_file` | `path = "gcodes/<relative>"` | `ComponentPresent("file_manager")` plus idle-state UI gate | current folder row removed after response |
| `printer.print.start` | `filename` | `ObjectPresent("virtual_sdcard")` | success only after `print_stats`/`virtual_sdcard` state confirms |
| `printer.print.pause` | none | `ObjectPresent("pause_resume")` | success only after paused state confirms |
| `printer.print.resume` | none | `ObjectPresent("pause_resume")` | success only after printing state confirms |
| `printer.print.cancel` | none | `ObjectPresent("pause_resume")` | success only after cancelled/standby state confirms |

### File Browser Data Model

Recommended pure model:

```kotlin
sealed interface FileEntry {
    val path: String
    val displayName: String
    val modified: Double?
    val sizeBytes: Long?
    val permissions: String
}

data class FolderEntry(...)
data class GcodeEntry(...)
data class FilePreview(...)
```

Implementation notes:

- Store paths relative to the `gcodes` root for print start and metadata.
- Store delete paths with the root prefix (`gcodes/<relative>`) at the command boundary, not as the
  display path.
- `server.files.get_directory` path should be `gcodes` for root and `gcodes/<subdir>` for children.
- `server.files.metadata` and `server.files.thumbnails` expect paths relative to `gcodes`, without
  the `gcodes/` prefix.
- Sorting: folders first, then gcode files by `modified` descending; unknown modified sorts last.
- Rows should show modified date and size from directory response. Do not fetch metadata for every row.
- Selected-file preview may fetch metadata/thumbnails once per selected filename, best effort.

### State-Confirmed Actions

Add a small pending-action observer in the relevant holder/screen:

- `pendingStart(filename)` is resolved when `printState` becomes Printing/Paused and
  `printFilename == filename`, or failed after a bounded timeout with a warning toast.
- `pendingPause` is resolved when `PrintState.Paused` or `pause_resume.is_paused == true`.
- `pendingResume` is resolved when `PrintState.Printing` and not paused.
- `pendingCancel` is resolved when state becomes Cancelled/Standby/Complete/Error and the active print
  is no longer printing.

Do not block the dispatch call waiting forever. The dispatcher handles command transport; the holder
handles state-confirmed user feedback.

### Security Threat Model

Phase 7 must include a `<threat_model>` block in every PLAN.md because `workflow.security_enforcement`
is enabled. Threats to explicitly cover:

- T-07-01: destructive file delete. Mitigation: idle-state gate plus full-screen destructive
  ConfirmGuard with exact filename/path; no row-level destructive affordance.
- T-07-02: accidental print start. Mitigation: ConfirmGuard with thumbnail/details; no immediate-start
  shortcut; state-confirmed success.
- T-07-03: graceful cancel vs emergency stop confusion. Mitigation: long-press Pause/Resume for graceful
  cancel with ConfirmGuard; Stop remains firmware emergency stop with existing red confirm language.
- T-07-04: path/URL confusion. Mitigation: model relative gcode paths separately from root-prefixed
  delete paths and HTTP thumbnail URLs; URL-encode segments through shared `thumbnailUrl`.
- T-07-05: stale command after reconnect/session swap. Mitigation: typed client/holder tied to current
  `SpineHandle`; pending actions clear on session change.

## Validation Architecture

### Automated Verification

- Unit tests for registry/catalog drift:
  `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.command.CommandCatalogDriftTest --no-daemon"`
- Unit tests for pure parsers and sorters:
  `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.state.*File* --no-daemon"`
- Unit tests for Files holder state:
  `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.ui.files.* --no-daemon"`
- Unit tests for Print Status gutter state:
  `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests works.mees.dinghy.ui.printstatus.* --no-daemon"`
- Full release unit suite before verification:
  `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon"`
- Compile gate:
  `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileReleaseKotlin --no-daemon"`

### Manual / Live Verification

- Ender 5 Plus core print-loop UAT:
  connect -> open Files -> browse root/subfolder -> select file -> confirm print -> observe
  state-confirmed start -> monitor progress/temps/Z -> pause -> resume -> graceful cancel.
- File delete UAT while idle:
  select a safe throwaway gcode file -> confirm delete -> row removed -> selection cleared -> current
  folder retained.
- Negative delete gate:
  while printing or paused, delete action is disabled/absent and cannot send `server.files.delete_file`.
- Thumbnail robustness:
  browse a folder containing files with thumbnails and files without thumbnails; no OOM, no layout jump,
  and placeholder rendering remains stable.
- flox performance spot-check:
  scroll a large gcode folder on the physical Nexus 7-class device; confirm no frozen frames and
  no visible list jank. If macrobenchmark is used, first address the folded benchmark harness todos.

### Acceptance Signals

- `FILE-01`: folder/file rows render name, modified date, and size from directory response.
- `FILE-02`: thumbnails are best-effort, downsampled by Coil/RecyclerView, and placeholders render when
  metadata/thumbnails are missing.
- `FILE-03`: print start is confirmed by `print_stats` state, not the command ack.
- `FILE-04`: delete is ConfirmGuard-gated and blocked during printing/paused.
- `JOB-01` and `JOB-02`: existing Print Status home is exercised during the live core loop.
- `JOB-03` through `JOB-05`: pause/resume/cancel/restart/files gutter states adapt from printer state.

## Planning Pitfalls

- Do not use `server.files.list` as the primary browser for a large library; it walks the root.
- Do not call `server.files.metadata` for every row as the list scrolls. Use compact directory metadata
  for rows and one-shot preview metadata for selected file.
- Do not treat print-control `"ok"` as successful user outcome. Confirm from state.
- Do not expose raw `JsonRpcClient` to Compose screens.
- Do not use Android Back for folder-up behavior.
- Do not implement row long-press delete.
- Do not add a separate Job route; Print Status is already the job monitor.
- Do not put the Files list in Compose LazyColumn unless there is a measured, explicit override of
  ADR 0001. RecyclerView is the chosen high-churn surface.
- Do not reimplement thumbnail URL construction; reuse `thumbnailUrl()`.

## Recommended Plan Breakdown

1. Registry/catalog/files parser foundation.
2. Session file-browser read surface and holder.
3. Hybrid Files screen and shell navigation.
4. Print start/delete ConfirmGuard flows with state-confirmed outcomes.
5. Print Status pause/resume/cancel/restart/files gutter wiring.
6. Ender 5 Plus live core-loop verification and flox scroll/perf check.

## RESEARCH COMPLETE
