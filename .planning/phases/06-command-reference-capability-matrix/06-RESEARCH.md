# Phase 6: Command Reference & Capability Matrix - Research

**Researched:** 2026-06-01
**Status:** Complete
**Mode:** Parallel source-domain research synthesized by orchestrator

## Scope Summary

Phase 6 is a reference/quality phase. It does not add a user-facing panel; it creates the command
knowledge substrate that later phases consume:

1. A committed docs catalog of sendable Klipper G-Code commands, Moonraker API operations, and
   Spoolman API operations.
2. A canonical in-code command registry for commands Dinghy Display actually sends, wrapping the
   existing typed builders instead of reimplementing gcode generation.
3. A per-printer availability matrix for Ender 5 Plus and Ender 3, reconciled against live captures
   and usable as a dev-facing reference while runtime gating remains live.

The key planning constraint is that the catalog can be comprehensive, but implementation detail must
be tiered: full semantics for commands Dinghy sends now or will send in v1, and lighter reference
entries for the rest.

## Authoritative Sources Read

### Klipper

- Klipper G-Code command reference: `https://www.klipper3d.org/G-Codes.html`
- Klipper status/object reference: `https://www.klipper3d.org/Status_Reference.html`

Findings:

- The G-Code command reference is the source for commands that a client can send. It contains the
  standard Klipper-supported G-Code set and module-scoped additional commands.
- The status reference is the source for object names and fields used by availability predicates and
  post-command confirmation.
- Do not catalog every Klipper config-section option in this phase. Config modules are relevant only
  where their presence creates a sendable command or status object.

### Moonraker

- External API introduction: `https://moonraker.readthedocs.io/en/latest/external_api/introduction/`
- Server administration: `https://moonraker.readthedocs.io/en/latest/external_api/server/`
- Printer administration/status: `https://moonraker.readthedocs.io/en/latest/external_api/printer/`
- File management: `https://moonraker.readthedocs.io/en/latest/external_api/file_manager/`
- Job queue: `https://moonraker.readthedocs.io/en/latest/external_api/job_queue/`
- Job history: `https://moonraker.readthedocs.io/en/latest/external_api/history/`
- Webcam management: `https://moonraker.readthedocs.io/en/latest/external_api/webcams/`
- Update management: `https://moonraker.readthedocs.io/en/latest/external_api/update_manager/`
- Devices/power: `https://moonraker.readthedocs.io/en/latest/external_api/devices/`
- Third-party integrations: `https://moonraker.readthedocs.io/en/latest/external_api/integrations/`
- JSON-RPC notifications: `https://moonraker.readthedocs.io/en/latest/external_api/jsonrpc_notifications/`
- Authorization: `https://moonraker.readthedocs.io/en/latest/external_api/authorization/`

Findings:

- Latest Moonraker docs have moved away from the older monolithic `web_api/` page into split
  `external_api/*` pages. Use the latest split pages as canonical; treat old `web_api/` as legacy
  comparison only.
- Most operations have both JSON-RPC method names and REST endpoint paths, but not all. File transfer
  is HTTP-only; `server.connection.identify` and `printer.objects.subscribe` are websocket/unix-socket
  operations.
- JSON-RPC success returns `result`; JSON-RPC errors return `error.code` and `error.message`; HTTP
  success wraps the operation result in a `result` body.

### Spoolman

- Spoolman ReDoc/OpenAPI: `https://donkie.github.io/Spoolman/`
- Official Spoolman repo:
  - `https://github.com/Donkie/Spoolman/blob/master/spoolman/api/v1/router.py`
  - `https://github.com/Donkie/Spoolman/blob/master/spoolman/main.py`
  - `https://github.com/Donkie/Spoolman/blob/master/spoolman/api/v1/spool.py`
  - `https://github.com/Donkie/Spoolman/blob/master/client/src/components/qrCodeScanner.tsx`
  - `https://github.com/Donkie/Spoolman/blob/master/client/src/pages/printing/spoolQrCodePrintingDialog.tsx`
  - `https://github.com/Donkie/Spoolman/blob/master/client/src/pages/printing/qrCodePrintingDialog.tsx`

Findings:

- Spoolman runtime API base path is `/api/v1/`, even though the hosted ReDoc page renders operation
  URLs as paths such as `/info`.
- Spoolman has direct REST endpoints for spools, filaments, vendors, settings, extra fields, export,
  and health/info. It does not expose a direct "active spool for this printer" endpoint in the
  checked REST surface; that belongs to Moonraker's Spoolman integration.
