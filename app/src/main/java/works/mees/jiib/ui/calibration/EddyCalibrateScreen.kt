package works.mees.jiib.ui.calibration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import works.mees.jiib.R
import works.mees.jiib.calibration.ProbeCalibrateHolder
import works.mees.jiib.calibration.ProbeCalibrateVm
import works.mees.jiib.calibration.ProbePageState
import works.mees.jiib.calibration.eddyProbeDescriptor
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.EddyChipArgs
import works.mees.jiib.command.GatingState
import works.mees.jiib.command.TestZArgs
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.ConfirmGuard
import works.mees.jiib.designsystem.components.ConsoleTail
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.HardLockStatusCard
import works.mees.jiib.designsystem.components.UnknownStatusCard
import works.mees.jiib.designsystem.control.Intent
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
import works.mees.jiib.ui.increments.IncrementControls
import java.util.Locale

// Maximum console lines retained during the resonance sweep — keeps memory bounded on 2 GB devices.
private const val EDDY_CONSOLE_CAP = 200

/**
 * Thin VM-reading wrapper for EddyCalibrateScreen. Owns the three-phase lifecycle:
 *
 * 1. **Idle** — Start foot action dispatches [CommandRegistry.eddyCalibrate]; "Starting…"
 *    feedback while the gcode is in-flight but `manual_probe.is_active` has not yet flipped.
 * 2. **Active (paper-test)** — [ManualProbeJog] via the shared [ProbeCalibrateHolder] VM;
 *    TESTZ/ACCEPT/ABORT wired in Field; Back suppressed (D-09; nav-layer BackHandler in AppShell).
 * 3. **Sweep** — after ACCEPT closes `manual_probe.is_active` (holder.vm.state == Accepted),
 *    firmware runs a multi-minute resonance sweep; [ConsoleTail] tails [gcodeResponses]; amber
 *    SAVE_CONFIG guard offered once the user is ready.
 *
 * **BUILD-BLIND:** `PROBE_EDDY_CURRENT_CALIBRATE` requires eddy-current hardware; this screen
 * cannot be end-to-end validated without physical hardware. A caution note is shown in the
 * Idle Focus body.
 *
 * **Pitfall 3 (Holder reset):** [LaunchedEffect](Unit) resets the holder on entry so stale
 * Accepted state from a prior session does not survive re-entry.
 *
 * **D-09 / Back suppression:** The nav-layer `BackHandler` in `composable<NavDest.ProbeEddyCalibrate>`
 * (AppShell.kt) owns system-Back suppression while Active || starting. This screen's Active-state
 * FootButtonBar omits Back so the only exits are Accept/Abort.
 *
 * @param container       the service-locator (dispatcher, printerState, gating, capabilities).
 * @param holder          the [ProbeCalibrateHolder] (derives Idle/Active/Accepted from
 *                        `manual_probe.is_active`; must be a separate instance keyed on
 *                        the eddy route so resets are isolated).
 * @param gcodeResponses  live raw gcode-response stream from the session's PrinterStateStore
 *                        (for tailing during the resonance sweep).
 * @param onBack          leave the page (neutral Back; only offered when NOT Active per D-09).
 */
