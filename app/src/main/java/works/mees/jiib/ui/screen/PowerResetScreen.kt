package works.mees.jiib.ui.screen

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.jiib.R
import works.mees.jiib.command.CommandDispatcher
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.ServiceRestartArgs
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.ConfirmGuard
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.ListRowIcon
import works.mees.jiib.designsystem.components.ListRowLabel
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.focus.FocusExplainer
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.systeminfo.HostActionAvailability
import works.mees.jiib.systeminfo.SystemInfo
import works.mees.jiib.systeminfo.SystemInfoHolder
import works.mees.jiib.systeminfo.hostActionAvailability
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// =============================================================================
// Data model — self-contained, no dependency on SystemInfo HostAction/McuAction
// =============================================================================

/**
 * The intent classification for a power/reset command row.
 *
 * [Danger] = red (`t.stop`) — destructive host power actions.
 * [Warn]   = amber (`t.heat`) — hazardous-but-in-process restarts.
 */
enum class PowerRowIntent { Danger, Warn }

/**
 * A single entry in the Power / Reset command list.
 *
 * Carries the stable [key] (matches the CommandDispatcher in-flight key), the display [label]
 * string, the [icon], the [intent] for icon/label tinting, the [enabled] predicate result, an
 * optional [disabledReason] shown as a muted caption when disabled, and the confirm-dialog copy
 * ([confirmTitleRes]/[confirmMsgRes]/[isDestructive]).
 *
 * The dispatch lambda is stored separately in [dispatchPowerCommand] so the model itself is pure/
 * testable without a live [CommandDispatcher].
 */
data class PowerCommand(
    val key: String,
    val label: String,
    val icon: JiibIcon,
    val intent: PowerRowIntent,
    val enabled: Boolean,
    val disabledReason: String?,
    val confirmTitleRes: Int,
    val confirmMsgRes: Int,
    val isDestructive: Boolean,
)

// =============================================================================
// Pure row builder — testable without Android context
// =============================================================================

/**
 * Builds the ordered list of [PowerCommand] rows from [avail] (host-action availability) and the
 * current [inFlightKeys] set (disables a row while its dispatch is in progress).
 *
 * Order: Reboot → Shutdown → Restart Moonraker → Firmware Restart → Restart Klipper.
 * Host rows (reboot/shutdown/restart-moonraker) are gated by [avail]; firmware/klipper are always
 * availability-enabled (but still disabled while in-flight).
 *
 * Label strings are resolved by the composable caller via [stringResource] and passed in to keep
 * this function pure (no Android context dependency — host-testable).
 *
 * @param avail                  derived from [hostActionAvailability]; drives enabled state for host rows.
 * @param inFlightKeys           the live in-flight dispatch key set; a row is disabled while its key is present.
 * @param labelReboot            resolved label string for Reboot.
 * @param labelShutdown          resolved label string for Shutdown.
 * @param labelRestartMoonraker  resolved label string for Restart Moonraker.
 * @param labelFirmwareRestart   resolved label string for Firmware Restart.
 * @param labelRestartKlipper    resolved label string for Restart Klipper.
 */
