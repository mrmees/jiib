package works.mees.jiib.ui.calibration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale
import works.mees.jiib.R
import works.mees.jiib.calibration.ProbeAccuracyResult
import works.mees.jiib.calibration.ProbeTestHolder
import works.mees.jiib.calibration.ProbeTestVm
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.GatingState
import works.mees.jiib.command.ProbeAccuracyArgs
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.HardLockStatusCard
import works.mees.jiib.designsystem.components.UnknownStatusCard
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.control.OutlinedControl
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.theme.fsSp

// ─────────────────────────────────────────────────────────────────────────────
// Sample steps for the samples stepper (integers: 1, 2, 3, 5, 10, 20, 30, 50)
// ─────────────────────────────────────────────────────────────────────────────
private val SAMPLES_STEPS: List<Int> = listOf(1, 2, 3, 5, 10, 20, 30, 50)
private val SAMPLES_DEFAULT_IDX: Int = SAMPLES_STEPS.indexOf(10).coerceAtLeast(0) // index 4

/**
 * Thin VM-reading wrapper for ProbeTestScreen. Collects `holder.vm`, the dispatcher,
 * printerState and gating, owns the `samples` UI state, then delegates to the stateless
 * [ProbeTestContent] overload (@Preview matrices target [ProbeTestContent], not this screen).
 *
 * **Auto-query:** `LaunchedEffect(Unit)` dispatches `queryProbe` once on entry so the
 * status/last-Z fields are populated immediately without user action.
 *
 * @param container the service-locator (provides the session dispatcher).
 * @param holder    the headless [ProbeTestHolder] (status + last-Z + accuracy state).
 * @param onBack    leave the page (standard Back).
 */
