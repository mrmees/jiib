package works.mees.dinghy.ui.systeminfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.systeminfo.DASH
import works.mees.dinghy.systeminfo.HostActionAvailability
import works.mees.dinghy.systeminfo.HostDevice
import works.mees.dinghy.systeminfo.McuDevice
import works.mees.dinghy.systeminfo.cpuValue
import works.mees.dinghy.systeminfo.distroValue
import works.mees.dinghy.systeminfo.formatBytes
import works.mees.dinghy.systeminfo.formatClock
import works.mees.dinghy.systeminfo.formatCpuLoad
import works.mees.dinghy.systeminfo.formatGb
import works.mees.dinghy.systeminfo.formatLoad
import works.mees.dinghy.systeminfo.formatMemoryUsedOverTotal
import works.mees.dinghy.systeminfo.formatTemp
import works.mees.dinghy.systeminfo.formatUptime
import works.mees.dinghy.systeminfo.orDash
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle

/**
 * Actions the user can trigger on the host SBC from the System Info device browser.
 * Danger intent (red) = could destroy (reboot/shutdown); Warn intent (amber) = service restart.
 */
enum class HostAction { Reboot, Shutdown, RestartMoonraker }

/**
 * Actions the user can trigger on a selected MCU from the System Info device browser.
 * Both use Warn intent (amber) — hazardous-in-process restarts.
 */
enum class McuAction { FirmwareRestart, RestartKlipper }

/**
 * Shared icon · label · value row for the System Info device Focus. Mirrors the old private
 * `InfoListRow` from [SystemInformationScreen] (extracted here for DRY reuse across Host + MCU
 * detail). Value is end-aligned via a Spacer weight (UAT-2). The old screen's private copy stays
 * until Task 9 deletes it.
 */
@Composable
internal fun DetailRow(icon: DinghyIcon, cd: String, label: String, value: String, uDp: Dp) {
    val t = LocalTokens.current
    ListRow(
        selected = false,
        onClick = {},
        uDp = uDp,
        leadingContent = {
            ListRowIcon(icon = icon, uDp = uDp, tint = t.text2, contentDescription = cd)
        },
    ) {
        Text(text = label, color = t.text, style = DinghyType.listLabel.toTextStyle(t))
        Spacer(Modifier.weight(1f))
        Text(text = value, color = t.text2, style = DinghyType.dataMeta.toTextStyle(t))
    }
}

/**
 * Focus detail rows for the selected host SBC device.
 *
 * Shows all static identity fields, live load/temp/memory, versions (Klipper, Moonraker), and
 * — on Pi hosts only — the throttle condition block (hidden when [throttle] is empty, i.e. non-Pi).
 *
 * @param host   The host device model (identity + live fields + version strings).
 * @param throttle Decoded throttle condition labels from [decodeThrottleConditions]; empty = off-Pi.
 * @param uDp    The unit grid Dp from [rememberUnitGrid] / [LocalUnitDp].
 */
