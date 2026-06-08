package works.mees.dinghy.systeminfo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Pure tolerant parsers for the System Information page (SYS-01..04). Mirrors the project's
 * pure-parser discipline ([works.mees.dinghy.calibration.BedMeshModel.from]): NO I/O, NO coroutines,
 * NO Compose; a `runCatching`/null-returning JsonObject walk, NEVER `!!`, NEVER schema-strict
 * `@Serializable` on the loose/heterogeneous Moonraker payloads. Any malformed/sparse payload
 * degrades to a model full of nulls — it NEVER throws (the DegradeTest gate + T-20-02-D mitigation).
 */

/**
 * Walk `machine.system_info.result.system_info` → [SystemInfo]. Accepts the object that CONTAINS the
 * `system_info` key (i.e. `.result`); a malformed/absent shape degrades to an all-null model.
 *
 * `cpu_info.{model,cpu_desc,processor,cpu_count,total_memory}` + `distribution.{name,version,
 * kernel_version}`. The empty string "" is treated as missing (→ null), so the RockPro64's blank
 * `model` degrades to "—" (Pitfall 4). `kernel` is read from `distribution.kernel_version`, NOT a
 * top-level key (Pitfall 3).
 */
fun SystemInfo.Companion.from(result: JsonObject?): SystemInfo = runCatching {
    val sys = result?.objectOrNull("system_info") ?: return@runCatching SystemInfo()
    val cpu = sys.objectOrNull("cpu_info")
    val distro = sys.objectOrNull("distribution")
    SystemInfo(
        model = cpu?.blankStringOrNull("model"),
        cpuDesc = cpu?.blankStringOrNull("cpu_desc"),
        processor = cpu?.blankStringOrNull("processor"),
        cpuCount = cpu?.intOrNullAt("cpu_count"),
        totalMemoryKb = cpu?.longOrNullAt("total_memory"),
        distroName = distro?.blankStringOrNull("name"),
        distroVersion = distro?.blankStringOrNull("version"),
        kernel = distro?.blankStringOrNull("kernel_version"),
    )
}.getOrDefault(SystemInfo())

/**
 * Walk a `notify_proc_stat_update` push frame (params[0]) → [ProcStatLive].
 *
 * MUST tolerate the absence of `throttled_state` and `system_uptime` — the push genuinely omits both
 * (Pitfall 1); they are sourced from [ProcStatQuery.from] instead. `cpu_temp` + the aggregate
 * `system_cpu_usage.cpu` + `system_memory.{used,total,available}`.
 */
fun ProcStatLive.Companion.fromPush(push: JsonObject?): ProcStatLive = runCatching {
    if (push == null) return@runCatching ProcStatLive()
    val mem = push.objectOrNull("system_memory")
    ProcStatLive(
        cpuTemp = push.floatOrNullAt("cpu_temp"),
        cpuLoadPercent = push.objectOrNull("system_cpu_usage")?.floatOrNullAt("cpu"),
        memUsedKb = mem?.longOrNullAt("used"),
        memTotalKb = mem?.longOrNullAt("total"),
        memAvailableKb = mem?.longOrNullAt("available"),
    )
}.getOrDefault(ProcStatLive())

/**
 * Walk `machine.proc_stats.result` → [ProcStatQuery]. The ONLY source of `throttled_state` +
 * `system_uptime`.
 *
 * `throttled_state` is an object on the Pi (`{bits, flags}`), explicit JSON `null` on the RockPro64
 * (→ [ProcStatQuery.throttledState] null, no crash — Pitfall 2), or absent on a sparse Moonraker
 * (→ null).
 */
fun ProcStatQuery.Companion.from(result: JsonObject?): ProcStatQuery = runCatching {
    if (result == null) return@runCatching ProcStatQuery()
    ProcStatQuery(
        throttledState = result.throttledStateOrNull(),
        cpuTemp = result.floatOrNullAt("cpu_temp"),
        systemUptimeSeconds = result.doubleOrNullAt("system_uptime"),
    )
}.getOrDefault(ProcStatQuery())

// ---- tolerant JsonObject walk helpers (local, mirroring the calibration/state parsers) ----

private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

/** String value, with the empty string "" treated as missing (→ null) — the blank-as-missing trap. */
private fun JsonObject.blankStringOrNull(key: String): String? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    if (!primitive.isString) return null
    return primitive.content.takeIf { it.isNotBlank() }
}

private fun JsonObject.intOrNullAt(key: String): Int? =
    runCatching { (this[key] as? JsonPrimitive)?.intOrNull }.getOrNull()

private fun JsonObject.longOrNullAt(key: String): Long? =
    runCatching { (this[key] as? JsonPrimitive)?.longOrNull }.getOrNull()

private fun JsonObject.floatOrNullAt(key: String): Float? =
    runCatching { (this[key] as? JsonPrimitive)?.floatOrNull }.getOrNull()

private fun JsonObject.doubleOrNullAt(key: String): Double? =
    runCatching { (this[key] as? JsonPrimitive)?.doubleOrNull }.getOrNull()

/**
 * `throttled_state` → [ThrottledState], tolerant of JsonNull / absent (→ null = "no throttle data").
 * `bits` is read as an int; `flags` as a list of strings.
 */
private fun JsonObject.throttledStateOrNull(): ThrottledState? {
    val el = this["throttled_state"]
    if (el == null || el is JsonNull) return null
    val obj = el as? JsonObject ?: return null
    val bits = obj.intOrNullAt("bits") ?: 0
    val flags = (obj["flags"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content }
        ?: emptyList()
    return ThrottledState(bits = bits, flags = flags)
}
