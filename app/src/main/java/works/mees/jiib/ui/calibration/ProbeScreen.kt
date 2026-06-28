package works.mees.jiib.ui.calibration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import works.mees.jiib.R
import works.mees.jiib.calibration.ApplyBabystepHolder
import works.mees.jiib.calibration.ApplyBabystepVm
import works.mees.jiib.calibration.ProbeCalibrateHolder
import works.mees.jiib.calibration.ProbeCalibrateVm
import works.mees.jiib.calibration.ProbeAccuracyResult
import works.mees.jiib.calibration.ProbeHubHolder
import works.mees.jiib.calibration.ProbePageState
import works.mees.jiib.calibration.ProbeTool
import works.mees.jiib.calibration.ProbeToolEntry
import works.mees.jiib.calibration.ProbeTestHolder
import works.mees.jiib.calibration.ProbeTestVm
import works.mees.jiib.calibration.eddyProbeDescriptor
import works.mees.jiib.command.EddyChipArgs
import works.mees.jiib.command.EddyTapArgs
import works.mees.jiib.command.ProbeAccuracyArgs
import works.mees.jiib.command.CommandDispatcher
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.GatingState
import works.mees.jiib.command.PrinterCommands
import works.mees.jiib.command.TestZArgs
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.ConfirmGuard
import works.mees.jiib.designsystem.components.ConsoleTail
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.ListRowIcon
import works.mees.jiib.designsystem.components.ListRowLabel
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.control.OutlinedControl
import works.mees.jiib.designsystem.icons.IconRef
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.icons.JiibIconView
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.FocusInset
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.Capabilities
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.TextRole
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.theme.fsSp
import works.mees.jiib.designsystem.components.ConfirmOnBack
import works.mees.jiib.designsystem.components.HardLockStatusCard
import works.mees.jiib.designsystem.components.UnknownStatusCard
import works.mees.jiib.ui.increments.IncrementControls

// Internal tap-stage API keys (Klipper STAGE= parameter values for eddyTapCalibrate).
// Kept here (ProbeScreen) so ProbeContent can build the correct EddyTapArgs.
private val EDDY_TAP_STAGE_KEYS = listOf("guess", "refine", "verify")

// Maximum gcode-response lines retained per eddy run — keeps memory bounded on 2 GB devices.
private const val EDDY_CONSOLE_CAP = 200

// Samples steps for the ProbeTest samples stepper in [ProbeTestBody].
private val SAMPLES_STEPS: List<Int> = listOf(1, 2, 3, 5, 10, 20, 30, 50)
private val SAMPLES_DEFAULT_IDX: Int = SAMPLES_STEPS.indexOf(10).coerceAtLeast(0) // index 4

/**
 * Shared model for a pending confirm dialog hosted at the [ProbeContent] level.
 *
 * [ProbeContent] owns a single `pending: ProbeConfirm?` state. Body composables (ApplyBabystepBody
 * and future R4/R5/R6 bodies) request a guard by calling [ProbeContent]'s `onRequestConfirm`
 * callback with a [ProbeConfirm] instance. The host renders it as a full-screen overlay sibling
 * to [ScreenScaffold], matching the BedMesh / ScrewsTilt pattern.
 *
 * Two-phase chaining (Apply → SAVE_CONFIG) is implemented by nesting a second [onConfirm] call to
 * `onRequestConfirm` inside the first [onConfirm] lambda.
 */
data class ProbeConfirm(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val warn: Boolean = true,
    val onConfirm: () -> Unit,
)

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
    probeTestHolder: ProbeTestHolder,
    probeCalibrateHolder: ProbeCalibrateHolder,
    eddyCalibrateHolder: ProbeCalibrateHolder,
    gcodeResponses: SharedFlow<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tools by probeHubHolder.tools.collectAsStateWithLifecycle()
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val gating by container.gatingState.collectAsStateWithLifecycle(initialValue = GatingState.Idle)
    // FIX-2: home_* HardLock — ProbeScreen owns Home All on Z-Offset and Eddy Calibrate bodies.
    val isHoming = (gating as? GatingState.Locked)?.key?.startsWith("home") == true
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    val applyBabystepVm by applyBabystepHolder.vm.collectAsStateWithLifecycle()
    val probeTestVm by probeTestHolder.vm.collectAsStateWithLifecycle()

    // R5: Tail gcodeResponses into a capped 200-line list for the eddy run bodies.
    // Mirrors EddyDriveCurrentScreen / EddyTapScreen in CalibrationRunScreen.kt exactly.
    val eddyLines = remember { mutableStateListOf<String>() }
    LaunchedEffect(gcodeResponses) {
        gcodeResponses.collect { line ->
            eddyLines.add(line)
            if (eddyLines.size > EDDY_CONSOLE_CAP) {
                eddyLines.removeRange(0, eddyLines.size - EDDY_CONSOLE_CAP)
            }
        }
    }

    // R5: Derive the eddy chip name from live capabilities (same pattern as EddyDriveCurrentScreen).
    val capabilities by container.capabilities.collectAsStateWithLifecycle(initialValue = Capabilities())
    val eddyChip = remember(capabilities) { eddyProbeDescriptor(capabilities)?.chip ?: "" }

    // R5: Eddy Tap stage selector state — index into EDDY_TAP_STAGE_KEYS (default = 0 = "guess").
    var eddySelectedStageIdx by remember { mutableStateOf(0) }

    // Z-Offset Calibrate session VM + step controls (R4).
    val probeCalibrateVm by probeCalibrateHolder.vm.collectAsStateWithLifecycle()
    val incrementLists by container.activeIncrementLists.collectAsStateWithLifecycle(emptyMap())
    val testzSteps = remember(incrementLists) {
        incrementLists["probe_testz"] ?: IncrementControls.defaultValueMap().getValue("probe_testz")
    }
    var step by remember { mutableStateOf(0.05) }
    // Rebase: when the active list changes, snap `step` to the nearest present value
    // (value-tracked control — verbatim from ProbeCalibrateScreen).
    LaunchedEffect(testzSteps) {
        if (step !in testzSteps) {
            step = testzSteps.minByOrNull { kotlin.math.abs(it - step) } ?: testzSteps.first()
        }
    }
    // "Starting…" immediate feedback: Start gcode in flight but session not yet Active (the klicky
    // macro homes/attaches/probes for several seconds before is_active flips — Pitfall 6).
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val starting = probeCalibrateVm.state == ProbePageState.Idle &&
        ("probe_calibrate" in inFlight || "z_endstop_calibrate" in inFlight)

    // R6: Eddy Calibrate session VM + "starting" feedback (separate holder — isolates reset/sawActive).
    val eddyCalibrateVm by eddyCalibrateHolder.vm.collectAsStateWithLifecycle()
    val eddyStarting = eddyCalibrateVm.state == ProbePageState.Idle && "eddy_calibrate" in inFlight

    // FIX-3: track which tool owns the active session so ProbeContent can force the correct Focus
    // body when a session fires while a NON-session tool is selected (lock-without-escape guard).
    var activeSessionTool by remember { mutableStateOf<ProbeTool?>(null) }
    // Clear owner when no session is active (session ended, aborted, or never started).
    val sessionActiveForTracking =
        (probeCalibrateVm.state == ProbePageState.Active || starting) ||
        (eddyCalibrateVm.state == ProbePageState.Active || eddyStarting)
    LaunchedEffect(sessionActiveForTracking) {
        if (!sessionActiveForTracking) activeSessionTool = null
    }
    // Adopt an orphan active session (live manual_probe with no tracked owner — external start, or app
    // restart / re-entry mid-session) as Z-Offset, so the Field controls + Abort foot appear instead of
    // leaving the user stuck with only Accept. Only fires when activeSessionTool is null, so a tracked
    // Z or eddy session (owner already set in onStart/onEddyStart) is never overridden.
    LaunchedEffect(probeCalibrateVm.state, activeSessionTool) {
        orphanSessionAdoption(probeCalibrateVm.state, activeSessionTool)?.let { activeSessionTool = it }
    }


    var selected by remember { mutableStateOf<ProbeTool?>(null) }
    var samplesIdx by remember { mutableStateOf(SAMPLES_DEFAULT_IDX) }
    // D-05: pre-select first tool + reconcile against the current visible list.
    // Writing state during composition is valid here — the condition settles after at most one
    // extra recomposition (once selected is set, the `if` body no longer fires).
    if (selected == null || tools.none { it.tool == selected }) {
        selected = tools.firstOrNull()?.tool
    }

    // Auto-query when PROBE_TEST is first shown.
    // R5/R6: Clear the shared eddy console lines whenever the user switches to ANY eddy tool so
    // Drive Current ↔ Tap ↔ Calibrate switches don't show each other's leftover output.
    // R6: Also reset the eddy calibrate holder on EDDY_CALIBRATE entry (Pitfall 3, separate holder).
    // NOTE: Z_OFFSET reset relocated to LaunchedEffect(Unit) below — resetting on every re-selection
    // wiped a pending Accepted when switching tools and back (Task 5 / Codex #4).
    LaunchedEffect(selected) {
        when (selected) {
            ProbeTool.PROBE_TEST -> dispatcher?.dispatch(CommandRegistry.queryProbe, Unit)
            ProbeTool.EDDY_CALIBRATE -> {
                eddyCalibrateHolder.reset()
                eddyLines.clear()
            }
            ProbeTool.EDDY_DRIVE_CURRENT,
            ProbeTool.EDDY_TAP -> eddyLines.clear()
            else -> {}
        }
    }

    // Screen-entry reset (mirrors TiltScreen.kt:93) — clears a STALE Accepted from a prior visit ONCE
    // on entry; in-screen tool switches now preserve a freshly captured Accepted.
    LaunchedEffect(Unit) { probeCalibrateHolder.reset() }

    // R6: Clear the sweep console when the paper-test session transitions Active→Accepted
    // so pre-sweep output (homing/probing noise) does not appear in the sweep console.
    LaunchedEffect(eddyCalibrateVm.state) {
        if (eddyCalibrateVm.state == ProbePageState.Accepted) {
            eddyLines.clear()
        }
    }

    // FIX-2: ConfirmOnBack intercepts system-Back while homing (matching old ProbeCalibrateScreen).
    // requestBack is the (possibly) confirm-gated version of onBack passed into ProbeContent.
    ConfirmOnBack(enabled = isHoming, onBack = onBack) { requestBack ->
    ProbeContent(
        tools = tools,
        selected = selected,
        applyBabystepVm = applyBabystepVm,
        probeTestVm = probeTestVm,
        probeCalibrateVm = probeCalibrateVm,
        eddyCalibrateVm = eddyCalibrateVm,
        step = step,
        steps = testzSteps,
        starting = starting,
        eddyStarting = eddyStarting,
        inFlight = inFlight,
        samplesIdx = samplesIdx,
        dispatcher = dispatcher,
        isPrinting = isPrinting,
        gating = gating,
        isHoming = isHoming,
        onAcknowledgeUnknown = { dispatcher?.acknowledgeUnresolved() },
        activeSessionTool = activeSessionTool,
        eddyLines = eddyLines,
        eddyChip = eddyChip,
        eddySelectedStageIdx = eddySelectedStageIdx,
        onEddySelectStageIdx = { eddySelectedStageIdx = it },
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onSelect = { selected = it },
        onSamplesUp = { samplesIdx = (samplesIdx + 1).coerceAtMost(SAMPLES_STEPS.lastIndex) },
        onSamplesDown = { samplesIdx = (samplesIdx - 1).coerceAtLeast(0) },
        onStart = {
            activeSessionTool = ProbeTool.Z_OFFSET  // FIX-3: mark owner before dispatch
            val d = dispatcher ?: return@ProbeContent
            if (probeCalibrateVm.startCommand == PrinterCommands.Z_ENDSTOP_CALIBRATE) {
                d.dispatch(CommandRegistry.zEndstopCalibrate, Unit)
            } else {
                d.dispatch(CommandRegistry.probeCalibrate, Unit)
            }
        },
        onAccept = { dispatcher?.dispatch(CommandRegistry.accept, Unit) },
        onAbort = {
            probeCalibrateHolder.markAborted()
            dispatcher?.dispatch(CommandRegistry.abort, Unit)
        },
        onEddyStart = {
            activeSessionTool = ProbeTool.EDDY_CALIBRATE  // FIX-3: mark owner before dispatch
            dispatcher?.dispatch(CommandRegistry.eddyCalibrate, EddyChipArgs(eddyChip))
        },
        onEddyAccept = { dispatcher?.dispatch(CommandRegistry.accept, Unit) },
        onEddyAbort = {
            eddyCalibrateHolder.markAborted()
            dispatcher?.dispatch(CommandRegistry.abort, Unit)
        },
        onHomeAll = { dispatcher?.dispatch(CommandRegistry.homeAll, Unit) },
        onTestZUp = { dispatcher?.dispatch(CommandRegistry.testZ, TestZArgs(step)) },
        onTestZDown = { dispatcher?.dispatch(CommandRegistry.testZ, TestZArgs(-step)) },
        onStepUp = {
            val idx = testzSteps.indexOf(step).let { if (it < 0) 0 else it }
            step = testzSteps[(idx + 1).coerceAtMost(testzSteps.lastIndex)]
        },
        onStepDown = {
            val idx = testzSteps.indexOf(step).let { if (it < 0) 0 else it }
            step = testzSteps[(idx - 1).coerceAtLeast(0)]
        },
        onBack = requestBack,
        modifier = modifier,
    )
    } // ConfirmOnBack
}

