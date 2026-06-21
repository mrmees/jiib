// GREEN — TDD tests for the hostDetailLines / mcuDetailLines pure line-builders (Task 9 UAT).
package works.mees.dinghy.systeminfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the scrollable plain-text line-builders introduced in Task 9 UAT.
 *
 * Cases covered:
 *  - Full Raspberry Pi host data → all lines present with correct content.
 *  - Non-Pi host (null throttle) → no throttle lines.
 *  - Sparse host (null live / null identity fields) → segments dropped from lines, no crash.
 *  - MCU full data → all lines present.
 *  - MCU sparse (all optional fields null) → only a "—" line, no crash.
 *  - Per-field segment degrade across formatters.
 */
class SysInfoDetailTextTest {

    // ── Host fixture helpers ──────────────────────────────────────────────────────────────────────

    private fun fullIdentity() = SystemInfo(
        model = "Raspberry Pi 4 Model B Rev 1.4",
        cpuDesc = "Cortex-A72",
        processor = "ARMv7",
        cpuCount = 4,
        totalMemoryKb = 7_948_288L,   // ~7.6 GB
        distroName = "Debian GNU/Linux",
        distroVersion = "12 (bookworm)",
        kernel = "6.6.20+rpt-rpi-v8",
    )

    private fun fullProcStats() = ProcStatQuery(
        throttledState = ThrottledState(bits = 0, flags = emptyList()),
        cpuTemp = 52.0f,
        systemUptimeSeconds = (2 * 86_400 + 3 * 3_600 + 14 * 60).toDouble(), // 2d 3h 14m
    )

    private fun fullLive() = ProcStatLive(
        cpuTemp = 52.0f,
        cpuLoadPercent = 12.0f,
        memUsedKb = 761_312L,
        memTotalKb = 7_948_288L,
    )

    private fun fullHost(
        identity: SystemInfo? = fullIdentity(),
        procStats: ProcStatQuery? = fullProcStats(),
        live: ProcStatLive? = fullLive(),
        klipperVersion: String? = "v0.12.0",
        moonrakerVersion: String? = "v0.8.0",
    ) = HostDevice(
        displayName = "Raspberry Pi 4",
        identity = identity,
        procStats = procStats,
        live = live,
        klipperVersion = klipperVersion,
        moonrakerVersion = moonrakerVersion,
    )

    // ── hostDetailLines — full Pi data ────────────────────────────────────────────────────────────

    @Test
    fun hostFullPiLine1CpuCoresRam() {
        val lines = hostDetailLines(fullHost(), emptyList())
        // "Cortex-A72 · 4 cores · 7.6 GB"
        val line1 = lines[0]
        assertTrue("Line 1 should contain cpu desc", "Cortex-A72" in line1)
        assertTrue("Line 1 should contain cores", "4 cores" in line1)
        assertTrue("Line 1 should contain RAM", "GB" in line1)
    }

    @Test
    fun hostFullPiLine2DistroKernel() {
        val lines = hostDetailLines(fullHost(), emptyList())
        val line2 = lines[1]
        assertTrue("Line 2 should contain distro name", "Debian GNU/Linux" in line2)
        assertTrue("Line 2 should contain kernel prefix", "kernel" in line2)
        assertTrue("Line 2 should contain kernel version", "6.6.20" in line2)
    }

    @Test
    fun hostFullPiLine3Uptime() {
        val lines = hostDetailLines(fullHost(), emptyList())
        val line3 = lines[2]
        assertTrue("Line 3 should start with Up", line3.startsWith("Up "))
        assertTrue("Line 3 should contain days", "2d" in line3)
    }

    @Test
    fun hostFullPiLine4LiveStats() {
        val lines = hostDetailLines(fullHost(), emptyList())
        val line4 = lines[3]
        assertTrue("Line 4 should contain CPU load", "CPU 12%" in line4)
        assertTrue("Line 4 should contain temp", "52°C" in line4)
        assertTrue("Line 4 should contain mem", "mem" in line4)
    }

