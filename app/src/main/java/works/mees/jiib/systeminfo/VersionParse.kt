package works.mees.jiib.systeminfo

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Pure tolerant parsers for Klipper + Moonraker version strings.
 *
 * Reads `software_version` from the `printer.info` result (passed as the raw [JsonElement] returned
 * by the JSON-RPC `printer.info` method), and `moonraker_version` from the `server.info` result.
 *
 * Follows the project's no-throw / null-degrade discipline: any absent, blank, or malformed field
 * degrades to null — never throws. Uses [blankStringOrNull] from [SystemInfoParse] (internal).
 */

/** Reads `software_version` from a `printer.info` result element. Returns null if absent/blank/malformed. */
fun parseKlipperVersion(printerInfoResult: JsonElement?): String? = runCatching {
    printerInfoResult?.jsonObject?.blankStringOrNull("software_version")
}.getOrNull()

/** Reads `moonraker_version` from a `server.info` result element. Returns null if absent/blank/malformed. */
fun parseMoonrakerVersion(serverInfoResult: JsonElement?): String? = runCatching {
    serverInfoResult?.jsonObject?.blankStringOrNull("moonraker_version")
}.getOrNull()