/**
 * Stateless Probe rendering surface — the @Preview matrix targets this composable.
 *
 * [probeToolIconToken] and [probeToolTitleRes] are `internal` helpers defined at the bottom
 * of this file (moved here from the now-deleted ProbeHubScreen.kt).
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
    probeTestVm: ProbeTestVm = ProbeTestVm(),
    probeCalibrateVm: ProbeCalibrateVm = ProbeCalibrateVm(),
    // R6: Eddy Calibrate session VM (SEPARATE holder — isolates from Z-Offset session).
    eddyCalibrateVm: ProbeCalibrateVm = ProbeCalibrateVm(),
    step: Double = 0.05,
    steps: List<Double> = IncrementControls.defaultValueMap().getValue("probe_testz"),
    starting: Boolean = false,
    // R6: "starting" flag for the eddy calibrate session (eddy_calibrate in inFlight while Idle).
    eddyStarting: Boolean = false,
    inFlight: Set<String> = emptySet(),
    samplesIdx: Int = SAMPLES_DEFAULT_IDX,
    liveTriggered: Boolean? = null,
    probeIsZEndstop: Boolean = false,
    dispatcher: CommandDispatcher? = null,
    isPrinting: Boolean = false,
    gating: GatingState = GatingState.Idle,
    // FIX-2: home_* HardLock morph + ConfirmOnBack (ported from old ProbeCalibrateScreen).
    isHoming: Boolean = false,
    onAcknowledgeUnknown: () -> Unit = {},
    // FIX-3: owning session tool — forces Focus to the session body when a session is active
    // on a non-session tool selection (lock-without-escape guard).
    activeSessionTool: ProbeTool? = null,
    // R5: eddy run state — capped console lines, chip name, Tap stage index.
    eddyLines: List<String> = emptyList(),
    eddyChip: String = "",
    eddySelectedStageIdx: Int = 0,
    onEddySelectStageIdx: (Int) -> Unit = {},
    onEmergencyStop: () -> Unit = {},
    onSelect: (ProbeTool) -> Unit,
    onSamplesUp: () -> Unit = {},
    onSamplesDown: () -> Unit = {},
    onStart: () -> Unit = {},
    onAccept: () -> Unit = {},
    onAbort: () -> Unit = {},
    // R6: Eddy Calibrate session callbacks.
    onEddyStart: () -> Unit = {},
    onEddyAccept: () -> Unit = {},
    onEddyAbort: () -> Unit = {},
    onHomeAll: () -> Unit = {},
    onTestZUp: () -> Unit = {},
    onTestZDown: () -> Unit = {},
    onStepUp: () -> Unit = {},
    onStepDown: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    // Z-Offset session lock: disable Field row selection + suppress foot-bar Back while active.
    val zActiveOrStarting = probeCalibrateVm.state == ProbePageState.Active || starting

    // R6: Eddy Calibrate session lock (same field-lock + back-suppression pattern as Z-Offset).
    val eddyActiveOrStarting = eddyCalibrateVm.state == ProbePageState.Active || eddyStarting

    // Combined session lock — either active session blocks tool switching and suppresses Back.
    val sessionActive = zActiveOrStarting || eddyActiveOrStarting

    // Z-Offset-specific Field/foot decisions (keyed on the owner-tracked tool — Eddy never trips it).
    val zFieldMode = probeFieldMode(activeSessionTool, probeCalibrateVm.state)
    val zFootMode = probeFootMode(activeSessionTool, probeCalibrateVm.state, sessionActive)

    // inFlight is threaded in as a param (already collected in ProbeScreen) — single subscriber.
    // Used to gate ManualProbeJog: no-op TESTZ while a jog is already in flight.
    val jogEnabled = probeCalibrateVm.state == ProbePageState.Active &&
        dispatcher != null && "testz" !in inFlight

    // R6: Same gate for the Eddy Calibrate jog — TESTZ disabled if already in flight.
    val eddyJogEnabled = eddyCalibrateVm.state == ProbePageState.Active &&
        dispatcher != null && "testz" !in inFlight

    // Pre-read SAVE_CONFIG strings for the onSaveConfig lambda built below
    // (string resources must be read in composable context, not inside a lambda).
    val saveTitle   = stringResource(R.string.calibration_save_config)
    val saveMessage = stringResource(R.string.calibration_save_config_confirm)

    // During an ACTIVE Probe-Calibrate routine the header morphs into a caution reminder to stow the
    // probe before adjusting (replaces the old in-body stow note). Same condition as the Field morph.
    val zControlsActive = zFieldMode == ProbeFieldMode.Z_CONTROL_ROWS
    // Null-selected guard: when selected == null use the hub identity fallback in the Focus header.
    val focusTitle = when {
        zControlsActive -> stringResource(R.string.probe_calibrate_remove_probe_title)
        else -> selected?.let { stringResource(probeToolTitleRes(it)) }
            ?: stringResource(R.string.probe_hub_title)
    }
    val focusTitleColor = if (zControlsActive) t.heat else null
    val focusIcon = selected?.let { probeToolIconToken(it) } ?: JiibIcons.RoutineProbeCalibrate

    // Shared full-screen guard host — bodies call onRequestConfirm to raise a ConfirmGuard that
    // overlays the entire scaffold (Focus + Field), not just the Focus content area.
    // Matches the BedMesh / ScrewsTilt pattern exactly.
    var pending by remember { mutableStateOf<ProbeConfirm?>(null) }

    // FIX-2: home_* Unknown — scope the "still running" card to home_* only (mirrors BedMesh).
    val unknownOwned = (gating as? GatingState.Unknown)?.key?.startsWith("home") == true

    // FIX-3: while any session is active, force the Focus body to the owning tool's screen.
    // Prevents a "locked in the wrong body" state when a session fires on a non-session tool.
    // Falls back to Z_OFFSET if the owner wasn't tracked (externally-started session edge case).
    val effectiveSelected = if (sessionActive) (activeSessionTool ?: ProbeTool.Z_OFFSET) else selected

    // onSaveConfig: raises the full-screen ProbeConfirm guard for SAVE_CONFIG (same pattern as
    // ApplyBabystepBody's save path — built here so string resources are read in composable context).
    val onSaveConfig: () -> Unit = {
        pending = ProbeConfirm(
            title = saveTitle,
            message = saveMessage,
            confirmLabel = saveTitle,
            warn = true,
            onConfirm = { dispatcher?.dispatch(CommandRegistry.saveConfig, Unit) },
        )
    }

    // R5: Eddy run body resources — loaded here so they're available in composable context when
    // building EddyRunBody lambdas. String resources MUST be read in composable context, not lambdas.
    val eddyBuildBlindNote = stringResource(R.string.calibration_run_build_blind_note)
    val eddyDriveCurrentDesc = stringResource(R.string.probe_tool_eddy_drive_current_desc)
    val eddyTapDesc = stringResource(R.string.probe_tool_eddy_tap_desc)
    // Tap stage display labels (order matches EDDY_TAP_STAGE_KEYS: guess/refine/verify).
    val tapStageLabels = listOf(
        stringResource(R.string.calibration_run_tap_stage_guess),
        stringResource(R.string.calibration_run_tap_stage_refine),
        stringResource(R.string.calibration_run_tap_stage_verify),
    )

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        Box(Modifier.fillMaxSize()) {
        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = focusTitle,
                    icon = focusIcon,
                    titleColor = focusTitleColor,
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
                    // FIX-2: home_* HardLock Focus morph (ported from old ProbeCalibrateScreen +
                    // BedMeshScreen). Precedence: Unknown > Locked > normal body.
                    // E-stop stays live in the FocusFrame header above this content area.
                    if (unknownOwned) {
                        UnknownStatusCard(
                            uDp = grid.uDp,
                            onDismiss = onAcknowledgeUnknown,
                            modifier = Modifier.fillMaxSize(),
                        )
                        return@FocusFrame
                    }
                    if (isHoming) {
                        HardLockStatusCard(
                            label = stringResource(R.string.gating_homing),
                            uDp = grid.uDp,
                            modifier = Modifier.fillMaxSize(),
                        )
                        return@FocusFrame
                    }
                    // FIX-3: render from effectiveSelected (session-forced tool) rather than the
                    // raw user selection, so a live session always shows its Accept/Abort body.
                    // D-09 BackHandler: the nav-layer BackHandler in AppShell.kt owns system-Back
                    // suppression while sessionActive.
                    when (effectiveSelected) {
                        ProbeTool.Z_OFFSET -> ZOffsetBody(
                            vm = probeCalibrateVm,
                            step = step,
                            starting = starting,
                            onStart = onStart,
                            onAccept = onAccept,
                            onSaveConfig = onSaveConfig,
                            onHomeAll = onHomeAll,
                            modifier = Modifier.fillMaxSize(),
                        )
                        ProbeTool.APPLY_BABYSTEP -> ApplyBabystepBody(
                            vm = applyBabystepVm,
                            dispatcher = dispatcher,
                            uDp = grid.uDp,
                            onRequestConfirm = { pending = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                        ProbeTool.PROBE_TEST -> ProbeTestBody(
                            vm = probeTestVm,
                            samplesIdx = samplesIdx,
                            dispatcher = dispatcher,
                            uDp = grid.uDp,
                            liveTriggered = liveTriggered,
                            probeIsZEndstop = probeIsZEndstop,
                            onSamplesUp = onSamplesUp,
                            onSamplesDown = onSamplesDown,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // R5: Eddy Drive Current — build-blind run + console-tail body (no stages).
                        ProbeTool.EDDY_DRIVE_CURRENT -> EddyRunBody(
                            description = eddyDriveCurrentDesc,
                            buildBlindNote = eddyBuildBlindNote,
                            lines = eddyLines,
                            tapStages = null,
                            selectedStage = null,
                            onSelectStage = {},
                            onRun = {
                                dispatcher?.dispatch(
                                    CommandRegistry.ldcDriveCurrent,
                                    EddyChipArgs(eddyChip),
                                )
                            },
                            onSave = onSaveConfig,
                            uDp = grid.uDp,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // R5: Eddy Tap Threshold — build-blind run + console-tail body + stage selector.
                        ProbeTool.EDDY_TAP -> EddyRunBody(
                            description = eddyTapDesc,
                            buildBlindNote = eddyBuildBlindNote,
                            lines = eddyLines,
                            tapStages = tapStageLabels,
                            selectedStage = tapStageLabels.getOrElse(eddySelectedStageIdx) {
                                tapStageLabels.first()
                            },
                            onSelectStage = { label ->
                                onEddySelectStageIdx(
                                    tapStageLabels.indexOf(label).coerceAtLeast(0)
                                )
                            },
                            onRun = {
                                dispatcher?.dispatch(
                                    CommandRegistry.eddyTapCalibrate,
                                    EddyTapArgs(
                                        EDDY_TAP_STAGE_KEYS.getOrElse(eddySelectedStageIdx) {
                                            "guess"
                                        }
                                    ),
                                )
                            },
                            onSave = onSaveConfig,
                            uDp = grid.uDp,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // R6: Eddy Current Calibrate — hybrid paper-test + sweep console body.
                        ProbeTool.EDDY_CALIBRATE -> EddyCalibrateBody(
                            vm = eddyCalibrateVm,
                            step = step,
                            steps = steps,
                            starting = eddyStarting,
                            enabled = eddyJogEnabled,
                            lines = eddyLines,
                            uDp = grid.uDp,
                            onTestZUp = onTestZUp,
                            onTestZDown = onTestZDown,
                            onStepUp = onStepUp,
                            onStepDown = onStepDown,
                            onStart = onEddyStart,
                            onAccept = onEddyAccept,
                            onAbort = onEddyAbort,
                            onSaveConfig = onSaveConfig,
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> {
                            // Null-selected guard (all enum values handled above).
                            // Kept for future-proofing (new ProbeTool values before wiring).
                            if (effectiveSelected != null) {
                                Text(
                                    text = stringResource(probeToolTitleRes(effectiveSelected)),
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
                when (zFieldMode) {
                    ProbeFieldMode.Z_CONTROL_ROWS -> ProbeControlRows(
                        step = step,
                        steps = steps,
                        jogEnabled = jogEnabled,
                        uDp = grid.uDp,
                        onTestZUp = onTestZUp,
                        onTestZDown = onTestZDown,
                        onStepUp = onStepUp,
                        onStepDown = onStepDown,
                        modifier = Modifier.weight(1f),
                    )
                    ProbeFieldMode.TOOL_LIST -> ListBlock(modifier = Modifier.weight(1f)) {
                        items(tools, key = { it.tool.name }) { entry ->
                            // Session lock (D-09): while ANY probe session is active-or-starting
                            // (Z-Offset OR Eddy Calibrate), dim all rows and make them non-tappable.
                            val rowLocked = sessionActive
                            ListRow(
                                // FIX-3: while locked, highlight the effective (session-owning) tool
                                // so the Field tracks the forced Focus body.
                                selected = if (rowLocked) (entry.tool == effectiveSelected) else (entry.tool == selected),
                                onClick = if (!rowLocked) { { onSelect(entry.tool) } } else { {} },
                                uDp = grid.uDp,
                                modifier = if (rowLocked) {
                                    Modifier.alpha(0.38f).semantics { disabled() }
                                } else {
                                    Modifier
                                },
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
                                    // Trailing readout: PERSISTED saved z_offset (negated) across ALL
                                    // states. Intentionally stable — the Focus hero shows the in-session
                                    // delta (currentZ - saved); they are different quantities by design,
                                    // not a disagreement.
                                    ProbeTool.Z_OFFSET -> ({
                                        val zTrailing = probeCalibrateVm.savedZOffset?.let { fmtZOffset(-it) }
                                        Text(
                                            text = zTrailing ?: "—",
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
                }

                FootButtonBar(
                    uDp = grid.uDp,
                    actions = when (zFootMode) {
                        ProbeFootMode.ABORT -> listOf(
                            FootAction(
                                label = stringResource(R.string.calibration_abort),
                                onClick = onAbort,
                                intent = Intent.Danger,
                                icon = JiibIcons.CalibrationAbort,
                                contentDescription = stringResource(R.string.calibration_abort),
                            ),
                        )
                        ProbeFootMode.NONE -> emptyList()
                        ProbeFootMode.BACK -> listOf(
                            FootAction(
                                label = stringResource(R.string.common_back),
                                onClick = onBack,
                                intent = Intent.Accent,
                                icon = JiibIcons.Back,
                                contentDescription = stringResource(R.string.cd_back),
                            ),
                        )
                    },
                )
            },
        )

        // Full-screen guard overlay — sibling to ScreenScaffold, covers both Focus and Field so
        // the user cannot escape via the Back foot button or a Field row tap.
        // Matches the BedMesh / ScrewsTilt hosting pattern exactly.
        pending?.let { c ->
            ConfirmGuard(
                title = c.title,
                message = c.message,
                confirmLabel = c.confirmLabel,
                warn = c.warn,
                // FIX-1: null pending BEFORE invoking the callback so a callback that chains a
                // second guard (Apply → SAVE_CONFIG) sets the new pending and it survives.
                // Old order { c.onConfirm(); pending = null } overwrote any new pending = null.
                onConfirm = { val cb = c.onConfirm; pending = null; cb() },
                onCancel = { pending = null },
            )
        }
        } // Box(Modifier.fillMaxSize())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ZOffsetBody — Focus content for the Z-Offset Calibrate tool (Task R4)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ZOffsetBody(
    vm: ProbeCalibrateVm,
    step: Double,
    starting: Boolean,
    onStart: () -> Unit,
    onAccept: () -> Unit,
    onSaveConfig: () -> Unit,
    onHomeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when (vm.state) {
            ProbePageState.Idle -> {
                Text(
                    text = stringResource(R.string.zoffset_idle_blurb),
                    style = JiibType.body.toTextStyle(t),
                    color = t.text2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = fsSp(8f, t.fs).dp),
                )
                Spacer(Modifier.weight(1f))
                ZHero(text = vm.savedZOffset?.let { fmtZOffset(-it) } ?: "—")
                Spacer(Modifier.weight(1f))
                // Negated-convention explainer, shown before the routine starts (above the button).
                InvertedValuesNote()
                IdleActionButton(vm = vm, starting = starting, onStart = onStart, onHomeAll = onHomeAll)
            }
            ProbePageState.Active -> {
                val r = zOffsetActiveReadouts(vm, step)
                Spacer(Modifier.weight(1f))
                HeroReadoutRow(label = stringResource(R.string.calibration_current_offset), value = r.currentOffset)
                ReadoutRow(label = stringResource(R.string.calibration_saved_offset), value = r.saved)
                ReadoutRow(label = stringResource(R.string.calibration_difference), value = r.difference)
                ReadoutRow(label = stringResource(R.string.calibration_increment), value = r.stepSize)
                Spacer(Modifier.weight(1f))
                // Footnote (above the buttons): explains the negated readout convention.
                InvertedValuesNote()
                OutlinedControl(
                    label = stringResource(R.string.calibration_accept),
                    icon = JiibIcons.CheckCircle,
                    onClick = onAccept,
                    intent = Intent.Go,
                    modifier = Modifier.fillMaxWidth().padding(top = fsSp(8f, t.fs).dp),
                )
            }
            ProbePageState.Accepted -> {
                val saved = vm.savedZOffset
                val captured = vm.capturedOffset
                val deltaText = if (saved != null && captured != null) fmtZOffset(captured - saved) else "—"
                Spacer(Modifier.weight(1f))
                ZHero(text = deltaText)
                Spacer(Modifier.weight(1f))
                OutlinedControl(
                    label = stringResource(R.string.calibration_reboot_to_save),
                    icon = JiibIcons.Save,
                    onClick = onSaveConfig,
                    intent = Intent.Warn,
                    modifier = Modifier.fillMaxWidth().padding(top = fsSp(8f, t.fs).dp),
                )
            }
        }
    }
}

/** The big Geist-Mono Z number (accent2 hero) + "mm" caption. */
@Composable
private fun ZHero(text: String) {
    val t = LocalTokens.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = text, style = JiibType.focusHero.toTextStyle(t), color = t.accent2)
        Text(text = "mm", style = JiibType.caption.toTextStyle(t), color = t.text3)
    }
}

