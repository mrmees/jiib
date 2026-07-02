package works.mees.jiib.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.jiib.R
import works.mees.jiib.config.Profile
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.ListRowIcon
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.focus.FocusExplainer
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.dispatch
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import androidx.compose.ui.text.style.TextAlign

// =============================================================================
// Pure mode-toggle state machine (Task 5 — 2-mode collapse)
//
// These are package-level symbols — importable by PrintersModeToggleTest without
// pulling in any Compose/Android runtime. The composable below consumes them via
// `remember { mutableStateOf(PrinterMode.Normal) }` only.
// =============================================================================

/**
 * The two operating modes of the Printers screen foot bar (D-13, Task 5).
 *
 *  - [Normal]    — row tap switches the active printer; Edit is outlined (unarmed).
 *  - [EditArmed] — row tap opens the inline connection editor; Edit button is filled.
 */
enum class PrinterMode { Normal, EditArmed }

/**
 * The effect produced when the user taps a profile row.  Pure — no composable symbols.
 */
enum class RowTapEffect { SwitchActive, OpenEditor }

/** Arm Edit (or disarm if already armed). */
fun armEdit(current: PrinterMode): PrinterMode =
    if (current == PrinterMode.EditArmed) PrinterMode.Normal else PrinterMode.EditArmed

/** Disarm to Normal (Back key handler). */
fun disarm(): PrinterMode = PrinterMode.Normal

/** Map a row tap under [mode] to its outcome. */
fun rowTapEffect(mode: PrinterMode): RowTapEffect = when (mode) {
    PrinterMode.Normal    -> RowTapEffect.SwitchActive
    PrinterMode.EditArmed -> RowTapEffect.OpenEditor
}

// =============================================================================
// Stateless content seam (WARNING-5 preview convention) — @Preview targets this.
// =============================================================================

/**
 * Stateless Printers layout — the `@Preview` matrix targets this composable (no VM, no live
 * Moonraker). Drives preview axes: Normal / EditArmed / Empty.
 *
 * The Focus region shows static instructions. Add + Find are direct-action Field rows.
 * Back (accent) + Edit (accent-outline, fills when armed); Delete lives in the printer editor.
 *
 * @param profiles    the printer profile list to display.
 * @param activeId    the id of the currently-active profile (null = no active profile).
 * @param printerMode the current [PrinterMode] for the foot bar (preview injects specific modes).
 * @param onRowClick  row click handler.
 * @param onAdd       Add row click handler.
 * @param onFind      Find row click handler.
 * @param onArmEdit   Edit foot button click handler.
 * @param onBack      Back foot button click handler.
 * @param modifier    caller-supplied modifier.
 */
