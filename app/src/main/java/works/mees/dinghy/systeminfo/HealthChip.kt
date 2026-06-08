package works.mees.dinghy.systeminfo

/**
 * The three health-chip states, rendered via the Phase-15.1 shape-coded status system
 * (warn = amber triangle "warning"; caution = red "disabled_by_default"/StatusStop; healthy =
 * go/shapeless).
 */
enum class HealthState { Healthy, Warn, Caution }

/**
 * Pure host-health decision (SYS-03). Total + side-effect-free over all `(ThrottledState?, Float?)`
 * inputs — no array indexing, no parse-to-throw, never crashes (T-20-02-T accept).
 *
 * PRECEDENCE (20-RESEARCH Health-Chip Logic Spec):
 *  - **Throttle path** (Pi authoritative) when [throttledState] != null — the raw `vcgencmd` bit mask:
 *      - low nibble `(bits & 0x0000F) != 0`  → an under-voltage / freq-cap / throttle is ACTIVE NOW
 *        → [HealthState.Caution] (red StatusStop).
 *      - else bits 16-19 `(bits & 0xF0000) != 0` → a throttle event HAS OCCURRED since boot
 *        → [HealthState.Warn] (amber triangle).
 *      - else → [HealthState.Healthy].
 *  - **Temp-fallback path** (RockPro64 / non-Pi) when [throttledState] == null — the D-12 LOCKED
 *    cutoffs:
 *      - cpuTemp >= 80 → [HealthState.Caution] ; >= 70 → [HealthState.Warn] ; else → Healthy.
 *      - null temp → [HealthState.Healthy] (documented no-crash default).
 *
 * The cutoff numbers 70 and 80 are LOCKED (20-CONTEXT D-12) — do not change them.
 */
fun healthState(throttledState: ThrottledState?, cpuTemp: Float?): HealthState {
    if (throttledState != null) {
        // Throttle-authoritative path — raw bit mask is the primary source.
        val bits = throttledState.bits
        return when {
            (bits and 0x0000F) != 0 -> HealthState.Caution
            (bits and 0xF0000) != 0 -> HealthState.Warn
            else -> HealthState.Healthy
        }
    }
    // Temp-fallback path with the D-12 LOCKED cutoffs.
    val t = cpuTemp ?: return HealthState.Healthy
    return when {
        t >= 80f -> HealthState.Caution
        t >= 70f -> HealthState.Warn
        else -> HealthState.Healthy
    }
}
