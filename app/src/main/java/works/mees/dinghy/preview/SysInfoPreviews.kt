package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import works.mees.dinghy.systeminfo.ProcStatLive
import works.mees.dinghy.systeminfo.ProcStatQuery
import works.mees.dinghy.systeminfo.SystemInfo
import works.mees.dinghy.systeminfo.ThrottledState
import works.mees.dinghy.ui.systeminfo.SystemInformationContent

/**
 * @Preview matrix for SystemInformationScreen (Phase 20, SYS-01..05) after 28-07 dense restyle.
 *
 * Targets the STATELESS [SystemInformationContent] seam — no holder, no Moonraker.
 *
 * ## Axes exercised
 *  - Populated (Raspberry Pi with all fields present)
 *  - Degraded (RockPro64-style: empty model, some nulls — "—" fallback path, SYS-04)
 *  - 6 theme combos on populated state
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

/** One-shot proc stats with uptime + healthy throttle state. */
private val populatedProcStats = ProcStatQuery(
    throttledState = ThrottledState(bits = 0, flags = emptyList()),
    cpuTemp = 52.3f,
    systemUptimeSeconds = 172_800.0, // 2 days
)

/** One-shot proc stats: null throttle (non-Pi) + warm temp → Warn chip. */
private val degradedProcStats = ProcStatQuery(
    throttledState = null, // non-Pi host — no vcgencmd data
    cpuTemp = 81.5f,       // warm → Warn chip (SYS-01 Warn band)
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

// ─────────────────────────────────────────────────────────────────────────────
// Helper composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun sysInfoPopulated() {
    SystemInformationContent(
        identity = populatedIdentity,
        procStats = populatedProcStats,
        live = populatedLive,
        onBack = {},
    )
}

@Composable
private fun sysInfoDegraded() {
    SystemInformationContent(
        identity = degradedIdentity,
        procStats = degradedProcStats,
        live = noLive,
        onBack = {},
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// State matrix: populated vs degraded
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "SysInfo: populated Pi (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun SysInfoPopulatedPortrait() = PreviewBox(colorfulDark) { sysInfoPopulated() }

@Preview(
    name = "SysInfo: populated Pi (Nexus7 landscape)",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun SysInfoPopulatedLandscape() = PreviewBox(colorfulDark) { sysInfoPopulated() }

@Preview(
    name = "SysInfo: degraded RockPro64 (all-dash fallback) (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun SysInfoDegradedPortrait() = PreviewBox(colorfulDark) { sysInfoDegraded() }

@Preview(
    name = "SysInfo: idle holder null (all-dash fallback) (Nexus7 portrait)",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun SysInfoAllNullIdle() = PreviewBox(colorfulDark) {
    SystemInformationContent(
        identity = null,
        procStats = null,
        live = null,
        onBack = {},
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 6-theme matrix on populated state (most complex — health chip + all values)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun SysInfoThemeColorfulDark() = PreviewBox(colorfulDark) { sysInfoPopulated() }

@Nexus7Previews
@Composable
private fun SysInfoThemeColorfulLight() = PreviewBox(colorfulLight) { sysInfoPopulated() }

@Nexus7Previews
@Composable
private fun SysInfoThemeSimpleDark() = PreviewBox(simpleDark) { sysInfoPopulated() }

@Nexus7Previews
@Composable
private fun SysInfoThemeSimpleLight() = PreviewBox(simpleLight) { sysInfoPopulated() }

@Nexus7Previews
@Composable
private fun SysInfoThemeHighContrastDark() = PreviewBox(highContrastDark) { sysInfoPopulated() }

@Nexus7Previews
@Composable
private fun SysInfoThemeHighContrastLight() = PreviewBox(highContrastLight) { sysInfoPopulated() }

// ─────────────────────────────────────────────────────────────────────────────
// fs = L overflow check — dense rows at largest text size
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "SysInfo fs=L portrait overflow check",
    device = NEXUS7_PORTRAIT,
    showBackground = true,
)
@Composable
private fun SysInfoFsLargeOverflowPortrait() = PreviewBox(fsLargeSeed) { sysInfoPopulated() }

@Preview(
    name = "SysInfo fs=L landscape overflow check",
    device = NEXUS7,
    showBackground = true,
)
@Composable
private fun SysInfoFsLargeOverflowLandscape() = PreviewBox(fsLargeSeed) { sysInfoPopulated() }

// ─────────────────────────────────────────────────────────────────────────────
// RTL spotcheck — confirms start/end-relative modifiers mirror correctly
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun SysInfoRtlSpotCheck() {
    PreviewBox(colorfulDark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            sysInfoPopulated()
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
private fun SysInfoPseudolocaleSpotCheck() = PreviewBox(colorfulDark) { sysInfoPopulated() }
