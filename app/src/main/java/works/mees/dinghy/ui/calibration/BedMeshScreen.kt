package works.mees.dinghy.ui.calibration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import works.mees.dinghy.designsystem.layout.LocalUnitDp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import works.mees.dinghy.R
import works.mees.dinghy.calibration.BedMeshHolder
import works.mees.dinghy.calibration.BedMeshVm
import works.mees.dinghy.calibration.CalibrationRoutine
import works.mees.dinghy.command.BedMeshProfileArgs
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.footAction
import works.mees.dinghy.designsystem.components.ListRowLabel
import works.mees.dinghy.control.ControlSpecs
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import works.mees.dinghy.calibration.BedMeshViewType
import works.mees.dinghy.calibration.meshSpan
import works.mees.dinghy.render.BedMeshHeatmapHost
import works.mees.dinghy.render.BedMeshHeatmapView
import works.mees.dinghy.render.resolveMeshColor
import works.mees.dinghy.ui.screen.TokenTextField
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.command.BedMeshRenameArgs
import works.mees.dinghy.designsystem.control.OutlinedControl

// ─── Edit-morph classifier ─────────────────────────────────────────────────────────────────────

/**
 * Which edit-form button set to show. The effective focus target is [selected] if set, else the
 * active mesh. "default" is reserved (never a saved target) so an active mesh named default ==
 * Active-unsaved.
 */
internal enum class MeshEditKind { ACTIVE_UNSAVED, ACTIVE_SAVED, PREVIEW_NONACTIVE }

/**
 * Pure classifier: decides which button matrix the edit form shows based on the current printer
 * state. No side effects; testable without Android.
 *
 * @param activeName  the name of the currently active (loaded) mesh profile, or "" if none.
 * @param isEmpty     true when no mesh data is loaded at all.
 * @param selected    the profile name the user has tapped in the Field list, or null = none.
 * @param savedNames  the set of profiles that exist in printer.cfg (survives restart).
 */
internal fun classifyMeshEdit(
    activeName: String,
    isEmpty: Boolean,
    selected: String?,
    savedNames: Set<String>,
): MeshEditKind {
    val isActiveTarget = selected == null || selected == activeName
    return when {
        !isActiveTarget -> MeshEditKind.PREVIEW_NONACTIVE
        isEmpty || activeName.isEmpty() || activeName == "default" || activeName !in savedNames ->
            MeshEditKind.ACTIVE_UNSAVED
        else -> MeshEditKind.ACTIVE_SAVED
    }
}

// ──────────────────────────────────────────────────────────────────────────────────────────────

/**
 * The Bed-Mesh screen — rebuilt for the jiib redesign (Phase 27, D-11..D-14).
 *
 * This is the thin VM-collecting wrapper (WARNING-5 preview-first convention). It collects live
 * state from [holder] and [container.dispatcher], hoists ephemeral UI state (selected profile,
 * field mode, confirm guards), and forwards a plain data snapshot to the stateless [BedMeshContent]
 * seam that the Task-2 `@Preview` matrix targets.
 *
 * The full-screen [SaveNameDialog] / [LoadSelectorDialog] overlays and the `MeshDialog` enum are
 * DELETED (D-11). Profile management is a Field [ListBlock] list + a [MeshFieldMode.SaveName]
 * Field-takeover (D-11..D-13). Actions live in [FootButtonBar] (D-14) (foot-of-list LAW).
 *
 * @param container the service-locator (provides the session dispatcher).
 * @param holder    the headless [BedMeshHolder] (heatmap model + scale mode + profiles + error).
 * @param onBack    leave the page (neutral Back).
 */
/**
 * The bed-mesh ramp's neutral "zero deviation" midpoint (low → neutral → high). Theme-INDEPENDENT
 * (a FIXED gray, not `tokens.outline`) so the gradient's middle reads identically in dark and light
 * mode — the per-theme outline flips dark↔light and dragged the dark-mode middle into a muddy valley
 * (UAT). Value = the blend of the dark-mode outline (#4B535E) and the light-mode outline (#A4ABB8):
 * RGB((75+164)/2, (83+171)/2, (94+184)/2) = (120, 127, 139) = #787F8B.
 */
private val MESH_MID_NEUTRAL = 0xFF787F8B.toInt()