- Official QR labels can use the default `WEB+SPOOLMAN:S-<id>` shape, and the client also supports
  full `/spool/show/<id>` URLs.

## Local Code/Artifact Findings

Current outbound JSON-RPC constants are in
`app/src/main/java/works/mees/dinghy/net/JsonRpc.kt`:

- Spine/session: `server.connection.identify`, `printer.objects.list`, `printer.objects.query`,
  `printer.objects.subscribe`, `access.oneshot_token`
- Actions: `printer.emergency_stop`, `printer.firmware_restart`, `printer.restart`,
  `printer.gcode.script`
- One-shot reads: `server.temperature_store`, `server.files.metadata`, `server.history.list`
- Notifications: `notify_status_update`, `notify_gcode_response`, `notify_klippy_ready`,
  `notify_klippy_shutdown`, `notify_klippy_disconnected`

Current gcode builders are in
`app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt`:

- Temperature: `setHeater`, `applyPreset`, `COOLDOWN`
- Move: `jog`, `forceMove`, `homeXY`, `homeAxis`, `homeAll`, `DISABLE_STEPPERS`
- Extrude: `extrude`, `selectTool`, `loadFilament`, `unloadFilament`
- Wrapper: `scriptParams`

These builders are safety-critical. They clamp numeric inputs before string formatting, avoid
free-text identifiers except capability-derived heater names, and wrap mode-changing moves/extrudes in
`SAVE_GCODE_STATE` / `RESTORE_GCODE_STATE`. The registry must wrap them verbatim and prove
byte-identical output.

Current call-site surface:

- `MoonrakerSession.kt`: identify -> objects.list -> objects.query -> objects.subscribe, plus
  temperature store and configfile one-shot reads.
- `MoonrakerService.kt`: metadata/history one-shot reads and recovery dispatch.
- `PrintStatusScreen.kt`: emergency stop.
- `TemperatureScreen.kt`, `MoveScreen.kt`, `ExtrudeScreen.kt`: `printer.gcode.script` carrying
  `PrinterCommands.scriptParams(...)`.

Current live capability model is in `Capabilities.kt` and `DeriveCapabilities.kt`. It derives typed
fields from `printer.objects.list`, but it does not yet retain the raw object-name set. Phase 6 should
add that raw set and a `hasObject()` helper while preserving existing typed fields and
`hasMacroIgnoreCase()`.

`docs/moonraker-capabilities.md` is valuable narrative evidence, but not structured enough for the
D-10 drift test. The plan should add machine-readable catalog/matrix sidecars and keep markdown as the
human reference.

## Recommended Implementation Architecture

Add a headless command-registry package under
`app/src/main/java/works/mees/dinghy/command/`. Keep it plain Kotlin with no Compose or Android UI
dependencies.

Recommended types:

```kotlin
enum class CommandTransport {
    JsonRpc,
    GcodeScript,
    RestEndpoint,
    SpoolmanRest,
}

sealed interface AvailabilityPredicate {
    data object Always : AvailabilityPredicate
    data class ObjectPresent(val name: String) : AvailabilityPredicate
    data class MacroPresent(val name: String) : AvailabilityPredicate
    data class ComponentPresent(val name: String) : AvailabilityPredicate
    data class GcodeCommandPresent(val name: String) : AvailabilityPredicate
    data class AnyOf(val predicates: List<AvailabilityPredicate>) : AvailabilityPredicate
    data class NotOnOurPrinters(val reason: String) : AvailabilityPredicate
}

data class CommandSemantics(
    val success: String,
    val error: String,
    val acceptance: String,
)

data class CommandSpec<P>(
    val catalogId: String,
    val dispatchKey: (P) -> String,
    val transport: CommandTransport,
    val method: String?,
    val params: (P) -> JsonElement?,
    val availability: AvailabilityPredicate,
    val semantics: CommandSemantics,
)
```

Use typed argument data classes only where useful: `JogArgs`, `SetHeaterArgs`, `ExtrudeArgs`,
`SelectToolArgs`, `MetadataArgs`, `HistoryListArgs`, `ObjectSubsetArgs`. Use `Unit` for no-param
commands.

Add thin helpers rather than reshaping the dispatcher:

```kotlin
fun <P> CommandDispatcher.dispatch(command: CommandSpec<P>, args: P) =
    dispatch(command.dispatchKey(args), command.method!!, command.params(args))
```

For direct one-shot reads:

```kotlin
suspend fun <P> JsonRpcClient.request(
    command: CommandSpec<P>,
    args: P,
    timeoutMs: Long = JsonRpcClient.DEFAULT_REQUEST_TIMEOUT_MS,
)
```

