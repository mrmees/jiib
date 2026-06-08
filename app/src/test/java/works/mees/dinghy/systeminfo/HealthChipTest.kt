// RED scaffold (Phase 20 Wave 0 / Plan 20-01) — turns GREEN in Plan 02 (healthState fn).
package works.mees.dinghy.systeminfo

import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Wave-0 RED scaffold (20-01) — turned GREEN by Plan 02 (the pure `healthState(...)` fn).
 *
 * SYS-03. The health-chip decision is a PURE function of (throttledState, cpuTemp) → 3 states
 * (healthy / warn / caution), host-unit-testable, rendered via the Phase-15.1 shape-coded
 * status system (warn = amber triangle "warning"; caution = red "disabled_by_default"/StatusStop;
 * healthy = go/shapeless).
 *
 * PRECEDENCE (RESEARCH Health-Chip Logic Spec):
 *   throttledState != null  -> THROTTLE path (Pi authoritative):
 *       (bits & 0x0000F) != 0 -> caution ; else (bits & 0xF0000) != 0 -> warn ; else healthy
 *   throttledState == null   -> TEMP-FALLBACK path (RockPro64 / non-Pi), D-12 LOCKED cutoffs:
 *       cpuTemp >= 80 -> caution ; cpuTemp >= 70 -> warn ; else healthy ; null temp -> healthy/no-crash
 *
 * Fixtures: E5 proc_stats has throttled_state{bits:0,flags:[]} (clean → healthy);
 * E3 proc_stats has throttled_state:null (→ temp fallback, cpu_temp 53.333 → healthy at the 70/80 cutoffs).
 *
 * Production symbol referenced (NOT YET BUILT → RED): the pure `healthState(throttledState, cpuTemp)`
 * fn in `works.mees.dinghy.systeminfo` — Plan 02 introduces it (and the HealthState enum).
 */
class HealthChipTest {

    @Test
    fun e5CleanThrottle_healthy() {
        // throttled_state {bits:0, flags:[]} → THROTTLE path, no bits set → healthy.
        assumeTrue("Plan 02 implements works.mees.dinghy.systeminfo.healthState(throttledState, cpuTemp)", false)
    }

    @Test
    fun syntheticActiveBit_caution() {
        // A synthetic active-now bit in the low nibble (e.g. bits & 0xF != 0) → caution (red StatusStop).
        assumeTrue("Plan 02 implements works.mees.dinghy.systeminfo.healthState(throttledState, cpuTemp)", false)
    }

    @Test
    fun syntheticOccurredBit_warn() {
        // A synthetic has-occurred-since-boot bit (bits 16-19, e.g. 0x10000) with the low nibble clear → warn (amber triangle).
        assumeTrue("Plan 02 implements works.mees.dinghy.systeminfo.healthState(throttledState, cpuTemp)", false)
    }

    @Test
    fun e3NullThrottle_tempFallback_70warn_80caution() {
        // throttled_state == null (RockPro64) → TEMP-FALLBACK path with D-12 LOCKED cutoffs:
        //   cpuTemp < 70 -> healthy ; >= 70 -> warn ; >= 80 -> caution.
        // E3 fixture cpu_temp 53.333 -> healthy; a synthetic 70.0 -> warn; 80.0 -> caution.
        assumeTrue("Plan 02 implements works.mees.dinghy.systeminfo.healthState(throttledState, cpuTemp)", false)
    }
}