@Composable
fun PrintersContent(
    profiles: List<works.mees.jiib.config.Profile>,
    activeId: String?,
    printerMode: PrinterMode,
    isPrinting: Boolean = false,
    onEmergencyStop: () -> Unit = {},
    onRowClick: (works.mees.jiib.config.Profile) -> Unit,
    onAdd: () -> Unit,
    onFind: () -> Unit,
    onArmEdit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = stringResource(R.string.system_row_printers),
                    icon = JiibIcons.SystemRowPrinters,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                    contentInset = 0.dp,
                ) {
                    FocusExplainer(
                        text = listOf(
                            stringResource(R.string.printers_help_add),
                            stringResource(R.string.printers_help_switch),
                            stringResource(R.string.printers_help_edit),
                            stringResource(R.string.printers_help_delete),
                        ).joinToString("\n\n"),
                        textAlign = TextAlign.Start,
                    )
                }
            },
            field = {
                ListBlock(modifier = Modifier.weight(1f)) {
                    item {
                        ListRow(selected = false, onClick = onAdd, uDp = grid.uDp,
                            leadingContent = { ListRowIcon(JiibIcons.PrinterAdd, grid.uDp, t.accent) }) {
                            Text(stringResource(R.string.printers_add), color = t.text,
                                style = JiibType.listLabel.toTextStyle(t), modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    item {
                        ListRow(selected = false, onClick = onFind, uDp = grid.uDp,
                            leadingContent = { ListRowIcon(JiibIcons.Search, grid.uDp, t.accent) }) {
                            Text(stringResource(R.string.conn_row_find), color = t.text,
                                style = JiibType.listLabel.toTextStyle(t), modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    items(profiles, key = { it.id }) { profile ->
                        ListRow(selected = profile.id == activeId, onClick = { onRowClick(profile) }, uDp = grid.uDp) {
                            Text(profile.displayName(), color = t.text, style = JiibType.listLabel.toTextStyle(t),
                                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${profile.host}:${profile.port}", color = t.text2,
                                style = JiibType.dataMeta.toTextStyle(t), maxLines = 1)
                        }
                    }
                }
                FootButtonBar(
                    uDp = grid.uDp,
                    actions = listOf(
                        FootAction(stringResource(R.string.common_back), JiibIcons.Back, onBack, Intent.Accent),
                        FootAction(
                            label = stringResource(R.string.printers_edit),
                            icon = JiibIcons.Edit,
                            onClick = onArmEdit,
                            intent = Intent.Accent, // always outlined in color
                            fill = if (printerMode == PrinterMode.EditArmed) t.accentSoft else null, // filled when armed
                        ),
                    ),
                )
            },
        )
    }
}

// =============================================================================
// Printers screen (Task 5 mode collapse + Find wiring)
// =============================================================================

/**
 * The **Printers** surface — rebuilt on the jiib kit with the 2-mode foot bar (D-13, Task 5).
 *
 * - **Focus** = static instructions [FocusFrame] (how to Add / Switch / Edit / Delete).
 * - **Field** = Add row + Find row + dense profile-row [ListBlock]; active row accent-tinted.
 * - **Foot** = [FootButtonBar]: Back (accent) + Edit (accent-outline, fills when armed);
 *   Add + Find are Field rows; Delete lives in the printer editor.
 *
 * ## Mode-toggle (D-13)
 *  - Edit armed: row tap opens the inline connection editor; Edit foot filled accentSoft.
 *  - Tap Edit again or Back → Normal.
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
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused

    // Mode-toggle state machine (Task 5 — 2-mode machine).
    var printerMode by remember { mutableStateOf(PrinterMode.Normal) }
    // Inline editor target: null = list view; EditorTarget.Edit = editing existing; EditorTarget.New = adding.
    var editingTarget by remember { mutableStateOf<EditorTarget?>(null) }
    // Find screen takeover.
    var findActive by remember { mutableStateOf(false) }

    BackHandler(printerMode != PrinterMode.Normal || editingTarget != null || findActive) {
        when {
            editingTarget != null -> editingTarget = null
            findActive -> findActive = false
            else -> printerMode = disarm()
        }
    }

    // Find takeover (Task 4 screen).
    if (findActive) {
        PrinterFindScreen(
            container = container,
            onAddAndConnect = { profile ->
                container.saveAndSetActiveProfile(profile)
                findActive = false
                onSwitched()
            },
            onNeedsEditor = { seed ->
                findActive = false
                editingTarget = EditorTarget.New(seed)
            },
            onBack = { findActive = false },
            modifier = modifier,
        )
        return
    }

    // Inline connection editor (Edit-mode row tap, Add, or Find→editor fallback).
    val target = editingTarget
    if (target != null) {
        PrinterConnectionEditor(
            container = container,
            profile = if (target is EditorTarget.Edit) target.profile else null,
            seed = (target as? EditorTarget.New)?.seed,
            onDone = { editingTarget = null },
            modifier = modifier,
        )
        return
    }

    // WR-04 (preview-first LAW): the live screen DELEGATES to the stateless [PrintersContent] seam —
    // the exact layout body the @Preview matrix renders — so screen and previews cannot drift.
    PrintersContent(
        profiles = profiles,
        activeId = activeId,
        printerMode = printerMode,
        isPrinting = isPrinting,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onRowClick = { profile ->
            when (rowTapEffect(printerMode)) {
                RowTapEffect.SwitchActive -> { container.setActiveProfile(profile.id); onSwitched() }
                RowTapEffect.OpenEditor -> { editingTarget = EditorTarget.Edit(profile) }
            }
        },
        onAdd = { editingTarget = EditorTarget.New() },
        onFind = { findActive = true },
        onArmEdit = { printerMode = armEdit(printerMode) },
        onBack = { if (printerMode != PrinterMode.Normal) printerMode = disarm() else onBack() },
        modifier = modifier,
    )
}

// =============================================================================
// Connection editor (inline — opened from Edit mode or Add)
// =============================================================================

/** Which profile the Connection editor targets — an existing one to [Edit], or a blank [New] printer (optionally seed-filled). */
private sealed interface EditorTarget {
    data class Edit(val profile: Profile) : EditorTarget
    data class New(val seed: ConnectionSeed? = null) : EditorTarget
}

// PrinterConnectionEditor + resolveEditorKeyOnSave live in PrinterConnectionEditor.kt; the
// network-scan (Find) flow lives in PrinterFindScreen.kt.