Notifications are not sent by the app. They can remain as constants in `JsonRpcMethods` or move to a
separate notification object, but the registry should focus on outbound operations.

## Catalog Strategy

Create human-readable markdown files:

- `docs/commands/klipper-gcode.md`
- `docs/commands/moonraker-api.md`
- `docs/commands/spoolman-api.md`
- `docs/commands/printer-availability-matrix.md`

Also create machine-readable sidecars for tests:

- `docs/commands/catalog.json`
- `docs/commands/printer-matrix.json`

Tests should parse the JSON sidecars, not markdown prose. Markdown remains the human reference; JSON
is the drift-test source.

Each catalog row should carry:

- Stable catalog ID: e.g. `KGC-G28`, `KGC-SET_HEATER_TEMPERATURE`, `MR-printer.gcode.script`,
  `SPM-GET-spool`
- Source API and category
- Command/method/endpoint name
- Params and key defaults
- Return/result shape
- Purpose
- Success/error/acceptance semantics
- Availability predicate
- Semantics tier: `full` or `light`
- Upstream URL

For Moonraker, catalog both JSON-RPC method and REST endpoint where both exist. For Spoolman, record
runtime base path `/api/v1/` explicitly.

Full-detail Klipper subset:

- Current Move/Temp/Extrude: `G0`, `G1`, `G28`, `G90`, `G91`, `M18`/`M84`, `M82`, `M83`,
  `SAVE_GCODE_STATE`, `RESTORE_GCODE_STATE`, `SET_HEATER_TEMPERATURE`, `TURN_OFF_HEATERS`,
  `TEMPERATURE_WAIT`, `FORCE_MOVE`, `T<n>`, `LOAD_FILAMENT`, `UNLOAD_FILAMENT`
- Files/print control: `SDCARD_PRINT_FILE`, `SDCARD_RESET_FILE`, `PAUSE`, `RESUME`, `CLEAR_PAUSE`,
  `CANCEL_PRINT`
- Macros/Console: `gcode_macro NAME`, `SET_GCODE_VARIABLE`, `RESPOND`, `HELP`, `STATUS`,
  `RESTART`, `FIRMWARE_RESTART`
- Calibration: `SCREWS_TILT_CALCULATE`, `Z_TILT_ADJUST`, `BED_MESH_CALIBRATE`, `BED_MESH_OUTPUT`,
  `BED_MESH_MAP`, `BED_MESH_CLEAR`, `BED_MESH_PROFILE`, `QUAD_GANTRY_LEVEL`, `PROBE`,
  `QUERY_PROBE`, `PROBE_ACCURACY`, `PROBE_CALIBRATE`, `SAVE_CONFIG`

Full-detail Moonraker subset:

- Existing: `server.connection.identify`, `access.oneshot_token`, `server.info`, `printer.info`,
  `printer.objects.list`, `printer.objects.query`, `printer.objects.subscribe`,
  `server.temperature_store`, `printer.gcode.script`, `printer.emergency_stop`,
  `printer.restart`, `printer.firmware_restart`, `server.files.metadata`, `server.history.list`
- Phase 7: `server.files.list`, `server.files.roots`, `server.files.directory`,
  `server.files.thumbnails`, `server.files.delete_file`, `printer.print.start`,
  `printer.print.pause`, `printer.print.resume`, `printer.print.cancel`
- Phase 8: `printer.gcode.help`, `server.gcode_store`, `notify_gcode_response`
- Phase 10: `server.webcams.list`, `server.webcams.get_item`, `server.webcams.test`,
  `notify_webcams_changed`
- Phase 11: `server.spoolman.status`, `server.spoolman.get_spool_id`,
  `server.spoolman.post_spool_id`, `server.spoolman.proxy`, `notify_active_spool_set`,
  `notify_spoolman_status_changed`
- Supporting components: `machine.device_power.*`, `server.job_queue.*`, `machine.update.*` as
  catalog entries where later phases need them.

Full-detail Spoolman subset:

- `GET /api/v1/health`, `GET /api/v1/info`
- `GET /api/v1/spool`, `GET /api/v1/spool/{spool_id}`
- `PUT /api/v1/spool/{spool_id}/use`, `PUT /api/v1/spool/{spool_id}/measure`
- `GET /api/v1/vendor`, `GET /api/v1/material`, `GET /api/v1/filament`,
  `GET /api/v1/filament/{filament_id}`
- `GET /api/v1/field/{entity_type}`
- QR payload parsing for `web+spoolman:s-<id>` and `/spool/show/<id>` URLs

## Registry Strategy

