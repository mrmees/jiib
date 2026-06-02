package works.mees.dinghy.command

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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