/** A labeled inline readout line (name start, value end) for the Active readout block (UAT-2). */
@Composable
private fun ReadoutRow(label: String, value: String) {
    val t = LocalTokens.current
    Row(Modifier.fillMaxWidth().padding(horizontal = fsSp(12f, t.fs).dp, vertical = fsSp(2f, t.fs).dp)) {
        Text(text = label, style = JiibType.body.toTextStyle(t), color = t.text2)
        Spacer(Modifier.weight(1f))
        Text(text = "$value mm", style = JiibType.dataInline.toTextStyle(t), color = t.text)
    }
}

/** Prominent labeled readout — the Current Offset hero line (label start, big accent2 value end). */
@Composable
private fun HeroReadoutRow(label: String, value: String) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = fsSp(12f, t.fs).dp, vertical = fsSp(2f, t.fs).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = JiibType.body.toTextStyle(t), color = t.text2)
        Spacer(Modifier.weight(1f))
        Text(text = "$value mm", style = JiibType.statValue.toTextStyle(t), color = t.accent2)
    }
}

/**
 * The negated-convention explainer caption ("jiib displays inverted values…"), shown in the
 * Probe-Calibrate Focus both before the routine (Idle) and during it (Active), above the action button.
 * Body-size (one step up from caption), dimmed to text3 so it reads as a footnote.
 */