The registry should include only commands Dinghy sends or is committed to send in v1, not every
catalog row. This keeps runtime code small while retaining comprehensive docs.

Rules:

- Registry entries link to `catalogId`.
- `JsonRpcMethods` outbound constants fold into registry entries. Notification names can remain
  outside the registry.
- G-Code registry entries call `PrinterCommands` builders verbatim.
- `CommandDispatcher` keeps debounce, in-flight, timeout, `RpcError`, and secret-redaction behavior.
- `printer.gcode.script` must keep the 120-second timeout path because Moonraker replies after G-Code
  completion, not immediately after send.
- `printer.emergency_stop` remains the e-stop path; do not send `M112` through `printer.gcode.script`
  for immediate stop.

Expected refactor surface:

- `MoonrakerSession.kt`
- `MoonrakerService.kt`
- `PrintStatusScreen.kt`
- `TemperatureScreen.kt`
- `MoveScreen.kt`
- `ExtrudeScreen.kt`
- `Capabilities.kt`
- `DeriveCapabilities.kt`
- Command tests, state tests, handshake tests, dispatcher tests

## Availability Matrix Strategy

Runtime gating must stay live and generic:

- Add `Capabilities.objects: Set<String>` and `hasObject(name)`.
- Add `Capabilities.components: Set<String>` and `hasComponent(name)` so Moonraker
  `ComponentPresent` predicates are live-queryable at runtime.
- Keep typed convenience fields (`hasBed`, `extruderCount`, `heaters`, `fans`, `macros`) for hot UI
  paths.
- Keep `hasMacroIgnoreCase()`.

Predicate order:

1. `server.info.components contains "<component>"` for Moonraker components such as `history`,
   `job_queue`, `webcam`, `spoolman`, `update_manager`, `power`.
2. `printer.objects.list contains "<object>"` for Klipper objects and calibration gates.
3. `printer.objects.list contains "gcode_macro NAME"` for user macros.
4. `printer.gcode.help contains command` for positive command/help evidence, never as the only
   negative proof because Moonraker documents it as non-exhaustive.
5. Component-specific list/status endpoint where needed: webcams, power devices, Spoolman status,
   job queue, update status.
6. Explicit `not_on_our_printers` for cataloged commands neither E5 nor E3 exposes.

The committed matrix should distinguish:

- Present on Ender 5 Plus
- Present on Ender 3
- Not present on either test printer
- Unknown / needs refresh
- Runtime predicate
- Confirmation/status source

The implementation plan needs a live capture/extract task before strict predicate tests can pass,
because `docs/moonraker-capabilities.md` is not complete machine-readable E5/E3 object data.

## Validation Architecture

### Host Tests

1. **Registry to catalog link test**
   - Parse `docs/commands/catalog.json`.
   - Assert every `CommandRegistry.all.catalogId` exists.
   - One-directional only: the comprehensive catalog may contain commands not in the registry.

2. **Predicate drift test**
   - Parse `docs/commands/printer-matrix.json`.
   - Assert every `ObjectPresent(name)` appears in at least one printer's object set or is explicitly
     listed under `not_on_printers`.
   - Assert every `MacroPresent(name)` appears in `gcode_macro NAME` case-insensitively or is explicitly
     listed under `not_on_printers`.
   - Assert every `ComponentPresent(name)` appears in captured `server.info.components` or is explicitly
     listed under `not_on_printers`.

3. **Byte-identical gcode test**
   - For every registry gcode entry, compare the registry params' `script` string against the current
     `PrinterCommands.*` output for representative and clamp-boundary inputs.
   - Include exact multiline newline comparisons for jog/extrude/preset builders.

4. **Dispatcher semantics regression**
   - Registry dispatch of `printer.gcode.script` still takes the 120-second `GCODE_TIMEOUT_MS` path.
   - Registry dispatch of non-gcode actions still uses default 10-second behavior.
   - `RpcError` remains non-fatal and surfaces printer rejection text.
   - Failure messages do not leak API keys or token URLs.

5. **Handshake/request regression**
   - Request order still follows identify -> objects.list -> objects.query -> objects.subscribe.
   - `server.connection.identify` still includes the live-required `url` field.
   - `objects.query` missing object/field omission remains non-fatal.

