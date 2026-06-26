package works.mees.jiib.spool

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.SpoolmanProxyArgs
import works.mees.jiib.command.request
import works.mees.jiib.net.JsonRpcClient
import java.net.URLEncoder

/**
 * The lean Spoolman INVENTORY reader (SPOOL-01; D-07). Mirrors
 * [works.mees.jiib.ui.files.FileBrowserClient]'s "interface of suspend reads returning
 * [JsonElement]?, each `runCatching{rpc.request(spec, args)}.getOrNull()`" shape exactly.
 *
 * ## Two transports, deliberately split (D-07)
 * Active-spool STATE (status/get/set/clear) is Moonraker-owned and routes through the existing JSON-RPC
 * session via [works.mees.jiib.spool.ActiveSpoolFacade]. INVENTORY is Spoolman-owned and rides the
 * `server.spoolman.proxy` (use_v2_response=true) passthrough — THIS client. It wraps the SAME session
 * [JsonRpcClient] (one connection/TLS stack, CLAUDE.md networking law) but is NOT forced through the
 * [works.mees.jiib.command.CommandDispatcher]: these are reads, not fire-and-forget actions, and the
 * caller wants the raw proxy-v2 envelope back to parse (D-07/Discretion).
 *
 * ## Best-effort, never throws
 * Each read returns the raw proxy-v2 envelope [JsonElement] (`{response, error, response_headers}`) or
 * null on any failure — `runCatching{...}.getOrNull()`. The [SpoolmanParsers] walk the envelope; a
 * rejected/absent read degrades to a null the parser turns into an empty result, never an exception.
 *
 * ## D-07/Pitfall 5: dotted query keys are URL-encoded HERE
 * Spoolman filter keys are dotted (`filament.material`, `filament.vendor.name`). They must be
 * percent-encoded BEFORE they reach the proxy `query` string or Moonraker mis-routes the passthrough.
 * The encode lives in THIS layer ([encodeQuery]) so callers pass plain `key=value&key=value` pairs.
 */
interface SpoolmanClient {
    /** GET /v1/spool/{id} → the single-spool detail envelope, or null on failure. */
    suspend fun getSpool(id: Int): JsonElement? = null

    /** GET /v1/spool?{query} → the spool list envelope (X-Total-Count in the headers), or null. */
    suspend fun listSpools(query: String? = null): JsonElement? = null

    /** GET /v1/filament?{query} → the filament list envelope, or null. */
    suspend fun listFilaments(query: String? = null): JsonElement? = null

    /** GET /v1/material → the materials string-array envelope, or null. */
    suspend fun listMaterials(): JsonElement? = null

    /** GET /v1/vendor → the vendor list envelope, or null. */
    suspend fun listVendors(): JsonElement? = null

    /** GET /v1/location → the location string-array envelope, or null. */
    suspend fun listLocations(): JsonElement? = null

    /** PUT /v1/spool/{id}/measure (D-04) → the updated detail envelope, or null. */
    suspend fun measureSpool(id: Int, grossGrams: Double): JsonElement? = null
}

/**
 * The production [SpoolmanClient] over the session [JsonRpcClient]. Each method builds a
 * [SpoolmanProxyArgs] and issues `server.spoolman.proxy` (use_v2_response=true), returning the raw
 * proxy-v2 envelope or null. Lean — no CommandDispatcher (D-07/Discretion).
 */
class MoonrakerSpoolmanClient(private val rpc: JsonRpcClient) : SpoolmanClient {

    override suspend fun getSpool(id: Int): JsonElement? =
        proxy("GET", "/v1/spool/$id")

    override suspend fun listSpools(query: String?): JsonElement? =
        proxy("GET", "/v1/spool", query)

    override suspend fun listFilaments(query: String?): JsonElement? =
        proxy("GET", "/v1/filament", query)

    override suspend fun listMaterials(): JsonElement? =
        proxy("GET", "/v1/material")

    override suspend fun listVendors(): JsonElement? =
        proxy("GET", "/v1/vendor")

    override suspend fun listLocations(): JsonElement? =
        proxy("GET", "/v1/location")

    /**
     * D-04 measured gross weight: PUT /v1/spool/{id}/measure carrying the gross grams in the request body
     * (the Moonraker proxy forwards the body to Spoolman). `remaining`/`used` are server-recomputed
     * from `gross − spool_weight`; the caller re-reads the spool to reflect the update.
     */
    override suspend fun measureSpool(id: Int, grossGrams: Double): JsonElement? =
        proxy("PUT", "/v1/spool/$id/measure", body = buildJsonObject { put("weight", grossGrams) })

    /**
     * Issue one proxy-v2 read. [query] is the plain `key=value&key=value` string; dotted keys are
     * URL-encoded here (D-07/Pitfall 5) before the proxy sees them. Best-effort — any failure → null.
     */
    private suspend fun proxy(method: String, path: String, query: String? = null, body: JsonObject? = null): JsonElement? =
        runCatching {
            rpc.request(
                CommandRegistry.spoolmanProxy,
                SpoolmanProxyArgs(method = method, path = path, query = encodeQuery(query), body = body),
            )
        }.getOrNull()

    /**
     * Percent-encode each `key=value` pair of a query string (D-07/Pitfall 5) so dotted Spoolman
     * filter keys (`filament.material`) survive the proxy passthrough. A null/blank query stays null.
     * Encoding is idempotent-safe for already-plain ASCII keys/values; uses UTF-8 and keeps the `=`/`&`
     * separators literal.
     */
    private fun encodeQuery(query: String?): String? {
        if (query.isNullOrBlank()) return null
        return query.split('&').joinToString("&") { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) {
                URLEncoder.encode(pair, "UTF-8")
            } else {
                val key = URLEncoder.encode(pair.substring(0, idx), "UTF-8")
                val value = URLEncoder.encode(pair.substring(idx + 1), "UTF-8")
                "$key=$value"
            }
        }
    }
}
