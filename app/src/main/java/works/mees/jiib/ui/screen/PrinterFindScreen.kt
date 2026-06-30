package works.mees.jiib.ui.screen

import androidx.compose.foundation.layout.BoxWithConstraints
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
import works.mees.jiib.R
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.dispatch
import works.mees.jiib.config.ConnectionConfig
import works.mees.jiib.config.DiscoveredPrinter
import works.mees.jiib.config.Profile
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.FocusText
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

/** Bounded settle window for the mDNS scan — 8s gives serialized resolves time to drain (multiple
 *  printers resolve one-at-a-time through [ResolveSerializer]). */
private const val FIND_SCAN_WINDOW_MS = 8000L

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
                    icon = JiibIcons.Search,
                    uDp = uDp,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    // Auto-scan on open means the pre-scan hint is never the initial state; the
                    // first emission is "Scanning…". Status order: probing > scanning > result.
                    val status = when {
                        probingHost != null -> stringResource(R.string.printers_find_testing)
                        scanning -> stringResource(R.string.printers_scanning)
                        scanned && discovered.isEmpty() -> stringResource(R.string.printers_scan_none_found)
                        else -> stringResource(R.string.printers_pick_found)
                    }
                    FocusText(text = status, role = JiibType.body, t = t, color = t.text2, modifier = Modifier.fillMaxWidth(), maxHeightU = 2f)
                }
            },
            field = {
                ListBlock(modifier = Modifier.weight(1f)) {
                    items(discovered, key = { "${it.host}:${it.port}" }) { p ->
                        // Show only the hostname (if the mDNS advertisement carried a real one) + the
                        // IP — no icon, no port (owner 2026-06-24). The "moonraker @ " mDNS prefix is
                        // stripped (redundant — Moonraker is all we connect to); a name that reduces to
                        // nothing or to the IP itself is treated as no hostname → the IP is the whole row.
                        val hostname = discoveredHostname(p.name).takeIf { it.isNotBlank() && it != p.host }
                        val hasHostname = hostname != null
                        ListRow(
                            selected = false,
                            onClick = { onPick(p) },
                            uDp = uDp,
                            trailingContent = if (hasHostname) {
                                { Text(p.host, color = t.text2, style = JiibType.dataMeta.toTextStyle(t), maxLines = 1) }
                            } else {
                                null
                            },
                        ) {
                            Text(
                                text = hostname ?: p.host,
                                color = t.text,
                                style = JiibType.listLabel.toTextStyle(t),
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                FootButtonBar(
                    uDp = uDp,
                    actions = listOf(
                        FootAction(stringResource(R.string.common_back), JiibIcons.Back, onBack, Intent.Accent),
                        FootAction(
                            label = if (scanning) stringResource(R.string.printers_scanning)
                                    else stringResource(R.string.conn_scan),
                            icon = JiibIcons.Search,
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
