package works.mees.dinghy.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.R
import works.mees.dinghy.config.Profile
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FocusEdge
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle

// =============================================================================
// Pure mode-toggle state machine (28-06, D-13)
//
// These are package-level symbols — importable by PrintersModeToggleTest without
// pulling in any Compose/Android runtime. The composable below consumes them via
// `remember { mutableStateOf(PrinterMode.Normal) }` only.
// =============================================================================

/**
 * The three operating modes of the Printers screen foot bar (D-13).
 *
 *  - [Normal]      — row tap switches the active printer; Edit+Delete are outline (unarmed).
 *  - [EditArmed]   — row tap opens the inline connection editor; Edit button is highlighted.
 *  - [DeleteArmed] — row tap raises a [ConfirmGuard]; Delete button is filled stop (C4 rule).
 */
enum class PrinterMode { Normal, EditArmed, DeleteArmed }

/**
 * The effect produced when the user taps a profile row.  Pure — no composable symbols.
 */
enum class RowTapEffect { SwitchActive, OpenEditor, RequestDelete }

/** Arm Edit (or disarm if already EditArmed, or switch from DeleteArmed). */
fun armEdit(current: PrinterMode): PrinterMode = when (current) {
    PrinterMode.EditArmed -> PrinterMode.Normal   // tap again → disarm
    else -> PrinterMode.EditArmed
}

/** Arm Delete (or disarm if already DeleteArmed, or switch from EditArmed). */
fun armDelete(current: PrinterMode): PrinterMode = when (current) {
    PrinterMode.DeleteArmed -> PrinterMode.Normal // tap again → disarm
    else -> PrinterMode.DeleteArmed
}

/** Disarm to Normal (Back key handler). */
fun disarm(): PrinterMode = PrinterMode.Normal

/** Map a row tap under [mode] to one of three outcomes. */
fun rowTapEffect(mode: PrinterMode): RowTapEffect = when (mode) {
    PrinterMode.Normal      -> RowTapEffect.SwitchActive
    PrinterMode.EditArmed   -> RowTapEffect.OpenEditor
    PrinterMode.DeleteArmed -> RowTapEffect.RequestDelete
}

// =============================================================================
// Stateless content seam (WARNING-5 preview convention) — @Preview targets this.
// =============================================================================

/**
 * Stateless Printers layout — the `@Preview` matrix targets this composable (no VM, no live
 * Moonraker). Drives all four preview axes: Normal / Edit-armed / Delete-armed / Empty.
 *
 * @param profiles       the printer profile list to display.
 * @param activeId       the id of the currently-active profile (null = no active profile).
 * @param connectionState the current [ConnectionState] for the active printer.
 * @param printerMode    the current [PrinterMode] for the foot bar (preview injects specific modes).
 * @param onRowClick     row click handler.
 * @param onAdd          Add button click handler.
 * @param onArmEdit      Edit foot button click handler.
 * @param onArmDelete    Delete foot button click handler.
 * @param onBack         Back foot button click handler.
 * @param modifier       caller-supplied modifier.
 */