@Composable
fun EddyCalibrateScreen(
    container: AppContainer,
    holder: ProbeCalibrateHolder,
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
    val vm by holder.vm.collectAsStateWithLifecycle()

    // Pitfall 3: fresh-instance reset on entry — clears sawActive/captured from any prior session.
    LaunchedEffect(Unit) { holder.reset() }

    val incrementLists by container.activeIncrementLists.collectAsStateWithLifecycle(emptyMap())
    val testzSteps = remember(incrementLists) {
        incrementLists["probe_testz"] ?: IncrementControls.defaultValueMap().getValue("probe_testz")
    }
    var step by remember { mutableStateOf(0.05) }
    LaunchedEffect(testzSteps) {
        if (step !in testzSteps) {
            step = testzSteps.minByOrNull { kotlin.math.abs(it - step) } ?: testzSteps.first()
        }
    }

    // Console tail for the resonance sweep phase (capped at EDDY_CONSOLE_CAP lines).
    val lines = remember { mutableStateListOf<String>() }
    LaunchedEffect(gcodeResponses) {
        gcodeResponses.collect { line ->
            lines.add(line)
            if (lines.size > EDDY_CONSOLE_CAP) lines.removeRange(0, lines.size - EDDY_CONSOLE_CAP)
        }
    }

    // "Starting…" feedback: eddyCalibrate dispatched but manual_probe.is_active not yet flipped.
    // Mirrors ProbeCalibrateScreen's `starting` logic (Pitfall 6 — verbatim key tracking; also
    // consumed by the nav-layer BackHandler in AppShell for D-09 suppression).
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val starting = vm.state == ProbePageState.Idle && "eddy_calibrate" in inFlight

    var saveGuard by remember { mutableStateOf(false) }
    val chip = eddyProbeDescriptor(capabilities)?.chip ?: ""

    EddyCalibrateContent(
        vm = vm,
        step = step,
        steps = testzSteps,
        starting = starting,
        saveGuard = saveGuard,
        lines = lines,
        isPrinting = isPrinting,
        gating = gating,
        enabled = vm.state == ProbePageState.Active && dispatcher != null && "testz" !in inFlight,
        onAcknowledgeUnknown = { dispatcher?.acknowledgeUnresolved() },
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onTestZUp = { dispatcher?.dispatch(CommandRegistry.testZ, TestZArgs(step)) },
        onTestZDown = { dispatcher?.dispatch(CommandRegistry.testZ, TestZArgs(-step)) },
        onStepUp = {
            step = testzSteps[
                (testzSteps.indexOf(step).let { if (it < 0) 0 else it } + 1)
                    .coerceAtMost(testzSteps.lastIndex)
            ]
        },
        onStepDown = {
            step = testzSteps[
                (testzSteps.indexOf(step).let { if (it < 0) 0 else it } - 1)
                    .coerceAtLeast(0)
            ]
        },
        onStart = { dispatcher?.dispatch(CommandRegistry.eddyCalibrate, EddyChipArgs(chip)) },
        onAccept = { dispatcher?.dispatch(CommandRegistry.accept, Unit) },
        onAbort = {
            holder.markAborted()
            dispatcher?.dispatch(CommandRegistry.abort, Unit)
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

/**
 * Stateless EddyCalibrateScreen layout — the @Preview matrix targets this composable.
 *
 * **Three-phase layout (Focus / Field):**
 *
 * | Phase | vm.state | Focus | Field |
 * |-------|----------|-------|-------|
 * | Idle | Idle | description + build-blind note | (spacer) |
 * | Active | Active | live-Z readout | [ManualProbeJog] |
 * | Sweep | Accepted | sweep-running caption | [ConsoleTail] |
 *
 * FootButtonBar is state-adaptive:
 * - **Idle (starting):** disabled "Starting…" (Back suppressed by nav-layer BackHandler).
 * - **Idle (ready):** Back (accent) + Start (go).
 * - **Active:** Accept (go) + Abort (danger) — Back suppressed per D-09.
 * - **Accepted:** Back (accent) + Save & Restart (warn, amber guard).
 *
 * The `home_*` gating HardLock morph is NOT wired here — eddy calibrate does not dispatch
 * homing commands so no Home All foot action is present and no `home_*` HardLock can fire
 * from this screen. The standard `safetyActive = gating !is Idle` keeps the e-stop live.
 */
@Composable
fun EddyCalibrateContent(
    vm: ProbeCalibrateVm,
    step: Double,
    steps: List<Double> = IncrementControls.defaultValueMap().getValue("probe_testz"),
    starting: Boolean,
    saveGuard: Boolean,
    lines: List<String>,
    isPrinting: Boolean = false,
    gating: GatingState = GatingState.Idle,
    enabled: Boolean,
    onAcknowledgeUnknown: () -> Unit = {},
    onEmergencyStop: () -> Unit = {},
    onTestZUp: () -> Unit,
    onTestZDown: () -> Unit,
    onStepUp: () -> Unit,
    onStepDown: () -> Unit,
    onStart: () -> Unit,
    onAccept: () -> Unit,
    onAbort: () -> Unit,
    onSaveGuardShow: () -> Unit,
    onSaveConfirm: () -> Unit,
    onSaveCancel: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        // EddyCalibrate does NOT dispatch homing — no home_* HardLock can fire from this screen.
        // Scope Unknown only to home_* for symmetry with ProbeCalibrateScreen.
        val isHoming = (gating as? GatingState.Locked)?.key?.startsWith("home") == true
        val unknownOwned = (gating as? GatingState.Unknown)?.key?.startsWith("home") == true

        Box(Modifier.fillMaxSize()) {
            ScreenScaffold(
                focus = {
                    FocusFrame(
                        title = stringResource(R.string.probe_tool_eddy_calibrate_title),
                        icon = JiibIcons.EddyCalibrate,
                        uDp = grid.uDp,
                        modifier = Modifier.fillMaxSize(),
                        isPrinting = isPrinting,
                        safetyActive = gating !is GatingState.Idle,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                    ) {
                        if (unknownOwned) {
                            UnknownStatusCard(
                                grid.uDp,
                                onDismiss = onAcknowledgeUnknown,
                                Modifier.fillMaxSize(),
                            )
                            return@FocusFrame
                        }
                        if (isHoming) {
                            HardLockStatusCard(
                                stringResource(R.string.gating_homing),
                                grid.uDp,
                                Modifier.fillMaxSize(),
                            )
                            return@FocusFrame
                        }
                        when (vm.state) {
                            ProbePageState.Idle -> EddyIdleFocus(
                                starting = starting,
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                            )
                            ProbePageState.Active -> EddyActiveFocus(
                                vm = vm,
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                            )
                            ProbePageState.Accepted -> EddySweepFocus(
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                            )
                        }
                    }
                },
                field = {
                    when (vm.state) {
                        ProbePageState.Idle -> Spacer(Modifier.fillMaxWidth().weight(1f))
                        ProbePageState.Active -> ManualProbeJog(
                            vm = vm,
                            step = step,
                            steps = steps,
                            enabled = enabled,
                            onTestZUp = onTestZUp,
                            onTestZDown = onTestZDown,
                            onStepUp = onStepUp,
                            onStepDown = onStepDown,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                        ProbePageState.Accepted -> ConsoleTail(
                            lines = lines,
                            uDp = grid.uDp,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                    // D-09: state-adaptive FootButtonBar.
                    FootButtonBar(
                        uDp = grid.uDp,
                        actions = buildList {
                            when (vm.state) {
                                ProbePageState.Idle -> if (starting) {
                                    // Start dispatched, session not yet live — disabled "Starting…" feedback.
                                    // Back is also suppressed here (nav-layer BackHandler handles it).
                                    add(FootAction(
                                        label = stringResource(R.string.probe_starting),
                                        icon = JiibIcons.CalibrationWait,
                                        onClick = {},
                                        intent = Intent.Neutral,
                                        enabled = false,
                                    ))
                                } else {
                                    // Back FIRST (accent — R5/R8); Start = go (the screen's expected action).
                                    add(FootAction(
                                        label = stringResource(R.string.common_back),
                                        icon = JiibIcons.Back,
                                        onClick = onBack,
                                        intent = Intent.Accent,
                                        contentDescription = stringResource(R.string.common_back),
                                    ))
                                    add(FootAction(
                                        label = stringResource(R.string.calibration_start),
                                        icon = JiibIcons.CalibrationRun,
                                        onClick = onStart,
                                        intent = Intent.Go,
                                    ))
                                }
                                ProbePageState.Active -> {
                                    // Back SUPPRESSED — nav-layer BackHandler in AppShell swallows
                                    // system Back while Active || starting (D-09).
                                    add(FootAction(
                                        label = stringResource(R.string.calibration_accept),
                                        icon = JiibIcons.CheckCircle,
                                        onClick = onAccept,
                                        intent = Intent.Go,
                                    ))
                                    add(FootAction(
                                        label = stringResource(R.string.calibration_abort),
                                        icon = JiibIcons.CalibrationAbort,
                                        onClick = onAbort,
                                        intent = Intent.Danger,
                                    ))
                                }
                                ProbePageState.Accepted -> {
                                    // Back FIRST (accent); SAVE_CONFIG stays warn (restarts Klipper —
                                    // hazard-in-process, R5). Sweep may still be running — Save offered
                                    // but not forced; user decides when the sweep is complete.
                                    add(FootAction(
                                        label = stringResource(R.string.common_back),
                                        icon = JiibIcons.Back,
                                        onClick = onBack,
                                        intent = Intent.Accent,
                                        contentDescription = stringResource(R.string.common_back),
                                    ))
                                    add(FootAction(
                                        label = stringResource(R.string.calibration_save_config),
                                        icon = JiibIcons.Save,
                                        onClick = onSaveGuardShow,
                                        intent = Intent.Warn,
                                    ))
                                }
                            }
                        },
                    )
                },
            )

            // Amber SAVE_CONFIG restart gate — persists the calibrated distance model.
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
// Phase-specific Focus composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Idle Focus: description of what the calibration does + build-blind caution banner.
 *
 * The [starting] flag switches to a brief "Starting…" caption while the gcode is in-flight
 * but `manual_probe.is_active` hasn't flipped yet (klicky homes/attaches/probes first).
 */
@Composable
private fun EddyIdleFocus(starting: Boolean, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.probe_tool_eddy_calibrate_desc),
            color = t.text2,
            style = JiibType.body.toTextStyle(t),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        // Build-blind caution note (t.heat = amber, per brief — NOT a glyph).
        Text(
            text = stringResource(R.string.calibration_run_build_blind_note),
            color = if (starting) t.text3 else t.heat,
            style = JiibType.caption.toTextStyle(t),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/**
 * Active Focus: live-Z readout from [vm.zPosition] (or saved offset while descending).
 *
 * Shown while `manual_probe.is_active` is true and the user is performing the paper-test.
 * The [ManualProbeJog] in the Field drives the actual Z nudges; this Focus surface provides
 * immediate numeric feedback for each TESTZ step.
 */
@Composable
private fun EddyActiveFocus(vm: ProbeCalibrateVm, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val zText = vm.zPosition?.let { String.format(Locale.US, "%.3f", it) }
        ?: vm.savedZOffset?.let { String.format(Locale.US, "%.3f", -it) }
        ?: "—"
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = zText,
            color = t.accent2,
            style = JiibType.focusHero.toTextStyle(t),
        )
        Text(
            text = "mm",
            color = t.text3,
            style = JiibType.caption.toTextStyle(t),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Sweep Focus: shown after ACCEPT closes the paper-test session while the firmware runs the
 * multi-minute resonance sweep. The [ConsoleTail] in the Field tails the live gcode responses.
 */
@Composable
private fun EddySweepFocus(modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.eddy_calibrate_sweep_running),
            color = t.text2,
            style = JiibType.body.toTextStyle(t),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
