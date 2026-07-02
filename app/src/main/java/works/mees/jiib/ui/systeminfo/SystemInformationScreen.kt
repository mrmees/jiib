package works.mees.jiib.ui.systeminfo

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import works.mees.jiib.R
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.ListRowIcon
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.systeminfo.Device
import works.mees.jiib.systeminfo.HostDevice
import works.mees.jiib.systeminfo.McuDevice
import works.mees.jiib.systeminfo.ProcStatLive
import works.mees.jiib.systeminfo.ProcStatQuery
import works.mees.jiib.systeminfo.SystemInfo
import works.mees.jiib.systeminfo.SystemInfoHolder
import works.mees.jiib.systeminfo.decodeThrottleConditions
import works.mees.jiib.systeminfo.formatCpuLoad
import works.mees.jiib.systeminfo.formatLoad
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

/**
 * The **System Information** screen rebuilt as a read-only device browser.
 *
 * ## Layout
 * Focus = selected device detail (scrollable text block only — no action buttons).
 * Field = device list (host first, then enumerated MCUs).
 * Foot = Back.
 *
 * Actions (reboot / shutdown / service restarts) have moved to the Power / Reset page
 * ([works.mees.jiib.ui.screen.PowerResetScreen]), reached from Printer Settings.
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
 * Moonraker, so the `@Preview` matrix in [works.mees.jiib.preview.SysInfoPreviews] drives every
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
                    contentInset = 0.dp,
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    when (val d = selected) {
                        is HostDevice -> HostDetail(host = d, throttle = throttle)
                        is McuDevice -> McuDetail(mcu = d)
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
                            icon = JiibIcons.Back,
                            onClick = onBack,
                            intent = Intent.Accent,
                        ),
                    ),
                )
            },
        )
    }
}

private fun deviceIcon(device: Device): JiibIcon = when (device) {
    is HostDevice -> JiibIcons.SysInfoHost
    is McuDevice -> JiibIcons.McuDevice
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
        Text(device.displayName, color = t.text, style = JiibType.listLabel.toTextStyle(t), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Text(deviceGlance(device), color = t.text2, style = JiibType.dataMeta.toTextStyle(t), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun deviceGlance(device: Device): String = when (device) {
    is HostDevice -> formatCpuLoad(device.live?.cpuLoadPercent)
    is McuDevice -> formatLoad(device.mcuAwake)
}
