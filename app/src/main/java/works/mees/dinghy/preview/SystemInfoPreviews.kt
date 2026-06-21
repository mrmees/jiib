package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import works.mees.dinghy.systeminfo.ProcStatLive
import works.mees.dinghy.systeminfo.ProcStatQuery
import works.mees.dinghy.systeminfo.SystemInfo
import works.mees.dinghy.systeminfo.ThrottledState
import works.mees.dinghy.ui.systeminfo.SystemInformationContent

/**
 * Day-one @Preview matrix for the Phase-20 System Information page (20-04 / PREVIEW_AND_TOKENS). Drives the
 * STATELESS [SystemInformationContent] content seam from pure sample data — NO live Moonraker, NO holder,
 * NO dispatcher. Covers the four authoritative states the cross-SBC UAT exercises:
 *  - a **healthy RPi 4** (Ender 5 Plus): full identity, clean throttle (bits 0 → healthy/go).
 *  - a **temp-fallback RockPro64** (Ender 3): blank cpu_info.model → distro-name host label, throttle
 *    NULL → temp-fallback path at an idle (sub-70) temp → healthy.
 *  - a **degraded/sparse** host: many "—" (a sparse/older Moonraker; SYS-04 stable layout).
 *  - a **caution** state: a Pi reporting an ACTIVE under-voltage throttle bit → red StatusStop shape.
 *
 * A `@Preview` annotation cannot select the Colorful/Simple/High-Contrast palette MODE; the themes MUST be
 * explicit [PreviewBox] seed wrappers, and `fs = L` is injected via [fsLargeSeed] (NOT `@Preview(fontScale)`,
 * a verified NO-OP — the #1 copy-paste trap).
 *
 * NOTE: This file was the Phase-20 era preview. The newer per-axis matrix lives in [SysInfoPreviews].
 * These cells call the rebuilt [SystemInformationContent] (Task 9) — mcus=null, versions=null, no-op actions.
 */

// --- Sample states ----------------------------------------------------------------------------------

/** Ender 5 Plus / RPi 4 — full identity, clean throttle (healthy). */
private val rpi4Identity = SystemInfo(
    model = "Raspberry Pi 4 Model B Rev 1.4",
    cpuDesc = "ARMv8 Processor",
    processor = "cortex-a72",
    cpuCount = 4,
    totalMemoryKb = 8_007_452L,
    distroName = "Debian GNU/Linux 12 (bookworm)",
    distroVersion = "12",
    kernel = "6.12.87+rpt-rpi-v8",
)
private val rpi4ProcStats = ProcStatQuery(
    throttledState = ThrottledState(bits = 0, flags = emptyList()),
    cpuTemp = 64.7f,
    systemUptimeSeconds = 185_640.0, // 2d 3h 34m
)
private val rpi4Live = ProcStatLive(
    cpuTemp = 65.1f,
    cpuLoadPercent = 29.31f,
    memUsedKb = 1_320_000L,
    memTotalKb = 8_007_452L,
    memAvailableKb = 6_687_452L,
)

/** Ender 3 / RockPro64 — blank model (→ distro-name host label), NULL throttle (temp fallback), idle temp. */
private val rockProIdentity = SystemInfo(
    model = null, // cpu_info.model == "" on the RockPro64 → null (host label falls back to distro name)
    cpuDesc = "rockchip,rk3399",
    processor = "cortex-a72/a53",
    cpuCount = 6,
    totalMemoryKb = 3_993_000L,
    distroName = "Armbian 25.11.2 noble",
    distroVersion = "25.11.2",
    kernel = "6.18.10-current-rockchip64",
)
private val rockProProcStats = ProcStatQuery(
    throttledState = null, // non-Pi → no throttle data → the chip uses the temp-fallback path
    cpuTemp = 52.0f,
    systemUptimeSeconds = 11_640.0, // 3h 14m
)
private val rockProLive = ProcStatLive(
    cpuTemp = 53.4f,
    cpuLoadPercent = 12.0f,
    memUsedKb = 761_312L,
    memTotalKb = 3_993_000L,
    memAvailableKb = 3_231_688L,
)

/** A caution state — a Pi reporting an ACTIVE under-voltage bit (low-nibble set) → red caution shape. */
private val cautionProcStats = ProcStatQuery(
    throttledState = ThrottledState(bits = 0x1, flags = listOf("Under-voltage detected")),
    cpuTemp = 82.0f,
    systemUptimeSeconds = 420.0, // 7m
)
private val cautionLive = ProcStatLive(
    cpuTemp = 82.5f,
    cpuLoadPercent = 88.0f,
    memUsedKb = 7_400_000L,
    memTotalKb = 8_007_452L,
    memAvailableKb = 607_452L,
)

