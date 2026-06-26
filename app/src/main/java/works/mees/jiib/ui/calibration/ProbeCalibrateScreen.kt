package works.mees.jiib.ui.calibration

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import works.mees.jiib.R
import works.mees.jiib.calibration.CalibrationRoutine
import works.mees.jiib.calibration.ProbeCalibrateHolder
import works.mees.jiib.calibration.ProbeCalibrateVm
import works.mees.jiib.calibration.ProbePageState
import works.mees.jiib.command.CommandDispatcher
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.DispatchEvent
import works.mees.jiib.command.TestZArgs
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.ConfirmGuard
import works.mees.jiib.designsystem.Severity
import works.mees.jiib.designsystem.SeverityToast
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.footAction
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.icons.JiibIconView
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.icons.IconRef
import works.mees.jiib.control.ControlSpecs
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.control.OutlinedControl
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.theme.fsSp
import works.mees.jiib.ui.increments.IncrementControls

/**
 * Thin VM-reading wrapper for ProbeCalibrateScreen. Collects `holder.vm`, computes the `starting`
 * state and owns the SAVE_CONFIG guard state, then delegates to the stateless
 * [ProbeCalibrateContent] overload (WARNING-5 preview seam — @Preview matrices target
 * [ProbeCalibrateContent], not this screen).
 *
 * **D-09 / Back suppression:** The nav-layer `BackHandler` in `composable<NavDest.CalibrationProbe>`
 * (AppShell.kt) owns system-Back suppression while `Active || starting`. This screen's
 * Active-state FootButtonBar omits a Back button so the only exits are Accept/Abort (T-27-04-01).
 *
 * **Pitfall 3:** `LaunchedEffect(Unit) { holder.reset() }` is present — per-session holder's
 * sawActive/captured latches survive re-entry; without reset a returning user sees stale Accepted.
 *
 * @param container the service-locator (provides the session dispatcher).
 * @param holder    the headless [ProbeCalibrateHolder] (state machine + live Z + bracket + start cmd).
 * @param onBack    leave the page (neutral Back; only offered when NOT Active per D-09).
 */