@Composable
fun BedMeshScreen(
    container: AppContainer,
    holder: BedMeshHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    val vm by holder.vm.collectAsStateWithLifecycle()

    // Ephemeral UI state — rememberSaveable so both survive rotation (27-UI-SPEC orientation rule).
    var selectedProfile by rememberSaveable { mutableStateOf<String?>(null) }
    var fieldMode by rememberSaveable(stateSaver = MeshFieldModeSaver) {
        mutableStateOf<MeshFieldMode>(MeshFieldMode.ProfileList)
    }
    // Focus edit-morph state: true = the MeshEditForm is shown in the Focus region.
    var editing by rememberSaveable { mutableStateOf(false) }

    // Confirm guard flags (local only — transient overlays, not navigation state).
    var showRemoveGuard by remember { mutableStateOf(false) }
    var showSaveConfigGuard by remember { mutableStateOf(false) }

    // WR-05 (27-review): the stable dispatch keys of the persist-relevant profile commands.
    // A Failure under one of these keys means nothing was persisted — used below to retract the
    // SAVE_CONFIG guard instead of inviting a pointless Klipper restart.
    // bedMeshProfileRename key is constant ("bed_mesh_profile_rename", independent of args) so
    // a failed rename also retracts the guard.
    val profilePersistKeys = remember {
        val probe = BedMeshProfileArgs("")
        val renameProbe = BedMeshRenameArgs("", "")
        setOf(
            CommandRegistry.bedMeshProfileSave.dispatchKey(probe),
            CommandRegistry.bedMeshProfileRemove.dispatchKey(probe),
            CommandRegistry.bedMeshProfileRename.dispatchKey(renameProbe),
        )
    }

    // Toast state (dismissable error + auto-dismiss).
    var toastError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(dispatcher) {
        val d = dispatcher ?: return@LaunchedEffect
        d.events.collect { event ->
            if (event is DispatchEvent.Failure) {
                toastError = event.message
                if (event.key in profilePersistKeys) showSaveConfigGuard = false
            }
        }
    }
    LaunchedEffect(vm.errorText) { vm.errorText?.let { toastError = it } }
    LaunchedEffect(toastError) {
        if (toastError != null) {
            delay(5_000)
            toastError = null
        }
    }

    BedMeshContent(
        vm = vm,
        selectedProfile = selectedProfile,
        fieldMode = fieldMode,
        editing = editing,
        showRemoveGuard = showRemoveGuard,
        showSaveConfigGuard = showSaveConfigGuard,
        toastError = toastError,
        isPrinting = isPrinting,
        dispatcherPresent = dispatcher != null,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onCycleScaleMode = { holder.cycleScaleMode() },
        onSelectProfile = { name ->
            selectedProfile = name
            editing = false
        },
        onClearMesh = {
            dispatcher?.dispatch(CommandRegistry.bedMeshClear, Unit)
            selectedProfile = null
            editing = false
            // BED_MESH_CLEAR is runtime-only; saved profiles untouched; no SAVE_CONFIG guard.
        },
        onOpenMeshConfig = {
            fieldMode = MeshFieldMode.MeshConfig
            editing = false
        },
        onOpenConfigEditor = { item -> fieldMode = MeshFieldMode.MeshConfigEditor(item) },
        onBackToConfigList = { fieldMode = MeshFieldMode.MeshConfig },
        onBackToProfileList = { fieldMode = MeshFieldMode.ProfileList },
        onSetViewType = { container.setBedMeshViewType(it) },
        onSetHighColorSel = { container.setBedMeshHighColorSel(it) },
        onSetLowColorSel = { container.setBedMeshLowColorSel(it) },
        onShowSaveName = { fieldMode = MeshFieldMode.SaveName(defaultProfileName()) },
        onSaveNameConfirm = { name ->
            val d = dispatcher
            fieldMode = MeshFieldMode.ProfileList
            // WR-05 (27-review): raise the amber SAVE_CONFIG restart guard only when the save
            // dispatch was actually sent — never invite a Klipper restart for a save that never
            // went out. (A server-side Failure under the save key retracts the guard above.)
            if (d != null) {
                d.dispatch(CommandRegistry.bedMeshProfileSave, BedMeshProfileArgs(name))
                showSaveConfigGuard = true
            }
        },
        onSaveNameCancel = { fieldMode = MeshFieldMode.ProfileList },
        onApplyProfile = { name ->
            dispatcher?.dispatch(CommandRegistry.bedMeshProfileLoad, BedMeshProfileArgs(name))
        },
        onShowRemoveGuard = { showRemoveGuard = true },
        onRemoveConfirm = {
            val d = dispatcher
            val target = selectedProfile ?: vm.model.profileName
            if (d != null && target.isNotEmpty() && target != "default") {
                d.dispatch(CommandRegistry.bedMeshProfileRemove, BedMeshProfileArgs(target))
                // WR-02 (27-review, updated): REMOVE is runtime-only by design — the profile
                // resurrects on the next firmware restart without a manual SAVE_CONFIG, but the
                // owner does NOT want a restart prompt on delete. No SAVE_CONFIG guard on removal.
            }
            selectedProfile = null
            editing = false
            showRemoveGuard = false
        },
        onRemoveCancel = { showRemoveGuard = false },
        onSaveConfigConfirm = {
            dispatcher?.dispatch(CommandRegistry.saveConfig, Unit)
            showSaveConfigGuard = false
        },
        onSaveConfigCancel = { showSaveConfigGuard = false },
        onEditOpen = { editing = true },
        onEditApply = { name ->
            dispatcher?.dispatch(CommandRegistry.bedMeshProfileLoad, BedMeshProfileArgs(name))
            editing = false
        },
        onEditSave = { newName ->
            val d = dispatcher
            val active = vm.model.profileName
            val kind = classifyMeshEdit(active, vm.isEmpty, selectedProfile, vm.profileNames.toSet())
            if (d != null) {
                if (kind == MeshEditKind.ACTIVE_SAVED && newName != active) {
                    // Rename = ONE ordered script (SAVE new -> REMOVE old). Using the single
                    // bedMeshProfileRename command avoids two separate dispatches that could race.
                    d.dispatch(CommandRegistry.bedMeshProfileRename, BedMeshRenameArgs(old = active, new = newName))
                } else {
                    // Active-unsaved save (or active-saved with unchanged name): plain SAVE.
                    d.dispatch(CommandRegistry.bedMeshProfileSave, BedMeshProfileArgs(newName))
                }
                showSaveConfigGuard = true
            }
            editing = false
        },
        onEditDelete = {
            // Raise the red ConfirmGuard instead of dispatching immediately.
            // onRemoveConfirm carries the actual dispatch + SAVE_CONFIG guard + form close.
            showRemoveGuard = true
        },
        onEditCancel = { editing = false },
        onHomeAll = { dispatcher?.dispatch(CommandRegistry.homeAll, Unit) },
        onCalibrate = { dispatcher?.dispatch(CommandRegistry.bedMeshCalibrate, Unit) },
        onDismissError = { toastError = null },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Field-mode sealed class for the BedMesh Field region (D-11/D-13).
 *
 * - [ProfileList]: the default Field state — a [ListBlock] of saved profiles with a state-adaptive
 *   [FootButtonBar] (D-14).
 * - [SaveName]: the Field-takeover showing the alphanumeric keyboard input for the save-profile
 *   name (D-13 sanctioned carve-out), pre-filled with [prefill].
 * - [MeshConfig]: the Mesh Config subpage list (Task 9 fills the body; stub for now).
 * - [MeshConfigEditor]: a config item's editor in the Focus (Task 9).
 */
internal sealed class MeshFieldMode {
    data object ProfileList : MeshFieldMode()
    data class SaveName(val prefill: String) : MeshFieldMode()
    data object MeshConfig : MeshFieldMode()                                  // NEW: Mesh Config subpage list
    data class MeshConfigEditor(val item: MeshConfigItem) : MeshFieldMode()  // NEW: a config row's editor
}

/** Identifies which config item is being edited in the Mesh Config subpage. */
internal enum class MeshConfigItem { VIEW_TYPE, HIGH_COLOR, LOW_COLOR, PREVIEW }

/** Saver so [MeshFieldMode] state survives process death / rotation (only [prefill] needs persisting). */
internal val MeshFieldModeSaver = androidx.compose.runtime.saveable.Saver<MeshFieldMode, Any>(
    save = { mode ->
        when (mode) {
            is MeshFieldMode.ProfileList -> "ProfileList"
            is MeshFieldMode.SaveName -> "SaveName:${mode.prefill}"
            is MeshFieldMode.MeshConfig -> "MeshConfig"
            is MeshFieldMode.MeshConfigEditor -> "MeshConfigEditor:${mode.item.name}"
        }
    },
    restore = { raw ->
        val s = raw as? String ?: return@Saver MeshFieldMode.ProfileList
        when {
            s == "ProfileList" -> MeshFieldMode.ProfileList
            s.startsWith("SaveName:") -> MeshFieldMode.SaveName(s.removePrefix("SaveName:"))
            s == "MeshConfig" -> MeshFieldMode.MeshConfig
            s.startsWith("MeshConfigEditor:") -> {
                val itemName = s.removePrefix("MeshConfigEditor:")
                runCatching { MeshFieldMode.MeshConfigEditor(MeshConfigItem.valueOf(itemName)) }
                    .getOrDefault(MeshFieldMode.ProfileList)
            }
            else -> MeshFieldMode.ProfileList
        }
    },
)

/** App-generated default profile name `YY.MM.DD_HH.MM`, pre-filled in the SaveName takeover (D-13). */
internal fun defaultProfileName(): String =
    SimpleDateFormat("yy.MM.dd_HH.mm", Locale.US).format(Date())

/**
 * Stateless BedMesh layout composable (WARNING-5 preview seam).
 *
 * Receives all data and callbacks as plain parameters — no holder, no VM, no [AppContainer].
 * The Task-2 `@Preview` matrix targets this directly via [SampleFixtures.bedMeshContent].
 *
 * Focus = [BedMeshHeatmapHost] (Views surface with P22 equality guards preserved — the [AndroidView]
 * factory is inside [BedMeshHeatmapHost] itself and runs once; recomposition only triggers `update`).
 * Preview: [BedMeshHeatmapHost] itself has the [LocalInspectionMode] → placeholder branch.
 *
 * Field = `when(fieldMode)` { ProfileList → list + state-adaptive foot; SaveName → takeover }.
 * Actions live in the field FootButtonBar (foot-of-list LAW).
 */
@Composable
internal fun BedMeshContent(
    vm: BedMeshVm,
    selectedProfile: String?,
    fieldMode: MeshFieldMode,
    editing: Boolean,
    showRemoveGuard: Boolean,
    showSaveConfigGuard: Boolean,
    toastError: String?,
    isPrinting: Boolean,
    dispatcherPresent: Boolean,
    onEmergencyStop: () -> Unit,
    onCycleScaleMode: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onClearMesh: () -> Unit,
    onOpenMeshConfig: () -> Unit,
    onOpenConfigEditor: (MeshConfigItem) -> Unit,
    onBackToConfigList: () -> Unit,
    onBackToProfileList: () -> Unit,
    onSetViewType: (BedMeshViewType) -> Unit,
    onSetHighColorSel: (Int) -> Unit,
    onSetLowColorSel: (Int) -> Unit,
    onShowSaveName: () -> Unit,
    onSaveNameConfirm: (String) -> Unit,
    onSaveNameCancel: () -> Unit,
    onApplyProfile: (String) -> Unit,
    onShowRemoveGuard: () -> Unit,
    onRemoveConfirm: () -> Unit,
    onRemoveCancel: () -> Unit,
    onSaveConfigConfirm: () -> Unit,
    onSaveConfigCancel: () -> Unit,
    onEditOpen: () -> Unit,
    onEditApply: (String) -> Unit,
    onEditSave: (String) -> Unit,
    onEditDelete: () -> Unit,
    onEditCancel: () -> Unit,
    onHomeAll: () -> Unit,
    onCalibrate: () -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        Box(Modifier.fillMaxSize()) {
            ScreenScaffold(
                focus = {
                    FocusFrame(
                        title = stringResource(routineTitleRes(CalibrationRoutine.BED_MESH)),
                        icon = routineIconToken(CalibrationRoutine.BED_MESH),
                        uDp = grid.uDp,
                        modifier = Modifier.fillMaxSize(),
                        isPrinting = isPrinting,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                        trailingActionIcon = if (!isPrinting && fieldMode !is MeshFieldMode.MeshConfig && fieldMode !is MeshFieldMode.MeshConfigEditor) DinghyIcons.Edit else null,
                        onTrailingAction = if (!isPrinting && fieldMode !is MeshFieldMode.MeshConfig && fieldMode !is MeshFieldMode.MeshConfigEditor) onEditOpen else null,
                        trailingActionContentDescription = "Edit mesh profile",
                    ) {
                        when {
                            editing -> {
                                val kind = classifyMeshEdit(
                                    activeName = vm.model.profileName,
                                    isEmpty = vm.isEmpty,
                                    selected = selectedProfile,
                                    savedNames = vm.profileNames.toSet(),
                                )
                                val targetName = selectedProfile ?: vm.model.profileName
                                MeshEditForm(
                                    kind = kind,
                                    targetName = targetName,
                                    onApply = { onEditApply(targetName) },
                                    onSave = onEditSave,
                                    onDelete = onEditDelete,
                                    onCancel = onEditCancel,
                                    t = t,
                                    uDp = grid.uDp,
                                )
                            }
                            fieldMode is MeshFieldMode.MeshConfigEditor -> {
                                MeshConfigEditorFocus(
                                    item = (fieldMode as MeshFieldMode.MeshConfigEditor).item,
                                    vm = vm,
                                    selectedProfile = selectedProfile,
                                    tokens = t,
                                    onSetViewType = onSetViewType,
                                    onSetHighColorSel = onSetHighColorSel,
                                    onSetLowColorSel = onSetLowColorSel,
                                    uDp = grid.uDp,
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                )
                            }
                            else -> {
                                BedMeshFocusRegion(
                                    vm = vm,
                                    selectedProfile = selectedProfile,
                                    tokens = t,
                                    uDp = grid.uDp,
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                )
                            }
                        }
                    }
                },
                field = {
                    when (fieldMode) {
                        is MeshFieldMode.ProfileList -> {
                            // Profile list: Clear Mesh (conditional top) + profiles + Mesh Config (bottom)
                            ListBlock(modifier = Modifier.weight(1f)) {
                                // Clear Mesh row — only when a live mesh is loaded and not printing
                                if (!vm.isEmpty && !isPrinting) {
                                    item(key = "__clear__") {
                                        ListRow(
                                            selected = false,
                                            onClick = onClearMesh,
                                            uDp = grid.uDp,
                                            leadingContent = {
                                                ListRowIcon(
                                                    icon = DinghyIcons.BlurOff,
                                                    uDp = grid.uDp,
                                                    tint = t.text2,
                                                    contentDescription = "Clear Mesh",
                                                )
                                            },
                                        ) { ListRowLabel("Clear Mesh") }
                                    }
                                }

                                // Saved profile rows (may be empty — ListBlock handles that gracefully
                                // since Clear Mesh + Mesh Config rows are always present anchors)
                                items(vm.profileNames, key = { it }) { name ->
                                    val isActive = name == vm.model.profileName && !vm.isEmpty
                                    ListRow(
                                        selected = name == selectedProfile,
                                        onClick = { onSelectProfile(name) },
                                        uDp = grid.uDp,
                                        trailingContent = {
                                            // Per-profile span (max−min probe Z) + a dot for the active profile.
                                            val span = vm.model.profiles[name]?.points?.let(::meshSpan)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                if (span != null) {
                                                    Text(
                                                        text = String.format(Locale.US, "%.3f mm", span),
                                                        color = t.text2,
                                                        style = DinghyType.dataMeta.toTextStyle(t),
                                                    )
                                                }
                                                if (isActive) {
                                                    Box(
                                                        Modifier
                                                            .size(8.dp)
                                                            .background(t.accent2, CircleShape),
                                                    )
                                                }
                                            }
                                        },
                                    ) {
                                        // Canonical list-label look (Geist SemiBold 20) — the
                                        // profile NAME is the row label; mono stays for VALUES.
                                        ListRowLabel(name)
                                    }
                                }

                                // Color Scale row — cycles the heatmap scale mode IN PLACE (no
                                // subfocus); current mode shown in the trailing slot. Reuses the
                                // relocated overlay's "expand" glyph (same control, just moved).
                                item(key = "__scale__") {
                                    ListRow(
                                        selected = false,
                                        onClick = onCycleScaleMode,
                                        uDp = grid.uDp,
                                        leadingContent = {
                                            ListRowIcon(
                                                icon = DinghyIcons.BabystepExpand,
                                                uDp = grid.uDp,
                                                tint = t.text2,
                                                contentDescription = "Color Scale",
                                            )
                                        },
                                        trailingContent = {
                                            Text(
                                                text = vm.scaleMode.displayLabel(),
                                                color = t.accent2,
                                                style = DinghyType.dataMeta.toTextStyle(t),
                                            )
                                        },
                                    ) { ListRowLabel("Color Scale") }
                                }

                                // Mesh Config row — always at bottom
                                item(key = "__config__") {
                                    ListRow(
                                        selected = false,
                                        onClick = onOpenMeshConfig,
                                        uDp = grid.uDp,
                                        leadingContent = {
                                            ListRowIcon(
                                                icon = DinghyIcons.Palette,
                                                uDp = grid.uDp,
                                                tint = t.text2,
                                                contentDescription = "Mesh Config",
                                            )
                                        },
                                    ) { ListRowLabel("Mesh Config") }
                                }
                            }

                            // Simplified footer: Back + (Home All | Calibrate), gated by !isPrinting
                            FootButtonBar(
                                uDp = grid.uDp,
                                actions = buildList {
                                    add(FootAction(
                                        label = stringResource(R.string.common_back),
                                        icon = DinghyIcons.Back,
                                        onClick = onBack,
                                        intent = Intent.Accent,
                                        contentDescription = stringResource(R.string.common_back),
                                    ))
                                    if (!isPrinting) {
                                        if (!vm.homed) {
                                            add(footAction(
                                                ControlSpecs.calibrationHomeAll,
                                                onClick = onHomeAll,
                                                enabled = dispatcherPresent,
                                            ))
                                        } else {
                                            add(FootAction(
                                                // owner 2026-06-17: play_circle (CalibrationRun)
                                                label = stringResource(R.string.mesh_calibrate),
                                                icon = DinghyIcons.CalibrationRun,
                                                onClick = onCalibrate,
                                                intent = Intent.Go,
                                                enabled = dispatcherPresent,
                                            ))
                                        }
                                    }
                                },
                            )
                        }

                        is MeshFieldMode.SaveName -> {
                            // D-13: SaveName Field-takeover with alphanumeric keyboard
                            var saveName by rememberSaveable { mutableStateOf(fieldMode.prefill) }
                            val valid = PrinterCommands.isValidProfileName(saveName)

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.mesh_save_name_label),
                                    color = t.text,
                                    style = DinghyType.listLabel.toTextStyle(t),
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                                TokenTextField(
                                    value = saveName,
                                    onValueChange = { saveName = it },
                                    label = stringResource(R.string.mesh_save_name_hint),
                                    isError = saveName.isNotEmpty() && !valid,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (saveName.isNotEmpty() && !valid) {
                                    Text(
                                        text = stringResource(R.string.mesh_save_name_invalid),
                                        color = t.stop,
                                        style = DinghyType.caption.toTextStyle(t),
                                    )
                                }
                            }

                            // SaveName foot: Save (go — accept/commit, R5; disabled until valid)
                            // + Cancel (danger, C7 cancel-with-loss)
                            FootButtonBar(
                                uDp = grid.uDp,
                                actions = listOf(
                                    FootAction(
                                        label = stringResource(R.string.mesh_save_confirm),
                                        icon = DinghyIcons.CheckCircle,
                                        onClick = { onSaveNameConfirm(saveName) },
                                        intent = Intent.Go,
                                        enabled = valid && dispatcherPresent,
                                    ),
                                    // C7: cancel-with-loss → Intent.Danger (red)
                                    FootAction(
                                        label = stringResource(R.string.common_cancel),
                                        icon = DinghyIcons.DialogClose,
                                        onClick = onSaveNameCancel,
                                        intent = Intent.Danger,
                                    ),
                                ),
                            )
                        }

                        is MeshFieldMode.MeshConfig -> {
                            // Mesh Config subpage: 4 config rows (View Type / High / Low Color / Preview)
                            ListBlock(modifier = Modifier.weight(1f)) {
                                item(key = "vt") {
                                    ListRow(
                                        selected = false,
                                        onClick = { onOpenConfigEditor(MeshConfigItem.VIEW_TYPE) },
                                        uDp = grid.uDp,
                                        leadingContent = {
                                            ListRowIcon(
                                                icon = DinghyIcons.MeshViewIso,
                                                uDp = grid.uDp,
                                                tint = t.text2,
                                                contentDescription = "View Type",
                                            )
                                        },
                                    ) { ListRowLabel("View Type") }
                                }
                                item(key = "hi") {
                                    ListRow(
                                        selected = false,
                                        onClick = { onOpenConfigEditor(MeshConfigItem.HIGH_COLOR) },
                                        uDp = grid.uDp,
                                        leadingContent = {
                                            ListRowIcon(
                                                icon = DinghyIcons.HdrStrong,
                                                uDp = grid.uDp,
                                                tint = t.text2,
                                                contentDescription = "High Color",
                                            )
                                        },
                                    ) { ListRowLabel("High Color") }
                                }
                                item(key = "lo") {
                                    ListRow(
                                        selected = false,
                                        onClick = { onOpenConfigEditor(MeshConfigItem.LOW_COLOR) },
                                        uDp = grid.uDp,
                                        leadingContent = {
                                            ListRowIcon(
                                                icon = DinghyIcons.HdrWeak,
                                                uDp = grid.uDp,
                                                tint = t.text2,
                                                contentDescription = "Low Color",
                                            )
                                        },
                                    ) { ListRowLabel("Low Color") }
                                }
                                item(key = "pv") {
                                    ListRow(
                                        selected = false,
                                        onClick = { onOpenConfigEditor(MeshConfigItem.PREVIEW) },
                                        uDp = grid.uDp,
                                        leadingContent = {
                                            ListRowIcon(
                                                icon = DinghyIcons.Preview,
                                                uDp = grid.uDp,
                                                tint = t.text2,
                                                contentDescription = "Preview",
                                            )
                                        },
                                    ) { ListRowLabel("Preview") }
                                }
                            }
                            FootButtonBar(
                                uDp = grid.uDp,
                                actions = listOf(
                                    FootAction(
                                        label = stringResource(R.string.common_back),
                                        icon = DinghyIcons.Back,
                                        onClick = onBackToProfileList,
                                        intent = Intent.Accent,
                                        contentDescription = stringResource(R.string.common_back),
                                    ),
                                ),
                            )
                        }

                        is MeshFieldMode.MeshConfigEditor -> {
                            // Field: config list stays visible (context for the Focus editor).
                            // Back returns to the MeshConfig list.
                            ListBlock(modifier = Modifier.weight(1f)) {
                                item(key = "vt") {
                                    ListRow(
                                        selected = fieldMode.item == MeshConfigItem.VIEW_TYPE,
                                        onClick = { onOpenConfigEditor(MeshConfigItem.VIEW_TYPE) },
                                        uDp = grid.uDp,
                                        leadingContent = {
                                            ListRowIcon(
                                                icon = DinghyIcons.MeshViewIso,
                                                uDp = grid.uDp,
                                                tint = t.text2,
                                                contentDescription = "View Type",
                                            )
                                        },
                                    ) { ListRowLabel("View Type") }
                                }
                                item(key = "hi") {
                                    ListRow(
                                        selected = fieldMode.item == MeshConfigItem.HIGH_COLOR,
                                        onClick = { onOpenConfigEditor(MeshConfigItem.HIGH_COLOR) },
                                        uDp = grid.uDp,
                                        leadingContent = {
                                            ListRowIcon(
                                                icon = DinghyIcons.HdrStrong,
                                                uDp = grid.uDp,
                                                tint = t.text2,
                                                contentDescription = "High Color",
                                            )
                                        },
                                    ) { ListRowLabel("High Color") }
                                }
                                item(key = "lo") {
                                    ListRow(
                                        selected = fieldMode.item == MeshConfigItem.LOW_COLOR,
                                        onClick = { onOpenConfigEditor(MeshConfigItem.LOW_COLOR) },
                                        uDp = grid.uDp,
                                        leadingContent = {
                                            ListRowIcon(
                                                icon = DinghyIcons.HdrWeak,
                                                uDp = grid.uDp,
                                                tint = t.text2,
                                                contentDescription = "Low Color",
                                            )
                                        },
                                    ) { ListRowLabel("Low Color") }
                                }
                                item(key = "pv") {
                                    ListRow(
                                        selected = fieldMode.item == MeshConfigItem.PREVIEW,
                                        onClick = { onOpenConfigEditor(MeshConfigItem.PREVIEW) },
                                        uDp = grid.uDp,
                                        leadingContent = {
                                            ListRowIcon(
                                                icon = DinghyIcons.Preview,
                                                uDp = grid.uDp,
                                                tint = t.text2,
                                                contentDescription = "Preview",
                                            )
                                        },
                                    ) { ListRowLabel("Preview") }
                                }
                            }
                            FootButtonBar(
                                uDp = grid.uDp,
                                actions = listOf(
                                    FootAction(
                                        label = stringResource(R.string.common_back),
                                        icon = DinghyIcons.Back,
                                        onClick = onBackToConfigList,
                                        intent = Intent.Accent,
                                        contentDescription = stringResource(R.string.common_back),
                                    ),
                                ),
                            )
                        }
                    }
                },
            )

            // Toast overlay
            if (toastError != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SeverityToast(
                        severity = Severity.Error,
                        text = toastError!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismissError() },
                    )
                }
            }

            // Remove profile ConfirmGuard (destructive/red, D-12)
            if (showRemoveGuard) {
                ConfirmGuard(
                    title = stringResource(R.string.mesh_remove),
                    message = stringResource(R.string.mesh_remove_confirm),
                    confirmLabel = stringResource(R.string.mesh_remove),
                    destructive = true,
                    onConfirm = onRemoveConfirm,
                    onCancel = onRemoveCancel,
                )
            }

            // SAVE_CONFIG restart ConfirmGuard (amber/warn, D-14)
            if (showSaveConfigGuard) {
                ConfirmGuard(
                    title = stringResource(R.string.calibration_save_config),
                    message = stringResource(R.string.calibration_save_config_confirm),
                    confirmLabel = stringResource(R.string.calibration_save_config),
                    warn = true,
                    onConfirm = onSaveConfigConfirm,
                    onCancel = onSaveConfigCancel,
                )
            }
        }
    }
}

