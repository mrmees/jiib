# Phase 7: Files & Print Control - Core Print-Loop Gate - Context

**Gathered:** 2026-06-02
**Status:** Ready for planning

<domain>
## Phase Boundary

The **core print-loop gate**: a user can drive a real print start-to-finish without the browser.
This phase adds the Files panel for Moonraker gcode browsing, selected-file preview, start-print,
and delete, and wires the existing Print Status home gutter into real print controls.

**In scope:** browse gcode files/folders with thumbnails and compact metadata; select a file and
preview print-readiness details; start a print through a confirm guard; delete a gcode file in idle
state; wire pause/resume/cancel/restart/files controls on Print Status; prove connect -> browse ->
start -> monitor -> pause/resume -> cancel on the Ender 5 Plus.

**Out of scope:** upload/slicing; typed search/filtering; rich full-library metadata fetching for
every visible row; deep reconnect/process-death print-loop robustness (Phase 14); the Tune panel.

</domain>

<decisions>
## Implementation Decisions

### File Library Browsing Model
- **D-01:** Default file ordering is **recent first**: folders grouped, gcode files sorted by modified
  time descending. This optimizes for "print the thing I just sliced" while keeping folders scannable.
- **D-02:** Browsing rows show **compact essentials** only: stable thumbnail/icon slot, filename,
  modified date, and size. Rich slicer metadata stays in the selected-file Focus preview rather than
  making every row dense or metadata-fetch-heavy.
- **D-03:** Folder navigation uses a **breadcrumb/path chip plus an Up row** inside folders. Android
  system Back returns to the caller screen rather than walking folders, preserving the shell back-stack
  contract.
- **D-04:** Thumbnail-less files and folders use **distinct placeholders**: folder icon for folders,
  file/benchy-style placeholder for gcode without thumbnail. Thumbnail failures are non-fatal and
  must not block browsing or layout stability.

### Preview & Print Confirmation
- **D-05:** Selecting a file reveals a **print-readiness snapshot** in Focus: thumbnail first, then
  filename, estimated time, size, modified date, filament/weight when available, and layer/object-height
  when available. Full metadata dumps are not part of the UI.
- **D-06:** Starting a print uses the existing full-screen **ConfirmGuard** with thumbnail + details and
  a deliberate Print confirm. No typed phrase and no immediate-start shortcut.
- **D-07:** `printer.print.start` success is based on the resulting **printer state flip for that file**
  (`print_stats` / progress), not the command ack alone. Ack may show a transient "starting" state, but
  the app does not call the print successfully started until state confirms it.
- **D-08:** Metadata or thumbnail fetch failures **do not block printing**. The confirm guard degrades
  to filename/path plus placeholders.

### Delete Entry Point & Safety
- **D-09:** Delete lives as a **selected-file Focus action**. Browsing rows remain tap-to-select only;
  destructive controls are not exposed per-row, by long press, or during scrolling.
- **D-10:** File delete is **blocked while printing or paused**. Delete is an idle-state library
  maintenance action only.
- **D-11:** Delete uses a destructive ConfirmGuard showing filename/path plus size/date when known.
  Thumbnail is not required in the delete guard.
- **D-12:** After successful delete, the picker stays in the current folder, removes the row, clears
  selection/preview, and shows a success toast.

### Print Status Control Gutter
- **D-13:** While actively printing, the Status gutter keeps the existing **Tune / Pause / Stop**
  structure. Tune stays disabled/deferred, Pause is wired, and Stop remains emergency stop.
- **D-14:** Graceful cancel is reached by **long-pressing Pause** while printing. Tap Pause performs
  normal pause; long-press Pause opens a destructive "Cancel print job?" prompt that sends graceful
  `printer.print.cancel` if confirmed. Stop remains firmware emergency stop.
- **D-15:** While paused, the gutter shows **Tune / Resume / Stop**. Tap Resume resumes normally;
  long-press Resume also opens the graceful Cancel Print prompt.
- **D-16:** After complete/error/cancelled with a last/current filename, the gutter shows
  **Files / Restart / Stop**. Files opens the picker, Restart uses ConfirmGuard for that file, and Stop
  remains emergency stop if Klippy is Ready.

### Folded Todos
- **macrobenchmark-module-wiring:** If Phase 7 planning chooses to use AndroidX Macrobenchmark /
  FrameTimingMetric for Files-list or thumbnail-scroll corroboration, first make the target build
  profileable or add a benchmark build type. This is **not** a Files/Print feature requirement; it is a
  low-priority measurement-enablement task if macrobenchmark is used.