// --- Stateless cells (updated for Task-9 signature: mcus=null, no-op actions) ---------------------

@Composable
private fun HealthyRpi4Cell() = SystemInformationContent(
    identity = rpi4Identity,
    procStats = rpi4ProcStats,
    live = rpi4Live,
    mcus = null,
    klipperVersion = null,
    moonrakerVersion = null,
    inFlightKeys = emptySet(),
    onHostAction = {},
    onMcuAction = {},
    onBack = {},
)

@Composable
private fun TempFallbackRockProCell() = SystemInformationContent(
    identity = rockProIdentity,
    procStats = rockProProcStats,
    live = rockProLive,
    mcus = null,
    klipperVersion = null,
    moonrakerVersion = null,
    inFlightKeys = emptySet(),
    onHostAction = {},
    onMcuAction = {},
    onBack = {},
)

/** Sparse/older Moonraker — everything null → every row degrades to "—" (SYS-04 stable layout). */
@Composable
private fun DegradedCell() = SystemInformationContent(
    identity = SystemInfo(),
    procStats = ProcStatQuery(),
    live = null,
    mcus = null,
    klipperVersion = null,
    moonrakerVersion = null,
    inFlightKeys = emptySet(),
    onHostAction = {},
    onMcuAction = {},
    onBack = {},
)

@Composable
private fun CautionCell() = SystemInformationContent(
    identity = rpi4Identity,
    procStats = cautionProcStats,
    live = cautionLive,
    mcus = null,
    klipperVersion = null,
    moonrakerVersion = null,
    inFlightKeys = emptySet(),
    onHostAction = {},
    onMcuAction = {},
    onBack = {},
)

// --- Healthy RPi 4: the full 6-theme matrix (dark/light × Colorful/Simple/HighContrast) -------------

@Nexus7Previews
@Composable
private fun SysInfoHealthyColorfulDark() = PreviewBox(colorfulDark) { HealthyRpi4Cell() }

@Nexus7Previews
@Composable
private fun SysInfoHealthyColorfulLight() = PreviewBox(colorfulLight) { HealthyRpi4Cell() }

@Nexus7Previews
@Composable
private fun SysInfoHealthySimpleDark() = PreviewBox(simpleDark) { HealthyRpi4Cell() }

@Nexus7Previews
@Composable
private fun SysInfoHealthySimpleLight() = PreviewBox(simpleLight) { HealthyRpi4Cell() }

@Nexus7Previews
@Composable
private fun SysInfoHealthyHighContrastDark() = PreviewBox(highContrastDark) { HealthyRpi4Cell() }

@Nexus7Previews
@Composable
private fun SysInfoHealthyHighContrastLight() = PreviewBox(highContrastLight) { HealthyRpi4Cell() }

/** fs = L overflow shot — catches row/value clipping at the largest text size. */
@Nexus7Previews
@Composable
private fun SysInfoHealthyFsLargeOverflow() = PreviewBox(fsLargeSeed) { HealthyRpi4Cell() }

// --- The other three states (Colorful/dark + a contrast spot-check) ---------------------------------

/** Temp-fallback RockPro64: blank model → distro-name host label, NULL throttle → temp-fallback chip. */
@Nexus7Previews
@Composable
private fun SysInfoTempFallbackColorfulDark() = PreviewBox(colorfulDark) { TempFallbackRockProCell() }

@Nexus7Previews
@Composable
private fun SysInfoTempFallbackHighContrastLight() = PreviewBox(highContrastLight) { TempFallbackRockProCell() }

/** Degraded/sparse: every row present, every value "—" (SYS-04). */
@Nexus7Previews
@Composable
private fun SysInfoDegradedColorfulDark() = PreviewBox(colorfulDark) { DegradedCell() }

@Nexus7Previews
@Composable
private fun SysInfoDegradedSimpleLight() = PreviewBox(simpleLight) { DegradedCell() }

/** Caution: active under-voltage throttle → red StatusStop shape (shape carries safety). */
@Nexus7Previews
@Composable
private fun SysInfoCautionColorfulDark() = PreviewBox(colorfulDark) { CautionCell() }

@Nexus7Previews
@Composable
private fun SysInfoCautionHighContrastDark() = PreviewBox(highContrastDark) { CautionCell() }

/** Pseudolocale (`en-XA`) i18n spot-check — any English showing through is an unrouted literal. */
@Preview(device = NEXUS7, locale = "en-XA", showBackground = true)
@Composable
private fun SysInfoPseudolocaleSpotCheck() {
    PreviewBox(colorfulDark) { HealthyRpi4Cell() }
}
