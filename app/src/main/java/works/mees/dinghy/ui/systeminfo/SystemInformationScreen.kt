package works.mees.dinghy.ui.systeminfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.icons.IconRef
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.systeminfo.HealthState
import works.mees.dinghy.systeminfo.ProcStatLive
import works.mees.dinghy.systeminfo.ProcStatQuery
import works.mees.dinghy.systeminfo.SystemInfo
import works.mees.dinghy.systeminfo.SystemInfoHolder
import works.mees.dinghy.systeminfo.formatCores
import works.mees.dinghy.systeminfo.formatCpuLoad
import works.mees.dinghy.systeminfo.formatGb
import works.mees.dinghy.systeminfo.formatMemoryUsedOverTotal
import works.mees.dinghy.systeminfo.formatTemp
import works.mees.dinghy.systeminfo.formatUptime
import works.mees.dinghy.systeminfo.healthState
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The **System Information** drawer destination (Phase 20, SYS-01..05) — a READ-ONLY printer-host
 * health/diagnostics page describing the SBC that runs Klipper + Moonraker for the ACTIVE printer (NOT
 * the Dinghy app — that is About — and NOT the printer's motion/heater hardware — those have their own
 * screens). Nothing on this page dispatches a command.
 *
 * ## Layout (the staging-doc Focus/Field/Gutter contract — `20-system-info-staging.md`)
 * [ScreenScaffold] with:
 *  - a **Focus health summary** (the only flag-raising zone): hostname/model, CPU temp, the shape-coded
 *    [HealthChip], uptime.
 *  - a **Field** single-scroll of two labeled sections — **Host** (CPU/RAM/distro/kernel, static-ish
 *    identity from `machine.system_info`) and **Live load** (CPU %, memory used/total, the 1 Hz push) —
 *    each a stack of icon-led label:value rows in FIXED logical order (NOT alphabetical).
 *  - a **Back-only** Neutral gutter; the global swipe-up drawer is suppressed on this screen (AppShell's
 *    swipe-suppress set, matching About/Phase-18).
 *
 * ## Graceful degradation (SYS-04)
 * Every value flows through the Wave-2 formatters that return "—" on null; the labeled row + its leading
 * icon STAY present (stable layout across both SBCs). Empty-string identity fields are already treated as
 * missing by the parser/formatters. The host LABEL fallback (Open Q1: `cpu_info.model` blank → distro
 * name) is applied here as `identity.model ?: identity.distroName`.
 *
 * Discipline: every color via [LocalTokens] (THEME-01 — this page has no data-color carve-out); every
 * font/icon size via `fsSp(baseSp, t.fs)` ([[dinghy-font-sizes-too-small]] — 15sp metadata floor, 17-18
 * body, 20-22 titles).
 *
 * @param holder the per-session read-only [SystemInfoHolder] off `AppContainer.systemInfoHolder`. Null
 *   while idle (no live session) — the page then renders the all-"—" degraded state, stable.
 * @param onBack the neutral gutter Back exit.
 */
@Composable
fun SystemInformationScreen(
    holder: SystemInfoHolder?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Null holder (idle) → the degraded all-"—" state via empty fallback flows; a live holder collects the
    // identity (one-shot), procStats (throttle+uptime one-shot), and live (1 Hz push) StateFlows.
    val identity by (holder?.identity ?: nullStateFlow()).collectAsStateWithLifecycle()
    val procStats by (holder?.procStats ?: nullStateFlow()).collectAsStateWithLifecycle()
    val live by (holder?.live ?: nullStateFlow()).collectAsStateWithLifecycle()

    SystemInformationContent(
        identity = identity,
        procStats = procStats,
        live = live,
        onBack = onBack,
        modifier = modifier,
    )
}

/** A reusable null StateFlow for the idle/no-holder path (kept off the hot path; no allocation per row). */
private fun <T> nullStateFlow(): kotlinx.coroutines.flow.StateFlow<T?> =
    kotlinx.coroutines.flow.MutableStateFlow(null)

/**
 * The STATELESS content seam (PREVIEW_AND_TOKENS preview-first LAW) — pure inputs, no holder/Moonraker, so
 * the `@Preview` matrix in [works.mees.dinghy.preview.SystemInfoPreviews] drives every theme + degrade
 * state without a live session. The wired [SystemInformationScreen] collects the holder flows into these.
 */
