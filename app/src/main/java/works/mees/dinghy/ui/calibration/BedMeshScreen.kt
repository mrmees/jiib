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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
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
import works.mees.dinghy.command.BedMeshProfileArgs
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.components.FloatingEStop
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.render.BedMeshHeatmapHost
import works.mees.dinghy.render.BedMeshHeatmapView
import works.mees.dinghy.ui.screen.TokenTextField
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
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
 * Field-takeover (D-11..D-13). Actions live in [FootButtonBar] (D-14), `gutter = null` (LAW).
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
        dispatcherPresent = dispatcher != null,
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
 * gutter = null (LAW).
 */
@Composable
internal fun BedMeshContent(
    vm: BedMeshVm,
    selectedProfile: String?,
    fieldMode: MeshFieldMode,
    showRemoveGuard: Boolean,
    showSaveConfigGuard: Boolean,
    toastError: String?,
    dispatcherPresent: Boolean,
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
                    BedMeshFocusRegion(
                        vm = vm,
                        tokens = t,
                        onCycleScaleMode = onCycleScaleMode,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
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
                                            fontFamily = Geist,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = fsSp(16f, t.fs).sp,
                                            textAlign = TextAlign.Center,
                                        )
                                        Text(
                                            text = stringResource(R.string.mesh_empty_state_hint),
                                            color = t.text3,
                                            fontFamily = Geist,
                                            fontSize = fsSp(15f, t.fs).sp,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            } else {
                                ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
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
                                                        fontFamily = Geist,
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = fsSp(15f, t.fs).sp,
                                                    )
                                                }
                                            } else null,
                                        ) {
                                            Text(
                                                text = name,
                                                color = t.text,
                                                fontFamily = GeistMono,
                                                fontWeight = FontWeight.Medium,
                                                // R11 type ramp: list-item labels at the 20sp default.
                                                fontSize = fsSp(20f, t.fs).sp,
                                            )
                                        }
                                    }
                                }
                            }

                            // D-14 state-adaptive FootButtonBar
                            FootButtonBar(
                                uDp = grid.uDp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                when {
                                    !vm.homed -> {
                                        // Unhomed branch: Back (accent, FIRST — R5/R8) + Home All
                                        // (go — homing is this state's expected action, R19).
                                        OutlinedControl(
                                            label = "",
                                            onClick = onBack,
                                            modifier = Modifier.weight(1f),
                                            intent = Intent.Accent,
                                            icon = DinghyIcons.Back,
                                            contentDescription = stringResource(R.string.common_back),
                                        )
                                        OutlinedControl(
                                            label = stringResource(R.string.calibration_home_all),
                                            onClick = onHomeAll,
                                            modifier = Modifier.weight(1f),
                                            intent = Intent.Go,
                                            enabled = dispatcherPresent,
                                        )
                                    }
                                    selectedProfile != null -> {
                                        // Profile selected: Back (accent, FIRST) + Apply (go —
                                        // the selection state's expected action, R5) + Remove (stop).
                                        OutlinedControl(
                                            label = "",
                                            onClick = onBack,
                                            modifier = Modifier.weight(1f),
                                            intent = Intent.Accent,
                                            icon = DinghyIcons.Back,
                                            contentDescription = stringResource(R.string.common_back),
                                        )
                                        OutlinedControl(
                                            label = stringResource(R.string.mesh_apply),
                                            onClick = {
                                                selectedProfile?.let { onApplyProfile(it) }
                                            },
                                            modifier = Modifier.weight(1f),
                                            intent = Intent.Go,
                                            enabled = dispatcherPresent,
                                        )
                                        OutlinedControl(
                                            label = stringResource(R.string.mesh_remove),
                                            onClick = onShowRemoveGuard,
                                            modifier = Modifier.weight(1f),
                                            intent = Intent.Danger,
                                            enabled = dispatcherPresent,
                                        )
                                    }
                                    else -> {
                                        // Homed, no selection: Back (accent, FIRST) + Calibrate
                                        // (go — the screen's expected action, R5/R19) + Save
                                        // (go — accept/commit class, R5).
                                        OutlinedControl(
                                            label = "",
                                            onClick = onBack,
                                            modifier = Modifier.weight(1f),
                                            intent = Intent.Accent,
                                            icon = DinghyIcons.Back,
                                            contentDescription = stringResource(R.string.common_back),
                                        )
                                        OutlinedControl(
                                            label = stringResource(R.string.mesh_calibrate),
                                            onClick = onCalibrate,
                                            modifier = Modifier.weight(1f),
                                            intent = Intent.Go,
                                            enabled = dispatcherPresent,
                                        )
                                        // WR-05 (27-review): with no active mesh, BED_MESH_PROFILE SAVE
                                        // errors in Klipper — Save is gated on a mesh being loaded (the
                                        // empty-state Focus already tells the user to calibrate first).
                                        OutlinedControl(
                                            label = stringResource(R.string.mesh_save),
                                            onClick = onShowSaveName,
                                            modifier = Modifier.weight(1f),
                                            intent = Intent.Go,
                                            enabled = dispatcherPresent && !vm.isEmpty,
                                        )
                                    }
                                }
                            }
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
                                    fontFamily = Geist,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = fsSp(20f, t.fs).sp,
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
                                        fontFamily = Geist,
                                        fontSize = fsSp(15f, t.fs).sp,
                                    )
                                }
                            }

                            // SaveName foot: Save (go — accept/commit, R5; disabled until valid)
                            // + Cancel (danger, C7 cancel-with-loss)
                            FootButtonBar(
                                uDp = grid.uDp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                OutlinedControl(
                                    label = stringResource(R.string.mesh_save_confirm),
                                    onClick = { onSaveNameConfirm(saveName) },
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Go,
                                    enabled = valid && dispatcherPresent,
                                )
                                // C7: cancel-with-loss → Intent.Danger (red)
                                OutlinedControl(
                                    label = stringResource(R.string.common_cancel),
                                    onClick = onSaveNameCancel,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Danger,
                                )
                            }
                        }
                    }
                },
                gutter = null,
            )

            // FloatingEStop top-left corner reservation (UAT-4).
            // Calibration = pop-to-root foot-gun: BedMesh is only reachable while idle,
            // so the E-stop overlay is suppressed here (visible = false).
            FloatingEStop(
                visible = false,
                onClick = {},
                uDp = grid.uDp,
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
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
                        fontFamily = Geist,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = fsSp(22f, t.fs).sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.mesh_no_active_mesh_hint),
                        color = t.text2,
                        fontFamily = Geist,
                        fontSize = fsSp(15f, t.fs).sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                // Live heatmap — P22 equality guard preserved (factory inside BedMeshHeatmapHost,
                // recomposition only triggers update, never recreates the AndroidView).
                BedMeshHeatmapHost(
                    tokens = tokens,
                    model = vm.model,
                    scaleMode = vm.scaleMode,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(t.rCard))
                        .border(BorderStroke(2.dp, t.outline), RoundedCornerShape(t.rCard)),
                )
            }

            // Scale-mode toggle overlay inset top-left (white/setting intent, ≥64dp, cycles D-09).
            Box(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                ScaleToggle(label = vm.scaleMode.displayLabel(), onClick = onCycleScaleMode)
            }
        }
    }
}

/** Scale-mode toggle: `expand` glyph + current mode (Mono for numeric), white/setting intent. */
@Composable
private fun ScaleToggle(label: String, onClick: () -> Unit) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Row(
        Modifier
            .heightIn(min = 64.dp)
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
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(16f, t.fs).sp,
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
