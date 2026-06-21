package works.mees.dinghy.ui.systeminfo

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.systeminfo.Device
import works.mees.dinghy.systeminfo.HostDevice
import works.mees.dinghy.systeminfo.McuDevice
import works.mees.dinghy.systeminfo.ProcStatLive
import works.mees.dinghy.systeminfo.ProcStatQuery
import works.mees.dinghy.systeminfo.SystemInfo
import works.mees.dinghy.systeminfo.SystemInfoHolder
import works.mees.dinghy.systeminfo.decodeThrottleConditions
import works.mees.dinghy.systeminfo.formatCpuLoad
import works.mees.dinghy.systeminfo.formatLoad
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle

/**
 * The **System Information** screen rebuilt as a read-only device browser.
 *
 * ## Layout
 * Focus = selected device detail (scrollable text block only — no action buttons).
 * Field = device list (host first, then enumerated MCUs).
 * Foot = Back.
 *
 * Actions (reboot / shutdown / service restarts) have moved to the Power / Reset page
 * ([works.mees.dinghy.ui.screen.PowerResetScreen]), reached from Printer Settings.
 *
 * ## State
 * Stateful entry [SystemInformationScreen] collects holder flows, runs a LaunchedEffect retry loop
 * calling [SystemInfoHolder.ensureLoaded] while MCUs haven't loaded yet (capabilities may not have
 * populated on first call), and a screen-scoped ~2 s MCU stats poll while boards are non-empty.
 *
 * @param holder the per-session [SystemInfoHolder] off AppContainer. Null while idle — the page
 *   then renders the all-"—" degraded host-only state.
 * @param onBack the neutral Back exit.
 * @param isPrinting whether a print is currently active (drives FocusFrame e-stop morph).
 * @param onEmergencyStop the e-stop callback forwarded into FocusFrame.
 */
@Composable
fun SystemInformationScreen(
    holder: SystemInfoHolder?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
) {
    val identity by (holder?.identity ?: nullStateFlow()).collectAsStateWithLifecycle()
    val procStats by (holder?.procStats ?: nullStateFlow()).collectAsStateWithLifecycle()
    val live by (holder?.live ?: nullStateFlow()).collectAsStateWithLifecycle()
    val mcus by (holder?.mcuDevices ?: nullStateFlow()).collectAsStateWithLifecycle()
    val klipperVersion by (holder?.klipperVersion ?: nullStateFlow()).collectAsStateWithLifecycle()
    val moonrakerVersion by (holder?.moonrakerVersion ?: nullStateFlow()).collectAsStateWithLifecycle()

    // Lazy load + retry while MCUs haven't loaded (capabilities may not have populated on first call).
    LaunchedEffect(holder) {
        val h = holder ?: return@LaunchedEffect
        repeat(10) {
            h.ensureLoaded()
            if (h.mcuDevices.value != null) return@LaunchedEffect
            delay(500)
        }
    }
    // Screen-scoped live poll of MCU stats (~2 s) — stops when the screen leaves the composition.
    LaunchedEffect(holder, mcus?.isNotEmpty()) {
        if (holder != null && !mcus.isNullOrEmpty()) {
            while (true) { delay(2000); holder.refreshMcuStats() }
        }
    }

    SystemInformationContent(
        identity = identity, procStats = procStats, live = live, mcus = mcus,
        klipperVersion = klipperVersion, moonrakerVersion = moonrakerVersion,
        isPrinting = isPrinting, onEmergencyStop = onEmergencyStop,
        onBack = onBack, modifier = modifier,
    )
}

/** A reusable null StateFlow for the idle/no-holder path (no allocation per row). */
private fun <T> nullStateFlow(): kotlinx.coroutines.flow.StateFlow<T?> =
    kotlinx.coroutines.flow.MutableStateFlow(null)

/**
 * Builds the ordered device list: host first, then MCUs (if loaded).
 *
 * The host entry is always present. MCUs are omitted when [mcus] is null (not yet loaded) — the
 * Field will show only the host row until [SystemInfoHolder.ensureLoaded] populates them.
 */
