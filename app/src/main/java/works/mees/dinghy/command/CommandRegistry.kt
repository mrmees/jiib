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

// --- Phase-17 Fine-Tune live-adjust args (D-03..D-12) -----------------------------------------------

/** Speed-factor override (D-03) — [pct] is the DISPLAYED percent; [PrinterCommands.speedFactor] clamps. */
data class SpeedFactorArgs(val pct: Int)

/** Flow/extrude-factor override (D-08) — [pct] is the DISPLAYED percent; [PrinterCommands.flowFactor] clamps. */
data class FlowFactorArgs(val pct: Int)

/**
 * One motion-limit nudge (D-04..D-07). [field] names WHICH limit this nudge sets — one of
 * `"velocity"`/`"accel"`/`"minCruiseRatio"`/`"scv"` — so a single spec serves all four; it also yields a
 * DISTINCT per-field dispatchKey (`"set_vel_<field>"`, Pitfall 4) so an in-flight velocity tweak never
 * busy-locks an accel tweak by key-collision. [value] is the target (the ratio on the wire for minCruiseRatio).
 */
data class VelocityLimitArgs(val field: String, val value: Double) {
    enum class Field { VELOCITY, ACCEL, MIN_CRUISE_RATIO, SCV }
    companion object {
        const val VELOCITY = "velocity"
        const val ACCEL = "accel"
        const val MIN_CRUISE_RATIO = "minCruiseRatio"
        const val SCV = "scv"
    }
}

/**
 * One pressure-advance nudge (D-09/D-10). [field] is `"advance"` or `"smoothTime"` (distinct dispatchKey
 * per field); [value] the target in seconds. Only the named field is sent (no EXTRUDER=, single-extruder v1).
 */
data class PressureAdvanceArgs(val field: String, val value: Double) {
    companion object {
        const val ADVANCE = "advance"
        const val SMOOTH_TIME = "smoothTime"
    }
}

/** Part-cooling-fan nudge (D-11) — [pct] is the DISPLAYED percent; [PrinterCommands.setFan] maps to 0..255. */
data class FanArgs(val pct: Int)

/**
 * Firmware-retraction four-field nudge (D-12). All four are re-sent each nudge (the holder folds the changed
 * field over the live readback); [PrinterCommands.setRetraction] clamps each. NO Z_HOP.
 */
data class RetractionArgs(
    val retractLength: Double,
    val unretractExtraLength: Double,
    val retractSpeed: Int,
    val unretractSpeed: Int,
)

// --- Phase-19 generic-output args (SC-2/SC-3) -------------------------------------------------------
// EVERY [name] is the BARE Klipper section name (HIGH-1) — the family prefix never reaches the wire.
// The dispatchKey is scoped per-output (`set_output_<family>_<name>`) so two outputs never busy-collide.

/** Generic-fan (`fan_generic`) nudge — [name] BARE, [pct] DISPLAYED percent; [PrinterCommands.setGenericFan] clamps. */
data class SetGenericFanArgs(val name: String, val pct: Int)

/** LED (`led`/`neopixel`/…) set — [name] BARE, channels 0f..1f; [w] non-null only on a white-channel LED. D-12 Off = all-zero. */
data class SetLedArgs(val name: String, val r: Float, val g: Float, val b: Float, val w: Float? = null)

/**
 * Servo set/disable — [name] BARE. When [disable] is true the servo is turned off (`WIDTH=0`); otherwise
 * it is set to [deg] clamped to 0..[maxDeg]. One args type folds both forms so the page's Off affordance
 * dispatches the same spec.
 */
data class SetServoArgs(val name: String, val deg: Int = 0, val maxDeg: Int = PrinterCommands.SERVO_ANGLE_DEFAULT_MAX, val disable: Boolean = false)

/**
 * `output_pin`/`pwm_tool` set — [name] BARE. [pwm] selects the form: true → `setPinPwm(name, pct)`;
 * false → `setPinDigital(name, on)`. One spec serves digital + PWM output_pin AND pwm_tool (no dedicated pwm-tool command).
 */