fun powerResetRows(
    avail: HostActionAvailability,
    inFlightKeys: Set<String>,
    labelReboot: String,
    labelShutdown: String,
    labelRestartMoonraker: String,
    labelFirmwareRestart: String,
    labelRestartKlipper: String,
): List<PowerCommand> = listOf(
    PowerCommand(
        key = "machine_reboot",
        label = labelReboot,
        icon = JiibIcons.HostReboot,
        intent = PowerRowIntent.Danger,
        enabled = avail.canReboot && "machine_reboot" !in inFlightKeys,
        disabledReason = if (!avail.canReboot) avail.powerDisabledReason else null,
        confirmTitleRes = R.string.sysinfo_confirm_reboot_title,
        confirmMsgRes = R.string.sysinfo_confirm_reboot_msg,
        isDestructive = true,
    ),
    PowerCommand(
        key = "machine_shutdown",
        label = labelShutdown,
        icon = JiibIcons.SystemRowPower,
        intent = PowerRowIntent.Danger,
        enabled = avail.canShutdown && "machine_shutdown" !in inFlightKeys,
        disabledReason = if (!avail.canShutdown) avail.powerDisabledReason else null,
        confirmTitleRes = R.string.sysinfo_confirm_shutdown_title,
        confirmMsgRes = R.string.sysinfo_confirm_shutdown_msg,
        isDestructive = true,
    ),
    PowerCommand(
        key = "services_restart_moonraker",
        label = labelRestartMoonraker,
        icon = JiibIcons.RestartService,
        intent = PowerRowIntent.Warn,
        enabled = avail.canRestartMoonraker && "services_restart_moonraker" !in inFlightKeys,
        disabledReason = if (!avail.canRestartMoonraker) avail.moonrakerDisabledReason else null,
        confirmTitleRes = R.string.sysinfo_confirm_restart_moonraker_title,
        confirmMsgRes = R.string.sysinfo_confirm_restart_moonraker_msg,
        isDestructive = false,
    ),
    PowerCommand(
        key = "fw_restart",
        label = labelFirmwareRestart,
        icon = JiibIcons.McuFirmwareRestart,
        intent = PowerRowIntent.Warn,
        enabled = "fw_restart" !in inFlightKeys,
        disabledReason = null,
        confirmTitleRes = R.string.sysinfo_confirm_firmware_restart_title,
        confirmMsgRes = R.string.sysinfo_confirm_firmware_restart_msg,
        isDestructive = false,
    ),
    PowerCommand(
        key = "host_restart",
        label = labelRestartKlipper,
        icon = JiibIcons.RestartKlipper,
        intent = PowerRowIntent.Warn,
        enabled = "host_restart" !in inFlightKeys,
        disabledReason = null,
        confirmTitleRes = R.string.sysinfo_confirm_restart_klipper_title,
        confirmMsgRes = R.string.sysinfo_confirm_restart_klipper_msg,
        isDestructive = false,
    ),
)

// =============================================================================
// Dispatch helper — maps a PowerCommand to its real CommandDispatcher call
// =============================================================================

/**
 * Dispatches the [CommandDispatcher] call that matches the given [cmd]'s key.
 *
 * Self-contained: no dependency on SystemInfo's HostAction/McuAction.
 */
fun dispatchPowerCommand(d: CommandDispatcher, cmd: PowerCommand) {
    when (cmd.key) {
        "machine_reboot"             -> d.dispatch(CommandRegistry.machineReboot, Unit)
        "machine_shutdown"           -> d.dispatch(CommandRegistry.machineShutdown, Unit)
        "services_restart_moonraker" -> d.dispatch(CommandRegistry.restartService, ServiceRestartArgs("moonraker"))
        "fw_restart"                 -> d.dispatch(CommandRegistry.firmwareRestart, Unit)
        "host_restart"               -> d.dispatch(CommandRegistry.restart, Unit)
    }
}

// =============================================================================
// Stateful screen — collects from holder + dispatcher
// =============================================================================

/**
 * The **Power / Reset** screen (Task A) — reached from Printer Settings, provides
 * host reboot/shutdown/restart and MCU/Klipper restart commands with confirmation guards.
 *
 * Stateful entry: collects [holder?.identity] for availability and [dispatcher?.inFlight] for
 * in-flight gating, then delegates all layout to the stateless [PowerResetContent] seam.
 *
 * @param holder     the per-session [SystemInfoHolder] (null while idle — identity = null → all host rows
 *                   remain enabled per [hostActionAvailability]'s conservative default).
 * @param dispatcher the session [CommandDispatcher] for dispatching actions. Null while idle → no dispatch.
 * @param isPrinting whether a print is currently active (drives FocusFrame e-stop morph).
 * @param onEmergencyStop the e-stop callback forwarded into FocusFrame.
 * @param onBack     Back foot button handler.
 */
@Composable
fun PowerResetScreen(
    holder: SystemInfoHolder?,
    dispatcher: CommandDispatcher?,
    isPrinting: Boolean,
    onEmergencyStop: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val identity by (holder?.identity ?: remember { MutableStateFlow<SystemInfo?>(null) })
        .collectAsStateWithLifecycle()
    val inFlightKeys by (dispatcher?.inFlight ?: remember { MutableStateFlow(emptySet<String>()) })
        .collectAsStateWithLifecycle()

    PowerResetContent(
        identity = identity,
        inFlightKeys = inFlightKeys,
        onCommand = { cmd -> dispatcher?.let { d -> dispatchPowerCommand(d, cmd) } },
        isPrinting = isPrinting,
        onEmergencyStop = onEmergencyStop,
        onBack = onBack,
        modifier = modifier,
    )
}

// =============================================================================
// Stateless content seam — @Preview targets this
// =============================================================================

