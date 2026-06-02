package works.mees.dinghy.ui.console

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure `server.gcode_store` JSON → [List]<[ConsoleLine]> walker (CONS-02 / D-02 backfill).
 *
 * This is the project's pure-function discipline (mirrors `ConsoleSeverity.classify` and the handshake's
 * null-safe JSON walkers in `MoonrakerSession`): NO I/O, NO coroutines, NO Compose — same input always
 * yields the same output, so it is fully host-testable off-hardware (`GcodeStoreParseTest`).
 *
 * Shape (probed 2026-06-02 against the real Ender 5 Plus, committed as `/fixtures/gcode_store_e5.json`):
 * `{ "gcode_store": [ { "message": String, "time": Double, "type": "command"|"response" } ] }`.
 *
 * House rule (T-08-04-T, the "bad field skipped, never fatal" discipline): the whole walk is wrapped in a
 * [runCatching] that degrades to an EMPTY list on any malformed top-level shape, and each entry is walked
 * via [mapNotNull] so a single entry missing `message` is SKIPPED rather than collapsing the snapshot.
 * A failed/garbage read therefore leaves the backfill seam at its empty default and never breaks Connected.
 *
 * D-04 (LOAD-BEARING): [ConsoleLine.rawMessage] keeps the ORIGINAL Klipper prefix intact; severity is
 * derived ONCE from that raw prefix via [ConsoleSeverity.classify] (the display layer decides strip-vs-keep).
 */
fun parseGcodeStore(result: JsonObject): List<ConsoleLine> = runCatching {
    (result["gcode_store"] as? JsonArray).orEmpty().mapNotNull { el ->
        val entry = el as? JsonObject ?: return@mapNotNull null
        val message = entry["message"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val time = entry["time"]?.jsonPrimitive?.doubleOrNull
        ConsoleLine(
            rawMessage = message,
            severity = ConsoleSeverity.classify(message),
            timeEpoch = time,
        )
    }
}.getOrDefault(emptyList())
