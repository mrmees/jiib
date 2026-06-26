package works.mees.jiib.ui.printstatus

/**
 * The pure, host-testable Standby-glance sensor selection (Phase 16). NO Compose, NO Android — plain
 * Kotlin so it runs in the JVM unit suite. Decouples "which temperature_sensor do we show at a glance"
 * from any UI: the Standby screen (16-06) calls [selectGlanceSensor] on `PrinterState.temperatureSensors`
 * and renders the result; it never re-derives the preference itself.
 *
 * Why a pure selector over a RETAINED map (T-16-04-D): a single glance field selected from the raw
 * `notify_status_update` diff would FLIP (mcu→chamber/host) or go stale whenever only one sensor object
 * appears in a partial diff. Selecting from the retained map with a STABLE preference order — the same
 * input map always yields the same choice — closes that class of bug. Order is by sorted key, NOT
 * diff-arrival order.
 */

/** A resolved glance reading: the chosen `temperature_sensor <name>` key and its temperature (°C). */
data class GlanceSensor(val name: String, val temperature: Double)

/**
 * Pick the preferred Standby-glance sensor from the retained map: an `mcu` sensor if present, else a
 * `host` sensor, else the first sensor by stable (sorted-key) order. Returns null when the map is empty.
 * The match is case-insensitive on the key (so `temperature_sensor MCU` is still preferred). Selection
 * is deterministic for a given map — independent of diff-arrival order — which is the partial-diff-flip
 * guard. Host-load (`machine.proc_stats`) is intentionally NOT modelled here (deferred to Phase 19).
 */
fun selectGlanceSensor(sensors: Map<String, Double>): GlanceSensor? {
    if (sensors.isEmpty()) return null
    // Stable iteration order: sort by key so the choice is diff-order-independent.
    val ordered = sensors.toSortedMap()
    val chosen = ordered.entries.firstOrNull { it.key.contains("mcu", ignoreCase = true) }
        ?: ordered.entries.firstOrNull { it.key.contains("host", ignoreCase = true) }
        ?: ordered.entries.first()
    return GlanceSensor(name = chosen.key, temperature = chosen.value)
}