/**
 * The Focus region of the BedMesh screen — the heatmap + empty-state. (Scale-mode cycling moved to
 * a "Color Scale" Field list row; no overlay here anymore.)
 *
 * The [BedMeshHeatmapHost] already contains the `LocalInspectionMode` → placeholder branch
 * internally (Phase-22 D-05/D-04), so no extra preview guard is needed here.
 * The P22 `applyTokens` equality guard lives inside [BedMeshHeatmapHost]'s `AndroidView.update`
 * block — it is preserved exactly as-is; this function does NOT recreate the factory.
 */
@Composable
private fun BedMeshFocusRegion(
    vm: BedMeshVm,
    selectedProfile: String?,
    tokens: ThemeTokens,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    // Resolve the model to render: if a saved profile is selected AND it differs from the active
    // mesh, preview that profile. Otherwise render the live model.
    val renderModel = remember(vm.model, selectedProfile) {
        val sel = selectedProfile
        if (sel != null && sel != vm.model.profileName) vm.model.previewOf(sel) ?: vm.model
        else vm.model
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
            // Gate on renderModel.isEmpty — a previewed saved profile must render even when no live
            // mesh is loaded (Codex correctness: renderModel != vm.model when previewing).
            if (renderModel.isEmpty) {
                // Empty-state Focus: no active mesh loaded (and no previewable selection)
                Column(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(t.rCard))
                        .border(BorderStroke(2.dp, t.outline), RoundedCornerShape(t.rCard))
                        .background(t.surface)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 27-review WR-04: registry-backed empty-state glyph (same grid_off, promoted verbatim).
                    DinghyIconView(icon = DinghyIcons.MeshEmpty, tint = t.text3, sizeDp = fsSp(48f, t.fs).dp)
                    Text(
                        text = stringResource(R.string.mesh_no_active_mesh),
                        color = t.text,
                        style = DinghyType.focusHeader.toTextStyle(t),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.mesh_no_active_mesh_hint),
                        color = t.text2,
                        style = DinghyType.caption.toTextStyle(t),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                // Live heatmap OR saved-profile preview — P22 equality guard preserved (factory
                // inside BedMeshHeatmapHost, recomposition only triggers update, never recreates).
                // Real per-printer viewMode + color selectors wired from vm (replaces Task-6 shim).
                val lowArgb = resolveMeshColor(tokens, vm.lowColorSel).toArgb()
                val highArgb = resolveMeshColor(tokens, vm.highColorSel).toArgb()
                BedMeshHeatmapHost(
                    tokens = tokens,
                    model = renderModel,
                    scaleMode = vm.scaleMode,
                    viewMode = when (vm.viewType) {
                        BedMeshViewType.HEATMAP -> BedMeshHeatmapView.ViewMode.HEATMAP
                        BedMeshViewType.PROBE_POINTS -> BedMeshHeatmapView.ViewMode.PROBE_POINTS
                        BedMeshViewType.ISO -> BedMeshHeatmapView.ViewMode.ISO_WIREFRAME
                    },
                    lowColorArgb = lowArgb,
                    highColorArgb = highArgb,
                    midColorArgb = MESH_MID_NEUTRAL,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(t.rCard))
                        .border(BorderStroke(2.dp, t.outline), RoundedCornerShape(t.rCard)),
                )
            }
        }
    }
}