@Composable
private fun InvertedValuesNote(modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Text(
        text = stringResource(R.string.probe_calibrate_inverted_note),
        style = JiibType.body.toTextStyle(t),
        color = t.text3,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(bottom = fsSp(4f, t.fs).dp),
    )
}

/** Idle action button: Starting… (disabled) / Home All (not homed) / Start (homed). */
@Composable
private fun IdleActionButton(
    vm: ProbeCalibrateVm, starting: Boolean, onStart: () -> Unit, onHomeAll: () -> Unit,
) {
    val t = LocalTokens.current
    val mod = Modifier.fillMaxWidth().padding(top = fsSp(8f, t.fs).dp)
    when {
        starting -> OutlinedControl(
            label = stringResource(R.string.probe_starting), icon = JiibIcons.CalibrationWait,
            onClick = {}, intent = Intent.Neutral, enabled = false,
            modifier = mod.alpha(0.38f).semantics { disabled() },
        )
        !vm.homedGate -> OutlinedControl(
            label = stringResource(R.string.calibration_home_all), icon = JiibIcons.MoveHomeAll,
            onClick = onHomeAll, intent = Intent.Go, modifier = mod,
        )
        else -> OutlinedControl(
            label = stringResource(R.string.calibration_start), icon = JiibIcons.CalibrationRun,
            onClick = onStart, intent = Intent.Go, modifier = mod,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EddyCalibrateBody — Focus content for the Eddy Calibrate tool (Task R6)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Focus body for the Eddy Current Calibrate tool (Task R6). Hybrid paper-test + sweep console.
 *
 * Three phases driven by [vm.state]:
 *  - **Idle:** description + build-blind ⚠ caution ([t.heat]) + [Start] ([Intent.Go]).
 *    While [starting] (eddy_calibrate in inFlight, session not yet live), shows a disabled
 *    "Starting…" button ([Intent.Neutral]) — mirrors [ZOffsetBody]'s Idle-starting treatment.
 *    [Start] dispatches `PROBE_EDDY_CURRENT_CALIBRATE` via [onStart], which OPENS the
 *    `manual_probe` session; the paper-test begins immediately.
 *  - **Active (paper-test):** Z hero readout (delta from saved offset, same formula as
 *    [ZOffsetBody]) + [ManualProbeJog] (TESTZ/step, two-column D-08 motif, weight=1f) +
 *    [Accept] ([Intent.Go]) + [Abort] ([Intent.Danger]). Back is suppressed by the combined
 *    [sessionActive] flag in [ProbeContent] (D-09).
 *  - **Accepted (sweep):** Post-Accept, firmware runs the resonance sweep. [ConsoleTail]
 *    fills available space + [Save] ([Intent.Warn]) raises the shared full-screen SAVE_CONFIG
 *    guard via [onSaveConfig] → [ProbeContent]'s [onRequestConfirm] host.
 *
 * **BUILD-BLIND:** `PROBE_EDDY_CURRENT_CALIBRATE` requires eddy-current hardware; the Idle
 * body shows a text-only ⚠ caution ([buildBlindNote] in [t.heat]); no glyph is added
 * (icon-law: never pick a glyph without owner approval).
 *
 * **Console clear:** [ProbeScreen] clears [lines] on the Active→Accepted transition (so
 * pre-sweep paper-test noise does not appear in the sweep console) and on tool-switch entry
 * (so Drive Current ↔ Tap ↔ Calibrate switches don't show each other's output).
 *
 * No [ConfirmGuard] rendered here — the guard is hosted full-screen in [ProbeContent] via
 * [onSaveConfig] (matches the [ZOffsetBody] / [ApplyBabystepBody] hosting pattern).
 */
@Composable
internal fun EddyCalibrateBody(
    vm: ProbeCalibrateVm,
    step: Double,
    steps: List<Double>,
    starting: Boolean,
    enabled: Boolean,
    lines: List<String>,
    uDp: Dp,
    onTestZUp: () -> Unit,
    onTestZDown: () -> Unit,
    onStepUp: () -> Unit,
    onStepDown: () -> Unit,
    onStart: () -> Unit,
    onAccept: () -> Unit,
    onAbort: () -> Unit,
    onSaveConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    // Z hero delta formula — verbatim from ZOffsetBody (Pitfall: don't redesign, port).
    // Active: live Z delta from saved baseline. Idle/pre-Active: negated saved offset.
    val saved = vm.savedZOffset
    val currentZ = when (vm.state) {
        ProbePageState.Active   -> vm.zPosition
        ProbePageState.Accepted -> vm.capturedOffset
        else                    -> null
    }
    val zText: String = when (vm.state) {
        ProbePageState.Idle -> saved?.let { fmtZOffset(-it) } ?: "—"
        else -> if (saved != null && currentZ != null) fmtZOffset(currentZ - saved) else "—"
    }

    when (vm.state) {
        // ── Idle: description + build-blind caution + Start button ──────────────────
        ProbePageState.Idle -> Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.probe_tool_eddy_calibrate_desc),
                style = JiibType.body.toTextStyle(t),
                color = t.text2,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            // Build-blind caution: amber t.heat text; NO glyph (icon-law).
            // The ⚠ is a unicode character embedded in the string resource.
            Text(
                text = stringResource(R.string.calibration_run_build_blind_note),
                style = JiibType.caption.toTextStyle(t),
                color = t.heat,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            if (starting) {
                // eddy_calibrate in inFlight — disabled "Starting…" feedback.
                OutlinedControl(
                    label = stringResource(R.string.probe_starting),
                    icon = JiibIcons.CalibrationWait,
                    onClick = {},
                    intent = Intent.Neutral,
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = fsSp(8f, t.fs).dp)
                        .alpha(0.38f)
                        .semantics { disabled() },
                )
            } else {
                OutlinedControl(
                    label = stringResource(R.string.calibration_start),
                    icon = JiibIcons.CalibrationRun,
                    onClick = onStart,
                    intent = Intent.Go,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = fsSp(8f, t.fs).dp),
                )
            }
        }

        // ── Active: Z readout + ManualProbeJog + Accept + Abort ──────────────────────
        ProbePageState.Active -> Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Z hero readout (Geist Mono, accent2 colour — same as ZOffsetBody Row1).
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = fsSp(8f, t.fs).dp),
            ) {
                Text(
                    text = zText,
                    style = JiibType.focusHero.toTextStyle(t),
                    color = t.accent2,
                )
                Text(
                    text = "mm",
                    style = JiibType.caption.toTextStyle(t),
                    color = t.text3,
                )
            }
            // ManualProbeJog fills available space (D-08 two-column motif).
            ManualProbeJog(
                vm = vm,
                step = step,
                steps = steps,
                enabled = enabled,
                onTestZUp = onTestZUp,
                onTestZDown = onTestZDown,
                onStepUp = onStepUp,
                onStepDown = onStepDown,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            // Accept (go) + Abort (danger) — Back suppressed by sessionActive in ProbeContent.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = fsSp(8f, t.fs).dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedControl(
                    label = stringResource(R.string.calibration_accept),
                    icon = JiibIcons.CheckCircle,
                    onClick = onAccept,
                    intent = Intent.Go,
                    modifier = Modifier.weight(1f),
                )
                OutlinedControl(
                    label = stringResource(R.string.calibration_abort),
                    icon = JiibIcons.CalibrationAbort,
                    onClick = onAbort,
                    intent = Intent.Danger,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Accepted (sweep): sweep label + ConsoleTail + Save ───────────────────────
        ProbePageState.Accepted -> Column(
            modifier = modifier,
        ) {
            Text(
                text = stringResource(R.string.eddy_calibrate_sweep_running),
                style = JiibType.caption.toTextStyle(t),
                color = t.text2,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            ConsoleTail(
                lines = lines,
                uDp = uDp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            // SAVE_CONFIG restarts Klipper (hazard-in-process, R5 → warn / amber).
            // Guard raised via onSaveConfig → ProbeContent's shared ProbeConfirm host.
            OutlinedControl(
                label = stringResource(R.string.calibration_save_config),
                icon = JiibIcons.Save,
                onClick = onSaveConfig,
                intent = Intent.Warn,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = fsSp(8f, t.fs).dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ApplyBabystepBody — Focus content for the Apply Babystepping tool
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Focus body for the Apply Babystepping tool (Task R2). Renders the three-row offset readout
 * and the Apply + Save amber action buttons, then delegates the TWO-PHASE guard flow to the
 * shared [ProbeContent]-level guard host via [onRequestConfirm].
 *
 *  Phase 1 — Apply: [Apply] button → calls [onRequestConfirm] with the apply-confirm payload.
 *    On confirm: dispatches `vm.applyCommand` (`Z_OFFSET_APPLY_PROBE` when probe-present, else
 *    `Z_OFFSET_APPLY_ENDSTOP`), then immediately calls [onRequestConfirm] again with the
 *    SAVE_CONFIG payload (two-phase chaining through the shared host).
 *  Phase 2 — Save: [Save] button → calls [onRequestConfirm] with the SAVE_CONFIG payload directly.
 *    On confirm: dispatches `saveConfig`.
 *
 * [ProbeContent] renders the resulting [ConfirmGuard] as a FULL-SCREEN overlay over the entire
 * scaffold (Focus + Field) — the guard cannot be escaped via the Back foot button or a Field row
 * tap. This matches the BedMesh / ScrewsTilt hosting pattern.
 *
 * [vm], [dispatcher], and [uDp] match the [ApplyBabystepScreen] parameter contract so the
 * rendering logic is directly portable (no logic re-invented here).
 */
@Composable
internal fun ApplyBabystepBody(
    vm: ApplyBabystepVm,
    dispatcher: CommandDispatcher?,
    uDp: Dp,
    onRequestConfirm: (ProbeConfirm) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    // Pre-read string resources so they can be safely captured in onClick lambdas.
    val applyTitle = stringResource(R.string.probe_apply_babystep_confirm_title)
    val applyMessage = stringResource(R.string.probe_apply_babystep_confirm_message)
    val applyLabel = stringResource(R.string.probe_apply_babystep_action)
    val saveTitle = stringResource(R.string.calibration_save_config)
    val saveMessage = stringResource(R.string.calibration_save_config_confirm)

    // SAVE_CONFIG confirm payload — shared between the Apply chain and the standalone Save button.
    val saveConfirm = ProbeConfirm(
        title = saveTitle,
        message = saveMessage,
        confirmLabel = saveTitle,
        warn = true,
        onConfirm = { dispatcher?.dispatch(CommandRegistry.saveConfig, Unit) },
    )

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
                // Raises Phase 1 guard via the shared ProbeContent host; on confirm immediately
                // raises Phase 2 (SAVE_CONFIG) by calling onRequestConfirm a second time.
                OutlinedControl(
                    label = applyLabel,
                    icon = JiibIcons.CheckCircle,
                    onClick = {
                        onRequestConfirm(
                            ProbeConfirm(
                                title = applyTitle,
                                message = applyMessage,
                                confirmLabel = applyLabel,
                                warn = true,
                                onConfirm = {
                                    // Probe-first branch: Z_OFFSET_APPLY_PROBE when probe-present.
                                    if (vm.applyCommand == PrinterCommands.Z_OFFSET_APPLY_PROBE) {
                                        dispatcher?.dispatch(CommandRegistry.zOffsetApplyProbe, Unit)
                                    } else {
                                        dispatcher?.dispatch(CommandRegistry.zOffsetApplyEndstop, Unit)
                                    }
                                    // Immediately chain to Phase 2 — SAVE_CONFIG.
                                    onRequestConfirm(saveConfirm)
                                },
                            )
                        )
                    },
                    intent = Intent.Warn,
                    enabled = vm.canApply,
                    modifier = Modifier.weight(1f),
                )
                // Save: always visible — re-opens the SAVE_CONFIG guard for a prior apply.
                OutlinedControl(
                    label = saveTitle,
                    icon = JiibIcons.Save,
                    onClick = { onRequestConfirm(saveConfirm) },
                    intent = Intent.Warn,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // No ConfirmGuard rendered here — guards are hosted full-screen in ProbeContent.
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ProbeTestBody — Focus content for the Probe Test tool
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Focus body for the Probe Test tool (Task R3). Renders four rows of content:
 *  Row1 — color-coded status dot (t.stop = TRIGGERED, t.accent = OPEN) + status text + last-Z.
 *  Row2 — six-stat accuracy grid (range / σ / avg / median / min / max) when [ProbeTestVm.accuracy]
 *    is non-null; a single "—" placeholder otherwise.
 *  Row3 — samples stepper `[−] n [+]` (endpoints disable at 0 / lastIndex).
 *  Row4 — `[Query]` + `[Probe Once]` + `[Run Accuracy]` buttons (all [Intent.Go]).
 *
 * No [ConfirmGuard] is needed here (no SAVE_CONFIG path). No foot-bar actions — the foot bar
 * stays Back-only from [ProbeContent]'s field. The `dispatcher` is called directly from the
 * buttons, matching the [ApplyBabystepBody] pattern.
 *
 * The auto-queryProbe [LaunchedEffect] lives in the STATEFUL [ProbeScreen], not here.
 */
@Composable
internal fun ProbeTestBody(
    vm: ProbeTestVm,
    samplesIdx: Int,
    dispatcher: CommandDispatcher?,
    uDp: Dp,
    liveTriggered: Boolean? = null,
    probeIsZEndstop: Boolean = false,
    onSamplesUp: () -> Unit,
    onSamplesDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Row1: Status dot + OPEN/TRIGGERED text + last Z ───────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Live probe switch status — polled via query_endstops (stepper_z) ONLY when the probe
                // is the Z endstop; otherwise no live indicator ("—"). Driven by liveTriggered (Task 4
                // poll), NOT vm.triggered.
                val shown: Boolean? = if (probeIsZEndstop) liveTriggered else null
                val dotColor = when (shown) {
                    true  -> t.stop
                    false -> t.accent
                    null  -> t.text3
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    val statusText = when (shown) {
                        true  -> stringResource(R.string.probe_test_status_triggered)
                        false -> stringResource(R.string.probe_test_status_open)
                        null  -> "—"
                    }
                    Text(
                        text = statusText,
                        style = JiibType.body.toTextStyle(t),
                        color = dotColor,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.probe_test_last_z_label),
                        style = JiibType.caption.toTextStyle(t),
                        color = t.text2,
                    )
                    Text(
                        text = vm.lastZ?.let { probeTestFmtZ(it) } ?: "—",
                        style = JiibType.statValue.toTextStyle(t),
                        color = t.text,
                    )
                    if (vm.lastZ != null) {
                        Text(
                            text = "mm",
                            style = JiibType.caption.toTextStyle(t),
                            color = t.text3,
                        )
                    }
                }
            }

            // ── Row2: Accuracy stat block (or "—" when no run yet) ────────────
            val acc = vm.accuracy
            if (acc != null) {
                ProbeTestAccuracyBlock(acc)
            } else {
                Text(
                    text = "—",
                    style = JiibType.statValue.toTextStyle(t),
                    color = t.text3,
                )
            }

            // ── Row3: Samples stepper [−] n [+] ───────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedControl(
                    label = "",
                    onClick = onSamplesDown,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (samplesIdx > 0) Modifier
                            else Modifier.alpha(0.38f).semantics { disabled() },
                        ),
                    intent = Intent.Neutral,
                    icon = JiibIcons.Decrease,
                    contentDescription = stringResource(R.string.probe_test_cd_samples_decrease),
                    enabled = samplesIdx > 0,
                )
                ProbeTestSamplesDisplay(
                    samples = SAMPLES_STEPS[samplesIdx],
                    modifier = Modifier.weight(1f),
                )
                OutlinedControl(
                    label = "",
                    onClick = onSamplesUp,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (samplesIdx < SAMPLES_STEPS.lastIndex) Modifier
                            else Modifier.alpha(0.38f).semantics { disabled() },
                        ),
                    intent = Intent.Neutral,
                    icon = JiibIcons.Increase,
                    contentDescription = stringResource(R.string.probe_test_cd_samples_increase),
                    enabled = samplesIdx < SAMPLES_STEPS.lastIndex,
                )
            }

            // ── Row4: Single Probe + Probe Accuracy (all Go intent) ───────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedControl(
                    label = stringResource(R.string.probe_test_probe_once),
                    icon = JiibIcons.ProbeSingle,
                    onClick = { dispatcher?.dispatch(CommandRegistry.probeOnce, Unit) },
                    intent = Intent.Go,
                    modifier = Modifier.weight(1f),
                )
                OutlinedControl(
                    label = stringResource(R.string.probe_test_run_accuracy),
                    icon = JiibIcons.ProbeAccuracy,
                    onClick = {
                        dispatcher?.dispatch(
                            CommandRegistry.probeAccuracy,
                            ProbeAccuracyArgs(SAMPLES_STEPS[samplesIdx]),
                        )
                    },
                    intent = Intent.Go,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EddyRunBody — Focus content shared by Eddy Drive Current + Eddy Tap (Task R5)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Focus body shared by [ProbeTool.EDDY_DRIVE_CURRENT] and [ProbeTool.EDDY_TAP] (Task R5).
 *
 * Renders up to four rows inside the [FocusFrame] content area:
 *  Row1 — [description] text ([JiibType.body], [t.text2]) + [buildBlindNote] amber-caption
 *          line ([JiibType.caption], [t.heat]) signaling that validation requires eddy hardware.
 *          The ⚠ in [buildBlindNote] is a unicode character in the string resource — NOT a new
 *          glyph (icon-law: never choose a glyph without owner approval).
 *  Row2 — [ConsoleTail] filling available space (weight=1f, newest lines at bottom).
 *  Row3 — (EDDY_TAP only, when [tapStages] is non-null) three stage-selector chips:
 *          Guess / Refine / Verify. Selected chip is highlighted accent (Intent.Accent +
 *          [t.accentSoft] fill); others are neutral-outlined. Mirrors [CalibrationRunContent]'s
 *          stage selector treatment exactly.
 *  Row4 — [Run] ([Intent.Go]) + [Save Config] ([Intent.Warn]) buttons. Run dispatches the
 *          tool-specific command (caller-supplied [onRun]). Save Config raises the shared
 *          full-screen [ProbeConfirm] guard via the caller-supplied [onSave] = [onSaveConfig].
 *
 * **BUILD-BLIND:** Both eddy commands require eddy-current hardware. [buildBlindNote] is shown
 * in [t.heat] (amber) as a text-only caution; no extra glyph is added.
 *
 * No [ConfirmGuard] rendered here — the guard is hosted full-screen in [ProbeContent] via
 * [onSave] → [onSaveConfig] (matches the [ApplyBabystepBody] / [ZOffsetBody] hosting pattern).
 *
 * @param description   one-line tool description rendered at the top of the Focus body.
 * @param buildBlindNote amber caution caption (from [R.string.calibration_run_build_blind_note]).
 * @param lines         ordered console lines (newest last); capped/managed by the stateful
 *                      [ProbeScreen] caller (≤[EDDY_CONSOLE_CAP]).
 * @param tapStages     display labels for the stage selector; null = no selector (Drive Current).
 * @param selectedStage the currently selected stage display label; must be one of [tapStages].
 * @param onSelectStage called with the tapped stage display label.
 * @param onRun         called when [Run] is tapped; dispatches the tool's calibration command.
 * @param onSave        called when [Save Config] is tapped; raises the shared ProbeConfirm guard.
 * @param uDp           one unit U from the screen's unit grid (passed to [ConsoleTail]).
 */
@Composable
internal fun EddyRunBody(
    description: String,
    buildBlindNote: String,
    lines: List<String>,
    tapStages: List<String>?,
    selectedStage: String?,
    onSelectStage: (String) -> Unit,
    onRun: () -> Unit,
    onSave: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ── Row1: description + build-blind caution ──────────────────────────────
        Text(
            text = description,
            style = JiibType.body.toTextStyle(t),
            color = t.text2,
        )
        // Build-blind note: amber caption (t.heat) — text-only caution.
        // NO new glyph added (icon-law: never pick a glyph without owner approval).
        // The ⚠ is a unicode character embedded in the string resource.
        Text(
            text = buildBlindNote,
            style = JiibType.caption.toTextStyle(t),
            color = t.heat,
        )

        // ── Row2: ConsoleTail — fills available space ─────────────────────────────
        ConsoleTail(
            lines = lines,
            uDp = uDp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 4.dp),
        )

        // ── Row3 (EDDY_TAP only): stage selector chips ─────────────────────────────
        // null tapStages = no selector (Drive Current path). Mirrors CalibrationRunContent's
        // stage selector: selected chip = accent border + accentSoft fill; others = neutral.
        if (tapStages != null && selectedStage != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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

        // ── Row4: Run (go) + Save Config (warn) buttons ─────────────────────────────
        // Intent R5: Run = go (expected action), Save Config = warn (Klipper restart — hazard).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedControl(
                label = stringResource(R.string.calibration_run),
                icon = JiibIcons.CalibrationRun,
                onClick = onRun,
                intent = Intent.Go,
                modifier = Modifier.weight(1f),
            )
            OutlinedControl(
                label = stringResource(R.string.calibration_save_config),
                icon = JiibIcons.Save,
                onClick = onSave,
                intent = Intent.Warn,
                modifier = Modifier.weight(1f),
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

/** Four-decimal mm format for probe Z values (matches [ProbeTestScreen]'s precision). */
private fun probeTestFmtZ(v: Double): String = String.format(Locale.US, "%.4f", v)

/** Three-decimal mm format for the Z-Offset hero readout in [ZOffsetBody] / Field trailing.
 *  Matches [ProbeCalibrateScreen]'s `fmtZ` precision. */
private fun fmtZOffset(v: Double): String = String.format(Locale.US, "%.3f", v)

/**
 * Six-stat accuracy grid from a PROBE_ACCURACY run.
 * Two columns of three label/value pairs: range+σ+avg (left), median+min+max (right).
 * Matches the layout in [ProbeTestScreen.ProbeAccuracyBlock].
 * Values use [AccuracyAutoText] with weight(1f) so long numerals shrink to fit instead of
 * overflowing on narrow Focus columns.
 */
@Composable
private fun ProbeTestAccuracyBlock(acc: ProbeAccuracyResult, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val col1 = listOf(
        Pair(stringResource(R.string.probe_test_accuracy_range),   probeTestFmtZ(acc.range)),
        Pair(stringResource(R.string.probe_test_accuracy_std_dev), probeTestFmtZ(acc.stdDev)),
        Pair(stringResource(R.string.probe_test_accuracy_average), probeTestFmtZ(acc.average)),
    )
    val col2 = listOf(
        Pair(stringResource(R.string.probe_test_accuracy_median),  probeTestFmtZ(acc.median)),
        Pair(stringResource(R.string.probe_test_accuracy_min),     probeTestFmtZ(acc.minimum)),
        Pair(stringResource(R.string.probe_test_accuracy_max),     probeTestFmtZ(acc.maximum)),
    )
    Row(
        modifier = modifier.fillMaxWidth(0.92f),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        listOf(col1, col2).forEach { col ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                col.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AccuracyAutoText(label, JiibType.caption, t.text2)
                        AccuracyAutoText(
                            text = value,
                            role = JiibType.dataInline,
                            color = t.text,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single-line shrink-to-fit text for the accuracy stat block.
 *
 * FONT-CONFORMANCE: derives TextStyle from the role (never inline fontFamily/fontSize) — the
 * FontConformanceTest forbids raw inline TextStyle in ProbeScreen.kt.
 * The value variant gets `Modifier.weight(1f)` + [TextAlign.End] so BasicText measures in a
 * constrained width and TextAutoSize can actually shrink (an unconstrained BasicText never shrinks).
 */
@Composable
private fun AccuracyAutoText(
    text: String,
    role: TextRole,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    val t = LocalTokens.current
    BasicText(
        text = text,
        style = role.toTextStyle(t).copy(color = color, textAlign = textAlign),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(
            minFontSize = fsSp(12f, t.fs).sp,
            maxFontSize = fsSp(role.baseSp, t.fs).sp,
            stepSize = 1.sp,
        ),
        modifier = modifier,
    )
}

/**
 * Read-only center cell for the [ProbeTestBody] samples stepper — shows the count and
 * the "Samples" label. Mirrors [ProbeTestScreen.SamplesDisplay].
 */
@Composable
private fun ProbeTestSamplesDisplay(samples: Int, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier.clip(shape).border(BorderStroke(2.dp, t.outline), shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            Text(
                text = samples.toString(),
                style = JiibType.statValue.toTextStyle(t),
                color = t.text,
            )
            Text(
                text = stringResource(R.string.probe_test_samples_label),
                style = JiibType.caption.toTextStyle(t),
                color = t.text3,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// probeToolIconToken / probeToolTitleRes — moved here from ProbeHubScreen (now deleted)
// ─────────────────────────────────────────────────────────────────────────────

/** Maps a [ProbeTool] to its owner-confirmed [JiibIcons] token. */
internal fun probeToolIconToken(tool: ProbeTool) = when (tool) {
    ProbeTool.Z_OFFSET           -> JiibIcons.RoutineProbeCalibrate
    ProbeTool.PROBE_TEST         -> JiibIcons.ProbeTestTool
    ProbeTool.APPLY_BABYSTEP     -> JiibIcons.Babystep
    ProbeTool.EDDY_CALIBRATE     -> JiibIcons.EddyCalibrate
    ProbeTool.EDDY_TAP           -> JiibIcons.EddyTap
    ProbeTool.EDDY_DRIVE_CURRENT -> JiibIcons.EddyDriveCurrent
}

/** Maps a [ProbeTool] to its title string resource ID. */
internal fun probeToolTitleRes(tool: ProbeTool): Int = when (tool) {
    ProbeTool.Z_OFFSET           -> R.string.probe_tool_z_offset_calibrate_title
    ProbeTool.PROBE_TEST         -> R.string.probe_tool_test_title
    ProbeTool.APPLY_BABYSTEP     -> R.string.probe_tool_babystep_title
    ProbeTool.EDDY_CALIBRATE     -> R.string.probe_tool_eddy_calibrate_title
    ProbeTool.EDDY_TAP           -> R.string.probe_tool_eddy_tap_title
    ProbeTool.EDDY_DRIVE_CURRENT -> R.string.probe_tool_eddy_drive_current_title
}

/** Field content mode — the Z-Offset Active session swaps the tool list for control rows. */
internal enum class ProbeFieldMode { TOOL_LIST, Z_CONTROL_ROWS }

/** Field foot-bar mode for the probe screen. */
internal enum class ProbeFootMode { BACK, ABORT, NONE }

/**
 * The Field morph ONLY engages for the owner-tracked Z-Offset session while Active. Keyed on
 * [activeSessionTool] (not [ProbePageState] alone, nor effectiveSelected) so an Eddy session — or an
 * untracked/null-owner session — can never swap the Field to Z control rows.
 */
internal fun probeFieldMode(activeSessionTool: ProbeTool?, zState: ProbePageState): ProbeFieldMode =
    if (activeSessionTool == ProbeTool.Z_OFFSET && zState == ProbePageState.Active) {
        ProbeFieldMode.Z_CONTROL_ROWS
    } else {
        ProbeFieldMode.TOOL_LIST
    }

/**
 * Foot-bar mode: Abort replaces Back ONLY during the Z-Offset Active session; any other active session
 * (Eddy, or Z "starting") keeps the foot empty (unchanged suppression); otherwise Back.
 */
internal fun probeFootMode(
    activeSessionTool: ProbeTool?,
    zState: ProbePageState,
    anySessionActive: Boolean,
): ProbeFootMode = when {
    activeSessionTool == ProbeTool.Z_OFFSET && zState == ProbePageState.Active -> ProbeFootMode.ABORT
    anySessionActive -> ProbeFootMode.NONE
    else -> ProbeFootMode.BACK
}

/**
 * Adopt an ORPHAN active manual_probe session as a Z-Offset session. An orphan is a session that is
 * live ([ProbePageState.Active]) but has no tracked owner ([activeSessionTool] == null) — it was started
 * outside this screen instance: by the console/Mainsail, or by an app restart / re-entry while the
 * session was still running. Without adoption the Focus resolves the orphan to Z-Offset (so it shows
 * Accept) but the Field/foot gates see a null owner and show NO control rows and NO Abort — leaving the
 * user stuck with only Accept (committing a possibly-wrong measurement). Adopting it as Z_OFFSET makes
 * [activeSessionTool] the single source of truth again so the morph + Abort foot appear, matching the
 * in-screen-start flow. TESTZ/ACCEPT/ABORT all work for any manual_probe session, so an (extremely rare)
 * orphan eddy session is also driveable this way. Returns the tool to adopt, or null when nothing
 * should be adopted (already tracked, or not a live session).
 */
internal fun orphanSessionAdoption(state: ProbePageState, activeSessionTool: ProbeTool?): ProbeTool? =
    if (state == ProbePageState.Active && activeSessionTool == null) ProbeTool.Z_OFFSET else null

// ─────────────────────────────────────────────────────────────────────────────
// ManualProbeJog + helpers — moved here from ProbeCalibrateScreen (now deleted)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reusable two-column jog widget (D-08 Move motif):
 *  - Column 1 (Z-nudge): Z-up (TESTZ +step) / [ZReadoutDisplay] / Z-down (TESTZ -step)
 *  - Column 2 (step selector): Increase / [StepDisplay] / Decrease
 *
 * Used by [ZOffsetBody] and [EddyCalibrateBody].
 */
@Composable
internal fun ManualProbeJog(
    vm: ProbeCalibrateVm,
    step: Double,
    steps: List<Double>,
    enabled: Boolean,
    onTestZUp: () -> Unit,
    onTestZDown: () -> Unit,
    onStepUp: () -> Unit,
    onStepDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val idx = steps.indexOf(step).let { if (it < 0) 0 else it }
    // D-08: two vertical 3-cell columns side by side (the Move motif).
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Column 1: Z-nudge column with live-Z readout center cell.
        // Outline = t.directional.z (Z-axis identity, same as Move's Z column).
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Z-up: TESTZ +step (accent, physical command)
            ProbeIconButton(
                glyphName = "arrow_upward",
                contentDescription = stringResource(R.string.probe_cd_raise),
                onClick = onTestZUp,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                intent = Intent.Accent,
                iconTint = t.accent,
                enabled = enabled,
            )
            // D-08 center cell: live-Z readout (Geist Mono, t.directional.z outline)
            ZReadoutDisplay(
                zValue = vm.zPosition ?: vm.savedZOffset,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            // Z-down: TESTZ -step (accent, physical command)
            ProbeIconButton(
                glyphName = "arrow_downward",
                contentDescription = stringResource(R.string.probe_cd_lower),
                onClick = onTestZDown,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                intent = Intent.Accent,
                iconTint = t.accent,
                enabled = enabled,
            )
        }
        // Column 2: step selector (neutral — a setting, not a directional control).
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedControl(
                label = "",
                onClick = onStepUp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (idx < steps.lastIndex) Modifier
                        else Modifier.alpha(0.38f).semantics { disabled() },
                    ),
                intent = Intent.Neutral,
                icon = JiibIcons.Increase,
                contentDescription = stringResource(R.string.probe_cd_step_larger),
                enabled = idx < steps.lastIndex,
            )
            StepDisplay(value = step, modifier = Modifier.weight(1f).fillMaxWidth())
            OutlinedControl(
                label = "",
                onClick = onStepDown,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (idx > 0) Modifier
                        else Modifier.alpha(0.38f).semantics { disabled() },
                    ),
                intent = Intent.Neutral,
                icon = JiibIcons.Decrease,
                contentDescription = stringResource(R.string.probe_cd_step_smaller),
                enabled = idx > 0,
            )
        }
    }
}

/** True when a larger step exists above the current one (mirrors ManualProbeJog's `idx < lastIndex`). */
internal fun incrementUpEnabled(step: Double, steps: List<Double>): Boolean {
    val idx = steps.indexOf(step).let { if (it < 0) 0 else it }
    return idx < steps.lastIndex
}

/** True when a smaller step exists below the current one. Unknown step → index 0 → false. */
internal fun incrementDownEnabled(step: Double, steps: List<Double>): Boolean {
    val idx = steps.indexOf(step).let { if (it < 0) 0 else it }
    return idx > 0
}

/**
 * The four Z-Offset Active-state control rows that replace the tool list in the Field while a
 * Z-Offset calibration session is live. Built standalone so Eddy can adopt it later.
 *
 * Move rows render the raw `arrow_upward`/`arrow_downward` ligatures via the SAME JiibIconView path
 * ProbeIconButton uses (icon-law: same glyph/purpose as ManualProbeJog, not a new choice) tinted
 * `t.directional.z`. Increment rows use the registered Increase/Decrease tokens via ListRowIcon.
 * Rows are non-tappable + dimmed when disabled.
 */
@Composable
internal fun ProbeControlRows(
    step: Double,
    steps: List<Double>,
    jogEnabled: Boolean,
    uDp: Dp,
    onTestZUp: () -> Unit,
    onTestZDown: () -> Unit,
    onStepUp: () -> Unit,
    onStepDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val upEnabled = incrementUpEnabled(step, steps)
    val downEnabled = incrementDownEnabled(step, steps)

    ListBlock(modifier = modifier) {
        item(key = "move_up") {
            ControlRow(
                enabled = jogEnabled, onClick = onTestZUp, uDp = uDp,
                leadingContent = { RawArrow("arrow_upward", stringResource(R.string.probe_cd_raise), uDp) },
                label = stringResource(R.string.zoffset_move_up),
            )
        }
        item(key = "move_down") {
            ControlRow(
                enabled = jogEnabled, onClick = onTestZDown, uDp = uDp,
                leadingContent = { RawArrow("arrow_downward", stringResource(R.string.probe_cd_lower), uDp) },
                label = stringResource(R.string.zoffset_move_down),
            )
        }
        item(key = "inc_up") {
            ControlRow(
                enabled = upEnabled, onClick = onStepUp, uDp = uDp,
                leadingContent = { ListRowIcon(icon = JiibIcons.Increase, uDp = uDp, tint = t.text) },
                label = stringResource(R.string.zoffset_increment_up),
            )
        }
        item(key = "inc_down") {
            ControlRow(
                enabled = downEnabled, onClick = onStepDown, uDp = uDp,
                leadingContent = { ListRowIcon(icon = JiibIcons.Decrease, uDp = uDp, tint = t.text) },
                label = stringResource(R.string.zoffset_increment_down),
            )
        }
    }
}

/** A raw Material ligature leading glyph, sized to a list-row icon, in the Z-axis directional tint. */
@Composable
private fun RawArrow(glyph: String, cd: String, uDp: Dp) {
    val t = LocalTokens.current
    JiibIconView(
        icon = JiibIcon(IconRef.Ligature(glyph), alternate = glyph),
        contentDescription = cd,
        tint = t.directional.z,
        sizeDp = uDp * 0.6f,
    )
}

/** One translucent control row honoring the standard disabled treatment. */
@Composable
private fun ControlRow(
    enabled: Boolean,
    onClick: () -> Unit,
    uDp: Dp,
    leadingContent: @Composable () -> Unit,
    label: String,
) {
    val t = LocalTokens.current
    ListRow(
        selected = false,
        onClick = if (enabled) onClick else ({}),
        uDp = uDp,
        modifier = if (enabled) Modifier else Modifier.alpha(0.38f).semantics { disabled() },
        leadingContent = leadingContent,
    ) {
        ListRowLabel(text = label, color = if (enabled) t.text else t.text3)
    }
}

/**
 * The live-Z readout center cell of the Z-nudge column (D-08). Shows the current probe-session Z
 * position (or saved offset when Idle). Uses Geist Mono tabular numerals and `t.directional.z` outline
 * for Z-axis visual identity.
 */
@Composable
private fun ZReadoutDisplay(zValue: Double?, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier.clip(shape).border(BorderStroke(2.dp, t.directional.z), shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = zValue?.let { fmtZ(it) } ?: "—",
                color = t.text,
                style = JiibType.statValue.toTextStyle(t),
            )
            Text(
                text = "mm",
                color = t.text3,
                style = JiibType.caption.toTextStyle(t),
            )
        }
    }
}

/** The current adjustment step (mm) — a neutral-outlined read-only cell between the [−]/[+] buttons. */
@Composable
private fun StepDisplay(value: Double, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier.clip(shape).border(BorderStroke(2.dp, t.outline), shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = fmtStep(value),
                color = t.text,
                style = JiibType.statValue.toTextStyle(t),
            )
            Text(
                text = "mm",
                color = t.text3,
                style = JiibType.caption.toTextStyle(t),
            )
        }
    }
}

/**
 * An outline-led icon button (renders a Material Symbols ligature, not a label).
 *
 * Renders through the a11y-aware [JiibIconView] so the real [contentDescription] is the spoken
 * TalkBack label — without promoting the site into the [JiibIcons] registry. The disabled/iconTint
 * coloring and ≥64dp button chrome match the original ProbeCalibrateScreen treatment.
 */
@Composable
private fun ProbeIconButton(
    glyphName: String,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    intent: Intent = Intent.Neutral,
    iconTint: Color? = null,
    enabled: Boolean = true,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val outline = intentColor(intent, t)
    val base = modifier
        .heightIn(min = 64.dp)
        .clip(shape)
        .border(BorderStroke(2.dp, if (enabled) outline else t.hair), shape)
        .background(if (enabled) Color.Transparent else t.surface)
    val box = if (enabled) base.clickable(onClick = onClick) else base
    Box(box, contentAlignment = Alignment.Center) {
        JiibIconView(
            icon = JiibIcon(IconRef.Ligature(glyphName), alternate = glyphName),
            contentDescription = contentDescription,
            tint = if (!enabled) t.text3 else (iconTint ?: t.text),
            sizeDp = fsSp(34f, t.fs).dp,
        )
    }
}

/** Three-decimal mm — manual-probe nudges are fine (down to 0.005 mm). */
private fun fmtZ(v: Double): String = String.format(Locale.US, "%.3f", v)

/** Step label: drop the trailing ".0" on whole-mm steps, keep the fractional ones (0.005 … 0.5). */
private fun fmtStep(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

/** The four Active-state Focus readouts for Probe Calibrate, formatted (negated convention). */
internal data class ZReadouts(
    val currentOffset: String,
    val saved: String,
    val difference: String,
    val stepSize: String,
)

internal fun zOffsetActiveReadouts(vm: ProbeCalibrateVm, step: Double): ZReadouts {
    val saved = vm.savedZOffset
    val zPos = vm.zPosition
    val currentText = zPos?.let { fmtZOffset(-it) } ?: "—"
    val savedText = saved?.let { fmtZOffset(-it) } ?: "—"
    // Difference = displayed Current − displayed Saved = (-zPos) − (-saved) = saved − zPos.
    val diffText = if (saved != null && zPos != null) fmtZOffset(saved - zPos) else "—"
    return ZReadouts(
        currentOffset = currentText,
        saved = savedText,
        difference = diffText,
        stepSize = fmtStep(step),
    )
}