    @Test
    fun hostFullPiLine5Versions() {
        val lines = hostDetailLines(fullHost(), emptyList())
        val line5 = lines[4]
        assertTrue("Line 5 should contain Klipper", "Klipper v0.12.0" in line5)
        assertTrue("Line 5 should contain Moonraker", "Moonraker v0.8.0" in line5)
    }

    @Test
    fun hostFullPiThrottleLineAppended() {
        val throttle = listOf("Under-voltage detected", "Frequency capped")
        val lines = hostDetailLines(fullHost(), throttle)
        assertTrue("Throttle conditions should be appended", lines.contains("Under-voltage detected"))
        assertTrue("Second throttle condition appended", lines.contains("Frequency capped"))
    }

    // ── hostDetailLines — non-Pi host (no throttle) ───────────────────────────────────────────────

    @Test
    fun hostNonPiNoThrottleLines() {
        val lines = hostDetailLines(fullHost(), emptyList())
        // No throttle conditions — the list should not contain any throttle text
        assertFalse("No throttle lines expected", lines.any { "voltage" in it.lowercase() })
    }

    // ── hostDetailLines — sparse / null fields ────────────────────────────────────────────────────

    @Test
    fun hostNullLiveDropsLiveSegments() {
        val host = fullHost(live = null, procStats = fullProcStats().copy(cpuTemp = null))
        val lines = hostDetailLines(host, emptyList())
        // No live data → live line should be absent (no load, no mem)
        assertFalse("No live line when live=null", lines.any { "CPU" in it && "%" in it && "mem" in it })
    }

    @Test
    fun hostNullKlipperVersionOmitsKlipperSegment() {
        val host = fullHost(klipperVersion = null, moonrakerVersion = "v0.8.0")
        val lines = hostDetailLines(host, emptyList())
        // Version line should only contain Moonraker
        val versionLine = lines.firstOrNull { "Moonraker" in it }
        assertTrue("Moonraker still present", versionLine != null)
        assertFalse("Klipper not in version line", lines.any { "Klipper" in it })
    }

    @Test
    fun hostBothVersionsNullOmitsVersionLine() {
        val host = fullHost(klipperVersion = null, moonrakerVersion = null)
        val lines = hostDetailLines(host, emptyList())
        assertFalse("No version line when both null", lines.any { "Klipper" in it || "Moonraker" in it })
    }

    @Test
    fun hostNullIdentityDoesNotCrash() {
        val host = fullHost(identity = null)
        val lines = hostDetailLines(host, emptyList())
        // Should still produce something (uptime, live, versions) — no crash
        assertTrue("Should return a non-empty list", lines.isNotEmpty())
    }

    @Test
    fun hostNullProcStatsAndLiveOmitsUptimeAndLive() {
        val host = fullHost(procStats = null, live = null)
        val lines = hostDetailLines(host, emptyList())
        assertFalse("No uptime line", lines.any { it.startsWith("Up ") })
        assertFalse("No live CPU line", lines.any { "CPU" in it && "%" in it })
    }

    @Test
    fun hostLine1OnlyCpuWhenRamNull() {
        val id = fullIdentity().copy(totalMemoryKb = null)
        val lines = hostDetailLines(fullHost(identity = id), emptyList())
        // CPU line should still have cpu/cores but no "GB"
        val line1 = lines[0]
        assertTrue("Has cpu desc", "Cortex-A72" in line1)
        assertFalse("No GB segment", "GB" in line1)
    }

    // ── mcuDetailLines — full MCU data ────────────────────────────────────────────────────────────

    private fun fullMcu() = McuDevice(
        key = "mcu",
        displayName = "Mainboard",
        firmwareVersion = "v0.12.0-123-gabcdef",
        chip = "STM32F407",
        clockHz = 168_000_000L,
        interfaceDesc = "USB /dev/serial/by-id/usb-Klipper_stm32f407",
        mcuAwake = 0.073f,
        bytesWrite = 11_264L,
        bytesRead = 14_336L,
        bytesRetransmit = 0L,
    )

