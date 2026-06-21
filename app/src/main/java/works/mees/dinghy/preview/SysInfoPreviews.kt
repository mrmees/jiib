package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import works.mees.dinghy.systeminfo.McuDevice
import works.mees.dinghy.systeminfo.ProcStatLive
import works.mees.dinghy.systeminfo.ProcStatQuery
import works.mees.dinghy.systeminfo.SystemInfo
import works.mees.dinghy.systeminfo.ThrottledState
import works.mees.dinghy.ui.systeminfo.HostAction
import works.mees.dinghy.ui.systeminfo.McuAction
import works.mees.dinghy.ui.systeminfo.SystemInformationContent

/**
 * @Preview matrix for SystemInformationScreen (Task 9 rebuild: device browser).
 *
 * Targets the STATELESS [SystemInformationContent] seam — no holder, no Moonraker.
 *
 * ## Axes exercised
 *  - (a) Host selected, full Raspberry Pi data + throttle conditions
 *  - (b) Host selected, non-Pi sparse (no throttle, "—" fields, null MCUs)
 *  - (c) MCU selected, multi-board fixture with CAN interface
 *  - (d) MCU sparse degrade — all optional fields null
 *  - 6 theme combos on host-populated state
 *  - fs = L overflow check — verifies dense rows don't clip at large text size
 *  - RTL spotcheck — confirms start/end-relative modifiers mirror correctly
 *  - Pseudolocale en-XA — i18n completeness sweep
 */

// ─────────────────────────────────────────────────────────────────────────────
// Fixture data
// ─────────────────────────────────────────────────────────────────────────────

/** Raspberry Pi-style populated identity — all fields present and non-blank. */
private val populatedIdentity = SystemInfo(
    model = "Raspberry Pi 4 Model B Rev 1.4",
    cpuDesc = "Cortex-A72",
    processor = "ARMv7",
    cpuCount = 4,
    totalMemoryKb = 3_883_008L, // ~3.7 GB
    distroName = "Debian GNU/Linux",
    distroVersion = "12 (bookworm)",
    kernel = "6.6.20+rpt-rpi-v8",
)

/** RockPro64-style degraded identity — model empty string (→ null), some absent fields. */
private val degradedIdentity = SystemInfo(
    model = null,         // empty → null on RockPro64 (Pitfall 4)
    cpuDesc = null,       // absent
    processor = "AArch64",
    cpuCount = 6,
    totalMemoryKb = null, // absent
    distroName = "Armbian",
    distroVersion = null,
    kernel = null,
)

/** One-shot proc stats with uptime + healthy throttle state (Pi). */
private val populatedProcStats = ProcStatQuery(
    throttledState = ThrottledState(bits = 0, flags = emptyList()),
    cpuTemp = 52.3f,
    systemUptimeSeconds = 172_800.0, // 2 days
)

/** One-shot proc stats: null throttle (non-Pi) + warm temp. */
private val degradedProcStats = ProcStatQuery(
    throttledState = null, // non-Pi host — no vcgencmd data
    cpuTemp = 81.5f,       // warm
    systemUptimeSeconds = 3_600.0,
)

/** Live push frame for the populated preview. */
private val populatedLive = ProcStatLive(
    cpuTemp = 52.3f,
    cpuLoadPercent = 14.7f,
    memUsedKb = 1_024_000L,
    memTotalKb = 3_883_008L,
)

/** Live push frame absent (null) — all live rows degrade to "—". */
private val noLive: ProcStatLive? = null

/** Mainboard MCU (USB/serial interface). */
private val mcuMainboard = McuDevice(
    key = "mcu",
    displayName = "Mainboard",
    firmwareVersion = "v0.12.0-123-gabcdef",
    chip = "STM32F407",
    clockHz = 168_000_000L,
    interfaceDesc = "USB /dev/serial/by-id/usb-Klipper_stm32f407",
    mcuAwake = 0.073f,
    taskAvg = 0.001f,
    taskStddev = 0.0003f,
    bytesWrite = 11_264L,
    bytesRead = 14_336L,
    bytesRetransmit = 0L,
)

/** CAN toolhead board (EBBCan) — subset of fields reported over CAN. */
private val mcuEbbCan = McuDevice(
    key = "mcu EBBCan",
    displayName = "EBBCan",
    firmwareVersion = "v0.12.0-100-gfedcba",
    chip = "STM32G0B1",
    clockHz = 64_000_000L,
    interfaceDesc = "canbus uuid=aabbccddeeff",
    mcuAwake = 0.031f,
    taskAvg = null,
    taskStddev = null,
    bytesWrite = 2_048L,
    bytesRead = 3_072L,
    bytesRetransmit = null,
)

/** MCU with all optional fields null — sparse degrade. */
private val mcuSparse = McuDevice(
    key = "mcu host",
    displayName = "Linux Process MCU",
)

private val multiMcus = listOf(mcuMainboard, mcuEbbCan)

// ─────────────────────────────────────────────────────────────────────────────
// Helper composables
// ─────────────────────────────────────────────────────────────────────────────

/** (a) Host selected, full Pi data + Pi throttle block. */
@Composable
private fun sysInfoHostFull() {
    SystemInformationContent(
        identity = populatedIdentity,
        procStats = populatedProcStats,
        live = populatedLive,
        mcus = multiMcus,
        klipperVersion = "v0.12.0-123-gabcdef",
        moonrakerVersion = "v0.8.0",
        inFlightKeys = emptySet(),
        onHostAction = {},
        onMcuAction = {},
        onBack = {},
    )
}