/**
 * Stateless Power / Reset layout (preview seam).
 *
 * Layout:
 *  - **Focus**: [FocusFrame] with title "Power / Reset" + [JiibIcons.SystemRowPower] + blurb.
 *    FocusFrame docks the e-stop per the header law (page reachable mid-print).
 *  - **Field**: [ListBlock] of 5 command rows, each tinted to its [PowerRowIntent] color.
 *    Disabled rows are dimmed + show the reason caption.
 *    Tapping an enabled row sets [pendingCommand]; a [ConfirmGuard] overlay renders until
 *    confirmed (dispatches + clears) or cancelled.
 *  - **Foot**: single Back button (Intent.Accent).
 *
 * Intent → token mapping: [PowerRowIntent.Danger] → `t.stop`, [PowerRowIntent.Warn] → `t.heat`.
 * NO inline fontFamily/fontSize — all type via [JiibType] roles (FontConformanceTest law).
 *
 * @param identity    current [SystemInfo] (null = no handshake yet → conservative enable).
 * @param inFlightKeys live in-flight dispatch key set.
 * @param onCommand   invoked with the confirmed [PowerCommand] to dispatch.
 * @param isPrinting  drives FocusFrame e-stop morph.
 * @param onEmergencyStop e-stop callback.
 * @param onBack      Back foot button.
 */
@Composable
fun PowerResetContent(
    identity: SystemInfo?,
    inFlightKeys: Set<String>,
    onCommand: (PowerCommand) -> Unit,
    isPrinting: Boolean = false,
    onEmergencyStop: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = powerResetRows(
        avail = hostActionAvailability(identity),
        inFlightKeys = inFlightKeys,
        labelReboot = stringResource(R.string.sysinfo_action_reboot),
        labelShutdown = stringResource(R.string.sysinfo_action_shutdown),
        labelRestartMoonraker = stringResource(R.string.sysinfo_action_restart_moonraker),
        labelFirmwareRestart = stringResource(R.string.sysinfo_action_firmware_restart),
        labelRestartKlipper = stringResource(R.string.sysinfo_action_restart_klipper),
    )

    var pendingCommand by remember { mutableStateOf<PowerCommand?>(null) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = stringResource(R.string.power_reset_title),
                    icon = JiibIcons.SystemRowPower,
                    uDp = grid.uDp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    FocusExplainer(text = stringResource(R.string.power_reset_blurb))
                }
            },
            field = {
                ListBlock(modifier = Modifier.weight(1f)) {
                    rows.forEach { cmd ->
                        item(key = cmd.key) {
                            PowerCommandRow(
                                cmd = cmd,
                                onTap = { if (cmd.enabled) pendingCommand = cmd },
                                uDp = grid.uDp,
                            )
                        }
                    }
                }
                FootButtonBar(
                    uDp = grid.uDp,
                    actions = listOf(
                        FootAction(
                            label = stringResource(R.string.common_back),
                            icon = JiibIcons.Back,
                            onClick = onBack,
                            intent = Intent.Accent,
                        ),
                    ),
                )
            },
        )

        // Confirm guard overlay — full-bleed, rendered above ScreenScaffold.
        pendingCommand?.let { cmd ->
            ConfirmGuard(
                title = stringResource(cmd.confirmTitleRes),
                message = stringResource(cmd.confirmMsgRes),
                confirmLabel = stringResource(R.string.sysinfo_confirm_common),
                onConfirm = { onCommand(cmd); pendingCommand = null },
                onCancel = { pendingCommand = null },
                destructive = cmd.isDestructive,
                warn = !cmd.isDestructive,
            )
        }
    }
}

// =============================================================================
// Private sub-composable — one command row
// =============================================================================

@Composable
private fun PowerCommandRow(
    cmd: PowerCommand,
    onTap: () -> Unit,
    uDp: Dp,
) {
    val t = LocalTokens.current
    val intentColor = when (cmd.intent) {
        PowerRowIntent.Danger -> t.stop
        PowerRowIntent.Warn   -> t.heat
    }
    val iconColor = if (cmd.enabled) intentColor else intentColor.copy(alpha = 0.38f)
    val labelColor = if (cmd.enabled) t.text else t.text2.copy(alpha = 0.38f)

    ListRow(
        selected = false,
        onClick = onTap,
        uDp = uDp,
        leadingContent = {
            ListRowIcon(
                icon = cmd.icon,
                uDp = uDp,
                tint = iconColor,
                contentDescription = cmd.label,
            )
        },
    ) {
        Column {
            ListRowLabel(cmd.label, color = labelColor)
            if (!cmd.enabled && cmd.disabledReason != null) {
                Text(
                    text = cmd.disabledReason,
                    color = t.text2.copy(alpha = 0.5f),
                    style = JiibType.caption.toTextStyle(t),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
