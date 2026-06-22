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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.sp
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
import androidx.compose.ui.graphics.toArgb
import works.mees.dinghy.render.BedMeshHeatmapHost
import works.mees.dinghy.render.BedMeshHeatmapView
import works.mees.dinghy.theme.seriesColor
import works.mees.dinghy.ui.screen.TokenTextField
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.theme.fsSp

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

    // Confirm guard flags (local only — transient overlays, not navigation state).
    var showRemoveGuard by remember { mutableStateOf(false) }
    var showSaveConfigGuard by remember { mutableStateOf(false) }

    // WR-05 (27-review): the stable dispatch keys of the two persist-relevant profile commands
    // (their key lambdas ignore the profile name). A Failure under one of these keys means nothing
    // was persisted — used below to retract the SAVE_CONFIG guard instead of inviting a pointless
    // Klipper restart.
    val profilePersistKeys = remember {
        val probe = BedMeshProfileArgs("")
        setOf(
            CommandRegistry.bedMeshProfileSave.dispatchKey(probe),
            CommandRegistry.bedMeshProfileRemove.dispatchKey(probe),
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
        showRemoveGuard = showRemoveGuard,
        showSaveConfigGuard = showSaveConfigGuard,
        toastError = toastError,
        isPrinting = isPrinting,
        dispatcherPresent = dispatcher != null,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onCycleScaleMode = { holder.cycleScaleMode() },
        onSelectProfile = { name -> selectedProfile = name },
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
            val name = selectedProfile
            if (d != null && name != null) {
                d.dispatch(CommandRegistry.bedMeshProfileRemove, BedMeshProfileArgs(name))
                // WR-02 (27-review): BED_MESH_PROFILE REMOVE only mutates Klipper's RUNTIME state —
                // without SAVE_CONFIG the profile resurrects on the next firmware restart. Surface
                // the amber restart guard after the remove dispatch, mirroring the save path.
                showSaveConfigGuard = true
            }
            selectedProfile = null
            showRemoveGuard = false
        },
        onRemoveCancel = { showRemoveGuard = false },
        onSaveConfigConfirm = {
            dispatcher?.dispatch(CommandRegistry.saveConfig, Unit)
            showSaveConfigGuard = false
        },
        onSaveConfigCancel = { showSaveConfigGuard = false },
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
 */
internal sealed class MeshFieldMode {
    data object ProfileList : MeshFieldMode()
    data class SaveName(val prefill: String) : MeshFieldMode()
}

/** Saver so [MeshFieldMode] state survives process death / rotation (only [prefill] needs persisting). */
internal val MeshFieldModeSaver = androidx.compose.runtime.saveable.Saver<MeshFieldMode, Any>(
    save = { mode ->
        when (mode) {
            is MeshFieldMode.ProfileList -> "ProfileList"
            is MeshFieldMode.SaveName -> "SaveName:${mode.prefill}"
        }
    },
    restore = { raw ->
        val s = raw as? String ?: return@Saver MeshFieldMode.ProfileList
        when {
            s == "ProfileList" -> MeshFieldMode.ProfileList
            s.startsWith("SaveName:") -> MeshFieldMode.SaveName(s.removePrefix("SaveName:"))
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
    showRemoveGuard: Boolean,
    showSaveConfigGuard: Boolean,
    toastError: String?,
    isPrinting: Boolean,
    dispatcherPresent: Boolean,
    onEmergencyStop: () -> Unit,
    onCycleScaleMode: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onShowSaveName: () -> Unit,
    onSaveNameConfirm: (String) -> Unit,
    onSaveNameCancel: () -> Unit,
    onApplyProfile: (String) -> Unit,
    onShowRemoveGuard: () -> Unit,
    onRemoveConfirm: () -> Unit,
    onRemoveCancel: () -> Unit,
    onSaveConfigConfirm: () -> Unit,
    onSaveConfigCancel: () -> Unit,
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
                    ) {
                        BedMeshFocusRegion(
                            vm = vm,
                            tokens = t,
                            onCycleScaleMode = onCycleScaleMode,
                            uDp = grid.uDp,
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                        )
                    }
                },
                field = {
                    when (fieldMode) {
                        is MeshFieldMode.ProfileList -> {
                            // Profile list (D-11/D-12)
                            if (vm.profileNames.isEmpty()) {
                                // Empty-state: no saved profiles
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.mesh_empty_state),
                                            color = t.text2,
                                            style = DinghyType.body.toTextStyle(t),
                                            textAlign = TextAlign.Center,
                                        )
                                        Text(
                                            text = stringResource(R.string.mesh_empty_state_hint),
                                            color = t.text3,
                                            style = DinghyType.caption.toTextStyle(t),
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            } else {
                                ListBlock(modifier = Modifier.weight(1f)) {
                                    items(vm.profileNames, key = { it }) { name ->
                                        val isActive = name == vm.model.profileName && !vm.isEmpty
                                        ListRow(
                                            selected = name == selectedProfile,
                                            onClick = { onSelectProfile(name) },
                                            uDp = grid.uDp,
                                            trailingContent = if (isActive) {
                                                {
                                                    Text(
                                                        text = stringResource(R.string.mesh_profile_active),
                                                        color = t.accent2,
                                                        style = DinghyType.caption.toTextStyle(t),
                                                    )
                                                }
                                            } else null,
                                        ) {
                                            // Canonical list-label look (Geist SemiBold 20) — the
                                            // profile NAME is the row label; mono stays for VALUES.
                                            ListRowLabel(name)
                                        }
                                    }
                                }
                            }

                            // D-14 state-adaptive FootButtonBar
                            FootButtonBar(
                                uDp = grid.uDp,
                                actions = buildList {
                                    when {
                                        !vm.homed -> {
                                            // Unhomed branch: Back (accent, FIRST — R5/R8) + Home All
                                            // (go — homing is this state's expected action, R19).
                                            add(FootAction(
                                                label = stringResource(R.string.common_back),
                                                icon = DinghyIcons.Back,
                                                onClick = onBack,
                                                intent = Intent.Accent,
                                                contentDescription = stringResource(R.string.common_back),
                                            ))
                                            add(footAction(
                                                ControlSpecs.calibrationHomeAll,
                                                onClick = onHomeAll,
                                                enabled = dispatcherPresent,
                                            ))
                                        }
                                        selectedProfile != null -> {
                                            // Profile selected: Back (accent, FIRST) + Apply (go —
                                            // the selection state's expected action, R5) + Remove (stop).
                                            add(FootAction(
                                                label = stringResource(R.string.common_back),
                                                icon = DinghyIcons.Back,
                                                onClick = onBack,
                                                intent = Intent.Accent,
                                                contentDescription = stringResource(R.string.common_back),
                                            ))
                                            add(FootAction(
                                                label = stringResource(R.string.mesh_apply),
                                                icon = DinghyIcons.CheckCircle,
                                                onClick = { selectedProfile?.let { onApplyProfile(it) } },
                                                intent = Intent.Go,
                                                enabled = dispatcherPresent,
                                            ))
                                            add(FootAction(
                                                label = stringResource(R.string.mesh_remove),
                                                icon = DinghyIcons.Delete,
                                                onClick = onShowRemoveGuard,
                                                intent = Intent.Danger,
                                                enabled = dispatcherPresent,
                                            ))
                                        }
                                        else -> {
                                            // Homed, no selection: Back (accent, FIRST) + Calibrate
                                            // (go — the screen's expected action, R5/R19) + Save
                                            // (go — accept/commit class, R5).
                                            add(FootAction(
                                                label = stringResource(R.string.common_back),
                                                icon = DinghyIcons.Back,
                                                onClick = onBack,
                                                intent = Intent.Accent,
                                                contentDescription = stringResource(R.string.common_back),
                                            ))
                                            add(FootAction(
                                                // owner 2026-06-17: play_circle (CalibrationRun), NOT
                                                // RoutineBedMesh/blur_linear — the Focus header already
                                                // shows blur_linear (routineIconToken(BED_MESH)); a foot
                                                // button reusing it = same-glyph-twice-on-one-screen.
                                                label = stringResource(R.string.mesh_calibrate),
                                                icon = DinghyIcons.CalibrationRun,
                                                onClick = onCalibrate,
                                                intent = Intent.Go,
                                                enabled = dispatcherPresent,
                                            ))
                                            // WR-05 (27-review): with no active mesh, BED_MESH_PROFILE SAVE
                                            // errors in Klipper — Save is gated on a mesh being loaded (the
                                            // empty-state Focus already tells the user to calibrate first).
                                            add(FootAction(
                                                label = stringResource(R.string.mesh_save),
                                                icon = DinghyIcons.Save,
                                                onClick = onShowSaveName,
                                                intent = Intent.Go,
                                                enabled = dispatcherPresent && !vm.isEmpty,
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
 * The Focus region of the BedMesh screen — the heatmap + scale-mode toggle + empty-state.
 *
 * The [BedMeshHeatmapHost] already contains the `LocalInspectionMode` → placeholder branch
 * internally (Phase-22 D-05/D-04), so no extra preview guard is needed here.
 * The P22 `applyTokens` equality guard lives inside [BedMeshHeatmapHost]'s `AndroidView.update`
 * block — it is preserved exactly as-is; this function does NOT recreate the factory.
 */
@Composable
private fun BedMeshFocusRegion(
    vm: BedMeshVm,
    tokens: ThemeTokens,
    onCycleScaleMode: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
            if (vm.isEmpty) {
                // Empty-state Focus: no active mesh loaded
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
                // Live heatmap — P22 equality guard preserved (factory inside BedMeshHeatmapHost,
                // recomposition only triggers update, never recreates the AndroidView).
                // TODO(Task 7): replace shim args with real per-printer viewMode + color selectors.
                BedMeshHeatmapHost(
                    tokens = tokens,
                    model = vm.model,
                    scaleMode = vm.scaleMode,
                    viewMode = BedMeshHeatmapView.ViewMode.HEATMAP,
                    lowColorArgb = tokens.seriesColor(1).toArgb(),
                    highColorArgb = tokens.seriesColor(0).toArgb(),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(t.rCard))
                        .border(BorderStroke(2.dp, t.outline), RoundedCornerShape(t.rCard)),
                )
            }

            // Scale-mode toggle overlay inset top-left (white/setting intent, 1U, cycles D-09).
            Box(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                ScaleToggle(label = vm.scaleMode.displayLabel(), onClick = onCycleScaleMode, uDp = uDp)
            }
        }
    }
}

/** Scale-mode toggle: `expand` glyph + current mode (Mono for numeric), white/setting intent. */
@Composable
private fun ScaleToggle(label: String, onClick: () -> Unit, uDp: Dp) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Row(
        Modifier
            .heightIn(min = uDp) // 1U (owner All-1U; was a pinned 64dp touch-floor)
            .clip(shape)
            .border(BorderStroke(2.dp, t.outline), shape)
            .background(t.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // C-G1: registry-routed (reuses the BabystepExpand "expand" ligature — owner-sanctioned
        // reuse precedent, DinghyIcons.kt; decorative beside the label → null a11y).
        DinghyIconView(
            icon = DinghyIcons.BabystepExpand,
            tint = t.text,
            sizeDp = fsSp(22f, t.fs).dp,
            contentDescription = null,
        )
        Text(
            text = label,
            color = t.text,
            style = DinghyType.dataMeta.toTextStyle(t),
        )
    }
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
