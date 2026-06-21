package works.mees.dinghy.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.config.Profile
import works.mees.dinghy.designsystem.components.FocusEdge
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.components.ListRowLabel
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.ui.route.NavDest

// =============================================================================
// Stateful wrapper — collects from AppContainer, delegates all layout to the
// stateless PrinterSettingsContent seam (WARNING-5 preview convention).
// =============================================================================

/**
 * The **Printer Settings** screen (Task 4.2) — per-printer settings hub for the active printer.
 *
 * Delegates ALL layout to the stateless [PrinterSettingsContent] seam so the @Preview matrix
 * can drive it without a live Moonraker session. The Connection row launches the inline
 * [PrinterConnectionEditor] in-place (BackHandler exits back to the hub).
 *
 * ## Write-scope law ([[dinghy-compose-write-scope-cancellation]])
 * All persistence routes through [AppContainer] intent helpers — process-scoped writeScope only.
 */
@Composable
fun PrinterSettingsScreen(
    container: AppContainer,
    onNavigate: (NavDest) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeProfile by container.activeProfile.collectAsStateWithLifecycle(null)
    val connectionState by container.connectionState.collectAsStateWithLifecycle(ConnectionState.Disconnected)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused

    var editingConnection by remember { mutableStateOf(false) }
    BackHandler(editingConnection) { editingConnection = false }
    if (editingConnection) {
        PrinterConnectionEditor(
            container = container,
            profile = activeProfile,
            onDone = { editingConnection = false },
            modifier = modifier,
        )
        return
    }

    PrinterSettingsContent(
        activeProfile = activeProfile,
        connectionState = connectionState,
        isPrinting = isPrinting,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onConnection = { editingConnection = true },
        onTheme = { onNavigate(NavDest.Theme) },
        onSystemInfo = { onNavigate(NavDest.SystemInfo) },
        onHeatPresets = { onNavigate(NavDest.HeatPresets) },
        onIncrementValues = { onNavigate(NavDest.IncrementValues) },
        onPower = { onNavigate(NavDest.Power) },
        onAdd = { editingConnection = true },
        onBack = onBack,
        modifier = modifier,
    )
}

// =============================================================================
// Stateless content seam (WARNING-5 preview convention) — @Preview targets this.
// =============================================================================

/**
 * Stateless Printer Settings layout.
 *
 * Layout:
 *  - **Focus**: active-printer [FocusFrame] with connection-state ring (same ring-color mapping as
 *    [PrintersContent]), body = displayName + host:port + connection-state label. Empty state shows
 *    the no-printers headline/body from the Printers screen pattern.
 *  - **Field**: [ListBlock] of 1U nav/toggle rows:
 *      1. Connection — → [onConnection]
 *      2. Theme & colors — → [onTheme]
 *      3. System Info — → [onSystemInfo]
 *      4. Power — inert stub, stop-tinted at 0.38f (D-08 parity with [PowerStubRow]).
 *  - **Foot**: single Back button (accent, R5).
 *
 * @param activeProfile    the currently active [Profile], or null (empty state / no printers).
 * @param connectionState  live [ConnectionState] for the active printer.
 * @param isPrinting       whether the printer is currently printing or paused (e-stop morph).
 * @param onEmergencyStop  e-stop callback (wired to shell-level FloatingEStop in FocusFrame).
 * @param onConnection     Connection row click handler.
 * @param onTheme          Theme row click handler.
 * @param onSystemInfo     System Info row click handler.
 * @param onAdd            Add affordance handler in empty state.
 * @param onBack           Back foot button handler.
 */
