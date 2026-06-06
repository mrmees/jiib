package works.mees.dinghy.command

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import works.mees.dinghy.net.JsonRpcMethods

data class IdentifyArgs(
    val clientName: String,
    val version: String,
    val type: String = "display",
    val url: String,
    val apiKey: String? = null,
)

data class SetHeaterArgs(val heater: String, val target: Int, val key: String? = null)
data class ApplyPresetArgs(val nozzle: Int, val bed: Int, val key: String = "apply_preset")
typealias PresetArgs = ApplyPresetArgs
data class JogArgs(val axis: String, val mm: Double, val feedMmMin: Int)
data class ForceMoveArgs(val axis: String, val mm: Double, val velocityMmS: Int)
data class HomeAxisArgs(val axis: String)
data class ExtrudeArgs(val mm: Double, val feedMmMin: Int)
data class SelectToolArgs(val index: Int)
data class MetadataArgs(val filename: String)
data class FileDirectoryArgs(val path: String? = null, val extended: Boolean = true)
data class FileNameArgs(val filename: String)
data class FileDeleteArgs(val path: String)
data class PrintStartArgs(val filename: String)
data class HistoryListArgs(val limit: Int = 1, val order: String = "desc")
data class GcodeStoreArgs(val count: Int = 1000)
data class ObjectSubsetArgs(val objects: Set<String>)
class ServerInfoArgs private constructor()

/**
 * Set/clear the Moonraker active spool (D-13). A non-null [spoolId] SETS it (`post_spool_id {spool_id}`);
 * null CLEARS it (`post_spool_id {}` — the real clear contract). Routed through the active JSON-RPC
 * session, gated on the `spoolman` component.
 */
data class SetSpoolArgs(val spoolId: Int?)

/**
 * A `server.spoolman.proxy` (use_v2_response=true) inventory request the lean SpoolmanClient issues
 * (D-07). [method] is the HTTP verb ("GET"/"PUT"), [path] the Spoolman REST path (e.g. "/v1/spool"),
 * [query] the optional already-URL-encoded query string (dotted keys encoded in the client layer).
 */
data class SpoolmanProxyArgs(val method: String, val path: String, val query: String? = null, val body: JsonObject? = null)

/** Manual-probe Z-jog nudge (D-01) — [step] is clamped to ±MAX_TESTZ_MM by [PrinterCommands.testZ]. */
data class TestZArgs(val step: Double)

/** Bed-mesh profile name arg (D-10) — [name] is allowlist-validated by [PrinterCommands.sanitizeProfileName]. */
data class BedMeshProfileArgs(val name: String)

/**
 * Z-babystep nudge arg (SC-5, Phase 16). [deltaMm] is canonicalized against the fixed
 * [PrinterCommands.BABYSTEP_STEPS] set (sign preserved) by [PrinterCommands.setGcodeOffsetZAdjust] —
 * NEGATIVE compresses (nozzle closer), POSITIVE expands.
 */
data class BabystepArgs(val deltaMm: Double)

object CommandRegistry {
    private val jsonRpcSemantics = CommandSemantics(
        success = "JSON-RPC result acknowledges the request.",
        error = "JSON-RPC error is surfaced by the existing client or dispatcher path.",
        acceptance = "Callers interpret result payloads using the existing state/holder logic.",
    )

    private val fileActionSemantics = CommandSemantics(
        success = "JSON-RPC result acknowledges Moonraker accepted the file request.",
        error = "JSON-RPC error is surfaced by the existing client or dispatcher path.",
        acceptance = "Callers confirm user-visible success from refreshed file browser or selected-file holder state.",
    )

    private val printActionSemantics = CommandSemantics(
        success = "JSON-RPC result acknowledges Moonraker accepted the print-control request.",
        error = "JSON-RPC error is surfaced by the existing client or dispatcher path.",
        acceptance = "Callers confirm user-visible success from print_stats, virtual_sdcard, and pause_resume state changes.",
    )

    private val gcodeSemantics = CommandSemantics(
        success = "Moonraker returns after the script completes.",
        error = "Klipper rejection text is surfaced as a non-fatal dispatch failure.",
        acceptance = "Script params are produced by PrinterCommands byte-identically.",
    )

