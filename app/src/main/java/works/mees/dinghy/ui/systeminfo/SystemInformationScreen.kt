package works.mees.dinghy.ui.systeminfo

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
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
 * The **System Information** drawer destination (Phase 20, SYS-01..05) — dense C6-exempt restyle
 * (28-07, D-11). Restyled to `ListRow` rows inside a `ListBlock`; scrolls freely
 * (no fit-one-page requirement for SysInfo). Nothing on this page dispatches a command.
 *
 * ## Layout (28-07 dense restyle)
 * `ScreenScaffold` with field-only layout. The `ListBlock` holds all info rows;
 * a `FootButtonBar` at the bottom of the field column holds the Back button. Health chip stays
 * as a dedicated labeled row. The SYS-01..SYS-05 content is unchanged — restyle only.
 *
 * ## Graceful degradation (SYS-04)
 * Every value flows through the Wave-2 formatters that return "—" on null. The labeled row + its
 * leading icon STAY present (stable layout across both SBCs).
 *
 * @param holder the per-session read-only [SystemInfoHolder] off `AppContainer.systemInfoHolder`.
 *   Null while idle — the page then renders the all-"—" degraded state.
 * @param onBack the neutral Back exit.
 */
@Composable
fun SystemInformationScreen(
    holder: SystemInfoHolder?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Null holder (idle) → the degraded all-"—" state via empty fallback flows.
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

/** A reusable null StateFlow for the idle/no-holder path (no allocation per row). */
private fun <T> nullStateFlow(): kotlinx.coroutines.flow.StateFlow<T?> =
    kotlinx.coroutines.flow.MutableStateFlow(null)

/**
 * The STATELESS content seam (PREVIEW_AND_TOKENS preview-first LAW) — pure inputs, no holder/
 * Moonraker, so the `@Preview` matrix in [works.mees.dinghy.preview.SysInfoPreviews] drives every
 * theme + degrade state without a live session.
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
    // Open Q1 host-label fallback: model when present, else the distro name (RockPro64 case).
    val hostLabel = identity?.model ?: identity?.distroName
    // The 1 Hz push omits throttle+uptime → those come from the one-shot procStats query.
    val chipTemp = live?.cpuTemp ?: procStats?.cpuTemp
    val health = healthState(procStats?.throttledState, chipTemp)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        ScreenScaffold(
            field = {
                ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    // ── SYS-01: Health chip ──────────────────────────────────────────────────
                    item(key = "health") {
                        val (icon, tint, labelRes) = healthChipVisual(health, t)
                        ListRow(
                            selected = false,
                            onClick = {},
                            uDp = grid.uDp,
                            leadingContent = {
                                // R23: canonical 0.6U list-row icon.
                                ListRowIcon(
                                    icon = icon,
                                    uDp = grid.uDp,
                                    tint = tint,
                                    contentDescription = stringResource(R.string.cd_sysinfo_health),
                                )
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.sysinfo_health),
                                color = t.text,
                                fontFamily = Geist,
                                fontSize = fsSp(20f, t.fs).sp, // R11 list-label default
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = stringResource(labelRes),
                                color = tint,
                                fontFamily = Geist,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = fsSp(15f, t.fs).sp,
                            )
                        }
                    }

                    // ── SYS-01: Host / model ────────────────────────────────────────────────
                    item(key = "host") {
                        InfoListRow(
                            icon = DinghyIcons.SysInfoHost,
                            cd = stringResource(R.string.cd_sysinfo_host),
                            label = stringResource(R.string.sysinfo_host),
                            value = hostLabel.orDash(),
                            uDp = grid.uDp,
                        )
                    }

                    // ── SYS-02: CPU temp ────────────────────────────────────────────────────
                    item(key = "cpu_temp") {
                        InfoListRow(
                            icon = DinghyIcons.LauncherTemperature,
                            cd = stringResource(R.string.cd_sysinfo_cpu_temp),
                            label = stringResource(R.string.sysinfo_cpu_temp),
                            value = formatTemp(chipTemp),
                            uDp = grid.uDp,
                        )
                    }

                    // ── SYS-03: Uptime ──────────────────────────────────────────────────────
                    item(key = "uptime") {
                        InfoListRow(
                            icon = DinghyIcons.SysInfoUptime,
                            cd = stringResource(R.string.cd_sysinfo_uptime),
                            label = stringResource(R.string.sysinfo_uptime),
                            value = formatUptime(procStats?.systemUptimeSeconds),
                            uDp = grid.uDp,
                        )
                    }

                    // ── SYS-04: CPU model + cores ───────────────────────────────────────────
                    item(key = "cpu") {
                        InfoListRow(
                            icon = DinghyIcons.SysInfoCpu,
                            cd = stringResource(R.string.cd_sysinfo_cpu),
                            label = stringResource(R.string.sysinfo_cpu),
                            value = cpuValue(identity),
                            uDp = grid.uDp,
                        )
                    }

                    // ── SYS-04: Total RAM ───────────────────────────────────────────────────
                    item(key = "ram") {
                        InfoListRow(
                            icon = DinghyIcons.SysInfoRam,
                            cd = stringResource(R.string.cd_sysinfo_ram),
                            label = stringResource(R.string.sysinfo_ram),
                            value = formatGb(identity?.totalMemoryKb),
                            uDp = grid.uDp,
                        )
                    }

                    // ── SYS-05: Distro ──────────────────────────────────────────────────────
                    item(key = "distro") {
                        InfoListRow(
                            icon = DinghyIcons.SysInfoDistro,
                            cd = stringResource(R.string.cd_sysinfo_distro),
                            label = stringResource(R.string.sysinfo_distro),
                            value = distroValue(identity),
                            uDp = grid.uDp,
                        )
                    }

                    // ── SYS-05: Kernel ──────────────────────────────────────────────────────
                    item(key = "kernel") {
                        InfoListRow(
                            icon = DinghyIcons.SysInfoKernel,
                            cd = stringResource(R.string.cd_sysinfo_kernel),
                            label = stringResource(R.string.sysinfo_kernel),
                            value = identity?.kernel.orDash(),
                            uDp = grid.uDp,
                        )
                    }

                    // ── Live: CPU load % (1 Hz push) ────────────────────────────────────────
                    item(key = "cpu_load") {
                        InfoListRow(
                            icon = DinghyIcons.Speed,
                            cd = stringResource(R.string.cd_sysinfo_cpu_load),
                            label = stringResource(R.string.sysinfo_cpu_load),
                            value = formatCpuLoad(live?.cpuLoadPercent),
                            uDp = grid.uDp,
                        )
                    }

                    // ── Live: Memory used / total (1 Hz push) ────────────────────────────────
                    item(key = "mem_usage") {
                        InfoListRow(
                            icon = DinghyIcons.SysInfoMemUsage,
                            cd = stringResource(R.string.cd_sysinfo_mem_usage),
                            label = stringResource(R.string.sysinfo_memory),
                            value = formatMemoryUsedOverTotal(live?.memUsedKb, live?.memTotalKb),
                            uDp = grid.uDp,
                        )
                    }
                }

                FootButtonBar(
                    uDp = grid.uDp,
                ) {
                    OutlinedControl(
                        label = stringResource(R.string.common_back),
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent, // R5: Back = accent
                    )
                }
            },
        )
    }
}

/**
 * A dense icon-label-value `ListRow` — `[icon] label + Spacer + value(GeistMono)`.
 * The row stays present even when value is "—" (stable layout, SYS-04).
 */
@Composable
private fun InfoListRow(
    icon: DinghyIcon,
    cd: String,
    label: String,
    value: String,
    uDp: androidx.compose.ui.unit.Dp,
) {
    val t = LocalTokens.current
    ListRow(
        selected = false,
        onClick = {},
        uDp = uDp,
        leadingContent = {
            // R23: canonical 0.6U list-row icon.
            ListRowIcon(
                icon = icon,
                uDp = uDp,
                tint = t.text2,
                contentDescription = cd,
            )
        },
    ) {
        Text(
            text = label,
            color = t.text,
            fontFamily = Geist,
            fontSize = fsSp(20f, t.fs).sp, // R11 list-label default
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            color = t.text2,
            fontFamily = GeistMono,
            fontSize = fsSp(15f, t.fs).sp,
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

/** The shape-coded (icon, tint, labelRes) triple for each [HealthState] (shape carries safety). */
private fun healthChipVisual(
    health: HealthState,
    t: ThemeTokens,
): Triple<DinghyIcon, androidx.compose.ui.graphics.Color, Int> = when (health) {
    HealthState.Caution -> Triple(DinghyIcons.StatusStop, t.stop, R.string.sysinfo_health_caution)
    // WR-06 (icon-registry-only LAW): use the REGISTERED Warning token — an inline DinghyIcon(
    // IconRef.Ligature(...)) bypasses DinghyIconsTest's drift guards and the subset-tool registry walk.
    HealthState.Warn -> Triple(DinghyIcons.Warning, t.heat, R.string.sysinfo_health_warn)
    HealthState.Healthy -> Triple(DinghyIcons.CheckCircle, t.go, R.string.sysinfo_health_healthy)
}
