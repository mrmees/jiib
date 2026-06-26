package works.mees.jiib.spool

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import works.mees.jiib.net.GoldenFixtures
import works.mees.jiib.net.MoonrakerJson

/**
 * Hardened test double for the (not-yet-built) Moonraker active-spool session surface
 * (`server.spoolman.status` / `get_spool_id` / `post_spool_id`) and the two server-push notifications.
 * Wave 0: defines its OWN test-facing surface (no unbuilt production reference) so it compiles standalone.
 *
 * MOCK-VS-REALITY DISCIPLINE: the [status] reply is lifted VERBATIM from the live
 * `spoolman-live-ender5-status-before-set.json` golden (`{spoolman_connected:true, spool_id:5,
 * pending_reports:[]}`), so a parser that mis-keys `spool_id` or assumes `pending_reports` non-empty
 * fails. [postSpoolId] records the set id and an empty params object (`{}`) CLEARS the active spool to
 * null (D-13). [injectActiveSpoolSet] / [injectSpoolmanStatusChanged] produce frames whose `params` is a
 * 1-ELEMENT ARRAY (the live contract — Moonraker always wraps a single object in an array), so a router
 * that reads `params` as a bare object instead of `params[0]` fails.
 *
 * All JSON is parsed/built with the shared [MoonrakerJson].
 */
class FakeMoonrakerSpoolmanSession {

    /** The current active spool id, mirroring `get_spool_id`/`post_spool_id`. Null = cleared. */
    var activeSpoolId: Int? = 5
        private set

    /** Every set/clear recorded by [postSpoolId], in order (for cadence assertions). */
    val postedSpoolIds: MutableList<Int?> = mutableListOf()

    /**
     * The `server.spoolman.status` RESULT object — VERBATIM from the live status-before-set golden:
     * `{"spoolman_connected":true,"pending_reports":[],"spool_id":5}`. Returned as the RPC reply's
     * `result` object (the same shape the production status read walks).
     */
    fun status(): JsonObject =
        MoonrakerJson.parseToJsonElement(GoldenFixtures.raw("spoolman-live-ender5-status-before-set.json"))
            .jsonObject["result"]!!
            .jsonObject

    /** The `server.spoolman.get_spool_id` RESULT object: `{spool_id: <current-or-null>}`. */
    fun getSpoolId(): JsonObject = buildJsonObject {
        put("spool_id", MoonrakerJson.parseToJsonElement(activeSpoolId?.toString() ?: "null"))
    }

    /**
     * `server.spoolman.post_spool_id`. A params object carrying `spool_id` SETS the active spool; an
     * EMPTY params object (`{}`) CLEARS it to null (D-13 — the real `post_spool_id {}` clear contract).
     * Records the result in [postedSpoolIds] and returns the resulting `{spool_id: <new-or-null>}`.
     */
    fun postSpoolId(params: JsonObject): JsonObject {
        val newId = params["spool_id"]?.jsonPrimitive?.int
        activeSpoolId = newId
        postedSpoolIds += newId
        return getSpoolId()
    }

    /**
     * A `notify_active_spool_set` push frame whose `params` is a 1-ELEMENT array carrying
     * `{spool_id: <id-or-null>}` (matches `spoolman-live-ender5-notify.json`). Pass null to model a
     * cleared active spool.
     */
    fun injectActiveSpoolSet(spoolId: Int?): JsonObject = buildJsonObject {
        put("jsonrpc", MoonrakerJson.parseToJsonElement("\"2.0\""))
        put("method", MoonrakerJson.parseToJsonElement("\"notify_active_spool_set\""))
        put(
            "params",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("spool_id", MoonrakerJson.parseToJsonElement(spoolId?.toString() ?: "null"))
                    },
                )
            },
        )
    }

    /**
     * A `notify_spoolman_status_changed` push frame whose `params` is a 1-ELEMENT array carrying
     * `{spoolman_connected: <bool>}` (per the live notify shape in docs/view_specific_notes/spoolman.md).
     */
    fun injectSpoolmanStatusChanged(connected: Boolean): JsonObject = buildJsonObject {
        put("jsonrpc", MoonrakerJson.parseToJsonElement("\"2.0\""))
        put("method", MoonrakerJson.parseToJsonElement("\"notify_spoolman_status_changed\""))
        put(
            "params",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("spoolman_connected", MoonrakerJson.parseToJsonElement(connected.toString()))
                    },
                )
            },
        )
    }
}
