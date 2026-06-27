package works.mees.jiib.ui.calibration

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import works.mees.jiib.R
import works.mees.jiib.calibration.ApplyBabystepHolder
import works.mees.jiib.calibration.ApplyBabystepVm
import works.mees.jiib.calibration.ProbeHubHolder
import works.mees.jiib.calibration.ProbeTool
import works.mees.jiib.calibration.ProbeToolEntry
import works.mees.jiib.command.CommandDispatcher
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.GatingState
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.ConfirmGuard
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.ListRowIcon
import works.mees.jiib.designsystem.components.ListRowLabel
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.control.OutlinedControl
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.FocusInset
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.theme.fsSp

/**
 * Single Focus-centric Probe screen — R1 replacement for the old ProbeHub + 5 tool-route sub-tree.
 *
 * Mirrors [works.mees.jiib.ui.finetune.FineTuneScreen]'s structure:
 *  - Field = [ListBlock] of selectable [ListRow]s over [ProbeHubHolder.tools], D-06 dim for unsupported
 *  - Focus = [FocusFrame] with placeholder body (Phase 2 will replace with per-tool content)
 *  - FootButtonBar inside the `field = {}` lambda = Back only (accent, R8 first)
 *
 * D-05: pre-select first tool + reconcile — `selected` is updated synchronously during composition
 * whenever the tools list changes or selected falls outside the current list.
 *
 * Null-selected guard: when `selected == null` (empty tools, disconnected, capabilities not yet
 * arrived), Focus falls back to the launcher "Probe" title + [JiibIcons.RoutineProbeCalibrate];
 * Back is always available and the Focus body is empty. `probeToolTitleRes(null)` is NEVER called.
 *
 * This LIVE overload resolves flows from [AppContainer] + [ProbeHubHolder] and delegates rendering
 * to the stateless [ProbeContent] — the same surface the @Preview matrix targets.
 */