/** (b) Host selected, non-Pi sparse (no throttle, some "—" fields, null MCUs = loading). */
@Composable
private fun sysInfoHostSparse() {
    SystemInformationContent(
        identity = degradedIdentity,
        procStats = degradedProcStats,
        live = noLive,
        mcus = null,
        klipperVersion = null,
        moonrakerVersion = null,
        inFlightKeys = emptySet(),
        onHostAction = {},
        onMcuAction = {},
        onBack = {},
    )
}

/** (c) MCU selected — multi-board fixture, CAN interface. Renders EBBCan (key="mcu EBBCan"). */
@Composable
private fun sysInfoMcuSelected() {
    SystemInformationContent(
        identity = populatedIdentity,
        procStats = populatedProcStats,
        live = populatedLive,
        mcus = multiMcus,
        klipperVersion = "v0.12.0-123-gabcdef",
        moonrakerVersion = "v0.8.0",
        inFlightKeys = emptySet(),
        onHostAction = {},
        onMcuAction = {},
        onBack = {},
        initialSelectedKey = "mcu EBBCan",
    )
}

/** (d) MCU sparse degrade — all optional fields null (Linux-process MCU, key="mcu host"). */
@Composable
private fun sysInfoMcuSparse() {
    SystemInformationContent(
        identity = populatedIdentity,
        procStats = degradedProcStats,
        live = noLive,
        mcus = listOf(mcuSparse),
        klipperVersion = null,
        moonrakerVersion = null,
        inFlightKeys = emptySet(),
        onHostAction = {},
        onMcuAction = {},
        onBack = {},
        initialSelectedKey = "mcu host",
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// (a) Host full: portrait + landscape
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "SysInfo (a): Host full Pi (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun SysInfoHostFullPortrait() = PreviewBox(colorfulDark) { sysInfoHostFull() }

@Preview(
    name = "SysInfo (a): Host full Pi (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun SysInfoHostFullLandscape() = PreviewBox(colorfulDark) { sysInfoHostFull() }

// ─────────────────────────────────────────────────────────────────────────────
// (b) Host sparse: portrait
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "SysInfo (b): Host sparse non-Pi (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun SysInfoHostSparsePortrait() = PreviewBox(colorfulDark) { sysInfoHostSparse() }

// ─────────────────────────────────────────────────────────────────────────────
// (c) MCU selected: portrait + landscape
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "SysInfo (c): MCU selected multi-board CAN (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun SysInfoMcuSelectedPortrait() = PreviewBox(colorfulDark) { sysInfoMcuSelected() }

@Preview(
    name = "SysInfo (c): MCU selected multi-board CAN (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun SysInfoMcuSelectedLandscape() = PreviewBox(colorfulDark) { sysInfoMcuSelected() }

// ─────────────────────────────────────────────────────────────────────────────
// (d) MCU sparse degrade: portrait
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "SysInfo (d): MCU sparse degrade (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun SysInfoMcuSparsePortrait() = PreviewBox(colorfulDark) { sysInfoMcuSparse() }

// ─────────────────────────────────────────────────────────────────────────────
// 6-theme matrix on host-populated state (most complex — full fields + action buttons)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun SysInfoThemeColorfulDark() = PreviewBox(colorfulDark) { sysInfoHostFull() }

@Nexus7Previews
@Composable
private fun SysInfoThemeColorfulLight() = PreviewBox(colorfulLight) { sysInfoHostFull() }

@Nexus7Previews
@Composable
private fun SysInfoThemeSimpleDark() = PreviewBox(simpleDark) { sysInfoHostFull() }

@Nexus7Previews
@Composable
private fun SysInfoThemeSimpleLight() = PreviewBox(simpleLight) { sysInfoHostFull() }

@Nexus7Previews
@Composable
private fun SysInfoThemeHighContrastDark() = PreviewBox(highContrastDark) { sysInfoHostFull() }

@Nexus7Previews
@Composable
private fun SysInfoThemeHighContrastLight() = PreviewBox(highContrastLight) { sysInfoHostFull() }

// ─────────────────────────────────────────────────────────────────────────────
// fs = L overflow check — dense rows at largest text size
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "SysInfo fs=L portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun SysInfoFsLargeOverflowPortrait() = PreviewBox(fsLargeSeed) { sysInfoHostFull() }

@Preview(
    name = "SysInfo fs=L landscape overflow check",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun SysInfoFsLargeOverflowLandscape() = PreviewBox(fsLargeSeed) { sysInfoHostFull() }

// ─────────────────────────────────────────────────────────────────────────────
// RTL spotcheck — confirms start/end-relative modifiers mirror correctly
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun SysInfoRtlSpotCheck() {
    PreviewBox(colorfulDark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            sysInfoHostFull()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pseudolocale en-XA — i18n completeness sweep
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "SysInfo pseudolocale en-XA",
    device = NEXUS7_PORTRAIT,
    locale = "en-XA",
    showBackground = true,
)
@Composable
private fun SysInfoPseudolocaleSpotCheck() = PreviewBox(colorfulDark) { sysInfoHostFull() }