@Composable
fun HostDetail(host: HostDevice, throttle: List<String>, uDp: Dp) {
    val id = host.identity
    DetailRow(
        icon = DinghyIcons.SysInfoHost,
        cd = stringResource(R.string.cd_sysinfo_host),
        label = stringResource(R.string.sysinfo_host),
        value = host.displayName,
        uDp = uDp,
    )
    DetailRow(
        icon = DinghyIcons.SysInfoCpu,
        cd = stringResource(R.string.cd_sysinfo_cpu),
        label = stringResource(R.string.sysinfo_cpu),
        value = cpuValue(id),
        uDp = uDp,
    )
    DetailRow(
        icon = DinghyIcons.LauncherTemperature,
        cd = stringResource(R.string.cd_sysinfo_cpu_temp),
        label = stringResource(R.string.sysinfo_cpu_temp),
        value = formatTemp(host.live?.cpuTemp ?: host.procStats?.cpuTemp),
        uDp = uDp,
    )
    DetailRow(
        icon = DinghyIcons.Speed,
        cd = stringResource(R.string.cd_sysinfo_cpu_load),
        label = stringResource(R.string.sysinfo_cpu_load),
        value = formatCpuLoad(host.live?.cpuLoadPercent),
        uDp = uDp,
    )
    DetailRow(
        icon = DinghyIcons.SysInfoMemUsage,
        cd = stringResource(R.string.cd_sysinfo_mem_usage),
        label = stringResource(R.string.sysinfo_memory),
        value = formatMemoryUsedOverTotal(host.live?.memUsedKb, host.live?.memTotalKb),
        uDp = uDp,
    )
    DetailRow(
        icon = DinghyIcons.SysInfoRam,
        cd = stringResource(R.string.cd_sysinfo_ram),
        label = stringResource(R.string.sysinfo_ram),
        value = formatGb(id?.totalMemoryKb),
        uDp = uDp,
    )
    DetailRow(
        icon = DinghyIcons.SysInfoDistro,
        cd = stringResource(R.string.cd_sysinfo_distro),
        label = stringResource(R.string.sysinfo_distro),
        value = distroValue(id),
        uDp = uDp,
    )
    DetailRow(
        icon = DinghyIcons.SysInfoKernel,
        cd = stringResource(R.string.cd_sysinfo_kernel),
        label = stringResource(R.string.sysinfo_kernel),
        value = id?.kernel.orDash(),
        uDp = uDp,
    )
    DetailRow(
        icon = DinghyIcons.SysInfoUptime,
        cd = stringResource(R.string.cd_sysinfo_uptime),
        label = stringResource(R.string.sysinfo_uptime),
        value = formatUptime(host.procStats?.systemUptimeSeconds),
        uDp = uDp,
    )
    DetailRow(
        icon = DinghyIcons.SysInfoTile,
        cd = stringResource(R.string.cd_sysinfo_klipper),
        label = stringResource(R.string.sysinfo_klipper_version),
        value = host.klipperVersion.orDash(),
        uDp = uDp,
    )
    DetailRow(
        icon = DinghyIcons.SystemRowAbout,
        cd = stringResource(R.string.cd_sysinfo_moonraker),
        label = stringResource(R.string.sysinfo_moonraker_version),
        value = host.moonrakerVersion.orDash(),
        uDp = uDp,
    )
    // Pi throttle block — rendered only when conditions are present (empty = off-Pi host).
    throttle.forEach { condition ->
        DetailRow(
            icon = DinghyIcons.Warning,
            cd = stringResource(R.string.cd_sysinfo_throttle),
            label = stringResource(R.string.sysinfo_throttle),
            value = condition,
            uDp = uDp,
        )
    }
}

/**
 * Focus detail rows for the selected MCU device.
 *
 * Per-field "—" via formatters when the MCU reports sparse data (CAN toolhead boards report a
 * different subset than the mainboard — SYS-04 degrade contract).
 *
 * @param mcu  The MCU device model from the device browser state.
 * @param uDp  The unit grid Dp.
 */
@Composable
fun McuDetail(mcu: McuDevice, uDp: Dp) {
    DetailRow(
        icon = DinghyIcons.SysInfoTile,
        cd = stringResource(R.string.cd_sysinfo_firmware),
        label = stringResource(R.string.sysinfo_mcu_firmware),
        value = mcu.firmwareVersion.orDash(),
        uDp = uDp,
    )
    DetailRow(
        icon = DinghyIcons.SysInfoCpu,
        cd = stringResource(R.string.cd_sysinfo_cpu),
        label = stringResource(R.string.sysinfo_mcu_chip),
        value = mcu.chip.orDash(),
        uDp = uDp,
    )
    DetailRow(
        icon = DinghyIcons.Speed,
        cd = stringResource(R.string.cd_sysinfo_clock),
        label = stringResource(R.string.sysinfo_mcu_clock),
        value = formatClock(mcu.clockHz),
        uDp = uDp,
    )
    DetailRow(
        icon = DinghyIcons.SysInfoHost,
        cd = stringResource(R.string.cd_sysinfo_interface),
        label = stringResource(R.string.sysinfo_mcu_interface),
        value = mcu.interfaceDesc.orDash(),
        uDp = uDp,
    )
    DetailRow(
        icon = DinghyIcons.SysInfoMemUsage,
        cd = stringResource(R.string.cd_sysinfo_cpu_load),
        label = stringResource(R.string.sysinfo_mcu_load),
        value = formatLoad(mcu.mcuAwake),
        uDp = uDp,
    )
    DetailRow(
        icon = DinghyIcons.SysInfoMemUsage,
        cd = stringResource(R.string.cd_sysinfo_bandwidth),
        label = stringResource(R.string.sysinfo_mcu_bandwidth),
        value = formatBytes(mcu.bytesWrite, mcu.bytesRead),
        uDp = uDp,
    )
    DetailRow(
        icon = DinghyIcons.Warning,
        cd = stringResource(R.string.cd_sysinfo_bandwidth),
        label = stringResource(R.string.sysinfo_mcu_retransmits),
        value = mcu.bytesRetransmit?.toString() ?: DASH,
        uDp = uDp,
    )
}

