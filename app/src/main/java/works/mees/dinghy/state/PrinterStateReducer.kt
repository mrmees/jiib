package works.mees.dinghy.state

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import works.mees.dinghy.net.JsonRpcMethods

/**
 * The PURE state layer (STATE-01 / STATE-04). Two free functions — [reduceSnapshot] and [reduceDiff] —
 * turn Moonraker's loose status JSON into the immutable [PrinterState]. Plus [applyKlippyMethod] folds a
 * `notify_klippy_*` method name into the first-class [PrinterState.klippyState].
 *
 * House rule (T-02-03 mitigation, ASVS V5): inbound wire JSON is untrusted-ish — every walk is null-safe
 * (`?.`/`orNull`), NEVER `!!`. A missing/garbage field is SKIPPED (the retained value is kept), never fatal.
 *
 * Purity (the whole point — RESEARCH "the risk is getting the diff-merge semantics exactly right"):
 *  - no `CoroutineScope`, socket, `Context`, or any I/O — same input always yields the same output;
 *  - so Wave-3 (02-04) re-runs these deterministically on every reconnect and the golden corpus proves
 *    merge/lifecycle semantics with no live hardware.
 *
 * Merge-not-replace (Pitfall 1, STATE-01): [reduceDiff] deep-merges a `notify_status_update` partial diff
 * field-by-field onto the RETAINED state. A temp-only `extruder` diff updates the temperature and KEEPS the
 * previously-set target; it does not blank un-diffed fields.
 */

/**
 * Seed a full [PrinterState] from an `objects.query` / `objects.subscribe` `status` object
 * (the `result.status` of a query reply, or the first element of a subscribe reply). Reads each
 * v1-subscribed object defensively; absent objects leave their defaults.
 */
fun reduceSnapshot(status: JsonObject): PrinterState =
    applyStatus(PrinterState(), status)

/**
 * Deep-merge a `notify_status_update` partial diff (the `[0]` "changed objects" object of the params
 * array) onto [current], producing the next [PrinterState]. Field-by-field merge — un-mentioned fields
 * are retained (Pitfall 1). Identical mechanics to the seed, only the base differs (retained vs default).
 */
fun reduceDiff(current: PrinterState, diff: JsonObject): PrinterState =
    applyStatus(current, diff)

/**
 * Fold a `notify_klippy_*` method name into the next [KlippyState] (STATE-04). These notifications carry
 * NO params, so the method name alone drives the transition. Last-known values are RETAINED (D-03 retain,
 * distinct from blanking) — only [PrinterState.klippyState] changes. Unknown methods leave state untouched.
 */
fun applyKlippyMethod(current: PrinterState, method: String): PrinterState {
    val next = when (method) {
        JsonRpcMethods.NOTIFY_KLIPPY_READY -> KlippyState.Ready
        JsonRpcMethods.NOTIFY_KLIPPY_SHUTDOWN -> KlippyState.Shutdown
        JsonRpcMethods.NOTIFY_KLIPPY_DISCONNECTED -> KlippyState.Disconnected
        else -> return current
    }
    return current.copy(klippyState = next)
}

// ---------------------------------------------------------------------------------------------------------
// Internals — a single status-walker shared by seed and diff so merge semantics are identical (only the
// base state differs). Every accessor below is null-safe: a missing object/field falls through to `current`.
// ---------------------------------------------------------------------------------------------------------

private fun applyStatus(current: PrinterState, status: JsonObject): PrinterState {
    var s = current

    status.objectOrNull("webhooks")?.stringOrNull("state")?.let { s = s.copy(klippyState = klippyFromWebhook(it)) }

    status.objectOrNull("print_stats")?.let { ps ->
        ps.stringOrNull("state")?.let { s = s.copy(printState = printStateFrom(it)) }
        ps.stringOrNull("filename")?.let { s = s.copy(printFilename = it) }
    }

    status.objectOrNull("toolhead")?.let { th ->
        th.stringOrNull("homed_axes")?.let { s = s.copy(homedAxes = it) }
        th.doubleListOrNull("position")?.let { s = s.copy(toolheadPosition = it) }
    }

    status.objectOrNull("gcode_move")?.let { gm ->
        gm.doubleOrNullAt("speed_factor")?.let { s = s.copy(speedFactor = it) }
        gm.doubleOrNullAt("extrude_factor")?.let { s = s.copy(extrudeFactor = it) }
    }

    // Progress can arrive on either virtual_sdcard or display_status; last writer wins per frame.
    status.objectOrNull("virtual_sdcard")?.doubleOrNullAt("progress")?.let { s = s.copy(progress = it) }
    status.objectOrNull("display_status")?.doubleOrNullAt("progress")?.let { s = s.copy(progress = it) }

    // Heaters: merge each present heater object field-by-field onto the retained HeaterState.
    val heaterUpdates = mutableMapOf<String, HeaterState>()
    for ((key, value) in status) {
        if (!isHeaterObject(key)) continue
        val obj = (value as? JsonObject) ?: continue
        val prev = s.heaters[key] ?: HeaterState()
        heaterUpdates[key] = prev.copy(
            temperature = obj.doubleOrNullAt("temperature") ?: prev.temperature,
            target = obj.doubleOrNullAt("target") ?: prev.target,
            power = obj.doubleOrNullAt("power") ?: prev.power,
        )
    }
    if (heaterUpdates.isNotEmpty()) {
        s = s.copy(heaters = s.heaters + heaterUpdates)
    }

    return s
}

private fun isHeaterObject(name: String): Boolean =
    name == "heater_bed" || name == "extruder" || name.matches(EXTRUDER_N) || name.startsWith("heater_generic ")

private val EXTRUDER_N = Regex("""extruder\d+""")

private fun klippyFromWebhook(state: String): KlippyState = when (state) {
    "ready" -> KlippyState.Ready
    "startup" -> KlippyState.Startup
    "shutdown" -> KlippyState.Shutdown
    "error" -> KlippyState.Error
    "disconnected" -> KlippyState.Disconnected
    else -> KlippyState.Disconnected
}

private fun printStateFrom(state: String): PrintState = when (state) {
    "standby" -> PrintState.Standby
    "printing" -> PrintState.Printing
    "paused" -> PrintState.Paused
    "complete" -> PrintState.Complete
    "error" -> PrintState.Error
    "cancelled" -> PrintState.Cancelled
    else -> PrintState.Standby
}

// --- null-safe JsonObject accessors (never `!!` on wire data; bad field -> null -> skipped) ---

private fun JsonObject.objectOrNull(key: String): JsonObject? = (this[key] as? JsonObject)

private fun JsonObject.stringOrNull(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()

private fun JsonObject.doubleOrNullAt(key: String): Double? =
    runCatching { this[key]?.jsonPrimitive?.doubleOrNull }.getOrNull()

private fun JsonObject.doubleListOrNull(key: String): List<Double>? =
    runCatching { (this[key] as? JsonArray)?.map { it.jsonPrimitive.double } }.getOrNull()
