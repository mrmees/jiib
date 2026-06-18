package works.mees.dinghy.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * JSON-RPC 2.0 envelope types for the Moonraker websocket
 * (`ws://<host>:<port>/websocket`) — the public wire contract Wave 2/3 code against.
 *
 * Design note (locked per CLAUDE.md "Networking deep-dive" + 02-RESEARCH § "JSON-RPC framing"):
 * `params`/`result` are LOOSE [JsonElement]?, NOT strict DTOs. Moonraker's `objects/subscribe`
 * deltas and `notify_status_update` payloads are heterogeneous, partial, and schema-loose; the
 * reducer (Wave 2) walks the [JsonElement] tree rather than decoding into rigid classes. The only
 * things modeled strictly are the JSON-RPC frame itself and the error object.
 *
 * Frame shapes (02-RESEARCH, all CITED):
 * - Request:      `{ "jsonrpc":"2.0", "method":..., "params":..., "id":N }`
 * - Success:      `{ "jsonrpc":"2.0", "result":..., "id":N }`
 * - Error:        `{ "jsonrpc":"2.0", "error":{ "code":Int, "message":String }, "id":N }`
 * - Notification: `{ "jsonrpc":"2.0", "method":..., "params":... }`  (NO id — basis of STATE-05 routing)
 */

/** Outbound JSON-RPC request. [id] is client-chosen and unique (correlate the reply by id). */
@Serializable
data class JsonRpcRequest(
    val method: String,
    val params: JsonElement? = null,
    val id: Long,
    val jsonrpc: String = "2.0",
)

/**
 * A JSON-RPC success response. Echoes the request [id]; [result] is the loose Moonraker payload
 * (e.g. an `{eventtime, status}` snapshot for query/subscribe, or `{objects:[...]}` for list).
 */
@Serializable
data class JsonRpcResponse(
    val result: JsonElement? = null,
    val id: Long? = null,
    val jsonrpc: String = "2.0",
)

/**
 * A JSON-RPC error response. The canonical "auth required" signal over the socket is an identify
 * reply carrying `{code:-32602, message:"Unauthorized"}` (02-RESEARCH § "Auth handshake"); classify
 * it via [RpcError.classifyIdentifyError].
 */
@Serializable
data class JsonRpcErrorResponse(
    val error: JsonRpcErrorObject,
    val id: Long? = null,
    val jsonrpc: String = "2.0",
)

/** The `error` object inside an error response. */
@Serializable
data class JsonRpcErrorObject(
    val code: Int,
    val message: String,
)

/**
 * An UNSOLICITED server notification — has [method], has NO id. The presence of `method` with an
 * absent `id` is exactly how STATE-05 routing distinguishes a push from a request reply.
 *
 * `notify_status_update` carries a 2-element array `[ {changed objects}, eventtime ]`;
 * `notify_gcode_response` carries a 1-element `[ "message" ]`; the `notify_klippy_*` carry no params.
 */
@Serializable
data class JsonRpcNotification(
    val method: String,
    val params: JsonElement? = null,
    val jsonrpc: String = "2.0",
)

/**
 * The SINGLE shared [Json] instance for the whole connection layer (02-PATTERNS § "Json posture").
 * `ignoreUnknownKeys`/`isLenient` are mandatory for Moonraker's loose payloads. Do NOT build a
 * `Json {}` per message — reuse this one (and walk wire data null-safely, never `!!`).
 */
val MoonrakerJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

/**
 * Well-known JSON-RPC method names this phase models. Notification method names are matched on the
 * inbound path; request method names are used to build [JsonRpcRequest]s in Wave 2.
 */
object JsonRpcMethods {
    // Requests (client → server)
    const val IDENTIFY = "server.connection.identify"
    const val OBJECTS_LIST = "printer.objects.list"
    const val OBJECTS_QUERY = "printer.objects.query"
    const val OBJECTS_SUBSCRIBE = "printer.objects.subscribe"
    // Moonraker maps the REST path /printer/query_endstops/status to a JSON-RPC method with DOTS
    // (the `query_endstops` segment keeps its underscore; only the path '/' becomes '.'). A slash
    // here yields -32601 Method not found. Verified live 2026-06-18.
    const val QUERY_ENDSTOPS_STATUS = "printer.query_endstops.status"
    const val ONESHOT_TOKEN = "access.oneshot_token"