/**
 * Focus content for the Mesh Config editor modes (Task 9).
 *
 * Dispatches to the appropriate editor based on [item]:
 * - VIEW_TYPE → [ViewTypeSelector] (description + 3 icon-only foot buttons)
 * - HIGH_COLOR / LOW_COLOR → [PoolColorPicker] (data-pool swatch grid)
 * - PREVIEW → [BedMeshFocusRegion] with current settings (selectedProfile=null → live mesh)
 */
@Composable
private fun MeshConfigEditorFocus(
    item: MeshConfigItem,
    vm: BedMeshVm,
    selectedProfile: String?,
    tokens: ThemeTokens,
    onSetViewType: (BedMeshViewType) -> Unit,
    onSetHighColorSel: (Int) -> Unit,
    onSetLowColorSel: (Int) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    when (item) {
        MeshConfigItem.VIEW_TYPE -> ViewTypeSelector(
            current = vm.viewType,
            onPick = onSetViewType,
            t = tokens,
            uDp = uDp,
            modifier = modifier,
        )
        MeshConfigItem.HIGH_COLOR -> PoolColorPicker(
            selected = vm.highColorSel,
            onPick = onSetHighColorSel,
            t = tokens,
            modifier = modifier,
        )
        MeshConfigItem.LOW_COLOR -> PoolColorPicker(
            selected = vm.lowColorSel,
            onPick = onSetLowColorSel,
            t = tokens,
            modifier = modifier,
        )
        MeshConfigItem.PREVIEW -> BedMeshFocusRegion(
            vm = vm,
            selectedProfile = null,  // always preview with current settings
            tokens = tokens,
            uDp = uDp,
            modifier = modifier,
        )
    }
}

