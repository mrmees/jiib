// GREEN (Plan 20-02) — live assertions for the kB->GB / kB->MB / compact-uptime formatters.
package works.mees.dinghy.systeminfo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SYS-03 formatting contract (LOCKED by the staging doc / RESEARCH Field-Mapping Table). Every
 * memory field across both endpoints is in kB (memory_units == "kB").
 *
 *   - kB -> GB : ÷ 1024² , e.g. 8007452 kB -> "7.6 GB" (total RAM row locks GB).
 *   - kB -> MB : ÷ 1024  , for the live "used / total" auto-scaled memory row.
 *   - uptime : compact "2d 3h 14m", DROPPING leading-zero units. Under a day -> "3h 14m"; under an
 *              hour -> "14m".
 *   - cpu load : integer % ; cpu temp : whole °C ; cores : integer.
 *   - every formatter returns "—" on null (SYS-04 degrade).
 */
class SysInfoFormatTest {

    @Test
    fun kbToGb() {
        assertEquals("7.6 GB", formatGb(8007452L))
        assertEquals("3.8 GB", formatGb(3945568L))
    }

    @Test
    fun kbToMb() {
        // used/total auto-scaled: used < 1 GB renders MB, total renders GB.
        // 761312 kB / 1024 = 743.47 -> 743 MB (the staging "744 MB" was illustrative).
        assertEquals("743 MB / 7.6 GB", formatMemoryUsedOverTotal(761312L, 8007452L))
        assertEquals("1.1 GB / 3.8 GB", formatMemoryUsedOverTotal(1131288L, 3945568L))
    }

    @Test
    fun uptimeCompactDropsLeadingZeroUnits() {
        assertEquals("2d 3h 14m", formatUptime((2 * 86_400 + 3 * 3_600 + 14 * 60).toDouble()))
        assertEquals("3h 14m", formatUptime((3 * 3_600 + 14 * 60).toDouble()))
        assertEquals("14m", formatUptime((14 * 60).toDouble()))
    }

    @Test
    fun cpuLoadAndTempAndCores() {
        assertEquals("29%", formatCpuLoad(29.31f))
        assertEquals("65°C", formatTemp(64.757f))
        assertEquals("4", formatCores(4))
    }

    @Test
    fun nullInputRendersDash() {
        assertEquals("—", formatGb(null))
        assertEquals("—", formatMemoryUsedOverTotal(null, 8007452L))
        assertEquals("—", formatUptime(null))
        assertEquals("—", formatCpuLoad(null))
        assertEquals("—", formatTemp(null))
        assertEquals("—", formatCores(null))
    }
}