/**
 * Action button row for the host SBC.
 *
 * - Reboot / Shutdown: [Intent.Danger] (red — could destroy).
 * - Restart Moonraker: [Intent.Warn] (amber — hazardous-in-process).
 * Buttons are disabled when the relevant in-flight key is set OR [avail] says the action
 * is unavailable for this host's service provider.
 *
 * @param avail       Derived availability flags from [hostActionAvailability].
 * @param inFlightKeys Set of command keys currently awaiting a Moonraker response.
 * @param onAction    Callback receiving the requested [HostAction].
 * @param uDp         Unit grid Dp (passed through but not used for sizing — kept for API symmetry).
 */
@Composable
fun HostActionButtons(
    avail: HostActionAvailability,
    inFlightKeys: Set<String>,
    onAction: (HostAction) -> Unit,
    uDp: Dp,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedControl(
            label = stringResource(R.string.sysinfo_action_reboot),
            onClick = { onAction(HostAction.Reboot) },
            intent = Intent.Danger,
            icon = DinghyIcons.HostReboot,
            enabled = avail.canReboot && "machine_reboot" !in inFlightKeys,
            modifier = Modifier.weight(1f),
        )
        OutlinedControl(
            label = stringResource(R.string.sysinfo_action_shutdown),
            onClick = { onAction(HostAction.Shutdown) },
            intent = Intent.Danger,
            icon = DinghyIcons.SystemRowPower,
            enabled = avail.canShutdown && "machine_shutdown" !in inFlightKeys,
            modifier = Modifier.weight(1f),
        )
        OutlinedControl(
            label = stringResource(R.string.sysinfo_action_restart_moonraker),
            onClick = { onAction(HostAction.RestartMoonraker) },
            intent = Intent.Warn,
            icon = DinghyIcons.RestartService,
            enabled = avail.canRestartMoonraker && "services_restart_moonraker" !in inFlightKeys,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Action button row for the selected MCU.
 *
 * - Firmware Restart: [Intent.Warn] — restarts Klipper firmware on ALL boards, hazardous.
 * - Restart Klipper: [Intent.Warn] — Klipper soft-restart, any active print is lost.
 * Both disabled when the relevant in-flight key is pending.
 *
 * @param inFlightKeys Set of command keys currently awaiting a Moonraker response.
 * @param onAction    Callback receiving the requested [McuAction].
 * @param uDp         Unit grid Dp (API symmetry with [HostActionButtons]).
 */
@Composable
fun McuActionButtons(inFlightKeys: Set<String>, onAction: (McuAction) -> Unit, uDp: Dp) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedControl(
            label = stringResource(R.string.sysinfo_action_firmware_restart),
            onClick = { onAction(McuAction.FirmwareRestart) },
            intent = Intent.Warn,
            icon = DinghyIcons.McuFirmwareRestart,
            enabled = "fw_restart" !in inFlightKeys,
            modifier = Modifier.weight(1f),
        )
        OutlinedControl(
            label = stringResource(R.string.sysinfo_action_restart_klipper),
            onClick = { onAction(McuAction.RestartKlipper) },
            intent = Intent.Warn,
            icon = DinghyIcons.RestartKlipper,
            enabled = "host_restart" !in inFlightKeys,
            modifier = Modifier.weight(1f),
        )
    }
}