/**
 * View-type selector: a description of the currently-selected view fills the space above three
 * icon-only foot buttons — **2D Heatmap** (HEATMAP), **3D Mesh** (ISO), **Probe Points** (PROBE_POINTS),
 * in that order. Tapping a button selects that view and the description above updates to match; the
 * active view's button carries a soft accent fill.
 */
@Composable
private fun ViewTypeSelector(
    current: BedMeshViewType,
    onPick: (BedMeshViewType) -> Unit,
    t: ThemeTokens,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val (name, description) = viewTypeBlurb(current)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Description of the selected view fills the space above the foot buttons.
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = name, color = t.text, style = DinghyType.focusHeroLabel.toTextStyle(t))
            Spacer(Modifier.height(8.dp))
            Text(text = description, color = t.text2, style = DinghyType.body.toTextStyle(t))
        }
        // Three icon-only foot buttons (count-driven); the active view's button gets a soft accent fill.
        FootButtonBar(
            uDp = uDp,
            actions = listOf(
                FootAction(
                    label = "2D Heatmap",
                    icon = DinghyIcons.MeshView2D,
                    onClick = { onPick(BedMeshViewType.HEATMAP) },
                    intent = Intent.Accent,
                    contentDescription = "2D Heatmap",
                    fill = if (current == BedMeshViewType.HEATMAP) t.accentSoft else null,
                ),
                FootAction(
                    label = "3D Mesh",
                    icon = DinghyIcons.MeshViewIso,
                    onClick = { onPick(BedMeshViewType.ISO) },
                    intent = Intent.Accent,
                    contentDescription = "3D Mesh",
                    fill = if (current == BedMeshViewType.ISO) t.accentSoft else null,
                ),
                FootAction(
                    label = "Probe Points",
                    icon = DinghyIcons.MeshViewProbe,
                    onClick = { onPick(BedMeshViewType.PROBE_POINTS) },
                    intent = Intent.Accent,
                    contentDescription = "Probe Points",
                    fill = if (current == BedMeshViewType.PROBE_POINTS) t.accentSoft else null,
                ),
            ),
        )
    }
}