    val identify: CommandSpec<IdentifyArgs> = jsonRpc(
        catalogId = "MR-server.connection.identify",
        method = JsonRpcMethods.IDENTIFY,
        key = { "identify" },
        params = { args ->
            buildJsonObject {
                put("client_name", args.clientName)
                put("version", args.version)
                put("type", args.type)
                put("url", args.url)
                args.apiKey?.let { put("api_key", it) }
            }
        },
    )

    val oneshotToken: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-access.oneshot_token",
        method = JsonRpcMethods.ONESHOT_TOKEN,
        key = { "oneshot_token" },
        params = { null },
    )

    val serverInfo: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-server.info",
        method = "server.info",
        key = { "server_info" },
        params = { null },
    )

    val objectsList: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-printer.objects.list",
        method = JsonRpcMethods.OBJECTS_LIST,
        key = { "objects_list" },
        params = { null },
    )

    val objectsQuery: CommandSpec<ObjectSubsetArgs> = jsonRpc(
        catalogId = "MR-printer.objects.query",
        method = JsonRpcMethods.OBJECTS_QUERY,
        key = { "objects_query" },
        params = { args -> objectsParam(args.objects) },
    )

    val objectsSubscribe: CommandSpec<ObjectSubsetArgs> = jsonRpc(
        catalogId = "MR-printer.objects.subscribe",
        method = JsonRpcMethods.OBJECTS_SUBSCRIBE,
        key = { "objects_subscribe" },
        params = { args -> objectsParam(args.objects) },
    )

    val temperatureStore: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-server.temperature_store",
        method = JsonRpcMethods.TEMPERATURE_STORE,
        key = { "temperature_store" },
        params = { null },
        availability = AvailabilityPredicate.ComponentPresent("history"),
    )

    val gcodeStore: CommandSpec<GcodeStoreArgs> = jsonRpc(
        catalogId = "MR-server.gcode_store",
        method = JsonRpcMethods.GCODE_STORE,
        key = { "gcode_store" },
        params = { args -> buildJsonObject { put("count", args.count) } },
    )

    val filesMetadata: CommandSpec<MetadataArgs> = jsonRpc(
        catalogId = "MR-server.files.metadata",
        method = JsonRpcMethods.FILES_METADATA,
        key = { args -> "files_metadata_${args.filename}" },
        params = { args -> buildJsonObject { put("filename", args.filename) } },
        availability = AvailabilityPredicate.ComponentPresent("file_manager"),
    )

    val filesGetDirectory: CommandSpec<FileDirectoryArgs> = jsonRpc(
        catalogId = "MR-server.files.get_directory",
        method = JsonRpcMethods.FILES_GET_DIRECTORY,
        key = { args -> "files_browse_${args.path ?: "root"}" },
        params = { args ->
            buildJsonObject {
                args.path?.let { put("path", it) }
                put("extended", args.extended)
            }
        },
        availability = AvailabilityPredicate.ComponentPresent("file_manager"),
        semantics = fileActionSemantics,
    )

    val filesThumbnails: CommandSpec<FileNameArgs> = jsonRpc(
        catalogId = "MR-server.files.thumbnails",
        method = JsonRpcMethods.FILES_THUMBNAILS,
        key = { args -> "files_thumbnails_${args.filename}" },
        params = { args -> buildJsonObject { put("filename", args.filename) } },
        availability = AvailabilityPredicate.ComponentPresent("file_manager"),
        semantics = fileActionSemantics,
    )

    val filesDelete: CommandSpec<FileDeleteArgs> = jsonRpc(
        catalogId = "MR-server.files.delete_file",
        method = JsonRpcMethods.FILES_DELETE_FILE,
        key = { args -> "files_delete_${args.path}" },
        params = { args -> buildJsonObject { put("path", args.path) } },
        availability = AvailabilityPredicate.ComponentPresent("file_manager"),
        semantics = fileActionSemantics,
    )

    val printStart: CommandSpec<PrintStartArgs> = jsonRpc(
        catalogId = "MR-printer.print.start",
        method = JsonRpcMethods.PRINT_START,
        key = { args -> "start_print_${args.filename}" },
        params = { args -> buildJsonObject { put("filename", args.filename) } },
        availability = AvailabilityPredicate.ObjectPresent("virtual_sdcard"),
        semantics = printActionSemantics,
    )

    val printPause: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-printer.print.pause",
        method = JsonRpcMethods.PRINT_PAUSE,
        key = { "pause_print" },
        params = { null },
        availability = AvailabilityPredicate.ObjectPresent("pause_resume"),
        semantics = printActionSemantics,
    )

    val printResume: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-printer.print.resume",
        method = JsonRpcMethods.PRINT_RESUME,
        key = { "resume_print" },
        params = { null },
        availability = AvailabilityPredicate.ObjectPresent("pause_resume"),
        semantics = printActionSemantics,
    )

    val printCancel: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-printer.print.cancel",
        method = JsonRpcMethods.PRINT_CANCEL,
        key = { "cancel_print" },
        params = { null },
        availability = AvailabilityPredicate.ObjectPresent("pause_resume"),
        semantics = printActionSemantics,
    )

    val historyList: CommandSpec<HistoryListArgs> = jsonRpc(
        catalogId = "MR-server.history.list",
        method = JsonRpcMethods.HISTORY_LIST,
        key = { "history_list" },
        params = { args ->
            buildJsonObject {
                put("limit", args.limit)
                put("order", args.order)
            }
        },
        availability = AvailabilityPredicate.ComponentPresent("history"),
    )

    val webcamsList: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-server.webcams.list",
        method = JsonRpcMethods.WEBCAMS_LIST,
        key = { "webcams_list" },
        params = { null },
        // NO availability predicate (D-08): the Moonraker `webcam` component is universal on E5/E3 — the
        // TILE is greyed on cam-count==0 (AppContainer.webcamCount), the command is never object-gated.
        // One-shot edge-driven read (cadence contract): NOT in V1_SUBSCRIBE_CORE, NOT polled.
    )

    // --- Phase-11 Spoolman active-spool + inventory specs (SPOOL-01/04/08; plan 11-04). ---
    // Active-spool state is Moonraker-owned over the EXISTING JSON-RPC session; inventory rides the
    // proxy passthrough. The read specs are ungated (status/get are universally answerable when the
    // component exists); the WRITE (post) and the proxy passthrough carry the ComponentPresent gate
    // (D-02) so they never fire on a printer without the spoolman component.

    val spoolmanStatus: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-server.spoolman.status",
        method = JsonRpcMethods.SPOOLMAN_STATUS,
        key = { "spoolman_status" },
        params = { null },
    )

    val spoolmanGetSpoolId: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-server.spoolman.get_spool_id",
        method = JsonRpcMethods.SPOOLMAN_GET_SPOOL_ID,
        key = { "spoolman_get_spool_id" },
        params = { null },
    )

    val spoolmanPostSpoolId: CommandSpec<SetSpoolArgs> = jsonRpc(
        catalogId = "MR-server.spoolman.post_spool_id",
        method = JsonRpcMethods.SPOOLMAN_POST_SPOOL_ID,
        key = { args -> "spoolman_post_${args.spoolId ?: "clear"}" },
        // D-13: a non-null id SETS (`{spool_id}`); null CLEARS (`{}` — empty params object).
        params = { args ->
            buildJsonObject {
                args.spoolId?.let { put("spool_id", it) }
            }
        },
        availability = AvailabilityPredicate.ComponentPresent("spoolman"),
    )

    val spoolmanProxy: CommandSpec<SpoolmanProxyArgs> = jsonRpc(
        catalogId = "MR-server.spoolman.proxy",
        method = JsonRpcMethods.SPOOLMAN_PROXY,
        key = { args -> "spoolman_proxy_${args.method}_${args.path}" },
        params = { args ->
            buildJsonObject {
                put("use_v2_response", true)
                put("request_method", args.method)
                put("path", args.path)
                args.query?.let { put("query", it) }
                args.body?.let { put("body", it) }
            }
        },
        availability = AvailabilityPredicate.ComponentPresent("spoolman"),
    )

    val emergencyStop: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-printer.emergency_stop",
        method = JsonRpcMethods.EMERGENCY_STOP,
        key = { "estop" },
        params = { null },
    )

    val firmwareRestart: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-printer.firmware_restart",
        method = JsonRpcMethods.FIRMWARE_RESTART,
        key = { "fw_restart" },
        params = { null },
    )

    val restart: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-printer.restart",
        method = JsonRpcMethods.RESTART,
        key = { "host_restart" },
        params = { null },
    )

    val setHeater: CommandSpec<SetHeaterArgs> = gcode(
        catalogId = "KGC-SET_HEATER_TEMPERATURE",
        key = { args -> args.key ?: "set_${args.heater}" },
        gcode = { args -> PrinterCommands.setHeater(args.heater, args.target) },
        availability = AvailabilityPredicate.ObjectPresent("extruder"),
    )

    val applyPreset: CommandSpec<ApplyPresetArgs> = gcode(
        catalogId = "KGC-SET_HEATER_TEMPERATURE_PRESET",
        key = { args -> args.key },
        gcode = { args -> PrinterCommands.applyPreset(args.nozzle, args.bed) },
        availability = AvailabilityPredicate.ObjectPresent("heater_bed"),
    )

    val jog: CommandSpec<JogArgs> = gcode(
        catalogId = "KGC-G1_JOG",
        key = { args -> "jog_${args.axis}" },
        gcode = { args -> PrinterCommands.jog(args.axis, args.mm, args.feedMmMin) },
        availability = AvailabilityPredicate.ObjectPresent("toolhead"),
    )

    val overrideJog: CommandSpec<JogArgs> = gcode(
        catalogId = "KGC-G1_OVERRIDE_JOG",
        key = { args -> "override_jog_${args.axis}" },
        gcode = { args -> PrinterCommands.overrideJog(args.axis, args.mm, args.feedMmMin) },
        availability = AvailabilityPredicate.ObjectPresent("toolhead"),
    )

    val forceMove: CommandSpec<ForceMoveArgs> = gcode(
        catalogId = "KGC-FORCE_MOVE",
        key = { args -> "jog_${args.axis}" },
        gcode = { args -> PrinterCommands.forceMove(args.axis, args.mm, args.velocityMmS) },
        availability = AvailabilityPredicate.GcodeCommandPresent("FORCE_MOVE"),
    )

    val homeAll: CommandSpec<Unit> = gcode(
        catalogId = "KGC-G28_HOME_ALL",
        key = { "home_all" },
        gcode = { PrinterCommands.homeAll() },
        availability = AvailabilityPredicate.ObjectPresent("toolhead"),
    )

    val homeXY: CommandSpec<Unit> = gcode(
        catalogId = "KGC-G28_HOME_XY",
        key = { "home_xy" },
        gcode = { PrinterCommands.homeXY() },
        availability = AvailabilityPredicate.ObjectPresent("toolhead"),
    )

    val homeAxis: CommandSpec<HomeAxisArgs> = gcode(
        catalogId = "KGC-G28_HOME_AXIS",
        key = { args -> "home_${args.axis}" },
        gcode = { args -> PrinterCommands.homeAxis(args.axis) },
        availability = AvailabilityPredicate.ObjectPresent("toolhead"),
    )

    val extrude: CommandSpec<ExtrudeArgs> = gcode(
        catalogId = "KGC-G1_EXTRUDE",
        key = { args -> if (args.mm < 0) "retract" else "extrude" },
        gcode = { args -> PrinterCommands.extrude(args.mm, args.feedMmMin) },
        availability = AvailabilityPredicate.ObjectPresent("extruder"),
    )

    val selectTool: CommandSpec<SelectToolArgs> = gcode(
        catalogId = "KGC-T_SELECT_TOOL",
        key = { args -> "tool_${args.index}" },
        gcode = { args -> PrinterCommands.selectTool(args.index) },
        availability = AvailabilityPredicate.ObjectPresent("extruder"),
    )

    val loadFilament: CommandSpec<Unit> = gcode(
        catalogId = "KGC-LOAD_FILAMENT",
        key = { "load" },
        gcode = { PrinterCommands.loadFilament() },
        availability = AvailabilityPredicate.MacroPresent("LOAD_FILAMENT"),
    )

    val unloadFilament: CommandSpec<Unit> = gcode(
        catalogId = "KGC-UNLOAD_FILAMENT",
        key = { "unload" },
        gcode = { PrinterCommands.unloadFilament() },
        availability = AvailabilityPredicate.MacroPresent("UNLOAD_FILAMENT"),
    )

    val cooldown: CommandSpec<Unit> = gcode(
        catalogId = "KGC-TURN_OFF_HEATERS",
        key = { "cooldown" },
        gcode = { PrinterCommands.COOLDOWN },
        availability = AvailabilityPredicate.ObjectPresent("extruder"),
    )

    val disableSteppers: CommandSpec<Unit> = gcode(
        catalogId = "KGC-M84_DISABLE_STEPPERS",
        key = { "disable_steppers" },
        gcode = { PrinterCommands.DISABLE_STEPPERS },
        availability = AvailabilityPredicate.ObjectPresent("toolhead"),
    )

    // --- Calibration commands (Phase 9). Every gcode spec inherits the G4 120s timeout automatically
    // (CommandDispatcher keys the long timeout off method == GCODE_SCRIPT — no per-spec timeout code). ---

    val screwsTiltCalculate: CommandSpec<Unit> = gcode(
        catalogId = "KGC-SCREWS_TILT_CALCULATE",
        key = { "screws_tilt" },
        gcode = { PrinterCommands.SCREWS_TILT_CALCULATE },
        availability = AvailabilityPredicate.ObjectPresent("screws_tilt_adjust"),
    )

    val zTiltAdjust: CommandSpec<Unit> = gcode(
        catalogId = "KGC-Z_TILT_ADJUST",
        key = { "z_tilt_adjust" },
        gcode = { PrinterCommands.Z_TILT_ADJUST },
        availability = AvailabilityPredicate.ObjectPresent("z_tilt"),
    )

    val quadGantryLevel: CommandSpec<Unit> = gcode(
        catalogId = "KGC-QUAD_GANTRY_LEVEL",
        key = { "quad_gantry_level" },
        gcode = { PrinterCommands.QUAD_GANTRY_LEVEL },
        // Built blind (D-02): gates itself off on both test printers via the live object predicate.
        availability = AvailabilityPredicate.ObjectPresent("quad_gantry_level"),
    )

    val bedMeshCalibrate: CommandSpec<Unit> = gcode(
        catalogId = "KGC-BED_MESH_CALIBRATE",
        key = { "bed_mesh_calibrate" },
        gcode = { PrinterCommands.BED_MESH_CALIBRATE },
        availability = AvailabilityPredicate.ObjectPresent("bed_mesh"),
    )

    val bedMeshProfileSave: CommandSpec<BedMeshProfileArgs> = gcode(
        catalogId = "KGC-BED_MESH_PROFILE_SAVE",
        key = { "bed_mesh_profile_save" },
        gcode = { args -> PrinterCommands.bedMeshProfileSave(args.name) },
        availability = AvailabilityPredicate.ObjectPresent("bed_mesh"),
    )

    val bedMeshProfileLoad: CommandSpec<BedMeshProfileArgs> = gcode(
        catalogId = "KGC-BED_MESH_PROFILE_LOAD",
        key = { "bed_mesh_profile_load" },
        gcode = { args -> PrinterCommands.bedMeshProfileLoad(args.name) },
        availability = AvailabilityPredicate.ObjectPresent("bed_mesh"),
    )

    val bedMeshProfileRemove: CommandSpec<BedMeshProfileArgs> = gcode(
        catalogId = "KGC-BED_MESH_PROFILE_REMOVE",
        key = { "bed_mesh_profile_remove" },
        gcode = { args -> PrinterCommands.bedMeshProfileRemove(args.name) },
        availability = AvailabilityPredicate.ObjectPresent("bed_mesh"),
    )

    val probeCalibrate: CommandSpec<Unit> = gcode(
        catalogId = "KGC-PROBE_CALIBRATE",
        key = { "probe_calibrate" },
        gcode = { PrinterCommands.PROBE_CALIBRATE },
        availability = AvailabilityPredicate.ObjectPresent("probe"),
    )

    val zEndstopCalibrate: CommandSpec<Unit> = gcode(
        catalogId = "KGC-Z_ENDSTOP_CALIBRATE",
        key = { "z_endstop_calibrate" },
        gcode = { PrinterCommands.Z_ENDSTOP_CALIBRATE },
        // Probe-LESS sibling (A3): gate on the printer actually EXPOSING the command, NOT ObjectPresent("probe")
        // — a probe predicate would hide it on exactly the probe-less printers that need it. Mirrors FORCE_MOVE.
        availability = AvailabilityPredicate.GcodeCommandPresent("Z_ENDSTOP_CALIBRATE"),
    )

    val testZ: CommandSpec<TestZArgs> = gcode(
        catalogId = "KGC-TESTZ",
        key = { "testz" },
        gcode = { args -> PrinterCommands.testZ(args.step) },
        availability = AvailabilityPredicate.ObjectPresent("manual_probe"),
    )

    val accept: CommandSpec<Unit> = gcode(
        catalogId = "KGC-ACCEPT",
        key = { "accept" },
        gcode = { PrinterCommands.ACCEPT },
        availability = AvailabilityPredicate.ObjectPresent("manual_probe"),
    )

    val abort: CommandSpec<Unit> = gcode(
        catalogId = "KGC-ABORT",
        key = { "abort" },
        gcode = { PrinterCommands.ABORT },
        availability = AvailabilityPredicate.ObjectPresent("manual_probe"),
    )

    val saveConfig: CommandSpec<Unit> = gcode(
        catalogId = "KGC-SAVE_CONFIG",
        key = { "save_config" },
        gcode = { PrinterCommands.SAVE_CONFIG },
        // Host action, NOT object-gated (D-12) — available whenever connected.
        availability = AvailabilityPredicate.Always,
    )

    // --- Phase-16 Print-Status: Z-babystep (SC-5) + Terminal Dismiss (D-05). ---

    /**
     * `SET_GCODE_OFFSET Z_ADJUST=<signed step> MOVE=1` — the session-only Z-babystep nudge (SC-5). Gated
     * on `gcode_move` (which carries `homing_origin[2]`, the running offset the Print-Status reads back).
     * The signed step is canonicalized against the fixed BABYSTEP_STEPS set by the pure builder (V5).
     */
    val babystepZ: CommandSpec<BabystepArgs> = gcode(
        catalogId = "KGC-SET_GCODE_OFFSET",
        key = { "babystep" },
        gcode = { PrinterCommands.setGcodeOffsetZAdjust(it.deltaMm) },
        availability = AvailabilityPredicate.ObjectPresent("gcode_move"),
    )

    /**
     * `SDCARD_RESET_FILE` — clear the loaded file after a Terminal print (D-05). Fixed const gcode, no
     * params; gated on `virtual_sdcard` (mirrors [printStart]).
     */
    val dismissPrint: CommandSpec<Unit> = gcode(
        catalogId = "KGC-SDCARD_RESET_FILE",
        key = { "dismiss_print" },
        gcode = { PrinterCommands.SDCARD_RESET_FILE },
        availability = AvailabilityPredicate.ObjectPresent("virtual_sdcard"),
    )

    val all: List<CommandSpec<*>> = listOf(
        identify,
        oneshotToken,
        serverInfo,
        objectsList,
        objectsQuery,
        objectsSubscribe,
        temperatureStore,
        gcodeStore,
        filesMetadata,
        filesGetDirectory,
        filesThumbnails,
        filesDelete,
        printStart,
        printPause,
        printResume,
        printCancel,
        historyList,
        webcamsList,
        spoolmanStatus,
        spoolmanGetSpoolId,
        spoolmanPostSpoolId,
        spoolmanProxy,
        emergencyStop,
        firmwareRestart,
        restart,
        setHeater,
        applyPreset,
        jog,
        overrideJog,
        forceMove,
        homeAll,
        homeXY,
        homeAxis,
        extrude,
        selectTool,
        loadFilament,
        unloadFilament,
        cooldown,
        disableSteppers,
        screwsTiltCalculate,
        zTiltAdjust,
        quadGantryLevel,
        bedMeshCalibrate,
        bedMeshProfileSave,
        bedMeshProfileLoad,
        bedMeshProfileRemove,
        probeCalibrate,
        zEndstopCalibrate,
        testZ,
        accept,
        abort,
        saveConfig,
        babystepZ,
        dismissPrint,
    )

    private fun objectsParam(objects: Set<String>): JsonElement = buildJsonObject {
        putJsonObject("objects") {
            objects.forEach { put(it, JsonNull) }
        }
    }

    private fun <P> jsonRpc(
        catalogId: String,
        method: String,
        key: (P) -> String,
        params: (P) -> JsonElement?,
        availability: AvailabilityPredicate = AvailabilityPredicate.Always,
        semantics: CommandSemantics = jsonRpcSemantics,
    ): CommandSpec<P> = CommandSpec(
        catalogId = catalogId,
        transport = CommandTransport.JsonRpc,
        method = method,
        dispatchKey = key,
        params = params,
        availability = availability,
        semantics = semantics,
    )

    private fun <P> gcode(
        catalogId: String,
        key: (P) -> String,
        gcode: (P) -> String,
        availability: AvailabilityPredicate,
    ): CommandSpec<P> = CommandSpec(
        catalogId = catalogId,
        transport = CommandTransport.GcodeScript,
        method = JsonRpcMethods.GCODE_SCRIPT,
        dispatchKey = key,
        params = { args -> PrinterCommands.scriptParams(gcode(args)) },
        availability = availability,
        semantics = gcodeSemantics,
    )
}
