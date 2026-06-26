package works.mees.jiib.state

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pull the trimmed `hostname` out of a Moonraker `printer.info` RESULT object (the object the rpc
 * layer returns — already unwrapped from the JSON-RPC envelope, like `machine.system_info`). A
 * non-string primitive (number/bool) is rejected so it can never become a printer name (the
 * `SystemInfoParse` `isString` convention). Blank or missing → null. Fail-safe (`runCatching`) so a
 * malformed payload never crashes the handshake.
 */
fun parsePrinterInfoHostname(result: JsonObject?): String? = runCatching {
    val prim = result?.get("hostname")?.jsonPrimitive ?: return@runCatching null
    if (!prim.isString) return@runCatching null
    prim.content.trim().ifBlank { null }
}.getOrNull()
