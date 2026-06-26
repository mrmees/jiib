package works.mees.jiib.spool

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import works.mees.jiib.net.MoonrakerJson

/**
 * PURE, headless parsers for Moonraker's Spoolman proxy-v2 envelope (SPOOL-02/08; D-07/D-08).
 *
 * The Moonraker `server.spoolman.proxy` reply (with `use_v2_response=true`) wraps the real Spoolman
 * payload in `{ response, error, response_headers }`:
 *  - `error: null` means **SUCCESS** (the payload is in `response`) — NOT "no data" (RESEARCH
 *    anti-pattern; mis-reading this is T-11-02-03).
 *  - `X-Total-Count` (total inventory count, possibly > the paginated page size) lives in
 *    `response_headers` — it is absent on non-paginated endpoints (materials/locations), so it is
 *    nullable.
 *  - `response` is the Spoolman body: an array of rows (spools/filaments/vendors), an array of plain
 *    strings (materials/locations), or a single object (a `get_spool` detail).
 *
 * Every walk is null-safe (`runCatching`/`as?`, never `!!`); a malformed envelope yields an empty
 * result, never throws (T-11-02-02). One un-decodable row drops via `mapNotNull`; the rest survive.
 * All JSON goes through the shared [MoonrakerJson] — never a fresh `Json {}`.
 */

/**
 * The parsed proxy-v2 envelope: the decoded [rows], the [totalCount] from `X-Total-Count` (null when
 * the endpoint doesn't paginate), and [success] (true when `error` is null/absent AND the envelope
 * was well-formed).
 */
data class ProxyEnvelope<T>(
    val rows: List<T>,
    val totalCount: Int? = null,
    val success: Boolean = false,
)

/** The raw envelope parts after the structural walk, before per-row typed decode. */
private data class RawEnvelope(
    val response: JsonElement?,
    val totalCount: Int?,
    val success: Boolean,
)

private fun walkEnvelope(result: JsonElement?): RawEnvelope? {
    val obj = result as? JsonObject ?: return null
    // error:null (or absent) == success. A present non-null error is a failure.
    val error = obj["error"]
    val success = error == null || error is JsonNull
    val headers = obj["response_headers"] as? JsonObject
    val totalCount = runCatching {
        (headers?.get("X-Total-Count") as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    }.getOrNull()
    return RawEnvelope(response = obj["response"], totalCount = totalCount, success = success)
}

/**
 * Generic typed envelope decode: walk `{response, error, response_headers}`, treat `error:null` as
 * success, pull `X-Total-Count`, and `mapNotNull` each `response[]` row via [strategy]. A malformed
 * envelope or a non-array `response` yields an empty, unsuccessful [ProxyEnvelope]; never throws.
 */
fun <T> parseProxyEnvelope(result: JsonElement?, strategy: DeserializationStrategy<T>): ProxyEnvelope<T> {
    val raw = walkEnvelope(result) ?: return ProxyEnvelope(emptyList())
    val array = raw.response as? JsonArray
        ?: return ProxyEnvelope(emptyList(), totalCount = raw.totalCount, success = raw.success)
    val rows = array.mapNotNull { entry ->
        runCatching { MoonrakerJson.decodeFromJsonElement(strategy, entry) }.getOrNull()
    }
    return ProxyEnvelope(rows = rows, totalCount = raw.totalCount, success = raw.success)
}

/** Spool list/detail rows from a proxy-v2 envelope (paginated → [ProxyEnvelope.totalCount] set). */
fun parseSpoolmanSpools(result: JsonElement?): ProxyEnvelope<SpoolmanSpool> =
    parseProxyEnvelope(result, SpoolmanSpool.serializer())

/**
 * Decode the SINGLE-spool DETAIL from a `/v1/spool/{id}` proxy-v2 envelope. Unlike a list endpoint the
 * `response` here is a lone object (not an array), so [parseSpoolmanSpools] sees no array and returns
 * empty — this walk pulls the object directly. Best-effort: a malformed/absent envelope or (when
 * [expectedId] is non-null) an id mismatch yields null, never throws (T-11-06-01 / T-11-07 confirm-card).
 *
 * @param expectedId when non-null, the decoded spool's `id` MUST match it (guards against a stale/echoed
 *        envelope resolving the wrong spool into a confirm card); pass null to accept any well-formed id.
 */
fun parseSpoolmanSpoolDetail(result: JsonElement?, expectedId: Int? = null): SpoolmanSpool? {
    val obj = result as? JsonObject ?: return null
    val response = obj["response"] as? JsonObject ?: return null
    val spool = runCatching {
        MoonrakerJson.decodeFromJsonElement(SpoolmanSpool.serializer(), response)
    }.getOrNull() ?: return null
    return if (expectedId == null || spool.id == expectedId) spool else null
}

/** Filament rows (e.g. the color-red-filaments / filament-search goldens). */
fun parseSpoolmanFilaments(result: JsonElement?): ProxyEnvelope<SpoolmanFilament> =
    parseProxyEnvelope(result, SpoolmanFilament.serializer())

/** Vendor rows (the vendors filter golden). */
fun parseSpoolmanVendors(result: JsonElement?): ProxyEnvelope<SpoolmanVendor> =
    parseProxyEnvelope(result, SpoolmanVendor.serializer())

/**
 * Plain-string rows (materials / locations endpoints — `response` is `["PLA+ 2.0", …]`). Non-string
 * entries drop; a malformed envelope yields an empty list, never throws.
 */
fun parseSpoolmanStringList(result: JsonElement?): ProxyEnvelope<String> {
    val raw = walkEnvelope(result) ?: return ProxyEnvelope(emptyList())
    val array = raw.response as? JsonArray
        ?: return ProxyEnvelope(emptyList(), totalCount = raw.totalCount, success = raw.success)
    val rows = array.mapNotNull { runCatching { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.getOrNull() }
    return ProxyEnvelope(rows = rows, totalCount = raw.totalCount, success = raw.success)
}

/** Alias: Spoolman material families (`parseProxyEnvelope` over the materials golden). */
fun parseSpoolmanMaterials(result: JsonElement?): ProxyEnvelope<String> = parseSpoolmanStringList(result)

/** Alias: Spoolman storage locations (`parseProxyEnvelope` over the locations golden). */
fun parseSpoolmanLocations(result: JsonElement?): ProxyEnvelope<String> = parseSpoolmanStringList(result)

/**
 * Hand-walk the `server.spoolman.status` RPC reply `result` object into a [SpoolmanStatus]. Unlike
 * the proxy endpoints this is NOT a proxy-v2 envelope — it's the bare status object
 * `{spoolman_connected, spool_id, pending_reports}`. Field-by-field `runCatching` walk so a missing
 * field degrades to its default; never throws. `error:null` semantics do not apply here.
 */
fun parseSpoolmanStatus(result: JsonElement?): SpoolmanStatus {
    val obj = result as? JsonObject ?: return SpoolmanStatus()
    val connected = runCatching { obj["spoolman_connected"]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: false
    val spoolId = runCatching { obj["spool_id"]?.jsonPrimitive?.intOrNull }.getOrNull()
    val pending = runCatching {
        (obj["pending_reports"] as? JsonArray)?.mapNotNull { entry ->
            runCatching { MoonrakerJson.decodeFromJsonElement(PendingSpoolmanReport.serializer(), entry) }.getOrNull()
        }
    }.getOrNull().orEmpty()
    return SpoolmanStatus(spoolmanConnected = connected, activeSpoolId = spoolId, pendingReports = pending)
}
