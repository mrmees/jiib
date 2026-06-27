package works.mees.jiib.ui.calibration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.FocusInset
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.Capabilities
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.theme.fsSp
import works.mees.jiib.ui.increments.IncrementControls

// Internal tap-stage API keys (Klipper STAGE= parameter values for eddyTapCalibrate).
// Mirrors CalibrationRunScreen's TAP_STAGE_KEYS; kept here so ProbeContent can build
// the correct EddyTapArgs without a public dependency on CalibrationRunScreen's private val.
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
    gcodeResponses: SharedFlow<String>,
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

    var selected by remember { mutableStateOf<ProbeTool?>(null) }
    var samplesIdx by remember { mutableStateOf(SAMPLES_DEFAULT_IDX) }
    // D-05: pre-select first tool + reconcile against the current visible list.
    // Writing state during composition is valid here — the condition settles after at most one
    // extra recomposition (once selected is set, the `if` body no longer fires).
    if (selected == null || tools.none { it.tool == selected }) {
        selected = tools.firstOrNull()?.tool
    }

    // Auto-query when PROBE_TEST is first shown; reset the probe-calibrate holder on Z_OFFSET entry
    // so a returning user never sees stale Accepted state (Pitfall 3).
    LaunchedEffect(selected) {
        when (selected) {
            ProbeTool.PROBE_TEST -> dispatcher?.dispatch(CommandRegistry.queryProbe, Unit)
            ProbeTool.Z_OFFSET -> probeCalibrateHolder.reset()
            else -> {}
        }
    }

    ProbeContent(
        tools = tools,
        selected = selected,
        applyBabystepVm = applyBabystepVm,
        probeTestVm = probeTestVm,
        probeCalibrateVm = probeCalibrateVm,
        step = step,
        steps = testzSteps,
        starting = starting,
        inFlight = inFlight,
        samplesIdx = samplesIdx,
        dispatcher = dispatcher,
        isPrinting = isPrinting,
        gating = gating,
        eddyLines = eddyLines,
        eddyChip = eddyChip,
        eddySelectedStageIdx = eddySelectedStageIdx,
        onEddySelectStageIdx = { eddySelectedStageIdx = it },
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onSelect = { selected = it },
        onSamplesUp = { samplesIdx = (samplesIdx + 1).coerceAtMost(SAMPLES_STEPS.lastIndex) },
        onSamplesDown = { samplesIdx = (samplesIdx - 1).coerceAtLeast(0) },
        onStart = {
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
    probeTestVm: ProbeTestVm = ProbeTestVm(),
    probeCalibrateVm: ProbeCalibrateVm = ProbeCalibrateVm(),
    step: Double = 0.05,
    steps: List<Double> = IncrementControls.defaultValueMap().getValue("probe_testz"),
    starting: Boolean = false,
    inFlight: Set<String> = emptySet(),
    samplesIdx: Int = SAMPLES_DEFAULT_IDX,
    dispatcher: CommandDispatcher? = null,
    isPrinting: Boolean = false,
    gating: GatingState = GatingState.Idle,
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

    // inFlight is threaded in as a param (already collected in ProbeScreen) — single subscriber.
    // Used to gate ManualProbeJog: no-op TESTZ while a jog is already in flight.
    val jogEnabled = probeCalibrateVm.state == ProbePageState.Active &&
        dispatcher != null && "testz" !in inFlight

    // Pre-read SAVE_CONFIG strings for the onSaveConfig lambda built below
    // (string resources must be read in composable context, not inside a lambda).
    val saveTitle   = stringResource(R.string.calibration_save_config)
    val saveMessage = stringResource(R.string.calibration_save_config_confirm)

    // Null-selected guard: when selected == null use the hub identity fallback in the Focus header.
    val focusTitle = selected?.let { stringResource(probeToolTitleRes(it)) }
        ?: stringResource(R.string.probe_hub_title)
    val focusIcon = selected?.let { probeToolIconToken(it) } ?: JiibIcons.RoutineProbeCalibrate

    // Shared full-screen guard host — bodies call onRequestConfirm to raise a ConfirmGuard that
    // overlays the entire scaffold (Focus + Field), not just the Focus content area.
    // Matches the BedMesh / ScrewsTilt pattern exactly.
    var pending by remember { mutableStateOf<ProbeConfirm?>(null) }

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
                    // D-09 BackHandler: the nav-layer BackHandler in composable<NavDest.Probe>
                    // (AppShell.kt) owns system-Back suppression while zActiveOrStarting (R4).
                    // TODO(R6): when Eddy Calibrate body is wired, OR in eddyActiveOrStarting
                    //  here and in the AppShell BackHandler for the combined sessionActive flag.
                    when (selected) {
                        ProbeTool.Z_OFFSET -> ZOffsetBody(
                            vm = probeCalibrateVm,
                            step = step,
                            steps = steps,
                            starting = starting,
                            enabled = jogEnabled,
                            onTestZUp = onTestZUp,
                            onTestZDown = onTestZDown,
                            onStepUp = onStepUp,
                            onStepDown = onStepDown,
                            onStart = onStart,
                            onAccept = onAccept,
                            onAbort = onAbort,
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
                        else -> {
                            // Placeholder body for tools not yet wired (R6 task — EDDY_CALIBRATE).
                            // onRequestConfirm = { pending = it } is available for future bodies
                            // that need a guard; pass it in here when wiring them.
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
                        // Session lock (D-09): while a Z-Offset probe session is active-or-starting,
                        // dim all rows and make them non-tappable so the user can't switch tools.
                        val rowLocked = zActiveOrStarting
                        ListRow(
                            selected = if (rowLocked) false else (entry.tool == selected),
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
                                // Trailing readout: OPEN/TRIGGERED color dot for PROBE_TEST row.
                                ProbeTool.PROBE_TEST -> ({
                                    val dotColor = when (probeTestVm.triggered) {
                                        true  -> t.stop
                                        false -> t.accent
                                        null  -> t.text3
                                    }
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(dotColor),
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

                // Back only — R5/R8: accent intent, first button.
                // D-09 session lock: suppress Back while Z-Offset session is active-or-starting;
                // the only exits are Accept/Abort (both in the ZOffsetBody Focus row).
                FootButtonBar(
                    uDp = grid.uDp,
                    actions = if (zActiveOrStarting) emptyList() else listOf(
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

        // Full-screen guard overlay — sibling to ScreenScaffold, covers both Focus and Field so
        // the user cannot escape via the Back foot button or a Field row tap.
        // Matches the BedMesh / ScrewsTilt hosting pattern exactly.
        pending?.let { c ->
            ConfirmGuard(
                title = c.title,
                message = c.message,
                confirmLabel = c.confirmLabel,
                warn = c.warn,
                onConfirm = { c.onConfirm(); pending = null },
                onCancel = { pending = null },
            )
        }
        } // Box(Modifier.fillMaxSize())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ZOffsetBody — Focus content for the Z-Offset Calibrate tool (Task R4)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Focus body for the Z-Offset Calibrate tool (Task R4). Renders four rows:
 *  Row1 — Z hero readout (state-adaptive: Idle = saved offset negated; Active = live Z position;
 *    Accepted = captured offset). Geist Mono, [JiibType.focusHero].
 *  Rows2-3 — [ManualProbeJog] (Z-nudge + step selector, two-column D-08 motif); enabled ONLY while
 *    Active and no TESTZ in flight.
 *  Row4 — State-adaptive button line:
 *    Idle + starting → disabled "Starting…" ([JiibType.Neutral], [CalibrationWait] icon).
 *    Idle + !homedGate → [Home All] ([Intent.Go]) — pre-flight, probe-calibrate needs XYZ homed.
 *    Idle + homedGate → [Start] ([Intent.Go]) — dispatches PROBE_CALIBRATE or Z_ENDSTOP_CALIBRATE
 *      per [ProbeCalibrateVm.startCommand] (probe-present gate A3).
 *    Active → [Accept] ([Intent.Go]) + [Abort] ([Intent.Danger]).
 *    Accepted → [Save &amp; Restart] ([Intent.Warn]) — raises the full-screen SAVE_CONFIG guard via
 *      [onSaveConfig], which calls [ProbeContent]'s shared [onRequestConfirm] host.
 *
 * No [ConfirmGuard] rendered here — the guard is hosted full-screen in [ProbeContent] via
 * [onSaveConfig] → [onRequestConfirm] (matches the [ApplyBabystepBody] pattern).
 *
 * Session lock: Field row selection + foot-bar Back are suppressed while [ProbePageState.Active]
 * or [starting] — enforced in [ProbeContent] via [zActiveOrStarting]. The nav-layer [BackHandler]
 * in `composable<NavDest.Probe>` (AppShell.kt) owns system-Back suppression (D-09 / T-27-04-01).
 */
@Composable
internal fun ZOffsetBody(
    vm: ProbeCalibrateVm,
    step: Double,
    steps: List<Double>,
    starting: Boolean,
    enabled: Boolean,
    onTestZUp: () -> Unit,
    onTestZDown: () -> Unit,
    onStepUp: () -> Unit,
    onStepDown: () -> Unit,
    onStart: () -> Unit,
    onAccept: () -> Unit,
    onAbort: () -> Unit,
    onSaveConfig: () -> Unit,
    onHomeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    // Row1 Z hero: proven delta formula from ProbeFocus (port, don't redesign).
    // Idle → negated saved offset; Active/Accepted → currentZ - saved (delta from saved baseline).
    val saved = vm.savedZOffset // raw, Moonraker stores positive
    val currentZ = when (vm.state) {
        ProbePageState.Active   -> vm.zPosition
        ProbePageState.Accepted -> vm.capturedOffset
        else                    -> null
    }
    val zText: String = when (vm.state) {
        ProbePageState.Idle -> saved?.let { fmtZOffset(-it) } ?: "—"
        else -> if (saved != null && currentZ != null) fmtZOffset(currentZ - saved) else "—"
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Row1: Z hero readout (Geist Mono, accent2 hero colour) ──────────────
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

        // ── Rows2-3: ManualProbeJog (two-column D-08 motif, weight fills space) ──
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

        // ── Row4: state-adaptive action buttons ──────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = fsSp(8f, t.fs).dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (vm.state) {
                ProbePageState.Idle -> when {
                    starting -> {
                        // Start gcode in flight, session not yet Active — disabled "Starting…"
                        OutlinedControl(
                            label = stringResource(R.string.probe_starting),
                            icon = JiibIcons.CalibrationWait,
                            onClick = {},
                            intent = Intent.Neutral,
                            enabled = false,
                            modifier = Modifier
                                .weight(1f)
                                .alpha(0.38f)
                                .semantics { disabled() },
                        )
                    }
                    !vm.homedGate -> {
                        // Must home XYZ first — Home All = the expected pre-flight action (Go).
                        OutlinedControl(
                            label = stringResource(R.string.calibration_home_all),
                            icon = JiibIcons.MoveHomeAll,
                            onClick = onHomeAll,
                            intent = Intent.Go,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    else -> {
                        // Homed and ready — Start = the screen's expected action (Go).
                        OutlinedControl(
                            label = stringResource(R.string.calibration_start),
                            icon = JiibIcons.CalibrationRun,
                            onClick = onStart,
                            intent = Intent.Go,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                ProbePageState.Active -> {
                    // Back suppressed (D-09) — only Accept (go) and Abort (danger) offered.
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
                ProbePageState.Accepted -> {
                    // SAVE_CONFIG restarts Klipper (hazard-in-process, R5 → warn / amber).
                    OutlinedControl(
                        label = stringResource(R.string.calibration_save_config),
                        icon = JiibIcons.Save,
                        onClick = onSaveConfig,
                        intent = Intent.Warn,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
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
                val dotColor = when (vm.triggered) {
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
                    val statusText = when (vm.triggered) {
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

            // ── Row4: Query + Probe Once + Run Accuracy (all Go intent) ───────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedControl(
                    label = stringResource(R.string.probe_test_query),
                    icon = JiibIcons.ProbeQuery,
                    onClick = { dispatcher?.dispatch(CommandRegistry.queryProbe, Unit) },
                    intent = Intent.Go,
                    modifier = Modifier.weight(1f),
                )
                OutlinedControl(
                    label = stringResource(R.string.probe_test_probe_once),
                    icon = JiibIcons.ProbeOnce,
                    onClick = { dispatcher?.dispatch(CommandRegistry.probeOnce, Unit) },
                    intent = Intent.Go,
                    modifier = Modifier.weight(1f),
                )
                OutlinedControl(
                    label = stringResource(R.string.probe_test_run_accuracy),
                    icon = JiibIcons.CalibrationRun,
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = label,
                            style = JiibType.caption.toTextStyle(t),
                            color = t.text2,
                        )
                        Text(
                            text = value,
                            style = JiibType.dataInline.toTextStyle(t),
                            color = t.text,
                        )
                    }
                }
            }
        }
    }
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