- **benchmark-harness-fairness-fixes:** Before any Phase 7 benchmark rerun, fix the Phase-1 harness
  asymmetries: Compose/Views pre-seed semantics, `ViewsBenchScene` `DiffUtil`, application-context image
  loader, and bounded synthetic thumbnail cache. This matters if Phase 7 re-measures file-list
  thumbnail scrolling, especially on flox/API-23-class hardware.

### the agent's Discretion
- Exact data-class names, repository/holder boundaries, cache sizes, and row implementation details,
  as long as they follow the locked UI law, command registry, live capability gating, and no-ad-hoc-polling
  patterns.
- Exact placeholder artwork within the existing token/icon language.
- Exact transient wording for "starting", success, failure, and delete toasts.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Planning Scope
- `.planning/ROADMAP.md` — Phase 7 goal and success criteria for Files & Print Control.
- `.planning/REQUIREMENTS.md` — FILE-01..04 and JOB-01..05 traceability.
- `.planning/PROJECT.md` — project constraints: minSdk 23, Nexus 7/Adreno 320 perf floor, keyboard-free
  printer controls, sideloaded APK, single-printer v1.

### UI Design Law
- `docs/ui_design/CLAUDE.md` — non-negotiable visual/interaction rules.
- `docs/ui_design/LAYOUT.md` — Focus / Field / Gutter grammar and orientation behavior.
- `docs/ui_design/THEMING.md` — semantic tokens, button intent colors, text-size rules.
- `docs/ui_design/README.md` §6 and interactions section — File / Job Picker layout, scroll+tap rule,
  no alphanumeric keyboard.
- `docs/ui_design/images/06-file-picker.png` — locked file/job picker artboard.
- `docs/ui_design/images/03-print-status.png` — existing Print Status gutter structure to preserve.
- `docs/ui_design/images/08-confirm.png` — ConfirmGuard shape for print/delete/cancel/restart prompts.
- `docs/ui_design/reference/hifi.css` — `.filelist`, `.frow2`, `.preview-focus`, and related component
  reference values.
- `docs/adr/0001-ui-toolkit-decision.md` — hybrid toolkit decision; high-churn Files list is a named
  classic-Views/RecyclerView + Coil candidate.

### Moonraker / Command Reference
- `docs/commands/moonraker-api.md` — planned/registered Phase 7 operations:
  `server.files.*`, `printer.print.start`, `printer.print.pause`, `printer.print.resume`,
  `printer.print.cancel`.
- `docs/commands/catalog.json` — machine-readable catalog IDs and semantics for drift tests.
- `docs/commands/printer-matrix.json` — live E5/E3 availability evidence for predicates.
- `docs/commands/printer-availability-matrix.md` — human-readable command availability matrix.
- `docs/moonraker-capabilities.md` — confirmed print metadata shape, thumbnail URL construction,
  print status fields, nullable layer behavior, and E5/E3 data compatibility.

### Existing Code
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt` — current Status home,
  emergency Stop, disabled Tune/Pause placeholders, thumbnail rendering pattern.
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintMetadataHolder.kt` — one-shot-per-filename
  metadata fetch pattern.
- `app/src/main/java/works/mees/dinghy/ui/printstatus/LastJobHolder.kt` — one-shot-on-idle history
  fetch pattern feeding terminal-state surfaces.
- `app/src/main/java/works/mees/dinghy/state/PrintMetadata.kt` — pure metadata parser and shared
  `thumbnailUrl()` helper.
- `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` — registry commands to extend with
  Phase 7 print/file operations.