internal fun buildDeviceList(
    host: HostDevice,
    mcus: List<McuDevice>?,
): List<Device> = buildList {
    add(host)
    if (mcus != null) addAll(mcus)
}

/**
 * The STATELESS content seam (PREVIEW_AND_TOKENS preview-first LAW) — pure inputs, no holder/
 * Moonraker, so the `@Preview` matrix in [works.mees.dinghy.preview.SysInfoPreviews] drives every
 * theme + degrade state without a live session.
 *
 * Owns selection state. Selection defaults to [HostDevice.HOST_KEY] and is restored across
 * recompositions via [rememberSaveable].
 *
 * @param initialSelectedKey the key that should be selected on first composition. Defaults to
 *   [HostDevice.HOST_KEY] (no change to runtime behavior). Pass a real MCU key in `@Preview`
 *   fixtures so MCU-selected previews render [McuDetail] rather than the host.
 */
@Composable
fun SystemInformationContent(
    identity: SystemInfo?,
    procStats: ProcStatQuery?,
    live: ProcStatLive?,
    mcus: List<McuDevice>?,
    klipperVersion: String?,
    moonrakerVersion: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
    initialSelectedKey: String = HostDevice.HOST_KEY,
) {
    val hostName = identity?.model ?: identity?.distroName ?: stringResource(R.string.sysinfo_device_host)
    val host = HostDevice(hostName, identity, procStats, live, klipperVersion, moonrakerVersion)
    val devices = buildDeviceList(host, mcus)

    var selectedKey by rememberSaveable { mutableStateOf(initialSelectedKey) }
    val selected = devices.firstOrNull { it.key == selectedKey } ?: host

    val throttle = decodeThrottleConditions(procStats?.throttledState)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = selected.displayName,
                    icon = deviceIcon(selected),
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxSize(),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    when (val d = selected) {
                        is HostDevice -> HostDetail(
                            host = d,
                            throttle = throttle,
                            uDp = grid.uDp,
                        )
                        is McuDevice -> McuDetail(
                            mcu = d,
                            uDp = grid.uDp,
                        )
                    }
                }
            },
            field = {
                ListBlock(modifier = Modifier.weight(1f)) {
                    items(devices, key = { it.key }) { device ->
                        DeviceRow(
                            device = device,
                            selected = device.key == selectedKey,
                            onClick = { selectedKey = device.key },
                            uDp = grid.uDp,
                        )
                    }
                }
                FootButtonBar(
                    uDp = grid.uDp,
                    actions = listOf(
                        FootAction(
                            label = stringResource(R.string.common_back),
                            icon = DinghyIcons.Back,
                            onClick = onBack,
                            intent = Intent.Accent,
                        ),
                    ),
                )
            },
        )
    }
}

private fun deviceIcon(device: Device): DinghyIcon = when (device) {
    is HostDevice -> DinghyIcons.SysInfoHost
    is McuDevice -> DinghyIcons.McuDevice
}

@Composable
private fun DeviceRow(device: Device, selected: Boolean, onClick: () -> Unit, uDp: Dp) {
    val t = LocalTokens.current
    ListRow(
        selected = selected,
        onClick = onClick,
        uDp = uDp,
        leadingContent = {
            ListRowIcon(
                icon = deviceIcon(device),
                uDp = uDp,
                tint = if (selected) t.accent else t.text2,
                contentDescription = when (device) {
                    is HostDevice -> stringResource(R.string.cd_sysinfo_host)
                    is McuDevice -> stringResource(R.string.cd_sysinfo_device_mcu)
                },
            )
        },
    ) {
        Text(device.displayName, color = t.text, style = DinghyType.listLabel.toTextStyle(t))
        Spacer(Modifier.weight(1f))
        Text(deviceGlance(device), color = t.text2, style = DinghyType.dataMeta.toTextStyle(t))
    }
}

private fun deviceGlance(device: Device): String = when (device) {
    is HostDevice -> formatCpuLoad(device.live?.cpuLoadPercent)
    is McuDevice -> formatLoad(device.mcuAwake)
}
