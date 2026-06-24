package works.mees.dinghy.ui.screen

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.config.ConnectionConfig
import works.mees.dinghy.config.DiscoveredPrinter
import works.mees.dinghy.config.Profile
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle

/** Bounded settle window for the mDNS scan (lifted from the old editor Find panel). */
private const val FIND_SCAN_WINDOW_MS = 6000L

/**
 * The Find-on-network takeover. Auto-scans for Moonraker instances on open, lets the user pick one,
 * probes it (no API key), and reports: a clean connect → [onAddAndConnect] (caller persists +
 * activates), or a failed connect → [onNeedsEditor] (caller opens the editor pre-filled). Owns probe
 * cancellation so a stale probe can't fire after Back. The foot Scan button re-scans.
 */
@Composable
fun PrinterFindScreen(
    container: AppContainer,
    onAddAndConnect: (Profile) -> Unit,
    onNeedsEditor: (ConnectionSeed) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused

    var scanRequest by remember { mutableStateOf(1) } // auto-scan on open; Scan button re-scans
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    var discovered by remember { mutableStateOf<List<DiscoveredPrinter>>(emptyList()) }
    var probingHost by remember { mutableStateOf<String?>(null) }
    var probeJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(scanRequest) {
        scanning = true; scanned = false; discovered = emptyList()
        try {
            withTimeoutOrNull(FIND_SCAN_WINDOW_MS) {
                container.discovery.discover().collect { p ->
                    if (discovered.none { it.host == p.host && it.port == p.port }) discovered = discovered + p
                }
            }
        } finally { scanning = false; scanned = true }
    }
    DisposableEffect(Unit) { onDispose { probeJob?.cancel() } }

    PrinterFindContent(
        scanning = scanning,
        scanned = scanned,
        discovered = discovered,
        probingHost = probingHost,
        isPrinting = isPrinting,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onScan = { if (!scanning) scanRequest++ },
        onPick = { printer ->
            probeJob?.cancel()
            probingHost = printer.host
            val config = ConnectionConfig(
                host = printer.host, port = printer.port, apiKey = null,
                useSecure = false, advancedUrl = null,
            )
            probeJob = container.runConnectionProbe(config) { result ->
                probingHost = null
                probeJob = null
                when (findPickDecision(result)) {
                    FindPickEffect.AddAndConnect -> onAddAndConnect(
                        buildProfileFromConnectionEditorSave(
                            existing = null, nameInput = printer.name,
                            host = printer.host, port = printer.port,
                            apiKeyInput = "", keyCleared = false, advancedUrlInput = "",
                        ),
                    )
                    FindPickEffect.OpenEditorSeeded ->
                        onNeedsEditor(ConnectionSeed(printer.host, printer.port))
                }
            }
        },
        onBack = onBack,
        modifier = modifier,
    )
}

/** Stateless Find layout — the `@Preview` matrix targets this. */
@Composable
fun PrinterFindContent(
    scanning: Boolean,
    scanned: Boolean,
    discovered: List<DiscoveredPrinter>,
    probingHost: String?,
    isPrinting: Boolean = false,
    onEmergencyStop: () -> Unit = {},
    onScan: () -> Unit,
    onPick: (DiscoveredPrinter) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        val uDp = grid.uDp
        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = stringResource(R.string.conn_row_find),
                    icon = DinghyIcons.Search,
                    uDp = uDp,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    Column(Modifier.fillMaxSize()) {
                        // Auto-scan on open means the pre-scan hint is never the initial state; the
                        // first emission is "Scanning…". Status order: probing > scanning > result.
                        val status = when {
                            probingHost != null -> stringResource(R.string.printers_find_testing)
                            scanning -> stringResource(R.string.printers_scanning)
                            scanned && discovered.isEmpty() -> stringResource(R.string.printers_scan_none_found)
                            else -> stringResource(R.string.printers_pick_found)
                        }
                        Text(text = status, color = t.text2, style = DinghyType.body.toTextStyle(t))
                    }
                }
            },
            field = {
                ListBlock(modifier = Modifier.weight(1f)) {
                    items(discovered, key = { "${it.host}:${it.port}" }) { p ->
                        ListRow(
                            selected = false,
                            onClick = { onPick(p) },
                            uDp = uDp,
                            leadingContent = { ListRowIcon(DinghyIcons.SysInfoCpu, uDp, t.text) },
                            trailingContent = {
                                Text(p.port.toString(), color = t.text2, style = DinghyType.dataMeta.toTextStyle(t))
                            },
                        ) {
                            Text(
                                text = p.name.ifBlank { p.host },
                                color = t.text,
                                style = DinghyType.listLabel.toTextStyle(t),
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                FootButtonBar(
                    uDp = uDp,
                    actions = listOf(
                        FootAction(stringResource(R.string.common_back), DinghyIcons.Back, onBack, Intent.Accent),
                        FootAction(
                            label = if (scanning) stringResource(R.string.printers_scanning)
                                    else stringResource(R.string.conn_scan),
                            icon = DinghyIcons.Search,
                            onClick = onScan,
                            intent = Intent.Accent,
                            enabled = !scanning,
                        ),
                    ),
                )
            },
        )
    }
}