Suggested verification commands:

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon" | tr -d '\r'
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileReleaseKotlin --no-daemon" | tr -d '\r'
```

### Manual / On-Device Regression

Because Phase 6 fully refactors proven send paths, on-device flox regression is mandatory:

- Move: jog X/Y/Z, home all/axis, disable steppers.
- Temperature: set heater, apply preset, cooldown.
- Extrude: extrude, retract, load/unload macro behavior, missing macro popup where applicable.
- Print Status: emergency stop -> ConfirmGuard -> shutdown/Splash routing.
- Recovery actions: firmware restart / host restart if reachable.

No new feature UAT is needed, but the old behavior must remain identical on the real Ender 5 Plus.

## Risks and Pitfalls

- Reimplementing gcode as generic templates would invalidate the Phase 5 safety proof. The registry
  must wrap existing builders.
- Markdown-only catalog/matrix tests will be brittle. Use JSON sidecars for enforcement.
- `printer.gcode.script` is not a fire-and-forget ack. Long-running commands need long timeout and
  state/response-stream-driven UI.
- `printer.print.*` returning `ok` is not sufficient user confirmation. Verify `print_stats.state` /
  `virtual_sdcard` state changes.
- `printer.gcode.help` is useful but not exhaustive. Do not treat absence from help as definitive
  unavailability.
- `FORCE_MOVE` is dangerous: Klipper documents that it bypasses normal boundary checks. Keep existing
  explicit override semantics and do not expose it casually in later phases.
- `RESTORE_GCODE_STATE MOVE=1` can move the toolhead; treat restore variants as motion-bearing.
- Spoolman active spool assignment is not a direct Spoolman REST endpoint in the checked sources.
  Catalog direct Spoolman REST separately from Moonraker's Spoolman integration.
- Static E5/E3 matrix is documentation/test evidence only. Do not ship baked per-printer data in the
  APK.
- API drift matters: record source URLs, Moonraker API version, Klipper version, and Spoolman runtime
  `/info.version` when refreshing captures.

## Suggested Plan Breakdown

### Wave 1: Capture and Test Scaffolding

- Add machine-readable `docs/commands/catalog.json` and `docs/commands/printer-matrix.json` skeletons.
- Add failing drift tests for registry catalog IDs and predicate references.
- Add failing byte-identical gcode tests for registry wrappers.
- Add or refresh live E5/E3 capture/extraction task so object/component/macro data is structured.

### Wave 2: Registry Foundation

- Add `CommandSpec`, `CommandTransport`, `CommandSemantics`, `AvailabilityPredicate`, and
  `CommandRegistry`.
- Register all current Phase 1-5 outbound methods/actions.
- Add `Capabilities.objects` and `hasObject()`.
- Preserve `PrinterCommands` builders byte-identically.

### Wave 3: Full Call-Site Refactor

- Refactor `MoonrakerSession` and `MoonrakerService` direct request calls to registry request helpers.
- Refactor Print Status, Temperature, Move, and Extrude action dispatch to registry entries.
- Preserve dispatcher timeout/error/redaction semantics.
- Keep notifications outside outbound registry unless a clean split emerges.

### Wave 4: Catalog and Matrix Artifacts

- Write `docs/commands/klipper-gcode.md`, `moonraker-api.md`, and `spoolman-api.md`.
- Fill `catalog.json` with stable IDs, source URLs, semantics tier, and predicates.
- Fill `printer-matrix.json` with E5/E3 object, macro, component, and `not_on_printers` evidence.
- Write `docs/commands/printer-availability-matrix.md` as the human-facing view.

### Wave 5: Verification and Flox Regression

- Run release unit tests and `compileReleaseKotlin`.
- Build/sign/install release APK on flox if current project practice requires it.
- Run focused on-device regression against the live Ender 5 Plus.
- Record verification results and any manual capture caveats.

## Open Questions (RESOLVED)

- **RESOLVED: Moonraker catalog breadth.** Catalog all official Moonraker API operations, but mark
  unsafe/admin/deprecated operations as light `reference_only` entries unless Dinghy sends them in v1.
- **RESOLVED: Deprecated update endpoints.** Keep deprecated Moonraker update endpoints as catalog-only
  compatibility notes unless a later phase explicitly sends them.
- **RESOLVED: Spoolman transport policy.** Catalog both direct Spoolman REST and Moonraker Spoolman
  integration surfaces; Phase 11 will choose the runtime preference order. Phase 6 only records
  predicates and semantics.
- **RESOLVED: E5/E3 help output.** Existing captures are sufficient for planning, but implementation
  must refresh live `gcode.commands` / `printer.gcode.help` evidence before strict matrix predicate
  tests pass.

## Research Complete

Parallel source-domain research covered Klipper, Moonraker, Spoolman, and local code/test seams. The
planner can proceed with a registry-plus-machine-readable-catalog strategy and a mandatory on-device
regression gate.