/** Name + brief description for each bed-mesh view type (shown above the View Type foot buttons). */
private fun viewTypeBlurb(v: BedMeshViewType): Pair<String, String> = when (v) {
    BedMeshViewType.HEATMAP -> "2D Heatmap" to
        "Top-down map. Each cell is shaded by height across the color ramp — the classic flat bed-mesh view."
    BedMeshViewType.ISO -> "3D Mesh" to
        "Isometric wireframe. The grid lifts by deviation to show the bed's shape, with lines colored by height."
    BedMeshViewType.PROBE_POINTS -> "Probe Points" to
        "Just the measured probe points as height-colored dots — no fill or interpolation between them."
}

/** Scale-mode display label (Mono numeric for the ± modes). */
private fun BedMeshHeatmapView.ScaleMode.displayLabel(): String = when (this) {
    BedMeshHeatmapView.ScaleMode.RELATIVE -> "RELATIVE"
    BedMeshHeatmapView.ScaleMode.PLATE -> "PLATE"
    BedMeshHeatmapView.ScaleMode.PM_010 -> "±0.10"
    BedMeshHeatmapView.ScaleMode.PM_025 -> "±0.25"
    BedMeshHeatmapView.ScaleMode.PM_050 -> "±0.50"
    BedMeshHeatmapView.ScaleMode.PM_100 -> "±1.00"
}

