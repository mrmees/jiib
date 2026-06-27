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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import works.mees.jiib.R
import works.mees.jiib.calibration.eddyProbeDescriptor
import works.mees.jiib.command.EddyChipArgs
import works.mees.jiib.command.EddyTapArgs
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.GatingState
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.ConfirmGuard
import works.mees.jiib.designsystem.components.ConsoleTail
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.HardLockStatusCard
import works.mees.jiib.designsystem.components.UnknownStatusCard
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.control.OutlinedControl
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.Capabilities
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

// Internal tap-stage API keys (Klipper STAGE= parameter values for eddyTapCalibrate).
private val TAP_STAGE_KEYS = listOf("guess", "refine", "verify")

// Maximum console lines retained per session — keeps memory bounded on 2 GB devices.
private const val CONSOLE_CAP = 200

// ─────────────────────────────────────────────────────────────────────────────
// Route-specific stateful wrappers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Stateful wrapper for the Eddy Drive Current calibration route
 * ([NavDest.ProbeEddyDriveCurrent]).
 *
 * Tails [gcodeResponses] from the live session store (capped at [CONSOLE_CAP] lines) and
 * delegates to the stateless [CalibrationRunContent] preview seam.
 *
 * **BUILD-BLIND:** `LDC_CALIBRATE_DRIVE_CURRENT` requires eddy-current hardware; this screen
 * cannot be end-to-end validated without physical hardware. A caution note is shown in the
 * Focus body to remind the user.
 *
 * @param container      the process-scoped service-locator (dispatcher, printerState, gating,
 *                       capabilities for chip extraction).
 * @param gcodeResponses live raw gcode-response stream from the session's [PrinterStateStore].
 * @param onBack         leave the page (standard Back — no session lock here).
 */