- `app/src/main/java/works/mees/dinghy/command/CommandSpec.kt` — availability predicate model.
- `app/src/main/java/works/mees/dinghy/command/CommandDispatchExtensions.kt` — registry dispatch/request
  helpers.
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` — lean route holder and back-stack contract.
- `app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt` — Files tile currently inert; Phase 7 makes
  it live.
- `app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt` — add `Dest.Files`, no separate Job route.
- `app/src/main/java/works/mees/dinghy/state/Capabilities.kt` and
  `app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt` — live `file_manager`,
  `virtual_sdcard`, and `pause_resume` gating surface.
- `app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt` — session-scoped holder wiring and
  registry request examples.

### Folded Todo Notes
- `.planning/todos/pending/macrobenchmark-module-wiring.md` — benchmark profileability issue.
- `.planning/todos/pending/benchmark-harness-fairness-fixes.md` — benchmark fairness fixes before rerun.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`PrintMetadata` / `parsePrintMetadata` / `thumbnailUrl()`:** already model the confirmed metadata and
  thumbnail URL scheme from live Moonraker captures. Reuse and extend carefully rather than re-parsing
  ad hoc in UI code.
- **`PrintMetadataHolder`:** proven one-shot-per-active-filename pattern. The Files picker likely needs a
  similar best-effort metadata/thumbnail layer, but row metadata is intentionally compact.
- **`LastJobHolder`:** terminal-state reference for Restart/Files gutter behavior after complete/error/
  cancelled.
- **`PrintStatusScreen`:** existing full-screen surface, Coil thumbnail rendering, ConfirmGuard for
  emergency stop, disabled Tune/Pause gutter placeholders that Phase 7 wires.
- **`CommandRegistry` + `CommandDispatcher`:** all new file and print-control sends must register and
  dispatch through the Phase-6 registry path.
- **`ConfirmGuard` and `SeverityToast`:** mandatory confirm and feedback primitives for print start,
  delete, graceful cancel, restart, success, and error messages.
- **`ScreenScaffold`, `AppShell`, `AppDrawer`, `TopRoute`:** the Files panel connects as a normal shell
  destination; no Navigation-Compose, no persistent chrome.

### Established Patterns
- **Design law:** file browsing is scroll+tap, no keyboard/search, and rows do not expand. Selected-file
  details live in Focus. Landscape always shows Focus + Field; portrait reveals Focus after selection.
- **Headless holder pattern:** holders consume existing service/session StateFlows and own derived UI
  state. Avoid per-screen sessions, ad-hoc polling, or raw transport calls.
- **Best-effort one-shots:** metadata/history-style reads degrade to null/placeholders and never crash or
  block core actions.
- **State-confirmed actions:** print start, pause/resume/cancel/restart must reflect resulting
  `print_stats` / `pause_resume` state rather than trusting command ack alone.
- **Live capability gating:** file operations gate on `file_manager`; print start on `virtual_sdcard`;
  pause/resume/cancel on `pause_resume`; delete on `file_manager` plus idle print state.
- **Hybrid toolkit:** because Files list is a high-churn thumbnail surface, planner should consider the
  ADR's RecyclerView + Coil path rather than assuming Compose LazyColumn is always acceptable.

### Integration Points
- Add Phase 7 registry entries only for runtime-dispatched commands: `server.files.get_directory`,
  `server.files.thumbnails`, `server.files.delete_file`, `printer.print.start`,
  `printer.print.pause`, `printer.print.resume`, and `printer.print.cancel`. Keep
  `server.files.roots` / `server.files.list` reference-only unless real call sites dispatch them.
- Extend `Dest`, `AppDrawer`, and `AppShell` so the Files tile becomes live and returns through the
  existing shell back-stack.
- Build a file-browser holder/repository that requests directory contents, sorts recent-first with
  grouped folders, exposes selection/preview state, and keeps metadata fetches bounded.
- Modify `PrintStatusScreen` gutter behavior for printing, paused, and terminal states without adding
  a separate Job Status route.
- Verification must include the Ender 5 Plus core loop, flox `ShellPresenceTest` route proof, and
  file-list thumbnail `gfxinfo` performance on the weak hardware floor.

</code_context>

<specifics>
## Specific Ideas

- Cancel is intentionally discoverable through **long-press Pause/Resume**, not a visible Cancel gutter
  tile. This preserves the Tune / Pause|Resume / Stop structure while keeping graceful cancel reachable.
- Stop remains the firmware emergency stop; graceful print cancel is `printer.print.cancel`.
- The picker should remain oriented after delete: same folder, cleared selection, success toast.
- System Back should preserve the existing app shell contract; folder-up is an in-list row, not system Back.

</specifics>

<deferred>
## Deferred Ideas

- **Tune panel:** remains disabled/deferred even while Phase 7 wires Pause/Resume/Cancel/Restart.
- **Typed search/filtering:** out of scope and conflicts with the current design law for printer-control
  surfaces.
- **Uploading gcode from the tablet:** out of scope per project requirements.
- **Deep print-loop robustness:** reconnect print-state resync and process-death recovery remain Phase 14.

</deferred>

---

*Phase: 7-Files & Print Control - Core Print-Loop Gate*
*Context gathered: 2026-06-02*
