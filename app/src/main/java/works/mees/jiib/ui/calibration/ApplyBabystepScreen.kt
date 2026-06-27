package works.mees.jiib.ui.calibration

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import works.mees.jiib.R
import works.mees.jiib.calibration.ApplyBabystepHolder
import works.mees.jiib.calibration.ApplyBabystepVm
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.GatingState
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.ConfirmGuard
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.HardLockStatusCard
import works.mees.jiib.designsystem.components.UnknownStatusCard
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.theme.fsSp
import works.mees.jiib.designsystem.icons.JiibIcons

/**
 * Thin VM-reading wrapper for the Apply Babystepping screen. Collects [holder.vm], the dispatcher,
 * printerState and gating state, owns the two-phase guard state (`applyGuard` and `saveGuard`),
 * then delegates to the stateless [ApplyBabystepContent] overload (@Preview matrices target Content).
 *
 * **Flow:** Apply button → applyGuard (warn) → dispatch vm.applyCommand → saveGuard (warn) →
 * dispatch SAVE_CONFIG. Two explicit amber confirms prevent accidental Klipper restarts.
 *
 * @param container the service-locator (provides the session dispatcher).
 * @param holder    the headless [ApplyBabystepHolder] (savedOffset / liveBabystep / newOffset).
 * @param onBack    leave the page (standard Back; always available — no session lock here).
 */