@Composable
fun PrinterSettingsContent(
    activeProfile: Profile?,
    connectionState: ConnectionState,
    isPrinting: Boolean = false,
    onEmergencyStop: () -> Unit = {},
    onConnection: () -> Unit,
    onTheme: () -> Unit,
    onSystemInfo: () -> Unit,
    onHeatPresets: () -> Unit = {},
    onIncrementValues: () -> Unit = {},
    onPower: () -> Unit = {},
    onAdd: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    // Connection-state ring color — identical mapping to PrintersContent.
    val ringColor: Color? = when (connectionState) {
        ConnectionState.Connected    -> t.accent
        ConnectionState.Connecting   -> t.heat
        ConnectionState.Syncing      -> t.heat
        is ConnectionState.Error     -> t.stop
        ConnectionState.Disconnected -> null
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        ScreenScaffold(
            focus = {
                if (activeProfile != null) {
                    // Active-printer card: lifted from PrintersContent's active-profile FocusFrame.
                    FocusFrame(
                        title = stringResource(R.string.system_row_printer_settings),
                        icon = DinghyIcons.PrinterSettings,
                        uDp = grid.uDp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        edge = ringColor?.let { FocusEdge.Data(it) } ?: FocusEdge.Neutral,
                        isPrinting = isPrinting,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                    ) {
                        Text(
                            text = activeProfile.displayName(),
                            color = t.text,
                            style = DinghyType.focusHeader.toTextStyle(t),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${activeProfile.host}:${activeProfile.port}",
                            color = t.text2,
                            style = DinghyType.dataMeta.toTextStyle(t),
                        )
                        Text(
                            text = stringResource(connectionState.printerSettingsLabelRes()),
                            color = ringColor ?: t.text2,
                            style = DinghyType.caption.toTextStyle(t),
                        )
                    }
                } else {
                    // Empty-state card: mirrors PrintersContent's empty FocusFrame branch.
                    FocusFrame(
                        title = stringResource(R.string.system_row_printer_settings),
                        icon = DinghyIcons.PrinterSettings,
                        uDp = grid.uDp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        isPrinting = isPrinting,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.printers_empty_headline),
                                    color = t.text,
                                    style = DinghyType.focusHeader.toTextStyle(t),
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = stringResource(R.string.printers_empty_body),
                                    color = t.text2,
                                    style = DinghyType.caption.toTextStyle(t),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            },
            field = {
                if (activeProfile != null) {
                    ListBlock(modifier = Modifier.weight(1f)) {
                        // Row 1: Connection
                        item {
                            PrinterSettingsNavRow(
                                icon = DinghyIcons.SystemRowPrinters,
                                label = stringResource(R.string.printer_settings_connection),
                                onClick = onConnection,
                                uDp = grid.uDp,
                            )
                        }

                        // Row 2: Theme & colors
                        item {
                            PrinterSettingsNavRow(
                                icon = DinghyIcons.SystemRowTheme,
                                label = stringResource(R.string.printer_settings_theme),
                                onClick = onTheme,
                                uDp = grid.uDp,
                            )
                        }

                        // Row 3: System Info
                        item {
                            PrinterSettingsNavRow(
                                icon = DinghyIcons.SysInfoTile,
                                label = stringResource(R.string.printer_settings_system_info),
                                onClick = onSystemInfo,
                                uDp = grid.uDp,
                            )
                        }

                        // Row 4: Heat Presets — per-printer preheat preset editor
                        item {
                            PrinterSettingsNavRow(
                                icon = DinghyIcons.TempPresets,
                                label = stringResource(R.string.heat_presets_title),
                                onClick = onHeatPresets,
                                uDp = grid.uDp,
                            )
                        }

                        // Row 4b: Increment Values — per-printer step-selector value lists
                        item {
                            PrinterSettingsNavRow(
                                icon = DinghyIcons.FineTune,
                                label = stringResource(R.string.increment_values_title),
                                onClick = onIncrementValues,
                                uDp = grid.uDp,
                            )
                        }

                        // Row 5: Power / Reset — navigates to the Power/Reset page (Task A)
                        item {
                            PrinterSettingsNavRow(
                                icon = DinghyIcons.SystemRowPower,
                                label = stringResource(R.string.printer_settings_power),
                                onClick = onPower,
                                uDp = grid.uDp,
                            )
                        }

                    }
                } else {
                    // Empty state: only an Add affordance.
                    ListBlock(modifier = Modifier.weight(1f)) {
                        item {
                            ListRow(
                                selected = false,
                                onClick = onAdd,
                                uDp = grid.uDp,
                                leadingContent = {
                                    ListRowIcon(
                                        icon = DinghyIcons.ManagePrinters,
                                        uDp = grid.uDp,
                                        tint = t.text,
                                    )
                                },
                            ) {
                                ListRowLabel(stringResource(R.string.printers_add))
                            }
                        }
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

// =============================================================================
// Private sub-composables
// =============================================================================

/** Standard 1U nav-row (leading icon + label, direct-tap → onClick). */
@Composable
private fun PrinterSettingsNavRow(
    icon: DinghyIcon,
    label: String,
    onClick: () -> Unit,
    uDp: Dp,
) {
    val t = LocalTokens.current
    ListRow(
        selected = false,
        onClick = onClick,
        uDp = uDp,
        leadingContent = {
            ListRowIcon(
                icon = icon,
                uDp = uDp,
                tint = t.text,
            )
        },
    ) {
        ListRowLabel(label)
    }
}


// =============================================================================
// Connection-state label resource (private to this file)
// =============================================================================

/**
 * The string resource for a connection state's human label — same mapping as
 * [PrintersScreen]'s private `labelRes()` extension.
 */
private fun ConnectionState.printerSettingsLabelRes(): Int = when (this) {
    ConnectionState.Connected    -> R.string.conn_state_connected
    ConnectionState.Connecting   -> R.string.conn_state_connecting
    ConnectionState.Syncing      -> R.string.conn_state_syncing
    is ConnectionState.Error     -> R.string.conn_state_error
    ConnectionState.Disconnected -> R.string.conn_state_disconnected
}
