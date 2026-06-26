package works.mees.jiib.systeminfo

import kotlinx.serialization.json.JsonElement

/**
 * Orchestrates the System Info screen's lazy data loads over injected query lambdas (so it is host-
 * unit-testable with no Moonraker / SpineHandle). The holder wires the lambdas to
 * `CommandDispatcher.query(...)`. Each call is best-effort at the holder layer (the holder wraps
 * these in runCatching); the parsers themselves never throw.
 */
class SystemInfoLoader(
    private val queryObjects: suspend (Set<String>) -> JsonElement,
    private val queryPrinterInfo: suspend () -> JsonElement,
    private val queryServerInfo: suspend () -> JsonElement,
) {
    suspend fun loadVersions(): Versions = Versions(
        klipper = parseKlipperVersion(queryPrinterInfo()),
        moonraker = parseMoonrakerVersion(queryServerInfo()),
    )

    suspend fun loadMcuDevices(objectNames: Set<String>): List<McuDevice> {
        val mcus = mcuObjectNames(objectNames)
        if (mcus.isEmpty()) return emptyList()
        val result = queryObjects(mcus.toSet() + "configfile")
        return parseMcuDevices(mcus, result)
    }

    suspend fun refreshStats(objectNames: Set<String>, existing: List<McuDevice>): List<McuDevice> {
        val mcus = mcuObjectNames(objectNames)
        if (mcus.isEmpty()) return existing
        val result = queryObjects(mcus.toSet())   // no configfile on refresh — static detail is preserved
        return mergeStats(existing, parseMcuStats(mcus, result))
    }
}