@Composable
fun ProbeTestScreen(
    container: AppContainer,
    holder: ProbeTestHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    val gating by container.gatingState.collectAsStateWithLifecycle(initialValue = GatingState.Idle)
    val vm by holder.vm.collectAsStateWithLifecycle()

    // Samples UI state — integer count sent to PROBE_ACCURACY; default 10 (index 4 in SAMPLES_STEPS).
    var samplesIdx by remember { mutableStateOf(SAMPLES_DEFAULT_IDX) }
    val samples = SAMPLES_STEPS[samplesIdx]

    // Auto-query once on entry to populate triggered/lastZ from the printer.
    LaunchedEffect(Unit) { dispatcher?.dispatch(CommandRegistry.queryProbe, Unit) }

    // ProbeTest gates on home_* only (same scope as ProbeCalibrate).
    val isHoming = (gating as? GatingState.Locked)?.key?.startsWith("home") == true
    val enabled = dispatcher != null && !isHoming

    ProbeTestContent(
        vm = vm,
        samples = samples,
        samplesIdx = samplesIdx,
        isPrinting = isPrinting,
        gating = gating,
        enabled = enabled,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onAcknowledgeUnknown = { dispatcher?.acknowledgeUnresolved() },
        onQuery = { dispatcher?.dispatch(CommandRegistry.queryProbe, Unit) },
        onProbeOnce = { dispatcher?.dispatch(CommandRegistry.probeOnce, Unit) },
        onRunAccuracy = { dispatcher?.dispatch(CommandRegistry.probeAccuracy, ProbeAccuracyArgs(samples)) },
        onSamplesUp = { samplesIdx = (samplesIdx + 1).coerceAtMost(SAMPLES_STEPS.lastIndex) },
        onSamplesDown = { samplesIdx = (samplesIdx - 1).coerceAtLeast(0) },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless ProbeTestScreen layout — the @Preview matrix targets this composable.
 *
 * Focus = probe status (color dot + OPEN/TRIGGERED), last-Z readout, and accuracy stat block
 * (range / stdDev / average / median / min / max in Geist Mono tabular). Shows "—" when null.
 *
 * Field = samples stepper (OutlinedControl +/− around [SamplesDisplay]) + FootButtonBar:
 *  - Back (accent, first per R8)
 *  - Query (Go)
 *  - Probe Once (Go)
 *  - Run Accuracy (Go)
 *  → 4 actions ≥ FOOT_BAR_ICON_ONLY_THRESHOLD → icon-only automatically.
 *
 * HardLock / Unknown morphs: ProbeTest owns `home_*` scope only — same as ProbeCalibrate.
 */
@Composable
fun ProbeTestContent(
    vm: ProbeTestVm,
    samples: Int,
    samplesIdx: Int = SAMPLES_DEFAULT_IDX,
    isPrinting: Boolean = false,
    gating: GatingState = GatingState.Idle,
    enabled: Boolean = true,
    onEmergencyStop: () -> Unit = {},
    onAcknowledgeUnknown: () -> Unit = {},
    onQuery: () -> Unit = {},
    onProbeOnce: () -> Unit = {},
    onRunAccuracy: () -> Unit = {},
    onSamplesUp: () -> Unit = {},
    onSamplesDown: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        // ProbeTest owns home_* scope only (same as ProbeCalibrate).
        val isHoming = (gating as? GatingState.Locked)?.key?.startsWith("home") == true
        val unknownOwned = (gating as? GatingState.Unknown)?.key?.startsWith("home") == true

        ScreenScaffold(
                focus = {
                    FocusFrame(
                        title = stringResource(R.string.probe_test_title),
                        icon = JiibIcons.ProbeTestTool,
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
                        // HardLock Focus morph: while homing, replace Focus body with status card.
                        if (isHoming) {
                            HardLockStatusCard(
                                stringResource(R.string.gating_homing),
                                grid.uDp,
                                Modifier.fillMaxSize(),
                            )
                            return@FocusFrame
                        }
                        ProbeTestFocus(
                            vm = vm,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                        )
                    }
                },
                field = {
                    // Samples stepper: OutlinedControl +/− around a SamplesDisplay readout cell.
                    ProbeTestSamplesStepper(
                        samples = samples,
                        samplesIdx = samplesIdx,
                        stepsCount = SAMPLES_STEPS.size,
                        onSamplesUp = onSamplesUp,
                        onSamplesDown = onSamplesDown,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    // FootButtonBar: Back (accent) + Query / Probe Once / Run Accuracy (all Go).
                    // 4 actions → icon-only per FOOT_BAR_ICON_ONLY_THRESHOLD (≥3).
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
                                label = stringResource(R.string.probe_test_query),
                                icon = JiibIcons.ProbeQuery,
                                onClick = onQuery,
                                intent = Intent.Go,
                                enabled = enabled,
                                contentDescription = stringResource(R.string.cd_probe_query),
                            ),
                            FootAction(
                                label = stringResource(R.string.probe_test_probe_once),
                                icon = JiibIcons.ProbeOnce,
                                onClick = onProbeOnce,
                                intent = Intent.Go,
                                enabled = enabled,
                                contentDescription = stringResource(R.string.cd_probe_once),
                            ),
                            FootAction(
                                label = stringResource(R.string.probe_test_run_accuracy),
                                icon = JiibIcons.CalibrationRun,
                                onClick = onRunAccuracy,
                                intent = Intent.Go,
                                enabled = enabled,
                                contentDescription = stringResource(R.string.probe_test_cd_run_accuracy),
                            ),
                        ),
                    )
                },
            )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ProbeTestFocus — the Focus body
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Focus body: three sections stacked vertically:
 *  1. Status row — color-coded dot (t.stop = TRIGGERED, t.accent = OPEN) + status text.
 *  2. Last-Z row — label + Geist Mono tabular value (mm).
 *  3. Accuracy block — 6-stat grid (range / σ / avg / median / min / max) when [ProbeTestVm.accuracy]
 *     is non-null; a single "—" placeholder otherwise.
 */
@Composable
private fun ProbeTestFocus(vm: ProbeTestVm, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Status row ──────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val dotColor = when (vm.triggered) {
                true  -> t.stop
                false -> t.accent
                null  -> t.text3
            }
            Box(
                Modifier
                    .size(18.dp)
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

        // ── Last-Z row ──────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.probe_test_last_z_label),
                style = JiibType.caption.toTextStyle(t),
                color = t.text2,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = vm.lastZ?.let { fmtProbeZ(it) } ?: "—",
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

        // ── Accuracy block ──────────────────────────────────────────────────
        val acc = vm.accuracy
        if (acc != null) {
            ProbeAccuracyBlock(acc)
        } else {
            Text(
                text = "—",
                style = JiibType.statValue.toTextStyle(t),
                color = t.text3,
            )
        }
    }
}

/**
 * Six-stat accuracy grid from a completed PROBE_ACCURACY run. Layout: two columns of three
 * label/value pairs (range+σ+avg in col-1, median+min+max in col-2).
 */
@Composable
private fun ProbeAccuracyBlock(acc: ProbeAccuracyResult, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    // Two-column table — each column is label/value pairs.
    val col1 = listOf(
        Pair(stringResource(R.string.probe_test_accuracy_range),   fmtProbeZ(acc.range)),
        Pair(stringResource(R.string.probe_test_accuracy_std_dev), fmtProbeZ(acc.stdDev)),
        Pair(stringResource(R.string.probe_test_accuracy_average), fmtProbeZ(acc.average)),
    )
    val col2 = listOf(
        Pair(stringResource(R.string.probe_test_accuracy_median),  fmtProbeZ(acc.median)),
        Pair(stringResource(R.string.probe_test_accuracy_min),     fmtProbeZ(acc.minimum)),
        Pair(stringResource(R.string.probe_test_accuracy_max),     fmtProbeZ(acc.maximum)),
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

// ─────────────────────────────────────────────────────────────────────────────
// ProbeTestSamplesStepper — the Field stepper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A compact horizontal stepper: [Decrease] / [SamplesDisplay] / [Increase], rendered in a single
 * row so it doesn't dominate the Field (the accuracy block is the main information surface).
 * Uses the same [OutlinedControl] + read-only center-cell pattern as the step-selector in
 * [ManualProbeJog].
 */
@Composable
private fun ProbeTestSamplesStepper(
    samples: Int,
    samplesIdx: Int,
    stepsCount: Int,
    onSamplesUp: () -> Unit,
    onSamplesDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
        SamplesDisplay(
            samples = samples,
            modifier = Modifier.weight(1f),
        )
        OutlinedControl(
            label = "",
            onClick = onSamplesUp,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (samplesIdx < stepsCount - 1) Modifier
                    else Modifier.alpha(0.38f).semantics { disabled() },
                ),
            intent = Intent.Neutral,
            icon = JiibIcons.Increase,
            contentDescription = stringResource(R.string.probe_test_cd_samples_increase),
            enabled = samplesIdx < stepsCount - 1,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SamplesDisplay — read-only center cell for the stepper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Read-only neutral-outlined cell showing the current samples count and the
 * "Samples" label below. Mirrors [StepDisplay] but for integer counts.
 */
@Composable
private fun SamplesDisplay(samples: Int, modifier: Modifier = Modifier) {
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
// Formatting helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Four-decimal mm format — probe Z values need more precision than TESTZ nudges. */
private fun fmtProbeZ(v: Double): String = String.format(Locale.US, "%.4f", v)
