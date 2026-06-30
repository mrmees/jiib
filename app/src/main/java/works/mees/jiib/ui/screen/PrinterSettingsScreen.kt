package works.mees.jiib.ui.screen

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
import works.mees.jiib.R
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.dispatch
import works.mees.jiib.config.Profile
import works.mees.jiib.designsystem.components.FocusEdge
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.ListRowIcon
import works.mees.jiib.designsystem.components.ListRowLabel
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.ConnectionState
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.FocusText
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.ui.route.NavDest

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
                        icon = JiibIcons.PrinterSettings,
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
                            style = JiibType.focusHeader.toTextStyle(t),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${activeProfile.host}:${activeProfile.port}",
                            color = t.text2,
                            style = JiibType.dataMeta.toTextStyle(t),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(connectionState.printerSettingsLabelRes()),
                            color = ringColor ?: t.text2,
                            style = JiibType.caption.toTextStyle(t),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    // Empty-state card: mirrors PrintersContent's empty FocusFrame branch.
                    FocusFrame(
                        title = stringResource(R.string.system_row_printer_settings),
                        icon = JiibIcons.PrinterSettings,
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
                                    style = JiibType.focusHeader.toTextStyle(t),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                FocusText(
                                    text = stringResource(R.string.printers_empty_body),
                                    role = JiibType.caption,
                                    t = t,
                                    color = t.text2,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
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
                                icon = JiibIcons.SystemRowPrinters,
                                label = stringResource(R.string.printer_settings_connection),
                                onClick = onConnection,
                                uDp = grid.uDp,
                            )
                        }

                        // Row 2: Theme & colors
                        item {
                            PrinterSettingsNavRow(
                                icon = JiibIcons.SystemRowTheme,
                                label = stringResource(R.string.printer_settings_theme),
                                onClick = onTheme,
                                uDp = grid.uDp,
                            )
                        }

                        // Row 3: Heat Presets — per-printer preheat preset editor
                        item {
                            PrinterSettingsNavRow(
                                icon = JiibIcons.TempPresets,
                                label = stringResource(R.string.heat_presets_title),
                                onClick = onHeatPresets,
                                uDp = grid.uDp,
                            )
                        }

                        // Row 4: Increment Values — per-printer step-selector value lists
                        item {
                            PrinterSettingsNavRow(
                                icon = JiibIcons.FineTune,
                                label = stringResource(R.string.increment_values_title),
                                onClick = onIncrementValues,
                                uDp = grid.uDp,
                            )
                        }

                        // Row 5: System Info — sits just above Power / Reset (owner UAT)
                        item {
                            PrinterSettingsNavRow(
                                icon = JiibIcons.SysInfoTile,
                                label = stringResource(R.string.printer_settings_system_info),
                                onClick = onSystemInfo,
                                uDp = grid.uDp,
                            )
                        }

                        // Row 6: Power / Reset — navigates to the Power/Reset page (Task A)
                        item {
                            PrinterSettingsNavRow(
                                icon = JiibIcons.SystemRowPower,
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
                                        icon = JiibIcons.ManagePrinters,
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

// =============================================================================
// Private sub-composables
// =============================================================================

/** Standard 1U nav-row (leading icon + label, direct-tap → onClick). */
@Composable
private fun PrinterSettingsNavRow(
    icon: JiibIcon,
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

/** The string resource for a connection state's human label. */
private fun ConnectionState.printerSettingsLabelRes(): Int = when (this) {
    ConnectionState.Connected    -> R.string.conn_state_connected
    ConnectionState.Connecting   -> R.string.conn_state_connecting
    ConnectionState.Syncing      -> R.string.conn_state_syncing
    is ConnectionState.Error     -> R.string.conn_state_error
    ConnectionState.Disconnected -> R.string.conn_state_disconnected
}