data class SetOutputPinArgs(val name: String, val pwm: Boolean, val pct: Int = 0, val on: Boolean = false)

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

    // --- Phase-20 System Information one-shot host-telemetry queries (SYS-01/02/03). Both no-args
    // queries with Always availability — every Moonraker host exposes machine.* unconditionally (NO
    // ComponentPresent gate). Seeded edge-driven per handshake; the 1 Hz live plane rides the
    // notify_proc_stat_update push (NOT these). These supply identity (system_info) + throttle/uptime
    // (proc_stats), both OMITTED from the push.

    /** `machine.system_info` — static host identity (cpu_info, distribution). One-shot per handshake. */
    val machineSystemInfo: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-machine.system_info",
        method = "machine.system_info",
        key = { "machine_system_info" },
        params = { null },
    )

    /** `machine.proc_stats` — throttled_state + system_uptime (the push omits both). One-shot per handshake. */
    val machineProcStats: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-machine.proc_stats",
        method = "machine.proc_stats",
        key = { "machine_proc_stats" },
        params = { null },
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
        availability = AvailabilityPredicate.MacroPresent(CommandMap.loadFilament.macro),
    )

    val unloadFilament: CommandSpec<Unit> = gcode(
        catalogId = "KGC-UNLOAD_FILAMENT",
        key = { "unload" },
        gcode = { PrinterCommands.unloadFilament() },
        availability = AvailabilityPredicate.MacroPresent(CommandMap.unloadFilament.macro),
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

    // --- Phase-17 Fine-Tune live-adjust specs (D-03..D-12). Every gcode spec inherits the G4 120s
    // timeout automatically. Gated STRICTLY on the owning OBJECT (RESEARCH § Capability-Gate Predicates) —
    // SET_VELOCITY_LIMIT/SET_PRESSURE_ADVANCE/SET_RETRACTION are built-in Klipper commands present whenever
    // their owning object exists, so NO GcodeCommandPresent help-query path is invented. ---

    /** `M220 S<pct>` speed-factor override (D-03). Gated on `gcode_move` (carries `speed_factor` readback). */
    val speedFactor: CommandSpec<SpeedFactorArgs> = gcode(
        catalogId = "KGC-M220",
        key = { "set_speed_factor" },
        gcode = { args -> PrinterCommands.speedFactor(args.pct) },
        availability = AvailabilityPredicate.ObjectPresent("gcode_move"),
    )

    /** `M221 S<pct>` flow-factor override (D-08). Gated on `gcode_move` (carries `extrude_factor` readback). */
    val flowFactor: CommandSpec<FlowFactorArgs> = gcode(
        catalogId = "KGC-M221",
        key = { "set_flow_factor" },
        gcode = { args -> PrinterCommands.flowFactor(args.pct) },
        availability = AvailabilityPredicate.ObjectPresent("gcode_move"),
    )

    /**
     * `SET_VELOCITY_LIMIT <FIELD>=<value>` (D-04..D-07) — ONE spec serves all four motion limits. Each
     * nudge sets exactly one field; the [VelocityLimitArgs.field] selects which builder arg is non-null and
     * yields a DISTINCT dispatchKey `set_vel_<field>` (Pitfall 4 — never a shared key). Gated on `toolhead`.
     */
    val setVelocityLimit: CommandSpec<VelocityLimitArgs> = gcode(
        catalogId = "KGC-SET_VELOCITY_LIMIT",
        key = { args -> "set_vel_${args.field}" },
        gcode = { args ->
            when (args.field) {
                VelocityLimitArgs.VELOCITY -> PrinterCommands.setVelocityLimit(velocity = args.value)
                VelocityLimitArgs.ACCEL -> PrinterCommands.setVelocityLimit(accel = args.value)
                VelocityLimitArgs.MIN_CRUISE_RATIO -> PrinterCommands.setVelocityLimit(minCruiseRatio = args.value)
                VelocityLimitArgs.SCV -> PrinterCommands.setVelocityLimit(scv = args.value)
                else -> error("unknown SET_VELOCITY_LIMIT field '${args.field}'")
            }
        },
        availability = AvailabilityPredicate.ObjectPresent("toolhead"),
    )

    /**
     * `SET_PRESSURE_ADVANCE <FIELD>=<value>` (D-09/D-10). One spec, two fields (advance/smoothTime) keyed
     * distinctly (`set_pa_<field>`). Gated on `extruder`. No EXTRUDER= param (single-extruder v1, D-09).
     */
    val setPressureAdvance: CommandSpec<PressureAdvanceArgs> = gcode(
        catalogId = "KGC-SET_PRESSURE_ADVANCE",
        key = { args -> "set_pa_${args.field}" },
        gcode = { args ->
            when (args.field) {
                PressureAdvanceArgs.ADVANCE -> PrinterCommands.setPressureAdvance(advance = args.value)
                PressureAdvanceArgs.SMOOTH_TIME -> PrinterCommands.setPressureAdvance(smoothTime = args.value)
                else -> error("unknown SET_PRESSURE_ADVANCE field '${args.field}'")
            }
        },
        availability = AvailabilityPredicate.ObjectPresent("extruder"),
    )

    /** `M106 S<0..255>` part-cooling fan (D-11). Gated STRICTLY on the part-cooling `fan` object only. */
    val setFan: CommandSpec<FanArgs> = gcode(
        catalogId = "KGC-M106",
        key = { "set_fan" },
        gcode = { args -> PrinterCommands.setFan(args.pct) },
        availability = AvailabilityPredicate.ObjectPresent("fan"),
    )

    /**
     * `SET_RETRACTION …` four-field firmware-retraction (D-12). Gated on `firmware_retraction` — built
     * BLIND (neither dev printer exposes it), mirroring [quadGantryLevel]: the live object predicate gates
     * it off on both test printers; the matrix records the not_on_printers exclusion. No Z_HOP.
     */
    val setRetraction: CommandSpec<RetractionArgs> = gcode(
        catalogId = "KGC-SET_RETRACTION",
        key = { "set_retraction" },
        gcode = { args ->
            PrinterCommands.setRetraction(
                retractLength = args.retractLength,
                unretractExtraLength = args.unretractExtraLength,
                retractSpeed = args.retractSpeed,
                unretractSpeed = args.unretractSpeed,
            )
        },
        availability = AvailabilityPredicate.ObjectPresent("firmware_retraction"),
    )

    // --- Phase-19 generic-output specs (SC-2/SC-3, HIGH-2 — dispatched through the catalog) --------
    //
    // Every output family is a typed CommandSpec delegating BYTE-IDENTICALLY to the Task-1 PrinterCommands
    // builder (D-11 drift contract — the registry NEVER re-templates the string). args carry the BARE
    // section name (HIGH-1). dispatchKey is scoped per-output so two outputs never busy-collide. Availability
    // is `Always`: the runtime descriptor discovery (19-04) is the SOURCE OF TRUTH for what the UI offers —
    // the registry spec does not re-derive object presence, so it does not duplicate the descriptor gate.
    // heater_generic reuses the EXISTING [setHeater] spec (the descriptor mapping passes the bare name).

    /** `SET_FAN_SPEED FAN=<name> SPEED=<0..1>` — a generic (`fan_generic`) fan. BARE name (HIGH-1). */
    val setGenericFan: CommandSpec<SetGenericFanArgs> = gcode(
        catalogId = "KGC-SET_FAN_SPEED-OUT",
        key = { "set_output_fan_${it.name}" },
        gcode = { args -> PrinterCommands.setGenericFan(args.name, args.pct) },
        availability = AvailabilityPredicate.Always,
    )

    /** `SET_LED LED=<name> RED=.. GREEN=.. BLUE=.. [WHITE=..]` — an LED/neopixel. BARE name (HIGH-1); D-12 Off = all-zero. */
    val setLed: CommandSpec<SetLedArgs> = gcode(
        catalogId = "KGC-SET_LED",
        key = { "set_output_led_${it.name}" },
        gcode = { args -> PrinterCommands.setLed(args.name, args.r, args.g, args.b, args.w) },
        availability = AvailabilityPredicate.Always,
    )

    /** `SET_SERVO SERVO=<name> ANGLE=<…>` / `WIDTH=0` (disable) — a servo. BARE name (HIGH-1). */
    val setServo: CommandSpec<SetServoArgs> = gcode(
        catalogId = "KGC-SET_SERVO-OUT",
        key = { "set_output_servo_${it.name}" },
        gcode = { args ->
            if (args.disable) PrinterCommands.setServoDisable(args.name)
            else PrinterCommands.setServoAngle(args.name, args.deg, args.maxDeg)
        },
        availability = AvailabilityPredicate.Always,
    )

    /**
     * `SET_PIN PIN=<name> VALUE=<…>` — one spec serves digital + PWM `output_pin` AND `pwm_tool` (no
     * dedicated pwm-tool command). Branches on [SetOutputPinArgs.pwm]. BARE name (HIGH-1).
     */
    val setOutputPin: CommandSpec<SetOutputPinArgs> = gcode(
        catalogId = "KGC-SET_PIN-OUT",
        key = { "set_output_pin_${it.name}" },
        gcode = { args ->
            if (args.pwm) PrinterCommands.setPinPwm(args.name, args.pct)
            else PrinterCommands.setPinDigital(args.name, args.on)
        },
        availability = AvailabilityPredicate.Always,
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
        machineSystemInfo,
        machineProcStats,
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
        speedFactor,
        flowFactor,
        setVelocityLimit,
        setPressureAdvance,
        setFan,
        setRetraction,
        setGenericFan,
        setLed,
        setServo,
        setOutputPin,
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