@Composable
fun ProbeCalibrateScreen(
    container: AppContainer,
    holder: ProbeCalibrateHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    val vm by holder.vm.collectAsStateWithLifecycle()

    // Pitfall 3: fresh-instance reset on entry — clears sawActive/captured from any prior session.
    LaunchedEffect(Unit) { holder.reset() }

    val incrementLists by container.activeIncrementLists.collectAsStateWithLifecycle(emptyMap())
    val testzSteps = remember(incrementLists) {
        incrementLists["probe_testz"] ?: IncrementControls.defaultValueMap().getValue("probe_testz")
    }

    var step by remember { mutableStateOf(0.05) }
    // Rebase: when the active list changes, snap `step` to the nearest present value (value-tracked control).
    LaunchedEffect(testzSteps) {
        if (step !in testzSteps) {
            step = testzSteps.minByOrNull { kotlin.math.abs(it - step) } ?: testzSteps.first()
        }
    }
    var saveGuard by remember { mutableStateOf(false) }

    // Immediate "Starting…" feedback: Start gcode in flight but session not yet Active (the klicky
    // macro homes/attaches/probes for several seconds before is_active flips). Verbatim from prior
    // implementation (Pitfall 6 — carry verbatim; also consumed by the nav-layer BackHandler).
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val starting = vm.state == ProbePageState.Idle &&
        ("probe_calibrate" in inFlight || "z_endstop_calibrate" in inFlight)

    // Dismissable error toast: rejected TESTZ/Accept/Start surfaces a redacted Failure.
    var toastError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(dispatcher) {
        val d = dispatcher ?: return@LaunchedEffect
        d.events.collect { event -> if (event is DispatchEvent.Failure) toastError = event.message }
    }
    LaunchedEffect(vm.errorText) { vm.errorText?.let { toastError = it } }
    LaunchedEffect(toastError) {
        if (toastError != null) {
            delay(5_000)
            toastError = null
        }
    }

    ProbeCalibrateContent(
        vm = vm,
        step = step,
        steps = testzSteps,
        starting = starting,
        saveGuard = saveGuard,
        toastError = toastError,
        isPrinting = isPrinting,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        enabled = vm.state == ProbePageState.Active && dispatcher != null && "testz" !in inFlight,
        onTestZUp = { dispatcher?.dispatch(CommandRegistry.testZ, TestZArgs(step)) },
        onTestZDown = { dispatcher?.dispatch(CommandRegistry.testZ, TestZArgs(-step)) },
        onStepUp = { step = testzSteps[(testzSteps.indexOf(step).let { if (it < 0) 0 else it } + 1).coerceAtMost(testzSteps.lastIndex)] },
        onStepDown = { step = testzSteps[(testzSteps.indexOf(step).let { if (it < 0) 0 else it } - 1).coerceAtLeast(0)] },
        onHomeAll = { dispatcher?.dispatch(CommandRegistry.homeAll, Unit) },
        onStart = {
            val d = dispatcher ?: return@ProbeCalibrateContent
            if (vm.startCommand == "Z_ENDSTOP_CALIBRATE") {
                d.dispatch(CommandRegistry.zEndstopCalibrate, Unit)
            } else {
                d.dispatch(CommandRegistry.probeCalibrate, Unit)
            }
        },
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
        onDismissError = { toastError = null },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless ProbeCalibrateScreen layout — the @Preview matrix targets this composable.
 *
 * Field = two vertical 3-cell columns side by side (the Move motif per D-08):
 *  - Column 1 (Z-nudge): Z-up (TESTZ +step) / [ZReadoutDisplay] center (live Z, `t.directional.z`) / Z-down
 *  - Column 2 (step selector): + (step up) / [StepDisplay] / − (step down)
 *
 * FootButtonBar is state-adaptive per D-09 (Idle-unhomed / starting / Idle-homed / Active / Accepted).
 * The Active branch omits Back (nav-layer BackHandler owns system-Back, T-27-04-01).
 */
@Composable
fun ProbeCalibrateContent(
    vm: ProbeCalibrateVm,
    step: Double,
    steps: List<Double> = IncrementControls.defaultValueMap().getValue("probe_testz"),
    starting: Boolean,
    saveGuard: Boolean,
    toastError: String?,
    isPrinting: Boolean = false,
    onEmergencyStop: () -> Unit = {},
    enabled: Boolean,
    onTestZUp: () -> Unit,
    onTestZDown: () -> Unit,
    onStepUp: () -> Unit,
    onStepDown: () -> Unit,
    onHomeAll: () -> Unit,
    onStart: () -> Unit,
    onAccept: () -> Unit,
    onAbort: () -> Unit,
    onSaveGuardShow: () -> Unit,
    onSaveConfirm: () -> Unit,
    onSaveCancel: () -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val idx = steps.indexOf(step).let { if (it < 0) 0 else it }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        Box(Modifier.fillMaxSize()) {
            ScreenScaffold(
                focus = {
                    FocusFrame(
                        title = stringResource(routineTitleRes(CalibrationRoutine.PROBE_CALIBRATE)),
                        icon = routineIconToken(CalibrationRoutine.PROBE_CALIBRATE),
                        uDp = grid.uDp,
                        modifier = Modifier.fillMaxSize(),
                        isPrinting = isPrinting,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                    ) {
                        ProbeFocus(vm = vm, modifier = Modifier.fillMaxSize().padding(8.dp))
                    }
                },
                field = {
                    // D-08: two vertical 3-cell columns side by side (the Move motif).
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
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
                            // Control baseline audit (Phase 4b, owner ruling 2026-06-15): the
                            // step-size ± migrate off the hand-rolled ProbeIconButton rogue onto the
                            // canonical OutlinedControl with the REGISTERED Increase/Decrease tokens
                            // (kills the raw "add"/"remove" ligature strings). Stays Intent.Neutral —
                            // this picks a magnitude (a setting), it does not command motion (§b#2).
                            // The vertical paired-column "Move motif" layout is preserved (owner kept
                            // the paired columns; the Z-nudge column beside it is a bed-area jog and
                            // stays as-is). End-stop disablement dims via the StepperRow/WR-07
                            // convention (alpha 0.38 + semantics{disabled()}) so a dead end-button
                            // reads as disabled rather than active-but-inert (R10).
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
                    // D-09: state-adaptive FootButtonBar (verbatim semantics from prior gutter).
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
                                } else if (!vm.homedGate) {
                                    // Back FIRST (accent — R5/R8); Home All = go (this state's
                                    // expected action, R19 motion-as-purpose).
                                    add(FootAction(
                                        label = stringResource(R.string.common_back),
                                        icon = JiibIcons.Back,
                                        onClick = onBack,
                                        intent = Intent.Accent,
                                        contentDescription = stringResource(R.string.common_back),
                                    ))
                                    add(footAction(ControlSpecs.calibrationHomeAll, onClick = onHomeAll))
                                } else {
                                    // Back FIRST (accent); Start = go (the screen's expected action).
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
                                    // Back SUPPRESSED — nav-layer BackHandler in AppShell swallows system Back (D-09 / T-27-04-01).
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
                                    // hazard-in-process, R5).
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

            // T-27-04-02: amber SAVE_CONFIG restart gate (proceed-at-peril) — persists the captured offset.
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

            // Error toast: dismissed by tap + auto-clears after 5 s.
            val shownError = toastError
            if (shownError != null) {
                Box(
                    Modifier.fillMaxSize().padding(12.dp), // gapM (R13)
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    SeverityToast(
                        Severity.Error,
                        shownError,
                        Modifier.fillMaxWidth().clickable { onDismissError() },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Focus composable (unchanged information, restyled)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Focus = three icons (probe — expand — nozzle) over the focal Z value (theme accent). State-adaptive:
 *  - Idle → the saved `probe.z_offset` from config (negated for display — Moonraker stores positive);
 *  - Active → the LIVE `manual_probe.z_position` from macro feedback (live nudge position),
 *    with the original saved offset shown smaller for reference;
 *  - Accepted → the captured offset, same saved-offset reference underneath.
 */
@Composable
private fun ProbeFocus(vm: ProbeCalibrateVm, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val saved = vm.savedZOffset // raw, stored positive in Moonraker
    val currentZ = when (vm.state) {
        ProbePageState.Active -> vm.zPosition
        ProbePageState.Accepted -> vm.capturedOffset
        else -> null
    }
    val zText = when (vm.state) {
        ProbePageState.Idle -> saved?.let { fmtZ(-it) } ?: "—"
        else -> if (saved != null && currentZ != null) fmtZ(currentZ - saved) else "—"
    }
    val zColor = t.accent2
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier.fillMaxWidth(0.82f),
            horizontalArrangement = Arrangement.spacedBy(12.dp), // gapM (R13)
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JiibIconView(
                icon = JiibIcon(IconRef.Ligature("detector"), alternate = "detector"),
                contentDescription = stringResource(R.string.probe_cd_probe),
                tint = t.text2,
                sizeDp = fsSp(56f, t.fs).dp,
                modifier = Modifier.weight(1f).aspectRatio(1f),
            )
            JiibIconView(
                icon = JiibIcon(IconRef.Ligature("expand"), alternate = "expand"),
                contentDescription = stringResource(R.string.probe_cd_z_offset),
                tint = t.text2,
                sizeDp = fsSp(56f, t.fs).dp,
                modifier = Modifier.weight(1f).aspectRatio(1f),
            )
            Icon(
                painter = painterResource(R.drawable.nozzle),
                contentDescription = stringResource(R.string.probe_cd_nozzle),
                tint = t.text2,
                modifier = Modifier.weight(1f).aspectRatio(1f),
            )
        }
        Text(
            text = zText,
            color = zColor,
            style = JiibType.focusHero.toTextStyle(t),
            modifier = Modifier.padding(top = 20.dp),
        )
        if (vm.state != ProbePageState.Idle) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                saved?.let {
                    Text(
                        text = stringResource(R.string.probe_saved_ref, fmtZ(-it)),
                        color = t.text2,
                        style = JiibType.statValue.toTextStyle(t),
                    )
                }
                currentZ?.let {
                    Text(
                        text = "Z  ${fmtZ(it)}",
                        color = t.text2,
                        style = JiibType.statValue.toTextStyle(t),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// D-08: ZReadoutDisplay — the new center cell of the Z-nudge column
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// StepDisplay (unchanged from prior implementation)
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// ProbeIconButton (unchanged from prior implementation)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * An outline-led icon button (renders a Material Symbols ligature, not a label).
 *
 * 18.1-03 (D-08/D-09): the glyph is a Material Symbols ligature named by [glyphName] (was a
 * `painter: Painter` drawable). It renders through the a11y-aware [JiibIconView] so the real
 * [contentDescription] is the spoken TalkBack label (NOT the raw ligature name) — without promoting
 * the site into the [JiibIcons] registry. The disabled/iconTint coloring and ≥64dp button chrome
 * are unchanged; the glyph is sized at a fixed Focus-jog magnitude.
 */
@Composable
private fun ProbeIconButton(
    glyphName: String,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    intent: Intent = Intent.Neutral,
    iconTint: androidx.compose.ui.graphics.Color? = null,
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

// ─────────────────────────────────────────────────────────────────────────────
// Formatting helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Three-decimal mm — manual-probe nudges are fine (down to 0.005 mm). */
private fun fmtZ(v: Double): String = String.format(Locale.US, "%.3f", v)

/** Step label: drop the trailing ".0" on whole-mm steps, keep the fractional ones (0.005 … 0.5). */
private fun fmtStep(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