// ─── Edit-morph UI ─────────────────────────────────────────────────────────────────────────────

/**
 * Alphanumeric name-entry field for the mesh edit morph. Extracted from the SaveName Field-takeover
 * (same keyboard type, same validation UX) so both paths share one rendering component.
 *
 * Disabled (read-only) when [readOnly] is true — used for PREVIEW_NONACTIVE where the profile name
 * is informational and cannot be changed from the Focus.
 */
@Composable
private fun MeshNameField(
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean,
    t: ThemeTokens,
) {
    val valid = PrinterCommands.isValidProfileName(value)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.mesh_save_name_label),
            color = t.text,
            style = DinghyType.listLabel.toTextStyle(t),
            modifier = Modifier.padding(bottom = 4.dp),
        )
        TokenTextField(
            value = value,
            onValueChange = if (readOnly) ({}) else onValueChange,
            label = stringResource(R.string.mesh_save_name_hint),
            isError = value.isNotEmpty() && !valid && !readOnly,
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isNotEmpty() && !valid && !readOnly) {
            Text(
                text = stringResource(R.string.mesh_save_name_invalid),
                color = t.stop,
                style = DinghyType.caption.toTextStyle(t),
            )
        }
    }
}

/**
 * Docked-action Focus composable for the mesh profile edit morph (Task 8).
 *
 * Shows a name field + contextual button matrix per [MeshEditKind]:
 * - [MeshEditKind.ACTIVE_UNSAVED]: Save (enabled when name is valid) — names the in-memory mesh.
 * - [MeshEditKind.ACTIVE_SAVED]: Save (rename, enabled when valid & changed) + Delete.
 * - [MeshEditKind.PREVIEW_NONACTIVE]: Apply (load this profile) + Delete.
 *
 * Read-only name field for [MeshEditKind.PREVIEW_NONACTIVE] (not the active mesh — cannot be
 * renamed from this context; Apply loads it first).
 *
 * All buttons use owner-picked glyphs (DinghyIcons.CheckCircle / Save / Delete — Task 8 brief).
 */
