package works.mees.jiib.ui.calibration

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.jiib.R
import works.mees.jiib.calibration.ProbeHubHolder
import works.mees.jiib.calibration.ProbeTool
import works.mees.jiib.calibration.ProbeToolEntry
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.GatingState
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.ListRowIcon
import works.mees.jiib.designsystem.components.ListRowLabel
import works.mees.jiib.designsystem.control.Intent
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tools by probeHubHolder.tools.collectAsStateWithLifecycle()
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val gating by container.gatingState.collectAsStateWithLifecycle(initialValue = GatingState.Idle)
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused

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
 */
@Composable
internal fun ProbeContent(
    tools: List<ProbeToolEntry>,
    selected: ProbeTool?,
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
                    // PLACEHOLDER body — Phase 2 replaces this with per-tool Focus content.
                    // Shows the selected tool's title + an em-dash caption via JiibType roles
                    // (NO inline fontSize/fontFamily — FontConformanceTest enforces this).
                    // TODO(refocus Phase 2): Z-Offset & Eddy Calibrate session bodies must re-introduce
                    //  the D-09 BackHandler (Back-suppress during an active manual-probe session) —
                    //  removed with the old routes in R1.
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
