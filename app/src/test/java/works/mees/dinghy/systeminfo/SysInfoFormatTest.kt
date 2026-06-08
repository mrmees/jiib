// RED scaffold (Phase 20 Wave 0 / Plan 20-01) — turns GREEN in Plan 02 (the pure formatter fns).
package works.mees.dinghy.systeminfo

import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Wave-0 RED scaffold (20-01) — turned GREEN by Plan 02 (the pure formatter fns).
 *
 * SYS-03 formatting contract (LOCKED by the staging doc / RESEARCH Field-Mapping Table). Every
 * memory field across both endpoints is in kB (memory_units == "kB").
 *
 *   - kB -> GB : ÷ 1024² , e.g. 8007452 kB -> "7.6 GB" (total RAM row locks GB).
 *   - kB -> MB : ÷ 1024  , for the live "used / total" auto-scaled memory row (staging "612 MB / 3.8 GB").
 *   - uptime : compact "2d 3h 14m", DROPPING leading-zero units. Under a day -> "3h 14m"
 *              (no "0d"); under an hour -> "14m". Source = proc_stats.system_uptime (HOST uptime).
 *   - cpu load : integer %, e.g. 29.31 -> "29%".  cpu temp : whole °C, e.g. 64.757 -> "65°C".
 *   - cores : integer.
 *
 * Production symbols referenced (NOT YET BUILT → RED): the kB->GB / kB->MB / uptime-compact
 * formatter fns in `works.mees.dinghy.systeminfo` — Plan 02 introduces them.
 */
class SysInfoFormatTest {

    @Test
    fun kbToGb() {
        // 8007452 kB -> "7.6 GB" ; 3945568 kB -> "3.8 GB".
        assumeTrue("Plan 02 implements the kB->GB formatter in works.mees.dinghy.systeminfo", false)
    }

    @Test
    fun kbToMb() {
        // used/available live row auto-scaled, kB -> MB (÷1024), e.g. "612 MB / 3.8 GB".
        assumeTrue("Plan 02 implements the kB->MB formatter in works.mees.dinghy.systeminfo", false)
    }

    @Test
    fun uptimeCompactDropsLeadingZeroUnits() {
        // "2d 3h 14m" when days>0 ; under a day -> "3h 14m" (no leading "0d 00h").
        assumeTrue("Plan 02 implements the compact-uptime formatter in works.mees.dinghy.systeminfo", false)
    }
}
