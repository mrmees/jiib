package works.mees.dinghy.systeminfo

/**
 * Pure data models for the System Information page (Phase 20, SYS-01..04). NO I/O, NO coroutines,
 * NO Compose — host-unit-testable plain Kotlin. The tolerant parsers that build these from the
 * loose Moonraker `machine.system_info` / `machine.proc_stats` / `notify_proc_stat_update` payloads
 * live in [SystemInfoParse]; the pure health-chip decision fn + formatters live in [healthState]
 * and [works.mees.dinghy.systeminfo.formatGb] etc.
 *
 * Every numeric/string field is NULLABLE: a sparse, older, or non-Pi Moonraker omits, blanks, or
 * nulls fields, and the SYS-04 degrade contract maps any absent value to "—" at the UI. The parsers
 * NEVER throw — see the DegradeTest gate.
 */

/**
 * Static host identity from `machine.system_info.result.system_info`.
 *
 * REAL-SHAPE traps (20-RESEARCH):
 *  - `cpu_info.model` is the EMPTY STRING "" on the RockPro64 (not null, not absent) — blank treated
 *    as missing → [model] is null (Pitfall 4).
 *  - `kernel` is read from `distribution.kernel_version`, NOT a top-level system_info key (Pitfall 3).
 *  - memory is in kB (`memory_units` == "kB").
 */
data class SystemInfo(
    val model: String? = null,
    val cpuDesc: String? = null,
    val processor: String? = null,
    val cpuCount: Int? = null,
    val totalMemoryKb: Long? = null,
    val distroName: String? = null,
    val distroVersion: String? = null,
    val kernel: String? = null,
) {
    companion object
}

/**
 * Live per-tick resource sample from the `notify_proc_stat_update` push frame (params[0]).
 *
 * REAL-SHAPE trap (20-RESEARCH Pitfall 1 — the single most important finding): the PUSH frame
 * OMITS `throttled_state` AND `system_uptime`. Those live ONLY in the one-shot `machine.proc_stats`
 * QUERY result ([ProcStatQuery]). [ProcStatLive.fromPush] MUST tolerate their absence and must NOT
 * source throttle/uptime from the push.
 */
data class ProcStatLive(
    val cpuTemp: Float? = null,
    val cpuLoadPercent: Float? = null,
    val memUsedKb: Long? = null,
    val memTotalKb: Long? = null,
    val memAvailableKb: Long? = null,
) {
    companion object
}

/**
 * One-shot query result from `machine.proc_stats.result` — the ONLY source of [throttledState] and
 * [systemUptimeSeconds].
 *
 * REAL-SHAPE trap (20-RESEARCH Pitfall 2): `throttled_state` is an object on the Pi
 * (`{bits, flags}`) but explicit JSON `null` on the RockPro64. JsonNull is tolerated as "no throttle
 * data" (→ health-chip temp fallback), NOT a parse error.
 */
data class ProcStatQuery(
    val throttledState: ThrottledState? = null,
    val cpuTemp: Float? = null,
    val systemUptimeSeconds: Double? = null,
) {
    companion object
}

/**
 * The Raspberry Pi `vcgencmd get_throttled` view as surfaced by Moonraker's `throttled_state`:
 * a raw [bits] mask plus the decoded [flags] string list. Consumed by the pure [healthState] fn
 * (throttle-authoritative path). Absent / JsonNull on non-Pi hosts (→ temp fallback).
 */
data class ThrottledState(
    val bits: Int = 0,
    val flags: List<String> = emptyList(),
)
