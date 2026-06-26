package works.mees.jiib.state

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * PURE temperature-history backfill mapper (TEMP-04, D-05) — the FIRST use of a Moonraker history
 * endpoint. Turns a `server.temperature_store` reply into per-sensor float series, host-testable with no
 * I/O, no coroutines, no socket — mirroring the [deriveCapabilities] / [reduceSnapshot] pure-module shape
 * so the Wave-3 handshake (05-03 Task 2) calls it deterministically and off-hardware tests prove order,
 * selection, and absence.
 *
 * House rule (T-05-03-T mitigation, ASVS V5): the store JSON is untrusted-ish — every walk is null-safe
 * (`?.`/`orNull`), NEVER `!!`. A non-finite / non-numeric sample is SKIPPED (reducer discipline); a sensor
 * absent from the response (or carrying no `temperatures` array) is OMITTED from the map, never fabricated
 * as an empty array.
 */

/**
 * Map a `server.temperature_store` [result] (an object keyed by sensor object name, each value carrying a
 * `temperatures` FIFO array — **index 0 = OLDEST**, one sample/second) into per-sensor [FloatArray]s in the
 * SAME order received (kept oldest→newest, exactly the order the RingBuffer pushes).
 *
 * Only the requested [sensors] (the heater object names the graph actually draws) are mapped — pure
 * `temperature_sensor X` entries the store also returns but the graph does not draw are ignored
 * (sensor-name alignment, RESEARCH §1). A requested sensor that is absent from the response, or present but
 * missing/garbling its `temperatures` array, is omitted from the returned map.
 */
fun parseTemperatureStore(result: JsonObject, sensors: Set<String>): Map<String, FloatArray> {
    val out = LinkedHashMap<String, FloatArray>()
    for (name in sensors) {
        val sensorObj = result[name] as? JsonObject ?: continue
        val temps = sensorObj["temperatures"] as? JsonArray ?: continue
        // Per-element null-safe map: a non-numeric or non-finite sample is skipped, not fatal (matches the
        // reducer's "a bad field is skipped, never fatal" house rule at the collection level).
        val series = temps.mapNotNull { element ->
            val d = runCatching { element.jsonPrimitive.doubleOrNull }.getOrNull() ?: return@mapNotNull null
            if (d.isFinite()) d.toFloat() else null
        }
        out[name] = series.toFloatArray()
    }
    return out
}