@Composable
private fun MeshEditForm(
    kind: MeshEditKind,
    targetName: String,
    onApply: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    t: ThemeTokens,
    uDp: Dp,
) {
    var name by rememberSaveable(targetName) {
        mutableStateOf(if (kind == MeshEditKind.ACTIVE_UNSAVED) "" else targetName)
    }
    val readOnly = kind == MeshEditKind.PREVIEW_NONACTIVE
    val nameValid = PrinterCommands.isValidProfileName(name)
    val nameChanged = name != targetName

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MeshNameField(
            value = name,
            onValueChange = { name = it },
            readOnly = readOnly,
            t = t,
        )
        Spacer(modifier = Modifier.weight(1f))

        // Primary action row: Apply (preview) or Save (active unsaved/saved) — 1U docked buttons.
        CompositionLocalProvider(LocalUnitDp provides uDp) {
            Row(
                modifier = Modifier.fillMaxWidth().height(uDp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (kind) {
                    MeshEditKind.PREVIEW_NONACTIVE -> {
                        OutlinedControl(
                            label = stringResource(R.string.mesh_apply),
                            onClick = onApply,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Go,
                            icon = DinghyIcons.CheckCircle,
                            contentDescription = stringResource(R.string.mesh_apply),
                        )
                    }
                    MeshEditKind.ACTIVE_UNSAVED -> {
                        OutlinedControl(
                            label = stringResource(R.string.mesh_save_confirm),
                            onClick = { onSave(name) },
                            modifier = Modifier.weight(1f),
                            intent = Intent.Go,
                            icon = DinghyIcons.Save,
                            enabled = nameValid,
                            contentDescription = stringResource(R.string.mesh_save_confirm),
                        )
                    }
                    MeshEditKind.ACTIVE_SAVED -> {
                        OutlinedControl(
                            label = stringResource(R.string.mesh_save_confirm),
                            onClick = { onSave(name) },
                            modifier = Modifier.weight(1f),
                            intent = Intent.Go,
                            icon = DinghyIcons.Save,
                            enabled = nameValid && nameChanged,
                            contentDescription = stringResource(R.string.mesh_save_confirm),
                        )
                    }
                }
                OutlinedControl(
                    label = stringResource(R.string.common_cancel),
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    intent = Intent.Accent,
                    icon = DinghyIcons.DialogClose,
                    contentDescription = stringResource(R.string.common_cancel),
                )
            }
        }

        // Delete row — shown for any saved-profile target (active saved or previewing non-active).
        if (kind != MeshEditKind.ACTIVE_UNSAVED) {
            CompositionLocalProvider(LocalUnitDp provides uDp) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(uDp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedControl(
                        label = stringResource(R.string.mesh_remove),
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        intent = Intent.Danger,
                        icon = DinghyIcons.Delete,
                        contentDescription = stringResource(R.string.mesh_remove),
                    )
                }
            }
        }
    }
}
