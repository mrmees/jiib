package works.mees.dinghy.systeminfo

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Dynamic per-tick MCU stats (the refresh-poll subset). */
data class McuStats(
    val mcuAwake: Float? = null,
    val taskAvg: Float? = null,
    val taskStddev: Float? = null,
    val bytesWrite: Long? = null,
    val bytesRead: Long? = null,
    val bytesRetransmit: Long? = null,
)

/** Mainboard `mcu` first, then the rest alphabetically (stable Field order). */
fun mcuObjectNames(objects: Set<String>): List<String> =
    objects.filter { it == "mcu" || it.startsWith("mcu ") }
        .sortedWith(compareBy({ it != "mcu" }, { it.lowercase() }))

/** "mcu"→Mainboard, "mcu host"→Host MCU, "mcu <name>"→"<name>". */
fun mcuDisplayName(objectName: String): String = when {
    objectName == "mcu" -> "Mainboard"
    objectName == "mcu host" -> "Host MCU"
    objectName.startsWith("mcu ") -> objectName.removePrefix("mcu ").trim()
    else -> objectName
}

private fun statusOf(queryResult: JsonElement?): JsonObject? = runCatching {
    queryResult?.jsonObject?.objectOrNull("status")
}.getOrNull()

/** Full initial parse: version/constants/interface (static) + last_stats (dynamic). */
fun parseMcuDevices(mcuNames: List<String>, queryResult: JsonElement?): List<McuDevice> {
    val status = statusOf(queryResult)
    val settings = status?.objectOrNull("configfile")?.objectOrNull("settings")
    return mcuNames.map { name ->
        val obj = status?.objectOrNull(name)
        val constants = obj?.objectOrNull("mcu_constants")
        val stats = parseStats(obj)
        // configfile section names are LOWERCASED by Klipper — look up with the lowercased object name.
        val section = settings?.objectOrNull(name.lowercase())
        McuDevice(
            key = name,
            displayName = mcuDisplayName(name),
            firmwareVersion = obj?.blankStringOrNull("mcu_version"),
            chip = constants?.blankStringOrNull("MCU"),
            // Klipper's mcu_constant is CLOCK_FREQ; fall back to CLOCK defensively.
            clockHz = constants?.longOrNullAt("CLOCK_FREQ") ?: constants?.longOrNullAt("CLOCK"),
            interfaceDesc = interfaceDesc(section),
            mcuAwake = stats.mcuAwake,
            taskAvg = stats.taskAvg,
            taskStddev = stats.taskStddev,
            bytesWrite = stats.bytesWrite,
            bytesRead = stats.bytesRead,
            bytesRetransmit = stats.bytesRetransmit,
        )
    }
}

/** Refresh-poll subset: just last_stats per object. */
fun parseMcuStats(mcuNames: List<String>, queryResult: JsonElement?): Map<String, McuStats> {
    val status = statusOf(queryResult)
    return mcuNames.associateWith { parseStats(status?.objectOrNull(it)) }
}

/** Re-apply fresh stats onto the static detail (version/chip/interface preserved). */
fun mergeStats(existing: List<McuDevice>, stats: Map<String, McuStats>): List<McuDevice> =
    existing.map { d ->
        val s = stats[d.key] ?: return@map d
        d.copy(
            mcuAwake = s.mcuAwake, taskAvg = s.taskAvg, taskStddev = s.taskStddev,
            bytesWrite = s.bytesWrite, bytesRead = s.bytesRead, bytesRetransmit = s.bytesRetransmit,
        )
    }

private fun parseStats(obj: JsonObject?): McuStats {
    val ls = obj?.objectOrNull("last_stats") ?: return McuStats()
    return McuStats(
        mcuAwake = ls.floatOrNullAt("mcu_awake"),
        taskAvg = ls.floatOrNullAt("mcu_task_avg"),
        taskStddev = ls.floatOrNullAt("mcu_task_stddev"),
        bytesWrite = ls.longOrNullAt("bytes_write"),
        bytesRead = ls.longOrNullAt("bytes_read"),
        bytesRetransmit = ls.longOrNullAt("bytes_retransmit"),
    )
}

private fun interfaceDesc(section: JsonObject?): String? {
    if (section == null) return null
    section.blankStringOrNull("canbus_uuid")?.let { return "CAN $it" }
    section.blankStringOrNull("serial")?.let { return it }
    return null
}

/**
 * Decode the Raspberry Pi `throttled_state` bits into human conditions. Pi firmware bit map (NOT
 * Moonraker's docs table — it has a duplicated 1<<16): bit0 under-voltage now, bit1 freq-capped now,
 * bit2 throttled now, bit3 soft-temp-limit now; bit16/17/18/19 = the occurred-since-boot mirror
 * (soft-temp-limit occurred = bit19 = 0x80000). Null state (non-Pi) → empty.
 */
fun decodeThrottleConditions(state: ThrottledState?): List<String> {
    val bits = state?.bits ?: return emptyList()
    val out = mutableListOf<String>()
    if (bits and 0x1 != 0) out += "Under-voltage detected"
    if (bits and 0x2 != 0) out += "ARM frequency capped"
    if (bits and 0x4 != 0) out += "Currently throttled"
    if (bits and 0x8 != 0) out += "Soft temperature limit active"
    if (bits and 0x10000 != 0) out += "Under-voltage has occurred"
    if (bits and 0x20000 != 0) out += "Frequency capping has occurred"
    if (bits and 0x40000 != 0) out += "Throttling has occurred"
    if (bits and 0x80000 != 0) out += "Soft temperature limit has occurred"
    return out
}