@Composable
fun PrintersContent(
    profiles: List<works.mees.dinghy.config.Profile>,
    activeId: String?,
    connectionState: ConnectionState,
    printerMode: PrinterMode,
    isPrinting: Boolean = false,
    onEmergencyStop: () -> Unit = {},
    onRowClick: (works.mees.dinghy.config.Profile) -> Unit,
    onAdd: () -> Unit,
    onArmEdit: () -> Unit,
    onArmDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    val ringColor: Color? = when (connectionState) {
        ConnectionState.Connected    -> t.accent
        ConnectionState.Connecting   -> t.heat
        ConnectionState.Syncing      -> t.heat
        is ConnectionState.Error     -> t.stop
        ConnectionState.Disconnected -> null
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        val activeProfile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

        ScreenScaffold(
            focus = {
                if (activeProfile != null) {
                    FocusFrame(
                        title = stringResource(R.string.system_row_printers),
                        icon = DinghyIcons.SystemRowPrinters,
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
                            text = stringResource(connectionState.labelRes()),
                            color = ringColor ?: t.text2,
                            style = DinghyType.caption.toTextStyle(t),
                        )
                    }
                } else {
                    // Framed empty state — Printers identity (existing owner-locked SystemRowPrinters
                    // glyph) holding the no-printers headline/body (moved out of the Field).
                    FocusFrame(
                        title = stringResource(R.string.system_row_printers),
                        icon = DinghyIcons.SystemRowPrinters,
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
                if (profiles.isEmpty()) {
                    // Blank weighted spacer keeps the FootButtonBar pinned to the foot of the Field;
                    // the empty headline/body moved into the Focus card above.
                    Spacer(Modifier.weight(1f))
                } else {
                    ListBlock(modifier = Modifier.weight(1f)) {
                        items(profiles, key = { it.id }) { profile ->
                            ListRow(
                                selected = profile.id == activeId,
                                onClick = { onRowClick(profile) },
                                uDp = grid.uDp,
                            ) {
                                Text(
                                    text = profile.displayName(),
                                    color = t.text,
                                    style = DinghyType.listLabel.toTextStyle(t),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${profile.host}:${profile.port}",
                                    color = t.text2,
                                    style = DinghyType.dataMeta.toTextStyle(t),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                FootButtonBar(
                    uDp = grid.uDp,
                ) {
                    // Back FIRST (accent — R5/R8); armed toggles keep Neutral as the
                    // inactive-state style (R18).
                    OutlinedControl(
                        label = stringResource(R.string.common_back),
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent,
                    )
                    OutlinedControl(
                        label = stringResource(R.string.printers_add),
                        onClick = onAdd,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent,
                    )
                    OutlinedControl(
                        label = stringResource(R.string.printers_edit),
                        onClick = onArmEdit,
                        modifier = Modifier.weight(1f),
                        intent = if (printerMode == PrinterMode.EditArmed) Intent.Accent else Intent.Neutral,
                    )
                    OutlinedControl(
                        label = stringResource(R.string.printers_delete),
                        onClick = onArmDelete,
                        modifier = Modifier.weight(1f),
                        intent = if (printerMode == PrinterMode.DeleteArmed) Intent.Danger else Intent.Neutral,
                    )
                }
            },
        )
    }
}

// =============================================================================
// Printers screen (rebuilt 28-06, D-13/D-15)
// =============================================================================

/**
 * The **Printers** surface — rebuilt on the jiib kit (28-06) with the R4 Edit/Delete mode-toggle
 * foot bar (D-13) and the D-15 Focus/Field/Foot layout.
 *
 * - **Focus** = active printer [FocusFrame] (name, host:port, connection-state ring, Klippy state).
 * - **Field** = dense profile-row [ListBlock]; active row accent-tinted; row tap dispatched by mode.
 * - **Foot** = [FootButtonBar] (1U): Add(Accent) / Edit(Neutral↔highlighted) / Delete(Neutral↔filled
 *   stop, C4) / Back(Neutral).
 *
 * ## Mode-toggle (D-13)
 *  - Edit armed: row tap opens the inline connection editor; Edit foot highlighted.
 *  - Delete armed: row tap raises [ConfirmGuard]; Delete foot filled stop.
 *  - Tap armed toggle again or Back → Normal.
 *
 * ## Write-scope law ([[dinghy-compose-write-scope-cancellation]])
 * EVERY persist routes through [AppContainer] intent helpers — process-scoped writeScope only.
 *
 * ## Security (T-28-06-01)
 * The API key field NEVER pre-fills the raw stored key. Blank = preserve stored key;
 * explicit Clear = write null ([AppContainer.resolveApiKeyEdit]).
 */
@Composable
fun PrintersScreen(
    container: AppContainer,
    onSwitched: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profiles by container.profileStore.profiles.collectAsStateWithLifecycle(emptyList())
    val activeId by container.profileStore.activeId.collectAsStateWithLifecycle(null)
    val connectionState by container.connectionState.collectAsStateWithLifecycle(ConnectionState.Disconnected)
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused

    // Mode-toggle state machine (28-06 D-13) — pure PrinterMode driven by foot bar buttons + BackHandler.
    var printerMode by remember { mutableStateOf(PrinterMode.Normal) }
    // Inline editor target: null = list view; Some(profile) = editing; EditorTarget.New = adding.
    var editingTarget by remember { mutableStateOf<EditorTarget?>(null) }
    // Pending delete for ConfirmGuard.
    var pendingDelete by remember { mutableStateOf<Profile?>(null) }

    // BackHandler priority (WR-02): dismiss the delete ConfirmGuard first (= "Keep"), then the
    // editor, then disarm the mode. Without pendingDelete in the gate, the second Back found the
    // handler disabled and NavHost popped the whole route out from under an open destructive guard.
    BackHandler(pendingDelete != null || printerMode != PrinterMode.Normal || editingTarget != null) {
        when {
            pendingDelete != null -> pendingDelete = null
            editingTarget != null -> editingTarget = null
            else -> printerMode = disarm()
        }
    }

    // ConfirmGuard for Delete-mode row tap.
    pendingDelete?.let { victim ->
        ConfirmGuard(
            title = stringResource(R.string.printers_delete_confirm_title),
            message = stringResource(R.string.printers_delete_confirm_body),
            confirmLabel = stringResource(R.string.printers_delete),
            cancelLabel = stringResource(R.string.common_back),
            onConfirm = {
                container.deleteProfile(victim.id)
                pendingDelete = null
                printerMode = disarm()
            },
            onCancel = { pendingDelete = null },
            destructive = true,
        )
        return
    }

    // Inline connection editor (Edit mode row tap or Add).
    val target = editingTarget
    if (target != null) {
        PrinterConnectionEditor(
            container = container,
            profile = if (target is EditorTarget.Edit) target.profile else null,
            onDone = {
                editingTarget = null
                // Stay in EditArmed after saving so the user can continue editing other printers.
            },
            modifier = modifier,
        )
        return
    }

    // WR-04 (preview-first LAW): the live screen DELEGATES to the stateless [PrintersContent] seam —
    // the exact layout body the @Preview matrix renders — so screen and previews cannot drift.
    PrintersContent(
        profiles = profiles,
        activeId = activeId,
        connectionState = connectionState,
        printerMode = printerMode,
        isPrinting = isPrinting,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onRowClick = { profile ->
            when (rowTapEffect(printerMode)) {
                RowTapEffect.SwitchActive -> {
                    container.setActiveProfile(profile.id)
                    onSwitched()
                }
                RowTapEffect.OpenEditor -> {
                    editingTarget = EditorTarget.Edit(profile)
                }
                RowTapEffect.RequestDelete -> {
                    pendingDelete = profile
                }
            }
        },
        onAdd = { editingTarget = EditorTarget.New },
        onArmEdit = { printerMode = armEdit(printerMode) },
        onArmDelete = { printerMode = armDelete(printerMode) },
        onBack = {
            if (printerMode != PrinterMode.Normal) {
                printerMode = disarm()
            } else {
                onBack()
            }
        },
        modifier = modifier,
    )
}

// =============================================================================
// Connection editor (inline — opened from Edit mode or Add)
// =============================================================================

/** Which profile the Connection editor targets — an existing one to [Edit], or a blank [New] printer. */
private sealed interface EditorTarget {
    data class Edit(val profile: Profile) : EditorTarget
    data object New : EditorTarget
}

// PrinterConnectionEditor, SecureToggleRow, DiscoveredPrinterRow, SCAN_WINDOW_MS, and
// resolveEditorKeyOnSave have been extracted to PrinterConnectionEditor.kt (Task 4.1).

/**
 * The string resource for a connection state’s human label (used in the Focus FocusFrame) —
 * WR-05: resolved via stringResource at the call site (the stringResource LAW).
 */
private fun ConnectionState.labelRes(): Int = when (this) {
    ConnectionState.Connected    -> R.string.conn_state_connected
    ConnectionState.Connecting   -> R.string.conn_state_connecting
    ConnectionState.Syncing      -> R.string.conn_state_syncing
    is ConnectionState.Error     -> R.string.conn_state_error
    ConnectionState.Disconnected -> R.string.conn_state_disconnected
}