@Composable
fun ProbeScreen(
    container: AppContainer,
    probeHubHolder: ProbeHubHolder,
    applyBabystepHolder: ApplyBabystepHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tools by probeHubHolder.tools.collectAsStateWithLifecycle()
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val gating by container.gatingState.collectAsStateWithLifecycle(initialValue = GatingState.Idle)
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    val applyBabystepVm by applyBabystepHolder.vm.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf<ProbeTool?>(null) }
    // D-05: pre-select first tool + reconcile against the current visible list.
    // Writing state during composition is valid here — the condition settles after at most one
    // extra recomposition (once selected is set, the `if` body no longer fires).
    if (selected == null || tools.none { it.tool == selected }) {
        selected = tools.firstOrNull()?.tool
    }

    ProbeContent(
        tools = tools,
        selected = selected,
        applyBabystepVm = applyBabystepVm,
        dispatcher = dispatcher,
        isPrinting = isPrinting,
        gating = gating,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onSelect = { selected = it },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless Probe rendering surface — the @Preview matrix targets this composable.
 *
 * [probeToolIconToken] and [probeToolTitleRes] are the same helpers defined in
 * [ProbeHubScreen.kt]; they live there until that file is deleted in Phase 2, after which the
 * definitions will be moved here. Both are `internal` and therefore visible module-wide.
 *
 * @param applyBabystepVm the resolved Apply Babystepping view-model. Defaults to a null-value
 *   instance so previews that don't exercise this tool don't need to pass one.
 * @param dispatcher the live session dispatcher (null while idle). Passed into [ApplyBabystepBody]
 *   for Apply / Save dispatches; also used via [onEmergencyStop] for the e-stop header dock.
 */
@Composable
internal fun ProbeContent(
    tools: List<ProbeToolEntry>,
    selected: ProbeTool?,
    applyBabystepVm: ApplyBabystepVm = ApplyBabystepVm(),
    dispatcher: CommandDispatcher? = null,
    isPrinting: Boolean = false,
    gating: GatingState = GatingState.Idle,
    onEmergencyStop: () -> Unit = {},
    onSelect: (ProbeTool) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    // Null-selected guard: when selected == null use the hub identity fallback in the Focus header.
    val focusTitle = selected?.let { stringResource(probeToolTitleRes(it)) }
        ?: stringResource(R.string.probe_hub_title)
    val focusIcon = selected?.let { probeToolIconToken(it) } ?: JiibIcons.RoutineProbeCalibrate

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = focusTitle,
                    icon = focusIcon,
                    uDp = grid.uDp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    isPrinting = isPrinting,
                    safetyActive = gating !is GatingState.Idle,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                    contentInset = FocusInset / 2,
                ) {
                    // Per-tool Focus body dispatch.
                    // TODO(refocus later tasks): Z-Offset & Eddy Calibrate session bodies must
                    //  re-introduce the D-09 BackHandler (Back-suppress during an active manual-probe
                    //  session) — removed with the old routes in R1.
                    when (selected) {
                        ProbeTool.APPLY_BABYSTEP -> ApplyBabystepBody(
                            vm = applyBabystepVm,
                            dispatcher = dispatcher,
                            uDp = grid.uDp,
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> {
                            // Placeholder body for tools not yet wired (later tasks).
                            if (selected != null) {
                                Text(
                                    text = stringResource(probeToolTitleRes(selected)),
                                    style = JiibType.body.toTextStyle(t),
                                    color = t.text,
                                )
                                Text(
                                    text = "—",
                                    style = JiibType.caption.toTextStyle(t),
                                    color = t.text2,
                                )
                            }
                        }
                    }
                }
            },
            field = {
                // Field: tool list + FootButtonBar (FootButtonBar lives INSIDE field — shared pattern).
                ListBlock(modifier = Modifier.weight(1f)) {
                    items(tools, key = { it.tool.name }) { entry ->
                        ListRow(
                            selected = entry.tool == selected,
                            onClick = { onSelect(entry.tool) },
                            uDp = grid.uDp,
                            leadingContent = {
                                // R23: canonical 0.6U list-row icon (registry-routed).
                                ListRowIcon(
                                    icon = probeToolIconToken(entry.tool),
                                    uDp = grid.uDp,
                                    // D-06: dim unsupported tools to text3; supported = accent.
                                    tint = if (entry.isSupported) t.accent else t.text3,
                                )
                            },
                            trailingContent = when (entry.tool) {
                                // Trailing readout: new z_offset for APPLY_BABYSTEP row
                                // (mirrors FineTuneScreen's current-value readout pattern).
                                ProbeTool.APPLY_BABYSTEP -> ({
                                    Text(
                                        text = applyBabystepVm.newOffset
                                            ?.let { babystepFmt(it) } ?: "—",
                                        style = JiibType.dataInline.toTextStyle(t),
                                        color = t.text2,
                                    )
                                })
                                else -> null
                            },
                        ) {
                            // D-06: dim unsupported label text to text3.
                            ListRowLabel(
                                text = stringResource(probeToolTitleRes(entry.tool)),
                                color = if (entry.isSupported) t.text else t.text3,
                            )
                        }
                    }
                }

                // Back only — R5/R8: accent intent, first button.
                FootButtonBar(
                    uDp = grid.uDp,
                    actions = listOf(
                        FootAction(
                            label = stringResource(R.string.common_back),
                            onClick = onBack,
                            intent = Intent.Accent,
                            icon = JiibIcons.Back,
                            contentDescription = stringResource(R.string.cd_back),
                        ),
                    ),
                )
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ApplyBabystepBody — Focus content for the Apply Babystepping tool
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Focus body for the Apply Babystepping tool (Task R2). Renders the three-row offset readout
 * and the Apply + Save amber action buttons, then owns the TWO-PHASE guard state machine:
 *
 *  Phase 1 — Apply: [Apply] button → amber [ConfirmGuard] → on confirm dispatch
 *    `vm.applyCommand` (`Z_OFFSET_APPLY_PROBE` when probe-present, else `Z_OFFSET_APPLY_ENDSTOP`)
 *    then immediately open Phase 2.
 *  Phase 2 — Save: amber SAVE_CONFIG [ConfirmGuard] → on confirm dispatch `saveConfig`.
 *    [Save] always re-opens the Phase 2 guard so the user can persist a prior apply.
 *
 * The ConfirmGuard overlays fill the Focus content area via the [Box] root (the ProbeScreen
 * Focus / Field split keeps the Back foot button accessible; the guards carry their own Cancel).
 *
 * [vm], [dispatcher], and [uDp] match the [ApplyBabystepScreen] parameter contract so the
 * rendering logic is directly portable (no logic re-invented here).
 */
@Composable
internal fun ApplyBabystepBody(
    vm: ApplyBabystepVm,
    dispatcher: CommandDispatcher?,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    // Phase 1: apply-confirm guard (warns the user before dispatching Z_OFFSET_APPLY_PROBE/ENDSTOP).
    var applyGuard by remember { mutableStateOf(false) }
    // Phase 2: SAVE_CONFIG guard (reuses the ProbeCalibrate saveGuard pattern, same strings).
    var saveGuard by remember { mutableStateOf(false) }

    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Row1a: Saved z_offset
            BabystepOffsetRow(
                label = stringResource(R.string.probe_apply_babystep_saved_label),
                value = vm.savedOffset?.let { babystepFmt(it) } ?: "—",
            )
            // Row1b: Live babystep
            BabystepOffsetRow(
                label = stringResource(R.string.probe_apply_babystep_live_label),
                value = vm.liveBabystep?.let { babystepFmt(it) } ?: "—",
            )

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(vertical = fsSp(10f, t.fs).dp),
                color = t.outline,
            )

            // Row2: New z_offset — the focal hero value (largest, accent colour).
            Text(
                text = stringResource(R.string.probe_apply_babystep_new_label),
                color = t.text2,
                style = JiibType.body.toTextStyle(t),
            )
            Text(
                text = vm.newOffset?.let { babystepFmt(it) } ?: "—",
                color = t.accent2,
                style = JiibType.focusHero.toTextStyle(t),
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "mm",
                color = t.text3,
                style = JiibType.caption.toTextStyle(t),
            )

            Spacer(Modifier.height(fsSp(16f, t.fs).dp))

            // Row3: Apply + Save action buttons (both warn/amber, R5).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Apply: disabled until canApply (live babystep non-null, non-zero, saved non-null).
                OutlinedControl(
                    label = stringResource(R.string.probe_apply_babystep_action),
                    icon = JiibIcons.CheckCircle,
                    onClick = { applyGuard = true },
                    intent = Intent.Warn,
                    enabled = vm.canApply,
                    modifier = Modifier.weight(1f),
                )
                // Save: always visible — re-opens the SAVE_CONFIG guard for a prior apply.
                OutlinedControl(
                    label = stringResource(R.string.calibration_save_config),
                    icon = JiibIcons.Save,
                    onClick = { saveGuard = true },
                    intent = Intent.Warn,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Phase 1: apply-confirm guard (warn) — dispatches Z_OFFSET_APPLY_PROBE/ENDSTOP.
        if (applyGuard) {
            ConfirmGuard(
                title = stringResource(R.string.probe_apply_babystep_confirm_title),
                message = stringResource(R.string.probe_apply_babystep_confirm_message),
                confirmLabel = stringResource(R.string.probe_apply_babystep_action),
                warn = true,
                onConfirm = {
                    // Probe-first branch: Z_OFFSET_APPLY_PROBE when probe-present.
                    if (vm.applyCommand == "Z_OFFSET_APPLY_PROBE") {
                        dispatcher?.dispatch(CommandRegistry.zOffsetApplyProbe, Unit)
                    } else {
                        dispatcher?.dispatch(CommandRegistry.zOffsetApplyEndstop, Unit)
                    }
                    applyGuard = false
                    saveGuard = true   // Immediately open the SAVE_CONFIG phase.
                },
                onCancel = { applyGuard = false },
            )
        }

        // Phase 2: SAVE_CONFIG guard (warn) — reuses ProbeCalibrate saveGuard strings verbatim.
        if (saveGuard) {
            ConfirmGuard(
                title = stringResource(R.string.calibration_save_config),
                message = stringResource(R.string.calibration_save_config_confirm),
                confirmLabel = stringResource(R.string.calibration_save_config),
                warn = true,
                onConfirm = {
                    dispatcher?.dispatch(CommandRegistry.saveConfig, Unit)
                    saveGuard = false
                },
                onCancel = { saveGuard = false },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helpers
// ─────────────────────────────────────────────────────────────────────────────

/** One label + value row for the Saved / Live babystep lines in [ApplyBabystepBody]. */
@Composable
private fun BabystepOffsetRow(label: String, value: String, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth(0.82f)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = t.text2,
            style = JiibType.body.toTextStyle(t),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            color = t.text,
            style = JiibType.statValue.toTextStyle(t),
        )
    }
}

/** Three-decimal mm formatting — matches the precision used in [ApplyBabystepScreen]. */
private fun babystepFmt(v: Double): String = String.format(Locale.US, "%.3f", v)