@Composable
fun SystemInformationContent(
    identity: SystemInfo?,
    procStats: ProcStatQuery?,
    live: ProcStatLive?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    // Open Q1 host-label fallback: model when present, else the distro name (the RockPro64 case where
    // cpu_info.model is the empty string → model is null). Both fields are carried on identity.
    val hostLabel = identity?.model ?: identity?.distroName
    // The 1 Hz push omits throttle+uptime → those come from the one-shot procStats query. The chip's temp
    // input prefers the live push temp, falling back to the query temp (the push may not have landed yet).
    val chipTemp = live?.cpuTemp ?: procStats?.cpuTemp
    val health = healthState(procStats?.throttledState, chipTemp)

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            field = {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SectionHeader(stringResource(R.string.sysinfo_title))

                    // ===================== FOCUS — health summary =========================
                    // Hostname / host model (D-02 dns).
                    IconInfoRow(
                        icon = DinghyIcons.SysInfoHost,
                        cd = stringResource(R.string.cd_sysinfo_host),
                        label = stringResource(R.string.sysinfo_host),
                        value = hostLabel.orDash(),
                    )
                    // CPU temp (D-03 thermostat — REUSED LauncherTemperature token).
                    IconInfoRow(
                        icon = DinghyIcons.LauncherTemperature,
                        cd = stringResource(R.string.cd_sysinfo_cpu_temp),
                        label = stringResource(R.string.sysinfo_cpu_temp),
                        value = formatTemp(chipTemp),
                    )
                    // The shape-coded health chip — its own row (shape carries safety, color redundant).
                    HealthChipRow(health = health)
                    // Uptime (D-04 schedule), from proc_stats.system_uptime.
                    IconInfoRow(
                        icon = DinghyIcons.SysInfoUptime,
                        cd = stringResource(R.string.cd_sysinfo_uptime),
                        label = stringResource(R.string.sysinfo_uptime),
                        value = formatUptime(procStats?.systemUptimeSeconds),
                    )

                    // ===================== FIELD — Host (static identity) =================
                    SectionLabel(stringResource(R.string.sysinfo_section_host))
                    // CPU model + cores (D-05 developer_board).
                    IconInfoRow(
                        icon = DinghyIcons.SysInfoCpu,
                        cd = stringResource(R.string.cd_sysinfo_cpu),
                        label = stringResource(R.string.sysinfo_cpu),
                        value = cpuValue(identity),
                    )
                    // Total RAM (D-06 memory).
                    IconInfoRow(
                        icon = DinghyIcons.SysInfoRam,
                        cd = stringResource(R.string.cd_sysinfo_ram),
                        label = stringResource(R.string.sysinfo_ram),
                        value = formatGb(identity?.totalMemoryKb),
                    )
                    // Distro name + version (D-07 deployed_code).
                    IconInfoRow(
                        icon = DinghyIcons.SysInfoDistro,
                        cd = stringResource(R.string.cd_sysinfo_distro),
                        label = stringResource(R.string.sysinfo_distro),
                        value = distroValue(identity),
                    )
                    // Kernel (D-08 code_blocks).
                    IconInfoRow(
                        icon = DinghyIcons.SysInfoKernel,
                        cd = stringResource(R.string.cd_sysinfo_kernel),
                        label = stringResource(R.string.sysinfo_kernel),
                        value = identity?.kernel.orDash(),
                    )

                    // ===================== FIELD — Live load (1 Hz push) =================
                    SectionLabel(stringResource(R.string.sysinfo_section_live))
                    // CPU load % (D-09 speed — REUSED Speed token).
                    IconInfoRow(
                        icon = DinghyIcons.Speed,
                        cd = stringResource(R.string.cd_sysinfo_cpu_load),
                        label = stringResource(R.string.sysinfo_cpu_load),
                        value = formatCpuLoad(live?.cpuLoadPercent),
                    )
                    // Memory used / total (D-10 data_usage).
                    IconInfoRow(
                        icon = DinghyIcons.SysInfoMemUsage,
                        cd = stringResource(R.string.cd_sysinfo_mem_usage),
                        label = stringResource(R.string.sysinfo_memory),
                        value = formatMemoryUsedOverTotal(live?.memUsedKb, live?.memTotalKb),
                    )

                    // Bottom breathing room so the last row clears the scroll edge.
                    Box(Modifier.height(24.dp))
                }
            },
            gutter = {
                OutlinedControl(
                    label = stringResource(R.string.common_back),
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    intent = Intent.Neutral, // plain nav spends no safety color (the About/Move precedent).
                    symbol = "arrow_back",
                )
            },
        )
    }
}

