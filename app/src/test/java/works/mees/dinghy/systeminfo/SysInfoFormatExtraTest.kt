// GREEN — TDD tests for the three device-browser formatters (formatClock/formatLoad/formatBytes).
package works.mees.dinghy.systeminfo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure formatter tests for the MCU / device-browser detail rows (Task 8).
 *
 *  - formatClock  : Hz → "168 MHz" (÷1_000_000, integer). "—" on null.
 *  - formatLoad   : fraction → integer percent (0.0731 → "7%"). "—" on null.
 *  - formatBytes  : (write, read) → "↑1.0 KB ↓2.0 KB". "—" if both null; each side degrades.
 */
class SysInfoFormatExtraTest {

    // ── formatClock ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun clockTypicalMhz() {
        assertEquals("168 MHz", formatClock(168_000_000L))
    }

    @Test
    fun clockLargeValue() {
        assertEquals("250 MHz", formatClock(250_000_000L))
    }

    @Test
    fun clockNullReturnsDash() {
        assertEquals("—", formatClock(null))
    }

    @Test
    fun clockZeroHz() {
        // 0 Hz → "0 MHz" (not a crash; degenerate Klipper board data edge-case)
        assertEquals("0 MHz", formatClock(0L))
    }

    // ── formatLoad ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun loadTypicalFraction() {
        // 0.0731 * 100 = 7.31 → "7%"
        assertEquals("7%", formatLoad(0.0731f))
    }

    @Test
    fun loadHighFraction() {
        // 0.85 * 100 = 85.0 → "85%"
        assertEquals("85%", formatLoad(0.85f))
    }

    @Test
    fun loadNullReturnsDash() {
        assertEquals("—", formatLoad(null))
    }

    @Test
    fun loadZero() {
        assertEquals("0%", formatLoad(0f))
    }

    // ── formatBytes ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun bytesTypical() {
        // 1024 bytes = 1.0 KB, 2048 bytes = 2.0 KB
        assertEquals("↑1.0 KB ↓2.0 KB", formatBytes(1024L, 2048L))
    }

    @Test
    fun bytesNonRound() {
        // 1536 bytes = 1.5 KB
        assertEquals("↑1.5 KB ↓0.5 KB", formatBytes(1536L, 512L))
    }

    @Test
    fun bytesBothNullReturnsDash() {
        assertEquals("—", formatBytes(null, null))
    }

    @Test
    fun bytesWriteNullDegradesSide() {
        // write null → "↑—", read present
        assertEquals("↑— ↓1.0 KB", formatBytes(null, 1024L))
    }

    @Test
    fun bytesReadNullDegradesSide() {
        // read null → "↓—", write present
        assertEquals("↑2.0 KB ↓—", formatBytes(2048L, null))
    }

    // ── shared helpers: cpuValue, distroValue, orDash ────────────────────────────────────────────

    @Test
    fun cpuValueModelAndCores() {
        val si = SystemInfo(cpuDesc = "Cortex-A72", cpuCount = 4)
        assertEquals("Cortex-A72 · 4 cores", cpuValue(si))
    }

    @Test
    fun cpuValueModelOnly() {
        val si = SystemInfo(cpuDesc = "Cortex-A72")
        assertEquals("Cortex-A72", cpuValue(si))
    }

    @Test
    fun cpuValueCoresOnly() {
        val si = SystemInfo(cpuCount = 6)
        assertEquals("6 cores", cpuValue(si))
    }

    @Test
    fun cpuValueNullReturnsDash() {
        assertEquals("—", cpuValue(null))
        assertEquals("—", cpuValue(SystemInfo()))
    }

    @Test
    fun cpuValueFallsBackToProcessorField() {
        val si = SystemInfo(cpuDesc = "", processor = "ARMv8 Processor", cpuCount = 4)
        assertEquals("ARMv8 Processor · 4 cores", cpuValue(si))
    }

    @Test
    fun distroValueNameAndVersion() {
        val si = SystemInfo(distroName = "Debian GNU/Linux", distroVersion = "12")
        assertEquals("Debian GNU/Linux 12", distroValue(si))
    }

    @Test
    fun distroValueVersionAlreadyInName() {
        val si = SystemInfo(distroName = "Ubuntu 22.04 LTS", distroVersion = "22.04")
        assertEquals("Ubuntu 22.04 LTS", distroValue(si))
    }

    @Test
    fun distroValueNullReturnsDash() {
        assertEquals("—", distroValue(null))
        assertEquals("—", distroValue(SystemInfo()))
    }

    @Test
    fun orDashPresentValue() {
        assertEquals("hello", "hello".orDash())
    }

    @Test
    fun orDashNullReturnsDash() {
        assertEquals("—", (null as String?).orDash())
    }

    @Test
    fun orDashBlankReturnsDash() {
        assertEquals("—", "  ".orDash())
        assertEquals("—", "".orDash())
    }
}