@Composable
fun EddyDriveCurrentScreen(
    container: AppContainer,
    gcodeResponses: SharedFlow<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    val gating by container.gatingState.collectAsStateWithLifecycle(initialValue = GatingState.Idle)
    val capabilities by container.capabilities.collectAsStateWithLifecycle(initialValue = Capabilities())

    // Cap at CONSOLE_CAP lines; tail the live stream from first entry.
    val lines = remember { mutableStateListOf<String>() }
    LaunchedEffect(gcodeResponses) {
        gcodeResponses.collect { line ->
            lines.add(line)
            if (lines.size > CONSOLE_CAP) lines.removeRange(0, lines.size - CONSOLE_CAP)
        }
    }

    // Derive "running" from the dispatcher's in-flight key for ldcDriveCurrent.
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val running = "ldc_drive_current" in inFlight

    var saveGuard by remember { mutableStateOf(false) }
    val chip = eddyProbeDescriptor(capabilities)?.chip ?: ""

    CalibrationRunContent(
        title = stringResource(R.string.probe_tool_eddy_drive_current_title),
        description = stringResource(R.string.probe_tool_eddy_drive_current_desc),
        buildBlindNote = stringResource(R.string.calibration_run_build_blind_note),
        lines = lines,
        running = running,
        tapStages = null,
        selectedStage = null,
        isPrinting = isPrinting,
        gating = gating,
        saveGuard = saveGuard,
        headerIcon = JiibIcons.EddyDriveCurrent,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onAcknowledgeUnknown = { dispatcher?.acknowledgeUnresolved() },
        onSelectStage = {},
        onRun = { dispatcher?.dispatch(CommandRegistry.ldcDriveCurrent, EddyChipArgs(chip)) },
        onSaveGuardShow = { saveGuard = true },
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
 * Stateful wrapper for the Eddy Tap Threshold calibration route
 * ([NavDest.ProbeEddyTap]).
 *
 * Tails [gcodeResponses] from the live session store (capped at [CONSOLE_CAP] lines) and
 * delegates to the stateless [CalibrationRunContent] preview seam. Exposes three stage
 * selectors (guess / refine / verify) that feed `STAGE=` on the `eddyTapCalibrate` command.
 *
 * **BUILD-BLIND:** `PROBE_EDDY_CURRENT_TAP_CALIBRATE` requires eddy-current hardware; this
 * screen cannot be end-to-end validated without physical hardware.
 *
 * @param container      the process-scoped service-locator (dispatcher, printerState, gating).
 * @param gcodeResponses live raw gcode-response stream from the session's [PrinterStateStore].
 * @param onBack         leave the page (standard Back — no session lock here).
 */
@Composable
fun EddyTapScreen(
    container: AppContainer,
    gcodeResponses: SharedFlow<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    val gating by container.gatingState.collectAsStateWithLifecycle(initialValue = GatingState.Idle)

    // Cap at CONSOLE_CAP lines; tail the live stream from first entry.
    val lines = remember { mutableStateListOf<String>() }
    LaunchedEffect(gcodeResponses) {
        gcodeResponses.collect { line ->
            lines.add(line)
            if (lines.size > CONSOLE_CAP) lines.removeRange(0, lines.size - CONSOLE_CAP)
        }
    }

    // Derive "running" from the dispatcher's in-flight key for eddyTapCalibrate.
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val running = "eddy_tap_calibrate" in inFlight

    var saveGuard by remember { mutableStateOf(false) }

    // Stage selector: display labels + index tracks which API key to use.
    val stageLabels = listOf(
        stringResource(R.string.calibration_run_tap_stage_guess),
        stringResource(R.string.calibration_run_tap_stage_refine),
        stringResource(R.string.calibration_run_tap_stage_verify),
    )
    var selectedStageIdx by remember { mutableStateOf(0) } // default = guess

    CalibrationRunContent(
        title = stringResource(R.string.probe_tool_eddy_tap_title),
        description = stringResource(R.string.probe_tool_eddy_tap_desc),
        buildBlindNote = stringResource(R.string.calibration_run_build_blind_note),
        lines = lines,
        running = running,
        tapStages = stageLabels,
        selectedStage = stageLabels[selectedStageIdx],
        isPrinting = isPrinting,
        gating = gating,
        saveGuard = saveGuard,
        headerIcon = JiibIcons.EddyTap,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onAcknowledgeUnknown = { dispatcher?.acknowledgeUnresolved() },
        onSelectStage = { label ->
            val idx = stageLabels.indexOf(label)
            if (idx >= 0) selectedStageIdx = idx
        },
        onRun = {
            dispatcher?.dispatch(
                CommandRegistry.eddyTapCalibrate,
                EddyTapArgs(TAP_STAGE_KEYS[selectedStageIdx]),
            )
        },
        onSaveGuardShow = { saveGuard = true },
        onSaveConfirm = {
            dispatcher?.dispatch(CommandRegistry.saveConfig, Unit)
            saveGuard = false
        },
        onSaveCancel = { saveGuard = false },
        onBack = onBack,
        modifier = modifier,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Stateless layout — the @Preview matrix targets this composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Stateless "run-and-watch" calibration screen — the [@Preview] matrix targets this composable.
 *
 * Reused by both Eddy Drive Current ([EddyDriveCurrentScreen]) and Eddy Tap Threshold
 * ([EddyTapScreen]).
 *
 * **Layout:**
 * - **Focus** — [FocusFrame] header + [description] body text + [buildBlindNote] amber-caption
 *   line + [ConsoleTail] filling the remainder.
 * - **Field** — optional [tapStages] stage-selector row (three filled chips, selected one
 *   accent-highlighted) + [FootButtonBar]: Back (accent, first) · Run (go) · Save Config (warn).
 *
 * **Gating** mirrors [ProbeCalibrateContent]: `home_*` triggers the HardLock Focus morph and
 * Unknown card; any gating flips `safetyActive` so the e-stop glyph replaces the header icon.
 *
 * **BUILD-BLIND note:** shown as a [JiibType.caption] line in [t.heat] (amber) — a text-only
 * caution treatment; NO new glyph is added (icon-law compliance: never choose a glyph, ask first).
 *
 * @param title          screen title rendered in the [FocusFrame] header.
 * @param description    one-line description text shown at the top of the Focus body.
 * @param buildBlindNote caution caption rendered below [description] in [t.heat] (amber) to
 *                       signal that the screen requires hardware validation.
 * @param lines          ordered list of console lines (newest last); capped/managed by the caller.
 * @param running        true when the calibration command is in-flight; disables the Run button.
 * @param tapStages      if non-null, a list of display labels for the tap stage selector. Null =
 *                       no selector (Drive Current path).
 * @param selectedStage  the currently selected stage label; must be one of [tapStages].
 * @param isPrinting     when true and [onEmergencyStop] is non-null, the header icon morphs to
 *                       e-stop (passed through [FocusFrame]).
 * @param gating         current gating state; drives [safetyActive] and the HardLock/Unknown morphs.
 * @param saveGuard      when true, the amber [ConfirmGuard] overlay is shown over the screen.
 * @param headerIcon     the [JiibIcon] shown in the [FocusFrame] header (idle glyph).
 * @param onEmergencyStop e-stop tap handler.
 * @param onAcknowledgeUnknown dismiss the Unknown gating card.
 * @param onSelectStage  called when the user taps a stage chip; receives the display label.
 * @param onRun          called when the user taps the Run foot button.
 * @param onSaveGuardShow show the SAVE_CONFIG confirm overlay.
 * @param onSaveConfirm  confirm the SAVE_CONFIG (dispatches the command + dismisses the guard).
 * @param onSaveCancel   cancel the SAVE_CONFIG overlay.
 * @param onBack         leave the page.
 */
@Composable
fun CalibrationRunContent(
    title: String,
    description: String,
    buildBlindNote: String,
    lines: List<String>,
    running: Boolean,
    tapStages: List<String>? = null,
    selectedStage: String? = null,
    isPrinting: Boolean = false,
    gating: GatingState = GatingState.Idle,
    saveGuard: Boolean,
    headerIcon: JiibIcon,
    onEmergencyStop: () -> Unit = {},
    onAcknowledgeUnknown: () -> Unit = {},
    onSelectStage: (String) -> Unit = {},
    onRun: () -> Unit,
    onSaveGuardShow: () -> Unit,
    onSaveConfirm: () -> Unit,
    onSaveCancel: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        val t = LocalTokens.current

        // HardLock morph: this screen owns home_* only (homing triggered from the foot when needed
        // on other screens; eddy commands themselves are standard gcode, not HardLock ops).
        val isHoming = (gating as? GatingState.Locked)?.key?.startsWith("home") == true
        // Unknown: scope the "still running" card to home_* so an unrelated screen's unresolved
        // op doesn't surface here.
        val unknownOwned = (gating as? GatingState.Unknown)?.key?.startsWith("home") == true

        Box(Modifier.fillMaxSize()) {
            ScreenScaffold(
                focus = {
                    FocusFrame(
                        title = title,
                        icon = headerIcon,
                        uDp = grid.uDp,
                        modifier = Modifier.fillMaxSize(),
                        isPrinting = isPrinting,
                        safetyActive = gating !is GatingState.Idle,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                    ) {
                        // Unknown Focus morph (precedence: Unknown > Locked > normal content).
                        if (unknownOwned) {
                            UnknownStatusCard(
                                uDp = grid.uDp,
                                onDismiss = onAcknowledgeUnknown,
                                modifier = Modifier.fillMaxSize(),
                            )
                            return@FocusFrame
                        }
                        // HardLock Focus morph: while homing, replace Focus body with a centered
                        // status card. E-stop stays live in the FocusFrame header.
                        if (isHoming) {
                            HardLockStatusCard(
                                label = stringResource(R.string.gating_homing),
                                uDp = grid.uDp,
                                modifier = Modifier.fillMaxSize(),
                            )
                            return@FocusFrame
                        }
                        // Normal Focus body: description + build-blind caution + console tail.
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = description,
                                style = JiibType.body.toTextStyle(t),
                                color = t.text2,
                            )
                            Spacer(Modifier.height(4.dp))
                            // Build-blind note: amber caption (t.heat) — text-only caution,
                            // no new glyph (icon-law: never pick a glyph without owner approval).
                            Text(
                                text = buildBlindNote,
                                style = JiibType.caption.toTextStyle(t),
                                color = t.heat,
                            )
                            ConsoleTail(
                                lines = lines,
                                uDp = grid.uDp,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                            )
                        }
                    }
                },
                field = {
                    // Stage selector (Tap Threshold only): a row of filled chips, one per stage.
                    // Selected chip = accent border + accentSoft fill; others = neutral border.
                    if (tapStages != null && selectedStage != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            tapStages.forEach { stage ->
                                val isSelected = stage == selectedStage
                                OutlinedControl(
                                    label = stage,
                                    onClick = { onSelectStage(stage) },
                                    modifier = Modifier.weight(1f),
                                    intent = if (isSelected) Intent.Accent else Intent.Neutral,
                                    fill = if (isSelected) t.accentSoft else null,
                                )
                            }
                        }
                    }
                    // FootButtonBar: Back (accent, first) · Run (go) · Save Config (warn).
                    // Intent R5: Back = accent (neutral nav), Run = go (expected action),
                    // Save Config = warn (restart Klipper — hazard-in-process).
                    FootButtonBar(
                        uDp = grid.uDp,
                        actions = buildList {
                            add(FootAction(
                                label = stringResource(R.string.common_back),
                                icon = JiibIcons.Back,
                                onClick = onBack,
                                intent = Intent.Accent,
                                contentDescription = stringResource(R.string.common_back),
                            ))
                            add(FootAction(
                                label = stringResource(R.string.calibration_run),
                                icon = JiibIcons.CalibrationRun,
                                onClick = onRun,
                                intent = Intent.Go,
                                enabled = !running,
                            ))
                            add(FootAction(
                                label = stringResource(R.string.calibration_save_config),
                                icon = JiibIcons.Save,
                                onClick = onSaveGuardShow,
                                intent = Intent.Warn,
                            ))
                        },
                    )
                },
            )

            // Amber SAVE_CONFIG restart gate — reuses ProbeCalibrate's strings + ConfirmGuard
            // pattern (calibration_save_config / calibration_save_config_confirm).
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
