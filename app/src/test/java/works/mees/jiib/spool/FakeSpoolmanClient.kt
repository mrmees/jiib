package works.mees.jiib.spool

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import works.mees.jiib.net.GoldenFixtures
import works.mees.jiib.net.MoonrakerJson

/**
 * Hardened test double for the (not-yet-built) Spoolman proxy client. Wave 0: this fake defines its OWN
 * test-facing read surface (no reference to an unbuilt production `SpoolmanClient`) so it compiles
 * standalone; Waves 1-2 conform the real type to this contract.
 *
 * MOCK-VS-REALITY DISCIPLINE (7 prior strikes — see CONTEXT D / SessionTestHarness): every read returns
 * the EXACT Moonraker `server.spoolman.proxy` v2 envelope the live server emits —
 * `{response: <rows-or-object>, error: null, response_headers: {"X-Total-Count": "<n>"}}` — lifted
 * VERBATIM from the golden captures (`spoolman-live-ender5-proxy-*.json`). The envelope is the `result`
 * object of the JSON-RPC reply (see `spoolman-live-ender5-proxy-pla.json` →
 * `{"result":{"response":[...],"response_headers":{...,"X-Total-Count":"7"},"error":null}}`).
 *
 * Hardening (the harness's own rule, modelled on [works.mees.jiib.net.SessionTestHarness]): a query
 * that does NOT match a seeded fixture returns the WELL-FORMED EMPTY envelope ([emptyEnvelope],
 * `X-Total-Count == "0"`, `response == []`), never a lenient catch-all that returns a default row set.
 * A parser that ignores the query string therefore FAILS the test instead of silently passing.
 *
 * All JSON is parsed with the shared [MoonrakerJson] (never a fresh `Json {}`), matching the production
 * inbound posture.
 */
class FakeSpoolmanClient {

    /**
     * Logical request → seeded golden fixture. The map keys are the canonical `(method, path, query)`
     * shapes the real proxy client will issue; the values are the golden whose `result` envelope is
     * returned VERBATIM. Unmatched requests fall through to [emptyEnvelope] (the hardened miss path).
     */
    private val seeded: Map<ProxyKey, String> = mapOf(
        // GET /v1/spool?allow_archived=false&limit=5 → the PLA list capture (X-Total-Count "7").
        ProxyKey("GET", "/v1/spool", "allow_archived=false&limit=5") to "spoolman-live-ender5-proxy-pla.json",
        // GET /v1/spool/5 → the single-spool detail object capture.
        ProxyKey("GET", "/v1/spool/5", null) to "spoolman-live-ender5-proxy-spool3.json",
        ProxyKey("GET", "/v1/spool/3", null) to "spoolman-live-ender5-proxy-spool3.json",
        // GET /v1/material → the materials string-array capture.
        ProxyKey("GET", "/v1/material", null) to "spoolman-live-ender5-proxy-materials.json",
        // GET /v1/vendor → the vendor list capture (X-Total-Count "6").
        ProxyKey("GET", "/v1/vendor", null) to "spoolman-live-ender5-proxy-vendors.json",
        // GET /v1/location → the location string-array capture.
        ProxyKey("GET", "/v1/location", null) to "spoolman-live-ender5-proxy-locations.json",
        // GET /v1/filament?filament.material=ABS → the ABS/ASA spool list capture.
        ProxyKey("GET", "/v1/spool", "filament.material=ABS") to "spoolman-live-ender5-proxy-abs-asa.json",
        // GET /v1/filament?limit=1000 → the filament list capture (client-side color-family classify).
        ProxyKey("GET", "/v1/filament", "limit=1000") to "spoolman-live-ender5-proxy-color-red-filaments.json",
    )

    /** GET /v1/spool/{id} → the detail envelope, or the empty envelope when not seeded. */
    fun getSpool(id: Int): JsonElement = envelopeFor(ProxyKey("GET", "/v1/spool/$id", null))

    /** GET /v1/spool?{query} → the list envelope (X-Total-Count from the seeded golden). */
    fun listSpools(query: String? = "allow_archived=false&limit=5"): JsonElement =
        envelopeFor(ProxyKey("GET", "/v1/spool", query))

    /** GET /v1/filament?{query} → the filament list envelope. */
    fun listFilaments(query: String? = null): JsonElement =
        envelopeFor(ProxyKey("GET", "/v1/filament", query))

    /** GET /v1/material → the materials string-array envelope. */
    fun listMaterials(): JsonElement = envelopeFor(ProxyKey("GET", "/v1/material", null))

    /** GET /v1/vendor → the vendor list envelope. */
    fun listVendors(): JsonElement = envelopeFor(ProxyKey("GET", "/v1/vendor", null))

    /** GET /v1/location → the location string-array envelope. */
    fun listLocations(): JsonElement = envelopeFor(ProxyKey("GET", "/v1/location", null))

    /**
     * PUT /v1/spool/{id}/measure (D-04 measured gross weight). Wave-0 the fake echoes the detail
     * envelope for the spool, or the empty envelope when not seeded — enough for a parser seam to
     * resolve. The real measure round-trip is exercised in Waves 1-2.
     */
    fun measureSpool(id: Int, grossGrams: Double): JsonElement = getSpool(id)

    /** Resolve a request to its golden `result` envelope, or the hardened empty envelope on a miss. */
    private fun envelopeFor(key: ProxyKey): JsonElement {
        val golden = seeded[key] ?: return emptyEnvelope()
        // The golden's top-level `result` IS the proxy-v2 envelope ({response, response_headers, error}).
        return MoonrakerJson.parseToJsonElement(GoldenFixtures.raw(golden))
            .jsonObject["result"]
            ?: emptyEnvelope()
    }

    /**
     * The well-formed EMPTY proxy-v2 envelope returned for any unmatched request: an empty `response`
     * array, `error: null`, and `X-Total-Count: "0"`. This is the hardened miss path — a parser that
     * ignores the query and assumes data fails against this, never silently passes.
     */
    private fun emptyEnvelope(): JsonObject = buildJsonObject {
        put("response", MoonrakerJson.parseToJsonElement("[]"))
        put("error", MoonrakerJson.parseToJsonElement("null"))
        put("response_headers", buildJsonObject { put("X-Total-Count", MoonrakerJson.parseToJsonElement("\"0\"")) })
    }

    /** A logical proxy request identity: HTTP method + path + (optional) query string. */
    private data class ProxyKey(val method: String, val path: String, val query: String?)
}