    // Action requests (client → server) — the destructive/recovery set CommandDispatcher wraps
    // (D-10/D-12; verified live in 04-RESEARCH). E-stop drives klippy_state → shutdown → Splash;
    // the two restarts are the Splash recovery actions.
    const val EMERGENCY_STOP = "printer.emergency_stop"
    const val FIRMWARE_RESTART = "printer.firmware_restart"
    const val RESTART = "printer.restart"

    // Runs ALL action gcodes (Move jog, temp set, extrude, macros) — Phase-5 action surface (RESEARCH §5).
    const val GCODE_SCRIPT = "printer.gcode.script"

    // One-shot temperature-history backfill for the temp graph (RESEARCH §1).
    const val TEMPERATURE_STORE = "server.temperature_store"

    // One-shot console-history backfill (CONS-02 / D-02 — Mainsail-parity replace-on-(re)connect).
    // Returns `{gcode_store:[{message,time,type}]}`; params `{count}` (server default cache 1000).
    const val GCODE_STORE = "server.gcode_store"

    // One-shot per-file gcode metadata (260601-sip Inc 2) — thumbnails, layer_count, object_height,
    // estimated_time; fetched ONCE per active print filename for the Status home derived cells.
    const val FILES_METADATA = "server.files.metadata"

    // One-shot webcam enumeration (Phase 10 CAM-01). Edge-driven ONCE per handshake (NOT subscribed,
    // NOT polled — cadence contract Rule 3); forwarded off the session as SpineHandle.webcams.
    const val WEBCAMS_LIST = "server.webcams.list"

    // Phase-7 file browser and core print-loop JSON-RPC commands.
    const val FILES_GET_DIRECTORY = "server.files.get_directory"
    const val FILES_THUMBNAILS = "server.files.thumbnails"
    const val FILES_DELETE_FILE = "server.files.delete_file"
    const val PRINT_START = "printer.print.start"
    const val PRINT_PAUSE = "printer.print.pause"
    const val PRINT_RESUME = "printer.print.resume"
    const val PRINT_CANCEL = "printer.print.cancel"

    // One-shot most-recent job history (260601-th9 Inc 3) — server.history.list?limit=1&order=desc;
    // fetched ONCE per not-printing transition for the idle Status "last completed job" card.
    const val HISTORY_LIST = "server.history.list"

    // Phase-11 Spoolman active-spool + inventory JSON-RPC surface (SPOOL-01/04/08; plan 11-04).
    // status/get/post drive the Moonraker-owned ACTIVE spool over the existing JSON-RPC session;
    // proxy is the use_v2_response=true REST passthrough the lean SpoolmanClient reads inventory through.
    const val SPOOLMAN_STATUS = "server.spoolman.status"
    const val SPOOLMAN_GET_SPOOL_ID = "server.spoolman.get_spool_id"
    const val SPOOLMAN_POST_SPOOL_ID = "server.spoolman.post_spool_id"
    const val SPOOLMAN_PROXY = "server.spoolman.proxy"

    // Notifications (server → client, no id)
    const val NOTIFY_STATUS_UPDATE = "notify_status_update"
    const val NOTIFY_GCODE_RESPONSE = "notify_gcode_response"
    const val NOTIFY_KLIPPY_READY = "notify_klippy_ready"
    const val NOTIFY_KLIPPY_SHUTDOWN = "notify_klippy_shutdown"
    const val NOTIFY_KLIPPY_DISCONNECTED = "notify_klippy_disconnected"

    // Phase-11 Spoolman server-push notifications (SPOOL-08 / D-10 external-change reconciliation).
    // Both carry `params` as a 1-ELEMENT array: active-spool-set → `[{spool_id}]`, status-changed →
    // `[{spoolman_connected}]`. An external mutator (Fluidd, a runout macro) pushes these.
    const val NOTIFY_ACTIVE_SPOOL_SET = "notify_active_spool_set"
    const val NOTIFY_SPOOLMAN_STATUS_CHANGED = "notify_spoolman_status_changed"

    // Phase-20 System Information: the free ~1 Hz host-telemetry push (cpu_temp / system_cpu_usage /
    // system_memory). params is a 1-ELEMENT array `[{...}]`. The push OMITS throttled_state +
    // system_uptime (those come from the one-shot machine.proc_stats QUERY). Previously dropped at
    // the dispatch `else`; now routed to JsonRpcClient.procStatUpdates (20-03 Task 1).
    const val NOTIFY_PROC_STAT_UPDATE = "notify_proc_stat_update"

    /** JSON-RPC error code Moonraker returns from `identify` when credentials are missing/invalid. */
    const val CODE_INVALID_PARAMS = -32602
}
