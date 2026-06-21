package works.mees.dinghy.ui.systeminfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.systeminfo.HostActionAvailability
import works.mees.dinghy.systeminfo.HostDevice
import works.mees.dinghy.systeminfo.McuDevice
import works.mees.dinghy.systeminfo.hostDetailLines
import works.mees.dinghy.systeminfo.mcuDetailLines
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
 * Focus detail body for the selected host SBC device.
 *
 * Renders a **scrollable plain-text block** (no icons, no per-row labels) followed by the
 * [HostActionButtons] docked below the scroll region. The FocusFrame title carries the device
 * name — it is NOT repeated in the body.
 *
 * Lines are built by [hostDetailLines] (pure, unit-tested). Each null/missing value segment is
 * gracefully dropped; if a whole conceptual line has no data it is omitted or rendered as "—".
 *
 * @param host     The host device model (identity + live fields + version strings).
 * @param throttle Decoded throttle condition labels from [decodeThrottleConditions]; empty = off-Pi.
 * @param avail    Derived host action availability flags.
 * @param inFlightKeys Set of command keys currently awaiting a Moonraker response.
 * @param onAction Callback receiving the requested [HostAction].
 * @param uDp      The unit grid Dp from [rememberUnitGrid] / [LocalUnitDp].
 */
@Composable
fun ColumnScope.HostDetail(
    host: HostDevice,
    throttle: List<String>,
    avail: HostActionAvailability,
    inFlightKeys: Set<String>,
    onAction: (HostAction) -> Unit,
    uDp: Dp,
) {
    val t = LocalTokens.current
    val lines = hostDetailLines(host, throttle)

    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = DinghyType.dataInline.toTextStyle(t),
                color = t.text,
            )
        }
    }

    HostActionButtons(avail, inFlightKeys, onAction, uDp)
}

/**
 * Focus detail body for the selected MCU device.
 *
 * Renders a **scrollable plain-text block** (no icons, no per-row labels) followed by the
 * [McuActionButtons] docked below. The FocusFrame title carries the device name — not repeated.
 *
 * Lines are built by [mcuDetailLines] (pure, unit-tested). Sparse MCU data (CAN boards report a
 * different subset than the mainboard — SYS-04 degrade) gracefully drops null segments.
 *
 * @param mcu  The MCU device model from the device browser state.
 * @param inFlightKeys Set of command keys currently awaiting a Moonraker response.
 * @param onAction Callback receiving the requested [McuAction].
 * @param uDp  The unit grid Dp.
 */
@Composable
fun ColumnScope.McuDetail(
    mcu: McuDevice,
    inFlightKeys: Set<String>,
    onAction: (McuAction) -> Unit,
    uDp: Dp,
) {
    val t = LocalTokens.current
    val lines = mcuDetailLines(mcu)

    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = DinghyType.dataInline.toTextStyle(t),
                color = t.text,
            )
        }
    }

    McuActionButtons(inFlightKeys, onAction, uDp)
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