@Composable
fun ApplyBabystepScreen(
    container: AppContainer,
    holder: ApplyBabystepHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    val gating by container.gatingState.collectAsStateWithLifecycle(initialValue = GatingState.Idle)
    val vm by holder.vm.collectAsStateWithLifecycle()

    // Phase 1: apply-confirm guard (warns the user before dispatching Z_OFFSET_APPLY_PROBE/ENDSTOP).
    var applyGuard by remember { mutableStateOf(false) }
    // Phase 2: SAVE_CONFIG guard (reuses the ProbeCalibrate saveGuard pattern, same strings).
    var saveGuard by remember { mutableStateOf(false) }

    ApplyBabystepContent(
        vm = vm,
        applyGuard = applyGuard,
        saveGuard = saveGuard,
        isPrinting = isPrinting,
        gating = gating,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onAcknowledgeUnknown = { dispatcher?.acknowledgeUnresolved() },
        onSaveGuardShow = { applyGuard = true },
        onApplyConfirm = {
            // Dispatch the appropriate apply command (probe vs. endstop) then transition to phase 2.
            if (vm.applyCommand == "Z_OFFSET_APPLY_ENDSTOP") {
                dispatcher?.dispatch(CommandRegistry.zOffsetApplyEndstop, Unit)
            } else {
                dispatcher?.dispatch(CommandRegistry.zOffsetApplyProbe, Unit)
            }
            applyGuard = false
            saveGuard = true
        },
        onApplyCancel = { applyGuard = false },
        onSaveConfirm = {
            dispatcher?.dispatch(CommandRegistry.saveConfig, Unit)
            saveGuard = false
        },
        onSaveCancel = { saveGuard = false },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless Apply Babystepping layout — the @Preview matrix targets this composable.
 *
 * Focus = three-line readout block (saved z_offset / live babystep / → new z_offset) in Geist Mono
 * tabular numerals; "—" when a value is null. The "New z_offset" row is the prominent focal value.
 *
 * Field = FootButtonBar: Back (accent, first per R5/R8) + Apply (warn, `enabled = vm.canApply`).
 *
 * Two-phase amber guards:
 *  1. Apply ConfirmGuard (warn): user confirms the apply-command dispatch.
 *  2. SAVE_CONFIG ConfirmGuard (warn): reuses [calibration_save_config*] strings verbatim, mirrors
 *     ProbeCalibrate's Accepted→Save flow. Both rendered as Box siblings over ScreenScaffold.
 *
 * HardLock / Unknown morphs: this screen owns `home_*` only (no probe session — no probe_calibrate
 * or z_endstop_calibrate lock). safetyActive = gating !is Idle (e-stop during any gated state).
 */
@Composable
fun ApplyBabystepContent(
    vm: ApplyBabystepVm,
    applyGuard: Boolean = false,
    saveGuard: Boolean = false,
    isPrinting: Boolean = false,
    gating: GatingState = GatingState.Idle,
    onEmergencyStop: () -> Unit = {},
    onAcknowledgeUnknown: () -> Unit = {},
    onSaveGuardShow: () -> Unit = {},
    onApplyConfirm: () -> Unit = {},
    onApplyCancel: () -> Unit = {},
    onSaveConfirm: () -> Unit = {},
    onSaveCancel: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        // ApplyBabystep owns home_* only; probe_calibrate / z_endstop_calibrate are not running here.
        val isHoming = (gating as? GatingState.Locked)?.key?.startsWith("home") == true
        val unknownOwned = (gating as? GatingState.Unknown)?.key?.startsWith("home") == true

        Box(Modifier.fillMaxSize()) {
            ScreenScaffold(
                focus = {
                    FocusFrame(
                        title = stringResource(R.string.probe_tool_babystep_title),
                        icon = JiibIcons.Babystep,
                        uDp = grid.uDp,
                        modifier = Modifier.fillMaxSize(),
                        isPrinting = isPrinting,
                        safetyActive = gating !is GatingState.Idle,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                    ) {
                        // Unknown Focus morph (precedence: Unknown > Locked > content)
                        if (unknownOwned) {
                            UnknownStatusCard(
                                grid.uDp,
                                onDismiss = onAcknowledgeUnknown,
                                Modifier.fillMaxSize(),
                            )
                            return@FocusFrame
                        }
                        // HardLock Focus morph: replace the entire Focus body while homing.
                        if (isHoming) {
                            HardLockStatusCard(
                                stringResource(R.string.gating_homing),
                                grid.uDp,
                                Modifier.fillMaxSize(),
                            )
                            return@FocusFrame
                        }
                        BabystepReadout(vm = vm, modifier = Modifier.fillMaxSize().padding(8.dp))
                    }
                },
                field = {
                    FootButtonBar(
                        uDp = grid.uDp,
                        actions = listOf(
                            FootAction(
                                label = stringResource(R.string.common_back),
                                icon = JiibIcons.Back,
                                onClick = onBack,
                                intent = Intent.Accent,
                                contentDescription = stringResource(R.string.common_back),
                            ),
                            FootAction(
                                label = stringResource(R.string.probe_apply_babystep_action),
                                icon = JiibIcons.CheckCircle,
                                onClick = onSaveGuardShow,
                                intent = Intent.Warn,
                                enabled = vm.canApply,
                            ),
                        ),
                    )
                },
            )

            // Phase 1: apply-confirm guard (warn) — dispatches Z_OFFSET_APPLY_PROBE/ENDSTOP.
            if (applyGuard) {
                ConfirmGuard(
                    title = stringResource(R.string.probe_apply_babystep_confirm_title),
                    message = stringResource(R.string.probe_apply_babystep_confirm_message),
                    confirmLabel = stringResource(R.string.probe_apply_babystep_action),
                    warn = true,
                    onConfirm = onApplyConfirm,
                    onCancel = onApplyCancel,
                )
            }

            // Phase 2: SAVE_CONFIG guard (warn) — reuses ProbeCalibrate saveGuard strings verbatim.
            if (saveGuard) {
                ConfirmGuard(
                    title = stringResource(R.string.calibration_save_config),
                    message = stringResource(R.string.calibration_save_config_confirm),
                    confirmLabel = stringResource(R.string.calibration_save_config),
                    warn = true,
                    onConfirm = onSaveConfirm,
                    onCancel = onSaveCancel,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BabystepReadout — the Focus body: three-row offset display
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Three-line readout in the Focus:
 *  - Saved z_offset (statValue Geist Mono) — the persisted probe z_offset from config.
 *  - Live babystep  (statValue Geist Mono) — the live `gcode_move.homing_origin[2]`.
 *  - (HorizontalDivider)
 *  - New z_offset   (focusHero Geist Mono, accent2) — the resulting new offset after apply.
 *
 * All values show "—" when null. The new-offset row is the focal value (largest, accent colour).
 */
@Composable
private fun BabystepReadout(vm: ApplyBabystepVm, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Saved z_offset row
        OffsetRow(
            label = stringResource(R.string.probe_apply_babystep_saved_label),
            value = vm.savedOffset?.let { babystepFmt(it) } ?: "—",
        )
        // Live babystep row
        OffsetRow(
            label = stringResource(R.string.probe_apply_babystep_live_label),
            value = vm.liveBabystep?.let { babystepFmt(it) } ?: "—",
        )

        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .padding(vertical = fsSp(10f, t.fs).dp),
            color = t.outline,
        )

        // New z_offset — the focal value (largest)
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
    }
}

/** One label + value row for the saved / live babystep rows. */
@Composable
private fun OffsetRow(label: String, value: String, modifier: Modifier = Modifier) {
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

// ─────────────────────────────────────────────────────────────────────────────
// Formatting helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Three-decimal mm — matches the precision used in ProbeCalibrateScreen. */
private fun babystepFmt(v: Double): String = String.format(Locale.US, "%.3f", v)
