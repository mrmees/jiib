// GREEN (Plan 20-02) — live assertions for the pure healthState(throttledState, cpuTemp) fn.
package works.mees.dinghy.systeminfo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SYS-03. The health-chip decision is a PURE function of (throttledState, cpuTemp) → 3 states
 * (healthy / warn / caution), host-unit-testable.
 *
 * PRECEDENCE (RESEARCH Health-Chip Logic Spec):
 *   throttledState != null  -> THROTTLE path (Pi authoritative):
 *       (bits & 0x0000F) != 0 -> caution ; else (bits & 0xF0000) != 0 -> warn ; else healthy
 *   throttledState == null   -> TEMP-FALLBACK path (RockPro64 / non-Pi), D-12 LOCKED cutoffs:
 *       cpuTemp >= 80 -> caution ; cpuTemp >= 70 -> warn ; else healthy ; null temp -> healthy
 */
class HealthChipTest {

    @Test
    fun e5CleanThrottle_healthy() {
        // E5 proc_stats throttled_state {bits:0, flags:[]} → THROTTLE path, no bits set → healthy.
        val state = ThrottledState(bits = 0, flags = emptyList())
        assertEquals(HealthState.Healthy, healthState(state, cpuTemp = 64.7f))
    }

    @Test
    fun syntheticActiveBit_caution() {
        // A synthetic active-now bit in the low nibble (bits & 0xF != 0) → caution.
        assertEquals(HealthState.Caution, healthState(ThrottledState(bits = 0x1), cpuTemp = 40f))
        // Caution wins even with an occurred bit also set.
        assertEquals(HealthState.Caution, healthState(ThrottledState(bits = 0x40001), cpuTemp = null))
    }

    @Test
    fun syntheticOccurredBit_warn() {
        // A has-occurred-since-boot bit (bits 16-19) with the low nibble clear → warn.
        assertEquals(HealthState.Warn, healthState(ThrottledState(bits = 0x10000), cpuTemp = 40f))
        assertEquals(HealthState.Warn, healthState(ThrottledState(bits = 0x40000), cpuTemp = 40f))
    }

    @Test
    fun e3NullThrottle_tempFallback_70warn_80caution() {
        // throttled_state == null (RockPro64) → TEMP-FALLBACK with D-12 LOCKED cutoffs.
        assertEquals(HealthState.Healthy, healthState(null, cpuTemp = 53.333f)) // E3 live temp
        assertEquals(HealthState.Healthy, healthState(null, cpuTemp = 69.99f))
        assertEquals(HealthState.Warn, healthState(null, cpuTemp = 70.0f))
        assertEquals(HealthState.Warn, healthState(null, cpuTemp = 72.0f))
        assertEquals(HealthState.Warn, healthState(null, cpuTemp = 79.99f))
        assertEquals(HealthState.Caution, healthState(null, cpuTemp = 80.0f))
        assertEquals(HealthState.Caution, healthState(null, cpuTemp = 81.0f))
    }

    @Test
    fun nullTemp_nullThrottle_healthyNoCrash() {
        // No throttle data and no temp → healthy default, never throws.
        assertEquals(HealthState.Healthy, healthState(null, cpuTemp = null))
    }
}