/** "CPU model · N cores" — degrades each side independently; "—" when neither is present. */
private fun cpuValue(identity: SystemInfo?): String {
    val model = identity?.cpuDesc?.takeIf { it.isNotBlank() } ?: identity?.processor?.takeIf { it.isNotBlank() }
    val cores = identity?.cpuCount
    return when {
        model != null && cores != null -> "$model · ${formatCores(cores)} cores"
        model != null -> model
        cores != null -> "${formatCores(cores)} cores"
        else -> DASH_UI
    }
}

/** "Debian GNU/Linux 12 (bookworm)" — name + version; "—" when the name is absent. */
private fun distroValue(identity: SystemInfo?): String {
    val name = identity?.distroName?.takeIf { it.isNotBlank() } ?: return DASH_UI
    val version = identity?.distroVersion?.takeIf { it.isNotBlank() }
    return if (version != null && version !in name) "$name $version" else name
}

private const val DASH_UI = "—"

/** A nullable/blank string → its value or the degrade dash (SYS-04). */
private fun String?.orDash(): String = this?.takeIf { it.isNotBlank() } ?: DASH_UI

/**
 * An icon-led label : monospace-value row — the About `InfoRow` structure with a LEADING glyph slot added
 * (the sole structural delta the staging doc calls for; About's private `InfoRow` is NOT widened). The
 * value is GeistMono (tabular). Stays present even when [value] is "—" (stable layout, SYS-04).
 */
@Composable
private fun IconInfoRow(
    icon: DinghyIcon,
    cd: String,
    label: String,
    value: String,
) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DinghyIconView(
            icon = icon,
            tint = t.text2,
            sizeDp = fsSp(22f, t.fs).dp,
            contentDescription = cd,
        )
        Text(
            text = label,
            color = t.text,
            fontFamily = Geist,
            fontSize = fsSp(17f, t.fs).sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = t.text2,
            fontFamily = GeistMono,
            fontSize = fsSp(15f, t.fs).sp,
        )
    }
}

/**
 * The health-summary chip row: the shape-coded [HealthState] glyph + its label. Shape carries safety
 * (THEMING — color is redundant): caution = [DinghyIcons.StatusStop] (square+✕) tinted `t.stop`; warn =
 * the `warning` triangle tinted `t.heat`; healthy = the shapeless/go [DinghyIcons.CheckCircle] tinted
 * `t.go`. No new chip drawable.
 */
@Composable
private fun HealthChipRow(health: HealthState) {
    val t = LocalTokens.current
    val (icon, tint, labelRes) = healthChipVisual(health, t)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DinghyIconView(
            icon = icon,
            tint = tint,
            sizeDp = fsSp(22f, t.fs).dp,
            contentDescription = stringResource(R.string.cd_sysinfo_health),
        )
        Text(
            text = stringResource(R.string.sysinfo_health),
            color = t.text,
            fontFamily = Geist,
            fontSize = fsSp(17f, t.fs).sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(labelRes),
            color = tint,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(15f, t.fs).sp,
        )
    }
}

/** The shape-coded (icon, tint, labelRes) triple for each [HealthState] (shape carries safety). */
private fun healthChipVisual(
    health: HealthState,
    t: ThemeTokens,
): Triple<DinghyIcon, androidx.compose.ui.graphics.Color, Int> = when (health) {
    HealthState.Caution -> Triple(DinghyIcons.StatusStop, t.stop, R.string.sysinfo_health_caution)
    HealthState.Warn -> Triple(DinghyIcon(IconRef.Ligature("warning"), alternate = "sysinfo_warn"), t.heat, R.string.sysinfo_health_warn)
    HealthState.Healthy -> Triple(DinghyIcons.CheckCircle, t.go, R.string.sysinfo_health_healthy)
}

@Composable
private fun SectionHeader(text: String) {
    val t = LocalTokens.current
    Text(
        text = text,
        color = t.text,
        fontFamily = Geist,
        fontWeight = FontWeight.Bold,
        fontSize = fsSp(22f, t.fs).sp,
    )
}

@Composable
private fun SectionLabel(text: String) {
    val t = LocalTokens.current
    Text(
        text = text,
        color = t.text2,
        fontFamily = Geist,
        fontWeight = FontWeight.SemiBold,
        fontSize = fsSp(20f, t.fs).sp,
        modifier = Modifier.padding(top = 8.dp),
    )
}