    @Test
    fun mcuFullFirmwareLine() {
        val lines = mcuDetailLines(fullMcu())
        val fw = lines.firstOrNull { "Firmware" in it }
        assertTrue("Firmware line present", fw != null)
        assertTrue("Firmware version present", "v0.12.0" in fw!!)
    }

    @Test
    fun mcuFullChipClockLine() {
        val lines = mcuDetailLines(fullMcu())
        val chipLine = lines.firstOrNull { "STM32F407" in it }
        assertTrue("Chip line present", chipLine != null)
        assertTrue("Clock present in chip line", "168 MHz" in chipLine!!)
    }

    @Test
    fun mcuFullInterfaceLine() {
        val lines = mcuDetailLines(fullMcu())
        assertTrue("Interface line present", lines.any { "USB" in it && "serial" in it })
    }

    @Test
    fun mcuFullLoadLine() {
        val lines = mcuDetailLines(fullMcu())
        val loadLine = lines.firstOrNull { it.startsWith("Load ") }
        assertTrue("Load line present", loadLine != null)
        assertTrue("Load value is percent", "%" in loadLine!!)
    }

    @Test
    fun mcuFullBandwidthLine() {
        val lines = mcuDetailLines(fullMcu())
        val bwLine = lines.firstOrNull { "↑" in it && "↓" in it }
        assertTrue("Bandwidth line present", bwLine != null)
        assertTrue("Retransmits in bandwidth line", "retransmits" in bwLine!!)
    }

    // ── mcuDetailLines — sparse / null fields ─────────────────────────────────────────────────────

    @Test
    fun mcuSparseAllNullsDoesNotCrash() {
        val mcu = McuDevice(key = "mcu host", displayName = "Linux Process MCU")
        val lines = mcuDetailLines(mcu)
        // Should contain at least the DASH line for the chip·clock group
        assertTrue("Non-empty lines from sparse MCU", lines.isNotEmpty())
        assertTrue("Dash line present for chip/clock", lines.contains("—"))
    }

    @Test
    fun mcuNullFirmwareOmitsFirmwareLine() {
        val mcu = fullMcu().copy(firmwareVersion = null)
        val lines = mcuDetailLines(mcu)
        assertFalse("No firmware line when null", lines.any { "Firmware" in it })
    }

    @Test
    fun mcuNullClockDropsClockSegment() {
        val mcu = fullMcu().copy(clockHz = null)
        val lines = mcuDetailLines(mcu)
        val chipLine = lines.firstOrNull { "STM32F407" in it }
        assertTrue("Chip line still present", chipLine != null)
        assertFalse("No MHz in chip line when clock null", "MHz" in chipLine!!)
    }

    @Test
    fun mcuNullChipAndClockEmitsDash() {
        val mcu = fullMcu().copy(chip = null, clockHz = null)
        val lines = mcuDetailLines(mcu)
        assertTrue("Dash line for chip/clock when both null", lines.contains("—"))
    }

    @Test
    fun mcuNullLoadOmitsLoadLine() {
        val mcu = fullMcu().copy(mcuAwake = null)
        val lines = mcuDetailLines(mcu)
        assertFalse("No Load line when mcuAwake null", lines.any { it.startsWith("Load ") })
    }

    @Test
    fun mcuNullRetransmitOmitsRetransmitSegment() {
        val mcu = fullMcu().copy(bytesRetransmit = null)
        val lines = mcuDetailLines(mcu)
        val bwLine = lines.firstOrNull { "↑" in it }
        assertTrue("Bandwidth line still present", bwLine != null)
        assertFalse("No retransmits segment", "retransmits" in bwLine!!)
    }

    @Test
    fun mcuNullBytesOmitsBandwidthLine() {
        val mcu = fullMcu().copy(bytesWrite = null, bytesRead = null, bytesRetransmit = null)
        val lines = mcuDetailLines(mcu)
        assertFalse("No bandwidth line when all bytes null", lines.any { "↑" in it || "↓" in it })
    }
}
